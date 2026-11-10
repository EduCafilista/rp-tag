#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Prévia do LEQUE (pedido 90) — desenha a roda exatamente com a matemática do
PoderLeque.render (mesmos raios, mesmos tamanhos de ícone e de número), para o
dono ver como ficou antes de abrir o jogo.

Saída: /home/user/previa_leque_5.33.0.png
"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

RAIZ = "/home/user/rp-tag/src/main/resources/assets/rptag/textures/gui"
SAIDA = "/home/user/previa_leque_5.33.0.png"

LADO = 620                       # ladoRoda() no jogo (px de GUI)
RAIO = LADO / 2.0
NOMES = ["RAIO", "ORBE", "ONDA", "OLHOS", "PENAS", "BANIR"]
ARQ = ["raio", "orbe", "onda", "olhos", "penas", "banir"]
APONTADO = 1                     # o mouse em cima do ORBE

# fundo de jogo (céu escuro com um chão qualquer)
W, H = 900, 760
img = Image.new("RGBA", (W, H), (18, 16, 26, 255))
d = ImageDraw.Draw(img)
for y in range(H // 2):
    t = y / (H / 2)
    d.line([(0, y), (W, y)], fill=(int(30 - 18 * t), int(22 - 12 * t), int(44 - 22 * t), 255))
d.rectangle([0, H // 2, W, H], fill=(30, 34, 30, 255))

cx, cy = W / 2.0, H / 2.0 + 6

# brilho atrás da casa apontada
brilho = Image.open(os.path.join(RAIZ, "leque_brilho.png")).convert("RGBA")
ang = -math.pi / 2 + APONTADO * (math.pi / 3.0)
bx = cx + math.cos(ang) * LADO * 0.63 * 0.5
by = cy + math.sin(ang) * LADO * 0.63 * 0.5
gs = int(RAIO * 0.66)
b2 = brilho.resize((gs, gs), Image.LANCZOS)
img.alpha_composite(b2, (int(bx - gs / 2), int(by - gs / 2)))

# a roda
roda = Image.open(os.path.join(RAIZ, "leque_roda.png")).convert("RGBA").resize((LADO, LADO), Image.LANCZOS)
img.alpha_composite(roda, (int(cx - LADO / 2), int(cy - LADO / 2)))

try:
    fonte = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
    fonte_peq = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    fonte_tit = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 17)
except Exception:
    fonte = fonte_peq = fonte_tit = ImageFont.load_default()

# as seis casas
for i, nome in enumerate(ARQ):
    a = -math.pi / 2 + i * (math.pi / 3.0)
    px = cx + math.cos(a) * LADO * 0.63 * 0.5
    py = cy + math.sin(a) * LADO * 0.63 * 0.5
    escolhida = (i == APONTADO)
    tam = int(LADO * 0.105 * (1.18 if escolhida else 1.0))
    ic = Image.open(os.path.join(RAIZ, "leque_%s.png" % nome)).convert("RGBA").resize((tam, tam), Image.LANCZOS)
    img.alpha_composite(ic, (int(px - tam / 2), int(py - tam / 2)))
    # o número no aro
    nx = cx + math.cos(a) * RAIO * 0.845
    ny = cy + math.sin(a) * RAIO * 0.845
    cor = (240, 210, 255, 255) if escolhida else (150, 100, 220, 255)
    d.text((nx, ny - 6), str(i + 1), font=fonte, fill=cor, anchor="mm")

# o miolo
d.text((cx, cy - 36), "O VAZIO ETERNO", font=fonte_tit, fill=(214, 180, 255, 255), anchor="mm")
d.text((cx, cy - 7), NOMES[APONTADO], font=fonte_tit, fill=(255, 255, 255, 255), anchor="mm")
d.text((cx, cy + 12), "orbe que estoura no primeiro", font=fonte_peq, fill=(230, 224, 240, 255), anchor="mm")
d.text((cx, cy + 27), "que encostar", font=fonte_peq, fill=(230, 224, 240, 255), anchor="mm")

# a linha de baixo
d.text((cx, cy + RAIO + 14), "R fecha  •  1-6 arremessa  •  clique escolhe  •  X repete",
       font=fonte_peq, fill=(190, 185, 200, 255), anchor="mm")

img.convert("RGB").save(SAIDA)
print("prévia do leque em", SAIDA)
