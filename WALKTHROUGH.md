# WALKTHROUGH — Memória de trabalho do RP Tag

> Registro técnico histórico. As entradas preservam o contexto de cada versão e podem conter procedimentos substituídos. Consulte os guias atuais para instruções operacionais.

>**Este documento é o diário de bordo do projeto.** Ele existe para que qualquer
>pessoa (ou assistente) que continuar este trabalho saiba **o que já foi feito,
>como as coisas funcionam, o que NÃO fazer e como construir/testar/entregar** —
>sem se perder e sem refazer o que já passou por testes.
>
> **Regra: atualizar este documento NO FIM DE CADA CICLO** (junto com o
>CHANGELOG). O guia de *jogador* fica no `README.md` e no `COMO-USAR.md`;
>o registro detalhado por versão fica no `CHANGELOG.md`.

---

## 1. Estado atual

| Item | Valor |
|---|---|
| Versão | **4.7.1** (2026-10-22) |
| Commit | (ver `git log`) |
| Jar | `rptag-4.7.1.jar` (**104.562.228 B** com 6 vídeos; **só na GitHub Release** — Release `v4.7.1` id 420651626, asset 665690225) |
| Checks | `check.py` → **413 PASS / 0 FAIL** + **JUnit 43/43** (`./gradlew test`) + `python -m unittest discover -s arte/tests` (23 ok; 3 dependem da fonte Consolas do Windows) |
| Smoke | Servidor NeoForge 21.1.249 real: Done ~0,5s, RCON OK, 0 erros do mod |
| Alvo | **Minecraft 1.21.1 + NeoForge 21.1.249**, Java 21 |
| Repo local | `/home/user/rp-tag` — `main` = `e20a9a7`, tag `v4.7.0` |
| Releases vivas | **v4.7.1** (jar) + `portais-v1.3.2` (só essa é mantida em paralelo) |
| Bundle | **NÃO gerar**: com os 6 vídeos ele passa de 155 MB (o backup é o próprio GitHub + Release) |
| Servidor local | `run/` foi apagado nesta limpeza: reinstalar com o installer 21.1.249 quando precisar de smoke test |

### 4.7.1 (2026-10-22): egípcio v2 (referência do dono) + REAL novo + void/gelo antigos
- **Pedido literal do dono:** "o avisar void eu ainda prefiro o estilo antigo, prefiro o
  antigo aviso de gelo; melhore o avisar real, tá muito simples; e o avisar egipicio
  nao apareceu no mods, eu quero um com animação igual os outros avisar, quero o
  olho de orus e todo o resto, algo nesse estilo da imagem".
- **VOID e GELO:** `VIDEO_PADRAO = false` nas duas classes — o vídeo não abre mais.
  O desenho procedural é **idêntico ao antigo** (comparei byte a byte com os .java do
  catbox: hvl6ev=void, f53nwn=gelo; só mudaram os campos `sub`/`video`).
- **REAL (estilo 3)** ganhou `RealAvisoFx`: friso duplo de hieróglifos, disco solar
  alado (behedeti com asas em leque + 2 uraeus), colunas de papiro, areia e placa
  central. Antes era só véu + 2 filetes no desenho genérico do ClientEffects.
- **EGÍPCIO v2** (`arte/cenas/egipcio.py` reescrito, paleta da imagem do dono):
  azul-noite, friso topo/rodapé, olho grande com íris em anéis (turquesa/azul/amarelo),
  leque de raios coloridos + brancos, círculos concêntricos, pirâmides, glifos caindo.
  O olho subiu para `EYE_Y = 2.52` e encolheu (`EYE_HW = 1.55`) para a placa caber.
- **PLACA** (`EgipcioAvisoFx.cartucho`): retângulo de cantos arredondados, moldura
  dupla de ouro, cantos em losango, **selo quadrado à direita** e nó embaixo.
- **Por que "não apareceu":** o vídeo não passou do carregamento (1 s) naquela máquina
  e caiu no procedural escuro. Ver `VideoEstado.MAX_SEM_QUADRO` e `MIN_PRONTOS`.
- `check.py` **413 PASS / 0 FAIL**.

### 4.7.0 (2026-10-22): EGÍPCIO em vídeo — o último procedural virou Blender
- **Cena:** `arte/cenas/egipcio.py` (1280×720, 24 fps, 144 quadros, RGBA), câmera em
  perspectiva (`k = 9/240`, `cy = 0.45`), tudo em **emissão chapada** (estilo do FX do Java).
  Templo à noite, colunas de capitel de papiro, arquitrave, Rá nascendo **no horizonte**
  (`h*0.71`) atrás das 3 pirâmides, dunas com crista dourada, 2 colunas de hieróglifos
  rolando nas laterais (param em `ABRE = 2.45`), olho desenhado traço por traço com a
  **mesma geometria do Java** (`R = 22k`, centro `cy - 46k`), areia + poeira, clarão no ABRE.
- **Textura:** `CYCLES` CPU, 24 spp + denoise, `transparent_max_bounces=16`, ~20 s/quadro
  (EEVEE não roda no ambiente: `EGL_BAD_MATCH`; `xvfb` morre por OOM).
- **O cartucho NÃO entra no vídeo** — ele acompanha a largura da mensagem, então quem
  desenha continua sendo o `EgipcioAvisoFx`; a área do texto é atenuada nos grãos.
- **Metadados:** `metas.py` ganhou o branch `egipcio` (sem ele o render nem grava
  `marcadores.json`): `ra_nasce .25`, `texto_entra 1.55`, `olho_abre 2.45`, `texto_sai 4.8`.
- **Java:** `EgipcioAvisoFx` ganhou `video` + `parar()`, `VideoFx.abrir("egipcio")`,
  `preparar`/`CARREGANDO` (relógio parado)/`FALHOU` (cai pro procedural no MESMO t),
  `sons(t)` movido para DEPOIS da carga, cartucho + texto nos dois modos.
  `ClientEffects` e `ClientEvents` encerram também o Egípcio.
- **Ordem de montagem da cena importa:** definir o engine (`CYCLES`) ANTES de
  `camera_perspectiva`; `c.brilho` usa material próprio por nome (`bpy.data.materials[nome]`)
  e é animado por `c.assar_no(mat, 'A', ...)`.
- **PEGADINHAS do olho (custaram 1 render inteiro, 45 min):**
  1. o Java usa **y para baixo** — `cy + X` vira `oy - X`. A espiral estava com
     sinal trocado e desenhava um "P" por cima da íris;
  2. `b.circ` do Java é **disco cheio**, não anel: a íris é `plano` + `mat_disco`;
  3. segmentos soltos (`b.seg` em laço) viram **"dentes"** na curva do Blender —
     usar UMA curva + `bevel_factor_end` animado (`c.ss`), que desenha de verdade;
  4. **ordem em z**: traços do olho em `Z_OLHO + .04`, íris em `Z_OLHO`,
     pupila `.02`, reflexo `.03` (antes a íris tapava os traços e o reflexo);
  5. pupila redonda = `mat_disco(corte=.985)`, nunca plano puro (saía quadrada).
- **Custo:** ~20 s/quadro (144 quadros ≈ **40 min**); `bpy` + toolchain juntos = OOM.

### 4.6.0 (2026-10-20): VOID e DIVINO em vídeo (mesma coreografia, em 3D)
- **Spec e plano:** `docs/superpowers/specs/2026-10-20-void-divino-video-design.md` e
  `docs/superpowers/plans/2026-10-20-void-divino-video.md`. Gates cumpridos: 4 quadros
  aprovados, movimento aprovado, depois exportação/integração e revisão independente.
- **Cenas:** `arte/cenas/void.py` usa câmera em perspectiva (`comum.camera_perspectiva`; a
  conta está em `arte/cenas/perspectiva.py`, Python puro testado e conferido contra o
  `world_to_camera_view` do Blender). O plano z = 0 continua sendo o quadro 16×9 e o ponto
  de fuga fica no título (cy 0,45 h). `arte/cenas/divino.py` é ortográfica. Metadados de todos
  os avisos em `arte/cenas/metas.py`; pesos de atenuação suaves em `arte/cenas/suave.py`.
- **Achados (não repetir):** (1) no desenho original o anel do horizonte do Void cai sobre a
  linha da mensagem (cy + 25k ≈ 1,05 Rh) → arco inferior atenuado durante a leitura; (2) o
  Divino cobre ~93 % da tela, e só o alfa ocupa 14,3 MB → a meta de 15 MB era inviável sem
  mudar a arte; o dono aprovou ~26 MB com cor JPEG 75 (`jpeg.cor` nos metadados, lido por
  `exportar.qualidades`; alfa sempre 90); (3) atenuar por região ou tempo com `if` seco gera
  objetos que somem por degrau → usar sempre as rampas de `suave.py` (apontado pela revisão
  independente); (4) depois de mudar a câmera no Blender, `view_layer.update()` antes de projetar.
- **No mod:** `VoidAvisoFx`/`DivinoAvisoFx` no padrão do Gelo (política + `abrir`,
  CARREGANDO/FALHOU, `parar()`, contorno escuro, apoio de leitura radial). Aviso novo e saída
  do mundo encerram os cinco avisos em vídeo.
- **Teste no jogo:** os dois em vídeo, `/rpfx off` → procedural, troca Void→Divino→clássico,
  JPEG corrompido → procedural no mesmo t, kick no meio. Capturas em `verificacao/4_6_0_*`.
  Modo reduzido coberto só por teste (não há opção no jogo para ativá-lo).
- **Integração com o GitHub (4.5.0/4.5.1 do EduCafilista):** os testes novos da 4.5.1
  (`ArgumentoSerializavelTest`, `LoginCommandsPacketTest`) não compilavam nem no `origin/main`:
  usam classes do Minecraft, ausentes no classpath de teste. Correção no `build.gradle`:
  `neoForge.addModdingDependenciesTo(sourceSets.test)` e remoção do `gson:2.11.0` de teste
  (o jogo exige gson 2.10.1 estrito; ele já vem pelas dependências do jogo).
- **Pendências menores:** o som de abertura pode tocar até 0,5 s antes do primeiro quadro
  em disco lento.

### 4.6.0 (parte 1, 2026-10-20): INFERNO e GELO em vídeo (redesign híbrido; nunca publicado sozinho — o número 4.5.0 ficou com a versão do EduCafilista)
- **Spec e plano:** `docs/superpowers/specs/2026-10-20-inferno-gelo-hibrido-design.md` e
  `docs/superpowers/plans/2026-10-20-inferno-gelo-hibrido.md`. Gates cumpridos: 4 quadros
  aprovados, movimento aprovado, depois exportação/integração.
- **Cenas:** `arte/cenas/inferno.py` (fissura serrilhada atrás do título, fogo orgânico que sobe,
  fumaça iluminada no teto, meteoros na faixa de cima, redemoinho de brasas) e `arte/cenas/gelo.py`
  (vidro estilhaçado, geada só nas bordas, samambaias de gelo, cristais em aglomerados, neve).
- **No mod:** `InfernoAvisoFx`/`GeloAvisoFx` no mesmo padrão do MORTE; `FxVideoPolitica`
  (vídeo só com `fxVideo` ligado **e** fora do modo reduzido); um aviso novo de qualquer estilo
  chama `parar()` de MORTE/INFERNO/GELO (o player é um só).
- **ACHADO no jogo (não repetir):** sem o fundo procedural escuro, texto claro some no céu claro →
  no modo vídeo há **contorno escuro** (`AvisoFxUtil.contornoEscuro`) + apoio de leitura mais forte.
- **Pendente:** smoke com o jar numa instalação NeoForge separada (precisa do launcher logado).
- A captura de tela dos testes só grava se o Minecraft estiver NA FRENTE (guarda de foreground).

### ENTREGA 4.2.0 (2026-10-19) — o que EU verifiquei e publiquei
O amigo empurrou 17 commits (merge `86990bd`) com o aviso MORTE em VÍDEO; o codigo
ja estava no GitHub, mas **sem tag e sem Release**. Verificado aqui do zero:
toolchain reconstruida (JDK 21 + installer client/server + launcher_profiles falso +
lwjgl); `javac` rc=0 → **96 classes**; `check.py` **337 PASS/0 FAIL**;
`arte/validar.py` **8/8** (288 quadros, todos 1280x720, todo `_c` tem `_a`);
**JUnit 26/26** (precisa de `src/test/resources` no classpath — sem isso os 3 testes do
`FxJpegTest` falham com "InputStream null", nao e bug do mod); jar **10.298.836 B**
(288 frames = 9,6 MB); smoke do servidor: "RP Tag 4.2.0", avisos 4 e 1 e `canon on/off`
ok, **0 ERROR/FATAL**.
Publicado: tag+Release **v4.2.0** (id 417947996, asset 10298836, `make_latest`,
download `-L` 200 + `cmp` identico, tag em `d84ec85`); **v4.1.0 apagada** (release+tag).
REGRA NOVA do amigo: **nenhum jar no git** — o `.gitignore` virou `*.jar` com excecao do
`gradle-wrapper.jar`; o jar vive so na Release (com video, cada ciclo adiciona ~10 MB).

### 🚑 4.5.1 (2026-10-21) — "Invalid player data" (LIÇÃO QUE CUSTA CARO)

**Sintoma:** ninguém entrava no mundo na 4.5.0; o jogo expulsava com **"Invalid
player data"**. O save NÃO estava corrompido.

**Causa:** `ListaAlvosArgument` (argumento custom do `/rp admin vida`) existia sem
`ArgumentTypeInfo` registrada. O servidor sobe, aceita RCON, tudo funciona — mas
quando um jogador entra, `PlayerList.placeNewPlayer → Commands.sendCommands` monta o
`ClientboundCommandsPacket` e chama `ArgumentTypeInfos.byClass(type)` para cada
argumento. Tipo custom fora do registry → `IllegalArgumentException` → queda com
"Invalid player data".

**REGRA (vale pra qualquer argumento novo):**
```java
// RpArgumentTypes.java — o registro NÃO pode morar no <clinit> do argumento
// (DeferredHolder resolve o registry na criação: ExceptionInInitializerError fora do jogo)
public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
        DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, RPTagMod.MODID);
public static final DeferredHolder<..., SingletonArgumentInfo<ListaAlvosArgument>> LISTA_ALVOS =
        ARGUMENT_TYPES.register("lista_alvos",
                () -> ArgumentTypeInfos.registerByClass(ListaAlvosArgument.class,
                        SingletonArgumentInfo.<ListaAlvosArgument>contextFree(ListaAlvosArgument::alvos)));
```
+ `RpArgumentTypes.ARGUMENT_TYPES.register(modEventBus)` no `RPTagMod`.
+ **Rede de segurança:** o comando pergunta `ListaAlvosArgument.disponivel()` e cai
  para `StringArgumentType.word()` se o registro não estiver ativo — assim um erro
  desses nunca mais derruba o login de ninguém.
+ **Teste:** `LoginCommandsPacketTest` instancia o `ClientboundCommandsPacket` real
  com e sem registro (é o objeto que o servidor cria no login). Roda sem servidor.
+ **Log de boot** para conferir: `RP Tag: /rp admin vida usando lista_alvos (...)`.

### 🏺 4.5.0 (2026-10-21) — AFK, regras de RP, vidas offline e o OLHO DE HÓRUS

**Pedido do dono:** aprender com o repo `TioNickZeus/in-rp-1.21.1` (AFK, regras, vidas),
gerenciar vidas de quem está **offline**, e criar o aviso `egipicio` de olho egípcio.

- **`AfkSistema.java`** — varredura a cada `VARREDURA_TICKS = 100` (5 s) lendo
  `player.getLastActionTime()` (o relógio do próprio jogo, não um contador caseiro).
  `TIMEOUT_PADRAO_S = 300`, persistido no mundo (`AfkTimeout`). **Entrar em AFK desliga o
  RP** (`setInRp(false)` + mensagem). Acorda com movimento (`ACORDA_MOVE2 = 0.0225`, ou
  seja 1,5 cm), giro de 2°, chat (`ServerChatEvent`, `receiveCanceled = true`), ataque e
  clique. Login/logout/`ServerStoppingEvent` limpam o estado — ninguém fica preso.
  `/afk` alterna (cooldown 3 s), `/rp admin afk tempo <s>` e `/rp admin afk status`.
- **`RegrasRp.java`** — três regras, **liberadas por padrão** (não muda servidor antigo):
  `pvp` (`AttackEntityEvent` + `LivingIncomingDamageEvent` para flecha/poção/TNT, vale se
  qualquer lado está em RP), `quebrar` (`BlockEvent.BreakEvent`) e `colocar`
  (`BlockEvent.EntityPlaceEvent` + balde via `RightClickBlock` → `TriState.FALSE` e
  `RightClickItem` → `FAIL`). Staff (permissão 2) passa. Persiste no mundo.
  Comandos: `/rp admin regra [pvp|quebrar|colocar] on|off`, `/rp admin regra`,
  `/rp admin regra tudo bloquear|liberar`.
- **`VidasAdmin.java`** — resolve o alvo por **nome** (online ou `ProfileCache` para
  offline), com `record Alvo(UUID, nome, ServerPlayer online)` e `getUUID()` para o
  código antigo continuar igual. Offline: o valor muda no `RPWorldData` (que já é por
  UUID) e fica um **recado** (`RecadosVida` no NBT) entregue no login por
  `ServerEvents.onLoggedIn` (estilo 4 + trovão).
  `/rp admin vida` sem argumento lista quem está abaixo de 5/5;
  `/rp admin vida lote A,B,C tirar 1` guarda os UUIDs e espera `/rp admin confirmar`
  (30 s, `VALIDADE_MS`) — `/rp admin cancelar` desiste.
- **`ListaAlvosArgument`** — o `StringArgumentType.word()` do brigadier **não aceita
  vírgula** (só `[A-Za-z0-9_.+-]`), e o lote morria com "Expected whitespace to end one
  argument". Este argumento lê manualmente até o espaço e aceita lista/seletores.
- **`EgipcioAvisoFx.java`** (estilo **12**, `egipicio`/`egipcio`) — O Olho de Hórus,
  `DUR = 6.5 s`, `ABRE = 2.45 s`, `TEXTO = 1.55 s`:
  - `noite` (papiro + fibras + manchas) → `ra` (disco no horizonte, `h*0.71`, atrás das
    pirâmides) → `piramides` (3, silhueta + aresta dourada) → `areiaChao` → `hieroglifos`
    (2 colunas rolando + ankh/olho/água/sol/junco) → **poço escuro** + `olho` → `cartucho`
    → `graos`; camada aditiva (`brilhoOlho`, flash na abertura); `texto`; `vinheta`.
  - Olho: **acima do título** em `cy - 46*k`, `R = 22*k`, desenhado traço por traço
    (sobrancelha → amêndoa → íris `ri*0.58` 0x140E02 → lágrima → espiral).
  - Título **AVISO** letra a letra dentro do cartucho (`hh = 5.2*S + 10*k`), mensagem
    **sempre** em `cy + 25*k`.
  - Áudio: harpa/flauta pentatônica (`ESCALA`), `ENDER_EYE_LAUNCH` + `AMETHYST_BLOCK_CHIME`
    + `BEACON_ACTIVATE` no ABRE, sino e baixo depois. No mundo: `FALLING_DUST` de areia,
    `BLOCK` de areia, `CAMPFIRE_COSY_SMOKE` e `END_ROD` (servidor, `particulasEgipcio`).
- **Verificação**: `verificacao/simula_4_5_0_egipcio.py` (PIL, espelha a matemática do
  Java) → `4_5_0_egipcio_como_ficou.png` (3 momentos), `_frame.png`, `_olho.png`,
  `_egipcio.gif` (65 quadros). `check.py` **362**, JUnit **33/33**, `javac` 104 classes.
- **Aprendizado do in-rp:** sweep 100 ticks + `lastActionTime`; `resetLastActionTime` no
  wake; graça de 1 s; regras por evento com bypass de staff; confirmação por UUID em lote.
  O que NÃO copiamos: o JSON separado de vidas (nosso `SavedData` já é por UUID) e o
  `EntityArgument` (que só enxerga online).

### 4.2.0 (2026-10-19): avisos em VÍDEO renderizados no Blender (piloto: MORTE)
- **Spec e plano:** `docs/superpowers/specs/2026-10-19-avisos-em-video-design.md` e
  `docs/superpowers/plans/2026-10-19-avisos-em-video-piloto-morte.md`.
- **Pipeline:** `arte/cenas/<id>.py` monta a cena do zero → `blender -b --factory-startup -P`
  renderiza 144 PNG RGBA 1280x720 → `python arte/previa.py <id> --gif` (o DONO APROVA) →
  `python arte/exportar.py <id>` (pares JPEG cor+alfa + `fx.json`) → `arte/validar.py`
  (chamado pelo `check.py`). Detalhes em `arte/README.md`.
- **No mod:** `VideoFx` (player, sem camada) + `VideoFrameLoader` (thread `rptag-fx-loader`)
  + `VideoEstado`/`FxTimeline`/`FxCor`/`FxJpeg` (Java puro, com JUnit). A classe do aviso
  (ex.: `MorteAvisoFx`) desenha cinza → vídeo → texto/sons; falhou → procedural NO MESMO t.
  Tempos em `MorteTempos` (fonte única: FX, cena do Blender e teste dos marcadores).
- **Cliente:** `config/rptag/cliente.properties` (`fxVideo=true|false`) + `/rpfx status|on|off`.
- **ACHADOS no teste no jogo (não repetir):** (1) `BufferedImage.getRGB` custa ~35 ms/quadro
  (> 41,7 ms do orçamento a 24 fps) → decodificar pelo RASTER (`FxCor.abgrDeRaster`, ~10 ms)
  e mostrar o quadro PRONTO mais novo ≤ o atual (exigir o quadro exato travava PC lento);
  (2) JPEG cortado o `ImageIO` lê "pela metade" só com AVISO → `FxJpeg` transforma aviso em erro.
- **Teste no jogo automatizado:** `./gradlew runServer` (pasta `run-server/`, RCON em localhost,
  senha no `server.properties` de lá) + `./gradlew runClient -PrptagTeste` (entra sozinho) +
  `python tools/rcon.py "rp admin avisar Dev morte TESTE"`. Capturas em `verificacao/4_2_0_*`.
- **REGRA NOVA:** jar **nenhum** no git (`.gitignore` = `*.jar` menos o gradle-wrapper);
  o `rptag-4.1.0.jar` saiu do repositório. Jar só na Release.
- **Próximos ciclos:** 1–2 avisos por vez (4.3.0, 4.4.0…), mesma rotina com prévia aprovada.

### ACHADO IMPORTANTE (2026-10-19): "versoes antigas no GitHub" NAO eram as Releases
User reclamou que a pagina do GitHub ainda mostrava da 3.85 ate a 3.99. NAO era cache nem
a API: os **19 jars estavam COMMITADOS na raiz do repo** (entraram no commit `8de56f6`,
o do 3.99.0). O GitHub lista os arquivos da raiz na home e o visitante ve tudo. Diagnostico
correto: `curl /contents/?ref=main` -> contar arquivos `.jar` (a API de releases ja dizia 2).
Correcao: `git rm` nos 19 jars + `git add -f rptag-4.0.0.jar` (o `.gitignore` passou a ter
`rptag-*.jar` com excecao `!rptag-4.0.0.jar`, entao so o jar ATUAL fica versionado) +
commit `6f058dd`. Conferido por fora: home do repo com 0 ocorrencias de `rptag-3.*.jar`.
REGRA NOVA para os proximos ciclos: ao subir o jar novo, `git rm` o jar da versao anterior
e fazer o `git add -f` do atual — a home do repositorio nunca deve acumular jars.
CUIDADO: `git status` pode ESCONDER isso (o `.gitignore` nao lista `*.jar`, mas o caminho
`git rm` + re-add pode passar batido); sempre confira com `git ls-tree HEAD --name-only | grep jar`.

### Rotina nova (2026-10-19): só a release ATUAL fica no GitHub
User pediu: "no github remova as versões anteriores, pode deixar somente a mais atual,
se não vai ficar muita informação nele". FEITO: apagadas 20 releases (v3.84.0→v3.99.0) e
as 20 tags correspondentes; sobraram **v4.0.0** (`62d63af`, jar 2188446 B) e
**portais-v1.3.2** (`6450fb1`, mantida porque e a unica release do outro mod e o aviso de
pausa NUNCA sai). A partir de agora, a cada ciclo: publicar a release nova, marcar
`make_latest`, baixar/conferir o asset e **apagar a release + tag anteriores**:
`GET /releases` -> `DELETE /releases/<id>` -> `GET /git/refs/tags` ->
`DELETE /git/refs/tags/<tag>`. O historico de codigo NAO se perde: os commits continuam
todos na `main`.
RECUPERACAO OFFLINE: a regressao do `.git` voltou DUAS vezes (abertura e meio de turno).
Alem do ritual `fetch --tags` + `reset --mixed origin/main`, foi criado o backup
`/home/user/rp-tag-git.bundle` (`git bundle create ... --all`): se o remoto falhar, da pra
restaurar com `git clone /home/user/rp-tag-git.bundle`.

### TOOLCHAIN (recriada 2026-10-19, /var/tmp e volátil — some entre turnos)
Reconstrução completa em ~1 min: (1) JDK 21 do Adoptium em `/var/tmp/java-21`;
(2) `installer.jar` do neoforge 21.1.249; (3) **`launcher_profiles.json` falso**
(`{"profiles":{},"settings":{},"version":3}`) ANTES do `--installClient /var/tmp/mcdir`
— sem esse arquivo o instalador aborta com "There is no minecraft launcher profile";
(4) `--installServer /home/user/run` (se `run/libraries` sumiu, dá
"Missing or unsupported mandatory dependencies: minecraft [MISSING]" no boot — é só
reinstalar); (5) classpath = neoforge-client + client-srg + neoforge-universal +
server-srg + todas as libs das duas árvores (excluir `net/minecraft/server/1.21.1/`)
+ **lwjgl-openal e lwjgl do Maven Central** (sem eles: "package org.lwjgl.openal does
not exist" no `MusicaCustom`); (6) `javac --release 21 -proc:none`.
**RCON**: o pacote correto é tamanho = `8 + len(payload) + 2` e o campo de id é
`<ii` — montar com `10 + len(b)` dá timeout silencioso (aconteceu neste turno).

**4.1.0 (2026-10-19) — MORTE OFF RP NAO CONTA + FX de morte + pesadelo novo.** User:
"se a pessoa que estiver off rp matar uma pessoa on rp, quando estiver vida canon, ele nao
pode perder uma vida canon"; mandou 2 prototipos do catbox: 9mvdzc = aviso de MORTE
("Linha Reta") e 1jsrwv = **o novo pesadelo**.
IMPLEMENTADO: (1) `CanonLife` ganhou `aoMorrer(LivingDeathEvent)` + set `MORTE_NAO_CONTA`:
se `e.getSource().getEntity()` for jogador e `!wd.isInRp(matador)`, marca a vitima; o
`aoRenascer` faz `remove(uuid)` e RETORNA antes de descontar. Morte sem matador (mob,
queda, lava, /kill) e matador ON RP continuam contando. Limpeza no logout.
(2) `client/MorteAvisoFx` = prototipo 9mvdzc adaptado: pacote dev.rptag.client,
`iniciar(titulo, mensagem)`, mensagem em `cy + 25*k` (regra 3.96.4), registrado em
`ClientGuiLayers` (camada `aviso_morte`); estilo 4 no `ClientEffects` agora chama o FX
ANTES de agendar veu+audio (veilStart/veilUntil/somCanonDue preservados).
(3) `client/PesadeloAvisoFx` REESCRITO com o prototipo 1jsrwv (minimalista: moldura que
falha, texto digitado com corrupcao, batida acelerando, SEM figuras). O "Seguido"
found-footage saiu — backup em `/home/user/backup/PesadeloAvisoFx_Seguido_figura_3.98.java`.
check.py: 312 PASS/0 FAIL (10 checks novos). javac rc=0 (87 classes), jar 2194303 B,
smoke verde: "RP Tag 4.1.0", `rp admin avisar @a 4 ...` e `... 1 ...` reconhecidos,
`canon on/off` OK, 0 ERROR/FATAL. Verificacao visual de layout em
`verificacao/4_1_0_{morte,pesadelo}_layout.png` (titulo y=533/509, mensagem y=652).

**4.0.0 (2026-10-19) — BARREIRA admin-only + faixa vertical + varinha.** User: "faça um
bloco, wand para os adm saber até onde vai a lorezone, algo que só os adm podem ver, tipo
uma barreira circulando... se o evento for debaixo das cavernas, eu quero que os players
so consigam o aviso assim que chegarem do lado da caverna e nao la no topo".
IMPLEMENTADO: (1) `Zone` ganhou `minY/maxY` (record + save/load "MinY"/"MaxY"; zonas
antigas = esfera inteira) e `faixaVertical()`; trigger exige `py >= minY && py <= maxY`;
(2) `/lorezone vertical <id> <minY> <maxY>` e `... auto`; listar mostra "Y a..b";
(3) `ZoneShowPayload` (codec MANUAL — `StreamCodec.composite` nao aceita 9 campos;
ByteBuf puro nao tem writeUtf/writeVarInt nesta API -> UTF-8 com writeInt+writeBytes);
(4) `client/ZoneVisualizer` desenha arame de esfera (12 meridianos + paralelos dentro da
faixa) + paredes amarelas em minY/maxY + linha central, no stage
`AFTER_TRANSLUCENT_BLOCKS` (AFTER_TRANSLUCENT_TERRAIN nao existe no 1.21.1), via
`RenderType.lines()` e `renderBuffers().bufferSource()`; expira sozinho; ninguem mais ve;
(5) item `rptag:lorezone_wand` (DeferredHolder<Item,Item> — DeferredItem nao existe na
API do 1.21.1 daqui), modelo `minecraft:item/blaze_rod`, lang pt/en; `LorezoneWand`
(PlayerInteractEvent.RightClickBlock/RightClickItem — `setCancellationResult` so existe
nas SUBCLASSES, nao na base) com cooldown de 250ms pra nao disparar 2x, permissao 2 e
`distance - radius <= 256`; (6) `/lorezone varinha`, `/lorezone mostrar <id> [seg]`,
`/lorezone esconder`.   Publicado: commit `62d63af` (tag `v4.0.0`), Release id 417221397, asset
  `rptag-4.0.0.jar` 2188446 B, download `-L` 200 e `cmp` identico.
  REGRESSAO DO .git NAO MORREU: ao abrir o turno o HEAD local estava em `437bb48`
  (3.84.0) — `8de56f6` nao existia como objeto e nao havia tag nenhuma. A ARVORE de
  arquivos estava intacta (check.py 302 PASS), so o historico tinha sumido. RECEITA DE
  RECUPERACAO (funcionou): `git remote set-url origin <url com PAT>`;
  `git fetch --tags origin main` (traz `origin/main` + todas as tags);
  `git reset --mixed origin/main` (NAO toca nos arquivos — o diff volta a ser so o do
  ciclo, conferido com `git status --porcelain`: 18 entradas esperadas);
  `git add <lista explicita>` (deixar o jar de fora) + commit + push (foi fast-forward).
  LICAO: antes de qualquer coisa, `git log --oneline -1`; se nao for o commit do ciclo
  anterior, NAO commitar por cima — buscar o remoto primeiro.

check.py 302 PASS/0 FAIL; smoke 4.0.0 verde (comandos parseiam,
`give rptag:lorezone_wand` reconhece o item, 0 ERROR/FATAL).

**3.99.0 (2026-10-19) — LOREZONES: aviso unico + audio com tempo maximo.** User:
"se o player ficar entrando e saindo da lorezone, vai ficar tocando toda vez a musica e
toda vez vai aparecer um aviso na cara dele, faça com que seja unico esse aviso...
se tiver o som toca toda vez, mas nao fica para sempre o som... na primeira vez isso tbm
nao pode ocorrer da musica ficar para sempre... faz um limite de tempo".
IMPLEMENTADO: (1) `LoreZonesData.avisosVistos` PERSISTENTE (Map<UUID,Set<String>> salvo
em `AvisosVistos` no .dat) — `avisoVisto/marcarAvisoVisto/resetAvisos`; o `SEEN` em
memoria e o `seen.remove(zid)` ao sair foram REMOVIDOS (era o bug do "aviso toda visita");
(2) `show()` nao toca mais som — o som pontual toca em `entrouAgora` (TODA entrada) e
para em `SOM_MAX_MS=30s` ou ao sair (SOM_ATIVO ganhou [2]=inicioMs); (3) `tickMusic`
reescrito: `MusicState(zoneId, startedMs, silenced)`, `maxSeg = musicInterval>0 ?
min(600, musicInterval) : 60`, ao vencer → `stopMusic` + silenced (nao volta ate sair);
`-2` = musica UNICA (chave persistente "m:<zona>"); (4) musicas custom/URL tambem
limitadas: `LorezoneMusicPayload` e `MusicFileQueryPayload` ganharam `maxSeg` (VAR_INT),
`MusicaCustom.definirLimite/armarVigia` (thread daemon para no prazo, teto 600s) — cobre
o caso do download terminar DEPOIS do STOP do servidor; (5) `/lorezone avisos reset
<jogador>` + `/lorezone musica <id> unica <som>`. Verificado com simulacao da maquina de
estados (`verificacao/simula_lorezone.py`, 3 cenarios) + check.py 292 PASS/0 FAIL +
smoke (comandos parseiam; 0 ERROR/FATAL). ATENCAO: `MusicSync.pedir` agora exige maxSeg.

**3.98.0 (2026-10-19)** — user pediu: "adicione os dois modelos de pesadelo, tanto o
com o fundo que voce deixou na versao 3.96.4, quanto a versao atual, eu quero as duas
opções". FEITO: estilo 11 = `pesadelo2` = **"Sono Sem Descanso"** (classe nova
`PesadeloAntigoAvisoFx`, camada `rptag:aviso_pesadelo2`), recuperado da tag v3.96.4 via
raw.githubusercontent (a REGRESSÃO .git 11ª apagou o commit local — o working tree
estava íntegro e o remoto tinha tudo). O estilo 1 `pesadelo` continua = "Seguido".
Ambos com `cy + 25k` (msg embaixo) e Escuridão 5s (`particulasPesadelo(alvo,true)`).
Mudanças de superfície: RPCommands (parser `case "pesadelo2" -> 11`, nomeEstilo
"PESADELO SONO", literal do subcomando, ajuda), LoreZoneCommands (ESTILOS +
nomeEstilo + lista), LoreZones clamp `Math.min(10` → `11` ×2, ClientEffects clamp
idem + rota 11, ClientGuiLayers registra as DUAS camadas. check.py 283 PASS/0 FAIL
(várias âncoras antigas de "ascensao removido" usavam `'case 11' not in` → reescritas
para checar o que importa: `ascensao` ausente + `case "pesadelo2" -> 11` presente).

**3.97.0 (2026-10-19)** — user mandou 5 .java da catbox: `0ncp46` = **AvisoFxUtil**
(compartilhada), `qbupf3` = **GeloAvisoFx** "Geada Eterna", `xe44nh` = **InfernoAvisoFx**
"Fornalha", `oj68ax` = **PesadeloAvisoFx "Seguido"** (o user rotulou como "atualize o
aviso inferno", mas o arquivo é um PESADELO — aplicado como novo pesadelo, estilo 1),
`nb9qgd` = **RunicoAvisoFx** "Tradução Arcana". Os 4 protótipos tinham
`iniciar(String mensagem)` ÚNICO e desenhavam a msg como texto grande; adaptados para
`iniciar(String titulo, String mensagem)` com o bloco de SUB padrão em `cy + 25 * k`
(mesma linha dos demais; a verificação `cy + 25 * k, 0` agora cobre os 8 FX).
Mapa de estilos: 1 pesadelo, 2 runico, 3 real, 4 morte, 5 inferno, 6 gelo, 7 divino,
8 void, 9 sangue, 10 demoniaco. 2/5/6 perderam o som do servidor (FX agora tem trilha
própria: runico = mesa de encantamento + sinos pentatônicos; inferno = firecharge/ghast/
lava; gelo = quebra de vidro + sinos) — `somAviso` só mantém sons em 3 (beacon) e 4
(respawn anchor). Partículas novas: particulasRunico/Inferno/Gelo (server-safe). O
`AvisoFxUtil` usa `Tesselator`/`BufferUploader` (1.21.1) e `SimpleSoundInstance.forUI(ev,pitch,vol)`.
check.py: 278 PASS/0 FAIL (custou reescrever 8 âncoras que auditavam o pesadelo antigo).

**3.96.4 (2026-10-19) — “a mensagem tem que ser embaixo do aviso padrão” (3ª cobrança)**:
user disse "ja pedi para voce arrumar isso, consegue verificar se ficou certo agora?".
DIAGNÓSTICO CERTO: a msg estava embaixo nos 5 desde o 3.96.1, mas cada FX usava
uma LINHA DIFERENTE (divino oy+9k=72px, demoniaco oy+11k=81px, sangue/pesadelo
ty+14.5S=47px/30px, void ty+17.5S=73px) → visual "desconexo" entre estilos.
FIX 3.96.4: TODOS → `cy + 25 * k` (linha única, mesma distância do centro em
qualquer resolução); void reflexo 16S→15S pra abrir folga.
VERIFICAÇÃO: (1) simulação PIL em 1080p com a MESMA matemática do Java →
`verificacao/avisos_verificacao_1080p.png` (5 painéis, linhas verde=base do
título/amarela=topo da msg); (2) bytecode `javap` → `25.0f` nas 5 classes.
**BUG GRANDE DO check.py DESCOBERTO E CORRIGIDO**: `print(PASS/FAIL)` estava na
linha 487 e TODOS os checks anexados desde a 3.95 (linhas 488+) NUNCA executavam
→ placar congelado em 259 desde então. Bloco movido pra ANTES do resumo:
agora **273 PASS / 0 FAIL** com os checks de 3.95/3.96/3.96.1/3.96.2/3.96.3/3.96.4
rodando DE VERDADE. LIÇÃO: SEMPRE conferir que o número de PASS muda ao
adicionar check; inserir seções novas ANTES do `print` do resumo.
Toolchain: /var/tmp zerado neste turno (JDK + installer + cp.txt rebaixados);
cp.txt precisa do CLIENT (`--installClient /var/tmp/mcdir`) + lwjgl 3.3.3.

**3.96.3 (2026-10-19)** — user pediu para ADICIONAR o susto extra no
pesadelo para testar: "qualquer coisa eu vou mudar o estilo do susto nao
curti tanto preferia o modelo antigo". FEITO: `RPCommands` case 1 →
`particulasPesadelo(alvo, true)` = DARKNESS 100 ticks (5s) + partículas.
Aviso importante ao user: ele PREFERE o modelo antigo do pesadelo (véu +
coração) — se pedir, reverter o estilo 1 para o pesadelo antigo
(3.63–3.94) é o caminho; NÃO mexer sem pedido explícito. Reverter o
susto = `particulasPesadelo(alvo, false)` (1 parâmetro).
Toolchain perdido no turno (volátil /var/tmp): JDK 21 + installer
rebaixados; cp.txt agora PRECISA do classpath de CLIENT — obtido via
`java -jar installer.jar --installClient /var/tmp/mcdir` (cria
client-...-srg.jar) + lwjgl/lwjgl-openal 3.3.3 do Maven Central.
ORDEM do cp.txt importa: neoforge-server/client ANTES dos jars do
minecraft (senão `Player.refreshDisplayName()` e
`MinecraftServer.getPlayerList()` não existem na classe vanilla); excluir
`*slim*` e `*unpacked*` (vanilla sem patches NeoForge).

**3.96.2 (2026-10-19)** — user mandou print do void com a mensagem
"se fudeu" EM CIMA do AVISO e pediu: mensagem embaixo do aviso, igual
divino/demoniaco, nos 5 FX ("esse aviso não deveria ser a mensagem...
faz direito igual ta no divino e no demoniaco"). EVIDÊNCIA: sangue
(confirmado certo) e pesadelo/void tinham o MESMO código de posicionamento
no 3.96.1 (ty+14.5S) — e o print do void mostrava o desenho EXATO do
3.96.0 → jar velho no mods do CLIENTE na hora do teste (falha recorrente).
Além disso, void foi alinhado: sub ty+27.5S → **ty+17.5S** (logo abaixo
do reflexo invertido, faixa visual cy+~25k = mesma de divino/demoniaco).
Layout FINAL dos 5 FX: título "AVISO" grande (S=k·1.9, cy−4S) +
mensagem do comando embaixo (banda cy+20k..27k) ou nada. User também
observou que "o pesadelo antigo dava um medo a mais" (não pediu nada
explicitamente; OPCIONAL futuro: Escuridão 5s do protótipo —
particulasPesadelo(alvo, true) — ou véu do pesadelo antigo; SÓ com
pedido).

**3.96.1 (2026-10-19)** — user mandou print do sangue: a MENSAGEM
aparecia EM CIMA do "AVISO" ("A MENSAGEM DEVE FICAR EMBAIXO DO AVISO
NE?"). Causa: os protótipos sangue/void/pesadelo desenham o sub em
`ty - 12 * k` (ACIMA do título) — divino/demoniaco desenham embaixo
(oy + 9k/11k com oy = cy + 5.5S) e por isso ficaram certos. FIX:
sangue/pesadelo `ty - 12*k` → `ty + 14.5 * S` (logo abaixo do título,
~cy + 20k = mesma altura visual de divino/demoniaco); void →
`ty + 27.5 * S` (abaixo do REFLEXO invertido que ocupa ty+16S..~ty+25S).
check.py: âncora nova "mensagem EMBAIXO nos 5 FX" (ty - 12 * k ausente
dos 5).  LIÇÃO: NEM TUDO nos protótipos é o desejado — posição do sub
nos protótipos sangue/void/pesadelo era acima; decisão do user: sub
SEMPRE abaixo do AVISO.

**3.96.0 (2026-10-19)** — user mandou +2 protótipos: catbox hvl6ev.java
("VoidAvisoFx — Queda Sem Fundo": buraco negro c/ anel de acreção,
túnel infinito, estrelas sugadas, título c/ aberraação cromática +
reflexo invertido; particulasMundo = PORTAL 120 + REVERSE_PORTAL 40) e
8za21k.java ("PesadeloAvisoFx — Sono Sem Descanso": túnel que respira,
tentáculos c/ garras, 16 olhos que seguem o título + olho gigante,
estática, caixinha desafinada CT/CP + coração HB + grito;
particulasMundo(p, escuridao) = SCULK_SOUL 30 + SQUID_INK 20 + SMOKE 40
+ DARKNESS 100 opcional). Adaptados 1:1 (package + USO doc; NADA
injetado; escala fixa k·1.9 intacta). Rotas: style 1 → PesadeloAvisoFx,
style 8 → VoidAvisoFx (iniciar("AVISO", limpoN) + return). somAviso:
case 1 (ELDER_GUARDIAN_CURSE) e case 8 (PORTAL_TRIGGER+WITHER_AMBIENT)
SAÍRAM → particulasPesadelo(alvo, **false**) / particulasVoid(alvo)
(server-safe FQN; escuridao=true EXISTE no método mas o comando usa
false — afeta gameplay). Estilos c/ FX próprio: 1, 7, 8, 9, 10; desenho
legacy restante: 0, 2, 3, 4, 5, 6. somAviso c/ som só em 2, 3, 4, 5, 6.
check.py: seção 3.96 (+6) + âncora 3.64 reescrita (ELDER_GUARDIAN_CURSE
saiu) → 259/0.

**3.95.0 (2026-10-19)** — pedidos do user: (1) "tira essa informaçao
que vem embaixo" (as legendas "AVISO DIVINO"/"CHAMADO DO ABISMO" eram da
integração 3.92/3.93; agora o subtítulo só mostra a MENSAGEM digitada no
comando — comportamento do próprio protótipo, cujo sub default é vazio);
(2) "tire o 'ascençao' quero esse seja o atual divino, nao quero adicionar
mais avisar": estilo 11 REMOVIDO (literal, mapa, nomeEstilo, case 11,
lorezone entry) e o FX celestial assumiu o **estilo 7 (divino)** — clamps
`Math.min(11`→`Math.min(10` (ClientEffects + LoreZones ×2); sonAviso case
7 saiu (ALLAY+BELL) → `particulasAscensao(alvo)`; (3) "para o aviso de
sangue coloque esse no lugar" (catbox 01a9gl.java = "SangueAvisoFx —
Sangria"): **sangue é o estilo 9** (4 = morte/canon INTACTO!); arquivo
1:1 (package dev.rptag.client + USO doc; NADA injetado), camada
`rptag:aviso_sangue`, rota `style == 9 → SangueAvisoFx.iniciar("AVISO",
limpo9)`, somAviso case 9 saiu (WARDEN_HEARTBEAT) → `particulasSangue`
(port do particulasMundo: BLOCK redstone 60 + FALLING_DUST 24, server-safe
FQN). somAviso agora só tem sons nos estilos SEM FX (4,5,6,8 + clássicos);
7/9/10 = partículas server-safe. check.py: seção 3.95 (+6) e 7 âncoras
reescritas p/ comportamento novo → 259/0.  BUG PEGO NO CHECK: replace
removiu `case 10 -> "demoniaco"` do nomeEstilo do LoreZoneCommands junto
com o 11 — restaurado; conferir switches inteiros ao remover cases.

**3.94.0 (2026-10-19)** — user testou os 2 FX e reportou: "não ficou
parecido [com o modelo], quero parecido com o modelo que enviei". CAUSA
RAIZ (diff contra os originais): eu tinha INVERTIDO a ordem dos argumentos
de `iniciar` — os protótipos são `iniciar("AVISO", mensagem)` (título
grande FIXO "AVISO" + mensagem configurável no SUBTÍTULO), mas eu chamava
uma chamada de `iniciar` com símbolos decorativos na mensagem e ainda injetara auto-ajuste `S=min(S,w·0.86/tot)`
que ENCOLHIA o título. FIX: chamadas corrigidas no ClientEffects
(`iniciar("AVISO", limpo)` / `iniciar("AVISO", limpoD)`) + linhas de
auto-ajuste REMOVIDAS dos 2 FX (diff vs catbox = só package/docs;
particulasMundo segue portada server-safe no RPCommands). check.py: 4
âncoras reescritas para ancorar FIDELIDADE (escala `k·1.9` presente,
`w * 0.86f` AUSENTE, ordem da chamada) → 259/0.  LIÇÃO: quando adaptar
protótipo do user, a assinatura/documentação do protótipo DEFINE a ordem
dos parâmetros — conferir o doc comment antes de integrar.

**3.93.0 (2026-10-19)** — user mandou o 2º arquivo (catbox k1dqve.java,
19.8KB "DemoniacoAvisoFx: Chamado do Abismo"): "faça o mesmo para o
aviso demoniaco". ADAPTAÇÃO IDÊNTICA ao 3.92: package dev.rptag.client
+ camada própria "aviso_demoniaco" no ClientGuiLayers + AUTO-AJUSTE da
mensagem (S = min(S, w·0.86/tot) + ty = cy−4·S antes de x0 — o
protótipo tinha S fixo) + ClientEffects.startScreenText intercepta
style 10 → DemoniacoAvisoFx.iniciar(texto sem §, " CHAMADO DO ABISMO
") + RETURN (o desenho 3.91 do demoníaco virou código morto — fica
arquivado no arquivo, um if a menos por frame). O FX: tela em vinho
PULSANDO no ritmo cardíaco (som+visual sincronizados, beat()), DENTES/
mandíbulas negros fechando das bordas, fissuras de lava do centro,
pentagrama invertido em brasa girando + 2 CHIFRES incandescentes,
texto subindo das trevas com glitch/shadows vermelho+laranja, OLHO de
fenda abrindo sob o título, brasas; trilha própria (ENDER_DRAGON_GROWL
0.6 + batidas + fim). somAviso case 10 → particulasAbismo(alvo)
SERVER-SAFE (do particulasMundo do protótipo: PENTAGRAMA de
SOUL_FIRE_FLAME no chão (5 cordas i→i+2, 15 pontos cada) + círculo de
FLAME (40) + LARGE_SMOKE (30, coluna) + LAVA respingos (14)). check
259/0 (+5; âncoras 3.90 "WITHER_AMBIENT >= 2" e 3.87 atualizadas — o
case 10 do somAviso não toca mais sons do servidor). 12 estilos, 2 com
FX cinematográfico (ascensao + demoniaco).

**3.92.0 (2026-10-19)** — user mandou o ARQUIVO pronto (catbox 10jv3n.
java — DESSA VEZ baixou! ~16KB, "DivinoAvisoFx: Ascensão Celestial",
protótipo gerado pra ele): clarão + vinheta, pilar de luz, halo rúnico
(anéis + tiques + HEXAGRAMA), 2 camadas de raios girando opostos, ASAS
de luz (2×9 penas batendo), letras caindo uma a uma com brilho dourado
varrendo (sweep), faíscas em estrela, ornamento, sub-mensagem, acorde
de sinos (BEACON_ACTIVATE + 5×AMETHYST_CHIME em tons 0.79-2.00 +
BEACON_DEACTIVATE) e partículas END_ROD no mundo. "adorei a animação
que ficou" = o 3.91 foi APROVADO. ADAPTAÇÃO: package dev.rptag.client
(era com.exemplo.rptag), camada própria via DivinoAvisoFx.registrar(e)
no ClientGuiLayers (registerAboveAll "aviso_divino"), Título = MENSAGEM
do comando com AUTO-AJUSTE (S = min(k·1.9, w·0.86/tot) — mensagens
longas encolhem, NUNCA vazam; o protótipo não tinha fit!), sub fixo
" AVISO DIVINO ". VIRA O ESTILO 12º "ascensao" (11) — NÃO substitui
o divino (user amou o atual): RPCommands case "ascensao"->11 +
nomeEstilo + literal + somAviso case 11 = SÓ partículas (o FX tem
trilha própria no cliente — beacon+acorde+deactivate; e SOM do servidor
não faria sentido duplicar) + particulasAscensao(alvo) = método SERVER-
SAFE (ServerLevel.sendParticles + END_ROD: anel 72 que sobe + espiral
48 — NUNCA chamar classe client de código common!). ClientEffects
.startScreenText intercepta style 11 → DivinoAvisoFx.iniciar(texto sem
§, sub) e RETURN (não seta screenText — o FX tem timeline própria 6s).
LoreZone ESTILOS ofEntries +ascensao/11 + nomeEstilo + lista; clamps
min(11)×2 em LoreZones. BUGS evitados: float elapsed (assinatura
desenhaOrbes já corrigida no 3.91); partículas via FQN (sem import
pile). JAVAC OK DE PRIMEIRA (o protótipo era sólido). check 254/0
(+7, −2 âncoras de clamp/min). 12 estilos no total!

**3.91.0 (2026-10-19)** — prints do user: "o demoniaco ainda está muito
oval, eu nao quero esses efeitos girando no inferno e nem no demoniaco...
esses pixel ficaram esquisitos e ja pedi para tirar, melhore o do divino
o simbolo ta estranho... esse é o mod que te falei (curseforge tensura-
ascensions), veja como elas funcionam e coloque algo assim... remova
esses feixes de luz do demoniaco e do inferno, deixe so no divino mas
melhore". Página do CurseForge LIDA: a assinatura do mod é o ritual de
awakening = "10-second cinematic with levitation, ORBITING ENERGY
BULLETS, layered audio" → trouxe a ASSINATURA (técnica própria): ORBES
de energia orbitando (desenhaOrbes: n=6, elipse achatada raio·0.42,
glow 6px + núcleo 3px, fase = 2π·i/n + elapsed·0.0011) nos 3 avisos
(divino dourado, inferno brasa, demoníaco sangue). MUDANÇAS: (1)
desenhaAnel = varredura ANGULAR (passos=max(64,ra+rb), pontos 2px) —
arco perfeito em qualquer proporção (fim dos "dots" do halo achatado da
print). (2) INFERNO: leque FORA; orbes de brasa + radial + halo triplo
liso. (3) DEMONIACO: leque FORA e o anel-oval do texto FORA; agora =
aura negra + veias + CÍRCULO DE INVOCAÇÃO REDONDO centrado na TELA
(Rinv = min(w,h)/4, ra=rb — nunca oval) com 3 anéis + 12 runas radiais
ESTÁTICAS + orbes de sangue + letras corrompidas. (4) DIVINO: coroa
FORA → SOL SAGRADO (desenhaSol: disco degradê 8 camadas + anel + 8
raios desenhaRaio — 4 cardeais 3.1r/0.42r + 4 diagonais 2.2r/0.26r) +
radial RESPIRANDO (resp7 = 0.90+0.10·sin·0.0021) + orbes dourados;
feixes SÓ no divino . BUGS: float→long (elapsed é float no método —
assinatura de desenhaOrbes era long); 1 patch rodou com cwd ERRADO
(/home/user) e o jar saiu SEM classes → CONFERIR ClientEffects.class no
jar antes de entregar (novo passo do ritual: unzip -l | grep Client
Effects). check 247/0 (+3/−3 âncoras).

**3.90.0 (2026-10-19)** — feedback do user c/ print: "o avisar demoniaco
ficou oval... faça a mesma pegada... no angelical eu nao gostei daquele
monte de pixels, mude essa coroa de anjos... o do inferno ta mais ou
menos... melhore o som algo mais daora (catbox rxavzi.mp3) corte esse
audio e deixe uma parte para o inferno... melhore essa parte do circulo
de fogo... adicione mais coisas no aviso de sangue para ficar bizarro...
pegue a tematica do mod de tensura que tem os bagulhos espaciais".
(1) desenhaAnel REESCRITO = varre LINHAS e desenha arcos laterais 2px
CONTÍNUOS (o tracejado por colunas é que dava o efeito "oval de
pontinhos"); rb maiores (menos achatado). (2) COROA DE ANJOS = glow
(ra170/rb22 α55) + núcleo brilhante (ra150/rb16 α215 0xFFF3C4) + acento
interno (ra104/rb11 α150). (3) inferno: mesmo desenho de anel em fogo
(0xD84E0A glow / 0xFFA050 núcleo / 0xFFD9A0 acento). (4) DEMONIACO =
AURA NEGRA: véu 205·resp + brilho radial 24 camadas 0x3A0208 + leque de
16 raios escuros girando (angBaseD = elapsed·0.0034, 12/6px, α70/45) +
veias + CÍRCULO duplo liso em volta do texto (glow raioX+12/bh+34 +
núcleo 0x8A0F0F) + letras corrompidas (mantidas) + som = WITHER_SPAWN +
WITHER_AMBIENT EM CAMADAS. (5) SANGUE: + CORAÇÃO GIGANTE translúcido
batendo ATRÁS do texto (MASCARA_CORACAO = a silhueta do coração 3D,
desenhaCoracaoFundo, esc = max(w,h)/14 · (0.90+0.14·pulsoCard), α34
0x6E0B0B) + veias + gotas+pulso (mantidos). (6) VOID = NEBULOSA: 2
brilhos radiais deslocados (roxo 0x3A1E7A α7 / azul 0x14285E α6, 18
camadas — temática espacial Tensura com a técnica do degradê
acumulado, TUDO original) + anel cósmico liso 0x7A5CCF + estrelas
(mantidas). (7) SOM DO INFERNO: o catbox rxavzi.mp3 deu 404 (página de
erro do catbox — link morto ou ainda processando; 3 tentativas) → criei
o SOM PROPRIO sintetizado com numpy+soundfile (pip na hora): riser
sombrio 9s 32kHz (boom 90→42Hz + rumble marrom LFO + sweep 70→190Hz +
impacto em 6.5s + 90 crackles de fogo, pico 0.60) =
assets/sounds/inferno_aviso.ogg (82.7KB) + sounds.json + SOM_INFERNO
createVariableRangeEvent; case 5 toca SOM_INFERNO.get() 1.6F + BLAZE
0.35F (ghast scream SAIU). QUANDO O USER REENVIAR O LINK: só trocar o
.ogg e rebuildar. BUG da hora: âncora com "1 espaço" de diferença (*
macabro vs *  macabro) — âncora de docstring é frágil, preferir
substituição de token único. check 244/0 (+8/−1 âncora).

**3.89.0 (2026-10-19)** — "mude o inferno faça na mesma pegada do
angelical, faça algo bonito de se ver, ai ja testo os dois juntos de uma
vez". INFERNO = AURA ARDENTE (espelho do 3.88): FORA muralha de dentes de
fogo e brasas/pontos; brilho radial 26 camadas 0xFFB060 (alpha 10·vis·
(1.05−s)), leque de 14 raios desenhaRaio girando NO SENTIDO CONTRARIO
(angBase5 = −elapsed·0.0055 — divino e inferno giram opostos, juntos
formam um sol de dois hemisférios), larguras 15/9, cores 0xFF8A2A/
0xD84E0A, HALO TRIPLO de brasa (ra 150/112/76, 0xD84E0A/0xFF8A2A/0xFFD9A0)
+ filetes 0x8A2400. Véu base 150 (0x140301). SOM mantido (ghast+blaze).
tinta 0xFFA347 mantida. check: âncora do inferno trocada (angBase5/
0xFFB060/haloY5 SEM yBrasa/resp5) + âncora 3.86 de cor trocada → 236/0.

**3.88.0 (2026-10-19)** — "no divino tira essas faixas na vertical,
melhora a qualidade disso, quero algo mais bonito nao varios pontos e
pontos... tem o mod do tensura que tem umas animacoes bonitas, consegue
fazer algo assim" (referência visual apenas — SEM decompilar, política).
DIVINO REFEITO como AURA SAGRADA: (1) FORA: as 7 colunas verticais e as
26 faísce/pontos. (2) BRILHO RADIAL: 26 retângulos concêntricos
ACUMULANDO alpha (9·vis·(1.05−s), cada 1) = degradê contínuo quente
0xFFF0C0 no centro morrendo nas bordas — o truque do degradê com fill()
é acumular camadas finas (blend ligado). (3) LEQUE DE LUZ: 14 raios
AFILADOS girando devagar (angBase = elapsed·0.0045°/ms) desenhados com
desenhaRaio — pushPose/translate/mulPose(ZP) e 4 segmentos com alpha
decrescente (a·(12−j·3)/12) e largura CRESCENTE (w·(1+j·0.22)) = raio
que esvae ao longo; larguras alternadas 14/8px, cores 0xFFF3C4/0xE8C765.
(4) HALO TRIPLO (3 anéis desenhaAnel, ra 150/112/76, alphas 95/140/185).
Véu base 115 (era 140). ERROS: var `a` já existia no método (→alpha) —
3ª sombra de variável neste ciclo (cy, cx, a: SEMPRE usar nomes únicos
em código dentro de métodos enormes). Referência Tensura: atende com
técnica própria (aura + leque girando), visual "mandala sagrada".

**3.87.0 (2026-10-19)** — pedido do user: "o aviso inferno deve ser algo
mais bizarro... mude o divino quero algo mais angelical,celestial, o void
está legal mas quero um som mais fundo profundo bizarro, o aviso de sangue
deve ser o pulsar mais profundo o som, o aviso demoniaco (crie) algo
bizarro de se ver, letras vermelhas e o mais macabro possivel". (1) INFERNO
(5): véu RESPIRANDO (sin·0.004 ±14) + MURALHA de fogo no rodapé (dentes
estáticos 0xD84E0A/0xFF7A1A) + 60 BRASAS que SOBEM (y = yB - elapsed·0.05·
(1+i%3) mod h — animadas!) + som = GHAST_SCREAM 0.6 + BLAZE_BURN 0.5.
(2) DIVINO (7) CELESTIAL: HALO duplo (2 anéis elípticos desenhaAnel acima
do texto, 0xE8C765/0xFFF3C4) + 7 raios claros largos + 26 FAÍSCES que
SOBEM (ascensão, elapsed·0.03) + som = ALLAY_ITEM_GIVEN 0.9 (coro de
anjo) + BELL_RESONATE 2.0. (3) VOID (8): som = PORTAL_TRIGGER pitch 0.3
(grave demais) + WITHER_AMBIENT 0.55 (gemido sepulcral). (4) SANGUE (9):
véu e gotas PULSAM em ritmo cardíaco DUPLO (pulsoCard = 0.70+0.30·min(1,
bat1³+0.6·bat2³), sin·0.0069 e sin·0.0069-0.9) + som = WARDEN_HEARTBEAT
**pitch 0.4 vol 1.3** (pulso profundo). (5) NOVO DEMONIACO (10): véu
0x0A0002 RESPIRANDO (sin·0.0031) + desenhaVeias (gretas de sangue dos 4
cantos em direção ao centro, LCG seed 666, 9 segmentos/canto, 0x6E0B0B/
0x450404) + desenhaAnel em volta do texto (elipse tracejada 0x5E0808,
raioX = min(w/2-16, tW·scale/2+46), rb = bh+20) + LETRAS = texto
CORROMPIDO (style==1||style==10 → desenhaTextoCorrompido) + som =
WITHER_SPAWN 0.45 vol 1.2. HELPERS novos: desenhaAnel (elipse tracejada
por colunas dx+=2, sqrt(1-(dx/ra)²)) e desenhaVeias. ESTILOS do lorezone
virou Map.ofEntries (Map.of SÓ ACEITA 10 PARES — 11 estoura!). SONS:
javap confirmou que os campos NÃO têm prefixo ENTITY_ (GHAST_SCREAM,
ALLAY_ITEM_GIVEN, WITHER_AMBIENT, WITHER_SPAWN — todos SoundEvent puro,
sem .value()); ERREI o cast float<<int DE NOVO na faísca divino (padrão
do bug: (int) ternário * vis precisa ((int) (((a)?x:y)·vis))<<24).
check +8/-2 âncoras → 236/0. Smoke verde (demoniaco/inferno/divino/void/
sangue parseiam, 0 ERROR).

**3.86.0 (2026-10-19)** — "faça mais estilos de avisar... o coração ficou
meio esquisito, melhore o 3d dele" (print do coração em uploads: as fatias
retangulares viraram pirâmide listrada). (1) CORAÇÃO REDESENHADO: máscara
pixel-art LINHAS (13px largura × 10 linhas: 2 lóbulos com vale nas linhas
0-1 {2,5,8,11}/{1,6,7,12}, corpo {1,12}, ponta em V {6,7}) EXTRUDADA 5px
(Z0=-2, Z1=3); caixa() com cor POR FACE (tampa clareia(FRENTE[r],0.22),
frente = gradiente FRENTE[10 linhas] de 0xFF7A93 a 0xAE1A3C, paredes LADO
0x9E1234, costas TRAS 0x70102A); reflexo BRILHO 0xFFC9D6 (2×2px no lóbulo
esquerdo, z+0.002); animação + balanço ZP 4°·sin(t·0.0016); translate y
0.02+dy. (2) 6 ESTILOS NOVOS de aviso (client clamp min(9)): 5 INFERNO
(véu 0x150401 + 46 brasas estáticas seed LCG + filete 0x8A2400, tinta
0xFFA347), 6 GELO (véu 0x081523 + 8 losangos desenhaLosango 0xBFE8FF,
tinta 0xDFF4FF), 7 DIVINO (véu 0x110C02 + 5 feixes de luz verticais
0xE8C765/0xC9A227 + ouro, tinta 0xFFEDB8), 8 VOID (véu 0x060310 + 60
estrelas estáticas 0xCBB2FF/branco, tinta 0xCBB2FF), 9 SANGUE (véu
0x0F0103 + colunas de sangue descendo do topo com bolha na ponta, tinta
0xE02020). TODOS com som próprio no somAviso: morte=RESPAWN_ANCHOR_
DEPLETE.value() (HOLDER! usa .value()), inferno=BLAZE_BURN, gelo=PLAYER_
HURT_FREEZE, divino=BELL_RESONATE, void=PORTAL_TRIGGER, sangue=WARDEN_
HEARTBEAT. /rp admin avisar ganhou os 6 LITERAIS (morte não existia!);
nomeEstilo/ajuda/lorezone ESTILOS (10 entradas) e clamps Math.min(9) nos
2 pontos de LoreZones. check +15 → 229/0 (2 âncoras antigas atualizadas:
FATIAS→LINHAS, min(4)→min(9)). ERROS DE COMPILação CORRIGIDOS: import do
BlockEntityRenderer DERRUBADO na reescrita (lição de novo: SEMPRE conferir
imports ao reescrever arquivo inteiro); var cy já existia no método (→gcx/
gcy); (int) ternário sem parênteses quebra <<24; quad de 2 pontos não
existe (4 pontos); Holder.Reference<SoundEvent> precisa .value().
/var/tmp sumiu INTEIRO + libraries de novo (installer 25ª, toolchain 26ª).

(3.86 extra) **REGRESSÃO DO .git DURANTE o ciclo de novo**: o commit 3.85
(cef4e1e) SUMIU da cadeia local (HEAD voltou a 437bb48) e o commit 3.86
(2248cbf) nasceu em cima do buraco → push rejeitado ("fetch first").
RESOLVIDO: force push (o 2248cbf contém as mudanças de 3.85 E 3.86 — nada
perdido) + tag v3.86.0 recriada apontando pro 2248cbf. NOVO RITUAL (antes
de commitar): `git log --oneline -2` tem que mostrar o commit do ciclo
anterior — se sumiu, re-commitar consolidado ANTES e avisar no commit.

**3.85.0 (2026-10-18)** — "teria como colocar aqueles avisos em lorezone?"
SIM: a lorezone agora TEM aviso persistente com os estilos do /rp admin
avisar. (1) Zone record +3 campos: `String aviso, int avisoEstilo, int
avisoSeg` (NBT "Aviso"/"AvisoEstilo"/"AvisoSeg"; zonas antigas = aviso
vazio → título clássico, SEM migration). (2) show(): aviso não-vazio →
ScreenTextPayload(aviso, seg clamp 1..60, estilo clamp 0..4) SUBSTITUI o
título vanilla (som pontual continua); 5 call sites do `new Zone(`
atualizados (load/criar/texto/som/setMusica — os 3 últimos preservam
z.aviso*). (3) `/lorezone aviso <id> <classico|pesadelo|runico|real|
morte> <segundos 1..60> <texto>` + `/lorezone aviso <id> off`; ESTILOS
Map nome→int; translateAmp do RPCommands virou package-private (era
private); estilo 0 sem § = vermelho (padrão avisar); >160 chars corta;
QUEM JÁ ESTÁ DENTRO vê o aviso novo NA HORA (teste imediato, igual
atualizarSons). (4) listar mostra `• id [dim] r=N — título
[aviso:pesadelo]` (nomeEstilo). check +10 → 214/0. Smoke: bibliotecas
INCOMPLETAS (73MB, net/minecraft VAZIO — NOVA variante: o teto do
snapshot não só apaga run/ como TRUNCA; sintoma = "minecraft [MISSING]"
+ NoSuchElementException no boot) → installer 24ª resolveu; `jar` não
está no PATH → /var/tmp/java-21/bin/jar; rcon.py recriado. Smoke verde:
`lorezone listar` OK + `aviso`/`aviso off` respondem "Zona nao existe"
(prova registro), 0 ERROR/FATAL.

**3.84.0 (2026-10-18)** — "faça um item que teria como colocar no chao
... o player poderia recuperar uma vida canon, faça um modelo 3d de um
coração ... quando o item for colocado no chão com botao direito o
coraçao 3d ira ficar girando e quando o player apertar shift click ele
ganha a vida ... aparecendo na tela 'Você ganhou uma nova chance' 'Voce
ganhou uma vida canon'". OFERTA de modelo externo não precisou: coração
3D PROCEDURAL. NOVO: (1) REGISTROS no RPTagMod: DeferredRegister.Blocks/
Items/BlockEntityType + CORACAO_BLOCK ("coracao_canon", Properties
strength(-1,3600000) = indestrutível em survival + noOcclusion +
SoundType.AMETHYST) + CORACAO_BE (BlockEntityType.Builder.of(...).
build(null) — javap) + CORACAO_ITEM (BlockItem, /give rptag:coracao_
canon); registers no ctor. (2) CanonCoracaoBlock (EntityBlock): SHAPE
box(2,0,2,14,12,14); useWithoutItem (javap: protected em
BlockBehaviour, 1.20.5+): SEM shift = actionbar dica; SHIFT = +1 vida
(setCanonLives, cap 5) + removeBlock (coração some) + AMETHYST_CHIME +
ScreenTextPayload("VOCÊ GANHOU UMA NOVA CHANCE — ... (N/5)", estilo 0
caixa typewriter); 5/5 = NÃO consome (fica pra quem precisar).
(3) CanonCoracaoBlockEntity trivial. (4) BER CanonCoracaoRenderer:
coração de 9 FATIAS voxel (float[][] de {-1,1,-1,1} a {1,6,-2,2} — cada
1px de altura, SEM sobreposição = sem z-fighting), RenderType.lightning()
(vanilla, padrão Portais, amiga de Iris), quad frente+verso (lightning
TEM cull), cores TOPO 0xFF6B80/LATERAL 0xD9274A/BASE 0x7A122E; anim:
giro (t*0.03°/ms) + pulso (1+0.10*sin(t*0.006)) + flutuação (sin(t*
0.0022)*0.03); try/catch (BER não derruba o render). CONSTRUTOR com
BlockEntityRendererProvider.Context (obrigatório — "invalid constructor
reference" sem). (5) client/ClientRenderers: EntityRenderersEvent.
RegisterRenderers → registerBlockEntityRenderer (javap). (6) ASSETS:
blockstates (1 variante), models/block = {"elements":[]} (invisível — o
visual é o BER), models/item = generated + textures/item/coracao_
canon.png (pixel-art 16x16 via PIL: coração 12x10 com highlight), lang
pt_br/en_us "block.rptag.coracao_canon". Smoke: give @a aceito (sem
"Unknown item" — registro validado). /var/tmp sumiu (21a/22a vez:
cp.txt + rcon.py), toolchain + rcon recriados. LIÇÃO: heredoc de docs
precisa rodar NO DIRETÓRIO do repo (1x falhou com cwd errado — os docs
não escreveram mas o commit foi; refazer docs + commit extra).

(extra pós-3.84) **.git REGREDIU DE NOVO (3ª vez)**: HEAD voltou sozinho a
8f3b809 (3.58.1!) e os commits 3.59→3.84 se perderam; working tree intacta →
re-commitado CONSOLIDADO **a9b2e82** ("3.59.0→3.84.0 CONSOLIDADO"). Portais
perdeu o .git inteiro → re-init + commit **8cdeb61** (1 commit, estado 1.3.2).
LIÇÃO NO RITUAL: começo de TODO ciclo → `git log --oneline -1`; HEAD errado =
re-commitar consolidado ANTES de qualquer coisa. PLANO DEFINITIVO: GITHUB
monorepo (pedido do user: os 2 mods NO MESMO repositório) — rp-tag na raiz +
portais-da-lore/ dentro; README raiz tem tabela dos 2 mods e o aviso "PORTAIS
EM PAUSA TEMPORÁRIA" (palavra do user: "fala que ta la o mod mas descontinuado
temporariamente pq nao quero mexer nele"); portais-da-lore/README.md = banner
de pausa. Conta pelo README antigo: **EduCafilista**. FEITO (mesmo dia): repo PÚBLICO **github.com/EduCafilista/rp-tag** já existia
(conteúdo 3.52) → **force push** do monorepo (main = 6450fb1; d7c1d1e antigo
substituído). RELEASES criadas: `v3.84.0` (id 416734009, marcada latest) com
rptag-3.84.0.jar + `portais-v1.3.2` (id 416734024) com portais-da-lore-
1.3.2.jar; tamanhos conferidos byte a byte; 6 links públicos testados 200
(repo, releases, 2 downloads, 2 READMEs). ROTINA DE CICLO agora termina com:
commit + `git push --force` SE necessário / push normal (URL inline com o
PAT — NÃO salvar remote com token em .git/config, que some entre ciclos;
token vive só na conversa — se o user revogar, pedir outro). Usuário:
**EduCafilista**.

**3.83.0 (2026-10-18)** — "na ultima vida coloque 'SUA ULTIMA VIDA
TOME CUIDADO' algo assim, na ultima tem que dar um medo abusrdo, para
a pessoa ficar preocupada, muito bizarro". CanonLife.ritualDeVidaPerdida
(restam==0): (1) aviso = "VOCÊ PERDEU SUA ÚLTIMA VIDA — TOME CUIDADO";
(2) MEDO ABSURDO = o efeito de medo no TETO do sistema: FearPayload(30)
+ addEffect(MobEffectInstance(RPEffects.MEDO, 30*20, 0, false, false))
(cópia exata do /rp admin medo 30 — véu azul-noite que ESCONDE +
tremedeira + icone no HUD, por CIMA de todo o ritual: strondo+télé+
audio+texto). check.py: a string java tem \u2014 LITERAL (escape no
source) — no check.py tem que ser '\\u2014' (o — python interpretado
falhou 1x). /var/tmp rcon.py sumiu de novo (20a vez), recriado.

**3.82.0 (2026-10-17)** — 2 prints (LORE ON/OFF novos APROVADOS,
"está quase lá") + 2 pedidos: "tire o embaçamento das letras" e o
sistema de vidas: "depois que a pessoa morre em canon on, se ela
morrer outras vezes ele nao perde mais vidas canon, tem tipo um
limite naquele momento, para ele nao morrer seguido e perder varias
vidas de uma vez, cada player tem 5 no começo". (1) linhaTexturizada
SEM a 3a camada (o brilho em -0.6px = halo liam como embaçado) —
sombra + fill apenas; assinatura perdeu o param topo; paleta do
banner ficou sombra+base só. (2) VIDAS: RPWorldData Map<UUID,Integer>
canonLives (getOrDefault 5 — VIDAS_INICIAIS), NBT "CanonLives"
(ListTag {Id, Lives}, persiste); CanonLife: COOLDOWN_MS = 5*60_000
(5 min) + PROTECAO_ATE Map<UUID,Long> — morte dentro da proteção NÃO
perde vida (fim do chain-kill); PROTECAO_ATE de propósito NÃO limpa
no logout (relog não burla; restart limpa, aceito); estoque 0 = não
perde mais; aviso agora mostra "VOCÊ PERDEU UMA VIDA — RESTAM N" ou
"VOCÊ PERDEU SUA ÚLTIMA VIDA CANÔNICA" (restam 0). /rp status:
"Vidas canônicas: X/5 · modo de PERDER: ...". check.py +6.

**3.81.0 (2026-10-17)** — print do banner ("a mensagem de vidas on
ficou esquisito na tela do player, refaz e deixa melhor"): a variação
de cor POR LETRA (manchas seed%5/seed%7) deixava cada char de uma cor
(patchwork roxo/verde). REFEITO LIMPO: linhaTexturizada agora e linha
SÓLIDA — cor ÚNICA pra frase inteira (aFill | base), sombra quase
preta (+1,+1.2) e brilho claro UNIFORME em todas as letras (0,-0.6);
paleta nova ON: sombra 0x07130A / base 0x3FA34D (verde VIVO) / topo
0x8FE896; OFF: 0x160404 / 0xC41F1F / 0xF07070. Faixa: alpha 150→202
(79% preto), padding 44→56, filetes topo/base 2px OPACOS (190*vis) na
cor do banner. Ritual de morte INTACTO (strondo/télé/audio/estilo 4).
check.py: paleta nova + '(int) (202 * vis) << 24' + marker 'aFill |
(base & 0xFFFFFF)'.

**3.80.0 (2026-10-17)** — print do banner verde + "está bugado ainda o
canon, e não é para aparecer no chat aquela mensagem, não precisa dela,
os players JÁ TEM AS VIDAS CANON, E TEM A CHANCE DE PERDER APENAS
QUANDO O ADM ATIVAR A OPÇÃO DE CANON". MODELO DEFINITIVO (2a reversão
— a 3.76 já era assim, a 3.77 foi engano meu): TODOS os players JÁ
têm vidas canônicas; /rp admin canon ON|OFF (SEM jogador) liga/desliga
o MODO onde quem morrer perde uma. RPWorldData voltou a boolean
canonEvent + NBT putBoolean/getBoolean("CanonEvent"); CanonLife checa
isCanonEvent() no respawn. CHAT: NENHUMA mensagem — as sendSuccess
viraram broadcast FALSE (só feedback do comando pro adm/log) e as
sendSystemMessage pro alvo foram REMOVIDAS; o aviso é o BANNER
LORE ON/OFF, agora via PacketDistributor.sendToAllPlayers (server
todo vê). Status: "Vidas canônicas: todos têm · modo de PERDER:
LIGADO/desligado". Ritual de morte INTACTO (strondo+télé+audio+texto).
check.py: checks do modelo por player → global; add 'banner BROADCAST
pro servidor todo; SEM msg de chat' ('sendToAllPlayers' in rp and
'Vida CANÔNICA concedida' not in rp). LIÇÃO: antes de virar o modelo,
RELER a última frase do user com calma — "os players JÁ TEM" era o
plano desde o início.

**3.79.0 (2026-10-17)** — 2 prints: (1) "não quero tremido quando eu
morrer, só mostra a mensagem na cara da pessoa" — os desvios FIXOS por
char (±1,5px) na escala 3x LIAM como tremedeira; (2) "o vida on ta
bugado a pessoa quase nao consegue enxergar" — o verde texturizado
morria contra o ceu. FIXES: (1) desenhaTextoTexturizado() (estilo 4) e
linhaTexturizada() (banner) SEM dx/dy — letras RETAS, textura = só
variação de cor (manchas) + topo irregular; (2) desenhaBannerCanon()
ganhou FAIXA ESCURA atrás das 2 linhas (bandW = max(titulo,sub)+44,
bandY0/Y1 = cyT-12 / cyS+lineHeight*sEsc+12, fill (150*vis)<<24 =
59% preto + filetes coloridos topo/base da faixa) — o banner le em
qualquer cenario. checks novos: 'float dx = ((seed % 5L)' NOT in ce
(reto) + bandW. LIÇÃO (3x no mesmo ciclo): âncora de patch tem que ser
COPIADA do arquivo, não da memória — falhei 2x (boot log estava na
3.77.0, não 3.78.0 — e nem tinha sido atualizada na 3.78; e âncora B
sem as linhas dx/dy). Regra: grep do trecho ANTES, SEMPRE.

**3.78.0 (2026-10-17)** — "quero mais efeitos no perdeu uma vida,
mostra a tela meio avermelhada, com um strondo e começa o audio".
RITUAL COMPLETO agora: (1) STRONDO = LIGHTNING_BOLT_THUNDER via
playNotifySound(WEATHER, 3.0F, 0.8F) no SERVIDOR, na hora do respawn;
(2) VÉU AVERMELHADO = novo estado no ClientEffects (veilStart/veilUntil
ligado ao aviso estilo 4 em startScreenText) + desenhaVeuCanon() na
camada oficial "canon_veu" (REGISTRADA ANTES do aviso_hud = texto por
cima do véu): fill 0x5A0D0D com alpha 0x5A·fade (in 300ms/out 600ms)
· pulso sin(now*0.004) ±10% ("a tela respira") + filetes 0x8B1616
topo/base; (3) O AUDIO da lore entra 1,5 s DEPOIS do strondo: o
PROPRIO CLIENTE dispara dentro do desenhaVeuCanon (somCanonDue/
somCanonPlayed) via SoundManager.play(SimpleSoundInstance.forUI(
SOM_CANON_DEATH.get(), 1.0F, 1.6F)) — forUI(SoundEvent,float,float)
javap-confirmado; o servidor NÃO toca mais o ogg (só o trovão).
Sequência ouvida pela vítima: TRONCO! … 1,5s … a musica sombria
(9,5s) enquanto congela com a tela vermelha e o texto estático.
Import SoundEvents tinha SAÍDO do CanonLife na 3.76 (javac pegou na
hora; recolocado).

**3.77.0 (2026-10-17)** — 2 prints de referência (" LORE OFF  /
 HARD-RP ON | VIDAS CANNON OFF " vermelho e " LORE ON  / ...ON "
verde, texto texturizado no mundo) + "o rp admin canon deve ser como o
efeito de medo, para os admin usar em players". REVERSÃO do modelo
server-wide → POR JOGADOR (como o medo): /rp admin canon <jogador>
on|off; RPWorldData voltou a Map<UUID,Boolean> canonLife + NBT
"CanonLife" (ListTag); CanonLife.java checa isCanonLife(uuid) no
respawn. NOVO PAYLOAD CanonBannerPayload(title, sub, seconds, on)
type "canon_banner" (STREAM_CODEC com 2 STR_UTF8 + VAR_INT + BOOL;
BOOL = ByteBufCodecs.BOOL), playToClient → ClientPayloadHandler.
handleCanonBanner → ClientEffects.startCanonBanner (campos volatile
bannerTitle/bannerSub/bannerStart/bannerDuration/bannerOn) → camada
oficial "canon_banner" no ClientGuiLayers (com shaders). DESENHO:
linhaTexturizada() helper (mesma técnica do estilo 4, parametrizada p/
cores; sombra SEMPRE, topo em 2 de 3 chars, manchas = escurece/claro)
e desenhaBannerCanon() = 2 linhas centradas (cyT=h*0.34, cyS abaixo),
escala título ≤2.6 / sub ≤1.1 (fit w-30), fade in 250ms/out 400ms,
a prova de crash (try/catch limpa bannerTitle). PALETAS: ON verde
sombra 0x0E2A14/base 0x2F6A38/topo 0x549E5C; OFF vermelho
0x2A0505/0x8B1616/0xC23030. TEXTOS LITERAIS dos exemplos: " LORE
ON "/" HARD-RP ON | VIDAS CANNON ON " (\u2740) e OFF. Death ritual
INTACTO (freeze 5s + "VOCÊ PERDEU UMA VIDA" estilo 4 + som ogg).
Status: "Vida canônica: ATIVA (ao morrer, perde uma vida)/não tem".
Ah — bug meu na 1ª linhaTexturizada: sombra com "cy * 0.0F + dy"
(sobra inofensiva, cy já foi consumido pelo translate).

**3.76.0 (2026-10-17)** — feedback da 3.75 (prints): (1) "não quero
essa mensagem" (a de conceder falava de bruxa); (2) "a vida canonica
todo player tem e tals, efeito igual o medo, ai o efeito seria ativado
durante um evento que os admin ativa" → MODELO NOVO: canon = EVENTO
server-wide (/rp admin canon on|off, SEM jogador — igual bolhas
on|off); NBT virou boolean "CanonEvent" no RPWorldData (persiste);
(3) "não quero essa parte da bruxa... quero apenas falando que o player
'VOCÊ PERDEU UMA VIDA'" → FRASE = "VOCÊ PERDEU UMA VIDA" (literal);
mensagens do comando sem bruxa; (4) "não quero esse aviso pesadelo e
de morte que as letras ficam mexendo, quero texturização na FONTE
igual na foto" → estilo 4 NOVO: desenhaTextoTexturizado() = camadas de
sangue + desvios FIXOS por char (seed i*0x9E37, sem tempo!) + manchas
(0x7E1010/0xC23030) + topo quente em 2 de 3 chars — ESTÁTICO, nada
de onda/sway/ghost; clamp do textStyle min(4); pesadelo 3.74 INTACTO
(continua pra /rp admin avisar pesadelo); (5) "arrume o som, não
apareceu" → fila/som vanilla ABANDONADOS: som CUSTOM da lore (catbox
g31le4.mp3, 9,5s) BAIXADO e convertido com soundfile (peak 0.55, mono,
como os do Portais) → assets/rptag/sounds/canon_death.ogg +
sounds.json {"canon_death": {"sounds": [{"name": "rptag:canon_death",
"stream": false}]}}; registro no RPTagMod: DeferredRegister<SoundEvent>
SOUNDS + SOM_CANON_DEATH = SOUNDS.register("canon_death",
createVariableRangeEvent(...)) + SOUNDS.register(modEventBus) no ctor
(MESMO padrão do LorePortalsMod); playNotifySound(SOM.get(), MASTER,
1.5F, 1.0F) no respawn — ATENÇÃO: Supplier usa .get() (Holder usa
.value(); javac pegou). Ritual: respawn → congela 5s + aviso estilo 4
(8s) + som 1x. status mostra "EVENTO LIGADO/desligado". Ajuda atualizada.
run/libraries sumiu (16a vez); installer+rcon re-run. ffmpeg NÃO existe
no sandbox — conversão = python soundfile (read mp3 OK, write OGG/VORBIS).

**3.75.0 (2026-10-17)** — 2 pedidos: "mude a texturização das letras
do pesadelo, mude um pouco em volta" e criar o EFEITO VIDA CANÔNICA
(infinito até o adm tirar): morreu = player congelado alguns segundos +
aviso insano + som famoso de Re:Zero. PESADELO (só o texto, veu/bordas
intactos): SWAY (texto inteiro escorrega de lado, sin(now*0.0008)*1.6),
fase PRÓPRIA por char na onda (seed%7*0.05 — acabou a senoide), GHOST
(char que falha deixa rastro escuro em -0.8,-0.4, alpha 70). VIDA
CANÔNICA (server/CanonLife.java novo, @EventBusSubscriber game bus):
(1) /rp admin canon <jogador> on|off (padrão do bolha on|off); NBT
"CanonLife" no RPWorldData (map canonLife + save/load ListTag — infinito
até o adm tirar, sobrevive relog); (2) ao MORRER e RENASCER
(PlayerEvent.PlayerRespawnEvent): ritualDeMorte() = congelamento 5s
(FREEZES map {uuid: Freeze(until, x,y,z)}; aCadaTick devolve pra âncora
se dist²>0.05, setDeltaMovement(ZERO), fallDistance=0) + aviso
ScreenTextPayload(FRASE, 7s, estilo 1 pesadelo) + fila de sons;
(3) FRASE insana: "SEU CORAÇÃO PAROU — UM FRAGMENTO DA SUA VIDA FOI
DEVORADO PELA BRUXA"; (4) SOM "Re:Zero" (homenagem, NÃO é áudio do
anime): WARDEN_HEARTBEAT 3x acelerando (gt+1/+12/+24, pitch
0.8/0.7/0.6) → SLIME_SQUISH pitch 0.35 (o ESMAGAMENTO, gt+32) →
ELDER_GUARDIAN_CURSE pitch 0.5 (gt+36); playNotifySound = SÓ A VÍTIMA
ouve (javap: Player.playNotifySound(SoundEvent, SoundSource, float,
float)); fila = ArrayDeque<Pending(due, who, snd, pitch, vol)> drenada
no PlayerTickEvent.Post do dono com gameTime do overworld; (5) limpeza
no PlayerLoggedOutEvent (sem leak); (6) /rp status mostra "Vida
canônica: ATIVA/não". PlayerTickEvent fica em
net.neoforged.neoforge.event.tick (não entity.player!) — javap antes.
Fim do mundo do /var/tmp: 15a vez (java-21, cp.txt, rcon.py sumiram).

**3.74.0 (2026-10-16)** — user mandou print de referencia (texto
vermelho estilo terror "Nao ha escolha a ser feita.") e pediu aquela
cara TEXTURIZADA pro texto do aviso PESADELO: "consegue fazer essa
texturizada na mensagem vermelha de pesadelo?... eu gostei de todo o
resto do avisar pesadelo". FIX SÓ NO TEXTO (veu/bordas/faixas/som
INTACTOS): desenhaTextoCorrompido() — o estilo 1 saiu da dupla
passada reta e virou desenho POR CARACTERE: (a) ONDA lenta (sin(x*0.16
+ now*0.0015) * 1.1px pre-escala, ciclo ~4,2 s) — os chars flutuam;
(b) 3 CAMADAS de sangue: sombra 0x3A0606 (+0.9,+1.1), base 0xA11A1A,
topo quente 0xE04848 (-0.6, alpha 140) = degrade vertical; (c) FALHAS
deterministicas: seed=(i*0x9E37+tick*0x85EB), tick=now/1900 — seed%13
= +1px x, seed%11 = +1px y, seed%17 = char ESCURO 0x6E0B0B por ~2 s
(1-2 chars por vez, sem aleatoriedade por frame = sem epilepsia);
(d) respeita vis/fade/pop/crescendo (recebe scale e vis prontos);
(e) codigos § removidos antes (pesadelo tem tinta fixa). Runico/real/
classico/caixa INTACTOS (a dupla passada "serio" vive no else). Log
do boot da camada de avisos → "3.74.0". Âncora do patch FALHOU 1x
(a dupla passada NÃO é o fim da classe — a caixa 3.69 vem depois);
re-ancorado no trecho real. /var/tmp sumiu de novo (14a vez: java-21,
cp.txt, rcon.py) → toolchain.sh + installer + rcon.py recriados.

**3.73.0 (2026-10-16)** — "ficou bom, mas da para melhorar, ficar algo
mais realeza algo bonito" → EDIÇÃO REAL (só vestimenta, layout ZERO):
(1) desenhaCoroa(g,x,y) = coroa 11x7 OURO com 3 pontas, joias (rubi
0xFFE04858 + 2 safiras 0xFF5878F0) e faísca no topo — DUAS coroas
ladeiam o título (tx0-16 e tx0+w+5, py+7); o " ... " do título saiu
(as coroas substituem). (2) desenhaDiamante(g,cx,cy) = losango ouro
7x7 c/ miolo claro — 4 diamantes nos cantos do painel (era cantinho
reto). (3) painel VELUDO: gradiente 0xF23A2566→0xF21E1140 (roxo real),
filete violeta → OURO VIVO 0x78E8A83C. (4) título: faixa 0x66351F4A,
texto OURO 0xFFFFD98A. (5) placas PLACA_BG 0xFF2A2148; slots de extras
0xFF241D3D; card outline OURO-ANTIGO 0xFF8A6A2E. (6) botões de vidro
ROXO-REAL (base 0xFF2A2350/hover 0xFF3D3270/off 0xFF221C38, borda
0xFF7A6AA8/0xFF443C64); slider: trilho 0xFF221C38, preenchimento
0xB0FFD98A. (7) galáxia: cada 7a estrela de OURO. checks: 'faixa
vitral violeta' → 6 novos (coroas/diamantes/veludo/placas/botões/ouro).

**3.72.0 (2026-10-16)** — print 3.71 ("está meio bugado") revelou 5
defeitos latentes, todos corrigidos: (1) DICA VAZANDO pra fora do painel
(a direita, no ceu) — a dica da digitacao tinha ~360px num painel de
248px; criado helper corta(s, maxW) (reticencias U+2026) e TODAS as
linhas de 1 linha truncam agora: dica corta(hint, PANEL_W-20) e a
dica ENCURTADA p/ " Digitacao LIGADA — /balao typewriter off";
(2) FALA do chat agora trunca com "…" (falaMostrada = corta(fala,
PANEL_W-24-width("<Você> ")-width(sufixo))) — antes falas compridas
vazavam; (3) X vermelho dos slots de extras DENTRO da caixinha
(12..20, 1..9 — o antigo 10..20/-2..8 vazava 2px acima do slot e
cobria o emoji; checks 122/157 mudaram pro marcador "X DENTRO do
slot"); (4) tag " Previa no mundo" SUBIU (cardH-21..-11) pra sair de
cima da grama (parecia barra verde quebrada); (5) chip preto do ICONE
da moldura ativa ganhou outline 0xFF4A5480 (sem borda parecia glitch).
NAO-bugs confirmados na print: os "▼" brancos = indicadores de cor
selecionada (drawSwatchRow y-8, por design desde 3.50) e os "+" nos
slots vazios = affordance de adicionar (0xFF7A8498). run/libraries
sumiu de novo (12a vez) → installer re-run.

**3.71.0 (2026-10-16)** — print 3.70: "o menu ainda está embaçado e a
pessoa nao vai conseguir ler" (painel borrado, botões nítidos). ROOT
CAUSE achada no BYTECODE (javap -c Screen): o blur vanilla vive DENTRO
de Screen.renderBackground(); NO 3.68 EU DELETEI O OVERRIDE do
renderBackground (quando transformei o metodo em desenhaGalaxia) — com
isso o renderBackground VANILLA voltou a rodar dentro do super.render()
(que o render() chama DEPOIS do painel) e borrou O FRAMEBUFFER INTEIRO
(mundo + painel já desenhado); os widgets (super.render depois do blur)
nasciam nítidos — por isso só o painel ficava embaçado. FIX: override
VAZIO de renderBackground() ("VAZIO DE PROPOSITO" no javadoc, check.py
confere count('renderBlurredBackground') == 1). O blur do MUNDO continua
sendo desenhado 1x no COMECO do render() (renderBlurredBackground
explicito). Lição de ferro: NUNCA remover override de metodo vanilla que
o super.render chama — o corpo vanilla volta a rodar; e javap -c ACABA
com suposição (eu jurava que o blur era no render()).
run/libraries sumiu de novo (11a vez) → installer re-run.

**3.70.0 (2026-10-16)** — print do 3.68 NO MUNDO: menu tava escuro
demais, "quando eu disse o fundo ficar embaçado eu disse do mine normal,
a tela a pessoa tem que ver, mas gostei do jeito que está ficando o
painel ficou bonito". CAUSA: o véu era 0x96 (59% opaco) + a galáxia
INTEIRA (nebulosas, via lactea, vinheta) pintada POR CIMA do blur — o
mundo ficava invisivel. FIX (2 MODOS): com blur OK = véu LEVE 0x36 (21%)
e desenhaGalaxia(g, true) — so 64 estrelas finas + 12 vivas + cometa
(SEM nebulosas/via lactea/vinheta); sem blur (fallback) = opaco + galáxia
completa desenhaGalaxia(g, false). PAINEL/BOTÕES INTACTOS (aprovados).
desenhaGalaxia agora recebe boolean leve; gates if (!leve) em nebulosas+
via lactea e vinheta. run/libraries sumiu DE NOVO (10a vez) → installer
re-run; toolchain/rcon.py sobreviveram dessa vez.

**3.69.0 (2026-10-16)** — user mostrou PRINT de referencia (stream c/
caixa de chat "Lider da Guilda: Se me chamar de careca de novo apanha")
e pediu o aviso typewriter assim: "o campo aparece primeiro e depois as
letras sendo escritas" (ex.: "Sniper: eu vou atras de voces hein").
O typewriter JA existia (3.64, fix 3.66) — o que mudou foi o DESENHO do
estilo 0 (classico): (1) caminho proprio desenhaAvisoCaixa() — sai da
faixa GRAD_BAR central (blit APOSENTADO do classico; GRAD_BAR_TEX field
morto, inofensivo) e virou CAIXA DE FALA ancorada ACIMA do hotbar
(boxY = h-70-boxH, centro na base): sombra + aro escuro + vidro
0xB90B0E15 + luz no topo + BARRA AZUL 0xFF8C9EFF na esquerda. (2) O
CAMPO nasce PRONTO (medidas no texto completo, linhas ESTAVEIS via
quebraLinhas() por palavra, max 3 linhas c/ escala 1.25→0.8) e so
DEPOIS as letras sao escritas: tAtraso=220ms, typed(limpo, elapsed-220)
(30 ms/char, mesma cadencia), cursor "_" PISCANDO (blink 300ms) no fim
da parte visivel. (3) NOME COLORIDO: texto antes do primeiro ":" em
azul-periwinkle 0xFF8C9EFF (ex. "Sniper:"), resto branco. (4) Entrada
pop crescendo 0.88→1.0 (mesma formula do center) + fade-in do campo
180ms; saida fade 500ms. Pesadelo/runico/real INTACTOS (aprovados) —
continuam grande no centro; so o classico/default mudou. Log do boot da
camada de avisos atualizado ("3.69.0: camada de avisos carregada").
Lição: o /rp admin avisar SEM estilo = 0 = esta caixa — o exemplo do
user ("Sniper: ...") é o caso de uso TIPICO dela (nome: fala).
/var/tmp sumiu DE NOVO entre ciclos (java-21+cp.txt+installer+rcon.py)
→ toolchain.sh + installer (9a vez) + rcon.py recriados.

**3.68.0 (2026-10-15)** — print do 3.67 APROVOU o layout; user pediu
"algo mais dahora, um quadrado talvez com o fundo embaçado, pegada de
galáxia muito foda". REDESIGN do menu (só visual, layout intacto):
(1) FUNDO EMBACADO = blur VANILLA do mundo `renderBlurredBackground(pt)`
(protected em Screen, javap-confirmado) dentro de try/catch + véu
0x960B0E15; sem mundo/falha = fill opaco 0xFF0B0E15 (a prova de crash).
(2) desenhaGalaxia(): 5 nebulosas em BLOBS (elipses concêntricas 5
níveis, deriva 90s) roxo/azul/magenta/teal, via láctea diagonal (4
blobs + 46 estrelas), 90 estrelas finas + 26 VIVAS (pulso 3 fases +
cruz de brilho no pico), COMETA a cada 9s (janela 1,1s, cauda 6
segmentos), vinheta mantida. blob() = helper de elipse macia.
(3) drawPanel = VIDRO ESPACIAL: fillGradient 0xF2252C52→0xF4120E2C,
luz no topo, respiro violeta na base, moldura 3 camadas (ouro
0xFFE8A83C / violeta 0x608A5FE0 / fillet 0x5CE8A83C vivo) + barra
dourada lateral + cantinhos 0xFFFFD98A. drawTitle = vitral violeta
0x66251F4A com filete duplo, texto creme 0xFFFFE9B8.
(4) card da prévia = CENÁRIO PROCEDURAL (SKY_TEX/blit APOSENTADO —
campo morto, inofensivo): céu fillGradient + 26 estrelas + LUA CHEIA
com halo/crateras + grama 2 tons com dentes + outline 0xFF4A5480.
(5) BOTÕES DE VIDRO: inner class GlassButton extends Button (renderWidget
protected, fundo 0xFF272D4E/hover 0xFF3A4270, luz no topo, borda de
luz = accent no hover, filete accent na base, texto creme) + 2
construtores (String e Component — bordaToggleLabel()/modeLabel()/
durationLabel() retornam COMPONENT, não String!). GlassSlider extends
AbstractSliderButton (renderWidget PUBLIC lá — override tem que ser
public; value protected) = trilho escuro + preenchimento ouro + knob.
TODOS os botões (teclado de adesivos incluso, 80 células) e o slider
usam vidro; accents: molduras OURO, Adesivos rosa 0xFFE58ACC,
LIGADO/Salvar verde 0xFF5CE87C, Padrão cinza, teclado roxo.
Lição: lambda b -> b.setMessage() continua ok (GlassButton É Button).
super.render() NÃO chama renderBackground em 1.21.1 (prova: painel
desenhado antes de super.render sobrevive) — pode deletar o override.

**3.67.0 (2026-10-15)** — user mandou a PRINT do menu 3.66: "muito
esquisito e feio... deixe esse bonita"; "os emojis para tirar ta no lugar
errado" (os slots de 20px COBRIAM o titulo "Emojis (clique no X...)" e o
botao "◐ 13s"); "tem uma parte transparente bugada em volta do balao" (o
halo do estilo BRILHO sujava o ceu do card de previa). FIXES: (1) titulo
dos Emojis ganhou LINHA PROPRIA (py+ROW_EXTRAS-19 = py+224; os slots
ficam em 243, os botoes em 264 — o vao 215..240 tava livre). (2) X
vermelho da coluna direita agora aparece SO com o mouse em cima do emoji
(hitbox gx-3..+bpx+3) + contorno vermelho — os X permanentes da 3.66
"flutuavam" fora da pílula (gy-5). (3) halo do BRILHO na previa suavizado
0xCC -> 0x77. (4) estrelas discretas: 56 (era 110), alpha 0x16..0x32, e
pontos de 1px (2px a cada 5). (5) faixa do titulo VISIVEL 0xFF3B455F +
filete duplo dourado (o 0xFF1F2534 era igual ao fundo do painel).
(6) FUNDO do painel 0xFF2B3245 -> 0xFF313B54 (contraste maior contra o
ceu 0xFF0B0E15). Lição do ciclo: FAIXA DO TITULO vs FUNDO = quase a
mesma cor → "menu invisível/feio"; SEMPRE olhar a print do user.
Emblema (3.66.0, vivo): badge do adesivo vira PICKER (4x8 grade em
MENU_K) ao clicar; SEMPRE desenha a coluna direita (antes sumia sem
adesivo); extrasXBoxes reconstruida a cada renderSim; clique no emoji
com X = remove (testado ANTES da branch que adiciona).

**3.66.0 (2026-10-15)** — user mandou 5 PRINTS: avisos Aparecem mas
"testei com a palavra caraca e so foi a letra c"; som runico igual ao
pesadelo; X vermelho só na esquerda da prévia (direita sem X); menu
"transparente" em cima; pediu menu mais frufu. FIXES: (1) typed() do
aviso NUNCA anexava o caractere normal (só códigos de cor) — cada frame
caía no fallback de 1 char = "C". Agora anexa + começa com 1 letra
visível. (2) AVISO CRESCENDO: scale-in 0.88→1.0 em ~0,45 s (o
"crescendo" que o user quis desde o início era no AVISO). (3) som
rúnico PRÓPRIO = AMETHYST_BLOCK_CHIME (maldição ficou só no pesadelo).
(4) X vermelho em CADA emoji da coluna DIREITA da prévia (extrasXBoxes,
clique-testado ANTES da branch que adiciona emoji) + bônus: a coluna
direita SUMIA inteira quando não havia adesivo (reestruturada pra
sempre desenhar). (5) menu: fundo opaco GARANTIDO dentro do render()
(além do renderBackground), céu de estrelas frufu, filete dourado
interno + cantinhos no painel, título centralizado " Balão de Fala ".
check 138/138; smoke 0 erros.

**3.65.0 (2026-10-15)** — user: "os 3 avisos NENHUM aparece na tela" +
"typewriter era pro AVISO, no balão vira OPÇÃO" + "menu esquisito".
(1) TYPEWRITER DO BALÃO = OPÇÃO: /balao typewriter [on|off] (persistente
por jogador, NBT "Typewriter" no RPWorldData) + payload TypewriterPayload
BIDIRECIONAL (playBidirectional! o MESMO TYPE registrado playToServer +
playToClient CRASHA o boot: "Cannot register payload ... already
registered" — provado) + estado visível na tela /balao (linha de dica).
(2) AVISO BLINDADO: flags SEPARADOS (medoDisabled/avisoDisabled — o erro
do medo não mata mais o aviso), try/catch próprio no desenhaAviso, LOGS
de diagnóstico ("AVISO recebido (estilo X)" + "desenhando aviso na tela
(1o frame)") — SE o user testar e ainda não aparecer, o log diz se o
payload chegou. typed() vazio não cancela o draw.
(3) MENU: dica duplicada ("Clique no X vermelho") agora só no título dos
Emojis; no topo mostra o estado da digitação. Ajuda + linha typewriter.
check 131/131; smoke: boot OK, /balao typewriter exige player (RCON ok),
ajuda lista, 0 erros.

**3.64.0 (2026-10-15)** — TYPEWRITER no balão (a fala se escreve sozinha,
28 ms/char, usando o bornMillis que já existia — mecânica dos mods
famosos, SEM decompilar nada) + AVISO com 4 ESTILOS e VÁRIOS alvos:
`/rp admin avisar <jogadores|@a> [classico|pesadelo|runico|real] <texto>`
(payload ganhou campo style; pesadelo = véu preto + borda sangue +
maldição de guardião ancião; runico = véu arcano + runas; real =
azul-noite + ouro + beacon; TODOS com typewriter 30 ms/char e SEM pulso).
Ajuda atualizada. Checks 125. Smoke: boot OK, /rp ajuda OK, 3 estilos
parseiam (sem players o @a responde "No player was found" = vanilla OK),
0 erros.

---

## 2. O ritual de cada ciclo (como entregar)

1. **Editar as sources** do `/home/user/rp-tag` (feature/correção do ciclo).
2. **`python3 check.py`** — checks estáticos do código; só segue se 0 FAIL.
3. **Build** (seção 7) — `javac` SEM pipe em `exit 0`; só jarifica se compilou.
4. **Smoke real** (seção 7) — servidor NeoForge sobe com o jar + comandos via RCON + `grep` de erros = 0.
5. **Docs**: `CHANGELOG.md` (nova versão no topo), este WALKTHROUGH (seção 1 + seções afetadas), `divulgacao/COMO-USAR.md` (bloco "NOVO na X.Y.Z" no topo).
6. **Git**: commit local (recriar `.git/config` se sumiu — seção 7) — **push é por fora** (só o dono tem o PAT).
7. **Zip das sources**: `git archive --format=zip -o /home/user/rptag-fonte-<ver>.zip HEAD`.
8. **Entrega**: copiar o jar pra `/home/user`, `divulgacao/` e `run/mods/`; **`present_file` do jar no chat**; apagar jars velhos do workspace.
9. **Sempre avisar na resposta**: *"apague o jar antigo da pasta `mods` — no SERVIDOR e no CLIENTE"* (falha recorrente!).
10. Responder em **PT-BR**.

---

## 3. Mapa do repositório

### Raiz
| Arquivo | Papel |
|---|---|
| `check.py` | 85 checks estáticos (roda antes de qualquer entrega) |
| `toolchain.sh` | baixa JDK 21 + gera `nf-binary.jar` (MC desofuscado) + `cp.txt` (classpath) do zero |
| `build.gradle` / `settings.gradle` | metadados (versão do mod em `version = '...'`) |
| `CHANGELOG.md` | registro detalhado por versão |
| `WALKTHROUGH.md` | **este documento** (memória de trabalho) |
| `README.md` / `README.en.md` | vitrine do repo |
| `ENTREGA.md` / `PUBLICAR.md` | notas de processo |

### Código — `src/main/java/dev/rptag/`
| Área | Arquivos |
|---|---|
| Núcleo | `RPTagMod` (constantes/VERSION), `ModNetworking` (canal "5"), `ServerEvents`, `RPWorldData` |
| Tag/Persona | `RPTags`, `Persona`, `PersonaCommands`, `PersonaSyncPayload`, `SyncRPStatePayload`, `RPCommands` (`/rp`, `/rp admin`, `/do`), `RPChat` |
| Balões (comum) | `BalaoCommands` (`/balao`), `BubbleStyle`, `UpdateBubbleStylePayload`, `SetBubbleModePayload`, `OpenBubbleStylePayload`, `ChatBubblePayload`, `ScreenTextPayload`, `Stickers` |
| Balões (client) | `client/BubbleRenderer` (desenho no mundo), `client/BubbleStyleScreen` (tela `/balao`), `client/ClientBubbleCache`, `client/BubbleBackgrounds` |
| Nameplate | `client/NameplateRenderer`, `client/ClientGuiLayers` |
| Medo | `RPEffects` (efeito `rptag:medo`), `FearPayload`, `client/ClientEffects` (HUD com shaders: `registerAboveAll` + véu 208) |
| Lore Zone | `LoreZones` (tick/música), `LoreZoneCommands` (`criar/texto/som/musica/baixar/listar/remover`), `LorezoneMusicPayload` |
| Música custom | `MusicFileQueryPayload`/`ChunkPayload`/`AnswerPayload` (sync servidor→clientes, chunks 30 kB), `MusicSync`, `client/MusicaCustom` |
| Chat/Roll | `ChatCommands` (`/g`), `RollCommands` (`/roll NdM+K`) |

### Recursos — `src/main/resources/`
- `META-INF/neoforge.mods.toml` (versão aqui TAMBÉM)
- `assets/rptag/textures/gui/`: `bubble.png` (**512×256, texel de recheio = 248**), `quadrada.png` (**64×32, texel 255**), `ring.png`, `cartum.png`, `brilho.png`, `dashed.png` (192 px, períodos 8), `stickers_hi.png` (células 8×16), `medo_hd.png` (128, foto inteira + rampa alpha 14 px), `vinheta`, `preview_sky`, `grad_bar`, `linhas`, `dupla`, `gibi`
- `assets/rptag/textures/mob_effect/medo.png` (64, ícone do efeito)
- `assets/rptag/font/stickers.json` + `textures/font/stickers.png` (emojis da fala, código −0xE000)

---

## 4. O que o mod faz hoje (resumo por área)

- **Tag RP**: `/rp` liga/desliga; nome no chat, TAB e na cabeça com moldura.
- **Persona**: título/subtítulo/cor por jogador, sync pra todos.
- **Balões de fala** (o que o jogador escreve vira balão na cabeça):
  - 6 molduras (Clássica, Cartum, Quadrada, Costura, Brilho, Sem borda), cores personalizáveis, adesivo à esquerda + emojis à direita;
  - **3.56**: texto quebra em linhas equilibradas (`MAX_TEXT_WIDTH = 130`) → mais palavras = balão cresce **pra cima**; rabicho **pixel-art em degraus** (14×4 + 7×4) com contorno, igual nas todas as molduras;
  - **3.56.1**: rabicho amostra a MESMA janela de texels da pílula (`bubble.png` 252–260 × 124–132, texel 248) → cor **exata**; Quadrada (texel 255) segue igual;
  - tela `/balao` com prévia fiel (quebra a fala com as mesmas regras — `wrapPrev` — e ajusta o zoom; cor do rabicho via `escalaTexel` 248/255).
- **Medo**: `/effect give <jogador> rptag:medo <s>` — ícone de foto inteira com borda dissolvendo + véu azul-noite 208 que esconde a tela; camada oficial de HUD (`registerAboveAll`), **funciona com shaders**.
- **Lore Zone**: `/lorezone criar <id> <raio> <titulo>` cria esfera de lore; texto, som (preset ou `1x na entrada`) e **música**; `/lorezone baixar <url>` baixa de verdade no servidor (`config/rptag/musicas/`) e **sincroniza pros clientes** (todo mundo ouve).
- **Roll**: `/roll` = 1d20; `/roll 2d6+3`, `/roll +3` = 1d20+3.
- **Chat**: `/g` global; chat local por proximidade (`/rp admin chatlocal`).

---

## 5. Histórico (o que aconteceu até aqui)

>Texto completo por versão: `CHANGELOG.md`. Resumo com foco em decisão:

| Versão | O que marcou |
|---|---|
| 3.3x | base do chat/tag/persona e dos primeiros comandos |
| 3.40–3.42 | polimento da tag e comandos admin |
| 3.49.0 | reorganização das telas do `/balao` (2 colunas adesivo/emojis) |
| 3.50.0 | música custom nas Lore Zones; nuvem do balão (depois removida) |
| 3.51.x | rabicho discreto; Quadrada quadrada ao todo; balão 2 colunas; tela à prova de crash |
| 3.52.0 | som de zona novo (muitos presets) + sliders de volume (Ambiente = `/lorezone som`, Música = `/lorezone musica`) |
| 3.53.0 | ajustes de balão/nametag |
| 3.54.x | medo com **shaders** (HUD `registerAboveAll`); ícone do medo = **foto inteira** (64/128) |
| 3.55.0 | X vermelho na prévia sem tooltip; `/lorezone baixar` REAL + sync v5 (chunks 30 kB); `/lorezone musica <id> 1x <som>`; `/roll +3`; `/balao teste` **removido** (prévia usa a última fala real); ícone do medo com borda dissolvendo; lorezone explicada como ESFERA |
| 3.56.0 | **balões reescritos**: wrap 130 (cresce pra cima), rabicho pixel em degraus p/ todas as molduras, prévia multi-linha, textura `bubble_tail.png` removida |
| 3.56.1 | rabicho na cor **exata** do balão (texel 248); `/rp ajuda` sem a linha amarela do WALKTHROUGH |
| 3.57.0 | **tons de fala** (estudo do mod Fabric "roleplay-speech-bubbles"): `/pensar` novo (itálico + parênteses), `/s` agora vira balão MAIÚSCULO+negrito, `/w` itálico que não funcionava CORRIGIDO; pacote `ChatBubblePayload` ganhou campo `tone`.  O mod Fabric embute telemetria (`creativemode-telemetry`) — jamais replicar |
| 3.58.0 | **rabicho = recorte na silhueta** (dono escolheu a opção B na prévia de 4 designs): textura `tail.png` texel branco + escala 248, contorno único sem emenda, prévia idêntica ao mundo |
| 3.58.1 | rabicho 26×18 saiu GIGANTE na print → **13×9 FIXO (padrão)**, nunca escala com o balão; pilha +12 |
| 3.60.0 | **balão PROCEDURAL**: silhueta única (corpo + rabicho) por geometria (`BubbleGeometry`), sem texturas; contorno exato, sombra macia, gloss, AA; prévia usa a MESMA função; `/rpformato novo\|antigo` |
| 3.63.0 | **sombra do procedural ainda desenhava rabicho** (as prints do dono mostravam o "triângulo bugado" — o `Spec` da sombra não recebia `tail`); título do `/rp ajuda` limpo (`╔══` quebrava na fonte → `» RP Tag — Comandos`) |
| 3.62.0 | **balão FLUTUANTE sem rabicho** (pesquisa: Talk Balloons/TalkBubbles/Vanilla Chat Bubbles — nenhum usa conector; 4 designs de rabicho já haviam sido reprovados pelo dono): `Look.tail=false` (campo novo na geometria) + quads do rabicho removidos do formato antigo; pilha +4 |
| 3.61.0 | **aviso SEM pulso, MUITO maior (3.2x + negrito de tinta, 8s) e na CAMADA OFICIAL de HUD** (sumia com shaders — mesmo mal do medo na 3.54); NOVO `/rp admin avisozona <id> <texto>` (avisa quem está dentro da lorezone); balão COM shaders → formato antigo SOZINHO (`RenderType.gui()` é engolido pelo Iris no mundo; detecta Iris/Oculus por reflexão, cache 1s) |

Commits recentes: `0e93318` (3.56.1) ← `69a211b` (3.56.0) ← `0d3f76e` (3.55.0) ← `928647f` (3.49.0). **Hashes de 3.50–3.54.1 foram perdidos** (regressão do `.git` num turno) — não referenciá-los.

---

## 6. Decisões permanentes & reprovados (NUNCA voltar)

### Como o trabalho é feito
- Push no GitHub **sem credenciais**: commit local sempre; quem faz push é o dono (PAT dele). "Arquivos pro GitHub" = commit + zip das sources + jar via `present_file`.
- Entrega do jar **sempre** com `present_file` (o usuário cobra o download).
- Respostas em PT-BR; testes ANTES de entregar; smoke RCON por ciclo.
- Qualidade visual nível mods famosos; telas 1.21 usam `renderBackground` com fill opaco.

### Visual do balão
-  Faixa/banda no balão;  emoji via fonte na fala;  emoji-tint no mundo;  bandas de fonte;  dashed antigo;  balão-nuvem;  contadores "emojis x/6";  botões Auto/"Minha cor";  ordem antiga dos botões;  card escuro na prévia;  editor baú.
-  **Rabicho triângulo antigo** (`bubble_tail.png` com texels quebrados — reprovado na 3.56) e  quadrado-conector;  degraus pixel-art (3.56–3.57);  **rabicho GRANDE** (26×18 da 3.58.0, reprovado na print).  Rabicho atual = **recorte na silhueta PEQUENO e FIXO** (`tail.png` texel branco, 13×9, contorno único, cor exata).
-  Balão cresce **pra cima** com o texto (linhas equilibradas, nunca tirinha deitada);  recheio cobre o encaixe do rabicho (costura invisível).
- Quadrada = quadrada AO TODO; Sem borda por último em 2 fileiras de 3; tela `/balao` à prova de crash.

### Medo
- Ícone = **FOTO INTEIRA** (carinha + palavra "medo") — nunca recortar/redesenhar; borda dissolvendo em alpha aprovada.
- **COM shaders**: camada oficial de HUD (`registerAboveAll`) + véu 208 — não voltar para `RenderGuiEvent.Post`.
- Mensagem só pra admin; paleta azul-noite; véu que ESCONDE a tela.

### Outros
-  Nuvem no balão (removida a pedido no 3.50 — manter fora).
-  **TELEMETRIA/coleta de dados JAMAIS** (o mod Fabric estudado no 3.57.0 embute `creativemode-telemetry` — jar-in-jar que rastreia sessões/mods; serviu só de referência de mecânica).
- Tons de fala (3.57.0): `tone` 0/1/2 no `ChatBubblePayload`; pensar/sussurro = itálico (fonte vanilla mantém a largura → medida intacta), grito = fake bold (2ª passada 0,75px).
- `/balao teste` **não existe mais** — testar é escrever no chat (a prévia usa a última fala).
- Música baixada fica no **SERVIDOR** e sincroniza pros clientes ("iniciado/concluído"); todo mundo ouve.
- Sons "1x na entrada" da lorezone: opção pedida explicitamente (manter).

---

## 7. Receitas operacionais

### Build pelo GRADLE (caminho dos humanos — o que o amigo usa)
- O projeto é um **ModDevGradle 2.0.146** padrão (Gradle 9.2.1 via wrapper, NF 21.1.249, foojay-resolver).
- `./gradlew build` → jar em `build/libs/rptag-<v>.jar`; `runClient`/`runServer` pra testar.
- **Testado no sandbox até a etapa de descompilação do Minecraft** (wrapper , plugin , downloads , 6 etapas do pipeline ) — a descompilação em si precisa de ~4 GB de RAM e o sandbox tem 2 GB (morre ali, KILL do cgroup, sem log de OOM). Em PC normal passa. NÃO mandar patch de memória pro repo (cache local do sandbox só).
- Guia do dev externo: `COMO-EDITAR.md` (raiz do repo).

### Toolchain do sandbox (o `/var/tmp`some entre sessões!)
```bash
bash /home/user/rp-tag/toolchain.sh   # JDK 21 + nf-binary.jar + cp.txt (tudo automático)
```
- Gera: `/var/tmp/java-21/`, `/var/tmp/nf-binary.jar` (MC 1.21.1 mapeado + NeoForge), `/var/tmp/cp.txt` (classpath completo: nf-binary + **todos** os artifacts do cache NFRT — brigadier, etc — + libs do neoforge).
- Internamente usa NFRT com um `neoform-edit.zip` **INTEIRO** (não só o `config.json` — o NFRT lê `config/joined.tsrg` de dentro!) com jvmargs de decompile `-Xmx1450m -XX:+UseSerialGC`.

### Build
```bash
cd /home/user/rp-tag
find src/main/java -name "*.java" > /var/tmp/sources.txt
export PATH=/var/tmp/java-21/bin:$PATH
rm -rf build/classes build/libs && mkdir -p build/classes
# SEM pipe: só jarifica se exit 0 (senão sai jar STALE)
javac --release 21 -encoding UTF-8 -proc:none -nowarn \
  -cp "$(cat /var/tmp/cp.txt)" -d build/classes @/var/tmp/sources.txt \
  && cp -r src/main/resources/* build/classes/ \
  && (cd build/classes && jar --create --file ../libs/rptag-<ver>.jar .)
cp build/libs/rptag-<ver>.jar /home/user/ /home/user/divulgacao/ /home/user/run/mods/
```

### Smoke (servidor real + RCON)
1. Se `run/libraries/net/minecraft/server/` estiver vazio → baixar o installer `neoforge-21.1.249-installer.jar` e `java -jar ... --installServer .` (~20 s).
2. Subir em background: `JAVA_HOME=/var/tmp/java-21 PATH=/var/tmp/java-21/bin:$PATH ./run.sh` (cwd `run/`).
3. Esperar `Done (` no `run/logs/latest.log`.
4. RCON: `127.0.0.1:25575`, senha `test123` (pacote: struct `<ii` + type 3 no login). Testar `rp ajuda` (ler a resposta INTEIRA — os comandos respondem com paginação longa).
5. `grep -icE "rptag.*(exception|ERROR)" run/logs/latest.log` → tem que ser 0.
6. Parar o servidor no fim. `lorezone criar` por RCON falha ("A player is required") — esperado.

### Git (commit local)
Se o `.git/config` sumir (não vai pro snapshot), recriar:
```ini
[core]
	repositoryformatversion = 0
	filemode = true
	bare = false
	logallrefupdates = true
[remote "origin"]
	url = https://github.com/EduCafilista/rp-tag.git
	fetch = +refs/heads/*:refs/remotes/origin/*
[branch "main"]
	remote = origin
	merge = refs/heads/main
[user]
	name = EduCafilista
	email = EduCafilista@users.noreply.github.com
```

### Geometria do balão (pra mexer sem quebrar)
- `pillW = max(34, textW + pad16 + leftW + rightW)`; `pillH = speechH + 8`; `speechH = 10 × n° de linhas`; `yP1 = 0`, texto acima, topo em `yP0 = yText − 4`.
- `leftW` (adesivo) = 14+6 se tiver; `rightW` (emojis) = n×14 + (n−1)×3 + 6.
- Ordem de desenho (z): sombra −0.20 → contorno −0.15 → **rabicho pixel** (contorno −0.15, recheio −0.12) → recheio da pílula −0.10 (cobre o encaixe) → texto → adesivos.
- **Balão procedural (3.60.0)** — `client/BubbleGeometry.java` (Java puro, SEM dependência do Minecraft: dá pra compilar e testar com `tools/teste-geometria.sh`) gera triângulos coloridos num `Sink`; `client/BubbleSink.java` escreve esses triângulos num `VertexConsumer` de `RenderType.gui()` (quad degenerado, giro normalizado). Silhueta = retângulo de cantos arredondados + rabicho por perfil de largura (concavo, 9 un. de profundidade, FIXO) no MESMO polígono; triangulação por ear-clipping; contorno = faixa entre a silhueta e a silhueta deslocada (miter limitado); AA = anel de alfa 1→0 de 0,22 un.; sombra = silhueta deslocada + anel macio; recheio com degrade vertical; gloss = faixa clara no topo do corpo. Camadas em Z: sombra −0,34 · halo −0,30 · tinta −0,26 · borda −0,22 · recheio −0,18 · AA −0,17 · gloss −0,14 · costura −0,12 (texto 0). O eixo Z do pose cresce com a distância (`zk`) pra camadas não brigarem no depth buffer. O desenho ANTIGO (texturas) continua no código como fallback: `BubbleRenderer.modern`; se o novo lançar exceção o mod volta sozinho pro antigo; manual: `/rpformato novo|antigo`. **(3.61.0) COM SHADERS o jogo usa o formato ANTIGO sozinho** (`usaModern()`: detecta `isShaderPackInUse` na IrisApi por reflexão, cache 1s) — o `RenderType.gui()` do procedural é engolido pelo pipeline do Iris no mundo (balão invisível, print do dono). Quando o pack desliga, o procedural volta.
- Rabicho antigo (3.58.1, só no fallback): `tail.png` 32×24 (texel branco 255, curva de HQ); quad **13×9 FIXO** (border +1px) em `cxT = x0 + pillW/2 + 1`, `ty0 = yP1 − 1.5`; contorno = quad expandido 1px (z −0.15) → recheio (z −0.12, `texel248(fill)` fora da Quadrada) → pílula (z −0.10) cobre o encaixe. Altura da pilha: `+ 12`. **Regra: o rabicho é tamanho padrão e NUNCA escala com o balão.**
- Prévia (menu): `wrapPrev(fala, 110)` com zoom K=2.0 (cai pra 1.5/1.2/0.95 conforme o nº de linhas), `simH = linhas × lineHeight × K + 8`, rabicho via `g.fill` + `escalaTexel` (×248/255 fora da Quadrada).

---

## 8. Pendências / próximas ideias

- Ideia do mod Fabric ainda NÃO portada: tela de composição de fala com botão "Enviar balão" + seletor de tom (aqui o fluxo é escrever no chat — discutir com o dono antes).
- Lembrete: o `WALKTHROUGH.en.md` ainda é o guia antigo de jogador em inglês (legado); se o repositório for público, vale atualizar ou remover.
- Lembrete: o `WALKTHROUGH.en.md` ainda é o guia antigo de jogador em inglês (legado); se o repositório for público, vale atualizar ou remover.

---

## 9. FX de aviso — estilo 9 (SANGUE) = "HEMORRAGIA" (4.3.0, 2026-10-20)

Substitui o "Sangria" (protótipo 3.95, `backup/SangueAvisoFx_Sangria_3.95.java`). O dono reclamou:
"o atual está muito cartoon" — o que fazia cartoon eram as **gotas redondas com brilho branco**
(`GLOSS = 0xFF8C8C`). Regra nova: **nada de gloss branco, nada de elipse perfeita.**

- Arquivo: `client/SangueAvisoFx.java` (~600 linhas, só `AvisoFxUtil`/`Buf`, sem RenderSystem manual).
- Timeline: 0,00–0,30 s **costura** (pele esticando) → 0,30 s **RUPTURA** (clarão + tremor + estalo)
  → ferida abre em 0,35 s com elástico → **escapa sangue até 6 s**.
- Peças: `carne()` (vinheta granulada), `veias()`/`veia()` (capilares recursivos das 4 bordas),
  `costura()`, `ferida()` (rasgo + lábios + estrias + **tendões curvos** + **escorrido em fita** +
  fibras internas), `jatos()` (11 partículas por batida com gravidade), `manchas()` (14 respingos +
  escorridos com **cabeça achatada**), `manchasFrente()` (4 cantos), `cortina()`, `poco()`, `grao()`.
- Coração: `BEATS` 12 batidas acelerando e afrouxando; `batida()` soma `exp(-dt*8.5)` com eco de 0,20 s.
- Paleta: `VOID 0x070001, CRUST 0x240003, DEEP 0x3D0006, BLOOD 0x8E0510, BLOOD_B 0xC4121F,
  FLESH 0x8E1512, FLESH_D 0x4A0806, GLOSS 0xE07070` (só para fio de luz, nunca branco).
- Texto: `S = k*1.9`, `ty = cy - 4*S` (nunca encolhe), mensagem em `cy + 25*k` (regra 3.96.4).
- Verificação: `verificacao/simula_4_3_0_sangue.py` reproduz a MESMA matemática em numpy/PIL e gera
  4 PNGs 1080p + `4_3_0_sangue_timeline.png` (6 quadros) + `4_3_0_sangue_animacao.gif` (6 s, 12 fps).
  **Não é screenshot do jogo** — é prova de geometria/cor.
- Checks: 331 (4 novos em `check.py`, seção 4.3).

---

## 10. Coração canônico 3D + `/rp admin vida` (4.4.0, 2026-10-20)

**Comando** (`RPCommands.java`): nó `vida` dentro de `/rp admin`, métodos
`vidaTirar/vidaDar/vidaDefinir/vidaVer`. A CONTA fica em `VidaCanonica.java`
(classe pura, testada em `VidaCanonicaTest`), justamente pra não depender de servidor.
`tirar` = `max(0, antes - qtd)`, `dar` = `min(5, antes + qtd)`, `definir` = clamp 0..5.
O aviso usa `ScreenTextPayload(msg, 8, 4)` (estilo 4 = morte) + trovão; com 0 vidas
entra `FearPayload(30)` + efeito `rptag:medo`. **Nunca mexe em `CanonLife.FREEZES`** —
quem congela é a morte, não o admin.

**Coração** (`client/CanonCoracaoRenderer.java`):
- `MASCARA` 15×13 (String[]) → `dist` (chamfer 8-vizinhos) → `SOLIDO[x][y][z]` com
  `NY = 13` e `FATOR_PROF = 1.35` (inflação: miolo gordo, borda fina).
- `construirFaces()` roda no `static{}` e grava só as faces expostas em `VX/VY/VZ/VC`
  (630 quads) — o render é um loop simples, sem alocar.
- `cor(x,y,z)`: gradiente TOPO `0xF75C7C` → MEIO `0xDC183E` → PONTA `0x840826`,
  miolo mais vivo, especular quente `0.26` no lóbulo esquerdo (nunca branco).
- **Giro:** `System.nanoTime()/1e9 * 24°/s` (~15 s por volta) + fase por posição
  (`(x*37 + z*91) & 1023`), batida tum-tum (`exp(-bt*18)` + eco em 0,22 s), `dy` flutua,
  `Axis.ZP` 5°, `Axis.XP` 6°. `RenderType.lightning()` desenhado frente+verso.
- Verificação: `verificacao/simula_4_4_0_coracao.py` lê a `MASCARA` do próprio Java.


### 4.7.2 — o literal que faltava (bug do `egipicio`)

`avisar()` tinha `case "egipicio"`, o `VideoFx` existia, os 144 quadros estavam no
jar — e mesmo assim `/rp admin avisar @s egipicio oi` saía **clássico**. Motivo:
a árvore do Brigadier não tinha `Commands.literal("egipicio")`, então a palavra
era consumida como `texto` do comando clássico (ou "Unknown or incomplete command").
Corrigido com **dois** literais (`egipicio` e `egipcio`) após o bloco do `real`.
**Regra nova:** todo estilo do `switch` de `avisar()` precisa de literal — o check
`4.7.2` do `check.py` varre só o corpo de `avisar()` (fatia até o próximo
`    private static `, senão pega `case "pvp"` de outro método) e falha se faltar.
Prova de runtime: servidor NeoForge 21.1.249 limpo + RCON →
`/rp admin avisar <jogadores> (<texto>|pesadelo|...|real|egipicio|egipcio)`.
`egipicio` sem texto dá "Unknown or incomplete command" igual ao `real`
(antes seria aceito como texto). 415 checks · 43 testes · javac limpo.

**Publicado:** release **421386479** (v4.7.2), asset **667716743**
`rptag-4.7.2.jar` = **104.562.338 B**, sha256
`a380cc5e18429a02205a6a2bd26298fc717340d7d20966747637f548e3393a6e`
(download + `cmp` idêntico). Commit `546ee75`. Apagados: release 420651626 e tag
`v4.7.1`. Vivas: `v4.7.2` e `portais-v1.3.2`.

### 4.7.3 — tab-complete duplicado

Registrar `egipicio` **e** `egipcio` como literais fazia o jogo listar as duas
sugestões. Regra: **um literal por estilo**. A grafia alternativa do dono
(`egipcio`) é tratada por *fallback* dentro de `avisar()`: se `style == 0` e a
primeira palavra do texto for `egipcio`, vira estilo 12 e o resto é mensagem.
⚠️ Ao reescrever `style` dentro do método, o lambda do `sendSuccess` quebra
("must be final or effectively final") → guardar `final int estiloFinal = style;`.
Check 4.7.3 garante: nenhum literal repetido, `egipcio` fora da árvore, fallback
presente. 418 checks · 43 testes.

**Publicado 4.7.3:** release **421410429**, asset **667804155**
`rptag-4.7.3.jar` = **104.562.425 B**, sha256
`e07e5331b74fb0656f03b83025e7fb6d67d31fef144b5b5bddb578e172d56438`
(download + `cmp` idêntico). Commit `88054d4`. Apagados: release 421386479 e tag
`v4.7.2`. Vivas: `v4.7.3` e `portais-v1.3.2`.

### 4.7.4 — separar FX quebrado de comando que nao chega

O dono mandou um print com `[Server: Showing new subtitle for X]` e
`[Server: killed 2 entities]`: **as duas linhas sao VANILLA** (feedback de
`/title ... subtitle` e de `/kill`), nao do mod. `avisar()` nunca manda
subtitle — o unico lugar do mod que manda title/subtitle vanilla e
`LoreZones.show()` (zona SEM aviso configurado).
Para diagnosticar sem depender de servidor: **`/rpfx teste <estilo> [texto]`**
(comando de CLIENTE em `FxComando`), que chama `ClientEffects.startScreenText`
direto. Se aparece no teste e nao no `/rp admin avisar` → problema no
comando/rede; se nao aparece em nenhum → cliente (config/video).
421 checks · 43 testes.

**Publicado 4.7.4:** release **422496016**, asset **671176457**
`rptag-4.7.4.jar` = **104.563.839 B**, sha256
`e0de85cc9288ec86f8fd7961cd9c452807b795dc5be125a44a4941896e5e846b`
(download + `cmp` idêntico). Commit `6726530`. Apagados: release 421410429 e tag
`v4.7.3`. Vivas: `v4.7.4` e `portais-v1.3.2`.

### 4.7.5 — trocar o video de um aviso por uma animacao do dono

Pedido recorrente: "quero que a animacao que eu mandei seja a nova do avisar X".
Fluxo: `python3 arte/importar_video.py <id> <arquivo> [--inicio --fim --fps --max
--ajuste cover|contain|stretch]`. Usa **ffmpeg do imageio-ffmpeg**
(`pip install imageio-ffmpeg`; nao ha ffmpeg no PATH do sandbox), extrai
1280x720 cover, renumera 0000..N-1 (o ffmpeg comeca em 0001!), escreve
`arte/render/<id>/marcadores.json` com `fps` + `fade` e chama o exportar.py.
`exportar.py` agora le `fps` do marcadores.json (antes cravava 24 e a duracao
saia errada). Testado com `previa/egipcio.gif` (36-38 quadros, 1,8 MB).

### 4.7.6 — o egipcio virou a animacao de CANVAS do dono

O dono manda o aviso como HTML de canvas (`<canvas id="c" width="960" height="540">`
+ `draw(t,msg,red)` deterministico, `DUR=7.5`, `k=Math.min(W,H)/240`).
Receita: `node arte/render_canvas.js <html> arte/render/egipcio_novo --fps 24`
— puppeteer + Chrome (precisa `sudo apt-get install libnspr4 libnss3 libatk1.0-0
libatk-bridge2.0-0 libcups2 libxkbcommon0 libasound2 libxdamage1 libatspi2.0-0`;
node_modules fica em /var/tmp/chrome). O script troca `W=960,H=540` por
1280x720 e `k=2.25` por `k=3`, desliga a placa do canvas (`__SEM_PLACA`) e
expoe `window.__render(t)`.
Depois: `marcadores.json` com fps/quadros/fade -> `python3 arte/exportar.py
egipcio` -> **180 quadros, 22,9 MB**. `check.py`: o check 4.7 agora exige **180**.
No Java (`EgipcioAvisoFx`): `DUR = 7.5f`, placa/texto posicionados pela area
16:9 desenhada (`FxTimeline.cobrir`) e, no modo video, `cy = h*0.783` (o lugar
do cartucho do canvas) com placa mais baixa (`4.6*S + 9*k`).
Previa sem abrir o jogo: `python3 arte/prever_egipcio.py "msg"`.

**Publicado 4.7.6:** release **422690644**, asset **672092843**
`rptag-4.7.6.jar` = **115.739.417 B**, sha256
`49be53e8af295ef05e352a4ad8e7f74492899188bb1a674301960231a8e111f2`
(download + `cmp` idêntico). Commit `7884933`. Apagados: release 422496016 e tag
`v4.7.4`. Vivas: `v4.7.6` e `portais-v1.3.2`.

### 4.7.7 — trilha do egipcio (sintetizada, sincronizada)

`arte/audio_egipcio.py` gera `assets/rptag/sounds/egipcio_aviso.ogg` (7,6 s) e
uma previa em `/home/user/previa/egipcio_som.mp3`. Tudo sintetizado em numpy:
ney (seno+3o harmonico+sopro), qanun (Karplus-Strong), tambor doum/tek, sistro
(FM), coro (3 vozes + formantes), impacto (sub+ruido+metal). Escala HIJAZ em Re.
A linha do tempo casa com a animacao: riser -> **impacto no clarao (1,0 s)** ->
whoosh das asas (1,5) -> tambor nas batidas (1,6/2,8/4,0/5,2/6,4) -> 2o golpe no
cartucho (2,45) -> sino (3,4) -> golpe final (5,62).
No Java: `SOM_EGIPCIO` em RPTagMod + `sounds.json`; `sons()` dispara uma vez
(`tentouTrilha`/`trilhaTocou`) e cai nos note blocks so como reserva (reduzido).
`pararTrilha()` usa `SoundManager.stop(ResourceLocation, SoundSource.MASTER)`.
⚠️ `SoundManager` tem `stop(SoundInstance)`, `stop(ResourceLocation, SoundSource)`
e `stop()` — o de ResourceLocation e o que funciona para cortar `forUI`.

**Publicado 4.7.7:** release **422842215**, asset **672728963**
`rptag-4.7.7.jar` = **115.842.924 B**, sha256
`35f3e90a35039c94b8ea1de407e8391961286b32ebd523946b3554bd8972b30d`
(download + `cmp` idêntico). Commit `ec56358`. Apagados: release 422690644 e tag
`v4.7.6`. Vivas: `v4.7.7` e `portais-v1.3.2`.

### 4.7.8 — trilha = audio do dono (corte de 7,5 s)

O dono manda um MP3 longo e quer ele como som do aviso. Receita:
1. `python3 arte/analisa_audio.py <mp3> --janela 7.5` -> mostra RMS por 0,5 s,
   os drops e **sugere o inicio do melhor trecho** (energia + impacto no 1o s).
2. `python3 arte/importar_audio.py <mp3> --inicio S --dur 7.5 --id egipcio_aviso
   --pico -1.2 --previa` -> `assets/rptag/sounds/<id>.ogg` (fades + normalizacao).
   O corte tem de por o **impacto em ~1,0 s** (o clarao da animacao).
3. Guardar a fonte em `arte/fontes/` para poder recortar depois.
No caso do egipcio: fonte de 60 s, drop em 35,0 s -> corte 33,95..41,45.
⚠️ `wave` do Python nao le `pcm_f32le` -> o importador usa **pcm_s16le**.

**Publicado 4.7.8:** release **422998464**, asset **673386045**
`rptag-4.7.8.jar` = **115.814.423 B**, sha256
`1ac04652c99338da3ecf9c406642cd9f28d1cf330adf3c37e76630210236870b`
(download + `cmp` idêntico). Commit `aa1e00b`. Apagados: release 422842215 e tag
`v4.7.7`. Vivas: `v4.7.8` e `portais-v1.3.2`. Fonte: `arte/fontes/egipcio_origem.mp3`
(60 s) -> corte 33,95..41,45 s -> ogg 75.581 B.

### 4.8.0 — TABLET DA FICHA (tudo dentro do rp-tag)

Regra do dono: **nada de mod separado** — a ficha mora no `rp-tag`.

**Mapa dos arquivos**
- `dev/rptag/FichaRp.java` — dados + NBT + os 8 capítulos e os 6 eixos
- `dev/rptag/FichaCodec.java` — como a ficha viaja pela rede (texto com limite,
  persona em 6 bytes)
- `dev/rptag/FichaOpenPayload.java` (`rptag:ficha_abrir`, S2C) e
  `FichaSavePayload.java` (`rptag:ficha_salvar`, C2S)
- `dev/rptag/FichaServer.java` — abrir/ver/salvar/entregar/resetar; ao salvar,
  nome+idade+(ofício · origem) viram `Persona` e o jogador entra em RP
- `dev/rptag/TabletItem.java` — direito abre, shift+direito mostra no chat
- `dev/rptag/FichaLivro.java` — a ficha no chat (sem `╔══`)
- `dev/rptag/FichaCommands.java` — `/ficha [ver|livro|dar|reset|lista|editar]`
- `client/FichaScreen.java` — a tela (8 capítulos, animação, personagem)
- `client/FichaWidgets.java` — botão, barra de personalidade, campo multi-linha
- `client/FichaFundo.java` — galáxia + **buraco negro** + **disco mágico**
- `RPWorldData` — `Map<UUID,FichaRp> fichas` e NBT `"Fichas"`

**Decisões que não voltam atrás**
- **sem pergunta de RAÇA**; "de onde você veio" (ORIGEM) ocupa o lugar
- PERSONALIDADE = 6 barras **bipolares 0..100** com ímã no centro (50)
- o personagem na tela é o **próprio jogador** (`minecraft.player`), não um NPC
- animação por pergunta = `p.swing(MAIN_HAND/OFF_HAND)` + giro da cabeça
  (`renderEntityInInventoryFollowsAngle`); **não depende do Emotecraft** — se ele
  estiver no servidor, as emotes normais continuam funcionando por fora
- salva **a cada capítulo** (`salvar(false)`), então fechar no meio não perde nada
- `registrar("6")` — o protocolo subiu por causa dos dois canais novos
- textura do item 16×16 (`textures/item/tablet.png`), modelo `item/generated`

**Layout (referência = prints do dono)**
`CAPÍTULO n / TÍTULO` à esquerda + "Sua história começa aqui" à direita; pergunta
centralizada; personagem à esquerda sobre o disco; conteúdo à direita; botões
Voltar/Continuar; quadradinhos de progresso; `ESC · Sair` / `ENTER · Continuar`.
Clicar num quadradinho pula para o capítulo.

⚠️ Regressão de snapshot: já aconteceu **10×**. Antes de qualquer `reset`, copiar
o trabalho novo para `/home/user/wNNN/`; depois `git remote add origin
https://<PAT>@github.com/...` (o `.git/config` NÃO sobrevive ao snapshot),
`git fetch origin main`, `git reset --hard FETCH_HEAD` e copiar de volta.

**Publicado 4.8.0:** release **423042370**, asset **673582062**
`rptag-4.8.0.jar` = **115.856.094 B**, sha256
`022bcdcf62a4473b1308783d0d7ac72333792ceb953f5b2ec936712e46bd8307`
(download + `cmp` idêntico; o asset antigo 673579870 foi trocado depois do ajuste
de layout). Commits `b72d141` + `81c62bf`. Apagados: release 422998464 e tag
`v4.7.8`. Vivas: `v4.8.0` e `portais-v1.3.2`.
Testes: 458 PASS / 0 FAIL · 43/43 JUnit · boot limpo no NeoForge 21.1.249
(`Done (0.642s)`, `/ficha lista` responde).

---

*Última atualização: ciclo 4.8.0 (2026-10-24) — Tablet da Ficha dentro do rp-tag.*
