package dev.rptag.aura;

import dev.rptag.NeptuneEventos;
import dev.rptag.RPTagMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Efeito ao ATACAR de cada aura (golpe corpo a corpo): Radiação, Arco-íris, Vazio e Iluminação/Gilgamesh (ouro).
 * Não muda o dano. O servidor só avisa; os clientes próximos desenham (AuraHitClient).
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class AuraHitFx {
    private static final Map<UUID, Long> LAST = new HashMap<>();

    @SubscribeEvent
    public static void onHit(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || src.getDirectEntity() != sp) return;   // só corpo a corpo
        LivingEntity target = e.getEntity();
        if (target == sp) return;
        int idx = sp.getData(AuraAnexos.AURA.get());
        AuraEstilo s = idx > 0 ? AuraEstilo.byIndex(idx) : null;
        if (s == null && NeptuneEventos.ehDeus(sp.getUUID())) {
            s = AuraEstilo.VAZIO;
        }
        if (s != AuraEstilo.RADIACAO && s != AuraEstilo.ARCOIRIS
                && s != AuraEstilo.VAZIO && s != AuraEstilo.ILUMINACAO) {
            return;
        }
        long now = sp.level().getGameTime();
        if (now - LAST.getOrDefault(sp.getUUID(), -10L) < 3) return;                                // evita enchente de efeitos
        LAST.put(sp.getUUID(), now);

        ServerLevel lv = sp.serverLevel();
        Vec3 c = target.getBoundingBox().getCenter();
        Vec3 d = c.subtract(sp.position());
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        float dx = len < 1e-3 ? 0f : (float) (d.x / len), dz = len < 1e-3 ? 1f : (float) (d.z / len);
        PacketDistributor.sendToPlayersNear(lv, null, c.x, c.y, c.z, 48.0,
                new AuraHitPayload(s.index(), c.x, c.y, c.z, dx, dz));
        SoundEvent snd;
        float vol = 0.5f, pitch = 1f;
        switch (s) {
            case RADIACAO -> { snd = SoundEvents.BEACON_POWER_SELECT; pitch = 1.6f; vol = 0.35f; }
            case ARCOIRIS -> { snd = SoundEvents.AMETHYST_BLOCK_CHIME; pitch = 1.2f; }
            case VAZIO -> { snd = SoundEvents.ENDERMAN_TELEPORT; pitch = 0.5f; vol = 0.4f; }
            default -> { snd = SoundEvents.PLAYER_ATTACK_SWEEP; pitch = 1.4f; }
        }
        lv.playSound(null, c.x, c.y, c.z, snd, SoundSource.PLAYERS, vol, pitch);
    }

    private AuraHitFx() {}
}
