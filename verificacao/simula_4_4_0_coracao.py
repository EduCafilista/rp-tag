"""Simulacao do CORACAO 3D canonico (4.4.0) — verificacao visual do giro.

Le a MASCARA direto do CanonCoracaoRenderer.java (fonte unica), refaz a
distance transform + as faces expostas + o sombreamento por normal e projeta
o solido girando. Nao e screenshot do jogo: e a mesma geometria e as mesmas
cores, para provar que o coracao le como coracao em QUALQUER angulo.

Saida em verificacao/:
  4_4_0_coracao_giro.png      -> 8 angulos (0..315)
  4_4_0_coracao_no_chao.png   -> como fica apoiado no chao
  4_4_0_coracao_giro.gif      -> uma volta completa
"""
import math
import os
import re

from PIL import Image, ImageDraw, ImageFont

AQUI = os.path.dirname(os.path.abspath(__file__))
FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# ---------------------------------------------------------------- fonte: o Java
def ler_mascara():
    src = open(os.path.join(AQUI, "..", "src", "main", "java", "dev", "rptag", "client",
                            "CanonCoracaoRenderer.java"), encoding="utf-8").read()
    bloco = re.search(r"private static final String\[\] MASCARA = \{(.*?)\};", src, re.S).group(1)
    linhas = re.findall(r'"([.#]+)"', bloco)
    return linhas


MASCARA = ler_mascara()
H, W = len(MASCARA), len(MASCARA[0])
NY = 13
FATOR_PROF = 1.35
PX = 1.0 / 16.0

TOPO, MEIO, PONTA = (0xF7, 0x5C, 0x7C), (0xDC, 0x18, 0x3E), (0x84, 0x08, 0x26)


def dentro(x, y):
    return 0 <= y < H and 0 <= x < W and MASCARA[y][x] == '#'


def construir_solido():
    INF = 999
    d = [[INF if dentro(x, y) else 0 for x in range(W)] for y in range(H)]
    for _ in range(16):
        for y in range(H):
            for x in range(W):
                if d[y][x] == 0:
                    continue
                m = d[y][x]
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < W and 0 <= ny < H:
                            m = min(m, d[ny][nx] + 1)
                d[y][x] = m
    sol = [[[False] * NY for _ in range(H)] for _ in range(W)]
    for y in range(H):
        for x in range(W):
            if MASCARA[y][x] != '#':
                continue
            meio = min((NY - 1) / 2.0, max(0.5, (d[y][x] - 0.5) * FATOR_PROF))
            for z in range(NY):
                sol[x][y][z] = abs(z - (NY - 1) / 2.0) <= meio
    return sol


SOL = construir_solido()


def tem(x, y, z):
    return 0 <= x < W and 0 <= y < H and 0 <= z < NY and SOL[x][y][z]


def mix(a, b, f):
    f = max(0.0, min(1.0, f))
    return tuple(a[i] + (b[i] - a[i]) * f for i in range(3))


def clareia(c, f):
    return tuple(c[i] + (255 - c[i]) * f for i in range(3))


def cor_voxel(x, y, z):
    u = y / (H - 1)
    c = mix(TOPO, MEIO, u * 2) if u < 0.5 else mix(MEIO, PONTA, (u - 0.5) * 2)
    dx = (x - (W - 1) / 2.0) / (W / 2.0)
    dz = (z - (NY - 1) / 2.0) / (NY / 2.0)
    miolo = 1.0 + 0.12 * (1.0 - (dx * dx + dz * dz)) - 0.06 * u
    c = tuple(min(255, v * miolo) for v in c)
    ex = (x - 4.0) / 2.4
    ey = (y - 1.4) / 2.2
    spec = max(0.0, 1.0 - (ex * ex + ey * ey)) * 0.26
    return tuple(min(255, v + (255 - v) * spec) for v in c)


def faces():
    """(4 vertices, normal, cor) de cada face exposta — igual ao Java."""
    saida = []
    for y in range(H):
        for x in range(W):
            for z in range(NY):
                if not SOL[x][y][z]:
                    continue
                x0 = (x - (W - 1) / 2.0) * PX
                x1 = x0 + PX
                y0 = (H - 1 - y) * PX
                y1 = y0 + PX
                z0 = (z - (NY - 1) / 2.0) * PX
                z1 = z0 + PX
                base = cor_voxel(x, y, z)
                if not tem(x, y - 1, z):
                    saida.append(([(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], (0, 1, 0), 1.00, base))
                if not tem(x, y + 1, z):
                    saida.append(([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0), 0.52, base))
                if not tem(x, y, z - 1):
                    saida.append(([(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)], (0, 0, -1), 0.70, base))
                if not tem(x, y, z + 1):
                    saida.append(([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1), 0.92, base))
                if not tem(x - 1, y, z):
                    saida.append(([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0), 0.64, base))
                if not tem(x + 1, y, z):
                    saida.append(([(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], (1, 0, 0), 0.80, base))
    return saida


FACES = faces()


def rot_y(p, ang):
    c, s = math.cos(ang), math.sin(ang)
    return (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)


def rot_x(p, ang):
    c, s = math.cos(ang), math.sin(ang)
    return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def rot_z(p, ang):
    c, s = math.cos(ang), math.sin(ang)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2])


def desenhar(d, ang_deg, cx, cy, escala, incl=(6.0, 5.0)):
    """projeta com o MESMO empilhamento do PoseStack: Y(ang) -> Z(incl) -> X(6)."""
    a = math.radians(ang_deg)
    iz, ix = math.radians(incl[1]), math.radians(incl[0])
    quads = []
    for verts, nrm, sh, base in FACES:
        vs = []
        for v in verts:
            p = rot_y(v, a)
            p = rot_z(p, iz)
            p = rot_x(p, ix)
            vs.append(p)
        nn = rot_x(rot_z(rot_y(nrm, a), iz), ix)
        if nn[2] <= 0.02:            # face virada pra tras
            continue
        prof = sum(v[2] for v in vs) / 4.0
        c = tuple(min(255, v * sh * 1.05) for v in base)
        quads.append((prof, vs, c))
    quads.sort(key=lambda q: q[0])
    for _, vs, c in quads:
        pts = [(cx + v[0] * escala, cy - v[1] * escala) for v in vs]
        d.polygon(pts, fill=tuple(int(v) for v in c))


def quadro(ang, Wimg=900, Himg=900, fundo=(10, 8, 12), chao=False):
    img = Image.new("RGB", (Wimg, Himg), fundo)
    d = ImageDraw.Draw(img)
    esc = Wimg * 0.40
    if chao:
        # chao de pedra em perspectiva simples, so pra dar escala de "bloco no chao"
        chaoY = Himg * 0.80
        for i in range(-8, 9):
            for j in range(0, 5):
                x0 = Wimg / 2 + (i + (j % 2) * 0.5) * esc * 0.16
                y0 = chaoY - esc * 0.10 + j * esc * 0.055
                tom = 44 + 8 * ((i + j) % 2)
                d.polygon([(x0, y0), (x0 + esc * 0.16, y0 - esc * 0.02),
                           (x0 + esc * 0.24, y0 + esc * 0.03), (x0 + esc * 0.08, y0 + esc * 0.055)],
                          fill=(tom, tom - 3, tom + 4))
        # o coracao APOIADO: a ponta encosta no chao
        cy = chaoY - esc * 0.045        # a ponta encosta no chao (so o flutuar)
        d.ellipse([Wimg / 2 - esc * 0.30, chaoY - esc * 0.03, Wimg / 2 + esc * 0.30, chaoY + esc * 0.035],
                  fill=(5, 4, 7))
    else:
        cy = Himg * 0.52
        chaoY = None
    desenhar(d, ang, Wimg / 2.0, cy, esc)
    return img


def tira_angulos():
    img = Image.new("RGB", (1600, 820), (12, 10, 16))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONTE, 30)
    for i, ang in enumerate([0, 45, 90, 135, 180, 225, 270, 315]):
        sub = quadro(ang, 400, 400, (12, 10, 16))
        x, y = (i % 4) * 400, (i // 4) * 400
        img.paste(sub, (x, y))
        d.text((x + 14, y + 10), f"{ang}°", font=f, fill=(190, 160, 180))
    img = img.crop((0, 0, 1600, 800))
    img.save(os.path.join(AQUI, "4_4_0_coracao_giro.png"))
    print("ok 4_4_0_coracao_giro.png")


def cena_chao():
    img = quadro(28, 1200, 800, (16, 14, 20), chao=True)
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONTE, 26)
    d.text((24, 22), "coracao_canon colocado no chao (girando)", font=f, fill=(200, 170, 190))
    img.save(os.path.join(AQUI, "4_4_0_coracao_no_chao.png"))
    print("ok 4_4_0_coracao_no_chao.png")


def gif(nome="4_4_0_coracao_giro.gif", n=48, tam=420):
    quadros = []
    for i in range(n):
        ang = 360.0 * i / n
        quadros.append(quadro(ang, tam, tam).convert("P", palette=Image.ADAPTIVE, colors=64))
    quadros[0].save(os.path.join(AQUI, nome), save_all=True, append_images=quadros[1:],
                    duration=70, loop=0, optimize=True)
    print("ok", nome)


if __name__ == "__main__":
    print(f"mascara {W}x{H}, profundidade {NY}, faces expostas = {len(FACES)}")
    tira_angulos()
    cena_chao()
    gif()
