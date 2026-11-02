# ENTREGA 5.10.1 — o título ficou "bonitinho" de verdade + `/title` vanilla fora do jogo

**Data:** 02/11/2026
**Jar:** `rptag-5.10.1.jar` — **69.298.365 B (66,1 MB)** · md5 **`53b8fafb7863ac68de413a6768ceec77`**
**Download:** https://github.com/EduCafilista/rp-tag/releases/download/v5.10.1/rptag-5.10.1.jar
**Release:** 431748405 (v5.10.1) · asset 702553027
**Verificação:** `check.py` **789 PASS / 0 FAIL** · servidor `Done`, 0 exceções · testes por RCON abaixo

---

## 1) O título agora tem relevo, moldura e "selo"

Antes era **só a palavra na cor da classe**: `[Chronos] Nephisto [O Ferreiro]` — tudo do mesmo
jeito, colchete com o mesmo peso da letra. Agora:

| peça | como ficou |
|---|---|
| **a etiqueta** (`Chronos`) | **gradiente letra por letra**: `tom = cor × (1,55 − 1,02·u)` — começa claro (dá o "brilho de metal") e termina no tom da classe. Tudo dentro do que o Minecraft aceita: cor por caractere. |
| **os colchetes** `[ ]` | **55 % da cor** e **sem negrito**: viram moldura, não competem com o nome. |
| **a etiqueta em si** | **negrito** — parece um selo/placa. |
| **o título pessoal** (`O Ferreiro`) | **itálico**, com um espaço antes — é apelido, não é placa. |
| **o nome do jogador** | **intocado** (era pedido implícito: o título enfeita, o nome é do jogador). |
| **cor escura** | se a luminância < 0,30, a rampa **mistura branco** (`0,68·(1−0,88u)`), senão `Sombra`/`Vazio`/`Morto` viravam borrão preto no TAB. |
| **hover** (mouse em cima) | mostra **`Classe: Chronos  #C9A6FF`** — no chat e no TAB (no nametag não tem mouse). |

**Onde aparece:** TAB, chat e nametag **acima da cabeça** — os três usam a **mesma classe**
(`TituloEstilo`), então nunca mais vão divergir (antes o cliente tinha a própria cópia da regra).

Paleta inteira com o estilo novo (a cor de fábrica de cada classe):

| | | | |
|---|---|---|---|
| `[Sagrado]` #FFD75E | `[Warden]` #6FB3FF | `[Gelo]` #9BEBFF | `[Fragmento]` #38E0A6 |
| `[Almas]` #2FE0C0 | `[Chronos]` #C9A6FF | `[Sombra]` #9A6BFF | `[Divino]` #FFE9A8 |
| `[Morte]` #FF7B7B | `[Vazio]` #8C8C9E | `[Ferreiro]` #E0A860 | `[Player]` #BFBFBF |

A cor de cada classe continua mudável (`/titulo cor <classe> #RRGGBB`) — o gradiente sai
**daquela** cor, então classe personalizada também ganha o relevo.

## 2) O `/title` **não** é do mod — e agora sai do jogo por padrão

Resposta direta ao que você perguntou: **o `/title` é comando VANILLA do Minecraft**
(`/title <alvo> title|subtitle|actionbar|times|clear|reset`), já vem no jogo puro, sem mod
nenhum. O comando do RP Tag é o **`/titulo`** (com "i").

Como os dois se confundiam, na 5.10.1 o `/title` **sai do jogo por padrão**:

```
/titulo ...                       ← é o do mod, funciona sempre
/rp admin titlevanilla on         ← devolve o /title vanilla (fica salvo no mundo)
/rp admin titlevanilla off        ← tira de novo (é o padrão)
```

Sai na hora, sem reiniciar, e a lista de comandos dos jogadores é reenviada na hora.
Serve também pra quem usa **datapack/mapa pronto** com `execute run title ...` — liga e volta.

Teste real no servidor de testes (RCON):

```
title @a times 20 60 20   →  Unknown or incomplete command, see below for error   ← removido ✔
help title                →  Unknown command or insufficient permissions          ← removido ✔
/rp admin titlevanilla on →  O /title ... está de volta
title @a times 20 60 20   →  No player was found                                  ← está de volta ✔
help title                →  /title <targets> (clear|reset|title|subtitle|actionbar|times)
/rp admin titlevanilla off→  O /title (vanilla) foi REMOVIDO
title @a times 20 60 20   →  Unknown or incomplete command                        ← removido ✔
```

> **Por que isso deu trabalho (e não dava pra "chutar"):** o Brigadier guarda os filhos da
> raiz do comando em **três** mapas — `children`, `literals` e `arguments`. O
> `getChildren()` que todo mundo usa é só a view do `children`, e **o parser do jogo lê o
> `literals`**. A remoção deixava o log bonito (`title=false`, `getChild("title")` nulo) e o
> `/title` continuava rodando — o nó ainda estava no `literals`. A 5.10.1 limpa **os dois**
> mapas (com `setAccessible` no campo, porque o Brigadier não tem `removeChild`). Está
> documentado no `TituloVanilla.java` pra ninguém cair nisso de novo.

---

## Como testar em 2 minutos

1. Entra no servidor com o mod. No TAB você vê `[SuaClasse] Nome [Título Pessoal]` —
   etiqueta com degradê, colchete apagado, pessoal em itálico.
2. Digita `/title @a times 20 60 20` → deve dar **Unknown or incomplete command**.
3. Digita `/titulo definir @a Chronos` → funciona (é o do mod).
4. Digita `/rp admin titlevanilla on` e repete o passo 2 → agora o `/title` funciona.
5. `/rp admin titlevanilla off` → volta a ficar bloqueado.

---

## Arquivos

- `src/main/java/dev/rptag/TituloEstilo.java` — **novo**: `etiqueta()`, `pessoal()`, `gradiente()`, `tom()`, `COLCHETE = 0.55f`.
- `src/main/java/dev/rptag/TituloVanilla.java` — **novo**: tira/devolve o `/title`, limpa `children` + `literals`.
- `Titulos.java` / `client/ClientTitulos.java` — passaram a usar `TituloEstilo` (TAB, chat e nametag).
- `RPWorldData.java` — flag `comandoTitleVanilla` (padrão `false`, salva no mundo).
- `RPCommands.java` — `/rp admin titlevanilla on|off` (+ ajuda).
- `previa/titulos_5.10.1.png` — a prévia antes × depois (o que você está vendo).
- `arte/previa_titulos_estilo.py` — o script que gera a prévia.
