# -*- coding: utf-8 -*-
"""Simula o desenho dos 5 FX usando a MESMA matematica do codigo Java.
Objetivo: verificar visualmente que a mensagem fica EMBAIXO do titulo AVISO."""
import math
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
W, H = 1920, 1080           # resolucao de referencia (1080p)
TITULO = "AVISO"
MSG = "VOCE NAO ESTA MAIS SOZINHO"

FXS = [  # nome, cx% , cy%, cor titulo, cor msg, bg, wobble(S), reflexo?
    ("pesadelo",  0.45, (200,180,230), (200,180,230), (24,10,32),  2.0, False),
    ("divino",    0.42, (255,240,200), (255,240,200), (18,20,34),  0.0, False),
    ("void",      0.45, (220,210,255), (180,160,255), (8,6,22),    0.0, True),
    ("sangue",    0.45, (255,150,150), (255,170,170), (26,6,8),    0.0, False),
    ("demoniaco", 0.45, (255,150,120), (255,150,120), (20,10,10),  0.0, False),
]

def medir(nome):
    k = min(W, H) / 240.0
    S = k * 1.9
    return k, S

scale_up = 0.5  # renderizo 1080p e reduzo na montagem

def render(nome, cyp, cTit, cMsg, bg, wob, refl):
    k = min(W, H) / 240.0
    S = k * 1.9
    cx, cy = W/2.0, H * cyp
    ty = cy - 4 * S
    img = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(img, "RGBA")

    # --- titulo letra a letra (igual ao Java: fonte 9px escalada por S) ---
    f9 = ImageFont.truetype(FONT, 9)
    fs = ImageFont.truetype(FONT, max(8, int(round(9 * S))))
    # largura no "fonte 9px" do MC ~ f.width; uso medicao real do PIL
    larguras = [f9.getlength(ch) for ch in TITULO]
    sp = 2.5
    tot = sum(larguras) + sp * (len(TITULO) - 1)
    x0 = cx - tot * S / 2.0
    acc = 0.0
    bottoms = []
    for i, ch in enumerate(TITULO):
        cw = larguras[i]
        dy = ty + 4 * S + wob * S * math.sin(2.6 + i * 1.9)     # wobble pesadelo (fase de t=2.5s)
        if nome == "void":
            dy = ty + 3 * k * math.sin(2.5 * 1.3 + i * 0.9)      # bob do void
        px = x0 + acc * S
        f = fs
        bb = d.textbbox((0, 0), ch, font=f)
        gw, gh = bb[2]-bb[0], bb[3]-bb[1]
        d.text((px, dy - bb[1]), ch, font=f, fill=cTit + (255,))
        bottoms.append(dy - bb[1] + gh)
        if refl:  # reflexo invertido (ty + 15S), espelhado e fraco
            rr = Image.new("RGBA", (int(gw)+4, int(gh)+4), (0,0,0,0))
            dd = ImageDraw.Draw(rr)
            dd.text((2 - bb[0], 2 - bb[1]), ch, font=f, fill=cTit + (70,))
            rr = rr.transpose(Image.FLIP_TOP_BOTTOM)
            # codigo Java: translate(px, ty + 15*S) + scale(S, -S) ->
            # o glifo espelhado ocupa [ty+15S-h, ty+15S]
            ry = ty + 15 * S - rr.size[1]
            img.paste(rr, (int(px) - 2, int(ry)), rr)
        acc += cw + sp
    titulo_base = max(bottoms)

    # --- mensagem: regra 3.96.4 = cy + 25*k, escala S2 = 0.75k ---
    S2 = min(k * 0.75, W * 0.85 / max(1, len(MSG) * 4.2))
    fmsg = ImageFont.truetype(FONT, max(8, int(round(9 * S2))))
    bb = d.textbbox((0, 0), MSG, font=fmsg)
    mw = bb[2] - bb[0]
    msg_top = cy + 25 * k
    d.text((cx - mw/2 - bb[0], msg_top - bb[1]), MSG, font=fmsg, fill=cMsg + (235,))

    # --- anotacoes de verificacao ---
    y1, y2 = titulo_base, msg_top
    for x0l, x1l in [(cx - 420, cx + 420)]:
        # linha da base do titulo
        for xx in range(int(x0l), int(x1l), 16):
            d.line([(xx, y1), (xx+8, y1)], fill=(90, 220, 120, 180), width=2)
        for xx in range(int(x0l), int(x1l), 16):
            d.line([(xx, y2), (xx+8, y2)], fill=(255, 210, 60, 200), width=2)
    d.line([(cx - 460, y1), (cx - 420, y1)], fill=(90, 220, 120, 255), width=3)
    d.line([(cx - 460, y2), (cx - 420, y2)], fill=(255, 210, 60, 255), width=3)
    d.line([(cx - 440, y1), (cx - 440, y2)], fill=(255, 255, 255, 160), width=2)
    fleg = ImageFont.truetype(FONT, 30)
    d.text((cx - 430, (y1 + y2)/2 - 20), f"{y2 - y1:.0f}px", font=fleg, fill=(255,255,255,235))
    d.text((60, 40), nome.upper(), font=ImageFont.truetype(FONT, 52), fill=cTit + (255,))
    d.text((60, H - 70), "linha amarela = topo da MENSAGEM   |   verde = base do AVISO",
           font=ImageFont.truetype(FONT, 26), fill=(200,200,200,220))
    img = img.resize((int(W*scale_up), int(H*scale_up)), Image.LANCZOS)
    return img, (y1, y2, (y2-y1)/min(W,H)*240)

panels, dados = [], []
for nome, cyp, ct, cm, bg, wob, refl in FXS:
    im, dados_fx = render(nome, cyp, ct, cm, bg, wob, refl)
    panels.append(im); dados.append((nome, dados_fx))
    print(f"{nome:<10} base_titulo={dados_fx[0]:7.1f}px  topo_msg={dados_fx[1]:7.1f}px  folga={dados_fx[2]:5.1f}k")

# montagem: 5 paineis empilhados
pw, ph = panels[0].size
out = Image.new("RGB", (pw, ph*5 + 0), (0,0,0))
for i, p in enumerate(panels):
    out.paste(p, (0, i*ph))
out.save("/home/user/verificacao/avisos_verificacao_1080p.png")
print("salvo:", out.size)
