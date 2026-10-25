package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rptag.FichaRp;
import dev.rptag.FichaSavePayload;
import dev.rptag.FichaOpenPayload;
import net.minecraft.client.Minecraft;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * (4.8.0) O TABLET — a ficha do personagem, capítulo por capítulo.
 *
 * <p>Uma pergunta por tela, um pedaço de cada vez: nome, idade, de onde veio,
 * ocupação, habilidades, história e personalidade (6 barras). No fim, o resumo
 * com o botão "Confirmar Personagem" — e o nome vira de verdade o nome do
 * personagem (nametag/chat/TAB), porque a ficha alimenta a {@code Persona}.
 *
 * <p>Visual: galáxia de fundo, um buraco negro de Netuno atrás do personagem,
 * botões de vidro e o boneco do próprio jogador girando no pedestal.
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
            "Está tudo correto?"
    };

    private static final String[] DICA = {
            "O nome que os outros vão ler no seu nametag, no chat e na TAB.",
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

    private static final int ALTURA_BOTAO = 20;
    private static final int ALTURA_CAMPO = 20;

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
    private double resumoRolagem;      // 0..resumoMax — o resumo rola em vez de cortar
    private double resumoMax;

    // layout (recalculado no init)
    private int px, py, pw, ph, colX, colW;

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

    // ================================================================ ciclo
    @Override
    protected void init() {
        animaveis.clear();
        campoAtual = null;
        campoLongo = null;
        FichaFundo.preparar();

        // (4.8.3) no resumo o painel abre mais: cabe mais texto por linha
        pw = Math.min(width - 20, cap == FichaRp.CAP_RESUMO ? 540 : 470);
        ph = Math.min(height - 34, 276);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        colX = px + (int) (pw * 0.46f);   // (4.8.4) um pouco mais à esquerda: sobra largura para o texto
        colW = (int) (pw * 0.44f);

        if (cap <= FichaRp.CAP_OFICIO) {
            campoAtual = FichaWidgets.campo(font, colX, py + 86, colW, limiteDoCampo(cap),
                    valorDoCampo(cap), s -> setCampo(cap, s));
            campoAtual.setEditable(dono);
            add(campoAtual);
        } else if (cap == FichaRp.CAP_HABILIDADES || cap == FichaRp.CAP_HISTORIA) {
            boolean hist = cap == FichaRp.CAP_HISTORIA;
            campoLongo = new FichaWidgets.TextoLivre(colX, py + 86, colW, hist ? 92 : 52,
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
            int passo = Math.min(26, Math.max(19, (ph - 122) / 6));
            int y0 = py + 70;
            for (int i = 0; i < 6; i++) {
                FichaWidgets.Barra b = new FichaWidgets.Barra(colX, y0 + i * passo, colW, 16, i,
                        ficha.persona, () -> {
                        });
                b.active = dono;
                add(b);
            }
        }

        // (4.8.2) X de fechar, sempre no canto do painel
        add(new FichaWidgets.BotaoX(px + pw - 22, py + 6, this::onClose));

        int by = py + ph - 40;
        if (cap == FichaRp.CAP_RESUMO) {
            if (dono) {
                botao(colX, by, colW, "Confirmar Personagem", true, this::confirmar);
            }
        } else {
            if (cap > 0) {
                botao(colX, by, 84, "Voltar", false, () -> irPara(cap - 1));
            }
            if (dono) {
                botao(colX + colW - 96, by, 96, "Continuar", true, () -> irPara(cap + 1));
            }
        }
        if (!dono) {
            botao(colX, by, colW, "Fechar", false, this::onClose);
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
    /** lê o que está na tela para a ficha (antes de trocar de capítulo). */
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
        resumoRolagem = 0;   // cada visita ao resumo começa do topo
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
        // clicar num quadradinho de progresso pula para o capítulo
        if (dono && botao == 0 && my >= py + ph - 20 && my <= py + ph - 4) {
            int n = FichaRp.CAPS;
            int larg = n * 9;
            int ini = px + pw / 2 - larg / 2;
            if (mx >= ini && mx <= ini + larg) {
                int alvo = (int) ((mx - ini) / 9);
                if (alvo >= 0 && alvo < n && alvo != cap) {
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
        // o fundo é desenhado no render (galáxia + buraco negro)
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        t += dt;
        tTroca += dt;
        float entrada = suave(Mth.clamp(tTroca / 0.28f, 0f, 1f));

        // (4.8.2) FUNDO IGUAL AO BALAO: blur do mundo + veu + estrelas
        boolean blurOk = false;
        if (minecraft != null && minecraft.level != null) {
            try {
                renderBlurredBackground(dt);
                blurOk = true;
            } catch (Throwable ignored) {
                // sem blur (menu/ shader exotico): cai no ceu solido
            }
        }
        FichaFundo.ceu(g, width, height, t, blurOk);

        // ---------------- painel
        // (4.8.2) painel mais fechado: antes era 0x8C (55%) e o HUD/mundo
        // apareciam por cima do texto. Agora 0xE8 (91%) com leve degradê.
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0x30000000);   // sombra
        g.fill(px, py, px + pw, py + ph, 0xE80B0718);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0x14000000, 0x3A000000);
        g.fill(px, py, px + pw, py + 1, FichaFundo.corComAlfa(0x8FD8FF, 0.30f));
        g.fill(px, py + ph - 1, px + pw, py + ph, FichaFundo.corComAlfa(0x8FD8FF, 0.22f));
        g.fill(px, py, px + 1, py + ph, FichaFundo.corComAlfa(0x8FD8FF, 0.22f));
        g.fill(px + pw - 1, py, px + pw, py + ph, FichaFundo.corComAlfa(0x8FD8FF, 0.22f));
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + 60, 0x14FFFFFF, 0x00FFFFFF);
        // cantos
        for (int[] c : new int[][]{{px, py}, {px + pw, py}, {px, py + ph}, {px + pw, py + ph}}) {
            FichaWidgets.losango(g, c[0], c[1], 4f, 0xFFD98A, 0.7f);
        }

        // ---------------- cabeçalho
        String c1 = "CAPÍTULO " + (cap + 1);
        String c2 = "· " + TITULO[cap];
        g.drawString(font, c1, px + 14, py + 11, 0xFFD98A, false);
        g.drawString(font, c2, px + 14 + font.width(c1) + 5, py + 11, 0xF4EEFF, false);
        String sussurro = (cap == FichaRp.CAP_RESUMO && resumoMax > 0)
                ? "role a roda para ver tudo" : "Sua história começa aqui";
        g.drawString(font, sussurro, px + pw - 30 - font.width(sussurro), py + 11, 0x7E6FA8, false);
        regua(g, px + 14, px + pw - 14, py + 24, 0x8FD8FF, 0.22f);

        // ---------------- pergunta
        float aTxt = entrada;
        String perg = PERGUNTA[cap];
        g.drawCenteredString(font, perg, px + pw / 2, py + 33, FichaFundo.corComAlfa(0xFFFFFF, 0.55f + 0.45f * aTxt));

        // ---------------- personagem
        renderPersonagem(g, entrada);

        // ---------------- conteúdo (desliza com a transição)
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0f, (1f - entrada) * 10f, 0f);
        renderConteudo(g, entrada);
        pose.popPose();

        // véu da transição (não cobre os botões)
        if (entrada < 1f) {
            g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, FichaFundo.corComAlfa(0x0A0616, 0.55f * (1f - entrada)));
        }

        // ---------------- widgets animados
        float off = (1f - entrada) * 8f;
        for (Map.Entry<AbstractWidget, Integer> e : animaveis.entrySet()) {
            e.getKey().setY(Math.round(e.getValue() + off));
        }
        super.render(g, mx, my, dt);

        // ---------------- progresso + rodapé
        renderProgresso(g);
        int rodape = py + ph + 9;
        tecla(g, px + 12, rodape, "ESC", "sair");
        String ent = cap == FichaRp.CAP_RESUMO ? "confirmar" : "continuar";
        int wEnt = font.width(ent) + 6 + font.width("ENTER") + 8;
        tecla(g, px + pw - 12 - wEnt, rodape, "ENTER", ent);

        // ---------------- confirmação (brilho dourado e fecha)
        if (tConfirmado >= 0f) {
            tConfirmado += dt;
            float k = Mth.clamp(tConfirmado / 0.65f, 0f, 1f);
            g.fill(0, 0, width, height, FichaFundo.corComAlfa(0xFFD98A, 0.35f * (1f - k)));
            g.drawCenteredString(font, "✦ Ficha registrada ✦", width / 2, height / 2 + 40,
                    FichaFundo.corComAlfa(0xFFFFFF, 1f - k * 0.4f));
            if (tConfirmado > 0.75f) {
                tConfirmado = -1f;
                fecharPedido = true;
            }
        }
    }

    /** (4.8.3) tecla desenhada como um "keycap" e o rótulo do lado. */
    private int tecla(GuiGraphics g, int x, int y, String tecla, String rotulo) {
        int w = font.width(tecla) + 8;
        g.fill(x, y - 2, x + w, y + 9, 0x24FFFFFF);
        g.fill(x, y - 2, x + w, y - 1, 0x3AFFFFFF);
        g.fill(x, y + 9, x + w, y + 10, 0x1AFFFFFF);
        g.drawString(font, tecla, x + 4, y, 0xD8CFF0, false);
        g.drawString(font, rotulo, x + w + 4, y, 0x8A7CB0, false);
        return x + w + 4 + font.width(rotulo);
    }

    private void renderProgresso(GuiGraphics g) {
        int n = FichaRp.CAPS;
        int larg = n * 9;
        int ini = px + pw / 2 - larg / 2;
        int y = py + ph - 15;
        for (int i = 0; i < n; i++) {
            int x = ini + i * 9;
            boolean feito = i < cap || (i == cap);
            if (i == cap) {
                FichaWidgets.losango(g, x + 2.5f, y + 2.5f, 3.5f, 0xFFD98A, 0.95f);
                FichaWidgets.losango(g, x + 2.5f, y + 2.5f, 1.5f, 0xFFF6DE, 0.95f);
            } else {
                int cor = feito ? 0x8FD8FF : 0x4A6FA8;
                g.fill(x, y, x + 5, y + 5, FichaFundo.corComAlfa(cor, feito ? 0.75f : 0.40f));
            }
        }
    }

    private void renderPersonagem(GuiGraphics g, float entrada) {
        Player p = minecraft == null ? null : minecraft.player;
        if (p == null) {
            return;
        }
        int base = py + ph - 46;
        int cxm = px + (int) (pw * 0.27f);
        // (4.8.3) o boneco acompanha o tamanho do painel (em tela pequena, menor)
        float k = Mth.clamp(ph / 276f, 0.60f, 1f);
        int escala = Math.round(50f * k * (0.70f + 0.30f * entrada));
        int raio = Math.round(49f * k);

        FichaFundo.buracoNegro(g, cxm, base - Math.round(76 * k), raio, t);
        FichaFundo.discoMagico(g, cxm, base - 2, Math.round(46 * k), t);

        float yaw = (float) Math.sin(t * 0.55f) * 0.34f;
        float pitch = (float) Math.sin(t * 0.41f) * 0.10f - 0.06f;
        int meia = Math.round(74 * k);
        InventoryScreen.renderEntityInInventoryFollowsAngle(g, cxm - meia, base - Math.round(132 * k),
                cxm + meia, base, escala, 0f, yaw, pitch, p);
    }

    private void renderConteudo(GuiGraphics g, float a) {
        int x = colX;
        int y = py + 52;   // a dica fica acima do campo (2 linhas cabem)
        int alfa = FichaFundo.corComAlfa(0xFFFFFF, a);
        if (cap == FichaRp.CAP_PERSONALIDADE) {
            desenharQuebrado(g, DICA[cap], x, py + 52, colW, FichaFundo.corComAlfa(0xB9AEDC, a));
            return;
        }
        if (cap == FichaRp.CAP_RESUMO) {
            renderResumo(g, x, py + 48, a); // resumo começa mais alto: ganha 1 linha
            return;
        }
        desenharQuebrado(g, DICA[cap], x, y, colW, FichaFundo.corComAlfa(0xB9AEDC, a));
        // apoio embaixo do campo
        int hCampo = (cap == FichaRp.CAP_HISTORIA) ? 92 : (cap == FichaRp.CAP_HABILIDADES ? 52 : ALTURA_CAMPO);
        desenharQuebrado(g, APOIO[cap], x, py + 100 + hCampo, colW, FichaFundo.corComAlfa(0x7E6FA8, a));
        if (cap == FichaRp.CAP_HISTORIA) {
            int n = ficha.historia == null ? 0 : ficha.historia.length();
            String c = n + "/400";
            g.drawString(font, c, x + colW - font.width(c), py + 94 + hCampo + 2,
                    FichaFundo.corComAlfa(0x6E5F98, a), false);
        }
        g.drawString(font, "▚ " + alfaTexto(), x, py + 76, FichaFundo.corComAlfa(0x8FD8FF, a * 0.6f), false);
    }

    private String alfaTexto() {
        return switch (cap) {
            case 0 -> "seu nome";
            case 1 -> "sua idade";
            case 2 -> "sua origem";
            case 3 -> "seu ofício";
            case 4 -> "suas habilidades";
            default -> "sua história";
        };
    }

    /**
     * (4.8.2) O RESUMO. Antes era uma linha por campo e a "Alma" (6 eixos) saía
     * voando pra fora do painel. Agora TODO valor quebra dentro da coluna e o
     * que não cabe vira "…" — nada passa do painel.
     */
    // ============ 4.8.4: O RESUMO EM CARTÕES (linguagem nova) ============
    // Nome grande no topo, linha de apoio com idade/origem e o resto em
    // cartões de vidro com o título em dourado. Quem não couber, rola.

    private void renderResumo(GuiGraphics g, int x, int y, float a) {
        int larg = px + pw - 14 - x;
        int limite = py + ph - 50;

        // ---- nome grande (2x quando cabe) + idade/origem
        String nome = ficha.ou(ficha.nome);
        int esc = font.width(nome) * 2 <= larg ? 2 : 1;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(esc, esc, 1f);
        g.drawString(font, nome, 0, 0, FichaFundo.corComAlfa(0xFFF3DA, a), false);
        pose.popPose();

        int yApoio = y + 10 * esc + 2;
        String apoio = umaLinha(juntar(ficha.ou(ficha.idade), ficha.ou(ficha.origem)), larg - 4);
        g.drawString(font, apoio, x, yApoio, FichaFundo.corComAlfa(0xB9AEDC, a * 0.95f), false);
        regua(g, x, x + larg - 8, yApoio + 12, 0xFFD98A, a * 0.30f);

        // ---- cartões (rolam se não couberem)
        int topo = yApoio + 18;
        int total = alturaCartoes(larg);
        resumoMax = Math.max(0, total - (limite - topo));
        resumoRolagem = Mth.clamp(resumoRolagem, 0, resumoMax);

        g.enableScissor(x - 2, topo - 2, x + larg + 2, limite);
        int ly = topo - (int) Math.round(resumoRolagem);
        ly = cartaoTexto(g, x, ly, larg, "OCUPAÇÃO", ficha.ou(ficha.oficio), a);
        ly = cartaoTexto(g, x, ly, larg, "HABILIDADES", ficha.ou(ficha.habilidades), a);
        ly = cartaoAlma(g, x, ly, larg, a);
        cartaoTexto(g, x, ly, larg, "HISTÓRIA", ficha.ou(ficha.historia), a);
        g.disableScissor();

        if (resumoMax > 0) {
            desenhaRolagem(g, x, larg, topo, limite);
        }
    }

    /** junta duas informações numa linha só ("27 anos · Uberlândia…"). */
    private static String juntar(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "—" : b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + "  ·  " + b;
    }

    /** corta uma linha com "…" quando passa da largura. */
    private String umaLinha(String s, int larg) {
        if (s == null) {
            return "—";
        }
        if (font.width(s) <= larg) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (font.width(sb.toString() + c) > larg - 6) {
                break;
            }
            sb.append(c);
        }
        return sb.toString().trim() + "…";
    }

    /** fundo de vidro do cartão com o fio dourado em cima. */
    private void fundoCartao(GuiGraphics g, int x, int y, int larg, int h, float a) {
        g.fill(x, y, x + larg, y + h, FichaFundo.corComAlfa(0xFFFFFF, a * 0.055f));
        g.fill(x, y, x + larg, y + 1, FichaFundo.corComAlfa(0xFFD98A, a * 0.42f));
        g.fill(x, y + h - 1, x + larg, y + h, FichaFundo.corComAlfa(0x8FD8FF, a * 0.10f));
        g.fill(x, y, x + 1, y + h, FichaFundo.corComAlfa(0x8FD8FF, a * 0.10f));
        g.fill(x + larg - 1, y, x + larg, y + h, FichaFundo.corComAlfa(0x8FD8FF, a * 0.10f));
        // brilho no canto de cima
        FichaWidgets.losango(g, x + 4f, y + 4f, 1.8f, 0xFFD98A, a * 0.75f);
    }

    private int cartaoTexto(GuiGraphics g, int x, int y, int larg, String titulo, String valor, float a) {
        int rw = larg - 16;
        List<FormattedCharSequence> linhas = font.split(Component.literal(
                valor == null || valor.isBlank() ? "—" : valor), Math.max(40, rw));
        int h = 19 + Math.max(1, linhas.size()) * 10 + 5;
        fundoCartao(g, x, y, larg, h, a);
        g.drawString(font, titulo, x + 9, y + 7, FichaFundo.corComAlfa(0xFFD98A, a * 0.85f), false);
        int ly = y + 19;
        for (FormattedCharSequence l : linhas) {
            g.drawString(font, l, x + 9, ly, FichaFundo.corComAlfa(0xF6F0FF, a), false);
            ly += 10;
        }
        return y + h + 6;
    }

    /** cartão da ALMA: as três forças em barrinhas. */
    private int cartaoAlma(GuiGraphics g, int x, int y, int larg, float a) {
        int[] idx = ficha.personaTopo();
        int h = 19 + Math.max(1, idx.length) * 13 + 5;
        fundoCartao(g, x, y, larg, h, a);
        g.drawString(font, "ALMA", x + 9, y + 7, FichaFundo.corComAlfa(0xFFD98A, a * 0.85f), false);

        if (idx.length == 0) {
            g.drawString(font, "equilibrado — nenhum lado puxa mais forte", x + 9, y + 19,
                    FichaFundo.corComAlfa(0xF6F0FF, a), false);
            return y + h + 6;
        }
        int lb = 56;
        int x1 = x + 9 + lb;
        int x2 = x + larg - 9 - lb;
        int cx = (x1 + x2) / 2;
        int ly = y + 19;
        for (int i : idx) {
            int v = ficha.persona[i];
            boolean dir = v > 50;
            String esq = dev.rptag.FichaRp.EIXOS[i][0];
            String dta = dev.rptag.FichaRp.EIXOS[i][1];
            int cAtivo = dir ? 0xFFD98A : 0x8FD8FF;
            int cFraco = 0x6E5F98;
            g.drawString(font, esq, x + 9, ly, FichaFundo.corComAlfa(dir ? cFraco : cAtivo, a), false);
            g.drawString(font, dta, x + larg - 9 - font.width(dta), ly,
                    FichaFundo.corComAlfa(dir ? cAtivo : cFraco, a), false);
            int by = ly + 9;
            g.fill(x1, by, x2, by + 1, FichaFundo.corComAlfa(0x4A6FA8, a * 0.85f));
            int pxv = x1 + Math.round((x2 - x1) * v / 100f);
            int ai = Math.min(cx, pxv);
            int af = Math.max(cx, pxv);
            if (af > ai) {
                g.fill(ai, by - 1, af, by + 2, FichaFundo.corComAlfa(cAtivo, a * 0.85f));
            }
            g.fill(cx, by - 2, cx + 1, by + 3, FichaFundo.corComAlfa(0xFFFFFF, a * 0.26f));
            g.fill(pxv - 1, by - 2, pxv + 2, by + 3, FichaFundo.corComAlfa(0xFFFFFF, a * 0.92f));
            ly += 13;
        }
        return y + h + 6;
    }

    /** régua que acende no meio e some nas pontas (nada de linha serrilhada). */
    private void regua(GuiGraphics g, int x1, int x2, int y, int cor, float alpha) {
        int n = Math.min(30, Math.max(6, (x2 - x1) / 5));
        if (n <= 0 || x2 <= x1) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float k = (i + 0.5f) / n;
            float f = (float) Math.sin(Math.PI * k);
            int al = Math.round(alpha * f * 255f);
            if (al <= 2) {
                continue;
            }
            int xa = x1 + (x2 - x1) * i / n;
            int xb = x1 + (x2 - x1) * (i + 1) / n;
            g.fill(xa, y, xb, y + 1, (al << 24) | (cor & 0xFFFFFF));
        }
    }

    /** barra de rolagem discreta + esfumaçado nas bordas. */
    private void desenhaRolagem(GuiGraphics g, int x, int larg, int y, int limite) {
        int alt = limite - y;
        float frac = (float) (alt / (alt + resumoMax));
        int hb = Math.max(14, Math.round(alt * frac));
        int yb = y + Math.round((alt - hb) * (float) (resumoRolagem / resumoMax));
        int bx = x + larg + 3;
        g.fill(bx, y, bx + 2, y + alt, 0x1FFFFFFF);
        g.fill(bx - 1, yb, bx + 3, yb + hb, FichaFundo.corComAlfa(0x8FD8FF, 0.55f));
        g.fill(bx, yb + 1, bx + 2, yb + hb - 1, FichaFundo.corComAlfa(0xBFE6FF, 0.85f));

        g.fillGradient(x - 2, limite - 14, x + larg, limite, 0x000B0718, 0xFF0B0718);
        if (resumoRolagem > 0.5) {
            g.fillGradient(x - 2, y - 2, x + larg, y + 8, 0xFF0B0718, 0x000B0718);
        }
    }

    /** altura dos cartões (para saber se precisa rolar). */
    private int alturaCartoes(int larg) {
        int rw = Math.max(40, larg - 16);
        int h = 0;
        for (String v : new String[]{ficha.ou(ficha.oficio), ficha.ou(ficha.habilidades)}) {
            h += 19 + Math.max(1, font.split(Component.literal(v == null || v.isBlank() ? "—" : v), rw).size()) * 10 + 5 + 6;
        }
        int[] idx = ficha.personaTopo();
        h += 19 + Math.max(1, idx.length) * 13 + 5 + 6;
        String hist = ficha.ou(ficha.historia);
        h += 19 + Math.max(1, font.split(Component.literal(hist == null || hist.isBlank() ? "—" : hist), rw).size()) * 10 + 5;
        return h;
    }

    private void desenharQuebrado(GuiGraphics g, String texto, int x, int y, int larg, int cor) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> linhas = font.split(Component.literal(texto), Math.max(20, larg));
        int ly = y;
        for (FormattedCharSequence linha : linhas) {
            g.drawString(font, linha, x, ly, cor, false);
            ly += 10;
        }
    }

    private static String encurta(String s, int max) {
        if (s == null) {
            return "—";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static float suave(float k) {
        return k * k * (3f - 2f * k);
    }

    /** abre a tela (chamado pelo pacote do servidor). */
    public static void abrir(FichaOpenPayload p) {
        Minecraft.getInstance().setScreen(new FichaScreen(p));
    }

    @Override
    public void removed() {
        // garante que nada fique preso no player (swing/foco dos campos)
        if (campoAtual != null) {
            campoAtual.setFocused(false);
        }
        super.removed();
    }
}
