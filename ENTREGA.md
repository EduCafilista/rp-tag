# RP Tag 5.45.0 — os cinco pedidos da rodada

## 1. O guarda-chuva na mão (de novo — e agora com a conta certa)
**Você:** *"EDITA DIREITO ESSA UMBRELA EU TE PEDI"*.

Na sua foto a cúpula aparecia **na altura das pernas** — ou seja, o guarda-chuva estava
apontando **pra baixo**. Fui atrás do motivo de verdade: a conta que a 5.44.1 usou para escolher
a rotação partia de uma **cadeia errada da mão** (`X(-90)` onde o jogo usa **`X(+90)`**). Com o
eixo invertido, `-100°` parecia "pra cima" no papel e era "pra baixo" no jogo. A prévia offline
mentia junto, porque usava a mesma conta torta.

Agora, a cadeia real (braço · **X(+90)** · Y(180) · display):
```
+Y do modelo depois de X(a):   (0, cos a, sin a)
depois de Y(180):              (0, cos a, -sin a)
depois de X(90):               y' = sin a ,  z' = cos a
querendo PRA CIMA (0,1,0):     sin a = 1  ->  a = 90°
```
→ **rotação `[80, 0, 0]`** (os mesmos 90° com 10° de inclinação pra frente).
**Translação zero** nas duas mãos e nas duas pessoas: o modelo é desenhado em volta da origem do
item, que o jogo põe **na mão** — não existe distância pra ele fugir. Aberto e fechado usam a
mesma rotação (abrir não muda de lugar).
Prévia conferida com o corpo do lado: `arte/previa_pose.png` (cúpula em **Y = 1,16**, a cabeça
termina em 0,50).

## 2. Os raios do `/vazio ligar`
**Você:** *"não aparecer na tela do jogador com poder ativado, os raios ficam atrapalhando a visão"*.
Quem **é** o vazio não vê **raio nenhum** na própria tela (antes via 2/3 deles). Quem olha de fora
continua vendo a aura inteira.

## 3. A roda de habilidades
- **Nada de mensagem.** O aviso *"Os poderes do vazio não respondem a você"* foi **apagado**, e a
  recusa do servidor também é muda. Sobrou só o estalo do botão.
- **Os poderes voltam a sair.** O cliente tinha um espelho **velho** das regras do servidor
  (bastava estar no vazio ou ser ADM). O servidor, desde a 5.44.2, exige o dom ligado — então a
  roda abria, você escolhia, e o poder morria calado no servidor. Agora o cliente **não decide
  nada**: só obedece o pacote do servidor. Escolher **fecha a roda** e arma; o **botão direito**
  solta o poder.

## 4. O rastro do Juízo
**Você:** *"no juízo, faça um rastro para o player seguir, assim encontrar o portal"*.
O chão do labirinto acende um **rastro de almas** (fogo de alma) à frente do jogador, apontando
para a saída: um ponto a cada ~1,4 bloco, redesenhado 5×/s, **para na parede** (é labirinto, tem
de contornar) e, perto do portal, ganha **brasas vermelhas** — quanto mais quente, mais perto.

## 5. O terremoto virou começo de lua de sangue
**Você:** *"o céu deve começar a ficar vermelho... essa lua/SOL do local... tem que tombar a tela"*.
- **Céu vermelho:** um **domo de 150 blocos** em volta da câmera, vinho no alto e vermelho vivo
  no horizonte — o céu inteiro muda, não é filtro de tela.
- **Sol e lua de sangue:** desenhados **na posição real** do sol e da lua do mundo (mesma conta do
  vanilla), então nascem e se põem no lugar certo. A lua é a da sua referência: disco escuro,
  **rachaduras de lava que pulsam**, halo vermelho e **borda acesa**.
- **Neblina vermelha** no horizonte (`ComputeFogColor`).
- **A tela tomba de verdade:** ~**9° de inclinação** constante (respirando devagar) + o tremor que
  já existia — o mundo fica torto o tempo todo.
- Tudo isso só existe **enquanto o terremoto está ligado** (vem do pacote do servidor) e é
  desenhado *antes* do terreno, então nada de céu pintado por cima do mundo.

## Provas
`check.py`: **1391 PASS / 0 FAIL**. Jar **37.164.594 B** (não emagreceu).
Servidor de teste: `RP Tag 5.45.0`, `Done (1.550s)!`, **0 exceções**.

## Arquivos
- `VazioPoderes.java` (recusa muda), `client/PoderLeque.java` (cliente obedece o servidor, sem
  mensagem), `client/RaiosVazioFx.java` (sem raios pra quem tem o poder).
- `JuizoRastro.java` (novo) + `JuizoFinal.saidaDe()`.
- `client/CeuDeSangueFx.java` (novo) + `client/NeptuneFx.java` (estado do terremoto + inclinação).
- `arte/importar_umbrella_obj.py` (rotação 80° e a conta certa) + os 16 modelos +
  `arte/previa_pose_umbrella.py` (cadeia corrigida).
