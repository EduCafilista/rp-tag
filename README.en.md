# RP Tag

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62a552) ![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange) ![License](https://img.shields.io/badge/License-MIT-blue)

**A NeoForge 1.21.1 mod** that shows **(ʀᴘ)** or **(ᴏꜰꜰ ʀᴘ)** next to players' names and adds the **`/rp`** command so players can switch modes themselves. Made for RPG servers.

> 🌎 **Idioma / Language:** [Português (BR)](README.md) | **English**

## ✨ Features

- **Name above head (nametag)** — the name gets a parenthesized tag in a different
  font style (Unicode small capitals): **(ʀᴘ)** in **cyan** or
  **(ᴏꜰꜰ ʀᴘ)** in **gray**. Only the tag gets colored — the name stays normal.
- **Chat** — messages show up as `Alex (ʀᴘ): hi` / `Steve (ᴏꜰꜰ ʀᴘ): hi`.
- **Player list (TAB)** — the name tag appears there too.
- **`/rp` command** available to every player:

  | Command | Who | Effect |
  |---|---|---|
  | `/rp` | everyone | toggles between RP and OFF RP |
  | `/rp on` / `/rp off` | everyone | sets the state directly |
  | `/rp status` | everyone | shows the current state |
  | `/rp set <player> on\|off` | admins (permission level 2 / OP) | sets another player's state |

- **State saved to the world** — survives relog, death and server restarts.
  Everyone starts in **OFF RP** by default.
- **No dependencies** — only NeoForge is required.

## 📥 Installation

**Requirements:** Minecraft 1.21.1 + NeoForge 21.1.x + Java 21

1. Install **NeoForge 1.21.1** on your server.
2. Drop `rptag-1.2.1.jar` into the **server's** `mods/` folder.
   → the tag now works in **chat** and **TAB** for everyone.
3. *(Optional)* Players who want the tag **above heads** drop the same jar into
   the **client's** `mods/` folder.

**Download:** grab the jar from the [Releases page](https://github.com/EduCafilista/rp-tag/releases) or the direct link in the Portuguese README.

## 🔧 Building from source

Requirements: **JDK 21** and internet access.

```bash
./gradlew build
```

The jar ends up at `build/libs/rptag-1.2.1.jar`.

## 🎨 Customizing

- **Tag text and colors**: `src/main/java/dev/rptag/RPTags.java`
  - `TAG_ON` / `TAG_OFF` — the text (parentheses and small caps are applied automatically)
  - `ChatFormatting.AQUA` (cyan for RP) and `ChatFormatting.GRAY` — swap for any of Minecraft's 16 colors
- **Nametag style**: `src/main/java/dev/rptag/client/NameplateRenderer.java`
  - `BADGE_ENABLED = true` draws the tag as a rounded pill badge instead of text
- **Command messages**: `RPCommands.java` and `ServerEvents.java`
- **NeoForge version**: `build.gradle`

## 🗂 Structure

```
src/main/java/dev/rptag/
  RPTagMod.java            # main class (@Mod)
  RPTags.java              # builds the (ʀᴘ)/(ᴏꜰꜰ ʀᴘ) tag
  RPWorldData.java         # world-saved state (SavedData)
  SyncRPStatePayload.java  # server -> client sync packet
  ModNetworking.java       # packet registration
  ServerEvents.java        # chat, TAB, login, syncing
  RPCommands.java          # /rp command
  client/                  # client-side nametag + state cache
```

## 📄 License

[MIT](LICENSE)
