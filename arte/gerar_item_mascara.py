"""MODELO 3D DO ITEM da mascara de pedra (4.9.8).

Antes o item era `item/generated` — um sprite chato: na mao e no inventario
aparecia um desenho quadrado, nada de mascara. Agora ele e um modelo de
VERDADE (JSON com `elements`): 4 faixas de pedra que formam a silhueta de
escudo, uma faixa de nariz saltando pra frente e uma borda (rim) que da
volume. As faces da FRENTE mostram a cara; as de lado/tras mostram a pedra.

Gera:
  assets/rptag/textures/item/mascara_pedra_item.png   (64x64: cara + pedra)
  assets/rptag/models/item/mascara_pedra.json          (elements + display)
  previa/mascara_item.png                             (conferencia 3 angulos)

uso: python arte/gerar_item_mascara.py
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
TEX_ENT = RAIZ / 'src/main/resources/assets/rptag/textures/entity/mascara_pedra.png'
TEX_ITEM = RAIZ / 'src/main/resources/assets/rptag/textures/item/mascara_pedra_item.png'
MODELO = RAIZ / 'src/main/resources/assets/rptag/models/item/mascara_pedra.json'
PREVIA = RAIZ / 'previa/mascara_item.png'

L = 64          # lado da textura
CARA = (0, 0, 32, 32)      # regiao da CARA (frente)
PEDRA = (32, 0, 64, 32)    # regiao da PEDRA (lados/tras/fundo)
BORDA = (0, 32, 64, 64)    # regiao da BORDA (rim)


def textura():
    """Monta a textura 64x64 do item a partir da arte da mascara (entity)."""
    ent = Image.open(TEX_ENT).convert('RGB')
    w, h = ent.size
    # a cara ocupa o miolo da textura da malha; recorta a regiao util
    cara = ent.crop((int(w * 0.16), int(h * 0.04), int(w * 0.84), int(h * 0.98))).resize((32, 32), Image.LANCZOS)

    img = Image.new('RGB', (L, L), (150, 146, 138))
    # ---- CARA (0,0)-(32,32)
    img.paste(cara, (CARA[0], CARA[1]))

    # ---- PEDRA (32,0)-(64,32): granito claro
    rng = np.random.default_rng(5)
    pedra = np.zeros((32, 32, 3), np.float32) + np.array([176, 172, 163], np.float32)
    for escala, amp in ((4, 14), (8, 8), (16, 5)):
        n = rng.normal(0, 1, (escala, escala))
        up = np.array(Image.fromarray(((n - n.min()) / (n.max() - n.min()) * 255).astype(np.uint8))
                      .resize((32, 32), Image.BICUBIC), np.float32) / 255.0 - 0.5
        pedra += up[:, :, None] * amp
    img.paste(Image.fromarray(np.clip(pedra, 0, 255).astype(np.uint8)), (PEDRA[0], PEDRA[1]))

    # ---- BORDA (0,32)-(64,64): pedra escura com lascas
    d = ImageDraw.Draw(img)
    d.rectangle((0, 32, 63, 63), fill=(104, 100, 92))
    for _ in range(70):
        x = int(rng.integers(0, L)); y = int(rng.integers(32, L))
        c = int(rng.integers(80, 150))
        d.point((x, y), fill=(c, c - 4, c - 10))
    for _ in range(12):
        x = int(rng.integers(2, L - 2)); y = int(rng.integers(34, L - 2))
        d.line((x, y, x + int(rng.integers(-6, 7)), y + int(rng.integers(-4, 5))), fill=(58, 56, 52), width=1)
    return img


def uv(regiao, u0, v0, u1, v1):
    """Converte um sub-retangulo (0..1) da regiao (em PIXELS de 64) em uv 0..16."""
    rx, ry, rx2, ry2 = regiao
    k = 16.0 / L                      # pixels -> unidades de uv
    return [round((rx + (rx2 - rx) * u0) * k, 2), round((ry + (ry2 - ry) * v0) * k, 2),
            round((rx + (rx2 - rx) * u1) * k, 2), round((ry + (ry2 - ry) * v1) * k, 2)]


def modelo():
    # ---- faixas da silhueta (de baixo pra cima): (y0, y1, meia-largura)
    faixas = [(1.6, 3.4, 1.35), (3.4, 5.2, 1.95), (5.2, 7.0, 2.55),
              (7.0, 8.8, 3.05), (8.8, 10.6, 3.45), (10.6, 12.4, 3.55), (12.4, 14.5, 3.05)]
    Z0, Z1 = 7.2, 9.0            # profundidade da placa (1,8)
    elementos = []

    for k, (y0, y1, meia) in enumerate(faixas):
        x0, x1 = round(8 - meia, 2), round(8 + meia, 2)
        v0 = k / len(faixas)
        v1 = (k + 1) / len(faixas)
        elementos.append({
            "from": [x0, y0, Z0], "to": [x1, y1, Z1],
            "faces": {
                # frente e tras = CARA (a cara aparece dos dois lados)
                "north": {"uv": uv(CARA, 0, 1 - v1, 1, 1 - v0), "texture": "#cara"},
                "south": {"uv": uv(CARA, 0, 1 - v1, 1, 1 - v0), "texture": "#cara"},
                # laterais, topo e base = PEDRA
                "east": {"uv": uv(PEDRA, 0, 1 - v1, 1, 1 - v0), "texture": "#pedra"},
                "west": {"uv": uv(PEDRA, 0, 1 - v1, 1, 1 - v0), "texture": "#pedra"},
                "up": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
                "down": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
            },
        })

    # ---- nariz: bloco que SALTA pra frente (z menor = pra fora)
    elementos.append({
        "from": [7.1, 6.4, 5.9], "to": [8.9, 10.2, 7.4],
        "faces": {
            "north": {"uv": uv(CARA, 0.42, 0.30, 0.58, 0.62), "texture": "#cara"},
            "south": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
            "east": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
            "west": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
            "up": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
            "down": {"uv": uv(PEDRA, 0, 0, 1, 1), "texture": "#pedra"},
        },
    })

    # ---- rim: borda grossa em volta (dá volume e faz sombra)
    rim_z0, rim_z1 = 6.95, 9.35
    for (x0, y0, x1, y1) in [(4.6, 12.4, 11.4, 14.75), (3.9, 9.0, 4.55, 12.6), (11.45, 9.0, 12.1, 12.6),
                             (4.7, 6.6, 5.25, 9.1), (10.75, 6.6, 11.3, 9.1)]:
        elementos.append({
            "from": [x0, y0, rim_z0], "to": [x1, y1, rim_z1],
            "faces": {
                "north": {"uv": uv(BORDA, 0, 0, 1, 1), "texture": "#borda"},
                "south": {"uv": uv(BORDA, 0, 0, 1, 1), "texture": "#borda"},
                "east": {"uv": uv(BORDA, 0, 0, 1, 1), "texture": "#borda"},
                "west": {"uv": uv(BORDA, 0, 0, 1, 1), "texture": "#borda"},
                "up": {"uv": uv(BORDA, 0, 0, 1, 1), "texture": "#borda"},
                "down": {"uv": uv(BORDA, 0, 0, 1, 1), "texture": "#borda"},
            },
        })

    return {
        "textures": {"cara": "rptag:item/mascara_pedra_item", "pedra": "rptag:item/mascara_pedra_item",
                     "borda": "rptag:item/mascara_pedra_item", "particle": "rptag:item/mascara_pedra_item"},
        "elements": elementos,
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 4.5, 0], "scale": [0.72, 0.72, 0.72]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 4.5, 0], "scale": [0.72, 0.72, 0.72]},
            "firstperson_righthand": {"rotation": [0, 180, 0], "translation": [1.13, 5.5, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1.13, 5.5, 1.13], "scale": [0.68, 0.68, 0.68]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        },
    }


# ===================================================== previa
def render_json(m, tex, yaw, pitch, lado=300):
    """Rasteriza os `elements` do modelo (só pra conferir que virou mascara)."""
    R = rot_x(pitch) @ rot_y(yaw)
    img = np.zeros((lado, lado, 3), np.float32) + np.array((16, 15, 19), np.float32) / 255.0
    zb = np.full((lado, lado), 1e9, np.float32)
    tx = np.asarray(tex.convert('RGB'), np.float32) / 255.0
    th, tw = tx.shape[:2]
    esc = lado / 18.0
    ox = oy = lado / 2.0
    luz = np.array([-0.4, 0.5, -0.75]); luz /= np.linalg.norm(luz)
    for el in m['elements']:
        x0, y0, z0 = el['from']; x1, y1, z1 = el['to']
        # 6 faces: (normal, 4 cantos, uv)
        F = [
            ((0, 0, -1), [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 'north'),
            ((0, 0, 1), [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], 'south'),
            ((-1, 0, 0), [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], 'west'),
            ((1, 0, 0), [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], 'east'),
            ((0, 1, 0), [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 'up'),
            ((0, -1, 0), [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], 'down'),
        ]
        for n, cantos, k in F:
            if k not in el['faces']:
                continue
            f = el['faces'][k]
            u0, v0, u1, v1 = f['uv']
            P = np.array(cantos, np.float32) @ R.T
            N = np.array(n, np.float32) @ R.T
            sx = ox + (P[:, 0] - 8.0) * esc; sy = oy - (P[:, 1] - 8.0) * esc; sz = P[:, 2]
            for (a, b, c, ua, va, ub, vb, uc, vc) in ((0, 1, 2, u0, v0, u1, v0, u1, v1),
                                                      (0, 2, 3, u0, v0, u1, v1, u0, v1)):
                tri_x = np.array([sx[a], sx[b], sx[c]]); tri_y = np.array([sy[a], sy[b], sy[c]])
                tri_z = np.array([sz[a], sz[b], sz[c]])
                X0 = int(max(0, np.floor(tri_x.min()))); X1 = int(min(lado - 1, np.ceil(tri_x.max())))
                Y0 = int(max(0, np.floor(tri_y.min()))); Y1 = int(min(lado - 1, np.ceil(tri_y.max())))
                if X1 <= X0 or Y1 <= Y0:
                    continue
                gx, gy = np.meshgrid(np.arange(X0, X1 + 1) + .5, np.arange(Y0, Y1 + 1) + .5)
                ax, ay = tri_x[0], tri_y[0]; bx, by = tri_x[1], tri_y[1]; cx2, cy2 = tri_x[2], tri_y[2]
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
                cor = tx[tj, ti] * (0.42 + 0.70 * dif)
                img[Y0:Y1 + 1, X0:X1 + 1][mm] = cor[mm]
                sub[mm] = z[mm]; zb[Y0:Y1 + 1, X0:X1 + 1] = sub
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def main():
    t = textura()
    TEX_ITEM.parent.mkdir(parents=True, exist_ok=True)
    t.save(TEX_ITEM)
    m = modelo()
    MODELO.write_text(json.dumps(m, indent=2) + "\n")

    vistas = [(0.0, 0.0, 'DE FRENTE'), (0.6, -0.10, '3/4'), (1.2, -0.10, 'QUASE DE LADO')]
    lado, folga = 300, 8
    tira = Image.new('RGB', (lado * 3 + folga * 4, lado + 40), (10, 9, 12))
    d = ImageDraw.Draw(tira)
    for i, (yaw, pitch, nome) in enumerate(vistas):
        im = render_json(m, t, yaw, pitch, lado)
        tira.paste(im, (folga + i * (lado + folga), 30))
        d.text((folga + i * (lado + folga) + 2, 10), 'ITEM 3D - ' + nome, fill=(235, 230, 220))
    PREVIA.parent.mkdir(exist_ok=True)
    tira.save(PREVIA)
    print(TEX_ITEM, TEX_ITEM.stat().st_size)
    print(MODELO, MODELO.stat().st_size, '| elementos:', len(m['elements']))
    print(PREVIA, PREVIA.stat().st_size)


if __name__ == '__main__':
    main()
