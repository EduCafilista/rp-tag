package dev.rptag.client;

import dev.rptag.RPEffects;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Efeitos de tela do admin (3.44.0):
 *
 * <ul>
 *   <li><b>MEDO</b> — a camera treme (roll/yaw/pitch em frequencias
 *       diferentes, com entrada rapida e saida suave) + vinheta vermelha
 *       pulsando nas EXTREMIDADES da tela.</li>
 *   <li><b>AVISO</b> — texto GRANDE no centro da tela com as cores que o
 *       admin escolheu via codigos & (o servidor ja traduz p/ section),
 *       com scrim translucido pra leitura e leve pulso.</li>
 * </ul>
 *
 * A PROVA DE CRASH: qualquer erro desliga os efeitos desta sessao.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class ClientEffects {

    private ClientEffects() {
    }

    // (3.65.0) FLAGS SEPARADOS: um erro do medo NAO mata o aviso (e vice-
    // versa) — antes o flag compartilhado podia desligar TUDO silenciosamente.
    private static volatile boolean medoDisabled = false;
    private static volatile boolean avisoDisabled = false;
    /** diagnostico (3.65.0): o aviso chegou e esta sendo desenhado? */
    private static boolean LOGOU_AVISO = false;
    @SuppressWarnings("unused")
    private static boolean disabled = false; // (legado — mantido p/ compat)

    // (3.46.0) TEXTURAS HD: gradientes REAIS com anti-aliasing (supersample
    // 4x + LANCZOS) — fim do aspecto "pixel quebrado". Desenhadas pela API
    // moderna de quads com blend + shaderColor (alpha por draw).
    private static final net.minecraft.resources.ResourceLocation VINHETA_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/vinheta.png");
    private static final net.minecraft.resources.ResourceLocation MEDO_HD_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/medo_hd.png");
    private static final net.minecraft.resources.ResourceLocation GRAD_BAR_TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/grad_bar.png");

    private static volatile long fearStart = 0;
    private static volatile long fearUntil = 0;
    private static volatile String screenText = null;
    private static volatile long textStart = 0;
    private static volatile long textDuration = 0;
    // (3.78.0) ritual da vida perdida: VÉU avermelhado + audio da lore
    private static volatile long veilStart = 0;
    private static volatile long veilUntil = 0;
    private static volatile long somCanonDue = 0;
    private static volatile boolean somCanonPlayed = true;
    // (3.77.0) BANNER da vida canônica (LORE ON/OFF)
    private static volatile String bannerTitle = null;
    private static volatile String bannerSub = "";
    private static volatile long bannerStart = 0;
    private static volatile long bannerDuration = 0;
    private static volatile boolean bannerOn = true;
    /** (3.87.0) estilo do aviso em exibicao: 0 classico, 1 pesadelo,
     * 2 runico, 3 real, 4 morte, 5 inferno, 6 gelo, 7 divino, 8 void,
     * 9 sangue, 10 demoniaco. */
    private static volatile int textStyle = 0;

    /** Liga o MEDO por N segundos (servidor clampa 1..30). */
    public static void startFear(int seconds) {
        long now = System.currentTimeMillis();
        fearStart = now;
        fearUntil = now + Math.max(1, Math.min(30, seconds)) * 1000L;
    }

    /** Mostra o texto GRANDE por N segundos (servidor clampa 1..60). */
    public static void startScreenText(String text, int seconds, int style) {
        // (3.94.0) estilo 10 = DEMONIACO: o FX proprio (Chamado do Abismo:
        // mandibulas, fissuras, pentagrama, olho de fenda) assume. Codigos
        // & sao ignorados.
        if (style == 10) {
            String limpoD = text.replaceAll("\\u00a7.", "");
            DemoniacoAvisoFx.iniciar("AVISO", limpoD);
            RPTagMod.LOGGER.info("RP Tag: CHAMADO DO ABISMO iniciado (FX proprio)");
            return;
        }
        // (3.95.0) estilo 7 = DIVINO: o FX celestial (pilar de luz, halo
        // runico, asas, letras caindo) assume. SEM legenda fixa — embaixo
        // so aparece a mensagem digitada no comando.
        if (style == 7) {
            String limpo7 = text.replaceAll("\\u00a7.", "");
            DivinoAvisoFx.iniciar("AVISO", limpo7);
            RPTagMod.LOGGER.info("RP Tag: AVISO DIVINO iniciado (FX proprio)");
            return;
        }
        // (3.95.0) estilo 9 = SANGUE: o FX proprio (Sangria: impacto de
        // sangue, filetes pingando, poca, veias) assume.
        if (style == 9) {
            String limpo9 = text.replaceAll("\\u00a7.", "");
            SangueAvisoFx.iniciar("AVISO", limpo9);
            RPTagMod.LOGGER.info("RP Tag: SANGRIA iniciada (FX proprio)");
            return;
        }
        // (3.97.0) estilo 1 = PESADELO: FX "Seguido" (found-footage: a
        // figura palida que se aproxima em cortes, moldura tremendo).
        if (style == 1) {
            String limpo1 = text.replaceAll("\\u00a7.", "");
            PesadeloAvisoFx.iniciar("AVISO", limpo1);
            RPTagMod.LOGGER.info("RP Tag: SEGUIDO iniciado (FX proprio)");
            return;
        }
        // (3.96.0) estilo 8 = VOID: o FX proprio (Queda Sem Fundo: buraco
        // negro, tunel infinito, estrelas sugadas) assume.
        if (style == 8) {
            String limpo8 = text.replaceAll("\\u00a7.", "");
            VoidAvisoFx.iniciar("AVISO", limpo8);
            RPTagMod.LOGGER.info("RP Tag: QUEDA SEM FUNDO iniciada (FX proprio)");
            return;
        }
        // (3.98.0) estilo 11 = PESADELO2: o modelo ANTIGO "Sono Sem Descanso"
        // (tunel que respira, tentaculos com garras, 16 olhos) — fica ao
        // lado do "Seguido" (estilo 1) como segunda opcao de pesadelo.
        if (style == 11) {
            String limpo11 = text.replaceAll("\\u00a7.", "");
            PesadeloAntigoAvisoFx.iniciar("AVISO", limpo11);
            RPTagMod.LOGGER.info("RP Tag: SONO SEM DESCANSO (pesadelo2) iniciado (FX proprio)");
            return;
        }
        // (3.97.0) estilo 2 = RUNICO: FX "Traducao Arcana" (circulo de
        // invocacao, pilar de luz, chuva runica, texto em lingua encantada)
        if (style == 2) {
            String limpo2 = text.replaceAll("\\u00a7.", "");
            RunicoAvisoFx.iniciar("AVISO", limpo2);
            RPTagMod.LOGGER.info("RP Tag: TRADUCAO ARCANA iniciada (FX proprio)");
            return;
        }
        // (3.97.0) estilo 5 = INFERNO: FX "Fornalha" (muralha de fogo,
        // redemoinho, meteoros, brasas, texto de metal derretido)
        if (style == 5) {
            String limpo5 = text.replaceAll("\\u00a7.", "");
            InfernoAvisoFx.iniciar("AVISO", limpo5);
            RPTagMod.LOGGER.info("RP Tag: FORNALHA iniciada (FX proprio)");
            return;
        }
        // (3.97.0) estilo 6 = GELO: FX "Geada Eterna" (tela congelando,
        // trinca de vidro, cristais, floco de neve, texto congelando)
        if (style == 6) {
            String limpo6 = text.replaceAll("\\u00a7.", "");
            GeloAvisoFx.iniciar("AVISO", limpo6);
            RPTagMod.LOGGER.info("RP Tag: GEADA ETERNA iniciada (FX proprio)");
            return;
        }
        // (4.1.0) estilo 4 = MORTE: o FX "Linha Reta" (monitor cardiaco que
        // desacelera ate virar linha reta, cranio em pixel art que se forma
        // do po e vira cinza) assume o desenho. O VEU avermelhado e o AUDIO
        // da lore (canon_death, 1,5 s depois do strondo) continuam valendo:
        // sao ligados ANTES de sair.
        if (style == 4) {
            String limpo4 = text.replaceAll("\u00a7.", "");
            long agora4 = System.currentTimeMillis();
            veilStart = agora4;
            veilUntil = agora4 + Math.max(1, Math.min(60, seconds)) * 1000L;
            somCanonDue = agora4 + 1500L;
            somCanonPlayed = false;
            MorteAvisoFx.iniciar("AVISO", limpo4);
            RPTagMod.LOGGER.info("RP Tag: LINHA RETA (aviso de morte) iniciado (FX proprio)");
            return;
        }
        screenText = text;
        textStart = System.currentTimeMillis();
        textDuration = Math.max(1, Math.min(60, seconds)) * 1000L;
        textStyle = style;
        // (3.78.0) o aviso da VIDA PERDIDA (estilo 4) liga o véu avermelhado
        // e agenda o audio da lore p/ entrar ~1,5 s DEPOIS do strondo
        if (style == 4) {
            veilStart = textStart;
            veilUntil = textStart + textDuration;
            somCanonDue = textStart + 1500L;
            somCanonPlayed = false;
        }
        LOGOU_AVISO = false;
        RPTagMod.LOGGER.info("RP Tag: AVISO recebido (estilo {}, {}s) — exibindo", style, seconds);
    }
    /** (3.77.0) recebe o BANNER da vida canônica (LORE ON/OFF). */
    public static void startCanonBanner(String title, String sub, int seconds, boolean on) {
        bannerTitle = title;
        bannerSub = sub;
        bannerStart = System.currentTimeMillis();
        bannerDuration = Math.max(1, Math.min(30, seconds)) * 1000L;
        bannerOn = on;
    }

    static {
        RPTagMod.LOGGER.info("RP Tag 4.2.0: camada de avisos carregada");
    }

    /** (3.78.0) VÉU da vida perdida: tela MEIO AVERMELHADA durante o
     * aviso (camada oficial = com shaders) + quando o strondo do trovão
     * (que vem do servidor) ja passou, dispara O AUDIO da lore aqui no
     * cliente — a vítima ouve: TRONCO! … e a musica sombria entra. */
    public static void desenhaVeuCanon(GuiGraphics g) {
        try {
            long now = System.currentTimeMillis();
            if (!somCanonPlayed && now >= somCanonDue) {
                somCanonPlayed = true;
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                RPTagMod.SOM_CANON_DEATH.get(), 1.0F, 1.6F));
            }
            if (now >= veilUntil) {
                return;
            }
            int w = g.guiWidth();
            int h = g.guiHeight();
            float elapsed = now - veilStart;
            float total = Math.max(1.0F, veilUntil - veilStart);
            float in = Math.min(1.0F, elapsed / 300.0F);
            float out = Math.min(1.0F, (veilUntil - now) / 600.0F);
            float base = Math.min(in, out);
            // pulso lento (±10%): a tela "respira" vermelho
            float pulso = 0.9F + 0.1F * (float) Math.sin(now * 0.004);
            int alpha = Math.max(0, Math.min(255, (int) (0x5A * base * pulso)));
            if (alpha <= 0) {
                return;
            }
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.fill(0, 0, w, h, alpha << 24 | 0x5A0D0D);          // véu sangue
            g.fill(0, 0, w, 2, (int) (alpha * 0.8F) << 24 | 0x8B1616);
            g.fill(0, h - 2, w, h, (int) (alpha * 0.8F) << 24 | 0x8B1616);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } catch (Throwable t) {
            veilUntil = 0; // a prova de crash: véu some, jogo segue
            RPTagMod.LOGGER.error("RP Tag: erro no veu da vida perdida", t);
        }
    }

    /** (3.82.0) linha SOLIDA do banner: cor única, sombra dura e NADA de
     * halo — o brilho em -0.6px liam como "embaçado" (print 3.81). Duas
     * camadas só: sombra + preenchimento. Letra CRISP. */
    private static void linhaTexturizada(GuiGraphics g, Font font, float cx, float cy,
            String texto, float scale, float vis, int sombra, int base) {
        String limpo = texto.replaceAll("\u00a7.", "");
        if (limpo.isEmpty() || vis <= 0.0F) {
            return;
        }
        int totalW = font.width(limpo);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        float x = -totalW / 2.0F;
        int aFill = (int) (255 * vis) << 24;
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            float cw = font.width(String.valueOf(c));
            if (c != ' ') {
                String ch = String.valueOf(c);
                g.drawString(font, ch, x + 1.0F, 1.2F, sombra, false);   // sombra
                g.drawString(font, ch, x, 0.0F, aFill | (base & 0xFFFFFF), false);
            }
            x += cw;
        }
        g.pose().popPose();
    }

    /** (3.77.0) BANNER da vida canônica: "❀ LORE ON ❀" verde ou "❀ LORE
     * OFF ❀" vermelho com o sub "HARD-RP ON | VIDAS CANNON ..." — texto
     * TEXTURIZADO estático (2 linhas, nada se mexe), na camada oficial. */
    public static void desenhaBannerCanon(GuiGraphics g) {
        try {
            String title = bannerTitle;
            if (title == null) {
                return;
            }
            long now = System.currentTimeMillis();
            float elapsed = now - bannerStart;
            if (elapsed >= bannerDuration) {
                bannerTitle = null;
                return;
            }
            float fadeIn = Math.min(1.0F, elapsed / 250.0F);
            float fadeOut = Math.min(1.0F, (bannerDuration - elapsed) / 400.0F);
            float vis = Math.min(fadeIn, fadeOut);
            if (vis <= 0.0F) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int w = g.guiWidth();
            int h = g.guiHeight();
            // (3.81.0) paleta SÓLIDA e viva: ON = verde forte, OFF = vermelho
            // (sombra quase preta + brilho claro uniforme — sem manchas)
            int sombra = (int) (200 * vis) << 24 | (bannerOn ? 0x07130A : 0x160404);
            int base = (int) (255 * vis) << 24 | (bannerOn ? 0x3FA34D : 0xC41F1F);
            String sub = bannerSub;
            float tEsc = Math.min(2.6F, (w - 30.0F) / Math.max(1, font.width(
                    title.replaceAll("\u00a7.", ""))));
            float sEsc = Math.min(1.1F, (w - 30.0F) / Math.max(1, font.width(
                    sub.replaceAll("\u00a7.", ""))));
            float cyT = h * 0.34F;
            float cyS = cyT + font.lineHeight * tEsc / 2.0F + font.lineHeight * sEsc / 2.0F + 8.0F;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            // (3.79.0) FAIXA ESCURA atras das duas linhas — o verde/vermelho
            // texturizado morria contra o ceu (print: "quase nao consegue
            // enxergar"). Agora o banner le em qualquer cenario.
            int tW = (int) (font.width(title.replaceAll("\u00a7.", "")) * tEsc);
            int sW = (int) (font.width(sub.replaceAll("\u00a7.", "")) * sEsc);
            int bandW = Math.max(tW, sW) + 56;
            int bandY0 = (int) cyT - 16;
            int bandY1 = (int) (cyS + font.lineHeight * sEsc) + 16;
            // (3.81.0) faixa MAIS fechada (79% preto) com filete forte
            g.fill(w / 2 - bandW / 2, bandY0, w / 2 + bandW / 2, bandY1,
                    (int) (202 * vis) << 24);
            g.fill(w / 2 - bandW / 2, bandY0, w / 2 + bandW / 2, bandY0 + 2,
                    (int) (190 * vis) << 24 | (bannerOn ? 0x3FA34D : 0xC41F1F));
            g.fill(w / 2 - bandW / 2, bandY1 - 2, w / 2 + bandW / 2, bandY1,
                    (int) (190 * vis) << 24 | (bannerOn ? 0x3FA34D : 0xC41F1F));
            linhaTexturizada(g, font, w / 2.0F, cyT, title, tEsc, vis, sombra, base);
            linhaTexturizada(g, font, w / 2.0F, cyS, sub, sEsc, vis, sombra, base);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } catch (Throwable t) {
            bannerTitle = null; // a prova de crash: banner some, jogo segue
            RPTagMod.LOGGER.error("RP Tag: erro no banner da vida canonica", t);
        }
    }

    /** (3.64.0) TYPEWRITER do aviso: devolve so os caracteres que ja
     * "apareceram" — codigos de cor § NAO contam nem quebram no meio. */
    private static String typed(String s, long elapsedMs) {
        // (3.66.0) FIX DO "SO A 1a LETRA": o laco NUNCA anexava o caractere
        // normal (so os codigos de cor) — caia no fallback de 1 char. Agora
        // anexa; e comeca com 1 letra visivel no instante zero.
        int vis = 1 + (int) Math.min(998, elapsedMs / 30);
        StringBuilder sb = new StringBuilder(s.length());
        int shown = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                sb.append(c).append(s.charAt(++i));
                continue;
            }
            if (shown++ >= vis) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Mede o quanto o MEDO esta ativo (0..1), com entrada/saida suaves. */
    private static float fearAmp(long now) {
        if (now >= fearUntil) {
            return 0.0F;
        }
        float elapsed = (now - fearStart) / 1000.0F;
        float remaining = (fearUntil - now) / 1000.0F;
        return Math.min(1.0F, elapsed / 0.25F) * Math.min(1.0F, remaining);
    }

    /**
     * (3.54.0) DESENHO do medo na CAMADA OFICIAL de HUD — roda DEPOIS do
     * shader (aparece com Iris) e acima de tudo na tela. Veu REFORCADO
     * (168 -> 208): com shader ou sem, a tela afunda na escuridao.
     * A prova de crash: qualquer erro desliga o efeito nesta sessao.
     */
    public static void desenharMedoCamada(net.minecraft.client.gui.GuiGraphics g) {
        if (medoDisabled) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            float amp = fearAmp(now);
            if (amp <= 0.0F) {
                return;
            }
            int w = g.guiWidth();
            int h = g.guiHeight();
            float pulse = 0.6F + 0.4F * (float) Math.sin(now * 0.011);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(
                    1.0F, 1.0F, 1.0F, Math.min(1.0F, amp * pulse));
            // vinheta HD esticada na tela inteira (gradiente radial suave)
            g.blit(VINHETA_TEX, 0, 0, 0, 0, w, h, w, h);
            // O MEDO ESCONDE: veu azul-noite REFORCADO pulsando com a tremedeira
            int veil = (int) (208 * amp);
            g.fill(0, 0, w, h, (veil << 24) | 0x05070F);
            // icone do efeito piscando no canto (igual HUD vanilla)
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                    Math.min(1.0F, amp * 1.2F));
            int isz = Math.max(20, Math.round(Math.min(w, h) * 0.055F));
            float blink = 0.62F + 0.38F * (float) Math.sin(now * 0.012);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                    Math.min(1.0F, amp * 1.2F) * blink);
            g.blit(MEDO_HD_TEX, w - isz - 10, 10, 0, 0, isz, isz, 128, 128);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } catch (Throwable t) {
            medoDisabled = true;
            RPTagMod.LOGGER.error("RP Tag: camada do medo desligada (erro)", t);
        }
    }

    // ---- MEDO: a camera treme ----
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (medoDisabled) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            float amp = fearAmp(now);
            if (amp <= 0.0F) {
                return;
            }
            float t = now * 0.001F;
            float roll = 2.1F * amp * (float) Math.sin(t * 18.0)
                    + 0.8F * amp * (float) Math.sin(t * 31.7);
            float yaw = 0.9F * amp * (float) Math.sin(t * 13.3 + 1.7);
            float pitch = 0.7F * amp * (float) Math.sin(t * 16.1 + 0.6);
            event.setRoll(event.getRoll() + roll);
            event.setYaw(event.getYaw() + yaw);
            event.setPitch(event.getPitch() + pitch);
        } catch (Throwable t) {
            disabled = true;
            RPTagMod.LOGGER.error("RP Tag: efeito MEDO desligado ate reiniciar o jogo", t);
        }
    }

    // ---- MEDO: vinheta vermelha nas extremidades + AVISO central ----
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (disabled) {
            return;
        }
        try {
            GuiGraphics g = event.getGuiGraphics();
            int w = g.guiWidth();
            int h = g.guiHeight();
            long now = System.currentTimeMillis();

            // (3.53.0) O EFEITO MANDA NO MEDO: quem tem rptag:medo ativo
            // (de /effect give, command block ou do /rp admin medo) treme ATE
            // O EFEITO ACABAR — o cliente le o efeito local e segura o medo
            // vivo enquanto ele existir, sem pacote e sempre em sync. Antes
            // quem recebia so o efeito (/effect give) ficava NORMAL (o
            // visual so vinha do pacote do /rp admin medo).
            var local = Minecraft.getInstance().player;
            if (local != null) {
                var inst = local.getEffect(RPEffects.MEDO);
                if (inst != null) {
                    long restoMs = Math.max(300L, Math.min(inst.getDuration() * 50L, 600_000L));
                    if (fearUntil < now) {
                        fearStart = now; // (re)comecou agora
                    }
                    fearUntil = Math.max(fearUntil, now + restoMs);
                }
            }

            // (3.54.0) o DESENHO do medo (vinheta + veu + icone) foi pra
            // CAMADA OFICIAL de HUD (ClientGuiLayers, registerAboveAll) —
            // ela roda DEPOS do pos-processamento do shader, entao o medo
            // aparece COM SHADERS tambem (o RenderGuiEvent.Post ficava por
            // baixo/fora da saida do Iris e o medo sumia com shader). Aqui
            // fica so o watcher do efeito + o AVISO.

        } catch (Throwable t) {
            disabled = true;
            RPTagMod.LOGGER.error("RP Tag: efeitos de tela desligados ate reiniciar o jogo", t);
        }
    }

    /**
     * (3.61.0) AVISO na CAMADA OFICIAL de HUD (ClientGuiLayers, acima de
     * tudo) — no RenderGuiEvent.Post ele ficava FORA da saida do shader e
     * SUMIA com Iris (o mesmo mal do medo na 3.54). SEM pulso (pedido do
     * dono) e MUITO maior: escala ate 3.2x, faixa mais alta e peso de
     * tinta (duas passadas) — coisa seria de servidor.
     */
    public static void desenhaAviso(GuiGraphics g) {
        if (avisoDisabled) {
            return;
        }
        try {
            desenhaAvisoInterno(g);
        } catch (Throwable t) {
            avisoDisabled = true;
            RPTagMod.LOGGER.error("RP Tag: camada do aviso desligada (erro)", t);
        }
    }

    private static void desenhaAvisoInterno(GuiGraphics g) {
        String text = screenText;
        if (text == null) {
            return;
        }
        if (!LOGOU_AVISO) {
            LOGOU_AVISO = true;
            RPTagMod.LOGGER.info("RP Tag: desenhando aviso na tela (1o frame)");
        }
        long now = System.currentTimeMillis();
        float elapsed = now - textStart;
        if (elapsed >= textDuration) {
            screenText = null;
            return;
        }
        int style = Math.max(0, Math.min(11, textStyle));
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        // (3.69.0) CLASSICO virou CAIXA DE FALA (o campo aparece primeiro e
        // DEPOIS as letras sao escritas, estilo "Sniper: eu vou atras de
        // voces hein") — caminho proprio; pesadelo/runico/real seguem o
        // desenho GRANDE central de sempre.
        if (style == 0) {
            desenhaAvisoCaixa(g, font, w, h, text, elapsed);
            return;
        }
        // TYPEWRITER: o aviso tambem se escreve sozinho (30 ms/char)
        String parcial = typed(text, (long) elapsed);
        if (parcial.isEmpty()) {
            parcial = String.valueOf(text.charAt(0));
        }
        // escala calculada no TEXTO COMPLETO (o aviso nao cresce enquanto digita)
        String corado = style == 0 ? text : text.replaceAll("\u00a7.", "");
        Component comp = Component.literal(parcial);
        int twFull = font.width(Component.literal(corado));
        float scale = Math.min(3.2F, (w - 24.0F) / Math.max(1, twFull));
        // (3.66.0) CRESCENDO (pedido do user): o aviso entra ~12% menor e
        // cresce suave ate o tamanho cheio em ~0,5 s
        scale *= 0.88F + 0.12F * Math.min(1.0F, elapsed / 450.0F);
        float fadeIn = Math.min(1.0F, elapsed / 300.0F);
        float fadeOut = Math.min(1.0F, (textDuration - elapsed) / 500.0F);
        float vis = Math.min(fadeIn, fadeOut);
        int bh = Math.round(26.0F * scale);
        int cy = h / 2;
        int a = (int) (vis * 255.0F);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();

        // ---- veu/moldura por estilo (SEM pulso — coisa seria) ----
        if (style == 1) { // PESADELO: escurece TUDO + vermelho-sangue + borda fina
            g.fill(0, 0, w, h, ((int) (205 * vis) << 24) | 0x070203);
            int borda = ((int) (150 * vis) << 24) | 0x6E0B0B;
            g.fill(0, 0, w, 3, borda);
            g.fill(0, h - 3, w, h, borda);
            g.fill(0, 0, 3, h, borda);
            g.fill(w - 3, 0, w, h, borda);
            g.fill(0, cy - bh - 5, w, cy - bh - 2,
                    ((int) (110 * vis) << 24) | 0x4A0808);
            g.fill(0, cy + bh + 2, w, cy + bh + 5,
                    ((int) (110 * vis) << 24) | 0x4A0808);
        } else if (style == 2) { // RUNICO: veu arcano + marquinhas de runa
            g.fill(0, 0, w, h, ((int) (160 * vis) << 24) | 0x0D0518);
            int runa = ((int) (120 * vis) << 24) | 0x7C4DBF;
            for (int x = 12; x < w - 6; x += 26) {
                g.fill(x, 4, x + 2, 9, runa);
                g.fill(x, h - 9, x + 2, h - 4, runa);
            }
        } else if (style == 3) { // REAL: azul-noite + filetes dourados
            g.fill(0, 0, w, h, ((int) (175 * vis) << 24) | 0x060A16);
            int ouro = ((int) (190 * vis) << 24) | 0xC9A227;
            int lw = Math.min(w - 40, (int) (twFull * scale) + 80);
            g.fill((w - lw) / 2, cy - bh - 7, (w + lw) / 2, cy - bh - 5, ouro);
            g.fill((w - lw) / 2, cy + bh + 5, (w + lw) / 2, cy + bh + 7, ouro);
            g.fill((w - lw) / 2 - 3, cy - bh - 9, (w - lw) / 2 + 3, cy - bh - 3, ouro);
            g.fill((w - lw) / 2 - 3, cy + bh + 3, (w - lw) / 2 + 3, cy + bh + 9, ouro);
            g.fill((w + lw) / 2 - 3, cy - bh - 9, (w + lw) / 2 + 3, cy - bh - 3, ouro);
            g.fill((w + lw) / 2 - 3, cy + bh + 3, (w + lw) / 2 + 3, cy + bh + 9, ouro);
        } else if (style == 5) { // INFERNO: AURA ARDENTE — a mesma pegada
            // bonita do DIVINO, mas em fogo: brilho radial quente + leque
            // de raios girando no sentido CONTRARIO + halo triplo de
            // brasa. Sem pontinhos, sem muralha (par do celestial).
            g.fill(0, 0, w, h, ((int) (150 * vis) << 24) | 0x140301);
            int cxi = w / 2;
            for (int i = 26; i >= 1; i--) {
                float s = i / 26.0F;
                int alphaI = Math.max(1, (int) (10 * vis * (1.05F - s)));
                int hx = (int) (cxi * s), hy = (int) (cy * s);
                g.fill(cxi - hx, cy - hy, cxi + hx, cy + hy,
                        (alphaI << 24) | 0xFFB060);
            }
            // (3.91.0) ORBES de brasa ORBITANDO o texto (toque estilo
            // "ritual de despertar" da referencia espacial — suave, sem feixes)
            desenhaOrbes(g, cxi, cy, (int) (twFull * scale) / 2 + 56, 6,
                    elapsed, ((int) (225 * vis) << 24) | 0xFFC080,
                    ((int) (85 * vis) << 24) | 0xD84E0A, vis);
            int haloY5 = cy - bh - 26;
            desenhaAnel(g, cxi, haloY5, Math.min(170, w / 4), 22,
                    ((int) (60 * vis) << 24) | 0xD84E0A);
            desenhaAnel(g, cxi, haloY5, Math.min(150, w / 4), 16,
                    ((int) (215 * vis) << 24) | 0xFFA050);
            desenhaAnel(g, cxi, haloY5, Math.min(104, w / 6), 11,
                    ((int) (150 * vis) << 24) | 0xFFD9A0);
            int brasaF = ((int) (150 * vis) << 24) | 0x8A2400;
            g.fill(0, 0, w, 2, brasaF);
            g.fill(0, h - 2, w, h, brasaF);
        } else if (style == 6) { // GELO: véu glacial + cristais nas laterais
            g.fill(0, 0, w, h, ((int) (165 * vis) << 24) | 0x081523);
            g.fill(0, 0, w, 2, ((int) (140 * vis) << 24) | 0x2E6A9E);
            g.fill(0, h - 2, w, h, ((int) (140 * vis) << 24) | 0x2E6A9E);
            int cristal = ((int) (170 * vis) << 24) | 0xBFE8FF;
            int cristal2 = ((int) (110 * vis) << 24) | 0x6FB4DE;
            long semente = 7L;
            for (int i = 0; i < 8; i++) { // losangos fixos pelas bordas
                semente = semente * 6364136223846793005L + 1442695040888963407L;
                int gcx = 14 + (int) ((semente >>> 33) % Math.max(1, w - 28));
                int gcy = (i % 2 == 0) ? 16 : h - 16;
                desenhaLosango(g, gcx, gcy, 4, (i % 3 == 0) ? cristal : cristal2);
            }
        } else if (style == 7) { // DIVINO CELESTIAL: AURA SAGRADA — brilho
            // radial suave (degradê) + leque de raios girando devagar +
            // halo triplo. SEM colunas verticais, SEM pontinhos (pedido
            // do dono: "mais bonito, não vários pontos")
            g.fill(0, 0, w, h, ((int) (115 * vis) << 24) | 0x120D03);
            int cxd = w / 2;
            // brilho radial: 26 retângulos concêntricos acumulando alpha
            // = degradê contínuo (quente no centro, morrendo nas bordas)
            float resp7 = 0.90F + 0.10F * (float) Math.sin(elapsed * 0.0021F);
            for (int i = 26; i >= 1; i--) {
                float s = i / 26.0F;
                int alpha = Math.max(1,
                        (int) (9 * vis * resp7 * (1.05F - s)));
                int hx = (int) (cxd * s), hy = (int) (cy * s);
                g.fill(cxd - hx, cy - hy, cxd + hx, cy + hy,
                        (alpha << 24) | 0xFFF0C0);
            }
            // leque de luz: 14 raios afilados girando MUITO devagar
            float angBase = elapsed * 0.0045F;
            int raioLuz = ((int) (80 * vis) << 24) | 0xFFF3C4;
            int raioLuz2 = ((int) (55 * vis) << 24) | 0xE8C765;
            for (int i = 0; i < 14; i++) {
                desenhaRaio(g, cxd, cy, angBase + i * (360.0F / 14.0F),
                        (int) (Math.max(w, h) * 0.75F), (i % 2 == 0) ? 14 : 8,
                        (i % 2 == 0) ? raioLuz : raioLuz2);
            }
            // SOL SAGRADO (3.91.0): disco com glow + anel + 8 raios afilados
            // (4 longos cardeais + 4 curtos diagonais) — icônico e limpo
            int haloY = cy - bh - 26;
            float rSol = Math.min(44, w / 18.0F);
            desenhaSol(g, cxd, haloY, rSol,
                    ((int) (235 * vis) << 24) | 0xFFF3C4,
                    ((int) (215 * vis) << 24) | 0xE8C765, vis);
            // orbes de luz orbitando o texto (toque "despertar" da referência)
            desenhaOrbes(g, cxd, cy, (int) (twFull * scale) / 2 + 56, 6,
                    elapsed, ((int) (225 * vis) << 24) | 0xFFF7E0,
                    ((int) (85 * vis) << 24) | 0xE8C765, vis);
            int ouro = ((int) (150 * vis) << 24) | 0xC9A227;
            g.fill(0, 0, w, 2, ouro);
            g.fill(0, h - 2, w, h, ouro);
        } else if (style == 8) { // VOID: NEBULOSA cosmica + estrelas
            g.fill(0, 0, w, h, ((int) (190 * vis) << 24) | 0x060310);
            // (3.90.0) NEBULOSA: 2 brilhos radiais (roxo/azul) deslocados =
            // poeira cosmica com profundidade (o mesmo degrê acumulado do
            // divino/inferno — tematica espacial)
            for (int i = 18; i >= 1; i--) {
                float s = i / 18.0F;
                int hx = (int) (w * 0.55F * s), hy = (int) (h * 0.55F * s);
                int nx = w * 40 / 100, ny = h * 38 / 100;
                g.fill(nx - hx, ny - hy, nx + hx, ny + hy,
                        (Math.max(1, (int) (7 * vis * (1.05F - s))) << 24) | 0x3A1E7A);
                int bx = w * 62 / 100, by = h * 64 / 100;
                g.fill(bx - hx, by - hy, bx + hx, by + hy,
                        (Math.max(1, (int) (6 * vis * (1.05F - s))) << 24) | 0x14285E);
            }
            // anel cosmico liso em volta do texto
            desenhaAnel(g, w / 2, cy, Math.min(w / 3, 300), Math.min(w / 9, 100),
                    ((int) (80 * vis) << 24) | 0x7A5CCF);
            long sementeV = 777L;
            for (int i = 0; i < 60; i++) { // estrelas fixas
                sementeV = sementeV * 6364136223846793005L + 1442695040888963407L;
                int x = (int) ((sementeV >>> 33) % Math.max(1, w));
                int y = (int) ((sementeV >>> 21) % Math.max(1, h));
                int s = (i % 5 == 0) ? 2 : 1;
                int estrela = ((int) (((i % 3 == 0) ? 190 : 120) * vis)) << 24
                        | ((i % 4 == 0) ? 0xCBB2FF : 0xFFFFFF);
                g.fill(x, y, x + s, y + s, estrela);
            }
            int filete = ((int) (160 * vis) << 24) | 0x9A6CFF;
            g.fill(0, 0, w, 2, filete);
            g.fill(0, h - 2, w, h, filete);
        } else if (style == 9) { // SANGUE: gotas escorrendo + PULSO CARDIACO
            // profundo (bate forte, bate fraco — o véu pulsa junto)
            float bat1 = (float) Math.max(0.0, Math.sin(elapsed * 0.0069));
            float bat2 = (float) Math.max(0.0, Math.sin(elapsed * 0.0069 - 0.9));
            float pulsoCard = 0.70F + 0.30F * Math.min(1.0F,
                    bat1 * bat1 * bat1 + 0.6F * bat2 * bat2 * bat2);
            g.fill(0, 0, w, h, ((int) (170 * pulsoCard * vis) << 24) | 0x0F0103);
            // (3.90.0) BIZARRO: CORACAO GIGANTE translucido BATENDO atras do
            // texto (mesma silhueta do coracao 3D, pulsa com o Warden) +
            // veias de sangue rastejando dos cantos
            float escC = Math.max(w, h) / 14.0F * (0.90F + 0.14F * pulsoCard);
            desenhaCoracaoFundo(g, w / 2, cy, escC,
                    ((int) (34 * pulsoCard * vis) << 24) | 0x6E0B0B);
            desenhaVeias(g, w, h, vis);
            int gota = ((int) (200 * pulsoCard * vis) << 24) | 0x8A0F0F;
            int gotaClara = ((int) (150 * pulsoCard * vis) << 24) | 0xB01616;
            long sementeS = 91L;
            for (int x = 8; x < w - 8; x += 24) { // colunas de sangue FIXAS
                sementeS = sementeS * 6364136223846793005L + 1442695040888963407L;
                int len = 14 + (int) ((sementeS >>> 33) % 30);
                g.fill(x, 0, x + 2, len, (x % 3 == 0) ? gotaClara : gota);
                g.fill(x - 1, len, x + 3, len + 2, gota); // bolha na ponta
            }
        } else if (style == 10) { // DEMONIACO: aura negra + CIRCULO DE
            // INVOCAÇÃO REDONDO de verdade (centrado na tela, ra = rb) com
            // runas radiais + veias + orbes de sangue orbitando
            float resp10 = 0.86F + 0.14F * (float) Math.sin(elapsed * 0.0031F);
            g.fill(0, 0, w, h, ((int) (205 * resp10 * vis) << 24) | 0x0A0002);
            int cxD = w / 2;
            for (int i = 24; i >= 1; i--) { // brilho radial vermelho-escuro
                float s = i / 24.0F;
                int alphaD = Math.max(1, (int) (8 * vis * (1.05F - s)));
                int hx = (int) (cxD * s), hy = (int) (cy * s);
                g.fill(cxD - hx, cy - hy, cxD + hx, cy + hy,
                        (alphaD << 24) | 0x3A0208);
            }
            desenhaVeias(g, w, h, vis);
            int Rinv = Math.min(w, h) / 4; // REDONDO (ra = rb), nunca oval
            desenhaAnel(g, cxD, cy, Rinv + 5, Rinv + 5,
                    ((int) (55 * resp10 * vis) << 24) | 0x5E0808);
            desenhaAnel(g, cxD, cy, Rinv, Rinv,
                    ((int) (180 * resp10 * vis) << 24) | 0x8A0F0F);
            desenhaAnel(g, cxD, cy, Rinv - 16, Rinv - 16,
                    ((int) (110 * resp10 * vis) << 24) | 0x6E0B0B);
            for (int i = 0; i < 12; i++) { // 12 runas radiais ESTÁTICAS
                double angR = (Math.PI * 2 * i) / 12 + 0.26;
                int rx0 = cxD + (int) Math.round((Rinv - 13) * Math.cos(angR));
                int ry0 = cy + (int) Math.round((Rinv - 13) * Math.sin(angR));
                int rx1 = cxD + (int) Math.round((Rinv - 3) * Math.cos(angR));
                int ry1 = cy + (int) Math.round((Rinv - 3) * Math.sin(angR));
                g.fill(Math.min(rx0, rx1), Math.min(ry0, ry1),
                        Math.max(rx0, rx1) + 2, Math.max(ry0, ry1) + 2,
                        ((int) (135 * resp10 * vis) << 24) | 0x8A0F0F);
            }
            desenhaOrbes(g, cxD, cy, Rinv + 18, 6, elapsed,
                    ((int) (210 * vis) << 24) | 0xFF4040,
                    ((int) (80 * vis) << 24) | 0x5E0808, vis);
        }

        // cor do texto por estilo (1-9 = paleta FIXA do estilo)
        int tinta = switch (style) {
            case 1 -> 0xFF2A1A; // vermelho-sangue
            case 2 -> 0xD9B8FF; // lavanda arcano
            case 3 -> 0xF2C94C; // ouro
            case 5 -> 0xFFA347; // brasas (laranja-fogo)
            case 6 -> 0xDFF4FF; // branco-gelo
            case 7 -> 0xFFEDB8; // luz dourada clara
            case 8 -> 0xCBB2FF; // lilás do void
            case 9 -> 0xE02020; // sangue vivo
            case 10 -> 0xE01010; // vermelho demoniaco
            default -> 0xFFFFFF;
        };
        if (style == 4) {
            // (3.76.0) TEXTURIZADA ESTÁTICA (o aviso da vida perdida):
            // camadas de sangue + desvios FIXOS por char — as letras
            // NÃO se mexem (pedido do dono)
            desenhaTextoTexturizado(g, font, w / 2.0F, cy, parcial, scale, vis);
        } else if (style == 1 || style == 10) {
            // (3.74.0) TEXTO CORROMPIDO: onda lenta por caractere + camadas
            // de sangue + falhas deterministicas (o visual "texturizada")
            // (3.87.0) o DEMONIACO usa as mesmas letras vermelhas macabras
            desenhaTextoCorrompido(g, font, w / 2.0F, cy, parcial, scale, vis, now);
        } else {
            boolean sombra = style == 0;
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, cy, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            int ty = -font.lineHeight / 2;
            if (vis < 1.0F) {
                ty += Math.round((1.0F - vis) * 6.0F);
            }
            int tx = -font.width(comp) / 2;
            // peso de "serio" (duas passadas) — mantido do 3.61
            g.drawString(font, comp, tx, ty, tinta, sombra);
            g.pose().pushPose();
            g.pose().translate(0.3F, 0.0F, 0.0F);
            g.drawString(font, comp, tx, ty, tinta, sombra);
            g.pose().popPose();
            g.pose().popPose();
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** (3.91.0) ORBES de energia ORBITANDO (assinatura da referência de
     *  ritual/awakening): n orbes em elipse achatada, cada um = glow 6px +
     *  núcleo 3px brilhante. Movimento contínuo e suave. */
    private static void desenhaOrbes(net.minecraft.client.gui.GuiGraphics g,
            int cx, int cyC, int raio, int n, float elapsed, int corNucleo,
            int corGlow, float visF) {
        int aG = (corGlow >>> 24) & 0xFF;
        int rgbG = corGlow & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            double fase = (Math.PI * 2 * i) / n + elapsed * 0.0011;
            int ox = cx + (int) Math.round(raio * Math.cos(fase));
            int oy = cyC + (int) Math.round(raio * 0.42 * Math.sin(fase));
            g.fill(ox - 3, oy - 3, ox + 3, oy + 3,
                    (Math.max(1, aG / 3) << 24) | rgbG);
            g.fill(ox - 1, oy - 1, ox + 2, oy + 2, corNucleo);
        }
    }

    /** (3.91.0) SOL SAGRADO do divino: disco com glow (degradê acumulado)
     *  + anel fino + 8 raios afilados (desenhaRaio) — 4 cardeais longos e
     *  largos, 4 diagonais curtos e finos. */
    private static void desenhaSol(net.minecraft.client.gui.GuiGraphics g,
            int cx, int cyC, float r, int corNucleo, int corRaio, float visF) {
        for (int i = 8; i >= 1; i--) {
            float s = i / 8.0F;
            int a = Math.max(1, (int) (115 * visF * (1.15F - s)));
            int rr = Math.round(r * s);
            g.fill(cx - rr, cyC - rr, cx + rr, cyC + rr,
                    (a << 24) | (corNucleo & 0xFFFFFF));
        }
        desenhaAnel(g, cx, cyC, (int) (r * 1.45F), (int) (r * 1.45F),
                ((int) (150 * visF) << 24) | 0xE8C765);
        for (int i = 0; i < 8; i++) {
            boolean cardeal = (i % 2 == 0);
            desenhaRaio(g, cx, cyC, i * 45.0F,
                    (int) (r * (cardeal ? 3.1F : 2.2F)),
                    Math.max(3, (int) (r * (cardeal ? 0.42F : 0.26F))), corRaio);
        }
    }

    /** (3.88.0) RAIO DE LUZ afilado: lâmina desenhada do centro pra fora
     *  com ROTAÇÃO (pose) e 4 segmentos de alpha decrescente = raio que
     *  "esvae" ao longo do comprimento (visual suave, sem degraus duros). */
    private static void desenhaRaio(net.minecraft.client.gui.GuiGraphics g,
            int cx, int cyy, float angulo, int comprimento, int largura, int cor) {
        int a = (cor >>> 24) & 0xFF;
        int rgb = cor & 0xFFFFFF;
        g.pose().pushPose();
        g.pose().translate(cx, cyy, 0.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angulo));
        for (int j = 0; j < 4; j++) { // j=0 perto do centro, j=3 longe
            int af = Math.max(1, a * (12 - j * 3) / 12);
            int wj = Math.max(3, Math.round(largura * (1.0F + j * 0.22F)));
            int y0 = -comprimento * (j + 1) / 4;
            int y1 = -comprimento * j / 4;
            g.fill(-wj / 2, y0, wj - wj / 2, y1, (af << 24) | rgb);
        }
        g.pose().popPose();
    }

    /** (3.90.0) CORACAO GIGANTE translucido (aviso de SANGUE): a mesma
     *  silhueta do coracao 3D (2 lobulos + ponta em V), desenhado ATRAS do
     *  texto e pulsando junto com a batida cardiaca. */
    private static final int[][] MASCARA_CORACAO = {
            {2, 5, 8, 11}, {1, 6, 7, 12}, {1, 12}, {1, 12}, {2, 11},
            {2, 11}, {3, 10}, {4, 9}, {5, 8}, {6, 7}
    };

    private static void desenhaCoracaoFundo(net.minecraft.client.gui.GuiGraphics g,
            int cx, int cyC, float esc, int cor) {
        float largura = 13 * esc;
        float y0 = cyC - 10 * esc / 2.0F;
        for (int r = 0; r < MASCARA_CORACAO.length; r++) {
            int[] linha = MASCARA_CORACAO[r];
            int yy = Math.round(y0 + r * esc);
            for (int s = 0; s + 1 < linha.length; s += 2) {
                int x0 = Math.round(cx - largura / 2 + linha[s] * esc);
                int x1 = Math.round(cx - largura / 2 + linha[s + 1] * esc);
                g.fill(x0, yy, x1, yy + Math.round(esc), cor);
            }
        }
    }

    /** (3.87.0) ANEL/ELIPSE (3.90.0 = contorno continuo, 2px) (halo do DIVINO, circulo
     * macabro do DEMONIACO) — centrado em (cx, cy), raios ra/rb. */
    private static void desenhaAnel(net.minecraft.client.gui.GuiGraphics g,
            int cx, int cyy, int ra, int rb, int cor) {
        if (ra <= 0 || rb <= 0) {
            return;
        }
        // (3.91.0) varredura ANGULAR: pontos suficientes p/ arco perfeito —
        // anel liso em QUALQUER proporcao (fim definitivo dos "dots")
        int passos = Math.max(64, ra + rb);
        for (int i = 0; i < passos; i++) {
            double angA = (Math.PI * 2 * i) / passos;
            int px = cx + (int) Math.round(ra * Math.cos(angA));
            int py = cyy + (int) Math.round(rb * Math.sin(angA));
            g.fill(px, py, px + 2, py + 2, cor);
        }
    }

    /** (3.87.0) VEIAS de sangue: gretas vermelhas crescendo dos 4 cantos
     *  em direção ao centro (determinísticas — sempre as mesmas gretas). */
    private static void desenhaVeias(net.minecraft.client.gui.GuiGraphics g,
            int w, int h, float vis) {
        long semente = 666L;
        int[][] cantos = {{0, 0, 1, 1}, {w, 0, -1, 1}, {0, h, 1, -1}, {w, h, -1, -1}};
        for (int[] c : cantos) {
            int x = c[0], y = c[1];
            for (int i = 0; i < 9; i++) {
                semente = semente * 6364136223846793005L + 1442695040888963407L;
                x += c[2] * (14 + (int) ((semente >>> 33) % 22));
                y += c[3] * (8 + (int) ((semente >>> 45) % 14));
                if (x < 0 || x > w || y < 0 || y > h) {
                    break;
                }
                int corVeia = (i % 2 == 0)
                        ? ((int) (150 * vis) << 24) | 0x6E0B0B
                        : ((int) (110 * vis) << 24) | 0x450404;
                g.fill(Math.max(0, x - 2), Math.max(0, y),
                        Math.min(w, x + 3), Math.min(h, y + 2), corVeia);
                g.fill(Math.max(0, x), Math.max(0, y - 2),
                        Math.min(w, x + 1), Math.min(h, y + 3), corVeia);
            }
        }
    }

    /** (3.86.0) losango preenchido (cristal de gelo) — usado no estilo GELO. */
    private static void desenhaLosango(net.minecraft.client.gui.GuiGraphics g,
            int cx, int cy, int r, int cor) {
        for (int dy = -r; dy <= r; dy++) {
            int larg = r - Math.abs(dy);
            g.fill(cx - larg, cy + dy, cx + larg + 1, cy + dy + 1, cor);
        }
    }

    /** (3.74.0) TEXTO CORROMPIDO do PESADELO: cada caractere flutua numa
     * onda lenta, o preenchimento tem 3 camadas de vermelho (sombra quase
     * preta, base sangue, topo quente = degrade) e alguns chars "falham"
     * (deslizam/escurecem) por ~2 s de forma deterministica — o visual da
     * referencia ("Nao ha escolha a ser feita."). Nao mexe em veu/bordas. */
    private static void desenhaTextoCorrompido(GuiGraphics g, Font font,
            float cx, float cy, String texto, float scale, float vis, long now) {
        String limpo = texto.replaceAll("\u00a7.", "");
        if (limpo.isEmpty() || vis <= 0.0F) {
            return;
        }
        int totalW = font.width(limpo);
        long tick = now / 1900L;
        int aSombra = (int) (190 * vis) << 24 | 0x3A0606;
        int aBase = (int) (255 * vis) << 24 | 0xA11A1A;
        int aTopo = (int) (140 * vis) << 24 | 0xE04848;
        int aFalha = (int) (255 * vis) << 24 | 0x6E0B0B;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        // (3.75.0) SWAY: o texto inteiro escorrega de lado devagar
        g.pose().translate((float) Math.sin(now * 0.0008) * 1.6F, 0.0F, 0.0F);
        int ty = -font.lineHeight / 2;
        if (vis < 1.0F) {
            ty += Math.round((1.0F - vis) * 6.0F);
        }
        float x = -totalW / 2.0F;
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            float cw = font.width(String.valueOf(c));
            if (c != ' ') {
                long seed = (i * 0x9E37L + tick * 0x85EBL) & 0xFFFFL;
                float dx = (seed % 13L == 0L) ? 1.0F : 0.0F;
                float dy = (seed % 11L == 0L) ? 1.0F : 0.0F;
                boolean falha = seed % 17L == 0L;
                // (3.75.0) cada char tem fase propria (seed) — a onda fica
                // irregular, menos "senoide de professor"
                float onda = (float) Math.sin((x + cw / 2.0F) * 0.16
                        + (seed % 7L) * 0.05 + now * 0.0015) * 1.1F;
                String ch = String.valueOf(c);
                if (falha) { // (3.75.0) GHOST: o char que falha deixa rastro
                    g.drawString(font, ch, x + dx - 0.8F, ty + onda + dy - 0.4F,
                            (int) (70 * vis) << 24 | 0x6E0B0B, false);
                }
                g.drawString(font, ch, x + dx + 0.9F, ty + onda + dy + 1.1F, aSombra, false);
                g.drawString(font, ch, x + dx, ty + onda + dy, falha ? aFalha : aBase, false);
                g.drawString(font, ch, x + dx, ty + onda + dy - 0.6F, aTopo, false);
            }
            x += cw;
        }
        g.pose().popPose();
    }

    /** (3.76.0) TEXTO TEXTURIZADO ESTÁTICO (a "fonte" do aviso de vida
     * perdida): mesma referência de terror ("Não há escolha a ser feita."),
     * mas SEM movimento — cada char tem um desvio fixo, o preenchimento
     * tem manchas de sangue variadas e o topo quente aparece em 2 de 3
     * chars (degradê irregular). Nada de onda/sway/fantasma. */
    private static void desenhaTextoTexturizado(GuiGraphics g, Font font,
            float cx, float cy, String texto, float scale, float vis) {
        String limpo = texto.replaceAll("\u00a7.", "");
        if (limpo.isEmpty() || vis <= 0.0F) {
            return;
        }
        int totalW = font.width(limpo);
        int aSombra = (int) (190 * vis) << 24 | 0x3A0606;
        int aTopo = (int) (110 * vis) << 24 | 0xE04848;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int ty = -font.lineHeight / 2;
        if (vis < 1.0F) {
            ty += Math.round((1.0F - vis) * 6.0F);
        }
        float x = -totalW / 2.0F;
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            float cw = font.width(String.valueOf(c));
            if (c != ' ') {
                long seed = (i * 0x9E37L) & 0xFFFFL;
                // (3.79.0) SEM TREMIDO (pedido do dono): as camadas ficam
                // RETAS — a textura e so a variação de cor (manchas de
                // sangue) + topo quente irregular; nada de deslocamento
                int base = 0xA11A1A;
                if (seed % 5L == 0L) {
                    base = 0x7E1010; // mancha escura
                }
                if (seed % 7L == 0L) {
                    base = 0xC23030; // mancha clara
                }
                String ch = String.valueOf(c);
                g.drawString(font, ch, x + 0.9F, ty + 1.1F, aSombra, false);
                g.drawString(font, ch, x, ty, (int) (255 * vis) << 24 | base, false);
                if (seed % 3L != 0L) { // topo quente em 2 de 3 (irregular)
                    g.drawString(font, ch, x, ty - 0.6F, aTopo, false);
                }
            }
            x += cw;
        }
        g.pose().popPose();
    }

    /** (3.69.0) CLASSICO = CAIXA DE FALA estilo "Sniper: eu vou atras de
     * voces hein": o CAMPO aparece primeiro (pop + fade rapido, tamanho ja
     * final) e DEPOIS as letras vao sendo escritas (30 ms/char) com o
     * cursor piscando; o nome (texto antes do ":") fica AZUL. */
    private static void desenhaAvisoCaixa(GuiGraphics g, Font font, int w, int h,
            String text, float elapsed) {
        long tAtraso = 220L; // as letras so comecam DEPOIS do campo pronto
        float pop = 0.88F + 0.12F * Math.min(1.0F, elapsed / 450.0F);
        float boxIn = Math.min(1.0F, elapsed / 180.0F);
        float visOut = Math.min(1.0F, (textDuration - elapsed) / 500.0F);
        float vis = Math.min(boxIn, visOut);
        if (vis <= 0.0F) {
            return;
        }
        String limpo = text.replaceAll("\u00a7.", "");
        int accent = 0xFF8C9EFF; // nome (antes do ":") em azul-periwinkle
        // ---- quebra o texto COMPLETO em linhas ESTAVEIS (o campo nasce
        // com o tamanho final e nao pula enquanto digita) ----
        float escala = 1.25F;
        int maxW = Math.max(120, w - 48);
        java.util.List<String> linhas = quebraLinhas(font, limpo, escala, maxW);
        while (linhas.size() > 3 && escala > 0.8F) {
            escala -= 0.15F;
            linhas = quebraLinhas(font, limpo, escala, maxW);
        }
        // TYPEWRITER do aviso (mesma cadencia dos outros estilos, so que
        // comecando DEPOIS do campo aparecer)
        String parcial = elapsed > tAtraso ? typed(limpo, (long) elapsed - tAtraso) : "";
        // ---- medidas do campo (pelo texto COMPLETO) ----
        int lineH = 12;
        int maior = 0;
        for (String ln : linhas) {
            maior = Math.max(maior, font.width(ln));
        }
        int boxW = Math.min(maxW, Math.round(maior * escala)) + 26;
        int boxH = Math.round(linhas.size() * lineH * escala) + 12;
        int boxX = (w - boxW) / 2;
        int boxY = h - 70 - boxH; // ancorado ACIMA do hotbar/coracoes
        int a = (int) (255 * vis);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(boxX + boxW / 2.0F, boxY + boxH / 2.0F, 0.0F);
        g.pose().scale(pop, pop, 1.0F);
        g.pose().translate(-(boxX + boxW / 2.0F), -(boxY + boxH / 2.0F), 0.0F);
        g.fill(boxX + 3, boxY + 4, boxX + boxW + 3, boxY + boxH + 4,
                (int) (90 * vis) << 24); // sombra
        g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1,
                ((int) (200 * vis) << 24) | 0x0A0D16); // aro escuro
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH,
                ((int) (185 * vis) << 24) | 0x0B0E15); // vidro da caixa
        g.fill(boxX + 2, boxY + 1, boxX + boxW - 2, boxY + 2,
                ((int) (36 * vis) << 24) | 0xFFFFFF); // luz no topo
        g.fill(boxX, boxY, boxX + 3, boxY + boxH,
                a << 24 | (accent & 0xFFFFFF)); // barra do nome
        // ---- as letras sendo escritas (linha a linha) ----
        g.pose().translate(boxX + 14, boxY + 7, 0.0F);
        g.pose().scale(escala, escala, 1.0F);
        int pi = limpo.indexOf(':'); // fim do nome (se houver)
        int usados = 0;
        boolean digitando = parcial.length() < limpo.length();
        long blink = System.currentTimeMillis() / 300L;
        for (int i = 0; i < linhas.size(); i++) {
            String ln = linhas.get(i);
            if (usados >= parcial.length()) {
                break;
            }
            String parte = parcial.substring(usados,
                    Math.min(parcial.length(), usados + ln.length()));
            usados += parte.length();
            int y = i * lineH;
            int corte = (i == 0 && pi >= 0) ? Math.min(pi + 1, parte.length()) : 0;
            if (corte > 0) { // nome colorido + resto branco
                String nome = parte.substring(0, corte);
                g.drawString(font, nome, 0, y, a << 24 | (accent & 0xFFFFFF), true);
                g.drawString(font, parte.substring(corte), font.width(nome), y,
                        a << 24 | 0xFFFFFF, true);
            } else {
                g.drawString(font, parte, 0, y, a << 24 | 0xFFFFFF, true);
            }
            if (usados >= parcial.length()) { // fim do que ja apareceu
                if (digitando && blink % 2L == 0L) { // cursor enquanto escreve
                    g.drawString(font, "_", font.width(parte) + 1, y,
                            a << 24 | (accent & 0xFFFFFF), true);
                }
                break;
            }
        }
        g.pose().popPose();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** (3.69.0) quebra por palavras na largura max (caixa do classico). */
    private static java.util.List<String> quebraLinhas(Font font, String s,
            float escala, int maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String pal : s.split(" ")) {
            String tent = cur.length() == 0 ? pal : cur + " " + pal;
            if (cur.length() > 0 && font.width(tent) * escala > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(pal);
            } else {
                cur = new StringBuilder(tent);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
