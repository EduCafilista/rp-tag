#!/usr/bin/env python3
"""Gera a arte e os modelos 3D do UMBRELLA DE SOMBRA (5.37.0).

Saída:
  * textures/item/umbrella_tex_<cor>.png      — tecido + madeira + metal (16x16, 8 cores)
  * textures/entity/umbrella/dome_<cor>.png   — a cúpula que aparece sobre a cabeça
  * textures/gui/umbrella_hud_<cor>.png       — o sprite do HUD
  * models/item/umbrella_fechado_base.json    — a geometria FECHADA (cubos 3D)
  * models/item/umbrella_aberto_base.json     — a geometria ABERTA (cúpula octogonal)
  * models/item/umbrella_{fechado,aberto}_<cor>.json — só troca a textura
  * models/item/umbrella_sombra.json          — o item, com as 16 combinações
  * data/rptag/recipes/umbrella_*.json        — 8 receitas + 8 pra tingir

Uso: python3 arte/gerar_umbrella.py
"""
import json
import os

from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag")
DATA = os.path.join(RAIZ, "src", "main", "resources", "data", "rptag", "recipes")

# ------------------------------------------------------------------ paleta (8 cores)
# nome, tecido claro, tecido escuro, detalhe (ponteira)
CORES = [
    ("sangue",  (168, 26, 44), (86, 12, 22), (226, 186, 112)),
    ("noite",   (58, 40, 74), (26, 18, 34), (168, 148, 214)),
    ("juizo",   (108, 52, 160), (52, 22, 86), (198, 142, 240)),
    ("sombra",  (128, 128, 136), (62, 62, 70), (208, 208, 218)),
    ("veneno",  (60, 122, 66), (26, 60, 32), (176, 216, 126)),
    ("luar",    (52, 96, 158), (24, 44, 84), (164, 208, 246)),
    ("ouro",    (198, 156, 48), (112, 82, 20), (255, 236, 156)),
    ("rosa",    (204, 92, 128), (112, 42, 68), (255, 200, 218)),
]

# regiões da textura do item (16x16)
R_TECIDO = (0, 0, 16, 8)      # 0..16 x 0..8
R_MADEIRA = (0, 8, 8, 16)     # 0..8  x 8..16
R_METAL = (8, 8, 12, 12)      # 8..12 x 8..12
R_ESCURO = (12, 8, 16, 16)    # 12..16 x 8..16


def novo(larg=16, alt=16):
    return Image.new("RGBA", (larg, alt), (0, 0, 0, 0))


def px(img, x, y, cor, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), (cor[0], cor[1], cor[2], a))


def zera(cor, f):
    return tuple(max(0, min(255, int(c * f))) for c in cor)


# ------------------------------------------------------------------ textura do item
def textura_item(claro, escuro, detalhe):
    img = novo(16, 16)
    # tecido: listras diagonais (dá volume no guarda-chuva)
    for y in range(R_TECIDO[1], R_TECIDO[3]):
        for x in range(R_TECIDO[0], R_TECIDO[2]):
            faixa = (x + y) % 6
            cor = claro if faixa < 3 else escuro
            if faixa == 0:
                cor = zera(claro, 1.22)
            px(img, x, y, cor)
    # madeira do cabo
    for y in range(R_MADEIRA[1], R_MADEIRA[3]):
        for x in range(R_MADEIRA[0], R_MADEIRA[2]):
            cor = (96, 66, 42) if (x + y) % 5 else (120, 86, 56)
            px(img, x, y, cor)
    # metal da ponteira
    for y in range(R_METAL[1], R_METAL[3]):
        for x in range(R_METAL[0], R_METAL[2]):
            px(img, x, y, detalhe if (x + y) % 3 else zera(detalhe, 0.75))
    # tecido escuro (amarrações e varetas)
    for y in range(R_ESCURO[1], R_ESCURO[3]):
        for x in range(R_ESCURO[0], R_ESCURO[2]):
            px(img, x, y, zera(escuro, 0.8) if (x + y) % 4 else zera(escuro, 0.55))
    return img


# ------------------------------------------------------------------ textura da CÚPULA (cabeça)
def textura_cupula(claro, escuro, detalhe):
    """16x16: tecido listrado (nervuras a cada 4 px) + metal na ponta + faixa escura (varetas).

    As UVs da camada da cabeça apontam assim:
      (0,0)  ponteira de metal      (4,1) anel de dentro
      (4,4)  anel do meio           (4,7) aba de fora
      (0,12) vareta pendurada
    """
    img = novo(16, 16)
    for y in range(16):
        for x in range(16):
            faixa = (x + y) % 8
            cor = claro if faixa < 4 else escuro
            if faixa in (0, 1):
                cor = zera(cor, 1.18)
            px(img, x, y, cor)
    for x in range(0, 16, 4):                      # nervuras
        for y in range(16):
            px(img, x, y, zera(escuro, 0.72))
    for y in range(3):                             # ponteira de metal (canto 0,0)
        for x in range(3):
            px(img, x, y, detalhe)
    for y in range(12, 16):                        # faixa escura das varetas (0,12)
        for x in range(4):
            px(img, x, y, zera(escuro, 0.55))
    return img


# ------------------------------------------------------------------ sprite do HUD (48x48)
def sprite_hud(claro, escuro, detalhe):
    img = novo(48, 48)
    cxm = 23.5
    for x in range(2, 46):
        t = (x - cxm) / 22.0
        y_up = int(6 + 10.0 * t * t)
        y_low = y_up + 11 + int(5.0 * abs(t))
        for y in range(y_up, min(y_low, 47) + 1):
            cor = zera(claro, 1.22) if (y - y_up) <= 3 else (claro if (y - y_up) <= 7 else escuro)
            px(img, x, y, cor)
        px(img, x, y_low + 1, escuro if x % 12 else claro)
        px(img, x, y_low + 2, escuro if x % 12 else claro)
    for x in (4, 12, 20, 28, 36, 44):
        for y in range(6, 32):
            if img.getpixel((x, y))[3]:
                px(img, x, y, zera(escuro, 0.7))
    for y in range(20, 41):
        px(img, 23, y, (96, 66, 42))
        px(img, 24, y, (122, 88, 58))
    for i in range(6):
        px(img, 23 - i, 41 + i // 2, (96, 66, 42))
    for y in range(3):
        px(img, 23, y, detalhe)
        px(img, 24, y + 1, detalhe)
    return img


# ------------------------------------------------------------------ geometria do item
def cubo(x0, y0, z0, x1, y1, z1, uv, rot=None):
    el = {"from": [x0, y0, z0], "to": [x1, y1, z1],
          "faces": {f: {"texture": "#0", "uv": list(uv)}
                    for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        el["rotation"] = rot
    return el


DISPLAY_INVENTARIO = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}


def modelo_fechado():
    """guarda-chuva FECHADO: o pano dobrado descendo em degraus pela haste (cone invertido).

    Quatro "mangas" de tecido, da mais larga (embaixo) para a mais fina (em cima), dão a
    silhueta de guarda-chuva fechado de verdade — nada de travesseiro na haste.
    """
    el = []
    mangas = [
        # (raio, y0, y1)
        (2.7, 10.6, 12.0),   # a saia do pano (ponta de baixo)
        (2.2, 9.0, 10.6),
        (1.7, 7.4, 9.0),
        (1.25, 5.8, 7.4),    # topo do pano, colado na haste
    ]
    for raio, y0, y1 in mangas:
        el.append(cubo(8 - raio, y0, 7.7, 8 + raio, y1, 8.3, R_TECIDO))
        el.append(cubo(7.7, y0, 8 - raio, 8.3, y1, 8 + raio, R_TECIDO))
        for ang in (45, -45):
            el.append(cubo(8 - raio, y0, 7.7, 8 + raio, y1, 8.3, R_TECIDO,
                           {"origin": [8, (y0 + y1) / 2.0, 8], "axis": "y", "angle": ang,
                            "rescale": False}))
    # amarração escura no meio do pano
    el.append(cubo(5.9, 8.5, 7.7, 10.1, 9.0, 8.3, R_ESCURO))
    el.append(cubo(7.7, 8.5, 5.9, 8.3, 9.0, 10.1, R_ESCURO))
    # haste (de baixo do pano até a mão) e gancho
    el.append(cubo(7.5, 4.2, 7.5, 8.5, 16.0, 8.5, [0, 8, 8, 16]))
    el.append(cubo(5.2, 16.0, 7.6, 7.5, 16.8, 8.4, [0, 8, 8, 16]))
    el.append(cubo(4.4, 15.2, 7.6, 5.2, 16.0, 8.4, [0, 8, 8, 16]))
    # ponteira de metal no alto do pano
    el.append(cubo(7.55, 4.4, 7.55, 8.45, 5.8, 8.45, R_METAL))
    return el


def modelo_aberto():
    """guarda-chuva aberto: cúpula em DEGRAUS (3 anéis fundos), varetas, haste e gancho.

    Cada anel é uma cruz de 4 placas grossas (X, Z e as duas diagonais) — juntas leem como cone
    redondo. O degrau entre os anéis é fundo de propósito: é isso que dá a sombra de cúpula.
    """
    el = []
    aneis = [
        # (raio, y de cima, y de baixo)
        (2.4, 1.4, 2.6),    # topo (o miolo, logo abaixo da ponteira)
        (5.4, 2.8, 4.2),    # meio
        (8.3, 4.6, 6.4),    # aba (cai fundo: é o que faz parecer cúpula)
    ]
    for raio, y0, y1 in aneis:
        el.append(cubo(8 - raio, y0, 6.9, 8 + raio, y1, 9.1, R_TECIDO))
        el.append(cubo(6.9, y0, 8 - raio, 9.1, y1, 8 + raio, R_TECIDO))
        for ang in (45, -45):
            el.append(cubo(8 - raio, y0, 6.9, 8 + raio, y1, 9.1, R_TECIDO,
                           {"origin": [8, (y0 + y1) / 2.0, 8], "axis": "y", "angle": ang,
                            "rescale": False}))
    # varetas penduradas nas 8 pontas da aba
    for (x0, z0) in ((-0.3, 7.75), (15.5, 7.75), (7.75, -0.3), (7.75, 15.5),
                     (1.7, 1.7), (13.7, 1.7), (1.7, 13.7), (13.7, 13.7)):
        el.append(cubo(x0, 6.4, z0, x0 + 0.8, 9.2, z0 + 0.5, [0, 8, 4, 16]))
    # ponteira e haste
    el.append(cubo(7.55, 0.4, 7.55, 8.45, 1.6, 8.45, R_METAL))
    el.append(cubo(7.45, 5.6, 7.45, 8.55, 15.4, 8.55, [0, 8, 8, 16]))
    # gancho do cabo
    el.append(cubo(7.45, 15.4, 7.45, 8.55, 16.6, 8.55, [0, 8, 8, 16]))
    el.append(cubo(5.2, 16.0, 7.4, 7.45, 16.8, 8.6, [0, 8, 8, 16]))
    el.append(cubo(4.4, 15.2, 7.4, 5.2, 16.0, 8.6, [0, 8, 8, 16]))
    return el


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 22], "translation": [1.0, 2.6, 1.0], "scale": [0.58, 0.58, 0.58]},
    "thirdperson_lefthand": {"rotation": [0, 90, 22], "translation": [1.0, 2.6, 1.0], "scale": [0.58, 0.58, 0.58]},
    "firstperson_righthand": {"rotation": [0, -82, 12], "translation": [1.9, 2.2, 1.4], "scale": [0.62, 0.62, 0.62]},
    "firstperson_lefthand": {"rotation": [0, 82, 12], "translation": [1.9, 2.2, 1.4], "scale": [0.62, 0.62, 0.62]},
    "gui": {"rotation": [18, 224, 0], "translation": [0, 1.2, 0], "scale": [0.74, 0.74, 0.74]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.72, 0.72, 0.72]},
}


def escrever(path, dados):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(dados, indent=2) + "\n")


def main():
    tex_item = os.path.join(ASSETS, "textures", "item")
    tex_ent = os.path.join(ASSETS, "textures", "entity", "umbrella")
    tex_gui = os.path.join(ASSETS, "textures", "gui")
    mod_item = os.path.join(ASSETS, "models", "item")
    for pasta in (tex_item, tex_ent, tex_gui, mod_item):
        os.makedirs(pasta, exist_ok=True)

    # 1) texturas por cor
    for i, (nome, claro, escuro, detalhe) in enumerate(CORES):
        textura_item(claro, escuro, detalhe).save(
            os.path.join(tex_item, "umbrella_tex_%d.png" % i))
        textura_cupula(claro, escuro, detalhe).save(
            os.path.join(tex_ent, "dome_%d.png" % i))
        sprite_hud(claro, escuro, detalhe).save(
            os.path.join(tex_gui, "umbrella_hud_%d.png" % i))

    # 2) geometrias base (uma fechada, uma aberta) — a cor entra pela textura
    for estado, elementos in (("fechado", modelo_fechado()), ("aberto", modelo_aberto())):
        escrever(os.path.join(mod_item, "umbrella_%s_base.json" % estado), {
            "parent": "minecraft:item/handheld",
            "textures": {"0": "rptag:item/umbrella_tex_0"},
            "elements": elementos,
            "display": DISPLAY,
        })
        for i in range(len(CORES)):
            escrever(os.path.join(mod_item, "umbrella_%s_%d.json" % (estado, i)), {
                "parent": "rptag:item/umbrella_%s_base" % estado,
                "textures": {"0": "rptag:item/umbrella_tex_%d" % i},
            })

    # 3) o item: fechado por padrão, e cada cor x aberto/fechado
    overrides = []
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i, "using": 0},
                          "model": "rptag:item/umbrella_fechado_%d" % i})
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i, "using": 1},
                          "model": "rptag:item/umbrella_aberto_%d" % i})
    escrever(os.path.join(mod_item, "umbrella_sombra.json"), {
        "parent": "rptag:item/umbrella_fechado_base",
        "textures": {"0": "rptag:item/umbrella_tex_0"},
        "overrides": overrides,
    })

    # 4) receitas
    ingredientes = [
        ("minecraft:red_wool", "minecraft:black_dye"),
        ("minecraft:black_wool", "minecraft:coal"),
        ("minecraft:purple_wool", "minecraft:crying_obsidian"),
        ("minecraft:gray_wool", "minecraft:gunpowder"),
        ("minecraft:green_wool", "minecraft:spider_eye"),
        ("minecraft:blue_wool", "minecraft:lapis_lazuli"),
        ("minecraft:yellow_wool", "minecraft:gold_ingot"),
        ("minecraft:pink_wool", "minecraft:red_dye"),
    ]
    for i, (la, cor) in enumerate(ingredientes):
        escrever(os.path.join(DATA, "umbrella_%d.json" % i), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": ["SSS", "SWS", " T "],
            "key": {"S": {"item": "minecraft:string"},
                    "W": {"item": la},
                    "T": {"item": "minecraft:stick"}},
            "result": {"id": "rptag:umbrella_sombra", "count": 1,
                       "components": {"minecraft:custom_model_data": i}},
        })
        escrever(os.path.join(DATA, "umbrella_tingir_%d.json" % i), {
            "type": "minecraft:crafting_shapeless",
            "category": "equipment",
            "ingredients": [{"item": "rptag:umbrella_sombra"}, {"item": cor}],
            "result": {"id": "rptag:umbrella_sombra", "count": 1,
                       "components": {"minecraft:custom_model_data": i}},
        })

    print("umbrella: %d cores | texturas de item/cupula/HUD | 2 geometrias 3D | %d receitas"
          % (len(CORES), len(CORES) * 2))


if __name__ == "__main__":
    main()
