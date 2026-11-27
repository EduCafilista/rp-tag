package dev.rptag.vampire;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Amuleto do Morcego: botão direito alterna a forma de morcego (só vampiros). Não gasta. */
public class BatAmuletItem extends Item {
    public BatAmuletItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) VampireLogic.toggleBat(sp, false);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Clique direito (ou tecla V): vira morceguinho voador.")
                .withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Voar gasta sangue · apenas para VAMPIROS.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
