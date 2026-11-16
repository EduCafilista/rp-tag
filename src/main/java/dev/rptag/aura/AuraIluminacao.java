package dev.rptag.aura;

import net.neoforged.bus.api.IEventBus;

/**
 * (5.60.0) A AURA DE ILUMINACAO — entrada do pacote no mod.
 *
 * <p>Pedido do dono: <i>"vamos adicionar isso como uma nova aura, /iluminacao, aonde ele vai ter
 * poder para ser usado"</i>. A aura e o circulo magico dourado (anel de runas + hexagrama), a
 * coluna de luz, as helices, os aneis que sobem, os raios e os PORTES DA BABILONIA girando em
 * volta do corpo — tudo procedural, sem textura nenhuma.
 *
 * <p>O PODER: os portoes disparam armas douradas para onde o jogador mira. Tecla G (mudavel em
 * Controles) ou {@code /iluminacao portoes}. So funciona com a aura LIGADA.
 */
public final class AuraIluminacao {
    private AuraIluminacao() {}

    /** Chame no construtor do mod: pendura o anexo do jogador e a entidade da arma. */
    public static void init(IEventBus modBus) {
        AuraAnexos.TIPOS.register(modBus);
        AuraEntidades.ENTIDADES.register(modBus);
    }
}
