package dev.rptag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (3.80.0) VIDAS CANÔNICAS: TODO jogador já tem (não é efeito individual).
 * O admin liga o MODO com {@code /rp admin canon on} — e, enquanto durar,
 * QUALQUER jogador que morrer passa pelo ritual ao renascer: fica
 * CONGELADO ~5 s ("não vai se mexer"), recebe o aviso texturizado
 * "VOCÊ PERDEU UMA VIDA" (estilo 4, estático — as letras NÃO se mexem) e
 * ouve O SOM (ogg próprio "canon_death", o da lore) — só ele ouve, 1,5 s
 * DEPOIS do STRONDO do trovão que abre o ritual (tela avermelhada
 * pulsando). Ao LIGAR/DESLIGAR, o SERVIDOR TODO recebe o BANNER
 * "❀ LORE ON/OFF ❀" (CanonBannerPayload) — nada vai pro chat.
 *
 * <p>(3.82.0) Cada player começa com {@code VIDAS_INICIAIS} (5) vidas
 * canônicas (persistidas). Depois de perder uma, fica protegido por
 * {@code COOLDOWN_MS} (5 min): morrer seguido não acumula perdas.
 *
 * <p>(3.83.0) Ao perder a ÚLTIMA ("VOCÊ PERDEU SUA ÚLTIMA VIDA — TOME
 * CUIDADO"), o jogador ainda leva um MEDO ABSURDO: 30 s do efeito rptag:medo
 * (véu que esconde + tremedeira) por cima de todo o ritual.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class CanonLife {

    private CanonLife() {
    }

    /** aviso (pedido literal do dono — curto e direto). */
    private static final String FRASE = "VOCÊ PERDEU UMA VIDA";

    /** quanto tempo o player fica sem se mexer (ms). */
    private static final int CONGELAMENTO_MS = 5000;
    /** (3.82.0) vidas canônicas que TODO player tem no começo. */
    private static final int VIDAS_INICIAIS = 5;
    /** (3.82.0) LIMITE anti-chain: depois de perder uma vida, o player
     * fica PROTEGIDO por este tempo — se morrer de novo logo em seguida
     * (morte seguida), NÃO perde mais vidas de uma vez. */
    private static final long COOLDOWN_MS = 5 * 60_000L; // 5 minutos
    /** proteção ativa até quando (ms de parede) — por jogador. */
    private static final Map<UUID, Long> PROTECAO_ATE = new HashMap<>();

    /** congelamento: posição âncora + até quando (ms de parede). */
    private record Freeze(long until, double x, double y, double z) {
    }

    private static final Map<UUID, Freeze> FREEZES = new HashMap<>();

    /** (4.1.0) mortes que NÃO contam: quem morreu levou o golpe de alguém
     *  que estava OFF RP. Marcado no LivingDeathEvent (o respawn já não
     *  sabe quem matou) e consumido em {@link #aoRenascer}. */
    private static final Set<UUID> MORTE_NAO_CONTA = new HashSet<>();

    /**
     * (4.1.0) Na HORA da morte, olha quem matou. Se o matador é um JOGADOR
     * que está OFF RP, essa morte não é canônica: a vítima não perde vida.
     * (Morte sem matador — queda, mob, lava, /kill — continua contando.)
     */
    @SubscribeEvent
    public static void aoMorrer(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer vitima)) {
            return;
        }
        RPWorldData wd = RPWorldData.get(vitima.server);
        if (!wd.isCanonEvent()) {
            return;
        }
        Entity agressor = e.getSource().getEntity();
        if (!(agressor instanceof ServerPlayer matador)) {
            return; // morte "do mundo" (queda, mob, fogo...): conta normal
        }
        if (matador.getUUID().equals(vitima.getUUID())) {
            return; // se matou sozinho: conta normal
        }
        if (wd.isInRp(matador.getUUID())) {
            return; // matador em RP: morte canônica normal
        }
        // matador OFF RP -> a morte foi fora do RP: NÃO desconta vida
        MORTE_NAO_CONTA.add(vitima.getUUID());
        RPTagMod.LOGGER.info("RP Tag: {} morreu para {} (OFF RP) — VIDA CANONICA PRESERVADA",
                vitima.getName().getString(), matador.getName().getString());
    }

    @SubscribeEvent
    public static void aoRenascer(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        RPWorldData wd = RPWorldData.get(p.server);
        if (!wd.isCanonEvent()) {
            return;
        }
        // (4.1.0) matador OFF RP: essa morte NÃO desconta vida (marca
        // posta no LivingDeathEvent; some assim que é lida)
        if (MORTE_NAO_CONTA.remove(p.getUUID())) {
            return;
        }
        long now = System.currentTimeMillis();
        // (3.82.0) LIMITE: morte seguida dentro do cooldown não perde vida
        if (now < PROTECAO_ATE.getOrDefault(p.getUUID(), 0L)) {
            return;
        }
        int vidas = wd.getCanonLives(p.getUUID());
        if (vidas <= 0) {
            return; // estoque zerado: não há mais o que perder
        }
        wd.setCanonLives(p.getUUID(), vidas - 1);
        PROTECAO_ATE.put(p.getUUID(), now + COOLDOWN_MS);
        ritualDeVidaPerdida(p, vidas - 1);
    }

    private static void ritualDeVidaPerdida(ServerPlayer player, int restam) {
        long now = System.currentTimeMillis();
        FREEZES.put(player.getUUID(), new Freeze(now + CONGELAMENTO_MS,
                player.getX(), player.getY(), player.getZ()));
        String msg = restam > 0
                ? FRASE + " \u2014 RESTAM " + restam
                : "VOCÊ PERDEU SUA ÚLTIMA VIDA \u2014 TOME CUIDADO";
        PacketDistributor.sendToPlayer(player, new ScreenTextPayload(msg, 8, 4));
        if (restam == 0) {
            // (3.83.0) MEDO ABSURDO: a última vida dispara o efeito de MEDO
            // no MÁXIMO (30 s = o teto do sistema, o mesmo do /rp admin
            // medo) — véu azul-noite que ESCONDE a tela + tremedeira de
            // camera + icone no HUD. A pessoa fica PREOCUPADA de verdade.
            PacketDistributor.sendToPlayer(player, new FearPayload(30));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    RPEffects.MEDO, 30 * 20, 0, false, false));
            RPTagMod.LOGGER.info("RP Tag: {} perdeu a ÚLTIMA vida canonica — MEDO ABSURDO ativado",
                    player.getName().getString());
        }
        // (3.78.0) O STRONDO: trovão NA HORA (o audio da lore entra ~1,5 s
        // depois, tocado pelo PROPRIO CLIENTE ao ver o véu — CanonLife.java)
        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 3.0F, 0.8F);
        // (4.9.0) QUEM ESTÁ POR PERTO FICA SABENDO: a perda de vida não é
        // segredo — quem viu (ou ouviu o trovão) recebe o recado no chat.
        avisarPorPerto(player);
        RPTagMod.LOGGER.info("RP Tag: VIDA CANÔNICA — {} perdeu uma vida (congelado 5s + som)",
                player.getName().getString());
    }

    /** (4.9.0) Raio (blocos) do aviso "perdeu uma vida" para os outros jogadores. */
    public static final double RAIO_AVISO_VIDA = 32.0D;

    /**
     * (4.9.0) "Fulano perdeu uma vida - tome cuidado" para todo mundo num raio de
     * {@link #RAIO_AVISO_VIDA} blocos (mesmo mundo), MENOS quem perdeu — ele já
     * recebe o aviso grande na tela. Regra do dono: a perda de vida canônica
     * tem testemunhas.
     */
    private static void avisarPorPerto(ServerPlayer perdedor) {
        if (perdedor.getServer() == null) {
            return;
        }
        Component recado = Component.literal(
                        perdedor.getName().getString() + " perdeu uma vida - tome cuidado")
                .withStyle(ChatFormatting.RED);
        double limite = RAIO_AVISO_VIDA * RAIO_AVISO_VIDA;
        for (ServerPlayer outro : perdedor.getServer().getPlayerList().getPlayers()) {
            if (outro == perdedor) {
                continue;
            }
            if (outro.level() != perdedor.level()) {
                continue;                      // outro mundo: não viu nada
            }
            if (outro.distanceToSqr(perdedor) > limite) {
                continue;
            }
            outro.sendSystemMessage(recado);
        }
    }

    @SubscribeEvent
    public static void aCadaTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isRemoved()) {
            return;
        }
        Freeze f = FREEZES.get(p.getUUID());
        if (f == null) {
            return;
        }
        if (System.currentTimeMillis() >= f.until()) {
            FREEZES.remove(p.getUUID());
            return;
        }
        double dx = p.getX() - f.x();
        double dy = p.getY() - f.y();
        double dz = p.getZ() - f.z();
        if (dx * dx + dy * dy + dz * dz > 0.05D) {
            p.teleportTo(f.x(), f.y(), f.z());
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void aoSair(PlayerEvent.PlayerLoggedOutEvent e) {
        FREEZES.remove(e.getEntity().getUUID());
        MORTE_NAO_CONTA.remove(e.getEntity().getUUID());
        // (3.82.0) PROTECAO_ATE é de propósito NÃO limpa no logout: sair e
        // voltar não burla o limite anti-mortes-seguidas
    }
}
