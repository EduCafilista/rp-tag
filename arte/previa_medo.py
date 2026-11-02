"""Previa (mock) do aviso MEDOOP — 5.8.1.

Desenha, em 4 momentos, o MedoOpAvisoFx.java usando a MESMA matematica do
codigo Java (parede de tijolos + pichacoes + maos, lanterna em leque radial,
olhos que somem perto da luz, silhueta crescendo a cada apagao, rachaduras da
membrana e o texto final).

  t = 1,6 s   a lanterna abre a visao (medo ~4%)
  t = 5,0 s   2 apagoes: a silhueta ja entrou
  t = 9,5 s   a membrana rachou inteira, silhueta no seu rosto (medo 100%)
  t = 13,5 s  "ELE ESTA ATRAS DE VOCE"

O texto no jogo e a fonte do Minecraft; aqui e uma aproximacao (DejaVu Sans Bold).

uso: python arte/previa_medo.py   ->  previa/medo_5.8.1.png
"""
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / 'previa'
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

W, H = 960, 640
CX, CY = W / 2.0, 285.0
DUR, TEXT_T, CPS = 16.0, 3.0, 20.0
ADV = [2.4, 4.0, 5.4, 6.7, 7.9, 9.0]

PALE = (0xE2, 0xE6, 0xEE)
COLD = (0x96, 0xA0, 0xB2)
SANGRIA = (0x70, 0x1C, 0x18)
TAU = math.tau

PHRASES = ['nao olhe', 'esta atras de voce', 'voce ouviu isso?', 'ele sabe que voce viu',
           'continue andando', 'nao pare', 'esta mais perto', 'nao vire']
PICHACOES = ['NAO OLHE', 'ELE ESTA AQUI', 'SAIA', 'NAO DURMA', 'ELE OUVE', 'CORRA', 'NAO ESTA SOZINHO']
PICHA_POS = [(70, 150, -8, 46), (560, 120, 5, 40), (140, 330, 4, 54), (640, 300, -6, 44),
             (330, 210, 3, 36), (780, 200, 9, 34), (420, 380, -4, 38)]


# ------------------------------------------------------------------ matematica
def cl(x):
    return 0.0 if x < 0 else min(1.0, x)


def ease(x):
    u = cl(x)
    return u * u * (3 - 2 * u)


def fr(x):
    return x - math.floor(x)


def hash01(i):
    return fr(math.sin(i * 12.9898 + 7 * 78.233) * 43758.547)


def mix(c1, c2, u):
    u = cl(u)
    return tuple(int(round(a + (b - a) * u)) for a, b in zip(c1, c2))


def raio_luz(medo):
    return 210.0 - 95.0 * medo


# --------------------------------------------------------------- rachaduras
RACHAS = []
racha_max = [0.0]
_rnd = random.Random(5)


def crescer(x, y, ang, ln, prof, d):
    if len(RACHAS) > 2600:
        return
    w0 = max(0.8, 3.6 - prof * 0.9)
    passo = 11.0
    cx, cy = x, y
    l = 0.0
    while l < ln:
        if len(RACHAS) > 2600:
            return
        ang += (_rnd.random() - 0.5) * 0.8
        nx, ny = cx + math.cos(ang) * passo, cy + math.sin(ang) * passo
        RACHAS.append((cx, cy, nx, ny, max(0.6, w0 * (1 - 0.6 * l / ln)), d + l))
        cx, cy = nx, ny
        if prof < 4 and _rnd.random() < 0.09:
            crescer(cx, cy, ang + (1 if _rnd.random() < 0.5 else -1) * (0.6 + _rnd.random() * 0.8),
                    ln * 0.55 * (0.6 + _rnd.random() * 0.5), prof + 1, d + l + passo)
        if cx < -40 or cx > W + 40 or cy < -40 or cy > H + 40:
            break
        l += passo
    racha_max[0] = max(racha_max[0], d + ln)


for _i in range(9):
    crescer(CX, 230.0, _i * TAU / 9.0 + (_rnd.random() - 0.5) * 0.4,
            300.0 + _rnd.random() * 150.0, 0, 0.0)

# olhos soltos (posicoes fixas, como o "reposiciona" sorteia no jogo)
_rnd2 = random.Random(41)
OLHOS = [(60 + _rnd2.random() * (W - 120), 90 + _rnd2.random() * 340,
          2 + _rnd2.random() * 3, 9 + _rnd2.random() * 9) for _ in range(8)]


# ------------------------------------------------------------------- desenho
def fonte(px):
    return ImageFont.truetype(FONT, max(7, int(round(px))))


def texto_girado(base, txt, cx, cy, tam, ang, cor, alpha=255):
    """Desenha um texto girado, centrado em (cx, cy)."""
    f = fonte(tam)
    prov = Image.new('RGBA', (int(tam * len(txt) * 1.4) + 40, int(tam * 2.6) + 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(prov)
    d.text((prov.width / 2, prov.height / 2), txt, font=f, fill=cor + (alpha,), anchor='mm')
    prov = prov.rotate(ang, resample=Image.BICUBIC, expand=False)
    base.alpha_composite(prov, (int(cx - prov.width / 2), int(cy - prov.height / 2)))


def texto_centro(base, txt, cx, cy, tam, cor, alpha=255, sombra=None):
    d = ImageDraw.Draw(base)
    f = fonte(tam)
    if sombra:
        d.text((cx + 1, cy + 1), txt, font=f, fill=sombra + (alpha,), anchor='mm')
    d.text((cx, cy), txt, font=f, fill=cor + (alpha,), anchor='mm')


def texto_espacado(base, txt, cx, cy, tam, esp, cor, alpha=255):
    f = fonte(tam)
    larguras = [f.getlength(c) for c in txt]
    total = sum(larguras) + esp * (len(txt) - 1)
    x = cx - total / 2
    d = ImageDraw.Draw(base)
    for c, lw in zip(txt, larguras):
        d.text((x, cy), c, font=f, fill=cor + (alpha,), anchor='lm')
        x += lw + esp


def corpo(base, x, pe, s, altura, cor):
    topo = pe - altura
    hcab = 0.15 * altura
    caby = topo + hcab * 0.5
    ombro = topo + hcab * 1.15
    quadril = topo + altura * 0.62
    d = ImageDraw.Draw(base)
    d.ellipse([x - 20 * s, caby - hcab * 0.52, x + 20 * s, caby + hcab * 0.52], fill=cor)   # cabeca
    d.rectangle([x - 8 * s, caby + hcab * 0.42, x + 8 * s, caby + hcab * 0.97], fill=cor)  # pescoco
    d.polygon([(x - 34 * s, ombro), (x + 34 * s, ombro),
               (x + 22 * s, quadril), (x - 22 * s, quadril)], fill=cor)                  # tronco
    mao = quadril + altura * 0.26
    for lado in (-1, 1):
        d.polygon([(x + lado * 34 * s, ombro), (x + lado * 41 * s, ombro + 6 * s),
                   (x + lado * 46 * s, mao), (x + lado * 38 * s, mao)], fill=cor)        # bracos
    d.rectangle([x - 19 * s, quadril, x - 4 * s, pe], fill=cor)                          # pernas
    d.rectangle([x + 4 * s, quadril, x + 19 * s, pe], fill=cor)
    return caby


def figura(base, k):
    """Silhueta alta: mais perto/maior a cada apagao (mesmas contas do Java)."""
    frac = k / len(ADV)
    s = 0.45 + 0.62 * frac
    x = CX + 210 * (1 - frac) + (8 * math.sin(k * 2.3) if k > 0 else 0)
    pe = 330 + 240 * frac
    altura = 150 + 280 * frac
    # contorno frio (sem ele o preto some no escuro)
    corpo(base, x, pe, s * 1.10, altura * 1.015, (0x3A, 0x47, 0x60, 115))
    caby = corpo(base, x, pe, s, altura, (0, 0, 0, 248))
    return x, caby, s


def maos(base):
    for (mx, my, rot, s) in [(250, 260, -12, 1.6), (700, 390, 14, 1.4), (90, 410, 6, 1.2), (830, 120, -8, 1.0)]:
        d = ImageDraw.Draw(base)
        c = SANGRIA + (158,)
        d.ellipse([mx - 14 * s, my - 17 * s, mx + 14 * s, my + 17 * s], fill=c)
        for j in range(-2, 3):
            ax = mx + j * 7 * s + rot * 2
            ay = my - 22 * s
            d.ellipse([ax - 4 * s, ay - 12 * s, ax + 4 * s, ay + 12 * s], fill=c)
        d.ellipse([mx + 11 * s, my - 9 * s, mx + 21 * s, my + 13 * s], fill=SANGRIA + (140,))


def parede(base):
    base.paste((0x10, 0x12, 0x18), (0, 0, W, H))
    d = ImageDraw.Draw(base)
    for lin in range(10):
        y = lin * 64 - 10
        impar = (lin % 2) == 1
        for col in range(-1, 13):
            x = col * 82 + (41 if impar else 0)
            brilho = cl(0.055 + 0.05 * hash01(lin * 37 + col * 11))
            cor = mix((0x23, 0x26, 0x2E), (0x3A, 0x3E, 0x48), brilho * 8)
            d.rectangle([x + 1.5, y + 1.5, x + 79, y + 61], fill=cor + (255,))
            d.rectangle([x, y, x + 82, y + 2], fill=(8, 9, 12, 230))
            d.rectangle([x, y, x + 2, y + 64], fill=(8, 9, 12, 230))
    umid = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    du = ImageDraw.Draw(umid)
    for i in range(18):
        mx, my = hash01(i * 13) * W, hash01(i * 29) * H
        rx, ry = 30 + 90 * hash01(i * 7), 20 + 60 * hash01(i * 3)
        du.ellipse([mx - rx, my - ry, mx + rx, my + ry], fill=(5, 6, 10, 90))
    base.alpha_composite(umid)


def escuridao(base, lx, ly, R, alfa=1.0):
    """Leque radial de 5 aneis (mesmas cores por vertice do Java)."""
    yy, xx = np.mgrid[0:H, 0:W]
    dist = np.hypot(xx - lx, yy - ly)
    longe = math.hypot(max(lx, W - lx), max(ly, H - ly)) * 1.35
    raios = [0.0, R * 0.45, R * 0.85, R * 1.15, longe]
    alfas = [0.0, 0.10, 0.45, 0.82, 0.965]
    a = np.interp(dist, raios, [x * alfa for x in alfas])
    arr = np.asarray(base, dtype=np.float32)
    arr *= (1.0 - a)[..., None]
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')


def brilho_add(base, cx, cy, r, cor, a, passos=10):
    """Disco com degradê radial somado (aditivo), como o Buf.brilho."""
    if a <= 0.004 or r <= 0.3:
        return
    camada = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(camada)
    for i in range(passos, 0, -1):
        u = i / passos
        rr = r * u
        al = int(255 * a * (1 - u) ** 0.8)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=cor + (al,))
    base.alpha_composite(camada)


def rachaduras(base, medo, pulso):
    revela = medo * racha_max[0] * 1.1
    if revela <= 4:
        return
    camada = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(camada)
    for (x1, y1, x2, y2, w, dd) in RACHAS:
        if dd > revela:
            continue
        borda = cl((revela - dd) / 30.0)
        al = int(255 * 0.10 * borda * pulso)
        d.line([x1, y1, x2, y2], fill=COLD + (al,), width=max(2, int(w * 3)))
        al2 = int(255 * 0.55 * borda * cl(pulso))
        d.line([x1, y1, x2, y2], fill=(0xBF, 0xD0, 0xF0, al2), width=max(1, int(w * 0.8)))
    base.alpha_composite(camada)


def olhos(base, lx, ly, R, t):
    lr = R * 0.8
    for (ex, ey, ew, esep) in OLHOS:
        if math.hypot(lx - ex, ly - ey) < lr:
            continue
        a = 0.75 if t > 1.5 else 0.0
        for lado in (-1, 1):
            x = ex + lado * esep / 2
            brilho_add(base, x, ey, 5 + ew * 2, (0xC8, 0xD8, 0xF4), 0.28 * a)
            d = ImageDraw.Draw(base)
            r = 3.4 + ew
            d.ellipse([x - 1.5, ey - r, x + 1.5, ey + r], fill=(255, 255, 255, int(255 * 0.9 * a)))


def olhos_figura(base, x, caby, s, t):
    a = ease(cl((t - 1.5) / 1.2))
    if a <= 0.02:
        return
    for lado in (-1, 1):
        ex0 = x + lado * 8 * s
        brilho_add(base, ex0, caby, 6 + 8 * s, (0xEA, 0xF0, 0xFA), 0.22 * a)
        brilho_add(base, ex0, caby, 2 + 2.5 * s, (255, 255, 255), 0.95 * a)


# --------------------------------------------------------------------- frame
def frame(t):
    medo_k = sum(1 for a in ADV if t >= a)
    alvo = 0.10 * ease(cl(t / 4)) + 0.82 * (medo_k / len(ADV)) + (0.08 if medo_k >= len(ADV) else 0)
    medo = cl(alvo)
    R = raio_luz(medo)
    lx = CX + 250 * math.sin(t * 0.37)
    ly = CY + 60 + 120 * math.cos(t * 0.29)

    base = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    parede(base)
    maos(base)

    base = escuridao(base, lx, ly, R)

    pulso = 0.65 + 0.35 * math.sin(t * 2.4) + 0.30 * math.exp(-((fr(t * 1.2) / 0.07) ** 2))
    rachaduras(base, medo, pulso)

    # a silhueta vem POR CIMA do escuro e das rachaduras (igual ao Java)
    fx, fcaby, fs = figura(base, medo_k)
    olhos_figura(base, fx, fcaby, fs, t)
    olhos(base, lx, ly, R, t)

    # pichacoes (texto girado), por cima da parede
    for i, (px, py, ang, tam) in enumerate(PICHA_POS):
        cor = (0xCD, 0xCD, 0xC4) if i % 3 == 0 else (0x78, 0x1A, 0x18)
        alfa = 140 if i % 3 == 0 else 190
        texto_girado(base, PICHACOES[i], px + 70, py + 16, tam * 0.62, ang, cor, alfa)

    # ---- sussurros nas beiradas
    for (frase, sx, sy, al) in [('nao olhe', 96, 470, 150), ('esta atras de voce', 760, 500, 140)]:
        texto_centro(base, frase, sx, sy, 15, COLD, al, sombra=(6, 8, 12))

    # ---- HUD: AVISO + MEMBRANA
    fi = ease(cl((t - 0.8) / 0.9))
    if fi > 0.02:
        texto_espacado(base, 'AVISO', CX, 44, 26, 17, PALE, int(230 * fi))
        d = ImageDraw.Draw(base)
        d.line([CX - 220, 43, CX - 90, 43], fill=PALE + (int(150 * fi),), width=2)
        d.line([CX + 90, 43, CX + 220, 43], fill=PALE + (int(150 * fi),), width=2)
        d.rectangle([740, 84, 920, 92], fill=PALE + (int(38 * fi),))
        d.rectangle([740, 84, 740 + 180 * medo, 92], fill=PALE + (int(230 * fi),))
        texto_centro(base, 'MEMBRANA', 785, 72, 13, COLD, int(255 * fi))
        texto_centro(base, f'{int(medo * 100)}%', 760, 104, 15, PALE, int(255 * fi))

    # ---- rodape escuro + linhas digitadas
    d = ImageDraw.Draw(base)
    pa = ease(cl((t - 1.8) / 1.0))
    if pa > 0.02:
        for i in range(18):
            u = i / 17.0
            y0 = H - 200 + i * (200 / 18.0)
            al = int(255 * pa * (0.92 * u * u + 0.05))
            d.rectangle([0, y0, W, y0 + 200 / 18.0 + 1], fill=(0, 0, 0, min(255, al)))

    if t >= TEXT_T:
        n = int((t - TEXT_T) * CPS)
        titulo = 'MEDO'
        letras = min(n, len(titulo))
        if letras > 0:
            texto_centro(base, titulo[:letras], CX, H - 150, 52, PALE, 255, sombra=(20, 18, 26))
        restante = n - len(titulo)
        for (txt, yy) in [('A membrana s\u00f3 se rompe quando voc\u00ea sente medo.', H - 84),
                          ('N\u00e3o olhe para tr\u00e1s.', H - 56)]:
            if restante <= 0:
                break
            m = min(restante, len(txt))
            texto_centro(base, txt[:m], CX, yy, 17, (0xC8, 0xCE, 0xE0), 255)
            restante -= len(txt)

    # ---- "ELE ESTA ATRAS DE VOCE"
    if medo_k >= len(ADV) and t >= ADV[-1] + 0.9:
        a = ease(cl((t - (ADV[-1] + 0.9)) / 0.5))
        off = 2 + 3 * abs(math.sin(t * 11))
        s = 'ELE EST\u00c1 ATR\u00c1S DE VOC\u00ca'
        texto_centro(base, s, CX - off, 205, 33, (0x78, 0x8C, 0xFF), int(140 * a))
        texto_centro(base, s, CX + off, 205, 33, (0xFF, 0x78, 0x78), int(140 * a))
        texto_centro(base, s, CX, 205, 33, PALE, int(255 * a), sombra=(10, 10, 16))

    # ---- vinheta + escuridao de entrada
    yy, xx = np.mgrid[0:H, 0:W]
    dx = np.minimum(xx, W - xx) / (W / 2.0)
    dy = np.minimum(yy, H - yy) / (H / 2.0)
    borda = np.clip(np.minimum(dx, dy) / 0.30, 0, 1) ** 1.4
    vin = (0.42 + 0.22 * medo) * (1 - borda)
    arr = np.asarray(base, dtype=np.float32)
    arr[..., :3] *= (1 - vin)[..., None]
    if t < 1.2:
        arr[..., :3] *= ease(cl(t / 1.2))
    base = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    return base.convert('RGB')


# --------------------------------------------------------------------- montagem
def principal():
    tempos = [1.6, 5.0, 9.5, 13.5]
    legendas = ['1,6 s  a lanterna abre a visao',
                '5,0 s  2 apagoes: a silhueta entrou',
                '9,5 s  a membrana rachou (medo 100%)',
                '13,5 s  ELE EST\u00c1 ATR\u00c1S DE VOC\u00ca']
    mini_w = 640
    esc = mini_w / W
    mini_h = int(H * esc)
    margem, topo, rodape = 18, 52, 26
    grade = Image.new('RGB', (mini_w * 2 + margem * 3, (mini_h + rodape) * 2 + margem * 2 + topo),
                      (14, 14, 18))
    d = ImageDraw.Draw(grade)
    d.text((margem, 14), 'RP Tag 5.8.1 — aviso MEDOOP (estilo 22)  ·  /rp admin avisar <alvo> medoop <texto>',
           font=fonte(20), fill=(230, 232, 240))
    for i, (t, leg) in enumerate(zip(tempos, legendas)):
        im = frame(t).resize((mini_w, mini_h), Image.LANCZOS)
        x = margem + (i % 2) * (mini_w + margem)
        y = topo + margem + (i // 2) * (mini_h + rodape + margem)
        grade.paste(im, (x, y))
        d.text((x, y + mini_h + 5), leg, font=fonte(16), fill=(200, 204, 214))
    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'medo_5.8.1.png'
    grade.save(destino)
    print('ok', destino)


if __name__ == '__main__':
    principal()
