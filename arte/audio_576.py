#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SOM DO TERREMOTO — 5.7.6 (segunda tentativa, agora com gravação REAL).

O QUE MUDOU EM RELAÇÃO À 5.7.3
------------------------------
Na 5.7.3 o som era 100% SINTETIZADO (ruído filtrado + senoides). O dono:
"melhore a qualidade do som, pesquise outro para colocar no local e que
combine". Então agora a espinha dorsal é uma GRAVAÇÃO REAL de tremor de terra:

    "Rumbling Sound Effects" — bretbernhoft — OpenGameArt — **CC0**
    https://opengameart.org/content/rumbling-sound-effects   (rumbling17.wav)
    (domínio público, sem atribuição obrigatória — mas fica o crédito aqui)

Por cima dela entram só as camadas que uma gravação de ronco não tem:
  1. ESTALOS DE ROCHA (400-3500 Hz, transientes secos) — é o que dá
     "terremoto" e não "vento";
  2. SUB-IMPACTOS (28-45 Hz, decay longo) — o baque físico;
  3. GRÃOS/POEIRA (900-3000 Hz) — pedrinha pulando;
  4. GROAN (62 Hz com vibrato) — a terra gemendo;
  5. automação de volume em ONDAS (a terra aperta e afrouxa).

Gera (mono? não: ESTÉREO, igual ao resto do mod):
  rumble_terremoto.ogg   12,0 s — reforço de fundo (toca a cada 12 s)
  terremoto_colapso.ogg  75,0 s — trilha principal (COLAPSO_MS = 75000)
"""
import os
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt

SR = 44100
rng = np.random.default_rng(576)

SND = '/home/user/rp-tag/src/main/resources/assets/rptag/sounds'
FONTE = '/tmp/r17.wav'          # CC0 OpenGameArt (baixado antes de rodar)

SUB = lambda x: (x[:, None] if x.ndim == 1 else x)          # noqa: E731


# ------------------------------------------------------------------ filtros
def bp(x, lo, hi, order=4):
    """Passa-faixa estável (SOS).

    (5.7.6) Com `lfilter` + coeficientes (b, a) e corte em 22 Hz o filtro
    EXPLODE (NaN em 5 de 5,3 milhões de amostras) — é o clássico problema
    numérico do butter de ordem 4 em frequência baixa. Com second-order
    sections (`sosfilt`) é estável e o resultado é o mesmo.
    """
    lo = max(12.0, lo)
    hi = min(SR / 2 - 200, hi)
    sos = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype='band', output='sos')
    y = sosfilt(sos, np.asarray(x, dtype=np.float64))
    return np.nan_to_num(y, nan=0.0, posinf=0.0, neginf=0.0).astype(np.float32)


def lp(x, hi, order=4):
    sos = butter(order, min(SR / 2 - 200, hi) / (SR / 2), btype='low', output='sos')
    y = sosfilt(sos, np.asarray(x, dtype=np.float64))
    return np.nan_to_num(y, nan=0.0, posinf=0.0, neginf=0.0).astype(np.float32)


def hp(x, lo, order=4):
    sos = butter(order, max(12.0, lo) / (SR / 2), btype='high', output='sos')
    y = sosfilt(sos, np.asarray(x, dtype=np.float64))
    return np.nan_to_num(y, nan=0.0, posinf=0.0, neginf=0.0).astype(np.float32)


def env_ad(n, ataque, decay, curva=1.0):
    t = np.arange(n) / SR
    a = np.clip(t / max(1e-4, ataque), 0, 1) ** curva
    d = np.exp(-np.maximum(0, t - ataque) / max(1e-4, decay))
    return (a * d).astype(np.float32)


def estalo(dur, lo, hi, forca, seed=None):
    r = np.random.default_rng(seed)
    n = int(dur * SR)
    x = bp(r.normal(0, 1, n).astype(np.float32), lo, hi)
    return (x * env_ad(n, 0.0012, dur * 0.30, 1.3) * forca).astype(np.float32)


def boom(dur, f0, forca):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 * (1.0 + 0.9 * np.exp(-t / (dur * 0.10)))     # pitch cai (o baque)
    fase = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(fase) + 0.35 * np.sin(2 * fase) + 0.12 * np.sin(3 * fase)
    return (x * env_ad(n, 0.004, dur * 0.45, 0.8) * forca).astype(np.float32)


# ------------------------------------------------------- base REAL (CC0)
def excita(x, drive=4.0):
    """EXCITADOR HARMÔNICO — o truque que salva este som.

    A gravação CC0 é quase toda SUBSÔNICA (energia medida: 1592 em 1-20 Hz,
    1118 em 20-60 Hz e 0,7 em 400-1200 Hz). Tocar isso direto em caixa de PC
    ou fone comum = nada (foi exatamente a reclamação do dono: "sem corpo").
    Passando o sub por uma saturação tanh saem HARMÔNICOS em 24-240 Hz que o
    ouvido escuta — e o som continua sendo a gravação real, com peso.
    """
    y = np.tanh(np.asarray(x, dtype=np.float64) * drive)
    return (y - y.mean()).astype(np.float32)


def base_cc0(duracao, trechos):
    """Junta trechos da gravação CC0 com crossfade — devolve estéreo."""
    d, sr = sf.read(FONTE, dtype='float32', always_2d=True)
    if sr != SR:                                    # 48k -> 44,1k
        n2 = int(len(d) * SR / sr)
        idx = np.linspace(0, len(d) - 1, n2)
        d = np.stack([np.interp(idx, np.arange(len(d)), d[:, c].astype(np.float64))
                      for c in range(d.shape[1])], axis=1).astype(np.float32)
    # (5.7.6) o WAV do OpenGameArt vem em 32-bit float com valores fora da
    # faixa e o filtro Butterworth EXPLODE em cima disso (NaN em 5 de 5,3
    # milhões de amostras). Sanitiza antes.
    d = np.nan_to_num(d, nan=0.0, posinf=0.0, neginf=0.0)
    d = np.clip(d, -1.0, 1.0)
    d = d - d.mean(axis=0, keepdims=True)           # tira DC (era 0,40 do RMS!)

    total = int(duracao * SR)
    out = np.zeros((total, 2), np.float32)
    pos = 0
    xf = int(2.5 * SR)                              # crossfade de 2,5 s
    for (t0, seg) in trechos:
        i0 = int(t0 * SR)
        n = min(int(seg * SR), len(d) - i0)
        bloco = d[i0:i0 + n].copy()
        # separa o que a gravação tem: SUB (8-90) e uma beirada (90-260)
        sub = bp(bloco, 8, 90)
        sub = sub * (0.55 / (float(np.sqrt((sub.astype(np.float64) ** 2).mean())) + 1e-9))
        borda = bp(bloco, 90, 260) * 3.6
        # o excitador gera os harmônicos audíveis a partir do sub
        harm = excita(sub * 1.6, 4.0) * 0.55
        harm = hp(harm, 70) * 0.5 + lp(hp(harm, 300), 2600) * 0.9
        bloco = (sub * 0.85 + borda * 1.05 + harm * 1.15).astype(np.float32)
        pedaco = len(bloco)
        fim = min(total, pos + pedaco)
        n_bloco = fim - pos
        if n_bloco <= 0:
            break
        bloco = bloco[:n_bloco]
        if pos > 0 and n_bloco > xf:
            r = np.linspace(0, 1, xf)[:, None]
            bloco[:xf] *= r
            out[pos:pos + xf] *= (1 - r)
        out[pos:fim] += bloco
        pos = fim
        if pos >= total:
            break
    return out


# ------------------------------------------------------------- o colapso
def nivelar(x, janela=3.0, minimo=0.55, maximo=2.2):
    """Deixa o volume CONSTANTE ao longo do tempo.

    A gravação CC0 tem vales (aos ~40 s o ronco quase some, medido em 94 de
    energia contra 600 no resto) — sem isso o terremoto "desaparece" no meio e
    volta, o que soa como erro. Aqui o ganho é calculado em janelas de 3 s e
    suavizado, limitado entre 0,55x e 2,2x (nada de estourar o silêncio).
    """
    mono = x.mean(axis=1) if x.ndim > 1 else x
    passo = int(janela * SR)
    n = len(mono)
    k = n // passo + 1
    rms = np.zeros(k, np.float32)
    for i in range(k):
        ch = mono[i * passo:min(n, (i + 1) * passo)]
        rms[i] = float(np.sqrt((ch ** 2).mean())) if len(ch) else 1e-6
    alvo = float(np.percentile(rms[rms > 1e-5], 60)) if np.any(rms > 1e-5) else 1.0
    ganho = np.clip(alvo / np.maximum(rms, 1e-6), minimo, maximo)
    # alisa (média móvel) pra não "bombear"
    suave = np.convolve(ganho, np.ones(3) / 3, mode='same')
    g = np.interp(np.arange(n), np.arange(k) * passo + passo / 2, suave).astype(np.float32)
    if x.ndim > 1:
        return (x * g[:, None]).astype(np.float32)
    return (x * g).astype(np.float32)


def colapso(duracao=75.0):
    n = int(duracao * SR)
    t = np.arange(n) / SR

    # 1) base real (3 trechos diferentes: começo, meio e um final mais denso)
    base = base_cc0(duracao, [(4.0, 26.0), (46.0, 26.0), (88.0, 30.0)])

    # 2) ONDAS: a terra aperta e afrouxa (senão fica um borrão constante)
    ondas = (0.72 + 0.20 * np.sin(2 * np.pi * 0.055 * t + 0.4)
             + 0.08 * np.sin(2 * np.pi * 0.017 * t + 1.7)).astype(np.float32)
    # abertura: os 1,5 primeiros segundos entram (o som "chega")
    abrir = np.clip(t / 1.5, 0, 1).astype(np.float32)
    ondas *= abrir
    base = base * SUB(ondas) * 0.85

    # 3) SUB-IMPACTOS: o baque físico (a cada 8-13 s, e um forte no começo)
    for tt in [0.25, 7.5, 16.0, 27.5, 39.0, 51.0, 63.5, 71.0]:
        b = boom(2.6, float(rng.uniform(28, 42)), float(rng.uniform(0.35, 0.7)))
        i = int(tt * SR)
        j = min(n, i + len(b))
        if j > i:
            base[i:j] += SUB(b[: j - i]) * 0.9

    # 4) ESTALOS DE ROCHA: transientes secos (mais no começo = "abrindo")
    def solta_estalo(tt, forca, lado=0.0):
        d = float(rng.uniform(0.05, 0.22))
        e = estalo(d, float(rng.uniform(450, 1100)), float(rng.uniform(2400, 4200)), forca)
        i = int(tt * SR)
        j = min(n, i + len(e))
        if j <= i:
            return
        bloco = e[: j - i]
        base[i:j, 0] += bloco * (1.0 - max(0.0, lado) * 0.6)
        base[i:j, 1] += bloco * (1.0 + min(0.0, lado) * 0.6)

    tt = 0.9
    while tt < duracao - 0.4:
        denso = tt < 9.0
        solta_estalo(tt, float(rng.uniform(0.35, 0.85)) * (1.15 if denso else 0.8),
                     float(rng.uniform(-0.8, 0.8)))
        # cascata: 2 a 4 estalos juntos (parece terra cedendo, não metrônomo)
        for _ in range(int(rng.integers(1, 4 if denso else 3))):
            solta_estalo(tt + float(rng.uniform(0.05, 0.55)),
                         float(rng.uniform(0.10, 0.40)), float(rng.uniform(-1, 1)))
        tt += float(rng.uniform(1.1 if denso else 1.9, 3.4 if denso else 5.2))

    # 5) GRÃOS DE POEIRA (900-3000): pedrinha pulando, sempre em movimento
    passo = int(0.25 * SR)
    grade = rng.random(n // passo + 2).astype(np.float32)
    mod = np.interp(np.arange(n), np.arange(len(grade)) * passo, grade).astype(np.float32)
    poeira = bp(rng.normal(0, 1, n).astype(np.float32), 900, 3000)
    poeira *= (0.05 + 0.35 * mod ** 3)
    base[:, 0] += poeira * 0.55
    base[:, 1] += np.roll(poeira, 733) * 0.55

    # 6) GROAN: a terra gemendo (62 Hz com vibrato) — sustenta o "vivo"
    vib = 1.0 + 0.018 * np.sin(2 * np.pi * 3.7 * t)
    fase = 2 * np.pi * np.cumsum(62.0 * vib) / SR
    groan = (np.sin(fase) * 0.45 + np.sin(2 * fase) * 0.18).astype(np.float32)
    groan *= (0.30 + 0.70 * np.abs(np.sin(2 * np.pi * 0.045 * t))).astype(np.float32) * 0.16
    base += SUB(groan)

    # 6b) TERRA ARRASTANDO (200-600 Hz) — o que faz "entulho se movendo"
    arrasto = bp(rng.normal(0, 1, n).astype(np.float32), 200, 620)
    arrasto *= (0.25 + 0.75 * mod ** 2).astype(np.float32) * 0.55
    base[:, 0] += arrasto
    base[:, 1] += np.roll(arrasto, 401)

    # 6c) NIVELA o volume (a gravação tem vales)
    base = nivelar(base, 3.0, 0.55, 2.2)

    # 7) cauda: os últimos 2 s fecham (o evento reinicia o som depois)
    tail = int(2.0 * SR)
    base[-tail:] *= np.linspace(1, 0, tail)[:, None]
    return base


# --------------------------------------------------------------- o rumble
def rumble(duracao=12.0):
    n = int(duracao * SR)
    t = np.arange(n) / SR
    base = base_cc0(duracao, [(20.0, 14.0)])[:n]
    base = base * SUB((0.70 + 0.30 * np.sin(2 * np.pi * 0.09 * t))).astype(np.float32)
    base = nivelar(base, 2.0, 0.7, 1.8)
    for tt in [0.4, 5.2, 9.4]:
        b = boom(1.9, float(rng.uniform(30, 40)), float(rng.uniform(0.25, 0.45)))
        i = int(tt * SR)
        j = min(n, i + len(b))
        base[i:j] += SUB(b[: j - i]) * 0.7
    tt = 0.6
    while tt < duracao - 0.3:
        e = estalo(float(rng.uniform(0.05, 0.18)), 500, 3500, float(rng.uniform(0.18, 0.45)))
        i = int(tt * SR)
        j = min(n, i + len(e))
        base[i:j] += SUB(e[: j - i])
        tt += float(rng.uniform(1.3, 3.0))
    # entra e sai macio (toca emendado)
    a = int(0.35 * SR)
    b = int(1.1 * SR)
    base[:a] *= np.linspace(0, 1, a)[:, None]
    base[-b:] *= np.linspace(1, 0, b)[:, None]
    return base


# --------------------------------------------------------------- utilidades
def normaliza(x, pico=0.92):
    m = float(np.max(np.abs(x)))
    return (x * (pico / m)).astype(np.float32) if m > 1e-9 else x


def grava(nome, dados):
    """Escreve o .ogg em pedaços de 1 s (sf.write inteiro SEGFAULTA aqui)."""
    caminho = os.path.join(SND, nome + '.ogg')
    with sf.SoundFile(caminho, mode='w', samplerate=SR,
                      channels=dados.shape[1] if dados.ndim > 1 else 1,
                      format='OGG', subtype='VORBIS') as f:
        for i in range(0, len(dados), SR):
            f.write(dados[i:i + SR])
    tam = os.path.getsize(caminho)
    print(f'  {nome}.ogg  {len(dados) / SR:5.1f} s  {tam / 1024:6.0f} KB')


def relatorio(nome, x):
    m = x.mean(axis=1) if x.ndim > 1 else x
    print(f'{nome}: rms {float(np.sqrt((m ** 2).mean())):.3f}  pico {float(np.abs(m).max()):.3f}')
    seg = SR * 10
    for k in range(min(8, len(m) // seg)):
        ch = m[k * seg:(k + 1) * seg]
        f = np.fft.rfft(ch)
        fr = np.fft.rfftfreq(len(ch), 1 / SR)
        lo = float(np.abs(f[(fr >= 30) & (fr < 150)]).mean())
        mi = float(np.abs(f[(fr >= 150) & (fr < 700)]).mean())
        hi = float(np.abs(f[(fr >= 700) & (fr < 4000)]).mean())
        print(f'   {k * 10:2d}-{k * 10 + 10:2d}s  grave {lo:7.1f}  corpo {mi:6.1f}  brilho {hi:6.1f}')


if __name__ == '__main__':
    if not os.path.exists(FONTE):
        raise SystemExit('falta o /tmp/r17.wav (CC0 OpenGameArt rumbling17.wav)')
    print('gerando terremoto_colapso.ogg (75 s)...')
    c = normaliza(colapso(75.0))
    grava('terremoto_colapso', c)
    relatorio('colapso', c)

    print('gerando rumble_terremoto.ogg (12 s)...')
    r = normaliza(rumble(12.0))
    grava('rumble_terremoto', r)
    relatorio('rumble', r)
    print('ok')
