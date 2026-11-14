#!/usr/bin/env python3
"""(5.46.0) A LUA DE SANGUE — a textura do terremoto.

Pedido do dono: <i>"a 'Lua' do terremoto, melhore isso ... coloque essa lua"</i>, mandando de
novo a referência animada (a página "Lua de Sangue", um shader de esfera rachada de lava).

A 5.45.0 desenhava a lua com UM MONTE DE DISCOS e raios saindo do centro — no jogo virou uma
bola vermelha chapada com uma estrela de rachaduras no meio (a foto dele). Aqui a lua passa a
ser uma TEXTURA de verdade, gerada por código com o mesmo recheio da referência:

  * superfície de pedra de sangue: ruído fractal (fBm) em três oitavas, indo de vinho quase
    preto a vermelho vivo — é o que dá o relevo;
  * RACHADURAS DE LAVA: veios que andam em zigue-zague pela superfície e BRILHAM (laranja
    quente), com um halo em volta de cada veio;
  * aro aceso: a borda do disco queima mais forte (o "contorno" da referência);
  * vinheta interna: escurece perto do aro, para a lua parecer uma ESFERA e não um círculo.

Tudo com semente fixa: a lua é sempre a mesma a cada sessão (nada de piscar aleatório).

Uso:
  python3 arte/gerar_lua_de_sangue.py
"""
import math
import os
import random

from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DESTINO = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "textures", "environment")

LADO = 256
SEMENTE = 20251031


_GRADES = {}


def _grade(semente):
    """a grade de valores aleatórios, criada UMA vez por semente (antes era por pixel)."""
    g = _GRADES.get(semente)
    if g is None:
        rnd = random.Random(semente)
        g = [[rnd.random() for _ in range(65)] for _ in range(65)]
        _GRADES[semente] = g
    return g


def ruido(x, y, semente):
    """ruído de valor com interpolação suave (a base do fBm)."""
    grade = _grade(semente)
    xi, yi = int(math.floor(x)) % 64, int(math.floor(y)) % 64
    fx, fy = x - math.floor(x), y - math.floor(y)
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    a = grade[xi][yi]
    b = grade[xi + 1][yi]
    c = grade[xi][yi + 1]
    d = grade[xi + 1][yi + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(x, y, semente, oitavas=4):
    total, amp, freq, norm = 0.0, 0.5, 1.0, 0.0
    for i in range(oitavas):
        total += amp * ruido(x * freq, y * freq, semente + i * 977)
        norm += amp
        amp *= 0.5
        freq *= 2.03
    return total / norm


def rachaduras(semente, quantas=11, passos=22):
    """as rachaduras de lava: caminhada aleatória pela superfície (coordenadas -1..1)."""
    rnd = random.Random(semente)
    veios = []
    for _ in range(quantas):
        x = rnd.uniform(-0.85, 0.85)
        y = rnd.uniform(-0.85, 0.85)
        ang = rnd.uniform(0, math.tau)
        caminho = []
        for _ in range(passos):
            caminho.append((x, y))
            ang += rnd.uniform(-0.75, 0.75)
            x += math.cos(ang) * 0.055
            y += math.sin(ang) * 0.055
            if x * x + y * y > 0.92 * 0.92:
                break
        if len(caminho) > 2:
            veios.append(caminho)
    return veios


def distancia_ao_veio(px, py, veios):
    """distância até o SEGMENTO mais próximo de qualquer rachadura.

    (A primeira versão media a distância até cada PONTO do caminho — e no jogo virava um
    colar de bolinhas laranja, não uma rachadura. Segmento resolve.)
    """
    melhor = 9.0
    for caminho in veios:
        for i in range(len(caminho) - 1):
            ax, ay = caminho[i]
            bx, by = caminho[i + 1]
            vx, vy = bx - ax, by - ay
            comp = vx * vx + vy * vy
            t = 0.0 if comp <= 1e-9 else max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / comp))
            d = math.hypot(px - (ax + vx * t), py - (ay + vy * t))
            if d < melhor:
                melhor = d
    return melhor


def gerar(nome, brilho, quente):
    """desenha a lua (brilho 1,0) ou o sol (brilho 1,35) em RGBA."""
    img = Image.new("RGBA", (LADO, LADO), (0, 0, 0, 0))
    px = img.load()
    veios = rachaduras(SEMENTE)
    raio = LADO * 0.5 - 2
    cx = cy = LADO * 0.5
    for y in range(LADO):
        for x in range(LADO):
            dx = (x - cx) / raio
            dy = (y - cy) / raio
            d2 = dx * dx + dy * dy
            if d2 > 1.0:
                continue
            # a esfera: a "profundidade" serve para escurecer as bordas (vinheta)
            prof = math.sqrt(max(0.0, 1.0 - d2))
            # coordenadas na superfície da esfera (para o ruído não esticar)
            sx, sy = dx * (0.6 + 0.4 * prof), dy * (0.6 + 0.4 * prof)

            # 1) pedra de sangue
            n = fbm(sx * 3.2 + 5.0, sy * 3.2 + 9.0, SEMENTE, 3)
            n2 = fbm(sx * 9.0, sy * 9.0, SEMENTE + 31, 2)
            n3 = fbm(sx * 24.0, sy * 24.0, SEMENTE + 53, 2)   # granulado fino da pedra
            mistura = max(0.0, min(1.0, n * 0.62 + n2 * 0.30 + n3 * 0.18))
            r = 30 + 120 * mistura
            g = 4 + 22 * mistura
            b = 6 + 16 * mistura
            # crateras/manchas escuras (as "nuvens" da lua)
            mancha = fbm(sx * 2.1 + 21.0, sy * 2.1 + 3.0, SEMENTE + 77, 3)
            if mancha < 0.42:
                k = (0.42 - mancha) / 0.42
                r *= 1.0 - 0.55 * k
                g *= 1.0 - 0.75 * k
                b *= 1.0 - 0.75 * k

            # 2) rachaduras de lava + halo
            dv = distancia_ao_veio(dx, dy, veios)
            if dv < 0.030:
                k = (1.0 - dv / 0.030) ** 2
                r = r * (1 - k) + 255 * k
                g = g * (1 - k) + (150 * quente) * k
                b = b * (1 - k) + (40 * quente) * k
            elif dv < 0.16:
                k = (0.16 - dv) / 0.16 * 0.50
                r = r * (1 - k) + 210 * k
                g = g * (1 - k) + 60 * k
                b = b * (1 - k) + 20 * k

            # 3) vinheta de esfera + aro aceso
            borda = 1.0 - prof
            r *= (1.0 - 0.45 * borda * borda)
            g *= (1.0 - 0.55 * borda * borda)
            b *= (1.0 - 0.55 * borda * borda)
            aro = max(0.0, (prof - 0.055) / 0.055) if prof < 0.055 else 0.0
            if aro > 0:
                r = r * (1 - aro) + 255 * aro
                g = g * (1 - aro) + (110 * quente) * aro
                b = b * (1 - aro) + (35 * quente) * aro

            r = min(255, r * brilho)
            g = min(255, g * brilho)
            b = min(255, b * brilho)
            # alfa: cheio dentro, com uma borda suave de 1,5 px (nada de serrilhado)
            a = 255 if d2 < 0.985 else int(255 * (1.0 - (d2 - 0.985) / 0.015))
            px[x, y] = (int(r), int(g), int(b), max(0, min(255, a)))
    os.makedirs(DESTINO, exist_ok=True)
    caminho = os.path.join(DESTINO, nome)
    img.save(caminho)
    print("lua: %s (%dx%d)" % (caminho, LADO, LADO))
    return img


def main():
    gerar("lua_de_sangue.png", 1.0, 1.0)
    gerar("sol_de_sangue.png", 1.18, 1.5)


if __name__ == "__main__":
    main()
