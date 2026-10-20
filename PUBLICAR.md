# Como publicar no GitHub (rotina atual — 4.1.0)

Passo a passo do que **realmente** é feito a cada versão nova.
O repo é `github.com/EduCafilista/rp-tag` (público).

> 🧹 **Regra número 1: só a versão mais recente fica publicada.**
> A cada ciclo, publique a release nova e **apague a release + a tag anteriores**.
> O histórico de código **não se perde** — todos os commits continuam na `main`.
> Cuidado extra: **nenhum jar** fica commitado no repo (desde a 4.2.0 o jar
> vai só na Release — ver a seção 5).

---

## 1. Antes de commitar: confira o `.git`

O ambiente de trabalho já perdeu o `.git` várias vezes. **Sempre** olhe o HEAD
antes de commitar:

```bash
git log --oneline -1
```

Se **não** for o commit do ciclo anterior, **não commite por cima** — busque o
remoto primeiro:

```bash
git fetch --tags origin main
git reset --mixed origin/main     # NÃO toca nos arquivos do disco
git status --porcelain            # o diff deve ser só o do ciclo atual
```

---

## 2. Bump da versão (3 lugares + log)

| Arquivo | O que muda |
|---|---|
| `src/main/java/dev/rptag/RPTagMod.java` | `VERSION = "4.1.0"` |
| `build.gradle` | `version = '4.1.0'` |
| `src/main/resources/META-INF/neoforge.mods.toml` | `version="4.1.0"` |
| `src/main/java/dev/rptag/client/ClientEffects.java` | log de boot `"RP Tag 4.1.0: ..."` |
| `check.py` | a verificação de versão também muda |

⚠️ No `.toml`, a faixa do Minecraft é `[1.21.1,1.22)` — colocar `[1.21.1]` **mata o boot**.

---

## 3. Testar (obrigatório antes de publicar)

```bash
python3 check.py          # tem que dar 0 FAIL
```

Depois compile e faça o smoke do servidor:

1. coloque **apenas** o jar novo em `run/mods/` (apague o antigo!);
2. suba o servidor, espere `RCON running`;
3. rode alguns comandos por RCON (`rp admin avisar @a 4 teste`, `lorezone listar`…);
4. `grep -ac "ERROR\|FATAL" logs/latest.log` → **0**;
5. pare o servidor.

---

## 4. Commit e push

```bash
git add <arquivos do ciclo>       # deixe o jar FORA deste add
git commit -m "4.1.0: <resumo do ciclo>"

git push "https://<TOKEN>@github.com/EduCafilista/rp-tag.git" HEAD:main
```

- O **token (PAT) vai inline na URL** do push. **Nunca** salvar em arquivo,
  no `.git/config` ou em script versionado.
- Se o `git remote` apontar pra URL com token, **volte** pra URL limpa depois:
  `git remote set-url origin https://github.com/EduCafilista/rp-tag.git`.

---

## 5. Jars: NENHUM no repositório (desde a 4.2.0)

Com os avisos em vídeo o jar cresce a cada ciclo e passa de 100 MB, que é o
limite do GitHub para arquivos no git. Por isso o jar **não vai mais pro repo**:
ele fica **só na Release**. O `.gitignore` tem:

```
*.jar
!gradle/wrapper/gradle-wrapper.jar
```

**Confira sempre** (o `git status` esconde esse tipo de coisa):

```bash
git ls-tree HEAD --name-only | grep jar     # deve listar NADA na raiz
```

---

## 6. Criar a release pela API

Sem `target_commitish`: **crie a tag pelo git e publique a release sem esse campo**
(com SHA curto o GitHub responde `422 tag_name is not a valid tag`).

```bash
PAT="<token>"
git tag -f v4.1.0 HEAD
git push "https://$PAT@github.com/EduCafilista/rp-tag.git" refs/tags/v4.1.0

# 1) cria a release
curl -X POST -H "Authorization: Bearer $PAT" -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/EduCafilista/rp-tag/releases -d @release.json
#    release.json = { "tag_name": "v4.1.0", "name": "...", "body": "..." }

# 2) sobe o jar
curl -X POST -H "Authorization: Bearer $PAT" -H "Content-Type: application/java-archive" \
  "https://uploads.github.com/repos/EduCafilista/rp-tag/releases/<ID>/assets?name=rptag-4.1.0.jar" \
  --data-binary @rptag-4.1.0.jar

# 3) marca como latest
curl -X PATCH -H "Authorization: Bearer $PAT" -H "Content-Type: application/json" \
  https://api.github.com/repos/EduCafilista/rp-tag/releases/<ID> \
  -d '{"make_latest":"true"}'
```

**Notas da release (PT-BR)**: título com emoji + 3 blocos — *o que mudou* (em
tópicos curtos), *como instalar* (baixar o asset, jogar em `mods` no cliente **e**
servidor, **apagar o jar antigo**), e o aviso de que o **Portais da Lore segue em
pausa**. No fim, a linha de testes (`check.py` + smoke).

---

## 7. Limpar as versões antigas

```bash
# lista as releases
curl -s -H "Authorization: Bearer $PAT" \
  "https://api.github.com/repos/EduCafilista/rp-tag/releases?per_page=100"

# apaga a release antiga (mantém o commit!)
curl -X DELETE -H "Authorization: Bearer $PAT" \
  https://api.github.com/repos/EduCafilista/rp-tag/releases/<ID_ANTIGA>

# apaga a tag antiga
curl -X DELETE -H "Authorization: Bearer $PAT" \
  https://api.github.com/repos/EduCafilista/rp-tag/git/refs/tags/v<versao_antiga>
```

**Exceção permanente**: `portais-v1.3.2` **não** é apagada — é a única release do
segundo mod do repo (que está em pausa) e o aviso de pausa nunca sai.

---

## 8. Conferir tudo (nunca confie, verifique)

```bash
# 1) só devem sobrar v4.1.0 e portais-v1.3.2
curl -s -H "Authorization: Bearer $PAT" \
  "https://api.github.com/repos/EduCafilista/rp-tag/releases?per_page=100"

# 2) o download precisa responder e ser idêntico ao jar local
curl -sL -o /tmp/dl.jar -w "HTTP %{http_code} bytes=%{size_download}\n" \
  https://github.com/EduCafilista/rp-tag/releases/download/v4.1.0/rptag-4.1.0.jar
cmp /tmp/dl.jar rptag-4.1.0.jar && echo "JAR OK"

# 3) a tag tem que apontar pro commit publicado
curl -s -H "Authorization: Bearer $PAT" \
  https://api.github.com/repos/EduCafilista/rp-tag/git/ref/tags/v4.1.0

# 4) nenhum jar antigo na raiz
curl -s "https://api.github.com/repos/EduCafilista/rp-tag/contents/?ref=main" \
  | grep -o '"name": "rptag-[^"]*\.jar"'
```

Se o **seu navegador** ainda mostrar versão antiga depois de tudo isso, é cache:
**Ctrl+Shift+R** (ou aba anônima).

---

## 9. Backup local do histórico

O `.git` deste ambiente já regrediu várias vezes. Pra não depender da rede:

```bash
git bundle create /home/user/rp-tag-git.bundle --all
# restaurar: git clone /home/user/rp-tag-git.bundle
```

---

## 10. Portais da Lore (segundo mod do repo)

- Está **EM PAUSA** — **não mexer** sem pedido explícito.
- O aviso de pausa existe no `README.md` da raiz, no README da pasta e na release
  `portais-v1.3.2`. **Não remover.**
- Os READMEs mencionam a pausa; a release dele fica fixa como está.
