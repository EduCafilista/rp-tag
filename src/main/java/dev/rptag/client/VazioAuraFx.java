package dev.rptag.client;

import net.minecraft.world.entity.player.Player;

/**
 * (5.29.0) A AURA DO VAZIO — QUANDO ELA APARECE.
 *
 * <p><b>A regra sempre foi a do {@code /vazio ligar}:</b> quem e o vazio no mundo ganha a aura
 * (o proprio mod ja avisa "Aura de RAIOS AMARELOS E NEGROS saindo do corpo"), e o
 * {@code /vazio desligar} apaga tudo. O cliente ja sabe quem e esse jogador pelo pacote de aura
 * ({@link RaiosVazioFx#alvoUuid()}), reenviado de tempos em tempos — entao aqui so perguntamos
 * pra ele, sem guardar relogio nenhum.
 *
 * <p>(5.66.0) O que tinha antes era um {@code pulso} que subia pra 1.0 a cada poder e nunca
 * descia: a aura preta ficava presa mesmo depois de desligar. Isso saiu. Nada de comando novo
 * (o {@code /iluminacao vazio} da 5.65.0 foi um engano meu e ja foi removido).
 */
public final class VazioAuraFx {

    private VazioAuraFx() {
    }

    /**
     * O jogador esta com a aura do VAZIO acesa?
     *
     * <p>Sim quando ele e o vazio ligado por {@code /vazio ligar} — e na hora que o dono roda
     * {@code /vazio desligar} o servidor manda {@code Aura.desligada()}, {@link RaiosVazioFx}
     * limpa o alvo e isto vira {@code false}: casca, tentaculos, mancha e fumaca somem juntos.
     */
    public static boolean ativa(Player p) {
        if (p == null) return false;
        try {
            return RaiosVazioFx.ligado() && p.getUUID().equals(RaiosVazioFx.alvoUuid());
        } catch (RuntimeException erro) {
            return false;      // regra de ouro: erro aqui nao pode derrubar o render
        }
    }

    /** (5.29.0) mantidos pra quem ja chamava — a aura nao acende mais por conta propria. */
    @Deprecated
    public static void acenderPor(long ms) {
    }

    /** @deprecated quem acende e apaga a aura e o {@code /vazio ligar} e o {@code /vazio desligar}. */
    @Deprecated
    public static void pulsar() {
    }
}
