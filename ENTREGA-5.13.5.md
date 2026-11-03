# RP Tag 5.13.5 — A SAÍDA É NO SPAWN, E O PORTAL SE ATRAVESSA

**O arquivo para baixar:**
[rptag-5.13.5.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.13.5/rptag-5.13.5.jar)
· 69.368.188 bytes · `md5 07ef1d650532868274b823b8c99a235c`
· `sha256 3eeb10e8c09788b3843afdb448f268280b7aedf06e7507228857c2c2f76dabf2`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.13.5

> **Troque o jar no servidor E no cliente.** O portal (bloco e desenho), a neblina e o aviso
> de saída são dos dois lados.

---

## 1. Saiu do vazio = vai pro SPAWN dele (e nada de aviso de admin na tela)

> *"dnovo voce nao conseguiu arrumar o negocio de sair do vazio e voltar em qualquer lugar
> domapa, tem que ser no SPAWN dele"*

Acabou o "volta de onde entrou" e acabou o "lugar aleatório". Toda saída do vazio — o portal,
o `/vazio soltar` e a morte — passa por um lugar só, o `irParaSpawn`:

1. **o spawn DELE**: a cama / âncora de renascimento em que ele renasceria se morresse;
2. **se ele não tiver cama/âncora**: o **spawn do mundo**, no chão (nunca dentro da pedra).

O lugar de onde ele entrou continua sendo gravado no save só como histórico — **não** decide mais
nada.

### A mensagem de admin na tela (o que causava)

Era isso: quem saía pelo portal e não tinha o registro de origem (servidor reiniciado, `/vazio
entrar`, exílio antigo) caía no caminho de reserva — e esse caminho mandava **"VOCÊ FOI TIRADO
DO VAZIO"**, que é a frase do admin. Agora:

- saindo **pelo portal** → sempre **"VOCÊ ESCAPOU DO VAZIO"**;
- **só** quando um admin solta (`/vazio soltar`) aparece **"VOCÊ FOI TIRADO DO VAZIO"** — e o
  aviso saiu de dentro do comando (antes ele aparecia **duas vezes**: uma ainda dentro do vazio,
  antes da animação).

E os comandos de admin pararam de gritar no chat de todo mundo:

| comando | antes | agora |
|---|---|---|
| `/vazio soltar` | chat global: "*X* voltou do exílio (num lugar aleatório do mundo)" | só quem digitou vê: "*X* vai voltar do exílio no SPAWN dele" |
| `/vazio exilar` | chat global: "Exilado no vazio: X" | só quem digitou |
| `/vazio entrar` | chat global com "Pra sair: /vazio soltar" | só quem digitou |
| atravessar o portal | barra de ação de todos no vazio: "X atravessou o portal" | **nada** (só som e animação) |

> Se você **também** não quiser a faixa "VOCÊ FOI TIRADO DO VAZIO" quando o admin te solta, é
> uma linha — me diz que eu tiro. Deixei porque foi o que você pediu no pedido anterior (dois
> casos: saiu sozinho × foi tirado).

---

## 2. O portal agora é atravessável (não é bloco físico)

> *"nao quero que o portal seja bloco fisico, quero que o playuer posssa passar, atravessar ele
> mesmo, como um portal"*

O bloco **Escuridão** perdeu a colisão (`noCollission`). Ele continua preto, indestrutível, com o
vórtice desenhado e com o clique — mas agora **dá para andar por dentro dele**, como portal do
Nether:

- você **atravessa andando** e, depois de **0,12 s dentro do miolo**, é levado;
- a conta: o miolo tem 1 bloco de profundidade, então dá **~0,22 s andando** e **~0,17 s
  correndo** dentro dele — passa nos dois, e quem só encosta na beirada **não** é levado;
- **clicar** no bloco continua funcionando (nada quebrou para quem está acostumado).

---

## 3. A dimensão mais escura de novo

> *"pode deixar a dimensao mais escura dnovo"*

| o quê | antes (5.13.1–5.13.4) | agora (5.13.5) |
|---|---|---|
| luz ambiente da dimensão | **0,42** (claro) | **0,14** |
| hora fixa | **6000** (meio-dia) | **18000** (meia-noite — sem sol) |
| cor da neblina | 0,130 / 0,120 / 0,165 | **0,058 / 0,052 / 0,080** |
| alcance da neblina | fecha em **210** blocos | fecha em **150** |

O que ilumina a praça e as ruínas agora são **as lanternas de alma** e o brilho dos blocos — do
jeito que o vazio tem que ser. **Não** é o breu da 5.13.1 (aquele em que você não via nem a
hotbar): o chão e as construções perto continuam legíveis, o horizonte é que some.

---

## 4. "Nos mods de mapa vou aparecer como uma dimensão nova?"

**Sim.** O vazio é uma dimensão de verdade, registrada no jogo com o ID **`rptag:exilio`** (e
existe também a `rptag:vazio`). Nos mods de mapa (Xaero's World Map/Minimap, JourneyMap, VoxelMap)
ela aparece na lista de dimensões, separada do Overworld/Nether/End, com mapa próprio — igual
qualquer dimensão de mod.

Duas observações:

- o **nome** dela agora está traduzido no mod (`dimension.rptag.exilio` → **"O Vazio"**; em
  inglês "The Void"). Mods que leem a tradução mostram esse nome; os que não leem mostram o ID
  `rptag:exilio` — e você já pode ver isso no próprio jogo: o `/forceload` dentro dela responde
  "*... in The Void*";
- o **céu/efeitos** dela usam o visual do Overworld (é o que segura a neblina preta do mod), então
  alguns mods de mapa podem desenhar o ícone de "Overworld" para ela. O mapa em si é separado.

---

## Testado no servidor (NeoForge 21.1.249, Java 21)

Mundo novo, `javac` 0 erros, `check.py` **883 PASS / 0 FAIL**:

| o que | resultado |
|---|---|
| 1º boot | praça em `y=10` · 8 pedestais · portal em `{0,10,-22}` · anel de 14 construções · **relevo pronto (103.399 blocos)** · **0 exceptions** |
| portal abre | `0 12 -22` = `escuridao` no vazio ✔ · o mesmo teste fora do vazio falha (não vaza bloco) ✔ |
| piso do portal | `0 9 -22` = `polished_blackstone` ✔ |
| bloco atravessável | `noCollission()` está no jar final (conferido no bytecode) ✔ |
| nome da dimensão | `/forceload` responde "*in **The Void***" ✔ |

**O que eu NÃO consegui testar aqui:** o andar-para-dentro do portal e o teleporte para o spawn
precisam de um **jogador** — e o meu ambiente não roda cliente (o NeoForge recusa cliente
vanilla; entidade sem jogador online também não "ticka" física). Então o teste final é o seu:
`/vazio entrar`, andar para dentro do portal, e ver se cai no seu spawn.

---

## O que NÃO mudou (de propósito)

- Névoa/aura/rastro da lore, bloco `escuridao` como bloco do portal, animação de abertura de
  baixo para cima, praça, pedestais, altares, ruínas, relevo e baús com raridade — tudo igual.
- Construção **uma vez só** (5.13.4): reentrar não refaz nada.
- Nada de slime; bicho só preto (Enderman/Warden).
