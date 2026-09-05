package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Utilitarios para montar a tag (ʀᴘ) / (ᴏꜰꜰ ʀᴘ).
 *
 * <p>A tag fica entre parenteses e usa SMALL CAPITALS Unicode ("letras
 * maiusculas pequenas"), que dao um visual de fonte diferente no jogo.
 *
 * <p>POR QUE NAO 𝐑𝐏 (negrito matematico, U+1D400)? Porque o Unifont 15.1
 * (a fonte que o Minecraft 1.21.1 usa pra simbolos) NAO tem esse bloco —
 * apareceria quadradinho. Small capitals existem no Unifont e renderizam
 * em qualquer client 1.21.1 (verificado glifo a glifo no unifont.hex).
 *
 * <p>Ha tambem um motivo historico: esses caracteres nao cabem em um {@code char}
 * Java (acima de U+FFFF) — um cast para char trunca o codigo e vira simbolos
 * coreanos (bug da versao 1.2.0). Small capitals sao BMP, logo seguras.
 *
 * <p>Cores: (ʀᴘ) = ciano (AQUA), (ᴏꜰꜰ ʀᴘ) = cinza. Apenas a tag recebe cor;
 * o nome do jogador continua sem cor.
 */
public final class RPTags {

    /** Texto simples dentro das tags (sem parenteses, sem fonte especial). */
    public static final String TAG_ON = "RP";
    public static final String TAG_OFF = "OFF RP";

    /**
     * Letras A-Z em small capital (Latin Letter Small Capital). Todas confirmadas
     * no Unifont 15.1, exceto X (nao existe; mantem 'x' normal).
     */
    private static final String[] SMALL_CAPS = {
            "\u1D00", // A
            "\u1D2E", // B
            "\u1D04", // C
            "\u1D05", // D
            "\u1D07", // E
            "\uA730", // F
            "\u0262", // G
            "\u029C", // H
            "\u026A", // I
            "\u1D0B", // J
            "\u1D0C", // K
            "\u029F", // L
            "\u1D0D", // M
            "\u0274", // N
            "\u1D0F", // O
            "\u1D18", // P
            "\uA7B1", // Q (LATIN LETTER SMALL CAPITAL Q)
            "\u0280", // R
            "\uA731", // S
            "\u1D1B", // T
            "\u1D1D", // U
            "\u1D20", // V
            "\u1D21", // W
            null,     // X (nao existe small capital)
            "\u028F", // Y
            "\u1D22"  // Z
    };

    private RPTags() {
    }

    /**
     * Converte letras para small capitals (A->ᴀ, R->ʀ, ...) — chars BMP
     * simples, sem risco de surrogate/truncamento.
     */
    public static String toSmallCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                String sc = SMALL_CAPS[c - 'A'];
                sb.append(sc != null ? sc : c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** @return o texto da tag com parenteses e fonte especial, ex.: (ʀᴘ). */
    public static String tagText(boolean inRp) {
        return "(" + toSmallCaps(inRp ? TAG_ON : TAG_OFF) + ")";
    }

    /** @return a tag completa, colorida (ciano p/ RP, cinza p/ OFF RP). */
    public static MutableComponent tag(boolean inRp) {
        return inRp
                ? Component.literal(tagText(true)).withStyle(ChatFormatting.AQUA)
                : Component.literal(tagText(false)).withStyle(ChatFormatting.GRAY);
    }

    /** @return true se o texto do componente ja termina com alguma das tags. */
    public static boolean hasTag(Component component) {
        if (component == null) {
            return false;
        }
        String text = component.getString();
        return text.endsWith(tagText(true)) || text.endsWith(tagText(false));
    }
}
