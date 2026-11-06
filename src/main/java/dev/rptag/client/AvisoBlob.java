package dev.rptag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.16.1) MANCHA SUAVE — o "gradiente radial" que o desenho do GUI não tem.
 *
 * <p>O protótipo do dono pinta a névoa, os halos e as luzes com {@code RadialGradient}. Tentar
 * fazer isso com triângulos dá dois defeitos que aparecem na tela: DEGRAU (faixas de alfa) e
 * CATAVENTO (as arestas dos leques). A saída é uma textura que já é um degradê radial —
 * {@code rptag:textures/misc/glow.png}, branca, gerada com alfa em smoothstep (1 no centro,
 * 0 na borda) — multiplicada pela cor e pelo alfa que a carta pedir.
 *
 * <p><b>NUNCA</b> usar {@code minecraft:textures/misc/shadow.png} aqui: aquela textura é PRETA
 * (é sombra), então o brilho sairia como mancha preta.
 *
 * <p>Desenha com o shader de textura (POSITION_TEX_COLOR), então quem chama precisa fechar o
 * buffer de geometria antes (o {@link AvisoRitual.Buf}) e reabrir depois.
 */
public final class AvisoBlob {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("rptag", "textures/misc/glow.png");

    private static boolean filtroOk;

    private AvisoBlob() {
    }

    /**
     * Desenha uma mancha radial suave.
     *
     * @param x canto esquerdo (em pixels da tela)
     * @param y canto de cima
     * @param w largura
     * @param h altura
     * @param rgb cor (sem alfa)
     * @param a alfa no centro (0..1)
     */
    public static void desenhar(GuiGraphics g, float x, float y, float w, float h, int rgb, float a) {
        if (a <= 0.004f || w <= 0.6f || h <= 0.6f) {
            return;
        }
        if (!filtroOk) {
            // a textura é 64x64 e é esticada por cima da tela toda: sem BLUR ela viraria
            // quadriculado de 12 px. Uma vez só basta.
            net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(TEX).setFilter(true, false);
            filtroOk = true;
        }
        int cor = ((int) (Math.min(1f, a) * 255) << 24) | (rgb & 0xFFFFFF);
        var pose = g.pose().last().pose();
        // NÃO mexe no blend: se quem chamou está no modo aditivo (glow), a mancha some
        // corretamente somando luz em vez de trocar para o blend normal no meio do desenho.
        g.flush();                     // fecha o que o GuiGraphics tinha na fila, senão sai fora de ordem
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(pose, x, y + h, 0).setUv(0f, 1f).setColor(cor);
        bb.addVertex(pose, x + w, y + h, 0).setUv(1f, 1f).setColor(cor);
        bb.addVertex(pose, x + w, y, 0).setUv(1f, 0f).setColor(cor);
        bb.addVertex(pose, x, y, 0).setUv(0f, 0f).setColor(cor);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        // volta o shader de cor para a geometria que vem depois
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }
}
