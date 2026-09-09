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
 * {@code /balao} — o balao de fala do jogador.
 *
 * <ul>
 *   <li>{@code /balao} — abre a TELA de personalizacao (cliente com o mod)</li>
 *   <li>{@code /balao modo on|off} — MODO BALAO: suas falas viram so balao,
 *       sem aparecer no chat (para criancas, ovos e personagens sem voz)</li>
 *   <li>{@code /balao cor <cor>} — cor da mensagem e do fundo</li>
 *   <li>{@code /balao emoji <antes|depois> <emojis>} — decoracao</li>
 *   <li>{@code /balao fundo <estilo>} — translucido/escuro/claro/gradiente/papel/noite/madeira</li>
 *   <li>{@code /balao limpar} — volta ao padrao</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class BalaoCommands {

    private BalaoCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("balao");

        // /balao — abre a tela (S2C; quem nao tem o mod no cliente ve a dica)
        root.executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            RPWorldData data = RPWorldData.get(player.server);
            PacketDistributor.sendToPlayer(player,
                    OpenBubbleStylePayload.of(data.getStyle(player.getUUID())));
            return Command.SINGLE_SUCCESS;
        });

        // /balao modo on|off
        root.then(Commands.literal("modo")
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleMode(player.getUUID(), true);
                    player.sendSystemMessage(Component.literal(
                            "Modo balao LIGADO! Suas falas aparecem SO no balao sobre a sua cabeca (quem estiver perto ve).")
                            .withStyle(ChatFormatting.GREEN));
                    player.sendSystemMessage(Component.literal("Dica: personalize com /balao (tela) ou /balao cor <cor>.")
                            .withStyle(ChatFormatting.GRAY));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleMode(player.getUUID(), false);
                    player.sendSystemMessage(Component.literal("Modo balao DESLIGADO. Suas falas voltaram ao chat normal.")
                            .withStyle(ChatFormatting.GRAY));
                    return Command.SINGLE_SUCCESS;
                })));

        // /balao cor <cor>
        root.then(Commands.literal("cor")
                .then(Commands.argument("cor", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String input = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "cor");
                            int rgb;
                            if (input.equalsIgnoreCase("off") || input.equalsIgnoreCase("padrao")) {
                                rgb = RPWorldData.DEFAULT_COLOR;
                            } else {
                                rgb = BubbleCommands.parseColor(input);
                                if (rgb < 0) {
                                    player.sendSystemMessage(Component.literal(
                                            "Cor invalida! Use nome (ciano), nome do Minecraft (dark_aqua) ou #RRGGBB.")
                                            .withStyle(ChatFormatting.RED));
                                    return 0;
                                }
                            }
                            RPWorldData data = RPWorldData.get(player.server);
                            BubbleStyle old = data.getStyle(player.getUUID());
                            data.setStyle(player.getUUID(), new BubbleStyle(rgb, old.prefix(), old.suffix(), old.background()));
                            player.sendSystemMessage(Component.literal("Cor do balao definida: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.format("#%06X", rgb)).withStyle(s -> s
                                            .withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)))));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /balao emoji <antes|depois> <emojis>
        root.then(Commands.literal("emoji")
                .then(Commands.literal("antes")
                        .then(Commands.argument("emojis", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> changeEmoji(ctx.getSource().getPlayerOrException(), true,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "emojis"))))))
                .then(Commands.literal("depois")
                        .then(Commands.argument("emojis", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> changeEmoji(ctx.getSource().getPlayerOrException(), false,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "emojis")))));

        // /balao fundo <estilo>
        root.then(Commands.literal("fundo")
                .then(Commands.argument("estilo", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String estilo = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "estilo")
                                    .toLowerCase();
                            int bg = switch (estilo) {
                                case "translucido", "padrao" -> 0;
                                case "escuro" -> 1;
                                case "claro" -> 2;
                                case "gradiente" -> 3;
                                case "papel" -> 4;
                                case "noite" -> 5;
                                case "madeira" -> 6;
                                default -> -1;
                            };
                            if (bg < 0) {
                                player.sendSystemMessage(Component.literal(
                                        "Fundos: translucido, escuro, claro, gradiente, papel, noite, madeira")
                                        .withStyle(ChatFormatting.GRAY));
                                return 0;
                            }
                            RPWorldData data = RPWorldData.get(player.server);
                            BubbleStyle old = data.getStyle(player.getUUID());
                            data.setStyle(player.getUUID(), new BubbleStyle(old.colorRGB(), old.prefix(), old.suffix(), bg));
                            player.sendSystemMessage(Component.literal("Fundo do balao: " + estilo)
                                    .withStyle(ChatFormatting.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /balao limpar
        root.then(Commands.literal("limpar").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            RPWorldData data = RPWorldData.get(player.server);
            data.setStyle(player.getUUID(), new BubbleStyle(RPWorldData.DEFAULT_COLOR, "", "", 0));
            player.sendSystemMessage(Component.literal("Balao voltou ao padrao.").withStyle(ChatFormatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }

    private static int changeEmoji(ServerPlayer player, boolean before, String emojis) {
        String value = emojis.trim();
        if (value.length() > BubbleStyle.MAX_DECOR) {
            player.sendSystemMessage(Component.literal("Maximo de " + BubbleStyle.MAX_DECOR + " caracteres de decoracao.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        RPWorldData data = RPWorldData.get(player.server);
        BubbleStyle old = data.getStyle(player.getUUID());
        data.setStyle(player.getUUID(), before
                ? new BubbleStyle(old.colorRGB(), value, old.suffix(), old.background())
                : new BubbleStyle(old.colorRGB(), old.prefix(), value, old.background()));
        player.sendSystemMessage(Component.literal("Decoracao " + (before ? "antes" : "depois") + ": " + value)
                .withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
