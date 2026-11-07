#!/usr/bin/env python3
"""Otimiza os quadros dos avisos em vídeo — (5.17.0) agora TAMBÉM reduz a resolução.

Histórico: na 5.10.0 este script só recompromia o JPEG (mesma resolução). Na 5.17.0 o
dono reclamou do peso do mod ("o mod de novo está pesando"), então o script passou a
reduzir para 896x504 (16:9, 70% da linear) na cor e 448x252 na máscara de alpha —
o jogo estica de volta pra tela cheia na GPU, de graça. Resultado medido: fx de
68,2 MB -> 32,8 MB (-52%), com SSIM 0,993 no que o jogador vê a 1280x720.

O VideoFrameLoader (5.17.0) aceita qualquer tamanho — ele lê a largura/altura do
arquivo e o VideoFx usa esse tamanho no UV do blit.

O mod lê `fx/<id>/frames/NNNN_c.jpg` (cor, vira TYPE_3BYTE_BGR) e
`NNNN_a.jpg` (alpha, vira TYPE_BYTE_GRAY). O caminho rápido do
VideoFrameLoader depende disso, então aqui NÃO se mexe em tamanho nem em
formato — só se recomprime o JPEG:

  - a COR sai em qualidade 72 com tabelas de Huffman otimizadas e
    codificação progressiva (o mesmo quadro, alguns KB menores);
  - o ALPHA (que é uma máscara quase preto-e-branco) sai em 55: bordas
    macias, ocupa metade;
  - se o arquivo novo ficar MAIOR que o original, o original fica.

Uso:
  python3 arte/otimizar_frames.py            # faz
  python3 arte/otimizar_frames.py --medir    # só mede, sem gravar
"""
import argparse
import io
import os
import sys
import time

from PIL import Image

RAIZ = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    'src', 'main', 'resources', 'assets', 'rptag', 'fx')
Q_COR = 55
Q_ALFA = 55
# (5.17.0) resolução de saída (16:9). 1280x720 original -> 896x504 na cor, 448x252 no alfa.
LARG_COR, ALT_COR = 896, 504
LARG_ALFA, ALT_ALFA = 448, 252


def recompressao(caminho, qualidade):
    """devolve (bytes_novos, bytes_antes) — sem gravar.

    (5.17.0) agora também REDUZ a resolução:
      - cor  -> 896x504 (LANCZOS), q55, 4:2:0, progressivo;
      - alfa -> 448x252 e volta suave pra 896x504 (é máscara, não precisa de detalhe);
      - alfa CHAPADO (todos os pixels iguais, ex.: egípcio/tablet) -> branco sólido q35,
        porque aquele arquivo não carrega informação nenhuma.
    """
    import numpy as np
    antes = os.path.getsize(caminho)
    with Image.open(caminho) as im:
        if caminho.endswith('_a.jpg'):
            im = im.convert('L')
            a = np.asarray(im)
            if a.size and (int(a.max()) - int(a.min())) <= 2:
                im = Image.new('L', (LARG_COR, ALT_COR), 255)
                qualidade = 35
            else:
                im = im.resize((LARG_ALFA, ALT_ALFA), Image.LANCZOS) \
                       .resize((LARG_COR, ALT_COR), Image.LANCZOS)
            buf = io.BytesIO()
            im.save(buf, 'JPEG', quality=qualidade, optimize=True, progressive=True)
        else:
            im = im.convert('RGB').resize((LARG_COR, ALT_COR), Image.LANCZOS)
            buf = io.BytesIO()
            im.save(buf, 'JPEG', quality=qualidade, optimize=True,
                    subsampling=2, progressive=True)
    return buf.getvalue(), antes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--medir', action='store_true', help='só mede, não grava')
    a = ap.parse_args()

    total_antes = total_depois = 0
    trocados = mantidos = 0
    t0 = time.time()
    pastas = sorted(d for d in os.listdir(RAIZ) if os.path.isdir(os.path.join(RAIZ, d, 'frames')))
    for pasta in pastas:
        pdir = os.path.join(RAIZ, pasta, 'frames')
        arqs = sorted(f for f in os.listdir(pdir) if f.endswith('.jpg'))
        antes_p = depois_p = 0
        for i, nome in enumerate(arqs):
            caminho = os.path.join(pdir, nome)
            q = Q_ALFA if nome.endswith('_a.jpg') else Q_COR
            novo, antes = recompressao(caminho, q)
            antes_p += antes
            if len(novo) < antes:
                depois_p += len(novo)
                trocados += 1
                if not a.medir:
                    with open(caminho, 'wb') as f:
                        f.write(novo)
            else:
                depois_p += antes
                mantidos += 1
        total_antes += antes_p
        total_depois += depois_p
        print('%-13s %6.1f MB -> %5.1f MB  (%d quadros)' % (
            pasta, antes_p / 1048576, depois_p / 1048576, len(arqs)))
        sys.stdout.flush()
    print('-' * 52)
    print('TOTAL %.1f MB -> %.1f MB  (-%.0f%%)  %d trocados, %d mantidos  (%.1f s)' % (
        total_antes / 1048576, total_depois / 1048576,
        100 * (1 - total_depois / max(1, total_antes)), trocados, mantidos, time.time() - t0))


if __name__ == '__main__':
    main()
