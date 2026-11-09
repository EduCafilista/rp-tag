package dev.rptag;

import java.util.List;

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

/**
 * (5.25.0) AMULETO DO ECLIPSE — {@code rptag:amuleto_eclipse}.
 *
 * <p>Pedido do dono: <i>"ter algum item que se ele usar ou estiver na mao, para ele evitar tomar
 * dano do sol"</i>. É o guarda-chuva do vampiro.
 *
 * <h2>Dois jeitos de proteger</h2>
 * <ul>
 *   <li><b>estar na mão</b> (principal ou secundária): proteção passiva — não precisa apertar
 *       nada, o sol não morde enquanto o amuleto estiver em uma das mãos;</li>
 *   <li><b>usar</b> (clique direito): abre a <b>PROTEÇÃO DO ECLIPSE</b> por 2 minutos (o efeito
 *       {@code rptag:sombra}). Dá pra guardar o amuleto na mochila que a sombra continua — e aí
 *       vem 20 s de recarga antes de poder abrir de novo.</li>
 * </ul>
 *
 * <p>O item não gasta, não quebra e não some: é um amuleto, não uma tocha. Quem decide quem é
 * vampiro é o atributo {@code rptag:vampiro} (ver {@link RPAttributes}); o amuleto serve pra
 * qualquer um, mas só faz diferença pra quem o sol queima.
 */
public final class AmuletoEclipseItem extends Item {

    /** quanto tempo a PROTEÇÃO DO ECLIPSE dura quando o item é usado (segundos). */
    public static final int SOMBRA_SEGUNDOS = 120;
    /** recarga do clique direito (segundos) — o tempo do efeito é maior de propósito. */
    public static final int RECARGA_SEGUNDOS = 20;

    public AmuletoEclipseItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand mao) {
        ItemStack stack = p.getItemInHand(mao);
        if (lv.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(p instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        // já está na sombra? não gasta a recarga à toa
        var atual = sp.getEffect(RPEffects.SOMBRA);
        if (atual != null && atual.getDuration() > (SOMBRA_SEGUNDOS - 10) * 20) {
            sp.displayClientMessage(Component.literal("\u00a7bO eclipse já está com você.")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.success(stack);
        }
        VampiroSistema.sombra(sp, SOMBRA_SEGUNDOS);
        sp.getCooldowns().addCooldown(this, RECARGA_SEGUNDOS * 20);
        lv.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        lv.playSound(null, sp.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        sp.displayClientMessage(Component.literal("\u00a7b\u00a7lSOMBRA DO ECLIPSE \u00a7r\u00a7b— o sol não te vê por 2 minutos.")
                .withStyle(ChatFormatting.AQUA), true);
        if (VampiroSistema.ehVampiro(sp) && VampiroSistema.noSol(sp)) {
            sp.clearFire();
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;   // brilho de item encantado: é um amuleto, tem que parecer especial
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        linhas.add(Component.literal("Segure na mão para o sol não te queimar.")
                .withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Clique direito: PROTEÇÃO DO ECLIPSE (2 min).")
                .withStyle(ChatFormatting.DARK_AQUA));
        linhas.add(Component.literal("Só faz diferença para quem é VAMPIRO.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
