package dev.rptag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (5.30.0) <b>O NOVO LABIRINTO É O REINO DO OUTRO MOD.</b>
 *
 * <p>Pedido do dono: <i>"a dimensão do juízo não vai existir mais daquele jeito, vai usar a
 * dimensão do outro mod para ser ela... e esse vai ser o novo labirinto"</i>, e depois
 * <i>"para o player entrar no portal em vez dele sair para o overworld ele vai para a sala que
 * eu te enviei"</i>. O mod do outro é o <b>Ronova: Ruler of Death</b> (CurseForge, Mian3450),
 * que traz o Reino dela (um vazio carmesim) e o Além (Afterlife).
 *
 * <h2>Por que a dimensão é DESCOBERTA e não fixada no código</h2>
 * O mod de referência é fechado e muda de ID entre versões. Se eu escrevesse
 * {@code ronova:realm} na mão e o autor renomeasse, o Juízo pararia de funcionar no meio de uma
 * sessão de RP. Então a dimensão é <b>procurada no registro do servidor</b> na hora: primeiro
 * uma que tenha "ronova" no nome do mod, depois uma que se chame "realm"/"reino", depois o
 * "afterlife"/"além", e por último qualquer dimensão de mod que exista. O ADM também pode
 * mandar a dimensão na mão com {@code /juizo dimensao definir <id>} se a escolha automática
 * errar — sem precisar de build nova.
 */
public final class JuizoReino {

    private JuizoReino() {
    }

    /** a escolhida (cache do resultado da procura, ou o que o ADM mandou). */
    private static ResourceKey<Level> escolhida;
    /** false = ainda não procuramos nesta sessão do servidor. */
    private static boolean procurou;

    /** (5.30.0) o ADM fixou a dimensão na mão? (nesse caso a procura automática não mexe) */
    private static boolean fixadaNaMao;

    // ---------------------------------------------------------------- descoberta

    /** todas as dimensões que NÃO são do vanilla nem do RP Tag (as candidatas). */
    public static List<ResourceKey<Level>> candidatas(MinecraftServer servidor) {
        List<ResourceKey<Level>> fora = new ArrayList<>();
        if (servidor == null) {
            return fora;
        }
        try {
            for (ResourceKey<Level> k : servidor.levelKeys()) {
                ResourceLocation id = k.location();
                String ns = id.getNamespace();
                if ("minecraft".equals(ns) || RPTagMod.MODID.equals(ns)) {
                    continue;
                }
                fora.add(k);
            }
            fora.sort(Comparator.comparing(k -> k.location().toString()));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui listar as dimensoes dos outros mods", t);
        }
        return fora;
    }

    /**
     * A dimensão do outro mod (memoizada). {@code null} = nenhum outro mod trouxe dimensão
     * (nesse caso o Juízo volta pro labirinto antigo do próprio RP Tag).
     */
    public static ResourceKey<Level> chave(MinecraftServer servidor) {
        if (procurou && !fixadaNaMao) {
            return escolhida;
        }
        procurou = true;
        if (fixadaNaMao) {
            return escolhida;
        }
        ResourceKey<Level> melhor = null;
        int melhorNota = 0;
        for (ResourceKey<Level> k : candidatas(servidor)) {
            int nota = notaDe(k.location());
            if (nota > melhorNota) {
                melhorNota = nota;
                melhor = k;
            }
        }
        escolhida = melhor;
        if (melhor != null) {
            RPTagMod.LOGGER.info("RP Tag: o labirinto do Julgamento agora e a dimensao {}",
                    melhor.location());
        }
        return melhor;
    }

    /**
     * a nota de uma dimensão: quanto mais o nome combina com o Reino da Morte, melhor.
     * (a ordem importa: o Reino vem antes do Além)
     */
    private static int notaDe(ResourceLocation id) {
        String ns = id.getNamespace().toLowerCase(java.util.Locale.ROOT);
        String caminho = id.getPath().toLowerCase(java.util.Locale.ROOT);
        int nota = 1;                       // qualquer dimensão de mod já serve de reserva
        if (ns.contains("ronova")) {
            nota += 100;
        }
        if (caminho.contains("realm") || caminho.contains("reino")) {
            nota += 40;
        }
        if (caminho.contains("afterlife") || caminho.contains("alem")
                || caminho.contains("além")) {
            nota += 20;
        }
        return nota;
    }

    /** a dimensão (já resolvida pelo nome) ou {@code null}. */
    public static ServerLevel nivel(MinecraftServer servidor) {
        ResourceKey<Level> k = chave(servidor);
        return k == null ? null : servidor.getLevel(k);
    }

    /** o nível é o reino do outro mod? */
    public static boolean eh(Level level) {
        if (level == null) {
            return false;
        }
        try {
            return ehChave(level.dimension())
                    || (level.dimension().equals(chave(level.getServer())) && chave(level.getServer()) != null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** a chave é o reino do outro mod? (usado no evento de troca de dimensão) */
    public static boolean ehChave(ResourceKey<Level> k) {
        if (k == null || escolhida == null) {
            return false;
        }
        return escolhida.equals(k);
    }

    /** (5.30.0) o ADM fixou a dimensão na mão (ou soltou de volta pro automático). */
    public static void fixar(ResourceLocation id) {
        if (id == null) {
            fixadaNaMao = false;
            escolhida = null;
            procurou = false;
            return;
        }
        fixadaNaMao = true;
        escolhida = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        procurou = true;
    }

    /** o nome da dimensão escolhida, para mostrar nos comandos. */
    public static String nome() {
        return escolhida == null ? "(nenhuma)" : escolhida.location().toString();
    }

    // ---------------------------------------------------------------- entrada

    /**
     * Manda o jogador para o reino (o novo labirinto), num ponto com chão.
     *
     * @return false quando não existe dimensão de outro mod (aí o Juízo usa o labirinto antigo)
     */
    public static boolean entrar(ServerPlayer p) {
        MinecraftServer servidor = p.getServer();
        ServerLevel lv = nivel(servidor);
        if (lv == null) {
            return false;
        }
        try {
            BlockPos alvo = pontoSeguro(lv);
            p.stopRiding();
            p.teleportTo(lv, alvo.getX() + 0.5D, alvo.getY() + 0.1D, alvo.getZ() + 0.5D, 0.0F, 0.0F);
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: entrar no reino do outro mod falhou", t);
            return false;
        }
    }

    /**
     * Um lugar de pé: procura perto do spawn uma coluna com chão sólido e dois blocos de ar.
     * (o Reino é um vazio com ilhas flutuando — cair no vazio seria perder o jogador)
     */
    private static BlockPos pontoSeguro(ServerLevel lv) {
        BlockPos spawn = lv.getSharedSpawnPos();
        for (int r = 0; r <= 40; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;   // só o anel de raio r
                    }
                    int x = spawn.getX() + dx, z = spawn.getZ() + dz;
                    int topo;
                    try {
                        topo = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    } catch (Throwable t) {
                        continue;
                    }
                    for (int y = Math.min(topo, lv.getMaxBuildHeight() - 3);
                            y > lv.getMinBuildHeight() + 2; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (lv.getBlockState(p.below()).isSolid()
                                && lv.isEmptyBlock(p) && lv.isEmptyBlock(p.above())) {
                            return p;
                        }
                    }
                }
            }
        }
        return spawn;   // não achou: o spawn do próprio mod (o melhor palpite honesto)
    }
}
