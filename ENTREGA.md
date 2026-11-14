# RP Tag 5.51.0 — guarda-chuva REMOVIDO + balão legível

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.51.0/rptag-5.51.0.jar
(também está na sua área de trabalho como `rptag-5.51.0.jar`, 37.029.883 bytes)

Instalar: jogue o `.jar` em `.minecraft/mods` (NeoForge 1.21.1 / Java 21). Não precisa de mais nada.

---

## 1. Guarda-chuva (umbrella): FORA do mod

Você mandou tirar e ele saiu **inteiro**:

- item `rptag:umbrella` — não existe mais (nem no registro, nem no criativo, nem no `/give`);
- comando `/umbrella` — removido;
- receitas (16), modelos `.json`, os dois `.obj`/atlas, texturas de item e de HUD (100 arquivos) — apagados;
- a camada de tela (HUD) que desenhava a cúpula/barra — removida;
- proteção solar que ele dava — só existe agora pelo **Amuleto do Eclipse**, como era antes;
- 8 cores, "abrir 65 s", recarga, pose na mão: nada disso sobra no código.

O `check.py` também foi limpo: **zero** prova mencionando umbrella. Se ele voltasse por acidente, as provas antigas não estão mais lá para dar falso PASS.

## 2. Balão: o texto voltou a ser legível

Causa do borrão: na 5.43.0 eu tinha feito o "contorno" desenhando **a mesma letra 4 vezes em volta** dela. Só que isso acontecia **dentro da matriz escalada** do balão — com o balão pequeno, cada cópia virava 3–4 pixels borrados, e a mancha transbordava a pílula.

Agora é **uma sombra só**, deslocada com um valor calculado da matriz (`umPixel`), que é sempre **1 pixel de TELA** — não importa o zoom nem o tamanho do balão. O negrito falso usa o mesmo pixel de tela. Resultado: letra nítida, dentro da pílula, em balão claro e escuro.

## 3. Prova de que está inteiro

- `check.py`: **1343 PASS / 0 FAIL**.
- Compila limpo (`javac --release 21`, 186 fontes).
- Servidor NeoForge 1.21.1 sobe sem exceção: 1293 receitas, 1399 advancements, praça do vazio, portais e pedestais erguidos.
- Jar conferido baixando o link acima: md5 `ced31d50a1b7e14a81a56774e8a8400c`.

> Versão anterior (5.50.0) apagada da página de releases — só a mais nova fica no ar.
