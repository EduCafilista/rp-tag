# RP Tag 5.58.0 — fonte do balão limpa (sem sombra)

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.58.0/rptag-5.58.0.jar
(também na sua área de trabalho como `rptag-5.58.0.jar`, 37.029.614 bytes)

---

## Por que a fonte parecia "bugada"

A letra era desenhada **duas vezes**: uma branca (a letra) e uma cópia escura 1 px ao lado (a
sombra). Só que o balão é desenhado numa matriz **escalada por um fator fracionário** — a letra é
amostrada em pixels que não caem no grid — então a cópia escura **se mistura com a letra**: os
traços engordam, ficam irregulares e a fonte parece borrada/duplicada.

O próprio mod já dizia isso desde a 3.35.0, no comentário do código:

> "SEM sombra: dentro de um balão claro a sombra preta do vanilla borrava cada letra e dava aquele
> aspecto sujo/embaçado. O recheio já é opaco, então o contraste sozinho basta."

Eu tinha reintroduzido a sombra na 5.43.0 (e ela voltou nas 5.56 e 5.57). **Agora saiu de vez:**

- a letra é desenhada **uma única vez**, sem cópia escura;
- o helper `haloDe` e a constante `SOMBRA_PX` foram **removidos**;
- o contraste fica por conta do recheio opaco da pílula, como era no início.

## Provas

- `check.py`: **1347 PASS / 0 FAIL** (prova nova: `drawInBatch` só 2 chamadas — texto e o negrito
  do grito — e nenhuma menção a sombra/halo);
- compila limpo (186 fontes, Java 21).

> Nada além do texto foi tocado: pílula, cores, pilha de balões, nametag e o resto do mod iguais.
