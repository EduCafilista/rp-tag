package dev.rptag.king;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cópia ilusória do Rei. Não é um ser vivo: anda em volta do dono, não causa dano e some ao ser atingida.
 * A pele é a do dono (o cliente procura o jogador pelo id sincronizado).
 */
public class IllusionEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(IllusionEntity.class, EntityDataSerializers.INT);
    private int life = 300;
    private float orbit, orbitRadius = 5f, orbitDir = 1f;

    public IllusionEntity(EntityType<? extends IllusionEntity> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(OWNER, -1); }

    public void setup(int ownerId, int lifeTicks, float angle, float radius) {
        entityData.set(OWNER, ownerId);
        life = lifeTicks;
        orbit = angle;
        orbitRadius = radius;
        orbitDir = level().random.nextBoolean() ? 1f : -1f;
    }

    public int ownerId() { return entityData.get(OWNER); }

    /** Some com um estalo. */
    public void poof() {
        if (level() instanceof net.minecraft.server.level.ServerLevel lv) {
            KingFx.send(lv, KingFxPayload.POOF, position().add(0, 0.9, 0), Vec3.ZERO, 0f, 0f, -1);
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Entity owner = level().getEntity(ownerId());
        if (owner == null || !owner.isAlive() || tickCount > life) { poof(); return; }
        orbit += 0.03f * orbitDir;
        Vec3 want = owner.position().add(Mth.cos(orbit) * orbitRadius, 0, Mth.sin(orbit) * orbitRadius);
        Vec3 d = want.subtract(position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        double len = flat.length();
        Vec3 v = len > 0.05 ? flat.scale(Math.min(0.22, len * 0.25) / len) : Vec3.ZERO;
        double vy = onGround() ? 0 : getDeltaMovement().y - 0.08;
        setDeltaMovement(v.x, vy, v.z);
        move(MoverType.SELF, getDeltaMovement());
        if (len > 0.2) {
            float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.rotLerp(0.3f, getYRot(), yaw));
        }
        if (position().distanceToSqr(owner.position()) > 30 * 30) poof();
    }

    @Override public boolean hurt(DamageSource source, float amount) { poof(); return true; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return true; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
