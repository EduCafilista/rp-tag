# RP Tag 5.49.0 — pedido 106

**Jar:** `rptag-5.49.0.jar` (37.325.525 bytes)
**Servidor:** NeoForge 21.1.249 / Java 21 — `RP Tag 5.49.0`, `Done (1.034s)`, 0 exceções
**Provas:** `python3 check.py` → **1413 PASS / 0 FAIL** · auditor do mod: **0 ERRO**

---

## O item: **Porte de Arma** (`rptag:porte_de_arma`)

Você: *"faça um item chamado 'Porte de arma', apenas um contrato, qualquer coisa do tipo"*.
Feito — é **só o documento**, sem mecânica nenhuma.

| | |
|---|---|
| ID | `rptag:porte_de_arma` |
| Nome | **Porte de Arma** (pt) · *Firearm Permit* (en) |
| Pegar | `/give @s rptag:porte_de_arma` |
| Empilha | 1 (é um documento assinado, não um pacote de folhas) |
| Raridade | Incomum (nome em amarelo) |
| Clique direito | **nada** — de propósito, não existe regra aqui para quebrar depois |

### A arte
Pergaminho com o título em vermelho, o corpo do contrato, a **linha de assinatura** com o rabisco
e o **selo de cera vermelho** no canto (`arte/gerar_porte_de_arma.py`, 64x64 como as outras
texturas do mod).

### Assinar o contrato (de graça, sem mecânica nova)
Renomeia na **bigorna** com o nome do portador — o tooltip passa a mostrar:

```
Autoriza o porte de arma em nome do portador.
Documento de roleplay — não é uma arma.
Portador: Ana Silva
```

Conferido no servidor de verdade por RCON:

```
summon ... {id:"rptag:porte_de_arma"}                        → Summoned new Firearm Permit
summon ... {id:"rptag:porte_de_arma", CustomName:"Ana Silva"} → Summoned new Ana Silva
```

## O que ficou pendente
- **1ª pessoa** do guarda-chuva (a 3ª pessoa da 5.48.0 já está ancorada na mão) — se estiver
  errada aí também, eu aplico a mesma âncora no `RenderHandEvent`.
- O **redesenho das 5 molduras do editor de balão**.
