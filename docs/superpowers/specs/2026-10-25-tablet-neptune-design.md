# Tablet de personagem do Neptune SMP

## Objetivo e decisões aprovadas

Adicionar ao RP Tag um tablet permanente para criar e consultar a ficha do personagem. O jogador recebe o item, usa o botão direito e responde às perguntas por etapas. A interface usa Minecraft nativo, sem navegador incorporado.

O visual de referência é a prévia `tablet-neptune-clean-v8.html`, na sessão local `.superpowers/brainstorm/272-1792937753/content/`. O boot mostra o símbolo original e o nome QuiralOS. A ficha usa a identidade Neptune SMP, fundo espacial discreto, buraco negro atrás do personagem, poucos textos auxiliares e botões claros. A prévia é uma referência visual; seus dados de exemplo e suas abas de demonstração não fazem parte do mod.

Esta especificação consolida as decisões da conversa. As regras de limites, recuperação e concorrência abaixo são propostas técnicas para revisão.

## Plataforma e integração

- Minecraft 1.21.1, compilação e testes com NeoForge 21.1.228.
- Funcionalidade dentro do RP Tag, instalada em cliente e servidor.
- O projeto atualmente compila com 21.1.249. A troca exige compilar e verificar as funcionalidades existentes; não basta alterar o número.
- Reutilizar `RPTagMod.ITEMS`, os registros de rede de `ModNetworking` e o armazenamento de mundo de `RPWorldData`.
- Manter o formato atual de `Persona` para preservar nomes, idade, descrição, chat e TAB existentes. Os campos novos ficam em um registro separado por UUID.
- Incrementar a versão do protocolo quando os novos pacotes forem registrados. Cliente e servidor precisam usar o mesmo JAR.

## Item e entrega

Registrar `rptag:tablet`, com pilha máxima de uma unidade, textura e nome traduzido. A primeira entrada após a atualização também entrega o tablet aos jogadores existentes. Um marcador persistente por UUID impede entregas repetidas em cada login.

Se o inventário estiver cheio, adiar a entrega e informar o jogador; não marcar como entregue antes da inserção efetiva. Permitir nova tentativa com `/tablet`. Esse comando também abre a própria ficha e permite recuperar uma unidade perdida, verificando antes se já existe uma no inventário. Não conceder benefícios de combate ou habilidades pelo item.

O tablet abre a ficha de quem o usa. Não contém os dados privados no próprio ItemStack e não permite consultar o dono anterior quando transferido.

## Modelo do tablet

Produzir um modelo tridimensional próprio do item no Blockbench, disponível nesta máquina. O usuário também autorizou Blender 5.2, instalado localmente, com a exigência de geometria low-poly caso seja utilizado. Priorizar Blockbench no formato Java Block/Item para exportação direta ao Minecraft, sem acrescentar biblioteca de animação ou renderizador próprio apenas para o item.

O tablet terá corpo fino e retangular, moldura escura, tela levemente recuada e poucos detalhes físicos. Usar geometria de cubos, textura pixelada e proporções coerentes com a mão do jogador. Evitar subdivisão, peças pequenas sem função visual e efeitos que dependam de materiais do Blender. A frente terá uma representação estática da tela; a interface interativa abre ao usar o item, sem exigir uma tela dinâmica renderizada no mundo.

Preservar símbolo e nome QuiralOS no boot da interface e a identidade Neptune SMP na ficha. Entregar o projeto editável `.bbmodel`, a textura PNG e o modelo JSON exportado, com ajustes de apresentação em primeira pessoa, terceira pessoa, inventário e item no chão. Se o Blender for usado para estudo ou visualização, entregar também o `.blend` low-poly; o modelo final ainda deve respeitar o formato aceito pelo Minecraft.

Antes de finalizar o recurso, apresentar uma prévia da frente, traseira e espessura do modelo. Validar os encaixes nas mãos direita e esquerda e a leitura do ícone no inventário dentro do jogo. O modelo do item não substitui a skin real do jogador exibida na interface.

## Fluxo da ficha

1. Boot curto do QuiralOS, com progresso visual e transição para o conteúdo.
2. Nome do personagem, até 32 caracteres.
3. Idade inteira entre 1 e 999 anos, mantendo o intervalo já usado por `/persona idade`.
4. Origem: de onde o personagem veio, até 240 caracteres. Não perguntar raça.
5. Habilidades narrativas, até 600 caracteres, sem efeitos de jogo.
6. História, até 3.000 caracteres, em campo com múltiplas linhas.
7. Revisão das cinco respostas, com retorno para corrigir qualquer etapa e confirmação explícita.

Nome, idade, origem, habilidades e história são obrigatórios na confirmação. O rascunho aceita campos vazios. Nome e idade existentes são preenchidos na primeira abertura; a descrição anterior permanece preservada, sem ser transformada automaticamente na história.

Voltar preserva as respostas. Enter avança em campos simples; no campo de história e nos demais campos com múltiplas linhas, insere uma quebra. Esc fecha a tela. A tela não pausa o servidor. O boot pode ser dispensado com uma ação de continuar, respeitando quem prefere menos animação.

Após a confirmação, o tablet mostra a ficha em leitura. Não oferecer edição ao jogador até um administrador liberar. A confirmação atualiza nome e idade de `Persona`, preserva sua descrição e usa a sincronização existente para nomes no chat e TAB. Não mudar automaticamente o estado RP/OFF RP.

## Dados, salvamento e autoridade

Guardar por UUID: rascunho, última etapa aceita, ficha confirmada, permissão de edição, marcador de entrega e revisão numérica do registro. Salvar em seção adicional de `RPWorldData`; mundos antigos sem essa seção começam com ficha vazia e personas preservadas.

O servidor é a autoridade. Pacotes enviados pelo jogador não escolhem o UUID de destino. O servidor verifica o remetente, estado de edição, limites, caracteres de controle e revisão antes de aceitar a alteração. Limitar os comprimentos também na decodificação da rede. Executar alterações no fluxo principal do servidor.

Salvar o rascunho ao sair de cada etapa e ao fechar a tela. Enviar também alterações após uma pausa curta de digitação, sem um pacote por tecla. O cliente só apresenta confirmação de salvamento após a resposta do servidor. Durante uma solicitação, impedir confirmações repetidas. Em caso de rejeição, manter o texto local e mostrar como tentar novamente.

Uma revisão antiga não substitui dados mais recentes. Ao detectar conflito, o servidor recusa a escrita e devolve o estado atual para recuperação. A confirmação valida todos os campos novamente e salva ficha, bloqueio e persona na mesma operação no fluxo principal.

O salvamento usa o mecanismo SavedData do Minecraft. A confirmação significa aceitação pelo servidor e marcação dos dados para salvar; não equivale a garantia contra perda em falha abrupta antes da gravação do mundo. Fechar normalmente e reiniciar o servidor deve preservar o registro.

Durante uma edição liberada, a ficha confirmada anterior continua válida até a nova confirmação. O rascunho não altera o nome público antecipadamente.

## Administração e privacidade

Adicionar `/tablet ver <jogador>` e `/tablet liberar <jogador>` para administradores com nível de permissão 2. O primeiro abre a ficha em leitura; o segundo inicia ou retoma a edição liberada e informa o alvo. A primeira versão administra jogadores online.

História, origem e habilidades são enviados somente ao próprio jogador ou ao administrador autorizado. Nome, idade e descrição de persona conservam a visibilidade pública já existente no RP Tag. Não transmitir a ficha completa a todos os clientes.

Os comandos `/persona criar`, `/persona idade`, `/persona desc` e `/persona off` devem respeitar o registro confirmado: instruir o jogador a solicitar liberação e usar o tablet. Mesmo após a liberação, alterações passam pelo rascunho para preservar a ficha anterior. Personagens antigos sem ficha confirmada conservam os comandos existentes.

## Interface nativa e animação

Renderizar a skin real do jogador ao lado do formulário. Usar widgets de texto nativos, incluindo suporte a múltiplas linhas, seleção, acentos, colagem e navegação por teclado. Revisão e ficha longa têm rolagem. Dimensionar a composição conforme a escala da GUI, garantindo acesso aos botões em telas menores.

Reaproveitar o desenho espacial procedural de `BubbleStyleScreen` onde fizer sentido, evitando copiar toda a tela de personalização. O buraco negro é uma composição leve de primitivas ou textura estática pequena, sem vídeo. Usar os arquivos originais locais de identidade, sem alterar a arte ou remover assinaturas.

Transições entre perguntas devem ser curtas e preservar o foco no campo correto. O modelo do jogador reage à etapa somente no preview, sem movimentar sua entidade no mundo nem transmitir emotes aos demais jogadores.

A integração Emotecraft requer uma verificação específica da versão instalada e da possibilidade de animar apenas o preview em NeoForge 1.21.1. Não está comprovada pela prévia web. Ela deve ser isolada e opcional: ausência ou incompatibilidade não impede abrir o tablet. Se não houver API adequada, manter o personagem renderizado e as transições de tela, registrar a limitação e apresentar a alternativa antes de considerar concluído o pedido de animações por pergunta. Não substituir silenciosamente essa exigência por um recurso declarado como pronto.

## Escopo dos arquivos

- `RPTagMod`: registro do tablet.
- Novas classes de ficha e serviço do tablet: validação, autorização, entrega e comandos.
- `RPWorldData`: dados adicionais e migração por ausência de seção.
- `ModNetworking` e handler cliente: pedidos, respostas e abertura da tela, sem carregar classes de renderização em servidor dedicado.
- `ServerEvents`: integração da entrega no login e sincronização da persona após confirmação.
- `PersonaCommands`: proteção das alterações de fichas confirmadas.
- Nova tela cliente: boot, perguntas, revisão e leitura; integração isolada de animação quando comprovada.
- Recursos: traduções, modelo e textura do item, símbolo QuiralOS e identidade Neptune.
- Testes e documentação de uso do tablet.

Não incluir alterações em portais, avisos de morte, artes anteriores ou arquivos locais não relacionados.

## Critérios de aceitação e verificação

- Compilar e executar os testes existentes com NeoForge 21.1.228; iniciar cliente e servidor dedicado sem erro de registro ou carregamento indevido de classes cliente.
- Testar entrega para jogador novo e existente, inventário cheio, reconexão e recuperação sem duplicar a entrega a cada login.
- Abrir com botão direito, exibir skin real e boot QuiralOS, completar as cinco respostas e revisar antes de confirmar.
- Verificar campos vazios, idade inválida, limites, caracteres de controle, textos longos e pacotes excessivos ou desatualizados.
- Fechar, reabrir, desconectar e reiniciar normalmente o servidor para confirmar persistência do rascunho e da ficha.
- Confirmar bloqueio de edição e dos comandos antigos, liberação administrativa e preservação da ficha anterior durante uma edição.
- Verificar que outro jogador sem permissão não recebe a ficha completa e não consegue alterar o registro alheio.
- Conferir resolução 1280x720 e 1920x1080 com escalas de GUI usuais, revisão longa, foco visível e operação por teclado.
- Verificar Emotecraft instalado e ausente, registrando versão testada e alcance real da animação.
- Gerar o JAR após as verificações e informar separadamente tudo que ainda dependa de teste dentro do jogo.

## Fora deste escopo

Sem sistema de raça, atributos de combate, aprovação de ficha pela equipe, consulta pública de histórias, banco de dados externo, navegador incorporado ou novas funções de tablet além da ficha.
