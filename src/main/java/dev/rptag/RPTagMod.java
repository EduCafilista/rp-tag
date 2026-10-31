package dev.rptag;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * RP Tag — mostra o estado RP no nome dos jogadores, baloes de fala e comandos /rp.
 *
 * <p>Os handlers de eventos ficam nas classes anotadas com {@code @EventBusSubscriber}:
 * <ul>
 *   <li>{@link ModNetworking} — registro dos pacotes de rede (mod bus)</li>
 *   <li>{@link ServerEvents} — eventos do lado do servidor (login, nomes, tab)</li>
 *   <li>{@link RPCommands} — comando /rp</li>
 *   <li>{@link BalaoCommands} — comando /balao (tela de personalizacao)</li>
 *   <li>{@code dev.rptag.client.ClientEvents} — eventos do lado do cliente (nametag)</li>
 *   <li>{@code dev.rptag.client.BubbleStyleScreen} — tela com seletor de cor</li>
 * </ul>
 *
 * <p><b>4.9.3</b> — Chegaram o aviso CIBERPSICOSE (estilo 14) e a MASCARA DE
 * PEDRA ({@link MascaraPedraItem}): item de cabeça, desenhado em 3D no rosto
 * pelo cliente ({@code client/CamadaMascara}), para o pessoal usar em lore.
 *
 * <p><b>4.9.1</b> — O aviso DIVINO voltou a ser o procedural antigo (dourado, com
 * halo rúnico e asas desenhadas): o vídeo do Blender que o Marcello pôs na 4.6.0
 * ficou esquisito (fundo cinza/branco) e o dono pediu o antigo de volta. A pasta
 * {@code assets/rptag/fx/divino} saiu do mod.
 *
 * <p><b>4.9.0</b> — O TABLET DA FICHA SAIU: o dono recusou a tela ("isso ficou
 * horrivel") e mandou remover do mod e do GitHub. Nao existe mais o item
 * {@code rptag:tablet}, nem as classes Ficha*, nem os comandos /tablet e /ficha.
 * O que sobrou do personagem e o {@link Persona} (resumo em texto), criado no
 * proprio /rp. Tambem saiu o {@code /rp admin regra} (as regras de RP agora
 * valem sozinhas) e o par {@code /rp admin confirmar|cancelar} (o lote de vida
 * aplica na hora).
 */
@Mod(RPTagMod.MODID)
public final class RPTagMod {

    public static final String MODID = "rptag";
    public static final String VERSION = "4.9.7";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** (3.76.0) SONS proprios — o da vida perdida e o ogg da lore (catbox). */
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final Supplier<SoundEvent> SOM_CANON_DEATH =
            SOUNDS.register("canon_death", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "canon_death")));

    /** (3.90.0) SOM do aviso INFERNO: riser sombrio sintetizado (boom +
     *  rumble + crackles de fogo, ~9s). Quando o user reenviar o link do
     *  audio (o catbox rxavzi.mp3 deu 404), basta trocar o .ogg. */
    public static final Supplier<SoundEvent> SOM_INFERNO =
            SOUNDS.register("inferno_aviso", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "inferno_aviso")));

    /** (4.7.7) TRILHA do aviso EGIPCIO: alaúde, ney e tambor de moldura,
     *  sintetizados em arte/audio_egipcio.py e SINCRONIZADOS com a animação
     *  (riser -> clarão -> asas -> cartucho -> sino). 7,6 s. */
    public static final Supplier<SoundEvent> SOM_EGIPCIO =
            SOUNDS.register("egipcio_aviso", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "egipcio_aviso")));

    /** (4.9.5) SOM do aviso CIBERPSICOSE: o áudio que o dono mandou (catbox
     *  mkqz2x.mp3, 4,05 s), convertido pra .ogg/Vorbis em arte/previa_mascara3d.py.
     *  Toca UMA vez no começo do aviso; o vidro quebrando continua por cima. */
    public static final Supplier<SoundEvent> SOM_CIBERPSICOSE =
            SOUNDS.register("ciberpsicose_aviso", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "ciberpsicose_aviso")));

    /** (3.84.0) BLOCO/ITEM/BE do Coração Canônico (só adm obtém via /give;
     * nunca spawna naturalmente). */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final Supplier<CanonCoracaoBlock> CORACAO_BLOCK =
            BLOCKS.register("coracao_canon", () -> new CanonCoracaoBlock(
                    BlockBehaviour.Properties.of()
                            .strength(-1.0F, 3600000.0F) // indestrutivel em survival
                            .sound(SoundType.AMETHYST)
                            .noOcclusion()));
    public static final Supplier<BlockEntityType<CanonCoracaoBlockEntity>> CORACAO_BE =
            BE_TYPES.register("coracao_canon", () -> BlockEntityType.Builder
                    .of(CanonCoracaoBlockEntity::new, CORACAO_BLOCK.get()).build(null));
    public static final Supplier<Item> CORACAO_ITEM =
            ITEMS.register("coracao_canon", () -> new BlockItem(CORACAO_BLOCK.get(),
                    new Item.Properties()));
    /** (4.0.0) VARINHA DE LOREZONE (so admin): clique direito mostra/esconde
     *  a barreira da zona mais proxima. Nao faz nada nas maos de um player. */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item,
            net.minecraft.world.item.Item> LOREZONE_WAND = ITEMS.register("lorezone_wand",
                    () -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()
                            .stacksTo(1)
                            .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** (4.9.3) MASCARA DE PEDRA (JoJo) — item de cabeca, para usar em lore. */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item,
            MascaraPedraItem> MASCARA_PEDRA = ITEMS.register("mascara_pedra",
            () -> new MascaraPedraItem(new net.minecraft.world.item.Item.Properties()
                    .stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));

    public RPTagMod(IEventBus modEventBus, ModContainer modContainer) {
        // (3.45.0) o efeito MEDO usa DeferredRegister (registro oficial,
        // aparece no /effect give e no HUD como efeito de verdade).
        RPEffects.EFFECTS.register(modEventBus);
        // (3.76.0) sons proprios (mesmo padrao do Portais da Lore)
        SOUNDS.register(modEventBus);
        // (3.84.0) coracao canonico
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BE_TYPES.register(modEventBus);
        // (4.5.1) BUG QUE DERRUBA JOGADOR NO LOGIN: argumento de comando custom
        // PRECISA estar no registry COMMAND_ARGUMENT_TYPE. Sem isso o servidor
        // nao consegue serializar a arvore de comandos e o cliente cai com
        // "Invalid player data" (IllegalArgumentException em ArgumentTypeInfos.byClass).
        RpArgumentTypes.ARGUMENT_TYPES.register(modEventBus);
    }
}
