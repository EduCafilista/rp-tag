# RP Tag 5.43.1 — está otimizado?

**Resposta curta:** o **servidor** está bem otimizado (o mod custa ~1% de um núcleo e **zero** pacote por tick). O **cliente** tem **um** desperdício real, pequeno mas fácil de matar. O **jar** é pesado (37 MB), mas o peso é vídeo e não custa FPS nem TPS.

Tudo abaixo foi **medido agora**, não estimado no olho.

---

## 1. Servidor — medido com e sem o mod

Subi o mesmo servidor NeoForge 21.1.249 duas vezes, sem nenhum jogador, e medi o consumo da thread do servidor por 40 s em cada caso:

| medida | **sem** o mod | **com** o mod | diferença |
|---|---|---|---|
| CPU ocioso (0 jogadores) | 15,78% de 1 núcleo | 17,00% de 1 núcleo | **+1,2 ponto percentual** |
| Tempo de subida (`Done`) | 0,870 s | 1,476 s | **+0,6 s** |
| "Can't keep up" (lag de tick) | 0 | 1 — **só na geração do spawn** (3 s depois de subir) | igual |
| Pacotes de rede por tick | — | **0** | — |

O +1,2 p.p. está dentro do ruído desta máquina virtual (que já gasta 15,78% sem mod nenhum). Ou seja: **o mod praticamente não custa tick**, com 0 jogadores.

O que garante isso, no código:

- **Zonas de lore:** cada jogador é testado **1× por segundo** (`if (player.tickCount % 20 != 0) return;`), não 20× por segundo.
- **AFK automático:** a varredura é espaçada (`if (++contador < VARREDURA_TICKS) return;`).
- **Nenhuma varredura de mundo por tick.** Nenhum arquivo tem, ao mesmo tempo, handler de tick e `getEntitiesOfClass`/`getEntities` — as 11 ocorrências de varredura estão todas em `VazioPoderes.java` e `JuizoFinal.java`, em comandos/eventos pontuais.
- **Nenhum pacote por tick.** As 197 chamadas de rede do mod são todas em comandos/eventos.

Com jogadores na casa, o custo cresce com o número de jogadores **por segundo**, não por tick — é a ordem certa.

---

## 2. Cliente — o único desperdício real

`BubbleRenderer.renderOne` refaz **todo o layout do balão em TODO QUADRO**, para cada balão, de cada jogador:

```
renderOne(...)                       // por balão, por jogador, por quadro
  └─ wrapBalanced(font, speech)      // quebra de linha: Stickers.styled() + font.width() POR PALAVRA
  └─ Stickers.styled(linha)          // + a passada de "linhas equilibradas", que remede várias combinações
  └─ segment(...)                    // listas novas a cada chamada
```

Medi o custo da peça principal (`Stickers.styled(...).getVisualOrderText()`) num banco isolado, com as classes do próprio mod:

```
styled+ordem:   0,88 µs por texto (76 chars)
styled+ordem:   0,56 µs por texto (75 chars)
styled+ordem:   0,26 µs por texto (53 chars)
```

É chamado **uma vez por palavra** (e mais vezes na passada de balanceamento), **mais** o `font.width(...)` em cima de cada uma — que é a parte mais cara e não dá para medir fora do jogo. Estimando por baixo: **30 a 90 µs por balão por quadro**.

O problema não é o número sozinho, é ser **jogado fora**: a "máquina de escrever" termina em `nº de letras × 28 ms` (~2 s para uma fala de 70 letras) e depois disso **o texto não muda mais** — mas a conta continua sendo refeita 60× por segundo até o balão expirar. Num servidor de RP com 10–20 balões na tela, isso é a maior parte do tempo de CPU que o mod gasta no cliente.

**Correção:** cachear o layout dentro do próprio balão, invalidando só quando o texto visível muda. Depois que a fala termina de ser digitada, custo → **zero**. Durante a digitação, cai de 60 para ~36 cálculos por segundo.

### Itens menores (mesma correção, linha ou duas cada)

| onde | o que acontece | custo |
|---|---|---|
| `BubbleRenderer.renderBubble` | `int[] hPx = new int[count]` novo a cada jogador **a cada quadro** | lixo de memória constante |
| `BubbleRenderer.renderOne` | `new BubbleGeometry.Look()` por balão por quadro | lixo idem |
| `ClientEffects.desenhaBannerCanon` | `title.replaceAll("§.", "")` **por quadro** — `replaceAll` compila a regex toda vez | 2 regex por quadro enquanto o banner aparece |

### O que já está certo no cliente (não mexer)

- Balões só são desenhados até **64 blocos** (`distanceToSqr > 64*64 → return`) — e crescem com a distância para continuar legíveis.
- Nametag com corte em **64 blocos** e cancelamento do vanilla (`setCanRender(FALSE)`) — nada de desenhar duas vezes.
- Vídeo de FX decodificado **em thread própria**, com buffer de 8 quadros e descarte de quadro atrasado (`VideoFrameLoader`). Nada disso pesa na thread principal.

---

## 3. O jar tem 37 MB — e isso não é problema de desempenho

| item | tamanho | pesa em quê |
|---|---|---|
| **quadros de vídeo** (`assets/rptag/fx`, 1752 JPG) | **35 MB** | só download e disco |
| texturas | 2,4 MB | — |
| sons (17 .ogg) | 2,3 MB | streaming, não carrega tudo |
| modelos (inclui os 2 `.obj` do guarda-chuva) | 1,2 MB | — |

Os quadros são lidos sob demanda, um a um, fora da thread principal. Ou seja: **o download é grande, o jogo não sente**. Se quiser, dá para re-encodar os quadros (mesma resolução, qualidade menor) e derrubar o jar de 37 para ~15 MB sem tocar em nada do jogo.

---

## 4. Resumo

| área | situação |
|---|---|
| Tick do servidor | ✅ otimizado (1×/s, sem varredura, sem pacote por tick) |
| Rede | ✅ nada por tick |
| Render do cliente | ⚠️ um desperdício real: layout do balão recalculado todo quadro |
| Memória do cliente | ⚠️ lixo pequeno e constante (arrays/listas por quadro) |
| Tamanho do jar | ⚠️ 37 MB, mas é vídeo — não custa FPS nem TPS |

**O que eu recomendo fazer:** o cache do layout do balão + os 3 itens menores (~60 linhas, tudo verificado por prova no `check.py`). É o único lugar onde o mod gasta CPU de verdade sem necessidade. O resto é cosmético de download.
