package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Formatador do chat por canal: monta os componentes e envia por proximidade.
 */
public final class RPChat {

    private RPChat() {
    }

    /**
     * @return o nome "exibivel" do jogador: nome do personagem (se houver
     *         persona) ou o nick, seguido da tag (ʀᴘ)/(ᴏꜰꜰ ʀᴘ).
     */
    public static MutableComponent displayName(ServerPlayer player) {
        RPWorldData data = RPWorldData.get(player.server);
        Persona persona = data.getPersona(player.getUUID());
        boolean inRp = data.isInRp(player.getUUID());

        MutableComponent base = persona.isEmpty()
                ? Component.literal(player.getName().getString())
                : Component.literal(persona.name());
        return base.append(RPTags.tag(inRp));
    }

    /** Chat comum (canal local): <Nome (ʀᴘ)> mensagem */
    public static void broadcastLocalChat(ServerPlayer from, String message, int range, boolean shout) {
        MutableComponent msg = Component.empty()
                .append(Component.literal("<"))
                .append(displayName(from))
                .append(Component.literal("> "))
                .append(Component.literal(shout ? message.toUpperCase() : message)
                        .withStyle(shout ? ChatFormatting.BOLD : ChatFormatting.WHITE));
        RPChat.sendLocal(from, range, msg);
    }

    /** Envia a mensagem a todos os jogadores dentro do raio (mesmo mundo). */
    public static void sendLocal(ServerPlayer from, double range, Component message) {
        for (ServerPlayer p : from.server.getPlayerList().getPlayers()) {
            if (p.level() != from.level()) {
                continue;
            }
            if (p.position().distanceToSqr(from.position()) <= range * range) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** Envia a mensagem a TODOS os jogadores do servidor. */
    public static void sendGlobal(MinecraftServer server, Component message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(message);
        }
    }
}
