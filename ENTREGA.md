# RP Tag 5.16.0 — AS OITO CARTAS DE TARÔ (Eremita, Estrela, Julgamento, Lua, Mago, Morte, Sol, Torre)

**O arquivo para baixar:**
[rptag-5.16.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.16.0/rptag-5.16.0.jar)
· 69.481.747 bytes · `md5 9f402f0807ecfd557937cafaef10a100`
· `sha256 19f2e0fc83edba7f1969e5805161a19bd304fbc84f376c193c7b0ac479ff1773`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.16.0

> **Troque o jar no servidor E no cliente.** O desenho é do cliente; o comando é do servidor.

---

## Como usar

```
/rp admin avisar @a eremita    Um grande perigo se aproxima.
/rp admin avisar @a estrela    ...
/rp admin avisar @a julgamento ...
/rp admin avisar @a lua        ...
/rp admin avisar @a mago       ...
/rp admin avisar @a morte2     ...
/rp admin avisar @a sol        ...
/rp admin avisar @a torre      ...
/rp admin avisar @a tolo       ...
```

- **`tolo`** é a **mesma carta do Louco**: você mandou o arquivo do TOLO igualzinho ao do Louco
  (comparei byte a byte, é o mesmo programa), então ele abre o aviso do Louco (estilo 25). Se um
  dia você mudar a carta do Tolo, é só mandar o arquivo novo que eu separo.
- **`morte2`** é a **carta A MORTE** (XIII). O nome `morte` já era o aviso de morte antigo do mod
  (estilo 4) e eu não troquei o que já existia. Também aceita **`ceifador`** e **`morte_carta`**.
- Todos aceitam **texto livre** (aparece digitado no meio do aviso) e qualquer seletor (`@a`, nome,
  lista).
- **Para testar sozinho**, sem passar pelo servidor: `/rpfx teste eremita`, `/rpfx teste torre`, etc.
- Nos avisos por zona: `/lorezone aviso <id> eremita <segundos> <texto>` (os oito estão na lista).

---

## O que entrou

Cada arquivo que você mandou é o **mesmo programa** (um canvas de 800×600, uma "carta" que sobe,
vira e mostra o título AVISO e três linhas). Em vez de copiar o desenho "por fora", eu portei o
**motor do protótipo** para dentro do mod:

- um **canvas 800×600** igual ao seu (centro em 400,255), escalado para a tela do jogador;
- a **pilha de transformação** (`translate` / `rotate` / `scale` / `save` / `restore`) e o
  **alfa global**, como no JavaFX;
- as **primitivas do `GraphicsContext`**: `fillRect`, `fillOval`, `strokeOval`, `fillPolygon`,
  `strokePolyline`, `strokeLine`, `fillArc`, `quadraticCurveTo`, `bezierCurveTo`, estrela de 8
  pontas, hexagrama, glifos e os símbolos alquímicos.

Por isso **cada carta é quase uma cópia linha a linha do seu arquivo** — mesmas cores, mesmas
posições, mesmos tempos.

**O que TODAS as oito fazem igual** (é o esqueleto comum do protótipo):

| tempo | o que acontece |
|---|---|
| 0,0 s | escurece e abre o céu da carta |
| 0,3 s | entra o **sigilo de 22 caminhos** (anéis que se fecham, 22 glifos, as Sequências 9→0 girando ao contrário) |
| 1,6 s | a **carta** começa a subir do rodapé |
| 2,4 s | a carta para de subir e flutua |
| 3,2 – 4,1 s | a carta **vira** (virada em perspectiva) |
| 4,8 s | entra o **título AVISO** letra por letra, com o fio e a linha embaixo |
| 5,6 s | as **cinco cartas** começam a orbitar (metade na frente, metade atrás) |
| 5,0 – 11,5 s | as **palavras da carta** sussurram subindo pela tela |
| 6,0 s | 1ª fala (a frase da carta) |
| 7,4 s | **o seu texto**, digitado com cursor |
| 9,0 s | 3ª fala (a frase final) |
| 12,4 s | escurece e fecha em 14 s |

---

## 1. O EREMITA (IX) — estilo 28

Montanhas de neve em três camadas, aurora verde-azul, 60 estrelas piscando e a **lanterna
pendurada balançando** — ela acende a neve em volta (os flocos perto dela ficam dourados).
A carta: a estrela de seis pontas, a montanha, o cajado e a lanterna.
Falas: *"Na solidão da montanha, só a lanterna conhece o caminho."* / *"Ela já foi até você."*

## 2. A ESTRELA (XVII) — estilo 29

A **estrela de oito pontas** gigante no alto, 120 estrelas, a **constelação de sete** ligada por
linhas, a aurora e um risco de luz que passa de vez em quando. Os dois **rios de luz** descem das
bordas até a carta, com anéis pulsando na chegada. A carta: a mulher, os dois jarros e as sete
estrelinhas. Falas: *"Mesmo na noite mais escura, uma estrela sabe onde você está."* /
*"Não deixe a calma enganar você."*

## 3. O JULGAMENTO (XX) — estilo 30

As **asas gigantes** abertas (nove penas de cada lado, curvas fechadas), as **ondas douradas** do
som da trombeta, as **almas** subindo em espiral com halo, as penas caindo e nove feixes de luz.
A carta: o anjo com a trombeta, as nuvens e os túmulos abertos.
Falas: *"A trombeta soou: todos serão chamados a responder."* / *"Sua hora chegou."*

## 4. A LUA (XVIII) — estilo 31

A **lua cheia** enorme com as crateras e o halo, 80 estrelas, a **água prateada** com 14 reflexos,
os dois **lobos uivando** (com os anéis do uivo subindo) e as gotas caindo. No sigilo, as **oito
fases da lua** girando.
Falas: *"Sob a lua, nada é o que parece ser."* / *"Ouça o uivo ao longe."*

## 5. O MAGO (I) — estilo 32

A **chuva de runas** verdes caindo em 16 colunas, a **lemniscata** (o infinito) desenhada no céu
com uma **cauda de luz** percorrendo e soltando faíscas, e os **quatro elementos** (fogo, água,
terra e ar) girando em volta com o seu símbolo alquímico.
A carta: a mesa com o bastão, a taça, a espada e as moedas.
Falas: *"Como acima, assim abaixo: a vontade molda o mundo."* / *"O feitiço já foi lançado."*

## 6. A MORTE (XIII) — estilo 33 (`morte2`)

O **sol pálido** nascendo entre as duas torres, a **bandeira negra** tremulando com o emblema, a
**lâmina de luz** atravessando a tela de tempos em tempos e pétalas caindo (algumas vermelhas). No
sigilo, os treze X girando, um vermelho por vez.
Falas: *"Nada escapa do fim: tudo o que vive um dia se despede."* / *"A sua vez chegou."*

## 7. O SOL (XIX) — estilo 34

O **sol dourado** com 24 raios girando, as três **colunas de luz**, os dois girassóis, as penas
caindo e a poeira de luz subindo. No sigilo, doze sois pequenos girando.
Falas: *"Quando o sol nasce, nenhuma sombra se esconde."* / *"A luz já encontrou você."*

## 8. A TORRE (XVI) — estilo 35

O **raio** caindo na torre **cinco vezes** (2,2 s · 3,5 s · 4,8 s · 7,0 s · 9,2 s — cada um é um
raio novo, cheio de ramificações, com clarão), a **coroa arrancada voando**, os **destroços** e as
**duas figuras caindo**. As brasas sobem o tempo todo.
Falas: *"O que foi construído sobre a mentira não resiste ao raio."* / *"A torre já está caindo."*

---

## Regras que continuam valendo

- **sem partícula do jogo e sem vídeo**: é tudo geometria e texto do próprio mod (por isso o jar
  não cresceu quase nada: 61 KB);
- **só o alvo vê** (o pacote vai para quem foi escolhido no seletor);
- **respeita o modo reduzido** — com os efeitos reduzidos ligados o aviso desenha mais leve;
- o aviso **roda inteiro sozinho** (nada de clique nem tecla);
- **falha em um aviso nunca derruba a tela**: o desenho é protegido por `try/catch`.

---

## Correção achada nesta versão

O sorteio das palavras sussurradas guardava o texto num índice e lia em **outro** índice. Quando
os dois não batiam, vinha `null` e **o resto do aviso parava de desenhar no meio** (o título AVISO
e as falas sumiam). Achei isso rodando o mesmo código das cartas num canvas Java de teste, gerei
24 imagens e olhei uma por uma. Corrigido: agora cada palavra leva o próprio texto junto.

Esse teste virou uma ferramenta: `previa/` roda o motor das cartas fora do jogo e salva PNGs —
se você quiser, na próxima eu te mostro as imagens antes de publicar.

---

## Conferido antes de publicar

- `javac` (Java 21, NeoForge 21.1.249): **0 erros**;
- `check.py`: **919 PASS / 0 FAIL** (14 regras novas só para as cartas, incluindo "a morte2 não
  bate com a morte antiga" e "os estilos 28–35 não existiam antes");
- servidor de teste com o jar novo: **0 exceções**;
- `rp admin avisar @a <estilo> teste` para os **dez** nomes (os oito + `tolo` + `louco`): todos
  responderam (com 0 jogador online o servidor responde "No player was found" — é o servidor
  reclamando que não há ninguém, não o comando falhando);
- as oito cartas foram desenhadas e conferidas em imagem antes de entrar no jar.

---

**Arquivos:** `rptag-5.16.0.jar` (instalar em `mods/` do servidor **e** do cliente).
