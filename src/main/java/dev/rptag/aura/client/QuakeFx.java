package dev.rptag.aura.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Terremoto (visual):
 *  - tela balançando como se o jogador corresse: balanço de câmera (cabeceio + giro lateral + rajadas), pulso de FOV
 *  - céu carmesim em degradê (zênite escuro -> horizonte alaranjado), estrelas avermelhadas, neblina carmesim
 *  - LUA DE SANGUE grande, com halo, crateras, subindo devagar durante o evento
 *  - leve filtro vermelho + vinheta na tela, poeira caindo e estrondos graves
 * Só no Overworld. Entra e sai suavemente (fade).
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class QuakeFx {
    private static final float TAU = (float) (Math.PI * 2.0);

    private static boolean active = false;
    private static float intensity = 1f;
    private static float level = 0f;       // 0..1 (fade)
    private static float outdoor = 1f;     // 0..1 (céu visível)
    private static int activeTicks = 0;

    private static VertexBuffer dome, stars, moon;

    /** (5.76.0) o ceu/camera novos estao no ar? (o ceu antigo do terremoto cede a vez para ele) */
    public static boolean ligado() { return level > 0.01f; }

    public static void setState(boolean on, float inten) {
        active = on;
        intensity = Mth.clamp(inten, 0.2f, 3f);
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) { level = 0f; activeTicks = 0; return; }
        float target = active ? 1f : 0f;
        float step = active ? 0.012f : 0.006f;                       // entra em ~4 s, sai em ~8 s
        level += Mth.clamp(target - level, -step, step);
        if (active) activeTicks++; else if (level <= 0f) activeTicks = 0;
        if (level < 0.02f || lv.dimension() != Level.OVERWORLD) return;

        BlockPos bp = mc.player.blockPosition().above();
        float sky = lv.getBrightness(LightLayer.SKY, bp) / 15f;
        outdoor += (sky - outdoor) * 0.1f;

        RandomSource rnd = lv.random;
        int n = 1 + (int) (level * intensity * 2f);
        for (int i = 0; i < n; i++) {                                 // poeira/pedriscos caindo
            double x = mc.player.getX() + (rnd.nextDouble() - 0.5) * 18, z = mc.player.getZ() + (rnd.nextDouble() - 0.5) * 18;
            double y = mc.player.getY() + 1.5 + rnd.nextDouble() * 5.0;
            lv.addParticle(new DustParticleOptions(new Vector3f(0.40f, 0.26f, 0.22f), 1.4f), x, y, z, 0, -0.05, 0);
        }
        if (activeTicks % 12 == 0) {                                  // estrondos graves
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDER_DRAGON_FLAP,
                    0.30f + rnd.nextFloat() * 0.15f, 0.6f * level * Math.min(1.5f, intensity)));
        }
    }

    // ------------------------------------------------------------ tela balançando
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (level < 0.01f) return;
        float pt = (float) e.getPartialTick();
        Minecraft mc = Minecraft.getInstance();
        float tm = (mc.level == null ? 0f : mc.level.getGameTime()) + pt;
        float q = level * intensity;
        float burst = (float) Math.pow(Math.max(0f, Mth.sin(tm * 0.07f)), 6) * 3f + 1f;    // rajadas mais fortes de tempos em tempos
        float step = tm * 0.75f;                                                           // cadência de corrida
        float bob = Math.abs(Mth.sin(step)) * 1.2f - 0.6f;                                 // cabeceio (pitch)
        float sway = Mth.sin(step * 0.5f) * 2.4f;                                          // tombo lateral (roll)
        float jx = Mth.sin(tm * 3.7f) * Mth.sin(tm * 1.9f) * 0.8f;                         // tremor irregular
        float jy = Mth.sin(tm * 4.3f + 1f) * Mth.sin(tm * 2.3f) * 0.7f;
        e.setPitch(e.getPitch() + (bob + jy) * q * burst);
        e.setYaw(e.getYaw() + (Mth.sin(tm * 0.52f + 1f) * 1.1f + jx) * q * burst);
        e.setRoll(e.getRoll() + (sway + jx * 0.8f) * q * burst);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (level < 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        float tm = (mc.level == null ? 0f : mc.level.getGameTime()) + (float) e.getPartialTick();
        e.setFOV(e.getFOV() + Mth.sin(tm * 0.75f) * 1.6f * level * intensity);
    }

    // --------------------------------------------------------- neblina carmesim
    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (level < 0.01f || mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        float k = level * (0.30f + 0.70f * outdoor) * 0.92f;
        e.setRed(Mth.lerp(k, e.getRed(), 0.55f));
        e.setGreen(Mth.lerp(k, e.getGreen(), 0.06f));
        e.setBlue(Mth.lerp(k, e.getBlue(), 0.07f));
    }

    // ------------------------------------------------ filtro vermelho + vinheta
    @SubscribeEvent
    public static void gui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (level < 0.01f || mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float tm = mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float a = level * (0.85f + 0.15f * Mth.sin(tm * 0.1f));
        g.fill(0, 0, w, h, argb(a * 0.09f, 150, 0, 10));
        g.fillGradient(0, 0, w, (int) (h * 0.25f), argb(a * 0.42f, 40, 0, 0), 0x00000000);
        g.fillGradient(0, (int) (h * 0.75f), w, h, 0x00000000, argb(a * 0.42f, 40, 0, 0));
    }

    private static int argb(float a, int r, int g, int b) {
        return (Mth.clamp((int) (a * 255f), 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }

    // ----------------------------------------------------- céu carmesim + lua de sangue
    @SubscribeEvent
    public static void sky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || level < 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null || lv.dimension() != Level.OVERWORLD) return;
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return;   // debaixo d'água/lava: sem céu
        ShaderInstance sh = GameRenderer.getPositionColorShader();
        if (sh == null) return;
        ensureMeshes();

        Matrix4f mv = e.getModelViewMatrix(), proj = e.getProjectionMatrix();
        float tm = lv.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.setShaderColor(1f, 1f, 1f, level);
        dome.bind(); dome.drawWithShader(mv, proj, sh); VertexBuffer.unbind();

        RenderSystem.setShaderColor(1f, 1f, 1f, level * (0.75f + 0.25f * Mth.sin(tm * 0.1f)));
        stars.bind(); stars.drawWithShader(mv, proj, sh); VertexBuffer.unbind();

        // a lua sobe devagar durante o evento
        float theta = 68f - 14f * (1f - (float) Math.exp(-activeTicks / 1200.0));
        Matrix4f mm = new Matrix4f(mv).rotateY((float) Math.toRadians(-90)).rotateX((float) Math.toRadians(theta));
        RenderSystem.setShaderColor(1f, 1f, 1f, level);
        moon.bind(); moon.drawWithShader(mm, proj, sh); VertexBuffer.unbind();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // --------------------------------------------------------------------- malhas
    private static void ensureMeshes() {
        if (dome != null) return;
        dome = buildDome();
        stars = buildStars();
        moon = buildMoon();
    }

    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static VertexBuffer upload(BufferBuilder bb) {
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(bb.buildOrThrow());
        VertexBuffer.unbind();
        return vb;
    }

    private static void v(BufferBuilder bb, float x, float y, float z, float[] c, float a) {
        bb.addVertex(x, y, z).setColor(c[0], c[1], c[2], a);
    }

    // degradê do céu por latitude: abaixo do horizonte escuro -> horizonte alaranjado -> carmesim -> zênite quase vinho
    private static final float[] K_LAT = {-14f, 0f, 10f, 30f, 60f, 90f};
    private static final float[][] K_COL = {
            {0.40f, 0.06f, 0.04f}, {0.82f, 0.17f, 0.07f}, {0.66f, 0.07f, 0.05f},
            {0.46f, 0.04f, 0.05f}, {0.28f, 0.02f, 0.04f}, {0.18f, 0.01f, 0.03f}};

    private static float[] skyColor(float lat) {
        for (int i = 0; i < K_LAT.length - 1; i++) {
            if (lat <= K_LAT[i + 1]) {
                float t = Mth.clamp((lat - K_LAT[i]) / (K_LAT[i + 1] - K_LAT[i]), 0f, 1f);
                return new float[]{Mth.lerp(t, K_COL[i][0], K_COL[i + 1][0]), Mth.lerp(t, K_COL[i][1], K_COL[i + 1][1]),
                        Mth.lerp(t, K_COL[i][2], K_COL[i + 1][2])};
            }
        }
        return K_COL[K_COL.length - 1];
    }

    private static VertexBuffer buildDome() {
        BufferBuilder bb = begin();
        final float R = 110f;
        final int LON = 40;
        float[] lats = {-14, -4, 0, 4, 10, 18, 28, 40, 55, 70, 90};
        for (int i = 0; i < lats.length - 1; i++) {
            float la0 = (float) Math.toRadians(lats[i]), la1 = (float) Math.toRadians(lats[i + 1]);
            float[] c0 = skyColor(lats[i]), c1 = skyColor(lats[i + 1]);
            for (int j = 0; j < LON; j++) {
                float lo0 = j * TAU / LON, lo1 = (j + 1) * TAU / LON;
                v(bb, R * Mth.cos(la0) * Mth.cos(lo0), R * Mth.sin(la0), R * Mth.cos(la0) * Mth.sin(lo0), c0, 1f);
                v(bb, R * Mth.cos(la0) * Mth.cos(lo1), R * Mth.sin(la0), R * Mth.cos(la0) * Mth.sin(lo1), c0, 1f);
                v(bb, R * Mth.cos(la1) * Mth.cos(lo1), R * Mth.sin(la1), R * Mth.cos(la1) * Mth.sin(lo1), c1, 1f);
                v(bb, R * Mth.cos(la1) * Mth.cos(lo0), R * Mth.sin(la1), R * Mth.cos(la1) * Mth.sin(lo0), c1, 1f);
            }
        }
        return upload(bb);
    }

    private static VertexBuffer buildStars() {
        BufferBuilder bb = begin();
        Random r = new Random(42);
        float[] col = {1f, 0.78f, 0.78f};
        for (int i = 0; i < 240; i++) {
            float y = 0.08f + r.nextFloat() * 0.92f, rad = Mth.sqrt(1f - y * y), phi = r.nextFloat() * TAU;
            float px = rad * Mth.cos(phi) * 105f, py = y * 105f, pz = rad * Mth.sin(phi) * 105f;
            float len = Mth.sqrt(px * px + py * py + pz * pz);
            // base tangente ao ponto
            float ax = 0f, ay = 1f, az = 0f;
            float t1x = py * az - pz * ay, t1y = pz * ax - px * az, t1z = px * ay - py * ax;
            float l1 = Mth.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
            t1x /= l1; t1y /= l1; t1z /= l1;
            float t2x = (py * t1z - pz * t1y) / len, t2y = (pz * t1x - px * t1z) / len, t2z = (px * t1y - py * t1x) / len;
            float s = 0.25f + r.nextFloat() * 0.55f, a = 0.25f + r.nextFloat() * 0.6f;
            v(bb, px - t1x * s - t2x * s, py - t1y * s - t2y * s, pz - t1z * s - t2z * s, col, a);
            v(bb, px + t1x * s - t2x * s, py + t1y * s - t2y * s, pz + t1z * s - t2z * s, col, a);
            v(bb, px + t1x * s + t2x * s, py + t1y * s + t2y * s, pz + t1z * s + t2z * s, col, a);
            v(bb, px - t1x * s + t2x * s, py - t1y * s + t2y * s, pz - t1z * s + t2z * s, col, a);
        }
        return upload(bb);
    }

    /** Disco radial no plano y=100 (centro -> borda), em `seg` segmentos. */
    private static void disc(BufferBuilder bb, float cx, float cz, float rad, float[] cc, float ca, float[] ce, float ea) {
        final int seg = 28;
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            v(bb, cx, 100f, cz, cc, ca);
            v(bb, cx, 100f, cz, cc, ca);
            v(bb, cx + Mth.cos(a1) * rad, 100f, cz + Mth.sin(a1) * rad, ce, ea);
            v(bb, cx + Mth.cos(a0) * rad, 100f, cz + Mth.sin(a0) * rad, ce, ea);
        }
    }

    private static VertexBuffer buildMoon() {
        BufferBuilder bb = begin();
        float[] glow = {0.90f, 0.10f, 0.08f}, body = {0.88f, 0.14f, 0.08f}, limb = {0.52f, 0.03f, 0.04f};
        float[] dark = {0.26f, 0.01f, 0.02f}, bright = {0.96f, 0.32f, 0.14f};
        disc(bb, 0, 0, 72f, glow, 0.10f, glow, 0f);                 // halo largo e suave
        disc(bb, 0, 0, 46f, glow, 0.22f, glow, 0f);
        disc(bb, 0, 0, 21f, body, 1f, limb, 1f);                    // corpo: centro mais vivo, borda escurecida
        float[][] craters = {{-7, 5, 5, .45f}, {8, -4, 4, .40f}, {2, 10, 3.5f, .40f}, {-10, -8, 3, .35f},
                {10, 9, 2.5f, .30f}, {-2, -3, 6, .25f}, {4, -12, 3, .35f}};
        for (float[] c : craters) disc(bb, c[0], c[1], c[2], dark, c[3], dark, c[3] * 0.4f);
        disc(bb, -5, -7, 3f, bright, 0.25f, bright, 0f);            // manchas claras
        disc(bb, 6, 2, 2.5f, bright, 0.22f, bright, 0f);
        return upload(bb);
    }

    private QuakeFx() {}
}
