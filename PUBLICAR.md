# Como publicar no GitHub

Passo a passo completo para subir este projeto e lançar a release com o jar.

## 1. Criar o repositório no GitHub

1. Acesse https://github.com/new
2. **Repository name:** `rp-tag`
3. **Description** (copie e cole):

   ```
   Mod NeoForge 1.21.1 que mostra (ʀᴘ)/(ᴏꜰꜰ ʀᴘ) no nome dos jogadores e adiciona o comando /rp — feito para servidores de RPG.
   ```

4. Marque **Public**. NÃO marque "Add a README" (já temos um aqui).
5. Clique em **Create repository**.

**Topics sugeridos** (na engrenagem ao lado de "About"):
`minecraft` · `mod` · `neoforge` · `minecraft-mod` · `rpg` · `roleplay` · `java`

## 2. Subir o código

```bash
cd rp-tag

git init
git add .
git commit -m "RP Tag 2.0.0: tag (ʀᴘ)/(ᴏꜰꜰ ʀᴘ) no nome + comando /rp (NeoForge 1.21.1)"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/rp-tag.git
git push -u origin main
```

> Troque `SEU_USUARIO` pelo seu usuário do GitHub.

## 3. Criar a release com o jar

```bash
git tag -a v2.1.0 -m "v2.1.0"
git push origin v2.1.0
```

Depois, no GitHub: **Releases → Draft a new release → escolha a tag `v2.1.0`**
e anexe o arquivo **`rptag-2.1.0.jar`** (arraste o arquivo no campo de anexos).

**Release notes** (copie e cole):

---

### RP Tag v2.1.0

Primeira versão pública! 🎉

Mod para **NeoForge 1.21.1** pensado para servidores de RPG:

- **(ʀᴘ)** ciano ou **(ᴏꜰꜰ ʀᴘ)** cinza no nome do jogador — entre parênteses e
  com fonte diferente (small capitals), colorindo **apenas a tag**
- A tag aparece no **nametag** (em cima da cabeça), no **chat** e na **lista TAB**
- Comando **`/rp`** para todos os jogadores: alternar, `/rp on`, `/rp off`,
  `/rp status` — e `/rp set <jogador> on|off` para admins
- Estado **salvo no mundo**: sobrevive a relog, morte e reinício do servidor
- Sem dependências além do NeoForge

**Instalação:** coloque o jar na pasta `mods/` do servidor (e do cliente para ver
a tag em cima da cabeça).

**Correções:** v1.2.0 renderizava caracteres coreanos no lugar da tag (bug de
truncamento Unicode) — corrigido e validado contra a fonte do jogo.

---

## 4. Atualizações futuras

A cada versão nova:

1. Atualize a versão em **3 lugares**: `build.gradle`, `neoforge.mods.toml` e `RPTagMod.java`
2. Adicione uma seção nova no `CHANGELOG.md`
3. `git commit`, crie a tag `vX.Y.Z`, dê push e crie a Release com o jar novo
