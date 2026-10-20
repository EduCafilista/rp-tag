package dev.rptag;

/**
 * (4.4.0) A CONTA das vidas canônicas, isolada do Minecraft de propósito:
 * é a mesma regra usada por {@code /rp admin vida}, pelo coração canônico e
 * pelo ritual de morte. Ficar numa classe pura permite testar com JUnit
 * (sem servidor), que é como este mod se protege de regressão.
 *
 * <p>Regras:
 * <ul>
 *   <li>teto {@value #TETO} vidas por jogador;</li>
 *   <li>nunca abaixo de {@code 0} (zero = sem vidas, o próximo ritual é o último);</li>
 *   <li>quantidade menor que 1 conta como 1 (não existe "tirar 0").</li>
 * </ul>
 */
public final class VidaCanonica {

    /** máximo de vidas canônicas por jogador. */
    public static final int TETO = 5;

    private VidaCanonica() {
    }

    /** quantas vidas ficam depois de TIRAR {@code qtd} (usado pelo /rp admin vida ... tirar). */
    public static int tirar(int antes, int qtd) {
        return Math.max(0, antes - Math.max(1, qtd));
    }

    /** quantas vidas ficam depois de DAR {@code qtd}, respeitando o teto. */
    public static int dar(int antes, int qtd) {
        return Math.min(TETO, antes + Math.max(1, qtd));
    }

    /** valor de "definir" já preso entre 0 e o teto. */
    public static int definir(int valor) {
        return Math.max(0, Math.min(TETO, valor));
    }

    /** texto curto de status ("3/5"). */
    public static String mostrar(int vidas) {
        return vidas + "/" + TETO;
    }
}
