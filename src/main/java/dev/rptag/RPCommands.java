package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Comando /rp — disponivel para todos os jogadores.
 *
 * <ul>
 *   <li>{@code /rp} — alterna entre RP e OFF RP</li>
 *   <li>{@code /rp on} | {@code /rp off} — define o estado</li>
 *   <li>{@code /rp status} — mostra o estado atual</li>
 *   <li>{@code /rp set <jogador> on|off} — so para admins (permissao 2)</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class RPCommands {

    private RPCommands() {
    }

    /** (3.44.0) Traduz codigos & (0-9, a-f, k-o, r; && = &) p/ section codes. */
    private static String translateAmp(String in) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '&' && i + 1 < in.length()) {
                char n = Character.toLowerCase(in.charAt(i + 1));
                if ("0123456789abcdefklmnor".indexOf(n) >= 0) {
                    sb.append('\u00a7').append(n);
                    i++;
                    continue;
                }
                if (n == '&') {
                    sb.append('&');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        String out = sb.toString();
        if (out.length() > 160) {
            out = out.substring(0, 157) + "...";
        }
        return out;
    }

    /** (3.44.0) Aplica o MEDO no alvo (tela treme + vinheta). */
    private static void medar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            ServerPlayer alvo, int seg) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo, new FearPayload(seg));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u5291 " + alvo.getName().getString() + " esta com MEDO por " + seg + "s!")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** (3.44.0) Mostra o texto GRANDE na tela do alvo (padrao: vermelho). */
    private static void avisar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            ServerPlayer alvo, String texto) {
        String traduzido = translateAmp(texto);
        if (traduzido.indexOf('\u00a7') < 0) {
            traduzido = "\u00a7c" + traduzido; // sem cor? VERMELHO (o classic "voce esta sendo observado")
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo,
                new ScreenTextPayload(traduzido, 6));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Aviso na tela de " + alvo.getName().getString() + "!")
                .withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root = Commands.literal("rp");

        // /rp — alterna
        root.executes(ctx -> {
            ServerEvents.toggle(ctx.getSource().getPlayerOrException());
            return Command.SINGLE_SUCCESS;
        });

        // /rp on
        root.then(Commands.literal("on").executes(ctx -> {
            ServerEvents.setState(ctx.getSource().getPlayerOrException(), true, true);
            return Command.SINGLE_SUCCESS;
        }));

        // /rp off
        root.then(Commands.literal("off").executes(ctx -> {
            ServerEvents.setState(ctx.getSource().getPlayerOrException(), false, true);
            return Command.SINGLE_SUCCESS;
        }));

        // /rp status
        root.then(Commands.literal("status").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            RPWorldData data = RPWorldData.get(player.server);
            boolean inRp = ServerEvents.isInRp(player);
            Persona persona = ServerEvents.getPersona(player);
            BubbleStyle st = data.getStyle(player.getUUID());
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Modo RP: ").withStyle(ChatFormatting.GRAY))
                    .append(RPTags.tag(inRp)));
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Persona: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(persona.isEmpty() ? "(nenhuma)" : persona.name())
                            .withStyle(ChatFormatting.AQUA)));
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Chat local: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(data.isChatLocal() ? "LIGADO (proximidade)" : "desligado (global)")
                            .withStyle(data.isChatLocal() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
            boolean lib = data.isBubbleAllowed(player.getUUID());
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Balao: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(lib ? "liberado" : "bloqueado pelo admin")
                            .withStyle(lib ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .append(Component.literal(" · modo ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(data.isBubbleMode(player.getUUID()) ? "LIGADO" : "off")
                            .withStyle(data.isBubbleMode(player.getUUID()) ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(String.format(" · cor #%06X · moldura %d",
                            st.colorRGB() & 0xFFFFFF, st.borderStyle())).withStyle(ChatFormatting.GRAY)));
            return Command.SINGLE_SUCCESS;
        }));

        // /rp admin chatlocal on|off — apenas admins (liga/desliga o chat local)
        root.then(Commands.literal("admin")
                .requires(src -> src.hasPermission(2))
                // (3.44.0) /rp admin medo <jogador> [segundos] — tela treme + bordas vermelhas
                .then(Commands.literal("medo")
                        .then(Commands.argument("jogador", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer alvo = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "jogador");
                                    medar(ctx, alvo, 5);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("segundos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 30))
                                        .executes(ctx -> {
                                            ServerPlayer alvo = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "jogador");
                                            int seg = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "segundos");
                                            medar(ctx, alvo, seg);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // (3.44.0) /rp admin avisar <jogador> <texto> — texto GRANDE na tela (cores com &)
                .then(Commands.literal("avisar")
                        .then(Commands.argument("jogador", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer alvo = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "jogador");
                                            String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                            avisar(ctx, alvo, texto);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("chatlocal")
                        .then(Commands.literal("on").executes(ctx -> {
                            RPWorldData.get(ctx.getSource().getServer()).setChatLocal(true);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Chat local LIGADO — o chat normal so aparece por perto (40 blocos). Use /g para global."), true);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            RPWorldData.get(ctx.getSource().getServer()).setChatLocal(false);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Chat local DESLIGADO — o chat normal e global de novo."), true);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("bolha")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.literal("on").executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                                    RPWorldData.get(ctx.getSource().getServer()).setBubbleMode(target.getUUID(), true);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Modo balao LIGADO para " + target.getName().getString()
                                                    + " (as falas aparecem so no balao, sem chat)."), true);
                                    target.sendSystemMessage(Component.literal(
                                            "Um admin ativou o MODO BALAO para voce: suas falas aparecem so no balao sobre a cabeca.")
                                            .withStyle(ChatFormatting.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                                    RPWorldData.get(ctx.getSource().getServer()).setBubbleMode(target.getUUID(), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Modo balao DESLIGADO para " + target.getName().getString() + "."), true);
                                    target.sendSystemMessage(Component.literal(
                                            "Um admin desativou o modo balao para voce: suas falas voltaram ao chat normal.")
                                            .withStyle(ChatFormatting.GRAY));
                                    return Command.SINGLE_SUCCESS;
                                }))))
                .then(Commands.literal("balao")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.literal("on").executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                                    RPWorldData.get(ctx.getSource().getServer())
                                            .setBubbleAllowed(target.getUUID(), true);
                                    RPWorldData.get(ctx.getSource().getServer())
                                            .setBubbleMode(target.getUUID(), true);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            target.getName().getString() + " agora PODE usar balao de fala."), true);
                                    target.sendSystemMessage(Component.literal(
                                            "Um admin LIGOU o seu balao de fala! Use /balao pra personalizar.")
                                            .withStyle(ChatFormatting.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                                    RPWorldData.get(ctx.getSource().getServer())
                                            .setBubbleAllowed(target.getUUID(), false);
                                    RPWorldData.get(ctx.getSource().getServer())
                                            .setBubbleMode(target.getUUID(), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            target.getName().getString() + " NAO pode mais usar balao de fala."), true);
                                    target.sendSystemMessage(Component.literal(
                                            "Um admin DESLIGOU o seu balao de fala (suas falas voltaram so ao chat).")
                                            .withStyle(ChatFormatting.GRAY));
                                    return Command.SINGLE_SUCCESS;
                                }))))
                .then(Commands.literal("balaolist")
                        .executes(ctx -> {
                            var ids = RPWorldData.get(ctx.getSource().getServer()).bubbleAllowedIds();
                            if (ids.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Ninguem com balao liberado ainda. Libere com /rp admin balao <jogador> on"), false);
                            } else {
                                StringBuilder sb = new StringBuilder("Com balao liberado: ");
                                int i = 0;
                                for (var id : ids) {
                                    var p = ctx.getSource().getServer().getPlayerList().getPlayer(id);
                                    sb.append(p != null ? p.getName().getString() : id.toString().substring(0, 8));
                                    if (++i < ids.size()) {
                                        sb.append(", ");
                                    }
                                }
                                String msg = sb.toString();
                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("bolhas")
                        .then(Commands.literal("on").executes(ctx -> {
                            RPWorldData.get(ctx.getSource().getServer()).setBubblesOn(true);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Baloes de fala LIGADOS para todos."), true);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            RPWorldData.get(ctx.getSource().getServer()).setBubblesOn(false);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Baloes de fala DESLIGADOS para todos."), true);
                            return Command.SINGLE_SUCCESS;
                        }))));

        // /rp set <jogador> on|off — apenas admins
        root.then(Commands.literal("set")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.literal("on").executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                            ServerEvents.setState(target, true, true);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "RP ativado para " + target.getName().getString() + "."), true);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                            ServerEvents.setState(target, false, true);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "RP desativado para " + target.getName().getString() + "."), true);
                            return Command.SINGLE_SUCCESS;
                        }))));

        event.getDispatcher().register(root);
    }
}
