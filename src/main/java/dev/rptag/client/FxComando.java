package dev.rptag.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * (4.2.0) /rpfx status|on|off (comando do CLIENTE) + carga da config do cliente.
 * (4.7.4) /rpfx teste <estilo> [texto] — dispara o FX localmente, SEM servidor:
 * serve para separar "o FX nao desenha" de "o comando nao chega".
 */
public final class FxComando {
    private FxComando() {}

    /** Carrega config/rptag/cliente.properties na primeira vez. */
    public static void garantirConfig() {
        if (FxConfig.carregado()) return;
        FxConfig.carregar(Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("rptag").resolve("cliente.properties"));
        logarAviso();
    }

    private static void logarAviso() {
        String a = FxConfig.consumirAviso();
        if (a != null) RPTagMod.LOGGER.warn("RP Tag FX: {}", a);
    }

    static String status() {
        return "RP Tag FX: video " + (FxConfig.video() ? "LIGADO" : "DESLIGADO")
                + " — " + VideoFx.contarVideos() + " aviso(s) com video";
    }

    public static void registrarComando(RegisterClientCommandsEvent event) {
        garantirConfig();
        event.getDispatcher().register(Commands.literal("rpfx")
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(status()), false);
                    return 1;
                }))
                .then(Commands.literal("on").executes(ctx -> {
                    FxConfig.definirVideo(true);
                    logarAviso();
                    ctx.getSource().sendSuccess(() -> Component.literal("RP Tag FX: avisos em VIDEO ligados."), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    FxConfig.definirVideo(false);
                    logarAviso();
                    ctx.getSource().sendSuccess(() -> Component.literal("RP Tag FX: video desligado (FX procedural)."), false);
                    return 1;
                }))
                // (4.7.4) teste do FX sem passar pelo servidor
                .then(Commands.literal("teste")
                        .then(Commands.argument("estilo", StringArgumentType.word())
                                .executes(ctx -> teste(ctx, StringArgumentType.getString(ctx, "estilo"), null))
                                .then(Commands.argument("texto", StringArgumentType.greedyString())
                                        .executes(ctx -> teste(ctx, StringArgumentType.getString(ctx, "estilo"),
                                                StringArgumentType.getString(ctx, "texto")))))));
    }

    /** (4.7.4) nome -> numero do estilo (o MESMO switch do avisar()). */
    static int estilo(String nome) {
        if (nome == null) return 0;
        return switch (nome.toLowerCase(java.util.Locale.ROOT)) {
            case "pesadelo" -> 1;
            case "runico" -> 2;
            case "real" -> 3;
            case "morte" -> 4;
            case "inferno" -> 5;
            case "gelo" -> 6;
            case "divino" -> 7;
            case "void" -> 8;
            case "sangue" -> 9;
            case "demoniaco" -> 10;
            case "pesadelo2" -> 11;
            case "egipicio", "egipcio" -> 12;
            case "expurgo" -> 13;
            default -> 0;
        };
    }

    /**
     * (4.7.4) toca o aviso direto no cliente, sem servidor e sem comando de admin.
     * Se aqui aparece e no /rp admin avisar nao, o problema esta no comando/rede,
     * nao no desenho.
     */
    private static int teste(CommandContext<CommandSourceStack> ctx, String nome, String texto) {
        int est = estilo(nome);
        String msg = (texto == null || texto.isBlank())
                ? "TESTE " + nome.toUpperCase(java.util.Locale.ROOT) : texto;
        ClientEffects.startScreenText(msg, 8, est);
        RPTagMod.LOGGER.info("RP Tag FX: TESTE local do estilo {} ({})", est, nome);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "RP Tag FX: teste do estilo " + est + " (" + nome + "). "
                        + "Se aparecer na tela, o desenho esta OK e o problema esta no comando."),
                false);
        return 1;
    }
}
