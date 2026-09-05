package dev.rptag.client;

import java.util.UUID;

import dev.rptag.RPTagMod;
import dev.rptag.RPTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Eventos do cliente.
 *
 * <p>Com o badge "bolinho" ligado (padrao), o nome fica limpo e a pastilha
 * colorida e desenhada por {@link NameplateRenderer}. Com o badge desligado,
 * a tag aparece como texto simples " (RP)" / " (OFF RP)" junto do nome.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (NameplateRenderer.BADGE_ENABLED || !ClientRPStates.hasData()) {
            return; // nome limpo; a pastilha cuida da aparencia
        }
        UUID id = event.getEntity().getUUID();
        event.setDisplayname(event.getDisplayname().copy().append(RPTags.tag(ClientRPStates.isInRp(id))));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRPStates.clear();
    }
}
