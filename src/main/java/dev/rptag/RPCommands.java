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
        // (3.45.0) agora o MEDO tambem e um EFEITO DE VERDADE: o icone
        // aparece no canto do HUD (com o tempo restante), igual Veneno.
        alvo.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                RPEffects.MEDO, seg * 20, 0, false, false)); // DeferredHolder E um Holder<MobEffect>
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u5291 " + alvo.getName().getString() + " esta com MEDO por " + seg + "s!")
                .withStyle(ChatFormatting.DARK_PURPLE), false); // (3.47.0) SO O ADMIN ve (era broadcast!)
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
                .withStyle(ChatFormatting.YELLOW), false); // (3.47.0) SO O ADMIN ve
    }

    /**
     * (3.50.0) /rp ajuda — lista TODOS os comandos com a funcao de cada um.
     * Jogadores veem os comandos gerais; ADMIN tambem recebe a secao admin.
     */
    private static int ajuda(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        final boolean adm = ctx.getSource().hasPermission(2);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a76\u2554\u2550\u2550 RP Tag \u2014 COMANDOS \u2550\u2550"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7e/rp \u00a77\u2014 liga/desliga o modo RP (tag \u00a7b\u25cf RP\u00a7/\u00a77\u25cb off)\u00a7e\n"
                + "/rp on|off \u00a77\u2014 define direto\u00a7e\n"
                + "/rp status \u00a77\u2014 mostra seu estado (RP, persona, balao)\u00a7e\n"
                + "/g <msg> \u00a77\u2014 fala GLOBAL (ouve o servidor todo)\u00a7e\n"
                + "/s <msg> \u00a77\u2014 GRITA (alcance maior)\u00a7e\n"
                + "/w <msg> \u00a77\u2014 sussurra (so quem esta colado ouve)\u00a7e\n"
                + "/me <acao> \u00a77\u2014 acao em 3a pessoa (sem balao)\u00a7e\n"
                + "/roll [NdN][+bonus] [motivo] \u00a77\u2014 dados pro evento (+3 = especialista)\u00a7e\n"
                + "/persona criar <nome> \u00a77\u2014 cria sua persona\u00a7e\n"
                + "/persona desc|idade|off|ver \u00a77\u2014 edita/ve a persona\u00a7e\n"
                + "/balao \u00a77\u2014 TELA de personalizacao do balao\u00a7e\n"
                + "/balao modo on|off \u00a77\u2014 fala so no balao (sem chat)"), false);
        if (adm) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7c\u2014\u2014\u2014 SO ADMINS \u2014\u2014\u2014"), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7c/rp set <jogador> on|off \u00a77\u2014 define o RP de outro\u00a7c\n"
                    + "/rp admin medo <jogador> [1-30s] \u00a77\u2014 tela ESCONDE + treme de medo\u00a7c\n"
                    + "/rp admin avisar <jogador> <texto> \u00a77\u2014 texto grande na tela (cores &)\u00a7c\n"
                    + "/rp admin chatlocal on|off \u00a77\u2014 chat normal por proximidade\u00a7c\n"
                    + "/rp admin bolha <jogador> on|off \u00a77\u2014 modo balao de outro\u00a7c\n"
                    + "/rp admin balao <jogador> on|off \u00a77\u2014 libera/bloqueia o balao\u00a7c\n"
                    + "/rp admin balaolist \u00a77\u2014 quem tem balao liberado\u00a7c\n"
                    + "/rp admin bolhas on|off \u00a77\u2014 baloes do servidor inteiro\u00a7c\n"
                    + "/do <texto> \u00a77\u2014 narracao de ambiente\u00a7c\n"
                    + "/lorezone criar <id> <raio> <titulo> \u00a77\u2014 cria zona de lore onde voce esta\u00a7c\n"
                    + "/lorezone texto|som|musica <id> ... \u00a77\u2014 edits a zona (musica aceita presets e @arquivo)\u00a7c\n"
                    + "/lorezone musicas \u00a77\u2014 lista sons prontos + API de musica custom\u00a7c\n"
                    + "/lorezone listar|remover <id> \u00a77\u2014 gerencia zonas\u00a7c\n"
                    + "/effect give <jogador> rptag:medo <s> \u00a77\u2014 medo via efeito vanilla"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root = Commands.literal("rp");

        // /rp — alterna
        root.executes(ctx -> {
            ServerEvents.toggle(ctx.getSource().getPlayerOrException());
            return Command.SINGLE_SUCCESS;
        });

        // (3.50.0) /rp ajuda (e /rp help) — lista comandos com a funcao de cada
        root.then(Commands.literal("ajuda").executes(ctx -> ajuda(ctx)));
        root.then(Commands.literal("help").executes(ctx -> ajuda(ctx)));

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
