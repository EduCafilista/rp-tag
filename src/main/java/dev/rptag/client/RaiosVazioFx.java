package dev.rptag.client;

import java.util.UUID;

/**
 * (5.68.0) QUEM E O VAZIO — <b>so o estado.</b>
 *
 * <p>Esta classe desenhava os <b>raios antigos</b> saindo do corpo de quem usava
 * {@code /vazio ligar} (a geometria 3D de 5.11.1, aquela que o aviso do comando ainda chama de
 * "raios amarelos e negros"). O dono, no pedido 123: <i>"no vazio ligar, eu pedi para voce remover
 * os raios amarelos, quero que deixe apenas o do novo codigo, os dois juntos nao, tira os raios
 * antigos"</i>.
 *
 * <p><b>Os raios foram apagados daqui.</b> Agora o vazio tem UM visual so: o do zip novo
 * ({@code AuraShellLayer} = casca de tinta colada na silhueta + {@code AuraFx} = tentaculos e
 * mancha no chao). O que sobrou nesta classe e o que os outros arquivos perguntam: quem esta com
 * o dom do vazio ({@link #alvoUuid()}) e se ele esta ligado ({@link #ligado()}) — o
 * {@code VazioAuraFx}, o rastro e o manto escuro continuam funcionando por causa disso.
 */
public final class RaiosVazioFx {

    private static boolean ativo;
    private static boolean euSou;
    private static UUID alvo;
    private static long surtoAte = 0L;

    /** Chamado pelo pacote de aura: quem e o vazio e se sou eu. */
    public static void definir(UUID id, boolean souEu) {
        if (id == null) {
            desligar();
            return;
        }
        if (!id.equals(alvo) || !ativo) {
            surtoAte = System.currentTimeMillis() + 2500L;   // transformacao
        }
        alvo = id;
        euSou = souEu;
        ativo = true;
    }

    /** {@code /vazio desligar}: apaga o vazio de todo mundo que esta vendo. */
    public static void desligar() {
        ativo = false;
        alvo = null;
        euSou = false;
        surtoAte = 0L;
    }

    public static boolean ligado() {
        return ativo && alvo != null;
    }

    /** Quem e o vazio (a casca de tinta e o rastro usam o mesmo alvo). */
    public static UUID alvoUuid() {
        return ativo ? alvo : null;
    }

    /** ainda esta no surto da transformacao? */
    public static boolean emSurto() {
        return System.currentTimeMillis() < surtoAte;
    }

    /** sou eu o vazio? (guardado pro caso de algum efeito precisar) */
    public static boolean souEu() {
        return ativo && euSou;
    }

    private RaiosVazioFx() {}
}
