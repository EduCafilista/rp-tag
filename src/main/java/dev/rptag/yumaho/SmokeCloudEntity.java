package dev.rptag.yumaho;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.Level;

/** Nuvem do Smokescreen. Servidor: tira o alvo dos mobs. Cliente: partículas + névoa (ver YuSmokeClient). */
public class SmokeCloudEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SmokeCloudEntity.class, EntityDataSerializers.INT);

    public SmokeCloudEntity(EntityType<? extends SmokeCloudEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(RADIUS, 5f);
        b.define(OWNER, -1);
        b.define(LIFE, 200);
    }

    public void setup(float radius, int life, int ownerId) {
        entityData.set(RADIUS, radius);
        entityData.set(LIFE, life);
        entityData.set(OWNER, ownerId);
    }

    public float radius() { return entityData.get(RADIUS); }
    public int ownerId() { return entityData.get(OWNER); }
    /** 1 durante a maior parte da vida, cai a 0 nos últimos 2 s. */
    public float fade() { return Mth.clamp((entityData.get(LIFE) - tickCount) / 40f, 0f, 1f); }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > entityData.get(LIFE)) {
            if (!level().isClientSide) discard();
            return;
        }
        if (level().isClientSide) clientParticles();
        else if (tickCount % 10 == 0) {
            float r = radius();
            double r2 = (double) r * r;
            for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r), x -> x.distanceToSqr(this) <= r2)) {
                if (m.getTarget() != null && m.getTarget().distanceToSqr(this) <= r2) m.setTarget(null);
            }
        }
    }

    private void clientParticles() {
        float r = radius(), fade = fade();
        int n = (int) Math.min(12, 2 + r * 1.2f * fade);
        var rnd = random;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            double y = getY() + rnd.nextDouble() * r * 0.55;
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() + Math.cos(a) * d, y, getZ() + Math.sin(a) * d,
                    0, 0.004, 0);
        }
        if (tickCount < 6) {
            for (int i = 0; i < 14; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(),
                        Math.cos(a) * 0.4, 0.05, Math.sin(a) * 0.4);
            }
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean isPickable() { return false; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
}
