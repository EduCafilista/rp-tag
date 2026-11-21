package dev.rptag.yumaho;

import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * (5.78.5) Agarrar: todo personagem (aura ligada) pega o alvo PELO PESCOÇO, exceto Gravidade e Radiação (não têm a habilidade).
 * O Macaco (forma gigante) segura o alvo PELO CORPO.
 * Tecla K: agarra. Com alguém na mão: K solta, Shift+K arremessa (corpo) / joga no chão (pescoço).
 * Tudo no servidor. Dano contínuo nunca baixa a vida do alvo do piso configurado (RP seguro).
 */
@EventBusSubscriber(modid = YuMaho.MODID)
public final class GrabAbility {
    private static final class Hold {
        final UUID target; final boolean body; int age;
        Hold(UUID t, boolean b) { target = t; body = b; }
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();      // dono -> alvo
    private static final Set<UUID> HELD = new HashSet<>();             // alvos presos
    private static final Map<UUID, Long> CD = new HashMap<>();

    /** null = pode pegar pelo pescoço; ver também isBody. */
    private static boolean isMacaco(ServerPlayer sp) { return sp.getData(AuraAnexos.MACACO.get()); }

    private static boolean allowed(ServerPlayer sp) {
        if (isMacaco(sp)) return true;
        int idx = sp.getData(AuraAnexos.AURA.get());
        if (idx > 0) {
            AuraEstilo s = AuraEstilo.byIndex(idx);
            return s != AuraEstilo.GRAVIDADE && s != AuraEstilo.RADIACAO;
        }
        return false;
    }

    private static boolean blockedStyle(ServerPlayer sp) {
        int idx = sp.getData(AuraAnexos.AURA.get());
        if (idx <= 0) return false;
        AuraEstilo s = AuraEstilo.byIndex(idx);
        return s == AuraEstilo.GRAVIDADE || s == AuraEstilo.RADIACAO;
    }

    private static double scale(ServerPlayer sp) { return Math.max(1.0, sp.getAttributeValue(Attributes.SCALE)); }

    // ------------------------------------------------------------------ tecla
    public static void toggle(ServerPlayer sp, boolean shift) {
        Hold h = HOLDS.get(sp.getUUID());
        if (h != null) {
            if (shift) throwTarget(sp, h); else release(sp, true);
            return;
        }
        if (!(allowed(sp) || (YuPower.enabled(sp) && !blockedStyle(sp)))) {
            sp.displayClientMessage(Component.translatable("msg.yumaho.grab.notallowed"), true);
            return;
        }
        long left = CD.getOrDefault(sp.getUUID(), 0L) - sp.level().getGameTime();
        if (left > 0) {
            sp.displayClientMessage(Component.translatable("msg.yumaho.cooldown", Component.translatable("ability.yumaho.grab"),
                    String.format("%.1f", left / 20.0)), true);
            return;
        }
        boolean body = isMacaco(sp);
        double reach = YuConfig.GRAB_REACH.get() * scale(sp);
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle(), end = eye.add(look.scale(reach));
        AABB box = sp.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(sp, eye, end, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isAlive(), reach * reach);
        if (eh == null || !(eh.getEntity() instanceof LivingEntity t) || !valid(sp, t)) {
            sp.displayClientMessage(Component.translatable("msg.yumaho.notarget"), true);
            return;
        }
        HOLDS.put(sp.getUUID(), new Hold(t.getUUID(), body));
        HELD.add(t.getUUID());
        sp.serverLevel().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, 0.7f);
        sp.displayClientMessage(Component.translatable(body ? "msg.yumaho.grab.body" : "msg.yumaho.grab.neck"), true);
    }

    private static boolean valid(ServerPlayer sp, LivingEntity t) {
        if (t == sp || HELD.contains(t.getUUID()) || HOLDS.containsKey(t.getUUID())) return false;
        if (t instanceof Player) { if (!YuConfig.GRAB_PLAYERS.get()) return false; }
        else if (!YuConfig.GRAB_MOBS.get()) return false;
        if (t.getType() == EntityType.ENDER_DRAGON || t.getType() == EntityType.WITHER) return false;
        if (t.getMaxHealth() > YuConfig.GRAB_MAX_HP.get()) return false;
        if (t instanceof ServerPlayer tp && tp.getData(AuraAnexos.MACACO.get())) return false;       // gigante não é pego
        return true;
    }

    // ------------------------------------------------------------------ soltar / arremessar
    private static LivingEntity target(ServerPlayer sp, Hold h) {
        return sp.serverLevel().getEntity(h.target) instanceof LivingEntity t ? t : null;
    }

    public static void release(ServerPlayer sp, boolean cooldown) {
        Hold h = HOLDS.remove(sp.getUUID());
        if (h == null) return;
        HELD.remove(h.target);
        if (cooldown) CD.put(sp.getUUID(), sp.level().getGameTime() + YuConfig.ticks(YuConfig.GRAB_CD));
    }

    private static void hurt(ServerPlayer sp, LivingEntity t, float amount, boolean respectFloor) {
        if (amount <= 0) return;
        if (respectFloor && t.getHealth() - amount < YuConfig.GRAB_MIN_HEALTH.get()) return;
        YuAbilities.busy = true;
        try { t.hurt(sp.damageSources().playerAttack(sp), amount); }
        finally { YuAbilities.busy = false; }
    }

    private static void throwTarget(ServerPlayer sp, Hold h) {
        LivingEntity t = target(sp, h);
        release(sp, true);
        if (t == null) return;
        Vec3 look = sp.getLookAngle();
        if (h.body) {                                           // Macaco arremessa longe
            t.setDeltaMovement(look.scale(2.0).add(0, 0.55, 0));
            hurt(sp, t, YuConfig.GRAB_THROW_DMG.get().floatValue(), false);
        } else {                                                // pescoço: joga no chão
            t.setDeltaMovement(look.x * 0.5, -1.3, look.z * 0.5);
            hurt(sp, t, YuConfig.GRAB_SLAM_DMG.get().floatValue(), false);
        }
        t.hurtMarked = true;
        t.fallDistance = 0;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, true, true));
        sp.serverLevel().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    // ------------------------------------------------------------------ segurando
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Hold h = HOLDS.get(sp.getUUID());
        if (h == null) return;
        LivingEntity t = target(sp, h);
        double maxSec = h.body ? YuConfig.GRAB_BODY_SEC.get() : YuConfig.GRAB_NECK_SEC.get();
        double sc = scale(sp);
        if (!sp.isAlive() || t == null || !t.isAlive() || t.isRemoved() || t.level() != sp.level()
                || ++h.age > maxSec * 20 || sp.distanceTo(t) > 8 * sc || sp.isSleeping()) {
            release(sp, true);
            return;
        }
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 want;
        if (h.body) {      // corpo inteiro na frente do peito, centro do alvo no ponto
            Vec3 p = sp.position().add(0, sp.getEyeHeight() * 0.62, 0).add(flat.scale(1.4 * sc));
            want = p.subtract(0, t.getBbHeight() * 0.5, 0);
        } else {           // pescoço na altura da mão, pés balançando no ar
            Vec3 p = sp.getEyePosition().add(flat.scale(0.95)).add(0, -0.15, 0);
            want = p.subtract(0, t.getEyeHeight() * 0.92, 0);
        }
        Vec3 d = want.subtract(t.position());
        if (t instanceof Player) {
            t.setDeltaMovement(d.scale(0.55).add(0, 0.0, 0));
            t.hurtMarked = true;
        } else {
            t.setPos(want.x, want.y, want.z);
            t.setDeltaMovement(Vec3.ZERO);
            if (t instanceof Mob m) { m.getNavigation().stop(); m.setTarget(null); }
        }
        t.fallDistance = 0;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 6, false, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.JUMP, 4, 128, false, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 4, 4, false, false, false));
        if (!h.body) t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));   // sufocando

        ServerLevel lv = sp.serverLevel();
        if (h.age % 20 == 0) hurt(sp, t, h.body ? YuConfig.GRAB_BODY_DMG.get().floatValue() : YuConfig.GRAB_NECK_DMG.get().floatValue(), true);
        if (h.age % 4 == 0) {
            Vec3 g = h.body ? want.add(0, t.getBbHeight() * 0.5, 0) : sp.getEyePosition().add(flat.scale(0.95)).add(0, -0.15, 0);
            lv.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.92f, 0.45f), 0.8f), g.x, g.y, g.z, 3, 0.12, 0.12, 0.12, 0);
        }
    }

    // ------------------------------------------------------------------ limpeza
    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) release(sp, false);
        // se o alvo morreu, o tick do dono solta
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { release(sp, false); CD.remove(sp.getUUID()); }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) release(sp, false);
    }

    private GrabAbility() {}
}
