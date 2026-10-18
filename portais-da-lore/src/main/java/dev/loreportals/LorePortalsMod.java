package dev.loreportals;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * PORTAIS DA LORE — mod independente de portais redondos pra contar historia.
 *
 * <p>DOIS MODOS DE PORTAL:</p>
 * <ul>
 *   <li><b>PAR (colocar e conectar)</b>: coloque 2 nucleos e use o
 *       Cristal de Vinculo em cada um — os dois portais se conectam e o
 *       circulo magico horizontal aparece sobre o nucleo.</li>
 *   <li><b>MOLDURA (formato especifico + nucleo)</b>: monte o anel de
 *       11 blocos de Moldura Runica (formato redondo 5x5) com o Nucleo
 *       no vao do meio de baixo — o portal ativa SOZINHO quando o
 *       formato fecha, com o grande portal circular de galaxia.</li>
 * </ul>
 *
 * <p>O visual do portal e 100% procedural (renderizador proprio). Sem
 * telemetria, sem coleta de dados.</p>
 */
@Mod(LorePortalsMod.MODID)
public final class LorePortalsMod {

    public static final String MODID = "loreportals";
    public static final String VERSION = "1.3.2";
    public static final org.slf4j.Logger LOG =
            com.mojang.logging.LogUtils.getLogger();

    // ------------------------------------------------------------------
    // REGISTROS
    // ------------------------------------------------------------------
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    /** SOM DE ENTRADA no portal (ogg proprio, assets/sounds). */
    public static final Supplier<SoundEvent> SOM_PORTAL_ENTRADA =
            SOUNDS.register("portal_entrada", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "portal_entrada")));

    // ---- blocos ----
    /** Moldura Runica: bloco de pedra com runas — o anel do portal grande. */
    public static final Supplier<PortalFrameBlock> PORTAL_FRAME =
            BLOCKS.register("portal_frame", () -> new PortalFrameBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                            .strength(3.0F, 1200.0F)));
    /** Nucleo do Portal: o bloco que ativa e segura o portal. */
    public static final Supplier<PortalCoreBlock> PORTAL_CORE =
            BLOCKS.register("portal_core", () -> new PortalCoreBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)
                            .strength(3.0F, 1200.0F)));

    // ---- itens (os de bloco saem do helper padrao do NeoForge) ----
    public static final Supplier<net.minecraft.world.item.BlockItem> PORTAL_FRAME_ITEM =
            ITEMS.registerSimpleBlockItem("portal_frame", PORTAL_FRAME);
    public static final Supplier<net.minecraft.world.item.BlockItem> PORTAL_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("portal_core", PORTAL_CORE);
    /** Cristal de Vinculo: liga dois nucleos (PAR) ou dois portais grandes. */
    public static final Supplier<CristalVinculoItem> CRISTAL =
            ITEMS.register("cristal_vinculo", CristalVinculoItem::new);

    // ---- block entity ----
    public static final Supplier<BlockEntityType<PortalBlockEntity>> PORTAL_BE =
            BE_TYPES.register("portal",
                    () -> BlockEntityType.Builder.of(PortalBlockEntity::new, PORTAL_CORE.get()).build(null));

    // ---- aba criativa ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("principal",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("\u00a75Portais da Lore"))
                    .icon(() -> new ItemStack(PORTAL_CORE_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(PORTAL_FRAME_ITEM.get());
                        out.accept(PORTAL_CORE_ITEM.get());
                        out.accept(CRISTAL.get());
                    })
                    .build());

    public LorePortalsMod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BE_TYPES.register(modBus);
        TABS.register(modBus);
        SOUNDS.register(modBus);
    }

    /** Atalho de id (ResourceLocation do mod). */
    public static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** ServerLevel de uma dimensao salva como "ns:caminho", ou null se nao existir. */
    public static net.minecraft.server.level.ServerLevel nivel(
            net.minecraft.server.MinecraftServer server, String dim) {
        int idx = dim.indexOf(':');
        if (idx <= 0 || idx >= dim.length() - 1) {
            return null;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath(dim.substring(0, idx), dim.substring(idx + 1));
        return server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, rl));
    }
}
