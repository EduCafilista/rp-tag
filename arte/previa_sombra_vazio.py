#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.16.3) PRÉVIA OFFLINE DA SOMBRA DO VAZIO.

O dono: "aquela animação em cima do portal é uma sombra bizarra que quando vira
algo". Este script desenha exatamente a mesma geometria e as mesmas cores do
`client/VazioAlmasFx.java`, para dar pra conferir sem abrir o jogo.

A sombra é feita só de QUADS de billboard (como no Java). O segredo pra ela
aparecer no escuro do vazio: CONTRA LUZ (um halo violeta bem fraco atrás) e
CONTORNO (a mesma silhueta, 12% maior, em violeta escuro).

Uso:  python3 arte/previa_sombra_vazio.py
Saída: previa/sombra_vazio_5.16.3.png
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

# ------------------------------------------------------------------ paleta (igual ao Java)
CORPO = (0.010, 0.009, 0.016)
CONTORNO = (0.20, 0.13, 0.34)
LUZ = (0.30, 0.18, 0.52)
OLHO = (1.00, 0.90, 0.70)
FUMA = (0.07, 0.05, 0.11)

ALTURA_TOTAL = 2.00          # a sombra é ALTA: 2 blocos
LARGURA, ALTURA = 460, 620
FOV = 70.0


# ------------------------------------------------------------------ vetores
def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def norm(a):
    d = math.sqrt(a[0] ** 2 + a[1] ** 2 + a[2] ** 2)
    if d < 1e-6:
        return (0.0, 1.0, 0.0)
    return (a[0] / d, a[1] / d, a[2] / d)


def clamp(v, a, b):
    return max(a, min(b, v))


def _suave(a, b, t):
    t = clamp(t, 0.0, 1.0)
    t = t * t * (3.0 - 2.0 * t)          # smoothstep
    return a + (b - a) * t


def perfil(u):
    """largura da silhueta em cada altura u (0 = barra da saia, 1 = ponta do capuz)."""
    if u < 0.50:                                        # saia
        return _suave(0.34, 0.20, u / 0.50)
    if u < 0.68:                                        # cintura -> ombros
        return _suave(0.20, 0.26, (u - 0.50) / 0.18)
    if u < 0.76:                                        # ombros -> base do capuz
        return _suave(0.26, 0.21, (u - 0.68) / 0.08)
    v = (u - 0.76) / 0.24                               # capuz: fecha na ponta
    return 0.21 * (1.0 - v) ** 1.6 + 0.012


# ------------------------------------------------------------------ quads (iguais aos do Java)
class Cena:
    def __init__(self):
        self.quads = []

    def quad(self, centro, direita, cima, hw, hh, cor):
        x, y, z = centro
        rx, ry, rz = direita[0] * hw, direita[1] * hw, direita[2] * hw
        ux, uy, uz = cima[0] * hh, cima[1] * hh, cima[2] * hh
        v = [(x - rx - ux, y - ry - uy, z - rz - uz),
             (x + rx - ux, y + ry - uy, z + rz - uz),
             (x + rx + ux, y + ry + uy, z + rz + uz),
             (x - rx + ux, y - ry + uy, z - rz + uz)]
        self.quads.append((v, cor))

    def tira(self, cx, cz, direita, y0, y1, w0, w1, cor):
        rx0, rz0 = direita[0] * w0, direita[2] * w0
        rx1, rz1 = direita[0] * w1, direita[2] * w1
        self.quads.append(([(cx - rx0, y0, cz - rz0),
                            (cx + rx0, y0, cz + rz0),
                            (cx + rx1, y1, cz + rz1),
                            (cx - rx1, y1, cz - rz1)], cor))

    def tira_vertical(self, direita, x, z, y0, y1, hw, cor):
        rx, rz = direita[0] * hw, direita[2] * hw
        self.quads.append(([(x - rx, y0, z - rz),
                            (x + rx, y0, z + rz),
                            (x + rx, y1, z + rz),
                            (x - rx, y1, z - rz)], cor))


def cor(rgb, alpha):
    a = int(clamp(alpha, 0.0, 1.0) * 255)
    r = int(clamp(rgb[0], 0.0, 1.0) * 255)
    g = int(clamp(rgb[1], 0.0, 1.0) * 255)
    b = int(clamp(rgb[2], 0.0, 1.0) * 255)
    return (r, g, b, a)


def halo(cena, c, direita, cima, raio, rgb, alpha, passos=5):
    """halo macio: quads concêntricos com queda quadrática."""
    for i in range(passos):
        f = (i + 1) / float(passos)
        a = alpha * (1.0 - f) ** 2 / passos * 3.0
        cena.quad(c, direita, cima, raio * f, raio * f * 1.15, cor(rgb, a))


def desenha_sombra(cena, cam, pos, indice, dist, t):
    """mesma matemática do desenharAlma() do VazioAlmasFx.java."""
    x, y, z = pos
    fase = indice * 1.7

    subiu = 0.09 * math.sin(t * 0.7 + fase)
    tremor = 0.035 * math.sin(t * 1.3 + fase * 2.0)
    cx = x + tremor
    cy = y + subiu
    cz = z + 0.05 * math.cos(t * 1.1 + fase)

    perto = clamp((140.0 - dist) / 60.0, 0.0, 1.0)
    base = 0.92 * perto
    if base <= 0.02:
        return

    d = norm(sub((cx, cy, cz), cam))
    direita = norm(cross((0.0, 1.0, 0.0), d))
    cima = norm(cross(d, direita))

    # ---- VIRA: de lado afina; de frente a cara aparece
    vira = (t * 0.22 + fase * 0.37) % 1.0
    ang = vira * 6.2832
    afina = 0.14 + 0.86 * abs(math.cos(ang))
    cara = clamp((math.cos(ang) - 0.50) / 0.50, 0.0, 1.0)

    y0 = cy - 0.05
    H = ALTURA_TOTAL
    meio = y0 + H * 0.62

    # 1) CONTRA LUZ — sem isso a sombra preta some no breu do vazio
    halo(cena, (cx, meio, cz), direita, cima, 0.95 * (0.55 + 0.45 * afina),
         LUZ, 0.30 * base, 6)

    # 2) CONTORNO: a mesma silhueta 12% maior, em violeta escuro
    CORPO_ATE = 0.78
    passos = 26
    for i in range(passos):
        u0 = CORPO_ATE * i / float(passos)
        u1 = CORPO_ATE * (i + 1) / float(passos)
        w0 = perfil(u0) * 1.10 * afina
        w1 = perfil(u1) * 1.10 * afina
        cena.tira(cx, cz, direita, y0 + H * u0, y0 + H * u1, w0, w1,
                  cor(CONTORNO, base * 0.55 * (0.5 + 0.5 * u0 / CORPO_ATE)))

    # 3) O CORPO: silhueta quase preta (a "boca do vazio")
    for i in range(passos):
        u0 = CORPO_ATE * i / float(passos)
        u1 = CORPO_ATE * (i + 1) / float(passos)
        w0 = perfil(u0) * afina
        w1 = perfil(u1) * afina
        cena.tira(cx, cz, direita, y0 + H * u0, y0 + H * u1, w0, w1,
                  cor(CORPO, base * 0.99))

    # 4) O CAPUZ (ponta inclinada pra um lado: é o que faz parecer que ela "olha")
    h0 = y0 + H * 0.74
    h1 = y0 + H * 1.00
    inclina = 0.055 * afina
    passos_c = 10
    for i in range(passos_c):
        u0 = h0 + (h1 - h0) * (i / float(passos_c))
        u1 = h0 + (h1 - h0) * ((i + 1) / float(passos_c))
        t0 = i / float(passos_c)
        t1 = (i + 1) / float(passos_c)
        w0 = (0.250 - 0.232 * t0 ** 1.6) * afina
        w1 = (0.250 - 0.232 * t1 ** 1.6) * afina
        ox0 = cx + direita[0] * inclina * t0 * t0
        ox1 = cx + direita[0] * inclina * t1 * t1
        oz0 = cz + direita[2] * inclina * t0 * t0
        oz1 = cz + direita[2] * inclina * t1 * t1
        cena.tira(ox0, oz0, direita, u0, u1, w0 * 1.10, w1 * 1.10,
                  cor(CONTORNO, base * 0.45 * (1.0 - 0.4 * t0)))
        cena.tira(ox0, oz0, direita, u0, u1, w0, w1, cor(CORPO, base * 0.99))

    # a CARA: um vão em V (a boca do capuz) — só aparece quando ela vem de frente
    if cara > 0.02:
        yf0 = y0 + H * 0.795
        yf1 = y0 + H * 0.945
        wf0 = 0.150 * afina
        wf1 = 0.070 * afina
        cena.tira(cx, cz, direita, yf0, yf1, wf0, wf1, cor((0.0, 0.0, 0.0), base * 0.94 * cara))

        # OS OLHOS: duas brasas separadas, no alto do vão
        yo = y0 + H * 0.895
        olho = (0.020 + 0.008 * cara) * (0.40 + 0.60 * afina)
        sep = 0.055 * afina
        for l in (-1, 1):
            ox = cx + direita[0] * (l * sep)
            oz = cz + direita[2] * (l * sep)
            halo(cena, (ox, yo, oz), direita, cima, olho * 4.5, OLHO, 0.45 * base * cara)
            cena.quad((ox, yo, oz), direita, cima, olho, olho * 0.60,
                      cor(OLHO, base * (0.35 + 0.65 * cara)))

        # a boca: fenda fina embaixo do vão
        cena.tira(cx, cz, direita, y0 + H * 0.805, y0 + H * 0.840,
                  0.030 * afina * cara, 0.012 * afina * cara,
                  cor((0.0, 0.0, 0.0), base * cara))

    # 5) FARRAPOS na barra da saia (9 tiras que balançam)
    tiras = 7
    for s in range(tiras):
        f = s / float(tiras - 1) - 0.5
        onda = 0.05 * math.sin(t * 1.1 + fase + s * 1.3)
        px = cx + direita[0] * (f * perfil(0.02) * afina * 1.85 + onda)
        pz = cz + direita[2] * (f * perfil(0.02) * afina * 1.85 + onda)
        comp = (0.26 + 0.30 * (1.0 - abs(f))) * (0.80 + 0.20 * math.sin(t * 0.9 + s * 2.3))
        w = 0.050 * (1.0 - abs(f) * 0.55) * (0.35 + 0.65 * afina)
        cena.tira_vertical(direita, px, pz, y0 + 0.06, y0 - comp * 0.55, w,
                           cor(CORPO, base * 0.9))
        cena.tira_vertical(direita, px, pz, y0 - comp * 0.55, y0 - comp, w * 0.45,
                           cor(CONTORNO, base * 0.30))

    # 6) A FUMAÇA que sobe da ponta do capuz
    topo = y0 + H
    for k in range(5):
        fr = (t * 0.28 + k * 0.21 + fase * 0.1) % 1.0
        ry = topo - 0.06 + fr * 1.0
        rx = cx + 0.11 * math.sin(t * 0.7 + k * 2.0 + fase) * (0.3 + fr)
        rz = cz + 0.11 * math.cos(t * 0.6 + k * 2.3 + fase) * (0.3 + fr)
        sz = 0.075 * (1.0 - fr * 0.55)
        cena.quad((rx, ry, rz), direita, cima, sz, sz * 1.4,
                  cor(FUMA, base * 0.34 * (1.0 - fr)))


# ------------------------------------------------------------------ rasterização
def projeta(p, cam, alvo, larg, alt):
    f = norm(sub(alvo, cam))
    r = norm(cross(f, (0.0, 1.0, 0.0)))
    u = cross(r, f)
    d = sub(p, cam)
    x = sum(d[i] * r[i] for i in range(3))
    y = sum(d[i] * u[i] for i in range(3))
    z = sum(d[i] * f[i] for i in range(3))
    if z < 0.05:
        return None
    fpx = (alt * 0.5) / math.tan(math.radians(FOV) * 0.5)
    return (larg * 0.5 + (x / z) * fpx, alt * 0.5 - (y / z) * fpx, z)


def fundo(larg=LARGURA, alt=ALTURA):
    img = Image.new('RGB', (larg, alt), (3, 2, 6))
    px = img.load()
    for yy in range(alt):
        for xx in range(larg):
            dx = (xx - larg * 0.5) / (larg * 0.5)
            dy = (yy - alt * 0.72) / (alt * 0.72)
            dd = min(1.0, math.sqrt(dx * dx + dy * dy))
            n = (1.0 - dd) ** 2
            px[xx, yy] = (int(3 + 14 * n), int(2 + 9 * n), int(6 + 24 * n))
    return img


def desenha_cena(t, dist=2.6, indice=0, larg=LARGURA, alt=ALTURA):
    cam = (0.0, 1.25, -dist)
    alvo = (0.0, 1.05, 0.0)
    cena = Cena()
    desenha_sombra(cena, cam, (0.0, 0.0, 0.0), indice, dist, t)

    ordenado = sorted(cena.quads,
                      key=lambda q: -sum(sum(abs(v[i] - cam[i]) for i in range(3)) for v in q[0]) / 4.0)
    img = fundo(larg, alt)
    for v, c in ordenado:
        pts = [projeta(p, cam, alvo, larg, alt) for p in v]
        if any(p is None for p in pts):
            continue
        if max(p[0] for p in pts) < 0 or min(p[0] for p in pts) > larg:
            continue
        if max(p[1] for p in pts) < 0 or min(p[1] for p in pts) > alt:
            continue
        camada = Image.new('RGBA', (larg, alt), (c[0], c[1], c[2], 0))
        ImageDraw.Draw(camada).polygon([(p[0], p[1]) for p in pts], fill=c)
        img = Image.alpha_composite(img.convert('RGBA'), camada).convert('RGB')
    return img


def main():
    quadros = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0]
    imgs = [desenha_cena(t) for t in quadros]

    try:
        fonte = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
    except Exception:
        fonte = ImageFont.load_default()

    rot = ['de frente (olhos acesos)', 'começa a virar', 'de lado (lâmina)',
           'virando de volta', 'de frente, de novo', 'quase de lado']
    cols, rows = 3, 2
    W, H = LARGURA * cols, (ALTURA + 34) * rows + 30
    grade = Image.new('RGB', (W, H), (8, 7, 12))
    dg = ImageDraw.Draw(grade)
    for i, im in enumerate(imgs):
        cx = (i % cols) * LARGURA
        cy = (i // cols) * (ALTURA + 34)
        grade.paste(im, (cx, cy + 34))
        dg.text((cx + 12, cy + 8), 't=%.1fs  %s' % (quadros[i], rot[i]),
                fill=(210, 200, 240), font=fonte)
    dg.text((12, H - 24),
            'RP Tag 5.16.3 - a sombra do portal (prévia offline: mesma matemática do VazioAlmasFx.java)',
            fill=(130, 120, 165), font=fonte)

    destino = os.path.join(os.path.dirname(__file__), '..', 'previa', 'sombra_vazio_5.16.3.png')
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    grade.save(destino)
    print('ok:', destino, grade.size)


if __name__ == '__main__':
    main()
