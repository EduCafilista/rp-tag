package dev.rptag.yumaho;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Sons sintetizados (assets/yumaho/sounds/*.ogg, mono) ligados em assets/yumaho/sounds.json. */
public final class YuSounds {
    public static SoundEvent HIT, RELEASE, WHIP;

    public static void register(RegisterEvent e) {
        e.register(Registries.SOUND_EVENT, h -> {
            HIT = make(h, "hit");
            RELEASE = make(h, "release");
            WHIP = make(h, "whip");
        });
    }

    private static SoundEvent make(RegisterEvent.RegisterHelper<SoundEvent> h, String name) {
        ResourceLocation id = YuMaho.rl(name);
        SoundEvent s = SoundEvent.createVariableRangeEvent(id);
        h.register(id, s);
        return s;
    }

    private YuSounds() {}
}
