#!/usr/bin/env python3
"""Mock do RESUMO do tablet (4.8.4) — linguagem de CARTÕES, fonte de pixel (Monocraft)."""
import math, random
from PIL import Image, ImageDraw, ImageFont

GUIA = "/home/user/rp-tag/src/main/resources/assets/rptag/textures/gui/"
F = "/home/user/rp-tag/arte/fonte_mock_monocraft.ttf"

AZUL = (143, 216, 255)
CREME = (246, 240, 255)
FRACO = (185, 174, 220)
OURO = (255, 217, 138)
VIOLETA = (110, 95, 152)
PAINEL = (11, 7, 24)


def mix(c1, c2, k):
    return tuple(int(a + (b - a) * k) for a, b in zip(c1, c2))


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


def uma_linha(txt, larg, f):
    if f.w(txt) <= larg:
        return txt
    out = ""
    for c in txt:
        if f.w(out + c) > larg - 8:
            break
        out += c
    return out.strip() + "…"


def regua(d, s, x1, x2, y, cor, alpha):
    n = min(30, max(6, int((x2 - x1) // 5)))
    for i in range(n):
        k = (i + 0.5) / n
        a = int(alpha * math.sin(math.pi * k) * 255)
        if a <= 2:
            continue
        xa = x1 + (x2 - x1) * i / n
        xb = x1 + (x2 - x1) * (i + 1) / n
        d.rectangle([xa * s, y * s, xb * s, (y + 1) * s], fill=(*cor, a))


def losango(d, s, cx, cy, r, cor, a=1.0):
    d.polygon([(cx * s, (cy - r) * s), ((cx + r) * s, cy * s), (cx * s, (cy + r) * s),
               ((cx - r) * s, cy * s)], fill=(*cor, int(255 * a)))


def boneco(img, s, cxm, base, k, d):
    """avatar no estilo do jogo (no cliente é a skin de verdade)."""
    cont = (18, 20, 34, 220)
    def bloco(x0, y0, x1, y1, cor, r=1):
        d.rounded_rectangle([(cxm + x0 * k) * s, (base + y0 * k) * s, (cxm + x1 * k) * s, (base + y1 * k) * s],
                            radius=max(1, int(r * k)) * s, fill=cor, outline=cont, width=max(1, int(k)))
    bloco(-5, -30, -1, -2, (44, 52, 88, 255))      # perna esq
    bloco(1, -30, 5, -2, (48, 57, 96, 255))        # perna dir
    bloco(-6, -56, 6, -29, (66, 82, 132, 255))     # corpo
    bloco(-9, -54, -6, -32, (58, 72, 116, 255))    # braço esq
    bloco(6, -54, 9, -32, (58, 72, 116, 255))      # braço dir
    bloco(-5, -67, 5, -56, (214, 174, 145, 255))   # cabeça
    d.rectangle([(cxm - 3 * k) * s, (base - 62 * k) * s, (cxm - 1 * k) * s, (base - 60 * k) * s],
                fill=(38, 32, 46, 255))            # olho esq
    d.rectangle([(cxm + 1 * k) * s, (base - 62 * k) * s, (cxm + 3 * k) * s, (base - 60 * k) * s],
                fill=(38, 32, 46, 255))            # olho dir


def desenha(W, H, dados, out, s=4, rolagem=0.0):
    img = Image.new("RGB", (W * s, H * s), (8, 11, 22))
    d = ImageDraw.Draw(img, "RGBA")
    f = Font(s)

    # céu
    grad = Image.new("RGBA", (W * s, H * s))
    gd = ImageDraw.Draw(grad)
    for i in range(H * s):
        k = i / (H * s)
        gd.line([(0, i), (W * s, i)], fill=(int(10 + 7 * k), int(13 + 9 * k), int(26 + 18 * k), 255))
    img.paste(grad, (0, 0), grad)
    random.seed(5)
    for _ in range(180):
        x, y = random.uniform(0, W), random.uniform(0, H)
        b = random.randint(28, 95)
        r = random.choice([0.5, 0.5, 1])
        d.ellipse([(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s], fill=(b, b, min(255, b + 22), 240))
    for (x, y, r) in [(0.12, 0.2, 1.7), (0.87, 0.15, 1.3), (0.75, 0.85, 1.5)]:
        d.ellipse([(x * W - r) * s, (y * H - r) * s, (x * W + r) * s, (y * H + r) * s],
                  fill=(235, 242, 255, 245))

    pw = min(W - 20, 540)
    ph = min(H - 34, 276)
    px = (W - pw) // 2
    py = (H - ph) // 2
    colX = px + int(pw * 0.46)

    # sombra + painel
    d.rectangle([(px - 3) * s, (py - 3) * s, (px + pw + 3) * s, (py + ph + 3) * s], fill=(0, 0, 0, 54))
    painel = Image.new("RGBA", (pw * s, ph * s))
    pp = ImageDraw.Draw(painel)
    for i in range(ph * s):
        k = i / max(1, ph * s - 1)
        pp.line([(0, i), (pw * s, i)], fill=(11, 7, 24, int(230 + 22 * k)))
    pp.rectangle([0, 0, pw * s - 1, 16 * s], fill=(255, 255, 255, 6))
    img.paste(painel, (px * s, py * s), painel)
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([px * s, py * s, (px + pw) * s - 1, (py + ph) * s - 1], outline=(143, 216, 255, 70))
    for (cx, cy) in [(px, py), (px + pw, py), (px, py + ph), (px + pw, py + ph)]:
        losango(d, s, cx, cy, 4, OURO, 0.75)

    # cabeçalho
    c1, c2 = "CAPÍTULO 8", "· RESUMO"
    f.draw(d, c1, px + 14, py + 10, OURO)
    f.draw(d, c2, px + 14 + f.w(c1) + 4, py + 10, CREME)
    sus = "role a roda para ver tudo"
    f.draw(d, sus, px + pw - 30 - f.w(sus), py + 10, (126, 111, 168))
    regua(d, s, px + 14, px + pw - 14, py + 23, AZUL, 0.20)

    perg = "É assim que você será lembrado?"
    f.draw(d, perg, px + pw / 2 - f.w(perg) / 2, py + 33, (250, 247, 255))

    # X
    bx, by = px + pw - 22, py + 6
    d.rectangle([bx * s, by * s, (bx + 18) * s - 1, (by + 18) * s - 1],
                fill=(176, 18, 36, 200), outline=(255, 154, 138, 210))
    for i in range(9):
        a, b2 = 4 + i, 4 + (8 - i)
        d.rectangle([(bx + a) * s, (by + a) * s, (bx + a + 2) * s, (by + a + 2) * s], fill=(206, 192, 232))
        d.rectangle([(bx + b2) * s, (by + a) * s, (bx + b2 + 2) * s, (by + a + 2) * s], fill=(206, 192, 232))

    # personagem
    cxm = px + int(pw * 0.27)
    base = py + ph - 46
    k = max(0.60, min(1.0, ph / 276))
    bur = Image.open(GUIA + "ficha_buraco.png").convert("RGBA")
    ane = Image.open(GUIA + "ficha_anel.png").convert("RGBA")
    lado = int(138 * k * s)
    img.paste(bur.resize((lado, lado), Image.LANCZOS),
              (int((cxm - 69 * k) * s), int((base - 76 * k - 69 * k) * s)),
              bur.resize((lado, lado), Image.LANCZOS))
    an = ane.resize((int(110 * k * s), int(55 * k * s)), Image.LANCZOS)
    img.paste(an, (int((cxm - 55 * k) * s), int((base - 2 - 27 * k) * s)), an)
    d = ImageDraw.Draw(img, "RGBA")
    boneco(img, s, cxm, base, k * 1.55, d)

    # ---------------- RESUMO (cartões)
    x = colX
    larg = px + pw - 14 - x
    limite = py + ph - 50
    y = py + 48

    nome = dados["nome"]
    fnome = Font(s, 8.2)
    esc = 2 if fnome.w(nome) * 2 <= larg else 1
    d.text((x * s, y * s), nome, font=ImageFont.truetype(F, int(8.2 * esc * s)), fill=(255, 243, 218))

    yApoio = y + 10 * esc + 2
    apoio = uma_linha(f"{dados['idade']}  ·  {dados['origem']}", larg - 4, f)
    f.draw(d, apoio, x, yApoio, FRACO)
    regua(d, s, x, x + larg - 8, yApoio + 12, OURO, 0.30)
    topo = yApoio + 18

    camada = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dc = ImageDraw.Draw(camada, "RGBA")

    def fundo_cartao(yy, h):
        dc.rectangle([x * s, yy * s, (x + larg) * s, (yy + h) * s], fill=(255, 255, 255, 14))
        dc.rectangle([x * s, yy * s, (x + larg) * s, (yy + 1) * s], fill=(*OURO, 106))
        dc.rectangle([x * s, (yy + h - 1) * s, (x + larg) * s, (yy + h) * s], fill=(*AZUL, 26))
        dc.rectangle([x * s, yy * s, (x + 1) * s, (yy + h) * s], fill=(*AZUL, 26))
        dc.rectangle([(x + larg - 1) * s, yy * s, (x + larg) * s, (yy + h) * s], fill=(*AZUL, 26))
        losango(dc, s, x + 4, yy + 4, 1.8, OURO, 0.75)

    def cartao(yy, titulo, valor):
        linhas = split(valor, larg - 16, f)
        h = 19 + max(1, len(linhas)) * 10 + 5
        fundo_cartao(yy, h)
        dc.text(((x + 9) * s, (yy + 7) * s), titulo, font=f.f, fill=(*OURO, 216))
        ly = yy + 19
        for ln in linhas:
            dc.text(((x + 9) * s, ly * s), ln, font=f.f, fill=(*CREME, 255))
            ly += 10
        return yy + h + 6

    def cartao_alma(yy):
        eixos = dados["alma"]
        h = 19 + max(1, len(eixos)) * 13 + 5
        fundo_cartao(yy, h)
        dc.text(((x + 9) * s, (yy + 7) * s), "ALMA", font=f.f, fill=(*OURO, 216))
        lb = 56
        x1, x2 = x + 9 + lb, x + larg - 9 - lb
        cx = (x1 + x2) // 2
        ly = yy + 19
        for (i, v) in eixos:
            dirr = v > 50
            esq, dta = dados["eixos"][i]
            cAtivo = OURO if dirr else AZUL
            dc.text(((x + 9) * s, ly * s), esq, font=f.f, fill=(*(VIOLETA if dirr else AZUL), 255))
            dc.text(((x + larg - 9 - f.w(dta)) * s, ly * s), dta, font=f.f, fill=(*(OURO if dirr else VIOLETA), 255))
            byy = ly + 9
            dc.rectangle([x1 * s, byy * s, x2 * s, (byy + 1) * s], fill=(*AZUL, 96))
            pxv = x1 + round((x2 - x1) * v / 100)
            dc.rectangle([min(cx, pxv) * s, (byy - 1) * s, max(cx, pxv) * s, (byy + 2) * s], fill=(*cAtivo, 216))
            dc.rectangle([cx * s, (byy - 2) * s, (cx + 1) * s, (byy + 3) * s], fill=(255, 255, 255, 66))
            dc.rectangle([(pxv - 1) * s, (byy - 2) * s, (pxv + 2) * s, (byy + 3) * s], fill=(255, 255, 255, 235))
            ly += 13
        return yy + h + 6

    # medir
    def altura():
        h = 0
        for v in (dados["oficio"], dados["habilidades"]):
            h += 19 + max(1, len(split(v, larg - 16, f))) * 10 + 5 + 6
        h += 19 + max(1, len(dados["alma"])) * 13 + 5 + 6
        h += 19 + max(1, len(split(dados["historia"], larg - 16, f))) * 10 + 5
        return h

    sc_max = max(0, altura() - (limite - topo))
    off = min(rolagem, sc_max)

    yy = topo - off
    yy = cartao(yy, "OCUPAÇÃO", dados["oficio"])
    yy = cartao(yy, "HABILIDADES", dados["habilidades"])
    yy = cartao_alma(yy)
    cartao(yy, "HISTÓRIA", dados["historia"])

    caixa = (int((x - 2) * s), int((topo - 2) * s), int((x + larg + 2) * s), int(limite * s))
    rec = camada.crop(caixa)
    img.paste(rec, (caixa[0], caixa[1]), rec)
    d = ImageDraw.Draw(img, "RGBA")

    if sc_max > 0:
        fade = Image.new("RGBA", (int((larg + 4) * s), int(14 * s)), PAINEL + (255,))
        alpha = Image.linear_gradient("L").resize(fade.size)
        fade.putalpha(alpha)
        img.paste(fade, (int((x - 2) * s), int((limite - 14) * s)), fade)

        alt = limite - topo
        frac = alt / (alt + sc_max)
        hb = max(14, round(alt * frac))
        yb = topo + round((alt - hb) * (off / sc_max if sc_max else 0))
        bxr = x + larg + 3
        d.rectangle([bxr * s, topo * s, (bxr + 2) * s, limite * s], fill=(255, 255, 255, 31))
        d.rectangle([(bxr - 1) * s, yb * s, (bxr + 3) * s, (yb + hb) * s], fill=(*AZUL, 140))
        d.rectangle([bxr * s, (yb + 1) * s, (bxr + 2) * s, (yb + hb - 1) * s], fill=(191, 230, 255, 216))

    # botão
    bw, bh = larg, 20
    bxx, byy = x, py + ph - 40
    d.rectangle([bxx * s, byy * s, (bxx + bw) * s - 1, (byy + bh) * s - 1], fill=(44, 30, 84, 224))
    d.rectangle([(bxx + 1) * s, (byy + 1) * s, (bxx + bw - 1) * s, (byy + 2) * s], fill=(255, 255, 255, 42))
    d.rectangle([(bxx + 1) * s, (byy + bh - 5) * s, (bxx + bw - 1) * s, (byy + bh - 1) * s], fill=(0, 0, 0, 40))
    d.rectangle([bxx * s, byy * s, (bxx + bw) * s - 1, (byy + bh) * s - 1], outline=(143, 216, 255, 190))
    d.rectangle([(bxx + bw / 2 - 18) * s, (byy + bh - 3) * s, (bxx + bw / 2 + 18) * s, (byy + bh - 2) * s],
                fill=(*OURO, 150))
    t = "Confirmar Personagem"
    f.draw(d, t, bxx + bw / 2 - f.w(t) / 2, byy + (bh - 8) / 2, (250, 240, 214))

    # progresso + teclas
    n = 8
    ini = px + pw // 2 - n * 9 // 2
    y2 = py + ph - 15
    for i in range(n):
        xx = ini + i * 9
        if i == n - 1:
            losango(d, s, xx + 2.5, y2 + 2.5, 3.5, OURO, 0.95)
            losango(d, s, xx + 2.5, y2 + 2.5, 1.5, (255, 246, 222), 0.95)
        else:
            d.rectangle([xx * s, y2 * s, (xx + 5) * s, (y2 + 5) * s], fill=(*AZUL, 200))

    rod = py + ph + 9

    def tecla(tx, nome, rotulo):
        w = f.w(nome) + 8
        d.rectangle([tx * s, (rod - 2) * s, (tx + w) * s, (rod + 9) * s], fill=(255, 255, 255, 36))
        d.rectangle([tx * s, (rod - 2) * s, (tx + w) * s, (rod - 1) * s], fill=(255, 255, 255, 58))
        d.rectangle([tx * s, (rod + 9) * s, (tx + w) * s, (rod + 10) * s], fill=(255, 255, 255, 26))
        f.draw(d, nome, tx + 4, rod, (216, 207, 240))
        f.draw(d, rotulo, tx + w + 4, rod, (138, 124, 176))
        return tx + w + 4 + f.w(rotulo)

    tecla(px + 12, "ESC", "sair")
    ent = "confirmar"
    wEnt = f.w(ent) + 6 + f.w("ENTER") + 8
    tecla(px + pw - 12 - wEnt, "ENTER", ent)

    img.save(out)
    return dict(painel=(px, py, pw, ph), colX=colX, larg=larg, rolagem_max=sc_max)


eixos = [("Prudente", "Corajoso"), ("Pragmático", "Empático"), ("Espontâneo", "Disciplinado"),
         ("Tradicional", "Curioso"), ("Reservado", "Sociável"), ("Contente", "Ambicioso")]

dados = dict(
    nome="Eduardo", idade="27 anos",
    origem="Uberlândia, Minas Gerais — o portão do cerrado",
    oficio="Cartógrafo de mundos mortos e contador de histórias",
    habilidades="Lê ruínas como quem lê livros, costura mapas improváveis e some nas sombras quando o sol cai.",
    alma=[(3, 78), (1, 22), (4, 18)],
    eixos=eixos,
    historia="Cresceu ouvindo que o mundo acabara três vezes antes de ele nascer. Passou a juventude desenhando rotas para lugares que ninguém queria visitar, e foi assim que aprendeu: toda ruína tem memória, e toda memória pede alguém que a conte.")

for (W, H, nome) in [(634, 321, "634"), (475, 241, "475")]:
    print(nome, desenha(W, H, dados, f"/home/user/rp-tag/arte/resumo_{nome}.png"))
