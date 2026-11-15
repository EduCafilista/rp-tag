# RP Tag 5.56.0 — fim do texto repetido (a sombra caía fora do balão)

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.56.0/rptag-5.56.0.jar
(também na sua área de trabalho como `rptag-5.56.0.jar`, 37.029.793 bytes)

---

## O bug era MEU, e a conta prova

Na 5.51.0 eu troquei o deslocamento da letra por um "1 pixel de tela" calculado como
`1 / escala da matriz`. A escala do balão é **0,04**, então isso dava **25 unidades de texto**.
Só que 1 unidade de texto vale ~2,5 pixels na tela: a **sombra escura** da frase era desenhada
**~60 px abaixo e à direita** da letra — fora da pílula, sem fundo. Era essa a segunda mensagem.

Não era pilha de balões, não era pacote duplicado, não era outro mod. Era a sombra.

## O que mudou agora

- a sombra voltou a ser a **do Minecraft**: 1 unidade de texto (`SOMBRA_PX = 1.0F`);
- o helper errado (`umPixel`) foi **removido**;
- o negrito falso também voltou a 0,75 unidade de texto;
- **desfeito** o que eu tinha inventado nas 5.53.0/5.55.0 (flush da geometria, bloqueio de
  desenho, dedupe de frase repetida): a pilha de balões e o desenho são os de antes.

Continua valendo só o que já estava aprovado: letra nítida e fade até zero.

## Provas

- `check.py`: **1347 PASS / 0 FAIL** (entrou a prova de que a sombra é 1 unidade e que `umPixel` não existe mais);
- compila limpo (186 fontes, Java 21);
- md5 do jar: ver na release.
