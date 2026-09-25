# ENTREGA — Guia pra continuar o RP Tag

Ola! Este documento e o mapa completo do projeto pra quem vai dar
continuidade. Versao atual: **3.43.0** (veja o `CHANGELOG.md` pra todo o
historico). Minecraft **1.21.1** + **NeoForge** (moddev 2.0.146).

## O que o mod faz hoje

- **Tag RP**: nome com tag `(RP)`/`(OFF RP)` em small caps no chat, nametag
  e TAB (o TAB atualiza ao ligar/desligar).
- **Balao de fala** acima da cabeca: 6 molduras distintas (Classica, Cartum,
  Dupla, Costura, Brilho, Gibi), cor/borda/letra/duracao personalizaveis na
  tela `/balao`, adesivo + emotes como EMBLEMA na borda, pilha de ate 3
  baloes sem sobreposicao, billboard (cada jogador ve o balao virado pra
  si), compensacao de distancia (legivel de longe), quebra de linha
  equilibrada (nada de palavra orfa).
- **Canais de chat RP**: `/g` (global), `/s` (grito), `/w` (sussurro),
  `/me` e `/do` (acoes — so no chat RP, nunca viram balao; quem esta
  OFF RP na regiao nao ve).
- **Admin**: `/rp admin balao <jogador> on|off` (default = NINGUEM tem
  balao), `/rp admin balaolist`, `/rp admin chatlocal on|off`,
  `/rp admin bolhas on|off`.
- **Utilidade**: `/balao teste <frase>` (previa na hora) e `/rp status`
  (diagnostico completo).

## Como buildar

1. Instale o **JDK 21** (templeate: `openjdk-21-jdk-headless`).
2. `./gradlew build` (ou `gradle build` com Gradle 9.2.1). O jar sai em
   `build/libs/rptag-<versao>.jar`. Precisa de ~4GB de RAM livres (o
   NeoForm decompila o Minecraft na primeira vez — demora ~8 min frio).
3. Pra testar: `./gradlew runServer` (aceite o eula em `run/eula.txt`).

## Arquitetura (mapa rapido)

| Arquivo | Papel |
|---|---|
| `client/BubbleRenderer.java` | O coracao visual: geometria da pilula (ANCORA: base = yP1 = 0, cresce so pra cima!), wrap equilibrado (`wrapBalanced`), emblema na borda, quads HD dos emojis, 6 molduras (`RING_MARGIN`), pop/fade/pilha, compensacao de distancia (`distComp`) |
| `client/BubbleStyleScreen.java` | Tela `/balao` (menu cartoon de placas, previa IGUAL ao mundo, emoji picker, adesivo na borda com hover) |
| `client/ClientBubbleCache.java` | Pilha de baloes por jogador (max 3, expira, off limpa tudo) |
| `client/BubbleBackgrounds.java` | Cores derivadas (recheio/borda/texto legivel) |
| `client/Stickers.java` (raiz) | Os 80 emojis Noto (atlas POT `font/stickers.png` 128x256 + `gui/stickers_hi.png` 1024x2048, codepoints 0xE000+) |
| `RPEffects.java` | Efeitos de status (MEDO = `rptag:medo`, icone no HUD via `textures/mob_effect/medo.png`) |
| `client/ClientEffects.java` | Tremedeira de camera + vinheta do MEDO + texto GRANDE central (avisos) |
| `ServerEvents.java` | Estado RP/persona, nomes (chat/TAB), payload do estilo BLINDADO (clamp 0..5, 0..100%, 2..20s, stickers <= 4/8) |
| `RPChat.java` | Canais + `spawnBubble` (gates: global on, opt-out, ADMIN, modo; ANTI-FLOOD 250ms) + `sendRpAction` (chat RP: so EM RP na regiao) |
| `ChatCommands.java` | /g /s /w /me /do (o /me vanilla e removido) |
| `RPWorldData.java` | Persistencia NBT (tag RP, persona, chatLocal, bubblesOn, allowlist, estilos) |

## REGRAS DE OURO (aprovados/reprovados em batalha — nao reverter!)

1. **EMOJI NUNCA via fonte na fala** = blocos pretos. So via quads do atlas
   (`emojiQuad`) — na fala (emoji digitado) e no emblema da borda.
2. **PILULA = tamanho EXATO do texto** (`pillH = speechH + 8`). NUNCA criar
   faixa/linha/expansao pro emoji (o "ar" gigante que o usuario odiou).
3. **Ancora**: base da pilula em `yP1 = 0` (o eixo Y e invertido: positivo
   DESCE sobre o nametag). Crescent pra cima, sempre.
4. **Adesivo/emotes = EMBLEMA NA BORDA** (metade pra fora, na frente),
   nunca sobre letras.
5. **Sticker no mundo = bitmap COLORIDO, NUNCA tintado** (o laço preto e
   tint na ARTE do asset, isso e permitido).
6. **Balao e privilegio de admin** (default NINGUEM) e `/balao modo off`
   some NA HORA.
7. **Render nunca crasha**: `catch (Throwable)` com auto-desliga da sessao.
8. MOLDURA Costura usa `dashed.png` (768x256, periodos de 8px — nao
   esticar); texturas POT sempre (2^n).
9. `/me` = acao no chat RP (sem balao). Balao e FALA.
10. Testes (suete de greps) + smoke RCON antes de toda entrega.

## Roadmap / ideias do dono

- ~~**Comando do MEDO**~~ **FEITO na 3.44.0**: `/rp admin medo <jogador>
  [segundos]` (camera treme + vinheta vermelha nas bordas) e
  `/rp admin avisar <jogador> <texto>` (texto GRANDE central, cores via
  &). Canais fear/screen_text, protocolo de rede v3.
- Diferenciar ainda mais os estilos / "menos pixel e mais imagem".
- Regioes RP avancadas (o chat de acoes ja respeita quem esta EM RP).

## Credito e licencas

- Emojis: **Noto Emoji** (googlefonts/noto-emoji) — Apache 2.0 / OFL 1.1.
- Repo: `github.com/EduCafilista/rp-tag` (publishing: apagar jar antigo +
  upload + Release; NUNCA apagar o repo).

Bom trabalho! — RP Tag 3.43.0
