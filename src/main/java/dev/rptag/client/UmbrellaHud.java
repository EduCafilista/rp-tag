package dev.rptag.client;

import dev.rptag.RPTagMod;
import dev.rptag.VarinhaSombraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * (5.37.0) <b>O UMBRELLA DE SOMBRA NO JOGO</b> — o HUD:
 *
 * <ul>
 *   <li>o sprite da cúpula + o estado — <b>ABERTO (protegido)</b> ou fechado com a dica do
 *       clique direito, e quantos segundos ainda restam de abertura.</li>
 * </ul>
 *
 * <p><b>(5.43.3) A CÚPULA SOBRE A CABEÇA FOI REMOVIDA.</b> O dono: <i>"TIRA A ANTIGA ANIMAÇÃO
 * DE GUARDA CHUVA, VOCE ENTENDEU?????"</i>. Até a 5.43.2 existiam DUAS coisas desenhando o
 * guarda-chuva: esta camada antiga (cubos 3D animados em cima do jogador, desde a 5.37.0) e o
 * modelo <b>.obj</b> na mão (desde a 5.42.0). Agora só existe o .obj — quem desenha é o
 * próprio item, com a pose de {@code arte/importar_umbrella_obj.py}.</p>
 *
 * <p><b>(5.37.2) NÃO use {@code @EventBusSubscriber} aqui:</b> esta classe não escuta evento
 * nenhum — quem chama é a camada de HUD registrada em {@code ClientGuiLayers}. Na 5.37.1 a
 * anotação ficou sobrando e o NeoForge <b>derrubou a construção do mod</b> inteiro
 * ("has no @SubscribeEvent methods, but register was called anyway").
 *
 * <p>O estado "aberto" é o MESMO que o servidor usa pra barrar o sol
 * ({@code VampiroSistema.guardaChuvaAberto}: item em uso). Cliente e servidor não têm como
 * discordar: quem manda é o pacote de uso do item do próprio Minecraft.
 */
public final class UmbrellaHud {

    /** quantas cores existem (as mesmas 8 do gerador de arte). */
    public static final int CORES = 8;

    private UmbrellaHud() {
    }

    /**
     * a cor do guarda-chuva (0..7). (5.41.0) delega pro item — antes esta classe relia o
     * componente por reflexão, o que era uma segunda fonte de verdade esperando pra divergir.
     */
    public static int corDo(ItemStack stack) {
        return VarinhaSombraItem.corDo(stack);
    }

    /**
     * (5.41.0) o guarda-chuva ABERTO que o jogador tem na mão, ou {@code null}.
     *
     * <p>Antes isto perguntava {@code p.isUsingItem()} — o estado tinha de ser "usando" no
     * cliente. Agora o estado mora no próprio item ({@code custom_model_data} >= 8), então o
     * HUD, o modelo 3D e a cúpula falam todos a mesma língua, sem depender de animação.
     */
    public static ItemStack aberto(Player p) {
        if (p == null) {
            return null;
        }
        for (InteractionHand mao : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(mao);
            if (VarinhaSombraItem.aberto(s)) {
                return s;
            }
        }
        return null;
    }

    /** algum umbrella na mão (aberto ou fechado)? */
    public static ItemStack naMao(Player p) {
        if (p == null) {
            return null;
        }
        for (InteractionHand mao : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(mao);
            if (s.getItem() instanceof VarinhaSombraItem) {
                return s;
            }
        }
        return null;
    }

    public static ResourceLocation spriteHud(int cor) {
        return ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID,
                "textures/gui/umbrella_hud_" + Math.floorMod(cor, CORES) + ".png");
    }

    /** o desenho do HUD (chamado pela camada registrada em {@code ClientGuiLayers}). */
    public static void desenhar(GuiGraphics g) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.options.hideGui || mc.screen != null) {
                return;
            }
            ItemStack aberto = aberto(mc.player);
            ItemStack mao = naMao(mc.player);
            if (aberto == null && mao == null) {
                return;
            }
            int cor = corDo(aberto != null ? aberto : mao);
            int w = g.guiWidth(), h = g.guiHeight();
            boolean estaAberto = aberto != null;
            int tam = 32;
            int x = w - tam - 12;
            int y = h - 78;
            if (!estaAberto) {
                g.setColor(1.0F, 1.0F, 1.0F, 0.45F);   // fechado = apagado
            }
            g.blit(spriteHud(cor), x, y, 0, 0, tam, tam, tam, tam);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            String titulo = estaAberto ? "\u00a7f\u00a7lABERTO" : "\u00a78fechado";
            int larguraTexto = mc.font.width(titulo);
            g.drawString(mc.font, titulo, x - larguraTexto - 6, y + 4, 0xFFFFFF, true);
            String sub = estaAberto
                    ? "\u00a77o sol n\u00e3o passa"
                    : "\u00a77clique direito";
            int larguraSub = mc.font.width(sub);
            g.drawString(mc.font, sub, x - larguraSub - 6, y + 16, 0xFFFFFF, false);
            if (estaAberto && mc.player != null) {
                int seg = VarinhaSombraItem.segundosRestantes(aberto, mc.player.level().getGameTime());
                String resta = "\u00a77\u00b7 \u00a7f" + seg + "s";
                g.drawString(mc.font, resta, x - mc.font.width(resta) - 6, y + 26, 0xFFFFFF, false);
            }
        } catch (Throwable ignorado) {
            // HUD é enfeite: nunca derruba o resto
        }
    }
}
