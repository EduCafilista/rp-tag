# Avisos em vídeo — piloto MORTE (4.2.0) Implementation Plan

>**For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O aviso MORTE "Linha Reta" (estilo 4) passa a tocar um vídeo renderizado no Blender (pares JPEG cor+alfa, 1280×720, 24 fps) com o texto e os sons de sempre por cima, e volta sozinho ao FX procedural em qualquer falha.

**Architecture:** `VideoFx` é um player sem camada própria: `abrir(id)` lê `fx.json` e liga uma thread que decodifica quadros; `desenhar(g, t)` escolhe o quadro pelo relógio e desenha em tela cheia. `MorteAvisoFx` compõe a tela: no modo vídeo desenha cinza → vídeo → texto/sons e pula o desenho procedural; se o player falhar, segue procedural no mesmo `t`. Tempos ficam em `MorteTempos` (Java puro), fonte única para o código, a cena do Blender e o teste dos marcadores.

**Tech Stack:** Java 21, NeoForge 21.1.249 (moddev 2.0.146), `javax.imageio`, Gson, JUnit 5; Blender 5.2 (EEVEE) via MCP do Blender; Python 3.13 + Pillow 10.4 para exportar/validar.

**Spec:** `docs/superpowers/specs/2026-10-19-avisos-em-video-design.md` (inclui as duas revisões feitas ao escrever este plano).

## Global Constraints

- Minecraft 1.21.1, NeoForge `21.1.249`, Java 21. Sem dependência nova em runtime (só `testImplementation`).
- Vídeo: **1280×720, 24 fps, 144 quadros (6 s)**; cor `NNNN_c.jpg` qualidade **85**, alfa `NNNN_a.jpg` tons de cinza qualidade **90**; numeração `%04d` a partir de `0000`.
- Assets em `assets/rptag/fx/<id>/fx.json` e `assets/rptag/fx/<id>/frames/`.
- Aviso só aparece com **≥ 4 quadros prontos**; buffer **≤ 8 quadros**; sem quadro pronto por **> 0,5 s** → fallback.
- Thread do loader: daemon, nome **`rptag-fx-loader`**.
- Config do cliente: `config/rptag/cliente.properties`, chave `fxVideo`, padrão `true`.
- Comando de cliente: `/rpfx status|on|off`.
- Regra de ouro 7: render nunca crasha — qualquer `Throwable` desliga o player na sessão e mantém o procedural.
- Texto: título "AVISO" + mensagem **embaixo** (padrão 3.96.4) — continua o código atual do `MorteAvisoFx`.
- Versão final **4.2.0** em `RPTagMod.VERSION`, `build.gradle`, `neoforge.mods.toml`.
- Comentários e mensagens em português sem acento no código Java (padrão atual do projeto: `// (4.2.0) ...`).

## Review Focus

1. **Aviso novo chega enquanto o vídeo toca** — o vídeo anterior para, a thread antiga morre e o novo começa do quadro 0 (sem misturar quadros de dois avisos). → teste em Task 3 (`VideoFxEstadoTest.reabrirDescartaGeracaoAntiga`).
2. **Sair do mundo no meio do vídeo** — thread encerra, buffers liberados, nada vaza para o próximo mundo. → Task 4, passo de `onLoggingOut` + verificação manual em Task 7.
3. **PC lento (decode atrasado)** — pula quadros pelo relógio em vez de câmera lenta; depois de 0,5 s sem quadro troca para o procedural no mesmo `t`. → teste `FxTimelineTest.quadroPeloRelogioPula` (Task 1) e `VideoFxEstadoTest.semQuadroMeioSegundoFalha` (Task 3).
4. **Arquivo de config ausente ou corrompido** — usa `fxVideo=true` e recria o arquivo, sem crash. → teste `FxConfigTest` (Task 2).
5. **`fx.json` com contagem de quadros diferente dos arquivos** — `validar.py` falha no `check.py`; no jogo, quadro faltando = falha de decode → fallback. → Task 6 (`validar.py`) e Task 7 (JPEG corrompido).

---

### Task 1: JUnit + tipos puros (`MorteTempos`, `FxTimeline`, `FxCor`)

**Files:**
- Modify: `build.gradle` (dependências de teste + `test { useJUnitPlatform() }`)
- Create: `src/main/java/dev/rptag/client/MorteTempos.java`
- Create: `src/main/java/dev/rptag/client/FxTimeline.java`
- Create: `src/main/java/dev/rptag/client/FxCor.java`
- Modify: `src/main/java/dev/rptag/client/MorteAvisoFx.java:29-30` (usa `MorteTempos`)
- Test: `src/test/java/dev/rptag/client/FxTimelineTest.java`, `src/test/java/dev/rptag/client/FxCorTest.java`

**Interfaces:**
- Produces:
  - `final class MorteTempos { static final float DUR = 6.0f, FLAT = 3.9f, ECG_END = 4.6f; static final float[] BEATS = {0.6f, 1.5f, 2.5f, 3.7f}; static final float TEXTO_ENTRA = 0.5f, CINZAS = DUR - 1.6f, TEXTO_SAI = DUR - 1.0f; }` — sem import de Minecraft.
  - `final class FxTimeline { static FxTimeline parse(String json) throws IllegalArgumentException; int fps(); int frames(); int width(); int height(); boolean aditivo(); float duracao(); int quadroEm(float t); List<Integer> marcadores(String nome); }` — `quadroEm` = `floor(t*fps)`, `-1` se `t<0`, `frames` se `t>=duracao()` (= fim). `parse` rejeita fps≤0, frames≤0, width/height ≠ 1280/720, `blend` fora de `normal|aditivo`, marcador fora de `[0, frames)`.
  - `final class FxCor { static int abgr(int rgb, int alfa); }` — `rgb` = `0xRRGGBB` (bits altos ignorados), `alfa` 0..255 (cinza do `_a.jpg`, usar o canal vermelho); retorno no formato do `NativeImage.setPixelRGBA` (ABGR).

- [ ] **Step 1: Adicionar teste ao Gradle**

Em `build.gradle`, adicionar:
```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'com.google.code.gson:gson:2.11.0'
}
test { useJUnitPlatform() }
```

- [ ] **Step 2: Escrever os testes que falham**

`FxTimelineTest`:
- `parseValido`: JSON do spec (fps 24, frames 144, 1280×720, blend normal, `batida` em 14 e 36) → `fps()==24`, `frames()==144`, `duracao()==6.0f`, `aditivo()==false`, `marcadores("batida")==List.of(14,36)`, `marcadores("x").isEmpty()`.
- `parseRejeita`: `assertThrows(IllegalArgumentException)` para fps 0, frames 0, width 1920, blend `"xor"`, marcador frame 144.
- `quadroPeloRelogioPula`: `quadroEm(-0.01f)==-1`, `quadroEm(0f)==0`, `quadroEm(1.0f)==24`, `quadroEm(1.49f)==35` (de 24 para 35 sem passar pelos do meio = pulo), `quadroEm(6.0f)==144`.

`FxCorTest`:
- `abgr(0x112233, 0x80) == 0x80332211`; `abgr(0xFF112233, 0) == 0x00332211`; `abgr(0xFFFFFF, 255) == 0xFFFFFFFF`.

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --console=plain`
Expected: FAIL de compilação (`FxTimeline`/`FxCor` não existem).

- [ ] **Step 4: Implementar `MorteTempos`, `FxTimeline` (Gson `JsonParser`), `FxCor`; trocar as constantes `DUR/FLAT/ECG_END/BEATS` do `MorteAvisoFx` por `MorteTempos.*` (import estático), sem mudar valores**

- [ ] **Step 5: Rodar testes e a suíte**

Run: `./gradlew test --console=plain` → `BUILD SUCCESSFUL`, 4 testes passando.
Run: `python check.py` → `312 PASS / 0 FAIL` (o refactor não pode quebrar nenhum grep; se algum grep procurava `DUR = 6.0f` dentro de `MorteAvisoFx`, ajustar o grep para olhar `MorteTempos.java`).

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/java/dev/rptag/client/{MorteTempos,FxTimeline,FxCor,MorteAvisoFx}.java src/test check.py
git commit -m "4.2.0: base do player de video (FxTimeline, FxCor, MorteTempos) + JUnit"
```

---

### Task 2: Config do cliente (`FxConfig`) + `/rpfx`

**Files:**
- Create: `src/main/java/dev/rptag/client/FxConfig.java`
- Modify: `src/main/java/dev/rptag/client/BubbleRenderer.java:183` (chamar `FxConfig.registrarComando(event)` dentro de `onClientCommands`)
- Test: `src/test/java/dev/rptag/client/FxConfigTest.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `final class FxConfig { static boolean video(); static void definirVideo(boolean on); static void carregar(Path arquivo); static void registrarComando(RegisterClientCommandsEvent e); }`
  - `carregar` é chamado com `FMLPaths.CONFIGDIR.get().resolve("rptag/cliente.properties")` na primeira chamada de `video()` (lazy). Ausente → cria com `fxVideo=true`. Ilegível ou valor inválido → `true` + `RPTagMod.LOGGER.warn`.
  - `/rpfx status` → `"RP Tag FX: video LIGADO|DESLIGADO — N aviso(s) com video"` (N vem de `VideoFx.contarVideos()`, Task 3; até lá a Task 2 escreve `0` e a Task 3 troca). `/rpfx on|off` → `definirVideo` + mensagem.
  - A lógica de arquivo fica em métodos que recebem `Path` (testáveis sem Minecraft); só `registrarComando` toca em classes do jogo.

- [ ] **Step 1: Teste que falha** — `FxConfigTest` com `@TempDir`:
  - `ausenteCriaPadrao`: `carregar(dir/"cliente.properties")` → `video()==true` e o arquivo passa a existir contendo `fxVideo=true`.
  - `lixoViraPadrao`: arquivo com bytes `\u0000ÿxx=` → `video()==true`, sem exceção.
  - `offPersiste`: `definirVideo(false)`; `carregar` de novo → `video()==false`.
- [ ] **Step 2:** `./gradlew test` → FAIL (classe não existe).
- [ ] **Step 3: Implementar `FxConfig`** com `java.util.Properties`; escrever com `Files.newBufferedWriter` criando `rptag/` se faltar.
- [ ] **Step 4:** `./gradlew test` → PASS; `./gradlew build` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — `git commit -m "4.2.0: config do cliente fxVideo + /rpfx status|on|off"`

---

### Task 3: Player (`VideoFrameLoader` + `VideoFx`)

**Files:**
- Create: `src/main/java/dev/rptag/client/VideoFrameLoader.java`
- Create: `src/main/java/dev/rptag/client/VideoFx.java`
- Modify: `src/main/java/dev/rptag/client/FxConfig.java` (status usa `VideoFx.contarVideos()`)
- Test: `src/test/java/dev/rptag/client/VideoFxEstadoTest.java`

**Interfaces:**
- Consumes: `FxTimeline.parse/quadroEm/frames/aditivo`, `FxCor.abgr`, `FxConfig.video()`.
- Produces:
  - `enum VideoFx.Estado { CARREGANDO, OK, FALHOU, FIM }`
  - `static boolean VideoFx.abrir(String id)` — `false` se `!FxConfig.video()`, player desligado na sessão, ou `assets/rptag/fx/<id>/fx.json` ausente/inválido. Fecha o vídeo anterior antes.
  - `static VideoFx.Estado VideoFx.desenhar(GuiGraphics g, float t)` — desenha o quadro de `t` em tela cheia (`g.blit` da textura `rptag:fx_video` em `0,0,guiWidth,guiHeight`, mistura normal ou aditiva conforme `aditivo()`); envia para a GPU só quando o índice muda.
  - `static void VideoFx.fechar()`; `static int VideoFx.contarVideos()` (conta ids com `fx.json` em `assets/rptag/fx/`, via `ResourceManager.listResources("fx", p -> p.getPath().endsWith("fx.json"))`).
  - `VideoFrameLoader(String id, FxTimeline tl, int geracao)`: thread daemon `rptag-fx-loader`; `NativeImage pegar(int quadro)` (devolve o quadro pronto ou `null`; descarta os anteriores); `int prontos()`; `boolean erro()`; `void parar()`. Decodifica `frames/%04d_c.jpg` e `_a.jpg` com `ImageIO.read`, monta `NativeImage` 1280×720 com `FxCor.abgr`, fila limitada a 8.
  - **Máquina de estado testável sem GPU:** a decisão de estado fica em `static VideoFx.Estado VideoFx.decidir(int quadro, int frames, boolean temQuadro, boolean erroLoader, float segSemQuadro, int prontos, boolean comecou)`:
    - `quadro >= frames` → `FIM`; `erroLoader` → `FALHOU`; `!comecou && prontos < 4` → `CARREGANDO`; `!temQuadro && segSemQuadro > 0.5f` → `FALHOU`; senão `OK`.
  - Geração: cada `abrir` incrementa um `int geracao`; quadros de loader com geração antiga são fechados (`NativeImage.close()`) e ignorados.
  - Todo `desenhar` dentro de `try { } catch (Throwable e)` → loga, marca player desligado na sessão, retorna `FALHOU`.

- [ ] **Step 1: Teste que falha** — `VideoFxEstadoTest`:
  - `fimGanhaDeTudo`: `decidir(144,144,false,true,9,0,true)==FIM`.
  - `carregaAteQuatro`: `decidir(0,144,false,false,0,3,false)==CARREGANDO`; com `prontos=4` → `OK`.
  - `semQuadroMeioSegundoFalha`: `decidir(30,144,false,false,0.51f,8,true)==FALHOU`; com `0.49f` → `OK`.
  - `erroDoLoaderFalha`: `decidir(10,144,true,true,0,8,true)==FALHOU`.
  - `reabrirDescartaGeracaoAntiga`: `VideoFx.aceitaGeracao(int quadroGeracao, int atual)` → `false` para `(1,2)`, `true` para `(2,2)`.
- [ ] **Step 2:** `./gradlew test` → FAIL.
- [ ] **Step 3: Implementar `VideoFrameLoader` e `VideoFx`** (a textura é uma `DynamicTexture` criada uma vez com `NativeImage(1280,720,false)` e registrada em `Minecraft.getInstance().getTextureManager().register(ResourceLocation.fromNamespaceAndPath("rptag","fx_video"), tex)`; a cada quadro novo `tex.setPixels(img); tex.upload();` — `setPixels` fecha a imagem anterior).
- [ ] **Step 4:** `./gradlew test` → PASS; `./gradlew build` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** — `git commit -m "4.2.0: player de video (loader em thread + estados + fallback)"`

---

### Task 4: MORTE em modo vídeo + fechamento ao sair

**Files:**
- Modify: `src/main/java/dev/rptag/client/MorteAvisoFx.java` (`iniciar`, `render`)
- Modify: `src/main/java/dev/rptag/client/ClientEvents.java:65` (`onLoggingOut` chama `VideoFx.fechar()`)
- Modify: `check.py` (greps 4.2)

**Interfaces:**
- Consumes: `VideoFx.abrir("morte")`, `VideoFx.desenhar(g, t)`, `VideoFx.Estado`, `VideoFx.fechar()`.
- Produces: `MorteAvisoFx.iniciar(String, String)` com a mesma assinatura (o `ClientEffects` não muda); campo `private static boolean video`.

Comportamento:
- `iniciar`: `video = VideoFx.abrir("morte")`; log `"RP Tag: LINHA RETA em VIDEO"` ou `"... procedural"`.
- `render`, com `video == true`:
  1. camada 1 só com o **cinza** (`b.rect(0,0,w,h, col(0x464650, ...))`) — sem as vinhetas (o vídeo traz).
  2. `Estado e = VideoFx.desenhar(g, t)`:
     - `CARREGANDO` → `inicio = System.nanoTime()` (o relógio não anda) e `return`.
     - `FALHOU` → `video = false`, log `"RP Tag: video do aviso falhou — seguindo procedural"`, e continua o render procedural **neste mesmo frame** (não reinicia `inicio`).
     - `FIM`/`OK` → segue.
  3. pula as seções 1-vinhetas, 2 (cinzas), 3 (crânio), 4 (ECG) e o brilho dos olhos; mantém a seção 5 (texto + mensagem) e `sons(t)`.
- `render` com `video == false`: exatamente o render atual.
- Ao terminar (`t >= DUR`): `VideoFx.fechar()` se `video`.

- [ ] **Step 1: Escrever os greps em `check.py`** (antes do código, devem falhar):
  - `4.2: VideoFx existe com abrir/desenhar/fechar` → `'static boolean abrir(String id)' in vfx and 'Estado desenhar(GuiGraphics g, float t)' in vfx and 'static void fechar()' in vfx`
  - `4.2: loader em thread daemon rptag-fx-loader` → `'rptag-fx-loader' in ldr and 'setDaemon(true)' in ldr`
  - `4.2: MORTE tenta video antes do procedural` → `'VideoFx.abrir("morte")' in mafx`
  - `4.2: MORTE cai pro procedural no mesmo t (FALHOU)` → `'Estado.FALHOU' in mafx and 'video = false' in mafx`
  - `4.2: render do player protegido (catch Throwable)` → `'catch (Throwable' in vfx`
  - `4.2: fecha o video ao sair do mundo` → `'VideoFx.fechar()' in rd(C + '/ClientEvents.java')`
  - `4.2: fxVideo em cliente.properties + /rpfx` → `'cliente.properties' in cfg and 'fxVideo' in cfg and 'literal("rpfx")' in cfg`
- [ ] **Step 2:** `python check.py` → os 7 novos `FAIL` (ou só o do MORTE e do ClientEvents, se Tasks 2-3 já os satisfazem).
- [ ] **Step 3: Implementar o modo vídeo no `MorteAvisoFx` e o `fechar` no `onLoggingOut`.**
- [ ] **Step 4:** `python check.py` → `319 PASS / 0 FAIL`; `./gradlew build` → `BUILD SUCCESSFUL`. Sem assets ainda, o aviso cai no procedural (é o fallback "sem fx.json" funcionando).
- [ ] **Step 5: Commit** — `git commit -m "4.2.0: MORTE em modo video (cinza+video+texto) com fallback no mesmo t"`

---

### Task 5: Cena do Blender + prévia para aprovação

**Files:**
- Create: `arte/cenas/morte.py`
- Create: `arte/.gitignore` (`*.blend`, `*.blend1`, `render/`, `previa/`)
- Create: `arte/README.md` (como abrir o Blender com o add-on MCP e rodar cada script)

**Interfaces:**
- Consumes: os valores de `MorteTempos` (copiados como constantes no topo de `morte.py`, com comentário apontando para o arquivo Java) e de `MorteAvisoFx` (`CRANIO`, `BONE 0xE1DAC3`, `BONE_D 0xA8A08C`, `RED 0xD21E28`, `DRED 0x5A0008`, `ASH 0x9696A0`, `DARK 0x0A0000`).
- Produces: `morte.py` define `montar()` (cena do zero) e `renderizar(pasta, previa: bool)`; render em `arte/render/morte/####.png` (RGBA) e `arte/previa/morte.mp4` (FFmpeg H.264, fundo cinza escuro `0x202020` só na prévia, para ver o alfa). Marcadores na timeline: `texto_entra`, `batida` ×4, `linha_reta`, `cinzas`, `texto_sai`, nos quadros `round(seg*24)` → 12, 14, 36, 60, 89, 94, 106, 120; no fim do render, grava esses marcadores em `arte/render/morte/marcadores.json` (`[{"name": ..., "frame": ...}]`) para o `exportar.py`.

Cena (essência do spec §3):
- Câmera ortográfica 16:9, EEVEE, `film_transparent = True`, 1280×720, 24 fps, quadros 0–143, bloom/glow ligado.
- **Crânio:** a grade `CRANIO` (16 colunas) vira cubos (voxels) com material osso/osso escuro nas bordas (a mesma regra `dr` do Java), órbitas `DARK`, brasas nas linhas 6–7 colunas 4–5 e 10–11 com emissão `RED` pulsando nas `BEATS` (+ eco 0,22 s) e apagando de `FLAT` a `FLAT+0.8`. Forma-se do pó: cada voxel entra entre `0.4` e `1.3` s (escala/alfa 0→1 em 0,25 s, ordem aleatória com semente fixa); de `DUR-1.6` a `DUR-0.6` cada voxel sobe e some (cinzas).
- **ECG:** curva emissiva `RED` em `y = 80%` da altura, desenhada da esquerda até `x = largura * t/ECG_END`, com a forma de onda de `MorteAvisoFx.ecg` (picos nas `BEATS`), cabeça brilhante branca, cor indo para `BONE` de `FLAT` a `FLAT+0.6`.
- **Tela:** vinheta em túnel preta (raio de 30% → 48% entre 1,5 e 4 s, abrindo no fim), borda vermelha `0xB4000A` pulsando com as batidas, ~50 cinzas `ASH` (1 a cada 7 `DRED`) caindo o tempo todo.
- Fade de entrada 0–0,5 s e de saída `DUR-1.0`–`DUR` em tudo.
- **Não** desenhar texto nem o cinza de "esfriar" (ficam no código).

- [ ] **Step 1:** Pedir ao dono para abrir o Blender 5.2 com o add-on MCP ligado; confirmar com `mcp__Blender__get_objects_summary`.
- [ ] **Step 2:** Rodar `morte.py` via `mcp__Blender__execute_blender_code` (`montar()`), conferir com `get_screenshot_of_window_as_image` e `render_viewport_to_path` nos quadros 20, 60, 94 e 125.
- [ ] **Step 3:** `renderizar("arte/render/morte", previa=True)` → existem 144 PNG RGBA 1280×720 e `arte/previa/morte.mp4`.
- [ ] **Step 4:** Enviar ao dono (`SendUserFile`) o MP4 e 4 quadros-chave (crânio formando ~q20, batida ~q60, linha reta ~q94, cinzas ~q125). **PARAR até o dono aprovar o visual.** Ajustes pedidos → editar `morte.py`, repetir Steps 2–4.
- [ ] **Step 5: Commit** (só scripts, sem render) — `git add arte/cenas/morte.py arte/.gitignore arte/README.md && git commit -m "arte: cena do Blender do aviso MORTE (Linha Reta)"`

---

### Task 6: Exportar para o jar + validação

**Files:**
- Create: `arte/exportar.py`
- Create: `arte/validar.py`
- Create: `src/main/resources/assets/rptag/fx/morte/fx.json` + `frames/` (gerados)
- Test: `src/test/java/dev/rptag/client/MorteMarcadoresTest.java`
- Modify: `check.py` (chamar `validar.py`)

**Interfaces:**
- Consumes: PNGs de `arte/render/morte/`, marcadores (exportados por `morte.py` para `arte/render/morte/marcadores.json` = `[{"name","frame"}]` no fim do render), `FxTimeline.parse`, `MorteTempos`.
- Produces:
  - `python arte/exportar.py <id> [--blend normal|aditivo]` → lê `arte/render/<id>/*.png` e `marcadores.json`, grava `src/main/resources/assets/rptag/fx/<id>/frames/%04d_c.jpg` (RGB, qualidade 85, `optimize=True`) e `%04d_a.jpg` (canal A como `L`, qualidade 90), e `fx.json` no formato do spec. Apaga quadros antigos da pasta antes.
  - `python arte/validar.py` → percorre `assets/rptag/fx/*/`; imprime `PASS/FAIL <id>: <regra>` e sai 1 se algo falhar. Regras: `fx.json` válido (mesmas regras do `FxTimeline.parse`); `frames` = número de `_c.jpg` = número de `_a.jpg`; todo `_c` tem `_a`; todos 1280×720; `_a` em modo `L`; marcadores dentro de `[0, frames)`; tamanho total da pasta impresso em MB.
  - `check.py` ganha `check('4.2: assets de video validos (arte/validar.py)', subprocess.run([sys.executable, '-I', 'arte/validar.py']).returncode == 0)`.

- [ ] **Step 1: Teste que falha** — `MorteMarcadoresTest` lê `/assets/rptag/fx/morte/fx.json` do classpath e afirma: `frames()==144`, `fps()==24`; `marcadores("batida") == [round(b*24) for b in MorteTempos.BEATS]` (= `[14,36,60,89]`); `marcadores("linha_reta") == [round(FLAT*24)]` (= `[94]`); `marcadores("texto_entra")==[12]`, `cinzas==[106]`, `texto_sai==[120]`.
- [ ] **Step 2:** `./gradlew test` → FAIL (`fx.json` não existe).
- [ ] **Step 3: Implementar `exportar.py` e `validar.py`; rodar `python arte/exportar.py morte`.**
- [ ] **Step 4:** `python arte/validar.py` → todas `PASS`, total ≈ 15 MB; `./gradlew test` → PASS; `python check.py` → `320 PASS / 0 FAIL`; `./gradlew build` → jar em `build/libs/` ~17 MB.
- [ ] **Step 5: Commit** — `git add arte/exportar.py arte/validar.py src/main/resources/assets/rptag/fx src/test check.py && git commit -m "4.2.0: video do aviso MORTE no jar + validar.py"`

---

### Task 7: Verificação no jogo

**Files:** nenhum código novo (só capturas em `verificacao/4_2_0_*.png`).

- [ ] **Step 1:** `./gradlew runClient` (com `runServer` em outro terminal, ou mundo single-player com cheats). Rodar `/rp admin avisar @s 4 VOCE MORREU`.
- [ ] **Step 2:** Capturar a tela em ~0,8 s (crânio formando), ~4 s (linha reta) e ~5 s (cinzas) → `verificacao/4_2_0_video_{cranio,linha,cinzas}.png`. Conferir lado a lado com a prévia aprovada: vídeo presente, texto "AVISO" + mensagem embaixo, texto vermelho → osso na linha reta, sons nas batidas (o log mostra `LINHA RETA em VIDEO`).
- [ ] **Step 3:** `/rpfx off` → mesmo comando → aparece o **procedural** de antes; `/rpfx status` mostra `DESLIGADO — 1 aviso(s) com video`; `/rpfx on`.
- [ ] **Step 4:** Corromper de propósito `build/resources/main/assets/rptag/fx/morte/frames/0050_c.jpg` (truncar o arquivo), rodar o `runClient` de novo → o aviso troca para o procedural perto de 2 s **sem reiniciar**, e o log mostra `video do aviso falhou — seguindo procedural`. Restaurar com `./gradlew processResources`.
- [ ] **Step 5:** Disparar dois avisos seguidos (estilo 4 e, 1 s depois, estilo 4 de novo) → o segundo começa do início, sem piscar quadros do primeiro. Sair do mundo no meio de um aviso e entrar de novo → nenhum resto de vídeo; nenhuma thread `rptag-fx-loader` sobrando (conferir pelo log ou pelo `jstack`).
- [ ] **Step 6: Commit** — `git add verificacao/4_2_0_*.png && git commit -m "4.2.0: verificacao no jogo (video, fallback, /rpfx)"`

---

### Task 8: Versão 4.2.0, jar fora do git, documentação

**Files:**
- Modify: `src/main/java/dev/rptag/RPTagMod.java` (`VERSION = "4.2.0"`), `build.gradle` (`version = '4.2.0'`), `src/main/resources/META-INF/neoforge.mods.toml` (`version="4.2.0"`)
- Modify: `check.py` (grep de versão 4.1.0 → 4.2.0)
- Modify: `.gitignore` (trocar as linhas `rptag-*.jar` / `!rptag-4.1.0.jar` por `*.jar` + `!gradle/wrapper/gradle-wrapper.jar`)
- Delete (do git): `rptag-4.1.0.jar` (`git rm --cached`; o arquivo pode ficar no disco)
- Modify: `CHANGELOG.md` (entrada 4.2.0 no topo), `README.md` (download só pelas Releases; o jar não fica mais na raiz), `WALKTHROUGH.md` (rotina: jar só em Release; pipeline `arte/`), `ENTREGA.md` (versão atual 4.2.0 + seção "Avisos em vídeo" com `VideoFx`, `arte/`, `/rpfx`)

- [ ] **Step 1:** Atualizar os 3 lugares da versão e o grep do `check.py`; `python check.py` → tudo PASS.
- [ ] **Step 2:** `.gitignore` + `git rm --cached rptag-4.1.0.jar`; `git status` não mostra nenhum `.jar` além do wrapper.
- [ ] **Step 3:** Docs e CHANGELOG (estilo das entradas atuais: título com emoji, seções curtas, data `2026-10-19`).
- [ ] **Step 4:** `./gradlew build` → `build/libs/rptag-4.2.0.jar`; `python check.py` → `PASS` em tudo; `./gradlew test` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "4.2.0: versao + jar so nas Releases + docs (avisos em video)"`
- [ ] **Step 6:** **Não** dar push nem criar Release sem o dono pedir. Informar o caminho do jar e o tamanho.
