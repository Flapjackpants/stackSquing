package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.config.BookLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class BookScreen extends Screen {
    private static final int TOP = 2;
    private static final int IMAGE_SIZE = BookText.IMAGE_SIZE;

    private BookRenderer renderer;
    private final CommandLine commandLine = new CommandLine();
    private EditBox box;
    private String savedInput = "";
    private boolean ticked;

    public BookScreen() {
        super(Component.translatable("stacksquing.book.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- lifecycle ----

    @Override
    protected void init() {
        BookLayout layout = BookText.effectiveLayout(StackSquingClient.config().layout, width);
        renderer = new BookRenderer(font, layout, (width - IMAGE_SIZE) / 2, width / 2, TOP);
        if (box != null) {
            savedInput = box.getValue();
        }
        box = renderer.createInput(savedInput);
        box.setCanLoseFocus(false);
        addRenderableWidget(box);
        setInitialFocus(box);
        box.setFocused(true);
        renderer.clamp();
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
        switch (commandLine.handleKey(event.key(), box, renderer)) {
            case CLOSE -> {
                onClose();
                return true;
            }
            case HANDLED -> {
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        renderer.scrollBy(-(int) Math.signum(vertical));
        return true;
    }

    // ---- rendering ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        renderer.drawBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        renderer.drawPage(g);
    }
}
