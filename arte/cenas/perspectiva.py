"""Camera em perspectiva sem bpy (testavel em Python puro).

A camera fica em (0, centro_y, distancia) olhando para -z. O plano z = 0
cobre exatamente largura x altura unidades do mundo (o mesmo quadro da
camera ortografica de comum.configurar), e o ponto de fuga fica em
(0, centro_y): e onde o titulo do aviso esta.
"""
SENSOR = 36.0


def parametros(distancia: float, centro_y: float, largura: float = 16.0, altura: float = 9.0) -> dict:
    return {'location': (0.0, float(centro_y), float(distancia)), 'sensor_width': SENSOR,
            'lens': SENSOR * distancia / largura, 'shift_x': 0.0, 'shift_y': -centro_y / largura,
            'largura': largura, 'altura': altura}


def projetar(p: dict, x: float, y: float, z: float) -> tuple:
    """(u, v) normalizado; u a partir da esquerda, v a partir de CIMA (como o Java)."""
    cx, cy, cz = p['location']
    larg, alt = p['largura'], p['altura']
    escala = cz / (cz - z)                  # 1 no plano z = 0
    # shift do Blender = fracao da largura do sensor: desloca o centro do quadro
    u = 0.5 + (x - cx) * escala / larg - p['shift_x']
    v_baixo = 0.5 + (y - cy) * escala / alt - p['shift_y'] * larg / alt
    return u, 1 - v_baixo
