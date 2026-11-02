#!/usr/bin/env python3
"""Prévia do FX de ENGOLIR (estilo 23) — o protótipo uKqjYudD do dono.

Desenha 3 quadros da animação (a poça, o corpo sendo puxado, o clímax com os
braços e o texto) para o dono ver o desenho sem abrir o jogo.
"""
import math
import random

from PIL import Image, ImageDraw, ImageFont

W, H = 960, 640
CX = W / 2
CHAO = 470


def fonte(tam):
    for cam in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(cam, tam)
        except Exception:
            continue
    return ImageFont.load_default()


def fundo(escuridao=0.35):
    img = Image.new("RGB", (W, H), (20, 17, 24))
    d = ImageDraw.Draw(img, "RGBA")
    # "mundo" visto por trás (silhueta de prédios perdidos)
    random.seed(7)
    x = 0
    while x < W:
        larg = random.randint(40, 90)
        alt = random.randint(80, 300)
        d.rectangle([x, CHAO - alt, x + larg, CHAO], fill=(34, 29, 40, 255))
        for fy in range(CHAO - alt + 12, CHAO - 10, 26):
            if random.random() < 0.5:
                d.rectangle([x + 8, fy, x + 8 + random.randint(6, 14), fy + 5],
                            fill=(120, 30, 34, 255))
        x += larg + random.randint(4, 26)
    # neblina preta
    d.rectangle([0, 0, W, H], fill=(3, 2, 4, int(255 * escuridao)))
    return img, d


def poco(d, raio, alpha=255):
    d.ellipse([CX - raio * 1.9, CHAO + 22 - raio * 0.62,
               CX + raio * 1.9, CHAO + 22 + raio * 0.62], fill=(0, 0, 0, alpha))
    d.ellipse([CX - raio * 1.55, CHAO + 22 - raio * 0.48,
               CX + raio * 1.55, CHAO + 22 + raio * 0.48], fill=(5, 2, 3, alpha))
    for i in range(3):
        k = 0.45 + i * 0.25
        r = raio * k
        d.ellipse([CX - r * 1.6, CHAO + 22 - r * 0.55, CX + r * 1.6, CHAO + 22 + r * 0.55],
                  outline=(138, 10, 20, 120), width=2)


def corpo(d, escala_y=1.0, escala_x=1.0, desce=0.0, alpha=255, cabeca=True):
    pe = CHAO + 6 + desce
    def r(x0, y0, x1, y1, cor):
        d.rectangle([CX + x0 * escala_x, pe + y0 * escala_y,
                     CX + x1 * escala_x, pe + y1 * escala_y],
                    fill=cor + (alpha,))
    if cabeca:
        r(-8, -34, 8, -18, (198, 142, 107))
        r(-8, -34, 8, -29, (58, 36, 18))
        r(-6, -26, -3, -24, (255, 255, 255))
        r(3, -26, 6, -24, (255, 255, 255))
    r(-8, -18, 8, -4, (0, 168, 168))
    r(-12, -18, -8, -6, (0, 168, 168))
    r(8, -18, 12, -6, (0, 168, 168))
    r(-12, -6, -8, 0, (198, 142, 107))
    r(8, -6, 12, 0, (198, 142, 107))
    r(-7, -4, -1, 6, (60, 60, 154))
    r(1, -4, 7, 6, (60, 60, 154))


def olhos(d, t, quantos=10):
    random.seed(4)
    for i in range(quantos):
        if i < 6:
            ox = CX + random.uniform(-75, 75)
            oy = CHAO + random.uniform(12, 54)
        else:
            a = random.uniform(0, 6.28)
            dd = random.uniform(150, 380)
            ox = CX + math.cos(a) * dd
            oy = CHAO + 30 + math.sin(a) * dd * 0.32
        rx = random.uniform(5, 9)
        ry = rx * 0.62
        d.ellipse([ox - rx, oy - ry, ox + rx, oy + ry], fill=(233, 226, 226, 230))
        d.ellipse([ox - ry * 0.8, oy - ry * 0.8, ox + ry * 0.8, oy + ry * 0.8],
                  fill=(176, 0, 0, 245))
        d.ellipse([ox - ry * 0.34, oy - ry * 0.34, ox + ry * 0.34, oy + ry * 0.34],
                  fill=(10, 0, 0, 245))


def bracos(d, t, comprimento=1.0):
    random.seed(11)
    for i in range(9):
        ang = -2.55 + i * (2.4 / 8)
        bx = CX + math.cos(ang) * 125
        by = CHAO + 26
        tx = CX + math.cos(ang) * (30 + 46 * 0.9)
        ty = CHAO - (120 + 150 * 0.8) * 0.55 * comprimento + 36
        pts = []
        for e in range(0, 4):
            u = e / 3
            x = bx + (tx - bx) * u + math.sin(u * 3.1) * 14 * (1 - u)
            y = by + (ty - by) * u - math.sin(u * 3.1) * 10 * (1 - u)
            pts.append((x, y))
        d.line([(q[0], q[1] + 7) for q in pts], fill=(160, 12, 24, 210), width=15)
        d.line(pts, fill=(26, 22, 32, 255), width=13, joint="curve")
        
        # dedos
        for k in range(7):
            fa = -2.6 + k * (1.35 / 6)
            d.line([pts[-1], (pts[-1][0] + math.cos(fa) * 15,
                              pts[-1][1] + math.sin(fa) * 11)],
                   fill=(26, 22, 32, 255), width=4)
            d.ellipse([pts[-1][0] + math.cos(fa) * 15 - 2, pts[-1][1] + math.sin(fa) * 11 - 2,
                       pts[-1][0] + math.cos(fa) * 15 + 2, pts[-1][1] + math.sin(fa) * 11 + 2],
                      fill=(194, 20, 30, 200))


def brilho_vermelho(img, cx, cy, r, forca):
    camada = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(camada)
    for i in range(r, 0, -4):
        a = int(forca * 255 * (1 - i / r) ** 2)
        dd.ellipse([cx - i, cy - i, cx + i, cy + i], fill=(255, 16, 16, a))
    return Image.alpha_composite(img.convert("RGBA"), camada).convert("RGB")


def legenda(d, txt, y, tam, cor, alpha=255):
    f = fonte(tam)
    larg = d.textlength(txt, font=f)
    d.text((CX - larg / 2, y), txt, font=f, fill=cor + (alpha,))


def quadro(n, titulo):
    if n == 1:
        img, d = fundo(0.10)
        poco(d, 62)
        corpo(d)
        olhos(d, 0, 4)
        legenda(d, "algo está embaixo de você...", CHAO - 210, 26, (140, 140, 140))
    elif n == 2:
        img, d = fundo(0.16)
        poco(d, 100)
        olhos(d, 0, 10)
        corpo(d, escala_y=1.45, escala_x=0.72, desce=120)
        d.ellipse([CX - 190, CHAO + 22 - 62, CX + 190, CHAO + 22 + 62],
                  fill=(0, 0, 0, 250))
        d.rectangle([CX - 16, CHAO + 22, CX + 16, CHAO + 84], fill=(0, 0, 0, 250))
        legenda(d, "algo está te puxando...", CHAO - 210, 26, (168, 168, 168))
    else:
        img, d = fundo(0.22)
        # a escuridão orgânica (nunca preta 100%)
        pts = []
        for i in range(65):
            a = i * 2 * math.pi / 65
            ond = 1 + 0.10 * math.sin(a * 5 + 1.2) + 0.06 * math.sin(a * 11 - 0.7)
            pts.append((CX + math.cos(a) * 560 * ond * 1.15,
                        CHAO - 40 + math.sin(a) * 560 * ond))
        d.polygon(pts, fill=(4, 3, 7, 205))
        for i in range(0, 65, 1):
            a0 = i * 2 * math.pi / 65
            b0 = ((i + 1) % 65) * 2 * math.pi / 65
            ond0 = 1 + 0.10 * math.sin(a0 * 5 + 1.2) + 0.06 * math.sin(a0 * 11 - 0.7)
            ond1 = 1 + 0.10 * math.sin(b0 * 5 + 1.2) + 0.06 * math.sin(b0 * 11 - 0.7)
            d.line([(CX + math.cos(a0) * 560 * ond0 * 1.15, CHAO - 40 + math.sin(a0) * 560 * ond0),
                    (CX + math.cos(b0) * 560 * ond1 * 1.15, CHAO - 40 + math.sin(b0) * 560 * ond1)],
                   fill=(138, 10, 20, 90), width=3)
        bracos(d, 0)
        olhos(d, 0, 6)
        legenda(d, "STEVE", CHAO - 250, 42, (184, 184, 184))
        legenda(d, "FOI ENGOLIDO", CHAO - 190, 56, (208, 20, 20))
        legenda(d, "PELA ESCURIDÃO", CHAO - 132, 40, (208, 20, 20))
        img = brilho_vermelho(img, CX, CHAO + 20, 200, 0.18)
        d = ImageDraw.Draw(img, "RGBA")

    # vinheta vermelha + grão
    rand = random.Random(3)
    for i in range(260):
        x = rand.randint(0, W - 1)
        y = rand.randint(0, H - 1)
        dd = min(x, y, W - x, H - y) / 150.0
        if dd < 1:
            d.point((x, y), fill=(255, 255, 255, 8))
    for i in range(0, 150, 2):
        a = int(70 * (1 - i / 150))
        d.rectangle([0, 0, W, 0 + 3], outline=None)
    # moldura vermelha
    for k in range(90):
        a = int(90 * (1 - k / 90))
        d.rectangle([k, k, W - k, H - k], outline=(90, 8, 14, a))
    # rodapé com o nome do quadro
    f = fonte(20)
    d.rectangle([0, H - 34, W, H], fill=(0, 0, 0, 190))
    d.text((16, H - 28), titulo, font=f, fill=(190, 190, 190, 255))
    return img


def main():
    quadros = [
        quadro(1, "1/3  a poça abre embaixo dos pés — 'algo está embaixo de você'"),
        quadro(2, "2/3  o corpo estica e desce, os olhos abrem na poça"),
        quadro(3, "3/3  a escuridão sobe, 9 braços com 7 dedos seguram — FOI ENGOLIDO"),
    ]
    larg, alt = 640, 427
    saida = Image.new("RGB", (larg * 3 + 40, alt + 70), (10, 8, 12))
    d = ImageDraw.Draw(saida)
    f = fonte(26)
    d.text((20, 16), "RP Tag 5.12.0 — animação de ENGOLIR (estilo 23, protótipo uKqjYudD)",
           font=f, fill=(210, 200, 210))
    for i, q in enumerate(quadros):
        saida.paste(q.resize((larg, alt), Image.LANCZOS), (10 + i * (larg + 10), 60))
    saida.save("previa/engolir_5.12.0.png")
    print("previa/engolir_5.12.0.png")


if __name__ == "__main__":
    main()
