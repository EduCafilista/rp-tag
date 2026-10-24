#!/usr/bin/env python3
"""Suite de verificações do RP Tag — roda ANTES de entregar um ciclo."""
import io, os, sys, zipfile

OK, FAIL = 0, 0
def check(nome, cond):
    global OK, FAIL
    print(('PASS  ' if cond else 'FAIL  ') + nome)
    OK, FAIL = OK + (1 if cond else 0), FAIL + (0 if cond else 1)

R = 'src/main/java/dev/rptag'
C = R + '/client'
A = 'src/main/resources/assets/rptag'

def rd(p):
    return io.open(p, encoding='utf-8').read()

# ---- 1. versão em 3 lugares
ver = all('4.8.0' in rd(f) for f in [R + '/RPTagMod.java', 'build.gradle',
        'src/main/resources/META-INF/neoforge.mods.toml'])
check('versão 4.8.0 em RPTagMod/build.gradle/mods.toml', ver)

# ---- 2. lorezone re-entrada (INSIDE + seen.remove)
lz = rd(R + '/LoreZones.java')
check('lorezone: mapa INSIDE existe', 'INSIDE' in lz)
check('3.99: AVISO unico e persistente (nao reseta ao sair; salvo no mundo)',
      'seen.remove(zid)' not in lz and 'avisosVistos' in lz
      and 'avisoVisto(uuid, zone.id())' in lz and 'marcarAvisoVisto(uuid, zone.id())' in lz
      and 'tag.put("AvisosVistos", vistos)' in lz)

# ---- 3. /rp ajuda
rp = rd(R + '/RPCommands.java')
check('/rp ajuda registrado', 'literal("ajuda")' in rp and 'literal("help")' in rp)
check('/rp ajuda lista admin p/ admins', 'SO ADMINS' in rp)

# ---- 4. música custom: payload + registro + cliente
check('LorezoneMusicPayload existe', os.path.exists(R + '/LorezoneMusicPayload.java'))
mn = rd(R + '/ModNetworking.java')
check('payloads registrados no canal v5', 'LorezoneMusicPayload' in mn and 'registrar("6")' in mn)
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
check('protocolo v5 (3 payloads de musica)', 'registrar("6")' in mn and 'MusicFileQueryPayload' in mn and 'MusicFileChunkPayload' in mn and 'MusicFileAnswerPayload' in mn)
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
check('aviso dura 8s (payload com estilo)', 'new ScreenTextPayload(traduzido, 8, style)' in rp)
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
check('coração 3D PROCEDURAL girando/batendo (BER — sólido voxel 4.4)',
      'RenderType.lightning()' in rdr and 'rotationDegrees' in rdr and 'pulso' in rdr
      and 'MASCARA' in rdr and 'SOLIDO' in rdr)
check('BER registrado (ClientRenderers, camada cliente)', 'registerBlockEntityRenderer' in rd(C + '/ClientRenderers.java'))
check('assets do coração completos (estado/modelo/item/textura)', _os.path.exists(A + '/textures/item/coracao_canon.png') and _os.path.exists(A + '/blockstates/coracao_canon.json') and '"elements": []' in rd(A + '/models/block/coracao_canon.json'))
check('protecao NÃO some no logout (relog nao burla)', 'PROTECAO_ATE é de propósito NÃO limpa' in cl)
check('status mostra X/5', '+ "/5"' in rp)
check('status mostra as vidas canônicas', 'Vidas canônicas:' in rp)
check('estilo 4 TEXTURIZADO estático (letras não mexem)', 'desenhaTextoTexturizado' in ce and 'Math.min(12, textStyle)' in ce)
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
check('4.0: boot log 4.8.0', 'RP Tag 4.8.0' in ce)

# ---- 3.86.0: CORAÇÃO 3D redesenhado + 6 estilos de aviso novos ----
ccr = rd(C + '/CanonCoracaoRenderer.java')
check('4.4: coracao = SOLIDO voxel (mascara 15x13 + 13 fatias infladas)',
      'MASCARA' in ccr and 'NY = 13' in ccr and 'construirSolido' in ccr and 'FATOR_PROF' in ccr)
check('4.4: so faces expostas + sombreamento por face (volume)',
      'construirFaces' in ccr and 'shade(' in ccr and 'tem(x, y - 1, z)' in ccr)
check('4.4: giro por relogio de parede (nao trava, nao pula) + batida tum-tum',
      'System.nanoTime()' in ccr and 'Axis.YP.rotationDegrees' in ccr
      and 'Math.exp(-bt * 18.0)' in ccr and 'getBlockPos().getX() * 37' in ccr)
ce = rd(C + '/ClientEffects.java')
check('3.98/4.5: client aceita estilos ate 12 (egipicio)', 'Math.min(12, textStyle)' in ce)
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
check('3.98/4.5: clamp de estilo da zona vai ate 12 (egipicio)', lz.count('Math.min(12,') == 2)

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
      and 'Math.min(12, textStyle)' in ce)
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
check('3.95: ajuda sem ascensao (com pesadelo2)', 'sangue, demoniaco, egipicio (alvos' in rp2
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
      and 'Math.min(12, textStyle)' in ce)
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
check('3.98/4.5: clamp de estilo max 12 (ClientEffects + LoreZones \u00d72)',
      'Math.min(12, textStyle)' in ce and lz.count('Math.min(12') == 2)

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
check('3.99: aviso so na PRIMEIRA entrada (entrouAgora + avisoVisto)',
      'boolean entrouAgora = !prev.contains(zone.id())' in lz
      and 'if (!data.avisoVisto(uuid, zone.id()))' in lz)
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
      'ScreenTextPayload(msg, 8, 4)' in rp and 'LIGHTNING_BOLT_THUNDER' in rp
      and 'FearPayload(30)' in rp)
check('4.4: tirar vida NAO congela o jogador (nao houve morte)',
      'FREEZES' not in rp.rsplit('vidaTirar', 1)[1].split('private static int vidaDar')[0])
check('4.4: ajuda lista o /rp admin vida', 'admin vida <jogador> tirar|dar' in rp)
check('4.4: coracao mantem o pickup SHIFT (+1 vida) e o teto 5/5',
      'isShiftKeyDown' in blk and 'vidas >= 5' in blk and 'NOVA CHANCE' in blk)

# ---- 4.6.0: Void e Divino em VIDEO (Blender) — tamanho dos videos no jar
def _tam_pasta(p):
    return sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(p) for f in fs)
for _id, _cls in (('void', 'VoidAvisoFx'), ('divino', 'DivinoAvisoFx')):
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
# meta ~15 MB; o Divino cobre a tela toda e o dono aprovou ~27 MB (cor em qualidade 75)
for _id, _teto in (('void', 16), ('divino', 27)):
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

check('4.5: regra PvP barra golpe corpo a corpo (AttackEntityEvent)',
      'AttackEntityEvent' in reg and 'isRegraPvp' in reg)
check('4.5: regra PvP barra dano indireto (flecha/pocao/TNT)',
      'LivingIncomingDamageEvent' in reg and 'getSource().getEntity()' in reg)
check('4.5: regra quebrar (BlockEvent.BreakEvent) e colocar (EntityPlaceEvent)',
      'BlockEvent.BreakEvent' in reg and 'EntityPlaceEvent' in reg
      and 'isRegraQuebrar' in reg and 'isRegraColocar' in reg)
check('4.5: balde tambem conta como colocar (TriState.FALSE)',
      'TriState.FALSE' in reg and 'BucketItem' in reg)
check('4.5: staff (permissao 2) passa pelas regras',
      reg.count('hasPermissions(2)') >= 4)
check('4.5: regras persistem no mundo (RegraPvp/Quebrar/Colocar)',
      'tag.putBoolean("RegraPvp"' in wdd and 'tag.putBoolean("RegraQuebrar"' in wdd
      and 'tag.putBoolean("RegraColocar"' in wdd)
check('4.5: /rp admin regra [pvp|quebrar|colocar] on|off + tudo bloquear|liberar',
      '.then(Commands.literal("regra")' in rp and 'mostrarRegras' in rp and 'liberar' in rp)

check('4.5: vida do admin acha jogador OFFLINE (cache de perfis)',
      'getProfileCache()' in vad and 'record Alvo' in vad)
check('4.5: vida offline guarda recado pro proximo login',
      'setRecadoVida' in rp and 'consumirRecadoVida' in rd(R + '/ServerEvents.java')
      and 'RecadosVida' in wdd)
check('4.5: vida em LOTE pede confirmacao (sem aplicar direto)',
      'guardar(' in rp and 'vidaConfirmar' in rp and 'VALIDADE_MS' in vad)
check('4.5: /rp admin vida sem argumento lista quem esta abaixo de 5/5',
      'vidaListar' in rp and 'canonLivesIds' in wdd)
check('4.5: ajuda lista regra + afk + lote', 'admin regra [pvp|quebrar|colocar]' in rp
      and 'admin afk tempo' in rp and 'vida lote A,B,C' in rp)

check('4.5: EGIPICIO e o estilo 12 (parse + nome + ajuda)',
      'case "egipicio", "egipcio" -> 12' in rp and 'case 12 -> "EGIPICIO"' in rp
      and 'egipicio (alvos' in rp)
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
check('4.7.1: REAL tem disco solar alado + friso de hieroglifos + colunas + placa',
      'discoSolar' in real and 'friso(' in real and 'colunas(' in real and 'placa(' in real)
check('4.7.1: REAL desenha a mensagem EMBAIXO do titulo (cy + 27k)',
      'cy + 27 * k' in real)
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
_literais = set(_re.findall(r'Commands\.literal\("([a-z0-9_]+)"\)', _comando))
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

# ============ 4.8.0: TABLET DA FICHA (tudo dentro do rp-tag) ============
_ficha = rd(R + '/FichaRp.java')
check('4.8.0: 8 capitulos, sem pergunta de RACA', 'CAPS = 8' in _ficha and 'raca' not in _ficha.lower())
check('4.8.0: capitulos nome/idade/origem/oficio/habilidades/historia/personalidade/resumo',
      all(k in _ficha for k in ['CAP_NOME', 'CAP_IDADE', 'CAP_ORIGEM', 'CAP_OFICIO',
                                'CAP_HABILIDADES', 'CAP_HISTORIA', 'CAP_PERSONALIDADE', 'CAP_RESUMO']))
check('4.8.0: 6 eixos de personalidade 0..100 com os rotulos do print',
      'Prudente' in _ficha and 'Corajoso' in _ficha and 'Pragmático' in _ficha
      and 'Empático' in _ficha and 'Espontâneo' in _ficha and 'Disciplinado' in _ficha
      and 'Tradicional' in _ficha and 'Curioso' in _ficha and 'Reservado' in _ficha
      and 'Sociável' in _ficha and 'Contente' in _ficha and 'Ambicioso' in _ficha)
check('4.8.0: ficha salva/carrega em NBT', 'putString("Nome"' in _ficha and 'getString("Nome")' in _ficha)
check('4.8.0: a ficha alimenta a Persona (nome/idade/descricao)',
      'new Persona(atual.nome, atual.idadeNumero(), atual.descricaoCurta())' in rd(R + '/FichaServer.java'))

_wd = rd(R + '/RPWorldData.java')
check('4.8.0: fichas guardadas no mundo (mapa + NBT "Fichas")',
      'getFicha(' in _wd and 'setFicha(' in _wd and 'put("Fichas"' in _wd and 'getList("Fichas"' in _wd)
check('4.8.0: item rptag:tablet registrado', 'ITEMS.register("tablet"' in rd(R + '/RPTagMod.java'))
check('4.8.0: tablet e um item (modelo + textura no jar)',
      os.path.exists(A + '/models/item/tablet.json') and os.path.exists(A + '/textures/item/tablet.png'))
check('4.8.0: nome do item traduzido pt_br/en_us',
      'item.rptag.tablet' in rd(A + '/lang/pt_br.json') and 'item.rptag.tablet' in rd(A + '/lang/en_us.json'))
check('4.8.0: clique direito abre, shift+direito mostra no chat',
      'FichaServer.abrir(sp)' in rd(R + '/TabletItem.java') and 'FichaLivro.mostrar' in rd(R + '/TabletItem.java'))
check('4.8.0: todo jogador recebe o tablet no login (sem duplicar)',
      'FichaServer.garantirTablet(joined)' in rd(R + '/ServerEvents.java')
      and 'if (jaTemTablet(sp))' in rd(R + '/FichaServer.java'))
check('4.8.0: canais ficha_abrir (S2C) e ficha_salvar (C2S) no protocolo v6',
      'registrar("6")' in mn and 'FichaOpenPayload' in mn and 'FichaSavePayload' in mn)
_fs = rd(C + '/FichaScreen.java')
check('4.8.0: tela da ficha existe com os 8 capitulos por pergunta',
      'class FichaScreen extends Screen' in _fs and 'CAP_PERSONALIDADE' in _fs)
check('4.8.0: rodape ESC · Sair / ENTER · Continuar', 'ESC · Sair' in _fs and 'ENTER · Continuar' in _fs)
check('4.8.0: quadradinhos de progresso + Voltar/Continuar',
      'renderProgresso' in _fs and '"Voltar"' in _fs and '"Continuar"' in _fs)
check('4.8.0: pagina final tem o botao Confirmar Personagem',
      'Confirmar Personagem' in _fs and 'confirmar()' in _fs)
check('4.8.0: personagem do proprio jogador na tela (modelo do player)',
      'renderEntityInInventoryFollowsAngle' in _fs and 'minecraft.player' in _fs)
_fundo = rd(C + '/FichaFundo.java')
check('4.8.0: fundo galaxia + buraco negro atras do personagem + disco no chao',
      'static void fundo(' in _fundo and 'buracoNegro' in _fundo and 'discoMagico' in _fundo)
check('4.8.0: animacao de transicao entre capitulos', 'tTroca' in _fs and 'suave(' in _fs)
check('4.8.0: personagem se mexe a cada pergunta (swing)',
      'p.swing(InteractionHand.MAIN_HAND)' in _fs and 'InteractionHand.OFF_HAND' in _fs)
check('4.8.0: campo de texto longo (historia quebra linha)',
      'class TextoLivre extends AbstractWidget' in rd(C + '/FichaWidgets.java'))
check('4.8.0: salva a cada capitulo (fechar no meio nao perde nada)',
      'salvar(false)' in _fs and 'FichaSavePayload' in _fs)

print(f'\n{OK} PASS / {FAIL} FAIL')
sys.exit(1 if FAIL else 0)
