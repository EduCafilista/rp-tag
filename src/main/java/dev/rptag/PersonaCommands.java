package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /persona} — identidade RP do jogador.
 *
 * <ul>
 *   <li>{@code /persona criar <nome>} — cria o personagem (e liga o RP)</li>
 *   <li>{@code /persona idade <n>} — define a idade</li>
 *   <li>{@code /persona desc <texto>} — descricao (visivel no tooltip)</li>
 *   <li>{@code /persona ver [jogador]} — mostra a ficha</li>
 *   <li>{@code /persona off} — remove o personagem</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class PersonaCommands {

    private PersonaCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("persona");

        // /persona — mostra a propria ficha
        root.executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            showCard(player, player);
            return Command.SINGLE_SUCCESS;
        });

        // /persona criar <nome>
        root.then(Commands.literal("criar")
                .then(Commands.argument("nome", StringArgumentType.string())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String nome = StringArgumentType.getString(ctx, "nome");
                            if (nome.length() > 32) {
                                player.sendSystemMessage(Component.literal("Nome muito longo (max 32).").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            Persona antiga = ServerEvents.getPersona(player);
                            ServerEvents.setPersona(player, new Persona(nome, antiga.age(), antiga.desc()));
                            if (!ServerEvents.isInRp(player)) {
                                ServerEvents.setState(player, true, false);
                            }
                            player.sendSystemMessage(Component.literal(
                                    "Persona criada! Voce agora se chama " + nome + " " + RPTags.tagText(true))
                                    .withStyle(ChatFormatting.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /persona idade <n>
        root.then(Commands.literal("idade")
                .then(Commands.argument("n", IntegerArgumentType.integer(1, 999))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int idade = IntegerArgumentType.getInteger(ctx, "n");
                            Persona antiga = ServerEvents.getPersona(player);
                            if (antiga.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Crie uma persona primeiro: /persona criar <nome>").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            ServerEvents.setPersona(player, new Persona(antiga.name(), idade, antiga.desc()));
                            player.sendSystemMessage(Component.literal("Idade definida: " + idade).withStyle(ChatFormatting.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /persona desc <texto>
        root.then(Commands.literal("desc")
                .then(Commands.argument("texto", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String desc = StringArgumentType.getString(ctx, "texto");
                            Persona antiga = ServerEvents.getPersona(player);
                            if (antiga.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Crie uma persona primeiro: /persona criar <nome>").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            ServerEvents.setPersona(player, new Persona(antiga.name(), antiga.age(), desc));
                            player.sendSystemMessage(Component.literal("Descricao atualizada! (passe o mouse sobre o nome)").withStyle(ChatFormatting.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })));

        // /persona ver [jogador]
        root.then(Commands.literal("ver")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    showCard(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jogador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                            showCard(player, target);
                            return Command.SINGLE_SUCCESS;
                        })));

        // /persona off
        root.then(Commands.literal("off").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerEvents.setPersona(player, Persona.EMPTY);
            player.sendSystemMessage(Component.literal("Persona removida. Voce voltou a ser ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }

    private static void showCard(ServerPlayer viewer, ServerPlayer target) {
        Persona persona = ServerEvents.getPersona(target);
        viewer.sendSystemMessage(Component.literal("───── Ficha RP ─────").withStyle(ChatFormatting.DARK_GRAY));
        if (persona.isEmpty()) {
            viewer.sendSystemMessage(Component.literal(target.getName().getString() + " nao tem persona criada.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        viewer.sendSystemMessage(Component.literal("Nome: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(persona.name()).withStyle(ChatFormatting.AQUA)));
        if (persona.age() > 0) {
            viewer.sendSystemMessage(Component.literal("Idade: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(persona.age())).withStyle(ChatFormatting.WHITE)));
        }
        if (!persona.desc().isEmpty()) {
            viewer.sendSystemMessage(Component.literal("Descrição: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(persona.desc()).withStyle(ChatFormatting.WHITE)));
        }
    }
}
