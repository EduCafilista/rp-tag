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
import math
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



# ------------------------------------------------------------------ ícone 2D (rede de segurança)
def icone_item(claro, escuro, detalhe):
    """Ícone 16x16 do guarda-chuva ABERTO, usado como modelo BASE do item.

    (5.37.3) O dono: "nao aparece nada na minha mao... nao tem modelo nem nada". O modelo 3D
    ficou nas variantes; a BASE é este ícone (item/generated), que é o caminho mais simples do
    Minecraft e SEMPRE aparece. Se algo do 3D falhar, ainda dá pra ver o guarda-chuva.
    """
    img = novo(16, 16)
    cxm = 7.5
    for x in range(16):
        t = (x - cxm) / 7.5
        y_up = int(2 + 3.4 * t * t)
        y_low = y_up + 3 + int(1.4 * abs(t))
        for y in range(y_up, min(y_low, 15) + 1):
            cor = zera(claro, 1.2) if (y - y_up) <= 1 else (claro if (y - y_up) <= 2 else escuro)
            px(img, x, y, cor)
        px(img, x, y_low + 1, escuro if x % 4 else claro)
    for x in (1, 4, 8, 11, 14):
        for y in range(2, 8):
            if img.getpixel((x, y))[3]:
                px(img, x, y, zera(escuro, 0.75))
    for y in range(6, 14):
        px(img, 7, y, (96, 66, 42))
        px(img, 8, y, (128, 92, 60))
    for i in range(3):
        px(img, 7 - i, 14 + (i // 2), (96, 66, 42))
    px(img, 7, 0, detalhe)
    px(img, 8, 1, detalhe)
    return img


# ------------------------------------------------------------------ geometria do item
def cubo(x0, y0, z0, x1, y1, z1, uv, rot=None):
    el = {"from": [x0, y0, z0], "to": [x1, y1, z1],
          "faces": {f: {"texture": "#layer0", "uv": list(uv)}
                    for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        el["rotation"] = rot
    return el


def disco(raio, y0, y1, uv, centro=8.0):
    """um DISCO de tecido (um "anel" cheio) feito de 5 placas cruzadas.

    <b>Por que 5 e nao 16 setores?</b> Modelo de item no Minecraft só aceita rotação de
    <b>-45° a 45°</b> (`rotation.angle`). Girar em 90°/135° faz o jogo REJEITAR o modelo
    inteiro — e o item volta a aparecer como o quadriculado rosa/preto de "faltando", que é
    exatamente o bug que o dono reportou. Então: 5 placas de diâmetro inteiro, giradas em
    -45, -22,5, 0, 22,5 e 45 graus. Cada placa dá dois braços (θ e θ+180°), então o disco
    fecha em 20 braços a cada 18° — sem furo no meio e sem ângulo ilegal.
    """
    el = []
    # espessura da placa = a corda do braço (senão aparecem frestas entre os braços)
    esp = max(1.0, min(3.0, 2.0 * raio * math.sin(math.radians(9.0))))
    for ang in (-45, -22.5, 0, 22.5, 45):
        el.append(cubo(centro - raio, y0, centro - esp / 2.0,
                       centro + raio, y1, centro + esp / 2.0, uv,
                       {"origin": [centro, (y0 + y1) / 2.0, centro], "axis": "y", "angle": ang}))
    return el


def haste_e_cabo(uv_madeira, y_cabo, y_alto):
    """a haste (de baixo pra cima) e o cabo em gancho — iguais no fechado e no aberto."""
    el = [cubo(7.55, y_cabo + 1.2, 7.55, 8.45, y_alto, 8.45, uv_madeira)]
    # cabo: desce, vira pra esquerda e sobe na ponta (o gancho)
    el.append(cubo(7.55, y_cabo + 0.35, 7.55, 8.45, y_cabo + 1.5, 8.45, uv_madeira))
    el.append(cubo(5.60, y_cabo + 0.35, 7.55, 7.60, y_cabo + 1.1, 8.45, uv_madeira))
    el.append(cubo(5.60, y_cabo + 0.35, 7.55, 6.40, y_cabo + 1.7, 8.45, uv_madeira))
    return el


def modelo_fechado():
    """guarda-chuva FECHADO: cabo com gancho EMBAIXO, pano enrolado subindo, ponteira no alto.

    (5.40.0) Antes o modelo estava DE CABEÇA PRA BAIXO (gancho no alto, ponteira no pé) — na
    mão saía um guarda-chuva invertido. Agora segue o padrão do `item/handheld` do vanilla:
    cabo embaixo, corpo subindo, ponta em cima. O pano é feito de 5 anéis radiais que
    ENGORDAM pra cima, e o modelo fica centrado em (8, 8) pra aparecer no lugar certo na mão.
    """
    el = []
    for raio1, y0, y1, uv in (
            (1.5, 13.6, 14.4, R_ESCURO),   # topo do pano (junto da ponteira)
            (2.0, 12.1, 13.6, R_TECIDO),
            (2.4, 10.4, 12.1, R_TECIDO),
            (2.7, 8.7, 10.4, R_TECIDO),
            (3.0, 7.0, 8.7, R_TECIDO)):     # a saia do pano (bem embaixo)
        el += disco(raio1, y0, y1, uv)
    # a fita escura que amarra o pano
    el += disco(3.1, 8.5, 9.3, R_ESCURO)
    el += haste_e_cabo(R_MADEIRA, y_cabo=0.6, y_alto=14.2)
    # ponteira de metal no alto
    el.append(cubo(7.55, 14.4, 7.55, 8.45, 15.9, 8.45, R_METAL))
    return el


def modelo_aberto():
    """guarda-chuva ABERTO: cúpula larga de 3 anéis no ALTO, haste descendo, gancho no pé.

    (5.40.0) O mesmo defeito do fechado, e pior: a cúpula estava em y 0,4..6,4 (embaixo) e a
    haste subia até 16,8 — saía de ponta-cabeça. E as 3 placas em cruz por anel liam como
    tábuas soltas; agora são anéis radiais de 8 setores, iguais aos da cúpula da cabeça, então
    o guarda-chuva fecha e aparece REDONDO. A cúpula tem 16px de ponta a ponta (1 bloco).
    """
    el = []
    for raio1, y0, y1, uv in (
            (3.0, 13.2, 14.4, R_TECIDO),    # miolo (logo abaixo da ponteira)
            (5.8, 11.4, 13.2, R_TECIDO),    # meio
            (8.3, 9.4, 11.4, R_TECIDO)):    # aba, caindo
        el += disco(raio1, y0, y1, uv)
    # as 8 pontas dos varões, penduradas na borda da aba
    for k in range(8):
        a = math.radians(45.0 * k)
        cx, cz = 8.0 + 7.6 * math.cos(a), 8.0 + 7.6 * math.sin(a)
        el.append(cubo(cx - 0.4, 7.2, cz - 0.4, cx + 0.4, 9.6, cz + 0.4, R_ESCURO))
    el += haste_e_cabo(R_MADEIRA, y_cabo=0.6, y_alto=13.4)
    # ponteira de metal no alto da cúpula
    el.append(cubo(7.55, 14.4, 7.55, 8.45, 16.1, 8.45, R_METAL))
    return el


# (5.40.0) O TAMANHO NA MÃO. Herdar o `display` do `block/block` (scale 0,375) deixava o
# guarda-chuva MINÚSCULO na mão — foi a reclamação do dono no print da 5.39.0. Estes números
# são os do `item/handheld` do vanilla (o padrão de TODO item na mão: espada, picareta,
# haste) com a escala aumentada, porque o guarda-chuva tem 1 bloco de comprimento e precisa
# aparecer inteiro. O aberto é mais largo (cúpula de 1 bloco), então fica um pouco menor na
# primeira pessoa pra não tampar a tela toda.
def _display(tp, fp, mao_y=4, mao_z=0.5, gui=1.5):
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, mao_y, mao_z],
                                  "scale": [tp, tp, tp]},
        "thirdperson_lefthand": {"rotation": [0, 90, 55], "translation": [0, mao_y, mao_z],
                                 "scale": [tp, tp, tp]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13],
                                  "scale": [fp, fp, fp]},
        "firstperson_lefthand": {"rotation": [0, 90, 25], "translation": [1.13, 3.2, 1.13],
                                 "scale": [fp, fp, fp]},
        "gui": {"rotation": [20, 224, 0], "translation": [0, gui, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    }


DISPLAY_FECHADO = _display(tp=1.25, fp=1.0)
DISPLAY_ABERTO = _display(tp=1.05, fp=0.70)



# ------------------------------------------------------------------ cúpula sobre a cabeça
# (5.40.0) A GEOMETRIA DA CÚPULA em UM lugar só. Era o ponto onde tudo divergia: o Java
# (client/CamadaUmbrella.java) desenhava uma coisa, a prévia offline desenhava outra e a
# cúpula saía 8,9 BLOCOS abaixo do jogador (unidade trocada: o Java estava em pixels, o
# espaço da camada é em BLOCOS). Este arquivo é gravado em arte/dome_geometria.json e o
# check.py compara os números com os `addBox(...)` do Java — se divergir, o build falha.
#
# Cada cubo é um "raio" da cúpula: vai do centro (x=0) para fora em +X, fino em Z, e é
# repetido em 16 setores girados em Y (22,5° cada) — é isso que faz a cúpula ser REDONDA.
# A profundidade em Z cresce com o raio para os setores se encostarem (senão o guarda-chuva
# vira uma estrela de varões soltos).
DOME_SETORES = 16
DOME_TRANSLATE_Y = 0.72     # em BLOCOS, acima do pivô da cabeça (o topo do chapéu fica a 0,53)
DOME_CUBOS = [
    # (texOffs x, texOffs y), x, y, z, largura, altura, profundidade   [pixels do modelo]
    ((0, 0),   0.0, -0.6, -1.4, 2.8, 1.0, 2.8),   # tampa do miolo (fecha o buraco do centro)
    ((4, 3),   2.6, -2.0, -1.6, 3.8, 1.0, 3.2),   # anel de dentro
    ((4, 6),   5.8, -3.6, -2.2, 3.8, 1.0, 4.4),   # anel do meio
    ((4, 9),   8.8, -5.2, -2.9, 3.8, 1.0, 5.8),   # aba (a borda que cai)
    ((0, 12), 11.4, -8.4, -0.4, 0.8, 3.2, 0.8),   # vareta pendurada na ponta da aba
    # ponteira de metal no alto (não gira: fica no eixo)
    ((4, 12),  -0.6,  0.4, -0.6, 1.2, 2.4, 1.2),
]


def escrever_dome():
    """grava a geometria da cúpula para a prévia offline e para a prova do check.py."""
    dados = {
        "setores": DOME_SETORES,
        "translate_y_blocos": DOME_TRANSLATE_Y,
        "cubos": [{"texOffs": list(t), "x": x, "y": y, "z": z, "w": w, "h": h, "d": d}
                  for (t, x, y, z, w, h, d) in DOME_CUBOS],
    }
    escrever(os.path.join(RAIZ, "arte", "dome_geometria.json"), dados)


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
        icone_item(claro, escuro, detalhe).save(
            os.path.join(tex_item, "umbrella_icon_%d.png" % i))
    # (5.39.0) O ARQUIVO QUE FALTAVA: `umbrella_sombra.json` apontava pra
    # `rptag:item/umbrella_icon` (sem numero) e ele nunca era gerado -> o jogo mostrava a
    # TEXTURA ROSA/PRETA de "faltando" na mao. Agora ele existe (tom 0 = sangue).
    icone_item(CORES[0][1], CORES[0][2], CORES[0][3]).save(
        os.path.join(tex_item, "umbrella_icon.png"))

    # 2) geometrias base (uma fechada, uma aberta) — a cor entra pela textura
    for estado, elementos, disp in (("fechado", modelo_fechado(), DISPLAY_FECHADO),
                                    ("aberto", modelo_aberto(), DISPLAY_ABERTO)):
        # (5.37.3) parent "block/block": é o caminho canônico do vanilla pra modelo COM CUBOS
        # (todo item 3D do jogo — tocha, placa, cerca — vem daí). Antes era "item/handheld",
        # que herda de item/generated -> builtin/generated (o gerador de quad 2D) e podia
        # simplesmente ignorar os elementos.
        # (5.40.0) o DISPLAY VOLTOU, agora com numeros de verdade: os do `item/handheld`
        # do vanilla com a escala maior (ver DISPLAY_FECHADO/DISPLAY_ABERTO logo acima).
        # Na 5.39.0 eu tinha tirado o display e deixado herdar o do `block/block`
        # (scale 0,375) -> o guarda-chuva saiu MINUSCULO na mao (print do dono).
        escrever(os.path.join(mod_item, "umbrella_%s_base.json" % estado), {
            "parent": "minecraft:block/block",
            "textures": {"layer0": "rptag:item/umbrella_tex_0",
                         "particle": "rptag:item/umbrella_tex_0"},
            "elements": elementos,
            "display": disp,
        })
        for i in range(len(CORES)):
            escrever(os.path.join(mod_item, "umbrella_%s_%d.json" % (estado, i)), {
                "parent": "rptag:item/umbrella_%s_base" % estado,
                "textures": {"layer0": "rptag:item/umbrella_tex_%d" % i,
                             "particle": "rptag:item/umbrella_tex_%d" % i},
            })

    # 3) o item: fechado por padrão, e cada cor x aberto/fechado
    overrides = []
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i, "rptag:aberto": 0},
                          "model": "rptag:item/umbrella_fechado_%d" % i})
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i, "rptag:aberto": 1},
                          "model": "rptag:item/umbrella_aberto_%d" % i})
    # (5.39.0) a base é o MODELO 3D fechado (textura que existe de verdade). Antes a base era
    # um `item/generated` apontando pra um arquivo que eu nunca gerei: o jogo desenhava o quad
    # com a textura de "faltando" (o quadriculado rosa/preto do print do dono).
    escrever(os.path.join(mod_item, "umbrella_sombra.json"), {
        "parent": "rptag:item/umbrella_fechado_base",
        "textures": {"layer0": "rptag:item/umbrella_tex_0",
                     "particle": "rptag:item/umbrella_tex_0"},
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

    escrever_dome()
    print("umbrella: %d cores | texturas de item/cupula/HUD | 2 geometrias 3D | %d receitas"
          % (len(CORES), len(CORES) * 2))


if __name__ == "__main__":
    main()
