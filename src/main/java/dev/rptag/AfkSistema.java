package dev.rptag;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * (4.5.0) SISTEMA AFK — "quem para de jogar sai do personagem".
 *
 * <p>Duas portas de entrada:
 * <ul>
 *   <li><b>/afk</b> — o jogador avisa na mão (é anunciado pra todo mundo);</li>
 *   <li><b>inatividade</b> — o servidor percebe sozinho (silencioso).</li>
 * </ul>
 *
 * <p>Ao entrar em AFK o jogador <b>sai do RP automaticamente</b> (a tag ● RP cai):
 * parado no meio do mundo ele não deve continuar sendo um personagem — é a regra
 * que o dono pediu ("AFK desliga RP sozinho"). Ao voltar, ele retorna em OFF RP e
 * liga quando quiser (sem surpresa: o mod não religa sozinho).
 *
 * <p><b>Como a detecção funciona (lições do in-rp):</b>
 * <ul>
 *   <li>usa o <b>lastActionTime do próprio vanilla</b> — ele é carimbado com um
 *       relógio MONOTÔNICO ({@link Util#getMillis()}), então mudar o relógio da
 *       máquina não engana o sistema;</li>
 *   <li>a varredura roda a cada {@value #VARREDURA_TICKS} ticks (5 s), não a cada
 *       tick: jogador ativo custa uma conta de subtração a cada 5 s;</li>
 *   <li>quem JÁ está em AFK é checado por tick (movimento/olhar) — só eles;</li>
 *   <li>carência de 1 s ao entrar: os pacotes do próprio /afk não acordam o cara;</li>
 *   <li>ao acordar, {@code resetLastActionTime()} — senão a próxima varredura
 *       marcaria AFK de novo em segundos (bug clássico, já corrigido lá).</li>
 * </ul>
 *
 * <p>Tempo padrão: {@value #TIMEOUT_PADRAO_S} s parado. Muda com
 * {@code /rp admin afk tempo <segundos>} (0 = desliga a detecção automática).
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class AfkSistema {

    /** varredura de inatividade: 100 ticks = 5 s. */
    public static final int VARREDURA_TICKS = 100;
    /** tempo parado (segundos) até virar AFK sozinho. */
    public static final int TIMEOUT_PADRAO_S = 300;
    /** carência depois de entrar em AFK (ms) — os pacotes do comando não acordam. */
    private static final long CARENCIA_MS = 1000L;
    /** deslocamento² que acorda: ~0,15 bloco (acima do jitter do cliente). */
    private static final double ACORDA_MOVE2 = 0.0225D;
    /** giro de cabeça (graus) que acorda. */
    private static final float ACORDA_GIRO = 2.0F;
    /** anti-spam do /afk. */
    private static final long COOLDOWN_MS = 3000L;

    /** quem está AFK. */
    private static final Set<UUID> AFK = ConcurrentHashMap.newKeySet();
    /** pose no momento em que entrou em AFK (pra detectar a volta). */
    private static final Map<UUID, Pose> POSES = new ConcurrentHashMap<>();
    /** último uso do /afk (anti-spam). */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    private static int contador;

    /** pose congelada do jogador quando virou AFK. */
    private record Pose(double x, double y, double z, float yaw, float pitch, long entrou) {}

    private AfkSistema() {
    }

    // ================================================================ estado
    public static boolean isAfk(ServerPlayer p) {
        return p != null && AFK.contains(p.getUUID());
    }

    public static int totalAfk() {
        return AFK.size();
    }

    /** tempo (s) parado até virar AFK; 0 = automático desligado. Persistido no mundo. */
    public static int timeoutSegundos(MinecraftServer server) {
        return RPWorldData.get(server).getAfkTimeout();
    }

    // ================================================================ entrar/sair
    /**
     * Marca como AFK e <b>desliga o RP</b>. Silencioso de propósito: quem chama
     * decide se anuncia (o /afk anuncia, a varredura não).
     */
    public static void entrar(ServerPlayer p) {
        if (p == null || AFK.contains(p.getUUID())) return;
        AFK.add(p.getUUID());
        POSES.put(p.getUUID(), new Pose(p.getX(), p.getY(), p.getZ(),
                p.getYRot(), p.getXRot(), Util.getMillis()));

        RPWorldData wd = RPWorldData.get(p.server);
        if (wd.isInRp(p.getUUID())) {
            wd.setInRp(p.getUUID(), false);
            p.displayClientMessage(Component.literal(
                    "Você saiu do RP automaticamente (AFK). Ao voltar, ligue com /rp on.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        RPTagMod.LOGGER.info("RP Tag: {} entrou em AFK (RP desligado)", p.getName().getString());
    }

    /**
     * (4.5.0) Prende o tempo (segundos) na faixa que o comando aceita: 0 desliga o
     * automático, 86400 = um dia. Serve de rede de segurança pra save editado na mão.
     */
    public static int normalizarTimeout(int segundos) {
        return Math.max(0, Math.min(86400, segundos));
    }

    /**
     * Acorda (sai do AFK). Seguro chamar sempre: sai na hora se a pessoa não
     * estava AFK, então duas fontes ao mesmo tempo não duplicam feedback.
     */
    public static void acordar(ServerPlayer p) {
        if (p == null || !AFK.remove(p.getUUID())) return;
        POSES.remove(p.getUUID());
        // sem isto a próxima varredura marcaria AFK de novo em segundos
        p.resetLastActionTime();
        p.displayClientMessage(Component.literal("\u25C0 De volta! (fora do AFK)")
                .withStyle(ChatFormatting.GREEN), true);
        p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.3F);
        RPTagMod.LOGGER.info("RP Tag: {} voltou do AFK", p.getName().getString());
    }

    /** /afk — alterna. Devolve true se entrou, false se saiu/negado. */
    public static boolean alternar(ServerPlayer p) {
        long agora = Util.getMillis();
        Long ultimo = COOLDOWN.get(p.getUUID());
        if (ultimo != null && agora - ultimo < COOLDOWN_MS) {
            p.displayClientMessage(Component.literal("Calma — espera 3 s pra usar o /afk de novo.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        COOLDOWN.put(p.getUUID(), agora);

        if (AFK.contains(p.getUUID())) {
            acordar(p);
            return false;
        }
        entrar(p);
        // o /afk é anunciado (a varredura não é)
        p.server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\u2592 " + p.getName().getString() + " está AFK (fora do RP)").withStyle(ChatFormatting.GRAY),
                false);
        p.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 0.9F);
        return true;
    }

    // ================================================================ varredura
    @SubscribeEvent
    public static void aCadaTickDoServidor(ServerTickEvent.Post e) {
        if (++contador < VARREDURA_TICKS) return;
        contador = 0;

        MinecraftServer server = e.getServer();
        int limiteS = timeoutSegundos(server);
        if (limiteS <= 0) {
            // desligou no meio do caminho: solta todo mundo (não deixa ninguém preso)
            if (!AFK.isEmpty()) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (AFK.remove(p.getUUID())) {
                        POSES.remove(p.getUUID());
                        p.displayClientMessage(Component.literal(
                                "AFK automático desligado — você não está mais AFK.")
                                .withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
            return;
        }

        long agora = Util.getMillis();
        long limiteMs = limiteS * 1000L;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == null || AFK.contains(p.getUUID())) continue;
            if (agora - p.getLastActionTime() >= limiteMs) {
                entrar(p);
            }
        }
    }

    @SubscribeEvent
    public static void aCadaTickDoJogador(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !AFK.contains(p.getUUID())) return;
        Pose pose = POSES.get(p.getUUID());
        if (pose == null) {
            // veio de estado salvo: começa a contar daqui
            POSES.put(p.getUUID(), new Pose(p.getX(), p.getY(), p.getZ(),
                    p.getYRot(), p.getXRot(), Util.getMillis()));
            return;
        }
        if (Util.getMillis() - pose.entrou() < CARENCIA_MS) return;

        double dx = p.getX() - pose.x(), dy = p.getY() - pose.y(), dz = p.getZ() - pose.z();
        boolean moveu = dx * dx + dy * dy + dz * dz > ACORDA_MOVE2;
        boolean olhou = Math.abs(p.getYRot() - pose.yaw()) > ACORDA_GIRO
                || Math.abs(p.getXRot() - pose.pitch()) > ACORDA_GIRO;
        if (moveu || olhou) acordar(p);
    }

    // ================================================================ acordar por ação
    /** receiveCanceled: o chat local cancela o evento — o cara digitou, logo está ali. */
    @SubscribeEvent(receiveCanceled = true)
    public static void aoFalar(ServerChatEvent e) {
        acordar(e.getPlayer());
    }

    @SubscribeEvent
    public static void aoBater(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) acordar(p);
    }

    @SubscribeEvent
    public static void aoClicar(PlayerInteractEvent.RightClickBlock e) {
        if (e.getEntity() instanceof ServerPlayer p) acordar(p);
    }

    // ================================================================ ciclo de vida
    @SubscribeEvent
    public static void aoEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // flag velha de um desligamento sujo não pode sobreviver ao login
        AFK.remove(p.getUUID());
        POSES.remove(p.getUUID());
        COOLDOWN.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AFK.remove(p.getUUID());
        POSES.remove(p.getUUID());
        COOLDOWN.remove(p.getUUID());
    }

    /** Nada de estado vazando pro próximo mundo carregado no mesmo JVM. */
    @SubscribeEvent
    public static void aoPararServidor(ServerStoppingEvent e) {
        AFK.clear();
        POSES.clear();
        COOLDOWN.clear();
        contador = 0;
    }
}
