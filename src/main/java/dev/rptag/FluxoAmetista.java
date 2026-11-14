package dev.rptag;

import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * (5.47.0) <b>FLUXO — a ametista VERMELHA, só o essencial.</b>
 *
 * <p>Pedido original: <i>"faça uma ametista vermelha chamada fluxo, faça todas as versões da
 * ametista do mine normal so que com textura vermelha"</i>. A 5.44.0 fez a família inteira
 * (bloco, ametista em formação, 3 brotos e cacho) e a 5.46.0 deixou os brotos <b>colocáveis</b>
 * para o dono testar.
 *
 * <p>Veredito dele: <i>"deixa só o bloco e o fluxo shard, tira as buddy horriveis kkkkk"</i>.
 *
 * <p>Então ficou <b>duas peças</b>, e é assim que fecha:
 * <ul>
 *   <li>{@code fluxo_block} — o <b>Bloco de Fluxo</b>: a pedra em cubo cheio, dureza e som da
 *       ametista, dropa ele mesmo;</li>
 *   <li>{@code fluxo_shard} — o <b>Fragmento de Fluxo</b>: o item, o "amethyst shard" vermelho
 *       (é ele que crafta o bloco).</li>
 * </ul>
 *
 * <p>As texturas vêm de {@code arte/gerar_fluxo.py}, que lê a ametista <b>de dentro do jar do
 * cliente</b> e transforma a matiz em vermelho saturado — nada de desenho no olho. Na 5.46.0 a
 * saturação foi empurrada de vez ("muito mais vermelha"): a média dos pixels foi de
 * (192, 99, 103) — rosa — para (185, 3, 7), sangue.
 */
public final class FluxoAmetista {

    private FluxoAmetista() {
    }

    /** o registro dos BLOCOS do Fluxo (vai no barramento no construtor do mod). */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RPTagMod.MODID);
    /** o registro dos ITENS do Fluxo (o fragmento + o item do bloco). */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(RPTagMod.MODID);

    /** (5.47.0) só um bloco: os brotos saíram a pedido do dono. */
    public static final String[] NOMES_BLOCOS = {"fluxo_block"};

    /** as propriedades base: dureza e som são os da ametista (1,5 e o "tilintar" de cristal). */
    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .strength(1.5F)
                .sound(SoundType.AMETHYST);
    }

    /** o bloco de Fluxo (o cubo cheio). */
    public static final DeferredBlock<AmethystBlock> BLOCO =
            BLOCKS.register("fluxo_block", () -> new AmethystBlock(props()));

    /** o item do bloco (dá para pegar, colocar e craftar). */
    public static final DeferredItem<?> ITEM_BLOCO = ITEMS.registerSimpleBlockItem(BLOCO);

    /** o FRAGMENTO DE FLUXO (o item da família, o "amethyst shard" vermelho). */
    public static final DeferredItem<?> SHARD =
            ITEMS.registerSimpleItem("fluxo_shard", new net.minecraft.world.item.Item.Properties());
}
