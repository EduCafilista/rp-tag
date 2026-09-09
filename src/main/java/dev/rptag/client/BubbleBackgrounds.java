package dev.rptag.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import dev.rptag.BubbleStyle;
import dev.rptag.RPTagMod;

/**
 * Fundos do balao de fala: cores solidas, gradiente e texturas
 * (papel/noite/madeira), borda opcional, e o "branco" usado como base.
 *
 * <p>IMPORTANTE (fix da 2.3.0): para desenhar poligonos no MUNDO 3D usamos
 * {@link RenderType#text(ResourceLocation)} — o mesmo render type do texto das
 * nametags, que funciona no contexto de entidades. O RenderType.gui() usado
 * antes era de GUI e o fundo ficava INVISIVEL no mundo. A textura branca
 * (white.png) multiplica pela cor do vertice, entao vale para qualquer cor.
 */
public final class BubbleBackgrounds {

    /** Luz maxima (mesmo valor do vanilla: 240 | 240 << 16). */
    public static final int LIGHT_FULLBRIGHT = 15728880;

    /** Estilos (mesmos codigos de {@link BubbleStyle}). */
    public static final int BG_TRANSLUCIDO = 0;
    public static final int BG_ESCURO = 1;
    public static final int BG_CLARO = 2;
    public static final int BG_GRADIENTE = 3;
    public static final int BG_PAPEL = 4;
    public static final int BG_NOITE = 5;
    public static final int BG_MADEIRA = 6;

    private static final ResourceLocation TEX_WHITE = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/white.png");
    private static final ResourceLocation TEX_PAPEL = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_papel.png");
    private static final ResourceLocation TEX_NOITE = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_noite.png");
    private static final ResourceLocation TEX_MADEIRA = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_madeira.png");

    /** Cache local de render types (RenderType.text e memoizado no vanilla, mas garantimos). */
    private static final Map<ResourceLocation, RenderType> TEXT_TYPES = new ConcurrentHashMap<>();

    private BubbleBackgrounds() {
    }

    /** @return o RenderType adequado ao estilo (funciona no mundo 3D e na GUI). */
    public static RenderType typeFor(int background) {
        ResourceLocation tex = switch (background) {
            case BG_PAPEL -> TEX_PAPEL;
            case BG_NOITE -> TEX_NOITE;
            case BG_MADEIRA -> TEX_MADEIRA;
            default -> TEX_WHITE; // translucido/escuro/claro/gradiente usam o branco
        };
        return TEXT_TYPES.computeIfAbsent(tex, RenderType::text);
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
    //  Desenho da capsula (usado pelo renderizador 3D e pelo preview 2D)
    // =====================================================================

    /** Desenha a borda (capsula 1px maior) e depois a capsula principal. */
    public static void pillWithBorder(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h,
            int background, int colorRGB, int borderRGB) {
        if (borderRGB != BubbleStyle.NO_BORDER) {
            int borderAlpha = background == BG_TRANSLUCIDO ? 0xF0 : 0xFF;
            pill(vc, m, x - 1.0F, y - 1.0F, w + 2.0F, h + 2.0F, background,
                    borderRGB | (borderAlpha << 24), false);
        }
        pill(vc, m, x, y, w, h, background, colorRGB, true);
    }

    /** Desenha a capsula arredondada inteira. */
    public static void pill(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h,
            int background, int colorARGBorRGB) {
        pill(vc, m, x, y, w, h, background, colorARGBorRGB, true);
    }

    private static void pill(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h,
            int background, int color, boolean mainLayer) {
        float r = Math.min(h / 2.0F, 5.0F);
        float x1 = x + w;
        float y1 = y + h;

        quad(vc, m, x + r, y, x1 - r, y1, background, color, x, y, w, h);
        quad(vc, m, x, y + r, x + r, y1 - r, background, color, x, y, w, h);
        quad(vc, m, x1 - r, y + r, x1, y1 - r, background, color, x, y, w, h);

        fan(vc, m, x + r, y + r, r, 180.0F, 270.0F, background, color, x, y, w, h);
        fan(vc, m, x1 - r, y + r, r, 270.0F, 360.0F, background, color, x, y, w, h);
        fan(vc, m, x1 - r, y1 - r, r, 0.0F, 90.0F, background, color, x, y, w, h);
        fan(vc, m, x + r, y1 - r, r, 90.0F, 180.0F, background, color, x, y, w, h);
    }

    /** Triangulo do rabicho com o estilo aplicado (sem borda no rabicho). */
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
            int background, int color, float u0, float v0, float uw, float vh) {
        if (isTextured(background)) {
            texTriMap(vc, m, ax, ay, bx, ay, ax, by, u0, v0, uw, vh);
            texTriMap(vc, m, ax, by, bx, ay, bx, by, u0, v0, uw, vh);
        } else {
            colorTri(vc, m, ax, ay, bx, ay, ax, by, background, color);
            colorTri(vc, m, ax, by, bx, ay, bx, by, background, color);
        }
    }

    private static void fan(VertexConsumer vc, Matrix4f m, float cx, float cy, float r,
            float a0, float a1, int background, int color,
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
                colorTri(vc, m, cx, cy, px0, py0, px1, py1, background, color);
            }
        }
    }

    /** Triangulo com cor (pode ser gradiente vertical: top = claro, bottom = escuro). */
    private static void colorTri(VertexConsumer vc, Matrix4f m,
            float x0, float y0, float x1, float y1, float x2, float y2,
            int background, int color) {
        switch (background) {
            case BG_ESCURO -> {
                solidTri(vc, m, x0, y0, x1, y1, x2, y2, 0xFF14141A);
            }
            case BG_CLARO -> {
                solidTri(vc, m, x0, y0, x1, y1, x2, y2, 0xFFF4F0E8);
            }
            case BG_GRADIENTE -> {
                int base = color & 0xFFFFFF;
                int top = lighten(base, 1.18F);
                int bottom = lighten(base, 0.62F);
                float minY = Math.min(y0, Math.min(y1, y2));
                float maxY = Math.max(y0, Math.max(y1, y2));
                int c0 = perVertex(minY, maxY, top, bottom, y0);
                int c1 = perVertex(minY, maxY, top, bottom, y1);
                int c2 = perVertex(minY, maxY, top, bottom, y2);
                vc.addVertex(m, x0, y0, 0.0F).setColor(c0);
                vc.addVertex(m, x1, y1, 0.0F).setColor(c1);
                vc.addVertex(m, x2, y2, 0.0F).setColor(c2);
                vc.addVertex(m, x2, y2, 0.0F).setColor(c2);
            }
            default -> {
                solidTri(vc, m, x0, y0, x1, y1, x2, y2, (0xE6 << 24) | (color & 0xFFFFFF));
            }
        }
    }

    private static int perVertex(float minY, float maxY, int top, int bottom, float thisY) {
        float t = maxY == minY ? 0.0F : Mth.clamp((thisY - minY) / (maxY - minY), 0.0F, 1.0F);
        int r = (int) (((top >> 16) & 0xFF) * (1 - t) + ((bottom >> 16) & 0xFF) * t);
        int g = (int) (((top >> 8) & 0xFF) * (1 - t) + ((bottom >> 8) & 0xFF) * t);
        int b = (int) ((top & 0xFF) * (1 - t) + (bottom & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
        // emissao identica a do BakedGlyph do vanilla (setColor + setUv + setLight)
        float uA = (x0 - u0) / uw, vA = (y0 - v0) / vh;
        float uB = (x1 - u0) / uw, vB = (y1 - v0) / vh;
        float uC = (x2 - u0) / uw, vC = (y2 - v0) / vh;
        vc.addVertex(m, x0, y0, 0.0F).setColor(0xFFFFFFFF).setUv(uA, vA).setLight(LIGHT_FULLBRIGHT);
        vc.addVertex(m, x1, y1, 0.0F).setColor(0xFFFFFFFF).setUv(uB, vB).setLight(LIGHT_FULLBRIGHT);
        vc.addVertex(m, x2, y2, 0.0F).setColor(0xFFFFFFFF).setUv(uC, vC).setLight(LIGHT_FULLBRIGHT);
        vc.addVertex(m, x2, y2, 0.0F).setColor(0xFFFFFFFF).setUv(uC, vC).setLight(LIGHT_FULLBRIGHT);
    }

    private static int lighten(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
