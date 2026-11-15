# RP Tag 5.57.0 — fonte do balão limpa (sombra de 1 pixel)

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.57.0/rptag-5.57.0.jar
(também na sua área de trabalho como `rptag-5.57.0.jar`, 37.029.806 bytes)

---

## O que estava errado na fonte

O texto duplicado fora do balão já morreu (5.56.0). O que sobrou foi a **distância da sombra**:
ela estava a **1 unidade de texto** da letra — mas 1 unidade de texto **não** é 1 pixel na tela.
Dentro do balão, que é desenhado escalado, 1 unidade vale de **2 a 5 pixels** (mais perto = mais).
Resultado: a sombra saía ~4 px da letra e a fonte parecia duplicada/borrada.

Agora a sombra vale **0,4 unidade de texto**, que dá **~1 pixel na tela** no tamanho normal do
balão — e continua colada na letra quando o balão cresce perto da câmera.

## Provas

- `check.py`: **1347 PASS / 0 FAIL**;
- compila limpo (186 fontes, Java 21).

> Nada além disso foi tocado: pílula, cores, pilha de balões, nametag e o resto do mod estão iguais.
