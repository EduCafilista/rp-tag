package dev.rptag;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TitleCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * (5.10.1) TIRA O {@code /title} DO JOGO.
 *
 * <p>O dono perguntou se o {@code /title} era do mod. <b>Não é</b>: é comando
 * VANILLA do Minecraft ({@code /title <alvo> title|subtitle|actionbar|times|clear|reset})
 * — já vem no jogo puro, sem mod nenhum. Como ele se confunde com o
 * {@code /titulo} do RP Tag (e o dono pediu "deixa só o /titulo"), ele é
 * removido da árvore de comandos.
 *
 * <p><b>O detalhe que custou três rodadas de teste:</b> o Brigadier guarda os
 * filhos da raiz em três mapas ({@code children}, {@code literals} e
 * {@code arguments}) e o parser do jogo lê o {@code literals} — o
 * {@code getChildren()}, que é só a view do {@code children}, não adianta. A
 * remoção "funcionava" no log ({@code title=false}, {@code getChild} nulo) e o
 * {@code /title} continuava rodando, porque o nó seguia no {@code literals}.
 * Hoje {@link #tirar} limpa os dois mapas; com isso o comando some de verdade
 * ({@code title @a times ...} passa a dar "Unknown or incomplete command").
 *
 * <p>A ação sempre acontece no dispatcher VIVO do servidor
 * ({@link #decidir(MinecraftServer)} → {@code server.getCommands().getDispatcher()}).
 *
 * <p><b>Cuidado:</b> o {@code /title} também é usado por datapacks e mapas
 * prontos ({@code execute run title ...}). Por isso a remoção tem volta:
 * {@code /rp admin titlevanilla on} devolve o comando na hora (e fica salvo no
 * mundo); {@code off} tira de novo.
 *
 * <p>Regra da casa: qualquer erro aqui só deixa o {@code /title} onde estava —
 * nunca derruba o servidor.
 */
public final class TituloVanilla {

    private static volatile CommandDispatcher<CommandSourceStack> registrado;
    private static volatile CommandBuildContext contexto;

    private TituloVanilla() {
    }

    // ------------------------------------------------------------- eventos
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // guarda pra poder DEVOLVER o comando depois (o /rp admin titlevanilla on
        // precisa de um dispatcher e do build context pra registrar de novo)
        registrado = event.getDispatcher();
        contexto = event.getBuildContext();
        // tira já da árvore do evento; o boot confirma no dispatcher VIVO
        tirar(registrado);
        if (tem(registrado)) {
            RPTagMod.LOGGER.info("RP Tag: /title vanilla ainda na arvore do evento; o boot confere de novo");
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        decidir(event.getServer());
    }

    /** Rede de segurança: se um /reload remontou a árvore, tira de novo no login. */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            MinecraftServer server = event.getEntity().getServer();
            if (server != null && !RPWorldData.get(server).comandoTitleVanilla() && tem(server)) {
                tirar(server.getCommands().getDispatcher());
            }
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui conferir o /title no login", erro);
        }
    }

    // ------------------------------------------------------------- ações
    /**
     * Aplica a escolha do mundo no dispatcher VIVO do servidor.
     *
     * @param manterVanilla true = devolve o {@code /title}; false = tira
     */
    public static void decidir(MinecraftServer server) {
        try {
            if (RPWorldData.get(server).comandoTitleVanilla()) {
                devolver(server.getCommands().getDispatcher());
            } else {
                tirar(server.getCommands().getDispatcher());
            }
            mandarComandos(server);
            RPTagMod.LOGGER.info("RP Tag: /title vanilla {} (comando do Minecraft puro, nao do mod)",
                    tem(server) ? "PRESENTE" : "fora");
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui decidir sobre o /title vanilla", erro);
        }
    }

    /** Liga/desliga e já avisa os jogadores (eles precisam da árvore nova). */
    public static void aplicar(MinecraftServer server, boolean manterVanilla) {
        try {
            RPWorldData.get(server).setComandoTitleVanilla(manterVanilla);
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui salvar a escolha do /title", erro);
        }
        decidir(server);
    }

    /** O comando vanilla existe no servidor agora? */
    public static boolean tem(MinecraftServer server) {
        return server != null && tem(server.getCommands().getDispatcher());
    }

    public static boolean tem(CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher != null && dispatcher.getRoot().getChild("title") != null;
    }

    /**
     * Tira o {@code /title} (e tudo debaixo dele, tipo {@code /title times}).
     *
     * <p><b>O pulo do gato (custou duas rodadas de teste):</b> o Brigadier guarda
     * os filhos da raiz em TRÊS lugares — o mapa {@code children}, o mapa
     * {@code literals} e o mapa {@code arguments}. O {@code getChildren()} devolve
     * a view do {@code children}, e o parser do jogo NÃO lê esse mapa: ele lê o
     * {@code literals} ({@code CommandNode.getRelevantNodes}). Resultado: remover
     * só pelo {@code getChildren()} deixava o log dizendo "title=false",
     * {@code getChild("title")} nulo e o {@code /title} continuava rodando.
     *
     * <p>Por isso aqui limpamos os dois mapas: o {@code children} (pra sumir da
     * listagem e do {@code getChild}) e o {@code literals} (pra sumir do parser).
     */
    public static void tirar(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            if (dispatcher == null) {
                return;
            }
            Object raiz = dispatcher.getRoot();
            // 1) a lista pública (é a view do mapa children)
            dispatcher.getRoot().getChildren().removeIf(n -> n.getName().equals("title"));
            // 2) o mapa de literais, que é o que o parser consulta de verdade
            boolean saiuDoParser = removerDoMapa(raiz, "literals", "title");
            // 3) por garantia, o children também (caso a view mude em versão futura)
            boolean saiuDaLista = removerDoMapa(raiz, "children", "title");
            if (dispatcher.getRoot().getChild("title") != null || !saiuDoParser) {
                RPTagMod.LOGGER.warn("RP Tag: /title ainda na arvore (parser={} lista={})",
                        !saiuDoParser, !saiuDaLista);
            }
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui remover o /title vanilla", erro);
        }
    }

    /**
     * Remove uma chave de um dos mapas internos do nó da raiz do Brigadier.
     *
     * @return true se o mapa existia e a chave saiu dele (ou já não estava lá)
     */
    private static boolean removerDoMapa(Object no, String campo, String chave) {
        try {
            java.lang.reflect.Field f =
                    com.mojang.brigadier.tree.CommandNode.class.getDeclaredField(campo);
            f.setAccessible(true);
            Object valor = f.get(no);
            if (valor instanceof java.util.Map<?, ?> mapa) {
                mapa.remove(chave);
                return true;
            }
            return false;
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui limpar o campo {} do /title", campo);
            return false;
        }
    }

    /** Devolve o {@code /title} (registra o comando vanilla de novo). */
    public static void devolver(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            if (dispatcher != null && contexto != null && !tem(dispatcher)) {
                TitleCommand.register(dispatcher, contexto);
            }
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui devolver o /title vanilla", erro);
        }
    }

    private static void mandarComandos(MinecraftServer server) {
        try {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(p);
            }
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui reenviar os comandos", erro);
        }
    }
}
