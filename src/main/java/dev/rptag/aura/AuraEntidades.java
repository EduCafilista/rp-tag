package dev.rptag.aura;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** (5.60.0) a ARMA DOURADA que sai dos portoes. */
public final class AuraEntidades {
    public static final DeferredRegister<EntityType<?>> ENTIDADES =
            DeferredRegister.create(Registries.ENTITY_TYPE, dev.rptag.RPTagMod.MODID);

    public static final Supplier<EntityType<ArmaDouradaEntity>> ARMA_DOURADA =
            ENTIDADES.register("golden_weapon",
                    () -> EntityType.Builder.<ArmaDouradaEntity>of(ArmaDouradaEntity::new, MobCategory.MISC)
                            .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).noSave()
                            .build("golden_weapon"));

    private AuraEntidades() {}
}
