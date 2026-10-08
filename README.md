# RP Tag

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62a552) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange) ![License](https://img.shields.io/badge/Licen%C3%A7a-MIT-blue)

**Mod para NeoForge 1.21.1** — o kit essencial de RP para servidores de lore: **persona** (vire outro personagem), **chat local** por proximidade com canais, **dados** para eventos, **lore zones** e a tag **● ʀᴘ / ○ ᴏꜰꜰ ʀᴘ** no nome e no TAB dos jogadores.

> 🌎 **Idioma / Language:** **Português (BR)** | [English](README.en.md)
>
> 📥 **Download:** baixe o `rptag-3.52.0.jar` na [aba de Releases](https://github.com/EduCafilista/rp-tag/releases) ou no [link direto](https://github.com/EduCafilista/rp-tag/raw/main/rptag-3.52.0.jar) (arquivo dentro do repositório).
>
> 🎮 **Novo por aqui?** Siga o [WALKTHROUGH.md](WALKTHROUGH.md) — guia passo a passo com tudo (instalação, comandos, customização e solução de problemas).

## ✨ O que ele faz

### 🎭 Persona — seja quem você quiser
- `/persona criar "Lord Aldric"` — o **nome do personagem substitui o nick** no nametag, chat e TAB
- `/persona idade 24` · `/persona desc "Ex-cavaleiro em busca de redenção"` — tooltip no nome (passe o mouse!)
- `/persona ver` — ficha completa · `/persona off` — volta ao nick
- Criar a persona **liga o RP automaticamente**

### 💬 Chat RP por proximidade
- O chat comum é **local** (40 blocos) — quem está longe não ouve
- `/s <msg>` **gritar** (100 blocos, CAIXA ALTA) · `/w <msg>` **sussurrar** (5 blocos)
- `/me <ação>` → `✦ Lord Aldric caminha pela floresta` · `/do <ambiente>` *(só admin)* → `✦ a porta range ao abrir`
- `/g <msg>` chat global · admins ligam/desligam o modo local com `/rp admin chatlocal on|off`

### 🗨️ Balões de fala (chat bubbles)
- O que você digita aparece num **balão arredondado com rabicho sobre a sua cabeça** —
  perfeito para **ovos, crianças e quem não usa microfone** (estilo QSMP)
- **Modo balão** — a fala vira **só o balão** (nada no chat!):
  `/balao modo on` para si · `/rp admin bolha <jogador> on` para o admin setar
- **MENU com SIMULAÇÃO DE CHAT AO VIVO**: linha `<Você> fala` + balão completo
  reagindo na hora a TUDO — fundo, borda, molde, cor da LETRA e adesivo ·
  **paletas de 12 cores à 1 clique em glifos de texto (funcionam em qualquer
  cliente)** · letra do balão limpa e 20% maior no mundo
- **Balão estilo QUADRINHOS com textura suave**: pílula arredondada de verdade
  (anti-aliased), contorno em toda a volta e rabicho — **tudo escolhido no
  MENU CARTOON de placas de quadrinhos** (molduras de tinta, etiquetas
  douradas, zonas-guia que acendem quando você pega um adesivo) (`/balao`)
  · **a prévia do menu é a PILULA REAL do mundo** (mesma textura arredondada,
  rabicho e as 6 molduras desenhadas de verdade — você VÊ a nuvem, os
  tracinhos e o gibi antes de salvar) ·
  **6 molduras com botões próprios**: Clássica, Cartum (grossa), Dupla,
  Tracejada (tracinhos retos), **Brilho** (halo suave ao redor do balão)
  **e Gibi** — o balãozão de HQ clássico, inflado com tinta grossa e brilho
  gelatinoso · *(a moldura de nuvem/pensamento com rabinho de bolinhas foi
  substituída pelo Brilho na 3.29.0 — a arte da nuvem continua no mod e dá
  pra voltar como uma 7ª moldura se quiser)* ·
  **2 seletores independentes** + **paletas de 1 clique** (8 cores prontas
  pra Dentro e 8 pra Borda — sem arrastar!) ·
  **cor da borda em 3 botões**: Auto / Minha cor / Sem · **cor da LETRA**:
  Auto (contraste sozinho) + 8 cores
- **ADESIVO LIVRE com o mouse**: 64 adesivos com a ARTE DE VERDADE no
  teclado, em **alta resolução (16px com contorno, brilho e sombra — o
  acabamento dos emojis dos mods famosos)**, em **2 páginas** — Clássicos
  (48) e **Fofos** (16: laço, dinossauro, planeta, galáxia, foguete,
  arco-íris, patinha, unicórnio, raposa, pinguim, fantasma, cogumelo...) —
  escolha vendo o emoji, **clique em QUALQUER ponto
  do balão** pra colar; **mira + % na tela** mostram exatamente onde ficou ·
  clique direito tira · **ESC ou ✔ Salvar** gravam tudo · **barra de duração**
  (2–20s)
- **VÁRIOS EMOJIS na fala**: com um adesivo na mão, **clique fora do balão**
  pra acrescentar ele à fala (um clique por emoji, **até 6**) — os emojis
  aparecem DENTRO do balão, do jeitinho que vão ficar no mundo · cada criança
  monta a combinação com a cara dela · slots ✨ na tela ("✖" tira, "+"
  abre o teclado)
- **ANIMAÇÃO estilo mods famosos**: o balão nasce com **pop-in com
  ressalto**, flutua suavemente pra cima enquanto você fala e some com
  **fade-out** suave · **PILHA de até 3 balões** por jogador — o mais novo
  perto da cabeça, os antigos sobem (igual Chat Bubbles/TalkBubbles)
- **ADMIN LIBERA QUEM USA** (padrão do servidor: ninguém): `/rp admin balao
  <jogador> on` libera + já liga o modo balão da pessoa · `off` revoga ·
  `/rp admin balaolist` lista os liberados · `/rp admin bolhas on|off` global
- **MODO BALÃO com controle real**: desligou (`/balao modo off` ou o botão ☾)?
  As falas voltam SÓ ao chat e o balão some NA HORA

### 🎲 Dados para eventos
- `/roll` (d20) · `/roll d100` · `/roll 2d6+1` — resultado anunciado por perto, com decomposição dos dados e motivo opcional

### 📜 Lore Zones
- Admins criam regiões com `/lorezone criar <id> <raio> <título>` que mostram **título épico na tela**, subtítulo e som quando um jogador entra (1x por sessão)
- **Trilha em LOOP**: `/lorezone musica <id> <som>` (ou `<id> intervalo <segundos> <som>`) toca uma música enquanto o jogador está na zona (para sozinha ao sair) — perfeita pra clima tenso
- **27 presets de som que tocam de verdade** (`/lorezone musicas`): caverna, coração do Warden, portal, trovão, vento das almas, dragão, discos… um som **substitui o outro** (nunca empilha) e som inválido é barrado na hora
- **Música CUSTOM**: `/lorezone musica <id> @<nome>` toca o seu `.ogg` (pasta `config/rptag/musicas/` do cliente) em loop — converta qualquer música com `yt-dlp -x --audio-format vorbis`
- **Música por LINK**: `/lorezone musica <id> https://site.com/tema.ogg` — os players baixam e tocam em loop sem fazer nada (`.ogg`/`.wav`; YouTube vira `.ogg` com yt-dlp)
- **Volume no lado do jogador**: som pontual = slider *Ambiente* · música = slider *Música* (Opções → Música e Sons)
- A história do servidor contada no próprio mapa

### 🏷️ Tag RP/OFF RP
- **● ʀᴘ** (ponto ciano) ou **○ ᴏꜰꜰ ʀᴘ** (ponto cinza) no nome e **também no TAB** — small capitals, colorindo só a tag
- Aparece no nametag, no chat e na TAB · `/rp` alterna, `/rp on|off|status`, `/rp set <jogador> on|off` (admin)
- **`/rp ajuda` lista TODOS os comandos com a função de cada um** — admins veem também a seção de comandos de admin

- **Tudo salvo no mundo** — sobrevive a relog, morte e reinício do servidor.
  Por padrão, todo mundo começa em **OFF RP**.
- **Sem dependências** — só precisa do NeoForge.

## 📥 Instalação

**Requisitos:** Minecraft 1.21.1 + NeoForge 21.1.x + Java 21

1. Instale o **NeoForge 1.21.1** no servidor.
2. Copie `rptag-3.50.0.jar` para a pasta `mods/` do **servidor**.
   → a tag no **chat** e na **TAB** já funciona pra todo mundo.
3. *(Opcional)* Jogadores que quiserem ver a tag **em cima da cabeça** colocam o
   jar também na pasta `mods/` do **cliente**.

## 🔧 Compilando do zero

Requisitos: **JDK 21** e internet.

```bash
./gradlew build
```

O jar sai em `build/libs/rptag-3.50.0.jar`.

## 🎨 Personalizar

- **Texto e cores da tag**: `src/main/java/dev/rptag/RPTags.java`
  - `TAG_ON` / `TAG_OFF` — o texto (parênteses e small caps são aplicados automaticamente)
  - `ChatFormatting.AQUA` (ciano do RP) e `ChatFormatting.GRAY` — troque pelas 16 cores do Minecraft
- **Estilo do nametag**: `src/main/java/dev/rptag/client/NameplateRenderer.java`
  - `BADGE_ENABLED = true` desenha a tag como pastilha arredondada ("bolinho") em vez de texto
- **Mensagens do comando**: `RPCommands.java` e `ServerEvents.java`
- **Versão do NeoForge**: `build.gradle`

## 🗂 Estrutura

```
src/main/java/dev/rptag/
  RPTagMod.java            # classe principal (@Mod)
  RPTags.java              # monta a tag ● ʀᴘ / ○ ᴏꜰꜰ ʀᴘ
  RPWorldData.java         # estado salvo no mundo (SavedData)
  SyncRPStatePayload.java  # pacote de sincronização servidor -> cliente
  ModNetworking.java       # registro do pacote
  ServerEvents.java        # chat, TAB, login, sincronização
  RPCommands.java          # comando /rp
  client/                  # nametag no cliente + cache de estados
```

## 📄 Licença

[MIT](LICENSE)
