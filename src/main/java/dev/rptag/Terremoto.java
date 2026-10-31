package dev.rptag;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.4.0) TERREMOTO: as Terras colapsam e a realidade se "corrige" (estilo Loki).
 *
 * <p>{@code /rp admin terremoto iniciar} liga o evento: tela tremendo e, de tempos em
 * tempos (por jogador), um PULSO que deixa a pessoa estunada e lenta. O adm encerra tudo
 * com {@code parar} ou leva jogador por jogador com {@code levar <jogador>} (efeito verde
 * e saída do servidor). O estado fica só na memória: reiniciar o servidor encerra o evento.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class Terremoto {
    private static boolean ativo;
    private static final Map<UUID, Integer> PROXIMO_PULSO = new HashMap<>();
    private static final Map<UUID, Integer> LEVAR_EM = new HashMap<>();
    private static final int LEVAR_TICKS = 60;

    private Terremoto() {}

    public static boolean ativo() { return ativo; }

    /** Intervalo até o próximo pulso: 8 a 20 segundos. */
    public static int sorteioIntervalo(RandomSource rng) { return 160 + rng.nextInt(241); }

    /** Duração do pulso: 2 a 4 segundos. */
    public static int sorteioDuracao(RandomSource rng) { return 40 + rng.nextInt(41); }

    public static void iniciar(MinecraftServer server) {
        ativo = true;
        PROXIMO_PULSO.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new TerremotoPayload(TerremotoPayload.INICIAR, 0));
            PROXIMO_PULSO.put(p.getUUID(), server.getTickCount() + sorteioIntervalo(p.getRandom()));
        }
    }

    public static void parar(MinecraftServer server) {
        ativo = false;
        PROXIMO_PULSO.clear();
        LEVAR_EM.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            PacketDistributor.sendToPlayer(p, new TerremotoPayload(TerremotoPayload.PARAR, 0));
    }

    public static void levar(ServerPlayer alvo) {
        PacketDistributor.sendToPlayer(alvo, new TerremotoPayload(TerremotoPayload.LEVAR, LEVAR_TICKS));
        alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LEVAR_TICKS + 20, 6, false, false));
        LEVAR_EM.put(alvo.getUUID(), alvo.server.getTickCount() + LEVAR_TICKS);
    }

    private static void pulso(ServerPlayer p) {
        int dur = sorteioDuracao(p.getRandom());
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 3, false, false));
        PacketDistributor.sendToPlayer(p, new TerremotoPayload(TerremotoPayload.PULSO, dur));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        int now = server.getTickCount();
        if (!LEVAR_EM.isEmpty()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Integer quando = LEVAR_EM.get(p.getUUID());
                if (quando == null) continue;
                if (now % 3 == 0) p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        p.getX(), p.getY() + 0.9, p.getZ(), 6, 0.35, 0.8, 0.35, 0.02);
                if (now >= quando) {
                    LEVAR_EM.remove(p.getUUID());
                    p.connection.disconnect(Component.literal("A realidade te levou."));
                }
            }
        }
        if (!ativo || now % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Integer quando = PROXIMO_PULSO.get(p.getUUID());
            if (quando == null) { PROXIMO_PULSO.put(p.getUUID(), now + sorteioIntervalo(p.getRandom())); continue; }
            if (now >= quando && !LEVAR_EM.containsKey(p.getUUID())) {
                pulso(p);
                PROXIMO_PULSO.put(p.getUUID(), now + sorteioIntervalo(p.getRandom()));
            }
        }
    }

    @SubscribeEvent public static void entrou(PlayerEvent.PlayerLoggedInEvent e) {
        if (ativo && e.getEntity() instanceof ServerPlayer p)
            PacketDistributor.sendToPlayer(p, new TerremotoPayload(TerremotoPayload.RETOMAR, 0));
    }

    /** Toda saída do servidor deixa um brilho verde sumindo no lugar do jogador. */
    @SubscribeEvent public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        LEVAR_EM.remove(p.getUUID());
        PROXIMO_PULSO.remove(p.getUUID());
        var level = p.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 45, 0.4, 0.9, 0.4, 0.04);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.3, 0.9, 0.3, 0.06);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
    }

    /** {@code /rp admin terremoto ...} */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        return Commands.literal("terremoto")
                .then(Commands.literal("iniciar").executes(ctx -> {
                    iniciar(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Terremoto INICIADO. Use /rp admin terremoto parar ou levar <jogador>.")
                            .withStyle(ChatFormatting.DARK_PURPLE), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("parar").executes(ctx -> {
                    parar(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Terremoto encerrado.").withStyle(ChatFormatting.GREEN), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Terremoto: " + (ativo ? "ATIVO" : "desligado")
                            + " | sendo levados: " + LEVAR_EM.size()).withStyle(ChatFormatting.GOLD), false);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("levar").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    levar(alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(alvo.getName().getString() + " esta sendo levado pela realidade...")
                            .withStyle(ChatFormatting.GREEN), true);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
