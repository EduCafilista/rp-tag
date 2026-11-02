#!/usr/bin/env python3
"""Prévia do TÍTULO novo (5.10.1) — gradiente metálico + colchetes apagados.

Usa EXATAMENTE a mesma conta do TituloEstilo.java (tom(), escurecer(),
misturar()), letra por letra, pra mostrar ao dono como vai ficar antes de
entrar no jogo. A fonte do jogo é a do Minecraft; aqui é DejaVu (aproximação).
"""
import os

from PIL import Image, ImageDraw, ImageFont

W, H = 1500, 1180
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, 'previa', 'titulos_5.10.1.png')

FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_I = '/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'  # não tem itálico na imagem; o Serif lembra o itálico do MC

COLCHETE = 0.55
PALETA = [('Sagrado', 0xFFD75E), ('Warden', 0x6FB3FF), ('Gelo', 0x9BE8FF),
          ('Fragmento', 0x38E0A6), ('Almas', 0x2FE0C0), ('Chronos', 0xC9A6FF),
          ('Sombra', 0x9A6BFF), ('Divino', 0xFFE9A8), ('Morte', 0xFF7B7B),
          ('Vazio', 0x8C8C9E), ('Ferreiro', 0xE0A860), ('Player', 0xBFBFBF)]


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def luminancia(c):
    r, g, b = rgb(c)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def misturar(a, b, t):
    t = max(0.0, min(1.0, t))
    ar, ag, ab = rgb(a)
    br, bg, bb = rgb(b)
    return (int(round(ar * (1 - t) + br * t)) << 16) | \
           (int(round(ag * (1 - t) + bg * t)) << 8) | \
           int(round(ab * (1 - t) + bb * t))


def escurecer(c, f):
    return misturar(c, 0x000000, 1 - f)


def tom(cor, u):
    """A MESMA conta do TituloEstilo.tom()."""
    if luminancia(cor) < 0.30:
        return misturar(cor, 0xFFFFFF, 0.68 * (1 - 0.88 * u))
    k = 1.55 - 1.02 * u
    r, g, b = rgb(cor)
    return (min(255, int(round(r * k))) << 16) | \
           (min(255, int(round(g * k))) << 8) | min(255, int(round(b * k)))


def fonte(px, tipo='b'):
    return ImageFont.truetype({'b': FONT_B, 'r': FONT_R, 'i': FONT_I}[tipo], px)


def gradiente(d, x, y, texto, cor, px, negrito=True, italico=False):
    """desenha letra por letra com o gradiente e devolve a largura."""
    f = fonte(px, 'i' if italico else ('b' if negrito else 'r'))
    n = len(texto)
    for i, ch in enumerate(texto):
        u = 0.35 if n <= 1 else i / (n - 1)
        d.text((x, y), ch, font=f, fill=rgb(tom(cor, u)))
        x += d.textlength(ch, font=f)
    return x


def colchete(d, x, y, ch, cor, px):
    f = fonte(px, 'r')
    d.text((x, y), ch, font=f, fill=rgb(escurecer(cor, COLCHETE)))
    return x + d.textlength(ch, font=f)


def etiqueta(d, x, y, texto, cor, px):
    x = colchete(d, x, y, '[', cor, px)
    x = gradiente(d, x, y, texto, cor, px)
    return colchete(d, x, y, ']', cor, px)


def pessoal(d, x, y, texto, cor, px):
    x += d.textlength(' ', font=fonte(px, 'r'))
    x = colchete(d, x, y, '[', cor, px)
    x = gradiente(d, x, y, texto, cor, px, negrito=False, italico=True)
    return colchete(d, x, y, ']', cor, px)


def nome(d, x, y, texto, px, cor=(255, 255, 255)):
    f = fonte(px, 'b')
    d.text((x, y), texto, font=f, fill=cor)
    return x + d.textlength(texto, font=f)


def placa(d, x0, y0, x1, y1, raio=6, fundo=(9, 9, 12, 232), borda=(96, 82, 52)):
    d.rounded_rectangle([x0, y0, x1, y1], radius=raio, fill=fundo, outline=borda, width=1)


def main():
    img = Image.new('RGB', (W, H), (12, 11, 10))
    d = ImageDraw.Draw(img, 'RGBA')
    titulo = fonte(21)
    rotulo = fonte(13)
    nota = fonte(13, 'r')

    d.text((34, 24), 'TÍTULOS 5.10.1 — agora com gradiente, negrito e colchete apagado',
           font=titulo, fill=(236, 220, 180))
    d.text((34, 54), 'a etiqueta ganha relevo (claro em cima do metal → sombra no fim), os colchetes '
                     'viram moldura e o título pessoal vai em itálico', font=nota, fill=(150, 140, 120))

    # ---------------------------------------------------------- 1) ANTES × DEPOIS
    y = 92
    d.text((34, y), 'ANTES  (era só a letra naquela cor)', font=rotulo, fill=(170, 120, 110))
    placa(d, 30, y + 22, W - 30, y + 78)
    # o estilo antigo, sem nada: tudo na mesma cor, sem negrito, sem itálico
    x = 50
    x = nome(d, x, y + 36, '[Chronos] ', 26, rgb(0xC9A6FF))
    x = nome(d, x, y + 36, 'Nephisto', 26)
    nome(d, x, y + 36, ' [O Ferreiro]', 26, (255, 255, 255))

    y = 190
    d.text((34, y), 'DEPOIS  (5.10.1)', font=rotulo, fill=(150, 210, 160))
    placa(d, 30, y + 22, W - 30, y + 84, borda=(120, 100, 60))
    x = 50
    x = etiqueta(d, x, y + 34, 'Chronos', 0xC9A6FF, 28)
    x += 12
    x = nome(d, x, y + 34, 'Nephisto', 28)
    x = pessoal(d, x, y + 34, 'O Ferreiro', 0xFFFFFF, 28)

    # zoom no "Chronos" pra ver a rampa letra por letra
    y = 294
    d.text((34, y), 'no zoom dá pra ver o relevo letra por letra (é cor por caractere, '
                    'que é tudo que o Minecraft deixa mudar):', font=nota, fill=(150, 140, 120))
    placa(d, 30, y + 26, W - 30, y + 130, borda=(120, 100, 60))
    x = 50
    for i, ch in enumerate('Chronos'):
        u = i / (len('Chronos') - 1)
        f = fonte(86)
        d.text((x, y + 30), ch, font=f, fill=rgb(tom(0xC9A6FF, u)))
        x += d.textlength(ch, font=f) + 6

    # ---------------------------------------------------------- 2) A PALETA TODA
    y = 470
    d.text((34, y), 'A PALETA INTEIRA com o estilo novo (a cor de fábrica de cada classe)',
           font=rotulo, fill=(210, 196, 160))
    placa(d, 30, y + 22, W - 30, y + 190)
    for i, (nome_classe, cor) in enumerate(PALETA):
        cx = 50 + (i % 4) * 370
        cy = y + 40 + (i // 4) * 52
        x = etiqueta(d, cx, cy, nome_classe, cor, 21)
        d.text((x + 14, cy + 3), '#%06X' % cor, font=fonte(13, 'r'), fill=(110, 104, 92))

    # ---------------------------------------------------------- 3) NOS 3 LUGARES
    y = 700
    d.text((34, y), 'ONDE APARECE — o MESMO desenho nos três lugares (o cliente usa a mesma classe '
                    'que o servidor)', font=rotulo, fill=(210, 196, 160))

    # chat
    y = 734
    placa(d, 30, y, W - 30, y + 92, raio=8)
    d.text((46, y + 10), 'CHAT', font=fonte(11), fill=(120, 112, 96))
    x = 46
    x = nome(d, x, y + 34, '<', 19, (140, 140, 150))
    x = nome(d, x, y + 34, 'Nephisto', 19, (200, 200, 210))
    x = nome(d, x, y + 34, '>', 19, (140, 140, 150))
    x += 10
    d.text((x, y + 34), 'a lâmina não mente.', font=fonte(19, 'r'), fill=(226, 226, 232))
    # e a linha "entrou no jogo" com o título
    x = 46
    x = nome(d, x, y + 62, 'Nephisto', 15, (255, 220, 120))
    x += 4
    d.text((x, y + 62), 'entrou no jogo', font=fonte(15, 'r'), fill=(150, 150, 150))

    # TAB
    y = 840
    placa(d, 30, y, W - 30, y + 120, raio=8)
    d.text((46, y + 10), 'TAB (lista de jogadores)', font=fonte(11), fill=(120, 112, 96))
    linhas = [('Chronos', 'Nephisto', 'O Ferreiro', 0xC9A6FF, 0xFFFFFF),
              ('Sagrado', 'Seraphiel', '', 0xFFD75E, 0xFFFFFF),
              ('Vazio', 'Rihm', 'O que sobrou', 0x8C8C9E, 0xFFE9A8)]
    for i, (etq, nick, pess, cor, corp) in enumerate(linhas):
        ly = y + 34 + i * 28
        x = 60
        x = etiqueta(d, x, ly, etq, cor, 18)
        x += 10
        x = nome(d, x, ly, nick, 18, (235, 235, 240))
        if pess:
            pessoal(d, x, ly, pess, corp, 18)

    # nametag
    y = 976
    placa(d, 30, y, W - 30, y + 118, raio=8)
    d.text((46, y + 10), 'NAMETAG acima da cabeça', font=fonte(11), fill=(120, 112, 96))

    # cabecinha de isométrico
    hx, hy = 120, y + 78
    d.polygon([(hx, hy - 26), (hx + 22, hy - 14), (hx, hy - 2), (hx - 22, hy - 14)],
              fill=(150, 110, 80), outline=(90, 66, 48))
    d.polygon([(hx - 22, hy - 14), (hx, hy - 2), (hx, hy + 26), (hx - 22, hy + 12)],
              fill=(120, 88, 64), outline=(90, 66, 48))
    d.polygon([(hx + 22, hy - 14), (hx, hy - 2), (hx, hy + 26), (hx + 22, hy + 12)],
              fill=(170, 126, 92), outline=(90, 66, 48))

    # a placa do nametag (fundo escuro como o vanilla)
    larg = 430
    px0 = hx + 40
    placa(d, px0, y + 46, px0 + larg, y + 92, raio=4, fundo=(10, 10, 14, 210), borda=(70, 62, 44))
    x = px0 + 16
    x = etiqueta(d, x, y + 56, 'Chronos', 0xC9A6FF, 20)
    x += 8
    x = nome(d, x, y + 56, 'Nephisto', 20)
    pessoal(d, x, y + 56, 'O Ferreiro', 0xFFFFFF, 20)

    # nota do /title
    d.text((34, H - 58), 'O /title NÃO era do mod — é comando VANILLA do Minecraft. Na 5.10.1 ele sai do jogo '
                         '(padrão) e fica só o /titulo. Volta com /rp admin titlevanilla on',
           font=fonte(13, 'r'), fill=(170, 150, 110))
    d.text((34, H - 34), 'sem mouse no jogo: o "hover" da etiqueta (que mostra a classe e o HEX) só aparece '
                         'onde o mouse existe — no chat e no TAB',
           font=fonte(12, 'r'), fill=(110, 104, 92))

    img.save(SAIDA)
    print('ok', SAIDA, img.size)


if __name__ == '__main__':
    main()
