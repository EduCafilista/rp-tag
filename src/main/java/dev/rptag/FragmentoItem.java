package dev.rptag;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * (5.13.0) FRAGMENTO DO VAZIO — a peça que abre o portal negro.
 *
 * <p>Pedido 50: <i>"ter fragmentos do vazio, ter pedestais em volta do centro aonde spawna na
 * dimensão, e assim que ele colocar os 8 fragmentos nos pedestais, abre um portal negro"</i>.
 *
 * <p>O fragmento é <b>item de verdade</b> (aparece no inventário, pode ser roubado, perdido,
 * dropado): é encontrado nos <b>santuários</b> espalhados pelo vazio e encaixado nos 8 pedestais em
 * volta da praça central.
 */
public final class FragmentoItem extends Item {

    public FragmentoItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.literal("Uma peça do próprio vazio.").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("Encaixe nos 8 pedestais em volta da praça.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Oito delas abrem o portal de volta.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;         // brilho de encantado: salta aos olhos no chão escuro
    }
}
