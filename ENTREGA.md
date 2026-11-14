# RP Tag 5.47.0 — pedido 104

**Jar:** `rptag-5.47.0.jar` (37.311.346 bytes, md5 `71ff1aaa08995f96560d276509223ee7`)
**Servidor:** NeoForge 21.1.249 / Java 21 — subiu limpo (`RP Tag 5.47.0`, `Done (1.736s)`, 0 exceções)
**Provas:** `python3 check.py` → **1401 PASS / 0 FAIL**

---

## 1. O céu — refeito do zero (o que estava feio e como morreu)

Suas palavras: *"esse céu está bonito pra você? tá horrível, muda o céu, deixa mais caótico"*.
As duas coisas que apareceram na sua foto tinham duas causas separadas:

| O que você viu | Por que acontecia | O que eu fiz |
|---|---|---|
| **faixas concêntricas** | o domo era feito de anéis com **cor fixa por anel** — cada anel virava um degrau | a cor agora é **por vértice** (`corCeu`): o degradê interpola dentro de cada quadrilátero, não existe mais degrau |
| **um quadrado em volta da lua** | o halo eram **3 quads chapados** | o halo virou uma **textura de degradê radial** (`halo_de_sangue.png`) — o alfa chega a ZERO na borda, então não tem canto |
| **veias em faixas de borda dura** | eram 22 quadriláteros de alfa fixo | virou uma **casca contínua** (`veia()`), com alfa **por vértice**: a veia nasce e morre sem borda |
| **azul vazando / roxo** | o domo tinha alfa baixo (0,42–0,76) e o céu azul do vanilla aparecia por baixo | o domo agora é **opaco** (0,90–1,00) |

E para ficar **caótico** de verdade (não um degradê de papel de parede):
- **nuvens de sangue rolando** numa casca a 142 blocos (ruído de senos animado, alfa por vértice);
- **veias escuras** atravessando o céu numa casca a 146 (elas se torcem com o tempo);
- **pulsação** (~2 por segundo) e **clarão** a cada 7 s, que lava o céu inteiro de laranja;
- a névoa do horizonte fica vermelha junto.

**Prova visual:** `arte/previa_ceu.png` — o `arte/previa_ceu.py` reproduz em Python a MESMA conta
do Java (`cor_ceu`, `nuvem`, `veia`, o aro e o halo de textura) e renderiza como fica na tela.
Se mudar a conta no Java, muda aqui também: é a prova de que não sobrou faixa nem quadrado.

## 2. A lua — de volta à sua referência (veios de lava)

Você disse que eu mudei completamente o que você mandou. A receita voltou a ser a da referência,
e agora ficou melhor que a 5.46.0:

- pedra de sangue (fBm em 3 oitavas, vinho quase preto → vermelho vivo) + crateras escuras;
- **rachaduras de lava com RAMOS finos** saindo das grossas (antes eram 18 veios soltos da mesma
  grossura — parecia um prato de macarrão; agora lê como rachadura em pedra);
- o **miolo do veio chega perto do branco-amarelo** (lava de verdade, não tinta laranja);
- halo em volta de cada veio, vinheta de esfera e **aro aceso** na borda.

## 3. O Fluxo — só o bloco e o fragmento

*"deixa só o bloco e o fluxo shard, tira as buddy horriveis"* — feito. O mod registra **duas** peças:

```
rptag:fluxo_block      → Bloco de Fluxo
rptag:fluxo_shard      → Fragmento de Fluxo
```

Saiu do jar: as texturas, os modelos, os blockstates, as tabelas de drop, a receita e os nomes
dos 3 brotos + do cacho + do bloco em formação. Confirmado no servidor de verdade por RCON:

```
setblock 0 -55 3 rptag:fluxo_block        → Changed the block at 0, -55, 3
setblock 0 -55 4 rptag:fluxo_cluster      → Unknown block type 'rptag:fluxo_cluster'
setblock 0 -55 5 rptag:fluxo_bud_pequeno  → Unknown block type 'rptag:fluxo_bud_pequeno'
summon … {id:"rptag:fluxo_shard"}         → Summoned new Fluxo Shard
summon … {id:"rptag:fluxo_block"}         → Summoned new Block of Fluxo
```

## 4. A umbrella — "quase acertando": mais 1/16 para cima e para o lado do corpo

A pose que você aprovou quase toda foi mantida (`[95,0,0]`), só a translação andou:

| | 5.46.0 | **5.47.0** |
|---|---|---|
| translação (1/16 de bloco) | `[-1, 1, 7]` | **`[-2, 0, 8]`** |
| cabo (a mão fica em Y=0,75) | Y=0,74 | **Y=0,81** |
| centro da cúpula | (0,37 / 1,92 / −0,08) | **(0,31 / 1,98 / −0,02)** |

Ou seja: subiu 1/16, foi 1/16 para o lado do corpo (a cúpula ficou mais centrada na cabeça) e
1/16 para trás (estava um pouco à frente do rosto). Aberta e fechada continuam com a MESMA rotação
e a MESMA translação. Prévia: `arte/previa_pose.png` (a conta vem da cadeia real do jogo).

## 5. O que ficou pendente

- O **redesenho das 5 molduras do editor de balão** (pedido antigo, ainda na fila).
