"""Trilha do aviso EGIPCIO — sintetizada do zero, sincronizada com a animacao.

Uso: python3 arte/audio_egipcio.py

Gera:
  src/main/resources/assets/rptag/sounds/egipcio_aviso.ogg   (o som do mod)
  /home/user/previa/egipcio_som.mp3                          (previa p/ ouvir)

A animacao (canvas do dono, 7,5 s) tem estes momentos:
  0,0-0,3  entra          (env)
  0,8-1,5  o olho abre    (op)
  1,0-1,3  clarao         (flash)
  1,5-2,6  asas abrem     (spread)
  1,6      cartucho       + pulso
  1,6 / 2,8 / 4,0 / 5,2 / 6,4  batidas (pulse = exp(-ph*6))
  2,2+     letras
  3,4      sino do templo
  7,0-7,5  sai            (env)

A trilha segue essa mesma linha do tempo: riser -> impacto no clarao, ney
(frauta de cana) em escala HIJAZ, tambor de moldura nas batidas, sistro nos
aneis de hieroglifos, coro quando o cartucho fecha e o sino do templo.
"""
import os
import struct
import subprocess
import sys
import wave

import numpy as np

SR = 44100
DUR = 7.6
N = int(SR * DUR)
T = np.arange(N) / SR
RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OGG = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "sounds", "egipcio_aviso.ogg")
MP3 = "/home/user/previa/egipcio_som.mp3"

rng = np.random.default_rng(20261024)


# ----------------------------------------------------------------- utilidades
def ss(a, b, x):
    u = np.clip((x - a) / (b - a), 0.0, 1.0)
    return u * u * (3 - 2 * u)


def env_ad(n, a, d, curva=2.0):
    """Ataque linear + decaimento exponencial, n amostras."""
    i = np.arange(n) / SR
    at = np.clip(i / max(a, 1e-4), 0, 1)
    de = np.exp(-np.maximum(0.0, i - a) * (3.0 / max(d, 1e-4)))
    return at * de ** (1.0 / curva)


def soma(buf, sinal, ini):
    """Soma `sinal` em `buf` comecando em `ini` segundos (corta no fim)."""
    a = int(ini * SR)
    if a >= len(buf):
        return
    n = min(len(sinal), len(buf) - a)
    buf[a:a + n] += sinal[:n]


def lowpass(x, corte, ordem=2):
    """Passa-baixa de 1 polo em cascata (barato e suficiente)."""
    a = np.exp(-2 * np.pi * corte / SR)
    y = x.copy()
    for _ in range(ordem):
        out = np.empty_like(y)
        acc = 0.0
        for i in range(len(y)):          # o laco e lento, mas roda uma vez so
            acc = (1 - a) * y[i] + a * acc
            out[i] = acc
        y = out
    return y


def lowpass_rapido(x, corte, ordem=2):
    """Passa-baixa por FFT (rapido, fase zero) — para camadas longas."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1.0 / np.sqrt(1.0 + (f / max(corte, 1.0)) ** (2 * ordem))
    return np.fft.irfft(X, len(x))


def bandpass_rapido(x, lo, hi, ordem=2):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = (1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1.0)) ** (2 * ordem))) * \
        (1.0 / np.sqrt(1.0 + (f / hi) ** (2 * ordem)))
    return np.fft.irfft(X * g, len(x))


def highpass_rapido(x, corte, ordem=2):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1.0 / np.sqrt(1.0 + (corte / np.maximum(f, 1.0)) ** (2 * ordem))
    return np.fft.irfft(X, len(x))


def reverb(x, decai=1.1, molhado=0.30, semente=7):
    """Reverberacao por convolucao com um impulso de ruido decaindo."""
    r = np.random.default_rng(semente)
    n = max(int(0.02 * SR), int(decai * SR))
    ir = r.standard_normal(n) * np.exp(-np.arange(n) / (0.32 * SR))
    _pd = min(n, int(0.012 * SR))
    ir[:_pd] *= np.linspace(0, 1, _pd)   # pre-delay suave
    # 3 reflexoes iniciais para nao soar "banheiro"
    for atras, ganho in ((0.021, 0.5), (0.037, 0.36), (0.053, 0.28)):
        ir[int(atras * SR)] += ganho
    ir /= np.abs(ir).sum() / 6.0
    m = len(x) + n - 1
    k = 1 << (m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, k) * np.fft.rfft(ir, k), k)[: len(x)]
    return x * (1 - molhado) + y * molhado * 2.2


# ------------------------------------------------------------------ instrumentos
def ney(freq, dur, vol=0.5, vibrato=5.4, sopro=0.055, bend=0.0):
    """Frauta de cana: senoide + 3o harmonico + ruido de sopro, com vibrato."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    f = np.full(n, freq, dtype=float)
    if bend:
        f *= 1.0 + bend * np.exp(-i / 0.055)          # ataque "deslizando" na nota
    # vibrato que entra devagar (como quem sopra)
    f *= 1.0 + 0.006 * np.sin(2 * np.pi * vibrato * i) * np.clip(i / 0.25, 0, 1)
    fase = 2 * np.pi * np.cumsum(f) / SR
    som = np.sin(fase) + 0.22 * np.sin(2 * fase) + 0.07 * np.sin(3 * fase)
    ar = env_ad(n, 0.07, dur * 0.9, 1.4)
    ar *= np.clip((dur - i) / 0.18, 0, 1)              # solta o ar no fim
    # sopro: ruido filtrado na faixa da frauta
    b = bandpass_rapido(rng.standard_normal(n), freq * 0.8, freq * 4.0) * sopro * ar
    return (som * 0.32 + b) * ar * vol


def qanun(freq, dur=1.2, vol=0.35, brilho=0.5):
    """Corda pulsada (alaúde/qanun): Karplus-Strong com brilho."""
    n = int(dur * SR)
    p = max(2, int(SR / freq))
    buf = rng.standard_normal(p)
    buf = 0.5 * buf + 0.5 * np.roll(buf, 1)
    buf *= brilho + (1 - brilho) * np.linspace(1, 0.6, p)
    out = np.empty(n)
    for i in range(n):
        out[i] = buf[i % p]
        buf[i % p] = 0.996 * 0.5 * (buf[i % p] + buf[(i + 1) % p])
    return out * env_ad(n, 0.004, dur, 2.2) * vol


def doum(vol=0.6, dur=0.5, f0=96.0):
    """Tambor de moldura — o grave ('doum')."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    f = f0 * (0.55 + 0.45 * np.exp(-i / 0.045))
    corpo = np.sin(2 * np.pi * np.cumsum(f) / SR)
    pele = bandpass_rapido(rng.standard_normal(n), 60, 420) * np.exp(-i / 0.035) * 0.4
    ar = env_ad(n, 0.003, dur * 0.55, 2.4)
    return (corpo * 0.9 + pele) * ar * vol


def tek(vol=0.32, dur=0.16):
    """A batida seca da borda ('tek')."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    corpo = np.sin(2 * np.pi * 420 * i) * np.exp(-i / 0.018) * 0.4
    pele = bandpass_rapido(rng.standard_normal(n), 1600, 6200) * np.exp(-i / 0.035)
    return (corpo + pele * 1.2) * vol


def sistro(freq=2600.0, dur=0.9, vol=0.22):
    """Sistro/chantal de metal: FM com decaimento rapido e brilho longo."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    mod = np.sin(2 * np.pi * freq * 1.41 * i) * 2.4 * np.exp(-i / 0.10)
    som = np.sin(2 * np.pi * freq * i + mod)
    som += 0.35 * np.sin(2 * np.pi * freq * 2.76 * i + mod * 0.5)
    return som * env_ad(n, 0.002, dur * 0.45, 2.0) * vol


def sino(freq=196.0, dur=3.2, vol=0.34):
    """Sino do templo: parciais inarmonicos (o metal grande)."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    som = np.zeros(n)
    for k, (mult, g) in enumerate(((1, 1.0), (2.01, 0.5), (2.98, 0.34), (4.21, 0.2), (5.43, 0.12))):
        som += g * np.sin(2 * np.pi * freq * mult * i + k) * np.exp(-i / (0.9 / (1 + k * 0.5)))
    return som * env_ad(n, 0.004, dur, 1.6) * vol


def coro(freq, dur, vol=0.3, ataque=0.75):
    """Pad vocal: 3 vozes levemente desafinadas com formante de 'o'."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    som = np.zeros(n)
    for d in (-0.004, 0.0, 0.0045):
        f = freq * (1 + d)
        fase = 2 * np.pi * f * i + 0.35 * np.sin(2 * np.pi * 0.27 * i + d * 900)
        dente = np.zeros(n)
        for h in range(1, 14):
            dente += (1.0 / h) * np.sin(h * fase)
        som += dente
    som /= 3.0
    # formantes de vogal (a magia do "coro")
    f1 = bandpass_rapido(som, 420, 900) * 1.0
    f2 = bandpass_rapido(som, 950, 1500) * 0.55
    som = lowpass_rapido(f1 + f2, 2600)
    ar = ss(0, ataque, i) * np.clip((dur - i) / 0.9, 0, 1)
    return som * ar * vol


def impacto(vol=0.9):
    """Queda de grave + estrondo de metal (o clarao)."""
    n = int(1.5 * SR)
    i = np.arange(n) / SR
    f = 92 * (0.28 + 0.72 * np.exp(-i / 0.16))
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-i / 0.42)
    estouro = bandpass_rapido(rng.standard_normal(n), 90, 2200) * np.exp(-i / 0.075) * 0.8
    metal = sistro(1750, 1.4, 0.5)[:n] if n <= int(1.4 * SR) else None
    som = sub + estouro
    if metal is not None:
        som[: len(metal)] += metal * 0.5
    return som * vol


def riser(dur=1.0, vol=0.26):
    """Sobe o ar ate o impacto."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    ruido = bandpass_rapido(rng.standard_normal(n), 250, 5200)
    env = (i / dur) ** 2.6
    tom = np.sin(2 * np.pi * np.cumsum(120 * (1 + 3.2 * (i / dur) ** 2)) / SR) * 0.25
    return (ruido * 0.5 + tom) * env * vol


def whoosh(dur=1.2, vol=0.20):
    """As asas abrindo: ar que passa e volta."""
    n = int(dur * SR)
    i = np.arange(n) / SR
    env = np.sin(np.pi * np.clip(i / dur, 0, 1)) ** 1.5
    ruido = rng.standard_normal(n)
    partes = []
    for lo, hi in ((300, 1400), (900, 3800), (2200, 6500)):
        partes.append(bandpass_rapido(ruido, lo, hi))
    som = (partes[0] * 0.5 + partes[1] * 0.45 + partes[2] * 0.18)
    return som * env * vol


# ------------------------------------------------------------------- a trilha
def main():
    # escala HIJAZ em Re (o "som egipcio" de verdade: 2a menor + 3a maior)
    D2, A2, D3, F3s, G3 = 73.42, 110.00, 146.83, 185.00, 196.00
    D4, Eb4, F4s, G4, A4, Bb4, C5, D5 = 293.66, 311.13, 369.99, 392.00, 440.00, 466.16, 523.25, 587.33

    # ---- camadas (seco, antes da reverb)
    vento = np.zeros(N)
    grave = np.zeros(N)
    ney_t = np.zeros(N)
    cordas = np.zeros(N)
    tambor = np.zeros(N)
    metal = np.zeros(N)
    pad = np.zeros(N)
    fx = np.zeros(N)

    # 1) vento de areia: ruido filtrado, so sobe e desce devagar
    i = T
    ar_vento = 0.55 + 0.45 * np.sin(2 * np.pi * 0.09 * i + 0.6)
    ar_vento *= ss(0.0, 0.8, i) * (1 - ss(6.6, 7.4, i))
    vento += bandpass_rapido(rng.standard_normal(N), 220, 1500) * ar_vento * 0.030

    # 2) pedal grave (Re + La): entra com o olho abrindo
    ar_g = ss(0.15, 1.5, i) * (1 - ss(6.4, 7.35, i)) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.13 * i))
    for f, g in ((D2, 1.0), (A2, 0.62), (D3, 0.40)):
        dente = sum((1.0 / h) * np.sin(2 * np.pi * f * h * i + h * 1.7) for h in range(1, 7))
        grave += dente * g * ar_g * 0.055
    grave = lowpass_rapido(grave, 900)

    # 3) riser -> impacto no clarao (1,0 s)
    soma(fx, riser(1.0, 0.30), 0.02)
    soma(fx, impacto(0.95), 0.98)
    # segundo golpe, quando o cartucho fecha (2,45 s)
    soma(fx, impacto(0.62), 2.45)
    soma(fx, sistro(3400, 1.1, 0.16), 2.45)

    # 4) ney — a melodia
    melodia = [
        (0.86, A4, 0.36, 0.55, -0.05),
        (1.20, G4, 0.30, 0.30, 0.0),
        (1.50, F4s, 0.62, 0.62, -0.03),
        (2.10, Eb4, 0.38, 0.40, 0.0),
        (2.50, D4, 0.58, 0.72, -0.04),
        (3.22, C5, 0.32, 0.38, 0.0),
        (3.56, Bb4, 0.32, 0.34, 0.0),
        (3.88, A4, 0.36, 0.40, 0.0),
        (4.20, G4, 0.55, 0.46, -0.03),
        (4.78, F4s, 0.32, 0.36, 0.0),
        (5.08, A4, 0.32, 0.40, 0.0),
        (5.38, C5, 0.34, 0.44, 0.0),
        (5.72, D5, 1.45, 0.62, 0.0),
    ]
    for t0, f, d, v, b in melodia:
        soma(ney_t, ney(f, d, v * 1.45, bend=b), t0)
    # o ney ganha um eco curto (dois toques) => caverna do templo
    ney_t = ney_t + 0.22 * np.concatenate([np.zeros(int(0.19 * SR)), ney_t[: -int(0.19 * SR)]])

    # 5) cordas pulsadas (alaúde/qanun): enfeite e acordes
    for t0, f, v in ((1.62, D4, 0.26), (1.80, F4s, 0.20), (2.02, A4, 0.20), (2.24, D5, 0.18)):
        soma(cordas, qanun(f, 1.3, v), t0)
    for j, (f, v) in enumerate(((D4, 0.30), (F4s, 0.24), (A4, 0.22), (D5, 0.20))):
        soma(cordas, qanun(f, 1.6, v), 2.47 + j * 0.06)
    for j, (f, v) in enumerate(((D3, 0.26), (A2 * 2, 0.22), (D4, 0.24), (F4s, 0.18))):
        soma(cordas, qanun(f, 2.2, v, 0.42), 5.62 + j * 0.045)

    # 6) tambor de moldura — nas batidas da animacao (1,6 / 2,8 / 4,0 / 5,2 / 6,4)
    for t0, v in ((1.60, 0.42), (2.80, 0.46), (4.00, 0.42), (5.20, 0.40), (6.40, 0.26)):
        soma(tambor, doum(v, 0.5), t0)
        if t0 < 6.0:
            soma(tambor, tek(0.22), t0 + 0.21)
            soma(tambor, tek(0.18), t0 + 0.42)
    soma(tambor, doum(0.85, 0.75), 2.45)     # com o impacto do cartucho
    soma(tambor, doum(0.80, 0.8), 5.62)      # o golpe final
    soma(tambor, doum(0.34, 0.4, 120.0), 3.40)
    for t0 in (3.60, 3.80, 4.35, 4.55, 4.80, 4.95):
        soma(tambor, tek(0.15), t0)

    # 7) sistro nos aneis de hieroglifos (1,2 -> 2,2) e no sino
    for j, t0 in enumerate((1.20, 1.45, 1.70, 1.95, 2.20)):
        soma(metal, sistro(2400 + 170 * j, 0.8, 0.13), t0)
    soma(metal, sino(174.6, 3.4, 0.30), 3.40)   # o sino do templo
    soma(metal, sistro(3200, 1.6, 0.10), 5.62)

    # 8) asas: o ar abrindo
    soma(fx, whoosh(1.2, 0.26), 1.50)

    # 9) coro quando o cartucho fecha — segura ate o fim
    for f, v in ((D3, 0.30), (A2 * 2, 0.24), (D4, 0.26), (F4s, 0.16)):
        soma(pad, coro(f, 4.3, v, 0.7), 2.42)
    # "uivo" do coro no golpe final
    soma(pad, coro(D5 * 0.5, 1.8, 0.16, 0.25), 5.62)

    # ------------------------------------------------------------- mistura
    seco = (vento * 0.75 + grave * 1.0 + ney_t * 1.25 + cordas * 1.15 +
            tambor * 1.15 + metal * 1.0 + pad * 0.85 + fx * 1.05)
    # reverb por familia (a percussao vai mais seca)
    env1 = reverb(vento * 0.5 + grave + pad, 1.5, 0.30, 11)
    env2 = reverb(ney_t + cordas + metal, 1.2, 0.28, 23)
    env3 = reverb(fx + tambor, 0.9, 0.14, 37)
    mono = env1 + env2 + env3
    # tira o excesso de grave que come a mixagem (o filme tem sub, nao sujeira)
    mono = highpass_rapido(mono, 34)
    # tira um pouco do "barro" do vento (o ney e o tambor dominam 200-700 Hz)
    _barro = bandpass_rapido(mono, 180, 700)
    mono = mono - _barro * 0.22
    mono = mono * (1 - ss(6.55, 7.5, T))        # sai junto com o video

    # estereo: leve abertura (ney/cordas/sistro um pouco abertos)
    esq = mono + 0.10 * (ney_t + cordas) * 0.5 + 0.14 * np.roll(metal, int(0.004 * SR))
    dir_ = mono - 0.10 * (ney_t + cordas) * 0.5 - 0.14 * np.roll(metal, int(0.004 * SR))
    st = np.stack([esq, dir_], axis=1)

    # normaliza e limita
    pico = np.abs(st).max()
    st = st / pico * 0.92
    st = np.tanh(st * 1.08) / np.tanh(1.08)
    st *= 0.94

    wav = "/tmp/egipcio_aviso.wav"
    with wave.open(wav, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((st * 32767).astype("<i2").tobytes())

    import imageio_ffmpeg
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    os.makedirs(os.path.dirname(OGG), exist_ok=True)
    os.makedirs(os.path.dirname(MP3), exist_ok=True)
    subprocess.run([ff, "-y", "-hide_banner", "-loglevel", "error", "-i", wav,
                    "-c:a", "libvorbis", "-q:a", "5", OGG], check=True)
    subprocess.run([ff, "-y", "-hide_banner", "-loglevel", "error", "-i", wav,
                    "-c:a", "libmp3lame", "-b:a", "192k", MP3], check=True)

    pico_db = 20 * np.log10(max(1e-9, np.abs(st).max()))
    rms_db = 20 * np.log10(max(1e-9, np.sqrt((st ** 2).mean())))
    print(f"ok: {OGG} ({os.path.getsize(OGG) / 1024:.0f} KB) e {MP3}")
    print(f"pico {pico_db:.1f} dBFS · RMS {rms_db:.1f} dBFS · {DUR}s")


if __name__ == "__main__":
    main()
