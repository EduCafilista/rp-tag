package dev.rptag;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * (5.78.3) Configuração do RP Tag (arquivo {@code config/rptag-client.toml}).
 *
 * <p>Existe por causa de UM problema: a tela de morte própria do mod. Ela é linda e o dono pediu
 * (5.25.1), mas ela engolia o <b>PlayerRevive</b>, que precisa da tela de morte original para
 * mostrar o "te levantam em 60 s". Agora dá para desligar sem compilar nada.
 */
public final class RpConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TELA_DE_MORTE;
    public static final ModConfigSpec.BooleanValue TELA_OFF_COM_REVIVE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Tela de morte do RP Tag (a que escurece e deixa so os botoes).",
                "true = usa a tela do mod | false = deixa a tela de morte do vanilla")
                .push("tela_de_morte");
        TELA_DE_MORTE = b.comment("Usar a tela de morte propria do RP Tag.")
                .define("usar_tela_propria", true);
        TELA_OFF_COM_REVIVE = b.comment(
                        "Desligar a tela do mod automaticamente quando existir mod de revive",
                        "(PlayerRevive, Hardcore Revival...). Deixe true: o revive depende da tela original.")
                .define("desligar_com_mod_de_revive", true);
        b.pop();
        SPEC = b.build();
    }

    static boolean descartarTelaComRevive() {
        try {
            return TELA_OFF_COM_REVIVE.get();
        } catch (Throwable t) {
            return true;
        }
    }

    private RpConfig() {
    }
}
