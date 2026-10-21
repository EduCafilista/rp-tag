import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('exportar', Path(__file__).resolve().parents[1] / 'exportar.py')
exportar = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exportar)


class QualidadeTest(unittest.TestCase):
    def test_padrao_85_90(self):
        self.assertEqual(exportar.qualidades({}), (85, 90))

    def test_cor_da_cena(self):
        self.assertEqual(exportar.qualidades({'jpeg': {'cor': 75}}), (75, 90))

    def test_alfa_nunca_abaixo_de_90(self):
        self.assertEqual(exportar.qualidades({'jpeg': {'cor': 75, 'alfa': 60}}), (75, 90))


if __name__ == '__main__':
    unittest.main()
