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

    /** (3.64.0) Mostra o texto GRANDE na tela dos alvos. ESTILOS:
     * 0 classico (faixa, cores &), 1 pesadelo (veu preto + sangue),
     * 2 runico (veu arcano + runas), 3 real (azul-noite + ouro).
     * Aceita VARIOS jogadores (nomes ou seletor @a — "avisar alguns players"). */
    private static void avisar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, String estilo, String texto) {
        int style = switch (estilo) {
            case "pesadelo" -> 1;
            case "runico" -> 2;
            case "real" -> 3;
            default -> 0;
        };
        String traduzido = translateAmp(texto);
        if (style == 0 && traduzido.indexOf('\u00a7') < 0) {
            traduzido = "\u00a7c" + traduzido; // sem cor? VERMELHO (o classic "voce esta sendo observado")
        }
        ScreenTextPayload payload = new ScreenTextPayload(traduzido, 8, style); // (3.61.0) 8s: aviso serio pede tempo
        for (ServerPlayer alvo : alvos) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo, payload);
            somAviso(alvo, style);
        }
        final int n = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Aviso (" + nomeEstilo(style) + ") na tela de " + n + " jogador(es)!")
                .withStyle(ChatFormatting.YELLOW), false); // (3.47.0) SO O ADMIN ve
    }

    /** (3.64.0) nome bonito do estilo pro feedback do admin. */
    private static String nomeEstilo(int style) {
        return switch (style) {
            case 1 -> "PESADELO";
            case 2 -> "RUNICO";
            case 3 -> "REAL";
            default -> "classico";
        };
    }

    /** (3.64.0) som do aviso direto no alvo (assustador pede som). */
    private static void somAviso(ServerPlayer alvo, int style) {
        switch (style) {
            case 1 -> alvo.level().playSound(null, alvo.blockPosition(),
                    net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 0.7F);
            // (3.66.0) RUNICO tem SOM PROPRIO: sino de ametista (mystico,
            // nada a ver com a maldicao do pesadelo)
            case 2 -> alvo.level().playSound(null, alvo.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 0.65F);
            case 3 -> alvo.level().playSound(null, alvo.blockPosition(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            default -> { }
        }
    }

    /**
     * (3.61.0) AVISO pra TODA A LOREZONE: quem estiver dentro da esfera da
     * zona &lt;id&gt; recebe o texto GRANDE na tela (igual /rp admin avisar).
     *
     * @return jogadores avisados (0 = zona inexistente ou vazia)
     */
    private static int avisarZona(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String zonaId, String texto) {
        LoreZones.LoreZonesData data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
        LoreZones.Zone zona = data.get(zonaId);
        if (zona == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Zona '" + zonaId + "' nao existe. Veja os ids com /lorezone listar."));
            return 0;
        }
        String traduzido = translateAmp(texto);
        if (traduzido.indexOf('\u00a7') < 0) {
            traduzido = "\u00a7c" + traduzido;
        }
        ScreenTextPayload payload = new ScreenTextPayload(traduzido, 8, 0);
        int n = 0;
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            if (p.level().dimension() == zona.dimension()
                    && p.position().distanceTo(zona.center()) <= zona.radius()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
                n++;
            }
        }
        if (n == 0) {
            ctx.getSource().sendFailure(Component.literal(
                    "Ninguem dentro da zona '" + zonaId + "' agora — aviso nao entregue."));
        } else {
            final int total = n;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Aviso na tela de " + total + " jogador(es) dentro de '" + zonaId + "'!")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return n;
    }

    /**
     * (3.50.0) /rp ajuda — lista TODOS os comandos com a funcao de cada um.
     * Jogadores veem os comandos gerais; ADMIN tambem recebe a secao admin.
     */
    private static int ajuda(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        final boolean adm = ctx.getSource().hasPermission(2);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a76\u00bb RP Tag \u2014 Comandos"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7e/rp \u00a77\u2014 liga/desliga o modo RP (tag \u00a7b\u25cf RP\u00a7/\u00a77\u25cb off)\u00a7e\n"
                + "/rp on|off \u00a77\u2014 define direto\u00a7e\n"
                + "/rp status \u00a77\u2014 mostra seu estado (RP, persona, balao)\u00a7e\n"
                + "/g <msg> \u00a77\u2014 fala GLOBAL (ouve o servidor todo)\u00a7e\n"
                + "/s <msg> \u00a77\u2014 GRITA (alcance maior)\u00a7e\n"
                + "/w <msg> \u00a77\u2014 sussurra (so quem esta colado ouve)\u00a7e\n"
                + "/pensar <msg> \u00a77\u2014 pensamento (balao italico com parenteses)\u00a7e\n"
                + "/me <acao> \u00a77\u2014 acao em 3a pessoa (sem balao)\u00a7e\n"
                + "/roll [NdN][+bonus] [motivo] \u00a77\u2014 dados pro evento (+3 = especialista)\u00a7e\n"
                + "/persona criar <nome> \u00a77\u2014 cria sua persona\u00a7e\n"
                + "/persona desc|idade|off|ver \u00a77\u2014 edita/ve a persona\u00a7e\n"
                + "/balao \u00a77\u2014 TELA de personalizacao do balao\u00a7e\n"
                + "/balao modo on|off \u00a77\u2014 fala so no balao (sem chat)" + "\n/balao typewriter on|off \u00a77\u2014 fala se escreve sozinha no balao (sua escolha)"), false);
        if (adm) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7c\u2014\u2014\u2014 SO ADMINS \u2014\u2014\u2014"), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7c/rp set <jogador> on|off \u00a77\u2014 define o RP de outro\u00a7c\n"
                    + "/rp admin medo <jogador> [1-30s] \u00a77\u2014 tela ESCONDE + treme de medo\u00a7c\n"
                    + "/rp admin avisar <jogadores> [estilo] <texto> \u00a77\u2014 texto na tela; estilos: classico, pesadelo, runico, real (alvos: nomes ou @a)\u00a7c\n"
                + "/rp admin avisozona <id> <texto> \u00a77\u2014 avisa TODO DENTRO da lorezone\u00a7c\n"
                    + "/rp admin chatlocal on|off \u00a77\u2014 chat normal por proximidade\u00a7c\n"
                    + "/rp admin bolha <jogador> on|off \u00a77\u2014 modo balao de outro\u00a7c\n"
                    + "/rp admin balao <jogador> on|off \u00a77\u2014 libera/bloqueia o balao\u00a7c\n"
                    + "/rp admin balaolist \u00a77\u2014 quem tem balao liberado\u00a7c\n"
                    + "/rp admin canon on|off \u00a77\u2014 modo de perder vidas (todos j\u00e1 t\u00eam; quem morrer perde uma)\u00a7c\n"
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
            // (3.82.0) vidas canônicas no status: estoque X/5 + modo
            int vidasCanon = data.getCanonLives(player.getUUID());
            boolean canon = data.isCanonEvent();
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("Vidas canônicas: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(vidasCanon + "/5").withStyle(
                            vidasCanon > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED))
                    .append(Component.literal(" · modo de PERDER: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(canon ? "LIGADO (quem morrer perde uma)" : "desligado")
                            .withStyle(canon ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)));
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
                // (3.64.0) /rp admin avisar <jogadores> [classico|pesadelo|runico|real]
                // <texto> — varios alvos (nomes ou @a) + 4 estilos de tela
                .then(Commands.literal("avisar")
                        .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                            String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                            avisar(ctx, alvos, "classico", texto);
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("pesadelo")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "pesadelo", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("runico")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "runico", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("real")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "real", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                // (3.61.0) /rp admin avisozona <id> <texto> — aviso na tela
                // de TODOS que estiverem dentro da esfera da lorezone
                .then(Commands.literal("avisozona")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String zonaId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                            String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                            return avisarZona(ctx, zonaId, texto);
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
                // (3.80.0) /rp admin canon on|off — LIGA/DESLIGA o modo de
                // PERDER vidas (todos os players JÁ têm vidas canônicas;
                // com o modo ligado, quem morrer perde uma). SEM mensagem
                // no chat: o aviso é o BANNER LORE ON/OFF pro servidor todo.
                .then(Commands.literal("canon")
                        .then(Commands.literal("on").executes(ctx -> {
                            RPWorldData.get(ctx.getSource().getServer()).setCanonEvent(true);
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                                    new CanonBannerPayload("\u2740 LORE ON \u2740",
                                            "\u2740 HARD-RP ON | VIDAS CANNON ON \u2740", 5, true));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Vidas canônicas: modo de PERDER LIGADO (quem morrer perde uma)."), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            RPWorldData.get(ctx.getSource().getServer()).setCanonEvent(false);
                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                                    new CanonBannerPayload("\u2740 LORE OFF \u2740",
                                            "\u2740 HARD-RP ON | VIDAS CANNON OFF \u2740", 5, false));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Vidas canônicas: modo de perder DESLIGADO."), false);
                            return Command.SINGLE_SUCCESS;
                        })))
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
