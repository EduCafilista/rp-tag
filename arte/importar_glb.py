"""IMPORTA a mascara do GLB (modelo do dono) para dentro do mod.

O Minecraft nao le .glb, entao este script converte o modelo em duas malhas
binarias que o Java le direto:

  1. geo/mascara_glb.bin    -> MASCARA NO ROSTO, ja no ESPACO DA CABEÇA do jogo
  2. geo/mascara_item.bin   -> MASCARA COMO ITEM (3D na mao/inventario),
                               normalizada num cubo de 1x1x1

## ESPACO DA CABEÇA (o detalhe que estava invertendo a mascara)

O bytecode do vanilla mostra a cabeca assim:
    CubeListBuilder.addBox(-4, -8, -4, 8, 8, 8)  @ PartPose.offset(0,0,0)
ou seja, dentro de `head.translateAndRotate(pose)`:

    x: -4 .. 4      y: -8 .. 0      z: -4 .. 4

**+Y aponta para BAIXO** (queixo em y=0, topo do cranio em y=-8) e o ROSTO
fica em z=-4. Modelo exportado com +Y pra cima sai DE CABEÇA PARA BAIXO no
jogo — foi exatamente esse o bug da 5.0.1. Agora o script espelha Y na
entrada e monta a caixa de destino explicitamente:

    x: +-2.70       y: -7.45 .. -0.45       z: -5.05 .. -3.75
      (largura 5,4)    (altura 7,0)           (fundo 0,25 DENTRO do rosto)

uso: python3 arte/importar_glb.py [caminho do .glb]
"""
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
GLB = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/home/user/mascara.glb')
BIN_ROSTO = RAIZ / 'src/main/resources/assets/rptag/geo/mascara_glb.bin'
BIN_ITEM = RAIZ / 'src/main/resources/assets/rptag/geo/mascara_item.bin'
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/entity/mascara_glb.png'
PREVIA = RAIZ / 'previa/mascara_glb.png'
TEX_LADO = 512

# ---- caixa de destino NO ESPACO DA CABEÇA (lembre: +Y = baixo)
ROSTO_X = 2.70          # meia-largura  -> 5,4 de largura (cabeca tem 8)
ROSTO_Y_TOPO = -7.45    # topo (y negativo e pra CIMA)
ROSTO_Y_BASE = -0.45    # queixo
ROSTO_Z_FRENTE = -5.05  # ponta do nariz (rosto da cabeca = -4)
ROSTO_Z_FUNDO = -3.75   # 0,25 DENTRO da cabeca (nao flutua)

# ---- caixa do ITEM (cubo unitario, +Y pra cima, frente em -Z: padrao de item)
ITEM_ALTURA = 1.00
ITEM_LARGURA = 0.77
ITEM_PROF = 0.17

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
        f = TIPO[a['componentType']][0]
        n = NCOMP[a['type']]
        off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        arr = np.frombuffer(binario, dtype='<' + f, count=a['count'] * n, offset=off)
        arr = arr.astype(np.float64 if f == 'f' else np.int64)
        return arr.reshape(a['count'], n) if n > 1 else arr

    def imagem(i):
        import io
        im = j['images'][i]
        bv = j['bufferViews'][im['bufferView']]
        o0 = bv.get('byteOffset', 0)
        return Image.open(io.BytesIO(binario[o0:o0 + bv['byteLength']]))

    prim = j['meshes'][0]['primitives'][0]
    pos = acc(prim['attributes']['POSITION'])
    nor = acc(prim['attributes']['NORMAL'])
    uv = acc(prim['attributes']['TEXCOORD_0'])
    idx = acc(prim['indices']).reshape(-1, 3)

    achadas = []

    def anda(n, M):
        m = j['nodes'][n]
        M2 = np.array(m['matrix'], float).reshape(4, 4).T @ M if 'matrix' in m else M
        if 'mesh' in j['nodes'][n]:
            achadas.append(M2)
        for c in j['nodes'][n].get('children', []):
            anda(c, M2)

    anda(0, np.eye(4))
    M = achadas[0] if achadas else np.eye(4)
    pos = (np.c_[pos, np.ones(len(pos))] @ M.T)[:, :3]
    R3 = M[:3, :3]
    nor = nor @ np.linalg.inv(R3).T
    nor /= (np.linalg.norm(nor, axis=1, keepdims=True) + 1e-12)

    mat = j['materials'][prim.get('material', 0)]
    tex = imagem(j['textures'][mat['pbrMetallicRoughness']['baseColorTexture']['index']]['source'])
    return pos, nor, uv, idx, tex


def espelha_y(pos, nor):
    """+Y do glTF (pra cima) -> +Y do Minecraft (pra baixo)."""
    p = pos.copy(); n = nor.copy()
    p[:, 1] *= -1.0
    n[:, 1] *= -1.0
    return p, n


def cabe_em(pos, nor, alvo):
    """Mapeia o bbox de `pos` exatamente na caixa `alvo` (min, max por eixo)."""
    mn, mx = pos.min(0), pos.max(0)
    (ax0, ay0, az0), (ax1, ay1, az1) = alvo
    p = pos.copy()
    for i, (a0, a1) in enumerate(((ax0, ax1), (ay0, ay1), (az0, az1))):
        esc = 0.0 if mx[i] - mn[i] < 1e-9 else (a1 - a0) / (mx[i] - mn[i])
        p[:, i] = a0 + (pos[:, i] - mn[i]) * esc
    # normais: reescala por eixo (aprox. boa para escala nao uniforme)
    n = np.zeros_like(pos)
    for i, (a0, a1) in enumerate(((ax0, ax1), (ay0, ay1), (az0, az1))):
        esc = 1.0 if mx[i] - mn[i] < 1e-9 else (a1 - a0) / (mx[i] - mn[i])
        n[:, i] = nor[:, i] / esc
    n /= (np.linalg.norm(n, axis=1, keepdims=True) + 1e-12)
    return p, n


def grava(caminho, pos, nor, uv, idx):
    tri = idx.reshape(-1)
    vp, vn, vu = pos[tri], nor[tri], uv[tri]
    n = len(tri)
    caminho.parent.mkdir(parents=True, exist_ok=True)
    with caminho.open('wb') as f:
        f.write(b'RPTM')
        # BIG-endian (DataInputStream) e PLANAR (um laco por componente no Java)
        f.write(struct.pack('>ii', 1, n))
        for eixo in range(3):
            f.write(vp[:, eixo].astype('>f4').tobytes())
        for eixo in range(3):
            f.write(vn[:, eixo].astype('>f4').tobytes())
        for eixo in range(2):
            f.write(vu[:, eixo].astype('>f4').tobytes())
    return n


# ------------------------------------------------------------------ previa
def pedra_tile(n=96, base=(150, 146, 138)):
    rng = np.random.default_rng(7)
    a = np.zeros((n, n, 3), np.float32) + np.array(base, np.float32)
    for escala, amp in ((n // 16, 14), (n // 8, 8), (n // 4, 5)):
        r = rng.normal(0, 1, (escala, escala))
        up = np.array(Image.fromarray(((r - r.min()) / (r.max() - r.min()) * 255).astype(np.uint8))
                      .resize((n, n), Image.BICUBIC), np.float32) / 255.0 - 0.5
        a += up[:, :, None] * amp
    return np.clip(a, 0, 255)


def rot_y(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def render(pos, nor, uv, idx, tex, yaw, pitch, lado, caixa_cabeca=None, foco=None):
    """Rasteriza a malha. IMPORTANTE: em espaco de cabeca +Y e PRA BAIXO, entao a
    tela usa sy = centro - (y - foco)*esc com o sinal trocado (y menor = mais alto)."""
    T = np.asarray(tex.convert('RGB'), np.float32) / 255.
    th, tw = T.shape[:2]
    R = rot_x(pitch) @ rot_y(yaw)
    img = np.zeros((lado, lado, 3), np.float32) + np.array((17, 16, 20), np.float32) / 255.
    if caixa_cabeca is not None:
        # fundo de pedra atras da cabeca, so pra dar contraste
        img[:] = np.array((26, 22, 18), np.float32) / 255.
    zb = np.full((lado, lado), 1e9, np.float32)
    c = foco if foco is not None else (pos.min(0) + pos.max(0)) / 2
    esc = lado / 22.0
    ox = oy = lado / 2.0
    luz = np.array([-0.42, -0.5, -0.75])   # y negativo = "de cima" (y pra baixo)
    luz /= np.linalg.norm(luz)

    def tri_screen(P, N, U, cor_fixa=None, alfa=1.0):
        V = (P - c) @ R.T
        Nn = N @ R.T
        sx = ox + V[:, 0] * esc
        sy = oy + V[:, 1] * esc          # +Y pra baixo = igual a tela
        sz = V[:, 2]
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
            nn = w0[..., None] * Nn[0] + w1[..., None] * Nn[1] + w2[..., None] * Nn[2]
            ln = np.linalg.norm(nn, axis=2) + 1e-9
            dif = np.clip((nn[:, :, 0] * luz[0] + nn[:, :, 1] * luz[1] + nn[:, :, 2] * luz[2]) / ln, 0, 1)
            cor = T[tj, ti] * (0.40 + 0.75 * dif)[:, :, None]
        img[y0:y1 + 1, x0:x1 + 1][mm] = cor[mm] * alfa + img[y0:y1 + 1, x0:x1 + 1][mm] * (1 - alfa)
        sub[mm] = z[mm]
        zb[y0:y1 + 1, x0:x1 + 1] = sub

    if caixa_cabeca is not None:
        k = 4.0
        # cabeca do player NO ESPACO DO JOGO: y de -8 (topo) a 0 (queixo)
        v = np.array([(-k, -8, -k), (k, -8, -k), (k, 0, -k), (-k, 0, -k),
                      (-k, -8, k), (k, -8, k), (k, 0, k), (-k, 0, k)], float)
        faces = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]
        for (a, b, cc, dd) in faces:
            for tri in ((a, b, cc), (a, cc, dd)):
                P = v[list(tri)]
                nn = np.cross(P[1] - P[0], P[2] - P[0]); nn /= (np.linalg.norm(nn) + 1e-9)
                if np.dot(nn, P.mean(0) - np.array([0, -4, 0])) < 0:
                    nn = -nn
                tri_screen(P, np.tile(nn, (3, 1)), np.zeros((3, 2)), cor_fixa=(0.29, 0.22, 0.17))

    for t3 in idx:
        tri_screen(pos[t3], nor[t3], uv[t3])
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    pos, nor, uv, idx, tex = ler_glb(GLB)
    print(f'malha: {len(pos)} vertices, {len(idx)} triangulos')
    p, n = espelha_y(pos, nor)

    # ---------------- 1) ROSTO: espaco da cabeca, caixa explicita
    alvo_rosto = ((-ROSTO_X, ROSTO_Y_TOPO, ROSTO_Z_FRENTE), (ROSTO_X, ROSTO_Y_BASE, ROSTO_Z_FUNDO))
    pr, nr = cabe_em(p, n, alvo_rosto)
    print('rosto: x', pr[:, 0].min().round(2), pr[:, 0].max().round(2),
          '| y', pr[:, 1].min().round(2), pr[:, 1].max().round(2),
          '| z', pr[:, 2].min().round(2), pr[:, 2].max().round(2))
    frente = pr[:, 2] < np.percentile(pr[:, 2], 10)
    print('normal media da FRENTE (z deve ser negativo):', nr[frente].mean(0).round(2))
    nv = grava(BIN_ROSTO, pr, nr, uv, idx)

    # ---------------- 2) ITEM: cubo unitario, +Y pra CIMA, frente em -Z
    pi, ni = cabe_em(p, n, ((-ITEM_LARGURA / 2, -ITEM_ALTURA / 2, -ITEM_PROF),
                          (ITEM_LARGURA / 2, ITEM_ALTURA / 2, 0.0)))
    grava(BIN_ITEM, pi, ni, uv, idx)

    # ---------------- 3) textura
    t = tex.convert('RGB')
    if t.size[0] > TEX_LADO:
        t = t.resize((TEX_LADO, TEX_LADO), Image.LANCZOS)
    TEX.parent.mkdir(parents=True, exist_ok=True)
    t.save(TEX, optimize=True)

    # ---------------- 4) previa (com a CABECA do jogo em escala, eixo certo)
    vistas = [(0.0, 0.0, 'DE FRENTE'), (0.7, -0.06, '3/4'), (1.55, -0.04, 'LADO'), (0.0, -0.5, 'DE BAIXO')]
    lado = 340
    tira = Image.new('RGB', (lado * 4 + 25, lado + 46), (10, 9, 12))
    d = ImageDraw.Draw(tira)
    for i, (yaw, pitch, nome) in enumerate(vistas):
        im = render(pr, nr, uv, idx, t, yaw, pitch, lado, caixa_cabeca=True, foco=np.array([0, -4, 0]))
        tira.paste(im, (5 + i * (lado + 5), 30))
        d.text((7 + i * (lado + 5), 10), nome, fill=(232, 226, 214))
    d.text((8, lado + 34), 'caixa marrom = CABEÇA do jogador (y -8..0, rosto em z=-4) — a máscara tem que encaixar nela', fill=(150, 146, 136))
    PREVIA.parent.mkdir(exist_ok=True)
    tira.save(PREVIA)

    print(f'{BIN_ROSTO} {BIN_ROSTO.stat().st_size} B ({nv} vertices)')
    print(f'{BIN_ITEM} {BIN_ITEM.stat().st_size} B')
    print(f'{TEX} {TEX.stat().st_size} B {t.size}')


if __name__ == '__main__':
    main()
