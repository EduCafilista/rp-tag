#!/usr/bin/env python3
"""Renderiza os modelos do UMBRELLA (5.37.0) fora do jogo, pra conferir a forma antes de publicar.

Lê os JSON de modelo (cubos + rotação + display) e a textura de cada cor, projeta em perspectiva
simples, pinta com painter's algorithm e monta uma folha com 4 vistas:

  1. FECHADO na mão (3ª pessoa)
  2. ABERTO na mão (3ª pessoa)
  3. CÚPULA aberta sobre a cabeça do jogador (3ª pessoa, corpo de referência)
  4. CÚPULA vista de frente (pra ver o anel de setores)

Uso: python3 arte/render_umbrella_offline.py [cor]
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag")
MODELOS = os.path.join(ASSETS, "models", "item")
TEXTURAS = os.path.join(ASSETS, "textures", "item")


# ------------------------------------------------------------------ geometria
def caixa(x0, y0, z0, x1, y1, z1, uv, rot=None):
    """um elemento de modelo (Blockbench): from/to, uv por face e rotação opcional."""
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "uv": uv, "rotation": rot}


def aplicar_display(pontos, display):
    """aplica translação/escala/rotação do display do item (ordem do Minecraft: T R S)."""
    tr = [t / 16.0 for t in display.get("translation", [0, 0, 0])]
    ro = display.get("rotation", [0, 0, 0])
    es = display.get("scale", [1, 1, 1])
    rx, ry, rz = [math.radians(a) for a in ro]
    saida = []
    for (x, y, z) in pontos:
        x, y, z = x * es[0], y * es[1], z * es[2]
        # rotação Z, depois Y, depois X (como o Minecraft faz)
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        saida.append((x + tr[0], y + tr[1], z + tr[2]))
    return saida


def cantos(el):
    a, b = el["from"], el["to"]
    pts = []
    for x in (a[0], b[0]):
        for y in (a[1], b[1]):
            for z in (a[2], b[2]):
                pts.append([x, y, z])
    rot = el.get("rotation")
    if rot:
        origem = rot.get("origin", [8, 8, 8])
        ang = math.radians(rot.get("angle", 0))
        eixo = rot.get("axis", "y")
        novos = []
        for p in pts:
            dx, dy, dz = p[0] - origem[0], p[1] - origem[1], p[2] - origem[2]
            if eixo == "y":
                dx, dz = dx * math.cos(ang) + dz * math.sin(ang), -dx * math.sin(ang) + dz * math.cos(ang)
            elif eixo == "x":
                dy, dz = dy * math.cos(ang) - dz * math.sin(ang), dy * math.sin(ang) + dz * math.cos(ang)
            else:
                dx, dy = dx * math.cos(ang) - dy * math.sin(ang), dx * math.sin(ang) + dy * math.cos(ang)
            novos.append((dx + origem[0], dy + origem[1], dz + origem[2]))
        pts = novos
    # modelo de item: 16 unidades = 1 bloco, e o "para cima" é -Y
    return [(p[0] / 16.0, -p[1] / 16.0, p[2] / 16.0) for p in pts]


FACES = [  # (índices dos cantos, normal, nome da face)
    ((0, 1, 3, 2), (0, 0, -1), "north"),
    ((4, 6, 7, 5), (0, 0, 1), "south"),
    ((0, 4, 5, 1), (-1, 0, 0), "west"),
    ((2, 3, 7, 6), (1, 0, 0), "east"),
    ((0, 2, 6, 4), (0, -1, 0), "up"),
    ((1, 5, 7, 3), (0, 1, 0), "down"),
]


def cores_da_textura(caminho):
    if not os.path.exists(caminho):
        return {}
    tex = Image.open(caminho).convert("RGBA")
    return tex


def amostra(tex, u, v):
    x = int(max(0, min(tex.width - 1, u)))
    y = int(max(0, min(tex.height - 1, v)))
    r, g, b, a = tex.getpixel((x, y))
    if a < 40:
        return None
    return (r, g, b)


# ------------------------------------------------------------------ render
def renderizar(elements, display, tex, largura=260, altura=260, yaw=-35, pitch=18, zoom=620,
               extras=(), centrar=False, regua=False):
    img = Image.new("RGBA", (largura, altura), (18, 16, 24, 255))
    des = ImageDraw.Draw(img)
    cy, sy = math.radians(yaw), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    yaw_c, yaw_s = math.cos(math.radians(yaw)), sy

    if regua:
        # um CUBO DE 1 BLOCO como régua (contorno), pra ver o tamanho do item sem chutar
        extras = tuple(extras) + tuple(
            caixa(x0, y0, z0, x0 + 0.4, y0 + 16.0, z0 + 0.4, [0, 0, 0, 0])
            for (x0, y0, z0) in ((-8, -8, -8), (7.6, -8, -8), (-8, -8, 7.6), (7.6, -8, 7.6)))
    todas = [(el, False) for el in elements] + [(el, True) for el in extras]
    # (5.40.0) o jogo, depois do display, faz translate(-0,5 -0,5 -0,5) pra pôr o CENTRO do
    # modelo na mão (é o `ItemRenderer`). Aqui, pra caber no quadro, a câmera mira o centro do
    # que sobrou disso (`centrar`) — o tamanho RELATIVO continua o mesmo.
    desloc = [0.0, 0.0, 0.0]
    if centrar:
        pts_todos = []
        for el in elements:
            pts_todos += [(x - 0.5, y - 0.5, z - 0.5)
                          for (x, y, z) in aplicar_display(cantos(el), display)]
        if pts_todos:
            desloc = [(min(p[i] for p in pts_todos) + max(p[i] for p in pts_todos)) / 2.0
                      for i in range(3)]
    faces = []
    for el, referencia in todas:
        if referencia:
            pts3 = [(x - desloc[0], y - desloc[1], z - desloc[2]) for (x, y, z) in cantos(el)]
        else:
            pts3 = [(x - 0.5 - desloc[0], y - 0.5 - desloc[1], z - 0.5 - desloc[2])
                    for (x, y, z) in aplicar_display(cantos(el), display)]
        vis = []
        for (x, y, z) in pts3:
            x1 = x * yaw_c + z * yaw_s
            z1 = -x * yaw_s + z * yaw_c
            y1 = y * cp - z1 * sp
            z2 = y * sp + z1 * cp
            d = 3.0 + z2
            if d < 0.05:
                d = 0.05
            vis.append((largura / 2 + x1 * zoom / d, altura / 2 - y1 * zoom / d, d))
        for idx, normal, nome in FACES:
            nx, ny, nz = normal
            nx2 = nx * yaw_c + nz * yaw_s
            nz2 = -nx * yaw_s + nz * yaw_c
            ny2 = ny * cp - nz2 * sp
            if ny2 * 0.25 + nz2 < 0.05:
                continue  # face virada pra trás
            poly = [vis[i][:2] for i in idx]
            prof = sum(vis[i][2] for i in idx) / 4.0
            uv = el.get("uv")
            faces_json = el.get("faces") or {}
            if nome in faces_json and isinstance(faces_json[nome], dict):
                uv = faces_json[nome].get("uv", uv)
            cor = None
            if isinstance(uv, list) and len(uv) == 4 and tex is not None:
                cor = amostra(tex, (uv[0] + uv[2]) / 2, (uv[1] + uv[3]) / 2)
            if cor is None:
                cor = (110, 110, 120)
            if referencia:
                cor = tuple(int(c * 0.55) for c in cor)
            luz = 0.62 + 0.38 * max(0.0, ny2 * 0.6 + nz2 * 0.7)
            cor = tuple(min(255, int(c * luz)) for c in cor)
            faces.append((prof, poly, cor))
    faces.sort(key=lambda f: -f[0])
    for _, poly, cor in faces:
        des.polygon(poly, fill=cor, outline=(0, 0, 0, 90))
    return img


def carregar(nome):
    with open(os.path.join(MODELOS, nome), encoding="utf-8") as fp:
        j = json.load(fp)
    if "elements" not in j:
        pai = j.get("parent", "").split("/")[-1]
        with open(os.path.join(MODELOS, pai + ".json"), encoding="utf-8") as fp:
            base = json.load(fp)
        base.setdefault("textures", {}).update(j.get("textures", {}))
        base["display"] = j.get("display", base.get("display", {}))
        return base
    return j


def cupula_do_json():
    """monta a cúpula a partir de arte/dome_geometria.json (a MESMA geometria do Java).

    Os cubos vêm em pixels de modelo; o Java os repete em `setores` setores girados em Y e
    depois sobe a cúpula `translate_y_blocos` acima do pivô da cabeça. Aqui é igual, com a
    subida já embutida em pixels (o referencial desta prévia vira Y pra cima no `cantos`).
    """
    caminho = os.path.join(RAIZ, "arte", "dome_geometria.json")
    with open(caminho, encoding="utf-8") as fp:
        g = json.load(fp)
    setores = int(g.get("setores", 16))
    ty = float(g.get("translate_y_blocos", 0.0))
    cubos = []
    for setor in range(setores):
        ang = 360.0 * setor / setores
        for c in g["cubos"]:
            cubos.append(caixa(c["x"], c["y"] - ty * 16.0, c["z"],
                               c["x"] + c["w"], c["y"] + c["h"] - ty * 16.0, c["z"] + c["d"],
                               [0, 0, 8, 8],
                               {"origin": [0, 0, 0], "axis": "y", "angle": ang}))
    return cubos, ty


def main():
    cor = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    tex = None
    for candidato in ("umbrella_tex_%d.png" % cor, "umbrella_aberto_%d.png" % cor):
        caminho = os.path.join(TEXTURAS, candidato)
        if os.path.exists(caminho):
            tex = Image.open(caminho).convert("RGBA")
            break
    fechado = carregar("umbrella_fechado_%d.json" % cor)
    aberto = carregar("umbrella_aberto_%d.json" % cor)

    # (5.40.0) a cabeça do jogador (referência) e a cúpula LIDA de arte/dome_geometria.json —
    # a mesma fonte que o Java usa. Antes estava copiada à mão aqui e divergia do jogo.
    cabeca = caixa(-4, -8, -4, 4, 0, 4, [0, 0, 0, 0])
    cupula, ty = cupula_do_json()

    paineis = [
        ("FECHADO na mao (3a pessoa)", renderizar(
            fechado["elements"], fechado.get("display", {}).get("thirdperson_righthand", {}),
            tex, centrar=True, regua=True, yaw=-38, pitch=14, zoom=430)),
        ("ABERTO na mao (3a pessoa)", renderizar(
            aberto["elements"], aberto.get("display", {}).get("thirdperson_righthand", {}),
            tex, centrar=True, regua=True, yaw=-38, pitch=14, zoom=430)),
        ("CUPULA sobre a cabeca", renderizar([], {}, tex, yaw=-30, pitch=12, zoom=300,
                                             extras=cupula)),
        ("CUPULA de frente", renderizar([], {}, tex, yaw=0, pitch=48, zoom=300,
                                        extras=cupula)),
    ]
    folha = Image.new("RGBA", (260 * len(paineis), 290), (18, 16, 24, 255))
    des = ImageDraw.Draw(folha)
    for i, (titulo, im) in enumerate(paineis):
        folha.paste(im, (i * 260, 30))
        des.text((i * 260 + 8, 8), titulo, fill=(230, 220, 255))
    saida = os.path.join(RAIZ, "arte", "previa_umbrella.png")
    folha.save(saida)
    print("previa:", saida, folha.size)


if __name__ == "__main__":
    main()
