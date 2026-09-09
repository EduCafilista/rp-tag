package dev.rptag;

import net.minecraft.nbt.CompoundTag;

/**
 * Estilo visual do balao de fala de um jogador.
 *
 * @param colorRGB   cor do CENTRO do balao (0xRRGGBB) e da mensagem no chat
 * @param borderRGB  cor da BORDA do balao (0xRRGGBB) ou -1 = sem borda
 * @param prefix     texto/emoji decorativo ANTES da mensagem (max 12)
 * @param suffix     texto/emoji decorativo DEPOIS da mensagem (max 12)
 * @param background estilo de fundo: 0=translucido, 1=escuro solido,
 *                   2=claro solido, 3=gradiente, 4=papel, 5=noite, 6=madeira
 */
public record BubbleStyle(int colorRGB, int borderRGB, String prefix, String suffix, int background) {

    public static final int MAX_DECOR = 12;
    public static final int MAX_BACKGROUND = 6;

    /** Sem borda. */
    public static final int NO_BORDER = -1;
    /** Borda padrao: quase preta (destaca o balao em qualquer cenario). */
    public static final int DEFAULT_BORDER = 0x111118;

    public static final BubbleStyle DEFAULT =
            new BubbleStyle(RPWorldData.DEFAULT_COLOR, DEFAULT_BORDER, "", "", 0);

    public BubbleStyle {
        if (prefix == null) {
            prefix = "";
        }
        if (suffix == null) {
            suffix = "";
        }
        if (prefix.length() > MAX_DECOR) {
            prefix = prefix.substring(0, MAX_DECOR);
        }
        if (suffix.length() > MAX_DECOR) {
            suffix = suffix.substring(0, MAX_DECOR);
        }
        if (background < 0 || background > MAX_BACKGROUND) {
            background = 0;
        }
        colorRGB = colorRGB & 0xFFFFFF;
        borderRGB = borderRGB == NO_BORDER ? NO_BORDER : (borderRGB & 0xFFFFFF);
    }

    public boolean hasBorder() {
        return borderRGB != NO_BORDER;
    }

    public static BubbleStyle load(CompoundTag tag) {
        return new BubbleStyle(
                tag.getInt("Color"),
                tag.contains("Border") ? tag.getInt("Border") : DEFAULT_BORDER,
                tag.getString("Prefix"),
                tag.getString("Suffix"),
                tag.getInt("Background"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Color", colorRGB);
        tag.putInt("Border", borderRGB);
        tag.putString("Prefix", prefix);
        tag.putString("Suffix", suffix);
        tag.putInt("Background", background);
        return tag;
    }
}
