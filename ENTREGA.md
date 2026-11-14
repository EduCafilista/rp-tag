# RP Tag 5.44.2 — "a roda abre pra quem não tem o dom" (corrigido)

## O que o dono apontou
> "A roda abre / não devia abrir pra quem não tem o vazio."

## Onde estava o buraco (era no SERVIDOR, não no desenho da roda)
`VazioPoderes.temPoder(ServerPlayer)` fazia a **primeira pergunta errada**: perguntava o
**LUGAR** antes de perguntar o **JOGADOR**:

```java
// ANTES (errado)
if (VazioReino.ehVazio(p.level()) || JuizoReino.eh(p.level())) {
    return true;                 // <- dom DE GRAÇA pra qualquer um que entrasse no vazio
}
return p.hasPermissions(2) && NeptuneEventos.ehDeus(p.getUUID());
```

Consequência: **entrar no vazio ou no reino do Juízo dava o dom a qualquer jogador**, mesmo
sem nunca ter ligado o vazio. (E o cliente, corretamente, obedecia: a roda só aparecia porque
o servidor dizia `pode = true`.)

## O que mudou na 5.44.2
```java
// AGORA: o dom é do JOGADOR, e de mais ninguém
if (!NeptuneEventos.ehDeus(p.getUUID())) {
    return false;                // sem /vazio ligar: nada de roda, nada de poder
}
if (VazioReino.ehVazio(p.level()) || JuizoReino.eh(p.level())) {
    return true;                 // com o dom ligado, no vazio e no reino ele responde
}
return p.hasPermissions(2);      // fora deles, só ADM (pra ensaiar a cena no overworld)
```

- O dom agora vem **só** de `/vazio ligar` (salvo no save do mundo).
- Estar no vazio/reino **não dá mais nada** a ninguém.
- `/vazio desligar` tira a roda **em até 1 segundo** (o servidor reconfere todo mundo a cada
  20 ticks e manda o pacote `VazioAcessoPayload` quando o estado muda; o cliente não decide nada).
- O cliente continua sem autonomia: `PoderLeque.temAcesso` começa `false` e só vira `true`
  quando o servidor manda.

## Bônus que veio junto: dar o dom a outra pessoa
```
/vazio ligar              -> você é o vazio (como antes)
/vazio ligar <jogador>    -> o dom é ligado NAQUELE jogador (ADM só, como o resto do /vazio)
/vazio desligar <jogador> -> tira o dom dele
```
Serve pra cena em que o "vazio" é um player comum: ele recebe o dom, o aviso na tela
("Aperte R") e o /vazio desligar tira na hora.

## Provas
`check.py`: **1373 PASS / 0 FAIL** (5 provas novas do pedido 98/5.44.2 + a de 5.43.0
realinhada). O jar **não emagreceu**: 37.157.483 bytes (5.44.1: 37.157.235).
Servidor de teste: `RP Tag 5.44.2 (rptag)`, `Done (1.194s)!`, **0 exceções**; comandos
conferidos por RCON (`/vazio ligar <jogador>` responde "No player was found" — o argumento existe).

## Arquivos tocados
- `src/main/java/dev/rptag/VazioPoderes.java` — `temPoder()` (a correção).
- `src/main/java/dev/rptag/NeptuneCommands.java` — `/vazio ligar|desligar [jogador]`.
- `check.py` — 5 provas novas; `RPTagMod.java` / `neoforge.mods.toml` / `build.gradle` — versão.
