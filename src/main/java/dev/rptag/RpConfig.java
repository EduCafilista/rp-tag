package dev.rptag;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * (5.78.3 / 5.78.16) Configuração do RP Tag (arquivo {@code config/rptag-client.toml}).
 *
 * <p>Inclui tanto a compatibilidade da tela de morte com mods de revive quanto a configuração de
 * peso/qualidade dos efeitos visuais 3D ({@code efeitos.qualidade}) para o modpack/players.
 */
public final class RpConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TELA_DE_MORTE;
    public static final ModConfigSpec.BooleanValue TELA_OFF_COM_REVIVE;
    public static final ModConfigSpec.ConfigValue<String> QUALIDADE_EFEITOS;

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

        b.comment("Qualidade e peso dos efeitos visuais 3D (auras, cascas, aneis, poderes).",
                "Opcoes: auto | alto | medio | baixo | minimo | desligado",
                "Padrao 'auto' ja comeca leve (medio) e reduz sozinho se o FPS cair.")
                .push("efeitos");
        QUALIDADE_EFEITOS = b.comment(
                        "Qualidade dos efeitos do RP Tag (tambem pode trocar no jogo com /rpfx qualidade <nivel>).")
                .define("qualidade", "auto");
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

    public static String qualidadeEfeitos() {
        try {
            return QUALIDADE_EFEITOS.get();
        } catch (Throwable t) {
            return "auto";
        }
    }

    public static void salvarQualidadeEfeitos(String valor) {
        try {
            QUALIDADE_EFEITOS.set(valor);
            QUALIDADE_EFEITOS.save();
        } catch (Throwable ignorado) {
        }
    }

    private RpConfig() {
    }
}
