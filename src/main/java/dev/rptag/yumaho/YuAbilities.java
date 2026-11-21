package dev.rptag.yumaho;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * As quatro habilidades, 100% no servidor. O cliente só manda "apertei" e desenha os efeitos.
 * Smokescreen / Fa Jin / Blackwhip / Gearshift (nerfado).
 */
@EventBusSubscriber(modid = YuMaho.MODID)
public final class YuAbilities {
    public static final int A_SET_POWER = 0, A_SMOKE = 1, A_FAJIN_START = 2, A_FAJIN_RELEASE = 3,
            A_WHIP = 4, A_GEAR = 5, A_POWER_DELTA = 6,
            // (5.78.5) o que veio no pacote do Drive: soltar o chicote, modo do chicote, agarrar e desacelerar
            A_WHIP_RELEASE = 7, A_WHIP_MODE = 8, A_GRAB = 9, A_GEAR_SLOW = 10,
            /** (5.78.6) Fa Jin de um clique (usado pela roda, para nao precisar segurar tecla). */
            A_FAJIN_FULL = 11;
    private static final int SM = 0, FJ = 1, WH = 2, GR = 3, GS = 4;
    private static final String[] NAMES = {"smoke", "fajin", "whip", "gear", "gear_slow"};

    /** true enquanto uma habilidade causa dano, para o efeito de raio dos golpes não disparar junto. */
    public static boolean busy;

    private static final Map<UUID, long[]> CD = new HashMap<>();
    private static final Map<UUID, Integer> CHARGE = new HashMap<>();
    private static final Map<UUID, Long> GEAR_END = new HashMap<>();
    private static final Map<UUID, Long> NOFALL = new HashMap<>();

    private static final net.minecraft.resources.ResourceLocation G_SPD = YuMaho.rl("gear_speed");
    private static final net.minecraft.resources.ResourceLocation G_ATK = YuMaho.rl("gear_attack_speed");
    private static final net.minecraft.resources.ResourceLocation G_DMG = YuMaho.rl("gear_damage");

    // ------------------------------------------------------------------ entrada
    public static void handle(ServerPlayer sp, int action, int arg) {
        if (sp == null || !sp.isAlive() || sp.isSpectator()) return;
        // (5.78.5) AGARRAR nao exige Yu-Maho: qualquer personagem com aura pega (o servidor decide)
        if (action == A_GRAB) {
            GrabAbility.toggle(sp, arg != 0);
            return;
        }
        if (!YuPower.enabled(sp)) return;
        switch (action) {
            case A_SET_POWER -> YuPower.set(sp, arg);
            case A_POWER_DELTA -> YuPower.set(sp, YuPower.percent(sp) + Mth.clamp(arg, -100, 100));
            case A_SMOKE -> smoke(sp);
            case A_FAJIN_START -> fajinStart(sp);
            case A_FAJIN_RELEASE -> fajinRelease(sp);
            case A_WHIP -> whip(sp);
            case A_WHIP_RELEASE -> releaseWhips(sp);
            case A_WHIP_MODE -> sp.setData(YuAttachments.WHIP_MODE.get(), Mth.clamp(arg, 0, 3));
            case A_GEAR -> gear(sp);
            case A_GEAR_SLOW -> gearSlow(sp);
            case A_FAJIN_FULL -> fajinFull(sp);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ cooldown
    private static boolean ready(ServerPlayer sp, int idx) {
        long left = CD.computeIfAbsent(sp.getUUID(), k -> new long[5])[idx] - sp.level().getGameTime();
        if (left > 0) {
            sp.displayClientMessage(Component.translatable("msg.yumaho.cooldown",
                    Component.translatable("ability.yumaho." + NAMES[idx]), String.format("%.1f", left / 20.0)), true);
            return false;
        }
        return true;
    }

    private static void startCd(ServerPlayer sp, int idx, int ticks) {
        CD.computeIfAbsent(sp.getUUID(), k -> new long[5])[idx] = sp.level().getGameTime() + ticks;
    }

    private static boolean needsPower(ServerPlayer sp) {
        if (YuPower.percent(sp) > 0) return false;
        sp.displayClientMessage(Component.translatable("msg.yumaho.nopower"), true);
        return true;
    }

    private static boolean pvpBlocked(LivingEntity t) { return t instanceof Player && !YuConfig.PVP.get(); }

    // ------------------------------------------------------------------ 1. Smokescreen
    private static void smoke(ServerPlayer sp) {
        if (needsPower(sp) || !ready(sp, SM)) return;
        float f = YuPower.frac(sp);
        ServerLevel lv = sp.serverLevel();
        SmokeCloudEntity c = new SmokeCloudEntity(YuEntities.SMOKE.get(), lv);
        c.setPos(sp.getX(), sp.getY(), sp.getZ());
        c.setup(YuPower.lerp(YuConfig.SMOKE_R_MIN.get(), YuConfig.SMOKE_R_MAX.get(), f),
                Math.round(YuPower.lerp(YuConfig.SMOKE_T_MIN.get(), YuConfig.SMOKE_T_MAX.get(), f) * 20f), sp.getId());
        lv.addFreshEntity(c);
        lv.playSound(null, sp.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.6f, 0.55f);
        startCd(sp, SM, YuConfig.ticks(YuConfig.SMOKE_CD));
    }

    // ------------------------------------------------------------------ 2. Fa Jin
    private static void fajinStart(ServerPlayer sp) {
        if (CHARGE.containsKey(sp.getUUID()) || needsPower(sp) || !ready(sp, FJ)) return;
        CHARGE.put(sp.getUUID(), 0);
        setEnergy(sp, 0);
    }

    private static void cancelCharge(ServerPlayer sp) {
        CHARGE.remove(sp.getUUID());
        setEnergy(sp, 0);
    }

    private static void setEnergy(ServerPlayer sp, int v) {
        v = Mth.clamp(v, 0, 100);
        if (sp.getData(YuAttachments.ENERGY.get()) != v) sp.setData(YuAttachments.ENERGY.get(), v);
    }

    /**
     * (5.78.6) FA JIN DE UM CLIQUE — para a RODA (U). Em modpack ninguem quer decorar teclas:
     * o botao da roda enche a carga e libera na hora, como se tivesse segurado o F.
     */
    private static void fajinFull(ServerPlayer sp) {
        if (CHARGE.containsKey(sp.getUUID())) {          // ja estava carregando: solta o que tem
            fajinRelease(sp);
            return;
        }
        if (needsPower(sp) || !ready(sp, FJ)) return;
        CHARGE.put(sp.getUUID(), Math.max(1, Math.round(YuConfig.FJ_CHARGE.get().floatValue() * 20f)));
        setEnergy(sp, 100);
        fajinRelease(sp);
    }

    private static void fajinRelease(ServerPlayer sp) {
        Integer ch = CHARGE.remove(sp.getUUID());
        if (ch == null) return;
        int energy = sp.getData(YuAttachments.ENERGY.get());
        setEnergy(sp, 0);
        if (energy < 10) return;                         // soltou cedo demais: nada acontece
        float f = YuPower.frac(sp), en = energy / 100f;
        ServerLevel lv = sp.serverLevel();
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        double range = YuConfig.FJ_RANGE.get() * (0.7 + 0.6 * f);
        AABB box = sp.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
        float dmg = (float) (YuConfig.FJ_DMG.get() * en * (0.25 + 0.75 * f));
        float yaw = sp.getYRot() * Mth.DEG_TO_RAD;
        int hits = 0;
        busy = true;
        try {
            for (LivingEntity t : lv.getEntitiesOfClass(LivingEntity.class, box, x -> x != sp && x.isAlive() && !x.isSpectator())) {
                if (pvpBlocked(t)) continue;
                Vec3 d = t.getBoundingBox().getCenter().subtract(eye);
                double dist = d.length();
                if (dist > range + t.getBbWidth() * 0.5) continue;
                if (dist > 1e-3 && d.normalize().dot(look) < 0.80) continue;          // cone ~37°
                if (t.hurt(sp.damageSources().playerAttack(sp), dmg)) {
                    hits++;
                    t.knockback(YuConfig.FJ_KB.get() * (0.4 + 0.6 * en) * (0.5 + 0.5 * f), Mth.sin(yaw), -Mth.cos(yaw));
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.15 + 0.3 * en, 0));
                    t.hurtMarked = true;
                }
            }
        } finally { busy = false; }
        Vec3 end = eye.add(look.scale(Math.min(range, hits > 0 ? range * 0.8 : range)));
        YuFx.send(lv, YuFxPayload.RELEASE, eye.add(look.scale(0.6)).add(0, -0.25, 0), end, f, en);
        lv.playSound(null, sp.blockPosition(), YuSounds.RELEASE, SoundSource.PLAYERS, 0.7f + 0.8f * en, 1.1f - 0.3f * f);
        // custo: cansaço e tropeço breve (maior com mais energia e poder)
        sp.causeFoodExhaustion(1.5f * en + 1.5f * f);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 + (int) (20 * en * f), 1, false, false, false));
        startCd(sp, FJ, YuConfig.ticks(YuConfig.FJ_CD));
    }

    // ------------------------------------------------------------------ 3. Blackwhip (entidade)
    /** (5.78.5) todos os chicotes vivos deste jogador. */
    private static java.util.List<WhipEntity> myWhips(ServerPlayer sp) {
        return sp.serverLevel().getEntitiesOfClass(WhipEntity.class, sp.getBoundingBox().inflate(96),
                w -> w.ownerId() == sp.getId());
    }

    /** Tecla de soltar (Alt esquerdo) ou apertar B de novo: todos os fios voltam. */
    public static void releaseWhips(ServerPlayer sp) {
        java.util.List<WhipEntity> ws = myWhips(sp);
        if (ws.isEmpty()) return;
        for (WhipEntity w : ws) w.release();
        startCd(sp, WH, YuConfig.ticks(YuConfig.WH_CD) / 2);
    }

    /**
     * (5.78.5) BLACKWHIP COMO ENTIDADE (pacote do Drive).
     *
     * <p>Cada fio é uma {@link WhipEntity} que sai da mão, voa, e faz uma coisa conforme o MODO
     * escolhido na engrenagem (U): chicote (dano + empurrão), puxar, prender ou gancho.
     * O número de fios cresce com o poder (1 com 10%, até 4 com 100%); os extras caçam outros
     * alvos na frente e, se não houver, abrem em leque.
     */
    private static void whip(ServerPlayer sp) {
        if (!myWhips(sp).isEmpty()) {
            releaseWhips(sp);
            return;                       // apertar B de novo solta os fios
        }
        if (needsPower(sp) || !ready(sp, WH)) return;
        float f = YuPower.frac(sp);
        ServerLevel lv = sp.serverLevel();
        int mode = sp.getData(YuAttachments.WHIP_MODE.get());
        int max = YuConfig.WH_MAX_COUNT.get();
        int count = Mth.clamp(1 + (int) (f * max * 0.999f), 1, max);
        float range = YuPower.lerp(YuConfig.WH_R_MIN.get(), YuConfig.WH_R_MAX.get(), f);
        float speed = YuPower.lerp(2.2, 3.6, f);
        float dmg = (float) (YuConfig.WH_DMG.get() * (0.3 + 0.7 * f));
        int hold = Math.round(YuPower.lerp(YuConfig.WH_HOLD_MIN.get(), YuConfig.WH_HOLD_MAX.get(), f) * 20f);
        float pull = YuPower.lerp(0.8, YuConfig.WH_PULL.get(), f);
        float grapple = YuPower.lerp(1.0, YuConfig.WH_GRAPPLE.get(), f);

        Vec3 look = sp.getLookAngle();
        java.util.List<Vec3> dirs = new java.util.ArrayList<>();
        dirs.add(look);
        if (count > 1) {
            Vec3 eye = sp.getEyePosition();
            java.util.List<LivingEntity> perto = new java.util.ArrayList<>(lv.getEntitiesOfClass(LivingEntity.class,
                    sp.getBoundingBox().inflate(range),
                    e -> e != sp && e.isAlive() && !e.isSpectator() && !pvpBlocked(e)
                            && e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > 0.5));
            perto.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
            for (int i = 1; i < count; i++) {
                if (i - 1 < perto.size()) {
                    dirs.add(perto.get(i - 1).getBoundingBox().getCenter().subtract(eye).normalize());
                } else {
                    double off = Math.toRadians(9 * ((i + 1) / 2)) * (i % 2 == 0 ? 1 : -1);
                    dirs.add(new Vec3(look.x * Math.cos(off) - look.z * Math.sin(off), look.y,
                            look.x * Math.sin(off) + look.z * Math.cos(off)).normalize());
                }
            }
        }
        Vec3 hand = WhipEntity.hand(sp, 1f);
        for (Vec3 d : dirs) {
            WhipEntity w = new WhipEntity(YuEntities.WHIP.get(), lv);
            w.setPos(hand.x, hand.y, hand.z);
            w.setup(sp, d, mode, f, range, speed, dmg, hold, pull, grapple);
            lv.addFreshEntity(w);
        }
        lv.playSound(null, sp.blockPosition(), YuSounds.WHIP, SoundSource.PLAYERS,
                0.8f + 0.5f * f, 0.9f + lv.random.nextFloat() * 0.2f);
        startCd(sp, WH, YuConfig.ticks(YuConfig.WH_CD));
    }

    /** (5.78.5) M — DESACELERAR o alvo na mira (Lentidão com nível e duração pelo poder). */
    private static void gearSlow(ServerPlayer sp) {
        if (needsPower(sp) || !ready(sp, GS)) return;
        float f = YuPower.frac(sp);
        EntityHitResult eh = rayEntity(sp, YuConfig.GS_RANGE.get() * (0.5 + 0.5 * f));
        if (eh == null || !(eh.getEntity() instanceof LivingEntity t) || pvpBlocked(t)) {
            sp.displayClientMessage(Component.translatable("msg.yumaho.notarget"), true);
            return;
        }
        int amp = Math.min(YuConfig.GS_MAX_AMP.get(), (int) (f * (YuConfig.GS_MAX_AMP.get() + 1)));
        int dur = Math.round(YuPower.lerp(YuConfig.GS_T_MIN.get(), YuConfig.GS_T_MAX.get(), f) * 20f);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, amp, false, true, true));
        ServerLevel lv = sp.serverLevel();
        YuFx.send(lv, YuFxPayload.CRACK, t.position().add(0, t.getBbHeight() * 0.5, 0), Vec3.ZERO, f, 0f);
        lv.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.7f + 0.4f * f, 1.4f - 0.5f * f);
        startCd(sp, GS, YuConfig.ticks(YuConfig.GS_CD));
    }

    /** Alvo na mira (usado pelo M). */
    private static EntityHitResult rayEntity(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition(), end = eye.add(sp.getLookAngle().scale(range));
        AABB box = sp.getBoundingBox().expandTowards(sp.getLookAngle().scale(range)).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(sp, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity, range * range);
    }

    /** (5.78.5) usado pelo chicote/gancho/agarrar: nada de queda por alguns ticks. */
    public static void noFall(ServerPlayer sp, int ticks) {
        NOFALL.put(sp.getUUID(), sp.level().getGameTime() + ticks);
    }

    // ------------------------------------------------------------------ 4. Gearshift (nerfado)
    private static void gear(ServerPlayer sp) {
        if (GEAR_END.containsKey(sp.getUUID()) || needsPower(sp) || !ready(sp, GR)) return;
        if (sp.getFoodData().getFoodLevel() < 7) {
            sp.displayClientMessage(Component.translatable("msg.yumaho.hungry"), true);
            return;
        }
        float f = YuPower.frac(sp);
        float k = 0.25f + 0.75f * f;                          // mesmo com 1% rende um pouco; 100% = máximo configurado
        mod(sp, Attributes.MOVEMENT_SPEED, G_SPD, YuConfig.GS_SPEED.get() * k);
        mod(sp, Attributes.ATTACK_SPEED, G_ATK, YuConfig.GS_ATKSPD.get() * k);
        mod(sp, Attributes.ATTACK_DAMAGE, G_DMG, YuConfig.GS_DMG.get() * k);
        long dur = Math.round(YuPower.lerp(YuConfig.GS_T_MIN.get(), YuConfig.GS_T_MAX.get(), f) * 20f);
        GEAR_END.put(sp.getUUID(), sp.level().getGameTime() + dur);
        YuFx.send(sp.serverLevel(), YuFxPayload.GEAR, sp.position(), sp.position(), f, 0f);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
    }

    private static void mod(ServerPlayer sp, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                            net.minecraft.resources.ResourceLocation id, double amount) {
        AttributeInstance inst = sp.getAttribute(attr);
        if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void unmod(ServerPlayer sp) {
        for (var pair : new Object[][]{{Attributes.MOVEMENT_SPEED, G_SPD}, {Attributes.ATTACK_SPEED, G_ATK}, {Attributes.ATTACK_DAMAGE, G_DMG}}) {
            @SuppressWarnings("unchecked")
            AttributeInstance inst = sp.getAttribute((net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) pair[0]);
            if (inst != null) inst.removeModifier((net.minecraft.resources.ResourceLocation) pair[1]);
        }
    }

    private static void endGear(ServerPlayer sp, boolean exhaust) {
        GEAR_END.remove(sp.getUUID());
        unmod(sp);
        if (!exhaust) return;
        float f = YuPower.frac(sp);
        int t = Math.round(YuConfig.GS_TIRED_T.get().floatValue() * 20f * (0.3f + 0.7f * f));
        if (t > 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, f > 0.6f ? 1 : 0, false, true, true));
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 0, false, true, true));
        }
        startCd(sp, GR, YuConfig.ticks(YuConfig.GS_CD));
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();
        boolean ok = sp.isAlive() && YuPower.enabled(sp);
        ServerLevel lv = sp.serverLevel();
        Integer ch = CHARGE.get(id);
        if (ch != null) {
            if (!ok) cancelCharge(sp);
            else {
                ch++;
                CHARGE.put(id, ch);
                int full = Math.max(1, Math.round(YuConfig.FJ_CHARGE.get().floatValue() * 20f));
                int en = Math.min(100, ch * 100 / full);
                setEnergy(sp, en);
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, false, false, false));
                if (ch % 2 == 0) {
                    Vec3 p = sp.position().add(0, 1.0, 0);
                    lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2 + en / 25, 0.35, 0.5, 0.35, 0.15);
                }
                if (ch > full + 200) {                                          // segurou demais: perde o controle
                    cancelCharge(sp);
                    sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true, true));
                    startCd(sp, FJ, YuConfig.ticks(YuConfig.FJ_CD));
                }
            }
        } else if (sp.getData(YuAttachments.ENERGY.get()) > 0) {
            setEnergy(sp, sp.getData(YuAttachments.ENERGY.get()) - 5);
        }
        Long ge = GEAR_END.get(id);
        if (ge != null) {
            if (!ok || lv.getGameTime() >= ge) endGear(sp, ok);
            else {
                sp.causeFoodExhaustion(0.04f + 0.04f * YuPower.frac(sp));
                if (sp.getFoodData().getFoodLevel() <= 3) endGear(sp, true);   // sem comida o Gearshift acaba
                else if (sp.tickCount % 2 == 0) {
                    lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 0.9, sp.getZ(), 3, 0.3, 0.6, 0.3, 0.05);
                    lv.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.9f, 0.25f), 1.0f), sp.getX(), sp.getY() + 0.1, sp.getZ(), 2, 0.3, 0.05, 0.3, 0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            CHARGE.remove(sp.getUUID());
            NOFALL.remove(sp.getUUID());
            if (GEAR_END.containsKey(sp.getUUID())) endGear(sp, false);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        // modificadores são transitórios (não são copiados); só garante que não sobra estado de mapa
        UUID id = e.getEntity().getUUID();
        CHARGE.remove(id);
        GEAR_END.remove(id);
    }

    private YuAbilities() {}
}
