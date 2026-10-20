# arte/ — cenas do Blender dos avisos em vídeo

Cada aviso em vídeo nasce de um script Python que monta a cena **do zero**
(reproduzível, sem `.blend` salvo à mão) e renderiza os quadros.

| Passo | Comando |
|---|---|
| Renderizar (144 quadros, ~1,5 min) | `blender -b --factory-startup -P arte/cenas/morte.py` |
| Só alguns quadros | `blender -b --factory-startup -P arte/cenas/morte.py -- --quadros 20,60,94,125` |
| Abrir a cena no Blender | `blender -b --factory-startup -P arte/cenas/morte.py -- --quadros 0 --salvar` → `arte/morte.blend` |
| Prévia (quadros + GIF) | `python arte/previa.py morte --quadros 20,60,94,125 --gif` |
| Exportar pro jar | `python arte/exportar.py morte` |
| Validar os assets | `python arte/validar.py` (o `check.py` chama) |

`blender` = `C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`.

- Os tempos vêm de `src/main/java/dev/rptag/client/MorteTempos.java` (fonte única).
- O texto ("AVISO" + mensagem) **não** vai no vídeo: é desenhado pelo código.
- `render/` e `previa/` são gerados e ficam fora do git.

## Proximos avisos: previas de Inferno e Gelo

As cenas `arte/cenas/inferno.py` e `arte/cenas/gelo.py` compartilham
`elementais.py` e as primitivas do piloto. Cada uma gera 144 PNG RGBA,
marcadores e `inspecao.json` (camera, objetos, resolucao e ausencia de texto).

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b --factory-startup -P arte/cenas/inferno.py
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b --factory-startup -P arte/cenas/gelo.py
python arte/previa.py inferno --quadros 24,60,108 --gif
python arte/previa.py gelo --quadros 24,60,108 --gif
```

Estado: previas para avaliacao visual. Ainda sem exportacao para os assets do
jar e sem mudancas nos efeitos Java. As fontes continuam em 4.2.0.

## Redesign hibrido (em producao)

`arte/previa.py` aceita `--fundo CAMINHO`, `--mensagem TEXTO` e `--exportado`
para compor o par JPEG cor/alfa. O fade ja exportado nao e aplicado duas vezes.
As referencias `arte/referencias/gameplay_claro.png` e `gameplay_escuro.png`
sao capturas do cliente local em mundo plano, modo espectador, sem chat visivel.
O texto nas previas e aproximado, com contorno para conferir leitura.
A previa anterior foi preservada em `arte/referencias/elementais_v1.py`.

Testes do compositor: `python -m unittest discover -s arte/tests -v`.
