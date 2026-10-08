# 🎮 Walkthrough — RP Tag do zero ao RP ativo

Guia passo a passo, do download até a tag aparecendo no jogo.

> 🌎 **Idioma / Language:** **Português (BR)** | [English](WALKTHROUGH.en.md)

---

## Passo 0 — O que você precisa

| Item | Onde conseguir |
|---|---|
| Minecraft **1.21.1** (Java Edition) | launcher oficial |
| **NeoForge 21.1.x** | https://neoforged.net/ → *Downloads* → versão 21.1.x |
| **Java 21** | https://adoptium.net/ (Temurin 21) |
| `rptag-3.42.0.jar` | a aba *Releases* deste repositório |

> ⚠️ A versão do mod acompanha a do Minecraft: o `rptag-3.42.0.jar` é para **1.21.1**. Outra versão do jogo precisa de recompilação.

---

## Passo 1 — Instalar no servidor

1. Instale o NeoForge no servidor (o instalador pergunta o caminho da pasta do servidor).
2. Na pasta do servidor, entre em `mods/`.
3. Copie o `rptag-3.42.0.jar` para dentro dela.
4. Inicie o servidor uma vez para gerar os arquivos (aceite o EULA no `eula.txt` com `eula=true`).

**Como saber se funcionou:** no log de boot procure a linha

```
RP Tag 2.0.0 (rptag)
```

Pronto — a partir daqui a tag já funciona no **chat** e na **TAB** para todos.

---

## Passo 2 — Instalar no cliente (opcional, para ver a tag na cabeça)

1. No launcher, crie/edite o perfil do NeoForge 1.21.1.
2. Abra a pasta do jogo (opção "abrindo a pasta do jogo" no launcher) e entre em `mods/`.
3. Copie o mesmo `rptag-3.42.0.jar` para lá.
4. Entre no servidor.

Sem o mod no cliente você ainda vê a tag no chat/TAB; com o mod, também vê **(ʀᴘ)** ou **(ᴏꜰꜰ ʀᴘ)** flutuando sobre a cabeça dos jogadores.

---

## Passo 3 — Usando em jogo

Entre no servidor com qualquer conta e teste:

| O que digitar | O que acontece |
|---|---|
| `/rp` | alterna: OFF RP → RP (e vice-versa) |
| `/rp status` | mostra o estado atual: `Modo RP atual: (ʀᴘ)` |
| `/rp on` | entra em RP direto |
| `/rp off` | sai do RP direto |
| `/rp set Steve off` | *(só OP/admin)* muda o estado de outro jogador |

**O que observar depois de `/rp`:**

- Mensagem de confirmação: `Modo RP ativado! Seu nome agora mostra (ʀᴘ).`
- **Chat:** sua próxima mensagem aparece como `Alex (ʀᴘ): oi` (ciano) ou `Steve (ᴏꜰꜰ ʀᴘ): oi` (cinza).
- **TAB:** seu nome na lista ganha a tag.
- **Nametag:** com o mod no cliente, a tag aparece sobre a cabeça.
- **Persistência:** deslogue, reinicie o servidor, morra... o estado continua salvo no mundo. Todo jogador novo começa em **OFF RP**.

**Para testar o admin:** no console ou com um OP, use `/rp set <nick> on` e peça para a pessoa olhar o próprio nome no TAB.

---

## Passo 4 — Compilando a partir do fonte

Para contribuir ou gerar seu próprio jar:

```bash
# requisitos: JDK 21 + internet
git clone https://github.com/SEU_USUARIO/rp-tag.git
cd rp-tag
./gradlew build          # (Windows: gradlew.bat build)
```

O jar sai em `build/libs/rptag-3.42.0.jar`. A primeira compilação demora alguns minutos (baixa e prepara o Minecraft automaticamente — você não precisa instalar nada do jogo).

---

## Passo 5 — Personalizando

### Mudar a cor da tag

Em `src/main/java/dev/rptag/RPTags.java`, método `tag(...)`:

```java
return inRp
        ? Component.literal(tagText(true)).withStyle(ChatFormatting.AQUA)  // ciano
        : Component.literal(tagText(false)).withStyle(ChatFormatting.GRAY); // cinza
```

Troque `AQUA`/`GRAY` por qualquer cor do enum: `DARK_AQUA`, `BLUE`, `YELLOW`, `GREEN`, `RED`, `WHITE`, `LIGHT_PURPLE`, `DARK_GREEN`...

### Mudar o texto da tag

No mesmo arquivo, topo da classe:

```java
public static final String TAG_ON  = "RP";      // vira (ʀᴘ)
public static final String TAG_OFF = "OFF RP";  // vira (ᴏꜰꜰ ʀᴘ)
```

Pode escrever em qualquer estilo — os parênteses e as small caps são aplicados automaticamente. Se quiser texto 100% normal (sem small caps), use letras minúsculas no `toSmallCaps(...)`... ou simplesmente remova a chamada.

### Reativar o nametag "bolinho" (pastilha arredondada)

Em `src/main/java/dev/rptag/client/NameplateRenderer.java`:

```java
public static final boolean BADGE_ENABLED = true;
```

### Mudar as mensagens do comando

Ficam em `RPCommands.java` (respostas do `/rp status` e do `/rp set`) e `ServerEvents.java` (confirmação de "Modo RP ativado/desativado").

Depois de qualquer mudança: `./gradlew build` e substitua o jar nos `mods/`.

---

## 🩺 Solução de problemas

| Sintoma | Causa provável | Solução |
|---|---|---|
| Tag aparece no chat mas não na cabeça | Mod ausente no cliente | Instale o jar também no `mods/` do cliente |
| Tag não aparece em lugar nenhum | Mod ausente no servidor | Instale no `mods/` do **servidor** (o essencial) |
| Aparecem quadradinhos `□□` | Cliente com resource pack que troca a fonte | Teste sem resource pack — as small caps usam a fonte padrão do jogo |
| `/rp set` diz "permissoes insuficientes" | Você não é OP | Peça OP (`op SeuNick` no console) ou use o console direto |
| Nomes duplicaram a tag depois de outro mod | Conflito raro de formatação | Reporte no repositório com o log |

---

## Mapa rápido do código

```
Servidor                          Cliente
─────────────────────────         ─────────────────────────────
RPCommands.java  → /rp            ClientPayloadHandler.java ← recebe estado
ServerEvents.java → chat/TAB      ClientRPStates.java       → cache
RPWorldData.java  → salva NBT    ClientEvents.java         → nome local
SyncRPStatePayload.java ──────────> (pacote servidor → cliente)
```

Bom RP! 🎭

---

## 🗨️ Balões de fala (novo na v2.3.0)

O que você digita no chat (local, `/g`, `/s`, `/w` ou `/me`) aparece num **balão
arredondado sobre a sua cabeça** — perfeito pra ovos, crianças e quem não usa microfone.

| Comando | Efeito |
|---|---|
| `/balao` | abre a TELA de personalização: seletor de cor (arrasta o mouse!), barra de destaque, fundos e emojis |
| `/rp admin bolha off` | *(admin)* desliga balões do servidor inteiro |

Cores disponíveis: branco, preto, cinza, cinzaescuro, vermelho, vermelhoescuro,
laranja, dourado, amarelo, verde, verdeescuro, ciano, azul, azulescuro, roxo, rosa/lilas.
O texto do balão escolhe preto ou branco sozinho, de acordo com a cor de fundo.

---

## 🥚 Modo balão e TELA de personalização (v2.5.0)

**Para ovos, crianças e personagens sem voz:** a fala vira **só o balão** — nada no chat.

| Comando | Efeito |
|---|---|
| `/balao` | abre a **TELA de personalização** (seletor de cor, preview ao vivo) |
| `/balao modo on` / `off` | liga/desliga o modo balão em si mesmo |
| `/rp admin bolha <jogador> on` | *(admin)* ativa o modo balão para outro jogador |
| `/rp admin balao <jogador> off` | *(admin)* proíbe o jogador de usar balão (fala volta só ao chat) |
| `/rp admin balao <jogador> on` | *(admin)* permite o balão de volta |
| `/rp admin balaolist` | *(admin)* lista quem está sem balão |
| `/rp admin bolhas off` | *(admin)* desliga balões do servidor inteiro |

### Como usar a tela (`/balao`)
- **SIMULAÇÃO DE CHAT AO VIVO** — a linha `<Você> sua fala assim` (nome na
  cor do balão, fala na cor da letra) + o balão em moldura com rabicho. Mudou
  QUALQUER coisa (fundo, borda, molde, letra, adesivo)? A simulação reage NA
  HORA. Rodapé com bolinhas das cores: `● #fundo ● borda ● letra`
- **Adesivo com o MOUSE**: `✦ Adesivos` → a ARTE de cada emoji aparece no
  teclado (2x) — **escolha vendo!** Clicou: gruda no mouse → **metade
  ESQUERDA do balão** = cola o adesivo (com X vermelho pra tirar) ·
  **metade DIREITA** = vira emoji da fala. Dá pra deixar **um emoji de cada
  lado, com figuras diferentes** (ex.: espada e escudo)
- **Pintar** — DOIS seletores independentes: **✦ Dentro** (esquerda) pinta o
  recheio e **▣ Borda** (direita) pinta a moldura. Embaixo de cada um tem uma
  **PALETA DE 1 CLIQUE** (8 cores prontas) — clicou, pintou, sem arrastar! Ou
  arraste nos quadrados pra qualquer cor custom (hex ao vivo no rótulo)
- **Borda** — escolha a cor na paleta "Borda:" e use o botão **▢ Sem borda /
  ▣ Com borda** pra tirar ou repor de uma vez (o ativo fica com moldura verde)
- **Molde** — 3 BOTÕES de estilo: **▤ Clássica** (linha fina) · **▤ Cartum**
  (grossa, capa de quadrinho) · **▤ Dupla** (duas linhas com respiro)
- **✎ Letra** — **Auto** (branco ou preto conforme o fundo, contraste sempre
  perfeito) ou 8 cores de chat à 1 clique — a fala no balão muda na hora
- **⏱ Duração** — ARRASTA a barra e escolhe 2 a 20 segundos de exibição
- **☾ Modo balão** — liga/desliga ali mesmo
- **✔ Salvar** (botão da DIREITA, como no Minecraft) ou **ESC** salvam —
  nunca mais perder a customização · **↺ Padrão** restaura o original
- **Fica salvo PRA SEMPRE**: saiu do servidor, voltou — cores, adesivo, molde
  e duração continuam lá (salvos no arquivo do mundo)
- **DIAGNÓSTICO DE JAR**: o canto superior direito da tela mostra a versão
  do CLIENTE (ex.: "v3.4.0") e a mensagem ao salvar mostra a versão do
  SERVIDOR ("💾 Salvo no servidor v3.4.0!"). As duas têm que ser IGUAIS — se
  diferirem, troque o jar do lado antigo (cliente E servidor sempre juntos)
- Depois é só **falar no chat** para ver o balão ✨

---

## 📜 Lore Zones (regiões com história)

O admin marca regiões do mapa que reagem quando um jogador entra: título
épico na tela, subtítulo, som — e agora até **música em loop**.

| Comando | Efeito |
|---|---|
| `/lorezone criar <id> <raio> <título>` | cria a zona onde você está (subtítulo vem do texto) |
| `/lorezone texto <id> <texto>` | define o subtítulo da zona |
| `/lorezone som <id> <som>` | som 1x ao entrar (ex.: `minecraft:ambient.cave`) |
| `/lorezone musica <id> <som>` (ou `<id> intervalo <segundos> <som>`) | **trilha em LOOP** enquanto estiver na zona — reinicia a cada `intervalo` segundos (10–600, padrão 45) e **para ao sair** |
| `/lorezone listar` | lista as zonas |
| `/lorezone remover <id>` | apaga a zona |

Sons tensos que funcionam bem: `minecraft:ambient.cave`,
`minecraft:entity.warden.heartbeat`, `minecraft:music.overworld.deep_dark`,
`minecraft:music_disc.13`, `minecraft:music_disc.11`.

---

## 😱 Efeitos de RP: MEDO e avisos

| Comando | Efeito |
|---|---|
| `/rp admin medo <jogador> [segundos]` | a tela do jogador **ESCONDE** sob um véu azul-noite que pulsa, com vinheta escura, carinha piscando no HUD e **tremor de câmera** — pânico de verdade |
| `/rp admin avisar <jogador> <texto>` | texto GRANDE na tela, cores com `&` (`&c` vermelho, `&6` laranja, `&l` negrito…) |
| `/effect give <jogador> rptag:medo <s>` | o mesmo medo via comando vanilla |

A mensagem de "está com medo" aparece **só para o admin** — o jogador sente
o efeito sem saber quem mandou. E na 3.49 o balão-nuvem (estilo pensamento
do Turma da Mônica) ganhou **bolas circulares HD de verdade**, sem quinas.


---

## 🎵 Música CUSTOM nas Lore Zones (3.50.0)

Qualquer música sua pode tocar numa zona — inclusive uma de vídeo do YouTube.

### Passo a passo
1. **Converta** a música pra OGG Vorbis (um comando, com o yt-dlp instalado):
   `yt-dlp -x --audio-format vorbis "https://youtube.com/watch?v=..."`
2. **Jogue o .ogg na pasta** `config/rptag/musicas/` do **CLIENTE** (crie a pasta
   se não existir) e renomeie pra um nome simples: `tenso.ogg`
3. **Aplique na zona**: `/lorezone musica <id> @tenso`
   — a música toca em LOOP enquanto o jogador estiver na zona e **para na hora
   que ele sai**. Cada jogador precisa do arquivo no próprio cliente.

### Sons prontos (presets — não precisa decorar minecraft:...)
`/lorezone som <id> caverna` · `/lorezone musica <id> coracao`

| Preset | Som |
|---|---|
| `caverna` | minecraft:ambient.cave |
| `coracao` | minecraft:entity.warden.heartbeat |
| `warden` | minecraft:entity.warden.agitated |
| `deepdark` | minecraft:music.overworld.deep_dark |
| `disc13` / `disc11` / `disc5` | discos assustadores |
| `mood` / `almas` | mood do basalto / vale das almas |
| `pigstep` | minecraft:music_disc.pigstep |

`/lorezone musicas` lista tudo no jogo. E agora **sair e voltar na zona mostra
a lore de novo** (título + som = 1x por visita).

## 📖 Lista de comandos dentro do jogo

Não decore nada: **`/rp ajuda`** lista todos os comandos com a função de cada
um. Quem é admin vê também a seção SO ADMINS (medo, avisar, lorezone, chat
local, balão...). `/lorezone musicas` lista os sons e músicas disponíveis.

### Rabicho novo e balão Quadrado inteiro (3.51.0)
- O rabinho do balão ficou **menor e curvado** (com contorno da moldura) —
  nada de triângulo colorido gigante.
- Na moldura **▣ Quadrada**, o rabicho vira um **retangulinho com contorno**:
  o balão fica quadrado AO TODO (corpo, recheio e rabinho).

### Medo com SHADERS + ícone é o PNG enviado (3.54.0)
- O medo (véu + vinheta + ícone) agora é uma **camada oficial de HUD** —
  roda depois do shader, então **aparece com Iris/shaderpacks** também.
- Véu reforçado (208) e ícone = a carinha da foto (64x64).

### Ajuste fino do rabicho (3.56.1)
- O rabicho ficou DA MESMA COR EXATA do balão (mesma janela de texels da
  pílula — fim da diferença sutil de tom que dava "cara de remendo").
- `/rp ajuda` sem a linha amarela do WALKTHROUGH.

### Balões reescritos (3.56.0)
- **Mais palavras = mais balão pra cima**: linhas equilibradas, balão sempre
  proporcionado (fim da tirinha gigante).
- **Rabicho pixel em degraus** (com contorno) em TODAS as molduras — nem
  quadrado, nem triângulo colorido. A prévia do `/balao` usa os mesmos
  degraus e também quebra a fala em linhas.

### Como a Lore Zone funciona (3.55.0)
- **Raio = ESFERA**: `/lorezone criar vila 10` cria uma zona que pega
  **10 blocos para cada lado de onde você estava** (diâmetro 20, com altura
  também — é esfera, não cilindro).
- **Som 1x**: `/lorezone musica <id> 1x <som>` toca uma única vez quando o
  jogador ENTRA (sem loop). Com `intervalo <s>` ela repete em loop.
- **Musica do SERVIDOR pra todo mundo**: `/lorezone baixar <link .ogg/.wav>`
  baixa pra `config/rptag/musicas/` do servidor ("download iniciado... /
  concluído!") — e o mod **entrega o arquivo pros jogadores sozinho** (quem
  não tem, recebe os bytes e toca na hora).

### X vermelho de verdade + teste pelo chat (3.55.0)
- O X dos emojis ficou grande e a tooltip "tirar Morango" não trava mais na
  tela (o nome vai na linha do título).
- `/balao teste` saiu: **escreva no chat** e abra o `/balao` — a prévia
  mostra a sua última fala real.
- `/roll +3` = especialidade (1d20+3).
- O "blur" quadrado na tela era o fundo do ícone do medo — agora dissolve.

### Ícone do medo + /effect give funciona (3.53.0)
- O efeito `rptag:medo` ganhou **ícone próprio** (a carinha amarela
  assustada) no HUD, igual Veneno/Wither.
- `/effect give <jogador> rptag:medo <segundos>` agora dispara o MEDO
  COMPLETO (tela esconde + treme até o efeito acabar) — antes quem recebia
  só o efeito ficava normal.

### Sons de verdade + um substitui o outro (3.52.0)
- Os presets de som (caverna, alma, coração…) **tocam de verdade agora** — o
  som sai do registro do jogo, igual ao `/playsound`.
- Novos presets: `portal`, `portalviagem`, `nether`, `vento`, `dragao`,
  `ghast`, `creeper`, `trovao`, `chuva`, `fogo`, `agua`, `sino`, `outros`,
  `relic`, `creator`, `maldicao` e `nenhum` (limpa o som). `/lorezone musicas`
  lista todos (27).
- Um som **substitui o outro** ao re-entrar/trocar de zona — nunca empilha.
- **Música por LINK direto**: `/lorezone musica <id> https://site.com/tema.ogg`
  — os players baixam e tocam em loop sozinhos. **YouTube não toca direto**:
  o comando te dá o `yt-dlp -x --audio-format vorbis "URL"` pronto pra copiar.
- **Volume**: quem quer ouvir menos/mais ajusta Opções → Música e Sons →
  **Ambiente** (sons da zona) e **Música** (trilhas/discos).
- Na tela `/balao`: X vermelho maior, mini-X em cada emoji da fala e o
  adesivo "na mão" solta ao clicar fora do balão (fim do morango fantasma).
