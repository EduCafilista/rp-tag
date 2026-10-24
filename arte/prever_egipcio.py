"""Previa do aviso EGIPCIO 4.7.6: o quadro do video + a placa/texto como o Java desenha.

Uso: python3 arte/prever_egipcio.py "<mensagem>"
Grava /home/user/previa/egipcio_4_7_6.gif e .png (um quadro cheio).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FRAMES = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "fx", "egipcio", "frames")
LARG, ALT = 1280, 720

# ---- o MESMO layout do EgipcioAvisoFx (modo video) ----
CX, CY = LARG / 2, ALT * 0.783
K = min(LARG, ALT) / 240.0


def fonte(px):
    for caminho in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ):
        if os.path.exists(caminho):
            return ImageFont.truetype(caminho, px)
    return ImageFont.load_default()


def desenhar(img, msg):
    d = ImageDraw.Draw(img)
    S = K * 1.75
    f = fonte(int(S * 6))
    # largura aproximada de cada caractere (a fonte do Minecraft e 6px de grade)
    w = [f.getlength(c) / (S * 6) * 6 for c in msg]
    tot = sum(w) + 1.5 * (len(msg) - 1)
    hw = tot * S / 2 + 34 * K
    hh = 4.6 * S + 9 * K
    r = 8 * K

    # placa (fundo escuro, cantos cortados) — igual ao cartucho()
    caixa = Image.new("RGBA", img.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(caixa)
    cd.rounded_rectangle([CX - hw, CY - hh, CX + hw, CY + hh], radius=r,
                         fill=(0x0A, 0x13, 0x30, 205))
    cd.rounded_rectangle([CX - hw, CY - hh, CX + hw, CY + hh], radius=r,
                         outline=(0xE8, 0xC7, 0x66, 225), width=3)
    cd.rounded_rectangle([CX - hw + 3 * K, CY - hh + 3 * K, CX + hw - 3 * K, CY + hh - 3 * K],
                         radius=r, outline=(0xB8, 0x86, 0x2A, 150), width=1)
    # cantos em losango
    for sx in (-1, 1):
        for sy in (-1, 1):
            x, y = CX + sx * hw, CY + sy * hh
            cd.polygon([(x - 3.4 * K, y), (x, y - 3.4 * K), (x + 3.4 * K, y), (x, y + 3.4 * K)],
                       fill=(0xE8, 0xC7, 0x66, 235))
    # selo à direita
    sx0 = CX + hw + 4 * K
    cd.rectangle([sx0, CY - 4.5 * K, sx0 + 9 * K, CY + 4.5 * K], fill=(0xFF, 0xEB, 0xAA, 205))
    cd.rectangle([sx0 + 1.7 * K, CY - 2.8 * K, sx0 + 7.3 * K, CY + 2.8 * K], fill=(0x0A, 0x13, 0x30, 220))
    img.alpha_composite(caixa)

    # texto dourado com contorno escuro
    x0 = CX - tot * S / 2
    acc = 0.0
    for i, c in enumerate(msg):
        px = x0 + acc * S
        acc += w[i] + 1.5
        for ox, oy in ((-2, 0), (2, 0), (0, -2), (0, 2)):
            d.text((px + ox, CY + oy), c, font=f, fill=(0x2A, 0x1A, 0x05, 235), anchor="lm")
        d.text((px, CY), c, font=f, fill=(0xE8, 0xC7, 0x66, 255), anchor="lm")
        d.text((px, CY - 2), c, font=f, fill=(0xFF, 0xEB, 0xAA, 120), anchor="lm")
    return img


def main():
    msg = sys.argv[1] if len(sys.argv) > 1 else "O Faraó observa"
    quadros = [60, 72, 84, 96, 108, 120, 132, 144, 156]
    gif = []
    for q in quadros:
        base = Image.open(os.path.join(FRAMES, "%04d_c.jpg" % q)).convert("RGBA")
        alfa = Image.open(os.path.join(FRAMES, "%04d_a.jpg" % q)).convert("L")
        base.putalpha(alfa)
        fundo = Image.new("RGBA", base.size, (0x0B, 0x0D, 0x1A, 255))
        img = Image.alpha_composite(fundo, base)
        gif.append(desenhar(img, msg).convert("P", palette=Image.ADAPTIVE, colors=128))

    saida = "/home/user/previa"
    os.makedirs(saida, exist_ok=True)
    gif[4].save(os.path.join(saida, "egipcio_4_7_6.png"))
    gif[0].save(os.path.join(saida, "egipcio_4_7_6.gif"), save_all=True,
                append_images=gif[1:], duration=180, loop=0, optimize=True)
    print("ok:", saida)


if __name__ == "__main__":
    main()
