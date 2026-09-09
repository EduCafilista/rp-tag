package dev.rptag;

import net.minecraft.nbt.CompoundTag;

/**
 * Estilo visual do balao de fala de um jogador.
 *
 * @param colorRGB   cor de fundo (0xRRGGBB) e da mensagem no chat
 * @param prefix     texto/emoji decorativo ANTES da mensagem (max 12)
 * @param suffix     texto/emoji decorativo DEPOIS da mensagem (max 12)
 * @param background estilo de fundo: 0=translucido, 1=escuro solido,
 *                   2=claro solido, 3=gradiente, 4=papel, 5=noite, 6=madeira
 */
public record BubbleStyle(int colorRGB, String prefix, String suffix, int background) {

    public static final int MAX_DECOR = 12;
    public static final int MAX_BACKGROUND = 6;

    public static final BubbleStyle DEFAULT = new BubbleStyle(RPWorldData.DEFAULT_COLOR, "", "", 0);

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
    }

    public static BubbleStyle load(CompoundTag tag) {
        return new BubbleStyle(
                tag.getInt("Color"),
                tag.getString("Prefix"),
                tag.getString("Suffix"),
                tag.getInt("Background"));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Color", colorRGB);
        tag.putString("Prefix", prefix);
        tag.putString("Suffix", suffix);
        tag.putInt("Background", background);
        return tag;
    }
}
