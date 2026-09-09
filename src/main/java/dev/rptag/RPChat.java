package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Formatador do chat por canal: monta os componentes, colore a mensagem com a
 * cor pessoal do jogador e dispara os baloes de fala sobre a cabeca.
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

    /** @return o estilo da mensagem com a cor pessoal do jogador (se definida). */
    public static Style messageStyle(ServerPlayer player) {
        RPWorldData data = RPWorldData.get(player.server);
        if (data.hasCustomColor(player.getUUID())) {
            return Style.EMPTY.withColor(TextColor.fromRgb(data.getColor(player.getUUID())));
        }
        return Style.EMPTY;
    }

    /** Mostra o balao de fala sobre a cabeca (se o recurso estiver ligado). */
    public static void spawnBubble(ServerPlayer from, String rawText, boolean italic) {
        RPWorldData data = RPWorldData.get(from.server);
        if (!data.isBubblesOn() || data.isBubbleOptOut(from.getUUID())) {
            return;
        }
        String text = rawText.length() > 96 ? rawText.substring(0, 93) + "..." : rawText;
        BubbleStyle style = data.getStyle(from.getUUID());
        // decora com os emojis/personalizacao do autor
        String decorated = (style.prefix().isEmpty() ? "" : style.prefix() + " ")
                + text
                + (style.suffix().isEmpty() ? "" : " " + style.suffix());
        ChatBubblePayload payload = new ChatBubblePayload(from.getUUID(), decorated, style.colorRGB(),
                italic, "", "", style.background());
        for (ServerPlayer p : from.server.getPlayerList().getPlayers()) {
            if (p.level() == from.level()
                    && p.position().distanceToSqr(from.position()) <= 64.0 * 64.0) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }

    /** Chat comum (canal local): <Nome (ʀᴘ)> mensagem + balao */
    public static void broadcastLocalChat(ServerPlayer from, String message, int range, boolean shout) {
        MutableComponent msg = Component.empty()
                .append(Component.literal("<"))
                .append(displayName(from))
                .append(Component.literal("> "))
                .append(Component.literal(shout ? message.toUpperCase() : message)
                        .withStyle(messageStyle(from))
                        .withStyle(shout ? ChatFormatting.BOLD : ChatFormatting.WHITE));
        sendLocal(from, range, msg);
        spawnBubble(from, message, false);
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
