package dev.rptag.yumaho.client;

import dev.rptag.yumaho.YuMaho;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** Visual da Dimensão Branca: sem sol/lua/estrelas, luz sempre cheia, névoa branco-amarelada (cores vêm do bioma). */
@EventBusSubscriber(modid = YuMaho.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class YuWhiteEffects extends DimensionSpecialEffects {
    private YuWhiteEffects() {
        super(Float.NaN, true, SkyType.NONE, true, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return color; }

    @Override
    public boolean isFoggyAt(int x, int z) { return false; }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent e) {
        e.register(YuMaho.rl("white"), new YuWhiteEffects());
    }
}
