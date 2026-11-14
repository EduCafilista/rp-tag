# RP Tag 5.44.0 — FLUXO: a ametista vermelha, com TODAS as versões do Mine

**O arquivo para baixar:**
[rptag-5.44.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.44.0/rptag-5.44.0.jar)
· 37.163.961 bytes · `md5 d17eb1b3d7e28af5266134842082a7d7`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.44.0

> **Tire o jar antigo da pasta `mods`** — servidor **e** cliente.

---

## O Fluxo — a família inteira da ametista, em vermelho

A ametista do Mine tem **6 formas** + o fragmento. Estão todas aqui, com o mesmo
comportamento e a mesma silhueta — só a pedra muda de roxo para vermelho:

| bloco / item | id | o que faz |
|---|---|---|
| **Bloco de Fluxo** | `rptag:fluxo_block` | cubo cheio, dropa ele mesmo |
| **Fluxo em Formação** | `rptag:fluxo_budding` | **cresce** brotos em volta (igual à ametista em formação) |
| **Broto de Fluxo Pequeno** | `rptag:fluxo_bud_pequeno` | 1º estágio, luz 1 |
| **Broto de Fluxo Médio** | `rptag:fluxo_bud_medio` | 2º estágio, luz 2 |
| **Broto de Fluxo Grande** | `rptag:fluxo_bud_grande` | 3º estágio, luz 4 |
| **Cacho de Fluxo** | `rptag:fluxo_cluster` | maduro, luz 5, dropa **4 fragmentos** |
| **Fragmento de Fluxo** | `rptag:fluxo_shard` | o item da família |

**As texturas são as da VANILLA com a matiz girada** — `arte/gerar_fluxo.py` abre o jar do
cliente, pega os 7 PNGs originais da ametista e passa cada pixel de roxo para vermelho
preservando **luminância, saturação e alfa**. Ou seja: as facetas, os brilhos e as pontas
claras continuam exatamente onde estavam — não é um retângulo vermelho, é a mesma pedra.
Prova no `check.py` (compara pixel a pixel com a original).

Conferência lado a lado (vanilla em cima, Fluxo embaixo): `arte/previa_fluxo.png`.

### Como conseguir

- **Craftar:** 9 fragmentos → 1 Bloco de Fluxo · 1 Bloco de Fluxo → 9 fragmentos
- **Tingir:** 1 ametista + 1 red dye → 1 Fragmento de Fluxo
- **Fluxo em Formação:** 8 fragmentos em volta de 1 glowstone dust
- **No mundo:** planta o Bloco de Fluxo em Formação e ele vai formando brotos sozinho
  (20% por tick aleatório, subindo de estágio até o cacho) — igual à ametista da vanilla
- **ADM:** `/give @s rptag:fluxo_cluster` (troque pelo id que quiser)

**Drops iguais aos da vanilla:** o em formação **não** dropa nada; os brotos só com **toque
suave**; o cacho dá 4 fragmentos (2 se explodir) — conferido rodando as tabelas no servidor.

---

## Bug antigo que apareceu no caminho (e foi corrigido)

As receitas do mod estavam em `data/rptag/recipes/` (plural). **No 1.21 a pasta virou
`recipe/` (singular)** — então o jogo simplesmente ignorava os 16 arquivos de receita do
umbrella: **não dava para craftar nem tingir nenhuma cor dele**. Movi para `recipe/` e para
`loot_table/`, e conferi no servidor: com as receitas do mod o jogo carrega **1310**, sem
elas **1290** — as 20 entram de fato agora.

---

## Testes

- `check.py`: **1367 PASS / 0 FAIL** (12 provas novas do Fluxo)
- `arte/auditar.py`: 0 erros · `arte/validar.py`: 54 ok, 0 falhas
- Servidor: `RP Tag 5.44.0 (rptag)` → `Done (1.500s)!`, **zero** exceções
- Colocado no mundo por RCON: `fluxo_block`, `fluxo_budding`, `fluxo_cluster[facing=up]`,
  `fluxo_bud_grande[facing=east]`, `fluxo_bud_medio[facing=north]` — todos aceitos
- Tabelas de drop rodadas no servidor: cacho → 2/4 fragmentos ✓ · em formação → 0 ✓ ·
  bloco → ele mesmo ✓
