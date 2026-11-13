# RP Tag 5.43.3 — a cúpula antiga do guarda-chuva saiu, e a praça do vazio ficou em paz

**O arquivo para baixar:**
[rptag-5.43.3.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.43.3/rptag-5.43.3.jar)
· 37.165.973 bytes · `md5 33650cb8e96ee4b02e4a62e297e09061`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.43.3

> **Tire o jar antigo da pasta `mods`** — servidor **e** cliente.

---

## 1. Como pegar as outras cores de umbrella

São **8 cores**: `sangue`, `noite`, `juizo`, `sombra`, `veneno`, `luar`, `ouro`, `rosa`.

**Na mão (jeito de jogador) — é só TINGIR.** O umbrella que você já tem + **1 ingrediente**, no
crafting, em qualquer posição (shapeless):

| cor | ingrediente | | cor | ingrediente |
|---|---|---|---|---|
| **sangue** | black dye | | **veneno** | spider eye |
| **noite** | coal | | **luar** | lapis lazuli |
| **juizo** | crying obsidian | | **ouro** | gold ingot |
| **sombra** | gunpowder | | **rosa** | red dye |

E dá pra **craftar do zero**, também do jeito normal (3 fios + 1 lã + 1 graveto):

`red_wool` → sangue · `black_wool` → noite · `purple_wool` → juizo · `gray_wool` → sombra ·
`green_wool` → veneno · `blue_wool` → luar · `yellow_wool` → ouro · `pink_wool` → rosa

**Como ADM:** `/umbrella cores` (a lista) · `/umbrella dar` (sangue) ·
`/umbrella dar <jogador> <0..7>` · `/umbrella info` (diz a cor que está na sua mão) ·
`/umbrella abrir` e `/umbrella fechar`.

---

## 2. A ANIMAÇÃO ANTIGA DO GUARDA-CHUVA SAIU

Você estava certo em reclamar: existiam **duas** coisas desenhando guarda-chuva no mod.

| o que | desde | agora |
|---|---|---|
| a cúpula de cubos 3D animada **em cima da cabeça** (desdobrava, encolhia, balançava) | 5.37.0 | **APAGADA** (`CamadaUmbrella.java` fora do mod, mais as 8 texturas `dome_*.png`) |
| o **modelo .obj** de verdade, na mão do jogador | 5.42.0 | **fica** — é o guarda-chuva do jogo |

Nenhuma linha do mod cita mais a camada antiga: o `check.py` varre **todos** os `.java` e cobra
que `CamadaUmbrella`, `quantoAberta`, `ABRIR_MS`, `FECHAR_MS` não voltem. O HUD (o sprite da cor
e os segundos) continua igual, e as 16 receitas continuam de pé.

---

## 3. A praça do vazio: parei de mexer nela

O que você viu nascer na praça eram as **8 colunas de obsidiana com lanterna de alma** da borda
(elas existem desde a 5.13.0) — e, pior, o **relevo** passava o pincel e o rebaixa-colunas **por
dentro da praça** antes de conferir o raio, ou seja, dava para ele mexer no chão da praça.

- Os pilares estão **desligados** (`PILARES_DA_PRACA = false`) — não nascem nem em mundo novo, nem
  em `/vazio relevo`.
- O relevo agora **confere o raio da praça ANTES** de pintar o chão ou rebaixar coluna: dentro da
  praça, ele não encosta em bloco nenhum, nunca mais.
- E a praça continua sendo montada **uma única vez na vida do mundo** (marca no save).

---

## Testes

- `check.py`: **1350 PASS / 0 FAIL** (13 provas novas do pedido 96)
- `arte/auditar.py`: 0 erros · `arte/validar.py`: 54 ok, 0 falhas
- Servidor: `RP Tag 5.43.3 (rptag)` → `Done (1.361s)!`, **zero** exceções
- O guarda-chuva continua no jogo: 2 `.obj` + 16 modelos (8 cores × aberto/fechado) + 16 receitas
