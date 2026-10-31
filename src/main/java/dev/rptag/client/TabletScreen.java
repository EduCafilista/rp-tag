package dev.rptag.client;

import dev.rptag.FichaPersonagem;
import dev.rptag.RPTagMod;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Interface nativa: nenhum navegador, vídeo ou entidade adicional. */
public final class TabletScreen extends Screen {
    private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/item/quiralos.png");
    private static final String[] LABELS = {"Nome", "Idade", "Origem", "Habilidades", "História", "Revisão"};
    private static final String[] QUESTIONS = {"Como devemos chamar você?", "Quantos anos você tem?", "De onde você veio?",
            "O que você sabe fazer?", "Conte um pouco da sua história.", "Esta é a sua história."};
    private static final String[] HINTS = {"Nome do personagem", "De 1 a 999 anos", "Seu lugar de origem",
            "Talentos e conhecimentos do personagem", "Seu passado e o que trouxe você até aqui"};
    private final TabletClientState state;
    private final long opened = Util.getMillis();
    private boolean boot = true;
    private boolean renderedReadOnly;
    private int x, y, w, h, formX, formW, fieldY;
    private int blackHoleRadius;
    private int layoutBottom;
    private boolean showCharacter = true;
    private long transitionAt;
    private AbstractWidget answer;
    private ActionButton next, back, reload;
    private String localError = "";

    public TabletScreen(TabletClientState state) {
        super(Component.translatable("tablet.rptag.title"));
        this.state = state;
    }

    public TabletClientState state() { return state; }
    public void refresh() { init(minecraft, width, height); }
    public void onServerUpdate() {
        if (renderedReadOnly != state.readOnly() || state.blocked()) refresh();
    }

    private boolean motion() { return minecraft != null && minecraft.options.screenEffectScale().get() > .01; }

    @Override protected void init() {
        TabletLayout layout = TabletLayout.of(width,height);
        x=layout.x(); y=layout.y(); w=layout.width(); h=layout.height();
        formX=layout.formX(); formW=layout.formWidth(); blackHoleRadius=layout.blackHoleRadius();
        showCharacter=layout.showCharacter();
        renderedReadOnly = state.readOnly();
        transitionAt = Util.getMillis();
        answer = null;
        if (boot) {
            addRenderableWidget(new ActionButton(x + w / 2 - 46, y + h - 35, 92, "Continuar", true, this::skipBoot));
            return;
        }
        int step = state.etapa();
        int bottom = layout.bottomBar();
        layoutBottom = bottom;
        // (5.2.0) tela baixa: a pergunta grande sai de cena para o campo nao encostar nos botoes
        int titleHeight = h < 168 ? 0 : Math.min(2, font.split(Component.literal(state.readOnly() ? "Sua ficha" : QUESTIONS[step]), formW).size()) * 11 + 9;
        fieldY = layout.fieldTop(titleHeight);
        int fieldHeight = layout.fieldHeight(titleHeight);
        if (state.readOnly() || step == 5) {
            addRenderableWidget(new Summary(formX, fieldY - 2, formW, fieldHeight + 3));
        } else if (step < 2) {
            EditBox box = new EditBox(font, formX+7, fieldY+20, formW-14, 10, Component.literal(HINTS[step]));
            box.setBordered(false);
            box.setTextShadow(false);
            box.setTextColor(0xffeee7f3);
            box.setMaxLength(FichaPersonagem.LIMITES[step]);
            box.setValue(state.respostas().campo(step));
            if (step == 1) box.setFilter(text -> text.matches("[0-9]{0,5}"));
            box.setHint(Component.literal(HINTS[step]));
            box.setResponder(value -> edited(step, value));
            answer = addRenderableWidget(box);
        } else {
            MultiLineEditBox box = new MultiLineEditBox(font, formX, fieldY + 11, formW, Math.max(22, fieldHeight - 12),
                    Component.literal(HINTS[step]), Component.literal(LABELS[step])) {
                @Override protected void renderBackground(GuiGraphics graphics) {
                    drawField(graphics,getX(),getY(),getWidth(),getHeight(),isFocused());
                }
            };
            box.setCharacterLimit(FichaPersonagem.LIMITES[step]);
            box.setValue(state.respostas().campo(step));
            box.setValueListener(value -> edited(step, value));
            answer = addRenderableWidget(box);
        }
        back = addRenderableWidget(new ActionButton(formX, bottom, 66, state.readOnly() ? "Fechar" : "Voltar", false, () -> {
            if (state.readOnly()) onClose(); else go(Math.max(0, state.etapa()-1));
        }));
        back.active = state.readOnly() || step > 0;
        if (!state.readOnly() && !state.blocked()) {
            int buttonWidth = Math.min(step == 5 ? 132 : 92, formW - 76);
            next = addRenderableWidget(new ActionButton(formX + formW - buttonWidth, bottom, buttonWidth,
                    step == 5 ? "Confirmar" : "Continuar", true, this::next));
            for (int i = 0; i < 6; i++) {
                final int destination = i;
                ActionButton dot = addRenderableWidget(new ActionButton(formX + Math.max(0, (formW - 120) / 2) + i*20,
                        y + h - 15, 18, "·", false, () -> go(destination)));
                dot.setHeight(12);
                dot.setMessage(Component.literal(LABELS[i]));
                dot.dot = true;
                dot.selected = i == step;
            }
        }
        if (state.blocked()) {
            // (5.2.0) ocupa o lugar do "Continuar" em vez de abrir outra linha: nada encosta no status
            reload = addRenderableWidget(new ActionButton(formX + 70, bottom, Math.max(80, formW - 74),
                    "Usar ficha do servidor", false, () -> {
                state.reloadServer(); localError = ""; refresh();
            }));
            reload.active = !state.pending();
        }
        if (answer != null) setInitialFocus(answer);
    }

    private void edited(int step, String value) {
        state.edit(state.respostas().comCampo(step, value), step);
        localError = "";
    }

    private void skipBoot() { boot = false; refresh(); }

    private void go(int step) {
        if (state.pending() && state.confirming() || state.blocked()) return;
        state.edit(state.respostas(), step);
        state.flush(false);
        localError = "";
        refresh();
    }

    private void next() {
        if (state.readOnly() || state.confirming()) return;
        if (state.etapa() < 5 && state.respostas().campo(state.etapa()).isBlank()) {
            localError = "Preencha este campo para continuar.";
            return;
        }
        String error = state.respostas().validationError(state.etapa() == 5);
        if (!error.isEmpty()) { localError = error; return; }
        if (state.etapa() == 5) state.flush(true);
        else go(state.etapa()+1);
    }

    @Override public void tick() {
        if (boot && Util.getMillis() - opened >= 1100) skipBoot();
        if (!boot && renderedReadOnly != state.readOnly()) refresh();
        boolean editing = !state.confirming() && !state.blocked();
        if (answer != null) answer.active = editing;
        if (next != null) next.active = editing;
        if (back != null) back.active = state.readOnly() || editing && state.etapa() > 0;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (boot && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE)) { skipBoot(); return true; }
        if (!boot && key == GLFW.GLFW_KEY_ENTER && !(getFocused() instanceof MultiLineEditBox)
                && !(getFocused() instanceof AbstractButton) && !state.readOnly()) { next(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() {
        if (!state.readOnly()) state.flush(false);
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    // Screen.render chama renderBackground novamente antes dos widgets; aqui causava blur na própria interface.
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if (boot) { skipBoot(); return true; }
        if (!boot && answer instanceof EditBox && mouseX>=formX && mouseX<formX+formW
                && mouseY>=fieldY+11 && mouseY<fieldY+37 && button==0) {
            return super.mouseClicked(Math.max(answer.getX()+1,Math.min(mouseX,answer.getRight()-1)),answer.getY()+4,button);
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    private static void drawField(GuiGraphics graphics,int x,int y,int width,int height,boolean focused) {
        graphics.fill(x,y,x+width,y+height,0xff141724);
        graphics.renderOutline(x,y,width,height,focused?0xffb28c68:0xff373445);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0,0,width,height,0xff0e0b1a,0xff070b13);
        if (boot) {
            int size = Math.min(88, h / 3);
            blitLogo(g, x + (w-size)/2, y + h/2 - size + 5, size);
            g.drawCenteredString(font, "QuiralOS", x+w/2, y+h/2+19, 0xfff1edf7);
            if (h > 190) g.drawCenteredString(font, "NEPTUNE SMP", x+w/2, y+h/2+35, 0xffa99cb7);
            int progress = (int)(Math.min(1, (Util.getMillis()-opened)/1000.0)*110);
            g.fill(x+w/2-55, y+h-52, x+w/2+55, y+h-50, 0xff302a3c);
            g.fill(x+w/2-55, y+h-52, x+w/2-55+progress, y+h-50, 0xffefb17b);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (!showCharacter) {
            g.drawString(font, "QuiralOS", x+15, y+14, 0xffc9c2d3, false);
        } else {
        for (int i = 0; i < 75; i++) {
            int sx = x + 5 + Math.floorMod(i*173 + i*i*13, Math.max(1,w-10));
            int sy = y + 5 + Math.floorMod(i*97 + i*i*7, Math.max(1,h-10));
            g.fill(sx,sy,sx+1,sy+1,0x554f526d);
        }
        g.drawString(font, "Neptune SMP", x+15, y+14, 0xffc9c2d3, false);
        int leftWidth = Math.max(0, formX-x-18);
        if (showCharacter && leftWidth > 40 && blackHoleRadius > 0) {
            blackHole(g, x+leftWidth/2+8, y+Math.max(48,(h-20)/3)-blackHoleRadius/3, blackHoleRadius);
            TabletPlayerPreview.render(g, state.target(), x+8, y+37, leftWidth, h-65, state.etapa(), motion());
            String name = state.respostas().nome().isBlank() ? "Seu personagem" : state.respostas().nome();
            g.drawCenteredString(font, font.plainSubstrByWidth(name,leftWidth-8), x+leftWidth/2+8, y+h-21, 0xffd4cddc);
        }
        }
        String stage = state.readOnly() ? "CONSULTA DE FICHA" : (state.etapa()+1) + " / 6   " + LABELS[state.etapa()].toUpperCase(java.util.Locale.ROOT);
        g.drawString(font, stage, formX, y+34, 0xffb8a6c7, false);
        if (h >= 168) {
            List<FormattedCharSequence> title = font.split(Component.literal(state.readOnly() ? "Sua ficha" : QUESTIONS[state.etapa()]),formW);
            int ty = y+50;
            for (FormattedCharSequence line:title) { g.drawString(font,line,formX,ty,0xfff0eaf5,false); ty+=11; }
        }
        if (!state.readOnly() && state.etapa()<5) g.drawString(font,LABELS[state.etapa()],formX,fieldY,0xffc6bdcf,false);
        if (answer instanceof EditBox) drawField(g,formX,fieldY+11,formW,26,answer.isFocused());
        String status = !localError.isEmpty() ? localError : !state.error().isEmpty() ? state.error()
                : state.readOnly() ? "Alterações precisam de liberação administrativa." : state.pending() ? "Salvando…" : state.dirty() ? "Alterações pendentes" : "Salvo no servidor";
        List<FormattedCharSequence> statusLines = font.split(Component.literal(status),formW);
        {
            int sy = layoutBottom - 20;
            for (FormattedCharSequence line:statusLines.subList(0,Math.min(2,statusLines.size()))) {
                g.drawString(font,line,formX,sy,!localError.isEmpty()||!state.error().isEmpty()?0xffffb4a2:0xffa7acae,false); sy+=9;
            }
        }
        super.render(g,mouseX,mouseY,partialTick);
        long elapsed = Util.getMillis()-transitionAt;
        if (motion() && elapsed<200) g.fill(formX,y+32,formX+formW,y+h-2,((int)(100*(1-elapsed/200.0))<<24)|0x090d17);
    }

    private static void blitLogo(GuiGraphics g,int x,int y,int size) {
        g.blit(LOGO,x,y,size,size,0,0,256,256,256,256);
    }

    private static void blackHole(GuiGraphics g,int cx,int cy,int r) {
        int halo=r+5;
        for(int dy=-halo;dy<=halo;dy++) {
            int extent=(int)Math.sqrt(halo*halo-dy*dy);
            g.fill(cx-extent,cy+dy,cx+extent+1,cy+dy+1,0x20633637);
        }
        for (int dy=-r;dy<=r;dy++) {
            int outer=(int)Math.sqrt(r*r-dy*dy);
            g.fill(cx-outer,cy+dy,cx+outer+1,cy+dy+1,0xffe75a24);
            int innerR=r-2;
            if (Math.abs(dy)<=innerR) {
                int inner=(int)Math.sqrt(innerR*innerR-dy*dy);
                g.fill(cx-inner,cy+dy,cx+inner+1,cy+dy+1,0xff02040a);
            }
        }
        for (int dx=-r-r/3;dx<=r+r/3;dx++) {
            int yy=cy-dx/4;
            g.fill(cx+dx,yy-3,cx+dx+1,yy+4,0x407a3325);
            g.fill(cx+dx,yy-2,cx+dx+1,yy+3,0xffed6d25);
            g.fill(cx+dx,yy-1,cx+dx+1,yy+1,0xffffd585);
        }
    }

    private static final class ActionButton extends AbstractButton {
        private final Runnable action;
        private final boolean primary;
        private boolean dot,selected;
        ActionButton(int x,int y,int width,String text,boolean primary,Runnable action) {
            super(x,y,width,22,Component.literal(text));
            this.action=action;this.primary=primary;
        }
        @Override public void onPress() { action.run(); }
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
            int color=primary?active?0xffefb17b:0xff655447:isHoveredOrFocused()?0xff302b3b:0xff151724;
            if (!dot) g.fill(getX(),getY(),getX()+width,getY()+height,color);
            if (isFocused()) g.renderOutline(getX()-1,getY()-1,width+2,height+2,0xffffd7ae);
            if (dot) {
                int size=selected?6:4, cx=getX()+width/2, cy=getY()+height/2;
                g.fill(cx-size/2,cy-size/2,cx+size/2,cy+size/2,selected?0xffefb17b:isHoveredOrFocused()?0xffd2c6de:0xff8e809e);
                return;
            }
            g.drawCenteredString(Minecraft.getInstance().font,getMessage().getString(),getX()+width/2,getY()+(height-8)/2,
                    primary?0xff241811:active?0xffded6e7:0xff746c7c);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class Summary extends AbstractScrollWidget {
        private final FichaPersonagem ficha = state.respostas();
        private final int contentHeight;
        Summary(int x,int y,int width,int height) {
            super(x,y,width,height,Component.literal("Ficha do personagem"));
            int lines=0;
            for(int i=0;i<5;i++) lines+=font.split(Component.literal(ficha.campo(i).isBlank()?"Não preenchido":ficha.campo(i)),width-16).size()*10+24;
            contentHeight=lines+8;
        }
        @Override protected int getInnerHeight() { return contentHeight; }
        @Override protected void renderBackground(GuiGraphics graphics) {
            if(isFocused()) graphics.renderOutline(getX(),getY(),getWidth(),getHeight(),0xff68556f);
        }
        @Override protected double scrollRate() { return 12; }
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float partialTick) {
            int yy=getY()+6;
            for(int i=0;i<5;i++) {
                g.drawString(font,LABELS[i],getX()+6,yy,0xffa99cb7,false);yy+=13;
                for(FormattedCharSequence line:font.split(Component.literal(ficha.campo(i).isBlank()?"Não preenchido":ficha.campo(i)),width-16)) {
                    g.drawString(font,line,getX()+6,yy,0xffe4ddea,false);yy+=10;
                }
                yy+=11;
            }
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.HINT,Component.literal("Use a roda do mouse ou as setas para ler a ficha."));
        }
    }
}
