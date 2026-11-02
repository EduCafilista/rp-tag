package dev.rptag;

/**
 * (5.13.0) A PLANTA DO VAZIO — só números, compartilhada entre servidor e cliente.
 *
 * <p>Fica numa classe sem dependência de Minecraft de propósito: o servidor usa para construir os
 * pedestais/santuários/portal e o <b>cliente</b> usa para desenhar as marcas de luz e o portal negro
 * sem precisar de pacote a cada frame. Se mudar um número aqui, os dois lados mudam junto.
 *
 * <h2>Pedido 50</h2>
 * <i>"ter pedestais em volta do centro aonde spawna na dimensão, e assim que ele colocar os 8
 * fragmentos nos pedestais, abre um portal negro"</i> — então:
 * <ul>
 *   <li><b>8 pedestais</b> num círculo de {@link #RAIO_PEDESTAL} blocos em volta da praça central;</li>
 *   <li><b>8 santuários</b> espalhados entre {@link #SANTUARIO_MIN} e {@link #SANTUARIO_MAX} blocos
 *       (é onde o jogador acha o fragmento);</li>
 *   <li><b>o portal</b> fica em {@link #PORTAL} (logo ao norte da praça), 5 de largura por 7 de
 *       altura.</li>
 * </ul>
 */
public final class VazioGeometria {

    private VazioGeometria() {
    }

    /** quantos fragmentos o portal pede. */
    public static final int TOTAL = 8;

    /** distância (blocos) dos pedestais até o centro da praça. */
    public static final int RAIO_PEDESTAL = 13;

    /** raio mínimo/máximo (blocos) onde nascem os santuários com fragmento. */
    public static final int SANTUARIO_MIN = 55, SANTUARIO_MAX = 135;

    /** quantos santuários existem. */
    public static final int SANTUARIOS = 8;

    /** os 8 pedestais (x,z) em círculo — o índice é o bit da máscara do jogador. */
    public static final int[][] PED = {
            { 13, 0 }, { 9, 9 }, { 0, 13 }, { -9, 9 },
            { -13, 0 }, { -9, -9 }, { 0, -13 }, { 9, -9 }
    };

    /** os 8 santuários (x,z) — espalhados, longe da vista da praça. */
    public static final int[][] SANTUARIO = {
            { 96, -34 }, { 61, 88 }, { -22, 118 }, { -104, 62 },
            { -118, -46 }, { -55, -112 }, { 44, -121 }, { 128, 21 }
    };

    /** onde fica o portal negro (x,z) em relação ao centro da praça. */
    public static final int[] PORTAL = { 0, -22 };

    /** largura (eixo x) e altura (eixo y) do miolo do portal. */
    public static final int LARGURA_MIOLO = 5, ALTURA_MIOLO = 7;

    /** borda (blocos de moldura) em volta do miolo. */
    public static final int BORDA = 1;

    /** quanto tempo (ms) o portal leva para abrir de baixo para cima. */
    public static final long ABERTURA_MS = 6000L;

    /** posição (x,z) do pedestal {@code i} em relação ao centro. */
    public static int[] pedestal(int i) {
        return PED[Math.floorMod(i, TOTAL)];
    }

    /** posição (x,z) do santuário {@code i} em relação ao centro. */
    public static int[] santuario(int i) {
        return SANTUARIO[Math.floorMod(i, SANTUARIOS)];
    }
}
