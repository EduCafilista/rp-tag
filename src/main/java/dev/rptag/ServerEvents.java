package dev.rptag;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Logica do lado do servidor: guardaria de estado/persona, sincronizacao
 * com os clientes, nomes (chat/TAB) e chat local por proximidade.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /** @return true se o jogador esta em RP. */
    public static boolean isInRp(ServerPlayer player) {
        return RPWorldData.get(player.server).isInRp(player.getUUID());
    }

    /** @return a persona do jogador (ou EMPTY). */
    public static Persona getPersona(ServerPlayer player) {
        return RPWorldData.get(player.server).getPersona(player.getUUID());
    }

    // ================================================================
    //  Estado RP
    // ================================================================

    /** Define o estado do jogador e sincroniza todos os clientes. */
    public static void setState(ServerPlayer target, boolean inRp, boolean notifyTarget) {
        MinecraftServer server = target.server;
        RPWorldData data = RPWorldData.get(server);
        data.setInRp(target.getUUID(), inRp);

        target.refreshDisplayName();
        PacketDistributor.sendToAllPlayers(new SyncRPStatePayload(target.getUUID(), inRp));

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

    // ================================================================
    //  Persona
    // ================================================================

    /** Define a persona e sincroniza todos os clientes. */
    public static void setPersona(ServerPlayer target, Persona persona) {
        RPWorldData.get(target.server).setPersona(target.getUUID(), persona);
        target.refreshDisplayName();
        PacketDistributor.sendToAllPlayers(PersonaSyncPayload.of(target.getUUID(), persona));

        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(target));
        target.server.getPlayerList().broadcastAll(tabPacket);
    }

    // ================================================================
    //  Login: sincroniza RP + persona de todos
    // ================================================================

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) {
            return;
        }
        MinecraftServer server = joined.server;
        RPWorldData data = RPWorldData.get(server);

        // Estado e persona do novo jogador para quem ja estava online.
        PacketDistributor.sendToAllPlayers(new SyncRPStatePayload(joined.getUUID(), data.isInRp(joined.getUUID())));
        PacketDistributor.sendToAllPlayers(PersonaSyncPayload.of(joined.getUUID(), data.getPersona(joined.getUUID())));

        // Estado e persona de todos os online para o novo jogador.
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(joined, new SyncRPStatePayload(other.getUUID(), data.isInRp(other.getUUID())));
            PacketDistributor.sendToPlayer(joined, PersonaSyncPayload.of(other.getUUID(), data.getPersona(other.getUUID())));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // As lore zones voltam a aparecer na proxima sessao.
        LoreZones.clearSeen(event.getEntity().getUUID());
    }

    // ================================================================
    //  Nomes (chat/TAB/servidor)
    // ================================================================

    /** Nome base do jogador: personagem (se houver) ou nick, com tooltip da descricao. */
    private static MutableComponent baseName(ServerPlayer player, Persona persona) {
        if (persona.isEmpty()) {
            return Component.literal(player.getName().getString());
        }
        MutableComponent name = Component.literal(persona.name());
        if (!persona.desc().isEmpty()) {
            MutableComponent tooltip = Component.literal(persona.name() + "\n")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(persona.desc()).withStyle(ChatFormatting.GRAY));
            if (persona.age() > 0) {
                tooltip.append(Component.literal(" • " + persona.age() + " anos").withStyle(ChatFormatting.GRAY));
            }
            name = name.withStyle(s -> s.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
        }
        return name;
    }

    /** Nome usado no chat/servidor (com persona e tag). */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // lado do cliente trata em dev.rptag.client.ClientEvents
        }
        if (RPTags.hasTag(event.getDisplayname())) {
            return;
        }
        event.setDisplayname(baseName(player, getPersona(player))
                .append(RPTags.tag(isInRp(player))));
    }

    /** Nome usado na lista de jogadores (TAB). */
    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Component base = event.getDisplayName();
        if (base == null || RPTags.hasTag(base)) {
            return; // nulo = padrao do vanilla; com tag = ja veio de getDisplayName()
        }
        event.setDisplayName(baseName(player, getPersona(player)).append(RPTags.tag(isInRp(player))));
    }

    // ================================================================
    //  Chat local (vanilla -> por proximidade)
    // ================================================================

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        RPWorldData data = RPWorldData.get(event.getPlayer().server);
        boolean bubbleMode = data.isBubbleMode(event.getPlayer().getUUID());
        if (!data.isChatLocal() && !bubbleMode) {
            return; // chat global vanilla, sem balao
        }
        event.setCanceled(true);
        if (bubbleMode) {
            // MODO BALAO: so o balao na cabeca (quem estiver perto ve) — nada no chat
            RPChat.spawnBubble(event.getPlayer(), event.getRawText(), false);
        } else {
            RPChat.broadcastLocalChat(event.getPlayer(), event.getRawText(), RPWorldData.LOCAL_RANGE, false);
        }
    }

    // ================================================================
    //  Salvamento do estilo do balao (tela de personalizacao)
    // ================================================================

    public static void handleUpdateBubbleStyle(final UpdateBubbleStylePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BubbleStyle style = new BubbleStyle(payload.colorRGB(), payload.prefix(), payload.suffix(),
                    payload.background());
            RPWorldData.get(player.server).setStyle(player.getUUID(), style);
            player.sendSystemMessage(Component.literal("Estilo do balao salvo! ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("( experimente falar! )").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        });
    }
}
