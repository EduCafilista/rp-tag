package dev.rptag.aura;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * (5.67.0) A ZONA DE RADIACAO — o que o {@code /radiacao} faz no servidor.
 *
 * <p>Quem esta com a aura de radiacao irradia tudo (jogadores e mobs) num raio de
 * {@link #RAIO} blocos: quanto mais perto, mais forte. Dano magico por segundo + fraqueza,
 * fome, tontura e lentidao; os efeitos ficam alguns segundos depois de sair da zona
 * ("ficou irradiado"). Quem tambem esta com a radiacao e imune.
 *
 * <p><b>So ADM ativa</b> (pedido do dono): o comando {@code /radiacao} exige nivel 2, e quem
 * nao tem permissao nao consegue por a aura. Aqui, de seguranca, quem perdeu o cargo tambem
 * para de irradiar.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class Radiacao {
    /** Raio da zona (o mesmo usado pelo anel azul no cliente). */
    public static final double RAIO = 10.0;
    /** Dano por segundo: 1 na borda, 4 colado no portador. */
    private static final float DANO_MIN = 1.0f, DANO_MAX = 4.0f;
    /** 5 s de efeito residual depois de sair da zona. */
    private static final int TICKS_EFEITO = 100;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer srv = e.getServer();
        int tempo = srv.getTickCount();
        if (tempo % 5 != 0) return;              // varre a cada 5 ticks
        boolean ferir = tempo % 20 == 0;         // dano/efeitos 1x por segundo

        for (ServerPlayer portador : srv.getPlayerList().getPlayers()) {
            if (portador.isSpectator() || !portador.isAlive()) continue;
            if (portador.getData(AuraAnexos.AURA.get()) != AuraEstilo.RADIACAO.index()) continue;
            if (!portador.hasPermissions(2)) continue;      // so ADM irradia
            ServerLevel level = portador.serverLevel();
            AABB caixa = portador.getBoundingBox().inflate(RAIO);
            try {
                for (LivingEntity alvo : level.getEntitiesOfClass(LivingEntity.class, caixa,
                        en -> en != portador && en.isAlive() && !en.isSpectator())) {
                    if (alvo instanceof Player pl
                            && pl.getData(AuraAnexos.AURA.get()) == AuraEstilo.RADIACAO.index()) {
                        continue;                            // os irradiados sao imunes entre si
                    }
                    double d = alvo.distanceTo(portador);
                    if (d > RAIO) continue;
                    irradiar(portador, alvo, (float) Mth.clamp(1.0 - d / RAIO, 0.0, 1.0), ferir);
                }
            } catch (RuntimeException erro) {
                // regra de ouro do mod: erro aqui nao pode derrubar o tick do servidor
            }
        }
    }

    private static void irradiar(ServerPlayer portador, LivingEntity alvo, float k, boolean ferir) {
        if (alvo instanceof ServerPlayer sp) {
            // contador Geiger: mais cliques quanto mais perto
            if (sp.getRandom().nextFloat() < 0.15f + 0.6f * k) {
                sp.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.5f,
                        1.6f + sp.getRandom().nextFloat() * 0.6f);
            }
            if (ferir) {
                sp.displayClientMessage(Component.literal(
                        "\u00a7bRadia\u00e7\u00e3o! Afaste-se de " + portador.getName().getString()), true);
            }
        }
        if (!ferir) return;

        alvo.invulnerableTime = 0;
        alvo.hurt(alvo.level().damageSources().magic(), Mth.lerp(k, DANO_MIN, DANO_MAX));
        alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TICKS_EFEITO, k > 0.6f ? 1 : 0, false, true));
        alvo.addEffect(new MobEffectInstance(MobEffects.HUNGER, TICKS_EFEITO, 1, false, true));
        if (k > 0.45f) alvo.addEffect(new MobEffectInstance(MobEffects.CONFUSION, TICKS_EFEITO, 0, false, true));
        if (k > 0.70f) alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TICKS_EFEITO, 1, false, true));
    }

    private Radiacao() {}
}
