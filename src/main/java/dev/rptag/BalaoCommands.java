package dev.rptag;

import com.mojang.brigadier.Command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Comandos do balao de fala — SIMPLES de proposito: toda a personalizacao
 * (cores, fundo, emojis) fica no editor em INVENTARIO, que e facil e divertido.
 *
 * <ul>
 *   <li>{@code /balaoedit} — abre o EDITOR (bau de 6 fileiras)</li>
 *   <li>{@code /balao} — tambem abre o editor</li>
 *   <li>{@code /balao modo on|off} — MODO BALAO: suas falas viram so balao</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class BalaoCommands {

    private BalaoCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("balao");

        // /balao — abre o editor em inventario
        root.executes(ctx -> {
            BalaoEditMenu.open(ctx.getSource().getPlayerOrException());
            return Command.SINGLE_SUCCESS;
        });

        // /balao modo on|off
        root.then(Commands.literal("modo")
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleMode(player.getUUID(), true);
                    player.sendSystemMessage(Component.literal(
                            "Modo balão LIGADO! Suas falas aparecem SÓ no balão sobre a sua cabeça.")
                            .withStyle(ChatFormatting.GREEN));
                    player.sendSystemMessage(Component.literal("Dica: personalize com /balaoedit")
                            .withStyle(ChatFormatting.GRAY));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleMode(player.getUUID(), false);
                    player.sendSystemMessage(Component.literal("Modo balão DESLIGADO. Suas falas voltaram ao chat normal.")
                            .withStyle(ChatFormatting.GRAY));
                    return Command.SINGLE_SUCCESS;
                })));

        event.getDispatcher().register(root);

        // /balaoedit — atalho direto pro editor
        event.getDispatcher().register(Commands.literal("balaoedit").executes(ctx -> {
            BalaoEditMenu.open(ctx.getSource().getPlayerOrException());
            return Command.SINGLE_SUCCESS;
        }));
    }
}
