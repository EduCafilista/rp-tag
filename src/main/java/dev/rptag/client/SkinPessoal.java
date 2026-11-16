package dev.rptag.client;

import dev.rptag.FichaSkinPayload;
import dev.rptag.SkinFicha;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * (5.61.0) A SKIN DO JOGADOR, LADO CLIENTE.
 *
 * <p>Quem sabe qual skin a pessoa esta usando e o CLIENTE (o servidor nunca ve o arquivo), entao
 * daqui sai o pacote {@link FichaSkinPayload} com o link da textura e o modelo (classico/slim).
 * O servidor guarda na ficha e o tablet passa a mostrar a skin DAQUELE momento.
 *
 * <p>Pra desenhar, a textura e baixada uma vez pelo {@link HttpTexture} do proprio Minecraft
 * (com cache em disco) e fica registrada; ate chegar, o tablet mostra um quadradinho vazio.
 */
public final class SkinPessoal {
    private static final Map<String, ResourceLocation> TEXTURAS = new HashMap<>();
    private static final Set<String> CARREGADAS = new HashSet<>();

    private SkinPessoal() {}

    /** Manda pro servidor a skin que o jogador esta usando AGORA. */
    public static void enviar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PacketDistributor.sendToServer(new FichaSkinPayload(urlAtual(), modeloAtual()));
    }

    public static String urlAtual() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof AbstractClientPlayer p)) return "";
        var skin = p.getSkin();
        String url = skin == null ? null : skin.textureUrl();
        return url != null && SkinFicha.urlValida(url) ? url : "";
    }

    public static String modeloAtual() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof AbstractClientPlayer p)) return "";
        var skin = p.getSkin();
        if (skin == null || skin.model() == null) return "";
        return skin.model().id();
    }

    /** Textura registrada do link (baixa em segundo plano na primeira vez). */
    public static ResourceLocation textura(String url) {
        if (!SkinFicha.urlValida(url) || url.isEmpty()) return null;
        ResourceLocation pronta = TEXTURAS.get(url);
        if (pronta != null) return pronta;
        try {
            Minecraft mc = Minecraft.getInstance();
            String hash = url.substring(url.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9_]", "");
            if (hash.isEmpty()) return null;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "ficha_skin/" + hash);
            File cache = new File(new File(mc.gameDirectory, "assets/skins"), hash + ".png");
            HttpTexture textura = new HttpTexture(cache, url, id, false, () -> CARREGADAS.add(url));
            mc.getTextureManager().register(id, textura);
            TEXTURAS.put(url, id);
            return id;
        } catch (RuntimeException erro) {
            TEXTURAS.put(url, null);
            return null;
        }
    }

    /** A textura ja chegou do servidor de skins? */
    public static boolean pronta(String url) {
        return CARREGADAS.contains(url) && TEXTURAS.get(url) != null;
    }

    /**
     * Desenha o ROSTO da skin salva (8x8 do rosto + 8x8 do chapeu), do tamanho pedido.
     * Antes de baixar, desenha um quadradinho escuro pra nao pular layout.
     */
    public static void desenhar(GuiGraphics g, String url, int x, int y, int tamanho) {
        ResourceLocation id = textura(url);
        if (id == null || !pronta(url)) {
            g.fill(x, y, x + tamanho, y + tamanho, 0xff1b2030);
            return;
        }
        g.blit(id, x, y, tamanho, tamanho, 8.0F, 8.0F, 8, 8, 64, 64);
        g.blit(id, x, y, tamanho, tamanho, 40.0F, 8.0F, 8, 8, 64, 64);
    }
}
