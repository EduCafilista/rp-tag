#!/usr/bin/env python3
"""Gera as texturas do TABLET da Ficha (4.8.1): buraco negro, nebulosa, anel.

Tudo procedural e com anti-aliasing de verdade (gradientes em float), porque o
dono reclamou dos "pixels quebrados" que apareciam atras do personagem quando o
buraco negro era desenhado com retangulos de 1 px na GUI.
"""
import numpy as np
from PIL import Image, ImageFilter

import os
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src/main/resources/assets/rptag/textures/gui") + os.sep


def suave(a, b, x):
    d = (b - a)
    if abs(d) < 1e-6:
        d = 1e-6 if d >= 0 else -1e-6
    t = np.clip((x - a) / d, 0, 1)
    return t * t * (3 - 2 * t)


def borda_fade(N, margem):
    """1 no meio, caindo para 0 nas bordas (tira o corte reto da textura)."""
    yy, xx = np.mgrid[0:N, 0:N].astype(float)
    fx = suave(0, margem, xx) * suave(0, margem, N - 1 - xx)
    fy = suave(0, margem, yy) * suave(0, margem, N - 1 - yy)
    return fx * fy


def lerp_cor(c0, c1, t):
    c0 = np.array(c0, dtype=float)
    c1 = np.array(c1, dtype=float)
    t = t[..., None]
    return c0 * (1 - t) + c1 * t


# ----------------------------------------------------------------- buraco negro
def buraco():
    N = 640
    y, x = np.mgrid[0:N, 0:N].astype(float)
    cx, cy = 320.0, 322.0
    ang = np.radians(-12.0)                       # disco inclinado, como a referencia
    dx, dy = x - cx, y - cy
    u = dx * np.cos(ang) + dy * np.sin(ang)
    v = -dx * np.sin(ang) + dy * np.cos(ang)

    R = 118.0
    q = np.clip(np.abs(u) / 380.0, 0, 1)
    esp = 28.0 * (1.0 - 0.62 * q ** 1.5) + 4.0
    perfil = np.exp(-(v / esp) ** 2 * 2.6)
    fio = np.exp(-(v / (esp * 0.26)) ** 2) * 0.9
    intens = np.clip(perfil + fio * 0.75, 0, 1.15)

    # cor: branco so no nucleo, depois amarelo -> laranja -> vermelho nas pontas
    t = np.clip(np.abs(u) / 390.0, 0, 1)
    cor = lerp_cor((255, 246, 214), (255, 206, 96), np.clip(t / 0.22, 0, 1))
    cor = cor * (1 - np.clip((t - 0.22) / 0.26, 0, 1)[..., None]) + \
        np.array((255, 140, 34)) * np.clip((t - 0.22) / 0.26, 0, 1)[..., None]
    cor = cor * (1 - np.clip((t - 0.48) / 0.30, 0, 1)[..., None]) + \
        np.array((232, 56, 18)) * np.clip((t - 0.48) / 0.30, 0, 1)[..., None]
    cor = cor * (1 - np.clip((t - 0.78) / 0.22, 0, 1)[..., None]) + \
        np.array((130, 12, 10)) * np.clip((t - 0.78) / 0.22, 0, 1)[..., None]

    # gas: o disco nao e liso
    gas = 0.82 + 0.18 * np.sin(u * 0.052 + 1.3) * np.cos(v * 0.021 + 0.4)
    alfa_d = np.clip(intens * gas, 0, 1) * (1.0 - 0.45 * np.clip((t - 0.70) / 0.30, 0, 1))
    disco = Image.fromarray(np.dstack([cor, alfa_d * 255]).astype(np.uint8), "RGBA")

    # halo: laranja (nao branco) para nao lavar a tela
    halo_src = Image.fromarray(np.dstack([np.full((N, N, 3), (255, 126, 40), dtype=float),
                                          alfa_d * 255]).astype(np.uint8), "RGBA")
    h1 = halo_src.filter(ImageFilter.GaussianBlur(22))
    h2 = halo_src.filter(ImageFilter.GaussianBlur(64))
    h1.putalpha(Image.fromarray((np.array(h1.split()[3], dtype=float) * 0.42).astype(np.uint8)))
    h2.putalpha(Image.fromarray((np.array(h2.split()[3], dtype=float) * 0.20).astype(np.uint8)))

    # esfera
    r = np.hypot(dx, dy)
    dentro = suave(R + 1.2, R - 1.2, r)
    grad = np.clip(r / R, 0, 1)
    esf_cor = lerp_cor((2, 2, 5), (22, 6, 12), grad ** 2)
    esfera = Image.fromarray(np.dstack([esf_cor, dentro * 255]).astype(np.uint8), "RGBA")

    # borda quente da esfera
    borda = np.exp(-((r - R) / 2.2) ** 2)
    anel_cor = lerp_cor((255, 236, 180), (255, 96, 26), np.clip(grad, 0, 1) ** 0.5)
    anel = Image.fromarray(np.dstack([anel_cor, np.clip(borda, 0, 1) * 240]).astype(np.uint8), "RGBA")
    anel_glow = anel.filter(ImageFilter.GaussianBlur(6))
    anel_glow.putalpha(Image.fromarray((np.array(anel_glow.split()[3], dtype=float) * 0.75).astype(np.uint8)))

    # a parte do disco que passa NA FRENTE da esfera (faixa nitida)
    frente_a = np.array(disco.split()[3], dtype=float) * np.clip(1.0 - (np.abs(u) / (R * 0.98)) ** 2, 0, 1) * 0.92
    frente = disco.copy()
    frente.putalpha(Image.fromarray(np.clip(frente_a, 0, 255).astype(np.uint8)))

    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for camada in (h2, h1, disco, esfera, anel_glow, anel, frente):
        out = Image.alpha_composite(out, camada)
    # desbota nas bordas: sem isso o quadrado da textura aparece na tela
    out.putalpha(Image.fromarray((np.array(out.split()[3], dtype=float) * borda_fade(N, 105)).astype(np.uint8)))
    out.save(OUT + "ficha_buraco.png")
    return out


# --------------------------------------------------------------------- nebulosa
def ruido(N, octaves=5, seed=3):
    rng = np.random.default_rng(seed)
    acc = np.zeros(N, dtype=float)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        h = max(2, N[0] >> (octaves - 1 - o))
        w = max(2, N[1] >> (octaves - 1 - o))
        base = rng.normal(0, 1, (h, w))
        img = Image.fromarray(((base - base.min()) / (np.ptp(base) + 1e-6) * 255).astype(np.uint8))
        img = img.resize((N[1], N[0]), Image.BICUBIC)
        acc += np.array(img, dtype=float) / 255.0 * amp
        tot += amp
        amp *= 0.55
    return acc / tot


def nebulosa():
    W, H = 1024, 576
    n1 = ruido((H, W), 7, 11)
    n2 = ruido((H, W), 6, 27)
    n3 = ruido((H, W), 5, 41)
    nuvem = np.clip((n1 * 0.50 + n2 * 0.32 + n3 * 0.18 - 0.38) / 0.44, 0, 1) ** 1.25

    # paleta da referencia: roxo/violeta com nucleo claro e bordas azul-escuras
    cor = lerp_cor((16, 9, 34), (86, 40, 132), np.clip(n2 * 1.7, 0, 1))
    cor = cor * (1 - np.clip(n1 * 1.3, 0, 1)[..., None]) + \
        np.array((132, 62, 178)) * np.clip(n1 * 1.3, 0, 1)[..., None]
    cor = cor + np.array((70, 30, 110)) * (nuvem ** 1.5)[..., None]
    # brilho suave em duas regioes (como as luzes da arte do dono)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    for (bx, by, br, forca, tom) in ((0.30, 0.34, 0.42, 0.55, (150, 110, 210)),
                                     (0.74, 0.62, 0.36, 0.35, (90, 90, 180)),
                                     (0.52, 0.20, 0.26, 0.28, (170, 120, 220))):
        d = np.hypot((xx / W - bx) * (W / H), (yy / H - by)) / br
        g = np.exp(-d ** 2 * 2.2)[..., None]
        cor = cor + np.array(tom) * g * forca

    alfa = np.clip(nuvem * 0.95 + 0.30, 0, 1)
    bx1 = suave(0, 70, xx) * suave(0, 70, W - 1 - xx)
    by1 = suave(0, 55, yy) * suave(0, 55, H - 1 - yy)
    alfa = alfa * bx1 * by1

    cor = np.clip(cor * 0.60, 0, 255)
    img = Image.fromarray(np.dstack([cor, alfa * 255]).astype(np.uint8), "RGBA")

    est = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = est.load()
    rng = np.random.default_rng(7)
    for _ in range(520):
        sx, sy = int(rng.integers(3, W - 3)), int(rng.integers(3, H - 3))
        b = rng.uniform(0.30, 1.0)
        cor_e = [(255, 255, 255), (205, 226, 255), (255, 232, 196), (222, 198, 255)][int(rng.integers(0, 4))]
        al = int(255 * b)
        px[sx, sy] = (*cor_e, al)
        if b > 0.86 and rng.random() < 0.55:
            for d, k in ((1, 0.38), (2, 0.16)):
                a2 = int(al * k)
                for ox, oy in ((d, 0), (-d, 0), (0, d), (0, -d)):
                    px[sx + ox, sy + oy] = (*cor_e, a2)
    est = est.filter(ImageFilter.GaussianBlur(0.6))
    out = Image.alpha_composite(img, est)
    out.save(OUT + "ficha_nebulosa.png")
    return out


# ------------------------------------------------------------------------- anel
def anel():
    W, H = 512, 256
    y, x = np.mgrid[0:H, 0:W].astype(float)
    cx, cy = W / 2, H / 2
    dx, dy = (x - cx) / 1.0, (y - cy) / 0.26
    r = np.hypot(dx, dy)

    def camada(raio, larg, cor, forca):
        d = (r - raio) / larg
        a = np.exp(-d ** 2) * forca
        c = np.array(cor, dtype=float)[None, None, :] * np.ones((H, W, 1))
        return Image.fromarray(np.dstack([c, np.clip(a, 0, 1) * 255]).astype(np.uint8), "RGBA")

    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for img in (camada(222, 14, (150, 220, 255), 0.30),
                camada(212, 5, (150, 226, 255), 0.95),
                camada(196, 3, (186, 142, 255), 0.75),
                camada(160, 26, (120, 190, 255), 0.20)):
        base = Image.alpha_composite(base, img.filter(ImageFilter.GaussianBlur(1.4)))
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    fade = suave(0, 26, xx) * suave(0, 26, W - 1 - xx) * suave(0, 18, yy) * suave(0, 18, H - 1 - yy)
    base.putalpha(Image.fromarray((np.array(base.split()[3], dtype=float) * fade).astype(np.uint8)))
    base.save(OUT + "ficha_anel.png")
    return base


if __name__ == "__main__":
    b = buraco()
    n = nebulosa()
    a = anel()
    b.resize((256, 256), Image.LANCZOS).save("/tmp/buraco_previa.png")
    n.resize((768, 432), Image.LANCZOS).save("/tmp/nebulosa_previa.png")
    a.resize((512, 256), Image.LANCZOS).save("/tmp/anel_previa.png")
    print("buraco", b.size, "nebulosa", n.size, "anel", a.size)
