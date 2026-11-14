#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.47.0) O HALO DA LUA/SOL — degradê radial de VERDADE, numa textura.

Por que existe: na 5.46.0 o halo eram 3 quads chapados e o dono viu um QUADRADO em volta da
lua na foto dele. A 5.47.0 tentou discos empilhados, mas empilhar disco de alfa fixo também dá
aneis (degraus de transparência). A solução limpa é esta: uma textura 128x128 com alfa caindo
de forma contínua do centro até a borda. Um quad só, e como o alfa chega a ZERO na borda, não
existe canto nenhum na tela — o halo é um círculo suave.

Uso: python3 arte/gerar_halo.py
Saída: assets/rptag/textures/environment/halo_de_sangue.png
"""
import math
import os
import struct
import zlib

LADO = 128
SAIDA = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                     'src', 'main', 'resources', 'assets', 'rptag', 'textures',
                     'environment', 'halo_de_sangue.png')


def png(caminho, w, h, rgba):
    """PNG RGBA direto, sem dependência nenhuma."""
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


def cor(r):
    """do centro (laranja quase branco) para fora (sangue escuro) — só a cor, o alfa é o halo."""
    quente = (1.0, 0.52, 0.24)
    frio = (0.62, 0.06, 0.03)
    k = min(1.0, r ** 0.55)
    return tuple(round(255 * (quente[i] * (1 - k) + frio[i] * k)) for i in range(3))


def gerar():
    px = bytearray(LADO * LADO * 4)
    meio = (LADO - 1) / 2.0
    for y in range(LADO):
        for x in range(LADO):
            # distância normalizada (0 no centro, 1 na borda do círculo inscrito)
            dx = (x - meio) / meio
            dy = (y - meio) / meio
            r = math.hypot(dx, dy)
            if r >= 1.0:
                a = 0
            else:
                # cauda longa e suave (potência 3.2): perto do centro brilha, na borda ZERO
                a = int(round(255 * (1.0 - r) ** 3.2))
            c = cor(min(1.0, r))
            i = (y * LADO + x) * 4
            px[i] = c[0]
            px[i + 1] = c[1]
            px[i + 2] = c[2]
            px[i + 3] = a
    png(SAIDA, LADO, LADO, px)
    print('halo: %s (%d B)' % (os.path.normpath(SAIDA), os.path.getsize(SAIDA)))


if __name__ == '__main__':
    gerar()
