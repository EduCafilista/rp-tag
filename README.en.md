# RP Tag

Roleplay mod for Minecraft 1.21.1 and NeoForge. Features include character
identities, proximity chat, speech bubbles, screen notifications, lore zones,
and canonical lives.

[Documentação em português](README.md)

## Projects and versions

| Project | Source version | Maintenance | Location |
| --- | --- | --- | --- |
| RP Tag | 4.5.0 | Active development | Repository root |
| Portais da Lore | 1.3.2 | Development suspended | [portais-da-lore](portais-da-lore/) |

JAR files are distributed through [GitHub releases](https://github.com/EduCafilista/rp-tag/releases).
The published version may differ from the source version while a release is
being prepared. Binary distributions are not committed to Git.

## Requirements and installation

- Minecraft 1.21.1.
- NeoForge 21.1.x; the development environment uses 21.1.249.
- Java 21.

1. Install NeoForge on the server and on clients that will use visual features.
2. Download the required RP Tag version from the releases page.
3. Remove the previous RP Tag JAR from `mods/` before adding the new file.
4. Install the same version on clients for speech bubbles, animations, screens,
   and other rendering features.

Server chat and identity features differ from client rendering features. Install
the mod on both sides for the complete experience.

## Features

### Character identity

RP and OFF RP status appears in chat, the player list, and nameplates. The game
uses the labels `● ʀᴘ` and `○ ᴏꜰꜰ ʀᴘ`.

Commands retain their Portuguese names, including in an English client.

| Command | Purpose |
| --- | --- |
| `/rp on` and `/rp off` | Change roleplay status |
| `/rp status` | Inspect status and diagnostic information |
| `/persona criar <name>` | Create a character identity |
| `/persona idade <number>` | Set age |
| `/persona desc <text>` | Set description |
| `/persona ver [player]` | View a character profile |
| `/persona off` | Remove the character identity |
| `/roll` | Roll dice |

### Chat and speech bubbles

Proximity chat includes shout, whisper, and global channels. Speech bubbles
support configurable frames, colors, duration, and stickers, with up to three
stacked bubbles per player. Administrators control access to bubbles.

| Command | Purpose |
| --- | --- |
| `/g <message>` | Global chat |
| `/s <message>` | Shout |
| `/w <message>` | Whisper |
| `/me <action>` | Character action |
| `/do <text>` | Environment description, subject to administrative permission |
| `/balao` | Open customization |
| `/balao teste <text>` | Preview a speech bubble |
| `/balao modo on` and `/balao modo off` | Change speech display mode |

### Screen notifications

Administrators use `/rp admin avisar <player|@a> <style> <message>` to display a
title and message. Available style identifiers are `classico`, `pesadelo`,
`runico`, `real`, `morte`, `inferno`, `gelo`, `divino`, `void`, `sangue`,
`demoniaco`, and `pesadelo2`.

In source version 4.5.0, Morte, Inferno, and Gelo can use animations rendered in
Blender. Procedural effects remain available when video is disabled, unavailable,
or fails. Message text is rendered by the client separately from the video.

Use `/rpfx status`, `/rpfx on`, or `/rpfx off` on the client. Settings are stored
in `config/rptag/cliente.properties`.

### Lore zones

Zones associate notifications, sound effects, and music with regions of the world.
Features include vertical limits, administrator-only boundary visualization, and
an inspection wand.

- `/lorezone listar`: list configured zones.
- `/lorezone vertical <id> <minY> <maxY>`: restrict the height range.
- `/lorezone vertical <id> auto`: restore the complete spherical region.
- `/lorezone mostrar <id> [seconds]`: display boundaries to the administrator.
- `/lorezone esconder`: stop boundary visualization.
- `/lorezone varinha`: obtain the inspection tool.
- `/lorezone musicas`: list sound presets.

Music options include presets, custom files, and supported URLs. Refer to the
in-game command help for individual arguments.

### Canonical lives

Administrators enable the system with `/rp admin canon on`. Each player starts
with five lives. Features include loss and recovery notifications, protection
against repeated losses, and a canonical heart that restores a life.

Kills by an OFF RP player do not deduct canonical lives. Other causes follow the
system rules. Since version 4.4.0, `/rp admin vida` can inspect and administer lives.

## Development

Use the included Gradle Wrapper.

```powershell
# Windows
.\gradlew.bat build
python check.py
```

```bash
# Linux and macOS
./gradlew build
python3 check.py
```

The build runs Java tests and produces `build/libs/rptag-4.5.0.jar`. Initial
builds require additional time to download dependencies and prepare Minecraft.

## Documentation

| Document | Contents |
| --- | --- |
| [COMO-EDITAR.md](COMO-EDITAR.md) | Development setup, builds, and tests (Portuguese) |
| [ENTREGA.md](ENTREGA.md) | Architecture and project conventions (Portuguese) |
| [PUBLICAR.md](PUBLICAR.md) | Release preparation and publication (Portuguese) |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [WALKTHROUGH.en.md](WALKTHROUGH.en.md) | Historical technical notes in English |
| [WALKTHROUGH.md](WALKTHROUGH.md) | Detailed technical record in Portuguese |
| [arte/README.md](arte/README.md) | Animation production and validation (Portuguese) |

Files under `docs/superpowers/` record designs and implementation plans. Their
presence does not establish that a feature has been implemented or released.

## License

Distributed under the [MIT license](LICENSE).
