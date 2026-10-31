package com.flapjackpants.stacksquing.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.flapjackpants.stacksquing.config.BookLayout;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

public final class ContainerBook {
    private static boolean shown;
    private static String savedInput = "";
    private static CommandLine commandLine;

    private ContainerBook() {
    }

    /** Session flag; false at startup, never saved. */
    public static boolean shown() {
        return shown;
    }

    public static boolean eligible(Screen screen) {
        return screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen || screen instanceof DispenserScreen;
    }

    public static boolean active(Screen screen, int screenWidth, int imageWidth) {
        return shown && eligible(screen) && BookText.containerBookFits(screenWidth, imageWidth);
    }

    private static CommandLine commandLine() {
        if (commandLine == null) {
            commandLine = new CommandLine();
        }
        return commandLine;
    }

    private static void unfocus(Screen screen, EditBox box) {
        box.setFocused(false);
        screen.setFocused(null);
    }

    public static void register(KeyMapping openBook) {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!eligible(screen)) {
                return;
            }
            ContainerBookHost host = (ContainerBookHost) screen;
            EditBox[] boxRef = new EditBox[1];

            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (boxRef[0] != null && boxRef[0].isFocused()) {
                    return true;
                }
                if (openBook.matches(event)) {
                    shown = !shown;
                    if (shown) {
                        StackSquingClient.state().onOpen();
                    }
                    host.stacksquing$rebuild();
                    return false;
                }
                return true;
            });

            if (!active(screen, w, host.stacksquing$imageWidth())) {
                return;
            }

            BookRenderer renderer = new BookRenderer(Screens.getFont(screen), BookLayout.SINGLE,
                    BookText.containerBookX(host.stacksquing$leftPos(), host.stacksquing$imageWidth()), w / 2,
                    BookText.containerBookTop(h));
            EditBox box = renderer.createInput(savedInput);
            box.setCanLoseFocus(true);
            box.setFocused(false);
            box.setResponder(v -> savedInput = v);
            boxRef[0] = box;
            Screens.getWidgets(screen).add(box);
            renderer.clamp();

            ScreenEvents.afterBackground(screen).register((s, g, mouseX, mouseY, delta) -> renderer.drawBackground(g));
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> renderer.drawPage(g));

            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!box.isFocused()) {
                    return true;
                }
                if (event.key() == InputConstants.KEY_ESCAPE) {
                    unfocus(s, box);
                    return false;
                }
                switch (commandLine().handleKey(event.key(), box, renderer)) {
                    case CLOSE -> {
                        shown = false;
                        savedInput = "";
                        host.stacksquing$rebuild();
                        return false;
                    }
                    case HANDLED -> {
                        return false;
                    }
                    default -> {
                        box.keyPressed(event);
                        return false;
                    }
                }
            });

            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (box.isFocused() && !box.isMouseOver(event.x(), event.y())) {
                    unfocus(s, box);
                }
                return true;
            });

            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
                if (renderer.contains(mouseX, mouseY)) {
                    renderer.scrollBy(-(int) Math.signum(vertical));
                    return false;
                }
                return true;
            });
        });
    }
}
