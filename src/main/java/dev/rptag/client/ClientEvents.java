package dev.rptag.client;

import java.util.UUID;

import dev.rptag.RPTagMod;
import dev.rptag.RPTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Eventos do cliente: nome com a tag (e com o nome do personagem,
 * quando houver persona) no nametag acima da cabeca.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (NameplateRenderer.BADGE_ENABLED || !ClientRPStates.hasData()) {
            return;
        }
        UUID id = event.getEntity().getUUID();

        String personaName = ClientPersonaCache.getName(id);
        MutableComponent base;
        if (personaName != null) {
            base = Component.literal(personaName);
            String desc = ClientPersonaCache.getDesc(id);
            if (!desc.isEmpty()) {
                Component tooltip = Component.literal(personaName + "\n").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(desc).withStyle(ChatFormatting.GRAY));
                base = base.withStyle(s -> s.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
            }
        } else {
            base = event.getDisplayname().copy();
        }
        event.setDisplayname(base.append(RPTags.tag(ClientRPStates.isInRp(id))));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRPStates.clear();
        ClientPersonaCache.clear();
        ClientBubbleCache.clear();
    }
}
