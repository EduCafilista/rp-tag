package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * (5.38.0) <b>O BURACO NEGRO DE ENTRAR NO VAZIO.</b>
 *
 * <p>Pedido do dono: <i>"muda a textura dele entrando no vazio, só partícula fica feio, entrar tem
 * que ser algo lindo, uma sombra negra, um buraco negro, algo assim"</i>.
 *
 * <p>Então nada de enxurrada de partícula: é <b>geometria desenhada na tela</b>, em camadas, na
 * ordem em que um buraco negro de verdade aparece:
 *
 * <ol>
 *   <li><b>lente gravitacional</b> — halo largo e fraco em volta (a luz que dobra);</li>
 *   <li><b>disco de acreção</b> — três anéis achatados girando em velocidades diferentes,
 *       com <i>doppler beaming</i>: um lado do anel fica mais quente que o outro;</li>
 *   <li><b>espirais de queda</b> — fios de luz que mergulham em curva pro centro;</li>
 *   <li><b>horizonte de eventos</b> — o disco negro, opaco, que come tudo;</li>
 *   <li><b>anel de fótons</b> — o fio de luz fininho colado na borda do preto;</li>
 *   <li><b>engolir</b> — no fim o negro cresce e cobre a tela, com um clarão na borda.</li>
 * </ol>
 *
 * <p>Depois do teleporte entra a {@link #iris(long) íris}: a escuridão cobre tudo e <b>abre</b> num
 * círculo, revelando o vazio — é a "sombra negra" abrindo pra você.
 *
 * <p>Tudo é barato: só triângulos/quads e uma vinheta, nada de textura, nada de shader. Roda bem
 * com Iris/Sodium (é a mesma camada de HUD dos outros avisos).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BuracoNegroFx {

    private static long buracoInicio = 0L;
    private static long buracoFim = 0L;
    private static long irisInicio = 0L;
    private static long irisFim = 0L;
    private static long semente = 1L;
    private static boolean somJaTocou = false;

    private BuracoNegroFx() {
    }

    /** começa a entrada (buraco negro). */
    public static void buraco(long ms) {
        buracoInicio = System.currentTimeMillis();
        buracoFim = buracoInicio + Math.max(600L, ms);
        irisInicio = 0L;
        semente = System.nanoTime();
        somJaTocou = false;
    }

    /** a escuridão abrindo depois do teleporte. */
    public static void iris(long ms) {
        irisInicio = System.currentTimeMillis();
        irisFim = irisInicio + Math.max(300L, ms);
        buracoInicio = 0L;
    }

    public static boolean ocupado() {
        long agora = System.currentTimeMillis();
        return (buracoInicio != 0L && agora <= buracoFim) || (irisInicio != 0L && agora <= irisFim);
    }

    /** clareia tudo quando o jogador sai do ar (troca de mundo, respawn...) pra não travar preto. */
    @SubscribeEvent
    public static void aoTick(ClientTickEvent.Post evento) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                buracoInicio = 0L;
                irisInicio = 0L;
            }
        } catch (Throwable ignorado) {
            // enfeite
        }
    }

    // ================================================================ desenho
    public static void desenhar(GuiGraphics g) {
        long agora = System.currentTimeMillis();
        boolean entrando = buracoInicio != 0L && agora <= buracoFim;
        boolean abrindo = irisInicio != 0L && agora <= irisFim;
        if (!entrando && !abrindo) {
            return;
        }
        try {
            int w = g.guiWidth();
            int h = g.guiHeight();
            float esc = Math.min(w, h);
            float cx = w * 0.5F;
            float cy = h * 0.47F;

            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

            if (entrando) {
                float u = AvisoFxUtil.cl((agora - buracoInicio)
                        / (float) Math.max(1L, buracoFim - buracoInicio));
                desenharBuraco(b, w, h, esc, cx, cy, u);
            }
            if (abrindo) {
                float u = AvisoFxUtil.cl((agora - irisInicio)
                        / (float) Math.max(1L, irisFim - irisInicio));
                desenharIris(b, w, h, esc, cx, cy, u);
            }
            b.flush();
            AvisoFxUtil.normal();
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: o buraco negro do vazio falhou (o jogo segue)", t);
            buracoInicio = 0L;
            irisInicio = 0L;
        }
    }

    private static void desenharBuraco(AvisoFxUtil.Buf b, int w, int h, float esc,
            float cx, float cy, float u) {
        AvisoFxUtil.aditivo();

        // ---- o mundo escurece de leve (vinheta que fecha) --------------------
        float fechando = AvisoFxUtil.ss(0.0F, 0.85F, u);
        b.vinheta(w, h, esc * (1.05F - 0.50F * fechando), 0x040209, 0.46F + 0.48F * fechando);

        // ---- raio do horizonte de eventos -----------------------------------
        // cresce devagar e depois DISPARA (o engolir)
        float nascendo = AvisoFxUtil.eo(AvisoFxUtil.cl(u * 1.15F));
        float engolir = AvisoFxUtil.ss(0.70F, 1.0F, u);
        float rCore = esc * (0.010F + 0.175F * nascendo) * (1.0F + 7.5F * engolir * engolir);

        // ---- 1) lente gravitacional: halo largo e fraco ---------------------
        b.brilho(cx, cy, rCore * 3.6F, 0x3A1F74, 0.12F + 0.16F * u);
        b.brilho(cx, cy, rCore * 2.2F, 0x7A4FD0, 0.13F + 0.15F * u);
        b.brilho(cx, cy, rCore * 1.35F, 0xC9A8FF, 0.12F + 0.16F * u);

        // ---- 2) espirais de queda (fios de luz mergulhando) -----------------
        desenharEspirais(b, esc, cx, cy, rCore, u);

        // ---- 3) disco de acreção (3 anéis achatados girando) ----------------
        desenharDisco(b, esc, cx, cy, rCore, u, 2.35F, 0.30F, 0x3A1155, 0xFF9BE0, 1.00F);
        desenharDisco(b, esc, cx, cy, rCore, u, 1.72F, 0.22F, 0x6B2CFF, 0xFFE6A8, 0.80F);
        desenharDisco(b, esc, cx, cy, rCore, u, 1.34F, 0.15F, 0x9C7BFF, 0xFFFFFF, 0.55F);

        // ---- 4) o buraco: preto chapado, opaco ------------------------------
        AvisoFxUtil.normal();
        b.circ(cx, cy, rCore, AvisoFxUtil.col(0x000000, 0.985F));

        // ---- 5) anel de fótons (o fio de luz na borda) ----------------------
        AvisoFxUtil.aditivo();
        float pulso = 0.80F + 0.20F * AvisoFxUtil.sin(u * 22.0F);
        b.anel(cx, cy, rCore * 1.045F, 108, Math.max(1.5F, esc * 0.0045F), u * 3.0F,
                0xEDE8FF, 0.90F * pulso);
        b.anel(cx, cy, rCore * 1.11F, 96, Math.max(1.0F, esc * 0.0022F), -u * 5.0F,
                0xB79BFF, 0.55F * pulso);

        // ---- 6) o engolir: o negro cobre a tela, com clarão na borda --------
        if (engolir > 0.001F) {
            float cobre = engolir * engolir;
            b.rect(0, 0, w, h, AvisoFxUtil.col(0x000000, cobre));
            float borda = rCore * 1.02F;
            if (borda < esc * 2.2F) {
                b.anel(cx, cy, borda, 120, Math.max(2.0F, esc * 0.006F) * (1.0F - 0.5F * engolir),
                        u * 4.0F, 0xFFF3D6, 0.55F * (1.0F - cobre));
            }
        }
        AvisoFxUtil.normal();
    }

    /** três anéis achatados, cada um com sua inclinação, giro e "doppler". */
    private static void desenharDisco(AvisoFxUtil.Buf b, float esc, float cx, float cy,
            float rCore, float u, float raioMul, float achatamento, int corFria, int corQuente,
            float peso) {
        float rxBase = rCore * raioMul + esc * 0.012F;
        float giro = u * (2.4F + raioMul * 1.1F);
        // (5.38.0) faixa de verdade, não "arame": 7 anéis concêntricos com alfa caindo pra fora
        for (int anelFino = 0; anelFino < 7; anelFino++) {
            float q = anelFino / 6.0F;                    // 0 = borda de dentro, 1 = fora
            float rx = rxBase * (1.0F + 0.26F * q);
            float ry = rx * achatamento + esc * 0.004F;
            float alfaAnel = peso * (0.42F - 0.30F * q) * (0.35F + 0.65F * u);
            int fatias = 64;
            for (int i = 0; i < fatias; i++) {
                float a0 = AvisoFxUtil.TAU * i / fatias + giro * (1.0F - 0.35F * q);
                float a1 = AvisoFxUtil.TAU * (i + 1) / fatias + giro * (1.0F - 0.35F * q);
                // doppler beaming: o lado direito do disco fica mais quente e brilhante
                float doppler = 0.38F + 0.62F * Math.max(0.0F, AvisoFxUtil.cos(a0 - 0.7F));
                int cor = AvisoFxUtil.mix(corFria, corQuente, 0.20F + 0.80F * doppler);
                float alfa = alfaAnel * (0.35F + 0.85F * doppler);
                float x0 = cx + AvisoFxUtil.cos(a0) * rx;
                float y0 = cy + AvisoFxUtil.sin(a0) * ry;
                float x1 = cx + AvisoFxUtil.cos(a1) * rx;
                float y1 = cy + AvisoFxUtil.sin(a1) * ry;
                b.seg(x0, y0, x1, y1, Math.max(1.6F, esc * (0.006F + 0.004F * q)),
                        AvisoFxUtil.col(cor, alfa), AvisoFxUtil.col(cor, alfa));
            }
        }
        // os dois "flares" laterais (o disco quente estourando nas pontas)
        for (int lado = 0; lado < 2; lado++) {
            float sinal = lado == 0 ? -1.0F : 1.0F;
            float fx = cx + sinal * rxBase * 1.05F;
            b.brilho(fx, cy, rxBase * 0.34F, corQuente, peso * 0.34F * (0.35F + 0.65F * u));
        }
    }

    /** fios de luz caindo em espiral (o material sendo puxado pra dentro). */
    private static void desenharEspirais(AvisoFxUtil.Buf b, float esc, float cx, float cy,
            float rCore, float u) {
        int fios = 26;
        for (int i = 0; i < fios; i++) {
            float s1 = AvisoFxUtil.rnd(i * 37 + 3, (int) (semente & 0xFFFF));
            float s2 = AvisoFxUtil.rnd(i * 53 + 7, (int) ((semente >> 7) & 0xFFFF));
            // cada fio tem sua fase: nasce longe, cai e some perto do horizonte
            float fase = AvisoFxUtil.fr(s1 + u * (0.9F + s2 * 0.9F));
            float dist = esc * (0.62F * (1.0F - fase) + 0.035F);
            if (dist < rCore * 1.02F) {
                continue;
            }
            float ang = s2 * AvisoFxUtil.TAU + fase * 5.4F + u * 2.2F;
            float comp = esc * (0.055F + 0.075F * (1.0F - fase));
            float x0 = cx + AvisoFxUtil.cos(ang) * dist;
            float y0 = cy + AvisoFxUtil.sin(ang) * dist * 0.72F;
            float ang2 = ang + 0.22F + 0.30F * (1.0F - fase);
            float d2 = dist * (1.0F - comp / esc * 2.2F);
            float x1 = cx + AvisoFxUtil.cos(ang2) * d2;
            float y1 = cy + AvisoFxUtil.sin(ang2) * d2 * 0.72F;
            float alfa = (0.16F + 0.34F * fase) * (0.4F + 0.6F * u);
            int cor = AvisoFxUtil.mix(0xB79BFF, 0xFFF2C8, fase);
            b.seg(x0, y0, x1, y1, Math.max(1.0F, esc * 0.0022F * (1.4F - fase)),
                    AvisoFxUtil.col(cor, alfa), AvisoFxUtil.col(cor, 0.0F));
        }
    }

    /** a escuridão abrindo: negro cobrindo tudo, menos um círculo que cresce. */
    private static void desenharIris(AvisoFxUtil.Buf b, int w, int h, float esc,
            float cx, float cy, float u) {
        float abre = AvisoFxUtil.ss(0.0F, 1.0F, u);
        float buraco = esc * 0.02F + esc * 1.75F * abre * abre;
        float diag = (float) Math.sqrt(w * (double) w + h * (double) h);

        AvisoFxUtil.normal();
        // anel preto: do buraco até muito além da tela
        int fatias = 96;
        float fora = Math.max(diag, buraco) * 1.15F;
        for (int i = 0; i < fatias; i++) {
            float a0 = AvisoFxUtil.TAU * i / fatias;
            float a1 = AvisoFxUtil.TAU * (i + 1) / fatias;
            float c0 = AvisoFxUtil.cos(a0), s0 = AvisoFxUtil.sin(a0);
            float c1 = AvisoFxUtil.cos(a1), s1 = AvisoFxUtil.sin(a1);
            int cor = AvisoFxUtil.col(0x020104, 1.0F);
            b.quad(cx + c0 * buraco, cy + s0 * buraco, cor,
                    cx + c1 * buraco, cy + s1 * buraco, cor,
                    cx + c1 * fora, cy + s1 * fora, cor,
                    cx + c0 * fora, cy + s0 * fora, cor);
        }
        // a borda do buraco brilha (o "lábio" da sombra se abrindo)
        AvisoFxUtil.aditivo();
        b.anel(cx, cy, Math.max(1.5F, buraco), 108, Math.max(1.6F, esc * 0.005F), 0.0F,
                0xC9B0FF, 0.62F * (1.0F - abre * 0.75F));
        b.brilho(cx, cy, buraco * 1.35F + esc * 0.02F, 0x6C3BD6, 0.10F * (1.0F - abre));
        // no fim, o negro todo desbota (não fica tarja preta na tela)
        if (abre > 0.85F) {
            AvisoFxUtil.normal();
            float sobra = (abre - 0.85F) / 0.15F;
            b.rect(0, 0, w, h, AvisoFxUtil.col(0x020104, 0.55F * sobra));
        }
        AvisoFxUtil.normal();
    }

    /** toca os sons da entrada (chamado pelo handler da cena). */
    public static void somEntrada() {
        if (somJaTocou) {
            return;
        }
        somJaTocou = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            mc.player.playSound(SoundEvents.END_PORTAL_SPAWN, 0.55F, 0.55F);
            mc.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.75F, 0.62F);
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65F, 0.5F);
        } catch (Throwable ignorado) {
            // som é enfeite
        }
    }
}
