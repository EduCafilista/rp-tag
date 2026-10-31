#!/usr/bin/env python3
"""Prova visual da máscara VESTIDA e do ITEM (5.2.1), com o personagem inteiro.

Rasteriza o MESMO .bin do jar. O corpo é um bloco simples (steve 8×12×4 px) só
para dar escala — o ponto é provar que a máscara assentou no rosto.
"""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, "/home/user/rp-tag/arte")
from render_mascara_offline import carregar, render, TEX, GEO_MASCARA, trans, escala3, mul, ident

TEXA = np.array(Image.open(TEX).convert("RGB"))
MALHA = carregar(GEO_MASCARA)
ESC = 1.0 / 16.0


def bloco(cx, cy, cz, sx, sy, sz):
    """caixa de 12 triângulos (em pixels do modelo) — o corpo do boneco."""
    x0, x1 = cx - sx / 2, cx + sx / 2
    y0, y1 = cy - sy / 2, cy + sy / 2
    z0, z1 = cz - sz / 2, cz + sz / 2
    c = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]
    pos, nrm = [], []
    for a, b, cc, d in faces:
        for tri in [(a, b, cc), (a, cc, d)]:
            for i in tri:
                pos.append(c[i])
                nrm.append((0, 0, 0))
    pos = np.array(pos, np.float32)
    nrm = np.zeros_like(pos)
    # normal por face (recalculada no render por diferença? aqui basta apontar pro fora do centro)
    centro = np.array([cx, cy, cz], np.float32)
    for i in range(0, len(pos), 3):
        n = pos[i] - centro
        n[1] = 0 if abs(n[1]) < 1e-6 else n[1]
        ln = np.linalg.norm(n)
        n = n / ln if ln > 1e-6 else np.array([0, 0, -1])
        nrm[i:i + 3] = n
    uv = np.zeros((len(pos), 2), np.float32)
    return dict(pos=pos, nrm=nrm, uv=uv, n=len(pos))


def juntar(*ms):
    pos = np.concatenate([m["pos"] for m in ms], 0)
    nrm = np.concatenate([m["nrm"] for m in ms], 0)
    uv = np.concatenate([m["uv"] for m in ms], 0)
    return dict(pos=pos, nrm=nrm, uv=uv, n=len(pos))


def cor_solida(tex_rgb):
    return np.tile(np.array(tex_rgb, np.uint8), (64, 64, 1))


def rot_xyz(rx, ry, rz):
    import math

    def aq(ax, deg):
        a = math.radians(deg) / 2
        s, c = math.sin(a), math.cos(a)
        return {'x': (s, 0, 0, c), 'y': (0, s, 0, c), 'z': (0, 0, s, c)}[ax]

    def qm(a, b):
        x1, y1, z1, w1 = a
        x2, y2, z2, w2 = b
        return (w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
                w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
                w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2)

    x, y, z, w = qm(qm(aq('x', rx), aq('y', ry)), aq('z', rz))
    M = ident()
    M[:3, :3] = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
    return M


def cena_personagem(yaw):
    """Cabeça (8³, queixo y=0, topo y=-8) + corpo + máscara em 1/16."""
    cabeca = bloco(0, -4, 0, 8, 8, 8)
    corpo = bloco(0, 11, 0, 8, 12, 4)
    pernas = bloco(0, 23, 0, 8, 12, 4)
    boneco = juntar(corpo, pernas)
    mala = MALHA
    R = mul(rot_xyz(0, yaw, 0))
    S = mul(escala3(ESC, ESC, ESC), R)
    # corpo desenhado sem textura de pele (cinza-azulado), máscara com a textura
    img = render(boneco, S, cor_solida((58, 86, 140)), W=420, H=460, zoom=1.35, dist=3.4,
                 fundo=(150, 190, 225), luz_dir=[-0.4, 0.6, -0.7])
    img2 = render(mala, S, TEXA, W=420, H=460, zoom=1.35, dist=3.4, fundo=(150, 190, 225),
                  luz_dir=[-0.4, 0.6, -0.7])
    # compõe: a máscara por cima do corpo só onde ela aparece (usa o próprio zbuffer:
    # mais simples, redesenha as duas na mesma imagem)
    ambos = juntar(boneco, mala)
    return render(ambos, S, np.concatenate([np.tile(cor_solida((58, 86, 140)), (1, 1, 1)),
                                            np.tile(TEXA, (400, 400, 1))[:1]], 0)[0] if False else TEXA,
                  W=420, H=460, zoom=1.35, dist=3.4, fundo=(150, 190, 225),
                  luz_dir=[-0.4, 0.6, -0.7])


def cena_personagem2(yaw, cor_pele=(58, 86, 140)):
    """Versão correta: máscara e corpo separados, corpo com textura chapada."""
    cabeca = bloco(0, -4, 0, 8, 8, 8)
    corpo = bloco(0, 11, 0, 8, 12, 4)
    pernas = bloco(0, 23, 0, 8, 12, 4)
    R = mul(rot_xyz(0, yaw, 0))
    S = mul(escala3(ESC, ESC, ESC), R)
    fundo = (150, 190, 225)
    linha1 = render(juntar(cabeca, corpo, pernas), S, cor_solida(cor_pele), W=420, H=470,
                    zoom=1.30, dist=3.6, fundo=fundo, luz_dir=[-0.4, 0.6, -0.7])
    return linha1


def principal():
    from PIL import ImageDraw, ImageFont
    F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
    Fp = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)

    vistas = [(-22, "frente"), (34, "3/4"), (86, "lado"), (150, "quase de costas")]
    telas = []
    for yaw, nome in vistas:
        cabeca = bloco(0, -4, 0, 8, 8, 8)
        corpo = bloco(0, 11, 0, 8, 12, 4)
        pernas = bloco(0, 23, 0, 8, 12, 4)
        R = mul(rot_xyz(0, yaw, 0))
        S = mul(escala3(ESC, ESC, ESC), R)
        fundo = (150, 190, 225)
        # desenha corpo (textura chapada) e máscara (textura do GLB) por cima
        from render_mascara_offline import render as _r
        base = _r(juntar(cabeca, corpo, pernas), S, cor_solida((62, 92, 148)), W=400, H=450,
                  zoom=1.30, dist=3.6, fundo=fundo, luz_dir=[-0.4, 0.6, -0.7])
        # segunda passada só da máscara, com transparência onde não há triângulo
        pos = MALHA["pos"] @ S[:3, :3].T + S[:3, 3]
        nrm = MALHA["nrm"] @ np.linalg.inv(S[:3, :3]).T
        n = MALHA["n"]
        # rasteriza a máscara numa imagem com alpha
        mask_img = np.zeros((450, 400, 4), np.float32)
        D = 3.6
        f = 400 * 0.9 * 1.30
        cam = pos.copy(); cam[:, 2] += D
        sx = 400 * 0.5 + cam[:, 0] / cam[:, 2] * f
        sy = 450 * 0.5 - cam[:, 1] / cam[:, 2] * f
        luz = np.array([-0.4, 0.6, -0.7]); luz /= np.linalg.norm(luz)
        nrm = nrm / np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
        dif = np.clip(nrm @ luz, 0, 1) * 0.75 + 0.35
        base_a = np.array(base, np.float32) / 255.0
        tex = TEXA
        th, tw = tex.shape[:2]
        zbuf = np.full((450, 400), 1e9)
        for q in range(0, n, 3):
            i0, i1, i2 = q, q + 1, q + 2
            x0, y0, z0 = sx[i0], sy[i0], pos[i0, 2] + D
            x1, y1, z1 = sx[i1], sy[i1], pos[i1, 2] + D
            x2, y2, z2 = sx[i2], sy[i2], pos[i2, 2] + D
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            xmin = max(int(np.floor(min(x0, x1, x2))), 0); xmax = min(int(np.ceil(max(x0, x1, x2))), 399)
            ymin = max(int(np.floor(min(y0, y1, y2))), 0); ymax = min(int(np.ceil(max(y0, y1, y2))), 449)
            if xmin > xmax or ymin > ymax:
                continue
            X, Y = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
            w0 = ((x1 - X) * (y2 - Y) - (x2 - X) * (y1 - Y)) / area
            w1 = ((x2 - X) * (y0 - Y) - (x0 - X) * (y2 - Y)) / area
            w2 = 1 - w0 - w1
            dentro = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not dentro.any():
                continue
            zz = w0 * z0 + w1 * z1 + w2 * z2
            sel = dentro & (zz < zbuf[ymin:ymax + 1, xmin:xmax + 1])
            if not sel.any():
                continue
            uu = w0 * MALHA["uv"][i0, 0] + w1 * MALHA["uv"][i1, 0] + w2 * MALHA["uv"][i2, 0]
            vv = w0 * MALHA["uv"][i0, 1] + w1 * MALHA["uv"][i1, 1] + w2 * MALHA["uv"][i2, 1]
            tx = np.clip((uu % 1.0) * (tw - 1), 0, tw - 1).astype(int)
            ty = np.clip(np.clip(vv, 0, 1) * (th - 1), 0, th - 1).astype(int)
            cor = tex[ty, tx].astype(np.float32) / 255.0
            sh = (w0 * dif[i0] + w1 * dif[i1] + w2 * dif[i2])[..., None]
            cor = np.clip(cor * sh, 0, 1)
            zbuf[ymin:ymax + 1, xmin:xmax + 1][sel] = zz[sel]
            base_a[ymin:ymax + 1, xmin:xmax + 1][sel] = cor[sel]
        im = Image.fromarray((np.clip(base_a, 0, 1) * 255).astype(np.uint8))
        d = ImageDraw.Draw(im)
        d.text((10, 8), nome, font=F, fill=(20, 30, 45))
        telas.append(im)

    grade = Image.new("RGB", (4 * 410 + 10, 450 + 74), (24, 30, 40))
    dg = ImageDraw.Draw(grade)
    dg.text((12, 10), "Máscara de Pedra 5.2.1 — vestida no personagem (mesma malha do jar, escala 1/16)",
            font=F, fill=(235, 240, 250))
    for i, im in enumerate(telas):
        grade.paste(im, (8 + i * 410, 40))
    dg.text((12, 40 + 450 + 6),
            "corpo em bloco cinza-azulado só para escala · a máscara cobre o rosto (x ±2,7 · y −7,45..−0,45 · z −5,05..−3,75 px)",
            font=Fp, fill=(150, 165, 185))
    grade.save("/home/user/previa/mascara_521_vestida.png")
    print("ok previa/mascara_521_vestida.png")


if __name__ == "__main__":
    principal()
