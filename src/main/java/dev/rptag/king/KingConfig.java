package dev.rptag.king;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balanceamento do Rei de Amarelo (config/rptag-king.toml). */
public final class KingConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PVP = B.comment("Habilidades e aura afetam outros jogadores").define("pvp", true);
    public static final ModConfigSpec.DoubleValue MARK_BONUS, MARK_SEC;

    public static final ModConfigSpec.DoubleValue AURA_FAR, AURA_MID, AURA_NEAR;
    public static final ModConfigSpec.DoubleValue DECREE_CD, DECREE_R, DECREE_DMG, DECREE_PUSH;
    public static final ModConfigSpec.DoubleValue CHAINS_CD, CHAINS_R, CHAINS_DMG, CHAINS_SEC, CHAINS_PULL;
    public static final ModConfigSpec.IntValue CHAINS_MAX;
    public static final ModConfigSpec.DoubleValue PORTAL_CD, PORTAL_RANGE, PORTAL_DMG, PORTAL_R;
    public static final ModConfigSpec.DoubleValue ILLU_CD, ILLU_SEC, ILLU_INVIS_SEC;
    public static final ModConfigSpec.IntValue ILLU_COUNT;
    public static final ModConfigSpec.DoubleValue HASTUR_CD, HASTUR_CHARGE, HASTUR_DMG, HASTUR_RANGE;
    public static final ModConfigSpec.DoubleValue FALL_CD, FALL_DMG, FALL_R, FALL_RADIUS, FALL_ZONE_SEC;
    public static final ModConfigSpec.IntValue FALL_COUNT;
    public static final ModConfigSpec.DoubleValue ULT_CD, ULT_SEC, ULT_RADIUS, ULT_WAVE_EVERY, ULT_WAVE_DMG, ULT_CLONE_EVERY;

    private static ModConfigSpec.DoubleValue d(String n, double def, double min, double max) { return B.defineInRange(n, def, min, max); }

    static {
        MARK_BONUS = B.comment("Dano extra (fração) que o Rei causa em quem tem a marca de Carcosa").defineInRange("markBonusDamage", 0.15, 0, 3);
        MARK_SEC = d("markSeconds", 8, 1, 120);

        B.push("aura");
        AURA_FAR = B.comment("Distâncias da aura passiva: sussurros / lentidão+distorção / fraqueza+marca").defineInRange("farBlocks", 15.0, 2, 64);
        AURA_MID = d("midBlocks", 8, 1, 64);
        AURA_NEAR = d("nearBlocks", 4, 1, 64);
        B.pop();

        B.push("decree");
        DECREE_CD = d("cooldownSec", 18, 0, 3600); DECREE_R = d("radius", 9, 2, 40);
        DECREE_DMG = d("damage", 8, 0, 200); DECREE_PUSH = d("push", 1.6, 0, 8);
        B.pop();

        B.push("chains");
        CHAINS_CD = d("cooldownSec", 22, 0, 3600); CHAINS_R = d("radius", 14, 2, 40);
        CHAINS_DMG = d("damage", 6, 0, 200); CHAINS_SEC = d("holdSeconds", 2.5, 0.5, 20);
        CHAINS_PULL = d("pull", 0.12, 0, 1); CHAINS_MAX = B.defineInRange("maxTargets", 8, 1, 32);
        B.pop();

        B.push("passage");
        PORTAL_CD = d("cooldownSec", 12, 0, 3600); PORTAL_RANGE = d("range", 20, 3, 64);
        PORTAL_DMG = d("arrivalDamage", 5, 0, 100); PORTAL_R = d("arrivalRadius", 3.5, 1, 12);
        B.pop();

        B.push("illusions");
        ILLU_CD = d("cooldownSec", 35, 0, 3600); ILLU_COUNT = B.defineInRange("count", 3, 1, 8);
        ILLU_SEC = d("lifetimeSeconds", 15, 2, 120); ILLU_INVIS_SEC = d("kingInvisibleSeconds", 6, 0, 60);
        B.pop();

        B.push("hastur");
        HASTUR_CD = d("cooldownSec", 16, 0, 3600); HASTUR_CHARGE = d("chargeSeconds", 1.5, 0.2, 10);
        HASTUR_DMG = d("damage", 18, 0, 500); HASTUR_RANGE = d("range", 48, 5, 128);
        B.pop();

        B.push("fall");
        FALL_CD = d("cooldownSec", 45, 0, 3600); FALL_COUNT = B.defineInRange("meteors", 12, 1, 40);
        FALL_DMG = d("damage", 12, 0, 300); FALL_R = d("impactRadius", 3.5, 1, 12);
        FALL_RADIUS = d("areaRadius", 11, 3, 40); FALL_ZONE_SEC = d("corruptionSeconds", 8, 1, 60);
        B.pop();

        B.push("realm");
        ULT_CD = d("cooldownSec", 120, 0, 7200); ULT_SEC = d("durationSec", 20, 3, 300);
        ULT_RADIUS = d("radius", 32, 8, 96); ULT_WAVE_EVERY = d("waveEverySec", 4, 1, 60);
        ULT_WAVE_DMG = d("waveDamage", 6, 0, 200); ULT_CLONE_EVERY = d("cloneEverySec", 5, 1, 60);
        B.pop();

        SPEC = B.build();
    }

    public static int ticks(ModConfigSpec.DoubleValue v) { return (int) Math.round(v.get() * 20.0); }

    private KingConfig() {}
}
