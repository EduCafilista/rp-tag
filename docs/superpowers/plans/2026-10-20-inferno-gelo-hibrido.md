# Inferno e Gelo híbridos — Implementation Plan

> Planejamento arquivado. Após avaliar as propostas, o responsável solicitou preservar os efeitos originais. Este documento registra o trabalho discutido e não autoriza novas alterações visuais. Consulte o código e os guias atuais para identificar o estado implementado.

>**For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (modo nativo escolhido pelo usuário) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar Inferno e Gelo com impacto central, acabamento híbrido e leitura preservada, integrados ao player de vídeo após aprovação das prévias.

**Architecture:** Cenas Blender separadas compartilham somente ferramentas de câmera, materiais, animação e saída. O compositor mostra PNG e JPEG exportado sobre gameplay; as classes Java existentes mantêm texto, sons e fallback no mesmo instante. Não ampliar a arquitetura do player para renderizar três camadas: elas já são compostas no Blender em um único vídeo por aviso.

**Tech Stack:** Blender 5.2/EEVEE, Python/Pillow, Java 21, NeoForge 21.1.249, Gradle/JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-20-inferno-gelo-hibrido-design.md`.

## Global Constraints

- 144 PNG RGBA, 1280×720, 24 fps, 6 s, fundo transparente.
- Fases: 0–1 s impacto; 1–2 s centro abrindo; 2–4,8 s leitura; 4,8–6 s saída.
- Região de leitura aproximada x=20–80% e y=32–66%, sem elementos opacos cruzando letras entre 2 e 4,8 s.
- Texto e sons permanecem no Java, com suas posições, cores e cadências atuais; não gravar letras no vídeo.
- Fundos de gameplay claros e escuros pertencem somente às prévias.
- Fallback procedural no mesmo instante, sem duplicar sons; modo reduzido usa procedural.
- Não alterar a 4.2.0 durante as prévias. Entrega integrada será 4.3.0.
- Preservar alterações locais existentes; commits com arquivos explícitos e sem trailers de IA.
- Sem push, criação de Release ou exclusão de tags nesta execução.

## Review Focus

1. JPEG com alfa cria contorno escuro: comparar composição do PNG e do par JPEG em fundo claro/escuro (T5).
2. Crop em 16:10/21:9 corta o acontecimento ou invade o texto: quadros recortados e centralizados seguindo `FxTimeline.cobrir` (T4).
3. Partícula retorna à tela por módulo e salta: inspeção animada do início/fim de trajetórias, sem teletransporte visível (T4).
4. Aviso novo deixa vídeo ou som antigo ativo: teste no jogo Inferno→Gelo→Morte e Inferno→aviso procedural (T6).
5. `reduzido=true` ainda mostra clarões do vídeo: teste de seleção puro e confirmação no jogo (T6).

## Mapa de arquivos

- Criar `arte/cenas/comum.py`: câmera, animação, materiais básicos, render e metadados, extraídos das primitivas efetivamente usadas do piloto.
- Modificar `arte/cenas/inferno.py` e `arte/cenas/gelo.py`: cenas independentes com `montar() -> dict` e `main() -> None`.
- Retirar `arte/cenas/elementais.py` do caminho ativo; ele é uma prévia local não commitada. Preservá-lo como `arte/referencias/elementais_v1.py` para comparação, sem usá-lo na montagem nova.
- Modificar `arte/previa.py`: composição com gameplay e suporte aos pares JPEG exportados.
- Criar `arte/tests/test_previa.py`: testes do compositor e do enquadramento; usar unittest.
- Criar `arte/inspecionar.py`: integridade de quadros/metadados/ausência de texto e relatório de amostras.
- Assets gerados: `arte/render/{inferno,gelo}` e `arte/previa/{inferno,gelo}` (ignorados); exportados: `src/main/resources/assets/rptag/fx/{inferno,gelo}`.
- Java: `InfernoAvisoFx.java`, `GeloAvisoFx.java`, `ClientEffects.java`; classe pura `FxVideoPolitica.java` para decisão do modo e teste JUnit correspondente.
- Entrega: versão, check.py e documentação em T7.

### Task 1: Preparar compositor e referência de gameplay

**Files:** `arte/previa.py`, `arte/tests/test_previa.py`, `arte/README.md`, `arte/.gitignore`, `arte/referencias/elementais_v1.py`.

**Interfaces:** preservar `compor(png, meta, quadro, mensagem="VOCE MORREU")`; adicionar argumentos opcionais `fundo=None` e `alfa=None`, ambos caminhos de arquivo. `alfa` indica que `png` é a imagem de cor JPEG. CLI: manter comandos atuais e adicionar `--fundo CAMINHO`, `--exportado`, `--mensagem TEXTO`; a última opção sobrepõe a mensagem de exemplo do metadata.

- [ ] Preservar os scripts e renders v1 antes de sobrescrever; copiar apenas caminhos verificados dentro de `arte/`, sem limpeza recursiva genérica.
- [ ] Criar teste `test_compor_preserva_gameplay_com_alfa_zero`: imagem RGBA transparente deve deixar o fundo visível em um pixel longe do texto. `test_jpeg_cor_e_alfa_compostos`: alfa preto preserva fundo, branco mostra cor. `test_mensagem_argumento_sobrepoe_metadata`: argumento explícito ganha do texto de exemplo. Executar `python -m unittest discover -s arte/tests -v` e observar falha antes da implementação.
- [ ] Implementar as opções mantendo compatibilidade com `python arte/previa.py morte --quadros 20,60,94,125`. Fechar imagens com context managers. Não trocar o fundo cinza padrão dos comandos existentes.
- [ ] Capturar duas cenas de gameplay próprias, sem avisos, chat ou dados privados: dia/exterior e ambiente escuro. Usar cliente de teste já configurado; guardar em `arte/referencias/gameplay_claro.png` e `gameplay_escuro.png`. Se cliente não iniciar, registrar bloqueio dessa evidência, sem apresentar fundo sintético como gameplay.
- [ ] Executar testes e comando legado MORTE. Conferir visualmente as duas referências. Commit explícito do compositor, testes, referências e documentação.

### Task 2: Ferramentas compartilhadas e Inferno — quatro quadros

**Files:** `arte/cenas/comum.py`, `arte/cenas/inferno.py`, `arte/inspecionar.py`.

**Interfaces:** `comum.configurar() -> Scene`; `comum.assar(obj, caminho: str, valores: list, indice: int=0) -> None`; `comum.renderizar(id: str, meta: dict, quadros: list[int] | None) -> None`. `inferno.montar() -> dict` monta cena e retorna metadados. Entrypoint suporta `--quadros 12,36,72,127`, `--salvar` e render completo sem flags. `meta` contém `id`, `markers`, `fade`, `preview` e `seed`; nomes/frames dos sons seguem o Java atual.

- [ ] Extrair utilitários mínimos sem modificar o visual ou as constantes do MORTE; as cenas novas não importam `morte.py`. Câmera ortográfica 16:9 e seed determinística por aviso.
- [ ] Montar fissura angular, interior incandescente, clarão curto e onda luminosa. Fragmentos saem do centro antes de 2 s. Fogo em camadas com silhueta curva e animações de fase independente; fumaça com bordas suaves e luz quente.
- [ ] Montar rastros de brasa intermitentes e meteoros com caudas dissipando; manter centro protegido no período de leitura. Partículas entram/saem fora do quadro ou perdem alfa antes de reiniciar a trajetória.
- [ ] Implementar `inspecionar.validar(id: str, quadros: list[int]) -> list[str]`: retorna erros de dimensão, modo, arquivo ausente, marcador fora de 0..143 ou texto Blender presente no relatório. CLI retorna código 1 para erro.
- [ ] Renderizar quadros 12,36,72,127; compor sobre os dois fundos. Inspecionar fissura, fragmentos, cores, alfa e posições, não apenas contagem de objetos. Documentar inventário e tempos no JSON de inspeção.
- [ ] Commit da cena e ferramentas; sem exportação para o jar.

### Task 3: Gelo — quatro quadros

**Files:** `arte/cenas/gelo.py`; consome ferramentas e metadata de T2.

**Interfaces:** `gelo.montar() -> dict`; mesmo CLI de Inferno. Marcadores do impacto em 0,12 s e sinos em 0,5/1,2/2/3 s, correspondentes ao Java. Fade de entrada 0–0,4 s e saída 4,8–6 s; Inferno mantém entrada 0–0,35 s.

- [ ] Montar impacto e rede de trincas irregulares; retirar trincas centrais até 2 s.
- [ ] Criar geada com ramificações em escala variada e crescimento por caminhos; cristais facetados translúcidos em posições e inclinações diferentes. Não exigir refração do gameplay, que o vídeo não pode conhecer.
- [ ] Montar floco discreto, neve em profundidade, névoa leve e soltura de fragmentos na saída. Garantir trajetórias sem saltos no campo visível.
- [ ] Renderizar e compor os mesmos quatro instantes sobre os dois fundos; executar inspeção. Conferir volume real, luz, legibilidade e variação.
- [ ] Commit explícito da cena. Apresentar os quatro momentos de AMBOS os avisos ao usuário e aguardar aprovação antes de T4. Ajustes de arte voltam a T2/T3.

### Task 4: Sequências completas e aprovação do movimento

**Files:** cenas de T2/T3, `arte/inspecionar.py`, testes de enquadramento no compositor.

**Interfaces:** inspeção usa 144 quadros; prévias animadas 640×360/12 fps, identificadas como redução da saída original. `previa.enquadrar(imagem, largura: int, altura: int) -> Image` usa cover centralizado, em conformidade com `FxTimeline.cobrir`.

- [ ] Criar teste de enquadramento com imagem de referência contendo centro marcado: saídas 16:10 e 21:9 preservam proporção e centro sem deformar. Ver falhar e implementar crop cover centralizado.
- [ ] Após aprovação dos quadros, renderizar 0000..0143 dos dois avisos; verificar todos os PNG com leitura completa e metadados.
- [ ] Gerar duas prévias animadas e amostras em 16:10 e 21:9. Inspecionar movimento nas transições, trajetória das partículas e clareza do texto de exemplo longo.
- [ ] Apresentar prévias completas e aguardar aprovação antes de exportar assets. Não substituir aprovação de movimento pela aprovação dos quadros estáticos.

### Task 5: Exportação e comparação PNG/JPEG

**Files:** assets `fx/inferno/`, `fx/gelo/`; testes do compositor em `arte/tests/test_previa.py`; utilizar `arte/exportar.py` e `arte/validar.py` existentes.

**Interfaces:** pares `frames/NNNN_c.jpg`, `NNNN_a.jpg`, `fx.json`; normal blend. Não alterar a resolução nem introduzir codec/dependência de vídeo.

- [ ] Exportar com `python arte/exportar.py inferno` e `python arte/exportar.py gelo`; usar `arte/validar.py` e inspeção completa de leitura dos JPEG. Registrar tamanho por aviso e do jar.
- [ ] Testar `test_alfa_jpeg_sobre_fundo_claro`: composição por par JPEG mantém fundo nos pixels transparentes, com tolerância de compressão de 8 níveis por canal nas áreas uniformes. Usar fixture pequena gerada no teste, sem render Blender.
- [ ] Compor quadros 12,36,72,127 a partir do JPEG exportado sobre fundos claro/escuro e comparar com PNG aprovado. Se houver halos perceptíveis, corrigir exportação ou material e repetir a avaliação dessas amostras.
- [ ] Commit explícito dos assets aprovados e testes. Não afirmar qualidade do alfa usando somente dimensões/cabeçalhos.

### Task 6: Integração dos avisos e teste no jogo

**Files:** `src/main/java/dev/rptag/client/FxVideoPolitica.java`, `InfernoAvisoFx.java`, `GeloAvisoFx.java`, `ClientEffects.java`; `src/test/java/dev/rptag/client/FxVideoPoliticaTest.java`, `ElementaisMarcadoresTest.java`; evidências `verificacao/4_3_0_*`.

**Interfaces:** `FxVideoPolitica.permitir(boolean habilitado, boolean reduzido) -> boolean` (Java puro). Consumir `VideoFx.abrir(id)`, `desenhar(g,t)`, `fechar()` e `VideoEstado` existentes, sem duplicar loader. As classes do aviso continuam responsáveis pelo relógio, sons e texto.

- [ ] Testes da política: true/false→true; true/true→false; false/false→false; false/true→false. Observar RED, implementar e executar GREEN. Teste dos fx.json confirma fps=24, frames=144, blend normal e marcadores correspondentes aos tempos Java.
- [ ] No início de cada aviso, encerrar vídeo anterior; tentar vídeo somente quando config permite e reduzido=false. Na central `ClientEffects`, fechar vídeo anterior também quando o aviso novo for procedural. Conferir todos os ramos de dispatch.
- [ ] Compor vídeo antes do texto, pular apenas o fundo procedural enquanto vídeo funciona. Preservar efeitos ligados às letras no código e leitura de mensagem abaixo do título. Sons chamados uma única vez por frame, após estado de carga resolvido; fallback não reinicia relógio nem índices de som.
- [ ] `CARREGANDO` segue política existente; `FALHOU` retorna procedural no mesmo t; fim libera vídeo. Não capturar falhas do vídeo recomeçando todo o aviso.
- [ ] Executar `./gradlew test` e `python check.py`. Subir servidor local e cliente de teste, disparar cada aviso; comparar com prévias. Testar config off, reduzido=true em ambiente de desenvolvimento, JPEG corrompido apenas em cópia de build, Inferno→Gelo→Morte, Inferno→Clássico, repetição rápida e saída do mundo. Restaurar arquivo corrompido e verificar ausência de loader após saída.
- [ ] Build do jar e smoke usando o jar em instalação de teste, separada dos arquivos de classes. Capturar evidência; se runtime do jar estiver bloqueado, registrar precisamente o teste pendente e não declarar conclusão dessa parte.
- [ ] Commit da integração e evidências; sem push.

### Task 7: Versão 4.3.0, documentação e revisão final

**Files:** `build.gradle`, `RPTagMod.java`, `neoforge.mods.toml`, log em `ClientEffects.java`, verificações de versão em `check.py`, `CHANGELOG.md`, `WALKTHROUGH.md`, `ENTREGA.md`, `README.md`, `arte/README.md`.

- [ ] Atualizar versão 4.3.0 nos três lugares e log; manter verificações de versão coerentes sem reescrever checks históricos de comportamento.
- [ ] Documentar novos avisos, pipeline, limites do alfa, opção reduzida e evidências reais. Corrigir os números de testes apresentados na documentação para o resultado final, sem hardcode de contagens antigas.
- [ ] Executar `./gradlew clean build`, `python check.py` e `python -m unittest discover -s arte/tests -v`; confirmar jar com MORTE/Inferno/Gelo e 288 JPEG por aviso. Verificar `git diff --check` e nenhum jar incluído no commit.
- [ ] Fazer revisão independente do diff final, incluindo cenas e os cinco pontos de Review Focus; corrigir problemas relevantes e repetir só os testes afetados.
- [ ] Commit final sem trailers de IA. Entregar jar, prévias, evidências e limitações efetivamente observadas. Publicação e push ficam fora do escopo.

## Self-review do plano

Cobertura: sequência visual T2/T3; composição e fundos T1/T4; fases aprovadas T3/T4;
compressão/alfa T5; fallback/config/reduzido/sons T6; entrega e docs T7.
Assinaturas das cenas, compositor, inspeção e política Java estão definidas acima.
As cinco condições do Review Focus têm verificação na tarefa responsável.
Os testes automatizados cobrem cálculo e composição; a qualidade visual é avaliada
por amostras e aprovação humana. Método de execução preservado: nativo, com revisão
independente final. Nenhuma implementação do redesign é iniciada por este documento.
