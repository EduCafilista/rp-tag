package dev.rptag.king;

import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import dev.rptag.yumaho.YuAbilities;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * O Rei de Amarelo: 6 habilidades + ultimate, escolhidas na roda. Tudo no servidor; o cliente só anima.
 * 0 Decreto · 1 Correntes de Carcosa · 2 Passagem Amarela · 3 A Peça dos Condenados · 4 Olhar de Hastur · 5 Queda de Carcosa · 6 Reino de Carcosa
 */
@EventBusSubscriber(modid = King.MODID)
public final class KingAbilities {
    public static final int DECREE = 0, CHAINS = 1, PASSAGE = 2, ILLUSION = 3, HASTUR = 4, FALL = 5, REALM = 6;

    // ------------------------------------------------------------------ estado (servidor)
    private record Task(long due, ResourceKey<Level> dim, Consumer<ServerLevel> run) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<UUID, long[]> CD = new HashMap<>();

    private static final class ChainHold { long end; UUID owner; ResourceKey<Level> dim; }
    private static final Map<UUID, ChainHold> HOLDS = new HashMap<>();

    private static final class Charge { long fireAt; UUID target; Vec3 last; ResourceKey<Level> dim; }
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    private static final class Zone { ResourceKey<Level> dim; Vec3 pos; double r; long end; UUID owner; }
    private static final List<Zone> ZONES = new ArrayList<>();

    private static final class Realm { ResourceKey<Level> dim; Vec3 center; long start, end, nextWave, nextClone; }
    private static final Map<UUID, Realm> REALMS = new HashMap<>();

    // ------------------------------------------------------------------ utilidades
    public static boolean isKing(Player p) {
        int idx = p.getData(AuraAnexos.AURA.get());
        return idx > 0 && AuraEstilo.byIndex(idx) == AuraEstilo.REI;
    }

    /** Inimigo válido para as habilidades. Outros Reis e quem o PvP protege ficam de fora. */
    public static boolean enemy(ServerPlayer owner, Entity e) {
        if (!(e instanceof LivingEntity le) || e == owner || !le.isAlive() || le.isSpectator() || e instanceof ArmorStand) return false;
        if (e instanceof Player p) return KingConfig.PVP.get() && !isKing(p) && !p.isCreative();
        return true;
    }

    private static List<LivingEntity> enemies(ServerPlayer sp, Vec3 c, double r) {
        double r2 = r * r;
        return sp.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, Math.max(4, r * 0.6), r),
                e -> enemy(sp, e) && e.position().distanceToSqr(c) <= r2);
    }

    public static void mark(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(KingEffects.MARK, ticks, 0, false, false, true));
    }

    private static int markTicks() { return KingConfig.ticks(KingConfig.MARK_SEC); }

    private static void hurt(ServerPlayer sp, LivingEntity t, double amount) {
        if (amount <= 0) return;
        YuAbilities.busy = true;
        try { t.hurt(sp.damageSources().indirectMagic(sp, sp), (float) amount); }
        finally { YuAbilities.busy = false; }
    }

    private static void sound(ServerLevel lv, Vec3 p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        lv.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void schedule(MinecraftServer s, ResourceKey<Level> dim, int delayTicks, Consumer<ServerLevel> run) {
        TASKS.add(new Task(s.getTickCount() + delayTicks, dim, run));
    }

    private static EntityHitResult rayEntity(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle(), end = eye.add(look.scale(range));
        return ProjectileUtil.getEntityHitResult(sp, eye, end, sp.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> enemy(sp, e) && e.isPickable(), range * range);
    }

    // ------------------------------------------------------------------ recarga
    private static final double[] CD_SEC_FALLBACK = {18, 22, 12, 35, 16, 45, 120};

    private static int cooldownTicks(int a) {
        return switch (a) {
            case DECREE -> KingConfig.ticks(KingConfig.DECREE_CD);
            case CHAINS -> KingConfig.ticks(KingConfig.CHAINS_CD);
            case PASSAGE -> KingConfig.ticks(KingConfig.PORTAL_CD);
            case ILLUSION -> KingConfig.ticks(KingConfig.ILLU_CD);
            case HASTUR -> KingConfig.ticks(KingConfig.HASTUR_CD);
            case FALL -> KingConfig.ticks(KingConfig.FALL_CD);
            default -> KingConfig.ticks(KingConfig.ULT_CD);
        };
    }

    private static boolean ready(ServerPlayer sp, int a) {
        long left = CD.computeIfAbsent(sp.getUUID(), k -> new long[7])[a] - sp.level().getGameTime();
        if (left > 0) {
            sp.displayClientMessage(Component.translatable("msg.king.cooldown", Component.translatable("king.ability." + a),
                    String.format("%.1f", left / 20.0)), true);
            return false;
        }
        return true;
    }

    private static void startCd(ServerPlayer sp, int a) {
        long[] cd = CD.computeIfAbsent(sp.getUUID(), k -> new long[7]);
        cd[a] = sp.level().getGameTime() + cooldownTicks(a);
        List<Integer> out = new ArrayList<>(7);
        for (long v : cd) out.add((int) v);
        sp.setData(KingAttachments.COOLDOWNS.get(), out);
    }

    // ------------------------------------------------------------------ entrada
    public static void cast(ServerPlayer sp, int a) {
        if (!sp.isAlive() || sp.isSpectator() || !isKing(sp) || a < 0 || a > 6) return;
        if (!ready(sp, a)) return;
        boolean ok = switch (a) {
            case DECREE -> decree(sp);
            case CHAINS -> chains(sp);
            case PASSAGE -> passage(sp);
            case ILLUSION -> illusions(sp);
            case HASTUR -> hastur(sp);
            case FALL -> fall(sp);
            default -> realm(sp);
        };
        if (ok) startCd(sp, a);
    }

    // ------------------------------------------------------------------ 1. Decreto do Rei
    private static boolean decree(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        Vec3 c = sp.position();
        double R = KingConfig.DECREE_R.get();
        Vec3 look = sp.getLookAngle();
        for (LivingEntity t : enemies(sp, c, R)) {
            Vec3 d = t.position().subtract(c);
            double dist = d.length();
            Vec3 dir = dist < 0.05 ? new Vec3(look.x, 0, look.z).normalize() : new Vec3(d.x, 0, d.z).normalize();
            double k = 1.0 - dist / R * 0.5;
            double push = KingConfig.DECREE_PUSH.get() * k;
            t.setDeltaMovement(dir.x * push, 0.35 + 0.2 * k, dir.z * push);
            t.hurtMarked = true;
            hurt(sp, t, KingConfig.DECREE_DMG.get());
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
        }
        KingFx.send(lv, KingFxPayload.DECREE, c, Vec3.ZERO, (float) R, 0f, sp.getId());
        sound(lv, c, KingSounds.DECREE, 1.4f, 0.9f);
        return true;
    }

    // ------------------------------------------------------------------ 2. Correntes de Carcosa
    private static boolean chains(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        List<LivingEntity> ts = new ArrayList<>(enemies(sp, sp.position(), KingConfig.CHAINS_R.get()));
        if (ts.isEmpty()) { sp.displayClientMessage(Component.translatable("msg.king.notarget"), true); return false; }
        ts.sort(Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        int dur = KingConfig.ticks(KingConfig.CHAINS_SEC);
        int n = Math.min(ts.size(), KingConfig.CHAINS_MAX.get());
        for (int i = 0; i < n; i++) {
            LivingEntity t = ts.get(i);
            ChainHold h = new ChainHold();
            h.end = lv.getServer().getTickCount() + dur; h.owner = sp.getUUID(); h.dim = lv.dimension();
            HOLDS.put(t.getUUID(), h);
            hurt(sp, t, KingConfig.CHAINS_DMG.get());
            mark(t, markTicks());
            KingFx.send(lv, KingFxPayload.CHAINS, t.position(), Vec3.ZERO, dur, 0f, t.getId());
        }
        sound(lv, sp.position(), KingSounds.CHAINS, 1.3f, 1f);
        return true;
    }

    // ------------------------------------------------------------------ 3. Passagem Amarela
    private static Vec3 findSafe(ServerLevel lv, ServerPlayer sp, Vec3 feet) {
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            Vec3 p = feet.add(0, dy, 0);
            AABB box = sp.getBoundingBox().move(p.subtract(sp.position()));
            if (lv.noCollision(sp, box)) return p;
        }
        return null;
    }

    private static boolean passage(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        double max = KingConfig.PORTAL_RANGE.get();
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        Vec3 feet;
        float yaw = sp.getYRot();
        EntityHitResult eh = rayEntity(sp, max);
        if (eh != null && eh.getEntity() instanceof LivingEntity t) {            // reaparece atrás do inimigo
            Vec3 tl = t.getLookAngle();
            Vec3 back = new Vec3(tl.x, 0, tl.z).normalize().scale(-1.6);
            feet = t.position().add(back);
            Vec3 to = t.position().subtract(feet);
            yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        } else {
            BlockHitResult bh = lv.clip(new ClipContext(eye, eye.add(look.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
            Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().subtract(look.scale(0.8)) : eye.add(look.scale(max));
            feet = end.subtract(0, sp.getEyeHeight(), 0);
        }
        Vec3 dest = findSafe(lv, sp, feet);
        if (dest == null || dest.distanceTo(sp.position()) < 1.5) { sp.displayClientMessage(Component.translatable("msg.king.nospace"), true); return false; }
        Vec3 from = sp.position();
        KingFx.send(lv, KingFxPayload.PORTAL, from, Vec3.ZERO, 0f, 0f, sp.getId());
        sound(lv, from, KingSounds.PORTAL, 1.1f, 1f);
        sp.teleportTo(lv, dest.x, dest.y, dest.z, yaw, 0f);
        YuAbilities.noFall(sp, 60);
        KingFx.send(lv, KingFxPayload.PORTAL, dest, Vec3.ZERO, 1f, 0f, sp.getId());
        sound(lv, dest, KingSounds.PORTAL, 1.1f, 1.3f);
        double R = KingConfig.PORTAL_R.get();
        for (LivingEntity t : enemies(sp, dest, R)) {                            // pequena explosão ao chegar
            Vec3 d = t.position().subtract(dest);
            Vec3 dir = new Vec3(d.x, 0, d.z);
            dir = dir.lengthSqr() < 1e-3 ? new Vec3(0, 0, 0) : dir.normalize();
            t.setDeltaMovement(dir.x * 0.9, 0.35, dir.z * 0.9);
            t.hurtMarked = true;
            hurt(sp, t, KingConfig.PORTAL_DMG.get());
        }
        return true;
    }

    // ------------------------------------------------------------------ 4. A Peça dos Condenados
    private static boolean illusions(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        spawnIllusions(sp, KingConfig.ILLU_COUNT.get());
        int inv = KingConfig.ticks(KingConfig.ILLU_INVIS_SEC);
        if (inv > 0) sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, inv, 0, false, false, false));
        for (ServerPlayer p : lv.players()) {                                    // confusão visual e sonora
            if (p != sp && p.distanceToSqr(sp) < 32 * 32 && enemy(sp, p)) {
                KingFx.send(lv, KingFxPayload.CONFUSE, p.position(), Vec3.ZERO, 120f, 0f, p.getId(), 1.0);
                p.playNotifySound(KingSounds.LAUGH, SoundSource.PLAYERS, 0.9f, 1f);
            }
        }
        return true;
    }

    private static void spawnIllusions(ServerPlayer sp, int n) {
        ServerLevel lv = sp.serverLevel();
        for (int i = 0; i < n; i++) {
            IllusionEntity e = new IllusionEntity(KingEntities.ILLUSION.get(), lv);
            float ang = (float) (i * Math.PI * 2 / n + lv.random.nextFloat() * 0.6f), r = 4f + lv.random.nextFloat() * 3f;
            e.setPos(sp.getX() + Mth.cos(ang) * r, sp.getY(), sp.getZ() + Mth.sin(ang) * r);
            e.setup(sp.getId(), KingConfig.ticks(KingConfig.ILLU_SEC), ang, r);
            lv.addFreshEntity(e);
            KingFx.send(lv, KingFxPayload.POOF, e.position().add(0, 0.9, 0), Vec3.ZERO, 0f, 0f, -1);
        }
    }

    // ------------------------------------------------------------------ 5. Olhar de Hastur
    private static boolean hastur(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        EntityHitResult eh = rayEntity(sp, KingConfig.HASTUR_RANGE.get());
        LivingEntity target = eh != null && eh.getEntity() instanceof LivingEntity t ? t : null;
        if (target == null) {                                                    // sem mira exata: o mais central no campo de visão
            Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
            target = lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(KingConfig.HASTUR_RANGE.get()),
                            e -> enemy(sp, e) && e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > 0.93)
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(sp))).orElse(null);
        }
        if (target == null) { sp.displayClientMessage(Component.translatable("msg.king.notarget"), true); return false; }
        int ch = KingConfig.ticks(KingConfig.HASTUR_CHARGE);
        Charge c = new Charge();
        c.fireAt = lv.getServer().getTickCount() + ch; c.target = target.getUUID(); c.last = target.getBoundingBox().getCenter(); c.dim = lv.dimension();
        CHARGES.put(sp.getUUID(), c);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ch + 5, 3, false, false, false));
        KingFx.send(lv, KingFxPayload.CHARGE, sp.position(), Vec3.ZERO, ch, target.getId(), sp.getId());
        sound(lv, sp.position(), KingSounds.HASTUR_CHARGE, 1.2f, 1f);
        return true;
    }

    private static void fireHastur(ServerPlayer sp, Charge c, ServerLevel lv) {
        Vec3 origin = sp.getEyePosition().add(sp.getLookAngle().scale(0.3)).add(0, -0.1, 0);
        Entity te = lv.getEntity(c.target);
        Vec3 aim = te != null && te.isAlive() ? te.getBoundingBox().getCenter() : c.last;
        Vec3 dir = aim.subtract(origin).normalize();
        double range = KingConfig.HASTUR_RANGE.get();
        BlockHitResult bh = lv.clip(new ClipContext(origin, origin.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : origin.add(dir.scale(range));
        Vec3 seg = end.subtract(origin);
        double len2 = seg.lengthSqr();
        for (LivingEntity t : lv.getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(2.0), e -> enemy(sp, e))) {
            Vec3 p = t.getBoundingBox().getCenter().subtract(origin);
            double u = Mth.clamp(p.dot(seg) / len2, 0, 1);
            Vec3 closest = origin.add(seg.scale(u));
            if (closest.distanceTo(t.getBoundingBox().getCenter()) <= 1.0 + t.getBbWidth() * 0.5) {
                mark(t, markTicks());
                hurt(sp, t, KingConfig.HASTUR_DMG.get());
                t.setDeltaMovement(t.getDeltaMovement().add(dir.x * 0.4, 0.15, dir.z * 0.4));
                t.hurtMarked = true;
            }
        }
        KingFx.send(lv, KingFxPayload.BEAM, origin, end, 1f, 0f, sp.getId());
        sound(lv, origin, KingSounds.HASTUR_BEAM, 1.6f, 1f);
    }

    // ------------------------------------------------------------------ 6. Queda de Carcosa
    private static boolean fall(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        BlockHitResult bh = lv.clip(new ClipContext(eye, eye.add(look.scale(36)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        Vec3 center = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation()
                : sp.position().add(new Vec3(look.x, 0, look.z).normalize().scale(12));
        int n = KingConfig.FALL_COUNT.get();
        double area = KingConfig.FALL_RADIUS.get(), R = KingConfig.FALL_R.get();
        MinecraftServer srv = lv.getServer();
        for (int i = 0; i < n; i++) {
            double a = lv.random.nextDouble() * Math.PI * 2, d = Math.sqrt(lv.random.nextDouble()) * area;
            double x = center.x + Math.cos(a) * d, z = center.z + Math.sin(a) * d;
            double y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            Vec3 pos = new Vec3(x, y, z);
            int delay = 40 + i * 2;                                              // 2 s depois, em sequência
            KingFx.send(lv, KingFxPayload.METEOR, pos, Vec3.ZERO, delay, (float) R, -1);
            schedule(srv, lv.dimension(), delay - 14, s -> sound(s, pos, KingSounds.METEOR, 0.5f, 1.4f));
            schedule(srv, lv.dimension(), delay, s -> impact(sp.getUUID(), s, pos, R));
        }
        return true;
    }

    private static void impact(UUID ownerId, ServerLevel lv, Vec3 pos, double R) {
        ServerPlayer owner = lv.getServer().getPlayerList().getPlayer(ownerId);
        sound(lv, pos, KingSounds.METEOR, 1.0f, 0.9f);
        if (owner != null) {
            for (LivingEntity t : enemies(owner, pos, R)) {
                Vec3 d = t.position().subtract(pos);
                Vec3 dir = new Vec3(d.x, 0, d.z);
                dir = dir.lengthSqr() < 1e-3 ? Vec3.ZERO : dir.normalize();
                t.setDeltaMovement(dir.x * 0.8, 0.5, dir.z * 0.8);
                t.hurtMarked = true;
                hurt(owner, t, KingConfig.FALL_DMG.get());
                mark(t, markTicks());
            }
        }
        Zone z = new Zone();
        z.dim = lv.dimension(); z.pos = pos; z.r = 3.0; z.owner = ownerId;
        z.end = lv.getServer().getTickCount() + KingConfig.ticks(KingConfig.FALL_ZONE_SEC);
        ZONES.add(z);
        KingFx.send(lv, KingFxPayload.ZONE, pos, Vec3.ZERO, 3.0f, KingConfig.ticks(KingConfig.FALL_ZONE_SEC), -1);
    }

    // ------------------------------------------------------------------ Ultimate: Reino de Carcosa
    private static boolean realm(ServerPlayer sp) {
        if (REALMS.containsKey(sp.getUUID())) return false;
        ServerLevel lv = sp.serverLevel();
        MinecraftServer srv = lv.getServer();
        Realm r = new Realm();
        r.dim = lv.dimension(); r.center = sp.position();
        r.start = srv.getTickCount();
        int dur = KingConfig.ticks(KingConfig.ULT_SEC);
        r.end = r.start + dur;
        r.nextWave = r.start + KingConfig.ticks(KingConfig.ULT_WAVE_EVERY);
        r.nextClone = r.start + 20;
        REALMS.put(sp.getUUID(), r);
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 1, false, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false, true));
        double R = KingConfig.ULT_RADIUS.get();
        sound(lv, r.center, KingSounds.REALM, 2.0f, 1f);
        for (ServerPlayer p : lv.players()) {
            if (p.position().distanceToSqr(r.center) <= (R + 32) * (R + 32)) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 25));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("msg.king.realm.sub").withStyle(ChatFormatting.GOLD)));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("msg.king.realm").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)));
            }
        }
        return true;
    }

    private static void endRealm(UUID owner, Realm r, ServerLevel lv) {
        REALMS.remove(owner);
        if (lv != null) KingFx.send(lv, KingFxPayload.REALM, r.center, Vec3.ZERO, 0f, 0f, -1, KingConfig.ULT_RADIUS.get() + 64);
    }

    private static void tickRealm(MinecraftServer srv, UUID ownerId, Realm r) {
        ServerLevel lv = srv.getLevel(r.dim);
        ServerPlayer owner = srv.getPlayerList().getPlayer(ownerId);
        long now = srv.getTickCount();
        if (lv == null || owner == null || !owner.isAlive() || now >= r.end) { endRealm(ownerId, r, lv); return; }
        double R = KingConfig.ULT_RADIUS.get();
        if ((now - r.start) % 20 == 0) {
            KingFx.send(lv, KingFxPayload.REALM, r.center, new Vec3(now - r.start, 0, 0), (float) R, (float) (r.end - now), -1, R + 64);
            for (LivingEntity t : enemies(owner, r.center, R)) mark(t, markTicks());      // todos os inimigos recebem a marca
        }
        if (now >= r.nextWave) {
            r.nextWave += KingConfig.ticks(KingConfig.ULT_WAVE_EVERY);
            Vec3 c = owner.position();
            for (LivingEntity t : enemies(owner, r.center, R)) {
                if (!t.hasEffect(KingEffects.MARK)) continue;                             // a onda atinge os marcados
                Vec3 d = t.position().subtract(c);
                Vec3 dir = new Vec3(d.x, 0, d.z);
                dir = dir.lengthSqr() < 1e-3 ? Vec3.ZERO : dir.normalize();
                t.setDeltaMovement(dir.x * 0.9, 0.4, dir.z * 0.9);
                t.hurtMarked = true;
                hurt(owner, t, KingConfig.ULT_WAVE_DMG.get());
            }
            KingFx.send(lv, KingFxPayload.WAVE, c, Vec3.ZERO, (float) R, 0f, owner.getId());
            sound(lv, c, KingSounds.WAVE, 1.4f, 0.8f);
        }
        if (now >= r.nextClone) {
            r.nextClone += KingConfig.ticks(KingConfig.ULT_CLONE_EVERY);
            long alive = lv.getEntitiesOfClass(IllusionEntity.class, owner.getBoundingBox().inflate(48), e -> e.ownerId() == owner.getId()).size();
            if (alive < 4) spawnIllusions(owner, 2);
        }
    }

    // ------------------------------------------------------------------ tick do servidor
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        MinecraftServer srv = e.getServer();
        long now = srv.getTickCount();

        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (now >= t.due) {
                it.remove();
                ServerLevel lv = srv.getLevel(t.dim);
                if (lv != null) t.run.accept(lv);
            }
        }

        for (Iterator<Map.Entry<UUID, ChainHold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {       // correntes prendendo
            var en = it.next();
            ChainHold h = en.getValue();
            ServerLevel lv = srv.getLevel(h.dim);
            Entity t = lv == null ? null : lv.getEntity(en.getKey());
            if (now >= h.end || !(t instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 6, false, false, false));
            le.addEffect(new MobEffectInstance(MobEffects.JUMP, 4, 128, false, false, false));
            Entity owner = lv.getEntity(h.owner);
            if (owner != null && le.distanceToSqr(owner) > 9) {                                              // puxa levemente
                Vec3 d = owner.position().subtract(le.position()).normalize().scale(KingConfig.CHAINS_PULL.get());
                le.setDeltaMovement(d.x, Math.min(0, le.getDeltaMovement().y), d.z);
            } else le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            le.hurtMarked = true;
        }

        for (Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator(); it.hasNext(); ) {         // Hastur carregando
            var en = it.next();
            Charge c = en.getValue();
            ServerLevel lv = srv.getLevel(c.dim);
            ServerPlayer owner = srv.getPlayerList().getPlayer(en.getKey());
            if (lv == null || owner == null || !owner.isAlive()) { it.remove(); continue; }
            Entity te = lv.getEntity(c.target);
            if (te != null && te.isAlive()) c.last = te.getBoundingBox().getCenter();
            if (now >= c.fireAt) { it.remove(); fireHastur(owner, c, lv); }
        }

        if (now % 10 == 0) {                                                                                  // zonas de corrupção
            for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
                Zone z = it.next();
                ServerLevel lv = srv.getLevel(z.dim);
                if (lv == null || now >= z.end) { it.remove(); continue; }
                ServerPlayer owner = srv.getPlayerList().getPlayer(z.owner);
                if (owner == null) continue;
                for (LivingEntity t : enemies(owner, z.pos, z.r)) {
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, false));
                    mark(t, 100);
                }
            }
        }

        for (Iterator<Map.Entry<UUID, Realm>> it = new ArrayList<>(REALMS.entrySet()).iterator(); it.hasNext(); ) {
            var en = it.next();
            tickRealm(srv, en.getKey(), en.getValue());
        }
    }

    // ------------------------------------------------------------------ dano e limpeza
    /** Quem tem a marca de Carcosa sofre mais dano do Rei. */
    @SubscribeEvent
    public static void markBonus(LivingIncomingDamageEvent e) {
        if (e.getEntity().hasEffect(KingEffects.MARK) && e.getSource().getEntity() instanceof Player p && isKing(p))
            e.setAmount((float) (e.getAmount() * (1.0 + KingConfig.MARK_BONUS.get())));
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) cancelAll(sp);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { cancelAll(sp); CD.remove(sp.getUUID()); }
    }

    private static void cancelAll(ServerPlayer sp) {
        CHARGES.remove(sp.getUUID());
        Realm r = REALMS.get(sp.getUUID());
        if (r != null) endRealm(sp.getUUID(), r, sp.serverLevel());
    }

    private KingAbilities() {}
}
