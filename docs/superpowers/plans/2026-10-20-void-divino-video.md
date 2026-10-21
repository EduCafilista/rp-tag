# Void e Divino em vídeo — Implementation Plan

>**For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Void e Divino passam a ter fundo em vídeo renderizado no Blender, com a coreografia atual em 3D, integrados ao player existente com fallback procedural.

**Architecture:** Duas cenas novas (`void.py` em perspectiva, `divino.py` em ortográfica) sobre `comum.py`. A matemática da câmera em perspectiva e os metadados ficam em módulos Python puros, testáveis sem Blender. No Java, `VoidAvisoFx` e `DivinoAvisoFx` copiam o padrão de `GeloAvisoFx`: o vídeo é o fundo e texto/sons continuam no código. O player (`VideoFx`) não muda.

**Tech Stack:** Blender 5.2/EEVEE, Python 3 + Pillow (unittest), Java 21, NeoForge 21.1.249, Gradle/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-20-void-divino-video-design.md`

## Global Constraints

- 144 PNG RGBA, 1280×720, 24 fps, 6 s, fundo transparente; um vídeo por aviso.
- Void: título em cy = 0,45 h (mundo y = +0,45); Divino: cy = 0,42 h (mundo y = +0,72). Mensagem em cy + 25/240 h.
- Zona do texto de 1,0 a 4,8 s: Void y 40–58 %, Divino y 37–55 %, x 25–75 %; nada opaco/claro cruza as letras.
- Void: imersão quase total, alfa ~0,9 na fase principal. Divino: jogo visível; vinheta só nas bordas, nunca véu na tela inteira.
- Divino: brilho atrás das letras ≤ 35 % do brilho dos anéis do halo.
- Tamanho: meta ≤ 15 MB por pasta `fx/<id>`; JPEG cor 85 / alfa 90; alfa nunca abaixo de 90.
- Texto, sons e partículas do servidor ficam no Java, com os tempos atuais; nenhum objeto de texto na cena.
- Modo reduzido e `fxVideo=false` usam o procedural (`FxVideoPolitica`).
- MORTE, Inferno e Gelo não mudam: câmera ortográfica e `metadata('inferno'|'gelo')` com saída idêntica.
- Versão de entrega: 4.6.0. Commits com arquivos explícitos, sem trailer de IA. Sem push nem Release.
- Gates do dono: (1) 4 quadros de cada aviso, (2) GIF de movimento, só então exportar.

## Review Focus

1. A mensagem do Void passa por cima do horizonte de eventos e do disco de acreção (y ≈ 0,55 h): ela deve continuar legível, com o disco mais fraco na frente dela (T2 mede, T6 confere no jogo).
2. Divino sobre céu claro: o halo dourado não pode "lavar" o título creme (T3 mede a luminância, T6 confere no jogo).
3. Anéis do túnel do Void atravessando a câmera: não pode haver estouro nem anel gigante piscando ao cruzar o plano de recorte; cada anel some antes (T4 confere os quadros vizinhos).
4. Grande área escura quase uniforme no alfa do Void: depois do JPEG, sem degraus/blocos visíveis nem halo (T5 compara PNG e par JPEG).
5. Aviso novo no meio (Void→Divino, Divino→Clássico, repetição rápida, `/lorezone aviso`) não pode deixar vídeo ou som preso (T6 testa os dois caminhos de disparo).

## Mapa de arquivos

- Criar `arte/cenas/perspectiva.py` (Python puro): parâmetros e projeção da câmera em perspectiva.
- Criar `arte/cenas/metas.py` (Python puro): metadados de cada aviso; `comum.metadata` passa a delegar para ele.
- Modificar `arte/cenas/comum.py`: `camera_perspectiva(...)` e `metadata` delegando.
- Criar `arte/cenas/void.py` e `arte/cenas/divino.py`.
- Modificar `arte/inspecionar.py`: medida de luminância por caixa.
- Testes Python: `arte/tests/test_perspectiva.py`, `arte/tests/test_metas.py`, `arte/tests/test_inspecionar.py`.
- Assets: `src/main/resources/assets/rptag/fx/{void,divino}/`.
- Java: `VoidAvisoFx.java`, `DivinoAvisoFx.java`, `ClientEffects.java`, `ClientEvents.java`; teste `src/test/java/dev/rptag/client/CelestiaisMarcadoresTest.java`.
- `check.py`, versão e documentação em T7.

### Task 1: Branch, câmera em perspectiva e metadados

**Files:**
- Create: `arte/cenas/perspectiva.py`, `arte/cenas/metas.py`, `arte/tests/test_perspectiva.py`, `arte/tests/test_metas.py`
- Modify: `arte/cenas/comum.py` (`metadata`, nova `camera_perspectiva`)

**Interfaces:**
- Produces: `perspectiva.parametros(distancia: float, centro_y: float, largura: float = 16.0, altura: float = 9.0) -> dict` com chaves `location` (tupla x,y,z), `sensor_width` (36.0), `lens` (mm), `shift_x` (0.0), `shift_y`.
- Produces: `perspectiva.projetar(p: dict, x: float, y: float, z: float) -> tuple[float, float]` → (u, v) normalizado, u da esquerda, v **de cima** (mesma convenção do Java: v = cy/h).
- Produces: `metas.metadata(fx: str, seed: int) -> dict` para `inferno`, `gelo`, `void`, `divino`, com as chaves `id`, `seed`, `markers`, `fade`, `preview`; `ValueError` para id desconhecido.
- Produces: `comum.camera_perspectiva(distancia: float = 20.0, centro_y: float = 0.0) -> bpy.types.Object`, que troca a câmera da cena e mantém o plano z = 0 cobrindo x −8..8 e y −4,5..4,5.

- [ ] **Step 1: Criar a branch** `void-divino-video` a partir do `main` local (que já tem a 4.5.0). Conferir com `git log --oneline -1` que a base contém `ceb129d`.
- [ ] **Step 2: Testes da câmera** (`test_perspectiva.py`, unittest, sem bpy):
  - `test_centro_do_titulo_void`: `p = parametros(20, 0.45)`; `projetar(p, 0, 0.45, 0) == (0.5, 0.45)` (tolerância 1e-6). O ponto de fuga fica no título.
  - `test_plano_z0_cobre_16x9`: `projetar(p, -8, 4.5, 0) ≈ (0, 0)` e `projetar(p, 8, -4.5, 0) ≈ (1, 1)`.
  - `test_profundidade_converge`: para `z = -40`, `projetar(p, 8, 0.45, -40)` tem u entre 0,5 e 1 e v ≈ 0,45; quanto mais negativo o z, mais perto de (0,5; 0,45).
- [ ] **Step 3: Testes dos metadados** (`test_metas.py`):
  - `test_inferno_e_gelo_identicos`: `metas.metadata('inferno', 482)` e `('gelo', 618)` iguais ao dicionário que o `comum.metadata` gera hoje. Copiar a saída atual dos `marcadores.json` de `arte/render/` ou do `fx.json` exportado como valor esperado.
  - `test_void`: markers `impacto` [0], `ressoar` [36, 65, 94] (1,5/2,7/3,9 s = `VoidAvisoFx.RES`), `fim` [122] (5,1 s); fade in [0, 0,5], out [4,8, 6]; preview `title_y` 0,45, `message_y` 0,45 + 25/240, cor [180, 160, 255] (0xB4A0FF), mensagem "A REALIDADE SE DESFEZ".
  - `test_divino`: markers `sino` [1, 7, 13, 19, 26] (`DivinoAvisoFx.CT` × 24, arredondado), `fim` [122]; fade in [0, 0,5], out [4,8, 6]; preview `title_y` 0,42, `message_y` 0,42 + 25/240, cor [255, 240, 190] (CREAM), mensagem "OS CEUS SE ABRIRAM".
  - `test_id_desconhecido`: `ValueError`.
- [ ] **Step 4: Rodar** `python -m unittest discover -s arte/tests -v`. Esperado: FAIL (módulos não existem).
- [ ] **Step 5: Implementar** `perspectiva.py` com câmera em (0, centro_y, distancia) olhando para −z, sensor horizontal de 36 mm, `lens = 36 * distancia / largura` e `shift_y = -centro_y / largura` (o shift do Blender é fração da maior dimensão do sensor). Implementar `metas.py` movendo a lógica atual de `comum.metadata`. Em `comum.py`: `metadata = metas.metadata` e `camera_perspectiva` aplicando `parametros` à câmera existente (`type='PERSP'`, `clip_start=0.1`, `clip_end=200`).
- [ ] **Step 6: Rodar** os testes de `arte/tests`. Esperado: todos PASS, incluindo os antigos do compositor.
- [ ] **Step 7: Regressão das cenas antigas:** `blender -b --factory-startup -P arte/cenas/inferno.py -- --quadros 72` e o mesmo para `gelo.py`. O `marcadores.json` gerado deve ser idêntico ao anterior (`git diff --no-index` contra uma cópia salva antes).
- [ ] **Step 8: Commit** dos 6 arquivos: `"4.6.0: camera em perspectiva e metadados puros (Void/Divino)"`.

### Task 2: Cena do Void — quatro quadros

**Files:**
- Create: `arte/cenas/void.py`
- Modify: `arte/inspecionar.py`; Test: `arte/tests/test_inspecionar.py`

**Interfaces:**
- Consumes: `comum.configurar`, `comum.camera_perspectiva(20.0, 0.45)`, `comum.metadata('void', 615)`, `comum.executar('void', montar)`.
- Produces: `void.montar() -> dict`; CLI igual à do Inferno (`--quadros`, `--salvar`).
- Produces: `inspecionar.luminancia(caminho_png: str, caixa: tuple[float, float, float, float]) -> float`, a média de `max(R,G,B)/255 × A/255` na caixa (x0, y0, x1, y1 em fração 0–1, y de cima).

- [ ] **Step 1: Teste** `test_luminancia_caixa`: imagem 100×100 transparente com quadrado branco opaco em x 0–50 → caixa (0, 0, 0,5, 1) dá 1,0; caixa (0,5, 0, 1, 1) dá 0,0; branco com alfa 128 dá ≈ 0,502. Ver falhar, implementar, ver passar.
- [ ] **Step 2: Montar o Void** conforme a spec (seção "Void — Queda Sem Fundo"), em unidades do mundo com k = 9/240 por unidade do procedural (Rh = 24k ≈ 0,9 de raio):
  - fundo do End em plano grande atrás de tudo (z ≈ −60, escalado para cobrir o quadro), alfa ~0,9 de 0,5 a 4,8 s, nebulosa de baixa frequência;
  - onda de choque lavanda (0–0,5 s);
  - buraco (disco preto + borda suave) e os três anéis do horizonte em z = 0;
  - disco de acreção como toro achatado inclinado (~70° em X, −0,3 rad em Z, balanço de 0,05 rad), mais claro no lado próximo e com a parte em frente à mensagem (y −0,1 a −0,7) até ~40 % do brilho das laterais;
  - 16 anéis do túnel em z de −60 a −1, vindo na direção da câmera, com ondulação própria e fade antes de z = −1;
  - 90 estrelas-risco sugadas para (0; 0,45);
  - 12 estilhaços escuros com contorno violeta, fora da zona do texto de 1 a 4,8 s.
  Sementes vindas de `random.Random(615)`, sem movimento sincronizado.
- [ ] **Step 3: Renderizar** `-- --quadros 7,29,72,127`. Rodar `python arte/inspecionar.py void --quadros 7,29,72,127`. Esperado: código 0, `text_in_scene: false`.
- [ ] **Step 4: Compor** `python arte/previa.py void --quadros 7,29,72,127 --fundo arte/referencias/gameplay_claro.png` e o mesmo com `gameplay_escuro.png`. Abrir as 8 imagens e conferir profundidade, buraco atrás do título e mensagem legível no quadro 72. Medir `luminancia` em `void/0072.png`: caixa da mensagem (0,25, 0,53, 0,75, 0,58) ≤ 0,5 × caixa lateral do disco (0,08, 0,40, 0,22, 0,50). Ajustar o disco até passar.
- [ ] **Step 5: Commit** de `void.py`, `inspecionar.py` e do teste: `"4.6.0: cena do Void (buraco negro, tunel em perspectiva) — quatro quadros"`.

### Task 3: Cena do Divino — quatro quadros + gate 1

**Files:**
- Create: `arte/cenas/divino.py`

**Interfaces:**
- Consumes: `comum.configurar` (ortográfica), `comum.metadata('divino', 733)`, `comum.executar`, `inspecionar.luminancia`.
- Produces: `divino.montar() -> dict`.

- [ ] **Step 1: Montar o Divino** conforme a spec (seção "Divino — Ascensão Celestial"), centro em mundo (0; +0,72), r1 = 62k ≈ 2,33:
  - clarão (0–0,3 s);
  - vinheta quente escura só nas bordas (DARK 0x0A0600, até ~0,55 de alfa nos cantos);
  - pilar com núcleo branco e bordas douradas descendo até o centro em 0–0,6 s, com poeira;
  - halo (disco suave, 3 anéis, 48 tiques, hexagrama duplo em sentidos opostos, 4 losangos);
  - duas coroas de raios (16 + 12) girando em sentidos contrários;
  - 2 × 9 penas com nervura e atraso por pena;
  - ornamento (linha + losango) em 0,8–1,4 s em mundo y ≈ 0,72 − 5,5·S − 6k;
  - 50 faíscas em estrela subindo, em três profundidades, com fade nas pontas.
  O miolo do disco do halo é atenuado dentro de raio 0,78·r1.
- [ ] **Step 2: Renderizar** `-- --quadros 5,24,72,127` e rodar `inspecionar.py divino` (código 0).
- [ ] **Step 3: Compor** sobre os dois fundos. Medir em `divino/0072.png`: caixa do título (0,40, 0,37, 0,60, 0,46) ≤ 0,35 × caixa do anel (0,47, 0,15, 0,53, 0,19). Conferir o título claro sobre `gameplay_claro` com a prévia (que já desenha contorno).
- [ ] **Step 4: Commit** de `divino.py`: `"4.6.0: cena do Divino (pilar, halo, raios, asas) — quatro quadros"`.
- [ ] **Step 5: Gate 1.** Mostrar ao dono as 16 composições (4 quadros × 2 fundos × 2 avisos). **Parar e esperar aprovação.** Ajustes voltam a T2/T3.

### Task 4: Sequências completas + gate 2 (movimento)

**Files:** cenas de T2/T3 (só ajustes); sem arquivos novos.

- [ ] **Step 1:** Render completo dos dois (sem `--quadros`). `inspecionar.py void` e `divino` sem `--quadros`: 144 quadros, código 0.
- [ ] **Step 2:** `python arte/previa.py void --gif --fundo arte/referencias/gameplay_escuro.png` e Divino com `gameplay_claro.png`. Gerar também os quadros 29 e 72 recortados em 16:10 e 21:9 com `previa.enquadrar`.
- [ ] **Step 3:** Conferir os quadros vizinhos de cada anel do túnel ao cruzar a câmera (Review Focus 3), as entradas e saídas de estrelas, faíscas e penas sem salto, o fim suave até o quadro 143, e as asas dentro do corte 16:10.
- [ ] **Step 4: Gate 2.** Mostrar os GIFs e os cortes ao dono. **Parar e esperar aprovação do movimento.**

### Task 5: Exportação, alfa JPEG e tamanho

**Files:** `src/main/resources/assets/rptag/fx/{void,divino}/`; `check.py` (novas linhas de tamanho).

- [ ] **Step 1:** `python arte/exportar.py void` e `python arte/exportar.py divino`; depois `python -I arte/validar.py` (código 0).
- [ ] **Step 2:** Medir `du -sh` das duas pastas. Se uma passar de 15 MB, seguir a ordem da spec (grão/densidade, depois qualidade do JPEG de cor) e reexportar.
- [ ] **Step 3:** Adicionar em `check.py`, no bloco 4.6: para `void` e `divino`, tamanho total da pasta `fx/<id>` ≤ 16 × 1024² bytes (meta de 15 MB com margem) e `fx.json` presente.
- [ ] **Step 4:** Compor os quadros 29 e 72 do par JPEG (`--exportado`) sobre os dois fundos e comparar com o PNG: sem degrau/bloco na área escura do Void, sem halo nas penas do Divino (Review Focus 4).
- [ ] **Step 5: Commit** dos assets e do `check.py`: `"4.6.0: videos do Void e do Divino exportados (pares JPEG cor+alfa)"`.

### Task 6: Integração Java e teste no jogo

**Files:**
- Modify: `VoidAvisoFx.java`, `DivinoAvisoFx.java`, `ClientEffects.java:86-88`, `ClientEvents.java:71-73`, `check.py`
- Create: `src/test/java/dev/rptag/client/CelestiaisMarcadoresTest.java`

**Interfaces:**
- Consumes: `FxVideoPolitica.permitir(boolean, boolean)`, `VideoFx.abrir(String) -> boolean`, `VideoFx.desenhar(GuiGraphics, float) -> VideoEstado`, `VideoFx.fechar()`, `AvisoFxUtil.contornoEscuro(...)`, `FxComando.garantirConfig()`, `FxConfig.video()`.
- Produces: `public static void parar()` em `VoidAvisoFx` e `DivinoAvisoFx`.

- [ ] **Step 1: Teste JUnit** `CelestiaisMarcadoresTest` (molde de `ElementaisMarcadoresTest`):
  - `voidSeisSegundosComRessoarDoJava`: fps 24, 144 quadros, `aditivo()` false, `impacto` [0], `ressoar` [q(1.5), q(2.7), q(3.9)], `fim` [q(5.1)].
  - `divinoSeisSegundosComSinosDoJava`: idem, `sino` [q(0.05), q(0.30), q(0.55), q(0.80), q(1.10)], `fim` [q(5.1)].
- [ ] **Step 2: check.py:** repetir o laço do bloco 4.5 para `('void','VoidAvisoFx')` e `('divino','DivinoAvisoFx')`, com as mesmas cinco condições (política + abrir, FALHOU, sons depois de CARREGANDO, `parar()`, `CONTORNO_VIDEO`), e incluir as duas classes nas checagens de `ClientEffects` e `ClientEvents`. Rodar `./gradlew test` e `python check.py`. Esperado: o JUnit novo passa (assets de T5); as checagens novas do `check.py` FALHAM.
- [ ] **Step 3: Implementar nas duas classes** o padrão de `GeloAvisoFx`:
  - campo `video` e `parar()`;
  - `iniciar` chama `FxComando.garantirConfig()` e `video = FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("void"|"divino")`, com log "em VIDEO|procedural";
  - em `render`, desenhar o vídeo antes de tudo e tratar CARREGANDO (relógio parado, sem sons) e FALHOU (procedural no mesmo t);
  - fechar o vídeo em t ≥ DUR;
  - mover `sons(t)` para depois do estado de carga;
  - com vídeo, pular as camadas procedurais de fundo e brilho, mas manter as letras e seus efeitos (Void: órbita, glow, aberração, reflexo; Divino: queda das letras e brilho dourado);
  - `CONTORNO_VIDEO` = 0x06000F no Void e 0x0A0600 no Divino, com `if (video) contornoEscuro(...)` na mensagem;
  - apoio de leitura radial escuro atrás do título no modo vídeo (alfa 0,35 no Void, 0,45 no Divino, raio 85k), no molde do `brilho` do Gelo.
- [ ] **Step 4:** Adicionar `VoidAvisoFx.parar()` e `DivinoAvisoFx.parar()` em `ClientEffects.startScreenText` (antes do dispatch) e na saída do mundo em `ClientEvents`.
- [ ] **Step 5:** Rodar `./gradlew test` e `python check.py`. Esperado: tudo PASS.
- [ ] **Step 6: No jogo:** `./gradlew runServer`, `./gradlew runClient -PrptagTeste`, `python tools/rcon.py "rp admin avisar Dev void TESTE"` e o mesmo com `divino`. Conferir:
  - cada vídeo até o fim, com a mensagem legível (Review Focus 1 e 2, Divino de dia);
  - `/rpfx off` e o modo reduzido → procedural;
  - JPEG corrompido numa cópia em `build/` → procedural no mesmo t;
  - Void→Divino, Divino→clássico e repetição rápida;
  - disparo por `/lorezone aviso <id> void` (Review Focus 5);
  - sair do mundo com o vídeo tocando.
  Capturas em `verificacao/4_6_0_*` (o Minecraft precisa estar na frente). Restaurar o JPEG.
- [ ] **Step 7: Commit** de Java, teste, `check.py` e capturas: `"4.6.0: Void e Divino em modo video + verificacao no jogo"`.

### Task 7: Versão 4.6.0, documentação e revisão final

**Files:** `build.gradle`, `RPTagMod.java`, `neoforge.mods.toml`, log em `ClientEffects.java`, checagens de versão em `check.py`, `CHANGELOG.md`, `WALKTHROUGH.md`, `ENTREGA.md`, `README.md`, `arte/README.md`.

- [ ] **Step 1:** Versão 4.6.0 nos três lugares, no log e nas checagens de versão.
- [ ] **Step 2:** Documentar no `WALKTHROUGH.md` (seção 4.6.0 no estilo da 4.5.0, com os achados reais) e no `arte/README.md` (comandos do Void/Divino, câmera em perspectiva e tamanhos medidos). Atualizar as contagens de testes com o resultado final.
- [ ] **Step 3:** Rodar `./gradlew clean build`, `python check.py` e `python -m unittest discover -s arte/tests -v`. Confirmar o jar com 5 vídeos (288 JPEG cada) e anotar o tamanho. Conferir `git diff --check` e que nenhum jar vai no commit.
- [ ] **Step 4:** Revisão independente do diff da branch, com os cinco pontos do Review Focus. Corrigir e repetir só os testes afetados.
- [ ] **Step 5: Commit final** sem trailer de IA. Entregar jar, prévias, evidências e limitações observadas. Push e Release ficam com o dono.

## Self-review do plano

Cobertura: sequência do Void em T2, do Divino em T3; composição/legibilidade com
medidas em T2/T3 e no jogo em T6; cortes 16:10/21:9 em T4; tamanho e alfa JPEG em T5;
política, fallback, `parar()`, contorno e sons em T6; câmera em perspectiva sem
mexer nas cenas antigas em T1; versão/docs em T7. Os gates 1 e 2 estão em T3 e T4.
Nomes conferidos entre tarefas: `parametros`/`projetar`, `metas.metadata`,
`camera_perspectiva`, `luminancia` e `parar()`. Cada item do Review Focus tem
verificação na tarefa dona.
