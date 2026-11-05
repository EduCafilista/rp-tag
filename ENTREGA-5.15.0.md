# RP Tag 5.15.0 — O LOUCO AGORA É O CAMINHO INTEIRO (a escada das 10 Sequências)

**O arquivo para baixar:**
[rptag-5.15.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.15.0/rptag-5.15.0.jar)
· 69.424.900 bytes · `md5 5d76d93afd85a26a6c02823d1eaca3fe`
· `sha256 c3112a24467d7229f1a427699c375683e22f171af8ab0363bfcb2b0122df32a6`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.15.0

> **Troque o jar no servidor E no cliente.** O desenho é do cliente; o comando é do servidor.

---

## Como usar

```
/rp admin avisar @a louco Um grande perigo se aproxima.
```

(qualquer seletor: nome, lista ou `@a`; aceita **texto livre**, que aparece digitado na tela)

**Para testar sozinho, sem passar pelo servidor:** `/rpfx teste louco`
— se aqui aparece e no comando de admin não, o problema é rede/comando, não o desenho.

**Nos avisos por zona:** `/lorezone aviso <id> louco <segundos> <texto>`.

---

## 1. O que mudou nesta versão

Saí o aviso antigo do Louco (névoa cinzenta + sigilo de 22 caminhos + relógio + lua carmesim).
**Entrou o protótipo definitivo do dono, o `FoolPathwayWarning`**: um aviso só, de 16 segundos,
em que **a escada das Sequências sobe de 9 até 0** e **cada Sequência acende o seu próprio efeito**.

A carta do Louco é o último degrau — é ela que fecha o aviso.

Nada mais mudou no resto do mod: o Marinheiro (26) e o Espectador (27) continuam exatamente
como na 5.14.0.

---

## 2. A escada das Sequências (o coração do aviso)

Dez nós em círculo. O nó **9** acende em 1,4 s e cada seguinte acende **0,5 s** depois;
o nó **0 (O Louco)** acende em **5,9 s** — junto com o clarão pálido, e é aí que a carta termina
de subir e começa a virar.

Cada nó acende com um **estouro de faíscas** na cor dele, e o nome aparece ao lado de fora.

| # | Sequência | o que liga quando ela acende |
|---|---|---|
| 9 | **Vidente** | o **pêndulo** — peso de pedra balançando do alto, com rastro e brilho |
| 8 | **Palhaço** | quatro **bonecos de papel** balançando nas bordas, moles, sem parar |
| 7 | **Mago** | **chamas** nas duas bordas da tela, tremendo e soltando brasa |
| 6 | **Sem Rosto** | cinco **máscaras** orbitando: metade passa **na frente** da carta, metade **atrás** |
| 5 | **Marionetista** | **fios espirituais** descendo do alto até a carta |
| 4 | **Feiticeiro Bizarro** | o **anel de maldição** em volta do centro, com as marcas em X e brasas subindo |
| 3 | **Estudioso do Passado** | o **tempo corre ao contrário**: dois relógios girando para trás e os **ecos** da carta |
| 2 | **Invocador de Milagres** | **pilares de luz** subindo das laterais |
| 1 | **Atendente dos Mistérios** | duas **mãos de névoa** subindo das bordas de baixo |
| 0 | **O Louco** | **a carta** — sobe, vira, e é o clarão que fecha a escada |

Os nós já acesos ficam **brilhando** (e o nome deles fica claro); os que ainda não acenderam
ficam apagados, quase invisíveis. Dá para "ler" a escada subindo na tela.

---

## 3. A linha do tempo (16 s, roda inteiro sozinho)

| tempo | o que acontece |
|---|---|
| 0,0 s | escurece; a névoa de fundo abre |
| 1,0 s | a **carta** começa a subir do rodapé |
| 1,4 s | acende a **Sequência 9 (Vidente)** — e o pêndulo entra |
| 1,9 s → 5,4 s | acendem as Sequências **8, 7, 6, 5, 4, 3, 2, 1**, uma a cada 0,5 s |
| 2,4 s | a carta terminou de subir e fica flutuando |
| 5,9 s | **clarão pálido** + acende a **Sequência 0 (O Louco)** |
| 5,9 – 6,8 s | a carta **vira** (virada em 3D, sem sumir) |
| 7,0 s | **clarão carmesim** + entra o **título** |
| 8,2 s | 1ª fala (escrita letra por letra) |
| 9,5 s | 2ª fala |
| 11,0 s | 3ª fala — a frase final |
| 14,4 s | começa a escurecer |
| 16,0 s | fecha |

**As falas** (as do protótipo, as mesmas do texto-base):

- *"Todo fio leva ao mesmo lugar: ao Louco."*
- (o seu texto, o que você escreveu no comando, digitado)
- *"Você já era uma marionete."*

O **texto do comando** entra no meio, digitado, e o resto do aviso continua rolando por trás dele.

---

## 4. O que se mantém das regras que você pediu

- **sem partícula** — nada de partícula por perto; tudo é desenhado na tela;
- **sem vídeo** — não usa quadro de vídeo, é tudo vetor (por isso o mod continua leve);
- **só o alvo vê** — o pacote vai para quem foi escolhido no seletor;
- **respeita o modo reduzido** — com os efeitos reduzidos ligados, o aviso desenha mais leve
  (menos faíscas, menos fios, sem os brilhos extras) e o texto continua igual;
- **letras claras sobre o escuro** (aqui o aviso é escuro de propósito, então o texto é branco —
  é o único aviso do mod que não usa letra preta, porque o fundo dele não é claro).

---

## 5. Correções menores que entraram junto

- O log do cliente dizia **"RP Tag 4.8.5: camada de avisos carregada"** (texto velho, escrito
  na mão). Agora ele diz **a versão de verdade** — `RP Tag 5.15.0: camada de avisos carregada`.
- `check.py` atualizado: as réguas do Louco agora verificam **a escada das 10 Sequências**
  (os dez nomes, os nove efeitos, o relógio de acendimento e o estouro de faísca no nó),
  e não mais a névoa/relógio da versão antiga.

---

## 6) Conferido antes de publicar

- `javac` (Java 21, NeoForge 21.1.249): **0 erros**;
- `check.py`: **906 PASS / 0 FAIL**;
- servidor de teste com o jar novo: **0 exceções**;
- `/rp admin avisar @a louco teste`, `... marinheiro teste`, `... espectador teste`:
  os três responderam (com 0 jogador online, "No player was found" — é o servidor reclamando
  que não há ninguém, não o comando falhando).

---

**Arquivos:** `rptag-5.15.0.jar` (instalar em `mods/` do servidor **e** do cliente).
