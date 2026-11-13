# RP Tag 5.43.2 — mod otimizado (o balão parou de ser recalculado a cada quadro)

**O arquivo para baixar:**
[rptag-5.43.2.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.43.2/rptag-5.43.2.jar)
· 37.154.886 bytes · `md5 35a88e5a9ad173b69c878935b5dab742`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.43.2

> **Tire o jar antigo da pasta `mods`** — servidor **e** cliente.

---

## O que eu medi antes de mexer (medido, não chutado)

**Servidor** — subi o mesmo NeoForge duas vezes, sem ninguém na casa, e medi 40 s de cada:

| | sem o mod | com o mod |
|---|---|---|
| CPU ociosa | 15,78% de 1 núcleo | 12,95% a 17,00% (mesma faixa) |
| "Can't keep up" | 0 | 0 (o único registro do 5.43.1 foi na geração do spawn) |
| Pacotes por tick | — | 0 |

O servidor já estava no jeito: as zonas de lore checam **1× por segundo** (não 20×), o AFK usa
varredura espaçada e **nenhum** arquivo junta handler de tick com varredura de mundo.

**Cliente** — aqui estava o desperdício. O `BubbleRenderer` refazia, **em todo quadro** e para
cada balão de cada jogador, a quebra de linha + `Stickers.styled()` + `font.width()` por palavra.
Medi a peça principal isolada: **0,26 a 0,88 µs por texto**, chamada **uma vez por palavra** —
e, com balões na tela, isso roda 60× por segundo sem parar.

O detalhe que fecha o assunto: a "máquina de escrever" termina em ~2 s e **depois disso o texto
não muda mais**. Era conta jogada fora, 60 vezes por segundo, até o balão expirar.

---

## O que mudou

| onde | antes | agora |
|---|---|---|
| Layout do balão | recalculado **todo quadro** | **cacheado**; recalculado só quando muda o texto visível |
| Altura da pilha | quebra de linha inteira por balão por quadro | sai do cache |
| Largura das linhas | `font.width` por segmento por quadro | sai do cache |
| Array de alturas | `new int[]` por jogador por quadro | array reaproveitado |
| Objeto `Look` da geometria | `new` por balão por quadro | um só, reaproveitado |
| Banner LORE ON/OFF | 4 `replaceAll` (compilando regex) por quadro | texto sem cor calculado **uma vez**, sem regex |
| Memória | — | cache poda sozinho os balões que já expiraram |

**O visual não mudou nada** — mesmas linhas, mesmas cores, mesmo typewriter, mesmo pop-in, mesmo
fade. Mudou só *quantas vezes* a conta é feita.

**Ganho real:** depois que a fala termina de ser digitada, o custo de layout vai a **zero**; durante
a digitação, cai de 60 para ~36 cálculos por segundo. Em servidor de RP com uma dezena de balões na
tela, é a maior parte da CPU que o mod gastava no cliente.

---

## O que deixei de propósito

Os **37 MB do jar** continuam iguais (você pediu pra manter): 35 MB são quadros de vídeo lidos sob
demanda, em thread própria, fora da thread principal — **não custa FPS nem TPS**, só download.

O relatório completo, com as medições: `arte/relatorio-desempenho.md`.

---

## Testes

- `check.py`: **1350 PASS / 0 FAIL** (14 provas novas travando o cache no lugar)
- `arte/auditar.py`: 0 erros, 14 avisos (todos antigos, conhecidos)
- Servidor: `RP Tag 5.43.2 (rptag)` → `Done (1.494s)!`, nenhuma exceção, 0 "Can't keep up"
- Guarda-chuva: pose da 5.43.1 mantida (cúpula acima da cabeça, medida em Y=0,86)
