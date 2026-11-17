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
 * (5.73.0) {@code /gravidade} — o personagem GRAVIDADE (zip do pedido 130).
 *
 * <pre>
 * /gravidade                liga em voce      (SO ADM)
 * /gravidade off            desliga           (SO ADM)
 * /gravidade &lt;jogador&gt;       liga em outro     (SO ADM)
 * /gravidade off &lt;jogador&gt;  desliga outro     (SO ADM)
 * </pre>
 *
 * <p>Com o personagem ligado, a tecla <b>H</b> abre a roda de poderes e a <b>J</b> liga/desliga a
 * aura (os poderes continuam). Nada de mensagem de aviso: o dono pediu pra tirar essas frases.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class GravidadeCommands {

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("gravidade")
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
        alvo.setData(AuraAnexos.AURA.get(), ligar ? AuraEstilo.GRAVIDADE.index() : 0);
        if (!ligar) {
            alvo.setData(AuraAnexos.GRAV.get(), 1);      // volta ao estado inicial (so a aura)
        }
        String quem = alvo.getGameProfile().getName();
        String msg = ligar
                ? "\u00a7b\u00a7lGRAVIDADE ligada em \u00a7f" + quem
                        + "\u00a7b. Roda: \u00a7fH\u00a7b. Aura: \u00a7fJ\u00a7b."
                : "\u00a77Gravidade desligada em \u00a7f" + quem + "\u00a77.";
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private GravidadeCommands() {}
}
