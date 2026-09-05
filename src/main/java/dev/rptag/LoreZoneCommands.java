package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lorezone} — comandos de administrador (permissao 2) para
 * criar regioes que contam a historia do mundo.
 *
 * <ul>
 *   <li>{@code /lorezone criar <id> <raio> <titulo>} — cria na sua posicao</li>
 *   <li>{@code /lorezone texto <id> <texto>} — lore exibida no subtitulo/chat</li>
 *   <li>{@code /lorezone som <id> <som>} — ex.: minecraft:ambient.cave</li>
 *   <li>{@code /lorezone remover <id>} — apaga a zona</li>
 *   <li>{@code /lorezone listar} — lista as zonas</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class LoreZoneCommands {

    private LoreZoneCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> ZONE_IDS = (ctx, builder) -> {
        var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(data.all().stream().map(LoreZones.Zone::id), builder);
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("lorezone")
                .requires(src -> src.hasPermission(2));

        // /lorezone criar <id> <raio> <titulo>
        root.then(Commands.literal("criar")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("raio", DoubleArgumentType.doubleArg(2, 500))
                                .then(Commands.argument("titulo", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String id = StringArgumentType.getString(ctx, "id");
                                            double raio = DoubleArgumentType.getDouble(ctx, "raio");
                                            String titulo = StringArgumentType.getString(ctx, "titulo");
                                            LoreZones.LoreZonesData.get(player.server).put(new LoreZones.Zone(
                                                    id, player.level().dimension(), player.position(),
                                                    raio, titulo, "", ""));
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Zona '" + id + "' criada aqui! Raio " + raio
                                                            + " blocos. Add texto com /lorezone texto " + id + " ..."),
                                                    true);
                                            return Command.SINGLE_SUCCESS;
                                        })))));

        // /lorezone texto <id> <texto>
        root.then(Commands.literal("texto")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String texto = StringArgumentType.getString(ctx, "texto");
                                    var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
                                    LoreZones.Zone z = data.get(id);
                                    if (z == null) {
                                        ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                                        return 0;
                                    }
                                    data.put(new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(), z.title(), texto, z.sound()));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Texto da zona '" + id + "' atualizado."), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // /lorezone som <id> <som>
        root.then(Commands.literal("som")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .then(Commands.argument("som", StringArgumentType.string())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String som = StringArgumentType.getString(ctx, "som");
                                    var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
                                    LoreZones.Zone z = data.get(id);
                                    if (z == null) {
                                        ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                                        return 0;
                                    }
                                    data.put(new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(), z.title(), z.text(), som));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Som da zona '" + id + "' definido: " + som), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // /lorezone remover <id>
        root.then(Commands.literal("remover")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            boolean ok = LoreZones.LoreZonesData.get(ctx.getSource().getServer()).remove(id);
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Zona '" + id + "' removida."), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                            }
                            return Command.SINGLE_SUCCESS;
                        })));

        // /lorezone listar
        root.then(Commands.literal("listar").executes(ctx -> {
            var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
            var all = data.all();
            if (all.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Nenhuma lore zone criada ainda.").withStyle(ChatFormatting.GRAY), false);
                return Command.SINGLE_SUCCESS;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Lore zones (" + all.size() + "):").withStyle(ChatFormatting.GOLD), false);
            for (LoreZones.Zone z : all) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  • " + z.id() + " [" + z.dimension().location() + "] r=" + (int) z.radius()
                                + " — " + z.title()).withStyle(ChatFormatting.GRAY), false);
            }
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }
}
