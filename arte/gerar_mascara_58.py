#!/usr/bin/env python3
"""(5.8.0) MÁSCARA DE PEDRA REFEITA — malha, textura e prévia num script só.

O que havia antes e por que foi refeito:

* **5.3.0** usou o `.bbmodel` do dono (`imports/imvaqj.bbmodel`, 116 cubos). A
  geometria está lá, mas a textura do arquivo é **pedra cinza com ruído** — não
  tem rosto nenhum pintado. Resultado no jogo: um bloco de pedra com dois
  buracos, "horrível".
* **4.9.6** era a malha procedural, mas com o relevo suave demais (parecia uma
  máscara de gesso derretida) e a textura 128×128 pintada à mão em outro script.

Esta versão junta o melhor dos dois: **geometria paramétrica com relevo de
escultura** (testa em Y, sobrancelha em V, órbitas em fenda, nariz, focinho,
presas, queixo pontudo, espiral na têmpora) + **textura pintada nas MESMAS
coordenadas (u,v) da geometria**, então o desenho nunca sai do lugar.

Saídas:
  src/main/resources/assets/rptag/geo/mascara_58.bin
  src/main/resources/assets/rptag/textures/entity/mascara_58.png
  previa/mascara_58.png                (4 vistas renderizadas com a malha real)

Uso: python3 arte/gerar_mascara_58.py
"""
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path("/home/user/rp-tag")
ASSETS = RAIZ / "src/main/resources/assets/rptag"
SAIDA_BIN = ASSETS / "geo/mascara_58.bin"
SAIDA_PNG = ASSETS / "textures/entity/mascara_58.png"
SAIDA_PREVIA = RAIZ / "previa/mascara_58.png"

# ---------------------------------------------------------------- dimensões (px)
COLS, ROWS = 44, 56          # grade da casca (u, v)
Y_TOPO, Y_QUEIXO = -8.15, -0.15
Z_FRENTE = -5.05             # plano médio do rosto
ENROLA = 1.55                # quanto a borda vai para trás
ESPESSURA = 0.75             # grossura da pedra
TEX = 256                    # textura (256 = traço fino sem serrilhado)


# ------------------------------------------------------------------- silhueta
# meia-largura (px) por altura v (0 = queixo, 1 = topo) — silhueta de ESCUDO
V_W = [0.00, 0.05, 0.12, 0.22, 0.34, 0.46, 0.58, 0.70, 0.80, 0.88, 0.94, 0.98, 1.00]
W_W = [0.45, 1.20, 2.00, 2.85, 3.42, 3.80, 3.98, 4.02, 3.94, 3.70, 3.24, 2.55, 1.45]


def largura(v):
    return np.interp(v, V_W, W_W)


def gauss(x, s):
    return np.exp(-(x / s) ** 2)


# -------------------------------------------------------------------- relevo
# positivo = para FORA (para -Z). Tudo em px.
def relevo(u, v):
    a = np.abs(u)
    r = np.zeros_like(u)

    # ---- TESTA: crista central em Y (as linhas do JoJo) + duas diagonais
    r += 0.62 * gauss(u, 0.075) * np.clip((v - 0.66) / 0.34, 0, 1) ** 0.6
    diag = v - (0.98 - 0.42 * a)          # reta que desce para os lados
    r += 0.30 * gauss(diag, 0.055) * gauss(a - 0.45, 0.30) * (v > 0.72)
    # ---- TESTA: leve inchaço geral
    r += 0.14 * gauss(u, 0.75) * gauss(v - 0.88, 0.13)

    # ---- SOBRANCELHA em V (brava): desce para o meio
    vb = 0.705 - 0.10 * a
    r += 0.50 * gauss(v - vb, 0.028) * gauss(a - 0.34, 0.22)

    # ---- ÓRBITAS: fenda funda, inclinada (canto de fora sobe)
    vo = 0.625 + 0.055 * a
    fenda = gauss(v - vo, 0.024) * gauss(a - 0.40, 0.20)
    r -= 1.25 * fenda
    # borda de baixo da órbita (o osso da maçã) sobe
    r += 0.34 * gauss(v - (vo - 0.055), 0.020) * gauss(a - 0.40, 0.20)

    # ---- NARIZ: ponte fina, dorso saliente, ponta e asas
    r += 0.55 * gauss(u, 0.085) * gauss(v - 0.60, 0.085)
    r += 0.95 * gauss(u, 0.115) * gauss(v - 0.475, 0.070)
    r += 0.70 * gauss(u, 0.145) * gauss(v - 0.375, 0.045)
    r += 0.30 * gauss(a - 0.19, 0.075) * gauss(v - 0.345, 0.035)      # asas
    r -= 0.45 * gauss(a - 0.145, 0.045) * gauss(v - 0.325, 0.024)     # narinas

    # ---- MAÇÃS
    r += 0.42 * gauss(a - 0.60, 0.16) * gauss(v - 0.47, 0.075)

    # ---- FOÇINHO / BOCA
    r += 0.45 * gauss(u, 0.42) * gauss(v - 0.285, 0.070)              # focinho
    r -= 0.95 * gauss(u, 0.30) * gauss(v - 0.225, 0.020)              # fenda da boca
    r += 0.34 * gauss(u, 0.30) * gauss(v - 0.262, 0.020)              # lábio de cima
    r += 0.26 * gauss(u, 0.26) * gauss(v - 0.188, 0.018)              # lábio de baixo
    # presas (dois caninos que sobem do lábio de baixo)
    for s in (-1, 1):
        r += 0.60 * gauss(u - s * 0.135, 0.030) * gauss(v - 0.203, 0.020)

    # ---- QUEIXO projetado
    r += 0.40 * gauss(u, 0.24) * gauss(v - 0.075, 0.055)
    r -= 0.30 * gauss(v - 0.008, 0.030)                               # borda de baixo

    # ---- ESPIRAL na têmpora (a voluta do modelo do dono), dos dois lados
    du = a - 0.72
    dv = v - 0.80
    ang = np.arctan2(dv * 2.2, du)
    rad = np.sqrt(du ** 2 + (dv * 0.75) ** 2)
    espiral = np.cos(ang * 1.0 - rad * 22.0) * gauss(rad - 0.135, 0.075)
    r += 0.34 * espiral

    # ---- RACHADURAS gravadas na pedra (finas, irregulares)
    rasgo = (np.sin(u * 23.0 + v * 7.0) * 0.5 + 0.5) ** 14
    r -= 0.10 * rasgo * gauss(a - 0.86, 0.13)
    return r


# ------------------------------------------------------------------ superfície
def ponto(u, v, casca):
    """(x, y, z) em px do modelo. casca 1.0 = externa, 0.0 = interna."""
    w = largura(v)
    x = u * w
    y = Y_QUEIXO + (Y_TOPO - Y_QUEIXO) * v
    # curvatura: frente no meio, bordas para trás
    z = Z_FRENTE + ENROLA * (np.abs(u) ** 2.4)
    z = z - relevo(u, v) * (1.0 if casca > 0.5 else 0.30)
    if casca <= 0.5:
        z = z + ESPESSURA
    return np.stack([x, y, z], axis=-1)


def normal(u, v, casca):
    e = 0.004
    uu = np.clip(u, -1, 1)
    vv = np.clip(v, 0.001, 0.999)
    a = ponto(np.minimum(1, uu + e), vv, casca)
    b = ponto(np.maximum(-1, uu - e), vv, casca)
    c = ponto(uu, np.minimum(1, vv + e), casca)
    d = ponto(uu, np.maximum(0, vv - e), casca)
    n = np.cross(a - b, c - d)
    ln = np.linalg.norm(n, axis=-1, keepdims=True)
    n = n / np.maximum(ln, 1e-9)
    # a frente olha para -Z
    flip = n[..., 2] > 0
    n[flip] *= -1
    if casca <= 0.5:
        n = -n
    return n


# --------------------------------------------------------------------- malha
def uv_de(u, v):
    """UV planar (a mesma para geometria e textura)."""
    s = 0.5 + 0.47 * u
    t = 1.0 - (0.035 + 0.93 * v)
    return np.stack([s, t], axis=-1)


def grade(casca):
    us = np.linspace(-1, 1, COLS)
    vs = np.linspace(0, 1, ROWS)
    U, V = np.meshgrid(us, vs, indexing="xy")          # (ROWS, COLS)
    P = ponto(U, V, casca)
    N = normal(U, V, casca)
    T = uv_de(U, V)

    tris_p, tris_n, tris_t = [], [], []
    for j in range(ROWS - 1):
        for i in range(COLS - 1):
            ids = [(j, i), (j, i + 1), (j + 1, i + 1), (j + 1, i)]
            if casca > 0.5:
                ordem = [0, 1, 2, 0, 2, 3]
            else:
                ordem = [0, 2, 1, 0, 3, 2]
            for k in ordem:
                jj, ii = ids[k]
                tris_p.append(P[jj, ii])
                tris_n.append(N[jj, ii])
                tris_t.append(T[jj, ii])
    return np.array(tris_p), np.array(tris_n), np.array(tris_t)


def aba():
    """liga a borda externa à interna (fecha o volume) pelas 4 bordas da grade."""
    us = np.linspace(-1, 1, COLS)
    vs = np.linspace(0, 1, ROWS)
    P, N, T, NR = [], [], [], []
    bordas = [
        (us, np.full_like(us, 0.0), 0),        # queixo
        (us, np.full_like(us, 1.0), 1),        # topo
        (np.full_like(vs, -1.0), vs, 2),       # lado esquerdo
        (np.full_like(vs, 1.0), vs, 3),        # lado direito
    ]
    for uu, vv, _ in bordas:
        fo = ponto(uu, vv, 1.0)
        fi = ponto(uu, vv, 0.0)
        uvt = uv_de(uu, vv)
        for k in range(len(uu) - 1):
            quad = [(fo[k], uvt[k]), (fo[k + 1], uvt[k + 1]),
                    (fi[k + 1], uvt[k + 1]), (fi[k], uvt[k])]
            seq = [0, 1, 2, 0, 2, 3]
            if _ == 1 or _ == 2:
                seq = [0, 2, 1, 0, 3, 2]
            for t in range(0, 6, 3):
                a_, b_, c_ = quad[seq[t]][0], quad[seq[t + 1]][0], quad[seq[t + 2]][0]
                nr = np.cross(b_ - a_, c_ - a_)
                ln = np.linalg.norm(nr)
                nr = nr / ln if ln > 1e-9 else np.array([0.0, 0.0, 1.0])
                for k2 in range(3):
                    pos, tuv = quad[seq[t + k2]]
                    P.append(pos)
                    T.append(tuv)
                    NR.append(nr)
    return np.array(P), np.array(NR), np.array(T)


def monta():
    P, N, T = [], [], []
    for casca in (1.0, 0.0):
        p, n, t = grade(casca)
        # 1/16 de passo? nada: a malha já está em px do modelo
        P.append(p); N.append(n); T.append(t)
    p, n, t = aba()
    P.append(p); N.append(n); T.append(t)
    return np.concatenate(P), np.concatenate(N), np.concatenate(T)


# --------------------------------------------------------------------- textura
# O desenho é ANALÍTICO em (u,v): cada traço é uma distância a um segmento, com
# borda suave. Assim a mesma pintura serve pra qualquer resolução e continua
# alinhada com a malha (a geometria usa as MESMAS coordenadas).
def _seg_dist(u, v, p0, p1):
    ax, ay = p0
    bx, by = p1
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy + 1e-9
    t = np.clip(((u - ax) * dx + (v - ay) * dy) / L, 0, 1)
    px, py = ax + dx * t, ay + dy * t
    return np.hypot(u - px, v - py)


def _stroke(pts, largura, suave=0.02):
    """cobertura 0..1 de uma polilinha (lista de pontos (u,v))."""
    cov = np.zeros(U.shape)
    for k in range(len(pts) - 1):
        d = _seg_dist(U, V, pts[k], pts[k + 1])
        cov = np.maximum(cov, np.clip((largura - d) / suave + 0.5, 0, 1))
    return cov


def _mancha(cu, cv, ru, rv, suave=0.05):
    d = np.sqrt(((U - cu) / ru) ** 2 + ((V - cv) / rv) ** 2)
    return np.clip((1 - d) / suave + 0.5, 0, 1)


def _com(cor, cov, rgb):
    for k in range(3):
        cor[..., k] = cor[..., k] * (1 - cov) + rgb[k] * cov


def gera_textura(caminho):
    global U, V
    S = TEX
    u = np.linspace(-1, 1, S)[None, :].repeat(S, 0)
    v = np.linspace(1, 0, S)[:, None].repeat(S, 1)   # LINHA 0 = TOPO = testa (v=1)
    U, V = u, v
    a = np.abs(u)

    ESCURO = np.array([0.075, 0.075, 0.09])
    PRETO = np.array([0.035, 0.035, 0.045])
    BRANCO = np.array([0.95, 0.96, 0.98])

    # ---------------- PEDRA: cinza claro azulado, com grão e nuvens
    rng = np.random.default_rng(58)
    grao = np.clip(rng.normal(0, 0.030, (S, S)), -0.10, 0.10)
    nuvem = (np.sin(u * 5.3 + 1.3) * np.cos(v * 7.7 - 0.4) * 0.5 + 0.5) * 0.05
    base = 0.735 + grao + nuvem
    rel = relevo(u, v)                       # o relevo escurece/clareia a pedra
    base = base + np.clip(rel, -1.4, 1.2) * 0.10
    cor = np.stack([base * 0.86, base * 0.90, base * 0.98], axis=-1)   # pedra azulada

    # ---------------- A "PINTURA" PRETA em volta dos olhos (marca do JoJo):
    # máscara de pincelada que cobre as órbitas e desce em bico entre elas
    olho_esq = _mancha(-0.42, 0.640, 0.310, 0.130, 0.22)
    olho_dir = _mancha(0.42, 0.640, 0.310, 0.130, 0.22)
    bico = np.clip((0.20 - np.abs(u)) / 0.20, 0, 1) * np.clip((v - 0.55) / 0.10, 0, 1) * 0.85
    pintura = np.clip(np.maximum(olho_esq, olho_dir) * 0.92 + bico * 0.6, 0, 1)
    _com(cor, pintura, ESCURO)

    # ---------------- OLHOS: amêndoa BRANCA inclinada + pupila vertical
    for s_ in (-1, 1):
        du = u - s_ * 0.395
        eixo = 0.640 + du * 0.16 * s_        # canto de fora sobe (olhar bravo)
        am = 1 - np.sqrt((du / 0.170) ** 2 + ((v - eixo) / 0.042) ** 2)
        _com(cor, np.clip(am / 0.10 + 0.5, 0, 1), BRANCO)
        # pupila
        _com(cor, _mancha(s_ * 0.345, 0.640, 0.036, 0.030, 0.012), PRETO)
        # pálpebra de cima: cobre só a METADE DE CIMA do amêndoa (v maior = mais alto)
        cov = np.clip((v - (eixo + 0.030)) / 0.012, 0, 1) * np.clip(am / 0.10 + 0.5, 0, 1)
        _com(cor, cov, ESCURO)
        # cílio de fora, puxado pra cima
        _com(cor, _stroke([(s_ * 0.22, 0.668), (s_ * 0.45, 0.690), (s_ * 0.60, 0.678)], 0.020, 0.02), PRETO)

    # ---------------- SOBRANCELHA em V (grossa)
    for s_ in (-1, 1):
        _com(cor, _stroke([(s_ * 0.66, 0.762), (s_ * 0.34, 0.726), (s_ * 0.085, 0.700)], 0.042, 0.03), ESCURO)

    # ---------------- NARIZ: sombras laterais + narinas + ponta clara
    for s_ in (-1, 1):
        _com(cor, _stroke([(s_ * 0.075, 0.690), (s_ * 0.105, 0.52), (s_ * 0.165, 0.372)], 0.020, 0.03), ESCURO)
        _com(cor, _mancha(s_ * 0.145, 0.332, 0.030, 0.014, 0.012), PRETO)
    _com(cor, _mancha(0.0, 0.392, 0.075, 0.045, 0.04), np.array([0.86, 0.87, 0.90]))
    _com(cor, _stroke([(0.0, 0.30), (0.0, 0.26)], 0.016, 0.02), ESCURO)

    # ---------------- BOCA: vão escuro, lábios marcados
    _com(cor, _mancha(0.0, 0.228, 0.290, 0.036, 0.04), PRETO)
    _com(cor, _stroke([(-0.30, 0.266), (0.0, 0.278), (0.30, 0.266)], 0.018, 0.02), ESCURO)
    _com(cor, _stroke([(-0.26, 0.186), (0.0, 0.178), (0.26, 0.186)], 0.016, 0.02), ESCURO)
    # fileira de dentes pequenos no fundo da boca
    for k in range(-2, 3):
        if abs(k) == 2:
            continue
        _com(cor, _mancha(k * 0.048, 0.222, 0.020, 0.012, 0.010), np.array([0.80, 0.80, 0.82]))
    # presas: dois caninos compridos
    for s_ in (-1, 1):
        for k in range(9):
            fy = 0.262 - k * 0.0075
            fw = 0.012 + k * 0.0052
            _com(cor, _mancha(s_ * 0.150, fy, fw, 0.0070, 0.009), BRANCO)
        _com(cor, _mancha(s_ * 0.148, 0.198, 0.040, 0.009, 0.012), PRETO * 0.9)

    # ---------------- MANDÍBULA e maçãs: hachura diagonal fina
    for s_ in (-1, 1):
        for k in range(4):
            x0 = s_ * (0.52 + k * 0.070)
            _com(cor, _stroke([(x0, 0.560), (x0 + s_ * 0.045, 0.415)], 0.011, 0.02), ESCURO * 0.95)
        _com(cor, _stroke([(s_ * 0.90, 0.470), (s_ * 0.76, 0.200), (s_ * 0.42, 0.048)], 0.026, 0.03), ESCURO)

    # ---------------- TESTA: crista em Y + diagonais
    _com(cor, _stroke([(0.0, 1.00), (0.0, 0.815)], 0.028, 0.02), ESCURO)
    for s_ in (-1, 1):
        _com(cor, _stroke([(0.0, 0.870), (s_ * 0.185, 0.995)], 0.026, 0.02), ESCURO)
        _com(cor, _stroke([(0.0, 0.815), (s_ * 0.215, 0.712)], 0.022, 0.02), ESCURO)
        _com(cor, _stroke([(s_ * 0.30, 0.945), (s_ * 0.52, 0.820)], 0.016, 0.02), ESCURO * 0.85)

    # ---------------- ESPIRAL na têmpora
    for s_ in (-1, 1):
        pts = []
        for t in np.linspace(0, 2.5 * np.pi, 26):
            rad = 0.018 + 0.026 * t
            pts.append((s_ * (0.72 + rad * np.cos(t)), 0.815 + rad * 0.90 * np.sin(t)))
        for k in range(len(pts) - 1):
            _com(cor, _stroke([pts[k], pts[k + 1]], 0.011, 0.02), ESCURO)

    # ---------------- RACHADURAS
    for k in range(10):
        x = rng.uniform(-0.95, 0.95)
        y = rng.uniform(0.10, 0.99)
        for passo in range(14):
            nx2 = x + rng.normal(0, 0.055)
            ny2 = y - abs(rng.normal(0.030, 0.018))
            if not (-1 < nx2 < 1 and 0.02 < ny2 < 1):
                break
            _com(cor, _stroke([(x, y), (nx2, ny2)], 0.0030, 0.008), ESCURO * 0.8)
            x, y = nx2, ny2

    # ---------------- brilho nas ARISTAS (a pedra é polida)
    brilho = np.clip(rel, 0, 1.2) * 0.22
    cor = cor + brilho[..., None] * np.array([0.9, 0.92, 1.0])

    # ---------------- METADE ESQUERDA mais escura (a referência é assim)
    sombra_lado = np.clip((-u - 0.45) / 0.55, 0, 1) ** 1.6 * 0.45
    cor = cor * (1 - sombra_lado[..., None])

    # ---------------- VINHETA na borda
    borda = np.clip((a - 0.78) / 0.22, 0, 1) ** 1.5 * 0.30
    cor = cor * (1 - borda[..., None])

    img = np.clip(cor, 0, 1)
    saida = Image.fromarray((img * 255).astype(np.uint8), "RGB")
    saida.save(caminho)
    return saida


# ---------------------------------------------------------------------- prévia
def render(P, N, T, tex, olho, ang, largura_px=420, altura_px=520):
    """render ortográfico com z-buffer + textura (só pra conferir)."""
    ca, sa = np.cos(ang), np.sin(ang)
    X = P[:, 0] * ca + P[:, 2] * sa
    Z = -P[:, 0] * sa + P[:, 2] * ca
    Y = P[:, 1]
    Nx = N[:, 0] * ca + N[:, 2] * sa
    Nz = -N[:, 0] * sa + N[:, 2] * ca
    Ny = N[:, 1]

    esc = min(largura_px / 10.0, altura_px / 9.6)
    sx = X * esc + largura_px / 2
    sy = (Y + 4.15) * esc + altura_px / 2

    zbuf = np.full((altura_px, largura_px), 1e9, dtype=np.float32)
    cor = np.zeros((altura_px, largura_px, 3), dtype=np.float32)
    tex = np.asarray(tex, dtype=np.float32) / 255.0
    TH, TW = tex.shape[:2]

    # luz vindo DA FRENTE e de cima-esquerda (a normal aponta para -Z, então a
    # direção "para a luz" tem z NEGATIVO)
    luz = np.array([-0.30, 0.55, -0.78])
    luz = luz / np.linalg.norm(luz)

    for k in range(0, len(P), 3):
        x = sx[k:k + 3]; y = sy[k:k + 3]; z = Z[k:k + 3]
        x0, x1 = int(np.floor(x.min())), int(np.ceil(x.max()))
        y0, y1 = int(np.floor(y.min())), int(np.ceil(y.max()))
        x0 = max(0, x0); y0 = max(0, y0)
        x1 = min(largura_px - 1, x1); y1 = min(altura_px - 1, y1)
        if x1 < x0 or y1 < y0:
            continue
        det = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
        if abs(det) < 1e-9:
            continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        w0 = ((y[1] - y[2]) * (xs - x[2]) + (x[2] - x[1]) * (ys - y[2])) / det
        w1 = ((y[2] - y[0]) * (xs - x[2]) + (x[0] - x[2]) * (ys - y[2])) / det
        w2 = 1 - w0 - w1
        dentro = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not dentro.any():
            continue
        zz = w0 * z[0] + w1 * z[1] + w2 * z[2]
        m = dentro & (zz < zbuf[y0:y1 + 1, x0:x1 + 1])
        if not m.any():
            continue
        nz = w0 * Nz[k] + w1 * Nz[k + 1] + w2 * Nz[k + 2]
        ny = w0 * Ny[k] + w1 * Ny[k + 1] + w2 * Ny[k + 2]
        nx = w0 * Nx[k] + w1 * Nx[k + 1] + w2 * Nx[k + 2]
        diff = np.clip(nx * luz[0] + ny * luz[1] + nz * luz[2], 0, 1) * 0.75 + 0.42
        uu = w0 * T[k, 0] + w1 * T[k + 1, 0] + w2 * T[k + 2, 0]
        vv = w0 * T[k, 1] + w1 * T[k + 1, 1] + w2 * T[k + 2, 1]
        px = np.clip((uu * (TW - 1)).astype(int), 0, TW - 1)
        py = np.clip((vv * (TH - 1)).astype(int), 0, TH - 1)
        amostra = tex[py, px]
        alvo = cor[y0:y1 + 1, x0:x1 + 1]
        alvo[m] = np.clip(amostra[m] * diff[m][:, None], 0, 1)
        cor[y0:y1 + 1, x0:x1 + 1] = alvo
        zb = zbuf[y0:y1 + 1, x0:x1 + 1]; zb[m] = zz[m]
        zbuf[y0:y1 + 1, x0:x1 + 1] = zb
    return (cor * 255).astype(np.uint8)


def render_com_cabeca(tex, ang):
    """A máscara VESTIDA: caixa da cabeça do jogador (skin cinza) + a máscara."""
    # cabeça do Minecraft: cubo de 8x8x8 px com o rosto em z=-4
    VP = []
    VN = []
    VT = []
    x0, x1, y0, y1, z0, z1 = -4.0, 4.0, -8.0, 0.0, -4.0, 4.0
    faces = [
        ((x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (0, 0, -1)),   # frente
        ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1)),    # trás
        ((x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (-1, 0, 0)),   # esq
        ((x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0), (1, 0, 0)),    # dir
        ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1), (0, 1, 0)),    # topo
    ]
    for a, b, c, d, n in faces:
        for tri in ((a, b, c), (a, c, d)):
            for v in tri:
                VP.append(v)
                VN.append(n)
                VT.append((0.5, 0.5))
    P = np.concatenate([np.array(VP), np.zeros((0, 3))])
    N = np.concatenate([np.array(VN), np.zeros((0, 3))])
    T = np.concatenate([np.array(VT), np.zeros((0, 2))])

    # pinta a cabeça de cinza-claro via uma "textura" 2x2
    tex_cabeca = Image.new("RGB", (4, 4), (168, 148, 128))
    P2, N2, T2 = monta()
    # empilha: cabeça primeiro (atrás) e a máscara depois
    Pc = np.concatenate([P, P2])
    Nc = np.concatenate([N, N2])
    Tc = np.concatenate([T, T2])
    texc = Image.new("RGB", (256, 256), (168, 148, 128))
    texc.paste(tex, (0, 0))
    # a cabeça usa a faixa superior (0..1) — forçamos as UV dela para o canto cinza
    Tc[:len(T)] = np.array([0.995, 0.005])
    texc.paste(Image.new("RGB", (8, 8), (168, 148, 128)), (246, 0))
    img = render(Pc, Nc, Tc, texc, None, ang, 420, 560)
    return img


def monta_previa(tex):
    P, N, T = monta()
    vistas = [("frente", 0.0), ("3/4", 0.72), ("lado", 1.5708)]
    paineis = [render(P, N, T, tex, None, a) for _, a in vistas]
    # 4º painel: CLOSE no rosto (é o que o dono olha no jogo)
    close = render(P, N, T, tex, None, 0.0, 420, 520)
    recorte = Image.fromarray(close).crop((60, 90, 360, 460)).resize((420, 520))
    paineis.append(np.asarray(recorte))
    nomes = [n for n, _ in vistas] + ["close no rosto"]
    LW, LH = paineis[0].shape[1], paineis[0].shape[0]
    img = Image.new("RGB", (LW * len(paineis), LH + 26), (22, 22, 26))
    d = ImageDraw.Draw(img)
    for i, nome in enumerate(nomes):
        img.paste(Image.fromarray(paineis[i]), (i * LW, 26))
        d.text((10 + i * LW, 8), "MASCARA 5.8.0 — " + nome, fill=(200, 200, 210))
    img.save(SAIDA_PREVIA)
    return len(P) // 3


def main():
    P, N, T = monta()
    tex = gera_textura(SAIDA_PNG)
    n = len(P)
    with open(SAIDA_BIN, "wb") as f:
        f.write(b"RPTM")
        # ATENÇÃO: os dois inteiros do cabeçalho são BIG-ENDIAN (o MalhaBin.java lê
        # com DataInputStream). np.int32(...).astype(">i4") NÃO garante a ordem
        # (numpy 2 devolveu little-endian) — por isso struct.pack explícito, que
        # é o que o importar_bbmodel.py já fazia.
        f.write(struct.pack(">ii", 1, n))
        for k in range(3):
            f.write(P[:, k].astype(">f4").tobytes())
        for k in range(3):
            f.write(N[:, k].astype(">f4").tobytes())
        f.write(T[:, 0].astype(">f4").tobytes())
        f.write(T[:, 1].astype(">f4").tobytes())
    # ---- validação: relê o arquivo como o Java vai ler
    cru = open(SAIDA_BIN, "rb").read()
    assert cru[:4] == b"RPTM", "cabecalho errado"
    ver, n2 = struct.unpack(">ii", cru[4:12])
    assert ver == 1 and n2 == n, f"cabecalho invalido: ver={ver} n={n2} (esperado 1/{n})"
    assert len(cru) == 12 + 8 * 4 * n, f"tamanho errado: {len(cru)}"
    tris = monta_previa(tex)
    print(f"malha: {n} vertices / {n // 3} triangulos -> {SAIDA_BIN}")
    print(f"textura -> {SAIDA_PNG}")
    print(f"previa  -> {SAIDA_PREVIA} ({tris} triangulos)")


if __name__ == "__main__":
    main()
