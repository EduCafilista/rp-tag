package dev.rptag.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * (4.8.0) Os controles do TABLET, no estilo "galáxia": botões de vidro com borda
 * que acende no hover, caixas de texto sem moldura e as barras de personalidade.
 */
final class FichaWidgets {

    static final int LINHA = 0x4A6FA8;       // azul-luz das bordas
    static final int LINHA_FORTE = 0x8FD8FF; // borda acesa
    static final int TEXTO = 0xF2ECFF;
    static final int TEXTO_FRACO = 0x9E93C4;
    static final int OURO = 0xFFD98A;

    private FichaWidgets() {
    }

    /** botão do tablet. */
    static final class Botao extends AbstractWidget {
        private final Runnable acao;
        private final boolean destaque;
        private float brilho;

        Botao(int x, int y, int w, int h, Component texto, boolean destaque, Runnable acao) {
            super(x, y, w, h, texto);
            this.acao = acao;
            this.destaque = destaque;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            Font f = Minecraft.getInstance().font;
            float alvo = isHovered ? 1f : 0f;
            brilho += (alvo - brilho) * Mth.clamp(dt * 0.35f, 0f, 1f);

            int fundo = destaque ? 0xCC241A44 : 0xCC120E24;
            int fundoHover = destaque ? 0xEE3A2A6E : 0xEE1E1840;
            g.fill(getX(), getY(), getX() + width, getY() + height, misturar(fundo, fundoHover, brilho));

            int borda = (destaque || brilho > 0.02f) ? LINHA_FORTE : LINHA;
            contorno(g, getX(), getY(), width, height, FichaFundo.corComAlfa(borda, 0.55f + 0.45f * brilho));
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + 3,
                    FichaFundo.corComAlfa(0xBFE6FF, 0.10f + 0.25f * brilho));

            // cantos em losango (o detalhe do estilo)
            for (int[] c : new int[][]{{getX(), getY()}, {getX() + width, getY()},
                    {getX(), getY() + height}, {getX() + width, getY() + height}}) {
                losango(g, c[0], c[1], 2.5f + brilho, OURO, 0.35f + 0.5f * brilho);
            }

            int tc = brilho > 0.4f ? 0xFFFFFF : (destaque ? 0xF6E6C0 : TEXTO);
            g.drawCenteredString(f, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, tc);
            if (brilho > 0.05f) {
                g.fill(getX() + 6, getY() + height - 4, getX() + width - 6, getY() + height - 3,
                        FichaFundo.corComAlfa(0x9FE0FF, 0.25f * brilho));
            }
        }

        @Override
        public void onClick(double mx, double my) {
            acao.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
        }
    }

    /**
     * (4.8.2) O "X" de fechar, no canto do painel. Antes só existia o botão
     * escrito "Fechar" lá embaixo (e sumia no resumo quando a pessoa acertava a
     * ficha). Agora tem um X sempre no mesmo lugar, do jeito de janela.
     */
    static final class BotaoX extends AbstractWidget {
        private final Runnable acao;
        private float brilho;

        BotaoX(int x, int y, Runnable acao) {
            super(x, y, 18, 18, Component.literal("Fechar"));
            this.acao = acao;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            float alvo = isHovered ? 1f : 0f;
            brilho += (alvo - brilho) * Mth.clamp(dt * 0.35f, 0f, 1f);

            g.fill(getX(), getY(), getX() + width, getY() + height,
                    misturar(0xB0120E24, 0xE04A1A22, brilho));
            contorno(g, getX(), getY(), width, height,
                    FichaFundo.corComAlfa(brilho > 0.35f ? 0xFF9A8A : LINHA, 0.55f + 0.45f * brilho));

            int cor = misturar(0xC9BCE6, 0xFFEDE6, brilho);
            int n = 9;
            for (int i = 0; i < n; i++) {
                int a = 4 + i;
                int b = 4 + (n - 1 - i);
                g.fill(getX() + a, getY() + a, getX() + a + 2, getY() + a + 2, cor);
                g.fill(getX() + b, getY() + a, getX() + b + 2, getY() + a + 2, cor);
            }
        }

        @Override
        public void onClick(double mx, double my) {
            acao.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
        }
    }

    /** uma barra de personalidade (0..100). */
    static final class Barra extends AbstractWidget {
        final int eixo;
        final int[] valores;
        private final Runnable aoMudar;

        Barra(int x, int y, int w, int h, int eixo, int[] valores, Runnable aoMudar) {
            super(x, y, w, h, Component.empty());
            this.eixo = eixo;
            this.valores = valores;
            this.aoMudar = aoMudar;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            Font f = Minecraft.getInstance().font;
            int v = valores[eixo];
            String esq = dev.rptag.FichaRp.EIXOS[eixo][0];
            String dir = dev.rptag.FichaRp.EIXOS[eixo][1];

            g.drawString(f, esq, getX(), getY() - 2, v < 50 ? TEXTO : TEXTO_FRACO, false);
            String num = String.valueOf(v);
            g.drawString(f, num, getX() + width / 2 - f.width(num) / 2, getY() - 2, OURO, false);
            g.drawString(f, dir, getX() + width - f.width(dir), getY() - 2, v > 50 ? TEXTO : TEXTO_FRACO, false);

            int y = getY() + 12;
            g.fill(getX(), y, getX() + width, y + 1, FichaFundo.corComAlfa(LINHA, 0.9f));
            for (int i = 0; i <= 4; i++) {
                int x = getX() + width * i / 4;
                g.fill(x, y - 3, x + 1, y + 4, FichaFundo.corComAlfa(LINHA, 0.9f));
            }

            int cx = getX() + width / 2;
            int px = getX() + Math.round(width * v / 100f);
            g.fill(Math.min(cx, px), y - 1, Math.max(cx, px), y + 2,
                    FichaFundo.corComAlfa(v > 50 ? 0x8FD8FF : 0xC9A6FF, 0.55f));

            int lado = isHovered || isFocused() ? 4 : 3;
            g.fill(px - lado, y - lado, px + lado, y + lado + 1, 0xFFF4F8FF);
            g.fill(px - lado + 1, y - lado + 1, px + lado - 1, y + lado, 0xFFFFFFFF);
            losango(g, px, y, 1.2f, OURO, 0.9f);
        }

        @Override
        public void onClick(double mx, double my) {
            aplicar(mx);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            aplicar(mx);
        }

        private void aplicar(double mx) {
            float f = (float) ((mx - getX()) / (double) width);
            int novo = Math.round(Mth.clamp(f, 0f, 1f) * 100);
            if (Math.abs(novo - 50) <= 3) {   // ímã no centro: fica fácil deixar "equilibrado"
                novo = 50;
            }
            valores[eixo] = novo;
            aoMudar.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
        }
    }

    /**
     * Campo de TEXTO LONGO: várias linhas, quebra de palavra, cursor, setas e Ctrl+V.
     * O EditBox do vanilla só tem uma linha — a história precisa de mais espaço.
     */
    static final class TextoLivre extends AbstractWidget {
        private final int max;
        private final String dica;
        private final Consumer<String> aoMudar;
        private String valor = "";
        private int cursor;
        private float pisca;
        private boolean editavel = true;

        TextoLivre(int x, int y, int w, int h, int max, String dica, String inicial, Consumer<String> aoMudar) {
            super(x, y, w, h, Component.empty());
            this.max = max;
            this.dica = dica;
            this.aoMudar = aoMudar;
            setValor(inicial);
        }

        String valor() {
            return valor;
        }

        void setEditavel(boolean v) {
            editavel = v;
        }

        void setValor(String s) {
            valor = s == null ? "" : s;
            if (valor.length() > max) {
                valor = valor.substring(0, max);
            }
            cursor = valor.length();
        }

        private Font fonte() {
            return Minecraft.getInstance().font;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            Font f = fonte();
            int x = getX(), y = getY(), w = width, h = height;
            g.fill(x, y, x + w, y + h, isFocused() ? 0xE0161030 : 0xC0100C1E);
            contorno(g, x, y, w, h, FichaFundo.corComAlfa(isFocused() ? LINHA_FORTE : LINHA, 0.65f));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h, FichaFundo.corComAlfa(0x4A6FA8, 0.6f));

            int largo = w - 10;
            List<int[]> linhas = quebrar(f, largo);
            int lh = 10;
            int visiveis = Math.max(1, (h - 6) / lh);

            // rola para o cursor ficar sempre à vista
            int linhaCursor = linhaDoCursor(linhas);
            int primeira = Mth.clamp(linhaCursor - visiveis + 1, 0, Math.max(0, linhas.size() - visiveis));

            if (valor.isEmpty() && !isFocused()) {
                g.drawString(f, dica, x + 5, y + 4, TEXTO_FRACO, false);
                return;
            }

            pisca += dt;
            boolean cursorAceso = ((int) (pisca * 2.5f) & 1) == 0;
            for (int i = primeira; i < Math.min(linhas.size(), primeira + visiveis); i++) {
                int[] r = linhas.get(i);
                int ly = y + 4 + (i - primeira) * lh;
                String txt = valor.substring(r[0], Math.min(r[1], valor.length()));
                g.drawString(f, txt, x + 5, ly, TEXTO, false);
                if (isFocused() && i == linhaCursor && cursorAceso) {
                    int col = Mth.clamp(cursor - r[0], 0, txt.length());
                    int cx = x + 5 + f.width(txt.substring(0, col));
                    g.fill(cx, ly - 1, cx + 1, ly + 9, 0xFFBFE6FF);
                }
            }
        }

        /** intervalos [inicio,fim) de cada linha, respeitando palavras. */
        private List<int[]> quebrar(Font f, int largo) {
            List<int[]> out = new ArrayList<>();
            int n = valor.length();
            int ini = 0;
            int ultimoEspaco = -1;
            for (int i = 0; i <= n; i++) {
                if (i == n) {
                    out.add(new int[]{ini, n});
                    break;
                }
                char c = valor.charAt(i);
                if (c == '\n') {
                    out.add(new int[]{ini, i});
                    ini = i + 1;
                    ultimoEspaco = -1;
                    continue;
                }
                if (c == ' ') {
                    ultimoEspaco = i;
                }
                if (f.width(valor.substring(ini, i + 1)) > largo) {
                    int corte = (ultimoEspaco > ini) ? ultimoEspaco : i;
                    out.add(new int[]{ini, corte});
                    ini = corte + ((corte < n && valor.charAt(corte) == ' ') ? 1 : 0);
                    if (ini <= i) {
                        ini = i;
                    }
                    ultimoEspaco = -1;
                    i = ini - 1;
                }
            }
            if (out.isEmpty()) {
                out.add(new int[]{0, 0});
            }
            return out;
        }

        private int linhaDoCursor(List<int[]> linhas) {
            for (int i = 0; i < linhas.size(); i++) {
                int[] r = linhas.get(i);
                if (cursor <= r[1]) {
                    return i;
                }
            }
            return linhas.size() - 1;
        }

        private void escrever(String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            int espaco = max - valor.length();
            if (espaco <= 0) {
                return;
            }
            String t = s.replace("\r", "").replace("\n", " ");
            if (t.length() > espaco) {
                t = t.substring(0, espaco);
            }
            valor = valor.substring(0, cursor) + t + valor.substring(cursor);
            cursor += t.length();
            aoMudar.accept(valor);
        }

        @Override
        public boolean charTyped(char c, int mods) {
            if (!isFocused() || !editavel) {
                return false;
            }
            if (c >= ' ' || c == '\n') {
                escrever(String.valueOf(c));
                return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (!isFocused() || !editavel) {
                return false;
            }
            boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0) {
                        valor = valor.substring(0, cursor - 1) + valor.substring(cursor);
                        cursor--;
                        aoMudar.accept(valor);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (cursor < valor.length()) {
                        valor = valor.substring(0, cursor) + valor.substring(cursor + 1);
                        aoMudar.accept(valor);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    cursor = Math.max(0, cursor - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    cursor = Math.min(valor.length(), cursor + 1);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    cursor = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    cursor = valor.length();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrl) {
                        escrever(Minecraft.getInstance().keyboardHandler.getClipboard());
                        return true;
                    }
                    return false;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public void onClick(double mx, double my) {
            if (!editavel) {
                return;
            }
            setFocused(true);
            cursor = valor.length();
        }

        @Override
        public void setFocused(boolean focado) {
            super.setFocused(focado);
            pisca = 0f;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
        }
    }

    /** caixa de texto curta do tablet (uma linha, sem moldura). */
    static EditBox campo(Font fonte, int x, int y, int w, int max, String valor, Consumer<String> aoDigitar) {
        EditBox box = new EditBox(fonte, x, y, w, 18, Component.empty());
        box.setBordered(false);
        box.setMaxLength(max);
        box.setTextColor(0xF2ECFF);
        box.setTextColorUneditable(0x9E93C4);
        box.setValue(valor == null ? "" : valor);
        box.setResponder(aoDigitar);
        return box;
    }

    // ------------------------------------------------------------ desenho
    static void contorno(GuiGraphics g, int x, int y, int w, int h, int cor) {
        g.fill(x, y, x + w, y + 1, cor);
        g.fill(x, y + h - 1, x + w, y + h, cor);
        g.fill(x, y, x + 1, y + h, cor);
        g.fill(x + w - 1, y, x + w, y + h, cor);
    }

    static void losango(GuiGraphics g, float cx, float cy, float r, int cor, float a) {
        int c = FichaFundo.corComAlfa(cor, a);
        for (int i = 0; i < Math.max(1, (int) Math.ceil(r)); i++) {
            float w = r - i;
            g.fill((int) (cx - w), (int) (cy - r + i), (int) (cx + w), (int) (cy - r + i + 1), c);
            g.fill((int) (cx - w), (int) (cy + r - i - 1), (int) (cx + w), (int) (cy + r - i), c);
        }
    }

    static int misturar(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int al = (int) Mth.lerp(t, aa, ba), r = (int) Mth.lerp(t, ar, br),
                gg = (int) Mth.lerp(t, ag, bg), bl = (int) Mth.lerp(t, ab, bb);
        return (al << 24) | (r << 16) | (gg << 8) | bl;
    }
}
