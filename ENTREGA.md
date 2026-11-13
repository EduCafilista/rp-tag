# RP Tag 5.43.0 — o guarda-chuva PRA CIMA na mão, os poderes com o vazio, e a tela limpa

**O arquivo para baixar:**
[rptag-5.43.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.43.0/rptag-5.43.0.jar)
· 37.179.657 bytes

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.43.0

> **Tire o jar antigo da pasta `mods`** — no servidor **e** no cliente.

---

## 1. O guarda-chuva agora fica na mão apontando PRA CIMA

Você: *"o guarda chuva deve ficar na mão e para cima, como se o player estivesse se escondendo
do sol, está bugando o antigo no meu peito"*.

O modelo estava **deitado na frente do peito** porque o número que veio no pacote deixa o eixo
dele na horizontal. Desta vez eu **calculei** em vez de chutar: montei a cadeia de transformação
do item na mão (braço em repouso → X(−90) → Y(180) → o `display` do modelo) e procurei a rotação
que deixa o **+Y do modelo apontando pra cima**. A família que resolve é **(−180, −90, −80)** —
os 80° de Z compensam o braço ficar um pouco à frente.

Resultado: o cabo fica **no punho** e a cúpula sobe **acima da cabeça** — o desenho de quem se
esconde do sol. Escala 0,72 (antes 0,95, era o que fazia ele cobrir o peito).

O **fechado** continua na pose normal de carregar (deitado no punho), como qualquer item.

## 2. Como pegar as outras cores

```
/umbrella dar <jogador> <1 a 8>      a cor na mão do jogador
/umbrella cores                      a lista das 8
/umbrella dar <jogador>              vai na cor "sangue" (1)
```

| # | cor | # | cor |
|---|---|---|---|
| 1 | sangue | 5 | veneno |
| 2 | noite | 6 | luar |
| 3 | juízo | 7 | ouro |
| 4 | sombra | 8 | rosa |

E também dá para **craftar** (3 fios + 3 lã + 1 graveto) ou **tingir** o que você já tem com o
corante certo — as duas receitas estão no jogo.

## 3. Poderes: agora NÃO ativam com o vazio desligado

Você: *"o ativar poderes ainda tá bugando, consigo ativar até com vazio desligar"*.

O defeito: fora do vazio bastava **ser ADM** para os poderes responderem — e você **é** ADM,
então eles respondiam sempre, mesmo depois do `/vazio desligar`. Agora:

- **dentro do Vazio e do Reino do Juízo:** o dom é do lugar (funciona sempre);
- **fora deles:** só com o vazio **ligado** (`/vazio ligar`). Desligou, os poderes param na hora
  e a roda fecha sozinha.

## 4. Terremoto: a tela ficou limpa

Você: *"tira esse trem da tela no terremoto"*. Saiu **tudo** que era desenhado por cima da tela
durante o terremoto: a nebulosa, a poeira, as faixas de visão dupla e a rachadura. Ficou o chão
tremendo (o tremor da câmera) e o som. Se um dia quiser de volta, é uma constante no código.

## 5. Raios do vazio: AMARELO e NEGRO

Você: *"o vazio ligar tem que mudar a cor dos raios, cor amarela, negro"*. A paleta dos raios
virou: **capa preta** (larga, some contra o cenário escuro), **corpo amarelo** e **núcleo
amarelo-claro**. O texto do `/vazio ligar` também avisa a cor nova.

## 6. Balão de fala: legível em qualquer cor

Você: *"consegue melhorar o design do /balao? tá muito feio, melhore para ficar mais legível"*.
O texto era desenhado **sem sombra e sem contorno**: em balão claro a letra clara sumia. Agora
cada letra ganha um **contorno de 1px na cor oposta** — texto claro com contorno escuro, texto
escuro com contorno claro. Lê sobre qualquer balão e sobre qualquer fundo do mundo, sem borrar
(é contorno, não sombra difusa).

---

## Testes

- `check.py`: **1332 PASS / 0 FAIL** (bloco novo 94-h/98, incluindo a prova que **calcula** a
  direção do guarda-chuva e exige que ela aponte pra cima)
- `arte/auditar.py`: 0 erro
- Servidor: `RP Tag 5.43.0 (rptag)` → `Done (1.492s)!`
