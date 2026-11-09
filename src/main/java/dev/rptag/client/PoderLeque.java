package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import dev.rptag.RPTagMod;
import dev.rptag.VazioPoderC2S;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * (5.31.0) <b>O LEQUE DE HABILIDADES (a tecla R) — igual ao mod de referência.</b>
 *
 * <p>O dono: <i>"eu não quero que os poderes sejam por comandos, quero que seja igual a do mod
 * que eu te mandei, uma keybind que mostra as habilidades e eu posso arremessar ataque"</i>.
 *
 * <h2>Como funciona</h2>
 * <ul>
 *   <li><b>R</b> abre/fecha o leque (dá pra trocar a tecla nas Opções → Controles → RP Tag);</li>
 *   <li>com o leque aberto, <b>1 a 6</b> arremessa a habilidade — e o leque fecha;</li>
 *   <li><b>clique do mouse</b> também arremessa a habilidade no centro do leque;</li>
 *   <li><b>X</b> arremessa na hora a última habilidade usada (sem abrir nada).</li>
 * </ul>
 *
 * <p>O cliente só manda o número: quem valida, cobra a recarga e aplica o dano é o servidor
 * ({@link dev.rptag.VazioPoderes}).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class PoderLeque {

    private PoderLeque() {
    }

    /** as seis habilidades, na ordem do leque. */
    public static final String[] NOMES = {"RAIO", "ORBE", "ONDA", "OLHOS", "PENAS", "BANIR"};
    /** o que cada uma faz, em uma linha (o leque mostra embaixo). */
    public static final String[] DESC = {
        "a lança carmesim que atravessa e explode",
        "orbe que estoura no primeiro que encostar",
        "a onda da morte: congela e corta tudo na frente",
        "um risco de pena para cada alvo na frente",
        "chuva de penas onde você olha",
        "o OLHO VERMELHO: bane o alvo para o outro mundo",
    };
    /** a tecla que abre o leque (R) e a que repete o último poder (X). */
    public static KeyMapping TECLA_LEQUE;
    public static KeyMapping TECLA_REPETIR;

    private static boolean aberto;
    private static long abertoEm;
    private static int ultimo = -1;
    private static long ultimoEm;

    // ------------------------------------------------------------- teclas

    /** (mod bus) registra as teclas nas Opções → Controles. */
    public static void registrarTeclas(RegisterKeyMappingsEvent e) {
        TECLA_LEQUE = new KeyMapping("key.rptag.leque", GLFW.GLFW_KEY_R, "key.categories.rptag");
        TECLA_REPETIR = new KeyMapping("key.rptag.repetir", GLFW.GLFW_KEY_X, "key.categories.rptag");
        e.register(TECLA_LEQUE);
        e.register(TECLA_REPETIR);
    }

    /** (mod bus) o leque é uma camada de HUD (aparece com shaders, igual às outras). */
    public static void registrarCamada(RegisterGuiLayersEvent e) {
        e.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                RPTagMod.MODID, "leque_poderes"), PoderLeque::render);
    }

    public static boolean estaAberto() {
        return aberto;
    }

    // ------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                return;
            }
            while (TECLA_LEQUE.consumeClick()) {
                aberto = !aberto;
                abertoEm = System.currentTimeMillis();
            }
            if (!aberto) {
                while (TECLA_REPETIR.consumeClick()) {
                    if (ultimo >= 0) {
                        mandar(ultimo);
                    }
                }
                return;
            }
            // com o leque aberto: 1..6 arremessa
            long janela = mc.getWindow().getWindow();
            for (int i = 0; i < NOMES.length; i++) {
                if (InputConstants.isKeyDown(janela, GLFW.GLFW_KEY_1 + i)) {
                    mandar(i);
                    aberto = false;
                    return;
                }
            }
            // clique esquerdo = a habilidade do centro
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(janela, GLFW.GLFW_KEY_ENTER)) {
                mandar(0);
                aberto = false;
            }
            // fecha sozinho depois de 8 s parado
            if (System.currentTimeMillis() - abertoEm > 8000L) {
                aberto = false;
            }
        } catch (Throwable ignorado) {
            // tecla é enfeite: nunca derruba o jogo
        }
    }

    private static void mandar(int poder) {
        try {
            ultimo = poder;
            ultimoEm = System.currentTimeMillis();
            PacketDistributor.sendToServer(new VazioPoderC2S(poder));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: mandar o poder {} falhou", poder, t);
        }
    }

    // ------------------------------------------------------------- desenho

    /** o leque: seis casas em arco, a tecla de cada uma, e a descrição da apontada. */
    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        if (!aberto) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            int w = g.guiWidth(), h = g.guiHeight();
            int cx = w / 2, cy = h / 2 + 40;
            int raio = 84;

            // véu escuro atrás (o leque tem que ler contra qualquer cenário)
            g.fill(cx - 210, cy - 130, cx + 210, cy + 70, 0xC0080406);

            for (int i = 0; i < NOMES.length; i++) {
                double ang = Math.PI + (i + 0.5) * (Math.PI / NOMES.length);   // arco de 180°
                int x = cx + (int) (Math.cos(ang) * raio) - 52;
                int y = cy + (int) (Math.sin(ang) * 46) - 12;
                boolean usado = i == ultimo;
                int fundo = usado ? 0xE0A01018 : 0xC0140A10;
                g.fill(x, y, x + 104, y + 26, fundo);
                g.fill(x, y, x + 104, y + 2, usado ? 0xFFFF4050 : 0xFF3A0A12);
                g.drawString(mc.font, "§f" + (i + 1) + " §l" + NOMES[i], x + 5, y + 4, 0xFFFFFF, false);
                g.drawString(mc.font, "§8[" + (i + 1) + "]", x + 84, y + 4, 0xFFFFFF, false);
            }
            g.drawString(mc.font, "§c§lPODERES DO VAZIO", cx - 60, cy - 122, 0xFFFFFF, false);
            g.drawString(mc.font, "§7aperte §f1-" + NOMES.length
                    + "§7 para arremessar  §8•  §7R fecha", cx - 96, cy + 44, 0xFFFFFF, false);
            int apontado = ultimo >= 0 ? ultimo : 0;
            g.drawString(mc.font, "§8" + DESC[apontado], cx - mc.font.width(DESC[apontado]) / 2,
                    cy + 58, 0xFFFFFF, false);
        } catch (Throwable ignorado) {
            aberto = false;
        }
    }

    /** usado pelo aviso de erro do servidor (mantém o leque com o último usado). */
    public static List<String> nomes() {
        List<String> l = new ArrayList<>();
        for (String n : NOMES) {
            l.add(n);
        }
        return l;
    }
}
