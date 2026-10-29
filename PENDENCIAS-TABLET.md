# Tablet Neptune SMP — problemas e ponto de parada

Desenvolvimento interrompido a pedido do usuário em 25/10/2026 e retomado posteriormente para corrigir a composição em tela completa, após nova captura e instrução do usuário. Este arquivo registra os problemas conhecidos e o estado do trabalho. Não considerar o visual ou a entrega final aprovados.

## Correção solicitada após a pausa: tela completa

O usuário esclareceu que a referência correta era a interface em tela completa apresentada no início da sessão. A janela com moldura foi uma interpretação incorreta da implementação, não uma exigência do usuário.

O código foi ajustado para ocupar toda a largura e altura da GUI, com fundo espacial opaco, sem moldura externa nem mundo visível nas bordas. O personagem continua à esquerda e o formulário à direita, com espaçamento interno. As margens externas de 10%/14% descritas abaixo são histórico da versão reprovada e não devem ser restauradas. A aparência final ainda precisa ser observada no Minecraft após reiniciar o cliente com a versão corrigida.

## 1. Interface no Minecraft reprovada

Atualização de QA: a consulta em tela completa foi aberta por RCON e inspecionada por captura. O fundo cobre a área do jogo e os textos estão nítidos nessa tela. As tentativas pelos comandos antigos não alteraram a ficha confirmada, conforme comparação de cópias do save. Uma falha de envio antecipado após fechar sem editar foi reproduzida e corrigida. O relatório detalhado está em `docs/QA-TELAS-TABLET-2026-10-25.md`; navegação, entrada de texto e escala da GUI no cliente continuam pendentes. Os problemas descritos abaixo registram a versão inicialmente reprovada.

O usuário avaliou a primeira versão dentro do jogo como horrível. A implementação não reproduziu adequadamente a composição limpa aprovada na prévia web.

Problemas observados na captura enviada pelo usuário:

- Fundo, personagem, títulos e mensagens apareciam borrados, enquanto o campo de texto e os botões permaneciam nítidos.
- O campo de texto exibia o visual padrão do Minecraft: fundo preto e contorno branco forte, destoando da proposta.
- O buraco negro estava grande demais e parecia um círculo atravessado por uma linha, com acabamento inferior ao esperado.
- O personagem ocupava espaço excessivo e a distribuição dos elementos não tinha o equilíbrio da referência aprovada.
- A tela tinha aparência de painel quase em tela cheia; a moldura e os espaços não transmitiam bem a ideia do tablet.
- Tipografia, proporções e acabamento geral precisam ser avaliados dentro do Minecraft, não apenas na prévia web.

A captura também mostra uma dica de tutorial do próprio Minecraft no canto superior direito. Esse elemento é do jogo, não do tablet.

## 2. Correções de interface escritas, mas ainda não aprovadas

Após a captura, foram feitas alterações no código:

- Identificada a causa do borrão: `Screen.render()` chama `renderBackground()` antes de desenhar os widgets. Como o tablet já havia desenhado o fundo, os textos e o personagem, o efeito era reaplicado por cima deles. A tela passou a sobrescrever esse método sem aplicar o desfoque novamente.
- Campos simples passaram a usar a edição nativa sem a borda padrão, com fundo próprio e encaminhamento dos cliques na área visual.
- Campos multilinha receberam fundo próprio.
- Moldura passou a ocupar até 90% da largura e 86% da altura da GUI, com limites máximos.
- Personagem e buraco negro foram reduzidos; foram ajustadas margens, cores e composição.
- Foram adicionados testes de dimensões para quatro tamanhos de GUI.

**Essas alterações compilaram, mas a aparência corrigida não foi conferida visualmente no jogo nem aprovada pelo usuário.** O cliente já aberto continuava com as classes da versão anterior carregadas. É necessário reiniciar o Minecraft com o JAR atualizado para avaliar a correção.

Não afirmar que o visual está resolvido com base apenas na compilação ou nos testes de dimensões.

## 3. Falhas funcionais encontradas e corrigidas no código

### Salvamento e confirmação no mesmo tick

O servidor inicialmente rejeitava a segunda atualização recebida no mesmo tick. O fluxo normal de salvar e depois confirmar podia produzir exatamente essa sequência, especialmente em conexão local.

A rejeição temporária era tratada pelo cliente como conflito permanente, e a única ação oferecida podia substituir texto local mais recente pela versão anterior do servidor.

Correção escrita:

- Orçamento de até 20 atualizações por janela de 20 ticks.
- Resposta com indicação explícita de erro temporário.
- Nova tentativa após 250 ms, preservando o texto local e a intenção de confirmar.
- Testes de regressão para o limite e a recuperação.

A revisão independente confirmou a correção por leitura. O cenário ainda não foi exercitado com duas contas no jogo.

### Nomes antigos incompatíveis com a nova validação

Personas antigas podiam conter códigos de cor aceitos pelos comandos anteriores. A nova validação rejeitava esses nomes e podia interromper a abertura da ficha ou a entrega no login.

Correção escrita:

- Limpar apenas a cópia destinada ao rascunho, removendo formatação e caracteres proibidos e respeitando o limite de nome.
- Preservar a persona pública existente até a confirmação pelo jogador.
- Teste de regressão para nome legado com formatação.

A revisão independente confirmou a correção por leitura. A migração de um mundo real ainda precisa de teste integrado.

### Entrada NBT sem UUID

Um teste revelou que uma entrada de ficha sem UUID causava exceção no carregamento. Foi adicionada verificação de UUID e do tipo da seção antes de ler a entrada. O teste passou após a correção.

## 4. Modelo e texturas

Os arquivos editáveis do tablet estão em `arte/modelos/tablet_neptune/`. O modelo foi produzido em low-poly no Blender 5.2 e inspecionado e exportado pelo MCP do Blockbench.

Problemas anteriores de textura identificados:

- Faces coplanares sobrepostas nas bordas, com risco de cintilação.
- Contorno quadrado visível ao redor do logo traseiro.
- Regiões de cor com pouca margem no atlas, com risco de mistura de tons à distância.
- Necessidade de ajustar o UV da tela e definir explicitamente a textura de partículas.

Foram corrigidos nos recursos exportados e conferidos em quatro vistas no Blockbench. O logo original QuiralOS foi preservado. O modelo atual tem 15 cubos, 85 faces exportadas e duas texturas.

**Ainda pendente:** conferir o modelo dentro do Minecraft nas duas mãos, no inventário, no chão, sob diferentes iluminações e distâncias. A verificação estrutural do JSON não comprova todos esses resultados.

Arquivos históricos e capturas anteriores continuam na pasta de arte. Para a versão atual, usar `tablet_neptune_blockbench.bbmodel`, o JSON em `export/assets/rptag/models/item/tablet.json` e as capturas `previas/corrigido-*.png`.

## 5. Animações incompletas

O usuário informou que Emotecraft não está instalado. Não foi adicionada dependência desse mod e não existe integração de emotes nesta versão.

Há renderização da skin real, mudança discreta do olhar entre etapas e transições de tela. Isso **não equivale às animações específicas do personagem por pergunta solicitadas originalmente**. Essa parte não deve ser apresentada como concluída.

## 6. Testes realizados e seus limites

Executado com Minecraft 1.21.1 e NeoForge 21.1.228:

- Linha de base dos testes existentes antes das alterações.
- Compilação e geração de JAR após as mudanças.
- Suíte final: 58 testes, sem falhas ou erros.
- Verificação estrutural do modelo e dos recursos incluídos no JAR.
- Inicialização do cliente de desenvolvimento com RP Tag 4.7.0.
- Inicialização de servidor dedicado em mundo separado, vinculado somente a `127.0.0.1:25566`; o servidor temporário foi encerrado.
- Revisão independente de dados, permissões e sincronização; dois achados foram corrigidos e revistos novamente.

Não concluído:

- Inspeção visual da interface após a última correção.
- Aprovação visual do usuário dentro do Minecraft.
- Teste completo com duas contas, incluindo acesso negado à ficha alheia.
- Entrega e recuperação com inventário cheio no jogo.
- Preenchimento, fechamento, reconexão e reinício completo do servidor com dados reais de uma ficha.
- Todas as etapas com textos máximos, colagem, acentos, rolagem e escalas de GUI dentro do jogo.
- Animações específicas por pergunta e integração com Emotecraft.

A ferramenta de inspeção da janela do Minecraft aguardou autorização do aplicativo e expirou. A captura fornecida pelo usuário foi a evidência visual disponível; não inventar capturas ou resultados de testes que não ocorreram.

## 7. Estado do repositório e do artefato

- Pasta: `F:/RP-ON/rp-tag`.
- Branch de desenvolvimento: `feat/tablet-neptune`.
- Commit local da implementação: `4bd8648` — `feat: implementar tablet de personagem Neptune SMP`.
- JAR gerado: `build/libs/rptag-4.7.0.jar`.
- SHA-256 do JAR gerado: `E1796BE271D14FFC780A4A830DE3754EC4E40C3E11B55631E6D2888F977A7CA5`.
- Não foi feito merge no `main`, push ou publicação.
- Os arquivos de trabalho de modelagem em `arte/modelos/` permanecem locais e não foram incluídos nesse commit. Os recursos do tablet usados pelo mod foram incluídos.
- Mudanças locais anteriores em `portais-da-lore/LORE.md`, outras artes e logs foram preservadas fora do commit.
- O cliente que o usuário estava usando não foi encerrado automaticamente.

## 8. Orientação para eventual retomada

1. Começar pela interface reprovada e pela captura do usuário, sem presumir aprovação da versão atual.
2. Reiniciar um cliente de teste com o JAR corrigido e observar o resultado real antes de fazer novas promessas sobre o visual.
3. Recuperar a composição da referência aprovada, ajustando tamanho do personagem, acabamento do buraco negro, moldura, campos, botões e tipografia.
4. Apresentar a interface dentro do Minecraft para avaliação.
5. Depois da revisão visual, executar os cenários integrados pendentes de salvamento, permissões, entrega e reinício.
6. Tratar as animações específicas como trabalho pendente, sem declarar equivalência com a simples mudança do olhar.

A nova instrução do usuário autorizou a correção para tela completa. Outras alterações de escopo devem respeitar as referências iniciais e este registro de pendências.
