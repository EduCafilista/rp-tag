package dev.rptag.aura;

import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Grito do Macaco: onda sonora que se expande 50 blocos em todas as direções (círculo de 100 de diâmetro).
 * Dano: 60 colado nele -> 20 na borda (era 30 -> 5 e o dono achou fraco). Perto (<= STUN_RADIUS): atordoado e imobilizado. Longe: só o zunido.
 * A onda é "de verdade": cada alvo só é atingido quando a frente da onda chega até ele.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class RoarAbility {
    public static final double RADIUS = 50.0;          // 50 blocos para cada lado
    public static final int TRAVEL_TICKS = 60;         // tempo para a onda chegar na borda (3 s)
    // (5.71.0) O DONO: "o dano do grito por algum motivo esta muito fraco".
    // O motivo no codigo: a onda batia UMA vez por alvo e o dano caia linear com a distancia —
    // 30 colado, mas 5 na borda, e qualquer armadura/Resistencia comia boa parte disso. Agora:
    // 60 colado e 20 na borda, e a queda e curva (^0.6), entao ate na metade do raio ainda doi.
    public static final float DAMAGE_MAX = 60f;        // dano colado no Macaco
    public static final float DAMAGE_MIN = 20f;        // dano na borda
    public static final double STUN_RADIUS = 25.0;     // até aqui: lento + imobilizado + tontura
    private static final int COOLDOWN_TICKS = 400;     // 20 s

    private static final class Roar {
        final ServerLevel level; final ServerPlayer caster; final Vec3 origin; int age;
        final Set<Integer> hit = new HashSet<>();
        Roar(ServerPlayer c) { caster = c; level = c.serverLevel(); origin = c.position(); }
    }

    private static final List<Roar> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, int[]> RINGING = new HashMap<>(); // {restante, total}

    /** Raio da frente da onda após t ticks (desacelera ao chegar na borda). Usado também no cliente. */
    public static double radiusAt(float t) {
        float u = Mth.clamp(t / TRAVEL_TICKS, 0f, 1f);
        return RADIUS * (1f - (1f - u) * (1f - u));
    }

    public static void tryRoar(ServerPlayer p) {
        if (!p.getData(AuraAnexos.MACACO.get())) {
            p.displayClientMessage(Component.literal("Só o Macaco pode rugir."), true);
            return;
        }
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            long s = (COOLDOWN_TICKS - (now - last)) / 20 + 1;
            p.displayClientMessage(Component.literal("Garganta cansada... " + s + "s"), true);
            return;
        }
        LAST.put(p.getUUID(), now);

        Roar r = new Roar(p);
        ACTIVE.add(r);
        Vec3 o = r.origin;
        ServerLevel lv = r.level;
        lv.playSound(null, o.x, o.y, o.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 10f, 0.5f);
        lv.playSound(null, o.x, o.y, o.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 10f, 0.6f);
        lv.sendParticles(ParticleTypes.SONIC_BOOM, o.x, o.y + 4.5, o.z, 1, 0, 0, 0, 0);
        PacketDistributor.sendToPlayersNear(lv, null, o.x, o.y, o.z, 160.0, new RoarFxPayload(o.x, o.y, o.z));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        for (Iterator<Roar> it = ACTIVE.iterator(); it.hasNext(); ) {
            Roar r = it.next();
            step(r);
            if (r.age >= TRAVEL_TICKS) it.remove();
        }
        MinecraftServer srv = e.getServer();
        for (Iterator<Map.Entry<UUID, int[]>> it = RINGING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, int[]> en = it.next();
            int[] v = en.getValue();
            ServerPlayer sp = srv.getPlayerList().getPlayer(en.getKey());
            if (sp == null || --v[0] <= 0) { it.remove(); continue; }
            if (v[0] % 4 == 0) { // zunido agudo que vai sumindo, só para quem foi atingido
                sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                        0.7f * v[0] / v[1], 2.0f);
            }
        }
    }

    private static void step(Roar r) {
        r.age++;
        double radius = radiusAt(r.age);
        Vec3 o = r.origin;
        AABB box = new AABB(o.x - radius, o.y - 40, o.z - radius, o.x + radius, o.y + 40, o.z + radius);
        for (LivingEntity t : r.level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != r.caster && en.isAlive() && !en.isSpectator() && !r.hit.contains(en.getId()))) {
            double dx = t.getX() - o.x, dz = t.getZ() - o.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius) continue;           // a frente da onda ainda não chegou
            r.hit.add(t.getId());
            affect(r, t, d, dx, dz);
        }
        // ecos do grito
        if (r.age == 15 || r.age == 30 || r.age == 45) {
            float f = 1f - r.age / 60f;
            r.level.playSound(null, o.x, o.y, o.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS,
                    8f * f + 1f, 0.7f + r.age / 90f);
        }
    }

    private static void affect(Roar r, LivingEntity t, double d, double dx, double dz) {
        double k = (float) Math.pow(Mth.clamp(d / RADIUS, 0.0, 1.0), 0.6);
        float dmg = (float) Mth.lerp(k, DAMAGE_MAX, DAMAGE_MIN);   // 60 perto -> 20 longe
        t.invulnerableTime = 0;
        t.hurt(r.level.damageSources().sonicBoom(r.caster), dmg);

        double len = Math.max(0.001, d), s = Mth.lerp(k, 1.6, 0.3);
        t.push(dx / len * s, 0.35 * s, dz / len * s);
        t.hurtMarked = true;

        boolean near = d <= STUN_RADIUS;
        if (near) {
            int dur = (int) Mth.lerp(d / STUN_RADIUS, 160.0, 60.0);   // 8 s colado -> 3 s
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 7, false, true));  // imobilizado
            t.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 128, false, false));            // sem pulo
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dur, 0, false, false));         // tontura
        }
        if (t instanceof ServerPlayer sp) {
            int ticks = near ? 140 : 80;
            RINGING.put(sp.getUUID(), new int[]{ticks, ticks});
        }
    }

    private RoarAbility() {}
}
