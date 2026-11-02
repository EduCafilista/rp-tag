# RP Tag — todos os comandos (5.11.1)

> Lista rápida. No jogo, `/rp ajuda` mostra isso no chat (a parte de admin só aparece para quem é OP).

## Jogador

| comando | o que faz |
|---|---|
| `/rp` | liga/desliga o modo RP |
| `/rp on` · `/rp off` | define direto |
| `/rp status` | seu estado: RP, persona, chat local, balão, cor, vidas |
| `/rp ajuda` (ou `/rp help`) | a lista de comandos no chat |
| `/g <msg>` | fala GLOBAL (o servidor todo ouve) |
| `/s <msg>` | GRITA (alcance maior) |
| `/w <msg>` | sussurra (só quem está colado) |
| `/me <ação>` | ação em 3ª pessoa (sem balão) |
| `/pensar <msg>` | pensamento (balão em itálico com parênteses) |
| `/do <texto>` | narração de ambiente |
| `/roll [NdN][+bônus] [motivo]` | dados (`/roll 1d20+3 percepção`) |
| `/tablet` | abre o TABLET com a ficha do personagem |
| `/persona criar <nome>` | cria sua persona |
| `/persona idade <n>` · `/persona desc <texto>` · `/persona ver [jogador]` · `/persona off` | edita/ve/apaga a persona |
| `/balao` | tela de personalização do balão |
| `/balao modo on\|off` | fala só no balão (sem chat) |
| `/balao typewriter on\|off` | o texto do balão se escreve sozinho |
| `/afk` | entra/sai do AFK |
| `/neptune` | resumo dos comandos de evento |

### Títulos

| comando | o que faz |
|---|---|
| `/titulo definir <jogadores> <etiqueta>` | põe a classe (`Chronos`, `Sagrado`, `Warden`…) |
| `/titulo personalizado <jogador> <texto>` | o apelido entre colchetes (`O Ferreiro`) |
| `/titulo cor <jogadores> <cor>` | cor da etiqueta (25 nomes ou `#RRGGBB`) |
| `/titulo cor pessoal <cor>` | cor do seu título pessoal |
| `/titulo limpar <jogadores>` | tira o título |
| `/titulo ver <jogador>` · `/titulo lista` · `/titulo paleta` | consulta |

## Admin (OP nível 2)

| comando | o que faz |
|---|---|
| `/rp set <jogador> on\|off` | define o RP de outro |
| `/rp admin medo <jogador> [1-30s]` | tela escurece + tremor de medo |
| `/rp admin avisar <jogadores> [estilo] <texto>` | texto na tela (estilos abaixo) |
| `/rp admin avisozona <id> <texto>` | avisa todo mundo DENTRO da lorezone |
| `/rp admin chatlocal on\|off` | chat por proximidade (global do servidor) |
| `/rp admin saidafx on\|off` | animação de saída dos jogadores |
| `/rp admin titlevanilla on\|off` | devolve/tira o `/title` do Minecraft puro (padrão: fora) |
| `/rp admin bolha <jogador> on\|off` | modo balão de outro |
| `/rp admin balao <jogador> on\|off` | libera/bloqueia o balão |
| `/rp admin balaolist` | quem tem balão liberado |
| `/rp admin bolhas on\|off` | balões do servidor inteiro |
| `/rp admin canon on\|off` | modo de perder vidas (quem morre perde uma) |
| `/rp admin vida <jogador> tirar\|dar [n]` | mexe na vida canônica na hora (0..5) |
| `/rp admin vida <jogador> definir <n>` | define o total |
| `/rp admin vida <jogador> ver` | quantas restam (nome vale offline) |
| `/rp admin vida lote A,B,C tirar 1` | em lote |
| `/rp admin afk tempo <s>` · `/rp admin afk status` | tempo parado até virar AFK |
| `/tablet ver <jogador>` | abre a ficha de outro (leitura) |
| `/tablet liberar <jogador>` | libera a ficha confirmada para edição |

**Estilos do `/rp admin avisar`:** `classico`, `pesadelo`, `pesadelo2`, `runico`, `real`, `morte`,
`inferno`, `gelo`, `divino`, `void`, `sangue`, `demoniaco`, `egipicio`, `expurgo`, `ciberpsicose`,
`energia`, `conhecimento`, `allforone`, `onefallall`, `morteop`, `sangueop`, `medoop`.
Alvos: nomes (aceita lista `Ana,Beto`) ou `@a`.

### Lorezones

| comando | o que faz |
|---|---|
| `/lorezone criar <id> <raio> <titulo>` | cria a zona onde você está |
| `/lorezone texto <id> <texto>` | texto/aviso da zona |
| `/lorezone vertical (<id>\|<minY>)` | altura da zona |
| `/lorezone mostrar <id> [segundos]` · `/lorezone esconder` | vê a zona (debug) |
| `/lorezone varinha` | varinha de seleção |
| `/lorezone som <id> <som>` | som da zona |
| `/lorezone musica <id> (<som>\|intervalo\|unica\|1x)` | música da zona |
| `/lorezone baixar <url>` · `/lorezone musicas` | música custom (API) |
| `/lorezone aviso <id> (off\|<estilo>)` | aviso automático ao entrar |
| `/lorezone avisos reset <jogador>` | zera os avisos já vistos |
| `/lorezone listar` · `/lorezone remover <id>` | gerencia |

### Evento NEPTUNE

| comando | o que faz |
|---|---|
| `/terremoto iniciar [segundos]` | a terra colapsa (som + tremor + lentidão em pulsos) |
| `/terremoto parar` | desliga |
| `/terremoto sair (todos\|<jogador>)` | a pessoa some nas sombras (kick em 4 s) |
| `/terremoto engolir <jogador>` | o chão leva a pessoa |
| `/terremoto fenda [<jogador>]` · `/terremoto fechar` | abre/fecha fendas no chão |
| `/terremoto status` | estado do evento |
| `/vazio ligar` · `/vazio desligar` | **Deus do Vazio**: raios vermelhos saindo do corpo (funciona voando) + trilha + medo — fica salvo no mundo |
| `/vazio apagar <jogador>` | apaga da existência |
| `/vazio exilar <jogador>` · `/vazio soltar <jogador>` | manda pro exílio / solta num lugar aleatório |
| `/vazio entrar` | o admin entra no exílio para testar |
| `/bicho aqui` · `/bicho <pos>` · `/bicho limpar` | caixinha de música (o bicho pula quando alguém chega) |

## Vanilla (não é do mod)

- `/title <alvo> title|subtitle|actionbar|times|clear|reset` — **comando do Minecraft puro**, sai do jogo
  por padrão (5.10.1). Volta com `/rp admin titlevanilla on`.
- O do mod é o **`/titulo`** (com "i").
