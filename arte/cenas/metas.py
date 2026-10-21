"""Metadados de cada aviso (marcadores, fade, previa) — Python puro, sem bpy."""
FPS = 24


def _meta(fx, seed, eventos, fade_in, preview):
    return {'id': fx, 'seed': seed, 'markers': [{'name': n, 'frame': round(t*FPS)} for n, t in eventos],
            'fade': {'in': [0, fade_in], 'out': [4.8, 6.]}, 'preview': preview}


def _elementais(fx,seed):
    eventos=[('impacto',0 if fx=='inferno' else .12),('texto_entra',.3 if fx=='inferno' else .4),('texto_sai',4.8)]
    eventos += [('crepitar',.5+.45*i) for i in range(11)] if fx=='inferno' else [('sino',t) for t in (.5,1.2,2.,3.)]
    eventos += [('fim',5.1)]
    return {'id':fx,'seed':seed,'markers':[{'name':n,'frame':round(t*FPS)} for n,t in eventos],
        'fade':{'in':[0,.35 if fx=='inferno' else .4],'out':[4.8,6.]},
        'preview':{'color':[255,173,65] if fx=='inferno' else [210,240,255],
        'message':'A FORNALHA DESPERTOU' if fx=='inferno' else 'O FRIO TOMOU CONTA',
        'title_y':.45,'message_y':.45+25/240}}



def metadata(fx, seed):
    if fx in ('inferno', 'gelo'):
        return _elementais(fx, seed)
    if fx == 'void':   # VoidAvisoFx: portal na entrada, RES 1.5/2.7/3.9, teleporte em DUR-0.9
        return _meta(fx, seed, [('impacto', 0), ('texto_entra', .6), ('texto_sai', 4.8)]
                     + [('ressoar', t) for t in (1.5, 2.7, 3.9)] + [('fim', 5.1)], .5,
                     {'color': [180, 160, 255], 'message': 'A REALIDADE SE DESFEZ',
                      'title_y': .45, 'message_y': .45 + 25/240})
    if fx == 'divino':  # DivinoAvisoFx: sinos CT, beacon em DUR-0.9
        return _meta(fx, seed, [('texto_entra', .35), ('texto_sai', 4.8)]
                     + [('sino', t) for t in (.05, .30, .55, .80, 1.10)] + [('fim', 5.1)], .5,
                     {'color': [255, 240, 190], 'message': 'OS CEUS SE ABRIRAM',
                      'title_y': .42, 'message_y': .42 + 25/240})
    raise ValueError('aviso sem metadados: %r' % fx)
