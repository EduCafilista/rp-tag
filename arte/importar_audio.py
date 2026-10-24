"""Corta QUALQUER audio e transforma na trilha de um aviso do mod.

Uso:
    python3 arte/importar_audio.py <arquivo> [opcoes]

    <arquivo>  .mp3 .wav .ogg .m4a .flac .opus ...

Opcoes:
    --inicio S    segundo onde o corte comeca            (padrao 0)
    --dur S       duracao do corte em segundos           (padrao 7.5)
    --id NOME     nome do som no mod                     (padrao egipcio_aviso)
    --pico DBFS   nivel de pico depois da normalizacao   (padrao -1.0)
    --fade-in S   fade de entrada                        (padrao 0.12)
    --fade-out S  fade de saida                          (padrao 0.60)
    --previa      tambem grava /home/user/previa/<id>.mp3

Grava src/main/resources/assets/rptag/sounds/<id>.ogg (Vorbis, mono/stereo
igual ao original) — e o arquivo que o Java toca.

Para achar o trecho certo de uma trilha longa:
    python3 arte/analisa_audio.py <arquivo>     # mostra energia por 0,5 s,
                                                # o build-up e os "drops"
"""
import os
import subprocess
import sys
import wave

import numpy as np

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SR = 44100


def ffmpeg():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def opcoes(argv):
    o = {"arquivo": None, "inicio": 0.0, "dur": 7.5, "id": "egipcio_aviso",
         "pico": -1.0, "fade_in": 0.12, "fade_out": 0.60, "previa": False}
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--inicio":
            o["inicio"] = float(argv[i + 1]); i += 2
        elif a == "--dur":
            o["dur"] = float(argv[i + 1]); i += 2
        elif a == "--id":
            o["id"] = argv[i + 1]; i += 2
        elif a == "--pico":
            o["pico"] = float(argv[i + 1]); i += 2
        elif a == "--fade-in":
            o["fade_in"] = float(argv[i + 1]); i += 2
        elif a == "--fade-out":
            o["fade_out"] = float(argv[i + 1]); i += 2
        elif a == "--previa":
            o["previa"] = True; i += 1
        else:
            o["arquivo"] = a; i += 1
    return o


def main():
    o = opcoes(sys.argv[1:])
    if not o["arquivo"]:
        sys.exit(__doc__)
    if not os.path.exists(o["arquivo"]):
        sys.exit("arquivo nao encontrado: " + o["arquivo"])

    ff = ffmpeg()
    tmp = "/tmp/_importar_audio.wav"
    # decodifica o trecho (resample 44,1 kHz, float 32) para mexer no numpy
    # pcm_s16le (o modulo wave do Python nao le float32)
    subprocess.run([ff, "-y", "-hide_banner", "-loglevel", "error",
                    "-ss", f"{o['inicio']}", "-t", f"{o['dur']}",
                    "-i", o["arquivo"], "-ar", str(SR), "-c:a", "pcm_s16le", tmp], check=True)

    w = wave.open(tmp)
    canais, largura, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
    raw = w.readframes(n)
    w.close()
    x = np.frombuffer(raw, dtype="<i2").reshape(-1, canais).astype(np.float64) / 32768.0
    if len(x) < sr * 0.3:
        sys.exit("o corte ficou vazio — confira --inicio/--dur")

    # fades (evita estalo no comeco e corte seco no fim)
    t = np.arange(len(x)) / sr
    env = np.ones(len(x))
    fi = int(o["fade_in"] * sr)
    fo = int(o["fade_out"] * sr)
    if fi > 0:
        env[:fi] = np.linspace(0, 1, fi) ** 1.5
    if fo > 0:
        env[-fo:] *= np.linspace(1, 0, fo) ** 1.5
    x *= env[:, None]

    # normaliza para o pico pedido (o mod toca em volume 0,85 — nao pode clipar)
    pico = np.abs(x).max()
    alvo = 10 ** (o["pico"] / 20.0)
    if pico > 0:
        x *= alvo / pico

    # limite suave (tanh) so se ainda houver pico fora da curva
    if np.abs(x).max() > 10 ** (-0.3 / 20):
        x = np.tanh(x * 1.05) / np.tanh(1.05)

    saida = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "sounds", o["id"] + ".ogg")
    os.makedirs(os.path.dirname(saida), exist_ok=True)
    tmp2 = "/tmp/_importar_audio_corte.wav"
    with wave.open(tmp2, "wb") as w2:
        w2.setnchannels(canais)
        w2.setsampwidth(2)
        w2.setframerate(sr)
        w2.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())

    subprocess.run([ff, "-y", "-hide_banner", "-loglevel", "error", "-i", tmp2,
                    "-c:a", "libvorbis", "-q:a", "6", saida], check=True)

    if o["previa"]:
        prev = os.path.join("/home/user/previa", o["id"] + ".mp3")
        os.makedirs(os.path.dirname(prev), exist_ok=True)
        subprocess.run([ff, "-y", "-hide_banner", "-loglevel", "error", "-i", tmp2,
                        "-c:a", "libmp3lame", "-b:a", "192k", prev], check=True)
        print("previa:", prev)

    rms = 20 * np.log10(np.sqrt((x ** 2).mean()) + 1e-12)
    print(f"ok: {saida} ({os.path.getsize(saida) / 1024:.0f} KB) — "
          f"{len(x) / sr:.2f}s, {canais} canal(is), pico {o['pico']:.1f} dBFS, RMS {rms:.1f} dBFS")


if __name__ == "__main__":
    main()
