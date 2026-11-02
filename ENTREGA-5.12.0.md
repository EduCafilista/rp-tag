# RP Tag 5.12.0 — pedido 49 (o pacote de 9 mudanças)

> Pedido do dono: *aumentar o som do andar do deus · vazio mais escuro com prédios perdidos ·
> `/apagar` matar 100% · o vazio que mata dar um tempo e depois matar · **NAO QUERO TELA PRETA NO
> TERREMOTO** · melhorar a animação de engolir (protótipo `uKqjYudD.java`) · portal/8 fragmentos para
> voltar do vazio sem admin · tirar o aviso de SANGUE da tela · a animação de saída tem que aparecer*.

**Jar:** `rptag-5.12.0.jar` · 69.324.468 B · sha256 e md5 na release
**Prévia:** `previa/engolir_5.12.0.png` (3 quadros da animação nova)

---

## 1. O som do andar do deus ficou MUITO mais alto

`NeptuneEventos.tickDeus()`:

| antes | agora |
|---|---|
| trilha tocada em volume `1.0` | **volume `2.6`** (alcance e volume ~2,5×) |
| só a trilha | **cada passo bate um `WARDEN_STEP` grave** (vol 1.35, pitch 0.55–0.67) |

Quando ele anda, dá para ouvir o peso antes de ver o corpo. A trilha continua parando quando ele
para (2,5 s) e o `/vazio desligar` continua mandando parar o som em todos os clientes.

## 2. O vazio ficou PRETO — e ganhou prédios perdidos

- `dimension_type/exilio.json`: luz ambiente **0,42 → 0,0**, céu **desligado** (`has_skylight:false`),
  e os **efeitos passaram de `overworld` (céu azul!) para `the_end`** — era isso que deixava o vazio
  com aquele roxo lavado.
- **`client/VazioNeblina.java` (novo)**: neblina do vazio em **#050309–#0A0512**, fechando em 96
  blocos (e em 26 se você estiver caindo no vazio). O horizonte vira breu com profundidade, não "tela
  preta".
- **PRÉDIOS PERDIDOS**: ruínas de arranha-céu nascem no breu, de 22 a 63 blocos de altura, com
  paredes furadas, andares quebrados e entulho na base, em paleta preta (deepslate, blackstone, tufo,
  concreto cinza, tijolo rachado). Nascem **60–190 blocos de quem anda**, no máximo uma a cada ~3 s,
  construídas em fatias de 240 blocos por tick (não travam o servidor) e **nunca em cima de outra**.
  A praça de chegada fica limpa (raio de 66 blocos).

## 3. `/apagar` agora MATA de verdade (100%)

O `hurt(magic, MAX_VALUE)` + `kill()` deixava o corpo caído e o mod de revive do modpack levantava.
Agora existe `NeptuneEventos.matarDefinitivo()`:

1. tira tudo que segura a vida (efeitos, invulnerabilidade, voo, montaria);
2. **`setHealth(0)` + `die()`** — de propósito **não** passa pelo `hurt()`, que é onde os mods de
   revive escutam e transformam a morte em "deitado";
3. se ainda estiver vivo: `hurt(genericKill)` (o do `/kill`, ignora totem) + `kill()`;
4. **SELO de 60 s** (`tickApagados`): se alguma coisa levantar o corpo nesse tempo, ele cai de novo no
   tick seguinte. Quem respawna de verdade (tela de morte) sai do selo — então não tem risco de
   matar o jogador depois que ele já morreu direito.

## 4. O vazio que mata dá tempo — e depois mata

Cair no vazio (`rptag:exilio`) **não mata mais no primeiro tick**:

- o dano de `fell_out_of_world` é **cancelado** enquanto a contagem corre;
- a gravidade é desligada e ele fica **12 segundos** vendo o vazio chamar (`O VAZIO TE CHAMA 12… 11…
  …`), com som e partículas subindo a cada segundo;
- aos 0 s: morte. Sem admin, sem resgate.

## 5. NADA de tela preta no terremoto

Achei a origem: o `exilar()` mandava **`Nebulosa(5, true)` para TODO MUNDO** — e a nebulosa desenhava
um disco **preto a 92%** por cima da tela. Além disso o `/apagar` mandava `Nebulosa(4, true)`.

- o `Nebulosa` do exílio **saiu** (ninguém mais leva disco preto quando alguém é engolido);
- a nebulosa agora tem miolo **0,62** (era 0,92) e lavagem **0,16** (era 0,26);
- a cortina do exílio caiu de **0,70 → 0,50**.

## 6. A animação de ENGOLIR, feita do protótipo `uKqjYudD.java`

`client/EngolirFx.java` (novo) — estilo **23**. É o porte linha a linha do `SwallowedByDarkness`:

1. o chão e o bonequinho de pé (Steve 16×32 de pixels);
2. a **poça negra** abrindo com **3 anéis vermelhos** e poeira sendo sugada;
3. `algo está embaixo de você…` → `algo está te puxando…`;
4. **tremor de câmera** (5–8 px, logo depois da poça);
5. **olhos** abrindo na poça e em volta (esclera `#E9E2E2`, pupila `#B00000`, alguns com **três
   pupilas**, brilho aditivo `#FF1010`, piscando);
6. o corpo **estica em Y e encolhe em X** e afunda 190 px, com clip na poça;
7. a **escuridão sobe** num **polígono orgânico de 65 pontos** com **borda vermelha pulsando** e
   faíscas — **alfa máximo 0,80** (nunca tela preta, regra do dono);
8. **9 braços com 7 dedos cada** agarram o corpo — desenhados **por cima** da escuridão, em
   `#141018` com a borda `#9A0C18` (senão o preto sumia no breu);
9. o texto corrompido: **NOME** / **`FOI ENGOLIDO`** / **`PELA ESCURIDÃO`** em `#D01414`, com glitch
   de `▓▒░█#@%&?`;
10. vinheta vermelha pulsando na batida + grão + desfoque curto no clímax + fade.

Quem é engolido vê **11 s**; agora ele só é teleportado **5,2 s** depois de começar (dá tempo de ver
a poça, os olhos e o corpo sendo puxado). Quem está a até **64 blocos** vê a mesma animação com o nome
dele.

## 7. Portal animado + 8 fragmentos = voltar do vazio SEM admin

`VazioReino.java` (novo):

- **8 pedestais** espalhados no vazio (de 210 a 470 blocos da praça), cada um com **coluna de luz**
  (END_ROD) visível de longe, obsidiana chorando e um **fragmento do vazio** em cima (shrieker de
  sculk). **Clique com o botão direito** para pegar — **1 por pedestal, por jogador** (não farma).
- **O PORTAL** fica a **780 blocos a leste**: dois pilares de obsidiana chorando de 16 blocos, verga
  de blackstone, miolo de sculk com catalisador. Ele **dorme** (brasa fraca + sussurro) e **acende**
  quando quem chega tem os 8: vórtice de almas girando dentro do arco, som de portal.
- **Tocar no miolo com 8/8** = volta para o lugar de onde foi expulso (ou um ponto sorteado do mundo
  se o servidor reiniciou). Com menos de 8, ele diz quantos faltam.
- Os fragmentos **ficam salvos no mundo** (`RPWorldData.VazioFragmentos`) — morrer no vazio não faz
  perder o que já achou. A altura de cada construção também é salva (`VazioYs`), então nada é
  reconstruído em cima de si mesmo depois de um restart.
- A cada 25 s o jogador recebe no actionbar quantos fragmentos tem e para que lado fica o portal.

## 8. O aviso de SANGUE na tela — era um número trocado

Em `saidaAutomatica()` o pacote era `ScreenTextPayload(nome, 18, 9)`: **9 é o estilo SANGUE**
(Sangria, o FX de hemorragia). Ou seja: quando alguém saía do servidor, todo mundo levava a **tela de
sangue** no lugar da animação de saída. Agora é **`(nome, 9, 18)`** — estilo 18 = animação de saída.

E no caminho do terremoto **nada mais manda estilo 9**: a animação de engolir é 23 e o aviso na tela
de quem está perto saiu (ficou só a mensagem do chat).

## 9. A animação de saída (códigos/números) agora roda

Era o mesmo bug do item 8: o cliente recebia o estilo errado, então `SaidaPlayerFx` **nunca era
chamado** — o `startScreenText` caía no `if (style == 9)` e desenhava sangue. Com o estilo 18 certo,
quem fica vê: o sprite do jogador se desfazendo em chuva de código, os **glifos verdes** em volta do
corpo no mundo, o `[OK]`/`DESCONECTADO`, o som da expulsão e o anúncio no chat. O gatilho continua
sendo o **logout** (`ServerEvents.onLoggedOut` → `saidaAutomatica`), sem comando.

---

## Comandos novos (para testar)

```
/vazio fragmentos [jogador]   dá os 8 fragmentos (testa o portal sem caçar pedestal)
/vazio portal                 te leva até o portal (780, 40)
/vazio pedestais              lista os 8 pedestais e quantos fragmentos você tem
```

## Como testar em 2 minutos

1. `/vazio entrar` — você cai na praça; repare no **breu** e nas **colunas de luz** ao longe.
2. Ande ~100 blocos em qualquer direção: **prédios perdidos** nascem no escuro.
3. `/vazio fragmentos` + `/vazio portal` → toque no **miolo de sculk** → você volta pro mundo.
4. `/terremoto sair <jogador>` → a **animação de engolir** roda na tela de todos (sem tela preta).
5. `/vazio apagar <jogador>` → o corpo **cai e não levanta** (nem com revive).
6. Saia do servidor com outra conta logada e veja a **animação de saída** (códigos verdes).

## Arquivos

- novos: `client/EngolirFx.java`, `client/VazioNeblina.java`, `VazioReino.java`,
  `verificacao/simula_5_12_0_engolir.py`, `previa/engolir_5.12.0.png`
- mexidos: `NeptuneEventos.java`, `client/ClientEffects.java`, `client/ClientGuiLayers.java`,
  `client/NeptuneFx.java`, `RPWorldData.java`, `ServerEvents.java`, `NeptuneCommands.java`,
  `data/rptag/dimension_type/exilio.json`, `check.py`, versão em 3 lugares.

`check.py`: **832 PASS / 0 FAIL** (31 checagens novas do 5.12.0).
