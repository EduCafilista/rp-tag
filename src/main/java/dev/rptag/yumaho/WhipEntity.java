package dev.rptag.yumaho;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Um chicote do Blackwhip. A entidade É a ponta: sai da mão, voa até acertar algo, e volta.
 * Estados: EXTEND (estende) -> HOLD (agarra/prende um alvo) | ANCHOR (prende no bloco e puxa o dono) -> RETRACT (retrai).
 * Tudo no servidor. O cliente só desenha (WhipRenderer) a partir do estado sincronizado.
 */
public class WhipEntity extends Entity {
    public static final int EXTEND = 0, HOLD = 1, ANCHOR = 2, RETRACT = 3;
    public static final int M_LASH = 0, M_PULL = 1, M_BIND = 2, M_GRAPPLE = 3;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(WhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(WhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(WhipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(WhipEntity.class, EntityDataSerializers.FLOAT);

    // só servidor
    private Vec3 dir = Vec3.ZERO;
    private Vec3 anchor = Vec3.ZERO;
    private float speed = 3f, range = 16f, dmg = 4f, pull = 1.5f, grapple = 1.5f;
    private int mode, hold;
    private double travelled;
    /** ticks desde a última troca de estado (os dois lados contam; o cliente zera ao receber o novo estado). */
    private int stateAge;

    public WhipEntity(EntityType<? extends WhipEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STATE, EXTEND);
        b.define(OWNER, -1);
        b.define(TARGET, -1);
        b.define(POWER, 0.5f);
    }

    public void setup(Player owner, Vec3 dir, int mode, float power, float range, float speed, float dmg, int holdTicks, float pull, float grapple) {
        entityData.set(OWNER, owner.getId());
        entityData.set(POWER, power);
        this.dir = dir.normalize();
        this.mode = mode;
        this.range = range;
        this.speed = speed;
        this.dmg = dmg;
        this.hold = holdTicks;
        this.pull = pull;
        this.grapple = grapple;
    }

    public int state() { return entityData.get(STATE); }
    public int ownerId() { return entityData.get(OWNER); }
    public int targetId() { return entityData.get(TARGET); }
    public float power() { return entityData.get(POWER); }
    public int age() { return stateAge; }

    private void setState(int s) { entityData.set(STATE, s); stateAge = 0; }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> acc) {
        if (STATE.equals(acc)) stateAge = 0;
        super.onSyncedDataUpdated(acc);
    }

    /** Posição da mão de onde o chicote sai (mesma conta no servidor e no cliente). */
    public static Vec3 hand(Entity o, float pt) {
        Vec3 eye = o.getEyePosition(pt), look = o.getViewVector(pt);
        float yaw = o.getViewYRot(pt) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return eye.add(look.scale(0.45)).add(right.scale(0.30)).add(0, -0.35, 0);
    }

    // ------------------------------------------------------------------ lógica (servidor)
    @Override
    public void tick() {
        super.tick();
        stateAge++;
        if (level().isClientSide) return;
        if (!(level().getEntity(ownerId()) instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != level()) {
            discard();
            return;
        }
        switch (state()) {
            case EXTEND -> extend(owner);
            case HOLD -> hold(owner);
            case ANCHOR -> anchor(owner);
            default -> retract(owner);
        }
    }

    /** Pede ao chicote para voltar (tecla de soltar). */
    public void release() {
        if (!level().isClientSide && state() != RETRACT) toRetract();
    }

    private void toRetract() {
        entityData.set(TARGET, -1);
        setState(RETRACT);
    }

    private boolean canHit(Entity e, Entity owner) {
        return e instanceof LivingEntity && e != owner && e.isAlive() && !e.isSpectator()
                && !(e instanceof Player && !YuConfig.PVP.get());
    }

    private void extend(ServerPlayer owner) {
        Vec3 from = position(), to = from.add(dir.scale(speed));
        BlockHitResult bh = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : to;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, from, end, new AABB(from, end).inflate(0.6), e -> canHit(e, owner));
        if (eh != null && eh.getEntity() instanceof LivingEntity t) {
            setPos(eh.getLocation());
            hitEntity(owner, t);
            return;
        }
        setPos(end);
        travelled += from.distanceTo(end);
        if (bh.getType() == HitResult.Type.BLOCK) {          // bloco: prende e puxa o dono
            anchor = end;
            crack();
            setState(ANCHOR);
        } else if (travelled >= range) toRetract();
    }

    private void crack() {
        YuFx.send((net.minecraft.server.level.ServerLevel) level(), YuFxPayload.CRACK, position(), Vec3.ZERO, power(), 0f);
        level().playSound(null, getX(), getY(), getZ(), YuSounds.WHIP, SoundSource.PLAYERS, 0.6f + 0.5f * power(), 0.9f + random.nextFloat() * 0.25f);
    }

    private void hurt(ServerPlayer owner, LivingEntity t, float amount) {
        if (amount <= 0) return;
        YuAbilities.busy = true;
        try { t.hurt(owner.damageSources().playerAttack(owner), amount); }
        finally { YuAbilities.busy = false; }
    }

    private void hitEntity(ServerPlayer owner, LivingEntity t) {
        entityData.set(TARGET, t.getId());
        crack();
        boolean heavy = t.getMaxHealth() > YuConfig.WH_MAX_PULL_HP.get();
        int m = (heavy && (mode == M_PULL || mode == M_BIND)) ? M_LASH : mode;
        switch (m) {
            case M_LASH -> {
                hurt(owner, t, dmg);
                float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
                t.knockback(0.5 + power() * 0.8, Mth.sin(yaw), -Mth.cos(yaw));
                toRetract();
            }
            case M_PULL -> { hurt(owner, t, dmg * 0.5f); setState(HOLD); }
            case M_BIND -> { hold = hold * 3 / 2; setState(HOLD); }
            default -> { hurt(owner, t, dmg * 0.3f); setState(ANCHOR); }     // gancho em entidade
        }
    }

    private void hold(ServerPlayer owner) {
        if (!(level().getEntity(targetId()) instanceof LivingEntity t) || !t.isAlive() || t.level() != level()) { toRetract(); return; }
        if (--hold <= 0 || owner.distanceTo(t) > range * 1.6 + 4) { toRetract(); return; }
        setPos(t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ());
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 6, false, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.JUMP, 4, 128, false, false, false));
        if (mode == M_PULL) {
            Vec3 look = owner.getLookAngle();
            Vec3 want = owner.position().add(new Vec3(look.x, 0, look.z).normalize().scale(2.2));
            Vec3 d = want.subtract(t.position());
            double len = d.length();
            t.setDeltaMovement(len > 0.8 ? d.normalize().scale(Math.min(len * 0.3, pull)).add(0, 0.04, 0) : Vec3.ZERO);
        } else {
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
        }
        t.hurtMarked = true;
        t.fallDistance = 0;
    }

    private void anchor(ServerPlayer owner) {
        if (targetId() >= 0 && level().getEntity(targetId()) instanceof LivingEntity t && t.isAlive()) {
            anchor = t.getBoundingBox().getCenter();
            setPos(anchor);
        }
        Vec3 to = anchor.subtract(owner.position().add(0, owner.getBbHeight() * 0.5, 0));
        double d = to.length();
        if (d < 2.0 || stateAge > 40 || (stateAge > 6 && owner.isShiftKeyDown())) { toRetract(); return; }
        owner.setDeltaMovement(to.normalize().scale(Math.min(d * 0.25, grapple)).add(0, 0.06, 0));
        owner.hurtMarked = true;
        owner.fallDistance = 0;
        YuAbilities.noFall(owner, 40);
    }

    private void retract(ServerPlayer owner) {
        Vec3 d = WhipEntity.hand(owner, 1f).subtract(position());
        double len = d.length();
        if (len < 1.6) { discard(); return; }
        setPos(position().add(d.scale(Math.min(len, 3.4) / len)));
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean isPickable() { return false; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
}
