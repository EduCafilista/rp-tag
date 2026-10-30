"""MASCARA DE PEDRA (JoJo) — textura 64x64 do item/fantasia + previa.

Desenha as 4 regioes que o modelo usa (cada face do modelo aponta para uma
delas por UV):

    0,0   .. 32,32   ROSTO  — a cara esculpida (olhos fundos, nariz, boca, presas)
    32,0  .. 64,32   PEDRA  — pedra lascada com rachaduras (placas laterais)
    0,32  .. 32,64   ESCURO — vao das orbitas / boca / sombra interna
    32,32 .. 64,64   CANTO  — pedra escura das bordas e do fundo

A mascara e a do JoJo (Phantom Blood): pedra clara, testa marcada por uma
linha vertical, sobrancelha grossa e caida (raiva), olhos fundos, nariz
comprido, boca grande virada para baixo e DUAS PRESAS compridas subindo.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/item/mascara_pedra.png'
PREVIA = RAIZ / 'previa'

BASE = (0xB4, 0xAA, 0x99)
CLARO = (0xD9, 0xD1, 0xC3)
MEDIO = (0x9C, 0x92, 0x82)
ESCURO = (0x6E, 0x65, 0x58)
RACHA = (0x2E, 0x28, 0x22)
OSSO = (0xE6, 0xDF, 0xD2)


def manchas(d, x0, y0, x1, y1, rng, qtd=90):
    """pedra: manchas claras/escuras em pixel (nada de gradiente suave)."""
    for _ in range(qtd):
        x = rng.randint(x0, x1 - 1)
        y = rng.randint(y0, y1 - 1)
        c = rng.choice((CLARO, MEDIO, ESCURO, BASE))
        if c == BASE:
            continue
        d.point((x, y), fill=c)


def rachadura(d, x0, y0, x1, y1, rng, passos=9, grossura=1, cor=RACHA):
    """rachadura quebrada (linha reta nunca: sempre em ziguezague)."""
    x, y = rng.randint(x0, x1), rng.randint(y0, y1)
    ang = rng.uniform(0, math.tau)
    for _ in range(passos):
        ang += rng.uniform(-0.9, 0.9)
        nx = max(x0, min(x1, x + math.cos(ang) * rng.uniform(1.5, 3.5)))
        ny = max(y0, min(y1, y + math.sin(ang) * rng.uniform(1.5, 3.5)))
        d.line([(x, y), (nx, ny)], fill=cor, width=grossura)
        # o "vinco" da rachadura tem um lado claro (luz batendo na lasca)
        d.line([(x + 1, y), (nx + 1, ny)], fill=MEDIO, width=1) if grossura == 1 else None
        x, y = nx, ny


def rosto(d, rng):
    """a cara esculpida, dentro de 0..32 x 0..32."""
    d.rectangle([0, 0, 31, 31], fill=BASE)
    manchas(d, 0, 0, 32, 32, rng, 140)

    # ---- testa: a linha vertical de pedra no meio (a "costura" da mascara)
    d.line([(16, 0), (15, 5)], fill=ESCURO, width=1)
    d.line([(17, 0), (16, 6)], fill=CLARO, width=1)

    # ---- sobrancelha: barra grossa caida pro meio (raiva) — fica ACIMA dos olhos
    for lado in (-1, 1):
        for i in range(10):
            x = 16 + lado * (2 + i)
            y = 5 + int(i * 0.62)          # desce conforme afasta do centro
            d.point((x, y), fill=ESCURO)
            d.point((x, y + 1), fill=ESCURO)
            d.point((x, y - 1), fill=CLARO)
    # vinco entre as sobrancelhas (a "raiva" da mascara)
    d.line([(14, 7), (13, 12)], fill=ESCURO, width=1)
    d.line([(18, 7), (19, 12)], fill=ESCURO, width=1)

    # ---- olhos fundos (orbitas vazadas)
    for lado in (-1, 1):
        cx = 16 + lado * 7
        d.polygon([(cx - 4, 9), (cx + 4, 11), (cx + 4, 17), (cx - 4, 16)], fill=ESCURO)
        d.polygon([(cx - 3, 10), (cx + 3, 12), (cx + 3, 16), (cx - 3, 15)], fill=(0x14, 0x11, 0x0E, 255))
        d.line([(cx - 2, 12), (cx + 2, 13)], fill=(0xE9, 0xE3, 0xD6, 255), width=1)   # esclerotica
        d.point((cx + lado, 13), fill=(0x0A, 0x08, 0x06, 255))                        # pupila
        d.line([(cx - 4, 17), (cx + 4, 18)], fill=CLARO, width=1)                     # borda de osso

    # ---- nariz comprido (luz na esquerda, sombra na direita)
    d.line([(15, 12), (14, 20)], fill=OSSO, width=1)
    d.line([(17, 12), (17, 20)], fill=ESCURO, width=1)
    d.rectangle([15, 19, 17, 21], fill=MEDIO)
    d.point((14, 21), fill=(0x1A, 0x16, 0x12, 255))     # narina
    d.point((18, 21), fill=(0x1A, 0x16, 0x12, 255))

    # ---- bochechas (osso em cima, sombra embaixo)
    for lado in (-1, 1):
        cx = 16 + lado * 9
        d.ellipse([cx - 4, 17, cx + 3, 22], fill=MEDIO)
        d.arc([cx - 4, 16, cx + 3, 21], 200, 340, fill=OSSO)

    # ---- boca: banda larga e escura, cantos caidos (a mascara "grita")
    d.polygon([(8, 24), (24, 24), (22, 29), (10, 29)], fill=(0x18, 0x14, 0x10, 255))
    d.rectangle([10, 26, 22, 28], fill=(0x0A, 0x08, 0x06, 255))
    d.line([(8, 24), (16, 23), (24, 24)], fill=ESCURO, width=1)    # labio de cima
    d.line([(9, 29), (16, 28), (23, 29)], fill=OSSO, width=1)      # labio de baixo
    d.line([(7, 22), (9, 24)], fill=ESCURO, width=1)               # canto caido esquerdo
    d.line([(25, 22), (23, 24)], fill=ESCURO, width=1)             # canto caido direito
    # as DUAS PRESAS compridas subindo de dentro da boca
    for px in (11, 21):
        d.polygon([(px - 1, 28), (px + 1, 28), (px, 22)], fill=(0xF2, 0xEC, 0xDC, 255))
        d.line([(px, 27), (px, 23)], fill=(0xB8, 0xAE, 0x9A, 255), width=1)
        d.point((px - 1, 28), fill=(0x0A, 0x08, 0x06, 255))

    # ---- queixo
    d.arc([11, 27, 21, 33], 0, 180, fill=CLARO)

    # ---- rachaduras do rosto
    for _ in range(7):
        rachadura(d, 0, 0, 31, 31, rng, passos=8)


def pedra(d, rng, x0, y0):
    """pedra lascada (32x32) na posicao x0,y0."""
    d.rectangle([x0, y0, x0 + 31, y0 + 31], fill=BASE)
    manchas(d, x0, y0, x0 + 32, y0 + 32, rng, 200)
    for _ in range(9):
        rachadura(d, x0, y0, x0 + 31, y0 + 31, rng, passos=10)
    # umas lascas grandes (poligonos)
    for _ in range(4):
        cx = rng.randint(x0 + 4, x0 + 27)
        cy = rng.randint(y0 + 4, y0 + 27)
        pts = [(cx + rng.randint(-7, 7), cy + rng.randint(-7, 7)) for _ in range(5)]
        d.polygon(pts, fill=MEDIO)
        d.line(pts + [pts[0]], fill=RACHA, width=1)


def main():
    rng = random.Random(1987)
    img = Image.new('RGBA', (64, 64), BASE + (255,))
    d = ImageDraw.Draw(img)

    rosto(d, rng)                       # 0,0 .. 32,32
    pedra(d, rng, 32, 0)                # 32,0 .. 64,32
    # voo escuro (orbitas/boca): 0,32 .. 32,64
    d.rectangle([0, 32, 31, 63], fill=(0x14, 0x11, 0x0D, 255))
    for _ in range(40):
        d.point((rng.randint(0, 31), rng.randint(32, 63)), fill=(0x22, 0x1D, 0x17, 255))
    # canto escuro: 32,32 .. 64,64
    d.rectangle([32, 32, 63, 63], fill=ESCURO)
    manchas(d, 32, 32, 64, 64, rng, 120)
    for _ in range(4):
        rachadura(d, 32, 32, 63, 63, rng, passos=7)

    TEX.parent.mkdir(parents=True, exist_ok=True)
    img.save(TEX)
    print(TEX, TEX.stat().st_size)

    # ampliacao pra conferir a arte
    PREVIA.mkdir(exist_ok=True)
    grande = img.resize((512, 512), Image.Resampling.NEAREST)
    destino = PREVIA / 'mascara_textura.png'
    grande.save(destino)
    print(destino, destino.stat().st_size)


if __name__ == '__main__':
    main()
