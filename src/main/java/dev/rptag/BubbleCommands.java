package dev.rptag;

import com.mojang.brigadier.Command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Personalizacao visual do chat e dos baloes:
 *
 * <ul>
 *   <li>{@code /cor <cor>} — define a SUA cor: pinta sua mensagem no chat e o
 *       fundo do seu balao de fala. Aceita nomes (ex.: ciano), aliases em
 *       portugues e hex {@code #RRGGBB}</li>
 *   <li>{@code /cor off} — volta ao branco</li>
 *   <li>{@code /bolha on|off} — liga/desliga os SEUS baloes</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class BubbleCommands {

    private BubbleCommands() {
    }

    /** Aliases em portugues -> ChatFormatting. */
    private static final java.util.Map<String, ChatFormatting> ALIASES = java.util.Map.ofEntries(
            java.util.Map.entry("ciano", ChatFormatting.AQUA),
            java.util.Map.entry("aqua", ChatFormatting.AQUA),
            java.util.Map.entry("cinza", ChatFormatting.GRAY),
            java.util.Map.entry("cinzaclaro", ChatFormatting.GRAY),
            java.util.Map.entry("cinzaescuro", ChatFormatting.DARK_GRAY),
            java.util.Map.entry("verde", ChatFormatting.GREEN),
            java.util.Map.entry("verdeescuro", ChatFormatting.DARK_GREEN),
            java.util.Map.entry("vermelho", ChatFormatting.RED),
            java.util.Map.entry("vermelhoescuro", ChatFormatting.DARK_RED),
            java.util.Map.entry("amarelo", ChatFormatting.YELLOW),
            java.util.Map.entry("laranja", ChatFormatting.GOLD),
            java.util.Map.entry("dourado", ChatFormatting.GOLD),
            java.util.Map.entry("azul", ChatFormatting.BLUE),
            java.util.Map.entry("azulescuro", ChatFormatting.DARK_BLUE),
            java.util.Map.entry("roxo", ChatFormatting.DARK_PURPLE),
            java.util.Map.entry("rosa", ChatFormatting.LIGHT_PURPLE),
            java.util.Map.entry("lilas", ChatFormatting.LIGHT_PURPLE),
            java.util.Map.entry("branco", ChatFormatting.WHITE),
            java.util.Map.entry("preto", ChatFormatting.BLACK));

    /** @return RGB da cor, ou -1 se invalida. */
    static int parseColor(String input) {
        String s = input.toLowerCase().replace("_", "").replace(" ", "");
        if (s.startsWith("#")) {
            try {
                return Integer.parseInt(s.substring(1), 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        ChatFormatting fmt = ALIASES.get(s);
        if (fmt != null) {
            Integer rgb = fmt.getColor();
            if (rgb != null) {
                return rgb;
            }
        }
        // nome oficial do Minecraft (aqua, dark_gray, light_purple...)
        for (ChatFormatting fmt2 : ChatFormatting.values()) {
            if (fmt2.isColor() && fmt2.getName().replace("_", "").equals(s)) {
                Integer rgb = fmt2.getColor();
                if (rgb != null) {
                    return rgb;
                }
            }
        }
        return -1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // /cor <cor> — cor pessoal (chat + balao)
        event.getDispatcher().register(Commands.literal("cor")
                .then(Commands.argument("cor", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String input = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "cor");
                            if (input.equalsIgnoreCase("off") || input.equalsIgnoreCase("padrao")) {
                                RPWorldData.get(player.server).clearColor(player.getUUID());
                                player.sendSystemMessage(Component.literal("Sua cor voltou ao padrao (branco).")
                                        .withStyle(ChatFormatting.GRAY));
                                return Command.SINGLE_SUCCESS;
                            }
                            int rgb = parseColor(input);
                            if (rgb < 0) {
                                player.sendSystemMessage(Component.literal("Cor invalida! Use um nome (ex.: ciano), "
                                        + "um nome do Minecraft (ex.: dark_aqua) ou hex #RRGGBB.")
                                        .withStyle(ChatFormatting.RED));
                                player.sendSystemMessage(Component.literal(
                                        "Cores: branco preto cinza cinzaescuro vermelho vermelhoescuro laranja/dourado "
                                                + "amarelo verde verdeescuro ciano azul azulescuro roxo rosa/lilas")
                                        .withStyle(ChatFormatting.GRAY));
                                return 0;
                            }
                            RPWorldData.get(player.server).setColor(player.getUUID(), rgb);
                            String hex = String.format("#%06X", rgb);
                            player.sendSystemMessage(Component.literal("Cor definida: ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal("■ " + hex).withStyle(s -> s.withColor(
                                            net.minecraft.network.chat.TextColor.fromRgb(rgb)))));
                            player.sendSystemMessage(Component.literal(
                                    "Sua mensagem no chat e seu balao de fala agora usam essa cor!")
                                    .withStyle(ChatFormatting.GRAY));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /bolha on|off — liga/desliga os proprios baloes
        event.getDispatcher().register(Commands.literal("bolha")
                .then(Commands.literal("on").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleOptOut(player.getUUID(), false);
                    player.sendSystemMessage(Component.literal("Seus baloes de fala estao LIGADOS.")
                            .withStyle(ChatFormatting.GREEN));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    RPWorldData.get(player.server).setBubbleOptOut(player.getUUID(), true);
                    player.sendSystemMessage(Component.literal("Seus baloes de fala estao DESLIGADOS.")
                            .withStyle(ChatFormatting.GRAY));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
