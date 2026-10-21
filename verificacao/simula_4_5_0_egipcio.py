#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(4.5.0) SIMULAÇÃO do EgipcioAvisoFx — "O Olho de Hórus".

Reproduz em PIL a MESMA matemática do Java, com o mesmo modelo de cor:
cada primitiva é um triângulo/quad com alpha POR VÉRTICE (gradiente), desenhado
numa imagem RGB (o PIL faz o blend do alpha). Camada aditiva = soma.

Tempos e medidas vêm do EgipcioAvisoFx.java:
    DUR 6.5 · ABRE 2.45 · TEXTO 1.55 · cx = w/2 · cy = h*0.45
    k = min(w,h)/240 · S = k*1.75 · R = 30*k · mensagem em cy + 25*k
"""
import math, os
from PIL import Image, ImageDraw, ImageFont, ImageChops

W, H = 1280, 720
K = min(W, H) / 240.0
CX, CY = W / 2.0, H * 0.45
DUR, ABRE, TEXTO = 6.5, 2.45, 1.55
R = 30 * K
S = K * 1.75
TAU = math.pi * 2

NOITE, PAPIRO, AREIA = 0x160F06, 0x3A2A11, 0xC8A24A
AREIA_D, OURO, OURO_B = 0x6E5320, 0xE8C766, 0xFFEBAA
LAPIS, TURQ, OCRE, OSSO = 0x1F4E9C, 0x2E9E8F, 0xA8321E, 0xF2E6C8


# ---------------------------------------------------------------- helpers
def cl(x): return 0.0 if x < 0 else min(1.0, x)
def ss(a, b, x):
    u = cl((x - a) / (b - a)); return u * u * (3 - 2 * u)
def eo(x): return 1 - (1 - x) ** 3
def fr(x): return x - math.floor(x)
def rnd(i, s): return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)
def mix(c1, c2, u):
    return tuple(int(((c1 >> sh) & 255) + (((c2 >> sh) & 255) - ((c1 >> sh) & 255)) * u)
                 for sh in (16, 8, 0))


def C(c, a):
    """cor (int RGB ou tupla) + alpha -> RGBA, com o alpha clampado como no col()."""
    if isinstance(c, tuple):
        r, g, b = c
    else:
        r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    return (int(r), int(g), int(b), int(cl(a) * 255))


_RADIAL = None


def _radial():
    """máscara 96x96: 255 no centro -> 0 na borda, com curva de brilho."""
    global _RADIAL
    if _RADIAL is None:
        n = 96
        im = Image.new("L", (n, n), 0)
        px = im.load()
        for y in range(n):
            for x in range(n):
                dx, dy = (x - n / 2 + 0.5) / (n / 2), (y - n / 2 + 0.5) / (n / 2)
                d = min(1.0, math.hypot(dx, dy))
                px[x, y] = int(255 * (1 - d) ** 1.35)
        _RADIAL = im
    return _RADIAL


class Buf:
    """Mesmo contrato do Buf do Java: tri/quad com cor por vértice."""

    def __init__(self, img):
        self.img = img
        self.d = ImageDraw.Draw(img, "RGBA")

    # gradiente por vértice aproximado: subdivide o triângulo em 3 faixas
    def tri(self, x1, y1, c1, x2, y2, c2, x3, y3, c3):
        # cor média ponderada pelos alphas (bom o bastante para o olho)
        aa = (c1[3] + c2[3] + c3[3]) / 3.0
        if aa < 1: return
        r = int((c1[0] + c2[0] + c3[0]) / 3)
        g = int((c1[1] + c2[1] + c3[1]) / 3)
        b = int((c1[2] + c2[2] + c3[2]) / 3)
        self.d.polygon([(x1, y1), (x2, y2), (x3, y3)], fill=(r, g, b, int(aa)))

    def quad(self, x1, y1, c1, x2, y2, c2, x3, y3, c3, x4, y4, c4):
        self.tri(x1, y1, c1, x2, y2, c2, x3, y3, c3)
        self.tri(x1, y1, c1, x3, y3, c3, x4, y4, c4)

    def rect(self, x, y, w, h, c):
        self.d.rectangle([x, y, x + w, y + h], fill=c)

    def seg(self, ax, ay, bx, by, w, ca, cb):
        dx, dy = by - ay, ax - bx
        L = math.hypot(dx, dy)
        if L < 1e-4: return
        dx, dy = dx / L * w / 2, dy / L * w / 2
        self.quad(ax + dx, ay + dy, ca, ax - dx, ay - dy, ca,
                  bx - dx, by - dy, cb, bx + dx, by + dy, cb)

    def circ(self, cx, cy, r, c):
        if r <= 0.05: return
        self.d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c)

    def elipse(self, cx, cy, ux, uy, a, b, c):
        nx, ny = -uy, ux
        self.d.polygon([(cx + ux * a * math.cos(i * TAU / 24) + nx * b * math.sin(i * TAU / 24),
                         cy + uy * a * math.cos(i * TAU / 24) + ny * b * math.sin(i * TAU / 24))
                        for i in range(24)], fill=c)

    def brilho(self, cx, cy, r, rgb, a):
        """leque de fatias (como o Java): opaco no centro, transparente na borda.
        Feito com UMA máscara radial — sobrepor círculos somaria alpha e viraria disco."""
        if a <= 0.004 or r <= 0:
            return
        m = _radial()
        size = max(2, int(r * 2))
        mm = m.resize((size, size), Image.BILINEAR)
        layer = Image.new("RGBA", (size, size), ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 0))
        layer.putalpha(mm.point(lambda v: int(v * a / 255.0)))
        if self.img.mode == "RGBA":
            tmp = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
            tmp.alpha_composite(layer, (max(0, int(cx - r)), max(0, int(cy - r))))
            self.img.alpha_composite(tmp)
        else:
            self.img.paste(layer, (max(0, int(cx - r)), max(0, int(cy - r))), layer)

    def vinheta(self, w, h, d, rgb, a):
        """4 bordas em degradê (igual ao Java)."""
        steps = 26
        for i in range(steps):
            u0, u1 = i / steps, (i + 1) / steps
            aa = a * (1 - u0) ** 1.6
            dd = d * u1
            self.d.rectangle([0, 0, w, dd], fill=C(rgb, aa))
            self.d.rectangle([0, h - dd, w, h], fill=C(rgb, aa))
            self.d.rectangle([0, 0, dd, h], fill=C(rgb, aa))
            self.d.rectangle([w - dd, 0, w, h], fill=C(rgb, aa))


def fonte(px):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
        if os.path.exists(p):
            return ImageFont.truetype(p, max(6, int(px)))
    return ImageFont.load_default()


# ================================================================== peças
def noite(b, t, env):
    b.rect(0, 0, W, H, C(NOITE, 0.90 * env))
    y = 0.0
    while y < H:
        sd = int(y / (3.2 * K))
        b.rect(0, y, W, max(0.6, 0.7 * K), C(PAPIRO, (0.05 + 0.05 * rnd(sd, 21)) * env))
        y += 3.2 * K
    for i in range(16):
        b.circ(rnd(i, 31) * W, rnd(i, 32) * H, (8 + 22 * rnd(i, 33)) * K, C(PAPIRO, 0.10 * env))


def ra(b, t, env):
    """disco de Rá atrás do olho (4.5.0: menor e mais discreto que no protótipo)."""
    sobe = eo(cl((t - 0.25) / 1.8))
    r = H * 0.155 * (0.55 + 0.45 * sobe)          # era h*0.30 — o disco engolia o olho
    yy = H * 0.71 + (1 - sobe) * 26 * K
    a = (0.13 + 0.08 * sobe) * env
    b.brilho(CX, yy, r * 1.7, OURO, a * 0.55)
    b.circ(CX, yy, r * 0.80, C(mix(OURO, OCRE, 0.35), a * 1.1))
    b.circ(CX, yy, r * 0.66, C(OURO_B, a * 0.45))
    n = 24
    for i in range(n):
        an = i * TAU / n + t * 0.12
        r0, r1 = r * 0.86, r * (1.05 + 0.16 * rnd(i, 41))
        b.tri(CX + math.cos(an) * r0, yy + math.sin(an) * r0, C(OURO, a * 0.55),
              CX + math.cos(an + 0.05) * r1, yy + math.sin(an + 0.05) * r1, C(OURO, 0),
              CX + math.cos(an - 0.05) * r1, yy + math.sin(an - 0.05) * r1, C(OURO, 0))


def pira(b, cxp, base, larg, alt, al):
    ax, bx = cxp - larg / 2, cxp + larg / 2
    topx, topy = cxp, base - alt
    b.tri(ax, base, C(0x120C05, al), bx, base, C(0x120C05, al), topx, topy, C(0x2A1E0C, al))
    b.tri(ax, base, C(0x0E0903, al * 0.9), topx, topy, C(0x241A0A, al), cxp, base, C(0x1A1308, al))
    b.rect(0, base, W, 1.2 * K, C(0x000000, 0.35 * al))


def piramides(b, t, env):
    base = H * 0.80
    a = 0.92 * env
    pira(b, W * 0.22, base, W * 0.30, H * 0.34, a)
    pira(b, W * 0.70, base, W * 0.26, H * 0.27, a * 0.9)
    pira(b, W * 0.46, base - 2 * K, W * 0.16, H * 0.17, a * 0.75)
    for bxo, bw, bh in [(0.22, 0.30, 0.34), (0.70, 0.26, 0.27), (0.46, 0.16, 0.17)]:
        b.seg(W * bxo - W * bw / 2, base, W * bxo, base - H * bh, 1.4 * K,
              C(OURO, 0.24 * env), C(OURO, 0.04 * env))


def areia_chao(b, t, env):
    a = 0.85 * env
    x = 0.0
    while x < W:
        xb = min(W, x + 7)
        ya = H * 0.86 - 7 * K * math.sin(x * 0.012 + 1.2) - 3 * K * math.sin(x * 0.045)
        yb = H * 0.86 - 7 * K * math.sin(xb * 0.012 + 1.2) - 3 * K * math.sin(xb * 0.045)
        b.quad(x, ya, C(AREIA_D, a), xb, yb, C(AREIA_D, a),
               xb, H, C(0x2A1E0C, a), x, H, C(0x2A1E0C, a))
        b.seg(x, ya, xb, yb, 1.1 * K, C(OURO, 0.20 * env), C(OURO, 0.20 * env))
        x += 7


COLUNAS = [[1 + int(rnd(i, 71 + cc) * 5) for i in range(14)] for cc in range(2)]


def glifo(b, x, y, s, tipo, cor):
    if tipo == 1:
        b.circ(x, y - s * 0.55, s * 0.34, cor)
        b.circ(x, y - s * 0.55, s * 0.20, C(0x000000, 0.55))
        b.seg(x - s * 0.5, y - s * 0.05, x + s * 0.5, y - s * 0.05, s * 0.20, cor, cor)
        b.seg(x, y - s * 0.05, x, y + s * 0.75, s * 0.20, cor, cor)
    elif tipo == 2:
        b.seg(x - s * 0.6, y, x + s * 0.6, y, s * 0.12, cor, cor)
        b.elipse(x, y, 1, 0, s * 0.55, s * 0.30, cor)
        b.circ(x, y, s * 0.17, C(0x000000, 0.6))
        b.seg(x - s * 0.1, y + s * 0.25, x - s * 0.35, y + s * 0.7, s * 0.10, cor, cor)
    elif tipo == 3:
        for i in range(3):
            yy = y - s * 0.5 + i * s * 0.45
            b.seg(x - s * 0.6, yy, x - s * 0.2, yy + s * 0.16, s * 0.11, cor, cor)
            b.seg(x - s * 0.2, yy + s * 0.16, x + s * 0.2, yy - s * 0.16, s * 0.11, cor, cor)
            b.seg(x + s * 0.2, yy - s * 0.16, x + s * 0.6, yy, s * 0.11, cor, cor)
    elif tipo == 4:
        b.circ(x, y, s * 0.5, cor)
        b.circ(x, y, s * 0.22, C(0x000000, 0.55))
        for i in range(8):
            an = i * TAU / 8
            b.seg(x + math.cos(an) * s * 0.55, y + math.sin(an) * s * 0.55,
                  x + math.cos(an) * s * 0.78, y + math.sin(an) * s * 0.78, s * 0.09, cor, cor)
    else:
        b.seg(x, y + s * 0.6, x + s * 0.12, y - s * 0.5, s * 0.9 * K + s * 0.08, cor, cor)
        b.elipse(x + s * 0.18, y - s * 0.45, 0, 1, s * 0.13, s * 0.3, cor)


def hieroglifos(b, t, env):
    a, cell = 0.55 * env, 26 * K
    for cc in range(2):
        colX = W * 0.085 if cc == 0 else W * 0.915
        scroll = (t * 26 * K) % cell
        base = int(t * 26 * K / cell)
        for i in range(-1, int(H / cell) + 2):
            tipo = COLUNAS[cc][(base + i) % 14]
            glifo(b, colX, i * cell - scroll, cell * 0.34, tipo,
                  C(OURO, a * (0.55 + 0.45 * rnd(i + base, 51 + cc))))
        b.rect(colX - cell * 0.48, 0, 1.4 * K, H, C(OURO, 0.18 * env))
        b.rect(colX + cell * 0.48, 0, 1.4 * K, H, C(OURO, 0.18 * env))


def olho(b, t, env):
    cyo = CY - 46 * K
    p = cl((t - 1.15) / (ABRE - 1.15))
    if p <= 0.001: return
    a = env * cl(p * 4)
    cor, corF = C(OURO, a), C(OURO_B, a * 0.95)

    p1 = cl(p / 0.22)
    if p1 > 0:
        n, xA, xB = 9, CX - R * 1.05, CX + R * 0.95
        for i in range(n):
            u1, u2 = i / n, (i + 1) / n
            if u2 > p1: break
            x1, x2 = xA + (xB - xA) * u1, xA + (xB - xA) * u2
            y1 = cyo - R * 0.92 - R * 0.34 * math.sin(u1 * 2.1) * (1 - u1 * 0.35)
            y2 = cyo - R * 0.92 - R * 0.34 * math.sin(u2 * 2.1) * (1 - u2 * 0.35)
            b.seg(x1, y1, x2, y2, R * 0.075, cor, cor)

    p2 = cl((p - 0.22) / 0.28)
    if p2 > 0:
        n = 22
        for i in range(n):
            u1, u2 = i / n, (i + 1) / n
            if u2 > p2: break
            ax1, ax2 = CX - R + 2 * R * u1, CX - R + 2 * R * u2
            b.seg(ax1, cyo - R * 0.52 * math.sin(u1 * math.pi), ax2,
                  cyo - R * 0.52 * math.sin(u2 * math.pi), R * 0.085, cor, cor)
            b.seg(ax1, cyo + R * 0.46 * math.sin(u1 * math.pi), ax2,
                  cyo + R * 0.46 * math.sin(u2 * math.pi), R * 0.085, cor, cor)

    p3 = cl((p - 0.50) / 0.22)
    if p3 > 0:
        ri = R * 0.34 * eo(p3)
        b.circ(CX, cyo, ri, corF)
        b.circ(CX, cyo, ri * 0.58, C(0x140E02, a))
        b.circ(CX - ri * 0.18, cyo - ri * 0.20, ri * 0.16, C(OURO_B, a * 0.9))

    p4 = cl((p - 0.72) / 0.14)
    if p4 > 0:
        b.seg(CX - R * 0.16, cyo + R * 0.42, CX - R * 0.16,
              cyo + R * 0.46 + R * 0.85 * p4, R * 0.10, cor, cor)

    p5 = cl((p - 0.86) / 0.14)
    if p5 > 0:
        n = 16
        xa, ya = CX + R * 0.28, cyo + R * 0.46
        for i in range(n):
            u1, u2 = i / n, (i + 1) / n
            if u2 > p5: break
            an1, an2 = u1 * 5.2, u2 * 5.2
            r1, r2 = R * 0.16 + u1 * R * 0.42, R * 0.16 + u2 * R * 0.42
            b.seg(xa + math.cos(an1 + 1.2) * r1, ya + math.sin(an1 + 1.2) * r1,
                  xa + math.cos(an2 + 1.2) * r2, ya + math.sin(an2 + 1.2) * r2, R * 0.085, cor, cor)

    if p >= 1:
        b.circ(CX, cyo, R * 0.30, C(OURO, 0.18 * env * (0.5 + 0.5 * math.sin(t * 2.6))))


def brilho_olho(b, t, env):
    cyo = CY - 46 * K
    p = cl((t - 1.15) / (ABRE - 1.15))
    if p <= 0.05: return
    pulso = 0.55 + 0.45 * math.sin(t * 2.6)
    b.brilho(CX, cyo, R * 1.5, OURO, 0.16 * env * p * pulso)
    b.brilho(CX, cyo, R * 0.30, OURO_B, 0.09 * env * p)


def cartucho(b, t, env, larg):
    al = ss(TEXTO - 0.25, TEXTO + 0.55, t) * env
    if al <= 0.01: return
    hw, hh = larg * 0.5 + 26 * K, 5.2 * S + 10 * K
    cor, cor2 = C(OURO, 0.55 * al), C(OURO, 0.22 * al)
    b.seg(CX - hw + 8 * K, CY - hh, CX + hw - 8 * K, CY - hh, 1.8 * K, cor, cor)
    b.seg(CX - hw + 8 * K, CY + hh, CX + hw - 8 * K, CY + hh, 1.8 * K, cor, cor)
    for lado in (-1, 1):
        px, py = CX + lado * (hw - 8 * K), CY - hh
        for i in range(1, 7):
            u = i / 6.0
            nx = CX + lado * (hw - 8 * K) * (1 + 0.10 * math.sin(u * math.pi))
            ny = CY - hh + 2 * hh * u
            b.seg(px, py, nx, ny, 1.8 * K, cor2, cor)
            px, py = nx, ny
    ny = CY + hh + 7 * K
    b.seg(CX - hw * 0.42, CY + hh, CX - hw * 0.30, ny, 1.6 * K, cor, cor)
    b.seg(CX + hw * 0.42, CY + hh, CX + hw * 0.30, ny, 1.6 * K, cor, cor)
    b.rect(CX - hw * 0.34, ny, hw * 0.68, 2.0 * K, cor)
    b.rect(CX - hw + 8 * K, CY - hh - 2.6 * K, hw * 2 - 16 * K, 1.3 * K, C(LAPIS, 0.30 * al))


NGR = 90
GRAO = [(rnd(i, 11), rnd(i, 12), 0.25 + 0.9 * rnd(i, 13), 0.4 + 1.5 * rnd(i, 14), rnd(i, 15))
        for i in range(NGR)]


def graos(b, t, env):
    for i in range(NGR):
        gx, gy, gv, gt, gf = GRAO[i]
        vx, vy = gv * 46 * K, gv * 13 * K
        px = fr(gx + t * vx / W) * W + math.sin(t * 1.6 + gf * 9) * 5 * K
        py = fr(gy + t * vy / H) * H
        a = (0.16 + 0.30 * gf) * env
        b.circ(px, py, gt * K * 0.5, C(OURO, a))
        if i % 7 == 0:
            b.seg(px, py, px - vx * 0.05, py - vy * 0.05, 0.8 * K, C(OURO, a * 0.7), C(OURO, 0))
    for i in range(22):
        px = rnd(i, 61) * W
        py = H - fr(rnd(i, 62) + t * 0.06 * (0.5 + rnd(i, 63))) * H
        b.circ(px + math.sin(t * 0.9 + i) * 6 * K, py, (0.8 + 1.4 * rnd(i, 64)) * K,
               C(OURO_B, 0.28 * env * rnd(i, 65)))


# ------------------------------------------------------------------ texto
def layout(msg):
    """fonte Minecraft: ~6px de largura por letra, espaço de 1.5 entre elas."""
    cw = [6.0] * len(msg)
    tot = sum(cw) + 1.5 * (len(msg) - 1)
    x0 = CX - tot * S / 2
    xs, acc = [], 0.0
    for i in range(len(msg)):
        xs.append(x0 + acc * S)
        acc += cw[i] + 1.5
    return xs, cw, tot


_tile_cache = {}


def letra(ch, px_h, px_w):
    """renderiza a letra e estica para a caixa da fonte do Minecraft."""
    key = (ch, px_h, int(px_w))
    if key in _tile_cache: return _tile_cache[key]
    f = fonte(px_h * 1.32)
    tmp = Image.new("RGBA", (px_h * 3, px_h * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(tmp)
    d.text((px_h * 0.2, px_h * 0.1), ch, font=f, fill=(255, 255, 255, 255))
    bb = tmp.getbbox()
    tile = tmp.crop(bb) if bb else tmp
    tile = tile.resize((max(1, int(px_w)), max(1, int(px_h))), Image.LANCZOS)
    _tile_cache[key] = tile
    return tile


def texto(img, t, env, xs, cw, msg):
    for i, ch in enumerate(msg):
        la = cl((t - TEXTO - 0.10 * i) / 0.26)
        e = eo(la)
        a8 = la * env
        if a8 < 0.03: continue
        sc = S * (1 + 0.55 * (1 - e))
        pulse = 1 + 0.015 * math.sin(t * 2.6)
        hpx = max(4, int(8 * sc * pulse))
        wpx = max(3, int(cw[i] * sc * pulse))
        px = int(xs[i] + cw[i] * S / 2 - cw[i] * sc / 2)
        py = int(CY - 4 * S - 4 * (sc - S))
        tile = letra(ch, hpx, wpx)

        def pintar(cor, alpha, dx=0, dy=0):
            a = tile.split()[3].point(lambda v, al=alpha: int(v * al))
            camada = Image.new("RGBA", tile.size, cor + (0,))
            camada.putalpha(a)
            img.alpha_composite(camada, (max(0, px + dx), max(0, py + dy)))

        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            pintar((0x2A, 0x1A, 0x05), a8, dx, dy)
        pintar(((OURO >> 16) & 255, (OURO >> 8) & 255, OURO & 255), a8)
        pintar(((OURO_B >> 16) & 255, (OURO_B >> 8) & 255, OURO_B & 255), a8 * 0.35, 0, -1)


# ================================================================== frame
def frame(t, msg="AVISO", sub="O templo de Ra abre os olhos para voce."):
    env = ss(0, 0.45, t) * (1 - ss(DUR - 0.85, DUR, t))
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))

    # ---- camada 1: normal
    b = Buf(img)
    noite(b, t, env)
    ra(b, t, env)
    piramides(b, t, env)
    areia_chao(b, t, env)
    hieroglifos(b, t, env)
    b.brilho(CX, CY - 46 * K, 38 * K, 0x0A0703, 0.72 * env)
    olho(b, t, env)
    xs, cw, tot = layout(msg)
    cartucho(b, t, env, tot * S)
    graos(b, t, env)

    # ---- camada 2: aditiva (ouro) — desenha em preto e SOMA
    add = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ba = Buf(add)
    flash = max(0.0, 1 - (t - ABRE) / 0.45) if t > ABRE else 0.0
    if flash > 0.01:
        # flash é aditivo: pinta o quadro inteiro
        ba.rect(0, 0, W, H, C(OURO_B, 0.32 * flash * flash))
    brilho_olho(ba, t, env)
    base = ImageChops.add(img.convert("RGB"), add.convert("RGB")) if add.getbbox() else img.convert("RGB")

    # ---- texto + mensagem
    out = base.convert("RGBA")
    texto(out, t, env, xs, cw, msg)
    sa = ss(TEXTO + 0.35, TEXTO + 0.95, t) * env
    if sub and sa >= 0.03:
        fsub = fonte(int(6 * min(K * 0.72, (W * 0.85) / max(1, len(sub) * 6)) * 2))
        d = ImageDraw.Draw(out)
        tw = d.textlength(sub, font=fsub)
        tx, ty = CX - tw / 2, CY + 25 * K
        d.text((tx + 1, ty + 1), sub, font=fsub, fill=(0x2A, 0x1A, 0x05, int(sa * 230)))
        d.text((tx, ty), sub, font=fsub, fill=C(OSSO, sa))

    # ---- camada 3: vinheta de papiro
    bv = Buf(out)
    bv.vinheta(W, H, min(W, H) * 0.14, NOITE, 0.55 * env)

    if env < 1:
        preto = Image.new("RGBA", (W, H), (0, 0, 0, int((1 - env) * 255)))
        out.alpha_composite(preto)
    return out.convert("RGB")


if __name__ == "__main__":
    os.makedirs("verificacao", exist_ok=True)
    frame(3.20).save("verificacao/4_5_0_egipcio_frame.png")
    frame(2.10).save("verificacao/4_5_0_egipcio_olho.png")
    frames = [frame(t / 20.0).convert("P", palette=Image.ADAPTIVE, colors=128)
              for t in range(0, int(DUR * 20), 2)]
    frames[0].save("verificacao/4_5_0_egipcio.gif", save_all=True, append_images=frames[1:],
                   duration=100, loop=0, optimize=True)
    print("ok:", len(frames), "frames")
