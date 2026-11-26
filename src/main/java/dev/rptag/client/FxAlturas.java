package dev.rptag.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * (5.77.0) OTIMIZACAO — o ANEL DE ALTURAS do chao, em cache.
 *
 * <p>Os aneis das auras (radiacao e campo gravitacional) seguem o relevo: para desenhar o chao
 * direito eles perguntam a altura do mundo em 96 pontos, e faziam isso A CADA QUADRO, por
 * jogador (sao 5.760 consultas por segundo por aura a 60 FPS). Consulta de heightmap varre
 * coluna de chunk — e caro.
 *
 * <p>Aqui a mesma resposta e reaproveitada: o anel e recalculado so a cada 4 ticks, ou na hora
 * se o jogador andou mais de 1 bloco. O desenho continua igual (o chao nao muda 15 vezes por
 * segundo para quem esta andando).
 */
public final class FxAlturas {

    /** pontos do anel (o mesmo 96 que os desenhos ja usavam). */
    public static final int N = 96;

    private static final Map<UUID, Dados> CACHE = new HashMap<>();

    private static final class Dados {
        float raio;
        double x, y, z;
        long tick;
        float[] ys;
    }

    private FxAlturas() {}

    /**
     * Alturas do anel de raio `raio` em volta do jogador, RELATIVAS ao Y dele (+0.1).
     * O array devolvido tem N+1 posicoes e pode ser lido ate o proximo recalculo.
     */
    public static float[] anel(Player p, float raio) {
        Level lv = p.level();
        long agora = lv == null ? 0L : lv.getGameTime();
        UUID id = p.getUUID();
        Dados d = CACHE.get(id);

        boolean refazer = d == null
                || Math.abs(d.raio - raio) > 0.01f
                || agora - d.tick >= 4
                || Math.abs(d.x - p.getX()) > 1.0 || Math.abs(d.z - p.getZ()) > 1.0
                || Math.abs(d.y - p.getY()) > 1.0;

        if (!refazer) return d.ys;

        if (d == null) { d = new Dados(); d.ys = new float[N + 1]; CACHE.put(id, d); }
        d.raio = raio; d.x = p.getX(); d.y = p.getY(); d.z = p.getZ(); d.tick = agora;

        float py = (float) p.getY();
        int minY = lv == null ? Integer.MIN_VALUE : lv.getMinBuildHeight();
        for (int i = 0; i <= N; i++) {
            float a = i * (float) (Math.PI * 2.0) / N;
            if (lv == null) { d.ys[i] = 0.1f; continue; }
            int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(p.getX() + Mth.cos(a) * raio), Mth.floor(p.getZ() + Mth.sin(a) * raio));
            d.ys[i] = (h <= minY ? py : h) - py + 0.1f;
        }
        limpar(agora);
        return d.ys;
    }

    /** Alturas em cache para pontos fixos ou entidades sem UUID de Player (ex: Rei de Amarelo). */
    private static final Map<Long, Dados> CACHE_CHAVE = new HashMap<>();

    public static float[] anelPos(Level lv, long chave, double cx, double cy, double cz, float raio) {
        long agora = lv == null ? 0L : lv.getGameTime();
        Dados d = CACHE_CHAVE.get(chave);

        boolean refazer = d == null
                || Math.abs(d.raio - raio) > 0.01f
                || agora - d.tick >= 4
                || Math.abs(d.x - cx) > 0.8 || Math.abs(d.z - cz) > 0.8
                || Math.abs(d.y - cy) > 1.0;

        if (!refazer) return d.ys;

        if (d == null) { d = new Dados(); d.ys = new float[N + 1]; CACHE_CHAVE.put(chave, d); }
        d.raio = raio; d.x = cx; d.y = cy; d.z = cz; d.tick = agora;

        float py = (float) cy;
        int minY = lv == null ? Integer.MIN_VALUE : lv.getMinBuildHeight();
        for (int i = 0; i <= N; i++) {
            float a = i * (float) (Math.PI * 2.0) / N;
            if (lv == null) { d.ys[i] = 0.04f; continue; }
            int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(cx + Mth.cos(a) * raio), Mth.floor(cz + Mth.sin(a) * raio));
            d.ys[i] = (h <= minY + 1 || Math.abs(h - cy) > 3.5) ? 0.04f : (h - py + 0.04f);
        }
        if (CACHE_CHAVE.size() > 48) {
            CACHE_CHAVE.entrySet().removeIf(en -> agora - en.getValue().tick > 200);
        }
        return d.ys;
    }

    /** tira do cache quem saiu do mundo (senao o mapa cresce a cada reconexao). */
    private static void limpar(long agora) {
        if (CACHE.size() <= 24) return;
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<UUID, Dados>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Dados> en = it.next();
            boolean velho = agora - en.getValue().tick > 200;
            boolean offline = mc.level == null || mc.level.getPlayerByUUID(en.getKey()) == null;
            if (velho || offline) it.remove();
        }
    }
}
