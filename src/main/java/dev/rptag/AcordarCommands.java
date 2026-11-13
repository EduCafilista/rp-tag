package dev.rptag;

import java.util.Collection;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * (5.42.0) <b>/acordar — a Axion na tela de QUEM VOCÊ ESCOLHER.</b>
 *
 * <p>O dono: <i>"no comando acordar, tenha a opção de selecionar o acordar eduujgaa por
 * exemplo, para saber em qual player vai acordar, se não vai aparecer na tela de todo
 * mundo"</i>.
 *
 * <p>Antes só existia {@code /rp admin axion acordar}, que valia pro servidor inteiro. Agora
 * tem o atalho curto:
 * <pre>
 *   /acordar &lt;jogadores&gt;    a Axion aparece SÓ na tela de quem foi citado
 *   /acordar                aparece pra quem mandou o comando
 *   /dormir &lt;jogadores&gt;     ela aparece, mas dormindo
 *   /guardar &lt;jogadores&gt;    tira ela da tela
 * </pre>
 *
 * <p>Os nomes aceitam seletor do próprio Minecraft ({@code @a}, {@code @p}, vários separados
 * por vírgula), então dá pra acordar a sala toda quando for de propósito.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class AcordarCommands {

    private AcordarCommands() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("acordar")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> mandar(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()),
                        AxionPayload.ACORDADA, "acordada"))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(ctx -> mandar(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "jogadores"),
                                AxionPayload.ACORDADA, "acordada"))));

        event.getDispatcher().register(Commands.literal("dormir")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(ctx -> mandar(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "jogadores"),
                                AxionPayload.DORMINDO, "dormindo"))));

        event.getDispatcher().register(Commands.literal("guardar")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(ctx -> mandar(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "jogadores"),
                                AxionPayload.ESCONDIDA, "fora da tela"))));
    }

    private static int mandar(net.minecraft.commands.CommandSourceStack src,
            Collection<ServerPlayer> alvos, int estado, String rotulo) {
        try {
            if (alvos == null || alvos.isEmpty()) {
                src.sendFailure(Component.literal("\u00a7cEscolha pelo menos um jogador."));
                return 0;
            }
            float escala = RPWorldData.get(src.getServer()).axionEscala();
            for (ServerPlayer alvo : alvos) {
                RPCommands.marcarAxionSoPara(alvo);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(alvo,
                        new AxionPayload(estado, escala, 0.995F, 0.995F));
            }
            String quem = String.join(", ",
                    alvos.stream().map(p -> p.getName().getString()).toList());
            src.sendSuccess(() -> Component.literal(
                            "\u00a7aAxion " + rotulo + " \u00a77s\u00f3 na tela de \u00a7f" + quem)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("\u00a7cA Axion n\u00e3o p\u00f4de aparecer: "
                    + t.getMessage()));
            return 0;
        }
    }
}
