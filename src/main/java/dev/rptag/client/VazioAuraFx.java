package dev.rptag.client;

import net.minecraft.world.entity.player.Player;

/**
 * (5.29.0) <b>A AURA DO VAZIO — so o ESTADO.</b>
 *
 * <p>Historia: na 5.29.0 ela desenhava petalas/penas carmesim em camadas em volta do corpo. Na
 * 5.34.0 o dono pediu pra tirar aquele desenho ("era para tirar ISSO que está agora") e ele ficou
 * desligado — mas o codigo morto continuou aqui (as penas, o anel, a poeira).
 *
 * <p><b>5.64.0 — A ANIMACAO NOVA ENTROU NO LUGAR:</b> o dono mandou o zip novo e disse
 * <i>"substitua o vazio atual, por essa animacao de vazio ai"</i>. Quem desenha agora e a
 * {@code dev.rptag.aura.client.AuraShellLayer} (casca de tinta preta colada na silhueta) +
 * {@code AuraFx} (tentaculos, mancha no chao e fumaca de tinta). <b>O desenho antigo foi apagado
 * de vez</b> — esta classe guarda so o estado (quando a aura esta acesa) pra quem ja chamava
 * {@link #pulsar()} e {@link #acenderPor(long)} nao quebrar.
 */
public final class VazioAuraFx {

    private VazioAuraFx() {
    }

    /** pulsos curtos (0..1) que o raio/orbe deixam na aura. */
    private static float pulso;

    /** (5.31.0) quando foi o ultimo poder (a aura fica acesa depois dele). */
    private static long ultimoPoder;

    /** (5.32.0) quanto tempo (ms) a aura fica acesa depois do ultimo gatilho. */
    private static long janela = 14000L;

    /** (5.32.0) acende a aura por um tempo maior (usado pelo modo deus do vazio). */
    public static void acenderPor(long ms) {
        janela = Math.max(14000L, ms);
        pulso = 1.0F;
        ultimoPoder = System.currentTimeMillis();
    }

    /** chamado pelo FX dos poderes quando o jogador solta algo. */
    public static void pulsar() {
        pulso = 1.0F;
        ultimoPoder = System.currentTimeMillis();
    }

    /** (5.32.0) a forca da aura: 1,0 logo depois do poder, apagando ate 0 na janela. */
    private static float desde(long ms) {
        if (ms < 0) {
            return 0.0F;
        }
        float f = 1.0F - ms / (float) janela;
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    /**
     * (5.64.0) A AURA ESTA ACESA NESTE JOGADOR?
     *
     * <p>Regra que ja existia: ela acende por alguns segundos depois de um poder (quem chama
     * {@link #pulsar()} / {@link #acenderPor(long)}). E o que a casca de tinta usa pra saber se
     * desenha.
     */
    public static boolean ativa(Player p) {
        if (p == null) return false;
        return pulso > 0.0F || (System.currentTimeMillis() - ultimoPoder) < janela;
    }

    /** (5.64.0) forca atual (0..1) — usada pra intensidade da casca de tinta. */
    public static float forca() {
        long desde = System.currentTimeMillis() - ultimoPoder;
        return 0.35F + 0.65F * desde(desde);
    }
}
