package dev.rptag.client;

import dev.rptag.BubbleStyle;
import net.minecraft.util.Mth;

/**
 * Sistema de cores do balao de fala (2.7.0 — CUSTOMIZACAO TOTAL).
 *
 * <p>O jogador escolhe TUDO: a cor da parte de DENTRO (preenchimento) e a
 * cor da BORDA (moldura), cada uma com o seletor de arrastar. A cor do texto
 * e escolhida sozinha (contraste com o interior).
 *
 * <p>O desenho e todo feito pelo PROPRIO JOGO (linhas de texto com cor de
 * fundo — o mesmo mecanismo do fundo das nametags). Esta classe apenas
 * DECIDE as cores.
 */
public final class BubbleBackgrounds {

    /** Transparencia do interior (quase opaco, legivel). */
    private static final int FILL_ALPHA = 0xF2;
    /** Transparencia da borda. */
    private static final int BORDER_ALPHA = 0xFF;

    private BubbleBackgrounds() {
    }

    /** Cor de DENTRO do balao (ARGB) — a cor escolhida pelo jogador. */
    public static int fillColorFor(int colorRGB) {
        return (FILL_ALPHA << 24) | (colorRGB & 0xFFFFFF);
    }

    /**
     * Cor da BORDA (ARGB). AUTO (-2) = um tom profundo derivado da cor
     * escolhida; cor especifica (>=0) usa a cor escolhida; -1 = sem borda.
     */
    public static int borderColorFor(int colorRGB, int borderRGB) {
        if (borderRGB == BubbleStyle.NO_BORDER) {
            return 0;
        }
        if (borderRGB == BubbleStyle.AUTO_BORDER) {
            return (BORDER_ALPHA << 24) | darken(colorRGB & 0xFFFFFF, 0.42F) | 0x0A0C12;
        }
        return (BORDER_ALPHA << 24) | (borderRGB & 0xFFFFFF);
    }

    /** @return a cor do texto (contraste automatico com o interior). */
    public static int textColorFor(int colorRGB) {
        return isLight(colorRGB & 0xFFFFFF) ? 0xFF241F16 : 0xFFFFFFFF;
    }

    public static boolean isLight(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.60;
    }

    /** Multiplica o RGB por um fator. */
    public static int darken(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
