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

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7e\u2500\u2500 O JULGAMENTO \u2500\u2500\n"
                + "\u00a77\u2022 \u00a7f5 vidas zeradas \u2192 \u00a77labirinto infinito com bichos bufados\n"
                + "\u00a77\u2022 \u00a7fachar a saída \u2192 \u00a77sala branca do boss\n"
                + "\u00a77\u2022 \u00a7fo ADM interpreta o boss com o modelo dele\n"
                + "\u00a77\u2022 \u00a7f/juizo ganhar <jogador> \u2192 \u00a77item do boss + volta com +2 vidas\n"
                + "\u00a77\u2022 \u00a7f/juizo perder <jogador> \u2192 \u00a77personagem perdido\n"
                + "\u00a77\u2022 \u00a7fa CHAVE (\u00a7f/juizo chave\u00a77) abre o portal atrás do trono\n"
                + "\u00a77\u2022 \u00a7f/juizo reviver <jogador> \u2192 \u00a77desfaz a perda (1 vida)"),
                false);
        return Command.SINGLE_SUCCESS;
    }
}
