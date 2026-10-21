# Publicação de releases

Procedimento de preparação, publicação e verificação de uma versão do RP Tag.
Execute os comandos na raiz do repositório. A publicação exige permissão de
escrita no projeto e uma versão de código previamente validada.

## Autenticação e estado do repositório

Utilize a autenticação do GitHub CLI ou um gerenciador de credenciais. Não inclua
tokens em URLs de remotes, comandos versionados ou documentação.

```bash
gh auth status
git status --short
git log -5 --oneline
git fetch origin
git remote -v
```

O remote esperado é `https://github.com/EduCafilista/rp-tag.git`. Revise as
diferenças entre o branch local e o remoto antes de publicar. Não utilize reset
ou force push como parte automática do processo de entrega.

## Versão e validação

Atualize a versão em:

| Arquivo | Campo |
| --- | --- |
| `build.gradle` | `version` |
| `src/main/java/dev/rptag/RPTagMod.java` | `VERSION` |
| `src/main/resources/META-INF/neoforge.mods.toml` | `version` |
| `src/main/java/dev/rptag/client/ClientEffects.java` | Log de inicialização |

Atualize também o README, o changelog e os documentos afetados. Exemplos abaixo
utilizam a versão de código 4.5.0; substitua o número pela versão da entrega.

```powershell
.\gradlew.bat clean build
python check.py
python -m unittest discover -s arte/tests -v
git diff --check
```

Em Linux e macOS, utilize `./gradlew` e o executável Python correspondente ao
ambiente. Confira o JAR em `build/libs/`, os testes e o comportamento no jogo.
Mudanças visuais exigem verificação no cliente; o funcionamento do servidor
isoladamente não comprova a renderização correta.

## Commit e envio do código

Revise o diff e adicione os caminhos explícitos do trabalho concluído.

```bash
git diff --stat
git diff --cached
git commit -m "Descrição objetiva da alteração"
git push origin main
```

O exemplo de commit pressupõe que os arquivos já foram adicionados ao índice.
Não inclua trailers de ferramentas nas mensagens de commit.

## Distribuição dos binários

Os JARs são distribuídos como assets de release. Não adicione o JAR do mod à
raiz do repositório. A exceção versionada é o Gradle Wrapper.

```gitignore
*.jar
!gradle/wrapper/gradle-wrapper.jar
```

Verifique os arquivos rastreados com `git ls-files '*.jar'`.

## Criação da release

Prepare notas em um arquivo Markdown com os recursos alterados, compatibilidade,
instruções de atualização e limitações conhecidas. Crie uma release em rascunho
para revisar o arquivo e o commit de destino antes da publicação.

```bash
gh release create v4.5.0 build/libs/rptag-4.5.0.jar --repo EduCafilista/rp-tag --target main --title "RP Tag 4.5.0" --notes-file notas-da-release.md --draft
```

Confirme que o nome da tag está disponível antes de executar. Caso a tag já
exista, verifique a versão publicada; não substitua um artefato existente sem
avaliar a necessidade de uma nova versão.

Depois da revisão e da autorização para publicar:

```bash
gh release edit v4.5.0 --repo EduCafilista/rp-tag --draft=false --latest
```

## Verificação após publicação

1. Confirme o commit associado à tag e o conteúdo das notas.
2. Confira o nome e o tamanho do asset publicado.
3. Baixe o JAR e compare seu SHA-256 com o arquivo validado localmente.
4. Verifique se os links de download da documentação apontam para a versão correta.

A política registrada do projeto mantém a versão mais recente do RP Tag
publicada. A remoção de releases ou tags anteriores deve ocorrer somente após
a nova versão ser verificada e com autorização do responsável. Preserve os
artefatos do projeto Portais da Lore quando a entrega envolver apenas o RP Tag.

Não trate a conclusão de um build ou de um push como comprovação de que uma
release foi criada e disponibilizada para download.
