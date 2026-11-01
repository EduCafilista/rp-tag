package dev.rptag.client;

import dev.rptag.NeptunePackets;
import dev.rptag.RPTagMod;
import static dev.rptag.client.AvisoFxUtil.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * (5.4.0) EFEITOS DE TELA DO EVENTO NEPTUNE.
 *
 * <p>Nada aqui decide nada: o servidor manda ({@link NeptunePackets}) e esta
 * classe só desenha. A regra de ouro do mod continua: <b>qualquer erro desliga
 * o efeito desta sessão</b> (try/catch em tudo), o jogo nunca cai por causa de
 * um efeito.
 *
 * <h2>TERREMOTO</h2>
 * A câmera treme o tempo todo (roll/yaw/pitch em frequências diferentes, força
 * vinda do pacote) e a tela ganha a "camada de espaço" — nebulosa girando devagar
 * por cima com blend aditivo (é o blur/bloom do vazio, o mesmo tipo de imagem
 * que o dono pediu), mais poeira subindo nas bordas. Nos pulsos de LENTIDÃO a
 * tela embaça de verdade: duas cópias do desenho deslocadas (visão dupla) e um
 * veio de calor atravessando.
 *
 * <h2>SAÍDA (verde)</h2>
 * O jogador que vai sair do servidor vê a tela dissolvendo em verde: brilho
 * central crescendo, vinheta verde fechando e "pixels" subindo. Os outros
 * jogadores não veem nada na tela (só as partículas no mundo).
 *
 * <h2>SUSTO (o bicho)</h2>
 * A face pula do centro pra tela inteira em ~0,25 s, com tremor, flash vermelho
 * e chiado — e some em 2,6 s.
 *
 * <h2>NEBULOSA</h2>
 * O "apagar da existência": espaço abrindo no meio da tela (nebulosa + buraco
 * negro pulsando) por alguns segundos.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class NeptuneFx {

    private NeptuneFx() {
    }

    private static final ResourceLocation NEBULOSA =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/nebulosa.png");
    private static final ResourceLocation BICHO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bicho.png");
    private static final ResourceLocation DIM_EXILIO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio");
    private static final ResourceLocation GLOW_VERDE =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/glow_verde.png");
    private static final ResourceLocation GLOW_BRANCO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/glow_branco.png");

    // --------------------------------------------------------------- estado
    private static volatile boolean quakeAtivo = false;
    private static volatile float quakeForca = 0.0F;
    private static volatile long quakeFim = 0L;
    private static volatile boolean tremorCampea = false;

    private static volatile long saidaInicio = 0L;
    private static volatile long saidaFim = 0L;
    private static volatile String saidaNome = "";

    private static volatile long sustoInicio = 0L;
    private static volatile float sustoForca = 0.0F;
    private static volatile long sustoSemente = 0L;

    private static volatile long nebInicio = 0L;
    private static volatile long nebFim = 0L;
    private static volatile boolean nebForte = false;

    // (5.6.0) CENAS do terremoto: rachadura na tela, queda e estouro
    private static volatile long rachInicio = 0L, rachFim = 0L;
    private static volatile float rachForca = 1.0F;
    private static volatile long rachSemente = 0L;
    private static volatile long quedaInicio = 0L, quedaFim = 0L;
    private static volatile long estouroInicio = 0L, estouroFim = 0L;

    private static volatile boolean souDeus = false;
    private static volatile boolean trilhaOn = false;

    /** flags de falha — um erro desliga o efeito, nunca o jogo */
    private static volatile boolean quakeOff = false;
    private static volatile boolean saidaOff = false;
    private static volatile boolean sustoOff = false;
    private static volatile boolean nebOff = false;

    // ------------------------------------------------------------- pacotes
    public static void onTerremoto(NeptunePackets.Terremoto p) {
        quakeAtivo = p.ativo();
        quakeForca = Math.max(0.0F, Math.min(1.5F, p.forca()));
        quakeFim = System.currentTimeMillis() + Math.max(0, p.segundos()) * 1000L;
        tremorCampea = p.travado();
    }

    public static void onSaida(NeptunePackets.Saida p) {
        long agora = System.currentTimeMillis();
        saidaInicio = agora;
        saidaFim = agora + Math.max(1, p.segundos()) * 1000L;
        saidaNome = p.nome() == null ? "" : p.nome();
        saidaOff = false;
    }

    public static void onSusto(NeptunePackets.Susto p) {
        sustoInicio = System.currentTimeMillis();
        sustoForca = Math.max(0.15F, Math.min(1.0F, p.intensidade()));
        sustoSemente = p.semente();
        sustoOff = false;
    }

    /** (5.6.0) chega uma CENA do terremoto (rachadura, queda ou estouro). */
    public static void onCena(NeptunePackets.Cena p) {
        long agora = System.currentTimeMillis();
        int ms = Math.max(200, p.ms());
        switch (p.tipo()) {
            case NeptunePackets.Cena.CENA_RACHADURA -> {
                rachInicio = agora;
                rachFim = agora + ms;
                rachForca = Math.max(0.2F, Math.min(1.5F, p.forca()));
                rachSemente = p.semente();
            }
            case NeptunePackets.Cena.CENA_QUEDA -> {
                quedaInicio = agora;
                quedaFim = agora + ms;
            }
            case NeptunePackets.Cena.CENA_ESTOURO -> {
                estouroInicio = agora;
                estouroFim = agora + ms;
                // (5.7.2) o estouro agora é o próprio desenharEstouro (a fenda
                // no chão). A rachadura "de vidro" da tela entra FRACA e curta —
                // antes ela vinha com forca 1.2 por ~4 s e dava cara de tela
                // rachada de celular (o dono não gostou).
                rachInicio = agora;
                rachFim = agora + 1100L;
                rachForca = 0.42F;
                rachSemente = p.semente();
            }
            default -> {
                // tipo desconhecido: ignora (jar novo, cliente velho)
            }
        }
    }

    public static void onAura(NeptunePackets.Aura p) {
        souDeus = p.deus();
        trilhaOn = p.trilha();
        // (5.7.1) AURA DO VAZIO do protótipo do dono (rachaduras, tentáculos,
        // estilhaços, fumaça, granulação) enquanto o deus estiver ligado
        if (p.deus()) {
            VazioAuraDarkFx.ligar();
        } else {
            VazioAuraDarkFx.desligar();
        }
    }

    public static void onNebulosa(NeptunePackets.Nebulosa p) {
        long agora = System.currentTimeMillis();
        if (p.segundos() <= 0) {
            nebFim = agora;
            return;
        }
        nebInicio = agora;
        nebFim = agora + p.segundos() * 1000L;
        nebForte = p.forte();
        nebOff = false;
    }

    // ---------------------------------------------------------------- câmera
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (quakeOff) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (!quakeAtivo || now > quakeFim) {
                return;
            }
            float t = now * 0.001F;
            // o tremor tem DUAS vozes: uma grave (o chão balançando) e outra
            // rápida (o tranco dos escombros) — igual à sensação de terremoto
            float f = quakeForca;
            float roll = (2.6F * (float) Math.sin(t * 5.1) + 1.1F * (float) Math.sin(t * 17.3)) * f;
            float yaw = (1.3F * (float) Math.sin(t * 4.3 + 1.1) + 0.5F * (float) Math.sin(t * 21.7)) * f;
            float pitch = (1.0F * (float) Math.sin(t * 6.7 + 0.4) + 0.4F * (float) Math.sin(t * 25.1)) * f;
            // tremor forte (pulso "travado") = soma um chacoalho curto
            if (tremorCampea) {
                roll += 1.2F * f * (float) Math.sin(t * 40.0);
                pitch += 0.8F * f * (float) Math.sin(t * 37.0);
            }
            // (5.6.0) QUEDA: ele está no fundo da fenda -> a câmera tomba e
            // olha pro chão (é o "não consegue se levantar" que dá pra ver)
            if (quedaInicio != 0L && now < quedaFim) {
                float kq = (float) (now - quedaInicio) / (float) Math.max(1L, quedaFim - quedaInicio);
                float tomba = (float) Math.sin(Math.PI * Math.min(1.0F, kq * 1.4F));
                roll += 14.0F * tomba;
                pitch += 22.0F * tomba;
                yaw += 5.0F * tomba * (float) Math.sin(t * 1.7);
            }
            event.setRoll(event.getRoll() + roll);
            event.setYaw(event.getYaw() + yaw);
            event.setPitch(event.getPitch() + pitch);
        } catch (Throwable erro) {
            quakeOff = true;
            RPTagMod.LOGGER.error("RP Tag: tremor de camera do terremoto desligado", erro);
        }
    }


    // ------------------------------------------------------------- desenho
    public static void desenhar(GuiGraphics g) {
        try {
            desenharTerremoto(g);
        } catch (Throwable t) {
            quakeOff = true;
            RPTagMod.LOGGER.error("RP Tag: camada do terremoto desligada", t);
        }
        try {
            desenharRachadura(g);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: camada da rachadura desligada", t);
        }
        try {
            desenharQueda(g);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: camada da queda desligada", t);
        }
        try {
            desenharEstouro(g);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: camada do estouro desligada", t);
        }
        try {
            desenharSaida(g);
        } catch (Throwable t) {
            saidaOff = true;
            RPTagMod.LOGGER.error("RP Tag: camada da saida verde desligada", t);
        }
        try {
            desenharSusto(g);
        } catch (Throwable t) {
            // (5.7.3) NÃO desliga mais pra sempre: se uma render falhar (textura
            // demorando a carregar, por exemplo), o próximo susto tenta de novo.
            RPTagMod.LOGGER.error("RP Tag: falha ao desenhar o susto do bicho (tenta no proximo)", t);
        }
        try {
            desenharNebulosa(g);
        } catch (Throwable t) {
            nebOff = true;
            RPTagMod.LOGGER.error("RP Tag: camada da nebulosa desligada", t);
        }
        try {
            desenharExilio(g);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: camada do exilio desligada", t);
        }
        try {
            desenharAuraDeus(g);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: camada da aura desligada", t);
        }
    }

    /** Real blur do jogo (menu de pausa usa); se falhar, segue sem. */
    private static void desfocar(float forca) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        try {
            mc.gameRenderer.processBlurEffect(forca);
        } catch (Throwable ignorado) {
            // versões/shader packs diferentes: sem blur, o resto do efeito fica
        }
    }

    // ---- TERREMOTO: nebulosa de espaço + poeira + visão dupla quando travado
    private static void desenharTerremoto(GuiGraphics g) {
        if (quakeOff) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!quakeAtivo || now > quakeFim) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float f = quakeForca;

        // (a) a camada de ESPAÇO: nebulosa girando devagar, aditiva, fraca
        //     (é o "blur" que combina com o evento — o universo se abrindo)
        float ang = (now % 240000L) / 240000.0F * 360.0F;
        float a1 = 0.10F + 0.05F * (float) Math.sin(now * 0.0013);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.42F, 0.40F, 0.72F, a1 * f);
        // desenha maior que a tela e deslocado (o giro "passa" pela borda)
        int m = (int) (Math.max(w, h) * 0.42F);
        g.blit(NEBULOSA, -m / 2 + (int) (Math.cos(Math.toRadians(ang)) * m * 0.3F),
                -m / 2 + (int) (Math.sin(Math.toRadians(ang)) * m * 0.3F),
                0, 0, w + m, h + m, w + m, h + m);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);

        // (a2) BLUR de verdade no pulso travado (o mesmo post-process do menu
        //      de pausa) — é o "estunado" com cara de espaço, e sai sozinho
        if (tremorCampea) {
            float pulso = 0.5F + 0.5F * (float) Math.sin(now * 0.006);
            desfocar(1.6F + 2.8F * pulso * f);
        }

        // (b) (5.4.1) POEIRA NA BORDA — antes eram quadradinhos marrons soltos
        // que o dono leu como fumaça estranha. Agora é um pó fino rente ao chão:
        // risquinhos curtos, quase transparentes, só nos 12% de baixo da tela.
        int borda = (int) (h * 0.12F);
        for (int i = 0; i < 34; i++) {
            float fase = ((now * 0.00022F + i * 0.093F) % 1.0F);
            int x = (int) ((i * 97 % w));
            int y = (int) (h - fase * borda);
            int alfa = (int) (26 * (1 - fase) * f);
            if (alfa > 2) {
                g.fill(x, y, x + 8 + (i % 4) * 5, y + 1, (alfa << 24) | 0x8A7457);
            }
        }

        // (b2) (5.6.0) DETRITOS CAINDO: lascas de terra/pedra passando pela tela
        // de cima pra baixo o tempo todo (é o "está tudo desabando em volta")
        for (int i = 0; i < 26; i++) {
            float s1 = rnd(i * 11 + 3, 7);
            float s2 = rnd(i * 5 + 9, 3);
            float vel = 0.22F + s2 * 0.55F;
            float fase = ((now * 0.00035F * vel) + s1) % 1.0F;
            int x = (int) (s1 * w) + (int) (Math.sin(now * 0.002F + i) * 10);
            int y = (int) (fase * h * 1.2F) - (int) (h * 0.1F);
            int tamy = 3 + (i % 3) * 2;
            int alfa = (int) (120 * f * (1F - Math.abs(fase - 0.5F) * 1.4F));
            if (alfa > 3) {
                g.fill(x, y, x + 2 + (i % 2), y + tamy, (Math.min(255, alfa) << 24) | 0x6E5A42);
            }
        }

        // (c) PULSO TRAVADO: visão dupla = duas faixas escuras deslocadas,
        //     que é o que o olho faz quando a cabeça chacoalha
        if (tremorCampea) {
            int alfa = (int) (17 * f);
            for (int i = 0; i < 9; i++) {
                int y = (int) ((i / 9.0F) * h + Math.sin(now * 0.006 + i) * 4);
                int off = (int) (Math.sin(now * 0.004 + i * 2) * 7);
                g.fill(off + 6, y, w + off - 6, y + h / 46, (alfa << 24) | 0x000000);
            }
            // (5.6.0) véu de terra no rodapé (a poeira que levanta do chão)
            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf bp = new AvisoFxUtil.Buf(g.pose().last().pose());
            for (int i = 0; i < 10; i++) {
                float kk = i / 10F;
                bp.rect(0, h - h * 0.22F + kk * h * 0.22F, w, h * 0.022F + 1F,
                        AvisoFxUtil.col(0x2A1E12, (0.04F + 0.10F * kk) * f));
            }
            bp.flush();
            AvisoFxUtil.normal();
            // veio de calor (linhas claras finas atravessando)
            int alfa2 = (int) (22 * f);
            for (int i = 0; i < 5; i++) {
                int y = (int) (((now * 0.02 + i * 61) % h));
                g.fill(0, y, w, y + 1, (alfa2 << 24) | 0xFFFFFF);
            }
        }
    }

    // ===================== (5.6.0) RACHADURA: o chão se abrindo na TELA ====
    // O dono pediu "efeito de algo se rachando". Aqui a tela racha de verdade:
    // 10 braços saem das bordas na direção do centro, serpenteando, crescendo
    // com o tempo, com galhos menores saindo de cada um. Traço escuro (a
    // abertura) com um fio claro na borda (a lasca) e poeira na ponta.
    private static void desenharRachadura(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (rachInicio == 0L || now > rachFim) {
            return;
        }
        long dur = Math.max(1L, rachFim - rachInicio);
        float u = (now - rachInicio) / (float) dur;            // 0..1
        float f = rachForca;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float escala = Math.min(w, h);

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        float cresce = ss(0.0F, 0.42F, u);          // a rachadura se abrindo
        float some = 1.0F - ss(0.80F, 1.0F, u);     // e cicatrizando no fim
        float alfaBase = cl(cresce) * some * Math.min(1.15F, f);
        if (alfaBase <= 0.01F) {
            AvisoFxUtil.normal();
            return;
        }

        // ---------------------------------------------------------- (1) O ABISMO
        // O chão se ABRE no rodapé da tela: uma boca preta que cresce pra cima,
        // com a boca jagada (zigue-zague) e a beirada iluminada (a lasca). É o
        // que faz ler "algo se rachando" em vez de "rabisco na tela".
        float fundura = h * 0.30F * cl(cresce) * Math.min(1.3F, f);
        int degraus = 16;
        float[] lx = new float[degraus + 1];
        float[] ly = new float[degraus + 1];
        for (int i = 0; i <= degraus; i++) {
            float t01 = i / (float) degraus;
            // perfil da BOCA: a terra AFUNDOU — mais fundo nas laterais, mais
            // raso no meio (é cova, não morro) + serra (a beirada quebrada)
            float perfil = 0.30F + 0.62F * Math.abs(t01 - 0.5F) * 2.0F;
            float serra = (rnd(i * 7 + 1, (int) (rachSemente & 0xFFFF)) - 0.5F) * 0.60F;
            perfil = Math.max(0.10F, Math.min(1.0F, perfil + serra * 0.42F));
            lx[i] = t01 * w;
            ly[i] = h - fundura * perfil;
        }
        // miolo do abismo (preto) e o "fundo" mais claro lá no fundo
        for (int i = 0; i < degraus; i++) {
            b.quad(lx[i], ly[i], col(0x000000, 0.94F * alfaBase),
                    lx[i + 1], ly[i + 1], col(0x000000, 0.94F * alfaBase),
                    lx[i + 1], h + 2F, col(0x000000, 0.94F * alfaBase),
                    lx[i], h + 2F, col(0x000000, 0.94F * alfaBase));
        }
        // beirada: fio claro (a terra lascada pegando luz) + halo terroso
        for (int i = 0; i < degraus; i++) {
            b.seg(lx[i], ly[i], lx[i + 1], ly[i + 1], 2.0F + 1.2F * Math.min(1.2F, f),
                    col(0xF3E6CC, 0.72F * alfaBase), col(0xF3E6CC, 0.72F * alfaBase));
            b.seg(lx[i], ly[i] - 2.5F, lx[i + 1], ly[i + 1] - 2.5F, 6.0F,
                    col(0x6B563B, 0.30F * alfaBase), col(0x6B563B, 0.30F * alfaBase));
        }
        // pedaços de terra despencando pra dentro do abismo
        for (int i = 0; i < 26; i++) {
            float s1 = rnd(i * 13 + 3, (int) (rachSemente & 0xFFFF));
            float s2 = rnd(i * 29 + 5, (int) (rachSemente & 0xFFFF));
            float caindo = ((now * 0.0011F) + s1 * 3.0F) % 1.0F;
            float px = s1 * w;
            float py = h - fundura - 6F + caindo * (fundura + 20F);
            if (py > h) {
                continue;
            }
            float lado = 2F + 4F * s2;
            b.quad(px, py, col(0x3A2C1B, 0.85F * alfaBase),
                    px + lado, py + 1.5F, col(0x3A2C1B, 0.85F * alfaBase),
                    px + lado - 1F, py + lado, col(0x3A2C1B, 0.85F * alfaBase),
                    px - 1.5F, py + lado - 1F, col(0x3A2C1B, 0.85F * alfaBase));
        }

        // ------------------------------------------------- (2) AS RACHADURAS
        // 9 rachaduras FINAS e serrilhadas subindo do abismo pras laterais e
        // pra cima (parecem vidro/pedra quebrada, não galho de árvore).
        int rachaduras = 11;
        for (int n = 0; n < rachaduras; n++) {
            float s1 = rnd(n * 17 + 2, (int) (rachSemente & 0xFFFF));
            float s2 = rnd(n * 31 + 9, (int) (rachSemente & 0xFFFF));
            // sai da boca do abismo (na base da tela)
            float x = s1 * w;
            float y = h - fundura * 0.75F;
            // direção: pra cima e pro lado
            float ang = (float) Math.toRadians(-90.0 + (s2 - 0.5F) * 105.0);
            int segs = 6 + (n % 5);
            float comprimento = cresce * segs;
            float largo = 2.4F + 3.4F * Math.min(1.2F, f);
            for (int i = 1; i <= segs; i++) {
                if (comprimento < i) {
                    break;
                }
                ang += (rnd(n * 41 + i, (int) ((now / 110) % 991)) - 0.5F) * 0.85F;
                float passo = escala * 0.055F;
                float nx = x + cos(ang) * passo;
                float ny = y + sin(ang) * passo;
                float afina = (float) Math.pow(1.0F - i / (float) segs, 1.3F);
                float larg = largo * (0.45F + 0.75F * afina);
                // a abertura (escura)
                b.seg(x, y, nx, ny, larg,
                        col(0x000000, 0.88F * alfaBase), col(0x000000, 0.55F * alfaBase));
                // a lasca (clara) encostada na abertura
                b.seg(x, y - larg * 0.55F, nx, ny - larg * 0.55F, Math.max(0.8F, larg * 0.30F),
                        col(0xF2E5CB, 0.55F * alfaBase), col(0xF2E5CB, 0.0F));
                // galhinho
                if (i == 2 || i == 4) {
                    float ga = ang + (rnd(n + i, 7) - 0.5F) * 2.4F;
                    float gl = passo * 0.7F;
                    b.seg(nx, ny, nx + cos(ga) * gl, ny + sin(ga) * gl, Math.max(0.8F, larg * 0.6F),
                            col(0x000000, 0.6F * alfaBase), col(0x000000, 0.0F));
                }
                x = nx;
                y = ny;
            }
            // poeira saindo da ponta (pontinhos, nada de bolota)
            if (cresce < 0.999F) {
                for (int p = 0; p < 10; p++) {
                    float pa = rnd(n * 19 + p, (int) ((now / 60) % 991));
                    float pb = rnd(n * 23 + p + 3, (int) ((now / 60) % 983));
                    disco(b, x + (pa - 0.5F) * 20F, y + (pb - 0.5F) * 20F,
                            1.0F + 1.8F * pa, 0x7A674E, 0.26F * alfaBase, 6);
                }
            }
        }

        // vinheta de terra nas bordas (o mundo ficando longe)
        b.vinheta(w, h, escala * 0.20F, 0x1A120A, 0.42F * alfaBase);
        b.flush();
        AvisoFxUtil.normal();
    }

    // ===================== (5.6.0) QUEDA: o jogador caiu na fenda =========
    // Ele caiu 2 blocos e está imóvel: a tela ganha poeira subindo de baixo,
    // uma vinheta de terra fechando os cantos e um tremor curto no impacto.
    private static void desenharQueda(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (quedaInicio == 0L || now > quedaFim) {
            return;
        }
        long dur = Math.max(1L, quedaFim - quedaInicio);
        float u = cl((now - quedaInicio) / (float) dur);
        float entra = ss(0.0F, 0.10F, u);                     // o baque
        float sai = 1.0F - ss(0.85F, 1.0F, u);
        float k = entra * sai;
        int w = g.guiWidth();
        int h = g.guiHeight();

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        // (a) o "chão" na altura dos olhos: faixa de terra cobrindo a parte de
        //     baixo da tela (ele está dentro da cova, o mundo fica na altura da
        //     cabeça) — degradê, sem canto
        int faixas = 12;
        float alturaTerra = h * 0.30F;
        for (int i = 0; i < faixas; i++) {
            float kk = i / (float) faixas;
            b.rect(0, h - alturaTerra + kk * alturaTerra, w, alturaTerra / faixas + 1F,
                    col(0x1C1409, (0.10F + 0.42F * kk) * k));
        }
        // (b) poeira subindo (bolinhas de pó da cova)
        int n = 46;
        for (int i = 0; i < n; i++) {
            float s1 = rnd(i * 3 + 1, (int) (quedaInicio & 0xFFFFF));
            float s2 = rnd(i * 7 + 3, (int) (quedaInicio & 0xFFFFF));
            float fase = ((now * 0.00035F) + s1) % 1.0F;
            float px = s1 * w + (float) Math.sin(now * 0.002F + i) * 14F;
            float py = h - fase * h * 0.55F;
            float rr = 3F + 9F * s2;
            disco(b, px, py, rr, 0x6B5B48, 0.16F * k * (1F - fase * 0.6F), 10);
        }
        // (c) vinheta de terra fechando os cantos
        b.vinheta(w, h, Math.min(w, h) * 0.34F, 0x120C05, 0.62F * k);
        // (d) tremor do impacto (faixas escuras rápidas nos 2 primeiros 25%)
        if (u < 0.25F) {
            float choque = 1F - u / 0.25F;
            for (int i = 0; i < 7; i++) {
                float yy = (i / 7F) * h + (float) Math.sin(now * 0.03F + i) * 8F;
                b.rect(0, yy, w, 3F + 6F * choque, col(0x000000, 0.20F * choque));
            }
        }
        b.flush();
        AvisoFxUtil.normal();

        // (e) o mundo desfoca (ele está atordoado no fundo do buraco)
        desfocar(2.5F + 2.5F * (1F - u) + 1.5F * (float) Math.sin(now * 0.004));
    }

    // ============ (5.7.2) ABERTURA DO TERREMOTO: o chão racha, sem flash ====
    // O dono VETOU a abertura antiga: "não quero essa animação que você fez
    // quando começou o terremoto". Ela era um FLASH BRANCO de 0,55 + blur 3,5
    // por 2,6 s (parecia foto estourando / tela lavada). Agora a abertura é
    // curta (1,4 s), escura, e mostra o CHÃO RACHANDO: uma fenda em zigue-zague
    // se abrindo do centro pra fora + pedrisco pulando da linha da rachadura.
    // Nada de branco, nada de blur acima de 1,2 (não dá enjoo).
    private static void desenharEstouro(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (estouroInicio == 0L || now > estouroFim) {
            return;
        }
        long dur = Math.max(1L, estouroFim - estouroInicio);
        float u = cl((now - estouroInicio) / (float) dur);
        int w = g.guiWidth();
        int h = g.guiHeight();
        float cx = w / 2f;
        float cy = h * 0.74f;                     // mira no CHÃO, não no meio da cara
        float esc = Math.min(w, h);

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        // (1) o tranco: 4 faixas de sombra piscam nas bordas (nunca branco)
        float tranco = (1F - ss(0.0F, 0.30F, u)) * 0.30F;
        if (tranco > 0.01F) {
            b.rect(0, 0, w, (int) (h * 0.16F), col(0x0A0505, tranco));
            b.rect(0, h - (int) (h * 0.20F), w, h, col(0x0A0505, tranco));
        }
        // (2) a FENDA na tela: zigue-zague que se ABRE do centro pra fora.
        //     cresce até 62% da tela em ~0,9 s e depois some
        float abre = eo(cl(u / 0.55F));
        float some = u < 0.62F ? 1F : 1F - (u - 0.62F) / 0.38F;
        float a = 0.75F * some;
        int pontos = 26;
        float semente = 0x5EED;
        float px = cx;
        float py = cy;
        // metade direita e metade esquerda (a rachadura é uma linha só)
        for (int lado = -1; lado <= 1; lado += 2) {
            px = cx;
            py = cy;
            for (int i = 1; i <= pontos; i++) {
                float t = i / (float) pontos;
                if (t > abre) {
                    break;
                }
                float nx = cx + lado * esc * 0.34F * t;
                float ny = cy + (rnd(i * 31 + 7, (int) semente) - 0.5F) * esc * 0.10F * t
                        - esc * 0.02F * t;
                // a linha: um retângulo fino girado é caro; uso 3 segmentos
                // curtos empilhados (dá o serrilhado da terra quebrando)
                b.seg(px, py, nx, ny, 1.6F + 3.2F * (1F - t),
                        col(0x140707, a * (1F - t * 0.35F)), col(0x140707, a * (1F - t * 0.35F)));
                // a "luz" do fundo da fenda (vermelho-sangue, bem discreta)
                b.seg(px, py, nx, ny, 0.7F + 1.2F * (1F - t),
                        col(0x6E1A12, a * 0.55F * (1F - t * 0.5F)),
                        col(0x6E1A12, a * 0.55F * (1F - t * 0.5F)));
                px = nx;
                py = ny;
            }
        }
        // (3) pedrisco saindo da rachadura (não é fumaça: é TERRA)
        for (int i = 0; i < 26; i++) {
            float s1 = rnd(i * 19 + 3, (int) semente);
            float s2 = rnd(i * 43 + 5, (int) semente);
            float t = (u * 1.6F + s2) % 1F;
            float x = cx + (s1 - 0.5F) * esc * 0.55F * abre;
            float y = cy - esc * 0.045F - esc * 0.16F * t * (0.4F + s1);
            float r = 1.5F + 3.5F * s2 * (1F - t);
            if (t < abre) {
                disco(b, x, y, r, 0x6B563C, 0.55F * (1F - t) * some, 6);
            }
        }
        b.flush();
        AvisoFxUtil.normal();
        // (4) tremor de tela MUITO curto e leve (antes era blur 3,5 por 1 s)
        if (u < 0.2F) {
            desfocar(1.2F * (1F - u / 0.2F));
        }
    }

    // ---- SAÍDA (5.7.2): a pessoa SOME nas sombras ------------------------
    // 5.7.0/5.7.1 tinha "luz verde / pixels verdes" e o dono REJEITOU ("essas
    // partículas verdes não ficaram legais"). Agora: começam POUCAS sombras em
    // volta do corpo e elas se multiplicam até engolir a pessoa. Sem flash, sem
    // lavada colorida e — regra da casa — SEM deixar a tela preta.
    private static void desenharSaida(GuiGraphics g) {
        if (saidaOff) {
            return;
        }
        long now = System.currentTimeMillis();
        if (saidaInicio == 0L || now > saidaFim) {
            return;
        }
        long dur = Math.max(1L, saidaFim - saidaInicio);
        float k = cl((now - saidaInicio) / (float) dur);          // 0..1
        int w = g.guiWidth();
        int h = g.guiHeight();
        float esc = Math.min(w, h);
        float cx = w / 2f;
        float cy = h * 0.60f;                                     // no corpo, não na cara
        long sem = saidaInicio;

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        // (1) o mundo escurece DE LEVE (no máximo 55%): assusta, não cega
        float vin = 0.55F * k * k;
        if (vin > 0.01F) {
            b.rect(0, 0, w, h, col(0x0A0710, vin));
        }
        // (2) as sombras: 3 no começo -> 26 no fim, nascendo em volta do corpo,
        //     girando devagar e crescendo. Borda macia (discos concêntricos),
        //     nada de quadrado.
        int quantas = 3 + (int) (23 * k);
        for (int i = 0; i < quantas; i++) {
            float s1 = rnd(i * 37 + 11, (int) (sem & 0xFFFFF));
            float s2 = rnd(i * 53 + 7, (int) ((sem >> 3) & 0xFFFFF));
            float nasc = i / (float) Math.max(1, quantas);
            float vida = cl((k - nasc) / Math.max(0.16F, 1F - nasc));
            if (vida <= 0.01F) {
                continue;
            }
            float ang = s1 * TAU + k * 1.7F * (s2 > 0.5F ? 1F : -1F);
            float dist = esc * (0.02F + 0.15F * (0.35F + s2) * (1F - vida * 0.5F));
            float px = cx + cos(ang) * dist;
            float py = cy + sin(ang) * dist * 0.55F - esc * 0.09F * vida;
            float raio = esc * (0.018F + 0.085F * vida) * (0.6F + s1 * 0.8F);
            disco(b, px, py, raio, 0x140C1E, 0.26F * vida, 14);
            disco(b, px, py, raio * 0.66F, 0x0B0612, 0.34F * vida, 12);
            disco(b, px, py, raio * 0.34F, 0x050309, 0.40F * vida, 10);
        }
        // (3) no último terço as sombras se JUNTAM no centro e apagam a
        //     silhueta — mas nunca 100% de preto (fica ~78% no pico).
        if (k > 0.6F) {
            float f = (k - 0.6F) / 0.4F;
            float raio = esc * (0.05F + 0.36F * eo(f));
            for (int i = 0; i < 7; i++) {
                float t2 = i / 7f;
                disco(b, cx, cy - esc * 0.05F * f, raio * (1F - t2 * 0.30F),
                        0x08040E, (0.10F + 0.09F * t2) * f, 22);
            }
        }
        b.flush();
        AvisoFxUtil.normal();
    }

    // ---- SUSTO: o bicho pula na tela (5.7.3 reforçado) ----------------
    // O dono: "o bicho da caixa nao está pulando na tela do player". Duas
    // causas: (a) o tick do servidor ignorava quem estava em CRIATIVO (é como
    // ele testa) — corrigido lá; (b) aqui a imagem aparecia pequena (escala
    // 0,35 -> 1,1) e sumia em 2,6 s. Agora ela PULA pra fora da tela: entra
    // grande, cresce até sair dos cantos, treme, e fica 3,4 s. Se a textura
    // falhar, desenha um vulto com olhos vermelhos (nunca fica sem susto).
    private static void desenharSusto(GuiGraphics g) {
        if (sustoOff || sustoInicio == 0L) {
            return;
        }
        long ms = System.currentTimeMillis() - sustoInicio;
        long durMs = 3400L;
        if (ms > durMs) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float k = ms / (float) durMs;

        // entra estourando (0,18 s), segura até 2,2 s e recua até o fim
        float entra = Math.min(1.0F, ms / 180.0F);
        float sai = ms > 2250 ? 1.0F - (ms - 2250) / (float) (durMs - 2250) : 1.0F;
        sai = cl(sai) * cl(sai);
        // escala: começa PEQUENA no centro e cresce até passar dos cantos
        float esc = (0.45F + 0.85F * eo(cl(ms / 900.0F))) * (0.85F + 0.25F * sustoForca);
        float tremor = (float) (Math.sin(ms * 0.11) * 12.0 + Math.sin(ms * 0.043) * 7.0) * entra;
        int lado = (int) (Math.min(w, h) * esc);
        int cx = w / 2 + (int) tremor;
        int cy = h / 2 + (int) (Math.cos(ms * 0.13) * 8.0);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // fundo vermelho de choque (pulsa)
        int alfaV = (int) ((120 + 60 * Math.sin(ms * 0.02)) * entra * sai);
        if (alfaV > 0) {
            g.fill(0, 0, w, h, (Math.min(255, alfaV) << 24) | 0x2A0000);
        }

        // ---- a cara do palhaço (textura), com aberração cromática
        try {
            float alfa = Math.min(1.0F, entra * 1.6F) * sai;
            int dx = (int) (4.0F * Math.sin(ms * 0.08)) * 2;
            // sombra/duplicata vermelha e ciano deslocadas = cara "vibrando"
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 0.25F, 0.25F, alfa * 0.55F);
            g.blit(BICHO, cx - lado / 2 + dx, cy - lado / 2, 0, 0, lado, lado, lado, lado);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.30F, 0.55F, 1.0F, alfa * 0.45F);
            g.blit(BICHO, cx - lado / 2 - dx, cy - lado / 2, 0, 0, lado, lado, lado, lado);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alfa);
            g.blit(BICHO, cx - lado / 2, cy - lado / 2, 0, 0, lado, lado, lado, lado);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        } catch (Throwable erro) {
            // fallback: vulto escuro com dois olhos vermelhos (sempre tem susto)
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
            int rr = (int) (lado * 0.46F);
            g.fill(cx - rr, cy - rr, cx + rr, cy + rr, 0xE0000000 | 0x0A0A0A);
            int olho = Math.max(6, rr / 4);
            g.fill(cx - rr / 2 - olho, cy - rr / 3, cx - rr / 2 + olho, cy - rr / 3 + olho, 0xFFFF2020);
            g.fill(cx + rr / 2 - olho, cy - rr / 3, cx + rr / 2 + olho, cy - rr / 3 + olho, 0xFFFF2020);
        }

        // flash branco curtíssimo no primeiro instante (só pra "estourar")
        if (ms < 140) {
            int alfa = (int) (170 * (1 - ms / 140.0F));
            g.fill(0, 0, w, h, (alfa << 24) | 0xFFFFFF);
        }
        // garras/vinheta vermelha pulsando nas bordas
        if (entra >= 1.0F) {
            int alfa = (int) (90 * (0.6F + 0.4F * (float) Math.sin(ms * 0.02)) * sai);
            g.fill(0, 0, w, 26, (alfa << 24) | 0x3A0000);
            g.fill(0, h - 26, w, h, (alfa << 24) | 0x3A0000);
            g.fill(0, 0, 18, h, (alfa << 24) | 0x3A0000);
            g.fill(w - 18, 0, w, h, (alfa << 24) | 0x3A0000);
        }
        if (k >= 0.999F) {
            sustoInicio = 0L;   // terminou: libera
        }
    }

    // ---- SOMBRA: o "apagar da existência" (5.5.0)
    // O dono reclamou do QUADRADO PRETO que aparecia no meio da tela quando
    // ele usava /vazio apagar ou /vazio exilar: era literalmente um
    // g.fill(w/2-r, h/2-r, w/2+r, h/2+r, preto) — um retângulo chapado.
    // Agora o efeito é uma SOMBRA de verdade: um borrão radial escuro (sem
    // canto nenhum, feito de triângulos em degradê), a silhueta de quem está
    // sendo apagado se desfazendo no meio e braços de sombra entrando pelas
    // bordas. Nada de retângulo.
    private static void desenharNebulosa(GuiGraphics g) {
        if (nebOff || nebInicio == 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > nebFim) {
            return;
        }
        long dur = Math.max(1L, nebFim - nebInicio);
        float k = cl((now - nebInicio) / (float) dur);
        float pico = sin((float) Math.PI * k);                 // 0 -> 1 -> 0
        int w = g.guiWidth();
        int h = g.guiHeight();
        float escala = Math.min(w, h);
        float cx = w / 2f;
        float cy = h * 0.52f;
        float forca = nebForte ? 1.0F : 0.72F;
        float semente = nebInicio * 0.001F;

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        // (a) lavagem leve da tela (não é caixa: cobre tudo, sem borda)
        b.rect(0, 0, w, h, col(0x060309, 0.26f * pico * forca));

        // (b) a sombra crescendo: 3 discos em degradê empilhados = borrão
        //     radial liso (48 fatias cada, nenhum canto aparece)
        float raio = escala * (0.12f + 0.42f * pico) * forca;
        disco(b, cx, cy + escala * 0.02f, raio * 1.55f, 0x0B0713, 0.55f * pico, 48);
        disco(b, cx, cy, raio * 1.05f, 0x06040B, 0.78f * pico, 48);
        disco(b, cx, cy, raio * 0.55f, 0x000000, 0.92f * pico, 48);

        // (c) a SILHUETA de quem está sendo apagado, se desfazendo de baixo
        //     pra cima (o corpo vira sombra e escorre)
        float alto = escala * 0.30f;
        float sobe = escala * 0.04f * sin(now * 0.004f);
        float cabeca = alto * 0.20f;
        float ombroY = cy - alto * 0.22f + sobe;
        float alfaSil = 0.55f * pico;
        // cabeça
        disco(b, cx, ombroY - cabeca * 0.9f, cabeca, 0x000000, alfaSil, 24);
        // tronco (trapézio) e braços
        b.quad(cx - escala * 0.055f, ombroY, col(0x000000, alfaSil),
                cx + escala * 0.055f, ombroY, col(0x000000, alfaSil),
                cx + escala * 0.075f, ombroY + alto * 0.62f, col(0x000000, alfaSil * 0.55f),
                cx - escala * 0.075f, ombroY + alto * 0.62f, col(0x000000, alfaSil * 0.55f));
        b.quad(cx - escala * 0.075f, ombroY + alto * 0.02f, col(0x000000, alfaSil * 0.9f),
                cx - escala * 0.050f, ombroY, col(0x000000, alfaSil * 0.9f),
                cx - escala * 0.105f, ombroY + alto * 0.45f, col(0x000000, 0.0f),
                cx - escala * 0.125f, ombroY + alto * 0.40f, col(0x000000, 0.0f));
        b.quad(cx + escala * 0.075f, ombroY + alto * 0.02f, col(0x000000, alfaSil * 0.9f),
                cx + escala * 0.050f, ombroY, col(0x000000, alfaSil * 0.9f),
                cx + escala * 0.105f, ombroY + alto * 0.45f, col(0x000000, 0.0f),
                cx + escala * 0.125f, ombroY + alto * 0.40f, col(0x000000, 0.0f));

        // (d) braços de sombra entrando pelas bordas (curvas, não retas)
        for (int i = 0; i < 16; i++) {
            float ang = i * TAU / 16f + semente;
            float comp = escala * (0.30f + 0.34f * pico) * (0.65f + 0.35f * rnd(i, 3));
            float x0 = cx + cos(ang) * escala * 0.85f;
            float y0 = cy + sin(ang) * escala * 0.85f;
            float segLen = comp / 7f;
            float cauda = 0.42f * pico;
            for (int j = 0; j < 7; j++) {
                float u0 = j / 7f;
                float u1 = (j + 1) / 7f;
                float desvio0 = sin(u0 * 6.0f + now * 0.004f + i) * escala * 0.05f;
                float desvio1 = sin(u1 * 6.0f + now * 0.004f + i) * escala * 0.05f;
                float px0 = x0 + (cx - x0) * u0 + cos(ang + 1.5708f) * desvio0;
                float py0 = y0 + (cy - y0) * u0 + sin(ang + 1.5708f) * desvio0;
                float px1 = x0 + (cx - x0) * u1 + cos(ang + 1.5708f) * desvio1;
                float py1 = y0 + (cy - y0) * u1 + sin(ang + 1.5708f) * desvio1;
                float al = cauda * (1f - u0 * 0.55f);
                b.seg(px0, py0, px1, py1, 3.2f + 1.6f * sin(now * 0.006f + i),
                        col(0x090610, al), col(0x090610, al * 0.75f));
            }
        }

        // (e) a "borda" da sombra: uma linha fina roxa, pra ler como sombra
        //     e não como buraco chapado
        b.anel(cx, cy, raio * 1.02f, 64, 1.6f, semente, 0x3A2A55, 0.35f * pico);

        // (f) vinheta fechando nas bordas (degradê, sem canto)
        b.vinheta(w, h, escala * 0.30f, 0x000000, 0.62f * pico * forca);

        b.flush();
        AvisoFxUtil.normal();
    }

    /** Disco em degradê radial: cor cheia no centro, transparente na borda. */
    private static void disco(AvisoFxUtil.Buf b, float cx, float cy, float r, int rgb, float a, int fatias) {
        for (int i = 0; i < fatias; i++) {
            float a0 = i * TAU / fatias;
            float a1 = (i + 1) * TAU / fatias;
            b.tri(cx, cy, col(rgb, a),
                    cx + cos(a0) * r, cy + sin(a0) * r, col(rgb, 0f),
                    cx + cos(a1) * r, cy + sin(a1) * r, col(rgb, 0f));
        }
    }

    // ---- EXÍLIO (5.5.0): quem está em rptag:exilio vê TUDO PRETO.
    // A dimensão já é escura por dados (sem céu, meia-noite fixa, luz 0), mas
    // o céu do End ainda desenha estrelas — aqui a tela fecha num preto macio
    // pra não sobrar nada. É só o que o dono pediu: "tudo preto e sem nada lá".
    private static void desenharExilio(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !DIM_EXILIO.equals(mc.level.dimension().location())) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = System.currentTimeMillis();
        float respira = 0.5F + 0.5F * (float) Math.sin(now * 0.0009);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, (int) ((0.70F + 0.03F * respira) * 255) << 24 | 0x000000);
        // vinheta macia (canto sem borda dura)
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0, 0, 0, 0.55F + 0.10F * respira);
        int lado = (int) (Math.max(w, h) * 1.35F);
        g.blit(NEBULOSA, w / 2 - lado / 2, h / 2 - lado / 2, 0, 0, lado, lado, lado, lado);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        // quase nada de texto, e bem apagado — só pra não parecer travado
        if ((now / 7000L) % 2 == 0) {
            String frase = "não há nada aqui";
            int larg = mc.font.width(frase);
            g.drawString(mc.font, frase, (w - larg) / 2, (int) (h * 0.84F), 0x22000000 | 0x1A0E24, false);
        }
    }

    // ---- AURA do deus: escurece as bordas de quem está perto dele?
    // (a aura em si é de partículas, no servidor; aqui só um leve escurecimento
    //  na tela do PRÓPRIO deus — os outros sentem pelo medo)
    private static void desenharAuraDeus(GuiGraphics g) {
        if (!souDeus) {
            return;
        }
        // (5.7.2) O DONO PEDIU: em PRIMEIRA pessoa (F1, o padrão) NÃO pode
        // aparecer fumaça/escurecimento na tela — "como vou acertar alguém sem
        // ver?". Em terceira pessoa (F5) aparece tudo. As rachaduras vermelhas
        // do chão são partículas do MUNDO (servidor), aparecem nos dois modos.
        boolean primeiraPessoa;
        try {
            primeiraPessoa = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        } catch (Throwable t) {
            primeiraPessoa = true;      // na dúvida, não atrapalha a mira
        }
        if (primeiraPessoa) {
            return;
        }
        // (5.7.1) a aura nova (protótipo VoidAuraDark) desenha tudo
        if (VazioAuraDarkFx.ligado()) {
            VazioAuraDarkFx.desenhar(g);
            return;
        }
        long now = System.currentTimeMillis();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float pulso = 0.5F + 0.5F * (float) Math.sin(now * 0.0011);
        int alfa = (int) (26 + 18 * pulso);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.35F, 0.15F, 0.6F, alfa / 255.0F);
        g.blit(NEBULOSA, 0, 0, 0, 0, w, h, w, w);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        // (trilhaOn existe só pra deixar claro pro compilador/leitor que o
        //  estado veio no pacote; o som roda no servidor)
        if (trilhaOn) {
            // nada a desenhar — a trilha é som
        }
    }
}
