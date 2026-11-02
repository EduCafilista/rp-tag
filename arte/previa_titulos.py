"""Previa (mock) dos TÍTULOS — 5.9.0.

Mostra como o título fica nos 3 lugares onde ele aparece:
  TAB  ·  nametag acima da cabeça  ·  chat
e a paleta pronta + os exemplos de /titulo cor.

O texto no jogo e a fonte do Minecraft; aqui e uma aproximacao (DejaVu Sans Bold).

uso: python arte/previa_titulos.py   ->  previa/titulos_5.9.0.png
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / 'previa'
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_R = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

W, H = 1180, 760
BG = (16, 16, 20)
PAINEL = (26, 27, 33)
LINHA = (44, 46, 56)
CINZA = (170, 175, 188)
BRANCO = (232, 235, 242)
AQUA = (85, 255, 255)
CINZA_TAG = (170, 170, 170)

PALETA = [
    ('Sagrado', 0xFFD75E), ('Warden', 0x6FB3FF), ('Gelo', 0x9BE8FF),
    ('Fragmento', 0x38E0A6), ('Almas', 0x2FE0C0), ('Chronos', 0xC9A6FF),
    ('Sombra', 0x9A6BFF), ('Divino', 0xFFE9A8), ('Morte', 0xFF7B7B),
    ('Vazio', 0x8C8C9E), ('Ferreiro', 0xE0A860), ('Player', 0xBFBFBF),
]


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def fonte(px, regular=False):
    return ImageFont.truetype(FONT_R if regular else FONT, px)


def painel(d, x, y, w, h, titulo):
    d.rounded_rectangle([x, y, x + w, y + h], 10, fill=PAINEL, outline=LINHA)
    d.text((x + 14, y + 10), titulo, font=fonte(15), fill=CINZA)


def titulo_composto(base, x, y, etiqueta, cor_etq, nome, pessoal, cor_pes,
                    tam=17, nome_cor=BRANCO, tag='(RP)', tag_cor=AQUA):
    """Desenha "[Chronos] Nome [O Ferreiro] (RP)" e devolve o x final."""
    d = ImageDraw.Draw(base)
    f = fonte(tam)
    fx = x
    if etiqueta:
        t = '[' + etiqueta + '] '
        d.text((fx, y), t, font=f, fill=rgb(cor_etq))
        fx += f.getlength(t)
    d.text((fx, y), nome, font=f, fill=nome_cor)
    fx += f.getlength(nome)
    if pessoal:
        t = ' [' + pessoal + ']'
        d.text((fx, y), t, font=f, fill=rgb(cor_pes))
        fx += f.getlength(t)
    if tag:
        t = ' ' + tag
        d.text((fx, y), t, font=f, fill=tag_cor)
        fx += f.getlength(t)
    return fx


def principal():
    img = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((28, 18), 'RP Tag 5.9.0 — TÍTULOS com cor modificável',
           font=fonte(24), fill=BRANCO)
    d.text((28, 50), 'aparece no TAB, no chat e no nametag acima da cabeça  ·  '
                     '/titulo definir · /titulo cor · /titulo cor pessoal',
           font=fonte(14, True), fill=CINZA)

    # ---------------- TAB ----------------
    painel(d, 28, 84, 520, 250, 'TAB — lista de jogadores')
    jogadores = [
        ('Sagrado', 0xFFD75E, 'Seraphiel', None, 0xFFFFFF, 'RP'),
        ('Warden', 0x6FB3FF, 'Myra', None, 0xFFFFFF, 'OFF RP'),
        ('Chronos', 0xC9A6FF, 'Nephisto', 'O Ferreiro', 0xFFFFFF, 'RP'),
        ('Morte', 0xFF7B7B, 'Kael', 'O Ceifador', 0xFFAA00, 'RP'),
        ('Almas', 0x2FE0C0, 'Astrael', None, 0xFFFFFF, 'OFF RP'),
        ('Player', 0xBFBFBF, 'Edu', None, 0xFFFFFF, 'RP'),
    ]
    y = 116
    for etq, cor, nome, pes, cpes, tag in jogadores:
        d.rectangle([40, y - 4, 536, y + 24], fill=(31, 33, 40))
        titulo_composto(img, 52, y, etq, cor, nome, pes, cpes, 16,
                        tag=('(' + tag + ')'),
                        tag_cor=AQUA if tag == 'RP' else CINZA_TAG)
        y += 34

    # ---------------- NAMETAG ----------------
    painel(d, 572, 84, 580, 250, 'acima da cabeça (nametag)')
    cx = 862
    # ceu/mundo
    d.rectangle([586, 112, 1138, 322], fill=(30, 46, 62))
    d.rectangle([586, 250, 1138, 322], fill=(38, 60, 38))
    # corpo (bloco do jogador)
    d.rectangle([cx - 26, 200, cx + 26, 300], fill=(58, 74, 128))
    d.rectangle([cx - 14, 214, cx + 14, 244], fill=(196, 150, 120))  # rosto
    # nametag
    rotulo = 'Chronos'
    cor_etq = 0xC9A6FF
    largura = 0
    f = fonte(16)
    for t, cor in [('[' + rotulo + '] ', cor_etq), ('Nephisto', 0xFFFFFF),
                   (' [O Ferreiro]', 0xFFAA00), (' (RP)', 0x55FFFF)]:
        largura += f.getlength(t)
    d.rounded_rectangle([cx - largura / 2 - 10, 236, cx + largura / 2 + 10, 264],
                        6, fill=(12, 12, 16, 200))
    titulo_composto(img, cx - largura / 2, 240, rotulo, cor_etq, 'Nephisto',
                    'O Ferreiro', 0xFFAA00, 16)
    d.text((cx, 330), 'o mesmo texto do TAB, virado pra câmera', font=fonte(13, True),
           fill=CINZA, anchor='mm')

    # ---------------- CHAT ----------------
    painel(d, 28, 350, 520, 178, 'chat (mensagem)')
    y = 384
    for etq, cor, nome, pes, cpes, texto in [
        ('Chronos', 0xC9A6FF, 'Nephisto', 'O Ferreiro', 0xFFAA00,
         'a membrana não se rompe hoje.'),
        ('Sagrado', 0xFFD75E, 'Seraphiel', None, 0xFFFFFF,
         'que seja feita a luz.'),
        ('Morte', 0xFF7B7B, 'Kael', 'O Ceifador', 0xFFAA00,
         'eu já contei as suas vidas.'),
    ]:
        fim = titulo_composto(img, 42, y, etq, cor, nome, pes, cpes, 15, tag=None)
        d.text((fim + 6, y), ':', font=fonte(15), fill=CINZA)
        d.text((fim + 14, y), texto, font=fonte(15, True), fill=(205, 210, 222))
        y += 46

    # ---------------- PALETA ----------------
    painel(d, 572, 350, 580, 178, 'paleta pronta — /titulo paleta')
    x, y = 592, 386
    for i, (nome, cor) in enumerate(PALETA):
        txt = '[' + nome + ']'
        d.text((x, y), txt, font=fonte(15), fill=rgb(cor))
        x += 182
        if (i + 1) % 3 == 0:
            x = 592
            y += 30

    # ---------------- COMANDOS ----------------
    painel(d, 28, 544, 1124, 188, 'como pintar — a cor é livre')
    linhas = [
        ('/titulo definir @a Chronos', 'etiqueta [Chronos] em todo mundo (cor de fábrica: lilás)'),
        ('/titulo cor @a ouro', 'pinta a ETIQUETA de dourado (#FFAA00)'),
        ('/titulo cor Steve #FFD700', 'HEX livre — qualquer cor (#RRGGBB; também vale FFD700 e #FFF)'),
        ('/titulo cor pessoal Steve vermelho', 'pinta o [O Ferreiro] que vem DEPOIS do nome'),
        ('/titulo cor @a padrao', 'volta pra cor de fábrica da etiqueta'),
        ('/titulo ver Steve', 'mostra a etiqueta, o título pessoal e as duas cores em HEX'),
    ]
    y = 578
    for cmd, explica in linhas:
        d.text((44, y), cmd, font=fonte(15), fill=(255, 214, 120))
        d.text((470, y + 1), explica, font=fonte(14, True), fill=CINZA)
        y += 25

    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'titulos_5.9.0.png'
    img.save(destino)
    print('ok', destino)


if __name__ == '__main__':
    principal()
