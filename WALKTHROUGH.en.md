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
| `rptag-1.2.1.jar` | this repository's *Releases* tab |

> ⚠️ The mod version matches the game version: `rptag-1.2.1.jar` is for **1.21.1**. Another Minecraft version requires recompiling.

---

## Step 1 — Install on the server

1. Install NeoForge on the server (the installer asks for the server folder path).
2. Inside the server folder, open `mods/`.
3. Copy `rptag-1.2.1.jar` into it.
4. Start the server once to generate files (accept the EULA in `eula.txt` with `eula=true`).

**How to know it worked:** in the boot log look for

```
RP Tag 1.2.1 (rptag)
```

Done — from this point the tag already works in **chat** and **TAB** for everyone.

---

## Step 2 — Install on the client (optional, to see the tag above heads)

1. In the launcher, create/edit the NeoForge 1.21.1 profile.
2. Open the game folder ("open game folder" option in the launcher) and enter `mods/`.
3. Copy the same `rptag-1.2.1.jar` there.
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

The jar ends up at `build/libs/rptag-1.2.1.jar`. The first build takes a few minutes (it downloads and prepares Minecraft automatically — you don't need the game installed).

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
