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
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(online, new ScreenTextPayload(msg, 8, 4));
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
                    "VOCE GANHOU UMA NOVA CHANCE \u2014 uma vida canonica foi restaurada (" + depois + "/5)", 6, 0));
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

    /** (3.64.0) Mostra o texto GRANDE na tela dos alvos. ESTILOS:
     * 0 classico (caixa de fala, cores &), 1 pesadelo (veu preto + sangue),
     * 2 runico (veu arcano + runas), 3 real (azul-noite + ouro),
     * 4 morte (letras retas de sangue), 5 inferno (brasas), 6 gelo
     * (cristais), 7 divino (feixes de luz), 8 void (estrelas),
     * 9 sangue (gotas escorrendo). (3.86.0) +6 estilos novos.
     * Aceita VARIOS jogadores (nomes ou seletor @a — "avisar alguns players"). */
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
            default -> 0;
        };
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
                    + "/rp admin avisar <jogadores> [estilo] <texto> \u00a77\u2014 texto na tela; estilos: classico, pesadelo, pesadelo2, runico, real, morte, inferno, gelo, divino, void, sangue, demoniaco, egipicio (alvos: nomes ou @a)\u00a7c\n"
                + "/rp admin avisozona <id> <texto> \u00a77\u2014 avisa TODO DENTRO da lorezone\u00a7c\n"
                    + "/rp admin chatlocal on|off \u00a77\u2014 chat normal por proximidade\u00a7c\n"
                    + "/rp admin bolha <jogador> on|off \u00a77\u2014 modo balao de outro\u00a7c\n"
                    + "/rp admin balao <jogador> on|off \u00a77\u2014 libera/bloqueia o balao\u00a7c\n"
                    + "/rp admin balaolist \u00a77\u2014 quem tem balao liberado\u00a7c\n"
                    + "/rp admin canon on|off \u00a77\u2014 modo de perder vidas (todos j\u00e1 t\u00eam; quem morrer perde uma)\u00a7c\n"
                    + "/rp admin vida <jogador> tirar|dar [n] \u00a77\u2014 tira/devolve vida can\u00f4nica na hora (0..5)\u00a7c\n"
                    + "/rp admin vida <jogador> definir <n> \u00a77\u2014 define o total\u00a7c\n"
                    + "/rp admin vida <jogador> ver \u00a77\u2014 mostra quantas restam (nome vale OFFLINE)\u00a7c\n"
                    + "/rp admin vida lote A,B,C tirar 1 \u00a77\u2014 em lote, aplica na hora\u00a7c\n"
                    + "/rp admin afk tempo <s>|status \u00a77\u2014 tempo parado at\u00e9 virar AFK\u00a7c\n"
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
                                                })))))
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

        // (4.5.0) /afk — entra/sai do AFK na mão (sai do RP sozinho)
        event.getDispatcher().register(Commands.literal("afk").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            AfkSistema.alternar(p);
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }
}
