package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** (4.2.0) /rpfx status|on|off (comando do CLIENTE) + carga da config do cliente. */
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
                })));
    }
}
