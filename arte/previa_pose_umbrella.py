#!/usr/bin/env python3
"""(5.46.0) Prévia da POSE do guarda-chuva NA MÃO — agora com a cadeia SIMULADA do jogo.

Por que este arquivo foi reescrito: nas 5.43.x e 5.44.x a prévia usava uma cadeia inventada
(braço · X(±90) · Y(180)), e por isso ela dizia "a cúpula está acima da cabeça" enquanto o
jogo mostrava o guarda-chuva nas pernas (5.44.1) e depois com o cabo no tornozelo (5.45.0).
A conta abaixo é a do Minecraft de verdade, bloco a bloco:

    mundo = T(0, 1.5, 0) · S(-1, -1, 1)                origem do jogador -> espaço do modelo
          · T(-5/16, 2/16, 0) · RX(braço)              pivô do braço direito
          · T(-1/16, 7/16, 1/16)                       translateToHand
          · RX(-90) · RY(180)                          ItemInHandLayer
          · T(1/16, 2/16, -10/16)                      posição do item na mão
          · T(translação/16) · R(rotação) · S(escala)  display do modelo

Ela foi VALIDADA nas duas fotos que o dono mandou:
  * 5.44.1/5.44.2 (rotação -100°)  -> cabo em Y=0,31 e cúpula em Y=-0,82 (nas pernas) ✔
  * 5.45.0        (rotação  80°)   -> cabo em Y=0,31 e cúpula em Y=1,44 (ombro/cabeça, longe) ✔

Uso:
  python3 arte/previa_pose_umbrella.py
"""

import json
import math
import os

from PIL import Image, ImageDraw

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELOS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "models", "item")
TEXTURAS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "textures", "item")

TOPO_DA_CABECA = 1.80      # o jogador tem 1,80 m; a cabeça vai de 1,50 a 1,80
MAO_Y = 0.75               # altura da mão com o braço em repouso


# ------------------------------------------------------------------ matrizes 4x4

def ident():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def T(x, y, z):
    m = ident()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def RX(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    m = ident()
    m[1][1], m[1][2], m[2][1], m[2][2] = c, -s, s, c
    return m


def RY(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    m = ident()
    m[0][0], m[0][2], m[2][0], m[2][2] = c, s, -s, c
    return m


def RZ(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    m = ident()
    m[0][0], m[0][1], m[1][0], m[1][1] = c, -s, s, c
    return m


def SC(x, y, z):
    m = ident()
    m[0][0], m[1][1], m[2][2] = x, y, z
    return m


def ap(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) + m[i][3] for i in range(3)]


# ------------------------------------------------------------------ a cadeia do jogo

def mao(braco_graus=5.7):
    """onde (e virado como) fica o referencial do item na mão direita, em 3ª pessoa."""
    m = T(0.0, 1.5, 0.0)
    m = mul(m, SC(-1.0, -1.0, 1.0))
    m = mul(m, T(-5.0 / 16.0, 2.0 / 16.0, 0.0))
    m = mul(m, RX(braco_graus))
    m = mul(m, T(-1.0 / 16.0, 7.0 / 16.0, 1.0 / 16.0))
    m = mul(m, RX(-90.0))
    m = mul(m, RY(180.0))
    m = mul(m, T(1.0 / 16.0, 2.0 / 16.0, -10.0 / 16.0))
    return m


def posar(vs, display, braco_graus=5.7):
    rot = display.get("rotation", [0, 0, 0])
    tr = display.get("translation", [0, 0, 0])
    es = display.get("scale", [1, 1, 1])
    m = mul(mao(braco_graus), T(tr[0] / 16.0, tr[1] / 16.0, tr[2] / 16.0))
    m = mul(m, mul(RZ(rot[2]), mul(RY(rot[1]), RX(rot[0]))))
    m = mul(m, SC(es[0], es[1], es[2]))
    return [ap(m, v) for v in vs], m


def ler_obj(caminho):
    vs, faces, tex = [], [], []
    for linha in open(caminho, encoding="utf-8", errors="ignore"):
        if linha.startswith("v "):
            p = linha.split()
            vs.append([float(p[1]), float(p[2]), float(p[3])])
        elif linha.startswith("vt "):
            p = linha.split()
            tex.append((float(p[1]), float(p[2])))
        elif linha.startswith("f "):
            face = []
            for pedaco in linha.split()[1:]:
                q = pedaco.split("/")
                vi = int(q[0]) - 1
                ti = int(q[1]) - 1 if len(q) > 1 and q[1] else -1
                face.append((vi, tex[ti] if 0 <= ti < len(tex) else (0.0, 0.0)))
            if len(face) >= 3:
                faces.append(face)
    return vs, faces


# ------------------------------------------------------------------ desenho

CINZA = (70, 70, 80)
CLARO = (118, 118, 130)
PELE = (168, 128, 100)


def corpo_de_referencia():
    """torso + cabeça + braço, em blocos (1,80 m de altura)."""
    pecas = []

    def caixa(cor, x0, x1, y0, y1, z0, z1):
        pecas.append((cor, [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]]))   # topo
        pecas.append((cor, [[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]]))   # base
        pecas.append((cor, [[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0]]))
        pecas.append((cor, [[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]]))
        pecas.append((cor, [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]]))
        pecas.append((cor, [[x1, y0, z0], [x1, y0, z1], [x1, y1, z1], [x1, y1, z0]]))

    caixa(CINZA, -0.25, 0.25, 0.75, 1.50, -0.13, 0.13)     # tronco
    caixa(PELE, -0.25, 0.25, 1.50, 1.80, -0.25, 0.25)      # cabeça
    caixa(CINZA, 0.25, 0.37, 0.75, 1.40, -0.10, 0.10)      # braço direito (lado do item)
    return pecas


def desenhar(vs, faces, tex, largura=320, altura=460, yaw=28, pitch=8, zoom=300):
    img = Image.new("RGBA", (largura, altura), (24, 22, 30, 255))
    des = ImageDraw.Draw(img)
    yc, ys = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    pc, ps = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, z = p[0] * yc + p[2] * ys, -p[0] * ys + p[2] * yc
        y, z = p[1] * pc - z * ps, p[1] * ps + z * pc
        d = max(0.1, 4.0 + z)
        return (largura / 2 + x * zoom / d, altura / 2 - (y - 0.95) * zoom / d, d)

    pintadas = []
    for face in faces:
        pts = [proj(vs[vi]) for vi, _ in face]
        u = sum(t[0] for _, t in face) / len(face)
        v = sum(t[1] for _, t in face) / len(face)
        tx = int(max(0, min(tex.width - 1, u * tex.width)))
        ty = int(max(0, min(tex.height - 1, (1.0 - v) * tex.height)))
        r, g, b, a = tex.getpixel((tx, ty))
        if a < 16:
            continue
        pintadas.append((sum(p[2] for p in pts) / len(pts), [p[:2] for p in pts], (r, g, b)))
    for cor, poly in corpo_de_referencia():
        pts = [proj(p) for p in poly]
        pintadas.append((sum(p[2] for p in pts) / len(pts), [p[:2] for p in pts], cor))
    pintadas.sort(key=lambda t: -t[0])
    for _, poly, cor in pintadas:
        des.polygon(poly, fill=cor, outline=(0, 0, 0, 80))
    return img


def medir(vs_orig, posados):
    cup = [posados[i] for i, v in enumerate(vs_orig) if v[1] > 0.60]
    c = (sum(p[0] for p in cup) / len(cup), sum(p[1] for p in cup) / len(cup),
         sum(p[2] for p in cup) / len(cup))
    cabo = min(p[1] for p in posados)
    topo = max(p[1] for p in posados)
    return c, cabo, topo


def main():
    tex = Image.open(os.path.join(TEXTURAS, "umbrella_0.png")).convert("RGBA")
    vs, faces = ler_obj(os.path.join(MODELOS, "umbrella_open.obj"))
    atual = json.load(open(os.path.join(MODELOS, "umbrella_obj_aberto_0.json")))["display"]
    atual = atual["thirdperson_righthand"]

    paineis = []
    # a pose da 5.45.0 (a que o dono viu com o cabo no tornozelo), só pra comparar
    v2, _ = posar(vs, {"rotation": [80, 0, 0], "translation": [0, 0, 0],
                       "scale": [1.1, 1.6, 1.1]})
    c, cabo, topo = medir(vs, v2)
    paineis.append(("5.45.0  rot [80,0,0] tr [0,0,0]\ncabo Y=%.2f  cupula Y=%.2f" % (cabo, c[1]),
                    desenhar(v2, faces, tex)))

    v3, _ = posar(vs, atual)
    c2, cabo2, topo2 = medir(vs, v3)
    paineis.append(("5.46.0  rot %s tr %s\ncabo Y=%.2f  cupula Y=%.2f  topo Y=%.2f"
                    % (atual["rotation"], atual["translation"], cabo2, c2[1], topo2),
                    desenhar(v3, faces, tex)))

    larg = sum(p[1].width for p in paineis)
    alt = max(p[1].height for p in paineis)
    saida = Image.new("RGBA", (larg, alt), (24, 22, 30, 255))
    des = ImageDraw.Draw(saida)
    x = 0
    for titulo, im in paineis:
        saida.paste(im, (x, 0))
        des.multiline_text((x + 8, 8), titulo, fill=(240, 230, 240))
        x += im.width
    destino = os.path.join(RAIZ, "arte", "previa_pose.png")
    saida.convert("RGB").save(destino)
    print("prévia: %s (%dx%d)" % (destino, saida.width, saida.height))
    print("cabo em Y=%.2f (a mão fica em %.2f) e cúpula em Y=%.2f (a cabeça acaba em %.2f)"
          % (cabo2, MAO_Y, c2[1], TOPO_DA_CABECA))


if __name__ == "__main__":
    main()
