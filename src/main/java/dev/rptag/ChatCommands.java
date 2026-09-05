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
 * Canais de chat RP:
 *
 * <ul>
 *   <li>{@code /g <msg>} — global (servidor inteiro)</li>
 *   <li>{@code /s <msg>} — grito (100 blocos, CAIXA ALTA)</li>
 *   <li>{@code /w <msg>} — sussurro (5 blocos, cinza italico)</li>
 *   <li>{@code /me <acao>} — acao do personagem (* Alex caminha...)</li>
 *   <li>{@code /do <descricao>} — descricao de ambiente (* a porta range...)</li>
 * </ul>
 *
 * <p>O chat vanilla (sem comando) tambem e local — veja {@code ServerEvents}.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class ChatCommands {

    private ChatCommands() {
    }

    /**
     * Remove um comando vanilla da arvore (ex.: o /me do emote) para que a
     * nossa versao RP seja a unica. Usa reflexao no mapa interno do brigadier.
     */
    private static void removeVanillaCommand(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String name) {
        try {
            java.lang.reflect.Field field = com.mojang.brigadier.tree.CommandNode.class.getDeclaredField("children");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> children = (java.util.Map<String, Object>) field.get(dispatcher.getRoot());
            children.remove(name.toLowerCase(java.util.Locale.ROOT));
        } catch (ReflectiveOperationException ignored) {
            // se falhar, o /me vanilla permanece
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // o /me vanilla (emote global) perderia por ordem de registro; remove ele
        removeVanillaCommand(event.getDispatcher(), "me");

        // /g <msg> — global
        event.getDispatcher().register(Commands.literal("g")
                .then(Commands.argument("msg", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "msg");
                            RPChat.sendGlobal(player.server, Component.empty()
                                    .append(Component.literal("<"))
                                    .append(RPChat.displayName(player))
                                    .append(Component.literal("> "))
                                    .append(Component.literal(msg))
                                    .append(Component.literal(" [global]").withStyle(ChatFormatting.DARK_GRAY)));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /s <msg> — grito
        event.getDispatcher().register(Commands.literal("s")
                .then(Commands.argument("msg", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "msg");
                            RPChat.sendLocal(player, RPWorldData.SHOUT_RANGE, Component.empty()
                                    .append(Component.literal("<"))
                                    .append(RPChat.displayName(player))
                                    .append(Component.literal("> grita: ").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal(msg.toUpperCase()).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /w <msg> — sussurro
        event.getDispatcher().register(Commands.literal("w")
                .then(Commands.argument("msg", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String msg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "msg");
                            RPChat.sendLocal(player, RPWorldData.WHISPER_RANGE, Component.empty()
                                    .append(RPChat.displayName(player))
                                    .append(Component.literal(" sussurra: ").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                                    .append(Component.literal(msg).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /me <acao>
        event.getDispatcher().register(Commands.literal("me")
                .then(Commands.argument("acao", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String acao = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "acao");
                            RPChat.sendLocal(player, RPWorldData.LOCAL_RANGE, Component.empty()
                                    .append(Component.literal("✦ ").withStyle(ChatFormatting.DARK_PURPLE))
                                    .append(RPChat.displayName(player).withStyle(ChatFormatting.ITALIC))
                                    .append(Component.literal(" " + acao).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC)));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /do <descricao de ambiente>
        event.getDispatcher().register(Commands.literal("do")
                .then(Commands.argument("descricao", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String desc = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "descricao");
                            RPChat.sendLocal(player, RPWorldData.LOCAL_RANGE, Component.empty()
                                    .append(Component.literal("✦ ").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal(desc).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
