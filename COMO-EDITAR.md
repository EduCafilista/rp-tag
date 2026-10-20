# 🛠️ COMO EDITAR E COMPILAR o RP Tag

> Guia pra quem vai **mexer no código** do mod. O diário de bordo completo do
> projeto (arquitetura, decisões, receitas de build/teste) está no
> **`WALKTHROUGH.md`** — lê ele depois deste.

---

## 1. O que instalar (uma vez só)

| Programa | Onde | Pra quê |
|---|---|---|
| **JDK 21** | https://adoptium.net (Temurin 21) | compilar |
| **IntelliJ IDEA Community** | https://www.jetbrains.com/idea/download (opcional, mas recomendo) | editar com autocomplete |
| Git | https://git-scm.com (opcional) | versionar suas mudanças |

> Windows: ao instalar o JDK, marque "Set JAVA_HOME" se a opção existir.

## 2. Compilar (a prova de tudo)

Abra o terminal **na pasta extraída** e rode:

- **Windows:** `gradlew.bat build`
- **Linux/Mac:** `./gradlew build`

O **primeiro build demora** (baixa o Gradle, o Minecraft e o NeoForge —
uns 5~15 min conforme a internet) e **precisa de ~4 GB de RAM livres**
(é o build padrão de mods NeoForge: ele descompila o Minecraft uma vez e
guarda no cache; das outras vezes é rapidinho).

✅ O jar pronto aparece em **`build/libs/rptag-4.2.0.jar`** — é esse que
vai na pasta `mods` do jogo/servidor (sempre APAGUE o jar antigo antes de
pôr o novo, no SERVIDOR e no CLIENTE!).

## 3. Testar direto no jogo (sem servidor de verdade)

- `gradlew.bat runClient` — abre um Minecraft 1.21.1 com o mod carregado
  (cria a pasta `run/` com o mundo de teste)
- `gradlew.bat runServer` — sobe um servidor de teste (aceite o eula em
  `run/eula.txt` na primeira vez)

## 4. Onde mexer (mapa rápido)

| Quero mudar... | Arquivo |
|---|---|
| Visual do balão no mundo | `src/main/java/dev/rptag/client/BubbleRenderer.java` |
| **Forma/estilo do balão (geometria)** | `src/main/java/dev/rptag/client/BubbleGeometry.java` (Java puro — testa com `tools/teste-geometria.sh`) |
| Tela de personalização `/balao` | `src/main/java/dev/rptag/client/BubbleStyleScreen.java` |
| Comandos de chat (`/g /s /w /pensar /me /do`) | `src/main/java/dev/rptag/ChatCommands.java` |
| Comando `/rp` e ajuda | `src/main/java/dev/rptag/RPCommands.java` |
| Molduras/cores/adesivos (dados) | `src/main/java/dev/rptag/BubbleStyle.java` |
| Efeito do MEDO | `src/main/java/dev/rptag/RPEffects.java` + `client/ClientEffects.java` |
| Zonas de lore | `src/main/java/dev/rptag/LoreZones.java` + `LoreZoneCommands.java` |
| **Barreira/varinha da zone** | `client/ZoneVisualizer.java` + `LorezoneWand.java` + `ZoneShowPayload.java` |
| **Vidas canônicas (ritual)** | `CanonLife.java` (+ `CanonCoracaoBlock.java`, `client/CanonCoracaoRenderer.java`) |
| **Um aviso cinematográfico (FX)** | `src/main/java/dev/rptag/client/<Estilo>AvisoFx.java` |
| **Qual FX toca em cada estilo** | `client/ClientEffects.java` (`startScreenText`) |
| **Motor compartilhado dos FX** | `src/main/java/dev/rptag/client/AvisoFxUtil.java` |
| **Registrar uma camada nova de tela** | `client/ClientGuiLayers.java` (`registerAboveAll`) |
| **Música custom da zone** | `MusicSync.java` + `client/MusicaCustom.java` |
| Texturas (balão, molduras, rabicho...) | `src/main/resources/assets/rptag/textures/gui/` |
| Textos no jogo (PT/EN) | `src/main/resources/assets/rptag/lang/pt_br.json` e `en_us.json` |

⚠️ **A versão do mod fica em 3 lugares** (mude os 3 juntos):
`build.gradle` (`version = '...'`), `RPTagMod.java` (`VERSION = "..."`) e
`src/main/resources/META-INF/neoforge.mods.toml` (`version="..."`).

## 5. Checklist antes de entregar

1. `gradlew build` sem erro
2. `python3 check.py` → **331 checks, 0 FAIL** (no Windows: `py check.py`) e `./gradlew test` → **22 testes JUnit**
3. Testou no jogo (runClient) o que você mexeu
4. Atualizou `CHANGELOG.md` e o `WALKTHROUGH.md`
5. Comita (`git add -A && git commit -m "o que mudou"`)

## 6. Regras do projeto (resumo — detalhes no WALKTHROUGH.md §6)

- Nada de telemetria/coleta de dados. NUNCA.
- Ícone do medo = foto inteira (não recortar/redesenhar).
- O rabicho do balão é pequeno e de tamanho FIXO (nunca escala).
- Medo usa camada oficial de HUD (`registerAboveAll`) — funciona com shaders.
- **Avisos**: título "AVISO" letra por letra, **sem auto-ajuste no título**; a mensagem
  **sempre embaixo** (`translate(cx - w*S2/2, cy + 25*k)`). Nunca `ty - 12*k`.
- **Não redesenhar** o que já foi aprovado sem o dono pedir (vale pra balão, canon, FX).
- Publicação: só a versão atual fica no GitHub (`PUBLICAR.md`).

Bom código! 🎈
