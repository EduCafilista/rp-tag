package dev.rptag;

import java.util.Collection;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * (5.28.0) <b>/juizo — os comandos do JULGAMENTO</b> (o labirinto + a sala branca do boss).
 *
 * <p>O fluxo do RP, do jeito que o dono desenhou:
 * <ol>
 *   <li>o player zerou as 5 vidas → cai no <b>labirinto</b> sozinho (automático);</li>
 *   <li>acha a saída → <b>sala branca</b>;</li>
 *   <li>o <b>ADM interpreta o boss</b> com o modelo dele;</li>
 *   <li>o ADM resolve a luta: {@code /juizo ganhar <jogador>} ou {@code /juizo perder <jogador>};</li>
 *   <li>quem ganhou recebe o item do boss; o ADM entrega a CHAVE
 *       ({@code /juizo chave <jogador>} ou dropa no chão) e ele abre o portal atrás do trono
 *       para voltar à vida com +2 vidas.</li>
 * </ol>
 *
 * <p>Tudo com permissão de ADM (nível 2) e com {@code try/catch}: comando não derruba servidor.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class JuizoCommands {

    private JuizoCommands() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("juizo")
                .requires(src -> src.hasPermission(2))

                // /juizo entrar <jogadores> — manda pro labirinto
                .then(Commands.literal("entrar")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "entrar"))))

                // /juizo sala <jogadores> — leva direto pra sala branca (teste do ADM)
                // (5.30.0) qual dimensão está servindo de labirinto (o Reino do outro mod)
                .then(Commands.literal("dimensao")
                        .executes(ctx -> dimensao(ctx))
                        .then(Commands.literal("listar").executes(ctx -> listarDimensoes(ctx)))
                        .then(Commands.literal("automatico").executes(ctx -> {
                            dev.rptag.JuizoReino.fixar(null);
                            var resolvida = dev.rptag.JuizoReino.chave(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a77Voltou pro automático. Dimensão encontrada: \u00a7f"
                                    + (resolvida == null ? "(nenhuma \u2014 labirinto antigo)"
                                            : resolvida.location().toString())),
                                    false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("definir")
                                .then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((ctx, b) -> {
                                            for (var k : dev.rptag.JuizoReino.candidatas(ctx.getSource().getServer())) {
                                                b.suggest(k.location().toString());
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            net.minecraft.resources.ResourceLocation rl =
                                                    net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "id");
                                            dev.rptag.JuizoReino.fixar(rl);
                                            boolean existe = ctx.getSource().getServer().getLevel(
                                                    net.minecraft.resources.ResourceKey.create(
                                                            net.minecraft.core.registries.Registries.DIMENSION, rl)) != null;
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "\u00a77Labirinto do Julgamento agora é: \u00a7f" + rl
                                                    + (existe ? "\u00a7a (carregada)"
                                                              : "\u00a7c (essa dimensão NÃO existe aqui)")),
                                                    true);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("sala")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "sala"))))

                // /juizo chave <jogadores> — entrega a chave do portal
                .then(Commands.literal("chave")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "chave"))))

                // /juizo ganhar <jogadores> — o ADM declara a vitória (+2 vidas + item do boss)
                .then(Commands.literal("ganhar")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "ganhar"))))

                // /juizo perder <jogadores> — o ADM declara a derrota (personagem perdido)
                .then(Commands.literal("perder")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "perder"))))

                // /juizo reviver <jogadores> — desfaz o "personagem perdido" (1 vida de volta)
                .then(Commands.literal("reviver")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "reviver"))))

                // /juizo sair <jogadores> — tira do julgamento (socorro)
                .then(Commands.literal("sair")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "sair"))))

                // /juizo status <jogadores>
                .then(Commands.literal("status")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(ctx -> comAlvos(ctx, "status"))))

                // /juizo info — como o RP funciona
                .then(Commands.literal("info").executes(JuizoCommands::info)));
    }

    private static int comAlvos(CommandContext<CommandSourceStack> ctx, String acao) {
        try {
            Collection<ServerPlayer> alvos = EntityArgument.getPlayers(ctx, "jogadores");
            int n = 0;
            for (ServerPlayer p : alvos) {
                try {
                    if (fazer(acao, p)) {
                        n++;
                    }
                } catch (Throwable t) {
                    RPTagMod.LOGGER.warn("RP Tag: /juizo " + acao + " falhou para "
                            + p.getName().getString(), t);
                }
            }
            final int feito = n;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a78[juizo] \u00a7f" + acao + " \u00a77em \u00a7f" + feito + " jogador(es)")
                    .withStyle(ChatFormatting.GRAY), false);
            return feito > 0 ? Command.SINGLE_SUCCESS : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("\u00a7c/juizo " + acao + " falhou: " + e.getMessage()));
            return 0;
        }
    }

    private static boolean fazer(String acao, ServerPlayer p) {
        switch (acao) {
            case "entrar" -> {
                return JuizoFinal.entrar(p);
            }
            case "sala" -> {
                JuizoFinal.achouSaida(p);
                return true;
            }
            case "chave" -> {
                JuizoFinal.darChave(p);
                return true;
            }
            case "ganhar" -> {
                JuizoFinal.vitoria(p);
                return true;
            }
            case "perder" -> {
                JuizoFinal.derrota(p);
                return true;
            }
            case "reviver" -> {
                RPWorldData.get(p.getServer()).marcarPersonagemPerdido(p.getUUID(), false);
                RPWorldData.get(p.getServer()).setCanonLives(p.getUUID(), 1);
                p.sendSystemMessage(Component.literal(
                        "\u00a7aSeu personagem voltou: \u00a7f1 vida canônica").withStyle(ChatFormatting.GREEN));
                return true;
            }
            case "sair" -> {
                JuizoFinal.sair(p);
                JuizoFinal.voltarParaCasa(p);
                return true;
            }
            case "status" -> {
                status(p);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void status(ServerPlayer p) {
        RPWorldData dados = RPWorldData.get(p.getServer());
        UUID id = p.getUUID();
        p.sendSystemMessage(Component.literal("\u00a78\u2500\u2500 JULGAMENTO \u2500\u2500")
                .withStyle(ChatFormatting.DARK_GRAY));
        p.sendSystemMessage(Component.literal("\u00a77Vidas canônicas: \u00a7f"
                + dados.getCanonLives(id)).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("\u00a77Personagem perdido: \u00a7f"
                + (dados.isPersonagemPerdido(id) ? "SIM" : "não")).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("\u00a77Dimensão agora: \u00a7f"
                + p.level().dimension().location()).withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("\u00a77No julgamento: \u00a7f"
                + (JuizoFinal.estaNoJulgamento(id) ? "SIM" : "não")).withStyle(ChatFormatting.GRAY));
    }

    /** (5.30.0) mostra/descobre qual dimensão está servindo de labirinto. */
    private static int dimensao(CommandContext<CommandSourceStack> ctx) {
        var srv = ctx.getSource().getServer();
        var k = dev.rptag.JuizoReino.chave(srv);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7e\u2500\u2500 LABIRINTO DO JULGAMENTO \u2500\u2500\n"
                + "\u00a77dimensão em uso: \u00a7f" + (k == null ? "(nenhuma \u2014 "
                + "usando o labirinto antigo do RP Tag)" : k.location().toString()) + "\n"
                + "\u00a77dimensões de outros mods: \u00a7f"
                + dev.rptag.JuizoReino.candidatas(srv).size()
                + "\u00a77  \u2022  \u00a7f/juizo dimensao listar\n"
                + "\u00a77se a escolha automática errar: \u00a7f/juizo dimensao definir <id>"),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** (5.30.0) lista as dimensões que os outros mods trouxeram. */
    private static int listarDimensoes(CommandContext<CommandSourceStack> ctx) {
        var srv = ctx.getSource().getServer();
        var lista = dev.rptag.JuizoReino.candidatas(srv);
        if (lista.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a77Nenhuma dimensão de outro mod carregada. O Julgamento usa o "
                    + "labirinto antigo do RP Tag."), false);
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("\u00a7e\u2500\u2500 DIMENS\u00d5ES DE OUTROS MODS \u2500\u2500\n");
        var emUso = dev.rptag.JuizoReino.chave(srv);
        for (var k : lista) {
            boolean escolhida = emUso != null && emUso.equals(k);
            sb.append(escolhida ? "\u00a7a\u25b8 " : "\u00a78\u2022 ").append("\u00a7f")
              .append(k.location()).append(escolhida ? "\u00a77  (é esta)" : "").append("\n");
        }
        String texto = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(texto), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7e\u2500\u2500 O JULGAMENTO \u2500\u2500\n"
                + "\u00a77\u2022 \u00a7f5 vidas zeradas \u2192 \u00a77o REINO do outro mod (o novo labirinto)\n"
                + "\u00a77\u2022 \u00a7fo PORTAL do reino \u2192 \u00a77leva pra ARENA, não pro overworld\n"
                + "\u00a77\u2022 \u00a7f/juizo dimensao \u2192 \u00a77qual dimensão está servindo de labirinto\n"
                + "\u00a77\u2022 \u00a7fachar a saída \u2192 \u00a77A ARENA DO JULGAMENTO\n"
                + "\u00a77\u2022 \u00a7fo ADM interpreta o boss \u2014 \u00a7fo mod NÃO põe boneco nenhum\n"
                + "\u00a77\u2022 \u00a7f/juizo ganhar <jogador> \u2192 \u00a77volta com +2 vidas (o ITEM quem dá é o ADM)\n"
                + "\u00a77\u2022 \u00a7f/juizo perder <jogador> \u2192 \u00a77personagem perdido\n"
                + "\u00a77\u2022 \u00a7fa CHAVE (\u00a7f/juizo chave\u00a77) abre o portal atrás do trono\n"
                + "\u00a77\u2022 \u00a7f/juizo reviver <jogador> \u2192 \u00a77desfaz a perda (1 vida)"),
                false);
        return Command.SINGLE_SUCCESS;
    }
}
