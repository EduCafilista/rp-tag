package dev.rptag;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/**
 * (5.37.0, reescrito na 5.41.0) <b>UMBRELLA DE SOMBRA</b> — {@code rptag:umbrella_sombra}.
 *
 * <p>Pedido do dono: <i>"tenho que fazer um umbrella 3d, para os vampiros nao tomar dano do
 * sol, procure um modelo top para ser usado"</i>.
 *
 * <h2>O estado aberto/fechado mora no PRÓPRIO ITEM (5.41.0)</h2>
 *
 * <p>Até a 5.40.0 o guarda-chuva só era considerado aberto enquanto estava "em uso"
 * (o item "em uso"), e o modelo aberto era escolhido por um predicado registrado no
 * cliente. Deu errado duas vezes: a primeira porque o predicado não existia, a segunda porque
 * segurar o botão direito com a animação de arco é frágil (qualquer coisa que cancele o uso
 * fecha o guarda-chuva). O dono resumiu: <i>"não tem a animação dele aberto, não fica aberto"</i>.
 *
 * <p>Agora é <b>clicar e ficar aberto</b>, e o estado viaja num componente que o Minecraft já
 * sincroniza sozinho — sem predicado, sem registrar nada, sem depender de animação:
 *
 * <ul>
 *   <li>{@code minecraft:custom_model_data} = <b>0..7</b> → guarda-chuva <b>FECHADO</b>
 *       (o número é a cor);</li>
 *   <li>{@code minecraft:custom_model_data} = <b>8..15</b> → guarda-chuva <b>ABERTO</b>
 *       (o número é a cor + 8).</li>
 * </ul>
 *
 * <p>Como {@code custom_model_data} é um predicado vanilla, o jogo troca o modelo 3D na mão
 * <b>sem nenhum código de cliente</b> — é o mesmo mecanismo que a bússola e o arco usam.
 *
 * <h2>Como funciona</h2>
 * <ul>
 *   <li><b>clique direito</b>: abre. Clique direito de novo: fecha. Agachado fecha na hora;</li>
 *   <li>aberto, o <b>sol não queima</b> o vampiro (ver {@link VampiroSistema#protegido});</li>
 *   <li>a abertura dura {@value #ABERTURA_SEGUNDOS} segundos e fecha sozinha;</li>
 *   <li>a cúpula é o próprio modelo .obj do item na mão (a camada antiga saiu na 5.43.3).</li>
 * </ul>
 *
 * <p>O item <b>não é encantado</b> (o brilho saiu a pedido do dono: <i>"ele não precisa ficar
 * encantado"</i>) — a cor e a folha de metal é que dão o brilho.
 */
public final class VarinhaSombraItem extends Item {

    /** quantas cores existem (o mesmo número de {@code arte/gerar_umbrella.py}). */
    public static final int CORES = 8;

    /** quanto tempo uma abertura dura (segundos) antes de "cansar" e fechar sozinha. */
    public static final int ABERTURA_SEGUNDOS = 65;
    /** recarga depois de fechar (segundos) — guarda-chuva cansa o braço. */
    public static final int RECARGA_SEGUNDOS = 5;

    /** a chave do NBT (dentro do {@code custom_data} vanilla) onde fica o fim da abertura. */
    public static final String NBT_EXPIRA = "rptag_expira";

    public VarinhaSombraItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------ estado

    /** o {@code custom_model_data} do stack (0 se não tiver). */
    public static int cru(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
    }

    /** a cor (0..7) — vale tanto fechado quanto aberto. */
    public static int corDo(ItemStack stack) {
        return Math.floorMod(cru(stack), CORES);
    }

    /** o guarda-chuva está ABERTO? (é o mesmo estado que troca o modelo e barra o sol) */
    public static boolean aberto(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof VarinhaSombraItem
                && cru(stack) >= CORES;
    }

    /** põe o estado de volta (fechado, na mesma cor). */
    private static void porEstado(ItemStack stack, int cor, boolean aberto) {
        int valor = Math.floorMod(cor, CORES) + (aberto ? CORES : 0);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(valor));
    }

    /** ABRE (ou renova a abertura). Devolve quanto tempo fica aberto. */
    public static int abrir(ItemStack stack, Level lv) {
        porEstado(stack, corDo(stack), true);
        long fim = lv.getGameTime() + (long) ABERTURA_SEGUNDOS * 20L;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(NBT_EXPIRA, fim));
        return ABERTURA_SEGUNDOS;
    }

    /** FECHA. */
    public static void fechar(ItemStack stack) {
        porEstado(stack, corDo(stack), false);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(NBT_EXPIRA));
    }

    /** quando a abertura acaba (game time), ou 0 se não está aberto. */
    public static long fimDaAbertura(ItemStack stack) {
        if (!aberto(stack)) {
            return 0L;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getLong(NBT_EXPIRA);
    }

    /** quantos segundos ainda cabem de guarda-chuva aberto. */
    public static int segundosRestantes(ItemStack stack, long gameTime) {
        long fim = fimDaAbertura(stack);
        if (fim <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, (fim - gameTime) / 20L);
    }

    // ------------------------------------------------------------------ uso

    @Override
    public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand mao) {
        ItemStack stack = p.getItemInHand(mao);
        boolean jaAberto = aberto(stack);
        // agachado: sempre FECHA, mesmo que o botão direito também feche — é a saída de
        // emergência (o dono pediu "agachado fecha na hora").
        if (jaAberto || p.isShiftKeyDown()) {
            fechar(stack);
            if (p instanceof ServerPlayer sp) {
                sp.getCooldowns().addCooldown(this, RECARGA_SEGUNDOS * 20);
                lv.playSound(null, sp.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS,
                        0.32F, 1.6F);
                sp.displayClientMessage(Component.literal("\u00a78guarda-chuva fechado \u00a77(recarrega em "
                        + RECARGA_SEGUNDOS + " s)").withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return InteractionResultHolder.success(stack);
        }
        if (p.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        int segundos = abrir(stack, lv);
        p.awardStat(Stats.ITEM_USED.get(this));
        if (p instanceof ServerPlayer sp) {
            lv.playSound(null, sp.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.9F, 1.55F);
            lv.playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS, 0.7F, 1.2F);
            sp.displayClientMessage(Component.literal("\u00a77guarda-chuva \u00a7dABERTO \u00a78por "
                    + segundos + " s").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * Cuida do relógio da abertura. Roda no servidor a cada tick; quando o tempo acaba, fecha.
     * Fechar aqui (e não só no clique) é o que faz a cúpula sumir e o modelo voltar sozinho.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level lv, Entity dono, int slot, boolean naMao) {
        if (lv.isClientSide || !aberto(stack)) {
            return;
        }
        try {
            long fim = fimDaAbertura(stack);
            // sem marcação (item veio de outro mundo/versão): normaliza fechado
            if (fim <= 0L || lv.getGameTime() > fim) {
                fechar(stack);
                if (dono instanceof ServerPlayer sp) {
                    lv.playSound(null, sp.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS,
                            0.28F, 1.7F);
                }
            }
        } catch (Throwable t) {
            fechar(stack);
        }
    }

    // ------------------------------------------------------------------ ficha

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> linhas, TooltipFlag flag) {
        boolean estaAberto = aberto(stack);
        linhas.add(Component.literal(estaAberto ? "\u00a7dABERTO \u00a78— o sol não te queima"
                : "\u00a78fechado — clique direito pra abrir").withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Clique direito abre e fecha \u00b7 agachado fecha na hora")
                .withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("\u00a78Dura " + ABERTURA_SEGUNDOS + " s aberto \u00b7 recarga de "
                + RECARGA_SEGUNDOS + " s").withStyle(ChatFormatting.DARK_GRAY));
        linhas.add(Component.literal("Umbrella para VAMPIRO — 8 cores, feitas na mão.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
