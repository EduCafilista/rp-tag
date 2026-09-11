package dev.rptag;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * RP Tag — mostra o estado RP no nome dos jogadores, baloes de fala e comandos /rp.
 *
 * <p>Os handlers de eventos ficam nas classes anotadas com {@code @EventBusSubscriber}:
 * <ul>
 *   <li>{@link ModNetworking} — registro dos pacotes de rede (mod bus)</li>
 *   <li>{@link ServerEvents} — eventos do lado do servidor (login, nomes, tab)</li>
 *   <li>{@link RPCommands} — comando /rp</li>
 *   <li>{@link BalaoCommands} — comando /balao (tela de personalizacao)</li>
 *   <li>{@code dev.rptag.client.ClientEvents} — eventos do lado do cliente (nametag)</li>
 *   <li>{@code dev.rptag.client.BubbleStyleScreen} — tela com seletor de cor</li>
 * </ul>
 */
@Mod(RPTagMod.MODID)
public final class RPTagMod {

    public static final String MODID = "rptag";
    public static final String VERSION = "3.13.0";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public RPTagMod(IEventBus modEventBus, ModContainer modContainer) {
        // Tudo e registrado via @EventBusSubscriber; nada a fazer aqui.
    }
}
