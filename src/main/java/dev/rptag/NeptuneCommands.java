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
 * /terremoto sair [jogador|todos] player por player: animação VERDE e kick depois de 4 s
 * /terremoto status               o que está ligado
 * /terremoto fenda <jogador>      força a terra a abrir embaixo dele (teste)
 * /terremoto fechar               fecha todas as fendas e devolve os blocos
 *
 * /vazio ligar | desligar         o admin vira o DEUS DO VAZIO (aura + trilha + medo)
 * /vazio apagar <jogador>         "---- vazio --- apagou X da existência"
 * /vazio exilar <jogador>         manda pra dimensão rptag:exilio (tudo preto)
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
                    "\u00a75\u2588 VOCÊ É O VAZIO. \u00a77Aura ligada, trilha ao andar, medo em quem chega perto.\n"
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
                            "\u00a7a" + alvo.getName().getString() + " voltou do ex\u00edlio."), true);
                    return Command.SINGLE_SUCCESS;
                })));

        vazio.then(Commands.literal("exilar")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    ServerPlayer quem = ctx.getSource().getPlayer();
                    NeptuneEventos.exilar(alvo, quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78Exilado no vazio: \u00a75" + alvo.getName().getString()), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // -------------------------------------------------------------- /saida
        // (5.7.6) "faça a animação para quando o pessoal poder usar no mapa":
        // agora a animação de saída (o bonequinho se desfazendo em código) pode
        // ser DISPARADA na hora, pra todo mundo, sem ninguém realmente sair do
        // servidor. Serve pra anunciar a saída de um personagem no RP.
        LiteralArgumentBuilder<CommandSourceStack> saida = Commands.literal("saida")
                .requires(src -> src.hasPermission(2));
        saida.then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
            int n = NeptuneEventos.tocarAnimacaoSaida(alvo, ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aAnima\u00e7\u00e3o de sa\u00edda rodando pra \u00a7f"
                            + n + "\u00a7a jogador(es) — nome: \u00a77"
                            + alvo.getName().getString()), true);
            return Command.SINGLE_SUCCESS;
        }));
        saida.then(Commands.literal("todos").executes(ctx -> {
            var lista = ctx.getSource().getServer().getPlayerList().getPlayers();
            int total = 0;
            for (ServerPlayer alvo : lista) {
                total = Math.max(total, NeptuneEventos.tocarAnimacaoSaida(alvo,
                        ctx.getSource().getServer()));
            }
            final int n = total;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aAnima\u00e7\u00e3o de sa\u00edda rodando pra \u00a7f" + n
                            + "\u00a7a jogador(es)."), true);
            return Command.SINGLE_SUCCESS;
        }));
        event.getDispatcher().register(saida);

        event.getDispatcher().register(vazio);

        // -------------------------------------------------------------- /bicho
        LiteralArgumentBuilder<CommandSourceStack> bicho = Commands.literal("bicho")
                .requires(src -> src.hasPermission(2));

        bicho.then(Commands.literal("aqui").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            NeptuneEventos.colocarCaixa((net.minecraft.server.level.ServerLevel) p.level(), p.blockPosition());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aCaixinha colocada aqui. \u00a77Ela vai tocar por "
                    + NeptuneEventos.BICHO_MUSICA_SEG + " s — quando alguém chegar a "
                    + (int) NeptuneEventos.BICHO_RAIO + " blocos, o bicho pula."), false);
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
                    + "\u00a7e/vazio ligar|desligar \u00a77— aura + trilha + medo (o Deus do Vazio)\n"
                    + "\u00a7e/vazio apagar <jogador> \u00a77— apaga da existência\n"
                    + "\u00a7e/vazio exilar <jogador> \u00a77— manda pra rptag:exilio (tudo preto e vazio)\n"
                    + "\u00a7e/vazio soltar <jogador> \u00a77— traz de volta do exílio\n"
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
