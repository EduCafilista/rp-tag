# Changelog

## 5.11.1 — os RAIOS VERMELHOS voltaram (e aparecem com shader, voando também) — 2026-11-02

> Correção de rumo pedida pelo dono: *"não quero tentáculos de pedra, quero os raios
> vermelhos saindo de deus, se não como vai funcionar quando ele estiver voando?
> quero essa aura em volta dele para ser algo bizarro, não mude a ideia sem me
> consultar"*.

### O que volta
- **Raios vermelhos saindo do corpo** (`RaiosVazioFx` de volta, em geometria 3D),
  **presos no jogador** — nada é medido a partir do chão, então **voando,
  caindo, na água ou no exílio os raios vão junto**.
- A **transformação** do `/vazio ligar` volta a ser rajada de **28 raios
  vermelhos** + onda de choque + lascas de pedra levantando.

### O que NÃO volta (o bug continua consertado)
- O raio **não usa mais** `RenderType.lightning()` (blend aditivo, que somava luz
  e desaparecia em pack de shader claro). Agora é `RenderType.debugQuads()`:
  **blend normal** (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`) + **profundidade**. O
  vermelho mancha a tela em vez de somar — **aparece com Iris/Sodium e no puro**.
- 3 passadas por raio, deslocadas **na direção da câmera** (sem z-fighting):
  - **capa** vermelho-escura, 2,35× de largura, alpha 0,50;
  - **corpo** vermelho vivo, alpha 0,92;
  - **núcleo** laranja-quente, 0,38× de largura, quase opaco.
- Continua tudo o que impedia a aura de "sumir": **salva no mundo** (`Vazios`) e
  **reenviada a cada 3 s / no login**.

### "Bizarro"
- **10 raios longos** (1,6 a 6 blocos) em 360° pra cima e pra baixo;
- **20 cerdas curtas** (0,35 a 1,1 bloco) grudadas no corpo — a coisa "ferve";
- o serrilhado é **re-sorteado a cada ~110 ms** (estala sem parar, não é desenho
  congelado);
- o conjunto **gira devagar** em volta do corpo;
- quem **é** o vazio vê 2/3 dos raios e mais fino; no F1 do próprio nada
  atravessa a mira.

### Partículas do servidor
Continuam **pétreas** (pedaços de `stone`/`cobblestone`/`deepslate`, poeira de
pedra, rastro de cascalho) — o que saiu foi só o **tentáculo de pedra**, que era
geometria e substituía os raios.

**Verificação:** `check.py` **798 PASS / 0 FAIL**; servidor sobe com 0 ERROR.

## 5.11.0 — o VAZIO virou PEDRA (e nunca mais "some") — 2026-11-02

> O dono: *"depois de um tempo os tentáculos vermelhos pararam de aparecer em volta
> do vazio, por quê?"*, *"os tentáculos não aparecem para quem estiver usando
> shaders, fica muito claro"*, *"não quero mais aquelas partículas vermelhas em
> volta do personagem, quero apenas partículas pétreas"* e *"melhora as partículas
> para ser algo mais concreto do que apenas partículas"*.

### Por que a aura "parava" (três causas, três consertos)
1. **Blend aditivo**: os raios usavam `RenderType.lightning()` (`SRC_ALPHA, ONE`),
   que **soma luz**. Com shader/pack claro o vermelho estourava em branco e
   desaparecia. Agora a aura é **geometria opaca** (`RenderType.debugQuads()`:
   blend normal + profundidade).
2. **Estado só na memória**: o `HashMap` de "deuses" zerava no restart e o
   `/vazio ligar` se perdia. Agora fica **salvo no mundo** (tag `Vazios`) e volta
   no `ServerStartedEvent` (`restaurarDeuses`).
3. **Pacote enviado uma única vez**: quem entrava depois do `/vazio ligar` nunca
   recebia a aura. Agora o servidor **reenvia a cada 3 s** e **no login**
   (`reenviarAura`).

### A aura agora é de pedra (nada de partícula vermelha)
- **`TentaculosVazioFx` (novo, cliente)**: 9 tentáculos de pedra em geometria
  sólida — tubo de seção quadrada (4 faces por segmento), sombra por face, "nós",
  veios escuros, bico na ponta e pedrinhas na base. Brotam do chão (0,8 s), ficam
  4–9 s e afundam (0,7 s) — reciclam sozinhos, nunca congelam.
- **`RaiosVazioFx` apagado** (eram as fitas vermelhas aditivas de 5.7.4).
- **Servidor**: poeira de pedra no lugar dos veios roxos; **estilhaços de BLOCO**
  (`stone`/`cobblestone`/`deepslate`) com gravidade no lugar dos raios de dust;
  rachaduras do chão e rastro de caos em **cascalho + poeira cinza**; a
  transformação do `/vazio ligar` virou **explosão de lascas de pedra**.
- **Vermelho**: só os **dois olhos** no rosto (não é aura, é a cara do vazio).
- Quem **é** o vazio vê 2/3 dos tentáculos e mais baixos; no F1 do próprio os que
  atravessariam a mira somem.

**Verificação:** `check.py` **796 PASS / 0 FAIL**; servidor sobe com 0 ERROR;
`/neptune` e `/rp ajuda` atualizados ("aura de PEDRA").

## 5.10.1 — título com relevo (gradiente, moldura, selo) + `/title` vanilla fora do jogo — 2026-11-02

> Dois pedidos do dono: *"quero que seja bonitinho [o título], não apenas as letras
> naquela cor"* e *"esse /title é do mod? se for, apaga e deixa só o /titulo"*.

### 1) O título ganhou desenho (TAB, chat e nametag)
- **Classe única** `TituloEstilo` — servidor (`Titulos`) e cliente (`ClientTitulos`)
  usam a MESMA regra. Antes o cliente tinha a própria cópia e os dois podiam divergir.
- **Gradiente letra por letra**: `tom = cor × (1,55 − 1,02·u)`, `u = i/(n−1)` — claro no
  começo, cor cheia no fim. É o "relevo de metal" que o Minecraft consegue fazer (cor
  por caractere, sem shader).
- **Colchetes a 55 % e sem negrito** → viram moldura.
- **Etiqueta em NEGRITO** (selo), **título pessoal em ITÁLICO** (apelido) com espaço antes.
- **Nome do jogador intocado.**
- **Cor escura** (luminância < 0,30) mistura branco `0,68·(1−0,88u)`, senão `Sombra`,
  `Vazio` e `Morto` desapareciam no fundo escuro do TAB.
- **Hover** na etiqueta: `Classe: Chronos  #C9A6FF` (chat e TAB; no nametag não há mouse).

### 2) `/title` sai do jogo por padrão
- O `/title` **não é do mod**: é comando **vanilla** (`TitleCommand`). Mesmo assim sai do
  jogo pra não confundir com `/titulo`; volta com `/rp admin titlevanilla on` (salvo no mundo).
- **O bug que custou três rodadas**: o Brigadier guarda os filhos da raiz em `children`,
  `literals` e `arguments`; `getChildren()` é só a view do `children` e **o parser lê o
  `literals`**. Remover só pelo `getChildren()` deixava `title=false` no log e o `/title`
  executando. Agora `tirar()` limpa `children` **e** `literals`.
- Sem reiniciar: tira/devolve na hora e reenvia a árvore pros jogadores online.
- Rede de segurança no login (cobre `/reload` que remonte a árvore).

**Verificação:** `check.py` **789 PASS / 0 FAIL**; testes por RCON com o servidor de pé
(`title` bloqueado → `on` → `title` executando → `off` → `title` bloqueado).

## 5.10.0 — CONHECIMENTO novo (memórias, balança) + mod 30 MB mais leve — 2026-11-02

> O dono mandou dois pedidos juntos: *"troque o aviso conhecimento para esse"*
> (protótipo novo em `n.uguu.se/YUkBZTyA.java`) e *"o mod está começando a ficar
> bem pesado, será que você consegue dar uma otimizada nele para diminuir o
> tamanho?"*

### 1) Aviso CONHECIMENTO (estilo 16) refeito pelo protótipo NOVO
O aviso antigo (o olho dourado com 3 anéis de runas e 12 olhinhos) era o porte de
um protótipo **anterior**. O novo é **calmo e geométrico**, como o Conhecimento
de Ordem Paranormal: sigilo, olho, memórias e balança.

- **O sigilo**: 24 fios radiais, anéis 172/178/146, os 72 tracinhos do aro, a
  **estrela de seis pontas** (dois triângulos girando em sentidos opostos), dois
  **quadrados** sobrepostos, anéis internos 92/72 e **duas bandas de runas**
  (48 no aro de 159 e 30 no de 82) — o alfabeto de 28 runas é o mesmo (semente 21).
- **As 4 MEMÓRIAS penduradas em cordas** (foto sépia com vultos, moldura dourada,
  alfinete), ligadas ao anel por **cordas curvas** (Bézier quadrática) que
  balançam; pulsos de luz sobem da memória pro olho.
- **A revelação**: no protótipo era passar o mouse ~1 s em cima da memória.
  **No jogo não tem mouse** — então a barra de progresso **enche sozinha** (1,9 s
  por memória, a partir de 4,3 s) e **o olho olha para a memória que está sendo
  revelada**. Quando enche, a foto escurece no pé e a **VERDADE** aparece digitada:
  - *"Você já esteve aqui antes."*
  - *"Seu nome não foi escolha sua."*
  - *"Alguém lembra de você. Você não lembra."*
  - *"As vozes sempre foram uma só."*
- **Brilhos que somam de verdade**: `Buf` só desenha no `flush()`, então o blend
  do momento do flush é o que vale — os trechos aditivos (luz do fundo, feixes,
  pulsos das cordas, poeira, pratos da balança, halo do olho/ícone) agora abrem
  o **próprio Buf** (`somar`/`voltar`). Antes o brilho saía como "fumaça" opaca.
- **A BALANÇA SANIDADE × CONHECIMENTO** (canto superior esquerdo, com os dois
  pratos e os brilhos): `conhecimento = nRev/4 × 0,72 + 0,28 × ease((t−4)/22)`,
  `sanidade = 100 − 0,45 × conhecimento`. Ela **inclina** conforme o conhecimento
  pesa.
- **Contador VERDADES**: 4 losangos no topo direito; cada verdade acende um.
- **4 colunas de runas** nas bordas da tela (subindo/descendo, com umas que
  brilham de vez em quando) — como no protótipo, mas presas à borda REAL da
  janela, não à moldura 960×640.
- **Olho**: lente com degradê, íris com **44 fibras radiais**, pupila que respira,
  piscada a cada 7,5 s, brilho aditivo em volta.
- **Com as 4 verdades**: onda grande, clarão e o texto **troca** para o STAGE1
  (*"Agora você sabe. E não pode deixar de saber."*).
- 10 sussurros nas laterais (*"tudo tem uma regra"*, *"a verdade tem um preço"*…),
  70 poeiras douradas, faixa escura no rodapé, ícone de perigo + **AVISO**.
- Duração: **17 s** (antes 13,5 s), porque agora tem 4 revelações + a troca de texto.
- **Nada de mouse, nada de tecla**: o aviso roda sozinho do começo ao fim.

### 2) Otimização: 105 MB → 75 MB de recursos (jar 99,9 MB → 66,0 MB)
- **Onde estava o peso**: `assets/rptag/fx` = **95,5 MB** de quadros JPEG
  (gelo 30,9 / egipcio 21,8 / void 15,1 / inferno 13,5 / morte 9,1 / tablet_fundo
  5,1). Cada quadro é um par `NNNN_c.jpg` (cor) + `NNNN_a.jpg` (máscara).
- **O que foi feito** (`arte/otimizar_frames.py`): **recompressão**, nada mais.
  - mesmo **1280×720**, mesmo nome, mesmo formato, mesmo caminho;
  - cor em **qualidade 72** + Huffman otimizado + **progressivo**;
  - alpha em **55** (é máscara: borda macia, metade do peso);
  - **se o arquivo novo ficasse maior que o original, o original ficava**.
- Resultado: **95,5 MB → 65,0 MB (−32%)**, 1.428 quadros trocados, 324 mantidos.
  Erro médio medido nos quadros de teste: **1,6 a 2,9 de 255** (invisível a 1:1;
  a prévia `previa/otimizacao_5.10.0.png` mostra antes × depois no pixel).
- **Por que não reduzi a resolução**: o `FxTimeline` recusa qualquer coisa que não
  seja 1280×720 e o `VideoFrameLoader` tem caminho rápido que depende disso — o
  dono escolheu o nível **leve** (*"só recomprimir"*), então a resolução ficou.
- O script fica no repo: dá pra rodar de novo (`--medir` só mede).

### Verificação
- `check.py`: **776 PASS / 0 FAIL** (12 checks novos: memórias, verdades, balança,
  sigilo, revelação por tempo, STAGE1, teto de 80 MB, maior quadro < 200 KB,
  amostra toda 1280×720, script e prévias no repo).
- Servidor de teste: `Done (0.752s)`, **0 exceções**, `RP Tag 5.10.0` na lista.
- Jar: **69.292.643 B** (66,0 MB), 1.752 quadros dentro (nenhum perdido).

### Release
- **v5.10.0** — release **431719665**, asset **702487180**
- `https://github.com/EduCafilista/rp-tag/releases/download/v5.10.0/rptag-5.10.0.jar`
- **69.292.643 B** (66,0 MB) · md5 **`2aa87c3b5cb909874511481dbce1761e`**

## 5.9.0 — TÍTULOS com COR MODIFICÁVEL (TAB, chat e nametag) — 2026-11-02

> O dono perguntou: *"você fez o set dos 'títulos' para os players? que vai ficar
> no TAB e em cima da cabeça do player? como os Chronos? e lembrando que tem que
> ser modificável a cor."*

### O que já existia (5.7.2) e o que faltava
- **Já existia** o sistema de títulos: `[Chronos] Nephisto [O Ferreiro]` no **TAB**,
  no **chat** e no **nametag acima da cabeça**, salvo no mundo (`rptag_titulos`),
  com a paleta de 12 etiquetas (`/titulo paleta`).
- **Faltava a COR modificável** — a etiqueta só podia usar a cor de fábrica da
  paleta e o título pessoal era sempre branco.

### O que entrou na 5.9.0
- **`/titulo cor <jogadores> <cor>`** — pinta a ETIQUETA (o `[Chronos]`).
- **`/titulo cor pessoal <jogadores> <cor>`** — pinta o título pessoal (o `[O Ferreiro]`).
- **Cor por nome** (português e inglês): `ouro`, `dourado`, `amarelo`, `vermelho`,
  `vermelho-escuro`, `verde`, `verde-escuro`, `menta`, `ciano`, `aqua`, `azul`,
  `azul-escuro`, `roxo`, `lilas`, `rosa`, `laranja`, `branco`, `preto`, `cinza`,
  `cinza-escuro`… e **HEX livre**: `FFD700`, `#FFD700` ou curto `#FFF`.
- **`padrao`** volta pra cor de fábrica da etiqueta; cor **desconhecida não pinta
  nada** — o comando reclama (`"xyz"` não pinta de preto sem avisar).
- **TAB do comando sugere as cores** (`/titulo cor @a <TAB>`).
- **`/titulo definir` e `/titulo limpar` aceitam vários jogadores** (`@a`, lista).
- **`/titulo ver <jogador>`** mostra as duas cores em HEX ("de fábrica" / "escolhida").
- A cor da etiqueta **sobrevive a redefinir a mesma etiqueta** (só volta pra
  fábrica quando a etiqueta muda).
- **Preto puro (`#000000`) funciona** — antes o campo `cor == 0` era lido como
  "sem cor" e voltava pra paleta (agora o "sem cor" é `-1`).
- **Canal do mod subiu pra v12** (`sync_titulo` ganhou a cor do título pessoal):
  cliente 5.8.1/menor é recusado com a mensagem de versão de sempre.

### Onde cada coisa é montada
| lugar | quem monta |
|---|---|
| TAB | `ServerEvents.onTabListNameFormat` → `Titulos.aplicar(...)` |
| chat | `ServerEvents.onNameFormat` → `Titulos.aplicar(...)` |
| acima da cabeça | `NameplateRenderer.onRenderNameTagTitulo` → `ClientTitulos.aplicar(...)` (pacote `sync_titulo`) |

### Verificação
- `check.py`: **765 PASS / 0 FAIL** (10 checks novos do 5.9.0 + os de protocolo atualizados para v12)
- `VerificaTitulos`: `ouro → #FFAA00`, `#FFD700 → #FFD700`, `fff → #FFFFFF`,
  `preto → #000000`, `xyz → NULL` (avisa), `Chronos` de fábrica `#C9A6FF`.
- servidor 5.9.0: `Done (0.732s)`, **0 exceptions**; `/titulo paleta` responde a
  paleta completa e `/titulo cor @a #FFD700`, `/titulo cor pessoal @a ouro`,
  `/titulo definir @a Chronos` **parseiam** ("No player was found" = sem ninguém online).
- `previa/titulos_5.9.0.png` — prévia dos 3 lugares + paleta + comandos.

### Publicação
- commit `7084ebd` (main) · **release 431675712** (`v5.9.0`) · asset **702344889**
  (`rptag-5.9.0.jar`, 99.930.695 B, md5 `6e9f6f3cdbb6df1678209bd2e33fc05d`)
- https://github.com/EduCafilista/rp-tag/releases/download/v5.9.0/rptag-5.9.0.jar

### Limpeza
- a pasta `src/main/resources/assets/rptag/fx/divino/` (só um `fx.json` órfão,
  o vídeo saiu do mod na 4.9.1) foi **apagada** — era o que deixava 2 falhas no
  `arte/validar.py`.

## 5.8.1 — AVISO "MEDOOP" (o protótipo AvisoMedo do dono) — 2026-11-02

> O dono mandou o protótipo JavaFX `AvisoMedo` (arquivo `oymNvtcE.java`) com a
> legenda **"avisar (medoop)"** — o medo de Ordem Paranormal: a membrana entre a
> realidade e o outro lado racha quando alguém sente medo.

### Novo estilo 22 `medoop`
`/rp admin avisar <alvo> medoop <texto>` — com TAB (aparece ao lado de
`onefallall`, `morteop` e `sangueop`), aceita `@a` e vários nomes. Dura **16 s**.

O que aparece na tela:
- **parede de tijolos** escura com **pichações** (*NÃO OLHE, ELE ESTÁ AQUI,
  SAIA, NÃO DURMA, ELE OUVE, CORRA, NÃO ESTÁ SOZINHO*) e **mãos ensanguentadas**;
- **lanterna** que abre um buraco de visão (o resto é escuridão quase total);
  ela passeia sozinha (lissajous — no jogo não existe mouse) e **pisca** de vez
  em quando; o raio **encolhe de 210 → 115 px** conforme o medo sobe;
- **8 olhos** soltos no escuro que **somem quando a luz chega perto** (e o
  jogador nunca os vê apagarem: eles reaparecem em outro canto);
- **6 apagões** (2,4 s / 4,0 s / 5,4 s / 6,7 s / 7,9 s / 9,0 s): a tela apaga, a
  câmera treme e a **silhueta alta fica mais perto e maior** a cada um (ela vai
  de 150 px a 430 px de altura, do canto até o centro);
- **9 rachaduras** da membrana com galhos, **crescendo junto com o medo**,
  pulsando na batida do coração;
- **medidor MEMBRANA** no canto (0 → 100 %);
- **sussurros** nas beiradas (*"não olhe", "está atrás de você", "você ouviu
  isso?", "ele sabe que você viu", "continue andando", "não pare", "está mais
  perto", "não vire"*);
- no fim, **"ELE ESTÁ ATRÁS DE VOCÊ"** com aberração cromática (azul de um lado,
  vermelho do outro);
- vinheta preta que **pulsa com a batida**, e o rodapé escuro onde entra o texto
  digitado: **MEDO** + *"A membrana só se rompe quando você sente medo."* +
  *"Não olhe para trás."*.

### Decisões do porte (o que mudou em relação ao protótipo)
- **Não existe mouse no aviso do jogo** → a lanterna anda sozinha em lissajous
  lento (250·sen(0,37t), 120·cos(0,29t)) e a "câmera" treme nos apagões.
- **Escuridão em leque radial de 5 anéis** (64 fatias, **cor por vértice**).
  Anel preenchido por cima de anel **soma alfa** e o buraco da luz fechava
  (virou um borrão cinza) — já tinha acontecido no 5.7.x.
- **A silhueta é desenhada POR CIMA** do escuro e das rachaduras, com um
  **contorno frio** (0x3A4760 a 45 %). Silhueta 100 % preta em fundo preto
  simplesmente não existe na tela.
- Rachaduras **mais curtas e mais finas** que a primeira tentativa: o leque de
  9 troncos com 430 px de comprimento virava uma teia branca cobrindo tudo.
- Reaproveita o `AvisoFxUtil.Buf` (triângulos POSITION_COLOR) e o
  `FxDraw.discoGrad` dos avisos 19/20/21; `try/catch` que **desliga só o aviso**,
  nunca o jogo.

### Arquivos
- `client/MedoOpAvisoFx.java` (novo, camada `rptag:aviso_medo_op`)
- `RPCommands.java` — `case "medoop" -> 22`, literal `medoop` no TAB, `nomeEstilo`
  (22 = MEDOOP; aproveitei e completei 18..21, que faltavam) e a lista do `/rp ajuda`
- `ClientEffects.java` — estilo 22 → `MedoOpAvisoFx.iniciar(...)`
- `ClientGuiLayers.java` — `MedoOpAvisoFx.registrar(event)`
- `arte/previa_medo.py` → `previa/medo_5.8.1.png` (4 momentos, mesma matemática)

### Verificação
- `check.py`: **755 PASS / 0 FAIL** (10 checks novos do 22)
- servidor 5.8.1: `Done (0.726s)`, **0 exceptions**;
  `/rp admin avisar @a medoop a membrana rasgou` → *"No player was found"*
  (o comando existe e parseia; não havia ninguém online)
- `/rp ajuda` lista `… onefallall, morteop, sangueop, medoop`

### Publicação
- commit `3c0b035` (main) · **release 431162197** (`v5.8.1`) · asset **700867776**
  (`rptag-5.8.1.jar`, 99.927.034 B, md5 `bd4a2c4636bdaa3997540ef999337a1f`)
- https://github.com/EduCafilista/rp-tag/releases/download/v5.8.1/rptag-5.8.1.jar

## 5.8.0 — MÁSCARA DE PEDRA REFEITA (malha nova + textura com rosto) — 2026-11-01
## 5.8.0 — MÁSCARA DE PEDRA REFEITA (malha nova + textura com rosto) — 2026-11-01

> O dono: *"a máscara está horrível até o momento"*.

### O que estava errado
- **5.3.0** passou a usar o `.bbmodel` do dono (116 cubos). A **geometria** está
  lá, mas a **textura do arquivo** é pedra cinza com ruído — **não tem rosto
  nenhum pintado**. No jogo o resultado era um bloco de pedra com dois buracos.
  Pior: os 116 cubos são um mosaico de caixinhas com 52 rotações diferentes, e
  as emendas aparecem como degraus.
- A malha procedural da **4.9.6** tinha relevo suave demais (parecia gesso
  derretido) e a textura era pintada num script separado, sem garantir o
  alinhamento com a geometria.

### O que foi feito (novo `arte/gerar_mascara_58.py`)
- **Malha nova de 9.852 triângulos** (grade 44×56), volume **fechado**: casca
  externa + casca interna + aba nas bordas (tem grossura de pedra).
  Relevo esculpido por gaussianas: silhueta de escudo, **testa em Y**, sobrancelha
  em **V** (brava), **órbitas em fenda inclinada**, nariz com ponte/dorso/asas e
  narinas, maçãs, **focinho**, **boca funda**, **duas presas**, queixo projetado e
  **espiral na têmpora** (a voluta do modelo original).
- **Textura 256×256 pintada nas MESMAS coordenadas (u,v) da malha** — por isso o
  desenho nunca escorrega: pedra azul-acinzentada com grão, **pintura preta em
  volta dos olhos** (a marca do JoJo, com bico entre eles), **amêndoa branca com
  pupila**, pálpebra, cílios, sobrancelha grossa, crista em Y, espirais,
  hachura nas maçãs/mandíbula, boca escura com dentes e **presas compridas**,
  narinas, **rachaduras** quebradas e a metade esquerda na sombra (como na
  referência).
- `CamadaMascara` e `RenderItemMascara` usam a malha/textura novas
  (`geo/mascara_58.bin` + `textures/entity/mascara_58.png`); o `.bbmodel` de 116
  cubos **continua no jar como reserva** (se o arquivo novo faltar, ele assume).
- `previa/mascara_58.png`: 4 vistas renderizadas com a malha REAL do jar (frente,
  3/4, lado e um close no rosto).
- BUG de empacotamento corrigido no caminho: o cabeçalho do `.bin` precisa ser
  **big-endian** (`struct.pack(">ii", ...)`); com numpy 2 o arquivo saía
  little-endian e a malha inteira era lida como lixo. O gerador agora **relê o
  arquivo e valida** antes de terminar.

### Qualidade
- `check.py` **745 PASS / 0 FAIL** (+9 checagens da máscara: cabeçalho do `.bin`,
  tamanho da textura, malha/textura ligadas na camada e no item, reserva antiga,
  gerador e prévia).
- Servidor de teste com a 5.8.0: `Done (0.751s)`, **0 exceptions**.

## 5.7.9 — SOM DO TERREMOTO CALA, CARA DO BICHO, EXÍLIO CLARO, SAÍDA AUTOMÁTICA E 3 AVISOS — 2026-11-01

### Bug do dono: o som do terremoto não parava
- `pararTerremoto` agora manda **`ClientboundStopSoundPacket`** por jogador para
  `rptag:terremoto_colapso` e `rptag:rumble_terremoto` (fonte `null` = mata todas
  as instâncias) — o mesmo padrão que resolveu a trilha no `desligarDeus`.
- Rede de proteção no cliente: ao receber `Terremoto(ativo=false)`, o
  `SoundManager` também para os dois sons na mão.

### Bug do dono: a cara do bicho não aparecia
- Era **ordem de desenho**: o vulto escuro (mesma textura, 1,06×, 85% de alfa)
  era desenhado DEPOIS da cara e cobria o rosto. Agora: vulto primeiro (corpo
  atrás, respirando), **cara opaca por cima** (1,18× mais clara, aberração
  cromática), **olhos acesos em aditivo** e a vinheta por último.

### Exílio MAIOR, VISÍVEL e ANDÁVEL
- `dimension_type/exilio.json`: `ambient_light` 0,06 → **0,42**, `has_skylight`
  **true**, `has_ceiling` **false**, `fixed_time` **6000** (meio-dia),
  `effects` `the_end` → **`overworld`**.
- `dimension/exilio.json`: camadas novas (`bedrock 1 / deepslate 3 / blackstone 2
  / tuff 2 / sculk 1 / light_gray_concrete 1`, superfície em **y=9**) e bioma
  `stony_shore`.
- **BUG DE NASCIMENTO**: o teleporte era para **y=1** (dentro da pedra). Agora
  `chegadaNoExilio()` resolve pela **superfície** (heightmap) e `prepararPraca()`
  constrói, uma vez por sessão, uma **praça de 30×30** (cruz de concreto claro,
  anel de pedra polida, centro de blackstone trabalhado) com **8 colunas de
  obsidiana + lanterna de alma** e uma cruz de lanternas no centro.
- `/vazio entrar` avisa que dá pra **andar, construir e usar comando**.

### A animação de saída virou automática (e o `/saida` saiu)
- **Removido** o comando `/saida` (o dono recusou: *"quero que a animação
  apareça quando o player sair do jogo"*).
- `NeptuneEventos.saidaAutomatica(player)`, chamado por `ServerEvents.onLoggedOut`:
  coluna de **sombra** no lugar exato (SQUID_INK/LARGE_SMOKE/ASH), **almas** e
  fogo de alma, **glifos** (códigos) em volta, **`SOM_EXPULSAO`** para quem está a
  32 blocos, `ScreenTextPayload(nome, 18, 9)` (estilo 18 = o sprite se desfazendo)
  e o anúncio no chat. Não depende mais do flag `saidafx` (o toggle continua).

### Avisos novos (protótipos do dono) — estilos 19, 20 e 21
- **`onefallall` (19)** — `client/SolAvisoFx.java`, porte do `AvisoSol`
  (`SfUpFekf.java`): sol dourado aquarelado (13 anéis com jitter + 24 arcos de
  pincel), 11 direções de raios, **8 silhuetas do legado** com tocha pulando e
  frases, silhueta encapuzada com o sol na cabeça e olhos que acendem, brasas,
  ripples, sussurros e as 3 linhas do protótipo (a 1ª é substituída pelo texto do
  comando). 13 s.
- **`morteop` (20)** — `client/MorteOpAvisoFx.java`, porte do `AvisoMorte`
  (`OjqYnxyx.java`): espiral de 3 braços, relógio de 60 marcas com 3 ponteiros,
  crânio que se forma → encara → **se desmancha em cinzas**, 200 cinzas, 12 ossos
  à deriva, sussurros. 11 s.
- **`sangueop` (21)** — `client/SangueOpAvisoFx.java`, porte do `AvisoSangue`
  (`vScmJibF.java`): fundo vermelho pulsando a 62 bpm, 26 veias crescendo das
  bordas, gota com degradê radial, anel tracejado (dois, sentidos opostos), as 7
  palavras acendendo e o ECG com "PULSO". 10,5 s.
- `client/FxDraw.java`: primitivas que faltavam (triângulo, linha grossa, anel,
  arco, disco, disco em degradê radial, elipse, losango, texto centrado).
- Registrados em `ClientGuiLayers`, ligados em `ClientEffects` (19/20/21) e nos
  nomes do `/rp admin avisar` e do `/lorezone aviso`.

### Qualidade
- `check.py` **736 PASS / 0 FAIL** (as checagens do exílio escuro e do `/saida`
  foram reescritas pro pedido novo; +12 checagens 5.7.9).
- Servidor de teste: `Done (0.653s)`, **0 exceptions**; dimensão conferida por
  comando (`light_gray_concrete` em y=9).

## 5.7.8 — SOM DE EXPULSÃO, EXÍLIO ANDÁVEL, CAIXA NO 1º CLIQUE — 2026-11-01

### Som de expulsão (o que o dono mandou)
- `expulsao.ogg` (a partir do MP3 enviado, 2,3 s) registrado como `rptag:expulsao`
  e tocado:
  - quando o player é **jogado pra fora do mapa** (`/vazio exilar`) — quem está
    a até 40 blocos também ouve;
  - no **`/terremoto sair`** (o kick) e na volta aleatória.

### Exílio (`/vazio`)
- **`/vazio soltar <jogador>` não devolve mais no mesmo lugar**: sorteia um ponto
  do Overworld até **4000 blocos** do spawn (24 tentativas, rejeita água/lava/ar,
  usa o heightmap) e avisa "a escuridão te cuspiu em algum lugar do mundo".
- **A dimensão `rptag:exilio` virou um lugar de verdade**: chão em camadas
  (bedrock → deepslate → blackstone → tufo → sculk), **escura** (luz ambiente
  0,06, sem céu, meia-noite fixa) mas **dá pra andar e se perder**.
- **`tickExilioMundo`**: a cada ~14 s nasce um **monólito** (deepslate/obsidiana
  com **lanterna de alma** no topo) a 10–25 blocos de quem está lá — todos
  iguais, servindo de referência e de armadilha; sussurros a cada ~11 s e
  poeira escura nos pés.
- **`/vazio entrar`**: o admin entra no exílio pra testar sem punir ninguém.

### Bicho (caixa)
- **Desfeito** o bicho como criatura (o dono pediu): saíram `BichoEntity`,
  `BichoRenderer`, o registro `rptag:bicho` e o spawn. **O susto na tela
  continua** e ficou melhor: a cara **avança** nos primeiros 0,6 s, as bordas
  escurecem e há um vulto respirando atrás.
- **A música agora começa no PRIMEIRO CLIQUE**, não ao colocar a caixa
  (`/bicho aqui` fica em silêncio até alguém bater).

### Testes
`check.py`: **716 PASS / 0 FAIL**. Servidor 5.7.8 sobe limpo (`Done 0.885s`);
dimensão do exílio gera **sculk no topo (y=11) e deepslate embaixo (y=2)**;
`/terremoto`, `/saida`, `/bicho` respondem; **zero exception** no log.

## 5.7.7 — O VAZIO NÃO CEGA, O BICHO DÁ PRA MATAR, CÓDIGOS EM VOLTA DO PLAYER
### (2026-11-01)

### Vazio (aura)
- **O vazio não fica mais cego em primeira pessoa.** As partículas do manto
  acima de 0,95 do corpo (peito/rosto) agora vão **só para os outros jogadores**
  (`auraPart`): em F1 ele vê o mundo; em F5 vê o manto e os raios.
- **Aura no MUNDO, não na tela.** Saíram as 70 nuvens de fumaça e as rachaduras
  desenhadas no overlay (era o **borrão preto flutuando no céu** do print).
  `VazioAuraDarkFx` agora só faz vinheta leve + granulação nas bordas.
- **Raios negros e vermelhos saindo do player**: 4 por disparo (a cada 4 ticks),
  saindo do peito, dos pés e do alto da cabeça, miolo NEGRO (SQUID_INK + dust
  quase preto) e ponta VERMELHA com brasa — 2,6 a 6,2 blocos de alcance.

### Exílio
- Animação maior: **aro de escuridão no chão** que abre e fecha, **6 tentáculos**
  de sombra em volta do corpo e **estouro final de brasa** em círculo.

### Terremoto
- **Tela balanceada**: nebulosa pela metade (0,10 → 0,05), desfoque do pulso de
  1,6–4,4 → **0,8–2,0**, detritos 26 → 18 e mais apagados.
- **Fendas aleatórias**: não abrem mais no pé do jogador — são sorteadas de
  **2 a 12 blocos** em qualquer direção. A poeira também deixou de ser um anel
  girando: agora são 5 pontos sorteados por quadro.

### Som do vazio
- **Saiu** o som que o mod tinha acrescentado na transformação (`WITHER_SPAWN`).
  Fica só o som original (`vazio_apagar` / `trilha_deus`).
- **BUG corrigido**: a trilha do deus continuava tocando "no ouvido de todos,
  infinito" depois do `/vazio desligar` — agora é enviado `StopSound` para todo
  mundo ao desligar.

### Apagar alguém (`/vazio apagar`)
- **Cinzas, sombra e alma**: por 3 s no local saem ASH + WHITE_ASH subindo,
  SQUID_INK/LARGE_SMOKE escorrendo e uma coluna de SOUL (com SOUL_FIRE_FLAME) —
  a marca pública de que a perda foi canônica.

### Bicho
- **1º clique = som; 2º clique (depois de 4 s) = susto.** Antes cada toque já
  arrebentava a caixa.
- **O bicho agora é uma criatura de verdade** (`rptag:bicho`): sai da caixa,
  persegue quem abriu, tem **40 de vida** e pode ser morto (solta grito e medo).
  Renderer próprio (billboard com a arte do susto).
- **Caixa**: só **uma** caixa tocando por vez no mundo; ao colocar outra, a
  anterior é apagada e o som dela é **parado** (`pararMusicaCaixa`) — fim das
  músicas sobrepostas.

### Saída
- **Códigos verdes EM VOLTA DO PLAYER** (no mundo): novo pacote `Codigos` +
  `GlifosSaidaFx` — 44 glifos (0/1/0x1F/session.close()…) em 3 anéis girando em
  volta do corpo, coluna de scan subindo, anel no chão e o nome flutuando.
  **Todo mundo vê**, na saída real e no `/saida`.

### Testes
`check.py`: **715 PASS / 0 FAIL** (+14). Servidor 5.7.7 sobe limpo; `rptag:bicho`
spawna; terremoto liga/desliga; `/saida` responde; **zero exception** no log.

## 5.7.6 — EXÍLIO ANIMADO, VAZIO PRETO, TERREMOTO ANDÁVEL — 2026-11-01

### Exílio (`/vazio exilar` / `/vazio soltar`)
- **Animação no mundo** (2,6 s) antes de levar: espiral de escuridão, pedaços do
  chão sugados, raios vermelhos, cinza. Todo mundo vê.
- **A volta também é animada** (escuridão se abrindo) e devolve pro **lugar
  exato** de onde a pessoa saiu (`ORIGEM`), não mais pro spawn.
- Na tela: sombras fechando (ida) / se abrindo (volta), máx. 0,72 — sem tela
  preta chapada.

### Vazio (`/vazio ligar`)
- **Manto de 3 camadas de preto** (SQUID_INK + LARGE_SMOKE + dust quase preto),
  20 fios em espiral, em cone; a **faixa do rosto é poupada** e ganha **dois
  olhos vermelhos** que acompanham a direção do olhar.
- **Transformação:** rajada de 28 raios vermelhos (360° + pra cima), onda de
  choque de brasa no chão e rugido; por 2,5 s os raios 3D ficam mais grossos,
  longos e vermelhos (`surtoAte` em `RaiosVazioFx`).
- **Rastro de caos:** mancha no chão a cada meio bloco andado, soltando tinta,
  brasa, sangue e cinza por **22 s** (`tickManchas`).

### Terremoto
- **Dá pra andar:** saiu `imobilizar` do pulso; entrou `pesado()` = **−45% de
  velocidade** e **−0,20 de pulo**. Anda arrastado, mas anda.
- **Sem partícula verde:** `estadoPoeira()` converte grama/musgo/folha/mato em
  **terra grossa** (nos 4 pontos que usavam o bloco real).
- **Som novo**: base é gravação real CC0 (OpenGameArt "Rumbling Sound Effects",
  bretbernhoft) com **excitador harmônico** (o original era todo abaixo de 60
  Hz), estalos de rocha em cascata, sub-impactos, arrasto de terra, grãos e
  groan. Grave 534→1088, corpo 97→435, brilho 4→23 (mesma medição de antes).
- **`/terremoto engolir <jogador>`**: o chão abre, engole e leva pro exílio com
  efeito que todos veem.
- `/terremoto sair` agora avisa e assusta quem está a até 24 blocos.

### Saída
- **`/saida <jogador>`** e **`/saida todos`**: roda a animação de saída (estilo
  18, `SaidaPlayerFx`) na hora, pra todo mundo, sem ninguém desconectar.

### Testes
`check.py`: **701 PASS / 0 FAIL** (+12). Servidor 5.7.6 sobe limpo; terremoto
liga/desliga e `/saida` responde sem exception.

## 5.7.5 — TERREMOTO NO MUNDO (sem rachadura na tela) — 2026-11-01

**O que você apontou:** "não estou gostando ainda de como fica o terremoto" e,
na pergunta de qual era o pior, **as rachaduras desenhadas na tela**. Feito:

**1) A RACHADURA DE TELA SAIU.** Saiu o "abismo preto no rodapé" + as 11
rachaduras serrilhadas desenhadas por cima do jogo (aquele efeito de vidro
rachado na lente). Sobrou só uma faixa de poeira nos **10% de baixo** da tela
(alpha 52/255) — nada de rabisco na frente do jogador.

**2) O TERREMOTO AGORA ACONTECE NO MUNDO.**
- **Abertura:** em vez de desenhar uma fenda na tela, o servidor abre **duas
  fendas REAIS** embaixo/ao lado de cada jogador (blocos viram ar e voltam no
  fim), cospe **pedra e poeira do próprio bloco** pra cima (`BLOCK` + 
  `FALLING_DUST`) e toca o estalo da terra quebrando. Na tela sobra só o "peso":
  bordas escurecem 0,9 s e um blur de 1,0 nos primeiros 0,18 s.
- **Escombros caindo do alto:** a cada 1,5 s chove pedra/terra em volta de cada
  jogador — é o "tudo desabando" sem nada desenhado na lente.
- **Fendas quebrando blocos mais frequentes:** de ~5 s para **2,8–4,6 s**, com a
  poeira do bloco que realmente quebrou (e tudo é restaurado no fim).

**3) Menos tela preta.** A queda agora só escurece as bordas (vinheta de terra
0,62) com a faixa de terra na altura dos olhos — sem cortina preta, e o pulso já
não estoura o desfoque.

### Sobre a "animação nova do Marcelo"
Procurei no GitHub: o último commit dele é o `a4cc42d` (5.5.0) e o terremoto
dele é o **5.4.0 antigo** (`TerremotoFx`: tremor + pulso roxo com estrelinhas +
blur espacial + banimento verde). Não há animação nova — essa é mais velha que a
que está no mod. Se ele mandar outra, é só me avisar o nome da branch.

### Testes
`check.py`: **689 PASS / 0 FAIL**. Servidor: carrega 5.7.5 limpo (`Done
(0.604s)`), `/terremoto iniciar 20` liga e `/terremoto parar` desliga sem
exception no log.

## 5.7.4 — RAIOS DE VERDADE NO VAZIO — 2026-11-01

**"Não quero apenas partículas, quero animação de verdade."** Feito: os raios do
vazio agora são **GEOMETRIA 3D desenhada no mundo**, não nuvem de ponto.

- `client/RaiosVazioFx.java` (novo): 14 raios vivos ao mesmo tempo, desenhados
  como **fitas 3D** (`RenderType.lightning()`) em `RenderLevelStageEvent`
  (AFTER_PARTICLES). Cada raio tem zigue-zague de 6 a 10 segmentos com
  serrilhado estável, vida de **95–260 ms** e depois **re-estala** em outra
  direção — é animação de relâmpago, não partícula.
- **Para todo lado:** azimute 360° sorteado + elevação de até ±0,75 rad (pra
  cima e pra baixo) e 3 alturas de saída (chão, corpo, cabeça). Alcance de 1,8 a
  6,4 blocos.
- **Duas passadas de cor:** capa **VERMELHA** por fora (mais larga) e núcleo
  **NEGRO-ROXO** por dentro, afinando na ponta.
- **Não atrapalha quem é o vazio:** se o cliente é o próprio vazio, ele vê
  **metade** dos raios, mais finos e transparentes (alpha 0,30 no F1 / 0,55 no
  F5) — e no F1 os raios que apontam pra câmera são encurtados. Ele precisa
  mirar.
- **Dá medo a quem vê:** para os outros jogadores os raios são grandes e
  opacos; **trovão grave** (`LIGHTNING_BOLT_THUNDER`, pitch 0,55, volume caindo
  com a distância até 32 blocos) estala a cada 4–7 s em volta do vazio.
- O pacote `Aura` agora carrega o **UUID de quem é o vazio** (e vai pra todos os
  jogadores, não só pro próprio), e `euSou` diz se o cliente é ele. Canal de
  rede **v11** (cliente 5.7.3 é recusado com aviso de versão).

### Testes
`check.py`: **685 PASS / 0 FAIL** (7 checks novos). Servidor: carrega 5.7.4
limpo (`Done (0.572s)`).

## 5.7.3 — BICHO NA TELA, MÚSICA ÚNICA, AURA DE RAIOS E SOM DO TERREMOTO — 2026-11-01

**1) O BICHO NÃO PULAVA NA TELA — eram DOIS bugs.**
- **servidor:** o tick da caixinha fazia `if (sp.isCreative() || sp.isSpectator()) continue;`
  — ou seja, **quem estava em criativo nunca levava o susto**, e é exatamente
  assim que o dono testa. Agora só o ESPECTADOR fica de fora.
- **cliente:** se UMA render do susto falhasse, `sustoOff = true` desligava o
  bicho **até reiniciar o jogo**. Agora o erro só é logado e a próxima tentativa
  acontece normalmente.
- **o susto ficou muito mais visível:** entra grande (0,45 da tela) e cresce até
  passar dos cantos, treme, tem aberração cromática (duplicata vermelha e ciano
  deslocadas) e dura **3,4 s** (era 2,6 s e ficava pequeno). Se a textura falhar,
  desenha um vulto escuro com dois olhos vermelhos — nunca fica sem susto.
- **clicar na caixa agora abre o bicho na hora** (antes só tocava música).

**2) ACABOU O "VÁRIAS MÚSICAS" NA CAIXA DO PALHAÇO — também eram dois bugs.**
- `durou % 30 == 0` era **verdade durante um segundo inteiro**, e o tick roda
  **20× por segundo** → **20 músicas tocadas juntas** a cada 30 s. Trocado por um
  instante marcado (`proximaMusica`): toca **uma vez** a cada 30 s.
- cada **clique** chamava `colocarCaixa`, que **adicionava outra caixa** na lista
  (cada uma com sua música no seu ciclo). Agora há **dedupe por posição** e uma
  trava de 4 s no som: apertar 10 vezes toca uma música só.

**3) SOM DE FUNDO DO TERREMOTO — refeito.** O antigo era 99% grave (energia
1651 na faixa 20-60 Hz contra **0,019** entre 400-2000 Hz): um zumbido que a
caixa de som do PC nem reproduz. O novo (`arte/audio_573.py`) tem **seis
camadas**: sub (peso), ronco das placas, corpo de terra (150-700 Hz), grãos,
estalos secos irregulares e um "groan" da terra cedendo — estéreo, com cada
estalo caindo num lado. Medição do novo: 474 / 92 / **118** / **22** nas faixas
18-60 / 60-150 / 150-700 / 700-2000 Hz — agora tem corpo. `rumble` 12 s (emenda
contínua) e `colapso` 75 s em **stream** (não carrega os 75 s decodificados na
RAM).

**4) AURA DO VAZIO (`/vazio ligar`) — agora é RAIOS SAINDO DO PLAYER.** O que
aparecia de "esquisito no F5" era a **fumaça desenhada na TELA** (um borrão preto
flutuando no céu). Mudou tudo:
- **no mundo:** coluna de escuridão em espiral envolvendo o corpo (dust
  roxo-escuro + LARGE_SMOKE + SQUID_INK), **raios serrilhados** que saem do
  peito e dos pés — **negros por dentro e vermelhos na ponta** — e as rachaduras
  vermelhas do chão continuam (agora 4 curtas, coadjuvantes).
- **na tela:** só o acabamento — vinheta escura nas bordas (0,42), peso escuro
  embaixo, granulação nas bordas e um piscar raro. **Zero fumaça, zero rachadura
  de tela.** No F5 você vê o personagem dentro da escuridão com os raios saindo
  dele; no F1 a visão fica limpa.

### Testes
`check.py`: **678 PASS / 0 FAIL** (9 checks novos). Servidor de teste: carrega
5.7.3 limpo ("Done"), `/bicho` em 3 comandos cria **2** caixas (o repetido não
duplica) e `/bicho limpar` zera.

## 5.7.2 — TERREMOTO NOVO, BANIDO NAS SOMBRAS E TÍTULOS — 2026-11-01

**1) SOM NOS AVISOS (o que o dono cobrou).** O CONHECIMENTO e o ALL FOR ONE
agora têm som PRÓPRIO, sintetizados aqui (44,1 kHz, estéreo, Vorbis):
- `conhecimento_aviso.ogg` — 11,5 s: sino de templo, drone grave por baixo,
  sussurros e campainhas brilhando no fim (parece "revelação", não susto);
- `allforone_aviso.ogg` — 12,5 s: boom grave + coro de vozes + estalos.
Os dois entram no PUSH do aviso (streaming, não esperam o download terminar) e
param junto com o efeito. Antes era só o som do vanilla batendo — agora tem
trilha.

**2) TERREMOTO — ABERTURA NOVA.** Saiu o flash BRANCO de 2,6 s (parecia foto
estourando) e o blur 3,5. Entrou: abertura de **1,4 s** com o chão rachando em
zigue-zague do centro pra fora, pedrisco pulando da linha da fenda, o mundo só
ESCURECENDO nas bordas (0,30 de alfa, nada de tela preta) e um tremor de 0,2 s
(blur 1,2 no máximo). A "rachadura de vidro" da tela agora entra fraca (0,42) e
curta (1,1 s) — antes ficava ~4 s e dava cara de tela de celular quebrada.

**3) TERREMOTO — FENDAS QUEBRANDO BLOCOS.** A cada ~5 s, durante o evento,
abre uma rachadura FINA de 4–8 blocos no chão em volta de um jogador: os blocos
quebram na frente dele com a poeira do PRÓPRIO bloco e o estalo. Mesmas regras
de segurança de sempre (só terra/pedra/areia, nunca baú/forno, nunca
água/lava) e **tudo volta** no fim (`fecharFendas`). O tempo de tela preta da
queda também caiu: 5,5 s → 3,2 s e a rachadura 4,5 s → 2,8 s.

**4) BANIMENTO — SOMBRAS, NÃO VERDE.** O dono rejeitou as 90 partículas verdes
("não ficaram legais"). Agora `/terremoto sair` (e `/saida`) começam com
**poucas** sombras (3) e o servidor vai MULTIPLICANDO a cada 2 ticks — 1 no
começo, 11 por vez no fim — mais algumas almas subindo, até a pessoa
desaparecer. A camada da tela também perdeu o verde: são 3 → 26 borrões escuros
que nascem em volta do corpo, crescem, se juntam no último terço e escurecem no
máximo 78% (sem tela preta, sem lavada colorida).

**5) SISTEMA DE TÍTULOS (a print do AETHERIS).** Qualquer jogador pode ter:
- uma **etiqueta colorida** na frente do nome — `[Sagrado]`, `[Warden]`,
  `[Gelo]`, `[Fragmento]`, `[Almas]`, `[Chronos]`, `[Sombra]`, `[Divino]`,
  `[Morte]`, `[Vazio]`, `[Ferreiro]`, `[Player]` — ou qualquer palavra (fica
  cinza-claro);
- um **título pessoal** depois do nome, como o `Nephisto [O Ferreiro]`.
Aparece **no TAB, no chat e no nametag acima da cabeça**. Comandos:
```
/titulo definir <jogador> <etiqueta>        (admin)
/titulo personalizado <jogador> <texto>     (admin)
/titulo limpar <jogador>                    (admin)
/titulo lista                               (todos)
/titulo paleta                              (todos)
/titulo ver <jogador>                       (todos)
```
Salvo no mundo (`rptag_titulos`), então reiniciar não perde nada. O texto é
limpo de `§` e limitado (16/24 caracteres) pra ninguém quebrar a tela.

**6) SOMBRA DA MORTE.** Quem morre recebe o efeito 19: tentáculos subindo do
chão, halo vermelho, cinza, fios de alma e a tela escurecendo e voltando; em
volta do corpo saem tinta de lula, fumaça escura, cinza e alma.

### Testes
`check.py`: **668 PASS / 0 FAIL**. Servidor de teste com o jar novo: sobe
limpo ("Done"), `/titulo paleta` e `/titulo lista` respondem, `/terremoto
fenda` abre 39 blocos e `/terremoto fechar` devolve tudo.

## 5.7.1 — CONSERTOS E AURA DO VAZIO — 2026-11-01

**1) CONHECIMENTO não "crasha no meio" mais.** Era um bug de verdade: o desenho
do olho escrevia no índice `2N` de um array de tamanho `N*2` — estourava o array
e o `try/catch` desligava o aviso na metade (exatamente o "começa a acontecer e
não acontece mais nada"). Corrigido (`N*2+1`), e agora quando algum FX falhar o
log mostra a exceção inteira.

**2) Vídeo do tablet.** Continua a SUA animação (120 quadros, 24 fps, 5 s,
**em loop**), com dois consertos:
- **sem marca d'água**: o `delogo` tira o selo "AI" do canto superior esquerdo e
  o rodapé é cortado (onde ficava o "Dreamina AI") — o quadro é reescalado pra
  1280x720;
- **cache do decodificador limitado** (16 quadros): no loop os quadros velhos se
  acumulavam e em 2–3 voltas o jogo engasgava/caía. Também ganhou uma trava que
  reinicia o vídeo se ele ficar sem entregar quadro por mais de 4 s;
- um pouco mais claro (menos apagado) e faixas de degradê nas bordas pra
  "derreter" no painel do tablet.

**3) AURA DO VAZIO do `/vazio ligar`.** Porte do `VoidAuraDark` que o dono
mandou: rachaduras vermelho-sangue saindo do chão, 12 tentáculos (halo vermelho
+ núcleo preto) tremendo em passos, 14 estilhaços orbitando, fumaça preta atrás
e na frente, cinza subindo, piscadas e granulação de filme. Fica ligada
enquanto o deus estiver no modo vazio (`/vazio desligar` apaga).
(O bonequinho de pixel do protótipo NÃO é desenhado: no jogo o personagem é o de
verdade — o resto é igual.)

### Testes
`check.py` **657 PASS / 0 FAIL** (6 checks novos) · `javac` 0 erro · servidor
real: **5.7.1**, `Done`, 0 ERROR.

## 5.7.0 — O QUE CHEGOU DO DONO: VÍDEO NO TABLET + 3 EFEITOS NOVOS — 2026-11-01

> arquivos enviados por link (uguu): `OVsEHLOl.java` (CONHECIMENTO),
> `yJXaxPdQ.java` (saída), `HxYcxABt.java` (energia), `xZomZtNl.java`
> (ALLFORONE) e `PWaHoNqN.mp4` (fundo do tablet).

**1) VÍDEO REAL ATRÁS DO PLAYER NO TABLET.** O mp4 do dono (buraco negro,
1280x720, 24 fps, 5,09 s) entrou no lugar do placeholder: **120 quadros** em
loop, 5,3 MB, recortado "cover" no painel do personagem e levemente apagado.
O aro procedural do tablet **não é mais desenhado** quando o vídeo está
rodando (`TabletVideoFundo.ativo()`) — o vídeo já É o buraco negro, dois aros
ficava estranho.

**2) AVISO CONHECIMENTO (estilo 16)** — `ConhecimentoAvisoFx`, porte do
`AvisoConhecimento`: fundo âmbar, 15 raias de luz, **3 anéis de runas** girando
em sentidos opostos (alfabeto de 28 runas em grade 3x4), **olho grande** dourado
que abre e pisca, **12 olhinhos** nas beiradas, poeira dourada, **sussurros**
("eu sei o seu nome"), ondas de revelação e as barras **SANIDADE ×
CONHECIMENTO** (quanto mais sabe, menos sã). Mostra o texto do comando.

**3) AVISO ALLFORONE (estilo 17)** — `AllForOneAvisoFx`, porte do
`AvisoEnergia` (arquivo "oneforall"): sol pintado à mão (raios amarelos + 70
arcos brancos), **silhueta de fogo** em 5 camadas que tremem, **olhos brancos**
abrindo no rosto, faíscas que convergem pra dentro do corpo e sobem como chama,
brasas e sussurros. Texto: "AS VOZES NA SUA CABEÇA / SEMPRE FORAM UMA PESSOA."

**4) ANIMAÇÃO DE SAÍDA DOS JOGADORES (estilo 18)** — `SaidaPlayerFx`, porte do
`AnimacaoSaida`: o bonequinho 16x32 do jogador que saiu **se desfaz em chuva de
código** (feixe de varredura subindo), anéis de texto girando
(`session.close();`), **log de terminal** com [OK], barra de SESSÃO e
"DESCONECTADO / Até a próxima, <nome>." Toca **pra quem fica**, quando alguém
desconecta. Liga/desliga: **`/rp admin saidafx on|off`** (padrão LIGADO).

### Comandos
- `/rp admin avisar <jogadores> conhecimento <texto>`
- `/rp admin avisar <jogadores> allforone <texto>`
- `/rp admin saidafx on|off` (ou sem nada = mostra o estado)

### Testes
`check.py` **651 PASS / 0 FAIL** (7 checks novos) · `javac` 0 erro · servidor
real: mod **5.7.0**, `Done`, **0 ERROR** no log, `/rp admin saidafx` respondendo.

## 5.6.1 — VÍDEO ATRÁS DO PLAYER NO TABLET — 2026-11-01

> "esse video deve ficar atras do player quando ele abrir o tablet"

**Feito:** o tablet (`TabletScreen`) agora tem uma **camada de vídeo atrás do
player**. O mod não toca mp4 — o vídeo é convertido em pares de JPEG por quadro
(`NNNN_c.jpg` cor + `NNNN_a.jpg` alpha, 1280x720) e um `fx.json`, o MESMO
formato dos avisos em vídeo do mod.

- Classe nova `client/TabletVideoFundo.java`: decodifica em thread separada
  (`VideoFrameLoader`), sobe no `DynamicTexture` e desenha com recorte "cover"
  dentro da área do personagem, **levemente apagado** (0,55/0,52/0,72 a 0,85)
  pra o boneco 3D e o texto continuarem legíveis.
- **Em LOOP**, como o dono pediu: quando o vídeo chega no fim, recomeça (sem
  parar) enquanto a ficha estiver aberta — e a volta **não dá engasgo**: quem
  volta pro quadro 0 é a própria thread de decodificação
  (`VideoFrameLoader(..., loop=true)`), que já deixa os primeiros quadros
  prontos. Os avisos em vídeo continuam parando no fim (loop=false).
- Começa do quadro 0 toda vez que o tablet abre; para ao fechar
  (`onClose`/`removed`).
- Se o asset não existir, o tablet fica exatamente como era (nada quebra). Se
  `fxVideo=false` no `config/rptag/cliente.properties`, o vídeo nem tenta.
- `arte/mp4_para_fx.py`: conversor **mp4 -> o formato do mod**
  (`--fps`, `--blend normal|aditivo`, `--alpha opaco|luminancia`, `--qualidade`),
  testado com um clipe de 2 s (24 quadros 1280x720, 1,1 MB).

**Placeholder:** o pacote `assets/rptag/fx/tablet_fundo/` está no jar com um
fundo roxo de nebulosa em loop (32 quadros, 1,2 MB) só pra a camada já existir
e ser testada. **Quando o mp4 do dono chegar, é só rodar o conversor e regerar**
— nada de código muda.

### Pendente
Os 4 links do catbox (`7q9b95` CONHECIMENTO, `m0qjal` SAÍDA, `1w29s3`
ALLFORONE e `p5z3m3.mp4`) voltaram de novo com **0 byte** — o upload do catbox
falhou e ele guardou arquivo vazio. O dono vai colar o código dos `.java` aqui.

### Testes
`check.py` **643 PASS / 0 FAIL** (7 checks novos de 5.6.1) · `javac` 0 erro ·
servidor real: mod **5.6.1**, `Done (0.645s)`, **0 ERROR**.

## 5.6.0 — O CHÃO RACHA, O PLAYER CAI E FICA IMÓVEL — 2026-11-01

> "melhore mais ainda o terremoto, quero efeito de algo se rachando o player
> caindo, ficando imovel, quero que seja legal mesmo o terremoto quando os
> players ver o terremoto"

### 1. O CHÃO ABRE DE VERDADE (servidor)
No pulso travado, embaixo de cada jogador a terra **cede**:
- **cova 3×3 com 2 de profundidade** — o jogador cai dentro e fica lá;
- **4 a 6 rachaduras serpenteando** pela superfície, 5 a 10 blocos cada, seguindo
  o relevo (e poeira do bloco saindo da boca da fenda);
- quando o pulso **solta**, a terra **fecha** e cada bloco volta exatamente como
  era (e a pessoa sobe de volta pro nível do chão).

**Segurança (o evento não pode estragar build):** só entra na fenda bloco de
uma lista branca (`CAVEIS`: terra, grama, pedra, deepslate, areia, cascalho,
argila, tuff, arenito, netherrack…). **Nunca** baú, forno, bloco com dados
(`hasBlockEntity()`), líquido, nem bloco „de valor". Tudo é restaurado:
no fim do pulso, no `/terremoto parar` e por `/terremoto fechar`.

### 2. FICA IMÓVEL (agora é de verdade)
Efeito de lentidão não segurava — o jogador **pulava** pra fora do buraco.
Agora o pulso travado aplica **modificador de atributo**:
`MOVEMENT_SPEED` em `-100%` e `JUMP_STRENGTH` em `-0,42` (transitório, com id
fixo, sai no instante em que o pulso solta). No buraco de 2 blocos, sem pulo e
sem velocidade, a pessoa **não sai, não pula, não corre** — só espera.

### 3. A CENA NA TELA (cliente) — três camadas novas
- **RACHADURA** (`desenharRachadura`): a tela racha. Abre um **abismo no rodapé**
  (boca jagada crescendo pra cima, beirada iluminada = a lasca, pedaços de terra
  despencando pra dentro) e **11 rachaduras serrilhadas** sobem dele, afinando,
  com galhos, poeira na ponta e vinheta de terra. Depois **cicatriza** sozinha.
- **QUEDA** (`desenharQueda`): quem caiu na fenda vê a cena de dentro: faixa de
  terra na base, poeira subindo, tremor de impacto nos primeiros 25%, vinheta
  fechando e **blur** (atordoado no fundo do buraco). A câmera também **tomba**
  (`roll +14°`, `pitch +22°`) — dá pra ver que ele não consegue se levantar.
- **ESTOURO** (`desenharEstouro`): o tranco inicial — flash arenoso, **duas ondas
  de choque** abrindo e poeira estourando do centro, com blur na entrada.
- **Detritos**: lascas de terra passando pela tela durante todo o terremoto e um
  véu de poeira no rodapé nos pulsos travados.

### 4. Comandos novos de teste
- `/terremoto fenda` — abre uma fenda no chão onde o comando foi digitado.
- `/terremoto fenda <jogador>` — abre embaixo do jogador (trava ele).
- `/terremoto fechar` — fecha todas e **devolve os blocos**.
- `/terremoto status` agora mostra **quantos blocos estão cavados** agora.

### Testes
- `check.py`: **636 PASS / 0 FAIL** (11 checks novos de 5.6.0).
- `javac` 0 erro · servidor NeoForge 21.1.249 real: `Done (0.640s)`, mod
  **5.6.0**, `/terremoto fenda` abriu **60 blocos** numa plataforma de teste e
  `/terremoto fechar` **restaurou tudo** (grama de volta em 202,59,202),
  `/terremoto iniciar 18` + `parar` no meio: **0 ERROR/exception**.

## 5.5.0 — AVISO ENERGIA, O EXÍLIO E A SOMBRA — 2026-11-01

> "agora vai ter um novo avisar ALLFORONE" · "esse video deve ficar atras do
> player quando ele abrir o tablet" · "o exilar do vazio deve mandar para outra
> dimensao (crie uma nova dimensao para isso) um lugar tudo preto e sem nada la"
> · "o apagar deve fazer tipo uma sombra e nao gostei desse quadrado preto na
> minha tela quando uso os comandos" · "os efeitos ao ligar devem circular o
> player, e tira os que estao na cabeca dele" · avisares "energia" e
> "conhecimento" + "nova animacao de saida"

### 1. AVISO ENERGIA (estilo 15) — `/rp admin avisar @a energia <texto>`
Porte fiel do protótipo do dono (`ywqg53.java`, `AvisoEnergiaOP`): a Energia de
Ordem Paranormal (caos, transformação, inconsistência).
- **Núcleo vivo:** 4 formas com "lobos" que mudam de frequência sozinhos — a
  forma **nunca** fica igual.
- **Raios** recursivos (glow violeta + núcleo branco) saindo do núcleo, raios
  atravessando a tela (a "anarquia") e 180 faíscas em caos sendo sugadas.
- **Glitch:** separação ciano/magenta no texto, fatias deslocadas, tremor e
  clarões; texto que **decodifica** de glifos (`#%&01<>`) para letras.
- **HUD:** círculos tracejados de cobre, marcas radiais e medidor de
  **ENTROPIA** (55%..93%, avisa "CRÍTICO" quando passa de 90%).
- **Moldura** de cobre com faísca correndo pela borda, scanlines, vinheta e
  ligar de CRT (a tela abre numa fresta). Dura 11 s.
- Ligado em: `ClientGuiLayers`, `ClientEffects` (estilo 15), `/rp admin avisar`
  (`energia`), `/rpfx teste energia` e no `nomeEstilo` do feedback.

### 2. O EXÍLIO — dimensão NOVA `rptag:exilio` (tudo preto e sem nada)
`/vazio exilar <jogador>` agora manda pra uma dimensão feita só pra isso:
- tipo `rptag:exilio`: **sem céu** (`has_skylight: false`), **luz ambiente 0**,
  meia-noite fixa (`fixed_time: 18000`), efeitos do End (o mais escuro que o
  vanilla tem sem texture pack);
- gerador flat com **UM bloco de concreto preto** em y=0 — o jogador antes caía
  pra sempre em `rptag:vazio` (que não tem chão nenhum);
- **camada de cliente**: quem está lá recebe um preto macio por cima de tudo
  (0,70 de alfa + vinheta) e, de vez em quando, um "não há nada aqui" quase
  invisível — pra não parecer que o jogo travou.
- `/vazio soltar <jogador>` traz de volta pro spawn do overworld (o admin nunca
  prende ninguém pra sempre).

### 3. `/vazio apagar` virou SOMBRA (e o quadrado preto morreu)
O que aparecia na tela era literalmente `g.fill(w/2-r, h/2-r, w/2+r, h/2+r,
preto)` — um **retângulo chapado** no meio do monitor. Trocado por uma sombra
de verdade, toda em geometria com degradê (sem canto nenhum):
- **3 discos radiais** (48 fatias) — borrão liso, centro escuro, borda que some;
- **silhueta** de quem está sendo apagado se desfazendo de baixo pra cima;
- **16 braços de sombra** entrando pelas bordas (curvas que respiram);
- **aro fino roxo** na borda da sombra (pra ler como sombra, não como buraco) e
  vinheta que fecha a tela. Vale pro `apagar` e pro `exilar`.

### 4. A AURA DO VAZIO agora CIRCULA o player
O dono mandou um print com as chamas **azuis em cima da cabeça**. Era o
`SCULK_SOUL` nascendo em `y + 2,1`. **Removido.** No lugar, **três anéis
horizontais** girando em sentidos diferentes:
- peito (y+1,05): 10 de tinta preta, girando rápido no sentido horário;
- cintura (y+0,62): 6 de fumaça escura, girando ao contrário;
- tornozelo (y+0,12): 6 de cinza, girando devagar.
Nada acima da cabeça, nada empilhado: o efeito **roda em volta** do personagem.

### 5. Pendente (arquivos que vieram vazios do catbox)
`1w29s3.java` (ALLFORONE), `m0qjal.java` (nova animação de saída),
`7q9b95.java` (CONHECIMENTO) e `p5z3m3.mp4` (vídeo atrás do player no tablet)
baixaram com **0 byte** (o catbox respondeu HTTP 200 com `content-length: 0`).
Nada foi implementado no chute — é só reenviar que eu porto igual ao ENERGIA.

### Testes
- `check.py`: **625 PASS / 0 FAIL** (15 checks novos de 5.5.0).
- `javac`: 0 erro. Servidor NeoForge 21.1.249 real: `Done (1.142s)`, mod
  carregado como **5.5.0**, dimensão `rptag:exilio` carregada (forceload ok),
  chão de concreto preto confirmado em y=0 com ar acima, `/vazio soltar` e
  `/vazio exilar` parseando, `/terremoto iniciar 15` + `parar` sem erro,
  **0 ERROR/exception** no log.

## 5.4.1 — TERREMOTO SEM CHIADO DE QUEIMADO E MAIS LONGO — 2026-11-01

> "no som do terremoto deve continuar por mais tempo, e esse som de coisa se
> queimando nao gostei, a fumaça ficou esquisita e nao combina"

### 1. O chiado de queimado NÃO era o áudio do dono
O mp3 dele (5pts9o) é praticamente só grave (28–150 Hz) — medido: 0,5% de
energia entre 900 e 5000 Hz. **O chiado era MEU**: o `rumble_terremoto`
sintetizado usava **ruído branco** (`ruido()`) pro corpo dos baques, e ruído
branco soa exatamente como fritura. Medição do defeito:

| arquivo (5.4.0) | grave 28–150 Hz | agudo 900–5k | razão (fritura) |
|---|---|---|---|
| `rumble_terremoto` | 0,039 | 0,034 | **87,6%** |
| `terremoto_colapso` (refeito) | 0,292 | 0,000 | **0,0%** |

Conserto: helper **`ruido_marrom()`** (1/f, passa-banda 30–180 Hz) — o corpo
dos impactos agora é terra, não óleo quente. Rumble final passa-baixa de 4ª
ordem em 160 Hz. O colapso novo tem passa-baixa em 200 Hz no fim, como rede.

### 2. O áudio do terra cedendo agora dura **75 s** (era 14,7 s)
`terremoto_longo()`: pega o áudio do dono, tira o chiado, e monta 75 s com
pedaços de 6,5 s em pontos de entrada diferentes, crossfade de 1,1 s e
variação de afinação por `resample_poly` (sem alias). Por cima, sub-grave que
"respira" (34 Hz ± 6) e baques distantes. E o servidor **repete** o áudio se o
evento durar mais que isso (`COLAPSO_MS = 75000`).

### 3. A fumaça saiu
`CAMPFIRE_COSY_SMOKE` **removido** — eram aquelas bolas brancas flutuando
(cara de bolha de sabão / queimado). No lugar, poeira do **próprio chão**:
`FALLING_DUST` com o bloco embaixo do jogador, `BLOCK` (pedrisco) só no pulso
travado e `ASH` bem pouca. Sem partícula nenhuma quando o bloco embaixo é ar.
Na tela, a "poeira" da borda virou pó fino (risco de 1 px a 26 de alfa) em vez
de quadradinhos marrons soltos.

### Testes
- `check.py`: **604 PASS / 0 FAIL** (8 checks novos de 5.4.1) · `validar.py`: 40 ok.
- Medição do áudio: fritura **0,0%** no colapso e no rumble; duração 75,0 s.
- Servidor 21.1.249: `Done`, `/terremoto iniciar 20` → LIGADO, `/terremoto parar`
  no meio → DESLIGADO, **0 erro**.
- Jar: **93.295.612 B**.

## 5.4.0 — EVENTO NEPTUNE: TERREMOTO, VAZIO, DEUS E O BICHO — 2026-10-31

> "preciso que os players fiquem estunados e lentos algumas vezes, aí podem
> correr e tudo tremendo... efeito de blur estilo espaço que combine com o
> evento... o terremoto vem de terras se colapsando"

Tudo num lugar só: `NeptuneEventos` (servidor), `NeptuneFx` (cliente),
`NeptuneCommands` (`/terremoto`, `/vazio`, `/bicho`, `/neptune`) e
`NeptunePackets` (os 5 pacotes S2C). Canal de rede subiu pra **v9**.

### 1. TERREMOTO — `/terremoto iniciar [segundos]`
- **O áudio do dono toca na hora** (`terremoto_colapso.ogg`, 14,7 s) pra todo mundo;
  depois o **rumble sintetizado** (8 s) entra em loop enquanto durar.
- **Câmera tremendo** com duas vozes (balanço grave do chão + tranco rápido dos escombros).
- **PULSOS de estuncado** (o pedido literal): **6 s travado** (Lentidão III + Fadiga +
  blur real + visão dupla) e **4 s livre** — "aí podem correr e tudo tremendo".
- **Blur de espaço**: nebulosa girando por cima (blend aditivo) + `processBlurEffect`
  (o mesmo blur do menu de pausa) no pulso travado, mais poeira e rachaduras.
- Termina sozinho ou por `/terremoto parar` (o desligamento que os admins pediram).

### 2. SAÍDA DO SERVIDOR — `/terremoto sair [jogador|todos]`
Player por player: animação **verde** (brilho central, pixels subindo, vinheta verde),
aviso no chat e **kick em 4 s** — "o universo se corrigiu".

### 3. DEUS DO VAZIO — `/vazio ligar | desligar`
- **Aura preta** de partículas (ink/smoke/sculk) girando em volta do corpo.
- **Trilha sonora própria** (o áudio do dono, 39,3 s) que **toca quando ele anda** e
  corta 2,5 s depois de parar.
- **Medo** (efeito + tremedeira de tela) em quem chega a 14 blocos.
- **Controle do destino**: `/vazio apagar <jogador>` → `---- vazio --- apagou "X" da
  existência` (+ som bizarro do dono e nebulosa na tela de todos);
  `/vazio exilar <jogador>` → **dimensão nova `rptag:vazio`** (data pack própria,
  céu do End, luz 0,02, chão vazio, teleporta pra y=120).

### 4. O BICHO — `/bicho aqui` ou `/bicho <x> <y> <z>`
- Nasce uma **CAIXA DE MÚSICA** no mundo (bloco `rptag:caixa_bicho`, arlequim).
- Ela toca **36 s** de caixinha de criança que **vai desafinando** (sintetizada) —
  "o som deve ficar um bom tempinho, para o player ir atrás".
- Quem chega a **3 blocos**: a caixa arrebenta, o **berro** toca e o **bicho PULA NA
  TELA** (face em tela cheia, flash branco/vermelho, tremor) — 2,6 s.

### Áudios
| arquivo | origem | uso |
|---|---|---|
| `terremoto_colapso.ogg` | **do dono** (5pts9o.mp3) | colapso |
| `vazio_apagar.ogg` | **do dono** (h36ma4.mp3) | apagar da existência |
| `trilha_deus.ogg` | **do dono** (z0r07u.mp3) | trilha do deus |
| `caixa_musica.ogg` | sintetizada | a isca do bicho |
| `bicho_grito.ogg` | sintetizada | o susto |
| `rumble_terremoto.ogg` | sintetizada | tremor de fundo |

`arte/audio_eventos.py` refaz todos; `arte/previa_neptune.py` desenha o que o jogador vê.

### Testes
- `check.py`: **603 PASS / 0 FAIL** (20 checks novos de 5.4.0) · `validar.py`: 40 ok.
- Servidor 21.1.249: `Done`, `/terremoto iniciar 12` → LIGADO e **desligou sozinho**,
  `/bicho` colocou a caixa, dimensão `rptag:vazio` carregou (`execute in`), **0 erro**.
- Jar: **93.565.061 B**.

## 5.3.1 — O ITEM NO INVENTÁRIO (o bug do display em pixels) — 2026-10-31

*"as mascaras melhoraram mas ainda estao bugadas no inv"* (print: a máscara saindo do slot)

### A causa (achada no bytecode, não no chute)
O `display` do JSON estava escrito em **PIXELS** ("48", "16"). No 1.21.1 o
`ItemTransform$Deserializer` faz outra coisa:

```java
translation.mul(0.0625F);                              // 1/16 de BLOCO
translation.set(clamp(x,-5,5), clamp(y,-5,5), ...);    // teto de 5 BLOCOS
scale.set(clamp(x,-4,4), clamp(y,-4,4), ...);          // multiplicador direto, teto 4
```

- `"translation": [0, 48, 0]` → **3 BLOCOS** = 48 px de GUI: o item subia e saía do slot;
- `"scale": [16,16,16]` → batia no **teto de 4**: ícone 4x maior que o slot.

Medido com as classes do jogo (`ItemTransform.Deserializer` de verdade, ver
`previa/mascara_531_inventario.png`): o item ocupava **26,6 x 42,4 px** num slot
de 16 px, com o centro **3 blocos acima**.

### O segundo problema (mesmo sintoma)
O espaço do item é o **cubo 0..1** (o `ItemRenderer` faz `translate(-0.5,-0.5,-0.5)`
antes de chamar o `BEWLR`). A nossa malha mora em **px da cabeça** (centro em
y = −4, z = −5,65), então mesmo com o `display` certo ela apareceria deslocada
pra cima/esquerda. Agora o `RenderItemMascara` calcula o centro da bbox e leva
em cima do (8, 8, 8): `pose.translate(offX, offY, offZ)`.

### Resultado (medido com o ItemTransform do jogo)
| contexto | antes | **agora** |
|---|---|---|
| gui (slot de 16 px) | 26,6 x 42,4 px, centro 3 blocos acima | **10,0 x 13,9 px, centro (0, 0)** |
| ground | — | 3,6 x 4,2 px |
| 3ª pessoa | — | 3,3 x 5,3 px |
| 1ª pessoa | — | 4,5 x 5,0 px |

### Prévia
`previa/mascara_531_inventario.png` — o antes (item saindo do slot, enorme) x o
depois (dentro do slot), desenhado com `arte/previa_gui_item.py`, que aplica a
MESMA conta do jogo (`* 0.0625`, `translate(-0.5)`, `scale(1/16)`).

### Testes
- `check.py`: **584 PASS / 0 FAIL** (6 checks novos de 5.3.1, um deles trava
  `translation <= 5` / `scale <= 4` pra isso não voltar).
- Servidor 21.1.249: `Done`, item invocado, **0 erro**.
- Jar: **91.361.089 B**.

## 5.3.0 — A MÁSCARA AGORA É O MODELO DO DONO (.bbmodel) — 2026-10-31

*"usa esse modelo para a mascara"* (mandou o `stone_mask.bbmodel` do Blockbench)

### O que entrou
O modelo do dono tem **116 cubos** com a textura `pedra.png` (128×128, pedra em
blocos) e **85 cubos girados em Y** — é uma máscara que curva em volta do rosto.
Substituiu a malha do GLB escaneado (8.014 triângulos irregulares), que era a
origem da "cara feia": casca irregular e textura suja.

| | GLB escaneado | **.bbmodel do dono** |
|---|---|---|
| geometria | 8.014 triângulos irregulares | **+1.392 triângulos em blocos** |
| textura | foto 512² (escaneada) | **pedra 128² desenhada** |
| pasta do jar | 769 KB de malha | **134 KB** |

### O conversor (`arte/importar_bbmodel.py`)
1. lê os cubos (`from`/`to`), a rotação em Y em volta de `origin` e o UV de cada face;
2. monta os 4 cantos de cada face como 2 triângulos (a UV segue a ordem dos cantos,
   então nada sai girado nem espelhado);
3. **espelha Y** (Blockbench é +Y pra cima, a cabeça do Minecraft é +Y pra baixo);
4. encaixa na cabeça: altura 8,4 · largura 7,2 · profundidade **comprimida** para
   2,2 (o modelo tem 5,7 de fundura; tudo precisa ficar em z < −4,5, senão a
   camada do chapéu da skin cobre);
5. grava `geo/mascara_bb.bin` + `textures/entity/mascara_bb.png`.

### Também mudou
- **camada e item usam a MESMA malha** (`mascara_bb.bin`) — o item é a máscara, sem sprite.
- A malha antiga do GLB e a textura de 512² saíram do jar (‑950 KB).
- Jar: **91.360.789 B** (era 91.993.088).

### Testes
- `check.py`: **578 PASS / 0 FAIL** · `arte/validar.py`: 40 ok.
- Servidor NeoForge 21.1.249: `Done (0.809s)`, máscara invocada, **0 erro**.
- `previa/mascara_530_vestida.png`: 5 ângulos, vestida, com a camada do chapéu.

## 5.2.2 — A MÁSCARA ESTAVA ESCONDIDA ATRÁS DA SKIN (e o item invertido) — 2026-10-31

*"a mascara esta ficando invertida, a mascara ainda está feia, resolva isso ai"*

### 1. O "ovoide" era a máscara atrás da camada do chapéu
Medindo vértice por vértice o `geo/mascara_glb.bin`: **62,9% da máscara estava
atrás da camada do chapéu** da skin (`PlayerModel` desenha esse cubo inflado em
z = −4,5; a malha ia de −5,05 a −3,75). Resultado: o jogo desenhava a pele **por
cima** e só sobrava um oval no meio da cara — exatamente a captura.

| | antes | agora |
|---|---|---|
| largura | 5,40 (68% do rosto) | **6,18** (77%) |
| altura | 7,00 | **8,00** (rosto inteiro) |
| z | −5,05..−3,75 | **−6,27..−4,78** |
| à frente do chapéu (z < −4,5) | **37,1%** | **100%** |

Escala **uniforme**: encaixa a altura e a largura segue a proporção do modelo.
(o primeiro teste esticou em X e a cara virou um prato.)

### 2. O item "invertido" era transformação dupla
No 5.2.1 eu apliquei a transformação do crânio (`translate(0.5,0,0.5)` +
`scale(-1,-1,1)`) **e** o `display` do crânio. As duas juntas jogavam a malha
para fora do quadro e espelhavam. Agora o item usa **a mesma escala 1/16** da
camada e o `display` do JSON em **pixels** (valores ×16).

### 3. O que NÃO se deve fazer (tentado e descartado)
Recalcular as normais "para fora" **piora tudo**: a máscara tem casca de fora e
de dentro (com aba fechando o volume), então ~50% das normais apontam para
dentro de propósito. A média por vértice cancela frente e verso e o relevo
(olhos, boca) vira borrão. As normais do GLB estão certas — prova em
`previa/diag_normais.png`. Trocar o *winding* sem trocar as UVs junto embaralha
a textura. O material é `noCull` (dupla face): o sentido dos vértices não importa.

### Testes
- `check.py`: **571 PASS / 0 FAIL** (6 checks novos).
- `arte/ajustar_mascara.py` (novo): refaz o `.bin` no lugar certo do rosto.
- `arte/previa_mascara_vestida.py`: antes/depois em 4 ângulos **com a camada do
  chapéu** e pele clara (com pele preta a silhueta some e a prévia mente).
- Servidor NeoForge 21.1.249: `Done (0.965s)`, máscara invocada, **0 erro**.

## 5.2.1 — A MÁSCARA 16× MAIOR (agora assentada no rosto) e o ITEM NA MÃO — 2026-10-31

*"a mascara ainda está bugada, e por algum motivo esta muito feia na mao, resolve isso"*

### 1. A máscara gigante: o espaço do `ModelPart` é em BLOCOS, não em pixels
Prova por bytecode (`javap` no jar do 1.21.1):

```
ModelPart.translateAndRotate:  ldc 16.0f / fdiv  (x, y e z do pivô)
ModelPart$Polygon.<init>:      divide cada vértice por 16
```

Ou seja: **depois** de `head.translateAndRotate` o espaço está em **blocos**
(a cabeça vai de −0,5 a +0,5). A malha do `.bin` está em **pixels do modelo**
(x ±2,7 · y −7,45..−0,45 · z −5,05..−3,75). Sem conversão ela era desenhada
**16× maior e 7,45 blocos acima da cabeça** — era exatamente a máscara gigante
flutuando na captura. Correção: `pose.scale(1/16)` logo depois do
`translateAndRotate`, nada mais.

### 2. O item na mão: parei de inventar número
Os translates/scales de cada contexto (`case GUI`, `THIRD_PERSON_…`) eram
chutes. Agora o item usa **a transformação do crânio vanilla**, copiada do
bytecode de `SkullBlockRenderer.renderSkull(null, 180, 0, …)`:

```
pose.translate(0.5f, 0f, 0.5f);
pose.scale(-1f, -1f, 1f);      // aqui, com o /16 da malha
```

E o posicionamento por contexto vem do `display` do
`minecraft:item/template_skull` (gui 30/45/0 · mão 45/45/0 escala 0.5 · chão
escala 0.5 · quadro 0/180/0). Conferido: o modelo do crânio é
`addBox(-4,-8,-4, 8,8,8)` — **o mesmo cubo da cabeça** — e a nossa máscara cai
inteira dentro dele, com o nariz saindo ~1 px pra frente. Por isso a
transformação do crânio põe a máscara exatamente onde o crânio fica.

### 3. Menos 769 KB e uma malha só
O `geo/mascara_item.bin` (a mesma máscara normalizada, só para o item) saiu:
o item agora desenha a malha da cabeça. Jar foi de 92.334.938 para
**91.992.489 B**.

### Testes
- `check.py`: **565 PASS / 0 FAIL** (8 checks novos para esta correção).
- `arte/render_mascara_offline.py` (novo): rasteriza o MESMO `.bin` do jar e
  desenha a caixa da cabeça junto — foi ele que mostrou o 16×.
- `previa/mascara_521_vestida.png` (4 ângulos, personagem inteiro) e
  `previa/mascara_521_item.png` (GUI, mão, 1ª pessoa, chão, quadro).
- Servidor NeoForge 21.1.249: `Done (1.312s)`, `summon` da máscara ok, **0 erro**.

## 5.2.0 — TABLET NEPTUNE ENTRA NO MOD (A BRANCH INTEIRA) — 2026-10-31

*"olhe a branche do github tablet, build ela por inteira no mod, deixe bom para os
players jogarem, coloque no mod e me manda o download para eu testar"*

### O que a branch `tablet-neptune` trouxe
A branch estava parada desde 25/10 (base 4.6.0, 50 commits atrás da main) e nunca
tinha sido mergeada. Foi portada **arquivo por arquivo** para a main 5.1.0, sem
merge cru, e ficou completa:

- **Item `rptag:tablet`** (QuiralOS Tablet): modelo 3D de 15 cubos + 2 texturas,
  `stacksTo(1)`, abre a ficha com clique direito.
- **Ficha do personagem em 5 etapas**: nome (32), idade (1–999), origem (240),
  habilidades (600), história (3000) + tela de revisão.
- **Entrega automática no primeiro login**, com aviso no chat; se o inventário
  estiver cheio, avisa e `/tablet` reentrega.
- **Comandos**: `/tablet`, `/tablet ver <jogador>` e `/tablet liberar <jogador>`
  (permissão 2), todos listados em `/rp ajuda`.
- **Trava do `/persona`**: depois de confirmar, `criar|idade|desc|off` ficam
  bloqueados até um admin liberar; `/persona` e `/persona ver` continuam livres.
- **Persistência** no save do mundo (`TabletSheets`), com descarte seguro de
  entrada corrompida (nunca derruba o servidor ao carregar).
- **Sincronização** com orçamento de 20 updates por 20 ticks, retry de 250 ms
  para erro temporário (não vira conflito) e revisão otimista de verdade.

### O que foi corrigido para "ficar bom pros players"
1. **Layout à prova de escala de GUI.** A conta antiga produzia largura negativa
   em telas pequenas (escala 4 num 1280×720 = 320×180 de GUI). Agora abaixo de
   430 px a coluna do personagem some e o formulário usa a tela inteira; nada de
   valor negativo, campo sempre com no mínimo 96 px.
2. **Status não invade mais o campo.** O texto de estado era desenhado em cima da
   borda do campo (visto na prévia antes de empacotar). Agora existe uma faixa
   reservada de 26 px entre o campo e os botões.
3. **"Usar ficha do servidor"** saiu da linha extra e foi para a barra de baixo,
   no lugar do "Continuar" (que fica desativado no conflito) — fim do atropelo.
4. **Abertura curta**: 1,1 s (era 2,5 s) e **qualquer clique pula** a tela
   QuiralOS.
5. **Descoberta**: tooltip no item ("Clique direito para abrir sua ficha") e
   mensagem no chat na primeira entrega.
6. **`/persona` mostra a ficha confirmada** (origem, habilidades, história) além
   de nome/idade.
7. **Blindagem**: pacote e fila do tablet no cliente em `try/catch` — payload
   estranho é ignorado com log, não derruba o jogo.

### Rede
Protocolo **v7 → v8**. Cliente e servidor precisam do **mesmo jar** 5.2.0.

### Testes
- `check.py`: **557 PASS / 0 FAIL** (23 checks novos; os 12 do bloco 4.9.0
  "o tablet saiu" foram invertidos pelo pedido 23).
- `verificacao/TesteTablet.java`: **76/76** asserts (limites, legado com §,
  revisão/conflito, NBT, bytebuf, orçamento, fluxo do cliente, retry, reload).
- `TesteLayout`: **570 asserts / 57 combinações** de tela (320×240 até 4K).
- Servidor real (NeoForge 21.1.249): `Done (0.771s)`, item registrado,
  `/tablet` nos comandos e em `/rp ajuda`, **0 exception/ERROR** no log.

### Pendências honestas
Não dá para abrir a tela pelo sandbox (sem cliente gráfico): a composição foi
conferida pela geometria real do layout (`previa/tablet_520_layout.png`), mas a
aprovação visual final é do dono no jogo. Também ficou de fora, por não haver
Emotecraft na lista do servidor, a animação por pergunta.

## 5.1.0 — MÁSCARA DE PÉ, ENCOSTADA NO ROSTO E ITEM 3D DE VERDADE — 2026-10-31

*"a mascara está longe do rosto, ficando de cabeça para baixo; use o 3d que te
enviei para ser o icone tambem"*

### 1. O eixo estava invertido (a máscara de cabeça para baixo)
O bytecode do vanilla não deixa dúvida:

```
HumanoidModel.createMesh: head -> addBox(-4, -8, -4, 8, 8, 8)  @ PartPose.offset(0,0,0)
```

Ou seja, dentro de `head.translateAndRotate(pose)`: **x −4..4, y −8..0, z −4..4**.
**+Y aponta para BAIXO** (queixo em y = 0, topo do crânio em y = −8) e o rosto
fica em z = −4. Minha conversão antiga assumia +Y pra cima — resultado: máscara
de cabeça para baixo. Agora o importador **espelha Y** e mapeia a malha numa
**caixa explícita**:

| eixo | faixa | o que significa |
|---|---|---|
| x | −2,70 .. 2,70 | largura 5,4 (cabeça tem 8) |
| y | **−7,45 .. −0,45** | de queixo a testa (lembre: y negativo é pra cima) |
| z | **−5,05 .. −3,75** | fundo 0,25 **dentro** da cabeça; nariz 1,05 pra fora |

### 2. Longe do rosto: era profundidade
O modelo tem quase tanta profundidade quanto altura. Antes ele gastava 1,6 de
bloco só entre o rosto e a parte de trás → a peça flutuava **1,25 bloco à frente**
da cara. Agora a espessura inteira é **1,3** e o fundo fica **0,25 dentro** da
cabeça.

### 3. O item agora é o modelo 3D de verdade
Os JSONs com blocos achatados (e as faixas que esticavam o rosto) foram embora.
Entrou `RenderItemMascara` — um `BlockEntityWithoutLevelRenderer` registrado via
`Item.initializeClient`/`IClientItemExtensions.getCustomRenderer()`, que desenha
**a mesma malha do GLB** (8.014 triângulos) com transformação por contexto
(GUI, mão, chão, moldura). `models/item/mascara_pedra.json` virou
`builtin/entity`, que é o que autoriza o renderer próprio.

### 4. Código
- `client/MalhaBin.java` (novo): leitor único do `.bin` (big-endian, planar).
- `client/RenderItemMascara.java` (novo): desenha o item.
- `CamadaMascara`: passa a desenhar direto do `MalhaBin`; a malha procedural
  continua como reserva se o `.bin` faltar.
- `geo/mascara_item.bin` (novo): a mesma malha normalizada em cubo unitário.
- Saíram: `textures/item/mascara_face.png`, `arte/gerar_item_mascara.py` e a
  prévia do item achatado.

**Prova:** `previa/mascara_511.png` — em cima a máscara **com a caixa da cabeça**
do jogo em escala (4 ângulos); embaixo a malha do item (frente, 3/4, lado, atrás).
Ambas lidas pelo `MalhaBin` do próprio mod.
**Qualidade:** `check.py` **534 PASS / 0 FAIL** · `arte/validar.py` 40 ok ·
servidor `Done (0.856s)`, 0 erros · jar **92.235.638 B**.

## 5.0.1 — ITEM NA MÃO E MÁSCARA NO ROSTO ACERTADOS — 2026-10-31

*"muda o icone na minha mao, ta muito feia na mao do player, diminua o scale da
mascara para caber na mao e no rosto do player, esta ficando longe no rosto"*

### Item na mão
- **O ícone agora é o ROSTO DO MODELO.** `arte/gerar_item_mascara.py` rasteriza
  a vista frontal da malha do GLB (192 px), dá nitidez suave e usa como textura
  (`item/mascara_face.png`, 128×128 **RGBA**). Antes era uma arte gerada por
  código, sem relação com o modelo.
- **Recorte por alfa (`render_type: minecraft:cutout`).** A silhueta do item é a
  do próprio modelo: fora dela é transparente. Antes eram faixas empilhadas e
  cada uma esticava uma fatia do rosto → virava "bandana"/tijolo.
- **Só frente e verso**: as faces laterais da chapa eram retangulares e, vistas
  de 3/4, apareciam como uma **moldura** em volta do rosto — era a parte "muito
  feia na mão".
- **Tamanho**: 8,9 unidades de item de altura (0,56 bloco) e `display` com
  escala **0,38** no terceiro pessoal → **0,21 bloco na mão**. Antes eram 14,8
  unidades × 0,72 = **0,92 bloco**, ou seja, a peça era maior que o próprio braço.

### Máscara no rosto
- **ALTURA 8,6 → 7,3**: a máscara passava mais de **1 bloco** por cima da cabeça
  (a cabeça tem 8 de altura). Agora fica de queixo a testa (7,3) e 5,2 de largura.
- **Z_TRAS −4,12 → −3,75**: o fundo da máscara agora entra **0,25 dentro da
  cabeça** (o rosto da cabeça fica em z = −4). Antes ficava 0,12 **pra fora**,
  e daí a sensação de "flutuando longe do rosto".

### Armadilhas anotadas
- `Image.paste` com máscara **compõe sobre o fundo e o alfa se perde** — a
  textura precisa ser montada em numpy (RGBA direto), senão o jogo desenha o
  quadrado inteiro.
- UV **esticado por faixa** sempre distorce o rosto: o certo é recorte por alfa.
- Git/workspace resetou de novo (6ª vez): `.git` voltou pra `437bb48`, `/var/tmp`
  zerado, `run/libraries` vazio → `git fetch`+`reset --hard`, `bash toolchain.sh`
  e `java -jar nf-installer.jar --installServer .`.

**Prova:** `previa/como_ficou_501.png` (item em 3 ângulos, item na mão com o
braço em escala, e a máscara na cabeça em 4 ângulos).
**Qualidade:** `check.py` **530 PASS / 0 FAIL** · `arte/validar.py` 40 ok · servidor
`Done (1.039s)`, 0 erros · jar **91.903.262 B**.

## 5.0.0 — A MÁSCARA AGORA É O MODELO DE VERDADE (GLB) — 2026-10-31

O dono mandou `cqb9ib.glb` e pediu: *"veja se consegue colocar essa mascara"*.
A máscara desenhada por código (malha 29×25 gerada em Java) saiu de cena e
entrou o **modelo de verdade**: **5.864 vértices / 8.014 triângulos**, com a
textura de pedra 2048×2048 dele.

### Como o GLB entra no Minecraft (o Minecraft não lê .glb)
Não dá para jogar o arquivo direto: o jogo não conhece glTF. O caminho foi
**converter**:

1. `arte/importar_glb.py` lê o `.glb` (header + JSON + BIN), aplica a **cadeia
   de matrizes dos nodes** (o Sketchfab exporta com eixo trocado) e leva a
   malha pro **espaço da cabeça** do Minecraft (cubo de 8³, rosto em z = -4):
   - escala pela **altura** → 8,6 (a cabeça tem 8, então sobra um fio pra fora);
   - **alarga 18% em X** (o modelo é estreito: daria 5,0 de largura);
   - **achata em Z pra 72%** (senão o nariz sairia ~3 blocos pra frente);
   - encosta o fundo (a parte aberta) em z = -4,12 — um fio à frente do rosto
     da cabeça pra não dar z-fighting.
2. Textura baseColor 2048² → **512²** em `textures/entity/mascara_glb.png`.
3. A malha vira `assets/rptag/geo/mascara_glb.bin` (**769 KB**, 24.042 vértices
   já expandidos em triângulos) e o `CamadaMascara` lê no primeiro render.

### Duas armadilhas que custaram tempo
- **byte order:** `struct.pack('<ii')` (little-endian) → o `DataInputStream` do
  Java lê **big-endian** → a versão chegava como `16777216` e a máscara caía na
  procedural em silêncio. Agora é `'>ii'` / `'>f4'`.
- **layout:** gravar **intercalado** (x,y,z por vértice) e o Java ler em **blocos
  planares** (todas as X, depois todas as Y…) **embaralha a malha inteira** — o
  bbox saía `[-6.39 .. 4.18]` nos três eixos. Agora é planar.

### Segurança
- A malha procedural **continua no código** como reserva: se o `.bin` faltar, o
  cabeçalho estiver estranho ou a leitura estourar, o log avisa e a máscara
  gerada entra no lugar. **O jogador nunca fica sem máscara e o jogo não cai.**
- `MAXV` subiu de 20.000 para 26.000 (a malha do GLB tem 24.042 vértices).

**Prova:** `previa/mascara_glb_java.png` — os 8.014 triângulos **lidos pelo
Java** e rasterizados. `previa/mascara_glb.png` — o modelo com a cabeça do
jogador junto, pra conferir encaixe.
**Qualidade:** `check.py` **528 PASS / 0 FAIL** · servidor `Done (0.691s)`, 0 erros · jar **91.890.420 B**.

## 4.9.9 — AVISO REAL: "Decreto da Coroa" — 2026-10-31

O dono mandou o FX novo (`s5fylu.java`) e pediu: *"atualize o real"*. O estilo
`real` deixou de ser "A Corte do Faraó" (friso de hieróglifos, disco solar,
obelisco, placa) e virou **"Decreto da Coroa"**:

- **coroa imperial** com capa de veludo carmesim, borda de **arminho**, aro de
  ouro com rubi/safiras/esmeraldas e pérolas, dois **arcos** com pérolas,
  **orbe e cruz** no topo; desce do alto com ar de cerimônia;
- **faixa de cetim** carmesim com rabos de andorinha, bordas de ouro e arminho,
  onde a **mensagem é carimbada em ouro**, letra por letra;
- **fios dourados** do aviso original com pontas quadradas e **flores-de-lis**;
- **louros** dourados crescem sob a faixa; aura, raios lentos e poeira de ouro.

### O que tive que ajustar no arquivo dele (senão não entrava no mod)

1. `package com.exemplo.rptag.client` → `dev.rptag.client`.
2. **Não tinha `parar()`** — o mod chama em dois lugares (aviso novo e saída do
   mundo). Sem ele não compila.
3. `iniciar(String)` de 1 argumento → `iniciar(String titulo, String mensagem)`,
   como todos os outros 12 FX do mod.
4. **Título × mensagem:** no arquivo original a mensagem ia na faixa e o título
   não existia. Como a regra da casa é *mensagem sempre EMBAIXO do título*,
   o **título ("AVISO")** ganhou uma linha pequena em maiúsculas espaçadas
   **acima da faixa**, e a coroa subiu (de `cy-32k` para `cy-44k`) pra abrir
   espaço — senão o título batia no arminho da faixa.
5. **Blindagem:** `render` virou um invólucro `try/catch` que chama
   `desenhar(...)`; em falha ele **corta o FX** e restaura o blend/depth.
6. **Bug de ritmo achado no harness:** o carimbo era `0,06 s` por letra. Numa
   mensagem de 66 letras isso dá 3,9 s — a última letra aparecia **depois do FX
   terminar** (6,5 s). Agora o passo é `min(0,06 ; 2,2/nº de letras)`.
7. `p * 1.04` (double) → `p * 1.04f`.

### Harness novo: `arte/harness_fx/`
Renderiza o FX **real** fora do jogo: compila o `RealAvisoFx.java` de verdade
contra um `AvisoFxUtil` falso cujo `Buf` rasteriza os triângulos num
framebuffer. As imagens de `previa/real_decreto.png` saíram daí — não são
redesenho. (Fonte do texto é aproximada; layout é real.)

**Qualidade:** `check.py` **522 PASS / 0 FAIL** · servidor NeoForge
`Done (0.735s)`, 0 erros · jar **91.232.930 B**.

## 4.9.8 — O BUG QUE DERRUBAVA O JOGO + ITEM 3D NA MÃO — 2026-10-31

*"minecraft simplesmente nao carrega mais"* / *"está bugado o item no rosto, na mão,
nao se parece com uma mascara"*

### 1. A causa do jogo não carregar (achada de verdade)
O bloco `static { construirMalha(); }` estava colocado **antes** das tabelas
`V_LARG`/`W_LARG` no arquivo da classe. Em Java a inicialização estática segue a
**ordem do texto**: `largura()` lia `V_LARG == null` →
`NullPointerException` na inicialização da classe → o jogo travava no carregando.
Na 4.9.7 a blindagem escondeu o erro (o jogo abria, mas **sem máscara**), e foi
por isso que ela apareceu "quadrada"/sem volume.

- A montagem agora é **preguiçosa**: `garantirMalha()` roda na primeira vez que a
  máscara é desenhada, dentro de `try/catch`, e **nenhum** `static { }` chama
  `construirMalha()`.
- Como o erro passou batido: eu testava só no servidor dedicado, que **não
  carrega classes de cliente**. Agora existe um harness que carrega
  `CamadaMascara` com um stub de `RPTagMod` e despeja os vértices; o render de
  conferência é feito **com os vértices que o Java emite**
  (`previa/mascara_java.png`).

### 2. O item virou modelo 3D (antes era sprite)
`models/item/mascara_pedra.json` era `item/generated` — um desenho chato na mão
e no inventário. Agora é **modelo com `elements`** (13 blocos): 7 faixas que
formam a silhueta de escudo, nariz saltando pra frente e borda (rim). Tem
`display` próprio pra mão, primeira pessoa, chão e GUI.
Textura nova `textures/item/mascara_pedra_item.png` (64×64: cara + pedra +
borda). Quem gera: `arte/gerar_item_mascara.py` → `previa/mascara_item.png`.

### 3. Relevo da máscara reforçado (agora aparece)
O relevo existia mas era fraco pro tamanho da peça. Escalado: nariz **+1,25**
dorso / +0,45 ponte / +0,40 asas, órbitas **−1,10**, testa em Y +0,55,
sobrancelha +0,48, maçãs +0,45, focinho +0,55, boca −0,55. No render de lado o
nariz e as órbitas agora aparecem **na silhueta**.

**Qualidade:** `check.py` **519 PASS / 0 FAIL** · servidor NeoForge
`Done (0.750s)` · armor stand + item_display com a máscara e `coracao_canon`
via RCON · jar **91.230.537 B**.

## 4.9.7 — BLINDADO (o mod não pode derrubar o jogo) + CORAÇÃO ANTIGO DE VERDADE — 2026-10-31

*"minecraft simplesmente nao carrega mais apos eu colocar a ultima versao"*

### Correção de rota (duas coisas que estavam erradas na 4.9.6)
1. **O coração NÃO tinha voltado ao antigo.** O comando que restauraria o arquivo
   falhou (`git show 66aa265` → eixo `.git` já regredido) e o `check.py` da 4.9.6
   foi editado na árvore regredida e sobrescrito pelo `git reset` — então ele
   validava a malha nova com nome de check antigo. Agora `CanonCoracaoRenderer` é
   o **voxel da 4.4** (confirmado: `MASCARA`/`SOLIDO`/`NY = 13`, 630 triângulos,
   sem `contornoY`) e há check que **proíbe** o preview da malha de voltar.
2. **Nada garantia que um erro da máscara não derrubasse o jogo.** O teste que eu
   vinha fazendo era no **servidor dedicado**, que não carrega classes de cliente.

### Blindagem (é isto que resolve "não carrega mais")
- `CamadaMascara`: a montagem da malha no `static` está em **try/catch** — se
  falhar em alguma máquina, o log guarda a causa e o jogo abre sem a máscara.
- `CamadaMascara.render`: **try/catch** com `popPose()` garantido — erro de
  render não vira crash.
- `CiberpsicoseAvisoFx`: o áudio novo toca dentro de **try/catch**.
- Regra nova desta versão em diante: **classe de cliente também é testada**
  (carregamos `CamadaMascara` e `CanonCoracaoRenderer` num JVM, ver WALKTHROUGH).

### O que continua
Máscara no estilo do modelo do Sketchfab (silhueta de escudo, testa em Y,
órbitas em fenda, focinho, presas), volume fechado, áudio do dono no
CIBERPSICOSE com o vidro quebrando mantido.

**Qualidade:** `check.py` **509 PASS / 0 FAIL** · servidor NeoForge
`Done (1.032s)` · jar **91.225.972 B**.

## 4.9.6 — MÁSCARA NO ESTILO DO MODELO DO SKETCHFAB + CORAÇÃO ANTIGO DE VOLTA — 2026-10-30

*"melhore algo assim [Sketchfab: MASCARA DE PIEDRA JOJO] ... quero o coraçao de lore antigo"*

### Máscara: agora é a do modelo de referência
A 4.9.5 tinha volume fechado, mas a cara ainda era "ovo com traços". Peguei o
render em 1920×1080 do modelo do Sketchfab (guerrerogrd, 1.483 triângulos) e
refiz os traços à mão, ponto a ponto, no mesmo esquema:

- **Silhueta de ESCUDO** — tabela `V_LARG`/`W_LARG`: ponta fina no queixo
  (meia-largura 0,70), largo e quase reto no meio do rosto (3,04) e topo ainda
  largo (2,86→2,55). Acabou o ovo; agora é o formato de escudo do modelo.
- **Testa em Y** — duas cristas salientes (`+0,22`) que descem para o meio.
- **Sobrancelha em V** brava (`+0,30`), logo acima das órbitas.
- **Órbitas em fenda** — fundas (`−0,62`), finas e inclinadas, canto de fora
  sobe; na textura são pretas com um risco de esclera.
- **Nariz** com ponte (`+0,30`), dorso (`+0,72`) e asas.
- **Focinho** saltando para frente (`+0,34`), **boca funda** (`−0,34`) com as
  **duas presas**, queixo projetado e borda de baixo recuada.
- **Textura**: pedra mais escura e contrastada, rachaduras saindo do centro,
  **hachuras de manga** nas laterais e **borda escura** fina na silhueta (como o
  contorno preto do modelo). Quem gera: `arte/previa_mascara3d.py`.

### Coração de lore: voltou o antigo
`CanonCoracaoRenderer` voltou ao **sólido voxel da 4.4** (silhueta 15×13 inflada
em 13 fatias, só faces expostas, sombreamento por face, giro por relógio e
batida tum-tum). A malha esculpida da 4.9.5 foi removida junto com o
`arte/previa_coracao3d.py`.

**Qualidade:** `check.py` **512 PASS / 0 FAIL** · JUnit **42/42** · servidor NeoForge `Done (1.933s)` · jar **91.225.946 B**.

## 4.9.5 — MÁSCARA CONSERTADA (VOLUME FECHADO) + CORAÇÃO ESCULPIDO + SOM NOVO — 2026-10-30

*"a mascara ficou muito bugada no rosto corrija isso ... coloque esse som no aviso
cyberpsicose, deixe o som do vidro quebrando ainda e continue melhorando a mascara 3d
quero que faça uma modelagem igual vez para o coraçao de lore, uma mascara 100% 3d"*

### 1. O bug da máscara (a causa era boba e grave)
As constantes de altura estavam **invertidas**: `Y_BASE = +0,62` e
`Y_TOPO = -8,55`. Em espaço de modelo o **+Y é para cima** e a cabeça vai de -4 a
+4 — então a máscara saía **de cabeça para baixo e pendurada no peito**, com a
cara do player aparecendo por cima. O render de conferência "compensava" o erro
com um `+3,95` no Y e por isso **parecia** certo lá. Agora:
`Y_BASE = -4,35` (queixo) e `Y_TOPO = +4,15` (testa), e o preview desenha a
**caixa da cabeça (8³) junto** — esse erro não passa mais.

### 2. Máscara 100% 3D (volume fechado)
- **Placa de rosto**: a frente é quase plana e só o extremo (`|u| > 0,88`)
  enrola para trás — formato de máscara, não de ovo.
- **Casca externa** com relevo esculpido (nariz +0,95, dorso, asas, órbitas
  -0,52, maçãs, boca, queixo) + **casca interna** deslocada 0,62 + **aba (rim)**
  ligando as bordas: a máscara tem **grossura** e é fechada.
- Malha **29×25** nas duas cascas (~2.900 triângulos), normais por diferença
  finita; montada UMA vez no `static`.
- **Textura própria** `textures/entity/mascara_pedra.png` (256×256) pintada no
  espírito da referência do JoJo: pedra clara rachada, órbitas fundas com
  esclera, nariz largo com narinas, boca escura com **duas presas**.
  Quem gera: `arte/previa_mascara3d.py`.

### 3. Coração de lore: de voxel para malha esculpida
`CanonCoracaoRenderer` deixou de ser sólido voxel (13 fatias infladas — de lado
virava tábua). Agora é **malha**: contorno clássico do coração
(`x = 16 sen³t`, `y = 13cos t − 5cos2t − 2cos3t − cos4t`), cada ponto puxado ao
centro por `s` e espessura `√(1 − s²)` → dois lóbulos, ponta em V e dobra do
meio, **de qualquer ângulo**. 56×13 × 2 faces (~2.800 triângulos), normais por
diferença finita, cor assada (rosa → vinho + luz + especular no lóbulo esquerdo),
giro por relógio de parede e batida tum-tum mantidos.
Preview: `arte/previa_coracao3d.py` → `previa/coracao3d.png`.

### 4. Som do dono no aviso CIBERPSICOSE
- `mkqz2x.mp3` (4,05 s) → `assets/rptag/sounds/ciberpsicose_aviso.ogg` (Vorbis,
  44,1 kHz, estéreo, normalizado e com fade de 20 ms).
- Registrado (`SOM_CIBERPSICOSE`) e tocado **uma vez** no primeiro quadro do
  aviso (flag `somTocou`). No modo `reduzido` não toca.
- **O vidro quebrando continua**: 1,7×0,30 nos surtos e 0,6×1,0 no colapso,
  mais os demais efeitos — a trilha entrou *por cima*, não no lugar.

**Qualidade:** `check.py` **505 PASS / 0 FAIL** · JUnit **42/42** · servidor
NeoForge `Done (0.993s)` · armor stand com a máscara e `coracao_canon` via RCON ·
jar **91.231.921 B**.

## 4.9.4 — MÁSCARA DE PEDRA: MODELO 3D REFEITO À MÃO — 2026-10-25

*"refaça esse modelo 3d na mão para ficar mais decente"*.

O modelo antigo eram **3 painéis retos** na frente do rosto — ficava chapado,
cara de papelão. Agora é uma **malha esculpida**:

- **Casca curva**: a superfície é um arco de círculo em volta do rosto
  (centro em z = -0,35, raio 4,55) — ela abraça a cabeça em vez de ficar plana.
- **Recorte da máscara**: a meia-abertura do arco muda com a altura
  (`angMax`): larga nas maçãs (~77°) e estreita no queixo e na testa (~45°) —
  é o formato do JoJo, não um retângulo.
- **Relevo esculpido** (`relevo`, tudo em gaussianas somadas): quilha central da
  pedra, **nariz comprido e pontudo** (+0,46), testa/sobrancelha saliente,
  **órbitas fundas** (-0,17), maçãs do rosto, **boca afundada** e queixo
  recuado. É o que dá a "cara" sem depender só da textura.
- **Malha 19×17** (576 triângulos) com **normal calculada por diferença
  finita** — por isso a luz do jogo bate na pedra como volume de verdade, com o
  nariz fazendo sombra.
- Render de conferência no repo: `arte/previa_mascara3d.py` (mesma matemática em
  Python, 3 ângulos, z-buffer + textura + luz) → `previa/mascara3d.png`.

**Qualidade:** `check.py` **497 PASS / 0 FAIL** · JUnit **42/42** · servidor
NeoForge `Done (0.673s)` · jar **91.148.340 B**.

## 4.9.3 — CIBERPSICOSE + MÁSCARA DE PEDRA — 2026-10-25

*"aviso de 'cyberpsicose'... quero fazer depois uma máscara de pedra, para o pessoal
usar em lore, faça o modelo baseado na máscara de pedra de jojo, faça para mim"*.

### Aviso CIBERPSICOSE (estilo 14)
- `client/CiberpsicoseAvisoFx.java` — o protótipo do dono (`catbox 57mzzd.java`)
  inteiro: religar a tela com falhas, `ALERTA: CIBERPSICOSE` corrompido
  (separação ciano/magenta), medidor de **HUMANIDADE** despencando de 100% a 23%,
  log de erros caindo pela esquerda, **COLAPSO** com o vidro estilhaçando, visão
  dupla, `SINAIS VITAIS CRÍTICOS` + ECG arrítmico, `REINICIANDO...` e o **modo
  persistente** (blocos corrompidos e rasgos nas bordas, banner amarelo com a
  mensagem) com surtos aleatórios a cada ~3 s.
- `/rp admin avisar <jogadores> ciberpsicose <texto>` · `/rpfx teste ciberpsicose`.
- Dura **20 s** (ou o tempo do pacote, se maior) — é o único aviso que "fica".
- Servidor: faíscas elétricas, fumaça e brilhos ao redor do alvo
  (`RPCommands.particulasCiberpsicose`). O "aplica Náusea" do protótipo **não**
  foi ligado: mexe no jogo, não só na tela.

### Máscara de Pedra (JoJo)
- **Item** `rptag:mascara_pedra` (`MascaraPedraItem`): é item de **cabeça**
  (`EquipmentSlot.HEAD`) — clica com o botão direito pra equipar, ou
  `/item replace entity @s armor.head with rptag:mascara_pedra`.
- **Textura 64x64** (`textures/item/mascara_pedra.png`, gerada por
  `arte/gerar_mascara.py`, com seed fixo): o rosto esculpido na região 0..32
  (sobrancelha caída, olhos fundos, nariz, boca com **duas presas**, queixo),
  pedra lascada com rachaduras em 32..64, além do vão escuro e do canto.
- **Modelo 3D na cabeça** (`client/CamadaMascara`): 3 painéis — o do meio com a
  cara inteira e os dois laterais com a pedra, virados ~26° pra trás (o "casco").
  Registrado nos dois modelos de skin (clássico e slim) via
  `EntityRenderersEvent.AddLayers`; só desenha quem está com o item na cabeça e
  não desenha jogador invisível.
- Prévias: `arte/previa_mascara.py` → `previa/mascara_no_rosto.png` e
  `previa/mascara_textura.png`.

**Qualidade:** `check.py` **494 PASS / 0 FAIL** · JUnit **42/42** · servidor
NeoForge 21.1.249 `Done (1.023s)` · jar **91.147.459 B**. No servidor, o item
entrou no slot de cabeça de um armor stand sem erro (item e NBT válidos).

## 4.9.2 — AVISO "EXPURGO" — 2026-10-25

O dono mandou o protótipo (`catbox 7l0m00.java`) e pediu para adicionar: *"adicione
esse aviso 'expurgo' e me mande o mod para eu testar"*.

- **Novo estilo 13 = EXPURGO** — `/rp admin avisar <jogadores> expurgo <texto>`
  (também `/rpfx teste expurgo <texto>`, no cliente, sem servidor).
- **O desenho é o do protótipo, inteiro** (~11,8 s): a TV liga com linha de luz e
  estática, cabeçalho `● REC` + `TRANSMISSÃO DE EMERGÊNCIA` + cronômetro, o
  anúncio digitado (`ESTE NÃO É UM TESTE.` + a sua mensagem), a contagem
  5-4-3-2-1 com onda vermelha e bipe, o clarão com sirene, as luzes girando nos
  cantos, o título **HORA DO EXPURGO** caindo letra por letra com separação RGB e
  tremor, as listras de perigo, 4 máscaras sorridentes com os olhos acesos e o
  fecho `QUE DEUS NOS ABENÇOE` antes da TV colapsar numa linha.
- **Som:** o protótipo usa `AMETHYST_BLOCK_RESONATE` (sirene/bipes), `BELL_BLOCK`
  (contagem), `ENDER_DRAGON_GROWL` + `LIGHTNING_BOLT_THUNDER` + sino no slam e
  `ENDERMAN_TELEPORT` + `FIRE_EXTINGUISH` no fim — tudo no cliente, sem pacote novo.
- **Servidor:** sangue de redstone caindo, poeira de redstone, fumaça densa e
  chamas ao redor de cada alvo (`RPCommands.particulasExpurgo`, lado servidor).
- **Adaptações só de encanamento:** pacote `dev.rptag.client`, `AvisoFxUtil` do mod
  (em vez do `com.exemplo`), `parar()` para um aviso novo encerrar o anterior, e o
  trio `glit ? 6 : 0.8f` virou `glit > 0 ?` (no protótipo `glit` é `float` 0/1 e o
  ternário com `int` não compila).
- Se o texto vier vazio, o padrão é **"TODOS OS CRIMES SÃO LEGAIS DURANTE 12 HORAS"**.
- `check.py` **478 PASS / 0 FAIL** (+13 checks do expurgo, −0) · JUnit **42/42** ·
  servidor NeoForge 21.1.249 `Done (1.107s)`; RCON confirma que `expurgo` é um nó
  real do comando (pede o texto) e que palavra inventada cai no clássico.

## 4.9.1 — O DIVINO VOLTOU AO ANTIGO — 2026-10-25

*"consegue voltar para o antigo aviso divino? tava melhor, esse que o marcelo mexeu
ficou esquisito, quero o antigo estava melhor"*.

- **Causa:** na **4.6.0** (commit `32234fb`, do Marcello) o divino deixou de ser
  desenhado e passou a usar um **vídeo do Blender** como fundo —
  `assets/rptag/fx/divino/` com 288 JPEGs (26 MB), fundo cinza/branco, e o texto
  ganhou contorno escuro pra dar leitura. Era isso que estava "esquisito".
- **O que voltou** (`DivinoAvisoFx.java`, exatamente a versão anterior ao `32234fb`):
  vinheta escura, clarão de abertura, pilar de luz descendo do céu, **2 camadas de
  raios** girando em sentidos opostos, halo com anéis, **48 tiques rúnicos**,
  **2 hexagramas**, 4 losangos orbitando, **asas de luz (2×9 penas)**, faíscas em
  estrela, ornamento sob o título e o acorde de 5 sinos (`AMETHYST_BLOCK_CHIME`)
  com beacon de abertura e de fecho — tudo em ouro/creme sobre escuro.
- **A pasta `fx/divino` saiu do mod** → o jar volta ~25 MB mais leve.
- O divino **não depende mais de `/rpfx on|off`**: como o vídeo não existe mais,
  ele nunca cai no modo procedural "de emergência" — é sempre o desenho.
- `parar()` continua (um aviso novo encerra o anterior).
- `CelestiaisMarcadoresTest`: só o teste do **void** ficou (o `fx.json` do divino
  não existe mais). JUnit **42/42**.

## 4.9.0 — O TABLET SAIU (e o RP ficou automático) — 2026-10-25

Três layouts recusados depois, o dono olhou o screenshot do jogo e disse
*"isso ficou horrivel"* — junto com o pedido: **remover tudo do tablet**, do mod
E do GitHub. Feito: não sobrou uma linha.

### Removido (tablet)
- `TabletItem` (o item `rptag:tablet`), `FichaRp`, `FichaCodec`, `FichaServer`,
  `FichaCommands`, `FichaLivro`, `FichaOpenPayload`, `FichaSavePayload`.
- No cliente: `FichaScreen`, `FichaWidgets`, `FichaFundo` (a tela inteira).
- Texturas `gui/ficha_anel.png`, `ficha_buraco.png`, `ficha_nevoa.png`;
  modelo e textura do item; as 2 linhas do `lang`; `/tablet` e `/ficha`.
- Os mocks e prévias: `arte/mock_tablet.py`, `arte/mock_resumo.py`,
  `arte/gerar_texturas_ficha.py`, `arte/tablet_*.png`, `arte/resumo_*.png`.
- O mapa `fichas` do `RPWorldData` e o NBT `"Fichas"` (save antigo ignora a chave).
- **Protocolo v6 → v7**: cliente antigo é recusado com a mensagem de versão.

> O **`/persona` continua** — o personagem em texto (nome, idade, descrição,
> personalidade) nunca dependeu da tela.

### Removido (comandos)
- **`/rp admin regra`** e as três flags (`RegraPvp`, `RegraQuebrar`,
  `RegraColocar`). O dono: *"não funciona muito bem"* e *"não vai fazer sentido
  o adm ter que usar esse comando"*. Agora **vale sozinho**: quem está em RP não
  faz PvP (corpo a corpo e dano indireto), não quebra e não coloca bloco (balde
  incluso). Staff com permissão 2 continua passando.
- **`/rp admin confirmar`** e **`/rp admin cancelar`**. O lote
  (`/rp admin vida lote A,B,C tirar 1`) **aplica na hora** — `VidasAdmin` perdeu
  `guardar/pegar/limpar/Pendente`.

### Novo
- **Aviso de vida perdida**: quando a vida canônica cai, todo mundo num **raio de
  32 blocos** (mesmo mundo) lê no chat **`<nome> perdeu uma vida - tome cuidado`**.
  Quem perdeu não recebe — pra ele já aparece o aviso grande na tela.

### Qualidade
- `check.py` **463 PASS / 0 FAIL** (checks do tablet apagados; 22 novos da 4.9.0).
- **JUnit 43/43**; `javac` limpo; jar **115.810.490 B**.
- Servidor NeoForge 21.1.249: `Done (0.938s)`; RCON confirma que `/tablet`,
  `/ficha`, `/rp admin regra` e `/rp admin confirmar` não existem mais e que
  `…vida lote` devolve `✔ … em N jogador(es)` na hora.

## 4.8.5 — LAYOUT DA REFERÊNCIA (fundo galáxia + painel à direita) — 2026-10-25

O dono mandou um `CharacterSheetScreen.java` de referência e disse *"tente
assim talvez"*. Refeito o layout inteiro seguindo ele — **sem** copiar o que a
gente já tinha decidido contra (roxo pesado, blocos de 1 px).

### O que veio da referência
- **painel alto à direita** (42% da largura), do topo do capítulo até o rodapé,
  em vez do cartão centralizado.
- **cabeçalho corrido na tela toda**: marca à esquerda, sussurro à direita, fio
  desbotado, e `CAPÍTULO n / TÍTULO` + pergunta **centralizados**.
- **rótulo em cima, valor embaixo**, com fio fino entre as linhas do resumo
  (é a leitura da referência — bem mais fácil de bater o olho).
- **botão em gradiente** violeta → magenta, com fio de luz no topo.
- **sliders finos** com marcas 0/25/50/75/100 e botão branco.
- **pontinhos de progresso** no rodapé, clicáveis (pulam de capítulo).
- **personagem grande** com o Netuno atrás, proporção da tela
  (`min(w,h) * 0.22`), e o **anel do pedestal** embaixo.

### O que ficou nosso (e melhor)
- a "galáxia" da referência eram **discos desenhados com `fill`** (aquilo vira
  pixel quebrado). Aqui virou uma **textura de névoa** (`ficha_nevoa.png`)
  tingida em 3 camadas com **alfa baixo** (10% / 7% / 9%) — brilho de galáxia
  suave, e **pouco roxo**, como você pediu.
- o **buraco negro continua em textura** com filtro LINEAR (nada de anel
  serrilhado).
- o resumo **rola** quando não cabe (scissor + roda do mouse), e a **ALMA**
  aparece em **barrinhas** dentro do resumo.

### Números
490 → **497 checks** no `check.py` (0 FAIL) · 43/43 JUnit · boot limpo no
NeoForge 21.1.249 (`Done (1.075s)`).

## 4.8.4 — RESUMO EM CARTÕES (linguagem nova) — 2026-10-25

O dono pediu para **trocar a linguagem** e melhorar. A página de linhas saiu; o
resumo agora é um **cartão de apresentação**.

### O que mudou
- **nome grande** (2× a fonte) no topo do resumo, com **idade · origem** logo
  abaixo numa linha só — é a primeira coisa que o olho pega.
- o resto virou **cartões de vidro**: `OCUPAÇÃO`, `HABILIDADES`, `ALMA` e
  `HISTÓRIA`, cada um com título dourado, fio de luz em cima e cantinho aceso.
- a coluna de texto começou **mais à esquerda** (46% do painel): cada linha
  carrega mais texto, então quase tudo cabe sem rolar.
- a rolagem (quando existe) é discreta: barra fina e esfumaçado nas bordas.
- `ALMA` continua em **barrinhas**, agora dentro do cartão.

### Números
486 → **490 checks** no `check.py` (0 FAIL) · 43/43 JUnit.

## 4.8.3 — O RESUMO VIROU PÁGINA (visual) — 2026-10-24

O dono olhou a 4.8.2 e disse: *"melhore a qualidade, tá horrível"*. Ele tinha
razão — o resumo era uma lista de linhas com réguas azuis de ponta a ponta.

### O que mudou
- **seções**: `QUEM VOCÊ É` · `SUA ALMA` · `SUA HISTÓRIA`, cada uma com um
  diamante dourado e uma régua que **acende no meio e desbota nas pontas**
  (nada de linha serrilhada de ponta a ponta).
- **linhas zebradas**: cada campo alterna um fundo levíssimo, então o olho
  acompanha o nome → valor sem se perder.
- **a "Alma" virou gráfico**: as três forças da persona agora são **barrinhas**
  com o lado dominante em dourado (antes era uma linha de texto).
- **rolagem charmosa**: barra fina à direita, esfumaçado em cima e embaixo —
  o texto não é mais cortado com "…".
- **painel abre mais largo** no resumo (540 px) para caber mais texto por linha.
- **sombra atrás do painel** e **brilho de topo nos botões** (profundidade, sem
  exagero).
- **rodapé com teclas desenhadas**: `[ESC] sair` e `[ENTER] confirmar`.
- o boneco agora **acompanha o tamanho da janela** (em tela pequena ele encolhe
  junto, em vez de estourar o painel).
- nos capítulos com campo, a dica desceu um pouco e o campo ganhou respiro —
  nada fica colado.

### Números
479 → **486 checks** no `check.py` (0 FAIL) · 43/43 JUnit.

## 4.8.2 — FUNDO DO JEITO DO BALÃO, RESUMO LEGÍVEL E RESET — 2026-10-24

### Fundo: menos roxo, mais galáxia (receita do balão)
- sai a **nebulosa roxa** inteira; entra o mesmo truque do balão: **blur do
  mundo** (`renderBlurredBackground`, quando há mundo) + um véu escuro e
  **estrelas de verdade** — 84 de poeira cintilando, 18 "vivas" com cruz no
  pico e um **cometa** atravessando de vez em quando.
- o buraco negro e o disco do pedestal continuam em **textura** (4.8.1), agora
  registrados em `DynamicTexture` com filtro **LINEAR** — nada de anel
  pontilhado nem arco serrilhado.
- vinheta fecha as bordas só quando **não** deu para usar o blur.

### Tela final (RESUMO) consertada
- antes: uma linha por campo, `Alma` (6 eixos) e `História` **vazavam para fora
  do painel**.
- agora: cada campo é **rótulo + valor que quebra dentro da coluna**, e o
  resumo **rola com a roda do mouse** quando é mais alto que o painel — **nada
  mais é cortado com "…"**, nada passa da borda. A barra de rolagem aparece à
  direita e o cabeçalho avisa "role a roda para ver tudo".
- `Alma` mostra só os **3 eixos mais fortes** (ex.: *Curioso · Empático ·
  Reservado*) em vez da lista inteira.

### Fechar e painel
- **X no canto** do painel (aparece em todos os capítulos, com hover vermelho).
- painel de 55% → **91% de opacidade**: o HUD/mundo não aparece mais por cima
  do texto.

### Resetar o tablet
- `/ficha resetar` · `/ficha reset` · `/ficha limpar` — **qualquer jogador**
  apaga a própria ficha e a tela **reabre limpa**, do zero.
- `/ficha resetar <jogador>` (admin) apaga a de outra pessoa.
- atalho novo: **`/tablet abrir`**, **`/tablet resetar`** e
  **`/tablet dar <jogador>`**.
- o dono reabre a ficha concluída **editando** (o "Confirmar Personagem" não
  desaparece mais).

### Números
465 → **479 checks** no `check.py` (0 FAIL) · 43/43 JUnit · boot limpo no
NeoForge 21.1.249.

## 4.8.1 — FUNDO DO TABLET EM TEXTURA (fim dos pixels quebrados) — 2026-10-24

### O problema
Na 4.8.0 o fundo, o buraco negro e o disco do chão eram desenhados **na hora**
com retângulos de 1–2 px (`GuiGraphics.fill`). De perto aquilo virava um
mosaico: **pixels quebrados atrás do personagem** — o dono viu e mandou corrigir.

### O que mudou
- **texturas de verdade**, com gradiente e anti-aliasing:
  - `textures/gui/ficha_nebulosa.png` (1024×576) — galáxia roxa/azul com nuvens e estrelas
  - `textures/gui/ficha_buraco.png` (640×640) — buraco negro com **disco de
    acreção laranja** atravessando, halo difuso e borda quente
  - `textures/gui/ficha_anel.png` (512×256) — o anel mágico do pedestal
- desenhadas com **`blit` + filtro LINEAR** (`setFilter(true, false)`) — a borda
  fica suave em qualquer resolução/escala de GUI
- as texturas têm **desbotamento nas bordas**, então nunca aparece o "quadrado"
  da imagem na tela
- nebulosa com **parallax lento** (duas camadas) e vinheta fechando as bordas
- o personagem continua no pedestal, agora com o disco laranja passando atrás

### Reproduzível
`arte/gerar_texturas_ficha.py` gera as três texturas (numpy + PIL): quem quiser
mudar cor, brilho ou inclinação do disco mexe nos parâmetros e roda de novo.

### Números
458 → **465 checks** no `check.py` (0 FAIL) · 43/43 JUnit · boot limpo no
NeoForge 21.1.249 (`Done (0.884s)!`).

## 4.8.0 — O TABLET DA FICHA (2026-10-24)

### O que entrou
O **Tablet da Lore** (`rptag:tablet`) — todo jogador recebe um ao entrar. Clique
com o **direito** abre a ficha; o tablet **fica com ele**, então dá para reabrir
e editar quando quiser. Shift + direito mostra a ficha no chat.

A criação é **por partes**, uma pergunta por tela, no visual das referências do
dono (galáxia + buraco negro de Netuno atrás do personagem):

| # | capítulo | pergunta |
|---|----------|----------|
| 1 | NOME | Como o mundo vai te chamar? |
| 2 | IDADE | Quantas primaveras viveu? |
| 3 | ORIGEM | De onde você veio? |
| 4 | OCUPAÇÃO | O que você fazia antes de chegar aqui? |
| 5 | HABILIDADES | O que você sabe fazer bem? |
| 6 | HISTÓRIA | Conte um pouco da sua história |
| 7 | PERSONALIDADE | O que move a sua alma? (6 barras) |
| 8 | RESUMO | Está tudo correto? → **Confirmar Personagem** |

- **não existe pergunta de raça** — a de "de onde veio" ocupa esse lugar
- barras bipolares 0–100: Prudente↔Corajoso, Pragmático↔Empático,
  Espontâneo↔Disciplinado, Tradicional↔Curioso, Reservado↔Sociável,
  Contente↔Ambicioso (com ímã no centro)
- quadradinhos de progresso (dá para clicar), `ESC · Sair` / `ENTER · Continuar`
- transição animada entre capítulos e o **boneco do próprio jogador** girando no
  pedestal, com o buraco negro atrás e um disco mágico no chão
- o personagem reage **a cada pergunta** (aceno de braço + giro da cabeça)
- **salva a cada capítulo**: fechar no meio não perde nada

### Integra com o que já existia
Ao confirmar, **nome + idade + (ocupação · origem)** viram a `Persona` — então o
nome do personagem aparece no nametag, no chat e na TAB sem comando nenhum, e o
jogador entra em RP automaticamente.

### Comandos
`/ficha` (abre) · `ver <jogador>` · `livro` · `dar <jogador>` · `reset` ·
`lista` · `editar` (só admin quando é de outro).

### Técnico
- fichas guardadas no mundo (`RPWorldData`, NBT `Fichas`), uma por UUID
- protocolo sobe para **v6** com `rptag:ficha_abrir` (S2C) e `rptag:ficha_salvar` (C2S)
- fundo, buraco negro, disco e botões são **procedurais** (quads) — nada de
  textura pesada; o jar só ganhou a textura 16×16 do item

## 4.7.8 — A TRILHA AGORA É O ÁUDIO QUE O DONO MANDOU (2026-10-24)

### O que entrou
O dono mandou um MP3 de **60 s** (`ff7211.mp3`, 256 kbps) e pediu para ser
**o** som do aviso egípcio. O aviso dura 7,5 s, então foi preciso achar o
trecho certo:

- análise de energia (`arte/analisa_audio.py`): a faixa tem build-ups em
  33,5 s e o **drop mais forte em 35,0 s** (RMS salta de −16,8 para −12,8 dB)
- corte **33,95 s → 41,45 s** (7,5 s), com o **impacto caindo em 1,05 s** —
  exatamente no clarão da animação, quando o olho abre
- fade de entrada 0,12 s (sem estalo), fade de saída 0,6 s (o aviso termina
  limpo), normalizado a **−1,2 dBFS** de pico
- resultado: `assets/rptag/sounds/egipcio_aviso.ogg`, 74 KB, mono 44,1 kHz

### Reproduzível
- `arte/fontes/egipcio_origem.mp3` — o MP3 original guardado no repo
- `arte/importar_audio.py` — corta qualquer áudio para qualquer aviso
  (`--inicio --dur --id --pico --fade-in --fade-out`)
- `arte/analisa_audio.py` — mostra a energia por 0,5 s, os drops e o **melhor
  trecho** de N segundos (já casando um impacto no 1º segundo)

> A trilha sintetizada da 4.7.7 (`arte/audio_egipcio.py`) continua no repo como
> alternativa: é só rodar de novo para voltar a ela.

## 4.7.7 — TRILHA NOVA DO EGÍPCIO (alaúde, ney e tambor) (2026-10-24)

### O problema
O som do aviso egípcio era um **arpejo de note block** + som de **areia
quebrando** (SAND_BREAK) — parecia caixinha de música e não combinava com a
animação nova de 7,5 s.

### A trilha (feita do zero, sincronizada com a imagem)
`arte/audio_egipcio.py` sintetiza a faixa — sem sample de fora, tudo em código:

| instrumento | como | onde entra |
|---|---|---|
| **ney** (flauta de cana) | seno + 3º harmônico + ruído de sopro, vibrato | a melodia inteira |
| **qanun/alaúde** | Karplus-Strong (corda pulsada) | acordes no cartucho e no final |
| **tambor de moldura** | "doum" (grave) + "tek" (borda) | **nas batidas da animação** (1,6 / 2,8 / 4,0 / 5,2 / 6,4) |
| **sistro** | FM metálico | nos anéis de hieróglifos girando |
| **coro** | 3 vozes com formante de "o" | do cartucho até o fim |
| **impacto** | sub + estrondo + metal | **no clarão (1,0 s)** e no 2º golpe (2,45 s) |

- Escala **HIJAZ em Ré** (2ª menor + 3ª maior) — o "som de Egito" de verdade.
- Reverb por família de instrumento (o templo tem cauda, a percussão fica seca).
- Pico −0,9 dBFS, RMS −17 dBFS, 7,6 s, ogg Vorbis 104 KB.

### No mod
- `SoundEvent` `rptag:egipcio_aviso` + `sounds.json`.
- `EgipcioAvisoFx.sons()` dispara **a trilha** uma vez; os note blocks antigos
  ficam só como **reserva** (modo reduzido / se o ogg não tocar).
- `pararTrilha()` corta o som na hora quando um aviso novo interrompe ou o
  jogador sai do mundo — nada de trilha tocando por cima do próximo aviso.

## 4.7.6 — O AVISO EGÍPCIO AGORA É A *SUA* ANIMAÇÃO (2026-10-24)

### O que mudou
O dono mandou a animação do aviso egípcio num HTML de canvas (`draw(t, msg, red)`,
7,5 s). Ela **substituiu** os 144 quadros do Blender.

- **180 quadros** (7,5 s a 24 fps), 1280x720, com alfa (fade de entrada e saída).
- Renderizada **quadro a quadro** fora do jogo (`arte/render_canvas.js`, Chrome
  headless) chamando a mesma função `draw()` da animação — sem redesenhar nada.
- O que veio: pirâmides, olho de Hórus com asas coloridas, disco de Rá com
  raios, anéis de hieróglifos girando, areia, estrelas e o cartucho dourado.
- A **placa** continua sendo desenhada pelo Java (ela acompanha o tamanho da
  mensagem) — agora caindo exatamente no lugar do cartucho do vídeo: comparação
  entre `FxTimeline.cobrir()` e a área desenhada, em `cy = h * 0.783` com
  `k = min(w,h)/240`, então **a placa não desalinha em outra resolução**.
- O texto segue o padrão do mod: dourado com contorno, typewriter, entrada
  crescendo, brilho passando — e a mensagem **embaixo do título**.

### Ferramentas novas
- `arte/render_canvas.js` — roda uma animação de canvas em PNG quadro a quadro
  (troca o `k` para manter a escala, tira a placa feita no canvas e o brilho
  dela, porque quem desenha isso é o Java).
- `arte/importar_video.py` (4.7.5) — para MP4/GIF/WEBM, quando não houver canvas.
- `arte/prever_egipcio.py` — prévia do resultado final (vídeo + placa + texto)
  sem abrir o jogo.

## 4.7.5 — IMPORTADOR DE VÍDEO (qualquer animação vira aviso) (2026-10-24)

### Para o dono mandar a animação dele
O aviso egípcio atual é um vídeo de 144 quadros gerado no Blender. Para trocar
por **uma animação sua** (MP4, GIF, WEBM, MOV, MKV ou uma pasta de PNGs) não é
preciso redesenhar nada: `arte/importar_video.py` faz o caminho inteiro.

```
python3 arte/importar_video.py egipcio caminho/da/animacao.mp4
```

- extrai os quadros em **1280x720 / 16:9** (corte central, sem distorcer)
- respeita o fps do vídeo (padrão 24, teto configurável de quadros)
- aplica **fade de entrada e de saída** no alfa (o "entrar/sair" suave na tela)
- chama o `arte/exportar.py`, que gera `frames/NNNN_c.jpg` + `NNNN_a.jpg` + `fx.json`
- `--inicio`, `--fim`, `--fps`, `--max`, `--ajuste cover|contain|stretch`

### Correção de base
`arte/exportar.py` gravava sempre `fps: 24`; agora respeita o fps do
`marcadores.json` (vídeo importado em outro fps ficava com a duração errada).

## 4.7.4 — /rpfx teste: descobrir onde o aviso trava (2026-10-24)

### O que apareceu no chat do dono
```
[Server: Showing new subtitle for EduJogaRP]
[Server: killed 2 entities]
```
Essas **duas linhas não são do RP Tag**. A primeira é o feedback do comando
vanilla `/title <jogador> subtitle <texto>` (rodado por console/command block);
a segunda é o feedback do `/kill`. O mod só usa título/legenda vanilla num
lugar: entrada de **lorezone sem aviso configurado** (`LoreZones.show`). O
`/rp admin avisar` **nunca** manda subtitle — ele manda o FX próprio.

### A novidade
Faltava um jeito de separar "o desenho não funciona" de "o comando não chega".
Agora existe o comando **de cliente** (não precisa de admin nem de servidor):
```
/rpfx teste egipicio
/rpfx teste egipicio O Faraó observa.
/rpfx teste divino / morte / real / gelo / void / ...
```
Ele toca o aviso **direto no seu jogo**, pelo mesmo caminho de desenho do
`/rp admin avisar`. Se aparecer aqui e não aparecer no comando, o problema é
no comando/rede; se não aparecer em nenhum, o problema é no cliente (config,
vídeo, driver).
- `check.py`: **421 checks** (o `/rpfx teste` e a lista de estilos entram na conta).
- Testes: 43/43 · `javac --release 21` limpo.

## 4.7.3 — UMA ENTRADA SÓ PARA O EGÍPCIO (2026-10-23)

### O que o dono viu
No tab-complete do jogo apareciam **duas** sugestões quase iguais:
`egipcio` e `egipicio` — porque na 4.7.2 eu registrei os dois literais.

### A correção
- Fica **um literal só**: `egipicio`. A lista do comando mostra uma entrada.
- A grafia `egipcio` **continua funcionando**, mas por *fallback* dentro de
  `avisar()`: se a primeira palavra da mensagem for `egipcio`, o aviso vira o
  estilo 12 e o resto é o texto.
  ```
  /rp admin avisar @a egipcio O Faraó observa.   <- funciona igual
  /rp admin avisar @a egipicio O Faraó observa.  <- oficial
  ```
- Check novo no `check.py` (**418 checks**): nenhum estilo pode aparecer 2x no
  comando, `egipcio` **não** pode ser literal e o fallback tem de existir.
- Bug de compilação pego no caminho: `style` deixa de ser *effectively final*
  quando o fallback reescreve a variável → o log do admin usa `estiloFinal`.

### Verificação em servidor
`/rp admin avisar <jogadores> (<texto>|...|real|egipicio)` — **uma** entrada de
egípcio na árvore; `egipicio ola` e `egipcio ola` ambos parseiam.

## 4.7.2 — O AVISO EGÍPCIO AGORA FUNCIONA NO COMANDO (2026-10-23)

### O bug
O estilo 12 (`egipício`) existia no mod — arte, vídeo, som, render — mas **não
estava na árvore de comandos**. Faltava o literal `egipicio` dentro de
`/rp admin avisar`. Resultado: o Brigadier consumia a palavra "egipicio" como se
fosse o **texto** da mensagem, e o aviso saía na caixa **clássica** (ou dizia
"Unknown or incomplete command"). Não era falha de registro nem de render:
era o parser de comandos.

### A correção
- `Commands.literal("egipicio")` e `Commands.literal("egipcio")` adicionados em
  `/rp admin avisar`, logo depois do bloco do `real`, ambos chamando
  `avisar(ctx, alvos, "egipicio", texto)` — as duas grafias funcionam.
- `check.py` ganhou a checagem **4.7.2** (415 checks no total): varre o corpo de
  `avisar()` e exige um literal de comando para **todo** estilo do `switch`
  (exceto o clássico). Esse bug não passa mais despercebido.

### Como usar
```
/rp admin avisar @s egipicio O Faraó observa.
/rp admin avisar @a egipcio A maldição caiu sobre vocês.
```

### Verificação em servidor de verdade
Servidor NeoForge 21.1.249 limpo, jar 4.7.2 em `mods/`, RCON:
```
/rp admin avisar <jogadores> (<texto>|pesadelo|pesadelo2|morte|inferno|gelo|
divino|void|sangue|demoniaco|runico|real|egipicio|egipcio)
```
`415 PASS / 0 FAIL` · `javac --release 21` limpo.

## 4.7.1 — EGÍPCIO NO ESTILO DA REFERÊNCIA + REAL NOVO + VOID/GELO DE VOLTA AO ANTIGO (2026-10-22)

### "O Faraó observa" — o aviso egípcio refeito na arte do dono
A cena do Blender foi **desenhada de novo** seguindo a imagem de referência:
- fundo **azul-noite** com faixas de céu, **friso de hieróglifos** no topo e no rodapé
- **olho de Hórus grande e dourado**, sobrancelha em arco, íris em anéis
  (turquesa, azul, amarelo) com pupila preta e brilho, lágrima em curva e espiral
- **leque de raios coloridos** atrás do olho (vermelho, laranja, amarelo, turquesa,
  azul) + raios brancos finos, **círculos concêntricos** dourados e feixes de luz
- **pirâmides escuras** no rodapé, glifos caindo e brilhos de 4 pontas
- **placa do faraó** (desenhada pelo cliente, porque acompanha o tamanho da
  mensagem): retângulo de cantos arredondados, moldura dupla de ouro, cantos em
  losango, **selo quadrado à direita** e o nó embaixo — como na referência

### "A Corte do Faraó" — o aviso REAL deixou de ser um véu
O estilo 3 (`real`) era o mais simples do mod (véu azul + 2 filetes). Agora tem FX próprio:
- **friso duplo de hieróglifos** (topo e rodapé) entre filetes de ouro
- **disco solar alado** (behedeti): disco de ouro com asas em leque e as duas
  **uraeus** coroando o disco, pulsando no alto
- **colunas de papiro** com capitel trapezoidal, folhas e base nas laterais
- **areia** subindo na diagonal e **placa central** dourada com o título em letras
  de osso, contorno de lápis-lazúli e filete de ouro
- a mensagem do comando continua **abaixo do título** (regra do dono)

### Void e Gelo: o vídeo do Blender saiu do caminho
O dono preferiu **os efeitos antigos** (procedurais) desses dois avisos.
- `VoidAvisoFx` e `GeloAvisoFx` agora têm `VIDEO_PADRAO = false`: o vídeo do
  Blender **não abre mais**; o desenho procedural (idêntico ao antigo) é o que aparece
- **os assets continuam no jar**: para voltar ao vídeo basta trocar a constante
  para `true` (ou desligar com `/rpfx`… não: `/rpfx` só controla o cliente inteiro)

### Por que o egípcio "não apareceu" para o dono
O aviso **era** disparado, mas naquela máquina o vídeo não passou do carregamento
(1 s de espera) e o aviso caiu no **FX procedural escuro** — que parecia "nada".
Nesta versão o vídeo é o mesmo caminho, mas com a arte nova e quadros menores
(9,2 MB → mesma faixa), que carregam bem mais rápido; e o fallback continua
funcionando no mesmo instante.

## 4.7.0 — O OLHO DE HÓRUS EM VÍDEO (BLENDER) (2026-10-22)

### O aviso egípcio deixou de ser só procedural
O `EgipcioAvisoFx` era o **último** aviso que ainda não tinha animação renderizada.
Agora ele abre no **templo à noite**, em 3D de verdade (câmera em perspectiva):

- **Templo** com colunas de capitel de papiro, filetes de ouro e arquitrave nas laterais
- **Rá nascendo no horizonte**, atrás das pirâmides, com disco suave e raios que giram
- **Três pirâmides** em silhueta com as arestas iluminadas, **dunas** com crista dourada
- **Duas colunas de hieróglifos** (ankh, olho, água, sol, junco) rolando nas laterais —
  elas **param** quando o olho abre
- **O Olho de Hórus** é desenhado traço por traço: sobrancelha → amêndoa → íris →
  pupila → lágrima → espiral, com a **mesma geometria do código Java**, e recebe um
  clarão de ouro no instante em que fecha (2,45 s)
- **Areia** soprando na diagonal e poeira dourada subindo; a área da mensagem é
  atenuada durante a leitura para o texto não brigar com a arte

### O que continua no cliente
- O **cartucho** (a moldura oval com o nó embaixo) acompanha a largura da mensagem
  digitada, então continua sendo desenhado pelo mod
- Título dourado, mensagem **sempre abaixo do título** e a trilha de alaúde não mudam

### Comportamento
- Sem vídeo, quadro corrompido ou PC lento: o FX procedural assume **no mesmo instante**
- `fxVideo=false` ou **modo reduzido**: sempre o FX procedural
- Um aviso novo (de qualquer estilo) ou a sair do mundo encerra o vídeo anterior

### Nota
- Cinco avisos já usavam vídeo (Morte, Inferno, Gelo, Void, Divino); com o Egípcio,
  **todos os avisos com vídeo do mod** passam pelo mesmo caminho (`VideoFx`)

## 4.6.0 — VOID E DIVINO EM VÍDEO (BLENDER) (2026-10-20)

### "Queda Sem Fundo" refeita no Blender
- Mesma coreografia do efeito anterior, renderizada em **3D**: a onda de choque abre
  a tela, o **buraco negro** cresce atrás do título e o **disco de acreção inclinado**
  passa por trás dele
- **Túnel** de anéis em profundidade, **estrelas riscadas** sugadas para o centro e
  estilhaços escuros girando à deriva
- **Imersão quase total**: o jogo fica pouco visível durante o aviso e volta no fim
- O arco inferior do horizonte e do disco é atenuado enquanto a mensagem está na tela

### "Ascensão Celestial" refeita no Blender
- **Pilar de luz** até o título, **halo** com tiques, hexagrama e losangos, **duas
  coroas de raios** em sentidos opostos, **asas** de 9 penas com movimento independente,
  ornamento e faíscas
- O centro do halo fica apagado atrás das letras para manter a leitura sobre céu claro

### Comportamento
- Sem vídeo, quadro corrompido ou PC lento: o FX procedural assume **no mesmo instante**
- `fxVideo=false` ou **modo reduzido**: sempre o FX procedural
- Um aviso novo (de qualquer estilo) ou a saída do mundo encerra o vídeo anterior

### Jar
- ~95 MB com cinco vídeos (Void 15,9 MB; Divino 25,8 MB, com cor JPEG em qualidade 75
  porque o efeito cobre quase toda a tela); distribuído **só nas Releases**

### Correção de build
- Os testes automáticos da 4.5.1 voltam a compilar (dependências do jogo no classpath de teste)

### Também nesta versão: Inferno e Gelo em vídeo
(feitos antes da 4.6.0 e nunca publicados separadamente; o número 4.5.0 ficou com a versão abaixo)

#### "Fornalha" refeita no Blender
- Uma **fissura incandescente** se abre atrás do título com clarão e onda de calor,
  e se desfaz em fragmentos carbonizados
- **Fogo de verdade** subindo pelo rodapé e pelos cantos (línguas irregulares,
  núcleo amarelo, pontas vinho), **fumaça** iluminada por baixo no teto,
  **meteoros** com rastro e um **redemoinho de brasas** discreto atrás do texto
- O centro fica livre para ler a mensagem

#### "Geada Eterna" refeita no Blender
- O impacto **estilhaça a tela como vidro** (trincas e anéis quebrados)
- A **geada cresce das bordas** com veios cristalinos e "samambaias" de gelo,
  **cristais translúcidos** em aglomerados nos cantos, neve em profundidade
  e um floco discreto girando atrás do título; no fim, fragmentos se soltam

#### Leitura
- No modo vídeo o texto ganha **contorno escuro**, legível até sobre céu claro

#### Igual ao MORTE
- Sem vídeo, quadro corrompido ou PC lento → o FX antigo assume **no mesmo instante**
- `fxVideo=false` ou **modo reduzido** → sempre o FX antigo
- Um aviso novo (de qualquer estilo) encerra o aviso em vídeo anterior

#### Jar
- ~54 MB com os três vídeos (Morte 9,6 MB, Inferno 14,1 MB, Gelo 32,4 MB),
  continua **só nas Releases**

## 4.5.1 — 2026-10-21 — 🚑 CORREÇÃO: "Invalid player data" ao entrar no mundo

> **Quem está na 4.5.0 precisa atualizar.** Na 4.5.0 ninguém conseguia entrar no
> mundo: o jogo expulsava com **"Invalid player data"**. Não era o save, não era
> corrupção de mundo — era o mod, e o save está intacto.

- **O que acontecia:** criei um tipo de argumento de comando próprio
  (`ListaAlvosArgument`, para aceitar `Ana,Beto` e `@a` no `/rp admin vida`) e
  **esqueci de registrá-lo** no registry `minecraft:command_argument_type`.
  O servidor funcionava sozinho, mas no momento em que um jogador entrava ele
  montava a lista de comandos para enviar e chamava
  `ArgumentTypeInfos.byClass(...)` — que só conhece tipos registrados. Estourava
  `IllegalArgumentException: Unrecognized argument type dev.rptag.ListaAlvosArgument`
  e a conexão caía com "Invalid player data".
- **O que foi feito:**
  - `RpArgumentTypes` registra o argumento do jeito certo (mesmo padrão que o
    NeoForge usa no `ModIdArgument`): `ArgumentTypeInfos.registerByClass(...)`
    dentro de um `DeferredRegister` no `COMMAND_ARGUMENT_TYPE`.
  - O registro saiu do `<clinit>` da classe do argumento — o `DeferredHolder` do
    NeoForge resolve o registry na criação, e isso quebrava qualquer uso da classe
    fora do jogo.
  - **Rede de segurança:** `/rp admin vida` agora pergunta
    `ListaAlvosArgument.disponivel()` antes de usar o tipo custom. Se por qualquer
    motivo o registro não estiver ativo, ele cai para o `word()` do vanilla (o admin
    perde a lista com vírgula, mas **o servidor continua aceitando jogadores**).
  - Log de boot: `RP Tag: /rp admin vida usando lista_alvos (...)` — se um dia
    aparecer "usando o argumento padrao", o motivo está logo acima no log.
- **Testes que travam a regressão:** `LoginCommandsPacketTest` monta o
  `ClientboundCommandsPacket` — o objeto exato que o servidor cria no login — com e
  sem o registro, e `ArgumentoSerializavelTest` faz o mesmo com
  `ArgumentTypeInfos.unpack`. `check.py` ganhou 7 checks (agora **369**).
- **Nada do save precisa ser mexido.** Basta trocar o jar; os jogadores voltam a
  entrar normalmente.

## 4.5.0 — 2026-10-21 — 🏺 AFK, regras de RP, vidas offline e o OLHO DE HÓRUS

- **AFK (novo)** — ficar parado vira AFK **sozinho** e o comando `/afk` faz na mão.
  - Varredura a cada 5 s em cima do `getLastActionTime()` do próprio jogo (não é
    contador caseiro: dormir, abrir baú e lag não enganam).
  - **Entrar em AFK DESLIGA o RP.** Ao voltar, é só `/rp on` — a mensagem avisa.
  - Acorda ao **mover** (>1,5 cm), **virar a cabeça** (>2°), falar no chat, atacar ou
    clicar. No relog, o estado é limpo (ninguém fica preso AFK).
  - `/rp admin afk tempo <segundos>` (0 desliga; padrão 300) e `/rp admin afk status`.
    O tempo fica no mundo (`AfkTimeout`).
- **Regras em RP** — três interruptores, todos **liberados por padrão** (servidor antigo
  não muda de comportamento):
  - `/rp admin regra pvp on|off` — barra o golpe (`AttackEntityEvent`) **e** o dano
    indireto (flecha, tridente, poção, TNT) quando qualquer um dos dois está em RP.
  - `/rp admin regra quebrar on|off` · `/rp admin regra colocar on|off` (blocos **e**
    baldes de água/lava/mob).
  - `/rp admin regra` mostra o estado; `tudo bloquear|liberar` de uma vez.
  - Quem tem permissão 2 (staff) passa direto — dá pra montar cenário sem desligar a regra.
    O aviso é discreto, na barra de ação.
- **Vidas de quem está OFFLINE** — era o buraco da 4.4.0 (só achava quem estava online):
  - `/rp admin vida <nome>` agora resolve pelo cache de perfis do servidor; o estoque é
    por UUID, então muda igual. A pessoa recebe o aviso (estilo 4 + trovão) **no login**.
  - `/rp admin vida` sem argumento lista quem está abaixo de 5/5.
  - `/rp admin vida lote Ana,Beto,Caio tirar 1|dar|definir` — **não aplica na hora**:
    mostra a lista e espera `/rp admin confirmar` em até 30 s (`/rp admin cancelar` desiste).
- **Aviso estilo 12: `egipicio`** (aceita `egipcio`) — *"O Olho de Hórus"*, 6,5 s:
  - Templo à noite em papiro, **3 pirâmides** no horizonte e **o disco de Rá nascendo
    atrás delas**; areia soprando e incenso.
  - O **olho é desenhado traço por traço** (sobrancelha → amêndoa → íris → pupila →
    lágrima → espiral do canto), com poço escuro atrás pra o ouro saltar.
  - Título **AVISO** letra a letra dentro de um **cartucho** (moldura oval com o nó e a
    faixa de lápis-lazúli) e a mensagem **sempre embaixo** (`cy + 25k`).
  - Áudio próprio: arpejo de **alaúde/flauta em escala pentatônica**, olho abrindo
    (clarão + coro), sino de templo no fim.
  - No mundo, o alvo recebe areia caindo, incenso e brilho.
- `check.py` **362** (25 checks novos), JUnit **33/33** (7 novos).

## 4.4.0 — 2026-10-20 —  Coração 3D de verdade + `/rp admin vida`

- **`/rp admin vida <jogador> tirar|dar [n] | definir <n> | ver`** — mexe nas vidas
  canônicas NA HORA, sem esperar morte. É o mesmo estoque do ritual (`getCanonLives`),
  então aparece no `/rp status` e no coração do mundo.
  - `tirar` manda o aviso de morte canônica (estilo 4) + trovão pro alvo; na última
    vida entra o MEDO ABSURDO de 30 s. **Não congela** (não houve morte).
  - `dar` devolve vida (teto 5) com o aviso de "nova chance".
  - `definir` põe o total de uma vez; `ver` só mostra.
- **Coração canônico redesenhado (4.4.0):** saiu a "máscara extrudada" (fatias de 5px
  que viravam tábuas ao girar), entrou um **sólido voxel**: silhueta 15×13 inflada por
  distance transform em **13 fatias** de profundidade. Só as faces expostas são
  desenhadas (630 quads), com sombreamento por face e gradiente rosa→vinho.
- **O giro no chão** agora está documentado no código: quem gira é o BER, chamado a cada
  quadro, lendo `System.nanoTime()` — não depende de ticker nem de blockstate, então
  não trava com lag, não pula e continua de onde parou.
- Batida em dois tempos (tum-tum), flutuação suave e inclinação de 5°.
- Verificação: `verificacao/4_4_0_coracao_giro.png` (8 ângulos), `_no_chao.png` e
  `_giro.gif`. `check.py` 337, JUnit 26/26.


## 4.3.0 — 2026-10-20 —  SANGUE novo: "HEMORRAGIA"

- **`avisar` estilo 9 (sangue) refeito do zero.** O antigo ("Sangria") lembrava desenho animado:
  gotas redondas com brilho branco. Agora a tela é um corpo que **se rasga**.
- 0,3 s: a pele estica numa costura fina → **RUPTURA** (clarão, tremor, estalo) → a ferida abre
  com efeito elástico, lábios de carne irregular, estrias de músculo e tendões que **estalam**.
- O coração **acelera e afrouxa**: cada batida esguicha jatos arteriais com gravidade.
- Sangue **escorre pelo vidro** em respingos irregulares e poças; o chão enche e drena no fim.
- Capilares crescem das 4 bordas; grão de filme tira o aspecto "vetor limpo".
- Sem brilho branco em lugar nenhum (era o que deixava cartoon). Mensagem continua **embaixo** do título.
- Verificação: `verificacao/4_3_0_sangue_animacao.gif` + 4 PNGs 1080p + timeline de 6 quadros.
- `check.py`: 331 checks (4 novos).




## 4.2.0 — AVISO DE MORTE EM VÍDEO (BLENDER)  (2026-10-19)

### "Linha Reta" agora é um vídeo renderizado no Blender
- O aviso de **morte** (`/rp admin avisar <alvo> morte <msg>` e o ritual da vida
  canônica) toca um **vídeo de 6 s a 24 fps** feito no Blender: crânio de
  **voxels 3D com luz de verdade** que se forma do pó e vira cinzas, olhos em
  brasa no ritmo do coração, monitor cardíaco com brilho, túnel e borda vermelha
- **Mesma essência:** os mesmos tempos (batidas, linha reta, cinzas), os mesmos
  sons, o título **"AVISO"** com a **mensagem embaixo** e o texto que vira osso
  na linha reta — o texto continua desenhado pelo código, então a mensagem do
  comando aparece normal
- O véu avermelhado e o áudio da lore (`canon_death`) continuam iguais

### Nunca fica sem aviso
- Sem vídeo, quadro corrompido ou PC lento demais → o aviso segue no **FX
  antigo (procedural) no mesmo instante**, sem reiniciar e sem crash
- PC lento mostra o quadro mais novo pronto (pula quadros) em vez de travar

### Opção do jogador
- `config/rptag/cliente.properties` → `fxVideo=false` desliga os vídeos
  (sempre o FX antigo)
- Comando do cliente **`/rpfx status|on|off`**

### Jar
- O jar ficou com **~10 MB** (o vídeo do MORTE tem 9,6 MB) e agora fica **só nas
  Releases** — não vai mais pro repositório

## 4.1.0 — MORTE FORA DO RP NÃO CONTA + FX DE MORTE + NOVO PESADELO  (2026-10-19)

### Vida canônica: matador OFF RP não tira vida
- Se quem **matou** estava **OFF RP**, a morte é considerada **fora do RP**:
  a vítima **NÃO perde vida canônica** — sem ritual, sem aviso, sem som
- Continua contando normal: morte por **mob, queda, lava, fogo ou /kill**
  (sem matador jogador) e morte causada por **quem está ON RP**
- A checagem é feita **no instante da morte** (onde o matador é conhecido)
  e consumida no respawn — não vaza para a próxima morte
- O log do servidor registra:
  `RP Tag: <vitima> morreu para <matador> (OFF RP) — VIDA CANONICA PRESERVADA`

### Novo aviso de MORTE — "Linha Reta"
- Monitor cardíaco batendo cada vez mais devagar… até virar **linha reta**
- **Crânio em pixel art** que se forma do pó, olhos em brasa que se apagam,
  e no fim vira cinzas subindo
- Visão em túnel, tela esfriando pra cinza, borda pulsando em vermelho
  no ritmo das batidas; cinzas caindo o tempo todo
- Sino fúnebre grave + batidas + apito da linha reta
- Usa o mesmo motor dos outros avisos: título **"AVISO"** grande + a
  **mensagem embaixo** (padrão de todos os FX)
- Vale para `/rp admin avisar @a 4 <msg>` **e** para o ritual da vida
  canônica (o véu avermelhado e o áudio `canon_death` continuam)

### Novo PESADELO (estilo 1) — minimalista, "Seguido"
- Tela quase apaga; moldura vermelha fina + 2 linhas dividindo em terços
  que piscam e falham
- Mensagem **digitada** letra por letra, às vezes **corrompendo** (`#%&?/*`)
- Coração acelerando + estática sutil; **sem figuras** (terror minimalista)
- Mantém as partículas do servidor + **Escuridão 5 s**
- **O pesadelo antigo ("Seguido" com a figura pálida) saiu do lugar** —
  o código dele está salvo em `backup/` no workspace

## 4.0.0 — BARREIRA DA LOREZONE (só admin) + FAIXA VERTICAL + VARINHA  (2026-10-19)

### "Barreira" visível só para admins
- `/lorezone mostrar <id> [segundos]` → desenha na SUA tela:
  - **ciano** = arame da esfera da zona (meridianos + paralelos)
  - **amarelo** = as duas paredes da **faixa vertical** = onde o aviso dispara
  - **laranja** = linha do centro mostrando o alcance em Y
- `/lorezone esconder` tira
- **Só quem pediu vê**: o servidor manda o pacote só pro admin que executou
  (players comuns nunca recebem nada)

### Varinha de Lorezone (item de admin)
- `/lorezone varinha` → item na mão
- **Clique direito** perto de uma zona (até 256 blocos) → mostra a barreira
- **Shift + clique direito** → esconde
- Se um player comum pegar, **não funciona** (permissão 2 obrigatória)

### Faixa vertical: aviso só no nível certo (o caso da CAVERNA)
- `/lorezone vertical <id> <minY> <maxY>` → a zona só dispara entre esses Y
  - Ex.: evento na caverna em Y=-30..20 → quem estiver no topo do mundo
    (Y=120) **não recebe** o aviso; só quando descer até a caverna
- `/lorezone vertical <id> auto` → volta a valer a esfera inteira
- O `/lorezone listar` mostra `Y -30..20` nas zonas limitadas

## 3.99.0 — LOREZONE: aviso ÚNICO + música/som com TEMPO MÁXIMO  (2026-10-19)

O problema: entrando e saindo da lorezone, **toda vez** aparecia o aviso e a
música voltava a tocar sem fim. Agora:

### Aviso: UMA VEZ POR JOGADOR, PARA SEMPRE
- Entrou a primeira vez → viu o aviso → **nunca mais** (nem saindo/voltando,
  nem relogando, nem reiniciando o servidor)
- Fica gravado no mundo (`AvisosVistos` no `rptag_lorezones.dat`)
- Admin pode liberar de novo: `/lorezone avisos reset <jogador>`

### Som: toca a CADA entrada — mas com LIMITE
- O som pontual da zona toca toda vez que o jogador entra
- Para sozinho em **30 s** (nunca vira ambiência eterna), e também ao sair

### Música: TEMPO MÁXIMO sempre (nem na primeira vez é infinita)
- Toca ao entrar e **para no tempo máximo** — não recomeça enquanto o jogador
  estiver dentro
- O número do `/lorezone musica <id> intervalo <seg> <som>` agora é o
  **tempo máximo** de reprodução (10–600 s)
- `/lorezone musica <id> 1x <som>` = toca ao entrar, limite de 60 s
- **NOVO** `/lorezone musica <id> unica <som>` = toca **só na primeira entrada
  da vida** daquele jogador (e com limite de 60 s)
- Músicas custom (`@arquivo`) e por link também recebem o limite: o servidor
  manda o `maxSeg` no pacote e o **cliente para sozinho** (vigia de prazo no
  `MusicaCustom`, teto de segurança de 10 min) — mesmo se o STOP se perder

### Verificação
- Simulação da máquina de estados (3 cenários) em
  `verificacao/simula_lorezone.py`: aviso 1x, som/silêncio por limite,
  música parando no tempo máximo e voltando só na próxima entrada

## 3.98.0 — AS DUAS OPÇÕES DE PESADELO  (2026-10-19)

Agora existem **dois estilos de pesadelo** — escolha o que combinar com a cena:

| Comando | Estilo | Modelo | O que é |
|---|---|---|---|
| `/rp admin avisar @a pesadelo <msg>` | 1 | **"Seguido"** | found-footage: figura pálida se aproximando em cortes |
| `/rp admin avisar @a pesadelo2 <msg>` | 11 | **"Sono Sem Descanso"** | o modelo ANTIGO (3.96.4): túnel que respira, tentáculos com garras, 16 olhos que seguem o título, olho gigante, estática |

-  Classe nova `PesadeloAntigoAvisoFx` (camada `rptag:aviso_pesadelo2`)
-  O antigo veio do 3.96.4 **já com a mensagem embaixo** (`cy + 25k`) — padrão mantido
-  Ambos continuam com a **Escuridão de 5s** (susto extra)
-  Também funciona no `/lorezone ... aviso pesadelo2 ...` (mapa + clamp até 11)
-  Ajuda do `/rp` lista `pesadelo, pesadelo2, runico, ...`

## 3.97.0 — 8 FX CINEMATOGRÁFICOS: inferno, gelo, rúnico + pesadelo novo  (2026-10-19)

Arquivos do user integrados (catbox 0ncp46/qbupf3/xe44nh/oj68ax/nb9qgd),
adaptados ao padrão do mod: **título "AVISO" grande + mensagem EMBAIXO**
(mesma linha `cy + 25k` dos outros FX).

| Estilo | FX | O que tem |
|---|---|---|
| 1  pesadelo | **"Seguido"** (found-footage) | tela quase apaga, moldura vermelha tremendo, **figura pálida que se aproxima em cortes**, mensagem digitada, coração acelerando + passos + olhar do Enderman |
| 2  rúnico | **"Tradução Arcana"** | círculo de invocação em perspectiva (3 anéis + octagrama + 16 runas), pilar de luz, chuva rúnica, texto em língua encantada que se traduz |
| 5  inferno | **"Fornalha"** | muralha de fogo, redemoinho, meteoros, ~100 brasas, fumaça, texto de metal derretido |
| 6  gelo | **"Geada Eterna"** | tela congelando em dendritos, trinca de vidro, cristais, floco geométrico, nevasca, texto congelando |

-  Nova classe compartilhada `AvisoFxUtil` (Buf/vinheta/anel/brilho/tocar)
-  2/5/6 agora têm **trilha própria no cliente** (não tocam mais o som do servidor — sem áudio duplicado)
-  partículas no servidor: `particulasRunico` (ENCHANT+WITCH+anel END_ROD), `particulasInferno` (tornado de chamas), `particulasGelo` (nevasca+estilhaços)
-  O 4º arquivo enviado (rotulado "inferno") é na verdade um **PesadeloAvisoFx "Seguido"** — foi aplicado como o **novo pesadelo** (substitui o "Sono Sem Descanso")
-  Escuridão 5s do pesadelo continua LIGADA (susto extra pedido no 3.96.3)

## 3.96.4 — MENSAGEM NA MESMA LINHA NOS 5 FX (correção de verdade)  (2026-10-19)

-  **Achei o defeito real**: a mensagem estava embaixo em todos, mas em
  **linhas diferentes** (divino 72px, demoniaco 81px, sangue 47px,
  pesadelo 30px, void 73px abaixo do título) — daí a sensação de
  "desconexo" entre os estilos
-  **Agora os 5 usam a MESMA linha**: `cy + 25 * k` (mesma posição
  relativa ao centro da tela em todos os FX)
-  `void`: reflexo invertido puxado de 16S → 15S pra abrir espaço,
  mensagem entra logo abaixo do reflexo
-  **Verificação visual** em 1080p gerada: `verificacao/avisos_verificacao_1080p.png`
  (5 painéis com as linhas medidas) + conferência no **bytecode** do jar
  (constante `25.0f` presente nas 5 classes)
-  **BUG DO PRÓPRIO check.py corrigido**: as seções anexadas desde a
  3.95 estavam DEPOIS do `print` do resumo e **nunca executavam** — o
  placar era congelado em 259. Movidas pra antes do resumo: agora
  **273 checks rodando de verdade**

## 3.96.3 — SUSTO EXTRA no pesadelo: ESCURIDÃO 5s  (2026-10-19)

-  `pesadelo` agora aplica **Escuridão por 5 segundos** (efeito real:
  o player fica sem ver nada ao redor enquanto o aviso roda) — a pedido
  do user, **para teste**
-  Fácil de reverter: é o parâmetro `escuridao` de
  `particulasPesadelo(alvo, true)` (voltar para `false` desliga)
-  O user avisou que **prefere o modelo antigo** do pesadelo (véu +
  coração do Warden) — se o teste não agradar, o pesadelo volta ao
  estilo antigo no 3.96.4

## 3.96.2 — PADRÃO ÚNICO: MENSAGEM EMBAIXO DO AVISO ⬇ (2026-10-19)

-  **Os 5 FX agora seguem EXATAMENTE o mesmo padrão** (igual divino e
  demoniaco): título **"AVISO" grande** + **mensagem do comando logo
  abaixo** (ou nada, se não digitar mensagem)
-  `void`: a mensagem subiu de muito abaixo do reflexo para **logo
  abaixo dele**, na mesma faixa visual dos outros FX
-  `sangue` e  `pesadelo`: já estavam embaixo no 3.96.1 — **se ainda
  viu mensagem em cima, o jar antigo (3.96.0) ainda estava na pasta
  `mods` do cliente!** (o print do void mostrava exatamente o desenho
  antigo)

## 3.96.1 — MENSAGEM EMBAIXO DO AVISO ⬇ (2026-10-19)

-  **Correção reportada com print**: em `sangue` (e também em `void` e
  `pesadelo`, que tinham o mesmo defeito nos protótipos) a **mensagem
  aparecia EM CIMA do título "AVISO"**
- ⬇ Agora a mensagem fica **EMBAIXO do AVISO**, igual ao `divino` e ao
  `demoniaco`:
  - `sangue` e `pesadelo`: logo abaixo do título
  - `void`: abaixo também do **reflexo invertido** do título (para não
    sobrepor o espelho)

## 3.96.0 — QUEDA SEM FUNDO + SONO SEM DESCANSO  (2026-10-19)

-  **`void` ganhou o FX "Queda Sem Fundo"** (arquivo do autor, visual
  idêntico):
  -  Onda de choque lavanda abre a tela e o centro vira um
    **BURACO NEGRO com anel de acreção inclinado**
  -  **Túnel infinito de anéis ondulantes** (você CAI pra dentro!)
  -  **Estrelas esticadas em listras** sugadas pro centro
  -  **Estilhaços da realidade** à deriva rumo ao buraco
  -  Título em **órbita lenta com aberração cromática** + REFLEXO
    invertido embaixo
  -  No mundo: **portais sendo sugados** ao redor do alvo
-  **`pesadelo` ganhou o FX "Sono Sem Descanso"** (arquivo do autor):
  -  A visão **fecha e abre como uma respiração** (túnel que respira)
  -  **Tentáculos/mãos de sombra** rastejam das bordas COM GARRAS
  -  **16 OLHOS** abrem em tempos diferentes, piscam e **SEGUIEM o
    título** (e um olho GIGANTE vigia por cima!)
  -  **Chuvisco de estática** (barras que piscam)
  -  Título trêmulo e torto com **visão dupla** + falhas de sumiço
  -  **Caixinha de música desafinada + coração lento + grito distante**
  -  No mundo: sculk + tinta de lula + fumaça rasteira
-  Agora **5 estilos têm FX com trilha própria** (pesadelo, divino,
  void, sangue, demoniaco); sons no servidor só em morte/inferno/gelo +
  clássicos
- ℹ A Escuridão opcional do pesadelo (afetaria o gameplay) ficou
  DESLIGADA no comando

## 3.95.0 — SANGRIA + SEM LEGENDA + SEM ESTILO NOVO  (2026-10-19)

-  **`sangue` ganhou o FX "Sangria"** (arquivo do autor, visual idêntico):
  -  **IMPACTO em 0,15s**: o sangue espirra do centro (gotas esticadas
    que escorrem pela tela)
  -  **Crosta pingando do teto**: ~22 filetes com cabeça de gota,
    brilho e gravidade (com som de pingo!)
  -  **Poça de sangue** sobe do rodapé com ondas e drena no fim
  -  **Veias/capilares** crescem das 4 bordas, pulsando (vinheta viva)
  -  O texto **bate na tela, treme e PINGA**: cada letra solta filetes!
  -  **No mundo**: espirro de bloco de redstone + poeira vermelha caindo
-  **`divino` AGORA É O FX CELESTIAL** (pilar de luz, asas, hexagrama,
  letras caindo) — o estilo extra "ascensao" **foi removido** (sem estilos
  novos: melhorando os que temos!)
-  **SEM LEGENDA FIXA embaixo** — nem "AVISO DIVINO", nem "CHAMADO DO
  ABISMO": embaixo só aparece **se você digitar uma mensagem** no comando
-  Sons no servidor só onde ainda não há FX (divino/sangue têm trilha
  própria no cliente agora); partículas de mundo server-side

## 3.94.0 — FIDELIDADE TOTAL AOS PROTÓTIPOS  (2026-10-19)

-  **CORREÇÃO de fidelidade**: os FX `ascensao` e `demoniaco` agora rodam
  **exatamente como os arquivos originais** enviados pelo autor do mod:
  - O **título grande fixo é "AVISO"** (cai/sobe letra a letra, do tamanho
    do protótipo) — antes eu estava colocando a mensagem no lugar do título
    e ainda encolhendo ela (auto-ajuste removido!);
  - A **mensagem configurável do comando** agora vai no **subtítulo**, na
    posição exata em que ela fica no protótipo (ela se ajusta sozinha, como
    sempre foi no modelo);
  - Código de render **byte a byte igual ao protótipo** (só package/registro
    adaptados) — comparação `diff` limpa contra os arquivos originais.
-  check.py reescrito para auditar a fidelidade (escala fixa `k·1.9`,
  título nunca encolhe, chamada `iniciar("AVISO", mensagem)`) → **259/0**.

## 3.93.0 — DEMONÍACO = CHAMADO DO ABISMO  (2026-10-19)

- O estilo `demoniaco` agora é o **FX cinematográfico completo** (com a
  **mensagem que você quiser**, auto-ajustável):
  -  A tela escurece em **vinho e PULSA no ritmo de um coração** (som e
    visual sincronizados!)
  -  **DENTES/GARRAS negros** fecham das bordas, como mandíbulas
  -  **Fissuras de lava** racham a tela a partir do centro
  - ⬡ **Pentagrama invertido rúnico girando em brasa** coroado por
    **2 chifres incandescentes**
  -  Um **OLHO DE FENDA** se abre sob o título
  -  O texto **sobe das trevas**, treme e **glitcha** com sombras
    vermelha/laranja
-  **Trilha própria**: crescendo do dragão + batidas sincronizadas
-  **Partículas no mundo**: **PENTAGRAMA de fogo de alma** desenhado no
  chão + círculo de chamas + coluna de fumaça + respingos de lava!
- Funciona em `/rp admin avisar @a demoniaco <texto>` e `/lorezone
  aviso <id> demoniaco <seg> <texto>`

## 3.92.0 — ASCENSÃO CELESTIAL  (2026-10-19)

- **NOVO ESTILO: `ascensao`** — o FX cinematográfico completo, com a
  **mensagem que VOCÊ quiser** (ela cai letra por letra, com auto-ajuste
  pra nunca vazar da tela!):
  -  **Clarão de abertura** + vinheta escura
  -  **Pilar de luz descendo do céu**
  -  **Halo rúnico**: 3 anéis + tiques + **hexagrama girando**
  -  **ASAS DE LUZ** (2×9 penas) que se abrem e batem devagar
  -  Faíscas em estrela subindo o tempo todo
  -  **Trilha própria**: beacon + acorde de 5 sinos + dissolução
  -  **Partículas no mundo**: coroa de varetas END que se expande e
    sobe + espiral ao redor de quem recebe!
- Funciona em `/rp admin avisar @a ascensao <texto>` **e** `/lorezone
  aviso <id> ascensao <seg> <texto>` — **12 estilos no total!**
- O efeito Divino (sol e orbes) foi preservado

## 3.91.0 — Orbes de energia + Sol Sagrado  (2026-10-19)

-  **ORBES DE ENERGIA ORBITANDO** (a assinatura dos rituais de
  despertar estilo Tensura, feita com técnica própria): 6 orbes girando
  ao redor do texto no **divino** (dourados), **inferno** (brasas) e
  **demoníaco** (sangue)
-  **Feixes girando REMOVIDOS** do inferno e do demoníaco — agora só o
  divino tem (como pedido!)
-  **FIM DEFINITIVO dos anéis "oval de pontinhos"**: anéis agora são
  desenhados por varredura angular — arco liso perfeito em qualquer
  proporção
-  **demoníaco = CÍRCULO DE INVOCAÇÃO REDONDO** (centrado na tela,
  perfeitamente circular) com 3 anéis + 12 runas radiais + aura negra +
  veias + orbes de sangue
-  **divino = SOL SAGRADO**: disco radiante com degradê, anel fino e 8
  raios afilados (4 longos + 4 curtos) no lugar da "coroa" — icônico,
  limpo, bonito de se ver; o brilho da tela agora **respira** suavinho

## 3.90.0 — Refinamento geral dos avisos  (2026-10-19)

-  **FIM do anel oval de pontinhos**: os anéis agora são **linhas
  contínuas e lisas** (2px) — no demoníaco, no divino e no inferno
-  **demoniaco virou AURA NEGRA**: brilho radial vermelho-escuro +
  **leque de raios negros girando** + veias + **círculo macabro duplo e
  liso** em volta do texto + som do **Wither em camadas** (invocação +
  gemido)
-  **coroa de anjos redesenhada**: glow suave + anel núcleo brilhante +
  acento interno — halo de verdade, sem "monte de pixels"
-  **círculo de fogo do inferno redesenhado** (mesma técnica, paleta de
  brasa) + **SOM NOVO PRÓPRIO**: riser sombrio com boom grave, rumble e
  estalos de fogo (~9s) *(o link do catbox enviado deu 404 — quando
  reenviar o áudio, eu corto a parte boa e troco a faixa!)*
-  **sangue BIZARRO**: um **CORAÇÃO GIGANTE translúcido batendo** atrás
  do texto (pulsando junto com a batida do Warden) + **veias rastejando**
  dos cantos + gotas
-  **void cósmico** (temática espacial estilo Tensura): **nebulosa**
  roxa/azul com profundidade + anel cósmico liso + estrelas

## 3.89.0 — Inferno = AURA ARDENTE  (2026-10-19)

- O inferno agora é o **par maligno do divino**: mesma técnica bonita,
  paleta de fogo
-  **Brilho radial ardente** — degradê quente (laranja → brasa) nascendo
  do centro da tela
-  **Leque de fogo girando** — 14 raios afilados de brasa girando NO
  SENTIDO CONTRÁRIO do divino (os dois juntos formam um sol de dois
  hemisférios: luz e fogo)
-  **Halo triplo de brasa** — 3 anéis de fogo sobre o texto
-  Fora a muralha de dentes de fogo e os pontinhos de brasa
- Som mantido (grito de ghast + fogo rugindo)

## 3.88.0 — Divino = AURA SAGRADA  (2026-10-19)

-  Fora as **faixas verticais** e os **pontinhos** (pedido do dono!)
-  **Brilho radial contínuo**: degradê quente dourado nascendo do centro
  da tela e morrendo suavemente nas bordas (26 camadas acumuladas)
-  **Leque de luz celestial**: 14 raios afilados que **giram devagar**
  em volta do texto, cada um esvaindo do centro pra fora (estilo aura
  sagrada / mandala — a qualidade das animações dos mods famosos)
-  **Halo triplo**: 3 anéis dourados concêntricos sobre o texto, do mais
  suave ao mais brilhante
- Som celestial (coro de anjos + sino) mantido — o texto dourado agora
  flutua numa aura de verdade

## 3.87.0 — Terror refinado +  DEMONÍACO (2026-10-19)

-  **inferno** ficou BIZARRO: a tela **respira**, uma **muralha de fogo**
  cresce no rodapé e **brasas sobem** pela tela; o aviso vem com **grito de
  ghast** torcido + fogo rugindo
-  **divino** ficou CELESTIAL/ANGELICAL: **halo duplo dourado** sobre o
  texto, raios de luz brancos, **faíscas que sobem** (ascensão) e o **coro
  de anjos** (allay) + sino ressoando agudo
-  **void** ganhou o som mais FUNDO: portal em pitch 0.3 (grave demais)
  + **gemido sepulcral de wither** — o abismo responde
-  **sangue** agora PULSA: véu e gotas batem em **ritmo cardíaco duplo**
  (bate forte, bate fraco) com o **pulso mais profundo** (coração do Warden
  em pitch 0.4, mais alto)
-  **NOVO: demoniaco** — o mais macabro do mod: véu quase preto que
  **respira**, **veias de sangue** crescendo dos 4 cantos da tela, um
  **anel macabro** fechando em volta do texto e as **letras vermelhas
  ondulando** (corrompidas). Som: **invocação de wither** afinada pro fundo
- Funciona em `/rp admin avisar` E `/lorezone aviso` — 11 estilos no total!

## 3.86.0 — 10 estilos de aviso + Coração 3D bonito  (2026-10-19)

- **6 estilos de aviso NOVOS** (além dos 4 existentes — agora são 10!):
  -  **morte** — as letras retas de sangue do ritual canônico, agora
    também no comando (com som de âncora de respawn)
  -  **inferno** — brasas flutuando na tela + letras laranja-fogo
  -  **gelo** — véu glacial com cristais + letras brancas-gelo
  -  **divino** — feixes de luz dourada descendo + sino de igreja
  -  **void** — céu estrelado do End + letras lilás
  -  **sangue** — gotas escorrendo do topo da tela + pulso do Warden
- Funcionam em `/rp admin avisar` **E** em `/lorezone aviso` (todos os 10!)
- **Coração Canônico REDESENHADO**: máscara pixel-art de verdade (dois
  lóbulos com vale + ponta em V), extrudado com profundidade, gradiente
  de cor (rosa claro no topo → vinho na ponta), paredes escuras de
  volume e reflexo brilhante no lóbulo — mais um balanço sutil no giro.
  Adeus pirâmide listrada!

## 3.85.0 — Avisos cinematográficos na LORE ZONE  (2026-10-18)

- **`/lorezone aviso <id> <estilo> <segundos> <texto>`** — a zona agora
  pode disparar um **AVISO de tela** quando alguém entra, com os mesmos
  estilos do `/rp admin avisar`: **classico** (faixa, aceita códigos &),
  **pesadelo** (véu preto + sangue), **runico** (véu arcano + runas),
  **real** (azul-noite + dourado) e **morte** (letras retas de cima).
- O aviso **substitui o título clássico** da zona (o som continua!) —
  `/lorezone aviso <id> off` volta ao `título formatado` de sempre.
- Quem **já está dentro** da zona vê o aviso novo **na hora** (teste
  imediato, sem precisar sair e voltar).
- Duração de 1 a 60 segundos por zona; o `/lorezone listar` marca as
  zonas com aviso: `• id [...] — título [aviso:pesadelo]`.
- Exemplo: `/lorezone aviso floresta pesadelo 8 &4ALGO TE OBSERVA ENTRE
  AS ÁRVORES`

## 3.84.0 — O CORAÇÃO CANÔNICO 3D  (2026-10-18)

- **Item novo**: `Coração da Vida Canônica` — só admin obtém
  (`/give <player> rptag:coracao_canon`); NUNCA spawna no mundo.
- **Colocado no chão**, vira um CORAÇÃO 3D voxel GIRANDO, BATENDO e
  flutuando (desenho procedural — sem modelo externo).
- **Player encontra → SHIFT + clique direito** = recupera 1 vida! O
  coração se consome, toca o sino de ametista e aparece na tela (caixa
  de fala): "VOCÊ GANHOU UMA NOVA CHANCE — uma vida canônica foi
  restaurada (X/5)".
- Vidas cheias (5/5)? O coração fica lá — não se desperdiça.
- Indestrutível em survival (só creative remove, sem drop).

## 3.83.0 — ÚLTIMA VIDA = terror puro (2026-10-18)

- Ao perder a última vida canônica, o aviso vira **"VOCÊ PERDEU SUA
  ÚLTIMA VIDA — TOME CUIDADO"**.
- E ela leva um **MEDO ABSURDO**: 30 segundos do efeito de medo no
  máximo (véu azul-noite que ESCONDE a tela + tremedeira + ícone no
  HUD) por cima do strondo, do véu vermelho e do audio. Bizarro.

## 3.82.0 — Letras nítidas + 5 VIDAS com limite (2026-10-17)

- **Sem embaçamento**: o halo de brilho atrás das letras do banner foi
  removido — só sombra + letra sólida, nitidez total.
- **Cada player tem 5 vidas canônicas** (persistidas no mundo).
- **LIMITE anti-mortes-seguidas**: depois de perder uma vida, o player
  fica protegido por 5 minutos — morrer de novo logo em seguida NÃO
  perde mais vidas de uma vez (relogar não burla).
- O aviso mostra quantas restam: "VOCÊ PERDEU UMA VIDA — RESTAM 4" (e
  na última: "VOCÊ PERDEU SUA ÚLTIMA VIDA CANÔNICA").
- `/rp status` mostra "Vidas canônicas: X/5".

## 3.81.0 — Banner LORE ON/OFF refeito (limpo e sólido) (2026-10-17)

- Fim do efeito "patchwork" (cada letra de uma cor): agora a frase
  inteira é de UMA cor — verde vivo (ON) ou vermelho vivo (OFF) — com
  sombra escura e brilho claro uniformes.
- Faixa escura atrás mais fechada (79% de preto) com filete forte na
  cor do banner — lê em qualquer cenário.

## 3.80.0 — Vidas canônicas pra TODO MUNDO, modo do adm (2026-10-17)

- **Todos os players JÁ têm vidas canônicas** — não é efeito individual.
- **`/rp admin canon on|off`** (sem jogador): liga/desliga o MODO onde
  quem morrer PERDE UMA VIDA. Persiste no mundo.
- **Nada no chat**: o único aviso é o BANNER " LORE ON/OFF " que o
  SERVIDOR INTEIRO vê (verde/vermelho, texturizado, com faixa escura).
- Ao morrer com o modo ligado: strondo → tela avermelhada → "VOCÊ
  PERDEU UMA VIDA" (reto, texturizado) →  o audio da lore.

## 3.79.0 — Sem tremido + banner legível (2026-10-17)

- **"VOCÊ PERDEU UMA VIDA" ficou RETO** — nada de tremedeira: a
  mensagem aparece na cara da pessoa, grande e estável (a textura agora
  é só nas cores: manchas de sangue e topo irregular).
- **Banner LORE ON/OFF legível**: ganhou uma faixa escura atrás das
  duas linhas — o verde/vermelho não morre mais contra o céu.

## 3.78.0 — Ritual completo: STRONDO + tela vermelha + audio (2026-10-17)

- Quando o portador de vida canônica morre, ao renascer:
  1.  **STRONDO** — um trovão rasga o momento;
  2.  **A tela fica MEIO AVERMELHADA** (véu de sangue pulsando,
     "respirando", com fade in/out) enquanto ele fica congelado;
  3.  **O AUDIO da lore entra** ~1,5 s depois do trovão — só ele ouve;
  4.  "VOCÊ PERDEU UMA VIDA" texturizado por cima de tudo.
- Funciona com shaders (tudo na camada oficial de HUD).

## 3.77.0 — Banner LORE ON/OFF + canon mira players (2026-10-17)

- **Dois avisos novos** (como nos exemplos): ao CONCEDER a vida
  canônica, o jogador vê o banner verde **" LORE ON  /  HARD-RP ON |
  VIDAS CANNON ON "**; ao TIRAR, o vermelho **" LORE OFF  /
  HARD-RP ON | VIDAS CANNON OFF "** — texto texturizado estático (não
  se mexe), na camada oficial (funciona com shaders).
- **`/rp admin canon <jogador> on|off`**: agora funciona como o efeito
  de MEDO — o admin aplica em quem quiser (persiste até outro admin
  tirar). `/rp status` mostra se VOCÊ tem.
- Morte de portador continua igual: congela 5s + "VOCÊ PERDEU UMA
  VIDA" + O SOM.

## 3.76.0 — Vida Canônica virou EVENTO + som da lore (2026-10-17)

- **`/rp admin canon on|off`** (sem jogador!): EVENTO server-wide —
  enquanto ligado, TODO jogador que morrer perde uma vida. Persiste no
  mundo até um admin desligar.
- **Aviso simples e direto**: "VOCÊ PERDEU UMA VIDA" — com a fonte
  TEXTURIZADA da referência (camadas de sangue, letras com desvio fixo)
  mas ESTÁTICA: as letras NÃO se mexem. Sem bruxa em lugar nenhum.
- **O SOM da lore** (o mp3 do catbox) virou ogg próprio do mod e toca
  NA HORA da morte — só pra vítima, bem alto.
- Congelamento de 5 s ao renascer mantido.
- O pesadelo do /rp admin avisar continua igual (aquele é à parte).

## 3.75.0 — VIDA CANÔNICA + pesadelo remodelado (2026-10-17)

- **Vida Canônica** (`/rp admin canon <jogador> on|off`): efeito
  PERMANENTE (persiste no mundo até um admin tirar). Quando o portador
  morre, ao renascer ele fica **congelado por 5 segundos** (sem se
  mexer), recebe o aviso insano **"SEU CORAÇÃO PAROU — UM FRAGMENTO DA
  SUA VIDA FOI DEVORADO PELA BRUXA"** (estilo pesadelo, com typewriter
  e texto corrompido) e ouve a homenagem a Re:Zero: **três batidas de
  coração acelerando, o ESMAGAMENTO e a maldição baixa** — só ele ouve.
- `/rp status` agora mostra a Vida canônica.
- **Pesadelo remodelado**: o texto inteiro escorrega de lado devagar,
  cada letra ondula com fase própria (nada de senoide certinha) e as
  letras que "falham" deixam um rastro escuro (ghost) atrás.

## 3.74.0 — Texto CORROMPIDO do pesadelo (2026-10-16)

- A mensagem vermelha do pesadelo ganhou a cara "texturizada" de
  terror: as letras **flutuam numa onda lenta**, o preenchimento tem
  **3 camadas de sangue** (degradê vertical) e alguns caracteres
  **"falham"** — deslizam e escurecem por uns 2 segundos, como na
  referência ("Não há escolha a ser feita.").
- O typewriter continua (as letras corrompidas vão sendo escritas).
- Véu, bordas, faixas e som do pesadelo INTACTOS; rúnico/real/clássico
  não mudaram.

## 3.73.0 — EDIÇÃO REAL  (2026-10-16)

- **Coroas de verdade**: duas coroas douradas com joias (rubi e
  safiras) ladeiam o título "Balão de Fala".
- **Veludo real**: o painel agora é roxo-profundo em gradiente, com
  filete de ouro vivo e DIAMANTES de ouro nos 4 cantos.
- **Placas, slots e botões** em tons de veludo roxo (botões continuam
  de vidro, agora roxo-real); slider com preenchimento ouro.
- **Toque final**: a moldura do card da prévia ficou ouro-antigo e a
  galáxia ganhou estrelas de ouro (cada 7ª).

## 3.72.0 — Cinco bugs da print corrigidos (2026-10-16)

- **Dica não vaza mais**: o aviso de digitação era maior que o painel e
  passava da borda — agora todo texto de uma linha trunca com "…" (e a
  dica ficou mais curta).
- **Falas longas** na linha do chat do menu também truncam com "…".
- **X dos slots de emoji** agora fica DENTRO da caixinha (vazava 2px
  pra cima e cobria o emoji).
- **Tag "Prévia no mundo"** subiu pra sair de cima da grama.
- **Chip da moldura ativa** (direita da faixa "Moldura") ganhou contorno
  — o quadrado preto sem borda parecia glitch.

## 3.71.0 — PAINEL NÍTIDO (causa raiz do menu embaçado) (2026-10-16)

- **Achada a causa real**: o blur vanilla do jogo estava rodando DEPOIS
  do painel ser desenhado (dentro do `super.render`), borrando o menu
  inteiro — os botões escapavam porque nasciam depois do blur.
- Agora o blur acontece UMA vez, só no mundo, no começo do frame:
  **mundo embaçado atrás, painel 100% nítido na frente**.

## 3.70.0 — O fundo embaçado agora DEIXA O MUNDO APARECER (2026-10-16)

- Correção do 3.68: o véu escuro era forte demais e a galáxia era
  pintada por cima do blur — o mundo sumia. Agora, com o menu aberto no
  mundo, só um véu leve + estrelinhas discretas e o cometa: **a cena
  continua visível** (embaçada, como deve ser).
- A galáxia completa (nebulosas, via láctea) ficou pro caso sem blur
  (menu de pausa / sem mundo).
- Painel e botões INTACTOS (aprovados!).

## 3.69.0 — Aviso CLÁSSICO = Caixa de Fala typewriter (2026-10-16)

- **O campo aparece primeiro, as letras depois**: o aviso clássico (o
  padrão, sem estilo) virou uma caixa de fala embaixo da tela, igual
  "Sniper: eu vou atras de voces hein" — a caixa entra com um pop e o
  texto vai sendo ESCRITO letra por letra, com cursor piscando.
- **Nome colorido**: o que vem antes do primeiro ":" fica azul (o nome
  de quem "fala") e o resto é branco.
- **Caixa estável**: ela nasce com o tamanho final (quebra o texto em
  até 3 linhas antes de começar a digitar) — não pula enquanto escreve.
- Pesadelo, rúnico e real continuam IGUAIS (grandes, no centro).

## 3.68.0 — Menu GALAXIA com fundo embaçado (2026-10-15)

- **Fundo embaçado de verdade**: o mundo atrás do menu aparece com o
  blur vanilla do jogo (e a galáxia por cima). Sem mundo, o fundo
  continua opaco.
- **Céu de GALAXIA**: nebulosas roxas/azuis/magenta em deriva lenta,
  via láctea diagonal, estrelas cintilando (algumas com cruz de brilho)
  e um COMETA riscando o céu a cada 9 segundos.
- **Painel de vidro espacial**: gradiente noite→violeta, moldura ouro
  com filete violeta, barra dourada e cantinhos de luz.
- **Botões de vidro**: adeus cinza vanilla — todos os botões (teclado
  de adesivos incluso) e o slider agora são de vidro escuro com brilho
  dourado no hover.
- **Prévia com cenário**: o card da prévia virou um mini-cenário
  desenhado (céu estrelado, lua cheia com halo e grama) — sempre
  visível, sem depender de textura.

## 3.67.0 — O menu consertado e bonito (2026-10-15)

- **"Emojis pra tirar no lugar errado"**: o título "Emojis (clique no X
  vermelho pra tirar)" estava na MESMA linha que os slots de emoji — os
  slots o cobriam (e encostavam no botão "◐ 13s"). Título ganhou linha
  própria; a fileira de slots continua logo abaixo.
- **X da prévia agora é ONDE VOCÊ OLHA**: o X vermelho aparece quando o
  mouse passa em cima do emoji (com contorno vermelho destacando) em vez
  de X's permanentes flutuando fora do balão.
- **"Parte transparente bugada" em volta do balão**: o halo do estilo
  Brilho na prévia foi suavizado (não mancha mais o céu do card).
- **Menu mais bonito**: faixa do título agora VISÍVEL (dourada, com
  filete duplo), fundo do painel mais claro/contraste, estrelas do fundo
  discretas (menos "chuva de pontos").

## 3.66.0 — O aviso escreve de verdade + menu frufu (2026-10-15)

- **FIX do aviso "só a letra C"**: o efeito máquina de escrever do aviso
  nunca anexava os caracteres normais — agora a palavra inteira se
  escreve, letra por letra, e o aviso ainda **cresce** suavemente ao
  entrar (scale-in).
- **Som rúnico próprio**: sino de ametista (o som de maldição ficou só
  no pesadelo).
- **X vermelho nos emojis da DIREITA da prévia** (e a coluna direita não
  desaparece mais quando não há adesivo).
- **Menu**: fundo 100% opaco (fim da parte transparente), céu de
  estrelinhas, filete dourado com cantinhos no painel e título
  centralizado.
- check.py: 138 PASS / 0 FAIL.

## 3.65.0 — Typewriter vira opção + avisos blindados (2026-10-15)

- **`/balao typewriter on|off`** — a digitação automática do balão agora
  é OPÇÃO do jogador (padrão LIGADA; fica salva por jogador; o estado
  aparece na tela /balao).
- **Avisos de tela blindados**: camadas do medo e do aviso têm flags e
  proteções SEPARADAS (um erro num não desliga mais o outro) + logs de
  diagnóstico ("AVISO recebido", "desenhando aviso") pra rastrear
  qualquer falha de exibição.
- **Menu**: dica do "X vermelho" não duplica mais; o topo mostra o estado
  da digitação automática.
- check.py: 131 PASS / 0 FAIL.

## 3.64.0 — A fala que se escreve + avisos de tela com estilo (2026-10-15)

- **TYPEWRITER no balão**: toda fala agora se ESCREVE sozinha, letra por
  letra (28 ms/char, ritmo de NPC de jogo), e o balão cresce junto. Feito
  só no cliente usando o instante de nascimento que o balão já carregava.
- **AVISO com 4 ESTILOS e VÁRIOS alvos**:
  `/rp admin avisar <jogadores|@a> [classico|pesadelo|runico|real] <texto>`
  - `classico` — faixa grande (como era; aceita cores &)
  - `pesadelo` — tela afunda no preto, borda vermelho-sangue, texto de
    sangue + maldição de guardião ancião (ASSUSTADOR de verdade)
  - `runico` — véu arcano roxo, marquinhas de runa nas bordas
  - `real` — azul-noite, texto de ouro com filetes e diamantes (anúncios)
  - todos com typewriter no aviso, SEM pulso (coisa séria), camada oficial
    de HUD (funciona com shaders) e som direto no alvo
- **Menu /rp ajuda** atualizado com o novo avisar.
- check.py: 125 PASS / 0 FAIL.


Todas as mudanças notáveis deste mod são documentadas aqui.

## [3.63.0] - 2026-10-09

### Corrigido
- **"Triângulo bugado" nas prints**: a SOMBRA do balão procedural ainda
  desenhava o rabicho mesmo com `tail=false` — o pendurado cinza que
  apareceu nas molduras Clássica/Costura/Brilho. A sombra agora segue a
  silhueta limpa (sem rabicho) em TODAS as molduras.
- **`/rp ajuda` com título limpo**: o `╔══ ... ══` quebrava na fonte do
  canto da tela; agora é `» RP Tag — Comandos` (o » é da fonte padrão e
  nunca quebra).

## [3.62.0] - 2026-10-09

### Redesenhado — Balão FLUTUANTE (sem rabicho, inspiração nos mods de referência)
- PESQUISA: os mods campeões de balão de fala — **Talk Balloons** (~282 mil
  downloads), **TalkBubbles**, **Vanilla Chat Bubbles** e **Better Chat
  Bubbles** — NENHUM usa conector/rabicho; o balão flutua direto sobre a
  cabeça e a POSIÇÃO é a própria ligação.
- Quatro designs de rabicho foram aprovados e reprovados pelo dono
  (triângulo, degraus, curva pequena, recorte na silhueta) — o que sempre
  sobrava feio era o "pescoço" pendurado. **O rabicho saiu dos DOIS
  formatos** (procedural: `Look.tail = false`, nova opção pública na
  geometria; antigo: quads do rabicho removidos).
- Silhueta pura (retângulo arredondado), mais respiro na pilha (+4) e o
  visual que o dono elogiou na 3.58 ("que bonitinho") sem o pendurado.

## [3.61.0] - 2026-10-09

### Corrigido
- **AVISO não pulsa mais na tela** (pedido do dono) — texto firme, com fade
  de entrada/saida apenas.
- **AVISO aparece COM SHADERS**: mudou pra CAMADA OFICIAL de HUD
  (`registerAboveAll`) — vivia no `RenderGuiEvent.Post` e sumia com Iris (o
  mesmo mal do medo na 3.54).
- **AVISO MUITO MAIOR e serio**: escala ate 3.2x (era 2.2x), faixa mais
  alta e peso de tinta (negrito em duas passadas). Duracao 8s (era 6).

### Novo
- **`/rp admin avisozona <id> <texto>`** — aviso na tela de **TODOS que
  estiverem dentro da lorezone** (esfera da zona), para anuncios da
  historia. Avisa quanto gente recebeu; erro claro se a zona nao existe ou
  esta vazia.

### Corrigido — balao com shaders
- Com shaderpack LIGADO o jogo volta SOZINHO pro formato antigo (por
  texturas, comprovado com shaders) — o formato procedural novo usa
  `RenderType.gui()` e o Iris engolia ele no mundo (balao invisivel, print
  do dono). Quando o pack desliga, o procedural volta. `/rpformato novo`
  avisa se shaders estiverem ativos.

## [3.60.0] - 2026-10-09

### Reescrito — COMO o balão é gerado (procedural, sem textura)
- Até a 3.58 o balão era montado com **texturas fatiadas** (pílula em 3
  pedaços, anéis por PNG, rabicho como quad separado com contorno próprio).
  Isso causava: bordas borradas quando a textura estica, **emenda** entre
  rabicho e corpo, cantos deformados em balão alto e uma cor diferente de
  tinta em cada textura. Agora o balão é **UMA silhueta de verdade**, gerada
  por código (`client/BubbleGeometry.java`):
  - corpo de cantos arredondados + **rabicho curvo de HQ no MESMO contorno**
    (um polígono só, sem emenda, tamanho fixo 11,5×9 que nunca escala);
  - **contorno de espessura exata** seguindo a silhueta (inclusive em volta
    do rabicho), com **anti-aliasing** (bordas suaves, sem serrilhado);
  - **sombra macia** (gradiente), **degrade** vertical no recheio e **gloss**
    (faixa de brilho no topo) — o branco não vira mais cinza sujo;
  - raio do canto proporcional à altura (balão de várias linhas deixa de
    parecer uma cápsula esticada).
- As **5 molduras** viraram geometria própria e ficaram bem distintas:
  Clássica (fina) · Cartum (grossa + sombra de tinta de HQ) · Quadrada
  (cantos pequenos) · Costura (pontilhado interno) · Brilho (halo claro).
- **A prévia do `/balao` usa a MESMA função** do mundo (`BubbleSink` +
  `BubbleGeometry.build`) — o que você vê no menu é exatamente o que aparece
  sobre a cabeça, inclusive o rabicho inteiro.
- Camadas em Z separadas e **o eixo Z cresce com a distância** (`zk`): longe,
  sombra/borda/recheio não piscam mais no depth buffer.

### Segurança do formato novo
- Se o formato novo lançar qualquer exceção ao desenhar, o mod **volta sozinho
  pro desenho antigo** (texturas) e registra no log — o balão nunca some.
- Troca manual no jogo: `/rpformato novo` · `/rpformato antigo`.
- O desenho antigo continua no código (fallback) e as texturas continuam no mod.

### Testado sem o Minecraft
- `BubbleGeometry` é Java puro: `bash tools/teste-geometria.sh` (JDK 21)
  verifica a triangulação (4.000 formas aleatórias: área dos triângulos =
  área do polígono) e roda `build()` em 6.000 combinações aleatórias (sem
  erro/NaN, ~0,1 ms por balão, ~710 triângulos).
- Corrigido nesses testes: laço infinito no pontilhado da Costura (arredondamento
  de float) — nunca chegou a uma versão publicada.
- `check.py`: +13 verificações (107 no total).

## [3.58.1] - 2026-10-08

### Corrigido — rabicho no TAMANHO PADRÃO
- A 3.58.0 desenhou a curva em 26×18 unidades e ela saiu **GIGANTE** na tela
  (print do jogador). Agora é **13×9 fixo** — o mesmo porte dos rabichos que
  funcionavam — e **NUNCA escala** com o tamanho do balão.
- Contorno continua único (acompanha a curva) e a prévia do `/balao` segue
  idêntica ao mundo. Altura da pilha recalibrada (+12).

## [3.58.0] - 2026-10-08

### Mudado — Rabicho RECORTE NA SILHUETA (escolha do dono)
- Prévia com 4 opções de ligação personagem → balão; o dono escolheu a
  **opção B**: o rabicho é parte do **próprio balão** — curva de HQ com
  **um contorno único, sem emenda nenhuma** (visual de mod premium).
- Nova textura `tail.png` (texel BRANCO puro): a cor vem 100% do tint, com
  escala 248/255 fora da Quadrada → cor **exata** do balão em TODAS as
  molduras. Fim dos degraus pixel-art (3.56–3.57).
- Contorno acompanha a curva do rabicho (mesmo expandido 1px) e a pílula
  cobre o encaixe — sem linha atravessada, sem remendo.
- Prévia do `/balao` usa o MESMO rabicho (mesma textura, mesma regra de cor).
- Altura da pilha de balões atualizada pro rabicho mais fundo (+17px).

## [3.57.0] - 2026-10-08

### Novo — Tons de fala (inspirados no mod "Roleplay Speech Bubbles" de Fabric)
- **`/pensar <msg>`** — pensamento: balão em itálico com a fala entre
  parênteses `( assim )`, no chat local aparece `<Nome> pensa: ( ... )`.
- **`/s <msg>` (grito)** agora também vira balão: **CAIXA ALTA + negrito**.
- **`/w <msg>` (sussurro)** — o itálico era pedido no pacote desde sempre,
  mas o renderer IGNORAVA a flag; agora é itálico de verdade.
- Tons via pacote (`tone`: 0 fala, 1 pensamento/sussurro, 2 grito); o
  itálico usa a fonte vanilla (mesma largura, a medida não muda) e o
  negrito é "fake bold" (2ª passada 0,75px, mesmo avanço).
- Estudo do mod Fabric: balão = entidade Text Display vanilla com cooldown,
  sanitização e expiração — o rp-tag JÁ tinha equivalentes (flood-gate 250ms,
  truncamento 96 chars, raio 64 blocos).  O mod Fabric embute telemetria
  (`creativemode-telemetry`) — NADA disso foi copiado.

## [3.56.1] - 2026-10-08

### Corrigido
- **Rabicho agora é da MESMA cor exata do balão**: os texels da pílula são
  248 (não 255) e o rabicho usava texel branco — ficava ~5% mais claro que
  o balão. Agora o rabicho amostra a mesma janela de textura da pílula
  (a Quadrada, de texel 255, segue como estava). Medido na print do jogador:
  antes `rgb(166,87,221)` vs balão `rgb(158,82,210)` — agora idênticos.
- `/rp ajuda`: removida a linha amarela "Tudo fica salvo no mundo · guia
  completa: WALKTHROUGH.md".

## [3.56.0] - 2026-10-05

### Reescrito — o desenho dos balões de fala
- **Mais palavras = mais balão (pra cima!)**: o texto quebra em linhas
  equilibradas mais cedo (130px) e a pílula cresce em ALTURA — o balão fica
  sempre proporcionado, nunca vira uma tirinha gigante deitada.
- **Rabicho PIXEL em degraus**: dois degraus de pixel art com contorno por
  toda a volta — **nem quadrado** ligando no personagem, **nem triângulo
  colorido**. O mesmo rabicho vale para TODAS as molduras (Clássica, Cartum,
  Quadrada, Costura e Brilho). A encaixe na pílula é coberto por ela mesma
  (costura invisível, sem linha atravessada).
- A textura antiga do rabicho (triângulo curvo) foi REMOVIDA do mod.

### Prévia do menu agora é fiel com textos longos
- A prévia do `/balao` quebra a sua fala em linhas (mesmas regras do mundo),
  cresce em altura com o texto e ajusta o zoom — **sua última fala do chat
  nunca mais vaza da pílula** na prévia.

## [3.55.0] - 2026-10-05

### Corrigido — X vermelho e a tooltip "tirar Morango" travada
- O X dos emojis ficou **GRANDE (10px) e sempre visível**, e a tooltip
  " tirar Morango" **não flutua mais na frente de tudo** — o nome agora
  aparece na própria linha do título, à direita (some quando tira o mouse).

### Novo — DOWNLOAD de música pra pasta do SERVIDOR (todo mundo ouve!)
- `/lorezone baixar <link direto .ogg/.wav>` — "⤵ Download iniciado..." /
  " Download concluído (N KB)!" e o arquivo vai pra
  `config/rptag/musicas/` do SERVIDOR.
- **SYNC AUTOMÁTICO**: quando um jogador entra na zona, o servidor pergunta
  se o cliente já tem o arquivo; quem não tem **recebe os bytes do
  servidor** (em pedaços, gravado em `config/rptag/musicas/` do cliente) e
  **toca na hora** — ninguém precisa baixar nada!
- (YouTube continua precisando virar .ogg primeiro — o comando ensina.)

### Novo — som que toca 1x SÓ QUANDO ENTRA na zona
- `/lorezone musica <id> 1x <som>` — toca uma única vez na entrada (sem
  loop). O loop normal continua: `/lorezone musica <id> <som>` (ou
  `intervalo <s>`).

### Novo — /roll +3 (especialidade)
- `/roll +3` = "sou especialista nisso" → rola **1d20+3**. Também vale
  `/roll +3 atacar` e combinado: `/roll 2d6+3`.

### Alterado — /balao sem "teste": escreva no chat!
- O `/balao teste <frase>` saiu. Agora a **prévia usa a sua última fala
  real do chat**: escreva qualquer coisa no chat, abra o `/balao` e veja a
  SUA frase no balão de verdade.

### Corrigido — o "blur" quadrado na tela
- O ícone do medo tinha um **fundo pálido quadrado** que aparecia como um
  borrão na tela (o "blur"). A borda do quadrado agora **dissolve em
  transparência** — só a carinha e a palavra "medo" flutuam.

### Documentado — como a Lore Zone funciona
- O raio é uma **ESFERA**: `/lorezone criar x 10` pega 10 blocos para CADA
  lado (diâmetro 20), em altura também. A mensagem de criação agora explica.

## [3.54.1] - 2026-10-05

### Alterado — o ícone do medo é a FOTO INTEIRA
- Sem recorte: a imagem completa (fundo e a palavra **"medo"** embaixo) é
  o ícone do efeito no HUD e o símbolo que pisca no cantinho durante o medo.

## [3.54.0] - 2026-10-05

### Corrigido — o MEDO aparece COM SHADERS (e ficou MAIS FORTE)
- A causa de sumir com shaders: o desenho do medo (vinheta + véu) vivia num
  gancho que fica por baixo/fora da saída do shader (Iris). Agora ele é uma
  **CAMADA OFICIAL de HUD** (`RegisterGuiLayersEvent.registerAboveAll`) — a
  mesma pipeline do blur de abóbora/vinheta vanilla, que roda **depois do
  pós-processamento** e **acima de tudo** na tela. Com shaders e sem shaders,
  o medo aparece do mesmo jeito.
- Véu REFORÇADO: alpha 168 → **208** (a tela afunda na escuridão de verdade).

### Novo — o ícone do medo é o PNG enviado
- O ícone do efeito agora é **a foto real da carinha** (recorte com fundo
  transparente, 64x64 com autocontraste) em vez da carinha redesenhada.

## [3.53.0] - 2026-10-05

### Novo — a carinha do MEDO virou o ícone do efeito
- A carinha amarela assustada (sorridente de medo, com dentes) agora é o
  **ícone oficial do efeito `rptag:medo`** — aparece no canto do HUD (com o
  tempo restante) igual Veneno/Wither, em vez do quadrado branco vazio.

### Corrigido — `/effect give rptag:medo` agora dispara o MEDO completo
- Antes quem recebia o efeito só pelo `/effect give` **ficava normal** (sem
  tremedeira): os visuais só ligavam via pacote do `/rp admin medo`.
- Agora o **cliente lê o efeito local**: quem tem `rptag:medo` ativo — de
  `/effect give`, command block ou `/rp admin medo` — tem a tela escondida
  pelo véu azul-noite, tremendo, **até o efeito acabar**, sempre em sync com
  o tempo real do efeito.

## [3.52.0] - 2026-10-05

### Corrigido — os SONS das lore zones que ficavam MUDOS
- A causa raiz: o som era criado "solto" (evento não registrado) e o pacote
  de som saía sem o holder certo — caverna, alma, coração e a maioria dos
  presets **não tocava**. Agora o som vem do REGISTRO do jogo
  (`BuiltInRegistries.SOUND_EVENT`, o mesmo caminho do `/playsound`) e toca
  de verdade. Volume do som pontual também subiu (0.8 → 1.0).

### Corrigido — sons EMPILHANDO (agora um substitui o outro)
- Ao entrar de novo na zona, trocar de zona ou re-disparar no intervalo, o
  som/música ANTERIOR é parado antes do novo começar (`StopSound` por
  jogador, por categoria). Nunca mais dois discos sobrepostos.
- Saindo da zona, o som pontual também para (não fica "ecoando" atrás).
- Admin trocou o som/música? Quem JÁ ESTÁ dentro ouve o novo NA HORA.

### Novo — 17 presets de som + "nenhum" (27 no total)
- `portal`, `portalviagem`, `nether`, `vento`, `dragao`, `ghast`, `creeper`,
  `trovao`, `chuva`, `fogo`, `agua`, `sino`, `outros` (Otherside),
  `relic`, `creator`, `maldicao` (disco Ward) e `nenhum` (limpa o som da
  zona) — todos conferidos no `sounds.json` da 1.21.1.
- `/lorezone som <id> <som inválido>` agora **não salva**: o admin recebe o
  erro NA HORA (era assim que som errado passava batido e ficava mudo).
- TAB completa presets + as músicas `@arquivo` do servidor.

### Novo — música por LINK direto (.ogg/.wav)
- `/lorezone musica <id> https://site.com/tema.ogg` — os players **baixam o
  áudio automaticamente** (cache em `config/rptag/musicas/cache/`) e tocam
  em LOOP. Suporta `.ogg` Vorbis e `.wav` (limite 32 MB).
- **YouTube não toca direto** (o vídeo precisa virar áudio primeiro): o
  comando responde com o `yt-dlp -x --audio-format vorbis "URL"` pronto pra
  copiar e o `@nome` pra usar em seguida.

### Novo — resposta diz em qual VOLUME o pessoal ajusta
- Som pontual = slider **Ambiente** · música/loop = slider **Música**
  (Opções → Música e Sons). A dica aparece na confirmação do comando.

### Corrigido — X vermelho e "morango fantasma" na tela do balão
- O X vermelho de remover ficou **maior (14px)** e cada emoji da fala agora
  tem um **mini X sempre visível** no canto do slot (clique no slot = tirou).
- Com um adesivo/emoji "na mão", clicar fora do balão agora **solta** o
  adesivo — antes ele empilhava na fala E ficava grudado no mouse (o
  "morango bugado no meio").

## [3.51.1] - 2026-10-04

### Corrigido — ordem dos botões da placa Moldura
- As 5 molduras agora ficam em 2 fileiras de 3 — `[Clássica] [Cartum]
  [Quadrada]` / `[Costura] [Brilho] [Sem borda]` — sem buraco na grade e
  sem o botão de borda solto numa fileira própria. A placa ficou compacta.

### Corrigido — rabicho discreto (fica pouco à mostra)
- O rabinho encolheu de novo e o contorno fica COLADO no recheio (~1px),
  sem as "asas" escuras que apareciam em volta; também não tem mais sombra
  no rabicho (a sombra fica só no corpo). Na Quadrada o retangulinho
  acompanhou. A pilha de balões recalculou a altura (rabicho menor).

## [3.51.0] - 2026-10-02

### Segurança — tela do balão à prova de crash
- O menu `/balao` agora é blindado como o mundo: **qualquer erro** no desenho
  (cards, prévia, teclado) cai para um "modo simples" — os botões seguem
  funcionando e o motivo fica no log. Desenho, overlay, montagem da tela e
  cliques protegidos: a tela NUNCA mais derruba o jogo (inclusive com
  FancyMenu/menus customizados por cima).

### Embelezado — rabicho do balão (fim do "triângulo colorido")
- O rabinho era um triângulo GRANDE e chapado (print!). Agora é uma curva
  menor e elegante, levemente à direita do centro, com contorno da moldura
  e recheio por cima — cauda de quadrinho de verdade.

### Quadrada 100% quadrada
- O recheio da Quadrada usava a textura da CÁPSULA redonda por cima do
  contorno quadrado (quinas duplas). Agora corpo e recheio usam a mesma
  textura de cantinhos, e o rabicho vira um RETANGULINHO com contorno —
  o balão fica quadrado ao todo.

### Emojis dos DOIS LADOS
- Adesivo mora na coluna da ESQUERDA e os emojis da fala na coluna da
  DIREITA, independentes: dá pra deixar um emoji de cada lado com figuras
  diferentes (ex.: espada e escudo). Na prévia, soltar o adesivo na metade
  ESQUERDA do balão = adesivo; na metade DIREITA = emoji. Colocar 2 não
  empilha mais tudo de um lado (o bug da 3.50).

### Removidos — botões "Auto" e "Minha cor"
- Obsoletos (a cor da borda já se escolhe na paleta "Borda:"). Ficou um
  botão único **Sem borda / Com borda** que tira e repõe a borda.

## [3.50.0] - 2026-10-02

### Corrigido — Lore Zone agora dispara ao SAIR E VOLTAR
- Sair e reentrar na zona mostra o título/som de novo: o registro de zonas
  vistas agora limpa a zona quando o jogador SAI dela ("1x por visita",
  não mais "1x para sempre").

### Adicionado — `/rp ajuda` (lista de comandos com a função de cada um)
- Jogadores veem todos os comandos gerais (`/rp`, `/g`, `/s`, `/w`, `/me`,
  `/roll`, `/persona`, `/balao`...). Admins veem também a seção SO ADMINS
  (medo, avisar, lorezone, chatlocal, bolha/balão/bolhas, set, /do).

### Adicionado — API de MÚSICA CUSTOM para Lore Zones
- `/lorezone musica <id> @<nome>` toca o arquivo
  `config/rptag/musicas/<nome>.ogg` (pasta do CLIENTE) em LOOP enquanto o
  jogador está na zona, com STOP na saída (payload novo
  `lorezone_music`, canal v4).
- Decodificação OGG Vorbis pelo próprio jogo (OggAudioStream) + source
  OpenAL em loop, volume seguindo a categoria MUSICA do jogador. Pega
  música de vídeo (ex.: YouTube) com `yt-dlp -x --audio-format vorbis`.
- **Presets prontos** com nome fácil: `caverna`, `coracao`, `warden`,
  `deepdark`, `disc13`, `disc11`, `disc5`, `mood`, `almas`, `pigstep` —
  valem em `/lorezone som` e `/lorezone musica`. `/lorezone musicas` lista
  tudo e ensina a API.
- **Fix de parse da 3.48**: `minecraft:xyz` solto não era aceito (o argumento
  `string()` do Brigadier rejeita `:` em palavra única). Agora o som é o
  último argumento (greedy) e aceita namespace solto; o intervalo virou
  `/lorezone musica <id> intervalo <segundos> <som>`.

### Alterado — ADEUS balão-nuvem, OLÁ balão QUADRADO
- O estilo Nuvem (e o antigo Gibi) saíram: quem tinha salvo vê agora o
  balão **Quadrado** — retângulo de cantinhos arredondados ("meio
  quadrado"), desenhado com a técnica de 3 fatias (textura nova
  `quadrada.png`), sem peças que quebram.

### Corrigido — prévia do menu = O MUNDO, letra com respiro
- A pílula, as molduras e o rabicho da prévia agora são os MESMOS quads
  texturizados do mundo (`RenderType.text`); o fundo de céu/grama virou
  textura via BLIT (não some mais em cliente com shader).
- Linhas do menu reespaçadas: título das placas ocupa linha DENTRO da
  placa (não come mais a primeira fileira), gap entre todas as linhas
  (fim do "Letra" colado no "Moldura").
- Contadores "Emojis (0/6 na fala · 0 no balão)" REMOVIDOS (era ruído).

### Corrigido — emojis duplicados/quebrados
- Os adesivos/emojis são contados e desenhados por CODEPOINT: cada emoji
  vale exatamente 1 glifo (a contagem antiga dividia por 2 e fatiava a
  lista em pares — slot pulado, emoji dobrado e prévia diferente do
  mundo).

## [3.49.0] - 2026-10-01

### Corrigido — nuvem do balão (estilo Turma da Mônica)
- **Bolas de verdade, não mais quadrados com costura**: as bolinhas da nuvem
  eram desenhadas com a textura da PÍLULA (que tem quinas e costura nas
  bordas) — no mundo isso virava "quadradões" emendados, nada parecido com
  uma nuvem. Agora existe uma textura própria `bola.png` (círculo esférico
  HD, gerada em 128px e suavizada) e as bolas são quads texturizados nela.
- **Silhueta scalloped (gibi)**: a borda da nuvem foi reescrita em DUAS
  fileiras de bolas intercaladas (grandes 18px + pequenas 13px) no topo,
  na base e nas laterais, com o rabinho de pensamento em bolinhas
  decrescentes (10/8/6) — a sobreposição das bolas forma o contorno
  recortado de gibi, sem quinas visíveis em nenhuma borda.

### Corrigido — MEDO agora ESCONDE a tela
- **Véu de pânico**: além da vinheta, do ícone piscando e do tremor de
  câmera, o MEDO agora cobre a tela inteira com um véu azul-noite
  translúcido que pulsa com a intensidade do efeito — quem está com medo
  NÃO vê o mundo com clareza (os "espinhos" verticais da 3.48 saíram).

### Corrigido — prévia do menu de estilo
- **O menu agora mostra como fica NO MUNDO**: o card de prévia tinha fundo
  escuro, que escondia a pílula clara e "bugava" a aparência. O card agora
  tem fundo de CÉU (gradiente azul do horizonte ao zênite) com faixa de
  GRAMA embaixo — igual ao ambiente do jogo, a pílula aparece como
  realmente fica.

## [3.48.0] - 2026-10-01

### Adicionado — Lore Zones com música
- **`/lorezone musica <id> <som> [intervalo]`**: além do som 1x ao entrar,
  o admin escolhe uma TRILHA em LOOP para tocar enquanto o jogador está na
  zona (source MUSIC, volume 0.7), reiniciada a cada `intervalo` segundos
  (10–600, padrão 45). Ao sair, a música para na hora (StopSound).
- Sugestões tensas: `minecraft:ambient.cave`, `minecraft:entity.warden.heartbeat`,
  `minecraft:music.overworld.deep_dark`, `minecraft:music_disc.13`.

### Alterado — MEDO estilo anime
- Vinheta em preto/azul escuro (nada de vermelho), cortinas verticais de
  aura balançando, tinta azul pesada e carinha azulada piscando no HUD.

## [3.42.0] - 2026-09-19

### Corrigido — emblemas (adesivo/emoji na borda)
- **Emoji "fora do lugar" (flutuando longe do balão)**: a largura da pílula
  era calculada só a partir do TEXTO, sem nunca considerar o espaço que o
  próprio emblema (adesivo + emotes) precisa. Numa mensagem curta ("Oi!")
  com vários emojis, cada um é desenhado 18px à direita do anterior a
  partir de um ponto fixo — isso ultrapassava a pílula minúscula rapidinho,
  deixando emoji pendurado no vazio bem longe do balão. Agora a pílula
  garante espaço para o emblema inteiro por LARGURA (sem reintroduzir
  faixa/altura extra — a correção é só horizontal).
- **Qualidade inconsistente entre os emojis do mesmo balão**: o adesivo
  único (o que fica preso a um canto) desenhava pela fonte de baixa
  resolução (`stickers.png`, 16×16 por glifo — a mesma usada para emoji
  digitado dentro do texto do chat normal), enquanto os emotes extras já
  usavam o atlas em alta resolução (`stickers_hi.png`, 128×128 por glifo).
  Resultado: dentro do MESMO balão, um emoji saía nítido e o outro saía
  borrado/blocado — a inconsistência que parecia "emoji quebrado". Agora
  os dois caminhos (adesivo único e extras) usam o mesmo atlas HD no
  mundo, com a mesma nitidez tipo Android em ambos.
- **Emoji não acompanhava o fade do balão**: o quad HD sempre desenhava
  com opacidade 100% fixa, ignorando o alfa de entrada (pop-in) e saída
  (fade) do balão — o emoji aparecia/sumia de golpe enquanto o resto do
  balão entrava/saía suavemente. Corrigido nos três lugares que desenham
  emoji (inline no texto, adesivo do canto, emotes extras).
- A mesma correção de largura foi aplicada na prévia do menu de estilo,
  pra não voltar a divergir do que acontece no mundo.

## [3.41.0] - 2026-09-19

### Corrigido
- **EMBLEMA (adesivo/emoji na borda) CAINDO EM CIMA DAS LETRAS.** O bug:
  `by`/`syB` marcavam o CENTRO do emblema, mas o glifo mede 16px de altura
  (8px pra cada lado desse centro). O offset usado era só 4px — ou seja,
  8px de glifo menos 4px de offset deixava exatos **4px do emblema
  dentro da pílula**, encostando (ou entrando) na primeira linha de
  texto. Ficava pior quanto mais curta a mensagem: com a frase pequena a
  posição horizontal também colapsava pro centro do balão (mesmo lugar
  onde o texto está), então o emblema acabava bem em cima das letras —
  exatamente o que apareceu nos prints. Corrigido em TRÊS lugares que
  tinham a mesma conta errada: o balão no mundo (`BubbleRenderer`), o
  emblema da prévia do menu e o adesivo livre arrastável da prévia
  (`BubbleStyleScreen`). Agora o offset conta a meia-altura real do
  glifo (8) mais uma folga (4) — o emblema nunca mais encosta no texto.
- **Prévia do menu com o texto ampliado (zoom 2x) maior que a própria
  cápsula.** A altura da cápsula da prévia era um valor fixo de 18px,
  mas o texto ampliado sozinho já ocupa ~16px + respiro — o texto
  estourava por baixo antes mesmo de qualquer emblema entrar em cena.
  Agora a altura da cápsula é calculada a partir do zoom de verdade.

## [3.40.0] - 2026-09-19

### Adicionado
- **/balao teste <frase>** — mostra a frase como balao NA HORA, sem depender
  do chat (perfeito pra testar o estilo). Respeita a liberacao do admin e,
  se o modo balao estiver OFF, liga sozinho e avisa.
- **/rp status COMPLETO** — agora mostra tudo de uma vez: estado RP, persona,
  chat local (ligado/global), balao (liberado/bloqueado), modo balao e o
  estilo salvo (cor + moldura). Otimo pra diagnosticar "por que nao funciona".

### Documentacao
- **ENTREGA.md**: guia de handover pra quem for continuar o desenvolvimento
  (arquitetura, como buildar, regras de ouro do projeto e roadmap).
## [3.39.0] - 2026-09-18

### Seguranca / Robustez (auditoria de brechas)
- **PAYLOAD BLINDADO NO SERVIDOR**: o estilo enviado pelo cliente agora e
  sanitizado antes de gravar — borderStyle 0..5, posicao do adesivo 0..100%,
  duracao 2..20s, adesivo <= 4 e emotes <= 8 (so codepoints validos).
- **CRASH DE SESSAO MORTO**: borderStyle NEGATIVO (de cliente alterado)
  estourava o RING_MARGIN e DESLIGAVA OS BALOES DE TODOS ate reiniciar o
  jogo — agora o servidor rejeita na entrada E o render clampa defensivo.
- **ANTI-FLOOD DE BALOES**: minimo de 250ms entre baloes do mesmo jogador
  (4/s e mais que suficiente pra conversa RP) — sem spam de pacotes pra
  quem esta perto.
- **/balao off limpa a pilha TODA na hora**: o pacote de limpeza (texto
  vazio) apagava so um slot (virava balao fantasma na pilha); agora some
  TUDO do jogador na hora, como sempre foi prometido.
## [3.38.0] - 2026-09-17

### Corrigido
- **QUEBRA DE LINHA INTELIGENTE**: o wrap guloso do vanilla jogava a ultima
  palavra orfa numa linha curta embaixo ("tentou pegar o / calice"). Agora
  as linhas ficam EQUILIBRADAS (mesmo numero de linhas, reparticao por
  programacao dinamica: "tentou pegar / o calice" vira 2 linhas emparelhadas
  — sem palavra sobrando feia). Palavra maior que a linha inteira e fatiada
  no limite (nunca mais clipa na borda do balao).
- **Linha de texto mais larga (140 → 160)**: quebra menos, baloes mais
  compactos pra frases de tamano normal.
- A altura da pilha usa exatamente as mesmas linhas do render (paridade total).
## [3.37.0] - 2026-09-17

### Redesenhado
- **EMOJI = EMBLEMA NA BORDA — FIM DA FAIXA, FIM DO "AR"!** O balao agora
  fica do tamanho EXATO do texto (colado, como nos baloes de referência),
  e o adesivo/emotes vira um EMBLEMA pregado NO CONTORNO: sobre poe
  apenas a borda (metade pra fora), no lado escolhido — nunca sobre as
  letras, nunca criando faixa, linha ou espaco vazio. Era o pedido desde
  a 3.32, agora no desenho definitivo:
  - pílula = texto + 8px (pronto, acabou o vazio)
  - emblema: 16px, centro 4px fora da borda, na frente de tudo
  - posicao horizontal continua a sua (stickerX), lado = stickerY
  - pilha de baloes conta o emblema pendurado
  - previa do menu identica (capsula colada + emblema na borda)
## [3.36.1] - 2026-09-16

### Melhorado
- **BALAO ACOMPANHA QUEM ESTA VENDO — e agora fica LEGIVEL DE LONGE!**
  O balao JA gira pra cada jogador que olha (cada cliente renderiza o
  billboard virado pra propria camera: de tras voce le, pela direita ele
  vira pra voce — visual, so pra quem esta por perto, exatamente como
  pedido). A novidade: ele tambem CRESCE SUAVEMENTE com a distancia
  (1.0x perto → ~2.4x a 64 blocos), entao quem ve de longe continua
  lendo em vez de ver um puntinho.
- A pilha de baloes usa o MESMO fator de distancia (nada de sobrepor de longe).

## [3.36.0] - 2026-09-16

### Corrigido (por cima da reforma)
- **FIX RAIZ DO "AR" RE-APLICADO** (a reforma tinha saido de uma base anterior
  a 3.35.0 e a geometria antiga voltou): a base da pilula encosta EXATAMENTE
  na ancora (yP1 = 0, logo acima do nome) e o balao cresce SO PRA CIMA, 100%
  colado — o nome NUNCA mais fica dentro do box, zero espaco morto.
- Previa do menu sem ar interno de novo (38px com faixa / 18px sem = mundo).

### Reforma visual dos baloes (source do usuario + Claude)

### Reforma visual dos balões

- **As 6 molduras eram idênticas na prática — agora cada uma é distinta.**
  Esta era a causa raiz do balão "feio". O contorno sempre era desenhado
  apenas 2px para fora da pílula, e o recheio era desenhado *depois, por
  cima*. Toda a tinta além desses 2px ficava escondida atrás do recheio,
  então Clássica, Cartum, Dupla, Brilho e Gibi saíam com a mesma casquinha
  fina. Agora cada estilo tem sua própria margem para fora
  (`RING_MARGIN = {2, 4, 5, 3, 4, 5}`).
- **A moldura Tracejada não aparecia.** Era desenhada exatamente no tamanho
  da pílula, então o recheio cobria os tracinhos por inteiro. Além disso a
  "tampa" usava 32px enquanto a textura foi feita com 16 — o contorno não
  acompanhava a curva. Os dois erros foram corrigidos.
- **Balão branco saía cinza sujo.** A textura antiga escurecia o recheio até
  181/255 e o interior tinha alfa 0xEE (deixava o cenário atravessar). Agora
  o interior é opaco e a textura mantém a cor escolhida de verdade.
- **O estilo "Brilho" era invisível** — o halo era pintado com a cor *escura*
  da borda. Agora o halo usa uma versão clara da cor do balão.
- **Texto borrado.** A sombra preta do vanilla sujava cada letra dentro de um
  balão claro. Removida (o interior opaco já dá contraste sozinho).
- **Rabicho** parecia uma lasca torta colada no balão: a textura não convergia
  numa ponta. Refeito como um triângulo de quadrinho de verdade.
- **Sombra** deixou de ser um eco preto deslocado 2px (cara de clipart) e
  virou um apoio suave logo abaixo.
- **Balão ~18% maior** (escala 0.034 → 0.040) para ler de mais longe, e faixa
  de texto mais larga (110 → 140) para quebrar menos linhas.
- **Texturas refeitas**: `bubble`, `ring`, `cartum`, `dupla`, `gibi`,
  `dashed`, `bubble_tail`, e a nova `brilho.png`. A `cloud.png` foi removida
  (sem uso desde a 3.29.0).

### Corrigido
- `RING_MARGIN` tinha 5 posições para 6 estilos: o Gibi lia a margem do
  estilo errado por causa do clamp.
- A escala do balão (`0.034`) estava escrita à mão no render **e** na conta
  de empilhamento. Virou a constante `SCALE` — mudar uma e esquecer a outra
  fazia os balões empilhados se sobreporem.
- Removido código morto com UVs erradas: `flatQuad` calculava as coordenadas
  para uma textura 256x128 enquanto a `bubble.png` é 512x256 (o "brilho
  gelatinoso" do Gibi amostrava a região errada), além da nuvem de
  pensamento sem uso desde a 3.29.0.
- A prévia do menu foi realinhada com as espessuras e o halo do mundo.

## [3.35.0] - 2026-09-15

### Corrigido
- **O AR NO BALÃO — BUG RAIZ ENCONTRADO E MORTO!** A geometria da pílula
  usava o eixo Y INVERTIDO: a base do balão ficava ABAIXO da âncora e o
  box DESCIA POR CIMA do nome — o nome ficava DENTRO do balão e sobrava
  um retângulo enorme meio vazio (era isso em TODOS os prints!).
  Agora a base da pílula encosta EXATAMENTE na âncora (logo acima do
  nome) e o balão cresce SÓ PRA CIMA, 100% colado no conteúdo:
  [faixa?] + texto + [faixa?] — ZERO espaço morto, nome NUNCA mais
  dentro do box.
- **Prévia do menu IGUAL ao mundo**: pílula da simulação também colada
  (36px com faixa / 16px sem), sem ar interno.

## [3.34.1] - 2026-09-15

### Corrigido
- **Borda automática instável**: a cor da borda "Auto" usava um OU bit a bit
  pra dar um tom frio à cor escurecida — isso podia CLAREAR canais de forma
  imprevisível dependendo da cor escolhida. Agora é uma soma com limite por
  canal, sempre estável.
- **Balão "Brilho" podia se aproximar demais do balão empilhado acima**: a
  conta de altura da pilha esquecia o estilo Brilho no grupo que precisa de
  mais respiro (só contava Tracejada e Gibi).
- **`/me` e `/do` "funcionavam" sem avisar nada** quando quem mandou estava
  OFF RP: a ação só é entregue a quem está EM RP, então o próprio jogador
  às vezes não via a própria mensagem em lugar nenhum. Agora: `/me` avisa
  com uma mensagem de erro se você estiver OFF RP (`/rp on` primeiro); e
  quem manda a ação (inclusive `/do` do admin) sempre recebe o eco da
  própria mensagem, esteja em RP ou não.
- **"Chat local" podia voltar desligado sozinho** em saves onde a chave
  nunca foi gravada (mesma classe de bug que já tinha sido corrigida só
  pro "balões ligados globalmente").
- Documentação: o README ainda descrevia a moldura 4 como "Pensamento"
  (nuvem com rabinho de bolinhas), mas ela virou "Brilho" na 3.29.0 — o
  texto foi corrigido pra bater com o que o jogo realmente desenha.

## [3.34.0] - 2026-09-15

### Corrigido
- **/ME NAO VIRA MAIS BALAO!** Acao e AÇAO, fala e FALA: o /me agora vai
  SO para o chat RP — nunca aparece mais como balao de fala.
- **/ME E /DO RESPEITAM A REGIAO DO RP**: as acoes agora so chegam a
  jogadores EM RP dentro do alcance local (a regiao). Quem esta OFF RP
  nao ve as acoes — separacao clara entre:
  - **BALAO** = fala (chat normal no modo balao, /g, /s, /w)
  - **CHAT RP** = acoes (/me, /do) — so pra quem esta em RP, na regiao
  - **CHAT NORMAL** = conversa OFF RP (todo mundo ve)
- **TAG RP NO TAB (FINALMENTE!)**: achado o bug que impedia o TAB de
  mostrar o estado RP desde sempre — o handler via "nome padrao do
  vanilla" e saia sem colocar a tag. Agora o TAB mostra
  Nome + (RP)/(OFF RP) certinho, com atualizacao ao ligar/desligar.

## [3.33.0] - 2026-09-15

### Corrigido
- **FIM DO "AR" NO BALÃO (o espaço enorme do print!)**: a faixa dos
  emotes/adesivo foi REPROJETADA pra ser JUSTA — 20px no total (16 do
  emoji + 2px de ar de cada lado), no lugar dos 28px cheios de vazio.
  O balão agora fica colado no conteúdo: texto em cima, faixinha embaixo
  (ou em cima), rabicho — SEM espaço morto.
- Largura da faixa enxuta também (margens menores).
- A pilha de balões acompanha a faixa nova (altura exata).
- Menu em paridade 1:1 (prévia 42px com faixa justa).

## [3.32.0] - 2026-09-15

### Corrigido
- **EMOTE = SO A BORDA, LINHA NENHUMA (o pedido exato!)**: os emotes da
  fala NAO entram mais no texto — agora moram na FAIXA do balao (a mesma
  do adesivo), ao lado dele. A fala e 100% texto puro:
  - NADA de linha de emoji (o espaco vazio que aparecia no print morreu)
  - NADA de texto empurrado/escorrido
  - O balao fica do tamanho EXATO do texto (+ a faixa, se tiver emote)

### Melhorado
- Emotes desenhados por QUADS HD (Android) na faixa, em fileira organizada
  ao lado do adesivo — no mundo E na previa do menu (paridade 1:1).
- A pilha de baloes calcula a altura pelo texto puro + faixa (perfeita).

## [3.31.1] - 2026-09-15

### Corrigido
- **O BUG DO PRINT (texto pendurado fora do balao): CAUSA RAIZ
  ENCONTRADA E MORTA!** A pílula era desenhada com a altura SEM a faixa do
  adesivo — quando a faixa expandia o balao pra cima (28px), a pílula
  terminava 28px ANTES do fim do texto e as letras PENDURAVAM fora,
  formando aquela "faixa preta" solta embaixo do balao. Agora a pílula
  cobre TUDO (faixa + texto) nos dois lados — geometria verificada
  matematicamente pra faixa em cima E embaixo.

## [3.31.0] - 2026-09-15

### Corrigido
- **TEXTO + EMOJI FUNCIONANDO JUNTOS (de verdade!)**: o emoji na fala NAO
  passa mais pela fonte de texto — agora e um QUAD da arte NOTO (Android)
  desenhado EXATAMENTE onde a geometria manda. Impossivel virar bloco
  preto, invadir a linha de cima ou empurrar as letras: o texto e
  renderizado 100% separado do emoji.
- **TEXTURAS POT** (128x256 / 1024x2048): a fonte e o atlas agora tem
  tamanho potencia-de-2 — nunca mais quebram em driver/GPU nenhum.

### Melhorado
- **EMOJIS NO MUNDO EM HD**: os emojis da fala agora sao desenhados do
  MESMO atlas Android 128px do menu — menos pixel, mais imagem (como
  pedido!). A largura continua 16px, entao a quebra de linha nao muda.

## [3.30.0] - 2026-09-15

### Corrigido
- **FIM DAS SOBREPOSICOES (o bug do laco no texto!)** — tres correcoes de
  geometria:
  1. **FAIXA DO ADESIVO DOBROU (18 -> 28px)**: o adesivo de 16px ficava
     apertado na faixa de 18 e a base dele INVADIA a primeira linha de
     texto. Agora tem 12px de respiro puro entre adesivo e letras.
  2. **PILHA NA ESCALA CERTA**: desde que o balao cresceu 13%, a pilha
     ainda calculava com a escala ANTIGA — os baloes empilhados
     se sobrepunham. Agora a conta usa a MESMA escala do render.
  3. **FOLGA DO GLIFO DO EMOJI**: o emoji desenhado pela fonte sobe ~7px
     acima da linha — a pilula agora reserva esse espaco ANTES da primeira
     linha (nada de emoji vazando por cima do balao).
- A altura da pilha (bubbleHeightPx) espelha TODAS as novas medidas — a
  pilha continua perfeita com adesivo + emoji + texto grande juntos.

### Melhorado
- Menu em paridade: faixa de 28px na previa (balao 52px), texto a 31px do
  topo quando a faixa e em cima, emoji da fala top-alinhado com o texto.

## [3.29.0] - 2026-09-14

### Removido
- **NUVEM APOSENTADA** (a pedido!): o estilo PENSAMENTO virou **BRILHO** —
  halo suave ao redor do balao + anel elegante (2 desenhos, zero engasgo).

### Corrigido
- **EMOJI NUNCA MAIS QUEBRA O CAMPO DE TEXTO** (o bug do print!): a linha do
  emoji agora tem 20px com o glifo CENTRADO — as letras ficam sempre dentro
  do balao, mesmo com emoji colado na fala.
- Correcao critica do atlas HD (fileiras 9 e 10 dos novos emojis mostravam
  arte errada no menu).

### Adicionado
- **16 EMOJIS NOVOS (80 no total!)**: Sudoro, Apaixonado, Olhos de coracao,
  Festa, 100, Alvo, Presente, Chocolate, Bala, Pirulito, Cenoura, Panda,
  Dizzy, Papai Noel, Fada e Coelhinho — todos NOTO (Android) em HD.
- **LACO PRETO** (a pedido!): o laco rosa virou laco preto (tint proprio).

## [3.28.0] - 2026-09-14

### Adicionado
- **EMOJIS ANDROID DE VERDADE (NOTO)!** Os 64 adesivos agora usam a arte
  OFICIAL do Noto Emoji (a mesma fonte de emojis do Android) — licenca
  Apache 2.0, embutida no mod. No menu eles aparecem em ALTA DEFINICAO
  (atlas 1024x1024, 128px por emoji desenhado por quad direto).

### Corrigido
- **NUVEM: FIM DA FALHA** — a textura agora tem gomos PERIODICOS (32px) e o
  desenho no mundo usa periodos 1:1 (tile-perfeito): os gomos ficam SEMPRE
  do mesmo tamanho, nunca mais esticados/smeared.
- **COSTURA: FIM DO TRACINHO ESQUISITO** — mesma tecnica (periodos de 8px
  1:1): pontinhos constantes em qualquer largura de balao.
- **PIXEIS QUEBRADOS com muitas mensagens**: a pilula agora e 512x256 (2x
  mais resolucao) e o rabicho 64x64 — muito mais liso, mesmo com a pilha
  cheia de baloes na tela.

## [3.27.1] - 2026-09-13

### Melhorado
- **CONTORNOS NITIDOS EM TELA**: os aneis agora sao SOLIDOS (o alpha semi-
  transparente das versoes anteriores sumia quando a textura era comprimida
  no mundo — dava aspecto "esquisito"/falhado). Anti-aliasing so na parede
  externa de 1px: borda limpa de PERTO E DE LONGE.
- Costura com pontinhos mais fortes (2.6x5 solidos) e espacamento calibrado.
- Balao com contraste do volume um tico maior (legivel de longe).

## [3.27.0] - 2026-09-12

### Melhorado
- **REDESIGN COMPLETO — padrao STREAMER (a borda grossa foi embora!)**:
  - **VOLUME GERADO COM IA**: o brilho de gelatina da pilula agora vem de
    uma arte gerada por IA, remontada DENTRO da capsula matematica exata
    (geometria perfeita + volume de estrela — tinge perfeitamente).
  - **BORDAS FINAS E ELEGANTES**: todos os contornos foram afinados
    (Classica ~2px, Cartum 4px, Dupla 2 linhas finas, Gibi 6px) — nada de
    "borda grossa feia".
  - **BALAO 13% MAIOR** no mundo (presenca de quem fala pra plateia).
  - **TRACEJADO MORREU, NASCEU A COSTURA**: pontilhado delicado seguindo a
    silhueta da capsula (textura propria, 1 draw) — o bug do "quadrado no
    meio" ficou impossivel (nao existe mais quad nenhum).
  - Botao renomeado p/ "∴ Costura", ICONE verde da moldura ativa na placa,
    e dicas que EXPLICAM cada moldura quando o mouse passa em cima.
- Menu em paridade 1:1 (aneis com margem, costura, sombra do Cartum).

## [3.26.0] - 2026-09-12

### Melhorado
- **CADA BALÃO OLHADO E REFEITO (os 6 estilos!)**:
  - **PENSAMENTO**: textura da nuvem REFEITA do zero — gomos orgânicos de
    verdade na silhueta, interior liso com brilho, orla com degradê
    (Turma da Mônica premium, nada de "taturana").
  - **CLASSICA**: já com o anel em relevo da 3.25 (luz em cima da tinta).
  - **CARTUM**: capa de quadrinho com PROFUNDIDADE — sombra da tinta
    deslocada embaixo + brilho branco na parte de cima da capa.
  - **DUPLA**: aro escuro ganhou LUZ (brilho no aro externo).
  - **TRACEJADA**: cada tracinho agora tem um PONTINHO fofinho no meio do
    gap (padrão bilhete de cupom) — em cima, embaixo e nas laterais.
  - **GIBI**: brilho de gelatina em ELIPSE DUPLA (luz principal + núcleo
    super brilhante), igual balãozão de HQ.
- O MENU acompanha TUDO (paridade 1:1): capa com profundidade, luz no aro,
  pontinhos no tracejado, brilho duplo do gibi e nuvem com gloss.

## [3.25.0] - 2026-09-12

### Melhorado
- **QUALIDADE DOS BALÕES NOVA (pediram, ta entregue!)**: a pílula agora tem
  **brilho de gelatina** (luz no topo com elipse de reflexo), **degradê
  profundo** de cima pra baixo e **oclusão suave nas bordas** — visual 3D
  no padrão dos mods famosos, nada de cor chapada.
- **CONTORNO COM RELEVO**: o estilo Clássica ganhou textura própria de tinta
  (luz em cima, sombra embaixo) desenhada por um único quad — contorno de
  verdade, não uma linha reta sem graça.
- **RABICHO novo**: maior (32x32), afunilado com ponta arredondada e o
  mesmo degradê da pílula — acabei com a "casquinha" quadrada.
- O MENU acompanha: recheio com brilho (faixa clara + sombra) e a Clássica
  da prévia usa o mesmo anel em relevo via scanlines.

## [3.24.0] - 2026-09-11

### Melhorado
- **ZOOM NO CONTEUDO DA PREVIA!** A fala do balão no menu agora aparece com
  **2x de tamanho** (e os emojis em ARTE ampliada, não mais fonte miadinha) —
  dá pra LER exatamente o que vai pro mundo antes de testar.
- O balão da prévia cresceu pra dar bolso pro texto grande (e segue com a
  faixa de 18px quando tem adesivo — texto nunca encosta na faixa).
- Etiqueta " Prévia" movida pro pé do card (não briga mais com o balão).
- Se você encher 6 emojis e não couber, o zoom cai pra 1.5x sozinho —
  nada estoura o card.

## [3.23.0] - 2026-09-11

### Corrigido
- **FIM DO RECORTE NA BORDA DO BALÃO!** A textura da pílula foi refeita:
  a arte agora preenche o canvas INTEIRO (256x128, sem margem morta) e os
  UVs foram corrigidos — as linhas de cima/baixo que apareciam "mordidas"
  (o talo do morango comendo a borda) nunca mais acontecem.
- **ADESIVO NUNCA MAIS NO CANTO**: o adesivo agora fica 18px pra dentro das
  bordas — encostado no canto arredondado ele SAIA do balão (visto no print).
  Agora mora sempre dentro da pílula, na faixa de cima ou de baixo.
- A pilha de balões reserva a altura da faixa do adesão (nada se sobrepõe).

### Melhorado
- **PÍLULA = CÁPSULA MATEMÁTICA**: textura gerada por equação (retângulo +
  2 semicírculos perfeitos), gradiente limpo sem manchas — e o MENU usa a
  mesma equação via scanlines: o que você vê na prévia é EXATAMENTE o shape
  do mundo.
- **MENU BLINDADO**: o balão da prévia agora é desenhado com RECORTE dentro
  do card — impossível vazar pra fora e invadir as outras linhas, por mais
  gordo que seja o balão (nuvem + faixa + rabicho).
- Etiqueta " Prévia" em chavinha própria com filete dourado, card com
  moldura dupla, vinheta suave no fundo da tela, bolinhas do pensamento
  mais juntas.

## [3.22.0] - 2026-09-11

### Corrigido
- **ADESIVO NUNCA MAIS EM CIMA DAS LETRAS!**: o adesivo agora fica PRENDO nas
  faixas de borda do balão (cima ou baixo) — em qualquer posição X, mas o Y
  é travado fora da linha de texto. No mundo E na prévia do menu.
- **MENU 100% VISÍVEL**: a prévia inteira foi reescrita usando apenas `fill`
  (o desenho que SEMPRE funciona no menu) — zero dependência de texturas para
  informação crítica. Quem tinha "menu invisível" agora vê TUDO: pílula,
  nuvem com gomos contornados, rabicho, tracejado e adesivo.
- Clicar no balão para colar o adesivo agora define o lado pela metade
  clicada (metade de cima = faixa de cima).

### Melhorado
- **DESIGN DO MENU**: placas com acento dourado lateral, prévia mais alta
  (44px) com faixas de borda visíveis para o adesivo, texto da prévia
  centralizado, nuvem do menu com contorno escuro em todos os gomos
  (igualzinho ao balão de pensamento do mundo).

## [3.21.0] - 2026-09-11

### Melhorado
- **BALÃO DE PENSAMENTO (estilo Turma da Mônica!)**: a moldura Nuvem virou o
  balão de pensamento clássico — nuvem fofa com gomos contornados e o
  **rabinho de bolinhas que diminuem descendo em direção à cabeça** (no lugar
  do rabicho comum). No mundo E na prévia do menu.
- **CARTÃO DE PRÉVIA no menu**: a prévia agora fica dentro de um quadro
  próprio com etiqueta " Prévia:" — o jogador sabe EXATAMENTE onde olhar
  para ver como o balão vai ficar. Prévia maior (120px de largura mínima).
- **PILHA CONTA AS BOLINHAS**: balões de pensamento reservam o espaço do
  rabinho na pilha — nada de um balão invadir as bolinhas do outro.
- Textura da nuvem regenerada com gomos MAIS PROFUNDOS e alternados
  (silhueta orgânica de nuvem de pensamento).

### Mantido (a pedido)
- **ADMIN LIBERA QUEM USA**: `/rp admin balao <jogador> on|off` +
  `/rp admin balaolist` — padrão do servidor continua sendo NINGUÉM usa até
  o admin liberar.

## [3.20.0] - 2026-09-11

### Melhorado
- **BALÕES 4x MAIS DETALHADOS**: textura da pílula ampliada para 256x128 com
  gradiente suave e brilho — visual caprichado no mundo.
- **NUVEM REFEITA (textura própria!)**: gomos fofos CONTORNADOS, com brilho —
  nada mais de bolinhas chapadas. A nuvem agora é fofo de verdade.
- **TRACEJADA RETA**: tracinhos retangulares retos e alinhados em volta do
  balão (sem mais "círculos" e sem descadinha torta).

### Corrigido
- **TECLADO DE EMOJIS 100% JOGÁVEL EM QUALQUER CLIENTE**: a grade agora é
  feita de **BOTÕES VANILLA** (o componente que sempre funcionou), cada emoji
  senta num **chip "██" da própria cor** (texto colorido — o mesmo motor das
  paletas ● que sempre renderizou) e a arte vem pela **fonte de bitmap**
  (a MESMA que desenha os emojis nos balões no mundo — comprovado nos prints
  com a coroa e o "?"). Tudo desenhado POR CIMA dos widgets, na ordem certa.
- Cartão de teste no topo da página: 3 cores (█) + 1 emoji — se eles
  aparecerem, os 64 também aparecem (mesmo motor).
- Preview do hover em **3x** com moldura de tinta.

### Técnico
- `StickerArt` (runs) removido; menu volta a usar a fonte rptag:stickers via
  drawInBatch — uma ÚNICA fonte de arte para mundo e menu.

## [3.19.0] - 2026-09-11

### Corrigido
- **BALÕES EMPILHADOS NÃO SE SOBREPÕEM MAIS**: o espaçamento da pilha era
  fixo (0.55 bloco) — mensagens longas (2-3 linhas) têm mais de 1 bloco de
  altura e um balão invadia o outro. Agora a pilha soma a **ALTURA REAL de
  cada balão** (linhas, linhas com emoji, e margem pros gomos/tinta das
  molduras) — três balões enormes continuam empilhados, um acima do outro,
  sem cruzar.
- **Respiro horizontal** para as molduras que estouram pra fora
  (Tracejada/Nuvem/Gibi) e para falas com emoji — o texto não encosta mais
  nos gomos da nuvem.

## [3.18.0] - 2026-09-11

### Corrigido
- **TECLADO DE EMOJIS REPROJETADO EM ALTO CONTRASTE**: células bem mais claras
  que o fundo (era quase invisível na escuridão do painel), cada emoji agora
  senta num **CHIP da própria cor** (fundo colorido — mesmo que a arte não
  apareça num cliente estranho, o seletor continua utilizável e bonito).
- **[] DE REMOVER SEMPRE VISÍVEL**: o adesivo livre da prévia agora tem uma
  caixinha vermelha [] grudada nele — clicou, tirou (não precisa mais caçar
  o hover).
- Contador honesto: " Emojis (2/6 na fala · 1 no balão)".

### Diagnóstico
- A página de adesivos ganhou um **cartão de teste com 3 quadradinhos
  coloridos** (vermelho/verde/azul) no canto superior: se eles aparecerem,
  o menu renderiza fills normal — e os emojis (mesmo motor) também têm que
  aparecer. Se NEM eles aparecerem, o cliente está com jar antigo (conferir
  o número da versão no canto superior direito do menu).

## [3.17.0] - 2026-09-11

### Corrigido
- **MODO BALÃO AGORA DESLIGA DE VERDADE**: desligou (`/balao modo off` ou o
  botão )? Suas falas vão SÓ pro chat e o balão que estava na cabeça SOME
  na hora (antes ele continuava aparecendo).
- **SÓ QUEM O ADMIN LIBERAR USA BALÃO**: o padrão do servidor agora é
  NINGUÉM — o admin libera com `/rp admin balao <jogador> on` (que também já
  liga o modo balão da pessoa) e revoga com `off`. `/rp admin balaolist`
  lista os liberados. Acabou o amigo usando balão sem permissão.
- **"MINHA COR" E "AUTO" COM ANEL DE SELEÇÃO HONESTO**: o contorno verde
  agora fica no botão que está ATIVO (antes marcava "Auto" sempre, parecendo
  que os outros não funcionavam). "Minha cor" re-aplica a última cor clicada
  na paleta da Borda.
- **MOLDURAS BEM DISTINTAS**: Cartum ficou mais grossa e a **Dupla** agora é
  linha fina + aro EXTERNO ESCURECIDO (duas voltas visíveis de verdade).
- **NUVEM MENOR E MAIS DELICADA**: gomos pequenos e colados (antes virava um
  balão gigante).

### Melhorado
- **EMOJIS DO MENU À PROVA DE QUALQUER CLIENTE**: os emojis agora são
  desenhados com o MESMO método dos painéis (GuiGraphics.fill, run a run da
  arte premium) — o caminho que comprovadamente funciona no seu cliente
  (fonte de bitmap e blit de textura falharam nos testes reais).
- **EMOJIS SEM FIM**: com um adesivo na mão, QUALQUER clique num lugar vazio
  da tela empilha mais um emoji na fala (botões continuam clicáveis; ESC ou
  botão direito soltam o adesivo).
- **PRÉVIA MAIOR (34px de altura)** com fala e emojis na posição real, e o
  adesivo livre VISÍVEL e PEGÁVEL de volta no menu.
- **BALÃO MAIS FOFO**: textura nova com gradiente suave (topo mais claro) e
  contorno anti-aliased — visual caprichado estilo QSMP.

### Técnico
- `OpenBubbleStylePayload` agora carrega o modo balão (o botão  abre
  refletindo o estado real do servidor). Adesivos do menu gerados em código
  (`StickerArt`, runs da spritesheet — 64 emojis embutidos).

## [3.16.0] - 2026-09-11

### Corrigido
- **A CAUSA-MÃE dos emojis com fundo**: a spritesheet estava com FUNDO PRETO
  OPACO desde a 3.11 (bug do gerador de arte) — por isso os emojis tinham
  fundo, viravam blocos pretos no balão e "não apareciam" no menu (o blit
  desenhava o quadrado preto inteiro). A arte foi REGENERADA do zero a partir
  da versão transparente (3.10) com o mesmo acabamento premium (contorno,
  brilho, sombra e ponto de luz): **fundo 100% transparente**, provado por
  verificação pixel a pixel (zero pixels visíveis fora da forma).

### Melhorado
- Teclado: passando o mouse num emoji agora mostra um **preview GRANDE (32px)
  ao lado do nome** — dá pra ver certinho qual é antes de escolher.
- Slots vazios com "+" mais visível.

## [3.15.0] - 2026-09-11

### Corrigido
- **FIM DO MURO PRETO DE EMOJIS no balão**: desde a 3.11 o adesivo herda a
  cor da LETRA escolhida — com a letra preta, cada emoji virava um BLOCO
  PRETO sólido e fundia tudo num rabisco ilegível (e o adesivo do canto
  virava um quadradão preto). Agora o adesivo é **SEMPRE a arte colorida**
  (nunca é tingida) — o emoji aparece exatamente como você escolheu, e a
  posição dele volta a ser visível.
- **TECLADO DE EMOJIS QUE NÃO APARECIA**: os emojis do menu eram desenhados
  com drawInBatch (texto), que **não pinta em todos os clientes** — a grade
  ficava VAZIA (comprovado no print). Agora os emojis do menu são desenhados
  por **BLIT da spritesheet** — o MESMO caminho dos ícones do inventário,
  o mais robusto do jogo. Grade, slots de emoji, adesivo na mão e adesivo
  da prévia: todos com a arte de verdade, colorida, sempre.

### Técnico
- Novamente mudança SÓ no cliente (BubbleStyleScreen + 1 linha no renderer):
  zero diff de servidor/comandos (provado por git diff).

## [3.14.0] - 2026-09-11

### Mudado
- **PRÉVIA DO MENU AGORA É O BALÃO REAL**: acabou a "cerquinha de glifos" que
  ninguém entendia. A simulação do `/balao` desenha a MESMA pílula
  arredondada do mundo (mesma textura, mesmo rabicho), tingida com as cores
  do jogador, e mostra **as 6 molduras desenhadas de verdade** — clicou em
  Nuvem, vê a nuvem; clicou em Gibi, vê o balãozão de HQ com brilho; clicou
  em Tracejada, vê os tracinhos. A fala e os emojis ficam DENTRO da pílula,
  na posição real.
- Dica do topo agora explica: "Prévia REAL: é assim que seu balão aparece no
  jogo".
- Etiqueta da placa " Moldura" não é mais cortada pelos botões; fileiras
  respiram (botoes de moldura 143/161/179, adesivos 198, emojis 221, salvar
  244).

### Técnico
- Mudança SÓ no cliente (BubbleStyleScreen): zero diff de servidor/comandos
  (provado por git diff — a única linha fora do client é a versão).

## [3.13.0] - 2026-09-11

### Corrigido
- **FIM DO BUG DOS ADESIVOS REPETIDOS** (o muro de emojis do print!): com um
  adesivo na mão, QUALQUER clique fora do balão era engolido e virava "mais
  um emoji na fala" — inclusive os cliques NOS BOTÕES DE MOLDURA (por isso
  a borda parecia travada e o local do adesivo não escolhia).
- **FLUXO GUIADO**: agora, segurando um adesivo, a tela ACENDE duas zonas —
  o **balão** (verde: clique = colar o adesivo livre) e a **linha da fala**
  (dourada: clique = empilhar na mensagem, um clique por emoji). Qualquer
  clique FORA das zonas **solta** o adesivo e deixa os botões funcionarem.
  Dica no topo muda junto ("Clique NO BALÃO pra colar · na FALE pra
  empilhar · ESC solta").
- **FIM DO MURO DE EMOJIS no mundo**: os emojis da fala agora saem
  **ESPAÇADOS** entre si (cada um legível, nada de grade de adesivos) e um
  balão cheio de emojis **substitui a pilha** (não empilha mais muro).

### Mudado
- **MENU REDESENHADO EM PLACAS DE QUADRINHOS**: painel com moldura de tinta
  grossa, sombra dura e brilho (cartoon de verdade), **placas " Cores" e
  " Moldura"** com etiquetas douradas, faixa de título com sublinhado e
  slots de emoji com caixinha própria. O tema TODO do menu fica em 10
  constantes (TINTA/FUNDO/OURO/...) — fácil de alterar.
- Fileira do resumo hex removida (as paletas já mostram a seleção) — menu
  mais compacto e sem NENHUMA área de clique invadindo outra.

## [3.12.0] - 2026-09-10

### Adicionado
- **3 NOVAS MOLDURAS de balão** (agora são 6!): **▤ Tracejada** (tracinhos
  arredondados em toda a volta, com rabicho em descadinha), **▤ Nuvem**
  (gomos de nuvem fofos ao redor do balão inteiro) e **▤ Gibi** — o balãozão
  de HQ clássico do estilo Turma da Mônica: bem inflado, tinta grossa e
  **brilho gelatinoso** no recheio.
- A moldura escolhida é realçada com a caixa verde na fileira certa (as
  molduras agora ocupam 2 fileiras de botões).
- Bordas são só um número no estilo: **zero mudança de rede** — mundos e
  combinações antigas continuam funcionando (Clássica/Cartum/Dupla intactas).

## [3.11.0] - 2026-09-10

### Adicionado
- **ARTE DOS EMOJIS EM ALTA RESOLUÇÃO (16px)**: todos os 64 adesivos foram
  reprocessados com o acabamento dos mods famosos (padrão Discord/Emojiful):
  **contorno escuro de sticker, brilho de caramelo no topo, sombra de volume
  na base e ponto de luz**. Spritesheet ampliada para 128x128 e fonte de
  bitmap com glifos de 16px — os emojis ficam maiores e muito mais bonitos
  no mundo e no menu.
- **ANIMAÇÃO do balão estilo mods famosos** (padrão Chat Bubbles/
  TalkBubbles/ChatBubble): **pop-in com ressalto** (easeOutBack) ao nascer,
  **flutuação suave pra cima** enquanto o balão vive e **fade-out** suave no
  fim.
- **PILHA DE BALÕES**: até **3 balões por jogador** empilham — o mais novo
  fica perto da cabeça e os antigos sobem (nada de mensagens se apagando).

### Técnico
- `ClientBubbleCache` agora guarda uma pilha por jogador com o instante de
  nascimento de cada balão; `BubbleRenderer` desenha cada balão da pilha com
  a própria animação. Linhas com emoji ocupam 18px (glifo 16px). Texto puro
  segue EXATAMENTE a mesma geometria de antes (zero regressão visual).

## [3.10.0] - 2026-09-10

### Adicionado
- **VÁRIOS EMOJIS NA FALA**: com um adesivo na mão, clique FORA do balão da
  simulação para acrescentar ele à fala — um clique por emoji, **até 6**. Os
  emojis extra aparecem DENTRO do balão acompanhando o texto (arte colorida
  de verdade), e a simulação do `/balao` mostra o resultado exato. Fileira
  nova " Emojis (n/6)" com slots: **/clique tira**, **+ abre o teclado**.
- **PÁGINA FOFA no teclado de adesivos**: 16 emojis novos desenhados à mão no
  mesmo estilo pixel-art — **Laço, Dinossauro, Planeta, Galáxia, Foguete,
  Arco-íris, Patinha, Borboleta, Chapéu de mago, Unicórnio, Raposa, Pinguim,
  Cogumelo, Fantasma, Rex e Varinha mágica** — pra fala carregar história e
  personalidade (fofo pra criançada!). O teclado agora tem **2 páginas**
  (Clássicos 48 + Fofos 16 = **64 adesivos**) com botão " Fofos ▶ /
  ◀ Clássicos".
- Spritesheet ampliada para 64x80 e fonte de bitmap com 64 glifos
  (U+E000–U+E03F).

### Corrigido
- O título do painel não é mais coberto pelo cabeçalho do teclado de adesivos
  (arrastava desde a 3.8.0).

### Técnico
- `BubbleStyle` ganhou o campo `extras` (máx. 12 chars = 6 emojis), salvo no
  NBT do mundo (`Extras`) e transmitido nos 3 pacotes (abrir estilo, salvar
  estilo e balão de fala). Mundos antigos continuam funcionando (sem Extras =
  vazio). Canal de rede v2 mantido: cliente e servidor precisam do MESMO jar
  3.10.0 (jars diferentes são recusados com aviso na conexão).

## [3.9.0] - 2026-09-10

### Corrigido
- **EMOJIS VISÍVEIS NO TECLADO DE VERDADE**: os adesivos do menu agora são
  desenhados pelo MESMO método do balão no mundo (`drawInBatch` + fonte de
  bitmap, escala 2x) — o único caminho que comprovadamente funciona em todos
  os clientes testados. Escolha VENDO o emoji (bolinhas e números eram só
  fallback); o nome segue no rodapé e no tooltip.
- O adesivo colado aparece na simulação com a arte real, e o adesivo "na mão"
  segue o mouse com a arte também.

## [3.8.0] - 2026-09-10

### Corrigido
- **FONTE DO BALÃO LIMPA DE NOVO**: o contorno de tinta da 3.7 fundia as letras
  num borrão (cada halo desenhava a própria sombra = tinta dupla). Voltou a
  sombra vanilla clássica — legível e bonita — com o texto 20% maior que o
  original.
- **MENU 100% JOGÁVEL EM QUALQUER CLIENTE**: TODOS os elementos essenciais
  (paletas, adesivos, simulação do balão) agora são desenhados apenas com
  TEXTO do jogo (glifos ● e ▣, o mesmo motor da tag "● ʀᴘ" que sempre
  funcionou) + botões vanilla. Se aparece no chat, aparece no menu.
- **SIMULAÇÃO DO /balao refeita**: linha do chat real (`<Você> fala` com as
  cores) + balão em moldura ▣ com o rabicho ▼ e o adesivo na posição exata —
  o que você vê ali é o que aparece sobre a sua cabeça.

## [3.7.0] - 2026-09-10

### Corrigido
- **CAUSA RAIZ de "a cor não salva" entre amigos**: os pacotes mudaram de
  formato desde a 3.2 mas o canal de rede continuou na versão 1 — cliente novo
  em servidor velho conectava e **corrompia os saves silenciosamente**. O canal
  agora é **v2**: jars diferentes são RECUSADOS com aviso claro na conexão.
  SEMPRE atualize cliente e servidor juntos!
- **Menu 100% sem texturas e sem fontes especiais** (era isso que sumia em
  certas GPUs): adesivos agora são **bolinha de cor + número + NOME no tooltip**
  — desenhados só com primitivas que funcionam em qualquer cliente.
- **Layout testado automaticamente**: 22 verificações (ordem das linhas, sem
  sobreposição, hitbox == desenho, codecs simétricos) — a 3.6 tinha a dica
  escrita POR CIMA da paleta Dentro.
- **Letra do balão no mundo com CONTORNO DE TINTA** (halo escuro em volta de
  cada letra) + escala 12% maior: legível em qualquer cenário.

## [3.6.0] - 2026-09-10

### Adicionado
- **COMANDOS DE ADMIN para o balão**: `/rp admin balao <jogador> off` proíbe o
  jogador de usar balão (a fala dele volta só ao chat), `on` permite de volta e
  `balaolist` lista os proibidos. Fica salvo no mundo.
- **Menu redesenhado (bonito e legível)**: cabeçalho com título dourado e faixa
  de acento, SIMULAÇÃO DE CHAT com fonte GRANDE (escala 1.5x) e rodapé com
  quadradinhos das cores atuais + hex.
- **Paletas de 12 cores** por alvo (Dentro e Borda) — 1 clique pinta, sem
  arrastar nada. Letra: Auto + 8 cores.

### Corrigido
- **Spritesheet dos adesivos agora é POTÊNCIA DE 2 (64x64)** — texturas não-
  potência-de-2 podem falhar em certas GPUs/configurações gráficas, o que
  apagava os desenhos do menu (teclado vazio, pílula invisível).
- Layout compacto (224px) que cabe em GUI scale 3 e telas pequenas; painel
  sempre com margem (clamp), nada mais corta.
- Texto do balão no mundo ~12% maior (escala 0.028) — mais legível de longe.

## [3.5.0] - 2026-09-10

### Adicionado
- **SIMULAÇÃO DE CHAT AO VIVO no menu**: painel estilo chat com a linha
  `<Você> sua fala assim` (nome na cor do balão, fala na cor da letra) e o
  balão completo — reage NA HORA a fundo, borda, molde, letra e adesivo.
- **COR DA LETRA**: modo **Auto** (branco/preto conforme o fundo, contraste
  perfeito) ou 8 cores de chat à 1 clique. Salva no mundo, viaja pra todos e
  aparece na mensagem de confirmação ("· Letra #RRGGBB / auto").
- Rodapé da simulação com o resumo `fundo · borda · letra`.

## [3.4.0] - 2026-09-10

### Adicionado
- **PALETAS DE 1 CLIQUE**: 8 cores prontas pra DENTRO e 8 pra BORDA, logo
  embaixo de cada seletor — clicou, pintou. Zero arrastar (pra quem o arrastar
  não estava funcionando). Arrastar continua disponível pra cor custom.
- **Diagnóstico de jar embutido**: a tela mostra a versão do CLIENTE (canto
  superior direito) e a mensagem de salvar mostra a versão do SERVIDOR
  (" Salvo no servidor vX.Y.Z!"). Se diferirem, o jar do lado antigo precisa
  ser trocado — essa era a causa real de "nada muda".
- Teclado de adesivos com **imagens grandes (20px)** e ponto de cor de
  identidade em cada célula.

### Corrigido
- Tela compactada (240px) pra caber em telas 480p com GUI scale 2 — os botões
  de baixo (Salvar!) não são mais cortados.
- Texturas legadas não usadas removidas do jar.

## [3.3.0] - 2026-09-10

### Adicionado
- **2 seletores INDEPENDENTES**: " Dentro" pinta o recheio e "▣ Borda" pinta
  a moldura — cada um com seu próprio quadrado e faixa de cores (fim de
  alternar alvo). Mexeu no da borda? Já vira PERSONALIZADA.
- **6 botões explícitos**: cor da borda (**▣ Auto / ▣ Minha cor / ▢ Sem
  borda**) e molde (**▤ Clássica / ▤ Cartum / ▤ Dupla**) — o ativo ganha
  moldura verde.
- **Mira de posicionamento**: ao colar/mover o adesivo, uma cruz segue o mouse
  e o canto do preview mostra a posição exata em **x,y %**.

### Corrigido
- **Emojis do editor finalmente visíveis SEMPRE**: os adesivos agora são
  desenhados como IMAGEM CRUA (blit da spritesheet), sem depender da fonte de
  bitmap no GUI — funciona em qualquer cliente.
- **Preview legível**: fundo xadrez atrás do balão — qualquer cor de dentro
  (até branca) contrasta; texto com sombra e hex ao vivo.
- Adesivo nunca mais cola cortado: posição limitada a 5–95% (sempre inteiro
  dentro do balão).

## [3.2.0] - 2026-09-10

### Adicionado
- **3 MOLDES de borda** para o jogador escolher: **Clássica** (linha de tinta
  fina), **Cartum** (grossa, capa de quadrinho) e **Dupla** (duas linhas com
  respiro). Salvo junto com o estilo.
- **Selo de versão** no canto da tela (ex.: "3.2.0") — se não aparecer, o jar
  do cliente é antigo (cliente e servidor precisam do MESMO jar novo).

### Corrigido
- **Emojis do teclado não apareciam na tela**: agora os adesivos do editor são
  desenhados pelo MESMO caminho do balão no mundo (fonte de imagem), idênticos
  de um lado pro outro.
- Mensagem de salvamento agora deixa claro que o estilo **fica salvo pra
  sempre** — sair e voltar mantém cores, adesivo, molde e duração.

## [3.1.0] - 2026-09-10

### Corrigido
- **"Coloquei 1 coração e apareceram 2"**: os adesivos antigos dos slots
  Antes/Meio/Depois continuavam sendo desenhados (fantasmas). Eles agora
  **migram sozinhos** para o adesivo livre único — 1 adesivo por balão.
- **Cor "não salvava"**: o botão Salvar ficava na ESQUERDA e o Cancelar na
  DIREITA (ao contrário do hábito do Minecraft) — era fácil cancelar tudo sem
  querer. Agora ** Salvar fica na direita** e o **ESC também salva**.

### Adicionado
- Botão **↺ Padrão** para restaurar o estilo original na hora.
- Com o adesivo na mão: clicar **fora do balão** ou **botão direito/ESC
  cancela** — sem colar sem querer e sem "comer" os cliques do seletor de cor.

## [3.0.0] - 2026-09-10

### Adicionado
- **ADESIVO LIVRE com o mouse**: o jogador pega o emoji e cola em QUALQUER
  ponto do balão — clique no adesivo, clique no lugar (ou arraste). Clica de
  novo pra reposicionar e clique direito pra tirar. A posição (% da largura e
  da altura) é salva junto com o estilo.

### Mudado
- **Balão com TEXTURA SUAVE**: adeus faixas empilhadas — a pílula agora é
  desenhada com textura arredondada anti-aliased (estilo app de mensagem),
  contorno de quadrinhos em toda a volta e rabicho curvado.
- **O preview da tela é o balão de verdade** — o que você vê é o que fica.
- Balão FULLBRIGHT: continua legível no escuro.

### Corrigido
- Estilos do balão NÃO eram recarregados ao reiniciar o servidor (cores e
  adesivos voltavam ao padrão) — agora persistem de verdade.

## [2.9.0] - 2026-09-10

### Mudado
- **Editor repensado (sem caminho sem saída)**: os 4 destinos de adesivo
  (Antes/Meio/Depois/Canto) ficam SEMPRE visíveis — clica no destino (fica
  verde) e depois no adesivo, ou arrasta. **Clique direito no destino TIRA**
  o adesivo (setinhas do teclado também trocam o destino).
- **Cores sem confusão**: cartões DENTRO e BORDA sempre visíveis mostrando as
  cores atuais — o seletor pinta o que estiver selecionado (moldura branca).
- **Borda em 3 estados claros**: Automática → Personalizada (sua cor) → Sem,
  tudo num botão que sempre diz o estado.

### Corrigido
- A cor escolhida na tela NUNCA mais é sobrescrita por valor antigo do mundo.
- Contorno do balão agora aparece em toda a volta (topo/fundo incluídos).

## [2.8.0] - 2026-09-10

### Mudado
- **Visual estilo QUADRINHOS**: pílula arredondada com recheio da cor de dentro,
  CONTORNO da cor da borda, sombra e rabicho — como o balão clássico dos quadrinhos.
- **Adesivo de CANTO**: além de Antes/Meio/Depois, o jogador pode arrastar um
  adesivo para o CANTO do balão (estilo reação de apps de mensagem).

### Corrigido
- Confirmação de salvamento agora mostra as cores escolhidas (dentro e borda),
  para o jogador ver exatamente o que ficou.

## [2.7.0] - 2026-09-10

### Adicionado
- **TECLADO DE ADESIVOS**: 48 adesivos coloridos em pixel-art (corações,
  estrela, fogo, gato, cachorro, coroa, ovo, dado e mais) — clique e ARRASTE
  o adesivo até Antes/Meio/Depois da frase, como um sticker de verdade.
- **CUSTOMIZAÇÃO TOTAL das cores**: o seletor agora escolhe a cor da parte
  de DENTRO **e** a cor da BORDA do balão (botão Alvo: Interior/Borda), com
  botão para remover a borda. A cor do texto se ajusta sozinha.

### Removido
- Fundos prontos (papel/noite/madeira etc.) — a cor de dentro agora é a que
  VOCÊ escolher no seletor.

## [2.6.0] - 2026-09-09

### Adicionado
- **12 ADESIVOS coloridos em pixel-art** (fonte de bitmap própria): coração,
  estrela, brilho, fogo, nota, raio, lua, flor, espada, ovo, diamante e coroa —
  são IMAGENS coloridas de verdade dentro do balão, não emotes preto e branco.
- **Barra de DURAÇÃO** na tela: o jogador escolhe de 2 a 20 segundos quanto
  tempo o texto fica visível sobre a cabeça.

### Corrigido
- **Quadrado do balão perfeito**: todas as posições agora em pixels inteiros
  (largura par e centralização arredondada) — sem meio-pixel borrado.

## [2.5.1] - 2026-09-09

### Corrigido
- **Menu de personalização embaçado**: o fundo padrão do 1.21 aplica o
  DESFOQUE do mundo por cima da tela aberta — borrava o painel inteiro.
  Agora a tela usa fundo OPACO próprio (nunca desfoque), com tema escuro
  nítido e biséis estilo vanilla.
- Botões de emoji não cortam mais o texto ("Depois" virou rótulo em cima
  do botão + botão mostra só o símbolo).

## [2.5.0] - 2026-09-09

### Adicionado
- **TELA de personalização com SELETOR DE COR de verdade** (`/balao`): arraste o
  mouse no quadrado de cores + faixa de arco-íris e escolha QUALQUER cor, com
  preview do balão ao vivo e código hex.

### Mudado
- **Visual do balão redesenhado**: cartão com cantos chanfrados, sombra, barra
  de destaque com a sua cor e gradiente real — tudo desenhado pelo próprio jogo.
- **Novo design da tag RP**: **● ʀᴘ** (ponto ciano) / **○ ᴏꜰꜰ ʀᴘ** (ponto cinza) —
  e ela aparece **no TAB também**, para todo mundo saber quem está em RP.
- **`/do` agora é SÓ PARA ADMIN** (descrição de ambiente vira ferramenta de narrador).
- Emojis: além de antes/depois, agora existe emoji do **MEIO** da mensagem.

### Removido
- `/balaoedit` e `/bolha` (tudo concentrado em `/balao`) e as personalizações
  por chat (`/cor`, `/balao cor`, `/balao borda`, `/balao fundo`, `/balao emoji`).
- Menu-baú da 2.4.0 (substituído pela tela com seletor de cor).

## [2.4.1] - 2026-09-09

### Corrigido
- **`/balaoedit` não abria nada**: faltava registrar a TELA do baú no cliente.
  Agora abre igual um baú de verdade (textura nativa do jogo).
- **Balão invisível no mundo**: o fundo agora é desenhado pelo PROPRIO JOGO —
  o mesmo mecanismo do fundo das nametags do Minecraft — e o rabicho é o
  caractere ▼ da fonte do jogo. Não existe mais NENHUM desenho customizado
  no mundo: impossível ficar invisível, impossível derrubar o cliente.

## [2.4.0] - 2026-09-09

### Adicionado
- **EDITOR do balão em inventário** (`/balaoedit` ou `/balao`): um baú de 6
  fileiras, 100% vanilla na tela — impossível de crashar pela GUI. Clique nas
  cores (centro e borda), troque o fundo, ligue/desligue o modo balão e limpe
  tudo. Fecha o baú = salva automaticamente.

### Mudado
- **Emojis agora por ARRASTAR**: pega o emoji na paleta e solta na caixinha
  Antes / Meio / Depois do texto (ou Shift+clique manda direto pro Antes).
  Novo: emoji no MEIO da mensagem!
- Personalização por comando de chat REMOVIDA (`/balao cor`, `/balao borda`,
  `/balao fundo`, `/balao emoji`, `/balao limpar`) — tudo agora no editor,
  mais fácil para os jogadores. `/balao modo on|off` continua.

### Corrigido
- **Crash do cliente ao desenhar o balao (raiz encontrada nos fontes do 1.21.1)**:
  o fundo agora usa BUFFER PROPRIO do mod e o flush acontece dentro da protecao
  de erros — qualquer problema desliga os baloes com aviso no log, o jogo NUNCA
  fecha. A emissao de vertices e identica a do vanilla (cor + UV + luz cheia) e
  usa a textura branca NATIVA do jogo (zero risco de falha de textura).

## [2.3.1] - 2026-09-09

### Corrigido
- **CRASH do cliente ao desenhar o balao** (reportado por teste real): o
  renderizador do balao agora e TOTALMENTE protegido — se qualquer erro
  acontecer ao desenhar, o balao e desligado ate reiniciar o jogo (com
  aviso no log) e o jogo **nunca mais fecha**. A tag RP, o chat e os
  comandos continuam funcionando normalmente.
- **Vertices do balao identicos aos do vanilla**: emissao de vertices agora
  e exatamente a mesma usada pelo texto do jogo (cor + UV + luz cheia),
  eliminando a divergencia que causava o problema.
- **`/balao cor` e `/balao borda` agora aceitam cores com `#`** (ex.:
  `/balao cor #FFD700`) — antes o `#` era rejeitado pelo parser do comando.

## [2.3.0] - 2026-09-09

### Corrigido
- **Fundo do balao invisivel no mundo** (bug critico da 2.2.0): os poligonos
  usavam RenderType de GUI, que nao renderiza no contexto de entidades —
  so o texto aparecia, quase ilegivel. Agora usa o render type de TEXTO
  (o mesmo das nametags), que funciona no mundo 3D. Fundo mais opaco (90%)
  e texto COM SOMBRA para leitura perfeita.

### Adicionado
- **Borda do balao** com cor personalizavel (`/balao borda <cor|off>`,
  botao na tela `/balao`), padrao quase-preta para destacar em qualquer cenario.

## [2.2.0] - 2026-09-09

### Adicionado
- **Modo balao** — para criancas, ovos e personagens sem voz: as falas do
  jogador aparecem SOMENTE no balao sobre a cabeca (sem texto no chat),
  para quem estiver perto.
  - `/balao modo on|off` — o proprio jogador liga/desliga
  - `/rp admin bolha <jogador> on|off` — admin ativa/desativa para outro jogador
- **Tela de personalizacao** (`/balao`) — abre uma GUI com preview ao vivo:
  - Sliders Vermelho/Verde/Azul + 12 cores rapidas
  - Emojis decorativos antes/depois do texto (botoes rapidos: **        ** —
    ou digite os seus na caixa)
  - 7 fundos: Translucido, Escuro, Claro, Gradiente, **Papel**, **Noite** e
    **Madeira** (texturas exclusivas empacotadas no mod)
  - Salvo no servidor (persiste no mundo); texto com contraste automatico
- Comandos por texto (sem GUI): `/balao cor`, `/balao emoji`, `/balao fundo`, `/balao limpar`

## [2.1.0] - 2026-09-09

### Adicionado
- **Balões de fala (chat bubbles)** — o que o jogador digita aparece num balão
  arredondado com rabicho sobre a cabeça, com quebra de linha e tempo de
  exibição proporcional ao tamanho da mensagem. Ideal para ovos, crianças e
  quem não usa microfone. Gerado pelo servidor: todo mundo com o mod no
  cliente vê os balões de todos (chat local, `/g`, `/s`, `/w` e `/me`).
- **`/cor <cor>`** — cor pessoal de cada jogador: pinta a mensagem no chat E o
  fundo do próprio balão. Aceita nomes em português (`ciano`), nomes do
  Minecraft (`dark_aqua`) e hex (`#55FFFF`). Contraste do texto automático.
- **`/bolha on|off`** — desliga os próprios balões.
- **`/rp admin bolha on|off`** — liga/desliga balões do servidor inteiro.

## [2.0.0] - 2026-09-05

### Adicionado
- **RP Persona** — `/persona criar <nome>` (liga o RP junto), `/persona idade`,
  `/persona desc` (tooltip ao passar o mouse no nome), `/persona ver [jogador]`,
  `/persona off`. O nome do personagem substitui o nick no nametag, chat e TAB.
- **RP Chat** — chat comum agora e **local** (40 blocos) por padrao; `/g` global,
  `/s` grito (100 blocos, CAIXA ALTA), `/w` sussurro (5 blocos), `/me` acao,
  `/do` ambiente. Toggle: `/rp admin chatlocal on|off` (persistido no mundo).
- **RP Roll** — `/roll` (d20), `/roll d100`, `/roll 2d6+1 [motivo]` com
  decomposicao dos dados, anunciado por proximidade.
- **Lore Zones** — `/lorezone criar|texto|som|remover|listar` (admin): regioes
  que mostram titulo, subtítulo e som quando um jogador entra (1x por sessao).

### Corrigido
- Conflito do `/me` com o emote vanilla (remocao do comando vanilla na arvore).

## [1.2.1] - 2026-09-04

### Corrigido
- **Bug crítico**: a tag com "negrito matemático" Unicode (𝐑𝐏) era montada com
  cast para `char`, truncando códigos acima de U+FFFF — o jogo exibia sílabas
  coreanas no lugar da tag.

### Alterado
- Investigada a fonte do Minecraft 1.21.1 (Unifont 15.1): o bloco
  "Mathematical Bold" (U+1D400) **não existe** nela e mostraria `□□`.
- A tag agora usa **small capitals** confirmadas glifo a glifo no Unifont:
  **(ʀᴘ)** em ciano e **(ᴏꜰꜰ ʀᴘ)** em cinza — renderiza em qualquer cliente.

## [1.2.0] - 2026-09-03

### Adicionado
- Tag entre parênteses e com fonte diferente (tentativa inicial com 𝐑𝐏).
- Apenas a tag recebe cor; o nome do jogador fica na cor normal.

## [1.1.0] - 2026-09-03

### Adicionado
- Nametag redesenhado no cliente com pastilhas arredondadas ("bolinho"):
  nome em placa escura + tag colorida ao lado (verde/cinza na época).
- Proteção contra servidor sem o mod (não desenha tag sem sync).

## [1.0.0] - 2026-09-03

### Adicionado
- Tag `(RP)` / `(OFF RP)` no nome: chat, TAB e nametag.
- Comando `/rp` (alternar), `/rp on|off|status` para todos e
  `/rp set <jogador> on|off` para admins.
- Estado persistido no mundo (`SavedData`), padrão OFF RP.
- Sincronização servidor → cliente via payload próprio.
