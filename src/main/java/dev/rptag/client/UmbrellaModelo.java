package dev.rptag.client;

import dev.rptag.RPTagMod;
import dev.rptag.VarinhaSombraItem;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * (5.40.0) <b>O MODELO ABERTO DO UMBRELLA.</b>
 *
 * <h2>O defeito que isto conserta</h2>
 * O {@code models/item/umbrella_sombra.json} escolhe a versão do guarda-chuva por dois
 * predicados: a cor ({@code custom_model_data}) e o estado
 * ({@code rptag:aberto} = 0 fechado / 1 aberto). Só que <b>predicado que ninguém registrou vale
 * SEMPRE 0</b>: o Minecraft procura a função em {@code ItemProperties} e, não achando, devolve
 * zero. Ou seja, os 8 modelos ABERTOS estavam no jar, com a arte certa, e nunca eram escolhidos
 * — era literalmente por isso que "não existe a versão dele aberto" (print do dono, 5.39.0).
 *
 * <p>Igual ao que o vanilla faz com o {@code pulling} do arco e o {@code blocking} do escudo:
 * o cliente registra, <b>para este item</b>, uma função que responde 1 quando o item está em
 * uso (botão direito segurando) — o mesmo estado que o servidor usa em
 * {@code VampiroSistema.protegido()} pra barrar o sol. Modelo e regra nunca mais podem divergir.
 *
 * <p>Também é o motivo de o predicado <b>não</b> se chamar {@code using}: nome próprio
 * ({@code rptag:aberto}) pra ninguém confundir com o {@code using} do spyglass/tridente.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class UmbrellaModelo {

    /** o nome do predicado usado pelos overrides de {@code umbrella_sombra.json}. */
    public static final ResourceLocation ABERTO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "aberto");

    private UmbrellaModelo() {
    }

    @SubscribeEvent
    public static void aoPrepararCliente(FMLClientSetupEvent evento) {
        evento.enqueueWork(() -> {
            try {
                ClampedItemPropertyFunction aberto = (stack, nivel, quem, semente) ->
                        quem != null && quem.isUsingItem()
                                && quem.getUseItem().getItem() instanceof VarinhaSombraItem
                                ? 1.0F : 0.0F;
                ItemProperties.register(RPTagMod.UMBRELLA.get(), ABERTO, aberto);
                RPTagMod.LOGGER.info("RP Tag: umbrella — predicado '" + ABERTO
                        + "' registrado (fechado/aberto na mao)");
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: nao consegui registrar o predicado do umbrella", t);
            }
        });
    }
}
