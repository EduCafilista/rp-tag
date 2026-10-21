"""Void "Queda Sem Fundo": buraco negro atras do titulo e queda num tunel.

Camera em PERSPECTIVA (comum.camera_perspectiva): o plano z = 0 e o quadro
16 x 9 de sempre (x -8..8, y -4.5..4.5) e o ponto de fuga fica no titulo do
VoidAvisoFx (cy = 0.45 h -> mundo y = +0.45). Tunel e estrelas vivem em z < 0
e andam em profundidade; nada chega perto da camera (z = 20).

Medidas do procedural: k = 9/240 unidade por "k" do Java; Rh = 24k = 0.9.
Zona do texto (1 a 4.8 s): x 25-75 %, y 40-58 % -> x -4..4, y -0.72..+0.9.
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import bpy
import math
import random
import comum as c
import perspectiva as P

SEED = 615
CY = .45
K = 9 / 240
RH = 24 * K
DIST = 20.0
CAM = P.parametros(DIST, CY)
NIGHT, DEEPB, VIO, CYAN, LAV = 0x06000F, 0x140A40, 0x7828C8, 0x78E6FF, 0xDCD2FF
TAU = 2 * math.pi


def eo(x):
    x = c.cl(x)
    return 1 - (1 - x) ** 3


def fr(x):
    return x - math.floor(x)


def rev(t):
    return eo((t - .1) / 1.2)


def na_zona_texto(x, y, z, t, folga=.15):
    """1 se o ponto (mundo) cai na zona do texto entre 1 e 4.8 s (com bordas suaves)."""
    if t < .8 or t > 5.0:
        return 0.
    u, v = P.projetar(CAM, x, y, z)
    dentro = (.25 - folga / 4 < u < .75 + folga / 4) and (.40 - folga / 8 < v < .58 + folga / 8)
    return (c.ss(.8, 1., t) * (1 - c.ss(4.8, 5., t))) if dentro else 0.


def coroa(nome, raio, largura, mat, z=0., n=96, onda=0., fase=0., a0=0., a1=TAU):
    """Coroa (anel plano) no plano XY local; onda = ondulacao 3-lobos relativa."""
    vs, fs = [], []
    arco = a1 - a0
    seg = max(2, int(n * arco / TAU))
    fechado = abs(arco - TAU) < 1e-6
    pontos = seg if fechado else seg + 1
    for i in range(pontos):
        a = a0 + arco * i / seg
        r = raio * (1 + onda * math.sin(3 * a + fase))
        for rr in (r - largura / 2, r + largura / 2):
            vs.append((math.cos(a) * rr, math.sin(a) * rr, 0))
    for i in range(seg):
        j = (i + 1) % pontos
        fs.append((2 * i, 2 * j, 2 * j + 1, 2 * i + 1))
    me = bpy.data.meshes.new(nome)
    me.from_pydata(vs, [], fs)
    me.materials.append(mat)
    ob = bpy.data.objects.new(nome, me)
    ob.location = (0, CY, z)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def disco(nome, raio, mat, z=0., n=64):
    vs = [(0, 0, 0)] + [(math.cos(TAU * i / n) * raio, math.sin(TAU * i / n) * raio, 0) for i in range(n)]
    fs = [(0, 1 + i, 1 + (i + 1) % n) for i in range(n)]
    me = bpy.data.meshes.new(nome)
    me.from_pydata(vs, [], fs)
    me.materials.append(mat)
    ob = bpy.data.objects.new(nome, me)
    ob.location = (0, CY, z)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def mat_preto_opaco(nome):
    """Preto que escreve profundidade (esconde a metade de tras do disco de acrecao)."""
    m = c.material(nome, 'DITHERED')
    out = c.no(m, 'ShaderNodeOutputMaterial', x=400)
    mix = c.no(m, 'ShaderNodeMixShader', x=200)
    tr = c.no(m, 'ShaderNodeBsdfTransparent')
    em = c.no(m, 'ShaderNodeEmission')
    em.inputs['Color'].default_value = (0, 0, 0, 1)
    oi = c.no(m, 'ShaderNodeObjectInfo')
    c.ligar(m, oi, 'Alpha', mix, 0)
    c.ligar(m, tr, 0, mix, 1)
    c.ligar(m, em, 0, mix, 2)
    c.ligar(m, mix, 0, out, 0)
    return m


def mat_nebulosa():
    """Ruido de BAIXA frequencia (barato no JPEG) entre azul profundo e violeta."""
    m = c.material('Nebulosa')
    nt = m.node_tree
    out = c.no(m, 'ShaderNodeOutputMaterial', x=900)
    mix = c.no(m, 'ShaderNodeMixShader', x=700)
    tr = c.no(m, 'ShaderNodeBsdfTransparent', x=500)
    em = c.no(m, 'ShaderNodeEmission', x=500)
    tc = c.no(m, 'ShaderNodeTexCoord', x=-600)
    ruido = c.no(m, 'ShaderNodeTexNoise', x=-400)
    ruido.inputs['Scale'].default_value = 1.6
    ruido.inputs['Detail'].default_value = 1.0
    ramp = c.no(m, 'ShaderNodeValToRGB', x=-150)
    ramp.color_ramp.elements[0].position = .35
    ramp.color_ramp.elements[0].color = c.lin(DEEPB)
    ramp.color_ramp.elements[1].position = .75
    ramp.color_ramp.elements[1].color = c.lin(VIO)
    oi = c.no(m, 'ShaderNodeObjectInfo', x=100)
    mul = c.no(m, 'ShaderNodeMath', x=300)
    mul.operation = 'MULTIPLY'
    sub = c.no(m, 'ShaderNodeMath', x=100)
    sub.operation = 'SUBTRACT'
    sub.use_clamp = True
    sub.inputs[1].default_value = .4
    c.ligar(m, tc, 'Object', ruido, 'Vector')
    c.ligar(m, ruido, 'Fac', ramp, 'Fac')
    c.ligar(m, ramp, 'Color', em, 'Color')
    c.ligar(m, ruido, 'Fac', sub, 0)
    c.ligar(m, sub, 0, mul, 0)
    c.ligar(m, oi, 'Alpha', mul, 1)
    c.ligar(m, mul, 0, mix, 0)
    c.ligar(m, tr, 0, mix, 1)
    c.ligar(m, em, 0, mix, 2)
    c.ligar(m, mix, 0, out, 0)
    return m


def mix_cor(a, b, u):
    ca, cb = c.lin(a), c.lin(b)
    return tuple(ca[i] + (cb[i] - ca[i]) * u for i in range(3)) + (1.,)


def montar():
    rng = random.Random(SEED)
    c.camera_perspectiva(DIST, CY)
    env_meio = lambda t: c.ss(0., .5, t)   # o fade de saida vem do exportar (marcadores.json)

    # ------------------------------------------------ espaco do End (imersao quase total)
    zf = -75.
    larg = 16 * (DIST - zf) / DIST * 1.1
    fundo = c.plano('EspacoEnd', larg, larg * 9 / 16, c.mat_emissao('Noite', c.lin(NIGHT)), zf)
    fundo.location.y = CY
    c.visibilidade(fundo, lambda t: .9 * env_meio(t))
    neb = c.plano('Nebulosa', larg * .98, larg * 9 / 16 * .98, mat_nebulosa(), zf + 1)
    neb.location.y = CY
    c.visibilidade(neb, lambda t: .55 * env_meio(t))
    c.animar(neb, 'rotation_euler', lambda t: .03 * t, 2)
    # o fundo do tunel e mais negro perto do buraco: a mensagem fica sobre escuro
    poco = c.brilho('PocoEscuro', NIGHT, 5.2 * (DIST - zf - 2) / DIST, (0, CY, zf + 2), .85)
    c.visibilidade(poco, lambda t: env_meio(t))

    # ------------------------------------------------ tunel de aneis (vem em profundidade)
    mats_tunel = {}
    for i in range(16):
        fase = rng.uniform(0, TAU)
        giro = rng.choice((-1, 1)) * rng.uniform(.08, .2)
        R = 7.5
        largura = .05 + .03 * rng.random()
        faixa = i % 4   # cor pela profundidade de partida: ciano no fundo, violeta perto
        if faixa not in mats_tunel:
            mats_tunel[faixa] = c.mat_emissao(f'Tunel{faixa}', mix_cor(CYAN, VIO, faixa / 3))
        ob = coroa(f'AnelTunel{i:02d}', R, largura, mats_tunel[faixa], 0., n=128, onda=.03, fase=fase)

        def u_de(t, i=i):
            return fr(i / 16 + t * .16)

        def z_de(t, u_de=u_de):
            return -70 + 76 * u_de(t) ** 1.5

        c.animar(ob, 'location', z_de, 2)
        c.animar(ob, 'rotation_euler', lambda t, g=giro, f=fase: f + g * t, 2)

        def alfa(t, u_de=u_de, z_de=z_de, R=R):
            u = u_de(t)
            a = math.sin(math.pi * u) * .55 * env_meio(t)
            # raio na tela (unidades do quadro): atenua enquanto passa sobre o texto
            rt = R * DIST / (DIST - z_de(t))
            if 1.2 < rt < 4.6 and .8 < t < 5.:
                a *= .2
            return a
        c.visibilidade(ob, alfa)

    # ------------------------------------------------ estrelas sugadas (riscos em profundidade)
    cores = [CYAN, LAV, VIO]
    mats_est = [c.mat_emissao(f'Estrela{j}', c.lin(cor)) for j, cor in enumerate(cores)]
    for i in range(90):
        ang = rng.uniform(0, TAU)
        R = math.sqrt(rng.uniform(1.5 ** 2, 9.5 ** 2))
        x, y = math.cos(ang) * R, CY + math.sin(ang) * R
        L = rng.uniform(2.5, 5.)
        esp = rng.uniform(.012, .028)
        ob = c.curva(f'Estrela{i:02d}', [(0, 0, 0), (0, 0, -L)], mats_est[i % 3], esp, 0)
        ob.location.x, ob.location.y = x, y
        semente, vel = rng.random(), .10 + .2 * rng.random()

        def rho(t, s=semente, v=vel):
            return fr(s - t * v)
        c.animar(ob, 'location', lambda t, rho=rho: -70 + 78 * rho(t), 2)

        def alfa(t, rho=rho, x=x, y=y):
            r = rho(t)
            a = c.ss(0, .15, r) * (1 - c.ss(.85, 1, r)) * .9 * env_meio(t)
            return a * (1 - na_zona_texto(x, y, -70 + 78 * r, t))
        c.visibilidade(ob, alfa)

    # ------------------------------------------------ estilhacos da realidade (por fora do texto)
    casca = c.mat_emissao('Estilhaco', c.lin(0x080014))
    borda = c.mat_emissao('EstilhacoBorda', c.lin(VIO))
    for i in range(12):
        tam = rng.uniform(.35, .8)
        pts = [(math.cos(a) * tam * rng.uniform(.6, 1.2), math.sin(a) * tam * rng.uniform(.6, 1.2), 0)
               for a in (rng.uniform(0, 2), rng.uniform(2.1, 4), rng.uniform(4.1, 6.2))]
        me = bpy.data.meshes.new(f'Estilhaco{i:02d}')
        me.from_pydata(pts, [], [(0, 1, 2)])
        me.materials.append(casca)
        ob = bpy.data.objects.new(me.name, me)
        bpy.context.scene.collection.objects.link(ob)
        aresta = c.curva(f'EstilhacoAresta{i:02d}', pts + [pts[0]], borda, .012, 0)
        aresta.parent = ob
        a0, s0, giro = rng.uniform(0, TAU), rng.random(), rng.uniform(-1.2, 1.2)

        def dist(t, s0=s0):
            return 11 * (.45 + .55 * fr(s0 - t * .04))

        def ang(t, a0=a0):
            return a0 + .25 * t
        c.animar(ob, 'location', lambda t, d=dist, a=ang: math.cos(a(t)) * d(t), 0)
        c.animar(ob, 'location', lambda t, d=dist, a=ang: CY + math.sin(a(t)) * d(t), 1)
        c.animar(ob, 'location', lambda t: .3, 2)
        c.animar(ob, 'rotation_euler', lambda t, g=giro: g * t, 2)

        def alfa(t, dist=dist):
            d = dist(t)
            return .9 * env_meio(t) * c.ss(5.0, 5.8, d) * (1 - c.ss(10.4, 11., d))
        c.visibilidade(ob, alfa)
        c.visibilidade(aresta, lambda t, alfa=alfa: .65 * alfa(t))

    # ------------------------------------------------ buraco negro + horizonte de eventos
    nucleo = disco('BuracoNucleo', RH, mat_preto_opaco('BuracoPreto'), 0.)
    for ax in (0, 1):
        c.animar(nucleo, 'scale', lambda t: max(1e-3, rev(t)), ax)
    c.visibilidade(nucleo, lambda t: 1. if rev(t) > .02 else 0.)
    sombra = c.brilho('BuracoBorda', 0x000000, RH * 1.6, (0, CY, -.01), .98)
    for ax in (0, 1):
        c.animar(sombra, 'scale', lambda t: max(1e-3, rev(t)), ax)
    nh = 32
    for nome, fator, cor, alfa, larg in [('Horizonte', 1.05, LAV, .95, .045), ('HorizonteCiano', 1.2, CYAN, .5, .03),
                                         ('HorizonteVioleta', 1.4, VIO, .3, .026)]:
        mat = c.mat_emissao(nome, c.lin(cor))
        mat_halo = c.mat_emissao(nome + 'Halo', c.lin(cor))
        for sgm in range(nh):
            a0, a1 = TAU * sgm / nh, TAU * (sgm + 1) / nh
            am = (a0 + a1) / 2
            for sufixo, lg, mt, k_a, z in (('', larg, mat, 1., .02), ('Halo', larg * 5, mat_halo, .18, .015)):
                ob = coroa(f'{nome}{sufixo}{sgm:02d}', RH * fator, lg, mt, z, n=nh * 4, a0=a0, a1=a1 + .002)
                for ax in (0, 1):
                    c.animar(ob, 'scale', lambda t: max(1e-3, rev(t)), ax)

                def alfa_h(t, am=am, r=RH * fator, a=alfa * k_a):
                    u, v = P.projetar(CAM, math.cos(am) * r * rev(t), CY + math.sin(am) * r * rev(t), 0)
                    sob_msg = (c.ss(.8, 1., t) * (1 - c.ss(4.8, 5., t))) if (.25 < u < .75 and .505 < v < .60) else 0.
                    return a * c.ss(.08, .3, t) * (1 - .85 * sob_msg)
                c.visibilidade(ob, alfa_h)

    # ------------------------------------------------ disco de acrecao 3D inclinado
    eixo = bpy.data.objects.new('DiscoAcrecao', None)
    bpy.context.scene.collection.objects.link(eixo)
    eixo.location = (0, CY, 0)
    c.animar(eixo, 'rotation_euler', lambda t: math.radians(-77), 0)
    c.animar(eixo, 'rotation_euler', lambda t: -.3 + .05 * math.sin(t), 2)
    for ax in (0, 1, 2):
        c.animar(eixo, 'scale', lambda t: max(1e-3, rev(t)), ax)
    nseg = 48
    for nome, raio, larg, cor, base in [('AcrecaoInterna', RH * 2.6, .16, LAV, .75), ('AcrecaoExterna', RH * 3.4, .12, CYAN, .35)]:
        mat = c.mat_emissao(nome, c.lin(cor))
        brilho_mat = c.mat_emissao(nome + 'Brilho', c.lin(cor))
        for s in range(nseg):
            a0, a1 = TAU * s / nseg, TAU * (s + 1) / nseg
            ob = coroa(f'{nome}{s:02d}', raio, larg, mat, 0., n=nseg * 4, a0=a0, a1=a1 + .002)
            ob.parent = eixo
            ob.location = (0, 0, 0)
            gl = coroa(f'{nome}Brilho{s:02d}', raio, larg * 4, brilho_mat, 0., n=nseg * 4, a0=a0, a1=a1 + .002)
            gl.parent = eixo
            gl.location = (0, 0, -.001)
            am = (a0 + a1) / 2
            lx, ly = math.cos(am) * raio, math.sin(am) * raio
            perto = 1.3 if ly < 0 else .7     # lado de baixo (rotacao -77 em X) vem para a camera

            def alfa(t, lx=lx, ly=ly, base=base, perto=perto):
                # posicao do segmento no mundo (rotacao X -77, depois Z), para medir a zona da mensagem
                th, ph = math.radians(-77), -.3 + .05 * math.sin(t)
                y1, z1 = ly * math.cos(th), ly * math.sin(th)
                s_ = rev(t)
                wx = (lx * math.cos(ph) - y1 * math.sin(ph)) * s_
                wy = CY + (lx * math.sin(ph) + y1 * math.cos(ph)) * s_
                wz = z1 * s_
                u, v = P.projetar(CAM, wx, wy, wz)
                frente_msg = c.ss(.8, 1., t) * (1 - c.ss(4.8, 5., t)) if (.25 < u < .75 and .505 < v < .60) else 0.
                return min(1., base * perto) * (1 - .88 * frente_msg) * c.ss(.1, .5, t)
            c.visibilidade(ob, alfa)
            c.visibilidade(gl, lambda t, alfa=alfa: .16 * alfa(t))

    # ------------------------------------------------ onda de choque (0 - 0,55 s)
    onda = coroa('OndaDeChoque', 1., .25, c.mat_emissao('Onda', c.lin(LAV)), .05)
    onda.shape_key_add(name='Basis')
    grande = onda.shape_key_add(name='Grande')
    for i, vtx in enumerate(grande.data):
        x, y, _ = onda.data.vertices[i].co
        r = math.hypot(x, y)
        externo = i % 2 == 1
        nr = 10.2 + (.015 if externo else -.015)
        vtx.co = (x / r * nr, y / r * nr, 0)
    c.assar(onda.data.shape_keys, 'key_blocks["Grande"].value', [eo(t / .55) for t in c.TEMPOS])
    c.visibilidade(onda, lambda t: .8 * (1 - eo(t / .55)) if t < .55 else 0.)

    return c.metadata('void', SEED)


if __name__ == '__main__':
    c.executar('void', montar)
