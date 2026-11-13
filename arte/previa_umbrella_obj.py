#!/usr/bin/env python3
"""(5.42.0) Prévia, fora do jogo, do guarda-chuva OBJ com as 8 cores.

Lê o ``.obj``/``.mtl`` do próprio mod e a textura repintada de cada cor, e desenha uma folha
com as 8 cores (fechado em cima, aberto embaixo) — é o que o dono vai ver na mão.

A conta é a mesma do jogo: o ``.obj`` vem em espaço de BLOCO (0..1), o item é centrado em
(0,5; 0,5; 0,5) e o ``display`` da 3ª pessoa gira/escala por cima.

Uso: python3 arte/previa_umbrella_obj.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODELOS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "models", "item")
TEXTURAS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "textures", "item")
CORES = ["sangue", "noite", "juizo", "sombra", "veneno", "luar", "ouro", "rosa"]


def ler_obj(caminho):
    """devolve (vértices, faces) — cada face é uma lista de (índice_v, u, v)."""
    vs, faces = [], []
    tex = []
    for linha in open(caminho, encoding="utf-8", errors="ignore"):
        if linha.startswith("v "):
            p = linha.split()
            vs.append((float(p[1]), float(p[2]), float(p[3])))
        elif linha.startswith("vt "):
            p = linha.split()
            tex.append((float(p[1]), float(p[2])))
        elif linha.startswith("f "):
            face = []
            for pedaco in linha.split()[1:]:
                partes = pedaco.split("/")
                vi = int(partes[0]) - 1
                ti = int(partes[1]) - 1 if len(partes) > 1 and partes[1] else -1
                u, v = tex[ti] if 0 <= ti < len(tex) else (0.0, 0.0)
                face.append((vi, u, v))
            if len(face) >= 3:
                faces.append(face)
    return vs, faces


def projetar(ponto, yaw, pitch, zoom, largura, altura, centro):
    """perspectiva simples com o item centrado no quadro."""
    x, y, z = ponto[0] - centro[0], ponto[1] - centro[1], ponto[2] - centro[2]
    cy, sy = math.cos(yaw), math.sin(yaw)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cp, sp = math.cos(pitch), math.sin(pitch)
    y, z = y * cp - z * sp, y * sp + z * cp
    d = 2.6 + z
    if d < 0.1:
        d = 0.1
    return (largura / 2 + x * zoom / d, altura / 2 - y * zoom / d, d)


def desenhar(arquivo_obj, textura, largura=250, altura=250, yaw=-35, pitch=16, zoom=520):
    vs, faces = ler_obj(os.path.join(MODELOS, arquivo_obj))
    if not vs:
        return Image.new("RGBA", (largura, altura), (18, 16, 24, 255))
    xs = [v[0] for v in vs]
    ys = [v[1] for v in vs]
    zs = [v[2] for v in vs]
    centro = ((min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0, (min(zs) + max(zs)) / 2.0)
    yaw_r, pitch_r = math.radians(yaw), math.radians(pitch)
    img = Image.new("RGBA", (largura, altura), (18, 16, 24, 255))
    des = ImageDraw.Draw(img)
    pintadas = []
    for face in faces:
        pontos = []
        uv_media = [0.0, 0.0]
        for vi, u, v in face:
            pt = projetar(vs[vi], yaw_r, pitch_r, zoom, largura, altura, centro)
            pontos.append(pt)
            uv_media[0] += u / len(face)
            uv_media[1] += v / len(face)
        # amostra a cor no meio da face (a textura é um atlas: o uv já cai no ladrilho certo)
        tx = int(max(0, min(textura.width - 1, uv_media[0] * textura.width)))
        ty = int(max(0, min(textura.height - 1, (1.0 - uv_media[1]) * textura.height)))
        r, g, b, a = textura.getpixel((tx, ty))
        if a < 16:
            continue
        prof = sum(p[2] for p in pontos) / len(pontos)
        # luz falsa: cima mais clara (dá volume ao que é chanfrado no modelo)
        luz = 0.55 + 0.45 * max(0.0, 1.0 - abs(pitch))
        cor = (min(255, int(r * luz)), min(255, int(g * luz)), min(255, int(b * luz)))
        pintadas.append((prof, [p[:2] for p in pontos], cor))
    pintadas.sort(key=lambda f: -f[0])
    for _, poly, cor in pintadas:
        des.polygon(poly, fill=cor)
    return img


def main():
    paineis = []
    for i, nome in enumerate(CORES):
        tex = Image.open(os.path.join(TEXTURAS, "umbrella_%d.png" % i)).convert("RGBA")
        paineis.append(("%s (fechado)" % nome,
                        desenhar("umbrella_closed.obj", tex)))
    for i, nome in enumerate(CORES):
        tex = Image.open(os.path.join(TEXTURAS, "umbrella_%d.png" % i)).convert("RGBA")
        paineis.append(("%s (aberto)" % nome,
                        desenhar("umbrella_open.obj", tex, yaw=-30, pitch=22, zoom=470)))

    largura, altura = 250, 280
    colunas = 8
    linhas = (len(paineis) + colunas - 1) // colunas
    folha = Image.new("RGBA", (largura * colunas, altura * linhas), (18, 16, 24, 255))
    des = ImageDraw.Draw(folha)
    for i, (titulo, im) in enumerate(paineis):
        cx = (i % colunas) * largura
        cy = (i // colunas) * altura
        des.text((cx + 8, cy + 8), titulo, fill=(230, 220, 255))
        folha.paste(im, (cx, cy + 20))
    saida = os.path.join(RAIZ, "arte", "previa_umbrella_obj.png")
    folha.save(saida)
    print("previa do modelo do dono:", saida, folha.size, len(paineis), "paineis")


if __name__ == "__main__":
    main()
