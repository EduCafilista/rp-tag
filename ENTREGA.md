# RP Tag 5.62.0 — o que vinha de novo no zip: FORMA MACACO + RUGIDO

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.62.0/rptag-5.62.0.jar
(também na sua área de trabalho como `rptag-5.62.0.jar`)

---

## O que eu adicionei (e o que pulei)

Você mandou: "adiciona o que estiver no arquivo, o que for repetido você não add". Comparei o zip
novo com o que já estava no mod:

**JÁ TINHA (não dupliquei):** a aura de iluminação (círculo dourado, coluna, hélices, raios,
portões), o poder dos Portões da Babilônia, as armas douradas, a tecla, os comandos `/iluminacao`.
O zip velho tinha também 8 estilos de aura (saber, archer, lancer...) e o `/aura` — **não** entraram,
porque você pediu **uma** aura só (`/iluminacao`).

**NOVO (entrou agora):**

### 1. Forma Macaco — `/macaco`
- **5x maior** (escala 5.0), passo alto (sobe blocos andando), alcance de bloco 8 e de entidade 6;
- **Regeneração II, Força III e Resistência III infinitas**;
- se reaplica sozinha a cada 2 s (relog, leite, morte) enquanto estiver ligada;
- `/macaco off` e aceita vários alvos: `/macaco @a`, `/macaco Ana,Beto`, `/macaco off @a`;
- **precisa de OP (nível 2)**.

### 2. Rugido do Macaco — tecla **V**
- onda sonora que **se expande de verdade**, 50 blocos pra cada lado (100 de diâmetro), levando 3 s
  pra chegar na borda — cada alvo só é atingido quando a frente da onda chega nele;
- **dano 30 colado → 5 na borda** (dano de sonic boom);
- até 25 blocos: **atordoado** (imobilizado + sem pulo + tontura) por 8 s colado → 3 s no limite;
- empurrão pra fora, zunido agudo que vai sumindo em quem foi atingido, ecos do grito;
- **20 s de recarga**, aviso de "garganta cansada" com o tempo que falta;
- **só o Macaco ruge** (fora da forma, avisa e não gasta recarga);
- visual: anéis correndo pelo chão **seguindo o relevo**, parede de pressão, clarão + pilar de luz e
  **tremor de câmera**.

> A tecla do rugido é **V**, não R: no RP Tag a **R** já é "Poderes do Vazio". Dá pra trocar em
> Controles → RP Tag → "Rugido do Macaco".

## Provas
- `check.py`: **1377 PASS / 0 FAIL** (11 provas novas);
- `arte/auditar.py`: **0 erro**;
- **servidor de teste subiu com a 5.62.0**: `/macaco` e `/iluminacao` registrados, `Done`, zero
  exceção no log.

> O canal de rede subiu de **20 para 21** (pacote do rugido + pacote do visual) — servidor e cliente
> precisam estar na MESMA versão.
