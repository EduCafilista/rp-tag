# Changelog

Todas as mudanças notáveis deste mod são documentadas aqui.

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
