package com.flapjackpants.stacksquing.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
    static final int ROW_WIDTH = 310;
    static final int ROW_HEIGHT = 25;
    static final int WIDGET_HEIGHT = 20;
    static final int LABEL_WIDTH = 90;
    static final int GAP = 4;

    private final Font font;

    SettingsList(Minecraft minecraft, Font font, int width, HeaderAndFooterLayout layout) {
        super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
        this.font = font;
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    void addHeading(Component text) {
        addEntry(new LabelRow(text.copy().withStyle(ChatFormatting.BOLD), 0xFFFFFFFF));
    }

    void addNote(Component text) {
        addEntry(new LabelRow(text, 0xFFAAAAAA));
    }

    void addWide(AbstractWidget widget) {
        addEntry(new WidgetRow(null, widget));
    }

    void addText(String labelKey, String value, String hint, Consumer<String> onChange) {
        EditBox box = addLabelledBox(labelKey, value, onChange);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
    }

    EditBox addLabelledBox(String labelKey, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, ROW_WIDTH - LABEL_WIDTH, WIDGET_HEIGHT,
                Component.translatable(labelKey));
        box.setMaxLength(1024);
        box.setValue(value);
        box.setResponder(onChange);
        addEntry(new WidgetRow(Component.translatable(labelKey), box));
        return box;
    }

    /** One row: main fills the width left over after the trailing widgets, which keep their own width. */
    void addSplit(AbstractWidget main, AbstractWidget... trailing) {
        addEntry(new SplitRow(main, trailing));
    }

    void clearAll() {
        clearEntries();
    }

    abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    final class LabelRow extends Row {
        private final Component text;
        private final int color;

        LabelRow(Component text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            g.text(font, text, getContentX(), getContentYMiddle() - font.lineHeight / 2 + 4, color);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    final class WidgetRow extends Row {
        private final Component label;
        private final AbstractWidget widget;

        WidgetRow(Component label, AbstractWidget widget) {
            this.label = label;
            this.widget = widget;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX();
            int w = getContentWidth();
            if (label != null) {
                g.text(font, label, x, getContentYMiddle() - font.lineHeight / 2, 0xFFFFFFFF);
                widget.setPosition(x + LABEL_WIDTH, getContentY());
                widget.setWidth(w - LABEL_WIDTH);
            } else {
                widget.setPosition(x, getContentY());
                widget.setWidth(w);
            }
            widget.extractRenderState(g, mouseX, mouseY, delta);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }
    }

    final class SplitRow extends Row {
        private final AbstractWidget main;
        private final AbstractWidget[] trailing;

        SplitRow(AbstractWidget main, AbstractWidget[] trailing) {
            this.main = main;
            this.trailing = trailing;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX();
            int right = x + getContentWidth();
            int y = getContentY();
            for (int i = trailing.length - 1; i >= 0; i--) {
                AbstractWidget w = trailing[i];
                right -= w.getWidth();
                w.setPosition(right, y);
                right -= GAP;
            }
            main.setPosition(x, y);
            main.setWidth(Math.max(0, right - x));
            main.extractRenderState(g, mouseX, mouseY, delta);
            for (AbstractWidget w : trailing) {
                w.extractRenderState(g, mouseX, mouseY, delta);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return all();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return all();
        }

        private List<AbstractWidget> all() {
            List<AbstractWidget> result = new ArrayList<>();
            result.add(main);
            result.addAll(List.of(trailing));
            return result;
        }
    }
}
