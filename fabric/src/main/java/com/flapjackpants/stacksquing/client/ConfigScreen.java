package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.config.BookLayout;
import com.flapjackpants.stacksquing.core.GroupStore;
import com.flapjackpants.stacksquing.core.ItemGroup;
import com.flapjackpants.stacksquing.core.QuantityColumn;
import com.flapjackpants.stacksquing.core.QuantityFormat;
import com.flapjackpants.stacksquing.core.Supergroup;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ConfigScreen extends Screen {
    private static final int ROW_WIDTH = 310;
    private static final int ROW_HEIGHT = 25;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LABEL_WIDTH = 90;

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private SettingsList list;

    // Values live here (not in the widgets) so they survive the widgets being rebuilt on resize.
    private String listFolder;
    private String groupsFile;
    private BookLayout bookLayout;
    private QuantityColumn column;
    private QuantityFormat format;
    private boolean hideFulfilled;
    private boolean groupsDirty;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("stacksquing.config.title"));
        this.parent = parent;
        StackSquingClient.groups().load();
        var config = StackSquingClient.config();
        listFolder = config.listFolder;
        groupsFile = config.groupsFile;
        bookLayout = config.layout;
        column = config.column;
        format = config.format;
        hideFulfilled = config.hideFulfilledGroups;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        list = layout.addToContents(new SettingsList(minecraft, width, layout));
        buildRows();
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        list.updateSize(width, layout);
    }

    private void buildRows() {
        GroupStore groups = StackSquingClient.groups();

        list.addHeading(Component.translatable("stacksquing.config.section.lists"));
        Path defaultFolder = FabricLoader.getInstance().getConfigDir().resolve("litematica");
        list.addText("stacksquing.config.list_folder", listFolder, defaultFolder.toString(), v -> listFolder = v);
        list.addText("stacksquing.config.groups_file", groupsFile, "~/.config/stackSquing/groups.json",
                v -> groupsFile = v);

        list.addHeading(Component.translatable("stacksquing.config.section.display"));
        list.addWide(CycleButton.builder((BookLayout v) -> Component.translatable(
                        v == BookLayout.SINGLE ? "stacksquing.config.layout.single" : "stacksquing.config.layout.double"),
                bookLayout)
                .withValues(BookLayout.values())
                .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.translatable("stacksquing.config.layout"),
                        (b, v) -> bookLayout = v));
        list.addWide(CycleButton.builder((QuantityColumn v) -> Component.translatable(
                        "stacksquing.config.column." + v.name().toLowerCase()), column)
                .withValues(QuantityColumn.values())
                .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.translatable("stacksquing.config.column"),
                        (b, v) -> column = v));
        list.addWide(CycleButton.builder((QuantityFormat v) -> Component.translatable(
                        "stacksquing.config.format." + v.name().toLowerCase()), format)
                .withValues(QuantityFormat.values())
                .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.translatable("stacksquing.config.format"),
                        (b, v) -> format = v));
        list.addWide(CycleButton.onOffBuilder(hideFulfilled)
                .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.translatable("stacksquing.config.hide_fulfilled"),
                        (b, v) -> hideFulfilled = v));

        list.addHeading(Component.translatable("stacksquing.config.section.supergroups"));
        List<Supergroup> supergroups = groups.supergroups();
        if (supergroups.isEmpty()) {
            list.addNote(Component.translatable("stacksquing.config.no_supergroups"));
        }
        for (Supergroup s : supergroups) {
            list.addWide(CycleButton.onOffBuilder(s.enabled)
                    .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.literal(s.name), (b, v) -> {
                        s.enabled = v;
                        groupsDirty = true;
                    }));
        }

        list.addHeading(Component.translatable("stacksquing.config.section.groups"));
        List<ItemGroup> itemGroups = groups.groups();
        if (itemGroups.isEmpty()) {
            list.addNote(Component.translatable("stacksquing.config.no_groups"));
        }
        for (ItemGroup g : itemGroups) {
            list.addWide(CycleButton.onOffBuilder(g.enabled)
                    .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.literal(g.name), (b, v) -> {
                        g.enabled = v;
                        groupsDirty = true;
                    }));
        }
    }

    @Override
    public void onClose() {
        GroupStore groups = StackSquingClient.groups();
        if (groupsDirty) {
            groups.save();
        }
        var config = StackSquingClient.config();
        config.listFolder = listFolder.trim();
        config.groupsFile = groupsFile.trim();
        config.layout = bookLayout;
        config.column = column;
        config.format = format;
        config.hideFulfilledGroups = hideFulfilled;
        Path groupsPath = config.resolvedGroupsFile();
        if (!groupsPath.equals(groups.configPath())) {
            groups.setConfigPath(groupsPath);
            groups.load();
        }
        StackSquingClient.saveConfig();
        StackSquingClient.state().refresh();
        minecraft.gui.setScreen(parent);
    }

    private final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
        SettingsList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
            super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
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

        void addText(String labelKey, String value, String hint, java.util.function.Consumer<String> onChange) {
            EditBox box = new EditBox(font, 0, 0, ROW_WIDTH - LABEL_WIDTH, WIDGET_HEIGHT,
                    Component.translatable(labelKey));
            box.setMaxLength(1024);
            box.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
            box.setValue(value);
            box.setResponder(onChange);
            addEntry(new WidgetRow(Component.translatable(labelKey), box));
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
    }
}
