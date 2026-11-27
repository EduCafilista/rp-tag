package dev.rptag.vampire;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * (5.78.18) VAMPIROS — pacote completo integrado ao RP Tag.
 *
 * <p>Unificado com o atributo {@code rptag:vampiro} e com o {@code VampiroSistema}:
 * <ul>
 *   <li>{@code /vampiro ligar|desligar [jogador]} e {@code /rp admin vampiro <jogadores> ligar|desligar}
 *       ativam tudo ao mesmo tempo;</li>
 *   <li>Sangue = barra de fome desenhada como 10 gotas (sem comida comum, só Garrafa de Sangue);</li>
 *   <li>Roda na tecla V: 1 Morder, 2 Forma de Morcego (Amuleto do Morcego), 3 Visão Vampírica;</li>
 *   <li>Sol queima (protegido por Amuleto do Eclipse, efeito Sombra ou capacete que gasta durabilidade).</li>
 * </ul>
 */
public final class Vampire {
    public static final String MODID = "rptag";
    private static final ResourceKey<CreativeModeTab> TAB_FOOD =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("food_and_drinks"));
    private static final ResourceKey<CreativeModeTab> TAB_TOOLS =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("tools_and_utilities"));

    public static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(MODID, p); }

    public static void init(IEventBus modBus) {
        VampireItems.ITEMS.register(modBus);
        VampireAttachments.TYPES.register(modBus);
        modBus.addListener(Vampire::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(TAB_FOOD)) {
            e.accept(VampireItems.EMPTY_FLASK.get());
            e.accept(VampireItems.BLOOD_FLASK.get());
        } else if (e.getTabKey().equals(TAB_TOOLS)) {
            e.accept(VampireItems.BAT_AMULET.get());
            e.accept(dev.rptag.RPTagMod.AMULETO_ECLIPSE.get());
        }
    }

    private Vampire() {}
}
