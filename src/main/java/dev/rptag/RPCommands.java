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

    /** (3.44.0) Traduz codigos & (0-9, a-f, k-o, r; && = &) p/ section codes.
     *  (3.85.0) package-private: LoreZoneCommands usa no /lorezone aviso. */
    static String translateAmp(String in) {
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
    /**
     * (4.4.0) /rp admin vida <jogador> tirar|dar|definir — MEXE nas vidas canônicas
     * de alguém NA HORA (sem esperar morte). É o mesmo estoque que o ritual de morte
     * usa (getCanonLives/setCanonLives), então o que este comando tira aparece no
     * /rp status e no coração do mundo.
     *
     * <p>(4.5.0) O alvo pode estar OFFLINE: o estoque é por UUID, então o valor muda
     * igual; o que fica guardado é um recado ("VOCÊ PERDEU UMA VIDA — RESTAM n")
     * entregue quando a pessoa voltar (ver {@code ServerEvents.onLoggedIn}).
     *
     * <p>Quem LEVA a perda ONLINE recebe o mesmo aviso de morte canônica (estilo 4) e
     * o trovão; se chegar a ZERO, o MEDO ABSURDO (30 s) entra igual ao ritual. NÃO
     * congela o jogador (não houve morte — quem aplica é um admin, muitas vezes no
     * meio de uma cena).
     */
    private static int vidaTirar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            VidasAdmin.Alvo alvo, int qtd) {
        RPWorldData wd = RPWorldData.get(ctx.getSource().getServer());
        int antes = wd.getCanonLives(alvo.getUUID());
        int depois = VidaCanonica.tirar(antes, qtd);
        if (depois == antes) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    alvo.nome() + " ja esta SEM vidas canonicas (0/5) - nada a tirar.")
                    .withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        wd.setCanonLives(alvo.getUUID(), depois);
        String msg = depois > 0
                ? "VOCE PERDEU UMA VIDA \u2014 RESTAM " + depois
                : "VOCE PERDEU SUA ULTIMA VIDA \u2014 TOME CUIDADO";
        ServerPlayer online = alvo.online();
        if (online != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(online, new ScreenTextPayload(msg, 12, 4));   // (5.27.0) 12 s: o dono achava rapido demais
            online.playNotifySound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.WEATHER, 3.0F, 0.8F);
            if (depois == 0) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(online, new FearPayload(30));
                online.addEffect(new net.minecraft.world.effect.MobEffectInstance(RPEffects.MEDO, 30 * 20, 0, false, false));
            }
        } else {
            // (4.5.0) offline: fica o recado pro proximo login
            wd.setRecadoVida(alvo.getUUID(), msg);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u2620 " + alvo.nome() + (alvo.isOnline() ? "" : " (OFFLINE)") + ": " + antes + "/5 -> " + depois
                        + "/5 (vida canonica REMOVIDA por admin)")
                .withStyle(ChatFormatting.RED), true);
        RPTagMod.LOGGER.info("RP Tag: admin removeu vida canonica de {} ({} -> {}){}",
                alvo.nome(), antes, depois, alvo.isOnline() ? "" : " [offline]");
        return Command.SINGLE_SUCCESS;
    }

    /** (4.4.0) oposto do tirar: devolve vida canonica (teto 5), com o mesmo aviso do coracao. */
    private static int vidaDar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            VidasAdmin.Alvo alvo, int qtd) {
        RPWorldData wd = RPWorldData.get(ctx.getSource().getServer());
        int antes = wd.getCanonLives(alvo.getUUID());
        int depois = VidaCanonica.dar(antes, qtd);
        if (depois == antes) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    alvo.nome() + " ja esta com as vidas CHEIAS (5/5).")
                    .withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        wd.setCanonLives(alvo.getUUID(), depois);
        ServerPlayer online = alvo.online();
        if (online != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(online, new ScreenTextPayload(
                    "VOCE GANHOU UMA NOVA CHANCE \u2014 uma vida canonica foi restaurada (" + depois + "/5)", 12, 0));
            online.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.4F);
        } else {
            wd.setRecadoVida(alvo.getUUID(), "VOCE GANHOU UMA NOVA CHANCE \u2014 uma vida canonica foi restaurada ("
                    + depois + "/5)");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u2740 " + alvo.nome() + (alvo.isOnline() ? "" : " (OFFLINE)") + ": " + antes + "/5 -> " + depois
                        + "/5 (vida canonica DEVOLVIDA por admin)")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return Command.SINGLE_SUCCESS;
    }

    /** (4.4.0) define o total de uma vez (0..5). */
    private static int vidaDefinir(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            VidasAdmin.Alvo alvo, int valor) {
        RPWorldData wd = RPWorldData.get(ctx.getSource().getServer());
        int antes = wd.getCanonLives(alvo.getUUID());
        int depois = VidaCanonica.definir(valor);
        wd.setCanonLives(alvo.getUUID(), depois);
        if (!alvo.isOnline()) {
            wd.setRecadoVida(alvo.getUUID(), "SUAS VIDAS CANONICAS FORAM DEFINIDAS PELO ADMIN: " + depois + "/5");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u2740 " + alvo.nome() + (alvo.isOnline() ? "" : " (OFFLINE)") + ": " + antes + "/5 -> " + depois
                        + "/5 (vidas canonicas DEFINIDAS por admin)")
                .withStyle(ChatFormatting.GOLD), true);
        return Command.SINGLE_SUCCESS;
    }

    /** (4.4.0) mostra quantas vidas canonicas o alvo tem. */
    private static int vidaVer(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            VidasAdmin.Alvo alvo) {
        int v = RPWorldData.get(ctx.getSource().getServer()).getCanonLives(alvo.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u2740 " + alvo.nome() + (alvo.isOnline() ? "" : " (OFFLINE)") + ": " + v + "/5 vidas canonicas"
                        + (v == 0 ? " (SEM vidas!)" : ""))
                .withStyle(v == 0 ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (4.5.0) /rp admin vida — sem argumento: quem NAO esta com 5/5 (online e offline). */
    private static int vidaListar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        RPWorldData wd = RPWorldData.get(server);
        java.util.TreeMap<String, Integer> mapa = new java.util.TreeMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            mapa.put(p.getName().getString(), wd.getCanonLives(p.getUUID()));
        }
        for (java.util.UUID id : wd.canonLivesIds()) {
            String nome = VidasAdmin.nomeDe(server, id);
            mapa.putIfAbsent(nome, wd.getCanonLives(id));
        }
        if (mapa.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Ninguem perdeu vida ainda (todos 5/5).")
                    .withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("\u2740 VIDAS CANONICAS").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        mapa.forEach((nome, v) -> ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + nome + ": " + v + "/5" + (v == 0 ? "  (SEM vidas)" : ""))
                .withStyle(v == 0 ? ChatFormatting.RED : v < 3 ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * (4.5.0) Resolve o argumento "alvo" da vida. Aceita NOME (online ou offline),
     * os seletores do vanilla que fazem sentido aqui (@a, @p, @s) e uma LISTA com
     * vírgula. Devolve todos os alvos — quem aplica itera.
     */
    private static java.util.List<VidasAdmin.Alvo> alvos(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String cru = ListaAlvosArgument.get(ctx, "jogador").trim();
        var server = ctx.getSource().getServer();
        java.util.List<VidasAdmin.Alvo> out = new java.util.ArrayList<>();
        if (cru.startsWith("@")) {
            switch (cru) {
                case "@a" -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        out.add(new VidasAdmin.Alvo(p.getUUID(), p.getName().getString(), p));
                    }
                }
                case "@p" -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) throw VidasAdmin.soJogador();
                    out.add(new VidasAdmin.Alvo(p.getUUID(), p.getName().getString(), p));
                }
                case "@s" -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    out.add(new VidasAdmin.Alvo(p.getUUID(), p.getName().getString(), p));
                }
                default -> throw VidasAdmin.seletorInvalido(cru);
            }
            return out;
        }
        for (String nome : VidasAdmin.dividirNomes(cru)) {
            out.add(VidasAdmin.resolver(ctx.getSource(), nome));
        }
        if (out.isEmpty()) throw VidasAdmin.naoAchei();
        return out;
    }

    /** (4.5.0) uma linha de resposta por alvo (usada pelos verbos em lote). */
    private static void resumo(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.List<VidasAdmin.Alvo> alvos) {
        if (alvos.size() > 1) {
            final int n = alvos.size();
            ctx.getSource().sendSuccess(() -> Component.literal("(" + n + " alvos)")
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /**
     * (4.5.1) O argumento de alvo: o custom (aceita "Ana,Beto" e @a) quando ele está
     * registrado; senão o word() do vanilla. O custom sem registro quebrava o LOGIN
     * de todo mundo com "Invalid player data" — o fallback existe pra isso nunca mais
     * acontecer, mesmo se algo der errado no registro.
     */
    private static com.mojang.brigadier.arguments.ArgumentType<String> argumentoAlvos() {
        boolean ok = ListaAlvosArgument.disponivel();
        // (4.5.1) deixa rastro no log: sem isso, o unico sintoma de um argumento
        // custom fora do registry era "Invalid player data" no login do jogador
        RPTagMod.LOGGER.info("RP Tag: /rp admin vida usando {}",
                ok ? "lista_alvos (aceita Ana,Beto e @a)" : "o argumento padrao (SOMENTE nomes simples)");
        return ok
                ? ListaAlvosArgument.alvos()
                : com.mojang.brigadier.arguments.StringArgumentType.word();
    }

    /** (4.5.0) monta os 4 verbos (tirar/dar/definir/ver) para o nó de alvo. */
    private static com.mojang.brigadier.builder.ArgumentBuilder<net.minecraft.commands.CommandSourceStack, ?> verbosVida() {
        var no = Commands.argument("jogador", argumentoAlvos());
        return no
                .then(Commands.literal("tirar")
                        .executes(c -> vidaTirarVarios(c, alvos(c), 1))
                        .then(Commands.argument("quantia", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                .executes(c -> vidaTirarVarios(c, alvos(c),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "quantia")))))
                .then(Commands.literal("dar")
                        .executes(c -> vidaDarVarios(c, alvos(c), 1))
                        .then(Commands.argument("quantia", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                .executes(c -> vidaDarVarios(c, alvos(c),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "quantia")))))
                .then(Commands.literal("definir")
                        .then(Commands.argument("quantia", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                                .executes(c -> vidaDefinirVarios(c, alvos(c),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "quantia")))))
                .then(Commands.literal("ver").executes(c -> vidaVerVarios(c, alvos(c))));
    }

    // (4.5.0) wrappers que iteram: o comando aceita vários alvos de uma vez
    private static int vidaTirarVarios(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,
            java.util.List<VidasAdmin.Alvo> lista, int qtd) {
        for (VidasAdmin.Alvo a : lista) vidaTirar(c, a, qtd);
        resumo(c, lista);
        return Command.SINGLE_SUCCESS;
    }

    private static int vidaDarVarios(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,
            java.util.List<VidasAdmin.Alvo> lista, int qtd) {
        for (VidasAdmin.Alvo a : lista) vidaDar(c, a, qtd);
        resumo(c, lista);
        return Command.SINGLE_SUCCESS;
    }

    private static int vidaDefinirVarios(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,
            java.util.List<VidasAdmin.Alvo> lista, int v) {
        for (VidasAdmin.Alvo a : lista) vidaDefinir(c, a, v);
        resumo(c, lista);
        return Command.SINGLE_SUCCESS;
    }

    private static int vidaVerVarios(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,
            java.util.List<VidasAdmin.Alvo> lista) {
        for (VidasAdmin.Alvo a : lista) vidaVer(c, a);
        return Command.SINGLE_SUCCESS;
    }

    /** (4.9.0) LOTE: "/rp admin vida lote A,B,C tirar 1" -> aplica NA HORA, sem confirmar.
     *  (antes guardava e pedia /rp admin confirmar; o passo extra saiu a pedido do dono). */
    private static int vidaLote(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String acao, int qtd) {
        String cru = ListaAlvosArgument.get(ctx, "jogador");
        java.util.List<VidasAdmin.Alvo> alvos = new java.util.ArrayList<>();
        java.util.List<String> falhas = new java.util.ArrayList<>();
        for (String nome : VidasAdmin.dividirNomes(cru)) {
            try {
                alvos.add(VidasAdmin.resolver(ctx.getSource(), nome));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                falhas.add(nome);
            }
        }
        if (alvos.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Nenhum nome valido na lista."));
            return 0;
        }
        for (VidasAdmin.Alvo a : alvos) {
            switch (acao) {
                case "tirar" -> vidaTirar(ctx, a, qtd);
                case "dar" -> vidaDar(ctx, a, qtd);
                default -> vidaDefinir(ctx, a, qtd);
            }
        }
        String acaoTxt = acao.equals("definir") ? "definir " + qtd : acao + " " + qtd;
        int total = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal("\u2714 " + acaoTxt + " em " + total
                        + " jogador(es)" + (falhas.isEmpty() ? "" : "  [ignorados: " + String.join(",", falhas) + "]"))
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

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

    // ================================================== (5.25.0) CIBERPSICOSE
    /**
     * Liga a CIBERPSICOSE nos alvos: efeito de verdade ({@code rptag:ciberpsicose}) por N
     * segundos + o estouro de faíscas elétricas no corpo + o som do glitch. O CLIENTE vê o
     * efeito e liga a tela quebrada — então o efeito sobrevive a relog e também funciona por
     * {@code /effect give <jogador> rptag:ciberpsicose 30}.
     */
    private static int ciberAplicar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, int seg) {
        for (ServerPlayer alvo : alvos) {
            alvo.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    RPEffects.CIBERPSICOSE, seg * 20, 0, false, false, true));
            particulasCiberpsicose(alvo);
            alvo.level().playSound(null, alvo.blockPosition(), RPTagMod.SOM_CIBERPSICOSE.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.0F);
        }
        final int n = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7b\u26a1 CIBERPSICOSE em " + n + " jogador(es) por " + seg + "s")
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Tira a ciberpsicose: o efeito sai e a tela do cliente se remonta sozinha. */
    private static int ciberParar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos) {
        for (ServerPlayer alvo : alvos) {
            alvo.removeEffect(RPEffects.CIBERPSICOSE);
            CiberpsicoseSistema.esquecer(alvo.getUUID());
            // (5.28.0) AVISA O CLIENTE: texto vazio no estilo 14 = "fecha a tela AGORA".
            // Era isso que faltava pra tela não ficar presa na cara do jogador.
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo,
                    new ScreenTextPayload("", 1, 14));
        }
        final int n = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7bCIBERPSICOSE desligada em " + n + " jogador(es)").withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    // ====================================================== (5.25.0) VAMPIRO
    /** Liga/desliga o atributo {@code rptag:vampiro} (1 = vampiro, 0 = normal). */
    private static int vampiroDefinir(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, boolean ligado) {
        java.util.List<String> feitos = new java.util.ArrayList<>();
        for (ServerPlayer alvo : alvos) {
            VampiroSistema.definir(alvo, ligado);
            feitos.add(alvo.getName().getString());
            if (ligado) {
                alvo.displayClientMessage(Component.literal(
                                "\u00a74\u00a7lVOC\u00ca \u00c9 UM VAMPIRO \u00a7r\u00a7c\u2014 o sol agora te queima. "
                                        + "Segure o Amuleto do Eclipse para se proteger.")
                        .withStyle(ChatFormatting.RED), false);
                // (5.25.0) o morcego: o som do vampiro (batida de asa no escuro)
                alvo.level().playSound(null, alvo.blockPosition(),
                        net.minecraft.sounds.SoundEvents.BAT_AMBIENT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.5F);
            } else {
                alvo.displayClientMessage(Component.literal("\u00a77O sol não te incomoda mais.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        final String nomes = String.join(", ", feitos);
        ctx.getSource().sendSuccess(() -> Component.literal((ligado ? "\u00a74VAMPIRO LIGADO \u00a77em " : "\u00a77VAMPIRO DESLIGADO em ")
                        + nomes + "  \u00a78(atributo rptag:vampiro = " + (ligado ? 1 : 0) + ")")
                .withStyle(ligado ? ChatFormatting.DARK_RED : ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Mostra o estado de cada alvo (vampiro? protegido? tomando sol?). */
    private static int vampiroVer(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos) {
        for (ServerPlayer alvo : alvos) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00a77" + alvo.getName().getString()
                            + ": \u00a7f" + VampiroSistema.estado(alvo)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Entrega o Amuleto do Eclipse. */
    private static int eclipseDar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos) {
        for (ServerPlayer alvo : alvos) {
            net.minecraft.world.item.ItemStack is = new net.minecraft.world.item.ItemStack(RPTagMod.AMULETO_ECLIPSE.get());
            if (!alvo.getInventory().add(is)) {
                alvo.drop(is, false);
            }
            alvo.displayClientMessage(Component.literal("\u00a7bO Amuleto do Eclipse chegou às suas mãos.")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        final int n = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7bAmuleto do Eclipse para " + n + " jogador(es)")
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (3.64.0) Mostra o texto GRANDE na tela dos alvos. ESTILOS:
     * 0 classico (caixa de fala, cores &), 1 pesadelo (veu preto + sangue),
     * 2 runico (veu arcano + runas), 3 real (azul-noite + ouro),
     * 4 morte (letras retas de sangue), 5 inferno (brasas), 6 gelo
     * (cristais), 7 divino (feixes de luz), 8 void (estrelas),
     * 9 sangue (gotas escorrendo). (3.86.0) +6 estilos novos.
     * Aceita VARIOS jogadores (nomes ou seletor @a — "avisar alguns players"). */
    /**
     * (5.16.2) um estilo da seção {@code /rp admin avisar2}: basta o nome, o texto vem depois.
     * Os alvos continuam vindo de fora ({@code <jogadores>}), então o comando fica
     * {@code /rp admin avisar2 <jogadores> <estilo> <texto>}.
     *
     * <p>(5.16.4) <b>O TEXTO É OPCIONAL.</b> O dono:
     * <i>"/rp admin avisar2 EdUuJoGaA morte2 — caso eu queira usar só isso eu posso, não
     * precisa de texto, se tiver texto ok, mas não precisa 100% das vezes, às vezes é apenas
     * aviso"</i>. Então:
     * <ul>
     *   <li>{@code /rp admin avisar2 Fulano morte2} — só o aviso (a carta aparece, sem fala);</li>
     *   <li>{@code /rp admin avisar2 Fulano morte2 Fulano traiu a coroa} — aviso + a fala.</li>
     * </ul>
     * Sem texto, os avisos de carta/ritual não inventam frase nenhuma: a linha da mensagem
     * simplesmente não é desenhada.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> aviso2(
            String estilo) {
        return Commands.literal(estilo)
                // (5.16.4) SEM TEXTO: só o aviso
                .executes(ctx -> {
                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                    avisar(ctx, alvos, estilo, "");
                    return Command.SINGLE_SUCCESS;
                })
                // com texto: aviso + a fala
                .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                            String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                            avisar(ctx, alvos, estilo, texto);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void avisar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, String estilo, String texto) {
        int style = switch (estilo) {
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
            case "pesadelo2" -> 11;   // (3.98.0) Sono Sem Descanso (antigo)
            case "egipicio", "egipcio" -> 12;   // (4.5.0) O Olho de Horus
            case "expurgo" -> 13;   // (4.9.2) Hora do Expurgo (TV de emergencia)
            case "ciberpsicose", "ciber" -> 14;   // (4.9.3) Alerta de Ciberpsicose (glitch)
            case "louco" -> 25;   // (5.14.0) O Louco (nevoa cinzenta, sigilo, relogio)
            case "marinheiro", "enforcado" -> 26;   // (5.14.0) O Marinheiro / Enforcado (tempestade, mar, leme)
            case "espectador", "justica" -> 27;   // (5.14.0) O Espectador / Justica (sonho, balanca, olhos)
            // (5.16.0) as oito cartas de tarô do dono (o mesmo motor de canvas do protótipo)
            case "eremita" -> 28;   // (5.16.0) O EREMITA
            case "julgamento" -> 29;   // (5.16.0) O JULGAMENTO
            case "lua" -> 30;   // (5.16.0) A LUA
            case "mago" -> 31;   // (5.16.0) O MAGO
            case "morte2", "ceifador", "morte_carta" -> 32;   // (5.16.0) A MORTE (carta)
            case "sol" -> 33;   // (5.16.0) O SOL
            case "torre" -> 34;   // (5.16.0) A TORRE
            case "estrela" -> 35;   // (5.16.0) A ESTRELA
            case "tolo", "foulo" -> 36;   // (5.16.2) TOLO = a CARTA do Louco (o protótipo FoolWarning, de volta)
            case "fita" -> 37;   // (5.17.0) FITA CASSETE: o aviso que a fita toca
            // (5.24.3) INFECCAO: o dono mandou DOIS protótipos e disse que os dois valem.
            // O estilo 38 é o cinematográfico (InfectionWarning); o 39 é o "estilo Minecraft"
            // (InfeccaoAviso). Os DOIS moram no /rp admin avisar — nunca no avisar2 (pedido 75).
            case "infeccao", "infecçao", "infecção", "infection" -> 38;
            case "infeccao2", "infecção2", "infecçao2", "infeccao_mc" -> 39;
            case "energia" -> 15;   // (5.5.0) ENERGIA — anomalia de entropia
            case "conhecimento" -> 16;   // (5.7.0) CONHECIMENTO — o olho que sabe
            case "morteop" -> 20;     // (5.7.9) protótipo AvisoMorte: relógio + crânio
            case "medoop" -> 22;       // (5.8.1) protótipo AvisoMedo: membrana + lanterna
            default -> 0;
        };
        // (5.20.0) "AVISAR AXION:" — o dono: 'SE O ADM "rp admin avisar AXION: " tem que ficar
        // vermelho mais escuro, personalizado'. Se o texto começa com AXION:, o aviso vira a
        // VOZ da Axion: nome em vermelho escuro, negrito, e o resto na mesma cor.
        boolean vozDaAxion = false;
        if (texto != null) {
            String t = texto.trim();
            String baixo = t.toLowerCase(java.util.Locale.ROOT);
            if (baixo.startsWith("axion:") || baixo.startsWith("axion :")) {
                texto = t.substring(baixo.indexOf(':') + 1).trim();
                vozDaAxion = true;
                style = 0;                     // sem fantasia: é ela falando
            }
        }
        // (4.7.3) Grafia "egipcio" (sem o "i"): nao tem literal proprio, para nao
        // aparecer duas vezes no tab-complete — mas se a primeira palavra do texto
        // for "egipcio", o aviso vira egipicio e o resto e a mensagem.
        if (style == 0 && texto != null) {
            String t = texto.trim();
            int sp = t.indexOf(' ');
            if (sp > 0 && t.substring(0, sp).toLowerCase(java.util.Locale.ROOT).equals("egipcio")) {
                style = 12;
                texto = t.substring(sp + 1).trim();
            }
        }
        final int estiloFinal = style;   // lambda do sendSuccess exige effectively final
        String traduzido = translateAmp(texto);
        if (vozDaAxion) {
            // (5.20.0) VERMELHO MAIS ESCURO (§4) com o nome dela na frente, personalizado
            traduzido = "\u00a74\u00a7lAXION\u00a74\u00a7l \u00bb \u00a7r\u00a74" + traduzido;
        } else if (style == 0 && traduzido.indexOf('\u00a7') < 0) {
            traduzido = "\u00a7c" + traduzido; // sem cor? VERMELHO (o classic "voce esta sendo observado")
        }
        ScreenTextPayload payload = new ScreenTextPayload(traduzido, 12, style); // (5.27.0) 12 s (era 8): aviso serio pede tempo
        for (ServerPlayer alvo : alvos) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo, payload);
            somAviso(alvo, style);
        }
        final int n = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Aviso (" + nomeEstilo(estiloFinal) + ") na tela de " + n + " jogador(es)!")
                .withStyle(ChatFormatting.YELLOW), false); // (3.47.0) SO O ADMIN ve
    }

    /** (4.5.0) liga/desliga UMA regra e responde colorido. */
    /** (3.64.0) nome bonito do estilo pro feedback do admin. */
    private static String nomeEstilo(int style) {
        return switch (style) {
            case 1 -> "PESADELO";
            case 2 -> "RUNICO";
            case 3 -> "REAL";
            case 4 -> "MORTE";
            case 5 -> "INFERNO";
            case 6 -> "GELO";
            case 7 -> "DIVINO";
            case 8 -> "VOID";
            case 9 -> "SANGUE";
            case 10 -> "DEMONIACO";
            case 11 -> "PESADELO SONO";
            case 12 -> "EGIPICIO";
            case 13 -> "EXPURGO";
            case 14 -> "CIBERPSICOSE";
            case 15 -> "ENERGIA";   // (5.5.0) anomalia de entropia
            case 16 -> "CONHECIMENTO";   // (5.7.0) o olho que sabe
            case 18 -> "SAIDA";   // (5.7.0) animacao de saida do jogador
            case 20 -> "MORTEOP";   // (5.7.9) relogio + cranio
            case 22 -> "MEDOOP";   // (5.8.1) a membrana rachando
            case 38 -> "INFECCAO";   // (5.24.3) veias + olho + FORCA x SANIDADE
            case 39 -> "INFECCAO (MINECRAFT)";   // (5.24.3) a variante do 2o protótipo
            default -> "classico";
        };
    }

    /** (3.93.0) PARTICULAS de mundo do DEMONIACO (server-side puro:
     *  PENTAGRAMA de fogo de alma no chao + circulo de chamas + coluna
     *  de fumaca + respingos de lava ao redor do jogador). Sem tocar em
     *  nenhuma classe client! */
    private static void particulasAbismo(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY() + 0.1, z = p.getZ(), R = 3.2;
        for (int i = 0; i < 5; i++) { // pentagrama (cordas i -> i+2)
            double a0 = i * 72 * Math.PI / 180, a1 = ((i + 2) % 5) * 72 * Math.PI / 180;
            for (int s = 0; s <= 14; s++) {
                double u = s / 14.0;
                double px = x + R * (Math.cos(a0) * (1 - u) + Math.cos(a1) * u);
                double pz = z + R * (Math.sin(a0) * (1 - u) + Math.sin(a1) * u);
                lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        px, y, pz, 0, 0, 0.04, 0, 1.0);
            }
        }
        for (int i = 0; i < 40; i++) { // circulo
            double a = i * Math.PI * 2 / 40;
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    x + Math.cos(a) * R, y, z + Math.sin(a) * R, 0, 0, 0.03, 0, 1.0);
        }
        for (int i = 0; i < 30; i++) {
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    x, y + i * 0.15, z, 2, 0.4, 0.05, 0.4, 0.01);
        }
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                x, y + 1, z, 14, 1.2, 0.3, 1.2, 0);
    }

    /** (3.95.0) PARTICULAS de mundo do DIVINO (server-side puro:
     *  coroa de END_ROD que se expande e sobe + espiral ascendente
     *  ao redor do jogador). Sem tocar em nenhuma classe client! */
    private static void particulasAscensao(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI * 2 / 72;
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x + Math.cos(a) * 2.5, y + 0.1, z + Math.sin(a) * 2.5,
                    0, Math.cos(a) * 0.08, 0.10 + (i % 3) * 0.04, Math.sin(a) * 0.08, 1.0);
        }
        for (int i = 0; i < 48; i++) {
            double a = i * 0.55;
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x + Math.cos(a) * 1.2, y + i * 0.12, z + Math.sin(a) * 1.2,
                    1, 0, 0.02, 0, 0.0);
        }
    }

    /** (3.96.0) VOID: portais sendo sugados ao redor do alvo (server-side puro). */
    private static void particulasVoid(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                x, y + 1, z, 120, 1.5, 1.5, 1.5, 0.8);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                x, y + 1, z, 40, 0.6, 0.8, 0.6, 0.05);
    }

    /** (3.97.0) PESADELO "Seguido": fumaca rasteira + almas de sculk
     *  (server-side puro). escuridao=true aplica o efeito Escuridao
     *  por 5s (afeta gameplay) — LIGADO desde 3.96.3 (susto extra). */
    private static void particulasPesadelo(ServerPlayer p, boolean escuridao) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                x, y + 0.2, z, 40, 1.5, 0.1, 1.5, 0.01);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                x, y + 0.5, z, 12, 1.0, 0.6, 1.0, 0.02);
        if (escuridao) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.DARKNESS, 100, 0, false, false));
    }

    /** (3.97.0) INFERNO "Fornalha": tornado de chamas em espiral +
     *  respingos de lava + fumaca densa (server-safe). */
    private static void particulasInferno(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        for (int i = 0; i < 60; i++) {
            double a = i * 0.6, r = 1.5 + i * 0.04;
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    x + Math.cos(a) * r, y + 0.1 + i * 0.05,
                    z + Math.sin(a) * r, 0, 0, 0.12, 0, 1.0);
        }
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                x, y + 1, z, 20, 1.2, 0.3, 1.2, 0);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                x, y + 2.5, z, 25, 1.0, 0.4, 1.0, 0.02);
    }

    /** (4.9.2) EXPURGO "Hora do Expurgo": o sangue (redstone) caindo, a
     *  poeira de redstone, a fumaca densa e as chamas do prototipo do dono —
     *  server-side puro (a TV inteira e desenhada no cliente). */
    private static void particulasExpurgo(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                x, y + 1.0, z, 60, 1.2, 0.8, 1.2, 0.15);
        lv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                x, y + 3.0, z, 40, 2.0, 0.3, 2.0, 0.0);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                x, y + 0.3, z, 40, 1.5, 0.2, 1.5, 0.02);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                x, y + 0.2, z, 25, 1.2, 0.1, 1.2, 0.02);
    }

    /** (4.9.3) CIBERPSICOSE "Alerta de Ciberpsicose": faiscas eletricas, fumaca
     *  e brilhos ao redor do alvo — server-side puro (a tela e do cliente).
     *  O prototipo tinha tambem um "aplica Nausea" opcional: NAO ligamos por
     *  padrao (mexe no jogo, nao so na tela — quem quiser usa /effect). */
    private static void particulasCiberpsicose(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                x, y + 1.2, z, 60, 0.7, 0.9, 0.7, 0.4);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                x, y + 1.0, z, 20, 0.5, 0.6, 0.5, 0.02);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 1.0, z, 12, 0.8, 0.8, 0.8, 0.05);
    }

    /** (3.97.0) GELO "Geada Eterna": nevasca + estilhacos de gelo
     *  compactado + brilhos (server-safe; nao congela o player). */
    private static void particulasGelo(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                x, y + 1.2, z, 80, 1.5, 1.0, 1.5, 0.05);
        lv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()),
                x, y + 1.0, z, 40, 0.5, 0.6, 0.5, 0.2);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 1.0, z, 15, 1.0, 1.0, 1.0, 0.02);
    }

    /** (3.97.0) RUNICO "Traducao Arcana": runas de verdade (ENCHANT)
     *  voando pro alvo + magia + anel de END_ROD no chao. */
    private static void particulasRunico(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                x, y + 1.5, z, 80, 1.5, 1.0, 1.5, 0.5);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                x, y + 1.0, z, 25, 0.8, 0.8, 0.8, 0.05);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x + Math.cos(a) * 2.5, y + 0.1, z + Math.sin(a) * 2.5, 0, 0, 0.05, 0, 1.0);
        }
    }

    /** (3.95.0) SANGUE: espirro de sangue no alvo — bloco de redstone
     *  explodindo + poeira vermelha caindo do ceu (server-safe). */
    private static void particulasSangue(ServerPlayer p) {
        net.minecraft.server.level.ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        net.minecraft.core.particles.BlockParticleOption blk =
                new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        net.minecraft.core.particles.BlockParticleOption fall =
                new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        lv.sendParticles(blk, x, y + 1.0, z, 60, 0.45, 0.6, 0.45, 0.25);
        for (int i = 0; i < 24; i++)
            lv.sendParticles(fall, x + (Math.random() - 0.5) * 2.4, y + 3.2,
                    z + (Math.random() - 0.5) * 2.4, 1, 0, 0, 0, 0);
    }

    /** (3.64.0) som do aviso direto no alvo (assustador pede som).
     *  (3.86.0) os 6 estilos novos ganharam som proprio. */
    /** (4.5.0) EGIPICIO: o deserto responde no servidor (areia + incenso + ouro). */
    private static void particulasEgipcio(ServerPlayer p) {
        var lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        var areiaCai = new net.minecraft.core.particles.BlockParticleOption(
                net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
                net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
        var areiaSol = new net.minecraft.core.particles.BlockParticleOption(
                net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
        lv.sendParticles(areiaCai, x, y + 3.2, z, 40, 1.2, 0.8, 1.2, 0.02);
        lv.sendParticles(areiaSol, x, y + 0.2, z, 30, 0.9, 0.1, 0.9, 0.06);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                x, y + 0.4, z, 18, 0.7, 0.2, 0.7, 0.012);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y + 1.6, z, 10, 0.8, 1.0, 0.8, 0.01);
    }

    private static void somAviso(ServerPlayer alvo, int style) {
        switch (style) {
            // (5.17.0) FITA: o chiado da cassete (o mesmo som do item)
            case 37 -> alvo.level().playSound(null, alvo.blockPosition(), RPTagMod.SOM_FITA.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            // (5.26.0) INFECCAO e INFECCAO 2: agora tem TRILHA PROPRIA (10 s, sintetizada pra
            // bater com a animacao). Quem toca e o FOX do cliente, no mesmo instante em que a tela
            // comeca — por isso aqui nao ha som do servidor (antes eram 2 sons baixinhos do jogo
            // que o dono ouvia como "fraco, sem som").
            case 38, 39 -> { }
            // (3.96.0) PESADELO: o FX do cliente tem TRILHA propria
            // (enderman + caixinha desafinada + coracao + grito) — o
            // servidor faz as PARTICULAS (sculk + tinta + fumaca) + o
            // SUSTO EXTRA: Escuridao 5s (3.96.3, a pedido do user p/ testar)
            case 1 -> particulasPesadelo(alvo, true);
            // (3.97.0) RUNICO: o FX tem TRILHA propria (mesa de encantamento
            // + sinos pentatonicos por runa) — o servidor so faz as PARTICULAS
            case 2 -> particulasRunico(alvo);
            case 3 -> alvo.level().playSound(null, alvo.blockPosition(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            // (3.86.0) MORTE: ancora de respawn se esvaziando (sepulcral) ·
            // INFERNO: blaze queimando · GELO: congelamento · DIVINO: sino
            // de igreja · VOID: portal distorcido grave · SANGUE: coração
            // do Warden (pulso escuro)
            case 4 -> alvo.level().playSound(null, alvo.blockPosition(),
                    net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 0.55F);
            // (3.97.0) INFERNO: o FX tem TRILHA propria (firecharge + ghast
            // + lava/ambiencia de fogo) — o servidor so faz as PARTICULAS
            case 5 -> particulasInferno(alvo);
            // (3.97.0) GELO: o FX tem TRILHA propria (quebra de vidro +
            // sinos de gelo) — o servidor so faz as PARTICULAS
            case 6 -> particulasGelo(alvo);
            // (3.95.0) DIVINO: o FX do cliente tem TRILHA propria (beacon
            // + acorde de sinos + deactivate) — o servidor so faz as
            // PARTICULAS de mundo (coroa de END_ROD subindo)
            case 7 -> particulasAscensao(alvo);
            // (3.96.0) VOID: o FX do cliente tem TRILHA propria (portal
            // do End) — o servidor so faz as PARTICULAS (portais sugados)
            case 8 -> particulasVoid(alvo);
            // (3.95.0) SANGUE: o FX do cliente tem TRILHA propria (slime +
            // splash + pingos de dripstone) — o servidor so faz as
            // PARTICULAS (espirro de bloco de redstone + poeira caindo)
            case 9 -> particulasSangue(alvo);
            // (4.5.0) EGIPICIO: o FX do cliente tem TRILHA propria (alaude
            // pentatonica) — o servidor faz a AREIA e o incenso
            case 12 -> particulasEgipcio(alvo);
            // (3.93.0) DEMONIACO: o FX do cliente tem TRILHA propria
            // (crescendo do dragao + batidas sincronizadas) — o servidor
            // so faz as PARTICULAS (pentagrama de fogo de alma no chao)
            case 10 -> particulasAbismo(alvo);
            // (3.98.0) PESADELO2 (Sono Sem Descanso): particulas do modelo
            // antigo (sculk + tinta) + Escuridao 5s (mesmo susto extra)
            case 11 -> particulasPesadelo(alvo, true);
            // (4.9.2) EXPURGO: o FX do cliente tem a TV inteira (estatica,
            // digitacao, contagem, sirene, mascaras) + TRILHA propria — o
            // servidor so faz o SANGUE de redstone caindo e a fumaca
            case 13 -> particulasExpurgo(alvo);
            // (4.9.3) CIBERPSICOSE: o FX do cliente tem a tela inteira (glitch,
            // humanidade, vidro, ECG) — o servidor faz as faiscas eletricas
            case 14 -> particulasCiberpsicose(alvo);
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
        ScreenTextPayload payload = new ScreenTextPayload(traduzido, 12, 0);   // (5.27.0) 12 s
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
                + "/tablet \u00a77\u2014 abre seu TABLET e a ficha do personagem\u00a7e\n"
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
                    // (5.25.0) a ciberpsicose como EFEITO e o vampiro (atributo + amuleto)
                    + "/rp admin ciberpsicose <jogadores> [1-600s] \u00a77\u2014 EFEITO rptag:ciberpsicose: a tela quebra do come\u00e7o ao fim\u00a7c\n"
                    + "/rp admin ciberpsicose parar <jogadores> \u00a77\u2014 tira a ciberpsicose\u00a7c\n"
                    + "/rp admin vampiro <jogadores> ligar|desligar \u00a77\u2014 o ATRIBUTO rptag:vampiro: o sol passa a queimar\u00a7c\n"
                    + "/rp admin vampiro ver <jogadores> \u00a77\u2014 mostra quem \u00e9 vampiro / est\u00e1 no sol / est\u00e1 protegido\u00a7c\n"
                    + "/rp admin eclipse dar <jogadores> \u00a77\u2014 entrega o AMULETO DO ECLIPSE (na m\u00e3o: o sol n\u00e3o queima)\u00a7c\n"
                    + "/rp admin avisar <jogadores> [estilo] <texto> \u00a77\u2014 texto na tela; estilos: classico, pesadelo, pesadelo2, runico, real, morte, inferno, gelo, divino, void, sangue, demoniaco, egipicio, expurgo, ciberpsicose, energia, conhecimento (alvos: nomes ou @a), morteop, medoop, infeccao, infeccao2\u00a7c\n"
                    + "/rp admin avisar2 <jogadores> <estilo> <texto> \u00a77\u2014 a SECAO NOVA (Lord of the Mysteries + cartas de taro; nada disso existe no avisar): louco, tolo/foulo, marinheiro/enforcado, espectador/justica, eremita, julgamento, lua, mago, morte2, sol, torre, estrela, fita\u00a7c\n"
                + "/rp admin avisozona <id> <texto> \u00a77\u2014 avisa TODO DENTRO da lorezone\u00a7c\n"
                    + "/rp admin chatlocal on|off \u00a77\u2014 chat normal por proximidade\u00a7c\n"
                    + "/rp admin balao <jogador> on|off \u00a77\u2014 libera/bloqueia o balao\u00a7c\n"
                    + "/rp admin balaolist \u00a77\u2014 quem tem balao liberado\u00a7c\n"
                    + "/rp admin canon on|off \u00a77\u2014 modo de perder vidas (todos j\u00e1 t\u00eam; quem morrer perde uma)\u00a7c\n"
                    + "/rp admin vida <jogador> tirar|dar [n] \u00a77\u2014 tira/devolve vida can\u00f4nica na hora (0..5)\u00a7c\n"
                    + "/rp admin vida <jogador> definir <n> \u00a77\u2014 define o total\u00a7c\n"
                    + "/rp admin vida <jogador> ver \u00a77\u2014 mostra quantas restam (nome vale OFFLINE)\u00a7c\n"
                    + "/rp admin vida lote A,B,C tirar 1 \u00a77\u2014 em lote, aplica na hora\u00a7c\n"
                    + "/rp admin afk tempo <s>|status \u00a77\u2014 tempo parado at\u00e9 virar AFK\u00a7c\n"
                    + "/rp admin bolhas on|off \u00a77\u2014 baloes do servidor inteiro\u00a7c\n"
                    + "/tablet ver <jogador> \u00a77\u2014 abre a ficha de outro jogador (leitura)\u00a7c\n"
                    + "/tablet liberar <jogador> \u00a77\u2014 libera a ficha confirmada para edicao\u00a7c\n"
                    + "/do <texto> \u00a77\u2014 narracao de ambiente\u00a7c\n"
                    + "/lorezone criar <id> <raio> <titulo> \u00a77\u2014 cria zona de lore onde voce esta\u00a7c\n"
                    + "/lorezone texto|som|musica <id> ... \u00a77\u2014 edits a zona (musica aceita presets e @arquivo)\u00a7c\n"
                    + "/lorezone musicas \u00a77\u2014 lista sons prontos + API de musica custom\u00a7c\n"
                    + "/lorezone listar|remover <id> \u00a77\u2014 gerencia zonas\u00a7c\n"
                    + "/effect give <jogador> rptag:medo <s> \u00a77\u2014 medo via efeito vanilla\u00a7c\n"
                    + "\u00a75\u2014\u2014 EVENTO NEPTUNE (5.4.0) \u00a77\u2014\u2014\u00a7c\n"
                    + "/terremoto iniciar [s] \u00a77\u2014 a terra colapsa (som + tremor + lentid\u00e3o em pulsos)\u00a7c\n"
                    + "/terremoto sair [jogador|todos] \u00a77\u2014 sa\u00edda em luz verde (kick em 4 s)\u00a7c\n"
                    + "/terremoto parar \u00a77\u2014 desliga o evento\u00a7c\n"
                    + "/vazio ligar|desligar \u00a77\u2014 o Deus do Vazio (raios vermelhos + trilha + medo)\u00a7c\n"
                    + "/vazio apagar|exilar <jogador> \u00a77\u2014 apaga da exist\u00eancia / manda pra dimens\u00e3o\u00a7c\n"
                    + "/bicho aqui \u00a77\u2014 caixinha de m\u00fasica (o bicho pula quando algu\u00e9m chega)\u00a7c\n"
                    + "/neptune \u00a77\u2014 resumo de todos esses comandos"), false);
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
                // ============================================ (5.25.0) CIBERPSICOSE
                // Pedido do dono: "Igual ao efeito medo, preciso criar um efeito para a
                // ciberpsicose, que assim como eu coloco medo nos personagens, eles vao sofrer
                // ciberpsicose, quero efeito mesmo".
                //   /rp admin ciberpsicose <jogadores> [segundos]   (padrao 30 s)
                //   /rp admin ciberpsicose parar <jogadores>
                // Nada de "aviso": e EFEITO (rptag:ciberpsicose) — a tela quebrada fica ligada
                // do comeco ao fim, com icone no HUD, igual o medo.
                .then(Commands.literal("ciberpsicose")
                        .then(Commands.literal("parar")
                                .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(ctx -> ciberParar(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores")))))
                        .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(ctx -> ciberAplicar(ctx,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores"), 30))
                                .then(Commands.argument("segundos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> ciberAplicar(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "segundos"))))))
                // ============================================ (5.25.0) VAMPIRO
                // Pedido do dono: "quero criar um atributo, que se eu atribuir a algum player ele
                // toma dano ao sol, como se fosse um vampiro".
                //   /rp admin vampiro <jogadores> ligar|desligar
                //   /rp admin vampiro ver <jogadores>
                .then(Commands.literal("vampiro")
                        .then(Commands.literal("ligar")
                                .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(ctx -> vampiroDefinir(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores"), true))))
                        .then(Commands.literal("desligar")
                                .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(ctx -> vampiroDefinir(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores"), false))))
                        .then(Commands.literal("ver")
                                .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(ctx -> vampiroVer(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores")))))
                        .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(ctx -> vampiroVer(ctx,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores")))))
                // ============================================ (5.25.0) AMULETO DO ECLIPSE
                .then(Commands.literal("eclipse")
                        .then(Commands.literal("dar")
                                .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(ctx -> eclipseDar(ctx,
                                                net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores"))))))
                // (4.5.0) /rp admin afk tempo <segundos> | status
                .then(Commands.literal("afk")
                        .then(Commands.literal("status").executes(ctx -> {
                            int seg = RPWorldData.get(ctx.getSource().getServer()).getAfkTimeout();
                            int n = AfkSistema.totalAfk();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "AFK: " + (seg <= 0 ? "automatico DESLIGADO" : "depois de " + seg + "s parado")
                                            + " | agora: " + n + " pessoa(s) AFK")
                                    .withStyle(ChatFormatting.GOLD), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("tempo")
                                .then(Commands.argument("segundos",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 86400))
                                        .executes(ctx -> {
                                            int seg = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "segundos");
                                            RPWorldData.get(ctx.getSource().getServer()).setAfkTimeout(seg);
                                            ctx.getSource().sendSuccess(() -> Component.literal(seg <= 0
                                                    ? "AFK automatico DESLIGADO (so /afk manual)."
                                                    : "AFK automatico: " + seg + "s parado -> sai do RP sozinho.")
                                                    .withStyle(ChatFormatting.GOLD), true);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // (4.4.0) /rp admin vida <jogador> tirar|dar [n] / definir <n> / ver
                // (4.5.0) <jogador> aceita NOME offline, lista com vírgula e @a/@p/@s
                .then(Commands.literal("vida")
                        .executes(ctx -> vidaListar(ctx))
                        .then(verbosVida())
                        .then(Commands.literal("lote")
                                .then(Commands.argument("jogador", argumentoAlvos())
                                        .then(Commands.literal("tirar")
                                                .executes(ctx -> vidaLote(ctx, "tirar", 1))
                                                .then(Commands.argument("quantia",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                                        .executes(ctx -> vidaLote(ctx, "tirar",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "quantia")))))
                                        .then(Commands.literal("dar")
                                                .executes(ctx -> vidaLote(ctx, "dar", 1))
                                                .then(Commands.argument("quantia",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                                        .executes(ctx -> vidaLote(ctx, "dar",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "quantia")))))
                                        .then(Commands.literal("definir")
                                                .then(Commands.argument("quantia",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                                                        .executes(ctx -> vidaLote(ctx, "definir",
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "quantia"))))))))
                // (3.64.0) /rp admin avisar <jogadores> [estilo] <texto>
                // (3.86.0) 10 estilos: classico, pesadelo, runico, real, morte,
                // inferno, gelo, divino, void, sangue — (3.87.0) +demoniaco
                .then(Commands.literal("avisar")
                                // (5.24.1) AQUI NAO TEM MAIS ESTILO DO avisar2.
                                // O dono: "os que tiver em rp admin avisar2 nao deve estar no avisar".
                                // Saíram daqui: louco, tolo, eremita, julgamento, lua, mago, morte2,
                                // sol, torre, estrela, marinheiro, fita e espectador — agora SÓ no
                                // /rp admin avisar2 (mesmo código de estilo, nenhum FX se perdeu).
                                // O que sobra aqui é a seção antiga: classico (sem literal), pesadelo,
                                // pesadelo2, morte, inferno, gelo, divino, void, sangue, demoniaco,
                                // runico, real, egipicio, expurgo, ciberpsicose, energia, conhecimento,
                                // morteop e medoop.
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
                                .then(Commands.literal("pesadelo2")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "pesadelo2", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("morte")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "morte", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("inferno")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "inferno", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("gelo")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "gelo", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("divino")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "divino", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("void")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "void", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("sangue")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "sangue", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("demoniaco")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "demoniaco", texto);
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
                                                })))
                                // (4.7.3) EGIPICIO: UM literal so (egipicio) — antes eu tinha
                                // posto "egipcio" tambem e o tab-complete mostrava DUAS
                                // entradas quase iguais. A grafia "egipcio" continua
                                // funcionando pelo fallback dentro de avisar().
                                .then(Commands.literal("egipicio")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "egipicio", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (4.9.2) EXPURGO: a TV de emergencia do prototipo
                                .then(Commands.literal("expurgo")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "expurgo", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (4.9.3) CIBERPSICOSE: o glitch cyberpunk (20 s, fica nas bordas)
                                .then(Commands.literal("ciberpsicose")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "ciberpsicose", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("energia")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "energia", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (5.7.0) CONHECIMENTO: o olho dourado que sabe tudo
                                .then(Commands.literal("conhecimento")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "conhecimento", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (5.7.0) ALLFORONE: as vozes na sua cabeça têm forma
                                // (5.7.9) ONEFALLALL: o protótipo do sol dourado
                                // (5.7.9) MORTEOP: espiral, relógio, crânio e cinzas
                                .then(Commands.literal("morteop")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "morteop", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (5.24.4) o SANGUEOP saiu do mod (o dono achou repetido com o SANGUE
                                // e nao gostou da arte): o /rp admin avisar nao oferece mais esse estilo.
                                // (5.8.1) MEDOOP: a membrana rachando no escuro
                                .then(Commands.literal("medoop")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "medoop", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (5.24.3) INFECCAO: veias, olho injetado, FORCA x SANIDADE (pedido 77).
                                // É do AVISAR — o dono foi claro: "no avisar nao no avisar2".
                                .then(Commands.literal("infeccao")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "infeccao", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                // (5.24.3) INFECCAO 2: a variante estilo Minecraft (o 2o protótipo do dono)
                                .then(Commands.literal("infeccao2")
                                        .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    avisar(ctx, alvos, "infeccao2", texto);
                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                // ================================================= (5.16.2) /rp admin avisar2
                // A SEÇÃO NOVA, separada da antiga: aqui ficam TODOS os avisos narrativos que o
                // dono mandou — Lord of the Mysteries (o caminho do Louco, o Tolo, o Enforcado/
                // Marinheiro, a Justiça/Espectador) e as cartas de tarô. O /rp admin avisar
                // continua existindo, igual, para os avisos antigos.
                // Uso: /rp admin avisar2 <jogadores> <estilo> <texto>
                .then(Commands.literal("avisar2")
                        .then(Commands.argument("jogadores", net.minecraft.commands.arguments.EntityArgument.players())
                                // ---- Lord of the Mysteries
                                .then(aviso2("louco"))          // 25 o Caminho do Louco (a escada 9..0)
                                .then(aviso2("tolo"))           // 36 a carta do Tolo (o Louco observa)
                                .then(aviso2("marinheiro"))     // 26 o Enforcado (tempestade)
                                .then(aviso2("enforcado"))      // 26 mesmo aviso, nome que o dono usa
                                .then(aviso2("espectador"))     // 27 a Justiça (sonho, balança, olhos)
                                .then(aviso2("justica"))        // 27 mesmo aviso, nome que o dono usa
                                // ---- as cartas de tarô
                                .then(aviso2("eremita"))        // 28 IX
                                .then(aviso2("julgamento"))     // 29 XX
                                .then(aviso2("lua"))            // 30 XVIII
                                .then(aviso2("mago"))           // 31 I
                                .then(aviso2("morte2"))         // 32 XIII (o "morte" continua o estilo antigo)
                                .then(aviso2("sol"))            // 33 XIX
                                .then(aviso2("torre"))          // 34 XVI
                                .then(aviso2("estrela"))       // 35 XVII
                                // (5.16.5) os nomes em INGLÊS saíram: eram o MESMO código dos
                                // nomes em português (mago/magician = estilo 31, e assim por diante).
                                // Só "foulo" ficou: é o jeito que o dono digitou "tolo" (outro apelido
                                // em português, não uma versão em inglês).
                                .then(aviso2("foulo"))          // o Tolo (apelido em português)
                                .then(aviso2("fita"))))         // (5.17.0) o aviso da FITA cassete
                .then(Commands.literal("avisozona")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("texto", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String zonaId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                            String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                            return avisarZona(ctx, zonaId, texto);
                                        }))))
                // (5.17.1) o /rp admin saidafx SAIU: a animação de saída no corpo é
                // padrão de todo jogador e roda sempre — não tem mais liga/desliga.
                // ============ (5.17.0) FITA CASSETE: o ADM grava a fala e entrega ============
                // /rp admin fita dar <jogadores> <texto>   -> grava a fala e entrega a fita
                // /rp admin fita gravar <texto>            -> regrava a fita que está na mão
                // /rp admin fita ler                       -> mostra a fala da fita na mão
                .then(Commands.literal("fita")
                        .then(Commands.literal("dar")
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .then(Commands.argument("texto",
                                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    var alvos = EntityArgument.getPlayers(ctx, "jogadores");
                                                    String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                                    String autor = ctx.getSource().getTextName();
                                                    int n = 0;
                                                    for (ServerPlayer alvo : alvos) {
                                                        var fita = new net.minecraft.world.item.ItemStack(RPTagMod.FITA.get());
                                                        FitaItem.gravar(fita, texto, autor);
                                                        fita.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                                                                Component.literal("\u00a7cFita de " + autor)
                                                                        .withStyle(ChatFormatting.ITALIC));
                                                        if (!alvo.getInventory().add(fita)) {
                                                            alvo.drop(fita, false);
                                                        }
                                                        n++;
                                                    }
                                                    final int quantos = n;
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "\u00a7aFita gravada e entregue para " + quantos
                                                                    + " jogador(es): \u00a77\"" + texto + "\"")
                                                            .withStyle(ChatFormatting.GREEN), false);
                                                    return Command.SINGLE_SUCCESS;
                                                }))))
                        .then(Commands.literal("gravar")
                                .then(Commands.argument("texto",
                                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String texto = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "texto");
                                            var fonte = ctx.getSource().getEntity();
                                            if (!(fonte instanceof ServerPlayer p)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Só dá pra regravar a fita que está NA MÃO de um jogador."));
                                                return 0;
                                            }
                                            var mao = p.getMainHandItem();
                                            if (!(mao.getItem() instanceof FitaItem)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "\u00a7cVocê precisa estar segurando uma fita na mão principal."));
                                                return 0;
                                            }
                                            FitaItem.gravar(mao, texto, ctx.getSource().getTextName());
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "\u00a7aFita regravada: \u00a77\"" + texto + "\"")
                                                    .withStyle(ChatFormatting.GREEN), false);
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("ler").executes(ctx -> {
                            var fonte = ctx.getSource().getEntity();
                            if (!(fonte instanceof ServerPlayer p)) {
                                ctx.getSource().sendFailure(Component.literal("Só funciona na mão de um jogador."));
                                return 0;
                            }
                            var mao = p.getMainHandItem();
                            if (!(mao.getItem() instanceof FitaItem)) {
                                ctx.getSource().sendFailure(Component.literal("\u00a7cSem fita na mão principal."));
                                return 0;
                            }
                            String f = FitaItem.fala(mao);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    f.isBlank() ? "\u00a77A fita está virgem." : "\u00a7aFala: \u00a7f\"" + f + "\"")
                                    .withStyle(ChatFormatting.GRAY), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7e/rp admin fita dar <jogadores> <texto>\u00a77 — grava e entrega\n"
                                    + "\u00a7e/rp admin fita gravar <texto>\u00a77 — regrava a fita da mão\n"
                                    + "\u00a7e/rp admin fita ler\u00a77 — mostra a fala da fita da mão")
                                    .withStyle(ChatFormatting.YELLOW), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                // ============ (5.17.0) NÚCLEO VIAL (só pra montar cena) ============
                // /rp admin vial dar <jogadores> [carga]
                // /rp admin vial carga <jogadores> <0..100> [ativo]
                .then(Commands.literal("vial")
                        .then(Commands.literal("dar")
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .executes(ctx -> vialDar(ctx, EntityArgument.getPlayers(ctx, "jogadores"), 100, false))
                                        .then(Commands.argument("carga",
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> vialDar(ctx, EntityArgument.getPlayers(ctx, "jogadores"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "carga"), false)))))
                        .then(Commands.literal("carga")
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .then(Commands.argument("valor",
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> vialCarga(ctx,
                                                        EntityArgument.getPlayers(ctx, "jogadores"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "valor")))
                                                .then(Commands.literal("ativo").executes(ctx -> vialCargaAtivo(ctx,
                                                        EntityArgument.getPlayers(ctx, "jogadores"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "valor"), true)))
                                                .then(Commands.literal("parado").executes(ctx -> vialCargaAtivo(ctx,
                                                        EntityArgument.getPlayers(ctx, "jogadores"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "valor"), false))))))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7e/rp admin vial dar <jogadores> [carga]\u00a77 — entrega um núcleo\n"
                                    + "\u00a7e/rp admin vial carga <jogadores> <0..100> [ativo|parado]\u00a77 — crava a carga")
                                    .withStyle(ChatFormatting.YELLOW), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                // ============ (5.17.0) BARRA DE PROGRESSÃO DO HUD ============
                // /rp admin barra <jogadores> <0..100> [rotulo]
                // /rp admin barra cor [<parte>] [<cor>]  (5.24.2: paleta clicável + cor por nome)
                // /rp admin barra titulo <texto> | onde <x> <y> | largura <px> | off | info
                .then(Commands.literal("barra")
                        .then(Commands.literal("vertical")
                                .then(Commands.literal("on").executes(ctx -> barraVertical(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> barraVertical(ctx, false)))
                                .executes(ctx -> barraVertical(ctx,
                                        RPWorldData.get(ctx.getSource().getServer()).barraVertical())))
                        .then(Commands.literal("off").executes(ctx -> barraOff(ctx)))
                        .then(Commands.literal("info").executes(ctx -> barraInfo(ctx)))
                        // (5.24.2) COR FACIL: /rp admin barra cor  -> paleta CLICAVEL
                        //          /rp admin barra cor <cor>     -> a barra inteira naquela cor
                        //          /rp admin barra cor <parte> <cor> -> controle fino (rotulo|barra|fundo)
                        .then(Commands.literal("cor")
                                .executes(ctx -> barraPaleta(ctx))
                                .then(Commands.argument("a", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests(sugereCor())
                                        .executes(ctx -> barraCorFacil(ctx,
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "a"), null))
                                        .then(Commands.argument("b", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests(sugereCor())
                                                .executes(ctx -> barraCorFacil(ctx,
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "a"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "b"))))))
                        .then(Commands.literal("titulo")
                                .then(Commands.argument("rotulo", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> barraTitulo(ctx,
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "rotulo")))))
                        .then(Commands.literal("onde")
                                .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1))
                                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1))
                                                .executes(ctx -> barraOnde(ctx,
                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "x"),
                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "y"))))))
                        .then(Commands.literal("largura")
                                .then(Commands.argument("px", com.mojang.brigadier.arguments.IntegerArgumentType.integer(40, 400))
                                        .executes(ctx -> barraLargura(ctx,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "px")))))
                        .executes(ctx -> barraInfo(ctx))
                        // (5.17.1) A FORMA SIMPLES: /rp admin barra 10 ANALISE
                        // (vai pra todo mundo; é essa que o dono pediu: "para o adm setar até a cor")
                        .then(Commands.argument("porcento", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> barraTodos(ctx,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "porcento"), null))
                                .then(Commands.argument("frase", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> barraTodos(ctx,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "porcento"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "frase")))))
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .then(Commands.argument("valor", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> barraValor(ctx, EntityArgument.getPlayers(ctx, "jogadores"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "valor")))
                                        .then(Commands.argument("rotulo", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String rot = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "rotulo");
                                                    RPWorldData.get(ctx.getSource().getServer()).setBarraRotulo(rot);
                                                    return barraValor(ctx, EntityArgument.getPlayers(ctx, "jogadores"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "valor"));
                                                })))))
                // ============ (5.18.0) AXION: DOIS jeitos de COLOCAR e UM de TIRAR =========
                // Ela NUNCA aparece sozinha (nem quando o jogador entra pela primeira vez).
                //   acordar -> COLOCA ela na tela, ACORDADA
                //   dormir  -> COLOCA ela na tela, DORMINDO
                //   tirar   -> TIRA ela da tela (volta a ficar escondida)
                // (5.42.0) O dono: "no comando acordar, tenha a opção de selecionar o acordar
                // eduujgaa por exemplo, para saber em qual player vai acordar, senão vai
                // aparecer na tela de todo mundo". Então acordar/dormir/tirar aceitam
                // OPCIONALMENTE de quem são: sem o nome, é pra todo mundo (como era); com o
                // nome, a Axion aparece SÓ na tela daquele jogador.
                .then(Commands.literal("axion")
                        .then(Commands.literal("acordar")
                                .executes(ctx -> axionEstado(ctx, AxionPayload.ACORDADA))
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .executes(ctx -> axionSoPara(ctx, AxionPayload.ACORDADA))))
                        .then(Commands.literal("dormir")
                                .executes(ctx -> axionEstado(ctx, AxionPayload.DORMINDO))
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .executes(ctx -> axionSoPara(ctx, AxionPayload.DORMINDO))))
                        .then(Commands.literal("tirar")
                                .executes(ctx -> axionEstado(ctx, AxionPayload.ESCONDIDA))
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .executes(ctx -> axionSoPara(ctx, AxionPayload.ESCONDIDA))))
                        // (5.24.0) de quem é a Axion (o balão dela só aparece pra essa pessoa)
                        .then(Commands.literal("dono")
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .executes(ctx -> axionDono(ctx, EntityArgument.getPlayer(ctx, "jogador"))))
                                .then(Commands.literal("nenhum").executes(ctx -> {
                                    var srv = ctx.getSource().getServer();
                                    RPWorldData d = RPWorldData.get(srv);
                                    d.setAxionDono(null, null);
                                    for (ServerPlayer alvo : srv.getPlayerList().getPlayers()) {
                                        enviarAxion(alvo, d);
                                    }
                                    return axionDono(ctx, null);
                                }))
                                .executes(ctx -> axionDono(ctx, null)))
                        // (5.20.0) /rp admin axion falar <texto> — BALÃO DE FALA:
                        // vermelho escuro com letras pretas, saindo dela na tela de todos.
                        .then(Commands.literal("falar")
                                .then(Commands.argument("texto",
                                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(ctx -> axionFalar(ctx,
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(
                                                        ctx, "texto")))))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7e/rp admin axion acordar\u00a77 \u2014 POE ela na tela, acordada\n"
                                    + "\u00a7e/rp admin axion dormir\u00a77 \u2014 POE ela na tela, dormindo\n"
                                    + "\u00a7e/rp admin axion tirar\u00a77 \u2014 TIRA ela da tela\n"
                                    + "\u00a7e/rp admin axion dono <jogador>\u00a77 \u2014 de quem ela é "
                                    + "(o balão dela só aparece pra essa pessoa)\n"
                                    + "\u00a7e/rp admin axion falar <texto>\u00a77 \u2014 ela diz isso "
                                    + "num balão vermelho de letras pretas, só na tela do dono")
                                    .withStyle(ChatFormatting.YELLOW), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                // (5.10.1) /rp admin titlevanilla on|off — o /title é comando VANILLA
                // (não é do mod). Padrão: sem ele, só o /titulo do RP Tag.
                // (5.18.0) o /rp admin titlevanilla SAIU (o dono: "tira o title vanila nao
                // gostei disso somento o bonito") — o /title do vanilla continua fora, sempre.
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
                // (5.18.0) o /rp admin bolha SAIU: era o mesmo que o balao/balaolist
                // (o dono: "ja tem o balao, é comando duplicado basicamente").
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
                                    sb.append(p != null ? (CharSequence) p.getName().getString() : (CharSequence) id.toString().substring(0, 8));
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

        // (5.24.0) /barra [on|off] — o jogador tira a barra da PRÓPRIA tela (todo mundo pode)
        event.getDispatcher().register(Commands.literal("barra")
                .then(Commands.literal("on").executes(ctx -> barraDoJogador(ctx, Boolean.TRUE)))
                .then(Commands.literal("off").executes(ctx -> barraDoJogador(ctx, Boolean.FALSE)))
                .executes(ctx -> barraDoJogador(ctx, null)));

        // (4.5.0) /afk — entra/sai do AFK na mão (sai do RP sozinho)
        event.getDispatcher().register(Commands.literal("afk").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            AfkSistema.alternar(p);
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }

    // ===================================================================================
    // (5.17.0) FITA / NÚCLEO VIAL / BARRA / AXION — os ajudantes dos comandos novos
    // ===================================================================================

    private static int vialDar(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, int carga, boolean ativo) {
        int n = 0;
        long dia = ctx.getSource().getServer().overworld().getDayTime() / 24000L;
        for (ServerPlayer alvo : alvos) {
            var item = new net.minecraft.world.item.ItemStack(
                    carga <= 0 ? RPTagMod.NUCLEO_VIAL_QUEBRADO.get() : RPTagMod.NUCLEO_VIAL.get());
            NucleoVialItem.definirCarga(item, Math.max(0, Math.min(100, carga)), ativo, dia);
            if (!alvo.getInventory().add(item)) {
                alvo.drop(item, false);
            }
            n++;
        }
        final int quantos = n;
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aNúcleo vial (carga " + carga + "%) "
                + (ativo ? "\u00a7cATIVO" : "\u00a77adormecido") + "\u00a7a entregue para " + quantos
                + " jogador(es).").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int vialCarga(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, int valor) {
        return vialCargaAtivo(ctx, alvos, valor, false);
    }

    private static int vialCargaAtivo(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, int valor, boolean ativo) {
        long dia = ctx.getSource().getServer().overworld().getDayTime() / 24000L;
        int mudados = 0;
        for (ServerPlayer alvo : alvos) {
            var inv = alvo.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var st = inv.getItem(i);
                if (!st.isEmpty() && st.getItem() instanceof NucleoVialItem) {
                    NucleoVialItem.definirCarga(st, Math.max(0, Math.min(100, valor)), ativo, dia);
                    mudados++;
                }
            }
            inv.setChanged();
        }
        final int m = mudados;
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aCarga cravada em " + valor + "%"
                + (ativo ? " \u00a7c(ATIVO)" : "") + "\u00a7a — " + m + " núcleo(s) atualizado(s).")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraValor(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            java.util.Collection<ServerPlayer> alvos, int valor) {
        var dados = RPWorldData.get(ctx.getSource().getServer());
        dados.setBarraValor(valor);
        dados.setBarraVisivel(true);
        for (ServerPlayer alvo : alvos) {
            enviarBarra(alvo, dados);
        }
        final int v = valor;
        final int n = alvos.size();
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aBarra em \u00a7f" + v + "%\u00a7a para "
                + n + " jogador(es)  \u00a78(" + dados.barraRotulo() + ")").withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.17.1) a forma simples: {@code /rp admin barra <porcento> [frase]} para TODO mundo. */
    private static int barraTodos(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            int valor, String frase) {
        var dados = RPWorldData.get(ctx.getSource().getServer());
        dados.setBarraValor(valor);
        if (frase != null && !frase.isBlank()) {
            dados.setBarraRotulo(frase);
        }
        dados.setBarraVisivel(true);
        var online = ctx.getSource().getServer().getPlayerList().getPlayers();
        for (ServerPlayer alvo : online) {
            enviarBarra(alvo, dados);
        }
        final int v = valor;
        final String r = dados.barraRotulo();
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aBarra: \u00a7f" + r + " \u00a7a\u2192 \u00a7f"
                + v + "%\u00a7a  \u00a78(na tela de " + online.size() + " jogador(es); off esconde)")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraOff(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        var dados = RPWorldData.get(ctx.getSource().getServer());
        dados.setBarraVisivel(false);
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, dados);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a77Barra ESCONDIDA para todo mundo.")
                .withStyle(ChatFormatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraInfo(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7eBARRA DO HUD\u00a77\n"
                + "  visível: " + (d.barraVisivel() ? "\u00a7asim" : "\u00a7cnão") + "\u00a77\n"
                + "  rótulo: \u00a7f" + d.barraRotulo() + "\u00a77\n"
                + "  valor: \u00a7f" + d.barraValor() + "%\u00a77\n"
                + "  formato: \u00a7f" + (d.barraVertical() ? "VERTICAL" : "horizontal") + "\u00a77 (barra vertical on|off)\n"
                + "  cor do texto: \u00a7f#" + String.format("%06X", d.barraCorTexto() & 0xFFFFFF) + "\u00a77\n"
                + "  cor da barra: \u00a7f#" + String.format("%06X", d.barraCorBarra() & 0xFFFFFF) + "\u00a77\n"
                + "  cor do fundo: \u00a7f#" + String.format("%06X", d.barraCorFundo() & 0xFFFFFF) + "\u00a77\n"
                + "  posição: \u00a7f" + String.format(java.util.Locale.ROOT, "%.2f, %.2f", d.barraX(), d.barraY())
                + "\u00a77  largura: \u00a7f" + d.barraLargura()
                + "\n\u00a7eMudar a cor:\u00a77 \u00a7f/rp admin barra cor\u00a77 (paleta pra clicar) "
                + "ou \u00a7f/rp admin barra cor vermelho").withStyle(ChatFormatting.YELLOW), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraCor(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String parte, String hex) {
        // (5.18.0) COR POR NOME — o dono: "SETE POR NOME DE COR E NAO POR CODIGO".
        // Aceita vermelho, verde, azul... e, para quem quiser, o #RRGGBB de sempre.
        int cor = corPorNome(hex);
        if (cor < 0) {
            ctx.getSource().sendFailure(Component.literal("\u00a7cCor inválida. Use um NOME: "
                    + "\u00a7fvermelho, vinho, laranja, amarelo, dourado, verde, esmeralda, ciano, "
                    + "azul, anil, roxo, lilas, rosa, branco, cinza, preto\u00a7c (ou #RRGGBB)."));
            return 0;
        }
        var d = RPWorldData.get(ctx.getSource().getServer());
        String qual;
        switch (parte.toLowerCase(java.util.Locale.ROOT)) {
            case "rotulo", "texto", "titulo" -> { d.setBarraCorTexto(cor); qual = "do RÓTULO"; }
            case "barra", "fill", "progresso" -> { d.setBarraCorBarra(cor); qual = "da BARRA"; }
            case "fundo", "background" -> { d.setBarraCorFundo(cor); qual = "do FUNDO"; }
            default -> {
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cUse: rotulo | barra | fundo"));
                return 0;
            }
        }
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, d);
        }
        final String q = qual;
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aCor " + q + " agora é \u00a7f#"
                + String.format("%06X", cor)).withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * (5.18.0) NOME DE COR -> RGB. O dono: <i>"mais facil de ver cores, sete por nome de cor e
     * nao por codigo"</i>. Aceita também {@code #RRGGBB} pra quem gosta de código.
     *
     * @return o RGB, ou -1 quando o nome não existe.
     */
    /** (5.24.2) os nomes de cor que aparecem no TAB e na paleta (a ordem é a da paleta). */
    public static final String[] NOMES_COR = {
        "vermelho", "vinho", "laranja", "amarelo", "dourado", "verde", "esmeralda", "lima",
        "ciano", "azul", "azulclaro", "anil", "roxo", "lilas", "rosa", "branco", "cinza", "preto"
    };

    /** (5.24.2) as partes que dá pra pintar separado. */
    private static final String[] PARTES = { "barra", "rotulo", "fundo" };

    /** (5.24.2) TAB das cores: sugere os nomes de cor E as partes. */
    private static com.mojang.brigadier.suggestion.SuggestionProvider<
            net.minecraft.commands.CommandSourceStack> sugereCor() {
        return (ctx, b) -> {
            for (String p : PARTES) {
                b.suggest(p);
            }
            for (String c : NOMES_COR) {
                b.suggest(c);
            }
            return b.buildFuture();
        };
    }

    /** (5.24.2) esta palavra é uma das partes (barra/rotulo/fundo)? */
    private static boolean ehParte(String t) {
        if (t == null) {
            return false;
        }
        String n = t.trim().toLowerCase(java.util.Locale.ROOT);
        return n.equals("barra") || n.equals("fill") || n.equals("progresso")
                || n.equals("rotulo") || n.equals("texto") || n.equals("titulo")
                || n.equals("fundo") || n.equals("background");
    }

    /**
     * (5.24.2) A COR FACIL — pedido do dono: <i>"deixa o jeito de mudar a cor da barra de
     * progressao mais facil dos adm mudar, ta muito dificil atualmente"</i>.
     *
     * <p>Antes era preciso saber que existiam três partes e escrever
     * {@code /rp admin barra cor barra vermelho}. Agora:
     * <ul>
     *   <li>{@code /rp admin barra cor} — abre a <b>paleta clicável</b> no chat: clica no nome
     *       da cor e a barra muda na hora (sem digitar mais nada);</li>
     *   <li>{@code /rp admin barra cor vermelho} — <b>uma palavra</b> pinta a barra inteira
     *       (a barra, o fundo e a letra já saem combinando);</li>
     *   <li>{@code /rp admin barra cor barra vermelho} — o jeito antigo continua, para quem
     *       quer pintar só uma parte.</li>
     * </ul>
     * E o TAB agora sugere os nomes de cor e as partes.
     */
    private static int barraCorFacil(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String a, String b) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var d = RPWorldData.get(ctx.getSource().getServer());
        // jeito A: /barra cor <cor>            -> a barra inteira
        if (b == null) {
            int cor = corPorNome(a);
            if (cor < 0) {
                if (ehParte(a)) {
                    ctx.getSource().sendFailure(Component.literal("\u00a7cFalta a cor. Exemplo: \u00a7f/rp admin barra cor "
                            + a + " vermelho\u00a7c — ou clique numa cor na paleta: \u00a7f/rp admin barra cor"));
                    return 0;
                }
                return barraCorInvalida(ctx);
            }
            return pintarBarraInteira(ctx, d, cor);
        }
        // jeito B: aceita a ordem invertida (/barra cor vermelho barra)
        String parte = a, nomeCor = b;
        if (!ehParte(a) && ehParte(b)) {
            parte = b;
            nomeCor = a;
        }
        return barraCor(ctx, parte, nomeCor);          // jeito antigo (parte + cor)
    }

    /** (5.24.2) pinta a barra INTEIRA de uma vez (barra + fundo + letra combinando). */
    private static int pintarBarraInteira(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            RPWorldData d, int cor) {
        d.setBarraCorBarra(cor);
        d.setBarraCorFundo(escurecer(cor, 0.30F));      // o fundo vira a mesma cor, bem escura
        d.setBarraCorTexto(textoSobre(cor));            // letra clara em fundo escuro e vice-versa
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, d);
        }
        final String nome = nomeDaCor(cor);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aBarra pintada de \u00a7f" + nome
                + "\u00a7a (barra, fundo e letra já combinando). \u00a78Fina o detalhe com: "
                + "/rp admin barra cor <barra|rotulo|fundo> <cor>"), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.24.2) A PALETA — cada cor é um botão: clicou, pintou. */
    private static int barraPaleta(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        var fonte = ctx.getSource();
        fonte.sendSuccess(() -> Component.literal("\u00a78\u00a7m          \u00a7r \u00a7eCOR DA BARRA \u00a78\u00a7m          "), false);
        fonte.sendSuccess(() -> Component.literal("\u00a77Clique numa cor para pintar a barra inteira:"), false);
        Component linha = Component.literal("");
        int naLinha = 0;
        for (String nome : NOMES_COR) {
            int cor = corPorNome(nome);
            String hex = String.format("%06X", cor);
            Component botao = Component.literal("\u00a78[\u00a7r")
                    .append(Component.literal("\u25A0 " + nome).withStyle(st -> st
                            .withColor(cor & 0xFFFFFF)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/rp admin barra cor " + nome))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("\u00a77pintar de \u00a7f" + nome + "\u00a78 (#" + hex + ")")))))
                    .append(Component.literal("\u00a78] "));
            linha = linha.copy().append(botao);
            naLinha++;
            if (naLinha % 3 == 0) {
                final Component l = linha;
                fonte.sendSuccess(() -> l, false);
                linha = Component.literal("");
            }
        }
        if (naLinha % 3 != 0) {
            final Component l = linha;
            fonte.sendSuccess(() -> l, false);
        }
        int[] atuais = { d.barraCorBarra(), d.barraCorTexto(), d.barraCorFundo() };
        fonte.sendSuccess(() -> Component.literal("\u00a78Agora: barra \u00a7f#" + String.format("%06X", atuais[0])
                + " \u00a78· letra \u00a7f#" + String.format("%06X", atuais[1])
                + " \u00a78· fundo \u00a7f#" + String.format("%06X", atuais[2])), false);
        fonte.sendSuccess(() -> Component.literal("\u00a78Só uma parte? \u00a7f/rp admin barra cor barra|rotulo|fundo <cor>"
                + " \u00a78· \u00a7f/rp admin barra info\u00a78 mostra tudo"), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.24.2) a cor não existe: ensina o caminho mais fácil. */
    private static int barraCorInvalida(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ctx.getSource().sendFailure(Component.literal("\u00a7cEssa cor não existe. Use uma destas (ou #RRGGBB):\n"
                + "\u00a7f" + String.join(", ", NOMES_COR) + "\n"
                + "\u00a78Dica: \u00a7f/rp admin barra cor\u00a78 abre a paleta pra clicar."));
        return 0;
    }

    /** (5.24.2) escurece uma cor (0 = preto, 1 = a própria cor). */
    private static int escurecer(int cor, float fator) {
        int r = (int) (((cor >> 16) & 0xFF) * fator);
        int g = (int) (((cor >> 8) & 0xFF) * fator);
        int b = (int) ((cor & 0xFF) * fator);
        return (Math.max(0, r) << 16) | (Math.max(0, g) << 8) | Math.max(0, b);
    }

    /** (5.24.2) letra que dá pra ler em cima desta cor (clara em fundo escuro, escura em fundo claro). */
    private static int textoSobre(int cor) {
        int r = (cor >> 16) & 0xFF, g = (cor >> 8) & 0xFF, b = cor & 0xFF;
        double luz = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luz > 0.6 ? 0x141018 : 0xF2F4F8;
    }

    /** (5.24.2) o nome mais próximo de uma cor (pra confirmar no chat). */
    private static String nomeDaCor(int cor) {
        for (String nome : NOMES_COR) {
            if (corPorNome(nome) == cor) {
                return nome;
            }
        }
        return "#" + String.format("%06X", cor);
    }

    public static int corPorNome(String nome) {
        if (nome == null) {
            return -1;
        }
        String n = nome.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("á", "a").replace("ã", "a").replace("â", "a")
                .replace("é", "e").replace("ê", "e")
                .replace("í", "i").replace("ó", "o").replace("ô", "o")
                .replace("ú", "u").replace("ç", "c");
        if (n.startsWith("#") || n.startsWith("0x")) {
            try {
                return Integer.parseInt(n.replace("#", "").replace("0x", "").replace(" ", ""), 16) & 0xFFFFFF;
            } catch (Exception e) {
                return -1;
            }
        }
        return switch (n) {
            case "vermelho", "red" -> 0xC81E2E;
            case "vinho", "sangue", "bordo" -> 0x6B0F1A;
            case "laranja", "orange" -> 0xE07A1F;
            case "amarelo", "yellow" -> 0xE8C63A;
            case "dourado", "ouro", "gold" -> 0xD4A017;
            case "verde", "green" -> 0x3FA24A;
            case "esmeralda" -> 0x12B886;
            case "verdeclaro", "lima", "lime" -> 0x8FE06A;
            case "ciano", "turquesa", "cyan" -> 0x2FB9C6;
            case "azul", "blue" -> 0x2F6FD0;
            case "anil", "azulmarinho", "marinho" -> 0x1B2A63;
            case "azulclaro", "ceu" -> 0x7FB6E8;
            case "roxo", "purple" -> 0x7A3FD0;
            case "lilas", "violeta" -> 0xB48CE8;
            case "rosa", "pink" -> 0xE06AA8;
            case "branco", "white" -> 0xF2F4F8;
            case "cinza", "gray", "grey" -> 0x8A8F99;
            case "cinzaescuro" -> 0x4A4E57;
            case "preto", "black" -> 0x141018;
            default -> -1;
        };
    }

    /** (5.18.0) /rp admin barra vertical on|off — a barra em pé (padrão) ou deitada. */
    private static int barraVertical(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            boolean on) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        d.setBarraVertical(on);
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, d);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "\u00a7aBarra na VERTICAL (em pé) \u2014 enche de baixo pra cima."
                : "\u00a7aBarra na HORIZONTAL (deitada) \u2014 enche da esquerda pra direita.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraTitulo(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String rotulo) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        d.setBarraRotulo(rotulo);
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, d);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aRótulo da barra: \u00a7f" + rotulo)
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraOnde(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            double x, double y) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        d.setBarraPos((float) x, (float) y);
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, d);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aBarra movida para "
                + String.format(java.util.Locale.ROOT, "%.2f, %.2f", x, y)
                + " \u00a78(0,0 = canto superior esquerdo; 1,1 = canto inferior direito)")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int barraLargura(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            int px) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        d.setBarraLargura(px);
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarBarra(alvo, d);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aLargura da barra: \u00a7f" + px + " px")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.17.0) manda o estado inteiro da barra pra um jogador. */
    public static void enviarBarra(ServerPlayer alvo, RPWorldData d) {
        // (5.24.0) quem pediu pra tirar a barra com /barra off não recebe ela de volta quando
        // o ADM mexe na barra de todo mundo — a escolha do jogador ganha.
        boolean visivel = d.barraVisivel() && !d.barraEscondida(alvo.getUUID());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo,
                new BarraPayload(visivel, d.barraRotulo(), d.barraValor(), d.barraCorTexto(),
                        d.barraCorBarra(), d.barraCorFundo(), d.barraX(), d.barraY(), d.barraLargura(),
                        d.barraVertical()));
    }

    /**
     * (5.24.0) /barra [on|off] — O JOGADOR TIRA A BARRA DA PRÓPRIA TELA.
     *
     * <p>O dono: <i>"no mod tem que ter um comando para tirar a barra da tela do player tbm, vai
     * que ele nao quer mais e tals"</i>. Sem argumento, alterna (liga/desliga). A escolha fica
     * guardada no save do mundo, então continua valendo quando ele voltar pro servidor.
     */
    private static int barraDoJogador(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            Boolean quer) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        RPWorldData d = RPWorldData.get(ctx.getSource().getServer());
        boolean esconder = (quer == null) ? !d.barraEscondida(p.getUUID()) : !quer;
        d.setBarraEscondida(p.getUUID(), esconder);
        enviarBarra(p, d);                    // manda o estado novo (escondida ou não) na hora
        String msg = esconder
                ? "\u00a77Barra do HUD \u00a7cfora da sua tela\u00a77. Pra ela voltar: \u00a7f/barra on"
                : "\u00a77Barra do HUD \u00a7ade volta na sua tela\u00a77.";
        if (esconder && !d.barraVisivel()) {
            msg = "\u00a77A barra do HUD já está desligada pro servidor inteiro \u2014 "
                    + "quando o ADM ligar, ela continua fora da sua tela.";
        }
        final String m = msg;
        ctx.getSource().sendSuccess(() -> Component.literal(m), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * (5.20.0) A AXION FALA — manda o balão pra todo mundo.
     *
     * <p>O dono: <i>"sera que teria alguma forma de ter um balao de fala vermelinho com letras
     * petras, que saia da axion? ... seria legal para o adm falar comigo"</i>. O balão é
     * desenhado colado nela, no canto da tela, e some sozinho (8 s).
     */
    private static int axionFalar(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String texto) {
        if (texto == null || texto.isBlank()) {
            ctx.getSource().sendFailure(Component.literal("\u00a7cEscreva o que ela vai falar."));
            return 0;
        }
        // tira os códigos § que o ADM talvez digite: o balão é sempre vermelho com letra preta
        String limpo = texto.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
        var server = ctx.getSource().getServer();
        RPWorldData d = RPWorldData.get(server);

        // (5.24.0) ELA FALA SÓ NA TELA DE QUEM TEM ELA.
        // O dono: "a axion nao fala no botao de tela de todos, e apenas na tela do personagem
        // tal que tem a axion". Então o balão vai para o DONO dela (definido com
        // /rp admin axion dono <jogador>) e para mais ninguém. Sem dono escolhido, a fala cai
        // na tela de quem mandou o comando (e o console é avisado de como escolher o dono).
        ServerPlayer destinatario = null;
        if (d.axionDono() != null) {
            destinatario = server.getPlayerList().getPlayer(d.axionDono());
            if (destinatario == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cA Axion é de \u00a7f" + d.axionDonoNome()
                                + "\u00a7c, mas ele está offline agora \u2014 o balão espera ele voltar."));
                return 0;
            }
        } else {
            destinatario = ctx.getSource().getPlayer();     // quem mandou (se for jogador)
            if (destinatario == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cA Axion não tem dono. Escolha primeiro: \u00a7f/rp admin axion dono <jogador>"));
                return 0;
            }
        }
        // ela precisa estar na tela pra falar
        if (d.axionEstado() == AxionPayload.ESCONDIDA) {
            d.setAxionEstado(AxionPayload.ACORDADA);
            for (ServerPlayer alvo : server.getPlayerList().getPlayers()) {
                enviarAxion(alvo, d);
            }
        }
        final ServerPlayer quem = destinatario;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(quem,
                new AxionFalaPayload(limpo, 8));
        // (5.42.0) O SOM. O dono: "tira esse som da axion ao falar, coloca algo tecnologia e
        // curto". Antes era o SOM_VAZIO (grave, longo, arrastado). Agora são dois bips de
        // chiptune (NOTE_BLOCK_BIT) colados — um "bip-bop" de terminal, que dura ~0,2 s.
        quem.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.42F, 1.45F);
        quem.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.34F, 2.05F);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7aAxion fala: \u00a74\u00a7l\"" + limpo + "\"\u00a7r \u00a77(balão só na tela de \u00a7f"
                        + quem.getName().getString() + "\u00a77)")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * (5.24.0) /rp admin axion dono [jogador] — QUEM TEM A AXION.
     *
     * <p>Com um jogador: ela passa a morar na tela dele (e a falar só ali). Sem argumento:
     * mostra quem é o dono atual. Com {@code nenhum}: solta (volta a ser a Axion do servidor).
     */
    private static int axionDono(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            ServerPlayer novo) {
        var server = ctx.getSource().getServer();
        RPWorldData d = RPWorldData.get(server);
        if (novo != null) {
            d.setAxionDono(novo.getUUID(), novo.getName().getString());
            for (ServerPlayer alvo : server.getPlayerList().getPlayers()) {
                enviarAxion(alvo, d);         // ela agora aparece só pra ele
            }
        }
        java.util.UUID id = d.axionDono();
        String nome = "\u00a78ninguém (ela é do servidor inteiro)";
        if (id != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            String n = p != null ? p.getName().getString() : d.axionDonoNome();
            if (n == null || n.isBlank()) {
                n = id.toString().substring(0, 8);
            }
            nome = "\u00a7f" + n;
        }
        final String quem = nome;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7aA Axion é de: " + quem + "\u00a78 — o balão dela só aparece na tela dessa pessoa.")
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }


    private static int axionEstado(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            int estado) {
        var d = RPWorldData.get(ctx.getSource().getServer());
        d.setAxionEstado(estado);
        for (ServerPlayer alvo : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            enviarAxion(alvo, d);
        }
        String nome = estado == AxionPayload.ACORDADA ? "\u00a7cna TELA \u00a77(acordada)"
                : estado == AxionPayload.DORMINDO ? "\u00a77na TELA (dormindo)"
                : "\u00a78fora da tela \u00a77(escondida)";
        ctx.getSource().sendSuccess(() -> Component.literal("\u00a7aAxion: " + nome)
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.42.0) quem recebeu a Axion por NOME (a tela é só desses, não do servidor todo). */
    private static final java.util.Set<java.util.UUID> AXION_SO_PARA =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** (5.42.0) marca que a Axion daquele jogador é "só dele" (usado pelo /acordar). */
    public static void marcarAxionSoPara(ServerPlayer p) {
        if (p != null) {
            AXION_SO_PARA.add(p.getUUID());
        }
    }

    /**
     * (5.42.0) {@code /rp admin axion acordar <jogadores>} — a Axion aparece <b>só</b> na tela
     * de quem foi escolhido.
     *
     * <p>Sem escolher ninguém o estado é do mundo inteiro (era assim e continua assim); com o
     * nome, o pacote vai <b>direto</b> pra aquela pessoa e a lista fica guardada, pra ninguém
     * mais receber a Axion por tabela numa reconexão ou troca de dimensão.
     */
    private static int axionSoPara(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            int estado) {
        java.util.Collection<ServerPlayer> alvos;
        try {
            alvos = EntityArgument.getPlayers(ctx, "jogadores");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("\u00a7cJogador n\u00e3o encontrado."));
            return 0;
        }
        var d = RPWorldData.get(ctx.getSource().getServer());
        for (ServerPlayer alvo : alvos) {
            AXION_SO_PARA.add(alvo.getUUID());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo,
                    new AxionPayload(estado, d.axionEscala(), 0.995F, 0.995F));
        }
        String nome = estado == AxionPayload.ACORDADA ? "\u00a7cACORDADA \u00a77(na tela)"
                : estado == AxionPayload.DORMINDO ? "\u00a77DORMINDO (na tela)"
                : "\u00a78fora da tela";
        String quem = String.join(", ", alvos.stream().map(p -> p.getName().getString()).toList());
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "\u00a7aAxion " + nome + " \u00a77s\u00f3 para \u00a7f" + quem)
                .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.17.0) manda o estado da Axion pra um jogador. */
    public static void enviarAxion(ServerPlayer alvo, RPWorldData d) {
        // (5.42.0) quem recebeu a Axion por NOME não é atropelado pelo estado global
        if (AXION_SO_PARA.contains(alvo.getUUID())) {
            return;
        }
        // (5.24.0) ela mora com UMA pessoa: quem não é o dono recebe "escondida".
        int estado = d.axionEstado();
        if (d.axionDono() != null && !d.axionDono().equals(alvo.getUUID())) {
            estado = AxionPayload.ESCONDIDA;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo,
                new AxionPayload(estado, d.axionEscala(), 0.995F, 0.995F));
    }

}
