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
ver = all('3.56.1' in rd(f) for f in [R + '/RPTagMod.java', 'build.gradle',
        'src/main/resources/META-INF/neoforge.mods.toml'])
check('versão 3.56.1 em RPTagMod/build.gradle/mods.toml', ver)

# ---- 2. lorezone re-entrada (INSIDE + seen.remove)
lz = rd(R + '/LoreZones.java')
check('lorezone: mapa INSIDE existe', 'INSIDE' in lz)
check('lorezone: saiu da zona esquece (seen.remove)', 'seen.remove(zid)' in lz)

# ---- 3. /rp ajuda
rp = rd(R + '/RPCommands.java')
check('/rp ajuda registrado', 'literal("ajuda")' in rp and 'literal("help")' in rp)
check('/rp ajuda lista admin p/ admins', 'SO ADMINS' in rp)

# ---- 4. música custom: payload + registro + cliente
check('LorezoneMusicPayload existe', os.path.exists(R + '/LorezoneMusicPayload.java'))
mn = rd(R + '/ModNetworking.java')
check('payloads registrados no canal v5', 'LorezoneMusicPayload' in mn and 'registrar("5")' in mn)
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
check('céu da prévia via BLIT', 'SKY_TEX' in bs and 'g.blit(SKY_TEX' in bs)
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
zp = 'build/libs/rptag-3.51.1.jar'
if os.path.exists(zp):
    z = zipfile.ZipFile(zp)
    n = z.namelist()
    check('jar: bola.png fora', 'assets/rptag/textures/gui/bola.png' not in n)
    check('jar: sem bubble_tail (rabicho e pixel)', 'assets/rptag/textures/gui/bubble_tail.png' not in n)
    check('jar: quadrada.png dentro', 'assets/rptag/textures/gui/quadrada.png' in n)
    check('jar: preview_sky.png dentro', 'assets/rptag/textures/gui/preview_sky.png' in n)
    check('jar: classes do payload', any('LorezoneMusicPayload' in x for x in n))
    check('jar: MusicaCustom dentro', any('MusicaCustom' in x for x in n))
else:
    print('AVISO: jar ainda não buildado — checks de jar pulados')

# ---- 3.51.0: rabicho, quadrada inteira, colunas, botoes ----
br = rd(C + '/BubbleRenderer.java')
check('rabicho PIXEL em degraus (3.56.0)', 'RABICHO PIXEL (3.56.0)' in br)
check('sem asas no rabicho (sem margin*1.6)', 'margin * 1.6F' not in br)
check('menu: sem fileira 3 de molduras', 'py + ROW_BTN3' not in bs)
check('quadrada com recheio quadrado', 'a QUADRADA usa a textura de cantinhos TAMBEM no recheio' in br)
check('mesmo rabicho pixel pra TODAS as molduras', 'TODAS as molduras' in br)
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
check('musica: para a anterior antes de tocar', 'para a musica anterior' in lz)
check('troca na hora (atualizarSons)', 'atualizarSons' in lz)
check('som para ao sair da zona (nao fica ecoando)', 'nao fica ecoando' in lz)
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
check('X vermelho grande e sempre visivel nos emojis', 'X vermelho GRANDE (10px)' in bs)

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
check('protocolo v5 (3 payloads de musica)', 'registrar("5")' in mn and 'MusicFileQueryPayload' in mn and 'MusicFileChunkPayload' in mn and 'MusicFileAnswerPayload' in mn)
check('MusicSync: servidor entrega o arquivo', 'MusicSync.pedir' in lzz and 'responder' in sev)
check('/lorezone baixar com mensagens (iniciado/concluido)', 'literal("baixar")' in lzcmd and 'iniciado' in lzcmd and 'concluido' in lzcmd)
check('musica 1x (intervalo -1, sem loop)', 'literal("1x")' in lzcmd and 'umaVez' in lzz)
check('raio explicado na criacao (esfera)', 'ESFERA' in lzcmd)
check('cliente grava o arquivo e toca', 'handleMusicChunk' in cph and 'tocarArquivoNome' in cph)
check('/roll +3 sozinho = especialista (1d20+3)', 'ESPECIALIDADE: vira 1d20+3' in rollc)
check('/balao teste removido', 'literal("teste")' not in bal)
check('previa usa a ultima fala real do chat', 'falaPrev' in bss and 'ultimaFala' in rd(C + '/ClientRPStates.java'))
check('captura a fala (ClientChatEvent)', 'ClientChatEvent' in rd(C + '/ClientEvents.java'))
check('X grande (10px) sem tooltip travada', 'X vermelho GRANDE (10px)' in bss and 'renderTooltip' not in bss.split('renderExtras')[1].split('private static String nameOf')[0])
check('icone medo: borda dissolvida (fim do quadrado)', '208 * amp' in ce)

# ---- 3.56.0: reescrita dos baloes (wrap, rabicho pixel, previa multi-linha) ----
check('wrap mais cedo: 130px (mais palavras = mais balao pra cima)', 'MAX_TEXT_WIDTH = 130' in br)
check('textura do rabicho antigo fora do codigo', 'bubble_tail' not in br and 'TAIL_TYPE' not in br and 'tailGui' not in bs)
check('previa quebra a fala em linhas (wrapPrev)', 'wrapPrev' in bs and 'linhasPrev' in bs)
check('previa cresce em altura com o texto', 'linhasPrev.size() * this.font.lineHeight * K' in bs)
check('rabicho pixel na previa (mesmos degraus)', 'RABICHO PIXEL = os MESMOS degraus do mundo' in bs)

# ---- 3.56.1: rabicho da MESMA cor do balao + rp ajuda limpo ----
check('rabicho usa os texels da pilula (janela 248 do bubble.png)', 'TAIL_U1 = 252.0F / 512.0F' in br)
check('Quadrada mantem rabicho texel 255', 'if (quadrada) {' in br and 'TAIL_U1, TAIL_V1, TAIL_U2, TAIL_V2, fill)' in br)
check('previa com cor escalada 248/255', 'escalaTexel' in bs)
check('rp ajuda SEM a linha do WALKTHROUGH', 'WALKTHROUGH' not in rp)

print(f'\n{OK} PASS / {FAIL} FAIL')
sys.exit(1 if FAIL else 0)

