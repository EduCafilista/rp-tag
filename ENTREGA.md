# RP Tag 5.60.0 — AURA DE ILUMINAÇÃO (`/iluminacao`) com poder

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.60.0/rptag-5.60.0.jar
(também na sua área de trabalho como `rptag-5.60.0.jar`)

---

## O que chegou

A aura do zip que você mandou (`fate-aura-neoforge-1.21.1`), integrada no RP Tag — só a dourada,
como você pediu: **uma aura só, a de ILUMINAÇÃO**.

### O desenho (no corpo do jogador, no mundo — todo mundo vê)
- **círculo mágico dourado** no chão: anel externo, faixa de 24 runas girando ao contrário,
  hexagrama girando, anel tracejado interno e uma onda que expande do centro;
- **coluna de luz** (duas, uma clara dentro da outra) que sobe e afina como chama;
- **hélices de luz** em 3 espirais subindo;
- **anéis ascendentes** que sobem e apagam;
- **16 raios dourados** ao redor;
- **5 PORTÕES DA BABILÓNIA** girando em volta do corpo, cada um com anéis, octagrama e uma arma
  (espada/lança) saindo e voltando;
- partículas douradas + `END_ROD` no chão e subindo.

Tudo **procedural** (sem textura), em brilho aditivo — igual ao zip.

### O PODER (é o que você pediu: "poder para ser usado")
**Portões da Babilónia** — uma rajada de **armas douradas** que saem de portais atrás/em volta do
corpo e voam para onde você está mirando:
- 2 armas por tick, durante 1,5 s (30 ticks);
- **5 s de recarga** (avisa quantos segundos faltam);
- espada e lança alternadas, com rastro de luz e estouro dourado ao acertar;
- **não machuca jogador** (seguro pra RP) — os bichos levam 8 de dano;
- **só funciona com a aura ligada** (desligada, avisa e não gasta recarga).

### Como usar
```
/iluminacao              liga a aura em você
/iluminacao off          desliga
/iluminacao portoes      usa o poder (igual à tecla)
/iluminacao <jogador>    liga em outro            (OP)
/iluminacao off <jogador> desliga em outro        (OP)
```
**Tecla G** = poder (dá pra trocar em Controles → RP Tag → "Portões da Babilônia").

A aura fica **salva no jogador** (sobrevive a morte, relog e troca de dimensão) e é **sincronizada
pra quem vê** você.

## Provas
- `check.py`: **1358 PASS / 0 FAIL** (11 provas novas da aura);
- `arte/auditar.py`: **0 erro(s)**;
- **servidor de teste subiu limpo** com a 5.60.0: `/iluminacao` registrado nas 3 formas,
  `summon rptag:golden_weapon` funcionando, nenhuma exceção no log.

> Nada do resto do mod foi tocado. A única mudança técnica fora da aura foi o canal de rede, que
> subiu de 18 para 19 (o pacote novo da tecla) — servidor e cliente precisam estar na MESMA versão.
