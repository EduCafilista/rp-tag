# RP Tag 5.13.4 — O VAZIO QUE NÃO FALA E NÃO SE REPETE

**O arquivo para baixar:**
[rptag-5.13.4.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.13.4/rptag-5.13.4.jar)
· 69.368.510 bytes · `md5 85b10cbea3b938ef1d8e8fc68caafabe`
· `sha256 bc363305a534759cab8ded14cca4410d5681a8d0d9a06d483f1d2e4aab03e234`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.13.4

> **Troque o jar no servidor E no cliente.** O aviso de saída (faixa com letras pretas) é
> desenhado no **cliente**; quem entra/sai é decidido no **servidor**.

---

## O que você pediu (e o que foi feito)

| # | o que você falou | o que mudou |
|---|---|---|
| 1 | *"tirar os avisos de tela quando entra e quando sai"* | **Não tem mais aviso nenhum na entrada.** O único texto de tela do caminho de entrada/saída é o novo aviso de fuga (item 2). O aviso antigo que sobrava no `usarPortal` ("A PORTA SE ABRE PARA O OUTRO LADO…") **foi apagado** — ficou só o som do portal. |
| 2 | *"um aviso com letras pretas dizendo que conseguiu escapar do vazio — ou que foi tirado"* | **Faixa creme (`0xEFEAD9`) com letras PRETAS (`0xFF0A0A0A`)**, no meio da tela, 6 s: **"VOCÊ ESCAPOU DO VAZIO"** quando ele sai sozinho pelo portal e **"VOCÊ FOI TIRADO DO VAZIO"** quando um admin usa `/vazio soltar`. O texto é desenhado duas vezes (peso de tinta) e a fonte cresce até 2,6×. |
| 3 | *"o portal deve ficar no nível do chão"* | O portal da praça nasce **com o piso no chão** (`polished_blackstone` em `y=9`) e o miolo de `escuridao` começa em `y=10` — quem entra **anda** para dentro dele, não sobe degrau nem cai em buraco. O caminho de laje preta até ele também está no chão. |
| 4 | *"tirar o aviso que toca quando cai no void"* | Caiu no vazio = **nenhuma mensagem**. A queda continua matando devagar (5.10.1), mas sem texto na tela e sem chat. |
| 5 | *"toda vez que entra no void a estrutura spawna de novo (as lanternas ficam quebrando)"* | **A dimensão se constrói UMA VEZ na vida do mundo.** Praça, 8 pedestais, 8 altares, portal, anel de ruínas e **cada um dos 184 chunks de relevo** gravam uma marca no save (`world/data/rptag_states.dat`). Reentrar, reiniciar o servidor, sair e voltar: **nada é refeito**. Testado em dois boots seguidos: no 2º, zero reconstrução. `/vazio construir` continua refazendo na marra quando você quiser. |
| 6 | *"fazer relevos na dimensão: prédios caídos, ruínas, coisas que evidenciem o vazio"* | **Relevo de verdade** num anel de 45 a 130 blocos em volta da praça: **184 chunks** esculpidos com montes, crateras, cascalho solto, blocos caídos de blackstone e tijolo rachado — ~103 mil blocos assentados. A amplitude **cresce com a distância** (rampa de 30 a 70 blocos), então não existe paredão de 5 blocos colado na praça. As ruínas/prédios que já existiam (`anelInicial`, `enfileirarRuinaBaixa`, `enfileirarQuarteirao`) continuam e agora **também** marcam o save. |
| 7 | *"não spawnar slime; se for spawnar bicho, que seja completamente preto"* | **Slime não nasce** (spawn cancelado + varredura a cada 4 s que apaga qualquer slime que apareça). Dos bichos só ficam os **pretos de verdade**: **Enderman** e **Warden**; todo o resto é cancelado no `FinalizeSpawnEvent` e varrido. |
| 8 | *"baús dentro da dimensão com raridade pros players acharem os fragmentos do void"* | **Baú em cada ruína/prédio baixo**, com **três raridades**: **comum** (82 %) — 2 a 4 de sucata; **raro** (15 %) — **1 fragmento do vazio**; **mítico** (3 %) — **2 fragmentos** + 2 a 4 diamantes + 1 a 2 echo shard. Em 55 % dos casos o baú ainda ganha um item bom extra. |

---

## Os dois bugs que só apareceram no servidor de verdade

Os dois eram do **relevo** e os dois deixavam a dimensão **chapada** (o dono veria "o relevo não
funcionou"):

### 1. O relevo não reconhecia o chão da dimensão

A laje do `rptag:exilio` é, de baixo para cima: bedrock, deepslate, blackstone, tuff, sculk e
**concreto cinza-claro**. O relevo só mexia no que chamava de "terreno natural" — e a lista
**não tinha concreto nem sculk**. Resultado: ele olhava o chão, dizia "isso aqui é construção,
não mexo" e pulava **100 % das colunas**. Agora a lista inclui `SCULK`, `LIGHT_GRAY_CONCRETE` e
`GRAY_CONCRETE`.

### 2. A fila de blocos engolia o terreno

Todo bloco do mod entra numa fila que coloca **400 blocos por tick**. O relevo despejava ~1.400
por tick e a fila tem teto de **40.000** — o que passava do teto era **jogado fora em silêncio**.
Metade da dimensão nunca nascia. Agora o relevo **despeja a fila na hora** (`despejarFila`) depois
de cada chunk: os 103 mil blocos são assentados em ~13 s e **nenhum** é descartado.

Ainda foi acrescentado: **o chunk é carregado (gerado) antes de esculpir** — esculpir num chunk
que nunca foi gerado devolvia altura 0 e o relevo sumia calado.

---

## Testado no servidor (NeoForge 21.1.249, Java 21)

Mundo **novo**, log limpo, sem exceção:

| o que | resultado |
|---|---|
| 1º boot | praça nivelada em `y=10` · 8 pedestais · portal em `{0,10,-22}` · anel de **14** construções · **relevo pronto (103.399 blocos)** |
| portal no chão | `0 9 -22` = `polished_blackstone` ✔ · `0 10 -22` **não** é piso ✔ (é o miolo) · `0 12 -23` = `escuridao` (fundo atrás) ✔ |
| relevo de longe | chunk `5,5` (≈113 blocos da praça): topo entre **y=11 e y=15**, tuff/deepslate/gravel/coal ore ✔ |
| relevo perto | chunk `2,2`: topo entre **y=7 e y=10** (rampa funcionando, sem paredão) ✔ |
| praça intacta | chunk `0,0` = 232 colunas em `y=9` + 19 em `y=10` (pedestais) ✔ |
| baús | 7 baús em 7 chunks de ruína; o inspecionado no save tinha `rotten_flesh x3` ×2 (raridade **comum**) ✔ |
| save das construções | `rptag_states.dat`: **184** marcas `R:` · **12** `B:` · `praca` · `anel` · `P0…` · `S0…` ✔ |
| 2º boot | **nada reconstruído** — só "relevo do vazio agendado", nenhum bloco posto, **0 exceptions** ✔ |

`javac` 0 erros · `check.py` **876 PASS / 0 FAIL**.

---

## O que NÃO mudou (de propósito)

- Névoa preta, bloco `escuridao`, aura, rastro, cidade na chegada e a volta **exatamente** ao ponto
  de entrada: tudo igual.
- A animação de abertura do portal de baixo para cima (5.13.3) e o `/vazio pedestais encher`.
- Blocos escuros da 5.13.0–5.13.2: não foram revertidos.
- Nada de som novo, nada de conceito novo.
