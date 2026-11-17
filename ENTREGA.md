# RP Tag 5.63.0 — aura só brilho, poder secreto (só ADM) e mais dano

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.63.0/rptag-5.63.0.jar
(também na sua área de trabalho como `rptag-5.63.0.jar`)

---

## 1. Nada de mensagem
Saiu o aviso **"Ligue a aura primeiro: /iluminacao"** — e também o da recarga. O poder **não fala
mais nada**: sem aura ligada, sem permissão ou em recarga, simplesmente não acontece nada.

## 2. A aura da iluminação agora é SÓ o brilho
Você pediu: *"tira os efeitos em volta, deixa apenas a aura em volta do player brilhante"*.

**Saiu:** o círculo mágico no chão (anel, 24 runas, hexagrama, anel tracejado, onda), a coluna de
luz, as 3 hélices, os anéis ascendentes, os 16 raios e os **5 Portões** girando em volta do corpo.

**Ficou:** um **brilho dourado em volta do jogador** — três cascas de luz aditivas (uma branca-quente
colada no corpo, uma média e uma larga e fraca), com pulsar leve e o alpha apagando no chão e no
topo (nada de cilindro de corte reto), mais as partículas douradas e o `END_ROD` subindo.

## 3. Mais dano
A arma da iluminação foi de **8 → 20 de dano** (10 corações por arma). São 2 armas por tick durante
1,5 s.

## 4. O poder é SEGREDO — só ADM
- `/iluminacao portoes` agora **exige OP (nível 2)**; quem não é OP **nem vê** o `portoes` no tab.
- A **tecla G** também só funciona pra OP (o servidor confere e ignora em silêncio).
- Quem não tem a aura ligada **não recebe aviso nenhum** — nada de descobrir o poder por acidente.

> O `/iluminacao` em si (ligar/desligar a aura) continua livre pra qualquer jogador. Se você quiser
> que **só adm** possa ligar a aura também, é só falar que eu fecho.

## Provas
- `check.py`: **1383 PASS / 0 FAIL** (7 provas novas);
- `arte/auditar.py`: **0 erro**;
- **servidor de teste subiu com a 5.63.0**: `/iluminacao` nas 3 formas, `portoes` só pra OP, `Done`,
  zero exceção.

> Canal de rede continua **21** (nada de novo no protocolo nesta versão).
