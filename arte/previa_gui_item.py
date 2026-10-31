#!/usr/bin/env python3
"""(5.3.1) Previa do ITEM no INVENTARIO, com a matematica do jogo de verdade.

O que o 1.21.1 faz para um item `builtin/entity` (descompilado):

    ItemRenderer:  modelo.getTransforms().getTransform(ctx).apply(leftHand, pose)
                   pose.translate(-0.5, -0.5, -0.5)
                   BEWLR.renderByItem(...)          <-- nossa mascara
    ItemTransform (ItemTransform$Deserializer):
                   translation -> translation * 0.0625  (1/16 de bloco, clamp +-5)
                   scale       -> multiplicador DIRETO    (clamp +-4)
                   ordem: translate -> rotate(X,Y,Z) -> scale

No inventario ainda tem o `pose.scale(16,-16,16)` do GuiGraphics, ou seja:
1 unidade de bloco = 16 px de tela. Foi por isso que "translation 48" (= 3
blocos = 48 px) jogava a mascara pra fora do slot.

Uso: python3 arte/previa_gui_item.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(RAIZ, 'arte'))
from render_mascara_offline import carregar, render, mul, escala, rot_x, rot_y, rot_z  # noqa: E402

A = os.path.join(RAIZ, 'src/main/resources/assets/rptag')
MALHA = carregar(os.path.join(A, 'geo/mascara_bb.bin'))
TEX = np.array(Image.open(os.path.join(A, 'textures/entity/mascara_bb.png')).convert('RGB'))
JSON = os.path.join(A, 'models/item/mascara_pedra.json')

# (5.3.1) o `display` BUGADO do 5.3.0, para o antes/depois. Em PIXELS: o
# ItemTransform lê translation * 1/16 (clamp +-5 blocos) e scale como
# multiplicador (clamp 4) -> 48 virava 3 BLOCOS e 16 virava 4x.
JSON_ANTIGO = os.path.join(RAIZ, 'previa/_display_530_bugado.json')

# centro da bbox -> centro do cubo 0..1 (o `pose.translate(offX,offY,offZ)` do Java)
X0, X1 = MALHA['pos'][:, 0].min(), MALHA['pos'][:, 0].max()
Y0, Y1 = MALHA['pos'][:, 1].min(), MALHA['pos'][:, 1].max()
Z0, Z1 = MALHA['pos'][:, 2].min(), MALHA['pos'][:, 2].max()
OFF = (8 - (X0 + X1) / 2, 8 - (Y0 + Y1) / 2, 8 - (Z0 + Z1) / 2)


def transladar(v):
    m = np.eye(4)
    m[:3, 3] = v
    return m


def matriz(ctx_nome, arquivo=None):
    """display[ctx] x translate(-0.5) x scale(1/16) x translate(off)."""
    d = json.load(open(arquivo or JSON)).get('display', {}).get(ctx_nome, {})
    rot = d.get('rotation', [0, 0, 0])
    tra = [v * 0.0625 for v in d.get('translation', [0, 0, 0])]      # 1/16 de bloco
    esc = d.get('scale', [1, 1, 1])                                   # multiplicador
    return mul(
        transladar(tra), rot_x(rot[0]), rot_y(rot[1]), rot_z(rot[2]),
        transladar((-0.5, -0.5, -0.5)),
        escala(1 / 16), transladar(OFF),
    )


def icone(ctx='gui', px=128, arquivo=None):
    """Desenha o item num slot de 16x16 px ampliado `px`/16 vezes."""
    # 1 bloco ocupa os `px` da imagem (o slot de 16 px aparece ampliado).
    # `dist` grande + zoom proporcional = quase ortografico, como o inventario.
    dist = 60.0
    zoom = dist / 0.9                      # f/dist = W  =>  1 bloco = W px
    return render(MALHA, matriz(ctx, arquivo), TEX, W=px, H=px, zoom=zoom,
                  fundo=(139, 139, 139), dist=dist)


def render_blocos(ctx='gui', blocos=4.0, k=16.0, arquivo=None):
    """Renderiza o item com 1 BLOCO = 16*k px de tela (k = zoom do painel)."""
    n = int(blocos * 16 * k)
    dist = 60.0
    return render(MALHA, matriz(ctx, arquivo), TEX, W=n, H=n, zoom=dist / 0.9,
                  fundo=(139, 139, 139), dist=dist)


def painel(px=256, arquivo=None, extra_topo=64):
    """Inventario de mentira (9x3 slots), com a mascara ONDE ELA CAI.

    O centro do item vem do `display` — a `translation` e em 1/16 de bloco
    (0,0625) e sobe no eixo +Y. Se foi escrita em pixels (bug do 5.3.0, "48"),
    o centro cai 3 BLOCOS acima do slot: da para ver o item invadindo a area
    de cima e saindo do slot, que foi o que o dono fotografou.
    """
    k = px / 16.0                                   # zoom do painel
    W, H = int(9 * 18 * k), int((3 * 18 + extra_topo) * k)
    img = Image.new('RGB', (W, H), (198, 198, 198))
    d = ImageDraw.Draw(img)
    y_off = int(extra_topo * k)
    for lin in range(3):
        for col in range(9):
            x, y = col * 18 * k, y_off + lin * 18 * k
            d.rectangle([x, y, x + 16 * k, y + 16 * k], fill=(139, 139, 139))
            d.rectangle([x, y, x + 16 * k, y + 16 * k], outline=(55, 55, 55))

    cfg = json.load(open(arquivo or JSON)).get('display', {}).get('gui', {})
    ty = cfg.get('translation', [0, 0, 0])[1] * 0.0625 * 16 * k    # blocos -> px de tela

    col, lin = 4, 1
    cx = col * 18 * k + 8 * k
    cy = y_off + lin * 18 * k + 8 * k - ty
    ic = render_blocos('gui', 4.0, k, arquivo)
    img.paste(ic, (int(cx - ic.width / 2), int(cy - ic.height / 2)))
    d.rectangle([col * 18 * k, y_off + lin * 18 * k,
                 col * 18 * k + 16 * k, y_off + lin * 18 * k + 16 * k],
                outline=(255, 80, 60), width=3)
    return img


if __name__ == '__main__':
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    Fp = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 12)
    imgs = [('gui (inventario)', icone('gui')),
            ('ground (no chao)', icone('ground')),
            ('thirdperson', icone('thirdperson_righthand')),
            ('firstperson', icone('firstperson_righthand'))]
    g = Image.new('RGB', (4 * 136 + 8, 128 + 46), (28, 30, 38))
    d = ImageDraw.Draw(g)
    d.text((8, 6), '5.3.1 — item mascara nos 4 contextos (slot desenhado, 16x16 px)', font=F, fill=(190, 235, 190))
    for i, (nome, im) in enumerate(imgs):
        x = 8 + i * 136
        g.paste(im.convert('RGB'), (x, 30))
        d.rectangle([x - 1, 29, x + 128, 158], outline=(90, 90, 90))
        d.text((x, 160), nome, font=Fp, fill=(170, 180, 200))
    saida = os.path.join(RAIZ, 'previa/mascara_531_gui.png')
    os.makedirs(os.path.dirname(saida), exist_ok=True)
    g.save(saida)
    print('gravado', saida)
    # ---- antes (5.3.0, display em pixels) x depois (5.3.1, unidades do jogo)
    import json as _j
    _j.dump({'display': {'gui': {'rotation': [30, 45, 0],
                                 'translation': [0, 48, 0], 'scale': [16, 16, 16]}}},
            open(JSON_ANTIGO, 'w'))
    ant = painel(arquivo=JSON_ANTIGO)
    dep = painel()
    W, H = ant.size
    par = Image.new('RGB', (W, 2 * H + 62), (28, 30, 38))
    dd = ImageDraw.Draw(par)
    F2 = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 17)
    dd.text((10, 8), '5.3.0 (BUGADO): display em PIXELS -> 48 = 3 blocos, escala 16 = teto 4',
            font=F2, fill=(255, 150, 130))
    par.paste(ant, (0, 30))
    dd.text((10, H + 38), '5.3.1: display em unidades do jogo (cranio vanilla) + malha centrada',
            font=F2, fill=(150, 235, 160))
    par.paste(dep, (0, H + 60))
    par.save(os.path.join(RAIZ, 'previa/mascara_531_inventario.png'))
    os.remove(JSON_ANTIGO)
    print('gravado previa/mascara_531_inventario.png')
    # numeros pra conferir o encaixe no slot
    p = MALHA['pos'] + np.array(OFF)
    alto = (p[:, 1].max() - p[:, 1].min()) / 16
    larg = (p[:, 0].max() - p[:, 0].min()) / 16
    d_gui = json.load(open(JSON))['display']['gui']['scale'][0]
    print(f'altura {alto:.2f} bloco x largura {larg:.2f} bloco; no slot: '
          f'{alto * 16 * d_gui:.1f} px de altura (slot = 16 px)')
