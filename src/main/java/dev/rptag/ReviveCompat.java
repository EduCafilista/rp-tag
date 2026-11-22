package dev.rptag;

import net.neoforged.fml.ModList;

/**
 * (5.78.3) COMPATIBILIDADE COM MODS DE REVIVE — pedido do dono:
 * <i>"o mod ta fazendo o mod revive nao funcionar"</i>.
 *
 * <h2>O que acontecia</h2>
 * O RP Tag troca a {@link net.minecraft.client.gui.screens.DeathScreen} do vanilla pela tela dele
 * (aquela que escurece e deixa só os botões — pedido 5.25.1). Só que o <b>PlayerRevive</b> vive
 * DENTRO da tela de morte: é ela que mostra o recado de "te levantam em 60 s" e é nela que o
 * amigo clica para reviver. Trocando a tela, o revive morria junto — o jogador não via mensagem
 * nenhuma e ninguém conseguia levantar ninguém.
 *
 * <h2>O que fazemos agora</h2>
 * Se existe um mod de revive instalado, o RP Tag <b>não encosta mais na tela de morte</b> (o
 * vanilla + o mod de revive cuidam dela) e também não manda o recado de morte para o cliente.
 * Sem mod de revive, a tela bonita do RP Tag continua exatamente como era.
 *
 * <p>Vale nos dois lados: no servidor evita mandar o pacote, no cliente evita a troca de tela.
 */
public final class ReviveCompat {

    /** Ids de mods que mexem em morte/revive e dependem da tela de morte original. */
    private static final String[] MODS_DE_REVIVE = {
            "playerrevive",      // CreativeMD — o mais usado (60 s para ser levantado)
            "hardcorerevival",   // Hardcore Revival
            "incapacitated",     // Incapacitated
            "down_but_not_out",  // Down But Not Out
            "reviveme",
            "revive",
            "corpse",            // Corpse / Corpse Complex (corpo no chão)
            "corpses",
            "gravestone",
            "deathbackpack",
    };

    private static Boolean cache;

    /** @return true se algum mod de revive/corpo está instalado. */
    public static boolean temRevive() {
        if (cache != null) {
            return cache;
        }
        boolean achou = false;
        try {
            ModList lista = ModList.get();
            if (lista != null) {
                for (String id : MODS_DE_REVIVE) {
                    try {
                        if (lista.isLoaded(id)) {
                            achou = true;
                            RPTagMod.LOGGER.info(
                                    "RP Tag: mod de revive detectado ({}). A tela de morte do RP Tag "
                                            + "fica desligada para o revive funcionar.", id);
                            break;
                        }
                    } catch (Throwable ignorado) {
                        // id invalido em alguma versao: segue
                    }
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui verificar mods de revive (segue com a tela do mod)", t);
        }
        cache = achou;
        return achou;
    }

    /**
     * @return true se o RP Tag pode usar a PRÓPRIA tela de morte.
     *         Falso quando existe mod de revive ou quando o dono desligou na config.
     */
    public static boolean podeUsarTelaPropria() {
        if (temRevive() && RpConfig.descartarTelaComRevive()) {
            return false;
        }
        try {
            return RpConfig.TELA_DE_MORTE.get();
        } catch (Throwable t) {
            return !temRevive();        // sem config carregada (servidor): decide pelo mod
        }
    }

    private ReviveCompat() {
    }
}
