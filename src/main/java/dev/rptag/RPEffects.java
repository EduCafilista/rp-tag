package dev.rptag;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * (3.45.0) Efeitos de status do mod.
 *
 * <p><b>MEDO</b> ({@code rptag:medo}): o efeito do terror RP — sem efeito
 * fisico no jogador (a tremedeira da camera vem do pacote de rede), o
 * status existe para o ICONE aparecer no canto do HUD (igual Veneno/
 * Wither) e pra poder dar via {@code /effect give <jogador> rptag:medo}.
 * O icone fica em {@code assets/rptag/textures/mob_effect/medo.png}
 * (3.53.0: carinha amarela assustada — e o cliente traduz o efeito em
 * tremedeira, entao {@code /effect give} dispara o medo completo).
 */
public final class RPEffects {

    private RPEffects() {
    }

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RPTagMod.MODID);

    /** O MEDO: roxo-escuro (cor da "aura" nos pixels de particula). */
    public static final DeferredHolder<MobEffect, MobEffect> MEDO =
            EFFECTS.register("medo", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6A0DAD) {
            }); // (1.21.1) o construtor e PROTECTED — subclasse anonima, igual o vanilla
}
