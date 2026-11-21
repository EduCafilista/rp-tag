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
 * (5.78.8) <b>RAIO E SOM — E SÓ ISSO.</b>
 *
 * <p>O dono: <i>"verifica o negócio do dano, de atacar — se ele não mexer nisso mais"</i>.
 * Até a 5.78.7 este evento <b>somava dano</b> a cada golpe corpo a corpo de quem tinha as
 * habilidades (bônus por poder + "golpe perfeito" que dobrava o bônus): o mod estava alterando
 * a luta sem o jogador pedir. <b>Isso saiu.</b> O dano do golpe é exatamente o do vanilla/do mod
 * de armas que estiver instalado — o RP Tag não soma, não multiplica e não cancela nada.
 *
 * <p>O que continua: o raio amarelo, a faísca e o som do golpe, escalando com a % de poder
 * (só visual, zero efeito na vida do alvo).
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
        // (5.78.8) NADA de e.setAmount(...): o dano quem decide é o jogo, não o RP Tag.

        ServerLevel lv = sp.serverLevel();
        Vec3 c = target.getBoundingBox().getCenter();
        YuFx.send(lv, YuFxPayload.HIT, c, c, f, 0f);
        float vol = 0.5f + 0.9f * f;
        float pitch = 1.25f - 0.4f * f + (sp.getRandom().nextFloat() - 0.5f) * 0.08f;
        lv.playSound(null, c.x, c.y, c.z, YuSounds.HIT, SoundSource.PLAYERS, vol, pitch);
    }

    private YuHit() {}
}
