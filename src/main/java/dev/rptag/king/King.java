package dev.rptag.king;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * (5.78.6) O REI DE AMARELO (Carcosa): aura passiva, 6 habilidades + ultimate numa roda.
 * Pacote do Drive portado para o rp-tag: ligado no RPTagMod (init) e no ModNetworking (rede).
 */
public final class King {
    /** Namespace de sons/idiomas. */
    public static final String ID = "carcosa";
    public static final String MODID = "rptag";

    public static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(ID, p); }

    public static void init(IEventBus modBus, ModContainer container) {
        KingAttachments.TYPES.register(modBus);
        KingEffects.EFFECTS.register(modBus);
        KingEntities.ENTITIES.register(modBus);
        modBus.addListener(KingSounds::register);
        container.registerConfig(ModConfig.Type.COMMON, KingConfig.SPEC, "rptag-king.toml");
    }

    private King() {}
}
