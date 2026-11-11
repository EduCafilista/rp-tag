package dev.rptag;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * (5.37.0) <b>UMBRELLA DE SOMBRA</b> — {@code rptag:umbrella_sombra}.
 *
 * <p>Pedido do dono: <i>"tenho que fazer um umbrella 3d, para os vampiros nao tomar dano do
 * sol, procure um modelo top para ser usado"</i>.
 *
 * <h2>Como funciona</h2>
 * <ul>
 *   <li><b>fechado</b> na mão: não protege nada — é só um guarda-chuva fechado;</li>
 *   <li><b>botão direito</b> abre (segurar). Enquanto estiver aberto (até 65 s, que é o tempo
 *       que a barra de uso enche) o <b>sol não queima</b> o vampiro: ver
 *       {@link VampiroSistema#protegido};</li>
 *   <li>agachar (shift) e usar fecha antes da hora;</li>
 *   <li>o aviso na tela diz se está <b>ABERTO (protegido)</b> ou que o sol está mordendo.</li>
 * </ul>
 *
 * <p>A cúpula aberta sobre a cabeça é desenhada pelo cliente em
 * {@code client/CamadaUmbrella} — este item só cuida do estado (aberto/fechado).
 */
public final class VarinhaSombraItem extends Item {

    /** quanto tempo uma abertura dura (segundos) antes de "cansar" e fechar sozinha. */
    public static final int ABERTURA_SEGUNDOS = 65;
    /** quanto tempo leva pra abrir (segundos) — soma ao tempo total de uso. */
    public static final int ABRIR_SEGUNDOS = 1;
    /** recarga depois de fechar (segundos) — guarda-chuva cansa o braço. */
    public static final int RECARGA_SEGUNDOS = 5;

    public VarinhaSombraItem(Properties props) {
        super(props);
    }

    /** quantos ticks a barra de uso fica enchendo (abrir + tempo aberto). */
    public static int ticksDeUso() {
        return (ABRIR_SEGUNDOS + ABERTURA_SEGUNDOS) * 20;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity quem) {
        return ticksDeUso();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;   // segura com as duas mãos: é a pose que combina com guarda-chuva
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand mao) {
        ItemStack stack = p.getItemInHand(mao);
        // agachado: fecha na hora. Como o item só "protege" enquanto está em uso, basta
        // cancelar o uso — e a recarga curta impede o truque de abrir/fechar sem parar.
        if (p.isShiftKeyDown()) {
            if (p instanceof ServerPlayer sp) {
                sp.stopUsingItem();
                sp.getCooldowns().addCooldown(this, RECARGA_SEGUNDOS * 20);
                lv.playSound(null, sp.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.35F, 1.5F);
                sp.displayClientMessage(Component.literal(
                        "\u00a78guarda-chuva fechado \u00a77(recarrega em " + RECARGA_SEGUNDOS + " s)")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return InteractionResultHolder.success(stack);
        }
        if (p.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        p.startUsingItem(mao);
        p.awardStat(Stats.ITEM_USED.get(this));
        if (p instanceof ServerPlayer) {
            lv.playSound(null, p.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.9F, 1.55F);
            lv.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level lv, LivingEntity quem, int restante) {
        if (!(quem instanceof ServerPlayer sp)) {
            return;
        }
        // fechou (soltou o botão ou o tempo acabou): recarga curta e um estalo de tecido
        sp.getCooldowns().addCooldown(this, RECARGA_SEGUNDOS * 20);
        lv.playSound(null, sp.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.35F, 1.5F);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Clique direito: ABRE — só aberto o sol não te queima.")
                .withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("\u00a78Agachado: fecha na hora \u00b7 dura " + ABERTURA_SEGUNDOS
                + " s aberto").withStyle(ChatFormatting.DARK_GRAY));
        linhas.add(Component.literal("Umbrella para VAMPIRO — 8 cores, feitas na mão.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
