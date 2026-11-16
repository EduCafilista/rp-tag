package dev.rptag.aura;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * (5.60.0) O PODER DA AURA DE ILUMINACAO: os PORTES DA BABILONIA.
 *
 * <p>Uma rajada de armas douradas que sai de portais atras/em volta do corpo e voa para onde o
 * jogador esta mirando. Dura 1,5 s, dispara 2 armas por tick e tem 5 s de recarga.
 *
 * <p>Como usar: tecla <b>G</b> (mudavel em Controles) ou {@code /iluminacao portoes}.
 * So dispara com a aura LIGADA — desligada, avisa e nao gasta recarga.
 *
 * <p>Seguro pra RP: as armas NAO machucam jogadores ({@link ArmaDouradaEntity#FERE_JOGADOR}).
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class PoderPortoes {
    private static final int RECARGA_TICKS = 100;  // 5 s
    private static final int DURACAO_TICKS = 30;   // 1,5 s de rajada
    private static final int POR_TICK = 2;
    private static final float VELOCIDADE = 3.2f;

    private static final Map<UUID, Long> ULTIMO = new HashMap<>();
    private static final List<Rajada> ATIVAS = new ArrayList<>();

    private static final class Rajada {
        final ServerPlayer jogador;
        int restante = DURACAO_TICKS;

        Rajada(ServerPlayer p) {
            this.jogador = p;
        }
    }

    /** Chamado pela tecla (pacote) e pelo comando. */
    public static void tentarDisparar(ServerPlayer p) {
        if (!AuraAnexos.ligada(p)) {
            p.displayClientMessage(Component.literal("\u00a7eLigue a aura primeiro: \u00a7f/iluminacao"), true);
            return;
        }
        long agora = p.serverLevel().getGameTime();
        Long ultimo = ULTIMO.get(p.getUUID());
        if (ultimo != null && agora - ultimo < RECARGA_TICKS) {
            long falta = (RECARGA_TICKS - (agora - ultimo)) / 20L + 1L;
            p.displayClientMessage(Component.literal("\u00a77Os portoes ainda estao se fechando... \u00a78("
                    + falta + "s)"), true);
            return;
        }
        ULTIMO.put(p.getUUID(), agora);
        ATIVAS.add(new Rajada(p));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        for (Iterator<Rajada> it = ATIVAS.iterator(); it.hasNext(); ) {
            Rajada r = it.next();
            if (!r.jogador.isAlive() || r.jogador.hasDisconnected()) {
                it.remove();
                continue;
            }
            disparar(r.jogador, r.restante % 3 == 0);
            if (--r.restante <= 0) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ULTIMO.remove(e.getEntity().getUUID());
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
