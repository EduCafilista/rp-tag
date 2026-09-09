package dev.rptag;

import net.minecraft.nbt.CompoundTag;

/**
 * Estilo visual do balao de fala de um jogador.
 *
 * @param colorRGB     cor de destaque do balao (0xRRGGBB) e da mensagem no chat
 * @param borderRGB    cor da barra lateral (0xRRGGBB), -1 = sem barra, -2 = automatica
 * @param prefix       adesivo ANTES da mensagem (max 12)
 * @param mid          adesivo no MEIO da mensagem (max 12)
 * @param suffix       adesivo DEPOIS da mensagem (max 12)
 * @param background   estilo de fundo: 0=translucido, 1=escuro solido,
 *                     2=claro, 3=gradiente, 4=papel, 5=noite, 6=madeira
 * @param durationSec  tempo que o balao fica visivel, em segundos (2 a 20)
 */
public record BubbleStyle(int colorRGB, int borderRGB, String prefix, String mid, String suffix,
        int background, int durationSec) {

    public static final int MAX_DECOR = 12;
    public static final int MAX_BACKGROUND = 6;

    /** Duracao padrao (segundos) e limites do slider. */
    public static final int DEFAULT_DURATION = 6;
    public static final int MIN_DURATION = 2;
    public static final int MAX_DURATION = 20;

    /** Sem barra. */
    public static final int NO_BORDER = -1;
    /**
     * Barra AUTOMATICA (padrao): deriva da cor escolhida pelo jogador
     * (escurece conforme precisar) — a moldura sempre combina.
     */
    public static final int AUTO_BORDER = -2;
    /** Barra padrao do estilo default (quando nao ha cor definida). */
    public static final int DEFAULT_BORDER = AUTO_BORDER;

    public static final BubbleStyle DEFAULT =
            new BubbleStyle(RPWorldData.DEFAULT_COLOR, DEFAULT_BORDER, "", "", "", 0, DEFAULT_DURATION);

    public BubbleStyle {
        if (prefix == null) {
            prefix = "";
        }
        if (mid == null) {
            mid = "";
        }
        if (suffix == null) {
            suffix = "";
        }
        if (prefix.length() > MAX_DECOR) {
            prefix = prefix.substring(0, MAX_DECOR);
        }
        if (mid.length() > MAX_DECOR) {
            mid = mid.substring(0, MAX_DECOR);
        }
        if (suffix.length() > MAX_DECOR) {
            suffix = suffix.substring(0, MAX_DECOR);
        }
        if (background < 0 || background > MAX_BACKGROUND) {
            background = 0;
        }
        durationSec = Mth_clamp(durationSec);
        colorRGB = colorRGB & 0xFFFFFF;
        borderRGB = borderRGB >= 0 ? (borderRGB & 0xFFFFFF) : borderRGB; // -1 sem, -2 auto
    }

    private static int Mth_clamp(int v) {
        return v < MIN_DURATION ? DEFAULT_DURATION : (v > MAX_DURATION ? MAX_DURATION : v);
    }

    public boolean hasBorder() {
        return borderRGB != NO_BORDER;
    }

    public static BubbleStyle load(CompoundTag tag) {
        return new BubbleStyle(
                tag.getInt("Color"),
                tag.contains("Border") ? tag.getInt("Border") : DEFAULT_BORDER,
                tag.getString("Prefix"),
                tag.contains("Mid") ? tag.getString("Mid") : "",
                tag.getString("Suffix"),
                tag.getInt("Background"),
                tag.contains("Duration") ? tag.getInt("Duration") : DEFAULT_DURATION);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Color", colorRGB);
        tag.putInt("Border", borderRGB);
        tag.putString("Prefix", prefix);
        tag.putString("Mid", mid);
        tag.putString("Suffix", suffix);
        tag.putInt("Background", background);
        tag.putInt("Duration", durationSec);
        return tag;
    }
}
