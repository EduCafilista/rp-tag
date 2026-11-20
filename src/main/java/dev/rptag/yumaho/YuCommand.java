package dev.rptag.yumaho;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 * /yumaho enable|disable [jogador]        (OP)  libera/remove as habilidades
 * /yumaho power <0-100> [jogador]               define o seu poder (OP para outros)
 * /yumaho white enter|exit [jogador]      (OP)  Dimensão Branca
 */
@EventBusSubscriber(modid = YuMaho.MODID)
public final class YuCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("yumaho")
                .then(Commands.literal("enable").requires(s -> s.hasPermission(2))
                        .executes(c -> enable(c, c.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("alvo", EntityArgument.player())
                                .executes(c -> enable(c, EntityArgument.getPlayer(c, "alvo"), true))))
                .then(Commands.literal("disable").requires(s -> s.hasPermission(2))
                        .executes(c -> enable(c, c.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("alvo", EntityArgument.player())
                                .executes(c -> enable(c, EntityArgument.getPlayer(c, "alvo"), false))))
                .then(Commands.literal("power")
                        .then(Commands.argument("pct", IntegerArgumentType.integer(0, 100))
                                .executes(c -> power(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("alvo", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                        .executes(c -> power(c, EntityArgument.getPlayer(c, "alvo"))))))
                .then(Commands.literal("white").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("enter")
                                .executes(c -> white(c, c.getSource().getPlayerOrException(), true))
                                .then(Commands.argument("alvo", EntityArgument.player())
                                        .executes(c -> white(c, EntityArgument.getPlayer(c, "alvo"), true))))
                        .then(Commands.literal("exit")
                                .executes(c -> white(c, c.getSource().getPlayerOrException(), false))
                                .then(Commands.argument("alvo", EntityArgument.player())
                                        .executes(c -> white(c, EntityArgument.getPlayer(c, "alvo"), false))))));
    }

    private static int enable(CommandContext<CommandSourceStack> c, ServerPlayer t, boolean on) {
        t.setData(YuAttachments.ENABLED.get(), on);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "msg.yumaho.enabled" : "msg.yumaho.disabled", t.getName()), true);
        return 1;
    }

    private static int power(CommandContext<CommandSourceStack> c, ServerPlayer t) {
        if (!YuPower.enabled(t)) { c.getSource().sendFailure(Component.translatable("msg.yumaho.notenabled", t.getName())); return 0; }
        int v = IntegerArgumentType.getInteger(c, "pct");
        YuPower.set(t, v);
        c.getSource().sendSuccess(() -> Component.translatable("msg.yumaho.power", t.getName(), v), false);
        return 1;
    }

    private static int white(CommandContext<CommandSourceStack> c, ServerPlayer t, boolean enter) {
        boolean ok = enter ? YuWhite.enter(t) : YuWhite.exit(t);
        if (!ok) { c.getSource().sendFailure(Component.translatable(enter ? "msg.yumaho.white.already" : "msg.yumaho.white.notin", t.getName())); return 0; }
        c.getSource().sendSuccess(() -> Component.translatable(enter ? "msg.yumaho.white.entered" : "msg.yumaho.white.exited", t.getName()), true);
        return 1;
    }

    private YuCommand() {}
}
