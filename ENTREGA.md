# RP Tag 5.43.4 — o guarda-chuva agora é segurado que nem no Vampirism

**O arquivo para baixar:**
[rptag-5.43.4.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.43.4/rptag-5.43.4.jar)
· 37.146.503 bytes · `md5 cf11014b30fb2609ed68349cf68cf6b8`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.43.4

> **Tire o jar antigo da pasta `mods`** — servidor **e** cliente.

---

## 1. As cores do umbrella (a sua pergunta)

São **8**: `sangue`, `noite`, `juizo`, `sombra`, `veneno`, `luar`, `ouro`, `rosa`.

**Do jeito de jogador — TINGIR:** o umbrella que você já tem + **1 ingrediente**, no crafting,
em qualquer posição:

| cor | ingrediente | | cor | ingrediente |
|---|---|---|---|---|
| **sangue** | black dye | | **veneno** | spider eye |
| **noite** | coal | | **luar** | lapis lazuli |
| **juizo** | crying obsidian | | **ouro** | gold ingot |
| **sombra** | gunpowder | | **rosa** | red dye |

**Craftar do zero:** 3 fios + 1 lã + 1 graveto — `red_wool` sangue · `black_wool` noite ·
`purple_wool` juizo · `gray_wool` sombra · `green_wool` veneno · `blue_wool` luar ·
`yellow_wool` ouro · `pink_wool` rosa.

**ADM:** `/umbrella cores` · `/umbrella dar` · `/umbrella dar <jogador> <0..7>` · `/umbrella info`
· `/umbrella abrir` · `/umbrella fechar`.

---

## 2. A POSIÇÃO na mão: copiada do Vampirism

Você mandou olhar como o Vampirism faz. Fui no modelo deles e **copiei os números** —
`assets/vampirism/models/item/umbrella.json`:

| contexto | rotação | translação | escala |
|---|---|---|---|
| 3ª pessoa (mão direita) | `[81, -85, -2]` | `[-1.391, 2.7826, 15.303]` | `[1, 1.7737, 1]` |
| 3ª pessoa (mão esquerda) | `[81, 90, 0]` | `[-1.391, 2.7826, 15.303]` | `[1, 1.7737, 1]` |
| 1ª pessoa | `[22, -5, 2]` | `[-3.478, 7.6521, 0]` | `[1, 1.6956, 0.9652]` |

**Por que o antigo estava deitado do seu lado:** o `.obj` que você mandou já vem com a cúpula
pra cima (e o loader do NeoForge não reorienta nada), mas o mod girava `[-180,-90,-90]` como se
ele viesse deitado — isso jogava o guarda-chuva na horizontal, com o cabo pra cima. Era
exatamente o que aparecia na sua print.

O **aberto e o fechado agora usam a mesma inclinação**: quando você abre, o guarda-chuva não
"pula" de lugar — ele só desenrola. As 8 cores usam a mesma pose (a cor muda o tecido, não o
jeito de segurar).

Prévia das poses lado a lado, com o corpo do jogador do lado pra dar escala:
`arte/previa_poses.png` (gerada por `arte/previa_poses_candidatas.py`).

---

## 3. A animação antiga em cima da cabeça saiu (feito na 5.43.3)

Existiam **duas** coisas desenhando guarda-chuva: a cúpula de cubos 3D animada sobre a cabeça
(5.37.0) e o modelo `.obj` na mão (5.42.0). A antiga foi **apagada** —
`CamadaUmbrella.java` e as 8 texturas `dome_*.png` saíram do mod. O HUD (sprite da cor e os
segundos) e as 16 receitas continuam.

---

## 4. A praça do vazio: parei de mexer nela (também na 5.43.3)

O que nascia na praça eram as **8 colunas de obsidiana com lanterna de alma** da borda, e o
**relevo** passava o pincel por dentro dela.

- Pilares **desligados** (`PILARES_DA_PRACA = false`) — não nascem nem em mundo novo nem em
  `/vazio relevo`.
- O relevo confere o raio da praça **antes** de pintar o chão e de rebaixar coluna.
- A praça continua sendo montada **uma única vez na vida do mundo**.

---

## Testes

- `check.py`: **1355 PASS / 0 FAIL** (18 provas novas nesta rodada)
- `arte/auditar.py`: 0 erros · `arte/validar.py`: 54 ok, 0 falhas
- Servidor: `RP Tag 5.43.4 (rptag)` → `Done (1.799s)!`, **zero** exceções
- O guarda-chuva segue no jogo: 2 `.obj` + 16 modelos (8 cores × aberto/fechado) + 16 receitas

**Se ainda ficar torto na sua mão:** me manda uma print e eu ajusto o número exato (a pose
inteira está nas `display`, dá pra mexer em segundos).
