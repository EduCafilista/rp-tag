#!/usr/bin/env python3
"""Antes × depois da otimização dos quadros (5.10.0).

Monta dois quadros de aviso lado a lado (original do git × recomprimido),
compostos sobre o mesmo fundo escuro, no tamanho REAL de tela (1:1) e com um
recorte ampliado 3x pra inspecionar pixel. Embaixo, o peso de cada arquivo.
"""
import os

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FX = os.path.join(RAIZ, 'src', 'main', 'resources', 'assets', 'rptag', 'fx')
ORIG = '/tmp/orig'
SAIDA = os.path.join(RAIZ, 'previa', 'otimizacao_5.10.0.png')
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

CASOS = [('egipcio/frames/0090', (470, 190, 810, 445)),
         ('gelo/frames/0072', (330, 200, 670, 455))]
ESCALA = 3


def composto(base, origem):
    c = Image.open(os.path.join(origem, base + '_c.jpg')).convert('RGB')
    a = Image.open(os.path.join(origem, base + '_a.jpg')).convert('L')
    fundo = Image.new('RGB', c.size, (18, 16, 14))
    fundo.paste(c, (0, 0), a)
    return fundo


def main():
    margem, topo = 14, 60
    larg, alt = 640, 360
    rec_w, rec_h = (CASOS[0][1][2] - CASOS[0][1][0]) * ESCALA, (CASOS[0][1][3] - CASOS[0][1][1]) * ESCALA
    folha = Image.new('RGB', (margem * 3 + larg * 2, margem + topo + (alt + 34 + rec_h + 26) * len(CASOS)),
                      (10, 9, 8))
    d = ImageDraw.Draw(folha)
    f14 = ImageFont.truetype(FONT, 14)
    f11 = ImageFont.truetype(FONT_R, 11)
    f13 = ImageFont.truetype(FONT, 13)
    d.text((margem, 16), 'OTIMIZAÇÃO DOS QUADROS 5.10.0 — mesma resolução (1280×720), '
                         'só recompressão', font=f13, fill=(232, 214, 170))

    y = margem + topo
    for base, caixa in CASOS:
        antes = composto(base, ORIG)
        depois = composto(base, FX)
        pa = os.path.getsize(os.path.join(ORIG, base + '_c.jpg')) + \
            os.path.getsize(os.path.join(ORIG, base + '_a.jpg'))
        pd = os.path.getsize(os.path.join(FX, base + '_c.jpg')) + \
            os.path.getsize(os.path.join(FX, base + '_a.jpg'))
        for i, (im, rotulo, peso) in enumerate([(antes, 'ANTES', pa), (depois, 'DEPOIS', pd)]):
            x = margem + i * (larg + margem)
            folha.paste(im.resize((larg, alt), Image.LANCZOS), (x, y))
            d.rectangle([x - 1, y - 1, x + larg, y + alt], outline=(70, 56, 30))
            d.text((x, y + alt + 4), '%s — %s  (%.1f KB por quadro)' % (rotulo, base, peso / 1024),
                   font=f11, fill=(190, 172, 132))
            rec = im.crop(caixa).resize((rec_w, rec_h), Image.NEAREST)
            folha.paste(rec, (x, y + alt + 26))
            d.rectangle([x - 1, y + alt + 25, x + rec_w, y + alt + 26 + rec_h],
                        outline=(70, 56, 30))
            d.text((x + 4, y + alt + 30), 'recorte 3× (pixel a pixel)', font=f11,
                   fill=(150, 136, 104))
        y += alt + 34 + rec_h + 26
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    folha.save(SAIDA)
    print('ok', SAIDA, folha.size)


if __name__ == '__main__':
    main()
