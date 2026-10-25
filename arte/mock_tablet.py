#!/usr/bin/env python3
"""Mock do tablet (4.8.5) — layout da referência: painel alto à direita,
cabeçalho corrido, rótulo em cima do valor, botões em gradiente."""
import math, random
from PIL import Image, ImageDraw, ImageFont

GUIA = "/home/user/rp-tag/src/main/resources/assets/rptag/textures/gui/"
F = "/home/user/rp-tag/arte/fonte_mock_monocraft.ttf"

TXT = (242, 236, 255)
FRACO = (143, 136, 173)
ROSA = (224, 123, 255)
VIOLETA = (176, 75, 255)
LILAS = (217, 199, 255)
PAINEL = (20, 11, 46)


def mix(c1, c2, k):
    return tuple(int(a + (b - a) * k) for a, b in zip(c1, c2))


def argb(c):
    return (c >> 16 & 255, c >> 8 & 255, c & 255)


class Font:
    def __init__(self, s, tam=8.2):
        self.s = s
        self.f = ImageFont.truetype(F, int(tam * s))

    def w(self, t):
        return self.f.getlength(t) / self.s

    def draw(self, d, t, x, y, cor):
        d.text((x * self.s, y * self.s), t, font=self.f, fill=cor)


def split(txt, larg, f):
    linhas, atual = [], ""
    for palavra in txt.split(" "):
        teste = (atual + " " + palavra).strip()
        if f.w(teste) <= larg or not atual:
            atual = teste
        else:
            linhas.append(atual)
            atual = palavra
    if atual:
        linhas.append(atual)
    return linhas


def fio(d, s, x1, x2, y, cor, alpha):
    n = min(40, max(6, int((x2 - x1) // 6)))
    for i in range(n):
        k = (i + 0.5) / n
        a = int(alpha * math.sin(math.pi * k))
        if a <= 2:
            continue
        xa = x1 + (x2 - x1) * i / n
        xb = x1 + (x2 - x1) * (i + 1) / n
        d.rectangle([xa * s, y * s, xb * s, (y + 1) * s], fill=(*argb(cor), a))


def losango(d, s, cx, cy, r, cor, a=1.0):
    d.polygon([(cx * s, (cy - r) * s), ((cx + r) * s, cy * s), (cx * s, (cy + r) * s),
               ((cx - r) * s, cy * s)], fill=(*argb(cor), int(255 * a)))


def boneco(d, s, cxm, base, k):
    cont = (18, 20, 34, 220)

    def bloco(x0, y0, x1, y1, cor):
        d.rounded_rectangle([(cxm + x0 * k) * s, (base + y0 * k) * s,
                             (cxm + x1 * k) * s, (base + y1 * k) * s],
                            radius=max(1, int(k)) * s, fill=cor, outline=cont, width=max(1, int(k)))

    bloco(-5, -30, -1, -2, (44, 52, 88, 255))
    bloco(1, -30, 5, -2, (48, 57, 96, 255))
    bloco(-6, -56, 6, -29, (66, 82, 132, 255))
    bloco(-9, -54, -6, -32, (58, 72, 116, 255))
    bloco(6, -54, 9, -32, (58, 72, 116, 255))
    bloco(-5, -67, 5, -56, (214, 174, 145, 255))
    d.rectangle([(cxm - 3 * k) * s, (base - 62 * k) * s, (cxm - 1 * k) * s, (base - 60 * k) * s],
                fill=(38, 32, 46, 255))
    d.rectangle([(cxm + 1 * k) * s, (base - 62 * k) * s, (cxm + 3 * k) * s, (base - 60 * k) * s],
                fill=(38, 32, 46, 255))


def desenha(W, H, dados, out, s=4, cap=7, rolagem=0.0):
    img = Image.new("RGB", (W * s, H * s), (7, 5, 16))
    d = ImageDraw.Draw(img, "RGBA")
    f = Font(s)

    # ---------- céu: degradê + névoa (violeta/magenta) + estrelas
    grad = Image.new("RGBA", (W * s, H * s))
    gd = ImageDraw.Draw(grad)
    for i in range(H * s):
        k = i / (H * s)
        gd.line([(0, i), (W * s, i)], fill=(int(6 + 20 * k), int(2 + 5 * k), int(16 + 40 * k), 255))
    img.paste(grad, (0, 0), grad)

    nevoa = Image.open(GUIA + "ficha_nevoa.png").convert("RGBA")
    for (cor, alpha, ox, oy, esc) in [((133, 107, 255), 26, -0.30, -0.25, 0.85),
                                      ((219, 112, 255), 17, 0.45, -0.10, 0.72),
                                      ((92, 143, 255), 24, 0.05, 0.42, 0.85)]:
        cam = nevoa.copy()
        cam = cam.resize((int(W * esc * s), int(H * 0.95 * s)), Image.LANCZOS)
        tint = Image.new("RGBA", cam.size, cor + (0,))
        tint.putalpha(cam.split()[3].point(lambda v: int(v * alpha / 255 * 2.2)))
        img.paste(tint, (int(ox * W * s), int(oy * H * s)), tint)

    random.seed(9)
    for _ in range(190):
        x, y = random.uniform(0, W), random.uniform(0, H)
        b = random.randint(30, 110)
        r = random.choice([0.5, 0.5, 1])
        d.ellipse([(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s],
                  fill=(b, b, min(255, b + 30), 240))
    for (x, y, r) in [(0.10, 0.15, 1.6), (0.30, 0.85, 1.4), (0.62, 0.12, 1.3), (0.88, 0.70, 1.5)]:
        d.ellipse([(x * W - r) * s, (y * H - r) * s, (x * W + r) * s, (y * H + r) * s],
                  fill=(240, 236, 255, 245))

    # ---------- geometria (mesma do Java)
    pw = max(286, min(int(W * 0.42), W - 60))
    px = W - 18 - pw
    py = max(58, int(H * 0.19))
    ph = H - 40 - py

    # personagem
    cx = int(px * 0.50)
    chao = int(H * 0.80)
    raio = int(min(W, H) * 0.22)
    escala = H * 0.135
    k = 2 * escala / 65.0
    bur = Image.open(GUIA + "ficha_buraco.png").convert("RGBA")
    ane = Image.open(GUIA + "ficha_anel.png").convert("RGBA")
    rbur = int(raio * (640 / 118) * s)
    b2 = bur.resize((rbur, rbur), Image.LANCZOS)
    img.paste(b2, (int(cx * s - rbur / 2), int((chao - raio * 1.05) * s - rbur / 2)), b2)
    ran = int(raio * 0.95 * 2 * s)
    a2 = ane.resize((ran, ran // 2), Image.LANCZOS)
    img.paste(a2, (int(cx * s - ran / 2), int((chao + 4) * s - ran / 4)), a2)
    d = ImageDraw.Draw(img, "RGBA")
    boneco(d, s, cx, chao, k)

    # ---------- cabeçalho
    f.draw(d, "RP TAG · LORE", 20, 10, FRACO)
    dirr = "Sua história começa aqui"
    f.draw(d, dirr, W - 20 - f.w(dirr), 10, FRACO)
    fio(d, s, 20, W - 20, 22, 0xFFFFFF, 34)
    titulo = f"CAPÍTULO {cap + 1} / RESUMO"
    f.draw(d, titulo, W / 2 - f.w(titulo) / 2, 28, LILAS)
    perg = dados["pergunta"]
    f.draw(d, perg, W / 2 - f.w(perg) / 2, 44, (255, 255, 255))

    # ---------- painel
    d.rectangle([(px - 3) * s, (py - 3) * s, (px + pw + 3) * s, (py + ph + 3) * s], fill=(0, 0, 0, 54))
    painel = Image.new("RGBA", (pw * s, ph * s))
    pp = ImageDraw.Draw(painel)
    for i in range(ph * s):
        kk = i / max(1, ph * s - 1)
        pp.line([(0, i), (pw * s, i)], fill=(20, 11, 46, int(200 + 22 * kk)))
    pp.rectangle([0, 0, pw * s - 1, 44 * s], fill=(255, 255, 255, 10))
    img.paste(painel, (px * s, py * s), painel)
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([px * s, py * s, (px + pw) * s - 1, (py + ph) * s - 1], outline=(*argb(0xA24BFF), 102))
    for (ccx, ccy) in [(px, py), (px + pw, py), (px, py + ph), (px + pw, py + ph)]:
        losango(d, s, ccx, ccy, 3.5, 0xE07BFF, 0.85)
    fio(d, s, px + 14, px + pw - 14, py + 40, 0xE07BFF, 85)

    ix, iw = px + 14, pw - 28

    # X de fechar
    bx, by = px + pw - 20, py + 6
    d.rectangle([bx * s, by * s, (bx + 18) * s - 1, (by + 18) * s - 1],
                fill=(176, 18, 36, 200), outline=(255, 154, 138, 210))
    for i in range(9):
        a, b3 = 4 + i, 4 + (8 - i)
        d.rectangle([(bx + a) * s, (by + a) * s, (bx + a + 2) * s, (by + a + 2) * s], fill=(206, 192, 232))
        d.rectangle([(bx + b3) * s, (by + a) * s, (bx + b3 + 2) * s, (by + a + 2) * s], fill=(206, 192, 232))

    # dica
    ly = py + 14
    for ln in split(dados["dica"], iw, f):
        f.draw(d, ln, ix, ly, FRACO)
        ly += 10

    # ---------- resumo (rótulo em cima, valor embaixo, fio)
    topo = py + 48
    limite = py + ph - 22 - 22
    campos = [("NOME", dados["nome"], 2), ("IDADE", dados["idade"], 1),
              ("VEIO DE", dados["origem"], 2), ("OCUPAÇÃO", dados["oficio"], 2),
              ("HABILIDADES", dados["habilidades"], 2), ("HISTÓRIA", dados["historia"], 3)]

    def altura_barras():
        return 12 + max(1, len(dados["alma_barras"])) * 14 + 8

    def altura_total():
        h = 0
        for _, v, mx in campos:
            h += 11 + min(len(split(v, iw, f)), mx) * 10 + 10
        return h + altura_barras()

    sc_max = max(0, altura_total() - (limite - topo))
    off = min(rolagem, sc_max)

    camada = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dc = ImageDraw.Draw(camada, "RGBA")
    y = topo - off
    for n, (rot, val, mx) in enumerate(campos):
        if n == 5:   # ALMA entra antes da HISTÓRIA
            dc.text((ix * s, y * s), "ALMA", font=f.f, fill=(*FRACO, 255))
            lb = 56
            x1, x2 = ix + lb, ix + iw - lb
            cx2 = (x1 + x2) // 2
            ly2 = y + 12
            for (i, v) in dados["alma_barras"]:
                dirr = v > 50
                esq, dta = dados["eixos"][i]
                dc.text((ix * s, ly2 * s), esq, font=f.f, fill=(*(TXT if not dirr else FRACO), 217))
                dc.text(((ix + iw - f.w(dta)) * s, ly2 * s), dta, font=f.f,
                        fill=(*(TXT if dirr else FRACO), 217))
                by2 = ly2 + 8
                dc.rectangle([x1 * s, by2 * s, x2 * s, (by2 + 1) * s], fill=(*argb(0x3A3268), 255))
                pxv = x1 + round((x2 - x1) * v / 100)
                dc.rectangle([min(cx2, pxv) * s, by2 * s, max(cx2, pxv) * s, (by2 + 1) * s],
                             fill=(*ROSA, 230))
                for kk in range(5):
                    mk = x1 + kk * (x2 - x1) // 4
                    dc.rectangle([mk * s, (by2 - 2) * s, (mk + 1) * s, (by2 + 3) * s],
                                 fill=(*argb(0x4A3F86), 255))
                dc.rectangle([(pxv - 2) * s, (by2 - 4) * s, (pxv + 3) * s, (by2 + 5) * s],
                             fill=(*argb(0xCBBFFF), 255))
                ly2 += 14
            fio(dc, s, ix, ix + iw, ly2 + 2, 0xFFFFFF, 51)
            y = ly2 + 8
        dc.text((ix * s, y * s), rot, font=f.f, fill=(*FRACO, 255))
        partes = split(val, iw, f)
        linhas = min(len(partes), mx)
        truncou = len(partes) > linhas
        yy = y + 11
        for i in range(linhas):
            dc.text((ix * s, yy * s), partes[i], font=f.f, fill=(*TXT, 255))
            if truncou and i == linhas - 1:
                dc.text(((ix + f.w(partes[i]) + 1) * s, yy * s), "…", font=f.f, fill=(*FRACO, 255))
            yy += 10
        fio(dc, s, ix, ix + iw, yy + 2, 0xFFFFFF, 51)
        y = yy + 8

    caixa = (int((ix - 2) * s), int((topo - 2) * s), int((ix + iw + 2) * s), int(limite * s))
    rec = camada.crop(caixa)
    img.paste(rec, (caixa[0], caixa[1]), rec)
    d = ImageDraw.Draw(img, "RGBA")

    if sc_max > 0:
        fade = Image.new("RGBA", (int((iw + 4) * s), int(12 * s)), (*PAINEL, 255))
        alpha = Image.linear_gradient("L").resize(fade.size)
        fade.putalpha(alpha)
        img.paste(fade, (int((ix - 2) * s), int((limite - 12) * s)), fade)
        alt = limite - topo
        frac = alt / (alt + sc_max)
        hb = max(14, round(alt * frac))
        yb = topo + round((alt - hb) * (off / sc_max if sc_max else 0))
        d.rectangle([(ix + iw + 1) * s, topo * s, (ix + iw + 3) * s, limite * s], fill=(255, 255, 255, 36))
        d.rectangle([(ix + iw + 1) * s, yb * s, (ix + iw + 3) * s, (yb + hb) * s], fill=(*ROSA, 216))

    # ---------- botão (gradiente da referência)
    bh = 22
    bxx, byy = ix, py + ph - bh - 10
    for i in range(bh * s):
        kk = i / (bh * s - 1)
        cor = mix((122, 31, 214), (74, 18, 160), kk)
        d.line([(bxx * s, byy * s + i), ((bxx + iw) * s, byy * s + i)], fill=(*cor, 255))
    d.rectangle([(bxx + 1) * s, (byy + 1) * s, (bxx + iw - 1) * s, (byy + 2) * s], fill=(255, 255, 255, 60))
    d.rectangle([(bxx + 1) * s, (byy + bh - 4) * s, (bxx + iw - 1) * s, (byy + bh - 1) * s], fill=(0, 0, 0, 50))
    d.rectangle([bxx * s, byy * s, (bxx + iw) * s - 1, (byy + bh) * s - 1], outline=(*argb(0xE07BFF), 230))
    t = "Confirmar personagem"
    f.draw(d, t, bxx + iw / 2 - f.w(t) / 2, byy + (bh - 8) / 2, (255, 255, 255))

    # ---------- rodapé
    ini = W // 2 - 8 * 10
    y2 = H - 28
    for i in range(8):
        xx = ini + i * 20
        cor = ROSA if i == 7 else (VIOLETA if i < 7 else (58, 26, 110))
        d.rectangle([xx * s, y2 * s, (xx + 6) * s, (y2 + 5) * s], fill=(*cor, 242))
        if i == 7:
            d.rectangle([xx * s, (y2 - 2) * s, (xx + 6) * s, (y2 - 1) * s], fill=(255, 214, 255, 230))
    fio(d, s, 20, W - 20, H - 18, 0xFFFFFF, 34)
    f.draw(d, "ESC · Sair", 20, H - 12, FRACO)
    en = "ENTER · Confirmar"
    f.draw(d, en, W - 20 - f.w(en), H - 12, FRACO)

    img.save(out)
    return dict(painel=(px, py, pw, ph), rolagem_max=sc_max)


eixos = [("Prudente", "Corajoso"), ("Pragmático", "Empático"), ("Espontâneo", "Disciplinado"),
         ("Tradicional", "Curioso"), ("Reservado", "Sociável"), ("Contente", "Ambicioso")]

dados = dict(
    nome="Eduardo", idade="27 anos",
    origem="Uberlândia, Minas Gerais — o portão do cerrado",
    oficio="Cartógrafo de mundos mortos e contador de histórias",
    habilidades="Lê ruínas como quem lê livros, costura mapas improváveis e some nas sombras quando o sol cai.",
    alma_barras=[(3, 78), (1, 72), (4, 18)],
    eixos=eixos,
    historia="Cresceu ouvindo que o mundo acabara três vezes antes de ele nascer. Passou a juventude desenhando rotas para lugares que ninguém queria visitar, e foi assim que aprendeu: toda ruína tem memória, e toda memória pede alguém que a conte.",
    dica="Confira com calma. Depois de confirmar, o nome passa a ser o do personagem.",
    pergunta="É assim que você será lembrado?")

for (W, H, nome) in [(634, 321, "634"), (960, 470, "960")]:
    print(nome, desenha(W, H, dados, f"/home/user/rp-tag/arte/tablet_{nome}.png"))
