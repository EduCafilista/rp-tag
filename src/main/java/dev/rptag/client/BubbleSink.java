package dev.rptag.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * (3.60.0) Liga a geometria procedural ({@link BubbleGeometry}) ao Minecraft:
 * cada triangulo vira um quad DEGENERADO (v3 repetido) num buffer
 * POSITION_COLOR (RenderType.gui()). O sentido de giro e normalizado aqui —
 * quem gera os triangulos nao precisa se preocupar com culling.
 *
 * <p>Regra de giro = a mesma do quad() do mundo e do GuiGraphics.fill:
 * (x1,y1) -> (x1,y2) -> (x2,y2) com Y pra baixo (produto vetorial &lt; 0).
 */
final class BubbleSink implements BubbleGeometry.Sink {

    private final Matrix4f matrix;
    private final VertexConsumer buf;
    private final float zShift;

    BubbleSink(Matrix4f matrix, VertexConsumer buf, float zShift) {
        this.matrix = matrix;
        this.buf = buf;
        this.zShift = zShift;
    }

    @Override
    public void tri(float z,
            float x1, float y1, int c1,
            float x2, float y2, int c2,
            float x3, float y3, int c3) {
        float cross = (x2 - x1) * (y3 - y2) - (y2 - y1) * (x3 - x2);
        if (cross == 0.0F || Float.isNaN(cross)) {
            return; // triangulo degenerado: nada a desenhar
        }
        float zz = z + zShift;
        if (cross > 0.0F) { // giro invertido: troca o 2o com o 3o vertice
            vertex(x1, y1, zz, c1);
            vertex(x3, y3, zz, c3);
            vertex(x2, y2, zz, c2);
            vertex(x2, y2, zz, c2);
        } else {
            vertex(x1, y1, zz, c1);
            vertex(x2, y2, zz, c2);
            vertex(x3, y3, zz, c3);
            vertex(x3, y3, zz, c3);
        }
    }

    private void vertex(float x, float y, float z, int argb) {
        buf.addVertex(matrix, x, y, z).setColor(argb);
    }
}
