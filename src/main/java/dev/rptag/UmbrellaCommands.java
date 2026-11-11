package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * (5.37.0) <b>/umbrella</b> — entrega e pinta os guarda-chuvas de sombra.
 *
 * <pre>
 * /umbrella dar [jogador] [cor]   entrega um umbrella na cor pedida (0..7)
 * /umbrella cores                 lista as 8 cores e o número de cada uma
 * </pre>
 *
 * <p>Existe pra o ADM testar as cores rápido sem montar receita (as receitas também estão no
 * mod: 3 fios + lã + graveto). A cor viaja no {@code custom_model_data} do item.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class UmbrellaCommands {

    /** nomes das 8 cores, na mesma ordem do gerador de arte ({@code arte/gerar_umbrella.py}). */
    public static final String[] CORES = {
        "sangue", "noite", "juizo", "sombra", "veneno", "luar", "ouro", "rosa"
    };

    private UmbrellaCommands() {
    }

    /** o item com a cor escolhida. */
    public static ItemStack item(int cor) {
        ItemStack stack = new ItemStack(RPTagMod.UMBRELLA.get());
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(Math.floorMod(cor, CORES.length)));
        return stack;
    }

    @SubscribeEvent
    public static void aoRegistrarComandos(RegisterCommandsEvent evento) {
        evento.getDispatcher().register(Commands.literal("umbrella")
                .then(Commands.literal("cores").executes(ctx -> {
                    StringBuilder linhas = new StringBuilder("\u00a78cores do umbrella: ");
                    for (int i = 0; i < CORES.length; i++) {
                        linhas.append("\u00a7f").append(i).append(" \u00a77").append(CORES[i]);
                        if (i + 1 < CORES.length) {
                            linhas.append("\u00a78, ");
                        }
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(linhas.toString()), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("dar")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("cor", IntegerArgumentType.integer(0, CORES.length - 1))
                                        .executes(ctx -> {
                                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                            int cor = IntegerArgumentType.getInteger(ctx, "cor");
                                            alvo.getInventory().placeItemBackInInventory(item(cor));
                                            alvo.level().playSound(null, alvo.blockPosition(),
                                                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
                                            alvo.displayClientMessage(Component.literal(
                                                    "\u00a78Umbrella \u00a7f" + CORES[cor]
                                                    + "\u00a78 na mochila \u2014 clique direito para abrir.")
                                                    .withStyle(ChatFormatting.DARK_GRAY), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "\u00a7aUmbrella \u00a7f" + CORES[cor] + "\u00a7a entregue para "
                                                    + alvo.getName().getString() + "."), false);
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .executes(ctx -> {
                            ServerPlayer quem = ctx.getSource().getPlayerOrException();
                            quem.getInventory().placeItemBackInInventory(item(0));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7aUmbrella \u00a7fsangue\u00a7a entregue (use \u00a7f/umbrella cores"
                                    + "\u00a7a para ver as outras)."), false);
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
