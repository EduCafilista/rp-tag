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
 * (5.67.0) {@code /radiacao} — a aura de radiacao.
 *
 * <pre>
 * /radiacao                liga em voce      (SO ADM)
 * /radiacao off            desliga           (SO ADM)
 * /radiacao &lt;jogador&gt;       liga em outro     (SO ADM)
 * /radiacao off &lt;jogador&gt;  desliga outro     (SO ADM)
 * </pre>
 *
 * <p>O dono: <i>"somentes os adms podem ativar esses poderes"</i> — por isso o comando inteiro
 * exige nivel 2, inclusive no proprio jogador. Quem nao e ADM nem ve o comando.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class RadiacaoCommands {

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("radiacao")
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
        alvo.setData(AuraAnexos.AURA.get(), ligar ? AuraEstilo.RADIACAO.index() : 0);
        String quem = alvo.getGameProfile().getName();
        String msg = ligar
                ? "\u00a7bAura de RADIACAO ligada em \u00a7f" + quem + "\u00a7b. Zona de \u00a7f"
                        + (int) Radiacao.RAIO + "\u00a7b blocos."
                : "\u00a77Aura de radiacao desligada em \u00a7f" + quem + "\u00a77.";
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private RadiacaoCommands() {}
}
