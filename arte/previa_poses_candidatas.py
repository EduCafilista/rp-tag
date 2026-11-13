#!/usr/bin/env python3
"""(5.43.4) Prévia de POSES candidatas para o guarda-chuva na mão (3ª pessoa).

O dono: <i>"veja como fica o umbrella do mod vampirism, ai fica daquela maneira"</i>.

Então este script desenha, lado a lado e com o corpo do jogador do lado (pose sem corpo não
dá pra julgar), a pose que está nos modelos hoje e as candidatas — incluindo os NÚMEROS
COPIADOS do modelo de guarda-chuva do Vampirism (``vampirism:models/item/umbrella.json``,
display ``thirdperson_righthand`` = rotação [81, -85, -2], translação [-1.391, 2.7826, 15.303],
escala [1, 1.7737, 1]).

Uso:
  python3 arte/previa_poses_candidatas.py
"""
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from previa_pose_umbrella import (  # noqa: E402
    MODELOS, RAIZ, TEXTURAS, TOPO_DA_CABECA, centro_da_cupula, desenhar, ler_obj, posar,
)

# --- de onde vieram os números do Vampirism (documentado, não chutado) ---------------------
# https://github.com/maxanier/Vampirism → src/main/resources/assets/vampirism/models/item/umbrella.json
VAMPIRISM_3A_D = {"rotation": [81, -85, -2],
                  "translation": [-1.391, 2.7826, 15.303],
                  "scale": [1.0, 1.7737, 1.0]}

CANDIDATAS = [
    ("HOJE (5.43.3)", {"rotation": [-180, -90, -90], "translation": [-6, 17, 8],
                       "scale": [0.72, 0.72, 0.72]}),
    ("VAMPIRISM cru", VAMPIRISM_3A_D),
    # o Vampirism foi feito num modelo em px (0..16) com o cabo embaixo; a nossa geometria está
    # em blocos (0..1) — a translação é em 1/16 de bloco, então só a escala Y muda o desenho.
    ("VAMPIRISM sem esticar", {"rotation": [81, -85, -2],
                               "translation": [-1.391, 2.7826, 15.303],
                               "scale": [1.0, 1.0, 1.0]}),
    ("VAMPIRISM com Y=0.9", {"rotation": [81, -85, -2],
                             "translation": [-1.391, 2.7826, 15.303],
                             "scale": [1.0, 0.9, 1.0]}),
]


def main():
    tex = Image.open(os.path.join(TEXTURAS, "umbrella_0.png")).convert("RGBA")
    vs, faces = ler_obj(os.path.join(MODELOS, "umbrella_open.obj"))
    paineis = []
    for titulo, display in CANDIDATAS:
        v2 = posar(vs, display)
        c = centro_da_cupula(vs, v2)
        nota = "cupula Y=%.2f X=%.2f Z=%.2f" % (c[1], c[0], c[2])
        rot = display["rotation"]
        paineis.append(("%s\nrot %s\n%s" % (titulo, rot, nota), desenhar(v2, faces, tex)))

    largura, altura = 300, 400
    folha = Image.new("RGBA", (largura * len(paineis), altura + 40), (22, 20, 28, 255))
    des = ImageDraw.Draw(folha)
    for i, (titulo, im) in enumerate(paineis):
        folha.paste(im, (i * largura, 38))
        for j, linha in enumerate(titulo.split("\n")):
            des.text((i * largura + 8, 6 + j * 11), linha, fill=(235, 225, 255))
    saida = os.path.join(RAIZ, "arte", "previa_poses.png")
    folha.save(saida)
    print("prévia das poses:", saida, folha.size, "(a cabeça termina em %.2f)" % TOPO_DA_CABECA)


if __name__ == "__main__":
    main()
