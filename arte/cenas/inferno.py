"""Fornalha hibrida: fissura, chamas organicas, fumaca e fragmentos.

Coordenadas do mundo: tela 16 x 9 (x -8..8, y -4.5..4.5). O titulo do
InfernoAvisoFx fica em cy = 0.45 h -> y = +0.45. Zona de leitura protegida
(2-4.8 s): x 20-80 %, y 32-66 % -> x -4.8..4.8, y -1.44..+1.62.
"""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import bpy
import math
import random
import comum as c

SEED=482
TITULO_Y=.45


def _op(nt,kind,*values):
    ob=nt.nodes.new('ShaderNodeMath');ob.operation=kind
    for i,v in enumerate(values):
        if isinstance(v,(int,float)):ob.inputs[i].default_value=v
        else:nt.links.new(v,ob.inputs[i])
    return ob.outputs[0]


def _tempo(mat,nome='T'):
    """No de valor com o tempo em segundos (assado quadro a quadro)."""
    v=c.valor(mat,nome,0.);c.assar_no(mat,nome,c.TEMPOS);return v.outputs[0]


def fogo_material(nome,altura=1.15,sobe=.55,lado=0,brilho=2.2,sx=9.,sy=2.6,base=.3):
    """Linguas de fogo que SOBEM: ruido 4D rolando pra cima + distorcao, borda nitida
    (limiar suave curto) e cor pela temperatura (nucleo branco-amarelo, pontas vinho).
    altura: quanto maior, mais baixas as chamas. lado: 1 = chama colada na borda
    esquerda do plano, -1 = direita, 0 = sem recuo lateral."""
    mat=c.material(nome);nt=mat.node_tree;ln=nt.links
    T=_tempo(mat)
    tc=nt.nodes.new('ShaderNodeTexCoord');sep=nt.nodes.new('ShaderNodeSeparateXYZ');ln.new(tc.outputs['Generated'],sep.inputs[0])
    x,y=sep.outputs['X'],sep.outputs['Y']
    # distorcao lenta (as linguas balancam sem oscilar juntas)
    v1=nt.nodes.new('ShaderNodeCombineXYZ')
    ln.new(_op(nt,'MULTIPLY',x,3.),v1.inputs[0]);ln.new(_op(nt,'SUBTRACT',_op(nt,'MULTIPLY',y,1.2),_op(nt,'MULTIPLY',T,sobe*.7)),v1.inputs[1])
    n1=nt.nodes.new('ShaderNodeTexNoise');n1.noise_dimensions='4D';n1.inputs['Scale'].default_value=1.6;n1.inputs['Detail'].default_value=2.
    ln.new(v1.outputs[0],n1.inputs['Vector']);ln.new(_op(nt,'MULTIPLY',T,.25),n1.inputs['W'])
    wx=_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',n1.outputs['Fac'],.5),.6)
    # linguas: alta frequencia em x, rolando pra cima
    v2=nt.nodes.new('ShaderNodeCombineXYZ')
    ln.new(_op(nt,'MULTIPLY',_op(nt,'ADD',x,_op(nt,'MULTIPLY',wx,.18)),sx),v2.inputs[0])
    ln.new(_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',y,_op(nt,'MULTIPLY',T,sobe)),sy),v2.inputs[1])
    n2=nt.nodes.new('ShaderNodeTexNoise');n2.noise_dimensions='4D';n2.inputs['Scale'].default_value=1.;n2.inputs['Detail'].default_value=5.
    n2.inputs['Roughness'].default_value=.62;ln.new(v2.outputs[0],n2.inputs['Vector']);ln.new(_op(nt,'MULTIPLY',T,.7),n2.inputs['W'])
    d=_op(nt,'SUBTRACT',_op(nt,'SUBTRACT',_op(nt,'MULTIPLY',n2.outputs['Fac'],1.5),_op(nt,'MULTIPLY',y,altura)),base)
    if lado:
        dentro=x if lado>0 else _op(nt,'SUBTRACT',1.,x)
        d=_op(nt,'SUBTRACT',d,_op(nt,'MULTIPLY',dentro,.75))
    # borda nitida: 0 -> 1 em 0.07 de densidade
    mr=nt.nodes.new('ShaderNodeMapRange');mr.interpolation_type='SMOOTHSTEP'
    mr.inputs['From Min'].default_value=0.;mr.inputs['From Max'].default_value=.07;ln.new(d,mr.inputs['Value'])
    info=nt.nodes.new('ShaderNodeObjectInfo');alfa=_op(nt,'MULTIPLY',mr.outputs['Result'],info.outputs['Alpha'])
    ramp=nt.nodes.new('ShaderNodeValToRGB');r=ramp.color_ramp
    r.elements[0].position=0.;r.elements[0].color=c.lin(0x5E0A04);r.elements[1].position=1.;r.elements[1].color=c.lin(0xFFF7DE)
    for p,col in [(.12,0xB0200A),(.3,0xF04A0C),(.52,0xFF8C1A),(.76,0xFFD45E)]:r.elements.new(p).color=c.lin(col)
    calor=nt.nodes.new('ShaderNodeClamp');ln.new(_op(nt,'MULTIPLY',d,1.45),calor.inputs[0]);ln.new(calor.outputs[0],ramp.inputs[0])
    em=nt.nodes.new('ShaderNodeEmission');ln.new(ramp.outputs[0],em.inputs[0]);em.inputs[1].default_value=brilho
    tr=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');out=nt.nodes.new('ShaderNodeOutputMaterial')
    ln.new(alfa,mix.inputs[0]);ln.new(tr.outputs[0],mix.inputs[1]);ln.new(em.outputs[0],mix.inputs[2]);ln.new(mix.outputs[0],out.inputs[0])
    return mat


def brilho_forno(nome,cor=0xFF6A14,potencia=2.2,lado=0):
    """Brilho quente que sobe da borda (alfa = (1-y)^p, opcionalmente colado num lado)."""
    mat=c.material(nome);nt=mat.node_tree;ln=nt.links
    tc=nt.nodes.new('ShaderNodeTexCoord');sep=nt.nodes.new('ShaderNodeSeparateXYZ');ln.new(tc.outputs['Generated'],sep.inputs[0])
    g=_op(nt,'POWER',_op(nt,'SUBTRACT',1.,sep.outputs['Y']),potencia)
    if lado:
        dentro=sep.outputs['X'] if lado>0 else _op(nt,'SUBTRACT',1.,sep.outputs['X'])
        g=_op(nt,'MULTIPLY',g,_op(nt,'POWER',_op(nt,'SUBTRACT',1.,dentro),2.))
    info=nt.nodes.new('ShaderNodeObjectInfo');alfa=_op(nt,'MULTIPLY',g,info.outputs['Alpha'])
    em=nt.nodes.new('ShaderNodeEmission');em.inputs[0].default_value=c.lin(cor)
    tr=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');out=nt.nodes.new('ShaderNodeOutputMaterial')
    ln.new(alfa,mix.inputs[0]);ln.new(tr.outputs[0],mix.inputs[1]);ln.new(em.outputs[0],mix.inputs[2]);ln.new(mix.outputs[0],out.inputs[0])
    return mat


def fumaca_material():
    """Fumaca densa com a parte de BAIXO iluminada pelo fogo (laranja) e topo escuro."""
    mat=c.material('FumacaIluminada');nt=mat.node_tree;ln=nt.links
    T=_tempo(mat)
    tc=nt.nodes.new('ShaderNodeTexCoord')
    noise=nt.nodes.new('ShaderNodeTexNoise');noise.noise_dimensions='4D';noise.inputs['Scale'].default_value=1.7
    noise.inputs['Detail'].default_value=6.;noise.inputs['Roughness'].default_value=.55
    ln.new(tc.outputs['Object'],noise.inputs[0]);ln.new(_op(nt,'MULTIPLY',T,.12),noise.inputs['W'])
    length=nt.nodes.new('ShaderNodeVectorMath');length.operation='LENGTH';ln.new(tc.outputs['Object'],length.inputs[0])
    # silhueta irregular: o raio e "comido" pelo ruido
    forma=_op(nt,'SUBTRACT',_op(nt,'SUBTRACT',1.,length.outputs['Value']),_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',.55,noise.outputs['Fac']),1.1))
    mr=nt.nodes.new('ShaderNodeMapRange');mr.interpolation_type='SMOOTHERSTEP'
    mr.inputs['From Min'].default_value=0.;mr.inputs['From Max'].default_value=.45;ln.new(forma,mr.inputs['Value'])
    info=nt.nodes.new('ShaderNodeObjectInfo');alfa=_op(nt,'MULTIPLY',mr.outputs['Result'],info.outputs['Alpha'])
    sep=nt.nodes.new('ShaderNodeSeparateXYZ');ln.new(tc.outputs['Object'],sep.inputs[0])
    luz=nt.nodes.new('ShaderNodeClamp');ln.new(_op(nt,'MULTIPLY',_op(nt,'SUBTRACT',.35,sep.outputs['Y']),.8),luz.inputs[0])
    ramp=nt.nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=c.lin(0x120D0C);ramp.color_ramp.elements[1].color=c.lin(0x8C4320)
    ramp.color_ramp.elements.new(.55).color=c.lin(0x3A1F15)
    ln.new(_op(nt,'MULTIPLY',luz.outputs[0],noise.outputs['Fac']),ramp.inputs[0])
    em=nt.nodes.new('ShaderNodeEmission');ln.new(ramp.outputs[0],em.inputs[0])
    tr=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');out=nt.nodes.new('ShaderNodeOutputMaterial')
    ln.new(alfa,mix.inputs[0]);ln.new(tr.outputs[0],mix.inputs[1]);ln.new(em.outputs[0],mix.inputs[2]);ln.new(mix.outputs[0],out.inputs[0])
    return mat


def anel_material(nome,cor):
    """Anel de calor suave (perfil gaussiano no raio 0.85), alfa pelo objeto."""
    mat=c.material(nome);nt=mat.node_tree;ln=nt.links
    tc=nt.nodes.new('ShaderNodeTexCoord');length=nt.nodes.new('ShaderNodeVectorMath');length.operation='LENGTH'
    ln.new(tc.outputs['Object'],length.inputs[0])
    dd=_op(nt,'DIVIDE',_op(nt,'SUBTRACT',length.outputs['Value'],.85),.07)
    g=_op(nt,'EXPONENT',_op(nt,'MULTIPLY',_op(nt,'MULTIPLY',dd,dd),-1.))
    info=nt.nodes.new('ShaderNodeObjectInfo');alfa=_op(nt,'MULTIPLY',g,info.outputs['Alpha'])
    em=nt.nodes.new('ShaderNodeEmission');em.inputs[0].default_value=c.lin(cor);em.inputs[1].default_value=1.4
    tr=nt.nodes.new('ShaderNodeBsdfTransparent');mix=nt.nodes.new('ShaderNodeMixShader');out=nt.nodes.new('ShaderNodeOutputMaterial')
    ln.new(alfa,mix.inputs[0]);ln.new(tr.outputs[0],mix.inputs[1]);ln.new(em.outputs[0],mix.inputs[2]);ln.new(mix.outputs[0],out.inputs[0])
    return mat


def lava_material():
    lava=c.material_solido('LavaInterior',0xFF7A13,.8,0.)
    nt=lava.node_tree;pb=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED')
    noise=nt.nodes.new('ShaderNodeTexNoise');noise.noise_dimensions='4D';noise.inputs['Scale'].default_value=4.;noise.inputs['Detail'].default_value=6.
    ramp=nt.nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.25;ramp.color_ramp.elements[0].color=c.lin(0x8A1A08)
    ramp.color_ramp.elements[1].position=.72;ramp.color_ramp.elements[1].color=c.lin(0xFFF6C4)
    ramp.color_ramp.elements.new(.48).color=c.lin(0xFF740E)
    nt.links.new(noise.outputs['Fac'],ramp.inputs[0]);nt.links.new(ramp.outputs[0],pb.inputs['Base Color']);nt.links.new(ramp.outputs[0],pb.inputs['Emission Color'])
    pb.inputs['Emission Strength'].default_value=1.8
    c.assar(nt,f'nodes["{noise.name}"].inputs["W"].default_value',[t*.5 for t in c.TEMPOS])
    return lava


def montar():
    rng=random.Random(SEED)

    # ------------------------------------------------ 1) FISSURA (0-1.7 s), atras do titulo
    n=27;xs=[-3.4+6.8*i/(n-1) for i in range(n)]
    sino=lambda x:max(0.,1-(x/3.4)**2)
    topo=[(x,TITULO_Y+.04+.40*sino(x)*(.55 if k%2 else 1.)+rng.uniform(-.06,.1)*sino(x)) for k,x in enumerate(xs)]
    base=[(x,TITULO_Y-.04-.34*sino(x)*(1. if k%2 else .5)-rng.uniform(-.06,.1)*sino(x)) for k,x in enumerate(reversed(xs))]
    contorno=topo+base[1:-1]
    mesh=bpy.data.meshes.new('InteriorRuptura')
    mesh.from_pydata([(x,y-TITULO_Y,0) for x,y in contorno],[],[tuple(range(len(contorno)))])
    mesh.materials.append(lava_material())
    gap=bpy.data.objects.new('AberturaIncandescente',mesh);bpy.context.scene.collection.objects.link(gap)
    gap.location=(0,TITULO_Y,3.2)
    abre=lambda t:c.ss(.04,.45,t)
    fecha=lambda t:1-c.ss(.85,1.6,t)
    c.visibilidade(gap,lambda t:c.ss(.03,.15,t)*fecha(t))
    c.animar(gap,'scale',lambda t:.08+.92*abre(t),0)
    c.animar(gap,'scale',lambda t:.05+.95*c.ss(.12,.6,t)*(1-.5*c.ss(.9,1.6,t)),1)
    ember=c.mat_emissao('FendaLaranja',c.lin(0xFF7A12));carvao=c.mat_emissao('BordaCarbonizada',c.lin(0x1A0C08))
    pts=[(x,y-TITULO_Y,0) for x,y in contorno]+[(contorno[0][0],contorno[0][1]-TITULO_Y,0)]
    for nome,mat,esp,z,alfa in [('BordaCarvao',carvao,.07,3.3,.95),('BordaBrasa',ember,.018,3.4,.9)]:
        ob=c.curva(nome,pts,mat,esp,z);ob.parent=gap;ob.location=(0,0,z-3.2)
        c.visibilidade(ob,lambda t,a=alfa:a*c.ss(.03,.15,t)*fecha(t))
    # rachaduras ramificadas saindo das bordas (crescem e esfriam)
    for i in range(9):
        x0,y0=contorno[rng.randrange(len(contorno))];ang=math.atan2(y0-TITULO_Y,x0*.3)+rng.uniform(-.5,.5)
        cur=[(x0,y0)];comp=rng.uniform(.6,1.2)
        for k in range(3):
            ang+=rng.uniform(-.9,.9);px,py=cur[-1];cur.append((px+math.cos(ang)*comp/3,py+math.sin(ang)*comp/3))
        ob=c.curva('RamificacaoFissura',[(x,y,0) for x,y in cur],ember,rng.uniform(.022,.036),3.35)
        ini=.12+rng.uniform(0,.18)
        c.visibilidade(ob,lambda t,s=ini:.85*c.ss(s,s+.2,t)*(1-c.ss(.8,1.4,t)))
    aura=c.brilho('HaloFissura',0xFF8A1E,4.,(0,TITULO_Y,2.8),.55)
    c.animar(aura,'scale',lambda t:(.3+.7*abre(t))*2.,0);c.animar(aura,'scale',lambda t:(.3+.7*abre(t))*.9,1)
    c.visibilidade(aura,lambda t:c.ss(.05,.25,t)*fecha(t))
    flash=c.brilho('ClaraoBreve',0xFFD08A,6,(0,TITULO_Y,3.5),.85)
    c.visibilidade(flash,lambda t:c.ss(.02,.1,t)*(1-c.ss(.12,.55,t)))
    anel=c.plano('OndaDeCalor',2,2,anel_material('OndaDeCalor',0xFFA040),3.0);anel.location.y=TITULO_Y
    for ax in (0,1):c.animar(anel,'scale',lambda t:.2+9.*c.ss(.06,1.,t),ax)
    c.visibilidade(anel,lambda t:.55*c.ss(.06,.16,t)*(1-c.ss(.35,1.,t)))
    # fragmentos carbonizados com borda quente (saem do centro antes de 2 s)
    coal=c.material_solido('Carvao',0x2A1710,.25,.05)
    pb=next(nd for nd in coal.node_tree.nodes if nd.type=='BSDF_PRINCIPLED');pb.inputs['Emission Color'].default_value=c.lin(0xFF5A10)
    for i in range(30):
        bpy.ops.mesh.primitive_cube_add(size=1,location=(0,0,3.4));ob=bpy.context.object;ob.name=f'FragmentoCarbonizado{i}'
        ob.data.materials.append(coal);s=rng.uniform(.07,.2);ob.scale=(s,s*rng.uniform(.6,1.4),.08)
        ox,oy=contorno[rng.randrange(len(contorno))];a=math.atan2(oy-TITULO_Y,ox)+rng.uniform(-.4,.4);v=rng.uniform(5,10);spin=rng.uniform(-6,6)
        c.animar(ob,'location',lambda t,ox=ox,a=a,v=v:ox+math.cos(a)*v*max(0,t-.8)**1.3,0)
        c.animar(ob,'location',lambda t,oy=oy,a=a,v=v:oy+math.sin(a)*v*max(0,t-.8)**1.3-1.5*max(0,t-.8)**2,1)
        c.animar(ob,'rotation_euler',lambda t,sp=spin:sp*max(0,t-.8),2)
        c.visibilidade(ob,lambda t:c.ss(.8,.9,t)*(1-c.ss(1.4,1.9,t)))

    # ------------------------------------------------ 2) FOGO nas bordas (camadas)
    entra=lambda t:c.ss(.25,1.5,t)
    sai=lambda t:1-c.ss(4.7,6.,t)
    gb=c.plano('BrilhoForno',17,3.6,brilho_forno('BrilhoForno',potencia=2.6),.6);gb.location.y=-4.5+1.8
    c.visibilidade(gb,lambda t:.4*entra(t)*sai(t))
    for nome,larg,alt,z,alfa,alt_k,sobe,dy in [('MuralhaFundo',17.5,2.7,1.0,.7,1.0,.45,0.),('MuralhaFrente',17.5,1.9,1.2,1.,1.15,.7,-.1)]:
        ob=c.plano(nome,larg,alt,fogo_material(nome,alt_k,sobe),z)
        yf=-4.5+alt/2+dy
        c.animar(ob,'location',lambda t,yf=yf,alt=alt:yf-alt*(1-entra(t))-alt*.7*c.ss(4.7,6.,t),1)
        c.visibilidade(ob,lambda t,a=alfa:a*c.ss(.2,.6,t))
    for lado in (1,-1):
        gl=c.plano('BrilhoLateral',3.5,9,brilho_forno('BrilhoLateral',lado=lado,potencia=.8),.7)
        gl.location.x=-lado*(8-1.75)
        c.visibilidade(gl,lambda t:.35*c.ss(.6,1.8,t)*sai(t))
        ob=c.plano('FogoLateral',3.2,9.,fogo_material(f'FogoLateral{lado}',1.9,.5,lado,2.,sx=3.2,sy=4.2,base=.22),.9)
        ob.location.x=-lado*(8-1.5)
        c.animar(ob,'location',lambda t:-9*(1-c.ss(.5,1.9,t))-6*c.ss(4.7,6.,t),1)
        c.visibilidade(ob,lambda t:.85*c.ss(.5,.9,t))

    # ------------------------------------------------ 3) FUMACA no teto (iluminada por baixo)
    smoke=fumaca_material()
    for i in range(18):
        x=-8.5+17*(i+rng.uniform(-.3,.3))/17;w=rng.uniform(3.,4.6)
        ob=c.plano(f'Fumaca_{i:02d}',w,w*rng.uniform(.6,.85),smoke,2.0+i*.002)
        y0=rng.uniform(4.0,5.1);dr=rng.uniform(-.25,.25);fase=rng.uniform(0,6)
        ob.location.x=x;ob.rotation_euler.z=rng.uniform(-.4,.4)
        c.animar(ob,'location',lambda t,y0=y0,f=fase:y0+.18*t+.08*math.sin(t*.8+f),1)
        c.animar(ob,'location',lambda t,x=x,d=dr:x+d*t,0)
        c.visibilidade(ob,lambda t,i=i:(.38+.14*(i%3)/2)*c.ss(.5,2.,t)*(1-c.ss(4.9,6.,t)))

    # ------------------------------------------------ 4) BRASAS (laterais, nunca no centro)
    for i in range(110):
        side=-1 if i%2==0 else 1;phase=rng.uniform(0,1);speed=rng.uniform(.14,.32);x=side*rng.uniform(5.,8.4)
        cor=0xFFC24E if i%3 else 0xFF7A1E
        ob=c.brilho(f'Brasa{i}',cor,rng.uniform(.03,.08),(x,0,rng.uniform(2.4,3.2)),1.6)
        c.animar(ob,'location',lambda t,p=phase,s=speed:-5.+10.2*((p+t*s)%1),1)
        c.animar(ob,'location',lambda t,x=x,i=i,side=side,p=phase,s=speed:x+.3*math.sin(t*2.3+i)+side*.25*((p+t*s)%1),0)
        c.visibilidade(ob,lambda t,p=phase,s=speed:math.sin(math.pi*((p+t*s)%1))**1.5*c.ss(.2,1.2,t)*(1-c.ss(4.8,5.9,t)))

    # ------------------------------------------------ 5) METEOROS na faixa de cima (acima do texto)
    for i in range(6):
        dir_=-1 if i%2==0 else 1;start=.35+i*.82;dur=1.15
        x0=-dir_*9.6;x1=dir_*9.6;y0=rng.uniform(4.2,4.9);y1=rng.uniform(2.3,2.9)
        def pos(t,s=start,x0=x0,x1=x1,y0=y0,y1=y1,dur=dur):
            u=(t-s)/dur;return x0+(x1-x0)*u,y0+(y1-y0)*u
        vis=lambda t,s=start,dur=dur:c.ss(s,s+.05,t)*(1-c.ss(s+dur-.1,s+dur,t))*(1-c.ss(4.7,5.2,t))
        for k,(r,a,cor) in enumerate([(.42,.55,0xFF8A2A),(.12,2.5,0xFFF4D6)]):
            ob=c.brilho(f'Meteoro{i}_{k}',cor,r,(0,0,3.8+k*.01),a)
            c.animar(ob,'location',lambda t,p=pos:p(t)[0],0);c.animar(ob,'location',lambda t,p=pos:p(t)[1],1)
            c.visibilidade(ob,vis)
        for k in range(1,34):
            atr=k*.009;r=.2*(1-k/36)+.04
            ob=c.brilho(f'RastroMeteoro{i}_{k}',0xFF9A3A if k<12 else 0xC8401A,r,(0,0,3.7),.42*(1-k/34)**1.3)
            c.animar(ob,'location',lambda t,p=pos,a=atr:p(t-a)[0],0);c.animar(ob,'location',lambda t,p=pos,a=atr:p(t-a)[1],1)
            c.visibilidade(ob,lambda t,v=vis,a=atr:v(t-a*.5))

    # ------------------------------------------------ 6) REDEMOINHO de brasas atras do titulo
    # brasas correndo pela elipse (x 3.0, y 1.2) com rastro curto, acendendo e apagando
    for i in range(26):
        a0=rng.uniform(0,math.tau);vel=rng.uniform(.7,1.05);rx=rng.uniform(2.7,3.3);ry=rx*.4;fase=rng.uniform(0,6)
        def orb(t,a0=a0,vel=vel,rx=rx,ry=ry):
            a=a0-t*vel;return rx*math.cos(a),TITULO_Y+ry*math.sin(a)
        luz=lambda t,f=fase:.55*c.ss(1.,2.2,t)*(1-c.ss(4.4,5.3,t))*max(0.,math.sin(t*1.9+f))**2
        for k in range(7):
            atr=k*.035
            ob=c.brilho(f'BrasaRedemoinho{i}_{k}',0xFFB040 if k<3 else 0xE0501A,.055*(1-k/9),(0,0,-.2),.9*(1-k/7))
            c.animar(ob,'location',lambda t,o=orb,a=atr:o(t-a)[0],0);c.animar(ob,'location',lambda t,o=orb,a=atr:o(t-a)[1],1)
            c.visibilidade(ob,luz)
    return c.metadata('inferno',SEED)


if __name__=='__main__':c.executar('inferno',montar)
