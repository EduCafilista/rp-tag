package dev.rptag;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados do mod salvos junto do mundo (data/rptag_states.dat):
 *
 * <ul>
 *   <li>estado (em RP ou nao) de cada jogador</li>
 *   <li>persona (personagem) de cada jogador</li>
 *   <li>preferencia de chat local ligado/desligado</li>
 *   <li>cor pessoal de cada jogador (mensagem no chat + fundo do balao)</li>
 *   <li>config de baloes de fala (global e por jogador)</li>
 * </ul>
 */
public final class RPWorldData extends SavedData {

    public static final String DATA_NAME = "rptag_states";

    /** Raio padrao do chat local (blocos). Grito=100, sussurro=5. */
    public static final int LOCAL_RANGE = 40;
    public static final int SHOUT_RANGE = 100;
    public static final int WHISPER_RANGE = 5;

    /** Cor padrao (branco) quando o jogador nao escolheu nenhuma. */
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    private final Map<UUID, Boolean> states = new HashMap<>();
    private final Map<UUID, Persona> personas = new HashMap<>();
    /** (5.2.0) fichas do tablet Neptune (rascunho + confirmada + revisao). */
    private final Map<UUID, FichaRegistro> fichas = new HashMap<>();
    private final Map<UUID, Integer> colors = new HashMap<>();
    private final Map<UUID, Boolean> bubbleOptOut = new HashMap<>();
    /** jogadores PROIBIDOS de usar balao por um admin (ausente = permitido). */
    private final Map<UUID, Boolean> bubbleAllowed = new HashMap<>();
    private final Map<UUID, Boolean> bubbleMode = new HashMap<>();
    /** (3.65.0) typewriter do balao por jogador (padrao LIGADO). */
    private final Map<UUID, Boolean> typewriter = new HashMap<>();
    /** (3.80.0) MODO de perder vidas (todos JÁ têm vidas canônicas; com
     * o modo ligado, quem morrer perde uma). Persiste no mundo. */
    private boolean canonEvent = false;
    /** (3.82.0) VIDAS CANÔNICAS de cada jogador (quem nunca morreu tem
     * VIDAS_INICIAIS = 5, guardado só quem já perdeu/ganhou diferente). */
    private final Map<UUID, Integer> canonLives = new HashMap<>();
    /**
     * (5.12.0) ALTURA em que cada construção do vazio foi levantada (0..7 =
     * pedestais, 8 = portal). Fica salvo no mundo para a estrutura NÃO ser
     * reconstruída em cima dela mesma depois de um restart (o heightmap subiria
     * a cada boot e o pedestal viraria uma torre).
     */
    // (5.13.0) 0..7 = altura dos PEDESTAIS, 8 = PORTAL, 9 = PRAÇA (o nível do
    // chão da chegada) e 10..17 = os 8 SANTUÁRIOS (onde o fragmento nasce).
    private final int[] vazioYs = new int[18];

    /** (5.12.0) FRAGMENTOS DO VAZIO que cada jogador já recolheu (0..8).
     *  Persiste no mundo: quem morre no vazio NÃO perde o que achou. */
    private final Map<UUID, Integer> fragmentos = new HashMap<>();
    /** (5.13.0) se o portal do vazio está aberto. */
    private boolean portalAberto;

    /**
     * (5.13.4) O QUE JÁ FOI CONSTRUÍDO NO VAZIO — gravado no save.
     *
     * <p>Pedido 53 do dono: <i>"toda vez que entro novamente no void, spawna de novo a
     * estrutura... as lanternas ficam quebrando; corrija para só spawnar uma vez"</i>.
     * Antes disso o controle era um {@code Set} estático (memória): cada restart do
     * servidor (ou reload) reconstruía praça, pedestais, altares e portal POR CIMA do que
     * já existia — e a reconstrução devolvia o bloco original no lugar da lanterna que o
     * jogador tinha trocado. Agora a marca é do MUNDO: constrói uma vez e nunca mais.
     */
    private final java.util.Set<String> construcoes = new java.util.HashSet<>();

    /** (5.13.4) já foi construído (chave tipo "praca", "P3", "S7", "portal", "R:12,-4")? */
    public boolean temConstrucao(String chave) {
        return construcoes.contains(chave);
    }

    /**
     * (5.13.4) marca como construído.
     *
     * @return true se marcou AGORA (false = já estava marcado, então não construa de novo)
     */
    public boolean marcarConstrucao(String chave) {
        if (construcoes.add(chave)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** (5.13.4) quantas construções estão marcadas (relatório). */
    public int quantasConstrucoes() {
        return construcoes.size();
    }

    /** (5.13.4) esquece tudo (usado SÓ pelo /vazio construir, que quer refazer na marra). */
    public void esquecerConstrucoes() {
        construcoes.clear();
        setDirty();
    }
    private final Map<UUID, BubbleStyle> styles = new HashMap<>();
    private boolean chatLocal = true;
    private boolean bubblesOn = true;
    /** (5.7.0) ANIMAÇÃO DE SAÍDA dos jogadores (quem fica vê o bonequinho se
     * desfazendo em código). (5.17.1) É PADRÃO e roda sempre: o liga/desliga saiu. */
    private boolean saidaFx = true;

    // ================= (5.17.0) BARRA DO HUD (só o ADM mexe) =================
    private boolean barraVisivel = false;
    private String barraRotulo = "AN\u00c1LISE";
    private int barraValor = 10;
    private int barraCorTexto = 0xE8EDF5;
    private int barraCorBarra = 0xC81E2E;
    private int barraCorFundo = 0x141018;
    // (5.17.0) a barra nasce ACIMA da Axion: o canto inferior direito é a casa dela
    private float barraX = 0.985F, barraY = 0.80F;
    private int barraLargura = 120;

    // ================= (5.17.0) AXION (a gatinha do canto) =================
    /** 0 = escondida (PADRÃO — ela só aparece quando o ADM manda), 1 = dormindo, 2 = acordada. */
    private int axionEstado = 0;
    private float axionEscala = 1.0F;
    /**
     * (5.17.2) VERSÃO DO SAVE — serve pras migrações.
     *
     * <p>O dono: <i>"a axion ainda aparece quando eu entrei a primeira vez no sv, ela nao
     * deveria começar sem ela na minha tela?"</i>. A causa: o mundo da 5.17.0 gravou
     * {@code AxionEstado} lá dentro, e o que está no save vence o padrão do código. Aqui o
     * save antigo (versão &lt; 2) é migrado UMA vez: a Axion volta a nascer escondida e o
     * ADM decide se ela aparece.
     */
    private int dataVersao = 0;
    /** (5.17.2) versão que este jar grava. */
    public static final int DATA_VERSAO = 2;
    /** (5.10.1) comando /title (VANILLA) de volta? Padrão NÃO — o dono pediu
     * "deixa só o /titulo". /rp admin titlevanilla on|off. */
    private boolean comandoTitleVanilla = false;
    /** (5.11.0) QUEM É O DEUS DO VAZIO (/vazio ligar).
     *
     * <p>Antes isso vivia só num {@code HashMap} da memória: quando o servidor
     * reiniciava, o modo vazio desligava sozinho e o dono ficava sem a aura sem
     * entender por quê ("depois de um tempo os tentáculos pararam de aparecer").
     * Agora fica salvo no mundo, junto do resto. */
    private final Set<UUID> vazios = new HashSet<>();

    // ==== (4.5.0) REGRAS EM RP (padrao: tudo LIBERADO) ====
    /** PvP permitido em RP (false = bloqueia; vale se qualquer lado estiver em RP). */
    /** Quebrar bloco permitido em RP. */
    /** Colocar bloco (e balde) permitido em RP. */
    /** (4.5.0) segundos parado ate virar AFK sozinho (0 = desligado). */
    private int afkTimeout = 300;
    /** (4.5.0) recado de vida pra quem estava OFFLINE quando o admin mexeu. */
    private final Map<UUID, String> recadosVida = new HashMap<>();

    public static RPWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RPWorldData::new, RPWorldData::load, null), DATA_NAME);
    }

    // ==== estado RP ====

    /** @return true se o jogador esta em RP (padrao: false = OFF RP). */
    public boolean isInRp(UUID id) {
        return states.getOrDefault(id, false);
    }

    public void setInRp(UUID id, boolean inRp) {
        states.put(id, inRp);
        setDirty();
    }

    // ==== persona ====

    /** @return a persona do jogador ou {@link Persona#EMPTY}. */
    public Persona getPersona(UUID id) {
        return personas.getOrDefault(id, Persona.EMPTY);
    }

    public void setPersona(UUID id, Persona persona) {
        if (persona.isEmpty()) {
            personas.remove(id);
        } else {
            personas.put(id, persona);
        }
        setDirty();
    }

    // ==== ficha do tablet (5.2.0) ====

    /** ficha do jogador (cria uma inicial a partir da persona, se ainda nao houver). */
    public FichaRegistro getFicha(UUID id) {
        FichaRegistro existente = fichas.get(id);
        if (existente != null && (existente.revisao() != 0 || existente.confirmada() != null)) {
            return existente;
        }
        FichaRegistro inicial = FichaRegistro.inicial(getPersona(id));
        if (existente != null && existente.tabletEntregue()) {
            inicial = inicial.comTabletEntregue();
        }
        fichas.put(id, inicial);
        setDirty();
        return inicial;
    }

    /** ficha sem criar nada (consulta de card/comando). */
    public FichaRegistro getFichaDireto(UUID id) {
        return fichas.get(id);
    }

    public void setFicha(UUID id, FichaRegistro ficha) {
        fichas.put(id, ficha);
        setDirty();
    }

    public boolean hasConfirmedFicha(UUID id) {
        FichaRegistro f = fichas.get(id);
        return f != null && f.confirmada() != null;
    }

    // ==== cor pessoal (chat + balao) ====

    public int getColor(UUID id) {
        return colors.getOrDefault(id, DEFAULT_COLOR);
    }

    public void setColor(UUID id, int rgb) {
        colors.put(id, rgb & 0xFFFFFF);
        setDirty();
    }

    public boolean hasCustomColor(UUID id) {
        return colors.containsKey(id);
    }

    public void clearColor(UUID id) {
        colors.remove(id);
        setDirty();
    }

    // ==== baloes de fala ====

    /** Baloes ligados globalmente (toggle de admin). */
    public boolean isBubblesOn() {
        return bubblesOn;
    }

    public void setBubblesOn(boolean on) {
        bubblesOn = on;
        setDirty();
    }

    /** (5.7.0) animação de saída dos jogadores (quem FICA vê). */
    /** (5.17.1) a animação de saída no corpo é padrão dos jogadores (sempre ligada). */
    public boolean saidaFx() {
        return saidaFx;
    }

    public boolean comandoTitleVanilla() {
        return comandoTitleVanilla;
    }

    /** (5.11.0) este jogador é o Deus do Vazio? (salvo no mundo) */
    public boolean ehVazio(UUID id) {
        return vazios.contains(id);
    }

    /** (5.11.0) liga/desliga o modo vazio e salva. */
    public void setVazio(UUID id, boolean ligado) {
        if (ligado) {
            vazios.add(id);
        } else {
            vazios.remove(id);
        }
        setDirty();
    }

    /** (5.11.0) todos os vazios salvos (pro boot devolver a aura). */
    public Set<UUID> vaziosIds() {
        return Collections.unmodifiableSet(vazios);
    }

    public void setComandoTitleVanilla(boolean manter) {
        comandoTitleVanilla = manter;
        setDirty();
    }

    // ---------------------------------------------------- (5.17.0) barra do HUD
    public boolean barraVisivel() { return barraVisivel; }
    public void setBarraVisivel(boolean v) { barraVisivel = v; setDirty(); }
    public String barraRotulo() { return barraRotulo; }
    public void setBarraRotulo(String r) { barraRotulo = r == null ? "" : r; setDirty(); }
    public int barraValor() { return barraValor; }
    public void setBarraValor(int v) { barraValor = Math.max(0, Math.min(100, v)); setDirty(); }
    public int barraCorTexto() { return barraCorTexto; }
    public void setBarraCorTexto(int c) { barraCorTexto = c; setDirty(); }
    public int barraCorBarra() { return barraCorBarra; }
    public void setBarraCorBarra(int c) { barraCorBarra = c; setDirty(); }
    public int barraCorFundo() { return barraCorFundo; }
    public void setBarraCorFundo(int c) { barraCorFundo = c; setDirty(); }
    public float barraX() { return barraX; }
    public float barraY() { return barraY; }
    public void setBarraPos(float x, float y) {
        barraX = Math.max(0F, Math.min(1F, x));
        barraY = Math.max(0F, Math.min(1F, y));
        setDirty();
    }
    public int barraLargura() { return barraLargura; }
    public void setBarraLargura(int px) { barraLargura = Math.max(40, Math.min(400, px)); setDirty(); }

    // --------------------------------------------------------- (5.17.0) Axion
    public int axionEstado() { return axionEstado; }
    public void setAxionEstado(int e) { axionEstado = Math.max(0, Math.min(2, e)); setDirty(); }
    public float axionEscala() { return axionEscala; }
    public void setAxionEscala(float e) { axionEscala = Math.max(0.3F, Math.min(3.0F, e)); setDirty(); }

    public void setSaidaFx(boolean on) {
        saidaFx = on;
        setDirty();
    }

    /** O jogador desligou os proprios baloes. */
    /** (3.65.0) typewriter do balao (padrao LIGADO). */
    // ==== (3.80.0) modo de perder vidas ====

    /** @return true se o MODO está ligado (quem morrer perde uma vida). */
    public boolean isCanonEvent() {
        return canonEvent;
    }

    /** Liga/desliga o modo (persiste no mundo). */
    public void setCanonEvent(boolean on) {
        this.canonEvent = on;
        setDirty();
    }

    // ==== (3.82.0) estoque de vidas canônicas ====

    /** vidas canônicas do jogador (quem nunca perdeu tem as 5 iniciais). */
    public int getCanonLives(UUID id) {
        return canonLives.getOrDefault(id, 5);
    }

    /** define o estoque de vidas canônicas (persiste no mundo). */
    public void setCanonLives(UUID id, int vidas) {
        canonLives.put(id, Math.max(0, vidas));
        setDirty();
    }

    /** (5.12.0) altura salva de uma construção do vazio (0 = ainda não feita). */
    public int getVazioY(int chave) {
        return (chave >= 0 && chave < vazioYs.length) ? vazioYs[chave] : 0;
    }

    /** (5.12.0) grava a altura de uma construção do vazio. */
    public void setVazioY(int chave, int y) {
        if (chave >= 0 && chave < vazioYs.length && y > 0) {
            vazioYs[chave] = y;
            setDirty();
        }
    }

    // ---- (5.13.0/5.13.1) O PORTAL NEGRO: onde ele está e se está aberto ----
    // (5.13.1) O portal invocado agora é POR DIMENSÃO: o dono invocou no overworld e o
    // código antigo só aceitava dentro do vazio (respondia "você precisa estar na
    // dimensão do vazio"). Com o mapa abaixo, cada dimensão pode ter o seu portal.

    /** porta invocada de uma dimensão: x, y, z. */
    public record Portal(int x, int y, int z) {
    }

    private final Map<String, Portal> portaisInvocados = new HashMap<>();

    /** o portal invocado desta dimensão (null = não tem; no vazio vale o da praça). */
    public Portal getPortalInvocado(String dimensao) {
        return portaisInvocados.get(dimensao);
    }

    /** grava o portal invocado de uma dimensão. */
    public void setPortalInvocado(String dimensao, int x, int y, int z) {
        portaisInvocados.put(dimensao, new Portal(x, y, z));
        setDirty();
    }

    /** todas as dimensões que têm portal invocado. */
    public Map<String, Portal> getPortaisInvocados() {
        return portaisInvocados;
    }

    /** o portal do vazio está aberto agora? (os 8 fragmentos foram encaixados) */
    public boolean isPortalAberto() {
        return portalAberto;
    }

    public void setPortalAberto(boolean aberto) {
        this.portalAberto = aberto;
        setDirty();
    }

    // ==== (5.13.0) O VAZIO: SANTUÁRIOS, PEDESTAIS E O LUGAR DE VOLTA ====
    // Um int por jogador guarda duas máscaras de 8 bits:
    //   bits 0..7  = quais SANTUÁRIOS ele já esvaziou (o fragmento que ele pegou)
    //   bits 8..15 = quais PEDESTAIS ele já encheu (o fragmento que ele encaixou)
    // Pedido 50: os 8 fragmentos vão nos 8 pedestais em volta da praça e aí o
    // portal negro abre. Guardar no mundo significa que ninguém perde progresso
    // ao deslogar/morrer.

    /** máscara dos santuários já esvaziados (bits 0..7). */
    public int getSantuarios(UUID id) {
        return fragmentos.getOrDefault(id, 0) & 0xFF;
    }

    /** máscara dos pedestais já cheios (bits 8..15). */
    public int getPedestais(UUID id) {
        return (fragmentos.getOrDefault(id, 0) >>> 8) & 0xFF;
    }

    /** quantos pedestais ele já encheu (0..8). */
    public int quantosPedestais(UUID id) {
        return Integer.bitCount(getPedestais(id));
    }

    /** ele já esvaziou o santuário {@code i}? */
    public boolean jaPegouSantuario(UUID id, int i) {
        return (getSantuarios(id) & (1 << i)) != 0;
    }

    /** marca o santuário como esvaziado. */
    public void marcarSantuario(UUID id, int i) {
        int v = fragmentos.getOrDefault(id, 0);
        fragmentos.put(id, v | (1 << i));
        setDirty();
    }

    /** este pedestal já está cheio para ele? */
    public boolean pedestalCheio(UUID id, int i) {
        return (getPedestais(id) & (1 << i)) != 0;
    }

    /**
     * Encaixa um fragmento no pedestal {@code i}.
     *
     * @return true se encaixou agora (false = já estava cheio)
     */
    public boolean encaixarNoPedestal(UUID id, int i) {
        int v = fragmentos.getOrDefault(id, 0);
        int mascara = (v >>> 8) & 0xFF;
        if ((mascara & (1 << i)) != 0) {
            return false;
        }
        fragmentos.put(id, (v & 0xFF) | ((mascara | (1 << i)) << 8));
        setDirty();
        return true;
    }

    /** ele tem os 8 pedestais cheios? */
    public boolean temOsOito(UUID id) {
        return getPedestais(id) == 0xFF;
    }

    /** zera os pedestais (o portal consumiu os fragmentos). */
    public void limparPedestais(UUID id) {
        int v = fragmentos.getOrDefault(id, 0);
        fragmentos.put(id, v & 0xFF);
        setDirty();
    }

    /** quantos fragmentos ele já colocou (atalho usado nas mensagens). */
    public int getFragmentos(UUID id) {
        return quantosPedestais(id);
    }

    // ---- (5.13.0) O LUGAR DE ONDE ELE ENTROU NO VAZIO (persistido) ----
    // Antes (5.12.0) isso vivia num mapa em memória: um restart do servidor
    // fazia o jogador voltar para um lugar aleatório. Agora vai pro save.

    /** de onde o jogador saiu quando foi exilado. */
    public record Volta(String dimensao, double x, double y, double z, float yaw, float pitch) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimensao);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            return tag;
        }

        public static Volta load(CompoundTag tag) {
            return new Volta(tag.getString("Dim"), tag.getDouble("X"), tag.getDouble("Y"),
                    tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        }
    }

    private final Map<UUID, Volta> voltas = new HashMap<>();

    /** guarda o lugar de onde ele foi exilado (o portal devolve ele pra lá). */
    public void setVolta(UUID id, Volta volta) {
        if (volta == null) {
            voltas.remove(id);
        } else {
            voltas.put(id, volta);
        }
        setDirty();
    }

    /** o lugar de onde ele foi exilado (null = não tem). */
    public Volta getVolta(UUID id) {
        return voltas.get(id);
    }

    public boolean isTypewriter(UUID id) {
        return this.typewriter.getOrDefault(id, Boolean.TRUE);
    }

    /** (3.65.0) define o typewriter do jogador. */
    public void setTypewriter(UUID id, boolean on) {
        this.typewriter.put(id, on);
        this.setDirty();
    }

    public boolean isBubbleOptOut(UUID id) {
        return bubbleOptOut.getOrDefault(id, false);
    }

    public void setBubbleOptOut(UUID id, boolean optOut) {
        bubbleOptOut.put(id, optOut);
        setDirty();
    }

    /**
     * MODO BALAO: as falas do jogador viram SOMENTE balao (nao aparecem no
     * chat). Para criancas, ovos e personagens sem voz.
     */
    public boolean isBubbleMode(UUID id) {
        return bubbleMode.getOrDefault(id, false);
    }

    public void setBubbleMode(UUID id, boolean on) {
        bubbleMode.put(id, on);
        setDirty();
    }

    // ==== permissao de balao (ADMIN LIBERA — 3.17.0: so quem o admin liberar usa) ====

    /** @return true se o admin LIBEROU o balao deste jogador (padrao: NINGUEM). */
    public boolean isBubbleAllowed(UUID id) {
        return bubbleAllowed.getOrDefault(id, false);
    }

    public void setBubbleAllowed(UUID id, boolean allowed) {
        if (allowed) {
            bubbleAllowed.put(id, true);
        } else {
            bubbleAllowed.remove(id);
        }
        setDirty();
    }

    public java.util.Set<UUID> bubbleAllowedIds() {
        return bubbleAllowed.keySet();
    }

    // ==== estilo do balao ====

    public BubbleStyle getStyle(UUID id) {
        BubbleStyle style = styles.get(id);
        if (style == null) {
            int color = colors.getOrDefault(id, DEFAULT_COLOR);
            return new BubbleStyle(color, BubbleStyle.DEFAULT_BORDER, BubbleStyle.BORDER_CLASSIC,
                    BubbleStyle.TEXT_AUTO, "", "", "", "", "", BubbleStyle.DEFAULT_STICKER_X,
                    BubbleStyle.DEFAULT_STICKER_Y, 0, BubbleStyle.DEFAULT_DURATION);
        }
        return style; // a cor escolhida na tela manda SEMPRE
    }

    public void setStyle(UUID id, BubbleStyle style) {
        styles.put(id, style);
        colors.put(id, style.colorRGB());
        setDirty();
    }

    // ==== chat local ====

    public boolean isChatLocal() {
        return chatLocal;
    }

    public void setChatLocal(boolean chatLocal) {
        this.chatLocal = chatLocal;
        setDirty();
    }


    /** (4.5.0) UUIDs com vida gravada (pra /rp admin vida sem argumento). */
    public java.util.Set<UUID> canonLivesIds() {
        return java.util.Collections.unmodifiableSet(canonLives.keySet());
    }

    /** (4.5.0) guarda o recado que o jogador (offline) vai ver ao entrar. */
    public void setRecadoVida(UUID id, String texto) {
        if (texto == null || texto.isBlank()) {
            recadosVida.remove(id);
        } else {
            recadosVida.put(id, texto);
        }
        setDirty();
    }

    /** (4.5.0) entrega e apaga (chamado no login). */
    public String consumirRecadoVida(UUID id) {
        String t = recadosVida.remove(id);
        if (t != null) setDirty();
        return t;
    }

    /** (4.5.0) segundos parado ate entrar em AFK; 0 desliga o automatico. */
    public int getAfkTimeout() {
        return afkTimeout;
    }

    public void setAfkTimeout(int segundos) {
        this.afkTimeout = Math.max(0, segundos);
        setDirty();
    }

    // ==== NBT ====

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag states = new ListTag();
        this.states.forEach((id, inRp) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putBoolean("InRp", inRp);
            states.add(e);
        });
        tag.put("States", states);

        ListTag personas = new ListTag();
        this.personas.forEach((id, persona) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.put("Persona", persona.save());
            personas.add(e);
        });
        tag.put("Personas", personas);

        ListTag sheets = new ListTag();
        this.fichas.forEach((id, ficha) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.put("Sheet", ficha.save());
            sheets.add(e);
        });
        tag.put("TabletSheets", sheets);

        ListTag colors = new ListTag();
        this.colors.forEach((id, rgb) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putInt("Rgb", rgb);
            colors.add(e);
        });
        tag.put("Colors", colors);

        ListTag optOuts = new ListTag();
        this.bubbleOptOut.forEach((id, opt) -> {
            if (opt) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                optOuts.add(e);
            }
        });
        tag.put("BubbleOptOut", optOuts);

        ListTag banned = new ListTag();
        this.bubbleAllowed.forEach((id, b) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            banned.add(e);
        });
        tag.put("BubbleAllowed", banned);

        ListTag modes = new ListTag();
        this.bubbleMode.forEach((id, on) -> {
            if (on) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                modes.add(e);
            }
        });
        tag.put("BubbleMode", modes);

        ListTag stylesList = new ListTag();
        this.styles.forEach((id, style) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.put("Style", style.save());
            stylesList.add(e);
        });
        tag.put("BubbleStyles", stylesList);

        ListTag typewriterList = new ListTag();
        this.typewriter.forEach((id, on) -> {
            if (on) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                typewriterList.add(e);
            }
        });
        tag.put("Typewriter", typewriterList);

        // (3.80.0) modo de perder vidas
        tag.putBoolean("CanonEvent", canonEvent);

        // (3.82.0) vidas canônicas por jogador
        ListTag vidasList = new ListTag();
        for (Map.Entry<UUID, Integer> en : canonLives.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", en.getKey());
            e.putInt("Lives", en.getValue());
            vidasList.add(e);
        }
        tag.put("CanonLives", vidasList);

        // (5.12.0) alturas das construções do vazio
        tag.putIntArray("VazioYs", vazioYs);
        tag.putBoolean("VazioPortalAberto", portalAberto);
        ListTag portaisList = new ListTag();
        for (Map.Entry<String, Portal> en : portaisInvocados.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Dim", en.getKey());
            e.putInt("X", en.getValue().x());
            e.putInt("Y", en.getValue().y());
            e.putInt("Z", en.getValue().z());
            portaisList.add(e);
        }
        tag.put("VazioPortais", portaisList);

        // (5.13.4) o que já foi construído no vazio (constrói uma vez, nunca mais)
        ListTag constrList = new ListTag();
        for (String k : construcoes) {
            constrList.add(net.minecraft.nbt.StringTag.valueOf(k));
        }
        tag.put("VazioConstrucoes", constrList);

        // (5.13.0) lugar de onde cada um foi exilado
        ListTag voltasList = new ListTag();
        for (Map.Entry<UUID, Volta> en : voltas.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", en.getKey());
            e.put("Volta", en.getValue().save());
            voltasList.add(e);
        }
        tag.put("VazioVoltas", voltasList);

        // (5.12.0) fragmentos do vazio
        ListTag fragList = new ListTag();
        for (Map.Entry<UUID, Integer> en : fragmentos.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", en.getKey());
            e.putInt("N", en.getValue());
            fragList.add(e);
        }
        tag.put("VazioFragmentos", fragList);

        tag.putBoolean("ChatLocal", chatLocal);
        tag.putBoolean("BubblesOn", bubblesOn);
        tag.putBoolean("SaidaFx", saidaFx);
        // (5.17.0) barra do HUD + Axion (sobrevivem ao restart, como o resto)
        tag.putBoolean("BarraOn", barraVisivel);
        tag.putString("BarraRotulo", barraRotulo);
        tag.putInt("BarraValor", barraValor);
        tag.putInt("BarraCorTexto", barraCorTexto);
        tag.putInt("BarraCorBarra", barraCorBarra);
        tag.putInt("BarraCorFundo", barraCorFundo);
        tag.putFloat("BarraX", barraX);
        tag.putFloat("BarraY", barraY);
        tag.putInt("BarraLargura", barraLargura);
        tag.putInt("AxionEstado", axionEstado);
        tag.putFloat("AxionEscala", axionEscala);
        tag.putInt("DataVersao", dataVersao);   // (5.17.2)
        tag.putBoolean("ComandoTitleVanilla", comandoTitleVanilla); // (5.10.1)
        // (4.9.0) as 3 flags de "regra em RP" sairam junto com o /rp admin regra
        tag.putInt("AfkTimeout", afkTimeout);
        // (4.5.0) recados de vida de quem estava offline
        ListTag recados = new ListTag();
        for (var en : recadosVida.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", en.getKey());
            e.putString("Texto", en.getValue());
            recados.add(e);
        }
        tag.put("RecadosVida", recados);

        // (5.11.0) quem é o Deus do Vazio (sobrevive ao restart)
        ListTag vaziosTag = new ListTag();
        for (UUID id : vazios) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            vaziosTag.add(e);
        }
        tag.put("Vazios", vaziosTag);
        return tag;
    }

    public static RPWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        RPWorldData data = new RPWorldData();

        // (5.11.0) o modo vazio volta junto com o mundo
        ListTag vaziosTag = tag.getList("Vazios", Tag.TAG_COMPOUND);
        for (int i = 0; i < vaziosTag.size(); i++) {
            CompoundTag e = vaziosTag.getCompound(i);
            if (e.hasUUID("Id")) {
                data.vazios.add(e.getUUID("Id"));
            }
        }

        ListTag states = tag.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < states.size(); i++) {
            CompoundTag e = states.getCompound(i);
            data.states.put(e.getUUID("Id"), e.getBoolean("InRp"));
        }

        ListTag personas = tag.getList("Personas", Tag.TAG_COMPOUND);
        for (int i = 0; i < personas.size(); i++) {
            CompoundTag e = personas.getCompound(i);
            data.personas.put(e.getUUID("Id"), Persona.load(e.getCompound("Persona")));
        }

        ListTag sheets = tag.getList("TabletSheets", Tag.TAG_COMPOUND);
        for (int i = 0; i < sheets.size(); i++) {
            CompoundTag e = sheets.getCompound(i);
            // (5.2.0) nunca confiar no save: entrada sem UUID ou malformada e ignorada
            if (!e.hasUUID("Id") || !e.contains("Sheet", Tag.TAG_COMPOUND)) {
                continue;
            }
            try {
                data.fichas.put(e.getUUID("Id"), FichaRegistro.load(e.getCompound("Sheet")));
            } catch (RuntimeException erro) {
                RPTagMod.LOGGER.warn("RP Tag: ficha do tablet descartada (dados invalidos)", erro);
            }
        }

        ListTag colors = tag.getList("Colors", Tag.TAG_COMPOUND);
        for (int i = 0; i < colors.size(); i++) {
            CompoundTag e = colors.getCompound(i);
            data.colors.put(e.getUUID("Id"), e.getInt("Rgb"));
        }

        ListTag optOuts = tag.getList("BubbleOptOut", Tag.TAG_COMPOUND);
        for (int i = 0; i < optOuts.size(); i++) {
            data.bubbleOptOut.put(optOuts.getCompound(i).getUUID("Id"), true);
        }

        ListTag banned = tag.getList("BubbleAllowed", Tag.TAG_COMPOUND);
        for (int i = 0; i < banned.size(); i++) {
            data.bubbleAllowed.put(banned.getCompound(i).getUUID("Id"), true);
        }

        // CORRECAO: estilos e modos de balao agora voltam ao reiniciar o servidor!
        ListTag modes = tag.getList("BubbleMode", Tag.TAG_COMPOUND);
        for (int i = 0; i < modes.size(); i++) {
            data.bubbleMode.put(modes.getCompound(i).getUUID("Id"), true);
        }

        ListTag typewriterList = tag.getList("Typewriter", Tag.TAG_COMPOUND);
        for (int i = 0; i < typewriterList.size(); i++) {
            data.typewriter.put(typewriterList.getCompound(i).getUUID("Id"), true);
        }

        data.canonEvent = tag.getBoolean("CanonEvent");

        ListTag vidasList = tag.getList("CanonLives", Tag.TAG_COMPOUND);
        for (int i = 0; i < vidasList.size(); i++) {
            CompoundTag e = vidasList.getCompound(i);
            data.canonLives.put(e.getUUID("Id"), e.getInt("Lives"));
        }

        int[] ys = tag.getIntArray("VazioYs");
        data.portalAberto = tag.getBoolean("VazioPortalAberto");
        // (5.13.4) o que já foi construído no vazio
        ListTag constrList = tag.getList("VazioConstrucoes", Tag.TAG_STRING);
        for (int i = 0; i < constrList.size(); i++) {
            data.construcoes.add(constrList.getString(i));
        }
        ListTag portaisList = tag.getList("VazioPortais", Tag.TAG_COMPOUND);
        for (int i = 0; i < portaisList.size(); i++) {
            CompoundTag e = portaisList.getCompound(i);
            data.portaisInvocados.put(e.getString("Dim"),
                    new Portal(e.getInt("X"), e.getInt("Y"), e.getInt("Z")));
        }
        for (int i = 0; i < Math.min(ys.length, data.vazioYs.length); i++) {
            data.vazioYs[i] = ys[i];
        }

        ListTag voltasList = tag.getList("VazioVoltas", Tag.TAG_COMPOUND);
        for (int i = 0; i < voltasList.size(); i++) {
            CompoundTag e = voltasList.getCompound(i);
            data.voltas.put(e.getUUID("Id"), Volta.load(e.getCompound("Volta")));
        }

        ListTag fragList = tag.getList("VazioFragmentos", Tag.TAG_COMPOUND);
        for (int i = 0; i < fragList.size(); i++) {
            CompoundTag e = fragList.getCompound(i);
            data.fragmentos.put(e.getUUID("Id"), e.getInt("N"));
        }

        ListTag stylesList = tag.getList("BubbleStyles", Tag.TAG_COMPOUND);
        for (int i = 0; i < stylesList.size(); i++) {
            CompoundTag e = stylesList.getCompound(i);
            data.styles.put(e.getUUID("Id"), BubbleStyle.load(e.getCompound("Style")));
        }

        // (correcao) mesmo cuidado que ja existia pro "BubblesOn": se a tag
        // nunca foi salva (mundo/save antigo), tem que manter o padrao real
        // (ligado) em vez de virar "false" so por ausencia da chave.
        data.chatLocal = !tag.contains("ChatLocal") || tag.getBoolean("ChatLocal");
        data.bubblesOn = !tag.contains("BubblesOn") || tag.getBoolean("BubblesOn");
        // (5.7.0) save antigo nao tem a chave -> padrao LIGADO
        data.saidaFx = !tag.contains("SaidaFx") || tag.getBoolean("SaidaFx");
        data.barraVisivel = tag.contains("BarraOn") && tag.getBoolean("BarraOn");
        if (tag.contains("BarraRotulo")) data.barraRotulo = tag.getString("BarraRotulo");
        if (tag.contains("BarraValor")) data.barraValor = tag.getInt("BarraValor");
        if (tag.contains("BarraCorTexto")) data.barraCorTexto = tag.getInt("BarraCorTexto");
        if (tag.contains("BarraCorBarra")) data.barraCorBarra = tag.getInt("BarraCorBarra");
        if (tag.contains("BarraCorFundo")) data.barraCorFundo = tag.getInt("BarraCorFundo");
        if (tag.contains("BarraX")) data.barraX = tag.getFloat("BarraX");
        if (tag.contains("BarraY")) data.barraY = tag.getFloat("BarraY");
        if (tag.contains("BarraLargura")) data.barraLargura = tag.getInt("BarraLargura");
        if (tag.contains("AxionEstado")) data.axionEstado = tag.getInt("AxionEstado");
        if (tag.contains("AxionEscala")) data.axionEscala = tag.getFloat("AxionEscala");

        // (5.17.2) MIGRAÇÃO: save da 5.17.0 (sem DataVersao) mandava a Axion APARECER na
        // tela de todo mundo que entrava. Agora ela nasce ESCONDIDA e só aparece se o ADM
        // mandar (/rp admin axion acordar). Roda uma vez só: depois a escolha do ADM fica.
        data.dataVersao = tag.getInt("DataVersao");
        if (data.dataVersao < DATA_VERSAO) {
            boolean estavaNaTela = data.axionEstado != 0;
            data.axionEstado = 0;
            data.dataVersao = DATA_VERSAO;
            data.setDirty();   // grava a migração junto (senão o save antigo fica no disco)
            if (estavaNaTela) {
                RPTagMod.LOGGER.info("RP Tag: save antigo -> a Axion volta a nascer FORA da tela "
                        + "(ela só aparece com /rp admin axion acordar)");
            }
        }
        data.comandoTitleVanilla = tag.contains("ComandoTitleVanilla") && tag.getBoolean("ComandoTitleVanilla");

        // (4.5.0) mesmo cuidado: save antigo nao tem as chaves -> padrao LIBERADO
        data.afkTimeout = tag.contains("AfkTimeout") ? tag.getInt("AfkTimeout") : AfkSistema.TIMEOUT_PADRAO_S;
        ListTag recados = tag.getList("RecadosVida", Tag.TAG_COMPOUND);
        for (int i = 0; i < recados.size(); i++) {
            CompoundTag e = recados.getCompound(i);
            data.recadosVida.put(e.getUUID("Id"), e.getString("Texto"));
        }
        return data;
    }
}
