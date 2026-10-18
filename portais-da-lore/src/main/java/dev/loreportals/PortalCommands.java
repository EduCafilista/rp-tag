package dev.loreportals;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Comandos do mod (raiz /portal):
 * <pre>
 *   /portal ajuda        — lista
 *   /portal info         — o que tem no bloco aonde voce esta
 *   /portal listar       — todos os portais do mundo
 *   /portal ir <n>       — viajar direto pra um portal da lista
 *   /portal desvincular  — quebra o vinculo do nucleo onde voce esta
 *   /portal apagar <n>   — apaga um registro (portal parou de existir)
 *   /portal contar [jogador] — quantas vezes alguem atravessou
 * </pre>
 * Op (nivel 2) para listar/ir/apagar/contar-de-outro.
 */
public final class PortalCommands {

    private PortalCommands() {
    }

    @EventBusSubscriber(modid = LorePortalsMod.MODID)
    public static final class Registro {
        private Registro() {
        }

        @SubscribeEvent
        public static void onCommands(RegisterCommandsEvent event) {
            registrar(event.getDispatcher());
        }
    }

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("portal")
                .then(Commands.literal("ajuda").executes(ctx -> {
                    CommandSourceStack s = ctx.getSource();
                    s.sendSuccess(() -> Component.literal(
                            "\u00a75\u00bb \u00a7d\u00a7lPortais da Lore"), false);
                    linha(s, "\u00a7d/portal info \u00a77\u2014 o que ha no bloco onde voce esta");
                    linha(s, "\u00a7d/portal listar \u00a77\u2014 todos os portais do mundo");
                    linha(s, "\u00a7d/portal ir <n> \u00a77\u2014 viajar pra um portal da lista");
                    linha(s, "\u00a7d/portal desvincular \u00a77\u2014 quebra o vinculo do nucleo aonde voce esta");
                    linha(s, "\u00a7d/portal apagar <n> \u00a77\u2014 apaga um registro do mundo");
                    linha(s, "\u00a7d/portal contar [jogador] \u00a77\u2014 quantas viagens ja fez");
                    linha(s, "\u00a77Cristal: \u00a7fuse em 2 nucleos pra conectar \u00a78| \u00a77shift+use desvincula");
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    PortalBlockEntity be = nucleoAqui(p);
                    if (be == null) {
                        p.sendSystemMessage(Component.literal(
                                "\u00a7cNao ha nucleo de portal no bloco aonde voce esta."));
                        return 0;
                    }
                    PortalData.Portal dado = PortalData.get(p.serverLevel().getServer()).get(be.chave());
                    p.sendSystemMessage(Component.literal("\u00a75\u00bb \u00a7d" + be.chave()));
                    if (dado == null) {
                        p.sendSystemMessage(Component.literal(
                                "\u00a77Estado: \u00a7fNao registrado (portal solto/dormindo)"));
                        return 1;
                    }
                    String estado = dado.linkedTo() != null ? "\u00a7aCONECTADO \u00a77\u2192 \u00a7d" + dado.linkedTo()
                            : (dado.isMolde() ? "\u00a7fAtivo (molde, sem vinculo)" : "\u00a7fRegistrado, sem vinculo");
                    p.sendSystemMessage(Component.literal("\u00a77Tipo: \u00a7f"
                            + (dado.isMolde() ? "MOLDE (anel de molduras)" : "PAR (conectado por cristal)")));
                    p.sendSystemMessage(Component.literal("\u00a77Estado: " + estado));
                    p.sendSystemMessage(Component.literal("\u00a77Viagens: \u00a7f" + dado.viagens()
                            + (dado.ultimoViajante() != null ? " \u00a77(\u00faltimo: " + dado.ultimoViajante() + ")" : "")));
                    return 1;
                }))
                .then(Commands.literal("listar").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    var data = PortalData.get(ctx.getSource().getServer());
                    var todos = data.todos();
                    if (todos.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "\u00a75\u00bb \u00a77Nenhum portal registrado no mundo."), false);
                        return 0;
                    }
                    int i = 1;
                    for (PortalData.Portal p : todos) {
                        String vinc = p.linkedTo() == null ? "\u00a77\u2014" : "\u00a7d\u2192 " + p.linkedTo();
                        final int numero = i;
                        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                "\u00a75[%d] \u00a7d%s \u00a77(%s) %s", numero, p.key(),
                                p.isMolde() ? "molde" : "par", vinc)), false);
                        i++;
                    }
                    return 1;
                }))
                .then(Commands.literal("ir")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                    var todos = PortalData.get(ctx.getSource().getServer()).todos();
                                    if (n > todos.size()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "\u00a7cNao existe portal #" + n + " (tem " + todos.size() + ")."));
                                        return 0;
                                    }
                                    PortalData.Portal p = java.util.List.copyOf(todos).get(n - 1);
                                    ServerPlayer pl = ctx.getSource().getPlayerOrException();
                                    var alvo = LorePortalsMod.nivel(ctx.getSource().getServer(), p.dim());
                                    if (alvo == null) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "\u00a7cDimensao nao encontrada: " + p.dim()));
                                        return 0;
                                    }
                                    pl.teleportTo(alvo, p.x() + 0.5, p.y() + 1.0, p.z() + 0.5,
                                            pl.getYRot(), pl.getXRot());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "\u00a75\u00bb \u00a7dChegou em " + p.key()), false);
                                    return 1;
                                })))
                .then(Commands.literal("desvincular").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    PortalBlockEntity be = nucleoAqui(p);
                    if (be == null) {
                        p.sendSystemMessage(Component.literal(
                                "\u00a7cFique EM CIMA do nucleo e use de novo."));
                        return 0;
                    }
                    if (!be.isLinked()) {
                        p.sendSystemMessage(Component.literal(
                                "\u00a77Este portal nao tem vinculo."));
                        return 0;
                    }
                    PortalData.get(p.serverLevel().getServer()).desvincular(be.chave());
                    be.atualizarDoVinculo();
                    p.sendSystemMessage(Component.literal(
                            "\u00a75\u00bb \u00a7dVinculo quebrado."));
                    return 1;
                }))
                .then(Commands.literal("apagar")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                    var data = PortalData.get(ctx.getSource().getServer());
                                    var todos = data.todos();
                                    if (n > todos.size()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "\u00a7cNao existe portal #" + n));
                                        return 0;
                                    }
                                    PortalData.Portal morto = java.util.List.copyOf(todos).get(n - 1);
                                    data.remover(morto.key());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "\u00a75\u00bb \u00a7cRegistro apagado: " + morto.key()), true);
                                    return 1;
                                })))
                .then(Commands.literal("contar")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var data = PortalData.get(ctx.getSource().getServer());
                            long total = data.todos().stream()
                                    .mapToLong(PortalData.Portal::viagens).sum();
                            p.sendSystemMessage(Component.literal(
                                    "\u00a75\u00bb \u00a7dVoce ja atravessou portais \u00a7l" + data.viagensDe(p.getUUID().toString())
                                            + "\u00a7r\u00a7d vez(es). O mundo ja somou \u00a7f" + total + "\u00a77 viagens."));
                            return 1;
                        })
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                                    var data = PortalData.get(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "\u00a75\u00bb \u00a7d" + alvo.getName().getString()
                                                    + " atravessou \u00a7l" + data.viagensDe(alvo.getUUID().toString())
                                                    + "\u00a7r\u00a7d vez(es)."), false);
                                    return 1;
                                }))));
    }

    private static PortalBlockEntity nucleoAqui(ServerPlayer p) {
        return p.serverLevel().getBlockEntity(p.blockPosition()) instanceof PortalBlockEntity be ? be : null;
    }

    private static void linha(CommandSourceStack s, String texto) {
        s.sendSuccess(() -> Component.literal("  " + texto), false);
    }
}
