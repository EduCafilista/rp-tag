# RP Tag 5.41.1 — A ANIMAÇÃO DO UMBRELLA + VARREDURA DO MOD

**O arquivo para baixar:**
[rptag-5.41.1.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.41.1/rptag-5.41.1.jar)
· 36.891.292 bytes · `md5 ce8e24e376d89b26f80a8588ee0990fe`
· `sha256 720cc6bfb9e54ca2c033699b31a58266c35d8683830b64449ed714a8b3e22a9d`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.41.1

> **Tire o jar antigo da pasta `mods`.** Um jar do mod por vez, no servidor **e** no cliente.

---

## O que você pediu, e o que mudou

> *"ele não precisa ficar encantado, não está bonita a animação, melhore, deixe mais modelada,
> não tem a animação dele aberto, não fica aberto no caso, corrija isso, a powerwheel aparece
> mesmo sem o player ter o vazio ligar, corrija isso, lembrando passe por todo o mod e veja
> quais erros você consegue arrumar"*

| O que você pediu | O que está feito |
|---|---|
| **não precisa ficar encantado** | O brilho de encantamento (aqueles pontinhos roxos girando) **saiu** do guarda-chuva. |
| **não fica aberto / não tem a animação** | Abrir deixou de depender de "segurar o botão". Agora é **um clique** e ele fica aberto de verdade. O estado mora no próprio item (o índice do modelo, que o Minecraft entende sozinho) — não depende mais de animação do jogador. |
| **a animação não está bonita** | A cúpula **se desdobra**: começa pequena (18%) e girada 55° e cresce girando em 0,26 s, assentando no fim. Ao fechar, encolhe em 0,2 s (antes desaparecia de um quadro pro outro). Aberta, tem um balanço leve de pano. |
| **deixe mais modelada** | A textura foi refeita com **volume**: gomos com costura e nervura, madeira com veio e nó, metal com brilho. A geometria virou **octogonal** (dois quadrados, um a 45°) com bordas e as 8 pontas das varetas. |
| **a powerwheel aparece sem o vazio** | A roda **só existe** pra quem tem o dom. O servidor manda um pacote (`vazio_acesso`) no login, na troca de dimensão e quando o estado muda; sem ele o cliente **não abre, não desenha e não aceita o atalho**. |
| **passe por todo o mod** | Entrou o `arte/auditar.py`: confere pacote registrado, classe de evento órfã (aquilo que já crashou o cliente uma vez), som/textura/receita que não existe e nome faltando no idioma. Ele roda junto com o `check.py` e hoje dá **0 erro**. Nesta passada, três itens que apareciam com o nome cru (`fita`, `nucleo_vial`, `nucleo_vial_quebrado`) ganharam nome nos dois idiomas. |

E, revisando a cúpula na prévia, achei um quinto defeito: ela estava **de ponta-cabeça e flutuando
um bloco acima da cabeça** (miolo embaixo, aba em cima). Corrigido — agora é um cone com a aba
encostando no chapéu e os varões pendurados.

---

## Como usar

```
/umbrella dar <jogador> <cor>     dá o guarda-chuva (cor 1..8)
/umbrella abrir                   abre na hora
/umbrella fechar                  fecha na hora
/umbrella info                    cor, aberto/fechado e segundos restantes
```

Na mão: **clique direito** para abrir (dura 65 s), clique de novo — ou agache — para fechar,
com 5 s de recarga. **Só aberto protege do sol.**

**Prévia da animação (desenhada fora do jogo, mesma conta do Java):** `arte/previa_abertura.png`
**Prévia dos modelos nas 8 cores:** `arte/previa_umbrella.png` e `arte/previa_umbrella_cores.png`

---

## Testes

- `check.py`: **1308 PASS / 0 FAIL**
- `arte/auditar.py`: **0 erro**, 14 avisos (literais repetidos em comandos — normais)
- `arte/validar.py`: 54 ok, 0 falhas
- Servidor: `RP Tag 5.41.1 (rptag)` → `Done (1.317s)!`, sem erro
