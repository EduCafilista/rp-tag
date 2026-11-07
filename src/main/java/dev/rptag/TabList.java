package dev.rptag;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (5.19.0) A LISTA DE JOGADORES (TAB) COM CARA DE SERVIDOR — pedido do dono.
 *
 * <p>Ele mandou a print do TAB antigo e pediu: <i>"acha que consegue fazer o tab do servidor
 * ficar desse jeito? nao sei se tem que ser no mod, ou em alguma parte do sv, ou algum plugin"</i>.
 *
 * <p><b>Por que aqui e não num plugin:</b> em NeoForge 1.21.1 não existe plugin (plugin é
 * Bukkit/Paper/Spigot). O TAB é montado pelo SERVIDOR e desenhado pelo cliente, e o servidor
 * manda duas caixas de texto: <i>cabeçalho</i> (em cima da lista) e <i>rodapé</i> (embaixo).
 * É exatamente o que esta classe faz, 100% do lado do servidor — não precisa de cliente
 * modificado nem de outro mod.
 *
 * <p>Cada jogador recebe o SEU rodapé, então o <i>Ping</i> mostrado é o ping de quem está
 * olhando. TPS, memória e uptime são medidos aqui; online é a contagem do servidor.
 *
 * <p>Layout (o mesmo da print):
 * <pre>
 *        ✦ NEPTUNE SMP ✦
 *      explore the deep ✦
 *   ▬▬▬▬▬▬▬  ▬▬▬▬▬▬  ▬▬▬▬▬▬▬
 *             (jogadores)
 *   ▬▬▬▬▬▬▬  ▬▬▬▬▬▬  ▬▬▬▬▬▬▬
 *   • Online » 1/67   • TPS » 20.0   • Ping » 31ms
 *   • Memory » 4775.9 MB / 20480.0 MB   • Uptime » 00:12:28
 * </pre>
 *
 * <p>Liga/desliga com {@code /rp admin tab on|off} (fica salvo no mundo).
 */
public final class TabList {

    private TabList() {
    }

    // ---------------------------------------------------------------- números
    /** durações dos últimos ticks (ms), pra média do TPS. */
    private static final Deque<Double> TICKS = new ArrayDeque<>();
    private static long ultimoTickNano = 0L;
    /** quando o servidor começou a rodar (uptime). */
    private static long inicioMs = 0L;

    /** título grande (linha 1). */
    public static final String TITULO = "NEPTUNE SMP";
    /** subtítulo (linha 2). */
    public static final String SUBTITULO = "explore the deep";

    // ----------------------------------------------------------- cabeçalho
    /** o cabeçalho do TAB (as duas linhas + a régua). */
    public static Component cabecalho() {
        // ✦ NEPTUNE SMP ✦  (NEPTUNE em ciano, SMP em dourado, diamantes em ouro)
        Component linha1 = Component.literal("\u2726 ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("NEPTUNE").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" ").withStyle(ChatFormatting.RESET))
                .append(Component.literal("SMP").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" \u2726").withStyle(ChatFormatting.GOLD));
        // explore the deep ✦
        Component linha2 = Component.literal(SUBTITULO + " ")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC)
                .append(Component.literal("\u2726").withStyle(ChatFormatting.AQUA));
        return Component.empty().append(linha1).append("\n").append(linha2).append("\n").append(regua());
    }

    /** a régua: um traço que "acende" no meio (o mesmo nas duas pontas do TAB). */
    public static Component regua() {
        return Component.literal("\u2588\u2588\u2588\u2588\u2588\u2588\u2588")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(" \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\u2588\u2588\u2588\u2588\u2588\u2588\u2588").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------- rodapé
    /**
     * o rodapé DESTE jogador (o ping é o dele).
     *
     * @param p quem vai olhar
     */
    public static Component rodape(ServerPlayer p, MinecraftServer server) {
        int online = server.getPlayerList().getPlayerCount();
        int max = server.getPlayerList().getMaxPlayers();

        Component linha1 = Component.empty()
                .append(Component.literal("\u25C6 Online \u00BB ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(online + "/" + max).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   "))
                .append(Component.literal("\u25C6 TPS \u00BB ").withStyle(corTps()))
                .append(Component.literal(tpsFormatado()).withStyle(corTps()))
                .append(Component.literal("   "))
                .append(Component.literal("\u25C6 Ping \u00BB ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(Math.max(0, ping(p)) + "ms").withStyle(ChatFormatting.WHITE));

        Component linha2 = Component.empty()
                .append(Component.literal("\u25C6 Memory \u00BB ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(String.format("%.1f", memoriaUsada())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" MB / ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format("%.1f", memoriaMaxima()) + " MB")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   "))
                .append(Component.literal("\u25C6 Uptime \u00BB ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(uptime()).withStyle(ChatFormatting.YELLOW));

        return Component.empty().append(regua()).append("\n").append(linha1).append("\n").append(linha2);
    }

    // -------------------------------------------------------------- medidas
    /** roda a cada tick: guarda a duração do tick pra média do TPS. */
    public static void amostrarTick(MinecraftServer server) {
        if (inicioMs == 0L) {
            inicioMs = System.currentTimeMillis();
        }
        long agora = System.nanoTime();
        if (ultimoTickNano > 0L) {
            double ms = (agora - ultimoTickNano) / 1_000_000.0;
            if (ms > 0 && ms < 5000) {
                TICKS.addLast(ms);
                while (TICKS.size() > 100) {
                    TICKS.removeFirst();
                }
            }
        }
        ultimoTickNano = agora;
    }

    /** TPS pela média dos últimos ~5 s (20 quando está tudo liso). */
    public static double tps() {
        if (TICKS.isEmpty()) {
            return 20.0;
        }
        double soma = 0;
        for (double v : TICKS) {
            soma += v;
        }
        double media = soma / TICKS.size();
        if (media <= 50.0) {
            return 20.0;
        }
        return Math.min(20.0, 1000.0 / media);
    }

    private static String tpsFormatado() {
        return String.format("%.1f", tps());
    }

    /** cor do TPS: verde/liso, amarelo/pesado, vermelho/travando. */
    public static ChatFormatting corTps() {
        double t = tps();
        if (t >= 19.0) {
            return ChatFormatting.GREEN;
        }
        return t >= 15.0 ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    public static double memoriaUsada() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / 1048576.0;
    }

    public static double memoriaMaxima() {
        return Runtime.getRuntime().maxMemory() / 1048576.0;
    }

    /** uptime no formato 00:12:28. */
    public static String uptime() {
        if (inicioMs == 0L) {
            return "00:00:00";
        }
        long s = (System.currentTimeMillis() - inicioMs) / 1000L;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /**
     * (5.19.0) O PING deste jogador, em ms.
     *
     * <p>Em 1.21.1 o número não fica mais no {@code ServerPlayer}: ele mora no
     * {@code ServerCommonPacketListenerImpl#latency()}, que o servidor atualiza a cada
     * keep-alive. Se por algum motivo não der pra ler, devolve 0 (aparece "0ms") em vez de
     * derrubar o TAB.
     */
    public static int ping(ServerPlayer p) {
        try {
            return p.connection.latency();
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------- envio
    /** manda cabeçalho + rodapé pra UM jogador. */
    public static void enviar(ServerPlayer p, MinecraftServer server) {
        try {
            boolean on = RPWorldData.get(server).isTabLigado();
            Component topo = on ? cabecalho() : Component.empty();
            Component baixo = on ? rodape(p, server) : Component.empty();
            p.connection.send(new ClientboundTabListPacket(topo, baixo));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: TAB falhou pra {} (o jogo segue)",
                    p.getGameProfile().getName(), t);
        }
    }

    /** manda pra todo mundo (o rodapé é individual por causa do ping). */
    public static void enviarTodos(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            enviar(p, server);
        }
    }
}
