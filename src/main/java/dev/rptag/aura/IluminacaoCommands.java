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
 * /iluminacao vazio        liga a aura do VAZIO (tinta preta) em voce
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
                .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), AuraEstilo.LIGADA))
                .then(Commands.literal("vazio")
                        // (5.64.1) o jeito de ACENDER o vazio — e o /iluminacao off apaga tudo
                        .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), 2))
                        .then(Commands.argument("alvo", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), 2))))
                .then(Commands.literal("portoes")
                        // (5.63.0) o poder e SEGREDO: so quem tem ADM (nivel 2) consegue usar
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            PoderPortoes.tentarDisparar(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(c -> aplicar(c, c.getSource().getPlayerOrException(), 0))
                        .then(Commands.argument("alvo", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), 0))))
                .then(Commands.argument("alvo", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> aplicar(c, EntityArgument.getPlayer(c, "alvo"), AuraEstilo.LIGADA))));
    }

    /** 0 = desligada, 1 = iluminacao (ouro), 2 = vazio (tinta preta). */
    private static int aplicar(CommandContext<CommandSourceStack> c, ServerPlayer alvo, int estilo) {
        alvo.setData(AuraAnexos.AURA.get(), estilo);
        String quem = alvo.getGameProfile().getName();
        String msg;
        if (estilo == 0) {
            msg = "\u00a77Aura desligada em \u00a7f" + quem + "\u00a77. Sumiu tudo.";
        } else if (estilo == AuraEstilo.VAZIO.index()) {
            msg = "\u00a78Aura do VAZIO (tinta) ligada em \u00a7f" + quem + "\u00a78.";
        } else {
            msg = "\u00a7eAura de ILUMINACAO ligada em \u00a7f" + quem
                    + "\u00a7e. Poder: tecla \u00a7fG\u00a7e ou \u00a7f/iluminacao portoes\u00a7e.";
        }
        final String m = msg;
        c.getSource().sendSuccess(() -> Component.literal(m), false);
        return 1;
    }

    private IluminacaoCommands() {}
}
