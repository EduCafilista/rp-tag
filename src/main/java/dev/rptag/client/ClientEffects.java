package dev.rptag.client;

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

            // VINHETA do MEDO (3.46.0): gradiente radial HD texturizado
            // (suave de verdade, sem faixas duras) + ICONE do efeito
            // piscando no canto superior direito, igual o HUD vanilla.
            float amp = fearAmp(now);
            if (amp > 0.0F) {
                float pulse = 0.6F + 0.4F * (float) Math.sin(now * 0.011);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(
                        1.0F, 1.0F, 1.0F, Math.min(1.0F, amp * pulse));
                g.blit(VINHETA_TEX, 0, 0, 0, 0, w, h, 256, 256);
                int isz = Math.max(20, Math.round(Math.min(w, h) * 0.055F));
                float blink = 0.62F + 0.38F * (float) Math.sin(now * 0.012);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                        Math.min(1.0F, amp * 1.2F) * blink);
                g.blit(MEDO_HD_TEX, w - isz - 10, 10, 0, 0, isz, isz, 128, 128);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            }

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
