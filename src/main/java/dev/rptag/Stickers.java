package dev.rptag;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * ADESIVOS do balao de fala (2.7.0): 48 emojis COLORIDOS em pixel-art,
 * salvos como fonte de bitmap do jogo (imagens de verdade, nada de emote
 * preto e branco). Fluem JUNTO com o texto, como emoji de celular.
 *
 * <p>Cada adesivo e um caractere reservado (U+E000 a U+E02F) com o estilo
 * apontando para a fonte {@code rptag:stickers} — quem desenha a imagem e o
 * proprio motor de texto do Minecraft.
 */
public final class Stickers {

    /** Fonte dos adesivos (assets/rptag/font/stickers.json). */
    public static final ResourceLocation FONT =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "stickers");

    /** Quantidade de adesivos (8 x 6 na sprite sheet). */
    public static final int COUNT = 48;

    /** Glifos dos 48 adesivos (grade 8 x 6 da sprite sheet). */
    public static final String[] GLYPHS = new String[COUNT];

    /** Nomes (para o tooltip do teclado de adesivos). */
    public static final String[] NAMES = {
            "Coração", "Coração roxo", "Coração azul", "Coração verde",
            "Coração amarelo", "Coração partido", "Estrela", "Brilho",
            "Trevo", "Fogo", "Raio", "Lua",
            "Sol", "Flor", "Broto", "Gota",
            "Nota musical", "Notas", "Espada", "Escudo",
            "Poção", "Ovo", "Coroa", "Diamante",
            "Balão", "Bolo", "Maçã", "Morango",
            "Moeda", "Dado", "Feliz", "Sorriso",
            "Chorando", "Bravo", "Dormindo", "Surpreso",
            "Óculos", "Língua", "Gato", "Cachorro",
            "Coelho", "Urso", "Pintinho", "Peixe",
            "Exclamação", "Interrogação", "Balão de fala", "Zzz"
    };

    static {
        for (int i = 0; i < COUNT; i++) {
            GLYPHS[i] = new String(Character.toChars(0xE000 + i));
        }
    }

    private Stickers() {
    }

    public static boolean isStickerChar(int codePoint) {
        return codePoint >= 0xE000 && codePoint < 0xE000 + COUNT;
    }

    /** Estilo que renderiza o caractere como adesivo colorido. */
    public static Style stickerStyle() {
        return Style.EMPTY.withFont(FONT);
    }

    /** Botao de adesivo na tela: mostra o glifo colorido (ou um traco se vazio). */
    public static Component label(String glyph) {
        if (glyph == null || glyph.isEmpty()) {
            return Component.literal("—");
        }
        return Component.literal(glyph).withStyle(Style.EMPTY.withFont(FONT).withItalic(false));
    }

    /**
     * Monta o texto do balao como COMPONENTE: caracteres-adesivo ganham a
     * fonte de imagem; o resto fica no texto normal (herda a cor).
     */
    public static MutableComponent styled(String s) {
        MutableComponent out = Component.empty();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (isStickerChar(cp)) {
                if (plain.length() > 0) {
                    out = out.append(plain.toString());
                    plain.setLength(0);
                }
                out = out.append(Component.literal(new String(Character.toChars(cp)))
                        .withStyle(stickerStyle()));
                i += Character.charCount(cp);
            } else {
                plain.appendCodePoint(cp);
                i++;
            }
        }
        if (plain.length() > 0) {
            out = out.append(plain.toString());
        }
        return out;
    }
}
