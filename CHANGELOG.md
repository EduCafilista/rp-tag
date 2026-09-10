# Changelog

Todas as mudanças notáveis deste mod são documentadas aqui.

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
