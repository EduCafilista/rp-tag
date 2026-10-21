# Desenvolvimento e compilação

Este guia descreve o ambiente de desenvolvimento do RP Tag. Consulte
[ENTREGA.md](ENTREGA.md) para arquitetura e convenções e
[WALKTHROUGH.md](WALKTHROUGH.md) para o registro técnico histórico.

## Requisitos

| Ferramenta | Uso |
| --- | --- |
| JDK 21 | Compilação e execução |
| Git | Controle de versão |
| Python 3 | Execução de `check.py` e ferramentas de arte |
| Pillow | Validação e composição dos arquivos de imagem |
| Blender 5.2 | Produção das cenas de animação, quando necessário |
| Editor ou IDE com suporte a Java | Edição e navegação no código |

Utilize o Gradle Wrapper do repositório; uma instalação global de Gradle não é
necessária. O primeiro build baixa dependências e prepara o ambiente Minecraft.
O tempo e o consumo de memória dependem da máquina e do estado do cache.

## Compilação

Execute os comandos na raiz do repositório.

```powershell
# Windows
.\gradlew.bat build
python check.py
```

```bash
# Linux e macOS
./gradlew build
python3 check.py
```

Na versão atual do código, o resultado é `build/libs/rptag-4.5.0.jar`.
Remova o JAR anterior de `mods/` antes de testar uma atualização.

## Testes

| Verificação | Comando no Windows |
| --- | --- |
| Testes Java | `.\gradlew.bat test` |
| Build completo | `.\gradlew.bat clean build` |
| Verificações do projeto | `python check.py` |
| Ferramentas de arte | `python -m unittest discover -s arte/tests -v` |
| Integridade dos assets de vídeo | `python arte/validar.py` |

As contagens de testes variam entre versões. Consulte a saída da execução e os
relatórios em `build/reports/tests/`; não utilize contagens históricas como
critério de sucesso. Testes automatizados não substituem a verificação no jogo
de alterações visuais ou de comportamento.

## Ambiente de teste no jogo

- `.\gradlew.bat runClient`: inicia o cliente de desenvolvimento; utiliza `run/`.
- `.\gradlew.bat runServer`: inicia o servidor de desenvolvimento; utiliza `run-server/`.
- `.\gradlew.bat runClient -PrptagTeste`: conecta ao servidor local de teste
  em `127.0.0.1:25565`, quando disponível.

A primeira execução do servidor exige a leitura e aceitação do EULA do
Minecraft em `run-server/eula.txt`. O usuário responsável pelo ambiente deve
realizar essa aceitação.

Se o RCON estiver habilitado no ambiente local, `tools/rcon.py` lê a configuração
de `run-server/server.properties`. Não versione senhas, dados de mundo ou arquivos
de configuração do ambiente de execução.

## Mapa do código

| Área | Arquivos principais |
| --- | --- |
| Inicialização e versão | `src/main/java/dev/rptag/RPTagMod.java` |
| Comandos e ajuda | `src/main/java/dev/rptag/RPCommands.java` |
| Chat | `src/main/java/dev/rptag/ChatCommands.java` |
| Personas | `src/main/java/dev/rptag/PersonaCommands.java` |
| Dados persistentes | `src/main/java/dev/rptag/RPWorldData.java` |
| Zonas de lore | `LoreZones.java` e `LoreZoneCommands.java` |
| Vidas canônicas | `CanonLife.java` e `CanonCoracaoBlock.java` |
| Balões no mundo | `client/BubbleRenderer.java` e `client/BubbleGeometry.java` |
| Personalização de balões | `client/BubbleStyleScreen.java` |
| Roteamento dos avisos | `client/ClientEffects.java` |
| Efeitos de cada estilo | `client/*AvisoFx.java` |
| Player de vídeo | `client/VideoFx.java` e `client/VideoFrameLoader.java` |
| Camadas de interface | `client/ClientGuiLayers.java` |
| Cenas e exportação | `arte/` |
| Traduções | `src/main/resources/assets/rptag/lang/` |

Os caminhos abreviados da tabela pertencem a `src/main/java/dev/rptag/`.

## Convenções e entrega

1. Consulte o estado do Git antes de editar e preserve alterações de outros trabalhos.
2. Mantenha a versão coerente em `build.gradle`, `RPTagMod.VERSION`,
   `META-INF/neoforge.mods.toml` e no log de inicialização de `ClientEffects`.
3. Execute as verificações relacionadas à alteração e confira o resultado no jogo.
4. Atualize o changelog e o registro técnico com resultados efetivamente observados.
5. Revise o diff e inclua no commit apenas os arquivos do trabalho concluído.
6. Siga [PUBLICAR.md](PUBLICAR.md) para distribuição.

Não inclua telemetria, dependências ou mudanças visuais fora do escopo aprovado.
As mensagens de commit devem descrever a alteração sem trailers de ferramentas.
