# ENTREGA 5.11.2 — o TÍTULO agora aparece no CHAT

**Data:** 02/11/2026
**Jar:** `rptag-5.11.2.jar` — **69.300.292 B (66,1 MB)** · md5 **`f2eeab3579020cbc92ffd1cb8c9e3a29`**
**Download:** _(preenchido depois da release)_
**Verificação:** `check.py` **801 PASS / 0 FAIL** · servidor sobe com **0 ERROR** · linha do chat conferida no servidor

---

## O que estava errado

O título (etiqueta + título pessoal) era aplicado só no **`PlayerEvent.NameFormat`** — que é o que
alimenta o **TAB** e o **nametag**. Só que o **chat por proximidade** (que é o padrão do servidor) não passa
por aí: ele é montado à mão em `RPChat.displayName()`, e essa função juntava apenas **persona + tag RP**.

Por isso o mesmo jogador aparecia assim:

| onde | como aparecia |
|---|---|
| TAB / cabeça | `[Sagrado] Seraphiel [O Ferreiro] ● ʀᴘ` |
| **chat** | `Seraphiel ● ʀᴘ` ← **sem título** |

## O conserto

`RPChat.displayName()` passou a montar o nome pelo mesmo caminho do TAB
(`Titulos.aplicar`), através de um método novo `RPChat.montarNome(server, uuid, nome, inRp)`.

Como **todos** os canais de chat usam `displayName`, o título entrou em todos de uma vez:

| canal | comando |
|---|---|
| chat local (o padrão) | digitar no chat |
| global | `/g <msg>` |
| grito | `/s <msg>` |
| sussurro | `/w <msg>` |
| pensamento | `/pensar <msg>` |
| ação de RP | `/me <ação>` |
| chat vanilla (quando o chat local está desligado) | já vinha certo pelo `NameFormat` |

## Como ficou (saída real do servidor de teste)

```
CHAT: <[Sagrado] Seraphiel [O Ferreiro]● ʀᴘ> a lamina nao mente.
```

A ordem é a **mesma do TAB**: **etiqueta na frente**, nome no meio, **título pessoal depois do nome** e a
tag RP no fim. O gradiente letra por letra da etiqueta, o negrito dela e o itálico do título pessoal
continuam valendo no chat (é o mesmo `TituloEstilo`).

> Para conferir sem abrir o jogo, usei um comando temporário que montava a linha no servidor e devolvia
> pelo RCON. Ele **foi removido antes da release** — não existe no jar.

---

## Como testar em 30 segundos

1. `/titulo definir @a Sagrado` e `/titulo personalizado SeuNome O Ferreiro`.
2. Fale no chat normalmente → `<[Sagrado] SeuPersonagem [O Ferreiro]● ʀᴘ> sua fala`.
3. Teste os outros canais: `/g`, `/s`, `/w`, `/pensar`, `/me` — todos devem mostrar o título.
4. O TAB e o nome acima da cabeça continuam iguais (nada mudou ali).

---

## Arquivos

- `src/main/java/dev/rptag/RPChat.java` — `displayName()` agora delega para o novo `montarNome(...)`, que aplica o título.
- `CHANGELOG.md`, `WALKTHROUGH.md` — a causa, o conserto e a lição (todo texto com nome fora do `NameFormat` tem que passar por `displayName`/`Titulos.aplicar`).
