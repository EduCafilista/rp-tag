package dev.rptag.yumaho;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Todos os números de balanceamento ficam aqui (config/rptag-yumaho.toml). Ajuste nos testes. */
public final class YuConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PVP;
    public static final ModConfigSpec.DoubleValue SMOKE_CD, SMOKE_R_MIN, SMOKE_R_MAX, SMOKE_T_MIN, SMOKE_T_MAX;
    public static final ModConfigSpec.DoubleValue FJ_DMG, FJ_CHARGE, FJ_RANGE, FJ_CD, FJ_KB;
    public static final ModConfigSpec.DoubleValue WH_CD, WH_R_MIN, WH_R_MAX, WH_DMG, WH_PULL, WH_GRAPPLE;
    public static final ModConfigSpec.DoubleValue WH_MAX_PULL_HP;
    // (5.78.5) pacote do Drive: chicote com vários fios, tempo de agarrão, modo e AGARRAR (K)
    public static final ModConfigSpec.IntValue WH_MAX_COUNT, GS_MAX_AMP;
    public static final ModConfigSpec.DoubleValue WH_HOLD_MIN, WH_HOLD_MAX, GS_RANGE;
    public static final ModConfigSpec.BooleanValue GRAB_PLAYERS, GRAB_MOBS;
    public static final ModConfigSpec.DoubleValue GRAB_REACH, GRAB_NECK_SEC, GRAB_BODY_SEC, GRAB_NECK_DMG, GRAB_BODY_DMG,
            GRAB_MIN_HEALTH, GRAB_THROW_DMG, GRAB_SLAM_DMG, GRAB_CD, GRAB_MAX_HP;
    public static final ModConfigSpec.DoubleValue GS_CD, GS_T_MIN, GS_T_MAX, GS_SPEED, GS_ATKSPD, GS_DMG, GS_TIRED_T;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PVP = b.comment("Habilidades podem ferir/puxar outros jogadores").define("pvp", true);
        // (5.78.8) o dano do golpe corpo a corpo NAO e mais alterado pelo mod (ver YuHit)

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
        WH_MAX_COUNT = b.comment("Chicotes simultâneos com 100% de poder (10% = 1, 50% = 2, 100% = este número)")
                .defineInRange("maxWhips", 4, 1, 8);
        WH_HOLD_MIN = b.comment("Tempo (s) que o chicote prende/puxa o alvo com ~0% / 100%").defineInRange("holdMinSec", 1.5, 0.2, 60.0);
        WH_HOLD_MAX = b.defineInRange("holdMaxSec", 5.0, 0.2, 60.0);
        b.pop();

        b.push("gearshift");
        GS_CD = b.defineInRange("cooldownSec", 45.0, 0.0, 3600.0);
        GS_T_MIN = b.defineInRange("durationMin", 4.0, 1.0, 120.0);
        GS_T_MAX = b.defineInRange("durationMax", 10.0, 1.0, 120.0);
        GS_SPEED = b.comment("Bônus de velocidade (fração) com 100%").defineInRange("speedBonus", 0.45, 0.0, 3.0);
        GS_ATKSPD = b.defineInRange("attackSpeedBonus", 0.35, 0.0, 3.0);
        GS_DMG = b.defineInRange("damageBonus", 0.20, 0.0, 3.0);
        GS_TIRED_T = b.comment("Segundos de cansaço (lentidão/fraqueza) ao acabar, com 100%").defineInRange("exhaustSec", 8.0, 0.0, 120.0);
        GS_RANGE = b.comment("Alcance (blocos) do M — desacelerar o alvo na mira, com 100% de poder").defineInRange("slowRange", 14.0, 2.0, 64.0);
        GS_MAX_AMP = b.comment("Nível máximo de Lentidão do M com 100% de poder").defineInRange("slowMaxLevel", 3, 0, 6);
        b.pop();

        b.push("grab");
        b.comment("AGARRAR (tecla K): pega o alvo pelo pescoço; a forma Macaco pega pelo corpo.");
        GRAB_PLAYERS = b.comment("Pode agarrar outros jogadores? (se false, PvP de agarrão fica desligado)")
                .define("players", true);
        GRAB_MOBS = b.define("mobs", true);
        GRAB_REACH = b.comment("Alcance (blocos) para pegar").defineInRange("reach", 4.5, 1.0, 16.0);
        GRAB_NECK_SEC = b.comment("Tempo (s) segurando pelo pescoço / pelo corpo").defineInRange("neckSec", 6.0, 0.5, 60.0);
        GRAB_BODY_SEC = b.defineInRange("bodySec", 8.0, 0.5, 60.0);
        GRAB_NECK_DMG = b.comment("Dano por segundo sufocando (pescoço) / esmagando (corpo)").defineInRange("neckDamagePerSec", 1.0, 0.0, 100.0);
        GRAB_BODY_DMG = b.defineInRange("bodyDamagePerSec", 1.5, 0.0, 100.0);
        GRAB_MIN_HEALTH = b.comment("O dano contínuo NUNCA deixa o alvo abaixo desta vida (RP seguro)")
                .defineInRange("minHealthLeft", 2.0, 0.5, 100.0);
        GRAB_THROW_DMG = b.comment("Dano ao ARREMESSAR (Shift+K, forma Macaco)").defineInRange("throwDamage", 6.0, 0.0, 100.0);
        GRAB_SLAM_DMG = b.comment("Dano ao JOGAR NO CHÃO (Shift+K, pescoço)").defineInRange("slamDamage", 4.0, 0.0, 100.0);
        GRAB_CD = b.comment("Recarga (s) depois de soltar/arremessar").defineInRange("cooldownSec", 2.0, 0.0, 600.0);
        GRAB_MAX_HP = b.comment("Vida máxima do alvo que pode ser agarrado").defineInRange("maxTargetHealth", 300.0, 1.0, 100000.0);
        b.pop();

        SPEC = b.build();
    }

    public static int ticks(ModConfigSpec.DoubleValue v) { return (int) Math.round(v.get() * 20.0); }

    private YuConfig() {}
}
