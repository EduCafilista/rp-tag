package dev.rptag.aura;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * (5.60.0) ESPADA/LANCA DOURADA que sai dos Portoes da Babilonia.
 * Id par = espada, impar = lanca (quem escolhe e o desenho, ver ArmaDouradaRenderer).
 */
public class ArmaDouradaEntity extends Projectile {
    /** Dano por arma. */
    public static final float DANO = 8.0f;
    /** false = NAO machuca jogador (seguro pra RP; os bichos levam dano). */
    public static final boolean FERE_JOGADOR = false;

    private static final int VIDA_MAX = 40;
    private int vida;

    public ArmaDouradaEntity(EntityType<? extends ArmaDouradaEntity> tipo, Level level) {
        super(tipo, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.vida = tag.getInt("vida");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("vida", this.vida);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return !(e instanceof ArmaDouradaEntity) && super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 mv = getDeltaMovement();

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
        }
        if (isRemoved()) {
            return;
        }

        setPos(getX() + mv.x, getY() + mv.y, getZ() + mv.z);
        double h = mv.horizontalDistance();
        setYRot((float) (Mth.atan2(mv.x, mv.z) * (180.0 / Math.PI)));
        setXRot((float) (Mth.atan2(mv.y, h) * (180.0 / Math.PI)));

        if (level().isClientSide) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(new DustParticleOptions(new Vector3f(1f, 0.8f, 0.15f), 1.2f),
                    getX() - mv.x * 0.5, getY() - mv.y * 0.5, getZ() - mv.z * 0.5, 0, 0, 0);
        } else if (++vida > VIDA_MAX) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (level().isClientSide) {
            return;
        }
        Entity alvo = r.getEntity();
        if (FERE_JOGADOR || !(alvo instanceof Player)) {
            alvo.hurt(level().damageSources().thrown(this, getOwner()), DANO);
        }
        estouro();
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide) {
            return;
        }
        estouro();
        discard();
    }

    private void estouro() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 12, 0.25, 0.25, 0.25, 0.15);
            sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.8f, 0.15f), 1.6f),
                    getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        }
    }
}
