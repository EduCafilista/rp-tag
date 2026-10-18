# WALKTHROUGH — Portais da Lore (memória do projeto)

> Editado no fim de cada ciclo. O mod é SEPARADO do rp-tag (pedido explícito:
> "faça outro mod, nao quero no mesmo mod").

## Estado atual
- **v1.3.2** — SOM NOVO MISTICO/INSANO (user: "nao gostei do som atual,
  muda pra algo mais mistico e mais insano, algo legal pra portal").
  Re-sintetizado do ZERO (2.6 s, stereo, peak 0.55 — volume mantido do
  1.3.1): (1) crescendo REVERSO 0->0.75 s (sweep 120->740 Hz + ar);
  (2) BOOM sub 62->34 Hz + SINO DE TEMPLO (parciais inarmonicos 1/2/
  2.76/5.4 x 220 Hz); (3) PAD mistico La menor add9 (6 vozes, vibrato
  4.5-6 Hz, detune por voz) florando apos o boom; (4) HARPA ascendente
  8 notas (A4..A6) pan alternado L/R; (5) ar etereo com tremor lento.
  Mesmo evento portal_entrada (chegada segue pitch 1.25). check
  134/134; smoke verde 0 erros. AGUARDANDO TESTE DO USER.
- **v1.3.1** — PAINEL VISIVEL + SOM NA METADE DO VOLUME (user mandou
  print do 1.3.0: a galaxia era quase invisivel — so estrelas soltas
  sobre o CEU normal, "ficou esquisito"; "som ta muito alto"). CAUSA:
  o painel gradiente tinha alpha 40 (aditivo quase nao soma em fundo
  claro de dia). FIX: painel alpha 40 -> 118 com cores mais ricas
  (indigo/violeta/purpura) = a janela agora ENCHE a abertura com espaco
  profundo; nebulosas ~50% mais presentes; nucleo mais presente e SEM
  os riscos de cruz no MOLDE (flare fora da galaxia — liam como riscos
  na tela). SOM: ogg re-sintetizado com peak 0.55 (era 0.85) + volumes
  de playSound: entrada 1.0 -> 0.55, chegada 0.9 -> 0.5, apoio
  PORTAL_TRIGGER 0.5 -> 0.25, enderman chegada 0.8 -> 0.45, carga
  PORTAL_AMBIENT 0.7 -> 0.35. check 134/134. AGUARDANDO TESTE.
- **v1.3.0** — ANIMACAO DE ENTRADA COMPLETA + SOM PROPRIO (user: "faca
  uma animacao pra quando o player entrar, faca um som pra ter quando
  entrar e arrume o resto"). (1) SOM NOVO: sounds/portal_entrada.ogg
  SYNTHESIZADO no sandbox (numpy+soundfile: sweep 95->400Hz + drone
  grave + ar + 6 pings cristalinos, stereo, 1.5 s, 22 KB); evento
  SOM_PORTAL_ENTRADA registrado (DeferredRegister<SoundEvent> +
  createVariableRangeEvent, sounds.json). Toca na ORIGEM (pitch 1.0 +
  PORTAL_TRIGGER de apoio 0.5/1.4) e no DESTINO (0.9/1.25) + enderman.
  (2) ANIMACAO DE CARGA: carregar() no tick — enquanto o player esta
  dentro (10 ticks de carga), 4 particulas PORTAL giram e CONVERGEM
  nele (raio 1.15->0, altura sobe), REVERSE_PORTAL no final da carga e
  PORTAL_AMBIENT grave no primeiro tick; no teleporte mantem onda no
  disco (pulso renderPulso) + rajadas origem/destino. check 131/131.
  smoke verde, 0 erros. AGUARDANDO TESTE DO USER.
- **v1.2.1** — JANELA ARRUMADA (user mandou print do 1.2.0 e disse:
  "arrume, nao quero que seja um circulo, quero que seja arrumado, mas
  essa textura de estrelas ficou legal"). REMOVIDO do MOLDE: aneis do
  halo, bordas circulares (o arco branco da print), bracos espirais e o
  anel de choque do pulso (faixaR DELETADA) — tudo que le como circulo.
  NOVO: lequeGrad = painel supereliptico com gradiente VERTICAL por
  segmento (indigo topo -> violeta meio -> purpura base, alpha 40)
  preenchendo a abertura inteira — visual de "janela pro espaco",
  arrumado. MANTIDO (aprovado): estrela4 de 4 pontas (26), nebulosas
  blob, cometas-rastro, nucleo branco pequeno (lequeR), animacao de
  travessia (agora = flash da janela inteira + nucleo, sem anel).
  check 126/126 (inclui guard "galaxia sem faixaV/bracoV"). AGUARDANDO
  TESTE DO USER.
- **v1.2.0** — ANIMACAO DE TRAVESSIA + SOME-AO-QUEBRAR + JANELA
  SUPERELIPSE (user mandou PRINT do portal ativo na moldura retangular
  dele e pediu 3 coisas: animacao ao entrar / portal nao some quando
  quebra / qualidade "ainda esquisito"). A print mostrou: estrelas
  QUADRADAS grandes (lidas como bug), elipse pura deixando CEU nos
  cantos da abertura. FIX/TRABALHO:
  (1) ANIMACAO: BE ganhou pulsoViagem/renderPulso (NBT "pul",
  syncCliente); no teleporte o tick dispara dispararPulso() na ORIGEM e
  no DESTINO -> renderer desenha onda de choque (anel expandindo ease-out
  ~1,1 s, faixaR) + flash do nucleo; particulas: ORIGEM 60 PORTAL
  (succao), DESTINO 50 REVERSE_PORTAL (materializacao) + som ender.
  (2) SOME-AO-QUEBRAR: revalidarVisualCliente() — o RENDERER revalida o
  flood-fill no proprio CLIENTE a cada 15 ticks (stagger por hashCode):
  portal some/reativa NA HORA sem depender da rede, e editar a moldura
  reajusta o disco AO VIVO. (3) QUALIDADE: FORMA_E 0.667 (superelipse
  n=3) nas primitivas lequeV/faixaV/bracoV/estrelas/nebulosas/cometas —
  a galaxia agora e uma JANELA que acompanha a abertura; nucleo REDONDO
  (lequeR) pulsa; estrelas = estrela4 (cruz de 4 pontas, fim dos
  quadrados); halo 8 aneis finos nao sobrepostos; braços 20 segs largura
  0.09. check 123/123; smoke: ativa->quebra->desativa->reergue->REATIVA,
  0 erros. AGUARDANDO TESTE DO USER.
- **v1.1.2** — FIX DEFINITIVO DO BRILHO ESTOURADO + EPICO COM ORCAMENTO
  (user respondeu ao formulario: bug = "brilho estourado/lavado"; estilo
  pedido = "mais epico/detalhado"). CAUSA RAIZ: blending ADITIVO SOMA as
  camadas por pixel — a paleta 1.1.0/1.1.1 empilhava alphas 140-235
  (centro passava de ~450 = branco lavado). FIX DUPLO:
  (1) VOLTOU pro RenderType.lightning() vanilla (caminho PROVADO no
  cliente do user desde 1.0.x; o RenderType.create custom da 1.1.1 era
  tipo desconhecido do Iris = risco de blending errado);
  (2) PALETA RECALIBRADA com "orcamento aditivo": aneis do halo NAO
  sobrepostos (cada pixel pega 1 anel), camadas fora do nucleo somam
  <= ~150 de alpha; nucleo branco-quente INTENCIONAL (referencia) mas
  zoneado (55/120/210). Epic: 3 bracos-filamento, 7 nebulosas em 3
  cores (violeta/magenta/ciano), 26 estrelas (branca/ciana/dourada),
  3 cometas com rastro, cruz discreta, borda definida (95/55).
  quad4 = DUPLO-EMITE (cull mostra 1 copia por lado — provado 1.0.x).
  check 122/122. AGUARDANDO TESTE DO USER (com shaders).
- **v1.1.1** — FIX DO "BUGADO" + DESIGN MACIO (user: "o portal ficou
  bugado, melhore o design" — sem print). Diagnostico: (a) o duplo-lado
  da 1.1.0 (dupla emissao de cada quad) com SHADERS/Iris pode desenhar as
  DUAS copias -> brilho aditivo DOBRADO/estourado; (b) as formas novas
  tinham BORDA DURA (losangos de nebulosa, cruz gigante, cometa-barra) —
  parecem glitch. FIX: **RenderType PROPRIO "loreportals_galaxia"** via
  RenderType.create(...) — igual lightning mas **NO_CULL** (as duas faces
  desenham sempre; identico dos 2 lados por construcao) e **COLOR_WRITE**
  (sem escrever profundidade: particulas nao somem atras do disco) — os
  shards sao PUBLICOS no NF 1.21.1 (javap provou). quad4 voltou a
  emissao UNICA. Design: halo em 6 aneis finos degrader, nucleo em 3
  discos concentricos pulsantes + cruz discreta (0.17 rx), nebulosas =
  lequeBlob (disco radial 10 segs, 2 camadas), cometas = rastro (fita
  que afina). check 121/121. AGUARDANDO TESTE DO USER (com shaders!).
- **v1.1.0** — VISUAL "CONCEITO" + DUPLO-LADO (user mandou uma arte de
  portal arco com galaxia: nucleo branco brilhante com cruz de luz, halo
  ciano, bracos violeta->magenta, nebulosas, estrelas, cometas — e
  reclamou: "portal de um lado bonito, do outro nem tanto"). CAUSA PROVADA
  EM BYTECODE: RenderType.lightning() TEM CULL ativo (o
  CompositeStateBuilder comeca com RenderStateShard.CULL e lightning()
  nunca chama setCullState — o comentario antigo "sem cull" era FALSO).
  FIX: quad4 agora emite CADA quad nas DUAS ordens (frente+verso) — o
  cull corta sempre exatamente UMA copia: lado de tras IDENTICO, sem
  brilho dobrado (aditiva). Galaxia MOLDE redesenhada (halo em faixas
  degradé, nucleo pulsante + flare cruz, 3 bracos recoloridos, 5
  nebulosas, 20 estrelas, 2 cometas ciclicos); oval PAR ganhou a mesma
  assinatura (halo+nucleo+flare, cores novas). check 119/119.
- **v1.0.9** — FORMATO FLEXIVEL + GATILHO RAIO 5 (user testou 1.0.7: "o
  portal nao aparece mesmo apos ativado" + print: ele constroi MOLDURA
  RETANGULAR PROPRIA ~9x7 com core embutido na base central — a validacao
  do ANEL 5x5 EXATO nunca ativava esse formato). FIX: geometriaDoFormato()
  = fast-path anel classico OU flood-fill BFS do core: qualquer formato
  FECHADO de molduras ativa (abertura de qualquer tamanho, limites:
  du em [-4,4], dv ate 4, <=40 celulas vazias); o DISCO se molda a
  abertura (rx/ry/cy/centroDu medidos, NBT "rx","ry","cy","cdu", disco
  elastico no renderer + deteccao no tick leem a geometria real).
  1.0.9: o GATILHO "coloco/quebro moldura" procurava nucleo num raio 4 —
  no formato do user o TOPO fica a dv=5 → nunca acordava. Raio 4 → 5.
  Smoke: retangular 9x6 ATIVA (rx 3.4, cy 3.0), anel classico OK,
  quebrar 1 moldura do topo → DESATIVADO (fica dormente, mantem vinculo),
  0 erros. AGUARDANDO TESTE DO USER (galaxia a preencher a abertura).
- **v1.0.7** — CRASH DO CLIENTE RESOLVIDO (crash-report do user, .minecraft/
  crash-reports): NoSuchElementException no PoseStack.pushPose vindo do
  SodiumWorldRenderer.renderBlockEntity. Causa: o setIdentity() da 1.0.5
  LIMPA a pilha de poses (e o 1.0.6 ainda fazia popPose sem pushPose) —
  o Sodium gerencia essa pilha com mixins e explode quando ela esvazia.
  FIX 1.0.7: JEITO VANILLA (EnchantTableRenderer) — dispatcher posiciona,
  render so pushPose/translate(0.5,0,0.5)/mulPose/desenhar/popPose com
  try-finally; SEM setIdentity, SEM camera manual. Cliente do user tem
  Sodium 0.8.13 + Iris + FancyMenu — compativel. Blindagem try/catch do
  1.0.6 mantida (portal invisivel em vez de crash, se algo der errado).
- **v1.0.6** — BLINDAGEM anti-crash: user reportou CRASH do CLIENTE ao
  entrar no sv (tela entrando no mundo, client so com 1.0.5). tryRender do
  dispatcher RELANCA como ReportedException (render de BE quebrado CRASHA
  o jogo) → render() agora e 100% try/catch(Throwable) com guard de
  mc/gameRenderer/camera/level nulos + log 1x. Portal pode ficar invisivel
  em caso de erro, mas o jogo NAO derruba. Aguardando crash-report do user
  pra causa exata (se 1.0.6 nao resolver).
- **v1.0.5** — posicionamento DETERMINISTICO do render: o render NAO confia
  mais no PoseStack que chega (varia entre dispatcher/global/shaders) —
  faz pose.setIdentity() e traduz ELE MESMO (pos-camara via
  Minecraft.gameRenderer.getMainCamera()), depois centro 0.5/0.5 e rotacao.
  Funciona em qualquer caminho de render (secao, global, Iris).
- **v1.0.4** — CAUSA RAIZ do visual bugado ACHADA (print do user: risca no
  ceu; "portal na cabeca" persistia): o renderizador usava o addVertex(x,y,z)
  CRU, que IGNORA o PoseStack → toda a geometria ia pro ESPACO DA CAMERA
  (oval y+1.7 = na cabeca do jogador; galaxia r2 = risca atravessando o ceu;
  por isso o translate(pos) da 1.0.1 "nao resolvia" — nunca teve efeito!).
  FIX: todo vertice agora passa por vc.addVertex(pose, x, y, z) com
  PoseStack.Pose = poseStack.last() repassado pras primitivas (quad4/lequeV/
  faixaV/bracoV/bracoE). Confirmado com javap VertexConsumer (sobrecargas
  addVertex(float...) vs addVertex(PoseStack.Pose, float...)). check.py
  ganhou 2 travas (addVertex(pose, presente; nenhum addVertex cru).
- **v1.0.3** — (1) PAR redesenhado: o "circulo horizontal a y+1.35" ficava
  NA ALTURA DA CABECA do jogador (print do user) → agora e um PORTAL OVAL
  VERTICAL girando (centro y+1.7, rx 0.95, ry 1.05; vidro translucido +
  2 bracos espirais elipticos + borda dupla + faiscas subindo), estilo as
  refs do user. Deteccao ajustada (perto do nucleo, pe y+0.4..y+2.9).
  (2) SEM spam de chat (pedido): removeu "O portal te atravessou pela
  lore..." (tick) e "Portal ativo e conectado a outro portal!" (clique no
  nucleo ativo agora e silencioso). Confirmado no BYTECODE 1.21.1 que o
  LevelRenderer traduz (pos-camara) antes do BER (translate DDD com dsub)
  — desenho local 0..1 esta certo; getRenderBoundingBox e em coords MUNDO.
- **v1.0.2** — CORRECAO VISUAL ("portal bugado em cima da cabeca", print do
  user: risca roxa/branca no ceu): o renderizador fazia pose.translate(pos)
  — mas o dispatcher do BER 1.21 JA traduz pro bloco antes do render() →
  o disco saia a ~2x a posicao do bloco, no ceu. Reescrito: PortalRenderer
  100% em coords LOCAIS (translate(0.5,0,0.5) pra girar no eixo, quad4
  explicito de 4 vertices, bracos com largura perpendicular de verdade —
  os bracos antigos viravam filetes). check.py ganhou 3 travas (sem
  translate(pos.getX, translate 0.5 presente, quad4 >= 8).
- **v1.0.1** — CORRECAO do vinculo PAR: o 1o clique do cristal no nucleo
  SO MARCAVA (nunca registrava no PortalData) → o vincular() falha sempre
  ("portais iguais ou invalidos"). Agora o 1o clique REGISTRA (ativarParSolto).
  Tambem: clicar na MOLDURA acha o nucleo perto (se so houver 1); marcacao
  perdida limpa com msg clara; deteccao do disco PAR cobre os pes em cima do
  nucleo (rel.y >= -0.45); chegada PAR = +1.02 (em cima do nucleo, nao dentro).
- **v1.0.0** — primeiro ciclo completo. Compila (javac, zero erro), check.py
  **89/89**, smoke no servidor: **Done 1.216 s, 0 ERROR/FATAL**, receitas
  carregadas (1293 = 1290 vanilla + 3 do mod), ciclo completo testado via
  RCON: **ATIVADO → DESATIVADO (quebrou moldura) → ATIVADO de novo →
  DESATIVADO+removido do registro (quebrou o núcleo)**.

## O que existe
- `src/main/java/dev/loreportals/` — LorePortalsMod (registros + aba
  "§5Portais da Lore"), PortalCoreBlock (BE), PortalFrameBlock, PortalData
  (SavedData `loreportals_data` no overworld), PortalBlockEntity (anel 11,
  4 orientações), PortalWorldTick (tick 20 = valida anéis; todo tick =
  teleporte após 10 ticks dentro do disco, cooldown 3 s; partículas), 
  CristalVinculoItem (marca → conecta; shift = desvincula), PortalCommands
  (/portal ajuda|info|listar|ir|desvincular|apagar|contar),
  client/PortalRenderer (disco procedural aditivo) + client/ClientInit.
- DOIS MODOS: **PAR** (2 núcleos + cristal; círculo azul horizontal r 0.95
  a y+1.35) e **MOLDE** (anel 5×5 de 11 molduras com núcleo no vão do meio
  de baixo; disco de galáxia roxa vertical r 2.05 a y+2.45).
- Recursos: blockstates/models/lang (pt_br + en_us)/texturas 16×16
  (moldura rúnica, núcleo com espiral, cristal com anel dourado),
  3 receitas, 2 loot tables, mods.toml, pack.mcmeta.
- `check.py` — 89 checks estruturais (roda sem Minecraft).
- `LORE.md` — guia completo do mod (jogador + dev).

## Receita de build (igual rp-tag; Gradle NÃO roda no sandbox)
1. `bash toolchain.sh` (se /var/tmp estiver vazio).
2. `find src/main/java -name "*.java" > /var/tmp/sources.txt`
3. `javac --release 21 -encoding UTF-8 -proc:none -nowarn -cp "$(cat /var/tmp/cp.txt)" -d build/classes @/var/tmp/sources.txt`
4. `cp -r src/main/resources/* build/classes/` ← **SEMPRE depois de editar recursos!**
5. `(cd build/classes && jar --create --file ../libs/portais-da-lore-1.0.0.jar .)`
6. `cp build/libs/...jar /home/user/ /home/user/divulgacao/ /home/user/run/mods/` — **os 3 lugares, apagando o jar antigo antes**.

## Lições deste ciclo
- **PoseStack de BER e TERRITÓRIO DO SODIUM**: NUNCA setIdentity() (limpa
  a deque → NoSuchElementException no proximo pushPose = crash do cliente
  inteiro). Emparelhamento estrito pushPose/popPose com try/finally.
- O crash-report do .minecraft/crash-reports do user e a fonte primaria —
  pedir cedo. lista de mods do user: sodium, iris, fancymenu, konkrete,
  melody, disccord, caelus, music_disc_maker + rptag + loreportals.
- Se o posicionamento do render misteriosamente NAO muda com o fix da pose,
  parar de depender da pilha: setIdentity() + translate(pos-camara) manual.
  camara = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().
- **1.21 VertexConsumer: addVertex(x,y,z) CRU = espaco da camera!** Para
  respeitar translate/rotate do PoseStack TEM que ser
  addVertex(PoseStack.Pose, x, y, z). Sintoma classico: geometria "colada"
  no jogador / risca no ceu, e mover a camera mexe o desenho.
- Diagnostico de render: (1) ler bytecode do LevelRenderer/dispatcher,
  (2) conferir COMO os vertices sao adicionados, (3) comparar com um
  renderer vanilla (EnchantTableRenderer). O bug estava no (2).
- Circle horizontal a ~1.35 do solo = altura de PESCOCO/CABECA do jogador
  do lado — portal de jogador tem que ser OVAL VERTICAL (tipo portal classico).
- Para confirmar modelo de coords de render, javap -c no LevelRenderer/
  BlockEntityRenderDispatcher resolve — nao adivinhar.
- **BER 1.21: o dispatcher JA traduz pro canto do bloco** — render() desenha
  em coords locais 0..1; pose.translate(pos) DUPLICA o deslocamento
  (portal no ceu). getRenderBoundingBox usa coords de MUNDO (contrario!).
- Rotacao do disco tem que ser ao redor do CENTRO: translate(0.5,0,0.5)
  → mulPose → desenhar centrado na origem.
- **v1.0.1**: editar cristal/tick via edit_file disse "success" mas NAO
  aplicou a 1a edicao (de novo!) → python replace com assert resolve.
- Deteccao de disco PAR: pe do jogador em cima do nucleo = centro-1.35+1.0
  → banda tem que cobrir -0.45, senao "em cima" nao conta como dentro.
- **mods.toml**: `versionRange="[1.21.1]"` (range fechado) QUEBRA o load
  ("minecraft [MISSING]" + NoSuchElementException) → usar `"[1.21.1,1.22)"`.
- **Receitas 1.21.1**: key do shaped TEM que ser objeto: `"S": {"item": "..."}`.
- **procurarNucleosDormantes**: raio de busca do núcleo = **5** desde a
  1.0.9 (anel clássico tem topo a dv=4, mas o FORMATO FLEXÍVEL aceita
  molduras até dv=5 — com raio 4 o formato do user nunca acordava).
- **Formato flexível (1.0.8+)**: NÃO voltar à validação do anel 5x5 exato
  — user constrói molduras próprias (retangulares, grandes). O disco se
  molda à abertura via rx/ry/cy/centroDu; renderer e tick leem a geometria
  viva do BE.
- **Smoke via RCON**: `fill`/`setblock` FORA de chunk carregado falham
  INTEIROS ("That position is not loaded") — `forceload add` a área ANTES;
 .portal listar lista dormentes também (desativar != remover registro).
- **SoundEvents 1.21.1**: sem prefixo ENTITY_ (é `ENDERMAN_TELEPORT`).
- **User le ANEL/ARCO como "circulo bugado"**:
- **Painel de fundo translucido some DIA claro**: em blending aditivo,
  alpha baixo sobre ceu azul = invisivel. Fundo da janela precisa
  alpha ~118 pra ler como "espaco profundo" mesmo de dia (nao estoura:
  sao cores ESCURAS — o estouro vem de camadas claras empilhadas).
- **Riscos finos (flare/cruz) em nucleo pequeno leem como defeito de
  tela** — no MOLDE, nucleo = so discos radiais macios.
- **Som custom do mod**: — RECEITA DO SOM MISTICO (aprovado em
  estrutura): reverso-swell -> boom sub + sino inarmonico -> pad coral
  (menor add9, vibrato/detune) -> harpa glissando pan-alternada; peak
  0.55. O user definiu por PALAVRAS-CHAVE ("mistico", "insano"): misticos
  = coral/sino/harpa; insano = reverso-swell + boom sub. gerar .ogg no sandbox com numpy+soundfile
  (format='OGG', subtype='VORBIS' — funciona!); registrar
  DeferredRegister.create(Registries.SOUND_EVENT, MODID) +
  SoundEvent.createVariableRangeEvent + sounds.json
  {"nome": {"sounds": [{"name": "modid:nome"}]}}; playSound(null, pos,
  SOM.get(), SoundSource.BLOCKS, vol, pitch). Ler(RES, ...) no check.py
  parte de src/main/resources/ — incluir assets/loreportals/ no caminho. no MOLDE nao usar faixas
  radiais, espirais nem bordas circulares — o painel inteiro com
  gradiente (lequeGrad, cor por segmento no leque) e a leitura "arrumada".
  Estrelas de 4 pontas foram APROVADAS explicitamente — nao remover.
- **Sync de BE + rede NAO sao confiaveis pra visual**: padrao da casa =
  REVALIDAR no cliente (renderer chama revalidarVisualCliente()
  periodicamente) + syncCliente/sendBlockUpdated como reforco. Pulso de
  travessia e o unico dado que so existe via rede (NBT "pul").
- **Elipse pura em abertura retangular = "esquisito"**: usar superelipse
  (su(): sgn(v)*|v|^(2/n), n=3) nas primitivas — a galaxia acompanha a
  forma da moldura; nucleo redondo por cima (lequeR).
- **Estrela/brilho como QUAD quadrado lê como bug** — sempre cruz de 4
  pontas (estrela4) com ponto central.
- **Brilho em blending aditivo = ORCAMENTO por pixel**: as camadas SOMAM;
  paleta com alphas 140-235 empilhados vira branco lavado ("estourado").
  Regra: aneis NAO sobrepostos (1 camada por pixel), max ~150 fora do
  nucleo; branco alto so no centro (pequeno, intencional). A "cruz"/
  formas duras grandes tambem leem como glitch — formas radiais macias.
- **RenderType custom (RenderType.create) no BER = RISCO com Iris**: tipo
  desconhecido pode perder o blending do pack. Preferir
  RenderType.lightning() vanilla + quad4 DUPLO-EMITE (o cull ativo do
  lightning mostra 1 copia por lado de visao — provado: o user via so a
  frente na 1.0.x). Shards publicos (NO_CULL etc.) ficam de volta caso
  um dia precise.
- **RenderType PROPRIO**: `RenderType.create(nome, POSITION_COLOR, QUADS,
  1536, false, true, CompositeState.builder()...createCompositeState(false))`
  funciona no NF 1.21.1 e os shards (NO_CULL, COLOR_WRITE,
  RENDERTYPE_LIGHTNING_SHADER, LIGHTNING_TRANSPARENCY, WEATHER_TARGET) sao
  **public static final** em RenderStateShard (javap provou) — nao precisa
  subclass/reflection. Para "visivel dos dois lados": NO_CULL no type >
  dupla emissao (que dobra brilho com shaders).
- **RenderType.lightning TEM CULL** (javap provou: builder default
  `cullState = CULL`, lightning() não chama setCullState) → geometria
  plana precisa DUPLO-LADO no helper (frente+verso) ou some por trás.
- **Probe de render headless NÃO ROLA**: RenderType.<clinit> puxa
  Bootstrap → precisa FML. Para decidir estado interno de RenderType,
  usar `javap -c -p` no bytecode direto.
- **Fatjars no cp.txt** (installertools-*-fatjar) embutem Guava VELHA e
  quebram probe/reflection em runtime (`buildOrThrow` NoSuchMethodError)
  → excluir fatjars do classpath de testes.
- **BER**: evento certo é `EntityRenderersEvent.RegisterRenderers` (NÃO
  existe RegisterRenderers solto no NF 21.1); `getRenderBoundingBox` vem de
  `IBlockEntityRendererExtension` (override ok).
- **computeIfAbsent** do SavedData 1.21.1: `new SavedData.Factory<>(ctor, load)`.
- `RenderType.lightning()` = POSITION_COLOR aditivo SEM cull — bom pra
  discos de portal (funciona com shaders, igual lição de gui() do rp-tag).
- `libraries/net/minecraft` (server srg) some do snapshot → installer
  `--installServer /home/user/run` reinstala (~30 s).
- Jar velho em run/mods engole o novo → **sempre apagar antes de copiar**.

## Próximos passos possíveis (NÃO fazer sem pedido)
- Teste visual do disco no cliente (renderer só roda com jogador).
- Som ambiente configurável, portais com cor por bioma, "portal key" por
  jogador, integração com /warp.
