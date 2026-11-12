# RP Tag 5.40.0 — O UMBRELLA NA MÃO (tamanho certo, virado pra cima e com a versão ABERTA)

**O arquivo para baixar:**
[rptag-5.40.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.40.0/rptag-5.40.0.jar)
· 36.884.475 bytes · `md5 73117e47dc0cefe9b5cd0e5fc736f9d4`
· `sha256 0962c26f8c978014956f07a5d79b64db4692612c456e5d3595be0d277baec4d1`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.40.0

> **Tire o jar antigo da pasta `mods`.** Um jar do mod por vez — se os dois ficarem lá, o jogo
> pode carregar o velho. Troque no servidor **e** no cliente (o modelo e a cúpula são do cliente;
> a proteção do sol é do servidor).

---

## O que você reclamou, e o que era

> *"o umbrella tá muito pequena, não existe a versão dele aberto, está todo bugado ainda"*

Quatro defeitos, todos no mesmo lugar. Nenhum era falta de arte:

**1. A versão aberta nunca era escolhida.** O modelo troca do guarda-chuva fechado pro aberto
por um "predicado" de estado — e o predicado que eu tinha usado (`using`) **não era registrado
em lugar nenhum**. Predicado sem registro vale **0 pra sempre**: os 8 modelos abertos estavam
dentro do jar, com a arte certa, e o jogo nunca perguntava por eles. Era literalmente isso o
"não existe a versão aberto". Agora existe uma classe que registra o predicado `rptag:aberto`
no cliente e responde 1 quando você está segurando o botão direito.

**2. Muito pequena.** Na 5.39.0 eu tinha tirado o `display` do modelo e deixado ele herdar o
tamanho do `block/block` do Minecraft (escala 0,375) — daí o guarda-chuva minúsculo do seu
print. Voltou o `display` do `item/handheld` (o mesmo padrão de espada, picareta, haste), com
escala maior.

**3. A cúpula sobre a cabeça estava 8,9 BLOCOS abaixo de você.** Eu escrevi `-8,9` no código
achando que era pixel, mas ali a conta já é em **blocos**. Ela era desenhada enterrada no chão,
embaixo do jogador. Agora a altura é `0,72` bloco — logo acima do chapéu.

**4. De quebra, dois problemas que eu achei revisando:**
- **As duas geometrias estavam de cabeça pra baixo**: o gancho do cabo em cima e a ponteira
  embaixo. Agora o cabo tem gancho embaixo, o pano sobe e a ponteira fica no alto (padrão de
  item na mão do Minecraft).
- **Os anéis usavam rotação de 90°/135°** — ângulo que o Minecraft **recusa** em modelo de
  item. Se tivesse ido assim, o guarda-chuva voltaria a aparecer rosa/preto. Agora são discos
  de 5 placas cruzadas, só com ângulos de −45° a 45°.

---

## Como usar

```
/umbrella dar <jogador> <cor>     dá o guarda-chuva na cor pedida (1 a 8)
/umbrella dar <jogador>           dá na cor "sangue"
/umbrella info                    mostra a cor, se está aberto e quantos segundos faltam
```

**Na mão:** clique direito e **segure** — em 1 segundo o guarda-chuva abre (na mão e sobre a
cabeça) e o sol para de te queimar. Dura até 65 segundos; soltar o botão ou agachar fecha na
hora, com 5 segundos de recarga.

> **Só aberto protege.** Fechado, ele é só um guarda-chuva bonito na sua mão — testado no
> servidor: fechado `protegido=false`, aberto `protegido=true`.

**As 8 cores:** sangue, noite, juízo, sombra, veneno, luar, ouro e rosa. Cada uma se crafta
(3 fios + 3 lã + 1 graveto) e dá pra tingir o que você já tem com o corante certo.

---

## O que garante que não volta a quebrar

O `check.py` agora tem **1288 provas** e falha o build antes de eu publicar se:

- algum modelo apontar pra uma textura ou pra um pai que **não existe** (foi o rosa/preto da 5.39.0);
- algum modelo usar rotação fora de −45°..45° (o jogo recusa e o item vira "faltando");
- o predicado do estado aberto **não** estiver registrado no cliente, ou o nome dele no Java
  não for o mesmo do JSON;
- algum dos 16 modelos (8 cores × aberto/fechado) sumir;
- o `display` voltar a ficar minúsculo, ou o guarda-chuva voltar a ficar de cabeça pra baixo;
- os cubos da cúpula no Java não baterem com a geometria de referência.

A cúpula virou **fonte única**: `arte/dome_geometria.json` é lido pelo jogo, pela prévia fora do
jogo e pelo validador. Se um dia divergir, o build para.

**Prévia (desenhada fora do jogo, com a mesma geometria):** `arte/previa_umbrella.png`

---

## Testes desta versão

- `check.py`: **1288 PASS / 0 FAIL**
- `arte/validar.py`: 54 ok, 0 falhas
- Servidor de teste: `RP Tag 5.40.0 (rptag)` → `Done (1.436s)!`, sem erro
- Jar: 19 modelos de umbrella, 8 texturas de item, 8 de cúpula, 8 de HUD, ícone sem número
  presente; nenhuma sonda de teste dentro do jar
