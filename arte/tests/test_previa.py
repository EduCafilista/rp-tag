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

