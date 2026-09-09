# RP Tag

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62a552) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange) ![License](https://img.shields.io/badge/License-MIT-blue)

**A NeoForge 1.21.1 mod** — the essential RP kit for lore servers: **personas** (become someone else), **local chat** with channels, **dice rolls**, **lore zones**, **speech bubbles** above heads and the **(ʀᴘ)/(ᴏꜰꜰ ʀᴘ)** tag on players' names.

> 🌎 **Idioma / Language:** [Português (BR)](README.md) | **English**
>
> 📥 **Download:** grab `rptag-2.2.0.jar` from the [Releases page](https://github.com/EduCafilista/rp-tag/releases) or the [direct link](https://github.com/EduCafilista/rp-tag/raw/main/rptag-2.2.0.jar).

## ✨ Features

### 🎭 Persona — be whoever you want
- `/persona create "Lord Aldric"` — the **character name replaces the nick** on the nametag, chat and TAB (also turns RP on)
- `/persona age 24` · `/persona desc "A knight seeking redemption"` — hover tooltip on the name
- `/persona show [player]` — full card · `/persona off` — back to your nick

### 💬 Local RP chat
- Plain chat is **local** (40 blocks) — far players can't hear you
- `/s <msg>` **shout** (100 blocks, CAPS) · `/w <msg>` **whisper** (5 blocks)
- `/me <action>` → `✦ Lord Aldric walks through the forest` · `/do <scene>` → `✦ the door creaks open`
- `/g <msg>` global chat · admins: `/rp admin chatlocal on|off`

### 🗨️ Speech bubbles
- What you type shows in a **rounded bubble above your head** — perfect for **eggs, kids and mic-less players** (QSMP style)
- Automatic line wrap; display time scales with message length
- **Bubble mode** — speech becomes **bubble-only** (no chat text!): `/balao modo on` for yourself · `/rp admin bolha <player> on` set by admins
- **`/balao`** opens a **customization screen**: color (RGB sliders + presets), **decorative emojis** (✦ ★ ♥ ⚔...) and **7 backgrounds** (translucent, dark, light, gradient, **paper**, **night**, **wood**) — live preview
- Text commands: `/balao cor ciano` · `/balao emoji antes ✦` · `/balao fundo noite`
- **`/cor <color>`** — quick color shortcut (chat + bubble), automatic text contrast
- **`/rp admin bolhas on|off`** — global toggle

### 🎲 Dice rolls
- `/roll` (d20) · `/roll d100` · `/roll 2d6+1 <reason>` — announced nearby, with dice breakdown

### 📜 Lore zones
- Admins: `/lorezone create <id> <radius> <title>` — regions that show an **epic title**, subtitle and sound when a player walks in (once per session)

### 🏷️ RP/OFF RP tag
- **(ʀᴘ)** cyan or **(ᴏꜰꜰ ʀᴘ)** gray next to the name — small caps font, only the tag is colored
- Nametag, chat and TAB · `/rp` toggles, `/rp on|off|status`, `/rp set <player> on|off` (admin)

- **Everything saved to the world** — survives relog, death and restarts. Everyone starts in **OFF RP**.
- **No dependencies** — only NeoForge.

## 📥 Installation

**Requirements:** Minecraft 1.21.1 + NeoForge 21.1.x + Java 21

1. Install **NeoForge 1.21.1** on your server.
2. Drop `rptag-2.2.0.jar` into the **server's** `mods/` folder.
3. *(Recommended)* Players drop the same jar into the **client's** `mods/` folder — that enables speech bubbles and the name tag above heads.

## 🔧 Building from source

Requirements: **JDK 21** and internet access.

```bash
./gradlew build
```

The jar ends up at `build/libs/rptag-2.2.0.jar`.

## 🎨 Customizing

- **Tag text/colors**: `RPTags.java` (`TAG_ON`, `TAG_OFF`, `ChatFormatting.AQUA`/`GRAY`)
- **Bubble colors default**: `RPWorldData.DEFAULT_COLOR`; players customize with `/cor`
- **Chat ranges**: `RPWorldData` (`LOCAL_RANGE`, `SHOUT_RANGE`, `WHISPER_RANGE`)
- **Pill nametag style**: `NameplateRenderer.java` → `BADGE_ENABLED = true`
- **Command messages**: `RPCommands.java`, `ChatCommands.java`, `BubbleCommands.java`

## 🗂 Structure

```
src/main/java/dev/rptag/
  RPTagMod.java            # main class (@Mod)
  RPTags.java              # (ʀᴘ)/(ᴏꜰꜰ ʀᴘ) tag builder
  Persona.java             # character identity (record)
  RPWorldData.java         # world-saved state (SavedData)
  SyncRPStatePayload.java  # server -> client sync packets
  PersonaSyncPayload.java
  ChatBubblePayload.java   # speech bubble packet
  ModNetworking.java       # packet registration
  ServerEvents.java        # state, personas, names, local chat
  RPChat.java              # channel formatting + bubble spawner
  RPCommands.java          # /rp
  PersonaCommands.java     # /persona
  ChatCommands.java        # /g /s /w /me /do
  BubbleCommands.java      # /cor /bolha
  RollCommands.java        # /roll
  LoreZones.java           # lore regions + tick trigger
  LoreZoneCommands.java    # /lorezone
  client/                  # bubbles renderer, nametag, caches
```

## 📄 License

[MIT](LICENSE)
