#!/usr/bin/env python3
"""(5.2.2) Reconstrói geo/mascara_glb.bin no lugar certo da cabeça.

O PROBLEMA QUE ISSO RESOLVE
---------------------------
A malha do GLB vinha em x ±2,70 (só 68% da largura do rosto) e z −5,05..−3,75,
ou seja **atrás da camada do chapéu** da skin, que fica em z = −4,5. O
`PlayerRenderer` desenha o chapéu por cima → 63% da máscara ficava escondida e
só sobrava um oval no meio da cara. Era o "ovoide escuro" da captura.

O QUE ESTE SCRIPT FAZ
---------------------
1. estica a malha para COBRIR o rosto (x ±4,15 · y −8,05..−0,05);
2. empurra para FRENTE da camada do chapéu (borda em z = −4,78);
3. conserta o WINDING: os triângulos vinham no sentido inverso da normal
   (0,2% de concordância) — é isso que fazia a máscara parecer "invertida"
   (o jogo iluminava como se fosse o lado de dentro);
4. reescreve o .bin no mesmo formato (RPTM v1, arrays planares big-endian).

Uso:  python3 arte/ajustar_mascara.py
"""
import struct
import numpy as np

GEO = "/home/user/rp-tag/src/main/resources/assets/rptag/geo/mascara_glb.bin"

# ---- alvo, em pixels do modelo (1 px = 1/16 de bloco)
# cabeça = x -4..4 · y -8..0 · z -4..4 · chapéu = ±4,5
# altura-alvo da máscara (a cabeça vai de -8 a 0). A largura vem da PROPORÇÃO
# do modelo — esticar em X deixava a cara larga como um prato.
ALVO_Y = (-8.05, -0.05)
# borda de trás da máscara: 0,28 na frente do chapéu (-4,5)
BORDA_Z = -4.78


def ler(caminho):
    d = open(caminho, "rb").read()
    assert d[:4] == b"RPTM", d[:4]
    ver, n = struct.unpack(">ii", d[4:12])
    assert ver == 1, ver
    off = 12
    arrs = []
    for _ in range(8):
        arrs.append(np.frombuffer(d[off:off + 4 * n], dtype=">f4").astype(np.float64))
        off += 4 * n
    px, py, pz, nx, ny, nz, u, v = arrs
    return dict(pos=np.stack([px, py, pz], 1), nrm=np.stack([nx, ny, nz], 1),
                uv=np.stack([u, v], 1), n=n)


def escrever(caminho, pos, nrm, uv):
    n = len(pos)
    with open(caminho, "wb") as f:
        f.write(b"RPTM")
        f.write(struct.pack(">ii", 1, n))
        for k in range(3):
            f.write(pos[:, k].astype(">f4").tobytes())
        for k in range(3):
            f.write(nrm[:, k].astype(">f4").tobytes())
        f.write(uv[:, 0].astype(">f4").tobytes())
        f.write(uv[:, 1].astype(">f4").tobytes())
    return n


def principal():
    m = ler(GEO)
    pos, nrm, uv = m["pos"].copy(), m["nrm"].copy(), m["uv"].copy()
    x0, x1 = pos[:, 0].min(), pos[:, 0].max()
    y0, y1 = pos[:, 1].min(), pos[:, 1].max()
    z0, z1 = pos[:, 2].min(), pos[:, 2].max()
    print(f"antes: x {x0:+.2f}..{x1:+.2f}  y {y0:+.2f}..{y1:+.2f}  z {z0:+.2f}..{z1:+.2f}")

    # ESCALA UNIFORME: a máscara é um rosto, não um adesivo. Encaixa a altura e
    # deixa a largura seguir a proporção do modelo (5,4 x 7,0 -> ~6,3 x 8,1).
    esc = (ALVO_Y[1] - ALVO_Y[0]) / (y1 - y0)
    cx = (x0 + x1) / 2.0
    pos[:, 0] = (pos[:, 0] - cx) * esc
    pos[:, 1] = ALVO_Y[0] + (pos[:, 1] - y0) * esc
    # profundidade: a borda de trás fica 0,28 à frente da camada do chapéu (-4,5)
    pos[:, 2] = (pos[:, 2] - z1) * esc + BORDA_Z

    # ---- winding: 99,8% dos triângulos estavam no sentido inverso da normal
    tri = pos.reshape(-1, 3, 3)
    geo = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    sn = nrm.reshape(-1, 3, 3).mean(1)
    acordo = (geo * sn).sum(1) > 0
    print(f"triângulos concordando com a normal ANTES: {acordo.mean()*100:.1f}%")
    tri_fix = tri.copy()
    tri_fix[~acordo] = tri[~acordo][:, [0, 2, 1], :]      # troca v1 <-> v2
    pos = tri_fix.reshape(-1, 3)
    geo = np.cross(tri_fix[:, 1] - tri_fix[:, 0], tri_fix[:, 2] - tri_fix[:, 0])
    acordo = (geo * sn).sum(1) > 0
    print(f"triângulos concordando com a normal DEPOIS: {acordo.mean()*100:.1f}%")


    # ---- NORMAIS: NÃO MEXER.
    # Testei recalcular do zero e PIOROU: a máscara tem casca de fora e casca de
    # dentro (com a aba que fecha o volume), então ~50% das normais apontam para
    # dentro de propósito. A média por vértice juntava frente e verso, as normais
    # se cancelavam e o relevo (olhos, boca) virava borrão. As do GLB estão certas.
    # Confira em previa/diag_normais.png: "malha nova + normais originais" = certo.

    n = escrever(GEO, pos, nrm, uv)
    p = pos
    print(f"depois: x {p[:,0].min():+.2f}..{p[:,0].max():+.2f}  "
          f"y {p[:,1].min():+.2f}..{p[:,1].max():+.2f}  "
          f"z {p[:,2].min():+.2f}..{p[:,2].max():+.2f}")
    print(f"gravado: {n} vértices / {n//3} triângulos")
    print(f"à frente do chapéu (z < -4.5): {(p[:,2] < -4.5).mean()*100:.1f}% dos vértices")


if __name__ == "__main__":
    principal()
