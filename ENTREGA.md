# RP Tag 5.48.0 — pedido 105

**Jar:** `rptag-5.48.0.jar` (37.321.196 bytes, md5 `e36f8b3c37c1cbc3993b68b25a8f2a98`)
**Servidor:** NeoForge 21.1.249 / Java 21 — subiu limpo (`RP Tag 5.48.0`, `Done (1.129s)`, 0 exceções)
**Provas:** `python3 check.py` → **1408 PASS / 0 FAIL**

---

## 1. O guarda-chuva na mão — agora ancorado no OSSO da mão

Você: *"ainda não está na mão? não fica na minha mão também a umbrella"*.

Cinco versões tentando acertar a mão pelo bloco `display` do modelo (`thirdperson_righthand`) e
sempre errava por um pedaço: essa cadeia tem seis matrizes (corpo, pivô do braço,
`translateToHand`, o giro de 90/180 do item na mão…) e era **tentativa**, não conta.

**Agora o mod desenha o guarda-chuva ele mesmo** (`client/UmbrellaNaMao.java`), em
`RenderPlayerEvent.Post`:

1. `PlayerRenderer.getModel().translateToHand(braco, pose)` — a **mesma função do jogo** que põe
   qualquer item na mão. A origem do modelo (o cabo) cai exatamente onde o jogo põe a mão;
2. dali ele é levantado (180° na base) e recebe os ajustes;
3. é desenhado no contexto `FIXED`, que no modelo é **identidade** — quem posiciona é o mod.

No modelo, `thirdperson_righthand/lefthand` ficaram com **escala 0**: senão o vanilla desenharia
um segundo guarda-chuva por cima deste.

### Se ainda não estiver do jeito que você quer, você acerta em 10 segundos — sem jar novo

Como eu não vejo o seu jogo, cada número virou comando de cliente (fica salvo em
`config/rptag-umbrella.txt` e volta na próxima sessão):

```
/umbrella                      mostra os valores atuais
/umbrella pos 0 -0.1 0         desloca em blocos (x y z)
/umbrella rot 0 0 -20          gira em graus (x y z)
/umbrella escala 1.4           tamanho
/umbrella virar                vira a cúpula (se aparecer pra baixo)
/umbrella reset                volta ao padrão
```

Se você mandar um print depois de ajustar, eu **gravo os seus números como padrão** na próxima
versão e o comando deixa de ser necessário.

## 2. Menu dos balões — as letras bugadas

Você: *"melhore a qualidade do menu dos balão, tem letras ficando bugadas"*.

Eram **duas** coisas somadas na prévia do balão:

| O que estava feio | Por que | Correção |
|---|---|---|
| letra borrada / "tremida" | o zoom caía em **1,5× / 1,2× / 0,95×** — a fonte do Minecraft é de pixel, e num fator fracionário cada texel da letra cai no meio de um pixel da tela | o zoom agora é **só 2× ou 1×** (2× quando cabe, 1× quando a fala é longa) |
| letra "duplicada"/contorno gordo | a **sombra** do texto é desenhada 1 unidade ao lado **no espaço do zoom** — em 2× ela saía 2 px deslocada e 2 px grossa | a prévia ampliada ficou **sem sombra**; no balão de verdade (que não tem zoom) a sombra continua |
| texto "andando" | a posição caía em pixel fracionário | toda posição do texto da prévia é **arredondada** |

## 3. A roda de habilidades — virou TELA de verdade

Você: *"a roda de habilidades ainda está bugada, corrija: após selecionar o poder, vai fechar ela,
e logo após isso, vai poder usar o poder escolhido"*.

O defeito era estrutural: a roda era só um **desenho por cima do HUD** que, para o mouse
funcionar, soltava e prendia o cursor na mão (`releaseMouse()` / `grabMouse()`). Daí vinham os
sintomas: clique que caía no vazio, câmera girando quando o cursor voltava, e o botão direito
esperando uma borda de clique que nunca vinha.

Agora (`client/RodaPoderes.java`) a roda é um **`Screen`**, igual ao inventário:

- o jogo entrega cursor, posição e clique — nada de soltar mouse na mão;
- **clicar numa casa arma o poder e fecha na hora**;
- ao fechar, o Minecraft **prende o mouse de volta sozinho** — então o próximo clique direito já é
  do jogo e **usa o poder escolhido**;
- as teclas **1–6** escolhem, **R** e **ESC** fecham;
- `isPauseScreen()` falso: o mundo continua rodando (não é menu de pausa);
- a rede de segurança do botão direito é **rearmada** ao escolher (`direitoAntes` passa a refletir
  a tecla naquele instante), então o clique seguinte nunca é engolido.

O desenho é o **mesmo** de antes — mudou só quem manda no mouse.

## 4. O rastro do vazio — não esconde mais o mundo

Você: *"o rastro do vazio está ficando bugado e deixando as coisas atrás invisível"*.

Achei a causa exata: o rastro era desenhado com `RenderType.debugQuads()`, que desenha **sem
transparência** — o alfa do vértice é jogado fora. Então as "manchas de escuridão" saíam
**pretas e opacas** por cima do chão, tapando o que estava atrás. Não era bug de geometria: era o
tipo de desenho.

- o rastro agora usa `RenderType.text(textura)`, que **mistura de verdade** (alfa vale);
- duas texturas novas (`arte/gerar_rastro.py`): `rastro_mancha.png` (borrão radial, alfa zero na
  borda — sem canto) e `rastro_lasca.png` (rachadura em zigue-zague com halo quente);
- a "costura" entre duas marcas virou um quad esticado da mesma textura, então o rastro continua
  um traço contínuo — só que agora **transparente**.

## 5. O que ficou pendente

- **Primeira pessoa** do guarda-chuva: a 3ª pessoa está resolvida por âncora; a 1ª pessoa ainda
  usa o `display` antigo. Se estiver errada aí também, eu aplico o mesmo truque no
  `RenderHandEvent` (mesma âncora, mesma conta).
- O **redesenho das 5 molduras do editor de balão** (pedido antigo, ainda na fila).
