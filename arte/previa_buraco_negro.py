#!/usr/bin/env python3
"""Prévia OFFLINE do buraco negro de entrar no vazio (5.38.0).

Porta a MESMA matemática de `client/BuracoNegroFx.java` para PIL, desenha 12 quadros da
entrada (u = 0 → 1) e as 4 fases da íris de saída. Serve pra conferir o desenho sem abrir o jogo.

Uso: python3 arte/previa_buraco_negro.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
W, H = 480, 270            # 16:9, mesma proporção do jogo
TAU = 6.2831853


def cl(x):
    return 0.0 if x < 0 else min(1.0, x)


def ss(a, b, x):
    u = cl((x - a) / (b - a))
    return u * u * (3 - 2 * u)


def eo(x):
    return 1 - (1 - x) ** 3


def fr(x):
    return x - math.floor(x)


def rnd(i, s):
    return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)


def mix(c1, c2, u):
    u = cl(u)
    r = int(((c1 >> 16) & 255) * (1 - u) + ((c2 >> 16) & 255) * u)
    g = int(((c1 >> 8) & 255) * (1 - u) + ((c2 >> 8) & 255) * u)
    b = int((c1 & 255) * (1 - u) + (c2 & 255) * u)
    return (r, g, b)


def vinheta(img, d, cor, a):
    """vinheta radial (escurece as bordas)."""
    if a <= 0.003:
        return
    cam = Image.new("RGB", (img.width, img.height), cor)
    mask = Image.new("L", (img.width, img.height), 0)
    des = ImageDraw.Draw(mask)
    cx, cy = img.width / 2, img.height / 2
    passos = 26
    for i in range(passos + 1):
        r = d + (max(img.width, img.height) * 1.2 - d) * i / passos
        v = int(255 * min(1.0, (i / passos) ** 1.4) * min(1.0, a))
        des.ellipse([cx - r, cy - r, cx + r, cy + r], fill=v)
    img.paste(Image.composite(cam, img, mask), (0, 0))


def brilho(img, cx, cy, r, cor, a):
    if a <= 0.004 or r <= 1:
        return
    cam = Image.new("RGB", (img.width, img.height), cor)
    mask = Image.new("L", (img.width, img.height), 0)
    des = ImageDraw.Draw(mask)
    passos = 22
    for i in range(passos + 1):
        rr = r * (1 - i / passos)
        v = int(255 * min(1.0, a) * (i / passos) ** 1.2)
        des.ellipse([cx - rr, cy - rr * 0.98, cx + rr, cy + rr * 0.98], fill=v)
    mask = mask.filter(ImageFilter.GaussianBlur(max(1, int(r * 0.10))))
    img.paste(Image.composite(cam, img, mask), (0, 0))


def anel(des, cx, cy, r, n, w, rot, cor, a):
    if a <= 0.004:
        return
    for i in range(n):
        ang = TAU * i / n + rot
        x = cx + math.cos(ang) * r
        y = cy + math.sin(ang) * r
        des.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=cor + (int(255 * cl(a)),))


def desenhar_buraco(u, semente=12345):
    img = Image.new("RGB", (W, H), (12, 16, 22))
    # "mundo" de referência: um chão e um céu, pra dar pra ver a vinheta comendo a cena
    des = ImageDraw.Draw(img)
    des.rectangle([0, 0, W, H * 0.55], fill=(96, 140, 190))
    des.rectangle([0, H * 0.55, W, H], fill=(70, 116, 64))
    cx, cy = W * 0.5, H * 0.47
    esc = min(W, H)

    fechando = ss(0.0, 0.85, u)
    vinheta(img, esc * (1.05 - 0.50 * fechando), (4, 2, 9), 0.46 + 0.48 * fechando)

    nascendo = eo(cl(u * 1.15))
    engolir = ss(0.70, 1.0, u)
    rcore = esc * (0.010 + 0.175 * nascendo) * (1.0 + 7.5 * engolir * engolir)

    brilho(img, cx, cy, rcore * 3.6, (58, 31, 116), min(1.0, 0.12 + 0.16 * u))
    brilho(img, cx, cy, rcore * 2.2, (122, 79, 208), min(1.0, 0.13 + 0.15 * u))
    brilho(img, cx, cy, rcore * 1.35, (201, 168, 255), min(1.0, 0.12 + 0.16 * u))

    des = ImageDraw.Draw(img, "RGBA")
    # espirais
    for i in range(26):
        s1 = rnd(i * 37 + 3, semente & 0xFFFF)
        s2 = rnd(i * 53 + 7, (semente >> 7) & 0xFFFF)
        fase = fr(s1 + u * (0.9 + s2 * 0.9))
        dist = esc * (0.62 * (1.0 - fase) + 0.035)
        if dist < rcore * 1.02:
            continue
        ang = s2 * TAU + fase * 5.4 + u * 2.2
        comp = esc * (0.055 + 0.075 * (1.0 - fase))
        x0, y0 = cx + math.cos(ang) * dist, cy + math.sin(ang) * dist * 0.72
        ang2 = ang + 0.22 + 0.30 * (1.0 - fase)
        d2 = dist * (1.0 - comp / esc * 2.2)
        x1, y1 = cx + math.cos(ang2) * d2, cy + math.sin(ang2) * d2 * 0.72
        a = (0.16 + 0.34 * fase) * (0.4 + 0.6 * u)
        cor = mix(0xB79BFF, 0xFFF2C8, fase)
        des.line([x0, y0, x1, y1], fill=cor + (int(255 * cl(a)),),
                 width=max(1, int(esc * 0.0022 * (1.4 - fase))))

    # discos de acreção (bandas concêntricas + flares, igual ao Java)
    for (mul, achat, fria, quente, peso) in (
            (2.35, 0.30, 0x3A1155, 0xFF9BE0, 1.00),
            (1.72, 0.22, 0x6B2CFF, 0xFFE6A8, 0.80),
            (1.34, 0.15, 0x9C7BFF, 0xFFFFFF, 0.55)):
        rx_base = rcore * mul + esc * 0.012
        giro = u * (2.4 + mul * 1.1)
        for anel_fino in range(7):
            q = anel_fino / 6.0
            rx = rx_base * (1.0 + 0.26 * q)
            ry = rx * achat + esc * 0.004
            alfa_anel = peso * (0.42 - 0.30 * q) * (0.35 + 0.65 * u)
            for i in range(64):
                a0 = TAU * i / 64 + giro * (1.0 - 0.35 * q)
                a1 = TAU * (i + 1) / 64 + giro * (1.0 - 0.35 * q)
                doppler = 0.38 + 0.62 * max(0.0, math.cos(a0 - 0.7))
                cor = mix(fria, quente, 0.20 + 0.80 * doppler)
                alfa = alfa_anel * (0.35 + 0.85 * doppler)
                des.line([cx + math.cos(a0) * rx, cy + math.sin(a0) * ry,
                          cx + math.cos(a1) * rx, cy + math.sin(a1) * ry],
                         fill=cor + (int(255 * cl(alfa)),),
                         width=max(2, int(esc * (0.006 + 0.004 * q))))
        for sinal in (-1.0, 1.0):
            brilho(img, cx + sinal * rx_base * 1.05, cy, rx_base * 0.34,
                   quente, peso * 0.34 * (0.35 + 0.65 * u))

    # horizonte de eventos
    des.ellipse([cx - rcore, cy - rcore, cx + rcore, cy + rcore], fill=(0, 0, 0))

    # anel de fótons
    pulso = 0.80 + 0.20 * math.sin(u * 22.0)
    anel(des, cx, cy, rcore * 1.045, 108, max(1.6, esc * 0.0045), u * 3.0,
         (237, 232, 255), 0.90 * pulso)
    anel(des, cx, cy, rcore * 1.11, 96, max(1.0, esc * 0.0022), -u * 5.0,
         (183, 155, 255), 0.55 * pulso)

    # engolir
    if engolir > 0.001:
        cobre = engolir * engolir
        camada = Image.new("RGBA", (W, H), (0, 0, 0, int(255 * cl(cobre))))
        img.paste(Image.alpha_composite(img.convert("RGBA"), camada).convert("RGB"), (0, 0))
        des = ImageDraw.Draw(img, "RGBA")
        borda = rcore * 1.02
        if borda < esc * 2.2:
            anel(des, cx, cy, borda, 120, max(2.0, esc * 0.006) * (1 - 0.5 * engolir),
                 u * 4.0, (255, 243, 214), 0.55 * (1 - cobre))
    return img


def desenhar_iris(u):
    img = Image.new("RGB", (W, H), (10, 12, 18))
    des = ImageDraw.Draw(img)
    des.rectangle([0, 0, W, H], fill=(26, 22, 34))     # o vazio (referência)
    cx, cy = W * 0.5, H * 0.47
    esc = min(W, H)
    abre = ss(0.0, 1.0, u)
    buraco = esc * 0.02 + esc * 1.75 * abre * abre
    diag = math.hypot(W, H)
    fora = max(diag, buraco) * 1.15

    camada = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d2 = ImageDraw.Draw(camada)
    # anel preto em 96 fatias (mesmo desenho do Java)
    for i in range(96):
        a0 = TAU * i / 96
        a1 = TAU * (i + 1) / 96
        d2.polygon([(cx + math.cos(a0) * buraco, cy + math.sin(a0) * buraco),
                    (cx + math.cos(a1) * buraco, cy + math.sin(a1) * buraco),
                    (cx + math.cos(a1) * fora, cy + math.sin(a1) * fora),
                    (cx + math.cos(a0) * fora, cy + math.sin(a0) * fora)],
                   fill=(2, 1, 4, 255))
    img = Image.alpha_composite(img.convert("RGBA"), camada).convert("RGB")
    des = ImageDraw.Draw(img, "RGBA")
    anel(des, cx, cy, max(1.5, buraco), 108, max(1.6, esc * 0.005), 0.0,
         (201, 176, 255), 0.62 * (1 - abre * 0.75))
    brilho(img, cx, cy, buraco * 1.35 + esc * 0.02, (108, 59, 214), 0.10 * (1 - abre))
    if abre > 0.85:
        sobra = (abre - 0.85) / 0.15
        camada = Image.new("RGBA", (W, H), (2, 1, 4, int(140 * sobra)))
        img = Image.alpha_composite(img.convert("RGBA"), camada).convert("RGB")
    return img


def main():
    us = [0.08, 0.22, 0.36, 0.48, 0.58, 0.66, 0.74, 0.82, 0.88, 0.94, 1.0]
    quadros = [desenhar_buraco(u) for u in us]
    # tira: 3 linhas x 4 colunas (11 entrada + 1 íris no fim)
    cols, rows = 4, 3
    tw, th = W // 2, H // 2
    folha = Image.new("RGB", (cols * (tw + 8) + 8, rows * (th + 22) + 8), (14, 12, 18))
    des = ImageDraw.Draw(folha)
    for i, q in enumerate(quadros):
        col, row = i % cols, i // cols
        x = 8 + col * (tw + 8)
        y = 22 + row * (th + 22)
        folha.paste(q.resize((tw, th)), (x, y))
        des.text((x, y - 14), "entrada  u=%.2f" % us[i], fill=(210, 200, 230))
    # a íris nas 4 fases
    x0 = 8 + 3 * (tw + 8)
    y0 = 22 + 2 * (th + 22)
    iris_tira = Image.new("RGB", (tw, th), (0, 0, 0))
    for k, u in enumerate((0.05, 0.45, 0.75, 1.0)):
        iris_tira.paste(desenhar_iris(u).resize((tw // 2, th // 2)),
                        ((k % 2) * (tw // 2), (k // 2) * (th // 2)))
    folha.paste(iris_tira, (x0, y0))
    des.text((x0, y0 - 14), "íris de saída (chegando no vazio)", fill=(210, 200, 230))
    saida = os.path.join(RAIZ, "arte", "previa_buraco_negro.png")
    folha.save(saida)
    print("prévia:", saida, folha.size)


if __name__ == "__main__":
    main()
