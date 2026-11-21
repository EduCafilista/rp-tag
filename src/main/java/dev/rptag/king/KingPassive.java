package dev.rptag.king;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Presença do Rei (passiva). Nunca causa dano. Três faixas de distância:
 *  longe  (15): sussurros e risadas só para quem está dentro (+ ilusões visuais no cliente)
 *  médio  (8):  lentidão gradual + Náusea (visão distorcida)
 *  perto  (4):  Fraqueza + Marca de Carcosa
 */
@EventBusSubscriber(modid = King.MODID)
public final class KingPassive {
    private static final Map<UUID, Long> NEXT_SOUND = new HashMap<>();

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer king) || king.tickCount % 10 != 0) return;
        if (!king.isAlive() || king.isSpectator() || !KingAbilities.isKing(king)) return;
        ServerLevel lv = king.serverLevel();
        double far = KingConfig.AURA_FAR.get(), mid = KingConfig.AURA_MID.get(), near = KingConfig.AURA_NEAR.get();
        Vec3 c = king.position();
        long now = lv.getGameTime();
        for (LivingEntity t : lv.getEntitiesOfClass(LivingEntity.class, king.getBoundingBox().inflate(far, far * 0.6, far),
                x -> KingAbilities.enemy(king, x))) {
            double d = t.position().distanceTo(c);
            if (d > far) continue;
            if (t instanceof ServerPlayer p && NEXT_SOUND.getOrDefault(p.getUUID(), 0L) <= now) {
                SoundEvent s = (d <= mid && lv.random.nextFloat() < 0.35f) ? KingSounds.LAUGH : KingSounds.WHISPER;
                p.playNotifySound(s, SoundSource.AMBIENT, 0.55f + (float) (0.4 * (1 - d / far)), 0.9f + lv.random.nextFloat() * 0.2f);
                NEXT_SOUND.put(p.getUUID(), now + 100 + lv.random.nextInt(120));
            }
            if (d <= mid) {
                int amp = d > mid * 0.75 ? 0 : d > near ? 1 : 2;                            // lentidão cresce conforme se aproxima
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, amp, false, false, false));
                if (t instanceof ServerPlayer) t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, false));
            }
            if (d <= near) {
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false, false));
                KingAbilities.mark(t, 120);
            }
        }
    }

    private KingPassive() {}
}
