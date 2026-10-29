"""Previa (mock) do aviso EXPURGO — 4.9.2.

Desenha, em 4 momentos, a TV de emergencia do ExpurgoAvisoFx.java:
  t = 2.2 s  anuncio digitado ("ESTE NAO E UM TESTE.")
  t = 3.2 s  contagem 5-4-3-2-1 (numero gigante + onda vermelha)
  t = 6.2 s  HORA DO EXPURGO (letras caindo, separacao RGB, listras de perigo)
  t = 8.4 s  sirene + mascaras sorridentes subindo dos cantos

O texto no jogo e a fonte do Minecraft; aqui e uma aproximacao (DejaVu Sans Bold).

uso: python arte/previa_expurgo.py   ->  previa/expurgo_4_9_2.png
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / 'previa'
W, H = 1280, 720
DUR, T_CONT, T_SLAM, T_FIM = 11.8, 2.6, 5.4, 9.6

RED = (0xE0, 0x14, 0x14)
DRED = (0x5A, 0x00, 0x00)
BLUE = (0x28, 0x50, 0xFF)
YEL = (0xFF, 0xC8, 0x00)
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)

FONTE_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONTE = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def cl(x):
    return 0.0 if x < 0 else min(1.0, x)


def ss(a, b, x):
    u = cl((x - a) / (b - a))
    return u * u * (3 - 2 * u)


def eo(x):
    x = cl(x)
    return 1 - (1 - x) ** 3


def cor(rgb, a):
    return rgb + (int(max(0.0, min(1.0, a)) * 255),)


def fonte(tam, negrito=True):
    try:
        return ImageFont.truetype(FONTE_B if negrito else FONTE, int(tam))
    except OSError:
        return ImageFont.load_default()


def texto_centro(d, txt, cx, y, f, fill, contorno=True):
    """Texto centrado em cx com o topo em y (contorno preto de 1 px)."""
    larg = d.textlength(txt, font=f)
    x = cx - larg / 2
    if contorno:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            d.text((x + ox, y + oy), txt, font=f, fill=(0, 0, 0, fill[3]))
    d.text((x, y), txt, font=f, fill=fill)


def hazard(img, y, hgt, off, a):
    """Faixa de perigo amarela e preta em diagonal, deslizando."""
    d = ImageDraw.Draw(img)
    d.rectangle([0, y, W, y + hgt], fill=cor(BLACK, 0.92 * a))
    sw = hgt * 0.9
    per = sw * 2
    o = off % per
    x = -hgt * 2 + o
    while x < W + hgt:
        d.polygon([(x, y + hgt), (x + sw, y + hgt), (x + sw + hgt, y), (x + hgt, y)], fill=cor(YEL, a))
        x += per
    faixa = 0.8 * hgt / 9
    d.rectangle([0, y, W, y + faixa], fill=cor(YEL, a))
    d.rectangle([0, y + hgt - faixa, W, y + hgt], fill=cor(YEL, a))


def mascara(d, x, y, s, a):
    """Mascara sorridente: capuz escuro, rosto palido, olhos em fenda, sorriso largo."""
    hood = cor((0x0C, 0x08, 0x0A), 0.97 * a)
    d.polygon([(x - 2.2 * s, y + 5 * s), (x + 2.2 * s, y + 5 * s),
               (x + 1.5 * s, y + 1.1 * s), (x - 1.5 * s, y + 1.1 * s)], fill=hood)
    d.ellipse([x - 1.38 * s, y - 0.1 * s - 1.72 * s, x + 1.38 * s, y - 0.1 * s + 1.72 * s], fill=hood)
    d.ellipse([x - 0.98 * s, y - 1.32 * s, x + 0.98 * s, y + 1.32 * s], fill=cor((0xDC, 0xD0, 0xBC), a))
    d.ellipse([x - 0.88 * s, y - 1.17 * s, x + 0.68 * s, y + 0.93 * s], fill=cor((0xEF, 0xE6, 0xD2), 0.55 * a))
    olho = cor((0x12, 0x0C, 0x0E), a)
    for e in (-1, 1):
        d.polygon([(x + e * 0.12 * s, y - 0.30 * s), (x + e * 0.66 * s, y - 0.46 * s),
                   (x + e * 0.62 * s, y - 0.20 * s), (x + e * 0.16 * s, y - 0.10 * s)], fill=olho)
        d.ellipse([x + e * 0.64 * s - 0.20 * s, y + 0.20 * s - 0.20 * s,
                   x + e * 0.64 * s + 0.20 * s, y + 0.20 * s + 0.20 * s], fill=cor((0xC8, 0x50, 0x50), 0.55 * a))
    lx, ly = x - 0.58 * s, y + 0.46 * s
    for i in range(1, 15):
        u = i / 14.0
        nx = x + (-0.58 + 1.16 * u) * s
        ny = y + 0.46 * s + 0.40 * s * (1 - (2 * u - 1) ** 2) - 0.18 * s * abs(2 * u - 1) ** 6
        d.line([(lx, ly), (nx, ny)], fill=olho, width=max(1, int(0.12 * s)))
        lx, ly = nx, ny


def quadro(t, msg='TODOS OS CRIMES SÃO LEGAIS DURANTE 12 HORAS'):
    random.seed(7)
    k = min(W, H) / 240.0
    cx, cy = W / 2.0, H / 2.0
    on, off = ss(0.25, 0.6, t), ss(DUR - 0.9, DUR - 0.35, t)
    red = ss(T_SLAM, T_SLAM + 0.5, t) * (1 - ss(T_FIM, T_FIM + 1.0, t))
    pulse = 0.5 + 0.5 * math.sin((t - T_SLAM) * math.tau * 0.9)

    img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, H], fill=cor(BLACK, 0.96 * on))
    if red > 0:
        d.rectangle([0, 0, W, H], fill=cor(DRED, (0.20 + 0.14 * pulse) * red))

    # vinheta
    vin = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dv = ImageDraw.Draw(vin)
    dist = min(W, H) * 0.45
    for i in range(90):
        u = i / 89.0
        dv.rectangle([0, 0, W, dist * u], fill=cor((0x12, 0x00, 0x00), (0.55 + 0.3 * red) * on * (1 - u) * 0.09))
        dv.rectangle([0, H - dist * u, W, H], fill=cor((0x12, 0x00, 0x00), (0.55 + 0.3 * red) * on * (1 - u) * 0.09))
        dv.rectangle([0, 0, dist * u, H], fill=cor((0x12, 0x00, 0x00), (0.55 + 0.3 * red) * on * (1 - u) * 0.09))
        dv.rectangle([W - dist * u, 0, W, H], fill=cor((0x12, 0x00, 0x00), (0.55 + 0.3 * red) * on * (1 - u) * 0.09))
    img = Image.alpha_composite(img, vin)
    d = ImageDraw.Draw(img)

    # estatica
    estat = (1 - ss(1.0, 1.8, t)) * 0.9 + 0.10 + 0.12 * red
    for _ in range(260):
        px, py = random.random() * W, random.random() * H
        sz = (1 + 5 * random.random()) * k * 0.5
        lum = int(0x70 + (255 - 0x70) * random.random())
        d.rectangle([px, py, px + sz * 2.2, py + sz], fill=cor((lum, lum, lum), 0.13 * estat * on))

    # listras de perigo
    faixa = ss(T_SLAM, T_SLAM + 0.7, t) * (1 - ss(T_FIM, T_FIM + 0.8, t))
    fh = 9 * k
    if faixa > 0.01:
        hazard(img, -fh + fh * 2 * faixa * 0.5 - fh * (1 - faixa), fh, t * 22 * k, faixa)
        hazard(img, H - fh * faixa, fh, -t * 22 * k, faixa)

    # mascaras
    ma = ss(T_SLAM + 1.0, T_SLAM + 2.6, t) * (1 - ss(T_FIM - 0.2, T_FIM + 0.7, t))
    MS = [(0.12, 1.00, 1.00), (0.30, 1.08, 0.82), (0.70, 1.08, 0.82), (0.88, 1.00, 1.00)]
    camada = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dm = ImageDraw.Draw(camada)
    if ma > 0.01:
        for i, (mx, my, esc) in enumerate(MS):
            rise = eo(cl((t - T_SLAM - 1.0 - 0.25 * i) / 1.2))
            s = 11 * k * esc
            mascara(dm, mx * W, H * my - rise * 0.30 * H - 0.1 * H, s, ma)
        img = Image.alpha_composite(img, camada)

    # luz lateral + cones da sirene
    if red > 0.01:
        luz = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        dl = ImageDraw.Draw(luz)
        for i in range(60):
            u = i / 59.0
            dl.rectangle([0, 0, 0.85 * H * u * 0.45, H * 0.9], fill=cor(RED, 0.40 * red * 0.05))
        e = luz.filter(ImageFilter.GaussianBlur(40))
        img = Image.alpha_composite(img, e)
        d = ImageDraw.Draw(img)
        for q in range(2):
            ox = 0 if q == 0 else W
            an = (1 if q == 0 else -1) * (t * 2.2) + (0.3 if q == 0 else 3.1416 - 0.3)
            wd = 0.20
            L = math.hypot(W, H) * 1.2
            d.polygon([(ox, H * 0.08),
                       (ox + math.cos(an - wd) * L, H * 0.08 + math.sin(an - wd) * L),
                       (ox + math.cos(an + wd) * L, H * 0.08 + math.sin(an + wd) * L)],
                      fill=cor(RED if q == 0 else BLUE, 0.22 * red))

    # ondas vermelhas da contagem
    for n in range(5):
        tau = t - (T_CONT + 0.55 * n)
        if 0 < tau < 0.5:
            u = eo(tau / 0.5)
            r = 18 * k + 70 * k * u
            d.ellipse([cx - r, cy + 6 * k - r, cx + r, cy + 6 * k + r],
                      outline=cor(RED, (1 - u) * 0.7), width=max(1, int((5 * (1 - u) + 1) * k)))

    # cabecalho
    ha = ss(0.9, 1.3, t) * (1 - off)
    if ha > 0.01:
        d.rectangle([0, 0, W, 11 * k], fill=cor(BLACK, 0.55 * ha))
        d.rectangle([0, 11 * k, W, 12.2 * k], fill=cor(RED, 0.9 * ha))
        f9 = fonte(9 * k * 0.9)
        d.text((5 * k, 2.6 * k), '● REC', font=f9, fill=cor(RED, ha))
        f9w = fonte(max(6, 9 * k * 0.9))
        d.text((24 * k, 2.6 * k), 'TRANSMISSÃO DE EMERGÊNCIA', font=f9w, fill=cor(WHITE, ha))
        secs = max(0, 43200 - max(0, t - T_SLAM) * 240)
        rel = '%02d:%02d:%02d' % (secs // 3600, (secs % 3600) // 60, secs % 60)
        larg = d.textlength(rel, font=f9w)
        d.text((W - larg - 5 * k, 2.6 * k), rel, font=f9w, fill=cor(RED if red > 0.3 else WHITE, ha))

    # anuncio digitado
    fa = (1 - ss(T_SLAM - 0.15, T_SLAM, t)) * on
    if 1.3 < t < T_SLAM + 0.05 and fa > 0.01:
        l1 = 'ESTE NÃO É UM TESTE.'
        l2 = msg
        s1 = min(len(l1), max(0, int((t - 1.3) * 22)))
        s2 = min(len(l2), max(0, int((t - 1.3 - len(l1) / 22 - 0.2) * 28)))
        f1 = fonte(k * 2.2 * 7)
        f2 = fonte(k * 1.5 * 7)
        yy = cy - 56 * k
        texto_centro(d, l1[:s1], cx, yy, f1, cor(WHITE, fa))
        texto_centro(d, l2[:s2], cx, yy + 18 * k, f2, cor((0xFF, 0xD2, 0xD2), fa))
        if t > T_CONT - 0.4:
            f3 = fonte(k * 1.5 * 7)
            texto_centro(d, 'O EXPURGO COMEÇA EM', cx, yy + 34 * k, f3, cor(RED, fa))
    if T_CONT < t < T_CONT + 0.55 * 5:
        n = 5 - int((t - T_CONT) / 0.55)
        p = ((t - T_CONT) / 0.55) % 1
        sd = k * 7.2 * (1 + 0.7 * (1 - eo(cl(p / 0.22))))
        ad = (1 - ss(0.82, 1.0, p)) * fa
        if ad > 0.03:
            texto_centro(d, str(n), cx, cy + 16 * k, fonte(sd * 7), cor(RED, ad))

    # titulo
    if t > T_SLAM:
        fim = ss(T_FIM, T_FIM + 0.5, t)
        ti = 'HORA DO EXPURGO'
        S = k * 4.6
        f = fonte(S * 7)
        larg = d.textlength(ti, font=f)
        x0 = cx - larg / 2
        ty = cy - 4 * S - 8 * k
        lt = cl((t - T_SLAM) / 0.22)
        a8 = lt * (1 - fim)
        if a8 > 0.03:
            for ox, oy in ((-2, -2), (2, -2), (-2, 2), (2, 2), (0, -2), (0, 2), (-2, 0), (2, 0)):
                d.text((x0 + ox, ty + oy), ti, font=f, fill=(0, 0, 0, int(a8 * 255)))
            d.text((x0 - 1.6, ty), ti, font=f, fill=cor((0xFF, 0x00, 0x00), a8 * 0.55))
            d.text((x0 + 1.6, ty), ti, font=f, fill=cor((0x00, 0xE5, 0xFF), a8 * 0.45))
            d.text((x0, ty), ti, font=f, fill=cor((0xFF, 0x2A, 0x22), a8))
        ma2 = ss(T_SLAM + 0.9, T_SLAM + 1.4, t) * (1 - fim)
        if ma2 > 0.03:
            texto_centro(d, msg, cx, ty + 12 * S + 4 * k, fonte(max(8, k * 1.6 * 7)), cor((0xFF, 0xE6, 0xE6), ma2))
    if ss(T_FIM, T_FIM + 0.5, t) > 0.03:
        texto_centro(d, 'QUE DEUS NOS ABENÇOE', cx, cy + 20 * k, fonte(max(8, k * 2 * 7)),
                     cor(WHITE, ss(T_FIM, T_FIM + 0.6, t) * (1 - off)))

    # CRT: linhas de varredura + cantos de camera
    for y in range(0, H, max(2, int(1.5 * k))):
        d.rectangle([0, y, W, y + max(1, int(1.5 * k * 0.45))], fill=(0, 0, 0, int(0.20 * on * 255)))
    cb, cw2 = 12 * k, 1.4 * k
    cc = cor(WHITE, 0.75 * ss(0.8, 1.2, t) * (1 - off))
    for px in (6 * k, W - 6 * k - cb):
        for py in (15 * k, H - 6 * k - cw2):
            d.rectangle([px, py, px + cb, py + cw2], fill=cc)
    for px in (6 * k, W - 6 * k - cw2):
        for py in (15 * k, H - 6 * k - cb):
            d.rectangle([px, py, px + cw2, py + cb], fill=cc)
    return img.convert('RGB')


def main():
    quadros = [
        (2.2, 'anuncio'),
        (3.35, 'contagem'),
        (6.1, 'titulo'),
        (8.3, 'sirene'),
    ]
    imgs = [quadro(t) for t, _ in quadros]
    # tira 2x2 com rotulo do instante
    folga = 8
    tira = Image.new('RGB', (W // 2 * 2 + folga * 3, H // 2 * 2 + folga * 3), (18, 18, 18))
    for i, (img, (t, _)) in enumerate(zip(imgs, quadros)):
        mini = img.resize((W // 2, H // 2), Image.Resampling.LANCZOS)
        x = folga + (i % 2) * (W // 2 + folga)
        y = folga + (i // 2) * (H // 2 + folga)
        tira.paste(mini, (x, y))
        dr = ImageDraw.Draw(tira)
        dr.text((x + 8, y + 6), 't = %.1f s' % t, font=fonte(18), fill=(255, 255, 255))
    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'expurgo_4_9_2.png'
    tira.save(destino)
    print(destino, destino.stat().st_size)

    # o quadro principal, sozinho
    principal = SAIDA / 'expurgo_4_9_2_grande.png'
    imgs[2].save(principal)
    print(principal, principal.stat().st_size)


if __name__ == '__main__':
    main()
