"""Geada Eterna hibrida: vidro estilhacado, geada que cresce das bordas, cristais facetados.

Coordenadas do mundo: tela 16 x 9 (x -8..8, y -4.5..4.5). Titulo do GeloAvisoFx em
cy = 0.45 h -> y = +0.45. Zona de leitura (2-4.8 s): x -4.8..4.8, y -1.44..+1.62.
"""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
import math
import random
import comum as c

SEED=618
TITULO_Y=.45


def _op(nt,kind,*values):
    ob=nt.nodes.new('ShaderNodeMath');ob.operation=kind
    for i,v in enumerate(values):
        if isinstance(v,(int,float)):ob.inputs[i].default_value=v
        else:nt.links.new(v,ob.inputs[i])
    return ob.outputs[0]


def _mix_out(mat,alfa,cor_socket=None,cor=None,forca=1.):
    nt=mat.node_tree;ln=nt.links
    em=nt.nodes.new('ShaderNodeEmission');em.inputs[1].default_value=forca
    if cor_socket is not None:ln.new(cor_socket,em.inputs[0])
    else:em.inputs[0].default_value=c.lin(cor)
    info=nt.nodes.new('ShaderNodeObjectInfo')
    tr=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');out=nt.nodes.new('ShaderNodeOutputMaterial')
    ln.new(_op(nt,'MULTIPLY',alfa,info.outputs['Alpha']),mix.inputs[0]);ln.new(tr.outputs[0],mix.inputs[1]);ln.new(em.outputs[0],mix.inputs[2]);ln.new(mix.outputs[0],out.inputs[0])


def geada_material(frente):
    """Geada que nasce das BORDAS: frente irregular (ruido) que avanca com o tempo
    (valor 'F', unidades do mundo), textura cristalina de Voronoi (veios brancos)."""
    mat=c.material('GeadaBordas');nt=mat.node_tree;ln=nt.links
    F=c.valor(mat,'F',0.).outputs[0];c.assar_no(mat,'F',frente)
    tc=nt.nodes.new('ShaderNodeTexCoord');sep=nt.nodes.new('ShaderNodeSeparateXYZ');ln.new(tc.outputs['Object'],sep.inputs[0])
    ax=_op(nt,'ABSOLUTE',sep.outputs['X']);ay=_op(nt,'ABSOLUTE',sep.outputs['Y'])
    dx=_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',1.,ax),8.);dy=_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',1.,ay),4.5)
    d=_op(nt,'MINIMUM',dx,dy)
    # cantos avancam mais (d menor perto dos cantos)
    canto=_op(nt,'MULTIPLY',_op(nt,'MULTIPLY',ax,ay),.9)
    d=_op(nt,'SUBTRACT',d,canto)
    noise=nt.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=1.4;noise.inputs['Detail'].default_value=8.
    noise.inputs['Roughness'].default_value=.7;ln.new(tc.outputs['Object'],noise.inputs[0])
    borda=_op(nt,'ADD',d,_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',noise.outputs['Fac'],.5),2.2))
    mr=nt.nodes.new('ShaderNodeMapRange');mr.interpolation_type='SMOOTHSTEP'
    ln.new(borda,mr.inputs['Value']);ln.new(F,mr.inputs['From Min'])
    ln.new(_op(nt,'SUBTRACT',F,.9),mr.inputs['From Max'])
    cobre=mr.outputs['Result']
    # veios cristalinos (Voronoi F2-F1 fino = linhas)
    vor=nt.nodes.new('ShaderNodeTexVoronoi');vor.feature='DISTANCE_TO_EDGE';vor.inputs['Scale'].default_value=38.
    ln.new(tc.outputs['Object'],vor.inputs['Vector'])
    veio=nt.nodes.new('ShaderNodeMapRange');veio.inputs['From Min'].default_value=.03;veio.inputs['From Max'].default_value=0.
    ln.new(vor.outputs['Distance'],veio.inputs['Value'])
    fino=nt.nodes.new('ShaderNodeTexNoise');fino.inputs['Scale'].default_value=28.;fino.inputs['Detail'].default_value=4.
    ln.new(tc.outputs['Object'],fino.inputs[0])
    dens=_op(nt,'ADD',_op(nt,'MULTIPLY',fino.outputs['Fac'],.35),_op(nt,'MULTIPLY',veio.outputs['Result'],.55))
    alfa=_op(nt,'MULTIPLY',cobre,_op(nt,'ADD',.04,_op(nt,'MULTIPLY',dens,.85)))
    ramp=nt.nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=c.lin(0x7FB4D6);ramp.color_ramp.elements[1].color=c.lin(0xF2FCFF)
    ln.new(dens,ramp.inputs[0])
    _mix_out(mat,alfa,ramp.outputs[0],forca=1.1)
    return mat


def cristal_material():
    """Gelo translucido: faces azul-claras meio transparentes, ARESTAS/borda clara
    (fresnel) e brilho especular da luz fria."""
    mat=c.material('CristalGelo','BLENDED');nt=mat.node_tree;ln=nt.links
    lw=nt.nodes.new('ShaderNodeLayerWeight');lw.inputs['Blend'].default_value=.35
    pb=nt.nodes.new('ShaderNodeBsdfPrincipled')
    pb.inputs['Base Color'].default_value=c.lin(0x9ED6F2);pb.inputs['Roughness'].default_value=.08
    pb.inputs['Emission Color'].default_value=c.lin(0x6FB8E0);pb.inputs['Emission Strength'].default_value=.35
    pb.inputs['Coat Weight'].default_value=1.
    borda=nt.nodes.new('ShaderNodeEmission');borda.inputs[0].default_value=c.lin(0xEAFBFF);borda.inputs[1].default_value=2.2
    add=nt.nodes.new('ShaderNodeMixShader');ln.new(lw.outputs['Fresnel'],add.inputs[0]);ln.new(pb.outputs[0],add.inputs[1]);ln.new(borda.outputs[0],add.inputs[2])
    info=nt.nodes.new('ShaderNodeObjectInfo')
    alfa=_op(nt,'MULTIPLY',_op(nt,'ADD',.5,_op(nt,'MULTIPLY',lw.outputs['Fresnel'],.5)),info.outputs['Alpha'])
    tr=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');out=nt.nodes.new('ShaderNodeOutputMaterial')
    ln.new(alfa,mix.inputs[0]);ln.new(tr.outputs[0],mix.inputs[1]);ln.new(add.outputs[0],mix.inputs[2]);ln.new(mix.outputs[0],out.inputs[0])
    return mat


def cristal(nome,altura,largura,mat,rng):
    """Prisma facetado irregular (5-6 lados, ponta deslocada)."""
    lados=rng.choice((5,6));verts=[]
    jit=[rng.uniform(.75,1.2) for _ in range(lados)]
    for nivel,r in [(0,largura*.55),(.62,largura),(.84,largura*.72)]:
        for k in range(lados):
            a=k*math.tau/lados;verts.append((math.cos(a)*r*jit[k],nivel*altura,math.sin(a)*r*.7*jit[k]))
    verts.append((altura*rng.uniform(-.08,.08),altura,0))
    faces=[]
    for j in range(2):
        for k in range(lados):faces.append((j*lados+k,j*lados+(k+1)%lados,(j+1)*lados+(k+1)%lados,(j+1)*lados+k))
    topo=3*lados
    for k in range(lados):faces.append((2*lados+k,2*lados+(k+1)%lados,topo))
    faces.append(tuple(reversed(range(lados))))
    me=bpy.data.meshes.new(nome);me.from_pydata(verts,[],faces);me.materials.append(mat)
    ob=bpy.data.objects.new(nome,me);bpy.context.scene.collection.objects.link(ob)
    return ob


def trinca(nome,pts,raios,mat,z):
    """Curva com espessura variavel por ponto (afina na ponta)."""
    cu=bpy.data.curves.new(nome,'CURVE');cu.dimensions='3D';cu.bevel_depth=1.;cu.bevel_resolution=1
    sp=cu.splines.new('POLY');sp.points.add(len(pts)-1)
    for p,(x,y),r in zip(sp.points,pts,raios):p.co=(x,y,0,1);p.radius=r
    ob=bpy.data.objects.new(nome,cu);bpy.context.scene.collection.objects.link(ob);cu.materials.append(mat);ob.location.z=z
    return ob


def samambaia(nome,x,y,ang,comp,mat,rng,ini,profund=2,larg=.022):
    """Dendrito de geada: haste levemente curva com ramos alternados a ~60 graus,
    cada vez menores (aspecto de pena/samambaia de gelo). Cresce a partir de 'ini'."""
    pts=[(x,y)];a=ang;n=8
    for j in range(n):
        a+=rng.uniform(-.12,.12);px,py=pts[-1];pts.append((px+math.cos(a)*comp/n,py+math.sin(a)*comp/n))
    ob=trinca(nome,pts,[larg*(1-.75*j/n) for j in range(n+1)],mat,1.2)
    c.animar(ob.data,'bevel_factor_end',lambda t,s=ini,d=comp:c.ss(s,s+.5+d*.35,t))
    c.visibilidade(ob,lambda t:.55)
    if profund>0:
        for j in range(1,n-1):
            px,py=pts[j];lado=1 if j%2 else -1
            sub=comp*(1-j/n)*rng.uniform(.45,.72)
            if sub<.12:continue
            samambaia(nome+'r',px,py,ang+lado*math.radians(rng.uniform(48,66)),sub,mat,rng,ini+.1+j*.05,profund-1,larg*.55)


def montar():
    rng=random.Random(SEED)
    sc=bpy.context.scene
    fria=bpy.data.lights.new('LuzFria','SUN');fria.energy=4.;fria.color=(.75,.9,1.)
    fo=bpy.data.objects.new('LuzFria',fria);fo.rotation_euler=(math.radians(-40),math.radians(35),0);sc.collection.objects.link(fo)
    contra=bpy.data.lights.new('Contraluz','SUN');contra.energy=2.;contra.color=(.85,.95,1.)
    ko=bpy.data.objects.new('Contraluz',contra);ko.rotation_euler=(math.radians(60),math.radians(-50),0);sc.collection.objects.link(ko)

    # ------------------------------------------------ 1) IMPACTO: vidro estilhacado (0.12-1.6 s)
    brilho_trinca=c.mat_emissao('RachaduraImpacto',c.lin(0xE6FAFF));brilho_trinca.node_tree.nodes['Emissao'].inputs['Strength'].default_value=2.
    cresce=lambda t:c.ss(.12,.45,t)
    some=lambda t:1-c.ss(.75,1.6,t)
    nr=12;angs=[i*math.tau/nr+rng.uniform(-.18,.18) for i in range(nr)];pontas=[]
    for i,a in enumerate(angs):
        comp=rng.uniform(2.4,4.2);pts=[(0,TITULO_Y)];aa=a
        for j in range(1,10):
            aa+=rng.uniform(-.22,.22);r=comp*j/9;pts.append((math.cos(aa)*r*1.35,TITULO_Y+math.sin(aa)*r*.8))
        pontas.append(pts)
        ob=trinca('TrincaImpacto',pts,[.045*(1-.85*j/9) for j in range(10)],brilho_trinca,3.)
        c.animar(ob.data,'bevel_factor_end',cresce);c.visibilidade(ob,lambda t:c.ss(.12,.18,t)*some(t))
    # aneis quebrados ligando as trincas (padrao de vidro)
    for anel in (2,4,6):
        for i in range(nr):
            if rng.random()<.35:continue
            p0=pontas[i][anel];p1=pontas[(i+1)%nr][anel]
            meio=((p0[0]+p1[0])/2*rng.uniform(.92,1.0),(p0[1]+p1[1])/2+rng.uniform(-.05,.05))
            ob=trinca('AnelVidro',[p0,meio,p1],[.014,.012,.008],brilho_trinca,3.)
            c.visibilidade(ob,lambda t,an=anel:.8*c.ss(.18+an*.035,.26+an*.035,t)*some(t))
    flash=c.brilho('ImpactoFrio',0xE8F8FF,5.5,(0,TITULO_Y,3.5),.9)
    c.visibilidade(flash,lambda t:c.ss(.1,.15,t)*(1-c.ss(.16,.6,t)))
    halo=c.brilho('HaloFrio',0x9FD8FF,3.2,(0,TITULO_Y,2.9),.45)
    c.visibilidade(halo,lambda t:c.ss(.12,.3,t)*some(t))
    ice=cristal_material()
    for i in range(16):
        a=i*math.tau/16+rng.uniform(-.15,.15);v=rng.uniform(3.5,6.5)
        ob=cristal('EstilhacoImpacto',rng.uniform(.2,.5),rng.uniform(.06,.13),ice,rng)
        ob.location.z=3.2;ob.rotation_euler=(rng.uniform(0,3),rng.uniform(0,3),a-math.pi/2)
        c.animar(ob,'location',lambda t,a=a,v=v:math.cos(a)*v*max(0,t-.14),0)
        c.animar(ob,'location',lambda t,a=a,v=v:TITULO_Y+math.sin(a)*v*max(0,t-.14)*.7,1)
        c.animar(ob,'rotation_euler',lambda t,i=i:t*(2+i%4),0)
        c.visibilidade(ob,lambda t:c.ss(.13,.2,t)*(1-c.ss(.7,1.4,t)))

    # ------------------------------------------------ 2) GEADA nas bordas (cresce devagar o tempo todo)
    frente=[.05+.85*c.ss(.6,2.2,t)+.3*c.ss(2.2,4.8,t) for t in c.TEMPOS]
    geada=c.plano('CamadaGeada',16,9,geada_material(frente),.2)
    c.visibilidade(geada,lambda t:c.ss(.5,1.3,t)*(1-c.ss(4.9,6.,t)))
    fil=c.mat_emissao('FilamentosDeGeada',c.lin(0xDDF4FF));fil.node_tree.nodes['Emissao'].inputs['Strength'].default_value=1.4
    raizes=[]
    for i in range(22):
        borda=rng.choice(('topo','topo','base','base','esq','dir'))
        if borda=='topo':raizes.append((rng.uniform(-7.8,7.8),4.5,-math.pi/2))
        elif borda=='base':raizes.append((rng.uniform(-7.8,7.8),-4.5,math.pi/2))
        elif borda=='esq':raizes.append((-8.,rng.uniform(-3.8,3.8),0.))
        else:raizes.append((8.,rng.uniform(-3.8,3.8),math.pi))
    for x,y,a in raizes:
        perto_canto=min(8-abs(x),4.5-abs(y))<1.2 or (abs(x)>5.5 and abs(y)>2.5)
        comp=rng.uniform(1.1,1.8) if perto_canto else rng.uniform(.6,1.15)
        samambaia('Dendrito',x,y,a+rng.uniform(-.3,.3),comp,fil,rng,rng.uniform(.6,1.6))

    # ------------------------------------------------ 3) CRISTAIS em aglomerados (cantos + alguns na borda)
    aglos=[(-8.,-4.5,math.radians(55)),(8.,-4.5,math.radians(125)),(-8.,4.5,math.radians(-55)),(8.,4.5,math.radians(-125)),
           (-3.1,-4.6,math.radians(90)),(3.6,-4.6,math.radians(90)),(-1.,4.6,math.radians(-90)),(5.2,4.6,math.radians(-90)),
           (-8.1,.6,0.),(8.1,-1.,math.pi)]
    for g,(gx,gy,gang) in enumerate(aglos):
        canto=abs(gx)>7.5 and abs(gy)>4.
        ncr=rng.randint(6,9) if canto else rng.randint(3,5)
        for k in range(ncr):
            heroi=k==0
            alt=(rng.uniform(1.4,2.0) if canto else rng.uniform(.9,1.3)) if heroi else rng.uniform(.3,1.0)*(1. if canto else .8)
            ob=cristal(f'Cristal{g}_{k}',alt,alt*rng.uniform(.2,.3),ice,rng)
            esp=math.radians(55 if canto else 40)
            a=gang+rng.uniform(-esp,esp)
            ob.location=(gx+rng.uniform(-.35,.35),gy+rng.uniform(-.2,.2),rng.uniform(1.,1.6))
            ob.rotation_euler=(rng.uniform(-.35,.35),rng.uniform(-.25,.25),a-math.pi/2)
            ini=.5+g*.06+rng.uniform(0,.7)
            for ax in (0,1,2):c.animar(ob,'scale',lambda t,s=ini:max(.001,c.ss(s,s+.9,t))*(1+.06*c.ss(2.,4.8,t)),ax)
            c.visibilidade(ob,lambda t:1.-c.ss(5.,5.9,t))

    # ------------------------------------------------ 4) FLOCO discreto atras do titulo
    root=bpy.data.objects.new('FlocoSutil',None);sc.collection.objects.link(root);root.location=(0,TITULO_Y,-.3)
    for i in range(6):
        a=i*math.tau/6
        ob=trinca('BracoFloco',[(0,0),(2.2*math.cos(a),2.2*math.sin(a))],[.022,.01],fil,0.);ob.parent=root;ob.location.z=0
        c.visibilidade(ob,lambda t:.16*c.ss(1.,2.2,t)*(1-c.ss(4.5,5.4,t)))
        for r in (.75,1.4):
            for lado in (-1,1):
                x,y=r*math.cos(a),r*math.sin(a);b=a+lado*math.radians(55);l=.55 if r<1 else .38
                ob=trinca('RamoFloco',[(x,y),(x+l*math.cos(b),y+l*math.sin(b))],[.012,.006],fil,0.);ob.parent=root;ob.location.z=0
                c.visibilidade(ob,lambda t:.16*c.ss(1.,2.2,t)*(1-c.ss(4.5,5.4,t)))
    c.animar(root,'rotation_euler',lambda t:t*.15,2)

    # ------------------------------------------------ 5) NEVE em profundidade + nevoa leve
    for i in range(120):
        prof=rng.random();x0=rng.uniform(-9.5,9.5);phase=rng.random();speed=.06+.12*prof
        lado_livre=abs(x0)>4.6
        r=.012+.05*prof;alfa=(.35+.55*prof)*(1. if lado_livre else .35)
        ob=c.brilho('Neve',0xE2F6FF,r,(x0,0,1.5+prof*1.5),1.4)
        c.animar(ob,'location',lambda t,p=phase,s=speed:5.-10.*((p+t*s)%1),1)
        c.animar(ob,'location',lambda t,x=x0,i=i,p=phase,s=speed:x-.6*((p+t*s)%1)+.12*math.sin(t*1.3+i),0)
        c.visibilidade(ob,lambda t,p=phase,s=speed,a=alfa:a*math.sin(math.pi*((p+t*s)%1))**1.5*c.ss(.4,1.4,t)*(1-c.ss(5.,6.,t)))
    nevoa=c.mat_brilho('Nevoa');nevoa.node_tree.nodes['Emissao'].inputs['Color'].default_value=c.lin(0xD8EEFA);c.assar_no(nevoa,'A',[.09]*c.N)
    for i in range(8):
        lado=-1 if i%2==0 else 1;x=lado*rng.uniform(6.,8.5);y=rng.uniform(-3.8,3.8)
        ob=c.plano(f'Nevoa{i}',rng.uniform(7,9),rng.uniform(2.,2.8),nevoa,.4);ob.location.x=x;ob.location.y=y
        c.animar(ob,'location',lambda t,x=x,lado=lado:x-lado*.12*t,0)
        c.visibilidade(ob,lambda t:c.ss(.8,2.,t)*(1-c.ss(4.8,6.,t)))

    # ------------------------------------------------ 6) SAIDA: fragmentos se soltam das bordas
    for i in range(22):
        lado=-1 if i%2==0 else 1;start=rng.uniform(4.75,5.3)
        ob=cristal('EstilhacoSaida',rng.uniform(.12,.3),.05,ice,rng);x=rng.uniform(-7.5,7.5);y=lado*rng.uniform(3.6,4.3)
        ob.location.z=2.2
        c.animar(ob,'location',lambda t,x=x,i=i,s=start:x+.4*math.sin(i)*max(0,t-s),0)
        c.animar(ob,'location',lambda t,y=y,s=start:y-1.8*max(0,t-s)**1.5,1)
        c.animar(ob,'rotation_euler',lambda t,i=i:t*(i%3+1),2)
        c.visibilidade(ob,lambda t,s=start:c.ss(s,s+.12,t)*(1-c.ss(s+.4,6.,t)))
    return c.metadata('gelo',SEED)


if __name__=='__main__':c.executar('gelo',montar)
