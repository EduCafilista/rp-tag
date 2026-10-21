"""Primitivas Blender compartilhadas; independente das cenas e tempos do MORTE."""
import bpy
import math
import json
from pathlib import Path
FPS, N = 24, 144
DUR = 6.
TEMPOS = [q/FPS for q in range(N)]
RAIZ = Path(__file__).resolve().parents[2]
import sys as _sys
_sys.path.insert(0, str(Path(__file__).resolve().parent))
import metas, perspectiva

def cl(x): return min(1., max(0.,x))
def ss(a,b,x):
    u=cl((x-a)/(b-a));return u*u*(3-2*u)
def lin(rgb):
    def ch(v):
        x=v/255.;return x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4
    return (ch((rgb>>16)&255),ch((rgb>>8)&255),ch(rgb&255),1.)

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
def configurar():
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



def animar(ob, caminho, fn, indice=0):
    assar(ob,caminho,[fn(t) for t in TEMPOS],indice)

def visibilidade(ob, fn):
    animar(ob,'color',fn,3)

def curva(nome, pontos, mat, espessura=.018, z=1):
    cu=bpy.data.curves.new(nome,'CURVE');cu.dimensions='3D';cu.bevel_depth=espessura;cu.bevel_resolution=2
    sp=cu.splines.new('POLY');sp.points.add(len(pontos)-1)
    for p,v in zip(sp.points,pontos):p.co=(*v,1)
    ob=bpy.data.objects.new(nome,cu);bpy.context.scene.collection.objects.link(ob)
    cu.materials.append(mat);ob.location.z=z
    return ob

def brilho(nome,cor,raio,pos,alfa=.5):
    mat=mat_brilho(nome);mat.node_tree.nodes['Emissao'].inputs['Color'].default_value=lin(cor)
    assar_no(mat,'A',[alfa]*N)
    ob=plano(nome,raio*2,raio*2,mat,pos[2]);ob.location.x,ob.location.y=pos[:2]
    return ob

def material_solido(nome,cor,emissao=.05,metal=.2):
    mat=material(nome,'DITHERED');out=no(mat,'ShaderNodeOutputMaterial')
    pb=no(mat,'ShaderNodeBsdfPrincipled');pb.inputs['Base Color'].default_value=lin(cor)
    pb.inputs['Metallic'].default_value=metal;pb.inputs['Roughness'].default_value=.3
    pb.inputs['Emission Color'].default_value=lin(cor);pb.inputs['Emission Strength'].default_value=emissao
    info=no(mat,'ShaderNodeObjectInfo');ligar(mat,info,'Alpha',pb,'Alpha');ligar(mat,pb,0,out,0)
    return mat

def metadata(fx,seed):
    return metas.metadata(fx,seed)

def camera_perspectiva(distancia=20.0,centro_y=0.0):
    """Troca a camera da cena por perspectiva; o plano z=0 continua cobrindo 16x9."""
    p=perspectiva.parametros(distancia,centro_y)
    co=bpy.context.scene.camera;cam=co.data
    cam.type='PERSP';cam.sensor_fit='HORIZONTAL';cam.sensor_width=p['sensor_width'];cam.lens=p['lens']
    cam.shift_x,cam.shift_y=p['shift_x'],p['shift_y'];cam.clip_start,cam.clip_end=.1,200
    co.location=p['location'];co.rotation_euler=(0,0,0)
    bpy.context.view_layer.update()
    return co

def renderizar(id,meta,quadros=None):
    sc=bpy.context.scene;out=RAIZ/'arte'/'render'/id;out.mkdir(parents=True,exist_ok=True)
    for marker in meta['markers']:sc.timeline_markers.new(marker['name'],frame=marker['frame'])
    (out/'marcadores.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    chosen=list(range(N)) if quadros is None else quadros
    samples=[]
    for q in chosen:
        if not 0<=q<N:raise ValueError('quadro fora de 0..143')
        sc.frame_set(q)
        samples.append({'frame':q,'visible_meshes':sum(ob.type=='MESH' and ob.color[3]>.01 for ob in sc.objects)})
        sc.render.filepath=str(out/f'{q:04d}.png');bpy.ops.render.render(write_still=True)
    report={'id':id,'objects':len(sc.objects),'camera':sc.camera.name,'frames':N,'fps':FPS,
        'resolution':[1280,720],'text_in_scene':any(o.type=='FONT' for o in sc.objects),
        'seed':meta['seed'],'rendered_frames':chosen,'samples':samples}
    (out/'inspecao.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

def executar(id,montar):
    import argparse,sys
    parser=argparse.ArgumentParser();parser.add_argument('--quadros');parser.add_argument('--salvar',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    configurar();meta=montar()
    if args.salvar:bpy.ops.wm.save_as_mainfile(filepath=str(RAIZ/'arte'/f'{id}.blend'))
    renderizar(id,meta,[int(q) for q in args.quadros.split(',')] if args.quadros else None)
