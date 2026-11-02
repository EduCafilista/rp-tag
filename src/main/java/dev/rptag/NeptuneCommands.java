package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * (5.4.0) COMANDOS DO EVENTO NEPTUNE — todos exigem OP (permissão 2).
 *
 * <pre>
 * /terremoto iniciar [segundos]   a terra colapsa (áudio do dono + tremor + pulso de lentidão)
 * /terremoto parar                os admins desligam quando acabar
 * /terremoto sair [jogador|todos] player por player: sombras engolindo e kick depois de 4 s
 * /terremoto engolir <jogador>     o chão leva a pessoa pro exílio (todos veem)
 * /terremoto status               o que está ligado
 * /terremoto fenda <jogador>      força a terra a abrir embaixo dele (teste)
 * /terremoto fechar               fecha todas as fendas e devolve os blocos
 *   (5.7.9) o /saida SAIU: a animação de saída roda sozinha no logout
 *
 * /vazio ligar | desligar         o admin vira o DEUS DO VAZIO (aura + trilha + medo)
 * /vazio apagar <jogador>         "---- vazio --- apagou X da existência"
 * /vazio exilar <jogador>         manda pra dimensão rptag:exilio (planície de pedra)
 * /vazio soltar <jogador>         traz de volta do exílio
 *
 * /bicho aqui                     põe a caixinha de música onde você está
 * /bicho <x> <y> <z>              põe a caixinha no lugar marcado
 * /bicho limpar                   apaga todas as caixinhas pendentes
 * </pre>
 *
 * <p>O atalho {@code /neptune} mostra o resumo de tudo (pra não ter que
 * decorar).
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class NeptuneCommands {

    private NeptuneCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        // ---------------------------------------------------------- /terremoto
        LiteralArgumentBuilder<CommandSourceStack> terremoto = Commands.literal("terremoto")
                .requires(src -> src.hasPermission(2));

        terremoto.then(Commands.literal("iniciar")
                .executes(ctx -> iniciar(ctx.getSource(), 60))
                .then(Commands.argument("segundos", IntegerArgumentType.integer(10, 1800))
                        .executes(ctx -> iniciar(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "segundos")))));

        terremoto.then(Commands.literal("parar").executes(ctx -> {
            NeptuneEventos.pararTerremoto(ctx.getSource().getServer(), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aTerremoto DESLIGADO (o tremor acabou pra todo mundo)."), true);
            return Command.SINGLE_SUCCESS;
        }));

        terremoto.then(Commands.literal("sair")
                .then(Commands.literal("todos").executes(ctx -> {
                    var todos = ctx.getSource().getServer().getPlayerList().getPlayers();
                    for (ServerPlayer p : todos) {
                        NeptuneEventos.sairDoServidor(p, 4, "O universo se corrigiu.");
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a7a" + todos.size() + " jogador(es) sumindo nas sombras em 4 s."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    NeptuneEventos.sairDoServidor(alvo, 4, "O universo se corrigiu.");
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a7a" + alvo.getName().getString()
                                    + " some nas sombras em 4 s."), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.7.6) /terremoto engolir <jogador> — o chão leva a pessoa EMBORA.
        // O dono perguntou: "tem a animação de quando o terremoto tirar a
        // pessoa do jogo? as outras pessoas que estiverem la dentro vao ver
        // essas pessoas desaparecendo? com efeito e tudo mais?" — agora tem:
        // a fenda abre, a escuridão sobe e engole, os outros VEEM (é partícula
        // no mundo, não tela) e no fim a pessoa vai pro exílio.
        terremoto.then(Commands.literal("engolir")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    ServerPlayer quem = ctx.getSource().getPlayer();
                    NeptuneEventos.engolirNoTerremoto(alvo, quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78O ch\u00e3o engoliu \u00a75" + alvo.getName().getString()
                                    + "\u00a78 (todo mundo viu)."), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.6.0) testar/forçar a fenda num jogador (o chão abre, ele cai)
        terremoto.then(Commands.literal("fenda")
                .executes(ctx -> {
                    // sem jogador: abre no chão onde o comando foi digitado
                    var nivel = ctx.getSource().getLevel();
                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                    boolean caiu = NeptuneEventos.abrirFendaEm(nivel, pos);
                    ctx.getSource().sendSuccess(() -> Component.literal(caiu
                            ? "\u00a7aFenda aberta aqui (" + NeptuneEventos.blocosCavados() + " blocos). Use /terremoto fechar."
                            : "\u00a7cAqui não tem terra/pedra pra rachar."), true);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    NeptuneEventos.imobilizarAlvo(alvo, true);
                    boolean caiu = NeptuneEventos.abrirFenda(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(caiu
                            ? "\u00a7aO ch\u00e3o abriu embaixo de " + alvo.getName().getString() + "."
                            : "\u00a7cN\u00e3o deu pra abrir: o piso dele n\u00e3o \u00e9 terra/pedra."), true);
                    return Command.SINGLE_SUCCESS;
                })));

        terremoto.then(Commands.literal("fechar").executes(ctx -> {
            NeptuneEventos.fecharFendas(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aTodas as fendas fecharam (blocos restaurados)."), true);
            return Command.SINGLE_SUCCESS;
        }));

        terremoto.then(Commands.literal("status").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7eTerremoto: \u00a7f" + (NeptuneEventos.terremotoAtivo() ? "LIGADO" : "desligado")
                    + "  \u00a77| caixinhas do bicho: \u00a7f" + NeptuneEventos.caixas()
                    + "  \u00a77| blocos cavados agora: \u00a7f" + NeptuneEventos.blocosCavados()), false);
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(terremoto);

        // -------------------------------------------------------------- /vazio
        LiteralArgumentBuilder<CommandSourceStack> vazio = Commands.literal("vazio")
                .requires(src -> src.hasPermission(2));

        vazio.then(Commands.literal("ligar").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            NeptuneEventos.ligarDeus(p);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a75\u2588 VOCÊ É O VAZIO. \u00a77Aura de RAIOS VERMELHOS saindo do corpo "
                    + "(voando também), trilha ao andar, medo em quem chega perto.\n"
                    + "\u00a77Use /vazio apagar <jogador> ou /vazio exilar <jogador>."), false);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("desligar").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            NeptuneEventos.desligarDeus(p);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00a77Modo vazio desligado."), false);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("apagar")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    ServerPlayer quem = ctx.getSource().getPlayer();
                    NeptuneEventos.apagarDaExistencia(alvo, quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78Apagado: \u00a75" + alvo.getName().getString()), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.5.0) tira do EXÍLIO (o admin sempre devolve o jogador)
        vazio.then(Commands.literal("soltar")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    NeptuneEventos.soltarDoExilio(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a7a" + alvo.getName().getString() + " voltou do ex\u00edlio (num lugar aleat\u00f3rio do mundo)."), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.7.8) /vazio entrar — o admin vai pra dimensão do exílio testar o
        // ambiente (andar no escuro, monólitos, sussurros) sem punir ninguém.
        vazio.then(Commands.literal("entrar").executes(ctx -> {
            ServerPlayer quem = ctx.getSource().getPlayerOrException();
            NeptuneEventos.entrarNoExilio(quem);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a78Voc\u00ea est\u00e1 no ex\u00edlio: d\u00e1 pra andar, construir e usar "
                            + "comando normalmente.\n\u00a78Pra sair: \u00a7f/vazio soltar "
                            + quem.getName().getString()), true);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("exilar")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    ServerPlayer quem = ctx.getSource().getPlayer();
                    NeptuneEventos.exilar(alvo, quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78Exilado no vazio: \u00a75" + alvo.getName().getString()), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.12.0) /vazio fragmentos [jogador] — dá os 8 fragmentos (teste do
        // portal sem caçar pedestal) e /vazio portal — leva o admin até ele.
        vazio.then(Commands.literal("fragmentos")
                .executes(ctx -> {
                    ServerPlayer quem = ctx.getSource().getPlayerOrException();
                    VazioReino.darTudo(quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75Voc\u00ea tem os 8 FRAGMENTOS DO VAZIO na mochila. "
                                    + "\u00a77Encaixe um em cada pedestal em volta da praça "
                                    + "(\u00a7f/vazio pedestais\u00a77) e o portal abre sozinho."), true);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    VazioReino.darTudo(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75" + alvo.getName().getString() + " recebeu os 8 fragmentos do vazio."), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.13.0) /vazio portal            -> INVOCA o portal onde você está olhando
        //          /vazio portal ir         -> só te leva até o portal que já existe
        //          /vazio portal <jogador>  -> invoca na frente do jogador
        // Pedido 50: "preciso também de um comando para invocar o portal".
        vazio.then(Commands.literal("portal")
                .executes(ctx -> {
                    ServerPlayer quem = ctx.getSource().getPlayerOrException();
                    String r = VazioReino.invocarPortal(quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(r), true);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("ir").executes(ctx -> {
                    ServerPlayer quem = ctx.getSource().getPlayerOrException();
                    NeptuneEventos.irAoPortal(quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78Levado ao portal do vazio."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    String r = VazioReino.invocarPortal(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75" + alvo.getName().getString() + "\u00a78: " + r), false);
                    return Command.SINGLE_SUCCESS;
                }))
                // (5.13.1) /vazio portal <x> <y> <z> — invoca numa posição exata (funciona
                // do console também; é assim que o dono testa sem entrar na dimensão)
                .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("z",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            var nv = src.getLevel();
                                            int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x");
                                            int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y");
                                            int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z");
                                            String r = VazioReino.invocarPortalEm(nv, x, y, z, src.getPlayer());
                                            src.sendSuccess(() -> Component.literal(r), true);
                                            return Command.SINGLE_SUCCESS;
                                        })))));

        // (5.13.0) /vazio construir — refaz praça, pedestais, altares e portal na hora
        vazio.then(Commands.literal("construir").executes(ctx -> {
            ServerPlayer quem = ctx.getSource().getPlayerOrException();
            if (!(quem.level() instanceof net.minecraft.server.level.ServerLevel nv)
                    || !VazioReino.ehVazio(nv)) {
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cSó funciona dentro de rptag:exilio."));
                return 0;
            }
            VazioReino.construirTudo(nv, quem);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aVazio reconstruído: praça, 8 pedestais, 8 altares e o portal."), true);
            return Command.SINGLE_SUCCESS;
        }));

        // (5.13.0) /vazio abrir | fechar — abre e fecha o portal NA MARRA (preview/vídeo)
        vazio.then(Commands.literal("abrir").executes(ctx -> {
            net.minecraft.server.level.ServerLevel nv = ctx.getSource().getServer()
                    .getLevel(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
            if (nv == null) {
                ctx.getSource().sendFailure(Component.literal("\u00a7cA dimensão não carregou."));
                return 0;
            }
            // (5.13.2) sem aviso no chat: o /vazio abrir só faz o portal abrir
            // (o dono: "esse aviso não precisa aparecer que o portal está sendo aberto").
            VazioReino.abrirPortal(nv, null);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("fechar").executes(ctx -> {
            net.minecraft.server.level.ServerLevel nv = ctx.getSource().getServer()
                    .getLevel(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
            if (nv == null) {
                ctx.getSource().sendFailure(Component.literal("\u00a7cA dimensão não carregou."));
                return 0;
            }
            VazioReino.fecharPortal(nv);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a78Portal fechado."), true);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("pedestais").executes(ctx -> {
            ServerPlayer quem = ctx.getSource().getPlayerOrException();
            RPWorldData dados = RPWorldData.get(quem.getServer());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a75Pedestais: \u00a7f" + dados.quantosPedestais(quem.getUUID()) + "/8"
                            + "\u00a78 · altares esvaziados: \u00a7f"
                            + Integer.bitCount(dados.getSantuarios(quem.getUUID())) + "/8\n"
                            + "\u00a78anel em volta da praça: " + VazioReino.listaPedestais()
                            + "\n\u00a78portal: " + VazioReino.ancoraPortal(
                                    (net.minecraft.server.level.ServerLevel) quem.level())
                            + "\u00a78 · aberto: \u00a7f" + dados.isPortalAberto()
                            + "\u00a78 · fila: " + VazioReino.construindo()), false);
            return Command.SINGLE_SUCCESS;
        }));

        // ------------------------------------------------------- /saida: MORREU
        // (5.7.9) O dono recusou o comando: "não quero essa /saida, quero que a
        // animação apareça quando o player sair do jogo". O comando saiu do
        // registro; quem dispara a animação agora é o próprio logout
        // (ServerEvents.onLoggedOut -> NeptuneEventos.saidaAutomatica).

        event.getDispatcher().register(vazio);

        // -------------------------------------------------------------- /bicho
        LiteralArgumentBuilder<CommandSourceStack> bicho = Commands.literal("bicho")
                .requires(src -> src.hasPermission(2));

        bicho.then(Commands.literal("aqui").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            NeptuneEventos.colocarCaixa((net.minecraft.server.level.ServerLevel) p.level(), p.blockPosition());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aCaixinha colocada aqui. \u00a77Ela toca por "
                    + NeptuneEventos.BICHO_MUSICA_SEG + " s. O primeiro clique só toca o som; "
                    + "o SEGUNDO (depois de 4 s) solta o bicho \u2014 e ele dá pra matar."), false);
            return Command.SINGLE_SUCCESS;
        }));

        bicho.then(Commands.literal("limpar").executes(ctx -> {
            NeptuneEventos.limparCaixas();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a77Todas as caixinhas pendentes foram removidas."), false);
            return Command.SINGLE_SUCCESS;
        }));

        bicho.then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                .executes(ctx -> {
                    BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                            .getBlockPos(ctx, "pos");
                    var nivel = ctx.getSource().getLevel();
                    NeptuneEventos.colocarCaixa(nivel, pos);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a7aCaixinha em " + pos.toShortString() + ". \u00a77O bicho espera alguém chegar."), false);
                    return Command.SINGLE_SUCCESS;
                }));

        event.getDispatcher().register(bicho);

        // ------------------------------------------------------------ /neptune
        event.getDispatcher().register(Commands.literal("neptune").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a78\u00a7l\u2588 \u00a75\u00a7lEVENTO NEPTUNE \u00a78\u00a7l\u2588\n"
                    + "\u00a7e/terremoto iniciar [s] \u00a77— a terra colapsa (som do colapso + tremor + lentidão em pulsos)\n"
                    + "\u00a7e/terremoto sair [jogador|todos] \u00a77— a pessoa some nas sombras (kick em 4 s)\n"
                    + "\u00a7e/terremoto parar \u00a77— desliga\n"
                    + "\u00a7e/vazio ligar|desligar \u00a77— raios vermelhos em volta do corpo + trilha + medo\n"
                    + "\u00a7e/vazio apagar <jogador> \u00a77— apaga da existência\n"
                    + "\u00a7e/vazio exilar <jogador> \u00a77— manda pra rptag:exilio (tudo preto e vazio)\n"
                    + "\u00a7e/vazio soltar <jogador> \u00a77— solta ele num lugar ALEATÓRIO do mundo\n"
                    + "\u00a7e/vazio fragmentos [jogador] \u00a77— dá os 8 fragmentos (teste do portal)\n"
                    + "\u00a7e/vazio portal \u00a77— vai até o portal do vazio\n"
                    + "\u00a7e/vazio pedestais \u00a77— lista os 8 pedestais e quantos fragmentos você tem\n"
                    + "\u00a7e/bicho aqui \u00a77— caixinha de música (o bicho pula quando alguém chega)")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
            return Command.SINGLE_SUCCESS;
        }));
    }

    private static int iniciar(CommandSourceStack src, int segundos) {
        NeptuneEventos.iniciarTerremoto(src.getServer(), segundos);
        return Command.SINGLE_SUCCESS;
    }
}
