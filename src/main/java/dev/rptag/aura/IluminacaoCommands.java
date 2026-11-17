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
 * (5.60.0) /iluminacao — a aura dourada e o poder dela.
 *
 * <pre>
 * /iluminacao              liga a aura em voce
 * /iluminacao off          desliga em voce
 * /iluminacao portoes      usa o poder (os Portoes da Babilonia) — igual a tecla G — SO ADM
 * /iluminacao &lt;jogador&gt;    liga em outro            (OP)
 * /iluminacao off &lt;jogador&gt; desliga em outro        (OP)
 * </pre>
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class IluminacaoCommands {

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("iluminacao")
                .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), true))
                .then(Commands.literal("portoes")
                        // (5.63.0) o poder e SEGREDO: so quem tem ADM (nivel 2) consegue usar
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            PoderPortoes.tentarDisparar(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("alvo", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), false))))
                .then(Commands.argument("alvo", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), true))));
    }

    private static int aplicar(CommandContext<CommandSourceStack> c, ServerPlayer alvo, boolean ligar) {
        alvo.setData(AuraAnexos.AURA.get(), ligar ? AuraEstilo.LIGADA : 0);
        String quem = alvo.getGameProfile().getName();
        String msg = ligar
                ? "\u00a7eAura de ILUMINACAO ligada em \u00a7f" + quem
                        + "\u00a7e. Poder: tecla \u00a7fG\u00a7e ou \u00a7f/iluminacao portoes\u00a7e."
                : "\u00a77Aura de iluminacao desligada em \u00a7f" + quem + "\u00a77.";
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private IluminacaoCommands() {}
}
