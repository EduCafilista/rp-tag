package dev.rptag;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * O BICHO DA CAIXA — 5.7.7.
 *
 * <h2>Por que existe</h2>
 * O dono: <i>"melhore esse bicho que aparece, quero que os players possam bater
 * nele caso queiram"</i>. Antes o bicho era SÓ um desenho na tela (o susto do
 * {@code NeptuneFx}) e sumia junto com a caixa — não dava pra revidar. Agora,
 * quando a caixa arrebenta, o bicho <b>sai pra fora de verdade</b>: é uma
 * criatura no mundo, com vida, que persegue quem abriu a caixa e pode ser
 * esfaqueada até morrer (e ela solta fumaça e um grito ao morrer).
 *
 * <h2>Como ele se comporta</h2>
 * <ul>
 *   <li>nasce com <b>40 de vida</b> (duas espadas de pedra dão conta);</li>
 *   <li>bate fraco (4) e devagar — é susto, não boss;</li>
 *   <li>persegue quem abriu a caixa e qualquer um que bata nele;</li>
 *   <li>se ninguém mexer, ele fica rondando e depois desaparece sozinho
 *       (despawn normal de monstro) — não fica entupindo o mapa.</li>
 * </ul>
 *
 * <p>O desenho é o mesmo da tela (a carinha da textura {@code bicho.png}), num
 * quad que sempre encara a câmera — ver {@code client/BichoRenderer}.
 */
public class BichoEntity extends Monster {

    public BichoEntity(EntityType<? extends BichoEntity> tipo, Level nivel) {
        super(tipo, nivel);
        this.xpReward = 12;
    }

    /** (5.7.7) atributos: vida 40, dano 4, velocidade de corredor lento. */
    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));   // bateu? ele vai
    }

    @Override
    public boolean removeWhenFarAway(double distancia) {
        return true;                    // despawn normal: não entope o mapa
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return RPTagMod.SOM_BICHO_GRITO.get();
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource fonte) {
        return RPTagMod.SOM_BICHO_GRITO.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RPTagMod.SOM_BICHO_GRITO.get();
    }

    @Override
    public void tick() {
        super.tick();
        // fumacinha escura no corpo (é bicho do vazio, não um panda)
        if (this.level().isClientSide && this.tickCount % 3 == 0 && this.random.nextFloat() < 0.6F) {
            this.level().addParticle(ParticleTypes.SMOKE,
                    this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                    this.getY() + this.random.nextDouble() * 1.4,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.5,
                    0.0, 0.01, 0.0);
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity alvo) {
        boolean bateu = super.doHurtTarget(alvo);
        if (bateu && alvo instanceof LivingEntity vivo) {
            this.level().playSound(null, this.blockPosition(), RPTagMod.SOM_BICHO_GRITO.get(),
                    SoundSource.HOSTILE, 0.7F, 1.15F);
            vivo.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    RPEffects.MEDO, 80, 0, false, false, true));
        }
        return bateu;
    }

    /** (5.7.7) registro dos atributos (mod bus). */
    @EventBusSubscriber(modid = RPTagMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registro {
        private Registro() {
        }

        @SubscribeEvent
        public static void onAtributos(EntityAttributeCreationEvent e) {
            e.put(RPTagMod.BICHO.get(), atributos().build());
        }
    }
}
