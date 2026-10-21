import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('metas', Path(__file__).resolve().parents[1] / 'cenas' / 'metas.py')
metas = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metas)

# saida do comum.metadata antes da extracao (4.5.0): nao pode mudar
INFERNO = json.loads('{"id":"inferno","seed":482,"markers":[{"name":"impacto","frame":0},{"name":"texto_entra","frame":7},{"name":"texto_sai","frame":115},{"name":"crepitar","frame":12},{"name":"crepitar","frame":23},{"name":"crepitar","frame":34},{"name":"crepitar","frame":44},{"name":"crepitar","frame":55},{"name":"crepitar","frame":66},{"name":"crepitar","frame":77},{"name":"crepitar","frame":88},{"name":"crepitar","frame":98},{"name":"crepitar","frame":109},{"name":"crepitar","frame":120},{"name":"fim","frame":122}],"fade":{"in":[0,0.35],"out":[4.8,6.0]},"preview":{"color":[255,173,65],"message":"A FORNALHA DESPERTOU","title_y":0.45,"message_y":0.5541666666666667}}')
GELO = json.loads('{"id":"gelo","seed":618,"markers":[{"name":"impacto","frame":3},{"name":"texto_entra","frame":10},{"name":"texto_sai","frame":115},{"name":"sino","frame":12},{"name":"sino","frame":29},{"name":"sino","frame":48},{"name":"sino","frame":72},{"name":"fim","frame":122}],"fade":{"in":[0,0.4],"out":[4.8,6.0]},"preview":{"color":[210,240,255],"message":"O FRIO TOMOU CONTA","title_y":0.45,"message_y":0.5541666666666667}}')


def frames(meta, nome):
    return [m['frame'] for m in meta['markers'] if m['name'] == nome]


class MetasTest(unittest.TestCase):
    def test_inferno_e_gelo_identicos(self):
        self.assertEqual(json.loads(json.dumps(metas.metadata('inferno', 482))), INFERNO)
        self.assertEqual(json.loads(json.dumps(metas.metadata('gelo', 618))), GELO)

    def test_void(self):
        m = metas.metadata('void', 615)
        self.assertEqual(frames(m, 'impacto'), [0])
        self.assertEqual(frames(m, 'ressoar'), [36, 65, 94])
        self.assertEqual(frames(m, 'fim'), [122])
        self.assertEqual(m['fade'], {'in': [0, 0.5], 'out': [4.8, 6.0]})
        self.assertEqual(m['preview']['title_y'], 0.45)
        self.assertAlmostEqual(m['preview']['message_y'], 0.45 + 25 / 240)
        self.assertEqual(m['preview']['color'], [180, 160, 255])
        self.assertEqual(m['preview']['message'], 'A REALIDADE SE DESFEZ')

    def test_divino(self):
        m = metas.metadata('divino', 733)
        self.assertEqual(frames(m, 'sino'), [1, 7, 13, 19, 26])
        self.assertEqual(frames(m, 'fim'), [122])
        self.assertEqual(m['fade'], {'in': [0, 0.5], 'out': [4.8, 6.0]})
        self.assertEqual(m['preview']['title_y'], 0.42)
        self.assertAlmostEqual(m['preview']['message_y'], 0.42 + 25 / 240)
        self.assertEqual(m['preview']['color'], [255, 240, 190])
        self.assertEqual(m['preview']['message'], 'OS CEUS SE ABRIRAM')

    def test_divino_cor_jpeg_75_os_outros_padrao(self):
        self.assertEqual(metas.metadata('divino', 733)['jpeg'], {'cor': 75})
        for fx, sd in (('void', 615), ('inferno', 482), ('gelo', 618)):
            self.assertNotIn('jpeg', metas.metadata(fx, sd))

    def test_id_desconhecido(self):
        with self.assertRaises(ValueError):
            metas.metadata('sangue', 1)


if __name__ == '__main__':
    unittest.main()
