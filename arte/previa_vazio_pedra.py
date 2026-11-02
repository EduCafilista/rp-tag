#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Prévia da 5.11.0 — o VAZIO virou PEDRA.

Desenha (sem renderizar o jogo) o antes × depois da aura do /vazio ligar:
  * antes (5.10.1): raios vermelhos em blend ADITIVO -> somem com shader
  * depois (5.11.0): tentáculos de PEDRA com volume, opacos -> aparecem sempre

Saída: previa/vazio_5.11.0.png
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, 'previa', 'vazio_5.11.0.png')

L, A = 1500, 1220
SS = 2  # supersampling

FUNDO = (13, 13, 16)
CARTAO = (22, 22, 27)
BORDA = (44, 44, 52)
TEXTO = (232, 232, 238)
TEXTO2 = (150, 150, 162)
ACENTO = (150, 150, 162)

F_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
F_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def fonte(caminho, tam):
    return ImageFont.truetype(caminho, tam)


# --------------------------------------------------------------- utilidades
def cartao(d, x, y, w, h, cor=CARTAO, borda=BORDA):
    d.rounded_rectangle([x, y, x + w, y + h], radius=10, fill=cor, outline=borda, width=2)


def texto(d, x, y, s, f, cor=TEXTO, ancora='la'):
    d.text((x, y), s, font=f, fill=cor, anchor=ancora)


def mistura(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


# --------------------------------------------------------------- o boneco
def boneco(d, cx, chao, escala=1.0, olhos=True):
    """Silhueta preta do jogador (o vazio é preto), com os dois olhos vermelhos."""
    k = escala
    alt = 150 * k
    larg = 46 * k
    cab_r = 20 * k
    topo = chao - alt
    # tronco
    d.rounded_rectangle([cx - larg / 2, topo + cab_r * 2.1, cx + larg / 2, chao - 46 * k],
                        radius=12 * k, fill=(6, 6, 8), outline=(30, 30, 36), width=max(1, int(2 * k)))
    # pernas
    d.rounded_rectangle([cx - larg / 2 + 4 * k, chao - 52 * k, cx - 4 * k, chao], radius=9 * k,
                        fill=(6, 6, 8), outline=(30, 30, 36), width=max(1, int(2 * k)))
    d.rounded_rectangle([cx + 4 * k, chao - 52 * k, cx + larg / 2 - 4 * k, chao], radius=9 * k,
                        fill=(6, 6, 8), outline=(30, 30, 36), width=max(1, int(2 * k)))
    # braços
    d.rounded_rectangle([cx - larg / 2 - 10 * k, topo + cab_r * 2.3, cx - larg / 2 + 6 * k, chao - 66 * k],
                        radius=8 * k, fill=(6, 6, 8), outline=(30, 30, 36), width=max(1, int(2 * k)))
    d.rounded_rectangle([cx + larg / 2 - 6 * k, topo + cab_r * 2.3, cx + larg / 2 + 10 * k, chao - 66 * k],
                        radius=8 * k, fill=(6, 6, 8), outline=(30, 30, 36), width=max(1, int(2 * k)))
    # cabeça
    d.ellipse([cx - cab_r, topo, cx + cab_r, topo + cab_r * 2], fill=(6, 6, 8),
              outline=(30, 30, 36), width=max(1, int(2 * k)))
    if olhos:
        for s in (-1, 1):
            ox = cx + s * 7 * k
            oy = topo + cab_r * 1.05
            d.ellipse([ox - 3.2 * k, oy - 2.6 * k, ox + 3.2 * k, oy + 2.6 * k], fill=(235, 30, 32))


# --------------------------------------------------------------- tentáculo
def tentaculo(d, bx, by, altura, largura, ang, semente, claro=1.0, atras=False):
    """
    Desenha UM tentáculo de pedra: tubo curvo com 4 faces sombreadas, nós,
    veios escuros e bico na ponta. 'claro' escurece quem está atrás do corpo.
    """
    rnd = random.Random(semente)
    n = 26
    pts = []
    for i in range(n + 1):
        s = i / n
        # sobe desacelerando
        y = by - altura * (1 - (1 - s) ** 1.75)
        # abre pra fora
        x = bx + math.cos(ang) * 0 + math.sin(ang) * 0
        x = bx + (s ** 2) * math.cos(ang) * altura * 0.28
        y += math.sin(ang) * (s ** 2) * altura * 0.06
        # ondulação
        x += math.sin(s * 3.1 + semente) * 6 * s
        # espessura com "nós"
        w = largura * (1 - 0.82 * s) * (1 + 0.17 * math.sin(s * 15 + semente))
        pts.append((x, y, max(0.7, w)))

    base = 62
    tom = (0.86 + 0.22 * rnd.random()) * claro
    escuro = atras

    # --- faces laterais: desenha faixas por segmento (a de trás é mais escura)
    for face in range(4):
        # 0 = sombra (-X), 1 = meio, 2 = luz (+X), 3 = topo
        f = [0.52, 0.80, 1.06, 0.92][face]
        for i in range(n):
            x0, y0, w0 = pts[i]
            x1, y1, w1 = pts[i + 1]
            if face == 1:
                off0, off1 = -w0 * 0.15, -w1 * 0.15
            elif face == 2:
                off0, off1 = -w0 * 0.62, -w1 * 0.62
            elif face == 3:
                off0, off1 = w0 * 0.62, w1 * 0.62
            else:
                off0, off1 = w0 * 0.15, w1 * 0.15
            k = f * tom * (0.90 + 0.18 * (i / n))
            if escuro:
                k *= 0.74
            cor = (int(min(255, base * 1.10 * k)), int(min(255, base * 1.10 * k)),
                   int(min(255, base * 1.18 * k)))
            d.polygon([(x0 - w0, y0), (x0 + w0, y0), (x1 + w1, y1), (x1 - w1, y1)], fill=cor)

    # --- veios escuros (rachaduras): faixas finas em alguns pontos
    for v in range(3):
        i = int(n * (0.18 + 0.24 * v)) + (semente % 3)
        if i >= n:
            continue
        x0, y0, w0 = pts[i]
        x1, y1, w1 = pts[i + 1]
        k = 0.45 * tom
        if escuro:
            k *= 0.72
        cor = (int(base * k), int(base * k), int(base * 1.06 * k))
        d.polygon([(x0 - w0 * 0.9, y0), (x0 - w0 * 0.15, y0),
                   (x1 - w1 * 0.15, y1), (x1 - w1 * 0.9, y1)], fill=cor)

    # --- bico de pedra na ponta
    xt, yt, wt = pts[-1]
    bico = wt * 2.6 + 2
    k = (1.02 * tom) if not escuro else (1.02 * tom * 0.62)
    cor = (int(base * 1.10 * k), int(base * 1.10 * k), int(base * 1.18 * k))
    d.polygon([(xt - wt, yt), (xt + wt, yt), (xt, yt - bico)], fill=cor)
    # aresta clara do bico
    d.line([(xt - wt * 0.6, yt - bico * 0.15), (xt, yt - bico * 0.92)],
           fill=(int(base * 1.3 * k), int(base * 1.3 * k), int(base * 1.4 * k)), width=2)


def pedrinhas(d, bx, by, raio, semente, claro=1.0):
    rnd = random.Random(semente)
    for i in range(4):
        a = rnd.uniform(0, math.tau)
        r = raio * rnd.uniform(0.7, 1.5)
        x = bx + math.cos(a) * r
        y = by + math.sin(a) * r * 0.25
        t = rnd.uniform(4, 9)
        k = claro * rnd.uniform(0.55, 1.0)
        cor = (int(52 * k), int(52 * k), int(58 * k))
        d.polygon([(x - t, y), (x - t * 0.4, y - t * 0.8), (x + t * 0.7, y - t * 0.55),
                   (x + t, y + t * 0.2), (x, y + t * 0.45)], fill=cor)
        d.line([(x - t * 0.4, y - t * 0.8), (x + t * 0.7, y - t * 0.55)],
               fill=(int(84 * k), int(84 * k), int(92 * k)), width=1)


# --------------------------------------------------------------- raios velhos
def raios_velhos(img, d, cx, chao, alt, forca=1.0):
    """Os raios da 5.7.4: fitas vermelhas ADITIVAS (somam luz) — o que sumia."""
    rnd = random.Random(7)
    camada = Image.new('RGBA', img.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(camada)
    for i in range(14):
        ang = rnd.uniform(0, math.tau)
        comp = rnd.uniform(90, 240)
        x0 = cx + math.cos(ang) * 8
        y0 = chao - alt * rnd.choice([0.08, 0.5, 0.88])
        pts = []
        for k in range(9):
            f = k / 8
            pts.append((x0 + math.cos(ang) * comp * f + rnd.uniform(-8, 8) * f,
                        y0 + math.sin(ang) * comp * f * 0.45 + rnd.uniform(-6, 6) * f))
        largura = max(2, int(7 * forca))
        # capa vermelha (aditiva = fica mais clara somando)
        dd.line(pts, fill=(235, 45, 40, int(190 * forca)), width=largura, joint='curve')
        # miolo "preto" do raio (na verdade roxo escuro)
        dd.line(pts, fill=(30, 0, 45, int(200 * forca)), width=max(1, largura // 2), joint='curve')
    camada = camada.filter(ImageFilter.GaussianBlur(1.4))
    img.paste(camada, (0, 0), camada)


def brilho(camada, raio=18):
    return camada.filter(ImageFilter.GaussianBlur(raio))


# --------------------------------------------------------------- cena
def cena_vazia(fundo_cor, com_shader=False):
    """Devolve uma imagem de cena (boneco + aura) no tamanho de um painel."""
    W, H = 640, 330
    img = Image.new('RGB', (W * SS, H * SS), fundo_cor)
    d = ImageDraw.Draw(img)
    W2, H2 = img.size
    chao = int(H2 * 0.80)
    cx = W2 // 2

    # chão
    d.rectangle([0, chao, W2, H2], fill=mistura(fundo_cor, (0, 0, 0), 0.45))
    d.line([(0, chao), (W2, chao)], fill=mistura(fundo_cor, (255, 255, 255), 0.10), width=2)
    return img, d, cx, chao, W2, H2


def painel_antes(img, d, cx, chao, com_shader):
    """5.10.1: raios vermelhos aditivos saindo do corpo."""
    forca = 0.35 if com_shader else 1.0     # com shader claro, o aditivo "lava"
    raios_velhos(img, d, cx, chao, 150 * SS, forca)
    pedrinhas(d, cx, chao, 40 * SS, 3, 0.6)
    boneco(d, cx, chao, SS * 1.0)
    return img


def painel_depois(img, d, cx, chao, com_shader):
    """5.11.0: tentáculos de PEDRA (opacos, com volume) em volta do corpo."""
    rnd = random.Random(11)
    # os de trás primeiro (mais escuros), depois o boneco, depois os da frente
    for i in range(5):
        a = rnd.uniform(-1.2, 1.2)
        tentaculo(d, cx + a * 92 * SS, chao + 6 * SS, rnd.uniform(95, 135) * SS,
                  rnd.uniform(11, 15) * SS, a, 100 + i, claro=0.95, atras=True)
    pedrinhas(d, cx - 60 * SS, chao, 30 * SS, 21, 0.75)
    pedrinhas(d, cx + 66 * SS, chao, 30 * SS, 22, 0.75)
    boneco(d, cx, chao, SS * 1.0)
    for i in range(5):
        a = rnd.uniform(-1.5, 1.5)
        tentaculo(d, cx + a * 78 * SS, chao + 10 * SS, rnd.uniform(90, 150) * SS,
                  rnd.uniform(12, 17) * SS, a * 0.8, 300 + i, claro=1.05)
    # pedaços de pedra soltos no ar (as partículas pétreas do servidor)
    for i in range(16):
        x = cx + rnd.uniform(-135, 135) * SS
        y = chao - rnd.uniform(20, 165) * SS
        t = rnd.uniform(2.2, 4.6) * SS
        d.polygon([(x - t, y), (x + t * 0.2, y - t), (x + t, y), (x, y + t * 0.8)],
                  fill=(70, 70, 78))
    return img


def montar_painel(titulo, subtitulo, funcao, com_shader, fundo_cor):
    img, d, cx, chao, W, H = cena_vazia(fundo_cor, com_shader)
    funcao(img, d, cx, chao, com_shader)
    return img   # já vem em 640*SS x 330*SS


# --------------------------------------------------------------- principal
def main():
    img = Image.new('RGB', (L * SS, A * SS), FUNDO)
    d = ImageDraw.Draw(img)

    f_tit = fonte(F_BOLD, 34 * SS)
    f_sub = fonte(F_REG, 17 * SS)
    f_sec = fonte(F_BOLD, 19 * SS)
    f_corpo = fonte(F_REG, 16 * SS)
    f_peq = fonte(F_REG, 14 * SS)

    texto(d, 48 * SS, 34 * SS, 'VAZIO 5.11.0 — agora é PEDRA (e aparece com shader)', f_tit)
    texto(d, 48 * SS, 80 * SS,
          'tentáculos de pedra em geometria sólida em volta de quem usa /vazio ligar · '
          'nenhuma partícula vermelha na aura', f_sub, TEXTO2)

    # ---------------- linha 1: antes x depois ----------------
    y1 = 128 * SS

    antes_normal = montar_painel('', '', painel_antes, False, (30, 27, 32))
    antes_shader = montar_painel('', '', painel_antes, True, (96, 108, 126))
    depois_normal = montar_painel('', '', painel_depois, False, (30, 27, 32))
    depois_shader = montar_painel('', '', painel_depois, True, (96, 108, 126))

    # montagem: 2x2
    pW, pH = 640, 330
    x0, y0 = 48 * SS, (y1 + 30 * SS)
    for (px, py, im, rot) in [
        (x0, y0, antes_normal, 'ANTES (5.10.1) — sem shader: os raios vermelhos apareciam'),
        (x0 + (pW + 36) * SS, y0, antes_shader, 'ANTES (5.10.1) — com shader: o aditivo lavou, quase some'),
        (x0, y0 + (pH + 46) * SS, depois_normal, 'DEPOIS (5.11.0) — sem shader: pedra sólida'),
        (x0 + (pW + 36) * SS, y0 + (pH + 46) * SS, depois_shader, 'DEPOIS (5.11.0) — com shader: a pedra continua lá'),
    ]:
        img.paste(im, (px, py))
        d.rectangle([px, py, px + pW * SS, py + pH * SS], outline=BORDA, width=2 * SS)
        texto(d, px, py - 24 * SS, rot, fonte(F_BOLD, 14 * SS), TEXTO2)
    d.text  # noop

    # ---------------- linha 2: o que mudou (técnico) ----------------
    y2 = y0 + (pH + 46) * SS + pH * SS + 46 * SS
    cartao(d, 48 * SS, y2, 690 * SS, 196 * SS)
    texto(d, 70 * SS, y2 + 16 * SS, 'POR QUE OS RAIOS SUMIAM COM SHADER', f_sec)
    linhas = [
        ('antes', 'RenderType.lightning() = blend ADITIVO (soma luz, ONE). Num pack claro a'),
        ('', 'soma estoura em branco e o vermelho "desaparece".'),
        ('agora', 'RenderType.debugQuads() = blend NORMAL + escreve profundidade.'),
        ('', 'A pedra escurece com o ambiente, tapa o que está atrás e aparece igual'),
        ('', 'com ou sem shader.'),
    ]
    yy = y2 + 48 * SS
    for tag, s in linhas:
        if tag:
            texto(d, 70 * SS, yy, tag.upper(), fonte(F_BOLD, 13 * SS),
                  (190, 90, 90) if tag == 'antes' else (120, 190, 130))
            texto(d, 70 * SS + 70 * SS, yy, s, f_corpo)
        else:
            texto(d, 70 * SS + 70 * SS, yy, s, f_corpo)
        yy += 26 * SS

    # ---------------- linha 2b: o que aparece em jogo ----------------
    cartao(d, 762 * SS, y2, 690 * SS, 196 * SS)
    texto(d, 784 * SS, y2 + 16 * SS, 'O QUE MUDOU NO JOGO', f_sec)
    itens = [
        '· 9 tentáculos de pedra brotam do chão em volta do corpo e afundam',
        '   sozinhos (4–9 s cada) — nunca fica congelado',
        '· cada um é um tubo de 4 faces + bico + pedras na base (volume real)',
        '· servidor manda PEDAÇOS DE BLOCO (stone/cobblestone/deepslate)',
        '· saiu o vermelho da aura; ficaram só os 2 olhos no rosto',
    ]
    yy = y2 + 48 * SS
    for s in itens:
        texto(d, 784 * SS, yy, s, f_corpo, TEXTO)
        yy += 26 * SS

    # ---------------- rodapé ----------------
    texto(d, 48 * SS, (A - 34) * SS,
          '/vazio ligar   →  aura de pedra + trilha + medo   ·   o modo vazio agora fica SALVO no mundo '
          '(reiniciar não apaga mais)', f_peq, (140, 140, 152))

    img = img.resize((L, A), Image.LANCZOS)
    os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
    img.save(SAIDA, 'PNG', optimize=True)
    print('salvo:', SAIDA, os.path.getsize(SAIDA), 'bytes')


if __name__ == '__main__':
    main()
