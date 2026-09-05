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
| `rptag-1.2.1.jar` | a aba *Releases* deste repositório |

> ⚠️ A versão do mod acompanha a do Minecraft: o `rptag-1.2.1.jar` é para **1.21.1**. Outra versão do jogo precisa de recompilação.

---

## Passo 1 — Instalar no servidor

1. Instale o NeoForge no servidor (o instalador pergunta o caminho da pasta do servidor).
2. Na pasta do servidor, entre em `mods/`.
3. Copie o `rptag-1.2.1.jar` para dentro dela.
4. Inicie o servidor uma vez para gerar os arquivos (aceite o EULA no `eula.txt` com `eula=true`).

**Como saber se funcionou:** no log de boot procure a linha

```
RP Tag 1.2.1 (rptag)
```

Pronto — a partir daqui a tag já funciona no **chat** e na **TAB** para todos.

---

## Passo 2 — Instalar no cliente (opcional, para ver a tag na cabeça)

1. No launcher, crie/edite o perfil do NeoForge 1.21.1.
2. Abra a pasta do jogo (opção "abrindo a pasta do jogo" no launcher) e entre em `mods/`.
3. Copie o mesmo `rptag-1.2.1.jar` para lá.
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

O jar sai em `build/libs/rptag-1.2.1.jar`. A primeira compilação demora alguns minutos (baixa e prepara o Minecraft automaticamente — você não precisa instalar nada do jogo).

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
