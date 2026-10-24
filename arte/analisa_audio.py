"""Analisa um audio para achar o MELHOR TRECHO de trilha do aviso.

Uso:
    python3 arte/analisa_audio.py <arquivo> [--janela 7.5]

Mostra:
  - duracao e formato
  - energia (RMS) a cada 0,5 s, com barra  -> da pra ver a estrutura
  - os "drops" (saltos de energia + grave) -> o momento de impacto
  - o melhor trecho de <janela> segundos: o que tem mais energia E um impacto
    no comeco (para casar com o clarao do aviso, que acontece em 1,0 s)

Depois e so cortar:
    python3 arte/importar_audio.py <arquivo> --inicio <S> --dur <janela> --id <nome>
"""
import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100


def ffmpeg():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def decodificar(arquivo, tmp="/tmp/_analisa_audio.wav"):
    subprocess.run([ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
                    "-i", arquivo, "-ar", str(SR), "-ac", "1",
                    "-c:a", "pcm_s16le", tmp], check=True)
    w = wave.open(tmp)
    x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768.0
    dur = w.getnframes() / w.getframerate()
    w.close()
    return x, dur


def db(v):
    return 20 * np.log10(max(v, 1e-9))


def energia(x, sr, t0, t1, lo=None, hi=None):
    seg = x[int(t0 * sr):int(t1 * sr)]
    if len(seg) < 64:
        return -120.0
    if lo is None:
        return db(np.sqrt((seg ** 2).mean()))
    S = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    f = np.fft.rfftfreq(len(seg), 1 / sr)
    return db(np.sqrt((S[(f >= lo) & (f < hi)] ** 2).mean()))


def main():
    argv = sys.argv[1:]
    if not argv:
        sys.exit(__doc__)
    arquivo = argv[0]
    janela = float(argv[argv.index("--janela") + 1]) if "--janela" in argv else 7.5
    x, dur = decodificar(arquivo)
    print(f"{os.path.basename(arquivo)} — {dur:.2f}s\n")

    print("== energia a cada 0,5 s (rms / grave <150 Hz) ==")
    t = 0.0
    while t < dur - 0.25:
        r = energia(x, SR, t, t + 0.5)
        lo = energia(x, SR, t, t + 0.5, 20, 150)
        print(f"{t:6.1f}s  {r:6.1f} dB  grave {lo:6.1f}  {'#' * int(max(0, r + 45))}")
        t += 0.5

    print("\n== impactos (maior salto de energia em 0,2 s) ==")
    saltos = []
    t = 0.2
    while t < dur - janela - 0.2:
        a = energia(x, SR, t - 0.2, t)
        b = energia(x, SR, t, t + 0.2)
        saltos.append((b - a, t, b))
        t += 0.1
    saltos.sort(reverse=True)
    for d, tt, r in saltos[:10]:
        print(f"t={tt:6.2f}s  salto {d:5.1f} dB  rms {r:6.1f} dB")

    print(f"\n== melhores trechos de {janela:g}s (energia media + impacto no 1o segundo) ==")
    melhor = []
    t = 0.0
    while t < dur - janela:
        en = np.mean([energia(x, SR, t + k * 0.5, t + k * 0.5 + 0.5)
                      for k in range(int(janela / 0.5))])
        # impacto entre 0,6 s e 1,4 s (o clarao do aviso acontece em 1,0 s)
        imp = max(s - a for s, a, _ in saltos if t + 0.6 <= _ <= t + 1.4) if False else \
            max([(energia(x, SR, u + 0.2, u + 0.4) - energia(x, SR, u - 0.2, u))
                 for u in np.arange(t + 0.6, t + 1.4, 0.1)] or [-99])
        melhor.append((en + 0.5 * imp, t, en, imp))
        t += 0.25
    melhor.sort(reverse=True)
    for nota, tt, en, imp in melhor[:6]:
        print(f"inicio {tt:6.2f}s -> fim {tt + janela:6.2f}s   energia {en:6.1f}  "
              f"impacto em ~{tt + 1.0:6.2f}s: {imp:5.1f}  (nota {nota:6.1f})")

    print("\ncorte sugerido:")
    n, tt, _, _ = melhor[0]
    print(f"  python3 arte/importar_audio.py {os.path.basename(arquivo)} --inicio {tt:.2f} --dur {janela:g} --id egipcio_aviso")


if __name__ == "__main__":
    main()
