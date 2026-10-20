"""Previa dos avisos: PNG ou JPEG cor/alfa sobre gameplay, com texto aproximado.
Exemplo: python arte/previa.py inferno --quadros 12,36,72,127 --fundo arte/referencias/gameplay_claro.png
O texto e apenas referencia de composicao; no jogo e desenhado pelo Java.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

RAIZ = Path(__file__).resolve().parent.parent
FPS = 24

def ss(a,b,x):
    u = min(1., max(0.,(x-a)/(b-a)))
    return u*u*(3-2*u)

def fade(meta,t):
    f = meta.get('fade')
    return ss(*f['in'],t)*(1-ss(*f['out'],t)) if f else 1.

def enquadrar(imagem,largura:int,altura:int):
    """Cover centralizado 16:9 -> largura x altura sem deformar (igual ao FxTimeline.cobrir do jogo)."""
    s=max(largura/imagem.width,altura/imagem.height)
    w,h=round(imagem.width*s),round(imagem.height*s)
    grande=imagem.resize((w,h),Image.Resampling.LANCZOS)
    x=(w-largura)//2;y=(h-altura)//2
    return grande.crop((x,y,x+largura,y+altura))

def compor(png,meta,quadro,mensagem=None,fundo=None,alfa=None):
    t = quadro/FPS
    with Image.open(png) as src:
        img=src.convert('RGBA')
    if alfa is not None:
        with Image.open(alfa) as src:
            mask=src.convert('L')
        if mask.size != img.size:
            raise ValueError('Cor e alfa precisam ter as mesmas dimensoes')
        img.putalpha(mask)
        mask.close()
    else:
        # O par JPEG exportado ja contem o fade; aplica-lo apenas ao PNG original.
        env=fade(meta,t)
        img.putalpha(img.getchannel('A').point(lambda v: round(v*env)))
    if fundo is not None:
        with Image.open(fundo) as src:
            base=src.convert('RGBA')
        # Cover centralizado sem distorcer o gameplay de referencia.
        factor=max(img.width/base.width,img.height/base.height)
        base=base.resize((round(base.width*factor),round(base.height*factor)),Image.Resampling.LANCZOS)
        x=(base.width-img.width)//2;y=(base.height-img.height)//2
        base=base.crop((x,y,x+img.width,y+img.height))
    else:
        base=Image.new('RGBA',img.size,(32,32,32,255))
    base.alpha_composite(img)
    preview=meta.get('preview',{})
    flat=next((m['frame'] for m in meta.get('markers',[]) if m['name']=='linha_reta'),10**9)
    cor=tuple(preview.get('color',(210,30,40) if quadro<flat else (225,218,195)))
    mensagem=preview.get('message','VOCE MORREU') if mensagem is None else mensagem
    env=fade(meta,t)
    a=round(255*ss(.5,.9,t)*env)
    w,h=base.size
    if a>8:
        title_font=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',max(1,round(h*15.2/240*1.35)))
        sub_font=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',max(1,round(h*6/240*1.35)))
        layer=Image.new('RGBA',base.size)
        draw=ImageDraw.Draw(layer)
        # Contraste semelhante a sombra/contorno do Java, inclusive sobre gameplay claro.
        draw.text((w/2,h*preview.get('title_y',126/240)),'AVISO',font=title_font,fill=cor+(a,),anchor='mm',stroke_width=2,stroke_fill=(10,12,16,a))
        draw.text((w/2,h*preview.get('message_y',148/240)),mensagem,font=sub_font,fill=cor+(a,),anchor='mm',stroke_width=1,stroke_fill=(10,12,16,a))
        base.alpha_composite(layer)
        layer.close()
    img.close()
    return base.convert('RGB')

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('id');ap.add_argument('--quadros');ap.add_argument('--gif',action='store_true')
    ap.add_argument('--fundo');ap.add_argument('--exportado',action='store_true');ap.add_argument('--mensagem')
    args=ap.parse_args()
    raw=RAIZ/'arte'/'render'/args.id
    meta=json.loads((raw/'marcadores.json').read_text(encoding='utf-8'))
    dst=RAIZ/'arte'/'previa'/args.id
    if args.fundo:dst=dst/Path(args.fundo).stem
    if args.exportado:dst=dst/'exportado'
    dst.mkdir(parents=True,exist_ok=True)
    if args.exportado:
        source=RAIZ/'src/main/resources/assets/rptag/fx'/args.id/'frames'
        pngs={int(p.stem[:4]):p for p in source.glob('*_c.jpg')}
    else:pngs={int(p.stem):p for p in raw.glob('[0-9][0-9][0-9][0-9].png')}
    def frame(q):
        return compor(pngs[q],meta,q,mensagem=args.mensagem,fundo=args.fundo,
                      alfa=pngs[q].with_name(f'{q:04d}_a.jpg') if args.exportado else None)
    if args.quadros:
        for q in map(int,args.quadros.split(',')):
            with frame(q) as im:im.save(dst/f'q{q:04d}.png')
    if args.gif:
        images=[frame(q).resize((640,360),Image.Resampling.LANCZOS) for q in sorted(pngs) if q%2==0]
        images[0].save(dst/f'{args.id}.gif',save_all=True,append_images=images[1:],duration=83,loop=0,optimize=True)
        for im in images:im.close()
    print('previa em',dst)

if __name__=='__main__':main()
