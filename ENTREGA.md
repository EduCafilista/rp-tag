# RP Tag 5.44.1 — o guarda-chuva voltou pra MÃO

**O arquivo para baixar:**
[rptag-5.44.1.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.44.1/rptag-5.44.1.jar)
· 37.157.235 bytes · `md5 8c99abc31c2536eaea781fb090be48a5`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.44.1

> **Tire o jar antigo da pasta `mods`** — servidor **e** cliente.

---

## O que estava errado (de verdade, dessa vez)

Não era o número da rotação. Era a **geometria**: os `.obj` do pacote estão com o centro em
`(0,5, 0,5)` e o cabo em `y=0,03` — ou seja, **deslocados da origem do item**. Todo modelo de
item é desenhado em volta da origem, que o jogo põe **na mão**. Com o modelo deslocado, cada
rotação/translação virava uma **alavanca**: o guarda-chuva girava em volta de um ponto que não
era o cabo dele e ia parar no ar, a um bloco do corpo. Foi isso nas três tentativas
(5.43.0, 5.43.1 e 5.43.4).

## O que mudou

1. **Geometria centralizada** — o gerador agora reescreve os `.obj` com o **cabo na origem**
   (X e Z em volta de 0, Y de 0 pra cima). Isso está travado por prova no `check.py`.
2. **Translação ZERO** no display da 3ª e da 1ª pessoa. Sem translação não existe distância
   para o guarda-chuva se afastar: ele é desenhado exatamente em volta da mão.
3. **Rotação calculada, não chutada** — querendo o `+Y` do modelo (cabo → cúpula) apontando
   para cima, a conta `-0,1736·cos a - 0,9848·sin a = 1` dá **a = -100°**. É esse número que
   está no modelo agora, com a conta escrita no gerador.
4. **Escala 1,6 em Y** só para a cúpula passar da cabeça (o modelo tem 0,9 bloco de altura).
5. O **fechado** usa a mesma rotação: abrir não muda o objeto de lugar, ele só desenrola.

Prévia da pose com o corpo do jogador do lado, pra dar escala: `arte/previa_pose.png`.

---

## Testes

- `check.py`: **1368 PASS / 0 FAIL** — incluindo 4 provas novas que travam isso:
  translação zero, geometria centrada no cabo, rotação `[-100, 0, 0]` e a conta no gerador
- `arte/auditar.py`: 0 erros · `arte/validar.py`: 54 ok, 0 falhas
- Servidor: `RP Tag 5.44.1 (rptag)` → `Done (1.149s)!`, **zero** exceções

**Se ainda não estiver na mão**, me manda o print: agora só sobra um número pra mexer
(a escala em Y), porque posição não existe mais — ela é zero.
