package dev.rptag.vampire;

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
 * {@code /vampiro ligar|desligar [jogadores]} (OP) — sincronizado 100% com
 * {@code /rp admin vampiro} e com o atributo {@code rptag:vampiro}.
 */
@EventBusSubscriber(modid = Vampire.MODID)
public final class VampireCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("vampiro").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ligar")
                        .executes(c -> set(c, List.of(c.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("alvo", EntityArgument.players())
                                .executes(c -> set(c, EntityArgument.getPlayers(c, "alvo"), true))))
                .then(Commands.literal("desligar")
                        .executes(c -> set(c, List.of(c.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("alvo", EntityArgument.players())
                                .executes(c -> set(c, EntityArgument.getPlayers(c, "alvo"), false)))));
    }

    private static int set(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> alvos, boolean on) {
        for (ServerPlayer t : alvos) {
            dev.rptag.VampiroSistema.definir(t, on);
            c.getSource().sendSuccess(() -> Component.translatable(on ? "msg.vampire.on" : "msg.vampire.off", t.getName()), true);
        }
        return alvos.size();
    }

    private VampireCommand() {}
}
