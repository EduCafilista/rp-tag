package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import dev.rptag.RPTagMod;

/**
 * Fondos do balao de fala: cores solidas, gradiente e texturas
 * (papel/noite/madeira, empacotadas no jar do mod).
 *
 * <p>Usado tanto pelo renderizador 3D do mundo quanto pelo preview 2D
 * da tela de personalizacao.
 */
public final class BubbleBackgrounds {

    /** Estilos (mesmos codigos de {@link dev.rptag.BubbleStyle}). */
    public static final int BG_TRANSLUCIDO = 0;
    public static final int BG_ESCURO = 1;
    public static final int BG_CLARO = 2;
    public static final int BG_GRADIENTE = 3;
    public static final int BG_PAPEL = 4;
    public static final int BG_NOITE = 5;
    public static final int BG_MADEIRA = 6;

    private static final ResourceLocation TEX_PAPEL = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_papel.png");
    private static final ResourceLocation TEX_NOITE = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_noite.png");
    private static final ResourceLocation TEX_MADEIRA = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_madeira.png");

    private BubbleBackgrounds() {
    }

    /** @return o RenderType adequado ao estilo. */
    public static RenderType typeFor(int background) {
        return switch (background) {
            case BG_PAPEL -> RenderType.text(TEX_PAPEL);
            case BG_NOITE -> RenderType.text(TEX_NOITE);
            case BG_MADEIRA -> RenderType.text(TEX_MADEIRA);
            default -> RenderType.gui();
        };
    }

    public static boolean isTextured(int background) {
        return background >= BG_PAPEL;
    }

    /** @return a cor do texto (contraste automatico com o fundo). */
    public static int textColorFor(int background, int colorRGB) {
        return switch (background) {
            case BG_CLARO, BG_PAPEL -> 0xFF1A1A22;
            default -> isLight(colorRGB) ? 0xFF1A1A22 : 0xFFFFFFFF;
        };
    }

    public static boolean isLight(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.62;
    }

    // =====================================================================
    //  Desenho da capsula (3D e 2D usam os mesmos emissores)
    // =====================================================================

    /** Desenha a capsula arredondada inteira com o estilo aplicado. */
    public static void pill(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h,
            int background, int colorRGB) {
        float r = Math.min(h / 2.0F, 5.0F);
        float x1 = x + w;
        float y1 = y + h;

        quad(vc, m, x + r, y, x1 - r, y1, background, colorRGB, x, y, w, h);
        quad(vc, m, x, y + r, x + r, y1 - r, background, colorRGB, x, y, w, h);
        quad(vc, m, x1 - r, y + r, x1, y1 - r, background, colorRGB, x, y, w, h);

        fan(vc, m, x + r, y + r, r, 180.0F, 270.0F, background, colorRGB, x, y, w, h);
        fan(vc, m, x1 - r, y + r, r, 270.0F, 360.0F, background, colorRGB, x, y, w, h);
        fan(vc, m, x1 - r, y1 - r, r, 0.0F, 90.0F, background, colorRGB, x, y, w, h);
        fan(vc, m, x + r, y1 - r, r, 90.0F, 180.0F, background, colorRGB, x, y, w, h);
    }

    /** Triangulo do rabicho com o estilo aplicado. */
    public static void tail(VertexConsumer vc, Matrix4f m,
            float x0, float y0, float x1, float y1, float x2, float y2,
            int background, int colorRGB) {
        if (isTextured(background)) {
            texTriMap(vc, m, x0, y0, x1, y1, x2, y2, 0.0F, 0.0F, 1.0F, 1.0F);
        } else {
            colorTri(vc, m, x0, y0, x1, y1, x2, y2, background, colorRGB);
        }
    }

    // ---- internos ----

    private static void quad(VertexConsumer vc, Matrix4f m,
            float ax, float ay, float bx, float by,
            int background, int colorRGB, float u0, float v0, float uw, float vh) {
        if (isTextured(background)) {
            texTriMap(vc, m, ax, ay, bx, ay, ax, by, u0, v0, uw, vh);
            texTriMap(vc, m, ax, by, bx, ay, bx, by, u0, v0, uw, vh);
        } else {
            colorTri(vc, m, ax, ay, bx, ay, ax, by, background, colorRGB);
            colorTri(vc, m, ax, by, bx, ay, bx, by, background, colorRGB);
        }
    }

    private static void fan(VertexConsumer vc, Matrix4f m, float cx, float cy, float r,
            float a0, float a1, int background, int colorRGB,
            float u0, float v0, float uw, float vh) {
        int segments = 4;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) Math.toRadians(a0 + (a1 - a0) * i / segments);
            float t1 = (float) Math.toRadians(a0 + (a1 - a0) * (i + 1) / segments);
            float px0 = cx + r * (float) Math.cos(t0);
            float py0 = cy + r * (float) Math.sin(t0);
            float px1 = cx + r * (float) Math.cos(t1);
            float py1 = cy + r * (float) Math.sin(t1);
            if (isTextured(background)) {
                texTriMap(vc, m, cx, cy, px0, py0, px1, py1, u0, v0, uw, vh);
            } else {
                colorTri(vc, m, cx, cy, px0, py0, px1, py1, background, colorRGB);
            }
        }
    }

    /** Triangulo com cor (pode ser gradiente vertical: top = claro, bottom = escuro). */
    private static void colorTri(VertexConsumer vc, Matrix4f m,
            float x0, float y0, float x1, float y1, float x2, float y2,
            int background, int colorRGB) {
        switch (background) {
            case BG_ESCURO -> {
                solidTri(vc, m, x0, y0, x1, y1, x2, y2, 0xFF14141A);
            }
            case BG_CLARO -> {
                solidTri(vc, m, x0, y0, x1, y1, x2, y2, 0xFFF4F0E8);
            }
            case BG_GRADIENTE -> {
                int top = lighten(colorRGB, 1.18F);
                int bottom = lighten(colorRGB, 0.62F);
                float avgY = (y0 + y1 + y2) / 3.0F;
                float minY = Math.min(y0, Math.min(y1, y2));
                float maxY = Math.max(y0, Math.max(y1, y2));
                int c0 = perVertex(avgY, minY, maxY, top, bottom, y0);
                int c1 = perVertex(avgY, minY, maxY, top, bottom, y1);
                int c2 = perVertex(avgY, minY, maxY, top, bottom, y2);
                vc.addVertex(m, x0, y0, 0.0F).setColor(c0);
                vc.addVertex(m, x1, y1, 0.0F).setColor(c1);
                vc.addVertex(m, x2, y2, 0.0F).setColor(c2);
                vc.addVertex(m, x2, y2, 0.0F).setColor(c2);
            }
            default -> {
                solidTri(vc, m, x0, y0, x1, y1, x2, y2, (0xD2 << 24) | colorRGB);
            }
        }
    }

    private static int perVertex(float y, float minY, float maxY, int top, int bottom, float thisY) {
        float t = maxY == minY ? 0.0F : Mth.clamp((thisY - minY) / (maxY - minY), 0.0F, 1.0F);
        int a = (int) (255 * (1 - t) + 255 * t);
        int r = (int) (((top >> 16) & 0xFF) * (1 - t) + ((bottom >> 16) & 0xFF) * t);
        int g = (int) (((top >> 8) & 0xFF) * (1 - t) + ((bottom >> 8) & 0xFF) * t);
        int b = (int) ((top & 0xFF) * (1 - t) + (bottom & 0xFF) * t);
        return (Math.min(a, 255) << 24) | (r << 16) | (g << 8) | b;
    }

    private static void solidTri(VertexConsumer vc, Matrix4f m,
            float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        vc.addVertex(m, x0, y0, 0.0F).setColor(argb);
        vc.addVertex(m, x1, y1, 0.0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0.0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0.0F).setColor(argb);
    }

    /** Triangulo com UV mapeado pela caixa do balao (origem u0,v0; tamanho uw x vh). */
    private static void texTriMap(VertexConsumer vc, Matrix4f m,
            float x0, float y0, float x1, float y1, float x2, float y2,
            float u0, float v0, float uw, float vh) {
        float uA = (x0 - u0) / uw, vA = (y0 - v0) / vh;
        float uB = (x1 - u0) / uw, vB = (y1 - v0) / vh;
        float uC = (x2 - u0) / uw, vC = (y2 - v0) / vh;
        vc.addVertex(m, x0, y0, 0.0F).setColor(0xFFFFFFFF).setUv(uA, vA).setUv2(0, 0).setLight(240);
        vc.addVertex(m, x1, y1, 0.0F).setColor(0xFFFFFFFF).setUv(uB, vB).setUv2(0, 0).setLight(240);
        vc.addVertex(m, x2, y2, 0.0F).setColor(0xFFFFFFFF).setUv(uC, vC).setUv2(0, 0).setLight(240);
        vc.addVertex(m, x2, y2, 0.0F).setColor(0xFFFFFFFF).setUv(uC, vC).setUv2(0, 0).setLight(240);
    }

    private static int lighten(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
