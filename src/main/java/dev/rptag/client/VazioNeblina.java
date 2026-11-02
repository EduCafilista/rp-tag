package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * (5.12.0) O VAZIO MAIS PRETO — pedido 49.
 *
 * <p>O dono: <i>"o vazio tem que ser mais escuro, mais preto"</i>. A dimensão já
 * é escura (sem céu, com teto e luz ambiente quase zero), mas a neblina do End
 * clareava o horizonte e dava aquele roxo lavado. Aqui a neblina de
 * {@code rptag:exilio} vira <b>quase preto</b> (#030201–#0A0512) e fecha em 96
 * blocos: os prédios perdidos aparecem como silhueta e o que está longe some
 * no breu, sem nunca virar "tela preta" (é neblina de mundo, com profundidade).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioNeblina {

    private VazioNeblina() {
    }

    private static final ResourceKey<Level> EXILIO = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio"));

    private static boolean noVazio() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.dimension().equals(EXILIO);
    }

    /** a cor da neblina: preto azulado, quase sem luz. */
    @SubscribeEvent
    public static void corDaNeblina(ViewportEvent.ComputeFogColor e) {
        if (!noVazio()) {
            return;
        }
        // (5.13.1) "o vazio deve ser como antes, pq agora tá tudo preto na dimensão".
        // A neblina era quase PRETA (#050308) e fechava em 96 blocos — o dono via um
        // breu sem horizonte e achou que a dimensão estava vazia. Agora ela é cinza-azul
        // de cidade morta ao entardecer (#2A2833–#33303F): dá para ver longe, e o clima
        // continua sendo o de um lugar sem sol.
        float t = (System.currentTimeMillis() % 20000L) / 20000F;
        float pulso = 0.006F + 0.006F * (float) Math.sin(t * 6.2831853F);
        e.setRed(0.052F + pulso);
        e.setGreen(0.048F + pulso);
        e.setBlue(0.070F + pulso * 1.2F);
    }

    /** a neblina fecha perto: o que é longe é breu. */
    @SubscribeEvent
    public static void distanciaDaNeblina(ViewportEvent.RenderFog e) {
        if (!noVazio()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float y = mc.player == null ? 0F : (float) mc.player.getY();
        // (5.13.1) horizonte visível: 190 blocos (era 96). Caindo no vazio, a neblina
        // volta a engolir rápido — é o único lugar onde o breu é proposital.
        float perto = y < 0 ? 2F : 22F;
        float longe = y < 0 ? 30F : 190F;
        e.setNearPlaneDistance(perto);
        e.setFarPlaneDistance(longe);
        e.setCanceled(true);
    }
}
