package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * (5.28.0) <b>A CHAVE DO JULGAMENTO</b> — o item que o ADM entrega/dropa depois da luta.
 *
 * <p>Pedido do dono: <i>"após ele derrotar o boss o adm vai dropar um item que seria uma chave
 * que abriria o portal atrás do boss, assim podendo retornar à vida, e ganhando mais 2 vidas
 * canônicas"</i>.
 *
 * <p>Ela só funciona dentro da sala branca: gira uma vez, abre o portão de escuridão atrás do
 * trono e some da mão (a chave é de uso único — a passagem fica aberta pra quem vier depois).
 */
public class ChaveJulgamentoItem extends Item {

    public ChaveJulgamentoItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand mao) {
        ItemStack pilha = player.getItemInHand(mao);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(pilha, true);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (JuizoFinal.abrirPortao(sp)) {
                if (!sp.isCreative()) {
                    pilha.shrink(1);        // a chave se gasta no portão
                }
                return InteractionResultHolder.success(pilha);
            }
        }
        return InteractionResultHolder.fail(pilha);
    }

    @Override
    public void appendHoverText(ItemStack pilha, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Abre o portal atrás do trono do Julgamento.")
                .withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Quem atravessa volta à vida com +2 vidas.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
