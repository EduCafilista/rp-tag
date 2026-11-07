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
 * (5.17.0) NÚCLEO DE ENERGIA VIAL — item <b>só de lore</b>, que se GASTA com o tempo.
 *
 * <p>Pedido do dono: <i>"serve apenas para lore, precisa se gastar com o tempo, muito lento, 1% a
 * cada 5 dias do mine só de ficar no inventário; se usar com o botão direito ele ativa e ele perde
 * 10% a cada 5 dias; eu também preciso que quando ele 'quebrar' ele não suma, vire uma versão
 * quebrada"</i>.
 *
 * <p>Como funciona (tudo gravado no próprio item, então sobrevive a morte, baú e troca de mão):
 * <ul>
 *   <li>{@code VialPct} — a carga, 0 a 100;</li>
 *   <li>{@code VialAtivo} — 1 quando está ativado;</li>
 *   <li>{@code VialUltimo} — o dia do mundo em que a carga foi calculada pela última vez.</li>
 * </ul>
 *
 * <p>O desgaste é calculado por <b>dia do mundo</b> ({@code level.getDayTime() / 24000}), nunca por
 * relógio real: servidor parado não gasta, e o jogador não é punido por ficar offline. A conta roda
 * no tick do servidor (uma vez por jogador a cada 2 s, ver {@link #tickJogador}) e também toda vez
 * que o jogador clica — assim o número mostrado nunca está atrasado.
 *
 * <p>Chegando a <b>0%</b> o item <b>NÃO some</b>: ele vira {@link RPTagMod#NUCLEO_VIAL_QUEBRADO}
 * no mesmo slot (preservando tudo o que estava gravado nele).
 */
public final class NucleoVialItem extends Item {

    /** carga (0..100). */
    public static final String TAG_PCT = "VialPct";
    /** 1 = ativado. */
    public static final String TAG_ATIVO = "VialAtivo";
    /** dia do mundo da última conta. */
    public static final String TAG_DIA = "VialUltimo";

    /**
     * (5.18.0) O RITMO — o dono: <i>"o nucleo deve durar bem mais, acabou muito rapido"</i>.
     *
     * <p>A conta agora é em <b>dias de jogo por 1%</b>, sem número grande na tela:
     * <ul>
     *   <li><b>parado</b> no inventário/baú: <b>1% a cada 30 dias</b> de jogo (3000 dias até zerar);</li>
     *   <li><b>ativado</b>: <b>1% a cada 10 dias</b> de jogo (1000 dias até zerar).</li>
     * </ul>
     * A carga é guardada com <b>casa decimal</b> ({@code VialCarga}), então um dia de jogo já
     * aparece na conta — antes só mexia no número inteiro e parecia que não gastava.
     */
    public static final int DIAS_POR_PCT_PARADO = 30;
    public static final int DIAS_POR_PCT_ATIVO = 10;
    public static final int CARGA_MAX = 100;

    /** (5.18.0) a carga exata (com decimais) — o {@code VialPct} inteiro continua salvo pra
     * quem já tinha o item e pro tooltip. */
    public static final String TAG_CARGA = "VialCarga";

    public NucleoVialItem(Properties properties) {
        super(properties);
    }

    /**
     * (5.18.0) O NOME NA MARRA — o dono mandou a print do item aparecendo como
     * <b>"item.rptag.nucleo_vial"</b>: o cliente dele não estava achando a tradução no lang
     * (cliente em outro idioma, ou lang não aplicado). Aqui o nome é escrito no código, então
     * aparece certo em QUALQUER idioma, sempre.
     */
    @Override
    public Component getName(ItemStack stack) {
        if (this == RPTagMod.NUCLEO_VIAL_QUEBRADO.get()) {
            return Component.literal("Núcleo de Energia Vial (quebrado)");
        }
        return Component.literal("Núcleo de Energia Vial");
    }

    /** (5.18.0) é a versão QUEBRADA? (não ativa, não brilha, não cobra carga) */
    public static boolean ehQuebrado(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == RPTagMod.NUCLEO_VIAL_QUEBRADO.get();
    }

    // ------------------------------------------------------------------ leitura
    private static CompoundTag tag(ItemStack stack) {
        CustomData dados = stack.get(DataComponents.CUSTOM_DATA);
        return dados == null ? new CompoundTag() : dados.copyTag();
    }

    /** carga em % (100 quando o item nunca foi registrado). */
    public static int carga(ItemStack stack) {
        CompoundTag t = tag(stack);
        return t.contains(TAG_PCT) ? Math.max(0, Math.min(CARGA_MAX, t.getInt(TAG_PCT))) : CARGA_MAX;
    }

    public static boolean ativo(ItemStack stack) {
        return tag(stack).getBoolean(TAG_ATIVO);
    }

    /** dia do mundo da última conta (0 quando nunca foi contado). */
    public static long dia(ItemStack stack) {
        return tag(stack).getLong(TAG_DIA);
    }

    /** (5.18.0) carga com decimais (0..100). Item antigo (só {@code VialPct} inteiro) também vale. */
    public static double cargaExata(ItemStack stack) {
        CompoundTag t = tag(stack);
        if (t.contains(TAG_CARGA)) {
            return Math.max(0.0, Math.min(CARGA_MAX, t.getDouble(TAG_CARGA)));
        }
        return carga(stack);
    }

    // ----------------------------------------------------------- carga/desgaste
    /**
     * Fecha a conta do tempo passado desde a última visita e grava de volta no item.
     *
     * @return true se o item PRECISOU virar a versão quebrada (carga chegou a 0).
     */
    private static boolean atualizar(ItemStack stack, long diaDeHoje) {
        long ultimo = dia(stack);
        double pct = cargaExata(stack);
        boolean on = ativo(stack);
        if (ultimo <= 0L) {                  // primeiro contato: começa a contar AGORA
            gravar(stack, pct, on, diaDeHoje);
            return false;
        }
        long passados = diaDeHoje - ultimo;
        if (passados <= 0L) {
            return false;                    // mesmo dia: nada a fazer
        }
        // (5.18.0) conta exata: 1% a cada 30 dias parado, 1% a cada 10 dias ativado
        double porDia = on ? (1.0 / DIAS_POR_PCT_ATIVO) : (1.0 / DIAS_POR_PCT_PARADO);
        double novo = Math.max(0.0, pct - passados * porDia);
        gravar(stack, novo, on, diaDeHoje);
        return novo <= 0.0;
    }

    private static void gravar(ItemStack stack, double pct, boolean on, long dia) {
        double v = Math.max(0.0, Math.min(CARGA_MAX, pct));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.putInt(TAG_PCT, (int) Math.round(v));
            t.putDouble(TAG_CARGA, v);
            t.putBoolean(TAG_ATIVO, on);
            t.putLong(TAG_DIA, dia);
        });
    }

    /** (compatibilidade) grava com o valor inteiro. */
    private static void gravar(ItemStack stack, int pct, boolean on, long dia) {
        gravar(stack, (double) pct, on, dia);
    }

    /** o usuário/adm pode cravar um valor (usado pelo comando {@code /rp admin vial carga}). */
    public static void definirCarga(ItemStack stack, int pct, boolean on, long dia) {
        gravar(stack, pct, on, dia);
    }

    // ------------------------------------------------------------------- uso
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);   // o servidor decide tudo
        }
        try {
            // (5.18.0) O QUEBRADO NÃO ACENDE — ele já deu o que tinha que dar.
            if (ehQuebrado(stack)) {
                if (player instanceof ServerPlayer q) {
                    q.displayClientMessage(Component.literal(
                            "\u00a78O núcleo está rachado.\u00a7r \u00a77Não acende mais \u2014 "
                            + "só um ADM pode dar outro."), true);
                    q.level().playSound(null, q.blockPosition(), RPTagMod.SOM_VIAL_QUEBRAR.get(),
                            SoundSource.PLAYERS, 0.5F, 0.55F);
                }
                return InteractionResultHolder.fail(stack);
            }
            long dia = level.getDayTime() / 24000L;
            // fecha a conta do passado ANTES de mexer (senão o tempo "sumia" ao clicar)
            if (atualizar(stack, dia) && trocarPorQuebrado(stack, player)) {
                return InteractionResultHolder.success(stack);
            }
            boolean on = !ativo(stack);
            int pct = carga(stack);
            gravar(stack, pct, on, dia);
            if (player instanceof ServerPlayer sp) {
                // som do vial (ativar e desativar têm o mesmo som, em alturas diferentes)
                sp.level().playSound(null, sp.blockPosition(), RPTagMod.SOM_VIAL.get(),
                        SoundSource.PLAYERS, 0.9F, on ? 1.0F : 0.82F);
                // (5.18.0) sem número nenhum na tela — o dono:
                // "quando o player ativar o nucleo vial, nao deve aparecer aquele 10% em 5 dias"
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        on ? "\u00a74\u00a7lO núcleo ACORDOU.\u00a7r \u00a77A energia vial está correndo "
                                + "por dentro dele."
                                : "\u00a78O núcleo voltou a dormir.\u00a7r \u00a77Parado, ele quase "
                                        + "não se gasta."), true);
            }
            return InteractionResultHolder.success(stack);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: núcleo vial falhou no clique", t);
            return InteractionResultHolder.success(stack);
        }
    }

    /**
     * Troca o núcleo (mesmo slot) pela versão quebrada. Chamado quando a carga chega a 0 —
     * pelo clique ou pelo {@link #tickJogador}. Nunca deixa o item sumir.
     */
    public static boolean trocarPorQuebrado(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.getItem() == RPTagMod.NUCLEO_VIAL_QUEBRADO.get()) {
                return false;
            }
            ItemStack quebrado = new ItemStack(RPTagMod.NUCLEO_VIAL_QUEBRADO.get());
            // leva tudo junto: nome, lore, carga (0) e o resto do custom_data
            quebrado.applyComponents(stack.getComponents());
            CustomData.update(DataComponents.CUSTOM_DATA, quebrado, t -> {
                t.putInt(TAG_PCT, 0);
                t.putBoolean(TAG_ATIVO, false);
            });
            int slot = player.getInventory().selected;
            // procura o slot exato do item pra não trocar o item errado
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i) == stack) {
                    slot = i;
                    break;
                }
            }
            player.getInventory().setItem(slot, quebrado);
            if (player instanceof ServerPlayer sp) {
                sp.level().playSound(null, sp.blockPosition(), RPTagMod.SOM_VIAL_QUEBRAR.get(),
                        SoundSource.PLAYERS, 1.0F, 0.7F);
                sp.displayClientMessage(Component.literal(
                        "\u00a78O núcleo se esgotou\u00a7r \u00a77\u2014 ele rachou, mas continua com você. "
                        + "Agora é só uma lembrança do que havia dentro."), false);
                RPTagMod.LOGGER.info("RP Tag: núcleo vial de {} se esgotou (virou a versão quebrada)",
                        sp.getGameProfile().getName());
            }
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: não consegui transformar o núcleo vial em quebrado", t);
            return false;
        }
    }

    // ------------------------------------------------------------- tick global
    /**
     * (5.17.0) passa o tempo em TODOS os núcleos do jogador (inventário inteiro, mão, mochila).
     * Chamado pelo {@link ServerEvents} a cada 2 s: é barato (só mexe quando o DIA do mundo muda,
     * e o dia muda a cada 20 minutos de jogo).
     */
    public static void tickJogador(ServerPlayer p) {
        try {
            var inv = p.getInventory();
            long dia = p.level().getDayTime() / 24000L;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !(st.getItem() instanceof NucleoVialItem) || ehQuebrado(st)) {
                    continue;                        // (5.18.0) quebrado não cobra nem gasta
                }
                if (dia(st) <= 0L) {                 // nunca contado: começa agora
                    gravar(st, carga(st), ativo(st), dia);
                    continue;
                }
                if (dia <= dia(st)) {
                    continue;                        // mesmo dia: nenhuma conta a fazer
                }
                boolean quebrou = atualizar(st, dia);
                p.getInventory().setChanged();
                if (quebrou) {
                    ItemStack quebrado = new ItemStack(RPTagMod.NUCLEO_VIAL_QUEBRADO.get());
                    quebrado.applyComponents(st.getComponents());
                    CustomData.update(DataComponents.CUSTOM_DATA, quebrado, t -> {
                        t.putInt(TAG_PCT, 0);
                        t.putBoolean(TAG_ATIVO, false);
                    });
                    inv.setItem(i, quebrado);
                    p.level().playSound(null, p.blockPosition(), RPTagMod.SOM_VIAL_QUEBRAR.get(),
                            SoundSource.PLAYERS, 1.0F, 0.7F);
                    p.displayClientMessage(Component.literal(
                            "\u00a78O núcleo se esgotou\u00a7r \u00a77\u2014 ele rachou, mas continua "
                            + "com você."), false);
                    RPTagMod.LOGGER.info("RP Tag: núcleo vial de {} se esgotou fora de uso",
                            p.getGameProfile().getName());
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: falha no tick do núcleo vial", t);
        }
    }

    // ---------------------------------------------------------------- tooltip
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        int pct = carga(stack);
        boolean on = ativo(stack);
        tooltip.add(Component.literal("\"Uma fonte de vida, e também de ruína.\"")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        tooltip.add(Component.literal(""));
        if (ehQuebrado(stack)) {
            // (5.18.0) o QUEBRADO: nome e recado certos, e deixa claro que não acende
            tooltip.add(Component.literal("Estado: QUEBRADO").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("A carga acabou. Ele não acende mais.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Só um ADM pode entregar outro.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.literal("Carga: " + pct + "%").withStyle(
                pct > 60 ? ChatFormatting.RED : pct > 25 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        // (5.18.0) o dono não quer número de ritmo na tela: só o estado
        tooltip.add(Component.literal(on ? "Estado: ATIVO" : "Estado: adormecido").withStyle(
                on ? ChatFormatting.RED : ChatFormatting.GRAY));
        tooltip.add(Component.literal("Clique direito: acordar / adormecer")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ativo(stack) && !ehQuebrado(stack);   // (5.18.0) quebrado não brilha
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !ehQuebrado(stack) && carga(stack) < CARGA_MAX;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * carga(stack) / (float) CARGA_MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB01422;          // vermelho vial
    }
}
