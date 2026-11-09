package dev.rptag.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import dev.rptag.RPTagMod;

/**
 * (5.25.1) A TELA DE MORTE FORA DE RP — "escurece e sobram os botões".
 *
 * <p>Pedido do dono: <i>"não quero essa animação na morte padrão dos players, faça algo mais legal
 * para se mostrar na morte deles... faça a tela escurecendo quando ele morre, sobrando apenas os
 * botões de respawn, lembrando que nem sempre quando eles morrerem vai ser vida canônica, porque
 * precisa ativar o canônico, só morte fora de RP no caso"</i>.
 *
 * <h2>O que esta tela faz</h2>
 * <ul>
 *   <li>a tela <b>escurece</b> em ~1,2 s e a animação/mundo do fundo <b>desaparece</b> — o vermelho
 *       do vanilla, o "You Died!", o "Score:" e a frase da causa <b>não aparecem</b>;</li>
 *   <li>sobram <b>só os botões</b> (Respawn / Tela de título), centralizados e por cima do preto;</li>
 *   <li>embaixo, uma <b>poeira subindo</b> e um <b>brilho vermelho muito fraco</b> — é o corpo no
 *       chão — e uma vinheta que fecha devagar. Nada de texto piscando na cara de quem morreu.
 * </li>
 * </ul>
 *
 * <h2>Só na morte FORA de RP</h2>
 * <p>Quem decide é o servidor: ele manda {@link dev.rptag.MorteRpPayload} com "isto é vida
 * canônica?" no instante da morte. Se for canônica, esta tela <b>não entra</b> — o ritual da vida
 * perdida (véu, trovão, aviso na tela) continua como sempre foi.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class MorteTelaRp extends DeathScreen {

    /** true = a próxima morte é de VIDA CANÔNICA (o servidor avisou) — aí não mexemos na tela. */
    private static volatile boolean canon = false;
    /** true = já recebemos o recado do servidor sobre esta morte. */
    private static volatile boolean avisado = false;
    /** (5.25.2) true = esta morte foi o SOL (o servidor sabe: o fogo do vampiro é dele). */
    private static volatile boolean peloSol = false;

    private final long inicio = System.currentTimeMillis();

    private MorteTelaRp() {
        // hardcore de verdade: no hardcore o vanilla NAO cria o botao de Respawn, e nos
        // seguimos a mesma regra (o resto do mundo nao muda)
        super(Component.empty(), Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getLevelData().isHardcore());
    }

    // ---------------------------------------------------------------- a ponte
    /**
     * chamado pelo pacote do servidor (no instante da morte do jogador local).
     *
     * @param canonVida a morte é de vida canônica? (se sim, esta tela nem entra)
     * @param foiOSol   (5.25.2) a morte foi o SOL? — pedido do dono: <i>"quando tiver morto para o
     *                  sol tem que ser um aviso, que morreu pegando fogo por causa do sol"</i>
     */
    public static void definirMorte(boolean canonVida, boolean foiOSol) {
        canon = canonVida;
        peloSol = foiOSol;
        avisado = true;
        // corrida de rede: se a tela do jogo ja abriu antes do recado chegar, troca agora
        Minecraft mc = Minecraft.getInstance();
        if (!canonVida && mc.screen != null && mc.screen.getClass() == DeathScreen.class) {
            mc.setScreen(new MorteTelaRp());
        }
    }

    /**
     * Troca a tela padrão de morte pela nossa — mas SÓ quando o servidor disse que esta morte é
     * fora de RP. Sem o recado (servidor antigo, por exemplo) a tela do jogo fica como está.
     */
    @SubscribeEvent
    public static void aoAbrirTela(ScreenEvent.Opening event) {
        if (canon || !avisado) {
            return;
        }
        var nova = event.getNewScreen();
        if (nova != null && nova.getClass() == DeathScreen.class) {
            event.setNewScreen(new MorteTelaRp());
        }
    }

    /** o jogador renasceu (ou saiu): o recado vale só para UMA morte. */
    public static void esquecer() {
        avisado = false;
        canon = false;
        peloSol = false;
    }

    // ---------------------------------------------------------------- montagem
    @Override
    protected void init() {
        super.init();   // o vanilla cria os botões (Respawn / Tela de título) do jeito certo
        // e a gente só reposiciona: centralizados, um embaixo do outro
        int cx = this.width / 2 - 100;
        int y0 = this.height / 2 - 14;
        int i = 0;
        for (var filho : this.children()) {
            if (filho instanceof Button botao) {
                botao.setX(cx);
                botao.setY(y0 + i * 26);
                i++;
            }
        }
    }

    // ---------------------------------------------------------------- desenho
    @Override
    public void render(GuiGraphics g, int mx, int my, float parcial) {
        float t = (System.currentTimeMillis() - inicio) / 1000F;
        int w = this.width;
        int h = this.height;

        // 1) o mundo escurece e some (o que sobra é preto)
        float escuro = Math.min(1F, t / 1.2F);
        g.fill(0, 0, w, h, ((int) (255 * escuro) << 24));

        try {
            desenharAtmosfera(g, t, w, h, escuro);
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: atmosfera da tela de morte falhou", erro);
        }

        // 2) SÓ os botões por cima (nenhum texto do vanilla é desenhado)
        for (var r : this.renderables) {
            r.render(g, mx, my, parcial);
        }

        // 3) o que dizer embaixo dos botões — o texto entra devagar, sem piscar na cara
        try {
            desenharTexto(g, t, w, h);
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: texto da tela de morte falhou", erro);
        }
    }

    /**
     * (5.25.2) O TEXTO DA TELA. Pedido do dono: <i>"quando tiver morto para o sol tem que ser um
     * aviso, que morreu pegando fogo por causa do sol, algo assim melhore"</i>.
     *
     * <p>Morreu pegando fogo no sol → o aviso é esse, em dourado e com o nome do sol maior; e o
     * lembrete de vida canônica fica embaixo, menor, porque continua sendo verdade. Qualquer outra
     * morte fora de RP → só o lembrete de sempre, em cinza.
     */
    private void desenharTexto(GuiGraphics g, float t, int w, int h) {
        if (peloSol) {
            // título: O SOL TE ALCANÇOU (entra 1 s depois do escuro)
            if (t > 1.0F) {
                int alfa = (int) (Math.min(1F, (t - 1.0F) / 0.6F) * 255F) << 24;
                Component titulo = Component.literal("\u2600  O SOL TE ALCAN\u00c7OU  \u2600")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                g.pose().pushPose();
                g.pose().translate(w / 2F, h / 2F - 64F, 0F);
                g.pose().scale(2.0F, 2.0F, 1F);
                int larg = this.font.width(titulo);
                g.drawString(this.font, titulo, -larg / 2, 0, alfa, true);
                g.pose().popPose();
            }
            // a causa, dita com todas as letras
            if (t > 1.35F) {
                int alfa = (int) (Math.min(1F, (t - 1.35F) / 0.5F) * 230F) << 24;
                Component causa = Component.literal(
                                "Voc\u00ea morreu pegando fogo \u2014 a luz do dia queimou a sua pele.")
                        .withStyle(ChatFormatting.YELLOW);
                int larg = this.font.width(causa);
                g.drawString(this.font, causa, w / 2 - larg / 2, h / 2 + 26, alfa, false);
                Component dica = Component.literal(
                                "A sombra, um teto ou o amuleto do eclipse teriam te salvado.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
                int larg2 = this.font.width(dica);
                g.drawString(this.font, dica, w / 2 - larg2 / 2, h / 2 + 40, alfa, false);
            }
            if (t > 1.9F) {
                int alfa = (int) (Math.min(1F, (t - 1.9F) / 0.8F) * 150F) << 24;
                Component recado = Component.literal(
                                "morte fora de RP \u2014 sua vida can\u00f4nica est\u00e1 intacta")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
                int larg = this.font.width(recado);
                g.drawString(this.font, recado, w / 2 - larg / 2, h / 2 + 62, alfa, false);
            }
            return;
        }
        // morte comum fora de RP: o lembrete discreto de sempre
        if (t > 1.6F) {
            int alfa = (int) (Math.min(1F, (t - 1.6F) / 0.8F) * 150F) << 24;
            Component recado = Component.literal(
                            "morte fora de RP \u2014 sua vida can\u00f4nica est\u00e1 intacta")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            int larg = this.font.width(recado);
            g.drawString(this.font, recado, w / 2 - larg / 2, h / 2 + 44, alfa, false);
        }
    }

    /** poeira subindo do corpo + brilho vermelho baixíssimo + vinheta que fecha. */
    private void desenharAtmosfera(GuiGraphics g, float t, int w, int h, float escuro) {
        if (escuro < 0.25F) {
            return;
        }
        // o brilho do "corpo" (embaixo, no meio): vermelho escuro, bem discreto —
        // retangulos concentricos que se alargam, do mais escuro pro mais claro
        float raio = Math.min(w, h) * 0.42F;
        int passos = 16;
        for (int i = passos; i > 0; i--) {
            float u = i / (float) passos;
            int alfa = (int) ((1F - u) * 0.055F * escuro * 255F);
            if (alfa <= 0) {
                continue;
            }
            int r = (int) (raio * u);
            int base = h - (int) (h * 0.18F);
            g.fill(w / 2 - r, base - r / 2, w / 2 + r, base + r / 2,
                    (alfa << 24) | (peloSol ? 0x6B2A08 : 0x3A0A0E));
        }
        // poeira/cinza subindo devagar (na morte pelo SOL vira brasa: laranja, mais rápida,
        // tremeluzindo — é o que o dono pediu: "algo assim melhore")
        int cor = peloSol ? 0xE8873A : 0x8A8A92;
        int n = peloSol ? 60 : 46;
        for (int i = 0; i < n; i++) {
            float semente = (float) (Math.sin(i * 12.9898) * 43758.5453D);
            semente = semente - (float) Math.floor(semente);
            float x = (semente * w + (float) Math.sin(t * 0.5F + i) * 18F);
            float vel = (peloSol ? 26F : 12F) + semente * (peloSol ? 44F : 26F);
            float y = h - ((t * vel + semente * h) % (h + 120F));
            float brilho = peloSol ? (0.7F + 0.3F * (float) Math.sin(t * 7F + i)) : 1F;
            int alfa = (int) (escuro * (26F + 40F * semente) * brilho);
            int tam = semente > 0.82F ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + tam, (int) y + tam, (alfa << 24) | cor);
        }
        // vinheta fechando
        int v = (int) (Math.min(1F, t / 1.4F) * 150F);
        int banda = Math.max(24, h / 6);
        g.fillGradient(0, 0, w, banda, (v << 24), 0x00000000);
        g.fillGradient(0, h - banda, w, h, 0x00000000, (v << 24));
    }

    // ---------------------------------------------------------------- comportamento
    /** a morte fora de RP não é pausa (igual o vanilla) — o servidor segue rodando. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** ao renascer, o recado do servidor é descartado (a próxima morte pede outro). */
    @Override
    public void removed() {
        super.removed();
        esquecer();
    }

    /** só para o log: esta tela entrou? */
    public static boolean ativa() {
        return Minecraft.getInstance().screen instanceof MorteTelaRp;
    }
}
