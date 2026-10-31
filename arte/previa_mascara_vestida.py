#!/usr/bin/env python3
"""Prova visual da máscara vestida (5.2.2) — usa a MESMA malha do jar.

Cenário em espaço de MODELO do Minecraft (1 unidade = 1 px, +Y para BAIXO):
cabeça 8³ com o queixo em y=0, camada do chapéu 9³ (o mesmo cubo inflado 0,5 do
PlayerModel) e corpo de blocos. Pele clara de propósito: com skin preta a
silhueta da máscara some no fundo escuro e a prévia mente.

Uso: python3 arte/previa_mascara_vestida.py
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, "/home/user/rp-tag/arte")
from render_mascara_offline import carregar, TEX, GEO_MASCARA, rot_y, escala3, mul

TEXA = np.array(Image.open(TEX).convert("RGB"))
ESC = 1.0 / 16.0
PELE = (214, 178, 148)
CHAPEU = (120, 96, 74)
ROUPA = (52, 64, 104)
CALCA = (38, 40, 62)


def bloco(cx, cy, cz, sx, sy, sz):
    x0, x1 = cx - sx / 2, cx + sx / 2
    y0, y1 = cy - sy / 2, cy + sy / 2
    z0, z1 = cz - sz / 2, cz + sz / 2
    c = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]
    pos = []
    for a, b, cc, d in faces:
        for tri in [(a, b, cc), (a, cc, d)]:
            for i in tri:
                pos.append(c[i])
    pos = np.array(pos, np.float32)
    nrm = np.zeros_like(pos)
    centro = np.array([cx, cy, cz], np.float32)
    for i in range(0, len(pos), 3):
        n = (pos[i] + pos[i + 1] + pos[i + 2]) / 3 - centro
        ln = np.linalg.norm(n)
        nrm[i:i + 3] = n / ln if ln > 1e-6 else np.array([0, 0, -1.0], np.float32)
    return dict(pos=pos, nrm=nrm, uv=np.zeros((len(pos), 2), np.float32), n=len(pos))


def juntar(*ms):
    return dict(pos=np.concatenate([m["pos"] for m in ms], 0),
                nrm=np.concatenate([m["nrm"] for m in ms], 0),
                uv=np.concatenate([m["uv"] for m in ms], 0),
                n=sum(m["n"] for m in ms))


def chapada(cor, tam=16):
    a = np.zeros((tam, tam, 3), np.uint8)
    a[:, :] = cor
    return a


def raster(camadas, M, W=430, H=470, dist=3.0, zoom=1.55, fundo=(150, 190, 225), y_down=True):
    """Desenha na ordem da lista (sem oclusão entre camadas, como o jogo faz por
    ordem de render). `y_down`: +Y do modelo aponta para baixo (câmera gira)."""
    img = np.zeros((H, W, 3), np.float32)
    img[:, :] = np.array(fundo, np.float32) / 255.0
    luz = np.array([-0.45, -0.35, -0.82])     # do alto-esquerda, vindo da frente
    if y_down:
        luz[1] = -luz[1]
    luz /= np.linalg.norm(luz)
    f = W * 0.9 * zoom
    for malha, tex in camadas:
        pos = malha["pos"] @ M[:3, :3].T + M[:3, 3]
        nrm = malha["nrm"] @ np.linalg.inv(M[:3, :3]).T
        nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
        cam = pos.copy()
        cam[:, 2] += dist
        sx = W * 0.5 + cam[:, 0] / cam[:, 2] * f
        sy = H * 0.5 + (cam[:, 1] / cam[:, 2] * f if y_down else -cam[:, 1] / cam[:, 2] * f)
        th, tw = tex.shape[:2]
        dif = np.clip(nrm @ luz, 0, 1) * 0.70 + 0.40
        zb = np.full((H, W), 1e9, np.float32)
        for q in range(0, malha["n"], 3):
            i0, i1, i2 = q, q + 1, q + 2
            x0, y0, x1, y1, x2, y2 = sx[i0], sy[i0], sx[i1], sy[i1], sx[i2], sy[i2]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            xmin = max(int(np.floor(min(x0, x1, x2))), 0)
            xmax = min(int(np.ceil(max(x0, x1, x2))), W - 1)
            ymin = max(int(np.floor(min(y0, y1, y2))), 0)
            ymax = min(int(np.ceil(max(y0, y1, y2))), H - 1)
            if xmin > xmax or ymin > ymax:
                continue
            X, Y = np.meshgrid(np.arange(xmin, xmax + 1) + .5, np.arange(ymin, ymax + 1) + .5)
            w0 = ((x1 - X) * (y2 - Y) - (x2 - X) * (y1 - Y)) / area
            w1 = ((x2 - X) * (y0 - Y) - (x0 - X) * (y2 - Y)) / area
            w2 = 1 - w0 - w1
            dentro = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not dentro.any():
                continue
            z = w0 * cam[i0, 2] + w1 * cam[i1, 2] + w2 * cam[i2, 2]
            sel = dentro & (z < zb[ymin:ymax + 1, xmin:xmax + 1])
            if not sel.any():
                continue
            uu = w0 * malha["uv"][i0, 0] + w1 * malha["uv"][i1, 0] + w2 * malha["uv"][i2, 0]
            vv = w0 * malha["uv"][i0, 1] + w1 * malha["uv"][i1, 1] + w2 * malha["uv"][i2, 1]
            tx = np.clip((uu % 1.0) * (tw - 1), 0, tw - 1).astype(int)
            ty = np.clip(np.clip(vv, 0, 1) * (th - 1), 0, th - 1).astype(int)
            cor = tex[ty, tx].astype(np.float32) / 255.0
            sh = (w0 * dif[i0] + w1 * dif[i1] + w2 * dif[i2])[..., None]
            cor = np.clip(cor * sh, 0, 1)
            zb[ymin:ymax + 1, xmin:xmax + 1][sel] = z[sel]
            img[ymin:ymax + 1, xmin:xmax + 1][sel] = cor[sel]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def cena(yaw, mascara, com_chapeu=True, mat_extra=None):
    M = mul(rot_y(yaw), escala3(ESC, ESC, ESC))
    if mat_extra is not None:
        M = mul(M, mat_extra)
    cabeca = bloco(0, -4, 0, 8, 8, 8)
    chapeu = bloco(0, -4, 0, 9, 9, 9)
    corpo = bloco(0, 10, 0, 8, 12, 4)
    bracos = juntar(bloco(-6, 10, 0, 4, 12, 4), bloco(6, 10, 0, 4, 12, 4))
    pernas = bloco(0, 22, 0, 8, 12, 4)
    camadas = [(juntar(corpo, bracos), chapada(ROUPA)), (pernas, chapada(CALCA)),
               (cabeca, chapada(PELE))]
    if com_chapeu:
        camadas.append((chapeu, chapada(CHAPEU)))
    camadas.append((mascara, TEXA))
    return raster(camadas, M)


def principal():
    F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
    Fp = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    nova = carregar(GEO_MASCARA)
    velha = carregar("/tmp/mascara_glb_backup.bin")

    vistas = [(-20, "frente"), (30, "3/4"), (80, "lado"), (150, "quase de costas")]
    antes = [cena(y, velha) for y, _ in vistas]
    depois = [cena(y, nova) for y, _ in vistas]
    W, H = antes[0].size
    grade = Image.new("RGB", (4 * (W + 8) + 8, 2 * (H + 40) + 40), (22, 26, 34))
    d = ImageDraw.Draw(grade)
    d.text((12, 8), "5.2.1 — a máscara ficava 63% ATRÁS da camada do chapéu da skin "
                    "(só sobrava o oval)", font=F, fill=(255, 150, 130))
    for i, im in enumerate(antes):
        grade.paste(im, (8 + i * (W + 8), 32))
    y2 = 32 + H + 14
    d.text((12, y2), "5.2.2 — cobrindo o rosto inteiro, 100% à frente do chapéu, "
                     "normais e winding corrigidos", font=F, fill=(150, 235, 160))
    for i, im in enumerate(depois):
        grade.paste(im, (8 + i * (W + 8), y2 + 32))
        d.text((10 + i * (W + 8), y2 + 18), vistas[i][1], font=Fp, fill=(165, 180, 200))
    grade.save("/home/user/previa/mascara_522_vestida.png")
    print("ok previa/mascara_522_vestida.png", grade.size)


if __name__ == "__main__":
    principal()
