import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('perspectiva', Path(__file__).resolve().parents[1] / 'cenas' / 'perspectiva.py')
perspectiva = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perspectiva)


class PerspectivaTest(unittest.TestCase):
    def setUp(self):
        self.p = perspectiva.parametros(20, 0.45)

    def assertPerto(self, a, b):
        self.assertAlmostEqual(a[0], b[0], places=6)
        self.assertAlmostEqual(a[1], b[1], places=6)

    def test_centro_do_titulo_void(self):
        self.assertPerto(perspectiva.projetar(self.p, 0, 0.45, 0), (0.5, 0.45))

    def test_plano_z0_cobre_16x9(self):
        self.assertPerto(perspectiva.projetar(self.p, -8, 4.5, 0), (0, 0))
        self.assertPerto(perspectiva.projetar(self.p, 8, -4.5, 0), (1, 1))

    def test_profundidade_converge(self):
        u, v = perspectiva.projetar(self.p, 8, 0.45, -40)
        self.assertTrue(0.5 < u < 1)
        self.assertAlmostEqual(v, 0.45, places=6)
        u2, _ = perspectiva.projetar(self.p, 8, 0.45, -80)
        self.assertLess(u2, u)

    def test_parametros_blender(self):
        self.assertEqual(self.p['location'], (0.0, 0.45, 20.0))
        self.assertEqual(self.p['sensor_width'], 36.0)
        self.assertAlmostEqual(self.p['lens'], 45.0)
        self.assertEqual(self.p['shift_x'], 0.0)
        self.assertAlmostEqual(self.p['shift_y'], -0.45 / 16)


if __name__ == '__main__':
    unittest.main()
