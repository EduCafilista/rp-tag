package dev.rptag.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import dev.rptag.VazioPoderPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * (5.29.0) <b>OS EFEITOS DOS PODERES DO VAZIO — e o PORTAL DO JUÍZO.</b>
 *
 * <p>O dono pediu (pedido 86): <i>"os poderes do vazio pode usar igual o do bicho desse mod"</i>
 * — o mod de referência é o <b>Ronova: Ruler of Death</b>, cuja assinatura visual é um
 * <b>raio-lança escuro com fio carmesim</b> que atravessa o mapa e explode onde bate, orbes
 * carmesim e a onda da morte. E pediu também <i>"faça uma portal mais bonito pfvr"</i> e
 * <i>"usa o portal desse mod como base"</i>.
 *
 * <p>Por que geometria e não partícula: com as partículas no mínimo (ou com Sodium/Iris) o
 * jogador não veria nada — o mesmo motivo que tirou o portal da praça das partículas
 * (pedido 50). Tudo aqui é {@code RenderType.debugQuads()} com cor por vértice.
 *
 * <p>Regra de ouro do mod: erro aqui faz o efeito sumir, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioPoderFx {

    private VazioPoderFx() {
    }

    /** um efeito vivo na tela: tipo, origem, alvo, nasceu (ns) e quanto dura (ns). */
    private record Efeito(int tipo, Vec3 de, Vec3 ate, long nasceu, long dura) {
    }

    private static final List<Efeito> VIVOS = new ArrayList<>();

    /** o portal do julgamento fica aceso sozinho até isso passar (o ADM reabre). */
    private static Efeito portalFixo;

    /** o cliente recebeu o pacote. */
    public static void receber(VazioPoderPayload p) {
        try {
            Vec3 de = new Vec3(p.ox(), p.oy(), p.oz());
            Vec3 ate = new Vec3(p.tx(), p.ty(), p.tz());
            long agora = System.nanoTime();
            switch (p.tipo()) {
                case 0 -> VIVOS.add(new Efeito(0, de, ate, agora, 700_000_000L));   // raio
                case 1 -> VIVOS.add(new Efeito(1, de, ate, agora, 900_000_000L));   // orbe
                case 2 -> VIVOS.add(new Efeito(2, de, ate, agora, 1_600_000_000L)); // onda
                case 3 -> VIVOS.add(new Efeito(3, de, ate, agora, 450_000_000L));   // olhos
                case 4 -> portalFixo = new Efeito(4, de, ate, agora, 8L * 60L * 1_000_000_000L);
                case 5 -> VIVOS.add(new Efeito(5, de, ate, agora, 1_400_000_000L)); // chuva de penas
                case 6 -> VIVOS.add(new Efeito(6, de, ate, agora, 2_600_000_000L)); // OLHO QUE BANE
                default -> {
                    return;
                }
            }
            // (5.31.0) TODO poder acende a aura em volta do corpo (era só raio/orbe)
            VazioAuraFx.pulsar();
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: efeito de poder do vazio falhou", t);
        }
    }

    /** o portal do juízo apaga (o ADM fecha a sala) — hoje só o tempo apaga. */
    public static void apagarPortal() {
        portalFixo = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            if (VIVOS.isEmpty() && portalFixo == null) {
                return;
            }
            long agora = System.nanoTime();
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            var mat = pose.last().pose();

            for (Iterator<Efeito> it = VIVOS.iterator(); it.hasNext();) {
                Efeito e = it.next();
                float vida = (agora - e.nasceu()) / (float) e.dura();
                if (vida >= 1.0F) {
                    it.remove();
                    continue;
                }
                desenhar(vc, mat, e, vida);
            }
            if (portalFixo != null) {
                float vida = (agora - portalFixo.nasceu()) / (float) portalFixo.dura();
                if (vida >= 1.0F) {
                    portalFixo = null;
                } else {
                    portal(vc, mat, portalFixo.de(), (agora - portalFixo.nasceu()) / 1_000_000_000.0, vida);
                }
            }
            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable ignorado) {
            // efeito é enfeite
        }
    }

    // ---------------------------------------------------------------- efeitos

    private static void desenhar(VertexConsumer vc, org.joml.Matrix4f mat, Efeito e, float vida) {
        switch (e.tipo()) {
            case 0 -> {   // RAIO: a lança escura com o fio carmesim, some rápido
                float alfa = (1.0F - vida) * (vida < 0.15F ? vida / 0.15F : 1.0F);
                tubo(vc, mat, e.de(), e.ate(), 0.16F, cor(0.85F * alfa, 0x1A, 0x03, 0x06));
                tubo(vc, mat, e.de(), e.ate(), 0.055F, cor(alfa, 0xFF, 0x30, 0x3C));
                // a explosão onde bate
                float raio = 0.6F + vida * 5.0F;
                esfera(vc, mat, e.ate(), raio, cor(0.55F * (1.0F - vida), 0xC0, 0x10, 0x1E));
            }
            case 1 -> {   // ORBE: bolha carmesim + rastro
                float alfa = 1.0F - vida;
                esfera(vc, mat, e.ate(), 0.75F + 0.35F * (float) Math.sin(vida * 9.0), cor(0.8F * alfa, 0xD8, 0x18, 0x28));
                tubo(vc, mat, e.de(), e.ate(), 0.07F, cor(0.5F * alfa, 0xFF, 0x40, 0x50));
            }
            case 2 -> {   // ONDA DA MORTE: anel que varre o chão
                float raio = 1.0F + vida * 17.0F;
                float alfa = (1.0F - vida) * 0.75F;
                anel(vc, mat, e.ate(), raio, cor(alfa, 0xB0, 0x10, 0x1C), 48);
                anel(vc, mat, e.ate(), raio * 0.78F, cor(alfa * 0.6F, 0x60, 0x06, 0x0C), 48);
            }
            case 3 -> {   // OLHOS: riscos finos, vários alvos
                float alfa = 1.0F - vida;
                tubo(vc, mat, e.de(), e.ate(), 0.03F, cor(alfa, 0xFF, 0x50, 0x5A));
            }
            case 5 -> chuvaDePenas(vc, mat, e, vida);
            case 6 -> olhoQueBane(vc, mat, e, vida);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- portal

    /**
     * <b>O PORTAL DO JUÍZO</b> (5.29.0) — refeito do zero.
     *
     * <p>Antes era um retângulo de escuridão. Agora é um <b>olho</b> em pé: moldura de pedra
     * negra, pálpebra carmesim, íris escura pulsando e penas girando em volta — o visual do
     * Ronova (o céu rasgado com o olho) aplicado ao portal da sala.
     */
    private static void portal(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 base,
            double t, float vida) {
        float c = 0.5F;                       // centro do portal
        float y0 = (float) base.y;
        float x0 = (float) base.x;
        float z0 = (float) base.z;
        float altura = 3.6F;
        float larguraMeia = 2.2F;

        // moldura: dois pilares e o arco (pedra quase preta)
        int pedra = cor(1.0F, 0x14, 0x10, 0x16);
        int pedraClara = cor(1.0F, 0x2A, 0x20, 0x28);
        quadZ(vc, mat, x0 - larguraMeia - 0.45F, y0, x0 - larguraMeia - 0.05F, y0 + altura, z0, pedra);
        quadZ(vc, mat, x0 + larguraMeia + 0.05F, y0, x0 + larguraMeia + 0.45F, y0 + altura, z0, pedra);
        quadZ(vc, mat, x0 - larguraMeia - 0.45F, y0 + altura, x0 + larguraMeia + 0.45F, y0 + altura + 0.4F, z0, pedraClara);

        // a pálpebra: elipse achatada em pé (o "olho")
        int lados = 40;
        float abertura = 0.55F + 0.10F * (float) Math.sin(t * 2.2);   // abre e fecha devagar
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            float r0 = (float) (0.5 + 0.5 * Math.cos(a0));
            float r1 = (float) (0.5 + 0.5 * Math.cos(a1));
            float px0 = x0 + (float) Math.sin(a0) * larguraMeia;
            float px1 = x0 + (float) Math.sin(a1) * larguraMeia;
            float py0 = y0 + altura * 0.5F + (float) Math.sin(a0) * (altura * 0.5F) * abertura;
            float py1 = y0 + altura * 0.5F + (float) Math.sin(a1) * (altura * 0.5F) * abertura;
            // borda carmesim
            int fio = cor(0.55F + 0.4F * r0, 0xFF, 0x28, 0x34);
            quadZ(vc, mat, px0, py0 - 0.09F, px1, py1 + 0.09F, z0 - 0.02F, fio);
        }

        // a íris: disco escuro que respira, com um brilho vermelho no meio
        float pulso = 1.0F + 0.06F * (float) Math.sin(t * 3.4);
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            float rx = 0.85F * pulso, ry = 0.62F * pulso;
            float cx = x0, cy = y0 + altura * 0.5F;
            int corIris = cor(0.9F, 0x64, 0x08, 0x10);
            vc.addVertex(mat, cx, cy, z0 + 0.02F).setColor(corIris);
            vc.addVertex(mat, cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry, z0 + 0.02F).setColor(corIris);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry, z0 + 0.02F).setColor(corIris);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry, z0 + 0.02F).setColor(corIris);
            // a pupila
            int corPupila = cor(1.0F, 0x08, 0x00, 0x02);
            vc.addVertex(mat, cx, cy, z0 + 0.03F).setColor(corPupila);
            vc.addVertex(mat, cx + (float) Math.cos(a0) * 0.26F, cy + (float) Math.sin(a0) * 0.26F, z0 + 0.03F).setColor(corPupila);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * 0.26F, cy + (float) Math.sin(a1) * 0.26F, z0 + 0.03F).setColor(corPupila);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * 0.26F, cy + (float) Math.sin(a1) * 0.26F, z0 + 0.03F).setColor(corPupila);
        }

        // penas girando em volta (a aura da arte, em volta do portal)
        for (int i = 0; i < 12; i++) {
            double ang = i / 12.0 * Math.PI * 2.0 + t * 0.8;
            float px = x0 + (float) Math.cos(ang) * (larguraMeia + 0.75F);
            float py = y0 + altura * 0.5F + (float) Math.sin(ang) * (altura * 0.55F);
            float alfa = 0.35F + 0.35F * (float) Math.sin(t * 2.0 + i);
            billboard(vc, mat, px, py, z0 - 0.1F, 0.10F, cor(alfa, 0xFF, 0x38, 0x44));
        }

        // o chão do portal acende
        anel(vc, mat, base, 1.9F + 0.2F * (float) Math.sin(t * 2.6), cor(0.5F, 0xB0, 0x10, 0x1C), 36);
    }

    // ---------------------------------------------------------------- penas

    /**
     * (5.31.0) <b>CHUVA DE PENAS</b>: a cortina carmesim que cai onde ele olha.
     *
     * <p>Penas pretas descendo em coluna, com as pontas carmesim, terminando num clarão no chão
     * — o visual do Ronova ({"Rain of Feathers"}), na nossa paleta escura.
     */
    private static void chuvaDePenas(VertexConsumer vc, org.joml.Matrix4f mat, Efeito e, float vida) {
        float alfa = (1.0F - vida) * (vida < 0.1F ? vida / 0.1F : 1.0F);
        Vec3 centro = e.ate();
        for (int i = 0; i < 36; i++) {
            double ang = i * 2.399963;                     // espalha bonito (espiral áurea)
            double raio = 0.6 + (i % 7) * 1.1;
            double queda = (vida * 18.0 + i * 0.7) % 12.0;
            double x = centro.x + Math.cos(ang) * raio;
            double z = centro.z + Math.sin(ang) * raio;
            double y = centro.y + 11.0 - queda;
            float comp = 0.45F;
            pena(vc, mat, x, y, z, 0.05 * Math.cos(ang), -1.0 * comp, 0.05 * Math.sin(ang),
                    0.06F, cor(0.9F * alfa, 0x08, 0x02, 0x04), cor(0.7F * alfa, 0xE0, 0x18, 0x24));
        }
        anel(vc, mat, new Vec3(centro.x, centro.y + 0.1, centro.z), 1.0F + vida * 6.0F,
                cor(0.5F * alfa, 0xB0, 0x10, 0x1C), 40);
    }

    // ---------------------------------------------------------------- o olho que bane

    /**
     * (5.31.0) <b>O OLHO VERMELHO QUE BANE</b> — a animação que o dono pediu.
     *
     * <p>Fases (2,6 s no total):
     * <ol>
     *   <li><b>abre</b> (0 – 0,5 s): uma fenda horizontal preta rasga o ar, e a pálpebra carmesim
     *       se abre de dentro pra fora;</li>
     *   <li><b>encara</b> (0,5 – 1,6 s): a íris aparece, encolhe a pupila e se volta pro alvo,
     *       com penas girando em volta como cílios;</li>
     *   <li><b>arranca</b> (1,6 – 2,2 s): um clarão carmesim toma a tela e o alvo é sugado (a
     *       coluna de luz sobe no lugar onde ele estava);</li>
     *   <li><b>fecha</b> (2,2 – 2,6 s): a pálpebra desce e o olho some.</li>
     * </ol>
     */
    private static void olhoQueBane(VertexConsumer vc, org.joml.Matrix4f mat, Efeito e, float vida) {
        Vec3 centro = e.de().add(0, 1.4, 0);
        Vec3 alvo = e.ate();

        // abertura da pálpebra: 0 (fechado) → 1 (escancarado) → 0 (fecha)
        float abre;
        if (vida < 0.19F) {
            abre = vida / 0.19F;
        } else if (vida < 0.85F) {
            abre = 1.0F;
        } else {
            abre = Math.max(0.0F, 1.0F - (vida - 0.85F) / 0.15F);
        }
        float larg = 3.4F;
        float alt = 1.25F * abre;

        // 1) a FENDA (o rasgo preto na realidade)
        quadZ(vc, mat, (float) centro.x - larg * 1.15F, (float) centro.y - 0.03F,
                (float) centro.x + larg * 1.15F, (float) centro.y + 0.03F,
                (float) centro.z - 0.05F, cor(0.95F * abre, 0x00, 0x00, 0x00));

        // 2) a PÁLPEBRA (elipse achatada carmesim em volta)
        int lados = 48;
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            float x0 = (float) (centro.x + Math.sin(a0) * larg);
            float x1 = (float) (centro.x + Math.sin(a1) * larg);
            float y0 = (float) (centro.y + Math.sin(a0) * alt);
            float y1 = (float) (centro.y + Math.sin(a1) * alt);
            float esp = 0.10F * abre;
            quadZ(vc, mat, x0, y0 - esp, x1, y1 + esp, (float) centro.z - 0.02F,
                    cor(0.75F * abre, 0xC8, 0x10, 0x1E));
        }

        // 3) a ÍRIS + a PUPILA (a pupila aperta conforme o alvo é arrancado)
        if (abre > 0.5F) {
            float irisA = (abre - 0.5F) * 2.0F;
            float aperto = vida > 0.55F && vida < 0.8F
                    ? 1.0F - 0.55F * (float) Math.sin((vida - 0.55F) / 0.25F * Math.PI)
                    : 1.0F;
            float rx = larg * 0.62F, ry = alt * 0.85F;
            for (int i = 0; i < lados; i++) {
                double a0 = i / (double) lados * Math.PI * 2.0;
                double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
                float cx = (float) centro.x, cy = (float) centro.y;
                int corIris = cor(0.9F * irisA, 0x8A, 0x08, 0x12);
                vc.addVertex(mat, cx, cy, (float) centro.z + 0.03F).setColor(corIris);
                vc.addVertex(mat, cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry,
                        (float) centro.z + 0.03F).setColor(corIris);
                vc.addVertex(mat, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry,
                        (float) centro.z + 0.03F).setColor(corIris);
                vc.addVertex(mat, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry,
                        (float) centro.z + 0.03F).setColor(corIris);
                int corPupila = cor(1.0F * irisA, 0x02, 0x00, 0x01);
                float pr = 0.62F * aperto;
                vc.addVertex(mat, cx, cy, (float) centro.z + 0.05F).setColor(corPupila);
                vc.addVertex(mat, cx + (float) Math.cos(a0) * pr, cy + (float) Math.sin(a0) * pr * 0.75F,
                        (float) centro.z + 0.05F).setColor(corPupila);
                vc.addVertex(mat, cx + (float) Math.cos(a1) * pr, cy + (float) Math.sin(a1) * pr * 0.75F,
                        (float) centro.z + 0.05F).setColor(corPupila);
                vc.addVertex(mat, cx + (float) Math.cos(a1) * pr, cy + (float) Math.sin(a1) * pr * 0.75F,
                        (float) centro.z + 0.05F).setColor(corPupila);
            }
        }

        // 4) as PENAS-CÍLIO girando em volta do olho
        for (int i = 0; i < 16; i++) {
            double ang = i / 16.0 * Math.PI * 2.0 + vida * 7.0;
            float px = (float) (centro.x + Math.cos(ang) * (larg + 0.55F));
            float py = (float) (centro.y + Math.sin(ang) * (alt + 0.45F));
            float comp = 1.1F + 0.5F * (float) Math.sin(vida * 9.0 + i);
            pena(vc, mat, px, py, (float) centro.z - 0.1F,
                    Math.cos(ang) * comp * 0.5, Math.sin(ang) * comp * 0.5, 0,
                    0.07F, cor(0.8F * abre, 0x05, 0x01, 0x02), cor(0.7F * abre, 0xD0, 0x14, 0x20));
        }

        // 5) o CLARÃO e a COLUNA (o alvo é arrancado)
        if (vida > 0.55F && vida < 0.78F) {
            float f = (vida - 0.55F) / 0.23F;              // 0..1 dentro da fase
            float alfa = 1.0F - Math.abs(f * 2.0F - 1.0F); // sobe e desce
            esfera(vc, mat, centro, 1.2F + f * 4.5F, cor(0.7F * alfa, 0xB0, 0x10, 0x1C));
            tubo(vc, mat, new Vec3(alvo.x, alvo.y, alvo.z),
                    new Vec3(alvo.x, alvo.y + 12.0, alvo.z), 0.55F, cor(0.6F * alfa, 0x20, 0x02, 0x06));
            tubo(vc, mat, new Vec3(alvo.x, alvo.y, alvo.z),
                    new Vec3(alvo.x, alvo.y + 12.0, alvo.z), 0.18F, cor(0.85F * alfa, 0xFF, 0x30, 0x3C));
        }
    }

    // ---------------------------------------------------------------- primitivas

    /** um "cano" quadrado entre dois pontos (o raio/rastro). */
    private static void tubo(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 de, Vec3 ate,
            float raio, int cor) {
        Vec3 dir = ate.subtract(de);
        double len = dir.length();
        if (len < 1e-4) {
            return;
        }
        Vec3 u = dir.normalize();
        // dois vetores perpendiculares quaisquer
        Vec3 ref = Math.abs(u.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = u.cross(ref).normalize().scale(raio);
        Vec3 b = u.cross(a).normalize().scale(raio);
        for (int lado = 0; lado < 4; lado++) {
            Vec3 off0 = switch (lado) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> a.scale(-1);
                default -> b.scale(-1);
            };
            Vec3 off1 = switch (lado) {
                case 0 -> b;
                case 1 -> a.scale(-1);
                case 2 -> b.scale(-1);
                default -> a;
            };
            vc.addVertex(mat, (float) (de.x + off0.x), (float) (de.y + off0.y), (float) (de.z + off0.z)).setColor(cor);
            vc.addVertex(mat, (float) (de.x + off1.x), (float) (de.y + off1.y), (float) (de.z + off1.z)).setColor(cor);
            vc.addVertex(mat, (float) (ate.x + off1.x), (float) (ate.y + off1.y), (float) (ate.z + off1.z)).setColor(cor);
            vc.addVertex(mat, (float) (ate.x + off0.x), (float) (ate.y + off0.y), (float) (ate.z + off0.z)).setColor(cor);
        }
    }

    /** uma bolha (três anéis cruzados: barato e lê como esfera). */
    private static void esfera(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 c, float r, int cor) {
        int lados = 20;
        for (int plano = 0; plano < 3; plano++) {
            for (int i = 0; i < lados; i++) {
                double a0 = i / (double) lados * Math.PI * 2.0;
                double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
                float x0 = (float) (c.x + Math.cos(a0) * r), x1 = (float) (c.x + Math.cos(a1) * r);
                float y0 = (float) (c.y + Math.sin(a0) * r), y1 = (float) (c.y + Math.sin(a1) * r);
                switch (plano) {
                    case 0 -> quadZ(vc, mat, x0, y0, x1, y1, (float) c.z, cor);
                    case 1 -> quadY(vc, mat, x0, (float) c.z + (float) Math.cos(a0) * r, x1,
                            (float) c.z + (float) Math.cos(a1) * r, (float) c.y, cor);
                    default -> quadX(vc, mat, (float) c.z + (float) Math.sin(a0) * r, y0,
                            (float) c.z + (float) Math.sin(a1) * r, y1, (float) c.x, cor);
                }
            }
        }
    }

    /** anel deitado no chão. */
    private static void anel(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 c, float r, int cor, int lados) {
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            float x0 = (float) (c.x + Math.cos(a0) * r), z0 = (float) (c.z + Math.sin(a0) * r);
            float x1 = (float) (c.x + Math.cos(a1) * r), z1 = (float) (c.z + Math.sin(a1) * r);
            float xi = r * 0.9F;
            float xi0 = (float) (c.x + Math.cos(a0) * xi), zi0 = (float) (c.z + Math.sin(a0) * xi);
            float xi1 = (float) (c.x + Math.cos(a1) * xi), zi1 = (float) (c.z + Math.sin(a1) * xi);
            vc.addVertex(mat, xi0, (float) c.y + 0.05F, zi0).setColor(cor);
            vc.addVertex(mat, x0, (float) c.y + 0.05F, z0).setColor(cor);
            vc.addVertex(mat, x1, (float) c.y + 0.05F, z1).setColor(cor);
            vc.addVertex(mat, xi1, (float) c.y + 0.05F, zi1).setColor(cor);
        }
    }

    private static void billboard(VertexConsumer vc, org.joml.Matrix4f mat,
            float x, float y, float z, float r, int cor) {
        quadZ(vc, mat, x - r, y - r, x + r, y + r, z, cor);
    }

    /** uma pena: losango comprido (base grossa e escura, ponta fina e carmesim). */
    private static void pena(VertexConsumer vc, org.joml.Matrix4f mat,
            double x, double y, double z, double dx, double dy, double dz,
            float largura, int corBase, int corPonta) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-5) {
            return;
        }
        double nx = -dz / len * largura;
        double nz = dx / len * largura;
        vc.addVertex(mat, (float) (x + nx), (float) y, (float) (z + nz)).setColor(corBase);
        vc.addVertex(mat, (float) (x - nx), (float) y, (float) (z - nz)).setColor(corBase);
        vc.addVertex(mat, (float) (x + dx), (float) (y + dy), (float) (z + dz)).setColor(corPonta);
        vc.addVertex(mat, (float) (x + dx), (float) (y + dy), (float) (z + dz)).setColor(corPonta);
    }

    private static void quadZ(VertexConsumer vc, org.joml.Matrix4f mat,
            float x0, float y0, float x1, float y1, float z, int cor) {
        vc.addVertex(mat, x0, y0, z).setColor(cor);
        vc.addVertex(mat, x1, y0, z).setColor(cor);
        vc.addVertex(mat, x1, y1, z).setColor(cor);
        vc.addVertex(mat, x0, y1, z).setColor(cor);
    }

    private static void quadY(VertexConsumer vc, org.joml.Matrix4f mat,
            float x0, float z0, float x1, float z1, float y, int cor) {
        vc.addVertex(mat, x0, y, z0).setColor(cor);
        vc.addVertex(mat, x1, y, z0).setColor(cor);
        vc.addVertex(mat, x1, y, z1).setColor(cor);
        vc.addVertex(mat, x0, y, z1).setColor(cor);
    }

    private static void quadX(VertexConsumer vc, org.joml.Matrix4f mat,
            float z0, float y0, float z1, float y1, float x, int cor) {
        vc.addVertex(mat, x, y0, z0).setColor(cor);
        vc.addVertex(mat, x, y0, z1).setColor(cor);
        vc.addVertex(mat, x, y1, z1).setColor(cor);
        vc.addVertex(mat, x, y1, z0).setColor(cor);
    }

    /** ARGB a partir de alfa + rgb. */
    private static int cor(float alfa, int r, int g, int b) {
        int a = Math.max(0, Math.min(255, (int) (alfa * 255.0F)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
