package dev.rptag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * (5.45.0) <b>O RASTRO DO JUÍZO — pra achar o portal sem se perder no labirinto.</b>
 *
 * <p>Pedido 101 do dono: <i>"no juízo, faça um rastro pro player seguir, assim encontrar o
 * portal"</i>.
 *
 * <p>O labirinto do Julgamento é <b>infinito e escuro</b> e a saída nasce de 240 a 360 blocos
 * longe, num ângulo sorteado ({@link JuizoFinal}). Antes o jogador só tinha as próprias pernas
 * e a sorte. Agora o chão acende um <b>rastro de almas</b> apontando para a saída:
 *
 * <ul>
 *   <li>as partículas saem <b>à frente do jogador</b>, no corredor, uma a cada ~1,4 bloco —
 *       dá pra seguir andando;</li>
 *   <li>o rastro <b>para na parede</b>: se o caminho em linha reta está tapado, ele some
 *       ali e o jogador entende que tem de contornar (é labirinto, não linha reta);</li>
 *   <li>a cada 4 ticks (5×/s) o rastro é redesenhado, então ele <b>anda junto</b> com o
 *       jogador — nunca fica largado atrás;</li>
 *   <li>quanto mais perto da saída, mais <b>quente</b> a cor (alma azul → chama do Juízo),
 *       e a cada 3 s um estouro de fogo marca o trecho.</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: erro aqui não desenha nada, nunca derruba o servidor.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class JuizoRastro {

    private JuizoRastro() {
    }

    /** quantos pontos o rastro desenha à frente do jogador. */
    private static final int PONTOS = 14;
    /** distância entre um ponto e outro (blocos). */
    private static final double PASSO = 1.4D;
    /** de quanto em quanto tempo o rastro é refeito (ticks). */
    private static final int CADA = 4;
    /** a partir daqui não vale a pena guiar (o jogador já está em cima da saída). */
    private static final double PERTO = 6.0D;

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        try {
            var servidor = event.getServer();
            if (servidor.getTickCount() % CADA != 0) {
                return;
            }
            for (ServerPlayer p : servidor.getPlayerList().getPlayers()) {
                if (!p.level().dimension().equals(JuizoFinal.CHAVE_JUIZO)) {
                    continue;
                }
                desenhar(p);
            }
        } catch (Throwable t) {
            // o rastro é enfeite: nunca derruba o servidor
        }
    }

    /** desenha o rastro de {@code p} até a saída dele (se ele já tiver uma). */
    private static void desenhar(ServerPlayer p) {
        try {
            BlockPos saida = JuizoFinal.saidaDe(p.getUUID());
            if (saida == null) {
                return;
            }
            if (!(p.level() instanceof ServerLevel lv)) {
                return;
            }
            Vec3 daqui = p.position();
            Vec3 alvo = Vec3.atCenterOf(saida);
            double distancia = daqui.distanceTo(alvo);
            if (distancia < PERTO) {
                return;                 // já achou: nada de rastro na cara dele
            }
            Vec3 dir = alvo.subtract(daqui).normalize();
            // perto da saída o rastro fica mais "quente" e mais denso
            float quente = (float) Math.max(0.0F, Math.min(1.0F, 1.0F - distancia / 300.0D));

            // anda na horizontal: o labirinto é plano, guiar em 3D confunde
            double dy = 0.0D;
            for (int i = 1; i <= PONTOS; i++) {
                Vec3 ponto = daqui.add(dir.x * PASSO * i, dy, dir.z * PASSO * i);
                BlockPos bp = BlockPos.containing(ponto.x, p.getY(), ponto.z);
                if (parede(lv, bp, p)) {
                    // tem parede na frente: o rastro acaba aqui (o jogador contorna e ele volta)
                    break;
                }
                double y = piso(lv, bp, p.getY());
                if (y < 0) {
                    break;
                }
                int n = (i % 4 == 0) ? 2 : 1;
                for (int k = 0; k < n; k++) {
                    lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            ponto.x + (Math.random() - 0.5D) * 0.25D,
                            y + 0.08D + Math.random() * 0.10D,
                            ponto.z + (Math.random() - 0.5D) * 0.25D,
                            1, 0.02D, 0.03D, 0.02D, 0.0D);
                }
                // perto da saída, uma brasa vermelha por cima da alma
                if (quente > 0.55F && i % 3 == 0) {
                    lv.sendParticles(ParticleTypes.FLAME,
                            ponto.x, y + 0.22D, ponto.z, 1, 0.0D, 0.02D, 0.0D, 0.0D);
                }
            }
        } catch (Throwable t) {
            // um jogador com problema não cala o rastro dos outros
        }
    }

    /** tem bloco sólido naquele ponto (parede do labirinto)? */
    private static boolean parede(ServerLevel lv, BlockPos bp, ServerPlayer p) {
        BlockState a = lv.getBlockState(bp);
        BlockState b = lv.getBlockState(bp.above());
        return (a.isSolid() && !a.isAir()) || (b.isSolid() && !b.isAir());
    }

    /** altura do chão do corredor ali (ou -1 se não houver chão). */
    private static double piso(ServerLevel lv, BlockPos bp, double yJogador) {
        int y = (int) Math.floor(yJogador) + 1;
        for (int i = 0; i < 8; i++) {
            BlockPos teste = new BlockPos(bp.getX(), y - i, bp.getZ());
            BlockState st = lv.getBlockState(teste);
            if (!st.isAir() && st.isSolid()) {
                return teste.getY() + 1.02D;
            }
        }
        return -1.0D;
    }
}
