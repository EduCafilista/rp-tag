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
 * <p><b>4.9.3</b> — Chegou o aviso CIBERPSICOSE (estilo 14).
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
    public static final String VERSION = "5.25.0";
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

    // ======================= 5.4.0 — EVENTO NEPTUNE (terremoto/vazio/deus) =======================
    /** COLAPSO: a terra rachando (áudio do dono, 14,7 s) — toca quando o
     *  terremoto começa. */
    public static final Supplier<SoundEvent> SOM_TERREMOTO_COLAPSO =
            SOUNDS.register("terremoto_colapso", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "terremoto_colapso")));
    /** RUMBLE de tremor (sintetizado, 8 s, em loop enquanto dura o evento). */
    public static final Supplier<SoundEvent> SOM_RUMBLE =
            SOUNDS.register("rumble_terremoto", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "rumble_terremoto")));
    /** VAZIO: o som bizarro de apagar alguém da existência (áudio do dono, 2,25 s). */
    public static final Supplier<SoundEvent> SOM_VAZIO =
            SOUNDS.register("vazio_apagar", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "vazio_apagar")));
    /** TRILHA DO DEUS (áudio do dono, 39,3 s) — toca enquanto ele anda. */
    public static final Supplier<SoundEvent> SOM_TRILHA_DEUS =
            SOUNDS.register("trilha_deus", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "trilha_deus")));
    /** CAIXA DE MÚSICA de criança (sintetizada, 36 s) — a isca do bicho. */
    public static final Supplier<SoundEvent> SOM_CAIXA =
            SOUNDS.register("caixa_musica", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "caixa_musica")));
    /** (5.7.2) SOM do aviso CONHECIMENTO: sino de templo, drone grave, sussurros
     *  invertidos e campainhas (sintetizado em arte/audio_570.py, 11,5 s). */
    public static final Supplier<SoundEvent> SOM_CONHECIMENTO =
            SOUNDS.register("conhecimento_aviso", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "conhecimento_aviso")));
    /** O SUSTO: berro estrondoso (sintetizado, 2,6 s). */
    public static final Supplier<SoundEvent> SOM_BICHO_GRITO =
            SOUNDS.register("bicho_grito", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "bicho_grito")));

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
    /**
     * (5.7.8) EXPULSÃO — o som que o DONO mandou (h.uguu.se/lDBUuUuk.mp3).
     * Toca quando o jogador é JOGADO PRA FORA DO MAPA (exílio) e no
     * /terremoto sair.
     */
    public static final java.util.function.Supplier<SoundEvent> SOM_EXPULSAO = SOUNDS.register("expulsao",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "expulsao")));

    /** (5.7.7) O BICHO em si — criatura de verdade, dá pra bater nela. */
    /** (5.4.0) CAIXA DO BICHO (só por comando — é a isca do susto). */
    public static final Supplier<CaixaBichoBlock> CAIXA_BICHO = BLOCKS.register("caixa_bicho",
            () -> new CaixaBichoBlock(BlockBehaviour.Properties.of()
                    .strength(0.4F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));
    public static final Supplier<Item> CAIXA_BICHO_ITEM = ITEMS.register("caixa_bicho",
            () -> new BlockItem(CAIXA_BICHO.get(), new Item.Properties()));

    /** (4.0.0) VARINHA DE LOREZONE (so admin): clique direito mostra/esconde
     *  a barreira da zona mais proxima. Nao faz nada nas maos de um player. */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item,
            net.minecraft.world.item.Item> LOREZONE_WAND = ITEMS.register("lorezone_wand",
                    () -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()
                            .stacksTo(1)
                            .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** (4.9.3) MASCARA DE PEDRA (JoJo) — item de cabeca, para usar em lore. */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item,
            TabletItem> TABLET = ITEMS.register("tablet",
                    () -> new TabletItem(new Item.Properties().stacksTo(1)));

    /**
     * (5.13.2) ESCURIDÃO — o bloco NEGRO do portal (pedido do dono: "não quero que
     * seja black concrete, crie um bloco preto... um bloco negro chamado escuridão").
     *
     * <p>(5.13.5) <b>SEM COLISÃO</b> — pedido 55: <i>"não quero que o portal seja bloco
     * físico, quero que o player possa passar, atravessar ele mesmo, como um portal"</i>.
     * O bloco continua preto, indestrutível, com a animação e com o clique, mas o
     * jogador ATRAVESSA andando (e é aí que ele teleporta).
     */
    public static final Supplier<EscuridaoBlock> ESCURIDAO = BLOCKS.register("escuridao",
            () -> new EscuridaoBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.SCULK)
                    .noCollission()
                    .lightLevel(st -> 1)));
    public static final Supplier<Item> ESCURIDAO_ITEM = ITEMS.register("escuridao",
            () -> new BlockItem(ESCURIDAO.get(), new Item.Properties()));

    // ================= (5.17.0) NÚCLEO VIAL, FITA CASSETE e os sons deles =================
    /** (5.17.0) o "acordar" do núcleo vial (energia subindo + vidro). */
    public static final Supplier<SoundEvent> SOM_VIAL = SOUNDS.register("vial_ativar",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "vial_ativar")));
    /** (5.17.0) o núcleo rachando quando a carga chega a 0. */
    public static final Supplier<SoundEvent> SOM_VIAL_QUEBRAR = SOUNDS.register("vial_quebrar",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "vial_quebrar")));
    /** (5.17.0) o chiado da fita cassete ao apertar o play. */
    public static final Supplier<SoundEvent> SOM_FITA = SOUNDS.register("fita",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "fita")));

    /**
     * (5.17.0) NÚCLEO DE ENERGIA VIAL — só lore, se gasta em dias do jogo
     * (1% a cada 5 dias parado, 10% a cada 5 dias ativado) e, ao chegar a 0,
     * vira a {@link #NUCLEO_VIAL_QUEBRADO} no MESMO slot (nunca some).
     */
    public static final Supplier<NucleoVialItem> NUCLEO_VIAL = ITEMS.register("nucleo_vial",
            () -> new NucleoVialItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** (5.17.0) a versão QUEBRADA (carga 0) — mesma coisa, sem energia. */
    public static final Supplier<NucleoVialItem> NUCLEO_VIAL_QUEBRADO = ITEMS.register("nucleo_vial_quebrado",
            () -> new NucleoVialItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    /** (5.17.0) FITA CASSETE — o ADM grava a fala, o jogador ouve no clique direito. */
    public static final Supplier<FitaItem> FITA = ITEMS.register("fita",
            () -> new FitaItem(new Item.Properties().stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));

    /** (5.13.2) FRAGMENTO DO VAZIO — item que abre o portal negro do exílio. */
    public static final Supplier<FragmentoItem> FRAGMENTO_VAZIO = ITEMS.register("fragmento_vazio",
            () -> new FragmentoItem(new Item.Properties().stacksTo(8)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));

    /**
     * (5.25.0) AMULETO DO ECLIPSE — o item que segura o sol pra quem é vampiro
     * (ver {@link AmuletoEclipseItem} e {@link VampiroSistema}).
     */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item,
            AmuletoEclipseItem> AMULETO_ECLIPSE = ITEMS.register("amuleto_eclipse",
            () -> new AmuletoEclipseItem(new net.minecraft.world.item.Item.Properties()
                    .stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.RARE)));

    public RPTagMod(IEventBus modEventBus, ModContainer modContainer) {
        // (5.25.0) o ATRIBUTO do vampiro (rptag:vampiro) — registro oficial
        RPAttributes.ATTRIBUTES.register(modEventBus);
        // ...e o gancho que COLA o atributo no jogador (sem isso o player não tem a ficha dele)
        modEventBus.addListener(RPTagMod::colarAtributosNoJogador);
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
        // (5.10.1) /title VANILLA fora do jogo. Registrado NA MAO: o scan de
        // @EventBusSubscriber não pegou esta classe (as outras pegou), e aqui
        // não dá pra depender de sorte — o registro explícito é determinístico.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(TituloVanilla.class);
    }

    /**
     * (5.25.0) Todo jogador nasce com a ficha do vampiro (valor 0). Sem esse gancho o atributo
     * existe no registro mas NÃO existe no jogador — e o {@code getAttribute} devolve null.
     */
    private static void colarAtributosNoJogador(
            net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent event) {
        event.add(net.minecraft.world.entity.EntityType.PLAYER, RPAttributes.VAMPIRO);
    }
}
