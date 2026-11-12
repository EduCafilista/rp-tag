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

# regiões da textura do item (16x16) — (5.41.0) reorganizadas pra ter DOIS tons de tecido
R_TECIDO = (0, 0, 16, 8)      # tecido claro  (os gomos de cima)
R_TECIDO2 = (0, 8, 8, 12)     # tecido escuro (os gomos de baixo — o padrão de 8 painéis)
R_MADEIRA = (0, 12, 8, 16)    # madeira do cabo
R_METAL = (8, 8, 12, 11)      # metal: ponteira, luva e as pontas das varetas
R_ESCURO = (12, 8, 16, 16)    # tecido preto: amarração e varetas


def novo(larg=16, alt=16):
    return Image.new("RGBA", (larg, alt), (0, 0, 0, 0))


def px(img, x, y, cor, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), (cor[0], cor[1], cor[2], a))


def zera(cor, f):
    return tuple(max(0, min(255, int(c * f))) for c in cor)


# ------------------------------------------------------------------ textura do item
def textura_item(claro, escuro, detalhe):
    """16x16 com as 5 regiões do modelo — VOLUME, não listra chapada.

    (5.41.0) A reclamação do dono: <i>"não está bonita, deixe mais modelada"</i>. A textura
    antiga era uma diagonal chapada, então cada cubo virava uma mancha lisa. Agora:

      * <b>tecido claro</b> (0..16 x 0..8): 4 gomos com costura escura, nervura brilhante no
        meio de cada gomo e sombra de cima pra baixo;
      * <b>tecido escuro</b> (0..8 x 8..12): o mesmo desenho, um tom abaixo — é o que faz o
        padrão de 8 painéis quando as duas placas do octógono se cruzam;
      * <b>madeira</b> (0..8 x 12..16): veios verticais com nó;
      * <b>metal</b> (8..12 x 8..11): brilho diagonal;
      * <b>tecido preto</b> (12..16 x 8..16): varetas e amarração.
    """
    img = novo(16, 16)

    def gomos(x0, y0, x1, y1, cor_clara, passo=4, brilho=1.20):
        """pinta uma faixa de tecido com gomos (costura, nervura e sombra)."""
        for y in range(y0, y1):
            altura = max(1, y1 - y0 - 1)
            for x in range(x0, x1):
                dentro = (x - x0) % passo
                cor = cor_clara if ((x - x0) // passo) % 2 == 0 else zera(cor_clara, 0.86)
                if dentro == 0:
                    cor = zera(cor_clara, 0.55)
                elif dentro == 1:
                    cor = zera(cor, brilho)
                elif dentro == passo - 1:
                    cor = zera(cor, 0.80)
                cor = zera(cor, 1.12 - 0.09 * (y - y0) * (8.0 / altura))
                px(img, x, y, cor)

    gomos(0, 0, 16, 8, claro)                 # tecido claro
    gomos(0, 8, 8, 12, zera(claro, 0.80))     # tecido escuro
    # ---- madeira: veios verticais com um nó
    for y in range(12, 16):
        for x in range(0, 8):
            base = (98, 68, 44) if (x * 7 + y) % 9 else (130, 94, 60)
            if x in (0, 5):
                base = zera(base, 0.70)
            if (x, y) in ((2, 13), (3, 13), (2, 14)):
                base = zera(base, 0.60)
            px(img, x, y, base)
    # ---- metal: brilho diagonal
    for y in range(8, 11):
        for x in range(8, 12):
            d = (x - 8) + (y - 8)
            px(img, x, y, zera(detalhe, min(1.30, 1.0 + 0.22 * (3 - abs(d - 2)))))
    # ---- tecido preto (varetas e amarração)
    for y in range(8, 16):
        for x in range(12, 16):
            px(img, x, y, zera(escuro, 0.92) if (x + y) % 4 else zera(escuro, 0.58))
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


def octogono(raio, y0, y1, cor_claro=R_TECIDO, cor_escuro=R_TECIDO2, centro=8.0,
             borda=None, lado_borda=0.3):
    """um OCTÓGONO — e o truque é o mesmo do pixel art: <b>dois quadrados</b>.

    Um quadrado e outro girado 45° por cima, do mesmo tamanho aproximado, formam um octógono
    perfeito. Isso resolve o problema de golpe de vista que vinha me perseguindo: o Minecraft
    só aceita rotação de <b>-45° a 45°</b> em modelo de item (90° faz o jogo rejeitar o modelo
    inteiro e o item vira o quadriculado rosa/preto), então qualquer coisa "redonda" tem de
    ser montada com quadrados e ±45°. Dois quadrados = octógono, e o desenho sai LIMPO: as
    duas placas usam tons diferentes de tecido e o cruzamento das pontas desenha os 8 gomos.

    @param raio   raio do octógono (o canto do quadrado girado encosta nele)
    @param borda  se não for nulo, um anel fino mais escuro logo abaixo (a "barriga" do pano)
    """
    el = []
    lado = 1.414 * raio
    lado2 = 1.075 * raio
    for (l, uv, rot) in ((lado, cor_claro, None), (lado2, cor_escuro, 45)):
        el.append(cubo(centro - l / 2.0, y0, centro - l / 2.0,
                       centro + l / 2.0, y1, centro + l / 2.0, uv,
                       {"origin": [centro, (y0 + y1) / 2.0, centro], "axis": "y", "angle": rot}
                       if rot else None))
        if borda is not None:
            el.append(cubo(centro - l / 2.0, y0 - lado_borda, centro - l / 2.0,
                           centro + l / 2.0, y0, centro + l / 2.0, borda,
                           {"origin": [centro, y0, centro], "axis": "y", "angle": rot}
                           if rot else None))
    return el


def pontas(raio, y, uv=R_METAL, centro=8.0, tam=0.7):
    """as 8 pontas das varetas na borda do octógono (uma por canto dos dois quadrados)."""
    el = []
    for k in range(8):
        a = math.radians(45.0 * k)
        cx, cz = centro + raio * math.cos(a), centro + raio * math.sin(a)
        el.append(cubo(cx - tam / 2.0, y, cz - tam / 2.0,
                       cx + tam / 2.0, y + tam, cz + tam / 2.0, uv))
    return el


def haste_e_cabo(uv_madeira, y_cabo, y_alto):
    """a haste (de baixo pra cima), a luva de metal e o cabo em gancho."""
    el = [cubo(7.6, y_cabo + 1.2, 7.6, 8.4, y_alto, 8.4, uv_madeira)]
    el.append(cubo(7.42, y_cabo + 2.4, 7.42, 8.58, y_cabo + 3.3, 8.58, R_METAL))
    # cabo: desce, vira pra esquerda e sobe na ponta (o gancho)
    el.append(cubo(7.6, y_cabo + 0.35, 7.6, 8.4, y_cabo + 1.45, 8.4, uv_madeira))
    el.append(cubo(5.5, y_cabo + 0.35, 7.6, 7.6, y_cabo + 1.05, 8.4, uv_madeira))
    el.append(cubo(5.5, y_cabo + 0.35, 7.6, 6.3, y_cabo + 1.85, 8.4, uv_madeira))
    return el


def modelo_fechado():
    """guarda-chuva FECHADO: cabo com gancho EMBAIXO, pano enrolado subindo, ponteira no alto.

    (5.40.0) Antes o modelo estava DE CABEÇA PRA BAIXO (gancho no alto, ponteira no pé).
    (5.41.0) Agora são 5 anéis octogonais que <b>engordam pra cima</b> (o pano enrolado),
    alternando os dois tons de tecido — o dono pediu <i>"deixe mais modelada"</i> e a
    silhueta passou a ter degraus de verdade em vez de um tubo liso.
    """
    el = []
    aneis = [
        # (raio, y de baixo, y de cima)
        (1.50, 13.4, 14.4),
        (1.95, 12.0, 13.4),
        (2.45, 10.4, 12.0),
        (2.80, 8.6, 10.4),
        (3.05, 6.8, 8.6),
    ]
    for i, (raio, y0, y1) in enumerate(aneis):
        # alterna qual placa é a clara (dá o efeito de tecido dobrado)
        claro, escuro = (R_TECIDO, R_TECIDO2) if i % 2 == 0 else (R_TECIDO2, R_TECIDO)
        el += octogono(raio, y0, y1, claro, escuro)
    # amarração preta no meio do pano
    el += octogono(3.12, 8.9, 9.6, R_ESCURO, R_ESCURO)
    # as pontas das varetas aparecem logo acima da amarração
    el += pontas(2.85, 9.6, R_METAL, tam=0.62)
    el += haste_e_cabo(R_MADEIRA, y_cabo=0.6, y_alto=14.0)
    # ponteira de metal no alto (com aro)
    el.append(cubo(7.5, 14.4, 7.5, 8.5, 15.8, 8.5, R_METAL))
    el.append(cubo(7.22, 14.05, 7.22, 8.78, 14.4, 8.78, R_METAL))
    return el


def modelo_aberto():
    """guarda-chuva ABERTO: cúpula octogonal de 4 degraus, com a borda caindo.

    (5.40.0) O mesmo defeito do fechado, e pior: a cúpula estava em y 0,4..6,4 (embaixo).
    (5.41.0) <b>Mais modelado</b> (pedido: <i>"deixe mais modelada"</i>): 4 degraus de
    octógono, cada um com uma <b>borda fina e escura</b> embaixo (a barriga do pano), as
    <b>8 pontas das varetas</b> saindo da aba e o topo com bojo de metal. O cruzamento das
    duas placas de cada degrau desenha os 8 gomos. A cúpula tem 16px de ponta a ponta.
    """
    el = []
    degraus = [
        # (raio, y de baixo, y de cima)
        (8.1, 9.3, 10.5),    # aba (a borda de fora)
        (6.0, 10.5, 11.6),   # segundo
        (3.9, 11.6, 12.7),   # terceiro
        (1.9, 12.7, 13.9),   # topo (o miolo)
    ]
    for i, (raio, y0, y1) in enumerate(degraus):
        claro, escuro = (R_TECIDO, R_TECIDO2) if i % 2 == 0 else (R_TECIDO2, R_TECIDO)
        el += octogono(raio, y0, y1, claro, escuro, borda=R_ESCURO, lado_borda=0.35)
    # as 8 pontas das varetas na aba
    el += pontas(8.05, 9.0, R_METAL, tam=0.8)
    el += haste_e_cabo(R_MADEIRA, y_cabo=0.6, y_alto=13.2)
    # topo: bojo de metal e a ponteira
    el += octogono(1.6, 13.9, 14.6, R_METAL, R_METAL)
    el.append(cubo(7.45, 14.6, 7.45, 8.55, 15.9, 8.55, R_METAL))
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
    # (5.41.0) O ESTADO MORA NO custom_model_data: 0..7 = FECHADO (a cor), 8..15 = ABERTO
    # (a cor + 8). `custom_model_data` é predicado VANILLA — o jogo troca o modelo 3D na mão
    # sozinho, sem predicado de cliente pra registrar (era esse o furo das 5.39/5.40: um
    # predicado caseiro que ninguém registrava, então o modelo aberto nunca era escolhido).
    overrides = []
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i},
                          "model": "rptag:item/umbrella_fechado_%d" % i})
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i + len(CORES)},
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
