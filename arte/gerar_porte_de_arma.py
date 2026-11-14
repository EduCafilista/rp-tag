#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.49.0) A TEXTURA DO "PORTE DE ARMA" — o contrato.

Pedido do dono: <i>"faça um item chamado 'Porte de arma', apenas um contrato, qualquer coisa do
tipo"</i>. Então é um DOCUMENTO: uma folha de pergaminho com o texto do contrato, a linha de
assinatura e o SELO de cera vermelho no canto — nada de arma desenhada, porque o item é a licença,
não a arma.

Desenhado em 4x (256) e reduzido para 64x64: a borda fica macia como nas outras texturas do mod
(as do mod são 64x64 desde a 5.28.0).

Uso: python3 arte/gerar_porte_de_arma.py
"""
import os

from PIL import Image, ImageDraw

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DESTINO = os.path.join(RAIZ, 'src', 'main', 'resources', 'assets', 'rptag', 'textures', 'item')

LADO = 64
ESCALA = 4          # desenha em 256 e reduz: borda suave

PERGAMINHO = (232, 214, 170)
PERGAMINHO_ESCURO = (196, 172, 122)
PERGAMINHO_CLARO = (246, 236, 206)
BORDA = (120, 96, 58)
TINTA = (58, 44, 28)
TINTA_FRACA = (128, 108, 76)
CERA = (168, 26, 34)
CERA_CLARA = (214, 62, 58)
CERA_ESCURA = (110, 12, 20)
OURO = (226, 178, 74)


def desenhar():
    g = Image.new('RGBA', (LADO * ESCALA, LADO * ESCALA), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    e = ESCALA

    def r(x0, y0, x1, y1, cor):
        d.rectangle([x0 * e, y0 * e, x1 * e - 1, y1 * e - 1], fill=cor)

    # ---- a folha (com o canto dobrado em cima à direita)
    r(14, 6, 50, 58, BORDA)
    r(15, 7, 49, 57, PERGAMINHO)
    # a dobra: um triângulo claro no canto de cima
    d.polygon([(43 * e, 7 * e), (49 * e, 7 * e), (49 * e, 16 * e)], fill=PERGAMINHO_ESCURO)
    d.polygon([(43 * e, 7 * e), (43 * e, 16 * e), (49 * e, 16 * e)], fill=PERGAMINHO_CLARO)
    # sombra de baixo (a folha não é um retângulo chapado)
    r(15, 55, 49, 57, PERGAMINHO_ESCURO)

    # ---- o título: uma barra mais grossa e mais escura
    r(19, 12, 39, 15, TINTA)
    r(19, 12, 26, 15, CERA)

    # ---- o texto do contrato (linhas de tamanhos diferentes, como um documento de verdade)
    linhas = [(19, 43), (19, 45), (19, 46), (19, 44), (19, 38),
              (19, 42), (19, 41), (19, 33)]
    y = 20
    for ini, fim in linhas:
        r(ini, y, fim, y + 1, TINTA_FRACA)
        y += 3

    # ---- a linha de assinatura
    r(19, 46, 37, 48, TINTA)
    # o rabisco da assinatura por cima da linha
    d.line([(20 * e, 46 * e), (24 * e, 44 * e), (27 * e, 47 * e), (31 * e, 44 * e), (36 * e, 46 * e)],
           fill=TINTA, width=e)

    # ---- O SELO DE CERA (o que faz ler como "contrato assinado")
    cx, cy, raio = 41, 47, 7
    d.ellipse([(cx - raio) * e, (cy - raio) * e, (cx + raio) * e, (cy + raio) * e],
              fill=CERA_ESCURA)
    d.ellipse([(cx - raio + 1) * e, (cy - raio + 1) * e, (cx + raio - 1) * e, (cy + raio - 1) * e],
              fill=CERA)
    # o brilho do selo (canto de cima à esquerda)
    d.ellipse([(cx - raio + 2) * e, (cy - raio + 2) * e, (cx - 1) * e, (cy - 1) * e],
              fill=CERA_CLARA)
    # o sinete: um "P" de chumbo, só a gota de ouro no meio
    d.ellipse([(cx - 2) * e, (cy - 2) * e, (cx + 2) * e, (cy + 2) * e], fill=OURO)
    d.ellipse([(cx - 1) * e, (cy - 1) * e, (cx + 1) * e, (cy + 1) * e], fill=CERA_ESCURA)

    final = g.resize((LADO, LADO), Image.LANCZOS)
    os.makedirs(DESTINO, exist_ok=True)
    caminho = os.path.join(DESTINO, 'porte_de_arma.png')
    final.save(caminho)
    print('porte de arma: %s (%d B)' % (caminho, os.path.getsize(caminho)))
    return final


if __name__ == '__main__':
    desenhar()
