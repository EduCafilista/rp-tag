package dev.rptag;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Logica do lado do servidor: guardaria de estado, sincronizacao com os clientes
 * e formatacao do nome no chat / TAB.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /** @return true se o jogador esta em RP. */
    public static boolean isInRp(ServerPlayer player) {
        return RPWorldData.get(player.server).isInRp(player.getUUID());
    }

    /** Define o estado do jogador e sincroniza todos os clientes. */
    public static void setState(ServerPlayer target, boolean inRp, boolean notifyTarget) {
        MinecraftServer server = target.server;
        RPWorldData.get(server).set(target.getUUID(), inRp);

        // Invalida o cache do nome no servidor (chat), dispara NameFormat de novo.
        target.refreshDisplayName();

        // Todos os clientes passam a conhecer o novo estado (nametag acima da cabeca).
        PacketDistributor.sendToAllPlayers(new SyncRPStatePayload(target.getUUID(), inRp));

        // Atualiza o nome na lista de jogadores (TAB) para todos.
        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(target));
        server.getPlayerList().broadcastAll(tabPacket);

        if (notifyTarget) {
            target.sendSystemMessage(Component.empty()
                    .append(Component.literal(inRp ? "Modo RP ativado! Seu nome agora mostra " : "Modo RP desativado! Seu nome agora mostra ")
                            .withStyle(inRp ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(RPTags.tag(inRp))
                    .append(Component.literal(".").withStyle(inRp ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
    }

    /** Alterna o estado do jogador. @return o novo estado. */
    public static boolean toggle(ServerPlayer player) {
        boolean newValue = !isInRp(player);
        setState(player, newValue, true);
        return newValue;
    }

    /** Quando um jogador entra, sincroniza o estado de todos para ele (e dele para todos). */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) {
            return;
        }
        MinecraftServer server = joined.server;
        RPWorldData data = RPWorldData.get(server);

        // Estado do novo jogador para quem ja estava online.
        PacketDistributor.sendToAllPlayers(new SyncRPStatePayload(joined.getUUID(), data.isInRp(joined.getUUID())));

        // Estado de todos os online para o novo jogador.
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(joined, new SyncRPStatePayload(other.getUUID(), data.isInRp(other.getUUID())));
        }
    }

    /** Nome usado no chat (e em qualquer getDisplayName no servidor). */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // lado do cliente trata em dev.rptag.client.ClientEvents
        }
        if (RPTags.hasTag(event.getDisplayname())) {
            return;
        }
        event.setDisplayname(event.getDisplayname().copy().append(RPTags.tag(isInRp(player))));
    }

    /** Nome usado na lista de jogadores (TAB), montado no servidor. */
    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Component base = event.getDisplayName();
        if (base == null || RPTags.hasTag(base)) {
            return; // nulo = padrao do vanilla; com tag = ja veio de getDisplayName()
        }
        event.setDisplayName(base.copy().append(RPTags.tag(isInRp(player))));
    }
}
