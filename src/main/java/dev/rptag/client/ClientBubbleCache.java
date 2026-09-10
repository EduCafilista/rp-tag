package dev.rptag.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache do cliente: balao de fala atual de cada jogador, recebido via pacote.
 * Um balao por jogador — se ele falar de novo, o balao e substituido.
 */
public final class ClientBubbleCache {

    /** Um balao visivel. */
    public record Bubble(String text, int colorRGB, int borderRGB, int borderStyle, int textRGB,
            boolean italic, String corner, int stickerX, int stickerY, long expireMillis) {
        public boolean expired() {
            return System.currentTimeMillis() >= expireMillis;
        }
    }

    private static final Map<UUID, Bubble> BUBBLES = new ConcurrentHashMap<>();

    private ClientBubbleCache() {
    }

    public static void set(UUID id, String text, int colorRGB, int borderRGB, int borderStyle, int textRGB,
            boolean italic, String corner, int stickerX, int stickerY, int durationMs) {
        // duracao escolhida pelo autor do balao (2 a 20 segundos)
        long duration = Math.min(20000, Math.max(2000, durationMs));
        BUBBLES.put(id, new Bubble(text, colorRGB, borderRGB, borderStyle, textRGB, italic, corner,
                stickerX, stickerY, System.currentTimeMillis() + duration));
    }

    /** @return o balao atual do jogador ou null se nao tem/esta expirado. */
    public static Bubble get(UUID id) {
        Bubble b = BUBBLES.get(id);
        if (b == null) {
            return null;
        }
        if (b.expired()) {
            BUBBLES.remove(id);
            return null;
        }
        return b;
    }

    public static void clear() {
        BUBBLES.clear();
    }
}
