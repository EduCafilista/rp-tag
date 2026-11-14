#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.48.0) AS TEXTURAS DO RASTRO DO VAZIO — mancha macia e lasca de rachadura.

Por que existem: o rastro era desenhado com `RenderType.debugQuads()`, que DESENHA SEM
TRANSPARÊNCIA (o alfa do vértice é ignorado). Na prática as "manchas de escuridão" saíam
PRETAS E OPACAS por cima do chão — o dono viu exatamente isso: "o rastro do vazio está ficando
bugado e deixando as coisas atrás invisível". Com textura e `RenderType.text(...)` o alfa vale
de verdade e o rastro volta a ser sombra.

- rastro_mancha.png   64x64  borrão radial (branco + alfa em queda) — tingido de quase preto;
- rastro_lasca.png    64x64  rachadura em zigue-zague com halo — tingida de sangue.

Uso: python3 arte/gerar_rastro.py
"""
import math
import os
import struct
import zlib

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DESTINO = os.path.join(RAIZ, 'src', 'main', 'resources', 'assets', 'rptag', 'textures', 'environment')

LADO = 64


def png(caminho, w, h, rgba):
    linhas = bytearray()
    for y in range(h):
        linhas.append(0)
        linhas += rgba[y * w * 4:(y + 1) * w * 4]

    def bloco(tipo, dados):
        b = tipo + dados
        return struct.pack('>I', len(dados)) + b + struct.pack('>I', zlib.crc32(b) & 0xFFFFFFFF)

    cab = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    open(caminho, 'wb').write(b'\x89PNG\r\n\x1a\n' + bloco(b'IHDR', cab)
                              + bloco(b'IDAT', zlib.compress(bytes(linhas), 9))
                              + bloco(b'IEND', b''))


def mancha():
    """borrão circular com queda suave: centro opaco, borda com alfa ZERO (nenhum canto)."""
    px = bytearray(LADO * LADO * 4)
    meio = (LADO - 1) / 2.0
    for y in range(LADO):
        for x in range(LADO):
            r = math.hypot((x - meio) / meio, (y - meio) / meio)
            a = 0 if r >= 1.0 else int(round(255 * (1.0 - r) ** 2.4))
            i = (y * LADO + x) * 4
            px[i] = px[i + 1] = px[i + 2] = 255
            px[i + 3] = a
    png(os.path.join(DESTINO, 'rastro_mancha.png'), LADO, LADO, px)


def lasca():
    """uma rachadura em zigue-zague atravessando o quadro, com halo quente em volta."""
    px = bytearray(LADO * LADO * 4)
    # o caminho da rachadura (de uma borda à outra, tremendo no meio)
    pontos = []
    passos = 26
    for i in range(passos + 1):
        t = i / passos
        x = t * (LADO - 1)
        y = LADO * 0.5 + math.sin(t * 9.0) * 9.0 + math.sin(t * 23.0) * 3.5
        pontos.append((x, y))

    def dist_seg(px_, py_):
        melhor = 999.0
        for i in range(len(pontos) - 1):
            ax, ay = pontos[i]
            bx, by = pontos[i + 1]
            vx, vy = bx - ax, by - ay
            comp = vx * vx + vy * vy
            t = 0.0 if comp <= 1e-9 else max(0.0, min(1.0, ((px_ - ax) * vx + (py_ - ay) * vy) / comp))
            d = math.hypot(px_ - (ax + vx * t), py_ - (ay + vy * t))
            melhor = min(melhor, d)
        return melhor

    for y in range(LADO):
        for x in range(LADO):
            d = dist_seg(x, y)
            # núcleo branco-quente + halo que cai (alfa chega a ZERO longe do traço)
            if d < 1.1:
                a, branco = 255, 1.0
            elif d < 5.0:
                k = (5.0 - d) / 3.9
                a, branco = int(220 * k * k), 0.55 * k
            else:
                a, branco = 0, 0.0
            i = (y * LADO + x) * 4
            px[i] = 255
            px[i + 1] = int(255 * (1.0 - 0.62 * branco))
            px[i + 2] = int(255 * (1.0 - 0.85 * branco))
            px[i + 3] = a
    png(os.path.join(DESTINO, 'rastro_lasca.png'), LADO, LADO, px)


if __name__ == '__main__':
    os.makedirs(DESTINO, exist_ok=True)
    mancha()
    lasca()
    for n in ('rastro_mancha.png', 'rastro_lasca.png'):
        p = os.path.join(DESTINO, n)
        print('%s (%d B)' % (n, os.path.getsize(p)))
