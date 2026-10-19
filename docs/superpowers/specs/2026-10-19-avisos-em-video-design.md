# Avisos em vídeo (Blender) — design

- **Data:** 2026-10-19
- **Projeto:** RP Tag (NeoForge 1.21.1), a partir da 4.1.0
- **Escopo deste ciclo:** piloto com o aviso MORTE "Linha Reta" (estilo 4) → versão **4.2.0**
- **Escopo total:** os 12 avisos de tela cheia, em ciclos seguintes (4.3.0, 4.4.0…)
- **Fora do escopo:** coração canônico 3D, balões de fala, molduras

## 1. Objetivo

Os avisos de tela cheia hoje são desenhados 100% em código (triângulos
coloridos, sem textura). O dono quer **cara de arte** — iluminação, textura,
profundidade — **mantendo a essência** de cada aviso (a mesma "história",
o mesmo ritmo, os mesmos sons, o mesmo padrão de texto).

Decisões do dono:

| Pergunta | Decisão |
|---|---|
| O que incomoda | Visual "vetor de código"/chapado; quer arte |
| Quanto o jar pode crescer | Sem limite — cada aviso vira um **vídeo** inteiro |
| Como chega no jogador | **Dentro do jar** |
| O que entra | Só os 12 avisos, começando por um **piloto (MORTE)** |
| Formato | **Pares JPEG cor + alfa**, player próprio, sem dependência externa |

### Critérios de sucesso do piloto

1. `/rp admin avisar @a 4 <msg>` mostra o "Linha Reta" renderizado no
   Blender, a 24 fps, com a mensagem por cima, sincronizado com os sons.
2. O ritual da vida canônica (morte RP) usa o mesmo vídeo, com véu
   avermelhado e `canon_death` como hoje.
3. Sem vídeo, com erro ou PC lento demais → o FX procedural atual aparece
   no lugar, sem crash e sem aviso perdido.
4. `check.py` e `./gradlew build` passam; smoke RCON confere 3 momentos.

## 2. Arquitetura

### Peças novas (todas em `src/main/java/dev/rptag/client/`)

| Arquivo | Responsabilidade | Depende de |
|---|---|---|
| `VideoFx.java` | Player. Registra **uma** camada de GUI; dono de **uma** `DynamicTexture` 1280×720; `iniciar(id, titulo, mensagem)` → `boolean` (false = sem vídeo, use o fallback); desenha o quadro atual em tela cheia com a mistura do aviso (`normal` ou `aditivo`) | `FxTimeline`, `VideoFrameLoader`, `FxTexto` |
| `VideoFrameLoader.java` | Thread daemon `rptag-fx-loader`: decodifica `NNNN_c.jpg` + `NNNN_a.jpg` via `ImageIO`, combina em `NativeImage` RGBA, mantém buffer de até 8 quadros à frente. Não toca na GPU | `ImageIO`, `ResourceManager` |
| `FxTimeline.java` | Lê `fx.json`; calcula o quadro pelo relógio; expõe marcadores (`texto_entra`, `batida`, `linha_reta`, `cinzas`, `texto_sai`…) e avisa quando cada um passa | Gson (já vem com o Minecraft) |
| `FxTexto.java` | Desenha o título "AVISO" + mensagem embaixo (padrão 3.96.4) e a "atuação" de texto de cada aviso, extraída dos `*AvisoFx` atuais | `GuiGraphics` |

### Encaixe no código atual

- Em `ClientEffects`, cada ramo `if (style == N)` passa a tentar
  `VideoFx.iniciar("<id>", "AVISO", limpo)` **antes**; se retornar `false`,
  segue o caminho atual (`MorteAvisoFx.iniciar(...)` etc.).
- Véu avermelhado, agendamento do `canon_death` e partículas/efeitos do
  servidor **não mudam** (são ligados antes da escolha vídeo/procedural).
- Os `*AvisoFx` atuais **ficam** no código como fallback. Nenhum é apagado.
- `ClientGuiLayers` registra a camada do `VideoFx`.
- Efeitos que dependem de ver o jogo por trás (ex.: tela esfriando para
  cinza no MORTE) continuam em código, desenhados antes do vídeo.

### Assets no jar

```
assets/rptag/fx/<id>/fx.json
assets/rptag/fx/<id>/frames/0000_c.jpg   (cor, RGB, qualidade 85)
assets/rptag/fx/<id>/frames/0000_a.jpg   (alfa, tons de cinza, qualidade 90)
...
```

`fx.json` do piloto (exemplo de formato):

```json
{
  "id": "morte",
  "fps": 24,
  "frames": 144,
  "width": 1280,
  "height": 720,
  "blend": "normal",
  "markers": [
    {"name": "texto_entra", "frame": 18},
    {"name": "batida", "frame": 24},
    {"name": "linha_reta", "frame": 91},
    {"name": "cinzas", "frame": 110},
    {"name": "texto_sai", "frame": 136}
  ]
}
```

(Os números acima são ilustrativos; os reais saem dos marcadores do Blender.)

### Fora do mod — `arte/`

```
arte/cenas/morte.py     monta a cena do zero no Blender (via MCP), reproduzível
arte/exportar.py        PNG RGBA → pares JPEG + fx.json (lê marcadores da timeline)
arte/validar.py         confere assets no jar (chamado pelo check.py)
arte/.gitignore         *.blend, *.blend1, render/ (PNG brutos)
```

## 3. Pipeline no Blender (piloto MORTE "Linha Reta")

- Câmera ortográfica 16:9, EEVEE, **film transparente**, 1280×720,
  24 fps, 144 quadros (6 s), bloom/glow ligado.
- **Essência traduzida:**
  - **Monitor cardíaco:** curva emissiva vermelha com rastro e bloom; picos
    espaçando até a **linha reta** (~3,8 s).
  - **Crânio:** pixel art estilo Minecraft como **voxels 3D** com luz real;
    forma-se do pó (partículas convergindo), olhos em brasa com glow
    pulsando em cada batida, apagam na linha reta, desfaz em cinzas que sobem.
  - **Tela:** vinheta em túnel fechando, borda vermelha pulsando nas
    batidas, cinzas caindo o tempo todo.
- **Fica em código:** cor do texto (vermelho → osso na linha reta), sons
  (sino fúnebre, batidas, apito) disparados pelos marcadores, véu,
  `canon_death`, tela esfriando para cinza.
- **Marcadores na timeline** do Blender → `exportar.py` → `fx.json`.
  Tempo do código e do vídeo vêm da mesma fonte.
- **Ponto de aprovação:** antes de exportar para o jar, o dono recebe
  quadros-chave + um MP4/GIF curto da prévia e aprova o visual.

## 4. Player no jogo

### Fluxo

1. `iniciar` lê `fx.json`. Ausente/inválido → `false` (fallback).
2. Loader começa; o aviso só **aparece com ≥ 4 quadros prontos**
   (~50–100 ms).
3. A cada render: `quadro = floor((agora − início) × fps)`. Atrasou →
   **pula quadros** (nunca fica em câmera lenta); som e texto seguem o relógio.
4. Só envia para a GPU quando o quadro muda (no máximo 24×/s).
5. Fim (`quadro ≥ frames`) → para o loader, libera buffers.

### Memória

- GPU: 1 textura 1280×720 RGBA ≈ 3,7 MB, compartilhada por todos os avisos.
- RAM: buffer de ≤ 8 quadros ≈ 30 MB, liberado no fim.
- Aviso novo no meio de outro: o atual para, o novo começa (regra atual).

### Erros (regra de ouro 7: render nunca crasha)

| Situação | Reação |
|---|---|
| `fx.json` ausente/inválido | `iniciar` retorna `false` → FX procedural |
| JPEG corrompido / decode falhou | troca **na hora** para o FX procedural com a mesma mensagem; log |
| Sem quadro pronto por > 0,5 s | mesma troca; log "PC lento" |
| `Throwable` no render | desliga o player na sessão (padrão atual); só FX antigos |
| Saída do mundo | encerra a thread e libera buffers |

### Configuração e diagnóstico

- Arquivo novo do **cliente** `config/rptag/cliente.properties` (o mod
  ainda não tem arquivo de config; só a pasta `musicas/`), lido com
  `java.util.Properties` na inicialização do cliente e criado com o padrão
  se não existir: `fxVideo=true|false` (padrão `true`); `false` → sempre
  FX procedural. Arquivo ilegível → assume o padrão e registra no log.
- `/rp status` mostra: modo vídeo ligado/desligado e quantos avisos têm vídeo.

## 5. Testes

- **`check.py` (greps):** `VideoFx` existe e é registrado; os ramos de
  estilo chamam `VideoFx` antes do FX antigo; fallback nos 3 casos
  (sem json, erro de decode, sem quadros); thread daemon; `fxVideo` na
  config; `/rp status` mostra o modo vídeo; versão 4.2.0 nos 3 lugares.
- **`arte/validar.py`:** cada `fx/<id>/` tem `fx.json` válido; contagem de
  quadros bate; todo `_c` tem `_a`; 1280×720; marcadores dentro da duração.
- **JUnit (primeiro teste Java do projeto):** `FxTimeline` (quadro pelo
  relógio, pulo de quadros, marcadores passados uma vez) e a combinação
  cor + alfa do loader. Roda no `./gradlew build`.
- **Smoke RCON:** `/rp admin avisar @a 4 teste` → capturas em 3 momentos
  (crânio formando, linha reta, cinzas) comparadas com a prévia aprovada;
  repetir com `fxVideo=false` e com um JPEG corrompido de propósito
  (fallback tem que aparecer).

## 6. Entrega

- **4.2.0:** só o MORTE em vídeo; os outros 11 seguem procedurais.
- **Jar fora do git:** o jar passa de 100 MB a médio prazo (limite do
  GitHub no git). `.gitignore` ignora **todos** os `rptag-*.jar`; o
  `rptag-4.1.0.jar` sai do repo; jars só nas **Releases** (limite 2 GB).
- Atualizar README (download), WALKTHROUGH (rotina de jar só em Release),
  ENTREGA (ainda diz 3.43.0) e CHANGELOG (entrada 4.2.0).
- Próximos ciclos: 1–2 avisos por vez, cada um com prévia aprovada antes
  de exportar.

## 7. Estimativas e riscos

- **Tamanho:** ~110 KB por quadro → ~15 MB por aviso → ~180 MB para os 12
  (pode subir com cenas mais detalhadas; o dono aceitou sem limite).
- **CPU:** decode JPEG 720p ≈ 5–15 ms por par em uma thread; folga a 24 fps.
  PC fraco → fallback automático ou `fxVideo=false`.
- **Resolução fixa 720p:** em 1440p/4K o vídeo é ampliado (fica um pouco
  mais macio); o texto continua nítido porque é desenhado pelo código.
- **Blender/MCP:** o Blender precisa estar aberto com o add-on MCP ligado
  durante a produção das cenas; o Blockbench não é usado neste escopo.
