package dev.rptag;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.FluidTags;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * (5.44.0) <b>FLUXO — a ametista VERMELHA.</b>
 *
 * <p>Pedido do dono: <i>"faça uma ametista vermelha chamada fluxo, faça todas as versões da
 * ametista do mine normal so que com textura vermelha"</i>.
 *
 * <p>Então aqui está a família INTEIRA da ametista do Minecraft, com o mesmo comportamento e a
 * mesma silhueta — só o tecido da pedra muda de roxo para vermelho (as texturas são as
 * originais com a matiz girada, geradas por {@code arte/gerar_fluxo.py}, que lê o jar do
 * cliente; nada de redesenho no olho):
 *
 * <ul>
 *   <li>{@code fluxo_block} — o bloco de ametista (cubo cheio, dropa ele mesmo);</li>
 *   <li>{@code fluxo_budding} — a ametista em formação: <b>cresce</b> os brotos sozinha, igual
 *       à vanilla (só que roxa→vermelha). Não dropa nada, também igual à vanilla;</li>
 *   <li>{@code fluxo_bud_pequeno}, {@code fluxo_bud_medio}, {@code fluxo_bud_grande} — os três
 *       estágios do broto, com a mesma altura/colisão/luz dos da vanilla;</li>
 *   <li>{@code fluxo_cluster} — o cacho maduro: dropa 4 fragmentos (2 se explodir, o bloco
 *       mesmo com toque suave), exatamente a tabela da vanilla;</li>
 *   <li>{@code fluxo_shard} — o <b>Fragmento de Fluxo</b>, o "item" da família.</li>
 * </ul>
 *
 * <p><b>Como nasce no mundo:</b> não existe geodesia de Fluxo — o caminho é craftar (ou o ADM
 * dar com {@code /give}). O {@code fluxo_budding} é o multiplicador: planta um e ele vai
 * formando brotos em volta, que viram cacho e dropam fragmento. É a mesma economia da ametista.
 */
public final class FluxoAmetista {

    private FluxoAmetista() {
    }

    /** o registro dos BLOCOS do Fluxo (vai no barramento no construtor do mod). */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RPTagMod.MODID);
    /** o registro dos ITENS do Fluxo (o fragmento + o item de cada bloco). */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(RPTagMod.MODID);

    /** os nomes de TODOS os blocos, na ordem em que aparecem no /give e no check.py. */
    public static final String[] NOMES_BLOCOS = {
        "fluxo_block", "fluxo_budding",
        "fluxo_bud_pequeno", "fluxo_bud_medio", "fluxo_bud_grande", "fluxo_cluster",
    };

    /** as propriedades base: dureza e som são os da ametista (1,5 e o "tilintar" de cristal). */
    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .strength(1.5F)
                .sound(SoundType.AMETHYST);
    }

    /** as propriedades dos brotos/cacho: sem oclusão (é cruz), some com pistão e água dentro. */
    private static BlockBehaviour.Properties propsBroto(int luz) {
        return props()
                .noOcclusion()
                .lightLevel(st -> luz)
                .pushReaction(PushReaction.DESTROY);
    }

    /** o bloco de Fluxo (o cubo cheio). */
    public static final DeferredBlock<AmethystBlock> BLOCO =
            BLOCKS.register("fluxo_block", () -> new AmethystBlock(props()));
    /** a ametista de Fluxo em FORMAÇÃO — a que cresce. */
    public static final DeferredBlock<FluxoBuddingBlock> BUDDING =
            BLOCKS.register("fluxo_budding", () -> new FluxoBuddingBlock(props().randomTicks()));
    /** os brotos e o cacho, com a mesma altura e o mesmo "deslocamento" dos da vanilla. */
    public static final DeferredBlock<AmethystClusterBlock> BUD_PEQUENO =
            BLOCKS.register("fluxo_bud_pequeno",
                    () -> new AmethystClusterBlock(3.0F, 4.0F, propsBroto(1)));
    public static final DeferredBlock<AmethystClusterBlock> BUD_MEDIO =
            BLOCKS.register("fluxo_bud_medio",
                    () -> new AmethystClusterBlock(4.0F, 3.0F, propsBroto(2)));
    public static final DeferredBlock<AmethystClusterBlock> BUD_GRANDE =
            BLOCKS.register("fluxo_bud_grande",
                    () -> new AmethystClusterBlock(5.0F, 3.0F, propsBroto(4)));
    public static final DeferredBlock<AmethystClusterBlock> CLUSTER =
            BLOCKS.register("fluxo_cluster",
                    () -> new AmethystClusterBlock(7.0F, 3.0F, propsBroto(5)));

    /** o item de cada bloco (dá para pegar, colocar e craftar). */
    public static final DeferredItem<?> ITEM_BLOCO = ITEMS.registerSimpleBlockItem(BLOCO);
    public static final DeferredItem<?> ITEM_BUDDING = ITEMS.registerSimpleBlockItem(BUDDING);
    public static final DeferredItem<?> ITEM_BUD_PEQUENO = ITEMS.registerSimpleBlockItem(BUD_PEQUENO);
    public static final DeferredItem<?> ITEM_BUD_MEDIO = ITEMS.registerSimpleBlockItem(BUD_MEDIO);
    public static final DeferredItem<?> ITEM_BUD_GRANDE = ITEMS.registerSimpleBlockItem(BUD_GRANDE);
    public static final DeferredItem<?> ITEM_CLUSTER = ITEMS.registerSimpleBlockItem(CLUSTER);

    /** o FRAGMENTO DE FLUXO (o item da família, o "amethyst shard" vermelho). */
    public static final DeferredItem<?> SHARD =
            ITEMS.registerSimpleItem("fluxo_shard", new net.minecraft.world.item.Item.Properties());

    /**
     * (5.44.0) <b>AMETISTA DE FLUXO EM FORMAÇÃO.</b>
     *
     * <p>É a {@link net.minecraft.world.level.block.BuddingAmethystBlock} com uma diferença que
     * não dava para contornar: a da vanilla cresce <b>ametista ROXA</b> (os blocos estão
     * cravados dentro dela). Esta cresce os brotos do Fluxo, com a mesma regra: 20% de chance
     * por tick aleatório, um dos 6 lados, e o broto sobe de estágio
     * (pequeno → médio → grande → cacho). O som de cada passo é o mesmo "tilintar" da vanilla.
     */
    public static final class FluxoBuddingBlock extends AmethystBlock {

        public FluxoBuddingBlock(BlockBehaviour.Properties props) {
            super(props);
        }

        @Override
        protected void randomTick(BlockState estado, ServerLevel nivel, BlockPos pos,
                                  RandomSource sorte) {
            try {
                if (sorte.nextInt(5) != 0) {
                    return;                                  // 20% por tick, igual à vanilla
                }
                Direction lado = Direction.values()[sorte.nextInt(Direction.values().length)];
                BlockPos alvo = pos.relative(lado);
                BlockState ali = nivel.getBlockState(alvo);
                Block proximo = null;
                if (ali.isAir() || ali.canBeReplaced()) {
                    proximo = BUD_PEQUENO.get();
                } else if (ali.is(BUD_PEQUENO.get())) {
                    proximo = BUD_MEDIO.get();
                } else if (ali.is(BUD_MEDIO.get())) {
                    proximo = BUD_GRANDE.get();
                } else if (ali.is(BUD_GRANDE.get())) {
                    proximo = CLUSTER.get();
                }
                if (proximo == null) {
                    return;
                }
                BlockState novo = proximo.defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, lado)
                        .setValue(AmethystClusterBlock.WATERLOGGED,
                                ali.getFluidState().is(FluidTags.WATER));
                nivel.setBlockAndUpdate(alvo, novo);
                nivel.playSound(null, alvo, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                        1.0F, 1.0F);
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: o Fluxo em formacao falhou (o bloco segue)", t);
            }
        }
    }

}
