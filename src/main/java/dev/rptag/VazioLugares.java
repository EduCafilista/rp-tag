package dev.rptag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * (5.23.0) OS LUGARES DO VAZIO TÊM NOME — e o nome aparece TODA VEZ que a pessoa entra.
 *
 * <p>Pedido do dono: <i>"todas as vezes que o player entrar na 'cidade' tem que ter o aviso da
 * cidade, ou em outros locais"</i>. Então cada região da dimensão ganhou um <b>aviso de entrada</b>
 * com:
 * <ul>
 *   <li><b>título grande</b> na tela (o do próprio jogo: aparece, fica e some);</li>
 *   <li><b>subtítulo</b> com a frase do lugar;</li>
 *   <li>uma linha de <b>lore no chat</b> em itálico cinza;</li>
 *   <li>um <b>som</b> grave do vazio e um anel de partículas de alma.</li>
 * </ul>
 *
 * <p>Diferente das lorezones ({@link LoreZones}, que são "uma vez por sessão"), aqui é
 * <b>toda entrada</b>: sai da cidade e volta, o aviso toca de novo. Para não piscar quando a
 * pessoa anda em cima da borda, a saída só é considerada 8 blocos <b>depois</b> do raio de
 * entrada (histerese).
 *
 * <p>Os lugares são medidos por posição (x,z) a partir da praça, sem bloco nenhum no mundo:
 * <pre>
 *   A PRAÇA            até 20 blocos do centro
 *   O PORTAL           14 blocos em volta do portal negro
 *   UM SANTUÁRIO       20 blocos em volta de cada um dos 8 altares
 *   A CIDADE MORTA     do anel de 45 até 300 blocos (onde a cidade fixa foi erguida)
 *   OS ERMOS           além de 300 blocos (onde a cidade não chega)
 * </pre>
 */
public final class VazioLugares {

    private VazioLugares() {
    }

    /** (5.23.0) um lugar do vazio: nome, frase, cor do título e som. */
    public record Lugar(String id, String nome, String frase, ChatFormatting cor, float tom) {
    }

    // ------------------------------------------------------------------ os lugares
    private static final Lugar PRACA = new Lugar("praca", "A PRAÇA",
            "o único lugar do vazio que ainda tem luz", ChatFormatting.GOLD, 1.25F);
    private static final Lugar PORTAL = new Lugar("portal", "O PORTAL",
            "a saída não gosta de emprestar ninguém", ChatFormatting.LIGHT_PURPLE, 0.85F);
    private static final Lugar SANTUARIO = new Lugar("santuario", "UM SANTUÁRIO QUEBRADO",
            "alguém rezou aqui. não adiantou", ChatFormatting.DARK_AQUA, 0.7F);
    private static final Lugar CIDADE = new Lugar("cidade", "A CIDADE MORTA",
            "ninguém mora aqui há muito tempo", ChatFormatting.DARK_RED, 0.55F);
    private static final Lugar ERMOS = new Lugar("ermos", "OS ERMOS",
            "aqui nem os ecos chegam", ChatFormatting.DARK_GRAY, 0.4F);

    /** todos, na ordem em que aparecem no `/vazio lugares`. */
    public static final Lugar[] TODOS = { PRACA, PORTAL, SANTUARIO, CIDADE, ERMOS };

    // ------------------------------------------------------------------ raios
    private static final double R_PRACA = 20.0;
    private static final double R_PORTAL = 14.0;
    private static final double R_SANTUARIO = 20.0;
    private static final double R_CIDADE_MIN = 45.0, R_CIDADE_MAX = 300.0;
    /** quanto a pessoa tem de ANDAR para "sair" de um lugar (não pisca na borda). */
    private static final double FOLGA = 8.0;
    /** o aviso de entrada não repete antes disto (ms) — proteção contra teleporte maluco. */
    private static final long ESPERA_MS = 1500L;

    /** em que lugar cada jogador está agora (id; ausente = nenhum). */
    private static final Map<UUID, String> DENTRO = new HashMap<>();
    /** quando foi o último aviso de cada jogador. */
    private static final Map<UUID, Long> ULTIMO = new HashMap<>();
    /** quando cada jogador foi conferido pela última vez. */
    private static final Map<UUID, Long> CONFERIDO = new HashMap<>();

    /** esquece o jogador (saiu do servidor / trocou de dimensão). */
    public static void esquecer(UUID id) {
        DENTRO.remove(id);
        ULTIMO.remove(id);
        CONFERIDO.remove(id);
    }

    /** qual lugar é este ponto (x,z) — null = nenhum. */
    public static Lugar lugarEm(double x, double z) {
        double d = Math.hypot(x, z);
        if (Math.hypot(x - VazioGeometria.PORTAL[0], z - VazioGeometria.PORTAL[1]) <= R_PORTAL) {
            return PORTAL;
        }
        if (d <= R_PRACA) {
            return PRACA;
        }
        if (d <= 70.0) {
            for (int[] alt : VazioGeometria.SANTUARIO) {
                if (Math.hypot(x - alt[0], z - alt[1]) <= R_SANTUARIO) {
                    return SANTUARIO;
                }
            }
        }
        // os altares vão até 135 blocos: procura de novo sem o corte de 70 (barato)
        for (int[] alt : VazioGeometria.SANTUARIO) {
            if (Math.hypot(x - alt[0], z - alt[1]) <= R_SANTUARIO) {
                return SANTUARIO;
            }
        }
        if (d >= R_CIDADE_MIN && d <= R_CIDADE_MAX) {
            return CIDADE;
        }
        if (d > R_CIDADE_MAX) {
            return ERMOS;
        }
        return null;                             // anel 20..45: a terra de ninguém
    }

    /**
     * (5.23.0) chamado no tick do vazio, uma vez por jogador. Confere de 400 em 400 ms e,
     * quando a pessoa ENTRA num lugar, manda o aviso (título + subtítulo + chat + som).
     */
    public static void tick(ServerLevel nv, ServerPlayer p, long agora) {
        try {
            Long visto = CONFERIDO.get(p.getUUID());
            if (visto != null && agora - visto < 400L) {
                return;
            }
            CONFERIDO.put(p.getUUID(), agora);

            Lugar l = decidir(p.getUUID(), p.getX(), p.getZ(), agora);
            if (l != null) {
                avisar(p, nv, l);                // entrou (ou voltou) num lugar: avisa de novo
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: aviso de lugar do vazio falhou (o jogo segue)", t);
        }
    }

    /**
     * (5.23.0) a DECISÃO pura (sem tela nenhuma): dado onde a pessoa está e a hora, diz se é hora
     * de anunciar um lugar e qual. Atualiza a memória de lugar. Devolve {@code null} quando não
     * muda nada (mesmo lugar, terra de ninguém, ou aviso recente demais).
     */
    public static Lugar decidir(UUID id, double x, double z, long agora) {
        Lugar atual = lugarEm(x, z);
        if (atual == null) {
            DENTRO.remove(id);
            return null;
        }
        if (atual.id().equals(DENTRO.get(id))) {
            return null;                         // continua no mesmo lugar
        }
        DENTRO.put(id, atual.id());
        Long ultimo = ULTIMO.get(id);
        if (ultimo != null && agora - ultimo < ESPERA_MS) {
            return null;                         // teleporte em série: não metralha a tela
        }
        ULTIMO.put(id, agora);
        return atual;
    }

    /** manda o aviso de um lugar para o jogador. */
    public static void avisar(ServerPlayer p, ServerLevel nv, Lugar lugar) {
        // 1) título e subtítulo na tela (fade in 10 ticks, fica 45, some 15)
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 45, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(lugar.frase()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(lugar.nome()).withStyle(lugar.cor(), ChatFormatting.BOLD)));
        // 2) a linha de lore no chat
        p.displayClientMessage(Component.literal("§8» §7" + lugar.nome() + " §8· §8" + lugar.frase()), false);
        // 3) o som grave do vazio + o anel de almas subindo
        nv.playSound(null, p.blockPosition(), RPTagMod.SOM_VAZIO.get(), SoundSource.AMBIENT,
                0.75F, lugar.tom());
        nv.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                p.getX(), p.getY() + 1.0, p.getZ(), 18, 1.4, 0.35, 1.4, 0.02);
    }

    /** texto do `/vazio lugares`: a tabela + onde a pessoa está agora. */
    public static String listar(ServerPlayer p) {
        StringBuilder sb = new StringBuilder("§8Lugares do vazio (o aviso toca TODA vez que você entra):");
        Lugar agora = lugarEm(p.getX(), p.getZ());
        for (Lugar l : TODOS) {
            boolean aqui = agora != null && agora.id().equals(l.id());
            sb.append("\n").append(aqui ? "§a▶ " : "§8· ").append("§7").append(l.nome())
                    .append(" §8— §8").append(l.frase());
        }
        // onde exatamente
        double d = Math.hypot(p.getX(), p.getZ());
        sb.append("\n§8Você está a §7").append((int) Math.round(d)).append("§8 blocos da praça");
        if (agora != null) {
            sb.append(" §8· dentro de §7").append(agora.nome());
        }
        return sb.toString();
    }

    /** o aviso de um lugar pelo id (para o comando de teste do ADM). */
    public static Lugar porId(String id) {
        for (Lugar l : TODOS) {
            if (l.id().equalsIgnoreCase(id)) {
                return l;
            }
        }
        return null;
    }

    /** (5.23.0) o jogador trocou de dimensão: esquece onde ele estava. */
    public static void aoTrocarDimensao(ServerPlayer p, net.minecraft.resources.ResourceKey<Level> de,
            net.minecraft.resources.ResourceKey<Level> para) {
        if (!de.equals(para)) {
            DENTRO.remove(p.getUUID());
            ULTIMO.remove(p.getUUID());
        }
    }
}
