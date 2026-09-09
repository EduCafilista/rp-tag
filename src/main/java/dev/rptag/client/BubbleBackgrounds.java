package dev.rptag.client;

import dev.rptag.BubbleStyle;

/**
 * Cores dos fundos do balao de fala.
 *
 * <p>O fundo e desenhado pelo PROPRIO JOGO: usamos o mesmo mecanismo do fundo
 * das nametags do Minecraft (a cor de fundo do drawInBatch do texto). Nao
 * existe mais nenhum poligono desenhado a mao — impossivel ficar invisivel,
 * impossivel derrubar o cliente.
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

    /** Cor de fundo do balao (formato ARGB, como o jogo espera). */
    public static int bgColorFor(int background, int colorRGB) {
        int c = colorRGB & 0xFFFFFF;
        return switch (background) {
            case BG_ESCURO -> 0xF014141A;
            case BG_CLARO -> 0xFFF6F2E9;
            case BG_GRADIENTE -> 0xE8000000 | c;
            case BG_PAPEL -> 0xFFF2E9D5;
            case BG_NOITE -> 0xF0101830;
            case BG_MADEIRA -> 0xF06E4B26;
            default -> (0xB4 << 24) | c; // translucido: a COR DO JOGADOR ~70%
        };
    }

    /** @return a cor do texto (contraste automatico com o fundo). */
    public static int textColorFor(int background, int colorRGB) {
        return switch (background) {
            case BG_CLARO, BG_PAPEL -> 0xFF1A1A22;
            case BG_NOITE, BG_MADEIRA -> 0xFFFFFFFF;
            default -> isLight(colorRGB) ? 0xFF1A1A22 : 0xFFFFFFFF;
        };
    }

    public static boolean isLight(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.62;
    }
}
