# RP Tag

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62a552) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange) ![License](https://img.shields.io/badge/License-MIT-blue)

**A NeoForge 1.21.1 mod** — the essential RP kit for lore servers: **personas** (become someone else), **local chat** with channels, **dice rolls**, **lore zones**, **speech bubbles** above heads and the **● ʀᴘ / ○ ᴏꜰꜰ ʀᴘ** tag on players' names and in the TAB list.

> 🌎 **Idioma / Language:** [Português (BR)](README.md) | **English**
>
> 📥 **Download:** grab `rptag-3.52.0.jar` from the [Releases page](https://github.com/EduCafilista/rp-tag/releases) or the [direct link](https://github.com/EduCafilista/rp-tag/raw/main/rptag-3.52.0.jar).

## ✨ Features

### 🎭 Persona — be whoever you want
- `/persona create "Lord Aldric"` — the **character name replaces the nick** on the nametag, chat and TAB (also turns RP on)
- `/persona age 24` · `/persona desc "A knight seeking redemption"` — hover tooltip on the name
- `/persona show [player]` — full card · `/persona off` — back to your nick

### 💬 Local RP chat
- Plain chat is **local** (40 blocks) — far players can't hear you
- `/s <msg>` **shout** (100 blocks, CAPS) · `/w <msg>` **whisper** (5 blocks)
- `/me <action>` → `✦ Lord Aldric walks through the forest` · `/do <scene>` *(admin only)* → `✦ the door creaks open`
- `/g <msg>` global chat · admins: `/rp admin chatlocal on|off`

### 🗨️ Speech bubbles
- What you type shows in a **rounded bubble above your head** — perfect for **eggs, kids and mic-less players** (QSMP style)
- Automatic line wrap; display time scales with message length
- **Bubble mode** — speech becomes **bubble-only** (no chat text!): `/balao modo on` for yourself · `/rp admin bolha <player> on` set by admins
- **MENU with LIVE CHAT SIMULATION**: a `<Você> speech` line + the full bubble
  reacting instantly to EVERYTHING — fill, border, frame, TEXT color and sticker
- **Comic-style bubble, real smooth texture**: true rounded pill (anti-aliased),
  full outline and tail — **everything picked on the COMIC-BOOK PLATES menu** (ink frames, gold
  labels, guide zones that light up when you grab a sticker) (`/balao`)
  · the menu preview uses the REAL in-world pill (same rounded texture, tail
  and all 6 frames drawn for real — see the cloud, dashes and comic style
  before saving) · **6 frames with their
  own buttons**: Classic, Cartoon (thick), Double, **Dashed (straight little
  strokes), Thought** — fluffy cloud with a **trail of descending dots, classic
  Monica's Gang thought-bubble style!** — **and Comic** — the big classic comic
  balloon, puffy with thick ink and a gelatinous shine · **2 independent
  pickers** + **one-click palettes** (8 ready colors each — no dragging!) ·
  **border color in 3 buttons**: Auto / My color / None · **TEXT color**:
  Auto (auto-contrast) + 8 colors
- **FREE STICKER with the mouse**: 64 stickers with their REAL ART on the
  keyboard, in **high resolution (16px with outline, shine and shading — the
  polish of famous emoji mods)**, in **2 pages** — Classics (48) and **Cute** (16: bow,
  dinosaur, planet, galaxy, rocket, rainbow, paw, unicorn, fox, penguin,
  ghost, mushroom...) — pick by sight, then **click ANY point of the bubble**
  to place it; **crosshair + % on screen** show exactly where it is ·
  right-click removes · **ESC or ✔ Save** stores everything ·
  **duration slider** (2–20s)
- **MULTIPLE EMOJIS in the speech**: grab a sticker and **click outside the
  bubble** to add it to the speech (one per click, **up to 6**) — the emojis
  show INSIDE the bubble, exactly like they will in the world · every kid
  builds their own combo · ✨ slots on the screen ("✖" removes, "+" opens
  the keyboard)
- **FAMOUS-MOD-STYLE ANIMATION**: bubbles are born with a **bouncy pop-in**,
  gently float upward while you talk and leave with a smooth **fade-out** ·
  **STACK of up to 3 bubbles** per player — newest near the head, older ones
  rise (just like Chat Bubbles/TalkBubbles)
  · **ADMIN-GRANTED bubbles** (default: nobody): `/rp admin balao <player> on`
  grants + turns their bubble mode on · `off` revokes · `/rp admin balaolist`
  lists granted players · **mode OFF hides the bubble instantly**
  · menu rebuilt: big font, 12-color one-click palettes, compact layout
  · **`/rp admin bolhas on|off`** — global toggle

### 🎲 Dice rolls
- `/roll` (d20) · `/roll d100` · `/roll 2d6+1 <reason>` — announced nearby, with dice breakdown

### 📜 Lore zones
- Admins: `/lorezone create <id> <radius> <title>` — regions that show an **epic title**, subtitle and sound when a player walks in (once per session)

### 🏷️ RP/OFF RP tag
- **● ʀᴘ** (cyan dot) or **○ ᴏꜰꜰ ʀᴘ** (gray dot) on the name and **also in the TAB list** — small caps, only the tag is colored
- Nametag, chat and TAB · `/rp` toggles, `/rp on|off|status`, `/rp set <player> on|off` (admin)

- **Everything saved to the world** — survives relog, death and restarts. Everyone starts in **OFF RP**.
- **No dependencies** — only NeoForge.

## 📥 Installation

**Requirements:** Minecraft 1.21.1 + NeoForge 21.1.x + Java 21

1. Install **NeoForge 1.21.1** on your server.
2. Drop `rptag-3.50.0.jar` into the **server's** `mods/` folder.
3. *(Recommended)* Players drop the same jar into the **client's** `mods/` folder — that enables speech bubbles and the name tag above heads.

## 🔧 Building from source

Requirements: **JDK 21** and internet access.

```bash
./gradlew build
```

The jar ends up at `build/libs/rptag-3.50.0.jar`.

## 🎨 Customizing

- **Tag text/colors**: `RPTags.java` (`TAG_ON`, `TAG_OFF`, `ChatFormatting.AQUA`/`GRAY`)
- **Bubble colors default**: `RPWorldData.DEFAULT_COLOR`; players customize with `/cor`
- **Chat ranges**: `RPWorldData` (`LOCAL_RANGE`, `SHOUT_RANGE`, `WHISPER_RANGE`)
- **Pill nametag style**: `NameplateRenderer.java` → `BADGE_ENABLED = true`
- **Command messages**: `RPCommands.java`, `ChatCommands.java`

## 🗂 Structure

```
src/main/java/dev/rptag/
  RPTagMod.java            # main class (@Mod)
  RPTags.java              # ● ʀᴘ / ○ ᴏꜰꜰ ʀᴘ tag builder
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
  ChatCommands.java        # /g /s /w /me /do (admin)
  RollCommands.java        # /roll
  LoreZones.java           # lore regions + tick trigger
  LoreZoneCommands.java    # /lorezone
  client/                  # bubbles renderer, nametag, caches
```

## 📄 License

[MIT](LICENSE)
