[README.md](https://github.com/user-attachments/files/31842984/README.md)
# rp-tag
Mod NeoForge 1.21.1 que mostra (ʀᴘ)/(ᴏꜰꜰ ʀᴘ) no nome dos jogadores e adiciona o comando /rp — feito para servidores de RPG.

# RP Tag

**Mod para NeoForge 1.21.1** — mostra **(ʀᴘ)** ou **(ᴏꜰꜰ ʀᴘ)** no nome dos jogadores e adiciona o comando **`/rp`** para os próprios jogadores alternarem o modo. Feito para servidores de RPG.

> 🎮 **Novo por aqui?** Siga o [WALKTHROUGH.md](WALKTHROUGH.md) — guia passo a passo com tudo (instalação, comandos, customização e solução de problemas). Para publicar o projeto no GitHub, veja [PUBLICAR.md](PUBLICAR.md).

## ✨ O que ele faz

- **Nome acima da cabeça (nametag)** — o nome ganha a tag entre parênteses com
  fonte diferente (small capitals Unicode): **(ʀᴘ)** em **ciano** ou
  **(ᴏꜰꜰ ʀᴘ)** em **cinza**. Apenas a tag muda de cor — o nome fica normal.
- **Chat** — as mensagens aparecem como `Alex (ʀᴘ): oi` / `Steve (ᴏꜰꜰ ʀᴘ): oi`.
- **Lista de jogadores (TAB)** — o nome também vem com a tag.
- **Comando `/rp`** liberado para todos os jogadores:

  | Comando | Quem usa | Efeito |
  |---|---|---|
  | `/rp` | todos | alterna entre RP e OFF RP |
  | `/rp on` / `/rp off` | todos | define o estado direto |
  | `/rp status` | todos | mostra o estado atual |
  | `/rp set <jogador> on\|off` | admins (permissão 2 / OP) | define o estado de outro jogador |

- **Estado salvo no mundo** — sobrevive a relog, morte e reinício do servidor.
  Por padrão, todo mundo começa em **OFF RP**.
- **Sem dependências** — só precisa do NeoForge.

## 📥 Instalação

**Requisitos:** Minecraft 1.21.1 + NeoForge 21.1.x + Java 21

1. Instale o **NeoForge 1.21.1** no servidor.
2. Copie `rptag-1.2.1.jar` para a pasta `mods/` do **servidor**.
   → a tag no **chat** e na **TAB** já funciona pra todo mundo.
3. *(Opcional)* Jogadores que quiserem ver a tag **em cima da cabeça** colocam o
   jar também na pasta `mods/` do **cliente**.

## 🔧 Compilando do zero

Requisitos: **JDK 21** e internet.

```bash
./gradlew build
```

O jar sai em `build/libs/rptag-1.2.1.jar`.

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
