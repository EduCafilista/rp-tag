# ENTREGA — Guia pra continuar o RP Tag

Olá! Este documento é o mapa do projeto pra quem vai dar continuidade.

- **Versão atual: 4.2.0** — histórico completo no `CHANGELOG.md`
- **Diário de bordo técnico: `WALKTHROUGH.md`** (decisões, receitas de build/teste, o que foi reprovado e por quê) — **leia ele depois deste**
- Minecraft **1.21.1** + **NeoForge 21.1.x** + **Java 21**
- Repo público: `github.com/EduCafilista/rp-tag`

> ⚠️ **Regra de publicação**: só a **versão mais recente** fica no GitHub.
> A cada ciclo, publique a release nova e **apague a release + tag anteriores**
> (o histórico de código continua na branch `main`). Detalhes no `PUBLICAR.md`.

---

## 1. O que o mod faz hoje

### 🏷️ Identidade RP
- **Tag RP**: `ʀᴘ` (ciano) / `ᴏꜰꜰ ʀᴘ` (cinza) em small caps no **chat**, **nametag**
  e **TAB** (o TAB atualiza na hora ao ligar/desligar).
- **Persona**: `/persona criar <nome>`, `/persona idade <n>`, `/persona desc <texto>`,
  `/persona ver [jogador]`, `/persona off` — ficha RP ao passar o mouse no nome.
- **Dados**: `/roll` (rolagem de dados no chat).

### 💬 Chat e balões
- **Canais RP**: `/g` (global), `/s` (grito), `/w` (sussurro), `/me` e `/do`
  (ações — só no chat RP, **nunca** viram balão; quem está OFF RP na região não vê).
- **Balão de fala** acima da cabeça: 6 molduras (Clássica, Cartum, Dupla, Costura,
  Brilho, Gibi), cor/borda/letra/duração na tela `/balao`, 80 emojis como **emblema
  na borda**, pilha de até 3 sem sobreposição, billboard, legível de longe,
  quebra de linha equilibrada, **typewriter opcional por jogador**.
- **Balão é privilégio de admin** (default: ninguém tem; `/rp admin balao <jogador> on|off`).

### 🎭 Medo
- `/rp admin medo <jogador> [segundos]` — efeito `rptag:medo`: véu azul-noite que
  **esconde** a tela, tremida de câmera, ícone no HUD. Teto de 30 s.

### 🎬 Avisos cinematográficos (a parte mais visível do mod)
`/rp admin avisar <jogador|@a> <estilo> <mensagem>` — 12 estilos:

| # | Estilo | # | Estilo |
|---|---|---|---|
| 0 | Clássico (texto) | 6 | Gelo — "Geada Eterna" |
| 1 | Pesadelo — minimalista | 7 | Divino — celestial |
| 2 | Rúnico — "Tradução Arcana" | 8 | Void — "Queda Sem Fundo" |
| 3 | Real (texto) | 9 | Sangue — "Sangria" |
| 4 | Morte — "Linha Reta" | 10 | Demoníaco — "Chamado do Abismo" |
| 5 | Inferno — "Fornalha" | 11 | Pesadelo 2 — "Sono Sem Descanso" |

Todos os FX seguem o mesmo padrão: **título "AVISO"** grande, letra por letra, com a
**MENSAGEM embaixo do título** (`cy + 25*k`). Nenhum FX redimensiona o título.

### 🎞️ Avisos em VÍDEO renderizados no Blender — 4.2.0
Os avisos estão sendo refeitos como **vídeo** (piloto: **Morte "Linha Reta"**; os
outros 11 seguem procedurais). O vídeo é o fundo; o **texto e os sons continuam no
código**. Qualquer falha (sem vídeo, quadro corrompido, PC lento) → o FX procedural
assume **no mesmo instante**. Jogador desliga em `config/rptag/cliente.properties`
(`fxVideo=false`) ou com o comando do cliente `/rpfx status|on|off`.
Pipeline das cenas em `arte/README.md`; spec e plano em `docs/superpowers/`.

### 🧭 Lore zones
- `/lorezone criar <id> <raio>` — cria uma zona esférica no lugar onde você está.
- `/lorezone texto <id> <mensagem>` e `/lorezone aviso <id> <estilo>`
  — o aviso que dispara pra quem entra.
- `/lorezone som <id> <preset|som>` e `/lorezone musica <id> <arquivo|intervalo|unica>`
  — trilha da zona (loop com **tempo máximo**; `unica` = só na primeira entrada).
- **Aviso único por jogador**: cada um vê o aviso **uma vez** (persistente);
  `/lorezone avisos reset <jogador>` reexibe.
- `/lorezone listar`, `/lorezone remover <id>`.

### 🧱 Barreira da lorezone (só admin) — 4.0.0
- `/lorezone mostrar <id> [segundos]` — desenha **na sua tela**: arame ciano da
  esfera + **paredes amarelas** da faixa vertical + linha central laranja.
- `/lorezone esconder` — some.
- `/lorezone varinha` — item `rptag:lorezone_wand`: **clique direito** mostra a
  barreira da zona mais próxima (≤256 blocos); **shift + clique** esconde.
- O pacote vai **só pra quem executou**: jogador comum nunca vê.

### ⛏️ Faixa vertical (o caso da caverna) — 4.0.0
- `/lorezone vertical <id> <minY> <maxY>` — a zona **só dispara entre esses Y**.
  Evento na caverna: quem está no topo **não recebe** o aviso; só ao descer.
- `/lorezone vertical <id> auto` — volta a valer a esfera inteira.
- Zonas criadas antes continuam funcionando (esfera completa).

### ❤️ Vidas canônicas
- `/rp admin canon on|off` — liga o modo no **servidor todo** (banner na tela, sem chat).
- Cada jogador começa com **5 vidas**. Quem morrer em modo canon passa pelo ritual:
  **strondo → congelado 5 s → aviso de morte (estilo 4) → áudio `canon_death`**.
- **Anti-chain**: depois de perder uma vida, 5 min de proteção (morrer seguido não acumula).
- **Última vida**: "TOME CUIDADO" + **medo absurdo** de 30 s.
- **Morte fora do RP não conta (4.1.0)**: se quem matou estava **OFF RP**, a vítima
  **não perde vida** — sem ritual, sem aviso, sem som. Vale só quando há matador
  jogador; mob/queda/lava/fogo/`/kill` continuam contando.
- **Coração Canônico 3D**: bloco/item decorativo (só admin dá com `/give`,
  `shift + clique direito` = +1 vida), animação de batida + flutuação.

### 🛠️ Utilidade
- `/balao teste <frase>`, `/rp status` (diagnóstico), `/rp ajuda`, `/rp admin ajuda`.

---

## 2. Como buildar

### Opção A — Gradle (o jeito normal)
1. Instale o **JDK 21** (Temurin).
2. `./gradlew build` — o jar sai em `build/libs/rptag-4.2.0.jar` (roda também os testes JUnit).
3. Primeiro build demora (~5–15 min, descompacta/decompila o Minecraft) e pede
   ~4 GB de RAM livres. Depois é rápido (cache).
4. Pra testar: `./gradlew runServer` (aceite o eula em `run/eula.txt`) ou
   `./gradlew runClient`.

### Opção B — compilar só com javac (usado no ambiente de entrega)
Receita completa e testada no `WALKTHROUGH.md` (seção **TOOLCHAIN**). Resumo:
1. JDK 21 do Adoptium;
2. installer do NeoForge → `--installServer` e `--installClient`;
   ⚠️ no cliente é preciso um **`launcher_profiles.json` falso** antes;
3. classpath = neoforge-client + client-srg + neoforge-universal + server-srg +
   libs das duas árvores + **lwjgl-openal/lwjgl do Maven Central**;
4. `javac --release 21 -encoding UTF-8 -proc:none -cp "$(cat cp.txt)" -d build/classes @sources.txt`.

> ⚠️ **Sempre apague o jar antigo** da pasta `mods` — no **servidor e no cliente**.
> Jar duplicado é a causa nº 1 de "o mod não mudou nada".

---

## 3. Arquitetura (mapa rápido)

### Raiz (`dev/rptag/`)
| Arquivo | Papel |
|---|---|
| `RPTagMod.java` | Registro do mod, `VERSION`, itens (varinha, coração), sons |
| `RPTags.java` | Texto/cores da tag (`ʀᴘ` / `ᴏꜰꜰ ʀᴘ`), small caps |
| `RPWorldData.java` | **Persistência NBT** (tag RP, persona, chatLocal, balões, vidas, estilo, typewriter) |
| `ServerEvents.java` | Estado RP/persona, nomes no chat/TAB |
| `RPChat.java` | Canais RP + `spawnBubble` (gates + anti-flood 250 ms) |
| `ChatCommands.java` | `/g /s /w /me /do` |
| `RPCommands.java` | `/rp` + `/rp admin` (avisos, medo, canon, balões) |
| `PersonaCommands.java` / `RollCommands.java` | `/persona`, `/roll` |
| `CanonLife.java` | **Vidas canônicas**: respawn, congelamento, ritual, morte OFF RP |
| `CanonCoracaoBlock(+Entity).java` | Coração canônico 3D (bloco + item) |
| `LoreZones.java` | Zonas (esfera + **faixa vertical minY/maxY**), aviso único, som/música, aviso na entrada |
| `LoreZoneCommands.java` | `/lorezone ...` (criar, vertical, mostrar, esconder, varinha, avisos, som, musica, listar) |
| `LorezoneWand.java` | Item da varinha (permissão 2, clique direito, dedupe 250 ms) |
| `MusicSync.java` | Envio de arquivos de música pro cliente em pedaços |
| `RPEffects.java` | Efeito `rptag:medo` |
| `Stickers.java` | 80 emojis Noto (atlas POT, codepoints 0xE000+) |
| `*Payload.java` | Rede: `ScreenTextPayload`, `FearPayload`, `ChatBubblePayload`, `ZoneShowPayload`, `LorezoneMusicPayload`, `MusicFile*Payload`, `CanonBannerPayload`, `PersonaSyncPayload`, `SyncRPStatePayload`, `TypewriterPayload`, etc. |

### `client/`
| Arquivo | Papel |
|---|---|
| `AvisoFxUtil.java` | **Motor compartilhado** dos FX: `Buf` (tri/quad/rect/seg/circ/anel/vinheta/brilho), `preparar/normal/aditivo`, `tocar`, helpers de cor e easing |
| `*AvisoFx.java` (9) | `Divino`, `Demoniaco`, `Sangue`, `Void`, `Pesadelo`, `PesadeloAntigo`, `Inferno`, `Gelo`, `Runico` |
| `MorteAvisoFx.java` | FX "Linha Reta" (estilo 4) — desde a 4.2.0 compõe cinza → **vídeo** → texto/sons, com fallback procedural |
| `VideoFx.java` / `VideoFrameLoader.java` | **Player dos avisos em vídeo** (4.2.0): quadro pelo relógio, uma textura 1280x720, thread `rptag-fx-loader` |
| `VideoEstado` / `FxTimeline` / `FxCor` / `FxJpeg` / `MorteTempos` | Java puro com JUnit: estados do player, `fx.json`, cor+alfa, leitura estrita do JPEG, tempos do MORTE |
| `FxConfig.java` / `FxComando.java` | `config/rptag/cliente.properties` (`fxVideo`) + `/rpfx status\|on\|off` |
| `ClientEffects.java` | **Central dos avisos**: roteia o estilo pro FX, véu do canon, banner, áudio da lore |
| `ClientGuiLayers.java` | Registra as camadas com `registerAboveAll` (com shaders, por cima de tudo) |
| `BubbleRenderer.java` | Coração visual do balão (pílula = tamanho exato do texto; âncora na base) |
| `BubbleStyleScreen.java` | Tela `/balao` (menu cartoon, preview igual ao mundo, emoji picker) |
| `ClientBubbleCache.java` / `BubbleSink.java` / `BubbleGeometry.java` / `BubbleBackgrounds.java` | Pilha, fila, geometria e cores derivadas dos balões |
| `ZoneVisualizer.java` | **Barreira** da lorezone (`RenderLevelStageEvent`, `RenderType.lines()`) — 4.0.0 |
| `CanonCoracaoRenderer.java` | Coração 3D (batida + flutuação + balanço) |
| `MusicaCustom.java` | Música custom por streaming/arquivo, com **tempo máximo** |
| `ClientPayloadHandler.java` | Handlers de todos os pacotes no cliente |
| `NameplateRenderer.java` / `ClientRenderers.java` / `ClientEvents.java` | Nametag, renderers, eventos |

---

## 4. REGRAS DE OURO (aprovadas/reprovadas em batalha — **não reverter!**)

1. **Emoji nunca via fonte na fala** = blocos pretos. Só via quads do atlas.
2. **Pílula do balão = tamanho EXATO do texto.** Nunca criar faixa/linha pro emoji.
3. **Âncora do balão**: base em `yP1 = 0`, cresce pra cima (eixo Y invertido).
4. **Adesivo/emote = emblema na BORDA**, metade pra fora — nunca sobre letras.
5. **Sticker no mundo = bitmap COLORIDO**, nunca tintado.
6. **Balão é privilégio de admin**; `/balao modo off` some na hora.
7. **Render nunca crasha**: `catch (Throwable)` com auto-desliga da sessão.
8. **Texturas POT sempre** (potência de 2); `dashed.png` não esticar.
9. **`/me` e `/do` são ação no chat** — não viram balão.
10. **Avisos**: título "AVISO" **letra por letra**, **sem auto-ajuste no título**;
    a mensagem **sempre embaixo**, `translate(cx - w*S2/2, cy + 25*k)`.
    `ty - 12*k` (mensagem acima) é **proibido**.
11. **Medo/AVISO com shaders** = `registerAboveAll` (nunca `RenderGuiEvent.Post`).
12. **`renderBackground` vazio** (blur) nos menus — **nunca remover**.
13. **Nunca portar telemetria** de referências externas.
14. **Testes antes de entregar**: `check.py` (337 checks) + `./gradlew test` (JUnit) + smoke do servidor por RCON.
15. **Vídeo dos avisos**: o texto **nunca** vai no vídeo; decodificar pelo **raster**
    (nunca `getRGB`, ~35 ms/quadro); JPEG com aviso do decodificador = **erro** (fallback).

---

## 5. Ciclo de entrega (o que fazer em toda versão)

1. Bump da versão em **3 lugares**: `RPTagMod.VERSION`, `build.gradle`, `neoforge.mods.toml`
   (e o log de boot no `ClientEffects`).
2. Atualizar **`CHANGELOG.md`** e **`WALKTHROUGH.md`** (o walkthrough é a memória do projeto:
   anote o que foi feito, o que falhou e as receitas novas).
3. `python3 check.py` → tem que terminar **0 FAIL**; `./gradlew test` verde.
4. Compilar → jar em `rptag-<versão>.jar`; conferir `unzip -l | grep -c .class`.
5. Smoke: subir o servidor, testar os comandos por RCON, `grep -c "ERROR\|FATAL"` = 0.
6. Commit + push (ver `PUBLICAR.md`; o PAT vai **inline** na URL, nunca em arquivo).
7. Release com o jar + `make_latest` + **apagar release/tag anteriores**.
8. **Nenhum jar no repositório** (desde a 4.2.0; o `.gitignore` já ignora `*.jar`):
   com os vídeos o jar passa de 100 MB, o limite do GitHub no git. Jar **só na Release**.

---

## 6. Roadmap / ideias em aberto

- **FX ignoram a duração configurada**: todos têm `DUR = 6.0f` fixo, mas a lorezone
  aceita 1–60 s — avisos longos ficam com tela vazia no fim (**correção pendente**).
- **Altura do título varia por estilo** (`cy` = 0.42h / 0.45h / 0.5h) — padronizar.
- **Flag `reduzido`** existe em todos os FX (modo sem flicker) mas **nunca é ligada**:
  virar opção por jogador.
- Estilos 0 (clássico) e 3 (real) são só texto — o "real" pede um tratamento
  cinematográfico (grão/VHS), mas isso é **design novo** (não fazer sem pedido).
- Barreira da lorezone é estática (fade-in, pulso nas paredes, rótulo com a faixa Y).
- Portais da Lore (segundo mod do repo) está **EM PAUSA** — não mexer sem pedido.

---

## 7. Créditos e licenças

- Emojis: **Noto Emoji** (googlefonts/noto-emoji) — Apache 2.0 / OFL 1.1.
- Repo: `github.com/EduCafilista/rp-tag` (MIT).
- **Nunca** apagar o repositório; publicação = Release com o jar.

Bom trabalho! — **RP Tag 4.2.0**
