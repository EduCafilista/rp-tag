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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * (5.37.0) <b>O UMBRELLA DE SOMBRA NO JOGO</b> — duas coisas:
 *
 * <ol>
 *   <li>no HUD (este arquivo): o sprite da cúpula + o estado — <b>ABERTO (protegido)</b> ou
 *       fechado com a dica do botão direito, e quantos segundos ainda restam de abertura;</li>
 *   <li>sobre a cabeça (o irmão {@link #camadas()}): a cúpula 3D aparece em cima do personagem
 *       enquanto está aberto — ver {@code CamadaUmbrella}.</li>
 * </ol>
 *
 * <p>O estado "aberto" é o MESMO que o servidor usa pra barrar o sol
 * ({@code VampiroSistema.guardaChuvaAberto}: item em uso). Cliente e servidor não têm como
 * discordar: quem manda é o pacote de uso do item do próprio Minecraft.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class UmbrellaHud {

    /** quantas cores existem (as mesmas 8 do gerador de arte). */
    public static final int CORES = 8;

    private UmbrellaHud() {
    }

    /** devolve {@code true} se o jogador está usando o umbrella; guarda a cor atual. */
    public static int corDo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        try {
            Object bruto = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            if (bruto instanceof Number n) {
                return Math.floorMod(n.intValue(), CORES);
            }
            if (bruto != null) {
                // 1.21.1 pode devolver um registro (value/components) em vez de int
                Object v = bruto.getClass().getMethod("value").invoke(bruto);
                if (v instanceof Number n2) {
                    return Math.floorMod(n2.intValue(), CORES);
                }
            }
        } catch (Throwable ignorado) {
            // sem dado de modelo = primeira cor
        }
        return 0;
    }

    /** a mão que está com o umbrella aberto, ou {@code null}. */
    public static ItemStack aberto(Player p) {
        if (p == null || !p.isUsingItem()) {
            return null;
        }
        ItemStack emUso = p.getUseItem();
        return emUso.getItem() instanceof VarinhaSombraItem ? emUso : null;
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

    public static ResourceLocation texturaCupula(int cor) {
        return ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID,
                "textures/entity/umbrella/dome_" + Math.floorMod(cor, CORES) + ".png");
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
                    : "\u00a77bot\u00e3o direito";
            int larguraSub = mc.font.width(sub);
            g.drawString(mc.font, sub, x - larguraSub - 6, y + 16, 0xFFFFFF, false);
            if (estaAberto && mc.player != null) {
                int ticks = mc.player.getUseItemRemainingTicks();
                int seg = Math.max(0, ticks / 20);
                String resta = "\u00a77\u00b7 \u00a7f" + seg + "s";
                g.drawString(mc.font, resta, x - mc.font.width(resta) - 6, y + 26, 0xFFFFFF, false);
            }
        } catch (Throwable ignorado) {
            // HUD é enfeite: nunca derruba o resto
        }
    }

    /** só para o javadoc de cima apontar pra alguém. */
    static Class<?> camadas() {
        return CamadaUmbrella.class;
    }
}
