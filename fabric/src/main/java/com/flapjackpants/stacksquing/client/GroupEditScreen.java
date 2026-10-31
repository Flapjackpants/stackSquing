package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.core.GroupEditing;
import com.flapjackpants.stacksquing.core.GroupStore;
import com.flapjackpants.stacksquing.core.ItemGroup;
import com.flapjackpants.stacksquing.core.MaterialList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class GroupEditScreen extends Screen {
    private record PickEntry(String itemName, Button button) {
    }

    private final Screen parent;
    private final String originalName;
    private final ItemGroup draft;
    private final Runnable onSaved;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final List<PickEntry> picks = new ArrayList<>();
    private SettingsList list;
    private EditBox includeBox;
    private EditBox excludeBox;
    private Component error;

    /** original == null creates a new group; otherwise a copy of original is edited and original is never mutated. */
    public GroupEditScreen(Screen parent, ItemGroup original, Runnable onSaved) {
        super(Component.translatable(original == null
                ? "stacksquing.config.group.new_title" : "stacksquing.config.group.edit_title"));
        this.parent = parent;
        this.onSaved = onSaved;
        if (original == null) {
            originalName = null;
            draft = new ItemGroup();
            draft.enabled = true;
        } else {
            originalName = original.name;
            draft = GroupEditing.copy(original);
        }
        if (StackSquingClient.state().list().isEmpty()) {
            StackSquingClient.state().onOpen();
        }
    }

    @Override
    protected void init() {
        picks.clear();
        layout.addTitleHeader(title, font);
        list = layout.addToContents(new SettingsList(minecraft, font, width, layout));
        buildRows();
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("stacksquing.config.group.save"), b -> save())
                .width(150).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(150).build());
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
        list.addLabelledBox("stacksquing.config.group.name", draft.name, v -> {
            draft.name = v;
            error = null;
        });
        list.addWide(CycleButton.onOffBuilder(draft.enabled)
                .create(0, 0, SettingsList.ROW_WIDTH, SettingsList.WIDGET_HEIGHT,
                        Component.translatable("stacksquing.config.group.enabled"), (b, v) -> draft.enabled = v));
        includeBox = list.addLabelledBox("stacksquing.config.group.include", GroupEditing.joinTerms(draft.include),
                v -> {
                    draft.include.clear();
                    draft.include.addAll(GroupEditing.parseTerms(v));
                    refreshPicks();
                });
        includeBox.setHint(Component.translatable("stacksquing.config.group.terms_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        excludeBox = list.addLabelledBox("stacksquing.config.group.exclude", GroupEditing.joinTerms(draft.exclude),
                v -> {
                    draft.exclude.clear();
                    draft.exclude.addAll(GroupEditing.parseTerms(v));
                    refreshPicks();
                });
        excludeBox.setHint(Component.translatable("stacksquing.config.group.terms_hint")
                .withStyle(ChatFormatting.DARK_GRAY));

        list.addHeading(Component.translatable("stacksquing.config.group.items"));
        list.addNote(Component.translatable("stacksquing.config.group.items_help"));
        MaterialList materials = StackSquingClient.state().list().orElse(null);
        List<String> names = GroupEditing.itemNames(materials);
        if (names.isEmpty()) {
            list.addNote(Component.translatable("stacksquing.config.group.no_list"));
        }
        for (String name : names) {
            Button button = Button.builder(Component.literal(name), b -> {
                GroupEditing.togglePick(groups, draft, name);
                includeBox.setValue(GroupEditing.joinTerms(draft.include));
                excludeBox.setValue(GroupEditing.joinTerms(draft.exclude));
                refreshPicks();
            }).width(SettingsList.ROW_WIDTH).build();
            picks.add(new PickEntry(name, button));
            list.addWide(button);
        }
        refreshPicks();
    }

    private void refreshPicks() {
        GroupStore groups = StackSquingClient.groups();
        for (PickEntry entry : picks) {
            if (groups.matches(draft, entry.itemName())) {
                entry.button().setMessage(Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(entry.itemName())));
            } else {
                entry.button().setMessage(Component.literal("✘ ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(entry.itemName()).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private void save() {
        GroupStore groups = StackSquingClient.groups();
        String name = draft.name.trim();
        String msg = GroupEditing.validateName(groups, originalName, name);
        if (msg != null) {
            error = Component.literal(msg);
            return;
        }
        draft.name = name;
        if (!groups.replaceGroup(originalName, draft)) {
            error = Component.translatable("stacksquing.config.group.save_failed");
            return;
        }
        onSaved.run();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (error != null) {
            g.centeredText(font, error, width / 2, layout.getHeaderHeight() - font.lineHeight - 2, 0xFFFF5555);
        }
    }
}
