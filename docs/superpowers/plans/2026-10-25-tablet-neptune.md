# Tablet Neptune SMP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar o tablet funcional no RP Tag, com criação gradual da ficha, persistência no servidor e consulta após confirmação.

**Architecture:** Tela nativa cliente, item Java com o modelo aprovado e serviço de ficha autorizado pelo servidor. Reutilizar SavedData, o registro de itens e a sincronização de persona existentes. Manter rascunho separado da ficha confirmada e enviar dados privados somente ao dono ou administrador autorizado.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.228, Gradle ModDev e JUnit 5 existentes. Blockbench via MCP para alterações do modelo; Blender 5.2 low-poly para sincronização e prévias.

**Spec:** `docs/superpowers/specs/2026-10-25-tablet-neptune-design.md`.

## Global Constraints

- Minecraft 1.21.1, compilação e testes com NeoForge 21.1.228.
- Funcionalidade dentro do RP Tag, instalada em cliente e servidor.
- Boot QuiralOS; identidade Neptune SMP nas perguntas; composição limpa aprovada na prévia `tablet-neptune-clean-v8.html`.
- Nome até 32 caracteres; idade entre 1 e 999; origem até 240; habilidades até 600; história até 3.000.
- Habilidades narrativas, sem efeitos de jogo. Origem substitui raça.
- Salvar no servidor por UUID. Não alterar personas existentes ao abrir ou salvar um rascunho.
- Edição bloqueada após confirmar; administradores com permissão 2 liberam alterações.
- Não adicionar navegador, dependência gráfica ou renderizador especial para o item.
- Não modificar artes anteriores, portais ou mudanças locais alheias ao tablet.
- Não enviar push automaticamente. Commits locais apenas com os arquivos desta funcionalidade, sem trailers de IA.

## Review Focus

1. Resposta de salvamento atrasada não deve apagar texto digitado depois do envio: verificar na tarefa 4.
2. Inventário cheio e cliques em recuperação não devem marcar entrega inexistente nem duplicar itens: verificar na tarefa 3.
3. Pacote antigo após liberação administrativa não deve sobrescrever um registro novo: verificar nas tarefas 2 e 4.
4. Mundo anterior à atualização deve preservar persona, estado RP e os demais dados: verificar na tarefa 2.
5. Textos longos, escala de GUI alta e caracteres acentuados não devem esconder botões ou perder conteúdo: verificar na tarefa 5.

## Arquivos e limites de responsabilidade

- `FichaPersonagem.java`: respostas e validação, sem dependência cliente.
- `FichaRegistro.java`: rascunho, confirmação, revisão, bloqueio, etapa e entrega.
- `TabletService.java`: autorização, entrega, persistência e comandos.
- `TabletItem.java`: abertura com botão direito, inclusive quando direcionado a um bloco.
- `TabletUpdatePayload.java` e `TabletSyncPayload.java`: mensagens com limites de decodificação.
- `client/TabletClientState.java`: fila de alterações e respostas, independente de a tela estar aberta.
- `client/TabletScreen.java`: boot, perguntas, revisão, leitura e renderização da skin.
- `client/TabletPlayerPreview.java`: renderização isolada do preview e integração opcional de animação comprovada.
- Alterar pontualmente `RPTagMod`, `RPWorldData`, `ServerEvents`, `PersonaCommands`, `ModNetworking` e `client/ClientPayloadHandler`.
- Recursos em `src/main/resources/assets/rptag/`: modelo e PNGs existentes do tablet, logo de boot e traduções.
- Novos testes em `src/test/java/dev/rptag/` e `src/test/java/dev/rptag/client/` para regras, NBT, pacotes e fila de salvamento.

## Tarefa 1: Modelo no jogo e compatibilidade do ambiente

**Arquivos:** `build.gradle`, `RPTagMod.java`, novo `TabletItem.java`, `META-INF/neoforge.mods.toml`, `assets/rptag/models/item/tablet.json`, `assets/rptag/textures/item/{tablet,quiralos}.png` e traduções existentes em `assets/rptag/lang/`.

**Interfaces:** `RPTagMod.TABLET`, fornecedor de `Item`; `TabletItem(Properties)` usa `stacksTo(1)`. A abertura será conectada a `TabletService.open(ServerPlayer)` na tarefa 3.

- [ ] Executar `./gradlew.bat test` antes de alterar o ambiente e registrar falhas preexistentes.
- [ ] Compilar e executar a suíte em 21.1.228. Ajustar somente incompatibilidades efetivamente encontradas; não supor que o número no manifesto comprova compatibilidade.
- [ ] Registrar o item e copiar o JSON/PNGs de `arte/modelos/tablet_neptune/export/assets/rptag/`, mantendo as referências `rptag:item/tablet` e `rptag:item/quiralos`.
- [ ] Adicionar tradução `item.rptag.tablet` como `Tablet QuiralOS` e equivalente em inglês. O boot usa o logo original local.
- [ ] Executar `python arte/modelos/tablet_neptune/verificar_texturas.py` e `./gradlew.bat build`. Resultado esperado: verificações passam e recursos aparecem no JAR.
- [ ] Iniciar cliente com `./gradlew.bat runClient`; obter o item com `/give @s rptag:tablet`, conferir textura e apresentação em ambas as mãos, inventário e chão. Corrigir no Blockbench via MCP os encaixes que falharem e reexportar.
- [ ] Commit local: `feat: adicionar modelo do tablet QuiralOS`.

## Tarefa 2: Regras e armazenamento da ficha

**Arquivos:** novos `FichaPersonagem.java`, `FichaRegistro.java`, testes correspondentes; alterar `RPWorldData.java`.

**Interfaces:** `record FichaPersonagem(String nome, String idade, String origem, String habilidades, String historia)`; `String validationError(boolean complete)` retorna vazio quando válido. Idade fica como texto no rascunho para permitir campo temporariamente vazio; converte para inteiro apenas ao confirmar.

`record FichaRegistro(FichaPersonagem rascunho, FichaPersonagem confirmada, boolean edicaoLiberada, int etapa, long revisao, boolean tabletEntregue)` usa `confirmada == null` antes da primeira confirmação. `boolean podeEditar()`, `FichaRegistro atualizar(FichaPersonagem respostas, int etapa, long revisaoEsperada, boolean confirmar)` e `FichaRegistro liberarEdicao()` implementam a transição sem alterar a instância anterior. Atualização ou confirmação inválida lança `IllegalArgumentException`; revisão obsoleta lança `IllegalStateException`. Cada transição aceita incrementa a revisão, inclusive liberação administrativa.

`RPWorldData.getFicha(UUID)` retorna registro existente ou inicializa o rascunho com nome/idade da persona. `setFicha(UUID,FichaRegistro)` marca o mundo como alterado. Persistir em lista `TabletSheets`, com uma entrada por UUID; guardar revisão e estado de entrega.

- [ ] Criar testes para limites exatos, idade vazia no rascunho, idade inválida na confirmação, acentos, espaços e caracteres de controle. Exemplo: `assertFalse(new FichaPersonagem("Ari", "0", "Porto", "Mapas", "História").validationError(true).isEmpty());`.
- [ ] Criar testes para bloqueio, revisão antiga, liberação e preservação da confirmação anterior. Exemplo: `assertThrows(IllegalStateException.class, () -> registro.atualizar(respostas, 2, registro.revisao()-1, false));`.
- [ ] Executar os testes novos, confirmar falha antes da implementação e implementar somente as regras necessárias.
- [ ] Cobrir ida e volta NBT com rascunho/confirmada diferentes e carregar NBT antigo sem `TabletSheets`; afirmar que persona e estado RP permanecem iguais. Dados malformados de uma entrada nova não podem apagar as outras seções do mundo.
- [ ] Executar `./gradlew.bat test --tests "dev.rptag.Ficha*Test"`; esperado: todos passam.
- [ ] Commit local: `feat: persistir fichas e rascunhos por jogador`.

## Tarefa 3: Entrega, comandos e autorização no servidor

**Arquivos:** novo `TabletService.java`, teste `TabletServiceTest.java`; alterar `TabletItem.java`, `ServerEvents.java`, `PersonaCommands.java`.

**Interfaces:** `TabletService.open(ServerPlayer)`, `boolean tryGiveTablet(ServerPlayer)`, `boolean personaLocked(ServerPlayer)` e handler de registro dos comandos. `tryGiveTablet` retorna verdadeiro somente se já há tablet no inventário ou se uma unidade foi inserida; inventário cheio retorna falso e preserva marcador de entrega pendente.

- [ ] Cobrir com teste a decisão de entrega para inventário cheio, jogador existente, item já presente e recuperação. Confirmar: `tabletEntregue` continua falso quando a inserção falha; recuperação repetida com item presente não concede outra unidade.
- [ ] Entregar no login uma única vez, incluindo quem entrou no mundo antes desta atualização. `/tablet` tenta recuperação quando necessário e abre a própria ficha; recusar spam de recuperação na mesma janela de 250 ms.
- [ ] Registrar `/tablet ver <jogador>` em leitura e `/tablet liberar <jogador>`, ambos exigindo permissão 2 e alvo online. Liberar preserva a ficha confirmada anterior.
- [ ] Proteger `/persona criar`, `idade`, `desc` e `off` para quem já tem ficha confirmada, mesmo com edição liberada; orientar a alteração pelo tablet. Manter comportamento anterior para personas sem ficha confirmada.
- [ ] Confirmar uma ficha chama `ServerEvents.setPersona` com nome/idade novos e descrição anterior; não muda o estado RP/OFF RP.
- [ ] Testar no servidor dedicado duas contas: consulta própria, consulta administrativa, tentativa sem permissão e entrega/recuperação sem duplicação.
- [ ] Commit local: `feat: integrar tablet e administracao de fichas`.

## Tarefa 4: Rede e salvamento sem sobrescrita

**Arquivos:** novos `TabletUpdatePayload.java`, `TabletSyncPayload.java`, `client/TabletClientState.java`; alterar `ModNetworking.java`, `client/ClientPayloadHandler.java`, `TabletService.java`; testes de codec e fila cliente.

**Interfaces:** C2S `TabletUpdatePayload(long revisao, int requestId, FichaPersonagem respostas, int etapa, boolean confirmar)`, sem UUID de destino. S2C `TabletSyncPayload(UUID target, int requestId, FichaRegistro registro, boolean readOnly, boolean open, String error)`. `TabletClientState.edit(FichaPersonagem,int)`, `flush(boolean confirmar)`, `accept(TabletSyncPayload)` e `tick()` controlam uma única solicitação em voo.

- [ ] Testar codecs com limites individuais 32/5/240/600/3000 e rejeição de texto excessivo antes da construção do registro. Etapa aceita fica entre 0 e 5. Mensagem de erro tem limite de 256 caracteres.
- [ ] Implementar validação e alterações no fluxo principal do servidor. O remetente só escreve sua ficha; consulta de terceiros existe apenas no comando autorizado. Respostas privadas são enviadas somente ao destinatário.
- [ ] Usar protocolo `6` nos registros do RP Tag e recusar a mistura de JARs incompatíveis.
- [ ] Salvar após 750 ms sem digitação, ao mudar de etapa e ao fechar. Manter uma solicitação em voo; acumular a versão local mais recente enquanto aguarda resposta. Resposta atrasada atualiza a revisão, sem substituir texto local mais novo.
- [ ] A fila continua processando respostas após fechar a tela. Não exibir estado de salvo sem confirmação do servidor; rejeição mantém a cópia local e oferece tentar novamente. Não prometer recuperar texto nunca enviado após encerramento abrupto do cliente.
- [ ] Testar `ackAntigoPreservaTextoNovo`, `fecharComEnvioPendenteEnviaUltimoTexto`, `confirmacaoNaoDuplica`, `revisaoObsoletaNaoSobrescreve` e `liberacaoInvalidaPacoteAnterior`. Assert central: após editar A, enviar A, editar B e receber ACK de A, o texto local continua B e o próximo envio usa a revisão do ACK.
- [ ] Executar testes e verificar captura manual de duas sessões abertas antes/depois da liberação administrativa. Commit: `feat: sincronizar fichas com salvamento confirmado`.

## Tarefa 5: Tela nativa completa

**Arquivos:** novos `client/TabletScreen.java`, `client/TabletPlayerPreview.java`; recursos do logo de boot e traduções; ligar abertura no handler cliente. Reaproveitar apenas os trechos úteis de `BubbleStyleScreen.java`.

**Interfaces:** `TabletScreen(TabletSyncPayload initial)` usa `TabletClientState` da tarefa 4. `TabletPlayerPreview.render(GuiGraphics,int x,int y,int width,int height,int etapa,float partialTick)` desenha a skin real sem modificar a entidade no mundo.

- [ ] Implementar boot QuiralOS de até 2,5 s, dispensável; perguntas Nome → Idade → Origem → Habilidades → História → Revisão; leitura após confirmação. Usar `EditBox` nos campos simples e widget nativo de múltiplas linhas nos textos maiores.
- [ ] Manter a composição limpa: personagem à esquerda, pergunta e resposta à direita, fundo espacial discreto, buraco negro, botões Voltar/Continuar e progresso compacto. As abas de demonstração web não entram no mod.
- [ ] Transições de cerca de 200 ms entre etapas; preferência para reduzir animação desativa movimento decorativo e reduz a transição. Não adicionar vídeo, navegador ou animação global do jogador.
- [ ] Preservar texto/foco durante redimensionamento e envio. Enter avança no campo simples e mantém quebra em campo multilinha; Esc fecha e solicita envio da última edição. Desabilitar confirmação repetida enquanto aguarda resposta.
- [ ] Conferir 1280×720 e 1920×1080, GUI 2/3/auto: nenhuma ação inacessível, revisão e ficha com rolagem, textos máximos e acentuados preservados, foco visível, colagem e navegação por teclado funcionando.
- [ ] Testar fechar/reabrir, reconectar e reiniciar normalmente o servidor. Registrar capturas do jogo e resultado de cada cenário.
- [ ] Executar a suíte e iniciar cliente/servidor dedicados. Commit: `feat: criar interface nativa da ficha Neptune SMP`.

## Tarefa 6: Animação do preview com Emotecraft

**Arquivos:** `client/TabletPlayerPreview.java` e adaptador cliente somente se a API exigir; dependência opcional de compilação somente após confirmar o artefato correto.

**Interfaces:** o render da tarefa 5 continua funcional sem Emotecraft. A animação deve atingir exclusivamente o preview e ser interrompida ao trocar etapa/fechar a tela.

- [ ] Identificar a versão NeoForge 1.21.1 usada pelo servidor e ler suas APIs reais. `run/mods` está vazio nesta inspeção; não há versão instalada comprovada no ambiente de desenvolvimento.
- [ ] Validar se a API permite animar um preview sem emitir um emote público nem alterar a entidade local. Não usar reflexão para adivinhar assinaturas e não anunciar compatibilidade sem execução.
- [ ] Se comprovado, integrar gestos discretos por etapa e testar abrir/fechar repetidamente, trocar rápido de pergunta, ausência do mod e servidor dedicado.
- [ ] Se a API não atender, documentar a limitação e apresentar alternativa antes de considerar a animação concluída. Essa limitação não impede testar a ficha e o tablet já funcionais.
- [ ] Commit local apenas quando a integração testada existir: `feat: animar preview do tablet com Emotecraft`.

## Tarefa 7: Revisão final e entrega

**Arquivos:** documentação do usuário existente, versões em `build.gradle`, `RPTagMod.java` e `neoforge.mods.toml`; registro de verificação em `docs/`.

- [ ] Executar `./gradlew.bat clean test build` e `python arte/modelos/tablet_neptune/verificar_texturas.py`. Não esconder falhas herdadas; comparar com a linha de base da tarefa 1.
- [ ] Conferir servidor dedicado, protocolo incompatível, tentativa de escrita sem liberação, dados excessivos, inventário cheio e preservação da ficha anterior durante edição.
- [ ] Verificar os recursos dentro do JAR, inclusive os dois PNGs do item e o logo do boot. Escolher a próxima versão a partir do estado real do repositório e mantê-la igual nos três arquivos.
- [ ] Revisar o diff completo, com atenção a carregamento cliente no servidor, permissões e privacidade. No modo nativo, solicitar uma revisão independente ao final, se a ferramenta estiver disponível; corrigir os achados antes da entrega.
- [ ] Registrar comandos de uso/administração e testes efetivamente realizados. Entregar o caminho do JAR, capturas no Minecraft e limitações remanescentes.
- [ ] Commit local da versão; sem push automático.

## Execução e ponto de revisão

Recomendação: modo nativo, implementando as tarefas nesta sessão e revisando o conjunto no final, conforme a preferência já expressa na conversa. A revisão deste plano antecede alterações no código Java. A existência dos modelos e da prévia web não comprova que os recursos de ficha já foram implementados no Minecraft.
