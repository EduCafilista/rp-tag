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

import java.util.Collection;
import java.util.List;

/**
 * /macaco                 -> vira Macaco (você)
 * /macaco <jogadores>     -> vira Macaco (aceita @a, @p, nomes...)
 * /macaco off [jogadores] -> volta ao normal
 * Precisa de OP (nível 2).
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class MacacoCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("macaco")
                .requires(s -> s.hasPermission(2))
                .executes(c -> run(c, List.of(c.getSource().getPlayerOrException()), true))
                .then(Commands.literal("off")
                        .executes(c -> run(c, List.of(c.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("alvos", EntityArgument.players())
                                .executes(c -> run(c, EntityArgument.getPlayers(c, "alvos"), false))))
                .then(Commands.argument("alvos", EntityArgument.players())
                        .executes(c -> run(c, EntityArgument.getPlayers(c, "alvos"), true))));
    }

    private static int run(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets, boolean on) {
        for (ServerPlayer p : targets) {
            if (on) MacacoForm.apply(p); else MacacoForm.remove(p);
        }
        int n = targets.size();
        c.getSource().sendSuccess(() -> Component.literal(
                (on ? "Forma Macaco ativada em " : "Forma Macaco removida de ") + n + " jogador(es)"), true);
        return n;
    }

    private MacacoCommand() {}
}
