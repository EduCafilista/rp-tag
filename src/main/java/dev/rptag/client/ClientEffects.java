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
    /** (3.64.0) estilo do aviso em exibicao (0 classico..3 real). */
    private static volatile int textStyle = 0;

    /** Liga o MEDO por N segundos (servidor clampa 1..30). */
    public static void startFear(int seconds) {
        long now = System.currentTimeMillis();
        fearStart = now;
        fearUntil = now + Math.max(1, Math.min(30, seconds)) * 1000L;
    }

    /** Mostra o texto GRANDE por N segundos (servidor clampa 1..60). */
    public static void startScreenText(String text, int seconds, int style) {
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
        RPTagMod.LOGGER.info("RP Tag 3.84.0: camada de avisos carregada");
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
        int style = Math.max(0, Math.min(4, textStyle));
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
        }

        // cor do texto por estilo (1-3 = paleta FIXA do estilo)
        int tinta = switch (style) {
            case 1 -> 0xFF2A1A; // vermelho-sangue
            case 2 -> 0xD9B8FF; // lavanda arcano
            case 3 -> 0xF2C94C; // ouro
            default -> 0xFFFFFF;
        };
        if (style == 4) {
            // (3.76.0) TEXTURIZADA ESTÁTICA (o aviso da vida perdida):
            // camadas de sangue + desvios FIXOS por char — as letras
            // NÃO se mexem (pedido do dono)
            desenhaTextoTexturizado(g, font, w / 2.0F, cy, parcial, scale, vis);
        } else if (style == 1) {
            // (3.74.0) TEXTO CORROMPIDO: onda lenta por caractere + camadas
            // de sangue + falhas deterministicas (o visual "texturizada")
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
