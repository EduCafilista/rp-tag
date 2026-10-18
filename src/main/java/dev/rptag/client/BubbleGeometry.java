package dev.rptag.client;

/**
 * (3.60.0) GERACAO PROCEDURAL DO BALAO — sem textura nenhuma.
 *
 * <p>Ate a 3.58 o balao era montado com texturas fatiadas (pilula em 3
 * pedacos + aneis + rabicho como quad separado). Isso dava: bordas
 * borradas quando a textura estica, o rabicho com contorno proprio (emenda),
 * cantos que deformam em balao alto, e cada moldura dependendo de um PNG.
 *
 * <p>Agora o balao e UMA silhueta de verdade: retangulo de cantos
 * arredondados + rabicho curvo de HQ no MESMO contorno (um poligono so),
 * triangulado por ear-clipping. O contorno e uma faixa de tamanho exato
 * seguindo a silhueta (inclusive em volta do rabicho), a sombra e a borda
 * tem suavizacao (anti-aliasing) por gradiente de alfa nos vertices, e o
 * recheio tem degrade + brilho de gloss.
 *
 * <p>Esta classe NAO conhece o Minecraft: so gera triangulos coloridos num
 * {@link Sink}. O mundo e a previa do menu usam EXATAMENTE a mesma funcao —
 * o que aparece no /balao e o que aparece sobre a cabeca.
 *
 * <p>Convencao de coordenadas: Y cresce PRA BAIXO (igual ao mundo/GUI).
 */
public final class BubbleGeometry {

    private BubbleGeometry() {
    }

    /** Recebe os triangulos (cada vertice com cor ARGB propria). */
    public interface Sink {
        void tri(float z,
                float x1, float y1, int c1,
                float x2, float y2, int c2,
                float x3, float y3, int c3);
    }

    // ---- ids dos estilos (iguais ao BubbleStyle) ----
    public static final int CLASSICA = 0;
    public static final int CARTUM = 1;
    public static final int QUADRADA = 2;
    public static final int COSTURA = 3;
    public static final int BRILHO = 4;

    // ---- camadas em Z (maior = mais perto da camera; texto fica em 0) ----
    public static final float Z_SOMBRA = -0.34F;
    public static final float Z_HALO = -0.30F;
    public static final float Z_TINTA = -0.26F;
    public static final float Z_BORDA = -0.22F;
    public static final float Z_RECHEIO = -0.18F;
    public static final float Z_FEATHER = -0.17F;
    public static final float Z_GLOSS = -0.14F;
    public static final float Z_COSTURA = -0.12F;

    /** Largura da suavizacao (AA) das bordas, em unidades do balao. */
    private static final float AA = 0.22F;

    /** Geometria do rabicho (fixo — nunca escala com o balao). */
    public static final float TAIL_BASE = 11.5F;
    public static final float TAIL_DEPTH = 9.0F;
    private static final float TAIL_LEAN = 0.9F;
    private static final float TAIL_FLARE = 1.8F;

    // =====================================================================
    //  POLIGONO
    // =====================================================================

    /** Poligono simples (Y pra baixo), orientacao horaria na tela. */
    public static final class Poly {
        public float[] x;
        public float[] y;
        public int n;

        Poly(int cap) {
            x = new float[cap];
            y = new float[cap];
        }

        void add(float px, float py) {
            if (n > 0) {
                float dx = px - x[n - 1];
                float dy = py - y[n - 1];
                if (dx * dx + dy * dy < 1.0e-6F) {
                    return; // ponto repetido: nunca gera aresta de comprimento 0
                }
            }
            if (n == x.length) {
                x = java.util.Arrays.copyOf(x, n * 2);
                y = java.util.Arrays.copyOf(y, n * 2);
            }
            x[n] = px;
            y[n] = py;
            n++;
        }

        /** Remove o ultimo ponto se ele repete o primeiro (poligono fechado). */
        void close() {
            if (n > 1) {
                float dx = x[0] - x[n - 1];
                float dy = y[0] - y[n - 1];
                if (dx * dx + dy * dy < 1.0e-6F) {
                    n--;
                }
            }
        }

        /** Area com sinal (positiva = horaria quando Y aponta pra baixo). */
        float signedArea() {
            float a = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                a += x[i] * y[j] - x[j] * y[i];
            }
            return a * 0.5F;
        }
    }

    /** Parametros da silhueta. */
    public static final class Spec {
        public float x0, y0, w, h;       // corpo (retangulo)
        public float radius;             // raio dos cantos
        public boolean tail = true;      // com rabicho?
        public float tailCx;             // x do centro do rabicho
    }

    private static int arcSteps(float r) {
        return Math.max(3, Math.min(14, (int) Math.ceil(r * 1.1F)));
    }

    private static void arc(Poly p, float cx, float cy, float r, float a0Deg, float a1Deg) {
        int steps = arcSteps(r);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0Deg + (a1Deg - a0Deg) * i / (double) steps);
            p.add(cx + (float) (r * Math.cos(a)), cy + (float) (r * Math.sin(a)));
        }
    }

    /** Bezier cubica amostrada (sem repetir o 1o ponto, que o caminho ja tem). */
    private static void cubic(Poly p, float x0, float y0, float x1, float y1,
            float x2, float y2, float x3, float y3, int steps) {
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float u = 1.0F - t;
            float b0 = u * u * u;
            float b1 = 3 * u * u * t;
            float b2 = 3 * u * t * t;
            float b3 = t * t * t;
            p.add(b0 * x0 + b1 * x1 + b2 * x2 + b3 * x3,
                    b0 * y0 + b1 * y1 + b2 * y2 + b3 * y3);
        }
    }

    /**
     * Silhueta COMPLETA: cantos arredondados + rabicho curvo de HQ no mesmo
     * contorno. Percorre no sentido horario (na tela, Y pra baixo).
     */
    public static Poly silhouette(Spec s) {
        float r = Math.max(0.5F, Math.min(s.radius, Math.min(s.w, s.h) / 2.0F));
        float xl = s.x0, xr = s.x0 + s.w, yt = s.y0, yb = s.y0 + s.h;
        Poly p = new Poly(96);

        // topo-esquerdo -> topo-direito
        arc(p, xl + r, yt + r, r, 180, 270);
        arc(p, xr - r, yt + r, r, 270, 360);
        // direita -> baixo-direito
        arc(p, xr - r, yb - r, r, 0, 90);

        if (s.tail) {
            float cx = Math.max(xl + r + TAIL_BASE / 2.0F + 2.0F,
                    Math.min(xr - r - TAIL_BASE / 2.0F - 2.0F, s.tailCx));
            // O rabicho e definido por um PERFIL: meia-largura w(f) e deslocamento
            // lateral xc(f) ao longo da profundidade f (0 = encaixe, 1 = ponta).
            // Lados levemente concavos (cintura fina), encaixe com um pequeno
            // "fillet" (flare) pra nao ter quina dura, ponta com 0.7 de largura.
            final int N = 10;
            float[] rx = new float[N + 1];
            float[] ry = new float[N + 1];
            float[] lx = new float[N + 1];
            float[] ly = new float[N + 1];
            for (int i = 0; i <= N; i++) {
                float f = i / (float) N;
                float w = (TAIL_BASE / 2.0F) * (float) Math.pow(1.0F - f, 1.30F) 
                        + TAIL_FLARE * (float) Math.pow(Math.max(0.0F, 1.0F - f / 0.22F), 2.0F);
                float xc = cx + TAIL_LEAN * (float) Math.pow(f, 1.5F);
                float yy = yb + f * TAIL_DEPTH;
                rx[i] = xc + w;
                ry[i] = yy;
                lx[i] = xc - w;
                ly[i] = yy;
            }
            for (int i = 0; i <= N; i++) { // encaixe direito -> ponta
                p.add(rx[i], ry[i]);
            }
            for (int i = N; i >= 0; i--) { // ponta -> encaixe esquerdo
                p.add(lx[i], ly[i]);
            }
        }

        // baixo-esquerdo -> esquerda
        arc(p, xl + r, yb - r, r, 90, 180);
        p.close();
        return p;
    }

    // =====================================================================
    //  TRIANGULACAO (ear clipping) — silhueta e simples, ~100 vertices
    // =====================================================================

    /** @return indices dos triangulos (3 por triangulo), poligono horario ou nao. */
    static int[] triangulate(Poly p) {
        int n = p.n;
        if (n < 3) {
            return new int[0];
        }
        int[] idx = new int[n];
        boolean ccw = p.signedArea() < 0; // Y pra baixo: horaria = area > 0
        for (int i = 0; i < n; i++) {
            idx[i] = ccw ? (n - 1 - i) : i; // normaliza pra "area > 0"
        }
        int[] out = new int[(n - 2) * 3];
        int o = 0;
        int m = n;
        int guard = 0;
        int i = 0;
        while (m > 3 && guard++ < n * n) {
            int a = idx[(i + m - 1) % m];
            int b = idx[i % m];
            int c = idx[(i + 1) % m];
            if (isEar(p, idx, m, (i + m - 1) % m, i % m, (i + 1) % m)) {
                out[o++] = a;
                out[o++] = b;
                out[o++] = c;
                // remove b
                for (int k = i % m; k < m - 1; k++) {
                    idx[k] = idx[k + 1];
                }
                m--;
                i = Math.max(0, (i % (m + 1)) - 1);
            } else {
                i++;
            }
        }
        if (m == 3) {
            out[o++] = idx[0];
            out[o++] = idx[1];
            out[o++] = idx[2];
        }
        return java.util.Arrays.copyOf(out, o);
    }

    private static boolean isEar(Poly p, int[] idx, int m, int ia, int ib, int ic) {
        int a = idx[ia], b = idx[ib], c = idx[ic];
        float cross = (p.x[b] - p.x[a]) * (p.y[c] - p.y[b]) - (p.y[b] - p.y[a]) * (p.x[c] - p.x[b]);
        if (cross <= 1.0e-6F) {
            return false; // vertice reflexo (ou colinear)
        }
        for (int k = 0; k < m; k++) {
            if (k == ia || k == ib || k == ic) {
                continue;
            }
            int q = idx[k];
            if (pointInTri(p.x[q], p.y[q], p.x[a], p.y[a], p.x[b], p.y[b], p.x[c], p.y[c])) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInTri(float px, float py, float ax, float ay, float bx, float by,
            float cx, float cy) {
        float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean neg = d1 < -1.0e-6F || d2 < -1.0e-6F || d3 < -1.0e-6F;
        boolean pos = d1 > 1.0e-6F || d2 > 1.0e-6F || d3 > 1.0e-6F;
        return !(neg && pos);
    }

    // =====================================================================
    //  OFFSET (expandir/encolher o contorno) com miter limitado
    // =====================================================================

    /** Contorno deslocado {@code d} pra FORA (d<0 = pra dentro). */
    static Poly offset(Poly p, float d) {
        int n = p.n;
        Poly o = new Poly(n);
        boolean cw = p.signedArea() > 0;
        for (int i = 0; i < n; i++) {
            int pi = (i + n - 1) % n;
            int ni = (i + 1) % n;
            float e1x = p.x[i] - p.x[pi], e1y = p.y[i] - p.y[pi];
            float e2x = p.x[ni] - p.x[i], e2y = p.y[ni] - p.y[i];
            float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y);
            float l2 = (float) Math.sqrt(e2x * e2x + e2y * e2y);
            if (l1 < 1.0e-6F || l2 < 1.0e-6F) {
                o.x[i] = p.x[i];
                o.y[i] = p.y[i];
                o.n++;
                continue;
            }
            e1x /= l1;
            e1y /= l1;
            e2x /= l2;
            e2y /= l2;
            // normal "pra fora": poligono horario (Y pra baixo) -> (dy, -dx)
            float s = cw ? 1.0F : -1.0F;
            float n1x = e1y * s, n1y = -e1x * s;
            float n2x = e2y * s, n2y = -e2x * s;
            float mx = n1x + n2x, my = n1y + n2y;
            float ml = (float) Math.sqrt(mx * mx + my * my);
            if (ml < 1.0e-4F) {
                mx = n1x;
                my = n1y;
                ml = 1.0F;
            }
            mx /= ml;
            my /= ml;
            float cosHalf = mx * n1x + my * n1y; // cos(meio angulo)
            float scale = 1.0F / Math.max(0.5F, cosHalf); // miter, limitado a 2x
            o.x[i] = p.x[i] + mx * d * scale;
            o.y[i] = p.y[i] + my * d * scale;
            o.n++;
        }
        return o;
    }

    // =====================================================================
    //  EMISSAO
    // =====================================================================

    private static void emitTri(Sink s, float z, Poly p, int a, int b, int c, int[] col) {
        s.tri(z, p.x[a], p.y[a], col[a], p.x[b], p.y[b], col[b], p.x[c], p.y[c], col[c]);
    }

    /** Preenche o poligono com cor por vertice. */
    private static void fill(Sink s, float z, Poly p, int[] col) {
        int[] t = triangulate(p);
        for (int i = 0; i + 2 < t.length; i += 3) {
            emitTri(s, z, p, t[i], t[i + 1], t[i + 2], col);
        }
    }

    /** Preenche o poligono com UMA cor. */
    private static void fillFlat(Sink s, float z, Poly p, int argb) {
        int[] col = new int[p.n];
        java.util.Arrays.fill(col, argb);
        fill(s, z, p, col);
    }

    /** Faixa entre dois contornos (mesmo numero de pontos), cores por contorno. */
    private static void strip(Sink s, float z, Poly in, Poly out, int cIn, int cOut) {
        int n = in.n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            s.tri(z, in.x[i], in.y[i], cIn, out.x[i], out.y[i], cOut, out.x[j], out.y[j], cOut);
            s.tri(z, in.x[i], in.y[i], cIn, out.x[j], out.y[j], cOut, in.x[j], in.y[j], cIn);
        }
    }

    private static int alphaMul(int argb, float k) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, k)) + 0.5F);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a = (int) (((c1 >>> 24) & 0xFF) * (1 - t) + ((c2 >>> 24) & 0xFF) * t + 0.5F);
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t + 0.5F);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t + 0.5F);
        int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t + 0.5F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int shade(int argb, float f) {
        int r = Math.max(0, Math.min(255, (int) (((argb >> 16) & 0xFF) * f + 0.5F)));
        int g = Math.max(0, Math.min(255, (int) (((argb >> 8) & 0xFF) * f + 0.5F)));
        int b = Math.max(0, Math.min(255, (int) ((argb & 0xFF) * f + 0.5F)));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int argb, float k) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Anel suave: de {@code from} (cor c) ate {@code from + width} (alfa 0). */
    private static void feather(Sink s, float z, Poly edge, float width, int color) {
        Poly outer = offset(edge, width);
        strip(s, z, edge, outer, color, alphaMul(color, 0.0F));
    }

    // =====================================================================
    //  O BALAO
    // =====================================================================

    /** Tudo que o desenho precisa saber. */
    public static final class Look {
        public int style;         // 0..4
        public int fill;          // ARGB do recheio (alfa = fade global)
        public int border;        // ARGB da borda (0 = sem borda)
        public int baseRgb;       // cor escolhida (pro halo do Brilho)
        public float alpha = 1.0F; // fade global 0..1
        public float x0, y0, w, h; // corpo da pilula
        public float tailCx;      // centro do rabicho
        /** false = so o contorno de sombra (nao usado ainda). */
        public boolean shadow = true;
        /** (3.62.0) false = balao FLUTUANTE: silhueta sem rabicho nenhum
         * (os mods de referencia — Talk Balloons etc — nao usam conector). */
        public boolean tail = true;
    }

    /** Espessura do contorno por estilo (unidades do balao). */
    public static float borderWidth(int style) {
        return switch (style) {
            case CARTUM -> 3.2F;
            case QUADRADA -> 1.8F;
            case COSTURA -> 1.2F;
            case BRILHO -> 1.4F;
            default -> 1.7F;
        };
    }

    /** Raio dos cantos por estilo (a Quadrada e a unica de cantos pequenos). */
    public static float cornerRadius(int style, float h) {
        if (style == QUADRADA) {
            return Math.min(4.0F, h / 2.0F);
        }
        return Math.min(h / 2.0F, 10.0F);
    }

    /** Folga que o balao ocupa pra FORA do corpo (borda + halo) — pro layout. */
    public static float outerMargin(int style) {
        return borderWidth(style) + (style == BRILHO ? 6.0F : 0.0F) + (style == CARTUM ? 1.4F : 0.0F);
    }

    /**
     * Gera o balao inteiro: sombra, halo, contorno, recheio, gloss e (na
     * costura) os pontinhos. Ordem de submissao = ordem de pintura.
     */
    public static void build(Sink s, Look k) {
        Spec sp = new Spec();
        sp.x0 = k.x0;
        sp.y0 = k.y0;
        sp.w = k.w;
        sp.h = k.h;
        sp.radius = cornerRadius(k.style, k.h);
        sp.tailCx = k.tailCx;
        sp.tail = k.tail;
        Poly sil = silhouette(sp);

        float a = k.alpha;
        int fillTop = alphaMul(lighten(k.fill, 0.07F), 1.0F);
        int fillBot = alphaMul(shade(k.fill, 0.93F), 1.0F);
        float bw = k.border != 0 ? borderWidth(k.style) : 0.0F;

        // ---------- 1) SOMBRA SUAVE (preenchida + borda macia) ----------
        if (k.shadow) {
            Spec ss = new Spec();
            ss.tail = k.tail; // (3.63.0) a sombra seguia desenhando o rabicho
            ss.x0 = k.x0 + 0.6F;
            ss.y0 = k.y0 + 1.7F;
            ss.w = k.w;
            ss.h = k.h;
            ss.radius = sp.radius;
            ss.tailCx = k.tailCx + 0.6F;
            Poly sh = offset(silhouette(ss), bw);
            int sc = ((int) (0x3A * a) << 24);
            fillFlat(s, Z_SOMBRA, sh, sc);
            feather(s, Z_SOMBRA, sh, 2.6F, sc);
        }

        // ---------- 2) HALO do BRILHO ----------
        if (k.style == BRILHO) {
            int hc = (lighten(0xFF000000 | k.baseRgb, 0.62F) & 0xFFFFFF) | ((int) (0xE0 * a) << 24);
            Spec hs = new Spec();
            hs.x0 = k.x0;
            hs.y0 = k.y0;
            hs.w = k.w;
            hs.h = k.h;
            hs.radius = sp.radius;
            hs.tail = false; // halo so em volta do corpo (o rabicho passa por cima)
            Poly hsil = silhouette(hs);
            Poly in = offset(hsil, bw);
            Poly mid = offset(hsil, bw + 2.4F);
            Poly out = offset(hsil, bw + 6.0F);
            strip(s, Z_HALO, in, mid, alphaMul(hc, 0.90F), alphaMul(hc, 0.42F));
            strip(s, Z_HALO, mid, out, alphaMul(hc, 0.42F), alphaMul(hc, 0.0F));
        }

        // ---------- 3) TINTA-SOMBRA do CARTUM (deslocamento duro de HQ) ----------
        if (k.style == CARTUM && k.border != 0) {
            Spec cs = new Spec();
            cs.x0 = k.x0 + 1.2F;
            cs.y0 = k.y0 + 1.6F;
            cs.w = k.w;
            cs.h = k.h;
            cs.radius = sp.radius;
            cs.tailCx = k.tailCx + 1.2F;
            Poly ink = offset(silhouette(cs), bw);
            int ic = shade(k.border, 0.55F);
            fillFlat(s, Z_TINTA, ink, ic);
            feather(s, Z_TINTA, ink, AA, ic);
        }

        // ---------- 4) CONTORNO (faixa colada na silhueta, inclusive no rabicho) ----------
        if (k.border != 0) {
            Poly outer = offset(sil, bw);
            strip(s, Z_BORDA, sil, outer, k.border, k.border);
            feather(s, Z_BORDA, outer, AA, k.border);
        }

        // ---------- 5) RECHEIO (degrade vertical) + suavizacao por cima da borda ----------
        {
            int[] col = new int[sil.n];
            float yTop = k.y0, yBot = k.y0 + k.h + TAIL_DEPTH;
            for (int i = 0; i < sil.n; i++) {
                col[i] = mix(fillTop, fillBot, (sil.y[i] - yTop) / (yBot - yTop));
            }
            fill(s, Z_RECHEIO, sil, col);
            // anel de AA do recheio (cobre a serrilha da juncao recheio/borda)
            Poly outer = offset(sil, AA);
            int[] fcol = new int[sil.n];
            int[] zcol = new int[sil.n];
            for (int i = 0; i < sil.n; i++) {
                fcol[i] = col[i];
                zcol[i] = alphaMul(col[i], 0.0F);
            }
            int n = sil.n;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                s.tri(Z_FEATHER, sil.x[i], sil.y[i], fcol[i], outer.x[i], outer.y[i], zcol[i],
                        outer.x[j], outer.y[j], zcol[j]);
                s.tri(Z_FEATHER, sil.x[i], sil.y[i], fcol[i], outer.x[j], outer.y[j], zcol[j],
                        sil.x[j], sil.y[j], fcol[j]);
            }
        }

        // ---------- 6) GLOSS: faixa clara no topo do corpo (so no corpo, nao no rabicho) ----------
        {
            float r = sp.radius;
            float gh = Math.max(3.0F, Math.min(k.h * 0.34F, 9.0F));
            float inset = Math.min(r * 0.55F + 1.2F, k.w / 4.0F);
            Poly g = new Poly(24);
            float gx0 = k.x0 + inset, gx1 = k.x0 + k.w - inset;
            float gy0 = k.y0 + 1.1F, gy1 = k.y0 + 1.1F + gh;
            float gr = Math.min(gh / 2.0F, 3.5F);
            arc(g, gx0 + gr, gy0 + gr, gr, 180, 270);
            arc(g, gx1 - gr, gy0 + gr, gr, 270, 360);
            arc(g, gx1 - gr, gy1 - gr, gr, 0, 90);
            arc(g, gx0 + gr, gy1 - gr, gr, 90, 180);
            g.close();
            int top = ((int) (0x4A * a) << 24) | 0xFFFFFF;
            int bot = 0x00FFFFFF;
            int[] col = new int[g.n];
            for (int i = 0; i < g.n; i++) {
                col[i] = mix(top, bot, (g.y[i] - gy0) / (gy1 - gy0));
            }
            fill(s, Z_GLOSS, g, col);
        }

        // ---------- 7) COSTURA: pontilhado por dentro do contorno DO CORPO ----------
        // (so o corpo: dentro do rabicho a curva e estreita demais e o
        // pontilhado se enrolava; no encaixe ele passa reto, como uma costura)
        if (k.style == COSTURA && k.border != 0) {
            Spec bs = new Spec();
            bs.x0 = k.x0;
            bs.y0 = k.y0;
            bs.w = k.w;
            bs.h = k.h;
            bs.radius = sp.radius;
            bs.tail = false;
            Poly path = offset(silhouette(bs), -2.0F);
            stitches(s, Z_COSTURA, path, shade(k.border, 1.0F), 3.0F, 2.2F, 0.5F);
        }
    }

    /** Pontilhado de tracinhos ao longo de um contorno fechado. */
    static void stitches(Sink s, float z, Poly path, int argb, float dash, float gap, float halfW) {
        int n = path.n;
        boolean inDash = true;   // maquina de estados: nunca depende de modulo de float
        float left = dash;       // quanto falta pro estado atual terminar
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float dx = path.x[j] - path.x[i];
            float dy = path.y[j] - path.y[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0e-5F) {
                continue;
            }
            float ux = dx / len, uy = dy / len;
            float nx = -uy * halfW, ny = ux * halfW;
            float t = 0.0F;
            int guard = 0;
            while (t < len - 1.0e-5F && guard++ < 4096) {
                float step = Math.min(left, len - t);
                if (inDash && step > 0.02F) {
                    float ax = path.x[i] + ux * t, ay = path.y[i] + uy * t;
                    float bx = path.x[i] + ux * (t + step), by = path.y[i] + uy * (t + step);
                    s.tri(z, ax - nx, ay - ny, argb, ax + nx, ay + ny, argb, bx + nx, by + ny, argb);
                    s.tri(z, ax - nx, ay - ny, argb, bx + nx, by + ny, argb, bx - nx, by - ny, argb);
                }
                t += step;
                left -= step;
                if (left <= 1.0e-5F) {
                    inDash = !inDash;
                    left = inDash ? dash : gap;
                }
            }
        }
    }
}
