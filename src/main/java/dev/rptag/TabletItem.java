package dev.rptag;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * O TABLET QUIRALOS — agora <b>um para cada jogador</b> (5.13.0).
 *
 * <h2>Pedido do dono</h2>
 * <i>"se eu pegar o tablet pronto de outra pessoa, ele deve ser um para cada player, e se eu
 * pegar o de outro player eu ver a do outro player... fica guardado as informações (lore de roubo
 * de tablet para pegar informações)"</i>.
 *
 * <h2>Como funciona</h2>
 * O <b>item</b> carrega o dono (UUID + nome) em {@code custom_data}. Quem abre o tablet vê a ficha
 * de <b>quem é o dono do item</b> — não a ficha de quem clicou:
 *
 * <ul>
 *   <li>tablet do próprio jogador → abre a ficha dele (edita se estiver liberada);</li>
 *   <li>tablet de outra pessoa → abre a ficha <b>do outro, só leitura</b>, com o nome dele no topo
 *       ("FICHA DE ..."): roubar o tablet entrega a ficha inteira, e o dono continua sendo o dono
 *       (devolver o tablet devolve o acesso);</li>
 *   <li>a ficha fica no mundo ({@link RPWorldData}), então o conteúdo <b>não</b> desaparece com o
 *       tablet: o item é só a chave.</li>
 * </ul>
 */
public final class TabletItem extends Item {

    /** chave (custom_data) do UUID do dono. */
    public static final String TAG_DONO = "TabletDono";
    /** chave (custom_data) do nome do dono (pra mostrar no tooltip sem consultar o servidor). */
    public static final String TAG_NOME = "TabletNome";

    public TabletItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------ dono do item
    /** @return UUID do dono do tablet, ou null se ele nunca foi batizado. */
    public static UUID dono(ItemStack stack) {
        CustomData dados = stack.get(DataComponents.CUSTOM_DATA);
        if (dados == null) {
            return null;
        }
        CompoundTag tag = dados.copyTag();
        return tag.hasUUID(TAG_DONO) ? tag.getUUID(TAG_DONO) : null;
    }

    /** @return nome do dono gravado no item (string vazia se não tem). */
    public static String donoNome(ItemStack stack) {
        CustomData dados = stack.get(DataComponents.CUSTOM_DATA);
        if (dados == null) {
            return "";
        }
        return dados.copyTag().getString(TAG_NOME);
    }

    /** grava o dono no item (uma vez). */
    public static void batizar(ItemStack stack, UUID id, String nome) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(TAG_DONO, id);
            tag.putString(TAG_NOME, nome == null ? "" : nome);
        });
    }

    /** o tablet pertence a este jogador? */
    public static boolean ehDo(ItemStack stack, UUID id) {
        UUID dono = dono(stack);
        return dono != null && dono.equals(id);
    }

    // ---------------------------------------------------------------- interação
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            TabletService.open(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            TabletService.open(player, context.getItemInHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    /**
     * O tooltip diz de quem é o tablet — é assim que o jogador descobre que roubou a ficha de
     * alguém (e é assim que o admin confere sem abrir).
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        UUID dono = dono(stack);
        String nome = donoNome(stack);
        if (dono == null) {
            tooltip.add(Component.literal("Tablet sem dono — clique direito para assumir")
                    .withStyle(ChatFormatting.GRAY));
        } else if (context.level() != null && net.minecraft.client.Minecraft.getInstance().player != null
                && dono.equals(net.minecraft.client.Minecraft.getInstance().player.getUUID())) {
            tooltip.add(Component.literal("Clique direito para abrir SUA ficha").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Tablet de " + (nome.isEmpty() ? "alguém" : nome))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Clique direito para LER a ficha dele").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Neptune SMP").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
