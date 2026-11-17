package dev.rptag.aura.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * RenderTypes das auras. "HULL" desenha só as faces de TRÁS do modelo do jogador (cull da frente):
 * assim o brilho/sombra aparece apenas FORA da silhueta, contornando o corpo sem cobrir a pele.
 */
public final class AuraRenderTypes extends RenderType {
    /**
     * (5.64.0) textura branca DO MOD (o auditores do mod cobram que toda textura citada exista no
     * jar — a textura branca do vanilla nao existe dentro dos nossos assets).
     */
    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "textures/misc/branco.png");

    private static final RenderStateShard.LayeringStateShard CULL_FRONT = new RenderStateShard.LayeringStateShard(
            "aura_cull_front",
            () -> { RenderSystem.enableCull(); GL11.glCullFace(GL11.GL_FRONT); },
            () -> GL11.glCullFace(GL11.GL_BACK));

    /** Brilho aditivo, só fora da silhueta (aura dourada). */
    public static final RenderType GLOW_HULL = create("aura_glow_hull",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(WHITE, false, false))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(CULL_FRONT)
                    .createCompositeState(false));

    /** Tinge aditivo por cima do corpo (pele alaranjada). */
    public static final RenderType GLOW_TINT = eyes(WHITE);

    /** Sombra translúcida (preto), só fora da silhueta (aura do vazio). */
    public static final RenderType SHADOW_HULL = create("aura_shadow_hull",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(WHITE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(CULL_FRONT)
                    .createCompositeState(false));

    /** Quads coloridos translúcidos sem textura (tentáculos de tinta). */
    public static final RenderType INK = create("aura_ink",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private AuraRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean so,
                            Runnable setup, Runnable clear) {
        super(n, f, m, s, a, so, setup, clear);
        throw new IllegalStateException("Não instanciar");
    }
}
