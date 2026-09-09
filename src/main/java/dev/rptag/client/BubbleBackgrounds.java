package dev.rptag.client;

import dev.rptag.BubbleStyle;
import net.minecraft.util.Mth;

/**
 * Sistema de cores do balao de fala (design 2.5.0).
 *
 * <p>O desenho e todo feito pelo PROPRIO JOGO (barras de texto com cor de
 * fundo — o mesmo mecanismo do fundo das nametags). Esta classe apenas
 * DECIDE as cores: fundo, moldura, barra de destaque e texto.
 *
 * <p>O visual e um "cartao" moderno: moldura de 1px, cantos chanfrados,
 * barra de destaque (accent) com a cor escolhida pelo jogador e fundo
 * suave — inspirado nos tooltips do jogo e em apps de voz.
 */
public final class BubbleBackgrounds {

    /** Estilos (mesmos codigos de {@link BubbleStyle}). */
    public static final int BG_TRANSLUCIDO = 0;
    public static final int BG_ESCURO = 1;
    public static final int BG_CLARO = 2;
    public static final int BG_GRADIENTE = 3;
    public static final int BG_PAPEL = 4;
    public static final int BG_NOITE = 5;
    public static final int BG_MADEIRA = 6;

    private BubbleBackgrounds() {
    }

    /** Cor de FUNDO do balao (ARGB), por estilo. */
    public static int bgColorFor(int background, int colorRGB) {
        int c = colorRGB & 0xFFFFFF;
        return switch (background) {
            case BG_ESCURO -> 0xF6141822;
            case BG_CLARO -> 0xF9F7F2E9;
            case BG_GRADIENTE -> 0x00000000 | c; // tratado linha a linha (gradiente real)
            case BG_PAPEL -> 0xFBF4ECDB;
            case BG_NOITE -> 0xF50E1526;
            case BG_MADEIRA -> 0xF56B4A28;
            default -> (0x9E << 24) | 0x12161C; // translucido: vidro escuro
        };
    }

    /** Cor de fundo do gradiente em uma linha (t de 0=topo a 1=base). */
    public static int gradientColorFor(int colorRGB, float t) {
        int top = lighten(colorRGB & 0xFFFFFF, 1.25F);
        int bottom = darken(colorRGB & 0xFFFFFF, 0.40F);
        return (0xFC << 24) | lerpRgb(top, bottom, t);
    }

    /**
     * Cor da MOLDURA (ARGB). AUTO (-2) deriva da cor do jogador; especifica
     * usa a cor escolhida; NO_BORDER (-1) devolve 0 (nao desenha).
     */
    public static int borderColorFor(int background, int colorRGB, int borderRGB) {
        if (borderRGB == BubbleStyle.NO_BORDER) {
            return 0;
        }
        if (borderRGB == BubbleStyle.AUTO_BORDER) {
            int c = background == BG_TRANSLUCIDO ? 0xFFFFFF : (colorRGB & 0xFFFFFF);
            return (0xE0 << 24) | darken(c, 0.45F) | 0x101012; // sempre tom profundo
        }
        return 0xF0000000 | (borderRGB & 0xFFFFFF);
    }

    /** Cor da BARRA de destaque (a "assinatura" da cor do jogador). */
    public static int accentColorFor(int background, int colorRGB) {
        int c = colorRGB & 0xFFFFFF;
        if (background == BG_CLARO || background == BG_PAPEL) {
            return 0xFF000000 | darken(c, 0.85F); // em fundos claros, accent mais firme
        }
        return 0xFF000000 | c;
    }

    /** @return a cor do texto (contraste automatico com o fundo). */
    public static int textColorFor(int background, int colorRGB) {
        return switch (background) {
            case BG_CLARO, BG_PAPEL -> 0xFF241F16;
            case BG_GRADIENTE -> isLight(colorRGB & 0xFFFFFF) ? 0xFF241F16 : 0xFFFFFFFF;
            default -> 0xFFFFFFFF;
        };
    }

    public static boolean isLight(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.60;
    }

    /** Multiplica o RGB por um fator (0-~1.3). */
    public static int darken(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    /** Clareia o RGB (fator > 1 puxa pro branco suavemente). */
    public static int lighten(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Mth.clamp((int) (r * factor + 24), 0, 255);
        g = Mth.clamp((int) (g * factor + 24), 0, 255);
        b = Mth.clamp((int) (b * factor + 24), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
