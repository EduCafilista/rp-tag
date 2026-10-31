#!/usr/bin/env python3
"""Renderizador offline da máscara (5.2.1) — rasteriza o MESMO .bin do jar.

Serve para conferir geometria e transformações SEM abrir o Minecraft: cada vista
aqui aplica exatamente a matriz que o Java aplica no jogo.

Uso:  python3 arte/render_mascara_offline.py
Saída: previa/mascara_vistas.png  +  previa/mascara_antes_depois.png
"""
import struct
import numpy as np
from PIL import Image

RAIZ = "/home/user/rp-tag/src/main/resources/assets/rptag"
GEO_MASCARA = RAIZ + "/geo/mascara_glb.bin"
TEX = RAIZ + "/textures/entity/mascara_glb.png"


# ----------------------------------------------------------------- malha
def carregar(caminho):
    d = open(caminho, "rb").read()
    magic = d[:4]
    assert magic == b"RPTM", magic
    ver, n = struct.unpack(">ii", d[4:12])
    off = 12
    arrs = []
    for _ in range(8):
        arrs.append(np.frombuffer(d[off:off + 4 * n], dtype=">f4").astype(np.float32))
        off += 4 * n
    px, py, pz, nx, ny, nz, u, v = arrs
    return dict(pos=np.stack([px, py, pz], 1), nrm=np.stack([nx, ny, nz], 1), uv=np.stack([u, v], 1), n=n)


# ----------------------------------------------------------------- matriz
def ident():
    return np.eye(4, dtype=np.float64)


def mul(*ms):
    r = ident()
    for m in ms:
        r = r @ m
    return r


def trans(x, y, z):
    m = ident(); m[0, 3] = x; m[1, 3] = y; m[2, 3] = z; return m


def escala(s):
    m = ident(); m[0, 0] = m[1, 1] = m[2, 2] = s; return m


def escala3(x, y, z):
    m = ident(); m[0, 0] = x; m[1, 1] = y; m[2, 2] = z; return m


def rot_y(deg):
    a = np.radians(deg); c, s = np.cos(a), np.sin(a)
    m = ident(); m[0, 0] = c; m[0, 2] = s; m[2, 0] = -s; m[2, 2] = c; return m


def rot_x(deg):
    a = np.radians(deg); c, s = np.cos(a), np.sin(a)
    m = ident(); m[1, 1] = c; m[1, 2] = -s; m[2, 1] = s; m[2, 2] = c; return m


def rot_z(deg):
    a = np.radians(deg); c, s = np.cos(a), np.sin(a)
    m = ident(); m[0, 0] = c; m[0, 1] = -s; m[1, 0] = s; m[1, 1] = c; return m


# ----------------------------------------------------------------- raster
def render(malha, M, tex, W=520, H=520, zoom=1.0, fundo=(28, 26, 34), luz_dir=None, dist=3.2):
    """Desenha a malha com a matriz M (mundo -> espaço de visão) em perspectiva."""
    luz = np.array(luz_dir if luz_dir is not None else [-0.35, 0.55, -0.75], dtype=np.float64)
    luz /= np.linalg.norm(luz)

    pos = malha["pos"] @ M[:3, :3].T + M[:3, 3]
    nrm = malha["nrm"] @ np.linalg.inv(M[:3, :3]).T
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)

    # câmera: olha para +Z a partir de z=-D (frente da malha em -Z, então a câmera
    # fica no -Z e vê a frente). Projeção simples.
    D = dist
    cam = pos.copy()
    cam[:, 2] += D
    f = W * 0.9 * zoom
    sx = W * 0.5 + cam[:, 0] / cam[:, 2] * f
    sy = H * 0.5 - cam[:, 1] / cam[:, 2] * f
    z = cam[:, 2]

    img = np.zeros((H, W, 3), np.float32)
    img[:, :] = np.array(fundo, np.float32) / 255.0
    zbuf = np.full((H, W), 1e9, np.float32)

    th, tw = tex.shape[:2]
    uv = malha["uv"]
    col = np.zeros((malha["n"], 3), np.float32)
    dif = np.clip(nrm @ luz, 0, 1) * 0.75 + 0.35
    for q in range(0, malha["n"], 3):
        i0, i1, i2 = q, q + 1, q + 2
        x0, y0, z0 = sx[i0], sy[i0], z[i0]
        x1, y1, z1 = sx[i1], sy[i1], z[i1]
        x2, y2, z2 = sx[i2], sy[i2], z[i2]
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        xmin = max(int(np.floor(min(x0, x1, x2))), 0)
        xmax = min(int(np.ceil(max(x0, x1, x2))), W - 1)
        ymin = max(int(np.floor(min(y0, y1, y2))), 0)
        ymax = min(int(np.ceil(max(y0, y1, y2))), H - 1)
        if xmin > xmax or ymin > ymax:
            continue
        xs = np.arange(xmin, xmax + 1) + 0.5
        ys = np.arange(ymin, ymax + 1) + 0.5
        X, Y = np.meshgrid(xs, ys)
        w0 = ((x1 - X) * (y2 - Y) - (x2 - X) * (y1 - Y)) / area
        w1 = ((x2 - X) * (y0 - Y) - (x0 - X) * (y2 - Y)) / area
        w2 = 1.0 - w0 - w1
        dentro = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not dentro.any():
            continue
        zz = w0 * z0 + w1 * z1 + w2 * z2
        sel = dentro & (zz < zbuf[ymin:ymax + 1, xmin:xmax + 1])
        if not sel.any():
            continue
        uu = w0 * uv[i0, 0] + w1 * uv[i1, 0] + w2 * uv[i2, 0]
        vv = w0 * uv[i0, 1] + w1 * uv[i1, 1] + w2 * uv[i2, 1]
        tx = np.clip((uu % 1.0) * (tw - 1), 0, tw - 1).astype(np.int32)
        ty = np.clip(np.clip(vv, 0, 1) * (th - 1), 0, th - 1).astype(np.int32)
        cor = tex[ty, tx].astype(np.float32) / 255.0
        shade = (w0 * dif[i0] + w1 * dif[i1] + w2 * dif[i2])[..., None]
        cor = np.clip(cor * shade, 0, 1)
        sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
        imgz = img[ymin:ymax + 1, xmin:xmax + 1]
        sub[sel] = zz[sel]
        imgz[sel] = cor[sel]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def caixa(imagem, M, aresta=(120, 124, 140), dist=3.2, zoom=1.0, divisor=1.0):
    """Desenha a CABEÇA do jogador com a mesma matriz.

    O cubo do ModelPart é addBox(-4,-8,-4, 8,8,8) em PIXELS: x -4..4, y -8..0
    (queixo em 0, topo em -8) e z -4..4. Em blocos isso é /16 — é a referência
    de escala que prova se a máscara está do tamanho certo.

    `divisor`: a matriz M já traz a escala 1/16 (caso corrigido) ou não (caso do
    bug, em que os vértices vão em pixels crus) — o divisor acompanha.
    """
    from PIL import ImageDraw
    d = ImageDraw.Draw(imagem)
    cantos = []
    for x in (-4, 4):
        for y in (-8, 0):
            for z in (-4, 4):
                p = M[:3, :3] @ (np.array([x, y, z]) / divisor) + M[:3, 3]
                cantos.append(p)
    W, H = imagem.size
    D = dist
    f = W * 0.9 * zoom
    pts = []
    for p in cantos:
        zz = p[2] + D
        pts.append((W * 0.5 + p[0] / zz * f, H * 0.5 - p[1] / zz * f))
    # faces (só o traço, para não tapar a máscara)
    for a, b in [(0, 1), (0, 2), (0, 4), (1, 3), (1, 5), (2, 3), (2, 6), (3, 7), (4, 5), (4, 6), (5, 7), (6, 7)]:
        d.line([pts[a], pts[b]], fill=aresta, width=2)
    return imagem


def principal():
    tex = np.array(Image.open(TEX).convert("RGB"))
    print("textura:", tex.shape)
    geomas = carregar(GEO_MASCARA)
    print(f"malha: {geomas['n']} vértices / {geomas['n']//3} triângulos")

    # ---------- ANTES (o que o 5.2.0 faz): vértices em blocos, sem /16
    antes = render(geomas, mul(rot_x(-8), rot_y(18)), tex, zoom=2.6)
    antes = caixa(antes, mul(rot_x(-8), rot_y(18)), zoom=2.6, divisor=16.0)
    # ---------- DEPOIS (5.2.1): escala 1/16 = pixels do modelo -> blocos
    M = mul(escala(1 / 16.0), rot_x(-6), rot_y(16))
    depois = render(geomas, M, tex, zoom=2.6)
    depois = caixa(depois, M, zoom=2.6)

    # ---------- vistas do DEPOIS
    vistas = []
    for nome, rot, zoom in [("frente", (0, 0), 2.55), ("3/4", (-6, 34), 2.55),
                            ("lado", (0, 88), 2.55), ("baixo", (26, 12), 2.55)]:
        im = render(geomas, mul(escala(1 / 16.0), rot_x(rot[0]), rot_y(rot[1])), tex, zoom=zoom)
        im = caixa(im, mul(escala(1 / 16.0), rot_x(rot[0]), rot_y(rot[1])), zoom=zoom)
        vistas.append((nome, im))

    # grade antes/depois
    from PIL import ImageDraw, ImageFont
    F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 20)
    Fm = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 15)
    par = Image.new("RGB", (1080, 620), (16, 15, 20))
    d = ImageDraw.Draw(par)
    par.paste(antes, (10, 46)); par.paste(depois, (550, 46))
    d.text((12, 14), "5.2.0 (BUG): malha em pixels desenhada como blocos -> 16x maior",
           font=F, fill=(255, 130, 110))
    d.text((552, 14), "5.2.1 (CORRIGIDO): escala 1/16 -> assenta no rosto",
           font=F, fill=(140, 235, 150))
    d.text((12, 560), "caixa cinza = cabeça do jogador (8x8x8 px do modelo)", font=Fm, fill=(160, 160, 175))
    par.save("/home/user/previa/mascara_antes_depois.png")

    grade = Image.new("RGB", (4 * 530 + 10, 590), (16, 15, 20))
    dg = ImageDraw.Draw(grade)
    for i, (nome, im) in enumerate(vistas):
        grade.paste(im, (10 + i * 530, 40))
        dg.text((14 + i * 530, 12), nome, font=F, fill=(230, 225, 240))
    grade.save("/home/user/previa/mascara_vistas.png")
    print("ok: previa/mascara_antes_depois.png e previa/mascara_vistas.png")


if __name__ == "__main__":
    principal()
