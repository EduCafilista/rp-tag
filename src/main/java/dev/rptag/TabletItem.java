package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * (4.8.0) TABLET DA LORE — o item que abre a ficha do personagem.
 *
 * <p>Botão direito = abre a criação/edição da ficha. Shift + botão direito =
 * mostra a ficha pronta no chat (para quem prefere ler sem abrir a tela).
 */
public class TabletItem extends Item {

    public TabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                mostrarNoChat(sp);
            } else {
                FichaServer.abrir(sp);
                sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void mostrarNoChat(ServerPlayer sp) {
        FichaRp f = RPWorldData.get(sp.server).getFicha(sp.getUUID());
        if (f.vazia()) {
            sp.sendSystemMessage(Component.literal("Sua ficha ainda está em branco — clique com o direito no tablet.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        FichaLivro.mostrar(sp, sp.getGameProfile().getName(), f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Sua ficha do lore-SMP").withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Botão direito ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("abre a criação").withStyle(ChatFormatting.WHITE)));
        linhas.add(Component.literal("Shift + direito ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("mostra no chat").withStyle(ChatFormatting.DARK_GRAY)));
    }
}
