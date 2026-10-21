# Void e Divino — avisos em vídeo (4.6.0)

Data: 2026-10-20
Estado: design aprovado na conversa, seção por seção; especificação escrita para revisão final.

## Objetivo e contexto

Levar os avisos Void "Queda Sem Fundo" e Divino "Ascensão Celestial" para o modo
vídeo, com a mesma rotina da 4.5.0 (Inferno e Gelo; antes numerada 4.3.0): cena Blender reproduzível,
quadros aprovados pelo dono, exportação em pares JPEG cor+alfa e integração no
player existente, com fallback para o FX procedural.

Diferente da 4.5.0, aqui **não há redesenho**: a coreografia dos efeitos atuais
(`VoidAvisoFx`, `DivinoAvisoFx`) é mantida e renderizada em 3D. O ganho esperado
vem de profundidade real, luz, brilho e materiais que o procedural 2D não tem.

Escolhas do dono nesta conversa:

- Avisos do ciclo: Void + Divino (par escuro/claro).
- Direção: mesma coreografia, em 3D.
- Tamanho: até ~15 MB por vídeo no jar.
- Void: imersão quase total (o jogo quase some durante a fase principal).
- Montagem: câmera em perspectiva no Void; ortográfica no Divino.

## Escopo

Inclui: duas cenas Blender, prévias, exportação, integração nas duas classes de
aviso, testes e verificação no jogo, atualização de `WALKTHROUGH.md` e
`arte/README.md` na entrega.

Não inclui: os outros 7 avisos procedurais, sons novos, mudanças de gameplay,
push ou Release. A implementação acontece em branch própria da 4.6.0, criada a
partir do `main` local (que já tem a 4.5.0) conforme o plano. A aprovação desta especificação autoriza
escrever o plano; a execução vem após aprovação do plano.

## Sequência visual aprovada

Os intervalos descrevem fases com transições sobrepostas e suaves. Texto, sons e
partículas do servidor continuam no código, com os tempos atuais.

### Void — Queda Sem Fundo (título em cy = 0,45 h)

- 0–0,5 s: onda de choque lavanda se expande do centro; o espaço do End escurece
  a tela até imersão quase total (alfa ~0,9; o jogo fica como vulto).
- 0,1–1,3 s: buraco negro abre atrás do título (raio ~0,1 h), com horizonte de
  eventos em três anéis (lavanda, ciano, violeta). Disco de acreção é anel 3D
  real, inclinado, com o lado mais próximo mais claro e balanço lento.
- 1–4,8 s: a câmera "cai". Túnel de anéis ondulados vem em profundidade (ciano no
  fundo, violeta perto) e passa pela câmera. Estrelas viram riscos sugados para o
  centro. Estilhaços escuros com contorno violeta giram à deriva rumo ao buraco,
  sempre por fora da zona do texto.
- 4,8–6 s: túnel e estrelas apagam, o escuro recua e o jogo volta, sem corte seco.
- No código: "AVISO" com órbita, aberração cromática e reflexo invertido;
  mensagem; sons (entrada e teleporte do Enderman no fim); partículas do servidor.

### Divino — Ascensão Celestial (título em cy = 0,42 h)

- 0–0,3 s: clarão branco curto. Vinheta quente e escura nas bordas; nunca um véu
  na tela inteira.
- 0–0,6 s: pilar de luz desce do topo até o título (núcleo branco, bordas
  douradas), com poeira iluminada dentro.
- 0,1–1,2 s: halo abre (raio ~0,26 h): anéis, 48 tiques, hexagrama duplo girando
  em sentidos opostos e 4 losangos orbitando. Duas coroas de raios giram em
  sentidos contrários, com volume suave e brilho.
- 0,3 s em diante: asas de luz (2 × 9 penas com nervura) abrem e batem devagar,
  cada pena com atraso próprio. Ornamento (linha + losango) aparece sob o título
  entre 0,8 e 1,4 s; ele passa a fazer parte do vídeo.
- 1–4,8 s: faíscas em estrela sobem pela tela inteira em profundidades diferentes.
- 4,8–6 s: tudo se recolhe e dissipa; o pilar sobe e some.
- No código: letras caindo uma a uma com o brilho dourado; mensagem; sinos e som
  de beacon no fim; partículas do servidor.

## Composição e legibilidade

- As cenas seguem os centros dos efeitos Java (Void 0,5 w / 0,45 h; Divino
  0,5 w / 0,42 h), não os do Inferno/Gelo. As medidas do procedural
  (k = min(w,h)/240) viram unidades de mundo fixas no quadro 16:9.
- O player faz cover 16:9; 16:10 e 21:9 cortam só as laterais. Buraco, anéis,
  halo e o corpo das asas ficam dentro do corte 16:10; só as pontas das asas
  podem sair no 21:9. Conferir com `previa.enquadrar`.
- Zona do texto, de 1,0 a 4,8 s: Void y ~40–58 % da altura; Divino y ~37–55 %;
  x 25–75 %. Nada opaco ou claro cruza as letras ali: estilhaços, estrelas e
  penas passam por fora.
- Void: o buraco escuro serve de fundo ao título. O disco de acreção fica mais
  fraco na frente da mensagem e mais forte nas laterais.
- Divino: o miolo do halo fica apagado. Atrás das letras, o brilho do disco deve
  ficar em no máximo ~35 % do brilho dos anéis. O hexagrama atravessa o texto só
  em baixa opacidade.
- Nos dois, o texto do modo vídeo usa o contorno escuro existente
  (`AvisoFxUtil.contornoEscuro`) e o apoio de leitura da 4.5.0.
- Conferir sobre `arte/referencias/gameplay_claro.png` e `gameplay_escuro.png`.
  O caso crítico é o Divino sobre céu claro.

## Tamanho dos assets

- Meta: até ~15 MB por vídeo no jar, com JPEG de cor em qualidade 85 e de alfa
  em qualidade 90, como hoje.
- Void: nebulosa de fundo suave e de baixa frequência; o detalhe fica nos riscos
  e anéis. Alfa quase constante na fase principal.
- Divino: alfa zero na maior parte da tela.
- Sem textura fina de ruído em tela cheia (foi o que levou o Gelo a 32 MB).
- Medir após exportar. Se passar da meta, reduzir primeiro grão e densidade de
  partículas, depois a qualidade do JPEG de cor. A qualidade do alfa nunca fica
  abaixo de 90, para não gerar halo ou recorte.

## Produção

- `arte/cenas/void.py` e `arte/cenas/divino.py` usam só `arte/cenas/comum.py`
  (sem depender de `morte.py`, `inferno.py` ou `gelo.py`). São reproduzíveis a
  partir de cena vazia, com sementes determinísticas e sem movimentos
  sincronizados.
- Saída de cada cena: 144 PNG RGBA 1280×720, 24 fps, 6 s, fundo transparente,
  `marcadores.json` e `inspecao.json`. Sem objeto de texto na cena.
- `comum.py` ganha uma câmera em perspectiva cujo plano z = 0 mapeia exatamente
  os mesmos 16 × 9 unidades da câmera ortográfica atual (x −8..8, y −4,5..4,5).
  Um teste garante que o ponto (0; +0,45) do mundo projeta em (0,5 w; 0,45 h).
  A câmera ortográfica não muda: MORTE, Inferno e Gelo continuam idênticos.
- Void: câmera em perspectiva; túnel e estrelas distribuídos em z negativo;
  disco de acreção com inclinação real.
- Divino: câmera ortográfica, planos com emissão e bloom, como Inferno/Gelo.
- `previa.py`, `exportar.py`, `inspecionar.py` e `validar.py`: verificar se
  aceitam os ids novos como estão. Qualquer ajuste necessário entra no plano,
  sem mudar o comportamento para os vídeos existentes.

## Gates de aprovação visual

1. Quatro quadros por aviso, compostos sobre gameplay claro e escuro, com texto
   aproximado:
   - Void: 0,3 s (choque), 1,2 s (buraco aberto), 3 s (queda/leitura), 5,3 s (saída).
   - Divino: 0,2 s (clarão/pilar), 1,0 s (halo e asas abrindo), 3 s (leitura),
     5,3 s (saída).
   O dono aprova antes do render completo.
2. Render completo + GIF de movimento de cada aviso. O dono aprova antes da
   exportação.
3. Exportação: conferir quadros exportados (par JPEG) sobre os fundos e o
   tamanho final de cada pasta.

## Integração

Mesmo padrão de `InfernoAvisoFx` e `GeloAvisoFx`:

- `video = FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("void" | "divino")`.
- Com vídeo: fundo do vídeo, depois texto e sons do código. Arquivo ausente,
  corrompido ou atrasado devolve ao procedural no mesmo t, sem reiniciar sons
  nem duplicar camadas.
- `parar()` em cada classe; `ClientEffects` passa a encerrar também Void e
  Divino quando um aviso novo começa, e a saída do mundo encerra os dois.
- No modo vídeo, o texto recebe contorno escuro. Os efeitos de texto existentes
  (órbita, aberração e reflexo do Void; queda das letras e brilho dourado do
  Divino) permanecem.
- Modo reduzido e `fxVideo=false` usam o procedural, como hoje.

## Testes e verificação

- Testes de marcadores dos dois vídeos, no molde de `ElementaisMarcadoresTest`.
- Teste da câmera em perspectiva (projeção do centro).
- `check.py` (inclui `arte/validar.py`), `./gradlew test` e
  `python -m unittest discover -s arte/tests -v`.
- No jogo: `run-server/` + `./gradlew runClient -PrptagTeste` +
  `python tools/rcon.py "rp admin avisar Dev void TESTE"` (e `divino`).
  Conferir: cada vídeo até o fim, `fxVideo` desligado, modo reduzido, JPEG
  corrompido, troca por outro aviso no meio e saída do mundo. Capturas em
  `verificacao/4_6_0_*`.

Critérios de aceite:

1. Coreografia reconhecível frente ao procedural atual, nos tempos descritos.
2. Mensagem e título legíveis entre 1,0 e 4,8 s nos dois fundos de teste.
3. Void: profundidade perceptível (túnel e estrelas vindo da tela), disco de
   acreção inclinado com lado próximo mais claro.
4. Divino: halo sem miolo estourado atrás do texto; asas com penas
   individualizadas e batida dessincronizada.
5. Partículas, estrelas e penas sem reaparecer por salto dentro da tela;
   entradas e saídas por fora do quadro ou com fade.
6. Sem recortes pretos, halos escuros ou retângulos no alfa, inclusive após o
   JPEG.
7. Fim suave até 6 s.
8. Todos os quadros presentes, dimensões e marcadores válidos, nenhum objeto de
   texto na cena, até ~15 MB por vídeo.

## Entregáveis

Scripts das duas cenas e a câmera em perspectiva em `comum.py`; quatro quadros
por aviso sobre gameplay; dois GIFs de movimento; assets exportados após
aprovação; integração e evidências de teste; documentos atualizados na entrega.
Commits sem trailer de IA. Push e Release ficam com o dono.
