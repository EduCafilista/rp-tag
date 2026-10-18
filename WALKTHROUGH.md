# 🧭 WALKTHROUGH — Memória de trabalho do RP Tag

> **Este documento é o diário de bordo do projeto.** Ele existe para que qualquer
> pessoa (ou assistente) que continuar este trabalho saiba **o que já foi feito,
> como as coisas funcionam, o que NÃO fazer e como construir/testar/entregar** —
> sem se perder e sem refazer o que já passou por testes.
>
> 📌 **Regra: atualizar este documento NO FIM DE CADA CICLO** (junto com o
> CHANGELOG). O guia de *jogador* fica no `README.md` e no `COMO-USAR.md`;
> o registro detalhado por versão fica no `CHANGELOG.md`.

---

## 1. Estado atual

| Item | Valor |
|---|---|
| Versão | **3.84.0** (2026-10-18) |
| Commit | (ver `git log`) |
| Jar | `rptag-3.84.0.jar` (entregue) |
| Checks | `check.py` → **204 PASS / 0 FAIL** (3.84.0: CORAÇÃO CANÔNICO 3D) |
| Smoke | Servidor NeoForge 21.1.249 real: Done ~0,5s, RCON OK, 0 erros do mod |
| Alvo | **Minecraft 1.21.1 + NeoForge 21.1.249**, Java 21 |
| Repo local | `/home/user/rp-tag` (commit local por ciclo; sem credenciais de push) |

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

**3.77.0 (2026-10-17)** — 2 prints de referência ("❀ LORE OFF ❀ /
❀ HARD-RP ON | VIDAS CANNON OFF ❀" vermelho e "❀ LORE ON ❀ / ...ON ❀"
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
0x2A0505/0x8B1616/0xC23030. TEXTOS LITERAIS dos exemplos: "❀ LORE
ON ❀"/"❀ HARD-RP ON | VIDAS CANNON ON ❀" (\u2740) e OFF. Death ritual
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
ladeiam o título (tx0-16 e tx0+w+5, py+7); o "✦ ... ✦" do título saiu
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
dica ENCURTADA p/ "✎ Digitacao LIGADA — /balao typewriter off";
(2) FALA do chat agora trunca com "…" (falaMostrada = corta(fala,
PANEL_W-24-width("<Você> ")-width(sufixo))) — antes falas compridas
vazavam; (3) X vermelho dos slots de extras DENTRO da caixinha
(12..20, 1..9 — o antigo 10..20/-2..8 vazava 2px acima do slot e
cobria o emoji; checks 122/157 mudaram pro marcador "X DENTRO do
slot"); (4) tag "✦ Previa no mundo" SUBIU (cardH-21..-11) pra sair de
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
interno + cantinhos no painel, título centralizado "✦ Balão de Fala ✦".
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

> Texto completo por versão: `CHANGELOG.md`. Resumo com foco em decisão:

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
| 3.57.0 | **tons de fala** (estudo do mod Fabric "roleplay-speech-bubbles"): `/pensar` novo (itálico + parênteses), `/s` agora vira balão MAIÚSCULO+negrito, `/w` itálico que não funcionava CORRIGIDO; pacote `ChatBubblePayload` ganhou campo `tone`. ⚠️ O mod Fabric embute telemetria (`creativemode-telemetry`) — jamais replicar |
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
- ❌ Faixa/banda no balão; ❌ emoji via fonte na fala; ❌ emoji-tint no mundo; ❌ bandas de fonte; ❌ dashed antigo; ❌ balão-nuvem; ❌ contadores "emojis x/6"; ❌ botões Auto/"Minha cor"; ❌ ordem antiga dos botões; ❌ card escuro na prévia; ❌ editor baú.
- ❌ **Rabicho triângulo antigo** (`bubble_tail.png` com texels quebrados — reprovado na 3.56) e ❌ quadrado-conector; ❌ degraus pixel-art (3.56–3.57); ❌ **rabicho GRANDE** (26×18 da 3.58.0, reprovado na print). ✅ Rabicho atual = **recorte na silhueta PEQUENO e FIXO** (`tail.png` texel branco, 13×9, contorno único, cor exata).
- ✅ Balão cresce **pra cima** com o texto (linhas equilibradas, nunca tirinha deitada); ✅ recheio cobre o encaixe do rabicho (costura invisível).
- Quadrada = quadrada AO TODO; Sem borda por último em 2 fileiras de 3; tela `/balao` à prova de crash.

### Medo
- Ícone = **FOTO INTEIRA** (carinha + palavra "medo") — nunca recortar/redesenhar; borda dissolvendo em alpha aprovada.
- **COM shaders**: camada oficial de HUD (`registerAboveAll`) + véu 208 — não voltar para `RenderGuiEvent.Post`.
- Mensagem só pra admin; paleta azul-noite; véu que ESCONDE a tela.

### Outros
- ❌ Nuvem no balão (removida a pedido no 3.50 — manter fora).
- ❌ **TELEMETRIA/coleta de dados JAMAIS** (o mod Fabric estudado no 3.57.0 embute `creativemode-telemetry` — jar-in-jar que rastreia sessões/mods; serviu só de referência de mecânica).
- Tons de fala (3.57.0): `tone` 0/1/2 no `ChatBubblePayload`; pensar/sussurro = itálico (fonte vanilla mantém a largura → medida intacta), grito = fake bold (2ª passada 0,75px).
- `/balao teste` **não existe mais** — testar é escrever no chat (a prévia usa a última fala).
- Música baixada fica no **SERVIDOR** e sincroniza pros clientes ("iniciado/concluído"); todo mundo ouve.
- Sons "1x na entrada" da lorezone: opção pedida explicitamente (manter).

---

## 7. Receitas operacionais

### Build pelo GRADLE (caminho dos humanos — o que o amigo usa)
- O projeto é um **ModDevGradle 2.0.146** padrão (Gradle 9.2.1 via wrapper, NF 21.1.249, foojay-resolver).
- `./gradlew build` → jar em `build/libs/rptag-<v>.jar`; `runClient`/`runServer` pra testar.
- **Testado no sandbox até a etapa de descompilação do Minecraft** (wrapper ✓, plugin ✓, downloads ✓, 6 etapas do pipeline ✓) — a descompilação em si precisa de ~4 GB de RAM e o sandbox tem 2 GB (morre ali, KILL do cgroup, sem log de OOM). Em PC normal passa. NÃO mandar patch de memória pro repo (cache local do sandbox só).
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

*Última atualização: ciclo 3.84.0 (2026-10-18), commit `git log -1`.*
