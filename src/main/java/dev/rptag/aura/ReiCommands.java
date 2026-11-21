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
 * (5.78.6) {@code /rei} — A AURA DO REI DE AMARELO (Carcosa), vinda no pacote do Drive.
 *
 * <pre>
 * /rei                liga a aura em voce        (SO ADM, nivel 2)
 * /rei off            desliga em voce
 * /rei &lt;jogador&gt;       liga em outro              (SO ADM)
 * /rei off &lt;jogador&gt;  desliga em outro           (SO ADM)
 * </pre>
 *
 * <p>Aliases: {@code /carcosa} e {@code /king}. Com a aura ligada, a tecla <b>Y</b> abre a roda de
 * 7 habilidades (Decreto, Correntes, Passagem Amarela, A Peca dos Condenados, Olhar de Hastur,
 * Queda de Carcosa e o Reino de Carcosa) e a passiva "Presenca do Rei" age sozinha.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class ReiCommands {

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        for (String nome : new String[]{"rei", "carcosa", "king"}) {
            e.getDispatcher().register(Commands.literal(nome)
                    .requires(s -> s.hasPermission(2))
                    .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), true))
                    .then(Commands.literal("off")
                            .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), false))
                            .then(Commands.argument("alvo", EntityArgument.player())
                                    .requires(s -> s.hasPermission(2))
                                    .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), false))))
                    .then(Commands.argument("alvo", EntityArgument.player())
                            .requires(s -> s.hasPermission(2))
                            .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), true))));
        }
    }

    private static int aplicar(CommandContext<CommandSourceStack> c, ServerPlayer alvo, boolean ligar) {
        alvo.setData(AuraAnexos.AURA.get(), ligar ? AuraEstilo.REI.index() : 0);
        String quem = alvo.getGameProfile().getName();
        String msg = ligar
                ? "\u00a7eO \u00a76\u00a7lREI DE AMARELO\u00a7e tomou \u00a7f" + quem
                        + "\u00a7e. Roda das habilidades: tecla \u00a7fY\u00a7e."
                : "\u00a77A aura do Rei de Amarelo saiu de \u00a7f" + quem + "\u00a77.";
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        if (ligar) {
            alvo.displayClientMessage(Component.literal("\u00a78Carcosa o observa."), true);
        }
        return 1;
    }

    private ReiCommands() {}
}
