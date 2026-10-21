"""Divino "Ascensao Celestial": pilar de luz, halo runico, raios, asas e faiscas.

Camera ORTOGRAFICA de comum.configurar (x -8..8, y -4.5..4.5; z = ordem de
desenho). Titulo do DivinoAvisoFx em cy = 0.42 h -> mundo y = +0.72.
Medidas do procedural: k = 9/240 unidade; r1 = 62k (halo).
Zona do texto (1 a 4.8 s): x 25-75 %, y 37-55 % -> x -4..4, y +1.17..-0.45.
O miolo do halo fica apagado (brilho atras das letras <= 35 % do anel).
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import bpy
import math
import random
import comum as c

SEED = 733
CY = .72
K = 9 / 240
R1 = 62 * K
S = K * 1.9                      # escala da fonte do titulo (Java)
GOLD, CREAM, WHITE, DARK = 0xFFCD60, 0xFFF0BE, 0xFFFFFF, 0x0A0600
TAU = 2 * math.pi
DEG = math.pi / 180


def eo(x):
    x = c.cl(x)
    return 1 - (1 - x) ** 3


def fr(x):
    return x - math.floor(x)


def rev(t):
    return eo((t - .1) / 1.1)


def zona_texto(x, y, t):
    """1 dentro da zona do texto (mundo) entre 1 e 4.8 s, com bordas suaves no tempo."""
    if not (-4.2 < x < 4.2 and -.6 < y < 1.3):
        return 0.
    return c.ss(.8, 1., t) * (1 - c.ss(4.8, 5., t))


def _op(nt, tipo, *vals):
    n = nt.nodes.new('ShaderNodeMath')
    n.operation = tipo
    for i, v in enumerate(vals):
        if isinstance(v, (int, float)):
            n.inputs[i].default_value = v
        else:
            nt.links.new(v, n.inputs[i])
    return n.outputs[0]


def _saida(m, cor, alfa, forca=1.0):
    nt = m.node_tree
    oi = nt.nodes.new('ShaderNodeObjectInfo')
    a = _op(nt, 'MULTIPLY', alfa, oi.outputs['Alpha'])
    em = nt.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = c.lin(cor)
    em.inputs['Strength'].default_value = forca
    tr = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    nt.links.new(a, mix.inputs[0])
    nt.links.new(tr.outputs[0], mix.inputs[1])
    nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs[0])
    return m


def _comprimento(nt):
    tc = nt.nodes.new('ShaderNodeTexCoord')
    ln = nt.nodes.new('ShaderNodeVectorMath')
    ln.operation = 'LENGTH'
    nt.links.new(tc.outputs['Object'], ln.inputs[0])
    return tc, ln.outputs['Value']


def mat_radial_oco(nome, cor, ini, fim):
    """(1 - raio) com o miolo apagado: alfa sobe de 0 (raio ini) a 1 (raio fim)."""
    m = c.material(nome)
    nt = m.node_tree
    _, ln = _comprimento(nt)
    queda = _op(nt, 'SUBTRACT', 1., ln)
    queda.node.use_clamp = True
    sobe = _op(nt, 'DIVIDE', _op(nt, 'SUBTRACT', ln, ini), fim - ini)
    sobe.node.use_clamp = True
    return _saida(m, cor, _op(nt, 'MULTIPLY', queda, sobe))


def mat_raio(nome, cor, ha):
    """Raio no eixo +x local, perfil gaussiano no angulo (largura ha), miolo apagado, ponta some."""
    m = c.material(nome)
    nt = m.node_tree
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Object'], sep.inputs[0])
    ln = nt.nodes.new('ShaderNodeVectorMath')
    ln.operation = 'LENGTH'
    nt.links.new(tc.outputs['Object'], ln.inputs[0])
    raio = ln.outputs['Value']
    queda = _op(nt, 'SUBTRACT', 1., raio)
    queda.node.use_clamp = True
    sobe = _op(nt, 'DIVIDE', _op(nt, 'SUBTRACT', raio, .05), .17)
    sobe.node.use_clamp = True
    ang = _op(nt, 'DIVIDE', _op(nt, 'DIVIDE', sep.outputs['Y'], _op(nt, 'MAXIMUM', sep.outputs['X'], .001)), ha)
    g = _op(nt, 'EXPONENT', _op(nt, 'MULTIPLY', _op(nt, 'MULTIPLY', ang, ang), -1.))
    return _saida(m, cor, _op(nt, 'MULTIPLY', _op(nt, 'MULTIPLY', queda, sobe), g))


def mat_feixe(nome, cor, sigma):
    """Feixe vertical: perfil gaussiano em x (coordenadas do objeto) e ponta de baixo suave."""
    m = c.material(nome)
    nt = m.node_tree
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Object'], sep.inputs[0])
    dx = _op(nt, 'DIVIDE', sep.outputs['X'], sigma)
    g = _op(nt, 'EXPONENT', _op(nt, 'MULTIPLY', _op(nt, 'MULTIPLY', dx, dx), -1.))
    base = _op(nt, 'DIVIDE', _op(nt, 'ADD', sep.outputs['Y'], 1.), .5)   # y -1 (baixo) .. -0.5: some
    base.node.use_clamp = True
    return _saida(m, cor, _op(nt, 'MULTIPLY', g, base))


def mat_borda(nome, cor):
    """Degrade linear: 1 na borda da tela (y local 0) ate 0 a 2,7 para dentro (y local -1)."""
    m = c.material(nome)
    nt = m.node_tree
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Object'], sep.inputs[0])
    a = _op(nt, 'ADD', sep.outputs['Y'], 1.)
    a.node.use_clamp = True
    return _saida(m, cor, a)


def mesh(nome, vs, fs, mat, z):
    me = bpy.data.meshes.new(nome)
    me.from_pydata(vs, [], fs)
    me.materials.append(mat)
    ob = bpy.data.objects.new(nome, me)
    ob.location = (0, CY, z)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def coroa(nome, raio, larg, mat, z, n=128, a0=0., a1=TAU):
    vs, fs = [], []
    seg = max(2, int(n * (a1 - a0) / TAU))
    fechado = abs(a1 - a0 - TAU) < 1e-6
    pts = seg if fechado else seg + 1
    for i in range(pts):
        a = a0 + (a1 - a0) * i / seg
        for rr in (raio - larg / 2, raio + larg / 2):
            vs.append((math.cos(a) * rr, math.sin(a) * rr, 0))
    for i in range(seg):
        j = (i + 1) % pts
        fs.append((2 * i, 2 * j, 2 * j + 1, 2 * i + 1))
    return mesh(nome, vs, fs, mat, z)


def linha(nome, p0, p1, larg, mat, z):
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy) or 1.
    nx, ny = -dy / L * larg / 2, dx / L * larg / 2
    vs = [(p0[0] + nx, p0[1] + ny, 0), (p1[0] + nx, p1[1] + ny, 0), (p1[0] - nx, p1[1] - ny, 0), (p0[0] - nx, p0[1] - ny, 0)]
    return mesh(nome, vs, [(0, 1, 2, 3)], mat, z)


def escala(ob, fn):
    for ax in (0, 1):
        c.animar(ob, 'scale', lambda t, fn=fn: max(1e-3, fn(t)), ax)


def montar():
    rng = random.Random(SEED)

    # ------------------------------------------------ vinheta quente escura (so bordas)
    for i, (rot, larg, pos) in enumerate([(0, 16.4, (0, 4.5)), (math.pi, 16.4, (0, -4.5)),
                                          (math.pi / 2, 9.4, (-8, 0)), (-math.pi / 2, 9.4, (8, 0))]):
        ob = c.plano(f'Vinheta{i}', larg, 2 * 2.7, mat_borda(f'Vinheta{i}', DARK), .1)
        ob.location.x, ob.location.y = pos
        ob.rotation_euler.z = rot
        c.visibilidade(ob, lambda t: .55 * c.ss(0, .5, t))

    # ------------------------------------------------ clarao de abertura
    clarao = c.plano('Clarao', 17, 10, c.mat_emissao('Clarao', c.lin(WHITE)), 9.)
    clarao.location.y = 0
    c.visibilidade(clarao, lambda t: (1 - t / .3) * .45 if t < .3 else 0.)

    # ------------------------------------------------ raios (duas coroas, sentidos opostos)
    for nome, n, ha, cor, alfa, vel in [('RaioOuro', 16, .028, GOLD, .40, 4), ('RaioCreme', 12, .010, CREAM, .50, -6)]:
        mat = mat_raio(nome, cor, ha)
        for i in range(n):
            a = i * TAU / n + rng.uniform(-.04, .04)
            hb = ha * 3.2
            vs = [(0, 0, 0), (math.cos(hb), -math.sin(hb), 0), (math.cos(hb), math.sin(hb), 0)]
            ob = mesh(f'{nome}{i:02d}', vs, [(0, 1, 2)], mat, 1. if nome == 'RaioOuro' else 1.1)
            comp = 18.4 * (.9 if nome == 'RaioCreme' else 1.)
            escala(ob, lambda t, comp=comp: comp * (.55 + .45 * rev(t)) * (.7 + .3 * c.ss(0, .5, t)))
            c.animar(ob, 'rotation_euler', lambda t, v=vel, a=a: a + v * t * DEG, 2)
            pulso = rng.uniform(0, TAU)
            c.visibilidade(ob, lambda t, a=alfa, p=pulso: a * (.85 + .15 * math.sin(1.7 * t + p)))

    # ------------------------------------------------ pilar de luz (desce ate o topo do titulo)
    topo_titulo = CY + 5 * S
    for nome, meia, cor, alfa, z in [('PilarOuro', 26 * K, CREAM, .30, 2.), ('PilarNucleo', 5 * K, WHITE, .45, 2.1)]:
        ob = c.plano(nome, 2, 2, mat_feixe(nome, cor, .45), z)
        ob.location.x = 0

        def baixo(t):
            return 4.5 - (4.5 - topo_titulo) * eo(t / .6)
        abre = nome == 'PilarOuro'
        c.animar(ob, 'scale', lambda t, m=meia, abre=abre: m * max(.05, rev(t)) if abre else m, 0)
        c.animar(ob, 'scale', lambda t, b=baixo: (4.9 - b(t)) / 2, 1)
        c.animar(ob, 'location', lambda t, b=baixo: (4.9 + b(t)) / 2, 1)
        c.visibilidade(ob, lambda t, a=alfa: a * (1 - .5 * c.ss(4.6, 6., t)))
    poeira = c.mat_emissao('Poeira', c.lin(CREAM))
    for i in range(28):
        x0 = rng.uniform(-.7, .7)
        s0, v = rng.random(), rng.uniform(.08, .16)
        tam = rng.uniform(.018, .04)
        ob = mesh(f'Poeira{i:02d}', [(-tam, 0, 0), (0, tam, 0), (tam, 0, 0), (0, -tam, 0)], [(0, 1, 2, 3)], poeira, 2.2)
        c.animar(ob, 'location', lambda t, x0=x0, i=i: x0 + .08 * math.sin(.9 * t + i), 0)
        c.animar(ob, 'location', lambda t, s0=s0, v=v: topo_titulo + (4.6 - topo_titulo) * fr(s0 - t * v), 1)

        def alfa_p(t, s0=s0, v=v, i=i, x0=x0):
            u = fr(s0 - t * v)
            y = topo_titulo + (4.6 - topo_titulo) * u
            dentro = 1. if y > 4.5 - (4.5 - topo_titulo) * eo(t / .6) else 0.
            return dentro * .7 * c.ss(0, .12, u) * (1 - c.ss(.85, 1, u)) * (.6 + .4 * math.sin(4 * t + i)) \
                * (1 - c.ss(4.8, 5.6, t)) * (1 - .6 * zona_texto(x0, y, t))
        c.visibilidade(ob, alfa_p)

    # ------------------------------------------------ halo (miolo apagado)
    disco = c.plano('HaloDisco', 2, 2, mat_radial_oco('HaloDisco', GOLD, .55, .82), 3.)
    disco.location.y = CY
    escala(disco, lambda t: R1 * 1.15 * rev(t))
    c.visibilidade(disco, lambda t: .40)
    for nome, fr_r, larg, cor, alfa, vel, tracos in [('AnelHalo', 1., .045, GOLD, .9, 0, 0),
                                                     ('AnelInterno', .78, .028, CREAM, .55, 0, 0),
                                                     ('AnelTracejado', 1.28, .032, GOLD, .7, 5, 4)]:
        mat = c.mat_emissao(nome, c.lin(cor))
        if tracos:
            nseg = 24
            for sgm in range(nseg):
                a0 = TAU * sgm / nseg
                ob = coroa(f'{nome}{sgm:02d}', R1 * fr_r, larg, mat, 3.2, a0=a0, a1=a0 + TAU / nseg * .55)
                escala(ob, rev)
                c.animar(ob, 'rotation_euler', lambda t, v=vel: v * t * DEG, 2)
                c.visibilidade(ob, lambda t, a=alfa: a)
        else:
            ob = coroa(nome, R1 * fr_r, larg, mat, 3.2)
            escala(ob, rev)
            c.visibilidade(ob, lambda t, a=alfa: a)
        brilho = coroa(nome + 'Brilho', R1 * fr_r, larg * 5, c.mat_emissao(nome + 'Brilho', c.lin(cor)), 3.1)
        escala(brilho, rev)
        c.visibilidade(brilho, lambda t, a=alfa: .15 * a)
    # 48 tiques runicos
    tique = c.mat_emissao('Tique', c.lin(CREAM))
    eixo_t = bpy.data.objects.new('Tiques', None)
    bpy.context.scene.collection.objects.link(eixo_t)
    eixo_t.location = (0, CY, 3.3)
    escala(eixo_t, rev)
    c.animar(eixo_t, 'rotation_euler', lambda t: -10 * t * DEG, 2)
    for i in range(48):
        a = i * TAU / 48
        r0 = R1 * 1.28 + 2 * K
        r2 = r0 + (9 if i % 4 == 0 else 4) * K
        ob = linha(f'Tique{i:02d}', (math.cos(a) * r0, math.sin(a) * r0), (math.cos(a) * r2, math.sin(a) * r2), .028, tique, 0)
        ob.parent = eixo_t
        ob.location = (0, 0, 0)
        c.visibilidade(ob, lambda t: .7)
    # hexagrama duplo (baixa opacidade: atravessa o titulo)
    ouro = c.mat_emissao('Hexagrama', c.lin(GOLD))
    for q in range(2):
        eixo_h = bpy.data.objects.new(f'Hexagrama{q}', None)
        bpy.context.scene.collection.objects.link(eixo_h)
        eixo_h.location = (0, CY, 3.25)
        escala(eixo_h, rev)
        c.animar(eixo_h, 'rotation_euler', lambda t, q=q: (1 if q == 0 else -1) * t * 8 * DEG + q * 60 * DEG, 2)
        rr = R1 * .78
        for i in range(3):
            a0, a1 = i * TAU / 3, (i + 1) * TAU / 3
            ob = linha(f'Hexagrama{q}{i}', (math.cos(a0) * rr, math.sin(a0) * rr), (math.cos(a1) * rr, math.sin(a1) * rr), .022, ouro, 0)
            ob.parent = eixo_h
            ob.location = (0, 0, 0)
            c.visibilidade(ob, lambda t: .45 - .25 * c.ss(.8, 1., t) * (1 - c.ss(4.8, 5., t)))
    # 4 losangos orbitando
    branco = c.mat_emissao('Losango', c.lin(WHITE))
    for i in range(4):
        d = 3 * K
        ob = mesh(f'Losango{i}', [(-d, 0, 0), (0, d, 0), (d, 0, 0), (0, -d, 0)], [(0, 1, 2, 3)], branco, 3.4)
        c.animar(ob, 'location', lambda t, i=i: math.cos(t * 20 * DEG + i * TAU / 4) * R1 * rev(t), 0)
        c.animar(ob, 'location', lambda t, i=i: CY + math.sin(t * 20 * DEG + i * TAU / 4) * R1 * rev(t), 1)
        c.visibilidade(ob, lambda t: .9 * c.ss(.1, .3, t))

    # ------------------------------------------------ asas de luz (2 x 9 penas, atraso por pena)
    pena_mat = c.mat_brilho('Pena')
    pena_mat.node_tree.nodes['Emissao'].inputs['Color'].default_value = c.lin(GOLD)
    c.assar_no(pena_mat, 'A', [1.] * c.N)
    nerv = c.mat_emissao('Nervura', c.lin(CREAM))
    for lado in (-1, 1):
        for i in range(9):
            f = i / 8
            atraso = rng.uniform(0, .5)
            # pena no eixo +x local, raiz na origem: raiz -> meio (0,35, largura 0,12) -> ponta
            vs = [(0, 0, 0), (.35, .12, 0), (1, 0, 0), (.35, -.12, 0)]
            ob = mesh(f'Pena{lado:+d}{i}', vs, [(0, 1, 2, 3)], pena_mat, 2.5 + .01 * i)
            ner = linha(f'Nervura{lado:+d}{i}', (0, 0), (.85, 0), .018, nerv, 0)
            ner.parent = ob
            ner.location = (0, 0, .005)
            ob.location.x = lado * R1 * 1.05
            ob.location.y = CY

            def ang(t, f=f, i=i, lado=lado, atraso=atraso):
                th = (-62 + 90 * f) * DEG + 6 * DEG * math.sin(t * 2.2 + i * .35 + atraso)
                # Java: dx = lado*cos(th), dy (tela, para baixo) = sin(th) -> mundo: y = -sin(th)
                return math.atan2(-math.sin(th), lado * math.cos(th))
            c.animar(ob, 'rotation_euler', ang, 2)
            L = R1 * 2.1 * (1 - .45 * f)
            escala(ob, lambda t, L=L: L * rev(t))
            c.visibilidade(ob, lambda t: .55 * c.ss(.3, 1.1, t))
            c.visibilidade(ner, lambda t: .45 * c.ss(.3, 1.1, t))

    # ------------------------------------------------ ornamento sob o titulo (0,8 - 1,4 s)
    oy = CY - (5.5 * S + 6 * K)
    ol = 70 * K
    orn = c.mat_brilho('Ornamento')
    orn.node_tree.nodes['Emissao'].inputs['Color'].default_value = c.lin(GOLD)
    c.assar_no(orn, 'A', [1.] * c.N)
    traco = c.plano('OrnamentoLinha', 2, 2, orn, 4.)
    traco.location.y = oy
    c.animar(traco, 'scale', lambda t: max(1e-3, ol * rev(t)), 0)
    c.animar(traco, 'scale', lambda t: .02, 1)
    c.visibilidade(traco, lambda t: .9 * c.ss(.8, 1.4, t))
    d = 4 * K
    joia = mesh('OrnamentoLosango', [(-d, 0, 0), (0, d, 0), (d, 0, 0), (0, -d, 0)], [(0, 1, 2, 3)], branco, 4.1)
    joia.location.y = oy
    c.visibilidade(joia, lambda t: .95 * c.ss(.8, 1.4, t))

    # ------------------------------------------------ faiscas em estrela subindo (3 profundidades)
    faisca = c.mat_emissao('Faisca', c.lin(CREAM))
    for i in range(50):
        prof = i % 3
        x0 = rng.uniform(-8.3, 8.3)
        s0, v = rng.random(), .05 + .08 * rng.random()
        sz = (1.2 + 2.4 * rng.random()) * K * .9 * (.7, 1., 1.3)[prof]
        a, b = sz * .28, sz
        vs = [(-a, 0, 0), (0, b, 0), (a, 0, 0), (0, -b, 0), (-b, 0, 0), (0, a, 0), (b, 0, 0), (0, -a, 0)]
        ob = mesh(f'Faisca{i:02d}', vs, [(0, 1, 2, 3), (4, 5, 6, 7)], faisca, 5. + prof)

        def py(t, s0=s0, v=v):
            return -4.95 + 9.9 * fr(t * v + s0)
        c.animar(ob, 'location', lambda t, x0=x0, i=i: x0 + math.sin(t * 1.3 + i) * .45 * K * 12, 0)
        c.animar(ob, 'location', py, 1)

        def alfa_f(t, s0=s0, v=v, i=i, x0=x0, prof=prof):
            p = fr(t * v + s0)
            y = -4.95 + 9.9 * p
            a = (.5 + .5 * math.sin(t * 4 + i * 2.1)) * math.sin(math.pi * p) * (.6, .8, 1.)[prof]
            return a * c.ss(0, .4, t) * (1 - zona_texto(x0, y, t))
        c.visibilidade(ob, alfa_f)

    return c.metadata('divino', SEED)


if __name__ == '__main__':
    c.executar('divino', montar)
