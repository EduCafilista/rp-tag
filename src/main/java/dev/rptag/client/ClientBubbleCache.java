package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache do cliente: baloes de fala de cada jogador, recebidos via pacote.
 *
 * <p>(3.11.0) PILHA estilo mods famosos (Chat Bubble/TalkBubbles): ate
 * {@value #MAX_STACK} baloes por jogador empilham — o mais novo fica perto
 * da cabeca e os antigos sobem. Cada balao guarda o instante em que nasceu,
 * dando o POP-IN na entrada e o FADE na saida.
 */
public final class ClientBubbleCache {

    /** Um balao visivel. */
    public record Bubble(String text, int colorRGB, int borderRGB, int borderStyle, int textRGB,
            boolean italic, String corner, String extras, int stickerX, int stickerY,
            long bornMillis, long expireMillis) {
        public boolean expired() {
            return System.currentTimeMillis() >= expireMillis;
        }
    }

    /** Maximo de baloes empilhados por jogador (padrao dos mods famosos). */
    public static final int MAX_STACK = 3;

    private static final Map<UUID, List<Bubble>> BUBBLES = new ConcurrentHashMap<>();

    private ClientBubbleCache() {
    }

    public static void set(UUID id, String text, int colorRGB, int borderRGB, int borderStyle, int textRGB,
            boolean italic, String corner, String extras, int stickerX, int stickerY, int durationMs) {
        // duracao escolhida pelo autor do balao (2 a 20 segundos)
        long duration = Math.min(20000, Math.max(2000, durationMs));
        long now = System.currentTimeMillis();
        List<Bubble> stack = BUBBLES.computeIfAbsent(id, k -> new ArrayList<>());
        synchronized (stack) {
            // (3.39.0) /balao off: o pacote de limpeza (texto vazio) agora apaga
            // a PILHA inteira na hora — antes virava um balao fantasma ocupando
            // vaga e os antigos continuavam pendurados.
            if (text.isEmpty()) {
                stack.clear();
                return;
            }
            // (3.13.0) balao cheio de emojis SUBSTITUI a pilha: nada de muro
            // de adesivos repetidos (a pilha continua pra falas normais)
            if (!extras.isEmpty()) {
                stack.clear();
            }
            stack.add(new Bubble(text, colorRGB, borderRGB, borderStyle, textRGB, italic, corner,
                    extras, stickerX, stickerY, now, now + duration));
            while (stack.size() > MAX_STACK) {
                stack.remove(0); // o mais antigo sai da pilha
            }
        }
    }

    /** @return os baloes visiveis do jogador (mais antigo primeiro) ou vazia. */
    public static List<Bubble> get(UUID id) {
        List<Bubble> stack = BUBBLES.get(id);
        if (stack == null) {
            return List.of();
        }
        synchronized (stack) {
            stack.removeIf(Bubble::expired);
            if (stack.isEmpty()) {
                BUBBLES.remove(id);
                return List.of();
            }
            return new ArrayList<>(stack);
        }
    }

    public static void clear() {
        BUBBLES.clear();
    }
}
