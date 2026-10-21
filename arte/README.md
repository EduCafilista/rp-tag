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

## Inferno e Gelo (4.3.0, no jar)

As cenas `arte/cenas/inferno.py` e `arte/cenas/gelo.py` usam so as primitivas de
`arte/cenas/comum.py` (nao dependem do `morte.py`). Cada uma gera 144 PNG RGBA,
`marcadores.json` e `inspecao.json` (camera, objetos, resolucao, ausencia de texto).
O titulo desses dois avisos fica em `cy = 0.45 h` (mundo y = +0.45); a zona de
leitura (2 a 4,8 s) e x 20-80 %, y 32-66 %.

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b --factory-startup -P arte/cenas/inferno.py
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b --factory-startup -P arte/cenas/gelo.py
python arte/inspecionar.py inferno --quadros 12,36,72,127
python arte/previa.py inferno --quadros 12,36,72,127 --fundo arte/referencias/gameplay_claro.png
python arte/previa.py inferno --gif --fundo arte/referencias/gameplay_escuro.png
python arte/exportar.py inferno
python arte/previa.py inferno --quadros 12,72 --exportado --fundo arte/referencias/gameplay_claro.png
```

Licoes desta rodada (nao repetir): fogo precisa de ruido que SOBE + limiar curto
(borda nitida) + rampa de temperatura; geada so nas bordas com frente irregular
(nunca um veu na tela inteira); Voronoi grande parece pedra rachada (use escala alta);
rastro de meteoro com poucos sprites vira "contas" (use 30+ bem juntos); nao gire
o objeto de uma elipse (ela inclina): anime a posicao ao longo dela.
Gelo exporta ~32 MB (a textura fina de geada pesa no JPEG); Inferno ~14 MB.

## Compositor de previas

`arte/previa.py` aceita `--fundo CAMINHO`, `--mensagem TEXTO` e `--exportado`
para compor o par JPEG cor/alfa. O fade ja exportado nao e aplicado duas vezes.
As referencias `arte/referencias/gameplay_claro.png` e `gameplay_escuro.png`
sao capturas do cliente local em mundo plano, modo espectador, sem chat visivel.
O texto nas previas e aproximado, com contorno para conferir leitura.
A previa anterior foi preservada em `arte/referencias/elementais_v1.py`.

`previa.enquadrar(imagem, largura, altura)` faz o mesmo cover 16:9 do jogo
(`FxTimeline.cobrir`) para conferir cortes 16:10/21:9.

Testes do compositor: `python -m unittest discover -s arte/tests -v`.
