"""Render de conferencia da MASCARA DE PEDRA — mesma matematica do Java.

Implementa exatamente as funcoes angMax/relevo/ponto/normal de
`client/CamadaMascara.java` (COLS=15, ROWS=13) e rasteriza a malha com
z-buffer, mapeamento de textura e luz difusa, em 3 angulos.

Assim da para ver o VOLUME (casca curvada + relevo do nariz/testa/orbitas)
sem abrir o jogo.

uso: python arte/previa_mascara3d.py   ->  previa/mascara3d.png
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/item/mascara_pedra.png'
SAIDA = RAIZ / 'previa'

# ---------------- constantes (batem com o Java) ----------------
COLS, ROWS = 19, 17
Z_CENTRO, RAIO = -0.35, 4.55
Y_BASE, Y_TOPO = 0.62, -8.55


def ang_max(v):
    s = math.sin(math.pi * min(1.0, max(0.0, v)))
    return 0.78 + 0.56 * max(0.0, s) ** 0.55


def gauss(x):
    return math.exp(-x * x)


def relevo(u, v):
    a = abs(u)
    r = 0.0
    r += 0.16 * gauss(u / 0.20)
    r += 0.46 * gauss(u / 0.26) * gauss((v - 0.435) / 0.115)
    r += 0.20 * gauss(u / 0.90) * gauss((v - 0.665) / 0.045)
    r -= 0.17 * gauss((a - 0.33) / 0.15) * gauss((v - 0.605) / 0.055)
    r += 0.13 * gauss((a - 0.55) / 0.19) * gauss((v - 0.375) / 0.085)
    r -= 0.13 * gauss(u / 0.34) * gauss((v - 0.245) / 0.045)
    r -= 0.19 * gauss((v - 0.02) / 0.055)
    r -= 0.06 * gauss((v - 0.97) / 0.04)
    return r


def ponto(u, v):
    th = u * ang_max(v)
    re = RAIO + relevo(u, v)
    return np.array([re * math.sin(th), Y_BASE + (Y_TOPO - Y_BASE) * v, Z_CENTRO - re * math.cos(th)])


def normal(u, v):
    e = 0.004
    uc, vc = min(1.0, max(-1.0, u)), min(1.0, max(0.0, v))
    du = ponto(min(1.0, uc + e), vc) - ponto(max(-1.0, uc - e), vc)
    dv = ponto(uc, min(1.0, vc + e)) - ponto(uc, max(0.0, vc - e))
    n = np.cross(du, dv)
    ln = np.linalg.norm(n)
    n = np.array([0, 0, -1.0]) if ln < 1e-5 else n / ln
    th = u * ang_max(v)
    fora = np.array([math.sin(th), 0.0, -math.cos(th)])
    if float(np.dot(n, fora)) < 0:
        n = -n
    return n


# ---------------- malha ----------------
def malha():
    pos = np.zeros((COLS, ROWS, 3))
    nor = np.zeros((COLS, ROWS, 3))
    uv = np.zeros((COLS, ROWS, 2))
    for j in range(COLS):
        u = -1 + 2 * j / (COLS - 1)
        for i in range(ROWS):
            v = i / (ROWS - 1)
            pos[j, i] = ponto(u, v)
            nor[j, i] = normal(u, v)
            uv[j, i] = (0.5 * j / (COLS - 1), 0.5 * (1 - v))
    tris = []
    for j in range(COLS - 1):
        for i in range(ROWS - 1):
            tris.append(((j, i), (j + 1, i), (j + 1, i + 1)))
            tris.append(((j, i), (j + 1, i + 1), (j, i + 1)))
    return pos, nor, uv, tris


# ---------------- render ----------------
def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def render(textura, yaw, pitch, lado=420, fundo=(22, 20, 26)):
    """Desenha a malha vista de (yaw, pitch) com luz fixa no espaco da camera."""
    pos, nor, uv, tris = malha()
    R = rot_x(pitch) @ rot_y(yaw)
    p = pos.reshape(-1, 3) @ R.T
    n = nor.reshape(-1, 3) @ R.T

    # projecao ortografica; o modelo tem ~9 unidades de altura
    escala = lado / 10.2
    cx, cy = lado / 2.0, lado / 2.0
    sx = cx + p[:, 0] * escala
    sy = cy + (p[:, 1] + 3.95) * escala          # +3,95 = meio da altura da máscara
    sz = p[:, 2]

    img = np.zeros((lado, lado, 3), dtype=np.float32)
    img[:, :] = np.array(fundo, dtype=np.float32) / 255.0     # tudo em 0..1 ate o fim
    zbuf = np.full((lado, lado), 1e9, dtype=np.float32)

    tex = np.asarray(textura.convert('RGB'), dtype=np.float32) / 255.0
    th, tw = tex.shape[:2]

    luz = np.array([-0.42, -0.55, -1.0])
    luz = luz / np.linalg.norm(luz)

    for tri in tris:
        idx = [j * ROWS + i for (j, i) in tri]
        P = np.stack([sx[idx], sy[idx]], axis=1)
        Z = sz[idx]
        N = n[idx]
        U = uv.reshape(-1, 2)[idx]

        x0, y0 = int(max(0, np.floor(P[:, 0].min()))), int(max(0, np.floor(P[:, 1].min())))
        x1, y1 = int(min(lado - 1, np.ceil(P[:, 0].max()))), int(min(lado - 1, np.ceil(P[:, 1].max())))
        if x1 <= x0 or y1 <= y0:
            continue
        xs = np.arange(x0, x1 + 1) + 0.5
        ys = np.arange(y0, y1 + 1) + 0.5
        gx, gy = np.meshgrid(xs, ys)

        ax, ay = P[0]
        bx, by = P[1]
        cx2, cy2 = P[2]
        d = (by - cy2) * (ax - cx2) + (cx2 - bx) * (ay - cy2)
        if abs(d) < 1e-9:
            continue
        w0 = ((by - cy2) * (gx - cx2) + (cx2 - bx) * (gy - cy2)) / d
        w1 = ((cy2 - ay) * (gx - cx2) + (ax - cx2) * (gy - cy2)) / d
        w2 = 1.0 - w0 - w1
        dentro = (w0 >= -0.002) & (w1 >= -0.002) & (w2 >= -0.002)
        if not dentro.any():
            continue

        z = w0 * Z[0] + w1 * Z[1] + w2 * Z[2]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        melhor = dentro & (z < sub)
        if not melhor.any():
            continue

        u = w0 * U[0, 0] + w1 * U[1, 0] + w2 * U[2, 0]
        v = w0 * U[0, 1] + w1 * U[1, 1] + w2 * U[2, 1]

        nx = w0 * N[0, 0] + w1 * N[1, 0] + w2 * N[2, 0]
        ny = w0 * N[0, 1] + w1 * N[1, 1] + w2 * N[2, 1]
        nz = w0 * N[0, 2] + w1 * N[1, 2] + w2 * N[2, 2]
        ln = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9
        dif = np.clip((nx * luz[0] + ny * luz[1] + nz * luz[2]) / ln, 0, 1)

        # a normal aponta para fora do modelo; a luz vem de "tras da camera"
        # (a camera olha para +Z depois da rotacao), entao usa -Z da normal
        sombra = 0.42 + 0.72 * dif

        ti = np.clip((u * tw).astype(int), 0, tw - 1)
        tj = np.clip((v * th).astype(int), 0, th - 1)
        cor = tex[tj, ti] * sombra[:, :, None]

        alvo = img[y0:y1 + 1, x0:x1 + 1]
        alvo[melhor] = cor[melhor]
        sub[melhor] = z[melhor]
        zbuf[y0:y1 + 1, x0:x1 + 1] = sub

    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    # a textura inteira: a região do ROSTO é o quadrante 0..0.5 (igual ao Java)
    rosto = Image.open(TEX).convert('RGBA')

    vistas = [(0.0, -0.08, 'FRENTE'), (0.62, -0.10, '3/4'), (1.15, -0.10, 'LADO')]
    lado = 420
    folga = 10
    tira = Image.new('RGB', (lado * 3 + folga * 4, lado + 46), (16, 15, 19))
    d = ImageDraw.Draw(tira)
    for i, (yaw, pitch, nome) in enumerate(vistas):
        img = render(rosto, yaw, pitch, lado)
        # espelha X: a projecao acima usa +X para a direita da tela
        tira.paste(img, (folga + i * (lado + folga), 36))
        d.text((folga + i * (lado + folga) + 4, 12), f'MASCARA DE PEDRA - {nome}', fill=(230, 226, 214))
    d.text((folga + 2, lado + 40 - 22), 'malha 19x17 - casca curva (raio 4,55) + relevo - normais por diferenca finita',
           fill=(150, 146, 136))

    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'mascara3d.png'
    tira.save(destino)
    print(destino, destino.stat().st_size)


if __name__ == '__main__':
    main()
