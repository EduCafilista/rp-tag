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
        // respira muito de leve (0,008..0,018) — dá vida sem clarear
        float t = (System.currentTimeMillis() % 20000L) / 20000F;
        float pulso = 0.006F + 0.006F * (float) Math.sin(t * 6.2831853F);
        e.setRed(0.020F + pulso);
        e.setGreen(0.014F + pulso * 0.6F);
        e.setBlue(0.032F + pulso);
    }

    /** a neblina fecha perto: o que é longe é breu. */
    @SubscribeEvent
    public static void distanciaDaNeblina(ViewportEvent.RenderFog e) {
        if (!noVazio()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float y = mc.player == null ? 0F : (float) mc.player.getY();
        // caindo no vazio: a neblina engole mais rápido ainda
        float perto = y < 0 ? 2F : 9F;
        float longe = y < 0 ? 26F : 96F;
        e.setNearPlaneDistance(perto);
        e.setFarPlaneDistance(longe);
        e.setCanceled(true);
    }
}
