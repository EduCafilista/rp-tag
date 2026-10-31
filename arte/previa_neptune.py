#!/usr/bin/env python3
"""(5.4.0) PRÉVIA do que o jogador vê no EVENTO NEPTUNE.

Monta 4 quadros 1280x720 usando as MESMAS texturas e as mesmas contas do
cliente (client/NeptuneFx.java): nebulosa aditiva por cima, poeira subindo,
barras de visão dupla no pulso travado, saída verde e o susto do bicho.

Não é screenshot do jogo — é a simulação honesta do overlay (o renderizador
offline não tem o mundo). Serve pra bater o olho e ver se a pegada está certa.

Uso: python3 arte/previa_neptune.py
Saída: previa/neptune_5.4.0.png
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUIS = os.path.join(RAIZ, 'src/main/resources/assets/rptag/textures/gui')
SAIDA = os.path.join(RAIZ, 'previa/neptune_5.4.0.png')
W, H = 1280, 720


def carregar(nome):
    return Image.open(os.path.join(GUIS, nome)).convert('RGBA')


def fundo_mundo():
    """Um 'mundo' de mentira: céu escuro + chão de barro com horizonte."""
    img = Image.new('RGB', (W, H), (26, 30, 44))
    d = ImageDraw.Draw(img)
    for y in range(H):
        k = y / H
        r = int(26 + 46 * k)
        g = int(30 + 38 * k)
        b = int(44 + 26 * k)
        d.line([(0, y), (W, y)], fill=(r, g, b))
    # chão
    d.polygon([(0, H * 0.62), (W, H * 0.58), (W, H), (0, H)], fill=(58, 44, 32))
    for i in range(0, W, 90):
        d.polygon([(i, H * 0.63 + (i % 40)), (i + 46, H * 0.62 + (i % 30)),
                   (i + 120, H), (i + 20, H)], fill=(48, 36, 26))
    return img


def blit_aditivo(base, tex, x, y, w, h, cor, alfa):
    """Simula RenderSystem.setShaderColor(r,g,b,a) + blit (blend normal)."""
    t = tex.resize((int(w), int(h)), Image.BILINEAR)
    a = np.asarray(t, np.float32) / 255.0
    a[..., 0] *= cor[0]
    a[..., 1] *= cor[1]
    a[..., 2] *= cor[2]
    a[..., 3] *= alfa
    camada = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA')
    base.alpha_composite(camada, (int(x), int(y))) if base.mode == 'RGBA' else None
    return base


def quadro_terremoto(travado=True):
    neb = carregar('nebulosa.png')
    base = fundo_mundo().convert('RGBA')
    # (a) nebulosa de espaço passando por cima (o "blur" do vazio)
    blit_aditivo(base, neb, -W * 0.2, -H * 0.2, W * 1.4, H * 1.4, (0.42, 0.40, 0.72), 0.16)
    # (a2) blur de verdade no pulso travado (o jogo usa processBlurEffect)
    if travado:
        base = base.filter(ImageFilter.GaussianBlur(3.5))
    d = ImageDraw.Draw(base)
    # rachaduras no chão (a terra cedendo)
    rng = np.random.default_rng(9)
    for _ in range(26):
        x0 = rng.uniform(0, W)
        y0 = rng.uniform(H * 0.60, H)
        passos = rng.integers(3, 7)
        x, y = x0, y0
        for _ in range(passos):
            x2 = x + rng.uniform(-40, 40)
            y2 = y + rng.uniform(6, 26)
            d.line([(x, y), (x2, y2)], fill=(18, 12, 8, 190), width=int(rng.integers(2, 4)))
            x, y = x2, y2
    # (b) poeira subindo nas bordas
    for i in range(26):
        fase = ((i * 0.137) % 1.0)
        x = int(i * 97 % W)
        y = int(H - fase * H * 0.22)
        alfa = int(60 * (1 - fase))
        if alfa > 2:
            d.rectangle([x, y, x + 2 + (i % 3) * 2, y + 2], fill=(107, 84, 60, alfa))
    # (c) visão dupla (barras) + veios de calor
    if travado:
        for i in range(9):
            y = int((i / 9.0) * H + np.sin(i) * 4)
            off = int(np.sin(i * 2) * 7)
            d.rectangle([off + 6, y, W + off - 6, y + H // 46], fill=(0, 0, 0, 17))
        for i in range(5):
            y = int((i * 61) % H)
            d.rectangle([0, y, W, y + 1], fill=(255, 255, 255, 22))
    # HUD: o jogador continua vendo a barra/atributos, então só o texto do evento
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 34)
    Fp = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 21)
    d.text((40, 40), '⚔ TERREMOTO — o chão está rachando', font=F, fill=(255, 90, 80))
    d.text((40, 84), ('ESTUNCADO — cambaleando, o chão cede' if travado
                      else 'AGORA! Corre! (a câmera continua tremendo)'), font=Fp,
           fill=(255, 200, 160) if travado else (150, 240, 160))
    return base.convert('RGB')


def quadro_saida(k=0.55):
    base = fundo_mundo().convert('RGBA')
    glow = carregar('glow_verde.png')
    d = ImageDraw.Draw(base)
    r = int(max(W, H) * (0.25 + 0.95 * k))
    blit_aditivo(base, glow, W / 2 - r, H / 2 - r, r * 2, r * 2, (0.55, 1.0, 0.68), 0.35 + 0.55 * k)
    # pixels subindo
    rng = np.random.default_rng(3)
    for i in range(60):
        px = ((i * 733 + 41) % 1000) / 1000.0
        fase = ((i * 0.11) % 1.0)
        x, y = int(px * W), int(H - fase * H * 1.1)
        sz = 2 + (i % 4)
        alfa = int(150 * (1 - fase) * (0.4 + k))
        if alfa > 4:
            d.rectangle([x, y, x + sz, y + sz], fill=(124, 255, 154, min(255, alfa)))
    # moldura verde fechando
    borda = int(60 * k)
    for i in range(borda):
        a = int(200 * k * (1 - i / max(1, borda)))
        d.rectangle([i, i, W - i, H - i], outline=(20, 140, 60, a))
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 40)
    Fp = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 22)
    d.text((W // 2 - 300, H // 2 - 60), 'está se desfazendo em luz verde...', font=F,
           fill=(230, 255, 235))
    d.text((W // 2 - 190, H // 2 + 4), 'o universo se corrigiu', font=Fp, fill=(190, 255, 200))
    return base.convert('RGB')


def quadro_susto(escala=1.05, ms=260):
    base = fundo_mundo().convert('RGBA')
    face = carregar('bicho.png')
    d = ImageDraw.Draw(base)
    soma = min(1.0, ms / 220.0)
    lado = int(min(W, H) * (0.35 + 0.75 * soma) * escala)
    d.rectangle([0, 0, W, H], fill=(42, 0, 0, int(140 * soma)))
    face = face.resize((lado, lado), Image.LANCZOS)
    base.alpha_composite(face, (W // 2 - lado // 2, H // 2 - lado // 2))
    d = ImageDraw.Draw(base)
    if ms < 260:
        a = int(200 * (1 - ms / 260.0))
        d.rectangle([0, 0, W, H], fill=(255, 255, 255, a))
    d.rectangle([0, 0, W, 22], fill=(58, 0, 0, 90))
    d.rectangle([0, H - 22, W, H], fill=(58, 0, 0, 90))
    return base.convert('RGB')


def quadro_vazio():
    base = fundo_mundo().convert('RGBA')
    neb = carregar('nebulosa.png')
    blit_aditivo(base, neb, 0, 0, W, H, (0.32, 0.26, 0.55), 0.62)
    d = ImageDraw.Draw(base)
    r = int(min(W, H) * 0.26)
    d.ellipse([W // 2 - r, H // 2 - r, W // 2 + r, H // 2 + r], fill=(0, 0, 0, 230))
    r2 = int(r * 0.55)
    d.ellipse([W // 2 - r2, H // 2 - r2, W // 2 + r2, H // 2 + r2], fill=(0, 0, 0, 255))
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 30)
    Fm = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf', 26)
    d.text((60, H - 120), '---- vazio --- apagou', font=F, fill=(190, 170, 255))
    d.text((60 + 480, H - 120), '"EduCafilista"', font=Fm, fill=(210, 160, 255))
    d.text((60, H - 80), 'da existência', font=F, fill=(150, 140, 190))
    return base.convert('RGB')


def main():
    quadros = [
        ('TERREMOTO (pulso estuncado)', quadro_terremoto(True)),
        ('TERREMOTO (4 s livre: pode correr)', quadro_terremoto(False)),
        ('SAÍDA DO SERVIDOR (verde)', quadro_saida()),
        ('O BICHO PULA NA TELA', quadro_susto()),
        ('VAZIO APAGA ALGUÉM', quadro_vazio()),
    ]
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 30)
    Fp = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 20)
    cols = 2
    cw, ch = W // 2, H // 2
    linhas = (len(quadros) + 1) // cols
    g = Image.new('RGB', (cols * cw + 30, linhas * (ch + 46) + 70), (16, 16, 20))
    d = ImageDraw.Draw(g)
    d.text((20, 18), 'RP Tag 5.4.0 — Evento Neptune (simulação do que o jogador vê)', font=F,
           fill=(230, 230, 240))
    for i, (nome, im) in enumerate(quadros):
        cx = 10 + (i % cols) * (cw + 10)
        cy = 62 + (i // cols) * (ch + 46)
        g.paste(im.resize((cw - 10, int((cw - 10) * H / W)), Image.LANCZOS), (cx, cy))
        d.text((cx + 6, cy + int((cw - 10) * H / W) + 6), nome, font=Fp, fill=(170, 180, 200))
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    g.save(SAIDA)
    print('gravado', SAIDA, g.size)


if __name__ == '__main__':
    main()
