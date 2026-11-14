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

        // (5.44.2) O DOM É DO JOGADOR — e o ADM pode dar pra outro.
        // O dono: "a roda abre pra quem não tem o dom". Agora ninguém ganha o dom por estar no
        // vazio: quem tem é quem foi LIGADO aqui (e isso fica salvo no save do mundo).
        // `/vazio ligar` (sem alvo) continua ligando em você; com alvo, liga no jogador citado.
        vazio.then(Commands.literal("ligar")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    NeptuneEventos.ligarDeus(p);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75\u2588 VOCÊ É O VAZIO. \u00a77Aura de RAIOS AMARELOS E NEGROS saindo do corpo "
                            + "(voando também), trilha ao andar, medo em quem chega perto.\n"
                            + "\u00a77A roda de poderes (tecla R) agora é sua. Use /vazio apagar <jogador> "
                            + "ou /vazio exilar <jogador>."), false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    NeptuneEventos.ligarDeus(alvo);
                    alvo.displayClientMessage(Component.literal(
                            "\u00a75\u2588 O VAZIO DESPERTOU EM VOCÊ. \u00a77Aperte \u00a7fR\u00a77 para abrir "
                            + "a roda de poderes."), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75O dom do vazio foi ligado em \u00a7f" + alvo.getName().getString()
                            + "\u00a75."), false);
                    return Command.SINGLE_SUCCESS;
                })));

        // ================= (5.31.0) OS PODERES DO VAZIO =================
        // O dono: "não quero esse comando de ativar poderes ou desativar" — o dom agora vem do
        // lugar (vazio/reino) ou do cargo de ADM, e quem arremessa é a TECLA (R abre o leque).
        // Este comando fica só como plano B para teste/console; a tecla é o caminho normal.
        vazio.then(Commands.literal("desligar")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    NeptuneEventos.desligarDeus(p);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00a77Modo vazio desligado."), false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    NeptuneEventos.desligarDeus(alvo);
                    alvo.displayClientMessage(Component.literal(
                            "\u00a78O vazio se apagou em você. A roda de poderes não responde mais."), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a77O dom do vazio foi desligado em \u00a7f"
                            + alvo.getName().getString() + "\u00a77."), false);
                    return Command.SINGLE_SUCCESS;
                })));

        vazio.then(Commands.literal("apagar")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    ServerPlayer quem = ctx.getSource().getPlayer();
                    NeptuneEventos.apagarDaExistencia(alvo, quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78Apagado: \u00a75" + alvo.getName().getString()), true);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.33.0) /vazio soltar — UM comando, duas formas:
        //   /vazio soltar <poder> [jogador]   → solta o poder (o ADM pode escolher em quem)
        //   /vazio soltar <jogador>           → tira a pessoa do exílio (era a forma antiga)
        // O dono: "o vazio soltar não solta mais o player escolhido, mostra apenas o soltar
        // poderes" — as DUAS voltaram, e o /help mostra as duas.
        vazio.then(Commands.literal("soltar")
                .then(Commands.literal("poder")
                        .then(Commands.argument("qual", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (String n : new String[]{"raio", "orbe", "onda", "olhos", "penas", "banir"}) {
                                        b.suggest(n);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(ctx -> soltarPoder(ctx, null))
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .executes(ctx -> soltarPoder(ctx, EntityArgument.getPlayer(ctx, "jogador"))))))
                .then(Commands.literal("exilio")
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            NeptuneEventos.soltarDoExilio(alvo);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7a" + alvo.getName().getString()
                                            + " vai voltar do ex\u00edlio no SPAWN dele."), false);
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    NeptuneEventos.soltarDoExilio(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a7a" + alvo.getName().getString()
                                    + " vai voltar do ex\u00edlio no SPAWN dele."), false);
                    return Command.SINGLE_SUCCESS;
                })));

        // (5.18.0) /vazio relevo — reconstrói (e CONSERTA) as montanhas e prédios do vazio.
        // O dono: "cade as montanhas que voce tinha feito anteriormente no vazio? sumiram
        // todas, volte para elas". O relevo agora é absoluto e repetível: este comando joga a
        // fila de novo e levanta o que estiver faltando — sem duplicar nada.
        // (5.36.0) /onde — responde em que dimensão (e lugar) você está, do jeito do mod.
        // Serve para conferir na hora se o portal/comando mandou mesmo para o mundo certo.
        vazio.then(Commands.literal("onde").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            ctx.getSource().sendSuccess(() -> Component.literal(NeptuneEventos.ondeEstou(p)), false);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("relevo").executes(ctx -> {
            var server = ctx.getSource().getServer();
            var nv = server.getLevel(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
            if (nv == null) {
                ctx.getSource().sendFailure(Component.literal("\u00a7cA dimensão do vazio não está carregada."));
                return 0;
            }
            int chunks = VazioReino.reconstruirRelevo(nv);
            int[] m = VazioReino.medirRelevo(nv);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aMontanhas do vazio: \u00a7f" + chunks + "\u00a7a chunks conferidos.\n"
                            + "\u00a77Montanha mais alta: \u00a7f" + m[0] + " blocos\u00a77 · "
                            + "colunas de serra: \u00a7f" + m[1] + "\n"
                            + "\u00a78O relevo mira a altura final, então rodar de novo só "
                            + "levanta o que falta — não empilha montanha."), false);
            return Command.SINGLE_SUCCESS;
        }));

        // (5.23.0) /vazio onde <x> <z> — em que lugar fica esse ponto (teste da geometria)
        vazio.then(Commands.literal("onde")
                .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                        .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x");
                                    int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z");
                                    VazioLugares.Lugar l = VazioLugares.lugarEm(x, z);
                                    String nome = l == null ? "terra de ninguem (anel 20..45)" : l.nome();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "\u00a7a" + x + "," + z + " \u00a78-> \u00a7f" + nome), false);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // (5.23.0) /vazio lugares — a tabela dos lugares e onde a pessoa está agora
        vazio.then(Commands.literal("lugares").executes(ctx -> {
            ServerPlayer quem = ctx.getSource().getPlayerOrException();
            ctx.getSource().sendSuccess(() -> Component.literal(VazioLugares.listar(quem)), false);
            return Command.SINGLE_SUCCESS;
        }));

        // (5.23.0) /vazio lugar <id> — toca o aviso de um lugar na hora (teste do ADM)
        vazio.then(Commands.literal("lugar")
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            for (VazioLugares.Lugar l : VazioLugares.TODOS) {
                                b.suggest(l.id());
                            }
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                            VazioLugares.Lugar l = VazioLugares.porId(id);
                            if (l == null) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "\u00a7cLugar desconhecido. Use: praca, portal, santuario, cidade, ermos"));
                                return 0;
                            }
                            ServerPlayer quem = ctx.getSource().getPlayer();
                            int n = 0;
                            if (quem != null) {
                                VazioLugares.avisar(quem, (net.minecraft.server.level.ServerLevel) quem.level(), l);
                                n = 1;
                            } else {
                                // do console: manda para quem estiver no vazio (teste)
                                var nv = ctx.getSource().getServer()
                                        .getLevel(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
                                if (nv != null) {
                                    for (ServerPlayer q : nv.players()) {
                                        VazioLugares.avisar(q, nv, l);
                                        n++;
                                    }
                                }
                            }
                            final int enviados = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7aAviso de \u00a7f" + l.nome() + "\u00a7a enviado para "
                                            + enviados + " jogador(es)."), false);
                            return Command.SINGLE_SUCCESS;
                        })));

        // (5.7.8) /vazio entrar — o admin vai pra dimensão do exílio testar o
        // ambiente (andar no escuro, monólitos, sussurros) sem punir ninguém.
        vazio.then(Commands.literal("entrar").executes(ctx -> {
            ServerPlayer quem = ctx.getSource().getPlayerOrException();
            // (5.36.0) entrar no vazio LIMPA qualquer marca do Juízo. Era essa marca que fazia
            // a pessoa, ao morrer no vazio, reaparecer na sala do Julgamento.
            JuizoFinal.sair(quem);
            NeptuneEventos.entrarNoExilio(quem);
            // (5.13.5) feedback SÓ para quem digitou: o dono não quer mensagem de admin
            // aparecendo na tela dos outros jogadores.
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a78Voc\u00ea est\u00e1 no ex\u00edlio: d\u00e1 pra andar, construir e usar "
                            + "comando normalmente.\n\u00a78Pra sair: \u00a7f/vazio soltar "
                            + quem.getName().getString()), false);
            return Command.SINGLE_SUCCESS;
        }));

        vazio.then(Commands.literal("exilar")
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    ServerPlayer quem = ctx.getSource().getPlayer();
                    NeptuneEventos.exilar(alvo, quem);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a78Exilado no vazio: \u00a75" + alvo.getName().getString()), false);
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

        vazio.then(Commands.literal("pedestais")
                .executes(ctx -> {
                    ServerPlayer quem = ctx.getSource().getPlayerOrException();
                    RPWorldData dados = RPWorldData.get(quem.getServer());
                    net.minecraft.server.level.ServerLevel nv = (net.minecraft.server.level.ServerLevel) quem.level();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75Pedestais: \u00a7f" + dados.quantosPedestais(quem.getUUID()) + "/8"
                                    + "\u00a78 · altares esvaziados: \u00a7f"
                                    + Integer.bitCount(dados.getSantuarios(quem.getUUID())) + "/8\n"
                                    + "\u00a78anel em volta da praça: " + VazioReino.listaPedestais()
                                    + "\n" + VazioReino.relatorioPortal(nv, quem.getUUID())
                                    + "\n\u00a78fila: " + VazioReino.construindo()), false);
                    return Command.SINGLE_SUCCESS;
                })
                // (5.13.3) TESTE: enche os 8 na hora e abre o portal da praça.
                // O dono ficou preso no vazio porque o portal não abria com os 8
                // fragmentos — este comando isola a causa em 1 segundo.
                .then(Commands.literal("encher").executes(ctx -> {
                    ServerPlayer quem = ctx.getSource().getPlayerOrException();
                    int cheios = VazioReino.encherPedestais(quem);
                    if (cheios < 0) {
                        ctx.getSource().sendFailure(Component.literal(
                                "\u00a7cSó funciona dentro de rptag:exilio."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00a75Pedestais cheios: \u00a7f" + cheios + "/8\u00a78 — o portal da "
                                    + "praça foi aberto na marra (5,5 s de animação).\n"
                                    + "\u00a78Confira com \u00a7f/vazio pedestais\u00a78."), false);
                    return Command.SINGLE_SUCCESS;
                })));

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

    /**
     * (5.33.0) <b>/vazio soltar poder &lt;qual&gt; [jogador]</b> — o "soltar poderes" de volta.
     *
     * <p>O dono (pedido 90): <i>"o vazio soltar não solta mais o player escolhido, mostra apenas
     * o soltar poderes, corrige"</i>. Então o comando agora faz <b>as duas coisas</b>: sem
     * jogador escolhido ele arremessa o poder normalmente (mira/leque) e, com um jogador
     * escolhido, o <b>banir</b> manda exatamente aquela pessoa para a dimensão do banir — nada de
     * banir o próprio conjurador por acidente.
     */
    private static int soltarPoder(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            ServerPlayer alvo) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String nome = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "qual");
        VazioPoderes.Poder poder = VazioPoderes.Poder.porId(nome);
        if (poder == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cPoderes: raio, orbe, onda, olhos, penas, banir."));
            return 0;
        }
        // com jogador escolhido + banir: a vítima é ELE, nunca o conjurador
        if (alvo != null && poder == VazioPoderes.Poder.BANIR) {
            if (alvo.getUUID().equals(p.getUUID())) {
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cVoc\u00ea n\u00e3o pode banir VOC\u00ca mesmo."));
                return 0;
            }
            boolean ok = VazioPoderes.banirAlvo(p, alvo);
            ctx.getSource().sendSuccess(() -> Component.literal(ok
                    ? "\u00a78Banido: \u00a75" + alvo.getName().getString()
                    : "\u00a7cA dimens\u00e3o do banir n\u00e3o respondeu."), false);
            return ok ? Command.SINGLE_SUCCESS : 0;
        }
        if (alvo != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a77O jogador escolhido s\u00f3 muda o poder \u00a75banir\u00a77."), false);
        }
        int r = VazioPoderes.usar(p, poder);
        if (r == 0) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cN\u00e3o saiu: poder fora do vazio/reino, em recarga ou sem alvo na mira."));
        }
        return r == 1 ? Command.SINGLE_SUCCESS : 0;
    }
}
