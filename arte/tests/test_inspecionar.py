import importlib.util
from pathlib import Path
import tempfile
import json
import unittest
from PIL import Image
spec=importlib.util.spec_from_file_location('inspecionar',Path(__file__).resolve().parents[1]/'inspecionar.py')
inspecionar=importlib.util.module_from_spec(spec)
spec.loader.exec_module(inspecionar)

class InspecaoTest(unittest.TestCase):
    def test_rejeita_texto_ausente_e_quadro_corrompido(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2]/'.superpowers') as tmp:
            pasta=Path(tmp)
            (pasta/'marcadores.json').write_text(json.dumps({'id':'teste','markers':[{'name':'fora','frame':144}]}))
            (pasta/'inspecao.json').write_text(json.dumps({'text_in_scene':True,'rendered_frames':[12]}))
            (pasta/'0012.png').write_bytes(b'PNG quebrado')
            erros=inspecionar.validar('teste',[12,36],raiz=pasta)
            self.assertTrue(any('texto' in e.lower() for e in erros))
            self.assertTrue(any('36' in e for e in erros))
            self.assertTrue(any('12' in e for e in erros))
            self.assertTrue(any('marcador' in e.lower() for e in erros))

    def test_quadro_valido_sem_texto(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2]/'.superpowers') as tmp:
            pasta=Path(tmp)
            (pasta/'marcadores.json').write_text(json.dumps({'id':'teste','markers':[{'name':'impacto','frame':12}]}))
            (pasta/'inspecao.json').write_text(json.dumps({'text_in_scene':False,'rendered_frames':[12]}))
            Image.new('RGBA',(1280,720)).save(pasta/'0012.png')
            self.assertEqual(inspecionar.validar('teste',[12],raiz=pasta),[])

    def test_luminancia_caixa(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2]/'.superpowers') as tmp:
            arq=Path(tmp)/'q.png'
            im=Image.new('RGBA',(100,100),(0,0,0,0))
            for x in range(50):
                for y in range(100):im.putpixel((x,y),(255,255,255,255))
            im.save(arq)
            self.assertAlmostEqual(inspecionar.luminancia(str(arq),(0,0,.5,1)),1.0,places=3)
            self.assertAlmostEqual(inspecionar.luminancia(str(arq),(.5,0,1,1)),0.0,places=3)
            meio=Path(tmp)/'m.png';Image.new('RGBA',(100,100),(255,255,255,128)).save(meio)
            self.assertAlmostEqual(inspecionar.luminancia(str(meio),(0,0,1,1)),128/255,places=3)
