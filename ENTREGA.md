# RP Tag 5.50.0 — pedido 107

**Jar:** `rptag-5.50.0.jar` (37.322.808 bytes, md5 `9dd6a933465b2ab5a2512de75f648009`)
**Servidor:** NeoForge 21.1.249 / Java 21 — `RP Tag 5.50.0`, `Done (1.692s)`, 0 exceções
**Provas:** `python3 check.py` → **1415 PASS / 0 FAIL** · auditor do mod: **0 ERRO**

---

## O guarda-chuva: na mão, PARA CIMA e ACIMA DA CABEÇA — sem comando

Você: *"não quero comando para configurar a umbrella, quero que você arrume para mim, faça para
ficar no local certo da mão, e quando aberto PARA CIMA, E ACIMA DA CABEÇA"*.

**O comando `/umbrella` e o arquivo de ajuste foram APAGADOS.** A pose agora é calculada.

### As três contas

**1. A mão (posição exata).**
`PlayerRenderer.getModel().translateToHand(braco, pose)` — a mesma função que o Minecraft usa
para pôr **qualquer** item na mão. A origem do modelo (o pé do cabo) cai onde o jogo desenha a
mão. Não existe mais "quase na mão".

**2. Para cima (sem ângulo chutado).**
O braço cai, o corpo gira e o modelo do jogador é espelhado, então "para cima" muda de direção a
cada pose. Em vez de escolher um ângulo na tentativa, o código **lê a matriz da mão**:

```
u = M⁻¹ · (0, −1, 0)      // o "para cima" do mundo, medido no espaço da mão
Q = rotationTo((0,1,0), u) // gira o +Y do guarda-chuva para lá
```

O `+Y` do modelo (cabo → cúpula) passa a apontar para o céu **em qualquer pose**: andando,
agachado, de lado, com o outro braço ocupado. É isso que o deixa "PARA CIMA" sem ajuste manual.

**3. Acima da cabeça (sem soltar o cabo).**
O modelo cru tem **0,906 bloco** de altura e a mão fica a ~0,75. O aberto usa escala **1,50**:

| | |
|---|---|
| topo da cúpula | 0,75 + 0,906 × 1,50 ≈ **2,11** (a cabeça acaba em 1,80) |
| centro da cúpula | ≈ **1,80** — encostando em cima da cabeça |
| largura | 0,507 × 1,50 ≈ **1,52 bloco** de diâmetro |
| o cabo | continua **na mão** (a origem não sai do lugar — quem cresce é o guarda-chuva) |

O **fechado** tem escala própria (**1,15**): 0,954 × 1,15 ≈ 1,10 bloco, um bastão curto na mão —
não faz sentido um guarda-chuva recolhido do tamanho de uma porta.

### Primeira pessoa
O `display` de 1ª pessoa também foi endireitado (rotação 0, um leve 6° de inclinação): o cabo
aparece no canto de baixo e a cúpula sobe pela direita da tela, em vez do guarda-chuva deitado.

### O que continua valendo da 5.48.0
- a roda de poderes é uma **tela** de verdade (clique escolhe e fecha, botão direito usa na hora);
- o rastro do vazio usa textura com **alfa de verdade** (não esconde mais o mundo atrás);
- a prévia do balão só amplia em **2× ou 1×**, com posição arredondada e sem sombra dobrada;
- o item **Porte de Arma** (`/give @s rptag:porte_de_arma`).

## Pendente
- O **redesenho das 5 molduras do editor de balão** (pedido antigo, ainda na fila).
