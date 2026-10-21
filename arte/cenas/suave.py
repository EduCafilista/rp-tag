"""Pesos SUAVES (sem degrau) para atenuar objetos perto do texto — Python puro, testado.

Regra da spec: nada aparece/some por salto dentro da tela. Toda atenuacao por
regiao ou por tempo passa por rampas smoothstep, nunca por if/else seco.
"""


def ss(a, b, x):
    u = min(1., max(0., (x - a) / (b - a)))
    return u * u * (3 - 2 * u)


def zona(u, v, x0, x1, y0, y1, m):
    """1 dentro da caixa [x0,x1]x[y0,y1] (fracoes da tela), 0 alem da margem m, rampa no meio."""
    return ss(x0 - m, x0, u) * (1 - ss(x1, x1 + m, u)) * ss(y0 - m, y0, v) * (1 - ss(y1, y1 + m, v))


def janela_texto(t):
    """Periodo de leitura: sobe entre 0,6 e 1,0 s, desce entre 4,8 e 5,2 s (rampas de ~10 quadros)."""
    return ss(.6, 1., t) * (1 - ss(4.8, 5.2, t))


def anel_sobre_texto(rt):
    """Peso de um anel do tunel cujo raio na tela (unidades do quadro) cruza a zona do texto."""
    return ss(.8, 1.4, rt) * (1 - ss(4.2, 5.0, rt))
