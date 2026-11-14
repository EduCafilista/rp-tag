# RP Tag 5.52.0 — fim do balão fantasma

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.52.0/rptag-5.52.0.jar
(também está na sua área de trabalho como `rptag-5.52.0.jar`, 37.029.932 bytes)

Instalar: jogue o `.jar` em `.minecraft/mods` (NeoForge 1.21.1 / Java 21). Apague o jar antigo.

---

## O balão duplicado no canto — o que era

Na imagem dava pra ver o balão normal ("estes") e **outro** em cima, apagadinho, no canto.
Não era um balão extra desenhado por engano: era o **balão anterior da pilha** (o que sobe pra
cima da cabeça quando você fala duas vezes seguidas) **morrendo**.

O código do fade tinha `Math.max(0.1F, ...)`: o alfa de saída nunca chegava a zero, parava em
**10%**. Resultado: o balão antigo não sumia — ficava na tela um contorno fantasma de 10% de
opacidade, com a letra invisível, parecendo um segundo balão bugado.

Agora:
- o fade vai **até zero** (`Mth.clamp`, sem piso de 0,1);
- balão com tempo esgotado **não entra na fila de desenho** (`if (remaining <= 0) return`);
- balão com alfa abaixo de 4% **não é desenhado** (`ALFA_MINIMO`) — nada de resíduo na tela.

## O que continua igual

- letra nítida (sombra de 1 pixel de tela, da 5.51.0);
- guarda-chuva continua fora do mod;
- `check.py`: **1344 PASS / 0 FAIL**;
- compila limpo e servidor NeoForge 1.21.1 sobe sem exceção (1293 receitas, 1399 advancements).

Jar conferido baixando o link acima: md5 `ca31681d70dae752899a47415f248db9`.

> A release da 5.51.0 foi apagada — só a mais nova fica no ar.
