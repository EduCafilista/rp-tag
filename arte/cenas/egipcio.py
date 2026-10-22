"""Egípcio v2 — "O Faraó observa": olho de Hórus grande, raios coloridos e friso.

Estilo da referência do dono (fundo azul-noite, olho dourado com íris turquesa/
azul/amarela, leque de raios coloridos atrás, friso de hieróglifos em cima e
embaixo, pirâmides escuras no rodapé).

Câmera em PERSPECTIVA (comum.camera_perspectiva): o plano z = 0 é o quadro
16 x 9 (x -8..8, y -4.5..4.5) e o ponto de fuga fica no título (cy = 0.45).

O TEXTO e a PLACA do título são desenhados pelo cliente (EgipcioAvisoFx):
a faixa central y 0.05..0.85 fica limpa para a placa dourada.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bpy
import math

import comum as c
import perspectiva as P
import suave as S

SEED = 811
CY = .45
K = 9 / 240
DIST = 20.0
CAM = P.parametros(DIST, CY)
TAU = 2 * math.pi

# paleta da referência (azul-noite + ouro + íris colorida)
NOITE, AZUL, AZUL_CLARO = 0x0A1024, 0x16224C, 0x28386E
OURO, OURO_B, OURO_D = 0xF2C64C, 0xFFE9A8, 0xB8862A
VERM, LARANJA, AMAR, TURQ, AZUL_RAY = 0xE23B2E, 0xF08A2A, 0xF7D24A, 0x35C0A6, 0x4A78D8
PUPILA = 0x0B0A06

EYE_Y = 2.52
EYE_HW = 1.55          # meia largura da amêndoa
EYE_UP = .52           # altura da borda de cima
EYE_DN = .34           # profundidade da borda de baixo
IRIS_R = .44
ABRE = 2.45

Z_CEU, Z_RAIOS, Z_FEIXE, Z_FRISOS = -9.0, -7.4, -7.0, -6.4
Z_PIRA, Z_GLIFOS, Z_AURA, Z_OLHO, Z_ESTRELA = -5.6, -4.2, .6, 1.2, 1.6


def fr(x):
    return x - math.floor(x)


def rnd(i, s):
    return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)


def eo(x):
    x = c.cl(x)
    return 1 - (1 - x) ** 3


def em(t, a, b):
    return c.ss(a, b, t)


# ------------------------------------------------------------------ materiais
def mat_grad(nome, paradas, alfa=1.0):
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
    for e, (p, rgb) in zip(el, paradas):
        e.position = p
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


def brilho_anim(nome, cor, raio, pos, curva_alfa):
    ob = c.brilho(nome, cor, raio, pos, 1.0)
    c.assar_no(bpy.data.materials[nome], 'A', [curva_alfa(t) for t in c.TEMPOS])
    return ob


# ------------------------------------------------------------------ fundo
def ceu():
    """Azul-noite: topo e base escuros, faixa central mais clara (a referência)."""
    m = mat_grad('Ceu', [(0.0, 0x070B18), (0.30, 0x1A2650), (0.55, 0x2A3A78), (0.78, 0x141C3C), (1.0, 0x05070F)])
    ob = c.plano('Ceu', 36, 24, m, Z_CEU)
    ob.location.y = CY
    return ob


def feixe(i, x0, larg, incl, alfa):
    """Feixe de luz branco vindo de cima (triângulo comprido)."""
    me = bpy.data.meshes.new('Feixe%d' % i)
    me.from_pydata([(x0 - larg / 2, 5.2, 0), (x0 + larg / 2, 5.2, 0),
                    (x0 + incl + larg * 1.9, -3.2, 0), (x0 + incl - larg * 1.9, -3.2, 0)],
                   [], [(0, 1, 2, 3)])
    ob = bpy.data.objects.new('Feixe%d' % i, me)
    ob.location.z = Z_FEIXE
    me.materials.append(c.mat_emissao('FeixeMat%d' % i, c.lin(0xFFF6DC)))
    bpy.context.scene.collection.objects.link(ob)
    fase = rnd(i, 5) * TAU
    c.visibilidade(ob, lambda t, a=alfa, f=fase: a * em(t, .1, 1.4) * (.75 + .25 * math.sin(t * .7 + f)))
    return ob


def feixes():
    feixe(0, 0.0, .55, .35, .10)
    feixe(1, -2.6, .34, 1.10, .07)
    feixe(2, 2.6, .34, -1.10, .07)
    feixe(3, -5.2, .22, .80, .05)
    feixe(4, 5.2, .22, -.80, .05)


def raios():
    """Leque de raios COLORIDOS atrás do olho (vermelho, laranja, amarelo, turquesa, azul)."""
    cores = [VERM, LARANJA, AMAR, TURQ, AZUL_RAY, AMAR, LARANJA, VERM]
    n = 32
    for i in range(n):
        a = -math.pi * .97 + (i / (n - 1)) * math.pi * 1.94      # leque quase completo
        comp = 3.15 + .55 * rnd(i, 41)
        larg = .052 + .034 * rnd(i, 42)
        r0 = .78
        p0 = (math.cos(a - larg) * r0, EYE_Y + math.sin(a - larg) * r0)
        p1 = (math.cos(a + larg) * r0, EYE_Y + math.sin(a + larg) * r0)
        p2 = (math.cos(a) * comp, EYE_Y + math.sin(a) * comp)
        me = bpy.data.meshes.new('Raio%02d' % i)
        me.from_pydata([(p0[0], p0[1], 0), (p1[0], p1[1], 0), (p2[0], p2[1], 0)], [], [(0, 1, 2)])
        ob = bpy.data.objects.new('Raio%02d' % i, me)
        ob.location.z = Z_RAIOS
        me.materials.append(c.mat_emissao('RaioMat%02d' % i, c.lin(cores[i % len(cores)])))
        bpy.context.scene.collection.objects.link(ob)
        # cresce do centro para fora no instante em que o olho abre
        d = .12 * rnd(i, 43)
        c.animar(ob, 'scale', lambda t, d=d: eo(c.cl((t - (ABRE - .08) - d) / .55)), 0)
        c.animar(ob, 'scale', lambda t, d=d: eo(c.cl((t - (ABRE - .08) - d) / .55)), 1)
        c.visibilidade(ob, lambda t, i=i: (.62 + .30 * rnd(i, 44)) * em(t, ABRE - .05, ABRE + .35))
        # raio branco fino no meio do colorido (o brilho da referência)
        mw = c.mat_emissao('RaioBrancoMat', c.lin(0xFFF8E0))
        bw = bpy.data.meshes.new('RaioB%02d' % i)
        bw.from_pydata([(p0[0], p0[1], 0), (p1[0], p1[1], 0), (p2[0] * .96, p2[1] * .96, 0)], [], [(0, 1, 2)])
        obw = bpy.data.objects.new('RaioB%02d' % i, bw)
        obw.location.z = Z_RAIOS + .03
        bw.materials.append(mw)
        bpy.context.scene.collection.objects.link(obw)
        c.animar(obw, 'scale', lambda t, d=d: eo(c.cl((t - (ABRE - .02) - d) / .5)), 0)
        c.animar(obw, 'scale', lambda t, d=d: eo(c.cl((t - (ABRE - .02) - d) / .5)), 1)
        c.visibilidade(obw, lambda t, i=i: .35 * em(t, ABRE, ABRE + .4))


def glifo_pts(tipo, x, y, s):
    """Hieróglifos em curvas (ankh, olho, água, sol, junco)."""
    if tipo == 0:      # ankh
        return [[(x + math.cos(i * TAU / 12) * s * .30, y + s * .52 + math.sin(i * TAU / 12) * s * .30, 0)
                 for i in range(13)],
                [(x - s * .40, y + s * .10, 0), (x + s * .40, y + s * .10, 0)],
                [(x, y + s * .10, 0), (x, y - s * .62, 0)]]
    if tipo == 1:      # olho
        return [[(x - s * .50, y, 0), (x + s * .50, y, 0)],
                [(x + math.cos(i * TAU / 12) * s * .40, y + math.sin(i * TAU / 12) * s * .22, 0)
                 for i in range(13)],
                [(x - s * .06, y - s * .18, 0), (x - s * .22, y - s * .52, 0)]]
    if tipo == 2:      # água
        out = []
        for k in range(3):
            yy = y + s * .34 - k * s * .34
            out.append([(x - s * .46, yy, 0), (x - s * .15, yy + s * .12, 0),
                        (x + s * .15, yy - s * .12, 0), (x + s * .46, yy, 0)])
        return out
    if tipo == 3:      # sol
        out = [[(x + math.cos(i * TAU / 12) * s * .26, y + math.sin(i * TAU / 12) * s * .26, 0)
                for i in range(13)]]
        for k in range(8):
            a = k * TAU / 8
            out.append([(x + math.cos(a) * s * .34, y + math.sin(a) * s * .34, 0),
                        (x + math.cos(a) * s * .50, y + math.sin(a) * s * .50, 0)])
        return out
    return [[(x, y - s * .55, 0), (x + s * .08, y + s * .50, 0)],
            [(x + s * .08, y + s * .50, 0), (x + s * .22, y + s * .24, 0),
             (x + s * .14, y + s * .00, 0), (x + s * .02, y + s * .16, 0)]]


def friso(y, i0=0, espelhar=False):
    """Faixa de hieróglifos do topo/rodapé (como as bordas da referência)."""
    m = c.mat_emissao('FrisoMat%d' % (0 if y > 0 else 1), c.lin(OURO_D))
    mb = c.mat_emissao('FrisoBarra%d' % (0 if y > 0 else 1), c.lin(0x0C1330))
    # banda escura
    banda = bpy.data.meshes.new('Banda%d' % int(y * 10))
    banda.from_pydata([(-9, y - .46, 0), (9, y - .46, 0), (9, y + .46, 0), (-9, y + .46, 0)], [], [(0, 1, 2, 3)])
    ob = bpy.data.objects.new('Banda%d' % int(y * 10), banda)
    ob.location.z = Z_FRISOS - .02
    banda.materials.append(mb)
    bpy.context.scene.collection.objects.link(ob)
    c.visibilidade(ob, lambda t: .92 * em(t, 0, .5))
    # filetes de ouro acima/abaixo da banda
    for dy in (-.46, .46):
        fio = c.curva('Filete%d_%d' % (int(y * 10), int(dy * 100)),
                      [(-9, y + dy, 0), (9, y + dy, 0)], m, espessura=.022, z=Z_FRISOS)
        c.visibilidade(fio, lambda t: .85 * em(t, .05, .8))
    # glifos em sequência
    n = 26
    for i in range(n):
        x = -8.2 + i * (16.4 / (n - 1))
        tipo = int(rnd(i, 91 + (0 if y > 0 else 7)) * 5) % 5
        s = .16
        for j, pts in enumerate(glifo_pts(tipo, x, y, s)):
            cu = c.curva('F%d_%d_%d' % (int(y * 10), i, j), pts, m, espessura=s * .075, z=Z_FRISOS)
            if espelhar:
                cu.rotation_euler[2] = math.pi
                cu.location.x = 2 * x
            d = .35 * (i / n)
            c.visibilidade(cu, lambda t, d=d: .85 * em(t, .15 + d, .7 + d))


def piramides():
    """Três pirâmides escuras no rodapé, atrás da placa do título."""
    for i, (x, base, larg, alt) in enumerate([(-2.55, -3.30, 3.5, 1.85), (0.0, -3.30, 4.4, 2.45),
                                             (2.75, -3.30, 3.0, 1.55)]):
        me = bpy.data.meshes.new('Pira%d' % i)
        hw = larg / 2
        me.from_pydata([(-hw, 0, 0), (hw, 0, 0), (0, alt, 0)], [], [(0, 1, 2)])
        ob = bpy.data.objects.new('Pira%d' % i, me)
        ob.location = (x, base, Z_PIRA - .02 * i)
        me.materials.append(c.mat_emissao('PiraMat%d' % i, c.lin(0x0A0F26)))
        bpy.context.scene.collection.objects.link(ob)
        c.visibilidade(ob, lambda t: em(t, .1, .9))
        ma = c.mat_emissao('PiraAresta%d' % i, c.lin(OURO_D))
        for j, pts in enumerate(([(-hw, 0, 0), (0, alt, 0)], [(0, 0, 0), (0, alt, 0)])):
            a = c.curva('PiraA%d_%d' % (i, j), pts, ma, espessura=.016, z=Z_PIRA + .01)
            a.location = (x, base, Z_PIRA + .01)
            c.visibilidade(a, lambda t: .40 * em(t, .4, 1.2))


def aura():
    """Círculo dourado fino + anéis concêntricos atrás do olho (a referência)."""
    m = c.mat_emissao('Aura', c.lin(OURO))
    for r, esp, al in ((2.05, .017, .42), (2.55, .011, .26), (3.05, .007, .16)):
        pts = [(math.cos(i * TAU / 96) * r, EYE_Y + math.sin(i * TAU / 96) * r, 0) for i in range(97)]
        ob = c.curva('Aneis%.0f' % (r * 100), pts, m, espessura=esp, z=Z_AURA)
        c.visibilidade(ob, lambda t, al=al: al * em(t, .35, 1.3))
    # brilho central
    brilho_anim('AuraBrilho', 0x9FB8FF, 3.4, (0, EYE_Y, Z_AURA - .1),
                lambda t: .16 * em(t, .2, 1.3))


def olho():
    """O OLHO DE HÓRUS grande, dourado, com íris colorida — desenhado traço por traço."""
    ox, oy = 0.0, EYE_Y
    m = c.mat_emissao('Olho', c.lin(OURO))
    mf = c.mat_emissao('OlhoClaro', c.lin(OURO_B))

    def revelar(nome, pts, esp, t0, t1, mat=None, z=Z_OLHO + .04):
        ob = c.curva(nome, [(x, y, 0) for x, y in pts], mat or m, espessura=esp, z=z)
        ob.data.bevel_factor_end = 0.0
        c.assar(ob.data, 'bevel_factor_end', [c.ss(t0, t1, t) for t in c.TEMPOS])
        return ob

    # 1) sobrancelha (1.15 - 1.45): barra curva
    n = 24
    sob = []
    for i in range(n + 1):
        u = i / n
        x = ox - EYE_HW * .92 + 2 * EYE_HW * .92 * u
        # arco largo: sobe no meio e desce nas pontas (como a referência)
        y = oy + EYE_UP + .30 + .30 * math.sin(math.pi * u)
        sob.append((x, y))
    revelar('Sobrancelha', sob, .095, 1.15, 1.45)

    # 2) amêndoa (1.45 - 1.85)
    n = 32
    cima = [(ox - EYE_HW + 2 * EYE_HW * (i / n), oy + EYE_UP * math.sin(math.pi * i / n)) for i in range(n + 1)]
    baixo = [(ox - EYE_HW + 2 * EYE_HW * (i / n), oy - EYE_DN * math.sin(math.pi * i / n)) for i in range(n + 1)]
    revelar('AmendoaCima', cima, .095, 1.45, 1.72)
    revelar('AmendoaBaixo', baixo, .095, 1.58, 1.85)

    # 3) íris colorida (1.85 - 2.10): anéis concêntricos, do ouro para fora
    aneis = [(IRIS_R, OURO), (IRIS_R * .93, TURQ), (IRIS_R * .74, AZUL_RAY),
             (IRIS_R * .55, AMAR), (IRIS_R * .38, OURO_D)]
    for k, (r, cor) in enumerate(aneis):
        mk = mat_disco('Iris%d' % k, [(0.0, cor), (1.0, cor)], alfa=1.0, corte=.94)
        d = c.plano('Iris%d' % k, 2, 2, mk, Z_OLHO + .01)
        d.location.y = oy
        for eixo in (0, 1):
            c.animar(d, 'scale', lambda t, r=r: r * eo(c.cl((t - 1.85 - .02 * k) / .20)), eixo)
    # pupila preta + brilho
    mp = mat_disco('Pupila', [(0.0, PUPILA), (1.0, 0x05050A)], alfa=1.0, corte=.96)
    pup = c.plano('Pupila', 2, 2, mp, Z_OLHO + .02)
    pup.location.y = oy
    for eixo in (0, 1):
        c.animar(pup, 'scale', lambda t: IRIS_R * .34 * eo(c.cl((t - 1.98) / .18)), eixo)
    mg = mat_disco('Brilho', [(0.0, 0xFFFFFF), (1.0, 0xFFFFFF)], alfa=1.0, corte=.55)
    gl = c.plano('Brilho', 2, 2, mg, Z_OLHO + .03)
    gl.location.x, gl.location.y = ox - IRIS_R * .12, oy + IRIS_R * .13
    for eixo in (0, 1):
        c.animar(gl, 'scale', lambda t: IRIS_R * .10 * eo(c.cl((t - 2.05) / .15)), eixo)

    # 4) lágrima + espiral (2.10 - 2.45): sai do meio de baixo, desce e enrola
    cauda = []
    for i in range(17):
        u = i / 16
        cauda.append((ox - .19 - .46 * math.sin(u * 1.6), oy - EYE_DN - .46 * u))
    revelar('Lagrima', cauda, .082, 2.08, 2.26)
    esp = []
    cx0, cy0 = ox - .92, oy - EYE_DN - .44
    for i in range(25):
        u = i / 24
        a = u * 4.9
        r = .22 * (1 - .68 * u)
        esp.append((cx0 + math.cos(a) * r, cy0 - math.sin(a) * r))
    revelar('Espiral', esp, .070, 2.24, 2.45)

    # 5) gancho da direita (o "L" da referência)
    gancho = [(ox + .60, oy - EYE_DN * .55), (ox + .60, oy - EYE_DN - .44),
              (ox + .99, oy - EYE_DN - .44), (ox + 1.03, oy - EYE_DN - .33)]
    revelar('Gancho', gancho, .082, 2.20, 2.45)

    # CLARÃO no instante em que o olho abre
    brilho_anim('Clarao', OURO_B, 5.2, (0, oy, Z_OLHO + .6),
                lambda t: .22 * max(0.0, 1 - (t - ABRE) / .45) if t >= ABRE else 0.0)


def glifos_caindo(n=44):
    """Pequenos hieróglifos dourados caindo (a referência tem vários espalhados)."""
    m = c.mat_emissao('GlifoQueda', c.lin(OURO))
    for i in range(n):
        s = .07 + .05 * rnd(i, 51)
        x0 = -7.6 + rnd(i, 52) * 15.2
        y0 = 4.6 + rnd(i, 53) * 5.0
        v = .16 + .26 * rnd(i, 54)
        tipo = int(rnd(i, 55) * 5) % 5
        for j, pts in enumerate(glifo_pts(tipo, 0, 0, s)):
            cu = c.curva('Q%d_%d' % (i, j), pts, m, espessura=s * .09, z=Z_GLIFOS)
            c.animar(cu, 'location', lambda t, x=x0: x + .10 * math.sin(t * .5 + i), 0)
            c.animar(cu, 'location', lambda t, y=y0, v=v: y - v * t - .0, 1)
            fase = rnd(i, 56)
            c.visibilidade(cu, lambda t, f=fase, i=i: .30 * (.5 + .5 * math.sin(t * 1.4 + f)) * em(t, .3, 1.2))


def estrelas(n=26):
    """Brilhos de 4 pontas (como na referência)."""
    m = c.mat_emissao('Estrela', c.lin(OURO_B))
    for i in range(n):
        s = .022 + .030 * rnd(i, 61)
        x = -7.4 + rnd(i, 62) * 14.8
        y = -3.0 + rnd(i, 63) * 7.4
        if abs(x) < 3.4 and 0.0 < y < 1.0:      # nao cai em cima da placa
            continue
        for k in range(2):
            a = k * math.pi / 2
            pts = [(x - math.cos(a) * s, y - math.sin(a) * s), (x + math.cos(a) * s, y + math.sin(a) * s)]
            cu = c.curva('E%d_%d' % (i, k), [(px, py, 0) for px, py in pts], m,
                         espessura=s * .22, z=Z_ESTRELA)
            fase = rnd(i, 64)
            c.visibilidade(cu, lambda t, f=fase: .40 * (.5 + .5 * math.sin(t * 2.1 + f)) * em(t, .6, 1.6))


def montar():
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 12
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 1
    sc.cycles.diffuse_bounces = 0
    sc.cycles.glossy_bounces = 0
    sc.cycles.transmission_bounces = 0
    sc.cycles.transparent_max_bounces = 24
    sc.render.filter_size = 1.1

    c.camera_perspectiva(DIST, CY)

    ceu()
    feixes()
    raios()
    friso(4.28)
    friso(-4.28, 1)
    piramides()
    aura()
    glifos_caindo()
    estrelas()
    olho()
    return c.metadata('egipcio', SEED)


if __name__ == '__main__':
    c.executar('egipcio', montar)
