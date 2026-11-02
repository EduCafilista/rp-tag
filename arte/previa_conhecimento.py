#!/usr/bin/env python3
"""Prévia do aviso CONHECIMENTO novo (5.10.0) — 4 momentos do roteiro.

Simula o que o ConhecimentoAvisoFx.java desenha (mesmas coordenadas do
protótipo: 960x640, centro (480,300)), num instante de cada etapa:

  t = 2,2 s  o sigilo se forma e as memórias aparecem
  t = 6,0 s  1ª verdade aberta, a barra da 2ª está enchendo
  t = 10,4 s 3 verdades, a balança pesa pro CONHECIMENTO
  t = 14,0 s as 4 abertas: o texto troca (STAGE1)

A fonte do jogo é a do Minecraft; aqui é DejaVu (aproximação).
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 960, 640
CX, CY = W / 2, 300
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, 'previa', 'conhecimento_5.10.0.png')

OURO = (255, 208, 70)
AMBAR = (235, 150, 25)
PALIDO = (255, 246, 200)
BRANCO = (255, 252, 240)

FS = 2  # supersampling (desenha 2x e reduz: o antialias do PIL é fraco)

FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_S = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
FONT_I = '/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'


def fnt(caminho, px):
    return ImageFont.truetype(caminho, int(px * FS))


class JavaRandom:
    """java.util.Random — o alfabeto de runas tem que sair igual ao do mod."""

    def __init__(self, seed):
        self.s = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def _next(self, bits):
        self.s = (self.s * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self.s >> (48 - bits)

    def next_int(self, bound):
        if bound & -bound == bound:
            return (bound * self._next(31)) >> 31
        while True:
            bits = self._next(31)
            v = bits % bound
            if bits - v + (bound - 1) < (1 << 31):
                return v

    def next_float(self):
        return self._next(24) / float(1 << 24)


def alfabeto_runas():
    r = JavaRandom(21)
    runas = []
    for _ in range(28):
        n = 3 + r.next_int(3)
        segs = []
        px, py = r.next_int(3) / 2.0, r.next_int(4) / 3.0 * 1.5
        for _ in range(n):
            while True:
                nx, ny = r.next_int(3) / 2.0, r.next_int(4) / 3.0 * 1.5
                if (nx, ny) != (px, py):
                    break
            segs.append((px, py, nx, ny))
            px, py = nx, ny
        runas.append(segs)
    return runas


RUNAS = alfabeto_runas()


def parada(u, c0, c1, c2):
    a, b = (c0, c1) if u < 0.5 else (c1, c2)
    k = u * 2 if u < 0.5 else (u - 0.5) * 2
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def ease(k):
    k = max(0.0, min(1.0, k))
    return k * k * (3 - 2 * k)


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def desenha_runa(d, idx, x, y, ang, tam, cor, alfa, esp):
    if alfa <= 0.02:
        return
    ca, sa = math.cos(ang), math.sin(ang)
    segs = RUNAS[idx % len(RUNAS)]
    for (x1, y1, x2, y2) in segs:
        ax, ay = (x1 - 0.5) * tam * 0.55, (y1 - 0.75) * tam * 0.55
        bx, by = (x2 - 0.5) * tam * 0.55, (y2 - 0.75) * tam * 0.55
        d.line([(x + ax * ca - ay * sa, y + ax * sa + ay * ca),
                (x + bx * ca - by * sa, y + bx * sa + by * ca)],
               fill=cor + (int(alfa * 255),), width=max(1, int(esp)))


def rgba(img, cor, alfa):
    camada = Image.new('RGBA', img.size, cor + (0,))
    return camada


def brilho(base, cx, cy, r, cor, alfa):
    """disco com centro opaco e borda transparente (como o Buf.brilho)."""
    if alfa <= 0.004 or r < 1:
        return
    n = 48
    d = ImageDraw.Draw(base, 'RGBA')
    a = int(max(0, min(1, alfa)) * 255)
    for i in range(n):
        a0 = i * 2 * math.pi / n
        a1 = (i + 1) * 2 * math.pi / n
        d.polygon([(cx, cy),
                   (cx + math.cos(a0) * r, cy + math.sin(a0) * r),
                   (cx + math.cos(a1) * r, cy + math.sin(a1) * r)],
                  fill=cor + (a,))


def brilho_soma(base, cx, cy, r, cor, alfa):
    """igual ao brilho, mas em mistura ADITIVA (o mod usa blendFunc ONE)."""
    if alfa <= 0.004 or r < 1:
        return
    camada = Image.new('RGBA', base.size, (0, 0, 0, 0))
    brilho(camada, cx, cy, r, cor, alfa)
    luz = Image.new('RGB', base.size, (0, 0, 0))
    luz.paste(camada.convert('RGB'), (0, 0), camada)
    base.alpha_composite(Image.merge('RGBA', (
        Image.eval(luz.split()[0], lambda v: v),
        luz.split()[1], luz.split()[2], camada.split()[3])),
        (0, 0))


# ---------------------------------------------------------------- peças
MEM_X = [170, 790, 128, 832]
MEM_Y = [212, 196, 372, 366]
MEM_GIRO = [-4, 3, 3, -3]
MEM_W, MEM_H = 132, 96
VERDADES = [("Você já esteve", "aqui antes."),
            ("Seu nome não foi", "escolha sua."),
            ("Alguém lembra de você.", "Você não lembra."),
            ("As vozes sempre", "foram uma só.")]


def rot(caixa, ang, centro):
    im = caixa.rotate(ang, resample=Image.BICUBIC, expand=True)
    return im, (centro[0] - im.width / 2, centro[1] - im.height / 2)


def memoria(img, i, t, revelada, prog):
    mx, my = MEM_X[i], MEM_Y[i] + 4 * math.sin(t * 0.6 + i * 1.9)
    ang = MEM_GIRO[i] + 1.4 * math.sin(t * 0.5 + i)
    p = ease(prog)
    hw, hh = MEM_W / 2, MEM_H / 2
    S = FS
    w, h = int((MEM_W + 10) * S), int((MEM_H + 10) * S)
    foto = Image.new('RGBA', (w, h), (26, 16, 5, 255))
    d = ImageDraw.Draw(foto, 'RGBA')
    # a foto sépia
    fx0, fy0 = 5 * S, 5 * S
    for k in range(MEM_H):
        v = k / MEM_H
        tinta = int(110 + 70 * (1 - abs(v - 0.5) * 1.6))
        d.line([(fx0, fy0 + k * S), (fx0 + MEM_W * S, fy0 + k * S)],
               fill=(int(tinta * 1.32), int(tinta * 1.05), int(tinta * 0.72), 255))
    brilho(foto, fx0 + MEM_W * S / 2, fy0 + MEM_H * S / 2, MEM_W * S * 0.62,
           (216, 180, 120), 0.55)
    # vultos
    rnd = random.Random(30 + i * 7)
    for k in range(3):
        u, v = rnd.uniform(-0.5, 0.5), rnd.uniform(-0.35, 0.35)
        r = (9 if k == 0 else 13 + 6 * rnd.random()) * S
        bx, by = fx0 + MEM_W * S / 2 + u * hw * S * 1.2, fy0 + MEM_H * S / 2 + v * hh * S * 1.1
        d.ellipse([bx - r * 0.8, by - r * 1.6, bx + r * 0.8, by + r * 1.6],
                  fill=(36, 26, 12, int((0.22 + 0.4 * p) * 255)))
    if p < 0.98:
        brilho(foto, fx0 + MEM_W * S / 2, fy0 + MEM_H * S / 2, MEM_W * S * 0.6,
               (200, 164, 110), (1 - p) * 0.45)
        desenha_runa(d, int(t * 5) + i * 5, fx0 + MEM_W * S / 2, fy0 + MEM_H * S / 2,
                     0, 40 * S, PALIDO, (1 - p) * 0.5, 1.4 * S)
        if prog > 0.02:
            bx = fx0 + MEM_W * S * prog
            d.rectangle([fx0, fy0 + MEM_H * S - 5 * S, bx, fy0 + MEM_H * S],
                        fill=(255, 220, 110, 235))
    else:
        d.rectangle([fx0, fy0 + MEM_H * S * 0.62, fx0 + MEM_W * S, fy0 + MEM_H * S],
                    fill=(0, 0, 0, 190))
        if revelada:
            f = fnt(FONT_I, 13)
            for ln, linha in enumerate(VERDADES[i]):
                d.text((fx0 + 8 * S, fy0 + MEM_H * S * 0.66 + ln * 16 * S), linha,
                       font=f, fill=BRANCO + (255,))
    # moldura + alfinete
    d.rectangle([fx0 - 5 * S, fy0 - 5 * S, fx0 + MEM_W * S + 5 * S, fy0 + MEM_H * S + 5 * S],
                outline=(PALIDO if revelada else OURO) + (235,), width=int((2.6 if revelada else 1.8) * S))
    d.ellipse([fx0 + MEM_W * S / 2 - 4 * S, fy0 - 14 * S, fx0 + MEM_W * S / 2 + 4 * S, fy0 - 6 * S],
              fill=OURO + (255,))
    im, pos = rot(foto, ang, (mx * S, my * S))
    img.alpha_composite(im, (int(pos[0]), int(pos[1])))


def olho(img, t, boost, abertura):
    d = ImageDraw.Draw(img, 'RGBA')
    w = (68 + 8 * boost) * FS
    h = (36 + 6 * boost) * FS
    cx, cy = CX * FS, CY * FS
    if abertura <= 0.05:
        d.line([(cx - w, cy), (cx + w, cy)], fill=OURO + (255,), width=int(3 * FS))
        return
    hh = max(0.5, h * abertura)
    pts = []
    for i in range(25):
        u = i / 24
        pts.append((cx - w + 2 * w * u, cy - 2 * hh * 2 * u * (1 - u)))
    for i in range(25):
        u = i / 24
        pts.append((cx + w - 2 * w * u, cy + 2 * hh * 2 * u * (1 - u)))
    d.polygon(pts, fill=(252, 240, 190, 255))
    ir = min(hh * 0.95, w * 0.42)
    lx = math.sin(t * 0.37) * 9 * FS
    ly = math.cos(t * 0.29) * 5 * FS
    ix, iy = cx + lx, cy + ly
    d.ellipse([ix - ir, iy - ir, ix + ir, iy + ir], fill=(122, 58, 2, 255))
    d.ellipse([ix - ir * 0.86, iy - ir * 0.86, ix + ir * 0.86, iy + ir * 0.86],
              fill=(232, 142, 22, 255))
    d.ellipse([ix - ir * 0.52, iy - ir * 0.52, ix + ir * 0.52, iy + ir * 0.52],
              fill=(255, 220, 110, 255))
    for k in range(44):
        ang = k * 2 * math.pi / 44 + math.sin(k * 12.9) * 0.03
        r0 = ir * (0.30 + 0.10 * random.Random(k).random())
        r1 = ir * (0.72 + 0.24 * random.Random(k + 99).random())
        cor = (255, 232, 160) if k % 3 == 0 else (138, 74, 0)
        d.line([(ix + math.cos(ang) * r0, iy + math.sin(ang) * r0),
                (ix + math.cos(ang + 0.06) * r1, iy + math.sin(ang + 0.06) * r1)],
               fill=cor + (120,), width=max(1, int(ir * 0.045)))
    pr = ir * (0.33 + 0.04 * math.sin(t * 1.3))
    d.ellipse([ix - pr, iy - pr, ix + pr, iy + pr], fill=(14, 6, 0, 255))
    d.ellipse([ix - ir * 0.5, iy - ir * 0.56, ix - ir * 0.28, iy - ir * 0.34],
              fill=(255, 255, 255, 220))
    d.line(pts + [pts[0]], fill=OURO + (255,), width=int(3.2 * FS))


def balanca(img, nrev, t):
    d = ImageDraw.Draw(img, 'RGBA')
    know = 100 * max(0, min(1, nrev / 4 * 0.72 + 0.28 * ease((t - 4) / 22)))
    san = 100 - 0.45 * know
    tilt = max(-0.5, min(0.5, (know - san) / 100 * 0.5))
    cx, cy, L = 118 * FS, 92 * FS, 62 * FS
    d.line([(cx, cy), (cx, cy + 62 * FS)], fill=OURO + (235,), width=int(2 * FS))
    d.line([(cx - 22 * FS, cy + 62 * FS), (cx + 22 * FS, cy + 62 * FS)],
           fill=OURO + (235,), width=int(2 * FS))
    lx, ly = cx - math.cos(tilt) * L, cy - math.sin(tilt) * L
    rx, ry = cx + math.cos(tilt) * L, cy + math.sin(tilt) * L
    d.line([(lx, ly), (rx, ry)], fill=OURO + (235,), width=int(2.6 * FS))
    d.ellipse([cx - 4 * FS, cy - 4 * FS, cx + 4 * FS, cy + 4 * FS], fill=PALIDO + (255,))
    for lado, (px, py) in enumerate([(lx, ly), (rx, ry)]):
        d.line([(px, py), (px - 16 * FS, py + 30 * FS)], fill=OURO + (235,), width=FS)
        d.line([(px, py), (px + 16 * FS, py + 30 * FS)], fill=OURO + (235,), width=FS)
        d.arc([px - 20 * FS, py + 6 * FS, px + 20 * FS, py + 30 * FS], 0, 180,
              fill=OURO + (235,), width=int(1.6 * FS))
        sz = (10 + (14 * san if lado == 0 else 16 * know) / 100) * FS
        brilho(img, px, py + 22 * FS, sz * 1.2, PALIDO if lado == 0 else OURO, 0.85)
    f = fnt(FONT, 11)
    d.text((lx - 34 * FS, ly + 46 * FS), 'SANIDADE', font=f, fill=(255, 232, 160, 235))
    d.text((lx - 12 * FS, ly + 58 * FS), '%d%%' % san, font=f, fill=(255, 232, 160, 235))
    d.text((rx - 40 * FS, ry + 46 * FS), 'CONHECIMENTO', font=f, fill=(255, 232, 160, 235))
    d.text((rx - 12 * FS, ry + 58 * FS), '%d%%' % know, font=f, fill=(255, 232, 160, 235))
    # contador VERDADES
    d.text((752 * FS, 74 * FS), 'VERDADES', font=f, fill=(255, 232, 160, 235))
    for i in range(4):
        x, y = (760 + i * 34) * FS, 108 * FS
        pts = [(x, y - 14 * FS), (x + 12 * FS, y), (x, y + 14 * FS), (x - 12 * FS, y)]
        if i < nrev:
            d.polygon(pts, fill=PALIDO + (200,))
        d.line(pts + [pts[0]], fill=OURO + (235,), width=int(1.6 * FS))


def painel(t, nrev, prog, etapa1, revel_t, digitado):
    """um quadro do aviso no instante t."""
    img = Image.new('RGBA', (W * FS, H * FS), (4, 2, 1, 255))
    d = ImageDraw.Draw(img, 'RGBA')
    ap = ease(t / 2)
    # fundo radial: o mesmo gradienteRadial() do Java
    raio = max(W, H) * 0.95
    for i in range(8):
        u0, u1 = i / 8.0, (i + 1) / 8.0
        c0 = parada(u0, (0x36, 0x22, 0x0A), (0x16, 0x0D, 0x03), (0x04, 0x02, 0x01))
        c1 = parada(u1, (0x36, 0x22, 0x0A), (0x16, 0x0D, 0x03), (0x04, 0x02, 0x01))
        camada = Image.new('RGBA', img.size, (0, 0, 0, 0))
        dc = ImageDraw.Draw(camada)
        for k in range(36):
            t0, t1 = k * 2 * math.pi / 36, (k + 1) * 2 * math.pi / 36
            dc.polygon([((CX + math.cos(t0) * raio * u0) * FS, (CY + math.sin(t0) * raio * u0) * FS),
                        ((CX + math.cos(t1) * raio * u0) * FS, (CY + math.sin(t1) * raio * u0) * FS),
                        ((CX + math.cos(t1) * raio * u1) * FS, (CY + math.sin(t1) * raio * u1) * FS),
                        ((CX + math.cos(t0) * raio * u1) * FS, (CY + math.sin(t0) * raio * u1) * FS)],
                       fill=c0 + (255,))
        img.alpha_composite(camada)
    brilho_soma(img, CX * FS, CY * FS, 380 * FS, AMBAR, 0.32 * ap)
    brilho_soma(img, CX * FS, CY * FS, 250 * FS, OURO, 0.22 * ap)
    # feixes
    for i in range(8):
        a = i * math.pi / 4 + t * 0.02
        for sinal in (-0.055, 0.055):
            pass
        p1 = (CX + math.cos(a - 0.055) * 60, CY + math.sin(a - 0.055) * 60)
        p2 = (CX + math.cos(a + 0.055) * 60, CY + math.sin(a + 0.055) * 60)
        p3 = (CX + math.cos(a + 0.055) * 760, CY + math.sin(a + 0.055) * 760)
        p4 = (CX + math.cos(a - 0.055) * 760, CY + math.sin(a - 0.055) * 760)
        camada = Image.new('RGBA', img.size, (0, 0, 0, 0))
        dc = ImageDraw.Draw(camada)
        dc.polygon([(p1[0] * FS, p1[1] * FS), (p2[0] * FS, p2[1] * FS),
                    (p3[0] * FS, p3[1] * FS), (p4[0] * FS, p4[1] * FS)],
                   fill=(255, 225, 130, int(26 * ap)))
        camada = camada.filter(ImageFilter.GaussianBlur(6 * FS))
        img.alpha_composite(camada)
    # colunas de runas das bordas
    if t > 1.0:
        apc = ease((t - 1.0) / 1.5)
        for c, x in enumerate([34, 72, W - 72, W - 34]):
            passo = 1 if c % 2 == 0 else -1
            for k in range(22):
                y = ((k * 28 + passo * t * 14) % (H + 40)) - 20
                fade = max(0, min(1, min(y, H - y) / 90))
                desenha_runa(d, int((k * 7 + c * 13) % 28), x * FS, y * FS, 0, 13 * FS,
                             OURO, 0.42 * fade * apc, 1.1 * FS)
    # sigilo
    a1 = ease((t - 0.3) / 1.2)
    if a1 > 0:
        for i in range(24):
            a = i * 2 * math.pi / 24 + t * 0.01
            d.line([((CX + math.cos(a) * 44) * FS, (CY + math.sin(a) * 44) * FS),
                    ((CX + math.cos(a) * 172) * FS, (CY + math.sin(a) * 172) * FS)],
                   fill=OURO + (int(0.18 * a1 * 255),), width=FS)
        for r, esp, al in ((172, 1.7, 0.85), (178, 1.1, 0.40), (146, 1.1, 0.40)):
            d.ellipse([(CX - r) * FS, (CY - r) * FS, (CX + r) * FS, (CY + r) * FS],
                      outline=OURO + (int(al * a1 * 255),), width=max(1, int(esp * FS)))
        for i in range(72):
            a = i * 2 * math.pi / 72 + t * 0.02
            r2 = 178 + (10 if i % 6 == 0 else 4)
            d.line([((CX + math.cos(a) * 178) * FS, (CY + math.sin(a) * 178) * FS),
                    ((CX + math.cos(a) * r2) * FS, (CY + math.sin(a) * r2) * FS)],
                   fill=OURO + (int(0.7 * a1 * 255),), width=FS)
        for k in range(44):
            th = 2 * math.pi * k / 44 - t * 0.05
            desenha_runa(d, (1 * 37 + k * 11) % 28, (CX + math.cos(th) * 159) * FS,
                         (CY + math.sin(th) * 159) * FS, th + math.pi / 2, 16 * FS,
                         OURO, a1 * (0.6 + 0.3 * math.sin(t * 1.2 + k)), 1.5 * FS)
    a2 = ease((t - 0.8) / 1.2)
    if a2 > 0:
        for rotp in (t * 0.05, -t * 0.05 + math.pi):
            pts = [(CX + math.cos(rotp + i * 2 * math.pi / 3 - math.pi / 2) * 135,
                    CY + math.sin(rotp + i * 2 * math.pi / 3 - math.pi / 2) * 135) for i in range(3)]
            d.line(pts + [pts[0]], fill=OURO + (int(0.85 * a2 * 255),), width=int(1.5 * FS))
            for p in pts:
                d.ellipse([p[0] * FS - 3.5 * FS, p[1] * FS - 3.5 * FS,
                           p[0] * FS + 3.5 * FS, p[1] * FS + 3.5 * FS], fill=PALIDO + (200,))
    a3 = ease((t - 1.3) / 1.2)
    if a3 > 0:
        for rotp in (t * 0.07, t * 0.07 + math.pi / 4):
            pts = [(CX + math.cos(rotp + i * 2 * math.pi / 4 - math.pi / 2) * 108,
                    CY + math.sin(rotp + i * 2 * math.pi / 4 - math.pi / 2) * 108) for i in range(4)]
            d.line(pts + [pts[0]], fill=OURO + (int(0.6 * a3 * 255),), width=int(1.2 * FS))
        d.ellipse([(CX - 92) * FS, (CY - 92) * FS, (CX + 92) * FS, (CY + 92) * FS],
                  outline=OURO + (int(0.5 * a3 * 255),), width=FS)
        for k in range(28):
            th = 2 * math.pi * k / 28 + t * 0.07
            desenha_runa(d, (2 * 37 + k * 11) % 28, (CX + math.cos(th) * 82) * FS,
                         (CY + math.sin(th) * 82) * FS, th + math.pi / 2, 12 * FS,
                         OURO, a3 * (0.6 + 0.3 * math.sin(t * 1.2 + k)), 1.5 * FS)
    # cordas
    for i in range(4):
        cresce = ease((t - 2.0 - i * 0.2) / 1.4)
        if cresce <= 0.02:
            continue
        fcx, fcy = MEM_X[i], MEM_Y[i] + 4 * math.sin(t * 0.6 + i * 1.9)
        ang = math.atan2(fcy - CY, fcx - CX)
        p0 = (CX + math.cos(ang) * 178, CY + math.sin(ang) * 178)
        p2 = (fcx, fcy - MEM_H / 2 - 6)
        c = ((p0[0] + p2[0]) / 2, (p0[1] + p2[1]) / 2 + 28 + 6 * math.sin(t * 0.8 + i))
        pts = []
        for k in range(25):
            u = k / 24.0 * cresce
            x = (1 - u) ** 2 * p0[0] + 2 * (1 - u) * u * c[0] + u * u * p2[0]
            y = (1 - u) ** 2 * p0[1] + 2 * (1 - u) * u * c[1] + u * u * p2[1]
            pts.append((x * FS, y * FS))
        brilho_linha = 1.0 if i < nrev else 0.55 + 0.25 * prog[i]
        d.line(pts, fill=OURO + (int(0.18 * brilho_linha * 255),), width=int(5 * FS))
        d.line(pts, fill=OURO + (int(0.75 * brilho_linha * 255),), width=max(1, int(1.4 * FS)))
    # memórias
    if t > 2.4:
        for i in range(4):
            if t < 2.4 + i * 0.25:
                continue
            memoria(img, i, t, i < nrev, prog[i])
    olho(img, t, 1.0 if etapa1 else 0.0, ease((t - 1.4) / 1.8))
    # poeira
    r = JavaRandom(7)
    for i in range(70):
        x, y = (r.next_float() * W, r.next_float() * H)
        yy = (y - t * 8) % (H + 24) - 12
        brilho(img, x * FS, yy * FS, max(1, 6 * FS), OURO, 0.35)
    balanca(img, nrev, t)
    # ícone + AVISO
    if t > 0.8:
        fi = ease((t - 0.8) / 0.8)
        brilho(img, CX * FS, 62 * FS, 60 * FS, OURO, 0.4 * fi)
        d.polygon([(CX * FS, 24 * FS), ((CX + 22) * FS, 100 * FS), ((CX - 22) * FS, 100 * FS)],
                  outline=PALIDO + (int(fi * 255),), width=int(3 * FS))
        f = fnt(FONT_S, 20)
        larg = sum(d.textlength(c, font=f) + 8 * FS for c in 'AVISO')
        x = CX * FS - larg / 2
        for ch in 'AVISO':
            d.text((x, 110 * FS), ch, font=f, fill=PALIDO + (int(fi * 255),))
            x += d.textlength(ch, font=f) + 8 * FS
    # rodapé + texto
    if t > 2.2:
        pa = ease((t - 2.2) / 1.0)
        for k in range(200):
            v = k / 200
            y = H - 210 + k
            d.line([(0, y * FS), (W * FS, y * FS)],
                   fill=(8, 3, 0, int(235 * pa * (v ** 1.5))))
    if t >= 3.4 and digitado > 0:
        f = fnt(FONT_S, 40)
        palavra = 'CONHECIMENTO'
        larg = sum(d.textlength(c, font=f) + 4.5 * FS for c in palavra)
        x = CX * FS - larg / 2
        for i, ch in enumerate(palavra):
            if i < digitado:
                d.text((x, (H - 140) * FS), ch, font=f, fill=(255, 214, 90, 255))
            x += d.textlength(ch, font=f) + 4.5 * FS
        frases = (("Agora você sabe.", "E não pode deixar de saber.") if etapa1 else
                  ("Uma verdade foi revelada. Ela não pode ser esquecida.",
                   "Quanto mais você sabe, menos você consegue voltar a ser quem era."))
        f2 = fnt(FONT_I, 19)
        resto = digitado - len(palavra)
        for i, fr in enumerate(frases):
            if resto <= 0:
                break
            m = max(0, min(resto, len(fr)))
            resto -= len(fr)
            largura = d.textlength(fr[:m], font=f2)
            d.text((CX * FS - largura / 2, (H - 92 + i * 24) * FS), fr[:m],
                   font=f2, fill=(255, 232, 170, 235))
    return img


def main():
    mn = min(0, 0)
    quadros = []
    # (t, nRev, prog, etapa1, revel_t, digitado)
    quadros.append(('2,2 s — o sigilo se forma', painel(2.2, 0, [0, 0, 0, 0], False, [0] * 4, 0)))
    quadros.append(('6,0 s — 1ª verdade aberta, a 2ª enchendo',
                    painel(6.0, 1, [1, 0.62, 0, 0], False, [0] * 4, int((6.0 - 3.4) * 22))))
    quadros.append(('10,4 s — 3 verdades, a balança pesa',
                    painel(10.4, 3, [1, 1, 1, 0.5], False, [0] * 4, 999)))
    quadros.append(('14,0 s — as 4 abertas: o texto troca',
                    painel(14.0, 4, [1, 1, 1, 1], True, [0] * 4, 999)))

    larg, alt = W * FS // 2, H * FS // 2
    margem, topo = 16, 34
    folha = Image.new('RGB', (margem * 3 + larg * 2, topo + margem * 2 + (alt + topo) * 2),
                      (10, 9, 8))
    d = ImageDraw.Draw(folha)
    d.text((margem, 10), 'AVISO · CONHECIMENTO 5.10.0 — prévia do roteiro (sem mouse: '
                         'as memórias se abrem sozinhas)', font=fnt(FONT, 13), fill=(232, 214, 170))
    for i, (titulo, im) in enumerate(quadros):
        x = margem + (i % 2) * (larg + margem)
        y = topo + margem + (i // 2) * (alt + topo)
        folha.paste(im.convert('RGB').resize((larg, alt), Image.LANCZOS), (x, y))
        d.rectangle([x - 1, y - 1, x + larg, y + alt], outline=(70, 56, 30))
        d.text((x, y + alt + 6), titulo, font=fnt(FONT, 11), fill=(190, 172, 132))
    folha.save(SAIDA)
    print('ok', SAIDA, folha.size, os.path.getsize(SAIDA), 'bytes')


if __name__ == '__main__':
    main()
