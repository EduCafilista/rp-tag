package dev.rptag;

import net.minecraft.nbt.CompoundTag;

/**
 * Estilo visual do balao de fala de um jogador.
 *
 * @param colorRGB     cor da parte de DENTRO do balao (0xRRGGBB)
 * @param borderRGB    cor da BORDA (0xRRGGBB), -1 = sem borda, -2 = automatica
 * @param prefix       adesivo ANTES da mensagem (max 12)
 * @param mid          adesivo no MEIO da mensagem (max 12)
 * @param suffix       adesivo DEPOIS da mensagem (max 12)
 * @param corner       adesivo LIVRE (o jogador coloca em QUALQUER ponto do balao, max 12)
 * @param stickerX     posicao do adesivo em % na largura do balao (0 = esquerda, 100 = direita)
 * @param stickerY     posicao do adesivo em % na altura do balao (0 = topo, 100 = base)
 * @param background   (mantido por compatibilidade; o visual agora usa as cores)
 * @param durationSec  tempo que o balao fica visivel, em segundos (2 a 20)
 */
public record BubbleStyle(int colorRGB, int borderRGB, String prefix, String mid, String suffix,
        String corner, int stickerX, int stickerY, int background, int durationSec) {

    public static final int MAX_DECOR = 12;
    public static final int MAX_BACKGROUND = 6;

    /** Duracao padrao (segundos) e limites do slider. */
    public static final int DEFAULT_DURATION = 6;
    public static final int MIN_DURATION = 2;
    public static final int MAX_DURATION = 20;

    /** Sem borda. */
    public static final int NO_BORDER = -1;
    /**
     * Borda AUTOMATICA (padrao): um tom profundo derivado da cor de dentro —
     * a moldura sempre combina.
     */
    public static final int AUTO_BORDER = -2;
    /** Borda padrao do estilo default. */
    public static final int DEFAULT_BORDER = AUTO_BORDER;

    /** Posicao padrao do adesivo livre: canto superior direito, estilo reacao. */
    public static final int DEFAULT_STICKER_X = 95;
    public static final int DEFAULT_STICKER_Y = 8;

    public static final BubbleStyle DEFAULT = new BubbleStyle(RPWorldData.DEFAULT_COLOR, DEFAULT_BORDER,
            "", "", "", "", DEFAULT_STICKER_X, DEFAULT_STICKER_Y, 0, DEFAULT_DURATION);

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
        if (corner == null) {
            corner = "";
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
        if (corner.length() > MAX_DECOR) {
            corner = corner.substring(0, MAX_DECOR);
        }
        if (background < 0 || background > MAX_BACKGROUND) {
            background = 0;
        }
        durationSec = Mth_clamp(durationSec);
        stickerX = clampPct(stickerX);
        stickerY = clampPct(stickerY);
        colorRGB = colorRGB & 0xFFFFFF;
        borderRGB = borderRGB >= 0 ? (borderRGB & 0xFFFFFF) : borderRGB; // -1 sem, -2 auto
    }

    private static int clampPct(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
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
                tag.contains("Corner") ? tag.getString("Corner") : "",
                tag.contains("StickerX") ? tag.getInt("StickerX") : DEFAULT_STICKER_X,
                tag.contains("StickerY") ? tag.getInt("StickerY") : DEFAULT_STICKER_Y,
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
        tag.putString("Corner", corner);
        tag.putInt("StickerX", stickerX);
        tag.putInt("StickerY", stickerY);
        tag.putInt("Background", background);
        tag.putInt("Duration", durationSec);
        return tag;
    }
}
