package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.UUID;

/**
 * (4.8.0) /ficha — o tablet em forma de comando.
 *
 * <pre>
 * /ficha                    abre a sua ficha (o tablet)
 * /ficha ver                mostra no chat
 * /ficha ver &lt;jogador&gt;      ficha de outra pessoa (tela, só leitura)
 * /ficha livro &lt;jogador&gt;    ficha de outra pessoa no chat
 * /ficha dar &lt;jogador&gt;      (admin) entrega o tablet
 * /ficha reset &lt;jogador&gt;    (admin) apaga a ficha para refazer
 * /ficha lista              (admin) quem já tem ficha
 * </pre>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class FichaCommands {

    private FichaCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        var root = Commands.literal("ficha");

        root.executes(ctx -> {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            FichaServer.abrir(sp);
            return Command.SINGLE_SUCCESS;
        });

        root.then(Commands.literal("ver").executes(ctx -> {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            FichaLivro.mostrar(sp, sp.getGameProfile().getName(), RPWorldData.get(sp.server).getFicha(sp.getUUID()));
            return Command.SINGLE_SUCCESS;
        }).then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
            FichaServer.ver(sp, alvo);
            return Command.SINGLE_SUCCESS;
        })));

        root.then(Commands.literal("livro")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    FichaLivro.mostrar(sp, alvo.getGameProfile().getName(),
                            RPWorldData.get(sp.server).getFicha(alvo.getUUID()));
                    return Command.SINGLE_SUCCESS;
                })));

        root.then(Commands.literal("dar")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    FichaServer.dar(alvo, true);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Tablet entregue para " + alvo.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), false);
                    return Command.SINGLE_SUCCESS;
                })));

        root.then(Commands.literal("reset")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    FichaServer.resetar(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Ficha de " + alvo.getGameProfile().getName() + " apagada.").withStyle(ChatFormatting.GREEN), false);
                    return Command.SINGLE_SUCCESS;
                })));

        root.then(Commands.literal("lista")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    Map<UUID, FichaRp> fichas = RPWorldData.get(server).getFichas();
                    if (fichas.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Ninguém criou ficha ainda.")
                                .withStyle(ChatFormatting.GRAY), false);
                        return Command.SINGLE_SUCCESS;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("── Fichas (" + fichas.size() + ") ──")
                            .withStyle(ChatFormatting.DARK_AQUA), false);
                    fichas.forEach((id, f) -> {
                        var pl = server.getPlayerList().getPlayer(id);
                        String nick = pl != null ? pl.getGameProfile().getName() : id.toString().substring(0, 8);
                        ctx.getSource().sendSuccess(() -> Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.literal(nick).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(" → " + f.ou(f.nome)).withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(f.concluida ? " ✔" : " …").withStyle(
                                        f.concluida ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), false);
                    });
                    return Command.SINGLE_SUCCESS;
                }));

        // atalho para quem já usou /persona antes
        root.then(Commands.literal("editar").executes(ctx -> {
            ServerPlayer sp = ctx.getSource().getPlayerOrException();
            FichaServer.abrir(sp);
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }
}
