# RP Tag 5.43.1 — a cúpula do guarda-chuva AGORA ACIMA DA CABEÇA

**O arquivo para baixar:**
[rptag-5.43.1.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.43.1/rptag-5.43.1.jar)
· 37.153.067 bytes · `md5 8a21324be4f1c4d76d1d6c21a72a46b7`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.43.1

> **Tire o jar antigo da pasta `mods`** — servidor **e** cliente.

---

## O que estava errado (e por que eu errei duas vezes)

Na 5.43.0 eu acertei o **eixo** (o guarda-chuva deixou de ficar deitado) mas errei a **altura**:
a cúpula continuou na linha do peito e caía pro lado — exatamente o que você viu.

O motivo: eu conferi que o modelo apontava "pra cima" olhando só a **direção** do eixo, e não
**onde** a cúpula caía depois de somada a posição do braço.

Agora a conta é outra: o programa pega os **4120 vértices** do seu `.obj`, aplica a cadeia real
do item na mão (`braço em repouso → X(−90) → Y(180) → rotação do modelo → translação em 1/16 de
bloco`), separa os **2859 vértices da cúpula** e mede onde o centro dela foi parar. Aí procura a
pose que deixa esse centro **acima da cabeça**.

**Resultado:** rotação `[−180, −90, −90]` e translação `[−6, 17, 8]`.

- antes (5.43.0): centro da cúpula em **Y = −0,07** → na altura do peito
- agora (5.43.1): centro da cúpula em **Y = 0,86** → acima da cabeça (que termina em 0,50)

**Prévia do antes e depois, com o corpo de referência:** `arte/previa_pose.png`

---

## O que mudou no jogo

- **Aberto:** o cabo fica no punho, a cúpula abre **acima da cabeça**, centralizada no corpo —
  o desenho de quem se esconde do sol.
- **Fechado:** continua na pose normal de carregar (deitado no punho), como qualquer item.
- **Primeira pessoa:** a cúpula fica em cima e à direita, sem tapar a visão.
- Só o **aberto** tem essa pose; é o único que muda de modelo.

---

## Como não errar a terceira vez

Entrou uma prova no `check.py` que **mede** a pose em vez de confiar em mim: ela aplica a mesma
cadeia de transformação nos vértices do `.obj`, acha o centro da cúpula e **falha o build** se ele
não estiver acima de Y = 0,55 nem centralizado no corpo. O defeito do "peito" e o do "pra baixo"
não passam mais por ela.

E a prévia `arte/previa_pose_umbrella.py` agora desenha o **corpo do jogador junto** — a prévia
antiga mostrava o guarda-chuva sozinho no vácuo, e foi por isso que eu não vi o erro.

---

## Testes

- `check.py`: **1336 PASS / 0 FAIL** (4 provas novas medindo a pose)
- Servidor: `RP Tag 5.43.1 (rptag)` → `Done (1.054s)!`
