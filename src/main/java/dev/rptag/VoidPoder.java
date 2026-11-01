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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.5.0) PODER DO VOID (estilo Marvel): o adm escolhe, jogador a jogador, se a pessoa
 * é MORTA ou mandada para a dimensão {@code rptag:vazio}. Em ambos os casos: a tela da vítima
 * escurece, todos ouvem o som do vazio e o chat avisa "---- VAZIO ---- apagou X da existência".
 *
 * <p>{@code /rp admin void apagar <jogador>}, {@code void dimensao <jogador>} e
 * {@code void voltar <jogador>} (traz de volta ao spawn do mundo).
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class VoidPoder {
    public static final ResourceKey<Level> VAZIO = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vazio"));
    /** O escurecer dura 2 s antes da ação. */
    static final int ESCURECER_TICKS = 40;

    private enum Acao { APAGAR, DIMENSAO }

    private record Pendente(Acao acao, int quando) {}
    private static final Map<UUID, Pendente> PENDENTES = new HashMap<>();

    private VoidPoder() {}

    /** Texto do chat. Fica numa função pura para o teste conferir o formato. */
    public static String textoChat(String nome, boolean dimensao) {
        return dimensao
                ? "---- VAZIO ---- jogou " + nome + " no vazio"
                : "---- VAZIO ---- apagou " + nome + " da existência";
    }

    private static void usar(MinecraftServer server, ServerPlayer alvo, Acao acao) {
        PENDENTES.put(alvo.getUUID(), new Pendente(acao, server.getTickCount() + ESCURECER_TICKS));
        PacketDistributor.sendToPlayer(alvo, new VoidPayload(ESCURECER_TICKS));
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            p.playNotifySound(RPTagMod.SOM_VOID_APAGAR.get(), SoundSource.MASTER, 1.0F, 1.0F);
    }

    private static void executar(MinecraftServer server, ServerPlayer alvo, Acao acao) {
        String nome = alvo.getName().getString();
        MutableComponent aviso = Component.literal(textoChat(nome, acao == Acao.DIMENSAO))
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        server.getPlayerList().broadcastSystemMessage(aviso, false);
        ServerLevel nivel = alvo.serverLevel();
        nivel.sendParticles(ParticleTypes.SQUID_INK, alvo.getX(), alvo.getY() + 1.0, alvo.getZ(), 60, 0.4, 0.9, 0.4, 0.05);
        nivel.sendParticles(ParticleTypes.PORTAL, alvo.getX(), alvo.getY() + 1.0, alvo.getZ(), 80, 0.5, 1.0, 0.5, 0.6);
        if (acao == Acao.APAGAR) {
            alvo.kill();
        } else {
            enviarParaVazio(server, alvo);
        }
    }

    public static void enviarParaVazio(MinecraftServer server, ServerPlayer alvo) {
        ServerLevel vazio = server.getLevel(VAZIO);
        if (vazio == null) {
            RPTagMod.LOGGER.error("RP Tag: dimensao rptag:vazio nao foi carregada");
            alvo.sendSystemMessage(Component.literal("A dimensão do vazio não está carregada.").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos centro = new BlockPos(0, 64, 0);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                vazio.setBlock(centro.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState(), 3);
        alvo.teleportTo(vazio, 0.5, 64.0, 0.5, alvo.getYRot(), alvo.getXRot());
    }

    public static void voltar(MinecraftServer server, ServerPlayer alvo) {
        ServerLevel mundo = server.overworld();
        BlockPos spawn = mundo.getSharedSpawnPos();
        alvo.teleportTo(mundo, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, alvo.getYRot(), alvo.getXRot());
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if (PENDENTES.isEmpty()) return;
        MinecraftServer server = e.getServer();
        int agora = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Pendente pend = PENDENTES.get(p.getUUID());
            if (pend == null || agora < pend.quando()) continue;
            PENDENTES.remove(p.getUUID());
            executar(server, p, pend.acao());
        }
    }

    /** {@code /rp admin void ...} */
    public static LiteralArgumentBuilder<CommandSourceStack> comando() {
        return Commands.literal("void")
                .then(Commands.literal("apagar").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    usar(ctx.getSource().getServer(), alvo, Acao.APAGAR);
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("dimensao").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    usar(ctx.getSource().getServer(), alvo, Acao.DIMENSAO);
                    return Command.SINGLE_SUCCESS;
                })))
                .then(Commands.literal("voltar").then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer alvo = EntityArgument.getPlayer(ctx, "jogador");
                    voltar(ctx.getSource().getServer(), alvo);
                    ctx.getSource().sendSuccess(() -> Component.literal(alvo.getName().getString() + " voltou do vazio.")
                            .withStyle(ChatFormatting.GREEN), true);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
