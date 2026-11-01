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
                .then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("etiqueta", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                    String etiqueta = StringArgumentType.getString(ctx, "etiqueta")
                                            .replace("[", "").replace("]", "").trim();
                                    if (etiqueta.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Etiqueta vazia. Use /titulo limpar pra tirar o título."));
                                        return 0;
                                    }
                                    Titulos.Titulo atual = Titulos.de(ctx.getSource().getServer(), alvo.getUUID());
                                    Titulos.Titulo novo = new Titulos.Titulo(etiqueta,
                                            Titulos.corDe(etiqueta), atual.personalizado());
                                    Titulos.definir(ctx.getSource().getServer(), alvo.getUUID(), novo);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Título de ")
                                            .append(alvo.getDisplayName())
                                            .append(Component.literal(" agora é ")
                                                    .withStyle(ChatFormatting.GRAY))
                                            .append(Titulos.aplicar(ctx.getSource().getServer(),
                                                    alvo.getUUID(), Component.literal(alvo.getName().getString()))),
                                            true);
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
                                            atual.cor(), texto);
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

        // ---- /titulo limpar <jogador>
        root.then(Commands.literal("limpar")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            Titulos.definir(ctx.getSource().getServer(), alvo.getUUID(), Titulos.Titulo.VAZIO);
                            ctx.getSource().sendSuccess(() -> Component.literal("Título de ")
                                    .append(alvo.getDisplayName())
                                    .append(Component.literal(" removido.").withStyle(ChatFormatting.GRAY)),
                                    true);
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
                                    .append(Component.literal(t.personalizado().isEmpty()
                                            ? "(nenhum)" : "[" + t.personalizado() + "]"))
                                    .withStyle(ChatFormatting.WHITE), false);
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
}
