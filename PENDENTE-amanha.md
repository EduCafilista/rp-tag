# 📌 Pendente pra amanhã — aviso SANGUE (vtjau7)

## O que ficou parado
O user mandou o protótipo do novo aviso **SANGUE** em:
`https://files.catbox.moe/vtjau7.java`

**O arquivo está VAZIO no catbox** — o servidor responde
`HTTP 200` com `content-length: 0` (tentado 6x, com cache-buster, `Range`,
HTTP/1.1 e user-agent). Os arquivos antigos do mesmo catbox
(`9mvdzc.java` 11117 B, `1jsrwv.java` 7552 B) baixam normalmente,
então **não é bloqueio daqui**: o upload não completou.

## Como resolver quando voltar
1. Subir de novo no catbox (conferir que o arquivo tem conteúdo antes), **ou**
2. **Colar o código direto no chat** — jeito à prova de falha, **ou**
3. Se for grande (>100 KB), mandar em 2 partes.

## Decisão em aberto (perguntei, não respondeu)
O estilo **9 = SANGUE** hoje é o `client/SangueAvisoFx.java` ("Sangria",
integrado na 3.95.0 a partir do protótipo `01a9gl.java`): impacto com espirro
de gotas, crosta pingando do teto, poça subindo do rodapé, veias crescendo das
bordas, texto que trepida e PINGA.
Opções: **(a)** substituir o atual (guardar backup do Sangria),
**(b)** criar um estilo novo (13º, ex. `sangue2`) mantendo os dois,
**(c)** decidir depois de ver o protótipo rodando.

## Checklist de integração (padrão do projeto — não esquecer)
- [ ] `package dev.rptag.client;` + `import static ...AvisoFxUtil.*;`
- [ ] `public static void iniciar(String titulo, String mensagem)`
- [ ] **MENSAGEM embaixo do título**: `translate(cx - f.width(sub)*S2/2, cy + 25*k)`,
      `S2 = min(k*0.75f, w*0.85f/width)` — `ty - 12*k` é PROIBIDO
- [ ] `registerAboveAll` em `ClientGuiLayers` (nunca `RenderGuiEvent.Post`)
- [ ] rota em `ClientEffects.startScreenText` (estilo 9)
- [ ] partículas + som no SERVIDOR (`RPCommands.somAviso`, caso 9)
- [ ] `check.py` antes do `print(PASS/FAIL)` e **327 → sobe** (nunca diminui)
- [ ] verificação visual em `verificacao/` (o user cobra a cada ciclo)
- [ ] CHANGELOG + WALKTHROUGH no fim do ciclo
- [ ] jar → Release nova + **apagar release/tag anterior**
- [ ] **nenhum** jar no git (regra do amigo, 4.2.0)

## Estado do projeto (verificado agora)
- Versão **4.2.0**, `check.py` **327 PASS / 0 FAIL**
- HEAD `cdd4fe9` = remoto, `git status` limpo
- 288 frames do vídeo do MORTE presentes
- Release **v4.2.0** (id 417947996) = latest, jar 10.298.836 B
- Backups em `/home/user/backup/` (4.1.0 src+jar+bundle) e
  `/home/user/rp-tag-git.bundle` (19 MB)

## ⚠️ Aviso recorrente do ambiente
O workspace **perdeu arquivos 5x** nesta sessão (o `.git` local volta pro
commit 3.84 e centenas de arquivos somem do disco). **Nada se perde de verdade**
— tudo está no GitHub. Ao abrir o turno, se algo parecer estranho:
```bash
cd /home/user/rp-tag
git remote add origin "https://<PAT>@github.com/EduCafilista/rp-tag.git"
git fetch --tags origin && git reset --hard origin/main
git remote set-url origin https://github.com/EduCafilista/rp-tag.git
```
