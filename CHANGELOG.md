# Changelog

Todas as mudanças notáveis deste mod são documentadas aqui.

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
