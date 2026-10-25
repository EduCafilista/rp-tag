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

    public static boolean tryGiveTablet(ServerPlayer player) {
        RPWorldData data = RPWorldData.get(player.server);
        ItemStack tablet = new ItemStack(RPTagMod.TABLET.get());
        if (player.getInventory().contains(tablet) || player.getInventory().add(tablet)) {
            FichaRegistro sheet = data.getFicha(player.getUUID());
            if (!sheet.tabletEntregue()) data.setFicha(player.getUUID(), sheet.comTabletEntregue());
            return true;
        }
        player.sendSystemMessage(Component.literal("Libere um espaço no inventário e use /tablet para receber seu tablet.").withStyle(ChatFormatting.YELLOW));
        return false;
    }

    public static boolean personaLocked(ServerPlayer player) {
        if (!RPWorldData.get(player.server).hasConfirmedFicha(player.getUUID())) return false;
        player.sendSystemMessage(Component.literal("Sua ficha já foi confirmada. Solicite liberação a um administrador e faça as alterações pelo tablet.").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public static void open(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = LAST_OPEN.get(player.getUUID());
        if (last != null && now - last < 5) return;
        LAST_OPEN.put(player.getUUID(), now);
        send(player, player, -1, true, false, "");
    }

    private static void send(ServerPlayer viewer, ServerPlayer target, int request, boolean open, boolean adminRead, String error) {
        FichaRegistro sheet = RPWorldData.get(target.server).getFicha(target.getUUID());
        PacketDistributor.sendToPlayer(viewer, new TabletSyncPayload(target.getUUID(), request, sheet,
                adminRead || !sheet.podeEditar(), open, false, error));
    }

    public static void handleUpdate(TabletUpdatePayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RPWorldData data = RPWorldData.get(player.server);
            int now = player.server.getTickCount();
            if (!UPDATE_BUDGETS.computeIfAbsent(player.getUUID(), id -> new TabletUpdateBudget()).accept(now)) {
                FichaRegistro sheet = data.getFicha(player.getUUID());
                PacketDistributor.sendToPlayer(player, new TabletSyncPayload(player.getUUID(), packet.requestId(), sheet,
                        !sheet.podeEditar(), false, true, "Aguarde um instante. O salvamento será tentado novamente."));
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
                send(player, player, packet.requestId(), false, false, "");
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException exception) {
                send(player, player, packet.requestId(), false, false, exception.getMessage());
            }
        });
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !RPWorldData.get(player.server).getFicha(player.getUUID()).tabletEntregue())
            tryGiveTablet(player);
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
            open(player);
            return 1;
        }).then(Commands.literal("ver").requires(s -> s.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    send(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "jogador"), -1, true, true, "");
                    return 1;
                })))
          .then(Commands.literal("liberar").requires(s -> s.hasPermission(2))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
                    RPWorldData data = RPWorldData.get(target.server);
                    data.setFicha(target.getUUID(), data.getFicha(target.getUUID()).liberarEdicao());
                    target.sendSystemMessage(Component.literal("Sua ficha foi liberada. Abra o tablet para editar.").withStyle(ChatFormatting.GREEN));
                    ctx.getSource().sendSuccess(() -> Component.literal("Edição liberada para " + target.getGameProfile().getName() + "."), false);
                    send(target, target, -1, false, false, "");
                    return 1;
                }))));
    }
}
