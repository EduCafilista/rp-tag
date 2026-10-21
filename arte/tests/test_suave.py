import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('suave', Path(__file__).resolve().parents[1] / 'cenas' / 'suave.py')
suave = importlib.util.module_from_spec(spec)
spec.loader.exec_module(suave)


def max_salto(fn, a, b, n=2000):
    xs = [a + (b - a) * i / n for i in range(n + 1)]
    ys = [fn(x) for x in xs]
    return max(abs(ys[i + 1] - ys[i]) for i in range(n))


def continua(fn, a, b):
    """Um degrau mantem o salto quando a amostragem dobra; uma rampa o reduz a metade."""
    return max_salto(fn, a, b, 4000) < .6 * max_salto(fn, a, b, 2000)


class SuaveTest(unittest.TestCase):
    def test_zona_um_dentro_zero_fora(self):
        self.assertEqual(suave.zona(.5, .5, .25, .75, .40, .58, .04), 1.0)
        self.assertEqual(suave.zona(.1, .5, .25, .75, .40, .58, .04), 0.0)
        self.assertEqual(suave.zona(.5, .9, .25, .75, .40, .58, .04), 0.0)

    def test_zona_sem_degrau(self):
        # varrendo a borda, nenhum passo pequeno muda o peso de uma vez
        self.assertTrue(continua(lambda u: suave.zona(u, .5, .25, .75, .40, .58, .04), 0, 1))
        self.assertTrue(continua(lambda v: suave.zona(.5, v, .25, .75, .40, .58, .04), 0, 1))

    def test_janela_texto_sem_degrau(self):
        self.assertEqual(suave.janela_texto(0.5), 0.0)
        self.assertEqual(suave.janela_texto(3.0), 1.0)
        self.assertEqual(suave.janela_texto(5.5), 0.0)
        self.assertTrue(continua(suave.janela_texto, 0, 6))

    def test_anel_sobre_texto_sem_degrau(self):
        self.assertEqual(suave.anel_sobre_texto(3.0), 1.0)
        self.assertEqual(suave.anel_sobre_texto(.5), 0.0)
        self.assertEqual(suave.anel_sobre_texto(6.0), 0.0)
        self.assertTrue(continua(suave.anel_sobre_texto, 0, 8))

    def test_degrau_seria_reprovado(self):
        # o criterio pega o bug que o revisor achou (if seco em vez de rampa)
        self.assertFalse(continua(lambda t: 1. if .8 < t < 5. else 0., 0, 6))


if __name__ == '__main__':
    unittest.main()
