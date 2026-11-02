# ENTREGA 5.11.0 — o VAZIO virou PEDRA (e aparece com shader)

**Data:** 02/11/2026
**Jar:** `rptag-5.11.0.jar` — **69.300.207 B (66,1 MB)** · md5 **`68a2ec53213dd08d42533999865afe69`**
**Download:** _(preenchido depois da release)_
**Verificação:** `check.py` **796 PASS / 0 FAIL** · servidor subiu sem exceção (0 ERROR)

---

## 1) Por que os tentáculos vermelhos "pararam de aparecer"

Não foi o desenho que quebrou (o código dos raios está lá desde a 5.7.4). Foram **três causas**, e as três
foram consertadas nesta versão:

| # | causa | o que acontecia | conserto na 5.11.0 |
|---|---|---|---|
| 1 | **blend ADITIVO** (`RenderType.lightning()`, `SRC_ALPHA, ONE`) | o raio **soma luz** na tela. Num pack de shader claro a soma estoura em branco e o vermelho **desaparece** — "fica muito claro" | saiu do aditivo. A pedra usa **`RenderType.debugQuads()`**: blend normal (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`), **escreve profundidade**, cor opaca |
| 2 | **o modo vazio vivia só na MEMÓRIA** (`HashMap<UUID,Boolean>`) | **reiniciou o servidor → o `/vazio ligar` se perdeu** e a aura não voltou mais (o `/vazio ligar` não "gruda" sozinho) | agora fica **salvo no mundo** (`data/rptag_states.dat`, tag `Vazios`) e **volta no boot** |
| 3 | **o pacote da aura era enviado UMA vez**, no `/vazio ligar` | quem **entrava depois** (ou dava F3+T / trocava de mundo) nunca recebia o pacote → **não via nada** | o servidor **reenvia a aura a cada 3 s** e também **no login** |

Ou seja: se você ligou o vazio e depois reiniciou o servidor, a aura sumia mesmo — não era impressão.

## 2) Agora é pedra de verdade, não partícula

Você pediu: *"não quero mais aquelas partículas vermelhas em volta do personagem, quero apenas partículas
pétreas"* e *"melhora as partículas para ser algo mais concreto do que apenas partículas"*.

**O vermelho saiu de tudo que circula o corpo.** A única coisa vermelha que ficou são **os dois olhos no
rosto** (eles não são aura — são a cara do vazio).

O que aparece hoje em `/vazio ligar`:

- **9 tentáculos de PEDRA** em volta do corpo, em **geometria sólida** (classe nova `TentaculosVazioFx`):
  - cada um é um **tubo de seção quadrada** — 4 faces por segmento, com **sombra por face** (a face virada
    pra luz é clara, a de trás é quase preta), **nós** que engrossam e afinam, **veios escuros** (rachaduras)
    e um **bico de pedra** na ponta;
  - **brota do chão** (0,8 s), fica de pé **4 a 9 s** e **afunda** (0,7 s); ao afundar, renasce em outro
    ângulo — nunca fica congelado;
  - tem **pedrinhas soltas** em volta da base (o chão "cedeu" ali);
  - quem **é** o vazio vê 2/3 deles e mais baixos (a tela dele tem que ficar legível); no F1 do próprio, os
    que atravessariam a mira somem.
- **Manto preto** no corpo continua (o preto é protagonista desde a 5.7.6), mas agora com **poeira de pedra**
  subindo junto no lugar dos veios roxos.
- **Partículas pétreas** no servidor: **pedaços de BLOCO de verdade** — `stone`, `cobblestone` e `deepslate`
  com gravidade — caindo do corpo, saindo das fendas do chão e marcando o rastro.
- **Rastro de caos** agora é **cascalho + poeira de pedra** (não é mais brasa/sangue).
- **A transformação** (`/vazio ligar`) é uma **explosão de lascas de pedra** + anel de cascalho subindo do
  chão (antes era rajada de raios vermelhos).

## 3) Os tentáculos aparecem com shader

Não é promessa de "vai ficar mais visível": mudou a **técnica**. Antes era blend aditivo (soma luz) — o
shader claro engolia. Agora é **geometria opaca com z-buffer**: a pedra escurece com o ambiente, tapa o que
está atrás dela e não depende de soma de luz. Funciona com Sodium/Iris e com o jogo puro.

---

## Como testar em 2 minutos

1. `/vazio ligar` — você é o vazio.
2. Olhe para os lados: **tentáculos de pedra** subindo e afundando em volta de você (o chão soltando cascalho).
3. Ligue o shader (Iris) — **continua lá**, agora escuro e com volume.
4. Peça para outro jogador entrar **depois** — ele **também vê** (antes não via).
5. Reinicie o servidor e entre de novo: **o modo vazio continua ligado** (antes desligava sozinho).
6. `/vazio desligar` — tudo some na hora.

---

## Arquivos

- `src/main/java/dev/rptag/client/TentaculosVazioFx.java` — **novo**: os tentáculos de pedra (geometria).
- `src/main/java/dev/rptag/client/RaiosVazioFx.java` — **apagado** (eram os raios aditivos).
- `src/main/java/dev/rptag/NeptuneEventos.java` — aura pétrea, estilhaços de pedra, transformação de pedra,
  `reenviarAura()` e `restaurarDeuses()`.
- `src/main/java/dev/rptag/RPWorldData.java` — tag `Vazios` (quem é o vazio sobrevive ao restart).
- `src/main/java/dev/rptag/ServerEvents.java` — restaura no boot e reenvia a aura no login.
- `previa/vazio_5.11.0.png` — a prévia antes × depois (o que você está vendo).
- `arte/previa_vazio_pedra.py` — o script que gera a prévia.
