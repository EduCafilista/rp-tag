# ENTREGA 5.9.0 — TÍTULOS com cor modificável (TAB, chat e acima da cabeça)

**Download:** https://github.com/EduCafilista/rp-tag/releases/download/v5.9.0/rptag-5.9.0.jar
`rptag-5.9.0.jar` · 99.930.695 bytes · md5 `6e9f6f3cdbb6df1678209bd2e33fc05d`
NeoForge **1.21.1** · Java **21** — **jar novo no cliente E no servidor** (o canal mudou pra v12).

---

## Respondendo direto

**Sim, os títulos existem** — desde a 5.7.2, e aparecem nos **três** lugares:

| onde | exemplo |
|---|---|
| **TAB** (lista de jogadores) | `[Chronos] Nephisto [O Ferreiro] (RP)` |
| **chat** | `[Chronos] Nephisto [O Ferreiro]: a membrana não se rompe hoje.` |
| **acima da cabeça** (nametag) | o mesmo texto, virado pra câmera |

E **a cor agora é modificável** (era o que faltava: na 5.8.1 a etiqueta só usava a
cor de fábrica da paleta e o título pessoal era sempre branco).

---

## Como usar

```
/titulo definir <jogadores> <etiqueta>        →  [Chronos] na frente do nome
/titulo personalizado <jogadores> <texto>     →  Nome [O Ferreiro]
/titulo cor <jogadores> <cor>                 →  pinta a ETIQUETA
/titulo cor pessoal <jogadores> <cor>         →  pinta o [O Ferreiro]
/titulo limpar <jogadores>                    →  tira tudo
/titulo ver <jogador>                         →  mostra tudo + as cores em HEX
/titulo paleta                                →  as 12 etiquetas prontas
/titulo lista                                 →  quem tem título
```

`<jogadores>` aceita **vários nomes** ou `@a` em `definir`, `cor` e `limpar`.

### Cores aceitas
- **por nome (25, português e inglês):** `ouro` `dourado` `amarelo` `laranja`
  `vermelho` `vermelho-escuro` `rosa` `roxo` `lilas` `azul` `azul-escuro`
  `ciano` `aqua` `menta` `verde` `verde-escuro` `branco` `preto` `cinza`
  `cinza-escuro` (e os nomes em inglês: `gold`, `red`, `blue`, `pink`…)
- **HEX livre:** `FFD700` · `#FFD700` · curto `#FFF`
  (não precisa de aspas — o argumento é o resto da linha)
- **`padrao`** → volta pra cor de fábrica da etiqueta
- cor **inválida** (ex.: `xyz`) **avisa** e não pinta nada

O **TAB** do comando sugere as cores: `/titulo cor @a <TAB>`.

### Exemplos prontos
```
/titulo definir @a Chronos
/titulo cor @a lilas
/titulo personalizado Nephisto O Ferreiro
/titulo cor pessoal Nephisto ouro
/titulo cor Steve #FF2D95
```

---

## Paleta pronta (`/titulo paleta`)

| etiqueta | cor | etiqueta | cor |
|---|---|---|---|
| `[Sagrado]` | `#FFD75E` dourado | `[Sombra]` | `#9A6BFF` roxo |
| `[Warden]` | `#6FB3FF` azul | `[Divino]` | `#FFE9A8` creme |
| `[Gelo]` | `#9BE8FF` ciano | `[Morte]` | `#FF7B7B` vermelho |
| `[Fragmento]` | `#38E0A6` menta | `[Vazio]` | `#8C8C9E` cinza-azulado |
| `[Almas]` | `#2FE0C0` água | `[Ferreiro]` | `#E0A860` bronze |
| `[Chronos]` | `#C9A6FF` lilás | `[Player]` | `#BFBFBF` branco sujo |

Qualquer outra palavra também vale (fica cinza-claro) — e pode ser pintada com
`/titulo cor`.

---

## Detalhes que evitam dor de cabeça

- **Salvo no mundo** (`rptag_titulos`): reiniciar o servidor não perde nada.
- **Preto puro** (`#000000`) funciona de verdade (antes era lido como "sem cor").
- **Rede**finir a **mesma** etiqueta mantém a cor que você escolheu; **trocar** de
  etiqueta volta pra cor de fábrica da paleta.
- O título aparece junto do **(RP)/(OFF RP)** e do nome da persona.
- Nada de `§` no texto: o comando limpa; etiqueta até **16** caracteres, título
  pessoal até **24**.

---

## O que foi verificado aqui

- `check.py`: **765 PASS / 0 FAIL** (10 checks novos do 5.9.0)
- teste da função de cor: `ouro → #FFAA00`, `#FFD700 → #FFD700`, `fff → #FFFFFF`,
  `preto → #000000`, `xyz → NULL (avisa)`, `Chronos` de fábrica `#C9A6FF`
- servidor 5.9.0: `Done (0.732s)`, **0 exceptions**; `/titulo paleta` responde;
  `/titulo definir @a Chronos`, `/titulo cor @a #FFD700`, `/titulo cor pessoal @a ouro`
  parseiam (sem ninguém online aparece "No player was found" — é esperado)
- jar rebaixado do GitHub e conferido (md5 igual, 5 classes de título dentro)

**Prévia:** `previa/titulos_5.9.0.png` (TAB + nametag + chat + paleta + comandos).
