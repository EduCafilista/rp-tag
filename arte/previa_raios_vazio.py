#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Prévia da 5.11.1 — os RAIOS VERMELHOS do vazio.

Mostra o antes (aditivo: sumia com shader) × depois (blend normal: aparece),
e um painel voando para provar que a aura não depende do chão.

Saída: previa/vazio_5.11.1.png
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, 'previa', 'vazio_5.11.1.png')

L, A = 1500, 1330
SS = 2

FUNDO = (13, 13, 16)
CARTAO = (22, 22, 27)
BORDA = (44, 44, 52)
TEXTO = (232, 232, 238)
TEXTO2 = (150, 150, 162)

F_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
F_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def fonte(caminho, tam):
    return ImageFont.truetype(caminho, tam)


def cartao(d, x, y, w, h):
    d.rounded_rectangle([x, y, x + w, y + h], radius=10, fill=CARTAO, outline=BORDA, width=2)


def texto(d, x, y, s, f, cor=TEXTO, ancora='la'):
    d.text((x, y), s, font=f, fill=cor, anchor=ancora)


def mistura(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


# --------------------------------------------------------------- o boneco
def boneco(d, cx, chao, k=1.0, flutuando=False):
    alt = 150 * k
    larg = 46 * k
    cab_r = 20 * k
    topo = chao - alt
    cor = (7, 7, 10)
    borda = (34, 34, 42)
    w = max(1, int(2 * k))
    d.rounded_rectangle([cx - larg / 2, topo + cab_r * 2.1, cx + larg / 2, chao - 46 * k],
                        radius=12 * k, fill=cor, outline=borda, width=w)
    d.rounded_rectangle([cx - larg / 2 + 4 * k, chao - 52 * k, cx - 4 * k, chao], radius=9 * k,
                        fill=cor, outline=borda, width=w)
    d.rounded_rectangle([cx + 4 * k, chao - 52 * k, cx + larg / 2 - 4 * k, chao], radius=9 * k,
                        fill=cor, outline=borda, width=w)
    d.rounded_rectangle([cx - larg / 2 - 10 * k, topo + cab_r * 2.3, cx - larg / 2 + 6 * k, chao - 66 * k],
                        radius=8 * k, fill=cor, outline=borda, width=w)
    d.rounded_rectangle([cx + larg / 2 - 6 * k, topo + cab_r * 2.3, cx + larg / 2 + 10 * k, chao - 66 * k],
                        radius=8 * k, fill=cor, outline=borda, width=w)
    d.ellipse([cx - cab_r, topo, cx + cab_r, topo + cab_r * 2], fill=cor, outline=borda, width=w)
    for s in (-1, 1):
        ox = cx + s * 7 * k
        oy = topo + cab_r * 1.05
        d.ellipse([ox - 3.4 * k, oy - 2.8 * k, ox + 3.4 * k, oy + 2.8 * k], fill=(240, 34, 36))


# --------------------------------------------------------------- raio
def raio(d, bx, by, ang, comp, largura, semente, opaco=True, alpha=1.0, quente=True):
    """Um raio serrilhado saindo de (bx,by) no ângulo ang."""
    rnd = random.Random(semente)
    n = 9
    pts = []
    for i in range(n + 1):
        f = i / n
        x = bx + math.cos(ang) * comp * f + rnd.uniform(-7, 7) * f
        y = by + math.sin(ang) * comp * f * 0.92 + rnd.uniform(-6, 6) * f
        pts.append((x, y))

    if opaco:
        # capa escura (o "corpo" do raio contra fundo claro)
        d.line(pts, fill=(120, 8, 12, int(160 * alpha)), width=max(2, int(largura * 3.4)), joint='curve')
        # corpo vermelho vivo
        d.line(pts, fill=(206, 22, 26, int(235 * alpha)), width=max(2, int(largura * 1.5)), joint='curve')
        # núcleo quente
        if quente:
            d.line(pts, fill=(255, 132, 74, int(255 * alpha)), width=max(1, int(largura * 0.55)), joint='curve')
    else:
        # versão 5.10.1: ADITIVA (soma luz) — em fundo claro, lava
        d.line(pts, fill=(200, 26, 26, int(120 * alpha)), width=max(1, int(largura * 1.6)), joint='curve')
        d.line(pts, fill=(60, 0, 70, int(140 * alpha)), width=max(1, int(largura * 0.8)), joint='curve')


def aura(d, cx, cy, comp_base, qtd, semente, opaco=True, alpha=1.0, cerdas=18):
    rnd = random.Random(semente)
    for i in range(qtd):
        ang = (i / qtd) * math.tau + rnd.uniform(-0.2, 0.2)
        comp = comp_base * rnd.uniform(0.55, 1.25)
        raio(d, cx, cy, ang, comp, rnd.uniform(2.0, 3.4), semente + i * 7, opaco, alpha)
    for i in range(cerdas):
        ang = rnd.uniform(0, math.tau)
        comp = comp_base * rnd.uniform(0.10, 0.30)
        raio(d, cx, cy, ang, comp, rnd.uniform(1.4, 2.4), semente + 500 + i, opaco, alpha, quente=False)


# --------------------------------------------------------------- cena
def cena(larg, alt, fundo, com_shader, novo, voando=False):
    W, H = larg * SS, alt * SS
    img = Image.new('RGB', (W, H), fundo)
    d = ImageDraw.Draw(img)

    if not voando:
        chao = int(H * 0.80)
        d.rectangle([0, chao, W, H], fill=mistura(fundo, (0, 0, 0), 0.45))
        d.line([(0, chao), (W, chao)], fill=mistura(fundo, (255, 255, 255), 0.10), width=2)
        cy = chao - 75 * SS          # meio do corpo
        cx = W // 2
        # as pedrinhas (partículas pétreas continuam)
        rnd = random.Random(5)
        for i in range(14):
            x = cx + rnd.uniform(-130, 130) * SS
            y = chao - rnd.uniform(4, 90) * SS
            t = rnd.uniform(2.0, 4.2) * SS
            d.polygon([(x - t, y), (x + t * 0.25, y - t), (x + t, y), (x, y + t * 0.8)],
                      fill=(66, 66, 74))
        boneco(d, cx, chao, SS * 1.0)
    else:
        # voando: nada de chão — a aura vai com ele
        cx, cy = W // 2, int(H * 0.52)
        # nuvens baixas ao fundo só pra dar noção de altura
        d.ellipse([W * 0.05, H * 0.78, W * 0.55, H * 0.95], fill=mistura(fundo, (255, 255, 255), 0.05))
        d.ellipse([W * 0.55, H * 0.84, W * 1.0, H * 1.05], fill=mistura(fundo, (255, 255, 255), 0.04))
        k = SS * (1.02 if novo else 1.0)
        boneco(d, cx, cy + 75 * k, k)
        cy = cy

    if novo:
        alpha = 1.0
        aura(d, cx, cy, 175 * SS, 12, 21, opaco=True, alpha=alpha)
    else:
        alpha = 0.20 if com_shader else 1.0     # aditivo lava no claro
        aura(d, cx, cy, 175 * SS, 12, 21, opaco=False, alpha=alpha)

    return img


def main():
    img = Image.new('RGB', (L * SS, A * SS), FUNDO)
    d = ImageDraw.Draw(img)
    f_tit = fonte(F_BOLD, 34 * SS)
    f_sub = fonte(F_REG, 17 * SS)
    f_rot = fonte(F_BOLD, 14 * SS)
    f_sec = fonte(F_BOLD, 19 * SS)
    f_corpo = fonte(F_REG, 16 * SS)
    f_peq = fonte(F_REG, 14 * SS)

    def T(x, y, s, f, cor=TEXTO, ancora='la'):
        texto(d, x * SS, y * SS, s, f, cor, ancora)

    T(48, 28, 'VAZIO 5.11.1 — RAIOS VERMELHOS saindo de deus', f_tit)
    T(48, 74, 'em geometria (não partícula) · blend NORMAL em vez de aditivo, por isso aparece com shader · '
              'preso no corpo, então funciona voando', f_sub, TEXTO2)

    pW, pH = 640, 296
    x1, x2 = 48, 48 + pW + 36

    # ---------------- linha 1: antes ----------------
    y = 122
    T(x1, y, 'ANTES (5.10.1) — sem shader: os raios apareciam', f_rot, TEXTO2)
    T(x2, y, 'ANTES (5.10.1) — com shader: o aditivo lavou, quase sumiu', f_rot, TEXTO2)
    img.paste(cena(pW, pH, (30, 27, 32), False, novo=False), (x1 * SS, (y + 18) * SS))
    img.paste(cena(pW, pH, (112, 124, 140), True, novo=False), (x2 * SS, (y + 18) * SS))
    for px in (x1, x2):
        d.rectangle([px * SS, (y + 18) * SS, (px + pW) * SS, (y + 18 + pH) * SS],
                    outline=BORDA, width=2 * SS)

    # ---------------- linha 2: depois ----------------
    y2 = y + 18 + pH + 40
    T(x1, y2, 'DEPOIS (5.11.1) — sem shader: raio vermelho de verdade', f_rot, TEXTO2)
    T(x2, y2, 'DEPOIS (5.11.1) — com shader: continua vermelho', f_rot, TEXTO2)
    img.paste(cena(pW, pH, (30, 27, 32), False, novo=True), (x1 * SS, (y2 + 18) * SS))
    img.paste(cena(pW, pH, (112, 124, 140), True, novo=True), (x2 * SS, (y2 + 18) * SS))
    for px in (x1, x2):
        d.rectangle([px * SS, (y2 + 18) * SS, (px + pW) * SS, (y2 + 18 + pH) * SS],
                    outline=BORDA, width=2 * SS)

    # ---------------- linha 3: voando ----------------
    y3 = y2 + 18 + pH + 40
    vW, vH = 640, 200
    T(x1, y3, 'VOANDO — o ponto do dono: a aura NÃO está presa no chão', f_rot, TEXTO2)
    img.paste(cena(vW, vH, (86, 104, 128), True, novo=True, voando=True),
              (x1 * SS, (y3 + 18) * SS))
    d.rectangle([x1 * SS, (y3 + 18) * SS, (x1 + vW) * SS, (y3 + 18 + vH) * SS],
                outline=BORDA, width=2 * SS)

    # ---------------- cartões (à direita do "voando") ----------------
    cx2, cw = x2, 640
    yc = y3
    cartao(d, cx2 * SS, yc * SS, cw * SS, 196 * SS)
    T(cx2 + 22, yc + 14, 'O QUE MUDOU NA TÉCNICA', f_sec)
    linhas = [
        ('antes', 'RenderType.lightning() = blend ADITIVO (soma luz).'),
        ('', 'Com shader claro a soma estoura e o vermelho some.'),
        ('agora', 'RenderType.debugQuads() = blend NORMAL + profundidade:'),
        ('', 'o vermelho MANCHA a tela em vez de somar — aparece sempre.'),
        ('', '3 passadas por raio: capa escura + corpo + núcleo quente.'),
    ]
    yy = yc + 46
    for tag, s in linhas:
        if tag:
            T(cx2 + 22, yy, tag.upper(), fonte(F_BOLD, 12 * SS),
              (190, 90, 90) if tag == 'antes' else (120, 190, 130))
        T(cx2 + 22 + 62, yy, s, f_corpo)
        yy += 30

    yc2 = yc + 196 + 18
    cartao(d, cx2 * SS, yc2 * SS, cw * SS, 196 * SS)
    T(cx2 + 22, yc2 + 14, 'A AURA "BIZARRA" (o que dá o volume)', f_sec)
    itens = [
        '10 raios longos (1,6 a 6 blocos) em 360°, pra cima e pra baixo',
        '20 cerdas curtas grudadas no corpo — a coisa parece ferver',
        'o serrilhado re-estala a cada ~110 ms (não é congelado)',
        'o conjunto inteiro gira devagar em volta dele',
        'partículas do servidor continuam PÉTREAS (pedaço de bloco)',
        'quem É o vazio vê 2/3 e mais fino; os outros veem tudo',
    ]
    yy = yc2 + 46
    for s in itens:
        T(cx2 + 22, yy, '· ' + s, f_corpo)
        yy += 27

    T(48, A - 26, '/vazio ligar   →   raios vermelhos no corpo + trilha + medo   ·   agora fica SALVO no mundo '
                    '(reiniciar não apaga mais)', f_peq, (140, 140, 152))

    img = img.resize((L, A), Image.LANCZOS)
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    img.save(SAIDA, 'PNG', optimize=True)
    print('salvo:', SAIDA, os.path.getsize(SAIDA), 'bytes')


if __name__ == '__main__':
    main()
