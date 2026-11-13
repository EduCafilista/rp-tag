# RP Tag 5.42.0 — O MODELO DE VERDADE DO GUARDA-CHUVA + a roda, a Axion e o portal do Juízo

**O arquivo para baixar:**
[rptag-5.42.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.42.0/rptag-5.42.0.jar)
· 37.153.501 bytes · `md5 2da64cf9e0f58fc312ba9c798f10a9b1`
· `sha256 fe37d81c1c3986ea6c32cde166dc1788fb450e6709a3e06f85ff283191dd70fe`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.42.0

> **Tire o jar antigo da pasta `mods`.** Um jar do mod por vez, no servidor **e** no cliente.

---

## 1. O guarda-chuva agora é o SEU modelo

Você mandou o pacote com os dois `.obj`. Ele entrou no mod inteiro:

- **Fechado e aberto**, cada um com ~4 mil faces — o guarda-chuva de verdade, com o pano em
  gomos, as varetas, o cabo em gancho e as ponteiras de metal.
- **As 8 cores** foram feitas repintando o seu atlas: mantive o padrão de listras, as costuras
  e o sombreado, troquei só o matiz. Metal, madeira e cinta preta ficaram intactos.
- O jogo lê `.obj` pelo loader `neoforge:obj` — conferi que ele existe no NeoForge 21.1.249
  antes de apostar no arquivo.
- **Prévia:** `arte/previa_umbrella_obj.png` (as 8 cores, fechado e aberto, desenhadas fora do
  jogo com o seu próprio `.obj`).

O guarda-chuva continua abrindo com **um clique** e **só aberto protege do sol**.

---

## 2. A roda de poderes

| O que você falou | O que era | O que mudou |
|---|---|---|
| "os poderes não saem mais com o botão direito" | **Escolher na roda já conjurava** o poder e marcava a recarga. Aí o botão direito caía em recarga e o servidor recusava. | Escolher agora só **arma** a habilidade. Quem solta é o botão direito. |
| "após selecionar deve fechar a wheel" | já fechava — mas gastando o poder | escolher **arma e fecha**, sem gastar nada |
| "ainda aparece pra quem já ativou / não deve aparecer pra cada um" | a roda obedecia o estado *global* | é **por jogador**: o servidor manda o acesso em cada troca de dimensão e a cada segundo; quem perdeu o dom tem a roda fechada na hora |

E o botão direito ganhou um **segundo caminho**: além do evento do NeoForge, o cliente lê a
tecla direto. Se um mod de terceiro engolir o evento (era o caso), o poder sai do mesmo jeito.

---

## 3. Axion

- **Som ao falar:** saiu o som grave e comprido do vazio; entrou um **bip duplo de chiptune**
  (`NOTE_BLOCK_BIT`), ~0,2 s — som de terminal, curtinho.
- **`/acordar <jogador>`** (e `/dormir`, `/guardar`): a Axion aparece **só na tela de quem você
  citar**. Sem citar ninguém, ela continua indo pra todos (como era). `/rp admin axion acordar
  <jogador>` também aceita o nome agora.

```
/acordar EdUuJoGaA        a Axion aparece só pra ele
/acordar @a               aí sim, pra todo mundo
/guardar EdUuJoGaA        tira da tela dele
```

---

## 4. Portal do Juízo

| Defeito | Causa | Correção |
|---|---|---|
| **multiplicando portais** | as posições da pedra e do portão só existiam na **memória do servidor**. Depois de um reinício elas eram procuradas de novo e podiam cair em outro lugar — cada clique abria um portal novo e o antigo ficava | a posição é achada **uma vez** e **gravada no save** do mundo |
| **multiplicando (2)** | a pedra era reconhecida por "qualquer obsidiana chorosa" — e a **moldura do portal também é** obsidiana chorosa | agora casa pela **posição** gravada |
| **não dá pra encontrar** | o portal de blocos estava lá, mas o **vórtice** é um efeito do cliente que dura 8 minutos e **não volta** quando você reconecta | o vórtice é reenviado sempre que alguém entra na sala, e uma coluna de partículas marca o lugar |
| **não dá pra encontrar (2)** | a dica era "a pedra escura ao lado do trono", sem distância | ao entrar você recebe a **coordenada e a distância em blocos** até a pedra |

O portal também não se refaz em cima de si mesmo: se já estiver aberto, clicar na pedra só
reforça o vórtice e avisa que está aberto.

---

## Testes

- `check.py`: **1325 PASS / 0 FAIL** (blocos novos 94-g e 96)
- `arte/auditar.py`: **0 erro** · `arte/validar.py`: 54 ok
- Servidor: `RP Tag 5.42.0 (rptag)` → `Done (1.284s)!`
- Testado por RCON: `/acordar [<jogadores>]`, `/dormir`, `/guardar`, `/juizo`, `/umbrella` e
  `/rp admin axion acordar` respondendo
