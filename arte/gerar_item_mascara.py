"""MODELO 3D E ICONE DO ITEM da mascara (5.0.1).

O item agora usa a CARA DO MODELO DE VERDADE: este script rasteriza a vista
frontal da malha do GLB (a mesma que o jogo desenha) e usa essa imagem como
textura da frente das faixas.

O que mudou da 4.9.8 para a 5.0.1:
  * o rosto vinha da arte antiga (gerada por codigo) -> agora vem do GLB;
  * o modelo estava GRANDE na mao (14,8 unidades de altura = 0,92 bloco);
    agora tem 9,2 unidades e as escalas de `display` encolheram junto;
  * o "rim" grosso que fazia a peca parecer um tijolo virou uma borda fina.

uso: python3 arte/gerar_item_mascara.py [caminho do .glb]
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
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/item/mascara_face.png'
MODELO = RAIZ / 'src/main/resources/assets/rptag/models/item/mascara_pedra.json'
PREVIA = RAIZ / 'previa/mascara_item.png'

L = 128                      # lado da textura do item
CARA = (0, 0, 64, 64)        # rosto (vista frontal do modelo)
PEDRA = (64, 0, 128, 64)     # pedra (lados, topo, base)

# ---- medidas do ITEM (unidades de item: 16 = 1 bloco inteiro)
ALT_ITEM = 9.2
LARG_MAX = 6.4


def ler_glb(caminho):
    d = Path(caminho).read_bytes()
    assert d[:4] == b'glTF', 'nao e um GLB'
    o, chunks = 12, []
    while o < len(d):
        clen, ctype = struct.unpack('<II', d[o:o + 8]); o += 8
        chunks.append((ctype, d[o:o + clen])); o += clen
    j = json.loads(chunks[0][1].decode('utf-8'))
    bin_ = chunks[1][1]
    TIPO = {5120: 'b', 5121: 'B', 5122: 'h', 5123: 'H', 5125: 'I', 5126: 'f'}
    NCOMP = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}

    def acc(i):
        a = j['accessors'][i]
        bv = j['bufferViews'][a['bufferView']]
        f = TIPO[a['componentType']]
        n = NCOMP[a['type']]
        off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        arr = np.frombuffer(bin_, dtype='<' + f, count=a['count'] * n, offset=off)
        arr = arr.astype(np.float64 if f == 'f' else np.int64)
        return arr.reshape(a['count'], n) if n > 1 else arr

    def imagem(i):
        import io
        im = j['images'][i]
        bv = j['bufferViews'][im['bufferView']]
        o0 = bv.get('byteOffset', 0)
        return Image.open(io.BytesIO(bin_[o0:o0 + bv['byteLength']]))

    prim = j['meshes'][0]['primitives'][0]
    pos = acc(prim['attributes']['POSITION'])
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
    mat = j['materials'][prim.get('material', 0)]
    tex = imagem(j['textures'][mat['pbrMetallicRoughness']['baseColorTexture']['index']]['source'])
    return pos, uv, idx, tex


def pedra_tile(n=128, base=(150, 146, 138)):
    """Textura de pedra do item (mesma nos lados e no fundo do rosto)."""
    rng = np.random.default_rng(7)
    a = np.zeros((n, n, 3), np.float32) + np.array(base, np.float32)
    for escala, amp in ((n // 16, 16), (n // 8, 9), (n // 4, 5)):
        r = rng.normal(0, 1, (escala, escala))
        up = np.array(Image.fromarray(((r - r.min()) / (r.max() - r.min()) * 255).astype(np.uint8))
                      .resize((n, n), Image.BICUBIC), np.float32) / 255.0 - 0.5
        a += up[:, :, None] * amp
    for _ in range(n * 2):
        x, y = int(rng.integers(0, n)), int(rng.integers(0, n))
        c = a[y, x] * float(rng.uniform(0.72, 1.05))
        a[y, x] = np.clip(c, 0, 255)
    return np.clip(a, 0, 255)


def render_frente(pos, uv, idx, tex, lado=192, margem=0.03):
    """Vista frontal ortografica da malha (rosto), com fundo de pedra."""
    mn, mx = pos.min(0), pos.max(0)
    c = (mn + mx) / 2
    q = pos - c
    s = (lado * (1 - 2 * margem)) / max(mx[1] - mn[1], mx[0] - mn[0])
    sx = lado / 2 + q[:, 0] * s
    sy = lado / 2 - q[:, 1] * s
    sz = q[:, 2]
    tx = np.asarray(tex.convert('RGBA'), np.float32) / 255.
    th, tw = tx.shape[:2]
    fundo = pedra_tile(lado) / 255.0
    fundo = np.array(Image.fromarray((fundo * 255).astype(np.uint8)).resize((lado, lado)), np.float32) / 255.
    img = fundo.copy()
    cobertura = np.zeros((lado, lado), bool)     # pixels que a malha cobre
    zb = np.full((lado, lado), 1e9, np.float32)
    luz = np.array([-0.35, 0.45, -0.82]); luz /= np.linalg.norm(luz)
    # normais aproximadas pela geometria (a frente olha pra -Z)
    for tri in idx:
        xs, ys, zs = sx[tri], sy[tri], sz[tri]
        x0, y0 = int(max(0, np.floor(xs.min()))), int(max(0, np.floor(ys.min())))
        x1, y1 = int(min(lado - 1, np.ceil(xs.max()))), int(min(lado - 1, np.ceil(ys.max())))
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        ax, ay, bx, by, cx, cy = xs[0], ys[0], xs[1], ys[1], xs[2], ys[2]
        d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(d) < 1e-9:
            continue
        w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / d
        w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / d
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        mm = m & (z < sub)
        if not mm.any():
            continue
        P = np.stack([w0, w1, w2], -1)
        u = (P[..., 0] * uv[tri[0], 0] + P[..., 1] * uv[tri[1], 0] + P[..., 2] * uv[tri[2], 0]) % 1.0
        v = (P[..., 0] * uv[tri[0], 1] + P[..., 1] * uv[tri[1], 1] + P[..., 2] * uv[tri[2], 1]) % 1.0
        ti = np.clip((u * tw).astype(int), 0, tw - 1)
        tj = np.clip(((1 - v) * th).astype(int), 0, th - 1)
        cor = tx[tj, ti, :3]
        if tx.shape[2] > 3:
            vis = tx[tj, ti, 3] > 0.5
            mm = mm & vis
            if not mm.any():
                continue
        # luz suave pela inclinacao aparente do triangulo (dx/dy no plano)
        e1 = np.array([bx - ax, by - ay]); e2 = np.array([cx - ax, cy - ay])
        cr = e1[0] * e2[1] - e1[1] * e2[0]
        soma = 0.86 if cr > 0 else 0.74
        img[y0:y1 + 1, x0:x1 + 1][mm] = (cor * soma)[mm]
        cobertura[y0:y1 + 1, x0:x1 + 1] |= mm
        sub[mm] = z[mm]; zb[y0:y1 + 1, x0:x1 + 1] = sub
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    # nitidez: contraste (percentis) + unsharp, senao em 64px a cara some
    # contraste SUAVE em torno do cinza medio (esticar por percentil estourava
    # a pedra pra branco e a cara pra preto) + unsharp leve
    from PIL import ImageFilter
    a = np.asarray(im, np.float32) / 255.0
    a = np.clip((a - 0.5) * 1.18 + 0.5, 0, 1)
    im = Image.fromarray((a * 255).astype(np.uint8))
    im = im.filter(ImageFilter.UnsharpMask(radius=1.6, percent=110, threshold=3))
    return im, cobertura


def textura(face, cobertura):
    """RGBA 128x128: rosto recortado (alfa) + pedra opaca nos lados."""
    arr = np.zeros((L, L, 4), np.uint8)
    p = pedra_tile(64).astype(np.uint8)
    arr[:, :, :3] = p[np.arange(L) % 64][:, np.arange(L) % 64]
    arr[:, :, 3] = 255

    face = face.convert('RGB').resize((64, 64), Image.LANCZOS)
    cob = np.asarray(Image.fromarray((cobertura * 255).astype(np.uint8)).resize((64, 64), Image.LANCZOS), np.float32)
    a8 = (np.clip((cob - 55.0) / 110.0, 0, 1) * 255).astype(np.uint8)
    r = np.asarray(face, np.uint8)
    arr[CARA[1]:CARA[1] + 64, CARA[0]:CARA[0] + 64, :3] = r
    # (5.0.1) ALFA de verdade: fora da silhueta = transparente. O `paste` do PIL
    # compoe sobre o fundo e o alfa se perde -> a peca virava uma caixa cinza.
    arr[CARA[1]:CARA[1] + 64, CARA[0]:CARA[0] + 64, 3] = a8
    return Image.fromarray(arr, 'RGBA')


def uv(regiao, u0, v0, u1, v1):
    """Sub-retangulo (0..1) da regiao em pixel -> uv 0..16 do modelo."""
    rx, ry, rx2, ry2 = regiao
    k = 16.0 / L
    return [round((rx + (rx2 - rx) * u0) * k, 2), round((ry + (ry2 - ry) * v0) * k, 2),
            round((rx + (rx2 - rx) * u1) * k, 2), round((ry + (ry2 - ry) * v1) * k, 2)]


def modelo():
    """Placa UNICA com o rosto recortado (alfa), mais uma casca de pedra atras.

    Antes eram faixas empilhadas: por mais finas que fossem, cada uma esticava
    uma fatia do rosto e a peca virava bandana/tijolo. Agora a silhueta vem do
    ALFA da textura — a mesma do modelo do dono — e o item fica leve na mao.
    """
    z0, z1 = 7.70, 7.78          # chapa fininha (0,08): sem moldura de lado
    topo, base = 12.9, 4.0       # altura 8,9 de item (0,56 bloco)
    meia = 4.35                  # largura total 8,7
    x0, x1 = round(8 - meia, 2), round(8 + meia, 2)

    def faces(frente, pedra=True):
        f = {"north": {"uv": uv(frente, 0, 0, 1, 1), "texture": "#face"}}
        if pedra:
            for k in ("south", "east", "west", "up", "down"):
                f[k] = {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"}
        return f

    # (5.0.1) só FRENTE e VERSO. As faces laterais eram retangulares e, vistas
    # de 3/4, apareciam como uma MOLDURA/caixa em volta do rosto (era o que o
    # dono chamava de "muito feia na mao"). Sem elas o item e o recorte puro.
    rasgo = {k: {"uv": uv(CARA, 0, 0, 1, 1), "texture": "#face"} for k in ("north", "south")}
    el = [
        # UMA placa: todas as faces saem do rosto recortado, entao a silhueta do
        # item e a do modelo em qualquer angulo (sem caixa de pedra atras)
        {"from": [x0, base, z0], "to": [x1, topo, z1], "faces": rasgo},
        # nariz: continha saltando pra frente, como no modelo
        {"from": [7.55, 7.3, z0 - 0.75], "to": [8.45, 9.5, z0 - 0.02],
         "faces": {"north": {"uv": uv(CARA, 0.46, 0.44, 0.54, 0.62), "texture": "#face"},
                   "east": {"uv": uv(CARA, 0.50, 0.44, 0.52, 0.62), "texture": "#face"},
                   "west": {"uv": uv(CARA, 0.48, 0.44, 0.50, 0.62), "texture": "#face"},
                   "up": {"uv": uv(CARA, 0.46, 0.44, 0.54, 0.46), "texture": "#face"},
                   "down": {"uv": uv(CARA, 0.46, 0.60, 0.54, 0.62), "texture": "#face"}}},
    ]

    return {
        # (5.0.1) cutout: o rosto tem ALFA (fora da silhueta = transparente).
        # Sem isso o jogo desenha o quadrado inteiro e a mascara vira uma caixa.
        "render_type": "minecraft:cutout",
        "textures": {"face": "rptag:item/mascara_face", "pedra": "rptag:item/mascara_face",
                     "particle": "rptag:item/mascara_face"},
        "elements": el,
        # (5.0.1) TUDO menor: na mao a peca estava com 0,92 bloco de altura e
        # batia na tela. Agora cabe na palma (0,34 bloco no terceiro pessoal).
        "display": {
            "gui": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.60, 0.60, 0.60]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0], "scale": [0.28, 0.28, 0.28]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.52, 0.52, 0.52]},
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2.4, 0], "scale": [0.38, 0.38, 0.38]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.4, 0], "scale": [0.38, 0.38, 0.38]},
            "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [0.7, 3.2, 0.7], "scale": [0.34, 0.34, 0.34]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0.7, 3.2, 0.7], "scale": [0.34, 0.34, 0.34]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
        },
    }


# ------------------------------------------------------------------ previa
def rot_y(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def render_json(m, tex, yaw, pitch, lado=300, escala=18.0):
    R = rot_x(pitch) @ rot_y(yaw)
    img = np.zeros((lado, lado, 3), np.float32) + np.array((16, 15, 19), np.float32) / 255.
    zb = np.full((lado, lado), 1e9, np.float32)
    tx = np.asarray(tex.convert('RGBA'), np.float32) / 255.
    th, tw = tx.shape[:2]
    esc, ox, oy = escala, lado / 2.0, lado / 2.0
    luz = np.array([-0.4, 0.5, -0.75]); luz /= np.linalg.norm(luz)
    for el in m['elements']:
        x0, y0, z0 = el['from']; x1, y1, z1 = el['to']
        F = [((0, 0, -1), [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 'north'),
             ((0, 0, 1), [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], 'south'),
             ((-1, 0, 0), [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], 'west'),
             ((1, 0, 0), [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], 'east'),
             ((0, 1, 0), [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 'up'),
             ((0, -1, 0), [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], 'down')]
        for n, cantos, k in F:
            if k not in el['faces']:
                continue
            f = el['faces'][k]
            u0, v0, u1, v1 = f['uv']
            P = np.array(cantos, np.float32) @ R.T
            N = np.array(n, np.float32) @ R.T
            sx = ox + (P[:, 0] - 8) * esc; sy = oy - (P[:, 1] - 8) * esc; sz = P[:, 2]
            for (a, b, c, ua, va, ub, vb, uc, vc) in ((0, 1, 2, u0, v0, u1, v0, u1, v1),
                                                      (0, 2, 3, u0, v0, u1, v1, u0, v1)):
                tri_x = np.array([sx[a], sx[b], sx[c]]); tri_y = np.array([sy[a], sy[b], sy[c]])
                tri_z = np.array([sz[a], sz[b], sz[c]])
                X0 = int(max(0, np.floor(tri_x.min()))); X1 = int(min(lado - 1, np.ceil(tri_x.max())))
                Y0 = int(max(0, np.floor(tri_y.min()))); Y1 = int(min(lado - 1, np.ceil(tri_y.max())))
                if X1 <= X0 or Y1 <= Y0:
                    continue
                gx, gy = np.meshgrid(np.arange(X0, X1 + 1) + .5, np.arange(Y0, Y1 + 1) + .5)
                ax, ay, bx, by, cx2, cy2 = tri_x[0], tri_y[0], tri_x[1], tri_y[1], tri_x[2], tri_y[2]
                d = (by - cy2) * (ax - cx2) + (cx2 - bx) * (ay - cy2)
                if abs(d) < 1e-9:
                    continue
                w0 = ((by - cy2) * (gx - cx2) + (cx2 - bx) * (gy - cy2)) / d
                w1 = ((cy2 - ay) * (gx - cx2) + (ax - cx2) * (gy - cy2)) / d
                w2 = 1 - w0 - w1
                dentro = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                if not dentro.any():
                    continue
                z = w0 * tri_z[0] + w1 * tri_z[1] + w2 * tri_z[2]
                sub = zb[Y0:Y1 + 1, X0:X1 + 1]
                mm = dentro & (z < sub)
                if not mm.any():
                    continue
                uu = w0 * ua + w1 * ub + w2 * uc
                vv = w0 * va + w1 * vb + w2 * vc
                ti = np.clip((uu / 16.0 * tw).astype(int), 0, tw - 1)
                tj = np.clip((vv / 16.0 * th).astype(int), 0, th - 1)
                dif = max(0.0, float(np.dot(N, luz)))
                cor = tx[tj, ti, :3] * (0.45 + 0.70 * dif)
                # corte por alfa (o jogo usa cutout: alpha < 0.5 nao desenha)
                if tx.shape[2] > 3:
                    mm = mm & (tx[tj, ti, 3] > 0.5)
                    if not mm.any():
                        continue
                img[Y0:Y1 + 1, X0:X1 + 1][mm] = cor[mm]
                sub[mm] = z[mm]; zb[Y0:Y1 + 1, X0:X1 + 1] = sub
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def main():
    pos, uv2, idx, tex0 = ler_glb(GLB)
    face, cob = render_frente(pos, uv2, idx, tex0)
    t = textura(face, cob)
    TEX.parent.mkdir(parents=True, exist_ok=True)
    t.save(TEX)
    m = modelo()
    # display numa linha só: fica fácil de conferir no check.py (e de ler)
    txt = json.dumps(m, indent=2)
    txt = txt.replace('"scale": [\n        ', '"scale": [').replace('"rotation": [\n        ', '"rotation": [') \
             .replace('"translation": [\n        ', '"translation": [')
    import re as _re
    txt = _re.sub(r'\[\s*([-0-9.,\s]+?)\s*\]', lambda mo: '[' + ', '.join(x.strip() for x in mo.group(1).replace('\n', ' ').split(',')) + ']', txt)
    MODELO.write_text(txt + "\n")

    # tamanho real da peca pra conferir o encaixe na mao
    ys = [e['from'][1] for e in m['elements']] + [e['to'][1] for e in m['elements']]
    xs = [e['from'][0] for e in m['elements']] + [e['to'][0] for e in m['elements']]
    alt, larg = max(ys) - min(ys), max(xs) - min(xs)
    print(f'item: {larg:.1f} x {alt:.1f} unidades  ({(alt/16):.2f} bloco de altura)')
    print(f'na mao (3a pessoa x0.38): {alt/16*0.38:.2f} bloco')

    # painel extra: o item NA MAO, com o braco do jogador em escala (o braco tem
    # 4 unidades de item de largura; o item na 3a pessoa usa scale 0.38)
    held = {k: v for k, v in m.items()}
    esc_item = m['display']['thirdperson_righthand']['scale'][0]
    el2 = []
    for e in m['elements']:
        el2.append({"from": [e['from'][0] * esc_item, e['from'][1] * esc_item, e['from'][2] * esc_item],
                    "to": [e['to'][0] * esc_item, e['to'][1] * esc_item, e['to'][2] * esc_item],
                    "faces": e['faces']})
    # braco: caixa de 4 x 12 x 4 (como o modelo do player), ao lado da peca
    braco = {"from": [2.6, -2.0, 6.0], "to": [6.6, 10.0, 10.0],
             "faces": {k: {"uv": [12.5, 4.0, 14.5, 6.0], "texture": "#pele"} for k in
                       ("north", "south", "east", "west", "up", "down")}}
    t2 = t.copy()
    t2.paste(Image.new('RGB', (16, 16), (196, 120, 88)), (100, 100))
    held['elements'] = el2 + [braco]
    held['textures'] = dict(m['textures'], pele='rptag:item/mascara_face')
    t2_arr = np.asarray(t2, np.uint8).copy()
    t2_arr[100:116, 100:116, :3] = np.array([196, 120, 88], np.uint8)
    t2 = Image.fromarray(t2_arr, 'RGBA')
    mao = render_json(held, t2, 0.55, -0.10, 300)
    d2 = ImageDraw.Draw(mao)
    d2.text((10, 8), f'NA MAO: {(max(e["to"][1] for e in m["elements"]) - min(e["from"][1] for e in m["elements"])) * esc_item / 16:.2f} bloco de altura', fill=(238, 220, 150))

    vistas = [(0.0, 0.0, 'DE FRENTE'), (0.65, -0.08, '3/4'), (1.35, -0.08, 'QUASE DE LADO')]
    lado, folga = 300, 8
    tira = Image.new('RGB', (lado * 4 + folga * 5, lado + 42), (10, 9, 12))
    d = ImageDraw.Draw(tira)
    for i, (yaw, pitch, nome) in enumerate(vistas):
        im = render_json(m, t, yaw, pitch, lado)
        tira.paste(im, (folga + i * (lado + folga), 28))
        d.text((folga + i * (lado + folga) + 2, 9), 'ITEM 3D - ' + nome, fill=(235, 230, 220))
    tira.paste(mao, (folga + 3 * (lado + folga), 28))
    d.text((folga + 3 * (lado + folga) + 2, 9), 'NA MAO DO JOGADOR', fill=(235, 230, 220))
    PREVIA.parent.mkdir(exist_ok=True)
    tira.save(PREVIA)
    print(TEX, TEX.stat().st_size, '|', MODELO, MODELO.stat().st_size, '| elementos:', len(m['elements']))


if __name__ == '__main__':
    main()
