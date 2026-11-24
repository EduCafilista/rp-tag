package dev.rptag.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import dev.rptag.VazioPoderPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * <b>OS EFEITOS DOS PODERES DO VAZIO — e o PORTAL DO JUÍZO</b> (redesenho "v2").
 *
 * <p>O que mudou nesta versão (a função de cada poder NÃO mudou — só o visual):
 * <ul>
 *   <li><b>duas camadas</b>: corpo escuro translúcido ({@code debugQuads}) + brilho aditivo
 *       ({@code lightning}). Antes era tudo uma camada só, então nada "acendia";</li>
 *   <li><b>primitivas de verdade</b>: esfera por latitude/longitude, discos e anéis em qualquer
 *       plano, fitas voltadas para a câmera, tubos afinando. A "esfera" antiga eram retângulos
 *       alinhados aos eixos (parecia uma caixa), e o olho do BANIR só aparecia de frente;</li>
 *   <li><b>assentado no chão</b>: anéis, ondas e a chuva de penas procuram o chão de verdade
 *       (antes ficavam no nível do olho quando o impacto era no ar);</li>
 *   <li><b>cada poder com começo, meio e fim</b>: carga, disparo, impacto, dissipação;</li>
 *   <li><b>ONDA em cone</b>, partindo de quem conjura (igual ao dano: 18 blocos, ~63°);</li>
 *   <li><b>PENAS</b> desenhadas sobre a área real do dano (aviso no chão, chuva, impacto);</li>
 *   <li><b>tremor de câmera</b> proporcional à distância;</li>
 *   <li>tudo geometria — aparece com partículas no mínimo, com Sodium e com Iris.</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: erro aqui faz o efeito sumir, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioPoderFx {

    private VazioPoderFx() {
    }

    /** um efeito vivo na tela: tipo, origem, alvo, chão sob o alvo, nasceu (ns) e quanto dura (ns). */
    private record Efeito(int tipo, Vec3 de, Vec3 ate, Vec3 chao, long nasceu, long dura) {
    }

    /** um tremor agendado: começa em {@code inicio} (ns), dura {@code dura}, força já atenuada. */
    private record Tremor(long inicio, long dura, float forca) {
    }

    private static final List<Efeito> VIVOS = new ArrayList<>();
    private static final List<Tremor> TREMORES = new ArrayList<>();

    /** o portal do julgamento fica aceso sozinho até isso passar (o ADM reabre). */
    private static Efeito portalFixo;

    /** onde está a câmera neste quadro (para as fitas virarem para ela). */
    private static Vec3 cam = Vec3.ZERO;

    // paleta do vazio
    private static final int[] SANGUE = {0xE8, 0x18, 0x28};
    private static final int[] CARMIM = {0xB0, 0x10, 0x1C};
    private static final int[] ESCURO = {0x0A, 0x01, 0x03};
    private static final int[] QUENTE = {0xFF, 0xE4, 0xE8};
    private static final int[] ROSA = {0xFF, 0x58, 0x6A};
    private static final int[] VINHO = {0x4A, 0x06, 0x12};
    private static final int[] GELO = {0xE8, 0xB8, 0xD0};

    /** o cliente recebeu o pacote. */
    public static void receber(VazioPoderPayload p) {
        try {
            Vec3 de = new Vec3(p.ox(), p.oy(), p.oz());
            Vec3 ate = new Vec3(p.tx(), p.ty(), p.tz());
            long agora = System.nanoTime();
            Vec3 chao = achaChao(ate);
            switch (p.tipo()) {
                case 0 -> {                                                                   // raio
                    VIVOS.add(new Efeito(0, de, ate, chao, agora, 800_000_000L));
                    tremer(ate, agora + 60_000_000L, 380_000_000L, 0.9F);
                }
                case 1 -> {                                                                   // orbe
                    VIVOS.add(new Efeito(1, de, ate, chao, agora, 1_000_000_000L));
                    tremer(ate, agora + 380_000_000L, 420_000_000L, 0.8F);
                }
                case 2 -> {                                                                   // onda
                    VIVOS.add(new Efeito(2, de, ate, achaChao(de.add(0, -0.2, 0)), agora, 1_700_000_000L));
                    tremer(de, agora, 900_000_000L, 0.6F);
                }
                case 3 -> VIVOS.add(new Efeito(3, de, ate, chao, agora, 520_000_000L));      // olhos
                case 4 -> portalFixo = new Efeito(4, de, ate, chao, agora, 8L * 60L * 1_000_000_000L);
                case 5 -> {                                                                   // chuva de penas
                    VIVOS.add(new Efeito(5, de, ate, chao, agora, 1_700_000_000L));
                    tremer(ate, agora + 700_000_000L, 700_000_000L, 0.45F);
                }
                case 6 -> {                                                                   // OLHO QUE BANE
                    VIVOS.add(new Efeito(6, de, ate, achaChao(ate), agora, 2_800_000_000L));
                    tremer(de, agora + 1_450_000_000L, 800_000_000L, 1.1F);
                }
                case 7 -> { /* só acende a aura (modo deus) */ }
                default -> {
                    return;
                }
            }
            // TODO poder acende a aura em volta do corpo
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

    /** procura o chão debaixo de um ponto (até 40 blocos); sem chão devolve o próprio ponto. */
    private static Vec3 achaChao(Vec3 p) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel lv = mc.level;
            if (lv == null) {
                return p;
            }
            var r = lv.clip(new ClipContext(p.add(0, 0.6, 0), p.add(0, -40.0, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            return r.getType() == HitResult.Type.BLOCK ? r.getLocation() : p;
        } catch (Throwable t) {
            return p;
        }
    }

    private static void tremer(Vec3 onde, long inicio, long dura, float forca) {
        try {
            var p = Minecraft.getInstance().player;
            if (p == null) {
                return;
            }
            double d = p.position().distanceTo(onde);
            float f = (float) Math.max(0.0, 1.0 - d / 42.0) * forca;
            if (f > 0.02F && TREMORES.size() < 12) {
                TREMORES.add(new Tremor(inicio, dura, f));
            }
        } catch (Throwable ignorado) {
            // sem jogador
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles ev) {
        if (TREMORES.isEmpty()) {
            return;
        }
        long agora = System.nanoTime();
        float soma = 0.0F;
        for (Iterator<Tremor> it = TREMORES.iterator(); it.hasNext();) {
            Tremor t = it.next();
            if (agora > t.inicio() + t.dura()) {
                it.remove();
                continue;
            }
            if (agora < t.inicio()) {
                continue;
            }
            float k = 1.0F - (agora - t.inicio()) / (float) t.dura();
            soma += t.forca() * k * k;
        }
        if (soma <= 0.0F) {
            return;
        }
        float s = agora / 1.0e9F;
        ev.setYaw(ev.getYaw() + soma * 1.7F * (float) Math.sin(s * 47.0F));
        ev.setPitch(ev.getPitch() + soma * 1.4F * (float) Math.sin(s * 61.0F + 1.0F));
        ev.setRoll(ev.getRoll() + soma * 1.0F * (float) Math.sin(s * 39.0F + 2.0F));
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
            cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            for (Iterator<Efeito> it = VIVOS.iterator(); it.hasNext();) {
                Efeito e = it.next();
                if ((agora - e.nasceu()) >= e.dura()) {
                    it.remove();
                }
            }
            // duas passadas: o corpo escuro primeiro, o brilho aditivo por cima
            for (int passe = 0; passe < 2; passe++) {
                boolean brilho = passe == 1;
                RenderType tipo = brilho ? RenderType.lightning() : RenderType.debugQuads();
                VertexConsumer vc = buf.getBuffer(tipo);
                pose.pushPose();
                pose.translate(-cam.x, -cam.y, -cam.z);
                Matrix4f mat = pose.last().pose();
                for (Efeito e : VIVOS) {
                    float vida = Math.min(0.999F, (agora - e.nasceu()) / (float) e.dura());
                    try {
                        desenhar(vc, mat, e, vida, brilho);
                    } catch (Throwable ignorado) {
                        // um efeito com defeito não derruba os outros
                    }
                }
                if (!brilho && portalFixo != null) {
                    float vida = (agora - portalFixo.nasceu()) / (float) portalFixo.dura();
                    if (vida >= 1.0F) {
                        portalFixo = null;
                    } else {
                        portal(vc, mat, portalFixo.de(), (agora - portalFixo.nasceu()) / 1_000_000_000.0, vida);
                    }
                }
                pose.popPose();
                buf.endBatch(tipo);
            }
        } catch (Throwable ignorado) {
            // efeito é enfeite
        }
    }

    // ================================================================ utilidades
    private static float seg(float v, float a, float b) {
        return Math.max(0.0F, Math.min(1.0F, (v - a) / (b - a)));
    }

    private static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static float hash(int n) {
        float s = (float) Math.sin(n * 12.9898 + 78.233) * 43758.5453F;
        return s - (float) Math.floor(s);
    }

    private static int c(float alfa, int[] rgb) {
        return cor(alfa, rgb[0], rgb[1], rgb[2]);
    }

    private static int mix(int[] a, int[] b, float t, float alfa) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return cor(alfa, (int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t), (int) (a[2] + (b[2] - a[2]) * t));
    }

    /** base (direita, cima) voltada para a câmera, vista de {@code em}. */
    private static Vec3[] viraCamera(Vec3 em) {
        Vec3 n = cam.subtract(em);
        n = n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3 r = new Vec3(0, 1, 0).cross(n);
        r = r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
        return new Vec3[] {r, n.cross(r).normalize()};
    }

    // ================================================================ despacho
    private static void desenhar(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        switch (e.tipo()) {
            case 0 -> raio(vc, m, e, vida, brilho);
            case 1 -> orbe(vc, m, e, vida, brilho);
            case 2 -> onda(vc, m, e, vida, brilho);
            case 3 -> olhos(vc, m, e, vida, brilho);
            case 5 -> penas(vc, m, e, vida, brilho);
            case 6 -> banir(vc, m, e, vida, brilho);
            default -> {
            }
        }
    }

    // ================================================================ 0 · RAIO
    /**
     * A lança carmesim: carga no cano, bainha escura irregular, núcleo branco-quente, três fios em
     * hélice, anéis de choque viajando, e uma explosão em camadas no fim (clarão, onda no chão,
     * lascas e coluna de luz).
     */
    private static void raio(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        Vec3 de = e.de().add(0, -0.15, 0), ate = e.ate();
        Vec3 d = ate.subtract(de);
        double len = d.length();
        if (len < 0.5) {
            return;
        }
        Vec3 dir = d.scale(1.0 / len);
        Vec3 ref = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(ref).normalize(), b = dir.cross(a).normalize();
        float env = vida < 0.10F ? vida / 0.10F : (float) Math.pow(1.0F - (vida - 0.10F) / 0.90F, 1.4);
        float kImp = seg(vida, 0.08F, 1.0F);
        float t = vida * 8.0F;

        // carga no cano: anel que é sugado para a boca
        Vec3 boca = de.add(dir.scale(0.8));
        if (vida < 0.22F) {
            float k = vida / 0.22F;
            Vec3[] cb = viraCamera(boca);
            if (brilho) {
                aroPlano(vc, m, boca, cb[0], cb[1], 1.6F * (1.0F - k) + 0.2F, 1.6F * (1.0F - k) + 0.35F,
                        c(0.0F, ROSA), c(0.9F * k, ROSA), 28, 0.0F, 0.0F, 6.2832F);
                disco(vc, m, boca, cb[0], cb[1], 0.45F * k, c(0.9F, QUENTE), c(0.0F, SANGUE), 16);
            }
        }

        // pontos do eixo com tremor (bainha irregular)
        int n = (int) Math.max(6, Math.min(40, len / 1.6));
        Vec3[] eixo = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            double jit = Math.sin(Math.PI * s) * 0.10 * env;
            int sem = (int) (vida * 14.0F);
            eixo[i] = de.add(d.scale(s)).add(a.scale((hash(i * 7 + sem) - 0.5) * 2 * jit))
                    .add(b.scale((hash(i * 13 + sem) - 0.5) * 2 * jit));
        }
        if (!brilho) {
            for (int i = 0; i < n; i++) {
                float w0 = 0.26F * env * (0.7F + 0.3F * (float) Math.sin(i * 1.7 + t));
                tubo(vc, m, eixo[i], eixo[i + 1], w0, w0, c(0.85F * env, ESCURO), c(0.85F * env, ESCURO));
            }
        } else {
            for (int i = 0; i < n; i++) {
                tubo(vc, m, eixo[i], eixo[i + 1], 0.11F * env, 0.11F * env, c(0.95F * env, SANGUE), c(0.95F * env, SANGUE));
                tubo(vc, m, eixo[i], eixo[i + 1], 0.04F * env, 0.04F * env, c(env, QUENTE), c(env, QUENTE));
            }
            // três fios em hélice
            for (int f = 0; f < 3; f++) {
                Vec3 prev = null;
                for (int i = 0; i <= n * 2; i++) {
                    float s = i / (float) (n * 2);
                    double ang = s * 22.0 + t * (2.4 + f * 0.3) + f * 2.0944;
                    double rr = (0.34 + 0.08 * Math.sin(s * 9.0 + t)) * env;
                    Vec3 p = de.add(d.scale(s)).add(a.scale(Math.cos(ang) * rr)).add(b.scale(Math.sin(ang) * rr));
                    if (prev != null) {
                        fita(vc, m, prev, p, 0.07F * env, 0.07F * env, c(0.85F * env, ROSA), c(0.85F * env, ROSA));
                    }
                    prev = p;
                }
            }
            // anéis de choque percorrendo o raio
            for (int i = 0; i < 6; i++) {
                float s = (vida * 1.8F + i / 6.0F) % 1.0F;
                Vec3 p = de.add(d.scale(s));
                float rr = 0.55F + 0.5F * s;
                aroPlano(vc, m, p, a, b, rr - 0.05F, rr + 0.05F, c(0.7F * env, SANGUE), c(0.7F * env, SANGUE), 18, 0.0F, 0.0F, 6.2832F);
            }
        }

        // impacto
        if (kImp <= 0.0F) {
            return;
        }
        float ke = ease(kImp), fade = (float) Math.pow(1.0F - kImp, 1.4);
        Vec3 g = e.chao().add(0, 0.06, 0);
        if (!brilho) {
            esferaUV(vc, m, ate, 0.5F + 3.0F * ke, c(0.45F * fade, ESCURO), 8, 14);
            aroPlano(vc, m, g, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 6.5F * ke - 0.9F * (1 - kImp) - 0.4F, 6.5F * ke,
                    c(0.0F, VINHO), c(0.8F * fade, CARMIM), 48, 0.0F, 0.0F, 6.2832F);
        } else {
            esferaUV(vc, m, ate, 0.4F + 3.4F * ke, c(0.55F * fade, CARMIM), 8, 14);
            if (kImp < 0.45F) {
                esferaUV(vc, m, ate, 0.25F + 1.4F * ke, c(0.9F * (1.0F - kImp / 0.45F), QUENTE), 6, 10);
            }
            aroPlano(vc, m, g, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 3.9F * ke - 0.25F, 3.9F * ke,
                    c(0.0F, ROSA), c(0.9F * fade, ROSA), 40, 0.0F, 0.0F, 6.2832F);
            for (int i = 0; i < 10; i++) {                                   // lascas saindo do ponto de impacto
                double ang = i / 10.0 * 6.2832 + hash(i) * 0.5, el = 0.25 + hash(i + 40) * 0.9;
                Vec3 dd = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el));
                double l0 = 0.6 + 2.0 * ke, l1 = l0 + 1.2 + 2.2 * ke * (0.5 + hash(i + 9));
                fita(vc, m, ate.add(dd.scale(l0)), ate.add(dd.scale(l1)), 0.14F * fade, 0.02F, c(0.9F * fade, ROSA), c(0.0F, SANGUE));
            }
            tubo(vc, m, g, g.add(0, 11.0 * (0.4 + 0.6 * ke), 0), 0.45F * fade, 0.05F * fade, c(0.6F * fade, SANGUE), c(0.0F, SANGUE));
            tubo(vc, m, g, g.add(0, 11.0 * (0.4 + 0.6 * ke), 0), 0.14F * fade, 0.02F * fade, c(0.9F * fade, QUENTE), c(0.0F, QUENTE));
        }
    }

    // ================================================================ 1 · ORBE
    /** A orbe: viaja (núcleo escuro, casca carmesim, três anéis orbitando, rastro) e estoura em tentáculos. */
    private static void orbe(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        Vec3 de = e.de().add(0, -0.25, 0), ate = e.ate();
        float kv = seg(vida, 0.0F, 0.38F), kx = seg(vida, 0.38F, 1.0F);
        float t = vida * 10.0F;
        if (kv < 1.0F || kx < 0.12F) {
            float kp = kv * kv * (3.0F - 2.0F * kv);
            Vec3 pos = de.add(ate.subtract(de).scale(kp));
            float viva = kv < 1.0F ? 1.0F : 1.0F - kx / 0.12F;
            float puls = 1.0F + 0.12F * (float) Math.sin(t * 5.0);
            if (!brilho) {
                esferaUV(vc, m, pos, 0.34F * puls * viva, c(0.98F * viva, ESCURO), 7, 12);
            } else {
                esferaUV(vc, m, pos, 0.55F * puls * viva, c(0.55F * viva, SANGUE), 7, 12);
                esferaUV(vc, m, pos, 0.85F * puls * viva, c(0.18F * viva, CARMIM), 6, 10);
                for (int r = 0; r < 3; r++) {                                  // anéis inclinados orbitando
                    double ang = t * (1.6 + r * 0.5) + r * 2.1;
                    Vec3 u = new Vec3(Math.cos(ang), Math.sin(ang) * 0.4, Math.sin(ang)).normalize();
                    Vec3 v = new Vec3(-Math.sin(ang * 0.7), Math.cos(r + 1.0), Math.cos(ang * 0.7)).cross(u).normalize();
                    aroPlano(vc, m, pos, u, v, 0.72F * viva - 0.04F, 0.72F * viva + 0.04F, c(0.8F * viva, ROSA), c(0.8F * viva, ROSA), 20, 0.0F, 0.0F, 6.2832F);
                }
                Vec3 atras = de.subtract(pos);                                 // rastro
                for (int i = 0; i < 9; i++) {
                    float s0 = i / 9.0F, s1 = (i + 1) / 9.0F;
                    Vec3 p0 = pos.add(atras.scale(s0 * 0.6)), p1 = pos.add(atras.scale(s1 * 0.6));
                    fita(vc, m, p0, p1, 0.36F * (1.0F - s0), 0.36F * (1.0F - s1), c(0.7F * (1.0F - s0) * viva, SANGUE), c(0.7F * (1.0F - s1) * viva, SANGUE));
                }
            }
        }
        if (kx <= 0.0F) {
            return;
        }
        float ke = ease(kx), fade = (float) Math.pow(1.0F - kx, 1.3);
        Vec3 g = e.chao().add(0, 0.06, 0);
        if (!brilho) {
            esferaUV(vc, m, ate, 0.9F * (1.0F - kx) + 0.2F, c(0.95F * (1.0F - kx * 0.6F), ESCURO), 7, 12);    // bolha de vazio que murcha
            esferaUV(vc, m, ate, 4.0F * ke, c(0.30F * fade, VINHO), 8, 14);
            aroPlano(vc, m, g, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 5.0F * ke - 0.8F, 5.0F * ke, c(0.0F, VINHO), c(0.75F * fade, CARMIM), 44, 0.0F, 0.0F, 6.2832F);
        } else {
            esferaUV(vc, m, ate, 3.6F * ke, c(0.30F * fade, SANGUE), 8, 14);
            aroPlano(vc, m, g, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 4.0F * ke - 0.2F, 4.0F * ke, c(0.0F, ROSA), c(0.85F * fade, ROSA), 40, 0.0F, 0.0F, 6.2832F);
            for (int i = 0; i < 12; i++) {                                     // tentáculos curvos
                double ang = i / 12.0 * 6.2832 + hash(i) * 0.4;
                Vec3 base = ate;
                Vec3 prev = base;
                double alcance = (2.2 + 2.4 * hash(i + 3)) * ke;
                for (int j = 1; j <= 6; j++) {
                    float f = j / 6.0F;
                    Vec3 p = base.add(Math.cos(ang) * alcance * f, Math.sin(f * 2.6) * 0.9 * ke - 0.5 * f * f * ke, Math.sin(ang) * alcance * f);
                    fita(vc, m, prev, p, 0.2F * (1 - f) * fade + 0.01F, 0.2F * (1 - (j + 1) / 6.0F) * fade + 0.01F, c(0.85F * fade, ROSA), c(0.35F * fade, SANGUE));
                    prev = p;
                }
            }
        }
    }

    // ================================================================ 2 · ONDA
    /**
     * A onda da morte: frentes de onda em arco (o cone real do dano: 18 blocos, ~63°) saindo de quem
     * conjura, com a cortina que sobe da frente, cristais de gelo que brotam (a lentidão) e fiapos.
     */
    private static void onda(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        Vec3 g0 = e.chao();
        Vec3 f3 = e.ate().subtract(e.de());
        double fl = Math.sqrt(f3.x * f3.x + f3.z * f3.z);
        double fx = fl < 1e-3 ? 0 : f3.x / fl, fz = fl < 1e-3 ? 1 : f3.z / fl;
        double base = Math.atan2(fz, fx);
        double metade = Math.toRadians(64.0);
        Vec3 g = new Vec3(e.de().x, g0.y + 0.07, e.de().z);
        for (int j = 0; j < 3; j++) {
            float k = seg(vida, j * 0.07F, 0.72F + j * 0.07F);
            if (k <= 0.0F || k >= 1.0F) {
                continue;
            }
            float ke = ease(k), fade = (1.0F - k) * (j == 0 ? 1.0F : 0.7F - j * 0.12F);
            double R = 18.0 * ke;
            int N = 44;
            Vec3 prevIn = null, prevOut = null;
            for (int i = 0; i <= N; i++) {
                double ang = base - metade + 2 * metade * i / N;
                double borda = 1.0 - Math.pow(Math.abs(ang - base) / metade, 3.0);
                Vec3 dd = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                double ancho = (0.35 + 1.1 * (1.0 - k)) * (0.4 + 0.6 * borda);
                Vec3 pOut = g.add(dd.scale(R)), pIn = g.add(dd.scale(Math.max(0.0, R - ancho)));
                if (prevOut != null) {
                    if (!brilho) {
                        quad(vc, m, prevIn, prevOut, pOut, pIn, c(0.0F, VINHO), c(0.7F * fade, VINHO), c(0.7F * fade, VINHO), c(0.0F, VINHO));
                    } else {
                        Vec3 up0 = prevOut.add(0, 0.12, 0), up1 = pOut.add(0, 0.12, 0);
                        fita(vc, m, up0, up1, 0.14F * fade + 0.03F, 0.14F * fade + 0.03F, c(0.9F * fade, ROSA), c(0.9F * fade, ROSA));
                    }
                }
                if (j == 0 && i % 2 == 0 && i > 0 && i < N) {                                   // cortina que sobe da frente
                    double h = (1.0 + 1.9 * borda) * (1.0 - k * 0.55) * (0.65 + 0.35 * hash(i * 3 + (int) (vida * 6)));
                    if (!brilho) {
                        quad(vc, m, pOut, pOut.add(dd.scale(0.5)), pOut.add(dd.scale(0.5)).add(0, h, 0), pOut.add(0, h * 0.8, 0),
                                c(0.55F * fade, CARMIM), c(0.55F * fade, VINHO), c(0.0F, VINHO), c(0.0F, VINHO));
                    } else {
                        fita(vc, m, pOut, pOut.add(0, h, 0), 0.07F, 0.01F, c(0.6F * fade, SANGUE), c(0.0F, SANGUE));
                    }
                }
                prevIn = pIn;
                prevOut = pOut;
            }
        }
        // cristais de gelo: brotam onde a frente passou
        for (int i = 0; i < 16; i++) {
            float nasce = 0.18F + 0.5F * hash(i + 21);
            float k = seg(vida, nasce, nasce + 0.14F);
            if (k <= 0.0F) {
                continue;
            }
            float sai = 1.0F - seg(vida, 0.75F, 1.0F);
            double ang = base + (hash(i + 5) - 0.5) * 2 * metade * 0.9;
            double rr = 4.0 + 12.0 * hash(i + 11);
            Vec3 p = g.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
            float h = (0.7F + 1.6F * hash(i + 31)) * ease(k) * sai;
            float inc = 0.25F * (hash(i + 2) - 0.5F);
            Vec3 topo = p.add(inc * h, h, inc * h * 0.6F);
            if (!brilho) {
                tubo(vc, m, p, topo, 0.20F * sai, 0.0F, c(0.85F * sai, VINHO), c(0.85F * sai, ESCURO));
            } else {
                tubo(vc, m, p.add(0, h * 0.2, 0), topo, 0.10F * sai, 0.0F, c(0.55F * sai, GELO), c(0.9F * sai, QUENTE));
            }
        }
    }

    // ================================================================ 3 · OLHOS
    /** O risco de pena até cada alvo e o olho que se abre nele (sempre voltado para quem olha). */
    private static void olhos(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        float alfa = (float) Math.pow(1.0F - vida, 1.2);
        Vec3 de = e.de().add(0, -0.2, 0), ate = e.ate();
        Vec3[] cb = viraCamera(ate);
        float abre = ease(seg(vida, 0.0F, 0.3F)) * (1.0F - seg(vida, 0.8F, 1.0F));
        float L = 0.62F;
        if (!brilho) {
            tubo(vc, m, de, ate, 0.05F, 0.03F, c(0.7F * alfa, ESCURO), c(0.7F * alfa, ESCURO));
            // esclera: lente preta-vinho
            int N = 14;
            for (int i = 0; i < N; i++) {
                float x0 = -L + 2 * L * i / N, x1 = -L + 2 * L * (i + 1) / N;
                float y0 = 0.30F * abre * (1 - (x0 / L) * (x0 / L)), y1 = 0.30F * abre * (1 - (x1 / L) * (x1 / L));
                quad(vc, m, pt(ate, cb, x0, -y0), pt(ate, cb, x0, y0), pt(ate, cb, x1, y1), pt(ate, cb, x1, -y1),
                        c(0.95F * alfa, VINHO), c(0.95F * alfa, VINHO), c(0.95F * alfa, VINHO), c(0.95F * alfa, VINHO));
            }
        } else {
            // pulso viajando pelo risco
            float s = (vida * 3.0F) % 1.0F;
            Vec3 p0 = de.add(ate.subtract(de).scale(s)), p1 = de.add(ate.subtract(de).scale(Math.min(1.0, s + 0.12)));
            fita(vc, m, p0, p1, 0.16F, 0.02F, c(0.9F * alfa, ROSA), c(0.0F, ROSA));
            tubo(vc, m, de, ate, 0.025F, 0.015F, c(0.9F * alfa, SANGUE), c(0.9F * alfa, QUENTE));
            // contorno da pálpebra
            Vec3 pa = null, pb = null;
            for (int i = 0; i <= 14; i++) {
                float x = -L + 2 * L * i / 14.0F, y = 0.30F * abre * (1 - (x / L) * (x / L));
                Vec3 qa = pt(ate, cb, x, y), qb = pt(ate, cb, x, -y);
                if (pa != null) {
                    fita(vc, m, pa, qa, 0.05F, 0.05F, c(0.95F * alfa, ROSA), c(0.95F * alfa, ROSA));
                    fita(vc, m, pb, qb, 0.05F, 0.05F, c(0.95F * alfa, ROSA), c(0.95F * alfa, ROSA));
                }
                pa = qa;
                pb = qb;
            }
            // íris + pupila em fenda
            disco(vc, m, ate.add(cb[1].scale(0.0)), cb[0].scale(0.9), cb[1].scale(0.9), 0.24F * abre, c(0.95F * alfa, SANGUE), c(0.95F * alfa, CARMIM), 18);
            disco(vc, m, ate, cb[0].scale(0.35), cb[1], 0.20F * abre, c(alfa, ESCURO), c(alfa, ESCURO), 12);
            if (vida > 0.5F) {                                                   // o disparo: anel de choque no olho
                float k = seg(vida, 0.5F, 1.0F);
                aroPlano(vc, m, ate, cb[0], cb[1], 0.5F * ease(k), 0.58F * ease(k), c(0.9F * (1 - k), ROSA), c(0.9F * (1 - k), ROSA), 22, 0.0F, 0.0F, 6.2832F);
            }
        }
    }

    /** ponto no plano (direita, cima) a partir de um centro. */
    private static Vec3 pt(Vec3 c0, Vec3[] b, double x, double y) {
        return c0.add(b[0].scale(x)).add(b[1].scale(y));
    }

    // ================================================================ 5 · PENAS
    /**
     * A chuva de penas sobre a área REAL do dano (16 x 16): selo de aviso no chão, 80 penas de
     * verdade (losango com ponta carmesim) caindo com balanço, respingo ao tocar e onda final.
     */
    private static void penas(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        Vec3 g = e.chao().add(0, 0.07, 0);
        float R = 8.0F;
        float aviso = seg(vida, 0.0F, 0.18F) * (1.0F - seg(vida, 0.8F, 1.0F));
        float t = vida * 9.0F;
        Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
        if (!brilho) {
            disco(vc, m, g, X, Z, R, c(0.28F * aviso, VINHO), c(0.0F, VINHO), 36);
        } else {
            // selo: anel tracejado girando + estrela de cinco pontas
            for (int i = 0; i < 24; i++) {
                if (i % 2 == 0) {
                    continue;
                }
                float a0 = i / 24.0F * 6.2832F + t * 0.2F, a1 = a0 + 0.2F;
                aroPlano(vc, m, g, X, Z, R - 0.18F, R, c(0.8F * aviso, ROSA), c(0.8F * aviso, ROSA), 3, 0.0F, a0, a1);
            }
            aroPlano(vc, m, g, X, Z, R * 0.55F - 0.06F, R * 0.55F + 0.06F, c(0.5F * aviso, SANGUE), c(0.5F * aviso, SANGUE), 28, 0.0F, 0.0F, 6.2832F);
            for (int i = 0; i < 5; i++) {
                float a0 = -t * 0.15F + i * 2.5133F, a1 = -t * 0.15F + (i + 2) * 2.5133F;
                Vec3 p0 = g.add(Math.cos(a0) * R * 0.55, 0.02, Math.sin(a0) * R * 0.55), p1 = g.add(Math.cos(a1) * R * 0.55, 0.02, Math.sin(a1) * R * 0.55);
                fita(vc, m, p0, p1, 0.07F, 0.07F, c(0.6F * aviso, ROSA), c(0.6F * aviso, ROSA));
            }
        }
        // as penas
        for (int i = 0; i < 80; i++) {
            float atraso = 0.10F + 0.40F * hash(i + 1);
            float f = (vida - atraso) / 0.34F;
            double ang = hash(i + 100) * 6.2832, rr = R * Math.sqrt(hash(i + 200));
            double x = g.x + Math.cos(ang) * rr, z = g.z + Math.sin(ang) * rr;
            if (f > 0.0F && f < 1.0F) {
                float queda = 1.0F - f * f;
                double y = g.y + 0.3 + 15.0 * queda;
                double bal = Math.sin(t * 3.0 + i) * 0.35 * queda;
                float fade = Math.min(1.0F, f * 6.0F);
                pena3D(vc, m, new Vec3(x + bal, y, z), 0.9F + 0.5F * hash(i + 300), 0.16F, ang, brilho, fade);
            } else if (f >= 1.0F && f < 1.35F) {                                    // respingo ao tocar o chão
                float k = (f - 1.0F) / 0.35F;
                if (brilho) {
                    aroPlano(vc, m, new Vec3(x, g.y, z), X, Z, 0.9F * ease(k) - 0.05F, 0.9F * ease(k), c(0.0F, ROSA), c(0.8F * (1 - k), ROSA), 10, 0.0F, 0.0F, 6.2832F);
                    fita(vc, m, new Vec3(x, g.y, z), new Vec3(x, g.y + 0.9 * ease(k), z), 0.1F * (1 - k), 0.0F, c(0.8F * (1 - k), SANGUE), c(0.0F, SANGUE));
                }
            }
        }
        float fim = seg(vida, 0.55F, 0.95F);
        if (fim > 0.0F && brilho) {                                                 // onda final varrendo a área
            aroPlano(vc, m, g, X, Z, R * ease(fim) - 0.5F, R * ease(fim), c(0.0F, ROSA), c(0.8F * (1 - fim), ROSA), 44, 0.0F, 0.0F, 6.2832F);
        }
    }

    /** uma pena em losango, vertical, de cabeça para baixo (ponta carmesim). */
    private static void pena3D(VertexConsumer vc, Matrix4f m, Vec3 p, float comp, float larg, double ang, boolean brilho, float fade) {
        Vec3 lado = new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(larg);
        Vec3 topo = p.add(0, comp * 0.5, 0), ponta = p.add(0, -comp * 0.5, 0);
        if (!brilho) {
            quad(vc, m, topo, p.subtract(lado), ponta, p.add(lado), c(0.95F * fade, ESCURO), c(0.9F * fade, ESCURO), c(0.9F * fade, VINHO), c(0.9F * fade, ESCURO));
        } else {
            fita(vc, m, p.add(0, comp * 0.1, 0), ponta, larg * 0.7F, 0.0F, c(0.15F * fade, SANGUE), c(0.85F * fade, ROSA));
        }
    }

    // ================================================================ 6 · BANIR
    /**
     * O OLHO QUE BANE: um olho de verdade (lente, íris em raios, pupila em fenda que aperta) aberto
     * de frente para o alvo, com cílios-pena, veias, runas girando, olhar que cai como coluna e um
     * selo que se abre no chão sob o banido.
     */
    private static void banir(VertexConsumer vc, Matrix4f m, Efeito e, float vida, boolean brilho) {
        Vec3 centro = e.de().add(0, 1.4, 0), alvo = e.ate();
        Vec3 f = alvo.subtract(centro);
        f = new Vec3(f.x, 0, f.z);
        f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 R = new Vec3(0, 1, 0).cross(f).normalize(), U = new Vec3(0, 1, 0);
        float abre = vida < 0.19F ? ease(vida / 0.19F) : vida < 0.85F ? 1.0F : Math.max(0.0F, 1.0F - (vida - 0.85F) / 0.15F);
        float L = 3.4F, H = 1.3F * abre;
        float t = vida * 18.0F;
        Vec3 frente = centro.add(f.scale(0.04));
        int N = 40;

        if (!brilho) {
            // fenda preta e lente (esclera) escura com degradê para as bordas
            for (int i = 0; i < N; i++) {
                float x0 = -L + 2 * L * i / N, x1 = -L + 2 * L * (i + 1) / N;
                float y0 = H * (float) Math.pow(Math.max(0.0F, 1 - (x0 / L) * (x0 / L)), 0.8);
                float y1 = H * (float) Math.pow(Math.max(0.0F, 1 - (x1 / L) * (x1 / L)), 0.8);
                float bord0 = Math.abs(x0) / L, bord1 = Math.abs(x1) / L;
                quad(vc, m, pt(centro, new Vec3[] {R, U}, x0, -y0), pt(centro, new Vec3[] {R, U}, x0, y0),
                        pt(centro, new Vec3[] {R, U}, x1, y1), pt(centro, new Vec3[] {R, U}, x1, -y1),
                        mix(VINHO, ESCURO, bord0, 0.96F * abre), mix(VINHO, ESCURO, bord0, 0.96F * abre),
                        mix(VINHO, ESCURO, bord1, 0.96F * abre), mix(VINHO, ESCURO, bord1, 0.96F * abre));
            }
            // pupila em fenda por cima da íris (escura)
            float aperto = vida > 0.52F && vida < 0.82F ? 1.0F - 0.6F * (float) Math.sin((vida - 0.52F) / 0.30F * Math.PI) : 1.0F;
            if (abre > 0.45F) {
                float ia = (abre - 0.45F) / 0.55F;
                disco(vc, m, frente.add(f.scale(0.06)), R.scale(0.30 * aperto * 1.0), U.scale(0.98), (float) Math.min(H * 0.95F, 1.15F) * ia, c(ia, ESCURO), c(ia, ESCURO), 20);
            }
            // veias saindo da lente
            for (int i = 0; i < 12; i++) {
                float s = i / 12.0F, x = -L * 0.9F + 1.8F * L * s, sgn = i % 2 == 0 ? 1 : -1;
                Vec3 p0 = pt(centro, new Vec3[] {R, U}, x, sgn * H * 0.9F * (1 - (x / L) * (x / L)));
                Vec3 p1 = pt(centro, new Vec3[] {R, U}, x + (hash(i) - 0.5F) * 1.2F, sgn * (H + 0.8F + 1.2F * hash(i + 4)));
                fita(vc, m, p0, p1, 0.09F * abre, 0.0F, c(0.7F * abre, VINHO), c(0.0F, VINHO));
            }
        } else {
            // halo de fundo
            disco(vc, m, centro.subtract(f.scale(0.08)), R, U, 5.8F * abre, c(0.0F, SANGUE), c(0.0F, SANGUE), 4);
            disco(vc, m, centro.subtract(f.scale(0.08)), R.scale(1.0), U.scale(0.55), 5.5F * abre, c(0.32F * abre, CARMIM), c(0.0F, CARMIM), 32);
            // contorno da pálpebra, dois fios (brilho + núcleo)
            for (int lado = -1; lado <= 1; lado += 2) {
                Vec3 prev = null;
                for (int i = 0; i <= N; i++) {
                    float x = -L + 2 * L * i / N, y = lado * H * (float) Math.pow(Math.max(0.0F, 1 - (x / L) * (x / L)), 0.8);
                    Vec3 p = pt(frente, new Vec3[] {R, U}, x, y);
                    if (prev != null) {
                        fita(vc, m, prev, p, 0.20F * abre, 0.20F * abre, c(0.75F * abre, SANGUE), c(0.75F * abre, SANGUE));
                        fita(vc, m, prev, p, 0.06F * abre, 0.06F * abre, c(0.95F * abre, QUENTE), c(0.95F * abre, QUENTE));
                    }
                    prev = p;
                }
            }
            // íris: degradê radial + raios
            if (abre > 0.45F) {
                float ia = (abre - 0.45F) / 0.55F;
                float rx = L * 0.55F, ry = Math.min(H * 0.92F, 1.2F);
                disco(vc, m, frente.add(f.scale(0.03)), R.scale(rx / ry), U, ry * ia, c(0.95F * ia, ROSA), c(0.9F * ia, CARMIM), 32);
                for (int i = 0; i < 28; i++) {
                    double ang = i / 28.0 * 6.2832 + t * 0.03;
                    Vec3 p0 = frente.add(R.scale(Math.cos(ang) * rx * 0.36 * ia)).add(U.scale(Math.sin(ang) * ry * 0.36 * ia)).add(f.scale(0.05));
                    Vec3 p1 = frente.add(R.scale(Math.cos(ang) * rx * 0.96 * ia)).add(U.scale(Math.sin(ang) * ry * 0.96 * ia)).add(f.scale(0.05));
                    fita(vc, m, p0, p1, 0.05F, 0.02F, c(0.55F * ia, QUENTE), c(0.15F * ia, SANGUE));
                }
            }
            // cílios-pena em volta
            for (int i = 0; i < 18; i++) {
                double ang = i / 18.0 * 6.2832 + vida * 3.0;
                double ex = Math.cos(ang) * (L + 0.55), ey = Math.sin(ang) * (H + 0.5);
                double comp = 1.0 + 0.5 * Math.sin(vida * 9.0 + i);
                Vec3 p0 = frente.add(R.scale(ex)).add(U.scale(ey));
                Vec3 p1 = p0.add(R.scale(Math.cos(ang) * comp * 0.7)).add(U.scale(Math.sin(ang) * comp * 0.7));
                fita(vc, m, p0, p1, 0.11F * abre, 0.0F, c(0.3F * abre, VINHO), c(0.85F * abre, ROSA));
            }
            // runas: dois anéis tracejados girando em sentidos opostos, no plano do olho
            for (int anel = 0; anel < 2; anel++) {
                float rr = L + 1.3F + anel * 0.8F;
                int marcas = 22 + anel * 8;
                for (int i = 0; i < marcas; i++) {
                    if ((i + anel) % 3 == 0) {
                        continue;
                    }
                    double a0 = i / (double) marcas * 6.2832 + t * (anel == 0 ? 0.08 : -0.11);
                    double a1 = a0 + 6.2832 / marcas * 0.55;
                    Vec3 p0 = frente.add(R.scale(Math.cos(a0) * rr)).add(U.scale(Math.sin(a0) * rr * 0.75));
                    Vec3 p1 = frente.add(R.scale(Math.cos(a1) * rr)).add(U.scale(Math.sin(a1) * rr * 0.75));
                    fita(vc, m, p0, p1, 0.07F * abre, 0.07F * abre, c(0.55F * abre, SANGUE), c(0.55F * abre, SANGUE));
                }
            }
            // o olhar: coluna que arranca o alvo + selo no chão
            if (vida > 0.52F && vida < 0.86F) {
                float k = seg(vida, 0.52F, 0.86F);
                float al = 1.0F - Math.abs(k * 2.0F - 1.0F);
                Vec3 gA = e.chao().add(0, 0.08, 0);
                Vec3 meio = alvo;
                tubo(vc, m, centro.add(f.scale(0.3)), meio, 0.30F * al, 0.55F * al, c(0.35F * al, SANGUE), c(0.7F * al, ROSA));
                tubo(vc, m, centro.add(f.scale(0.3)), meio, 0.10F * al, 0.18F * al, c(0.9F * al, QUENTE), c(0.9F * al, QUENTE));
                tubo(vc, m, gA, gA.add(0, 13.0, 0), 0.55F * al, 0.06F * al, c(0.55F * al, SANGUE), c(0.0F, SANGUE));
                tubo(vc, m, gA, gA.add(0, 13.0, 0), 0.16F * al, 0.02F * al, c(0.95F * al, QUENTE), c(0.0F, QUENTE));
                float sel = ease(seg(vida, 0.52F, 0.64F));
                Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
                aroPlano(vc, m, gA, X, Z, 2.4F * sel - 0.12F, 2.4F * sel, c(0.9F * al, ROSA), c(0.9F * al, ROSA), 40, 0.0F, 0.0F, 6.2832F);
                aroPlano(vc, m, gA, X, Z, 1.5F * sel - 0.07F, 1.5F * sel, c(0.7F * al, SANGUE), c(0.7F * al, SANGUE), 32, 0.0F, 0.0F, 6.2832F);
                for (int i = 0; i < 12; i++) {                                      // marcas do selo girando
                    double a0 = i / 12.0 * 6.2832 + t * 0.12;
                    fita(vc, m, gA.add(Math.cos(a0) * 1.6 * sel, 0.02, Math.sin(a0) * 1.6 * sel), gA.add(Math.cos(a0) * 2.35 * sel, 0.02, Math.sin(a0) * 2.35 * sel),
                            0.08F, 0.08F, c(0.7F * al, ROSA), c(0.7F * al, ROSA));
                }
                esferaUV(vc, m, alvo, 1.9F * (1.0F - k) + 0.2F, c(0.45F * al, SANGUE), 7, 12);
            }
            if (vida > 0.5F && vida < 0.62F) {                                     // clarão no instante do olhar
                float k = seg(vida, 0.5F, 0.62F), al = 1.0F - Math.abs(k * 2 - 1);
                disco(vc, m, frente.add(f.scale(0.08)), R, U, 4.2F * al + 1.0F, c(0.75F * al, QUENTE), c(0.0F, SANGUE), 32);
            }
        }
        if (!brilho && vida > 0.52F && vida < 0.86F) {                             // bolha escura onde o alvo some
            float k = seg(vida, 0.52F, 0.86F), al = 1.0F - Math.abs(k * 2.0F - 1.0F);
            esferaUV(vc, m, alvo, 1.2F * al + 0.1F, c(0.9F * al, ESCURO), 7, 12);
        }
    }

    // ================================================================ primitivas
    /** um quad com cor por vértice. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c0, Vec3 d, int ca, int cb, int cc, int cd) {
        vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(ca);
        vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(cb);
        vc.addVertex(m, (float) c0.x, (float) c0.y, (float) c0.z).setColor(cc);
        vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(cd);
    }

    /** fita voltada para a câmera, com largura e cor diferentes em cada ponta. */
    private static void fita(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, float w0, float w1, int c0, int c1) {
        if (w0 <= 0.0F && w1 <= 0.0F) {
            return;
        }
        Vec3 lado = p1.subtract(p0).cross(p0.add(p1).scale(0.5).subtract(cam));
        if (lado.lengthSqr() < 1e-12) {
            return;
        }
        lado = lado.normalize();
        Vec3 a = lado.scale(w0 * 0.5), b = lado.scale(w1 * 0.5);
        quad(vc, m, p0.add(a), p0.subtract(a), p1.subtract(b), p1.add(b), c0, c0, c1, c1);
    }

    /** tubo quadrado afinando de r0 para r1, cada ponta com sua cor. */
    private static void tubo(VertexConsumer vc, Matrix4f m, Vec3 de, Vec3 ate, float r0, float r1, int c0, int c1) {
        Vec3 dir = ate.subtract(de);
        if (dir.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 u = dir.normalize();
        Vec3 ref = Math.abs(u.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = u.cross(ref).normalize(), b = u.cross(a).normalize();
        for (int lado = 0; lado < 4; lado++) {
            Vec3 o0 = switch (lado) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> a.scale(-1);
                default -> b.scale(-1);
            };
            Vec3 o1 = switch (lado) {
                case 0 -> b;
                case 1 -> a.scale(-1);
                case 2 -> b.scale(-1);
                default -> a;
            };
            quad(vc, m, de.add(o0.scale(r0)), de.add(o1.scale(r0)), ate.add(o1.scale(r1)), ate.add(o0.scale(r1)), c0, c0, c1, c1);
        }
    }

    /** disco (leque de triângulos) num plano qualquer; centro e borda com cores diferentes. */
    private static void disco(VertexConsumer vc, Matrix4f m, Vec3 centro, Vec3 u, Vec3 v, float raio, int cCentro, int cBorda, int lados) {
        if (raio <= 0.0F) {
            return;
        }
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * 6.2832, a1 = (i + 1) / (double) lados * 6.2832;
            Vec3 p0 = centro.add(u.scale(Math.cos(a0) * raio)).add(v.scale(Math.sin(a0) * raio));
            Vec3 p1 = centro.add(u.scale(Math.cos(a1) * raio)).add(v.scale(Math.sin(a1) * raio));
            quad(vc, m, centro, p0, p1, p1, cCentro, cBorda, cBorda, cBorda);
        }
    }

    /** anel (ou arco) num plano qualquer: raios r0..r1, de {@code a0} a {@code a1} (radianos). */
    private static void aroPlano(VertexConsumer vc, Matrix4f m, Vec3 centro, Vec3 u, Vec3 v, float r0, float r1,
            int cIn, int cOut, int lados, float rotacao, float a0, float a1) {
        if (r1 <= 0.0F) {
            return;
        }
        r0 = Math.max(0.0F, r0);
        for (int i = 0; i < lados; i++) {
            double s0 = a0 + (a1 - a0) * i / lados + rotacao, s1 = a0 + (a1 - a0) * (i + 1) / lados + rotacao;
            Vec3 i0 = centro.add(u.scale(Math.cos(s0) * r0)).add(v.scale(Math.sin(s0) * r0));
            Vec3 o0 = centro.add(u.scale(Math.cos(s0) * r1)).add(v.scale(Math.sin(s0) * r1));
            Vec3 o1 = centro.add(u.scale(Math.cos(s1) * r1)).add(v.scale(Math.sin(s1) * r1));
            Vec3 i1 = centro.add(u.scale(Math.cos(s1) * r0)).add(v.scale(Math.sin(s1) * r0));
            quad(vc, m, i0, o0, o1, i1, cIn, cOut, cOut, cIn);
        }
    }

    /** esfera por latitude/longitude (casca única, uma cor). */
    private static void esferaUV(VertexConsumer vc, Matrix4f m, Vec3 c0, float r, int cor, int lat, int lon) {
        if (r <= 0.0F || ((cor >>> 24) & 255) == 0) {
            return;
        }
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * i / lat - Math.PI / 2, p1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double t0 = 6.2832 * j / lon, t1 = 6.2832 * (j + 1) / lon;
                quad(vc, m, esf(c0, r, p0, t0), esf(c0, r, p1, t0), esf(c0, r, p1, t1), esf(c0, r, p0, t1), cor, cor, cor, cor);
            }
        }
    }

    private static Vec3 esf(Vec3 c0, float r, double lat, double lon) {
        return c0.add(Math.cos(lat) * Math.cos(lon) * r, Math.sin(lat) * r, Math.cos(lat) * Math.sin(lon) * r);
    }

    // ================================================================ o portal (inalterado)

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

    // ================================================================ helpers do portal

    /** anel deitado no chão (usado pelo portal). */
    private static void anel(VertexConsumer vc, Matrix4f mat, Vec3 c0, float r, int cor, int lados) {
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            float x0 = (float) (c0.x + Math.cos(a0) * r), z0 = (float) (c0.z + Math.sin(a0) * r);
            float x1 = (float) (c0.x + Math.cos(a1) * r), z1 = (float) (c0.z + Math.sin(a1) * r);
            float xi = r * 0.9F;
            float xi0 = (float) (c0.x + Math.cos(a0) * xi), zi0 = (float) (c0.z + Math.sin(a0) * xi);
            float xi1 = (float) (c0.x + Math.cos(a1) * xi), zi1 = (float) (c0.z + Math.sin(a1) * xi);
            vc.addVertex(mat, xi0, (float) c0.y + 0.05F, zi0).setColor(cor);
            vc.addVertex(mat, x0, (float) c0.y + 0.05F, z0).setColor(cor);
            vc.addVertex(mat, x1, (float) c0.y + 0.05F, z1).setColor(cor);
            vc.addVertex(mat, xi1, (float) c0.y + 0.05F, zi1).setColor(cor);
        }
    }

    /** uma pena: losango comprido (base grossa e escura, ponta fina e carmesim). */
    private static void pena(VertexConsumer vc, Matrix4f mat,
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

    /** ARGB a partir de alfa + rgb. */
    private static int cor(float alfa, int r, int g, int b) {
        int a = Math.max(0, Math.min(255, (int) (alfa * 255.0F)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
