package dev.rptag.aura;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * (5.70.0) PRISMA CELESTIAL — o poder da aura {@code /arcoiris} (zip do pedido 127).
 *
 * <p>Tecla <b>C</b> (mudavel em Controles). So ADM, e so com a aura arco-iris ligada; sem aura ou em
 * recarga nao acontece nada e nao aparece mensagem nenhuma (segredo, como o dono pediu na 5.63.0).
 *
 * <p>O jogador com a aura arcoiris concentra luz por 1 s (círculo mágico arco-íris)
 * e dispara um raio prismático de até 60 blocos por 2 s, que acompanha a mira.
 * Cada pulso (5 por segundo) causa DAMAGE em todos no caminho do raio.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class PrismaCelestial {
    public static final int CHARGE_TICKS = 20;        // concentração
    public static final int FIRE_TICKS = 40;          // raio ativo
    public static final double RANGE = 60.0;
    private static final double BEAM_RADIUS = 1.2;    // largura de acerto do raio
    private static final float DAMAGE = 6.0f;         // por pulso (5 pulsos/s = 30/s no centro do raio)
    private static final int PULSE_EVERY = 4;
    /** false = o raio não machuca jogadores (seguro para RP); mude para true para PvP. */
    private static final boolean HURTS_PLAYERS = false;
    private static final int COOLDOWN_TICKS = 240;    // 12 s

    private static final class Cast {
        final ServerPlayer p; int age;
        Cast(ServerPlayer p) { this.p = p; }
    }

    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void tryCast(ServerPlayer p) {
        if (!p.hasPermissions(2)) {
            return;      // so ADM
        }
        // (5.70.0) silencioso: sem aura, sem aviso (o dono ja vetou mensagem de poder na 5.63.0)
        if (p.getData(AuraAnexos.AURA.get()) != AuraEstilo.ARCOIRIS.index()) {
            return;
        }
        for (Cast c : ACTIVE) if (c.p == p) return;   // já lançando
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;      // em recarga: nada de mensagem
        }
        LAST.put(p.getUUID(), now);
        ACTIVE.add(new Cast(p));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE_TICKS + FIRE_TICKS + 10, 2, false, false));
        PacketDistributor.sendToPlayersNear(p.serverLevel(), null, p.getX(), p.getY(), p.getZ(), 128.0,
                new PrismaFxPayload(p.getId()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        for (Iterator<Cast> it = ACTIVE.iterator(); it.hasNext(); ) {
            Cast c = it.next();
            if (!c.p.isAlive() || c.p.hasDisconnected()) { it.remove(); continue; }
            c.age++;
            step(c);
            if (c.age >= CHARGE_TICKS + FIRE_TICKS) it.remove();
        }
    }

    private static void step(Cast c) {
        ServerPlayer p = c.p;
        ServerLevel level = p.serverLevel();
        if (c.age < CHARGE_TICKS) {
            if (c.age % 4 == 0) {   // notas que sobem enquanto a luz se concentra
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.5f, 0.5f + 1.5f * c.age / CHARGE_TICKS);
            }
            return;
        }
        if (c.age == CHARGE_TICKS) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 3.0f, 1.4f);
        }
        if (c.age % 5 == 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 2.0f, 1.0f + p.getRandom().nextFloat() * 0.4f);
        }
        if (c.age % PULSE_EVERY != 0) return;

        Vec3 start = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        Vec3 end = start.add(dir.scale(RANGE));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();

        AABB box = new AABB(start, end).inflate(BEAM_RADIUS + 1.0);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != p && en.isAlive() && !en.isSpectator())) {
            if (t instanceof Player && !HURTS_PLAYERS) continue;
            if (distToSegment(t.getBoundingBox().getCenter(), start, end) > BEAM_RADIUS + t.getBbWidth() / 2.0) continue;
            t.invulnerableTime = 0;
            t.hurt(level.damageSources().indirectMagic(p, p), DAMAGE);
            t.push(dir.x * 0.4, 0.15, dir.z * 0.4);
            t.hurtMarked = true;
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));   // marcado pela luz
        }
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        if (l2 < 1e-6) return p.distanceTo(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / l2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private PrismaCelestial() {}
}
