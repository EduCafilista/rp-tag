package dev.rptag.king;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Sons sintetizados (assets/carcosa/sounds, mono). Troque os .ogg por seus próprios se quiser. */
public final class KingSounds {
    public static SoundEvent WHISPER, LAUGH, DECREE, CHAINS, PORTAL, HASTUR_CHARGE, HASTUR_BEAM, METEOR, REALM, WAVE;

    public static void register(RegisterEvent e) {
        e.register(Registries.SOUND_EVENT, h -> {
            WHISPER = make(h, "whisper"); LAUGH = make(h, "laugh"); DECREE = make(h, "decree");
            CHAINS = make(h, "chains"); PORTAL = make(h, "portal"); HASTUR_CHARGE = make(h, "hastur_charge");
            HASTUR_BEAM = make(h, "hastur_beam"); METEOR = make(h, "meteor"); REALM = make(h, "realm"); WAVE = make(h, "wave");
        });
    }

    private static SoundEvent make(RegisterEvent.RegisterHelper<SoundEvent> h, String name) {
        ResourceLocation id = King.rl(name);
        SoundEvent s = SoundEvent.createVariableRangeEvent(id);
        h.register(id, s);
        return s;
    }

    private KingSounds() {}
}
