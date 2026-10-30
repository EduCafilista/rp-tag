"""MASCARA DE PEDRA 5.0 — prototipo e render de conferencia (mesma matematica do Java).

Implementa EXATAMENTE o que `client/CamadaMascara.java` faz:

  * casca em arco (abraça a cara) com recorte que estreita no queixo e na testa;
  * relevo esculpido em gaussianas (nariz, orbitas, maçãs, boca, queixo);
  * VOLUME FECHADO: casca externa + casca interna + aba (rim) ligando as bordas
    -> é um objeto 3D de verdade, não uma folha;
  * normais por diferença finita.

Desenha a máscara em cima da CABEÇA do player (cubo 8x8x8, rosto em z=-4) para
conferir encaixe, escala e se ela não está de cabeça para baixo.

uso: python arte/previa_mascara3d.py   ->  previa/mascara3d.png
     python arte/previa_mascara3d.py --textura  ->  textures/entity/mascara_pedra.png
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/entity/mascara_pedra.png'
SAIDA = RAIZ / 'previa'

# ==================================================== constantes (batem com o Java)
COLS, ROWS = 29, 25                # pontos da malha (largura x altura)
Z_FRENTE = -4.48                   # plano da frente (o rosto fica em z = -4)
ENROLA = 1.70                      # quanto o extremo (|u| > 0,88) enrola para trás
ESPESSURA = 0.62                   # grossura da pedra (fecha o volume)
Y_BASE = -4.35                     # embaixo (abaixo do queixo)
Y_TOPO = 4.15                      # em cima (testa)

# ==================================================== superfície
def _sstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


_V = [0.00, 0.06, 0.16, 0.30, 0.46, 0.64, 0.78, 0.90, 0.97, 1.00]
_W = [0.70, 1.70, 2.45, 2.88, 3.02, 3.04, 3.02, 2.98, 2.86, 2.55]


def largura(v):
    """Meia-largura da máscara na altura v (0 = queixo, 1 = testa).

    Silhueta de ESCUDO (a do modelo do Sketchfab): sobe rápido da ponta do
    queixo, fica larga e quase reta no meio e só afina um pouco no topo.
    """
    v = min(1.0, max(0.0, v))
    # tabela de controle: ponta fina embaixo, largo no meio, topo arredondado
    return float(np.interp(v, _V, _W))


def gauss(x):
    return math.exp(-x * x)


def relevo(u, v):
    """Relevo em unidades do modelo; positivo empurra a pedra para FORA (para -Z)."""
    a = abs(u)
    r = 0.0
    # ---- massa geral: testa e maçãs um pouco inchadas, rosto abaulado
    r += 0.12 * gauss(u / 0.55) * gauss((v - 0.80) / 0.22)                   # testa
    # ---- a TESTA em Y: duas cristas que descem para o meio (a marca do JoJo)
    vc = 0.735 - 0.10 * a
    r += 0.34 * gauss((a - 0.15) / 0.070) * gauss((v - vc) / 0.045)
    # ---- sobrancelha em V (brava): crista grossa por cima das órbitas
    vb = 0.645 + 0.055 * a
    r += 0.30 * gauss((a - 0.30) / 0.16) * gauss((v - vb) / 0.028)
    # ---- ÓRBITAS: fenda funda e FINA, inclinada (canto de fora sobe)
    vo = 0.575 + 0.050 * a
    r -= 0.72 * gauss((a - 0.32) / 0.080) * gauss((v - vo) / 0.022)
    # ---- NARIZ: crista larga embaixo, fina em cima, descendo da sobrancelha
    r += 0.30 * gauss(u / 0.10) * gauss((v - 0.615) / 0.060)                 # ponte
    r += 0.72 * gauss(u / 0.14) * gauss((v - 0.470) / 0.070)                 # dorso
    r += 0.26 * gauss((a - 0.17) / 0.075) * gauss((v - 0.405) / 0.035)       # asas
    # ---- MAÇÃS
    r += 0.30 * gauss((a - 0.55) / 0.15) * gauss((v - 0.435) / 0.075)
    # ---- FOCINHO: a parte de baixo do rosto salta pra frente (o "bico")
    r += 0.34 * gauss(u / 0.42) * gauss((v - 0.315) / 0.085)
    # ---- BOCA: buraco fundo
    r -= 0.34 * gauss(u / 0.30) * gauss((v - 0.243) / 0.030)
    # ---- QUEIXO: ponta pra frente e borda de baixo recuada
    r += 0.16 * gauss(u / 0.34) * gauss((v - 0.115) / 0.06)
    r -= 0.26 * gauss((v - 0.010) / 0.045)
    r -= 0.05 * gauss((v - 0.985) / 0.030)
    return r


def ponto(u, v, rel=1.0):
    """Um ponto da casca externa (rel=1) ou da interna (rel=0)."""
    w = largura(v)
    x = w * u
    # frente quase plana; só o terço externo enrola para trás
    t = max(0.0, (abs(u) - 0.88) / 0.12)
    z = Z_FRENTE + ENROLA * (t ** 1.5) - relevo(u, v) * (1.0 if rel > 0.5 else 0.35)
    if rel <= 0.5:
        z += ESPESSURA
    return np.array([x, Y_BASE + (Y_TOPO - Y_BASE) * v, z])


def normal(u, v, rel=1.0):
    e = 0.004
    uc, vc = min(1.0, max(-1.0, u)), min(0.999, max(0.001, v))
    du = ponto(min(1.0, uc + e), vc, rel) - ponto(max(-1.0, uc - e), vc, rel)
    dv = ponto(uc, min(1.0, vc + e), rel) - ponto(uc, max(0.0, vc - e), rel)
    n = np.cross(du, dv)
    ln = np.linalg.norm(n)
    n = np.array([0.0, 0.0, -1.0]) if ln < 1e-6 else n / ln
    if n[2] > 0:
        n = -n
    if rel <= 0.5:
        n = -n
    return n


# ==================================================== malha (fechada)
def malha():
    """Devolve lista de triângulos: (p0,p1,p2, n0,n1,n2, t0,t1,t2) com uv no espaço 0..1."""
    tris = []
    uvs = lambda u, v: (0.5 * (u + 1.0), 1.0 - v)

    P = lambda j, i, rel: ponto(-1 + 2 * j / (COLS - 1), i / (ROWS - 1), rel=rel)
    N = lambda j, i, rel: normal(-1 + 2 * j / (COLS - 1), i / (ROWS - 1), rel=rel)
    UV = lambda j, i: uvs(-1 + 2 * j / (COLS - 1), i / (ROWS - 1))

    def grade(rel, inverte):
        for j in range(COLS - 1):
            for i in range(ROWS - 1):
                p = [P(j, i, rel), P(j + 1, i, rel), P(j + 1, i + 1, rel), P(j, i + 1, rel)]
                n = [N(j, i, rel), N(j + 1, i, rel), N(j + 1, i + 1, rel), N(j, i + 1, rel)]
                t = [UV(j, i), UV(j + 1, i), UV(j + 1, i + 1), UV(j, i + 1)]
                if inverte:
                    n = [-x for x in n]
                    p, n, t = p[::-1], n[::-1], t[::-1]
                tris.append((p[0], p[1], p[2], n[0], n[1], n[2], t[0], t[1], t[2]))
                tris.append((p[0], p[2], p[3], n[0], n[2], n[3], t[0], t[2], t[3]))

    grade(1.0, False)   # casca externa
    grade(0.0, True)    # casca interna (normais para dentro)

    # ---- aba (rim): liga a borda externa à interna nos 4 lados
    def tira(pontos_out, pontos_in, uvs_):
        for k in range(len(pontos_out) - 1):
            po0, po1 = pontos_out[k], pontos_out[k + 1]
            pi0, pi1 = pontos_in[k], pontos_in[k + 1]
            t0, t1 = uvs_[k], uvs_[k + 1]
            centro = (po0 + po1 + pi0 + pi1) * 0.25
            for (a, b, c, ta, tb, tc) in ((po0, po1, pi1, t0, t1, t1), (po0, pi1, pi0, t0, t1, t0)):
                nn = np.cross(b - a, c - a)
                ln = np.linalg.norm(nn)
                nn = np.array([0.0, 0.0, 1.0]) if ln < 1e-6 else nn / ln
                meio = (a + b + c) / 3.0
                if float(np.dot(nn, meio - np.array([0.0, meio[1], centro[2] + 0.9]))) < 0:
                    nn = -nn
                tris.append((a, b, c, nn, nn, nn, ta, tb, tc))

    borda_inf = [P(j, 0, 1.0) for j in range(COLS)]
    borda_inf_i = [P(j, 0, 0.0) for j in range(COLS)]
    tira(borda_inf, borda_inf_i, [UV(j, 0) for j in range(COLS)])

    borda_sup = [P(j, ROWS - 1, 1.0) for j in range(COLS - 1, -1, -1)]
    borda_sup_i = [P(j, ROWS - 1, 0.0) for j in range(COLS - 1, -1, -1)]
    tira(borda_sup, borda_sup_i, [UV(j, ROWS - 1) for j in range(COLS - 1, -1, -1)])

    esq = [P(0, i, 1.0) for i in range(ROWS - 1, -1, -1)]
    esq_i = [P(0, i, 0.0) for i in range(ROWS - 1, -1, -1)]
    tira(esq, esq_i, [UV(0, i) for i in range(ROWS - 1, -1, -1)])

    dir_ = [P(COLS - 1, i, 1.0) for i in range(ROWS)]
    dir_i = [P(COLS - 1, i, 0.0) for i in range(ROWS)]
    tira(dir_, dir_i, [UV(COLS - 1, i) for i in range(ROWS)])
    return tris


def caixa_cabeca():
    """A cabeça do player (8x8x8, rosto em z=-4) em cinza — só para conferir o encaixe."""
    k = 4.0
    v = [np.array(x, dtype=float) for x in
         [(-k, -k, -k), (k, -k, -k), (k, k, -k), (-k, k, -k),
          (-k, -k, k), (k, -k, k), (k, k, k), (-k, k, k)]]
    faces = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]
    tris = []
    for (a, b, c, d) in faces:
        for (i0, i1, i2) in ((a, b, c), (a, c, d)):
            p0, p1, p2 = v[i0], v[i1], v[i2]
            n = np.cross(p1 - p0, p2 - p0)
            n = n / np.linalg.norm(n)
            if float(np.dot(n, (p0 + p1 + p2) / 3.0)) < 0:
                n = -n
            tris.append((p0, p1, p2, n, n, n, (0.0, 0.0), (0.0, 0.0), (0.0, 0.0)))
    return tris


# ==================================================== textura
def textura(lado=256):
    """Textura da MALHA. u_tex = (u+1)/2 (esq->dir), v_tex = 1 - y/lado (v=1 em cima).

    O modelo do Sketchfab lê pela ARTE, não pelo relevo: contorno preto grosso,
    um Y de rachadura na testa, órbitas pretas e o queixo escuro. É isso que
    está pintado aqui, por cima da pedra.
    """
    rng = np.random.default_rng(11)
    base = np.array([159, 160, 163], dtype=np.float32)

    X = lambda u: int((u + 1.0) * 0.5 * (lado - 1))
    Y = lambda v: int((1.0 - v) * (lado - 1))

    # ---- pedra: ruído em 4 escalas
    img = np.zeros((lado, lado, 3), np.float32)
    for oitava, amp in ((8, 9.0), (16, 6.0), (48, 3.5), (128, 1.8)):
        n = rng.normal(0, 1, (oitava, oitava))
        up = np.array(Image.fromarray(((n - n.min()) / (n.max() - n.min()) * 255).astype(np.uint8))
                      .resize((lado, lado), Image.BICUBIC), dtype=np.float32) / 255.0 - 0.5
        img += up[:, :, None] * amp
    img += base

    Yg, Xg = np.mgrid[0:lado, 0:lado]
    uu = Xg / (lado - 1.0) * 2.0 - 1.0
    vv = 1.0 - Yg / (lado - 1.0)

    # ---- luz assada do relevo (topo-esquerda), bem marcada
    for (cu, cv, sx, sy, força) in [
            (0.00, 0.500, 0.085, 0.095, 0.55),    # dorso do nariz (claro)
            (0.115, 0.500, 0.055, 0.110, -0.85),  # lado direito do nariz (sombra)
            (-0.32, 0.585, 0.105, 0.040, -1.00),  # órbita esquerda
            (0.32, 0.585, 0.105, 0.040, -1.00),   # órbita direita
            (-0.15, 0.720, 0.075, 0.045, 0.45),   # crista Y esquerda
            (0.15, 0.720, 0.075, 0.045, 0.45),    # crista Y direita
            (-0.30, 0.660, 0.150, 0.030, 0.35),   # sobrancelha
            (0.30, 0.660, 0.150, 0.030, -0.45),
            (-0.55, 0.435, 0.120, 0.080, 0.30),   # maçã esquerda
            (0.55, 0.435, 0.120, 0.080, -0.40),   # maçã direita
            (0.00, 0.243, 0.230, 0.028, -0.90),   # boca
            (0.00, 0.060, 0.230, 0.045, -0.55)]:  # queixo
        img += (força * np.exp(-((uu - cu) / sx) ** 2 - ((vv - cv) / sy) ** 2))[:, :, None] * 34

    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert('RGB')
    d = ImageDraw.Draw(im)

    ESCURO, FUNDO, CLARO = (22, 21, 20), (40, 38, 35), (240, 236, 224)
    PEDRA_ESC, LUZ_RACHA = (112, 106, 92), (226, 220, 204)
    TRACO = (18, 17, 16)

    # ================================================== 1) CONTORNO preto grosso
    # (a "manga": linha preta de 3-4 px por dentro da silhueta inteira)
    for k in range(6):
        m = 0.024 + k * 0.011
        mask = (np.minimum(np.minimum(uu + 1.0, 1.0 - uu) / 2.0, np.minimum(vv, 1.0 - vv)) < m)
        arr = np.asarray(im).astype(np.float32)
        arr[mask & ~(np.minimum(np.minimum(uu + 1.0, 1.0 - uu) / 2.0, np.minimum(vv, 1.0 - vv)) < 0.030)] *= 0.55
        if k < 3:
            arr[mask] = np.array(TRACO, np.float32)
        im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert('RGB')
        d = ImageDraw.Draw(im)

    # ================================================== 2) o Y da TESTA
    topo = (int(lado * 0.495), int(lado * 0.030))
    meio = (int(lado * 0.50), int(lado * 0.175))          # v ≈ 0,825
    esq = (int(lado * 0.315), int(lado * 0.268))          # v ≈ 0,73
    dir_ = (int(lado * 0.685), int(lado * 0.268))
    wY = int(lado * 0.026)
    d.line((topo, meio), fill=TRACO, width=wY)
    d.line((meio, esq), fill=TRACO, width=wY)
    d.line((meio, dir_), fill=TRACO, width=wY)
    # brilho da lasca levantada do lado de cima das linhas
    d.line((topo[0] - 3, topo[1], meio[0] - 3, meio[1]), fill=(210, 204, 188), width=2)
    d.line((meio[0] - 2, meio[1], esq[0], esq[1] - 3), fill=(210, 204, 188), width=2)

    # ================================================== 3) ÓRBITAS pretas + fenda
    def amendoa(cx, cy, w, h, slant, n=48):
        """Olho em amêndoa: pontas finas, corpo cheio, inclinado (canto de fora sobe)."""
        pts = []
        for k in range(n + 1):
            t = -1.0 + 2.0 * k / n
            off = slant * t
            yy = h * max(0.0, 1.0 - abs(t) ** 1.7) ** 0.55
            pts.append((cx + t * w, cy + off - yy))
        for k in range(n, -1, -1):
            t = -1.0 + 2.0 * k / n
            off = slant * t
            yy = h * max(0.0, 1.0 - abs(t) ** 1.7) ** 0.55
            pts.append((cx + t * w, cy + off + yy))
        return pts
    for cx_u in (-0.32, 0.32):
        cx, cy = X(cx_u), Y(0.585)
        w, h = int(lado * 0.112), int(lado * 0.038)
        canto_int = int(cx_u < 0) * 2 - 1
        # órbita funda (sombra em volta)
        d.polygon(amendoa(cx, cy, w * 1.10, h * 1.35, h * 0.30), fill=(66, 65, 63))
        # amêndoa preta do olho + esclera por dentro
        d.polygon(amendoa(cx, cy, w, h, h * 0.30), fill=(16, 15, 14))
        d.polygon(amendoa(cx, cy - h * 0.03, w * 0.82, h * 0.56, h * 0.28), fill=CLARO)
        # pupila (o olhar vai para dentro = bravo)
        px = cx + (-1 if cx_u > 0 else 1) * w * 0.16
        d.ellipse((px - w * 0.13, cy - h * 0.42, px + w * 0.13, cy + h * 0.16), fill=(14, 13, 12))
        # pálpebra de cima (linha preta grossa acompanhando a amêndoa)
        pal = amendoa(cx, cy, w * 1.02, h * 1.02, h * 0.30)
        d.line(pal[:len(pal) // 2], fill=TRACO, width=int(lado * 0.018))

    # ================================================== 4) NARIZ (contorno)
    d.line((X(0.012), Y(0.545), X(0.012), Y(0.50)), fill=(120, 114, 100), width=int(lado * 0.010))
    d.ellipse((X(-0.19), Y(0.415), X(-0.05), Y(0.385)), fill=(16, 15, 14))    # narina esq
    d.ellipse((X(0.05), Y(0.415), X(0.19), Y(0.385)), fill=(16, 15, 14))      # narina dir
    d.line((X(-0.20), Y(0.385), X(0.20), Y(0.385)), fill=TRACO, width=int(lado * 0.012))

    # ================================================== 5) BOCA preta + presas
    bx0, bx1 = X(-0.30), X(0.30)
    by0, by1 = Y(0.268), Y(0.222)
    d.rounded_rectangle((bx0, by0, bx1, by1), radius=int(lado * 0.02), fill=(14, 13, 12))
    d.line((bx0 - 6, by1 + 3, bx1 + 6, by1 + 4), fill=TRACO, width=int(lado * 0.012))
    for t in (-0.16, 0.16):
        px = X(t)
        d.polygon([(px - int(lado * 0.026), by0 - 1), (px + int(lado * 0.026), by0 - 1),
                   (px, by0 + int(lado * 0.068))], fill=CLARO)
    # queixo escuro (o modelo tem o queixo na sombra)
    arr = np.asarray(im).astype(np.float32)
    queixo = (vv < 0.085) & (np.abs(uu) < 0.45)
    arr[queixo] *= 0.68
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert('RGB')
    d = ImageDraw.Draw(im)

    # ================================================== 6) rachaduras + hachuras
    def racha(x, y, ang, comp, prof):
        for _ in range(comp):
            ang += rng.normal(0, 0.45)
            nx, ny = x + math.cos(ang) * rng.uniform(5, 11), y + math.sin(ang) * rng.uniform(5, 11)
            d.line((x + 1, y - 1, nx + 1, ny - 1), fill=LUZ_RACHA, width=1)
            d.line((x, y, nx, ny), fill=TRACO, width=1)
            x, y = nx, ny
            if rng.random() < 0.4 and prof < 3:
                racha(x, y, ang + rng.choice([-1.0, 1.0]), int(comp * 0.5), prof + 1)

    for k in range(6):
        cx0 = int(lado * 0.5 + rng.normal(0, lado * 0.12))
        cy0 = int(lado * (0.30 + 0.55 * rng.random()))
        racha(cx0, cy0, rng.uniform(0, 6.28), int(rng.integers(3, 8)), 0)

    for _ in range(9):
        lado_s = rng.choice([-1, 1])
        x = int(lado * 0.5 + lado_s * lado * rng.uniform(0.30, 0.47))
        y = int(rng.integers(0, lado))
        ang = rng.uniform(-1.0, 1.0) + (math.pi if lado_s > 0 else 0.0)
        comp = int(rng.integers(9, 26))
        d.line((x, y, x + math.cos(ang) * comp, y + math.sin(ang) * comp),
               fill=TRACO, width=int(rng.integers(1, 3)))

    return im.convert('RGBA')


# ==================================================== render
def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def render(textura_img, yaw, pitch, lado=460, fundo=(21, 20, 24), mostrar_cabeca=True):
    tris = caixa_cabeca() + malha() if mostrar_cabeca else malha()
    R = rot_x(pitch) @ rot_y(yaw)

    escala = lado / 12.6
    cx, cy = lado / 2.0, lado / 2.0
    img = np.zeros((lado, lado, 3), np.float32)
    img[:, :] = np.array(fundo, np.float32) / 255.0
    zbuf = np.full((lado, lado), 1e9, np.float32)
    tex = np.asarray(textura_img.convert('RGB'), np.float32) / 255.0
    th, tw = tex.shape[:2]
    luz = np.array([-0.50, -0.72, -0.48])
    luz = luz / np.linalg.norm(luz)

    for (p0, p1, p2, n0, n1, n2, t0, t1, t2) in tris:
        P = np.stack([p0, p1, p2]) @ R.T
        N = np.stack([n0, n1, n2]) @ R.T
        U = np.stack([t0, t1, t2])
        sx = cx + P[:, 0] * escala
        sy = cy - P[:, 1] * escala
        sz = P[:, 2]
        x0, y0 = int(max(0, np.floor(sx.min()))), int(max(0, np.floor(sy.min())))
        x1, y1 = int(min(lado - 1, np.ceil(sx.max()))), int(min(lado - 1, np.ceil(sy.max())))
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        ax, ay = sx[0], sy[0]
        bx, by = sx[1], sy[1]
        cx2, cy2 = sx[2], sy[2]
        d = (by - cy2) * (ax - cx2) + (cx2 - bx) * (ay - cy2)
        if abs(d) < 1e-9:
            continue
        w0 = ((by - cy2) * (gx - cx2) + (cx2 - bx) * (gy - cy2)) / d
        w1 = ((cy2 - ay) * (gx - cx2) + (ax - cx2) * (gy - cy2)) / d
        w2 = 1.0 - w0 - w1
        dentro = (w0 >= -0.002) & (w1 >= -0.002) & (w2 >= -0.002)
        if not dentro.any():
            continue
        z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        melhor = dentro & (z < sub)
        if not melhor.any():
            continue

        nx = w0 * N[0, 0] + w1 * N[1, 0] + w2 * N[2, 0]
        ny = w0 * N[0, 1] + w1 * N[1, 1] + w2 * N[2, 1]
        nz = w0 * N[0, 2] + w1 * N[1, 2] + w2 * N[2, 2]
        ln = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9
        dif = np.clip((nx * luz[0] + ny * luz[1] + nz * luz[2]) / ln, 0, 1)
        sombra = 0.22 + 0.86 * dif

        if np.allclose(U, 0.0):
            cor = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 3), np.float32) + np.array([0.16, 0.155, 0.16]) * sombra[:, :, None]
        else:
            uu = w0 * U[0, 0] + w1 * U[1, 0] + w2 * U[2, 0]
            vv = w0 * U[0, 1] + w1 * U[1, 1] + w2 * U[2, 1]
            ti = np.clip((uu * tw).astype(int), 0, tw - 1)
            tj = np.clip((vv * th).astype(int), 0, th - 1)
            cor = tex[tj, ti] * sombra[:, :, None]

        alvo = img[y0:y1 + 1, x0:x1 + 1]
        alvo[melhor] = cor[melhor]
        sub[melhor] = z[melhor]
        zbuf[y0:y1 + 1, x0:x1 + 1] = sub

    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    TEX.parent.mkdir(parents=True, exist_ok=True)
    tex = textura()
    tex.save(TEX)
    print(TEX, TEX.stat().st_size)

    vistas = [(0.0, -0.06, 'FRENTE'), (0.62, -0.08, '3/4'), (1.35, -0.06, 'LADO')]
    lado, folga = 460, 10
    tira = Image.new('RGB', (lado * 3 + folga * 4, lado + 48), (14, 13, 17))
    d = ImageDraw.Draw(tira)
    for i, (yaw, pitch, nome) in enumerate(vistas):
        im = render(tex, yaw, pitch, lado)
        tira.paste(im, (folga + i * (lado + folga), 38))
        d.text((folga + i * (lado + folga) + 4, 14), f'MASCARA 5.0 - {nome}', fill=(232, 228, 216))
    d.text((folga + 2, lado + 42 - 20),
           'malha 29x25 - placa de rosto (frente plana, borda enrolada) + casca interna + aba = volume fechado',
           fill=(150, 146, 136))
    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'mascara3d.png'
    tira.save(destino)
    print(destino, destino.stat().st_size)


if __name__ == '__main__':
    main()
