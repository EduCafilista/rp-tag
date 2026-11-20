package dev.rptag.yumaho;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class YuEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, YuMaho.MODID);

    public static final Supplier<EntityType<SmokeCloudEntity>> SMOKE = ENTITIES.register("smoke_cloud",
            () -> EntityType.Builder.<SmokeCloudEntity>of(SmokeCloudEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).clientTrackingRange(12).updateInterval(20).noSave().fireImmune()
                    .build("smoke_cloud"));

    private YuEntities() {}
}
