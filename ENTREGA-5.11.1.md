# ENTREGA 5.11.1 — os RAIOS VERMELHOS voltaram (e aparecem com shader, voando também)

**Data:** 02/11/2026
**Jar:** `rptag-5.11.1.jar` — **69.300.167 B (66,1 MB)** · md5 **`df08df649a19630997ab544f8b95b3bb`**
**Download:** https://github.com/EduCafilista/rp-tag/releases/download/v5.11.1/rptag-5.11.1.jar
**Release:** 431835290 (v5.11.1) · asset 702771828
**Verificação:** `check.py` **798 PASS / 0 FAIL** · servidor sobe com **0 ERROR**

---

## Foi erro meu ter trocado a ideia

Você pediu **raios vermelhos saindo de deus**, e a aura tem que funcionar **voando**. Eu troquei por
tentáculos de pedra sem perguntar — desculpa. A pedra era um jeito (errado) de resolver o problema
técnico; **o conceito era seu e eu mudei sozinho**. Fica registrado no `WALKTHROUGH.md`: se for mudar o
**conceito**, pergunto antes.

## O que voltou

- **Raios vermelhos saindo do corpo**, em geometria 3D, **presos no jogador**. Tudo é medido a partir do
  corpo (pé / peito / cabeça), nada do chão — então **voando, caindo, na água ou no exílio os raios vão
  junto com ele**.
- A **transformação** do `/vazio ligar` voltou a ser rajada de **28 raios vermelhos** + onda de choque no
  chão + lascas de pedra levantando com o impacto.

## O que NÃO voltou (o bug continua consertado)

Os raios de antes **sumiam com shader** porque usavam `RenderType.lightning()`, que é **blend aditivo**
(`SRC_ALPHA, ONE` — soma luz). Contra um céu claro de shader, a soma estoura em branco e o vermelho
desaparece.

Agora é `RenderType.debugQuads()`: **blend NORMAL** (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`) + **escreve
profundidade**. O vermelho **mancha** a tela em vez de somar — continua vermelho com Iris, com Sodium e
no jogo puro.

Cada raio é desenhado em **3 passadas**, deslocadas poucos milímetros **na direção da câmera** (nunca no
plano da tela, então não dá z-fighting):

| passada | largura | cor | alpha |
|---|---|---|---|
| **capa** | 2,35× | vermelho-escuro | ~0,50 |
| **corpo** | 1× | vermelho vivo | ~0,92 |
| **núcleo** | 0,38× | laranja quase branco | ~0,98 |

E continua valendo o que impedia a aura de sumir de vez: o modo vazio fica **salvo no mundo** (volta no
boot) e a aura é **reenviada a cada 3 s e no login**.

## "Bizarro" (o que dá o volume)

- **10 raios longos** — 1,6 a 6 blocos — em 360°, pra cima e pra baixo;
- **20 cerdas curtas** — 0,35 a 1,1 bloco — grudadas no corpo, é o que faz a coisa **ferver**;
- o serrilhado é **re-sorteado a cada ~110 ms**: os raios estalam o tempo todo, não é um desenho congelado;
- o conjunto **gira devagar** em volta do corpo;
- quem **é** o vazio vê 2/3 dos raios e mais finos (precisa mirar); no F1 do próprio, nada atravessa a mira.

## Partículas do servidor

Continuam **pétreas**, como você pediu na mensagem anterior: pedaços de **bloco** de verdade
(`stone`, `cobblestone`, `deepslate`) com gravidade, poeira de pedra, rachaduras e rastro de cascalho.
O que saiu foi só o **tentáculo de pedra**, que era geometria e estava no lugar dos raios.

---

## Como testar em 2 minutos

1. `/vazio ligar` — você é o vazio: raios vermelhos estalando em volta do corpo.
2. **Voe** (criativo, `/gamemode c` + voo) — os raios vão junto, no ar.
3. Ligue o **shader** (Iris) — os raios continuam vermelhos (antes sumiam).
4. Chame alguém que **entre depois** — ele também vê (antes não via).
5. Reinicie o servidor e entre — o modo vazio **continua ligado**.
6. `/vazio desligar` — tudo some na hora.

---

## Arquivos

- `src/main/java/dev/rptag/client/RaiosVazioFx.java` — **reescrito**: raios em blend normal, 3 passadas, 3D preso no corpo.
- `src/main/java/dev/rptag/client/TentaculosVazioFx.java` — **apagado**.
- `src/main/java/dev/rptag/NeptuneEventos.java` — transformação com raios vermelhos + lascas de pedra.
- `previa/vazio_5.11.1.png` — a prévia (antes × depois, com shader e voando).
- `arte/previa_raios_vazio.py` — o script que gera a prévia.
