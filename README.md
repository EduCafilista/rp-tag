# RP Tag

Mod de roleplay para Minecraft 1.21.1 e NeoForge. Inclui identidade de personagem,
chat por proximidade, balões de fala, avisos de tela, zonas de lore e vidas canônicas.

[English documentation](README.en.md)

## Projetos e versões

| Projeto | Versão do código | Manutenção | Diretório |
| --- | --- | --- | --- |
| RP Tag | 4.7.3 | Em desenvolvimento | Raiz do repositório |
| Portais da Lore | 1.3.2 | Desenvolvimento suspenso | [portais-da-lore](portais-da-lore/) |

Os arquivos JAR são distribuídos pela [página de releases](https://github.com/EduCafilista/rp-tag/releases).
A versão disponível para download pode diferir da versão do código enquanto uma
publicação estiver em preparação. Os binários não são versionados no Git.

## Requisitos e instalação

- Minecraft 1.21.1.
- NeoForge 21.1.x; o ambiente de desenvolvimento utiliza 21.1.249.
- Java 21.

1. Instale o NeoForge no servidor e nos clientes que utilizarão os recursos visuais.
2. Baixe a versão desejada na página de releases.
3. Remova a versão anterior do RP Tag da pasta `mods/` antes de instalar o novo JAR.
4. Instale a mesma versão nos clientes para balões, animações, telas e demais
   recursos de renderização.

Os recursos de chat e identificação no servidor têm comportamento distinto dos
recursos visuais do cliente. Para uma experiência completa, instale o mod nos dois.

## Funcionalidades

### Identidade e personagem

O estado RP ou OFF RP aparece no chat, na lista de jogadores e no nome sobre o
personagem. As tags exibidas pelo jogo são `● ʀᴘ` e `○ ᴏꜰꜰ ʀᴘ`.

| Comando | Finalidade |
| --- | --- |
| `/rp on` e `/rp off` | Alterar o estado de roleplay |
| `/rp status` | Consultar o estado e informações de diagnóstico |
| `/persona criar <nome>` | Criar a identidade do personagem |
| `/persona idade <n>` | Definir a idade |
| `/persona desc <texto>` | Definir a descrição |
| `/persona ver [jogador]` | Consultar a ficha |
| `/persona off` | Remover a identidade de personagem |
| `/roll` | Realizar uma rolagem de dados |

### Chat e balões

O chat local utiliza proximidade. Os canais de grito, sussurro e global atendem
contextos diferentes; ações de personagem são enviadas por `/me` e `/do`.

| Comando | Finalidade |
| --- | --- |
| `/g <mensagem>` | Chat global |
| `/s <mensagem>` | Grito |
| `/w <mensagem>` | Sussurro |
| `/me <ação>` | Ação do personagem |
| `/do <texto>` | Descrição de ambiente, conforme permissão administrativa |
| `/balao` | Personalizar moldura, cores, duração e adesivos |
| `/balao teste <frase>` | Visualizar um exemplo de balão |
| `/balao modo on` e `/balao modo off` | Alterar o modo de exibição da fala |

A administração controla a disponibilidade dos balões por jogador. O sistema
suporta empilhamento de até três balões, animação de entrada e saída e exibição
orientada para a câmera de cada observador.

### Avisos e efeitos

O comando administrativo `/rp admin avisar <jogador|@a> <estilo> <mensagem>` exibe
um título e uma mensagem na tela dos destinatários.

| Estilo | Identificador |
| --- | --- |
| Clássico | `classico` |
| Pesadelo | `pesadelo` |
| Rúnico | `runico` |
| Real | `real` |
| Morte | `morte` |
| Inferno | `inferno` |
| Gelo | `gelo` |
| Divino | `divino` |
| Void | `void` |
| Sangue | `sangue` |
| Demoníaco | `demoniaco` |
| Pesadelo alternativo | `pesadelo2` |

Na versão do código 4.7.3, Morte, Inferno, Divino e Egípcio podem utilizar animações
renderizadas no Blender. Void e Gelo voltaram aos efeitos procedurais (escolha do dono);
os assets de vídeo deles continuam no jar, prontos para religar.
Os efeitos procedurais permanecem disponíveis como alternativa quando o vídeo está
desativado, indisponível ou apresenta falha.
O texto da mensagem é desenhado pelo cliente e não faz parte dos quadros de vídeo.

Use `/rpfx status`, `/rpfx on` ou `/rpfx off` para consultar ou alterar essa opção
no cliente. A configuração é persistida em `config/rptag/cliente.properties`.

### Zonas de lore

As zonas permitem associar avisos, efeitos sonoros e música a regiões do mundo.
O sistema inclui delimitação vertical e ferramentas administrativas de visualização.

- `/lorezone listar`: consultar as zonas cadastradas.
- `/lorezone vertical <id> <minY> <maxY>`: limitar a faixa de altura.
- `/lorezone vertical <id> auto`: restabelecer a delimitação esférica completa.
- `/lorezone mostrar <id> [segundos]`: visualizar os limites na tela do administrador.
- `/lorezone esconder`: encerrar a visualização.
- `/lorezone varinha`: obter a ferramenta de inspeção.
- `/lorezone musicas`: consultar os presets de som.

As opções de som e música incluem presets, arquivos personalizados e URLs
compatíveis. Consulte a ajuda do jogo para os argumentos de cada subcomando.

### Vidas canônicas

O modo canônico é ativado pela administração com `/rp admin canon on`. Cada
jogador começa com cinco vidas. O sistema inclui avisos de perda e recuperação,
proteção contra perdas sucessivas e um coração canônico para restaurar uma vida.

Mortes causadas por um jogador em OFF RP não descontam vidas canônicas. Mortes
por outras causas seguem as regras do sistema. Desde a versão 4.4.0, o comando
`/rp admin vida` permite consultar e administrar o saldo de vidas.

## Desenvolvimento

Utilize o Gradle Wrapper incluído no repositório.

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

O build executa os testes Java e produz `build/libs/rptag-4.7.3.jar`. O primeiro
build pode levar mais tempo devido ao download de dependências e à preparação
do ambiente Minecraft.

## Documentação

| Documento | Conteúdo |
| --- | --- |
| [COMO-EDITAR.md](COMO-EDITAR.md) | Ambiente de desenvolvimento, build e testes |
| [ENTREGA.md](ENTREGA.md) | Arquitetura, convenções e continuidade do projeto |
| [PUBLICAR.md](PUBLICAR.md) | Preparação e publicação de releases |
| [CHANGELOG.md](CHANGELOG.md) | Histórico de versões |
| [WALKTHROUGH.md](WALKTHROUGH.md) | Registro técnico e decisões históricas |
| [arte/README.md](arte/README.md) | Produção e validação das animações |

A documentação de design em `docs/superpowers/` registra propostas e planos.
Sua presença não significa que a funcionalidade foi implementada ou publicada.

## Licença

Distribuído sob a [licença MIT](LICENSE).
