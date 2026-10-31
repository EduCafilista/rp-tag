package dev.rptag.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * (5.1.0) LEITOR DA MALHA BINÁRIA gerada por {@code arte/importar_glb.py}.
 *
 * <p>O Minecraft não carrega {@code .glb}, então o modelo do dono é convertido no
 * arquivo {@code geo/mascara_glb.bin}, <b>no espaço da cabeça</b> (pixels do
 * modelo: x ±2,7 · y −7,45..−0,45 · z −5,05..−3,75). O mesmo arquivo serve para a
 * máscara vestida (camada) e para o item na mão — (5.2.1) não existe mais uma
 * segunda malha normalizada: ela só duplicava 769 KB no jar.
 *
 * <p>Formato: magic {@code RPTM}, versão, nº de vértices e os arrays
 * <b>planar</b> (todas as X, depois todas as Y…) em <b>big-endian</b> — é o que o
 * {@link DataInputStream} lê. Trocar isso embaralha a malha inteira.
 */
public final class MalhaBin {

    public final int n;                       // vértices (já expandidos em triângulos)
    public final float[] px, py, pz;          // posição
    public final float[] nx, ny, nz;          // normal
    public final float[] u, v;                // textura

    private MalhaBin(int n) {
        this.n = n;
        px = new float[n]; py = new float[n]; pz = new float[n];
        nx = new float[n]; ny = new float[n]; nz = new float[n];
        u = new float[n]; v = new float[n];
    }

    /** Lê do classpath. Devolve {@code null} se o arquivo faltar ou estiver estranho. */
    public static MalhaBin carregar(String caminho) {
        InputStream cru = MalhaBin.class.getResourceAsStream(caminho);
        if (cru == null) {
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: {} nao esta no jar", caminho);
            return null;
        }
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(cru))) {
            byte[] magic = new byte[4];
            d.readFully(magic);
            if (magic[0] != 'R' || magic[1] != 'P' || magic[2] != 'T' || magic[3] != 'M') {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: cabecalho estranho em {}", caminho);
                return null;
            }
            int versao = d.readInt(), n = d.readInt();
            if (versao != 1 || n < 3 || n > 400000) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: {} invalido (versao {}, {} vertices)", caminho, versao, n);
                return null;
            }
            MalhaBin m = new MalhaBin(n);
            for (int i = 0; i < n; i++) m.px[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.py[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.pz[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.nx[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.ny[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.nz[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.u[i] = d.readFloat();
            for (int i = 0; i < n; i++) m.v[i] = d.readFloat();
            return m;
        } catch (IOException | RuntimeException e) {
            dev.rptag.RPTagMod.LOGGER.error("RP Tag: falha ao ler " + caminho + " (o jogo segue)", e);
            return null;
        }
    }
}
