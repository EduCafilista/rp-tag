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

    /**
     * (5.64.1) O RELOGIO DA AURA ANTIGA SAIU DAQUI.
     *
     * <p>Ele guardava um {@code pulso} que subia pra 1.0 a cada poder e <b>nunca descia</b> — com
     * isso a aura preta ficava acesa pra sempre, mesmo depois de desligar (o dono viu isso:
     * <i>"eu desliguei e continuou a aura negra"</i>). Quem decide se a aura aparece agora e o
     * anexo do jogador ({@code /iluminacao}, {@code /iluminacao vazio}, {@code /iluminacao off}).
     *
     * <p>Os dois metodos ficam aqui so pra quem ja chamava eles continuar compilando.
     */
    @Deprecated
    public static void acenderPor(long ms) {
    }

    /** @deprecated a aura nao acende mais sozinha depois de um poder — ver {@link #acenderPor(long)}. */
    @Deprecated
    public static void pulsar() {
    }
}
