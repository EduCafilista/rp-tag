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
                case 7 -> { /* (5.32.0) só acende a aura (modo deus) */ }
                default -> {
                    return;
                }
            }
            // (5.31.0) TODO poder acende a aura em volta do corpo (era só raio/orbe)
            VazioAuraFx.pulsar();
            if (p.tipo() == 7) {
                VazioAuraFx.acenderPor(30_000L);   // modo deus: 30 s de aura cheia
            }
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
            case 0 -> {   // (5.34.0) RAIO: lança carmesim com NÚCLEO branco e explosão de impacto
                float alfa = (1.0F - vida) * (vida < 0.15F ? vida / 0.15F : 1.0F);
                // capa escura (dá volume), corpo vermelho, fio branco-quente no meio
                tubo(vc, mat, e.de(), e.ate(), 0.22F, cor(0.85F * alfa, 0x14, 0x02, 0x05));
                tubo(vc, mat, e.de(), e.ate(), 0.10F, cor(alfa, 0xFF, 0x24, 0x34));
                tubo(vc, mat, e.de(), e.ate(), 0.035F, cor(alfa, 0xFF, 0xE8, 0xEE));
                // a explosão onde bate: clarão + dois anéis de choque
                esfera(vc, mat, e.ate(), 0.6F + vida * 5.0F,
                        cor(0.55F * (1.0F - vida), 0xC0, 0x10, 0x1E));
                anel(vc, mat, e.ate(), 0.8F + vida * 6.5F,
                        cor(0.7F * (1.0F - vida), 0xFF, 0x38, 0x48), 32);
                anel(vc, mat, e.ate(), 0.4F + vida * 4.0F,
                        cor(0.9F * (1.0F - vida), 0xFF, 0xA0, 0xB0), 24);
            }
            case 1 -> {   // (5.34.0) ORBE: núcleo escuro, casca pulsando e rastro duplo
                float alfa = 1.0F - vida;
                float puls = 1.0F + 0.16F * (float) Math.sin(vida * 22.0);
                esfera(vc, mat, e.ate(), 0.42F * puls, cor(0.95F * alfa, 0x08, 0x00, 0x02));
                esfera(vc, mat, e.ate(), 0.78F * puls, cor(0.75F * alfa, 0xD8, 0x18, 0x28));
                esfera(vc, mat, e.ate(), 1.05F * puls, cor(0.28F * alfa, 0x88, 0x08, 0x14));
                anel(vc, mat, e.ate(), 1.15F * puls, cor(0.6F * alfa, 0xFF, 0x50, 0x60), 28);
                tubo(vc, mat, e.de(), e.ate(), 0.13F, cor(0.35F * alfa, 0x60, 0x04, 0x0A));
                tubo(vc, mat, e.de(), e.ate(), 0.05F, cor(0.7F * alfa, 0xFF, 0x50, 0x5C));
            }
            case 2 -> {   // (5.34.0) ONDA DA MORTE: três anéis + a CORTINA que sobe do chão
                float raio = 1.0F + vida * 17.0F;
                float alfa = (1.0F - vida) * 0.75F;
                anel(vc, mat, e.ate(), raio, cor(alfa, 0xB0, 0x10, 0x1C), 48);
                anel(vc, mat, e.ate(), raio * 0.78F, cor(alfa * 0.6F, 0x60, 0x06, 0x0C), 48);
                anel(vc, mat, e.ate(), raio * 0.55F, cor(alfa * 0.35F, 0x40, 0x02, 0x06), 40);
                // a cortina: 36 colunas curtas subindo na borda da onda
                float altura = 1.25F * (1.0F - vida * 0.5F);
                Vec3 c = e.ate();
                for (int i = 0; i < 36; i++) {
                    double a = i / 36.0 * Math.PI * 2.0;
                    double x0 = c.x + Math.cos(a) * raio, z0 = c.z + Math.sin(a) * raio;
                    double x1 = c.x + Math.cos(a) * (raio + 0.45), z1 = c.z + Math.sin(a) * (raio + 0.45);
                    tubo(vc, mat, new Vec3(x0, c.y + 0.05, z0), new Vec3(x1, c.y + altura, z1),
                            0.10F, cor(alfa * 0.5F, 0xD0, 0x14, 0x22));
                }
            }
            case 3 -> {   // (5.34.0) OLHOS: risco + o OLHO aberto no alvo (pálpebra e pupila)
                float alfa = 1.0F - vida;
                tubo(vc, mat, e.de(), e.ate(), 0.04F, cor(alfa, 0xFF, 0x50, 0x5A));
                float abre = Math.min(1.0F, vida * 4.0F);
                Vec3 a = e.ate();
                // a pálpebra: dois arcos (losango) no plano da câmera
                for (int i = 0; i < 2; i++) {
                    float s = i == 0 ? 1.0F : -1.0F;
                    tubo(vc, mat, new Vec3(a.x - 0.55, a.y, a.z),
                            new Vec3(a.x, a.y + 0.34F * abre * s, a.z),
                            0.045F, cor(alfa, 0xFF, 0xC0, 0xC8));
                    tubo(vc, mat, new Vec3(a.x, a.y + 0.34F * abre * s, a.z),
                            new Vec3(a.x + 0.55, a.y, a.z),
                            0.045F, cor(alfa, 0xFF, 0xC0, 0xC8));
                }
                esfera(vc, mat, a, 0.16F, cor(alfa, 0xFF, 0x20, 0x30));
                esfera(vc, mat, a, 0.07F, cor(alfa, 0x02, 0x00, 0x00));
            }
            case 5 -> chuvaDePenas(vc, mat, e, vida);
            case 6 -> olhoQueBane(vc, mat, e, vida);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- portal

    /**
     * <b>O PORTAL DO JUÍZO</b> (5.29.0, refeito na 5.32.0 a partir do portal do outro mod).
     *
     * <p>O dono mandou o jar do <b>Ronova</b> para eu ver a animação do portal. Lá dentro:
     * {@code RiftVortex} — um <b>vórtice carmesim que gira</b> (textura animada de nuvens
     * vermelhas), com anéis concêntricos em volta, uma <b>íris</b> ({@code rift_vortex_iris}) e,
     * por fora, os <b>olhos</b> ({@code DeathRiftEyes}).
     *
     * <p>Então o portal da arena virou isso: um <b>olho de vórtice que gira</b>, com três anéis
     * girando em sentidos alternados, a pupila que segue quem chega perto, penas-pálpebra em
     * volta e a luz batendo no chão. Tudo geometria ({@code debugQuads}), então aparece com
     * partículas no mínimo, com Sodium e com Iris.
     *
     * @param t    segundos desde que abriu (a animação)
     * @param vida 0..1 (1 = prestes a apagar)
     */
    private static void portal(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 base,
            double t, float vida) {
        float cx = (float) base.x;
        float cz = (float) base.z;
        float cy = (float) base.y;          // (5.34.0) o pacote já manda o CENTRO da boca
        int lados = 48;

        // (5.34.0) A MOLDURA DE PEDRA DO CLIENTE SAIU. O dono: "o portal está bugado na parte
        // de trás". Era isto: o cliente desenhava uma moldura de pedra POR CIMA da moldura de
        // blocos, em outro plano — de trás apareciam duas molduras e o olho parecia solto.
        // Agora a moldura é só a de blocos (obsidiana chorosa) e aqui fica só o VÓRTICE.

        // ---- 1) O VÓRTICE: disco escuro + 3 espirais girando
        float raio = 1.35F;                 // cabe dentro do vão de 3 x 5 blocos
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            int corMiolo = cor(0.97F, 0x06, 0x01, 0x03);
            vc.addVertex(mat, cx, cy, cz + 0.02F).setColor(corMiolo);
            vc.addVertex(mat, cx + (float) Math.cos(a0) * raio, cy + (float) Math.sin(a0) * raio,
                    cz + 0.02F).setColor(corMiolo);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * raio, cy + (float) Math.sin(a1) * raio,
                    cz + 0.02F).setColor(corMiolo);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * raio, cy + (float) Math.sin(a1) * raio,
                    cz + 0.02F).setColor(corMiolo);
        }
        for (int volta = 0; volta < 3; volta++) {
            float raioInterno = 0.14F + volta * 0.36F;
            float raioExterno = raioInterno + 0.46F;
            double giro = t * (1.6 + volta * 0.5) * (volta % 2 == 0 ? 1 : -1);
            int pas = 14;
            for (int i = 0; i < pas; i++) {
                double a0 = i / (double) pas * Math.PI * 2.0 + giro;
                double a1 = (i + 1) / (double) pas * Math.PI * 2.0 + giro;
                double curva = 0.35;
                float alfa = 0.55F - volta * 0.12F;
                int cInt = cor(alfa * 0.35F, 0x60, 0x04, 0x0A);
                int cExt = cor(alfa, 0xE8, 0x18, 0x28);
                vc.addVertex(mat, cx + (float) (Math.cos(a0) * raioInterno),
                        cy + (float) (Math.sin(a0) * raioInterno), cz + 0.04F).setColor(cInt);
                vc.addVertex(mat, cx + (float) (Math.cos(a1) * raioInterno),
                        cy + (float) (Math.sin(a1) * raioInterno), cz + 0.04F).setColor(cInt);
                vc.addVertex(mat, cx + (float) (Math.cos(a1 + curva) * raioExterno),
                        cy + (float) (Math.sin(a1 + curva) * raioExterno), cz + 0.04F).setColor(cExt);
                vc.addVertex(mat, cx + (float) (Math.cos(a0 + curva) * raioExterno),
                        cy + (float) (Math.sin(a0 + curva) * raioExterno), cz + 0.04F).setColor(cExt);
            }
        }

        // ---- 2) A ÍRIS com a pupila que olha pra quem chega
        float olhandoX = 0.0F, olhandoY = 0.0F;
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                Vec3 p = mc.player.position();
                double dx = p.x - base.x, dy = (p.y + 1.0) - cy;
                double d = Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
                olhandoX = (float) (dx / d) * 0.14F;
                olhandoY = (float) (dy / d) * 0.14F;
            }
        } catch (Throwable ignorado) {
            // sem jogador: pupila no centro
        }
        float pulso = 1.0F + 0.05F * (float) Math.sin(t * 3.1);
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            int cIris = cor(0.85F, 0xB4, 0x0E, 0x1C);
            vc.addVertex(mat, cx + olhandoX * 0.5F, cy + olhandoY * 0.5F, cz + 0.06F).setColor(cIris);
            vc.addVertex(mat, cx + (float) Math.cos(a0) * 0.52F * pulso,
                    cy + (float) Math.sin(a0) * 0.52F * pulso, cz + 0.06F).setColor(cIris);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * 0.52F * pulso,
                    cy + (float) Math.sin(a1) * 0.52F * pulso, cz + 0.06F).setColor(cIris);
            vc.addVertex(mat, cx + (float) Math.cos(a1) * 0.52F * pulso,
                    cy + (float) Math.sin(a1) * 0.52F * pulso, cz + 0.06F).setColor(cIris);
            int cPupila = cor(1.0F, 0x01, 0x00, 0x00);
            vc.addVertex(mat, cx + olhandoX, cy + olhandoY, cz + 0.08F).setColor(cPupila);
            vc.addVertex(mat, cx + olhandoX + (float) Math.cos(a0) * 0.22F,
                    cy + olhandoY + (float) Math.sin(a0) * 0.22F, cz + 0.08F).setColor(cPupila);
            vc.addVertex(mat, cx + olhandoX + (float) Math.cos(a1) * 0.22F,
                    cy + olhandoY + (float) Math.sin(a1) * 0.22F, cz + 0.08F).setColor(cPupila);
            vc.addVertex(mat, cx + olhandoX + (float) Math.cos(a1) * 0.22F,
                    cy + olhandoY + (float) Math.sin(a1) * 0.22F, cz + 0.08F).setColor(cPupila);
        }

        // ---- 3) os 8 olhinhos, agora DENTRO do vórtice (antes ficavam atrás da parede)
        for (int i = 0; i < 8; i++) {
            double ang = i / 8.0 * Math.PI * 2.0 + t * 0.25;
            float ex = cx + (float) Math.cos(ang) * (raio * 0.80F);
            float ey = cy + (float) Math.sin(ang) * (raio * 0.80F);
            float pisca = 0.55F + 0.45F * (float) Math.sin(t * 2.3 + i * 1.7);
            int cOlho = cor(0.85F * pisca, 0xFF, 0x2A, 0x38);
            vc.addVertex(mat, ex - 0.13F, ey, cz + 0.10F).setColor(cOlho);
            vc.addVertex(mat, ex + 0.13F, ey, cz + 0.10F).setColor(cOlho);
            vc.addVertex(mat, ex, ey + 0.07F, cz + 0.10F).setColor(cOlho);
            vc.addVertex(mat, ex, ey - 0.07F, cz + 0.10F).setColor(cOlho);
        }

        // ---- 4) as penas-pálpebra, coladas na borda do vórtice
        for (int i = 0; i < 12; i++) {
            double ang = i / 12.0 * Math.PI * 2.0 + t * 0.7;
            float px = cx + (float) Math.cos(ang) * (raio * 0.98F);
            float py = cy + (float) Math.sin(ang) * (raio * 0.98F);
            float comp = 0.34F + 0.16F * (float) Math.sin(t * 2.6 + i);
            pena(vc, mat, px, py, cz + 0.09F,
                    Math.cos(ang) * comp, Math.sin(ang) * comp, 0,
                    0.06F, cor(0.75F, 0x06, 0x01, 0x02), cor(0.8F, 0xE2, 0x18, 0x26));
        }

        // ---- 5) a luz no chão (só o anel, sem tubo atravessando parede)
        anel(vc, mat, new Vec3(base.x, base.y - 2.0F + 0.06F, base.z),
                1.9F + 0.15F * (float) Math.sin(t * 2.2), cor(0.55F, 0xB8, 0x12, 0x22), 40);
    }

    // ---------------------------------------------------------------- penas

    /**
     * (5.31.0) <b>CHUVA DE PENAS</b>: a cortina carmesim que cai onde ele olha — penas pretas
     * descendo em coluna, com as pontas carmesim, terminando num clarão no chão.
     */
    private static void chuvaDePenas(VertexConsumer vc, org.joml.Matrix4f mat, Efeito e, float vida) {
        float alfa = (1.0F - vida) * (vida < 0.1F ? vida / 0.1F : 1.0F);
        Vec3 centro = e.ate();
        for (int i = 0; i < 36; i++) {
            double ang = i * 2.399963;
            double raio = 0.6 + (i % 7) * 1.1;
            double queda = (vida * 18.0 + i * 0.7) % 12.0;
            double x = centro.x + Math.cos(ang) * raio;
            double z = centro.z + Math.sin(ang) * raio;
            double y = centro.y + 11.0 - queda;
            pena(vc, mat, x, y, z, 0.05 * Math.cos(ang), -0.45, 0.05 * Math.sin(ang),
                    0.06F, cor(0.9F * alfa, 0x08, 0x02, 0x04), cor(0.7F * alfa, 0xE0, 0x18, 0x24));
        }
        anel(vc, mat, new Vec3(centro.x, centro.y + 0.1, centro.z), 1.0F + vida * 6.0F,
                cor(0.5F * alfa, 0xB0, 0x10, 0x1C), 40);
    }

    // ---------------------------------------------------------------- o olho que bane

    /**
     * (5.31.0) <b>O OLHO VERMELHO QUE BANE</b> — abre a fenda, escancara a pálpebra, a íris
     * encara, o clarão arranca o alvo e o olho fecha (2,6 s).
     */
    private static void olhoQueBane(VertexConsumer vc, org.joml.Matrix4f mat, Efeito e, float vida) {
        Vec3 centro = e.de().add(0, 1.4, 0);
        Vec3 alvo = e.ate();
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
        int lados = 48;

        // a FENDA preta
        quadZ(vc, mat, (float) centro.x - larg * 1.15F, (float) centro.y - 0.03F,
                (float) centro.x + larg * 1.15F, (float) centro.y + 0.03F,
                (float) centro.z - 0.05F, cor(0.95F * abre, 0x00, 0x00, 0x00));

        // a PÁLPEBRA
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

        // a ÍRIS + a PUPILA que aperta
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

        // as PENAS-CÍLIO
        for (int i = 0; i < 16; i++) {
            double ang = i / 16.0 * Math.PI * 2.0 + vida * 7.0;
            float px = (float) (centro.x + Math.cos(ang) * (larg + 0.55F));
            float py = (float) (centro.y + Math.sin(ang) * (alt + 0.45F));
            float comp = 1.1F + 0.5F * (float) Math.sin(vida * 9.0 + i);
            pena(vc, mat, px, py, (float) centro.z - 0.1F,
                    Math.cos(ang) * comp * 0.5, Math.sin(ang) * comp * 0.5, 0,
                    0.07F, cor(0.8F * abre, 0x05, 0x01, 0x02), cor(0.7F * abre, 0xD0, 0x14, 0x20));
        }

        // o CLARÃO e a COLUNA (o alvo é arrancado)
        if (vida > 0.55F && vida < 0.78F) {
            float f = (vida - 0.55F) / 0.23F;
            float alfa = 1.0F - Math.abs(f * 2.0F - 1.0F);
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
