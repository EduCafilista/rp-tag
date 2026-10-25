package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rptag.FichaRp;
import dev.rptag.FichaSavePayload;
import dev.rptag.FichaOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * (4.8.5) O TABLET — ficha do personagem, capítulo por capítulo.
 *
 * <p>Layout novo, na linguagem da referência do dono: <b>painel alto à
 * direita</b>, cabeçalho corrido na largura da tela (capítulo + pergunta),
 * <b>rótulo em cima e valor embaixo</b> com fios finos entre as linhas, botões
 * em gradiente violeta/magenta e os pontinhos de progresso no rodapé.
 *
 * <p>O fundo continua nosso: blur do mundo + estrelas e <b>névoa de galáxia em
 * textura suave</b> (violeta/magenta em alfa baixo) — sem pixels quebrados.
 */
public class FichaScreen extends Screen {

    private static final String[] TITULO = {"NOME", "IDADE", "ORIGEM", "OCUPAÇÃO", "HABILIDADES",
            "HISTÓRIA", "PERSONALIDADE", "RESUMO"};

    private static final String[] PERGUNTA = {
            "Como o mundo vai te chamar?",
            "Quantas primaveras viveu?",
            "De onde você veio?",
            "O que você fazia antes de chegar aqui?",
            "O que você sabe fazer bem?",
            "Conte um pouco da sua história",
            "O que move a sua alma?",
            "É assim que você será lembrado?"
    };

    private static final String[] DICA = {
            "O nome que os outros vão ler no nametag, no chat e na TAB.",
            "Cada ano deixa uma marca. Qual é a sua idade?",
            "Uma cidade, um reino, um porto esquecido — de onde vêm suas lembranças?",
            "Ofício dá forma ao personagem: ferreiro, curandeira, ladrão, capitão…",
            "Magia, lâmina, negociação, bons ouvidos… o que a sua mão sabe fazer?",
            "Sem pressa: escreva os pedaços que explicam quem você é hoje.",
            "Não existem respostas certas. Encontre o seu equilíbrio.",
            "Confira com calma. Depois de confirmar, o nome passa a ser o do personagem."
    };

    private static final String[] APOIO = {
            "É assim que os outros vão te chamar no mundo.",
            "A idade faz parte da sua história, sem limitar quem você pode se tornar.",
            "O lugar de onde viemos fica na fala e nos costumes.",
            "O que suas mãos fizeram antes daqui?",
            "Habilidade não é só magia: pode ser coragem, paciência ou um bom ouvido.",
            "Pode ser curta. Só precisa ser sua.",
            "Arraste as barras — o número no meio é o seu valor.",
            "Você pode reabrir o tablet a qualquer momento para editar."
    };

    private static final String[] DICA_CAMPO = {
            "Ex.: Kael, Elara, Thorin…",
            "Ex.: 27",
            "Ex.: as montanhas de Vhoran",
            "Ex.: ferreiro, curandeira, capitão…",
            "Escreva à vontade. ENTER quebra a linha.",
            "Escreva à vontade. ENTER quebra a linha.",
            "",
            ""
    };

    // ---------------- cores da linguagem nova (referência)
    private static final int TXT = 0xF2ECFF;
    private static final int TXT_FRACO = 0x8F88AD;
    private static final int ROSA = 0xE07BFF;
    private static final int VIOLETA = 0xB04BFF;
    private static final int PAINEL = 0xC8140B2E;
    private static final int BORDA = 0x66A24BFF;

    private static final int ALTURA_BOTAO = 22;


    private final FichaRp ficha;
    private final String nomeDono;
    private final boolean dono;
    private int cap;

    // animação
    private float t;
    private float tTroca;
    private float tConfirmado = -1f;
    private boolean gesto1, gesto2;
    private boolean fecharPedido;
    private double resumoRolagem;
    private double resumoMax;

    // layout (recalculado no init)
    private int px, py, pw, ph;

    private final Map<AbstractWidget, Integer> animaveis = new LinkedHashMap<>();
    private EditBox campoAtual;
    private FichaWidgets.TextoLivre campoLongo;

    public FichaScreen(FichaOpenPayload p) {
        super(Component.literal("Ficha"));
        this.ficha = p.ficha();
        this.nomeDono = p.nomeDono();
        this.dono = p.editar();
        this.cap = Mth.clamp(p.ficha().pagina, 0, FichaRp.CAP_RESUMO);
    }

    // ================================================================ layout
    @Override
    protected void init() {
        animaveis.clear();
        campoAtual = null;
        campoLongo = null;
        FichaFundo.preparar();

        // painel alto à direita, como na referência
        pw = Mth.clamp((int) (width * 0.42f), 286, Math.max(300, width - 60));
        px = width - 18 - pw;
        py = Math.max(58, (int) (height * 0.19f));
        ph = height - 40 - py;

        int ix = px + 14;                  // margem interna
        int iw = pw - 28;
        int topo = py + 44;                // abaixo de "dica + fio"

        if (cap <= FichaRp.CAP_OFICIO) {
            campoAtual = FichaWidgets.campo(font, ix, topo + 10, iw, limiteDoCampo(cap),
                    valorDoCampo(cap), s -> setCampo(cap, s));
            campoAtual.setEditable(dono);
            add(campoAtual);
        } else if (cap == FichaRp.CAP_HABILIDADES || cap == FichaRp.CAP_HISTORIA) {
            boolean hist = cap == FichaRp.CAP_HISTORIA;
            campoLongo = new FichaWidgets.TextoLivre(ix, topo + 10, iw, hist ? 92 : 56,
                    hist ? 400 : 160, DICA_CAMPO[cap], hist ? ficha.historia : ficha.habilidades,
                    s -> {
                        if (hist) {
                            ficha.historia = s;
                        } else {
                            ficha.habilidades = s;
                        }
                    });
            campoLongo.setEditavel(dono);
            add(campoLongo);
        } else if (cap == FichaRp.CAP_PERSONALIDADE) {
            int passo = Mth.clamp((ph - 96) / 6, 18, 30);
            for (int i = 0; i < 6; i++) {
                FichaWidgets.Barra b = new FichaWidgets.Barra(ix, topo + 16 + i * passo, iw, 14, i,
                        ficha.persona, () -> {
                        });
                b.active = dono;
                add(b);
            }
        }

        // X de fechar no canto do painel
        add(new FichaWidgets.BotaoX(px + pw - 20, py + 6, this::onClose));

        // botões no pé do painel
        int by = py + ph - ALTURA_BOTAO - 10;
        int metade = (iw - 8) / 2;
        if (cap == FichaRp.CAP_RESUMO) {
            if (dono) {
                botao(ix, by, iw, "Confirmar personagem", true, this::confirmar);
            } else {
                botao(ix, by, iw, "Fechar", false, this::onClose);
            }
        } else {
            int bx = ix;
            if (cap > 0) {
                botao(bx, by, metade, "Voltar", false, () -> irPara(cap - 1));
                bx += metade + 8;
            }
            if (dono) {
                botao(bx, by, cap > 0 ? metade : iw, "Continuar", true, () -> irPara(cap + 1));
            } else {
                botao(bx, by, iw - (bx - ix), "Fechar", false, this::onClose);
            }
        }
    }

    private void botao(int x, int y, int w, String texto, boolean destaque, Runnable acao) {
        add(new FichaWidgets.Botao(x, y, w, ALTURA_BOTAO, Component.literal(texto), destaque, acao));
    }

    private <T extends AbstractWidget> void add(T w) {
        addRenderableWidget(w);
        animaveis.put(w, w.getY());
    }

    private int limiteDoCampo(int c) {
        return switch (c) {
            case FichaRp.CAP_NOME -> 32;
            case FichaRp.CAP_IDADE -> 24;
            case FichaRp.CAP_ORIGEM -> 48;
            default -> 48;
        };
    }

    private String valorDoCampo(int c) {
        return switch (c) {
            case FichaRp.CAP_NOME -> ficha.nome;
            case FichaRp.CAP_IDADE -> ficha.idade;
            case FichaRp.CAP_ORIGEM -> ficha.origem;
            default -> ficha.oficio;
        };
    }

    private void setCampo(int c, String s) {
        switch (c) {
            case FichaRp.CAP_NOME -> ficha.nome = s;
            case FichaRp.CAP_IDADE -> ficha.idade = s;
            case FichaRp.CAP_ORIGEM -> ficha.origem = s;
            default -> ficha.oficio = s;
        }
    }

    // ============================================================ navegação
    private void coletar() {
        if (campoAtual != null) {
            setCampo(cap, campoAtual.getValue());
        }
        if (campoLongo != null) {
            if (cap == FichaRp.CAP_HISTORIA) {
                ficha.historia = campoLongo.valor();
            } else {
                ficha.habilidades = campoLongo.valor();
            }
        }
    }

    private void salvar(boolean concluir) {
        ficha.pagina = cap;
        PacketDistributor.sendToServer(new FichaSavePayload(ficha.copia(), concluir));
    }

    private void irPara(int novo) {
        coletar();
        int destino = Mth.clamp(novo, 0, FichaRp.CAP_RESUMO);
        cap = destino;
        ficha.pagina = destino;
        salvar(false);
        tTroca = 0f;
        gesto1 = false;
        gesto2 = false;
        resumoRolagem = 0;
        rebuildWidgets();
    }

    private void confirmar() {
        coletar();
        if (ficha.vazia()) {
            som(SoundEvents.VILLAGER_NO, 0.7f, 0.9f);
            return;
        }
        ficha.concluida = true;
        salvar(true);
        tConfirmado = 0f;
        som(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.15f);
    }

    @Override
    public void onClose() {
        coletar();
        if (dono && tConfirmado < 0f) {
            salvar(false);
            som(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.3f);
        }
        super.onClose();
    }

    private void som(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> ev, float vol, float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ev.value(), vol, pitch);
        }
    }

    private void som(net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ev, vol, pitch);
        }
    }

    // ================================================================ teclado
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && !(getFocused() instanceof FichaWidgets.TextoLivre)) {
            if (cap == FichaRp.CAP_RESUMO) {
                if (dono) {
                    confirmar();
                }
            } else {
                irPara(cap + 1);
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (cap == FichaRp.CAP_RESUMO && resumoMax > 0) {
            resumoRolagem = Mth.clamp(resumoRolagem - dy * 12, 0, resumoMax);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        // pontinhos de progresso (rodapé): pulam de capítulo
        if (dono && botao == 0) {
            int ini = width / 2 - FichaRp.CAPS * 10;
            int y = height - 28;
            if (my >= y - 2 && my <= y + 9 && mx >= ini && mx <= ini + FichaRp.CAPS * 20) {
                int alvo = (int) ((mx - ini) / 20);
                if (alvo >= 0 && alvo < FichaRp.CAPS && alvo != cap) {
                    irPara(alvo);
                    som(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public void tick() {
        if (fecharPedido) {
            fecharPedido = false;
            onClose();
            return;
        }
        Player p = minecraft == null ? null : minecraft.player;
        if (p == null) {
            return;
        }
        int passos = (int) (tTroca * 20f);
        if (passos >= 8 && !gesto1) {
            gesto1 = true;
            p.swing(InteractionHand.MAIN_HAND);
        }
        if (passos >= 22 && !gesto2) {
            gesto2 = true;
            p.swing(InteractionHand.OFF_HAND);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================ render
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        // intencionalmente vazio: o fundo é desenhado no render (galáxia)
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        t += dt;
        tTroca += dt;
        float entrada = suave(Mth.clamp(tTroca / 0.28f, 0f, 1f));

        boolean blurOk = false;
        if (minecraft != null && minecraft.level != null) {
            try {
                renderBlurredBackground(dt);
                blurOk = true;
            } catch (Throwable ignored) {
                // sem blur: cai no céu sólido
            }
        }
        FichaFundo.ceu(g, width, height, t, blurOk);

        cabecalho(g);

        // ---------------- personagem à esquerda
        renderPersonagem(g, entrada);

        // ---------------- painel
        painel(g);

        // ---------------- conteúdo do capítulo
        float off = (1f - entrada) * 8f;
        for (Map.Entry<AbstractWidget, Integer> e : animaveis.entrySet()) {
            e.getKey().setY(Math.round(e.getValue() + off));
        }
        conteudoTexto(g, entrada);
        super.render(g, mx, my, dt);

        // ---------------- rodapé (pontinhos + teclas)
        rodape(g);

        if (tConfirmado >= 0f) {
            tConfirmado += dt;
            float k = Mth.clamp(tConfirmado / 0.65f, 0f, 1f);
            g.fill(0, 0, width, height, FichaFundo.corComAlfa(0xFFD98A, 0.30f * (1f - k)));
            g.drawCenteredString(font, "✦ Ficha registrada ✦", width / 2, height / 2 + 40,
                    FichaFundo.corComAlfa(0xFFFFFF, 1f - k * 0.4f));
            if (tConfirmado > 0.75f) {
                tConfirmado = -1f;
                fecharPedido = true;
            }
        }
    }

    /** cabeçalho corrido: marca, sussurro, fio, capítulo e pergunta (centralizados). */
    private void cabecalho(GuiGraphics g) {
        g.drawString(font, "RP TAG · LORE", 20, 10, TXT_FRACO, false);
        String dir = "Sua história começa aqui";
        g.drawString(font, dir, width - 20 - font.width(dir), 10, TXT_FRACO, false);
        fio(g, 20, width - 20, 22, 0x22FFFFFF);

        String c = "CAPÍTULO " + (cap + 1) + " / " + TITULO[cap];
        g.drawCenteredString(font, c, width / 2, 28, 0xD9C7FF);
        g.drawCenteredString(font, PERGUNTA[cap], width / 2, 44, 0xFFFFFF);
    }

    /** fio que acende no meio e desbota nas pontas. */
    private void fio(GuiGraphics g, int x1, int x2, int y, int argb) {
        int a0 = (argb >>> 24) & 0xFF;
        int n = Math.min(40, Math.max(6, (x2 - x1) / 6));
        for (int i = 0; i < n; i++) {
            float k = (i + 0.5f) / n;
            int al = Math.round(a0 * (float) Math.sin(Math.PI * k));
            if (al <= 2) {
                continue;
            }
            int xa = x1 + (x2 - x1) * i / n;
            int xb = x1 + (x2 - x1) * (i + 1) / n;
            g.fill(xa, y, xb, y + 1, (al << 24) | (argb & 0xFFFFFF));
        }
    }

    private void painel(GuiGraphics g) {
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0x30000000);
        g.fill(px, py, px + pw, py + ph, PAINEL);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + 44, 0x18FFFFFF, 0x00FFFFFF);
        // contorno
        int b = BORDA;
        g.fill(px, py, px + pw, py + 1, b);
        g.fill(px, py + ph - 1, px + pw, py + ph, b);
        g.fill(px, py, px + 1, py + ph, b);
        g.fill(px + pw - 1, py, px + pw, py + ph, b);
        // cantos acesos
        for (int[] c : new int[][]{{px, py}, {px + pw, py}, {px, py + ph}, {px + pw, py + ph}}) {
            FichaWidgets.losango(g, c[0], c[1], 3.5f, ROSA, 0.85f);
        }
        // fio embaixo do título do capítulo
        fio(g, px + 14, px + pw - 14, py + 40, 0x55E07BFF);
    }

    /** dica + conteúdo textual do capítulo (fora dos widgets). */
    private void conteudoTexto(GuiGraphics g, float a) {
        int ix = px + 14;
        int iw = pw - 28;
        int alfa = FichaFundo.corComAlfa(0xFFFFFF, a);

        // dica do capítulo, sempre no alto do painel
        quebrado(g, DICA[cap], ix, py + 14, iw, FichaFundo.corComAlfa(TXT_FRACO, a));

        if (cap == FichaRp.CAP_RESUMO) {
            resumo(g, a);
            return;
        }
        if (cap == FichaRp.CAP_PERSONALIDADE) {
            return;   // as barras se desenham sozinhas
        }

        // linha do campo + apoio
        int topo = py + 44;
        int hCampo = cap == FichaRp.CAP_HISTORIA ? 92 : (cap == FichaRp.CAP_HABILIDADES ? 56 : 18);
        g.fill(ix, topo + 10 + hCampo + 3, ix + iw, topo + 11 + hCampo + 3,
                FichaFundo.corComAlfa(ROSA, a * 0.75f));
        quebrado(g, APOIO[cap], ix, topo + 18 + hCampo, iw, FichaFundo.corComAlfa(TXT_FRACO, a));
        if (cap == FichaRp.CAP_HISTORIA) {
            int n = ficha.historia == null ? 0 : ficha.historia.length();
            String c = n + "/400";
            g.drawString(font, c, ix + iw - font.width(c), topo + 12 + hCampo,
                    FichaFundo.corComAlfa(0x6E5F98, a), false);
        }
    }

    // ================================================================ resumo
    private void resumo(GuiGraphics g, float a) {
        int ix = px + 14;
        int iw = pw - 28;
        int topo = py + 48;
        int limite = py + ph - ALTURA_BOTAO - 22;

        int total = alturaResumo(iw);
        resumoMax = Math.max(0, total - (limite - topo));
        resumoRolagem = Mth.clamp(resumoRolagem, 0, resumoMax);

        g.enableScissor(ix - 2, topo - 2, ix + iw + 2, limite);
        int y = topo - (int) Math.round(resumoRolagem);

        y = linha(g, ix, iw, y, "NOME", ficha.ou(ficha.nome), a, 2);
        y = linha(g, ix, iw, y, "IDADE", ficha.ou(ficha.idade), a, 1);
        y = linha(g, ix, iw, y, "VEIO DE", ficha.ou(ficha.origem), a, 2);
        y = linha(g, ix, iw, y, "OCUPAÇÃO", ficha.ou(ficha.oficio), a, 2);
        y = linha(g, ix, iw, y, "HABILIDADES", ficha.ou(ficha.habilidades), a, 2);
        y = linhaAlma(g, ix, iw, y, a);
        linha(g, ix, iw, y, "HISTÓRIA", ficha.ou(ficha.historia), a, 3);

        g.disableScissor();

        if (resumoMax > 0) {
            int alt = limite - topo;
            float frac = (float) (alt / (alt + resumoMax));
            int hb = Math.max(14, Math.round(alt * frac));
            int yb = topo + Math.round((alt - hb) * (float) (resumoRolagem / resumoMax));
            g.fill(ix + iw + 1, topo, ix + iw + 3, topo + alt, 0x22FFFFFF);
            g.fill(ix + iw, yb, ix + iw + 4, yb + hb, FichaFundo.corComAlfa(ROSA, 0.35f));
            g.fill(ix + iw + 1, yb + 1, ix + iw + 3, yb + hb - 1, FichaFundo.corComAlfa(ROSA, 0.85f));
            g.fillGradient(ix - 2, limite - 12, ix + iw, limite, 0x00140B2E, 0xF0140B2E);
            if (resumoRolagem > 0.5) {
                g.fillGradient(ix - 2, topo - 2, ix + iw, topo + 7, 0xF0140B2E, 0x00140B2E);
            }
        }
    }

    /** uma linha do resumo: rótulo em cima, valor embaixo, fio no fim (referência). */
    private int linha(GuiGraphics g, int x, int w, int y, String rotulo, String valor, float a, int maxLinhas) {
        g.drawString(font, rotulo, x, y, FichaFundo.corComAlfa(TXT_FRACO, a), false);

        String v = valor == null || valor.isBlank() ? "—" : valor;
        List<FormattedCharSequence> partes = font.split(Component.literal(v), w);
        int linhas = Math.min(partes.size(), Math.max(1, maxLinhas));
        boolean truncou = partes.size() > linhas;
        int ly = y + 11;
        for (int i = 0; i < linhas; i++) {
            g.drawString(font, partes.get(i), x, ly, FichaFundo.corComAlfa(TXT, a), false);
            if (truncou && i == linhas - 1) {
                g.drawString(font, "…", x + font.width(partes.get(i)) + 1, ly,
                        FichaFundo.corComAlfa(TXT_FRACO, a), false);
            }
            ly += 10;
        }
        fio(g, x, x + w, ly + 2, 0x33FFFFFF);
        return ly + 8;
    }

    /** o rótulo ALMA + três barrinhas (o mesmo desenho dos sliders, só leitura). */
    private int linhaAlma(GuiGraphics g, int x, int w, int y, float a) {
        g.drawString(font, "ALMA", x, y, FichaFundo.corComAlfa(TXT_FRACO, a), false);
        int[] idx = ficha.personaTopo();
        int ly = y + 12;
        if (idx.length == 0) {
            g.drawString(font, "equilibrado", x, ly, FichaFundo.corComAlfa(TXT, a), false);
            fio(g, x, x + w, ly + 12, 0x33FFFFFF);
            return ly + 18;
        }
        int lb = 56;
        int x1 = x + lb;
        int x2 = x + w - lb;
        int cx = (x1 + x2) / 2;
        for (int i : idx) {
            int v = ficha.persona[i];
            boolean dir = v > 50;
            String esq = FichaRp.EIXOS[i][0];
            String dta = FichaRp.EIXOS[i][1];
            g.drawString(font, esq, x, ly, FichaFundo.corComAlfa(dir ? TXT_FRACO : TXT, a * 0.85f), false);
            g.drawString(font, dta, x + w - font.width(dta), ly,
                    FichaFundo.corComAlfa(dir ? TXT : TXT_FRACO, a * 0.85f), false);
            int by = ly + 8;
            g.fill(x1, by, x2, by + 1, FichaFundo.corComAlfa(0x3A3268, a));
            int pxv = x1 + Math.round((x2 - x1) * v / 100f);
            int ai = Math.min(cx, pxv);
            int af = Math.max(cx, pxv);
            if (af > ai) {
                g.fill(ai, by, af, by + 1, FichaFundo.corComAlfa(ROSA, a * 0.9f));
            }
            for (int k = 0; k <= 4; k++) {
                int mark = x1 + k * (x2 - x1) / 4;
                g.fill(mark, by - 2, mark + 1, by + 3, FichaFundo.corComAlfa(0x4A3F86, a));
            }
            g.fill(pxv - 2, by - 4, pxv + 3, by + 5, FichaFundo.corComAlfa(0xCBBFFF, a));
            ly += 14;
        }
        fio(g, x, x + w, ly + 2, 0x33FFFFFF);
        return ly + 8;
    }

    private int alturaResumo(int iw) {
        int h = 0;
        int[] maxs = {2, 1, 2, 2, 2, 3};
        String[] vals = {ficha.ou(ficha.nome), ficha.ou(ficha.idade), ficha.ou(ficha.origem),
                ficha.ou(ficha.oficio), ficha.ou(ficha.habilidades), ficha.ou(ficha.historia)};
        for (int i = 0; i < vals.length; i++) {
            List<FormattedCharSequence> p = font.split(Component.literal(
                    vals[i] == null || vals[i].isBlank() ? "—" : vals[i]), iw);
            h += 11 + Math.min(p.size(), Math.max(1, maxs[i])) * 10 + 10;
        }
        h += 12 + Math.max(1, ficha.personaTopo().length) * 14 + 8;   // as barras
        return h;
    }

    // ================================================================ rodapé
    private void rodape(GuiGraphics g) {
        int ini = width / 2 - FichaRp.CAPS * 10;
        int y = height - 28;
        for (int i = 0; i < FichaRp.CAPS; i++) {
            int x = ini + i * 20;
            int cor = i == cap ? ROSA : (i < cap ? VIOLETA : 0x3A1A6E);
            g.fill(x, y, x + 6, y + 5, FichaFundo.corComAlfa(cor, i <= cap ? 0.95f : 0.8f));
            if (i == cap) {
                g.fill(x, y - 2, x + 6, y - 1, FichaFundo.corComAlfa(0xFFD6FF, 0.9f));
            }
        }
        fio(g, 20, width - 20, height - 18, 0x22FFFFFF);
        g.drawString(font, "ESC · Sair", 20, height - 12, TXT_FRACO, false);
        String en = cap == FichaRp.CAP_RESUMO ? "ENTER · Confirmar" : "ENTER · Continuar";
        g.drawString(font, en, width - 20 - font.width(en), height - 12, TXT_FRACO, false);
    }

    // ============================================================ personagem
    private void renderPersonagem(GuiGraphics g, float entrada) {
        Player p = minecraft == null ? null : minecraft.player;
        if (p == null) {
            return;
        }
        // proporções da referência: Netuno ~22% do menor lado, boneco no pedestal
        int cx = (int) (px * 0.50f);
        int chao = (int) (height * 0.80f);
        int raio = Math.round(Math.min(width, height) * 0.22f);
        int escala = Math.round(height * 0.135f * (0.74f + 0.26f * entrada));

        FichaFundo.buracoNegro(g, cx, chao - raio * 1.05f, raio, t);
        FichaFundo.discoMagico(g, cx, chao + 4, raio * 0.95f, t);

        float yaw = (float) Math.sin(t * 0.55f) * 0.34f;
        float pitch = (float) Math.sin(t * 0.41f) * 0.10f - 0.06f;
        int meia = Math.round(height * 0.16f);
        InventoryScreen.renderEntityInInventoryFollowsAngle(g, cx - meia,
                chao - Math.round(height * 0.46f), cx + meia, chao,
                escala, 0f, yaw, pitch, p);
    }

    // ================================================================ util
    private void quebrado(GuiGraphics g, String texto, int x, int y, int larg, int cor) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> linhas = font.split(Component.literal(texto), Math.max(20, larg));
        int ly = y;
        for (FormattedCharSequence l : linhas) {
            g.drawString(font, l, x, ly, cor, false);
            ly += 10;
        }
    }

    private static float suave(float k) {
        return k * k * (3f - 2f * k);
    }

    /** abre a tela (chamado pelo pacote do servidor). */
    public static void abrir(FichaOpenPayload p) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new FichaScreen(p));
    }

    @Override
    public void removed() {
        if (campoAtual != null) {
            campoAtual.setFocused(false);
        }
        super.removed();
    }
}
