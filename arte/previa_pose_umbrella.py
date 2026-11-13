#!/usr/bin/env python3
"""(5.43.1) Prévia da POSE do guarda-chuva na mão, com o corpo do jogador de referência.

O dono reclamou duas vezes da pose ("está bugado no meu peito", "está indo para baixo").
A prévia antiga desenhava o item sozinho, no vácuo — e é impossível julgar pose sem o corpo
do lado. Esta desenha o torso e a cabeça (blocos de 1,75 m) junto, então dá para ver de
relance se a cúpula está ACIMA DA CABEÇA (o pedido: "como se o player estivesse se escondendo
do sol") ou caindo na altura do peito.

A conta da pose é a mesma do jogo:
    mundo = braço(repouso) · X(-90) · Y(180) · [rotação do display]
e a translação do display entra em 1/16 de bloco.

Uso:
  python3 arte/previa_pose_umbrella.py            # a pose atual dos modelos + a antiga (defeito)
  python3 arte/previa_pose_umbrella.py --otimizar # procura a melhor translação e imprime
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELOS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "models", "item")
TEXTURAS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "textures", "item")

# a cabeça do jogador: 8x8x8 px centrada na coluna, do pescoço pra cima -> topo em 0,5 bloco
TOPO_DA_CABECA = 0.5
# onde queremos o CENTRO da cúpula: um pouco acima do topo da cabeça
ALVO_DA_CUPULA = 0.86


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


def _rx(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def _ry(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def _rz(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _ap(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def posar(vs, display, braco_x=-75.0):
    """aplica a cadeia REAL do item na mão (3ª pessoa) e devolve os vértices no mundo."""
    rot = display.get("rotation", [0, 0, 0])
    tr = display.get("translation", [0, 0, 0])
    es = display.get("scale", [1, 1, 1])
    base = _mul(_rx(braco_x), _mul(_rx(-90.0), _ry(180.0)))
    m = _mul(base, _mul(_rx(rot[0]), _mul(_ry(rot[1]), _rz(rot[2]))))
    out = []
    for v in vs:
        p = _ap(m, [v[0] * es[0], v[1] * es[1], v[2] * es[2]])
        out.append([p[0] + tr[0] / 16.0, p[1] + tr[1] / 16.0, p[2] + tr[2] / 16.0])
    return out


def centro_da_cupula(vs_orig, vs_posado, corte=0.60):
    """o centro da CÚPULA depois da pose (a cúpula é a parte de cima do .obj original)."""
    dentro = [vs_posado[i] for i, v in enumerate(vs_orig) if v[1] > corte]
    if not dentro:
        return None
    n = len(dentro)
    return (sum(p[0] for p in dentro) / n, sum(p[1] for p in dentro) / n,
            sum(p[2] for p in dentro) / n)


def corpo_de_referencia():
    """torso + cabeça de um jogador, em blocos — só pra dar escala ao desenho."""
    pecas = []

    def quad(cor, pts):
        pecas.append((cor, pts))

    cinza = (78, 78, 88)
    claro = (100, 100, 112)
    # tronco: 0,5 de largura, 0,75 de altura, 0,25 de profundidade (centrado em x=0)
    for dz in (-0.13, 0.13):
        quad(cinza, [[-0.25, 0.0, dz], [0.25, 0.0, dz], [0.25, -0.75, dz], [-0.25, -0.75, dz]])
    for dx in (-0.25, 0.25):
        quad(cinza, [[dx, 0.0, -0.13], [dx, 0.0, 0.13], [dx, -0.75, 0.13], [dx, -0.75, -0.13]])
    # cabeça: 0,5 de lado, topo em 0,5
    for dz in (-0.25, 0.25):
        quad(claro, [[-0.25, TOPO_DA_CABECA, dz], [0.25, TOPO_DA_CABECA, dz],
                     [0.25, 0.0, dz], [-0.25, 0.0, dz]])
    for dx in (-0.25, 0.25):
        quad(claro, [[dx, TOPO_DA_CABECA, -0.25], [dx, TOPO_DA_CABECA, 0.25],
                     [dx, 0.0, 0.25], [dx, 0.0, -0.25]])
    return pecas


def desenhar(vs, faces, tex, largura=300, altura=400, yaw=-25, pitch=6, zoom=330):
    img = Image.new("RGBA", (largura, altura), (22, 20, 28, 255))
    des = ImageDraw.Draw(img)
    yc, ys = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    pc, ps = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, z = p[0] * yc + p[2] * ys, -p[0] * ys + p[2] * yc
        y, z = p[1] * pc - z * ps, p[1] * ps + z * pc
        d = 3.0 + z
        d = max(0.1, d)
        return (largura / 2 + x * zoom / d, altura / 2 - y * zoom / d, d)

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
        des.polygon(poly, fill=cor, outline=(0, 0, 0, 70))
    return img


def carregar(cor=0):
    tex = Image.open(os.path.join(TEXTURAS, "umbrella_%d.png" % cor)).convert("RGBA")
    vs, faces = ler_obj(os.path.join(MODELOS, "umbrella_open.obj"))
    with open(os.path.join(MODELOS, "umbrella_obj_aberto_%d.json" % cor), encoding="utf-8") as fp:
        display = json.load(fp)["display"]["thirdperson_righthand"]
    return vs, faces, tex, display


def otimizar(vs):
    """procura a pose que põe o CENTRO DA CÚPULA acima da cabeça e centralizado no corpo."""
    melhor = None
    for rot in ([-180, -90, -80], [-180, -90, -90], [-180, -90, -100]):
        for tx in range(-8, 9, 2):
            for ty in range(0, 24, 2):
                for tz in range(-8, 9, 2):
                    v2 = posar(vs, {"rotation": rot, "translation": [tx, ty, tz],
                                    "scale": [0.72, 0.72, 0.72]})
                    c = centro_da_cupula(vs, v2)
                    nota = -abs(c[0]) * 2.5 - abs(c[1] - ALVO_DA_CUPULA) * 3.0 - abs(c[2]) * 1.5
                    if melhor is None or nota > melhor[0]:
                        melhor = (nota, rot, [tx, ty, tz], c)
    return melhor


def main():
    vs, faces, tex, display = carregar(0)
    if "--otimizar" in sys.argv:
        nota, rot, tr, c = otimizar(vs)
        print("melhor: rot=%s tr=%s -> cupula em (%.2f, %.2f, %.2f)  nota %.2f"
              % (rot, tr, c[0], c[1], c[2], nota))
        return

    paineis = []
    # (1) a pose que está nos modelos hoje
    v2 = posar(vs, display)
    c = centro_da_cupula(vs, v2)
    paineis.append(("POSE ATUAL (rot %s)\ncupula em Y=%.2f" % (display["rotation"], c[1]),
                    desenhar(v2, faces, tex)))
    # (2) a pose quebrada da 5.43.0, pra comparação
    quebrada = {"rotation": [-180, -90, -80], "translation": [0, 0.5, 0],
                "scale": [0.72, 0.72, 0.72]}
    v3 = posar(vs, quebrada)
    c3 = centro_da_cupula(vs, v3)
    paineis.append(("o defeito da 5.43.0\ncupula em Y=%.2f" % c3[1],
                    desenhar(v3, faces, tex)))

    largura, altura = 300, 400
    folha = Image.new("RGBA", (largura * len(paineis), altura + 26), (22, 20, 28, 255))
    des = ImageDraw.Draw(folha)
    for i, (titulo, im) in enumerate(paineis):
        folha.paste(im, (i * largura, 24))
        for j, linha in enumerate(titulo.split("\n")):
            des.text((i * largura + 8, 6 + j * 11), linha, fill=(235, 225, 255))
    saida = os.path.join(RAIZ, "arte", "previa_pose.png")
    folha.save(saida)
    print("previa da pose:", saida, folha.size)
    print("centro da cupula agora: Y=%.2f (a cabeca termina em %.2f)" % (c[1], TOPO_DA_CABECA))


if __name__ == "__main__":
    main()
