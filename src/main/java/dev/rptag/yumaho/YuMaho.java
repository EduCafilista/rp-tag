package dev.rptag.yumaho;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * Habilidades externas de Yu-Maho. Chamado por Aura.init(modBus).
 * Módulos: poder (YuPower), Smokescreen, Fa Jin, Blackwhip, Gearshift (YuAbilities),
 * efeitos de raio (YuHit + client/YuFxClient) e Dimensão Branca (YuWhite).
 */
public final class YuMaho {
    /** Namespace dos assets/dados (sons, lang, dimensão). */
    public static final String ID = "yumaho";
    /** MODID do rp-tag (registros do DeferredRegister). */
    public static final String MODID = "rptag";

    public static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    /** (5.78.0) O mod container vem do RPTagMod (o ModLoadingContext.get() nao e garantido no 1.21.1). */
    public static void init(IEventBus modBus, ModContainer container) {
        YuAttachments.TYPES.register(modBus);
        YuEntities.ENTITIES.register(modBus);
        modBus.addListener(YuSounds::register);
        container.registerConfig(ModConfig.Type.COMMON, YuConfig.SPEC, "rptag-yumaho.toml");
    }

    private YuMaho() {}
}
