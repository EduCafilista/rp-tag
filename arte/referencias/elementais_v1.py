"""Cenas de Inferno e Gelo: previews Blender reproduziveis, 720p/24fps/6s.
Usa as primitivas de material/camera do piloto MORTE; nenhum texto e renderizado.
"""
import os, sys, math, json, random
sys.path.insert(0, os.path.dirname(__file__))
import morte as m
import bpy
T = [i / 24 for i in range(144)]

def curve(name, pts, mat, radius=.025, z=1):
    cu=bpy.data.curves.new(name, 'CURVE'); cu.dimensions='3D'; cu.bevel_depth=radius; cu.bevel_resolution=2
    sp=cu.splines.new('POLY'); sp.points.add(len(pts)-1)
    for p, (x,y) in zip(sp.points,pts): p.co=(x,y,0,1)
    ob=bpy.data.objects.new(name,cu); bpy.context.scene.collection.objects.link(ob); ob.location.z=z; cu.materials.append(mat)
    return ob

def grow(ob, delay=.1, end=1.3):
    for axis in range(3): m.assar(ob,'scale',[max(.001,m.ss(delay,end,t)) for t in T],axis)

def glow(name,color,size,xy,z=0,alpha=.4):
    mat=m.mat_brilho(name); mat.node_tree.nodes['Emissao'].inputs['Color'].default_value=m.lin(color)
    m.assar_no(mat,'A',[alpha]*144)
    ob=m.plano(name,size,size,mat,z); ob.location.x,ob.location.y=xy
    return ob

def flame(name,x,y,w,h,mat,phase):
    # Tubo de secciones irregulares: silueta curva y volumen real, sin simulacion externa.
    verts=[]; faces=[]; rings=9; sides=7
    for j in range(rings):
        u=j/(rings-1); r=w*(1-u)**.8+.002
        cx=.35*w*math.sin(u*5+phase)*u
        for k in range(sides):
            a=k*math.tau/sides; verts.append((cx+r*math.cos(a),u*h,r*.5*math.sin(a)))
    for j in range(rings-1):
        for k in range(sides):
            a=j*sides+k; b=j*sides+(k+1)%sides; faces.append((a,b,b+sides,a+sides))
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.materials.append(mat)
    ob=bpy.data.objects.new(name,mesh);bpy.context.scene.collection.objects.link(ob);ob.location=(x,y,2)
    m.assar(ob,'scale',[.65+.35*m.ss(0,.5,t)+.12*math.sin(t*9+phase) for t in T],1)
    m.assar(ob,'rotation_euler',[.12*math.sin(t*6+phase) for t in T],2)
    return ob

def inferno():
    rng=random.Random(482)
    orange=m.mat_emissao('Brasa',m.lin(0xFF8C1E)); yellow=m.mat_emissao('Nucleo',m.lin(0xFFD23C))
    red=m.mat_osso('FogoVermelho',m.lin(0xFF4600),1.0)
    # Variacao de temperatura na superficie 3D: ruido animado alimenta a rampa incandescente.
    nt=red.node_tree; pb=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED')
    tex=nt.nodes.new('ShaderNodeTexNoise');tex.noise_dimensions='4D';tex.inputs['Scale'].default_value=3.5
    ramp=nt.nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position=.15;ramp.color_ramp.elements[0].color=m.lin(0x9B1003)
    ramp.color_ramp.elements[1].position=.82;ramp.color_ramp.elements[1].color=m.lin(0xFFD23C)
    nt.links.new(tex.outputs['Fac'],ramp.inputs[0]);nt.links.new(ramp.outputs[0],pb.inputs['Base Color']);nt.links.new(ramp.outputs[0],pb.inputs['Emission Color'])
    m.assar(nt,'nodes["'+tex.name+'"].inputs["W"].default_value',[t*.7 for t in T])
    m.vinheta('Calor',0xB83208,-2,[78]*144,[.6]*144)
    for i in range(40):
        x=-8.6+i*.44
        h=rng.uniform(1.3,3.1)
        flame('Chama_%02d'%i,x,-4.7,.25,h,red,i)
        core=flame('Nucleo_%02d'%i,x,-4.65,.105,h*.65,yellow,i);core.location.z=2.5
    for i in range(8):
        pts=[]
        for j in range(60):
            u=j/59;a=i*math.tau/8+u*3.6;r=.55+u*2.1
            pts.append((math.cos(a)*r,math.sin(a)*r*.66+.45))
        ob=curve('Espiral_%d'%i,pts,orange,.018,-.5)
        ob.color[3]=.24
        m.assar(ob,'rotation_euler',[t*.58 for t in T],2);grow(ob)
    for i in range(80):
        ob=glow('Brasa_%03d'%i,0xFFB638,rng.uniform(.04,.15),(0,0),3,.9)
        phase=rng.random();x=rng.uniform(-8,8);speed=rng.uniform(.12,.32)
        m.assar(ob,'location',[x+.22*math.sin(t*3+i) for t in T],0)
        m.assar(ob,'location',[-4.8+10*((phase+t*speed)%1) for t in T],1)
    for i in range(5):
        ob=curve('Meteoro_%d'%i,[(0,0),(-.9,.55),(-1.8,1.1)],yellow,.035,4)
        m.assar(ob,'location',[-10+23*((t*.28+i*.21)%1) for t in T],0)
        m.assar(ob,'location',[6-12*((t*.28+i*.21)%1) for t in T],1)
    glow('Forno',0xFF4600,19,(0,-6),-1,.6)

def gelo():
    rng=random.Random(618)
    ice=m.mat_osso('GeloFacetado',m.lin(0xBEEBFF),.16)
    light=m.mat_emissao('GeadaLuminosa',m.lin(0xD2F0FF))
    m.vinheta('Frio',0x5AA0DC,-2,[82]*144,[.55]*144)
    for side in [-1,1]:
        for i in range(21):
            x=-8.4+i*.84;h=rng.uniform(.65,2.0)
            bpy.ops.mesh.primitive_cone_add(vertices=5,radius1=.22,radius2=0,depth=h,location=(x,side*(4.6-h*.23),1))
            ob=bpy.context.object;ob.name='Cristal_%d_%d'%(side,i);ob.rotation_euler=(side*math.pi/2,.13*math.sin(i),.13*math.cos(i));ob.data.materials.append(ice)
            grow(ob,.1+i*.016,1.1+i*.035)
    def branch(name,x,y,a,length,depth,delay):
        end=(x+math.cos(a)*length,y+math.sin(a)*length)
        ob=curve(name,[(0,0),(end[0]-x,end[1]-y)],light,.009*depth,2);ob.location.x=x;ob.location.y=y
        m.assar(ob.data,'bevel_factor_end',[m.ss(delay,delay+.8,t) for t in T])
        if depth>1:
            for u in [.45,.8]:
                for sign in [-1,1]:branch(name+'b',x+(end[0]-x)*u,y+(end[1]-y)*u,a+sign*math.pi/3,length*.42,depth-1,delay+.25)
    for side in [-1,1]:
        for i in range(14):branch('Dendrito',-8+i*1.25+rng.uniform(-.2,.2),side*4.55,-side*math.pi/2+rng.uniform(-.25,.25),rng.uniform(.7,1.6),3,.12+i*.04)
    root=bpy.data.objects.new('FlocoGiratorio',None);bpy.context.scene.collection.objects.link(root);root.location.y=.45
    for i in range(6):
        a=i*math.tau/6
        ob=curve('BracoFloco',[(0,0),(2.1*math.cos(a),2.1*math.sin(a))],light,.025,-.7);ob.parent=root;ob.color[3]=.28
        for r in [.8,1.4]:
            for sign in [-1,1]:
                x,y=r*math.cos(a),r*math.sin(a);b=a+math.pi+sign*math.pi/3
                ob=curve('RamoFloco',[(x,y),(x+.5*math.cos(b),y+.5*math.sin(b))],light,.015,-.7);ob.parent=root;ob.color[3]=.28
    m.assar(root,'rotation_euler',[t*.15 for t in T],2);grow(root,.1,1.1)
    for i in range(70):
        ob=glow('Neve_%03d'%i,0xD2F0FF,rng.uniform(.035,.10),(0,0),3,.75)
        phase=rng.random();x=rng.uniform(-10,10)
        m.assar(ob,'location',[x-t*.45 for t in T],0);m.assar(ob,'location',[5-10*((phase+t*.13)%1) for t in T],1)
    for i in range(12):
        a=i*math.tau/12+.15;pts=[(0,0)]
        for j in range(1,5):pts.append((j*.55*math.cos(a)+rng.uniform(-.12,.12),j*.55*math.sin(a)+rng.uniform(-.12,.12)))
        ob=curve('Trinca',pts,light,.009,-.9);m.assar(ob,'color',[.28*(1-m.ss(.7,1.7,t)) if t>=.12 else 0 for t in T],3)

def main(fx):
    args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    m.limpar(); {'inferno':inferno,'gelo':gelo}[fx]()
    sc=bpy.context.scene
    out=os.path.join(m.RAIZ,'arte','render',fx);os.makedirs(out,exist_ok=True)
    events=[('impacto',0 if fx=='inferno' else .12),('texto_entra',.5),('texto_sai',4.8)]
    if fx=='gelo':events += [('sino',t) for t in [.5,1.2,2.,3.]]
    else:events += [('crepitar',.5+.45*i) for i in range(11)]
    markers=[{'name':n,'frame':round(t*24)} for n,t in events]
    for e in markers:sc.timeline_markers.new(e['name'],frame=e['frame'])
    meta={'id':fx,'markers':markers,'fade':{'in':[0,.35 if fx=='inferno' else .4],'out':[4.8,6]},'preview':{'color':[255,140,30] if fx=='inferno' else [210,240,255],'message':'A FORNALHA DESPERTOU' if fx=='inferno' else 'O FRIO TOMOU CONTA','title_y':.45,'message_y':.45+25/240}}
    with open(os.path.join(out,'marcadores.json'),'w',encoding='utf-8') as fp:json.dump(meta,fp,indent=2)
    frames=[int(q) for q in args[args.index('--quadros')+1].split(',')] if '--quadros' in args else range(144)
    for f in frames:
        sc.frame_set(f);sc.render.filepath=os.path.join(out,'%04d.png'%f);bpy.ops.render.render(write_still=True)
    inventory={'id':fx,'objects':len(sc.objects),'camera':sc.camera.name,'frames':144,'fps':24,'resolution':[1280,720],'text_in_scene':any(o.type=='FONT' for o in sc.objects)}
    with open(os.path.join(out,'inspecao.json'),'w') as fp:json.dump(inventory,fp,indent=2)

if __name__=='__main__':main('inferno')

