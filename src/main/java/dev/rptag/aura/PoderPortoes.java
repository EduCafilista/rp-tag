package dev.rptag.aura;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * (5.60.0) O PODER DA AURA DE ILUMINACAO: os PORTES DA BABILONIA.
 *
 * <p>Uma chuva de armas douradas que sai de portais atras/em volta do corpo e voa para onde o
 * jogador esta mirando.
 *
 * <p><b>5.71.0 — SEGURA O BOTAO E NAO PARA.</b> O dono: <i>"o ataque dos portoes pode ser
 * 'infinito' ate o player soltar o botao, ai para de lancar as lancas douradas"</i>. Antes era uma
 * rajada fixa de 1,5 s com 5 s de recarga. Agora e igual arma automatica: apertou a tecla (ou o
 * comando, que faz uma rajada curta de 1 s) comeca a atirar; enquanto estiver segurando, sai arma
 * a cada tick; soltou, parou na hora.
 *
 * <p><b>So ADM e so com a aura LIGADA</b>, e sem mensagem nenhuma pra quem nao pode — o poder e
 * segredo (pedido 118). Se a aura for desligada no meio, para sozinho.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class PoderPortoes {
    /** Ticks de rajada quando o poder vem do comando (sem tecla pra segurar). */
    private static final int RAJADA_DO_COMANDO = 20;
    /** Intervalo minimo entre dois "apertos" (evita macro de clique). */
    private static final int RECARGA_TICKS = 10;
    private static final int POR_TICK = 2;
    private static final float VELOCIDADE = 3.2f;

    private static final Map<UUID, Long> ULTIMO = new HashMap<>();
    /** quem esta com a tecla apertada agora. */
    private static final Set<UUID> SEGURANDO = new HashSet<>();
    /** rajadas do comando: uuid -> ticks que faltam. */
    private static final Map<UUID, Integer> RAJADAS = new HashMap<>();

    /** Chamado pelo pacote da tecla: true = apertou, false = soltou. */
    public static void tecla(ServerPlayer p, boolean segurando) {
        if (!podeUsar(p)) {
            SEGURANDO.remove(p.getUUID());
            return;
        }
        if (!segurando) {
            SEGURANDO.remove(p.getUUID());   // soltou: a chuva de lancas para aqui
            return;
        }
        long agora = p.serverLevel().getGameTime();
        Long ultimo = ULTIMO.get(p.getUUID());
        if (ultimo != null && agora - ultimo < RECARGA_TICKS) {
            return;                          // cooldown curtinho entre dois apertos
        }
        ULTIMO.put(p.getUUID(), agora);
        SEGURANDO.add(p.getUUID());
    }

    /** {@code /iluminacao portoes}: rajada curta de 1 s (o comando nao tem "soltar"). */
    public static void tentarDisparar(ServerPlayer p) {
        if (!podeUsar(p)) {
            return;
        }
        long agora = p.serverLevel().getGameTime();
        Long ultimo = ULTIMO.get(p.getUUID());
        if (ultimo != null && agora - ultimo < RECARGA_TICKS) {
            return;
        }
        ULTIMO.put(p.getUUID(), agora);
        RAJADAS.put(p.getUUID(), RAJADA_DO_COMANDO);
    }

    /** (5.63.0) so ADM com a aura ligada — em silencio pra todo mundo. */
    private static boolean podeUsar(ServerPlayer p) {
        return p.hasPermissions(2) && AuraAnexos.ligada(p) && p.isAlive() && !p.isSpectator();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        // quem esta segurando a tecla: atira todo tick enquanto a aura estiver ligada
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            boolean segurando = SEGURANDO.contains(id);
            if (!segurando) continue;
            if (!podeUsar(p)) {          // morreu, virou espectador, perdeu ADM ou desligou a aura
                SEGURANDO.remove(id);
                continue;
            }
            disparar(p, p.tickCount % 3 == 0);
        }
        // rajadas do comando
        for (Iterator<Map.Entry<UUID, Integer>> it = RAJADAS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null || !podeUsar(p)) { it.remove(); continue; }
            disparar(p, en.getValue() % 3 == 0);
            int resto = en.getValue() - 1;
            if (resto <= 0) it.remove(); else en.setValue(resto);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        ULTIMO.remove(id);
        SEGURANDO.remove(id);
        RAJADAS.remove(id);
    }

    private static void disparar(ServerPlayer p, boolean som) {
        ServerLevel level = p.serverLevel();
        RandomSource rnd = p.getRandom();
        Vec3 olhar = p.getLookAngle();
        Vec3 frente = new Vec3(olhar.x, 0, olhar.z);
        frente = frente.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : frente.normalize();
        Vec3 direita = new Vec3(-frente.z, 0, frente.x);
        Vec3 alvo = p.pick(64.0, 1.0f, false).getLocation();

        for (int i = 0; i < POR_TICK; i++) {
            Vec3 pos = p.position().add(0, 1.0, 0)
                    .add(direita.scale((rnd.nextDouble() - 0.5) * 7.0))
                    .add(0, 0.5 + rnd.nextDouble() * 2.3, 0)
                    .add(frente.scale(-1.0 - rnd.nextDouble() * 1.2));
            Vec3 dir = alvo.subtract(pos);
            dir = dir.lengthSqr() < 1e-4 ? olhar : dir.normalize();

            ArmaDouradaEntity arma = new ArmaDouradaEntity(AuraEntidades.ARMA_DOURADA.get(), level);
            arma.setOwner(p);
            arma.setPos(pos.x, pos.y, pos.z);
            arma.shoot(dir.x, dir.y, dir.z, VELOCIDADE, 1.5f);
            level.addFreshEntity(arma);
            portal(level, pos, dir);
        }
        if (som) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.2f, 1.4f + rnd.nextFloat() * 0.4f);
        }
    }

    /** Anel dourado de particulas perpendicular ao disparo (o "portal"). */
    private static void portal(ServerLevel level, Vec3 pos, Vec3 dir) {
        Vec3 cima = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(cima).normalize();
        Vec3 v = dir.cross(u).normalize();
        DustParticleOptions ouro = new DustParticleOptions(new Vector3f(1f, 0.8f, 0.15f), 1.3f);
        for (int k = 0; k < 14; k++) {
            double a = k * Math.PI * 2 / 14;
            Vec3 o = pos.add(u.scale(Math.cos(a) * 0.5)).add(v.scale(Math.sin(a) * 0.5));
            level.sendParticles(ouro, o.x, o.y, o.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.02);
    }

    private PoderPortoes() {}
}
