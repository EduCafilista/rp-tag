# RP Tag 5.61.0 — o tablet salva a SKIN da pessoa

**Baixar:** https://github.com/EduCafilista/rp-tag/releases/download/v5.61.0/rptag-5.61.0.jar
(também na sua área de trabalho como `rptag-5.61.0.jar`)

---

## O que você pediu

> "o tablet deve salvar a skin da pessoa, acha que consegue fazer isso? salvar o modelo que a
> pessoa tava usando quando criou/salvou o tablet?"

Consegui. A ficha agora guarda **a skin do momento em que a pessoa preencheu/confirmou** — não a
skin de agora.

## Como funciona

- **Quem sabe a skin é o cliente** (o servidor nunca vê o arquivo da skin). Então, quando o jogador
  abre a própria ficha pra editar e quando ele aperta **salvar/confirmar** (etapa 6), o cliente
  manda pro servidor: o **link da textura** + o **modelo** (Clássico ou Slim).
- O servidor confere e grava na ficha (`FichaRegistro` → `SkinUrl` + `SkinModel`), junto com o
  resto — então isso **salva no mundo** e vai no pacote do tablet.
- **Congela junto com a ficha**: depois que a ficha é confirmada (e sem liberação do admin), a
  skin **não muda mais**, mesmo que a pessoa troque de skin no launcher. É o retrato do momento.
- Trocar de skin **não** invalida o texto que o jogador está escrevendo (não mexe na revisão).

## O que aparece no tablet

No painel do personagem, ao lado do boneco 3D, entra um **rosto da skin salva** com o rótulo
`SKIN SALVA` e o modelo (`Clássico` / `Slim`). O boneco 3D continua sendo o jogador ao vivo — o
rosto guardado é o registro do momento em que ele salvou.

## Segurança e limites (importante)

- O servidor **só aceita link do servidor de texturas da Mojang**
  (`http://textures.minecraft.net/texture/...`) — cliente não pode mandar URL arbitrária.
- A imagem é baixada pelo próprio `HttpTexture` do Minecraft, com **cache em disco**: baixa uma vez
  e fica. Enquanto não chega, aparece um quadradinho escuro (nada de textura rosa de erro).
- **Se a skin não tem link** (jogador em servidor offline com skin vinda de arquivo local, sem
  perfil da Mojang), não há o que salvar — nesse caso a ficha fica sem skin guardada e o tablet
  mostra só o boneco ao vivo, como antes.

## Provas

- `check.py`: **1366 PASS / 0 FAIL** (8 provas novas);
- `arte/auditar.py`: **0 erro**;
- **servidor de teste subido com a 5.61.0**: `Done`, comandos do tablet ok, nenhuma exceção.

> O canal de rede subiu de **19 para 20** (o pacote novo da skin) — servidor e cliente precisam
> estar na MESMA versão.
