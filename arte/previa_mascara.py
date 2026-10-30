"""Previa da MASCARA DE PEDRA no rosto (mock em PIL) + icone do item.

Não é o render do jogo (esse é Java), mas usa a MESMA textura e a MESMA
geometria da CamadaMascara: 3 painéis, o do meio reto e os laterais virados
~24° para trás, do queixo à testa.

uso: python arte/previa_mascara.py  ->  previa/mascara_no_rosto.png
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RAIZ = Path(__file__).resolve().parent.parent
TEX = RAIZ / 'src/main/resources/assets/rptag/textures/item/mascara_pedra.png'
SAIDA = RAIZ / 'previa'

W, H = 900, 640
PELE = (0xC6, 0x9A, 0x74)
PELE_ESC = (0x9E, 0x74, 0x54)
CABELO = (0x2A, 0x22, 0x1C)


def rosto_64():
    """recorta a região do ROSTO da textura e amplia (NEAREST, pixel art)."""
    img = Image.open(TEX).convert('RGBA')
    rosto = img.crop((0, 0, 32, 32))
    return rosto.resize((384, 384), Image.Resampling.NEAREST)


def main():
    base = Image.new('RGBA', (W, H), (28, 26, 32, 255))
    d = ImageDraw.Draw(base)

    # cabeça do jogador (frente), 8x8x8 -> proporção quadrada
    cx, cy = W // 2, H // 2
    lado = 300
    x0, y0 = cx - lado // 2, cy - lado // 2 - 20
    d.rounded_rectangle([x0, y0, x0 + lado, y0 + lado], radius=18, fill=PELE)
    # cabelo
    d.rectangle([x0 - 10, y0 - 18, x0 + lado + 10, y0 + 46], fill=CABELO)
    d.rounded_rectangle([x0, y0, x0 + lado, y0 + lado], radius=18, fill=None, outline=PELE_ESC, width=6)
    # pescoço e ombros (contexto)
    d.rectangle([cx - 46, y0 + lado - 6, cx + 46, y0 + lado + 60], fill=PELE_ESC)
    d.rounded_rectangle([x0 - 90, y0 + lado + 40, x0 + lado + 90, y0 + lado + 190], radius=30, fill=(60, 52, 70))

    # ---- a máscara: 3 painéis (iguais aos da CamadaMascara)
    # centro = cara inteira; laterais = pedra (textura 32..64, 0..32)
    tex = rosto_64()                       # já é o ROSTO ampliado (384x384)
    tex_full = Image.open(TEX).convert('RGBA')
    pedra = tex_full.crop((32, 0, 64, 32)).resize((384, 384), Image.Resampling.NEAREST)
    topo, basey = y0 + 6, y0 + lado - 4
    alt = basey - topo

    def painel(x_esq, larg, graus, u_ini, u_fim, fonte=None):
        src = fonte if fonte is not None else tex
        recorte = src.crop((u_ini, 0, u_fim, src.height))
        novo_h = alt
        novo_w = max(2, int(abs(larg)))
        p = recorte.resize((novo_w, novo_h), Image.Resampling.NEAREST)
        # os painéis laterais ficam mais escuros (estão virados pra trás)
        if graus != 0:
            esc = Image.new('RGBA', p.size, (0, 0, 0, 70 if abs(graus) < 30 else 110))
            p = Image.alpha_composite(p, esc)
            # perspectiva: encolhe a borda de fora
            p = p.transform((novo_w, novo_h), Image.Transform.AFFINE,
                            (1, 0.10 if graus > 0 else -0.10, 0, 0, 1, 0),
                            resample=Image.Resampling.NEAREST)
        return p

    larg_meio = int(lado * 0.62)          # 4,9 / 8 unidades do modelo
    larg_lat = int(lado * 0.20)           # 1,6 / 8 unidades
    meio = painel(cx - larg_meio // 2, larg_meio, 0, 0, tex.width)
    esq = painel(cx - larg_meio // 2 - larg_lat, larg_lat, 26, 0, int(tex.width * 0.22), pedra)
    dir_ = painel(cx + larg_meio // 2, larg_lat, -26, int(tex.width * 0.78), tex.width, pedra)

    base.alpha_composite(esq, (cx - larg_meio // 2 - larg_lat, topo + 6))
    base.alpha_composite(dir_, (cx + larg_meio // 2, topo + 6))
    base.alpha_composite(meio, (cx - larg_meio // 2, topo))

    # sombra da máscara sobre o rosto
    sombra = base.filter(ImageFilter.GaussianBlur(1))

    # ---- legenda
    f_titulo = None
    d = ImageDraw.Draw(sombra)
    d.text((24, 18), 'MASCARA DE PEDRA (rptag:mascara_pedra)', fill=(240, 236, 226))
    d.text((24, 36), 'cabeca, 3 paineis · textura 64x64 · modelo baseado na mascara do JoJo', fill=(170, 164, 152))

    # ---- ícone do item (16x16 como fica no inventário) e ampliado
    icone = Image.open(TEX).convert('RGBA').crop((0, 0, 32, 32)).resize((64, 64), Image.Resampling.NEAREST)
    icone_g = icone.resize((160, 160), Image.Resampling.NEAREST)
    sombra.alpha_composite(icone_g, (W - 200, H - 200))
    d.rectangle([W - 204, H - 204, W - 36, H - 36], outline=(90, 84, 76))

    SAIDA.mkdir(exist_ok=True)
    destino = SAIDA / 'mascara_no_rosto.png'
    sombra.convert('RGB').save(destino)
    print(destino, destino.stat().st_size)


if __name__ == '__main__':
    main()
