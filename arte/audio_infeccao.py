#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
(5.26.0) AS TRILHAS DA INFECÇÃO — pedido do dono:

    "o avisar Infecçao merece um som mais irado, algo que dê para mostrar o que se está
     passando na animação, o infecção2 tbm merece um novo som, isso ta muito fraco sem som"

Gera dois .ogg (Vorbis, 44,1 kHz, estéreo) que contam a MESMA história que a tela do
aviso conta, batendo com a linha do tempo do FX:

  infeccao_aviso.ogg   (10 s) — estilo 38  (InfeccaoAvisoFx)
     0,0–2,5  o coração da coisa começa a bater; o vinho pulsa
     1,5–5,0  as VEIAS crescem (estalo úmido, um tecido esticando)
     3,0–6,5  o OLHO abre (tom agudo que sobe) e a SANIDADE começa a cair (dissônancia)
     4,0–7,0  sussurros ("ela promete força...")
     6,5–8,0  o surto: grito distorcido subindo
     8,2      APAGÃO (o baque grave que corta a tela)
     8,6–10,0 sobra o coração batendo devagar e a poeira

  infeccao2_aviso.ogg  (10 s) — estilo 39  (Infeccao2AvisoFx)
     a variante "de Minecraft": slime espremendo, o Warden farejando, esporos
     estourando, o mesmo coração e um baque final de blocos.

Roda:  python3 arte/audio_infeccao.py
Saída: src/main/resources/assets/rptag/sounds/infeccao_aviso.ogg
       src/main/resources/assets/rptag/sounds/infeccao2_aviso.ogg
"""
import os

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
DUR = 10.0
N = int(SR * DUR)
T = np.arange(N) / SR
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAIDA = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag", "sounds")

rng = np.random.default_rng(20260903)


# ------------------------------------------------------------------ utilidades
def env_adsr(n, ataque=0.005, decai=0.2, sustain=0.0, solta=0.1):
    """envelope simples (ataque/decay/sustain/release) em amostras."""
    e = np.zeros(n)
    a = max(1, int(ataque * SR))
    d = max(1, int(decai * SR))
    r = max(1, int(solta * SR))
    a = min(a, n)
    e[:a] = np.linspace(0, 1, a)
    d = min(d, n - a)
    if d > 0:
        e[a:a + d] = np.linspace(1, sustain, d)
    resto = n - a - d
    if resto > 0:
        e[a + d:] = sustain
    r = min(r, n)
    e[n - r:] *= np.linspace(1, 0, r)
    return e


def env_exp(n, tau):
    """decaimento exponencial (percussão, estalos)."""
    return np.exp(-np.arange(n) / (tau * SR))


def colocar(buf, som, inicio, ganho=1.0, pan=0.0):
    """soma um som (mono) no buffer estéreo (N,2) na posição de tempo 'inicio' (s)."""
    i = int(inicio * SR)
    if i >= N:
        return
    s = som[: N - i]
    gl = ganho * min(1.0, 1.0 - pan)
    gr = ganho * min(1.0, 1.0 + pan)
    buf[i:i + len(s), 0] += s * gl
    buf[i:i + len(s), 1] += s * gr


def ruido(n):
    return rng.standard_normal(n)


def bp(x, lo, hi, ordem=2):
    lo = max(20.0, lo)
    hi = min(SR * 0.45, hi)
    b, a = signal.butter(ordem, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return signal.lfilter(b, a, x)


def lp(x, f, ordem=2):
    b, a = signal.butter(ordem, min(f, SR * 0.45) / (SR / 2), btype="low")
    return signal.lfilter(b, a, x)


def hp(x, f, ordem=2):
    b, a = signal.butter(ordem, max(20.0, f) / (SR / 2), btype="high")
    return signal.lfilter(b, a, x)


def varredura(x, f_ini, f_fim, largura=800.0):
    """passa-banda cujo centro anda de f_ini a f_fim (tecido esticando)."""
    saida = np.zeros_like(x)
    blocos = 64
    tam = len(x) // blocos
    for i in range(blocos):
        ini = i * tam
        fim = ini + tam if i < blocos - 1 else len(x)
        u = i / max(1, blocos - 1)
        c = f_ini + (f_fim - f_ini) * u
        saida[ini:fim] = bp(x[ini:fim], max(60.0, c - largura / 2), c + largura / 2)
    return saida


def reverb(x, atrasos=((0.031, 0.35), (0.047, 0.28), (0.071, 0.22), (0.113, 0.16))):
    """eco curto em paralelo (dá espaço sem precisar de IR)."""
    y = x.copy()
    for d, g in atrasos:
        i = int(d * SR)
        y[i:] += x[:-i] * g
    return y


def normalizar(x, pico=0.92):
    m = np.max(np.abs(x))
    return x if m < 1e-9 else x / m * pico


def limitar(x):
    """cola suave no teto (tanh) — nada de estouro no alto-falante."""
    return np.tanh(x * 1.05) * 0.95


# ------------------------------------------------------------------ peças
def batida_coracao(grave=52.0, forca=1.0):
    """'tum-tá' do coração: senoide grave com queda rápida + um estalo úmido."""
    n = int(0.55 * SR)
    t = np.arange(n) / SR
    f = grave * (1.0 + 0.8 * np.exp(-t * 40))          # o tom cai (o 'tum')
    fase = 2 * np.pi * np.cumsum(f) / SR
    corpo = np.sin(fase) * env_exp(n, 0.09)
    corpo += 0.35 * np.sin(fase * 2.02) * env_exp(n, 0.05)
    estalo = bp(ruido(n), 90, 380) * env_exp(n, 0.02) * 0.5
    som = (corpo + estalo) * forca
    # o 'tá' (segunda batida, mais fraca)
    n2 = int(0.35 * SR)
    i2 = int(0.17 * SR)
    corpo2 = np.sin(np.cumsum(f * 1.1) / SR * 2 * np.pi)[:n2] * env_exp(n2, 0.06) * 0.55
    som[i2:i2 + n2] += corpo2[: max(0, min(n2, n - i2))]
    return som


def tecido_esticando(dur=0.7, f_ini=500, f_fim=1800):
    n = int(dur * SR)
    x = ruido(n)
    y = varredura(x, f_ini, f_fim, largura=900)
    y *= env_adsr(n, ataque=0.06, decai=dur * 0.8, sustain=0.0, solta=0.1)
    # um "crec" por cima
    y += bp(ruido(n), 1500, 4200) * env_exp(n, 0.03) * 0.4
    return y


def sussurro(dur=1.4, ritmo=5.0):
    n = int(dur * SR)
    x = ruido(n)
    y = bp(x, 700, 2600)
    mod = 0.5 + 0.5 * np.sin(2 * np.pi * ritmo * np.arange(n) / SR + rng.random() * 6)
    y *= mod * env_adsr(n, ataque=0.25, decai=dur * 0.6, sustain=0.0, solta=0.3)
    return y * 0.6


def grito_distorcido(dur=1.5, f_ini=180.0, f_fim=900.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f_ini * (f_fim / f_ini) ** (t / dur)
    fase = 2 * np.pi * np.cumsum(f) / SR
    dente = signal.sawtooth(fase)                      # serra = voz rasgada
    dente += 0.5 * signal.sawtooth(fase * 1.98 + 0.6)
    dente += 0.3 * signal.sawtooth(fase * 3.01 + 1.2)
    dente = np.tanh(dente * 2.6)                       # saturação
    dente = lp(dente, 5200)
    env = env_adsr(n, ataque=0.05, decai=dur * 0.35, sustain=0.55, solta=0.5)
    vibrato = 1 + 0.03 * np.sin(2 * np.pi * 6.5 * t)
    return dente * env * vibrato * 0.5


def apagao(grave=44.0):
    """o baque do apagão: sub descendo + estrondo + silêncio logo depois."""
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    f = grave * 1.9 * np.exp(-t * 2.2) + grave * 0.7
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 0.35)
    estrondo = lp(ruido(n), 900) * env_exp(n, 0.12) * 0.7
    return (sub + estrondo) * 1.1


def slime(dur=0.5, f_ini=420.0, f_fim=150.0):
    """o 'squish' molhado do slime (a cara do Minecraft)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f_ini * (f_fim / f_ini) ** (t / dur)
    boing = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 0.12)
    molhado = bp(ruido(n), 250, 2200) * env_exp(n, 0.08)
    return (boing * 0.8 + molhado * 0.8)


def grunhido_warden(dur=2.2, f=48.0):
    """o rosnado do Warden: sub-harmônico + formante de bicho grande."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    base = np.sin(2 * np.pi * f * t + 0.4 * np.sin(2 * np.pi * 0.7 * t))
    base += 0.6 * np.sin(2 * np.pi * f * 0.5 * t)
    base += 0.3 * np.sin(2 * np.pi * f * 1.5 * t)
    arranhado = bp(ruido(n), 300, 1100) * 0.7
    env = env_adsr(n, ataque=0.35, decai=dur * 0.5, sustain=0.4, solta=0.6)
    return np.tanh((base * 0.8 + arranhado) * env * 1.6) * 0.55


def esporo_estoura(dur=0.25):
    n = int(dur * SR)
    return (bp(ruido(n), 1200, 5000) * env_exp(n, 0.02)
            + np.sin(2 * np.pi * 900 * np.arange(n) / SR) * env_exp(n, 0.03) * 0.4)


def estalo_bloco(dur=0.35):
    """o 'toc' seco de bloco (Minecraft gosta)."""
    n = int(dur * SR)
    x = bp(ruido(n), 600, 2400) * env_exp(n, 0.03)
    return x


def drone(dur, freqs=(55.0, 82.4, 110.0), ganho=0.35, tremor=0.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    som = np.zeros(n)
    for i, f in enumerate(freqs):
        som += np.sin(2 * np.pi * f * t + i) / (i + 1.4)
    if tremor > 0:
        som *= 1 + tremor * np.sin(2 * np.pi * 1.15 * t)
    som *= env_adsr(n, ataque=1.2, decai=1.0, sustain=0.85, solta=2.0)
    return som * ganho


# ------------------------------------------------------------------ INFECÇÃO (38)
def trilha_infeccao():
    buf = np.zeros((N, 2))

    # 1) o coração: acelera de 72 pra 150 bpm enquanto a coisa toma conta
    t = 0.55
    while t < 9.2:
        u = min(1.0, t / 8.0)
        intervalo = 0.83 - 0.42 * u                     # acelera
        colocar(buf, batida_coracao(52 + 6 * u, 0.95), t, ganho=0.85 - 0.25 * u)
        t += intervalo

    # 2) o drone do vinho, pulsando junto
    colocar(buf, drone(DUR, (55.0, 82.4, 110.0), 0.32, tremor=0.18), 0.0, ganho=0.55)
    colocar(buf, drone(7.0, (73.4, 110.0, 146.8), 0.22), 1.0, ganho=0.5)

    # 3) as VEIAS crescem (do canto pro centro, como no FX)
    for k, ini in enumerate((1.5, 2.3, 3.1, 3.9, 4.6, 5.3)):
        pan = -0.7 + 0.28 * k
        colocar(buf, tecido_esticando(0.7, 420 + 90 * k, 1500 + 220 * k), ini,
                ganho=0.5, pan=pan)

    # 4) o OLHO abre: um tom fino que sobe e desafina
    n = int(4.5 * SR)
    t = np.arange(n) / SR
    agudo = np.sin(2 * np.pi * (1180 + 240 * t) * t) * 0.16
    agudo += np.sin(2 * np.pi * (1245 + 250 * t) * t) * 0.11        # batimento dissonante
    agudo *= env_adsr(n, ataque=1.6, decai=1.2, sustain=0.5, solta=1.5)
    colocar(buf, agudo, 2.8, ganho=0.55)

    # 5) os sussurros (a "promessa")
    for ini, pan in ((4.0, -0.5), (5.1, 0.55), (6.2, -0.2)):
        colocar(buf, sussurro(1.5, 5.0 + rng.random()), ini, ganho=0.5, pan=pan)

    # 6) o SURTO
    colocar(buf, grito_distorcido(1.6, 200, 780), 6.4, ganho=0.55)
    colocar(buf, grito_distorcido(1.0, 300, 1200), 6.9, ganho=0.35, pan=0.35)

    # 7) o APAGÃO e a volta do coração devagar
    colocar(buf, apagao(44.0), 8.15, ganho=0.9)
    colocar(buf, batida_coracao(48, 0.9), 9.0, ganho=0.7)
    colocar(buf, batida_coracao(46, 0.7), 9.55, ganho=0.5)

    # 8) um chiado de fundo (o ar da sala)
    colocar(buf, hp(ruido(N), 3000) * 0.05, 0.0, ganho=0.35)

    return buf


# ------------------------------------------------------------------ INFECÇÃO 2 (39)
def trilha_infeccao2():
    buf = np.zeros((N, 2))

    # 1) o Warden farejando (o bicho da coisa)
    colocar(buf, grunhido_warden(2.6, 46.0), 0.15, ganho=0.85)
    colocar(buf, grunhido_warden(2.0, 40.0), 5.4, ganho=0.6, pan=-0.3)

    # 2) coração (mais "de caverna": grave e seco)
    t = 0.9
    while t < 9.3:
        u = min(1.0, t / 8.5)
        colocar(buf, batida_coracao(44 + 5 * u, 1.0), t, ganho=0.8 - 0.2 * u)
        t += 0.88 - 0.34 * u

    # 3) slime espremendo (o lado Minecraft da coisa)
    for ini, f0, f1, pan in ((1.2, 380, 130, -0.4), (2.6, 300, 110, 0.45),
                             (4.1, 460, 150, -0.2), (6.0, 340, 120, 0.3),
                             (7.4, 260, 95, -0.35)):
        colocar(buf, slime(0.55, f0, f1), ini, ganho=0.55, pan=pan)

    # 4) esporos estourando (chuva de pipocas molhadas)
    for _ in range(26):
        ini = rng.uniform(1.0, 8.6)
        colocar(buf, esporo_estoura(0.18 + rng.random() * 0.12), ini,
                ganho=0.18 + rng.random() * 0.16, pan=rng.uniform(-0.8, 0.8))

    # 5) um zumbido de caverna subindo
    n = int(7.5 * SR)
    t = np.arange(n) / SR
    f = 62.0 * (1.0 + 0.35 * t / 7.5)
    zumbido = (np.sin(2 * np.pi * np.cumsum(f) / SR)
               + 0.4 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR))
    zumbido *= env_adsr(n, ataque=2.2, decai=1.5, sustain=0.6, solta=2.0)
    colocar(buf, zumbido, 0.8, ganho=0.3)

    # 6) o baque final de blocos + o último espremido
    colocar(buf, estalo_bloco(0.5), 8.1, ganho=0.75, pan=-0.15)
    colocar(buf, estalo_bloco(0.5), 8.16, ganho=0.7, pan=0.2)
    colocar(buf, apagao(40.0), 8.1, ganho=0.75)
    colocar(buf, slime(0.8, 300, 80), 8.7, ganho=0.6)

    # 7) o rabo: o coração ainda bate uma vez e o ar vai morrendo (não cortar seco)
    colocar(buf, batida_coracao(42, 0.8), 9.2, ganho=0.55)
    colocar(buf, drone(2.2, (41.2, 61.7), 0.3), 8.6, ganho=0.5)

    # 8) chiado de caverna
    colocar(buf, bp(ruido(N), 200, 1400) * 0.05, 0.0, ganho=0.3)

    return buf


# ------------------------------------------------------------------ saída
def gravar(buf, nome):
    os.makedirs(SAIDA, exist_ok=True)
    esq = reverb(buf[:, 0])
    dir_ = reverb(buf[:, 1])
    est = np.stack([esq, dir_], axis=1)
    est = normalizar(est, 0.9)
    est = limitar(est)
    # fade de 30 ms nas pontas (sem estalo no começo/fim)
    f = int(0.03 * SR)
    est[:f] *= np.linspace(0, 1, f)[:, None]
    est[-f:] *= np.linspace(1, 0, f)[:, None]
    caminho = os.path.join(SAIDA, nome)
    sf.write(caminho, est.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    print(f"{nome}: {os.path.getsize(caminho)} bytes · "
          f"{est.shape[0] / SR:.2f} s · pico {np.max(np.abs(est)):.2f} · rms "
          f"{np.sqrt(np.mean(est ** 2)):.3f}")


if __name__ == "__main__":
    print("sintetizando INFECÇÃO (10 s)...")
    gravar(trilha_infeccao(), "infeccao_aviso.ogg")
    print("sintetizando INFECÇÃO 2 (10 s)...")
    gravar(trilha_infeccao2(), "infeccao2_aviso.ogg")
