package dev.rptag.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * (5.48.0) <b>A RODA DE PODERES COMO TELA DE VERDADE.</b>
 *
 * <h2>O defeito</h2>
 * O dono, três rodadas seguidas: <i>"a roda de habilidades ainda está bugada, corrija: após
 * selecionar o poder vai fechar ela, e logo após isso vai poder usar o poder escolhido"</i>.
 *
 * <p>Até a 5.47.0 a roda era só um <b>desenho por cima do HUD</b> que, para o mouse funcionar,
 * soltava e prendia o cursor na mão ({@code mouseHandler.releaseMouse()/grabMouse()}) e lia a
 * posição do mouse por conta própria. Isso dá exatamente os defeitos que ele descreveu:
 * o cursor solto não é do jogo (o clique pode cair no vazio, a câmera dá um giro quando o
 * cursor volta) e o botão direito ficava travado esperando uma borda de clique que nunca vinha.
 *
 * <h2>A correção</h2>
 * Agora a roda é um {@link Screen} — o mesmo mecanismo que o inventário usa:
 * <ul>
 *   <li>o jogo entrega o cursor, a posição e o clique (<b>sem</b> releaseMouse na mão);</li>
 *   <li><b>clicar numa casa</b> arma o poder e <b>fecha na hora</b> ({@link #mouseClicked});</li>
 *   <li>ao fechar, o Minecraft prende o mouse de volta sozinho — então o próximo clique
 *       direito já é do jogo e <b>usa o poder escolhido</b>;</li>
 *   <li>as teclas 1-6 escolhem, R e ESC fecham;</li>
 *   <li>{@link #isPauseScreen()} é falso: o mundo continua rodando (não é menu de pausa).</li>
 * </ul>
 *
 * <p>O desenho é o MESMO de antes (quem desenha é {@link PoderLeque#desenharRoda}), então a
 * arte não mudou — mudou só quem manda no mouse.
 */
public final class RodaPoderes extends Screen {

    public RodaPoderes() {
        super(Component.literal("Poderes do Vazio"));
    }

    /** não pausa o jogo: a roda é HUD, não menu. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** sem véu escuro: o mundo atrás continua visível (era assim no desenho antigo). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float parcial) {
        // de propósito: nada
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float parcial) {
        try {
            PoderLeque.desenharRoda(g, mouseX, mouseY);
        } catch (Throwable t) {
            // desenho é enfeite: nunca derruba o jogo
            this.onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (botao == 0) {
            int casa = PoderLeque.casaEm(mx, my, this.width, this.height);
            if (casa >= 0) {
                PoderLeque.escolher(casa);       // arma E fecha
                return true;
            }
        }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public boolean keyPressed(int tecla, int scan, int mods) {
        if (tecla >= GLFW.GLFW_KEY_1 && tecla <= GLFW.GLFW_KEY_6) {
            PoderLeque.escolher(tecla - GLFW.GLFW_KEY_1);
            return true;
        }
        if (tecla == GLFW.GLFW_KEY_R) {
            this.onClose();
            return true;
        }
        return super.keyPressed(tecla, scan, mods);
    }

    /** fechar = voltar pro jogo (o Minecraft prende o cursor sozinho aqui). */
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}
