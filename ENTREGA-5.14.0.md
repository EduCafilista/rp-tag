# RP Tag 5.14.0 — OS TRÊS AVISOS NOVOS: O LOUCO, O MARINHEIRO E O ESPECTADOR

**O arquivo para baixar:**
[rptag-5.14.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.14.0/rptag-5.14.0.jar)
· 69.420.302 bytes · `md5 3e189f1551e8855fca6d886c3e8ad0aa`
· `sha256 33d7dd7e0d7f07999894d5b2655757a1f3d03699fb4f1f1cd3478f1269298671`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.14.0

> **Troque o jar no servidor E no cliente.** O desenho é do cliente; o comando é do servidor.

---

## Como usar

```
/rp admin avisar @a louco Um grande perigo se aproxima.
/rp admin avisar @a marinheiro Um grande perigo se aproxima.
/rp admin avisar @a espectador Um grande perigo se aproxima.
```

(qualquer seletor: nome, lista ou `@a`; os três aceitam **texto livre**, que aparece digitado
na tela.)

**Para testar sozinho, sem passar pelo servidor:** `/rpfx teste louco`, `/rpfx teste marinheiro`,
`/rpfx teste espectador` — se aqui aparece e no comando de admin não, o problema é rede/comando,
não o desenho.

**Nos avisos por zona** (`/lorezone aviso <id> louco|marinheiro|espectador <segundos> <texto>`)
os três também estão na lista.

---

## 1. O LOUCO (estilo 25) — névoa cinzenta, sigilo, relógio

> *"Acima da névoa cinzenta, o Louco observa. Não pronuncie Seu nome."*

- **névoa cinzenta** em duas camadas (46 bolhas, metade passa na frente da carta);
- **anel de sigilo com os 22 caminhos** — cada casa ligada à nona seguinte, girando, com 22
  glifos na borda e as **sequências 9 → 0** rodando ao contrário;
- **relógio**: 60 tiques, ponteiro que "clica" um passo por segundo (com o anel de pulso na
  virada), e o pêndulo do destino;
- **a carta do Louco** sobe do fundo em 1,6 s, **vira em 3,2 s** (verso: olho dourado; face: o
  caminhante de costas sob a lua vermelha) — e cinco cartas começam a orbitar;
- **lua carmesim** subindo à direita a partir de 4 s, com halo e crateras;
- **seis pares de olhos** abrindo na névoa (piscam sozinhos), **poeira dourada** e **sussurros**
  ("quem é você?", "o relógio não para", "ele já sabe"…);
- **título AVISO** em ouro, letra por letra, e as falas **digitadas** (30 letras por segundo,
  cursor piscando).

## 2. O MARINHEIRO (estilo 26) — a tempestade

> *"Sobre o mar de névoa, o Enforcado escuta a tempestade. O trovão já chamou o seu nome."*

- **céu de tempestade** com 26 nuvens, **mar revolto** em 4 faixas de onda (a crista acende no
  relâmpago) e **chuva** que engrossa com o tempo;
- **raios de verdade**: cada descarga é gerada por **deslocamento de ponto médio** (4 refinações)
  e ganha **4 ramos** — desenhados em três passes (brilho azul, miolo claro, risco branco) e
  piscando;
- as descargas grandes caem em **2,2 / 3,7 / 4,8 / 6,9 / 9,0 / 10,6 s** — a de **4,8 s é a
  principal**: clarão na tela, **tremor** da imagem e o trovão;
- **leme de navio** girando dentro do anel de sigilo (8 raios + 32 tiques + pinos), com **arcos
  elétricos correndo pelo anel**;
- **bolas de raio**: cinco esferas flutuando com estalos saindo delas;
- **a carta do Enforcado** (forca, corda, o pendurado) sobe, vira, e as cartas orbitam;
- **faíscas elétricas**, névoa do mar, sussurros ("afunde", "maré negra", "a tempestade sabe"…).

## 3. O ESPECTADOR (estilo 27) — o céu de sonho

> *"Entre sonhos e pensamentos, a Justiça vê o que você esconde. Ela já leu a sua mente."*

- **céu de sonho** rosa e lilás, com 70 estrelas piscando e **aurora** em três cores (3 passes
  cada, somados em modo aditivo);
- **espiral hipnótica**: três braços girando e **ondas mentais** que expandem do centro;
- **a balança** (mastro, haste inclinada, os dois pratos com a bacia) balançando — e inclinando
  mais quando a carta vira;
- **anel de olhos**: 12 olhinhos girando na borda do sigilo, cada um piscando no seu tempo, e
  **seis olhos grandes** flutuando nos cantos (a íris segue o centro da tela);
- **a carta da Justiça** (XI: a figura, o olho na mão, a espada) sobe, vira e as cartas orbitam;
- **bolhas de sonho**, **pétalas** caindo (giram e viram de lado), **brilhos** em cruz e
  **sussurros** ("não minta", "o que você esconde?", "ela vê"…).

---

## O que é igual nos três (e por quê)

- **Nada de partícula.** Tudo é geometria e texto desenhados pelo próprio mod — quem joga com
  "partículas: mínimo" vê exatamente o mesmo aviso.
- **Nada de vídeo.** Não pesa no download, não depende de codec, não trava em PC fraco.
- **Duração de 14 s** cada, com entrada e saída suaves (sem tela preta longa, sem flash branco).
- **Um aviso por vez**: quando outro aviso chega, o anterior sai pela saída dele (não ficam dois
  desenhando um por cima do outro).
- **Só o alvo vê.** Nada é mandado para o chat do admin nem para o servidor inteiro.
- **Acessibilidade**: se você ligou o modo reduzido do aviso de ciberpsicose, os três respeitam —
  menos piscada, menos clarão (no Marinheiro o flash do relâmpago cai para 40% e o tremor some).

## Novidades técnicas (para quem cuida do código)

- `AvisoRitual.java` — o que o JavaFX tinha e o GUI do jogo não: **halo radial, polígono,
  polilinha, arco, degradê vertical/horizontal e texto escalado**. Ficou barato de propósito: a
  névoa desenha 46 halos por quadro, então o halo usa 6 anéis × 14 lados (antes eram 10 × 20, o
  que gastava ~18 mil triângulos só de névoa).
- `LoucoAvisoFx`, `MarinheiroAvisoFx`, `EspectadorAvisoFx` — cada um com o seu `iniciar/parar`,
  registrado em `registerAboveAll`.
- `check.py`: **901 réguas**, todas passando.

> **Aviso sobre os números**: os estilos **25, 26 e 27** foram escolhidos porque 15, 16 e 17 já
> eram da Energia, do Conhecimento e do AllForOne. Se algum dia você vir um aviso trocado,
> é sinal de número repetido — não é isso aqui.

---

## Como atualizar

1. Baixe o `rptag-5.14.0.jar` no link do topo.
2. Jogue em `mods/` **do servidor** — apagando o jar antigo.
3. Jogue em `mods/` **do cliente** — apagando o jar antigo.
4. Entre no jogo e teste com `/rpfx teste louco` (não precisa de admin nem de servidor).
