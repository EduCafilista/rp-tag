# QA das telas do tablet Neptune SMP

Data: 25/10/2026. Ambiente: Minecraft 1.21.1, NeoForge 21.1.228, RP Tag 4.7.0. Branch de trabalho: `feat/tablet-neptune`.

## Escopo e conclusão

Foram executados testes automatizados, comandos RCON no servidor local e inspeção por captura da ficha confirmada. Uma falha de salvamento foi reproduzida e corrigida.

**O QA visual e de interação de todas as telas ainda não está completo.** RCON permite acionar comandos e abrir a ficha, mas não gera cliques, teclas ou mudanças na escala da GUI. A captura da consulta confirma a composição em tela completa; não comprova navegação por todos os campos.

## Ambiente e evidência

- RCON conectado a `127.0.0.1:25575`, usando `tools/rcon.py`; senha lida do arquivo local e não publicada.
- Servidor do jogo em `127.0.0.1:25565`.
- Inicialmente não havia jogadores no servidor; o cliente estava em singleplayer. Após a conexão de um jogador, os comandos que dependem de jogador foram exercitados.
- A ficha foi aberta com `execute as @a[limit=1] at @s run tablet`.
- Captura nativa da consulta: `.superpowers/sdd/2026-10-25-tablet-neptune/qa-evidencias/ficha-tela-completa.jpg` — 594 × 372 pixels, incluindo a barra da janela.
- Cópias NBT antes/depois dos comandos ficaram na mesma pasta ignorada pelo Git. Os conteúdos privados não foram copiados para este relatório.
- Os cliques de automação foram recusados com `user input was detected in this window`; o usuário orientou usar RCON. Não foram simulados cliques por outro mecanismo para contornar essa restrição.

## Matriz de verificação

| Cenário | Método | Resultado |
| --- | --- | --- |
| Ficha confirmada abre pelo servidor | RCON e captura | Aprovado: consulta exibida no cliente. |
| Composição em tela completa | Captura e teste de layout | Aprovado na consulta capturada: sem moldura externa, sem mundo/HUD nas bordas. |
| Nitidez após a correção do desfoque | Captura | Aprovado na consulta capturada: textos e personagem nítidos. |
| Personagem e ficha lado a lado | Captura | Aprovado na consulta capturada, sem colisão entre as duas colunas. |
| Boot QuiralOS | Recursos e caminho conferidos no código | Pendente observar o boot completo e testar sua dispensa no cliente. |
| Nome, limite de 32 e rejeição de texto inválido | Testes de regras e codec | Aprovado no modelo de dados; digitação, seleção e colagem no widget ainda pendentes. |
| Idade, intervalo 1–999 e entradas inválidas | Testes automatizados | Aprovado na validação; comportamento visual do campo e mensagens ainda pendente. |
| Origem com limite 240, acentos e quebra de linha | Testes automatizados | Aprovado nos dados e na persistência; edição visual ainda pendente. |
| Habilidades com limite 600 | Testes automatizados | Aprovado no modelo de dados; edição visual ainda pendente. |
| História com limite 3.000 e múltiplas linhas | Testes automatizados | Aprovado nos dados; rolagem, seleção e colagem no widget ainda pendentes. |
| Revisão e confirmação | Testes de registro e fila cliente | Aprovado nas transições, normalização e bloqueio; clicar e revisar uma ficha longa ainda pendente. |
| Voltar/trocar etapas rapidamente | Teste da fila cliente | Aprovado na preservação da última etapa e das respostas; interação real ainda pendente. |
| Fechar com envio em andamento | Teste da fila cliente | Aprovado na drenagem da última versão local após a resposta anterior. |
| Resposta atrasada não apaga texto novo | Teste da fila cliente | Aprovado. |
| Resposta duplicada ou de outra ficha | Teste da fila cliente | Aprovado: não substitui os dados locais. |
| Rejeição temporária por limite de pedidos | Testes de orçamento e fila | Aprovado: nova tentativa após 250 ms sem descartar o texto. |
| Fechar sem editar e depois voltar a digitar | Regressão reproduzida em teste | Falhou inicialmente; corrigido para respeitar os 750 ms de pausa. |
| Comandos antigos não contornam ficha confirmada | RCON e comparação NBT antes/depois | Aprovado: nome, idade, descrição e remoção não alteraram `Personas` nem `TabletSheets`. |
| Reabrir não duplica tablet | RCON, contagem sem remoção | Aprovado: um tablet antes e um depois de duas reaberturas. |
| Comando de tela executado sem jogador | RCON | Aprovado: erro claro de jogador necessário, sem queda do servidor. |
| Liberação para alvo inexistente | RCON | Aprovado: jogador não encontrado, sem queda do servidor. |
| Dados reais gravados pelo servidor são recarregáveis | `save-all flush`, cópias NBT e teste de leitura | Aprovado: as fichas e personas mantêm os dados após carregar/salvar/carregar em memória. |
| Reinício real do processo do servidor | Não executado nesta rodada | Pendente. O teste de NBT não substitui o reinício. |
| Quatro dimensões de GUI | Teste matemático de layout | Aprovado em 640×360, 512×267, 426×240 e 320×180. Redimensionamento e escala dentro do cliente ainda pendentes. |
| Acesso de jogador comum à ficha de outro | Não executado com duas contas | Pendente. `execute as` via RCON conserva a permissão do console e não prova a restrição de um cliente comum. |
| Emotes por pergunta | Emotecraft não instalado, conforme o usuário | Não implementado; não aprovado como animação completa. |

## Falha encontrada e corrigida

### QA-01 — envio antecipado após fechar a ficha sem editar

Severidade: baixa, funcional. Não foi observada perda de dados; havia desrespeito ao intervalo de salvamento.

Reprodução automatizada:

1. Abrir um estado de ficha sem alterações.
2. Fechar a tela, solicitando `flush(false)`.
3. Reabrir e começar a digitar.
4. Avançar o relógio em apenas 100 ms e executar o tick cliente.

Resultado anterior: o fechamento sem alterações deixava `force=true`. O primeiro texto da próxima abertura era enviado antes dos 750 ms previstos.

Correção: `TabletClientState.flush` limpa a intenção de envio e retorna quando não há alteração nem confirmação pendente. O teste de regressão observa ausência de envio aos 100/749 ms e exatamente um envio aos 750 ms.

Arquivo: `src/main/java/dev/rptag/client/TabletClientState.java`.

## Cobertura automatizada acrescentada nesta rodada

Resultado final: **67 testes executados, zero falhas, zero erros e nenhum teste ignorado**, com os testes de cópia do save habilitados. `test build` concluiu com sucesso. O verificador de texturas também passou.

JAR atualizado: `build/libs/rptag-4.7.0.jar`. O cliente já aberto precisa ser reiniciado para carregar a correção QA-01; a captura visual desta rodada foi feita na instância anteriormente iniciada, cuja composição em tela completa já estava presente.

- Limites exatos e excesso de tamanho em todas as perguntas textuais.
- Idades vazias, zero, acima do teto, negativas, decimais, alfabéticas e longas.
- Salvar/reabrir cada etapa com acentos e quebras de linha.
- Normalização de nome e idade ao confirmar, sem perder a história.
- Confirmação recebida muda para leitura e impede novos envios.
- Navegação rápida preserva a última etapa.
- Resposta de outra ficha e resposta duplicada não alteram a sessão.
- Round-trip dos pacotes completos de atualização e resposta, incluindo `retryable`.
- Regressão do envio antecipado após fechamento sem alterações.
- Comparação de cópias do save real antes/depois das tentativas de alteração via comandos antigos.
- Leitura e regravação em memória das fichas reais salvas pelo servidor.

Os dois últimos testes usam `RPTAG_QA_SNAPSHOT_DIR` e são ignorados quando as cópias do cenário não estão disponíveis. Não dependem de dados privados fixos no repositório.

## Verificações que ainda exigem o cliente

Para concluir o QA de interação, ainda é necessário executar uma rodada controlada no Minecraft, sem outra automação concorrente: boot e dispensa, formulário vazio, erros, digitação/colagem/seleção, Tab/Enter/Esc, todas as perguntas, revisão longa, confirmação e rolagem em diferentes escalas.

Não classificar esses cenários como aprovados somente porque a suíte de dados passou. Não considerar o visual aprovado pelo usuário a partir deste relatório.
