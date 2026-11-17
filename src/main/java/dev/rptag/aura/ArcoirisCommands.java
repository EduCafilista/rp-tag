package dev.rptag.aura;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * (5.69.0) {@code /arcoiris} — a aura arco-iris (zip do pedido 124).
 *
 * <pre>
 * /arcoiris                liga em voce      (SO ADM)
 * /arcoiris off            desliga           (SO ADM)
 * /arcoiris &lt;jogador&gt;       liga em outro     (SO ADM)
 * /arcoiris off &lt;jogador&gt;  desliga outro     (SO ADM)
 * </pre>
 *
 * <p>O dono: <i>"somentes os adms podem ativar esses poderes"</i> — o comando inteiro exige
 * nivel 2, igual o {@code /radiacao} e o {@code /iluminacao}.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class ArcoirisCommands {

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("arcoiris")
                .requires(s -> s.hasPermission(2))
                .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), true))
                .then(Commands.literal("off")
                        .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("alvo", EntityArgument.player())
                                .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), false))))
                .then(Commands.argument("alvo", EntityArgument.player())
                        .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), true))));
    }

    private static int aplicar(CommandContext<CommandSourceStack> c, ServerPlayer alvo, boolean ligar) {
        alvo.setData(AuraAnexos.AURA.get(), ligar ? AuraEstilo.ARCOIRIS.index() : 0);
        String quem = alvo.getGameProfile().getName();
        String msg = ligar
                ? "\u00a7d\u00a7lAura ARCO-IRIS ligada em \u00a7f" + quem
                : "\u00a77Aura arco-iris desligada em \u00a7f" + quem + "\u00a77.";
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private ArcoirisCommands() {}
}
