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
