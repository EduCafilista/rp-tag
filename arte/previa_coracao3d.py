"""CORAÇÃO DE LORE 5.0 — prototipo e render de conferencia (mesma matematica do Java).

O coracao do bloco `rptag:coracao_canon` era um SOLIDO VOXEL (fatias de 1 px):
de frente lia como coracao, de lado virava um amontoado de tabuas. Agora ele e
uma MALHA ESCULPIDA, feita a mao, igual a mascara:

  * contorno: a curva classica do coracao (x = 16 sin^3 t, y = 13cos t - 5cos2t
    - 2cos3t - cos4t), normalizada para 1 x 0,95;
  * corpo: cada ponto do contorno vira uma "fatia" s de 0 a 1 a partir do
    centro; no eixo Z a espessura afunda como sqrt(1 - s^2) -> um coracao
    cheinho, com dois lobulos e a ponta em V, visto de QUALQUER angulo;
  * normais por diferenca finita; cor assada (gradiente rosa -> vinho + luz
    fixa + brilho especular no lobulo esquerdo).

uso: python arte/previa_coracao3d.py   ->  previa/coracao3d.png
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / 'previa'

# ==================================================== constantes (batem com o Java)
NT = 56          # pontos em volta do contorno
NS = 13          # aneis do centro ate a borda
ESPESSURA = 0.34  # metade da grossura (em blocos)
LARGURA = 0.95    # largura final em blocos
ALTURA = 0.90     # altura final em blocos

TOPO = (0xF7, 0x5C, 0x7C)
MEIO = (0xDC, 0x18, 0x3E)
PONTA = (0x84, 0x08, 0x26)
LUZ = np.array([-0.35, 0.62, -0.70])
LUZ = LUZ / np.linalg.norm(LUZ)


def contorno(t):
    """Ponto do contorno do coracao (t em [0,1)). x em [-1,1]."""
    a = 2.0 * math.pi * t
    x = 16.0 * math.sin(a) ** 3
    y = 13.0 * math.cos(a) - 5.0 * math.cos(2 * a) - 2.0 * math.cos(3 * a) - math.cos(4 * a)
    return x / 16.0, y / 16.0


# ---- normaliza o contorno para a caixa [-1,1] x [-1,1] (mesma conta do Java)
_pontos = [contorno(k / 400.0) for k in range(400)]
_xmin = min(p[0] for p in _pontos); _xmax = max(p[0] for p in _pontos)
_ymin = min(p[1] for p in _pontos); _ymax = max(p[1] for p in _pontos)
CX = (_xmin + _xmax) * 0.5
CY = (_ymin + _ymax) * 0.5
ESC = 2.0 / max(_xmax - _xmin, _ymax - _ymin)


def contorno_norm(t):
    x, y = contorno(t)
    return (x - CX) * ESC, (y - CY) * ESC


def ponto(t, s, frente=True):
    """Superfície: s = 0 (centro) ate 1 (borda). frente/verso no eixo Z."""
    cx, cy = contorno_norm(t)
    x = cx * s * (LARGURA * 0.5)
    y = cy * s * (ALTURA * 0.5) + ALTURA * 0.5
    z = ESPESSURA * math.sqrt(max(0.0, 1.0 - s * s)) * (1.0 if frente else -1.0)
    return np.array([x, y, z])


def normal(t, s, frente=True):
    e = 1e-3
    t0, t1 = (t - e) % 1.0, (t + e) % 1.0
    s0, s1 = max(0.0, s - e), min(1.0, s + e)
    dt = ponto(t1, s, frente) - ponto(t0, s, frente)
    ds = ponto(t, s1, frente) - ponto(t, s0, frente)
    n = np.cross(ds, dt)
    ln = np.linalg.norm(n)
    n = np.array([0.0, 0.0, 1.0 if frente else -1.0]) if ln < 1e-9 else n / ln
    # aponta para fora do centro do coracao
    centro = np.array([0.0, ALTURA * 0.5, 0.0])
    if float(np.dot(n, ponto(t, s, frente) - centro)) < 0:
        n = -n
    return n


def cor(t, s, n, frente=True):
    """Cor assada: gradiente vertical + luz + especular no lobulo esquerdo."""
    p = ponto(t, s, frente)
    u = min(1.0, max(0.0, p[1] / ALTURA))
    if u < 0.5:
        f = u * 2.0
        c = [TOPO[i] + (MEIO[i] - TOPO[i]) * f for i in range(3)]
    else:
        f = (u - 0.5) * 2.0
        c = [MEIO[i] + (PONTA[i] - MEIO[i]) * f for i in range(3)]
    dif = max(0.0, float(np.dot(n, LUZ)))
    k = 0.52 + 0.62 * dif
    # brilho especular (lobulo esquerdo, so na frente)
    esp = 0.0
    if frente:
        ex = (p[0] + 0.30) / 0.22
        ey = (p[1] - ALTURA * 0.80) / 0.20
        esp = 0.30 * math.exp(-(ex * ex + ey * ey)) * max(0.0, float(np.dot(n, LUZ))) ** 2
    return [min(255.0, c[i] * k + (255 - c[i]) * esp) for i in range(3)]


def malha():
    """Triangulos com posicao, normal e cor por vertice."""
    tris = []
    # aneis (frente e verso) + o ponto do centro de cada lado
    for frente in (True, False):
        s_lista = [0.14 + (1.0 - 0.14) * (k / (NS - 1)) for k in range(NS)]
        for k in range(NS - 1):
            s0, s1 = s_lista[k], s_lista[k + 1]
            for j in range(NT):
                t0 = j / NT
                t1 = ((j + 1) % NT) / NT
                q = [(t0, s0), (t1, s0), (t1, s1), (t0, s1)]
                P = [ponto(a, b, frente) for (a, b) in q]
                N = [normal(a, b, frente) for (a, b) in q]
                C = [cor(a, b, n, frente) for (a, b), n in zip(q, N)]
                if not frente:
                    P, N, C = P[::-1], N[::-1], C[::-1]
                for (i0, i1, i2) in ((0, 1, 2), (0, 2, 3)):
                    tris.append((P[i0], P[i1], P[i2], N[i0], N[i1], N[i2], C[i0], C[i1], C[i2]))
        # tampa do centro (leque) para nao ficar buraco no meio
        sm = s_lista[0]
        Pc = ponto(0.0, 0.0, frente)
        Nc = normal(0.0, 0.0, frente)
        Cc = cor(0.0, 0.0, Nc, frente)
        for j in range(NT):
            t0 = j / NT
            t1 = ((j + 1) % NT) / NT
            P = [Pc, ponto(t0, sm, frente), ponto(t1, sm, frente)]
            N = [Nc, normal(t0, sm, frente), normal(t1, sm, frente)]
            C = [Cc, cor(t0, sm, N[1], frente), cor(t1, sm, N[2], frente)]
            if not frente:
                P, N, C = P[::-1], N[::-1], C[::-1]
            tris.append((P[0], P[1], P[2], N[0], N[1], N[2], C[0], C[1], C[2]))
    return tris


# ==================================================== render
def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def render(yaw, pitch, lado=420, fundo=(20, 17, 22)):
    tris = malha()
    R = rot_x(pitch) @ rot_y(yaw)
    escala = lado / 1.35
    cx, cy = lado / 2.0, lado / 2.0
    img = np.zeros((lado, lado, 3), np.float32)
    img[:, :] = np.array(fundo, np.float32) / 255.0
    zbuf = np.full((lado, lado), 1e9, np.float32)

    for (p0, p1, p2, n0, n1, n2, c0, c1, c2) in tris:
        P = np.stack([p0, p1, p2]) @ R.T
        sx = cx + P[:, 0] * escala
        sy = cy - (P[:, 1] - ALTURA * 0.5) * escala
        sz = P[:, 2]
        x0, y0 = int(max(0, np.floor(sx.min()))), int(max(0, np.floor(sy.min())))
        x1, y1 = int(min(lado - 1, np.ceil(sx.max()))), int(min(lado - 1, np.ceil(sy.max())))
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        ax, ay = sx[0], sy[0]
        bx, by = sx[1], sy[1]
        cxx, cyy = sx[2], sy[2]
        d = (by - cyy) * (ax - cxx) + (cxx - bx) * (ay - cyy)
        if abs(d) < 1e-9:
            continue
        w0 = ((by - cyy) * (gx - cxx) + (cxx - bx) * (gy - cyy)) / d
        w1 = ((cyy - ay) * (gx - cxx) + (ax - cxx) * (gy - cyy)) / d
        w2 = 1.0 - w0 - w1
        dentro = (w0 >= -0.002) & (w1 >= -0.002) & (w2 >= -0.002)
        if not dentro.any():
            continue
        z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        melhor = dentro & (z < sub)
        if not melhor.any():
            continue
        cor_ = (w0[:, :, None] * np.array(c0) + w1[:, :, None] * np.array(c1) + w2[:, :, None] * np.array(c2))
        alvo = img[y0:y1 + 1, x0:x1 + 1]
        alvo[melhor] = (cor_ / 255.0)[melhor]
        sub[melhor] = z[melhor]
        zbuf[y0:y1 + 1, x0:x1 + 1] = sub

    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    vistas = [(0.0, 0.0, 'FRENTE'), (0.75, -0.12, '3/4'), (1.57, 0.0, 'LADO')]
    lado, folga = 420, 10
    tira = Image.new('RGB', (lado * 3 + folga * 4, lado + 46), (13, 12, 15))
    d = ImageDraw.Draw(tira)
    for i, (yaw, pitch, nome) in enumerate(vistas):
        im = render(yaw, pitch, lado)
        tira.paste(im, (folga + i * (lado + folga), 36))
        d.text((folga + i * (lado + folga) + 4, 12), f'CORACAO DE LORE - {nome}', fill=(240, 214, 222))
    d.text((folga + 2, lado + 40 - 20),
           f'malha {NT}x{NS} x2 faces - contorno classico + espessura sqrt(1-s^2) - normais por diferenca finita',
           fill=(160, 148, 152))
    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'coracao3d.png'
    tira.save(destino)
    print(destino, destino.stat().st_size, 'triangulos:', len(malha()))


if __name__ == '__main__':
    main()
