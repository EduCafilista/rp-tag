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
    // (5.7.6) EXÍLIO: a escuridão que engole (indo) e que se abre (voltando)
    private static volatile long exilioInicio = 0L, exilioFim = 0L;
    private static volatile long voltaInicio = 0L, voltaFim = 0L;

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
        // (5.7.9) BUG DO DONO: o /terremoto parar calava o tremor mas o SOM
        // seguia tocando. O servidor agora manda o StopSound; aqui é a segunda
        // rede de proteção — quando chega o pacote de "desligado", o cliente
        // para na mão qualquer instância dos sons do terremoto que ainda esteja
        // viva (o áudio do colapso tem 75 s e continuava rolando sozinho).
        if (!p.ativo()) {
            try {
                var sm = Minecraft.getInstance().getSoundManager();
                sm.stop(RPTagMod.SOM_TERREMOTO_COLAPSO.get().getLocation(), null);
                sm.stop(RPTagMod.SOM_RUMBLE.get().getLocation(), null);
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: nao consegui calar o som do terremoto no cliente", t);
            }
        }
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
            case NeptunePackets.Cena.CENA_EXILIO -> {
                exilioInicio = agora;
                exilioFim = agora + ms;
                voltaInicio = 0L;
            }
            case NeptunePackets.Cena.CENA_VOLTA -> {
                voltaInicio = agora;
                voltaFim = agora + ms;
                exilioInicio = 0L;
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
        boolean eu = p.euSou();
        souDeus = eu;
        trilhaOn = p.trilha();
        // (5.11.1) RAIOS VERMELHOS em geometria 3D em volta de QUEM É O VAZIO.
        // Voltaram os raios (pedido do dono) — mas em blend NORMAL
        // (RenderType.debugQuads), não no aditivo que sumia com shader.
        RaiosVazioFx.definir(p.alvo(), eu);
        // o acabamento de TELA é só pro próprio vazio (é ele que está dentro)
        if (eu) {
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
            desenharExilioCena(g);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: cena do exilio desligada", t);
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
        // (5.7.7) "balance um pouquinho mais a tela": a nebulosa estava
        // pesada demais (tingia tudo de roxo). Caiu para ~metade.
        float a1 = 0.05F + 0.03F * (float) Math.sin(now * 0.0013);
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
            // (5.7.7) o desfoque do pulso era 1,6–4,4 (cegava no pico).
            // Agora é 0,8–2,0: dá o "estufado" sem embaçar o mundo.
            float pulso = 0.5F + 0.5F * (float) Math.sin(now * 0.006);
            desfocar(0.8F + 1.2F * pulso * f);
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
        // (5.7.7) detritos: 26 -> 18 lascas e um pouco mais apagadas, pra tela
        // não ficar parecendo chuva de lixo.
        for (int i = 0; i < 18; i++) {
            float s1 = rnd(i * 11 + 3, 7);
            float s2 = rnd(i * 5 + 9, 3);
            float vel = 0.22F + s2 * 0.55F;
            float fase = ((now * 0.00035F * vel) + s1) % 1.0F;
            int x = (int) (s1 * w) + (int) (Math.sin(now * 0.002F + i) * 10);
            int y = (int) (fase * h * 1.2F) - (int) (h * 0.1F);
            int tamy = 3 + (i % 3) * 2;
            int alfa = (int) (78 * f * (1F - Math.abs(fase - 0.5F) * 1.4F));
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
    // (5.7.5) A RACHADURA DE TELA SAIU.
    // Era um "vidro rachado" desenhado por cima do jogo (o abismo preto no rodapé
    // + 11 rachaduras serrilhadas). O dono apontou isso como o que mais incomoda:
    // "as rachaduras desenhadas na tela". Agora quem racha é o MUNDO: os blocos
    // quebram de verdade (fendaDePassagem no servidor) e a poeira é do próprio
    // bloco. Sobrou só uma pitada de "peso" no rodapé — nada de rabisco na lente.
    private static void desenharRachadura(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (rachInicio == 0L || now > rachFim) {
            return;
        }
        long dur = Math.max(1L, rachFim - rachInicio);
        float u = cl((now - rachInicio) / (float) dur);
        // sobe rápido, morre devagar
        float forca = ss(0.0F, 0.18F, u) * (1.0F - ss(0.55F, 1.0F, u));
        if (forca <= 0.01F) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float faixa = h * 0.10F;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // poeira subindo do chão: degradê escuro só nos 10% de baixo
        int a = (int) (52 * forca);
        g.fillGradient(0, h - (int) faixa, w, h, 0x00000000, (a << 24) | 0x140C08);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
    }

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
    // ============ (5.7.5) ABERTURA DO TERREMOTO — agora é o MUNDO ==========
    // O dono apontou a rachadura desenhada na TELA como o que mais incomoda. A
    // abertura antiga desenhava um zigue-zague na lente; saiu. Quem faz o
    // espetáculo agora é o servidor, no mundo: no instante do START ele abre
    // fendas de VERDADE embaixo dos jogadores, joga pedra e poeira pra cima e
    // toca o estalo da terra. Aqui sobrou só o "peso" da câmera: as bordas
    // escurecem por 0,9 s e o desfoque é curtinho (1,0 no pico, e só nos
    // primeiros 0,18 s).
    private static void desenharEstouro(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (estouroInicio == 0L || now > estouroFim) {
            return;
        }
        long dur = Math.max(1L, estouroFim - estouroInicio);
        float u = cl((now - estouroInicio) / (float) dur);
        int w = g.guiWidth();
        int h = g.guiHeight();

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());
        // um "soco" escuro que entra e sai (nunca branco, nunca tela preta)
        float peso = (1F - ss(0.0F, 0.45F, u)) * 0.34F;
        if (peso > 0.01F) {
            int faixa = (int) (h * (0.13F + 0.05F * u));
            b.rect(0, 0, w, faixa, col(0x0A0505, peso));
            b.rect(0, h - faixa, w, h, col(0x0A0505, peso));
        }
        b.flush();
        AvisoFxUtil.normal();
        if (u < 0.18F) {
            desfocar(1.0F * (1F - u / 0.18F));
        }
    }

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

        // ---- (5.7.9) BUG DO DONO: "não dá nem para ver o rosto mais do
        // bicho". A causa era a ORDEM: o vulto escuro (a MESMA textura pintada
        // de preto, 6% maior, com 85% de alfa) era desenhado DEPOIS da cara e
        // comia o rosto inteiro — sobrava só a silhueta. Agora o vulto vem
        // PRIMEIRO (ele é o corpo atrás), e a cara é a ÚLTIMA coisa a ser
        // pintada, opaca, maior e com os olhos acesos por cima.
        try {
            float avanca = 1.0F + 0.35F * (1.0F - cl(ms / 600.0F));
            int lado2 = (int) (lado * avanca);
            float resp = 0.5F + 0.5F * (float) Math.sin(ms * 0.006);

            // (1) o VULTO — corpo escuro atrás, respirando
            int lado3 = (int) (lado2 * (1.10F + 0.08F * resp));
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.05F, 0.0F, 0.05F, 0.85F * entra * sai);
            g.blit(BICHO, cx - lado3 / 2, cy - lado3 / 2 + (int) (12 * resp),
                    0, 0, lado3, lado3, lado3, lado3);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);

            // (2) a CARA — opaca, por cima de tudo, com aberração cromática
            float alfa = Math.min(1.0F, entra * 1.6F) * sai;
            int dx = (int) (4.0F * Math.sin(ms * 0.08)) * 2;
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 0.28F, 0.28F, alfa * 0.60F);
            g.blit(BICHO, cx - lado2 / 2 + dx, cy - lado2 / 2, 0, 0, lado2, lado2, lado2, lado2);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.30F, 0.55F, 1.0F, alfa * 0.50F);
            g.blit(BICHO, cx - lado2 / 2 - dx, cy - lado2 / 2, 0, 0, lado2, lado2, lado2, lado2);
            // opaca e um tico mais CLARA que o original: o rosto tem que ler
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.18F, 1.12F, 1.10F, alfa);
            g.blit(BICHO, cx - lado2 / 2, cy - lado2 / 2, 0, 0, lado2, lado2, lado2, lado2);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);

            // (3) os OLHOS acesos por cima da cara (dois borrões vermelhos no
            // lugar dos olhos da textura — o rosto fica inconfundível)
            float olhoA = 0.55F * entra * sai;
            if (olhoA > 0.02F) {
                int olhoDx = (int) (lado2 * 0.155F);
                int olhoY = cy - (int) (lado2 * 0.075F);
                int olhoR = Math.max(3, (int) (lado2 * 0.032F));
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                        com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                        com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                int a8 = (int) (olhoA * 255);
                for (int i = 3; i >= 1; i--) {
                    int r = olhoR * i / 2 + 2;
                    int aa = a8 / (i * 2);
                    g.fill(cx - olhoDx - r, olhoY - r, cx - olhoDx + r, olhoY + r, (aa << 24) | 0xFF2A10);
                    g.fill(cx + olhoDx - r, olhoY - r, cx + olhoDx + r, olhoY + r, (aa << 24) | 0xFF2A10);
                }
                g.fill(cx - olhoDx - olhoR / 2, olhoY - olhoR / 2, cx - olhoDx + olhoR / 2, olhoY + olhoR / 2, (a8 << 24) | 0xFFE8D0);
                g.fill(cx + olhoDx - olhoR / 2, olhoY - olhoR / 2, cx + olhoDx + olhoR / 2, olhoY + olhoR / 2, (a8 << 24) | 0xFFE8D0);
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            }

            // (4) vinheta preta em volta (foco no rosto, sem tapar o rosto)
            float aura = 0.55F * entra * sai;
            for (int i = 0; i < 16; i++) {
                float t2 = i / 16f;
                int alfaB = (int) (aura * 255 * (1 - t2) * 0.5f);
                int m = (int) (Math.min(w, h) * 0.5F * t2);
                if (alfaB <= 0) {
                    continue;
                }
                int cor = (Math.min(255, alfaB) << 24);
                g.fill(0, 0, w, m, cor);
                g.fill(0, h - m, w, h, cor);
                g.fill(0, 0, m, h, cor);
                g.fill(w - m, 0, w, h, cor);
            }
        } catch (Throwable erro) {
            // fallback: vulto escuro com dois olhos vermelhos (sempre tem susto)
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
            int rr = (int) (lado * 0.46F);
            g.fill(cx - rr, cy - rr, cx + rr, cy + rr, 0xE0000000 | 0x0A0A0A);
            int olho = Math.max(6, rr / 4);
            g.fill(cx - rr / 2 - olho, cy - rr / 3, cx - rr / 2 + olho, cy - rr / 3 + olho, 0xFFFF2020);
            g.fill(cx + rr / 2 - olho, cy - rr / 3, cx + rr / 2 + olho, cy - rr / 3 + olho, 0xFFFF2020);
            RPTagMod.LOGGER.warn("RP Tag: desenho da cara do bicho falhou (vulto no lugar)", erro);
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
    // ---- EXÍLIO (5.7.6): a escuridão que ENGole e a que DEVOLVE ------------
    /**
     * (5.7.6) ANIMAÇÃO DE TELA DO EXÍLIO.
     *
     * <p>O corpo do que acontece está NO MUNDO (partículas, ver
     * {@code NeptuneEventos.tickExilios}) — aqui é só o peso na lente de quem
     * está sendo levado: sombras subindo pelas bordas e fechando em volta,
     * sem nunca virar preto chapado (regra do dono: "tela preta longa
     * proibida").
     *
     * <p>A VOLTA é o contrário: nasce tudo escuro e a escuridão se abre do
     * centro pra fora, com um pouco de brasa caindo — "a escuridão te devolve".
     */
    private static void desenharExilioCena(GuiGraphics g) {
        long now = System.currentTimeMillis();
        boolean indo = exilioInicio != 0L && now <= exilioFim;
        boolean vindo = voltaInicio != 0L && now <= voltaFim;
        if (!indo && !vindo) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float esc = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;

        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        if (indo) {
            float u = cl((now - exilioInicio) / (float) Math.max(1L, exilioFim - exilioInicio));
            // (1) vinheta pesada que avança pra dentro (máx. 0.72 — não cega)
            float peso = 0.72F * u * u;
            b.vinheta(w, h, esc * (0.46F - 0.30F * u), 0x040207, peso);
            // (2) sombras puxando do corpo pra dentro: discos que nascem na
            //     borda e correm pro centro (a sucção do exílio)
            for (int i = 0; i < 22; i++) {
                float s1 = rnd(i * 23 + 5, (int) (exilioInicio & 0xFFFFF));
                float s2 = rnd(i * 41 + 9, (int) ((exilioInicio >> 3) & 0xFFFFF));
                float vida = cl((u - i / 22f * 0.55F) / 0.45F);
                if (vida <= 0.02F) {
                    continue;
                }
                float ang = s1 * TAU + u * 2.4F;
                float dist = esc * (0.52F - 0.40F * vida);
                float px = cx + cos(ang) * dist;
                float py = cy + sin(ang) * dist * 0.62F;
                float raio = esc * (0.03F + 0.10F * vida) * (0.6F + s2 * 0.7F);
                disco(b, px, py, raio, 0x0A0512, 0.30F * vida, 14);
                disco(b, px, py, raio * 0.55F, 0x040208, 0.40F * vida, 12);
            }
            // (3) brasa vermelha estalando no meio (o vazio cuspindo)
            for (int i = 0; i < 10; i++) {
                float s1 = rnd(i * 17 + 3, (int) ((exilioInicio >> 5) & 0xFFFFF));
                float s2 = rnd(i * 29 + 1, 11);
                float f = ((now * 0.0006F + s1) % 1f);
                float ang = s2 * TAU;
                float r = esc * 0.05F * f;
                disco(b, cx + cos(ang) * r * 3f, cy + sin(ang) * r * 2f,
                        3F + 5F * (1F - f), 0xE6212B, 0.35F * (1F - f) * u, 8);
            }
        }

        if (vindo) {
            float u = cl((now - voltaInicio) / (float) Math.max(1L, voltaFim - voltaInicio));
            // começa fundo e ABRE: a escuridão se afasta do centro
            float abre = 1.0F - u;
            b.vinheta(w, h, esc * (0.10F + 0.42F * abre), 0x040207, 0.68F * abre * abre);
            if (abre > 0.02F) {
                for (int i = 0; i < 16; i++) {
                    float s1 = rnd(i * 31 + 7, (int) (voltaInicio & 0xFFFFF));
                    float s2 = rnd(i * 13 + 5, 23);
                    float ang = s1 * TAU + u * 1.4F;
                    float dist = esc * (0.06F + 0.50F * u) * (0.5F + s2);
                    float px = cx + cos(ang) * dist;
                    float py = cy + sin(ang) * dist * 0.6F;
                    disco(b, px, py, esc * 0.16F * abre * (0.5F + s1), 0x0B0514,
                            0.26F * abre, 16);
                }
            }
            // brasa/cinza caindo (o que veio junto com ele)
            for (int i = 0; i < 14; i++) {
                float s1 = rnd(i * 7 + 3, (int) (voltaInicio & 0xFFFFF));
                float s2 = rnd(i * 19 + 5, 31);
                float f = ((now * 0.0007F + s1) % 1f);
                float px = s2 * w;
                float py = h * (0.35F + f * 0.6F);
                disco(b, px, py, 2F + 4F * s1, 0xC2401A, 0.30F * (1F - f) * (1F - u), 6);
            }
        }

        b.flush();
        AvisoFxUtil.normal();
    }

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
