# 🧭 WALKTHROUGH — Memória de trabalho do RP Tag

> **Este documento é o diário de bordo do projeto.** Ele existe para que qualquer
> pessoa (ou assistente) que continuar este trabalho saiba **o que já foi feito,
> como as coisas funcionam, o que NÃO fazer e como construir/testar/entregar** —
> sem se perder e sem refazer o que já passou por testes.
>
> 📌 **Regra: atualizar este documento NO FIM DE CADA CICLO** (junto com o
> CHANGELOG). O guia de *jogador* fica no `README.md` e no `COMO-USAR.md`;
> o registro detalhado por versão fica no `CHANGELOG.md`.

---

## 1. Estado atual

| Item | Valor |
|---|---|
| Versão | **3.58.1** (2026-10-08) |
| Commit | (ver `git log`) |
| Jar | `rptag-3.58.1.jar` (~1.892.264 B) |
| Checks | `check.py` → **94 PASS / 0 FAIL** |
| Smoke | Servidor NeoForge 21.1.249 real: Done ~0,5s, RCON OK, 0 erros do mod |
| Alvo | **Minecraft 1.21.1 + NeoForge 21.1.249**, Java 21 |
| Repo local | `/home/user/rp-tag` (commit local por ciclo; sem credenciais de push) |

---

## 2. O ritual de cada ciclo (como entregar)

1. **Editar as sources** do `/home/user/rp-tag` (feature/correção do ciclo).
2. **`python3 check.py`** — checks estáticos do código; só segue se 0 FAIL.
3. **Build** (seção 7) — `javac` SEM pipe em `exit 0`; só jarifica se compilou.
4. **Smoke real** (seção 7) — servidor NeoForge sobe com o jar + comandos via RCON + `grep` de erros = 0.
5. **Docs**: `CHANGELOG.md` (nova versão no topo), este WALKTHROUGH (seção 1 + seções afetadas), `divulgacao/COMO-USAR.md` (bloco "NOVO na X.Y.Z" no topo).
6. **Git**: commit local (recriar `.git/config` se sumiu — seção 7) — **push é por fora** (só o dono tem o PAT).
7. **Zip das sources**: `git archive --format=zip -o /home/user/rptag-fonte-<ver>.zip HEAD`.
8. **Entrega**: copiar o jar pra `/home/user`, `divulgacao/` e `run/mods/`; **`present_file` do jar no chat**; apagar jars velhos do workspace.
9. **Sempre avisar na resposta**: *"apague o jar antigo da pasta `mods` — no SERVIDOR e no CLIENTE"* (falha recorrente!).
10. Responder em **PT-BR**.

---

## 3. Mapa do repositório

### Raiz
| Arquivo | Papel |
|---|---|
| `check.py` | 85 checks estáticos (roda antes de qualquer entrega) |
| `toolchain.sh` | baixa JDK 21 + gera `nf-binary.jar` (MC desofuscado) + `cp.txt` (classpath) do zero |
| `build.gradle` / `settings.gradle` | metadados (versão do mod em `version = '...'`) |
| `CHANGELOG.md` | registro detalhado por versão |
| `WALKTHROUGH.md` | **este documento** (memória de trabalho) |
| `README.md` / `README.en.md` | vitrine do repo |
| `ENTREGA.md` / `PUBLICAR.md` | notas de processo |

### Código — `src/main/java/dev/rptag/`
| Área | Arquivos |
|---|---|
| Núcleo | `RPTagMod` (constantes/VERSION), `ModNetworking` (canal "5"), `ServerEvents`, `RPWorldData` |
| Tag/Persona | `RPTags`, `Persona`, `PersonaCommands`, `PersonaSyncPayload`, `SyncRPStatePayload`, `RPCommands` (`/rp`, `/rp admin`, `/do`), `RPChat` |
| Balões (comum) | `BalaoCommands` (`/balao`), `BubbleStyle`, `UpdateBubbleStylePayload`, `SetBubbleModePayload`, `OpenBubbleStylePayload`, `ChatBubblePayload`, `ScreenTextPayload`, `Stickers` |
| Balões (client) | `client/BubbleRenderer` (desenho no mundo), `client/BubbleStyleScreen` (tela `/balao`), `client/ClientBubbleCache`, `client/BubbleBackgrounds` |
| Nameplate | `client/NameplateRenderer`, `client/ClientGuiLayers` |
| Medo | `RPEffects` (efeito `rptag:medo`), `FearPayload`, `client/ClientEffects` (HUD com shaders: `registerAboveAll` + véu 208) |
| Lore Zone | `LoreZones` (tick/música), `LoreZoneCommands` (`criar/texto/som/musica/baixar/listar/remover`), `LorezoneMusicPayload` |
| Música custom | `MusicFileQueryPayload`/`ChunkPayload`/`AnswerPayload` (sync servidor→clientes, chunks 30 kB), `MusicSync`, `client/MusicaCustom` |
| Chat/Roll | `ChatCommands` (`/g`), `RollCommands` (`/roll NdM+K`) |

### Recursos — `src/main/resources/`
- `META-INF/neoforge.mods.toml` (versão aqui TAMBÉM)
- `assets/rptag/textures/gui/`: `bubble.png` (**512×256, texel de recheio = 248**), `quadrada.png` (**64×32, texel 255**), `ring.png`, `cartum.png`, `brilho.png`, `dashed.png` (192 px, períodos 8), `stickers_hi.png` (células 8×16), `medo_hd.png` (128, foto inteira + rampa alpha 14 px), `vinheta`, `preview_sky`, `grad_bar`, `linhas`, `dupla`, `gibi`
- `assets/rptag/textures/mob_effect/medo.png` (64, ícone do efeito)
- `assets/rptag/font/stickers.json` + `textures/font/stickers.png` (emojis da fala, código −0xE000)

---

## 4. O que o mod faz hoje (resumo por área)

- **Tag RP**: `/rp` liga/desliga; nome no chat, TAB e na cabeça com moldura.
- **Persona**: título/subtítulo/cor por jogador, sync pra todos.
- **Balões de fala** (o que o jogador escreve vira balão na cabeça):
  - 6 molduras (Clássica, Cartum, Quadrada, Costura, Brilho, Sem borda), cores personalizáveis, adesivo à esquerda + emojis à direita;
  - **3.56**: texto quebra em linhas equilibradas (`MAX_TEXT_WIDTH = 130`) → mais palavras = balão cresce **pra cima**; rabicho **pixel-art em degraus** (14×4 + 7×4) com contorno, igual nas todas as molduras;
  - **3.56.1**: rabicho amostra a MESMA janela de texels da pílula (`bubble.png` 252–260 × 124–132, texel 248) → cor **exata**; Quadrada (texel 255) segue igual;
  - tela `/balao` com prévia fiel (quebra a fala com as mesmas regras — `wrapPrev` — e ajusta o zoom; cor do rabicho via `escalaTexel` 248/255).
- **Medo**: `/effect give <jogador> rptag:medo <s>` — ícone de foto inteira com borda dissolvendo + véu azul-noite 208 que esconde a tela; camada oficial de HUD (`registerAboveAll`), **funciona com shaders**.
- **Lore Zone**: `/lorezone criar <id> <raio> <titulo>` cria esfera de lore; texto, som (preset ou `1x na entrada`) e **música**; `/lorezone baixar <url>` baixa de verdade no servidor (`config/rptag/musicas/`) e **sincroniza pros clientes** (todo mundo ouve).
- **Roll**: `/roll` = 1d20; `/roll 2d6+3`, `/roll +3` = 1d20+3.
- **Chat**: `/g` global; chat local por proximidade (`/rp admin chatlocal`).

---

## 5. Histórico (o que aconteceu até aqui)

> Texto completo por versão: `CHANGELOG.md`. Resumo com foco em decisão:

| Versão | O que marcou |
|---|---|
| 3.3x | base do chat/tag/persona e dos primeiros comandos |
| 3.40–3.42 | polimento da tag e comandos admin |
| 3.49.0 | reorganização das telas do `/balao` (2 colunas adesivo/emojis) |
| 3.50.0 | música custom nas Lore Zones; nuvem do balão (depois removida) |
| 3.51.x | rabicho discreto; Quadrada quadrada ao todo; balão 2 colunas; tela à prova de crash |
| 3.52.0 | som de zona novo (muitos presets) + sliders de volume (Ambiente = `/lorezone som`, Música = `/lorezone musica`) |
| 3.53.0 | ajustes de balão/nametag |
| 3.54.x | medo com **shaders** (HUD `registerAboveAll`); ícone do medo = **foto inteira** (64/128) |
| 3.55.0 | X vermelho na prévia sem tooltip; `/lorezone baixar` REAL + sync v5 (chunks 30 kB); `/lorezone musica <id> 1x <som>`; `/roll +3`; `/balao teste` **removido** (prévia usa a última fala real); ícone do medo com borda dissolvendo; lorezone explicada como ESFERA |
| 3.56.0 | **balões reescritos**: wrap 130 (cresce pra cima), rabicho pixel em degraus p/ todas as molduras, prévia multi-linha, textura `bubble_tail.png` removida |
| 3.56.1 | rabicho na cor **exata** do balão (texel 248); `/rp ajuda` sem a linha amarela do WALKTHROUGH |
| 3.57.0 | **tons de fala** (estudo do mod Fabric "roleplay-speech-bubbles"): `/pensar` novo (itálico + parênteses), `/s` agora vira balão MAIÚSCULO+negrito, `/w` itálico que não funcionava CORRIGIDO; pacote `ChatBubblePayload` ganhou campo `tone`. ⚠️ O mod Fabric embute telemetria (`creativemode-telemetry`) — jamais replicar |
| 3.58.0 | **rabicho = recorte na silhueta** (dono escolheu a opção B na prévia de 4 designs): textura `tail.png` texel branco + escala 248, contorno único sem emenda, prévia idêntica ao mundo |
| 3.58.1 | rabicho 26×18 saiu GIGANTE na print → **13×9 FIXO (padrão)**, nunca escala com o balão; pilha +12 |

Commits recentes: `0e93318` (3.56.1) ← `69a211b` (3.56.0) ← `0d3f76e` (3.55.0) ← `928647f` (3.49.0). **Hashes de 3.50–3.54.1 foram perdidos** (regressão do `.git` num turno) — não referenciá-los.

---

## 6. Decisões permanentes & reprovados (NUNCA voltar)

### Como o trabalho é feito
- Push no GitHub **sem credenciais**: commit local sempre; quem faz push é o dono (PAT dele). "Arquivos pro GitHub" = commit + zip das sources + jar via `present_file`.
- Entrega do jar **sempre** com `present_file` (o usuário cobra o download).
- Respostas em PT-BR; testes ANTES de entregar; smoke RCON por ciclo.
- Qualidade visual nível mods famosos; telas 1.21 usam `renderBackground` com fill opaco.

### Visual do balão
- ❌ Faixa/banda no balão; ❌ emoji via fonte na fala; ❌ emoji-tint no mundo; ❌ bandas de fonte; ❌ dashed antigo; ❌ balão-nuvem; ❌ contadores "emojis x/6"; ❌ botões Auto/"Minha cor"; ❌ ordem antiga dos botões; ❌ card escuro na prévia; ❌ editor baú.
- ❌ **Rabicho triângulo antigo** (`bubble_tail.png` com texels quebrados — reprovado na 3.56) e ❌ quadrado-conector; ❌ degraus pixel-art (3.56–3.57); ❌ **rabicho GRANDE** (26×18 da 3.58.0, reprovado na print). ✅ Rabicho atual = **recorte na silhueta PEQUENO e FIXO** (`tail.png` texel branco, 13×9, contorno único, cor exata).
- ✅ Balão cresce **pra cima** com o texto (linhas equilibradas, nunca tirinha deitada); ✅ recheio cobre o encaixe do rabicho (costura invisível).
- Quadrada = quadrada AO TODO; Sem borda por último em 2 fileiras de 3; tela `/balao` à prova de crash.

### Medo
- Ícone = **FOTO INTEIRA** (carinha + palavra "medo") — nunca recortar/redesenhar; borda dissolvendo em alpha aprovada.
- **COM shaders**: camada oficial de HUD (`registerAboveAll`) + véu 208 — não voltar para `RenderGuiEvent.Post`.
- Mensagem só pra admin; paleta azul-noite; véu que ESCONDE a tela.

### Outros
- ❌ Nuvem no balão (removida a pedido no 3.50 — manter fora).
- ❌ **TELEMETRIA/coleta de dados JAMAIS** (o mod Fabric estudado no 3.57.0 embute `creativemode-telemetry` — jar-in-jar que rastreia sessões/mods; serviu só de referência de mecânica).
- Tons de fala (3.57.0): `tone` 0/1/2 no `ChatBubblePayload`; pensar/sussurro = itálico (fonte vanilla mantém a largura → medida intacta), grito = fake bold (2ª passada 0,75px).
- `/balao teste` **não existe mais** — testar é escrever no chat (a prévia usa a última fala).
- Música baixada fica no **SERVIDOR** e sincroniza pros clientes ("iniciado/concluído"); todo mundo ouve.
- Sons "1x na entrada" da lorezone: opção pedida explicitamente (manter).

---

## 7. Receitas operacionais

### Toolchain do zero (o `/var/tmp`some entre sessões!)
```bash
bash /home/user/rp-tag/toolchain.sh   # JDK 21 + nf-binary.jar + cp.txt (tudo automático)
```
- Gera: `/var/tmp/java-21/`, `/var/tmp/nf-binary.jar` (MC 1.21.1 mapeado + NeoForge), `/var/tmp/cp.txt` (classpath completo: nf-binary + **todos** os artifacts do cache NFRT — brigadier, etc — + libs do neoforge).
- Internamente usa NFRT com um `neoform-edit.zip` **INTEIRO** (não só o `config.json` — o NFRT lê `config/joined.tsrg` de dentro!) com jvmargs de decompile `-Xmx1450m -XX:+UseSerialGC`.

### Build
```bash
cd /home/user/rp-tag
find src/main/java -name "*.java" > /var/tmp/sources.txt
export PATH=/var/tmp/java-21/bin:$PATH
rm -rf build/classes build/libs && mkdir -p build/classes
# SEM pipe: só jarifica se exit 0 (senão sai jar STALE)
javac --release 21 -encoding UTF-8 -proc:none -nowarn \
  -cp "$(cat /var/tmp/cp.txt)" -d build/classes @/var/tmp/sources.txt \
  && cp -r src/main/resources/* build/classes/ \
  && (cd build/classes && jar --create --file ../libs/rptag-<ver>.jar .)
cp build/libs/rptag-<ver>.jar /home/user/ /home/user/divulgacao/ /home/user/run/mods/
```

### Smoke (servidor real + RCON)
1. Se `run/libraries/net/minecraft/server/` estiver vazio → baixar o installer `neoforge-21.1.249-installer.jar` e `java -jar ... --installServer .` (~20 s).
2. Subir em background: `JAVA_HOME=/var/tmp/java-21 PATH=/var/tmp/java-21/bin:$PATH ./run.sh` (cwd `run/`).
3. Esperar `Done (` no `run/logs/latest.log`.
4. RCON: `127.0.0.1:25575`, senha `test123` (pacote: struct `<ii` + type 3 no login). Testar `rp ajuda` (ler a resposta INTEIRA — os comandos respondem com paginação longa).
5. `grep -icE "rptag.*(exception|ERROR)" run/logs/latest.log` → tem que ser 0.
6. Parar o servidor no fim. `lorezone criar` por RCON falha ("A player is required") — esperado.

### Git (commit local)
Se o `.git/config` sumir (não vai pro snapshot), recriar:
```ini
[core]
	repositoryformatversion = 0
	filemode = true
	bare = false
	logallrefupdates = true
[remote "origin"]
	url = https://github.com/EduCafilista/rp-tag.git
	fetch = +refs/heads/*:refs/remotes/origin/*
[branch "main"]
	remote = origin
	merge = refs/heads/main
[user]
	name = EduCafilista
	email = EduCafilista@users.noreply.github.com
```

### Geometria do balão (pra mexer sem quebrar)
- `pillW = max(34, textW + pad16 + leftW + rightW)`; `pillH = speechH + 8`; `speechH = 10 × n° de linhas`; `yP1 = 0`, texto acima, topo em `yP0 = yText − 4`.
- `leftW` (adesivo) = 14+6 se tiver; `rightW` (emojis) = n×14 + (n−1)×3 + 6.
- Ordem de desenho (z): sombra −0.20 → contorno −0.15 → **rabicho pixel** (contorno −0.15, recheio −0.12) → recheio da pílula −0.10 (cobre o encaixe) → texto → adesivos.
- Rabicho (3.58.1): `tail.png` 32×24 (texel branco 255, curva de HQ); quad **13×9 FIXO** (border +1px) em `cxT = x0 + pillW/2 + 1`, `ty0 = yP1 − 1.5`; contorno = quad expandido 1px (z −0.15) → recheio (z −0.12, `texel248(fill)` fora da Quadrada) → pílula (z −0.10) cobre o encaixe. Altura da pilha: `+ 12`. **Regra: o rabicho é tamanho padrão e NUNCA escala com o balão.**
- Prévia (menu): `wrapPrev(fala, 110)` com zoom K=2.0 (cai pra 1.5/1.2/0.95 conforme o nº de linhas), `simH = linhas × lineHeight × K + 8`, rabicho via `g.fill` + `escalaTexel` (×248/255 fora da Quadrada).

---

## 8. Pendências / próximas ideias

- Ideia do mod Fabric ainda NÃO portada: tela de composição de fala com botão "Enviar balão" + seletor de tom (aqui o fluxo é escrever no chat — discutir com o dono antes).
- Lembrete: o `WALKTHROUGH.en.md` ainda é o guia antigo de jogador em inglês (legado); se o repositório for público, vale atualizar ou remover.
- Lembrete: o `WALKTHROUGH.en.md` ainda é o guia antigo de jogador em inglês (legado); se o repositório for público, vale atualizar ou remover.

---

*Última atualização: ciclo 3.56.1 (2026-10-08), commit `0e93318`.*
