package dev.rptag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.Command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /roll [dados] [motivo]} — dados para eventos de RP.
 *
 * <p>Formatos aceitos: {@code /roll} (d20), {@code /roll d100},
 * {@code /roll 2d6+1}, {@code /roll 3d4 atacar goblin}.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class RollCommands {

    private RollCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("roll")
                .executes(ctx -> doRoll(ctx.getSource().getPlayerOrException(), "d20", ""))
                .then(Commands.argument("dados", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> doRoll(ctx.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "dados"), ""))
                        .then(Commands.argument("motivo", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> doRoll(ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "dados"),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "motivo"))))));
    }

    private static int doRoll(ServerPlayer player, String expr, String reason) {
        String normalized = expr.toLowerCase().replace(" ", "");
        ParsedRoll roll = parse(normalized);
        if (roll == null) {
            player.sendSystemMessage(Component.literal(
                    "Formato invalido! Use: /roll, /roll d20, /roll 2d6+1, /roll 3d4 motivo")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        List<Integer> dice = new ArrayList<>(roll.count());
        int total = roll.modifier();
        for (int i = 0; i < roll.count(); i++) {
            int v = ThreadLocalRandom.current().nextInt(1, roll.sides() + 1);
            dice.add(v);
            total += v;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dice.size(); i++) {
            if (i > 0) {
                sb.append("+");
            }
            sb.append("[").append(dice.get(i)).append("]");
        }
        if (roll.modifier() != 0) {
            sb.append(roll.modifier() > 0 ? "+" : "").append(roll.modifier());
        }
        String breakdown = sb.toString();

        Component msg = Component.empty()
                .append(Component.literal("🎲 ").withStyle(ChatFormatting.AQUA))
                .append(RPChat.displayName(player))
                .append(Component.literal(" rolou ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(normalized).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(breakdown + " = " + total).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (reason != null && !reason.isBlank()) {
            msg = msg.copy().append(Component.literal(" (" + reason + ")").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        RPChat.sendLocal(player, RPWorldData.LOCAL_RANGE * 2, msg);
        return Command.SINGLE_SUCCESS;
    }

    record ParsedRoll(int count, int sides, int modifier) {
    }

    /** Aceita: d20, 2d6, 2d6+1, 3d4-2. */
    static ParsedRoll parse(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            int mod = 0;
            String dicePart = s;
            int plus = s.lastIndexOf('+');
            int minus = s.lastIndexOf('-');
            int signIdx = Math.max(plus, minus);
            if (signIdx > 0) {
                dicePart = s.substring(0, signIdx);
                mod = Integer.parseInt(s.substring(signIdx));
            }
            int count = 1;
            int sides;
            int d = dicePart.indexOf('d');
            if (d < 0) {
                return null;
            }
            if (d > 0) {
                count = Integer.parseInt(dicePart.substring(0, d));
            }
            sides = Integer.parseInt(dicePart.substring(d + 1));
            if (count < 1 || count > 20 || sides < 2 || sides > 1000) {
                return null;
            }
            return new ParsedRoll(count, sides, mod);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
