# RP Tag 5.59.0 — saída sem mensagem no chat

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.59.0/rptag-5.59.0.jar
(também na sua área de trabalho como `rptag-5.59.0.jar`)

---

## O que saiu

Toda vez que alguém saía do servidor, o mod escrevia **duas** mensagens que eu tinha inventado
na 5.7.9 — sem você pedir:

1. no **chat**: `Fulano saiu do jogo — e as sombras o levaram.`
2. em cima da **hotbar**: `Fulano se desfez em sombra.`

As duas foram **removidas**. Não há mais nenhum texto no logout.

## O que fica (a animação, que é o que você queria)

- coluna de sombra subindo no lugar exato do corpo;
- os códigos/glifos girando em volta do corpo;
- as partículas (soul + soul fire flame);
- o som da expulsão e o som de caverna.

Nada além disso mudou: balão, fonte, tablet, lorezones, vazio — tudo igual.

## Provas

- `check.py`: **1347 PASS / 0 FAIL**;
- prova nova: dentro de `saidaAutomatica` não existe `broadcastSystemMessage` nem
  `displayClientMessage` (nenhuma mensagem, nem no chat nem na tela) e a animação
  (`sombraChuva`) continua lá.
