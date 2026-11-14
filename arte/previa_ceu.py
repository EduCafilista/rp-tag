#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.47.0) PRÉVIA DO CÉU DE SANGUE — o mesmo desenho do CeuDeSangueFx, renderizado em Python.

Para quê: o dono reprovou o céu da 5.46.0 OLHANDO (\"tá horrível\") e eu não tenho o jogo aqui.
Este script refaz, em Python, a MESMA matemática do Java (`corCeu`, `nuvem`, as 22 veias, o aro
e o halo de textura) e cospe uma imagem de como o céu fica na tela — assim dá para conferir
antes de publicar que não sobrou FAIXA nem QUADRADO.

Regra: se mudar a conta no Java, muda aqui também (esta prévia é a prova visual).

Uso: python3 arte/previa_ceu.py [saida.png]
"""
import math
import os
import sys

import numpy as np
from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TEX = os.path.join(RAIZ, 'src', 'main', 'resources', 'assets', 'rptag', 'textures', 'environment')

LARG, ALT = 1000, 560
FOV_H = math.radians(92.0)      # campo de visão horizontal

# ---- os mesmos números do CeuDeSangueFx.java
ASTRO = 21.0
RAIO_LUA = 100.0
F = 1.0                          # força do terremoto
T = 3.7                          # instante (s)
PULSO = 0.5 + 0.5 * math.sin(T * 2.1 * math.pi)
FASE = T % 7.0
CLARAO = (1.0 - FASE / 0.28) ** 2 if FASE < 0.28 else 0.0


def veia(theta, phi, t):
    """.veia() do Java: faixas compridas que se torcem (alfa por vértice, borda macia)."""
    torcao = math.sin(theta * 2.0 + t * 0.07) * 0.55 + math.sin(theta * 4.7 - t * 0.05) * 0.25
    a = math.sin((phi + torcao) * 11.0 + t * 0.12)
    b = math.sin((phi + torcao) * 23.0 - t * 0.19 + theta * 3.0)
    return 0.5 + 0.5 * (0.62 * a + 0.38 * b)


def cor_ceu(phi):
    """corCeu(): h=1 no horizonte, 0 nos polos; cor POR VÉRTICE (sem degrau)."""
    h = 1.0 - abs(math.pi * 0.5 - phi) / (math.pi * 0.5)
    h = h * h * (3.0 - 2.0 * h)
    r = 72.0 + (196.0 - 72.0) * h
    g = 5.0 + (26.0 - 5.0) * h
    b = 12.0 + (22.0 - 12.0) * h
    r = r + (255.0 - r) * CLARAO
    g = g + (90.0 - g) * CLARAO
    b = b + (60.0 - b) * CLARAO
    a = min(1.0, F * (0.90 + 0.10 * h) + 0.24 * CLARAO * F)
    return r, g, b, a


def nuvem(theta, phi, t):
    a = math.sin(theta * 2.3 + t * 0.22) * math.sin(phi * 3.1 - t * 0.16)
    b = math.sin(theta * 5.7 - t * 0.31 + phi * 4.3)
    c = math.sin(theta * 11.0 + t * 0.47) * math.sin(phi * 6.0 + t * 0.13)
    return 0.5 + 0.5 * (0.5 * a + 0.3 * b + 0.2 * c)


def veias(t):
    """as 22 faixas escuras, como no desenharVeias(): (theta, largura, phi, comprimento, alfa)."""
    fora = []
    for k in range(22):
        semente = k * 1.7
        theta = semente + t * 0.035 * (1.0 if k % 2 == 0 else -1.0)
        phi = 0.35 + 0.85 * (0.5 + 0.5 * math.sin(semente * 2.1 + t * 0.05))
        largura = 0.045 + 0.05 * (0.5 + 0.5 * math.sin(semente * 3.3))
        comprimento = 0.35 + 0.55 * (0.5 + 0.5 * math.cos(semente * 1.9 + t * 0.02))
        fora.append((theta, largura, phi, comprimento, 0.30 * F))
    return fora


def main(saida):
    # ---- a câmera olha para o céu com a lua à vista (elevação 34°, azimute 0)
    az_lua, el_lua = 0.0, math.radians(34.0)
    dir_lua = np.array([math.cos(el_lua) * math.cos(az_lua),
                        math.sin(el_lua),
                        math.cos(el_lua) * math.sin(az_lua)])
    alvo = dir_lua
    olho = np.array([0.0, 0.0, 0.0])
    frente = alvo / np.linalg.norm(alvo)
    direita = np.cross(frente, np.array([0.0, 1.0, 0.0]))
    direita /= np.linalg.norm(direita)
    cima = np.cross(direita, frente)

    ys, xs = np.mgrid[0:ALT, 0:LARG]
    u = (xs - LARG / 2.0) / (LARG / 2.0)
    v = -(ys - ALT / 2.0) / (ALT / 2.0)
    escala = math.tan(FOV_H / 2.0)
    dirs = (frente[None, None, :]
            + direita[None, None, :] * (u[..., None] * escala)
            + cima[None, None, :] * (v[..., None] * escala * ALT / LARG))
    dirs /= np.linalg.norm(dirs, axis=2, keepdims=True)

    # ângulos do céu
    phi = np.arccos(np.clip(dirs[..., 1], -1.0, 1.0))       # 0 = zênite
    theta = np.arctan2(dirs[..., 2], dirs[..., 0]) % (2 * math.pi)
    cos_lua = np.clip(dirs @ dir_lua, -1.0, 1.0)
    ang_lua = np.arccos(cos_lua)                             # ângulo até a lua (rad)

    img = np.zeros((ALT, LARG, 3), dtype=np.float64)

    # 1) o domo (degradê por vértice: aqui é analítico, então é contínuo de verdade)
    hh = 1.0 - np.abs(math.pi * 0.5 - phi) / (math.pi * 0.5)
    hh = hh * hh * (3.0 - 2.0 * hh)
    r = 72.0 + (196.0 - 72.0) * hh
    g = 5.0 + (26.0 - 5.0) * hh
    b = 12.0 + (22.0 - 12.0) * hh
    r = r + (255.0 - r) * CLARAO
    g = g + (90.0 - g) * CLARAO
    b = b + (60.0 - b) * CLARAO
    a = np.clip(F * (0.90 + 0.10 * hh) + 0.24 * CLARAO * F, 0.0, 1.0)
    img[..., 0] = r * a
    img[..., 1] = g * a
    img[..., 2] = b * a

    # 2) as nuvens de sangue (casca de baixo, alfa só onde passa do corte)
    nv = np.vectorize(lambda th, ph: nuvem(th, ph, T))(theta, phi)
    alfa_nuvem = np.clip((nv - 0.40) * 1.45 * F, 0.0, 1.0)
    img = img * (1 - alfa_nuvem[..., None]) + np.array([40.0, 0.0, 4.0]) * alfa_nuvem[..., None]

    # 3) as veias escuras (casca contínua, alfa por vértice: sem borda dura)
    vv_ = np.vectorize(lambda th, ph: veia(th, ph, T))(theta, phi)
    alfa_veia = np.clip((vv_ - 0.52) * 1.15 * F, 0.0, 1.0)
    img = img * (1 - alfa_veia[..., None]) + np.array([22.0, 0.0, 2.0]) * alfa_veia[..., None]

    # 4) o aro aceso da lua
    raio_disco = math.atan(ASTRO / RAIO_LUA)                 # ~11,9°
    bater = 1.0 + 0.045 * PULSO + 0.10 * CLARAO
    aro0, aro1 = raio_disco * bater * 0.995, raio_disco * bater * 1.035
    alfa_aro = min(1.0, F * (0.30 + 0.45 * PULSO + 0.5 * CLARAO))
    dentro_aro = (ang_lua >= aro0) & (ang_lua <= aro1)
    img[dentro_aro] = (img[dentro_aro] * (1 - alfa_aro)
                       + np.array([255.0, 70.0, 30.0]) * alfa_aro)

    # 5) O HALO — a textura com degradê radial (alfa zero na borda: nem canto, nem anel)
    halo = np.asarray(Image.open(os.path.join(TEX, 'halo_de_sangue.png')).convert('RGBA'),
                      dtype=np.float64) / 255.0
    raio_halo = raio_disco * 3.6 * (1.0 + 0.05 * PULSO + 0.22 * CLARAO)
    alfa_halo = min(1.0, F * (0.72 + 0.28 * PULSO + 0.45 * CLARAO))
    rr = np.clip(ang_lua / raio_halo, 0.0, 1.0)
    iy = np.clip((rr * (halo.shape[0] - 1)).astype(int), 0, halo.shape[0] - 1)
    ha = halo[iy, halo.shape[1] // 2, 3][..., None] * alfa_halo
    hc = halo[iy, halo.shape[1] // 2, :3] * 255.0
    img = img * (1 - ha) + hc * ha

    # 6) a LUA (textura, no disco do céu)
    lua = np.asarray(Image.open(os.path.join(TEX, 'lua_de_sangue.png')).convert('RGBA'),
                     dtype=np.float64) / 255.0
    lado = lua.shape[0]
    # projeta o disco na tela: raio local 0..1 dentro do disco
    frente_l = dir_lua
    dir_l = np.cross(frente_l, np.array([0.0, 1.0, 0.0]))
    dir_l /= np.linalg.norm(dir_l)
    cima_l = np.cross(dir_l, frente_l)
    xu = dirs @ dir_l
    yu = dirs @ cima_l
    dentro = (ang_lua <= raio_disco * bater) & (np.abs(xu) <= 10) & (np.abs(yu) <= 10)
    uu = np.clip((xu / (raio_disco * bater) * 0.5 + 0.5), 0, 1)
    vv = np.clip((yu / (raio_disco * bater) * 0.5 + 0.5), 0, 1)
    ix = np.clip((uu * (lado - 1)).astype(int), 0, lado - 1)
    iy2 = np.clip(((1 - vv) * (lado - 1)).astype(int), 0, lado - 1)
    la = lua[iy2, ix, 3][..., None] * dentro[..., None]
    lc = lua[iy2, ix, :3] * 255.0
    img = img * (1 - la) + lc * la

    # 7) o chão (só para dar a noção de horizonte) — cinza escuro rachado
    chao = phi > math.pi * 0.5
    img[chao] = img[chao] * 0.18 + np.array([10.0, 3.0, 3.0])

    out = np.clip(img, 0, 255).astype(np.uint8)
    Image.fromarray(out).save(saida)
    print('prévia do céu: %s (%dx%d)' % (saida, LARG, ALT))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(RAIZ, 'arte', 'previa_ceu.png'))
