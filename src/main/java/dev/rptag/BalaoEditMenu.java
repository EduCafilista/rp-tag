package dev.rptag;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * EDITOR DO BALAO EM INVENTARIO ({@code /balaoedit} e {@code /balao}).
 *
 * <p>Um bau de 6 fileiras, 100% vanilla na tela do cliente (nada de GUI
 * custom = nada que possa crashar o cliente). Tudo e facil:
 *
 * <ul>
 *   <li>🎨 Cor do CENTRO — clique para trocar (couro tingido mostra a cor!)</li>
 *   <li>🛡 Cor da BORDA — clique troca; Shift+clique remove a borda</li>
 *   <li>📜 Fundo — clique troca entre os 7 estilos</li>
 *   <li>⭐ Emojis — PEGUE o emoji (clique) e SOLTE na caixinha
 *       Antes / Meio / Depois do texto (ou arraste segurando o mouse!)</li>
 *   <li>🔔 Modo balao — liga/desliga · 🧽 Limpar — volta ao padrao</li>
 * </ul>
 *
 * <p>Salva automaticamente ao fechar (e a cada mudanca). Os emojis sao
 * "emprestados": ao fechar, quaisquer copies sumidas no inventario sao
 * recolhidas para nao sujar o jogo do jogador.
 */
public class BalaoEditMenu extends AbstractContainerMenu {

    public static final int SLOT_COUNT = 54;

    // botoes
    private static final int SLOT_INFO = 4;
    private static final int SLOT_HELP = 13;
    private static final int SLOT_COLOR = 19;
    private static final int SLOT_BORDER = 20;
    private static final int SLOT_BG = 21;
    private static final int SLOT_CLEAR = 22;
    private static final int SLOT_MODE = 23;
    // paleta de emojis (fileira 4)
    private static final int PALETTE_START = 27;
    private static final int PALETTE_END = 35;
    // caixinhas dos emojis (fileira 6)
    private static final int[] BEFORE_SLOTS = {45, 46, 47};
    private static final int[] MID_SLOTS = {48, 49};
    private static final int[] AFTER_SLOTS = {50, 51, 52};

    /** Emojis da paleta (todos existem na fonte do Minecraft). */
    private static final String[] EMOJIS = {"✦", "★", "♥", "⚔", "☾", "✿", "♪", "⚡", "✧"};

    /** Paleta do centro: {nome, rgb}. */
    private static final Object[][] CENTRO_COLORS = {
            {"Ciano RP", 0x55FFFF}, {"Branco", 0xFFFFFF}, {"Areia", 0xF4F0E8},
            {"Prata", 0xC8C8C8}, {"Grafite", 0x7A7A7A}, {"Carvao", 0x1A1A22},
            {"Verde Menta", 0x55FF88}, {"Verde", 0x55FF55}, {"Dourado", 0xFFD700},
            {"Laranja", 0xFFAA00}, {"Rosa", 0xFF7BAC}, {"Vermelho", 0xFF5555},
            {"Lilas", 0xC77DFF}, {"Roxo", 0x9B59FF}, {"Azul", 0x5555FF},
            {"Azul Ceu", 0x7AD9FF}};
    /** Paleta da borda: {nome, rgb}. */
    private static final Object[][] BORDER_COLORS = {
            {"Tinta (padrao)", 0x111118}, {"Branco", 0xFFFFFF}, {"Ciano", 0x55FFFF},
            {"Verde Menta", 0x55FF88}, {"Dourado", 0xFFD700}, {"Rosa", 0xFF7BAC},
            {"Lilas", 0xC77DFF}, {"Azul", 0x5555FF}, {"Laranja", 0xFFAA00},
            {"Vermelho", 0xFF5555}, {"Grafite", 0x7A7A7A}};
    private static final String[] BG_NAMES = {"Translucido", "Escuro", "Claro", "Gradiente", "Papel", "Noite", "Madeira"};

    /** Marcador dos itens de emoji "emprestados". */
    private static final String EMOJI_MARK = "RPTagEmoji";

    private final SimpleContainer items = new SimpleContainer(SLOT_COUNT);
    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;

    /**
     * Construtor unico (servidor e cliente) — a estrutura de slots tem que ser
     * identica dos dois lados; os ITENS so o servidor preenche (e sincroniza).
     */
    public BalaoEditMenu(int id, Inventory playerInv) {
        super(RPTagMod.BALAO_EDIT_MENU.get(), id);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                if (isPalette(index)) {
                    this.addSlot(new Slot(this.items, index, x, y)); // pega emoji
                } else if (isTarget(index)) {
                    this.addSlot(new Slot(this.items, index, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return isEmojiItem(stack); // so emoji entra aqui
                        }
                    });
                } else {
                    this.addSlot(new Slot(this.items, index, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }

                        @Override
                        public boolean mayPickup(Player player) {
                            return false;
                        }
                    });
                }
            }
        }
        if (!playerInv.player.level().isClientSide && playerInv.player instanceof ServerPlayer sp) {
            RPWorldData data = RPWorldData.get(sp.server);
            this.style = data.getStyle(sp.getUUID());
            this.bubbleMode = data.isBubbleMode(sp.getUUID());
            refillPalette();
            refreshItems();
        }
    }

    // =====================================================================
    //  Abertura
    // =====================================================================

    /** Abre o editor para o jogador (server-side). */
    public static void open(ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("✏ Editar Balão");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BalaoEditMenu(id, inv);
            }
        });
    }

    // =====================================================================
    //  Cliques
    // =====================================================================

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player.level().isClientSide) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        // nada de jogar emojis fora (Q) nem coletar com duplo clique
        if (clickType == ClickType.THROW || clickType == ClickType.PICKUP_ALL) {
            return;
        }
        // nada de guardar emoji "emprestado" no inventario do jogador
        if (slotId >= SLOT_COUNT && isEmojiItem(this.getCarried())) {
            return;
        }
        if (slotId < 0 || slotId >= SLOT_COUNT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (isButton(slotId)) {
            handleButton(slotId, button, player);
            refreshItems();
            broadcastChanges();
            return;
        }
        if (!isPalette(slotId) && !isTarget(slotId)) {
            return; // vidro decorativo
        }
        if (clickType == ClickType.QUICK_MOVE) {
            if (isPalette(slotId)) {
                // Shift no emoji da paleta = coloca direto no ANTES
                ItemStack emoji = this.items.getItem(slotId).copy();
                if (!emoji.isEmpty()) {
                    addToGroup(BEFORE_SLOTS, emoji);
                }
            } else {
                // Shift na caixinha = limpa a caixinha
                this.items.setItem(slotId, ItemStack.EMPTY);
            }
            applyEmojis(player);
            refreshItems();
            broadcastChanges();
            return;
        }
        super.clicked(slotId, button, clickType, player);
        refillPalette();
        applyEmojis(player);
        refreshItems();
        broadcastChanges();
    }

    private void handleButton(int slotId, int button, Player player) {
        switch (slotId) {
            case SLOT_COLOR -> {
                int idx = indexOf(CENTRO_COLORS, style.colorRGB());
                idx = wrap(idx + (button == 1 ? -1 : 1), CENTRO_COLORS.length);
                style = withColor((int) CENTRO_COLORS[idx][1]);
            }
            case SLOT_BORDER -> {
                if (player.isShiftKeyDown()) {
                    style = withBorder(BubbleStyle.NO_BORDER);
                } else {
                    int idx = style.hasBorder() ? indexOf(BORDER_COLORS, style.borderRGB()) : 0;
                    idx = wrap(idx + (button == 1 ? -1 : 1), BORDER_COLORS.length);
                    style = withBorder((int) BORDER_COLORS[idx][1]);
                }
            }
            case SLOT_BG -> {
                int idx = wrap(style.background() + (button == 1 ? -1 : 1), BG_NAMES.length);
                style = withBackground(idx);
            }
            case SLOT_CLEAR -> {
                style = BubbleStyle.DEFAULT;
                for (int[] group : new int[][]{BEFORE_SLOTS, MID_SLOTS, AFTER_SLOTS}) {
                    for (int s : group) {
                        this.items.setItem(s, ItemStack.EMPTY);
                    }
                }
            }
            case SLOT_MODE -> {
                if (player instanceof ServerPlayer sp) {
                    RPWorldData data = RPWorldData.get(sp.server);
                    boolean on = !data.isBubbleMode(sp.getUUID());
                    data.setBubbleMode(sp.getUUID(), on);
                    this.bubbleMode = on;
                }
            }
            default -> {
            }
        }
    }

    // =====================================================================
    //  Fechamento: salva e recolhe emojis "emprestados"
    // =====================================================================

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide && player instanceof ServerPlayer sp) {
            applyEmojis(sp);
            // devolve emojis que ficaram no cursor
            if (isEmojiItem(this.getCarried())) {
                this.setCarried(ItemStack.EMPTY);
            }
            sweepEmojiItems(player);
            RPWorldData data = RPWorldData.get(sp.server);
            data.setStyle(sp.getUUID(), this.style);
            sp.sendSystemMessage(Component.literal("Balão atualizado! Fale no chat para ver ✨")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // todo o movimento e tratado no clicked(); nada de shift-mover aqui
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public net.minecraft.core.NonNullList<ItemStack> getItems() {
        return this.items.getItems();
    }

    // =====================================================================
    //  Internos
    // =====================================================================

    private void refreshItems() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (isPalette(i) || isTarget(i)) {
                continue; // conteudo real do jogador
            }
            this.items.setItem(i, itemFor(i));
        }
    }

    private ItemStack itemFor(int slot) {
        if (slot == SLOT_INFO) {
            return infoItem();
        }
        if (slot == SLOT_HELP) {
            return named(Items.BOOK, "Como funciona", false,
                    lore("• Clique nas cores para trocar"),
                    lore("(botao direito volta)"),
                    lore("• Pegue um emoji e solte na caixinha"),
                    lore("  Antes, Meio ou Depois do texto"),
                    lore("• Shift+clique na caixinha limpa ela"),
                    lore("• Fecha = salva automaticamente!"));
        }
        if (slot == SLOT_COLOR) {
            String name = nameOf(CENTRO_COLORS, style.colorRGB());
            return dyedLeather(style.colorRGB(),
                    "✿ Cor do CENTRO: " + hex(style.colorRGB()),
                    lore("Clique: próxima cor"),
                    lore("Botão direito: cor anterior"),
                    lore("Cor atual: " + name));
        }
        if (slot == SLOT_BORDER) {
            if (!style.hasBorder()) {
                return dyedLeather(0x555555,
                        "▣ Cor da BORDA: (sem borda)",
                        lore("Clique: escolher cor da borda"),
                        lore("A borda é a moldura do balão!"));
            }
            String name = nameOf(BORDER_COLORS, style.borderRGB());
            return dyedLeather(style.borderRGB(),
                    "▣ Cor da BORDA: " + hex(style.borderRGB()),
                    lore("Clique: próxima cor"),
                    lore("Botão direito: cor anterior"),
                    lore("Shift+clique: SEM borda"),
                    lore("Cor atual: " + name));
        }
        if (slot == SLOT_BG) {
            return named(Items.PAINTING, "▤ Fundo: " + BG_NAMES[style.background()], false,
                    lore("Clique: próximo fundo"),
                    lore("Botão direito: fundo anterior"),
                    lore("Estilos: Translúcido, Escuro, Claro,"),
                    lore("Gradiente, Papel, Noite, Madeira"));
        }
        if (slot == SLOT_CLEAR) {
            return named(Items.SPONGE, "✖ Limpar tudo", false,
                    lore("Volta o balão ao padrão"),
                    lore("(cores, fundo e emojis)"));
        }
        if (slot == SLOT_MODE) {
            return bubbleMode
                    ? named(Items.BELL, "☾ Modo balão: LIGADO", true,
                            lore("Suas falas aparecem SÓ no balão!"),
                            lore("Clique para desligar"))
                    : named(Items.BELL, "☾ Modo balão: DESLIGADO", false,
                            lore("Suas falas aparecem no chat + balão"),
                            lore("Clique para LIGAR"));
        }
        return named(Items.GRAY_STAINED_GLASS_PANE, " ", false);
    }

    private ItemStack infoItem() {
        String antes = readGroup(BEFORE_SLOTS);
        String meio = readGroup(MID_SLOTS);
        String depois = readGroup(AFTER_SLOTS);
        return named(Items.NAME_TAG, "✦ Seu Balão de Fala", false,
                lore("Cor: " + hex(style.colorRGB())
                        + "  ·  Borda: " + (style.hasBorder() ? hex(style.borderRGB()) : "sem")),
                lore("Fundo: " + BG_NAMES[style.background()]),
                lore(""),
                lore("Prévia: " + antes + " sua fala aqui " + depois
                        + (meio.isEmpty() ? "" : "  (meio: " + meio + ")")),
                lore(""),
                lore("Fecha = salva!"));
    }

    private void refillPalette() {
        for (int i = PALETTE_START; i <= PALETTE_END; i++) {
            if (this.items.getItem(i).isEmpty()) {
                this.items.setItem(i, emojiItem(EMOJIS[i - PALETTE_START]));
            }
        }
    }

    /** Le os emojis das caixinhas e salva no estilo do jogador. */
    private void applyEmojis(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        RPWorldData.get(sp.server).setStyle(sp.getUUID(), new BubbleStyle(
                style.colorRGB(), style.borderRGB(),
                readGroup(BEFORE_SLOTS), readGroup(MID_SLOTS), readGroup(AFTER_SLOTS),
                style.background()));
    }

    private String readGroup(int[] slots) {
        StringBuilder sb = new StringBuilder();
        for (int s : slots) {
            ItemStack st = this.items.getItem(s);
            if (!st.isEmpty()) {
                sb.append(st.getHoverName().getString());
            }
        }
        String r = sb.toString();
        return r.length() > BubbleStyle.MAX_DECOR ? r.substring(0, BubbleStyle.MAX_DECOR) : r;
    }

    private boolean addToGroup(int[] slots, ItemStack emoji) {
        for (int s : slots) {
            if (this.items.getItem(s).isEmpty()) {
                this.items.setItem(s, emoji.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    private BubbleStyle withColor(int rgb) {
        return new BubbleStyle(rgb, style.borderRGB(), style.prefix(), style.mid(), style.suffix(), style.background());
    }

    private BubbleStyle withBorder(int rgb) {
        return new BubbleStyle(style.colorRGB(), rgb, style.prefix(), style.mid(), style.suffix(), style.background());
    }

    private BubbleStyle withBackground(int bg) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(), bg);
    }

    private static void sweepEmojiItems(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isEmojiItem(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isButton(int slot) {
        return slot == SLOT_COLOR || slot == SLOT_BORDER || slot == SLOT_BG
                || slot == SLOT_CLEAR || slot == SLOT_MODE;
    }

    private static boolean isPalette(int slot) {
        return slot >= PALETTE_START && slot <= PALETTE_END;
    }

    private static boolean isTarget(int slot) {
        return in(BEFORE_SLOTS, slot) || in(MID_SLOTS, slot) || in(AFTER_SLOTS, slot);
    }

    private static boolean in(int[] arr, int v) {
        for (int a : arr) {
            if (a == v) {
                return true;
            }
        }
        return false;
    }

    private static int wrap(int v, int len) {
        return ((v % len) + len) % len;
    }

    private static int indexOf(Object[][] palette, int rgb) {
        for (int i = 0; i < palette.length; i++) {
            if ((int) palette[i][1] == rgb) {
                return i;
            }
        }
        return 0;
    }

    private static String nameOf(Object[][] palette, int rgb) {
        return (String) palette[indexOf(palette, rgb)][0];
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    // =====================================================================
    //  Fabrica de itens da GUI
    // =====================================================================

    /** Item de emoji "emprestado" (marcado para podermos recolher depois). */
    private static ItemStack emojiItem(String emoji) {
        ItemStack st = new ItemStack(Items.PAPER);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(emoji).withStyle(s -> s.withItalic(false)));
        CompoundTag tag = new CompoundTag();
        tag.putByte(EMOJI_MARK, (byte) 1);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return st;
    }

    private static boolean isEmojiItem(ItemStack st) {
        if (st.isEmpty()) {
            return false;
        }
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().contains(EMOJI_MARK);
    }

    private static ItemStack named(net.minecraft.world.item.Item item, String name, boolean green,
            Component... lore) {
        ItemStack st = new ItemStack(item);
        Style s = Style.EMPTY.withItalic(false);
        if (green) {
            s = s.withColor(TextColor.fromRgb(0x55FF88));
        }
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s));
        if (lore.length > 0) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            for (Component l : lore) {
                lines.add(l);
            }
            st.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
        }
        return st;
    }

    private static ItemStack dyedLeather(int rgb, String name, Component... lore) {
        ItemStack st = named(Items.LEATHER_CHESTPLATE, name, false, lore);
        st.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
        return st;
    }

    private static Component lore(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withItalic(false).withColor(TextColor.fromRgb(0xAAAAAA)));
    }
}
