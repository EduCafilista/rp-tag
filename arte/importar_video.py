"""Importa QUALQUER video/GIF e transforma no aviso em video do mod.

Uso:
    python3 arte/importar_video.py <id> <arquivo> [opcoes]

    <id>       nome do aviso: egipcio, morte, inferno, divino, real, void, gelo
    <arquivo>  .mp4 .webm .mkv .mov .gif .avi (ou uma pasta de PNGs)

Opcoes:
    --inicio S     comeca em S segundos            (padrao 0)
    --fim S        termina em S segundos           (padrao: ate o fim)
    --fps N        quadros por segundo             (padrao 24, igual aos outros)
    --max N        teto de quadros                 (padrao 288 = 12 s a 24 fps)
    --ajuste X     cover (padrao, corta) | contain (letterbox) | stretch
    --sem-export   so gera os PNG em arte/render/, nao mexe no jar

O que ele faz:
  1) ffmpeg extrai os quadros em 1280x720 (16:9), na ordem, em
     arte/render/<id>/NNNN.png
  2) escreve arte/render/<id>/marcadores.json (fps, quadros, fade de entrada
     e de saida) — o fade do comeco/fim e o que faz o aviso "entrar" na tela
  3) roda arte/exportar.py <id>, que converte para frames/NNNN_c.jpg +
     NNNN_a.jpg + fx.json dentro de src/main/resources/assets/rptag/fx/<id>/

Atencao: isso SUBSTITUI os quadros daquele aviso. O alfa vem do video (video
normal = tela cheia opaca, que e o caso dos avisos atuais). Se o video tiver
fundo preto que devia ser transparente, me avise que eu trato o alfa.
"""
import json
import os
import shutil
import subprocess
import sys

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FPS_PADRAO = 24
LARG, ALT = 1280, 720
MAX_PADRAO = 288


def ffmpeg_exe():
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        pass
    for nome in ("ffmpeg", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg"):
        caminho = shutil.which(nome) if "/" not in nome else (nome if os.path.exists(nome) else None)
        if caminho:
            return caminho
    sys.exit("ffmpeg nao encontrado. Rode: pip install imageio-ffmpeg")


def duracao(ffmpeg, arquivo):
    """Duracao em segundos, sem ffprobe (o build static pode nao ter)."""
    out = subprocess.run([ffmpeg, "-i", arquivo], capture_output=True, text=True).stderr
    for linha in out.splitlines():
        if "Duration:" in linha:
            hms = linha.split("Duration:")[1].split(",")[0].strip()
            h, m, s = hms.split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    return 0.0


def filtro(ajuste):
    if ajuste == "stretch":
        return f"scale={LARG}:{ALT},setsar=1"
    if ajuste == "contain":
        return (f"scale={LARG}:{ALT}:force_original_aspect_ratio=decrease,"
                f"pad={LARG}:{ALT}:(ow-iw)/2:(oh-ih)/2:black,setsar=1")
    # cover (padrao): preenche a tela e corta o que sobra, sem distorcer
    return (f"scale={LARG}:{ALT}:force_original_aspect_ratio=increase,"
            f"crop={LARG}:{ALT},setsar=1")


def opcoes(argv):
    o = {"inicio": 0.0, "fim": None, "fps": FPS_PADRAO, "max": MAX_PADRAO,
         "ajuste": "cover", "export": True}
    resto = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--inicio":
            o["inicio"] = float(argv[i + 1]); i += 2
        elif a == "--fim":
            o["fim"] = float(argv[i + 1]); i += 2
        elif a == "--fps":
            o["fps"] = float(argv[i + 1]); i += 2
        elif a == "--max":
            o["max"] = int(argv[i + 1]); i += 2
        elif a == "--ajuste":
            o["ajuste"] = argv[i + 1]; i += 2
        elif a == "--sem-export":
            o["export"] = False; i += 1
        else:
            resto.append(a); i += 1
    return resto, o


def main():
    resto, opt = opcoes(sys.argv[1:])
    if len(resto) < 2:
        sys.exit(__doc__)
    fx, origem = resto[0], resto[1]
    if not os.path.exists(origem):
        sys.exit("arquivo nao encontrado: " + origem)

    destino = os.path.join(RAIZ, "arte", "render", fx)
    shutil.rmtree(destino, ignore_errors=True)
    os.makedirs(destino, exist_ok=True)

    ffmpeg = ffmpeg_exe()
    dur = duracao(ffmpeg, origem)
    fim = opt["fim"] if opt["fim"] is not None else (dur if dur > 0 else 0)
    trecho = max(0.1, fim - opt["inicio"])

    # teto de quadros: baixa o fps quando o trecho for longo demais
    fps = opt["fps"]
    if trecho * fps > opt["max"]:
        fps = max(6.0, opt["max"] / trecho)
        print(f"[hmm] trecho de {trecho:.1f}s não cabe em {opt['max']} quadros a {opt['fps']} fps "
              f"→ usando {fps:.2f} fps")

    cmd = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error"]
    if opt["inicio"]:
        cmd += ["-ss", f"{opt['inicio']}"]
    if opt["fim"] is not None:
        cmd += ["-t", f"{trecho}"]
    cmd += ["-i", origem, "-vf", filtro(opt["ajuste"]), "-r", f"{fps}",
            "-pix_fmt", "rgb24", os.path.join(destino, "%04d.png")]
    print("[1/3] extraindo quadros...", " ".join(cmd))
    subprocess.run(cmd, check=True)

    pngs = sorted(f for f in os.listdir(destino) if f.endswith(".png"))
    if not pngs:
        sys.exit("o ffmpeg não extraiu nenhum quadro — o arquivo tem vídeo mesmo?")
    # o ffmpeg numera a partir de 0001; o mod exige 0000..N-1 sem buracos
    for i, nome in enumerate(pngs):
        os.rename(os.path.join(destino, nome), os.path.join(destino, f"{i:04d}.png"))
    n = len(pngs)

    meta = {
        "fps": round(fps, 3),
        "quadros": n,
        "fade": {"in": [0.0, min(0.6, n / fps / 8)], "out": [max(0.0, n / fps - 0.5), n / fps]},
        "jpeg": {"cor": 80},
        "origem": os.path.basename(origem),
    }
    with open(os.path.join(destino, "marcadores.json"), "w", encoding="utf-8") as fp:
        json.dump(meta, fp, indent=1, ensure_ascii=False)
    print(f"[2/3] {n} quadros em arte/render/{fx} ({n / fps:.2f}s a {fps:.2f} fps)")

    if not opt["export"]:
        print("[3/3] pulado (--sem-export)")
        return
    print("[3/3] exportando para o mod...")
    subprocess.run([sys.executable, os.path.join(RAIZ, "arte", "exportar.py"), fx], check=True)


if __name__ == "__main__":
    main()
