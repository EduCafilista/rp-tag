# RP Tag 5.13.6 — MONTANHAS, ALMAS VAZIAS E O BLOCO DO PORTAL

**O arquivo para baixar:**
[rptag-5.13.6.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.13.6/rptag-5.13.6.jar)
· 69.384.841 bytes · `md5 beef6ada5c2006fb3d9be6c64e7d67e9`
· `sha256 91b8fdef1d736bd738a62aba0d6dc5b1a2faf58bb741a1fe9331f345bc46f2fa`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.13.6

> **Troque o jar no servidor E no cliente.** As almas (o desenho do corpo), o relevo e o bloco
> animado do portal são dos dois lados.

---

## 1. Montanhas de verdade (e não "monte de areia")

> *"coloca uns relevos na dimensão, umas montanhas, coisas assim"*

O relevo antigo era uma ondulação macia: dava para subir andando e as colinas todas com a mesma
cara redonda. Agora ele usa **ridged noise** — a conta `1 - |seno|`, que **cria quina** em vez de
lomba. É a diferença entre serra e duna. Três escalas somadas:

| escala | tamanho | o que é |
|---|---|---|
| `largo` | ~270 blocos | a cordilheira que atravessa o vazio |
| `r1` / `r2` | ~100 / ~55 blocos | o contraforte e a encosta |
| `r3` | ~40 blocos | a agulha, que dá relevo **dentro** de um chunk só |

- **perto da praça** (até ~62 blocos) o chão continua manso: é caminho de quem chega;
- **daí para fora** sobe numa rampa até o pico — 40 blocos de altura máxima, encosta íngreme;
- **no horizonte, a 118 blocos**, tem o **cinturão de serras**: uma muralha de 6 a 16 blocos que
  fecha o mundo em todas as direções, para o vazio não terminar num horizonte reto.

**Medido no mundo gerado (46 chunks do anel):** a superfície vai de **y=5 a y=52** (47 blocos de
desnível), o desnível **dentro de um único chunk** tem mediana de **14 blocos** e chega a **43** —
ou seja: penhasco, não colina. Antes, o mesmo ponto dava 194 colunas todas em y=40, chapado.

### A pedra também mudou (montanha tem estrato, não sorteio)

`pedraDoMonte` monta a montanha **em camadas por altura**, como rocha de verdade:

- **pé**: deepslate e tuff;
- **contraforte**: tuff, deepslate, cobbled deepslate e gravel (o desmoronamento);
- **parede**: blackstone, tuff, **deepslate bricks rachado** e veio de carvão;
- **cume**: smooth basalt, blackstone, sculk e **crying obsidian** (o vazio vazando na pedra).

E no pé da serra cai **entulho** (30% das colunas): pedregulho solto de cobbled deepslate e gravel.

---

## 2. As almas vazias perdidas no vazio

> *"faça almas vazias que ficaram perdidas naquele local"*

São **12 almas**, cada uma perdida num ponto diferente do vazio:

`{26,-31} {-33,19} {14,43} {-19,-46} {63,66} {-78,37} {99,-72} {-44,-101} {124,86} {-113,-24} {41,-104} {-96,96}`

**O que elas fazem:**

- ficam **orbitando devagar** (raio 2,5 ± 1,2) o lugar onde morreram, paradas no chão;
- quando alguém chega **a menos de 42 blocos**, a alma **se vira e vem** — 0,9 bloco por segundo,
  flutuando na altura do chão;
- **a 2,6 blocos ela para** e fica **encarando** você, sem tocar, sem dano, sem hitbox, sem
  empurrar, sem mob nenhum no meio;
- **a menos de 9 blocos** ela **sussurra** (`SOUL_ESCAPE`, baixinho) e um gemido de caverna vem
  junto — no máximo uma vez a cada 6 segundos, por alma;
- **não ataca, não persegue quem foge**: se você sai dos 42 blocos, ela volta a orbitar o lugar
  dela.

**O corpo é desenhado no cliente e não é entidade.** Duas coisas resolveram isso:

1. **Não existe mob, ArmorStand nem nada no mundo.** A posição das 12 almas é só um vetor no
   servidor (`ALMAS_ESTADO`), recalculado a cada meio segundo. Sem entidade: não dá para matar,
   não dá para quebrar no criativo, não duplica quando o chunk descarrega, não pesa no tick.
2. **O desenho é geometria no cliente** (`VazioAlmasFx`), mandado por um pacote pequeno
   (`VazioAlmasPayload`, 36 floats = 12 almas × x,y,z), e só para quem está a menos de 260
   blocos. **Não é partícula** — então "partículas: mínimo" nas opções do jogo não apaga a alma.

> **O que já tinha dado errado (para não repetir):** na primeira tentativa a alma era um
> `ArmorStand` invisível. O chunk descarregava, o servidor achava que a entidade tinha morrido e
> criava outra: o log enchia de *"12 almas… 36… 48"* e o vazio se enchia de almas fantasma.

---

## 3. O bloco `escuridao` (o miolo do portal) ficou animado

> *"melhore a qualidade desse bloco vazio"*

A textura do miolo do portal agora é **animada em 4 quadros** (`escuridao.png` 32×128 +
`escuridao.png.mcmeta`, `frametime 8`, interpolação ligada):

- **ruído periódico de 4 oitavas** (dá para repetir o bloco lado a lado sem emenda visível);
- **centro escuro de verdade**: `0,42 + 0,58·r²` — o miolo afunda e a borda clareia, então o
  portal *parece* fundo, não um quadrado preto;
- **22 poeiras** roxas `(128,98,176)` e **9 brasas** vermelhas `(126,26,44)` por quadro;
- brilho médio **13/255** — continua sendo "escuridão", como o nome promete.

---

## 4. O que mais mudou (o "veja o que for para melhorar")

- **Nada de montanha em cima dos altares.** Os 8 altares do fragmento ficam num raio de 16 blocos
  poupado do relevo — se a serra subisse por cima, o fragmento nascia soterrado e o jogador
  ficava preso num vazio sem saída.
- **Praça, caminho, casas e ruínas continuam intactos**: o relevo respeita a praça + 12, o
  caminho (`|x| ≤ 10`, `-36 < z < 8`) e as 15 construções.
- **Costura do check**: `check.py` agora tem **891 réguas** (884 antes), todas passando.

---

## Como atualizar

1. Baixe o `rptag-5.13.6.jar` no link do topo.
2. Jogue em `mods/` **do servidor** — apagando o jar antigo.
3. Jogue em `mods/` **do cliente** — apagando o jar antigo.
4. **O mundo antigo continua valendo**: a praça, o portal e os altares já construídos ficam onde
   estão. As montanhas aparecem nos chunks que ainda não foram gerados; onde já existe chão
   gerado, ele não é refeito.
