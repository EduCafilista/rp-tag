package dev.rptag.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (4.2.0) O fx.json de um aviso em video: fps, quadros, mistura e os
 * MARCADORES exportados da timeline do Blender. Java puro (testavel).
 */
public final class FxTimeline {
    public static final int LARGURA = 1280, ALTURA = 720;

    private final int fps, frames;
    private final boolean aditivo;
    private final Map<String, List<Integer>> marcadores;

    private FxTimeline(int fps, int frames, boolean aditivo, Map<String, List<Integer>> marcadores) {
        this.fps = fps;
        this.frames = frames;
        this.aditivo = aditivo;
        this.marcadores = marcadores;
    }

    /** Le e VALIDA o fx.json; qualquer coisa fora do formato -> IllegalArgumentException. */
    public static FxTimeline parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            int fps = o.get("fps").getAsInt(), frames = o.get("frames").getAsInt();
            int w = o.get("width").getAsInt(), h = o.get("height").getAsInt();
            String blend = o.get("blend").getAsString();
            if (fps <= 0 || frames <= 0) throw new IllegalArgumentException("fps/frames invalidos");
            if (w != LARGURA || h != ALTURA) throw new IllegalArgumentException("tamanho tem que ser 1280x720");
            if (!blend.equals("normal") && !blend.equals("aditivo")) throw new IllegalArgumentException("blend invalido: " + blend);
            Map<String, List<Integer>> m = new HashMap<>();
            JsonArray arr = o.has("markers") ? o.getAsJsonArray("markers") : new JsonArray();
            for (JsonElement e : arr) {
                JsonObject mo = e.getAsJsonObject();
                int f = mo.get("frame").getAsInt();
                if (f < 0 || f >= frames) throw new IllegalArgumentException("marcador fora do video: " + f);
                m.computeIfAbsent(mo.get("name").getAsString(), k -> new ArrayList<>()).add(f);
            }
            return new FxTimeline(fps, frames, blend.equals("aditivo"), m);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("fx.json invalido: " + e.getMessage(), e);
        }
    }

    public int fps() { return fps; }
    public int frames() { return frames; }
    public int width() { return LARGURA; }
    public int height() { return ALTURA; }
    public boolean aditivo() { return aditivo; }
    public float duracao() { return frames / (float) fps; }

    /** Quadro pelo RELOGIO (atrasou = pula quadros): -1 antes do inicio, frames = fim. */
    public int quadroEm(float t) {
        if (t < 0) return -1;
        return (int) Math.min(frames, Math.floor(t * fps));
    }

    /**
     * Retangulo {x, y, largura, altura} do video na tela: SEMPRE 16:9 (nao
     * distorce em 16:10/21:9), centralizado e cobrindo a tela inteira (corta
     * as sobras dos lados ou de cima/baixo).
     */
    public static int[] cobrir(int telaW, int telaH) {
        float s = Math.max(telaW / (float) LARGURA, telaH / (float) ALTURA);
        int w = Math.round(LARGURA * s), h = Math.round(ALTURA * s);
        return new int[]{(int) Math.floor((telaW - w) / 2f), (int) Math.floor((telaH - h) / 2f), w, h};
    }

    public List<Integer> marcadores(String nome) {
        return Collections.unmodifiableList(marcadores.getOrDefault(nome, List.of()));
    }
}
