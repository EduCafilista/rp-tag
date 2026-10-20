"""Confere os avisos em video que vao no jar (chamado pelo check.py).

Uso: python arte/validar.py   -> sai 1 se algo estiver errado

Para cada assets/rptag/fx/<id>/: fx.json valido (mesmas regras do
FxTimeline.parse), numero de quadros = arquivos _c = arquivos _a, todo _c
com seu _a, tudo 1280x720, _a em tons de cinza, marcadores dentro do video.
"""
import glob
import json
import os
import sys

from PIL import Image

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FX = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "fx")
OK, FALHAS = 0, 0


def chk(nome, cond):
    global OK, FALHAS
    print(("PASS  " if cond else "FAIL  ") + nome)
    OK, FALHAS = OK + (1 if cond else 0), FALHAS + (0 if cond else 1)


def validar(pasta):
    fx = os.path.basename(pasta)
    try:
        with open(os.path.join(pasta, "fx.json"), encoding="utf-8") as fp:
            j = json.load(fp)
    except (OSError, ValueError) as e:
        chk("%s: fx.json legivel (%s)" % (fx, e), False)
        return
    frames = j.get("frames", 0)
    chk("%s: fps > 0 e frames > 0" % fx, j.get("fps", 0) > 0 and frames > 0)
    chk("%s: 1280x720 no fx.json" % fx, j.get("width") == 1280 and j.get("height") == 720)
    chk("%s: blend normal|aditivo" % fx, j.get("blend") in ("normal", "aditivo"))
    chk("%s: marcadores dentro do video" % fx,
        all(0 <= m.get("frame", -1) < frames and m.get("name") for m in j.get("markers", [])))
    cs = sorted(glob.glob(os.path.join(pasta, "frames", "*_c.jpg")))
    as_ = sorted(glob.glob(os.path.join(pasta, "frames", "*_a.jpg")))
    chk("%s: %d quadros = %d _c = %d _a" % (fx, frames, len(cs), len(as_)), frames == len(cs) == len(as_))
    esperados = ["%04d" % q for q in range(frames)]
    chk("%s: todo _c tem seu _a (0000..%04d)" % (fx, frames - 1),
        [os.path.basename(p)[:4] for p in cs] == esperados and [os.path.basename(p)[:4] for p in as_] == esperados)
    tam = all(Image.open(p).size == (1280, 720) for p in cs + as_)
    chk("%s: todos os quadros 1280x720" % fx, tam)
    chk("%s: alfa em tons de cinza (L)" % fx, all(Image.open(p).mode == "L" for p in as_))
    mb = sum(os.path.getsize(p) for p in cs + as_) / 1e6
    print("      %s: %.1f MB" % (fx, mb))


def main():
    pastas = sorted(p for p in glob.glob(os.path.join(FX, "*")) if os.path.isdir(p))
    if not pastas:
        print("nenhum aviso em video em " + FX)
    for p in pastas:
        validar(p)
    print("\nvalidar.py: %d ok, %d falhas" % (OK, FALHAS))
    sys.exit(1 if FALHAS else 0)


if __name__ == "__main__":
    main()
