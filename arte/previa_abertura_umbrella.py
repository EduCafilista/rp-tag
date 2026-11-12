#!/usr/bin/env python3
"""Prévia da ANIMAÇÃO de abrir do guarda-chuva (5.41.1), em quadros.

O dono: "não está bonita a animação, melhore". A cúpula deixou de aparecer de uma vez: ela
cresce girando (ease-out) em 260 ms e encolhe em 200 ms ao fechar.

Este script desenha a MESMA conta que o Java faz em `client/CamadaUmbrella.java`:
    escala  = 0,18 + 0,82 * abre
    giro Y  = 55° * (1 - abre)
    altura  = 0,72 - 0,10 * (1 - abre)

Uso: python3 arte/previa_abertura_umbrella.py [cor]
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

import render_umbrella_offline as R

RAIZ = R.RAIZ
TEXTURAS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag",
                        "textures", "entity", "umbrella")


def quadros_animacao():
    """os instantes que a prévia mostra: (rótulo, quanto aberto de 0 a 1)."""
    return [
        ("fechado", 0.0),
        ("abrindo 1/4", 1.0 - (1.0 - 0.25) ** 3),
        ("abrindo 1/2", 1.0 - (1.0 - 0.50) ** 3),
        ("abrindo 3/4", 1.0 - (1.0 - 0.75) ** 3),
        ("ABERTO", 1.0),
    ]


def cupula_transformada(abre):
    """a cúpula com a transformação daquele instante da animação (em pixels de modelo)."""
    geometria = json.load(open(os.path.join(RAIZ, "arte", "dome_geometria.json"),
                              encoding="utf-8"))
    cubos, ty = R.cupula_do_json()
    escala = 0.18 + 0.82 * abre
    giro = math.radians(55.0 * (1.0 - abre))
    subida = (ty - 0.10 * (1.0 - abre)) * 16.0     # blocos -> pixels
    saida = []
    for c, dado in zip(cubos, geometria["cubos"] * geometria.get("setores", 16)):
        x0, y0, z0 = c["from"]
        x1, y1, z1 = c["to"]
        # a cúpula gira em volta do eixo do jogador (0,0) e depois escala e sobe
        ang = None
        rot = c.get("rotation")
        if rot:
            ang = math.radians(rot.get("angle", 0))
        pts = []
        for x in (x0, x1):
            for y in (y0, y1):
                for z in (z0, z1):
                    if ang is not None:
                        x, z = (x * math.cos(ang) + z * math.sin(ang),
                                -x * math.sin(ang) + z * math.cos(ang))
                    x, z = (x * math.cos(giro) + z * math.sin(giro),
                            -x * math.sin(giro) + z * math.cos(giro))
                    pts.append((x * escala, y * escala - subida, z * escala))
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        zs = [p[2] for p in pts]
        saida.append({"from": [min(xs), min(ys), min(zs)],
                      "to": [max(xs), max(ys), max(zs)],
                      # o arquivo guarda a REGIÃO da textura (texOffs), não o uv do cubo:
                      # vira um quadrado dessa região, que é como o ModelPart desenha
                      "uv": [dado["texOffs"][0], dado["texOffs"][1],
                             dado["texOffs"][0] + 6, dado["texOffs"][1] + 6]})
    return saida


def main():
    cor = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    caminho = os.path.join(TEXTURAS, "dome_%d.png" % cor)
    tex = Image.open(caminho).convert("RGBA") if os.path.exists(caminho) else None
    if tex is None:
        print("textura da cupula nao encontrada:", caminho)
        return

    cabeca = R.caixa(-4, -8, -4, 4, 0, 4, [0, 0, 0, 0])
    paineis = []
    for rotulo, abre in quadros_animacao():
        cubos = cupula_transformada(abre)
        if abre <= 0.0:
            cabeca_txt = "fechado (sem cupula)"
            im = R.renderizar([cabeca], {}, tex, yaw=-30, pitch=14, zoom=330, vazia=True)
        else:
            im = R.renderizar([cabeca], {}, tex, yaw=-30, pitch=14, zoom=330, extras=cubos)
            cabeca_txt = rotulo
        paineis.append((cabeca_txt, im))

    largura, altura = 260, 290
    folha = Image.new("RGBA", (largura * len(paineis), altura), (18, 16, 24, 255))
    des = ImageDraw.Draw(folha)
    for i, (titulo, im) in enumerate(paineis):
        folha.paste(im.convert("RGBA"), (i * largura, 30))
        des.text((i * largura + 8, 8), titulo, fill=(230, 220, 255))
    saida = os.path.join(RAIZ, "arte", "previa_abertura.png")
    folha.save(saida)
    print("previa da animacao:", saida, folha.size)


if __name__ == "__main__":
    main()
