"""Cena do aviso MORTE "Linha Reta" (RP Tag 4.2.0) — monta do zero e renderiza.

Uso (Blender 5.2, sem interface):
    blender -b --factory-startup -P arte/cenas/morte.py -- [--quadros 20,60,94,125]

Sem --quadros renderiza os 144 quadros em arte/render/morte/NNNN.png (RGBA)
e grava arte/render/morte/marcadores.json para o arte/exportar.py.

Coordenadas: a cena e desenhada em "unidades de GUI" com k = 1 (tela de
426.67 x 240, y PARA BAIXO), igual ao MorteAvisoFx.java, e convertida para
o mundo do Blender (16 x 9, y para cima). Assim as formulas do Java viram
as mesmas formulas aqui, mudando so o acabamento (luz, volume, brilho).

Tempos: copiados de src/main/java/dev/rptag/client/MorteTempos.java
(fonte unica — o teste MorteMarcadoresTest confere o fx.json contra ela).
"""
import bpy
import json
import math
import os
import sys

# ---------------------------------------------------------------- tempos (MorteTempos.java)
DUR, FLAT, ECG_END = 6.0, 3.9, 4.6
BEATS = [0.6, 1.5, 2.5, 3.7]
TEXTO_ENTRA, CINZAS, TEXTO_SAI = 0.5, DUR - 1.6, DUR - 1.0
FPS, N = 24, 144

# ---------------------------------------------------------------- cores (MorteAvisoFx.java)
BONE, BONE_D, RED, DRED, ASH, DARK = 0xE1DAC3, 0xA8A08C, 0xD21E28, 0x5A0008, 0x9696A0, 0x0A0000
RED_BORDA, ASH_VERM = 0xB4000A, 0x78140F
CRANIO = [
    "....########....",
    "..############..",
    ".##############.",
    ".##############.",
    "################",
    "##oooo####oooo##",
    "##oooo####oooo##",
    "##oooo####oooo##",
    "###oo######oo###",
    "#######oo#######",
    "######oooo######",
    "..############..",
    "..#.##.##.##.#..",
    "...#.#.##.#.#...",
]

# ---------------------------------------------------------------- tela
W, H = 240.0 * 16 / 9, 240.0          # tela em unidades de GUI (k = 1)
U = 9.0 / H                            # 1 unidade de GUI no mundo do Blender
CX, CY = W / 2, H / 2
PX = 2.3 * 1.3                         # pixel do cranio (30% maior que no FX procedural)
SX, SY = CX - 8 * PX, CY - 36 - 7 * PX          # 6 acima do FX procedural (cranio 30% maior)

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SAIDA = os.path.join(RAIZ, "arte", "render", "morte")


# ---------------------------------------------------------------- matematica do Java
def cl(x):
    return 0.0 if x < 0 else min(x, 1.0)


def ss(a, b, x):
    u = cl((x - a) / (b - a))
    return u * u * (3 - 2 * u)


def fr(x):
    return x - math.floor(x)


def rnd(i, s):
    return fr(math.sin(i * 12.9898 + s * 78.233) * 43758.547)


def pulso(t):
    s = 0.0
    for tb in BEATS:
        if t >= tb:
            s += math.exp(-(t - tb) * 9)
        if t >= tb + 0.22:
            s += 0.55 * math.exp(-(t - tb - 0.22) * 9)
    return min(1.2, s)


def ecg(x, amp=24.0):
    v, u0 = 0.0, 10.0
    for tb in BEATS:
        u = (x - W * tb / ECG_END) / u0
        if abs(u) > 1.3:
            continue
        v += (math.exp(-(u * 6) ** 2) - 0.3 * math.exp(-((u - 0.25) * 8) ** 2)
              + 0.2 * math.exp(-((u + 0.6) * 6) ** 2) + 0.3 * math.exp(-((u - 0.8) * 5) ** 2))
    return v * amp


def mundo(x, y, z=0.0):
    """GUI (y para baixo) -> mundo do Blender (y para cima)."""
    return ((x - CX) * U, (CY - y) * U, z)


def lin(c):
    """hex sRGB -> RGB linear (o Blender trabalha em linear)."""
    def ch(v):
        v /= 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return (ch((c >> 16) & 255), ch((c >> 8) & 255), ch(c & 255), 1.0)


def mix(c1, c2, u):
    a, b = lin(c1), lin(c2)
    return tuple(a[i] + (b[i] - a[i]) * u for i in range(4))


TEMPOS = [f / FPS for f in range(N)]


# ---------------------------------------------------------------- animacao (rapida, 1 chave por quadro)
def fcurve(idb, caminho, indice=0):
    ad = idb.animation_data or idb.animation_data_create()
    if ad.action is None:
        ad.action = bpy.data.actions.new(idb.name + "_anim")
    act = ad.action
    if hasattr(act, "fcurve_ensure_for_datablock"):
        return act.fcurve_ensure_for_datablock(idb, caminho, index=indice)
    return act.fcurves.new(caminho, index=indice)


def assar(idb, caminho, valores, indice=0):
    fc = fcurve(idb, caminho, indice)
    fc.keyframe_points.clear()
    fc.keyframe_points.add(len(valores))
    co = []
    for f, v in enumerate(valores):
        co += [float(f), float(v)]
    fc.keyframe_points.foreach_set("co", co)
    for kp in fc.keyframe_points:
        kp.interpolation = "LINEAR"
    fc.update()


def assar_no(mat, no, valores):
    assar(mat.node_tree, 'nodes["%s"].outputs[0].default_value' % no, valores)


# ---------------------------------------------------------------- cena
def limpar():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
        try:
            sc.render.engine = eng
            break
        except TypeError:
            continue
    sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = 1280, 720, 100
    sc.render.fps = FPS
    sc.frame_start, sc.frame_end = 0, N - 1
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.render.image_settings.color_depth = "8"
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    try:
        sc.eevee.taa_render_samples = 32
    except AttributeError:
        pass
    mundo_bl = bpy.data.worlds.new("Mundo")
    mundo_bl.use_nodes = True
    mundo_bl.node_tree.nodes["Background"].inputs[1].default_value = 0.0
    sc.world = mundo_bl

    cam = bpy.data.cameras.new("Camera")
    cam.type = "ORTHO"
    cam.ortho_scale = 16.0
    cam.clip_end = 100
    co = bpy.data.objects.new("Camera", cam)
    co.location = (0, 0, 30)
    sc.collection.objects.link(co)
    sc.camera = co

    sol = bpy.data.lights.new("Luz", "SUN")
    sol.energy = 3.0
    so = bpy.data.objects.new("Luz", sol)
    so.rotation_euler = (math.radians(35), math.radians(-30), 0)
    sc.collection.objects.link(so)
    return sc


def material(nome, metodo="BLENDED"):
    m = bpy.data.materials.new(nome)
    m.use_nodes = True
    try:
        m.surface_render_method = metodo
    except (AttributeError, TypeError):
        m.blend_method = "BLEND"
    m.node_tree.nodes.clear()
    return m


def no(m, tipo, nome=None, x=0, **ent):
    n = m.node_tree.nodes.new(tipo)
    if nome:
        n.name = n.label = nome
    n.location = (x, 0)
    for k, v in ent.items():
        n.inputs[k].default_value = v
    return n


def ligar(m, a, sa, b, sb):
    m.node_tree.links.new(a.outputs[sa], b.inputs[sb])


def valor(m, nome, v=0.0):
    n = no(m, "ShaderNodeValue", nome)
    n.outputs[0].default_value = v
    return n


def mat_emissao(nome, cor, alfa_do_objeto=True):
    """Cor chapada (emissao) com alfa por OBJETO (obj.color[3]) — um material serve N objetos."""
    m = material(nome)
    out = no(m, "ShaderNodeOutputMaterial", x=600)
    mixs = no(m, "ShaderNodeMixShader", x=400)
    tr = no(m, "ShaderNodeBsdfTransparent", x=200)
    em = no(m, "ShaderNodeEmission", "Emissao", x=200)
    em.inputs["Color"].default_value = cor
    em.inputs["Strength"].default_value = 1.0
    if alfa_do_objeto:
        oi = no(m, "ShaderNodeObjectInfo", x=0)
        ligar(m, oi, "Alpha", mixs, 0)
    ligar(m, tr, 0, mixs, 1)
    ligar(m, em, 0, mixs, 2)
    ligar(m, mixs, 0, out, 0)
    return m


def mat_brilho(nome):
    """Brilho radial (como o brilho() do Java): alfa A no centro -> 0 na borda. A e a cor sao animaveis."""
    m = material(nome)
    out = no(m, "ShaderNodeOutputMaterial", x=900)
    mixs = no(m, "ShaderNodeMixShader", x=700)
    tr = no(m, "ShaderNodeBsdfTransparent", x=500)
    em = no(m, "ShaderNodeEmission", "Emissao", x=500)
    tc = no(m, "ShaderNodeTexCoord", x=-400)
    vl = no(m, "ShaderNodeVectorMath", x=-200)
    vl.operation = "LENGTH"
    um = no(m, "ShaderNodeMath", x=0)
    um.operation = "SUBTRACT"
    um.inputs[0].default_value = 1.0
    um.use_clamp = True
    a = valor(m, "A", 0.0)
    mul = no(m, "ShaderNodeMath", x=200)
    mul.operation = "MULTIPLY"
    mul.use_clamp = True
    oi = no(m, "ShaderNodeObjectInfo", x=0)
    mul2 = no(m, "ShaderNodeMath", x=350)
    mul2.operation = "MULTIPLY"
    ligar(m, tc, "Object", vl, 0)
    ligar(m, vl, "Value", um, 1)
    ligar(m, um, 0, mul, 0)
    ligar(m, a, 0, mul, 1)
    ligar(m, mul, 0, mul2, 0)
    ligar(m, oi, "Alpha", mul2, 1)
    ligar(m, mul2, 0, mixs, 0)
    ligar(m, tr, 0, mixs, 1)
    ligar(m, em, 0, mixs, 2)
    ligar(m, mixs, 0, out, 0)
    return m


def plano(nome, larg, alt, mat, z):
    """Plano larg x alt (unidades do mundo) com as coordenadas de objeto em -1..1 (raio = 1)."""
    me = bpy.data.meshes.new(nome)
    me.from_pydata([(-1, -1, 0), (1, -1, 0), (1, 1, 0), (-1, 1, 0)], [], [(0, 1, 2, 3)])
    ob = bpy.data.objects.new(nome, me)
    ob.scale = (larg / 2, alt / 2, 1)
    ob.location.z = z
    me.materials.append(mat)
    bpy.context.scene.collection.objects.link(ob)
    return ob


# ---------------------------------------------------------------- 1) vinhetas (tunel preto + borda vermelha)
def vinheta(nome, cor, z, d_vals, a_vals):
    """A vinheta do Java (faixa de d unidades de GUI vinda das bordas, alfa a -> 0), so que
    ELIPTICA e suave (smoothstep): sem a "caixa" retangular das 4 faixas."""
    m = material(nome)
    out = no(m, "ShaderNodeOutputMaterial", x=1400)
    mixs = no(m, "ShaderNodeMixShader", x=1200)
    tr = no(m, "ShaderNodeBsdfTransparent", x=1000)
    em = no(m, "ShaderNodeEmission", x=1000)
    em.inputs["Color"].default_value = lin(cor)
    tc = no(m, "ShaderNodeTexCoord", x=-600)
    r = no(m, "ShaderNodeVectorMath", x=-400)
    r.operation = "LENGTH"
    ligar(m, tc, "Object", r, 0)
    r0 = valor(m, "R0", 0.5)
    a = valor(m, "A", 0.0)
    mr = no(m, "ShaderNodeMapRange", x=-100)
    mr.interpolation_type = "SMOOTHSTEP"
    ligar(m, r, "Value", mr, "Value")
    ligar(m, r0, 0, mr, "From Min")
    mr.inputs["From Max"].default_value = 1.3
    mul = no(m, "ShaderNodeMath", x=200)
    mul.operation = "MULTIPLY"
    mul.use_clamp = True
    ligar(m, mr, "Result", mul, 0)
    ligar(m, a, 0, mul, 1)
    ligar(m, mul, 0, mixs, 0)
    ligar(m, tr, 0, mixs, 1)
    ligar(m, em, 0, mixs, 2)
    ligar(m, mixs, 0, out, 0)
    plano(nome, 16.0, 9.0, m, z)
    assar_no(m, "R0", [max(0.0, 1.0 - d / (H / 2)) for d in d_vals])
    assar_no(m, "A", a_vals)


def montar_vinhetas():
    d_tunel = [H * (0.30 + 0.18 * ss(1.5, 4, t) * (1 - ss(DUR - 1.4, DUR - 0.2, t))) for t in TEMPOS]
    vinheta("Tunel", 0x000000, 1.0, d_tunel, [0.92] * N)
    d_borda = [H * 0.38] * N
    a_borda = [0.20 + 0.45 * pulso(t) for t in TEMPOS]
    vinheta("BordaVermelha", RED_BORDA, 1.1, d_borda, a_borda)


# ---------------------------------------------------------------- 2) cinzas caindo
def montar_cinzas():
    m_cinza = mat_emissao("Cinza", lin(ASH))
    m_verm = mat_emissao("CinzaVermelha", lin(ASH_VERM))
    for i in range(60):
        sz = (0.6 + 1.4 * rnd(i, 4)) * 1.1
        ob = plano("cinza%02d" % i, sz * U, sz * U, m_verm if i % 7 == 0 else m_cinza, 1.5 + i * 0.001)
        ob.rotation_euler.z = rnd(i, 5) * math.pi
        xs, ys, als, rs = [], [], [], []
        for t in TEMPOS:
            p = fr(t * (0.05 + 0.08 * rnd(i, 2)) + rnd(i, 3))
            x = rnd(i, 1) * W + math.sin(t * 0.8 + i) * 12
            y = H * (-0.02 + 1.04 * p)
            wx, wy, _ = mundo(x, y)
            xs.append(wx)
            ys.append(wy)
            als.append(0.6 * math.sin(math.pi * p))
            rs.append(rnd(i, 5) * math.pi + t * (0.6 + rnd(i, 6)))
        assar(ob, "location", xs, 0)
        assar(ob, "location", ys, 1)
        assar(ob, "color", als, 3)
        assar(ob, "rotation_euler", rs, 2)


# ---------------------------------------------------------------- 3) cranio de voxels
def cubo_chanfrado():
    me = bpy.data.meshes.new("Voxel")
    s = PX * U / 2
    v = [(x, y, z) for x in (-s, s) for y in (-s, s) for z in (-s, s)]
    f = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    me.from_pydata(v, [], f)
    me.validate()
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.bevel(bm, geom=list(bm.edges), offset=s * 0.18, segments=2, affect="EDGES")
    bm.to_mesh(me)
    bm.free()
    return me


def mat_osso(nome, cor, emissao=0.25):
    m = material(nome, "DITHERED")
    out = no(m, "ShaderNodeOutputMaterial", x=600)
    pb = no(m, "ShaderNodeBsdfPrincipled", x=300)
    pb.inputs["Base Color"].default_value = cor
    pb.inputs["Roughness"].default_value = 0.55
    pb.inputs["Emission Color"].default_value = cor
    pb.inputs["Emission Strength"].default_value = emissao
    oi = no(m, "ShaderNodeObjectInfo", x=0)
    ligar(m, oi, "Alpha", pb, "Alpha")
    ligar(m, pb, 0, out, 0)
    return m


def montar_cranio():
    pai = bpy.data.objects.new("Cranio", None)
    pai.location = mundo(SX + 8 * PX, SY + 7 * PX, 2.5)
    bpy.context.scene.collection.objects.link(pai)
    assar(pai, "rotation_euler", [math.radians(14)] * N, 0)
    assar(pai, "rotation_euler", [math.radians(9) * math.sin(t * 0.9) for t in TEMPOS], 1)

    me = cubo_chanfrado()
    m_osso = mat_osso("Osso", lin(BONE))
    m_osso_d = mat_osso("OssoEscuro", lin(BONE_D))
    m_orbita = mat_osso("Orbita", lin(DARK), 0.0)
    me.materials.append(m_osso)
    eye_a = [(0.55 + 0.45 * pulso(t)) if t < FLAT else 0.55 * (1 - ss(FLAT, FLAT + 0.8, t)) for t in TEMPOS]

    m_brasa = material("Brasa", "DITHERED")
    out = no(m_brasa, "ShaderNodeOutputMaterial", x=600)
    em = no(m_brasa, "ShaderNodeEmission", x=300)
    em.inputs["Color"].default_value = lin(RED)
    forca = valor(m_brasa, "Forca", 1.0)
    ligar(m_brasa, forca, 0, em, "Strength")
    mixs = no(m_brasa, "ShaderNodeMixShader", x=450)
    tr = no(m_brasa, "ShaderNodeBsdfTransparent", x=300)
    oi = no(m_brasa, "ShaderNodeObjectInfo", x=0)
    ligar(m_brasa, oi, "Alpha", mixs, 0)
    ligar(m_brasa, tr, 0, mixs, 1)
    ligar(m_brasa, em, 0, mixs, 2)
    ligar(m_brasa, mixs, 0, out, 0)
    assar_no(m_brasa, "Forca", [0.15 + 2.8 * a for a in eye_a])

    for r, linha in enumerate(CRANIO):
        for c, ch in enumerate(linha):
            if ch == ".":
                continue
            idx = r * 16 + c
            brasa = ch == "o" and (r in (6, 7)) and (4 <= c <= 5 or 10 <= c <= 11)
            if ch == "o":
                mat = m_brasa if brasa else m_orbita
                fundo = 0.3 if not brasa else 0.0          # orbita afundada (volume)
            else:
                borda = c + 1 >= 16 or linha[c + 1] == "." or r + 1 >= len(CRANIO) or CRANIO[r + 1][c] == "."
                mat = m_osso_d if borda else m_osso
                fundo = 0.0
            ob = bpy.data.objects.new("voxel_%d_%d" % (r, c), me)
            ob.parent = pai
            bpy.context.scene.collection.objects.link(ob)
            ob.material_slots[0].link = "OBJECT"           # malha compartilhada, material por voxel
            ob.material_slots[0].material = mat
            base = ((c - 7.5) * PX * U, (6.5 - r) * PX * U, -fundo * PX * U)
            ta, td = 0.4 + 0.9 * rnd(idx, 1), DUR - 1.6 + 1.0 * rnd(idx, 2)
            ang = rnd(idx, 7) * 2 * math.pi
            xs, ys, zs, als, ss_ = [], [], [], [], []
            for t in TEMPOS:
                a = ss(ta, ta + 0.25, t)
                # forma do PO: cada voxel chega de longe, girando pro lugar
                dist = (1 - a) * 18 * U
                dx, dy = math.cos(ang) * dist, math.sin(ang) * dist
                if t > td:                                   # vira CINZA subindo
                    a *= 1 - ss(td, td + 0.7, t)
                    dy += (t - td) * 25 * (0.5 + rnd(idx, 3)) * U
                    dx += math.sin(t * 3 + idx) * 2 * (t - td) * U
                xs.append(base[0] + dx)
                ys.append(base[1] + dy)
                zs.append(base[2])
                als.append(a * (0.95 if ch != "o" else 1.0))
                ss_.append(0.35 + 0.65 * a)
            assar(ob, "location", xs, 0)
            assar(ob, "location", ys, 1)
            assar(ob, "location", zs, 2)
            assar(ob, "color", als, 3)
            for k in range(3):
                assar(ob, "scale", ss_, k)

    # brilho dos olhos (como o brilho() do Java)
    m_olho = mat_brilho("BrilhoOlhos")
    m_olho.node_tree.nodes["Emissao"].inputs["Color"].default_value = lin(RED)
    assar_no(m_olho, "A", [0.55 * eye_a[f] * ss(1.0, 1.4, t) * (1 - ss(DUR - 1.4, DUR - 0.6, t))
                           for f, t in enumerate(TEMPOS)])
    for e in (0, 1):
        ob = plano("olho%d" % e, 10 * PX * U, 10 * PX * U, m_olho, 0)
        ob.parent = pai
        ob.location = (((5 if e == 0 else 11) - 7.5) * PX * U, (6.5 - 7) * PX * U, PX * U)


# ---------------------------------------------------------------- 4) monitor cardiaco
def montar_ecg():
    yb = H * 0.80
    pts = []
    x = 0.0
    while x <= W:
        pts.append(mundo(x, yb - ecg(x), 4.0))
        x += 1.0
    cor_ecg = [ss(FLAT, FLAT + 0.6, t) for t in TEMPOS]
    xh = [W * cl(t / ECG_END) for t in TEMPOS]

    def curva(nome, espessura, alfa_max):
        cu = bpy.data.curves.new(nome, "CURVE")
        cu.dimensions = "3D"
        cu.bevel_depth = espessura * U
        cu.bevel_resolution = 2
        sp = cu.splines.new("POLY")
        sp.points.add(len(pts) - 1)
        for i, p in enumerate(pts):
            sp.points[i].co = (p[0], p[1], p[2], 1.0)
        cu.bevel_factor_mapping_end = "SEGMENTS"
        ob = bpy.data.objects.new(nome, cu)
        bpy.context.scene.collection.objects.link(ob)
        m = material(nome + "Mat")
        out = no(m, "ShaderNodeOutputMaterial", x=1200)
        mixs = no(m, "ShaderNodeMixShader", x=1000)
        tr = no(m, "ShaderNodeBsdfTransparent", x=800)
        em = no(m, "ShaderNodeEmission", x=800)
        em.inputs["Strength"].default_value = 1.0
        mc = no(m, "ShaderNodeMix", x=600)
        mc.data_type = "RGBA"
        u = valor(m, "U", 0.0)
        ligar(m, u, 0, mc, "Factor")
        mc.inputs[6].default_value = lin(RED)
        mc.inputs[7].default_value = lin(BONE)
        ligar(m, mc, 2, em, "Color")
        # alfa = (0.25 + 0.75 * clamp(1 - (xh - x)/(0.55 W))) * alfa_max
        tc = no(m, "ShaderNodeTexCoord", x=-600)
        sep = no(m, "ShaderNodeSeparateXYZ", x=-400)
        ligar(m, tc, "Object", sep, 0)
        xg = no(m, "ShaderNodeMath", x=-200)
        xg.operation = "MULTIPLY_ADD"
        ligar(m, sep, "X", xg, 0)
        xg.inputs[1].default_value = 1 / U
        xg.inputs[2].default_value = CX
        cab = valor(m, "XH", 0.0)
        dif = no(m, "ShaderNodeMath", x=0)
        dif.operation = "SUBTRACT"
        ligar(m, cab, 0, dif, 0)
        ligar(m, xg, 0, dif, 1)
        dv = no(m, "ShaderNodeMath", x=150)
        dv.operation = "DIVIDE"
        ligar(m, dif, 0, dv, 0)
        dv.inputs[1].default_value = 0.55 * W
        um = no(m, "ShaderNodeMath", x=300)
        um.operation = "SUBTRACT"
        um.use_clamp = True
        um.inputs[0].default_value = 1.0
        ligar(m, dv, 0, um, 1)
        ma = no(m, "ShaderNodeMath", x=450)
        ma.operation = "MULTIPLY_ADD"
        ligar(m, um, 0, ma, 0)
        ma.inputs[1].default_value = 0.75 * alfa_max
        ma.inputs[2].default_value = 0.25 * alfa_max
        ligar(m, ma, 0, mixs, 0)
        ligar(m, tr, 0, mixs, 1)
        ligar(m, em, 0, mixs, 2)
        ligar(m, mixs, 0, out, 0)
        cu.materials.append(m)
        assar(cu, "bevel_factor_end", [cl(t / ECG_END) for t in TEMPOS])
        assar_no(m, "U", cor_ecg)
        assar_no(m, "XH", xh)

    curva("ECG", 0.55, 1.0)
    curva("ECGBrilho", 2.6, 0.22)

    # cabeca do traço: ponto branco + brilho na cor do ECG (some depois do fim)
    vis = [1.0 if t < ECG_END + 0.3 else 0.0 for t in TEMPOS]
    m_pt = mat_brilho("PontoECG")                      # disco de borda macia (A alto = miolo cheio)
    m_pt.node_tree.nodes["Emissao"].inputs["Color"].default_value = (1, 1, 1, 1)
    assar_no(m_pt, "A", [3.0] * N)
    pt = plano("PontoECG", 5 * U, 5 * U, m_pt, 4.2)
    m_gl = mat_brilho("BrilhoECG")
    gl = plano("BrilhoECG", 16 * U, 16 * U, m_gl, 4.1)
    hx, hy = [], []
    for f, t in enumerate(TEMPOS):
        wx, wy, _ = mundo(xh[f], yb - ecg(xh[f]))
        hx.append(wx)
        hy.append(wy)
    for ob in (pt, gl):
        assar(ob, "location", hx, 0)
        assar(ob, "location", hy, 1)
    assar(pt, "color", [0.95 * v for v in vis], 3)
    assar(gl, "color", vis, 3)
    assar_no(m_gl, "A", [0.6] * N)
    cores = [mix(RED, BONE, u) for u in cor_ecg]
    for k in range(3):
        assar(m_gl.node_tree, 'nodes["Emissao"].inputs[0].default_value', [c[k] for c in cores], k)


# ---------------------------------------------------------------- marcadores + render
def marcadores():
    lista = [("texto_entra", TEXTO_ENTRA)] + [("batida", b) for b in BEATS] + \
            [("linha_reta", FLAT), ("cinzas", CINZAS), ("texto_sai", TEXTO_SAI)]
    sc = bpy.context.scene
    sc.timeline_markers.clear()
    saida = []
    for nome, seg in lista:
        f = int(round(seg * FPS))
        sc.timeline_markers.new(nome, frame=f)
        saida.append({"name": nome, "frame": f})
    return saida


def montar():
    limpar()
    montar_vinhetas()
    montar_cinzas()
    montar_cranio()
    montar_ecg()
    return marcadores()


def renderizar(quadros=None):
    sc = bpy.context.scene
    os.makedirs(SAIDA, exist_ok=True)
    lista = quadros if quadros else range(N)
    for f in lista:
        sc.frame_set(f)
        sc.render.filepath = os.path.join(SAIDA, "%04d.png" % f)
        bpy.ops.render.render(write_still=True)
    meta = {
        "id": "morte",
        "markers": marcadores(),
        # entrada/saida suave de TUDO (o env do Java): o exportar.py multiplica o alfa
        "fade": {"in": [0.0, 0.5], "out": [DUR - 1.0, DUR]},
    }
    with open(os.path.join(SAIDA, "marcadores.json"), "w", encoding="utf-8") as fp:
        json.dump(meta, fp, indent=2)


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    quadros = None
    if "--quadros" in args:
        quadros = [int(v) for v in args[args.index("--quadros") + 1].split(",")]
    montar()
    if "--salvar" in args:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(RAIZ, "arte", "morte.blend"))
    renderizar(quadros)
