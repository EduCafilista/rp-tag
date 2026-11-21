package dev.rptag.king;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Marcado por Carcosa: sem dano próprio; quem tem a marca sofre mais dano do Rei e recebe as ondas do ultimate. */
public final class KingEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, King.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> MARK = EFFECTS.register("carcosa_mark",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xE8B800) {});

    private KingEffects() {}
}
