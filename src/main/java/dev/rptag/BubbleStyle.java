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
 * @param extras       VARIOS emojis que acompanham a fala DENTRO do balao
 *                     (max 12 chars = 6 emojis; sao desenhados depois do texto)
 * @param stickerX     posicao do adesivo em % na largura do balao (0 = esquerda, 100 = direita)
 * @param stickerY     posicao do adesivo em % na altura do balao (0 = topo, 100 = base)
 * @param background   (mantido por compatibilidade; o visual agora usa as cores)
 * @param durationSec  tempo que o balao fica visivel, em segundos (2 a 20)
 */
public record BubbleStyle(int colorRGB, int borderRGB, int borderStyle, int textColorRGB, String prefix,
        String mid, String suffix, String corner, String extras, int stickerX, int stickerY,
        int background, int durationSec) {

    public static final int MAX_DECOR = 12;
    /** Maximo de chars de extras (2 por emoji = 6 emojis acompanhando a fala). */
    public static final int MAX_EXTRAS = 12;
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

    // ==== ESTILOS DE BORDA (o jogador escolhe o desenho da moldura!) ====
    /** Classica: linha de tinta em toda a volta. */
    public static final int BORDER_CLASSIC = 0;
    /** Cartum: contorno GROSSO, capa de quadrinho. */
    public static final int BORDER_CARTOON = 1;
    /** Dupla: duas linhas finas com um respiro entre elas. */
    public static final int BORDER_DOUBLE = 2;
    /** Tracejada: tracinhos fofos ao redor de todo o balao. */
    public static final int BORDER_DASHED = 3;
    /**
     * Brilho (nome interno "CLOUD" por historico — desde a 3.29.0 este estilo
     * NAO desenha mais a nuvem/pensamento, e sim um halo suave + anel; o
     * menu ja mostra o botao correto "Brilho"). Mantido com este nome so
     * pra nao quebrar mundos salvos com o indice 4 gravado.
     */
    public static final int BORDER_CLOUD = 4;
    /** Gibi: balaozao de HQ (estilo Turma da Monica) — inflado, tinta grossa e brilho. */
    public static final int BORDER_COMIC = 5;
    public static final int BORDER_STYLE_COUNT = 6;
    /** Cor da LETRA automatica (branco/preto conforme o fundo). */
    public static final int TEXT_AUTO = -3;

    /** Posicao padrao do adesivo livre: canto superior direito, estilo reacao. */
    public static final int DEFAULT_STICKER_X = 90;
    public static final int DEFAULT_STICKER_Y = 10;

    public static final BubbleStyle DEFAULT = new BubbleStyle(RPWorldData.DEFAULT_COLOR, DEFAULT_BORDER,
            BORDER_CLASSIC, TEXT_AUTO, "", "", "", "", "", DEFAULT_STICKER_X, DEFAULT_STICKER_Y, 0,
            DEFAULT_DURATION);

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
        if (extras == null) {
            extras = "";
        }
        if (extras.length() > MAX_EXTRAS) {
            extras = extras.substring(0, MAX_EXTRAS);
        }
        if (background < 0 || background > MAX_BACKGROUND) {
            background = 0;
        }
        durationSec = Mth_clamp(durationSec);
        if (borderStyle < 0 || borderStyle >= BORDER_STYLE_COUNT) {
            borderStyle = BORDER_CLASSIC;
        }
        if (textColorRGB >= 0) {
            textColorRGB = textColorRGB & 0xFFFFFF;
        } else {
            textColorRGB = TEXT_AUTO;
        }
        stickerX = clampPct(stickerX);
        stickerY = clampPct(stickerY);
        colorRGB = colorRGB & 0xFFFFFF;
        borderRGB = borderRGB >= 0 ? (borderRGB & 0xFFFFFF) : borderRGB; // -1 sem, -2 auto
        // MIGRACAO (3.1.0): adesivo dos slots antigos (Antes/Meio/Depois) vira o
        // adesivo LIVRE — assim nao existe mais "emoji fantasma" dobrado no balao.
        if (corner.isEmpty() && (!prefix.isEmpty() || !mid.isEmpty() || !suffix.isEmpty())) {
            corner = !prefix.isEmpty() ? prefix : (!mid.isEmpty() ? mid : suffix);
            if (stickerX == DEFAULT_STICKER_X && stickerY == DEFAULT_STICKER_Y) {
                stickerX = 10;
                stickerY = 40;
            }
            prefix = "";
            mid = "";
            suffix = "";
        }
    }

    private static int clampPct(int v) {
        return v < 5 ? 5 : (v > 95 ? 95 : v); // sempre visivel DENTRO do balao
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
                tag.contains("BorderStyle") ? tag.getInt("BorderStyle") : BORDER_CLASSIC,
                tag.contains("TextColor") ? tag.getInt("TextColor") : TEXT_AUTO,
                tag.getString("Prefix"),
                tag.contains("Mid") ? tag.getString("Mid") : "",
                tag.getString("Suffix"),
                tag.contains("Corner") ? tag.getString("Corner") : "",
                tag.contains("Extras") ? tag.getString("Extras") : "",
                tag.contains("StickerX") ? tag.getInt("StickerX") : DEFAULT_STICKER_X,
                tag.contains("StickerY") ? tag.getInt("StickerY") : DEFAULT_STICKER_Y,
                tag.getInt("Background"),
                tag.contains("Duration") ? tag.getInt("Duration") : DEFAULT_DURATION);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Color", colorRGB);
        tag.putInt("Border", borderRGB);
        tag.putInt("BorderStyle", borderStyle);
        tag.putInt("TextColor", textColorRGB);
        tag.putString("Prefix", prefix);
        tag.putString("Mid", mid);
        tag.putString("Suffix", suffix);
        tag.putString("Corner", corner);
        tag.putString("Extras", extras);
        tag.putInt("StickerX", stickerX);
        tag.putInt("StickerY", stickerY);
        tag.putInt("Background", background);
        tag.putInt("Duration", durationSec);
        return tag;
    }
}
