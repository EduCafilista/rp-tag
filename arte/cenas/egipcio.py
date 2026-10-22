"""Egípcio "O Livro da Saída do Dia": templo à noite, Rá nascendo e o Olho de Hórus.

Câmera em PERSPECTIVA (comum.camera_perspectiva): o plano z = 0 é o quadro
16 x 9 de sempre (x -8..8, y -4.5..4.5) e o ponto de fuga fica no título
(cy = 0.45 h -> mundo y = +0.45). O que é "longe" (céu, estrelas, Rá,
pirâmides, dunas) vive em z < 0; o templo, os hieróglifos e o olho em z > 0.

O texto e o CARTUCHO não entram nos quadros: o cartucho acompanha a largura
da mensagem digitada, então quem desenha é o EgipcioAvisoFx (como nos outros
avisos em vídeo). A área do texto fica limpa e escura.

Geometria do olho copiada do Java (R = 22k = 0.825 unidade):
    sobrancelha .. -1.05R..+0.95R, -0.92R - 0.34R sen(u 2.1)(1 - 0.35u)
    amêndoa ...... 2R de largura, 0.52R acima / 0.46R abaixo
    íris/pupila .. 0.34R / 0.58 da íris
    lágrima ...... x -0.16R, de +0.42R a +1.27R (para baixo)
    espiral ...... (0.28R, 0.46R), raio 0.16R -> 0.58R, an 0..5.2
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bpy
import math

import comum as c
import perspectiva as P
import suave as S

SEED = 727
CY = .45
K = 9 / 240
DIST = 20.0
CAM = P.parametros(DIST, CY)
TAU = 2 * math.pi

# paleta do EgipcioAvisoFx.java
NOITE, PAPIRO, AREIA = 0x0B0A14, 0x3A2A11, 0xC8A24A
AREIA_D, OURO, OURO_B = 0x6E5320, 0xE8C766, 0xFFEBAA
LAPIS, TURQ, OCRE, OSSO = 0x1F4E9C, 0x2E9E8F, 0xA8321E, 0xF2E6C8

KK = 9 / 240              # 1 "k" do Java em unidade de mundo (o Java usa min(w,h)/240)
OLHO_Y = CY + 46 * KK     # 2.175  (cy - 46k no Java, eixo y para baixo)
OLHO_R = 22 * KK          # 0.825  (R = 22k)
ABRE = 2.45

Z_CEU, Z_EST, Z_RA, Z_PIRA, Z_DUNA = -9.0, -8.4, -6.6, -5.2, -3.0
Z_COL, Z_GLIFO, Z_OLHO, Z_AREIA = 0.6, 0.9, 1.2, 1.8


# ------------------------------------------------------------------ util
def fr(x):
    return x - math.floor(x)


def rnd(i, s):
    """Ruído determinístico 0..1 (mesma semente = mesmo quadro ao re-renderizar)."""
    return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)


def eo(x):
    x = c.cl(x)
    return 1 - (1 - x) ** 3


def em(t, a, b):
    return c.ss(a, b, t)


def soma_desloc(v, t_ini, t_fim, n=144):
    """Integral discreta de uma velocidade que morre entre t_ini e t_fim (rolagem que para)."""
    out, acc = [], 0.0
    for q in range(n):
        out.append(acc)
        t = q / 24.0
        acc += v * (1 - c.ss(t_ini, t_fim, t)) / 24.0
    return out


# ------------------------------------------------------------------ materiais
def mat_grad(nome, paradas, alfa=1.0):
    """Gradiente VERTICAL (Generated Y: 0 embaixo, 1 em cima). paradas = [(pos, rgb)].
    O alfa final = alfa x obj.color[3] (funciona com comum.visibilidade)."""
    m = c.material(nome)
    out = c.no(m, 'ShaderNodeOutputMaterial', x=900)
    mix = c.no(m, 'ShaderNodeMixShader', x=700)
    tr = c.no(m, 'ShaderNodeBsdfTransparent', x=500)
    emi = c.no(m, 'ShaderNodeEmission', 'Emissao', x=500)
    tc = c.no(m, 'ShaderNodeTexCoord', x=-600)
    sep = c.no(m, 'ShaderNodeSeparateXYZ', x=-400)
    c.ligar(m, tc, 'Generated', sep, 0)
    ramp = m.node_tree.nodes.new('ShaderNodeValToRGB')
    ramp.location = (-180, 0)
    c.ligar(m, sep, 'Y', ramp, 0)
    el = ramp.color_ramp.elements
    while len(el) > len(paradas):
        el.remove(el[-1])
    while len(el) < len(paradas):
        el.new(.5)
    for e, (p, rgb) in zip(el, paradas):
        e.position = p
        r, g, b, _ = c.lin(rgb)
        e.color = (r, g, b, 1.)
    c.ligar(m, ramp, 'Color', emi, 'Color')
    oi = c.no(m, 'ShaderNodeObjectInfo', x=0)
    mul = c.no(m, 'ShaderNodeMath', x=300)
    mul.operation = 'MULTIPLY'
    mul.use_clamp = True
    mul.inputs[1].default_value = alfa
    c.ligar(m, oi, 'Alpha', mul, 0)
    c.ligar(m, mul, 0, mix, 0)
    c.ligar(m, tr, 0, mix, 1)
    c.ligar(m, emi, 0, mix, 2)
    c.ligar(m, mix, 0, out, 0)
    return m


def mat_disco(nome, paradas, alfa=1.0, corte=.55):
    """Disco circular: cor por raio (ColorRamp no LENGTH) e alfa 1 -> 0 entre `corte` e a borda."""
    m = c.material(nome)
    out = c.no(m, 'ShaderNodeOutputMaterial', x=900)
    mix = c.no(m, 'ShaderNodeMixShader', x=700)
    tr = c.no(m, 'ShaderNodeBsdfTransparent', x=500)
    emi = c.no(m, 'ShaderNodeEmission', 'Emissao', x=500)
    tc = c.no(m, 'ShaderNodeTexCoord', x=-600)
    vl = c.no(m, 'ShaderNodeVectorMath', x=-400)
    vl.operation = 'LENGTH'
    c.ligar(m, tc, 'Object', vl, 0)
    ramp = m.node_tree.nodes.new('ShaderNodeValToRGB')
    ramp.location = (-180, 0)
    c.ligar(m, vl, 'Value', ramp, 0)
    el = ramp.color_ramp.elements
    while len(el) > len(paradas):
        el.remove(el[-1])
    while len(el) < len(paradas):
        el.new(.5)
    for e, (pos, rgb) in zip(el, paradas):
        e.position = pos
        r, g, b, _ = c.lin(rgb)
        e.color = (r, g, b, 1.)
    c.ligar(m, ramp, 'Color', emi, 'Color')
    mr = c.no(m, 'ShaderNodeMapRange', x=100)
    mr.inputs['From Min'].default_value = corte
    mr.inputs['From Max'].default_value = 1.0
    mr.inputs['To Min'].default_value = 1.0
    mr.inputs['To Max'].default_value = 0.0
    mr.clamp = True
    c.ligar(m, vl, 'Value', mr, 0)
    oi = c.no(m, 'ShaderNodeObjectInfo', x=0)
    mul = c.no(m, 'ShaderNodeMath', x=300)
    mul.operation = 'MULTIPLY'
    mul.use_clamp = True
    mul.inputs[1].default_value = alfa
    c.ligar(m, mr, 0, mul, 0)
    mul2 = c.no(m, 'ShaderNodeMath', x=450)
    mul2.operation = 'MULTIPLY'
    mul2.use_clamp = True
    c.ligar(m, mul, 0, mul2, 0)
    c.ligar(m, oi, 'Alpha', mul2, 1)
    c.ligar(m, mul2, 0, mix, 0)
    c.ligar(m, tr, 0, mix, 1)
    c.ligar(m, emi, 0, mix, 2)
    c.ligar(m, mix, 0, out, 0)
    return m


def mat_vinheta(nome, cor, raio, dureza=.55, forca=.55):
    """Escurece as BORDAS: alfa 0 no centro -> forca na borda (o inverso do comum.brilho)."""
    m = c.material(nome)
    out = c.no(m, 'ShaderNodeOutputMaterial', x=900)
    mix = c.no(m, 'ShaderNodeMixShader', x=700)
    tr = c.no(m, 'ShaderNodeBsdfTransparent', x=500)
    emi = c.no(m, 'ShaderNodeEmission', 'Emissao', x=500)
    emi.inputs['Color'].default_value = c.lin(cor)
    tc = c.no(m, 'ShaderNodeTexCoord', x=-500)
    vl = c.no(m, 'ShaderNodeVectorMath', x=-300)
    vl.operation = 'LENGTH'
    c.ligar(m, tc, 'Object', vl, 0)
    rem = c.no(m, 'ShaderNodeMapRange', x=-100)
    rem.inputs['From Min'].default_value = dureza
    rem.inputs['From Max'].default_value = 1.0
    rem.inputs['To Min'].default_value = 0.0
    rem.inputs['To Max'].default_value = 1.0
    rem.clamp = True
    c.ligar(m, vl, 'Value', rem, 0)
    pw = c.no(m, 'ShaderNodeMath', x=100)
    pw.operation = 'POWER'
    pw.inputs[1].default_value = 1.6
    c.ligar(m, rem, 0, pw, 0)
    mult = c.no(m, 'ShaderNodeMath', x=300)
    mult.operation = 'MULTIPLY'
    mult.use_clamp = True
    mult.inputs[1].default_value = forca
    c.ligar(m, pw, 0, mult, 0)
    oi = c.no(m, 'ShaderNodeObjectInfo', x=0)
    mult2 = c.no(m, 'ShaderNodeMath', x=450)
    mult2.operation = 'MULTIPLY'
    mult2.use_clamp = True
    c.ligar(m, mult, 0, mult2, 0)
    c.ligar(m, oi, 'Alpha', mult2, 1)
    c.ligar(m, mult2, 0, mix, 0)
    c.ligar(m, tr, 0, mix, 1)
    c.ligar(m, emi, 0, mix, 2)
    c.ligar(m, mix, 0, out, 0)
    return m


def brilho_anim(nome, cor, raio, pos, curva_alfa):
    """brilho radial com alfa animado (curva_alfa(t)); material fica em bpy.data.materials[nome]."""
    ob = c.brilho(nome, cor, raio, pos, 1.0)
    c.assar_no(bpy.data.materials[nome], 'A', [curva_alfa(t) for t in c.TEMPOS])
    return ob


# ------------------------------------------------------------------ fundo
def ceu():
    m = mat_grad('Ceu', [(0.0, 0x2B1B09), (0.05, 0x150E06), (0.30, 0x0A0810), (1.0, 0x04030A)])
    ob = c.plano('Ceu', 36, 24, m, Z_CEU)
    ob.location.y = CY
    return ob


def estrelas(n=90):
    m = c.mat_emissao('Estrela', c.lin(OURO_B))
    for i in range(n):
        r = .010 + .026 * rnd(i, 13) ** 2
        ob = c.plano('Estrela%03d' % i, r, r, m, Z_EST)
        ob.location.x = (rnd(i, 11) * 2 - 1) * 12
        ob.location.y = .7 + rnd(i, 12) * 6.0
        fase, vel, base = rnd(i, 14) * TAU, .7 + 1.6 * rnd(i, 15), .30 + .55 * rnd(i, 16)
        c.visibilidade(ob, lambda t, f=fase, v=vel, b=base: b * (.55 + .45 * math.sin(f + t * v)) ** 2)


def ra():
    """Disco de Rá nascendo no HORIZONTE, atrás das pirâmides (h*0.71 no Java)."""
    y_ra, r = -1.89, 1.30
    brilho_anim('RaHalo', OURO, r * 2.4, (0, y_ra, Z_RA - .2),
                lambda t: .26 * em(t, .1, 1.9) * (1 + .06 * math.sin(t * .9)))
    m = mat_disco('RaDisco', [(.0, 0xFFF2C4), (.35, 0xF0C066), (.75, 0xE8A94A), (1.0, 0xC4732A)],
                  alfa=.95, corte=.30)
    disco = c.plano('Ra', r * 2, r * 2, m, Z_RA)
    # sobe 26k (0.975) enquanto aparece
    c.animar(disco, 'location', lambda t: y_ra - (1 - eo(c.cl((t - .25) / 1.8))) * 26 * K, 1)
    c.visibilidade(disco, lambda t: em(t, .05, .7))
    # raios curtos girando devagar
    mr = c.mat_emissao('RaRaio', c.lin(OURO))
    for i in range(24):
        a0 = i * TAU / 24
        comp = .55 + .55 * rnd(i, 21)
        pts, n = [], 5
        for j in range(n + 1):
            a = a0 + .10 * j / n
            rr = r * .95 + comp * j / n
            pts.append((math.cos(a) * rr, math.sin(a) * rr, 0))
        raio = c.curva('RaRaio%02d' % i, pts, mr, espessura=.030, z=Z_RA + .05)
        raio.location = (0, y_ra - .05, Z_RA + .05)
        c.animar(raio, 'rotation_euler', lambda t: .12 * t, 2)
        c.visibilidade(raio, lambda t, i=i: .42 * em(t, .35, 1.5) * (.7 + .3 * math.sin(t + i)))


def piramide(nome, x, y0, larg, alt, z, cor):
    """Silhueta de pirâmide (triângulo 2D) + aresta central e a do lado do sol."""
    hw = larg / 2
    me = bpy.data.meshes.new(nome)
    me.from_pydata([(-hw, 0, 0), (hw, 0, 0), (0, alt, 0)], [], [(0, 1, 2)])
    ob = bpy.data.objects.new(nome, me)
    ob.location = (x, y0, z)
    me.materials.append(c.mat_emissao(nome + 'Mat', c.lin(cor)))
    bpy.context.scene.collection.objects.link(ob)
    c.visibilidade(ob, lambda t: em(t, .10, 1.10))
    ma = c.mat_emissao(nome + 'ArestaMat', c.lin(OURO))
    for i, pts in enumerate(([(-hw, 0, 0), (0, alt, 0)],            # face iluminada (sol a oeste)
                             [(0, 0, 0), (0, alt, 0)],              # aresta central
                             [(hw, 0, 0), (0, alt, 0)])):           # face na sombra
        aresta = c.curva('%sAresta%d' % (nome, i), pts, ma,
                         espessura=.028 if i == 0 else .016, z=z + .02 + .001 * i)
        aresta.location = (x, y0, z + .02 + .001 * i)
        c.visibilidade(aresta, lambda t, i=i: (.52 if i == 0 else .24) * em(t, .40, 1.60))
    return ob


def piramides():
    piramide('PiraEsq', -5.0, -1.55, 5.6, 3.4, Z_PIRA, 0x171320)
    piramide('PiraDir', 5.1, -1.55, 4.4, 2.6, Z_PIRA + .2, 0x191524)
    piramide('PiraCentro', 1.1, -1.50, 2.7, 1.6, Z_PIRA + .4, 0x1C1728)


def duna(nome, base_y, amp, cor, z, alfa=.98, crista=.5):
    n, hi = 72, 13.0
    pts = []
    for j in range(n + 1):
        x = -hi + 2 * hi * j / n
        y = base_y - amp * .20 * math.sin(x * .52 + z) - amp * .06 * math.sin(x * 1.6 + 2 + z)
        pts.append((x, y))
    me = bpy.data.meshes.new(nome)
    vs = [(x, y, 0) for x, y in pts] + [(-hi, -8, 0), (hi, -8, 0)]
    fs = [(j, j + 1, n + 2) for j in range(n)] + [(n, n + 1, n + 2)]
    me.from_pydata(vs, [], fs)
    ob = bpy.data.objects.new(nome, me)
    ob.location.z = z
    me.materials.append(c.mat_emissao(nome + 'Mat', c.lin(cor)))
    bpy.context.scene.collection.objects.link(ob)
    c.visibilidade(ob, lambda t, a=alfa: a * em(t, 0.0, .9))
    cr = c.curva(nome + 'Crista', [(x, y, 0) for x, y in pts],
                 c.mat_emissao(nome + 'CristaMat', c.lin(OURO)), espessura=.018, z=z + .03)
    c.visibilidade(cr, lambda t, a=crista: a * em(t, .3, 1.5))
    return ob


def dunas():
    duna('DunaLonge', -1.62, 1.30, 0x2E2210, Z_DUNA - 1.2, .95, .26)
    duna('DunaMeio', -2.10, 1.45, 0x201809, Z_DUNA - .5, .97, .34)
    duna('DunaPerto', -2.75, 1.60, 0x140F06, Z_DUNA, 1.0, .42)


def coluna(nome, x, y0, alt, larg, z):
    m = c.mat_emissao(nome + 'Mat', c.lin(0x150F07))
    fuste = c.plano(nome, larg, alt, m, z)
    fuste.location.x, fuste.location.y = x, y0 + alt / 2
    cap = c.plano(nome + 'Cap', larg * 1.7, alt * .085, m, z + .01)
    cap.location.x, cap.location.y = x, y0 + alt - alt * .04
    base = c.plano(nome + 'Base', larg * 1.35, alt * .05, m, z + .01)
    base.location.x, base.location.y = x, y0 + alt * .025
    for ob in (fuste, cap, base):
        c.visibilidade(ob, lambda t: em(t, .15, 1.3))
    mb = c.mat_emissao(nome + 'Fio', c.lin(OURO))
    for lado in (-1, 1):
        fio = c.curva('%sFio%d' % (nome, lado), [(0, 0, 0), (0, alt, 0)], mb, espessura=.016, z=z + .02)
        fio.location = (x + lado * larg / 2, y0, z + .02)
        c.visibilidade(fio, lambda t: .38 * em(t, .45, 1.7))
    # capitel com folhas de papiro (3 traços)
    for k in range(3):
        ax = x + (k - 1) * larg * .45
        folha = c.curva('%sFolha%d' % (nome, k),
                        [(ax, y0 + alt - alt * .05, 0), (ax + (k - 1) * larg * .22, y0 + alt + alt * .07, 0)],
                        mb, espessura=.014, z=z + .02)
        c.visibilidade(folha, lambda t: .30 * em(t, .6, 1.8))
    return fuste


def templo():
    for i, (x, z, alt) in enumerate([(-6.3, .7, 5.0), (-7.9, -.4, 4.4), (6.3, .7, 5.0), (7.9, -.4, 4.4)]):
        coluna('Col%d' % i, x, -1.30, alt, 1.00, z)
    m = c.mat_emissao('Arquitrave', c.lin(0x120D06))
    for lado in (-1, 1):
        v = c.plano('Viga%d' % lado, 3.4, .40, m, -.1)
        v.location.x, v.location.y = lado * 7.1, 3.42
        c.visibilidade(v, lambda t: .90 * em(t, .30, 1.5))


def glifo_pts(tipo, x, y, s):
    """Um hieróglifo em curvas: ankh, olho, água, sol, junco."""
    if tipo == 0:      # ankh
        return [[(x + math.cos(i * TAU / 14) * s * .30, y + s * .55 + math.sin(i * TAU / 14) * s * .30, 0)
                 for i in range(15)],
                [(x - s * .42, y + s * .12, 0), (x + s * .42, y + s * .12, 0)],
                [(x, y + s * .12, 0), (x, y - s * .70, 0)]]
    if tipo == 1:      # olho
        return [[(x - s * .55, y, 0), (x + s * .55, y, 0)],
                [(x + math.cos(i * TAU / 14) * s * .46, y + math.sin(i * TAU / 14) * s * .24, 0)
                 for i in range(15)],
                [(x - s * .08, y - s * .20, 0), (x - s * .26, y - s * .58, 0)]]
    if tipo == 2:      # água
        out = []
        for k in range(3):
            yy = y + s * .40 - k * s * .40
            out.append([(x - s * .55, yy, 0), (x - s * .18, yy + s * .14, 0),
                        (x + s * .18, yy - s * .14, 0), (x + s * .55, yy, 0)])
        return out
    if tipo == 3:      # sol
        out = [[(x + math.cos(i * TAU / 14) * s * .32, y + math.sin(i * TAU / 14) * s * .32, 0)
                for i in range(15)]]
        for k in range(8):
            a = k * TAU / 8
            out.append([(x + math.cos(a) * s * .42, y + math.sin(a) * s * .42, 0),
                        (x + math.cos(a) * s * .60, y + math.sin(a) * s * .60, 0)])
        return out
    return [[(x, y - s * .62, 0), (x + s * .10, y + s * .58, 0)],
            [(x + s * .10, y + s * .58, 0), (x + s * .26, y + s * .28, 0),
             (x + s * .16, y + s * .00, 0), (x + s * .02, y + s * .18, 0)]]


def hieroglifos():
    """Duas colunas rolando nas laterais; PARAM quando o olho abre (ABRE = 2.45)."""
    m = c.mat_emissao('Glifo', c.lin(OURO))
    cel, v = .46, .62
    desloc = soma_desloc(v, 2.15, 2.95)

    def rolagem(t):
        q = int(round(t * 24))
        return desloc[min(max(q, 0), len(desloc) - 1)]

    for cidx, x in enumerate((-4.95, 4.95)):
        mf = c.mat_emissao('Moldura%d' % cidx, c.lin(OURO))
        for lado in (-1, 1):
            fio = c.curva('Mold%d_%d' % (cidx, lado), [(0, 0, 0), (0, 6.4, 0)], mf, espessura=.013, z=Z_GLIFO - .05)
            fio.location = (x + lado * .34, -1.9, Z_GLIFO - .05)
            c.visibilidade(fio, lambda t: .26 * em(t, .5, 1.8))
        # 20 glifos: cobre a coluna e o que entra/sai durante a rolagem
        for i in range(20):
            s = .155
            y0 = -1.9 + i * cel
            tipo = int(rnd(i, 71 + cidx) * 5) % 5
            fio_a = .44 * (.55 + .45 * rnd(i, 33 + cidx))
            for j, pts in enumerate(glifo_pts(tipo, x, y0, s)):
                cu = c.curva('G%d_%d_%d' % (cidx, i, j), pts, m, espessura=s * .085, z=Z_GLIFO)
                c.animar(cu, 'location', lambda t, r=rolagem: r(t), 1)
                # desaparece suave nas pontas da coluna (e no fim do aviso)
                yy = y0 + .55

                def alfa(t, yy=yy, a=fio_a):
                    p = yy + rolagem(t)
                    return a * em(t, .55, 1.75) * c.ss(-2.2, -1.3, p) * (1 - c.ss(4.3, 5.1, p))
                c.visibilidade(cu, alfa)


def olho():
    """O OLHO DE HÓRUS: desenhado traço por traço entre 1.15 e 2.45 s (igual ao Java).

    IMPORTANTE: o Java usa y para BAIXO; aqui y cresce para CIMA. Tudo que no Java
    e "cy - d" vira "oy + d" e tudo que e "cy + d" vira "oy - d" (a espiral era o
    unico ponto em que isso tinha sido esquecido).
    """
    ox, oy, R = 0.0, OLHO_Y, OLHO_R
    m = c.mat_emissao('Olho', c.lin(OURO))
    mf = c.mat_emissao('OlhoForte', c.lin(OURO_B))

    def revelar(nome, pts, esp, t0, t1, mat=None):
        """Curva UNICA revelada por bevel_factor_end (sem os 'dentes' de segmentos soltos)."""
        ob = c.curva(nome, [(x, y, 0) for x, y in pts], mat or m, espessura=esp, z=Z_OLHO + .04)
        ob.data.bevel_factor_end = 0.0
        c.assar(ob.data, 'bevel_factor_end', [c.ss(t0, t1, t) for t in c.TEMPOS])
        return ob

    # 1) sobrancelha (1.15 - 1.45)
    n = 18
    sob = [(ox - R * 1.05 + 2 * R * (i / n),
            oy + R * .92 + R * .34 * math.sin((i / n) * 2.1) * (1 - (i / n) * .35)) for i in range(n + 1)]
    revelar('Sobrancelha', sob, R * .075, 1.15, 1.45)

    # 2) amêndoa (1.45 - 1.85): borda de cima e de baixo
    n = 24
    cima = [(ox - R + 2 * R * (i / n), oy + R * .52 * math.sin(math.pi * i / n)) for i in range(n + 1)]
    baixo = [(ox - R + 2 * R * (i / n), oy - R * .46 * math.sin(math.pi * i / n)) for i in range(n + 1)]
    revelar('AmendoaCima', cima, R * .085, 1.45, 1.72)
    revelar('AmendoaBaixo', baixo, R * .085, 1.58, 1.85)

    # 3) íris (1.85 - 2.05) e pupila (1.95 - 2.15) — no Java b.circ e DISCO cheio,
    # nao anel: a espiral passa por cima dela e precisa de ouro cheio embaixo
    ir = R * .34
    mi = mat_disco('IrisMat', [(0.0, OURO_B), (0.82, OURO_B), (1.0, 0xD8B45A)], alfa=1.0, corte=.90)
    disco = c.plano('Iris', 2, 2, mi, Z_OLHO)
    disco.location.y = oy
    for eixo in (0, 1):
        c.animar(disco, 'scale', lambda t: ir * eo(c.cl((t - 1.85) / .20)), eixo)
    # pupila CIRCULAR (o Java usa circ; um plano quadrado aparecia como um quadrado preto)
    mp = mat_disco('PupilaMat', [(0.0, 0x140E02), (1.0, 0x0B0700)], alfa=1.0, corte=.985)
    pup = c.plano('Pupila', 2, 2, mp, Z_OLHO + .02)
    pup.location.y = oy
    for eixo in (0, 1):
        c.animar(pup, 'scale', lambda t: ir * .58 * eo(c.cl((t - 1.95) / .20)), eixo)
    # reflexo de luz no canto alto-esquerdo da íris (como o circ do Java)
    refl = mat_disco('ReflexoMat', [(0.0, OURO_B), (1.0, OURO_B)], alfa=1.0, corte=.55)
    rf = c.plano('Reflexo', 2, 2, refl, Z_OLHO + .03)
    rf.location.x, rf.location.y = ox - ir * .18, oy + ir * .20
    for eixo in (0, 1):
        c.animar(rf, 'scale', lambda t: ir * .16 * eo(c.cl((t - 2.02) / .15)), eixo)

    # 4) lágrima (2.10 - 2.28): desce a partir da borda de baixo
    revelar('Lagrima', [(ox - R * .16, oy - R * .42), (ox - R * .16, oy - R * 1.27)], R * .10, 2.10, 2.28)

    # 5) espiral (2.28 - 2.45)
    xa, ya = ox + R * .28, oy - R * .46
    n = 24
    esp = []
    for i in range(n + 1):
        u = i / n
        an, rr = u * 5.2, R * .16 + u * R * .42
        esp.append((xa + math.cos(an + 1.2) * rr, ya - math.sin(an + 1.2) * rr))
    revelar('Espiral', esp, R * .085, 2.26, 2.45)

    # poço escuro atrás (38k, como o Java) e halo que pulsa depois de abrir
    brilho_anim('OlhoPoco', 0x0A0703, 38 * KK, (0, oy, Z_OLHO - .25), lambda t: .74 * em(t, .55, 1.5))
    brilho_anim('OlhoHalo', OURO, 2.9, (0, oy, Z_OLHO - .1),
                lambda t: .16 * em(t, 2.2, 2.8) * (.85 + .15 * math.sin(t * 2.6)))

    # CLARÃO de ouro quando o olho abre
    brilho_anim('Clarao', OURO_B, 6.2, (0, oy, Z_OLHO + .5),
                lambda t: .20 * max(0.0, 1 - (t - ABRE) / .40) if t >= ABRE else 0.0)


def areia(n=70, poeira=26):
    m = c.mat_emissao('Grao', c.lin(OURO))
    mp = c.mat_emissao('Poeira', c.lin(OURO_B))
    for i in range(n):
        r = .018 + .026 * rnd(i, 22)
        ob = c.plano('Grao%03d' % i, r, r, m, Z_AREIA)
        x0 = -9.5 + rnd(i, 23) * 3.0
        y0 = -4.4 + rnd(i, 24) * 8.8
        vx = 1.2 + 1.5 * rnd(i, 25)
        vy = -.12 + .30 * rnd(i, 26)
        f = rnd(i, 27) * TAU
        c.animar(ob, 'location', lambda t, x=x0, v=vx, f=f: x + v * t + .16 * math.sin(t * 2.4 + f), 0)
        c.animar(ob, 'location', lambda t, y=y0, v=vy: y + v * t, 1)
        c.visibilidade(ob, lambda t, i=i, x=x0, v=vx, y=y0, vy=vy, f=f: (.34 + .46 * rnd(i, 28)) * em(t, .15, 1.1)
                       * fora_do_texto(x + v * t + .16 * math.sin(t * 2.4 + f), y + v * t, Z_AREIA, t))
    for i in range(poeira):
        r = .012 + .022 * rnd(i, 31)
        ob = c.plano('Poeira%02d' % i, r, r, mp, Z_AREIA + .1)
        x0 = (rnd(i, 32) * 2 - 1) * 8.5
        y0 = -4.5 + rnd(i, 33) * 2.0
        v = .30 + .45 * rnd(i, 34)
        f = rnd(i, 35) * TAU
        c.animar(ob, 'location', lambda t, x=x0, f=f: x + .30 * math.sin(t * .85 + f), 0)
        c.animar(ob, 'location', lambda t, y=y0, v=v: y + v * t, 1)
        c.visibilidade(ob, lambda t, y=y0, v=v, i=i: .26 * rnd(i, 36) * em(t, .5, 1.5) *
                       (1 - c.ss(3.2, 4.2, y0 + v * t)))


def vinheta():
    m = mat_vinheta('Vinheta', NOITE, 1.0, .40, .62)
    ob = c.plano('Vinheta', 17.2, 9.7, m, Z_AREIA + .5)
    ob.location.y = CY
    c.visibilidade(ob, lambda t: .85 * em(t, .0, .8))
    return ob


def fora_do_texto(x, y, z, t):
    """1 = pode aparecer; perto do título/mensagem cai para 0,12 (o Java desenha o texto por cima)."""
    u, v = P.projetar(CAM, x, y, z)
    return 1 - .88 * S.janela_texto(t) * S.zona(u, v, .22, .78, .37, .61, .06)


def montar():
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 24
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 3
    sc.cycles.diffuse_bounces = 1
    sc.cycles.glossy_bounces = 1
    sc.cycles.transmission_bounces = 1
    sc.cycles.transparent_max_bounces = 16
    sc.render.filter_size = 1.2

    c.camera_perspectiva(DIST, CY)

    ceu()
    estrelas()
    ra()
    piramides()
    dunas()
    templo()
    hieroglifos()
    olho()
    areia()
    vinheta()
    return c.metadata('egipcio', SEED)


if __name__ == '__main__':
    c.executar('egipcio', montar)
