"""Previa (mock) do aviso DIVINO procedural antigo — 4.9.1.

Reproduz a geometria do DivinoAvisoFx.java (o que voltou na 4.9.1) num quadro
em t = 1.6 s: vinheta escura, pilar de luz, 2 camadas de raios, halo com aneis,
tiques runicos, 2 hexagramas, losangos orbitando, asas (2x9 penas), faiscas em
estrela, ornamento e o titulo dourado com glow.

O texto no jogo e desenhado pela fonte do Minecraft; aqui e uma aproximacao
(DejaVu Serif Bold) so pra dar a composicao.

uso: python arte/previa_divino.py            -> previa/divino_4_9_1.png
"""
import math
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / 'previa'
W, H = 1280, 720
T = 1.6
DEG = math.pi / 180
TAU = math.tau

GOLD = (0xFF, 0xCD, 0x60)
CREAM = (0xFF, 0xF0, 0xBE)
WHITE = (0xFF, 0xFF, 0xFF)
DARK = (0x0A, 0x06, 0x00)


def cl(x):
    return 0.0 if x < 0 else min(1.0, x)


def ss(a, b, x):
    u = cl((x - a) / (b - a))
    return u * u * (3 - 2 * u)


def eo(x):
    x = cl(x)
    return 1 - (1 - x) ** 3


def fr(x):
    return x - math.floor(x)


def rnd(i, s):
    return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)


def camada():
    """RGBA transparente + draw com blend aditivo manual no final."""
    return Image.new('RGBA', (W, H), (0, 0, 0, 0))


def cor(rgb, a):
    return rgb + (int(max(0.0, min(1.0, a)) * 255),)


def aditivo(base, brilho):
    """Soma o brilho (blend aditivo, igual ao SRC_ALPHA/ONE do jogo)."""
    return Image.fromarray(_somar(base, brilho))


class Cena:
    """Acumula camadas aditivas: cada desenho vai numa camada propria."""

    def __init__(self, base):
        self.base = base
        self.brilho = camada()
        self.d = ImageDraw.Draw(self.brilho)

    def nova(self):
        self.base = aditivo(self.base, self.brilho)
        self.brilho = camada()
        self.d = ImageDraw.Draw(self.brilho)

    def fim(self):
        return aditivo(self.base, self.brilho)


def _somar(base, brilho):
    import numpy as np
    b = np.asarray(base, dtype=np.float32)
    g = np.asarray(brilho, dtype=np.float32)
    alfa = g[:, :, 3:4] / 255.0
    out = b[:, :, :3] + g[:, :, :3] * alfa
    out = np.clip(out, 0, 255).astype(np.uint8)
    return np.dstack([out, b[:, :, 3:4].astype(np.uint8)])


def main():
    k = min(W, H) / 240.0
    cx, cy = W / 2.0, H * 0.42
    rev = eo(cl((T - 0.1) / 1.1))
    env = ss(0, 0.5, T) * (1 - ss(6.0 - 1.2, 6.0, T))

    # 1) fundo: vinheta escura (blend normal sobre um "mundo" escuro)
    fundo = Image.new('RGBA', (W, H), (8, 8, 14, 255))
    vin = camada()
    dv = ImageDraw.Draw(vin)
    d = min(W, H) * 0.30
    va = 0.55 * env
    passos = 120
    for i in range(passos):
        u = i / (passos - 1.0)
        a = int(255 * va * (1 - u))
        margem = d * u
        dv.rectangle([0, 0, W, margem], fill=cor(DARK, va * (1 - u)))
        dv.rectangle([0, H - margem, W, H], fill=cor(DARK, va * (1 - u)))
        dv.rectangle([0, 0, margem, H], fill=cor(DARK, va * (1 - u)))
        dv.rectangle([W - margem, 0, W, H], fill=cor(DARK, va * (1 - u)))
    fundo = Image.alpha_composite(fundo, vin)

    cena = Cena(fundo)
    bd = cena.d

    # 2) pilar de luz
    yp = cy * eo(cl(T / 0.6))
    for s in (-1, 1):
        pw = 26 * k * rev * s
        cw = 5 * k * s
        bd.polygon([(cx + pw, 0), (cx, 0), (cx, yp), (cx + pw, yp)], fill=cor(CREAM, 0.30 * env))
        bd.polygon([(cx + cw, 0), (cx, 0), (cx, yp), (cx + cw, yp)], fill=cor(WHITE, 0.45 * env))

    cena.nova(); bd = cena.d
    # 3) raios (2 camadas girando em sentidos opostos)
    diag = math.hypot(W, H)
    rl = diag * (0.55 + 0.45 * rev) * (0.7 + 0.3 * env)

    def raios(n, comp, rot, ha, a, rgb):
        for i in range(n):
            ang = rot + i * TAU / n
            L = comp * (1.0 if i % 2 == 0 else 0.68) * (0.75 + 0.25 * math.sin(T * 2.3 + i * 1.7))
            bd.polygon([(cx, cy),
                        (cx + math.cos(ang - ha) * L, cy + math.sin(ang - ha) * L),
                        (cx + math.cos(ang + ha) * L, cy + math.sin(ang + ha) * L)], fill=cor(rgb, a))

    raios(16, rl, T * 4 * DEG, 0.028, 0.40 * env, GOLD)
    raios(12, rl * 0.9, -T * 6 * DEG, 0.010, 0.50 * env, CREAM)

    cena.nova(); bd = cena.d
    # 4) halo + aneis
    r1 = 62 * k * rev

    def disco(r, a, rgb):
        # varias cascas concentricas: o alfa cai do centro pra borda (fade radial)
        camadas_d = 28
        for c in range(camadas_d):
            raio = r * (1 - c / camadas_d)
            al = a / camadas_d * 2.2
            passos_d = 72
            for i in range(passos_d):
                a0 = i * TAU / passos_d
                a1 = (i + 1) * TAU / passos_d
                bd.polygon([(cx, cy), (cx + math.cos(a0) * raio, cy + math.sin(a0) * raio),
                            (cx + math.cos(a1) * raio, cy + math.sin(a1) * raio)], fill=cor(rgb, al))

    def anel(r, n, w, rot, dash, rgb, a):
        for i in range(n):
            if dash > 0 and i % dash == dash - 1:
                continue
            a0 = rot + i * TAU / n
            a1 = rot + (i + 1) * TAU / n
            bd.line([(cx + math.cos(a0) * r, cy + math.sin(a0) * r),
                     (cx + math.cos(a1) * r, cy + math.sin(a1) * r)], fill=cor(rgb, a), width=max(1, int(w)))

    disco(r1 * 1.15, 0.40 * env, GOLD)
    anel(r1, 90, 2, 0, 0, GOLD, 0.90 * env)
    anel(r1 * 0.78, 72, 1, 0, 0, CREAM, 0.55 * env)
    anel(r1 * 1.28, 96, 1, T * 5 * DEG, 4, GOLD, 0.70 * env)

    # tiques runicos
    for i in range(48):
        a = -T * 10 * DEG + i * TAU / 48
        r0 = r1 * 1.28 + 2 * k
        r2 = r0 + (9 if (i & 3) == 0 else 4) * k
        bd.line([(cx + math.cos(a) * r0, cy + math.sin(a) * r0),
                 (cx + math.cos(a) * r2, cy + math.sin(a) * r2)], fill=cor(CREAM, 0.7 * env), width=1)

    # hexagrama
    for q in range(2):
        rot = (1 if q == 0 else -1) * T * 8 * DEG + q * 60 * DEG
        rr = r1 * 0.78
        for i in range(3):
            a0 = rot + i * TAU / 3
            a1 = rot + (i + 1) * TAU / 3
            bd.line([(cx + math.cos(a0) * rr, cy + math.sin(a0) * rr),
                     (cx + math.cos(a1) * rr, cy + math.sin(a1) * rr)], fill=cor(GOLD, 0.45 * env), width=1)

    # losangos orbitando
    for i in range(4):
        a = T * 20 * DEG + i * TAU / 4
        px, py = cx + math.cos(a) * r1, cy + math.sin(a) * r1
        s = 3 * k
        bd.polygon([(px - s, py), (px, py - s), (px + s, py), (px, py + s)], fill=cor(WHITE, 0.9 * env))

    cena.nova(); bd = cena.d
    # 5) asas (2 x 9 penas)
    wa = 0.30 * env * ss(0.3, 1.1, T)

    def asa(ax, ay, side, comp, a):
        n = 9
        for i in range(n):
            f = i / (n - 1.0)
            th = (-62 + 90 * f) * DEG + 6 * DEG * math.sin(T * 2.2 + i * 0.35)
            dx, dy = side * math.cos(th), math.sin(th)
            L = comp * (1 - 0.45 * f)
            wd = 0.12 * L
            mx, my = ax + dx * L * 0.35, ay + dy * L * 0.35
            tx, ty = ax + dx * L, ay + dy * L
            px, py = -dy * wd, dx * wd
            bd.line([(ax, ay), (tx, ty)], fill=cor(CREAM, a * 0.55), width=max(1, int(0.10 * L)))
            bd.polygon([(mx + px, my + py), (tx, ty), (mx - px, my - py)], fill=cor(GOLD, a * 0.35))

    for s in (-1, 1):
        asa(cx + s * r1 * 1.05, cy, s, r1 * 2.1 * rev, wa)

    cena.nova(); bd = cena.d
    # 6) faiscas em estrela
    for i in range(50):
        p = fr(T * (0.05 + 0.08 * rnd(i, 2)) + rnd(i, 3))
        px = rnd(i, 1) * W + math.sin(T * 1.3 + i) * 12
        py = H * (1.05 - p * 1.1)
        sz = (1.2 + 2.4 * rnd(i, 4)) * k * 0.9
        al = env * (0.5 + 0.5 * math.sin(T * 4 + i * 2.1)) * math.sin(math.pi * p)
        bd.polygon([(px - sz * 0.28, py), (px, py - sz), (px + sz * 0.28, py), (px, py + sz)], fill=cor(CREAM, al))
        bd.polygon([(px - sz, py), (px, py - sz * 0.28), (px + sz, py), (px, py + sz * 0.28)], fill=cor(CREAM, al))

    cena.nova(); bd = cena.d
    # 7) ornamento sob o titulo
    S = k * 1.9
    oy = cy + 5.5 * S + 6 * k
    ol = 70 * k * rev
    ao = 0.9 * env * ss(0.8, 1.4, T)
    bd.line([(cx - ol, oy), (cx, oy)], fill=cor(GOLD, ao), width=2)
    bd.line([(cx, oy), (cx + ol, oy)], fill=cor(GOLD, ao), width=2)
    bd.polygon([(cx - 4 * k, oy), (cx, oy - 4 * k), (cx + 4 * k, oy), (cx, oy + 4 * k)],
               fill=cor(WHITE, 0.95 * env * ss(0.8, 1.4, T)))

    fundo = cena.fim()

    # 8) texto (aproximacao: fonte serifada dourada com glow)
    try:
        fonte = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf', int(30 * k))
    except OSError:
        fonte = ImageFont.load_default()
    txt = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    td = ImageDraw.Draw(txt)
    titulo = 'AVISO'
    larg = td.textlength(titulo, font=fonte)
    tx = cx - larg / 2
    ty = cy - 4 * S - 16 * k
    td.text((tx, ty), titulo, font=fonte, fill=cor(CREAM, env))
    glow = txt.filter(ImageFilter.GaussianBlur(6))
    fundo = aditivo(fundo, glow)
    fundo = Image.alpha_composite(fundo, txt)

    # mensagem
    try:
        fm = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf', int(11 * k))
    except OSError:
        fm = fonte
    msg = 'QUE A LUZ TE GUIE'
    lm = td.textlength(msg, font=fm)
    td.text((cx - lm / 2, cy + 25 * k), msg, font=fm, fill=cor(CREAM, ss(1.0, 1.6, T) * env))
    fundo = Image.alpha_composite(fundo, txt)

    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'divino_4_9_1.png'
    fundo.convert('RGB').save(destino)
    print(destino, destino.stat().st_size)


if __name__ == '__main__':
    main()
