#!/usr/bin/env python3
"""Otimiza os quadros dos avisos em vídeo SEM mudar a resolução (1280x720).

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
Q_COR = 72
Q_ALFA = 55


def recompressao(caminho, qualidade):
    """devolve (bytes_novos, bytes_antes) — sem gravar."""
    antes = os.path.getsize(caminho)
    with Image.open(caminho) as im:
        modo = 'L' if qualidade == Q_ALFA else 'RGB'
        im = im.convert(modo)
        buf = io.BytesIO()
        im.save(buf, 'JPEG', quality=qualidade, optimize=True, progressive=True)
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
