#!/usr/bin/env python3
"""Auditoria geral do mod (5.41.0) — acha erros que o check.py não cobre.

Roda de dentro de /home/user/rp-tag:  python3 arte/auditar.py
"""
import json
import os
import re
import sys

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
JAVA = os.path.join(RAIZ, "src", "main", "java", "dev", "rptag")
ASSETS = os.path.join(RAIZ, "src", "main", "resources", "assets", "rptag")
DATA = os.path.join(RAIZ, "src", "main", "resources", "data")

problemas = []
avisos = []


def ler(caminho):
    with open(caminho, encoding="utf-8") as fp:
        return fp.read()


def javas():
    for pasta, _, arquivos in os.walk(JAVA):
        for a in arquivos:
            if a.endswith(".java"):
                p = os.path.join(pasta, a)
                yield p, ler(p)


# ---------------------------------------------------------------- 1. pacotes registrados
fonte_net = ler(os.path.join(JAVA, "ModNetworking.java"))
for caminho, txt in javas():
    nome = os.path.basename(caminho)[:-5]
    if "implements CustomPacketPayload" not in txt:
        continue
    if re.search(r'^public (final )?class ' + nome + r'\b', txt, re.M):
        continue   # classe-container com records dentro (NeptunePackets)
    if (nome + ".TYPE") in fonte_net:
        continue
    problemas.append("pacote SEM registro: " + nome)

# ---------------------------------------------------------------- 2. @EventBusSubscriber sem @SubscribeEvent
for caminho, txt in javas():
    if not re.search(r"^@EventBusSubscriber", txt, re.M):
        continue
    if "@SubscribeEvent" not in txt:
        problemas.append("EventBusSubscriber sem nenhum @SubscribeEvent (derrubava o jogo): "
                         + os.path.basename(caminho))

# ---------------------------------------------------------------- 3. sons citados x sounds.json
sons_json = json.loads(ler(os.path.join(ASSETS, "sounds.json"))) if os.path.exists(
    os.path.join(ASSETS, "sounds.json")) else {}
sons_proprios = set(sons_json.get("sounds", {}).keys()) if isinstance(sons_json, dict) else set()
for caminho, txt in javas():
    for m in re.finditer(r"RPTagMod\.som\(?\s*\"([a-z0-9_]+)\"", txt):
        if m.group(1) not in sons_proprios:
            problemas.append("som proprio citado e nao existe em sounds.json: " + m.group(1))

# ---------------------------------------------------------------- 4. texturas citadas no Java
for caminho, txt in javas():
    for m in re.finditer(r'"(textures/[a-z0-9_/]+\.png)"', txt):
        alvo = os.path.join(ASSETS, m.group(1))
        if not os.path.exists(alvo):
            problemas.append("textura citada no Java nao existe: " + m.group(1))

# ---------------------------------------------------------------- 5. modelos: todo .json tem pai/textura
modelos = os.path.join(ASSETS, "models")
for pasta, _, arquivos in os.walk(modelos):
    for a in arquivos:
        if not a.endswith(".json"):
            continue
        p = os.path.join(pasta, a)
        j = json.loads(ler(p))
        pai = j.get("parent", "")
        if pai.startswith("rptag:"):
            if not os.path.exists(os.path.join(modelos, pai.split(":", 1)[1] + ".json")):
                problemas.append("modelo com pai inexistente: %s -> %s" % (a, pai))
        for k, v in (j.get("textures") or {}).items():
            if isinstance(v, str) and v.startswith("rptag:") and not v.startswith("rptag:item/"):
                if not os.path.exists(os.path.join(ASSETS, "textures", v.split(":", 1)[1] + ".png")):
                    problemas.append("modelo %s: textura %s nao existe" % (a, v))
            elif isinstance(v, str) and not v.startswith("rptag:item/"):
                pass
            elif isinstance(v, str) and v.startswith("rptag:item/"):
                if not os.path.exists(os.path.join(ASSETS, "textures", "item",
                                                   v.split("/")[-1] + ".png")):
                    problemas.append("modelo %s: textura %s nao existe" % (a, v))

# ---------------------------------------------------------------- 6. receitas: item resultante existe
itens_java = set()
for _, txt in javas():
    for m in re.finditer(r'ITEMS\.register\("([a-z0-9_]+)"', txt):
        itens_java.add("rptag:" + m.group(1))
for pasta, _, arquivos in os.walk(os.path.join(DATA, "rptag", "recipes")):
    for a in arquivos:
        if not a.endswith(".json"):
            continue
        j = json.loads(ler(os.path.join(pasta, a)))
        res = j.get("result") or {}
        rid = res.get("id") or res.get("item")
        if isinstance(rid, str) and rid.startswith("rptag:") and rid not in itens_java:
            problemas.append("receita %s produz item que nao existe: %s" % (a, rid))

# ---------------------------------------------------------------- 7. comandos literais duplicados
for caminho, txt in javas():
    if "Commands.literal(" not in txt:
        continue
    vistos = {}
    for m in re.finditer(r"Commands\.literal\(\"([a-z0-9_]+)\"\)", txt):
        nome = m.group(1)
        vistos.setdefault(nome, 0)
        vistos[nome] += 1
    for nome, n in vistos.items():
        if n > 1:
            avisos.append("%s: literal(\"%s\") aparece %d vezes (confira se nao e o mesmo ramo)"
                          % (os.path.basename(caminho), nome, n))

# ---------------------------------------------------------------- 8. chaves de lang
for arquivo in ("pt_br.json", "en_us.json"):
    p = os.path.join(ASSETS, "lang", arquivo)
    if not os.path.exists(p):
        problemas.append("lang faltando: " + arquivo)
        continue
    idioma = json.loads(ler(p))
    for item in sorted(itens_java):
        chave = "item." + item.replace(":", ".")
        if chave not in idioma:
            avisos.append("%s: sem nome para %s" % (arquivo, item))

# ---------------------------------------------------------------- 9. coisas que o dono pediu pra tirar
for caminho, txt in javas():
    if "isFoil" in txt and "VarinhaSombraItem" in caminho:
        problemas.append("o umbrella voltou a ser encantado (isFoil)")

print("=" * 60)
for p in problemas:
    print("ERRO  " + p)
for a in avisos:
    print("aviso " + a)
print("=" * 60)
print("auditar.py: %d erro(s), %d aviso(s)" % (len(problemas), len(avisos)))
sys.exit(1 if problemas else 0)
