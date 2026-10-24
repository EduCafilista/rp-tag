"""Exporta um aviso renderizado no Blender para o jar do mod.

Uso: python arte/exportar.py <id> [--blend normal|aditivo]

Le arte/render/<id>/NNNN.png (RGBA) + marcadores.json e grava em
src/main/resources/assets/rptag/fx/<id>/:
  frames/NNNN_c.jpg  cor (RGB, qualidade 85, ou "jpeg.cor" do marcadores.json)
  frames/NNNN_a.jpg  alfa (tons de cinza, qualidade 90)
  fx.json            fps, quadros, tamanho, mistura e marcadores
O "fade" do marcadores.json (entrada/saida suave, o env do Java) e
aplicado aqui, multiplicando o alfa de cada quadro.
"""
import glob
import json
import os
import sys

from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FPS, LARG, ALT = 24, 1280, 720


def ss(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)


def fade(meta, t):
    f = meta.get("fade")
    if not f:
        return 1.0
    return ss(f["in"][0], f["in"][1], t) * (1 - ss(f["out"][0], f["out"][1], t))


def qualidades(meta):
    """(cor, alfa) do JPEG. A cena pode baixar a cor (marcadores.json "jpeg": {"cor": N});
    o alfa fica sempre em 90 (abaixo disso o recorte ganha halo)."""
    cor = int(meta.get("jpeg", {}).get("cor", 85))
    return cor, 90


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit("uso: python arte/exportar.py <id> [--blend normal|aditivo]")
    fx = args[0]
    blend = args[args.index("--blend") + 1] if "--blend" in args else "normal"
    src = os.path.join(RAIZ, "arte", "render", fx)
    dst = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "fx", fx)
    frames_dir = os.path.join(dst, "frames")
    with open(os.path.join(src, "marcadores.json"), encoding="utf-8") as fp:
        meta = json.load(fp)
    fps = float(meta.get("fps", FPS))   # (4.7.5) video importado pode vir em outro fps
    pngs = sorted(glob.glob(os.path.join(src, "[0-9][0-9][0-9][0-9].png")))
    if not pngs:
        sys.exit("nenhum quadro em " + src)
    nums = [int(os.path.basename(p)[:4]) for p in pngs]
    if nums != list(range(len(nums))):
        sys.exit("quadros faltando em " + src + " (tem que ser 0000..N-1 sem buracos)")

    os.makedirs(frames_dir, exist_ok=True)
    for velho in glob.glob(os.path.join(frames_dir, "*.jpg")):
        os.remove(velho)
    total = 0
    q_cor, q_alfa = qualidades(meta)
    for q, p in enumerate(pngs):
        img = Image.open(p).convert("RGBA")
        if img.size != (LARG, ALT):
            sys.exit("%s nao e %dx%d" % (p, LARG, ALT))
        r, g, b, a = img.split()
        env = fade(meta, q / fps)
        if env < 1.0:
            a = a.point(lambda v, e=env: int(round(v * e)))
        c_path = os.path.join(frames_dir, "%04d_c.jpg" % q)
        a_path = os.path.join(frames_dir, "%04d_a.jpg" % q)
        Image.merge("RGB", (r, g, b)).save(c_path, "JPEG", quality=q_cor, optimize=True)
        a.save(a_path, "JPEG", quality=q_alfa, optimize=True)
        total += os.path.getsize(c_path) + os.path.getsize(a_path)

    fxjson = {
        "id": fx,
        "fps": round(fps, 3) if fps != int(fps) else int(fps),
        "frames": len(pngs),
        "width": LARG,
        "height": ALT,
        "blend": blend,
        "markers": meta.get("markers", []),
    }
    with open(os.path.join(dst, "fx.json"), "w", encoding="utf-8", newline="\n") as fp:
        json.dump(fxjson, fp, indent=2)
        fp.write("\n")
    print("exportado: %s — %d quadros, %.1f MB" % (fx, len(pngs), total / 1e6))


if __name__ == "__main__":
    main()
