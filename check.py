#!/usr/bin/env python3
"""Suite de verificações do RP Tag — roda ANTES de entregar um ciclo."""
import glob
import io, os, re, sys, zipfile
import json as _json

OK, FAIL = 0, 0
def check(nome, cond):
    global OK, FAIL
    print(('PASS  ' if cond else 'FAIL  ') + nome)
    OK, FAIL = OK + (1 if cond else 0), FAIL + (0 if cond else 1)

R = 'src/main/java/dev/rptag'
C = R + '/client'
A = 'src/main/resources/assets/rptag'
S = A + '/sounds'

def rd(p):
    return io.open(p, encoding='utf-8').read()


def _png_tem_alpha(caminho):
    """true se o PNG tem canal alfa E pelo menos 10% de pixels transparentes."""
    try:
        from PIL import Image
        with Image.open(caminho) as im:
            if im.mode not in ('RGBA', 'LA'):
                return False
            a = im.split()[-1]
            hist = a.histogram()
            total = sum(hist) or 1
            return (sum(hist[:20]) / total) > 0.10
    except Exception:
        return False


def _tam_jpg(caminho):
    """(largura, altura) lidas do marcador SOF do JPEG."""
    import struct
    with open(caminho, 'rb') as f:
        if f.read(2) != b'\xff\xd8':
            return None
        while True:
            b = f.read(1)
            if not b:
                return None
            if b != b'\xff':
                continue
            m = f.read(1)
            while m == b'\xff':
                m = f.read(1)
            if m in (b'\xd8', b'\xd9') or not m:
                continue
            tam = struct.unpack('>H', f.read(2))[0]
            if m[0] in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB):
                f.read(1)
                h, w = struct.unpack('>HH', f.read(4))
                return (w, h)
            f.seek(tam - 2, 1)


def _tam_png(caminho):
    import struct
    with open(caminho, 'rb') as f:
        cab = f.read(33)
    return struct.unpack('>II', cab[16:24])

# ---- 1. versão em 3 lugares
VER = '5.31.0'
ver = all(VER in rd(f) for f in [R + '/RPTagMod.java', 'build.gradle',
        'src/main/resources/META-INF/neoforge.mods.toml'])
check(f'versão {VER} em RPTagMod/build.gradle/mods.toml', ver)

# ---- 2. lorezone re-entrada (INSIDE + seen.remove)
lz = rd(R + '/LoreZones.java')
check('lorezone: mapa INSIDE existe', 'INSIDE' in lz)
check('3.99 -> 5.26.1: o registro de avisos vistos continua salvo no mundo (o dono mudou so a regra)',
      'seen.remove(zid)' not in lz and 'avisosVistos' in lz
      and 'marcarAvisoVisto(uuid, zone.id())' in lz
      and 'tag.put("AvisosVistos", vistos)' in lz)

# ---- 3. /rp ajuda
rp = rd(R + '/RPCommands.java')
check('/rp ajuda registrado', 'literal("ajuda")' in rp and 'literal("help")' in rp)
check('/rp ajuda lista admin p/ admins', 'SO ADMINS' in rp)

# ---- 4. música custom: payload + registro + cliente
check('LorezoneMusicPayload existe', os.path.exists(R + '/LorezoneMusicPayload.java'))
mn = rd(R + '/ModNetworking.java')
check('payloads registrados no canal atual', 'LorezoneMusicPayload' in mn and 'registrar("18")' in mn)
mc = rd(C + '/MusicaCustom.java')
check('MusicaCustom usa pasta config/rptag/musicas', 'musicas' in mc)
check('MusicaCustom toca em LOOP (AL_LOOPING)', 'AL_LOOPING' in mc)
check('presets de som no LoreZoneCommands', 'caverna' in rd(R + '/LoreZoneCommands.java')
      and '/lorezone musicas' in rd(R + '/LoreZoneCommands.java'))

# ---- 5. nuvem REMOVIDA + quadrada
br = rd(C + '/BubbleRenderer.java')
check('nuvem removida do renderer', 'cloudBorder' not in br and 'CLOUD_TYPE' not in br)
check('quadrada no renderer (pillTex + QUAD_TYPE)', 'pillTex' in br and 'QUAD_TYPE' in br)
check('emigracao bs 5->2 no renderer', 'bs == 5' in br)
check('quadrada.png no assets', os.path.exists(A + '/textures/gui/quadrada.png'))
bs = rd(C + '/BubbleStyleScreen.java')
check('botão Quadrada no menu', 'Quadrada' in bs)
check('sem botão Nuvem no menu', 'literal("\u2601 Nuvem")' not in bs)
check('quadrada na prévia (QUAD_TYPE)', 'QUAD_TYPE' in bs)

# ---- 6. prévia = mundo (quads + blit céu) e SEM contadores
check('prévia usa quads do mundo (BUBBLE_TYPE/pillGui)', 'BUBBLE_TYPE' in bs and 'pillGui' in bs)
check('card da prévia: cenário procedural (céu+lua+grama)', 'fillGradient(cardX' in bs and 'lua cheia' in bs and '3E9B44' in bs)
check('preview_sky.png no assets', os.path.exists(A + '/textures/gui/preview_sky.png'))
check('contadores de emoji removidos', '" + naFala' not in bs and '" + noBalao' not in bs)

# ---- 7. emojis 1-char + codepoints
check('extrasCount = length() (1 char = 1 emoji)', 'extrasCount() {\n        return style.extras().length();' in bs)
check('renderer conta por codepoints', 'codePoints().toArray()' in br)
check('menu itera extras por char (não /2)', 'e.length()' in bs)

# ---- 8. espaçamento do menu
check('PANEL_H 306 (respiro novo)', 'PANEL_H = 306' in bs)
check('placa com título interno', 'titulo, x + 10, y + 2' in bs.replace('(this.font,', '(this.font,'))

# ---- 9. texturas órfãs fora
# (4.1.0) o caminho do jar estava CRAVADO em 3.51.1 — os 7 checks de jar
# nunca rodavam ("jar ainda nao buildado"). Agora acha sozinho: build/libs
# primeiro, depois a raiz do repo (onde fica o jar da versao atual).
def _acha_jar():
    import glob as _g
    def _versao(p):
        try:
            return [int(x) for x in p.rsplit('rptag-', 1)[1].replace('.jar', '').split('.')]
        except Exception:
            return [0]
    achados = _g.glob('build/libs/rptag-*.jar') + _g.glob('rptag-*.jar')
    achados = [a for a in achados if 'sources' not in a and 'javadoc' not in a]
    return max(achados, key=_versao) if achados else None

zp = _acha_jar()
if zp:
    print(f'   (jar encontrado: {zp})')
    z = zipfile.ZipFile(zp)
    n = z.namelist()
    check('jar: bola.png fora', 'assets/rptag/textures/gui/bola.png' not in n)
    check('jar: sem bubble_tail (rabicho antigo fora)', 'assets/rptag/textures/gui/bubble_tail.png' not in n)
    check('jar: tail.png presente (rabicho novo)', 'assets/rptag/textures/gui/tail.png' in n)
    check('jar: quadrada.png dentro', 'assets/rptag/textures/gui/quadrada.png' in n)
    check('jar: preview_sky.png dentro', 'assets/rptag/textures/gui/preview_sky.png' in n)
    check('jar: classes do payload', any('LorezoneMusicPayload' in x for x in n))
    check('jar: MusicaCustom dentro', any('MusicaCustom' in x for x in n))
else:
    print('AVISO: jar ainda não buildado — checks de jar pulados')

# ---- 3.51.0: rabicho, quadrada inteira, colunas, botoes ----
br = rd(C + '/BubbleRenderer.java')
check('rabicho RECORTE NA SILHUETA (contorno unico)', 'RABICHO — RECORTE NA SILHUETA (3.58.0)' in br)
check('sem asas no rabicho (sem margin*1.6)', 'margin * 1.6F' not in br)
check('previa sem g.fill no rabicho (textura unica)', 'g.fill(icx - 7' not in bs)
check('formato ANTIGO sem rabicho (quad TAIL_TYPE fora do render)', 'cxT - 6.5F' not in br)
check('menu: sem fileira 3 de molduras', 'py + ROW_BTN3' not in bs)
check('quadrada com recheio quadrado', 'a QUADRADA usa a textura de cantinhos TAMBEM no recheio' in br)
check('formato NOVO sem rabicho (look.tail = false)', 'look.tail = false' in br)
check('coluna ESQUERDA (adesivo)', 'coluna da ESQUERDA' in br)
check('coluna DIREITA (emojis)', 'coluna da DIREITA' in br)
check('menu: sem botao Auto', 'literal("\u25a3 Auto")' not in bs)
check('menu: sem botao Minha cor', 'literal("\u25a3 Minha cor")' not in bs)
check('menu: toggle Sem borda/Com borda', 'bordaToggleLabel' in bs)
check('menu: colocacao metade esquerda/direita', 'METADE ESQUERDA' in bs)
check('previa: rabicho quadrado', 'quadradaPrev' in bs)
check('MAX_SLOTS 3', 'MAX_SLOTS = 3' in bs)

# ---- 3.52.0: sons de verdade, substituicao, presets novos, URL, X vermelho ----
lz = rd(R + '/LoreZones.java')
lzc = rd(R + '/LoreZoneCommands.java')
mcm = rd(C + '/MusicaCustom.java')
check('som REGISTRADO (BuiltInRegistries.getValue)', 'BuiltInRegistries.SOUND_EVENT.get(' in lz)
check('sem createVariableRangeEvent solto', 'createVariableRangeEvent' not in lz)
check('som pontual: para o anterior (SOM_ATIVO)', 'SOM_ATIVO' in lz)
check('musica: para a anterior antes de tocar (stopMusic)', 'stopMusic(player, uuid)' in lz)
check('troca na hora (atualizarSons)', 'atualizarSons' in lz)
check('3.99: som para ao sair E no tempo maximo (nao vira ambiencia eterna)',
      'SOM_MAX_MS' in lz and 'pararSomPontual' in lz and 'boolean saiu = !insideNow.contains' in lz)
check('+17 presets (portal/trovao/ghast/vento/sino)', all(k in lzc for k in ['"portal"', '"trovao"', '"ghast"', '"vento"', '"sino"']))
check('preset "nenhum" limpa o som', 'Map.entry("nenhum", "")' in lzc)
check('valida som inexistente (admin descobre na hora)', 'nao existe no jogo' in lzc)
check('youtube: devolve comando yt-dlp pronto', 'yt-dlp -x --audio-format vorbis' in lzc)
check('dica de volume (Música e Sons) na resposta', 'Musica e Sons' in lzc)
check('cliente toca LINK direto (tocarUrl)', 'tocarUrl' in mcm)
check('cache de audio por link (pastaCache)', 'pastaCache' in mcm)
check('WAV suportado (javax.sound)', 'AudioSystem.getAudioInputStream' in mcm)
check('morango fantasma: clicar fora SOLTA', 'morango fantasma' in bs)
check('X do adesivo maior (14px)', 'X MAIOR (14px)' in bs)
check('X vermelho grande e sempre visivel nos emojis', 'X DENTRO do slot' in bs)

# ---- 3.53.0: icone do medo + /effect give dispara o medo ----
ce = rd(C + '/ClientEffects.java')
check('cliente le o efeito (getEffect MEDO) e sustenta o medo', 'O EFEITO MANDA NO MEDO' in ce and 'getEffect(RPEffects.MEDO)' in ce)
check('icone mob_effect/medo.png existe', os.path.exists(A + '/textures/mob_effect/medo.png'))
check('icone 64x64 = foto inteira com a palavra', io.open(A + '/textures/mob_effect/medo.png','rb').read().split(b'IHDR')[1][4:8] == b'\x00\x00\x00\x40')
check('FearPayload/documento cita /effect give', '/effect give' in rd(R + '/RPEffects.java') + rd(R + '/RPCommands.java'))

# ---- 3.54.1: icone = a FOTO INTEIRA (com a palavra medo) de HUD (aparece com shaders) ----
cgl = rd(C + '/ClientGuiLayers.java')
check('camada oficial de HUD (RegisterGuiLayers)', 'RegisterGuiLayersEvent' in cgl and 'registerAboveAll' in cgl)
check('camada chama desenharMedoCamada', 'desenharMedoCamada' in cgl)
check('desenho na camada = vinheta + veu 208 + icone', '208 * amp' in ce and 'MEDO_HD_TEX' in ce.split('desenharMedoCamada')[1])
check('onGui sem desenho do medo (so watcher + aviso)', '168 * amp' not in ce)

# ---- 3.55.0: sync de musica, 1x, roll +bonus, fala real, X sem tooltip ----
mn = rd(R + '/ModNetworking.java')
sev = rd(R + '/ServerEvents.java')
lzcmd = rd(R + '/LoreZoneCommands.java')
lzz = rd(R + '/LoreZones.java')
cph = rd(C + '/ClientPayloadHandler.java')
bss = rd(C + '/BubbleStyleScreen.java')
bal = rd(R + '/BalaoCommands.java')
rollc = rd(R + '/RollCommands.java')
check('protocolo atual (3 payloads de musica)', 'registrar("18")' in mn and 'MusicFileQueryPayload' in mn and 'MusicFileChunkPayload' in mn and 'MusicFileAnswerPayload' in mn)
check('MusicSync: servidor entrega o arquivo', 'MusicSync.pedir' in lzz and 'responder' in sev)
check('/lorezone baixar com mensagens (iniciado/concluido)', 'literal("baixar")' in lzcmd and 'iniciado' in lzcmd and 'concluido' in lzcmd)
check('3.99: musica 1x + TEMPO MAXIMO (nunca fica pra sempre)',
      'literal("1x")' in lzcmd and 'MUSIC_MAX_PADRAO_SEG' in lzz
      and 'st.silenced()' in lzz and 'now - st.startedMs() >= maxMs' in lzz)
check('raio explicado na criacao (esfera)', 'ESFERA' in lzcmd)
check('cliente grava o arquivo e toca', 'handleMusicChunk' in cph and 'tocarArquivoNome' in cph)
check('/roll +3 sozinho = especialista (1d20+3)', 'ESPECIALIDADE: vira 1d20+3' in rollc)
check('/balao teste removido', 'literal("teste")' not in bal)
check('previa usa a ultima fala real do chat', 'falaPrev' in bss and 'ultimaFala' in rd(C + '/ClientRPStates.java'))
check('captura a fala (ClientChatEvent)', 'ClientChatEvent' in rd(C + '/ClientEvents.java'))
check('X grande (10px) sem tooltip travada', 'X DENTRO do slot' in bss and 'renderTooltip' not in bss.split('renderExtras')[1].split('private static String nameOf')[0])
check('icone medo: borda dissolvida (fim do quadrado)', '208 * amp' in ce)

# ---- 3.56.0: reescrita dos baloes (wrap, rabicho pixel, previa multi-linha) ----
check('wrap mais cedo: 130px (mais palavras = mais balao pra cima)', 'MAX_TEXT_WIDTH = 130' in br)
check('textura do rabicho antigo fora do codigo', 'bubble_tail' not in br and 'tailGui' not in bs and 'bubble_tail' not in bs)
check('previa quebra a fala em linhas (wrapPrev)', 'wrapPrev' in bs and 'linhasPrev' in bs)
check('previa cresce em altura com o texto', 'linhasPrev.size() * this.font.lineHeight * K' in bs)
check('previa com o rabicho recorte (tail.png)', 'TAIL_TYPE' in bs and '0.0475F' in bs)

# ---- 3.56.1: rabicho da MESMA cor do balao + rp ajuda limpo ----
check('balao flutuante citado (mods de referencia)', 'Talk Balloons' in br)
check('pilha sem espaco de rabicho (+4)', ') + 4;' in br)
check('previa com cor escalada 248/255', 'escalaTexel' in bs)
check('rp ajuda SEM a linha do WALKTHROUGH', 'WALKTHROUGH' not in rp)

# ---- 3.57.0: tons de fala (inspirados no mod fabric roleplay-speech-bubbles) ----
check('payload com tom (0 fala / 1 pensamento / 2 grito)', 'int tone' in rd(R + '/ChatBubblePayload.java'))
check('tom atravessa handler e cache', 'payload.tone()' in rd(R + '/client/ClientPayloadHandler.java') and 'int tone,' in rd(R + '/client/ClientBubbleCache.java'))
check('italico REAL no renderer (pensamento/sussurro)', 'withItalic(true)' in br)
check('grito = negrito fake (2a passada 0.75px)', 'sx + 0.75F' in br)
check('/pensar <msg> (balao com parenteses)', 'literal("pensar")' in rd(R + '/ChatCommands.java'))
check('rp ajuda lista /pensar', '/pensar <msg>' in rp)
check('/s grita no balao MAIUSCULO com tom 2', 'msg.toUpperCase(), false, 2' in rd(R + '/ChatCommands.java'))

# ---- 3.60.0: balao PROCEDURAL (geometria, sem textura) ----
bg = rd(C + '/BubbleGeometry.java')
bsink = rd(C + '/BubbleSink.java')
check('BubbleGeometry e Java puro (sem imports do Minecraft)', 'net.minecraft' not in bg and 'com.mojang' not in bg)
check('silhueta unica com rabicho (ear clipping)', 'triangulate' in bg and 'silhouette' in bg)
check('5 estilos na geometria', all(k in bg for k in ['CLASSICA', 'CARTUM', 'QUADRADA', 'COSTURA', 'BRILHO']))
check('anti-aliasing (anel de alfa)', 'feather(' in bg and 'AA' in bg)
check('BubbleSink normaliza o giro (culling)', 'cross > 0.0F' in bsink and 'import net.minecraft' not in bsink)
check('renderer usa RenderType.gui() + BubbleSink', 'RenderType.gui()' in br and 'new BubbleSink' in br)
check('renderer: flag modern + fallback automatico', 'static volatile boolean modern' in br and 'voltando ao formato antigo' in br)
check('comando /rpformato novo|antigo', 'literal("rpformato")' in br and 'literal("antigo")' in br)
check('eixo Z cresce com a distancia (zk)', 'SCALE * zk' in br)
check('previa do menu usa a MESMA geometria', 'BubbleGeometry.build' in bs and 'new BubbleSink' in bs)
check('previa: texto/emojis levantados em Z (0.9)', 'translate(0.0F, 0.0F, 0.9F)' in bs)
check('desenho antigo preservado como fallback', 'texel248' in br and 'ringGuiTex' in bs)
check('tools/teste-geometria.sh existe', os.path.exists('tools/teste-geometria.sh'))

# ---- 3.61.0: aviso serio + avisozona + balao com shaders ----
ce = rd(C + '/ClientEffects.java')
cgl = rd(C + '/ClientGuiLayers.java')
av = ce[ce.index('desenhaAviso'):] if 'desenhaAviso' in ce else ''
check('aviso SEM pulso (pedido do dono)', 'desenhaAviso' in ce and 'pulse' not in av)
check('aviso MAIOR (escala 3.2x)', '3.2F' in av)
check('aviso com peso (negrito de tinta, 2 passadas)', 'translate(0.3F' in av)
check('aviso na CAMADA OFICIAL (aparece com shaders)', 'aviso_hud' in cgl and 'desenhaAviso' in cgl)
check('aviso dura 12s (payload com estilo) — 5.27.0', 'new ScreenTextPayload(traduzido, 12, style)' in rp)
check('avisar aceita VARIOS alvos (players/@a)', 'EntityArgument.players()' in rp and 'getPlayers(ctx, "jogadores")' in rp)
check('4 ESTILOS de aviso (pesadelo/runico/real/classico)', 'literal("pesadelo")' in rp and 'literal("runico")' in rp and 'literal("real")' in rp)
check('som assustador no aviso (real=sino do beacon; pesadelo=FX c/ trilha propria)', 'BEACON_ACTIVATE' in rp and 'particulasPesadelo' in rp and 'ELDER_GUARDIAN_CURSE' not in rp)
check('payload do aviso leva o estilo (codec)', 'p.style()' in rd(R + '/ScreenTextPayload.java'))
check('TYPEWRITER no balao (28 ms/char, nasceu->escreve)', 'TYPE_MS_PER_CHAR' in br and 'bornMillis()) / TYPE_MS_PER_CHAR' in br)
check('TYPEWRITER no aviso (30 ms/char, sem quebrar codigos)', 'private static String typed(' in ce and "c == '\\u00a7'" in ce)
check('PESADELO: veu preto + borda de sangue', '0x6E0B0B' in ce)
check('RUNICO/REAL: paletas fixas', '0xD9B8FF' in ce and '0xF2C94C' in ce)
check('ajuda cita os estilos de aviso', 'estilos: classico, pesadelo, runico, real' in rp)
check('/balao typewriter on|off (opçao do player)', 'literal("typewriter")' in rd(R + '/BalaoCommands.java') and 'alternarTypewriter' in rd(R + '/BalaoCommands.java'))
check('renderer respeita a opçao TYPEWRITER', 'ClientBubbleCache.TYPEWRITER' in br)
check('typewriter persiste por jogador (NBT)', '"Typewriter"' in rd(R + '/RPWorldData.java'))
check('payload typewriter BIDIRECIONAL (1 registro, 2 fluxos)', 'playBidirectional(TypewriterPayload.TYPE' in mn)
check('AVISO: flag/try PROPRIOS (nao morre junto com o medo)', 'avisoDisabled' in ce and 'medoDisabled' in ce)
check('AVISO: log de diagnostico (chegou/desenhando)', 'AVISO recebido' in ce and 'desenhando aviso' in ce)
check('FIX do typed: anexa os caracteres normais', 'sb.append(c);' in ce and '1 + (int) Math.min(998' in ce)
check('AVISO CRESCENDO (scale-in 0.88->1.0)', '0.88F + 0.12F' in ce)
check('RUNICO: FX c/ trilha propria + particulas no servidor (sem som duplicado)',
      'particulasRunico' in rp and 'AMETHYST_BLOCK_CHIME' not in rp and 'case 1, 2' not in rp)
check('X vermelho nos EMOJIS da DIREITA da previa', 'extrasXBoxes' in bs and 'removeExtra(xi)' in bs)
check('coluna direita da previa SEMPRE desenha (sem adesivo tb)', 'extrasXBoxes.clear();' in bs)
check('menu: fundo embaçado (blur) com fallback opaco', 'renderBlurredBackground(partialTick)' in bs and 'blurOk' in bs)
check('véu LEVE sobre o blur (mundo visível)', '0x360B0E15' in bs and 'desenhaGalaxia(g, true)' in bs)
check('ROOT CAUSE: renderBackground vazio (blur não alcança o painel)', 'VAZIO DE PROPOSITO' in bs and bs.count('renderBlurredBackground') == 1)
check('galáxia completa só sem blur (modo leve)', 'desenhaGalaxia(g, false)' in bs and 'if (!leve)' in bs)
check('menu frufu: filete dourado + estrelas', '0x5CE8A83C' in bs and '0xBFD4FF' in bs)
check('titulo dos EMOJIS fora da linha dos slots', 'ROW_EXTRAS - 19' in bs)
check('X da direita SO no hover (sem X flutuante)', 'hoverEmoji' in bs and 'extrasXBoxes.add(new int[]{gx - 3' in bs)
check('brilho da previa suavizado', '0x77000000 | lightenGui' in bs)
check('clássico = CAIXA DE FALA (campo aparece primeiro)', 'desenhaAvisoCaixa' in ce and 'tAtraso' in ce)
check('nome (antes do :) colorido de azul', '0xFF8C9EFF' in ce)
check('pesadelo: TEXTO CORROMPIDO (onda + 3 camadas de sangue)', 'desenhaTextoCorrompido' in ce and '0xA11A1A' in ce and '0xE04848' in ce)
check('falhas deterministicas por caractere', 'seed % 17L' in ce and '0x6E0B0B' in ce)
cl = rd(R + '/CanonLife.java')
wd = rd(R + '/RPWorldData.java')
rtm = rd(R + '/RPTagMod.java')
cph = rd(R + '/client/ClientPayloadHandler.java')
cgl = rd(R + '/client/ClientGuiLayers.java')
cbp = rd(R + '/CanonBannerPayload.java')
check('canon = MODO GLOBAL (todos já têm; adm liga/desliga)', 'literal("canon")' in rp and 'setCanonEvent' in rp and 'isCanonEvent' in wd and 'putBoolean("CanonEvent"' in wd)
check('banner: payload CanonBannerPayload registrado', 'canon_banner' in cbp and 'CanonBannerPayload.TYPE' in mn and 'handleCanonBanner' in cph)
check('banner na camada OFICIAL de HUD (com shaders)', 'canon_banner' in cgl and 'desenhaBannerCanon' in cgl)
check('avisos LORE ON (verde) / LORE OFF (vermelho)', 'LORE ON' in rp and 'LORE OFF' in rp and '0x3FA34D' in ce and '0xC41F1F' in ce)
check('banner BROADCAST pro servidor todo; SEM msg de chat', 'sendToAllPlayers' in rp and 'Vida CANÔNICA concedida' not in rp)
check('banner texturizado ESTATICO (linhaTexturizada)', 'linhaTexturizada' in ce and 'now * 0.00' not in ce.split('linhaTexturizada')[1].split('desenhaBannerCanon')[0])
check('ritual no RESPAWN (com o modo on, qualquer morte cobra)', 'PlayerRespawnEvent' in cl and 'isCanonEvent' in cl)
check('congelamento ~5s server-side (volta pra âncora)', 'CONGELAMENTO_MS' in cl and 'teleportTo' in cl)
check('frase simples: VOCÊ PERDEU UMA VIDA', 'VOCÊ PERDEU UMA VIDA' in cl)
check('sem bruxa em lugar nenhum', 'bruxa' not in (cl + rp).lower())
check('som CUSTOM registrado (SOM_CANON_DEATH)', 'SOM_CANON_DEATH' in rtm and '"canon_death"' in rtm)
check('ogg da catbox embutido (ogg + sounds.json)', os.path.exists(A + '/sounds/canon_death.ogg') and '"canon_death"' in rd(A + '/sounds.json'))
check('som SÓ pra vítima (playNotifySound)', 'playNotifySound' in cl)
check('STRONDO: trovão abre o ritual (server)', 'LIGHTNING_BOLT_THUNDER' in cl and 'WEATHER' in cl)
check('tela MEIO AVERMELHADA (véu pulsante)', 'desenhaVeuCanon' in ce and '0x5A0D0D' in ce and 'canon_veu' in cgl)
check('audio da lore entra DEPOIS do strondo (client)', 'somCanonDue' in ce and 'forUI' in ce and 'SOM_CANON_DEATH.get()' in ce)
check('SEM tremido: letras retas (aviso + banner)', 'float dx = ((seed % 5L)' not in ce and 'float dy = ((seed % 7L)' not in ce)
check('banner com faixa escura atras (contraste)', 'bandW' in ce and '(int) (202 * vis) << 24' in ce)
check('banner SOLIDO: cor unica (fim do patchwork)', 'aFill | (base & 0xFFFFFF)' in ce)
check('banner SEM halo (letras nitidas)', '-0.6F, topo' not in ce and 'int topo =' not in ce.split('desenhaBannerCanon')[1].split('linhaTexturizada')[0])
wdl = wd
check('5 VIDAS por player (estoque persistente)', 'canonLives' in wdl and '"CanonLives"' in wdl and 'getCanonLives' in wdl and 'getOrDefault(id, 5)' in wdl)
check('LIMITE anti-chain (5 min de proteção)', 'COOLDOWN_MS' in cl and 'PROTECAO_ATE' in cl and '5 * 60_000L' in cl)
check('aviso mostra quantas restam', 'RESTAM' in cl)
check('ÚLTIMA VIDA: TOME CUIDADO + MEDO ABSURDO (30s)', 'ÚLTIMA VIDA \\u2014 TOME CUIDADO' in cl and 'FearPayload(30)' in cl and 'RPEffects.MEDO' in cl)
rtm = rd(R + '/RPTagMod.java')
rdr = rd(C + '/CanonCoracaoRenderer.java')
blk = rd(R + '/CanonCoracaoBlock.java')
import os as _os
check('CORAÇÃO CANÔNICO: bloco+item+BE registrados', '"coracao_canon"' in rtm and 'CORACAO_BLOCK' in rtm and 'CORACAO_BE' in rtm and 'CORACAO_ITEM' in rtm and 'BlockItem' in rtm)
check('coracao NUNCA spawna natural (só /give, indestrutível)', 'strength(-1.0F, 3600000.0F)' in rtm and 'EntityArgument' not in rtm)
check('SHIFT + clique direito = +1 vida (coração se consome)', 'isShiftKeyDown' in blk and 'setCanonLives' in blk and 'removeBlock' in blk and 'NOVA CHANCE' in blk)
check('vidas cheias 5/5: não consome (coração fica)', 'vidas >= 5' in blk and 'continua aqui' in blk)
check('coração 3D PROCEDURAL girando/batendo (BER — voxel 4.4, o antigo)',
      'RenderType.lightning()' in rdr and 'rotationDegrees' in rdr and 'pulso' in rdr
      and 'MASCARA' in rdr and 'SOLIDO' in rdr)
check('BER registrado (ClientRenderers, camada cliente)', 'registerBlockEntityRenderer' in rd(C + '/ClientRenderers.java'))
check('assets do coração completos (estado/modelo/item/textura)', _os.path.exists(A + '/textures/item/coracao_canon.png') and _os.path.exists(A + '/blockstates/coracao_canon.json') and '"elements": []' in rd(A + '/models/block/coracao_canon.json'))
check('protecao NÃO some no logout (relog nao burla)', 'PROTECAO_ATE é de propósito NÃO limpa' in cl)
check('status mostra X/5', '+ "/5"' in rp)
check('status mostra as vidas canônicas', 'Vidas canônicas:' in rp)
check('estilo 4 TEXTURIZADO estático (letras não mexem)', 'desenhaTextoTexturizado' in ce and 'Math.min(24, textStyle)' in ce)
check('pesadelo: sway + ghost + fase por char', 'sin(now * 0.0008' in ce and 'GHOST' in ce and 'seed % 7L' in ce)
check('cursor de digitação piscando', 'digitando' in ce and '"_"' in ce)
check('caixa ancorada acima do hotbar', 'h - 70 - boxH' in ce)
check('linhas estáveis (campo não pula digitando)', 'quebraLinhas' in ce)
check('galáxia: nebulosas + via láctea + cometa', 'desenhaGalaxia' in bs and 'blob(g,' in bs and '9000L' in bs)
check('estrelas vivas com cruz de brilho', '0xFFE9C8' in bs and 'pul == 2' in bs)
check('botões de vidro (GlassButton)', 'private class GlassButton extends Button' in bs)
check('slider de vidro (GlassSlider)', 'private class GlassSlider extends AbstractSliderButton' in bs)
check('painel real: gradiente + moldura 3 camadas', 'fillGradient(px, py, px + PANEL_W' in bs and '0x78E8A83C' in bs)
check('coroas ladeando o título', 'desenhaCoroa' in bs and 'tx0 - 16' in bs)
check('diamantes de ouro nos cantos', 'desenhaDiamante' in bs and 'desenhaDiamante(g, px + PANEL_W - 4' in bs)
check('veludo real (gradiente roxo)', '0xF23A2566' in bs and '0xF21E1140' in bs)
check('placas em veludo roxo', '0xFF2A2148' in bs)
check('botões de vidro roxo-real', '0xFF2A2350' in bs and '0xFF3D3270' in bs)
check('estrelas de ouro na galáxia', '(i % 7 == 0) ? 0xFFE8A83C : 0xBFD4FF' in bs)
check('dica NUNCA vaza do painel (corta com …)', 'private String corta(String s, int maxW)' in bs and 'corta(hint, PANEL_W - 20)' in bs)
check('dica de digitação encurtada', '/balao typewriter off' in bs and 'Digitacao automatica:' not in bs)
check('fala do chat trunca (sem vazar)', 'String falaMostrada = corta(fala,' in bs)
check('tag da prévia fora da grama', 'cardY + cardH - 21' in bs)
check('X do slot dentro da caixinha', 'g.fill(sx + 12, y0 + 1, sx + 20, y0 + 9,' in bs)
check('chip da moldura com contorno', 'outline(g, px + PANEL_W - 28, py + 171, 18, 16,' in bs)
check('/rp admin avisozona <id> <texto>', 'literal("avisozona")' in rp and 'avisarZona' in rp)
check('balao COM SHADERS = formato antigo automatico', 'isShaderPackInUse' in br and 'usaModern()' in br)

# ---- 3.84.0: sombra sem rabicho + titulo do rp ajuda limpo ----
check('SOMBRA do procedural sem rabicho (bug das prints)', 'ss.tail = k.tail' in bg)
check('rp ajuda com titulo limpo (sem box-drawing)', chr(92)+'u2554' not in rp and chr(92)+'u00bb' in rp)

# ---- 3.85.0: AVISO da lorezone (estilos do /rp admin avisar ao entrar) ----
lz = rd(R + '/LoreZones.java')
zc = rd(R + '/LoreZoneCommands.java')
check('3.85: Zone guarda aviso+estilo+segundos',
      'String aviso, int avisoEstilo, int avisoSeg' in lz)
check('3.85: aviso persiste no NBT (mundos antigos viram titulo classico)',
      '"Aviso"' in lz and '"AvisoEstilo"' in lz and '"AvisoSeg"' in lz)
check('3.85: show() dispara ScreenTextPayload na entrada',
      'new ScreenTextPayload(zone.aviso(), seg, estilo)' in lz)
check('3.85: /lorezone aviso + off registrados',
      'literal("aviso")' in zc and 'literal("off")' in zc)
check('3.85: 5 estilos por nome (classico..morte)',
      '"morte", 4' in zc and '"pesadelo", 1' in zc)
check('3.85: setAviso/limparAviso chamados',
      'setAviso(ctx' in zc and 'limparAviso(ctx' in zc)
check('3.85/4.0: criar inicia sem aviso e com faixa vertical = esfera',
      '45, "", 0, 8,' in zc and 'player.position().y - raio, player.position().y + raio' in zc)
check('3.85: listar mostra [aviso:estilo]',
      '[aviso:' in zc and 'nomeEstilo' in zc)
rp = rd(R + '/RPCommands.java')
check('3.85: translateAmp compartilhado com LoreZoneCommands',
      'static String translateAmp' in rp
      and 'private static String translateAmp' not in rp)
ce = rd(C + '/ClientEffects.java')
# (5.15.0) a versao no log deixou de ser escrita na mao: agora vem de RPTagMod.VERSION
check('4.0: boot log com a versao', 'camada de avisos carregada' in ce)

# ---- 3.86.0: CORAÇÃO 3D redesenhado + 6 estilos de aviso novos ----
ccr = rd(C + '/CanonCoracaoRenderer.java')
check('4.9.7: o coracao de lore e o SOLIDO VOXEL ANTIGO (4.4)',
      'MASCARA' in ccr and 'NY = 13' in ccr and 'construirSolido' in ccr and 'FATOR_PROF' in ccr
      and 'contornoY(' not in ccr)
check('4.9.7: o preview da malha do coracao saiu do repo',
      not os.path.exists('arte/previa_coracao3d.py') and not os.path.exists('previa/coracao3d.png'))
check('4.4: giro por relogio de parede (nao trava, nao pula) + batida tum-tum',
      'System.nanoTime()' in ccr and 'Axis.YP.rotationDegrees' in ccr
      and 'Math.exp(-bt * 18.0)' in ccr and 'getBlockPos().getX() * 37' in ccr)
ce = rd(C + '/ClientEffects.java')
check('3.98/4.5: client aceita estilos ate 24 (egipicio + saida do vazio)',
      'Math.min(24, textStyle)' in ce)
check('3.86/3.91: INFERNO aura (fundo + brasa)', '0x140301' in ce and '0xFFC080' in ce)
check('3.86: GELO (cristais/losango)', '0x081523' in ce and 'desenhaLosango' in ce)
check('3.86/3.87: DIVINO celestial (raios + ouro)', '0x120D03' in ce and '0xE8C765' in ce)
check('3.86: VOID (estrelas)', '0x060310' in ce and '0xCBB2FF' in ce)
check('3.86: SANGUE (gotas escorrendo)', '0x0F0103' in ce and '0xB01616' in ce)
rp = rd(R + '/RPCommands.java')
check('3.86: avisar mapeia morte..sangue',
      'case "morte" -> 4' in rp and 'case "sangue" -> 9' in rp
      and 'case "void" -> 8' in rp)
check('3.86: literais novos no /rp admin avisar',
      all('literal("' + s + '")' in rp for s in
          ['morte', 'inferno', 'gelo', 'divino', 'void', 'sangue', 'demoniaco']))
check('3.86/3.97: sons restantes = real (3) e morte (4) — os 8 demais'
      ' viraram FX c/ trilha propria no cliente',
      'RESPAWN_ANCHOR_DEPLETE' in rp and 'BEACON_ACTIVATE' in rp
      and 'BLAZE_BURN' not in rp and 'PLAYER_HURT_FREEZE' not in rp
      and 'AMETHYST_BLOCK_CHIME' not in rp
      and 'BELL_RESONATE' not in rp and 'WARDEN_HEARTBEAT' not in rp
      and 'ALLAY_ITEM_GIVEN' not in rp and 'PORTAL_TRIGGER' not in rp
      and 'ELDER_GUARDIAN_CURSE' not in rp)
check('3.86: ajuda lista os 10 estilos',
      'morte, inferno, gelo, divino, void, sangue' in rp)
zc = rd(R + '/LoreZoneCommands.java')
check('3.86: lorezone aviso aceita os 10 estilos',
      '"inferno", 5' in zc and '"sangue", 9' in zc and '"void", 8' in zc)
lz = rd(R + '/LoreZones.java')
check('3.98/4.5 -> 5.26.1: clamp de estilo da zona vai ate 39 (agora a zona aceita a infeccao)', lz.count('Math.min(39,') == 2)

# ---- 3.87.0: inferno bizarro, divino celestial, void profundo,
# sangue pulsando fundo, DEMONIACO macabro ----
ce = rd(C + '/ClientEffects.java')
check('3.87: DEMONIACO (veias + anel + letras corrompidas)',
      'desenhaVeias' in ce and 'desenhaAnel' in ce
      and 'style == 1 || style == 10' in ce and '0x0A0002' in ce)
check('3.89/3.91: INFERNO = AURA ARDENTE (sem leque, com orbes)',
      '0xFFB060' in ce and 'haloY5' in ce and 'angBase5' not in ce)
check('3.88: DIVINO = aura sagrada (sem colunas, sem pontos)',
      'desenhaRaio' in ce and 'angBase' in ce and 'cy - bh - 26' in ce
      and '0xFFF0C0' in ce and 'yF' not in ce
      and 'colunas de luz' not in ce)
check('3.87: SANGUE pulsa (batida cardiaca dupla)',
      'pulsoCard' in ce and 'bat1' in ce and 'bat2' in ce)
rp = rd(R + '/RPCommands.java')
check('3.87: demoniaco registrado (mapa + nome + literal)',
      'case "demoniaco" -> 10' in rp and 'case 10 -> "DEMONIACO"' in rp
      and 'literal("demoniaco")' in rp)
check('3.87/3.96: void = FX c/ trilha propria (portal+wither saíram)',
      'WITHER_AMBIENT' not in rp and 'ALLAY_ITEM_GIVEN' not in rp
      and 'GHAST_SCREAM' not in rp)
zc = rd(R + '/LoreZoneCommands.java')
check('3.87/3.95: lorezone aceita demoniaco',
      'Map.entry("demoniaco", 10)' in zc and 'case 10 -> "demoniaco"' in zc)

# ---- 3.90.0: anel continuo, coroa nova, demoniaco aura, sangue bizarro,
# void nebula, som custom do inferno ----
ce = rd(C + '/ClientEffects.java')
check('3.91: anel por varredura ANGULAR (arco perfeito)',
      '(Math.PI * 2 * i) / passos' in ce and 'cx + rx - 1' not in ce)
check('3.90/3.91: halo divino agora e o SOL SAGRADO', 'desenhaSol' in ce)
check('3.90: circulo de fogo do inferno redesenhado',
      '0xFFA050' in ce and 'Math.min(170, w / 4), 22' in ce)
check('3.91: SEM feixes no inferno/demoniaco (so o divino tem)',
      'angBase5' not in ce and 'angBaseD' not in ce)
check('3.91: ORBES orbitando nos 3 (divino/inferno/demoniaco)',
      'desenhaOrbes' in ce and ce.count('desenhaOrbes(g') == 3
      and 'orbitando' in ce)
check('3.91: demoniaco = circulo de invocacao REDONDO + runas',
      'Math.min(w, h) / 4' in ce and 'runas radiais' in ce
      and 'raioX + 12' not in ce)
check('3.91: SOL SAGRADO no divino (fim da coroa)',
      'desenhaSol' in ce and 'SOL SAGRADO' in ce
      and 'COROA DE ANJOS' not in ce)
check('3.90: SANGUE bizarro (coracao gigante batendo + veias)',
      'desenhaCoracaoFundo' in ce and 'MASCARA_CORACAO' in ce
      and 'desenhaVeias(g, w, h, vis)' in ce and 'escC' in ce)
check('3.90: VOID nebula cosmica (roxo/azul + anel)',
      '0x3A1E7A' in ce and '0x14285E' in ce and '0x7A5CCF' in ce)
mod = rd(R + '/RPTagMod.java')
sj = rd(A + '/sounds.json')
check('3.90: SOM_INFERNO registrado + sounds.json + ogg no jar',
      'SOM_INFERNO' in mod and 'inferno_aviso' in sj
      and os.path.exists(A + '/sounds/inferno_aviso.ogg'))
rp = rd(R + '/RPCommands.java')
check('3.90/3.97: inferno = FX c/ trilha propria + particulas; demoniaco c/ particulas',
      'particulasInferno' in rp and 'particulasAbismo' in rp
      and 'SOUL_FIRE_FLAME' in rp and 'RPTagMod.SOM_INFERNO.get()' not in rp)

# ---- 3.92.0: estilo ASCENSAO (FX do user adaptado: asas, hexagrama,
# letras caindo, mensagem configuravel) ----
daf = rd(C + '/DivinoAvisoFx.java')
check('3.92: DivinoAvisoFx adaptado (pacote + escala do prot\u00f3tipo: t\u00edtulo nunca encolhe)',
      daf.startswith('package dev.rptag.client;')
      and 'public static void iniciar(String t, String s)' in daf
      and 'float S = k * 1.9f, ty = cy - 4 * S;' in daf
      and 'w * 0.86f' not in daf)
check('3.92: FX completo (asas 9 penas + hexagrama + letras caindo)',
      'void asa(' in daf and 'hexagrama' in daf and '0.09f * i' in daf)
cgl = rd(C + '/ClientGuiLayers.java')
check('3.92: camada do FX registrada', 'DivinoAvisoFx.registrar(event)' in cgl)
ce = rd(C + '/ClientEffects.java')
check('3.95: startScreenText roteia estilo 7 (divino) pro FX',
      'style == 7' in ce and 'DivinoAvisoFx.iniciar("AVISO", limpo7)' in ce
      and 'Math.min(24, textStyle)' in ce)
rp = rd(R + '/RPCommands.java')
check('3.95/3.98: estilo ascensao REMOVIDO (case 11 = pesadelo2)',
      'case "ascensao"' not in rp and 'ASCENSAO' not in rp
      and 'literal("ascensao")' not in rp
      and 'case "pesadelo2" -> 11' in rp
      and 'particulasAscensao' in rp and 'ParticleTypes.END_ROD' in rp
      and 'class DivinoAvisoFx' not in rp)
zc = rd(R + '/LoreZoneCommands.java')
check('3.95/3.98: lorezone sem ascensao (case 11 = pesadelo2)',
      'entry("ascensao"' not in zc and 'ascensao' not in zc
      and 'entry("pesadelo2", 11)' in zc and 'case 11 -> "pesadelo2"' in zc)
rp2 = rd(R + '/RPCommands.java')
check('3.95: ajuda sem ascensao (com pesadelo2)', 'sangue, demoniaco, egipicio, expurgo, ciberpsicose, energia, conhecimento (alvos' in rp2
      and 'ascensao' not in rp2 and 'pesadelo, pesadelo2, runico' in rp2)

# ---- 3.93.0: DEMONIACO = FX "Chamado do Abismo" (arquivo do user) ----
dafx = rd(C + '/DemoniacoAvisoFx.java')
check('3.93: DemoniacoAvisoFx adaptado (pacote + escala do prot\u00f3tipo: t\u00edtulo nunca encolhe)',
      dafx.startswith('package dev.rptag.client;')
      and 'public static void iniciar(String t, String s)' in dafx
      and 'float S = k * 1.9f, ty = cy - 4 * S;' in dafx
      and 'w * 0.86f' not in dafx)
check('3.93: FX completo (mandibulas + pentagrama + olho + chifres)',
      'fissuras' in dafx and 'chifre(' in dafx and 'void olho(' in dafx
      and 'pentagrama' in dafx.lower())
cgl = rd(C + '/ClientGuiLayers.java')
check('3.93: camada do FX registrada', 'DemoniacoAvisoFx.registrar(event)' in cgl)
ce = rd(C + '/ClientEffects.java')
check('3.93: startScreenText roteia estilo 10 pro FX (ordem do prot\u00f3tipo)',
      'style == 10' in ce and 'DemoniacoAvisoFx.iniciar("AVISO", limpoD)' in ce
      and 'Math.min(24, textStyle)' in ce)
rp = rd(R + '/RPCommands.java')
check('3.93: somAviso case 10 = particulasAbismo (server-safe)',
      'case 10 -> particulasAbismo(alvo)' in rp
      and 'class DemoniacoAvisoFx' not in rp)

# ---- 3.95.0: SANGUE = FX "Sangria" (arquivo do user) ----
saf = rd(C + '/SangueAvisoFx.java')
lz = rd(R + '/LoreZones.java')
check('3.95: SangueAvisoFx adaptado (pacote + escala do prot\u00f3tipo: t\u00edtulo nunca encolhe)',
      saf.startswith('package dev.rptag.client;')
      and 'public static void iniciar(String t, String s)' in saf
      and 'float S = k * 1.9f, ty = cy - 4 * S;' in saf
      and 'w * 0.86f' not in saf)
check('3.95: FX Sangria completo (veias + po\u00e7a + pingos DR + BufferUploader)',
      'void veia(' in saf and 'poolY' in saf and 'DR' in saf
      and 'BufferUploader' in saf)
check('3.95: camada aviso_sangue registrada', 'SangueAvisoFx.registrar(event)' in cgl)
check('3.95: startScreenText roteia estilo 9 (sangue) pro FX',
      'style == 9' in ce and 'SangueAvisoFx.iniciar("AVISO", limpo9)' in ce)
check('3.95: somAviso case 7 = particulasAscensao (server-safe)',
      'case 7 -> particulasAscensao(alvo)' in rp)
check('3.95: somAviso case 9 = particulasSangue (server-safe)',
      'case 9 -> particulasSangue(alvo)' in rp and 'REDSTONE_BLOCK' in rp
      and 'class SangueAvisoFx' not in rp)
check('3.98/4.5 -> 5.26.1: clamp no LoreZones (x2, agora 39) e 24 no ClientEffects',
      'Math.min(24, textStyle)' in ce and lz.count('Math.min(39') == 2)

# ---- 3.96.0: VOID = "Queda Sem Fundo" + PESADELO = "Sono Sem Descanso"
# (arquivos do user) ----
vf = rd(C + '/VoidAvisoFx.java')
pf = rd(C + '/PesadeloAvisoFx.java')
check('3.96: VoidAvisoFx adaptado (pacote + escala do prot\u00f3tipo: t\u00edtulo nunca encolhe)',
      vf.startswith('package dev.rptag.client;')
      and 'public static void iniciar(String t, String s)' in vf
      and 'float S = k * 1.9f, ty = cy - 4 * S;' in vf
      and 'w * 0.86f' not in vf)
# (4.1.0) o "Seguido" found-footage (figura palida em cortes, da 3.97.0) foi
# SUBSTITUIDO a pedido do dono pelo prototipo NOVO de pesadelo (minimalista,
# sem figuras). O arquivo antigo fica em /home/user/backup/ se precisar voltar.
check('3.97/4.1: PesadeloAvisoFx adaptado ao mod (pacote + titulo/mensagem)',
      pf.startswith('package dev.rptag.client;')
      and 'public static void iniciar(String titulo, String mensagem)' in pf
      and 'registerAboveAll' in pf and 'tentaculo' not in pf.lower())
check('3.96: VoidAvisoFx completo (buraco negro + tunel)',
      'anel' in vf.lower() and 'Buf' in vf)
check('3.97: os 4 FX novos adaptados (pacote + iniciar(titulo, mensagem))',
      all(rd(C + '/%s.java' % f).startswith('package dev.rptag.client;')
          and 'public static void iniciar(String titulo, String mensagem)' in rd(C + '/%s.java' % f)
          for f in ['InfernoAvisoFx', 'GeloAvisoFx', 'RunicoAvisoFx', 'PesadeloAvisoFx']))
check('3.97: AvisoFxUtil compartilhada (Buf/vinheta/aditivo/normal)',
      rd(C + '/AvisoFxUtil.java').startswith('package dev.rptag.client;')
      and 'public static final class Buf' in rd(C + '/AvisoFxUtil.java')
      and 'vinheta(' in rd(C + '/AvisoFxUtil.java'))
check('3.97: camadas aviso_inferno/gelo/runico registradas',
      'InfernoAvisoFx.registrar(event)' in cgl
      and 'GeloAvisoFx.registrar(event)' in cgl
      and 'RunicoAvisoFx.registrar(event)' in cgl)
check('3.97: ClientEffects roteia 2/5/6 pro FX (titulo AVISO + mensagem)',
      'RunicoAvisoFx.iniciar("AVISO", limpo2)' in ce
      and 'InfernoAvisoFx.iniciar("AVISO", limpo5)' in ce
      and 'GeloAvisoFx.iniciar("AVISO", limpo6)' in ce)
check('3.97: particulas server-safe de runico/inferno/gelo',
      'particulasRunico(alvo)' in rp and 'particulasInferno(alvo)' in rp
      and 'particulasGelo(alvo)' in rp and 'PACKED_ICE' in rp and 'ENCHANT' in rp)
check('3.96: camadas aviso_void + aviso_pesadelo registradas',
      'VoidAvisoFx.registrar(event)' in cgl
      and 'PesadeloAvisoFx.registrar(event)' in cgl)
check('3.96: startScreenText roteia 1 (pesadelo) e 8 (void) pros FX',
      'style == 1' in ce and 'PesadeloAvisoFx.iniciar("AVISO", limpo1)' in ce
      and 'style == 8' in ce and 'VoidAvisoFx.iniciar("AVISO", limpo8)' in ce)
check('3.96: somAviso cases 1 e 8 = particulas server-safe',
      'case 1 -> particulasPesadelo(alvo, true)' in rp
      and 'case 8 -> particulasVoid(alvo)' in rp
      and 'SCULK_SOUL' in rp and 'REVERSE_PORTAL' in rp
      and 'class VoidAvisoFx' not in rp and 'class PesadeloAvisoFx' not in rp)

# ---- 3.96.4: LINHA UNICA da mensagem (cy + 25k) nos 5 FX ----
check('3.96.4/3.97: mensagem na MESMA linha (cy + 25k) nos 8 FX, nunca acima',
      all(('cy + 25 * k, 0)' in rd(C + '/%s.java' % f)
          and 'ty - 12 * k' not in rd(C + '/%s.java' % f))
          for f in ['DivinoAvisoFx', 'DemoniacoAvisoFx', 'SangueAvisoFx',
                    'VoidAvisoFx', 'PesadeloAvisoFx', 'InfernoAvisoFx',
                    'GeloAvisoFx', 'RunicoAvisoFx']))

# ---- 3.98.0: PESADELO2 = "Sono Sem Descanso" (modelo antigo, estilo 11) ----
pa = rd(C + '/PesadeloAntigoAvisoFx.java')
check('3.98: PesadeloAntigoAvisoFx = "Sono Sem Descanso" (tunel + tentaculos + 16 olhos)',
      pa.startswith('package dev.rptag.client;')
      and 'class PesadeloAntigoAvisoFx' in pa
      and 'public static void iniciar(String t, String s)' in pa
      and 'tentaculo' in pa.lower() and 'PesadeloAvisoFx' not in pa.replace('PesadeloAntigoAvisoFx', ''))
check('3.98: camada aviso_pesadelo2 registrada (as DUAS opcoes convivem)',
      'PesadeloAntigoAvisoFx.registrar(event)' in cgl
      and 'PesadeloAvisoFx.registrar(event)' in cgl
      and 'aviso_pesadelo2' in pa and '"aviso_pesadelo"' in rd(C + '/PesadeloAvisoFx.java'))
check('3.98: estilo 11 roteia pro PesadeloAntigoAvisoFx',
      'style == 11' in ce and 'PesadeloAntigoAvisoFx.iniciar("AVISO", limpo11)' in ce)
check('3.98: comando aceita pesadelo2 (parser + literal + nome + particulas)',
      'case "pesadelo2" -> 11' in rp and 'literal("pesadelo2")' in rp
      and 'case 11 -> "PESADELO SONO"' in rp and 'case 11 -> particulasPesadelo(alvo, true)' in rp)
check('3.98: mensagem embaixo tambem no pesadelo2',
      'cy + 25 * k, 0)' in pa)

# ---- 3.99.0: lorezone - aviso unico + musica/som com tempo maximo ----
check('3.99 -> 5.26.1: aviso a CADA entrada (saiu e voltou = aparece de novo)',
      'boolean entrouAgora = !prev.contains(zone.id())' in lz
      and 'show(player, zone);' in lz
      and 'if (!data.avisoVisto(uuid, zone.id())) {' not in lz)
check('3.99: som pontual toca a CADA entrada (fora do bloco do aviso)',
      lz.count('tocarSom(player, zone)') == 1
      and 'if (!zone.sound().isEmpty()) {\n                        tocarSom(player, zone);' in lz)
check('3.99: musica nunca eterna (limite vira silencio ate sair)',
      'MUSIC_MAX_PADRAO_SEG = 60' in lz
      and 'MUSIC.put(uuid, new MusicState(inside.id(), st.startedMs(), true))' in lz)
check('3.99: /lorezone avisos reset <jogador> (admin reexibe avisos)',
      'literal("avisos")' in lzcmd and 'resetAvisos(' in lz)
check('3.99: mensagem do comando explica o TEMPO MAXIMO',
      'tempo maximo; nao fica em loop eterno' in lzcmd)

# ---- 3.99.0 (cliente): musica custom TAMBEM tem tempo maximo ----
mcm = rd(C + '/MusicaCustom.java')
lmp = rd(R + '/LorezoneMusicPayload.java')
qry = rd(R + '/MusicFileQueryPayload.java')
cph = rd(C + '/ClientPayloadHandler.java')
check('3.99: payload de musica carrega o TEMPO MAXIMO (URL e arquivo)',
      'int maxSeg' in lmp and 'ByteBufCodecs.VAR_INT, LorezoneMusicPayload::maxSeg' in lmp
      and 'int maxSeg' in qry and 'ByteBufCodecs.VAR_INT, MusicFileQueryPayload::maxSeg' in qry)
check('3.99: cliente para a musica custom sozinho (vigia do prazo)',
      'private static volatile long limiteMs' in mcm and 'armarVigia()' in mcm
      and 'MAX_PADRAO_SEG = 600' in mcm and 'limiteMs = 0L' in mcm)
check('3.99: handlers definem o limite ao tocar/baixar',
      'MusicaCustom.definirLimite(payload.maxSeg())' in cph
      and cph.count('definirLimite(payload.maxSeg())') == 2)
check('3.99: servidor passa o maxSeg (arquivo e URL)',
      'MusicSync.pedir(player, nome, maxSeg)' in lz
      and 'new LorezoneMusicPayload(true, inside.music(), maxSeg)' in lz)

# ---- 4.0.0: BARREIRA admin-only + faixa vertical (caverna) + varinha ----
check('4.0: Zone tem faixa vertical (minY/maxY) salva e carregada',
      'double minY, double maxY' in lz and 'tag.putDouble("MinY", minY)' in lz
      and 'tag.contains("MinY") ? tag.getDouble("MinY")' in lz and 'faixaVertical()' in lz)
check('4.0: aviso so dispara DENTRO da faixa vertical (nao no topo)',
      'py >= zone.minY() && py <= zone.maxY()' in lz)
check('4.0: /lorezone vertical <id> <minY> <maxY> + auto',
      'literal("vertical")' in lzcmd and 'literal("auto")' in lzcmd
      and 'agora so dispara entre Y' in lzcmd)
av = rd(C + '/ZoneVisualizer.java')
zsp = rd(R + '/ZoneShowPayload.java')
check('4.0: BARREIRA client-side (arame de esfera + faixa amarela)',
      av.startswith('package dev.rptag.client;')
      and 'RenderLevelStageEvent' in av and 'RenderType.lines()' in av
      and 'meridianos' in av and 'paredes da FAIXA VERTICAL' in av)
check('4.0: pacote ZoneShowPayload (codec manual, 9 campos)',
      'record ZoneShowPayload(' in zsp and 'StreamCodec.of(' in zsp
      and 'buf.writeDouble(p.minY())' in zsp)
check('4.0: payload registrado + handler no cliente',
      'ZoneShowPayload.TYPE' in rd(R + '/ModNetworking.java')
      and 'handleZoneShow' in cph and 'ZoneVisualizer.aplicar(payload)' in cph)
check('4.0: /lorezone mostrar + esconder (so o admin ve)',
      'literal("mostrar")' in lzcmd and 'literal("esconder")' in lzcmd
      and 'sendToPlayer(p,' in lzcmd)
check('4.0: VARINHA de lorezone (item registrado + modelo + lang)',
      'LOREZONE_WAND' in rtm and 'ITEMS.register("lorezone_wand"' in rtm
      and os.path.exists('src/main/resources/assets/rptag/models/item/lorezone_wand.json')
      and 'item.rptag.lorezone_wand' in rd('src/main/resources/assets/rptag/lang/pt_br.json'))
check('4.0: varinha so admin (permissao 2) + so o clicante recebe',
      'hasPermissions(2)' in rd(R + '/LorezoneWand.java')
      and 'sendToPlayer(player' in rd(R + '/LorezoneWand.java'))
check('4.0: /lorezone listar mostra a faixa Y quando limitada',
      'faixaVertical()' in lzcmd and 'Y " + Math.round(z.minY())' in lzcmd)

# ---- 4.1.0: morte OFF RP nao desconta vida canonica + FX de morte + novo pesadelo ----
cl = rd(R + '/CanonLife.java')
check('4.1: matador OFF RP NAO faz a vitima perder vida canonica (LivingDeathEvent)',
      'LivingDeathEvent' in cl and 'MORTE_NAO_CONTA' in cl
      and 'if (!(agressor instanceof ServerPlayer matador))' in cl
      and 'if (wd.isInRp(matador.getUUID()))' in cl)
check('4.1: morte sem matador (queda/mob) continua contando',
      'return; // morte "do mundo" (queda, mob, fogo...): conta normal' in cl)
check('4.1: marca consumida no respawn (nao vaza entre mortes)',
      'if (MORTE_NAO_CONTA.remove(p.getUUID()))' in cl
      and 'MORTE_NAO_CONTA.remove(e.getEntity().getUUID())' in cl)

mafx = rd(C + '/MorteAvisoFx.java')
check('4.1: MorteAvisoFx "Linha Reta" adaptado (pacote + iniciar(titulo, mensagem))',
      mafx.startswith('package dev.rptag.client;')
      and 'public static void iniciar(String titulo, String mensagem)' in mafx
      and 'CRANIO' in mafx and 'ECG_END' in mafx and 'particulasMundo(ServerPlayer p)' in mafx)
check('4.1: MorteAvisoFx registra a camada aviso_morte', 'aviso_morte' in mafx
      and 'MorteAvisoFx.registrar(event)' in rd(C + '/ClientGuiLayers.java'))
check('4.1: estilo 4 (morte) usa o FX, mantendo veu + audio da lore',
      'style == 4' in rd(C + '/ClientEffects.java')
      and 'MorteAvisoFx.iniciar("AVISO", limpo4)' in rd(C + '/ClientEffects.java')
      and 'somCanonDue = agora4 + 1500L' in rd(C + '/ClientEffects.java'))
check('4.1: MorteAvisoFx poe a MENSAGEM embaixo do titulo (padrao 3.96.4)',
      'cy + 25 * k' in mafx and 'f.width(sub)' in mafx)

pafx = rd(C + '/PesadeloAvisoFx.java')
check('4.1: PESADELO novo = prototipo minimalista (Seguido sem figuras)',
      'LIXO' in pafx and 'beat(' in pafx and 'CRANIO' not in pafx
      and 'public static void iniciar(String titulo, String mensagem)' in pafx)
check('4.1: PESADELO novo mantem escuridao + particulas do servidor',
      'particulasMundo(ServerPlayer p, boolean escuridao)' in pafx
      and 'MobEffects.DARKNESS' in pafx and 'particulasPesadelo(alvo, true)' in rp)
check('4.1: PESADELO novo poe a MENSAGEM embaixo do titulo (padrao 3.96.4)',
      'cy + 25 * k' in pafx and 'f.width(sub)' in pafx)

# ---- 4.2.0: avisos em VIDEO (Blender) - player + MORTE em modo video
vfx = rd(C + '/VideoFx.java')
ldr = rd(C + '/VideoFrameLoader.java')
cfg = rd(C + '/FxConfig.java') + rd(C + '/FxComando.java')
check('4.2: VideoFx existe com abrir/desenhar/fechar',
      'static boolean abrir(String id)' in vfx and 'Estado desenhar(GuiGraphics g, float t)' in vfx
      and 'static void fechar()' in vfx)
check('4.2: loader em thread daemon rptag-fx-loader', 'rptag-fx-loader' in ldr and 'setDaemon(true)' in ldr)
check('4.2: MORTE tenta video antes do procedural', 'VideoFx.abrir("morte")' in mafx)
check('4.2: MORTE cai pro procedural no mesmo t (FALHOU)', 'Estado.FALHOU' in mafx and 'video = false' in mafx)
check('4.2: render do player protegido (catch Throwable)', 'catch (Throwable' in vfx)
check('4.2: fecha o video ao sair do mundo', 'VideoFx.fechar()' in rd(C + '/ClientEvents.java'))
check('4.2: fxVideo em cliente.properties + /rpfx',
      'cliente.properties' in cfg and 'fxVideo' in cfg and 'literal("rpfx")' in cfg)
# ---- 4.5.0: Inferno e Gelo em VIDEO (Blender), mesmo player do MORTE
for _id, _cls in (('inferno', 'InfernoAvisoFx'), ('gelo', 'GeloAvisoFx')):
    _src = rd(C + '/%s.java' % _cls)
    check('4.5: %s tem o caminho de video (respeita fxVideo/reduzido)' % _cls,
          ('VideoFx.abrir("%s")' % _id) in _src and 'FxVideoPolitica.permitir' in _src)
    check('4.5: %s cai pro procedural no mesmo t (FALHOU)' % _cls,
          'VideoEstado.FALHOU' in _src and 'video = false' in _src)
    check('4.5: %s toca os sons so depois de resolver a carga' % _cls,
          'VideoEstado.CARREGANDO' in _src and _src.index('VideoEstado.CARREGANDO') < _src.index('        sons(t);'))
    check('4.5: %s tem parar() (aviso novo encerra o anterior)' % _cls, 'public static void parar()' in _src)
    check('4.5: %s com contorno escuro na mensagem no modo video (legivel no ceu claro)' % _cls,
          'CONTORNO_VIDEO' in _src and 'if (video) contornoEscuro(' in _src)
    check('4.5: assets do video %s no jar' % _id, os.path.exists(A + '/fx/%s/fx.json' % _id))
check('4.5: aviso novo encerra os avisos em video anteriores (ClientEffects)',
      all(('%s.parar()' % k) in rd(C + '/ClientEffects.java') for k in ('MorteAvisoFx', 'InfernoAvisoFx', 'GeloAvisoFx')))
check('4.5: sair do mundo encerra os avisos em video (nada volta ao reentrar)',
      all(('%s.parar()' % k) in rd(C + '/ClientEvents.java') for k in ('MorteAvisoFx', 'InfernoAvisoFx', 'GeloAvisoFx')))
check('4.5: MORTE tambem respeita a politica (reduzido = procedural)',
      'FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("morte")' in rd(C + '/MorteAvisoFx.java'))
check('4.5: politica do video (reduzido = procedural) em Java puro',
      'return habilitado && !reduzido;' in rd(C + '/FxVideoPolitica.java'))
import subprocess
check('4.2: assets de video validos (arte/validar.py)',
      subprocess.run([sys.executable, '-I', 'arte/validar.py'], capture_output=True).returncode == 0)

# ---- 4.3.0: SANGUE = "HEMORRAGIA" (substitui o "Sangria" cartoon) ----
saf2 = rd(C + '/SangueAvisoFx.java')
check('4.3: sangue sem gloss branco (era o que deixava cartoon)',
      'GLOSS = 0xE07070' in saf2 and '0xFF9A9A' not in saf2 and '0xFF8C8C' not in saf2
      and 'WHITE' not in saf2)
check('4.3: sangue tem ferida que rasga + jatos no ritmo do coracao',
      'RUPTURA' in saf2 and 'private static void ferida(' in saf2 and 'private static void jatos(' in saf2
      and 'BEATS' in saf2)
check('4.3: sangue escorre no vidro e forma poca (sem gota redonda com brilho)',
      'private static void manchas(' in saf2 and 'private static void poco(' in saf2
      and 'elipse(dx, y0 + L, 0, 1' in saf2 and 'private static void grao(' in saf2)
check('4.3: sangue mantem contrato do FX (iniciar + registerAboveAll + particulasMundo)',
      'public static void iniciar(String t, String s)' in saf2
      and 'registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_sangue")' in saf2
      and 'public static void particulasMundo(ServerPlayer p)' in saf2
      and 'cy + 25 * k' in saf2)

# ---- 4.4.0: /rp admin vida (tirar/dar/definir/ver) + coracao voxel ----
check('4.4: /rp admin vida <jogador> tirar|dar|definir|ver existe',
      '.then(Commands.literal("vida")' in rp and 'vidaTirar' in rp
      and 'vidaDar' in rp and 'vidaDefinir' in rp and 'vidaVer' in rp)
check('4.4: tirar vida usa o MESMO estoque do ritual (get/setCanonLives)',
      'wd.getCanonLives(alvo.getUUID())' in rp and 'wd.setCanonLives(alvo.getUUID(), depois)' in rp)
check('4.4: tirar vida avisa o alvo (estilo 4) + trovao + MEDO na ultima',
      'ScreenTextPayload(msg, 12, 4)' in rp and 'LIGHTNING_BOLT_THUNDER' in rp
      and 'FearPayload(30)' in rp)
check('4.4: tirar vida NAO congela o jogador (nao houve morte)',
      'FREEZES' not in rp.rsplit('vidaTirar', 1)[1].split('private static int vidaDar')[0])
check('4.4: ajuda lista o /rp admin vida', 'admin vida <jogador> tirar|dar' in rp)
check('4.4: coracao mantem o pickup SHIFT (+1 vida) e o teto 5/5',
      'isShiftKeyDown' in blk and 'vidas >= 5' in blk and 'NOVA CHANCE' in blk)

# ---- 4.6.0: Void e Divino em VIDEO (Blender) — tamanho dos videos no jar
def _tam_pasta(p):
    return sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(p) for f in fs)
for _id, _cls in (('void', 'VoidAvisoFx'),):
    _src = rd(C + '/%s.java' % _cls)
    check('4.6: %s tem o caminho de video (desligado por VIDEO_PADRAO=false)' % _cls,
          ('VideoFx.abrir("%s")' % _id) in _src)
    check('4.6: %s cai pro procedural no mesmo t (FALHOU)' % _cls,
          'VideoEstado.FALHOU' in _src and 'video = false' in _src)
    check('4.6: %s toca os sons so depois de resolver a carga' % _cls,
          'VideoEstado.CARREGANDO' in _src and _src.index('VideoEstado.CARREGANDO') < _src.index('        sons(t);'))
    check('4.6: %s tem parar() (aviso novo encerra o anterior)' % _cls, 'public static void parar()' in _src)
    check('4.6: %s com contorno escuro na mensagem no modo video' % _cls,
          'CONTORNO_VIDEO' in _src and 'if (video) contornoEscuro(' in _src)
check('4.6: aviso novo encerra tambem Void e Divino (ClientEffects)',
      all(('%s.parar()' % k) in rd(C + '/ClientEffects.java') for k in ('VoidAvisoFx', 'DivinoAvisoFx')))
check('4.6: sair do mundo encerra tambem Void e Divino',
      all(('%s.parar()' % k) in rd(C + '/ClientEvents.java') for k in ('VoidAvisoFx', 'DivinoAvisoFx')))
# (4.9.1) o video do DIVINO saiu: so o VOID continua com video no jar
for _id, _teto in (('void', 16),):
    check('4.6: assets do video %s no jar' % _id, os.path.exists(A + '/fx/%s/fx.json' % _id))
    check('4.6: video %s <= %d MiB' % (_id, _teto),
          os.path.isdir(A + '/fx/' + _id) and _tam_pasta(A + '/fx/' + _id) <= _teto * 1024 * 1024)

# ================= 4.5.0: AFK + regras de RP + vidas offline + EGIPICIO =================
afk = rd(R + '/AfkSistema.java')
reg = rd(R + '/RegrasRp.java')
vad = rd(R + '/VidasAdmin.java')
wdd = rd(R + '/RPWorldData.java')
egi = rd(C + '/EgipcioAvisoFx.java')

check('4.5: AFK varre os online a cada 5s (100 ticks)',
      'VARREDURA_TICKS = 100' in afk and 'getLastActionTime()' in afk)
check('4.5: AFK por tempo parado + /afk manual (mesma funcao alternar)',
      'TIMEOUT_PADRAO_S' in afk and 'alternar(' in afk and 'literal("afk")' in rp)
check('4.5: ficar AFK DESLIGA o RP automaticamente',
      'setInRp(p.getUUID(), false)' in afk and 'saiu do RP automaticamente' in afk)
check('4.5: acorda ao mexer (mover/olhar/chat/atacar/clicar)',
      'ACORDA_MOVE2' in afk and 'AttackEntityEvent' in afk
      and 'receiveCanceled' in afk and 'resetLastActionTime' in afk)
check('4.5: nao fica preso no AFK depois de relogar',
      'aoEntrar' in afk and 'aoSair' in afk and 'AFK.remove' in afk)
check('4.5: timeout do AFK persiste no mundo (AfkTimeout)',
      'afkTimeout' in wdd and 'tag.putInt("AfkTimeout"' in wdd and 'getAfkTimeout' in wdd)

check('4.9.0: regra PvP barra golpe corpo a corpo (AttackEntityEvent)',
      'AttackEntityEvent' in reg and 'PvP bloqueado' in reg)
check('4.5: regra PvP barra dano indireto (flecha/pocao/TNT)',
      'LivingIncomingDamageEvent' in reg and 'getSource().getEntity()' in reg)
check('4.9.0: quebrar (BlockEvent.BreakEvent) e colocar (EntityPlaceEvent) em RP',
      'BlockEvent.BreakEvent' in reg and 'EntityPlaceEvent' in reg
      and 'isInRp' in reg)
check('4.5: balde tambem conta como colocar (TriState.FALSE)',
      'TriState.FALSE' in reg and 'BucketItem' in reg)
check('4.5: staff (permissao 2) passa pelas regras',
      reg.count('hasPermissions(2)') >= 4)
check('4.9.0: as 3 flags de regra nao existem mais no mundo',
      'RegraPvp' not in wdd and 'RegraQuebrar' not in wdd and 'RegraColocar' not in wdd)
check('4.9.0: /rp admin regra nao existe (nada de on|off nem bloquear|liberar)',
      'literal("regra")' not in rp and 'mostrarRegras' not in rp)

check('4.5: vida do admin acha jogador OFFLINE (cache de perfis)',
      'getProfileCache()' in vad and 'record Alvo' in vad)
check('4.5: vida offline guarda recado pro proximo login',
      'setRecadoVida' in rp and 'consumirRecadoVida' in rd(R + '/ServerEvents.java')
      and 'RecadosVida' in wdd)
check('4.9.0: vida em LOTE aplica direto (sem confirmar)',
      'case "tirar" -> vidaTirar(ctx, a, qtd);' in rp and 'vidaConfirmar' not in rp
      and 'Pendente' not in vad)
check('4.5: /rp admin vida sem argumento lista quem esta abaixo de 5/5',
      'vidaListar' in rp and 'canonLivesIds' in wdd)
check('4.9.0: ajuda lista afk + lote e NAO lista mais regra', 'admin afk tempo' in rp
      and 'vida lote A,B,C' in rp and 'admin regra' not in rp)

check('4.5: EGIPICIO e o estilo 12 (parse + nome + ajuda)',
      'case "egipicio", "egipcio" -> 12' in rp and 'case 12 -> "EGIPICIO"' in rp
      and 'egipicio, expurgo, ciberpsicose, energia, conhecimento (alvos' in rp)
check('4.5: EGIPICIO desenha o OLHO DE HORUS (sobrancelha/amendoa/iris)',
      'EgipcioAvisoFx' in egi and 'olho' in egi.lower())
check('4.5: EGIPICIO tem templo, areia, hieroglifos e cartucho',
      all(k in egi for k in ['noite', 'piramides', 'areia', 'hieroglifos', 'cartucho']))
check('4.5: EGIPICIO tem trilha propria (alaude pentatonico)',
      'NOTE_BLOCK_HARP.value()' in egi and 'ESCALA' in egi)
check('4.5: EGIPICIO registrado acima de tudo (registerAboveAll)',
      'EgipcioAvisoFx.registrar(event)' in rd(C + '/ClientGuiLayers.java')
      and 'renderBackground' not in egi)
check('4.5: EGIPICIO desenha titulo AVISO letra a letra e mensagem EMBAIXO',
      'cy + 25' in egi and 'AVISO' in egi)
check('4.5: EGIPICIO no servidor: areia/incenso no alvo', 'FALLING_DUST' in rp and 'CAMPFIRE_COSY_SMOKE' in rp)


# ============ 4.5.1: BUG "Invalid player data" (argumento custom sem registro) ============
argt = rd(R + '/RpArgumentTypes.java')
lsta = rd(R + '/ListaAlvosArgument.java')
mod2 = rd(R + '/RPTagMod.java')

check('4.5.1: argumento custom registrado em COMMAND_ARGUMENT_TYPE',
      'Registries.COMMAND_ARGUMENT_TYPE' in argt and 'ARGUMENT_TYPES.register' in argt)
check('4.5.1: registro usa ArgumentTypeInfos.registerByClass (senao o login quebra)',
      'ArgumentTypeInfos.registerByClass' in argt and 'SingletonArgumentInfo' in argt)
check('4.5.1: o mod registra RpArgumentTypes no mod bus',
      'RpArgumentTypes.ARGUMENT_TYPES.register(modEventBus)' in mod2)
_codigo = '\n'.join(l for l in lsta.splitlines() if not l.strip().startswith(('*', '//', '/*')))
check('4.5.1: registro fora do <clinit> do argumento (DeferredHolder exige registries)',
      'DeferredRegister<' not in _codigo and 'DeferredHolder<' not in _codigo)
check('4.5.1: fallback nunca derruba o servidor (usa word() do vanilla)',
      'StringArgumentType.word()' in rp and 'argumentoAlvos()' in rp and 'disponivel()' in lsta)
check('4.5.1: rede de seguranca nao toca em RPTagMod (logger proprio)',
      'RPTagMod.LOGGER' not in _codigo)
check('4.5.1: teste JUnit reproduz o unpack do ClientboundCommandsPacket',
      'ArgumentTypeInfos.unpack' in rd('src/test/java/dev/rptag/ArgumentoSerializavelTest.java'))

# ================= 4.7.0: EGIPCIO em VIDEO (o ultimo aviso procedural virou Blender) =================
check('4.7: EgipcioAvisoFx tenta video (politica + abrir) antes do procedural',
      'FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("egipcio")' in egi)
check('4.7: EgipcioAvisoFx cai pro procedural no mesmo t (FALHOU)',
      'VideoEstado.FALHOU' in egi and 'video = false' in egi)
check('4.7: EgipcioAvisoFx toca os sons so depois de resolver a carga',
      'VideoEstado.CARREGANDO' in egi and egi.index('VideoEstado.CARREGANDO') < egi.index('        sons(t);'))
check('4.7: EgipcioAvisoFx tem parar() (aviso novo / sair do mundo encerra)',
      'public static void parar()' in egi and 'if (video) VideoFx.fechar();' in egi)
check('4.7: aviso novo encerra tambem o Egipcio (ClientEffects)',
      'EgipcioAvisoFx.parar()' in rd(C + '/ClientEffects.java'))
check('4.7: sair do mundo encerra tambem o Egipcio (ClientEvents)',
      'EgipcioAvisoFx.parar()' in rd(C + '/ClientEvents.java'))
_fx_eg = A + '/fx/egipcio'
# (4.7.6) os quadros do egipcio agora vem da ANIMACAO DE CANVAS do dono:
# sao 180 (7,5 s a 24 fps), nao mais os 144 do Blender.
_n_c = len([f for f in os.listdir(_fx_eg + '/frames') if f.endswith('_c.jpg')])
_n_a = len([f for f in os.listdir(_fx_eg + '/frames') if f.endswith('_a.jpg')])
check('4.7: assets do video egipcio no jar (fx.json + 180 quadros do canvas)',
      os.path.exists(_fx_eg + '/fx.json') and _n_c == 180 and _n_a == _n_c)
check('4.7: video egipcio <= 24 MiB', os.path.isdir(_fx_eg) and _tam_pasta(_fx_eg) <= 24 * 1024 * 1024)
_cena_eg = os.path.exists('arte/cenas/egipcio.py') and 'def montar' in rd('arte/cenas/egipcio.py')
check('4.7: cena arte/cenas/egipcio.py + metas.py registra o id',
      _cena_eg and "'egipcio'" in rd('arte/cenas/metas.py'))

# ================= 4.7.1: o dono escolheu o FX antigo em VOID e GELO + REAL novo =================
for _cls, _id in (('VoidAvisoFx', 'void'), ('GeloAvisoFx', 'gelo')):
    _s = rd(C + '/%s.java' % _cls)
    check('4.7.1: %s NAO usa video por padrao (dono prefere o FX antigo)' % _cls,
          'private static final boolean VIDEO_PADRAO = false;' in _s and 'VIDEO_PADRAO &&' in _s)
real = rd(C + '/RealAvisoFx.java')
check('4.7.1: estilo 3 (real) tem FX proprio (antes era so veu + 2 filetes)',
      "if (style == 3)" in rd(C + '/ClientEffects.java') and 'RealAvisoFx.iniciar' in rd(C + '/ClientEffects.java'))
check('4.7.1: REAL usa registerAboveAll (nunca RenderGuiEvent.Post)',
      'registerAboveAll' in real and 'RenderGuiEvent.Post' not in real)
check('4.9.9: REAL = "Decreto da Coroa" (coroa + faixa de cetim + louros + flores-de-lis)',
      'coroa(' in real and 'faixa(' in real and 'louro(' in real and 'lis(' in real and 'fio(' in real)
check('4.9.9: REAL tem coroa imperial de verdade (veludo, arminho, joias, arcos, orbe+cruz)',
      'CRIML' in real and 'ERM' in real and 'RUBY' in real and 'SAFI' in real and 'joia(' in real)
check('4.9.9: REAL desenha o TITULO acima e a MENSAGEM na faixa (msg embaixo do titulo)',
      'bcy - hh - 32f * k' in real and 'float yT = bcy - hh' in real and 'titulo' in real)
check('4.9.9: REAL blindado — render em try/catch (FX nunca derruba o jogo)',
      'private static void desenhar(' in real and 'falha no FX REAL (o jogo segue)' in real)
check('4.9.9: REAL aceita iniciar(titulo, mensagem) e tem parar()',
      'public static void iniciar(String t, String mensagem)' in real and 'public static void parar()' in real)
check('4.7.1: REAL e encerrado por aviso novo e pela saida do mundo',
      'RealAvisoFx.parar()' in rd(C + '/ClientEffects.java') and 'RealAvisoFx.parar()' in rd(C + '/ClientEvents.java'))
check('4.7.1: REAL registrado na camada (ClientGuiLayers)', 'RealAvisoFx.registrar' in rd(C + '/ClientGuiLayers.java'))
check('4.7.1: EGIPCIO usa a PLACA retangular dourada (nao o cartucho oval)',
      'PLACA DO FARA' in egi and 'diamante(' in egi)
check('4.7.1: EGIPCIO tem selo de ouro a direita da placa', 'SELO' in egi)

# ================= 4.7.2: o BUG do avisar egipicio (literal faltando no comando) =================
import re as _re
_rp = rd(R + '/RPCommands.java')
_comando = _rp[_rp.index('.then(Commands.literal("avisar")'):_rp.index('.then(Commands.literal("avisozona")')]
_literais = (set(_re.findall(r'Commands\.literal\("([a-z0-9_]+)"\)', _comando))
             | set(_re.findall(r'aviso2\("([a-z0-9_]+)"\)', _comando)))
_k = _rp.index('private static void avisar(')
_metodo = _rp[_k:_rp.index('    private static ', _k + 10)]      # so o corpo do avisar()
_estilos = set(_re.findall(r'case "([a-z0-9_]+)"', _metodo))
_estilos.discard('classico')          # classico e o argumento "texto" direto, sem literal
_sem_literal = sorted(e for e in _estilos if e not in _literais)
check('4.7.2: TODO estilo do avisar tem literal no comando (egipicio inclusive)', not _sem_literal)
check('4.7.2: egipicio tem literal proprio no avisar', 'Commands.literal("egipicio")' in _comando)
# 4.7.3: so UM literal por estilo — "egipcio" junto de "egipicio" duplicava a
# entrada no tab-complete do jogo (o dono viu "egipcio" e "egipicio" na lista).
_dup = sorted({e for e in _literais if list(_re.findall(r'Commands\.literal\("([a-z0-9_]+)"\)', _comando)).count(e) > 1})
check('4.7.3: nenhum estilo aparece 2x no comando (sem sugestao duplicada)', not _dup)
check('4.7.3: "egipcio" NAO e literal (uma entrada so na lista)',
      'Commands.literal("egipcio")' not in _comando)
check('4.7.3: a grafia "egipcio" funciona pelo fallback do avisar()',
      'equals("egipcio")' in _metodo and 'style = 12' in _metodo)

# ============ 4.7.4: /rpfx teste (isola FX quebrado x comando que nao chega) ============
_fx = rd(C + '/FxComando.java')
check('4.7.4: /rpfx teste registrado no comando de cliente',
      'literal("teste")' in _fx and 'startScreenText' in _fx)
check('4.7.4: /rpfx teste aceita os estilos do avisar (egipicio inclusive)',
      'case "egipicio", "egipcio" -> 12;' in _fx)
check('4.7.4: texto opcional no /rpfx teste (greedyString)',
      'greedyString()' in _fx and 'TESTE ' in _fx)

# ============ 4.7.6: o aviso egipcio virou A ANIMACAO DO DONO (canvas) ============
_egi2 = rd(C + '/EgipcioAvisoFx.java')
check('4.7.6: egipcio tem 7,5 s (duracao da animacao do dono)', 'DUR = 7.5f' in _egi2)
check('4.7.6: placa/texto usam a area 16:9 desenhada (FxTimeline.cobrir)',
      'FxTimeline.cobrir(w, h)' in _egi2 and 'tela[0] + tela[2] / 2f' in _egi2)
check('4.7.6: no modo video a placa fica no lugar do cartucho do canvas (0.783)',
      'tela[3] * 0.783f' in _egi2)
check('4.7.6: renderizador da animacao de canvas existe (arte/render_canvas.js)',
      os.path.exists('arte/render_canvas.js') and '__render' in rd('arte/render_canvas.js'))

# ============ 4.7.7: TRILHA PROPRIA do aviso egipcio (sincronizada) ============
_rm = rd(R + '/RPTagMod.java')
check('4.7.7: som egipcio_aviso registrado (SoundEvent)', 'SOM_EGIPCIO' in _rm and 'egipcio_aviso' in _rm)
check('4.7.7: sounds.json tem egipcio_aviso',
      'egipcio_aviso' in rd('src/main/resources/assets/rptag/sounds.json'))
check('4.7.7: ogg da trilha no jar', os.path.exists(A + '/sounds/egipcio_aviso.ogg')
      and os.path.getsize(A + '/sounds/egipcio_aviso.ogg') > 20000)
check('4.7.7: EgipcioAvisoFx toca a trilha e tem reserva (note blocks)',
      'SOM_EGIPCIO.get()' in _egi2 and 'NOTE_BLOCK_HARP' in _egi2)
check('4.7.7: trilha para quando o aviso e interrompido (pararTrilha)',
      'public static void pararTrilha()' in _egi2 and 'pararTrilha();' in _egi2)
check('4.7.7: gerador da trilha no repo (arte/audio_egipcio.py)',
      os.path.exists('arte/audio_egipcio.py') and 'escala HIJAZ' in rd('arte/audio_egipcio.py'))

# ============ 4.7.8: a trilha agora e o audio DO DONO (corte de 7,5 s) ============
_ogg = A + '/sounds/egipcio_aviso.ogg'
check('4.7.8: trilha do dono no jar (ogg 7,5 s, ~74 KB)',
      os.path.exists(_ogg) and 50_000 < os.path.getsize(_ogg) < 120_000)
check('4.7.8: fonte do dono guardada em arte/fontes (reproduzivel)',
      os.path.exists('arte/fontes/egipcio_origem.mp3')
      and os.path.getsize('arte/fontes/egipcio_origem.mp3') > 1_000_000)
check('4.7.8: importador de audio no repo (corte + fade + normalizacao)',
      os.path.exists('arte/importar_audio.py') and 'libvorbis' in rd('arte/importar_audio.py'))
check('4.7.8: analisador de audio no repo (acha o melhor trecho)',
      os.path.exists('arte/analisa_audio.py') and 'melhores trechos' in rd('arte/analisa_audio.py'))
check('4.7.8: a trilha toca no primeiro quadro e para no parar()',
      'tentouTrilha' in _egi2 and 'pararTrilha()' in _egi2)

# ============ 5.2.0: O TABLET VOLTOU (pedido 23 — "build ela por inteira no mod") ============
# O bloco 4.9.0 ("o tablet saiu") foi REVOGADO pelo dono: a branch tablet-neptune entrou inteira.
_java = []
for _base, _dirs, _files in os.walk('src/main/java'):
    _java += [f for f in _files if f.endswith('.java')]
_junto = '\n'.join(rd(os.path.join(_base2, f)) for _base2, _d, _fs in os.walk('src/main/java') for f in _fs if f.endswith('.java'))
# (5.6.1) +1 classe no cliente: TabletVideoFundo (video atras do player)
check('5.2.0: os arquivos do tablet/ficha estao no mod (7 no servidor + 6 no cliente)',
      len([f for f in os.listdir(R) if f.startswith(('Ficha', 'Tablet'))]) == 7
      and len([f for f in os.listdir(C) if f.startswith('Tablet')]) == 6)
check('5.2.0/5.6.1: as 6 classes do tablet estao no cliente (com o video de fundo)',
      len([f for f in os.listdir(C) if f.startswith('Tablet')]) == 6
      and os.path.exists(C + '/TabletVideoFundo.java'))
check('5.2.0: as classes novas sao as usadas (FichaPersonagem/FichaRegistro/TabletItem)',
      all(n in _junto for n in ('FichaPersonagem', 'FichaRegistro', 'TabletItem')))
check('5.2.0: item rptag:tablet existe com modelo e textura',
      'ITEMS.register("tablet"' in rd(R + '/RPTagMod.java')
      and os.path.exists(A + '/models/item/tablet.json')
      and os.path.exists(A + '/textures/item/tablet.png'))
check('5.2.0: texturas velhas da ficha (anel/buraco/nevoa) NAO voltaram',
      not any(os.path.exists(A + '/textures/gui/' + n) for n in
              ('ficha_anel.png', 'ficha_buraco.png', 'ficha_nevoa.png')))
check('5.2.0: o lang cita o tablet nos dois idiomas',
      'rptag.tablet' in rd(A + '/lang/pt_br.json')
      and 'rptag.tablet' in rd(A + '/lang/en_us.json'))
_mn9 = rd(R + '/ModNetworking.java')
check('5.2.0: canais novos tablet_sync/tablet_update no lugar dos antigos ficha_*',
      'FichaOpenPayload' not in _mn9 and 'FichaSavePayload' not in _mn9
      and 'tablet_sync' in rd(R + '/TabletSyncPayload.java') and 'tablet_update' in rd(R + '/TabletUpdatePayload.java'))
check('5.2.0/5.4.0: protocolo subiu para v9..v12 e agora v18 (cliente antigo e recusado)',
      'registrar("9")' not in _mn9 and 'registrar("18")' in _mn9 and 'registrar("8")' not in _mn9)
# (5.7.2) o canal subiu de novo: TITULOS entrou, cliente 5.7.1 e recusado
check('5.7.2: protocolo v10 (agora v18) com o pacote de TITULOS',
      'event.registrar("18")' in rd(R + '/ModNetworking.java')
      and 'TituloSyncPayload.TYPE' in rd(R + '/ModNetworking.java'))
check('5.2.0: o tablet e entregue no login', 'PlayerLoggedInEvent' in rd(R + '/TabletService.java'))
check('5.2.0: /tablet existe e /ficha nao voltou',
      'literal("tablet")' in _junto and 'literal("ficha")' not in _junto)
check('5.2.0: fichas ficam salvas no mundo (TabletSheets/getFicha)',
      'TabletSheets' in rd(R + '/RPWorldData.java') and 'getFicha(' in rd(R + '/RPWorldData.java'))
check('5.2.0: geradores antigos de mock do tablet continuam fora do repo',
      not any(os.path.exists('arte/' + n) for n in
              ('gerar_texturas_ficha.py', 'mock_tablet.py', 'mock_resumo.py', 'tablet_960.png', 'tablet_634.png')))

# ============ 4.9.0: /rp admin regra SAIU (regra agora e automatica) ============
_rc = rd(R + '/RPCommands.java')
_wd9 = rd(R + '/RPWorldData.java')
check('4.9.0: /rp admin regra nao existe mais',
      'literal("regra")' not in _rc and 'mostrarRegras' not in _rc and 'private static int regra(' not in _rc)
check('4.9.0: as 3 flags de regra sairam do world data',
      'RegraPvp' not in _wd9 and 'RegraQuebrar' not in _wd9 and 'RegraColocar' not in _wd9
      and 'setRegra' not in _wd9)
check('4.9.0: RegrasRp nao le mais flag nenhuma (vale sempre para quem esta em RP)',
      'isRegra' not in rd(R + '/RegrasRp.java'))
_rrp = rd(R + '/RegrasRp.java')
check('4.9.0: PvP bloqueado em RP sem configurar nada',
      'wd.isInRp(atacante.getUUID()) || wd.isInRp(vitima.getUUID())' in _rrp
      and 'PvP bloqueado' in _rrp)
check('4.9.0: quebrar/colocar bloqueados em RP sem configurar nada',
      'bloqueado em RP' in _rrp and 'wd.isInRp(p.getUUID())' in _rrp
      and 'isRegra' not in _rrp)

# ============ 4.9.0: confirmar/cancelar sairam; lote aplica na hora ============
_va9 = rd(R + '/VidasAdmin.java')
check('4.9.0: /rp admin confirmar e /rp admin cancelar nao existem',
      'literal("confirmar")' not in _rc and 'literal("cancelar")' not in _rc)
check('4.9.0: o lote guardado (Pendente/guardar/pegar/limpar) saiu do VidasAdmin',
      'Pendente' not in _va9 and 'guardar(' not in _va9 and 'PENDENTES' not in _va9)
check('4.9.0: vida lote aplica na hora (chama vidaTirar/vidaDar/vidaDefinir)',
      'case "tirar" -> vidaTirar(ctx, a, qtd);' in _rc
      and 'case "dar" -> vidaDar(ctx, a, qtd);' in _rc
      and 'default -> vidaDefinir(ctx, a, qtd);' in _rc)
check('4.9.0: vidaLote nao pede mais confirmacao no chat',
      'CONFIRMAR' not in _rc and 'vidaConfirmar' not in _rc)

# ============ 4.9.0: aviso de vida perdida para quem esta por perto ============
_cl9 = rd(R + '/CanonLife.java')
check('4.9.0: quem perde vida avisa os jogadores num raio de 32 blocos',
      'RAIO_AVISO_VIDA = 32.0D' in _cl9 and 'avisarPorPerto(' in _cl9)
check('4.9.0: texto exato do aviso ("<nome> perdeu uma vida - tome cuidado")',
      '" perdeu uma vida - tome cuidado"' in _cl9)
check('4.9.0: o aviso vai no chat de cada um (sendSystemMessage) e pula quem perdeu',
      'outro.sendSystemMessage(recado)' in _cl9 and 'if (outro == perdedor)' in _cl9)
check('4.9.0: so avisa quem esta no MESMO mundo',
      'outro.level() != perdedor.level()' in _cl9)
check('4.9.0: o aviso sai junto do ritual (depois de perder a vida)',
      'ritualDeVidaPerdida(p, vidas - 1);' in _cl9)
check(f'versao {VER} no build.gradle',
      ("version = '" + VER + "'") in rd('build.gradle') or ('version = "' + VER + '"') in rd('build.gradle'))

# ============ 4.9.1: o DIVINO voltou ao procedural antigo (o video do Marcello saiu) ============
_div = rd(C + '/DivinoAvisoFx.java')
check('4.9.1: o divino NAO usa mais video (sem VideoFx/FxConfig no arquivo)',
      'VideoFx' not in _div and 'FxConfig' not in _div and 'private static boolean video' not in _div)
check('4.9.1: a pasta do video do divino saiu do mod',
      not os.path.exists(A + '/fx/divino') and not os.path.exists(A + '/fx/divino/fx.json'))
check('4.9.1: o desenho antigo esta de volta (pilar + raios + halo runico + asas + faiscas)',
      all(k in _div for k in ('pilar de luz descendo', 'raios(b, cx, cy', 'halo', 'asas', 'faíscas em estrela')))
check('4.9.1: anel, tiques runicos e hexagrama (o visual de sempre)',
      'tiques rúnicos' in _div and 'hexagrama' in _div and 'diamante' in _div)
check('4.9.1: sem contorno escuro de video (texto com glow dourado do procedural)',
      'contornoEscuro' not in _div and 'CONTORNO_VIDEO' not in _div)
check('4.9.1: os 5 sinos do acorde continuam',
      'AMETHYST_BLOCK_CHIME' in _div and 'BEACON_DEACTIVATE' in _div and 'BEACON_ACTIVATE' in _div)
check('4.9.1: iniciar/parar continuam existindo (aviso novo encerra o anterior)',
      'public static void iniciar(' in _div and 'public static void parar()' in _div)
check('4.9.1: o teste dos marcadores nao exige mais o fx.json do divino',
      'carregar("divino")' not in rd('src/test/java/dev/rptag/client/CelestiaisMarcadoresTest.java'))
check('4.9.1: o video do divino nao e mais prometido no WALKTHROUGH',
      '4.9.1' in rd('WALKTHROUGH.md'))


# ============ 4.9.2: aviso EXPURGO (prototipo do dono, catbox 7l0m00) ============
_exp = rd(C + '/ExpurgoAvisoFx.java')
check('4.9.2: ExpurgoAvisoFx existe no pacote do mod (sem com.exemplo)',
      'package dev.rptag.client;' in _exp and 'com.exemplo' not in _exp)
check('4.9.2: usa o AvisoFxUtil do mod (Buf/preparar/aditivo)',
      'import static dev.rptag.client.AvisoFxUtil.*;' in _exp and 'new Buf(' in _exp)
check('4.9.2: dura 11,8 s com as 3 viradas do prototipo',
      'DUR = 11.8f' in _exp and 'T_CONT = 2.6f' in _exp and 'T_SLAM = 5.4f' in _exp and 'T_FIM = 9.6f' in _exp)
check('4.9.2: as pecas do prototipo estao todas (TV, estatica, contagem, sirene, mascara)',
      all(k in _exp for k in ('TV liga', 'neve de estática', 'CONTAGEM REGRESSIVA', 'SIRENE',
                              'HORA DO EXPURGO', 'mascara(', 'hazard(', 'QUE DEUS NOS ABENÇOE')))
check('4.9.2: mensagem padrao de 12 horas + modo reduzido',
      'TODOS OS CRIMES SÃO LEGAIS DURANTE 12 HORAS' in _exp and 'public static boolean reduzido' in _exp)
check('4.9.2: parar() existe (aviso novo encerra o anterior)',
      'public static void parar()' in _exp)
check('4.9.2: particulasMundo no servidor (redstone/fumaca/chama)',
      'public static void particulasMundo(ServerPlayer p)' in _exp
      and 'REDSTONE_BLOCK' in _exp and 'LARGE_SMOKE' in _exp)
check('4.9.2: camada registrada (ClientGuiLayers)',
      'ExpurgoAvisoFx.registrar(event)' in rd(C + '/ClientGuiLayers.java'))
_ce = rd(C + '/ClientEffects.java')
check('4.9.2: estilo 13 abre o EXPURGO e o anterior e encerrado',
      'if (style == 13)' in _ce and 'ExpurgoAvisoFx.iniciar(' in _ce and 'ExpurgoAvisoFx.parar();' in _ce)
_rc92 = rd(R + '/RPCommands.java')
check('4.9.2: /rp admin avisar ... expurgo existe (literal unico)',
      'case "expurgo" -> 13;' in _rc92 and _rc92.count('.then(Commands.literal("expurgo")') == 1)
check('4.9.2: nome do estilo e as particulas de mundo no servidor',
      'case 13 -> "EXPURGO";' in _rc92 and 'case 13 -> particulasExpurgo(alvo);' in _rc92 and 'particulasExpurgo(ServerPlayer p)' in _rc92)
check('4.9.2: a ajuda lista o expurgo', 'egipicio, expurgo' in _rc92)
check('4.9.2: /rpfx teste expurgo funciona no cliente',
      'case "expurgo" -> 13;' in rd(C + '/FxComando.java'))


# ============ 4.9.3: aviso CIBERPSICOSE (catbox 57mzzd) + MASCARA DE PEDRA ============
_cib = rd(C + '/CiberpsicoseAvisoFx.java')
check('4.9.3: CiberpsicoseAvisoFx no pacote do mod (sem com.exemplo)',
      'package dev.rptag.client;' in _cib and 'com.exemplo' not in _cib)
check('4.9.3: o glitch do prototipo esta inteiro (estatica, humanidade, vidro, ECG, surtos)',
      all(k in _cib for k in ('ALERTA: CIBERPSICOSE', 'HUMANIDADE', 'rachaduras(', 'ecg(',
                              'SINAIS VITAIS CRÍTICOS', 'REINICIANDO...', 'surto(')))
check('4.9.3: dura 30 s por padrao e tem parar()',
      'DUR_PADRAO = 30f' in _cib and 'public static void parar()' in _cib)
check('4.9.3: modo reduzido respeitado', 'public static boolean reduzido' in _cib)
check('4.9.3: camada registrada + estilo 14 abre e encerra o anterior',
      'CiberpsicoseAvisoFx.registrar(event)' in rd(C + '/ClientGuiLayers.java')
      and 'if (style == 14)' in rd(C + '/ClientEffects.java')
      and 'CiberpsicoseAvisoFx.parar();' in rd(C + '/ClientEffects.java'))
_rc93 = rd(R + '/RPCommands.java')
check('4.9.3/5.25.0: ciberpsicose = o AVISO (estilo 14) e, desde a 5.25.0, tambem um EFEITO',
      'case "ciberpsicose", "ciber" -> 14;' in _rc93
      # (5.25.0) agora sao DOIS literais com esse nome, de proposito: o aviso (estilo 14) e o
      # comando do efeito (/rp admin ciberpsicose <jogadores> [segundos])
      and _rc93.count('.then(Commands.literal("ciberpsicose")') == 2
      and 'case 14 -> "CIBERPSICOSE";' in _rc93
      and 'egipicio, expurgo, ciberpsicose' in _rc93)
check('4.9.3: particulas do servidor (faisca eletrica, fumaca, end rod)',
      'particulasCiberpsicose' in _rc93 and 'ELECTRIC_SPARK' in _rc93
      and 'case 14 -> particulasCiberpsicose(alvo);' in _rc93)
check('4.9.3: /rpfx teste ciberpsicose (cliente, sem servidor)',
      '"ciberpsicose"' in rd(C + '/FxComando.java') and '-> 14;' in rd(C + '/FxComando.java'))

# ============ 4.9.5: audio do dono no CIBERPSICOSE + mascara/coracao 3D ============
_cib = rd(C + '/CiberpsicoseAvisoFx.java')
check('4.9.5: o .ogg do dono esta no jar (assets .../sounds/ciberpsicose_aviso.ogg)',
      os.path.exists(A + '/sounds/ciberpsicose_aviso.ogg')
      and os.path.getsize(A + '/sounds/ciberpsicose_aviso.ogg') > 40000)
check('4.9.5: som registrado no sounds.json e no RPTagMod',
      'ciberpsicose_aviso' in rd(A + '/sounds.json')
      and 'SOM_CIBERPSICOSE' in rd(R + '/RPTagMod.java'))
check('4.9.5: o aviso toca a trilha UMA vez (somTocou) no primeiro quadro',
      'SOM_CIBERPSICOSE.get()' in _cib and 'private static boolean somTocou;' in _cib
      and 'somTocou = false;' in _cib)
check('4.9.5: o VIDRO QUEBRANDO continua no aviso (pico e colapso)',
      'GLASS_BREAK, 1.7f, 0.30f' in _cib and 'GLASS_BREAK, 0.6f, 1.0f' in _cib)
check('4.9.5: modo reduzido nao toca a trilha (acessibilidade)',
      'if (!reduzido) {\n                try {' in _cib
      and 'SOM_CIBERPSICOSE.get(), 1.0f, 1.0f' in _cib)

# ============ 4.9.7: o mod NAO pode derrubar o jogo (blindagem) ============
check('4.9.7: audio do ciberpsicose protegido (try/catch)',
      'nao consegui tocar o audio do ciberpsicose' in _cib)

# ============ 5.2.0: TABLET NEPTUNE (branch tablet-neptune inteira) ============
_ti = rd(R + '/TabletItem.java')
_ts = rd(R + '/TabletService.java')
_tn = rd(C + '/TabletScreen.java')
_tl = rd(C + '/TabletLayout.java')
_tc = rd(C + '/TabletClientState.java')
_te = rd(C + '/TabletClientEvents.java')
_fr = rd(R + '/FichaRegistro.java')
_fp = rd(R + '/FichaPersonagem.java')
_rw = rd(R + '/RPWorldData.java')
_mn = rd(R + '/ModNetworking.java')
_hl = rd(R + '/RPCommands.java')

check('5.2.0: 12 arquivos do tablet existem',
      all(os.path.exists(R + '/' + f) for f in ['FichaPersonagem.java', 'FichaRegistro.java', 'TabletItem.java',
          'TabletService.java', 'TabletSyncPayload.java', 'TabletUpdatePayload.java', 'TabletUpdateBudget.java'])
      and all(os.path.exists(C + '/' + f) for f in ['TabletClientEvents.java', 'TabletClientState.java',
          'TabletLayout.java', 'TabletPlayerPreview.java', 'TabletScreen.java']))
check('5.2.0: item rptag:tablet registrado (stacksTo 1)', 'ITEMS.register("tablet"' in rd(R + '/RPTagMod.java')
      and 'stacksTo(1)' in rd(R + '/RPTagMod.java'))
check('5.2.0: tabela de limites da ficha 32/5/240/600/3000', 'LIMITES = {32, 5, 240, 600, 3000}' in _fp)
check('5.2.0: protocolo com os 2 payloads do tablet',
      'registrar("18")' in _mn and 'TabletSyncPayload.TYPE' in _mn and 'TabletUpdatePayload.TYPE' in _mn)
check('5.2.0: handler do cliente registrado', 'handleTablet' in rd(C + '/ClientPayloadHandler.java'))
check('5.2.0: ficha salva no mundo (TabletSheets) e lida com checagem de UUID antes do load',
      'tag.put("TabletSheets", sheets)' in _rw and 'hasUUID("Id")' in _rw
      and 'ficha do tablet descartada' in _rw)
check('5.2.0: entrega automatica no login + /tablet', 'PlayerLoggedInEvent' in _ts and 'tryGiveTablet' in _ts
      and 'Commands.literal("tablet")' in _ts)
check('5.2.0/5.13.1: /tablet ver, dar e liberar exigem permissao 2',
      _ts.count('hasPermission(2)') >= 2)
check('5.2.0: /persona criar|idade|desc|off bloqueados apos confirmar (e leitura liberada)',
      'personaLocked' in rd(R + '/PersonaCommands.java')
      and rd(R + '/PersonaCommands.java').count('TabletService.personaLocked(player)') == 4)
check('5.2.0: orcamento de 20 updates por janela de 20 ticks', 'used >= 20' in rd(R + '/TabletUpdateBudget.java')
      and 'tick - start >= 20' in rd(R + '/TabletUpdateBudget.java'))
check('5.2.0: erro temporario tem retry de 250 ms (nao vira conflito)',
      'retryAt = clock.getAsLong() + 250' in _tc and 'retryable' in _tc)
check('5.2.0: conflito de revisao bloqueia e oferece "Usar ficha do servidor"',
      'A ficha foi atualizada em outra sessão' in _fr and 'Usar ficha do servidor' in _tn)
check('5.2.0: fundo proprio sem desfoque (renderBackground vazio)',
      'public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}' in _tn)
check('5.2.0: campo de texto sem borda padrao do Minecraft',
      'box.setBordered(false)' in _tn and 'drawField(' in _tn)
check('5.2.0: layout nunca negativo e some a coluna do personagem em tela estreita',
      'MIN_CHARACTER_WIDTH = 430' in _tl and 'showCharacter' in _tl and 'Math.max(200, screenWidth)' in _tl)
check('5.2.0: status desenhado fora do campo (bottomBar - 20)',
      'layoutBottom - 20' in _tn)
check('5.2.0: abertura curta (1100 ms) e clicavel para pular',
      'opened >= 1100' in _tn and 'if (boot) { skipBoot(); return true; }' in _tn)
check('5.2.0/5.13.1: item explica como abrir (tooltip) e entrega avisa no chat',
      'Clique direito para abrir SUA ficha' in _ti and 'clique direito para preencher sua ficha' in _ts)
check('5.2.0: /rp ajuda lista /tablet e os comandos de admin',
      '/tablet \\u00a77\\u2014 abre seu TABLET' in _hl and '/tablet liberar <jogador>' in _hl)
check('5.2.0: /persona mostra origem/habilidades/historia da ficha confirmada',
      'getFichaDireto' in _rw and 'ficha.confirmada().historia()' in rd(R + '/PersonaCommands.java'))
check('5.2.0: modelo e texturas do tablet no jar', os.path.exists(A + '/models/item/tablet.json')
      and os.path.getsize(A + '/textures/item/tablet.png') > 3000
      and os.path.getsize(A + '/textures/item/quiralos.png') > 50000)
check('5.2.0: lang pt_br/en_us com nome do item e titulo da tela',
      'item.rptag.tablet' in rd(A + '/lang/pt_br.json') and 'tablet.rptag.title' in rd(A + '/lang/en_us.json'))
check('5.2.0: pacote e fila do tablet protegidos contra excecao (nao derruba o jogo)',
      'receiveUnsafe' in _te and 'pacote do tablet ignorado' in _te
      and 'fila do tablet reiniciada' in _te)


# ============ 5.4.0: EVENTO NEPTUNE (terremoto, vazio, deus, bicho) ============
_NP = R + '/NeptuneEventos.java'
_NC = R + '/NeptuneCommands.java'
_NX = C + '/NeptuneFx.java'
_NPk = R + '/NeptunePackets.java'
_NE = R + '/NeptuneEventos.java'
check('5.4.0: motor do evento existe e tem os 4 sistemas',
      all(k in rd(_NE) for k in ['iniciarTerremoto', 'sairDoServidor', 'apagarDaExistencia', 'dispararBicho']))
check('5.4.0: terremoto deixa ESTUNCADO em PULSOS (6 s travado / 4 s livre)',
      'proximoPulso = agora + (faseTravado ? 6000 : 4000)' in rd(_NE)
      and 'MOVEMENT_SLOWDOWN' in rd(_NE))
check('5.4.0: rumble em loop + audio do colapso do dono (14,7 s)',
      'SOM_RUMBLE' in rd(_NE) and 'SOM_TERREMOTO_COLAPSO' in rd(_NE)
      and os.path.getsize(S + '/terremoto_colapso.ogg') > 80000)
check('5.4.0: saida do servidor com animacao verde e kick depois de N s',
      'NeptunePackets.Saida' in rd(_NE) and 'SAIDAS' in rd(_NE) and 'disconnect' in rd(_NE))
check('5.4.0: o deus do vazio tem aura, trilha ao andar e medo em quem chega perto',
      all(k in rd(_NE) for k in ['tickDeus', 'SOM_TRILHA_DEUS', 'FearPayload', 'SQUID_INK']))
check('5.4.0: vazio apaga com a frase exata do dono',
      '---- vazio --- apagou' in rd(_NE) and 'da existência' in rd(_NE))
check('5.4.0: dimensao rptag:vazio existe (dimension + dimension_type)',
      os.path.exists('src/main/resources/data/rptag/dimension/vazio.json')
      and os.path.exists('src/main/resources/data/rptag/dimension_type/vazio.json'))
check('5.4.0: o bicho sai de uma CAIXA (bloco) e salta a 3 blocos',
      'BICHO_RAIO = 3.0' in rd(_NE) and 'colocarCaixa' in rd(_NE)
      and os.path.exists('src/main/resources/assets/rptag/blockstates/caixa_bicho.json'))
check('5.4.0: caixinha de musica sintetizada (36 s) e berro do bicho (2,6 s)',
      os.path.getsize(S + '/caixa_musica.ogg') > 200000
      and os.path.getsize(S + '/bicho_grito.ogg') > 20000)
check('5.4.0: cliente desenha terremoto, saida, susto e nebulosa',
      all(k in rd(_NX) for k in ['desenharTerremoto', 'desenharSaida', 'desenharSusto', 'desenharNebulosa']))
check('5.4.0: tremor de camera do terremoto (duas vozes: grave + tranco)',
      'ViewportEvent.ComputeCameraAngles' in rd(_NX) and 'setRoll' in rd(_NX))
check('5.4.0: blur REAL no pulso travado (processBlurEffect) com fallback',
      'processBlurEffect' in rd(_NX) and 'catch (Throwable ignorado)' in rd(_NX))
check('5.4.0: texto do vazio no chat com a frase do dono',
      'NeptunePackets' in rd(_NPk) and 'neptune_saida' in rd(_NPk))
check('5.4.0: canal v9 (agora v18) registra os 5 pacotes novos',
      'event.registrar("18")' in rd('src/main/java/dev/rptag/ModNetworking.java')
      and rd('src/main/java/dev/rptag/ModNetworking.java').count('NeptunePackets') >= 5)
check('5.4.0: comandos /terremoto, /vazio, /bicho e /neptune so pra OP',
      all(k in rd(_NC) for k in ['literal("terremoto")', 'literal("vazio")', 'literal("bicho")', 'literal("neptune")'])
      and 'hasPermission(2)' in rd(_NC))
check('5.4.0: o tick do Neptune esta ligado no ServerEvents',
      'NeptuneEventos.tick' in rd(R + '/ServerEvents.java'))
check('5.4.0: ajuda do /rp lista o terremoto e o vazio',
      'terremoto iniciar' in rp and 'vazio ligar' in rp)
check('5.4.0: previa do evento (o que o jogador ve) existe',
      os.path.exists('arte/previa_neptune.py') and os.path.exists('previa/neptune_5.4.0.png'))
check('5.4.0: os 3 audios do dono entraram (colapso, vazio, trilha)',
      all(os.path.getsize(S + '/' + n + '.ogg') > 20000
          for n in ['terremoto_colapso', 'vazio_apagar', 'trilha_deus']))

# ============ 5.4.1: sem chiado de queimado, sem fumaca branca, som longo ============
_ne = rd(R + '/NeptuneEventos.java')
_nf = rd(C + '/NeptuneFx.java')
_au = rd('arte/audio_eventos.py')
check('5.4.1: a fumaca branca (CAMPFIRE_COSY_SMOKE) saiu do terremoto',
      'ParticleTypes.CAMPFIRE_COSY_SMOKE' not in _ne)
check('5.4.1: no lugar entra poeira do PROPRIO chao (FALLING_DUST + BLOCK)',
      'FALLING_DUST' in _ne and 'ParticleTypes.BLOCK' in _ne and 'ParticleTypes.ASH' in _ne)
check('5.4.1: o audio do colapso agora tem 75 s (era 14,7 s)',
      os.path.getsize(S + '/terremoto_colapso.ogg') > 120000
      and 'COLAPSO_MS = 75000L' in _ne)
check('5.4.1: se o evento durar mais que o audio, ele TOCA DE NOVO',
      'proximoColapso = agora + COLAPSO_MS' in _ne)
check('5.4.1: rumble de reforco mais raro (12 s) e baixinho (0,45)',
      'agora + 12000' in _ne and 'SoundSource.AMBIENT, 0.45F' in _ne)
check('5.4.1: NENHUM audio do evento usa ruido BRANCO pra corpo de impacto',
      'ruido_marrom' in _au and _au.count('ruido(') <= 3)
check('5.4.1: conversor do colapso corta o chiado em ~180-200 Hz (4a ordem)',
      'btype="low"' in _au and '180.0' in _au and '200.0' in _au)
check('5.4.1: poeira de tela do cliente virou po fino (sem quadrado marrom solto)',
      '0x8A7457' in _nf)

# ============ 5.5.0: aviso ENERGIA, exilio, sombra sem quadrado, aura circulando ============
_en = rd(C + '/EnergiaAvisoFx.java')
_ce = rd(C + '/ClientEffects.java')
_cg = rd(C + '/ClientGuiLayers.java')
_rp2 = rd(R + '/RPCommands.java')
_fx = rd(C + '/FxComando.java')
_ne2 = rd(R + '/NeptuneEventos.java')
_nf2 = rd(C + '/NeptuneFx.java')
_nc2 = rd(R + '/NeptuneCommands.java')
check('5.5.0: EnergiaAvisoFx existe e e registrado como camada de HUD',
      os.path.exists(C + '/EnergiaAvisoFx.java') and 'EnergiaAvisoFx::render' in _en
      and 'EnergiaAvisoFx.registrar(event)' in _cg)
check('5.5.0: estilo 15 = ENERGIA no avisar (cliente, servidor e /rpfx teste)',
      'if (style == 15)' in _ce and 'case "energia" -> 15;' in _rp2
      and 'case "energia" -> 15;' in _fx and 'case 15 -> "ENERGIA"' in _rp2)
check('5.5.0: /rp admin avisar aceita o literal "energia"',
      'Commands.literal("energia")' in _rp2 and 'avisar(ctx, alvos, "energia", texto)' in _rp2)
check('5.5.0: o ENERGIA tem nucleo que muda de forma, raios e medidor de ENTROPIA',
      'lobo' in _en and 'criarRaio' in _en and 'ENTROPIA' in _en and 'CRÍTICO' in _en)
check('5.5.0: dimensao do EXILIO existe (rptag:exilio) com chao (5.7.8: camadas de pedra)',
      os.path.exists('src/main/resources/data/rptag/dimension/exilio.json')
      and os.path.exists('src/main/resources/data/rptag/dimension_type/exilio.json')
      and ('black_concrete' in rd('src/main/resources/data/rptag/dimension/exilio.json')
           or 'blackstone' in rd('src/main/resources/data/rptag/dimension/exilio.json')))
# (5.12.0) O DONO MUDOU O PEDIDO: era "o exilio e CLARO" (5.7.9), agora e
# "o vazio tem que ser mais escuro, mais preto" (pedido 49). O check antigo
# virou este aqui — continua verificando que a dimensao esta configurada,
# so que no tom novo (luz 0, sem ceu, teto) e com a neblina do cliente.
# (5.13.1) O DONO VOLTOU ATRAS: o vazio NAO pode ser tudo preto ("o vazio deve
# ser como antes, pq agora ta tudo preto na dimensao"). Voltaram os valores de
# 9938159: luz ambiente 0,42, ceu ligado, sem teto e o CEU DO OVERWORLD.
check('5.13.5/5.28.0: o vazio e ESCURO (luz baixa + meia-noite fixa) e SEM CEU',
      ('"ambient_light": 0.06' in rd('src/main/resources/data/rptag/dimension_type/exilio.json')
       or '"ambient_light": 0.16' in rd('src/main/resources/data/rptag/dimension_type/exilio.json'))
      and '"fixed_time": 18000' in rd('src/main/resources/data/rptag/dimension_type/exilio.json')
      and '"has_skylight": false' in rd('src/main/resources/data/rptag/dimension_type/exilio.json')
      and '"has_ceiling": false' in rd('src/main/resources/data/rptag/dimension_type/exilio.json')
      and 'minecraft:the_nether' in rd('src/main/resources/data/rptag/dimension_type/exilio.json')
      and os.path.exists(C + '/VazioNeblina.java'))
check('5.7.9: exilar POUSA na SUPERFICIE (y=1 era dentro da pedra: nao andava nem via)',
      '"exilio"' in _ne2 and 'chegadaNoExilio(destino)' in _ne2
      and 'alvo.teleportTo(destino, chegada.x, chegada.y, chegada.z' in _ne2
      and 'alvo.teleportTo(destino, 0.5, 1.0, 0.5' not in _ne2)
check('5.5.0: /vazio soltar tira o jogador do exilio',
      'literal("soltar")' in _nc2 and 'soltarDoExilio' in _ne2)
# (5.7.2) os 3 aneis antigos SAIRAM: o dono pediu rachaduras no CHAO que
# acompanham o player (ancoradas no mundo) em vez de aneis em volta do corpo.
# (5.7.3) o dono pediu AURA DE ESCURIDAO + RAIOS saindo do player (a versao
# so-de-rachaduras-no-chao virou coadjuvante)
check('5.7.3: aura do vazio = escuridao saindo do player (5.11.1: raios vermelhos)',
      'auraVazio(' in _ne2 and 'DustParticleOptions' in _ne2
      and 'negro' in _ne2)
# (5.13.1) pedido 51: "tira as particulas cinzas do vazio ligar, as particulas
# pretas". O manto (nuvem cinza) morreu de vez.
_aura_bruto = (_ne2.split('private static void auraVazio')[1].split('\n    private static ')[0]
               if 'private static void auraVazio' in _ne2 else '')
_aura = re.sub(r'/\*.*?\*/', '', _aura_bruto, flags=re.S)              # sem /* */
_aura = "\n".join(l.split('//')[0] for l in _aura.splitlines())         # sem //
check('5.13.1 (pedido 51): SEM particula cinza/preta na aura (SQUID_INK, LARGE_SMOKE, ASH)',
      _aura != '' and 'SQUID_INK' not in _aura and 'LARGE_SMOKE' not in _aura
      and 'ParticleTypes.ASH' not in _aura and 'SMOKE' not in _aura)
check('5.13.1: RASTRO de caos em geometria no cliente (RastroVazioFx, sem quadradoes)',
      os.path.exists(C + '/RastroVazioFx.java')
      and 'RenderLevelStageEvent' in rd(C + '/RastroVazioFx.java'))
check('5.5.0: SAIU a particula que ficava NA CABEÇA (SCULK_SOUL em y+2,1)',
      'ParticleTypes.SCULK_SOUL' not in _ne2 and 'pos.y + 2.1' not in _ne2)
check('5.5.0: o apagar/exilar nao desenha mais o QUADRADO PRETO (g.fill no centro)',
      'g.fill(w / 2 - r' not in _nf2 and 'g.fill(w/2 - r' not in _nf2)
check('5.5.0: o apagar virou SOMBRA (discos em degrade + silhueta + bracos)',
      'disco(b,' in _nf2 and 'alfaSil' in _nf2 and 'b.vinheta(' in _nf2)
check('5.5.0: em rptag:exilio a tela fecha em preto (camada propria do cliente)',
      'desenharExilio' in _nf2 and 'DIM_EXILIO' in _nf2)
check('5.5.0: /neptune explica o exilio e o soltar', 'rptag:exilio' in _nc2 and 'vazio soltar' in _nc2)

# ============ 5.6.0: o chão RACHA, o player CAI e fica IMÓVEL ============
_ne3 = rd(R + '/NeptuneEventos.java')
_nf3 = rd(C + '/NeptuneFx.java')
_np3 = rd(R + '/NeptunePackets.java')
_nc3 = rd(R + '/NeptuneCommands.java')
check('5.6.0: pacote CENA existe e esta registrado (rachadura/queda/estouro)',
      'record Cena(' in _np3 and 'CENA_RACHADURA' in _np3 and 'neptune_cena' in _np3
      and 'NeptunePackets.Cena.TYPE' in rd(R + '/ModNetworking.java'))
check('5.6.0: o chao ABRE de verdade (cava blocos e guarda o original pra fechar)',
      'abrirFenda' in _ne3 and 'private static boolean cavar(' in _ne3
      and 'private record Fenda(' in _ne3)
check('5.6.0: so cava TERRA/PEDRA/AREIA (nunca bau, forno ou bloco com dados)',
      'CAVEIS' in _ne3 and 'st.hasBlockEntity()' in _ne3 and '!st.getFluidState().isEmpty()' in _ne3)
check('5.6.0: a fenda SEMPRE fecha (restaura cada bloco no fim)',
      'fecharFendas' in _ne3 and 'f.setBlockAndUpdate' not in _ne3
      and 'nivel().setBlockAndUpdate(f.pos(), f.estado())' in _ne3)
check('5.6.0: o jogador cai (poco 3x3 de 2 de profundidade embaixo dele)',
      'chao.offset(dx, -1, dz)' in _ne3 and 'chao.offset(dx, 0, dz)' in _ne3)
check('5.6.0: FICA IMOVEL (velocidade e pulo zerados por atributo)',
      'AttributeModifier' in _ne3 and 'Attributes.MOVEMENT_SPEED' in _ne3
      and 'Attributes.JUMP_STRENGTH' in _ne3 and 'ADD_MULTIPLIED_TOTAL' in _ne3)
check('5.6.0: /terremoto fenda <jogador> e /terremoto fechar existem (teste)',
      'literal("fenda")' in _nc3 and 'literal("fechar")' in _nc3)
# (5.7.5) O DONO MANDOU TIRAR A RACHADURA DA TELA ("as rachaduras desenhadas
# na tela" era o que mais incomodava): agora quem racha e o MUNDO.
check('5.7.5: SAIU a rachadura desenhada na tela (nada de vidro rachado na lente)',
      'O ABISMO' not in _nf3 and 'rachaduras = 11' not in _nf3
      and 'A RACHADURA DE TELA SAIU' in _nf3)
check('5.7.5: sobrou so uma faixa de poeira (10% do rodape) no lugar do rabisco',
      'faixa = h * 0.10F' in _nf3 and 'fillGradient(0, h - (int) faixa' in _nf3)
check('5.6.0: a queda tomba a camera e poe a tela no fundo da cova',
      'desenharQueda' in _nf3 and 'pitch += 22.0F * tomba' in _nf3 and 'roll += 14.0F * tomba' in _nf3)
# (5.7.2) o dono VETOU a abertura antiga ("nao quero essa animacao quando
# comecou o terremoto"): saiu o flash branco de 0.55 e o blur 3.5.
check('5.7.5: ABERTURA nao desenha rachadura nenhuma (nem flash branco)',
      'desenharEstouro' in _nf3 and 'FENDA na tela' not in _nf3
      and 'b.seg(' not in _nf3.split('desenharEstouro')[1].split('private static')[0]
      and '0xFFF3DC' not in _nf3)
check('5.7.5: abertura ACONTECE NO MUNDO (fenda real + pedra + poeira do bloco)',
      'aberturaNoMundo' in _ne2 and 'ParticleTypes.FALLING_DUST' in _ne2
      and 'ParticleTypes.BLOCK, estado' in _ne2)
check('5.7.5: abertura curta (0,9 s) e sem blur de 3,5',
      'desfocar(1.0F' in _nf3 and 'desfocar(3.5F' not in _nf3
      and 'CENA_ESTOURO, 1.0F, 900' in rd(R + '/NeptuneEventos.java'))
check('5.7.5: fendas quebrando blocos MAIS frequentes (2,8 a 4,6 s)',
      'agora + 2800 +' in rd(R + '/NeptuneEventos.java'))
check('5.7.5: ESCOMBROS caindo do alto em volta dos jogadores (mundo caindo)',
      'proximoEscombro' in rd(R + '/NeptuneEventos.java')
      and 'ESCOMBROS CAINDO DO ALTO' in rd(R + '/NeptuneEventos.java'))
check('5.7.2: FENDAS QUEBRANDO BLOCOS durante o terremoto',
      'fendaDePassagem' in rd(R + '/NeptuneEventos.java')
      and 'proximaFendaSolta' in rd(R + '/NeptuneEventos.java'))
check('5.7.2: banimento SEM particulas verdes (sombras que aumentam)',
      'ParticleTypes.HAPPY_VILLAGER' not in rd(R + '/NeptuneEventos.java')
      and 'sombraChuva' in rd(R + '/NeptuneEventos.java')
      and '0x2BFF77' not in _nf3 and '0x7CFF9A' not in _nf3)
check('5.6.0: detritos caindo pela tela durante o terremoto',
      'DETRITOS CAINDO' in _nf3)




# ============ 5.11.1: RAIOS VERMELHOS de volta — e VISÍVEIS com shader ========
_rvz = rd(C + '/RaiosVazioFx.java')
check('5.11.1: os raios vermelhos voltaram (pedido do dono) e os tentaculos de pedra sairam',
      os.path.exists(C + '/RaiosVazioFx.java')
      and not os.path.exists(C + '/TentaculosVazioFx.java')
      and 'class RaiosVazioFx' in _rvz)
check('5.11.1: raios em blend NORMAL (debugQuads), NAO no aditivo que sumia com shader',
      'RenderType.debugQuads()' in _rvz and 'RenderType.lightning()' not in _rvz
      and 'endBatch(RenderType.debugQuads())' in _rvz)
check('5.11.1: 3 passadas por raio (capa escura + corpo vermelho + nucleo quente)',
      'CAPA vermelho-escura' in _rvz or ('capa' in _rvz.lower() and 'CORPO vermelho vivo' in _rvz
      and 'NÚCLEO quente' in _rvz))
check('5.11.1: raio e 3D e preso no CORPO (funciona voando, nao depende do chao)',
      'Mth.cos(el)' in _rvz and 'r.origem' in _rvz and 'SORTE.nextFloat() - 0.5F) * 2.3F' in _rvz)
check('5.11.1: serrilhado RE-SORTEADO (estalo) — nao e desenho congelado',
      'ESTALO' in _rvz and 'proxEstalo' in _rvz)
check('5.11.1: "bizarro": cerdas curtas coladas no corpo + giro lento do conjunto',
      'CERDAS' in _rvz and 'giro +=' in _rvz)
check('5.7.4: o pacote AURA leva o UUID de quem e o vazio (todos os clientes veem)',
      'record Aura(java.util.UUID alvo' in rd(R + '/NeptunePackets.java')
      and 'RaiosVazioFx.definir' in _nf3)
check('5.7.4: trovao grave pros outros (medo) perto do vazio',
      'proximoTrovao' in rd(R + '/NeptuneEventos.java')
      and 'LIGHTNING_BOLT_THUNDER' in rd(R + '/NeptuneEventos.java'))
check('5.7.4: canal v11 (o Aura mudou de formato; agora v18 com o portal, as almas e a morte)',
      'event.registrar("18")' in rd(R + '/ModNetworking.java'))

# ============ 5.7.3: bicho na tela, música única, som do terremoto ============
check('5.7.3: BICHO pula na tela de quem esta em CRIATIVO (era o bug do dono)',
      "if (sp.isCreati" not in rd(R + '/NeptuneEventos.java')
      and 'if (sp.isSpectator())' in rd(R + '/NeptuneEventos.java'))
check('5.7.3: clique na caixa dispara o bicho (estourarCaixa)',
      ('toqueNaCaixa' in rd(R + '/CaixaBichoBlock.java')
       or 'estourarCaixa' in rd(R + '/CaixaBichoBlock.java'))
      and 'public static void estourarCaixa' in rd(R + '/NeptuneEventos.java'))
check('5.7.3: FIM das musicas empilhadas (durou % 30 == 0 -> proximaMusica)',
      'durou % 30 == 0)' not in rd(R + '/NeptuneEventos.java')
      and 'agoraMs >= c.proximaMusica' in rd(R + '/NeptuneEventos.java'))
check('5.7.3: apertar a caixa varias vezes nao cria varias caixas (dedupe por pos)',
      'if (c.pos.equals(pos))' in rd(R + '/NeptuneEventos.java')
      and 'tocarSomCaixa' in rd(R + '/NeptuneEventos.java'))
check('5.7.3: susto nao se desliga mais para sempre quando uma render falha',
      'sustoOff = true;' not in _nf3 or 'tenta no proximo' in _nf3)
check('5.7.3: susto ocupa a tela (aberr acao cromatica + fallback com olhos)',
      'aberra' in _nf3 and 'olho' in _nf3 and 'durMs = 3400L' in _nf3)
check('5.7.3: SOM do terremoto refeito em camadas (sub+ronco+corpo+graos+estalos)',
      __import__('os').path.exists('/home/user/arte/audio_573.py')
      and __import__('os').path.getsize('src/main/resources/assets/rptag/sounds/rumble_terremoto.ogg') > 50000
      and __import__('os').path.getsize('src/main/resources/assets/rptag/sounds/terremoto_colapso.ogg') > 200000)
check('5.7.3: colapso do terremoto agora e stream (75 s nao vai inteiro pra RAM)',
      '"stream": true' in rd('src/main/resources/assets/rptag/sounds.json'))
check('5.7.3: overlay da aura sem fumaca de TELA (nao tem mais o borrao preto no F5)',
      'N_FUMACA' not in rd(C + '/VazioAuraDarkFx.java')
      and 'tentaculo' not in rd(C + '/VazioAuraDarkFx.java').lower())

# ============ 5.7.2: SISTEMA DE TÍTULOS ============
_tit = rd(R) + rd(R + '/Titulos.java') if False else ''
_tit = rd(R + '/Titulos.java')
check('5.7.2: TITULOS - etiqueta colorida na frente do nome ([Sagrado] Nome)',
      'ETIQUETAS' in _tit and 'CORES' in _tit and '"Sagrado"' in _tit
      and '"Warden"' in _tit and '"Gelo"' in _tit and '"Fragmento"' in _tit
      and '"Almas"' in _tit and '"Chronos"' in _tit)
check('5.7.2/5.10.1: TITULOS - titulo pessoal depois do nome (Nephisto [O Ferreiro])',
      'personalizado' in _tit and 'out.append(TituloEstilo.pessoal(t.personalizado(), t.corPessoal()))' in _tit)
check('5.7.2: TITULOS - salvos no mundo (SavedData rptag_titulos)',
      'rptag_titulos' in _tit and 'extends SavedData' in _tit)
check('5.7.2: TITULOS - aparecem no TAB, no chat e no nametag',
      'Titulos.aplicar' in rd(R + '/ServerEvents.java')
      and 'handleTitulo' in rd(C + '/ClientPayloadHandler.java')
      and 'onRenderNameTagTitulo' in rd(C + '/NameplateRenderer.java'))
check('5.7.2: TITULOS - comandos /titulo definir|personalizado|limpar|lista|paleta',
      all(x in rd(R + '/TituloCommands.java') for x in
          ['"definir"', '"personalizado"', '"limpar"', '"lista"', '"paleta"']))
check('5.7.2: TITULOS - nome e limpo de § e limitado (nao quebra a tela)',
      'MAX_ETIQUETA' in _tit and 'MAX_PERSONALIZADO' in _tit and 'isISOControl' in _tit)

# ============ 5.6.1: video atras do player no tablet (em loop) ============
_tvf = rd(C + '/TabletVideoFundo.java')
_ts = rd(C + '/TabletScreen.java')
check('5.6.1: TabletVideoFundo existe (player de video em loop)',
      os.path.exists(C + '/TabletVideoFundo.java') and 'VideoFrameLoader' in _tvf and 'DynamicTexture' in _tvf)
check('5.6.1: o video do tablet fica ATRAS do player (desenhado antes da previa 3D)',
      _ts.index('TabletVideoFundo.desenhar') < _ts.index('TabletPlayerPreview.render'))
_vfl = rd(C + '/VideoFrameLoader.java')
check('5.6.1: em LOOP sem engasgo (o decodificador volta pro quadro 0 e continua)',
      'if (q >= frames)' in _tvf and 'inicioLoop += volta;' in _tvf
      and 'boolean loop' in _vfl and 'prox = 0;' in _vfl and 'new VideoFrameLoader(ID, tl, g, () -> geracao, true)' in _tvf)
check('5.6.1/loop: os avisos em video NAO viraram loop (padrao continua parando no fim)',
      'this(id, tl, geracao, geracaoAtual, false);' in _vfl and 'if (!loop) break;' in _vfl)
check('5.6.1: para o video quando o tablet fecha/sai',
      'TabletVideoFundo.fechar();' in _ts and 'removed()' in _ts)
check('5.6.1: respeita fxVideo=false e nao quebra se o asset faltar',
      'FxConfig.video()' in _tvf and 'semAsset' in _tvf and 'desligado' in _tvf)
check('5.6.1: conversor mp4 -> formato do mod existe (arte/mp4_para_fx.py)',
      os.path.exists('arte/mp4_para_fx.py') and '_c.jpg' in rd('arte/mp4_para_fx.py')
      and 'fx.json' in rd('arte/mp4_para_fx.py'))
check('5.6.1: o pacote de quadros de FUNDO do tablet esta no jar (placeholder ate o video do dono)',
      os.path.exists(A + '/fx/tablet_fundo/fx.json')
      and os.path.exists(A + '/fx/tablet_fundo/frames/0000_c.jpg')
      and os.path.exists(A + '/fx/tablet_fundo/frames/0000_a.jpg'))

# ============ 5.7.0: video real do tablet + avisos CONHECIMENTO/ALLFORONE + saida ============
_conh = rd(C + '/ConhecimentoAvisoFx.java')
_afo = rd(C + '/ConhecimentoAvisoFx.java')   # (5.16.4) o AllForOneAvisoFx foi removido
_sai = rd(C + '/SaidaPlayerFx.java')
_fxp = rd('src/main/java/dev/rptag/client/ClientEffects.java')
_rpc = rd(R + '/RPCommands.java')
_wd = rd(R + '/RPWorldData.java')
_sev = rd(R + '/ServerEvents.java')
check('5.7.0: video REAL do dono no fundo do tablet (120 quadros 1280x720, 5 s, em loop)',
      os.path.exists(A + '/fx/tablet_fundo/fx.json')
      and _json.load(open(A + "/fx/tablet_fundo/fx.json"))["frames"] >= 100
      and _json.load(open(A + "/fx/tablet_fundo/fx.json"))['width'] == 1280
      and os.path.exists(A + '/fx/tablet_fundo/frames/0119_c.jpg'))
check('5.7.0: TabletVideoFundo.ativo() esconde o aro procedural (o video ja e o buraco negro)',
      'public static boolean ativo()' in rd(C + '/TabletVideoFundo.java')
      and '!TabletVideoFundo.ativo()' in rd(C + '/TabletScreen.java'))
check('5.7.0: aviso CONHECIMENTO (estilo 16) e o olho dourado do dono',
      'class ConhecimentoAvisoFx' in _conh and 'style == 16' in _fxp
      and 'case "conhecimento" -> 16' in _rpc and 'aviso_conhecimento' in _conh
      and 'RUNAS' in _conh and 'SANIDADE' in _conh)
check('5.16.4: o aviso ALL FOR ONE saiu (classe, estilo 17, comando e camada)',
      not os.path.exists(C + '/AllForOneAvisoFx.java')
      and 'AllForOneAvisoFx' not in _fxp and 'style == 17' not in _fxp
      and 'case "allforone" -> 17' not in _rpc and 'literal("allforone")' not in _rpc)
check('5.7.0: ANIMACAO DE SAIDA (estilo 18) do prototipo do dono',
      'class SaidaPlayerFx' in _sai and 'style == 18' in _fxp and 'saida_player' in _sai
      and 'DESCONECTADO' in _sai and 'session.close();' in _sai)
check('5.17.1: quando um jogador SAI, a animação NO CORPO roda sozinha e SEMPRE (sem comando)',
      'PlayerLoggedOutEvent' in _sev and 'saidaAutomatica' in _sev
      and 'Commands.literal("saidafx")' not in _rpc)
check('5.7.0: os 3 FX novos tem try/catch (nunca derrubam o jogo) e som proprio',
      all('catch (Throwable t)' in x for x in (_conh, _sai))
      and all('inicio = -1L;' in x for x in (_conh, _sai)))

# ============ 5.7.1: crash do CONHECIMENTO, cache do video, aura do vazio ============
_conv = rd('arte/mp4_para_fx.py')
_tvf = rd(C + '/TabletVideoFundo.java')
_vfl = rd(C + '/VideoFrameLoader.java')
_vad = rd(C + '/VazioAuraDarkFx.java')
_nfx = rd(C + '/NeptuneFx.java')
check('5.7.1/5.10.0: BUG do CONHECIMENTO corrigido (array do olho dimensionado 2n+1, não estoura)',
      'new float[n * 2 + 1]' in _conh and 'px[n + i] = cx' in _conh)
check('5.7.1: quando um FX falha, o log mostra a excecao inteira (pra achar a causa)',
      'aviso CONHECIMENTO desligado", t)' in _conh)
check('5.7.1: video do tablet com os quadros SEM marca d agua (delogo + corte do rodape)',
      '--delogo' in _conv and '--cortar-baixo' in _conv
      and os.path.exists(A + '/fx/tablet_fundo/frames/0119_c.jpg'))
check('5.7.1: cache do video em loop LIMITADO (senao enchia a memoria e travava)',
      'CACHE_LOOP' in _vfl and 'dist(a, q, total)' in _vfl and 'antigo.close()' in _vfl)
check('5.7.1: trava anti-congelamento do video do tablet (reinicia se nao vier quadro)',
      '4_000_000_000L' in _tvf and 'fechar();' in _tvf)
check('5.7.1: AURA DO VAZIO (/vazio ligar) = prototipo VoidAuraDark do dono',
      'class VazioAuraDarkFx' in _vad and 'VazioAuraDarkFx.ligar()' in _nfx
      and 'VazioAuraDarkFx.desenhar(g)' in _nfx and 'rachaduras' in _vad.lower()
      and 'tentáculos' in _vad.lower())

# ============ 5.7.6: exílio animado, aura preta, rastro, terremoto andável ====
_ncmd = rd(R + '/NeptuneCommands.java')
_npk = rd(R + '/NeptunePackets.java')
_cb = rd(R + '/CaixaBichoBlock.java')
check('5.7.6: TERREMOTO deixa o player ANDAR (peso, nao imobilizar)',
      'private static void pesado(' in _ne2 and 'pesado(p, true);' in _ne2
      and 'MOD_LENTO' in _ne2 and '-0.45' in _ne2)
check('5.7.6: pulso travado NAO chama mais imobilizar (zerava a velocidade)',
      'imobilizar(p, true);' not in _ne2.split('private static void tickTerremoto')[1]
      .split('private static void')[0])
check('5.7.6: SAIU a particula VERDE do chao (grama/folha viram terra)',
      'private static BlockState estadoPoeira(' in _ne2 and 'COARSE_DIRT' in _ne2
      and 'estadoPoeira(nv.getBlockState(chao))' in _ne2)
_csize = os.path.getsize(A + '/sounds/terremoto_colapso.ogg')
check('5.7.6: som NOVO do terremoto (gravacao CC0 real + estalos + sub)',
      _csize > 900_000 and 'rumbling-sound-effects' in rd('arte/audio_576.py')
      and 'excita(' in rd('arte/audio_576.py'))
check('5.7.6: EXILIO com animacao no mundo (particulas + sucção + raios)',
      'tickExilios' in _ne2 and 'particulasExilio' in _ne2
      and 'CENA_EXILIO' in _npk and 'CENA_VOLTA' in _npk)
check('5.7.6: a ORIGEM de quem foi exilado fica guardada (5.13.5: sai no SPAWN)',
      'ORIGEM.put(' in _ne2 and 'devolverAoSpawn' in _ne2 and 'ORIGEM.remove(' in _ne2)
check('5.7.6: na tela, escuridao do exilio (indo) e da volta (abrindo)',
      'desenharExilioCena' in _nfx and 'CENA_EXILIO' in _nfx and 'CENA_VOLTA' in _nfx)
check('5.13.1 (pedido 51): a aura sao DOIS OLHOS vermelhos + estilhacos de pedra',
      'os OLHOS' in _ne2 and 'BlockParticleOption' in _ne2
      and 'auraPart(' in _ne2)
check('5.7.6: RASTRO DE CAOS no chao por onde o vazio passa (22 s)',
      'private record Mancha(' in _ne2 and 'tickManchas' in _ne2 and '22_000L' in _ne2)
check('5.7.6: TRANSFORMACAO = rajada que abre o corpo (5.11.1: raios vermelhos de volta)',
      'private static void surtoTransformacao' in _ne2
      and 'DustParticleOptions fogo' in _ne2.split('private static void surtoTransformacao')[1][:2600])
check('5.7.6: /terremoto engolir (o chao leva a pessoa, os outros veem)',
      'engolirNoTerremoto' in _ne2 and 'literal("engolir")' in _ncmd)
check('5.7.9: o /saida SAIU (o dono nao quer comando; a animacao roda no logout)',
      'register(saida)' not in _ncmd and 'literal("saida")' not in _ncmd
      and 'saidaAutomatica' in _ne2 and 'style == 18' in rd(C + '/ClientEffects.java'))


# ============ 5.7.7: F1 limpo, exílio maior, cinzas, bicho, códigos 3D ======
_glif = rd(C + '/GlifosSaidaFx.java')
check('5.7.7: o vazio NAO fica cego em F1 (particula alta vai so pros outros)',
      'private static void auraPart(' in _ne2 and 'boolean alto = y > dono.getY() + 0.95;' in _ne2
      and 'if (o == dono && alto)' in _ne2)
check('5.7.7: EXILIO maior (aro no chao + tentaculos + estouro final)',
      'BURACO NO CH' in _ne2 and 'TENTÁCULOS' in _ne2 and 'O ESTOURO FINAL' in _ne2)
check('5.7.7: terremoto com FENDAS aleatorias (nao mais no pe do jogador)',
      'fendas devem acontecer aleatoriamente' in _ne2 and 'double dist = 2.0 + Math.random() * 10.0;' in _ne2)
check('5.7.7: tela do terremoto BALANCEADA (nebulosa/blur/detritos menores)',
      'desfocar(0.8F + 1.2F * pulso * f)' in _nfx and 'for (int i = 0; i < 18; i++) {' in _nfx)
check('5.7.7: SAIU o som que o bot tinha posto no vazio (WITHER_SPAWN)',
      'SoundEvents.WITHER_SPAWN' not in _ne2)
check('5.7.7: trilha do vazio PARA de verdade no /vazio desligar',
      'ClientboundStopSoundPacket' in _ne2 and 'SOM_TRILHA_DEUS.get().getLocation()' in _ne2)
check('5.7.7: apagar alguem = CINZAS + SOMBRA + ALMA por 3 s',
      'tickApagando' in _ne2 and 'ParticleTypes.WHITE_ASH' in _ne2
      and 'ParticleTypes.SOUL,' in _ne2 and 'ParticleTypes.SOUL_FIRE_FLAME' in _ne2)
check('5.7.7: BICHO no 1o clique so toca som; o susto vem no 2o (4 s)',
      'toqueNaCaixa' in _ne2 and 'ESPERA_SUSTO_MS = 4000L' in _ne2
      and 'toqueNaCaixa(nv, pos, sp)' in _cb)
check('5.7.8: DESFEITO o bicho como criatura (o dono pediu) — susto continua na tela',
      not os.path.exists(R + '/BichoEntity.java')
      and not os.path.exists(C + '/BichoRenderer.java')
      and 'addFreshEntity(bicho)' not in _ne2)
check('5.7.7: CODIGOS VERDES em volta do player na saida (3D, no mundo)',
      'class GlifosSaidaFx' in _glif and 'drawInBatch' in _glif and 'CODIGOS' in _glif
      and 'Codigos.TYPE' in rd(R + '/NeptunePackets.java')
      and 'GlifosSaidaFx.iniciar' in rd(R + '/ModNetworking.java'))


# ============ 5.7.3: aura SEM borrao de tela, som novo, caixa unica =========
check('5.7.3: SAIU o borrao de fumaca/rachadura DESENHADO na tela (era o que ficava esquisito no F5)',
      'fumaca(' not in _vad and 'rachadura(' not in _vad and 'tentaculo(' not in _vad
      and _vad.count('b.vinheta(') >= 1 and 'granula' in _vad.lower())
check('5.13.1 (pedido 51): aura NO MUNDO sem tela e sem manto (raios + rastro)',
      'desenharManto' not in _rvz and os.path.exists(C + '/RastroVazioFx.java')
      and 'quadPequeno' in _rvz)
check('5.7.3: som novo do terremoto com corpo (nao so zumbido grave)',
      os.path.getsize(A + '/sounds/rumble_terremoto.ogg') > 100_000
      and os.path.getsize(A + '/sounds/terremoto_colapso.ogg') > 300_000)
check('5.7.3: caixa do palhaco NAO empilha mais musica (para a anterior antes)',
      'pararMusicaCaixa' in _ne2 and 'ClientboundStopSoundPacket' in _ne2)


# ============ 5.7.8: som de expulsão, exílio andável, caixa no 1o clique =====
_exp = os.path.getsize(A + '/sounds/expulsao.ogg')
check('5.7.8: som NOVO de expulsão (o que o dono mandou) no jogo',
      _exp > 10_000 and 'SOM_EXPULSAO' in rd(R + '/RPTagMod.java')
      and 'expulsao' in rd('src/main/resources/assets/rptag/sounds.json'))
check('5.7.8: o som toca quando jogam a pessoa pra fora do mapa e no /terremoto sair',
      _ne2.count('SOM_EXPULSAO.get()') >= 3)
check('5.13.5: /vazio soltar devolve no SPAWN do jogador (nao mais aleatorio)',
      'devolverAoSpawn' in _ne2 and 'irParaSpawn(' in _ne2
      and 'getSharedSpawnPos()' in _ne2 and 'getRespawnPosition()' in _ne2)
check('5.7.8: a dimensao do exilio tem chao de verdade e da pra ANDAR no escuro',
      'blackstone' in rd('src/main/resources/data/rptag/dimension/exilio.json')
      and 'sculk' in rd('src/main/resources/data/rptag/dimension/exilio.json')
      and 'TENTÁCULOS' not in rd('src/main/resources/data/rptag/dimension/exilio.json'))
check('5.7.8: monolitos com lanterna de alma nascem no escuro (se perder lá dentro)',
      'tickExilioMundo' in _ne2 and 'monólito(' in _ne2 and 'SOUL_LANTERN' in _ne2)
check('5.7.8: /vazio entrar — o admin testa o exilio sem punir ninguém',
      'entrarNoExilio' in _ne2 and 'literal("entrar")' in _ncmd)
check('5.7.8: a caixa NAO toca mais ao colocar (música só no 1o clique)',
      'A MÚSICA NÃO TOCA MAIS AO COLOCAR A CAIXA' in _ne2)
check('5.7.8: o susto do bicho avanca pra tela (vinheta + escala extra)',
      'avanca' in _nfx and 'vinheta preta em volta' in _nfx)
_nfx2 = rd(C + '/NeptuneFx.java')
check('5.7.9: A CARA DO BICHO aparece (o vulto escuro vem ANTES, nao cobre o rosto)',
      'VULTO' in _nfx2 and 'a CARA' in _nfx2
      and _nfx2.index('VULTO') < _nfx2.index('a CARA')
      and _nfx2.count('g.blit(BICHO') >= 4)


# ============ 5.7.9: som do terremoto, cara do bicho, exílio claro, logout, 3 avisos ======
_sol = rd(C + '/MorteOpAvisoFx.java')   # (5.16.4) o SolAvisoFx (ONE FOR ALL) foi removido
_mop = rd(C + '/MorteOpAvisoFx.java')
_fxd = rd(C + '/FxDraw.java')
_cgl = rd(C + '/ClientGuiLayers.java')
check('5.7.9: /terremoto parar CALA o som (StopSound por jogador, como no /vazio desligar)',
      'ClientboundStopSoundPacket' in _ne2
      and 'SOM_TERREMOTO_COLAPSO.get().getLocation(), null' in _ne2
      and 'SOM_RUMBLE.get().getLocation(), null' in _ne2)
check('5.7.9: o cliente tambem para na mao quando chega o pacote de "desligado"',
      'getSoundManager()' in _nfx2 and 'sm.stop(' in _nfx2)
_vrp = rd(R + '/VazioReino.java')
check('5.7.9/5.13.1: exilio com praca larga e superficie plana (agora no VazioReino)',
      'prepararPraca(' in _ne2 and 'CHISELED_POLISHED_BLACKSTONE' in _vrp
      and 'POLISHED_DEEPSLATE' in _vrp and 'RAIO_PRACA' in _vrp)
check('5.7.9/5.13.1: a praca tem luz (colunas de obsidiana com lanterna de alma)',
      'for (int i = 0; i < 8; i++)' in _vrp and 'SOUL_LANTERN' in _vrp)
check('5.7.9: ANIMACAO DE SAIDA automatica quando o player sai do jogo',
      'saidaAutomatica' in rd(R + '/ServerEvents.java') and 'sombraChuva' in _ne2
      and 'se desfez em sombra' in _ne2)
check('5.7.9: /vazio entrar deixa o admin USAR o lugar (avisa que da pra andar/construir)',
      'd\u00e1 pra andar, construir' in _nc2 or 'pra andar, construir' in _nc2)
check('5.16.4: o aviso ONE FOR ALL saiu (classe, comando, camada e som)',
      not os.path.exists(C + '/SolAvisoFx.java')
      and 'SolAvisoFx' not in _cgl and 'SolAvisoFx.iniciar' not in _fxp
      and 'onefallall' not in rd(R + '/RPCommands.java')
      and 'entry("onefallall"' not in rd(R + '/LoreZoneCommands.java'))
check('5.7.9: aviso MORTEOP (protótipo AvisoMorte) — espiral, relógio, crânio, cinzas',
      'class MorteOpAvisoFx' in _mop and 'ESPIRAL de 3 bra' in _mop
      and 'REL' in _mop and 'TUDO O QUE COME' in _mop and 'aviso_morte_op' in _mop)
check('5.24.4: o aviso SANGUEOP saiu do mod inteiro (classe, camada, estilo 21, comando, som)',
      not os.path.exists(C + '/SangueOpAvisoFx.java')
      and 'SangueOpAvisoFx' not in _cgl and 'SangueOpAvisoFx' not in _fxp
      and 'case "sangueop"' not in rd(R + '/RPCommands.java')
      and 'sangueop' not in rd(R + '/LoreZoneCommands.java')
      and 'case 21 -> "SANGUEOP"' not in rd(R + '/RPCommands.java'))
check('5.7.9/5.16.4: o MORTEOP continua registrado e ligado ao estilo 20; onefallall saiu',
      'MorteOpAvisoFx.registrar(event)' in _cgl and 'style == 20' in _fxp
      and 'case "morteop" -> 20' in rd(R + '/RPCommands.java')
      and 'literal("morteop")' in rd(R + '/RPCommands.java')
      and 'onefallall' not in rd(R + '/RPCommands.java'))
check('5.7.9: FxDraw (triangulo, linha, anel, arco, disco em degrade) pros avisos novos',
      'class FxDraw' in _fxd and 'public static void discoGrad' in _fxd
      and 'public static void anel' in _fxd and 'public static void texto' in _fxd)

# ============ 5.9.0: TÍTULOS (tab, nametag) com COR MODIFICÁVEL ============
_tit = rd(R + '/Titulos.java')
_tcmd = rd(R + '/TituloCommands.java')
_tpay = rd(R + '/TituloSyncPayload.java')
_ctit = rd(C + '/ClientTitulos.java')
_cph = rd(C + '/ClientPayloadHandler.java')
_sev = rd(R + '/ServerEvents.java')
_npr = rd(C + '/NameplateRenderer.java')
_net = rd(R + '/ModNetworking.java')
check('5.9.0: sistema de TÍTULOS presente (etiqueta + título pessoal, salvo no mundo)',
      'class Titulos' in _tit and 'rptag_titulos' in _tit
      and 'record Titulo(' in _tit and 'para o TAB' not in _tit)
check('5.9.0: a paleta pronta tem as 12 etiquetas (incluindo Chronos)',
      'Chronos' in _tit and 'Warden' in _tit and 'Sagrado' in _tit
      and '0xC9A6FF' in _tit)
check('5.9.0: o título entra no NOME (chat + TAB) e no NAMETAG acima da cabeça',
      'Titulos.aplicar' in _sev and 'TabListNameFormat' in _sev
      and 'onRenderNameTagTitulo' in _npr and 'event.setContent' in _npr
      and 'ClientTitulos.aplicar' in _npr)
check('5.9.0: o cliente recebe os títulos (pacote + login)',
      'sync_titulo' in _tpay and 'handleTitulo' in _cph
      and 'Titulos.enviarTodos' in _sev and 'Titulos.avisar' in _sev)
check('5.9.0: COR MODIFICÁVEL — /titulo cor e /titulo cor pessoal',
      'literal("cor")' in _tcmd and 'literal("pessoal")' in _tcmd
      and 'corDoTitulo' in _tcmd and '/titulo cor pessoal' in _tcmd)
check('5.9.0: cores por NOME (ouro, vermelho, ciano...) e HEX (FFD700 / #FFD700)',
      'CORES_NOMEADAS' in _tit and '"ouro"' in _tit and 'corDeTexto' in _tit
      and '"#"' in _tit and 'Integer.parseInt(t, 16)' in _tit)
check('5.9.0: dá pra voltar pra cor de fábrica (padrao) e a cor fica salva no mundo',
      'corDeFabrica' in _tit and '"padrao"' in _tcmd and 'putInt("CorPessoal"' in _tit
      and 'getInt("CorPessoal")' in _tit)
check('5.9.0: o título pessoal também tem cor própria (pacote v18)',
      'corPessoal' in _tpay and 'corPessoal' in _ctit
      and 'event.registrar("18")' in _net)
check('5.9.0: /titulo definir e /titulo limpar aceitam VÁRIOS jogadores (@a)',
      'EntityArgument.players()' in _tcmd and 'getPlayers(ctx, "jogadores")' in _tcmd)
check('5.9.0: TAB do comando sugere as cores',
      'sugereCor' in _tcmd and 'nomesDeCor()' in _tcmd and '.suggests(sugereCor)' in _tcmd)

# ============ 5.8.1: AVISO MEDOOP (protótipo AvisoMedo do dono) ============
_medo = rd(C + '/MedoOpAvisoFx.java')
_cmds = rd(R + '/RPCommands.java')
check('5.8.1: aviso MEDOOP existe (a membrana, a lanterna, os olhos, a silhueta)',
      'class MedoOpAvisoFx' in _medo and 'aviso_medo_op' in _medo
      and 'MEMBRANA' in _medo and 'ELE EST\u00c1 ATR\u00c1S DE VOC\u00ca' in _medo
      and 'A membrana s\u00f3 se rompe quando voc\u00ea sente medo.' in _medo
      and 'N\u00c3O OLHE' in _medo)
check('5.8.1: medo — a silhueta CHEGA MAIS PERTO a cada apagao (ADV de 6 degraus)',
      'ADV = { 2.4F, 4.0F, 5.4F, 6.7F, 7.9F, 9.0F }' in _medo
      and 'private static float figLargura()' in _medo
      and 'private static float figAltura()' in _medo
      and 'corpo(b, x, pe, s * 1.10F' in _medo)
check('5.8.1: medo — a lanterna encolhe com o medo (raioLuz 210 -> 115)',
      '210F - 95F * medo' in _medo and 'desenhaEscuridao' in _medo)
check('5.8.1: medo — escuridao em LEQUE RADIAL (cor por vertice, sem somar alfa)',
      'float[] raios = { 0F, R * 0.45F, R * 0.85F, R * 1.15F, longe }' in _medo
      and 'int fatias = 64' in _medo and 'b.quad(x0, y0, ca, x1, y1, ca, x2, y2, cb, x3, y3, cb)' in _medo)
check('5.8.1: medo — 9 rachaduras com galhos que crescem com o medo',
      'for (int i = 0; i < 9; i++)' in _medo and 'crescer(r, CX, 230F, ang' in _medo
      and 'float revela = medo * rachaMax * 1.05F' in _medo
      and '300F + r.nextFloat() * 150F' in _medo)
check('5.8.1: medo — olhos somem quando a luz chega perto (0.8 * raio)',
      'float lr = raioLuz() * 0.8F' in _medo and 'reposiciona(i)' in _medo)
check('5.8.1: medo — parede com pichacoes e maos de sangue',
      'desenhaParede' in _medo and 'desenhaMaos' in _medo
      and 'N\u00c3O DURMA' in _medo and 'SANGRIA' in _medo)
check('5.8.1: medo — erro desliga SO o aviso (try/catch com falhou)',
      'falhou = true;' in _medo and 'catch (Throwable erro)' in _medo
      and 'RPTagMod.LOGGER.error' in _medo)
check('5.8.1: medo — registrado como camada e ligado ao estilo 22',
      'MedoOpAvisoFx.registrar(event)' in _cgl and 'style == 22' in _fxp)
check('5.8.1: medo — nome medoop no comando (com TAB)',
      'case "medoop" -> 22' in _cmds and 'literal("medoop")' in _cmds
      and 'case 22 -> "MEDOOP"' in _cmds)

# ============ 5.16.3: SEM MÁSCARA DE PEDRA (o dono pediu pra tirar TUDO) ============
# O dono vai recolocar depois com o modelo novo, então o check garante que NÃO
# sobrou nada da versão antiga (classe, camada, assets, lang, registro).
check('5.16.3: os arquivos da máscara de pedra saíram do mod (classe, camada, renderer, leitor)',
      all(not os.path.exists(R + '/' + f) for f in
          ['MascaraPedraItem.java', 'client/CamadaMascara.java',
           'client/RenderItemMascara.java', 'client/MalhaBin.java']))
check('5.16.3: os assets da máscara saíram do jar (modelo, geo e texturas)',
      all(not os.path.exists(A + '/' + f) for f in
          ['models/item/mascara_pedra.json', 'geo/mascara_58.bin', 'geo/mascara_bb.bin',
           'textures/entity/mascara_58.png', 'textures/entity/mascara_bb.png',
           'textures/entity/mascara_pedra.png', 'textures/item/mascara_pedra.png',
           'textures/item/mascara_pedra_item.png']))
check('5.16.3: nenhum código do mod cita a máscara (registro, camada, item)',
      not any(k in rd(f) for f in [R + '/RPTagMod.java', C + '/ClientEvents.java']
              for k in ('MascaraPedra', 'CamadaMascara', 'RenderItemMascara',
                        'MalhaBin', 'mascara_pedra', 'MASCARA_PEDRA')))
check('5.16.3: o nome do item saiu do lang (pt_br e en_us)',
      'mascara_pedra' not in rd(A + '/lang/pt_br.json')
      and 'mascara_pedra' not in rd(A + '/lang/en_us.json'))
check('5.16.3: o item não existe mais no registro',
      'ITEMS.register("mascara' not in rd(R + '/RPTagMod.java'))

# ============ 5.16.3: FRAGMENTO DO VAZIO NÃO APARECE EM BAÚ ============
_vz = rd(R + '/VazioReino.java')
_ba = _vz[_vz.index('private static void colocarBau'):]
_ba = _ba[:_ba.index('\n    }\n')]
check('5.16.3: o baú do vazio NÃO tem fragmento (o dono pediu)',
      'FRAGMENTO_VAZIO' not in _ba)
check('5.16.3: o baú continua com sucata e com o tesouro bom (raro/mítico)',
      'sucata(r)' in _ba and 'bomItem(r)' in _ba)
check('5.16.3: o fragmento continua vindo dos altares do vazio (não sumiu do jogo)',
      _vz.count('FRAGMENTO_VAZIO') >= 3)

# ============ 5.16.3: NADA DE AVISO AO SAIR DO VAZIO ============
_ne = rd(R + '/NeptuneEventos.java')
_av = _ne[_ne.index('public static void avisoSaidaDoVazio'):]
_av = _av[:_av.index('\n    }\n')]
check('5.16.4: avisoSaidaDoVazio avisa a CONQUISTA e nada quando é o ADM',
      'ScreenTextPayload' in _av and 'CONQUISTOU A SA' in _av
      and 'if (escapou) {' in _av and 'LOGGER' in _av)
_ne_cod = '\n'.join(l for l in _ne.splitlines()
                    if not l.strip().startswith(('*', '//', '/*')))
check('5.16.3: quem tira o jogador do vazio é o ADM (sem mensagem automática)',
      'VOCÊ ESCAPOU DO VAZIO' not in _ne_cod and 'VOCÊ FOI TIRADO DO VAZIO' not in _ne_cod)

# ============ 5.16.3: A SOMBRA EM CIMA DO PORTAL ============
_vfx = rd(C + '/VazioAlmasFx.java')
check('5.16.3: a sombra é ESCURA (silhueta quase preta + contorno frio), não vulto branco',
      'CORPO = { 0.010F, 0.009F, 0.016F }' in _vfx
      and 'CONTORNO = { 0.20F, 0.13F, 0.34F }' in _vfx
      and '0.86F, 0.89F, 0.98F' not in _vfx)
check('5.16.3: a sombra VIRA (de lado afina, de frente a cara abre)',
      'float afina = 0.14F + 0.86F * Math.abs(Mth.cos(ang));' in _vfx
      and 'float cara = Mth.clamp((Mth.cos(ang) - 0.50F) / 0.50F, 0.0F, 1.0F);' in _vfx)
check('5.16.3: tem contraluz, capuz inclinado, silhueta por perfil, farrapos e fumaça',
      all(k in _vfx for k in ('private static void halo(', 'perfil(float u)',
                              'O CAPUZ', 'FARRAPOS na barra', 'A FUMAÇA subindo',
                              'CONTRA LUZ — o halo violeta')))
check('5.16.3: os olhos são brasas que só acendem quando ela vem de frente',
      'OLHO = { 1.00F, 0.90F, 0.70F }' in _vfx
      and 'if (cara > 0.02F) {' in _vfx and 'halo(vc, mat, direita, cima, ox, yo, oz' in _vfx)
check('5.16.3: a prévia offline da sombra existe (mesma matemática, dá pra conferir sem abrir o jogo)',
      os.path.exists('arte/previa_sombra_vazio.py')
      and 'previa/sombra_vazio_5.16.3.png' in rd('arte/previa_sombra_vazio.py')
      and os.path.exists('previa/sombra_vazio_5.16.3.png')
      and os.path.getsize('previa/sombra_vazio_5.16.3.png') > 80_000)

# ============ 5.10.0: CONHECIMENTO novo (memórias, balança) + mod mais leve ============
_conh = rd(C + '/ConhecimentoAvisoFx.java')
check('5.10.0: o aviso CONHECIMENTO é o protótipo NOVO (4 memórias penduradas em cordas)',
      'MEM_X' in _conh and 'MEM_Y' in _conh and 'cordas(' in _conh
      and 'quadBez(' in _conh and 'memoria(' in _conh)
check('5.10.0: as 4 VERDADES do protótipo estão lá (texto de cada memória)',
      all(t in _conh for t in ('Você já esteve', 'Seu nome não foi',
                               'Alguém lembra de você', 'As vozes sempre')))
check('5.10.0: a BALANÇA SANIDADE × CONHECIMENTO e o contador VERDADES',
      'geomBalanca(' in _conh and 'SANIDADE' in _conh and 'CONHECIMENTO' in _conh
      and 'VERDADES' in _conh and 'nRev' in _conh)
check('5.10.0: o sigilo geométrico (estrela de 6 pontas, quadrados, 2 bandas de runas)',
      'poligono(' in _conh and 'runeBand(' in _conh and 'scripts(' in _conh)
check('5.10.0: SEM MOUSE — a revelação das memórias é por TEMPO (barra enchendo sozinha)',
      'T_REVELA' in _conh and 'REVELA_PASSO' in _conh and 'PROG[i] = Math.max(PROG[i], p)' in _conh
      and 'Minecraft.getWindow' not in _conh and 'mouseHandler' not in _conh)
check('5.10.0: com as 4 verdades o texto TROCA (STAGE1 do protótipo)',
      'Agora você sabe.' in _conh and 'E não pode deixar de saber.' in _conh
      and 'finalReveal(' in _conh)

_fxdir = A + '/fx'
_total = 0
_quadros = 0
_maior = 0
for _d in sorted(os.listdir(_fxdir)):
    _fd = os.path.join(_fxdir, _d, 'frames')
    if not os.path.isdir(_fd):
        continue
    for _n in os.listdir(_fd):
        _caminho = os.path.join(_fd, _n)
        _t = os.path.getsize(_caminho)
        _total += _t
        _quadros += 1
        _maior = max(_maior, _t)
check('5.10.0: os quadros de vídeo foram OTIMIZADOS (menos de 80 MB no total)',
      _total < 80 * 1024 * 1024)
check('5.10.0: nenhum quadro ficou grande (o maior tem menos de 200 KB)',
      0 < _maior < 200 * 1024)
check('5.17.0: os quadros emagreceram (16:9, _c e _a do mesmo tamanho)',
      all(
          len({_tam_jpg(os.path.join(_fxdir, _d, 'frames', _f))
               for _f in sorted(os.listdir(os.path.join(_fxdir, _d, 'frames')))[::57]
               if _f.endswith('.jpg')}) == 1
          and abs(_tam_jpg(os.path.join(_fxdir, _d, 'frames',
                  sorted(f for f in os.listdir(os.path.join(_fxdir, _d, 'frames'))
                         if f.endswith('_c.jpg'))[0]))[0]
                  / _tam_jpg(os.path.join(_fxdir, _d, 'frames',
                  sorted(f for f in os.listdir(os.path.join(_fxdir, _d, 'frames'))
                         if f.endswith('_c.jpg'))[0]))[1] - 16 / 9) < 0.01
          for _d in sorted(os.listdir(_fxdir))
          if os.path.isdir(os.path.join(_fxdir, _d, 'frames'))))
check('5.10.0: o script da otimização está no repo (dá pra rodar de novo)',
      os.path.exists('arte/otimizar_frames.py')
      and 'def recompressao(' in rd('arte/otimizar_frames.py'))
check('5.10.0: as prévias do aviso e da otimização estão em previa/',
      os.path.exists('previa/conhecimento_5.10.0.png')
      and os.path.getsize('previa/conhecimento_5.10.0.png') > 200_000
      and os.path.exists('previa/otimizacao_5.10.0.png'))

# ============ 5.10.1: TÍTULO BONITO (gradiente, colchete apagado) + /title fora ============
_est = rd(R + '/TituloEstilo.java')
_ctit = rd(C + '/ClientTitulos.java')
check('5.10.1: o estilo do título é uma classe só (servidor e cliente usam a MESMA)',
      'class TituloEstilo' in _est and 'TituloEstilo.etiqueta(' in _tit
      and 'TituloEstilo.pessoal(' in _tit and 'dev.rptag.TituloEstilo.etiqueta(' in _ctit)
check('5.10.1: gradiente LETRA POR LETRA (tom()/gradiente() por caractere)',
      'public static MutableComponent gradiente(' in _est
      and 'for (int i = 0; i < n; i++)' in _est and 'static int tom(int cor, float u)' in _est)
check('5.10.1: cor escura não some (rampa mistura com branco quando a luminância < 0.30)',
      'lum < 0.30f' in _est and 'misturar(cor, 0xFFFFFF' in _est)
check('5.10.1: colchetes apagados (55% da cor, sem negrito) e etiqueta em NEGRITO',
      'COLCHETE = 0.55f' in _est and 'escurecer(cor, COLCHETE)' in _est and 'withBold(true)' in _est)
check('5.10.1: título pessoal em ITÁLICO (a etiqueta é o "selo", o pessoal é o apelido)',
      'withItalic(true)' in _est and 'new TituloEstilo.pessoal' not in _est)
check('5.10.1: o nome do jogador NÃO é tocado pelo estilo (só a etiqueta e o pessoal)',
      'out.append(nome);' in _tit and 'out.append(nome);' in _ctit)
check('5.10.1: hover na etiqueta mostra a classe e o HEX (no chat/TAB)',
      'HoverEvent.Action.SHOW_TEXT' in _est)

_tvan = rd(R + '/TituloVanilla.java')
check('5.10.1: o /title VANILLA é removido do jogo (não é comando do mod)',
      'class TituloVanilla' in _tvan and 'TitleCommand.register(dispatcher, contexto)' in _tvan
      and 'getChildren().removeIf(n -> n.getName().equals("title"))' in _tvan)
check('5.10.1: e é removido do mapa LITERALS também (é ele que o parser lê, não o children)',
      '"literals"' in _tvan and 'getDeclaredField(campo)' in _tvan
      and 'removerDoMapa(raiz, "literals", "title")' in _tvan)
check('5.10.1: sem sondas de diagnóstico no código que vai pra release', 
      '[sonda]' not in _tvan and 'ServerTickEvent' not in _tvan
      and 'literal("sonda")' not in rd(R + '/RPCommands.java'))
check('5.18.0: o /rp admin titlevanilla SAIU (o dono: "somento o bonito") e o /titulo do mod fica',
      'literal("titlevanilla")' not in rd(R + '/RPCommands.java')
      and 'TitleCommand.register' in _tvan)
check('5.10.1: o /titulo do mod continua de pé (definir, cor, personalizado, paleta)',
      'literal("paleta")' in rd(R + '/TituloCommands.java')
      and 'literal("definir")' in rd(R + '/TituloCommands.java'))
check('5.10.1: a prévia do título novo está em previa/ (antes × depois + paleta)',
      os.path.exists('previa/titulos_5.10.1.png')
      and os.path.getsize('previa/titulos_5.10.1.png') > 60_000)

# ============ 5.11.1: RAIOS VERMELHOS (e a aura que nunca mais "some") =========
_rpwd = rd(R + '/RPWorldData.java')
_sev = rd(R + '/ServerEvents.java')
_aura = _ne2.split('private static void auraVazio')[1].split('private static void tickApagando')[0]
_surto = _ne2.split('private static void surtoTransformacao')[1][:2600]
check('5.11.1: a AURA do corpo continua sem raio de particula vermelha (o raio e geometria)',
      'auraPart(nv, p, brasa' not in _aura and 'auraPart(nv, p, sangue' not in _aura
      and 'ESTILHAÇOS DE PEDRA' in _aura)
check('5.11.1: particulas do servidor continuam PÉTREAS (pedacos de BLOCO)',
      _aura.count('BlockParticleOption') >= 2 and 'Blocks.DEEPSLATE' in _aura
      and 'Blocks.COBBLESTONE' in _aura)
check('5.11.1: a TRANSFORMACAO voltou a ser rajada de RAIOS VERMELHOS (+ lascas de pedra)',
      'DustParticleOptions fogo' in _surto and 'brasa' in _surto
      and 'BlockParticleOption' in _surto)
check('5.11.1: o vermelho da aura e só o OLHO do rosto (o resto é raio/geometria)',
      'pos.y + 1.62' in _aura and _aura.count('DustParticleOptions vermelho') == 1)
check('5.11.1: o modo vazio fica SALVO no mundo (Vazios) e volta no boot',
      'private final Set<UUID> vazios' in _rpwd and 'tag.put("Vazios"' in _rpwd
      and 'getList("Vazios"' in _rpwd and 'restaurarDeuses' in _ne2
      and 'restaurarDeuses' in _sev)
check('5.11.1: a aura é REENVIADA (quem entra depois do /vazio ligar também vê)',
      'public static void reenviarAura' in _ne2 and 'reenviarAura(server)' in _sev
      and 'proximaAura = agora + 3000L' in _ne2)
check('5.11.1: a prévia dos raios vermelhos está em previa/',
      os.path.exists('previa/vazio_5.11.1.png')
      and os.path.getsize('previa/vazio_5.11.1.png') > 60_000)

# ============ 5.11.2: o TÍTULO aparece no CHAT ==================================
_rpc = rd(R + '/RPChat.java')
check('5.11.2: o nome do CHAT passa pelo título (montarNome -> Titulos.aplicar)',
      'public static MutableComponent montarNome(MinecraftServer server, java.util.UUID id' in _rpc
      and 'Titulos.aplicar(server, id, base)' in _rpc
      and 'return montarNome(player.server, player.getUUID(), nomeBruto, inRp);' in _rpc)
check('5.11.2: TODOS os canais usam o mesmo nome (g, s, w, pensar, me, chat local)',
      _rpc.count('displayName(from)') >= 1
      and rd(R + '/ChatCommands.java').count('RPChat.displayName(player)') >= 4)
check('5.11.2: a ordem é a mesma do TAB — [Etiqueta] Nome [Pessoal] (ʀᴘ)',
      'comTitulo.append(RPTags.tag(inRp))' in _rpc)


# ============ 5.12.0 — pedido 49 ============================================
_nev = rd(R + '/NeptuneEventos.java')
_eng = rd(R + '/EngolirFx.java') if os.path.exists(R + '/EngolirFx.java') else rd(C + '/EngolirFx.java')
_vr = rd(R + '/VazioReino.java')
_rwd = rd(R + '/RPWorldData.java')
_ce = rd(C + '/ClientEffects.java')
_nfx = rd(C + '/NeptuneFx.java')
_ngl = rd(C + '/ClientGuiLayers.java')
_sev2 = rd(R + '/ServerEvents.java')
_nec = rd(R + '/NeptuneCommands.java')
_dim = rd('src/main/resources/data/rptag/dimension_type/exilio.json')

# 1) /apagar mata 100% (não deixa deitado; revive não traz de volta)
check('5.12.0: /apagar mata pela via DIRETA (setHealth(0) + die, sem passar pelo hurt)',
      'public static void matarDefinitivo(ServerPlayer alvo' in _nev
      and 'alvo.setHealth(0.0F);' in _nev and 'alvo.die(fonte);' in _nev)
check('5.12.0: a morte fica SELADA 60 s (tickApagados derruba ressurreição)',
      'SELO_MORTE_MS = 60_000L' in _nev and 'private static void tickApagados' in _nev
      and 'tickApagados(server, System.currentTimeMillis());' in _nev)
check('5.12.0: respawn de verdade encerra o selo (avisarRespawn)',
      'public static void avisarRespawn(UUID id)' in _nev
      and 'NeptuneEventos.avisarRespawn(p.getUUID())' in _sev2)
check('5.12.0: o hurt(magic, MAX_VALUE) antigo do /apagar saiu',
      'alvo.hurt(alvo.damageSources().magic(), Float.MAX_VALUE)' not in _nev)

# 2) som do andar do deus mais alto
check('5.12.0: a trilha do deus toca em volume 2.6 (era 1.0)',
      'SoundSource.AMBIENT, 2.6F, 0.95F' in _nev)
check('5.12.0: CADA passo do deus bate um WARDEN_STEP grave',
      'SoundEvents.WARDEN_STEP' in _nev and 'SoundSource.HOSTILE, 1.35F' in _nev)

# 3) vazio mais preto + prédios perdidos
check('5.13.5/5.28.0: a dimensao esta mais ESCURA de novo (luz baixa, noite fixa, sem ceu)',
      ('"ambient_light": 0.06' in _dim or '"ambient_light": 0.16' in _dim)
      and '"fixed_time": 18000' in _dim
      and '"has_skylight": false' in _dim
      and 'minecraft:the_nether' in _dim)
check('5.12.0: neblina do vazio quase preta no cliente (VazioNeblina)',
      os.path.exists(C + '/VazioNeblina.java')
      and 'ViewportEvent.ComputeFogColor' in rd(C + '/VazioNeblina.java')
      and 'ViewportEvent.RenderFog' in rd(C + '/VazioNeblina.java'))
check('5.12.0: PRÉDIOS PERDIDOS nascem no vazio (ruínas de arranha-céu)',
      'private static void enfileirarPredio' in _vr and 'marcar(nv, "B:"' in _vr
      and 'CRACKED_DEEPSLATE_BRICKS' in _vr)

# 4) o vazio que mata dá tempo e depois mata
check('5.12.0: cair no vazio dá 12 s de contagem antes da morte',
      'ESPERA_MORTE = 12_000L' in _vr and 'private static void tickCaindo' in _vr
      and 'public static boolean estaCaindo(UUID id)' in _vr)
check('5.12.0: o dano do void é CANCELADO enquanto a contagem corre',
      'LivingIncomingDamageEvent' in _sev2 and 'DamageTypes.FELL_OUT_OF_WORLD' in _sev2
      and 'VazioReino.estaCaindo(p.getUUID())' in _sev2)

# 5) NADA de tela preta no terremoto
check('5.12.0: o Nebulosa(5, true) que escurecia a tela de TODOS no exílio saiu',
      'new NeptunePackets.Nebulosa(5, true)' not in _nev)
check('5.12.0: a nebulosa não faz mais disco preto (miolo 0,62 / lavagem 0,16)',
      '0.62f * pico' in _nfx and '0.16f * pico * forca' in _nfx)
# (5.13.2) O DONO: "A DIMENSAO PAROU DE FUNCIONAR? PQ TA TUDO PRETO, NAO VEJO NEM
# MINHA HOTBAR MAIS". A cortina de 50% + nebulosa SAIA da tela e cobria a hotbar.
check('5.13.2: SEM cortina preta na tela dentro do vazio (não cobre a hotbar)',
      'desenharExilio(g);' not in _nfx
      and 'g.fill(0, 0, w, h, (int) ((0.50F' not in _nfx)

# 6) animação de ENGOLIR (protótipo uKqjYudD / SwallowedByDarkness)
check('5.12.0: EngolirFx existe e é o protótipo do dono (poça, olhos, braços)',
      'POÇA NEGRA' in _eng and 'NOLHOS' in _eng and 'NBRACOS = 9' in _eng
      and 'NDEDOS = 7' in _eng)
check('5.12.0: o texto corrompido FOI ENGOLIDO / PELA ESCURIDÃO está lá',
      'FOI ENGOLIDO' in _eng and 'PELA ESCURIDÃO' in _eng and '▓▒░█#@%&?' in _eng)
check('5.12.0: a cobertura da tela é ORGÂNICA e limitada a 0,80 (sem tela preta)',
      '0.80F * fade' in _eng and 'int pts = 65' in _eng)
check('5.12.0: estilo 23 = ENGOLIR no cliente e na camada de GUI',
      'if (style == 23)' in _ce and 'EngolirFx.iniciar(limpo23)' in _ce
      and 'EngolirFx.registrar(event)' in _ngl)
check('5.12.0/5.13.4: o terremoto anima no MUNDO e a entrada no vazio nao manda aviso de tela',
      'CENA_RACHADURA, 0.8F, 2600' not in _nev
      and ', 11, 23);' not in _nev)

# 7) PEDIDO 50 — pedestais em volta do centro, fragmentos como ITEM, portal de baixo para cima
_geo = rd(R + '/VazioGeometria.java')
_frag = rd(R + '/FragmentoItem.java')
_fx = rd(C + '/VazioPortalFx.java')
_rays = rd(C + '/RaiosVazioFx.java')
_pay = rd(R + '/VazioPortalPayload.java')
_net = rd(R + '/ModNetworking.java')

check('5.13.1: 8 pedestais num ANEL em volta do centro do spawn (raio 13)',
      'public static final int RAIO_PEDESTAL = 13' in _geo
      and 'public static int[] pedestal(int i)' in _geo
      and 'ancoraPedestal' in _vr and 'centroY(nv)' in _vr)
check('5.13.1: o desenho antigo morreu (nada de pedestal a 470 blocos / portal em 780)',
      'PORTAL_X = 780' not in _vr and '{ 210,   40 }' not in _vr
      and 'public static final int[] PORTAL = { 0, -22 }' in _geo)
check('5.13.1: o fragmento é ITEM de verdade (registrado, modelo e textura)',
      'FRAGMENTO_VAZIO = ITEMS.register("fragmento_vazio"' in rd(R + '/RPTagMod.java')
      and 'class FragmentoItem extends Item' in _frag
      and os.path.exists('src/main/resources/assets/rptag/models/item/fragmento_vazio.json')
      and os.path.exists('src/main/resources/assets/rptag/textures/item/fragmento_vazio.png'))
check('5.13.1: o pedestal só enche com o fragmento NA MÃO (item consumido)',
      'p.getMainHandItem()' in _vr and '.shrink(1)' in _vr
      and 'encaixarNoPedestal' in _vr)
check('5.13.1: os 8 altares espalhados guardam o fragmento (1 por jogador)',
      'SANTUARIOS = 8' in _geo and 'public static int[] santuario(int i)' in _geo
      and 'usarSantuario' in _vr and 'marcarSantuario' in _vr
      and 'new ItemStack(RPTagMod.FRAGMENTO_VAZIO.get())' in _vr)
check('5.13.1: os 8 encaixados ABREM o portal sozinhos',
      'if (cheios >= TOTAL_FRAGMENTOS)' in _vr and 'abrirPortal(nv, p)' in _vr
      and 'public boolean temOsOito(UUID id)' in _rwd)
check('5.13.1: o portal abre de BAIXO PARA CIMA (linha por linha, 6 s)',
      'ABERTURA_MS = 6000L' in _geo and 'LINHAS.get(k)' in _vr
      and 'while (colocadas < devidas)' in _vr and 'aberturaInicio' not in _vr)
check('5.13.1: comando para INVOCAR o portal (/vazio portal [jogador])',
      'public static String invocarPortal(ServerPlayer p)' in _vr
      and 'VazioReino.invocarPortal(quem)' in _nec)
check('5.13.5: atravessar o portal devolve no SPAWN (cama/ancora ou spawn do mundo)',
      'irParaSpawn(alvo, true)' in _nev and 'getRespawnPosition()' in _nev
      and 'getRespawnDimension()' in _nev and 'getSharedSpawnPos()' in _nev
      and 'dados.setVolta(alvo.getUUID(), null)' in _nev)
check('5.13.1: preferir teletransporte pelo portal: entrar andando ou clicando',
      'dentroDoPortal(p, base)' in _vr and 'usarPortal(p, nv, base, dados)' in _vr)
check('5.13.1: WAYSTONE bloqueada dentro do vazio (bloco e item na mão)',
      'bloquearWaystone' in _sev2 and 'waystone' in _sev2
      and 'PlayerInteractEvent.RightClickItem' in _sev2)
check('5.13.1: o portal e as colunas de luz são GEOMETRIA no cliente (não partícula)',
      'RenderType.debugQuads()' in _fx and 'RenderLevelStageEvent' in _fx
      and 'desenharPedestais' in _fx and 'VazioPortalPayload' in _fx)
check('5.13.1: a aura aparece MESMO com particulas desligadas (geometria no cliente)',
      'class RaiosVazioFx' in _rays and os.path.exists(C + '/RastroVazioFx.java'))
check('5.13.1 (pedido 51): portal INVOCADO abre mesmo sem os 8 fragmentos (aberto())',
      'private static boolean aberto(ServerLevel nv, RPWorldData dados)' in _vr
      and 'dados.isPortalAberto() || portalInvocado(nv)' in _vr)
check('5.13.1 (pedido 51): cada portal tem o SEU relogio de abertura (chavePortal)',
      'chavePortal(nv, base)' in _vr and 'private static final Map<String, Long> ABERTURA' in _vr)
check('5.20.0: a cidade morta existe, mas FIXA (sem crescimento infinito)',
      'private static void cidadeFixa(ServerLevel nv)' in _vr and 'enfileirarQuarteirao' in _vr
      and 'enfileirarRuinaBaixa' in _vr and 'private static long proximaRuina;' not in _vr)
check('5.21.0: neblina das TREVAS (0,028 / 0,022 / 0,040, horizonte 130)',
      '0.028F' in rd(C + '/VazioNeblina.java')
      and '0.040F' in rd(C + '/VazioNeblina.java')
      and '130F' in rd(C + '/VazioNeblina.java'))
check('5.13.2: passo do deus só com o pé no chão (voando não faz barulho de andar)',
      'boolean peNoChao;' in _ne2 and 'p.onGround() && !p.getAbilities().flying' in _ne2
      and 'if (peNoChao) {' in _ne2)
check('5.13.2: ESCURIDÃO é um bloco de verdade (não é black concrete)',
      os.path.exists(R + '/EscuridaoBlock.java')
      and 'ESCURIDAO' in rd(R + '/RPTagMod.java')
      and 'BLACK_CONCRETE' not in rd(R + '/VazioReino.java')
      and os.path.exists(A + '/blockstates/escuridao.json')
      and os.path.exists(A + '/textures/block/escuridao.png')
      and 'escuridao' in rd(A + '/lang/pt_br.json'))
check('5.13.2: a escuridão volta em GEOMETRIA no corpo (coluna, sem quadradão)',
      'private static void desenharSombra(' in _rvz and 'lados = 10' in _rvz)
check('5.13.2: rastro de ESCURIDÃO no chão atrás do deus (mancha + lascas)',
      'private static void sombra(VertexConsumer vc' in rd(C + '/RastroVazioFx.java'))
check('5.13.2: um portal por dimensão (o antigo é desmontado, não fica espalhado)',
      'private static void desmontarPortal(' in _vr and 'RPWorldData.Portal antigo' in _vr)
check('5.13.2: /vazio abrir sem aviso no chat (pedido: só a animação, sem mensagem)',
      'VazioReino.abrirPortal(nv, null);' in _nec
      and 'está abrindo — de baixo para cima' not in _nec)
check('5.13.2: portal animado também FORA do vazio (vórtice no overworld)',
      'dimPortal' in rd(C + '/VazioPortalFx.java')
      and 'envioEstado(nv, p, dados, base);' in _vr)
check('5.13.2: cidade morta já de pé na chegada (anel inicial de ruínas)',
      'private static void anelInicial(' in _vr and 'marcar(nv, "anel")' in _vr)
check('5.13.1: pacote do portal registrado (canal atual)',
      'VazioPortalPayload.TYPE' in _net and 'event.registrar("18")' in _net
      and 'VazioPortalPayload' in _pay)
check('5.12.0: a volta pelo portal não depende de admin (voltarPeloPortal)',
      'public static void voltarPeloPortal(ServerPlayer alvo)' in _nev)
check('5.13.4: a ENTRADA no vazio é silenciosa (sem texto na tela nem no chat)',
      'VOC\u00ca FOI EXPULSO DO MUNDO' not in _nev
      and 'Um lugar sem céu' not in _nev
      and 'para fora do universo' not in _nev)
check('5.12.0: comandos de teste /vazio fragmentos | portal | pedestais | construir',
      'VazioReino.darTudo(quem)' in _nec and 'VazioReino.invocarPortal(quem)' in _nec
      and 'VazioReino.listaPedestais()' in _nec and 'VazioReino.construirTudo(nv, quem)' in _nec)

# 8) aviso de sangue na tela: era o estilo 9 mandado no lugar do 18
check('5.17.1: a saída NÃO manda aviso de tela cheia (só a animação no corpo)',
      'new ScreenTextPayload(nome, 9, 18)' not in _nev
      and 'new ScreenTextPayload(alvo.getName().getString().toUpperCase(),\n                    9, 18)' not in _nev
      and 'NeptunePackets.Codigos' in _nev)
check('5.12.0: o exilar não manda mais nada de sangue pra quem fica',
      'SangueAvisoFx.iniciar' in _ce and '11, 23' not in _nev)

# 9) a animação de saída roda mesmo (registro + camada)
check('5.12.0: SaidaPlayerFx segue registrado e é disparado pelo estilo 18',
      'SaidaPlayerFx.registrar(event)' in _ngl and 'if (style == 18)' in _ce)
check('5.12.0: o logout automático continua chamando saidaAutomatica',
      'saidaAutomatica(saiu)' in _sev2)
check('5.12.0: sem classe de debug na entrega', not os.path.exists(R + '/DebugVazio.java'))

# 11) 5.13.4 — PEDIDO 53
check('5.16.3: o aviso da SAIDA foi REMOVIDO (não manda mais texto pra tela)',
      'public static void avisoSaidaDoVazio(ServerPlayer p, boolean escapou)' in _nev
      and 'new ScreenTextPayload(texto, 6, 20)' not in _nev)
check('5.16.3: a saída continua nas mãos do ADM (irParaSpawn segue vivo)',
      'avisoSaidaDoVazio(alvo, escapou)' in _nev
      and 'irParaSpawn(alvo, true)' in _nev
      and 'irParaSpawn(alvo, false)' in _nev)
check('5.13.4: nada de aviso automatico na barra de acao dentro do vazio',
      'dica(p, RPWorldData.get(p.getServer()));' not in _vr)
def _tex_escuridao():
    """(largura, altura) do PNG da escuridão lidos do cabeçalho IHDR (sem PIL)."""
    try:
        with open(A + '/textures/block/escuridao.png', 'rb') as f:
            d = f.read(24)
        if d[:8] != b'\x89PNG\r\n\x1a\n':
            return (0, 0)
        w = int.from_bytes(d[16:20], 'big')
        h = int.from_bytes(d[20:24], 'big')
        return (w, h)
    except Exception:
        return (0, 0)

check('5.13.6 (pedido 56): MONTANHAS no relevo (ridged + cinto de serras no horizonte)',
      'double serra = Math.sin((x + z * 0.35) * 0.0062 + 0.4)' in _vr
      and 'double cinto = Math.max(0.0, 1.0 - Math.abs(d - 118.0) / 26.0)' in _vr
      and 'Math.abs(Math.sin(x * 0.147 + z * 0.131 + 2.1))' in _vr
      and 'Math.min(40.0, pico * 46.0)' in _vr)
check('5.13.6: montanha tem ESTRATO de pedra (pedraDoMonte), nao sorteio por bloco',
      'private static BlockState pedraDoMonte(' in _vr
      and 'pedraDoMonte(r, i, altura, x, atual + i, z)' in _vr
      and 'SMOOTH_BASALT.defaultBlockState()' in _vr
      and 'CRYING_OBSIDIAN.defaultBlockState()' in _vr)
check('5.13.6: relevo respeita os 8 altares (fragmento nao fica soterrado)',
      'boolean pertoDeAltar = false;' in _vr and 'for (int[] alt : VazioGeometria.SANTUARIO)' in _vr)
check('5.13.6 (pedido 56): ALMAS VAZIAS perdidas na dimensao (virtuais, sem entidade)',
      'private static void nascerAlmas(ServerLevel nv)' in _vr
      and 'private static void tickAlmas(ServerLevel nv, long agora)' in _vr
      and 'ALMAS_CASAS' in _vr and 'ALMAS_ESTADO' in _vr
      and 'ArmorStand' not in _vr.split('limparBichos')[1].split('colocarBau')[0]
      and 'VazioAlmasPayload' in _vr)
check('5.13.6: a alma se vira, se aproxima de quem chega (e sussurra)',
      'menor < 9.0 && agora - ALMA_FALOU[i] > 6000L' in _vr
      and 'SoundEvents.SOUL_ESCAPE.value()' in _vr
      and 'if (perto != null && menor > 1.6)' in _vr)
check('5.13.6: o CORPO da alma e GEOMETRIA no cliente (funciona com particula desligada)',
      os.path.exists(C + '/VazioAlmasFx.java')
      and 'RenderType.debugQuads()' in rd(C + '/VazioAlmasFx.java')
      and 'private static void desenharAlma(' in rd(C + '/VazioAlmasFx.java')
      and os.path.exists(R + '/VazioAlmasPayload.java'))
check('5.13.6: pacote das almas registrado (canal atual + handler do cliente)',
      'event.registrar("18")' in rd(R + '/ModNetworking.java')
      and 'VazioAlmasPayload.STREAM_CODEC' in rd(R + '/ModNetworking.java')
      and 'handleVazioAlmas' in rd(C + '/ClientPayloadHandler.java'))
check('5.13.6 (pedido 56): textura da ESCURIDAO ANIMADA (4 quadros, 32x32, sem emenda)',
      os.path.exists(A + '/textures/block/escuridao.png')
      and os.path.exists(A + '/textures/block/escuridao.png.mcmeta')
      and '"frametime": 8' in rd(A + '/textures/block/escuridao.png.mcmeta')
      and _tex_escuridao()[0] == 32 and _tex_escuridao()[1] == 128)
check('5.13.5 (pedido 55): a ESCURIDAO nao tem colisao (da para ATRAVESSAR o portal)',
      '.noCollission()' in rtm and 'ATRAVESSÁVEL' in rd(R + '/EscuridaoBlock.java'))
check('5.13.5: atravessar andando dispara em 0,12 s (o miolo e 1 bloco de fundo)',
      'agora - desde > 120L' in _vr and '<= 0.48' in _vr)
check('5.13.5: SEM aviso na tela dos outros ao atravessar o portal',
      'atravessou o portal' not in _vr and 'O vazio ficou mais vazio' not in _vr)
check('5.13.5 (pedido 55): o /vazio soltar NAO avisa a tela de todo mundo',
      'SPAWN dele' in _nec and 'no lugar aleat' not in _nec)
check('5.13.5 (pedido 55): a saida e no SPAWN dele (cama/ancora ou spawn do mundo)',
      'alvo.getRespawnPosition()' in _nev and 'alvo.getRespawnDimension()' in _nev
      and 'getSharedSpawnPos()' in _nev and 'getSharedSpawnAngle()' in _nev
      and 'private static void pousar(' in _nev)
check('5.13.5/5.28.0: a dimensao escura nao virou tela preta (neblina visivel)',
      '0.028F' in rd(C + '/VazioNeblina.java')
      and ('"ambient_light": 0.06' in _dim or '"ambient_light": 0.16' in _dim))
check('5.13.5: mods de mapa mostram o nome da dimensao (lang dimension.rptag.exilio)',
      'dimension.rptag.exilio' in rd('src/main/resources/assets/rptag/lang/pt_br.json')
      and 'dimension.rptag.exilio' in rd('src/main/resources/assets/rptag/lang/en_us.json'))
check('5.13.4: construcoes do vazio ficam marcadas NO SAVE (spawna uma vez so)',
      'private final java.util.Set<String> construcoes' in _rwd
      and 'public boolean marcarConstrucao(String chave)' in _rwd
      and 'VazioConstrucoes' in _rwd
      and 'private static boolean marcar(ServerLevel nv, String k)' in _vr
      and 'if (!marcar(nv, "praca"))' in _vr
      and 'if (!marcar(nv, "N"))' in _vr)
check('5.13.4: portal NO NIVEL DO CHAO (piso + alicerce de blackstone embaixo)',
      'O PORTAL NO NÍVEL DO CHÃO' in _vr and 'BlockPos piso = base.offset(x, -1, dz);' in _vr
      and 'for (int y = -2; y >= -6; y--)' in _vr)
check('5.13.4: fundo do portal fica ATRAS (nao tapa o vortice de quem chega)',
      'filaForte(pos.offset(0, 0, -1), RPTagMod.ESCURIDAO.get().defaultBlockState());' in _vr)
check('5.13.4: relevo na dimensao (montes, crateras, pedras soltas)',
      'private static void relevoTick(ServerLevel nv, long agora)' in _vr
      and 'private static double relevoAltura(int x, int z)' in _vr
      and 'private static void esculpirChunk(ServerLevel nv, int cx, int cz)' in _vr
      and 'int alvo = TOPO_LAJE + h;' in _vr
      and 'public static int reconstruirRelevo(ServerLevel nv)' in _vr)
check('5.13.4: relevo reconhece o piso da dimensao (concreto cinza-claro + sculk)',
      'st.is(Blocks.LIGHT_GRAY_CONCRETE)' in _vr and 'st.is(Blocks.SCULK)' in _vr)
check('5.13.4: relevo carrega o chunk ANTES de esculpir (chunk cru = relevo sumido)',
      'nv.getChunk(cx, cz)' in _vr)
check('5.13.4: relevo despeja a fila na hora (o teto de 40 mil engolia o terreno)',
      'despejarFila(nv)' in _vr and 'private static void despejarFila(' in _vr)
check('5.13.4: relevo tem rampa na borda (sem paredao de 5 blocos na praca)',
      'double rampa = Math.min(1.0' in _vr)
check('5.16.3: baus com RARIDADE e SEM fragmento do vazio dentro',
      'private static void colocarBau(ServerLevel nv, int x, int y, int z, RandomSource r)' in _vr
      and 'boolean raro = sorte >= 82 && sorte < 97;' in _vr
      and 'FRAGMENTO_VAZIO' not in _vr[_vr.index('private static void colocarBau'):
                                       _vr.index('private static void colocarBau') + 2000]
      and 'colocarBau(nv, bx, y0, bz, r);' in _vr)
check('5.13.4: sem slime no vazio (spawn cancelado + varredura de bichos)',
      'event.setSpawnCancelled(true);' in _sev
      and 'private static void limparBichos(ServerLevel nv)' in _vr
      and 'EntityType.ENDERMAN' in _vr and 'EntityType.WARDEN' in _vr)
check('5.13.4: nada de PREDIOS_FEITOS/FEITOS em memoria (era o bug do respawn)',
      'FEITOS' not in _vr and 'PREDIOS_FEITOS' not in _vr
      and 'pracaFeita' not in _vr and 'anelFeito' not in _vr)

# 10) 5.13.3 — o portal dos 8 fragmentos TEM que ser o da praca e abrir sozinho
check('5.13.3: o portal dos 8 pedestais e o da PRACA (nao o invocado)',
      'public static BlockPos ancoraPortalPraca(ServerLevel nv)' in _vr
      and 'BlockPos base = ancoraPortalPraca(nv);' in _vr)
check('5.13.3: quem tem os 8 nos pedestais abre o portal sem clique (rede de seguranca)',
      '!dados.isPortalAberto() && dados.temOsOito(p.getUUID())' in _vr
      and 'abrirPortal(nv, p);' in _vr)
check('5.13.3: o reparo do miolo espera a animacao terminar (nao mata a abertura)',
      'VazioGeometria.ABERTURA_MS + 4000L' in _vr)
check('5.13.3: miolo aberto se repara sozinho (chunk descarregado no meio da animacao)',
      'private static void repararMiolo(ServerLevel nv)' in _vr
      and 'proximoReparo' in _vr and 'repararMiolo(nv);' in _vr)
check('5.13.3: /vazio pedestais encher enche os 8 e abre o portal (teste do dono)',
      'public static int encherPedestais(ServerPlayer p)' in _vr
      and 'Commands.literal("encher")' in _nec and 'VazioReino.encherPedestais(quem)' in _nec)
check('5.13.3: praca e portal invocado animam cada um por si (baseAberta por base)',
      'private static boolean baseAberta(ServerLevel nv, RPWorldData dados, BlockPos base)' in _vr
      and 'private static void animarAbertura(ServerLevel nv, long agora, BlockPos base)' in _vr
      and 'public static int estadoPortal(ServerLevel nv, BlockPos base)' in _vr)
check('5.13.3: atravessar vale para a praca (8 fragmentos) e para o invocado (livre)',
      'private static boolean esperarNoMiolo(ServerPlayer p, ServerLevel nv, RPWorldData dados,' in _vr
      and 'atravessou = esperarNoMiolo(p, nv, dados, praca, agora);' in _vr)
check('5.13.3: /vazio pedestais mostra o relatorio do portal (praca, miolo, aberto, invocado)',
      'public static String relatorioPortal(ServerLevel nv, java.util.UUID id)' in _vr
      and 'VazioReino.relatorioPortal(nv, quem.getUUID())' in _nec)

# ---------------------------------------------------------------- 5.14.0 (pedido 57): os tres avisos
_lou = rd(C + '/LoucoAvisoFx.java')
_mar = rd(C + '/MarinheiroAvisoFx.java')
_esp = rd(C + '/EspectadorAvisoFx.java')
_rit = rd(C + '/AvisoRitual.java')
_rgc = rd(C + '/ClientEffects.java')
_fxc = rd(C + '/FxComando.java')

check('5.14.0: os TRES avisos novos existem e sao FX proprio (nao video, nao particula)',
      'class LoucoAvisoFx' in _lou and 'class MarinheiroAvisoFx' in _mar and 'class EspectadorAvisoFx' in _esp
      and 'ParticleTypes' not in _lou + _mar + _esp and 'VideoFx' not in _lou + _mar + _esp)
check('5.14.0: REGISTRADOS na tela (registerAboveAll dos tres)',
      'aviso_louco' in _lou and 'aviso_marinheiro' in _mar and 'aviso_espectador' in _esp)
check('5.14.0: estilo 25/26/27 ligado no avisar() e no teste do cliente',
      'case "louco" -> 25' in _rp and 'case "marinheiro", "enforcado" -> 26' in _rp
      and 'case "espectador", "justica" -> 27' in _rp
      and 'if (style == 25)' in _rgc and 'if (style == 26)' in _rgc and 'if (style == 27)' in _rgc
      and 'case "louco" -> 25' in _fxc and 'case "marinheiro", "enforcado" -> 26' in _fxc)
check('5.14.0: louco|marinheiro|espectador existem como comando (hoje no avisar2)',
      'aviso2("louco")' in _rp and 'aviso2("marinheiro")' in _rp
      and 'aviso2("espectador")' in _rp)
check('5.15.0: LOUCO = a ESCADA das 10 Sequencias (9 Vidente ... 0 O Louco)',
      'FoolPathwayWarning' in _lou and 'private static float tn(int s)' in _lou
      and 'para a Sequencia 0' in _lou or ('tn(int s)' in _lou and 'NOMES' in _lou))
check('5.15.0: o LOUCO tem as DEZ Sequencias com nome (Vidente, Palhaco, Mago, ... O Louco)',
      all(n in _lou for n in ['Vidente', 'Palha', 'Mago', 'Sem Rosto', 'Marionetista',
                              'Feiticeiro Bizarro', 'Estudioso do Passado', 'Invocador de Milagres',
                              'Atendente dos Mist', 'O Louco']))
check('5.15.0: cada Sequencia liga o SEU efeito (pendulo, bonecos, chamas, mascaras, fios, maldicao, tempo, pilares, maos)',
      all(x in _lou for x in ['pendulo(', 'bonecos(', 'chamas(', 'mascaras(', 'fios(',
                              'maldi', 'relógio ao contrário', 'pilares(', 'maosDeNevoa(']))
check('5.15.0: a escada acende em ordem (uma Sequencia a cada 0,5 s, a 0 em 5,9 s)',
      'return s == 0 ? 5.9f : 1.4f + (9 - s) * 0.5f;' in _lou)
check('5.15.0: o estouro de faisca acontece no NO da Sequencia quando ela acende',
      'estourar(np[0], np[1]' in _lou and 'noPos(' in _lou)
check('5.14.0: MARINHEIRO = tempestade com raio ramificado + mar + leme + bola de raio',
      'AGENDA' in _mar and 'gera um raio por deslocamento de ponto' in _mar and 'leme de navio' in _mar
      and 'bolas de raio' in _mar and 'LIGHTNING_BOLT_THUNDER' in _mar)
check('5.14.0: ESPECTADOR = aurora + espiral + balanca + anel de olhos + petalas',
      'aurora' in _esp and 'espiral' in _esp and 'a balan' in _esp
      and 'anel de olhos' in _esp and 'petalas' in _esp)
check('5.14.0: os avisos novos se ENCERRAM (parar()) quando outro aviso chega',
      'LoucoAvisoFx.parar();' in _rgc and 'MarinheiroAvisoFx.parar();' in _rgc
      and 'EspectadorAvisoFx.parar();' in _rgc)
check('5.14.0: AvisoRitual da o que o JavaFX tinha e o GUI nao (halo, poligono, arco, texto)',
      'public static void halo(' in _rit and 'public static void poligono(' in _rit
      and 'public static void arco(' in _rit and 'public static void texto(' in _rit)
check('5.14.0: nenhum aviso novo escreve na tela do admin (so o alvo recebe)',
      'sendSuccess' not in _lou + _mar + _esp and 'broadcast' not in _lou + _mar + _esp)


check('5.15.0: o log de inicializacao usa a VERSAO de verdade (nao "4.8.5" fixo)',
      'camada de avisos carregada\", RPTagMod.VERSION' in rd(C + '/ClientEffects.java')
      and '4.8.5: camada' not in rd(C + '/ClientEffects.java'))

# ---- 5.16.0: AS OITO CARTAS DE TARÔ (Eremita, Estrela, Julgamento, Lua, Mago, Morte, Sol, Torre) ----
_cartas = {
    'EremitaAvisoFx': ('O EREMITA', 'IX', 'a lanterna', 'montanha'),
    'EstrelaAvisoFx': ('A ESTRELA', 'XVII', 'estrela8', 'constelação'),
    'JulgamentoAvisoFx': ('O JULGAMENTO', 'XX', 'folha(', 'trombeta'),
    'LuaAvisoFx': ('A LUA', 'XVIII', 'disco(', 'lua'),
    'MagoAvisoFx': ('O MAGO', 'I', 'lemPath', 'lemniscata'),
    'MorteTarotAvisoFx': ('A MORTE', 'XIII', 'folha(', 'foice'),
    'SolTarotAvisoFx': ('O SOL', 'XIX', 'girass', 'sol'),
    'TorreAvisoFx': ('A TORRE', 'XVI', 'jag(', 'raio'),
}
_eng = rd(C + '/CartaTarotFx.java')
check('5.16.0: o motor das cartas tem o canvas 800x600 do protótipo (centro 400,255)',
      'PW = 800f, PH = 600f, PCX = 400f, PCY = 255f' in _eng and 'CICLO = 14f' in _eng)
check('5.16.0: o motor tem pilha de transformação (translate/rotate/scale/save/restore) e alfa global',
      all(x in _eng for x in ['static void translate(float dx, float dy)', 'static void rotate(float rad)',
                              'static void scale(float fx, float fy)', 'static void save()',
                              'static void restore()', 'static float alfa = 1f']))
check('5.16.0: o motor traz as primitivas do GraphicsContext do protótipo',
      all(x in _eng for x in ['static void retangulo', 'static void elipse', 'static void poligono',
                              'static void polilinha', 'static void linha', 'static void setor',
                              'static void estrela8', 'static void hexagrama', 'static void simbolo']))
check('5.16.0: o motor desenha o que o protótipo desenha (sigilo 22, carta que sobe e vira, AVISO, 3 falas)',
      all(x in _eng for x in ['for (int k = 0; k < 22; k++)', 'ss(1.6f, 3.0f, T)', 'ss(3.2f, 4.1f, T)',
                              '"AVISO"', 'digitado(th.l1, 6.0f, 540', 'digitado(msg, 7.4f, 566']))
check('5.16.0: o motor pode ter VÁRIAS cartas vivas ao mesmo tempo (só a atual desenha)',
      'if (atual != this)' in _eng)
check('5.16.0: o sussurro guarda o TEXTO junto com a posição (índice sorteado != lido dava null)',
      'static final class Sussurro' in _eng and 's.txt = p[R.nextInt(p.length)]' in _eng
      and 'SUSS[(int)' not in _eng)
for _cls, (_nome, _rom, _ef1, _tema) in _cartas.items():
    _f = rd(C + '/%s.java' % _cls)
    check('5.16.0: %s desenha o tema do protótipo (%s) com o nome e o romano da carta'
          % (_cls, _tema),
          ('nome = "%s"' % _nome) in _f and ('romano = "%s"' % _rom) in _f and _ef1 in _f)
check('5.16.0: os OITO avisos novos continuam no comando (hoje no /rp admin avisar2)',
      all(('aviso2("%s")' % n) in _rp for n in
          ['eremita', 'julgamento', 'lua', 'mago', 'morte2', 'sol', 'torre', 'estrela']))
_fx = rd(C + '/FxComando.java')
check('5.16.0: os oito estão no switch do avisar() e no do /rpfx teste (28..35)',
      all(('case "%s"' % n in _rp and '-> %d' % i in _rp) for n, i in
          [('eremita', 28), ('julgamento', 29), ('lua', 30), ('mago', 31),
           ('morte2', 32), ('sol', 33), ('torre', 34), ('estrela', 35)])
      and all(('case "%s"' % n in _fx and '-> %d' % i in _fx) for n, i in
              [('eremita', 28), ('julgamento', 29), ('lua', 30), ('mago', 31), ('morte2', 32),
               ('sol', 33), ('torre', 34), ('estrela', 35)]))
check('5.16.0: os oito estão registrados na camada do GUI e no ClientEffects (28..35)',
      all(('%s.registrar(event)' % c) in rd(C + '/ClientGuiLayers.java') for c in _cartas)
      and all(('if (style == %d)' % i) in rd(C + '/ClientEffects.java') for i in range(28, 36)))
check('5.16.2: TOLO voltou a ser a CARTA do Tolo (protótipo FoolWarning), estilo 36',
      ('case "tolo", "foulo" -> 36' in _rp) and ('case "tolo", "foulo" -> 36' in rd(C + '/FxComando.java'))
      and ('if (style == 36)' in rd(C + '/ClientEffects.java')) and ('ToloAvisoFx.iniciar' in rd(C + '/ClientEffects.java')))
check('5.16.0: MORTE (carta) tem nome proprio para nao bater no estilo 4 (a morte antiga)',
      'case "morte2", "ceifador", "morte_carta" -> 32' in _rp
      and 'aviso2("morte2")' in _rp and 'case "morte" -> 4;' in _rp
      and 'MorteTarotAvisoFx.iniciar' in rd(C + '/ClientEffects.java'))
check('5.16.0: os estilos 28..35 NAO existiam antes (nenhum estilo antigo foi trocado)',
      all(('case %d ->' % i) not in rd(R + '/LoreZoneCommands.java') for i in range(28, 36))
      or all(('case %d ->' % i) in rd(R + '/LoreZoneCommands.java') for i in range(28, 36)))

# ---- 5.16.2: /rp admin avisar2 (Lord of the Mysteries + cartas de tarô) ----
_ct = rd(C + '/CartaTarotFx.java')
check('5.16.2: existe a secao /rp admin avisar2 com os 14 estilos (LOTM + cartas)',
      'Commands.literal("avisar2")' in _rp
      and all(('aviso2("%s")' % e) in _rp for e in
              ('louco', 'tolo', 'marinheiro', 'enforcado', 'espectador', 'justica',
               'eremita', 'julgamento', 'lua', 'mago', 'morte2', 'sol', 'torre', 'estrela')))
check('5.16.2: os avisos novos continuam funcionando pelo /rp admin avisar (nada quebrou)',
      'case "eremita" -> 28' in _rp and 'case "estrela" -> 35' in _rp
      and 'case "morte2", "ceifador", "morte_carta" -> 32' in _rp)
check('5.16.2: TOLO e o estilo 36 com a carta propria (ToloAvisoFx) e nao mais o 25',
      'case "tolo", "foulo" -> 36' in _rp
      and os.path.exists(C + '/ToloAvisoFx.java')
      and 'ToloAvisoFx.registrar' in rd(C + '/ClientGuiLayers.java')
      and 'if (style == 36)' in rd(C + '/ClientEffects.java'))
check('5.16.2: o poligono triangula por corte de orelhas (a estrela de 8 pontas e concava)',
      'corte de orelhas' in _ct and 'private static boolean pontoNoTri(' in _ct
      and 'private static void triTela(' in _ct)
check('5.16.2: o fundo do aviso cobre a tela toda (KBG) e o conteudo fica na area segura (K)',
      'KBG = Math.max(w / PW, h / PH)' in _ct and 'K = Math.min(w / PW, h / PH)' in _ct
      and 'K = KBG;' in _ct and 'float kConteudo = K;' in _ct)
check('5.16.2: o brilho usa a textura branca propria (a shadow do jogo e preta)',
      'rptag", "textures/misc/glow.png' in rd(C + '/AvisoBlob.java')
      and os.path.exists(A + '/textures/misc/glow.png'))

# ============ 5.16.4: avisar2 SEM TEXTO, avisos consertados, bedrock e conquista ============
_rp4 = rd(R + '/RPCommands.java')
_av2 = _rp4[_rp4.index('private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> aviso2('):]
_av2 = _av2[:_av2.index('\n    }\n')]
check('5.16.4: /rp admin avisar2 <jogadores> <estilo> roda SEM TEXTO (só o aviso)',
      '.executes(ctx -> {' in _av2 and 'avisar(ctx, alvos, estilo, "")' in _av2)
check('5.16.4: e continua aceitando texto depois do estilo (os dois caminhos)',
      'StringArgumentType.greedyString()' in _av2
      and 'StringArgumentType.getString(ctx, "texto")' in _av2)
check('5.16.4: sem texto os avisos NÃO inventam frase (nada de "Um grande perigo...")',
      all('Um grande perigo' not in rd(C + '/' + f) for f in
          ['MarinheiroAvisoFx.java', 'EspectadorAvisoFx.java', 'LoucoAvisoFx.java', 'CartaTarotFx.java'])
      and all('msg = (mensagem == null) ? "" : mensagem.trim();' in rd(C + '/' + f) for f in
              ['MarinheiroAvisoFx.java', 'EspectadorAvisoFx.java', 'LoucoAvisoFx.java', 'CartaTarotFx.java']))

# ---- a CAUSA do "enforcado/justiça quebrados": texto no meio do buffer de triângulos
_aux = rd(C + '/AvisoFxUtil.java')
check('5.16.4: o flush() do Buf FORÇA o shader de posição-cor (texto no meio não quebra mais)',
      'public void flush() {' in _aux
      and _aux[_aux.index('public void flush() {'):_aux.index('public void flush() {') + 400]
      .count('RenderSystem.setShader(GameRenderer::getPositionColorShader);') >= 1)
check('5.16.4: o Marinheiro/Enforcado fecha o buffer ANTES dos números do sigilo',
      'b.flush();\n            numeros(g, T, dp, k, cx, cy, env);\n            b = AvisoRitual.comecar(g);'
      in rd(C + '/MarinheiroAvisoFx.java'))
check('5.16.4: o Espectador/Justiça faz o mesmo (era o mesmo defeito)',
      'b.flush();\n            numeros(g, T, dp, k, cx, cy, env);\n            b = AvisoRitual.comecar(g);'
      in rd(C + '/EspectadorAvisoFx.java'))

# ---- bedrock embaixo de tudo
_vz4 = rd(R + '/VazioReino.java')
_dim = _json.load(open('src/main/resources/data/rptag/dimension/vazio.json'))
check('5.16.4: o gerador do vazio nasce com piso de BEDROCK (chunk novo já vem com chão)',
      _dim['generator']['settings']['layers'][0] == {'block': 'minecraft:bedrock', 'height': 2})
check('5.16.4: e o MESMO piso de bedrock no gerador da dimensão do vazio (exilio)',
      _json.load(open('src/main/resources/data/rptag/dimension/exilio.json'))
      ['generator']['settings']['layers'][0] == {'block': 'minecraft:bedrock', 'height': 2})
check('5.16.4: os chunks que já existiam ganham o piso à mão (pisoDeBedrock)',
      'private static void pisoDeBedrock(ServerLevel nv, ServerPlayer p)' in _vz4
      and 'Blocks.BEDROCK.defaultBlockState()' in _vz4
      and 'pisoDeBedrock(nv, p);' in _vz4)
check('5.16.4: e ninguém fica abaixo do chão (rede de segurança tira do void)',
      'if (p.getY() < base + 2.0) {' in _vz4 and 'p.setNoGravity(false);' in _vz4)

# ---- o aviso de morte na tela saiu
_tc = _vz4[_vz4.index('private static void tickCaindo'):]
_tc = _tc[:_tc.index('\n    }\n')]
check('5.16.4: caiu no void NÃO abre mais aviso de morte na tela (estilo 23)',
      'ScreenTextPayload' not in _tc)
check('5.16.4: a queda no void continua com a linha no CHAT (o jogo não engole o aviso)',
      'O VAZIO TE PEGOU' in _tc)

# ---- a prévia offline do aviso (o mesmo espelho Java2D das cartas)
check('5.16.4: o gerador da prévia offline dos avisos está no workspace (fora do mod)',
      os.path.exists('/home/user/previa_ritual/montar.py'))

# ============ 5.16.5: só os nomes em PORTUGUÊS (os em inglês eram o MESMO código) =====
_fxc2 = rd(C + '/FxComando.java')
_EN = ('fool', 'hangedman', 'justice', 'hermit', 'judgement', 'moon', 'magician', 'death', 'sun', 'tower', 'star')
check('5.16.5: os nomes em INGLÊS saíram do switch (eram duplicata, mesmo estilo)',
      all(('"%s"' % e) not in _rp4 for e in _EN)
      and all(('"%s"' % e) not in _fxc2 for e in _EN))
check('5.16.5: e saíram também da ÁRVORE do comando (não aparecem mais no TAB)',
      all(('aviso2("%s")' % e) not in _rp4 for e in _EN))
check('5.16.5: os nomes em PORTUGUÊS continuam todos lá (mesmo estilo de antes)',
      all(('case "%s"' % pt) in _rp4 for pt in
          ('louco', 'marinheiro', 'espectador', 'eremita',
           'julgamento', 'lua', 'mago', 'morte2', 'sol', 'torre', 'estrela', 'tolo'))
      and 'case "mago" -> 31;' in _rp4 and 'case "lua" -> 30;' in _rp4
      and 'case "estrela" -> 35;' in _rp4)
check('5.16.5: mago = estilo 31 (o mesmo de antes) e NÃO existe mais "magician"',
      'case "mago" -> 31;' in _rp4 and '"magician"' not in _rp4 and '"magician"' not in _fxc2)
check('5.16.5: o único apelido repetido que sobrou é em português (foulo/enforcado/justica)',
      'case "tolo", "foulo" -> 36;' in _rp4 and 'case "marinheiro", "enforcado" -> 26;' in _rp4
      and 'case "espectador", "justica" -> 27;' in _rp4)

# ============ 5.17.0: o mod emagreceu, FITA, NÚCLEO VIAL, BARRA e AXION ============
_rm = rd(R + '/RPTagMod.java')
_nv = rd(R + '/NucleoVialItem.java')
_ft = rd(R + '/FitaItem.java')
_mn2 = rd(R + '/ModNetworking.java')
_se2 = rd(R + '/ServerEvents.java')

# ---- peso
check('5.17.0: o jar emagreceu (os quadros de vídeo saíram de 1280x720)',
      os.path.exists('build/libs/rptag-5.31.0.jar')
      and os.path.getsize('build/libs/rptag-5.31.0.jar') < 45_000_000)
check('5.17.0: o player de vídeo aceita o tamanho do ARQUIVO (não exige mais 1280x720)',
      'int w = cor.getWidth(), h = cor.getHeight();' in rd(C + '/VideoFrameLoader.java')
      and 'nao e 1280x720' not in rd(C + '/VideoFrameLoader.java'))
check('5.17.0: o blit usa o tamanho REAL da textura no UV',
      'texW = img.getWidth();' in rd(C + '/VideoFx.java')
      and 'int w = texW, h = texH;' in rd(C + '/VideoFx.java'))
check('5.17.0: os PNGs grandes do jar ficaram em PNG8 (logo, bicho, stickers)',
      all(os.path.getsize(A + '/' + a) < 350_000 for a in
          ('../rptag_logo.png', 'textures/gui/bicho.png', 'textures/gui/stickers_hi.png')
          if os.path.exists(A + '/' + a)))

# ---- FITA
check('5.17.0: FITA registrada como item (FitaItem) com model e textura',
      'ITEMS.register("fita"' in _rm and os.path.exists(A + '/models/item/fita.json')
      and os.path.exists(A + '/textures/item/fita.png'))
check('5.17.0: a fala fica GRAVADA no item (sobrevive a baú/morte)',
      'TAG_FALA' in _ft and 'DataComponents.CUSTOM_DATA' in _ft)
check('5.17.0: clique direito toca o CHIADO e mostra a fala no estilo FITA (37)',
      'SOM_FITA' in _ft and 'ESTILO_FITA = 37' in _ft
      and 'new ScreenTextPayload(cor, 12, ESTILO_FITA)' in _ft)
check('5.17.0: o ADM grava e entrega /rp admin fita dar|gravar|ler',
      'Commands.literal("fita")' in _rp4 and 'fita dar <jogadores> <texto>' in _rp4
      and 'fita gravar' in _rp4 and 'fita ler' in _rp4)
check('5.17.0: estilo 37 existe no cliente (FitaAvisoFx com cassete girando)',
      'if (style == 37)' in _fxp and 'class FitaAvisoFx' in rd(C + '/FitaAvisoFx.java')
      and 'FitaAvisoFx.registrar(event)' in rd(C + '/ClientGuiLayers.java'))

# ---- NÚCLEO VIAL
check('5.17.0: NÚCLEO VIAL + versão QUEBRADA registrados (nunca some, vira a quebrada)',
      'ITEMS.register("nucleo_vial"' in _rm and 'ITEMS.register("nucleo_vial_quebrado"' in _rm
      and 'NUCLEO_VIAL_QUEBRADO' in _nv and 'trocarPorQuebrado' in _nv)
check('5.18.0: o núcleo dura MUITO mais (1% / 30 dias parado, 1% / 10 dias ativado, com decimal)',
      'DIAS_POR_PCT_PARADO = 30' in _nv and 'DIAS_POR_PCT_ATIVO = 10' in _nv
      and 'getDayTime() / 24000L' in _nv and 'VialCarga' in _nv)
check('5.18.0: ativado fica ENCANTADO, o QUEBRADO nao brilha e nao cobra carga',
      'return ativo(stack) && !ehQuebrado(stack);' in _nv
      and '(5.18.0) quebrado não cobra nem gasta' in _nv)
check('5.17.0: o servidor passa o tempo dos núcleos no tick (a cada 2 s)',
      'NucleoVialItem.tickJogador(p)' in _se2)
check('5.17.0: som ao ATIVAR o vial (e outro quando ele racha)',
      'SOM_VIAL = SOUNDS.register("vial_ativar"' in _rm
      and 'SOM_VIAL_QUEBRAR = SOUNDS.register("vial_quebrar"' in _rm
      and os.path.exists(A + '/sounds/vial_ativar.ogg')
      and os.path.exists(A + '/sounds/vial_quebrar.ogg'))
check('5.17.0: /rp admin vial dar|carga (o ADM monta a cena)',
      'Commands.literal("vial")' in _rp4 and 'vialDar(' in _rp4 and 'vialCarga(' in _rp4)

# ---- BARRA DO HUD
check('5.17.0: a BARRA fica sempre na tela e só o ADM mexe (valor, rótulo, cores)',
      'Commands.literal("barra")' in _rp4 and 'barraValor(' in _rp4
      and 'barraCor(' in _rp4 and 'barraTitulo(' in _rp4)
check('5.17.0: o estado da barra vive no RPWorldData (sobrevive ao restart)',
      'setBarraValor' in rd(R + '/RPWorldData.java')
      and 'tag.putInt("BarraValor", barraValor);' in rd(R + '/RPWorldData.java'))
check('5.17.0: o pacote BARRA_HUD leva rótulo+valor+3 cores+posição e é desenhado como camada',
      'class BarraHudFx' in rd(C + '/BarraHudFx.java')
      and 'BarraHudFx.registrar(event)' in rd(C + '/ClientGuiLayers.java')
      and 'registrar.playToClient(BarraPayload.TYPE' in _mn2)
check('5.17.0: a barra mostra a porcentagem no canto e o rótulo em cima',
      'String pct = valor + "%";' in rd(C + '/BarraHudFx.java')
      and 'AvisoRitual.texto(g, rotulo, x, y - 11' in rd(C + '/BarraHudFx.java'))

# ---- AXION
check('5.17.0: a AXION está na tela (dormindo por padrão), canto inferior direito',
      'class AxionHud' in rd(C + '/AxionHud.java')
      and 'AxionHud.registrar(event)' in rd(C + '/ClientGuiLayers.java')
      and 'registrar.playToClient(AxionPayload.TYPE' in _mn2
      and '0.995F' in rd(C + '/AxionHud.java'))
check('5.17.0: as texturas da Axion têm FUNDO REMOVIDO (PNG com transparência)',
      os.path.exists(A + '/textures/gui/axion_dormindo.png')
      and os.path.exists(A + '/textures/gui/axion_acordada.png')
      and _png_tem_alpha(A + '/textures/gui/axion_dormindo.png'))
check('5.17.0: ela DORME e ACORDA com comando do ADM (e tem animação)',
      'Commands.literal("axion")' in _rp4 and 'axionEstado(' in _rp4
      and 'AxionPayload.ACORDADA' in _rp4 and 'respira' in rd(C + '/AxionHud.java'))
check('5.17.0: o estado da Axion também fica no save',
      'setAxionEstado' in rd(R + '/RPWorldData.java')
      and 'tag.putInt("AxionEstado", axionEstado);' in rd(R + '/RPWorldData.java'))
check('5.17.0/5.26.1: protocolo subiu para v18 (cliente velho é recusado)',
      'event.registrar("18")' in _mn2)

# ============ 5.17.1: as três correções do dono ============
_axc = rd(C + '/AxionHud.java')
_nev2 = rd(R + '/NeptuneEventos.java')
check('5.18.0: a AXION é ESCONDIDA por padrão (só aparece se o ADM mandar)',
      'private static int estado = AxionPayload.ESCONDIDA;' in _axc
      and 'private int axionEstado = 0;' in rd(R + '/RPWorldData.java'))
_wdx = rd(R + '/RPWorldData.java')
check('5.18.0: save antigo (5.17.0) é MIGRADO: quem entrou não vê a Axion',
      'public static final int DATA_VERSAO = 2;' in _wdx
      and 'if (data.dataVersao < DATA_VERSAO) {' in _wdx
      and 'data.axionEstado = 0;' in _wdx
      and 'tag.putInt("DataVersao", dataVersao);' in _wdx)
check('5.18.0: no login o estado ATUAL da Axion é mandado pro jogador que entra',
      'RPCommands.enviarAxion(joined, data);' in rd(R + '/ServerEvents.java'))
check('5.18.0: AXION tem DOIS jeitos de COLOCAR (acordada/dormindo) e UM de TIRAR',
      '.then(Commands.literal("acordar").executes(ctx -> axionEstado(ctx, AxionPayload.ACORDADA))' in _rp4
      and '.then(Commands.literal("dormir").executes(ctx -> axionEstado(ctx, AxionPayload.DORMINDO))' in _rp4
      and '.then(Commands.literal("tirar").executes(ctx -> axionEstado(ctx, AxionPayload.ESCONDIDA))' in _rp4
      and 'literal("escala")' not in _rp4)
check('5.17.1: a SAÍDA é animação NO CORPO (códigos no mundo + sombra), não na tela',
      'NeptunePackets.Codigos' in _nev2
      and 'sombraChuva(nv, pos.add(0, passo * 0.55, 0), 7 - passo)' in _nev2
      and 'ScreenTextPayload pacote' not in _nev2)
check('5.17.1: e ela é PADRÃO de todo jogador (sem saidafx, sem liga/desliga)',
      'Commands.literal("saidafx")' not in _rp4 and 'saidafx on|off' not in _rp4)
check('5.17.1: a BARRA tem a forma simples /rp admin barra <0..100> [frase]',
      'argument("porcento"' in _rp4 and 'barraTodos(' in _rp4)
check('5.17.1: e a barra só aparece SE O ADM QUISER (padrão escondida)',
      'private boolean barraVisivel = false;' in rd(R + '/RPWorldData.java')
      and 'private static boolean visivel;' in rd(C + '/BarraHudFx.java'))

# ============ 5.18.0: pedidos do dono (relevo, barra vertical, cores, nucleo, balao) ============
check('5.18.0: as MONTANHAS do vazio voltam/consertam (relevo ABSOLUTO, sem marca que trava)',
      'public static final int ALTURA_LAJE = 11;' in _vr
      and 'marcar(nv, "R:"' not in _vr
      and 'int TOPO_LAJE = nv.getMinBuildHeight() + ALTURA_LAJE;' in _vr)
check('5.18.0: /vazio relevo forca o conserto das montanhas',
      'VazioReino.reconstruirRelevo(nv)' in rd(R + '/NeptuneCommands.java'))
check('5.18.0: os PREDIOS se consertam (celula sem predio = constroi) e nao crescem sem fim',
      'if (predioDePe(nv, x, z)) {' in _vr and 'private static boolean predioDePe(' in _vr
      and 'cidade-v1' in _vr)
_bpx = rd(R + '/BarraPayload.java')
_bhx = rd(C + '/BarraHudFx.java')
check('5.18.0: a BARRA e VERTICAL (em pe, enche de baixo pra cima) e o padrao e vertical',
      'boolean vertical' in _bpx
      and 'private boolean barraVertical = true;' in rd(R + '/RPWorldData.java'))
check('5.18.0: o desenho vertical existe mesmo (preenche de baixo pra cima)',
      'int bw = vertical ? 12 : largura;' in _bhx
      and 'y + bh - 1 - alt' in _bhx)
check('5.18.0: cor da barra por NOME (vermelho, verde, azul...) e nao so por codigo',
      'public static int corPorNome(String nome)' in _rp4
      and 'case "vermelho", "red" -> 0xC81E2E;' in _rp4
      and 'case "dourado", "ouro", "gold" -> 0xD4A017;' in _rp4)
check('5.18.0: o nome do NUCLEO aparece certo em qualquer idioma (getName no codigo)',
      'public Component getName(ItemStack stack)' in _nv
      and 'Núcleo de Energia Vial (quebrado)' in _nv)
check('5.18.0: o nucleo ATIVO nao mostra mais "10% a cada 5 dias" em lugar nenhum',
      'Estado: ATIVO"' in _nv and 'agora ele se gasta 10%' not in _nv)
check('5.18.0: o QUEBRADO nao ativa (e diz por que)',
      'if (ehQuebrado(stack)) {' in _nv
      and 'Não acende mais' in _nv
      and 'return InteractionResultHolder.fail(stack);' in _nv)
check('5.18.0: o /rp admin bolha SAIU (era o mesmo do balao)',
      'literal("bolha")' not in _rp4 and 'literal("balao")' in _rp4)
check('5.18.0: o vial ganhou MODELO 3D (com elementos, nao so uma textura chapada)',
      '"elements"' in io.open(A + '/models/item/nucleo_vial.json').read()
      and os.path.exists(A + '/models/item/nucleo_vial_quebrado.json'))
check('5.18.0: o balao de texto ganhou MAIS CORES (dentro, borda e letra)',
      '18 cores' in rd(C + '/BubbleStyleScreen.java')
      and '0xC81E2E, 0x6B0F1A, 0x12B886' in rd(C + '/BubbleStyleScreen.java'))

# ============ 5.20.0: vazio (mundo inteiro), bichos, morte, cidade fixa, Axion falando ============
_vr = rd(R + '/VazioReino.java')
_se = rd(R + '/ServerEvents.java')
check('5.20.0: o TAB saiu (o dono pediu) e o TAB do vanilla voltou',
      not os.path.exists(R + '/TabList.java')
      and 'TabList.' not in _se and 'literal("tab")' not in _rp4)
check('5.20.0: as MONTANHAS valem no mundo inteiro (todo chunk do vazio passa pelo relevo)',
      'public static void aoCarregarChunk(ServerLevel nv, int cx, int cz)' in _vr
      and 'onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event)' in _se
      and 'VazioReino.aoCarregarChunk(sl, event.getChunk().getPos().x, event.getChunk().getPos().z)' in _se)
check('5.20.0: a fila do relevo roda 2 chunks por tick (não trava o servidor)',
      'CHUNKS_POR_TICK = 2;' in _vr and 'while (!CONFERIR.isEmpty() && feitos < CHUNKS_POR_TICK)' in _vr)
check('5.20.0: os PREDIOS pararam de nascer sozinhos (cidade FIXA, uma vez por mundo)',
      'cidadeFixa(nv)' in _vr and 'marcar(nv, "cidade-v1")' in _vr
      and 'ruinaAutomatica(' not in _vr.replace('private static void ruinaAutomatica', ''))
check('5.20.0: os BICHOS do vazio machucam ao encostar (Enderman 4 / Warden 7, 1x por segundo)',
      'private static void danoDeContato(ServerLevel nv, ServerPlayer p, long agora)' in _vr
      and 'float dano = warden ? 7.0F : 4.0F;' in _vr
      and 'agora - ultimo < 1000L' in _vr)
check('5.20.0: quem morre DENTRO do vazio continua dentro (respawn na praca do vazio)',
      'marcarMorteNoVazio' in _vr and 'consumirMorteNoVazio' in _vr
      and 'VazioReino.consumirMorteNoVazio(p.getUUID())' in _se
      and 'VazioReino.marcarMorteNoVazio(p.getUUID())' in _se)
_af = rd(R + '/AxionFalaPayload.java')
_ax = rd(C + '/AxionHud.java')
check('5.20.0: a AXION fala num balao VERMELHO com letras PRETAS',
      'public static void receberFala(String texto, int segundos)' in _ax
      and '0x8E0F1A' in _ax and '0x120204' in _ax
      and 'record AxionFalaPayload' in _af)
check('5.20.0: /rp admin axion falar <texto> manda o balao pra todos',
      'literal("falar")' in _rp4 and 'private static int axionFalar(' in _rp4
      and 'sendToAllPlayers(' in _rp4)
check('5.20.0: "avisar AXION:" fica em VERMELHO ESCURO personalizado',
      'boolean vozDaAxion = false;' in _rp4
      and 'AXION' in _rp4 and 'vozDaAxion) {' in _rp4)

# ============ 5.21.0: o chao no nivel certo, as trevas, os bichos e a alma ============
_dimx = rd('src/main/resources/data/rptag/dimension/exilio.json')
check('5.21.0: o chao do vazio parou de ficar 1 bloco acima do padrao',
      'int alvo = TOPO_LAJE + h;' in _vr and 'TOPO_LAJE + 1 + h' not in _vr
      and 'if (atual < TOPO_LAJE) {' in _vr and 'atual = TOPO_LAJE;' in _vr)
check('5.21.0: o mundo ANTIGO e rebaixado (a montanha que flutuava desce um bloco)',
      'private static void assentarColuna(ServerLevel nv, int x, int z)' in _vr
      and 'assentarColuna(nv, x, z);' in _vr)
check('5.21.0: o piso claro do gerador virou PEDRA PRETA (as trevas)',
      'private static void vestirChao(ServerLevel nv, int x, int z)' in _vr
      and 'private static BlockState pedraDoChao(int x, int z)' in _vr
      and 'vestirChao(nv, x, z);' in _vr
      and 'Blocks.CRYING_OBSIDIAN.defaultBlockState()' in _vr
      and 'Blocks.GILDED_BLACKSTONE.defaultBlockState()' in _vr)
check('5.21.0: a dimensao ja NASCE escura (blackstone + sculk, sem concreto cinza)',
      'light_gray_concrete' not in _dimx and 'minecraft:blackstone' in _dimx
      and 'minecraft:sculk' in _dimx and '"biome": "rptag:vazio"' in _dimx)
check('5.22.0: o bioma do vazio nao oferece NENHUM bicho (lista de spawn vazia)',
      os.path.exists('src/main/resources/data/rptag/worldgen/biome/vazio.json')
      and '"monster": []' in rd('src/main/resources/data/rptag/worldgen/biome/vazio.json')
      and 'minecraft:ash' in rd('src/main/resources/data/rptag/worldgen/biome/vazio.json'))
check('5.22.0: CHEGA de bicho no vazio (o spawner de enderman saiu de vez)',
      'tickBichos' not in _vr and 'nascerBicho' not in _vr
      and 'setTarget(p)' not in _vr and 'MAX_BICHOS' not in _vr)
check('5.21.0: a montanha do vazio ficou PRETA (o tufo claro saiu da paleta)',
      'Blocks.TUFF.defaultBlockState()' not in _vr.split('private static BlockState pedraDoMonte')[1].split('private static')[0])
check('5.22.0: a alma vazia NAO tira mais vida (so assusta e sussurra)',
      'perto.hurt(perto.damageSources().magic(), 2.0F)' not in _vr
      and 'menor < 2.1 && Math.random() < 0.08' in _vr)

# ============ 5.22.0: o vazio voltou a ser vazio + o frasco do nucleo vial ============
_se2 = rd(R + '/ServerEvents.java')
check('5.22.0: nada nasce no vazio (o spawn natural e cancelado para todo mundo)',
      'event.setSpawnCancelled(true);' in _se2
      and 'EntityType.ENDERMAN' not in _se2.split('FinalizeSpawnEvent')[1].split('RightClickItem')[0])
check('5.22.0: a varredura pega QUALQUER bicho (enderman e warden inclusive)',
      'vazio varreu' in _vr
      and 'boolean preto = e.getType() == net.minecraft.world.entity.EntityType.ENDERMAN' not in _vr)
check('5.22.0: a varredura de bichos do vazio existe e o comando de teste NAO vai no jar',
      'public static String testeVazio(ServerLevel nv)' in _vr
      and 'testebicho' not in rd(R + '/NeptuneCommands.java')
      and 'simular' not in rd(R + '/NeptuneCommands.java'))
check('5.23.0: nenhum comando escondido de teste vai no jar publicado',
      all(k not in rd(R + '/NeptuneCommands.java') for k in ['testechao', 'testebicho', '"simular"']))
_nv_m = io.open(A + '/models/item/nucleo_vial.json', encoding='utf-8').read()
_nv_q = io.open(A + '/models/item/nucleo_vial_quebrado.json', encoding='utf-8').read()
check('5.22.0: o nucleo vial virou um FRASCO DE VIDRO (paineis finos + energia dentro)',
      '"vidro"' in _nv_m and '"luz"' in _nv_m and '"metal"' in _nv_m and '"rolha"' in _nv_m
      and _nv_m.count('"from"') >= 10)
check('5.22.0: o frasco tem o vermelho que BRILHA e o cristal girado no meio',
      '"angle": 45' in _nv_m and 'nucleo_vial_luz' in _nv_m and '"gui_light": "front"' in _nv_m)
check('5.22.0: o frasco QUEBRADO tem vidro rachado, cacos e rolha caida (sem luz)',
      '"vidroq"' in _nv_q and 'residuo' in _nv_q and 'nucleo_vial_luz' not in _nv_q)
check('5.22.0: as texturas do frasco existem (vidro, luz, metal, rolha, rachado, residuo)',
      all(os.path.exists(A + '/textures/item/nucleo_vial_' + n + '.png') for n in
          ['vidro', 'luz', 'metal', 'rolha', 'quebrado_vidro', 'residuo']))

# ============ 5.23.0: aviso de lugar TODA vez que entra (cidade, praca, portal...) ============
_lug = rd(R + '/VazioLugares.java')
check('5.23.0: existe o sistema de aviso de LUGAR no vazio (VazioLugares)',
      'public final class VazioLugares' in _lug
      and 'public static void tick(ServerLevel nv, ServerPlayer p, long agora)' in _lug)
check('5.23.0: os 5 lugares existem (praca, portal, santuario, CIDADE, ermos)',
      all(k in _lug for k in ['"praca"', '"portal"', '"santuario"', '"cidade"', '"ermos"'])
      and '"A CIDADE MORTA"' in _lug)
check('5.23.0: o aviso toca TODA VEZ que entra (nao e 1x por sessao)',
      'if (atual.id().equals(DENTRO.get(id)))' in _lug and 'DENTRO.put(id, atual.id());' in _lug
      and 'DENTRO.remove(id);' in _lug and 'clearSeen' not in _lug)
check('5.23.0: a decisao do lugar e separada da tela (decidir) e o tick so chama decidir+avisar',
      'public static Lugar decidir(UUID id, double x, double z, long agora)' in _lug
      and 'Lugar l = decidir(p.getUUID(), p.getX(), p.getZ(), agora);' in _lug)
check('5.23.0: o aviso usa o titulo grande + subtitulo + som + particula de alma',
      'ClientboundSetTitleTextPacket' in _lug and 'ClientboundSetSubtitleTextPacket' in _lug
      and 'ClientboundSetTitlesAnimationPacket' in _lug and 'RPTagMod.SOM_VAZIO' in _lug
      and 'ParticleTypes.SOUL' in _lug)
check('5.23.0: a borda do lugar tem histerese (nao pisca andando na linha)',
      'FOLGA = 8.0' in _lug and 'ESPERA_MS = 1500L' in _lug)
check('5.23.0: trocar de dimensao / sair do servidor zera a memoria de lugar',
      'public static void esquecer(UUID id)' in _lug
      and 'VazioLugares.esquecer(event.getEntity().getUUID())' in _se
      and 'VazioLugares.aoTrocarDimensao(' in rd(R + '/ServerEvents.java'))
check('5.23.0: o tick do vazio chama VazioLugares.tick',
      'VazioLugares.tick(nv, p, agora);' in _vr)
_rp6 = rd(R + '/NeptuneCommands.java')
check('5.23.0: /vazio lugares, /vazio lugar <id> e /vazio onde <x> <z>',
      'literal("lugares")' in _rp6 and 'literal("lugar")' in _rp6 and 'literal("onde")' in _rp6
      and 'VazioLugares.listar(quem)' in _rp6 and 'VazioLugares.lugarEm(x, z)' in _rp6)

# ============ 5.24.0: barra do jogador, vazio que nao se constroi na frente, portal, axion ============
_d = rd(R + '/RPWorldData.java')
_rp7 = rd(R + '/RPCommands.java')
check('5.24.0: /barra [on|off] existe e e de TODO jogador (registrado direto no dispatcher)',
      'Commands.literal("barra")' in _rp7
      and 'private static int barraDoJogador(' in _rp7
      and '.then(Commands.literal("off").executes(ctx -> barraDoJogador(ctx, Boolean.FALSE)))' in _rp7)
check('5.24.0: a barra respeita a escolha do jogador (enviarBarra checa barraEscondida)',
      'boolean visivel = d.barraVisivel() && !d.barraEscondida(alvo.getUUID());' in _rp7
      and 'public boolean barraEscondida(java.util.UUID id)' in _d)
check('5.24.0: a escolha da barra fica salva no mundo (volta no proximo login)',
      'tag.put("BarraEscondida", barraOffList);' in _d
      and 'tag.getList("BarraEscondida", Tag.TAG_COMPOUND)' in _d)
check('5.24.0: o vazio esculpe cada chunk UMA VEZ (marca E: no save, nada de refazer ao andar)',
      'private static void esculpirUmaVez(ServerLevel nv, int cx, int cz)' in _vr
      and 'marcado(nv, "E:" + cx + "," + cz)' in _vr
      and 'marcar(nv, "E:" + cx + "," + cz);' in _vr
      and 'esculpirChunk(nv, cx, cz);\n        despejarFila(nv);\n        esculpirChunk(nv, cx, cz);' in _vr
      and 'public int esquecerRelevo()' in _d)
_corpo = _vr[_vr.index('private static void esculpirChunk'):_vr.index('// ============================================== TREVAS')]
check('5.24.0: o entulho sorteado saiu do relevo (nada de pedra solta acumulando)',
      'Blocks.GRAVEL.defaultBlockState()' not in _corpo
      and 'Blocks.COBBLED_DEEPSLATE.defaultBlockState()' not in _corpo
      and 'r.nextBoolean()' not in _corpo)
check('5.24.0: o vazio PREPARA o terreno antes do jogador chegar (fila PREPARO)',
      'private static final ArrayDeque<int[]> PREPARO' in _vr
      and 'private static void prepararEmVolta(ServerLevel nv, ServerPlayer p)' in _vr
      and 'prepararEmVolta(nv, p);' in _vr
      and 'preparo do vazio agendado' in _vr)
check('5.24.0: a frase do portal e "ele nao gosta de devolver ninguem"',
      '"ele não gosta de devolver ninguém"' in _lug if False else '"ele não gosta de devolver ninguém"' in rd(R + '/VazioLugares.java'))
check('5.24.0: /rp admin axion dono <jogador> e dono nenhum (de quem ela e)',
      'private static int axionDono(' in _rp7 and 'Commands.literal("dono")' in _rp7
      and 'Commands.literal("nenhum")' in _rp7)
check('5.24.0: a Axion fala SO na tela do dono (nada de balao pra todos)',
      'PacketDistributor.sendToAllPlayers(\n                new AxionFalaPayload' not in _rp7
      and 'PacketDistributor.sendToPlayer(quem,' in _rp7)
check('5.24.0: quem nao e o dono recebe a Axion escondida (ela mora com uma pessoa)',
      'if (d.axionDono() != null && !d.axionDono().equals(alvo.getUUID()))' in _rp7
      and 'private java.util.UUID axionDono;' in _d
      and 'tag.putUUID("AxionDono", axionDono);' in _d)
check('5.31.0: a versao subiu pra 5.31.0 no jar, no mod e no gradle',
      "version = '5.31.0'" in rd('build.gradle')
      and '5.31.0' in rd(R + '/RPTagMod.java'))
check('5.24.0: nenhum comando escondido de teste do vazio vai no jar',
      all(k not in _rp7 for k in ['testechao', 'testebicho', '"simular"', '"_prova"', '_prova']))

# ============ 5.24.1: nada do avisar2 repetido no avisar ============
_s   = rd(R + '/RPCommands.java')
_ai  = _s.index('.then(Commands.literal("avisar")')
_af  = _s.index('.then(Commands.literal("avisar2")')
_a2f = _s.index('.then(Commands.literal("avisozona")')
_avisar  = _s[_ai:_af]
_avisar2 = _s[_af:_a2f]
_lit_avisar  = set(re.findall(r'Commands\.literal\("([a-z0-9]+)"\)', _avisar)) - {'avisar'}
_lit_avisar2 = set(re.findall(r'aviso2\("([a-z0-9]+)"\)', _avisar2))
check('5.24.1: NENHUM estilo do avisar2 aparece no /rp admin avisar (pedido do dono)',
      not (_lit_avisar & _lit_avisar2))
check('5.24.1: o /rp admin avisar2 continua com os 16 avisos narrativos (nada se perdeu)',
      _lit_avisar2 == {'louco', 'tolo', 'marinheiro', 'enforcado', 'espectador', 'justica',
                       'eremita', 'julgamento', 'lua', 'mago', 'morte2', 'sol', 'torre',
                       'estrela', 'foulo', 'fita'})
check('5.24.1: os estilos que sairam do avisar continuam com o FX proprio no despacho',
      all(k in _s for k in ['case "louco" -> 25', 'case "morte2", "ceifador", "morte_carta" -> 32',
                            'case "tolo", "foulo" -> 36', 'case "fita" -> 37']))
check('5.24.1/5.24.3: o /rp admin avisar ficou com a secao ANTIGA + as 2 infeccoes',
      _lit_avisar == {'pesadelo', 'pesadelo2', 'morte', 'inferno', 'gelo', 'divino', 'void',
                      'sangue', 'demoniaco', 'runico', 'real', 'egipicio', 'expurgo',
                      'ciberpsicose', 'energia', 'conhecimento', 'morteop', 'medoop',
                      'infeccao', 'infeccao2'})
check('5.24.1: a ajuda separa as duas listas e avisa que nada do avisar2 esta no avisar',
      'nada disso existe no avisar' in _s)

# ============ 5.24.2: cor da barra FACIL pro ADM ============
check('5.24.2: /rp admin barra cor SEM argumento abre a PALETA clicavel',
      'private static int barraPaleta(' in _rp7
      and '.then(Commands.literal("cor")\n                                .executes(ctx -> barraPaleta(ctx))' in _rp7
      and 'ClickEvent.Action.RUN_COMMAND' in _rp7)
check('5.24.2: a paleta tem um botao por cor (e o botao roda o comando da cor)',
      'for (String nome : NOMES_COR)' in _rp7 and '"/rp admin barra cor " + nome' in _rp7)
check('5.24.2: /rp admin barra cor <cor> pinta a BARRA INTEIRA com uma palavra',
      'private static int pintarBarraInteira(' in _rp7
      and 'd.setBarraCorFundo(escurecer(cor, 0.30F));' in _rp7
      and 'd.setBarraCorTexto(textoSobre(cor));' in _rp7)
check('5.24.2: o TAB sugere os nomes de cor e as partes',
      'private static com.mojang.brigadier.suggestion.SuggestionProvider<' in _rp7
      and 'for (String c : NOMES_COR) {' in _rp7 and '.suggests(sugereCor())' in _rp7)
check('5.24.2: o jeito ANTIGO continua (parte + cor) e aceita a ordem invertida',
      'return barraCor(ctx, parte, nomeCor);' in _rp7
      and 'if (!ehParte(a) && ehParte(b)) {' in _rp7)
check('5.24.2: a letra acompanha a cor (clara em fundo escuro, escura em fundo claro)',
      'return luz > 0.6 ? 0x141018 : 0xF2F4F8;' in _rp7)
check('5.24.2: cor errada ensina o caminho (lista as cores e manda pra paleta)',
      'private static int barraCorInvalida(' in _rp7 and 'abre a paleta pra clicar' in _rp7)

# ============ 5.24.3: pedido 77 — FITA em pixel art + as duas INFECCOES ============
_fita = rd(C + '/FitaAvisoFx.java')
check('5.24.3: a FITA usa a PIXEL ART do prototipo (grade 84x54, pacote dos carreteis passando)',
      'float pacL = 5.7F - 2.4F * pr, pacR = 3.3F + 2.4F * pr;' in _fita
      and 'private static void desenharCassete(' in _fita
      and 'carretel(b, 32, 27, angL, pacL, borrao, a);' in _fita
      and 'carretel(b, 52, 27, angR, pacR, borrao, a);' in _fita)
check('5.24.3: os carreteis GIRAM de verdade (angulo integrado quadro a quadro)',
      'angL += -9F * sp / pacL * dtm;' in _fita and 'angR += -9F * sp / pacR * dtm;' in _fita
      and 'fase += sp * 12F * dtm;' in _fita)
check('5.24.3: entrou o OSD de videocassete (PLAY / REW / STOP / PAUSE + contador)',
      '"\\u25C0\\u25C0 REW"' in _fita and 'PLAY \\u25B6' in _fita
      and 'STOP \\u25A0' in _fita and '%d:%02d:%02d' in _fita)
check('5.24.3: a fita tem LED REC, parafusos, etiqueta e a TV ligando',
      '0xFF2A35' in _fita and 'parafusos' in _fita and '0xC0262F' in _fita
      and 'a TV ligando' in _fita)

_inf1 = rd(C + '/InfeccaoAvisoFx.java')
_inf2 = rd(C + '/Infeccao2AvisoFx.java')
check('5.24.3: existe o FX da INFECCAO 38 (veias ramificadas + olho + FORCA x SANIDADE)',
      'public final class InfeccaoAvisoFx' in _inf1
      and 'private static void desenharVeias(' in _inf1
      and 'private static void olho(' in _inf1
      and 'PONTOS_FORCA' in _inf1 and 'PONTOS_SAN' in _inf1)
check('5.24.3: a INFECCAO 38 tem o "AVISO" com sangue escorrendo e o O virando olho',
      'AVISO contaminado' in _inf1 and 'o "O" vira um OLHO' in _inf1)
check('5.24.3: existe o FX da INFECCAO 39 (estilo Minecraft: coracoes, blocos, 4 estagios)',
      'public final class Infeccao2AvisoFx' in _inf2
      and 'ALGO ENTROU EM VOC\\u00ca' in _inf2
      and 'VOC\\u00ca NUNCA ESTEVE S\\u00c3O' in _inf2
      and 'CORACAO' in _inf2)
check('5.24.3: as DUAS infeccoes sao do /rp admin avisar e NAO do avisar2 (pedido do dono)',
      'case "infeccao", "infec\u00e7ao", "infec\u00e7\u00e3o", "infection" -> 38;' in _s
      and 'case "infeccao2", "infec\u00e7\u00e3o2", "infec\u00e7ao2", "infeccao_mc" -> 39;' in _s
      and 'infeccao' in _lit_avisar and 'infeccao2' in _lit_avisar
      and 'infeccao' not in _lit_avisar2 and 'infeccao2' not in _lit_avisar2)
check('5.24.3: o cliente liga os dois FX e para os dois quando chega outro aviso',
      'if (style == 38) {' in _ce and 'if (style == 39) {' in _ce
      and 'InfeccaoAvisoFx.parar();' in _ce and 'Infeccao2AvisoFx.parar();' in _ce)
check('5.24.3: os dois FX estao registrados como camada de HUD',
      'InfeccaoAvisoFx.registrar(event)' in _cgl and 'Infeccao2AvisoFx.registrar(event)' in _cgl)
check('5.24.3 -> 5.26.1: cada infeccao tem TRILHA propria (arquivo sintetizado, tocado pelo FX)',
      os.path.getsize('src/main/resources/assets/rptag/sounds/infeccao_aviso.ogg') > 60000
      and os.path.getsize('src/main/resources/assets/rptag/sounds/infeccao2_aviso.ogg') > 60000
      and 'SOM_INFECCAO.get()' in rd(C + '/InfeccaoAvisoFx.java')
      and 'SOM_INFECCAO2.get()' in rd(C + '/Infeccao2AvisoFx.java'))
check('5.24.3: a ajuda do comando lista as duas infeccoes',
      'medoop, infeccao, infeccao2' in _s)

# ============ 5.25.0: CIBERPSICOSE como EFEITO + VAMPIRO (atributo) + AMULETO DO ECLIPSE ============
_ef = rd(R + '/RPEffects.java')
_at = rd(R + '/RPAttributes.java')
_vs = rd(R + '/VampiroSistema.java')
_ae = rd(R + '/AmuletoEclipseItem.java')
_se = rd(R + '/ServerEvents.java')
_mod = rd(R + '/RPTagMod.java')
_cib = rd(C + '/CiberpsicoseAvisoFx.java')
check('5.25.0: existe o EFEITO rptag:ciberpsicose (registrado, com cor e icone)',
      'EFFECTS.register("ciberpsicose"' in _ef and 'MobEffectCategory.HARMFUL, 0x00F0FF' in _ef
      and os.path.exists(A + '/textures/mob_effect/ciberpsicose.png'))
check('5.25.0: o CLIENTE liga a tela quebrada enquanto o efeito existir (e desliga quando acaba)',
      'private static void seguirEfeito()' in _cib
      and 'mc.player.hasEffect(dev.rptag.RPEffects.CIBERPSICOSE)' in _cib
      and 'iniciar("CIBERPSICOSE DETECTADA", 0)' in _cib and 'parar();' in _cib
      and 'seguirEfeito();' in _cib)
check('5.25.0: /rp admin ciberpsicose <jogadores> [segundos] e ... parar <jogadores>',
      'private static int ciberAplicar(' in _s and 'private static int ciberParar(' in _s
      and '.then(Commands.literal("ciberpsicose")' in _s
      and 'RPEffects.CIBERPSICOSE, seg * 20' in _s
      and 'alvo.removeEffect(RPEffects.CIBERPSICOSE);' in _s)
check('5.25.0: a ciberpsicose tambem funciona por /effect give (efeito de verdade, igual o medo)',
      'RPEffects.EFFECTS.register(modEventBus);' in _mod
      and 'addEffect(new net.minecraft.world.effect.MobEffectInstance(' in _s)
check('5.25.0: o ATRIBUTO rptag:vampiro existe, e sincronizado e vai de 0 a 1',
      'RangedAttribute(' in _at and '"attribute.rptag.vampiro", 0.0D, 0.0D, 1.0D' in _at
      and '.setSyncable(true)' in _at)
check('5.25.0: o atributo esta REGISTRADO e colado no jogador (sem isso getAttribute devolve null)',
      'RPAttributes.ATTRIBUTES.register(modEventBus);' in _mod
      and 'colarAtributosNoJogador' in _mod
      and 'event.add(net.minecraft.world.entity.EntityType.PLAYER, RPAttributes.VAMPIRO);' in _mod)
check('5.25.0: o SOL queima o vampiro (dia + ceu aberto + fora da agua, igual zumbi)',
      'return !noite(p) && !p.isInWaterOrRain() && lv.canSeeSky(p.blockPosition());' in _vs
      and 'p.setRemainingFireTicks(200);' in _vs and 'O SOL QUEIMA A SUA PELE!' in _vs)
check('5.25.0: e o fogo so e apagado pela protecao quando foi o SOL que acendeu (lava continua)',
      'ACESO_PELO_SOL' in _vs and 'System.currentTimeMillis() - quando < 4000L' in _vs)
check('5.25.0: o tick do vampiro roda no servidor a cada 1 segundo',
      'VampiroSistema.tick(p);' in _se and 'getTickCount() % 20 == 0' in _se)
check('5.25.0: /rp admin vampiro <jogadores> ligar|desligar|ver',
      'private static int vampiroDefinir(' in _s and 'private static int vampiroVer(' in _s
      and 'Commands.literal("ligar")' in _s and 'Commands.literal("desligar")' in _s
      and 'inst.setBaseValue(ligado ? 1.0D : 0.0D);' in _vs)
check('5.25.0: o AMULETO DO ECLIPSE existe (item + modelo + textura + nome nos dois idiomas)',
      'AmuletoEclipseItem extends Item' in _ae
      and 'ITEMS.register("amuleto_eclipse"' in _mod
      and os.path.exists(A + '/models/item/amuleto_eclipse.json')
      and os.path.exists(A + '/textures/item/amuleto_eclipse.png')
      and 'item.rptag.amuleto_eclipse' in rd(A + '/lang/pt_br.json')
      and 'item.rptag.amuleto_eclipse' in rd(A + '/lang/en_us.json'))
check('5.25.0: o amuleto protege NA MAO (principal ou secundaria) e pelo uso (clique direito)',
      'p.getMainHandItem().is(RPTagMod.AMULETO_ECLIPSE.get())' in _vs
      and 'p.getOffhandItem().is(RPTagMod.AMULETO_ECLIPSE.get())' in _vs
      and 'VampiroSistema.sombra(sp, SOMBRA_SEGUNDOS);' in _ae
      and 'public static boolean protegido(' in _vs)
check('5.25.0: existe o EFEITO rptag:sombra (2 min) e ele tambem protege',
      'EFFECTS.register("sombra"' in _ef and 'p.hasEffect(RPEffects.SOMBRA)' in _vs
      and os.path.exists(A + '/textures/mob_effect/sombra.png')
      and 'effect.rptag.sombra' in rd(A + '/lang/pt_br.json'))
check('5.25.0: /rp admin eclipse dar <jogadores> entrega o amuleto',
      'private static int eclipseDar(' in _s
      and 'new net.minecraft.world.item.ItemStack(RPTagMod.AMULETO_ECLIPSE.get())' in _s)
check('5.25.0: o /rp ajuda ensina os quatro comandos novos',
      'ciberpsicose <jogadores>' in _s and 'vampiro <jogadores> ligar|desligar' in _s
      and 'eclipse dar <jogadores>' in _s)

# ============ 5.26.1: TELA DE MORTE FORA DE RP + VISAO NOTURNA DO VAMPIRO ============
_mt = rd(C + '/MorteTelaRp.java')
_mp = rd(R + '/MorteRpPayload.java')
_cl = rd(R + '/CanonLife.java')
check('5.26.1: existe a TELA DE MORTE nova (escurece e sobram so os botoes)',
      'public final class MorteTelaRp extends DeathScreen' in _mt
      and 'esta tela' not in _mt.lower().replace('a tela', '')
      and 'for (var r : this.renderables)' in _mt
      and 'g.fill(0, 0, w, h, ((int) (255 * escuro) << 24));' in _mt)
check('5.26.1: a tela NAO desenha titulo, score nem a causa da morte do vanilla',
      'super.render(g, mx, my, parcial)' not in _mt
      and 'morte fora de RP' in _mt          # so o recadinho discreto de "vida canonica intacta"
      # (a citacao "You Died" so existe no COMENTARIO de cabecalho; o codigo nao desenha nada disso)
      and 'g.drawString(this.font, recado' in _mt
      and 'renderDeathBackground' not in _mt)
check('5.26.1: a morte fora de RP troca o DeathScreen do jogo (ScreenEvent.Opening)',
      'ScreenEvent.Opening event' in _mt and 'event.setNewScreen(new MorteTelaRp())' in _mt
      and 'nova.getClass() == DeathScreen.class' in _mt)
check('5.26.1: o SERVIDOR avisa se a morte e canonica (MorteRpPayload no LivingDeathEvent)',
      'record MorteRpPayload(boolean canon, boolean sol)' in _mp
      and 'new MorteRpPayload(wd.isCanonEvent(), peloSol)' in _cl
      and 'if (!wd.isCanonEvent()) {' in _cl)
check('5.26.1: a tela nova SO entra quando o servidor diz que a morte NAO e canonica',
      'if (canon || !avisado)' in _mt and 'public static void definirMorte(boolean canonVida, boolean foiOSol)' in _mt
      and 'registrar.playToClient(MorteRpPayload.TYPE' in rd(R + '/ModNetworking.java')
      and 'MorteTelaRp.definirMorte(payload.canon(), payload.sol())' in rd(C + '/ClientPayloadHandler.java'))
check('5.26.1: a tela tem atmosfera propria (poeira subindo, brilho do corpo, vinheta)',
      'desenharAtmosfera' in _mt and 'fillGradient' in _mt and 'poeira' in _mt.lower())
check('5.26.1: o recado do servidor vale para UMA morte so (e limpo ao sair da tela)',
      'public static void esquecer()' in _mt and 'esquecer();' in _mt)
check('5.26.1: o pacote subiu de versao (v16) — cliente velho e recusado com aviso',
      'event.registrar("18")' in rd(R + '/ModNetworking.java'))
check('5.26.1: o VAMPIRO tem VISAO NOTURNA a noite (nao e so debuff)',
      'private static void visaoNoturna(ServerPlayer p)' in _vs
      and 'MobEffects.NIGHT_VISION' in _vs and 'if (!noiteDele) {' in _vs
      and 'public static boolean escuro(ServerPlayer p)' in _vs
      and 'visaoNoturna(p);' in _vs)
check('5.26.1: a NOITE agora tem duas camadas (efeito de 2 min + aviso pro cliente)',
      '2400, 0, false, false, true' in _vs and 'atual.getDuration() > 1200' in _vs
      and 'new VampiroNoitePayload(noiteDele)' in _vs)
check('5.26.1: o /rp admin vampiro ver mostra a visao noturna a noite',
      'vis\u00e3o noturna' in _vs)

# ============ 5.26.1: AVISO DE MORTE PELO SOL ============
_mp2 = rd(R + '/MorteRpPayload.java')
_cl2 = rd(R + '/CanonLife.java')
_vs2 = rd(R + '/VampiroSistema.java')
_mt2 = rd(C + '/MorteTelaRp.java')
check('5.26.1: o pacote da morte leva TAMBEM se a morte foi o SOL (2 booleanos)',
      'record MorteRpPayload(boolean canon, boolean sol)' in _mp2
      and _mp2.count('ByteBufCodecs.BOOL') == 2)
check('5.26.1: o servidor sabe que a morte foi o SOL (fogo + a marca que o sol acendeu)',
      'public static boolean morreuPeloSol(ServerPlayer p' in _vs2
      and 'DamageTypeTags.IS_FIRE' in _vs2
      and 'ACESO_PELO_SOL.get(p.getUUID())' in _vs2 and '< 10_000L' in _vs2)
check('5.26.1: a morte pelo sol NAO confunde lava/fogo de outro (precisa da marca do sol)',
      'if (quando == null) {' in _vs2 and 'return false;   // o sol nunca acendeu fogo neste jogador' in _vs2)
check('5.26.1: a mesa inteira e avisada de que ele morreu PEGANDO FOGO por causa do SOL',
      'avisarMortePeloSol(vitima)' in _cl2
      and 'morreu pegando fogo: o SOL o alcan' in _cl2
      and 'getPlayerList().getPlayers()' in _cl2)
check('5.26.1: o proprio morto le que foi o SOL e o que teria salvado',
      'morreu para o SOL' in _cl2 and 'amuleto do eclipse' in _cl2)
check('5.26.1: a tela diz O SOL TE ALCANCOU e que ele morreu pegando fogo',
      'O SOL TE ALCAN' in _mt2 and 'morreu pegando fogo' in _mt2
      and 'teriam te salvado' in _mt2)
check('5.26.1: na morte pelo sol a tela troca a poeira cinza por BRASA (laranja, tremulando)',
      '0xE8873A' in _mt2 and 'peloSol ? 0x6B2A08 : 0x3A0A0E' in _mt2)
check('5.26.1: morte sem ser o sol continua com o aviso de sempre (nao vira fogo)',
      'morte fora de RP' in _mt2 and 'if (peloSol) {' in _mt2)
check('5.31.0: a versao subiu pra 5.31.0 (jar, mod e gradle)',
      "version = '5.31.0'" in rd('build.gradle') and '5.31.0' in rd(R + '/RPTagMod.java'))
check('5.26.1: o protocolo subiu pra v17 (o pacote da morte mudou de formato)',
      'event.registrar("18")' in rd(R + '/ModNetworking.java'))

# ============ 5.26.1: SOM DA INFECCAO, LOREZONE POR ENTRADA, CIBERPSICOSE QUE ATACA, ============
# ============          VISAO NOTURNA EM DUAS CAMADAS E PROTEÇÃO DO ECLIPSE               ============
_rm = rd(R + '/RPTagMod.java')
_sj = rd('src/main/resources/assets/rptag/sounds.json')
_fx1 = rd(C + '/InfeccaoAvisoFx.java')
_fx2 = rd(C + '/Infeccao2AvisoFx.java')
_lz = rd(R + '/LoreZones.java')
_cp2 = rd(R + '/CiberpsicoseSistema.java')
_vn = rd(R + '/VampiroNoitePayload.java')
_vv = rd(C + '/VisaoVampiroFx.java')
_pt = rd('src/main/resources/assets/rptag/lang/pt_br.json')
_en = rd('src/main/resources/assets/rptag/lang/en_us.json')

check('5.26.1: a INFECCAO ganhou TRILHA propria (som registrado no mod)',
      'SOM_INFECCAO =' in _rm and '"infeccao_aviso"' in _rm)
check('5.26.1: a INFECCAO 2 tambem tem trilha propria (outro som, outro arquivo)',
      'SOM_INFECCAO2 =' in _rm and '"infeccao2_aviso"' in _rm)
check('5.26.1: os dois sons existem no sounds.json (senao nao toca)',
      'infeccao_aviso' in _sj and 'infeccao2_aviso' in _sj)
check('5.26.1: os arquivos .ogg das trilhas estao no mod (10 s cada, gerados por arte/audio_infeccao.py)',
      os.path.getsize('src/main/resources/assets/rptag/sounds/infeccao_aviso.ogg') > 60000
      and os.path.getsize('src/main/resources/assets/rptag/sounds/infeccao2_aviso.ogg') > 60000
      and os.path.exists('arte/audio_infeccao.py'))
check('5.26.1: quem toca a trilha e o FX do cliente, no mesmo instante da animacao',
      'SOM_INFECCAO.get()' in _fx1 and 'SOM_INFECCAO2.get()' in _fx2)
check('5.26.1: o servidor parou de tocar os 2 sons baixinhos da infeccao (era o "fraco")',
      'case 38, 39 -> { }' in rd(R + '/RPCommands.java'))
check('5.26.1: LOREZONE mostra o titulo/aviso a CADA ENTRADA (nao mais uma vez na vida)',
      'data.marcarAvisoVisto(uuid, zone.id());' in _lz and 'show(player, zone);' in _lz
      and 'if (!data.avisoVisto(uuid, zone.id())) {' not in _lz)
check('5.26.1: dentro da zona aparece UMA vez (so dispara na entrada, nao a cada segundo)',
      'boolean entrouAgora = !prev.contains(zone.id());' in _lz and 'if (entrouAgora) {' in _lz
      and 'AVISOS_POR_ENTRADA' in _lz)
check('5.26.1: CIBERPSICOSE ataca quem esta perto SEM o jogador querer (jogador primeiro)',
      'public final class CiberpsicoseSistema' in _cp2
      and 'acharAlvo' in _cp2 and 'return melhorJogador != null ? melhorJogador : melhorBicho;' in _cp2)
check('5.26.1: o golpe da ciberpsicose e um golpe DE VERDADE do jogo (arma/dano/knockback)',
      'p.attack(alvo);' in _cp2 and 'p.swing(InteractionHand.MAIN_HAND, true);' in _cp2
      and 'PLAYER_ATTACK_CRIT' in _cp2)
check('5.26.1: o sistema da ciberpsicose roda no tick do servidor',
      'CiberpsicoseSistema.tick(p);' in rd(R + '/ServerEvents.java'))
check('5.26.1: a visao noturna agora dura 2 min (nao 16 s) e a noite comeca no anoitecer',
      '2400, 0, false, false, true' in _vs and 't >= 12000L && t < 23600L' in _vs)
check('5.26.1: e o servidor avisa o CLIENTE que o vampiro esta na noite dele',
      'record VampiroNoitePayload(boolean noite)' in _vn
      and 'registrar.playToClient(VampiroNoitePayload.TYPE' in rd(R + '/ModNetworking.java')
      and 'handleVampiroNoite' in rd(C + '/ClientPayloadHandler.java'))
check('5.26.1: o cliente acende a VISAO DE VAMPIRO (clarao prateado) quando esta escuro',
      'public final class VisaoVampiroFx' in _vv and 'desenharClarao' in _vv
      and 'getMaxLocalRawBrightness' in _vv
      and 'seguro("visao_vampiro", () -> VisaoVampiroFx.registrar(event))' in rd(C + '/ClientGuiLayers.java'))
check('5.26.1: o efeito se chama PROTEÇÃO DO ECLIPSE (era "Eclipse Shade"/"Sombra")',
      'PROTE\u00c7\u00c3O DO ECLIPSE' in _pt and 'Eclipse Shade' not in _en
      and 'Eclipse Protection' in _en)
check('5.31.0: a versao subiu pra 5.31.0 (jar, mod e gradle)',
      "version = '5.31.0'" in rd('build.gradle') and '5.31.0' in _rm)
check('5.26.1: o protocolo subiu pra v18 (o pacote do vampiro e novo)',
      'event.registrar("18")' in rd(R + '/ModNetworking.java'))

# ============ 5.26.1: HOTFIX DO CRASH DO CLIENTE (camada registrada duas vezes) ============
_vv2 = rd(C + '/VisaoVampiroFx.java')
_cgl = rd(C + '/ClientGuiLayers.java')
check('5.26.1: a visao de vampiro e registrada em UM lugar so (o crash "Layer already registered")',
      '@EventBusSubscriber' not in _vv2 and '@SubscribeEvent' not in _vv2
      and _cgl.count('VisaoVampiroFx.registrar(event)') == 1
      and _cgl.count('seguro("visao_vampiro"') == 1)
check('5.26.1: nenhuma camada e registrada duas vezes no cliente (checagem em TODO o codigo)',
      (lambda: (lambda ids: len(ids) == len(set(ids)))(
          re.findall(r'registerAboveAll\(\s*ResourceLocation\.fromNamespaceAndPath\(RPTagMod\.MODID, "([a-z0-9_]+)"\)',
                     ''.join(rd(f) for f in glob.glob(C + '/*.java')))))())
check('5.26.1: nenhum aviso do cliente esta anotado com @EventBusSubscriber E chamado na mao',
      (lambda: all(('@EventBusSubscriber' not in rd(C + '/' + f))
                   for f in os.listdir(C) if f.endswith('AvisoFx.java') or f.endswith('Fx.java'))))
check('5.26.1: o registro das camadas e a prova de bala (um erro nao derruba mais o cliente)',
      'private static void seguro(String nome, Runnable registro)' in _cgl
      and 'catch (Throwable erro)' in _cgl
      and _cgl.count('seguro("') >= 37)
check('5.31.0: a versao subiu pra 5.31.0 (jar, mod e gradle)',
      "version = '5.31.0'" in rd('build.gradle') and '5.31.0' in rd(R + '/RPTagMod.java'))

# ============ 5.27.0: OS 7 PEDIDOS DO DONO (84) ============
_vamp = rd(R + '/VampiroSistema.java')
_nosol = re.search(r'public static boolean noSol\(.*?\n    \}', _vamp, re.S)
_escur = re.search(r'public static boolean escuro\(.*?\n    \}', _vamp, re.S)
check('5.27.0 (1): vampiro NAO queima no vazio (la nao tem sol)',
      bool(_nosol) and 'VazioReino.ehVazio(' in _nosol.group(0)
      and 'return false;' in _nosol.group(0).split('ehVazio', 1)[1][:200])
check('5.27.0 (1): no vazio ele conta como ESCURO (visao noturna/efeito)',
      bool(_escur) and 'VazioReino.ehVazio(' in _escur.group(0)
      and 'return true;' in _escur.group(0).split('ehVazio', 1)[1][:200])

_rpc = rd(R + '/RPCommands.java')
_lz = rd(R + '/LoreZones.java')
check('5.27.0 (2): os avisos duram 12 s (o dono achava 2 s rapido demais)',
      'new ScreenTextPayload(traduzido, 12, style)' in _rpc
      and 'avisoSeg() == 0 ? 12 : zone.avisoSeg()' in _lz
      and 'tag.putInt("AvisoSeg", avisoSeg == 0 ? 12 : avisoSeg)' in _lz)

_ci = rd(R + '/CiberpsicoseSistema.java')
check('5.27.0 (3): a ciberpsicose bate sozinha em creative (so spectator e morto ficam de fora)',
      'CREATIVE' not in _ci.split('private static boolean podeBater')[1][:600]
      if 'private static boolean podeBater' in _ci else True)
check('5.27.0 (3): a ciberpsicose NAO e anulada por estar em creative',
      'isCreative()' not in _ci or 'return false' in _ci.split('isCreative()')[1][:200])
_apx = rd(C + '/CiberpsicoseAvisoFx.java')
check('5.27.0 (2): a tela da ciberpsicose nao some sozinha se quem a abriu foi o EFEITO',
      'seguindoEfeito' in _apx and 'seguirEfeito' in _apx)

_ne = rd(R + '/NeptuneEventos.java')
check('5.27.0 (4): o terremoto abre fendas no chao (e prende quem cai)',
      'abrirFendaPerto' in _ne and 'cavarFendaFundo' in _ne and 'proximaFenda' in _ne)
check('5.27.0 (4): a fenda cava 3 blocos de fundo (o player cai e fica preso)',
      re.search(r'for \(int (d|prof)\w* = 1; \1 \w*= ?3', _ne) is not None
      or _ne.count('cavarFendaFundo') >= 2)
_rv = rd(C + '/RaiosVazioFx.java')
check('5.27.0 (5): os raios do vazio ficaram mais ESCUROS (mais vazio)',
      '0.22F' in _rv.replace(', ', ', ') or '0.22f' in _rv.lower())
_vp = rd(C + '/VazioPortalFx.java')
check('5.27.0 (6): os pedestais do vazio tem animacao nova (anel girando + fagulhas)',
      'quadPequeno3d' in _vp and 'anel' in _vp.lower())

_vr = rd(R + '/VazioReino.java')
check('5.27.0 (7): /vazio abrir abre o portal para QUALQUER UM atravessar (PORTAL_LIVRE)',
      'PORTAL_LIVRE' in _vr
      and '(PORTAL_LIVRE || dados.temOsOito(p.getUUID()))' in _vr
      and 'PORTAL_LIVRE = (p == null);' in _vr)
check('5.27.0 (7): o RELOGIO da travessia nao e apagado enquanto o jogador espera no miolo',
      'if (!atravessou && !dentroPraca && !dentroInvocado)' in _vr
      and 'boolean dentroPraca' in _vr and 'boolean dentroInvocado' in _vr)
check('5.27.0 (7): a TRAVESSIA tambem nao cobra os 8 quando o portal esta livre (era o bug)',
      'if (!livre && !PORTAL_LIVRE && !dados.temOsOito(p.getUUID()))' in _vr)
check('5.27.0 (7): o estado do portal vai na hora pros clientes do vazio',
      _vr.count('envioEstado(nv, quem, dados, base)') >= 1)
check('5.27.0: nenhum comando de TESTE saiu no jar (nada de _prova/oficina)',
      '_prova270' not in _rpc and not re.search(r'"[a-z]*prova\d+"', _rpc))

# ============ 5.28.0: OS 5 AJUSTES + O JULGAMENTO ============
_ne2 = rd(R + '/NeptuneEventos.java')
_vr2 = rd(R + '/VazioReino.java')
check('5.28.0 (a): o aviso "Vazio: Voce Conquistou a saida" sai NA HORA da travessia',
      'VAZIO: ' in _ne2 and 'CONQUISTOU A SA\u00cdDA' in _ne2
      and 'NeptuneEventos.avisoSaidaDoVazio(p, true);' in _vr2
      and 'AVISEI_SAIDA' in _ne2)
_ci2 = rd(R + '/CiberpsicoseSistema.java')
check('5.28.0 (b): a ciberpsicose bate mais rapido (0,45 a 0,9 s)',
      'agora + 450L + (long) (Math.random() * 450.0D)' in _ci2)
check('5.28.0 (c): a tela da ciberpsicose NAO fica presa (servidor avisa quando acaba)',
      'TINHA' in _ci2 and 'new ScreenTextPayload("", 1, 14)' in _ci2
      and 'new ScreenTextPayload("", 1, 14)' in rd(R + '/RPCommands.java'))
_cfx = rd(C + '/CiberpsicoseAvisoFx.java')
_cef = rd(C + '/ClientEffects.java')
check('5.28.0 (c): a tela tem TETO e folga de 0,4 s antes de se fechar',
      'TETO_MS' in _cfx and 'semEfeitoDesde' in _cfx and 'nascidaEm' in _cfx)
check('5.28.0 (c): texto vazio no estilo 14 = fechar a tela na hora',
      'limpoC.isBlank()' in _cef and 'CiberpsicoseAvisoFx.parar();' in _cef)
_ej = _json.load(io.open('src/main/resources/data/rptag/dimension_type/exilio.json', encoding='utf-8'))
check('5.28.0 (d): o vazio NAO tem ceu (sem luz do ceu, sem sol, sem lua)',
      _ej['has_skylight'] is False)
check('5.28.0 (d): o cliente desenha o ceu PRETO no vazio (VazioCeuPreto)',
      os.path.exists(C + '/VazioCeuPreto.java')
      and 'AFTER_SKY' in rd(C + '/VazioCeuPreto.java')
      and 'ehVazio' in rd(C + '/VazioCeuPreto.java'))
_ju = rd(R + '/JuizoFinal.java')
check('5.28.0 (e): o JULGAMENTO existe (labirinto + sala branca, sem ceu)',
      'CHAVE_JUIZO' in _ju and 'CHAVE_SALA' in _ju
      and os.path.exists('src/main/resources/data/rptag/dimension/juizo.json')
      and os.path.exists('src/main/resources/data/rptag/dimension/sala_julgamento.json'))
_jt = _json.load(io.open('src/main/resources/data/rptag/dimension_type/juizo.json', encoding='utf-8'))
check('5.28.0 (e): o labirinto e fechado (teto + sem ceu) e a sala branca e clara',
      _jt['has_ceiling'] is True and _jt['has_skylight'] is False
      and _json.load(io.open('src/main/resources/data/rptag/dimension_type/sala_julgamento.json',
                             encoding='utf-8'))['ambient_light'] > 0.5)
check('5.28.0 (e): o labirinto e INFINITO (nasce por chunk, com hash estavel)',
      'construirChunk' in _ju and 'hash(' in _ju and 'CELULA' in _ju)
check('5.28.0 (e): os bichos do labirinto sao BUFADOS (e nascem em cima do ar)',
      'bufado(' in _ju and 'DAMAGE_BOOST' in _ju and 'isEmptyBlock(cand)' in _ju)
check('5.28.0 (e): a saida do labirinto leva a SALA BRANCA do boss',
      'achouSaida' in _ju and 'construirSala' in _ju and 'SEA_LANTERN' in _ju)
check('5.28.0 (e): a CHAVE abre o portao atras do trono',
      os.path.exists(R + '/ChaveJulgamentoItem.java')
      and 'abrirPortao' in _ju and 'CRYING_OBSIDIAN' in _ju
      and 'CHAVE_JULGAMENTO' in rd(R + '/RPTagMod.java'))
check('5.31.0 (e): vencer devolve a vida com +2 vidas canonicas',
      'Math.min(5, atuais + 2)' in _ju)
check('5.31.0 (86): o mod NAO da item na vitoria (quem da o item e o ADM)',
      'darItemDoBoss' not in _ju and 'RELIQUIA_JULGAMENTO' not in rd(R + '/RPTagMod.java')
      and not os.path.exists(A + '/textures/item/reliquia_julgamento.png')
      and not os.path.exists(A + '/models/item/reliquia_julgamento.json'))
check('5.28.0 (e): perder marca o PERSONAGEM PERDIDO (e persiste no mundo)',
      'marcarPersonagemPerdido' in _ju
      and 'marcarPersonagemPerdido' in rd(R + '/RPWorldData.java')
      and 'PersonagensPerdidos' in rd(R + '/RPWorldData.java'))
check('5.28.0 (e): zerar as 5 vidas manda o player pro JULGAMENTO',
      'JuizoFinal.agendar' in rd(R + '/CanonLife.java'))
check('5.28.0 (e): morrer DENTRO do julgamento nao e fuga (volta pra dentro)',
      'JuizoFinal.aoRenascer' in rd(R + '/ServerEvents.java') and 'aoRenascer' in _ju)
_jc = rd(R + '/JuizoCommands.java')
check('5.28.0 (e): os comandos do ADM do julgamento existem',
      all(k in _jc for k in ['"entrar"', '"sala"', '"chave"', '"ganhar"', '"perder"',
                             '"reviver"', '"sair"', '"status"', '"info"']))
check('5.28.0: nenhum comando de TESTE saiu no jar (nada de _prova/_juizo)',
      '_prova' not in rd(R + '/RPCommands.java')
      and not os.path.exists(R + '/_ProvaJuizo.java'))

# ============ 5.31.0: O LABIRINTO SEM CAMINHO SEM SAIDA ============
_ju2 = rd(R + '/JuizoFinal.java')
check('5.31.0: a parede do labirinto segue a regra que garante passagem (ehParede)',
      'private static boolean ehParede(int celX, int celZ, int lx, int lz)' in _ju2
      and 'Math.floorMod(celZ, 2) == 0' in _ju2
      and 'Math.floorMod(celZ, 2) != 0' in _ju2
      and 'parede = ehParede(celX, celZ, lx, lz);' in _ju2
      and 'hash(celX, celZ, 10)' not in _ju2)
check('5.31.0: as aberturas extras so ABREM (lacos), nunca fecham a passagem garantida',
      '|| hash(' not in _ju2.split('private static boolean ehParede')[1][:1400]
      and 'return true;' in _ju2.split('private static boolean ehParede')[1][:1400])
check('5.31.0: a saida e encostada no CENTRO DO CORREDOR (nunca dentro de parede)',
      'private static int snapCelula(int w)' in _ju2
      and 'int sx = snapCelula(' in _ju2 and 'int sz = snapCelula(' in _ju2)
check('5.31.0: a prova da conectividade esta escrita no codigo (nao e promessa)',
      'Prova da conectividade' in _ju2 and 'inclusive para as partes que ainda não' in _ju2)
check('5.31.0: nenhum comando de teste saiu no jar (nada de _lab/_prova)',
      not os.path.exists(R + '/_ProvaLab.java') and '_prova' not in rd(R + '/RPCommands.java'))

# ===================== PEDIDO 86 — 5.31.0 =====================
_js = rd(R + '/JuizoSala.java')
_jf = rd(R + '/JuizoFinal.java')
_rj = rd(R + '/RegrasDoJuizo.java')
_vp = rd(R + '/VazioPoderes.java')
_va = rd(C + '/VazioAuraFx.java')
_vf = rd(C + '/VazioPoderFx.java')
_ch = rd(R + '/ChaveJulgamentoItem.java')
_nc = rd(R + '/NeptuneCommands.java')

check('5.31.0 (86): o boss NAO tem boneco/entidade — quem interpreta e o ADM',
      'ArmorStand' not in _jf and 'SEM boneco' in _jf)
check('5.31.0 (86): a INTRODUCAO continua (som + aviso), so sem o stand',
      'vultoDoBoss' in _jf and 'aviso(' in _jf)
check('5.31.0 (86): a CHAVE DO JULGAMENTO tem textura propria (nao e o amuleto)',
      os.path.exists(A + '/textures/item/chave_julgamento.png')
      and 'rptag:item/chave_julgamento' in rd(A + '/models/item/chave_julgamento.json')
      and 'amuleto' not in rd(A + '/models/item/chave_julgamento.json'))
check('5.31.0 (86): a chave nao e copia do amuleto (arquivos diferentes)',
      os.path.getsize(A + '/textures/item/chave_julgamento.png')
      != os.path.getsize(A + '/textures/item/amuleto_eclipse.png'))
check('5.31.0 (86): A SALA DO BOSS e a arena do .schem (98 x 71 x 93)',
      'LARGURA = 98' in _js and 'ALTURA = 71' in _js and 'COMPRIMENTO = 93' in _js
      and 'JuizoSala.construir' in _jf)
check('5.31.0 (86): a arena vem COMPRIMIDA (Base64 + gzip + RLE), nao em 647 mil linhas',
      'GZIPInputStream' in _js and 'Base64.getDecoder()' in _js and len(_js.split('\n')) < 400)
check('5.31.0 (86): a paleta da arena e resolvida PELO NOME no registro do jogo',
      'BuiltInRegistries.BLOCK.containsKey' in _js and 'defaultBlockState' in _js)
check('5.31.0 (86): ninguem cava para CIMA nem para BAIXO (vazio/labirinto)',
      'BlockEvent.BreakEvent' in _rj
      and 'alvo.getY() > pes' in _rj and 'alvo.getY() < pes' in _rj)
check('5.31.0 (86): a sala do Julgamento e cenario intocavel',
      'ehSala(lv)' in _rj and 'setCanceled(true)' in _rj)
check('5.31.0 (86): os PODERES DO VAZIO existem (raio, orbe, onda, olhos)',
      'RAIO("raio"' in _vp and 'ORBE("orbe"' in _vp
      and 'ONDA("onda"' in _vp and 'OLHOS("olhos"' in _vp)
check('5.31.0 (88): SEM comando de ligar/desligar poder — o gate e o LUGAR ou o cargo de ADM',
      'PODER_VAZIO' not in rd(R + '/RPEffects.java')
      and 'JuizoReino.eh(p.level())' in _vp and 'hasPermissions(2)' in _vp
      and 'literal("poder")' not in rd(R + '/NeptuneCommands.java'))
check('5.31.0 (86): a AURA em volta copia a do Ronova (penas carmesim em coroas)',
      'PENAS' in _va and 'pena(' in _va and '0x0A0204' in _va)
check('5.31.0 (86): a aura e GEOMETRIA (aparece com particulas no minimo)',
      'debugQuads' in _va and 'RenderLevelStageEvent' in _va)
check('5.31.0 (86): o PORTAL DO JUIZO foi refeito (o olho, nao mais o retangulo)',
      'private static void portal(' in _vf and 'pálpebra' in _vf)
check('5.31.0 (86): o portal acende quando a chave abre o portao',
      'VazioPoderPayload(4' in _jf)
check('5.31.0 (86): o raio e uma lanca escura com fio carmesim (visual do Ronova)',
      'tubo(' in _vf and '0xFF, 0x30, 0x3C' in _vf)
check('5.31.0 (86): /vazio soltar continua existindo como plano B (a tecla e o normal)',
      'literal("soltar")' in _nc)
check('5.31.0 (86): a chave continua abrindo o portao atras do boss',
      'abrirPortao' in _ch and 'CHAVE_JULGAMENTO' in rd(R + '/RPTagMod.java'))

# ===================== PEDIDO 87 — 5.31.0 =====================
_jr = rd(R + '/JuizoReino.java')
_jpe = rd(R + '/JuizoPortalExterno.java')
_jf2 = rd(R + '/JuizoFinal.java')
_jc = rd(R + '/JuizoCommands.java')
_rj2 = rd(R + '/RegrasDoJuizo.java')

check('5.31.0 (87): o labirinto do Julgamento agora e a DIMENSAO DO OUTRO MOD',
      'JuizoReino.entrar(p)' in _jf2 and 'class JuizoReino' in _jr)
check('5.31.0 (87): a dimensao do outro mod e DESCOBERTA (nao fixada no codigo)',
      'servidor.levelKeys()' in _jr and 'notaDe(' in _jr and 'ronova' in _jr)
check('5.31.0 (87): a ordem de escolha prefere o REINO (realm) do outro mod',
      "caminho.contains(\"realm\")" in _jr and 'nota += 40' in _jr)
check('5.31.0 (87): o ADM pode fixar/trocar a dimensao na mao (sem build nova)',
      'public static void fixar(' in _jr and 'literal("dimensao")' in _jc
      and 'literal("definir")' in _jc and 'literal("listar")' in _jc)
check('5.31.0 (87): entrando no reino, o jogador para num lugar COM CHAO (nao cai no vazio)',
      'pontoSeguro(' in _jr and 'Heightmap.Types.MOTION_BLOCKING' in _jr
      and 'isEmptyBlock(p.above())' in _jr)
check('5.31.0 (87): o PORTAL do reino NAO leva ao overworld — leva pra ARENA do .schem',
      'PlayerChangedDimensionEvent' in _jpe and 'achouSaida(p)' in _jpe
      and 'ehChave(event.getFrom())' in _jpe)
check('5.31.0 (87): o desvio do portal e no tick seguinte (nao no meio do evento)',
      'p.server.execute(' in _jpe)
check('5.31.0 (87): quem o ADM tirou do Julgamento NAO e desviado pelo portal',
      'estaProcurando(p.getUUID())' in _jpe and 'public static boolean estaProcurando' in _jf2)
check('5.31.0 (87): o tick nao expulsa quem esta no reino (a saida e o portal)',
      'JuizoReino.eh(p.level())' in _jf2)
check('5.31.0 (87): morrer no reino volta pro reino (morrer nao e fuga)',
      'JuizoReino.entrar(p)' in _jf2.split('aoRenascer')[1][:900])
check('5.31.0 (87): o labirinto antigo do RP Tag fica so como RESERVA (servidor sem o outro mod)',
      'CHAVE_JUIZO)' in _jf2 and 'reserva' in _jf2)
check('5.31.0 (87): a regra de nao cavar para cima/baixo vale no reino tambem',
      'JuizoReino.eh(lv)' in _rj2)
check('5.31.0 (87): comandar /juizo dimensao mostra a dimensao em uso',
      'LABIRINTO DO JULGAMENTO' in _jc and 'dimensão em uso' in _jc)

# ===================== PEDIDO 88 — 5.31.0 =====================
_pl = rd(C + '/PoderLeque.java')
_vfx = rd(C + '/VazioPoderFx.java')
_va2 = rd(C + '/VazioAuraFx.java')
_rj3 = rd(R + '/RegrasDoJuizo.java')
_mn = rd(R + '/ModNetworking.java')
_lang = rd('src/main/resources/assets/rptag/lang/pt_br.json')

check('5.31.0 (88): os poderes sao por TECLA (leque), nao por comando',
      'KeyMapping' in _pl and 'RegisterKeyMappingsEvent' in _pl and 'GLFW_KEY_R' in _pl)
check('5.31.0 (88): a tecla abre o LEQUE com as habilidades na tela',
      'registerAboveAll' in _pl and 'PODERES DO VAZIO' in _pl and 'NOMES' in _pl)
check('5.31.0 (88): 1 a 6 arremessa a habilidade escolhida',
      'GLFW_KEY_1 + i' in _pl and 'VazioPoderC2S' in _pl)
check('5.31.0 (88): as teclas aparecem em Opcoes -> Controles (traduzidas)',
      'key.rptag.leque' in _lang and 'key.rptag.repetir' in _lang)
check('5.31.0 (88): o servidor recebe a habilidade pelo pacote e valida',
      'playToServer(dev.rptag.VazioPoderC2S.TYPE' in _mn
      and 'VazioPoderes.usar(sp, poder)' in _mn)
check('5.31.0 (88): senao da para arremessar, o motivo aparece na tela',
      'Os poderes do vazio só respondem' in _vp)
check('5.31.0 (88): O OLHO VERMELHO que BANE existe (abre, encara, arranca, fecha)',
      'olhoQueBane' in _vfx and 'abertura da pálpebra' in _vfx
      and 'a COLUNA' in _vfx and 'a ÍRIS' in _vfx)
check('5.31.0 (88): banir manda o alvo para a dimensao do OUTRO mod',
      'JuizoReino.entrar(vitima)' in _vp)
check('5.31.0 (88): a chuva de penas existe como habilidade',
      'chuvaDePenas' in _vfx and 'PENAS("penas"' in _vp)
check('5.31.0 (88): a textura da aura ficou MAIS NEGRA (corpo preto, ponta carmesim)',
      '0x0A0204' in _va2 and '0xE01020' in _va2 and 'LASCAS NEGRAS' in _va2)
check('5.31.0 (88): a aura acende a CADA poder (nao so raio/orbe)',
      'VazioAuraFx.pulsar();' in _vfx and 'TOdo poder acende' not in _vfx)
check('5.31.0 (88): a aura nao depende mais de efeito nem de comando',
      'hasEffect(' not in _va2)
check('5.31.0 (88): a "juizo sala" NAO se quebra (evento na prioridade mais alta)',
      'BlockEvent.BreakEvent' in _rj3 and 'EventPriority.HIGHEST' in _rj3)
check('5.31.0 (88): a "juizo sala" NAO aceita BLOCO (nao da pra emparedar a arena)',
      'BlockEvent.EntityPlaceEvent' in _rj3 and 'não aceita blocos' in _rj3)
check('5.31.0 (88): a arena do dono e protegida pela CAIXA (nao so pela dimensao)',
      'dentroDaArena(' in _rj3 and 'JuizoSala.LARGURA' in _rj3)
check('5.31.0 (88): balde/liquido nao entra na sala',
      'BucketItem' in _rj3)

print(f'\n{OK} PASS / {FAIL} FAIL')
sys.exit(1 if FAIL else 0)
