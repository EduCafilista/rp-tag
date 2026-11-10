package dev.rptag.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.rptag.RPTagMod;
import dev.rptag.VazioPoderC2S;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * (5.31.0 / refeito na 5.32.0) <b>A RODA DE HABILIDADES DO VAZIO.</b>
 *
 * <p>O dono: <i>"melhore esse leque de habilidades, está feio e travado"</i> e mandou uma arte de
 * referência (uma roda escura com seis casas em volta de um miolo, marcas no aro e um "?" no
 * centro). A 5.31.0 desenhava seis retângulos num arco — feio e "travado" porque não tinha
 * animação nenhuma.
 *
 * <h2>Como é agora</h2>
 * <ul>
 *   <li>uma <b>roda de verdade</b> (textura própria {@code gui/leque_roda.png}), com aro, marcas,
 *       diamantes nos pontos cardeais e o miolo escuro;</li>
 *   <li>as seis habilidades têm <b>ícone desenhado</b> ({@code gui/leque_*.png}) e ficam em volta
 *       do miolo, com o número da tecla;</li>
 *   <li>a roda <b>abre com animação</b> (cresce e aparece em 0,15 s) e fecha do mesmo jeito —
 *       é o que tira o "travado";</li>
 *   <li>o <b>mouse escolhe</b>: a casa sob o cursor cresce e o miolo mostra o nome e a descrição
 *       dela; <b>clique</b> arremessa;</li>
 *   <li>as teclas <b>1 a 6</b> continuam valendo, e <b>X</b> repete a última;</li>
 *   <li>a casa em <b>recarga</b> fica escurecida com o tempo em cima.</li>
 * </ul>
 *
 * <p>O cliente só manda o número da habilidade: quem valida, cobra a recarga e aplica o dano é o
 * servidor ({@link dev.rptag.VazioPoderes}).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class PoderLeque {

    private PoderLeque() {
    }

    /** as seis habilidades, na ordem da roda (a 1 fica em cima). */
    public static final String[] NOMES = {"RAIO", "ORBE", "ONDA", "OLHOS", "PENAS", "BANIR"};
    public static final String[] DESC = {
        "a lança carmesim: atravessa a fileira e explode no fim",
        "orbe que estoura no primeiro que encostar",
        "a onda da morte: congela e corta tudo na frente",
        "um risco de pena para cada alvo na frente",
        "chuva de penas onde você olha",
        "o olho vermelho: bane o alvo para o outro mundo",
    };
    /** o piso de recarga de cada uma (ms) — o servidor é quem manda, isto é só o desenho. */
    public static final long[] RECARGA = {1200L, 600L, 6000L, 4000L, 3500L, 12000L};

    private static final ResourceLocation TEX_RODA =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/leque_roda.png");
    /** (5.33.0) o halo roxo da casa apontada (a arte do dono tem brilho). */
    private static final ResourceLocation TEX_BRILHO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/leque_brilho.png");
    /** (5.33.0) a tampa escura redonda da casa em recarga. */
    private static final ResourceLocation TEX_APAGADO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/leque_apagado.png");
    private static final ResourceLocation[] TEX_ICONE = new ResourceLocation[6];

    static {
        String[] nomes = {"raio", "orbe", "onda", "olhos", "penas", "banir"};
        for (int i = 0; i < nomes.length; i++) {
            TEX_ICONE[i] = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID,
                    "textures/gui/leque_" + nomes[i] + ".png");
        }
    }

    public static KeyMapping TECLA_LEQUE;
    public static KeyMapping TECLA_REPETIR;

    /** a roda está aberta? */
    private static boolean aberto;
    /** animação: 0 = fechada, 1 = aberta (interpola suave). */
    private static float animado;
    private static long ultimoTick = System.nanoTime();
    private static long abertoEm;
    private static int ultimo = -1;
    private static int apontado = -1;
    /** quando cada poder volta a estar pronto (ms) — só para o desenho. */
    private static final long[] PRONTO = new long[6];
    private static boolean mouseAntes;

    // ------------------------------------------------------------- teclas

    public static void registrarTeclas(RegisterKeyMappingsEvent e) {
        TECLA_LEQUE = new KeyMapping("key.rptag.leque", GLFW.GLFW_KEY_R, "key.categories.rptag");
        TECLA_REPETIR = new KeyMapping("key.rptag.repetir", GLFW.GLFW_KEY_X, "key.categories.rptag");
        e.register(TECLA_LEQUE);
        e.register(TECLA_REPETIR);
    }

    public static void registrarCamada(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "roda_poderes"),
                PoderLeque::render);
    }

    public static boolean estaAberto() {
        return aberto;
    }

    /** (5.33.0) fecha o leque e devolve o cursor para o jogo. */
    private static void fechar() {
        if (!aberto) {
            return;
        }
        aberto = false;
        soltarMouse(Minecraft.getInstance(), false);
    }

    // ------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            while (TECLA_LEQUE.consumeClick()) {
                if (mc.screen != null) {
                    continue;
                }
                aberto = !aberto;
                abertoEm = System.currentTimeMillis();
                apontado = -1;
                // (5.33.0) O CURSOR PRECISA FICAR LIVRE — era isso que deixava o leque
                // "travado": no Minecraft o mouse fica preso no centro da tela, então
                // não dava pra apontar a casa. Abrindo o leque a gente solta o cursor e,
                // ao fechar, prende de novo (senão a câmera não responde).
                soltarMouse(mc, aberto);
            }
            // a animação roda sempre (para fechar suave)
            long agora = System.nanoTime();
            float dt = Math.min(0.1F, (agora - ultimoTick) / 1_000_000_000.0F);
            ultimoTick = agora;
            float alvo = aberto ? 1.0F : 0.0F;
            animado = Mth.lerp(Math.min(1.0F, dt * 14.0F), animado, alvo);

            if (!aberto) {
                while (TECLA_REPETIR.consumeClick()) {
                    if (ultimo >= 0) {
                        mandar(ultimo);
                    }
                }
                return;
            }
            // fecha sozinho depois de 10 s parado
            if (System.currentTimeMillis() - abertoEm > 15000L) {
                fechar();
                return;
            }
            // teclas 1..6
            long janela = mc.getWindow().getWindow();
            for (int i = 0; i < NOMES.length; i++) {
                if (InputConstants.isKeyDown(janela, GLFW.GLFW_KEY_1 + i)) {
                    mandar(i);
                    fechar();
                    return;
                }
            }
            // mouse: aponta e clica
            Minecraft m = Minecraft.getInstance();
            int w = m.getWindow().getGuiScaledWidth();
            int h = m.getWindow().getGuiScaledHeight();
            double mx = m.mouseHandler.xpos() * w / (double) m.getWindow().getScreenWidth();
            double my = m.mouseHandler.ypos() * h / (double) m.getWindow().getScreenHeight();
            apontado = casaSobMouse(w, h, mx, my);
            boolean clicando = GLFW.glfwGetMouseButton(janela, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (clicando && !mouseAntes && apontado >= 0) {
                mandar(apontado);
                fechar();
            }
            // ESC fecha
            if (InputConstants.isKeyDown(janela, GLFW.GLFW_KEY_ESCAPE)) {
                fechar();
                return;
            }
            mouseAntes = clicando;
        } catch (Throwable ignorado) {
            // a roda é enfeite: nunca derruba o jogo
        }
    }

    /**
     * (5.33.0) solta/prende o cursor do jogo.
     *
     * <p>Sem isso o mouse fica travado no meio da tela e o jogador não consegue escolher a
     * habilidade — era exatamente a reclamação ("eu teria que poder mexer para escolher").
     */
    private static void soltarMouse(Minecraft mc, boolean soltar) {
        try {
            if (mc.screen != null) {
                return;
            }
            if (soltar) {
                mc.mouseHandler.releaseMouse();
            } else {
                mc.mouseHandler.grabMouse();
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: soltar o cursor do leque falhou", t);
        }
    }

    private static void mandar(int poder) {
        try {
            ultimo = poder;
            abertoEm = System.currentTimeMillis();
            PRONTO[poder] = System.currentTimeMillis() + RECARGA[poder];
            PacketDistributor.sendToServer(new VazioPoderC2S(poder));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: mandar o poder {} falhou", poder, t);
        }
    }

    // ------------------------------------------------------------- geometria

    /** lado da roda em pixels de GUI (cresce um pouco com a tela). */
    private static int ladoRoda(int w, int h) {
        return Math.max(230, Math.min(420, Math.min(w, h) - 40));
    }

    private static int centroY(int h) {
        return h / 2 + 6;
    }

    /** o deslocamento do centro da casa i, em pixels de GUI. */
    private static double[] casaPos(int i, int lado) {
        double ang = -Math.PI / 2 + i * (Math.PI * 2.0 / 6.0);
        double raio = lado * 0.63 * 0.5;      // 0.63 do raio da textura, metade do lado
        return new double[]{Math.cos(ang) * raio, Math.sin(ang) * raio};
    }

    private static int casaSobMouse(int w, int h, double mx, double my) {
        int lado = ladoRoda(w, h);
        double cx = w / 2.0, cy = centroY(h);
        double radioCasa = lado * 0.083;      // raio da casa na textura (0.083 do lado)
        for (int i = 0; i < 6; i++) {
            double[] p = casaPos(i, lado);
            double dx = mx - (cx + p[0]), dy = my - (cy + p[1]);
            if (dx * dx + dy * dy <= radioCasa * radioCasa * 1.35) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------- desenho

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        if (animado <= 0.02F) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight();
            int lado = ladoRoda(w, h);
            float cx = w / 2.0F, cy = centroY(h);
            float e = animado;                                  // 0..1
            float escala = 0.86F + 0.14F * ease(e);
            int alfa = (int) (255 * ease(e));
            float raio = lado / 2.0F;
            long agora = System.currentTimeMillis();

            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.scale(escala, escala, 1.0F);

            // ---- a roda (a arte do dono: aro roxo, seis casas, diamantes e estrelas)
            g.setColor(1.0F, 1.0F, 1.0F, alfa / 255.0F);
            g.blit(TEX_RODA, -lado / 2, -lado / 2, 0, 0, lado, lado, lado, lado);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            // ---- o brilho por trás da casa apontada (antes das casas, senão cobre o ícone)
            if (apontado >= 0) {
                double[] pb = casaPos(apontado, lado);
                int gs = (int) (raio * 0.66);
                g.setColor(0.78F, 0.46F, 1.0F, 0.85F * e);
                g.blit(TEX_BRILHO, (int) (pb[0] - gs / 2.0), (int) (pb[1] - gs / 2.0),
                        0, 0, gs, gs, gs, gs);
                g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            // ---- as seis casas
            int icone = (int) (lado * 0.105);
            int diametroCasa = (int) (lado * 0.170);
            for (int i = 0; i < 6; i++) {
                double[] p = casaPos(i, lado);
                boolean escolhida = (i == apontado) || (apontado < 0 && i == Math.max(0, ultimo));
                boolean recarregando = agora < PRONTO[i];
                float crescer = escolhida ? 1.18F : 1.0F;
                int tam = (int) (icone * crescer);
                int x = (int) (p[0] - tam / 2.0), y = (int) (p[1] - tam / 2.0);

                g.setColor(1, 1, 1, alfa / 255.0F);
                g.blit(TEX_ICONE[i], x, y, 0, 0, tam, tam, tam, tam);
                g.setColor(1, 1, 1, 1);

                if (recarregando) {
                    int d2 = (int) (diametroCasa * 0.96);
                    g.blit(TEX_APAGADO, (int) (p[0] - d2 / 2.0), (int) (p[1] - d2 / 2.0),
                            0, 0, d2, d2, d2, d2);
                    long falta = (PRONTO[i] - agora) / 1000L + 1;
                    g.drawCenteredString(mc.font, "\u00a7d\u00a7l" + falta + "s",
                            (int) p[0], (int) p[1] - 4, 0xFFFFFF);
                }

                // (5.33.0) o NÚMERO no aro, do jeito da arte do dono
                double ang = -Math.PI / 2 + i * (Math.PI / 3.0);
                int nx = (int) (Math.cos(ang) * raio * 0.845);
                int ny = (int) (Math.sin(ang) * raio * 0.845) - 4;
                boolean aceso = (i == apontado);
                g.drawCenteredString(mc.font, (aceso ? "\u00a7d\u00a7l" : "\u00a75") + (i + 1),
                        nx, ny, 0xFFFFFF);
            }

            // ---- o miolo: título, habilidade escolhida e a descrição
            int escolhido = apontado >= 0 ? apontado : Math.max(0, ultimo);
            float fonte = lado / 320.0F;                 // a fonte não muda: a roda sim
            float fr = raio / fonte;                     // raio em unidades de fonte
            pose.pushPose();
            pose.scale(fonte, fonte, 1.0F);
            g.drawCenteredString(mc.font, "\u00a75\u00a7lO VAZIO ETERNO",
                    0, (int) (-fr * 0.225F), 0xFFFFFF);
            if (apontado < 0) {
                g.drawCenteredString(mc.font, "\u00a78Selecione uma habilidade",
                        0, (int) (-fr * 0.045F), 0xFFFFFF);
                g.drawCenteredString(mc.font, "\u00a77\u00a7l" + NOMES[escolhido],
                        0, (int) (fr * 0.035F), 0xFFFFFF);
            } else {
                g.drawCenteredString(mc.font, "\u00a7d\u00a7l" + NOMES[escolhido],
                        0, (int) (-fr * 0.045F), 0xFFFFFF);
                String d = DESC[escolhido];
                String l1 = d, l2 = "";
                if (d.length() > 24) {
                    int corte = d.lastIndexOf(' ', 24);
                    if (corte < 8) {
                        corte = 24;
                    }
                    l1 = d.substring(0, corte);
                    l2 = d.substring(Math.min(corte + 1, d.length()));
                }
                g.drawCenteredString(mc.font, "\u00a77" + l1, 0, (int) (fr * 0.025F), 0xFFFFFF);
                if (!l2.isEmpty()) {
                    g.drawCenteredString(mc.font, "\u00a77" + l2, 0, (int) (fr * 0.100F), 0xFFFFFF);
                }
            }
            pose.popPose();

            pose.popPose();

            // ---- a tecla escrita embaixo da roda
            g.drawCenteredString(mc.font,
                    "\u00a78R fecha  \u00a78\u2022  \u00a781-6 arremessa  \u00a78\u2022  "
                    + "\u00a78clique escolhe  \u00a78\u2022  \u00a78X repete",
                    w / 2, (int) (cy + lado * 0.5F * escala + 8), 0xFFFFFF);
        } catch (Throwable t) {
            aberto = false;
        }
    }

    /** curva de entrada (rápido no começo, macio no fim). */
    private static float ease(float t) {
        float c = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - c) * (1.0F - c) * (1.0F - c);
    }

    /** usado pelo comando de teste (o leque com as teclas). */
    public static java.util.List<String> nomes() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String n : NOMES) {
            l.add(n);
        }
        return l;
    }
}
