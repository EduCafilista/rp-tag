#!/usr/bin/env python3
"""(5.42.0) Importa o GUARDA-CHUVA 3D que o dono mandou e gera as 8 cores.

O dono (pedido 94-g): *"o guarda chuva tá tudo bugado, pega um modelo"* — e mandou um pacote
com dois modelos de verdade, feitos no Blockbench:

  * ``umbrella_open.obj``   — o guarda-chuva ABERTO  (~4 mil faces)
  * ``umbrella_closed.obj`` — o guarda-chuva FECHADO (~3,9 mil faces)
  * ``umbrella.mtl``        — o material (a textura entra por ``#atlas``)
  * ``umbrella.png``        — o atlas 64x64 com 8 ladrilhos de 16x16

O Minecraft abre arquivo ``.obj`` de item pelo loader ``neoforge:obj`` (que o próprio NeoForge
registra — conferido no jar do 21.1.249). Este script faz três coisas:

  1) copia o ``.obj``/``.mtl`` para ``assets/rptag/models/item/`` (o jogo lê de lá);
  2) <b>repinta o atlas nas 8 cores</b> do mod — mantendo o padrão de listras e o sombreado,
     trocando só o matiz: tecido claro vira a cor, tecido "creme" vira o tom claro dela, e os
     ladrilhos de baixo viram versões escuras (é o de dentro da cúpula). Metal, madeira e
     cinta preta ficam intactos;
  3) escreve os <b>16 modelos de item</b> (8 cores × aberto/fechado) com o bloco ``display``
     ajustado e reescreve os ``overrides`` do ``umbrella_sombra.json``.

A partir daqui o item na mão é este modelo. O que fica do lado antigo é a CÚPULA sobre a
cabeça (``client/CamadaUmbrella``), que é outra coisa.

Uso: python3 arte/importar_umbrella_obj.py
"""
import io
import colorsys
import json
import os
import shutil

from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag")
MODELOS = os.path.join(ASSETS, "models", "item")
TEXTURAS = os.path.join(ASSETS, "textures", "item")
ORIGEM = os.path.join(RAIZ, "arte", "origem")

# a paleta é a MESMA do resto do mod (arte/gerar_umbrella.py): nome, tecido, escuro, detalhe
CORES = [
    ("sangue", (168, 26, 44), (86, 12, 22), (226, 186, 112)),
    ("noite", (58, 40, 74), (26, 18, 34), (168, 148, 214)),
    ("juizo", (108, 52, 160), (52, 22, 86), (198, 142, 240)),
    ("sombra", (128, 128, 136), (62, 62, 70), (208, 208, 218)),
    ("veneno", (60, 122, 66), (26, 60, 32), (176, 216, 126)),
    ("luar", (52, 96, 158), (24, 44, 84), (164, 208, 246)),
    ("ouro", (198, 156, 48), (112, 82, 20), (255, 236, 156)),
    ("rosa", (204, 92, 128), (112, 42, 68), (255, 200, 218)),
]

# os 4 ladrilhos de tecido do atlas 64x64 (x0, y0, x1, y1) e como cada um se comporta
LADRILHOS_TECIDO = [
    (0, 0, 16, 16, "cor"),        # tecido principal (vermelho na origem)
    (16, 0, 32, 16, "claro"),     # o segundo tom (creme na origem)
    (32, 0, 48, 16, "cor_baixo"),   # parte de dentro, mais escura
    (48, 0, 64, 16, "claro_baixo"),
]

# display: (5.46.0) A POSE — MEDIDA, NÃO ADIVINHADA.
#
# Histórico: 5.43.0 [-180,-90,-80], 5.43.1 [-180,-90,-90] com translação [-6,17,8], 5.43.4
# copiou o Vampirism, 5.44.1 [-100,0,0] (cúpula nas pernas), 5.45.0 [80,0,0] (guarda-chuva
# subiu, mas o cabo ficou na altura do tornozelo e a cúpula longe da cabeça). Todas erradas
# porque eram CHUTE.
#
# A partir da 5.46.0 existe uma SIMULAÇÃO da cadeia real do jogo (a mesma conta que o
# Minecraft faz ao desenhar item na mão em 3ª pessoa), e ela foi VALIDADA contra duas fotos do
# dono no jogo:
#
#   mundo = T(0,1.5,0) · S(-1,-1,1)            (origem do jogador -> modelo)
#         · T(-5/16, 2/16, 0) · RX(braço)      (pivô do braço direito)
#         · T(-1/16, 7/16, 1/16)               (translateToHand)
#         · RX(-90) · RY(180)                  (ItemInHandLayer)
#         · T(1/16, 2/16, -10/16)              (posição do item na mão)
#         · T(translação/16) · R(rotação) · S(escala)     (o display que está aqui embaixo)
#
# Com [80,0,0] e translação zero a simulação prevê exatamente o que a foto mostrou: cabo em
# Y=0,31 (tornozelo) e cúpula em Y=1,44 — longe da cabeça. Agora, resolvendo para:
#   * o CABO na mão                  -> Y ≈ 0,74
#   * o guarda-chuva VERTICAL        -> eixo (0, +1,60, -0,02) em 95°
#   * a CÚPULA acima da cabeça       -> Y 1,70..2,19 (a cabeça acaba em 1,80)
# dá rotação 95° e translação [-2, 0, 8] em 1/16 de bloco.
# (5.47.0) o dono viu a 5.46.0 e disse "quase acertando o local certo": subiu mais 1/16, foi
# 1/16 para o lado do corpo (a cúpula saiu de X=0,37 para 0,31, mais centrada na cabeça) e
# ficou 1/16 mais para trás (o guarda-chuva estava um pouco à frente do rosto).
# A translação NÃO é chute: é a solução de M·t = (alvo − cabo), onde M são os eixos da mão.
DISPLAY = {
    # (5.48.0) A 3ª PESSOA AGORA É DESENHADA PELO MOD (client/UmbrellaNaMao), não por aqui.
    # Motivo: cinco versões tentando acertar a mão por `display` e o dono sempre via o
    # guarda-chuva flutuando. `UmbrellaNaMao` ancora o modelo no OSSO DA MÃO do jogador
    # (`translateToHand`) e desenha ele mesmo — então aqui a escala é ZERO, pra o vanilla não
    # desenhar um segundo guarda-chuva por cima.
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0],
                              "scale": [0.0, 0.0, 0.0]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0],
                             "scale": [0.0, 0.0, 0.0]},
    "firstperson_righthand": {"rotation": [95, 0, 0], "translation": [0, 4, 0],
                              "scale": [0.9, 1.2, 0.9]},
    "firstperson_lefthand": {"rotation": [95, 0, 0], "translation": [0, 4, 0],
                             "scale": [0.9, 1.2, 0.9]},
    # no chão, na moldura e no inventário o modelo só é DEITADO/girado pra caber bonito —
    # são contextos em que ninguém segura nada, então não há mão pra errar.
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    # (5.48.0) `fixed` é o contexto que o UmbrellaNaMao usa pra desenhar na mão: aqui ele
    # PRECISA ser identidade — o modelo cru é que 0,9 bloco de altura, e quem posiciona é o mod.
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
}

# (5.46.0) o FECHADO tem a MESMA rotação e a MESMA translação — abrir não muda o guarda-chuva
# de lugar, ele só desenrola na mão. O que muda é a escala em Y: recolhido ele é um bastão de
# ~0,95 bloco (a escala 1,6 deixava um poste de 1,5 bloco passando da cabeça).
DISPLAY_FECHADO = dict(DISPLAY)
DISPLAY_FECHADO["thirdperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0],
                                            "scale": [0.0, 0.0, 0.0]}
DISPLAY_FECHADO["thirdperson_lefthand"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0],
                                           "scale": [0.0, 0.0, 0.0]}


def _mistura(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _escurece(cor, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in cor)


def recolorir(atlas, cor, escuro, detalhe):
    """pinta os 4 ladrilhos de tecido na cor pedida, mantendo o SOMBREADO do original.

    A conta: cada pixel do tecido tem uma luminância (0..1) na origem; o pixel novo é a
    interpolação entre o tom escuro e o tom claro daquela cor. Assim as dobras, as costuras e
    o padrão de listras do modelo continuam exatamente onde estavam.
    """
    saida = atlas.copy()
    tons = {
        "cor": (cor, escuro),
        "claro": (_mistura(cor, (255, 255, 255), 0.45), _mistura(escuro, (60, 60, 60), 0.25)),
    }
    tons["cor_baixo"] = (tons["cor"][0], _escurece(tons["cor"][1], 1.0))
    tons["claro_baixo"] = (tons["claro"][0], tons["claro"][1])
    for x0, y0, x1, y1, tipo in LADRILHOS_TECIDO:
        claro_t, escuro_t = tons[tipo]
        for x in range(x0, x1):
            for y in range(y0, y1):
                r, g, b, a = atlas.getpixel((x, y))
                if a < 8:
                    continue
                lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                # o ladrilho de "baixo" é a parte de dentro: sempre um degrau mais escuro
                if tipo.endswith("_baixo"):
                    lum *= 0.62
                novo = _mistura(escuro_t, claro_t, max(0.0, min(1.0, lum)))
                saida.putpixel((x, y), (novo[0], novo[1], novo[2], a))
    return saida


def modelo_obj(nome_obj, textura, display=None):
    """o JSON de um modelo de item que usa o loader OBJ do NeoForge."""
    return {
        "loader": "neoforge:obj",
        "model": "rptag:models/item/" + nome_obj,
        "flip_v": True,
        "automatic_culling": False,
        "gui_light": "side",
        "textures": {
            "atlas": "rptag:item/" + textura,
            "particle": "rptag:item/" + textura,
        },
        "display": display or DISPLAY,
    }


def centralizar(vertices):
    """leva a geometria para a ORIGEM do item: centro em X/Z = 0 e o cabo em Y = 0.

    É isto que impede o guarda-chuva de "flutuar longe da mão": com o modelo centrado, girar
    ou transladar não cria braço de alavanca — ele gira em volta de si mesmo, no lugar da mão.
    """
    xs = [v[0] for v in vertices]
    ys = [v[1] for v in vertices]
    zs = [v[2] for v in vertices]
    cx = (min(xs) + max(xs)) / 2.0
    cz = (min(zs) + max(zs)) / 2.0
    y0 = min(ys)
    return [[v[0] - cx, v[1] - y0, v[2] - cz] for v in vertices]


def escrever_centralizado(origem, destino):
    """copia o .obj trocando só as linhas `v` pelas centralizadas (faces/normais/mtl iguais)."""
    linhas = []
    vertices = []
    for linha in io.open(origem, encoding="utf-8", errors="ignore"):
        if linha.startswith("v "):
            p = linha.split()
            vertices.append([float(p[1]), float(p[2]), float(p[3])])
            linhas.append(None)                     # marcador: reescreve depois
        else:
            linhas.append(linha)
    novos = centralizar(vertices)
    saida = []
    i = 0
    for linha in linhas:
        if linha is None:
            v = novos[i]
            i += 1
            saida.append("v %.6f %.6f %.6f\n" % (v[0], v[1], v[2]))
        else:
            saida.append(linha)
    with io.open(destino, "w", encoding="utf-8") as fp:
        fp.write("".join(saida))
    return novos


def main():
    fonte = os.path.join(ORIGEM, "umbrella_atlas.png")
    if not os.path.exists(fonte):
        raise SystemExit("falta a textura de origem: " + fonte)
    atlas = Image.open(fonte).convert("RGBA")

    # 1) os .obj/.mtl vão para onde o jogo lê — os .obj CENTRALIZADOS na origem do item
    for arq in ("umbrella_open.obj", "umbrella_closed.obj"):
        origem = os.path.join(ORIGEM, arq)
        if not os.path.exists(origem):
            raise SystemExit("falta o modelo de origem: " + origem)
        escrever_centralizado(origem, os.path.join(MODELOS, arq))
    shutil.copyfile(os.path.join(ORIGEM, "umbrella.mtl"),
                    os.path.join(MODELOS, "umbrella.mtl"))

    # 2) o atlas nas 8 cores
    for i, (nome, cor, escuro, detalhe) in enumerate(CORES):
        recolorir(atlas, cor, escuro, detalhe).save(
            os.path.join(TEXTURAS, "umbrella_%d.png" % i))

    # 3) os 16 modelos (8 cores x aberto/fechado)
    for i in range(len(CORES)):
        for estado, obj in (("fechado", "umbrella_closed.obj"),
                            ("aberto", "umbrella_open.obj")):
            caminho = os.path.join(MODELOS, "umbrella_obj_%s_%d.json" % (estado, i))
            with open(caminho, "w", encoding="utf-8") as fp:
                disp = DISPLAY if estado == "aberto" else DISPLAY_FECHADO
                fp.write(json.dumps(modelo_obj(obj, "umbrella_%d" % i, disp), indent=2) + "\n")

    # 4) o item aponta para eles: custom_model_data 0..7 fechado, 8..15 aberto
    overrides = []
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": i},
                          "model": "rptag:item/umbrella_obj_fechado_%d" % i})
    for i in range(len(CORES)):
        overrides.append({"predicate": {"custom_model_data": len(CORES) + i},
                          "model": "rptag:item/umbrella_obj_aberto_%d" % i})
    item = {
        "parent": "rptag:item/umbrella_obj_fechado_0",
        "textures": {"layer0": "rptag:item/umbrella_0",
                     "atlas": "rptag:item/umbrella_0",
                     "particle": "rptag:item/umbrella_0"},
        "overrides": overrides,
    }
    with open(os.path.join(MODELOS, "umbrella_sombra.json"), "w", encoding="utf-8") as fp:
        fp.write(json.dumps(item, indent=2) + "\n")

    print("umbrella: %d cores do atlas + %d modelos OBJ (aberto/fechado) + item com 16 estados"
          % (len(CORES), len(CORES) * 2))


if __name__ == "__main__":
    main()
