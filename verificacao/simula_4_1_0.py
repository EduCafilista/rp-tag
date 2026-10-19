"""Simulacao de LAYOUT dos 2 FX novos (4.1.0) em 1920x1080.

NAO e screenshot do jogo: reproduz a MATEMATICA do render (k, S, ty, cy + 25*k)
E a escala do Minecraft (glifo de 8 px de altura multiplicado por S) para provar
que a MENSAGEM cai ABAIXO do titulo nos dois FX.
"""
from PIL import Image, ImageDraw, ImageFont
import os

W, H = 1920, 1080
F1 = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
F2 = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

def sprite(txt, scale, color, bold=True):
    """Desenha o texto em alta resolucao e reduz para a geometria do Minecraft
    (altura do glifo = 8 px * scale)."""
    f = ImageFont.truetype(F1 if bold else F2, 160)
    w, h = f.getbbox(txt)[2], 200
    tmp = Image.new("RGBA", (w + 40, h), (0, 0, 0, 0))
    ImageDraw.Draw(tmp).text((10, 10), txt, font=f, fill=color)
    alvo_h = max(1, int(round(8 * scale)))
    alvo_w = max(1, int(round(tmp.width * alvo_h / h)))
    return tmp.resize((alvo_w, alvo_h), Image.LANCZOS)

def mcw(txt):
    """largura em 'pixels de fonte' do Minecraft (letras maiusculas ~6 px)."""
    return len(txt) * 6 + (len(txt) - 1) * 1.5

def render(nome, cor_titulo, cor_sub, out):
    img = Image.new("RGB", (W, H), (7, 7, 9))
    d = ImageDraw.Draw(img)
    k = min(W, H) / 240.0
    cx, cy = W / 2.0, H * 0.5
    morte = nome.startswith("MORTE")
    msg, sub = "AVISO", ("VOCE MORREU" if morte else "VOCE ESTA SENDO SEGUIDO")

    tot = mcw(msg)
    S = min(k * (1.9 if morte else 1.7), W * 0.86 / tot)
    ty = (cy + 6 * k - 4 * S) if morte else (cy - 4 * S)

    # titulo, letra a letra (como o mod faz)
    xs, acc = [], 0.0
    for ch in msg:
        xs.append(cx - (tot * S) / 2 + acc * S)
        acc += 6 + 1.5
    for i, ch in enumerate(msg):
        sp = sprite(ch, S, cor_titulo)
        img.paste(sp, (int(xs[i]), int(ty + (8 * S - sp.height))), sp)

    # MENSAGEM: regra 3.96.4 -> (cx - w*S2/2, cy + 25*k)
    S2 = min(k * 0.75, W * 0.85 / mcw(sub))
    sp2 = sprite(sub, S2, cor_sub, bold=False)
    img.paste(sp2, (int(cx - sp2.width / 2), int(cy + 25 * k)), sp2)

    d = ImageDraw.Draw(img)
    y_sub = cy + 25 * k
    d.line([(0, y_sub), (W, y_sub)], fill=(46, 46, 54), width=1)
    if morte:
        d.line([(0, H * 0.80), (W, H * 0.80)], fill=(34, 34, 40), width=1)
        d.text((W - 470, H * 0.80 + 6), "monitor cardiaco (0.80h)", font=ImageFont.truetype(F2, 20), fill=(80, 80, 90))
    d.text((24, H - 74), f"{nome}  —  verificacao de LAYOUT (simulacao, nao screenshot)  1920x1080  k=4.5",
           font=ImageFont.truetype(F2, 21), fill=(125, 125, 135))
    d.text((24, H - 46), f"titulo (AVISO) topo em y={ty:.0f}  altura {8*S:.0f}px   |   MENSAGEM em y={y_sub:.0f}  (sempre ABAIXO)",
           font=ImageFont.truetype(F2, 21), fill=(125, 125, 135))
    d.text((24, H - 20), "regra 3.96.4: translate(cx - w*S2/2, cy + 25*k)  •  S2 = min(k*0.75, w*0.85/width)",
           font=ImageFont.truetype(F2, 21), fill=(90, 90, 100))
    img.save(out)

os.makedirs("verificacao", exist_ok=True)
render("MORTE — Linha Reta (4.1.0)", (210, 30, 40), (225, 218, 195), "verificacao/4_1_0_morte_layout.png")
render("PESADELO — novo (4.1.0)", (255, 32, 32), (150, 150, 160), "verificacao/4_1_0_pesadelo_layout.png")
for f in ["verificacao/4_1_0_morte_layout.png", "verificacao/4_1_0_pesadelo_layout.png"]:
    print(f, os.path.getsize(f), "bytes")
