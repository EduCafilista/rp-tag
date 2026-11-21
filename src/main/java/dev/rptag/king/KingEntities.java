package dev.rptag.king;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class KingEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, King.MODID);

    public static final Supplier<EntityType<IllusionEntity>> ILLUSION = ENTITIES.register("king_illusion",
            () -> EntityType.Builder.<IllusionEntity>of(IllusionEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).updateInterval(1).noSave().fireImmune()
                    .build("king_illusion"));

    private KingEntities() {}
}
