# RP Tag 5.13.3 — O PORTAL DOS 8 PEDESTAIS

**O arquivo para baixar:**
[rptag-5.13.3.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.13.3/rptag-5.13.3.jar)
· 69.363.773 bytes · `md5 230c28ee7cc8cb3f4cb02f3d0aba1ba6`
· `sha256 ef0993afac359f27689cdf5d65fac6fbb7d69c6b6759afc692260076616317df`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.13.3

> **Troque o jar no servidor E no cliente.** O bloco do portal e a animação são os dois
> lados: servidor coloca a escuridão de baixo para cima, cliente desenha o vórtice.

---

## O que você falou

> "o rptag 5.10.1 ainda funciona a dimensão, o pessoal conseguindo usar e tudo certinho, era só
> melhorar o que eu te pedi, blocos mais escuros, e a correção de todo o resto"
> "mesmo ativando os 8 pedestais não abriu o portal animado aonde eu te pedi, um portal para o
> player que for preso sair"

Então: **nada de conceito novo**. O que mudou aqui é só o que estava impedindo o portal de abrir.

---

## Por que o portal não abria (a causa raiz)

Os 8 fragmentos chamavam `abrirPortal`, e essa função pegava a posição do portal por
`ancoraPortal(nv)` — que devolve **o portal INVOCADO primeiro**, se existir um. Ou seja:

1. qualquer `/vazio portal` feito **dentro do vazio** (mesmo uma vez, para testar) gravava um
   portal invocado no save, **para sempre**;
2. a partir daí, o 8º fragmento abria **aquele** portal (que podia estar longe, dentro de uma
   parede, num canto qualquer) — e não o da praça;
3. se esse portal invocado fosse desmontado por outro `/vazio portal`, o registro velho ficava
   e o portal "abria" num lugar onde não havia moldura nenhuma.

E, pior: o **portal animado** (o desenho do vórtice) seguia a mesma posição errada. Resultado:
os 8 pedestais acesos, nenhum portal na praça.

## O que foi feito

| Correção | O que muda no jogo |
|---|---|
| **`ancoraPortalPraca`** | o caminho dos 8 fragmentos é **sempre** o portal da praça. Portal invocado virou só ferramenta de teste e não tira mais a vez dele. |
| **Rede de segurança** | se você (ou um jogador) tem os **8 nos pedestais** e o portal da praça está fechado, ele **abre sozinho no próximo tick** — não depende de acertar o clique no bloco certo. |
| **Reparo automático do miolo** | portal aberto que perdeu bloco (chunk descarregado no meio da animação, criativo, `/fill`) **se reconstrói em até 5 s**. E, importante: o reparo **espera a animação terminar** — antes ele enchia as 7 linhas no 1º segundo e matava a abertura de baixo para cima. |
| **Praça e invocado animam cada um por si** | antes, se existia um invocado, só ele era animado (`baseAberta`, `estadoPortal` por base). |
| **Atravessar vale para os dois** | quem encosta no miolo da **praça** sai (com os 8 fragmentos); quem encosta no **invocado** atravessa livre. |
| **`/vazio pedestais encher`** | enche os 8 na hora e abre o portal da praça. É o teste de 1 segundo: se isso abre, o problema era só o clique; se não abrir, o `relatorioPortal` diz onde está. |
| **`/vazio pedestais` com relatório** | mostra: coordenadas da praça, quantos blocos de escuridão existem no miolo (0/35 = fechado, 35/35 = aberto), se o mundo acha que está aberto, se você tem os 8 e se existe portal invocado atrapalhando. |

## O que fazer no seu mundo (1 minuto, sem perder nada)

```
/vazio construir          # refaz praça, 8 pedestais, altares e o portal no lugar certo
/vazio pedestais          # relatório: praca / miolo / aberto / invocado
/vazio pedestais encher   # testa na hora: 8 pedestais + portal abrindo
```

Se o `/vazio pedestais encher` abrir o portal, **está resolvido** — o problema era a posição que
o 8º fragmento estava escolhendo. Se não abrir, o `relatório` (linha "praça / miolo / aberto")
diz exatamente qual etapa falhou — me manda essa linha que eu fecho o resto.

---

## Verificação técnica (servidor de teste 5.13.3)

- `javac` Java 21: **0 erros**. `check.py`: **862 PASS / 0 FAIL** (8 checagens novas: `ancoraPortalPraca`,
  rede de segurança, reparo + espera da animação, praça/invocado por base, `/vazio pedestais encher`,
  relatório).
- Servidor subiu com `RP Tag 5.13.3`, **0 exceções** no log.
- Abertura medida no servidor real: **2 linhas em 2 s → 7 linhas em 10 s** (animação de 6 s
  intacta, reparo não interferindo).
- Reparo: bloco apagado em `0 13 -22` voltou **em ~5 s** (log `miolo do portal reparado (1 blocos faltando)`).

## O que NÃO mudou

Nada do que você aprovou: a dimensão iluminada, a névoa roxo-acinzentada, o bloco `escuridao`,
a aura preta sem quadrados, o rastro, o passo só no chão, a cidade nascendo de pé na chegada e a
volta no ponto exato de entrada.
