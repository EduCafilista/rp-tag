"""Previa de um aviso em video: quadros sobre fundo cinza-escuro + texto aproximado.

Uso: python arte/previa.py <id> [--quadros 20,60,94,125] [--gif]

Le arte/render/<id>/NNNN.png (+ marcadores.json para o fade) e grava em
arte/previa/<id>/: os quadros-chave como PNG e, com --gif, um GIF 640x360
a 12 fps. O texto ("AVISO" + mensagem) aqui e so uma APROXIMACAO pra julgar
a composicao — no jogo ele e desenhado pelo codigo (MorteAvisoFx).
"""
import glob
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FPS = 24


def ss(a, b, x):
    u = min(1.0, max(0.0, (x - a) / (b - a)))
    return u * u * (3 - 2 * u)


def fade(meta, t):
    f = meta.get("fade")
    if not f:
        return 1.0
    return ss(f["in"][0], f["in"][1], t) * (1 - ss(f["out"][0], f["out"][1], t))


def compor(png, meta, quadro, mensagem="VOCE MORREU"):
    t = quadro / FPS
    img = Image.open(png).convert("RGBA")
    r, g, b, a = img.split()
    env = fade(meta, t)
    a = a.point(lambda v: int(v * env))
    img = Image.merge("RGBA", (r, g, b, a))
    fundo = Image.new("RGBA", img.size, (32, 32, 32, 255))
    fundo.alpha_composite(img)
    d = ImageDraw.Draw(fundo)
    w, h = fundo.size
    linha_reta = next((m["frame"] for m in meta["markers"] if m["name"] == "linha_reta"), 10 ** 9)
    cor = (210, 30, 40) if quadro < linha_reta else (225, 218, 195)
    al = int(255 * ss(0.5, 0.9, t) * env)
    if al > 8:
        # mesma posicao/tamanho do jogo (GUI k=1: titulo 8px*1.9 em y 118-134 de 240;
        # mensagem 0.75x em cy + 25)
        f1 = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", int(h * 15.2 / 240 * 1.35))
        f2 = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", int(h * 6.0 / 240 * 1.35))
        camada = Image.new("RGBA", fundo.size, (0, 0, 0, 0))
        dc = ImageDraw.Draw(camada)
        dc.text((w / 2, h * 126 / 240), "AVISO", font=f1, fill=cor + (al,), anchor="mm")
        dc.text((w / 2, h * 148 / 240), mensagem, font=f2, fill=cor + (al,), anchor="mm")
        fundo.alpha_composite(camada)
    return fundo.convert("RGB")


def main():
    args = sys.argv[1:]
    fx = args[0]
    src = os.path.join(RAIZ, "arte", "render", fx)
    dst = os.path.join(RAIZ, "arte", "previa", fx)
    os.makedirs(dst, exist_ok=True)
    with open(os.path.join(src, "marcadores.json"), encoding="utf-8") as fp:
        meta = json.load(fp)
    pngs = {int(os.path.basename(p)[:4]): p for p in glob.glob(os.path.join(src, "[0-9][0-9][0-9][0-9].png"))}
    if "--quadros" in args:
        for q in [int(v) for v in args[args.index("--quadros") + 1].split(",")]:
            compor(pngs[q], meta, q).save(os.path.join(dst, "q%04d.png" % q))
    if "--gif" in args:
        quadros = [compor(pngs[q], meta, q).resize((640, 360), Image.LANCZOS)
                   for q in sorted(pngs) if q % 2 == 0]
        quadros[0].save(os.path.join(dst, fx + ".gif"), save_all=True, append_images=quadros[1:],
                        duration=int(1000 / 12), loop=0, optimize=True)
    print("previa em", dst)


if __name__ == "__main__":
    main()
