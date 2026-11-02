# RP Tag 5.13.2 — PEDIDO 52

**O arquivo para baixar:**
[rptag-5.13.2.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.13.2/rptag-5.13.2.jar)
· 69.362.105 bytes · `md5 fa723a5b44b0f7fe204d05049f9182d9`
· `sha256 462b5766c98bcb5e762fcea42dff3cf66db690e23db8e067022064095ee02ba9`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.13.2

> **Troque o jar no servidor E no cliente.** A névoa do vazio, a aura preta, o rastro e o vórtice
> do portal são desenhados **no cliente** — com o jar velho no cliente, o portal aparece como
> parede de pedra preta sem animação. O canal de rede continua o **v13** (nenhum pacote novo
> obrigatório), então cliente 5.13.1 ainda conecta; só não vê o que é novo.

---

## O que você pediu, e o que foi feito

| # | Seu pedido (52) | Como ficou |
|---|---|---|
| 1 | **"a dimensão é o bug nº1, tá tudo preto, não vejo nem minha hotbar"** | A **cortina preta do cliente saiu do código** (`NeptuneFx.desenharExilio` virou no-op). Ela pintava 50% de preto + uma nebulosa 55% **por cima da hotbar**, em todo lugar da dimensão. A escuridão agora é do **mundo** (névoa + luz da dimensão), nunca de uma camada na tela. |
| 2 | **"a névoa está muito escura, não dá pra ver o mundo"** | `VazioNeblina` foi de quase-preto (0,052 / 0,048 / 0,070) para **roxo-acinzentado legível**: `0.130 / 0.120 / 0.165` com pulso lento. O horizonte abriu de 190 para **210 blocos** (perto 26). Ainda é noite no breu — mas dá para jogar. |
| 3 | **"mesmo voando fica fazendo som de pisar no chão"** | O `WARDEN_STEP` só toca com **o pé no chão**: `onGround()` **e** não voando (criativo), não caindo de elytra, não em barco/cavalo, não nadando, não espectador. Voando, o limiar sobe de 0,9 para 2,2 e o som não sai. |
| 4 | **"o portal não pode ser `black_concrete`"** | Nasceu o bloco **`rptag:escuridao`**: preto, **sólido** (colisão cheia, não é passável — "não pode passar para trás o bloco"), luminância **1** e luz **soul-fire** (não vira buraco preto chapado). Substituiu o `black_concrete` no miolo **e** no fundo do portal. |
| 5 | **"o portal é animação, não construção física pesada"** | O miolo do portal é **o bloco de escuridão**, e em cima dele o cliente desenha o **vórtice animado** (`VazioPortalFx`) — moldura de obsidiana, rachaduras vermelhas, estrelas roxas e brasas, girando. A forma física ficou como você aprovou ("ficou legal assim"), só o **material** mudou. |
| 6 | **"esse aviso não precisa aparecer que o portal está sendo aberto"** | `/vazio abrir` é **silencioso**: nada no chat. Ele **garante a moldura** (se o chunk foi regenerado, ele reconstrói), apaga o miolo e **anima de baixo para cima** com som. Se já estava aberto, **reabre do zero** (bom para gravar vídeo). |
| 7 | **"o portal do vazio leva pro overworld, o do overworld leva pro vazio"** | O destino é **onde o portal foi criado**: portal no overworld → vazio (`entrarNoExilio`); portal no vazio → de volta ao ponto exato de entrada. O bloco de escuridão responde em **qualquer dimensão**. |
| 8 | **"não criar portais em qualquer canto do mundo"** | `invocarPortalEm` **desmonta o portal anterior** (apaga miolo, fundo, moldura e laje) antes de levantar o novo. Sempre existe **um** portal invocado por dimensão — nunca um rastro deles espalhado. |
| 9 | **"não tem nada preto mais nos efeitos em volta dele"** | Voltou o preto — **sem os quadrados**. Agora é um **cilindro de 10 faixas verticais** coladas no corpo (`RaiosVazioFx.desenharSombra`), mais opaco no pé e transparente na cabeça, girando devagar: lê como coluna de fumaça preta, e por ser vertical nunca vira quadrado na câmera. |
| 10 | **"eu ainda quero o rastro de escuridão atrás do deus"** | O rastro ganhou **mancha preta** (`RastroVazioFx.sombra`): leque de 12 triângulos achatados no chão, raio irregular, centro quase opaco e borda que dissolve em alpha 0 — sem contorno, sem adesivo. Esfria junto com a rachadura. |
| 11 | **"a animação de entrar está boa"** | Não foi tocada. Nem a de engolir (5,2 s) nem a de chegada. |
| 12 | **"a dimensão precisa de cidade já na chegada"** | `anelInicial`: **14 construções** erguidas de uma vez quando a dimensão abre — anel de **26 a 78 blocos** em volta da praça (torre, ruína baixa e quarteirão), sem cobrir o caminho do portal. Antes a primeira ruína caía a 34+ blocos e, com a névoa fechada, você entrava e não via nada. Depois disso a cidade continua crescendo (uma a cada ~2 s). |
| 13 | **"sair pelo portal devolve no mesmo lugar" (3ª vez)** | `voltarParaEntrada` virou o **único caminho de saída**: portal, `/vazio soltar` e morte passam por ele. E o ponto de entrada agora é gravado **também** em quem entrou por portal/comando (`levarProExilio` grava se não houver registro) — não só pelo `/vazio exilar`. |
| 14 | **"o portal invocado no overworld não desenhava"** | O servidor só mandava o estado do portal para quem estava **no vazio**. Agora `tickPortaisInvocados` manda `envioEstado` **todo tick** para quem está na dimensão do portal, e o cliente guarda a **dimensão** que recebeu (`VazioPortalFx.dimPortal`) — o vórtice desenha em qualquer mundo. Pedestais continuam só no vazio. |

---

## O portal, por dentro

- **Bloco novo:** `rptag:escuridao` (`EscuridaoBlock`) — som **sculk**, indestrutível em
  survival (`strength(-1, 3600000)`), opaco e **luz própria 1** (só para o bloco não ler como
  buraco chapado no escuro). Textura própria escura com granulado
  (`textures/block/escuridao.png`).
- **Interação:** clique direito no miolo (`interagirPortal`) **ou** ficar **encostado** nele por
  0,7 s (`dentroDoPortal` — a hitbox do jogador fica a ~0,5 bloco do plano do miolo, então
  "entrar andando" não existe mais: o bloco é sólido).
- **Fundo:** uma camada de escuridão em `z+1` fecha a vista para trás — mesmo no /gamemode spectator.
- **Itens:** `/give <jogador> rptag:escuridao` (bloco) · `rptag:escuridao` também tem item de
  inventário com model próprio (aparece no criativo em "Blocos de construção").

## O que muda para quem já tem o mundo salvo

Nada é apagado. O `rptag:escuridao` **não** existe em mundos antigos (bloco novo) — os portais
que já estavam de pé são reconstruídos sozinhos no primeiro tick da dimensão (`garantirPortais`).
O `anelInicial` roda **uma vez por sessão** e só fora do raio da praça (`RAIO_PRACA + 6`).

---

## Como testar em 2 minutos

```
/vazio construir          # praça, 8 pedestais, 8 altares e o portal no lugar
/vazio abrir              # silencioso: só a animação de baixo para cima
/vazio portal             # invoca um portal novo (o antigo é desmontado)
/vazio portal 200 70 200  # idem, numa posição exata (funciona no overworld)
/give @s rptag:escuridao  # o bloco do portal na mão
```

Ao entrar pelo portal do overworld, ande até ele e **encoste** (ou clique): você cai na praça do
vazio. Para voltar, encoste no portal da praça — você reaparece **exatamente** onde encostou.

---

## Verificação técnica

- `javac` (Java 21, NeoForge 1.21.1): **0 erros**.
- `check.py`: **854 PASS / 0 FAIL** (9 checagens novas desta versão: passo só no chão, bloco
  `escuridao`, `desenharSombra`, rastro `sombra(`, `desmontarPortal`, `/vazio` silencioso,
  `dimPortal`/`envioEstado`, `anelInicial`, e a cortina preta **ausente**).
- Servidor de teste: `RP Tag 5.13.2` carregou, **0 exceções** no log, 25565 aberto,
  `anel de ruinas em volta da praca erguido (14 construcoes)`.
- Portal no overworld: miolo preenchido **de baixo para cima** (7 linhas) e leitura do bloco
  `rptag:escuridao` confirmada por comando (`execute if block` → *Test passed*).
