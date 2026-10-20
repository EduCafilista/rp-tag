# Inferno e Gelo — redesign híbrido dos avisos

Data: 2026-10-20
Estado: design aprovado na conversa; especificação escrita para revisão final.

## Objetivo e contexto

Refazer o acabamento das prévias de Inferno “Fornalha” e Gelo “Geada Eterna”.
As prévias locais atuais ainda apresentam chamas rígidas, rastros uniformes,
geada muito linear e cristais repetidos. Servem como comparação, não como
resultado aprovado. O piloto MORTE da versão 4.2.0 fornece o pipeline existente.

A direção escolhida é híbrida: fragmentos e cristais angulares, próximos da
identidade Minecraft, com fogo, fumaça, geada e iluminação mais orgânicos.
Cada aviso apresenta um acontecimento central, seguido de abertura do centro
para leitura. A visão do jogo permanece parcialmente visível.

## Escopo

Este ciclo cobre as duas cenas Blender, suas prévias, exportação e integração
com o player existente, após aprovação visual. Preservar texto dinâmico, sons,
partículas do servidor e fallback procedural dos dois avisos. A aprovação desta
especificação autoriza preparar o plano; a execução vem após aprovação do plano.

Não inclui os outros avisos, coração canônico, balões, alterações de gameplay,
novos sons ou publicação de uma Release. A versão de entrega será definida no
plano de integração, sem alterar a 4.2.0 durante a produção das prévias.

## Sequência visual aprovada

### Inferno — Fornalha

- 0–1 s: fissura angular atrás da futura posição do título; interior passa de
  vermelho escuro para amarelo incandescente, com clarão curto, faíscas e onda
  de calor. A onda é uma distorção luminosa dentro do overlay; não pressupõe
  refração do framebuffer do jogo.
- 1–2 s: fissura se desfaz em fragmentos carbonizados. Chamas curvas avançam
  pelo rodapé e laterais. Fumaça sobe e recebe iluminação do fogo.
- 2–4,8 s: centro livre para leitura. Fogo em camadas nas bordas, com núcleos
  claros e pontas irregulares. Redemoinho formado por rastros intermitentes de
  brasa. Meteoros com cabeça luminosa e cauda dissipando.
- 4,8–6 s: fogo baixa, brasas enfraquecem e fumaça se desfaz.

### Gelo — Geada Eterna

- 0–1 s: impacto gelado, clarão breve e rachaduras irregulares do centro para
  as bordas.
- 1–2 s: rachaduras centrais desaparecem; geada ramifica nas bordas. Cristais
  angulares crescem com tamanhos e inclinações variados, faces translúcidas
  e reflexos frios.
- 2–4,8 s: centro livre. Silhueta discreta de floco gira atrás do título;
  neve em profundidades diferentes e névoa leve mantêm movimento. Geada
  continua crescendo lentamente nas bordas.
- 4,8–6 s: pequenos fragmentos se soltam e geada desaparece gradualmente.

Os intervalos descrevem fases, com transições sobrepostas e suaves. O título e
a mensagem continuam obedecendo ao tempo e layout dos efeitos Java atuais.

## Composição e legibilidade

As cenas respeitam os centros e posições de título/mensagem de InfernoAvisoFx
e GeloAvisoFx, não os do MORTE. De 2 a 4,8 s, reservar a região central
aproximada x=20–80% e y=32–66% para leitura: sem meteoros, fragmentos ou cristais
opacos cruzando as letras. Nessa região, o floco/redemoinho pode aparecer apenas
como fundo discreto; flashes do impacto já devem ter terminado.

As bordas continuam intensas, mas não podem formar um painel opaco que apague
o gameplay durante toda a fase principal. A mensagem deve ser legível sobre
cenas claras e escuras, com escala de GUI diferente e no enquadramento 16:9.
Conferir também cortes 16:10 e 21:9 usados pelo player, especialmente a posição
dos elementos essenciais e a zona central do texto.

## Produção e componentes

- Uma cena por aviso, com entrada própria, responsável pela direção visual,
  objetos, materiais, animação e marcadores.
- Utilitários compartilhados responsáveis por câmera, luz, animação e exportação.
  Extrair do piloto apenas o que as novas cenas realmente utilizarem; evitar
  que Inferno/Gelo dependam de constantes ou da montagem do MORTE.
- Scripts reproduzíveis a partir de cena vazia. Sementes determinísticas para
  variação de partículas, cristais e tempos, sem movimentos sincronizados.
- Saída: 144 PNG RGBA, 1280×720, 24 fps, 6 s, fundo transparente. Cor e alfa
  usam o mesmo enquadramento e quadros.
- Três camadas conceituais: impacto inicial; bordas persistentes; partículas
  e fragmentos em profundidade. Podem ser objetos da mesma cena, sem exigir
  múltiplos arquivos de vídeo ou mudanças no player.
- Cristais: geometria facetada com luz e reflexos; transparência renderizada
  sobre fundo transparente, sem prometer refração do mundo do Minecraft.
- Fogo: silhuetas curvas e irregulares, variação de temperatura e animação
  independente; evitar cones rígidos e repetição de altura/fase.

## Fluxo de assets e integração

Cena Blender → PNG RGBA + marcadores → prévia composta sobre gameplay →
aprovação visual → pares JPEG cor/alfa + fx.json → player existente.

O texto nunca é gravado nos assets. O compositor de prévia usa posições e cores
específicas de cada aviso e identifica a tipografia como aproximação. Usar
capturas de gameplay do próprio projeto, livres de mensagens privadas, como
fundos claros e escuros; o fundo fica apenas na prévia.

A integração deve reaproveitar a composição vídeo + texto/sons na classe do
aviso. Enquanto carrega, seguir a política existente do player. Arquivo ausente,
corrompido ou atraso excessivo devolve ao procedural no mesmo instante; não
reiniciar sons nem duplicar camadas. O modo reduzido deve conservar o efeito
procedural quando o vídeo não oferecer uma variante compatível, para respeitar
as opções atuais de redução de clarões e partículas.

## Verificação e aprovação visual

Antes do render completo, gerar quatro quadros por aviso: impacto (0,5 s),
centro abrindo (1,5 s), fase de leitura (3 s), saída (5,3 s). Conferir cor/alfa,
bordas, hierarquia, luz e texto aproximado sobre gameplay claro e escuro.
Obter aprovação desses quadros antes de renderizar as sequências completas.
Depois, gerar prévias animadas para aprovação de movimento antes da integração.

Critérios de aceite:

1. Impacto inicial reconhecível e centro liberado até 2 s.
2. Mensagem legível entre 2 e 4,8 s nos fundos de teste.
3. Fogo sem pontas rígidas repetidas e sem oscilação coletiva evidente.
4. Geada irregular, com ramificações e crescimento progressivo; cristais com
   volume perceptível e variação de tamanho/inclinação.
5. Partículas sem reaparecer por salto no interior da tela; entradas e saídas
   ocorrem fora do quadro ou com fade.
6. Sem recortes pretos, halos escuros ou retângulos no alfa, inclusive após
   compressão em JPEG. Inspecionar quadros exportados, além dos PNG originais.
7. Fim suave até 6 s, sem corte visível de fumaça, neve ou cristais.
8. Todos os quadros presentes, dimensões e marcadores válidos, nenhum objeto
   de texto na cena Blender.

Na integração, executar os testes existentes e os testes pertinentes aos novos
assets. Conferir no jogo: cada vídeo até o fim, opção fxVideo=false, modo
reduzido, arquivo corrompido, substituição por outro aviso e saída do mundo.
Comparar a versão do jar com as prévias aprovadas. Renderização e teste são
critérios de entrega; não alegar aprovação com base apenas em contagem de objetos.

## Entregáveis

Scripts reproduzíveis das duas cenas e utilitários necessários; quatro quadros
por aviso sobre gameplay; duas prévias animadas; assets exportados somente após
aprovação visual; integração e evidências de teste; atualização dos documentos
na entrega. Nenhum trailer de IA nas mensagens de commit, conforme preferência
expressa do usuário. Push ou Release não faz parte desta etapa de design.
