package dev.rptag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = RPTagMod.MODID)
public final class TabletService {
    private static final Map<UUID, Integer> LAST_OPEN = new HashMap<>();
    private static final Map<UUID, TabletUpdateBudget> UPDATE_BUDGETS = new HashMap<>();
    private TabletService() {}

    /**
     * (5.13.0) dá o tablet <b>do</b> jogador: o item já nasce com o dono gravado, então um dia ele
     * pode ser roubado e continuar mostrando a ficha de quem é.
     *
     * <p>Só entrega se ele ainda não tiver um tablet <b>dele</b> (ter o de outra pessoa não conta:
     * o ladrão também precisa poder abrir a própria ficha).
     */
    public static boolean tryGiveTablet(ServerPlayer player) {
        RPWorldData data = RPWorldData.get(player.server);
        if (temTabletProprio(player)) {
            return true;
        }
        ItemStack tablet = novoTablet(player);
        if (player.getInventory().add(tablet)) {
            FichaRegistro sheet = data.getFicha(player.getUUID());
            if (!sheet.tabletEntregue()) {
                data.setFicha(player.getUUID(), sheet.comTabletEntregue());
                player.sendSystemMessage(Component.literal("Você recebeu o ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Tablet QuiralOS").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" — clique direito para preencher sua ficha.").withStyle(ChatFormatting.GRAY)));
            }
            return true;
        }
        player.sendSystemMessage(Component.literal("Libere um espaço no inventário e use /tablet para receber seu tablet.").withStyle(ChatFormatting.YELLOW));
        return false;
    }

    /** (5.13.0) o tablet deste jogador (item já ligado a ele). */
    public static ItemStack novoTablet(ServerPlayer dono) {
        ItemStack stack = new ItemStack(RPTagMod.TABLET.get());
        TabletItem.batizar(stack, dono.getUUID(), dono.getGameProfile().getName());
        return stack;
    }

    /** (5.13.0) ele tem um tablet DELE no inventário? */
    public static boolean temTabletProprio(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(RPTagMod.TABLET.get()) && TabletItem.ehDo(stack, player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** (5.13.0) o tablet que pertence a este jogador (ou EMPTY). */
    public static ItemStack tabletDono(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(RPTagMod.TABLET.get()) && TabletItem.ehDo(stack, player.getUUID())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** (5.13.0) procura o tablet de outra pessoa no inventário (o "roubado"). */
    public static ItemStack tabletDeOutro(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(RPTagMod.TABLET.get())) {
                UUID dono = TabletItem.dono(stack);
                if (dono != null && !dono.equals(player.getUUID())) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean personaLocked(ServerPlayer player) {
        if (!RPWorldData.get(player.server).hasConfirmedFicha(player.getUUID())) return false;
        player.sendSystemMessage(Component.literal("Sua ficha já foi confirmada. Solicite liberação a um administrador e faça as alterações pelo tablet.").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** (5.13.0) abre a ficha de QUEM É O DONO do tablet na mão. */
    public static void open(ServerPlayer player, ItemStack stack) {
        int now = player.server.getTickCount();
        Integer last = LAST_OPEN.get(player.getUUID());
        if (last != null && now - last < 5) return;
        LAST_OPEN.put(player.getUUID(), now);
        if (stack == null || !stack.is(RPTagMod.TABLET.get())) {
            // sem tablet na mão (comando/atalho): abre a própria ficha
            send(player, player.getUUID(), player.getGameProfile().getName(), -1, true, false, "");
            return;
        }
        UUID dono = TabletItem.dono(stack);
        if (dono == null) {
            // tablet antigo, sem dono gravado: adota quem abriu (migração dos saves velhos)
            TabletItem.batizar(stack, player.getUUID(), player.getGameProfile().getName());
            dono = player.getUUID();
        }
        String nome = TabletItem.donoNome(stack);
        send(player, dono, nome, -1, true, false, "");
    }

    /** atalho antigo (comando /tablet sem item na mão). */
    public static void open(ServerPlayer player) {
        open(player, player.getMainHandItem());
    }

    /**
     * (5.13.0) manda a ficha do DONO para quem está abrindo.
     *
     * <p>Se quem abre não é o dono, vai <b>só leitura</b> e marcado como {@code deOutro} — o cliente
     * escreve "FICHA DE <NOME>" no topo. É a lore do roubo de tablet: o item é a chave da ficha.
     */
    private static void send(ServerPlayer viewer, UUID donoId, String donoNome, int request, boolean open,
            boolean adminRead, String error) {
        RPWorldData dados = RPWorldData.get(viewer.server);
        FichaRegistro sheet = dados.getFicha(donoId);
        boolean deOutro = !donoId.equals(viewer.getUUID());
        boolean readOnly = adminRead || deOutro || !sheet.podeEditar();
        String nome = (donoNome == null || donoNome.isBlank())
                ? nomeDe(viewer, donoId)
                : donoNome;
        PacketDistributor.sendToPlayer(viewer, new TabletSyncPayload(donoId, request, sheet,
                readOnly, open, false, error, nome, deOutro));
    }

    /** nome do dono (online agora, persona ou o que estiver gravado no mundo). */
    private static String nomeDe(ServerPlayer quem, UUID id) {
        ServerPlayer online = quem.server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        Persona persona = RPWorldData.get(quem.server).getPersona(id);
        return (persona == null || persona.name() == null) ? "" : persona.name();
    }

    public static void handleUpdate(TabletUpdatePayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RPWorldData data = RPWorldData.get(player.server);
            int now = player.server.getTickCount();
            if (!UPDATE_BUDGETS.computeIfAbsent(player.getUUID(), id -> new TabletUpdateBudget()).accept(now)) {
                FichaRegistro sheet = data.getFicha(player.getUUID());
                PacketDistributor.sendToPlayer(player, new TabletSyncPayload(player.getUUID(), packet.requestId(), sheet,
                        !sheet.podeEditar(), false, true,
                        "Aguarde um instante. O salvamento será tentado novamente.",
                        player.getGameProfile().getName(), false));
                return;
            }
            try {
                if (packet.requestId() < 0) throw new IllegalArgumentException("Solicitação inválida.");
                FichaRegistro sheet = data.getFicha(player.getUUID()).atualizar(packet.respostas(), packet.etapa(), packet.revisao(), packet.confirmar());
                data.setFicha(player.getUUID(), sheet);
                if (packet.confirmar()) {
                    Persona previous = data.getPersona(player.getUUID());
                    ServerEvents.setPersona(player, new Persona(sheet.confirmada().nome(), Integer.parseInt(sheet.confirmada().idade()), previous.desc()));
                }
                send(player, player.getUUID(), player.getGameProfile().getName(),
                        packet.requestId(), false, false, "");
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                send(player, player.getUUID(), player.getGameProfile().getName(),
                        packet.requestId(), false, false, exception.getMessage());
            }
        });
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !RPWorldData.get(player.server).getFicha(player.getUUID()).tabletEntregue()) {
            tryGiveTablet(player);
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_OPEN.remove(event.getEntity().getUUID());
        UPDATE_BUDGETS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tablet").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int now = player.server.getTickCount();
            Integer last = LAST_OPEN.get(player.getUUID());
            if (last != null && now - last < 5) return 0;
            tryGiveTablet(player);
            // abre o tablet que ele tem na mão (o dele ou um roubado)
            ItemStack naMao = player.getMainHandItem().is(RPTagMod.TABLET.get())
                    ? player.getMainHandItem()
                    : (temTabletProprio(player) ? tabletDono(player) : player.getMainHandItem());
            open(player, naMao);
            return 1;
        }).then(Commands.literal("ver").requires(s -> s.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    send(ctx.getSource().getPlayerOrException(), alvo.getUUID(),
                            alvo.getGameProfile().getName(), -1, true, true, "");
                    return 1;
                })))
        // (5.13.0) /tablet dar <jogador> [dono] — entrega um tablet (o próprio ou, como admin,
        // o de OUTRA pessoa: é assim que se testa a lore do roubo de tablet).
        .then(Commands.literal("dar").requires(s -> s.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            ItemStack stack = novoTablet(alvo);
                            if (!alvo.getInventory().add(stack)) {
                                alvo.drop(stack, false);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Tablet de " + alvo.getGameProfile().getName() + " entregue."), false);
                            return 1;
                        })
                        .then(Commands.argument("dono", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                            ServerPlayer dono = EntityArgument.getPlayer(ctx, "dono");
                            ItemStack stack = new ItemStack(RPTagMod.TABLET.get());
                            TabletItem.batizar(stack, dono.getUUID(), dono.getGameProfile().getName());
                            if (!alvo.getInventory().add(stack)) {
                                alvo.drop(stack, false);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Tablet de " + dono.getGameProfile().getName() + " entregue a "
                                            + alvo.getGameProfile().getName()
                                            + " (ele vai LER a ficha do outro)."), false);
                            return 1;
                        }))))
        // /tablet meus — lista os tablets que ele carrega e de quem são
        .then(Commands.literal("meus").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.is(RPTagMod.TABLET.get())) {
                    continue;
                }
                UUID dono = TabletItem.dono(stack);
                String nome = TabletItem.donoNome(stack);
                if (sb.length() > 0) {
                    sb.append("\u00a78 · ");
                }
                if (dono == null) {
                    sb.append("\u00a77sem dono");
                } else if (dono.equals(player.getUUID())) {
                    sb.append("\u00a7aSEU");
                } else {
                    sb.append("\u00a7dDE ").append(nome.isEmpty() ? dono.toString() : nome);
                }
            }
            String txt = sb.length() == 0 ? "\u00a78nenhum tablet" : sb.toString();
            ctx.getSource().sendSuccess(() -> Component.literal("\u00a78Tablets: " + txt), false);
            return 1;
        }))
          .then(Commands.literal("liberar").requires(s -> s.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                    RPWorldData data = RPWorldData.get(target.server);
                    data.setFicha(target.getUUID(), data.getFicha(target.getUUID()).liberarEdicao());
                    target.sendSystemMessage(Component.literal("Sua ficha foi liberada. Abra o tablet para editar.").withStyle(ChatFormatting.GREEN));
                    ctx.getSource().sendSuccess(() -> Component.literal("Edição liberada para " + target.getGameProfile().getName() + "."), false);
                    send(target, target.getUUID(), target.getGameProfile().getName(), -1, false, false, "");
                    return 1;
                }))));
    }
}
