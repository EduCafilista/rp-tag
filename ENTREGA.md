# Guia de manutenção do RP Tag

Este documento apresenta o estado do código, a arquitetura e as convenções
necessárias para dar continuidade ao projeto.

## Estado do código

| Item | Referência |
| --- | --- |
| Versão do RP Tag | 4.6.0 |
| Minecraft | 1.21.1 |
| NeoForge de desenvolvimento | 21.1.249 |
| Java | 21 |
| Artefato de build | `build/libs/rptag-4.6.0.jar` |
| Portais da Lore | 1.3.2; desenvolvimento suspenso |

A versão publicada deve ser confirmada na página de releases. Planos em
`docs/superpowers/` podem descrever trabalhos futuros; não são evidência de
funcionalidade implementada.

## Organização da documentação

- [README.md](README.md): instalação e recursos para usuários e administradores.
- [COMO-EDITAR.md](COMO-EDITAR.md): ambiente, build e testes.
- [PUBLICAR.md](PUBLICAR.md): preparação e publicação de versões.
- [CHANGELOG.md](CHANGELOG.md): alterações por versão.
- [WALKTHROUGH.md](WALKTHROUGH.md): histórico técnico detalhado.
- [arte/README.md](arte/README.md): produção de assets e prévias.

## Funcionalidades e persistência

O servidor controla estado RP, personas, canais de chat, permissões, zonas de
lore e vidas canônicas. Os dados persistentes são mantidos no mundo. O cliente
renderiza nomes, balões, efeitos de tela, animações e visualização administrativa
das zonas.

A identidade de personagem é administrada por `/persona`; a ajuda principal está
em `/rp ajuda`. O sistema de balões depende da liberação administrativa. As zonas
suportam faixa vertical, avisos, som e música. O modo canônico começa com cinco
vidas por jogador e inclui proteção de cinco minutos contra perdas sucessivas.
Mortes causadas por um jogador em OFF RP não descontam vidas canônicas.

## Arquitetura

### Raiz (`dev/rptag/`)
| Arquivo | Papel |
|---|---|
| `RPTagMod.java` | Registro do mod, `VERSION`, itens (varinha, coração), sons |
| `RPTags.java` | Texto/cores da tag (`ʀᴘ` / `ᴏꜰꜰ ʀᴘ`), small caps |
| `RPWorldData.java` | **Persistência NBT** (tag RP, persona, chatLocal, balões, vidas, estilo, typewriter) |
| `ServerEvents.java` | Estado RP/persona, nomes no chat/TAB |
| `RPChat.java` | Canais RP + `spawnBubble` (gates + anti-flood 250 ms) |
| `ChatCommands.java` | `/g /s /w /me /do` |
| `RPCommands.java` | `/rp` + `/rp admin` (avisos, medo, canon, balões) |
| `PersonaCommands.java` / `RollCommands.java` | `/persona`, `/roll` |
| `CanonLife.java` | **Vidas canônicas**: respawn, congelamento, ritual, morte OFF RP |
| `CanonCoracaoBlock(+Entity).java` | Coração canônico 3D (bloco + item) |
| `LoreZones.java` | Zonas (esfera + **faixa vertical minY/maxY**), aviso único, som/música, aviso na entrada |
| `LoreZoneCommands.java` | `/lorezone ...` (criar, vertical, mostrar, esconder, varinha, avisos, som, musica, listar) |
| `LorezoneWand.java` | Item da varinha (permissão 2, clique direito, dedupe 250 ms) |
| `MusicSync.java` | Envio de arquivos de música ao cliente em partes |
| `RPEffects.java` | Efeito `rptag:medo` |
| `Stickers.java` | 80 emojis Noto (atlas POT, codepoints 0xE000+) |
| `*Payload.java` | Rede: `ScreenTextPayload`, `FearPayload`, `ChatBubblePayload`, `ZoneShowPayload`, `LorezoneMusicPayload`, `MusicFile*Payload`, `CanonBannerPayload`, `PersonaSyncPayload`, `SyncRPStatePayload`, `TypewriterPayload`, etc. |

### `client/`
| Arquivo | Papel |
|---|---|
| `AvisoFxUtil.java` | **Motor compartilhado** dos FX: `Buf` (tri/quad/rect/seg/circ/anel/vinheta/brilho), `preparar/normal/aditivo`, `tocar`, utilitários de cor e easing |
| `*AvisoFx.java` (9) | `Divino`, `Demoniaco`, `Sangue`, `Void`, `Pesadelo`, `PesadeloAntigo`, `Inferno`, `Gelo`, `Runico` |
| `MorteAvisoFx.java` | FX "Linha Reta" (estilo 4) — desde a 4.2.0 compõe cinza → **vídeo** → texto/sons, com fallback procedural |
| `VideoFx.java` / `VideoFrameLoader.java` | **Player dos avisos em vídeo** (4.2.0): quadro pelo relógio, uma textura 1280x720, thread `rptag-fx-loader` |
| `VideoEstado` / `FxTimeline` / `FxCor` / `FxJpeg` / `MorteTempos` | Java puro com JUnit: estados do player, `fx.json`, cor+alfa, leitura estrita do JPEG, tempos do MORTE |
| `FxVideoPolitica.java` | Vídeo só com `fxVideo` ligado e fora do modo reduzido (4.5.0) |
| `FxConfig.java` / `FxComando.java` | `config/rptag/cliente.properties` (`fxVideo`) + `/rpfx status\|on\|off` |
| `ClientEffects.java` | **Central dos avisos**: encaminha cada estilo ao efeito correspondente, véu do canon, banner, áudio da lore |
| `ClientGuiLayers.java` | Registra as camadas com `registerAboveAll` (com shaders, por cima de tudo) |
| `BubbleRenderer.java` | Coração visual do balão (pílula = tamanho exato do texto; âncora na base) |
| `BubbleStyleScreen.java` | Tela `/balao` (menu cartoon, preview igual ao mundo, emoji picker) |
| `ClientBubbleCache.java` / `BubbleSink.java` / `BubbleGeometry.java` / `BubbleBackgrounds.java` | Pilha, fila, geometria e cores derivadas dos balões |
| `ZoneVisualizer.java` | **Barreira** da lorezone (`RenderLevelStageEvent`, `RenderType.lines()`) — 4.0.0 |
| `CanonCoracaoRenderer.java` | Coração 3D (batida + flutuação + balanço) |
| `MusicaCustom.java` | Música custom por streaming/arquivo, com **tempo máximo** |
| `ClientPayloadHandler.java` | Handlers de todos os pacotes no cliente |
| `NameplateRenderer.java` / `ClientRenderers.java` / `ClientEvents.java` | Nametag, renderers, eventos |

## Renderização e animações

O código 4.6.0 integra vídeos de Morte, Inferno, Gelo, Void e Divino. O player utiliza uma
textura dinâmica e decodificação em segundo plano. A classe de cada aviso mantém
texto, sons e o efeito procedural alternativo. O fallback deve continuar no mesmo
instante da animação, sem repetir sons nem reiniciar a mensagem.

`config/rptag/cliente.properties` controla `fxVideo`; os comandos do cliente são
`/rpfx status`, `/rpfx on` e `/rpfx off`. O modo reduzido deve respeitar a política
de seleção de efeitos definida no código.

As propostas visuais e os conceitos em `arte/` não devem ser tratados como
aprovação para substituir efeitos existentes. Mudanças artísticas exigem uma
referência visual aprovada pelo responsável.

## Convenções de implementação

1. Não incluir telemetria ou coleta de dados.
2. Preservar o comportamento aprovado ao trabalhar em componentes relacionados.
3. Manter o tamanho do balão associado ao texto e a âncora na base; adesivos
   permanecem na borda e não devem sobrepor as letras.
4. Renderizar adesivos com o atlas colorido, sem aplicar tintura indevida.
5. Manter balões sujeitos às permissões administrativas; o modo desativado deve
   produzir efeito imediato.
6. Tratar falhas de renderização sem encerrar o jogo e liberar recursos de
   animação quando o aviso termina ou o jogador sai do mundo.
7. Manter o título do aviso e a mensagem separados. A mensagem fica abaixo do
   título, conforme o layout existente em cada efeito.
8. Registrar efeitos de tela nas camadas oficiais do cliente, preservando a
   compatibilidade com shaders e o comportamento do modo reduzido.
9. `/me` e `/do` são ações de chat; não devem criar balões de fala.
10. Preservar as restrições conhecidas de texturas, atlas e telas de configuração
    registradas no histórico técnico. Mudanças nessas áreas exigem teste visual.
11. Utilizar decodificação pelo raster para os quadros JPEG. Tratar quadros
    corrompidos como erro de vídeo e acionar o fallback.
12. Não versionar arquivos JAR de distribuição, senhas ou configurações do ambiente local.

## Verificação e entrega

Utilize `gradlew.bat` no Windows e `./gradlew` em Linux ou macOS.

```powershell
.\gradlew.bat clean build
python check.py
python -m unittest discover -s arte/tests -v
```

Confirme o resultado das execuções e teste no jogo as alterações de comportamento
ou renderização. Registre limitações e verificações pendentes de forma explícita.
As contagens históricas de testes não substituem um resultado atual.

O servidor de desenvolvimento utiliza `run-server/`; o cliente utiliza `run/`.
O aceite do EULA, quando necessário, pertence ao responsável pelo ambiente.
A receita de compilação manual registrada no walkthrough é uma referência
histórica; o Gradle Wrapper é o procedimento de desenvolvimento recomendado.

Antes de uma entrega, revise o diff, mantenha a versão coerente nos metadados,
atualize os documentos afetados e inclua no commit apenas os arquivos pertinentes.
As mensagens de commit não devem conter trailers de ferramentas. A distribuição
segue o procedimento de [PUBLICAR.md](PUBLICAR.md).
