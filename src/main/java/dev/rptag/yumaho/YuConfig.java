package dev.rptag.yumaho;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Todos os números de balanceamento ficam aqui (config/rptag-yumaho.toml). Ajuste nos testes. */
public final class YuConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PVP;
    public static final ModConfigSpec.DoubleValue HIT_BONUS, CRIT_CHANCE_MAX;
    public static final ModConfigSpec.DoubleValue SMOKE_CD, SMOKE_R_MIN, SMOKE_R_MAX, SMOKE_T_MIN, SMOKE_T_MAX;
    public static final ModConfigSpec.DoubleValue FJ_DMG, FJ_CHARGE, FJ_RANGE, FJ_CD, FJ_KB;
    public static final ModConfigSpec.DoubleValue WH_CD, WH_R_MIN, WH_R_MAX, WH_DMG, WH_PULL, WH_GRAPPLE;
    public static final ModConfigSpec.DoubleValue WH_MAX_PULL_HP;
    public static final ModConfigSpec.DoubleValue GS_CD, GS_T_MIN, GS_T_MAX, GS_SPEED, GS_ATKSPD, GS_DMG, GS_TIRED_T;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PVP = b.comment("Habilidades podem ferir/puxar outros jogadores").define("pvp", true);

        b.push("hit");
        HIT_BONUS = b.comment("Dano extra por golpe corpo a corpo com 100% de poder (cresce com o quadrado do %)")
                .defineInRange("bonusDamageAt100", 6.0, 0.0, 200.0);
        CRIT_CHANCE_MAX = b.comment("Chance do 'golpe perfeito' (raio gigante, dano x2 do bônus) com 100% de poder")
                .defineInRange("critChanceAt100", 0.15, 0.0, 1.0);
        b.pop();

        b.push("smokescreen");
        SMOKE_CD = b.defineInRange("cooldownSec", 20.0, 0.0, 3600.0);
        SMOKE_R_MIN = b.comment("Raio (blocos) com 1% / 100% de poder").defineInRange("radiusMin", 3.0, 1.0, 40.0);
        SMOKE_R_MAX = b.defineInRange("radiusMax", 9.0, 1.0, 40.0);
        SMOKE_T_MIN = b.comment("Duração (s) com 1% / 100%").defineInRange("durationMin", 6.0, 1.0, 120.0);
        SMOKE_T_MAX = b.defineInRange("durationMax", 14.0, 1.0, 120.0);
        b.pop();

        b.push("fajin");
        FJ_DMG = b.comment("Dano com energia 100% e poder 100%").defineInRange("maxDamage", 26.0, 0.0, 500.0);
        FJ_CHARGE = b.comment("Segundos segurando a tecla até 100% de energia").defineInRange("chargeSec", 3.0, 0.5, 30.0);
        FJ_RANGE = b.comment("Alcance do golpe (blocos) com poder 50%").defineInRange("range", 3.5, 1.0, 12.0);
        FJ_CD = b.defineInRange("cooldownSec", 6.0, 0.0, 600.0);
        FJ_KB = b.defineInRange("knockback", 2.2, 0.0, 10.0);
        b.pop();

        b.push("blackwhip");
        WH_CD = b.defineInRange("cooldownSec", 4.0, 0.0, 600.0);
        WH_R_MIN = b.defineInRange("rangeMin", 8.0, 2.0, 64.0);
        WH_R_MAX = b.defineInRange("rangeMax", 24.0, 2.0, 64.0);
        WH_DMG = b.defineInRange("damage", 6.0, 0.0, 200.0);
        WH_PULL = b.comment("Força máxima de puxar o alvo").defineInRange("pullStrength", 2.0, 0.0, 6.0);
        WH_GRAPPLE = b.comment("Velocidade máxima do gancho (puxar a si mesmo)").defineInRange("grappleSpeed", 2.0, 0.0, 6.0);
        WH_MAX_PULL_HP = b.comment("Vida máxima do alvo que pode ser puxado").defineInRange("maxPullHealth", 150.0, 1.0, 100000.0);
        b.pop();

        b.push("gearshift");
        GS_CD = b.defineInRange("cooldownSec", 45.0, 0.0, 3600.0);
        GS_T_MIN = b.defineInRange("durationMin", 4.0, 1.0, 120.0);
        GS_T_MAX = b.defineInRange("durationMax", 10.0, 1.0, 120.0);
        GS_SPEED = b.comment("Bônus de velocidade (fração) com 100%").defineInRange("speedBonus", 0.45, 0.0, 3.0);
        GS_ATKSPD = b.defineInRange("attackSpeedBonus", 0.35, 0.0, 3.0);
        GS_DMG = b.defineInRange("damageBonus", 0.20, 0.0, 3.0);
        GS_TIRED_T = b.comment("Segundos de cansaço (lentidão/fraqueza) ao acabar, com 100%").defineInRange("exhaustSec", 8.0, 0.0, 120.0);
        b.pop();

        SPEC = b.build();
    }

    public static int ticks(ModConfigSpec.DoubleValue v) { return (int) Math.round(v.get() * 20.0); }

    private YuConfig() {}
}
