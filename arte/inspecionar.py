"""Inspecao completa dos PNG e metadados das cenas Blender."""
import argparse
import json
from pathlib import Path
from PIL import Image
RAIZ=Path(__file__).resolve().parent.parent

def validar(id: str, quadros: list[int], raiz=None) -> list[str]:
    pasta=Path(raiz) if raiz is not None else RAIZ/'arte'/'render'/id
    erros=[]
    try:
        meta=json.loads((pasta/'marcadores.json').read_text(encoding='utf-8'))
        if meta.get('id')!=id:erros.append('id dos metadados difere da cena')
        for m in meta.get('markers',[]):
            if not isinstance(m.get('frame'),int) or not 0<=m['frame']<144 or not m.get('name'):
                erros.append('marcador fora de 0..143 ou sem nome')
    except (OSError,ValueError,TypeError) as e:erros.append('metadados invalidos: '+str(e))
    try:
        info=json.loads((pasta/'inspecao.json').read_text(encoding='utf-8'))
        if info.get('text_in_scene') is not False:erros.append('cena contem texto ou nao comprova ausencia de texto')
        for q in quadros:
            if q not in info.get('rendered_frames',[]):erros.append(f'quadro {q} nao consta da renderizacao atual')
    except (OSError,ValueError) as e:erros.append('inspecao Blender ausente/invalida: '+str(e))
    for q in quadros:
        arq=pasta/f'{q:04d}.png'
        try:
            with Image.open(arq) as im:
                if im.size!=(1280,720) or im.mode!='RGBA':erros.append(f'quadro {q}: esperado RGBA 1280x720')
                im.load()
            with Image.open(arq) as im:im.verify()
        except (OSError,ValueError,SyntaxError) as e:erros.append(f'quadro {q} ilegivel/ausente: {e}')
    return erros

def luminancia(caminho_png: str, caixa) -> float:
    """Media de max(R,G,B)/255 * A/255 na caixa (x0,y0,x1,y1 em fracao 0-1, y de cima)."""
    with Image.open(caminho_png) as im:
        im=im.convert('RGBA');w,h=im.size
        x0,y0,x1,y1=caixa
        reg=im.crop((round(x0*w),round(y0*h),round(x1*w),round(y1*h)))
        px=list(reg.getdata())
    if not px:return 0.0
    return sum(max(r,g,b)*a for r,g,b,a in px)/(255*255*len(px))

def main():
    ap=argparse.ArgumentParser();ap.add_argument('id');ap.add_argument('--quadros',default='12,36,72,127')
    a=ap.parse_args();qs=[int(q) for q in a.quadros.split(',')]
    erros=validar(a.id,qs)
    for e in erros:print('FAIL',e)
    if not erros:print('PASS',a.id,':',len(qs),'quadros integros e sem texto Blender')
    raise SystemExit(bool(erros))
if __name__=='__main__':main()
