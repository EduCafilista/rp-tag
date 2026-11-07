package dev.rptag;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * (5.17.0) FITA CASSETE — o item que "fala".
 *
 * <p>Pedido do dono: <i>"fita cassete que toda vez que o player apertar botão direito aparece uma
 * frase, que o adm vai escolher — ele vai configurar qual vai ser a mensagem antes, e entregar
 * para o player; e essa mensagem aparece como o avisar FITA"</i>.
 *
 * <p>O ADMIN grava a fala com {@code /rp admin fita dar <jogador> <texto>} (ou reescreve a de um
 * item que já existe com {@code /rp admin fita gravar <texto>} na mão). A fala fica
 * <b>gravada no próprio item</b> (custom_data {@code FitaFala}) — então ela sobrevive a baú,
 * morte e troca de dono; quem receber a fita ouve a mesma frase.
 *
 * <p>No clique direito a frase aparece no <b>estilo FITA</b> (37) do avisar: fita cassete girando,
 * chiado, as palavras surgindo como se estivessem sendo reproduzidas.
 */
public final class FitaItem extends Item {

    /** a fala gravada na fita (custom_data). */
    public static final String TAG_FALA = "FitaFala";
    /** de quem é a fita (custom_data) — só informativo. */
    public static final String TAG_DONO = "FitaDono";
    /** quantas vezes já foi tocada (custom_data) — o dono gosta de saber. */
    public static final String TAG_TOQUES = "FitaToques";

    public FitaItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ leitura
    /** a fala gravada (string vazia = fita virgem). */
    public static String fala(ItemStack stack) {
        CustomData dados = stack.get(DataComponents.CUSTOM_DATA);
        return dados == null ? "" : dados.copyTag().getString(TAG_FALA);
    }

    public static String donoNome(ItemStack stack) {
        CustomData dados = stack.get(DataComponents.CUSTOM_DATA);
        return dados == null ? "" : dados.copyTag().getString(TAG_DONO);
    }

    public static int toques(ItemStack stack) {
        CustomData dados = stack.get(DataComponents.CUSTOM_DATA);
        return dados == null ? 0 : dados.copyTag().getInt(TAG_TOQUES);
    }

    /** grava a fala (e o dono) na fita. */
    public static void gravar(ItemStack stack, String texto, String dono) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.putString(TAG_FALA, texto == null ? "" : texto);
            t.putString(TAG_DONO, dono == null ? "" : dono);
        });
    }

    private static void contarToque(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t ->
                t.putInt(TAG_TOQUES, t.getInt(TAG_TOQUES) + 1));
    }

    // --------------------------------------------------------------------- uso
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        try {
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.success(stack);
            }
            String texto = fala(stack);
            // o chiado da fita SEMPRE toca (mesmo virgem) — é o que faz o item parecer vivo
            level.playSound(null, sp.blockPosition(), RPTagMod.SOM_FITA.get(),
                    SoundSource.PLAYERS, 1.0F, 0.95F + (sp.getRandom().nextFloat() - 0.5F) * 0.1F);
            if (texto.isBlank()) {
                sp.displayClientMessage(Component.literal(
                        "\u00a78A fita está virgem.\u00a7r \u00a77Nada foi gravado nela \u2014 "
                        + "só o chiado."), true);
                return InteractionResultHolder.success(stack);
            }
            contarToque(stack);
            // o MESMO pacote de tela cheia dos avisos, no estilo FITA (37)
            String cor = translateAmp(texto);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new ScreenTextPayload(cor, 8, ESTILO_FITA));
            RPTagMod.LOGGER.info("RP Tag: fita tocada por {}: {}", sp.getGameProfile().getName(), texto);
            return InteractionResultHolder.success(stack);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: fita falhou no clique", t);
            return InteractionResultHolder.success(stack);
        }
    }

    /** (5.17.0) o estilo FITA do avisar em tela cheia (o mesmo número no servidor e no cliente). */
    public static final int ESTILO_FITA = 37;

    /** o texto pode vir com {@code &} do admin: vira {@code §} (o mesmo do /rp admin avisar). */
    public static String translateAmp(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '&' && i + 1 < texto.length()
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(texto.charAt(i + 1)) >= 0) {
                sb.append('\u00a7');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- tooltip
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        String f = fala(stack);
        tooltip.add(Component.literal("Fita cassete").withStyle(ChatFormatting.GRAY));
        if (f.isBlank()) {
            tooltip.add(Component.literal("Virgem \u2014 nada gravado").withStyle(ChatFormatting.DARK_GRAY,
                    ChatFormatting.ITALIC));
            tooltip.add(Component.literal("Um ADM grava com /rp admin fita").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("\"" + f + "\"").withStyle(ChatFormatting.RED,
                    ChatFormatting.ITALIC));
            String d = donoNome(stack);
            if (!d.isBlank()) {
                tooltip.add(Component.literal("gravada por " + d).withStyle(ChatFormatting.DARK_GRAY));
            }
            int t = toques(stack);
            if (t > 0) {
                tooltip.add(Component.literal("tocada " + t + (t == 1 ? " vez" : " vezes"))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.literal("Clique direito: ouvir").withStyle(ChatFormatting.DARK_GRAY));
    }
}
