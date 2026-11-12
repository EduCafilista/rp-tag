# RP Tag 5.41.0 — o umbrella ABRE de verdade, mais modelado e sem brilho + a power wheel só pra quem tem o vazio

**O arquivo para baixar:**
[rptag-5.41.0.jar](https://github.com/EduCafilista/rp-tag/releases/download/v5.41.0/rptag-5.41.0.jar)
· 36.890.066 bytes · `md5 20ead4d58cea17a8bad35dae153df6b1`
· `sha256 7dc4c4108cf7c70dd06efce39d84d93eb5bbac2c93829423e295149779935206`

**Página da release:** https://github.com/EduCafilista/rp-tag/releases/tag/v5.41.0

> **Tire o jar antigo da pasta `mods`.** Um jar do mod por vez — se os dois ficarem lá, o jogo
> pode carregar o velho. Troque no servidor **e** no cliente.

---

## 1. "não tem a animação dele aberto, não fica aberto" — era o mecanismo errado

Até a 5.40.0 o guarda-chuva só era considerado aberto **enquanto você estava segurando o botão
direito** (`isUsingItem`), e o modelo aberto era escolhido por um predicado que o cliente tinha
de registrar. Essa corrente tinha dois elos fracos: qualquer coisa que cancele o "uso" fecha o
guarda-chuva, e um predicado que ninguém registra vale **0 para sempre** (foi o bug da 5.39).

**Agora o estado mora no próprio item**, num componente que o Minecraft já sincroniza sozinho:

| `custom_model_data` | estado |
|---|---|
| **0 a 7** | **FECHADO** (o número é a cor) |
| **8 a 15** | **ABERTO** (a cor + 8) |

`custom_model_data` é predicado **vanilla**: o jogo troca o modelo 3D na mão **sem nenhum código
de cliente**. Não há mais nada para registrar, e o modelo não pode "não achar" o estado.

Na prática:
- **clique direito** → abre. Clique de novo → fecha. Agachado → fecha na hora.
- fica aberto **65 segundos** e fecha sozinho (relógio no `inventoryTick` do servidor).
- `/umbrella abrir` e `/umbrella fechar` para cena e teste.
- `/umbrella info` agora mostra cor, valor do `custom_model_data`, se o modelo é ABERTO ou
  fechado e se a proteção do sol está ativa.

> **Só aberto protege do sol** — e essa é a mesma regra do servidor
> (`VampiroSistema.guardaChuvaAberto`), agora lendo o estado do item.

## 2. "ele não precisa ficar encantado"

Saiu o `isFoil`. O brilho de encantamento não aparece mais no guarda-chuva.

## 3. "não está bonita, deixe mais modelada"

**Textura** refeita com 5 regiões e volume (antes era uma listra diagonal chapada, o que fazia
todo cubo virar uma mancha):
- tecido **claro** e **escuro** com gomos, costura entre eles, nervura pegando luz e sombra de
  cima pra baixo;
- madeira com **veio e nó**; metal com **brilho diagonal**; tecido preto para as varetas.

**Geometria** reconstruída em octógonos — e o motivo técnico: o Minecraft só aceita rotação de
**−45° a 45°** em modelo de item (90° faz o jogo **rejeitar** o modelo e o item volta a aparecer
rosa/preto). Então "redondo" tem de ser feito com quadrados: **dois quadrados, um girado 45° =
octógono**. Com isso:
- cúpula aberta em 4 degraus, cada um com borda fina e escura (a "barriga" do pano);
- pano enrolado do fechado em 5 anéis que engordam pra cima;
- **as 8 pontas das varetas** saindo da aba e da cúpula, e bojo de metal no topo.

## 4. "a power wheel aparece mesmo sem o player ter o vazio ligado"

Antes a roda (tecla R) abria para **qualquer jogador** — o servidor só recusava na hora de
soltar o poder. Agora o servidor manda o estado de acesso (`VazioAcessoPayload`, calculado em
`VazioPoderes.temPoder`) **no login, na troca de dimensão e sempre que muda**. Sem o dom:

- a roda **não abre** (com um aviso curto em vez de um menu que não funciona);
- **não é desenhada** (nem no meio da animação);
- os atalhos 1–6 e o clique **não respondem**;
- se você perder o dom com a roda aberta, ela fecha e desarma a habilidade.

Quem decide continua sendo o **servidor** — o cliente só obedece.

## 5. Varredura do mod ("passe por todo o mod e veja quais erros")

Entrou o `arte/auditar.py`, que roda junto com o build e reprova:

- pacote de rede **sem registro**;
- classe com `@EventBusSubscriber` e **sem nenhum `@SubscribeEvent`** (isso já derrubou o
  cliente uma vez);
- som, textura ou receita apontando para **arquivo que não existe**;
- item registrado **sem nome** nos dois idiomas;
- comando com `literal()` repetido no mesmo lugar.

O que ele achou e já está corrigido nesta versão: **3 itens apareciam com o nome cru** no
inventário — `escuridao`, `coracao_canon` e `chave_julgamento` ganharam nome em PT-BR e inglês.

---

## Testes desta versão

- `check.py`: **1302 PASS / 0 FAIL**
- `arte/auditar.py`: **0 erro**
- `arte/validar.py`: 54 ok, 0 falhas
- Servidor: `RP Tag 5.41.0 (rptag)` → `Done (1.202s)!`; `/umbrella cores` responde as 8 cores
- Jar: sem sonda de teste, 19 modelos de umbrella, 8 texturas × 3 (item, cúpula, HUD)

**Prévias fora do jogo:** `arte/previa_umbrella.png` (as 4 vistas) e
`arte/previa_umbrella_cores.png` (as 8 cores).
