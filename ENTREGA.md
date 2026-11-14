# RP Tag 5.53.0 — fim da frase repetida no balão

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.53.0/rptag-5.53.0.jar
(também na sua área de trabalho como `rptag-5.53.0.jar`, 37.030.310 bytes)

Instalar: jogue o `.jar` em `.minecraft/mods` (NeoForge 1.21.1 / Java 21) e **apague o jar antigo**.

---

## O texto aparecendo duas vezes

O jogo pede o desenho de uma entidade **mais de uma vez no mesmo quadro** (passe normal e passes
extras, tipo contorno/brilho). Cada pedido redesenhava o balão inteiro, e a cópia que sobrava
caía deslocada — sem a pílula, mais escura e menor: era essa a frase repetida do print.

Agora:

- **um balão por jogador por quadro** — se o mesmo jogador voltar a ser oferecido para desenho
  poucos milissegundos depois, é a mesma passada e o mod ignora (`jaDesenhado`);
- **a geometria é liberada na hora** (`endBatch(RenderType.gui())` logo depois de montar a
  pílula) — assim ela nunca é reenviada no fim do quadro em espaço de tela, que era o fantasma
  no canto.

## O que continua da versão anterior

- fade do balão vai **até zero** (nada de balão morto a 10% de opacidade);
- letra nítida: sombra única de **1 pixel de tela** (`umPixel`), em qualquer zoom;
- guarda-chuva continua removido do mod.

## Provas

- `check.py`: **1346 PASS / 0 FAIL**;
- compila limpo (186 fontes, Java 21);
- servidor NeoForge 1.21.1 sobe sem exceção (1293 receitas, 1399 advancements);
- jar baixado do link e conferido: md5 `a87b01c0b16550ef18fc3114a30a9cd0`.
