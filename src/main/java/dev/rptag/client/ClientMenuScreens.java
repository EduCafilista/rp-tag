package dev.rptag.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.rptag.RPTagMod;

/**
 * Registro das TELAS dos menus (client-side). Sem isto o cliente recebe o
 * pacote de abrir o bau, nao encontra tela e FECHA NA HORA (nada aparece).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class ClientMenuScreens {

    private ClientMenuScreens() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(RPTagMod.BALAO_EDIT_MENU.get(), BalaoEditScreen::new);
    }
}
