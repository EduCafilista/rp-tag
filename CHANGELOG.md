# Changelog

Todas as mudanças notáveis deste mod são documentadas aqui.

## [3.56.1] - 2026-10-08

### Corrigido
- **Rabicho agora é da MESMA cor exata do balão**: os texels da pílula são
  248 (não 255) e o rabicho usava texel branco — ficava ~5% mais claro que
  o balão. Agora o rabicho amostra a mesma janela de textura da pílula
  (a Quadrada, de texel 255, segue como estava). Medido na print do jogador:
  antes `rgb(166,87,221)` vs balão `rgb(158,82,210)` — agora idênticos.
- `/rp ajuda`: removida a linha amarela "Tudo fica salvo no mundo · guia
  completa: WALKTHROUGH.md".

## [3.56.0] - 2026-10-05

### Reescrito — o desenho dos balões de fala
- **Mais palavras = mais balão (pra cima!)**: o texto quebra em linhas
  equilibradas mais cedo (130px) e a pílula cresce em ALTURA — o balão fica
  sempre proporcionado, nunca vira uma tirinha gigante deitada.
- **Rabicho PIXEL em degraus**: dois degraus de pixel art com contorno por
  toda a volta — **nem quadrado** ligando no personagem, **nem triângulo
  colorido**. O mesmo rabicho vale para TODAS as molduras (Clássica, Cartum,
  Quadrada, Costura e Brilho). A encaixe na pílula é coberto por ela mesma
  (costura invisível, sem linha atravessada).
- A textura antiga do rabicho (triângulo curvo) foi REMOVIDA do mod.

### Prévia do menu agora é fiel com textos longos
- A prévia do `/balao` quebra a sua fala em linhas (mesmas regras do mundo),
  cresce em altura com o texto e ajusta o zoom — **sua última fala do chat
  nunca mais vaza da pílula** na prévia.

## [3.55.0] - 2026-10-05

### Corrigido — X vermelho e a tooltip "tirar Morango" travada
- O X dos emojis ficou **GRANDE (10px) e sempre visível**, e a tooltip
  "✖ tirar Morango" **não flutua mais na frente de tudo** — o nome agora
  aparece na própria linha do título, à direita (some quando tira o mouse).

### Novo — DOWNLOAD de música pra pasta do SERVIDOR (todo mundo ouve!)
- `/lorezone baixar <link direto .ogg/.wav>` — "⤵ Download iniciado..." /
  "✔ Download concluído (N KB)!" e o arquivo vai pra
  `config/rptag/musicas/` do SERVIDOR.
- **SYNC AUTOMÁTICO**: quando um jogador entra na zona, o servidor pergunta
  se o cliente já tem o arquivo; quem não tem **recebe os bytes do
  servidor** (em pedaços, gravado em `config/rptag/musicas/` do cliente) e
  **toca na hora** — ninguém precisa baixar nada!
- (YouTube continua precisando virar .ogg primeiro — o comando ensina.)

### Novo — som que toca 1x SÓ QUANDO ENTRA na zona
- `/lorezone musica <id> 1x <som>` — toca uma única vez na entrada (sem
  loop). O loop normal continua: `/lorezone musica <id> <som>` (ou
  `intervalo <s>`).

### Novo — /roll +3 (especialidade)
- `/roll +3` = "sou especialista nisso" → rola **1d20+3**. Também vale
  `/roll +3 atacar` e combinado: `/roll 2d6+3`.

### Alterado — /balao sem "teste": escreva no chat!
- O `/balao teste <frase>` saiu. Agora a **prévia usa a sua última fala
  real do chat**: escreva qualquer coisa no chat, abra o `/balao` e veja a
  SUA frase no balão de verdade.

### Corrigido — o "blur" quadrado na tela
- O ícone do medo tinha um **fundo pálido quadrado** que aparecia como um
  borrão na tela (o "blur"). A borda do quadrado agora **dissolve em
  transparência** — só a carinha e a palavra "medo" flutuam.

### Documentado — como a Lore Zone funciona
- O raio é uma **ESFERA**: `/lorezone criar x 10` pega 10 blocos para CADA
  lado (diâmetro 20), em altura também. A mensagem de criação agora explica.

## [3.54.1] - 2026-10-05

### Alterado — o ícone do medo é a FOTO INTEIRA
- Sem recorte: a imagem completa (fundo e a palavra **"medo"** embaixo) é
  o ícone do efeito no HUD e o símbolo que pisca no cantinho durante o medo.

## [3.54.0] - 2026-10-05

### Corrigido — o MEDO aparece COM SHADERS (e ficou MAIS FORTE)
- A causa de sumir com shaders: o desenho do medo (vinheta + véu) vivia num
  gancho que fica por baixo/fora da saída do shader (Iris). Agora ele é uma
  **CAMADA OFICIAL de HUD** (`RegisterGuiLayersEvent.registerAboveAll`) — a
  mesma pipeline do blur de abóbora/vinheta vanilla, que roda **depois do
  pós-processamento** e **acima de tudo** na tela. Com shaders e sem shaders,
  o medo aparece do mesmo jeito.
- Véu REFORÇADO: alpha 168 → **208** (a tela afunda na escuridão de verdade).

### Novo — o ícone do medo é o PNG enviado
- O ícone do efeito agora é **a foto real da carinha** (recorte com fundo
  transparente, 64x64 com autocontraste) em vez da carinha redesenhada.

## [3.53.0] - 2026-10-05

### Novo — a carinha do MEDO virou o ícone do efeito
- A carinha amarela assustada (sorridente de medo, com dentes) agora é o
  **ícone oficial do efeito `rptag:medo`** — aparece no canto do HUD (com o
  tempo restante) igual Veneno/Wither, em vez do quadrado branco vazio.

### Corrigido — `/effect give rptag:medo` agora dispara o MEDO completo
- Antes quem recebia o efeito só pelo `/effect give` **ficava normal** (sem
  tremedeira): os visuais só ligavam via pacote do `/rp admin medo`.
- Agora o **cliente lê o efeito local**: quem tem `rptag:medo` ativo — de
  `/effect give`, command block ou `/rp admin medo` — tem a tela escondida
  pelo véu azul-noite, tremendo, **até o efeito acabar**, sempre em sync com
  o tempo real do efeito.

## [3.52.0] - 2026-10-05

### Corrigido — os SONS das lore zones que ficavam MUDOS
- A causa raiz: o som era criado "solto" (evento não registrado) e o pacote
  de som saía sem o holder certo — caverna, alma, coração e a maioria dos
  presets **não tocava**. Agora o som vem do REGISTRO do jogo
  (`BuiltInRegistries.SOUND_EVENT`, o mesmo caminho do `/playsound`) e toca
  de verdade. Volume do som pontual também subiu (0.8 → 1.0).

### Corrigido — sons EMPILHANDO (agora um substitui o outro)
- Ao entrar de novo na zona, trocar de zona ou re-disparar no intervalo, o
  som/música ANTERIOR é parado antes do novo começar (`StopSound` por
  jogador, por categoria). Nunca mais dois discos sobrepostos.
- Saindo da zona, o som pontual também para (não fica "ecoando" atrás).
- Admin trocou o som/música? Quem JÁ ESTÁ dentro ouve o novo NA HORA.

### Novo — 17 presets de som + "nenhum" (27 no total)
- `portal`, `portalviagem`, `nether`, `vento`, `dragao`, `ghast`, `creeper`,
  `trovao`, `chuva`, `fogo`, `agua`, `sino`, `outros` (Otherside),
  `relic`, `creator`, `maldicao` (disco Ward) e `nenhum` (limpa o som da
  zona) — todos conferidos no `sounds.json` da 1.21.1.
- `/lorezone som <id> <som inválido>` agora **não salva**: o admin recebe o
  erro NA HORA (era assim que som errado passava batido e ficava mudo).
- TAB completa presets + as músicas `@arquivo` do servidor.

### Novo — música por LINK direto (.ogg/.wav)
- `/lorezone musica <id> https://site.com/tema.ogg` — os players **baixam o
  áudio automaticamente** (cache em `config/rptag/musicas/cache/`) e tocam
  em LOOP. Suporta `.ogg` Vorbis e `.wav` (limite 32 MB).
- **YouTube não toca direto** (o vídeo precisa virar áudio primeiro): o
  comando responde com o `yt-dlp -x --audio-format vorbis "URL"` pronto pra
  copiar e o `@nome` pra usar em seguida.

### Novo — resposta diz em qual VOLUME o pessoal ajusta
- Som pontual = slider **Ambiente** · música/loop = slider **Música**
  (Opções → Música e Sons). A dica aparece na confirmação do comando.

### Corrigido — X vermelho e "morango fantasma" na tela do balão
- O X vermelho de remover ficou **maior (14px)** e cada emoji da fala agora
  tem um **mini X sempre visível** no canto do slot (clique no slot = tirou).
- Com um adesivo/emoji "na mão", clicar fora do balão agora **solta** o
  adesivo — antes ele empilhava na fala E ficava grudado no mouse (o
  "morango bugado no meio").

## [3.51.1] - 2026-10-04

### Corrigido — ordem dos botões da placa Moldura
- As 5 molduras agora ficam em 2 fileiras de 3 — `[Clássica] [Cartum]
  [Quadrada]` / `[Costura] [Brilho] [Sem borda]` — sem buraco na grade e
  sem o botão de borda solto numa fileira própria. A placa ficou compacta.

### Corrigido — rabicho discreto (fica pouco à mostra)
- O rabinho encolheu de novo e o contorno fica COLADO no recheio (~1px),
  sem as "asas" escuras que apareciam em volta; também não tem mais sombra
  no rabicho (a sombra fica só no corpo). Na Quadrada o retangulinho
  acompanhou. A pilha de balões recalculou a altura (rabicho menor).

## [3.51.0] - 2026-10-02

### Segurança — tela do balão à prova de crash
- O menu `/balao` agora é blindado como o mundo: **qualquer erro** no desenho
  (cards, prévia, teclado) cai para um "modo simples" — os botões seguem
  funcionando e o motivo fica no log. Desenho, overlay, montagem da tela e
  cliques protegidos: a tela NUNCA mais derruba o jogo (inclusive com
  FancyMenu/menus customizados por cima).

### Embelezado — rabicho do balão (fim do "triângulo colorido")
- O rabinho era um triângulo GRANDE e chapado (print!). Agora é uma curva
  menor e elegante, levemente à direita do centro, com contorno da moldura
  e recheio por cima — cauda de quadrinho de verdade.

### Quadrada 100% quadrada
- O recheio da Quadrada usava a textura da CÁPSULA redonda por cima do
  contorno quadrado (quinas duplas). Agora corpo e recheio usam a mesma
  textura de cantinhos, e o rabicho vira um RETANGULINHO com contorno —
  o balão fica quadrado ao todo.

### Emojis dos DOIS LADOS
- Adesivo mora na coluna da ESQUERDA e os emojis da fala na coluna da
  DIREITA, independentes: dá pra deixar um emoji de cada lado com figuras
  diferentes (ex.: espada e escudo). Na prévia, soltar o adesivo na metade
  ESQUERDA do balão = adesivo; na metade DIREITA = emoji. Colocar 2 não
  empilha mais tudo de um lado (o bug da 3.50).

### Removidos — botões "Auto" e "Minha cor"
- Obsoletos (a cor da borda já se escolhe na paleta "Borda:"). Ficou um
  botão único **Sem borda / Com borda** que tira e repõe a borda.

## [3.50.0] - 2026-10-02

### Corrigido — Lore Zone agora dispara ao SAIR E VOLTAR
- Sair e reentrar na zona mostra o título/som de novo: o registro de zonas
  vistas agora limpa a zona quando o jogador SAI dela ("1x por visita",
  não mais "1x para sempre").

### Adicionado — `/rp ajuda` (lista de comandos com a função de cada um)
- Jogadores veem todos os comandos gerais (`/rp`, `/g`, `/s`, `/w`, `/me`,
  `/roll`, `/persona`, `/balao`...). Admins veem também a seção SO ADMINS
  (medo, avisar, lorezone, chatlocal, bolha/balão/bolhas, set, /do).

### Adicionado — API de MÚSICA CUSTOM para Lore Zones
- `/lorezone musica <id> @<nome>` toca o arquivo
  `config/rptag/musicas/<nome>.ogg` (pasta do CLIENTE) em LOOP enquanto o
  jogador está na zona, com STOP na saída (payload novo
  `lorezone_music`, canal v4).
- Decodificação OGG Vorbis pelo próprio jogo (OggAudioStream) + source
  OpenAL em loop, volume seguindo a categoria MUSICA do jogador. Pega
  música de vídeo (ex.: YouTube) com `yt-dlp -x --audio-format vorbis`.
- **Presets prontos** com nome fácil: `caverna`, `coracao`, `warden`,
  `deepdark`, `disc13`, `disc11`, `disc5`, `mood`, `almas`, `pigstep` —
  valem em `/lorezone som` e `/lorezone musica`. `/lorezone musicas` lista
  tudo e ensina a API.
- **Fix de parse da 3.48**: `minecraft:xyz` solto não era aceito (o argumento
  `string()` do Brigadier rejeita `:` em palavra única). Agora o som é o
  último argumento (greedy) e aceita namespace solto; o intervalo virou
  `/lorezone musica <id> intervalo <segundos> <som>`.

### Alterado — ADEUS balão-nuvem, OLÁ balão QUADRADO
- O estilo Nuvem (e o antigo Gibi) saíram: quem tinha salvo vê agora o
  balão **Quadrado** — retângulo de cantinhos arredondados ("meio
  quadrado"), desenhado com a técnica de 3 fatias (textura nova
  `quadrada.png`), sem peças que quebram.

### Corrigido — prévia do menu = O MUNDO, letra com respiro
- A pílula, as molduras e o rabicho da prévia agora são os MESMOS quads
  texturizados do mundo (`RenderType.text`); o fundo de céu/grama virou
  textura via BLIT (não some mais em cliente com shader).
- Linhas do menu reespaçadas: título das placas ocupa linha DENTRO da
  placa (não come mais a primeira fileira), gap entre todas as linhas
  (fim do "Letra" colado no "Moldura").
- Contadores "Emojis (0/6 na fala · 0 no balão)" REMOVIDOS (era ruído).

### Corrigido — emojis duplicados/quebrados
- Os adesivos/emojis são contados e desenhados por CODEPOINT: cada emoji
  vale exatamente 1 glifo (a contagem antiga dividia por 2 e fatiava a
  lista em pares — slot pulado, emoji dobrado e prévia diferente do
  mundo).

## [3.49.0] - 2026-10-01

### Corrigido — nuvem do balão (estilo Turma da Mônica)
- **Bolas de verdade, não mais quadrados com costura**: as bolinhas da nuvem
  eram desenhadas com a textura da PÍLULA (que tem quinas e costura nas
  bordas) — no mundo isso virava "quadradões" emendados, nada parecido com
  uma nuvem. Agora existe uma textura própria `bola.png` (círculo esférico
  HD, gerada em 128px e suavizada) e as bolas são quads texturizados nela.
- **Silhueta scalloped (gibi)**: a borda da nuvem foi reescrita em DUAS
  fileiras de bolas intercaladas (grandes 18px + pequenas 13px) no topo,
  na base e nas laterais, com o rabinho de pensamento em bolinhas
  decrescentes (10/8/6) — a sobreposição das bolas forma o contorno
  recortado de gibi, sem quinas visíveis em nenhuma borda.

### Corrigido — MEDO agora ESCONDE a tela
- **Véu de pânico**: além da vinheta, do ícone piscando e do tremor de
  câmera, o MEDO agora cobre a tela inteira com um véu azul-noite
  translúcido que pulsa com a intensidade do efeito — quem está com medo
  NÃO vê o mundo com clareza (os "espinhos" verticais da 3.48 saíram).

### Corrigido — prévia do menu de estilo
- **O menu agora mostra como fica NO MUNDO**: o card de prévia tinha fundo
  escuro, que escondia a pílula clara e "bugava" a aparência. O card agora
  tem fundo de CÉU (gradiente azul do horizonte ao zênite) com faixa de
  GRAMA embaixo — igual ao ambiente do jogo, a pílula aparece como
  realmente fica.

## [3.48.0] - 2026-10-01

### Adicionado — Lore Zones com música
- **`/lorezone musica <id> <som> [intervalo]`**: além do som 1x ao entrar,
  o admin escolhe uma TRILHA em LOOP para tocar enquanto o jogador está na
  zona (source MUSIC, volume 0.7), reiniciada a cada `intervalo` segundos
  (10–600, padrão 45). Ao sair, a música para na hora (StopSound).
- Sugestões tensas: `minecraft:ambient.cave`, `minecraft:entity.warden.heartbeat`,
  `minecraft:music.overworld.deep_dark`, `minecraft:music_disc.13`.

### Alterado — MEDO estilo anime
- Vinheta em preto/azul escuro (nada de vermelho), cortinas verticais de
  aura balançando, tinta azul pesada e carinha azulada piscando no HUD.

## [3.42.0] - 2026-09-19

### Corrigido — emblemas (adesivo/emoji na borda)
- **Emoji "fora do lugar" (flutuando longe do balão)**: a largura da pílula
  era calculada só a partir do TEXTO, sem nunca considerar o espaço que o
  próprio emblema (adesivo + emotes) precisa. Numa mensagem curta ("Oi!")
  com vários emojis, cada um é desenhado 18px à direita do anterior a
  partir de um ponto fixo — isso ultrapassava a pílula minúscula rapidinho,
  deixando emoji pendurado no vazio bem longe do balão. Agora a pílula
  garante espaço para o emblema inteiro por LARGURA (sem reintroduzir
  faixa/altura extra — a correção é só horizontal).
- **Qualidade inconsistente entre os emojis do mesmo balão**: o adesivo
  único (o que fica preso a um canto) desenhava pela fonte de baixa
  resolução (`stickers.png`, 16×16 por glifo — a mesma usada para emoji
  digitado dentro do texto do chat normal), enquanto os emotes extras já
  usavam o atlas em alta resolução (`stickers_hi.png`, 128×128 por glifo).
  Resultado: dentro do MESMO balão, um emoji saía nítido e o outro saía
  borrado/blocado — a inconsistência que parecia "emoji quebrado". Agora
  os dois caminhos (adesivo único e extras) usam o mesmo atlas HD no
  mundo, com a mesma nitidez tipo Android em ambos.
- **Emoji não acompanhava o fade do balão**: o quad HD sempre desenhava
  com opacidade 100% fixa, ignorando o alfa de entrada (pop-in) e saída
  (fade) do balão — o emoji aparecia/sumia de golpe enquanto o resto do
  balão entrava/saía suavemente. Corrigido nos três lugares que desenham
  emoji (inline no texto, adesivo do canto, emotes extras).
- A mesma correção de largura foi aplicada na prévia do menu de estilo,
  pra não voltar a divergir do que acontece no mundo.

## [3.41.0] - 2026-09-19

### Corrigido
- **EMBLEMA (adesivo/emoji na borda) CAINDO EM CIMA DAS LETRAS.** O bug:
  `by`/`syB` marcavam o CENTRO do emblema, mas o glifo mede 16px de altura
  (8px pra cada lado desse centro). O offset usado era só 4px — ou seja,
  8px de glifo menos 4px de offset deixava exatos **4px do emblema
  dentro da pílula**, encostando (ou entrando) na primeira linha de
  texto. Ficava pior quanto mais curta a mensagem: com a frase pequena a
  posição horizontal também colapsava pro centro do balão (mesmo lugar
  onde o texto está), então o emblema acabava bem em cima das letras —
  exatamente o que apareceu nos prints. Corrigido em TRÊS lugares que
  tinham a mesma conta errada: o balão no mundo (`BubbleRenderer`), o
  emblema da prévia do menu e o adesivo livre arrastável da prévia
  (`BubbleStyleScreen`). Agora o offset conta a meia-altura real do
  glifo (8) mais uma folga (4) — o emblema nunca mais encosta no texto.
- **Prévia do menu com o texto ampliado (zoom 2x) maior que a própria
  cápsula.** A altura da cápsula da prévia era um valor fixo de 18px,
  mas o texto ampliado sozinho já ocupa ~16px + respiro — o texto
  estourava por baixo antes mesmo de qualquer emblema entrar em cena.
  Agora a altura da cápsula é calculada a partir do zoom de verdade.

## [3.40.0] - 2026-09-19

### Adicionado
- **/balao teste <frase>** — mostra a frase como balao NA HORA, sem depender
  do chat (perfeito pra testar o estilo). Respeita a liberacao do admin e,
  se o modo balao estiver OFF, liga sozinho e avisa.
- **/rp status COMPLETO** — agora mostra tudo de uma vez: estado RP, persona,
  chat local (ligado/global), balao (liberado/bloqueado), modo balao e o
  estilo salvo (cor + moldura). Otimo pra diagnosticar "por que nao funciona".

### Documentacao
- **ENTREGA.md**: guia de handover pra quem for continuar o desenvolvimento
  (arquitetura, como buildar, regras de ouro do projeto e roadmap).
## [3.39.0] - 2026-09-18

### Seguranca / Robustez (auditoria de brechas)
- **PAYLOAD BLINDADO NO SERVIDOR**: o estilo enviado pelo cliente agora e
  sanitizado antes de gravar — borderStyle 0..5, posicao do adesivo 0..100%,
  duracao 2..20s, adesivo <= 4 e emotes <= 8 (so codepoints validos).
- **CRASH DE SESSAO MORTO**: borderStyle NEGATIVO (de cliente alterado)
  estourava o RING_MARGIN e DESLIGAVA OS BALOES DE TODOS ate reiniciar o
  jogo — agora o servidor rejeita na entrada E o render clampa defensivo.
- **ANTI-FLOOD DE BALOES**: minimo de 250ms entre baloes do mesmo jogador
  (4/s e mais que suficiente pra conversa RP) — sem spam de pacotes pra
  quem esta perto.
- **/balao off limpa a pilha TODA na hora**: o pacote de limpeza (texto
  vazio) apagava so um slot (virava balao fantasma na pilha); agora some
  TUDO do jogador na hora, como sempre foi prometido.
## [3.38.0] - 2026-09-17

### Corrigido
- **QUEBRA DE LINHA INTELIGENTE**: o wrap guloso do vanilla jogava a ultima
  palavra orfa numa linha curta embaixo ("tentou pegar o / calice"). Agora
  as linhas ficam EQUILIBRADAS (mesmo numero de linhas, reparticao por
  programacao dinamica: "tentou pegar / o calice" vira 2 linhas emparelhadas
  — sem palavra sobrando feia). Palavra maior que a linha inteira e fatiada
  no limite (nunca mais clipa na borda do balao).
- **Linha de texto mais larga (140 → 160)**: quebra menos, baloes mais
  compactos pra frases de tamano normal.
- A altura da pilha usa exatamente as mesmas linhas do render (paridade total).
## [3.37.0] - 2026-09-17

### Redesenhado
- **EMOJI = EMBLEMA NA BORDA — FIM DA FAIXA, FIM DO "AR"!** O balao agora
  fica do tamanho EXATO do texto (colado, como nos baloes de referência),
  e o adesivo/emotes vira um EMBLEMA pregado NO CONTORNO: sobre poe
  apenas a borda (metade pra fora), no lado escolhido — nunca sobre as
  letras, nunca criando faixa, linha ou espaco vazio. Era o pedido desde
  a 3.32, agora no desenho definitivo:
  - pílula = texto + 8px (pronto, acabou o vazio)
  - emblema: 16px, centro 4px fora da borda, na frente de tudo
  - posicao horizontal continua a sua (stickerX), lado = stickerY
  - pilha de baloes conta o emblema pendurado
  - previa do menu identica (capsula colada + emblema na borda)
## [3.36.1] - 2026-09-16

### Melhorado
- **BALAO ACOMPANHA QUEM ESTA VENDO — e agora fica LEGIVEL DE LONGE!**
  O balao JA gira pra cada jogador que olha (cada cliente renderiza o
  billboard virado pra propria camera: de tras voce le, pela direita ele
  vira pra voce — visual, so pra quem esta por perto, exatamente como
  pedido). A novidade: ele tambem CRESCE SUAVEMENTE com a distancia
  (1.0x perto → ~2.4x a 64 blocos), entao quem ve de longe continua
  lendo em vez de ver um puntinho.
- A pilha de baloes usa o MESMO fator de distancia (nada de sobrepor de longe).

## [3.36.0] - 2026-09-16

### Corrigido (por cima da reforma)
- **FIX RAIZ DO "AR" RE-APLICADO** (a reforma tinha saido de uma base anterior
  a 3.35.0 e a geometria antiga voltou): a base da pilula encosta EXATAMENTE
  na ancora (yP1 = 0, logo acima do nome) e o balao cresce SO PRA CIMA, 100%
  colado — o nome NUNCA mais fica dentro do box, zero espaco morto.
- Previa do menu sem ar interno de novo (38px com faixa / 18px sem = mundo).

### Reforma visual dos baloes (source do usuario + Claude)

### Reforma visual dos balões

- **As 6 molduras eram idênticas na prática — agora cada uma é distinta.**
  Esta era a causa raiz do balão "feio". O contorno sempre era desenhado
  apenas 2px para fora da pílula, e o recheio era desenhado *depois, por
  cima*. Toda a tinta além desses 2px ficava escondida atrás do recheio,
  então Clássica, Cartum, Dupla, Brilho e Gibi saíam com a mesma casquinha
  fina. Agora cada estilo tem sua própria margem para fora
  (`RING_MARGIN = {2, 4, 5, 3, 4, 5}`).
- **A moldura Tracejada não aparecia.** Era desenhada exatamente no tamanho
  da pílula, então o recheio cobria os tracinhos por inteiro. Além disso a
  "tampa" usava 32px enquanto a textura foi feita com 16 — o contorno não
  acompanhava a curva. Os dois erros foram corrigidos.
- **Balão branco saía cinza sujo.** A textura antiga escurecia o recheio até
  181/255 e o interior tinha alfa 0xEE (deixava o cenário atravessar). Agora
  o interior é opaco e a textura mantém a cor escolhida de verdade.
- **O estilo "Brilho" era invisível** — o halo era pintado com a cor *escura*
  da borda. Agora o halo usa uma versão clara da cor do balão.
- **Texto borrado.** A sombra preta do vanilla sujava cada letra dentro de um
  balão claro. Removida (o interior opaco já dá contraste sozinho).
- **Rabicho** parecia uma lasca torta colada no balão: a textura não convergia
  numa ponta. Refeito como um triângulo de quadrinho de verdade.
- **Sombra** deixou de ser um eco preto deslocado 2px (cara de clipart) e
  virou um apoio suave logo abaixo.
- **Balão ~18% maior** (escala 0.034 → 0.040) para ler de mais longe, e faixa
  de texto mais larga (110 → 140) para quebrar menos linhas.
- **Texturas refeitas**: `bubble`, `ring`, `cartum`, `dupla`, `gibi`,
  `dashed`, `bubble_tail`, e a nova `brilho.png`. A `cloud.png` foi removida
  (sem uso desde a 3.29.0).

### Corrigido
- `RING_MARGIN` tinha 5 posições para 6 estilos: o Gibi lia a margem do
  estilo errado por causa do clamp.
- A escala do balão (`0.034`) estava escrita à mão no render **e** na conta
  de empilhamento. Virou a constante `SCALE` — mudar uma e esquecer a outra
  fazia os balões empilhados se sobreporem.
- Removido código morto com UVs erradas: `flatQuad` calculava as coordenadas
  para uma textura 256x128 enquanto a `bubble.png` é 512x256 (o "brilho
  gelatinoso" do Gibi amostrava a região errada), além da nuvem de
  pensamento sem uso desde a 3.29.0.
- A prévia do menu foi realinhada com as espessuras e o halo do mundo.

## [3.35.0] - 2026-09-15

### Corrigido
- **O AR NO BALÃO — BUG RAIZ ENCONTRADO E MORTO!** A geometria da pílula
  usava o eixo Y INVERTIDO: a base do balão ficava ABAIXO da âncora e o
  box DESCIA POR CIMA do nome — o nome ficava DENTRO do balão e sobrava
  um retângulo enorme meio vazio (era isso em TODOS os prints!).
  Agora a base da pílula encosta EXATAMENTE na âncora (logo acima do
  nome) e o balão cresce SÓ PRA CIMA, 100% colado no conteúdo:
  [faixa?] + texto + [faixa?] — ZERO espaço morto, nome NUNCA mais
  dentro do box.
- **Prévia do menu IGUAL ao mundo**: pílula da simulação também colada
  (36px com faixa / 16px sem), sem ar interno.

## [3.34.1] - 2026-09-15

### Corrigido
- **Borda automática instável**: a cor da borda "Auto" usava um OU bit a bit
  pra dar um tom frio à cor escurecida — isso podia CLAREAR canais de forma
  imprevisível dependendo da cor escolhida. Agora é uma soma com limite por
  canal, sempre estável.
- **Balão "Brilho" podia se aproximar demais do balão empilhado acima**: a
  conta de altura da pilha esquecia o estilo Brilho no grupo que precisa de
  mais respiro (só contava Tracejada e Gibi).
- **`/me` e `/do` "funcionavam" sem avisar nada** quando quem mandou estava
  OFF RP: a ação só é entregue a quem está EM RP, então o próprio jogador
  às vezes não via a própria mensagem em lugar nenhum. Agora: `/me` avisa
  com uma mensagem de erro se você estiver OFF RP (`/rp on` primeiro); e
  quem manda a ação (inclusive `/do` do admin) sempre recebe o eco da
  própria mensagem, esteja em RP ou não.
- **"Chat local" podia voltar desligado sozinho** em saves onde a chave
  nunca foi gravada (mesma classe de bug que já tinha sido corrigida só
  pro "balões ligados globalmente").
- Documentação: o README ainda descrevia a moldura 4 como "Pensamento"
  (nuvem com rabinho de bolinhas), mas ela virou "Brilho" na 3.29.0 — o
  texto foi corrigido pra bater com o que o jogo realmente desenha.

## [3.34.0] - 2026-09-15

### Corrigido
- **/ME NAO VIRA MAIS BALAO!** Acao e AÇAO, fala e FALA: o /me agora vai
  SO para o chat RP — nunca aparece mais como balao de fala.
- **/ME E /DO RESPEITAM A REGIAO DO RP**: as acoes agora so chegam a
  jogadores EM RP dentro do alcance local (a regiao). Quem esta OFF RP
  nao ve as acoes — separacao clara entre:
  - **BALAO** = fala (chat normal no modo balao, /g, /s, /w)
  - **CHAT RP** = acoes (/me, /do) — so pra quem esta em RP, na regiao
  - **CHAT NORMAL** = conversa OFF RP (todo mundo ve)
- **TAG RP NO TAB (FINALMENTE!)**: achado o bug que impedia o TAB de
  mostrar o estado RP desde sempre — o handler via "nome padrao do
  vanilla" e saia sem colocar a tag. Agora o TAB mostra
  Nome + (RP)/(OFF RP) certinho, com atualizacao ao ligar/desligar.

## [3.33.0] - 2026-09-15

### Corrigido
- **FIM DO "AR" NO BALÃO (o espaço enorme do print!)**: a faixa dos
  emotes/adesivo foi REPROJETADA pra ser JUSTA — 20px no total (16 do
  emoji + 2px de ar de cada lado), no lugar dos 28px cheios de vazio.
  O balão agora fica colado no conteúdo: texto em cima, faixinha embaixo
  (ou em cima), rabicho — SEM espaço morto.
- Largura da faixa enxuta também (margens menores).
- A pilha de balões acompanha a faixa nova (altura exata).
- Menu em paridade 1:1 (prévia 42px com faixa justa).

## [3.32.0] - 2026-09-15

### Corrigido
- **EMOTE = SO A BORDA, LINHA NENHUMA (o pedido exato!)**: os emotes da
  fala NAO entram mais no texto — agora moram na FAIXA do balao (a mesma
  do adesivo), ao lado dele. A fala e 100% texto puro:
  - NADA de linha de emoji (o espaco vazio que aparecia no print morreu)
  - NADA de texto empurrado/escorrido
  - O balao fica do tamanho EXATO do texto (+ a faixa, se tiver emote)

### Melhorado
- Emotes desenhados por QUADS HD (Android) na faixa, em fileira organizada
  ao lado do adesivo — no mundo E na previa do menu (paridade 1:1).
- A pilha de baloes calcula a altura pelo texto puro + faixa (perfeita).

## [3.31.1] - 2026-09-15

### Corrigido
- **O BUG DO PRINT (texto pendurado fora do balao): CAUSA RAIZ
  ENCONTRADA E MORTA!** A pílula era desenhada com a altura SEM a faixa do
  adesivo — quando a faixa expandia o balao pra cima (28px), a pílula
  terminava 28px ANTES do fim do texto e as letras PENDURAVAM fora,
  formando aquela "faixa preta" solta embaixo do balao. Agora a pílula
  cobre TUDO (faixa + texto) nos dois lados — geometria verificada
  matematicamente pra faixa em cima E embaixo.

## [3.31.0] - 2026-09-15

### Corrigido
- **TEXTO + EMOJI FUNCIONANDO JUNTOS (de verdade!)**: o emoji na fala NAO
  passa mais pela fonte de texto — agora e um QUAD da arte NOTO (Android)
  desenhado EXATAMENTE onde a geometria manda. Impossivel virar bloco
  preto, invadir a linha de cima ou empurrar as letras: o texto e
  renderizado 100% separado do emoji.
- **TEXTURAS POT** (128x256 / 1024x2048): a fonte e o atlas agora tem
  tamanho potencia-de-2 — nunca mais quebram em driver/GPU nenhum.

### Melhorado
- **EMOJIS NO MUNDO EM HD**: os emojis da fala agora sao desenhados do
  MESMO atlas Android 128px do menu — menos pixel, mais imagem (como
  pedido!). A largura continua 16px, entao a quebra de linha nao muda.

## [3.30.0] - 2026-09-15

### Corrigido
- **FIM DAS SOBREPOSICOES (o bug do laco no texto!)** — tres correcoes de
  geometria:
  1. **FAIXA DO ADESIVO DOBROU (18 -> 28px)**: o adesivo de 16px ficava
     apertado na faixa de 18 e a base dele INVADIA a primeira linha de
     texto. Agora tem 12px de respiro puro entre adesivo e letras.
  2. **PILHA NA ESCALA CERTA**: desde que o balao cresceu 13%, a pilha
     ainda calculava com a escala ANTIGA — os baloes empilhados
     se sobrepunham. Agora a conta usa a MESMA escala do render.
  3. **FOLGA DO GLIFO DO EMOJI**: o emoji desenhado pela fonte sobe ~7px
     acima da linha — a pilula agora reserva esse espaco ANTES da primeira
     linha (nada de emoji vazando por cima do balao).
- A altura da pilha (bubbleHeightPx) espelha TODAS as novas medidas — a
  pilha continua perfeita com adesivo + emoji + texto grande juntos.

### Melhorado
- Menu em paridade: faixa de 28px na previa (balao 52px), texto a 31px do
  topo quando a faixa e em cima, emoji da fala top-alinhado com o texto.

## [3.29.0] - 2026-09-14

### Removido
- **NUVEM APOSENTADA** (a pedido!): o estilo PENSAMENTO virou **BRILHO** —
  halo suave ao redor do balao + anel elegante (2 desenhos, zero engasgo).

### Corrigido
- **EMOJI NUNCA MAIS QUEBRA O CAMPO DE TEXTO** (o bug do print!): a linha do
  emoji agora tem 20px com o glifo CENTRADO — as letras ficam sempre dentro
  do balao, mesmo com emoji colado na fala.
- Correcao critica do atlas HD (fileiras 9 e 10 dos novos emojis mostravam
  arte errada no menu).

### Adicionado
- **16 EMOJIS NOVOS (80 no total!)**: Sudoro, Apaixonado, Olhos de coracao,
  Festa, 100, Alvo, Presente, Chocolate, Bala, Pirulito, Cenoura, Panda,
  Dizzy, Papai Noel, Fada e Coelhinho — todos NOTO (Android) em HD.
- **LACO PRETO** (a pedido!): o laco rosa virou laco preto (tint proprio).

## [3.28.0] - 2026-09-14

### Adicionado
- **EMOJIS ANDROID DE VERDADE (NOTO)!** Os 64 adesivos agora usam a arte
  OFICIAL do Noto Emoji (a mesma fonte de emojis do Android) — licenca
  Apache 2.0, embutida no mod. No menu eles aparecem em ALTA DEFINICAO
  (atlas 1024x1024, 128px por emoji desenhado por quad direto).

### Corrigido
- **NUVEM: FIM DA FALHA** — a textura agora tem gomos PERIODICOS (32px) e o
  desenho no mundo usa periodos 1:1 (tile-perfeito): os gomos ficam SEMPRE
  do mesmo tamanho, nunca mais esticados/smeared.
- **COSTURA: FIM DO TRACINHO ESQUISITO** — mesma tecnica (periodos de 8px
  1:1): pontinhos constantes em qualquer largura de balao.
- **PIXEIS QUEBRADOS com muitas mensagens**: a pilula agora e 512x256 (2x
  mais resolucao) e o rabicho 64x64 — muito mais liso, mesmo com a pilha
  cheia de baloes na tela.

## [3.27.1] - 2026-09-13

### Melhorado
- **CONTORNOS NITIDOS EM TELA**: os aneis agora sao SOLIDOS (o alpha semi-
  transparente das versoes anteriores sumia quando a textura era comprimida
  no mundo — dava aspecto "esquisito"/falhado). Anti-aliasing so na parede
  externa de 1px: borda limpa de PERTO E DE LONGE.
- Costura com pontinhos mais fortes (2.6x5 solidos) e espacamento calibrado.
- Balao com contraste do volume um tico maior (legivel de longe).

## [3.27.0] - 2026-09-12

### Melhorado
- **REDESIGN COMPLETO — padrao STREAMER (a borda grossa foi embora!)**:
  - **VOLUME GERADO COM IA**: o brilho de gelatina da pilula agora vem de
    uma arte gerada por IA, remontada DENTRO da capsula matematica exata
    (geometria perfeita + volume de estrela — tinge perfeitamente).
  - **BORDAS FINAS E ELEGANTES**: todos os contornos foram afinados
    (Classica ~2px, Cartum 4px, Dupla 2 linhas finas, Gibi 6px) — nada de
    "borda grossa feia".
  - **BALAO 13% MAIOR** no mundo (presenca de quem fala pra plateia).
  - **TRACEJADO MORREU, NASCEU A COSTURA**: pontilhado delicado seguindo a
    silhueta da capsula (textura propria, 1 draw) — o bug do "quadrado no
    meio" ficou impossivel (nao existe mais quad nenhum).
  - Botao renomeado p/ "∴ Costura", ICONE verde da moldura ativa na placa,
    e dicas que EXPLICAM cada moldura quando o mouse passa em cima.
- Menu em paridade 1:1 (aneis com margem, costura, sombra do Cartum).

## [3.26.0] - 2026-09-12

### Melhorado
- **CADA BALÃO OLHADO E REFEITO (os 6 estilos!)**:
  - **PENSAMENTO**: textura da nuvem REFEITA do zero — gomos orgânicos de
    verdade na silhueta, interior liso com brilho, orla com degradê
    (Turma da Mônica premium, nada de "taturana").
  - **CLASSICA**: já com o anel em relevo da 3.25 (luz em cima da tinta).
  - **CARTUM**: capa de quadrinho com PROFUNDIDADE — sombra da tinta
    deslocada embaixo + brilho branco na parte de cima da capa.
  - **DUPLA**: aro escuro ganhou LUZ (brilho no aro externo).
  - **TRACEJADA**: cada tracinho agora tem um PONTINHO fofinho no meio do
    gap (padrão bilhete de cupom) — em cima, embaixo e nas laterais.
  - **GIBI**: brilho de gelatina em ELIPSE DUPLA (luz principal + núcleo
    super brilhante), igual balãozão de HQ.
- O MENU acompanha TUDO (paridade 1:1): capa com profundidade, luz no aro,
  pontinhos no tracejado, brilho duplo do gibi e nuvem com gloss.

## [3.25.0] - 2026-09-12

### Melhorado
- **QUALIDADE DOS BALÕES NOVA (pediram, ta entregue!)**: a pílula agora tem
  **brilho de gelatina** (luz no topo com elipse de reflexo), **degradê
  profundo** de cima pra baixo e **oclusão suave nas bordas** — visual 3D
  no padrão dos mods famosos, nada de cor chapada.
- **CONTORNO COM RELEVO**: o estilo Clássica ganhou textura própria de tinta
  (luz em cima, sombra embaixo) desenhada por um único quad — contorno de
  verdade, não uma linha reta sem graça.
- **RABICHO novo**: maior (32x32), afunilado com ponta arredondada e o
  mesmo degradê da pílula — acabei com a "casquinha" quadrada.
- O MENU acompanha: recheio com brilho (faixa clara + sombra) e a Clássica
  da prévia usa o mesmo anel em relevo via scanlines.

## [3.24.0] - 2026-09-11

### Melhorado
- **ZOOM NO CONTEUDO DA PREVIA!** A fala do balão no menu agora aparece com
  **2x de tamanho** (e os emojis em ARTE ampliada, não mais fonte miadinha) —
  dá pra LER exatamente o que vai pro mundo antes de testar.
- O balão da prévia cresceu pra dar bolso pro texto grande (e segue com a
  faixa de 18px quando tem adesivo — texto nunca encosta na faixa).
- Etiqueta "👀 Prévia" movida pro pé do card (não briga mais com o balão).
- Se você encher 6 emojis e não couber, o zoom cai pra 1.5x sozinho —
  nada estoura o card.

## [3.23.0] - 2026-09-11

### Corrigido
- **FIM DO RECORTE NA BORDA DO BALÃO!** A textura da pílula foi refeita:
  a arte agora preenche o canvas INTEIRO (256x128, sem margem morta) e os
  UVs foram corrigidos — as linhas de cima/baixo que apareciam "mordidas"
  (o talo do morango comendo a borda) nunca mais acontecem.
- **ADESIVO NUNCA MAIS NO CANTO**: o adesivo agora fica 18px pra dentro das
  bordas — encostado no canto arredondado ele SAIA do balão (visto no print).
  Agora mora sempre dentro da pílula, na faixa de cima ou de baixo.
- A pilha de balões reserva a altura da faixa do adesão (nada se sobrepõe).

### Melhorado
- **PÍLULA = CÁPSULA MATEMÁTICA**: textura gerada por equação (retângulo +
  2 semicírculos perfeitos), gradiente limpo sem manchas — e o MENU usa a
  mesma equação via scanlines: o que você vê na prévia é EXATAMENTE o shape
  do mundo.
- **MENU BLINDADO**: o balão da prévia agora é desenhado com RECORTE dentro
  do card — impossível vazar pra fora e invadir as outras linhas, por mais
  gordo que seja o balão (nuvem + faixa + rabicho).
- Etiqueta "👀 Prévia" em chavinha própria com filete dourado, card com
  moldura dupla, vinheta suave no fundo da tela, bolinhas do pensamento
  mais juntas.

## [3.22.0] - 2026-09-11

### Corrigido
- **ADESIVO NUNCA MAIS EM CIMA DAS LETRAS!**: o adesivo agora fica PRENDO nas
  faixas de borda do balão (cima ou baixo) — em qualquer posição X, mas o Y
  é travado fora da linha de texto. No mundo E na prévia do menu.
- **MENU 100% VISÍVEL**: a prévia inteira foi reescrita usando apenas `fill`
  (o desenho que SEMPRE funciona no menu) — zero dependência de texturas para
  informação crítica. Quem tinha "menu invisível" agora vê TUDO: pílula,
  nuvem com gomos contornados, rabicho, tracejado e adesivo.
- Clicar no balão para colar o adesivo agora define o lado pela metade
  clicada (metade de cima = faixa de cima).

### Melhorado
- **DESIGN DO MENU**: placas com acento dourado lateral, prévia mais alta
  (44px) com faixas de borda visíveis para o adesivo, texto da prévia
  centralizado, nuvem do menu com contorno escuro em todos os gomos
  (igualzinho ao balão de pensamento do mundo).

## [3.21.0] - 2026-09-11

### Melhorado
- **BALÃO DE PENSAMENTO (estilo Turma da Mônica!)**: a moldura Nuvem virou o
  balão de pensamento clássico — nuvem fofa com gomos contornados e o
  **rabinho de bolinhas que diminuem descendo em direção à cabeça** (no lugar
  do rabicho comum). No mundo E na prévia do menu.
- **CARTÃO DE PRÉVIA no menu**: a prévia agora fica dentro de um quadro
  próprio com etiqueta "👀 Prévia:" — o jogador sabe EXATAMENTE onde olhar
  para ver como o balão vai ficar. Prévia maior (120px de largura mínima).
- **PILHA CONTA AS BOLINHAS**: balões de pensamento reservam o espaço do
  rabinho na pilha — nada de um balão invadir as bolinhas do outro.
- Textura da nuvem regenerada com gomos MAIS PROFUNDOS e alternados
  (silhueta orgânica de nuvem de pensamento).

### Mantido (a pedido)
- **ADMIN LIBERA QUEM USA**: `/rp admin balao <jogador> on|off` +
  `/rp admin balaolist` — padrão do servidor continua sendo NINGUÉM usa até
  o admin liberar.

## [3.20.0] - 2026-09-11

### Melhorado
- **BALÕES 4x MAIS DETALHADOS**: textura da pílula ampliada para 256x128 com
  gradiente suave e brilho — visual caprichado no mundo.
- **NUVEM REFEITA (textura própria!)**: gomos fofos CONTORNADOS, com brilho —
  nada mais de bolinhas chapadas. A nuvem agora é fofo de verdade.
- **TRACEJADA RETA**: tracinhos retangulares retos e alinhados em volta do
  balão (sem mais "círculos" e sem descadinha torta).

### Corrigido
- **TECLADO DE EMOJIS 100% JOGÁVEL EM QUALQUER CLIENTE**: a grade agora é
  feita de **BOTÕES VANILLA** (o componente que sempre funcionou), cada emoji
  senta num **chip "██" da própria cor** (texto colorido — o mesmo motor das
  paletas ● que sempre renderizou) e a arte vem pela **fonte de bitmap**
  (a MESMA que desenha os emojis nos balões no mundo — comprovado nos prints
  com a coroa e o "?"). Tudo desenhado POR CIMA dos widgets, na ordem certa.
- Cartão de teste no topo da página: 3 cores (█) + 1 emoji — se eles
  aparecerem, os 64 também aparecem (mesmo motor).
- Preview do hover em **3x** com moldura de tinta.

### Técnico
- `StickerArt` (runs) removido; menu volta a usar a fonte rptag:stickers via
  drawInBatch — uma ÚNICA fonte de arte para mundo e menu.

## [3.19.0] - 2026-09-11

### Corrigido
- **BALÕES EMPILHADOS NÃO SE SOBREPÕEM MAIS**: o espaçamento da pilha era
  fixo (0.55 bloco) — mensagens longas (2-3 linhas) têm mais de 1 bloco de
  altura e um balão invadia o outro. Agora a pilha soma a **ALTURA REAL de
  cada balão** (linhas, linhas com emoji, e margem pros gomos/tinta das
  molduras) — três balões enormes continuam empilhados, um acima do outro,
  sem cruzar.
- **Respiro horizontal** para as molduras que estouram pra fora
  (Tracejada/Nuvem/Gibi) e para falas com emoji — o texto não encosta mais
  nos gomos da nuvem.

## [3.18.0] - 2026-09-11

### Corrigido
- **TECLADO DE EMOJIS REPROJETADO EM ALTO CONTRASTE**: células bem mais claras
  que o fundo (era quase invisível na escuridão do painel), cada emoji agora
  senta num **CHIP da própria cor** (fundo colorido — mesmo que a arte não
  apareça num cliente estranho, o seletor continua utilizável e bonito).
- **[✖] DE REMOVER SEMPRE VISÍVEL**: o adesivo livre da prévia agora tem uma
  caixinha vermelha [✖] grudada nele — clicou, tirou (não precisa mais caçar
  o hover).
- Contador honesto: "✨ Emojis (2/6 na fala · 1 no balão)".

### Diagnóstico
- A página de adesivos ganhou um **cartão de teste com 3 quadradinhos
  coloridos** (vermelho/verde/azul) no canto superior: se eles aparecerem,
  o menu renderiza fills normal — e os emojis (mesmo motor) também têm que
  aparecer. Se NEM eles aparecerem, o cliente está com jar antigo (conferir
  o número da versão no canto superior direito do menu).

## [3.17.0] - 2026-09-11

### Corrigido
- **MODO BALÃO AGORA DESLIGA DE VERDADE**: desligou (`/balao modo off` ou o
  botão ☾)? Suas falas vão SÓ pro chat e o balão que estava na cabeça SOME
  na hora (antes ele continuava aparecendo).
- **SÓ QUEM O ADMIN LIBERAR USA BALÃO**: o padrão do servidor agora é
  NINGUÉM — o admin libera com `/rp admin balao <jogador> on` (que também já
  liga o modo balão da pessoa) e revoga com `off`. `/rp admin balaolist`
  lista os liberados. Acabou o amigo usando balão sem permissão.
- **"MINHA COR" E "AUTO" COM ANEL DE SELEÇÃO HONESTO**: o contorno verde
  agora fica no botão que está ATIVO (antes marcava "Auto" sempre, parecendo
  que os outros não funcionavam). "Minha cor" re-aplica a última cor clicada
  na paleta da Borda.
- **MOLDURAS BEM DISTINTAS**: Cartum ficou mais grossa e a **Dupla** agora é
  linha fina + aro EXTERNO ESCURECIDO (duas voltas visíveis de verdade).
- **NUVEM MENOR E MAIS DELICADA**: gomos pequenos e colados (antes virava um
  balão gigante).

### Melhorado
- **EMOJIS DO MENU À PROVA DE QUALQUER CLIENTE**: os emojis agora são
  desenhados com o MESMO método dos painéis (GuiGraphics.fill, run a run da
  arte premium) — o caminho que comprovadamente funciona no seu cliente
  (fonte de bitmap e blit de textura falharam nos testes reais).
- **EMOJIS SEM FIM**: com um adesivo na mão, QUALQUER clique num lugar vazio
  da tela empilha mais um emoji na fala (botões continuam clicáveis; ESC ou
  botão direito soltam o adesivo).
- **PRÉVIA MAIOR (34px de altura)** com fala e emojis na posição real, e o
  adesivo livre VISÍVEL e PEGÁVEL de volta no menu.
- **BALÃO MAIS FOFO**: textura nova com gradiente suave (topo mais claro) e
  contorno anti-aliased — visual caprichado estilo QSMP.

### Técnico
- `OpenBubbleStylePayload` agora carrega o modo balão (o botão ☾ abre
  refletindo o estado real do servidor). Adesivos do menu gerados em código
  (`StickerArt`, runs da spritesheet — 64 emojis embutidos).

## [3.16.0] - 2026-09-11

### Corrigido
- **A CAUSA-MÃE dos emojis com fundo**: a spritesheet estava com FUNDO PRETO
  OPACO desde a 3.11 (bug do gerador de arte) — por isso os emojis tinham
  fundo, viravam blocos pretos no balão e "não apareciam" no menu (o blit
  desenhava o quadrado preto inteiro). A arte foi REGENERADA do zero a partir
  da versão transparente (3.10) com o mesmo acabamento premium (contorno,
  brilho, sombra e ponto de luz): **fundo 100% transparente**, provado por
  verificação pixel a pixel (zero pixels visíveis fora da forma).

### Melhorado
- Teclado: passando o mouse num emoji agora mostra um **preview GRANDE (32px)
  ao lado do nome** — dá pra ver certinho qual é antes de escolher.
- Slots vazios com "+" mais visível.

## [3.15.0] - 2026-09-11

### Corrigido
- **FIM DO MURO PRETO DE EMOJIS no balão**: desde a 3.11 o adesivo herda a
  cor da LETRA escolhida — com a letra preta, cada emoji virava um BLOCO
  PRETO sólido e fundia tudo num rabisco ilegível (e o adesivo do canto
  virava um quadradão preto). Agora o adesivo é **SEMPRE a arte colorida**
  (nunca é tingida) — o emoji aparece exatamente como você escolheu, e a
  posição dele volta a ser visível.
- **TECLADO DE EMOJIS QUE NÃO APARECIA**: os emojis do menu eram desenhados
  com drawInBatch (texto), que **não pinta em todos os clientes** — a grade
  ficava VAZIA (comprovado no print). Agora os emojis do menu são desenhados
  por **BLIT da spritesheet** — o MESMO caminho dos ícones do inventário,
  o mais robusto do jogo. Grade, slots de emoji, adesivo na mão e adesivo
  da prévia: todos com a arte de verdade, colorida, sempre.

### Técnico
- Novamente mudança SÓ no cliente (BubbleStyleScreen + 1 linha no renderer):
  zero diff de servidor/comandos (provado por git diff).

## [3.14.0] - 2026-09-11

### Mudado
- **PRÉVIA DO MENU AGORA É O BALÃO REAL**: acabou a "cerquinha de glifos" que
  ninguém entendia. A simulação do `/balao` desenha a MESMA pílula
  arredondada do mundo (mesma textura, mesmo rabicho), tingida com as cores
  do jogador, e mostra **as 6 molduras desenhadas de verdade** — clicou em
  Nuvem, vê a nuvem; clicou em Gibi, vê o balãozão de HQ com brilho; clicou
  em Tracejada, vê os tracinhos. A fala e os emojis ficam DENTRO da pílula,
  na posição real.
- Dica do topo agora explica: "Prévia REAL: é assim que seu balão aparece no
  jogo".
- Etiqueta da placa "🖼 Moldura" não é mais cortada pelos botões; fileiras
  respiram (botoes de moldura 143/161/179, adesivos 198, emojis 221, salvar
  244).

### Técnico
- Mudança SÓ no cliente (BubbleStyleScreen): zero diff de servidor/comandos
  (provado por git diff — a única linha fora do client é a versão).

## [3.13.0] - 2026-09-11

### Corrigido
- **FIM DO BUG DOS ADESIVOS REPETIDOS** (o muro de emojis do print!): com um
  adesivo na mão, QUALQUER clique fora do balão era engolido e virava "mais
  um emoji na fala" — inclusive os cliques NOS BOTÕES DE MOLDURA (por isso
  a borda parecia travada e o local do adesivo não escolhia).
- **FLUXO GUIADO**: agora, segurando um adesivo, a tela ACENDE duas zonas —
  o **balão** (verde: clique = colar o adesivo livre) e a **linha da fala**
  (dourada: clique = empilhar na mensagem, um clique por emoji). Qualquer
  clique FORA das zonas **solta** o adesivo e deixa os botões funcionarem.
  Dica no topo muda junto ("Clique NO BALÃO pra colar · na FALE pra
  empilhar · ESC solta").
- **FIM DO MURO DE EMOJIS no mundo**: os emojis da fala agora saem
  **ESPAÇADOS** entre si (cada um legível, nada de grade de adesivos) e um
  balão cheio de emojis **substitui a pilha** (não empilha mais muro).

### Mudado
- **MENU REDESENHADO EM PLACAS DE QUADRINHOS**: painel com moldura de tinta
  grossa, sombra dura e brilho (cartoon de verdade), **placas "🎨 Cores" e
  "🖼 Moldura"** com etiquetas douradas, faixa de título com sublinhado e
  slots de emoji com caixinha própria. O tema TODO do menu fica em 10
  constantes (TINTA/FUNDO/OURO/...) — fácil de alterar.
- Fileira do resumo hex removida (as paletas já mostram a seleção) — menu
  mais compacto e sem NENHUMA área de clique invadindo outra.

## [3.12.0] - 2026-09-10

### Adicionado
- **3 NOVAS MOLDURAS de balão** (agora são 6!): **▤ Tracejada** (tracinhos
  arredondados em toda a volta, com rabicho em descadinha), **▤ Nuvem**
  (gomos de nuvem fofos ao redor do balão inteiro) e **▤ Gibi** — o balãozão
  de HQ clássico do estilo Turma da Mônica: bem inflado, tinta grossa e
  **brilho gelatinoso** no recheio.
- A moldura escolhida é realçada com a caixa verde na fileira certa (as
  molduras agora ocupam 2 fileiras de botões).
- Bordas são só um número no estilo: **zero mudança de rede** — mundos e
  combinações antigas continuam funcionando (Clássica/Cartum/Dupla intactas).

## [3.11.0] - 2026-09-10

### Adicionado
- **ARTE DOS EMOJIS EM ALTA RESOLUÇÃO (16px)**: todos os 64 adesivos foram
  reprocessados com o acabamento dos mods famosos (padrão Discord/Emojiful):
  **contorno escuro de sticker, brilho de caramelo no topo, sombra de volume
  na base e ponto de luz**. Spritesheet ampliada para 128x128 e fonte de
  bitmap com glifos de 16px — os emojis ficam maiores e muito mais bonitos
  no mundo e no menu.
- **ANIMAÇÃO do balão estilo mods famosos** (padrão Chat Bubbles/
  TalkBubbles/ChatBubble): **pop-in com ressalto** (easeOutBack) ao nascer,
  **flutuação suave pra cima** enquanto o balão vive e **fade-out** suave no
  fim.
- **PILHA DE BALÕES**: até **3 balões por jogador** empilham — o mais novo
  fica perto da cabeça e os antigos sobem (nada de mensagens se apagando).

### Técnico
- `ClientBubbleCache` agora guarda uma pilha por jogador com o instante de
  nascimento de cada balão; `BubbleRenderer` desenha cada balão da pilha com
  a própria animação. Linhas com emoji ocupam 18px (glifo 16px). Texto puro
  segue EXATAMENTE a mesma geometria de antes (zero regressão visual).

## [3.10.0] - 2026-09-10

### Adicionado
- **VÁRIOS EMOJIS NA FALA**: com um adesivo na mão, clique FORA do balão da
  simulação para acrescentar ele à fala — um clique por emoji, **até 6**. Os
  emojis extra aparecem DENTRO do balão acompanhando o texto (arte colorida
  de verdade), e a simulação do `/balao` mostra o resultado exato. Fileira
  nova "✨ Emojis (n/6)" com slots: **✖/clique tira**, **+ abre o teclado**.
- **PÁGINA FOFA no teclado de adesivos**: 16 emojis novos desenhados à mão no
  mesmo estilo pixel-art — **Laço, Dinossauro, Planeta, Galáxia, Foguete,
  Arco-íris, Patinha, Borboleta, Chapéu de mago, Unicórnio, Raposa, Pinguim,
  Cogumelo, Fantasma, Rex e Varinha mágica** — pra fala carregar história e
  personalidade (fofo pra criançada!). O teclado agora tem **2 páginas**
  (Clássicos 48 + Fofos 16 = **64 adesivos**) com botão "✨ Fofos ▶ /
  ◀ Clássicos".
- Spritesheet ampliada para 64x80 e fonte de bitmap com 64 glifos
  (U+E000–U+E03F).

### Corrigido
- O título do painel não é mais coberto pelo cabeçalho do teclado de adesivos
  (arrastava desde a 3.8.0).

### Técnico
- `BubbleStyle` ganhou o campo `extras` (máx. 12 chars = 6 emojis), salvo no
  NBT do mundo (`Extras`) e transmitido nos 3 pacotes (abrir estilo, salvar
  estilo e balão de fala). Mundos antigos continuam funcionando (sem Extras =
  vazio). Canal de rede v2 mantido: cliente e servidor precisam do MESMO jar
  3.10.0 (jars diferentes são recusados com aviso na conexão).

## [3.9.0] - 2026-09-10

### Corrigido
- **EMOJIS VISÍVEIS NO TECLADO DE VERDADE**: os adesivos do menu agora são
  desenhados pelo MESMO método do balão no mundo (`drawInBatch` + fonte de
  bitmap, escala 2x) — o único caminho que comprovadamente funciona em todos
  os clientes testados. Escolha VENDO o emoji (bolinhas e números eram só
  fallback); o nome segue no rodapé e no tooltip.
- O adesivo colado aparece na simulação com a arte real, e o adesivo "na mão"
  segue o mouse com a arte também.

## [3.8.0] - 2026-09-10

### Corrigido
- **FONTE DO BALÃO LIMPA DE NOVO**: o contorno de tinta da 3.7 fundia as letras
  num borrão (cada halo desenhava a própria sombra = tinta dupla). Voltou a
  sombra vanilla clássica — legível e bonita — com o texto 20% maior que o
  original.
- **MENU 100% JOGÁVEL EM QUALQUER CLIENTE**: TODOS os elementos essenciais
  (paletas, adesivos, simulação do balão) agora são desenhados apenas com
  TEXTO do jogo (glifos ● e ▣, o mesmo motor da tag "● ʀᴘ" que sempre
  funcionou) + botões vanilla. Se aparece no chat, aparece no menu.
- **SIMULAÇÃO DO /balao refeita**: linha do chat real (`<Você> fala` com as
  cores) + balão em moldura ▣ com o rabicho ▼ e o adesivo na posição exata —
  o que você vê ali é o que aparece sobre a sua cabeça.

## [3.7.0] - 2026-09-10

### Corrigido
- **CAUSA RAIZ de "a cor não salva" entre amigos**: os pacotes mudaram de
  formato desde a 3.2 mas o canal de rede continuou na versão 1 — cliente novo
  em servidor velho conectava e **corrompia os saves silenciosamente**. O canal
  agora é **v2**: jars diferentes são RECUSADOS com aviso claro na conexão.
  SEMPRE atualize cliente e servidor juntos!
- **Menu 100% sem texturas e sem fontes especiais** (era isso que sumia em
  certas GPUs): adesivos agora são **bolinha de cor + número + NOME no tooltip**
  — desenhados só com primitivas que funcionam em qualquer cliente.
- **Layout testado automaticamente**: 22 verificações (ordem das linhas, sem
  sobreposição, hitbox == desenho, codecs simétricos) — a 3.6 tinha a dica
  escrita POR CIMA da paleta Dentro.
- **Letra do balão no mundo com CONTORNO DE TINTA** (halo escuro em volta de
  cada letra) + escala 12% maior: legível em qualquer cenário.

## [3.6.0] - 2026-09-10

### Adicionado
- **COMANDOS DE ADMIN para o balão**: `/rp admin balao <jogador> off` proíbe o
  jogador de usar balão (a fala dele volta só ao chat), `on` permite de volta e
  `balaolist` lista os proibidos. Fica salvo no mundo.
- **Menu redesenhado (bonito e legível)**: cabeçalho com título dourado e faixa
  de acento, SIMULAÇÃO DE CHAT com fonte GRANDE (escala 1.5x) e rodapé com
  quadradinhos das cores atuais + hex.
- **Paletas de 12 cores** por alvo (Dentro e Borda) — 1 clique pinta, sem
  arrastar nada. Letra: Auto + 8 cores.

### Corrigido
- **Spritesheet dos adesivos agora é POTÊNCIA DE 2 (64x64)** — texturas não-
  potência-de-2 podem falhar em certas GPUs/configurações gráficas, o que
  apagava os desenhos do menu (teclado vazio, pílula invisível).
- Layout compacto (224px) que cabe em GUI scale 3 e telas pequenas; painel
  sempre com margem (clamp), nada mais corta.
- Texto do balão no mundo ~12% maior (escala 0.028) — mais legível de longe.

## [3.5.0] - 2026-09-10

### Adicionado
- **SIMULAÇÃO DE CHAT AO VIVO no menu**: painel estilo chat com a linha
  `<Você> sua fala assim` (nome na cor do balão, fala na cor da letra) e o
  balão completo — reage NA HORA a fundo, borda, molde, letra e adesivo.
- **COR DA LETRA**: modo **Auto** (branco/preto conforme o fundo, contraste
  perfeito) ou 8 cores de chat à 1 clique. Salva no mundo, viaja pra todos e
  aparece na mensagem de confirmação ("· Letra #RRGGBB / auto").
- Rodapé da simulação com o resumo `#fundo · ▣ borda · ✎ letra`.

## [3.4.0] - 2026-09-10

### Adicionado
- **PALETAS DE 1 CLIQUE**: 8 cores prontas pra DENTRO e 8 pra BORDA, logo
  embaixo de cada seletor — clicou, pintou. Zero arrastar (pra quem o arrastar
  não estava funcionando). Arrastar continua disponível pra cor custom.
- **Diagnóstico de jar embutido**: a tela mostra a versão do CLIENTE (canto
  superior direito) e a mensagem de salvar mostra a versão do SERVIDOR
  ("💾 Salvo no servidor vX.Y.Z!"). Se diferirem, o jar do lado antigo precisa
  ser trocado — essa era a causa real de "nada muda".
- Teclado de adesivos com **imagens grandes (20px)** e ponto de cor de
  identidade em cada célula.

### Corrigido
- Tela compactada (240px) pra caber em telas 480p com GUI scale 2 — os botões
  de baixo (Salvar!) não são mais cortados.
- Texturas legadas não usadas removidas do jar.

## [3.3.0] - 2026-09-10

### Adicionado
- **2 seletores INDEPENDENTES**: "✦ Dentro" pinta o recheio e "▣ Borda" pinta
  a moldura — cada um com seu próprio quadrado e faixa de cores (fim de
  alternar alvo). Mexeu no da borda? Já vira PERSONALIZADA.
- **6 botões explícitos**: cor da borda (**▣ Auto / ▣ Minha cor / ▢ Sem
  borda**) e molde (**▤ Clássica / ▤ Cartum / ▤ Dupla**) — o ativo ganha
  moldura verde.
- **Mira de posicionamento**: ao colar/mover o adesivo, uma cruz segue o mouse
  e o canto do preview mostra a posição exata em **x,y %**.

### Corrigido
- **Emojis do editor finalmente visíveis SEMPRE**: os adesivos agora são
  desenhados como IMAGEM CRUA (blit da spritesheet), sem depender da fonte de
  bitmap no GUI — funciona em qualquer cliente.
- **Preview legível**: fundo xadrez atrás do balão — qualquer cor de dentro
  (até branca) contrasta; texto com sombra e hex ao vivo.
- Adesivo nunca mais cola cortado: posição limitada a 5–95% (sempre inteiro
  dentro do balão).

## [3.2.0] - 2026-09-10

### Adicionado
- **3 MOLDES de borda** para o jogador escolher: **Clássica** (linha de tinta
  fina), **Cartum** (grossa, capa de quadrinho) e **Dupla** (duas linhas com
  respiro). Salvo junto com o estilo.
- **Selo de versão** no canto da tela (ex.: "3.2.0") — se não aparecer, o jar
  do cliente é antigo (cliente e servidor precisam do MESMO jar novo).

### Corrigido
- **Emojis do teclado não apareciam na tela**: agora os adesivos do editor são
  desenhados pelo MESMO caminho do balão no mundo (fonte de imagem), idênticos
  de um lado pro outro.
- Mensagem de salvamento agora deixa claro que o estilo **fica salvo pra
  sempre** — sair e voltar mantém cores, adesivo, molde e duração.

## [3.1.0] - 2026-09-10

### Corrigido
- **"Coloquei 1 coração e apareceram 2"**: os adesivos antigos dos slots
  Antes/Meio/Depois continuavam sendo desenhados (fantasmas). Eles agora
  **migram sozinhos** para o adesivo livre único — 1 adesivo por balão.
- **Cor "não salvava"**: o botão Salvar ficava na ESQUERDA e o Cancelar na
  DIREITA (ao contrário do hábito do Minecraft) — era fácil cancelar tudo sem
  querer. Agora **✔ Salvar fica na direita** e o **ESC também salva**.

### Adicionado
- Botão **↺ Padrão** para restaurar o estilo original na hora.
- Com o adesivo na mão: clicar **fora do balão** ou **botão direito/ESC
  cancela** — sem colar sem querer e sem "comer" os cliques do seletor de cor.

## [3.0.0] - 2026-09-10

### Adicionado
- **ADESIVO LIVRE com o mouse**: o jogador pega o emoji e cola em QUALQUER
  ponto do balão — clique no adesivo, clique no lugar (ou arraste). Clica de
  novo pra reposicionar e clique direito pra tirar. A posição (% da largura e
  da altura) é salva junto com o estilo.

### Mudado
- **Balão com TEXTURA SUAVE**: adeus faixas empilhadas — a pílula agora é
  desenhada com textura arredondada anti-aliased (estilo app de mensagem),
  contorno de quadrinhos em toda a volta e rabicho curvado.
- **O preview da tela é o balão de verdade** — o que você vê é o que fica.
- Balão FULLBRIGHT: continua legível no escuro.

### Corrigido
- Estilos do balão NÃO eram recarregados ao reiniciar o servidor (cores e
  adesivos voltavam ao padrão) — agora persistem de verdade.

## [2.9.0] - 2026-09-10

### Mudado
- **Editor repensado (sem caminho sem saída)**: os 4 destinos de adesivo
  (Antes/Meio/Depois/Canto) ficam SEMPRE visíveis — clica no destino (fica
  verde) e depois no adesivo, ou arrasta. **Clique direito no destino TIRA**
  o adesivo (setinhas do teclado também trocam o destino).
- **Cores sem confusão**: cartões DENTRO e BORDA sempre visíveis mostrando as
  cores atuais — o seletor pinta o que estiver selecionado (moldura branca).
- **Borda em 3 estados claros**: Automática → Personalizada (sua cor) → Sem,
  tudo num botão que sempre diz o estado.

### Corrigido
- A cor escolhida na tela NUNCA mais é sobrescrita por valor antigo do mundo.
- Contorno do balão agora aparece em toda a volta (topo/fundo incluídos).

## [2.8.0] - 2026-09-10

### Mudado
- **Visual estilo QUADRINHOS**: pílula arredondada com recheio da cor de dentro,
  CONTORNO da cor da borda, sombra e rabicho — como o balão clássico dos quadrinhos.
- **Adesivo de CANTO**: além de Antes/Meio/Depois, o jogador pode arrastar um
  adesivo para o CANTO do balão (estilo reação de apps de mensagem).

### Corrigido
- Confirmação de salvamento agora mostra as cores escolhidas (dentro e borda),
  para o jogador ver exatamente o que ficou.

## [2.7.0] - 2026-09-10

### Adicionado
- **TECLADO DE ADESIVOS**: 48 adesivos coloridos em pixel-art (corações,
  estrela, fogo, gato, cachorro, coroa, ovo, dado e mais) — clique e ARRASTE
  o adesivo até Antes/Meio/Depois da frase, como um sticker de verdade.
- **CUSTOMIZAÇÃO TOTAL das cores**: o seletor agora escolhe a cor da parte
  de DENTRO **e** a cor da BORDA do balão (botão Alvo: Interior/Borda), com
  botão para remover a borda. A cor do texto se ajusta sozinha.

### Removido
- Fundos prontos (papel/noite/madeira etc.) — a cor de dentro agora é a que
  VOCÊ escolher no seletor.

## [2.6.0] - 2026-09-09

### Adicionado
- **12 ADESIVOS coloridos em pixel-art** (fonte de bitmap própria): coração,
  estrela, brilho, fogo, nota, raio, lua, flor, espada, ovo, diamante e coroa —
  são IMAGENS coloridas de verdade dentro do balão, não emotes preto e branco.
- **Barra de DURAÇÃO** na tela: o jogador escolhe de 2 a 20 segundos quanto
  tempo o texto fica visível sobre a cabeça.

### Corrigido
- **Quadrado do balão perfeito**: todas as posições agora em pixels inteiros
  (largura par e centralização arredondada) — sem meio-pixel borrado.

## [2.5.1] - 2026-09-09

### Corrigido
- **Menu de personalização embaçado**: o fundo padrão do 1.21 aplica o
  DESFOQUE do mundo por cima da tela aberta — borrava o painel inteiro.
  Agora a tela usa fundo OPACO próprio (nunca desfoque), com tema escuro
  nítido e biséis estilo vanilla.
- Botões de emoji não cortam mais o texto ("Depois" virou rótulo em cima
  do botão + botão mostra só o símbolo).

## [2.5.0] - 2026-09-09

### Adicionado
- **TELA de personalização com SELETOR DE COR de verdade** (`/balao`): arraste o
  mouse no quadrado de cores + faixa de arco-íris e escolha QUALQUER cor, com
  preview do balão ao vivo e código hex.

### Mudado
- **Visual do balão redesenhado**: cartão com cantos chanfrados, sombra, barra
  de destaque com a sua cor e gradiente real — tudo desenhado pelo próprio jogo.
- **Novo design da tag RP**: **● ʀᴘ** (ponto ciano) / **○ ᴏꜰꜰ ʀᴘ** (ponto cinza) —
  e ela aparece **no TAB também**, para todo mundo saber quem está em RP.
- **`/do` agora é SÓ PARA ADMIN** (descrição de ambiente vira ferramenta de narrador).
- Emojis: além de antes/depois, agora existe emoji do **MEIO** da mensagem.

### Removido
- `/balaoedit` e `/bolha` (tudo concentrado em `/balao`) e as personalizações
  por chat (`/cor`, `/balao cor`, `/balao borda`, `/balao fundo`, `/balao emoji`).
- Menu-baú da 2.4.0 (substituído pela tela com seletor de cor).

## [2.4.1] - 2026-09-09

### Corrigido
- **`/balaoedit` não abria nada**: faltava registrar a TELA do baú no cliente.
  Agora abre igual um baú de verdade (textura nativa do jogo).
- **Balão invisível no mundo**: o fundo agora é desenhado pelo PROPRIO JOGO —
  o mesmo mecanismo do fundo das nametags do Minecraft — e o rabicho é o
  caractere ▼ da fonte do jogo. Não existe mais NENHUM desenho customizado
  no mundo: impossível ficar invisível, impossível derrubar o cliente.

## [2.4.0] - 2026-09-09

### Adicionado
- **EDITOR do balão em inventário** (`/balaoedit` ou `/balao`): um baú de 6
  fileiras, 100% vanilla na tela — impossível de crashar pela GUI. Clique nas
  cores (centro e borda), troque o fundo, ligue/desligue o modo balão e limpe
  tudo. Fecha o baú = salva automaticamente.

### Mudado
- **Emojis agora por ARRASTAR**: pega o emoji na paleta e solta na caixinha
  Antes / Meio / Depois do texto (ou Shift+clique manda direto pro Antes).
  Novo: emoji no MEIO da mensagem!
- Personalização por comando de chat REMOVIDA (`/balao cor`, `/balao borda`,
  `/balao fundo`, `/balao emoji`, `/balao limpar`) — tudo agora no editor,
  mais fácil para os jogadores. `/balao modo on|off` continua.

### Corrigido
- **Crash do cliente ao desenhar o balao (raiz encontrada nos fontes do 1.21.1)**:
  o fundo agora usa BUFFER PROPRIO do mod e o flush acontece dentro da protecao
  de erros — qualquer problema desliga os baloes com aviso no log, o jogo NUNCA
  fecha. A emissao de vertices e identica a do vanilla (cor + UV + luz cheia) e
  usa a textura branca NATIVA do jogo (zero risco de falha de textura).

## [2.3.1] - 2026-09-09

### Corrigido
- **CRASH do cliente ao desenhar o balao** (reportado por teste real): o
  renderizador do balao agora e TOTALMENTE protegido — se qualquer erro
  acontecer ao desenhar, o balao e desligado ate reiniciar o jogo (com
  aviso no log) e o jogo **nunca mais fecha**. A tag RP, o chat e os
  comandos continuam funcionando normalmente.
- **Vertices do balao identicos aos do vanilla**: emissao de vertices agora
  e exatamente a mesma usada pelo texto do jogo (cor + UV + luz cheia),
  eliminando a divergencia que causava o problema.
- **`/balao cor` e `/balao borda` agora aceitam cores com `#`** (ex.:
  `/balao cor #FFD700`) — antes o `#` era rejeitado pelo parser do comando.

## [2.3.0] - 2026-09-09

### Corrigido
- **Fundo do balao invisivel no mundo** (bug critico da 2.2.0): os poligonos
  usavam RenderType de GUI, que nao renderiza no contexto de entidades —
  so o texto aparecia, quase ilegivel. Agora usa o render type de TEXTO
  (o mesmo das nametags), que funciona no mundo 3D. Fundo mais opaco (90%)
  e texto COM SOMBRA para leitura perfeita.

### Adicionado
- **Borda do balao** com cor personalizavel (`/balao borda <cor|off>`,
  botao na tela `/balao`), padrao quase-preta para destacar em qualquer cenario.

## [2.2.0] - 2026-09-09

### Adicionado
- **Modo balao** — para criancas, ovos e personagens sem voz: as falas do
  jogador aparecem SOMENTE no balao sobre a cabeca (sem texto no chat),
  para quem estiver perto.
  - `/balao modo on|off` — o proprio jogador liga/desliga
  - `/rp admin bolha <jogador> on|off` — admin ativa/desativa para outro jogador
- **Tela de personalizacao** (`/balao`) — abre uma GUI com preview ao vivo:
  - Sliders Vermelho/Verde/Azul + 12 cores rapidas
  - Emojis decorativos antes/depois do texto (botoes rapidos: ** ✦ ★ ♥ ⚔ ☾ ✿ ♪ ⚡** —
    ou digite os seus na caixa)
  - 7 fundos: Translucido, Escuro, Claro, Gradiente, **Papel**, **Noite** e
    **Madeira** (texturas exclusivas empacotadas no mod)
  - Salvo no servidor (persiste no mundo); texto com contraste automatico
- Comandos por texto (sem GUI): `/balao cor`, `/balao emoji`, `/balao fundo`, `/balao limpar`

## [2.1.0] - 2026-09-09

### Adicionado
- **Balões de fala (chat bubbles)** — o que o jogador digita aparece num balão
  arredondado com rabicho sobre a cabeça, com quebra de linha e tempo de
  exibição proporcional ao tamanho da mensagem. Ideal para ovos, crianças e
  quem não usa microfone. Gerado pelo servidor: todo mundo com o mod no
  cliente vê os balões de todos (chat local, `/g`, `/s`, `/w` e `/me`).
- **`/cor <cor>`** — cor pessoal de cada jogador: pinta a mensagem no chat E o
  fundo do próprio balão. Aceita nomes em português (`ciano`), nomes do
  Minecraft (`dark_aqua`) e hex (`#55FFFF`). Contraste do texto automático.
- **`/bolha on|off`** — desliga os próprios balões.
- **`/rp admin bolha on|off`** — liga/desliga balões do servidor inteiro.

## [2.0.0] - 2026-09-05

### Adicionado
- **RP Persona** — `/persona criar <nome>` (liga o RP junto), `/persona idade`,
  `/persona desc` (tooltip ao passar o mouse no nome), `/persona ver [jogador]`,
  `/persona off`. O nome do personagem substitui o nick no nametag, chat e TAB.
- **RP Chat** — chat comum agora e **local** (40 blocos) por padrao; `/g` global,
  `/s` grito (100 blocos, CAIXA ALTA), `/w` sussurro (5 blocos), `/me` acao,
  `/do` ambiente. Toggle: `/rp admin chatlocal on|off` (persistido no mundo).
- **RP Roll** — `/roll` (d20), `/roll d100`, `/roll 2d6+1 [motivo]` com
  decomposicao dos dados, anunciado por proximidade.
- **Lore Zones** — `/lorezone criar|texto|som|remover|listar` (admin): regioes
  que mostram titulo, subtítulo e som quando um jogador entra (1x por sessao).

### Corrigido
- Conflito do `/me` com o emote vanilla (remocao do comando vanilla na arvore).

## [1.2.1] - 2026-09-04

### Corrigido
- **Bug crítico**: a tag com "negrito matemático" Unicode (𝐑𝐏) era montada com
  cast para `char`, truncando códigos acima de U+FFFF — o jogo exibia sílabas
  coreanas no lugar da tag.

### Alterado
- Investigada a fonte do Minecraft 1.21.1 (Unifont 15.1): o bloco
  "Mathematical Bold" (U+1D400) **não existe** nela e mostraria `□□`.
- A tag agora usa **small capitals** confirmadas glifo a glifo no Unifont:
  **(ʀᴘ)** em ciano e **(ᴏꜰꜰ ʀᴘ)** em cinza — renderiza em qualquer cliente.

## [1.2.0] - 2026-09-03

### Adicionado
- Tag entre parênteses e com fonte diferente (tentativa inicial com 𝐑𝐏).
- Apenas a tag recebe cor; o nome do jogador fica na cor normal.

## [1.1.0] - 2026-09-03

### Adicionado
- Nametag redesenhado no cliente com pastilhas arredondadas ("bolinho"):
  nome em placa escura + tag colorida ao lado (verde/cinza na época).
- Proteção contra servidor sem o mod (não desenha tag sem sync).

## [1.0.0] - 2026-09-03

### Adicionado
- Tag `(RP)` / `(OFF RP)` no nome: chat, TAB e nametag.
- Comando `/rp` (alternar), `/rp on|off|status` para todos e
  `/rp set <jogador> on|off` para admins.
- Estado persistido no mundo (`SavedData`), padrão OFF RP.
- Sincronização servidor → cliente via payload próprio.
