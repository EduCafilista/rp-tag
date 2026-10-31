#!/usr/bin/env python3
"""(5.3.0) Converte o .bbmodel do Blockbench (116 cubos) na malha do mod.

O dono mandou o modelo dele: `stone_mask.bbmodel` — 116 cubos, textura
`pedra.png` (128x128), com 85 cubos girados em Y para a máscara curvar em volta
do rosto. Isso é MUITO melhor que a malha do GLB escaneado (8.014 triângulos
irregulares): aqui são 116 × 12 = 1.392 triângulos com UV desenhada à mão.

O conversor faz, na ordem:
1. lê os cubos (from/to, rotação em Y em volta de `origin`, UV por face);
2. gira cada cubo e monta os 4 cantos de cada face como triângulos;
3. leva do espaço do Blockbench (Y pra CIMA, frente em -Z) para o espaço da
   CABEÇA do Minecraft (Y pra BAIXO, frente em -Z): espelha Y;
4. escala/posiciona na cabeça e grava no formato .bin do mod (RPTM v1,
   arrays planares big-endian) + exporta a textura.

Uso: python3 arte/importar_bbmodel.py
"""
import base64
import json
import struct
from pathlib import Path

import numpy as np
from PIL import Image

RAIZ = Path("/home/user/rp-tag/src/main/resources/assets/rptag")
BBMODEL = Path("/home/user/imports/imvaqj.bbmodel")
SAIDA_BIN = RAIZ / "geo/mascara_bb.bin"
SAIDA_PNG = RAIZ / "textures/entity/mascara_bb.png"

# ---- alvo no espaço da CABEÇA (px do modelo): x -4..4, y -8..0, z -4..4
# O modelo tem 10,09 x 10,97 x 5,70. Ele é uma máscara que ENVOLVE o rosto:
# deixamos a altura em 8,9 (cobre testa e queixo) e a largura acompanha a
# proporção (~8,2). O z é comprimido: só a parte da frente pode aparecer, porque
# a camada do chapéu da skin (cubo de ±4,5) cobre tudo o que ficar atrás dela.
ALTURA_ALVO = 8.4
Z_TRAS = -4.55            # a borda de trás encosta na camada do chapéu (-4,5)
PROFUNDIDADE = 2.2        # o modelo tem 5,70 de fundura: comprime (o chapeu cobre o resto)
Y_CENTRO = -4.0           # centro vertical da cabeça


def carrega(caminho):
    j = json.loads(Path(caminho).read_text(encoding="utf-8"))
    # textura embutida (base64)
    tex = j["textures"][0]["source"].split(",", 1)[1]
    img = Image.open(__import__("io").BytesIO(base64.b64decode(tex))).convert("RGB")
    return j, img


def rot_y(v, graus, origem):
    """rotação em volta de +Y (padrão right-handed do THREE/Blockbench)."""
    a = np.radians(graus)
    c, s = np.cos(a), np.sin(a)
    p = v - origem
    out = np.empty_like(p)
    out[..., 0] = p[..., 0] * c + p[..., 2] * s
    out[..., 1] = p[..., 1]
    out[..., 2] = -p[..., 0] * s + p[..., 2] * c
    return out + origem


# Cada face: 4 cantos na ordem (sup-esq, sup-dir, inf-dir, inf-esq) vistos DE FORA.
# O UV segue a mesma ordem, então a textura não sai girada nem espelhada.
def cantos(from_, to, face):
    x0, y0, z0 = from_
    x1, y1, z1 = to
    if face == "north":        # -Z, visto de -Z: dir = -X, cima = +Y
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if face == "south":        # +Z
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
    if face == "east":         # +X
        return [(z0 if False else x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)]
    if face == "west":         # -X
        return [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)]
    if face == "up":           # +Y, visto de cima: cima da textura = -Z
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    if face == "down":         # -Y
        return [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]
    raise ValueError(face)


def triangula():
    j, img = carrega(BBMODEL)
    res = j["resolution"]
    tex_w, tex_h = res["width"], res["height"]
    pos, uv = [], []
    for e in j["elements"]:
        frm = np.array(e["from"], float)
        to = np.array(e["to"], float)
        rot = e.get("rotation") or [0, 0, 0]
        org = np.array(e.get("origin") or [0, 0, 0], float)
        for face, dados in (e.get("faces") or {}).items():
            if "uv" not in dados:
                continue
            u1, v1, u2, v2 = dados["uv"]
            cs = np.array(cantos(frm, to, face), float)
            if abs(rot[1]) > 1e-9:
                cs = rot_y(cs, rot[1], org)
            # UV dos 4 cantos, na mesma ordem
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            # 2 triângulos (0,1,2) e (0,2,3)
            for tri in ((0, 1, 2), (0, 2, 3)):
                for k in tri:
                    pos.append(cs[k])
                    uv.append((uvs[k][0] / tex_w, uvs[k][1] / tex_h))
    return np.array(pos), np.array(uv), img


def para_espaco_da_cabeca(pos):
    """Blockbench (Y pra cima, frente -Z) -> cabeça do MC (Y pra baixo, frente -Z)."""
    p = pos.copy()
    p[:, 1] = -p[:, 1]                    # espelha Y (pra baixo)
    lo, hi = p.min(0), p.max(0)
    # escala uniforme pela ALTURA
    esc = ALTURA_ALVO / (hi[1] - lo[1])
    centro_x = (lo[0] + hi[0]) / 2
    p[:, 0] = (p[:, 0] - centro_x) * esc
    p[:, 1] = Y_CENTRO + (p[:, 1] - (lo[1] + hi[1]) / 2) * esc
    # profundidade: comprime só o eixo Z. Tudo tem de ficar À FRENTE de -4,5,
    # senão a camada do chapéu da skin cobre (foi o bug do 5.2.1).
    z_esc = PROFUNDIDADE / (hi[2] - lo[2])
    p[:, 2] = Z_TRAS - (hi[2] - p[:, 2]) * z_esc       # nariz pra frente (-Z)
    return p


def normais(pos):
    tri = pos.reshape(-1, 3, 3)
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    ln = np.linalg.norm(n, axis=1, keepdims=True)
    n = np.where(ln > 1e-9, n / np.maximum(ln, 1e-9), np.array([0, 0, -1.0]))
    # orienta para FORA do centro do rosto
    centro = np.array([0.0, -4.0, -4.6])
    ref = tri.mean(1) - centro
    ref[:, 2] = np.minimum(ref[:, 2], -0.2)
    vira = (n * ref).sum(1) < 0
    n[vira] = -n[vira]
    return np.repeat(n, 3, axis=0)


def grava(pos, uvs, img):
    nrm = normais(pos)
    n = len(pos)
    SAIDA_BIN.parent.mkdir(parents=True, exist_ok=True)
    with SAIDA_BIN.open("wb") as f:
        f.write(b"RPTM")
        f.write(struct.pack(">ii", 1, n))
        for k in range(3):
            f.write(pos[:, k].astype(">f4").tobytes())
        for k in range(3):
            f.write(nrm[:, k].astype(">f4").tobytes())
        f.write(uvs[:, 0].astype(">f4").tobytes())
        f.write(uvs[:, 1].astype(">f4").tobytes())
    SAIDA_PNG.parent.mkdir(parents=True, exist_ok=True)
    img.save(SAIDA_PNG)
    return n


def principal():
    pos, uvs, img = triangula()
    print(f"cubos convertidos: {len(pos)//6} faces -> {len(pos)} vértices / {len(pos)//3} triângulos")
    print(f"textura: {img.size[0]}x{img.size[1]}")
    mundo = para_espaco_da_cabeca(pos)
    n = grava(mundo, uvs, img)
    lo, hi = mundo.min(0), mundo.max(0)
    print(f"gravado {SAIDA_BIN.name}: {n} vértices")
    print(f"  x {lo[0]:+.2f}..{hi[0]:+.2f}  y {lo[1]:+.2f}..{hi[1]:+.2f}  z {lo[2]:+.2f}..{hi[2]:+.2f}")
    print(f"  altura {hi[1]-lo[1]:.2f}  largura {hi[0]-lo[0]:.2f}  profundidade {hi[2]-lo[2]:.2f}")
    print(f"  à frente do chapéu (z < -4.5): {(mundo[:,2] < -4.5).mean()*100:.1f}% dos vértices")
    print(f"  textura gravada em {SAIDA_PNG}")


if __name__ == "__main__":
    principal()
