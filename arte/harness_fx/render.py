import struct, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONTE = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
_cache = {}

def fonte(px):
    px = max(4, int(round(px)))
    if px not in _cache:
        _cache[px] = ImageFont.truetype(FONTE, px)
    return _cache[px]

def ler(caminho):
    d = open(caminho, 'rb').read()
    w, h = struct.unpack('>ii', d[:8])
    o = 8
    fb = np.frombuffer(d, dtype='>f4', count=w*h*3, offset=o).astype(np.float32).reshape(h, w, 3)
    o += w*h*3*4
    (n,) = struct.unpack('>i', d[o:o+4]); o += 4
    textos = []
    for _ in range(n):
        (l,) = struct.unpack('>i', d[o:o+4]); o += 4
        s = d[o:o+l].decode('utf-8'); o += l
        x, y, esc = struct.unpack('>fff', d[o:o+12]); o += 12
        (cor,) = struct.unpack('>i', d[o:o+4]); o += 4
        (sombra,) = struct.unpack('>?', d[o:o+1]); o += 1
        textos.append((s, x, y, esc, cor, sombra))
    return w, h, fb, textos

def compor(caminho, ampliar=2):
    w, h, fb, textos = ler(caminho)
    im = Image.fromarray((np.clip(fb, 0, 1) * 255).astype(np.uint8), 'RGB')
    # texto: pinta num layer RGBA com a cor/alfa pedidos
    cam = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(cam)
    for s, x, y, esc, cor, sombra in textos:
        a = (cor >> 24) & 255
        if a == 0: continue
        rgb = ((cor >> 16) & 255, (cor >> 8) & 255, cor & 255)
        px = 7.0 * esc * 1.05
        f = fonte(px)
        if sombra:
            d.text((x + esc, y + esc), s, font=f, fill=(rgb[0] // 4, rgb[1] // 4, rgb[2] // 4, a))
        d.text((x, y), s, font=f, fill=(rgb[0], rgb[1], rgb[2], a))
    im = Image.alpha_composite(im.convert('RGBA'), cam).convert('RGB')
    if ampliar > 1:
        im = im.resize((w * ampliar, h * ampliar), Image.LANCZOS)
    return im

if __name__ == '__main__':
    for p in sys.argv[1:]:
        im = compor(p)
        saida = p.replace('.bin', '.png')
        im.save(saida)
        print(saida, im.size)
