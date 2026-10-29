# Tablet de personagem Neptune SMP

O RP Tag 4.7.0 adiciona um tablet com criação gradual de ficha. O alvo de compilação é Minecraft 1.21.1 e NeoForge 21.1.228. Cliente e servidor precisam do mesmo JAR: o protocolo do RP Tag passou de 5 para 6.

## Uso

O jogador recebe uma unidade no primeiro login após a atualização. Se o inventário estiver cheio, basta liberar um espaço e usar `/tablet`. Esse comando também recupera o item quando não há outro tablet no inventário. O tablet não guarda dados privados no ItemStack: ao ser transferido, abre a ficha de quem o usa.

Use o botão direito com o tablet, ou `/tablet`, para abrir o boot QuiralOS e responder nome, idade, origem, habilidades e história. Habilidades são narrativas e não concedem efeitos. A revisão permite voltar a qualquer pergunta antes de confirmar.

O servidor recebe o rascunho após uma pausa na digitação, ao mudar de etapa e ao fechar a tela. O indicador de salvamento só confirma uma resposta aceita pelo servidor. O texto mais recente permanece na sessão enquanto há envio em andamento. Em caso de conflito, confira os dados antes de escolher usar a ficha do servidor: essa ação substitui a cópia local. Textos nunca enviados não são recuperáveis após encerramento abrupto do cliente.

Após confirmar, a ficha fica em leitura. Nome e idade passam para a persona existente; sua descrição pública é preservada e o estado RP/OFF RP não muda. A história completa é privada, disponível apenas ao dono e a administradores autorizados.

## Administração

- `/tablet ver <jogador>`: consulta a ficha de um jogador online, sem permitir edição por essa tela.
- `/tablet liberar <jogador>`: libera o rascunho para edição. A ficha confirmada anterior continua válida até uma nova confirmação.

Os dois comandos administrativos exigem nível de permissão 2. Após uma ficha ser confirmada, os comandos antigos de alteração de `/persona` orientam o jogador a usar o tablet, evitando contornar o bloqueio. Personas sem ficha confirmada conservam esses comandos.

## Interface e modelo

O boot usa o logo original QuiralOS; a ficha usa a identidade Neptune SMP. O modelo do item foi preparado no Blender 5.2 em low-poly, revisado e exportado pelo MCP do Blockbench. Há duas texturas, 15 cubos e 85 faces exportadas.

A tela é nativa do Minecraft, com a skin real no preview, campos de texto, revisão com rolagem e transições curtas. A opção de acessibilidade de efeitos de tela do jogo reduz o movimento da interface. O preview muda discretamente o olhar entre etapas. Não há integração de emotes nesta entrega: o usuário informou que Emotecraft não está instalado.

O desfoque automático de fundo foi desativado nesta tela para que ele não seja aplicado novamente por cima da interface. Os campos usam a edição nativa com aparência própria. A interface ocupa a tela inteira, sem moldura externa e sem deixar o mundo aparecer nas bordas, conforme as referências iniciais. Personagem e formulário têm espaçamento interno; o formulário fica à direita e se adapta às escalas menores da GUI.

## Validação realizada

- Linha de base do projeto aprovada antes das alterações.
- Compilação e testes com NeoForge 21.1.228: 58 testes passaram, sem falhas.
- Cobertura nova: regras e limites, acentos/quebras de linha, dados NBT malformados, preservação de mundo antigo, migração de nomes legados, confirmação/bloqueio/liberação, revisão obsoleta, fila de envio, resposta atrasada, nova tentativa após limite temporário e dimensões da GUI.
- Verificador do modelo aprovado: referências de PNG, UV, faces coplanares e correspondência entre imagens incorporadas e exportadas.
- Cliente iniciado com RP Tag 4.7.0 / NeoForge 21.1.228 em `run-tablet-client`; usuário abriu a ficha e enviou a captura que revelou o desfoque indevido da primeira interface.
- Servidor dedicado iniciado com sucesso em diretório separado, vinculado a `127.0.0.1:25566`; encerrado após a verificação de inicialização.
- Revisão independente identificou o limite de pedidos no mesmo tick e nomes legados incompatíveis. Ambos receberam testes de regressão e correções.

## Verificações ainda pendentes

Reabrir o cliente com o JAR corrigido e conferir visualmente as cinco perguntas, revisão longa, botões, escala da GUI e encaixes do item. A captura enviada pelo usuário é anterior à correção de desfoque e composição; não comprova a aparência da versão corrigida.

Testes com duas contas, inventário cheio e reinício completo do servidor com uma ficha preenchida ainda precisam de execução manual. Os testes automatizados de dados e fila não substituem esses cenários integrados. Não usar um cliente antigo com o servidor atualizado.
