package dev.rptag;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * RP Tag — mostra (RP) / (OFF RP) no nome dos jogadores e adiciona o comando /rp.
 *
 * <p>Os handlers de eventos ficam nas classes anotadas com {@code @EventBusSubscriber}:
 * <ul>
 *   <li>{@link ModNetworking} — registro dos pacotes de rede (mod bus)</li>
 *   <li>{@link ServerEvents} — eventos do lado do servidor (login, nomes)</li>
 *   <li>{@link RPCommands} — comando /rp</li>
 *   <li>{@link BalaoCommands} — comandos /balao e /balaoedit</li>
 *   <li>{@code dev.rptag.client.ClientEvents} — eventos do lado do cliente (nametag)</li>
 * </ul>
 */
@Mod(RPTagMod.MODID)
public final class RPTagMod {

    public static final String MODID = "rptag";
    public static final String VERSION = "2.4.0";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Registro dos menus (o editor do balao em inventario). */
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);

    public static final Supplier<MenuType<BalaoEditMenu>> BALAO_EDIT_MENU =
            MENUS.register("balao_edit",
                    () -> new MenuType<>(BalaoEditMenu::new, FeatureFlags.VANILLA_SET));

    public RPTagMod(IEventBus modEventBus, ModContainer modContainer) {
        MENUS.register(modEventBus);
    }
}
