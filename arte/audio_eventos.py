#!/usr/bin/env python3
"""(5.4.0) ÁUDIO DO EVENTO TERREMOTO / VAZIO / TRILHA / CAIXA DO BICHO.

Dois caminhos:

1. Os três áudios que o dono mandou (catbox) entram como estão, só normalizados:
     imports/sons/5pts9o.mp3 -> sounds/terremoto_colapso.ogg   (colapso, 14,7 s)
     imports/sons/h36ma4.mp3 -> sounds/vazio_apagar.ogg        (void, 2,25 s)
     imports/sons/z0r07u.mp3 -> sounds/trilha_deus.ogg         (trilha, 39,3 s)

2. Os que NÃO existiam são SINTETIZADOS aqui (numpy -> .ogg):
     caixa_musica   caixinha de música de criança, ~36 s, vai desafinando
     bicho_grito    o susto: trovão + berro estrondoso, 2,6 s
     rumble         tremor grave de terra rachando, 8 s (loop)

Uso:  python3 arte/audio_eventos.py            (tudo)
      python3 arte/audio_eventos.py --so-sintetizados
"""
import os
import subprocess
import sys
import wave

import numpy as np

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SONS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "sounds")
IMPORTS = "/home/user/imports/sons"
SR = 44100


def ff():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def gravar(nome, x, sr=SR):
    """x: float [-1..1], shape (n,) ou (n,2) -> sounds/<nome>.ogg (Vorbis)."""
    os.makedirs(SONS, exist_ok=True)
    x = np.asarray(x, dtype=np.float64)
    if x.ndim == 1:
        x = x[:, None]
    x = np.clip(x, -1, 1)
    # limite suave só pra garantir que não estala
    pico = np.abs(x).max()
    if pico > 0.999:
        x = np.tanh(x * 1.02) / np.tanh(1.02)
    tmp = "/tmp/_audio_eventos.wav"
    pcm = (x * 32767.0).astype("<i2")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(x.shape[1])
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())
    saida = os.path.join(SONS, nome + ".ogg")
    subprocess.run([ff(), "-y", "-hide_banner", "-loglevel", "error", "-i", tmp,
                    "-c:a", "libvorbis", "-q:a", "6", saida], check=True)
    seg = len(x) / sr
    print(f"  {nome}.ogg  {seg:5.2f} s  {os.path.getsize(saida)/1024:7.1f} KB")


# ---------------------------------------------------------------- utilidades
def seno(freq, dur, sr=SR, fase=0.0):
    t = np.arange(int(dur * sr)) / sr
    return np.sin(2 * np.pi * freq * t + fase)


def ruido(dur, sr=SR):
    return np.random.default_rng(7).standard_normal(int(dur * sr))


def passa_baixa(x, corte, sr=SR, ordem=2):
    """Filtro de 1ª/2ª ordem barato (RC digital) — bom o bastante pra rumble."""
    a = np.exp(-2 * np.pi * corte / sr)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    for _ in range(ordem - 1):
        acc = 0.0
        for i in range(len(y)):
            acc = (1 - a) * y[i] + a * acc
            y[i] = acc
    return y


def passa_alta(x, corte, sr=SR):
    return x - passa_baixa(x, corte, sr, 1)


def normalizar(x, pico_db=-1.5):
    m = np.abs(x).max()
    if m <= 0:
        return x
    return x * (10 ** (pico_db / 20.0) / m)


def aplicar_fade(x, fin=0.01, fout=0.05, sr=SR):
    n = len(x)
    estereo = x.ndim > 1
    a, b = int(fin * sr), int(fout * sr)
    if a > 0:
        env = np.linspace(0, 1, a)
        x[:a] *= env[:, None] if estereo else env
    if b > 0:
        env = np.linspace(1, 0, b)
        x[-b:] *= env[:, None] if estereo else env
    return x


def eco(x, atraso, ganho, repeticoes=3, sr=SR):
    """Reverb barato: repetições do sinal com atraso e ganho decrescente."""
    y = x.copy()
    d = int(atraso * sr)
    for k in range(1, repeticoes + 1):
        g = ganho ** k
        desloc = d * k
        if desloc < len(y):
            y[desloc:] += x[:len(x) - desloc] * g
    return y


# ---------------------------------------------------------------- 1. caixinha
def caixa_musica(segundos=36.0):
    """Caixinha de música de criança que vai AFROUXANDO e desafinando.

    Timbre de celesta (sine + 3ª + 5ª), ataque curto e cauda longa, melodia em
    lá menor (a que todo mundo acha que é de berço), andamento que desacelera
    e um "grilo" de manivela entre as frases.
    """
    sr = SR
    n = int(segundos * sr)
    out = np.zeros(n)
    # melodia (MIDI). frases curtas, descanso no fim de cada uma
    midi = [57, 60, 64, 62, 60, 57, 56, 57,      # Am
            57, 60, 64, 67, 64, 60, 57, 56,      # Am -> E
            53, 57, 60, 64, 62, 60, 57, 56,      # F -> E
            57, 60, 64, 62, 57, 56, 52, 45]      # descendo pro grave
    t0 = 0.6
    t = t0
    rng = np.random.default_rng(11)
    i = 0
    while t < segundos - 2.0:
        # andamento desacelera de 0,52 s/nota pra 0,78 s/nota
        passo = 0.52 + 0.26 * (t / segundos)
        nota = midi[i % len(midi)]
        # desafina mais no fim (caixa velha)
        desafino = (rng.standard_normal() * (2 + 8 * (t / segundos)))
        f = 440.0 * 2 ** ((nota - 69 + desafino / 100.0) / 12.0)
        dur = min(1.6, passo * 2.6)
        env = np.exp(-np.arange(int(dur * sr)) / sr * 3.4)
        env *= np.minimum(1.0, np.arange(int(dur * sr)) / (0.004 * sr))
        tom = (seno(f, dur) * 1.00
               + seno(f * 2.0, dur) * 0.32
               + seno(f * 3.01, dur) * 0.16
               + seno(f * 4.98, dur) * 0.07
               + seno(f * 7.1, dur) * 0.03) * env
        tom += rng.standard_normal(len(tom)) * 0.004 * env        # chiado da mola
        ini = int(t * sr)
        fim = min(n, ini + len(tom))
        out[ini:fim] += tom[:fim - ini] * 0.55
        t += passo
        # a cada 8 notas, a manivela chia
        if i % 8 == 7:
            c = ruido(0.55) * np.exp(-np.arange(int(0.55 * sr)) / sr * 6)
            c = passa_alta(c, 1600) * 0.05
            ini = int(t * sr)
            fim = min(n, ini + len(c))
            out[ini:fim] += c[:fim - ini]
            t += 0.45
        i += 1
    # a caixa para de repente (o bicho vai pular) — silêncio com um último "clac"
    out = eco(out, 0.19, 0.28, 3)
    out = aplicar_fade(normalizar(out, -3.0), 0.02, 0.35)
    return out


# ---------------------------------------------------------------- 2. o berro
def bicho_grito():
    """O susto: baque de trovão + berro distorcido + vidro/metal. ~2,6 s."""
    sr = SR
    dur = 2.6
    n = int(dur * sr)
    t = np.arange(n) / sr

    # (a) trovão: ruído grave com estalo inicial
    trov = ruido(dur)
    trov = passa_baixa(trov, 220, ordem=2)
    env_t = np.exp(-t * 1.1)
    trov = trov * env_t * 1.2

    # (b) sub-baque (o "soco" no peito)
    sub = np.sin(2 * np.pi * (70 * np.exp(-t * 3.2) + 28) * t) * np.exp(-t * 4.0) * 1.1

    # (c) berro: dente-de-serra que cai de afinação e satura
    f0 = 320 * np.exp(-t * 0.9) + 90
    fase = np.cumsum(2 * np.pi * f0 / sr)
    serra = 2 * ((fase / (2 * np.pi)) % 1.0) - 1
    serra = np.tanh(serra * 6.0)                     # distorção
    gritaria = serra * np.exp(-np.maximum(0, t - 0.05) * 1.3)
    gritaria *= (t > 0.04)                            # entra logo depois do baque

    # (d) grito agudo (a "criança") por cima, curto
    agudo = np.sin(2 * np.pi * (1500 * np.exp(-t * 1.6) + 500) * t) * np.exp(-t * 2.2) * 0.25

    # (e) metal: parciais inarmônicos batendo
    meta = sum(seno(f, dur) * a * np.exp(-t * (3.0 + i))
               for i, (f, a) in enumerate([(1830, 0.20), (2470, 0.16), (3110, 0.10)]))

    x = trov * 0.9 + sub * 0.9 + gritaria * 0.55 + agudo + meta * 0.5
    x = np.tanh(x * 1.6)                              # cola tudo (e satura)
    x = eco(x, 0.11, 0.25, 2)
    return aplicar_fade(normalizar(x, -1.2), 0.001, 0.25)


# ---------------------------------------------------------------- 3. rumble
def rumble_terremoto(segundos=8.0):
    """Tremor de terra rachando: ruído marrom modulado + baques distantes."""
    sr = SR
    n = int(segundos * sr)
    t = np.arange(n) / sr
    base = ruido(segundos)
    base = passa_baixa(base, 90, ordem=3)
    base /= max(1e-9, np.abs(base).max())
    # ondulação lenta (a terra "respira")
    mod = 0.55 + 0.45 * np.sin(2 * np.pi * 0.23 * t) ** 2
    x = base * mod
    # baques: 3 impactos graves espalhados
    for quando in (0.4, 3.1, 5.9):
        d = np.exp(-np.maximum(0, t - quando) * 5.0) * (t >= quando)
        x += np.sin(2 * np.pi * (48 + 30 * np.exp(-np.maximum(0, t - quando) * 3)) * t) * d * 0.5
        x += ruido(segundos) * passa_baixa(np.exp(-np.maximum(0, t - quando) * 9.0), 400) * 0.25
    # fades só nas pontas (é loop, então curtos)
    x = aplicar_fade(normalizar(x, -3.0), 0.05, 0.20)
    return x


# ---------------------------------------------------------------- imports
def importar_do_dono():
    """Os 3 mp3 do catbox -> ogg normalizado."""
    ffexe = ff()
    mapa = [("5pts9o", "terremoto_colapso", None),
            ("h36ma4", "vazio_apagar", None),
            ("z0r07u", "trilha_deus", None)]
    for origem, destino, _ in mapa:
        src = os.path.join(IMPORTS, origem + ".mp3")
        if not os.path.exists(src):
            print(f"  !! faltando {src}")
            continue
        tmp = "/tmp/_imp.wav"
        subprocess.run([ffexe, "-y", "-hide_banner", "-loglevel", "error",
                        "-i", src, "-ac", "2", "-ar", str(SR),
                        "-c:a", "pcm_s16le", tmp], check=True)
        w = wave.open(tmp)
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768.0
        canais = w.getnchannels()
        w.close()
        x = x.reshape(-1, canais)
        x = aplicar_fade(normalizar(x, -1.5), 0.01, 0.35)
        gravar(destino, x)


def main():
    so_sint = "--so-sintetizados" in sys.argv
    if not so_sint:
        print("originais do dono:")
        importar_do_dono()
    print("sintetizados:")
    gravar("caixa_musica", caixa_musica())
    gravar("bicho_grito", bicho_grito())
    gravar("rumble_terremoto", rumble_terremoto())
    print("pronto.")


if __name__ == "__main__":
    main()
