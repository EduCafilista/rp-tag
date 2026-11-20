package dev.rptag.yumaho;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Raio amarelo + som em todo golpe corpo a corpo de quem tem as habilidades.
 * Tudo escala com a % de poder: dano extra, quantidade/tamanho dos raios, volume e tom do som.
 */
@EventBusSubscriber(modid = YuMaho.MODID)
public final class YuHit {

    @SubscribeEvent
    public static void onHit(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp) return;   // só corpo a corpo
        LivingEntity target = e.getEntity();
        if (target == sp || YuAbilities.busy || !YuPower.enabled(sp)) return;
        int pct = YuPower.percent(sp);
        if (pct <= 0) return;
        float f = pct / 100f;
        if (target instanceof net.minecraft.world.entity.player.Player && !YuConfig.PVP.get()) return;

        boolean crit = sp.getRandom().nextFloat() < YuConfig.CRIT_CHANCE_MAX.get() * f;
        float bonus = (float) (YuConfig.HIT_BONUS.get() * f * f) * (crit ? 2f : 1f);
        e.setAmount(e.getAmount() + bonus);

        ServerLevel lv = sp.serverLevel();
        Vec3 c = target.getBoundingBox().getCenter();
        YuFx.send(lv, YuFxPayload.HIT, c, c, f, crit ? 1f : 0f);
        float vol = (0.5f + 0.9f * f) * (crit ? 1.4f : 1f);
        float pitch = (1.25f - 0.4f * f) * (crit ? 0.85f : 1f) + (sp.getRandom().nextFloat() - 0.5f) * 0.08f;
        lv.playSound(null, c.x, c.y, c.z, YuSounds.HIT, SoundSource.PLAYERS, vol, pitch);
    }

    private YuHit() {}
}
