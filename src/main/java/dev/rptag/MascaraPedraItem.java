package dev.rptag;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * (4.9.3) MASCARA DE PEDRA — a máscara do JoJo (Phantom Blood), para o pessoal
 * usar em lore.
 *
 * <p>É um item de CABEÇA: {@link #getEquipmentSlot} devolve {@code HEAD}, então
 * o vanilla já aceita equipar (clique com o botão direito, ou
 * {@code /item replace entity @s armor.head with rptag:mascara_pedra}).
 *
 * <p>No inventário/mão ela aparece como o ícone pixel art; na cabeça, o desenho
 * em 3D vem de {@code dev.rptag.client.CamadaMascara} (só cliente — o servidor
 * nunca toca em classe de renderização).
 */
public class MascaraPedraItem extends Item {

    public MascaraPedraItem(Properties props) {
        super(props);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    /** só quem tem a máscara na cabeça "veste" ela (o resto é item comum). */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        return slot == EquipmentSlot.HEAD;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("A mascara de pedra do JoJo.").withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Equipe na cabeca (armor.head).").withStyle(ChatFormatting.DARK_GRAY));
        linhas.add(Component.literal("Um poder antigo dorme nela...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
