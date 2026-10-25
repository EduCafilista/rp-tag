# RP Tag 4.9.0 — o tablet saiu

**Jar:** https://github.com/EduCafilista/rp-tag/releases/download/v4.9.0/rptag-4.9.0.jar
(115.810.490 B · `rptag-4.9.0.jar`)

---

## 1. O tablet foi removido — tudo

| Saiu | O que era |
|---|---|
| `rptag:tablet` | o item que abria a tela |
| `FichaScreen`, `FichaWidgets`, `FichaFundo` | a tela inteira (cliente) |
| `FichaRp`, `FichaCodec`, `FichaServer`, `FichaCommands`, `FichaLivro`, `FichaOpenPayload`, `FichaSavePayload` | os 8 capítulos, o salvamento e os canais de rede |
| `ficha_anel.png`, `ficha_buraco.png`, `ficha_nevoa.png` | as texturas da tela |
| `/tablet`, `/ficha` | os comandos |
| `arte/mock_tablet.py`, `arte/mock_resumo.py`, `arte/gerar_texturas_ficha.py`, `tablet_*.png`, `resumo_*.png` | os mocks e as prévias |

O mapa `Fichas` do save também saiu (save antigo só ignora a chave órfã, **nada
quebra**). O protocolo foi de **v6 → v7**: cliente com o mod antigo é recusado com
mensagem de versão, em vez de mandar pacote que não existe mais.

**`/persona` continua** — o personagem em texto (nome, idade, descrição,
personalidade). Nunca dependeu da tela.

---

## 2. `/rp admin regra` saiu — agora vale sozinho

Antes: o admin precisava ligar/desligar `pvp`, `quebrar` e `colocar`.

Agora: **quem está em RP já está protegido**, sem comando nenhum.

- PvP cancelado (corpo a corpo **e** flecha/tridente/poção/TNT);
- não quebra bloco;
- não coloca bloco (balde de água/lava incluso).

Staff com permissão 2 continua passando (precisa montar cenário).

---

## 3. `confirmar` e `cancelar` saíram

`/rp admin vida lote Ana,Beto,@a tirar 1` **aplica na hora** e responde:

```
✔ tirar 1 em 3 jogador(es)
```

---

## 4. Novo: aviso de vida perdida

Quando alguém perde uma vida canônica, todo jogador num **raio de 32 blocos**
(mesmo mundo) recebe no chat:

```
Fulano perdeu uma vida - tome cuidado
```

Quem perdeu **não** recebe essa linha — pra ele já aparece o aviso grande na tela
(com o congelamento e o trovão).

---

## 5. `balao` vs `bolha` (a sua pergunta)

| Comando | O que faz |
|---|---|
| `/rp admin bolha <jogador> on\|off` | só **liga o modo balão**: as falas do jogador saem **só no balão**, não vão pro chat. |
| `/rp admin balao <jogador> on\|off` | **dá a permissão** pra usar o balão (o `/balao`, que personaliza cor/estilo) **e já liga** o modo balão. |

Resumindo: **`bolha` = ligar o balão; `balao` = liberar o balão pra pessoa + ligar.**
Sem a permissão (`allowed`) o `/balao` do jogador não funciona.

---

## 6. Testes desta versão

- `check.py` → **463 PASS / 0 FAIL**
- JUnit → **43/43**
- Servidor NeoForge 21.1.249 real → `Done (0.938s)`
- RCON conferiu: `/tablet` → *Unknown command*; `/ficha` → *Unknown command*;
  `/rp admin regra` → *Incorrect argument*; `/rp admin confirmar` → *Incorrect argument*;
  `/rp admin vida lote …` → `✔ … em N jogador(es)` na hora.
