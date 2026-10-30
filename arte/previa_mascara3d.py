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
Z_FRENTE = -4.62                   # plano da frente (o rosto fica em z = -4)
ENROLA = 1.70                      # quanto o extremo (|u| > 0,88) enrola para trás
ESPESSURA = 0.62                   # grossura da pedra (fecha o volume)
Y_BASE = -4.35                     # embaixo (abaixo do queixo)
Y_TOPO = 4.15                      # em cima (testa)

# ==================================================== superfície
def largura(v):
    """Meia-largura da máscara na altura v (0 = queixo, 1 = testa)."""
    v = min(1.0, max(0.0, v))
    return 1.35 + 2.85 * (math.sin(math.pi * v) ** 0.75)


def gauss(x):
    return math.exp(-x * x)


def relevo(u, v):
    """Relevo em unidades do modelo; positivo empurra a pedra para FORA (para -Z)."""
    a = abs(u)
    r = 0.0
    r += 0.12 * gauss(u / 0.60)                                                 # massa do rosto (leve)
    r += 0.12 * gauss(u / 0.20)                                                 # quilha central
    r += 0.95 * gauss(u / 0.14) * gauss((v - 0.470) / 0.105)                    # nariz comprido
    r += 0.34 * gauss(u / 0.09) * gauss((v - 0.585) / 0.090)                    # dorso do nariz
    r += 0.30 * gauss((a - 0.20) / 0.10) * gauss((v - 0.43) / 0.045)            # asas do nariz
    r += 0.30 * gauss((a - 0.16) / 0.24) * gauss((v - 0.705) / 0.035)           # glabela/sobrancelha
    r -= 0.52 * gauss((a - 0.34) / 0.115) * gauss((v - 0.615) / 0.050)          # órbitas fundas
    r += 0.30 * gauss((a - 0.56) / 0.14) * gauss((v - 0.40) / 0.065)            # maçãs
    r -= 0.20 * gauss(u / 0.24) * gauss((v - 0.215) / 0.032)                    # boca
    r -= 0.24 * gauss((v - 0.030) / 0.048)                                      # queixo
    r -= 0.06 * gauss((v - 0.985) / 0.030)                                      # topo
    return r


def ponto(u, v, rel=1.0):
    """Um ponto da casca externa (rel=1) ou da interna (rel=0)."""
    w = largura(v)
    x = w * u
    # frente quase plana; só o terço externo (|u| > 0,55) enrola para trás
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
    # a normal tem de apontar para FORA (para -Z na frente)
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
    """Pinta a textura da MALHA. u = (u+1)/2 (esquerda->direita), v = 1 - y/lado (v=1 em cima)."""
    rng = np.random.default_rng(11)
    base = np.array([186, 179, 163], dtype=np.float32)

    X = lambda u: int((u + 1.0) * 0.5 * (lado - 1))
    Y = lambda v: int((1.0 - v) * (lado - 1))

    # pedra: ruído em 4 escalas
    img = np.zeros((lado, lado, 3), np.float32)
    for oitava, amp in ((8, 11.0), (16, 7.0), (48, 4.0), (128, 2.0)):
        n = rng.normal(0, 1, (oitava, oitava))
        up = np.array(Image.fromarray(((n - n.min()) / (n.max() - n.min()) * 255).astype(np.uint8))
                      .resize((lado, lado), Image.BICUBIC), dtype=np.float32) / 255.0 - 0.5
        img += up[:, :, None] * amp
    img += base

    Yg, Xg = np.mgrid[0:lado, 0:lado]
    uu = Xg / (lado - 1.0) * 2.0 - 1.0
    vv = 1.0 - Yg / (lado - 1.0)

    # luz assada do relevo (mesma leitura do Java: topo-esquerda)
    for (cu, cv, sx, sy, força) in [
            (0.0, 0.46, 0.10, 0.07, 0.42),        # dorso do nariz (claro)
            (0.16, 0.46, 0.07, 0.09, -0.55),      # lado direito do nariz (sombra)
            (-0.30, 0.615, 0.14, 0.06, -0.60),    # órbita esquerda
            (0.30, 0.615, 0.14, 0.06, -0.60),     # órbita direita
            (-0.05, 0.70, 0.34, 0.03, 0.30),      # sobrancelha
            (-0.52, 0.40, 0.12, 0.10, 0.25),      # maçã esquerda
            (0.52, 0.40, 0.12, 0.10, -0.30),      # maçã direita
            (0.0, 0.215, 0.22, 0.03, -0.55),      # boca
            (0.0, 0.02, 0.22, 0.04, -0.35)]:      # queixo
        img += (força * np.exp(-((uu - cu) / sx) ** 2 - ((vv - cv) / sy) ** 2))[:, :, None] * 30

    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).convert('RGB')
    d = ImageDraw.Draw(im)

    ESCURO, FUNDO, CLARO = (26, 24, 22), (52, 48, 43), (233, 228, 213)
    PEDRA_ESC, LUZ_RACHA = (108, 102, 89), (222, 216, 200)

    # ---- rachaduras (galhos, com brilho do lado de cima = lasca levantada)
    def racha(x, y, ang, comp, prof):
        x0, y0 = x, y
        for _ in range(comp):
            ang += rng.normal(0, 0.45)
            nx, ny = x + math.cos(ang) * rng.uniform(5, 11), y + math.sin(ang) * rng.uniform(5, 11)
            d.line((x, y, nx, ny), fill=(LUZ_RACHA if prof < 2 else PEDRA_ESC), width=1)
            d.line((x + 1, y - 1, nx + 1, ny - 1), fill=PEDRA_ESC, width=1)
            d.line((x - 1, y + 1, nx - 1, ny + 1), fill=ESCURO, width=1)
            x, y = nx, ny
            if rng.random() < 0.4 and prof < 3:
                racha(x, y, ang + rng.choice([-1.0, 1.0]), int(comp * 0.5), prof + 1)
        d.line((x0, y0, x, y), fill=PEDRA_ESC, width=1)

    for k in range(14):
        racha(int(rng.integers(6, lado - 6)), int(rng.integers(0, lado)), rng.uniform(0, 6.28),
              int(rng.integers(3, 9)), 0)

    # ---- olhos: órbita funda, pálpebra pesada e esclera (o direito meio velado)
    for lado_s, cx in (('e', X(-0.34)), ('d', X(0.34))):
        cy = Y(0.615)
        w, h = int(lado * 0.105), int(lado * 0.045)
        # órbita escura
        d.polygon([(cx - w, cy + h * 0.55), (cx - w * 0.5, cy - h), (cx + w * 0.6, cy - h * 0.8),
                   (cx + w, cy + h * 0.35), (cx + w * 0.5, cy + h), (cx - w * 0.4, cy + h * 0.9)],
                  fill=FUNDO)
        # fenda do olho
        d.polygon([(cx - w * 0.72, cy + h * 0.25), (cx - w * 0.2, cy - h * 0.55),
                   (cx + w * 0.62, cy - h * 0.30), (cx + w * 0.5, cy + h * 0.45)], fill=ESCURO)
        # esclera
        ex0, ey0 = cx - w * 0.45, cy - h * 0.35
        ex1, ey1 = cx + w * 0.42, cy + h * 0.30
        d.ellipse((ex0, ey0, ex1, ey1), fill=(206, 201, 186) if lado_s == 'e' else (150, 146, 134))
        d.ellipse((cx - w * 0.12, cy - h * 0.18, cx + w * 0.12, cy + h * 0.22), fill=ESCURO)
        # pálpebra de cima, grossa e caída
        d.polygon([(cx - w * 0.8, cy - h * 0.5), (cx + w * 0.7, cy - h * 0.34), (cx + w * 0.6, cy - h * 0.05),
                   (cx - w * 0.7, cy - h * 0.15)], fill=(84, 79, 70))
        d.line((cx - w, cy + h * 0.6, cx + w, cy + h * 0.5), fill=(120, 114, 100), width=2)

    # ---- nariz: dorso claro, asas e narinas
    d.line((X(-0.03), Y(0.60), X(-0.03), Y(0.42)), fill=(226, 221, 206), width=4)
    d.line((X(0.06), Y(0.60), X(0.06), Y(0.42)), fill=(120, 114, 100), width=5)
    d.ellipse((X(-0.17), Y(0.455), X(-0.04), Y(0.415)), fill=ESCURO)   # narina esquerda
    d.ellipse((X(0.04), Y(0.455), X(0.17), Y(0.415)), fill=ESCURO)     # narina direita
    d.arc((X(-0.15), Y(0.48), X(0.15), Y(0.40)), 200, 340, fill=(150, 144, 130), width=3)

    # ---- boca: buraco escuro + presas de vampiro
    bx0, bx1 = X(-0.26), X(0.26)
    by0, by1 = Y(0.245), Y(0.185)
    d.ellipse((bx0, by0, bx1, by1), fill=ESCURO)
    d.ellipse((bx0 + 5, by0 + 4, bx1 - 5, by1 - 3), fill=(14, 13, 12))
    for t in (-0.14, 0.14):
        px = X(t)
        d.polygon([(px - int(lado * 0.022), by0 + 2), (px + int(lado * 0.022), by0 + 2),
                   (px, by0 + int(lado * 0.085))], fill=CLARO)
    d.line((bx0 - 6, by1 + 4, bx1 + 6, by1 + 5), fill=(214, 209, 194), width=3)   # lábio de baixo

    # ---- lascas nas bordas (silhueta irregular)
    for _ in range(10):
        lado_s = rng.choice([-1, 1])
        x = int(lado * 0.5 + lado_s * lado * rng.uniform(0.30, 0.48))
        y = int(rng.integers(0, lado))
        r = int(rng.integers(4, 11))
        d.polygon([(x, y), (x + lado_s * r, y + r // 2), (x, y + r)], fill=PEDRA_ESC)

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
        sombra = 0.34 + 0.68 * dif

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
