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

    private static boolean disabled = false;

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

    /** Liga o MEDO por N segundos (servidor clampa 1..30). */
    public static void startFear(int seconds) {
        long now = System.currentTimeMillis();
        fearStart = now;
        fearUntil = now + Math.max(1, Math.min(30, seconds)) * 1000L;
    }

    /** Mostra o texto GRANDE por N segundos (servidor clampa 1..60). */
    public static void startScreenText(String text, int seconds) {
        screenText = text;
        textStart = System.currentTimeMillis();
        textDuration = Math.max(1, Math.min(60, seconds)) * 1000L;
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
        if (disabled) {
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
            disabled = true;
            RPTagMod.LOGGER.error("RP Tag: camada do medo desligada (erro)", t);
        }
    }

    // ---- MEDO: a camera treme ----
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (disabled) {
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

            // AVISO: texto GRANDE no centro
            String text = screenText;
            if (text == null) {
                return;
            }
            float elapsed = now - textStart;
            if (elapsed >= textDuration) {
                screenText = null;
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            Component comp = Component.literal(text);
            int tw = font.width(comp);
            // escala grande mas SEMPRE dentro da tela
            float scale = Math.min(2.2F, (w - 24.0F) / Math.max(1, tw));
            float fadeIn = Math.min(1.0F, elapsed / 300.0F);
            float fadeOut = Math.min(1.0F, (textDuration - elapsed) / 500.0F);
            float vis = Math.min(fadeIn, fadeOut);
            int bh = Math.round(18.0F * scale);
            // (3.46.0) faixa com gradiente GAUSSIANO HD (some suavemente pra
            // cima e pra baixo — nada de retangulo duro atras do texto)
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, vis);
            g.blit(GRAD_BAR_TEX, 0, h / 2 - bh, w, 2 * bh, 0, 0, 64, 64);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            g.pose().pushPose();
            float pulse = 1.0F + 0.02F * (float) Math.sin(now * 0.006);
            g.pose().translate(w / 2.0F, h / 2.0F, 0.0F);
            g.pose().scale(scale * pulse, scale * pulse, 1.0F);
            int ty = -font.lineHeight / 2;
            if (vis < 1.0F) {
                ty += Math.round((1.0F - vis) * 6.0F);
            }
            g.drawString(font, comp, -tw / 2, ty, 0xFFFFFF, true);
            g.pose().popPose();
        } catch (Throwable t) {
            disabled = true;
            RPTagMod.LOGGER.error("RP Tag: efeitos de tela desligados ate reiniciar o jogo", t);
        }
    }
}
