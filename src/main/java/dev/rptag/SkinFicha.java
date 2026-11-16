package dev.rptag;

/**
 * (5.61.0) A SKIN GUARDADA NA FICHA.
 *
 * <p>Pedido do dono: <i>"o tablet deve salvar a skin da pessoa, salvar o modelo que a pessoa tava
 * usando quando criou/salvou o tablet"</i>. A ficha guarda o <b>link</b> da textura e o
 * <b>modelo</b> (classico ou slim) que o jogador usava no momento em que preencheu/confirmou —
 * e isso NUNCA mais muda, mesmo que ele troque de skin depois.
 *
 * <p>Seguranca: so aceita link do servidor de texturas da Mojang (nada de URL arbitraria vinda do
 * cliente) e limita o tamanho. O cliente e quem sabe a skin (o servidor nao ve o arquivo), entao
 * ele manda; o servidor confere e guarda.
 */
public final class SkinFicha {
    private SkinFicha() {}

    /** Prefixo aceito no link de textura. */
    public static final String HOST = "http://textures.minecraft.net/texture/";
    public static final int URL_MAX = 128;

    /** "" e valido (quer dizer: ficha ainda sem skin salva). */
    public static boolean urlValida(String url) {
        if (url == null || url.isEmpty()) return true;
        return url.length() <= URL_MAX && url.startsWith(HOST);
    }

    /** "" / "classic" / "slim" (o id do PlayerSkin.Model do 1.21.1 e "wide" pra classico). */
    public static boolean modeloValido(String modelo) {
        if (modelo == null) return true;
        return modelo.isEmpty() || "classic".equals(modelo) || "wide".equals(modelo) || "slim".equals(modelo);
    }

    /** Nome que aparece no tablet. */
    public static String nomeDoModelo(String modelo) {
        return "slim".equals(modelo) ? "Slim" : "Cl\u00e1ssico";
    }
}
