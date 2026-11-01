#!/usr/bin/env python3
"""mp4 -> formato de vídeo do mod (fx/<id>/frames/NNNN_c.jpg + NNNN_a.jpg + fx.json).

O mod NÃO toca mp4: o VideoFx lê pares de JPEG por quadro
  NNNN_c.jpg = cor (1280x720, RGB)
  NNNN_a.jpg = máscara de alpha (1280x720, cinza)
e um fx.json com fps/quadros/tamanho/mistura.

Uso:
  python3 arte/mp4_para_fx.py video.mp4 <id> [--fps 24] [--blend normal|aditivo]
                                       [--alpha opaco|luminancia] [--saida DIR]
                                       [--max-quadros N]

Exemplos:
  # vídeo atrás do player no tablet (opaco, 24 fps)
  python3 arte/mp4_para_fx.py p5z3m3.mp4 tablet_fundo --fps 24

  # um aviso que se funde com a tela (alpha = brilho do quadro)
  python3 arte/mp4_para_fx.py aviso.mp4 aviso_x --alpha luminancia --blend aditivo
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile

LARGURA, ALTURA = 1280, 720


def ffmpeg():
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        pass
    exe = shutil.which('ffmpeg')
    if not exe:
        sys.exit('ffmpeg não encontrado (pip install imageio-ffmpeg)')
    return exe


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('video')
    ap.add_argument('id', help='nome da pasta em assets/rptag/fx/<id>')
    ap.add_argument('--fps', type=int, default=24)
    ap.add_argument('--blend', choices=['normal', 'aditivo'], default='normal')
    ap.add_argument('--alpha', choices=['opaco', 'luminancia'], default='opaco')
    ap.add_argument('--saida', default=None)
    ap.add_argument('--max-quadros', type=int, default=0)
    ap.add_argument('--qualidade', type=int, default=88)
    ap.add_argument('--zoom', type=float, default=1.0,
                    help='recorta o centro e amplia (tira marca d\'agua do canto e aproxima)')
    ap.add_argument('--centro', type=float, default=0.5,
                    help='onde esta o centro do que interessa (0.5 = no meio)')
    a = ap.parse_args()

    if not os.path.exists(a.video):
        sys.exit('arquivo não encontrado: ' + a.video)
    if os.path.getsize(a.video) == 0:
        sys.exit('o arquivo está com 0 byte (upload não completou)')

    saida = a.saida or os.path.join('src/main/resources/assets/rptag/fx', a.id)
    frames_dir = os.path.join(saida, 'frames')
    if os.path.isdir(frames_dir):
        shutil.rmtree(frames_dir)
    os.makedirs(frames_dir, exist_ok=True)

    # no disco grande do projeto (/tmp e tmpfs de 992 MB: 120 quadros 1280x720 estouram)
    tmp = tempfile.mkdtemp(prefix='.mp4fx_', dir=os.path.abspath(saida))
    paleta = os.path.join(tmp, 'f_%04d.jpg')   # JPEG: PNG de 1280x720 nao cabe no /tmp
    recorte = ''
    if a.zoom > 1.001:
        cw, ch = int(LARGURA / a.zoom) & ~1, int(ALTURA / a.zoom) & ~1
        cx = int(round(a.centro * LARGURA - cw / 2))
        cx = max(0, min(LARGURA - cw, cx))
        cy = (ALTURA - ch) // 2
        recorte = f'crop={cw}:{ch}:{cx}:{cy},'
    filtro = (f'fps={a.fps},' + recorte +
              f'scale={LARGURA}:{ALTURA}:force_original_aspect_ratio=decrease:flags=lanczos,'
              f'pad={LARGURA}:{ALTURA}:(ow-iw)/2:(oh-ih)/2:black')
    cmd = [ffmpeg(), '-y', '-hide_banner', '-loglevel', 'error',
           '-i', a.video, '-vf', filtro, paleta]
    if a.max_quadros > 0:
        cmd += ['-frames:v', str(a.max_quadros)]
    subprocess.run(cmd, check=True)

    from PIL import Image
    arquivos = sorted(f for f in os.listdir(tmp) if f.endswith(('.jpg', '.png')))
    if not arquivos:
        sys.exit('nenhum quadro extraído — o vídeo está corrompido?')

    for i, nome in enumerate(arquivos):
        im = Image.open(os.path.join(tmp, nome)).convert('RGB')
        base = os.path.join(frames_dir, '%04d' % i)
        im.save(base + '_c.jpg', quality=a.qualidade, optimize=True, subsampling=2)
        if a.alpha == 'luminancia':
            alfa = im.convert('L')
        else:
            alfa = Image.new('L', im.size, 255)
        alfa.save(base + '_a.jpg', quality=a.qualidade, optimize=True)
    shutil.rmtree(tmp, ignore_errors=True)

    meta = {
        'id': a.id,
        'fps': a.fps,
        'frames': len(arquivos),
        'width': LARGURA,
        'height': ALTURA,
        'blend': a.blend,
        'markers': [],
    }
    with open(os.path.join(saida, 'fx.json'), 'w', encoding='utf-8') as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)

    total = sum(os.path.getsize(os.path.join(frames_dir, f)) for f in os.listdir(frames_dir))
    print('ok: %d quadros @ %d fps (%.2f s) em %s' % (len(arquivos), a.fps,
                                                     len(arquivos) / a.fps, saida))
    print('tamanho das imagens: %.1f MB' % (total / 1048576.0))
    if total > 60 * 1048576:
        print('AVISO: passou de 60 MB — baixe o --fps ou o --qualidade para o jar não inflar')


if __name__ == '__main__':
    main()
