package dev.rptag.client;

import dev.rptag.NeptunePackets;
import dev.rptag.RPTagMod;
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

    public static void onAura(NeptunePackets.Aura p) {
        souDeus = p.deus();
        trilhaOn = p.trilha();
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
            desenharSaida(g);
        } catch (Throwable t) {
            saidaOff = true;
            RPTagMod.LOGGER.error("RP Tag: camada da saida verde desligada", t);
        }
        try {
            desenharSusto(g);
        } catch (Throwable t) {
            sustoOff = true;
            RPTagMod.LOGGER.error("RP Tag: camada do susto desligada", t);
        }
        try {
            desenharNebulosa(g);
        } catch (Throwable t) {
            nebOff = true;
            RPTagMod.LOGGER.error("RP Tag: camada da nebulosa desligada", t);
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

        // (b) poeira subindo nas bordas (tira de terra)
        int borda = (int) (h * 0.22F);
        for (int i = 0; i < 26; i++) {
            float fase = ((now * 0.00035F + i * 0.137F) % 1.0F);
            int x = (int) ((i * 97 % w));
            int y = (int) (h - fase * borda);
            int alfa = (int) (60 * (1 - fase) * f);
            if (alfa > 2) {
                g.fill(x, y, x + 2 + (i % 3) * 2, y + 2, (alfa << 24) | 0x6B543C);
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
            // veio de calor (linhas claras finas atravessando)
            int alfa2 = (int) (22 * f);
            for (int i = 0; i < 5; i++) {
                int y = (int) (((now * 0.02 + i * 61) % h));
                g.fill(0, y, w, y + 1, (alfa2 << 24) | 0xFFFFFF);
            }
        }
    }

    // ---- SAÍDA VERDE: dissolve pra luz
    private static void desenharSaida(GuiGraphics g) {
        if (saidaOff) {
            return;
        }
        long now = System.currentTimeMillis();
        if (saidaInicio == 0L || now > saidaFim) {
            return;
        }
        long dur = Math.max(1L, saidaFim - saidaInicio);
        float k = Math.min(1.0F, (now - saidaInicio) / (float) dur);   // 0..1
        int w = g.guiWidth();
        int h = g.guiHeight();

        // brilho central cresce até tomar a tela
        int r = (int) (Math.max(w, h) * (0.25F + 0.95F * k));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.55F, 1.0F, 0.68F, 0.35F + 0.55F * k);
        g.blit(GLOW_VERDE, w / 2 - r, h / 2 - r, 0, 0, r * 2, r * 2, r * 2, r * 2);

        // vinheta verde fechando nas bordas
        int alfaV = (int) (200 * k);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.10F, 0.55F, 0.20F, alfaV / 255.0F);
        g.blit(GLOW_VERDE, -w / 3, -h / 3, 0, 0, w + w / 2, h + h / 2, w + w / 2, h + h / 2);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);

        // "pixels" verdes subindo e sumindo (a dissolução)
        long semente = saidaInicio;
        for (int i = 0; i < 60; i++) {
            float px = ((i * 733 + (int) (semente % 97)) % 1000) / 1000.0F;
            float fase = ((now * 0.0006F + i * 0.11F) % 1.0F);
            int x = (int) (px * w);
            int y = (int) (h - fase * h * 1.1F);
            int sz = 2 + (i % 4);
            int alfa = (int) (150 * (1 - fase) * (0.4F + k));
            if (alfa > 4) {
                g.fill(x, y, x + sz, y + sz, (Math.min(255, alfa) << 24) | 0x7CFF9A);
            }
        }
        // a última "lavada" verde: nos 35% finais, a tela toda vira verde
        if (k > 0.65F) {
            int alfa = (int) (255 * ((k - 0.65F) / 0.35F));
            g.fill(0, 0, w, h, (Math.min(255, alfa) << 24) | 0x2BFF77);
        }
    }

    // ---- SUSTO: o bicho pula na tela
    private static void desenharSusto(GuiGraphics g) {
        if (sustoOff || sustoInicio == 0L) {
            return;
        }
        long ms = System.currentTimeMillis() - sustoInicio;
        long durMs = 2600L;
        if (ms > durMs) {
            return;
        }
        float t = ms / 1000.0F;
        int w = g.guiWidth();
        int h = g.guiHeight();
        // entra estourando (0,22 s) e recua devagar, tremendo
        float entra = Math.min(1.0F, ms / 220.0F);
        float sai = ms > 1400 ? 1.0F - (ms - 1400) / (float) (durMs - 1400) : 1.0F;
        float esc = (0.35F + 0.75F * entra) * (0.75F + 0.35F * sustoForca);
        float tremor = (float) (Math.sin(ms * 0.09) * 7.0) * entra * (1 - sai * 0.6F);
        int lado = (int) (Math.min(w, h) * esc);
        int cx = w / 2 + (int) tremor;
        int cy = h / 2 + (int) (Math.cos(ms * 0.11) * 5);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // fundo vermelho de choque
        int alfaV = (int) (140 * entra * sai);
        g.fill(0, 0, w, h, (alfaV << 24) | 0x2A0000);
        // a cara
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, entra * 1.4F) * sai);
        g.blit(BICHO, cx - lado / 2, cy - lado / 2, 0, 0, lado, lado, lado, lado);
        // flash branco do primeiro instante
        if (ms < 260) {
            int alfa = (int) (200 * (1 - ms / 260.0F));
            g.fill(0, 0, w, h, (alfa << 24) | 0xFFFFFF);
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        // garras nas bordas (vinheta vermelha pulsando)
        if (entra >= 1.0F) {
            int alfa = (int) (90 * (0.6F + 0.4F * (float) Math.sin(ms * 0.02)) * sai);
            g.fill(0, 0, w, 22, (alfa << 24) | 0x3A0000);
            g.fill(0, h - 22, w, h, (alfa << 24) | 0x3A0000);
        }
    }

    // ---- NEBULOSA: o espaço se abrindo (vazio apagando alguém)
    private static void desenharNebulosa(GuiGraphics g) {
        if (nebOff || nebInicio == 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > nebFim) {
            return;
        }
        long dur = Math.max(1L, nebFim - nebInicio);
        float k = (now - nebInicio) / (float) dur;
        float pico = (float) Math.sin(Math.PI * Math.min(1.0F, Math.max(0.0F, k)));   // sobe e desce
        int w = g.guiWidth();
        int h = g.guiHeight();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.32F, 0.26F, 0.55F, 0.62F * pico);
        g.blit(NEBULOSA, 0, 0, 0, 0, w, h, w, h);
        // buraco negro pulsando no centro
        int r = (int) (Math.min(w, h) * (0.10F + 0.16F * pico * (nebForte ? 1.6F : 1.0F)));
        g.fill(w / 2 - r, h / 2 - r, w / 2 + r, h / 2 + r, ((int) (230 * pico) << 24) | 0x000000);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    // ---- AURA do deus: escurece as bordas de quem está perto dele?
    // (a aura em si é de partículas, no servidor; aqui só um leve escurecimento
    //  na tela do PRÓPRIO deus — os outros sentem pelo medo)
    private static void desenharAuraDeus(GuiGraphics g) {
        if (!souDeus) {
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
