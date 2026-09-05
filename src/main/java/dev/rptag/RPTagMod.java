package dev.rptag;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * RP Tag — mostra (RP) / (OFF RP) no nome dos jogadores e adiciona o comando /rp.
 *
 * <p>Os handlers de eventos ficam nas classes anotadas com {@code @EventBusSubscriber}:
 * <ul>
 *   <li>{@link ModNetworking} — registro do pacote de sincronizacao (mod bus)</li>
 *   <li>{@link ServerEvents} — eventos do lado do servidor (login, nomes)</li>
 *   <li>{@link RPCommands} — comando /rp</li>
 *   <li>{@code dev.rptag.client.ClientEvents} — eventos do lado do cliente (nametag)</li>
 * </ul>
 */
@Mod(RPTagMod.MODID)
public final class RPTagMod {

    public static final String MODID = "rptag";
    public static final String VERSION = "1.2.1";

    public RPTagMod(IEventBus modEventBus, ModContainer modContainer) {
        // Tudo e registrado via @EventBusSubscriber; nada a fazer aqui.
    }
}
