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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ConfigScreen extends Screen {
    private static final int ROW_WIDTH = SettingsList.ROW_WIDTH;
    private static final int WIDGET_HEIGHT = SettingsList.WIDGET_HEIGHT;

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
        list = layout.addToContents(new SettingsList(minecraft, font, width, layout));
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
            Button edit = Button.builder(Component.translatable("stacksquing.config.group.edit"),
                    b -> minecraft.gui.setScreen(new GroupEditScreen(this, g, this::rebuildRows))).width(50).build();
            Button delete = Button.builder(Component.translatable("stacksquing.config.group.delete"),
                    b -> minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                        if (confirmed) {
                            groups.removeGroup(g.name);
                            rebuildRows();
                        }
                        minecraft.gui.setScreen(this);
                    }, Component.translatable("stacksquing.config.group.delete_title", g.name),
                            Component.translatable("stacksquing.config.group.delete_message")))).width(50).build();
            list.addSplit(CycleButton.onOffBuilder(g.enabled)
                    .create(0, 0, ROW_WIDTH, WIDGET_HEIGHT, Component.literal(g.name), (b, v) -> {
                        g.enabled = v;
                        groupsDirty = true;
                    }), edit, delete);
        }
        list.addWide(Button.builder(Component.translatable("stacksquing.config.add_group"),
                b -> minecraft.gui.setScreen(new GroupEditScreen(this, null, this::rebuildRows)))
                .width(ROW_WIDTH).build());
    }

    private void rebuildRows() {
        double scroll = list.scrollAmount();
        list.clearAll();
        buildRows();
        list.setScrollAmount(scroll);
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
}
