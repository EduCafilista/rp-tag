#!/usr/bin/env python3
"""(5.44.0) A AMETISTA VERMELHA — "FLUXO".

Pedido do dono: <i>"faça uma ametista vermelha chamada fluxo, faça todas as versões da ametista
do mine normal so que com textura vermelha"</i>.

Então este script pega TODAS as texturas da família da ametista do Minecraft (extraídas do jar
do cliente, que é a fonte da verdade — nada de desenhar no olho) e gira a MATIZ para o vermelho,
preservando:
  * a LUMINÂNCIA original (as facetas, os brilhos, o degradê das pontas continuam onde estavam);
  * a SATURAÇÃO original (o vermelho tem o mesmo "quão forte" do roxo);
  * o ALFA (as bordas recortadas dos brotos e do cacho).

A tabela roda em HSV: o roxo da ametista mora em ~270°; o alvo é 0° (vermelho) com uma pontinha
para o laranja (até -12°), porque vermelho puro em HSV fica "chapado" e o Fluxo é para parecer
pedra, não plástico.

Uso:
  python3 arte/gerar_fluxo.py            # gera/atualiza as texturas
  python3 arte/gerar_fluxo.py --listar   # só mostra o que faria
"""
import colorsys
import io
import os
import sys
import zipfile

from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag")
CLIENTE = ("/home/user/mcclient/libraries/net/minecraft/client/1.21.1-20240808.144430/"
           "client-1.21.1-20240808.144430-extra.jar")

# (origem no jar, destino no mod). (5.47.0) SO DUAS TEXTURAS: o bloco e o fragmento — o dono
# mandou tirar "as buddy horriveis" (ametista em formação, brotos e cacho).
TEXTURAS = [
    ("assets/minecraft/textures/block/amethyst_block.png", "textures/block/fluxo_block.png"),
    ("assets/minecraft/textures/item/amethyst_shard.png", "textures/item/fluxo_shard.png"),
]

# o vermelho do Fluxo: matiz alvo (graus) e uma faixa para o alaranjado, para a pedra ter o
# mesmo "degradê de cor" que o roxo da ametista (que vai de ~250° a ~300°).
MATIZ_ALVO = 0.0
MATIZ_LEQUE = 9.0      # quanto a matiz original "espalha" em volta do alvo

# (5.46.0) O DONO: "a ametista vermelha deve ser MAIS VERMELHA, muito mais vermelha".
# A 5.44.0 só girava a matiz e mantinha a saturação ORIGINAL — e a ametista do Minecraft é
# lavada (roxo claro, saturação ~0,35), então o resultado saiu rosa/salmão em vez de sangue.
# Agora a saturação é empurrada para perto do máximo e o valor desce um tico: é vermelho de
# verdade, mantendo as facetas (a luminância continua sendo a da textura original).
SAT_ESCALA = 2.6
SAT_PISO = 0.92
VALOR = 0.96


def _vermelho(h, s, v):
    """troca a matiz da ametista (roxo) pela do Fluxo (vermelho) e SATURA o vermelho."""
    # a ametista vai de ~250° (0.69) a ~300° (0.83); normaliza dentro dessa faixa em -1..1
    faixa = (h - 0.75) / 0.10
    faixa = max(-1.5, min(1.5, faixa))
    alvo = (MATIZ_ALVO + faixa * MATIZ_LEQUE) % 360.0
    s2 = max(SAT_PISO, min(1.0, s * SAT_ESCALA))
    v2 = max(0.0, min(1.0, v * VALOR))
    return colorsys.hsv_to_rgb(alvo / 360.0, s2, v2)


def converter(imagem):
    saida = imagem.convert("RGBA")
    px = saida.load()
    largura, altura = saida.size
    for x in range(largura):
        for y in range(altura):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s < 0.06:                      # cinza/quase cinza: deixa como está (é sombra)
                continue
            nr, ng, nb = _vermelho(h, s, v)
            px[x, y] = (int(round(nr * 255)), int(round(ng * 255)), int(round(nb * 255)), a)
    return saida


def main():
    if not os.path.exists(CLIENTE):
        print("sem o jar do cliente em", CLIENTE)
        print("rode:  bash /home/user/setup_env.sh")
        return 1
    listar = "--listar" in sys.argv
    with zipfile.ZipFile(CLIENTE) as jar:
        nomes = set(jar.namelist())
        for origem, destino in TEXTURAS:
            if origem not in nomes:
                print("FALTA no jar do cliente:", origem)
                return 1
            alvo = os.path.join(ASSETS, destino)
            if listar:
                print("%-58s -> %s" % (origem, destino))
                continue
            os.makedirs(os.path.dirname(alvo), exist_ok=True)
            with jar.open(origem) as fp:
                img = Image.open(io.BytesIO(fp.read()))
            vermelha = converter(img)
            vermelha.save(alvo)
            print("%-40s %dx%d -> %s" % (os.path.basename(origem), img.width, img.height,
                                         os.path.relpath(alvo, RAIZ)))
    if not listar:
        print("\n%d texturas do Fluxo prontas." % len(TEXTURAS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
