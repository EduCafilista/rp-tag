package dev.rptag.vampire;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** Garrafa de Sangue cheia: o vampiro bebe e recupera sangue; quem não é vampiro passa mal. Devolve a garrafa vazia. */
public class BloodFlaskItem extends Item {
    public BloodFlaskItem(Properties p) { super(p); }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack s, LivingEntity e) { return 32; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide && user instanceof Player p) {
            if (VampireLogic.isVampire(p)) {
                p.getFoodData().eat(10, 0.4f);
            } else {                                                           // humano bebendo sangue
                p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1f, 0.8f);
            if (!p.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack empty = new ItemStack(VampireItems.EMPTY_FLASK.get());
                if (stack.isEmpty()) return empty;
                if (!p.getInventory().add(empty)) p.drop(empty, false);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Sacia 5 gotas de sangue para vampiros.")
                .withStyle(ChatFormatting.RED));
        linhas.add(Component.literal("Humanos passam mal ao beber.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
