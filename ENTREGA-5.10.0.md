# ENTREGA 5.10.0 — CONHECIMENTO novo + mod 30 MB mais leve

**Data:** 02/11/2026
**Jar:** `rptag-5.10.0.jar` — **69.292.343 B (66,0 MB)** · md5 **`a4fba42320ff31abbc90a0d93bbfe39f`**
**Download:** https://github.com/EduCafilista/rp-tag/releases/download/v5.10.0/rptag-5.10.0.jar
**Release:** 431719665 (v5.10.0) · asset 702475538
**Verificação:** `check.py` **776 PASS / 0 FAIL** · servidor `Done (0.752s)`, 0 exceções

---

## 1) O aviso CONHECIMENTO virou o do protótipo novo

`/rp admin avisar @a conhecimento` — estilo 16.

O que aparece, na ordem:

| tempo | o que acontece |
|---|---|
| 0,0 s | o preto abre; fundo âmbar escuro no centro |
| 0,3 s | fios radiais, anéis 172/178/146, os 72 tracinhos do aro e a **banda de 48 runas** |
| 0,8 s | a **estrela de seis pontas** (dois triângulos girando ao contrário) e os **quadrados** |
| 1,0 s | as **4 colunas de runas** das bordas começam a subir |
| 1,3 s | anéis internos 92/72 e a segunda banda (30 runas) |
| 1,4 s | o **olho** abre (pisca a cada 7,5 s) |
| 2,0 s | as **cordas** crescem do anel até os alfinetes |
| 2,4 s | as **4 memórias** aparecem penduradas |
| 2,8 s | a **balança SANIDADE × CONHECIMENTO** |
| 3,0 s | o contador **VERDADES** |
| 3,4 s | **CONHECIMENTO** letra por letra + as duas frases |
| 4,3 s | 1ª memória: a barra enche e a **verdade** aparece |
| 6,2 s | 2ª verdade |
| 8,1 s | 3ª verdade |
| 10,0 s | 4ª verdade → **clarão, onda grande e o texto troca** |
| 15,6 s | escurece e fecha |

**As quatro verdades** (escritas dentro das memórias, digitadas):

- *"Você já esteve aqui antes."*
- *"Seu nome não foi escolha sua."*
- *"Alguém lembra de você. Você não lembra."*
- *"As vozes sempre foram uma só."*

**A adaptação do mouse:** no protótipo você passa o mouse 1 s em cima da memória
para ela abrir. No jogo não tem mouse — então **a barra enche sozinha** (1,9 s por
memória) e **o olho olha para a memória que está sendo revelada**. Nada de clique,
nada de tecla: o aviso roda inteiro sozinho.

**A balança:** quanto mais verdades abertas, mais pesa o CONHECIMENTO e mais cai a
SANIDADE (o prato da esquerda sobe, o da direita desce). No fim da conta:
CONHECIMENTO 72–100%, SANIDADE 55–100%.

**Diferença de layout (de propósito):** as duas memórias de cima desceram de
y=168/160 para y=212/196. No protótipo elas ficavam **embaixo** da balança e do
contador VERDADES, que tapavam a verdade escrita. Agora nada fica escondido.

---

## 2) O mod emagreceu 30 MB

O peso era **100% quadro de vídeo**: `assets/rptag/fx` tinha **95,5 MB** de JPEG;
o resto do mod (classes, sons, texturas, geometrias) é ~10 MB.

| pasta | antes | depois |
|---|---|---|
| gelo | 30,9 MB | **18,4 MB** |
| egipcio | 21,8 MB | **18,0 MB** |
| void | 15,1 MB | **10,6 MB** |
| inferno | 13,5 MB | **8,4 MB** |
| morte | 9,1 MB | **5,2 MB** |
| tablet_fundo | 5,1 MB | **4,3 MB** |
| **total** | **95,5 MB** | **65,0 MB** (−32%) |

**Recursos do mod:** 105 MB → **75 MB**
**Jar:** 99,9 MB → **66,0 MB**

**O que foi feito** — e o que NÃO foi (opção "leve" que você escolheu):

- **Não mexi na resolução.** Tudo continua **1280×720**, mesmo nome, mesmo
  formato, mesmo caminho. O `FxTimeline` só aceita 1280×720 e o `VideoFrameLoader`
  só usa o caminho rápido (10 ms por quadro) com esse formato — mudar tamanho
  exigiria mexer no motor do vídeo.
- **Só recompressei:** cor em qualidade **72** com Huffman otimizado e JPEG
  progressivo; a máscara de alpha em **55** (ela é quase preto-e-branco, aceita
  bem mais aperto).
- **Se o arquivo novo ficasse maior, o original ficava** — 324 quadros foram
  mantidos como estavam.
- **Erro médio medido:** 1,6 a 2,9 em 255. A 1:1 na tela é indistinguível — a
  prévia `previa/otimizacao_5.10.0.png` mostra antes × depois lado a lado e um
  recorte ampliado 3× no pixel.
- O script está no repo: `arte/otimizar_frames.py` (rode com `--medir` pra só
  conferir, sem gravar).

**Se um dia quiser mais:** baixar os quadros para 960×540 economizaria mais ~20 MB,
mas aí muda o motor do vídeo (`FxTimeline` + `VideoFx.blit` + os 6 `fx.json`).
Fica pra quando você pedir.

---

## 3) Como testar

1. Entre no servidor com o `rptag-5.10.0.jar` (cliente e servidor).
2. `/rp admin avisar @a conhecimento`
3. Olhe: sigilo → cordas → memórias abrindo sozinhas → verdades → balança pesando
   → texto troca no final. Dura ~17 s.
4. Se quiser repetir, rode o comando de novo (o aviso novo sempre recomeça do zero).

---

## 4) Arquivos desta entrega

- `previa/conhecimento_5.10.0.png` — 4 momentos do aviso (2,2 s / 6,0 s / 10,4 s / 14,0 s)
- `previa/otimizacao_5.10.0.png` — antes × depois dos quadros, com recorte 3×
- `arte/otimizar_frames.py` — o script da otimização
- `arte/previa_conhecimento.py` — o gerador da prévia do aviso
- `src/main/java/dev/rptag/client/ConhecimentoAvisoFx.java` — o aviso (reescrito)
