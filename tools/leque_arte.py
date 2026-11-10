#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.33.0) A ARTE DO LEQUE — gera as texturas da roda de habilidades do Vazio.

O dono mandou uma arte (uploads/leque_habilidades_vazio_escuridao_espaco.png):
uma roda roxa "O Vazio Eterno" com seis casas, marcas no aro, diamantes nos
pontos cardeais e estrelinhas. Aqui ela é re-desenhada em código, na paleta do
Vazio (roxo/violeta sobre preto), para virar textura do jogo.

Saída (assets/rptag/textures/gui/):
  leque_roda.png   1024x1024  o aro, as seis casas, os diamantes e as estrelas
  leque_<nome>.png  256x256   o ícone de cada habilidade (traço roxo)
  leque_apagado.png 128x128   a tampa escura da casa em recarga
  leque_brilho.png  256x256   o brilho roxo por trás da casa apontada
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, "src/main/resources/assets/rptag/textures/gui")

# paleta do Vazio (a mesma da arte do dono)
PRETO = (8, 4, 14, 255)
ROXO_ESCURO = (22, 9, 38, 255)
ROXO = (124, 60, 228, 255)
ROXO_CLARO = (166, 112, 255, 255)
ROXO_FRACO = (96, 52, 160, 255)
LAVANDA = (216, 196, 255, 255)
BRANCO = (245, 238, 255, 255)


def novo(tam):
    return Image.new("RGBA", (tam, tam), (0, 0, 0, 0))


def circulo(d, cx, cy, r, cor, largura=1):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=cor, width=largura)


def tracejado(d, cx, cy, r, cor, largura=1, passos=60, ocupado=0.55):
    for i in range(passos):
        if (i / passos) % 1.0 > ocupado:
            continue
        a0 = 2 * math.pi * i / passos
        a1 = 2 * math.pi * (i + 1) / passos
        d.line([cx + r * math.cos(a0), cy + r * math.sin(a0),
                cx + r * math.cos(a1), cy + r * math.sin(a1)], fill=cor, width=largura)


def losango(d, cx, cy, r, cor, preenchido=True):
    pts = [(cx, cy - r), (cx + r * 0.72, cy), (cx, cy + r), (cx - r * 0.72, cy)]
    if preenchido:
        d.polygon(pts, fill=cor)
    else:
        d.line(pts + [pts[0]], fill=cor, width=max(1, int(r * 0.22)))


def roda(tam=2048):
    """a roda inteira, desenhada grande e reduzida no fim (antisserrilhado)."""
    im = novo(tam)
    d = ImageDraw.Draw(im)
    cx = cy = tam / 2.0
    R = tam * 0.5

    # ---- o disco: gradiente radial escuro (quase preto no meio, roxo no aro)
    grad = Image.new("RGBA", (tam, tam), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    passos = 220
    for i in range(passos, 0, -1):
        t = i / passos                       # 1 = borda
        r = R * t
        mistura = t ** 2.2
        cor = (int(PRETO[0] + (38 - PRETO[0]) * mistura),
               int(PRETO[1] + (16 - PRETO[1]) * mistura),
               int(PRETO[2] + (64 - PRETO[2]) * mistura),
               int(238 + 17 * mistura))
        gd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=cor)
    im = grad
    d = ImageDraw.Draw(im)

    # ---- aro externo duplo
    circulo(d, cx, cy, R * 0.962, ROXO, int(tam * 0.0075))
    circulo(d, cx, cy, R * 0.936, ROXO_FRACO, int(tam * 0.0035))

    # ---- as marquinhas do aro (entre 0.885 e 0.925)
    for i in range(120):
        a = 2 * math.pi * i / 120
        longo = (i % 10 == 0)
        r0 = R * (0.885 if longo else 0.898)
        r1 = R * 0.925
        cor = ROXO_CLARO if longo else ROXO_FRACO
        d.line([cx + r0 * math.cos(a), cy + r0 * math.sin(a),
                cx + r1 * math.cos(a), cy + r1 * math.sin(a)],
               fill=cor, width=int(tam * (0.004 if longo else 0.0022)))

    # ---- segundo aro interno
    circulo(d, cx, cy, R * 0.868, ROXO, int(tam * 0.0045))

    # ---- os diamantes dos pontos cardeais (o de cima é maior)
    for i, ang in enumerate([-math.pi / 2, 0, math.pi / 2, math.pi]):
        rr = R * 0.962
        px, py = cx + rr * math.cos(ang), cy + rr * math.sin(ang)
        lado = tam * (0.026 if i == 0 else 0.017)
        losango(d, px, py, lado, ROXO_CLARO)
        circulo(d, px, py, lado * 0.30, PRETO)

    # ---- as seis casas (uma por habilidade)
    for i in range(6):
        ang = -math.pi / 2 + i * (2 * math.pi / 6)
        px = cx + R * 0.632 * math.cos(ang)
        py = cy + R * 0.632 * math.sin(ang)
        r = R * 0.166
        # o cabo que liga a casa ao miolo
        d.line([cx + R * 0.30 * math.cos(ang), cy + R * 0.30 * math.sin(ang),
                px - r * 0.98 * math.cos(ang), py - r * 0.98 * math.sin(ang)],
               fill=ROXO_FRACO, width=int(tam * 0.0035))
        # o miolo escuro da casa
        d.ellipse([px - r, py - r, px + r, py + r], fill=(14, 6, 26, 235))
        circulo(d, px, py, r, ROXO, int(tam * 0.0055))
        tracejado(d, px, py, r * 0.84, ROXO_FRACO, max(1, int(tam * 0.003)),
                  passos=54, ocupado=0.5)

    # ---- o miolo central
    rm = R * 0.335
    d.ellipse([cx - rm, cy - rm, cx + rm, cy + rm], fill=(12, 5, 22, 240))
    circulo(d, cx, cy, rm, ROXO, int(tam * 0.006))
    tracejado(d, cx, cy, rm * 0.90, ROXO_FRACO, max(1, int(tam * 0.0032)),
              passos=90, ocupado=0.55)

    # ---- estrelinhas espalhadas (como na arte)
    estrelas = [(0.30, -0.62, 0.006), (-0.44, -0.52, 0.005), (0.58, 0.10, 0.004),
                (-0.60, 0.24, 0.005), (0.22, 0.66, 0.006), (-0.28, 0.72, 0.004),
                (0.70, -0.38, 0.004), (-0.72, -0.30, 0.0045), (0.05, -0.86, 0.004),
                (-0.06, 0.86, 0.0035), (0.86, 0.28, 0.0035), (-0.86, -0.12, 0.004)]
    for (ex, ey, er) in estrelas:
        px, py = cx + ex * R, cy + ey * R
        rr = er * tam
        d.ellipse([px - rr, py - rr, px + rr, py + rr], fill=LAVANDA)
        # o brilhinho em cruz
        d.line([px - rr * 3.2, py, px + rr * 3.2, py], fill=(*ROXO_CLARO[:3], 120),
               width=max(1, int(rr * 0.55)))
        d.line([px, py - rr * 3.2, px, py + rr * 3.2], fill=(*ROXO_CLARO[:3], 120),
               width=max(1, int(rr * 0.55)))
    for _i in range(46):
        a = (math.sin(_i * 12.9898) * 43758.5453) % 1.0
        b = (math.sin(_i * 78.233) * 12345.6789) % 1.0
        rr = R * (0.34 + 0.62 * b)
        px, py = cx + rr * math.cos(a * 2 * math.pi), cy + rr * math.sin(a * 2 * math.pi)
        s = tam * 0.0016 * (1 + (b * 3) % 1)
        d.ellipse([px - s, py - s, px + s, py + s], fill=(*LAVANDA[:3], 110))

    # ---- brilho por dentro do aro (o "vidro" da arte)
    brilho = Image.new("RGBA", (tam, tam), (0, 0, 0, 0))
    bd = ImageDraw.Draw(brilho)
    bd.ellipse([cx - R * 0.93, cy - R * 0.93, cx + R * 0.93, cy + R * 0.93],
               fill=(90, 40, 180, 26))
    brilho = brilho.filter(ImageFilter.GaussianBlur(tam * 0.02))
    im = Image.alpha_composite(im, brilho)
    return im.resize((1024, 1024), Image.LANCZOS)


def icone(tam, desenhar):
    im = novo(tam * 2)
    d = ImageDraw.Draw(im)
    T = tam * 2
    # halo roxo por trás
    halo = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hd.ellipse([T * 0.18, T * 0.18, T * 0.82, T * 0.82], fill=(120, 60, 220, 90))
    halo = halo.filter(ImageFilter.GaussianBlur(T * 0.06))
    im = Image.alpha_composite(im, halo)
    d = ImageDraw.Draw(im)
    desenhar(d, T)
    return im.resize((tam, tam), Image.LANCZOS)


def raio(d, T):
    """a lança carmesim — um raio vertical"""
    p = [(0.60, 0.14), (0.33, 0.52), (0.49, 0.52), (0.40, 0.86),
         (0.68, 0.44), (0.52, 0.44)]
    pts = [(x * T, y * T) for (x, y) in p]
    d.polygon(pts, fill=(150, 90, 255, 255))
    d.line(pts + [pts[0]], fill=BRANCO, width=int(T * 0.022))


def orbe(d, T):
    """orbe com órbita"""
    d.ellipse([T * 0.28, T * 0.28, T * 0.72, T * 0.72], outline=BRANCO, width=int(T * 0.026))
    d.ellipse([T * 0.35, T * 0.35, T * 0.65, T * 0.65], fill=(140, 80, 240, 210))
    d.ellipse([T * 0.16, T * 0.44, T * 0.84, T * 0.60], outline=ROXO_CLARO, width=int(T * 0.020))
    d.ellipse([T * 0.46, T * 0.45, T * 0.54, T * 0.53], fill=BRANCO)


def onda(d, T):
    """a onda da morte: três arcos concêntricos"""
    for i, y in enumerate((0.36, 0.52, 0.68)):
        pts = []
        for k in range(41):
            x = 0.16 + 0.68 * k / 40.0
            yy = y + 0.075 * math.sin((x - 0.16) / 0.68 * math.pi * 2 + i * 0.6)
            pts.append((x * T, yy * T))
        d.line(pts, fill=BRANCO if i == 1 else ROXO_CLARO, width=int(T * 0.028))


def olhos(d, T):
    """um olho aberto (o do banir)"""
    pts = []
    for k in range(33):
        x = k / 32.0
        pts.append(((0.16 + 0.68 * x) * T, (0.5 - 0.22 * math.sin(x * math.pi)) * T))
    for k in range(33):
        x = 1.0 - k / 32.0
        pts.append(((0.16 + 0.68 * x) * T, (0.5 + 0.22 * math.sin(x * math.pi)) * T))
    d.line(pts, fill=BRANCO, width=int(T * 0.026))
    d.ellipse([T * 0.40, T * 0.40, T * 0.60, T * 0.60], fill=(150, 90, 255, 255))
    d.ellipse([T * 0.455, T * 0.455, T * 0.545, T * 0.545], fill=BRANCO)


def penas(d, T):
    """a chuva de penas: uma pena cheia, com cálamo e barbas"""
    # a pétala cheia (vane)
    vane = [(0.5, 0.10), (0.66, 0.34), (0.70, 0.54), (0.62, 0.74),
            (0.5, 0.86), (0.38, 0.74), (0.30, 0.54), (0.34, 0.34)]
    d.polygon([(x * T, y * T) for (x, y) in vane], fill=(120, 62, 220, 235))
    d.line([(x * T, y * T) for (x, y) in vane] + [(0.5 * T, 0.10 * T)],
           fill=BRANCO, width=int(T * 0.026))
    # o cálamo
    d.line([(0.5 * T, 0.16 * T), (0.5 * T, 0.92 * T)], fill=BRANCO, width=int(T * 0.024))
    # as barbas
    for k in range(6):
        y = 0.26 + k * 0.10
        d.line([(0.5 * T, y * T), ((0.64 - k * 0.012) * T, (y + 0.05) * T)],
               fill=LAVANDA, width=int(T * 0.011))
        d.line([(0.5 * T, y * T), ((0.36 + k * 0.012) * T, (y + 0.05) * T)],
               fill=LAVANDA, width=int(T * 0.011))


def banir(d, T):
    """o olho na fenda (o portal do banir)"""
    # a fenda vertical
    pts = [(0.36, 0.10), (0.64, 0.10), (0.58, 0.90), (0.42, 0.90)]
    d.line(pts + [pts[0]], fill=ROXO_CLARO, width=int(T * 0.026))
    # o olho dentro
    olho = []
    for k in range(25):
        x = k / 24.0
        olho.append(((0.38 + 0.24 * x) * T, (0.5 - 0.13 * math.sin(x * math.pi)) * T))
    for k in range(25):
        x = 1.0 - k / 24.0
        olho.append(((0.38 + 0.24 * x) * T, (0.5 + 0.13 * math.sin(x * math.pi)) * T))
    d.line(olho, fill=BRANCO, width=int(T * 0.020))
    d.ellipse([T * 0.44, T * 0.44, T * 0.56, T * 0.56], fill=(160, 70, 240, 255))
    d.ellipse([T * 0.475, T * 0.475, T * 0.525, T * 0.525], fill=BRANCO)


def apagado(tam=128):
    """a tampa escura que cobre a casa em recarga."""
    im = novo(tam * 4)
    d = ImageDraw.Draw(im)
    T = tam * 4
    d.ellipse([0, 0, T, T], fill=(6, 3, 12, 205))
    circulo(d, T / 2, T / 2, T * 0.47, (*ROXO_FRACO[:3], 150), int(T * 0.02))
    return im.resize((tam, tam), Image.LANCZOS)


def brilho(tam=256):
    """o halo roxo da casa apontada."""
    im = novo(tam)
    d = ImageDraw.Draw(im)
    d.ellipse([tam * 0.10, tam * 0.10, tam * 0.90, tam * 0.90], fill=(120, 60, 220, 150))
    im = im.filter(ImageFilter.GaussianBlur(tam * 0.07))
    d = ImageDraw.Draw(im)
    circulo(d, tam / 2, tam / 2, tam * 0.40, (*ROXO_CLARO[:3], 170), max(1, int(tam * 0.012)))
    return im


def main():
    os.makedirs(SAIDA, exist_ok=True)
    roda().save(os.path.join(SAIDA, "leque_roda.png"))
    for nome, fn in [("raio", raio), ("orbe", orbe), ("onda", onda),
                     ("olhos", olhos), ("penas", penas), ("banir", banir)]:
        icone(256, fn).save(os.path.join(SAIDA, "leque_%s.png" % nome))
    apagado().save(os.path.join(SAIDA, "leque_apagado.png"))
    brilho().save(os.path.join(SAIDA, "leque_brilho.png"))
    print("texturas do leque ok em", SAIDA)


if __name__ == "__main__":
    main()
