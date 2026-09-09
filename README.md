# RP Tag

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62a552) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange) ![License](https://img.shields.io/badge/Licen%C3%A7a-MIT-blue)

**Mod para NeoForge 1.21.1** — o kit essencial de RP para servidores de lore: **persona** (vire outro personagem), **chat local** por proximidade com canais, **dados** para eventos, **lore zones** e a tag **(ʀᴘ)/(ᴏꜰꜰ ʀᴘ)** no nome dos jogadores.

> 🌎 **Idioma / Language:** **Português (BR)** | [English](README.en.md)
>
> 📥 **Download:** baixe o `rptag-2.2.0.jar` na [aba de Releases](https://github.com/EduCafilista/rp-tag/releases) ou no [link direto](https://github.com/EduCafilista/rp-tag/raw/main/rptag-2.2.0.jar) (arquivo dentro do repositório).
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
- `/me <ação>` → `✦ Lord Aldric caminha pela floresta` · `/do <ambiente>` → `✦ a porta range ao abrir`
- `/g <msg>` chat global · admins ligam/desligam o modo local com `/rp admin chatlocal on|off`

### 🗨️ Balões de fala (chat bubbles)
- O que você digita aparece num **balão arredondado com rabicho sobre a sua cabeça** —
  perfeito para **ovos, crianças e quem não usa microfone** (estilo QSMP)
- **Modo balão** — a fala vira **só o balão** (nada no chat!):
  `/balao modo on` para si · `/rp admin bolha <jogador> on` para o admin setar
- **`/balao`** abre a **tela de personalização**: cor (sliders RGB + presets),
  **emojis decorativos** (✦ ★ ♥ ⚔...) e **fundos** (translúcido, escuro, claro,
  gradiente, **papel**, **noite** e **madeira**) — com preview ao vivo
- Por texto: `/balao cor ciano` · `/balao emoji antes ✦` · `/balao fundo noite`
- **`/cor <cor>`** — atalho da cor (chat + balão) · **`/rp admin bolhas on|off`** — global

### 🎲 Dados para eventos
- `/roll` (d20) · `/roll d100` · `/roll 2d6+1` — resultado anunciado por perto, com decomposição dos dados e motivo opcional

### 📜 Lore Zones
- Admins criam regiões com `/lorezone criar <id> <raio> <título>` que mostram **título épico na tela**, subtítulo e som quando um jogador entra (1x por sessão)
- A história do servidor contada no próprio mapa

### 🏷️ Tag RP/OFF RP
- **(ʀᴘ)** ciano ou **(ᴏꜰꜰ ʀᴘ)** cinza no nome — fonte diferente (small capitals), colorindo só a tag
- Aparece no nametag, no chat e na TAB · `/rp` alterna, `/rp on|off|status`, `/rp set <jogador> on|off` (admin)

- **Tudo salvo no mundo** — sobrevive a relog, morte e reinício do servidor.
  Por padrão, todo mundo começa em **OFF RP**.
- **Sem dependências** — só precisa do NeoForge.

## 📥 Instalação

**Requisitos:** Minecraft 1.21.1 + NeoForge 21.1.x + Java 21

1. Instale o **NeoForge 1.21.1** no servidor.
2. Copie `rptag-2.2.0.jar` para a pasta `mods/` do **servidor**.
   → a tag no **chat** e na **TAB** já funciona pra todo mundo.
3. *(Opcional)* Jogadores que quiserem ver a tag **em cima da cabeça** colocam o
   jar também na pasta `mods/` do **cliente**.

## 🔧 Compilando do zero

Requisitos: **JDK 21** e internet.

```bash
./gradlew build
```

O jar sai em `build/libs/rptag-2.2.0.jar`.

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
  RPTags.java              # monta a tag (ʀᴘ)/(ᴏꜰꜰ ʀᴘ)
  RPWorldData.java         # estado salvo no mundo (SavedData)
  SyncRPStatePayload.java  # pacote de sincronização servidor -> cliente
  ModNetworking.java       # registro do pacote
  ServerEvents.java        # chat, TAB, login, sincronização
  RPCommands.java          # comando /rp
  client/                  # nametag no cliente + cache de estados
```

## 📄 Licença

[MIT](LICENSE)
