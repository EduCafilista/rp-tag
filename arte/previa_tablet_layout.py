#!/usr/bin/env python3
"""Prévia da composição do TabletScreen (5.2.0) usando a MESMA geometria do TabletLayout.

Não é captura do jogo: reproduz as primitivas (gradiente, estrelas, buraco negro,
campo, botões, status) com as medidas calculadas pelo layout real, para conferir
equilíbrio e se nada se sobrepõe em cada escala de GUI.
"""
from PIL import Image, ImageDraw, ImageFont

FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONTE_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

MIN_CHARACTER_WIDTH = 430


def layout(sw, sh):
    """Réplica fiel de TabletLayout.of + bottomBar/fieldTop/fieldHeight."""
    w = max(200, sw)
    h = max(140, sh)
    right = max(12, w // 24)
    character = w >= MIN_CHARACTER_WIDTH
    if character:
        form_x = max(126, min(w * 54 // 100, w - right - 168))
        form_w = w - right - form_x
        if form_w < 168:
            form_x = max(96, w - right - 168)
            form_w = w - right - form_x
    else:
        form_x = max(8, w // 24)
        form_w = max(96, w - right - form_x)
    radius = max(9, min(form_w * 21 // 100, (h - 60) // 7)) if character else 0
    bottom = h - 39
    return dict(x=0, y=0, w=w, h=h, form_x=form_x, form_w=form_w, radius=radius,
                character=character, bottom=bottom)


def field_top(l, title):
    return min(50 + title, l["bottom"] - 36)


def field_h(l, title):
    return max(20, l["bottom"] - 26 - field_top(l, title))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def desenhar(l, etapa, nome, pergunta, rotulo, dica, texto, status, escala=2):
    ss = escala * 2  # supersampling para o texto não sair serrilhado
    img = Image.new("RGB", (l["w"] * ss, l["h"] * ss))
    d = ImageDraw.Draw(img)
    # fundo: fillGradient 0xff0e0b1a -> 0xff070b13
    topo, base = (0x0e, 0x0b, 0x1a), (0x07, 0x0b, 0x13)
    for yy in range(l["h"]):
        d.rectangle([0, yy * ss, l["w"] * ss, yy * ss + ss], fill=lerp(topo, base, yy / l["h"]))

    if l["character"]:
        for i in range(75):
            sx = 5 + (i * 173 + i * i * 13) % max(1, l["w"] - 10)
            sy = 5 + (i * 97 + i * i * 7) % max(1, l["h"] - 10)
            d.rectangle([sx * ss, sy * ss, sx * ss + ss, sy * ss + ss], fill=(0x4f, 0x52, 0x6d))
        d.text((15 * ss, 12 * ss), "Neptune SMP", font=ImageFont.truetype(FONTE, 8 * ss), fill=(0xc9, 0xc2, 0xd3))
    else:
        d.text((15 * ss, 12 * ss), "QuiralOS", font=ImageFont.truetype(FONTE, 8 * ss), fill=(0xc9, 0xc2, 0xd3))

    if l["character"] and l["radius"] > 0:
        left = max(0, l["form_x"] - 18)
        r = l["radius"]
        cx = left // 2 + 8
        cy = max(48, (l["h"] - 20) // 3) - r // 3
        halo = r + 5
        for dy in range(-halo, halo + 1):
            ext = int((max(0, halo * halo - dy * dy)) ** 0.5)
            d.rectangle([(cx - ext) * ss, (cy + dy) * ss, (cx + ext + 1) * ss - 1, (cy + dy + 1) * ss - 1],
                        fill=(0x26, 0x1c, 0x22))
        for dy in range(-r, r + 1):
            ext = int((max(0, r * r - dy * dy)) ** 0.5)
            d.rectangle([(cx - ext) * ss, (cy + dy) * ss, (cx + ext + 1) * ss - 1, (cy + dy + 1) * ss - 1],
                        fill=(0xe7, 0x5a, 0x24))
            ir = r - 2
            if abs(dy) <= ir:
                ext2 = int((max(0, ir * ir - dy * dy)) ** 0.5)
                d.rectangle([(cx - ext2) * ss, (cy + dy) * ss, (cx + ext2 + 1) * ss - 1, (cy + dy + 1) * ss - 1],
                            fill=(0x02, 0x04, 0x0a))
        for dx in range(-r - r // 3, r + r // 3 + 1):
            yy = cy - dx // 4
            d.rectangle([(cx + dx) * ss, (yy - 3) * ss, (cx + dx + 1) * ss - 1, (yy + 4) * ss - 1], fill=(0x7a, 0x33, 0x25))
            d.rectangle([(cx + dx) * ss, (yy - 2) * ss, (cx + dx + 1) * ss - 1, (yy + 3) * ss - 1], fill=(0xed, 0x6d, 0x25))
            d.rectangle([(cx + dx) * ss, (yy - 1) * ss, (cx + dx + 1) * ss - 1, (yy + 2) * ss - 1], fill=(0xff, 0xd5, 0x85))
        # silhueta do personagem (o jogo desenha a skin real)
        px, py = cx, cy + r + 16
        d.ellipse([(px - 5) * ss, (py - 16) * ss, (px + 5) * ss, (py - 5) * ss], fill=(0x2b, 0x2f, 0x45))
        d.rounded_rectangle([(px - 8) * ss, (py - 5) * ss, (px + 8) * ss, (py + 16) * ss], radius=4 * ss, fill=(0x2b, 0x2f, 0x45))
        f = ImageFont.truetype(FONTE, 8 * ss)
        txt = nome if len(nome) < 18 else nome[:17] + "…"
        d.text((cx * ss, (l["h"] - 23) * ss), txt, font=f, fill=(0xd4, 0xcd, 0xdc), anchor="ma")

    fx, fw = l["form_x"], l["form_w"]
    f8 = ImageFont.truetype(FONTE, 8 * ss)
    f10 = ImageFont.truetype(FONTE, 9 * ss)
    estagio = f"{etapa + 1} / 6   {rotulo.upper()}"
    d.text((fx * ss, 32 * ss), estagio, font=f8, fill=(0xb8, 0xa6, 0xc7))
    title = 0
    if l["h"] >= 168:
        d.text((fx * ss, 48 * ss), pergunta, font=f10, fill=(0xf0, 0xea, 0xf5))
        title = 20
    fy = field_top(l, title)
    fh = field_h(l, title)
    if etapa < 5:
        d.text((fx * ss, (fy - 12) * ss), rotulo, font=f8, fill=(0xc6, 0xbd, 0xcf))
        if etapa < 2:
            d.rectangle([fx * ss, (fy + 11) * ss, (fx + fw) * ss - 1, (fy + 37) * ss - 1], fill=(0x14, 0x17, 0x24))
            d.rectangle([fx * ss, (fy + 11) * ss, (fx + fw) * ss - 1, (fy + 37) * ss - 1], outline=(0x37, 0x34, 0x45))
            d.text(((fx + 7) * ss, (fy + 16) * ss), texto or dica, font=f8,
                   fill=(0xee, 0xe7, 0xf3) if texto else (0x66, 0x62, 0x74))
        else:
            d.rectangle([fx * ss, (fy + 11) * ss, (fx + fw) * ss - 1, (fy + 11 + max(22, fh - 12)) * ss - 1],
                        fill=(0x14, 0x17, 0x24))
            d.rectangle([fx * ss, (fy + 11) * ss, (fx + fw) * ss - 1, (fy + 11 + max(22, fh - 12)) * ss - 1],
                        outline=(0x37, 0x34, 0x45))
            d.text(((fx + 6) * ss, (fy + 18) * ss), texto or dica, font=f8,
                   fill=(0xee, 0xe7, 0xf3) if texto else (0x66, 0x62, 0x74))
    # status
    d.text((fx * ss, (l["bottom"] - 20) * ss), status, font=ImageFont.truetype(FONTE, 7 * ss), fill=(0xa7, 0xac, 0xae))
    # botões
    by = l["bottom"]
    d.rectangle([fx * ss, by * ss, (fx + 66) * ss - 1, (by + 22) * ss - 1], fill=(0x15, 0x17, 0x24))
    d.text(((fx + 33) * ss, (by + 7) * ss), "Voltar", font=f8, fill=(0xde, 0xd6, 0xe7), anchor="ma")
    bw = min(92, fw - 76)
    d.rectangle([(fx + fw - bw) * ss, by * ss, (fx + fw) * ss - 1, (by + 22) * ss - 1], fill=(0xef, 0xb1, 0x7b))
    d.text(((fx + fw - bw // 2) * ss, (by + 7) * ss), "Continuar", font=f8, fill=(0x24, 0x18, 0x11), anchor="ma")
    # pontos de etapa
    dx0 = fx + max(0, (fw - 120) // 2)
    for i in range(6):
        cx = dx0 + i * 20 + 9
        cy = l["h"] - 15 + 6
        s = 6 if i == etapa else 4
        cor = (0xef, 0xb1, 0x7b) if i == etapa else (0x8e, 0x80, 0x9e)
        d.rectangle([(cx - s // 2) * ss, (cy - s // 2) * ss, (cx + s // 2) * ss, (cy + s // 2) * ss], fill=cor)
    return img.resize((l["w"] * escala, l["h"] * escala), Image.LANCZOS)


CENAS = [
    (320, 240, "320x240 (GUI grande / janela pequena)"),
    (427, 240, "427x240 (escala 3 num 1280x720)"),
    (640, 360, "640x360 (escala 2 num 1280x720)"),
    (854, 480, "854x480 (escala 2 num 1080p)"),
]

def main():
    imagens = []
    for w, h, rotulo in CENAS:
        l = layout(w, h)
        imagens.append((rotulo, desenhar(l, 2, "Ana Beatriz", "De onde você veio?",
                                        "Origem", "Seu lugar de origem",
                                        "Uberlândia, Minas Gerais", "Salvo no servidor")))
    largura = max(i.width for _, i in imagens) + 40
    altura = sum(i.height + 34 for _, i in imagens) + 20
    fora = Image.new("RGB", (largura, altura), (0x06, 0x06, 0x0a))
    d = ImageDraw.Draw(fora)
    d.text((20, 8), "Tablet Neptune 5.2.0 — composição por escala de GUI (geometria real do layout)",
           font=ImageFont.truetype(FONTE, 15), fill=(0xe8, 0xdf, 0xf0))
    y = 32
    for rotulo, img in imagens:
        d.text((20, y), rotulo, font=ImageFont.truetype(FONTE_MONO, 12), fill=(0x9c, 0x92, 0xae))
        fora.paste(img, (20, y + 16))
        y += img.height + 34
    fora.save("/home/user/previa/tablet_520_layout.png")
    print("ok /home/user/previa/tablet_520_layout.png", fora.size)


if __name__ == "__main__":
    main()
