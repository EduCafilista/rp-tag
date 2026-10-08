# 🎮 Walkthrough — RP Tag from zero to RP active

Step-by-step guide, from download to the tag showing in game.

> 🌎 **Idioma / Language:** [Português (BR)](WALKTHROUGH.md) | **English**

---

## Step 0 — What you need

| Item | Where to get it |
|---|---|
| Minecraft **1.21.1** (Java Edition) | official launcher |
| **NeoForge 21.1.x** | https://neoforged.net/ → *Downloads* → version 21.1.x |
| **Java 21** | https://adoptium.net/ (Temurin 21) |
| `rptag-3.42.0.jar` | this repository's *Releases* tab |

> ⚠️ The mod version matches the game version: `rptag-3.42.0.jar` is for **1.21.1**. Another Minecraft version requires recompiling.

---

## Step 1 — Install on the server

1. Install NeoForge on the server (the installer asks for the server folder path).
2. Inside the server folder, open `mods/`.
3. Copy `rptag-3.42.0.jar` into it.
4. Start the server once to generate files (accept the EULA in `eula.txt` with `eula=true`).

**How to know it worked:** in the boot log look for

```
RP Tag 2.0.0 (rptag)
```

Done — from this point the tag already works in **chat** and **TAB** for everyone.

---

## Step 2 — Install on the client (optional, to see the tag above heads)

1. In the launcher, create/edit the NeoForge 1.21.1 profile.
2. Open the game folder ("open game folder" option in the launcher) and enter `mods/`.
3. Copy the same `rptag-3.42.0.jar` there.
4. Join the server.

Without the client mod you still see the tag in chat/TAB; with it, you also see **(ʀᴘ)** or **(ᴏꜰꜰ ʀᴘ)** floating above players' heads.

---

## Step 3 — Using it in game

Join the server with any account and try:

| What to type | What happens |
|---|---|
| `/rp` | toggles: OFF RP → RP (and back) |
| `/rp status` | shows the current state: `Current RP mode: (ʀᴘ)` |
| `/rp on` | enter RP directly |
| `/rp off` | leave RP directly |
| `/rp set Steve off` | *(OP/admin only)* changes another player's state |

**What to look for after `/rp`:**

- Confirmation message in your chat.
- **Chat:** your next message shows as `Alex (ʀᴘ): hi` (cyan) or `Steve (ᴏꜰꜰ ʀᴘ): hi` (gray).
- **TAB:** your name in the list gets the tag.
- **Nametag:** with the client mod, the tag floats above your head.
- **Persistence:** relog, restart the server, die... the state stays saved in the world. Every new player starts in **OFF RP**.

**To test admin:** from the console or an OP account, use `/rp set <nick> on` and ask the person to check their name in TAB.

---

## Step 4 — Building from source

To contribute or build your own jar:

```bash
# requirements: JDK 21 + internet
git clone https://github.com/EduCafilista/rp-tag.git
cd rp-tag
./gradlew build          # (Windows: gradlew.bat build)
```

The jar ends up at `build/libs/rptag-3.42.0.jar`. The first build takes a few minutes (it downloads and prepares Minecraft automatically — you don't need the game installed).

---

## Step 5 — Customizing

### Change the tag color

In `src/main/java/dev/rptag/RPTags.java`, method `tag(...)`:

```java
return inRp
        ? Component.literal(tagText(true)).withStyle(ChatFormatting.AQUA)  // cyan
        : Component.literal(tagText(false)).withStyle(ChatFormatting.GRAY); // gray
```

Swap `AQUA`/`GRAY` for any enum value: `DARK_AQUA`, `BLUE`, `YELLOW`, `GREEN`, `RED`, `WHITE`, `LIGHT_PURPLE`, `DARK_GREEN`...

### Change the tag text

Same file, top of the class:

```java
public static final String TAG_ON  = "RP";      // becomes (ʀᴘ)
public static final String TAG_OFF = "OFF RP";  // becomes (ᴏꜰꜰ ʀᴘ)
```

Any text works — parentheses and small caps are applied automatically.

### Re-enable the rounded pill nametag

In `src/main/java/dev/rptag/client/NameplateRenderer.java`:

```java
public static final boolean BADGE_ENABLED = true;
```

### Change command messages

They live in `RPCommands.java` (`/rp status` and `/rp set` replies) and `ServerEvents.java` (the "RP mode enabled/disabled" confirmation).

After any change: `./gradlew build` and replace the jar in `mods/`.

---

## 🩺 Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Tag shows in chat but not above heads | Mod missing on the client | Install the jar on the client's `mods/` too |
| Tag shows nowhere | Mod missing on the server | Install on the **server's** `mods/` (the essential one) |
| Squares `□□` instead of the tag | Client resource pack replacing the font | Test without the resource pack — small caps use the game's default font |
| `/rp set` says insufficient permissions | You are not OP | Get OP (`op YourNick` in console) or use the console |
| Names got double-tagged with another mod | Rare formatting conflict | Report on the repository with the log |

---

## Quick code map

```
Server                            Client
─────────────────────────         ─────────────────────────────
RPCommands.java  → /rp            ClientPayloadHandler.java ← receives state
ServerEvents.java → chat/TAB      ClientRPStates.java       → cache
RPWorldData.java  → saves NBT     ClientEvents.java         → local name
SyncRPStatePayload.java ──────────> (server → client packet)
```

Happy RP! 🎭

---

## 🗨️ Speech bubbles (new in v2.3.0)

What you type (local chat, `/g`, `/s`, `/w`, `/me`) shows up in a **rounded bubble
above your head** — perfect for eggs, kids and mic-less players.

| Command | Effect |
|---|---|
| `/balao` | opens the customization SCREEN: color picker (drag your mouse!), accent bar, backgrounds and emojis |
| `/rp admin bolha off` | *(admin)* disables bubbles server-wide |

---

## 🥚 Bubble mode and the customization SCREEN (v2.5.0)

**For eggs, kids and mic-less characters:** speech becomes **bubble-only** — nothing in chat.

| Command | Effect |
|---|---|
| `/balao` | opens the **customization screen** (color picker, live preview) |
| `/balao modo on` / `off` | toggle bubble mode for yourself |
| `/rp admin bolha <player> on` | *(admin)* enables bubble mode for another player |
| `/rp admin balao <player> off` | *(admin)* forbids the player from using bubbles (speech goes to chat only) |
| `/rp admin balao <player> on` | *(admin)* allows bubbles again |
| `/rp admin balaolist` | *(admin)* lists players without bubbles |
| `/rp admin bolhas off` | *(admin)* disables bubbles server-wide |

### How to use the screen (`/balao`)
- **LIVE CHAT SIMULATION** — the top panel shows the line
  `<Você> sua fala assim` (name in bubble color, speech in text color) and the
  full bubble. Change ANYTHING (fill, border, frame, text color, sticker)? The
  simulation reacts INSTANTLY. Footer: `#fill · ▣ border · ✎ text`
- **Sticker with the MOUSE**: `✦ Adesivos` → each sticker is a **color dot +
  number** (hover shows the NAME) → click an emoji (it sticks to the
  cursor) → **click anywhere on the bubble** to place it. Click the placed
  sticker to **pick it back** and reposition; **right-click** the bubble
  **removes** it. **One sticker per bubble** — the old Before/Middle/After
  slots migrated into it automatically. Click outside the bubble or
  right-click to **cancel** without placing
- **Paint** — TWO independent pickers: **✦ Dentro** (left) paints the fill
  and **▣ Borda** (right) paints the frame. Under each one there is a
  **ONE-CLICK PALETTE** (8 ready colors) — click, painted, no dragging! Or
  drag the squares for any custom color (live hex on the label)
- **Border** — 3 COLOR buttons: **▣ Auto** (always matches) · **▣ Minha cor**
  (whatever you paint on the right picker) · **▢ Sem borda** — active one gets
  a green frame
- **Frame** — 3 STYLE buttons: **▤ Clássica** (thin) · **▤ Cartum** (thick
  comic cover) · **▤ Dupla** (double line)
- **✎ Letra (text)** — **Auto** (white or black depending on the fill, always
  perfect contrast) or 8 chat colors at 1 click — the bubble text updates instantly
- **⏱ Duration** — DRAG the slider for 2 to 20 seconds of display time
- **☾ Bubble mode** — toggle right there
- **✔ Save** (RIGHT-side button, Minecraft-style) or **ESC** stores
  everything — no more losing your setup · **↺ Padrão** restores defaults
- **Saved FOREVER**: leave and rejoin the server — colors, sticker, frame and
  duration are stored in the world file
- **JAR DIAGNOSTIC**: the top-right of the screen shows the CLIENT version
  (e.g. "v3.4.0") and the save message shows the SERVER version ("💾 Salvo no
  servidor v3.4.0!"). Both must MATCH — otherwise replace the old side's jar
  (always update client AND server together)
- Then just **say something in chat** to see your bubble ✨

---

## 📜 Lore Zones (story regions)

Admins mark map regions that react when a player walks in: epic on-screen
title, subtitle, sound — and now even **looping music**.

| Command | Effect |
|---|---|
| `/lorezone criar <id> <raio> <titulo>` | creates the zone at your position (subtitle comes from the text) |
| `/lorezone texto <id> <texto>` | sets the zone subtitle |
| `/lorezone som <id> <som>` | one-shot sound on enter (e.g. `minecraft:ambient.cave`) |
| `/lorezone musica <id> <som>` (ou `<id> intervalo <segundos> <som>`) | **LOOPING track** while inside — restarts every `intervalo` seconds (10–600, default 45) and **stops on exit** |
| `/lorezone listar` | list zones |
| `/lorezone remover <id>` | delete a zone |

Spooky sounds that work well: `minecraft:ambient.cave`,
`minecraft:entity.warden.heartbeat`, `minecraft:music.overworld.deep_dark`,
`minecraft:music_disc.13`, `minecraft:music_disc.11`.

---

## 😱 RP effects: FEAR and warnings

| Command | Effect |
|---|---|
| `/rp admin medo <player> [seconds]` | the player's screen gets **HIDDEN** under a pulsing night-blue veil, with a dark vignette, a blinking face on the HUD and **camera shake** — real panic |
| `/rp admin avisar <player> <text>` | BIG on-screen text, `&` colors (`&c` red, `&6` orange, `&l` bold…) |
| `/effect give <player> rptag:medo <s>` | same fear via the vanilla command |

The "is afraid" toast shows **to admins only** — the player feels the
effect without knowing who sent it. In 3.49 the cloud bubble (Turma da
Mônica-style thought balloon) got **real HD circular balls**, no more seams.


---

## 🎵 CUSTOM music in Lore Zones (3.50.0)

Any music you own can play inside a zone — even audio from a YouTube video.

1. Convert it to OGG Vorbis: `yt-dlp -x --audio-format vorbis "<url>"`
2. Drop the `.ogg` into the CLIENT's `config/rptag/musicas/` folder with a
   simple name (e.g. `tense.ogg`)
3. Apply it: `/lorezone musica <id> @tense` — it loops while the player is
   inside the zone and stops the moment they leave. Each player needs the file.

Ready-made presets (27): `caverna`, `coracao`, `warden`, `deepdark`,
`disc13`, `disc11`, `disc5`, `mood`, `almas`, `pigstep`, plus `portal`,
`portalviagem`, `nether`, `vento`, `dragao`, `ghast`, `creeper`, `trovao`,
`chuva`, `fogo`, `agua`, `sino`, `outros`, `relic`, `creator`, `maldicao`
and `nenhum` (clears the zone sound). `/lorezone musicas` lists them in game.
Zones re-trigger when you **leave and come back** (1x per visit), a new sound
**replaces** the previous one (never stacks), and music accepts a **direct
file link** (`https://.../tema.ogg` — players download it automatically;
YouTube must be converted first, the command gives you the ready `yt-dlp`
line). Players adjust volume via Options → Music & Sounds → **Ambient**
(zone sounds) and **Music** (soundtracks).
**`/rp ajuda`** lists every command with what it does (admins also get the
admin section).
