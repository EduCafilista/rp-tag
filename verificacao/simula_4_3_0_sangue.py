"""Simulacao VISUAL do novo SangueAvisoFx ("HEMORRAGIA", 4.3.0) — 2026-10-20.

Nao e screenshot do jogo: reproduz em numpy/PIL a MESMA matematica do
SangueAvisoFx.java (k, cx, cy, S, ty, cy+25*k, BEATS, TENDOES, MAN, veia(),
ferida(), jatos(), cortina(), poco(), manchas()) para provar como o
`avisar "sangue"` fica no fim das contas.

Saida: verificacao/4_3_0_sangue_*.png
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)))
FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"

VOID, CRUST, DEEP, BLOOD, BLOOD_B = (0x07, 0x00, 0x01), (0x24, 0x00, 0x03), (0x3D, 0x00, 0x06), (0x8E, 0x05, 0x10), (0xC4, 0x12, 0x1F)
FLESH, FLESH_D, GLOSS = (0x8C, 0x4A, 0x44), (0x5E, 0x2A, 0x28), (0xFF, 0x9A, 0x9A)

TAU = 6.2831853
DUR, RUPTURA = 6.0, 0.30
BEATS = [0.32, 0.95, 1.48, 1.94, 2.34, 2.70, 3.02, 3.36, 3.74, 4.20, 4.78, 5.45]
TENDOES = [0.70, 1.55, 2.45]
DR = [1.15, 1.75, 2.45, 3.10, 4.05, 5.05]


def fr(x): return x - math.floor(x)
def rnd(i, s): return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)
def cl(x): return max(0.0, min(1.0, x))
def ss(a, b, x): u = cl((x - a) / (b - a)); return u * u * (3 - 2 * u)
def eo(x): x = cl(x); return 1 - (1 - x) ** 3
def lerp(a, b, u): return a + (b - a) * u


NMAN = 14
MAN = []
for i in range(NMAN):
    MAN.append([0.05 + 0.90 * rnd(i, 11), 0.03 + 0.58 * rnd(i, 12), 5 + 15 * rnd(i, 13),
                RUPTURA + 0.02 + 2.4 * rnd(i, 14), i * 37 + 5, 0.35 + 0.65 * rnd(i, 15)])


class Tela:
    """Canvas RGBA float64 com alpha-composite e blend ADITIVO (como o Buf)."""

    def __init__(self, w, h, fundo):
        self.w, self.h = w, h
        self.img = np.zeros((h, w, 3), np.float64)
        self.img[:, :] = fundo

    # ---------------- primitivas ----------------
    def _mask(self, pts, bbox):
        x0, y0, x1, y1 = bbox
        m = Image.new("L", (max(1, x1 - x0), max(1, y1 - y0)), 0)
        ImageDraw.Draw(m).polygon([(x - x0, y - y0) for x, y in pts], fill=255)
        return np.asarray(m, np.float64)[..., None] / 255.0

    def quad(self, p, c, aditivo=False):
        """p = [(x,y)x4] na ordem TL,TR,BR,BL; c = [(r,g,b,a)x4] com rgb em 0..255."""
        c = [((v[0] / 255.0, v[1] / 255.0, v[2] / 255.0, v[3]) if isinstance(v, tuple) else v) for v in c]
        xs = [q[0] for q in p]; ys = [q[1] for q in p]
        x0, y0 = int(max(0, min(xs))), int(max(0, min(ys)))
        x1, y1 = int(min(self.w, max(xs) + 1)), int(min(self.h, max(ys) + 1))
        if x1 <= x0 or y1 <= y0:
            return
        m = self._mask(p, (x0, y0, x1, y1))
        dx = xs[1] - xs[0]
        dy = ys[2] - ys[0]
        gx = ((np.arange(x0, x1) + 0.5 - xs[0]) / dx) if abs(dx) > 1e-6 else np.zeros(x1 - x0)
        gy = ((np.arange(y0, y1) + 0.5 - ys[0]) / dy) if abs(dy) > 1e-6 else np.zeros(y1 - y0)
        gx = np.clip(gx, 0, 1)[None, :, None]
        gy = np.clip(gy, 0, 1)[:, None, None]
        top = np.array(c[0]) * (1 - gx) + np.array(c[1]) * gx
        bot = np.array(c[3]) * (1 - gx) + np.array(c[2]) * gx
        cor = top * (1 - gy) + bot * gy
        a = cor[..., 3:4] * m
        reg = self.img[y0:y1, x0:x1]
        if aditivo:
            self.img[y0:y1, x0:x1] = reg + cor[..., :3] * a
        else:
            self.img[y0:y1, x0:x1] = reg * (1 - a) + cor[..., :3] * a

    def rect(self, x, y, w, h, c, aditivo=False):
        self.quad([(x, y), (x + w, y), (x + w, y + h), (x, y + h)], [c] * 4, aditivo)

    def circ(self, cx, cy, r, c):
        c = (c[0] / 255.0, c[1] / 255.0, c[2] / 255.0, c[3])
        r = max(0.6, r)
        x0, y0 = int(max(0, cx - r)), int(max(0, cy - r))
        x1, y1 = int(min(self.w, cx + r + 1)), int(min(self.h, cy + r + 1))
        if x1 <= x0 or y1 <= y0:
            return
        yy, xx = np.mgrid[y0:y1, x0:x1]
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        m = np.clip(r - d + 0.5, 0, 1)[..., None]
        a = m * c[3]
        reg = self.img[y0:y1, x0:x1]
        self.img[y0:y1, x0:x1] = reg * (1 - a) + np.array(c[:3]) * a

    def seg(self, ax, ay, bx, by, w, ca, cb):
        dx, dy = bx - ax, by - ay
        L = math.hypot(dx, dy)
        if L < 0.01:
            return
        nx, ny = -dy / L * w / 2, dx / L * w / 2
        cm = [tuple((ca[i] + cb[i]) / 2 for i in range(4))] * 4
        self.quad([(ax + nx, ay + ny), (bx + nx, by + ny), (bx - nx, by - ny), (ax - nx, ay - ny)], cm)

    def elipse(self, cx, cy, ux, uy, a, bb, c):
        c = (c[0] / 255.0, c[1] / 255.0, c[2] / 255.0, c[3])
        rx, ry = max(0.6, a), max(0.6, bb)
        x0, y0 = int(max(0, cx - rx)), int(max(0, cy - ry))
        x1, y1 = int(min(self.w, cx + rx + 1)), int(min(self.h, cy + ry + 1))
        if x1 <= x0 or y1 <= y0:
            return
        yy, xx = np.mgrid[y0:y1, x0:x1]
        d = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
        m = np.clip(1 - d, 0, 1)[..., None] * 2.2
        m = np.clip(m, 0, 1)
        al = m * c[3]
        reg = self.img[y0:y1, x0:x1]
        self.img[y0:y1, x0:x1] = reg * (1 - al) + np.array(c[:3]) * al

    def brilho(self, cx, cy, r, rgb, a, aditivo=True):
        x0, y0 = int(max(0, cx - r)), int(max(0, cy - r))
        x1, y1 = int(min(self.w, cx + r + 1)), int(min(self.h, cy + r + 1))
        if x1 <= x0 or y1 <= y0:
            return
        yy, xx = np.mgrid[y0:y1, x0:x1]
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / max(1e-6, r)
        f = np.clip(1 - d, 0, 1) ** 2 * a
        reg = self.img[y0:y1, x0:x1]
        self.img[y0:y1, x0:x1] = reg + np.array(rgb) / 255.0 * f[..., None]

    def vinheta(self, d, rgb, a):
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        cx, cy = self.w / 2, self.h / 2
        e = np.maximum(np.abs(xx - cx) / cx, np.abs(yy - cy) / cy)
        f = np.clip((e - (1 - d / max(self.w, self.h))) / 0.5, 0, 1) ** 1.6 * a
        self.img[:] = self.img * (1 - f[..., None]) + np.array(rgb) / 255.0 * f[..., None]


# ---------------------------------------------------------------- o FX
def batida(t):
    s = 0.0
    for tb in BEATS:
        if t >= tb: s += math.exp(-(t - tb) * 8.5)
        if t >= tb + 0.20: s += 0.55 * math.exp(-(t - tb - 0.20) * 8.5)
    return min(1.25, s)


def abertura(t):
    if t < RUPTURA: return 0.0
    u = cl((t - RUPTURA) / 0.35)
    el = 1 + 0.22 * math.sin(u * math.pi) * (1 - u)
    return eo(u) * el * (1 - ss(DUR - 0.7, DUR - 0.1, t))


def veia(T, x, y, ang, ln, dep, seed, g, a, k):
    if dep <= 0 or g <= 0.02: return
    L = ln * min(1, g); px, py, an = x, y, ang
    for i in range(4):
        an += (rnd(seed + i, 41) - 0.5) * 0.55
        nx, ny = px + math.cos(an) * L / 4, py + math.sin(an) * L / 4
        wd = (0.35 + dep * 0.55) * k
        T.seg(px, py, nx, ny, wd, (*DEEP, a), (*DEEP, a * 0.82))
        T.seg(px, py, nx, ny, wd * 0.42, (*BLOOD, a * 0.55), (*BLOOD, a * 0.40))
        if (i == 1 or i == 3) and g > 0.35:
            veia(T, nx, ny, an + (0.75 if rnd(seed + i, 42) > 0.5 else -0.75),
                 ln * 0.58, dep - 1, seed * 7 + i * 13 + dep, cl((g - 0.35) / 0.65), a * 0.88, k)
        px, py = nx, ny


def renderizar(t, W=1920, H=1080, msg="AVISO", sub="VOCE ESTA SANGRANDO"):
    k = min(W, H) / 240.0
    cx, cy = W / 2.0, H * 0.45
    env = ss(0, 0.30, t) * (1 - ss(DUR - 0.85, DUR, t))
    beat = batida(t)
    T = Tela(W, H, (VOID[0] / 255, VOID[1] / 255, VOID[2] / 255))
    T.img *= 0.34
    T.rect(0, 0, W, H, (*VOID, (0.66 + 0.12 * beat) * env))

    # ---- carne
    d = min(W, H) * 0.30
    va = (0.70 + 0.22 * beat) * env
    T.quad([(0, 0), (W, 0), (W, d), (0, d)], [(*CRUST, va), (*CRUST, va), (*CRUST, 0), (*CRUST, 0)])
    T.quad([(0, H), (W, H), (W, H - d), (0, H - d)], [(*CRUST, va), (*CRUST, va), (*CRUST, 0), (*CRUST, 0)])
    T.quad([(0, 0), (0, H), (d, H), (d, 0)], [(*CRUST, va), (*CRUST, va), (*CRUST, 0), (*CRUST, 0)])
    T.quad([(W, 0), (W, H), (W - d, H), (W - d, 0)], [(*CRUST, va), (*CRUST, va), (*CRUST, 0), (*CRUST, 0)])
    for i in range(150):
        u, v = rnd(i, 21), rnd(i, 22)
        ex, ey = abs(u - 0.5) * 2, abs(v - 0.5) * 2
        if max(ex, ey) < 0.55 + 0.12 * math.sin(t * 1.7 + i): continue
        a = (0.18 + 0.22 * rnd(i, 24)) * (0.75 + 0.35 * beat) * env
        T.circ(u * W, v * H, (1.2 + 3.2 * rnd(i, 23)) * k * 0.5, (*(BLOOD if i % 5 == 0 else FLESH_D), a))

    # ---- veias
    g = eo(cl((t - 0.15) / 1.6))
    if g > 0.02:
        a = (0.50 + 0.30 * beat) * env
        ln = min(W, H) * 0.34
        for e in range(4):
            for q in range(5):
                sd = e * 17 + q * 5 + 3
                u = [0.10, 0.33, 0.58, 0.84, 0.95][q] + 0.04 * (rnd(sd, 31) - 0.5)
                ja = (rnd(sd, 32) - 0.5) * 46 * math.pi / 180
                vx = W * u if e < 2 else (0 if e == 2 else W)
                vy = (0 if e == 0 else H) if e < 2 else H * u
                ang = [math.pi / 2, -math.pi / 2, 0, math.pi][e] + ja
                veia(T, vx, vy, ang, ln, 3, sd * 29 + 11, g, a, k)

    # ---- ferida
    my, raioY, larg = cy, H * 0.30, 34 * k
    ab = abertura(t)
    lx, rx, yy, ed = [], [], [], []
    if ab > 0.02:
        n = 34
        for i in range(n + 1):
            u = i / n; v = (u - 0.5) * 2
            perfil = max(0, 1 - v * v) ** 0.55
            jag = 0.86 + 0.26 * rnd(i // 2, 91) + 0.16 * rnd(i, 95) + 0.10 * math.sin(u * 7.3)
            resp = 1 + 0.055 * math.sin(t * 2.2) + 0.09 * beat
            wd = larg * perfil * jag * ab * resp
            yy.append(my - raioY + 2 * raioY * u); lx.append(cx - wd); rx.append(cx + wd); ed.append(perfil)
        def mixc(c1, c2, u):
            return tuple(int(c1[j] + (c2[j] - c1[j]) * u) for j in range(3))
        for i in range(n):
            u1, u2 = i / n, (i + 1) / n
            c1 = mixc(DEEP, VOID, ss(0, 0.55, u1)); c2 = mixc(DEEP, VOID, ss(0, 0.55, u2))
            T.quad([(lx[i], yy[i]), (rx[i], yy[i]), (rx[i + 1], yy[i + 1]), (lx[i + 1], yy[i + 1])],
                   [(*c1, 0.99 * env), (*c1, 0.99 * env), (*c2, 0.99 * env), (*c2, 0.99 * env)])
        for i in range(2, n - 2, 2):
            sd = i * 17 + 3
            hw = (rx[i] - lx[i]) * 0.5
            x1 = cx + (rnd(sd, 111) - 0.5) * 2 * hw * 0.8
            ln = (3 + 7 * rnd(sd, 112)) * k * (1 + 0.1 * beat)
            ang = (rnd(sd, 113) - 0.5) * 0.9
            T.seg(x1, yy[i], x1 + math.sin(ang) * ln, yy[i] + math.cos(ang) * ln, 0.6 * k,
                  (*BLOOD, 0.30 * env), (*BLOOD, 0.45 * env))
        lip = 7 * k
        for i in range(n):
            s1 = 0.55 + 0.95 * rnd(i // 3, 93); s2 = 0.55 + 0.95 * rnd((i + 1) // 3, 93)
            a1 = env * (0.22 + 0.42 * ed[i]); a2 = env * (0.22 + 0.42 * ed[i + 1])
            o1, o2, av = lip * s1 * 1.15, lip * s2 * 1.15, 0.45 * env
            T.quad([(lx[i] - o1, yy[i]), (lx[i], yy[i]), (lx[i + 1], yy[i + 1]), (lx[i + 1] - o2, yy[i + 1])],
                   [(*VOID, 0), (*VOID, av), (*VOID, av), (*VOID, 0)])
            T.quad([(rx[i] + o1, yy[i]), (rx[i], yy[i]), (rx[i + 1], yy[i + 1]), (rx[i + 1] + o2, yy[i + 1])],
                   [(*VOID, 0), (*VOID, av), (*VOID, av), (*VOID, 0)])
            T.quad([(lx[i], yy[i]), (lx[i] - lip * s1, yy[i]), (lx[i + 1] - lip * s2, yy[i + 1]), (lx[i + 1], yy[i + 1])],
                   [(*FLESH, a1), (*FLESH_D, 0), (*FLESH_D, 0), (*FLESH, a2)])
            T.quad([(rx[i], yy[i]), (rx[i] + lip * s1, yy[i]), (rx[i + 1] + lip * s2, yy[i + 1]), (rx[i + 1], yy[i + 1])],
                   [(*FLESH, a1), (*FLESH_D, 0), (*FLESH_D, 0), (*FLESH, a2)])
            T.seg(lx[i], yy[i], lx[i + 1], yy[i + 1], 1.2 * k, (*BLOOD_B, 0.60 * env), (*BLOOD_B, 0.60 * env))
            T.seg(rx[i], yy[i], rx[i + 1], yy[i + 1], 1.2 * k, (*BLOOD_B, 0.60 * env), (*BLOOD_B, 0.60 * env))
            if i % 3 == 0:
                T.seg(lx[i] - lip * 0.85, yy[i], lx[i] - lip * 0.15, yy[i] + 2.5 * k, 0.6 * k, (*FLESH_D, 0.45 * env), (*FLESH_D, 0.12 * env))
                T.seg(rx[i] + lip * 0.85, yy[i], rx[i] + lip * 0.15, yy[i] - 2.5 * k, 0.6 * k, (*FLESH_D, 0.45 * env), (*FLESH_D, 0.12 * env))
            if i % 4 == 1:
                sd = i * 31 + 7
                rl = (10 + 34 * rnd(sd, 96)) * k * cl(ab) * (0.6 + 0.4 * rnd(sd, 97))
                for q in range(3):
                    ang = (rnd(sd + q, 98) - 0.5) * 1.5
                    sx = lx[i] if q % 2 == 0 else rx[i]
                    exx = sx + (-1 if q % 2 == 0 else 1) * math.cos(ang) * rl * (q + 1) / 3
                    eyy = yy[i] + math.sin(ang) * rl * (q + 1) / 3
                    T.seg(sx, yy[i] + (q - 1) * k, exx, eyy, 0.55 * k, (*DEEP, 0.75 * env), (*BLOOD, 0.15 * env))
        # tendões (curvos)
        for i, tt in enumerate(TENDOES):
            y = my + (i - (len(TENDOES) - 1) / 2) * raioY * 0.34
            idx = max(0, min(n, round((y - (my - raioY)) / (2 * raioY) * n)))
            hw = (rx[idx] - lx[idx]) / 2
            frac = 0.48 + 0.36 * rnd(i, 101)
            sag = (2.5 + 3.5 * rnd(i, 102)) * k
            tau = t - tt
            tensao = 1.0 if tau <= 0 else 1 - eo(cl(tau / 0.30)) * 0.92
            if tau < 0.30:
                passos = 5
                x0, x1 = cx - hw * frac * tensao, cx + hw * frac * tensao
                pxp, pyp, aAnt = x0, y, 0.85 * env
                for q in range(1, passos + 1):
                    u = q / passos
                    xx = x0 + (x1 - x0) * u
                    yy_t = y + sag * math.sin(u * math.pi) * (0.7 + 0.3 * math.sin(t * 4.1 + i))
                    aa = 0.85 * env * (1 - (cl(tau / 0.30) if tau > 0 else 0))
                    T.seg(pxp, pyp, xx, yy_t, (0.75 - 0.22 * u) * k, (*FLESH, aAnt), (*FLESH, aa))
                    pxp, pyp, aAnt = xx, yy_t, aa
        # escorrido: fita contínua
        fy = my + raioY * 0.90
        fl = cl((t - 0.55) / 1.5)
        if fl > 0.02 and ab > 0.2:
            seg = 44
            prevL = prevR = prevY = 0
            for i in range(seg + 1):
                u = i / seg
                y = fy + u * H * 0.22
                x = cx + (4.5 * math.sin(u * 4.1 + t * 2.1) + 2.5 * math.sin(u * 9.7 - t * 3.3)) * k * u
                wdt = (4 + 7 * fl) * k * (0.45 + 0.80 * u) * (1 - 0.55 * ss(0.80, 1.0, u)) * (0.85 + 0.22 * math.sin(u * 6.1 - t * 1.7)) * (1 + 0.05 * beat)
                L, R = x - wdt * 0.5, x + wdt * 0.5
                if i > 0:
                    aa = env * (1 - 0.30 * u)
                    cc = mixc(DEEP, BLOOD, cl(u * 0.9))
                    T.quad([(prevL, prevY), (prevR, prevY), (R, y), (L, y)], [(*cc, aa)] * 4)
                    if i % 2 == 0:
                        T.seg((prevL + prevR) * 0.5, prevY, x, y, 0.8 * k, (*BLOOD_B, aa * 0.32), (*BLOOD_B, aa * 0.32))
                prevL, prevR, prevY = L, R, y

    else:
        a = cl(t / RUPTURA) * env
        n = 22
        for i in range(n):
            u1, u2 = i / n, (i + 1) / n
            y1 = cy - raioY + 2 * raioY * u1; y2 = cy - raioY + 2 * raioY * u2
            x1 = cx + (rnd(i, 51) - 0.5) * 2.4 * k * a
            x2 = cx + (rnd(i + 1, 51) - 0.5) * 2.4 * k * a
            T.seg(x1, y1, x2, y2, 1.1 * k, (*CRUST, 0.9 * a), (*CRUST, 0.9 * a))
            if i % 3 == 0: T.circ(x1, y1, 0.5 * k, (*BLOOD, 0.6 * a))

    # ---- jatos
    if ab > 0.05:
        for bi, tb in enumerate(BEATS):
            tau = t - tb
            if tau <= 0 or tau > 0.55: continue
            life = 1 - tau / 0.55
            for j in range(11):
                sd = bi * 53 + j * 11 + 7
                lado = -1.0 if rnd(sd, 1) < 0.5 else 1.0
                ang = lado * (0.12 + 1.25 * rnd(sd, 2))
                y0 = my + (rnd(sd, 3) - 0.5) * raioY * 1.15
                x0 = cx + lado * larg * (0.55 + 0.5 * rnd(sd, 4))
                v = (110 + 230 * rnd(sd, 5)) * k
                vx, vy = math.cos(ang) * v, math.sin(ang) * v * 0.75 - 60 * k
                ga = 520 * k
                px = x0 + vx * tau; py = y0 + vy * tau + 0.5 * ga * tau * tau
                t2 = max(0, tau - 0.035)
                px2 = x0 + vx * t2; py2 = y0 + vy * t2 + 0.5 * ga * t2 * t2
                sz = (2.4 + 4.6 * rnd(sd, 6)) * k * life
                c = (*(BLOOD_B if rnd(sd, 7) < 0.18 else BLOOD), 0.95 * env * cl(life * 1.6))
                T.seg(px2, py2, px, py, sz, (*BLOOD, 0), c)
                T.circ(px, py, sz * 0.62, c)
            if bi % 2 == 0 and tau < 0.42:
                u = tau / 0.42
                hh = (60 + 130 * (1 - u)) * k
                T.seg(cx, my - raioY * 0.15, cx + (rnd(bi, 61) - 0.5) * 12 * k,
                      my - raioY * 0.15 - hh, 5.5 * k * (1 - u), (*BLOOD, 0.85 * env * (1 - u)), (*BLOOD, 0))

    # ---- manchas
    for i in range(NMAN):
        t0 = MAN[i][3]
        if t < t0: continue
        age = t - t0; sc = eo(cl(age / 0.12))
        px, py = MAN[i][0] * W, MAN[i][1] * H
        r = MAN[i][2] * k * sc; seed = int(MAN[i][4]); a = 0.86 * env * sc
        py2 = py + min(0.5 * 22 * k * age * age, H * 0.5)
        for j in range(5):
            an = j * TAU / 5 + rnd(seed + j, 71) * 1.1
            dd = r * (0.06 + 0.30 * rnd(seed + j, 72))
            T.circ(px + math.cos(an) * dd, py2 + math.sin(an) * dd * 0.9,
                   r * (0.30 + 0.26 * rnd(seed + j, 73)), (*BLOOD, a))
        for j in range(5):
            an = rnd(seed + j, 84) * TAU
            dd = r * (1.1 + 1.5 * rnd(seed + j, 85))
            T.circ(px + math.cos(an) * dd, py2 + math.sin(an) * dd * 0.85,
                   r * (0.06 + 0.10 * rnd(seed + j, 86)), (*BLOOD, a * 0.9))
        for j in range(5):
            an = (j * TAU / 5 + rnd(seed + j, 74) * 0.7) * 0.55 + 1.0
            L = r * (0.5 + 1.1 * rnd(seed + j, 75))
            ex, ey = px + math.cos(an) * L, py2 + math.sin(an) * L
            T.seg(px, py2, ex, ey, r * 0.13, (*BLOOD, a * 0.9), (*BLOOD, 0))
            T.circ(ex, ey, r * 0.08, (*BLOOD, a * 0.7))
        for j in range(1 + int(MAN[i][5] * 2)):
            dx = px + (rnd(seed + j, 76) - 0.5) * r * 1.2
            y0 = py2 + r * 0.5
            L = min(H * 0.55, 0.5 * 26 * k * age * age * (0.5 + rnd(seed + j, 78)))
            if L < 3: continue
            wd = r * (0.09 + 0.06 * rnd(seed + j, 79))
            T.seg(dx, y0, dx + 1.2 * k * math.sin(age * 2.1 + j), y0 + L, wd, (*DEEP, a * 0.92), (*BLOOD, a * 0.92))
            T.elipse(dx, y0 + L, 0, 1, wd * 0.95, wd * 1.65, (*BLOOD, a * 0.95))
        T.brilho(px - r * 0.22, py2 - r * 0.26, r * 0.30, (0xFF, 0x3A, 0x3A), 0.10 * env * sc)

    # ---- cortina
    gg = eo(cl((t - 0.45) / 1.1))
    if gg > 0.02:
        def topo(x):
            return (6 * k + 3 * k * math.sin(x * 0.037 + t * 1.4) + 2 * k * math.sin(x * 0.10 - t * 2.1)) * gg
        for x in range(0, W, 10):
            xb = min(W, x + 10)
            ha, hb = topo(x), topo(xb)
            T.quad([(x, 0), (xb, 0), (xb, hb), (x, ha)], [(*CRUST, 0.95 * env)] * 2 + [(*DEEP, 0.95 * env)] * 2)
        for i in range(7):
            xx = (i + 0.2 + 0.65 * rnd(i, 31)) / 7 * W
            tau = t - (0.5 + 0.6 * rnd(i, 32))
            if tau <= 0: continue
            r = (2.6 + 2.2 * rnd(i, 33)) * k; v0 = (7 + 9 * rnd(i, 34)) * k; ga = 12 * k
            yh = min(topo(xx) + v0 * tau + 0.5 * ga * tau * tau, H * 0.13)
            c = (*BLOOD, 0.95 * env)
            wh = 0.42 * (0.6 + 0.8 * rnd(i, 35))
            T.quad([(xx - wh * r, 0), (xx + wh * r, 0), (xx + 0.62 * wh * r * 1.5, yh), (xx - 0.62 * wh * r * 1.5, yh)], [c] * 4)
            T.elipse(xx, yh, 0, 1, r * 0.92, r * 1.45, c)
            T.circ(xx - 0.25 * r, yh - 0.35 * r, r * 0.18, (*GLOSS, 0.18 * env))

    # ---- grão
    for i in range(240):
        x = rnd(i, 7 * 4 + 1) * W; y = rnd(i, 11 * 4 + 3) * H
        r = (0.4 + 1.1 * rnd(i, 4)) * k * 0.5
        claro = rnd(i, 5) > 0.86
        rgb_g = (0xFF, 0x30, 0x20) if claro else (0, 0, 0)
        T.circ(x, y, r, (rgb_g[0], rgb_g[1], rgb_g[2], (0.05 if claro else 0.10) * env))

    # ---- poça
    lf = ss(0.5, 1.6, t) * (1 - ss(DUR - 1.5, DUR - 0.2, t))
    pa = cl(lf * 4)
    if pa > 0.01:
        def poolY(x):
            n = (rnd(int(x / (14 * k)), 71) - 0.5) * 5
            return H - (0.02 + 0.10 * lf) * H + n * k - 3 * k * math.sin(x * 0.028 + t * 2.3) - 1.6 * k * math.sin(x * 0.083 - t * 3.2)
        for x in range(0, W, 8):
            xb = min(W, x + 8)
            ya, yb = poolY(x), poolY(xb)
            T.quad([(x, ya), (xb, yb), (xb, H), (x, H)],
                   [(*BLOOD, 0.95 * pa), (*BLOOD, 0.95 * pa), (*CRUST, 0.98 * pa), (*CRUST, 0.98 * pa)])
            T.seg(x, ya + k * 0.5, xb, yb + k * 0.5, 1.2, (*GLOSS, 0.28 * pa), (*GLOSS, 0.28 * pa))

    # ---- clarão + miolo vivo (aditivo)
    flash = max(0.0, 1 - (t - RUPTURA) / 0.28) if t > RUPTURA else 0.0
    if flash > 0.01:
        T.rect(0, 0, W, H, (0xC0, 0x10, 0x18, 0.30 * flash ** 3), aditivo=True)
    if ab > 0.02:
        ca = (0.22 + 0.42 * beat) * env
        T.brilho(cx, cy, (26 + 16 * beat) * k, BLOOD_B, ca)
        T.brilho(cx, cy, 12 * k, (0xFF, 0x6A, 0x6A), ca * 0.7)

    # ---- TEXTO
    S = k * 1.9
    ty = cy - 4 * S
    fnt_px = 160
    fonte = ImageFont.truetype(FONTE, fnt_px)
    tot = sum(6 + (1.5 if i < len(msg) - 1 else 0) for i in range(len(msg)))
    x0 = cx - tot * S / 2
    xs, acc = [], 0.0
    for i in range(len(msg)):
        xs.append(x0 + acc * S); acc += 6 + 1.5
    for i, ch in enumerate(msg):
        la = cl((t - 0.18 - 0.055 * i) / 0.20)
        if la <= 0.05: continue
        e = eo(la)
        sc = S * (1 + 0.75 * (1 - e))
        alvo_h = max(2, int(round(8 * sc)))
        tmp = Image.new("RGBA", (240, 240), (0, 0, 0, 0))
        ImageDraw.Draw(tmp).text((12, 0), ch, font=fonte, fill=(216, 24, 34, 255))
        bb = tmp.getbbox()
        if not bb: continue
        tmp = tmp.crop(bb)
        tmp = tmp.resize((max(1, int(tmp.width * alvo_h / tmp.height)), alvo_h), Image.LANCZOS)
        # halo/crosta: versão escura maior
        halo = Image.new("RGBA", (tmp.width + 4, tmp.height + 4), (*CRUST, 0))
        a_cam = np.asarray(tmp, np.float64)[..., 3:4] / 255.0 * (la * env)
        rgb = np.asarray(tmp, np.float64)[..., :3]
        halo_a = np.zeros((tmp.height + 4, tmp.width + 4, 1))
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                halo_a[2 + dy:2 + dy + tmp.height, 2 + dx:2 + dx + tmp.width, :] = np.maximum(
                    halo_a[2 + dy:2 + dy + tmp.height, 2 + dx:2 + dx + tmp.width, :], a_cam)
        px = int(xs[i]); py = int(ty - 4 * (sc - S))
        # compõe halo + corpo
        x1, y1 = px - 2, py - 2
        x2, y2 = x1 + halo.width, y1 + halo.height
        sub_img = T.img[max(0, y1):y2, max(0, x1):x2]
        if sub_img.size == 0: continue
        hh, ww = sub_img.shape[:2]
        ha = halo_a[:hh, :ww]
        crgb = np.array(CRUST) / 255.0
        sub_img[:] = sub_img * (1 - ha) + crgb * ha
        cy0, cx0 = max(0, py), max(0, px)
        rh, rw = min(rgb.shape[0], T.h - cy0), min(rgb.shape[1], T.w - cx0)
        if rh <= 0 or rw <= 0: continue
        a2 = a_cam[:rh, :rw]
        reg = T.img[cy0:cy0 + rh, cx0:cx0 + rw]
        T.img[cy0:cy0 + rh, cx0:cx0 + rw] = reg * (1 - a2) + rgb[:rh, :rw] / 255.0 * a2

    # ---- filetes das letras
    for i in range(len(msg)):
        for dd in range(3):
            sd = i * 13 + dd * 5
            tau = t - (0.75 + 0.22 * i + 0.45 * rnd(sd, 62))
            if tau <= 0: continue
            maxL = (18 + 46 * rnd(sd, 63)) * k
            L = min(maxL, 7 * k * tau + 4.2 * k * tau * tau)
            dx = xs[i] + 6 * S * (0.18 + 0.64 * rnd(sd, 64))
            y0 = ty + 6.6 * S
            wd = (0.9 - 0.25 * dd) * k
            T.seg(dx, y0, dx, y0 + L, wd, (*DEEP, 0.95 * env), (*BLOOD, 0.95 * env))
            T.circ(dx, y0 + L, wd * 1.15, (*BLOOD, 0.95 * env))
            T.circ(dx - wd * 0.3, y0 + L - wd * 0.4, wd * 0.35, (*GLOSS, 0.40 * env))

    # ---- mensagem
    sa = ss(0.75, 1.35, t) * env
    if sub and sa > 0.03:
        S2 = min(k * 0.75, W * 0.85 / max(1, len(sub) * 6))
        alvo_h = max(2, int(round(8 * S2)))
        tmp = Image.new("RGBA", (4000, 240), (0, 0, 0, 0))
        ImageDraw.Draw(tmp).text((10, 0), sub, font=fonte, fill=(255, 107, 107, 255))
        bb = tmp.getbbox()
        tmp = tmp.crop(bb)
        tmp = tmp.resize((max(1, int(tmp.width * alvo_h / tmp.height)), alvo_h), Image.LANCZOS)
        px = int(cx - tmp.width / 2); py = int(cy + 25 * k)
        arr = np.asarray(tmp, np.float64)
        hh, ww = arr.shape[:2]
        y2, x2 = min(T.h, py + hh), min(T.w, px + ww)
        if y2 <= py or x2 <= px:
            pass
        else:
            a2 = (arr[:y2 - py, :x2 - px, 3:4] / 255.0) * sa
            reg = T.img[py:y2, px:x2]
            T.img[py:y2, px:x2] = reg * (1 - a2) + arr[:y2 - py, :x2 - px, :3] / 255.0 * a2

    T.vinheta(min(W, H) * 0.30, CRUST, (0.55 + 0.20 * beat) * env)
    return np.clip(T.img, 0, 1) ** (1 / 1.05)


def salvar(t, nome, W=1920, H=1080, sub="VOCE ESTA SANGRANDO"):
    img = renderizar(t, W, H, sub=sub)
    Image.fromarray((img * 255).astype(np.uint8)).save(os.path.join(OUT, nome))
    print("ok", nome)


if __name__ == "__main__":
    salvar(0.42, "4_3_0_sangue_ruptura.png")
    salvar(1.30, "4_3_0_sangue_jato.png")
    salvar(2.72, "4_3_0_sangue_meio.png")
    salvar(4.30, "4_3_0_sangue_estavel.png")
    # tira de 6 quadros 1280x720
    frames = [0.18, 0.36, 1.30, 2.72, 4.30, 5.55]
    tira = Image.new("RGB", (1280 * 2, 720 * 3))
    for i, tt in enumerate(frames):
        im = Image.fromarray((renderizar(tt, 1280, 720) * 255).astype(np.uint8))
        tira.paste(im, ((i % 2) * 1280, (i // 2) * 720))
    tira.save(os.path.join(OUT, "4_3_0_sangue_timeline.png"))
    print("ok tira")


def gerar_gif(nome="4_3_0_sangue_animacao.gif", W=800, H=450, fps=12):
    """GIF da animação inteira (6 s) — para ver o movimento, não só quadros."""
    quadros = []
    n = int(DUR * fps)
    for i in range(n):
        t = i / fps
        arr = (np.clip(renderizar(t, W, H), 0, 1) * 255).astype(np.uint8)
        quadros.append(Image.fromarray(arr).convert("P", palette=Image.ADAPTIVE, colors=128))
    quadros[0].save(os.path.join(OUT, nome), save_all=True, append_images=quadros[1:],
                    duration=int(1000 / fps), loop=0, optimize=True)
    print("ok gif", nome, n, "quadros")
