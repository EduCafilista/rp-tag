"""IMPORTA a mascara do GLB (Sketchfab / arquivo do dono) para dentro do mod.

Antes a mascara era GERADA por codigo (arte/previa_mascara3d.py). Agora a malha
vem do modelo de verdade que o dono mandou (`mask.glb`, 5864 vertices /
8014 triangulos, textura de pedra 2048x2048). Este script:

  1. le o .glb (header + JSON + BIN, aplica a cadeia de matrizes dos nodes);
  2. leva a malha para o ESPACO DA CABECA do Minecraft (cabeca = cubo de 8 em
     x/y/z, rosto em z=-4). Escala pela ALTURA, alarga um pouco em X (o modelo
     e estreito) e achata em Z (senao o focinho sai 3 blocos pra frente);
  3. recorta a textura baseColor pra 512x512 -> textures/entity/mascara_glb.png;
  4. grava a malha binaria -> assets/rptag/geo/mascara_glb.bin (o Java le isso);
  5. desenha previas em previa/mascara_glb.png (com a cabeca do jogador junto).

uso:  python3 arte/importar_glb.py [caminho do .glb]
"""
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
GLB = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/tmp/mask.glb')
BIN = RAIZ / 'src/main/resources/assets/rptag/geo/mascara_glb.bin'
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/entity/mascara_glb.png'
PREVIA = RAIZ / 'previa/mascara_glb.png'

# ---- ajustes da malha no espaco da cabeca (em unidades do modelo do player)
ALTURA = 7.3      # (5.0.1) altura: 8,6 fazia a mascara passar 1 bloco pra fora da cabeca
ALARGA = 1.20     # o modelo e estreito; alarga so em X
ACHA = 0.70       # achata em Z (o modelo e fundo: senao salta 3 blocos)
Z_TRAS = -3.75    # (5.0.1) afunda 0,25 DENTRO da cabeca (rosto = -4): antes ficava
                  # 0,12 pra fora e o jogador via a mascara flutuando longe do rosto
TEX_LADO = 512

TIPO = {5120: ('b', 1), 5121: ('B', 1), 5122: ('h', 2), 5123: ('H', 2), 5125: ('I', 4), 5126: ('f', 4)}
NCOMP = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def ler_glb(caminho):
    d = Path(caminho).read_bytes()
    assert d[:4] == b'glTF', 'nao e um GLB'
    o, chunks = 12, []
    while o < len(d):
        clen, ctype = struct.unpack('<II', d[o:o + 8]); o += 8
        chunks.append((ctype, d[o:o + clen])); o += clen
    j = json.loads(chunks[0][1].decode('utf-8'))
    binario = chunks[1][1]

    def acc(i):
        a = j['accessors'][i]
        bv = j['bufferViews'][a['bufferView']]
        f, _ = TIPO[a['componentType']]
        n = NCOMP[a['type']]
        off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        arr = np.frombuffer(binario, dtype='<' + f, count=a['count'] * n, offset=off)
        arr = arr.astype(np.float64 if f == 'f' else np.int64)
        return arr.reshape(a['count'], n) if n > 1 else arr

    def imagem(i):
        im = j['images'][i]
        bv = j['bufferViews'][im['bufferView']]
        o0 = bv.get('byteOffset', 0)
        import io
        return Image.open(io.BytesIO(binario[o0:o0 + bv['byteLength']]))

    prim = j['meshes'][0]['primitives'][0]
    pos = acc(prim['attributes']['POSITION'])
    nor = acc(prim['attributes']['NORMAL'])
    uv = acc(prim['attributes']['TEXCOORD_0'])
    idx = acc(prim['indices']).reshape(-1, 3)

    # cadeia de matrizes dos nodes ate o que tem a malha
    def matriz(n):
        m = j['nodes'][n]
        return np.array(m['matrix'], float).reshape(4, 4).T if 'matrix' in m else np.eye(4)

    achadas = []

    def anda(n, M):
        m = matriz(n) @ M
        if 'mesh' in j['nodes'][n]:
            achadas.append(m)
        for c in j['nodes'][n].get('children', []):
            anda(c, m)

    anda(j['scene'] if isinstance(j['scene'], int) else 0, np.eye(4))
    M = achadas[0] if achadas else np.eye(4)

    pos = (np.c_[pos, np.ones(len(pos))] @ M.T)[:, :3]
    # normais: so a parte 3x3 (as transformacoes aqui sao rotacao+escala uniforme,
    # mas normaliza no fim de qualquer jeito)
    R3 = M[:3, :3]
    nor = nor @ np.linalg.inv(R3).T
    nor /= (np.linalg.norm(nor, axis=1, keepdims=True) + 1e-12)

    mat = j['materials'][prim.get('material', 0)]
    tex_i = mat['pbrMetallicRoughness']['baseColorTexture']['index']
    return pos, nor, uv, idx, imagem(j['textures'][tex_i]['source'])


def para_espaco_cabeca(pos):
    """Centraliza em x/y, escala pela altura e encosta o fundo em Z_TRAS."""
    mn, mx = pos.min(0), pos.max(0)
    centro = (mn + mx) / 2
    p = pos - centro
    s = ALTURA / (mx[1] - mn[1])
    p[:, 0] *= s * ALARGA
    p[:, 1] *= s
    p[:, 2] *= s * ACHA
    z_fundo = p[:, 2].max()
    p[:, 2] += Z_TRAS - z_fundo
    return p


def main():
    pos, nor, uv, idx, tex = ler_glb(GLB)
    print(f'malha: {len(pos)} vertices, {len(idx)} triangulos')
    p = para_espaco_cabeca(pos)
    print('cabeca: x', p[:, 0].min().round(2), p[:, 0].max().round(2),
          '| y', p[:, 1].min().round(2), p[:, 1].max().round(2),
          '| z', p[:, 2].min().round(2), p[:, 2].max().round(2))
    # a face tem que apontar pra -Z (frente da cabeca): confere a normal media
    # dos vertices mais a frente
    frente = p[:, 2] < np.percentile(p[:, 2], 15)
    print('normal media da frente:', nor[frente].mean(0).round(2), '(z deve ser negativo)')

    # ---- textura
    t = tex.convert('RGB')
    if t.size[0] > TEX_LADO:
        t = t.resize((TEX_LADO, TEX_LADO), Image.LANCZOS)
    TEX.parent.mkdir(parents=True, exist_ok=True)
    t.save(TEX, optimize=True)

    # ---- malha binaria (triangulos expandidos: o Java so percorre em triplas)
    tri = idx.reshape(-1)
    vpos, vnor, vuv = p[tri], nor[tri], uv[tri]
    n = len(tri)
    BIN.parent.mkdir(parents=True, exist_ok=True)
    with BIN.open('wb') as f:
        f.write(b'RPTM')                       # magic
        # BIG-endian: é o que o DataInputStream do Java lê (o little-endian
        # fazia a versão chegar como 16777216 e o Java cair na malha procedural)
        f.write(struct.pack('>ii', 1, n))      # versao, numero de vertices
        # PLANAR (todas as X, depois todas as Y, ...): é como o Java lê
        # (um laço por componente). Intercalado por vértice embaralha a malha.
        for eixo in range(3):
            f.write(vpos[:, eixo].astype('>f4').tobytes())
        for eixo in range(3):
            f.write(vnor[:, eixo].astype('>f4').tobytes())
        for eixo in range(2):
            f.write(vuv[:, eixo].astype('>f4').tobytes())

    # ---- previa (malha + cabeca do player)
    def ry(a):
        c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

    def rx(a):
        c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

    tx = np.asarray(t, np.float32) / 255.
    th, tw = tx.shape[:2]

    def desenha(img, zb, P, Nn, U, lado, R, cor_fixa=None, alfa=1.0):
        V = P @ R.T
        esc = lado / 13.0
        sx = lado / 2 + V[:, 0] * esc; sy = lado / 2 - V[:, 1] * esc; sz = V[:, 2]
        x0, y0 = int(max(0, np.floor(sx.min()))), int(max(0, np.floor(sy.min())))
        x1, y1 = int(min(lado - 1, np.ceil(sx.max()))), int(min(lado - 1, np.ceil(sy.max())))
        if x1 <= x0 or y1 <= y0:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        ax, ay, bx, by, cx2, cy2 = sx[0], sy[0], sx[1], sy[1], sx[2], sy[2]
        d = (by - cy2) * (ax - cx2) + (cx2 - bx) * (ay - cy2)
        if abs(d) < 1e-9:
            return
        w0 = ((by - cy2) * (gx - cx2) + (cx2 - bx) * (gy - cy2)) / d
        w1 = ((cy2 - ay) * (gx - cx2) + (ax - cx2) * (gy - cy2)) / d
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            return
        z = w0 * sz[0] + w1 * sz[1] + w2 * sz[2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        mm = m & (z < sub)
        if not mm.any():
            return
        if cor_fixa is not None:
            cor = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 3), np.float32) + np.array(cor_fixa)
        else:
            u = (w0 * U[0, 0] + w1 * U[1, 0] + w2 * U[2, 0]) % 1.0
            v = (w0 * U[0, 1] + w1 * U[1, 1] + w2 * U[2, 1]) % 1.0
            ti = np.clip((u * tw).astype(int), 0, tw - 1)
            tj = np.clip(((1 - v) * th).astype(int), 0, th - 1)
            nn = w0[..., None] * (Nn[0] @ R.T) + w1[..., None] * (Nn[1] @ R.T) + w2[..., None] * (Nn[2] @ R.T)
            ln = np.linalg.norm(nn, axis=2) + 1e-9
            dif = np.clip((nn[:, :, 0] * -0.40 + nn[:, :, 1] * 0.50 + nn[:, :, 2] * -0.77) / ln, 0, 1)
            cor = tx[tj, ti] * (0.45 + 0.75 * dif)[:, :, None]
        img[y0:y1 + 1, x0:x1 + 1][mm] = cor[mm] * alfa + img[y0:y1 + 1, x0:x1 + 1][mm] * (1 - alfa)
        sub[mm] = z[mm]; zb[y0:y1 + 1, x0:x1 + 1] = sub

    def cabeca(lado, R):
        k = 4.0
        v = np.array([(-k, -k, -k), (k, -k, -k), (k, k, -k), (-k, k, -k),
                      (-k, -k, k), (k, -k, k), (k, k, k), (-k, k, k)], float)
        f = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]
        tri = []
        for (a, b, c, d) in f:
            tri += [(a, b, c), (a, c, d)]
        return v, np.array(tri)

    def render(yaw, pitch, lado=340):
        R = rx(pitch) @ ry(yaw)
        img = np.zeros((lado, lado, 3), np.float32) + np.array((18, 17, 22), np.float32) / 255.
        zb = np.full((lado, lado), 1e9, np.float32)
        cv, ct = cabeca(lado, R)
        for (a, b, c) in ct:
            P = cv[[a, b, c]]
            n = np.cross(P[1] - P[0], P[2] - P[0]); n /= (np.linalg.norm(n) + 1e-9)
            if np.dot(n, P.mean(0)) < 0:
                n = -n
            desenha(img, zb, P, np.tile(n, (3, 1)), np.zeros((3, 2)), lado, R, cor_fixa=(0.30, 0.24, 0.19))
        for k in range(0, len(tri), 3):
            q = tri[k:k + 3]
            desenha(img, zb, p[q], nor[q], uv[q], lado, R)
        return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))

    vistas = [(0.0, 0.0, 'DE FRENTE'), (0.75, -0.06, '3/4'), (1.55, -0.05, 'LADO'), (0.0, -0.55, 'DE BAIXO')]
    tira = Image.new('RGB', (340 * 4 + 25, 372), (10, 9, 12))
    d = ImageDraw.Draw(tira)
    for i, (y, pi, nome) in enumerate(vistas):
        tira.paste(render(y, pi), (5 + i * 345, 26))
        d.text((7 + i * 345, 9), nome, fill=(232, 226, 214))
    PREVIA.parent.mkdir(exist_ok=True)
    tira.save(PREVIA)

    print(f'{BIN} {BIN.stat().st_size} B ({n} vertices expandidos)')
    print(f'{TEX} {TEX.stat().st_size} B {t.size}')
    print(f'{PREVIA} {PREVIA.stat().st_size} B')


if __name__ == '__main__':
    main()
