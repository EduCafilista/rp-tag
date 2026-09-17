package dev.rptag.client;

import dev.rptag.BubbleStyle;

/**
 * Sistema de cores do balao de fala (2.8.0 — estilo classico de quadrinhos).
 *
 * <p>O jogador escolhe TUDO com o seletor de arrastar: a cor da parte de
 * DENTRO (preenchimento) e a cor da BORDA (contorno). A cor do texto e
 * escolhida sozinha (contraste com o interior).
 */
public final class BubbleBackgrounds {

    /**
     * (3.35.0) Interior OPACO. Com 0xEE o cenario atravessava o balao e a cor
     * escolhida saia lavada — junto com a textura antiga (que escurecia ate
     * 181), um balao branco aparecia CINZA SUJO no mundo.
     */
    private static final int FILL_ALPHA = 0xFF;
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
            // (correcao) o OR bit a bit anterior misturava canais de forma
            // imprevisivel (podia CLAREAR a borda em vez de so dar um tom frio
            // sutil, e o resultado mudava de jeito estranho conforme a cor).
            // Uma soma com clamp por canal da o mesmo tom frio, sempre estavel.
            int dark = darken(colorRGB & 0xFFFFFF, 0.34F);
            return (BORDER_ALPHA << 24) | addTint(dark, 0x0A0C12);
        }
        return (BORDER_ALPHA << 24) | (borderRGB & 0xFFFFFF);
    }

    /** @return a cor do texto (contraste automatico com o interior). */
    public static int textColorFor(int colorRGB) {
        return isLight(colorRGB & 0xFFFFFF) ? 0xFF1A1714 : 0xFFFFFFFF;
    }

    public static boolean isLight(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.60;
    }

    /** Multiplica o RGB por um fator. */
    public static int darken(int rgb, float factor) {
        int r = clamp((int) (((rgb >> 16) & 0xFF) * factor));
        int g = clamp((int) (((rgb >> 8) & 0xFF) * factor));
        int b = clamp((int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Soma um tom (tint) ao RGB, com clamp por canal (nunca estoura pra cor errada). */
    private static int addTint(int rgb, int tint) {
        int r = clamp(((rgb >> 16) & 0xFF) + ((tint >> 16) & 0xFF));
        int g = clamp(((rgb >> 8) & 0xFF) + ((tint >> 8) & 0xFF));
        int b = clamp((rgb & 0xFF) + (tint & 0xFF));
        return (r << 16) | (g << 8) | b;
    }
}
