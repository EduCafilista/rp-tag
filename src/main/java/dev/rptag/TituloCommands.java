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

/**
 * Comandos do SISTEMA DE TÍTULOS (5.7.2).
 *
 * <pre>
 *   /titulo definir &lt;jogador&gt; &lt;etiqueta&gt;      [Sagrado], [Warden], [Chronos]…
 *   /titulo personalizado &lt;jogador&gt; &lt;texto&gt;   Nome [O Ferreiro]
 *   /titulo cor &lt;jogador&gt; &lt;cor&gt;             (5.9.0) cor da ETIQUETA
 *   /titulo cor pessoal &lt;jogador&gt; &lt;cor&gt;     (5.9.0) cor do título pessoal
 *   /titulo limpar &lt;jogador&gt;                  tira tudo
 *   /titulo ver &lt;jogador&gt;                     mostra o que está valendo
 *   /titulo lista                             todas as almas com título
 *   /titulo paleta                            as etiquetas prontas + cores
 * </pre>
 *
 * <p>Só ADMIN (nível 2) mexe no título dos outros. O jogador comum pode ver a
 * lista e a paleta.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class TituloCommands {

    private TituloCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("titulo");

        // ---- /titulo paleta (todo mundo vê qual etiqueta existe)
        root.then(Commands.literal("paleta").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("Etiquetas prontas:")
                    .withStyle(ChatFormatting.AQUA), false);
            for (int i = 0; i < Titulos.ETIQUETAS.length; i++) {
                final int cor = Titulos.CORES[i];
                final String nome = Titulos.ETIQUETAS[i];
                ctx.getSource().sendSuccess(() -> Component.literal(" • [" + nome + "]")
                        .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(cor)))
                        .append(Component.literal("  #" + String.format("%06X", cor))
                                .withStyle(ChatFormatting.DARK_GRAY)), false);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                            "Qualquer outra palavra também vale (fica cinza-claro).")
                    .withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }));

        // ---- /titulo definir <jogador> <etiqueta>
        root.then(Commands.literal("definir")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .then(Commands.argument("etiqueta", StringArgumentType.string())
                                .executes(ctx -> {
                                    var alvos = EntityArgument.getPlayers(ctx, "jogadores");
                                    String etiqueta = StringArgumentType.getString(ctx, "etiqueta")
                                            .replace("[", "").replace("]", "").trim();
                                    if (etiqueta.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Etiqueta vazia. Use /titulo limpar pra tirar o título."));
                                        return 0;
                                    }
                                    for (ServerPlayer alvo : alvos) {
                                        Titulos.Titulo atual = Titulos.de(ctx.getSource().getServer(), alvo.getUUID());
                                        // (5.9.0) se a etiqueta é a MESMA, mantém a cor escolhida na mão;
                                        // etiqueta nova volta pra cor de fábrica da paleta
                                        int cor = atual.etiqueta().equalsIgnoreCase(etiqueta)
                                                ? atual.cor() : -1;
                                        Titulos.Titulo novo = new Titulos.Titulo(etiqueta, cor,
                                                atual.personalizado(), atual.corPessoal());
                                        Titulos.definir(ctx.getSource().getServer(), alvo.getUUID(), novo);
                                        ctx.getSource().sendSuccess(() -> Component.literal("✦ ")
                                                .withStyle(ChatFormatting.DARK_AQUA)
                                                .append(Titulos.aplicar(ctx.getSource().getServer(),
                                                        alvo.getUUID(), Component.literal(alvo.getName().getString()))),
                                                false);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Título definido em " + alvos.size() + " jogador(es).")
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // ---- /titulo personalizado <jogador> <texto livre…>
        root.then(Commands.literal("personalizado")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                    String texto = StringArgumentType.getString(ctx, "texto")
                                            .replace("[", "").replace("]", "").trim();
                                    Titulos.Titulo atual = Titulos.de(ctx.getSource().getServer(), alvo.getUUID());
                                    Titulos.Titulo novo = new Titulos.Titulo(atual.etiqueta(),
                                            atual.cor(), texto, atual.corPessoal());
                                    Titulos.definir(ctx.getSource().getServer(), alvo.getUUID(), novo);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Título pessoal de ")
                                            .append(alvo.getDisplayName())
                                            .append(Component.literal(" → ")
                                                    .withStyle(ChatFormatting.GRAY))
                                            .append(Titulos.aplicar(ctx.getSource().getServer(),
                                                    alvo.getUUID(), Component.literal(alvo.getName().getString()))),
                                            true);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // ---- (5.9.0) /titulo cor [pessoal] <jogadores> <cor>
        com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> sugereCor =
                (ctx, builder) -> {
                    for (String nome : Titulos.nomesDeCor()) {
                        builder.suggest(nome);
                    }
                    builder.suggest("padrao");
                    return builder.buildFuture();
                };
        root.then(Commands.literal("cor")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("pessoal")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .then(Commands.argument("cor", StringArgumentType.greedyString())
                                        .suggests(sugereCor)
                                        .executes(ctx -> corDoTitulo(ctx, true, sugereCor)))))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .then(Commands.argument("cor", StringArgumentType.greedyString())
                                .suggests(sugereCor)
                                .executes(ctx -> corDoTitulo(ctx, false, sugereCor)))));

        // ---- /titulo limpar <jogador>
        root.then(Commands.literal("limpar")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(ctx -> {
                            var alvos = EntityArgument.getPlayers(ctx, "jogadores");
                            for (ServerPlayer alvo : alvos) {
                                Titulos.definir(ctx.getSource().getServer(), alvo.getUUID(), Titulos.Titulo.VAZIO);
                                ctx.getSource().sendSuccess(() -> Component.literal("Título de ")
                                        .append(alvo.getDisplayName())
                                        .append(Component.literal(" removido.").withStyle(ChatFormatting.GRAY)),
                                        false);
                            }
                            return Command.SINGLE_SUCCESS;
                        })));

        // ---- /titulo ver <jogador>
        root.then(Commands.literal("ver")
                .then(Commands.argument("jogador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            Titulos.Titulo t = Titulos.de(ctx.getSource().getServer(), alvo.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal("Etiqueta: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(t.etiqueta().isEmpty()
                                            ? Component.literal("(nenhuma)").withStyle(ChatFormatting.DARK_GRAY)
                                            : Component.literal("[" + t.etiqueta() + "]")
                                                    .withStyle(s -> s.withColor(
                                                            net.minecraft.network.chat.TextColor.fromRgb(t.cor())))),
                                    false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Título pessoal: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(t.personalizado().isEmpty()
                                            ? Component.literal("(nenhum)").withStyle(ChatFormatting.DARK_GRAY)
                                            : Component.literal("[" + t.personalizado() + "]")
                                                    .withStyle(s2 -> s2.withColor(
                                                            net.minecraft.network.chat.TextColor.fromRgb(t.corPessoal())))),
                                    false);
                            // (5.9.0) as cores, pro admin ver o que está valendo
                            ctx.getSource().sendSuccess(() -> Component.literal("Cor da etiqueta: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal("#" + String.format("%06X", t.cor())
                                                    + (t.corDeFabrica() ? " (de fábrica)" : " (escolhida)"))
                                            .withStyle(ChatFormatting.DARK_GRAY)), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Cor do título pessoal: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal("#" + String.format("%06X", t.corPessoal()))
                                            .withStyle(ChatFormatting.DARK_GRAY)), false);
                            return Command.SINGLE_SUCCESS;
                        })));

        // ---- /titulo lista
        root.then(Commands.literal("lista").executes(ctx -> {
            var server = ctx.getSource().getServer();
            var todos = Titulos.Dados.get(server).lista();
            if (todos.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                                "Ninguém tem título ainda. /titulo definir <jogador> <etiqueta>")
                        .withStyle(ChatFormatting.GRAY), false);
                return Command.SINGLE_SUCCESS;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Almas com título (" + todos.size() + "):")
                    .withStyle(ChatFormatting.AQUA), false);
            for (var e : todos) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                String nick = p != null ? p.getName().getString() : e.getKey().toString().substring(0, 8);
                Component linha = Titulos.aplicar(server, e.getKey(), Component.literal(nick));
                ctx.getSource().sendSuccess(() -> Component.literal(" ✦ ").withStyle(ChatFormatting.DARK_AQUA)
                        .append(linha), false);
            }
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }

    /**
     * (5.9.0) corpo do {@code /titulo cor [pessoal] <jogadores> <cor>}.
     *
     * @param pessoal true = pinta o {@code [O Ferreiro]} (depois do nome);
     *                false = pinta a etiqueta {@code [Chronos]} (na frente)
     */
    private static int corDoTitulo(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            boolean pessoal,
            com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> sugereCor)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var server = ctx.getSource().getServer();
        var alvos = EntityArgument.getPlayers(ctx, "jogadores");
        String texto = StringArgumentType.getString(ctx, "cor").trim();
        boolean padrao = texto.equalsIgnoreCase("padrao") || texto.equalsIgnoreCase("auto")
                || texto.equalsIgnoreCase("reset") || texto.equalsIgnoreCase("padrão");

        Integer rgb = padrao ? null : Titulos.corDeTexto(texto);
        if (!padrao && rgb == null) {
            ctx.getSource().sendFailure(Component.literal(
                            "Cor desconhecida: \"" + texto + "\". Use ouro, vermelho, ciano, roxo… "
                                    + "ou um HEX (FFD700 ou \"#FFD700\").")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        for (ServerPlayer alvo : alvos) {
            Titulos.Titulo atual = Titulos.de(server, alvo.getUUID());
            int novaCor;
            if (pessoal) {
                novaCor = padrao ? -1 : rgb;
            } else {
                // na etiqueta, "padrao" = volta pra cor de fábrica da paleta
                novaCor = padrao ? -1 : rgb;
            }
            Titulos.Titulo novo = new Titulos.Titulo(atual.etiqueta(), novaCor,
                    atual.personalizado(), pessoal ? novaCor : atual.corPessoal());
            Titulos.definir(server, alvo.getUUID(), novo);
            ctx.getSource().sendSuccess(() -> Component.literal(pessoal
                            ? "Cor do título pessoal de "
                            : "Cor da etiqueta de ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(alvo.getDisplayName())
                    .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Titulos.aplicar(server, alvo.getUUID(),
                            Component.literal(alvo.getName().getString()))), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
