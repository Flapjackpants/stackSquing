package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.config.BookLayout;
import com.flapjackpants.stacksquing.core.CommandProcessor;
import com.flapjackpants.stacksquing.core.DisplayRow;
import com.flapjackpants.stacksquing.core.Format;
import com.flapjackpants.stacksquing.core.UiText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.InputConstants;

public final class BookScreen extends Screen {
    private static final int TOP = 2;
    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_WIDTH = BookText.TEXT_WIDTH;
    private static final int IMAGE_SIZE = BookText.IMAGE_SIZE;
    private static final int STATUS_LINE = 12;
    private static final int INPUT_LINE = 13;

    private static final int BLACK = 0xFF000000;
    private static final int GREY = 0xFF808080;
    private static final int GROUP_BLUE = 0xFF1E3A8A;
    private static final int SUPER_PURPLE = 0xFF5B2A86;
    private static final int WARNING = 0xFFB06000;
    private static final int ERROR = 0xFFAA0000;

    private BookLayout layout = BookLayout.SINGLE;
    private EditBox box;
    private String savedInput = "";
    private boolean ticked;
    private int historyCursor = StackSquingClient.history().size();

    private List<String> wrappedSource;
    private BookLayout wrappedLayout;
    private List<FormattedCharSequence> wrapped = List.of();

    public BookScreen() {
        super(Component.translatable("stacksquing.book.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static CommandProcessor state() {
        return StackSquingClient.state();
    }

    // ---- geometry ----

    private int imgX() {
        return (width - IMAGE_SIZE) / 2;
    }

    private int pageTextX(int page) {
        if (layout == BookLayout.SINGLE) {
            return imgX() + BookViewScreen.PAGE_TEXT_X_OFFSET;
        }
        int cx = width / 2;
        return page == 0 ? cx - 172 + 42 : cx - 20 + BookViewScreen.PAGE_TEXT_X_OFFSET;
    }

    private int lastPage() {
        return layout == BookLayout.DOUBLE ? 1 : 0;
    }

    private int lineY(int line) {
        return TOP + BookViewScreen.PAGE_TEXT_Y_OFFSET + line * LINE_HEIGHT;
    }

    // ---- lifecycle ----

    @Override
    protected void init() {
        layout = BookText.effectiveLayout(StackSquingClient.config().layout, width);
        if (box != null) {
            savedInput = box.getValue();
        }
        int prompt = font.width("> ");
        box = new EditBox(font, pageTextX(lastPage()) + prompt, lineY(INPUT_LINE) - 1, TEXT_WIDTH - prompt, 10,
                Component.translatable("stacksquing.book.input"));
        box.setBordered(false);
        box.setTextColor(BLACK);
        box.setTextShadow(false);
        box.setMaxLength(256);
        box.setCanLoseFocus(false);
        box.setValue(savedInput);
        addRenderableWidget(box);
        setInitialFocus(box);
        box.setFocused(true);
        clamp();
    }

    @Override
    public void tick() {
        super.tick();
        ticked = true;
    }

    // ---- input ----

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!ticked) {
            return false;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            String cmd = box.getValue().trim();
            List<String> history = StackSquingClient.history();
            if (!cmd.isEmpty() && (history.isEmpty() || !history.get(history.size() - 1).equals(cmd))) {
                history.add(cmd);
            }
            box.setValue("");
            historyCursor = history.size();
            if (!state().execute(cmd)) {
                onClose();
            } else {
                clamp();
            }
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP) {
            scrollBy(-BookText.visibleListRows(layout));
            return true;
        }
        if (key == InputConstants.KEY_PAGEDOWN) {
            scrollBy(BookText.visibleListRows(layout));
            return true;
        }
        if (key == InputConstants.KEY_UP) {
            List<String> history = StackSquingClient.history();
            if (historyCursor > 0 && !history.isEmpty()) {
                historyCursor = Math.min(historyCursor, history.size()) - 1;
                box.setValue(history.get(historyCursor));
                box.moveCursorToEnd(false);
            }
            return true;
        }
        if (key == InputConstants.KEY_DOWN) {
            List<String> history = StackSquingClient.history();
            if (historyCursor < history.size() - 1) {
                historyCursor++;
                box.setValue(history.get(historyCursor));
                box.moveCursorToEnd(false);
            } else {
                historyCursor = history.size();
                box.setValue("");
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scrollBy(-(int) Math.signum(vertical));
        return true;
    }

    private void scrollBy(int delta) {
        state().setScroll(state().scroll() + delta);
        clamp();
    }

    private void clamp() {
        int clamped = UiText.clampScroll(state().scroll(), contentTotal(), BookText.visibleListRows(layout));
        if (clamped != state().scroll()) {
            state().setScroll(clamped);
        }
    }

    // ---- content model ----

    private boolean panelVisible() {
        return !state().panelLines().isEmpty();
    }

    private int contentTotal() {
        return panelVisible() ? wrappedPanel().size() : state().rows().size();
    }

    private List<FormattedCharSequence> wrappedPanel() {
        List<String> source = state().panelLines();
        if (source != wrappedSource || wrappedLayout != layout) {
            List<FormattedCharSequence> out = new ArrayList<>();
            for (String line : source) {
                UiText.PanelLineParts p = UiText.splitPanelLine(line);
                if (p.kind() == UiText.PanelLineKind.BLANK) {
                    out.add(FormattedCharSequence.EMPTY);
                    continue;
                }
                String indent = p.indent().isEmpty() ? "" : " ";
                MutableComponent c = Component.literal(indent);
                switch (p.kind()) {
                    case HEADING -> c.append(Component.literal(p.key()).withStyle(ChatFormatting.BOLD));
                    case ENTRY -> {
                        c.append(Component.literal(p.key()).withStyle(ChatFormatting.BOLD).withColor(0x1E3A8A));
                        c.append(Component.literal(p.rest()));
                    }
                    default -> {
                        c.append(Component.literal(p.key()).withStyle(ChatFormatting.BOLD).withColor(0x555555));
                        c.append(Component.literal(p.rest()).withColor(0x555555));
                    }
                }
                out.addAll(font.split(c, TEXT_WIDTH));
            }
            wrapped = out;
            wrappedSource = source;
            wrappedLayout = layout;
        }
        return wrapped;
    }

    // ---- rendering ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        if (layout == BookLayout.SINGLE) {
            g.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, imgX(), TOP, 0f, 0f,
                    IMAGE_SIZE, IMAGE_SIZE, 256, 256);
        } else {
            int cx = width / 2;
            int lx = cx - 172;
            int rx = cx - 20;
            float edge = IMAGE_SIZE / 256f;
            g.blit(BookViewScreen.BOOK_LOCATION, lx, TOP, lx + IMAGE_SIZE, TOP + IMAGE_SIZE, edge, 0f, 0f, edge);
            g.blit(RenderPipelines.GUI_TEXTURED, BookViewScreen.BOOK_LOCATION, rx, TOP, 0f, 0f,
                    IMAGE_SIZE, IMAGE_SIZE, 256, 256);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        clamp();
        drawHeader(g);
        drawContent(g);
        drawStatusAndPrompt(g);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        CommandProcessor s = state();
        String title = panelVisible() ? s.panelTitle() : s.title();
        if (title.isEmpty()) {
            title = "stackSquing";
        }
        int total = contentTotal();
        int visible = BookText.visibleListRows(layout);
        int first = Math.min(total, s.scroll() + 1);
        int last = Math.min(total, s.scroll() + visible);
        String indicator = UiText.scrollIndicator(first, last, total);
        int y = TOP + BookViewScreen.PAGE_INDICATOR_TEXT_Y_OFFSET;
        int indicatorPage = lastPage();
        int indicatorRight = pageTextX(indicatorPage) + TEXT_WIDTH;
        int indicatorW = font.width(indicator);
        int titleX = pageTextX(0);
        int titleRoom = layout == BookLayout.SINGLE ? TEXT_WIDTH - indicatorW - 4 : TEXT_WIDTH;
        drawFit(g, titleX, y, titleRoom, Component.literal(title), BLACK);
        if (!indicator.isEmpty()) {
            g.text(font, indicator, indicatorRight - indicatorW, y, GREY, false);
        }
    }

    private void drawContent(GuiGraphicsExtractor g) {
        CommandProcessor s = state();
        int total = contentTotal();
        int visible = BookText.visibleListRows(layout);
        boolean panel = panelVisible();
        List<FormattedCharSequence> wrappedLines = panel ? wrappedPanel() : List.of();
        for (int i = 0; i < visible; i++) {
            int idx = s.scroll() + i;
            if (idx >= total) {
                break;
            }
            BookText.Slot slot = BookText.slot(i, layout);
            int x = pageTextX(slot.page());
            int y = lineY(slot.line());
            if (panel) {
                g.text(font, wrappedLines.get(idx), x, y, BLACK, false);
            } else {
                drawRow(g, x, y, s.rows().get(idx));
            }
        }
    }

    private void drawRow(GuiGraphicsExtractor g, int x, int y, DisplayRow row) {
        String qty = Format.formatQuantity(row.quantity(), StackSquingClient.config().format, row.label());
        switch (row.kind()) {
            case ITEM -> {
                int color = row.fulfilled() ? GREY : BLACK;
                MutableComponent name = Component.literal(row.label());
                if (row.fulfilled()) {
                    name.withStyle(ChatFormatting.STRIKETHROUGH);
                }
                drawLine(g, x, y, BookText.itemPrefix(row.displayNumber()), name, Component.literal(qty), color);
            }
            case GROUP_HEADER -> drawLine(g, x, y, "",
                    Component.literal(row.label()).withStyle(ChatFormatting.BOLD),
                    Component.literal(qty).withStyle(ChatFormatting.BOLD), GROUP_BLUE);
            case SUPERGROUP_HEADER -> drawLine(g, x, y, "",
                    Component.literal(row.label()).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                    Component.literal(qty).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE), SUPER_PURPLE);
            default -> {
            }
        }
    }

    private void drawLine(GuiGraphicsExtractor g, int x, int y, String prefix, Component name, Component qty,
                          int color) {
        int dotW = font.width(".");
        int prefixW = font.width(prefix);
        int qtyW = font.width(qty);
        int nameMax = TEXT_WIDTH - prefixW - qtyW - 2 * dotW;
        int nameX = x + prefixW;
        int nameW = font.width(name);
        if (!prefix.isEmpty()) {
            g.text(font, prefix, x, y, color, false);
        }
        g.text(font, qty, x + TEXT_WIDTH - qtyW, y, color, false);
        if (nameW <= nameMax) {
            g.text(font, name, nameX, y, color, false);
            int gapStart = nameX + nameW + 1;
            int gapEnd = x + TEXT_WIDTH - qtyW - 1;
            String dots = BookText.leaderDots(gapEnd - gapStart, dotW);
            if (!dots.isEmpty()) {
                g.text(font, dots, gapStart, y, color, false);
            }
        } else {
            drawFit(g, nameX, y, nameMax, name, color);
        }
    }

    /** Draws text, scrolling it with the marquee inside a scissor box when wider than maxW. */
    private void drawFit(GuiGraphicsExtractor g, int x, int y, int maxW, Component text, int color) {
        int overflow = font.width(text) - maxW;
        if (overflow <= 0) {
            g.text(font, text, x, y, color, false);
            return;
        }
        g.enableScissor(x, y, x + maxW, y + LINE_HEIGHT);
        g.text(font, text, x - BookText.marqueeOffset(overflow, Util.getMillis()), y, color, false);
        g.disableScissor();
    }

    private void drawStatusAndPrompt(GuiGraphicsExtractor g) {
        int x = pageTextX(lastPage());
        String status = state().status();
        int color = switch (UiText.classifyStatus(status)) {
            case NORMAL -> BLACK;
            case WARNING -> WARNING;
            case ERROR -> ERROR;
        };
        drawFit(g, x, lineY(STATUS_LINE), TEXT_WIDTH, Component.literal(status), color);
        g.text(font, ">", x, lineY(INPUT_LINE), BLACK, false);
    }
}
