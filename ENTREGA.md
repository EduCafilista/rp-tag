# RP Tag 5.46.0 — as cinco correções da rodada

## 1. Raios do `/vazio ligar` — agora é só na PRIMEIRA pessoa
**Você:** *"os raios ainda devem aparecer, no f5, so nao deve aparecer na primeira pessoa"*.
Feito: em **F5 (3ª pessoa) o vazio vê a própria aura**, igual todo mundo. Só na **1ª pessoa** não
aparece raio nenhum (era o que atrapalhava a visão). O corte de 2/3 de antes continua fora.

## 2. O guarda-chuva — o problema do player (resolvido com conta, não com chute)
**Você:** *"a umbrella continua bugada na questão do player"*.

Parei de adivinhar e **simulei a cadeia real do jogo** (a mesma conta que o Minecraft faz para
desenhar item na mão em 3ª pessoa):

```
mundo = T(0,1.5,0) · S(-1,-1,1)          origem do jogador -> espaço do modelo
      · T(-5/16, 2/16, 0) · RX(braço)    pivô do braço direito
      · T(-1/16, 7/16, 1/16)             translateToHand
      · RX(-90) · RY(180)                ItemInHandLayer
      · T(1/16, 2/16, -10/16)            posição do item na mão
      · T(translação/16) · R(rotação) · S(escala)      display do modelo
```

**Ela foi validada nas suas duas fotos**: com `-100°` (5.44.1) ela prevê cabo em Y=0,31 e cúpula
em Y=−0,82 (nas pernas, a sua primeira foto); com `80°` (5.45.0) prevê cabo em Y=0,31 e cúpula em
Y=1,44 (longe da cabeça, a foto de agora). Ou seja: a conta bate com o jogo.

Resolvendo para **cabo na mão + guarda-chuva vertical + cúpula acima da cabeça**:
**rotação `[95, 0, 0]`** e **translação `[-1, 1, 7]`** (1/16 de bloco). Medido:
- **cabo em Y=0,74** — a mão fica em 0,75;
- **cúpula de 1,70 a 2,19** — a cabeça termina em 1,80, então a cúpula cobre a cabeça;
- eixo do guarda-chuva: `(0; +1,60; −0,02)` → **vertical**.

O fechado usa a **mesma** rotação e translação (só a escala em Y muda, para virar um bastão de
~0,95 bloco em vez de um poste de 1,5). A prévia com o corpo do lado está em
`arte/previa_pose.png` (a 5.45.0 do lado esquerdo, a nova do lado direito).

## 3. Ametista vermelha MUITO mais vermelha
**Você:** *"a ametista vermelha deve ser MAIS VERMELHA, muito mais vermelha"*.
A 5.44.0 só girava a matiz e **mantinha a saturação** — e a ametista do Minecraft é lavada, então
saía **rosa/salmão** (RGB médio 192, 99, 103). Agora a saturação é empurrada para 0,92+:
**RGB médio 185, 3, 7** — sangue. As facetas (claro/escuro da pedra) continuam as originais; a
prova no `check.py` mede a correlação da estrutura, não a cor chapada.
Montagem das 6 texturas: `arte/fluxo_5.46.0.png`.

## 4. Os brotos de Fluxo agora COLOCAM como bloco
**Você:** *"as bud eu nao consigo colocar como um bloco"*.
Era isso mesmo: o `AmethystClusterBlock` da vanilla **recusa** qualquer apoio que não seja a
ametista em formação (`canSurvive` falso) — o broto sumia no tick seguinte. Os quatro (3 brotos +
cacho) agora usam `FluxoClusterBlock`, que aceita qualquer apoio: dá para decorar chão, parede e
teto. Testado no servidor: `/setblock` e os blocos **continuam lá** depois de ticks.

## 5. A lua do terremoto — virou TEXTURA (a sua referência)
**Você:** *"a 'Lua' do terremoto, melhore isso, corrija esse erro, coloque essa lua"*.
A 5.45.0 desenhava a lua com discos em código e virou uma bola vermelha chapada com uma estrela de
rachaduras no meio. Agora:
- `textures/environment/lua_de_sangue.png` e `sol_de_sangue.png`, gerados por
  `arte/gerar_lua_de_sangue.py`: **esfera de pedra de sangue** (ruído fractal em 3 oitavas +
  granulado fino), **veios de lava que brilham** (rachaduras por segmento, com halo), **manchas
  escuras** de cratera e **aro aceso** — a mesma receita da referência animada que você mandou;
- o céu ganhou **48 anéis** no domo (eram 12 — por isso as faixas concêntricas na sua foto):
  agora o degradê é liso;
- o sol (de dia) usa a versão mais quente da mesma textura, no lugar exato do sol do vanilla.

## Provas
`check.py`: **1396 PASS / 0 FAIL**. Jar **37.281.656 B** (não emagreceu).
Servidor de teste: `RP Tag 5.46.0`, `Done (1.622s)!`, **0 exceções**; `/setblock` dos brotos
conferido com `execute if block` depois de ticks.

## Arquivos
- `client/RaiosVazioFx.java` (corte só na 1ª pessoa).
- `arte/importar_umbrella_obj.py` (pose 95° / tr [-1,1,7] + a cadeia escrita) e os 16 modelos;
  `arte/previa_pose_umbrella.py` (simulação da cadeia real).
- `arte/gerar_fluxo.py` (saturação) + 7 texturas novas; `FluxoAmetista.java`
  (`FluxoClusterBlock`).
- `client/CeuDeSangueFx.java` (textura + domo liso); `arte/gerar_lua_de_sangue.py` e as 2 texturas.
