import importlib.util
from pathlib import Path
import tempfile
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location('previa', Path(__file__).resolve().parents[1] / 'previa.py')
previa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(previa)

class CompositorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2] / '.superpowers')
        self.root = Path(self.tmp.name)
        self.meta = {'markers': [], 'preview': {'message': 'EXEMPLO'}}
        self.fundo = self.root / 'fundo.png'
        Image.new('RGB', (1280,720), (30,90,120)).save(self.fundo)

    def tearDown(self):
        self.tmp.cleanup()

    def test_compor_preserva_gameplay_com_alfa_zero(self):
        png = self.root / 'overlay.png'
        Image.new('RGBA', (1280,720), (255,0,0,0)).save(png)
        result = previa.compor(png, self.meta, 72, fundo=self.fundo)
        self.assertEqual(result.getpixel((10,10)), (30,90,120))

    def test_jpeg_cor_e_alfa_compostos(self):
        cor, alfa = self.root/'cor.jpg', self.root/'alfa.jpg'
        Image.new('RGB',(1280,720),(200,30,10)).save(cor)
        mask=Image.new('L',(1280,720),0)
        mask.paste(255,(640,0,1280,720));mask.save(alfa)
        result = previa.compor(cor,self.meta,72,fundo=self.fundo,alfa=alfa)
        self.assertEqual(result.getpixel((10,10)), (30,90,120))
        self.assertLess(max(abs(a-b) for a,b in zip(result.getpixel((1200,10)),(200,30,10))),4)

    def test_mensagem_argumento_sobrepoe_metadata(self):
        png=self.root/'overlay.png'
        Image.new('RGBA',(1280,720),(0,0,0,0)).save(png)
        explicit=previa.compor(png,self.meta,72,mensagem='MINHA MENSAGEM')
        same=previa.compor(png,{'markers': [],'preview':{'message':'MINHA MENSAGEM'}},72)
        self.assertEqual(explicit.tobytes(),same.tobytes())

if __name__=='__main__': unittest.main()



class EnquadrarTest(unittest.TestCase):
    """O cover da previa tem que bater com FxTimeline.cobrir: 16:9 centralizado, sem deformar."""

    def _referencia(self):
        im = Image.new('RGB', (1280, 720), (0, 0, 0))
        im.paste((255, 0, 0), (620, 340, 660, 380))   # quadrado 40x40 no centro
        return im

    def _caixa_vermelha(self, im):
        px = im.load()
        xs = [x for x in range(im.width) for y in (im.height // 2,) if px[x, y][0] > 200 and px[x, y][1] < 60]
        ys = [y for y in range(im.height) for x in (im.width // 2,) if px[x, y][0] > 200 and px[x, y][1] < 60]
        return max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2

    def test_16_10_corta_os_lados_sem_deformar(self):
        out = previa.enquadrar(self._referencia(), 1152, 720)
        self.assertEqual(out.size, (1152, 720))
        w, h, cx, cy = self._caixa_vermelha(out)
        self.assertLessEqual(abs(w - h), 2)
        self.assertLessEqual(abs(cx - 576), 2)
        self.assertLessEqual(abs(cy - 360), 2)

    def test_21_9_corta_em_cima_e_embaixo_sem_deformar(self):
        out = previa.enquadrar(self._referencia(), 1680, 720)
        self.assertEqual(out.size, (1680, 720))
        w, h, cx, cy = self._caixa_vermelha(out)
        self.assertLessEqual(abs(w - h), 2)
        self.assertAlmostEqual(w, 40 * 1680 / 1280, delta=3)
        self.assertLessEqual(abs(cx - 840), 2)
        self.assertLessEqual(abs(cy - 360), 2)
