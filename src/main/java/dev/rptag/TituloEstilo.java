package dev.rptag;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * (5.10.1) O TÍTULO BONITO — o dono reclamou que era "só as letras naquela cor".
 *
 * <p>Como o Minecraft só deixa escolher COR / NEGRITO / ITÁLICO por caractere,
 * o "capricho" aqui é feito letra por letra:
 *
 * <pre>
 *    [C h r o n o s]  Nome  [O Ferreiro]
 *     ↑ ↑─────────↑  ↑     ↑─────────↑
 *     │  gradiente   │     │ itálico com gradiente
 *     └ colchete apagado (55% da cor)
 * </pre>
 *
 * <ul>
 *   <li><b>gradiente metálico</b>: a etiqueta começa quase branca (o brilho do
 *       metal pegando a luz) e termina escura (a sombra) — 1,55× a 0,55× da cor
 *       escolhida. Cor escura (preta, vinho…) inverte a rampa: começa clara de
 *       verdade, senão o preto ficaria invisível no chat.</li>
 *   <li><b>colchetes apagados</b>: 55% da cor, sem negrito — é o que dá a
 *       impressão de "moldura" em volta da etiqueta.</li>
 *   <li><b>etiqueta em negrito</b> e <b>título pessoal em itálico</b>: separa o
 *       "selo" (a classe) do "apelido" (o que o jogador é).</li>
 *   <li><b>hover</b>: passar o mouse na etiqueta mostra o nome e a cor em HEX.</li>
 * </ul>
 *
 * <p>Vale nos TRÊS lugares: TAB, chat e o nametag acima da cabeça (o cliente usa
 * esta mesma classe, então o nametag fica idêntico ao chat — nada de um bonito
 * e outro sem graça).
 */
public final class TituloEstilo {

    private TituloEstilo() {
    }

    /** quanto o colchete é apagado em relação à cor (0.55 = 55% do brilho). */
    private static final float COLCHETE = 0.55f;

    /** {@code [Chronos]} bonito: colchetes apagados + letras em gradiente e negrito. */
    public static MutableComponent etiqueta(String texto, int cor) {
        MutableComponent out = Component.empty();
        out.append(colchete("[", cor));
        out.append(gradiente(texto, cor, true, false));
        out.append(colchete("]", cor));
        // o "hover" só na etiqueta: quem quiser saber de que cor é, passa o mouse
        String hex = String.format("#%06X", cor & 0xFFFFFF);
        Style dica = Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal("Classe: ").withStyle(s -> s.withColor(0xAAAAAA))
                        .append(Component.literal(texto).withStyle(s -> s.withColor(cor)))
                        .append(Component.literal("  " + hex).withStyle(s -> s.withColor(0x555555)))));
        out.withStyle(s -> s.withHoverEvent(dica.getHoverEvent()));
        return out;
    }

    /** {@code [O Ferreiro]} bonito: espaço na frente, colchetes apagados, itálico em gradiente. */
    public static MutableComponent pessoal(String texto, int cor) {
        MutableComponent out = Component.empty();
        out.append(Component.literal(" "));
        out.append(colchete("[", cor));
        out.append(gradiente(texto, cor, false, true));
        out.append(colchete("]", cor));
        return out;
    }

    private static MutableComponent colchete(String s, int cor) {
        final int c = escurecer(cor, COLCHETE);
        return Component.literal(s).withStyle(st -> st.withColor(TextColor.fromRgb(c)));
    }

    /**
     * A etiqueta/o título letra por letra, com o gradiente metálico.
     *
     * @param negrito etiqueta sim (o "selo" tem peso), título pessoal não
     * @param italico o título pessoal é um comentário, então vai em itálico
     */
    public static MutableComponent gradiente(String texto, int cor, boolean negrito, boolean italico) {
        MutableComponent out = Component.empty();
        int n = texto.length();
        for (int i = 0; i < n; i++) {
            float u = n <= 1 ? 0.35f : i / (float) (n - 1);
            final int c = tom(cor, u);
            MutableComponent ch = Component.literal(String.valueOf(texto.charAt(i)))
                    .withStyle(st -> {
                        Style s2 = st.withColor(TextColor.fromRgb(c));
                        if (negrito) {
                            s2 = s2.withBold(true);
                        }
                        if (italico) {
                            s2 = s2.withItalic(true);
                        }
                        return s2;
                    });
            out.append(ch);
        }
        return out;
    }

    /** A cor de uma letra do gradiente: começa no brilho do metal, termina na sombra. */
    static int tom(int cor, float u) {
        float lum = luminancia(cor);
        if (lum < 0.30f) {
            // cor escura não tem como "clarear por multiplicação": mistura com branco
            float branco = 0.68f * (1f - 0.88f * u);
            return misturar(cor, 0xFFFFFF, branco);
        }
        float k = 1.55f - 1.02f * u;
        int r = limitar(Math.round(((cor >> 16) & 255) * k));
        int g = limitar(Math.round(((cor >> 8) & 255) * k));
        int b = limitar(Math.round((cor & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    static float luminancia(int cor) {
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    static int misturar(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (limitar(r) << 16) | (limitar(g) << 8) | limitar(bl);
    }

    static int escurecer(int cor, float f) {
        return misturar(cor, 0x000000, 1f - f);
    }

    private static int limitar(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
