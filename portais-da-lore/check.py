#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check.py do mod PORTAIS DA LORE (loreportals) — sanidade estrutural."""
import os, re, sys, json

RAIZ = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(RAIZ, "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "loreportals")
RES = os.path.join(SRC, "resources")

ok = falhas = 0
def chk(nome, cond, extra=""):
    global ok, falhas
    if cond:
        ok += 1
    else:
        falhas += 1
        print(f"  FALHOU: {nome} {extra}")

def ler(*p):
    with open(os.path.join(*p), encoding="utf-8") as f:
        return f.read()

def tem(*p):
    return os.path.isfile(os.path.join(*p))

def jjson(*p):
    with open(os.path.join(*p), encoding="utf-8") as f:
        return json.load(f)

# ---------------- versao ----------------
main = ler(JAVA, "LorePortalsMod.java")
toml = ler(RES, "META-INF", "neoforge.mods.toml")
gradle = ler(RAIZ, "build.gradle")
chk("MODID constante", 'MODID = "loreportals"' in main)
chk("VERSION 1.3.2 (java)", 'VERSION = "1.3.2"' in main)
chk("modId no mods.toml", 'modId="loreportals"' in toml)
chk("version no mods.toml", 'version="1.3.2"' in toml)
chk("version no build.gradle", "version = '1.3.2'" in gradle)
chk("modid NAO e rptag", "rptag" not in main.lower() and "rp-tag" not in main.lower())

# ---------------- arquivos java ----------------
JAVAS = ["LorePortalsMod.java", "PortalCoreBlock.java", "PortalFrameBlock.java",
         "PortalData.java", "PortalBlockEntity.java", "PortalWorldTick.java",
         "CristalVinculoItem.java", "PortalCommands.java"]
for j in JAVAS:
    chk(f"java {j}", tem(JAVA, j))
chk("java client/PortalRenderer", tem(JAVA, "client", "PortalRenderer.java"))
chk("java client/ClientInit", tem(JAVA, "client", "ClientInit.java"))

# ---------------- mecanica: anel + disco ----------------
be = ler(JAVA, "PortalBlockEntity.java")
chk("TIPO_PAR", 'TIPO_PAR = "PAR"' in be)
chk("TIPO_MOLDE", 'TIPO_MOLDE = "MOLDE"' in be)
m = re.search(r"static final int\[\]\[\] ANEL = \{(.*?)\};", be, re.S)
if m:
    n = len(re.findall(r"\{", m.group(1)))
    chk("ANEL = 11 offsets exatos", n == 11, f"(achei {n})")
else:
    chk("ANEL existe", False)
chk("FORMATO FLEXIVEL: geometriaDoFormato com flood-fill", "geometriaDoFormato" in be and "ArrayDeque" in be)
chk("FORMATO FLEXIVEL: anel classico e caminho rapido", "anelFechado(facing)" in be)
chk("FORMATO FLEXIVEL: renderRx/Ry/Cy/CentroDu sincronizados",
    all(x in be for x in ["renderRx", "renderRy", "renderCy", "renderCentroDu"])
    and 'putFloat("rx"' in be)
chk("FORMATO FLEXIVEL: msg de falha atualizada", "Formato nao fechado" in ler(JAVA, "PortalCoreBlock.java"))
chk("FORMATO FLEXIVEL: disco elastico no renderer", "rx = be.renderRx" in ler(JAVA, "client", "PortalRenderer.java"))
chk("disco MOLDE r 2.05", "DISCO_RAIO_MOLDE = 2.05F" in be)
chk("disco MOLDE y 2.45", "DISCO_Y = 2.45F" in be)
chk("disco PAR r 0.95", "DISCO_RAIO_PAR = 0.95F" in be)
chk("oval PAR centro y 1.7", "DISCO_Y_PAR = 1.7F" in be)
chk("offset() cobre 4 facings", be.count("case ") >= 4 or "switch" in be)
chk("nucleo e BaseEntityBlock", "extends BaseEntityBlock" in ler(JAVA, "PortalCoreBlock.java"))
chk("core valida anel no use", "tentarAtivarPorMoldura" in ler(JAVA, "PortalCoreBlock.java"))

# ---------------- dados persistentes ----------------
pd = ler(JAVA, "PortalData.java")
chk("SavedData", "extends SavedData" in pd)
chk("nome do save", 'NOME = "loreportals_data"' in pd)
chk("vinculo simetrico", "comLinked(keyB)" in pd and "comLinked(keyA)" in pd)
chk("desvincular simetrico", pd.count("comLinked(null)") >= 2)
chk("contagem de viagens", "comViagem" in pd and "contarViagem" in pd)
chk("viagens salvas no NBT", '"viagens_jogadores"' in pd)

# ---------------- tick / teleporte ----------------
tick = ler(JAVA, "PortalWorldTick.java")
chk("cooldown de teleporte", "COOLDOWN_MS" in tick)
chk("ticks pra teleportar", "TICKS_PARA_TELEPORTAR" in tick)
chk("acorda nucleos (moldura)", "procurarNucleosDormantes" in tick)
chk("conta viagem ao teleportar", "contarViagem" in tick)
chk("deteccao PAR cobre o oval (-1.3..1.2)", "-1.3" in tick and "1.2" in tick)
chk("SEM msg de teleporte (spam removido)", "atravessou" not in tick)
chk("PAR = oval VERTICAL centro 1.7", "DISCO_Y_PAR = 1.7F" in be)
chk("oval gira (rotacao Y no render)", "age * 12.0F" in ler(JAVA, "client", "PortalRenderer.java"))
chk("oval: bracos elipticos + borda dupla", "bracoE" in ler(JAVA, "client", "PortalRenderer.java") and "faixaV" in ler(JAVA, "client", "PortalRenderer.java"))
chk("chegada PAR = em cima do nucleo (1.02)", "1.02" in tick and "alturaChegada" in tick)
chk("ServerTickEvent", "ServerTickEvent" in tick)

# ---------------- cristal ----------------
cr = ler(JAVA, "CristalVinculoItem.java")
chk("cristal stack 1", "stacksTo(1)" in cr)
chk("shift desvincula", "isShiftKeyDown" in cr and "desvincular" in cr)
chk("cristal ativa PAR solto", "ativarParSolto" in cr or "registrar" in cr)
chk("1o clique REGISTRA o nucleo (bug 1.0.0)", cr.count("ativarParSolto") >= 2)
chk("clica na MOLDURA acha o nucleo", "PortalFrameBlock" in cr and "nucleosProximos" in cr)
chk("marcacao perdada limpa", "MARCADO.remove(id)" in cr)
chk("tooltip do cristal", "appendHoverText" in cr)

# ---------------- comandos ----------------
pc = ler(JAVA, "PortalCommands.java")
chk("registro de comandos", "RegisterCommandsEvent" in pc)
for sub in ["ajuda", "info", "listar", "ir", "desvincular", "apagar", "contar"]:
    chk(f"/portal {sub}", f'"{sub}"' in pc)
chk("perm op p/ listar", "hasPermission(2)" in pc)

# ---------------- render ----------------
pr = ler(JAVA, "client", "PortalRenderer.java")
ci = ler(JAVA, "client", "ClientInit.java")
chk("renderer registra no evento certo", "EntityRenderersEvent$RegisterRenderers" in ci
    or "EntityRenderersEvent.RegisterRenderers" in ci)
chk("registerBlockEntityRenderer", "registerBlockEntityRenderer" in ci)
chk("galaxia usa o lightning vanilla (caminho Iris-safe)", "RenderType.lightning()" in pr)
chk("galaxia: bracos espirais", "braco" in pr)
chk("galaxia: estrelas piscando", "hash" in pr)
chk("bounding box ampliado", "getRenderBoundingBox" in pr and "inflate" in pr)
chk("jeito vanilla: dispatcher posiciona, render so ajusta centro", "pose.translate(0.5F, 0.0F, 0.5F)" in pr and "getMainCamera" not in pr)
chk("rotaciona ao redor do centro (translate 0.5 na origem)", "pose.translate(0.5F, 0.0F, 0.5F)" in pr)
chk("quads explicitos de 4 vertices", "quad4" in pr and pr.count("quad4(") >= 5)
chk("RenderType.lightning (caminho vanilla, conhecido do Iris)", "RenderType.lightning()" in pr and "RenderType.create(" not in pr)
chk("DUPLO-EMITE frente+verso (cull mostra 1 copia por lado)", pr.count("vc.addVertex(pose, ax, ay, az)") == 2)
chk("SUPERELIPSE: galaxia vira janela da abertura", "FORMA_E" in pr and "private static float su(" in pr)
chk("estrelas de 4 PONTAS (sem quadrados-bug)", "estrela4(pose, vc" in pr)
chk("nucleo REDONDO pulsante", "lequeR(pose, vc" in pr)
gx = pr[pr.index("private static void galaxia("):pr.index("// formas e efeitos")]
chk("JANELA ARRUMADA: gradiente vertical no painel (lequeGrad)", "lequeGrad(pose, vc, cy, rx, ry" in pr)
chk("painel VISIVEL (alpha 118 — sem ceu no lugar da galaxia)", "118)" in gx)
chk("nucleo sem riscos de cruz no MOLDE", "flare(" not in gx)
chk("SOM calibrado: entrada 0.55 / chegada 0.5", "SOM_PORTAL_ENTRADA.get(), SoundSource.BLOCKS, 0.55F" in tick and "SOM_PORTAL_ENTRADA.get(), SoundSource.BLOCKS, 0.5F, 1.25F" in tick)
chk("MOLDE sem circulos: galaxia sem faixaV/bracoV", "faixaV(" not in gx and "bracoV(" not in gx)
chk("pulso sem circulo: faixaR removida", "faixaR" not in pr)
chk("estrelas mantidas (aprovado pelo user)", "estrela4(pose, vc" in pr and "for (int s = 0; s < 26" in pr)

chk("ANIMACAO: onda de travessia no disco", "renderPulso" in pr and "private static void pulso(" in pr)
chk("cliente revalida o formato (some ao quebrar sem rede)", "revalidarVisualCliente" in pr and "% 15 == 0" in pr)
chk("BE: pulso salvo/sincronizado (NBT pul)", 'putLong("pul"' in be and "dispararPulso" in be)
chk("TICK: pulso na origem E no destino", tick.count("dispararPulso()") >= 2)
chk("chegada: particulas REVERSE_PORTAL", "REVERSE_PORTAL" in tick)
chk("SOM: evento portal_entrada registrado (DeferredRegister SoundEvent)", "SOM_PORTAL_ENTRADA" in main and "createVariableRangeEvent" in main)
chk("SOM: sounds.json aponta pro ogg do mod", 'portal_entrada' in ler(RES, "assets", "loreportals", "sounds.json"))
chk("SOM: ogg empacotado (sounds/portal_entrada.ogg)", tem(RES, "assets", "loreportals", "sounds", "portal_entrada.ogg"))
chk("SOM de entrada toca na ORIGEM", tick.count("SOM_PORTAL_ENTRADA.get()") >= 2)
chk("ANIMACAO de carga: espiral convergindo no player", "private static void carregar(" in tick and "PORTAL_AMBIENT" in tick)

chk("RAIZ: todo vertice passa pela POSE (bug portal-na-cabeca)", "vc.addVertex(pose," in pr)
chk("RAIZ: nenhum addVertex cru sem pose", "vc.addVertex(ax" not in pr and "vc.addVertex(cx" not in pr)
chk("CRASH SODIUM: NENHUM setIdentity no codigo (nao-comentario)", pr.split("//")[0].count("setIdentity") == 0 and ".setIdentity()" not in pr)
chk("CRASH SODIUM: pushPose/popPose emparelhados", pr.count("pose.pushPose()") == pr.count("pose.popPose()"))
chk("BLINDAGEM: render inteiro em try/catch (crash-proof)", "catch (Throwable" in pr and "JA_LOGOU_ERRO" in pr)
chk("BLINDAGEM: guard de level nulo", "be.getLevel() == null" in pr)
chk("sem texturas no render (POSITION_COLOR)", "addVertex" in pr and "blit(" not in pr)

# ---------------- recursos ----------------
chk("pack.mcmeta", tem(RES, "pack.mcmeta"))
chk("mods.toml", tem(RES, "META-INF", "neoforge.mods.toml"))
A = os.path.join(RES, "assets", "loreportals")
for bs in ["portal_core", "portal_frame"]:
    chk(f"blockstate {bs}", tem(A, "blockstates", f"{bs}.json"))
    chk(f"model block {bs}", tem(A, "models", "block", f"{bs}.json"))
    chk(f"model item {bs}", tem(A, "models", "item", f"{bs}.json"))
    chk(f"textura {bs}", tem(A, "textures", "block", f"{bs}.png"))
chk("model cristal", tem(A, "models", "item", "cristal_vinculo.json"))
chk("textura cristal", tem(A, "textures", "item", "cristal_vinculo.png"))
for lang, chaves in [("pt_br", ["portal_frame", "portal_core", "cristal_vinculo", "principal"]),
                     ("en_us", ["portal_frame", "portal_core", "cristal_vinculo", "principal"])]:
    lj = jjson(A, "lang", f"{lang}.json")
    for c in chaves:
        chk(f"lang {lang} tem {c}", any(c in k for k in lj))
D = os.path.join(RES, "data", "loreportals")
for lt in ["portal_core", "portal_frame"]:
    chk(f"loot {lt}", tem(D, "loot_table", "blocks", f"{lt}.json"))
for rc, res_id in [("portal_frame", "loreportals:portal_frame"),
                   ("portal_core", "loreportals:portal_core"),
                   ("cristal_vinculo", "loreportals:cristal_vinculo")]:
    r = jjson(D, "recipe", f"{rc}.json")
    chk(f"receita {rc}", r.get("result", {}).get("id") == res_id)
chk("receita core usa olho de ender", "ender_eye" in ler(D, "recipe", "portal_core.json"))

# ---------------- higiene ----------------
todos_java = ""
for raiz, _, arqs in os.walk(JAVA):
    for a in arqs:
        if a.endswith(".java"):
            todos_java += ler(raiz, a)
chk("SEM telemetria", not re.search(r"(?i)new URL[(]|HttpClient|sendHttpRequest|analyticsApi|telemetryApi", todos_java))
chk("SEM HttpURLConnection", "HttpURLConnection" not in todos_java)
chk("mensagens com codigos de cor", todos_java.count("\\u00a7") > 20)
chk("sem box-drawing quebra-fonte", "\u2554" not in todos_java and "\u2550\u2550" not in todos_java)

# ---------------- compilado? ----------------
chk("classes compiladas existem", tem(RAIZ, "build", "classes", "dev", "loreportals", "LorePortalsMod.class"))
chk("jar existe", tem(RAIZ, "build", "libs", "portais-da-lore-1.3.2.jar"))

print(f"\ncheck.py do loreportals: {ok} ok, {falhas} falhas")
sys.exit(1 if falhas else 0)
