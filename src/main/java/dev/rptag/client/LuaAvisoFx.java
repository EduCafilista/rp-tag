package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.cl;
import static dev.rptag.client.AvisoFxUtil.col;
import static dev.rptag.client.AvisoFxUtil.cos;
import static dev.rptag.client.AvisoFxUtil.mix;
import static dev.rptag.client.AvisoFxUtil.rnd;
import static dev.rptag.client.AvisoFxUtil.sin;
import static dev.rptag.client.AvisoFxUtil.ss;
import static dev.rptag.client.CartaTarotFx.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * XVIII · A LUA — estilo 30 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo do dono ({@code LuaWarning}): a lua cheia enorme, os dois lobos uivando
 * com os anéis do uivo, a água prateada, os pilares do portal e as oito fases da lua girando na
 * borda do sigilo.
 */
public final class LuaAvisoFx extends CartaTarotFx {

    private static final LuaAvisoFx INST = new LuaAvisoFx();
    private final Tema t = new TemaLua();

    private LuaAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "lua", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaLua extends CartaTarotFx.Tema {
        final List<float[]> gotas = new ArrayList<>();

        TemaLua() {
            nome = "A LUA";
            romano = "XVIII";
            ac = 0xAABEFF;
            fogc = 0x8296D2;
            sky0 = 0x05050F;
            sky1 = 0x141A3A;
            glowc = 0x96AAFF;
            flashc = 0xC8D7FF;
            shc = 0x96AAFF;
            tg0 = 0xF5F7FF;
            tg1 = 0xA8B8FF;
            tg2 = 0x4A5AA8;
            bk = 0x0A0C1E;
            pc0 = 0xD8DCF0;
            pc1 = 0x9AA2C8;
            ink = 0x1A1F3C;
            l1 = "Sob a lua, nada é o que parece ser.";
            l3 = "Ouça o uivo ao longe.";
            l3c = 0xB8A0FF;
            c1 = 0xB4BDE0;
            c2 = 0xF2F4FF;
            palavras = new String[]{"uivo", "a lua mente", "ilusão", "o que se esconde", "maré", "sonho ruim",
                    "sombras", "a água sabe"};
        }

        @Override
        void reset() {
            gotas.clear();
        }

        @Override
        void emblem() {
            disco(0, 0, 8, ac, 1f);
            disco(4, -2, 7, bk, 1f);
        }

        @Override
        void back(float t) {
            disco(0, 0, 26, 0xC8D2FF, 1f);
            disco(11, -5, 22, bk, 1f);
            estrela8(-34, -48, 6, 2.2f, 0, true, 0xC8D2FF, 1f);
            estrela8(34, 50, 5, 2, 0, true, 0xC8D2FF, 1f);
            estrela8(30, -52, 4, 1.6f, 0, true, 0xC8D2FF, 1f);
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x0A1040, 0x4A5AA8);
            for (int k = 0; k < 16; k++) {
                float a = k * 0.3927f, r = k % 2 == 1 ? 22 : 26;
                poligono(new float[]{cos(a) * 17, cos(a) * r, cos(a + 0.15f) * 17},
                        new float[]{-30 + sin(a) * 17, -30 + sin(a) * r, -30 + sin(a + 0.15f) * 17}, 3,
                        col(0xC8CDF0, 0.6f));
            }
            disco(0, -30, 14, 0xF4F4DC, 1f);
            retangulo(-38, 0, 10, 40, col(0x1A2050, 1f));
            retangulo(28, 0, 10, 40, col(0x1A2050, 1f));
            curva(0, 54, -12, 38, 0, 26, 3f, col(0xCFD6F4, 1f));
            curva(0, 26, 10, 16, 0, 8, 3f, col(0xCFD6F4, 1f));
            for (int s : new int[]{-1, 1}) {
                poligono(new float[]{s * 22, s * 20, s * 16, s * 14, s * 14},
                        new float[]{44, 32, 24, 32, 44}, 5, col(0x0A0C20, 1f));
            }
            elipse(-22 + 22, 46 + 6, 22, 6, 0x1A3A7A, 1f);
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.5f, 2f, T), mx = PCX, my = 100;
            for (int i = 0; i < 80; i++) {
                alfa = a * (0.2f + 0.7f * (0.5f + 0.5f * sin(t * (0.7f + rn(i + 7, 1) * 2) + i)));
                float s = rn(i + 5, 2) > 0.85f ? 2 : 1;
                retangulo(rn(i, 3) * PW, rn(i + 3, 4) * 330, s, s, col(0xF4F6FF, 1f));
            }
            alfa = 1f;
            // o halo da lua
            mancha(mx, my, 340, 0xBECDFF, 0.5f * a);
            mancha(mx, my, 160, 0x8296E6, 0.18f * a);
            // a lua cheia (três discos para o degradê)
            disco(mx, my, 62, 0xA9B6EC, a);
            disco(mx, my, 56, 0xDFE6FF, a);
            disco(mx, my, 48, 0xFFFFFF, a);
            float[][] crateras = {{-20, -12, 12}, {18, 14, 15}, {-8, 26, 7}, {26, -20, 8}, {-30, 16, 6}};
            for (float[] c : crateras) {
                disco(mx + c[0], my + c[1], c[2], 0x7887BE, 0.25f);
            }
            // a água
            retangulo(0, 452, PW, PH - 452, col(0x04061A, 1f));
            degradeRect(0, 452, PW, 30, 0x0A1030, 0x04061A);
            aditivo();
            for (int k = 0; k < 14; k++) {
                float y = 458 + k * 9, w = (40 + k * 9) * (0.8f + 0.2f * sin(t * 1.5f + k));
                float x = mx + sin(t * 1.2f + k * 0.7f) * (4 + k * 0.8f);
                retangulo(x - w / 2, y, w, 2, col(0xC8D7FF, (0.35f - 0.02f * k) * a));
            }
            normal();
        }

        @Override
        void mid(float T, float t) {
            float a = ss(2f, 3f, T);
            if (a <= 0.01f) {
                return;
            }
            // as duas margens
            folha(-10, PH, -10, 470, 200, PH, 100, 440, col(0x03030A, 1f));
            folha(PW + 10, PH, PW + 10, 470, 600, PH, 700, 440, col(0x03030A, 1f));
            for (int s : new int[]{-1, 1}) {
                float x = s < 0 ? 70 : 730, y = 462;
                save();
                translate(x, y);
                scale(-s, 1);
                alfa = a;
                poligono(new float[]{-20, -14, -8, -4, 6, 9, 1, 3, 14, 20},
                        new float[]{0, -24, -34, -50, -62, -58, -46, -30, -12, 0}, 10, col(0x03030A, 1f));
                poligono(new float[]{-6, -8, -1}, new float[]{-52, -62, -54}, 3, col(0x03030A, 1f));
                alfa = 1f;
                restore();
                // os anéis do uivo
                float sx0 = x + (s < 0 ? 8 : -8), sy0 = y - 60;
                float ang = (float) Math.atan2(100 - sy0, PCX - sx0);
                aditivo();
                for (int k = 0; k < 3; k++) {
                    float ph = (t * 0.5f + k / 3f + (s > 0 ? 0.2f : 0)) % 1, rr = 14 + ph * 80;
                    arco2(sx0, sy0, rr, -(ang + 0.6f), 1.2f, (1 - ph) * 0.4f * a, 2f);
                }
                normal();
            }
        }

        /** arco aberto em radianos (o {@code strokeArc} do protótipo). */
        private void arco2(float cx, float cy, float r, float inicio, float extent, float a, float w) {
            int n = 14;
            float px = cx + cos(inicio) * r, py = cy + sin(inicio) * r;
            for (int i = 1; i <= n; i++) {
                float an = inicio + extent * i / n;
                float ex = cx + cos(an) * r, ey = cy + sin(an) * r;
                linha(px, py, ex, ey, w, col(0xBECDFF, a));
                px = ex;
                py = ey;
            }
        }

        @Override
        void front(float T, float t) {
            if (T > 2 && R.nextFloat() < 0.12f && gotas.size() < 30) {
                gotas.add(new float[]{PCX + (R.nextFloat() - 0.5f) * 200, 140, 0.8f + R.nextFloat() * 1.2f});
            }
            aditivo();
            Iterator<float[]> it = gotas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[1] += p[2];
                p[2] += 0.03f;
                if (p[1] > 455) {
                    it.remove();
                    continue;
                }
                linha(p[0], p[1] - p[2] * 4, p[0], p[1], 2f, col(0xD2DEFF, 0.7f));
                disco(p[0], p[1], 2, 0xFFFFFF, 0.9f);
            }
            for (int i = 0; i < 14; i++) {
                float x = PCX + cos(t * 0.2f + i * 2) * (180 + rn(i, 1) * 220);
                float y = PCY + 90 + sin(t * 0.3f + i * 3) * (70 + rn(i + 5, 2) * 110);
                disco(x, y, 1.8f, 0xBED2FF, (0.3f + 0.7f * (0.5f + 0.5f * sin(t * 2 + i))) * 0.8f);
            }
            normal();
        }

        @Override
        void ring(float T, float t, float dp) {
            int cur = (int) Math.floor(t * 1.2f) % 8;
            for (int k = 0; k < 8; k++) {
                save();
                rotate(k * 0.7854f);
                translate(0, -150);
                // a fase da lua de cada casa
                disco(0, 0, 7, 0xE1E8FF, (k == cur ? 1 : 0.6f) * dp);
                int f = (4 - k) * 3;   // -8 … +8 : a sombra de cada fase
                save();
                recorte(-7f, -7f, 14f, 14f);
                disco(f, 0, 7, bk, dp);
                semRecorte();
                restore();
                contorno(0, 0, 7, 1, col(0xBECDFF, 0.9f * dp));
                restore();
            }
        }
    }
}
