package dev.rptag;

import com.mojang.brigadier.Command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Comandos do balao de fala — SIMPLES de proposito: toda a personalizacao
 * (cores com seletor, barra, emojis, fundo) fica na TELA de personalizacao,
 * que abre com {@code /balao}. Um comando so, nada de decorar texto.
 *
 * <ul>
 *   <li>{@code /balao} — abre a tela de personalizacao</li>
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

        // /balao — abre a tela de personalizacao
        root.executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            RPWorldData data = RPWorldData.get(player.server);
            if (!data.isBubbleAllowed(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "Um admin desligou o seu balao de fala. Peça a um admin: /rp admin balao <voce> on")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return Command.SINGLE_SUCCESS;
            }
            PacketDistributor.sendToPlayer(player,
                    OpenBubbleStylePayload.of(data.getStyle(player.getUUID()),
                            data.isBubbleMode(player.getUUID())));
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
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleMode(player.getUUID(), false);
                    player.sendSystemMessage(Component.literal("Modo balão DESLIGADO. Suas falas voltaram ao chat normal.")
                            .withStyle(ChatFormatting.GRAY));
                    return Command.SINGLE_SUCCESS;
                })));

        // (3.65.0) /balao typewriter [on|off] — a fala se escreve sozinha?
        root.then(Commands.literal("typewriter")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean novo = !RPWorldData.get(player.server).isTypewriter(player.getUUID());
                    return alternarTypewriter(player, novo);
                })
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return alternarTypewriter(player, true);
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return alternarTypewriter(player, false);
                })));

        event.getDispatcher().register(root);
    }

    /** (3.65.0) liga/desliga o typewriter do balao do jogador (persistente). */
    private static int alternarTypewriter(ServerPlayer player, boolean on) {
        RPWorldData.get(player.server).setTypewriter(player.getUUID(), on);
        PacketDistributor.sendToPlayer(player, new TypewriterPayload(on));
        player.sendSystemMessage(Component.literal(on
                ? "\u270e Digitacao automatica LIGADA: suas falas se escrevem sozinhas no balao."
                : "\u270e Digitacao automatica DESLIGADA: suas falas aparecem inteiras na hora.")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return Command.SINGLE_SUCCESS;
    }
}
