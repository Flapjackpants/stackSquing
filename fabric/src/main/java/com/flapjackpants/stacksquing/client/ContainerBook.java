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
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.KeyEvent;

public final class ContainerBook {
    private static boolean shown;
    private static String savedInput = "";
    private static CommandLine commandLine;
    private static Panel current;

    private ContainerBook() {
    }

    /** Session flag; false at startup, never saved. */
    public static boolean shown() {
        return shown;
    }

    public static boolean eligible(Screen screen) {
        return screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen || screen instanceof DispenserScreen
                || screen instanceof InventoryScreen || screen instanceof CraftingScreen
                || screen instanceof AbstractFurnaceScreen;
    }

    public static boolean active(Screen screen, int screenWidth, int imageWidth) {
        return shown && eligible(screen) && BookText.containerBookFits(screenWidth, imageWidth)
                && !recipeBlocked(screen, screenWidth, imageWidth);
    }

    /** Recipe book open and no room for it, the container and the side book. */
    public static boolean recipeBlocked(Screen screen, int screenWidth, int imageWidth) {
        return screen instanceof RecipeBookHost r && r.stacksquing$recipeBookVisible()
                && !BookText.recipeAndBookFit(screenWidth, imageWidth);
    }

    public static int recipeShift(Screen screen, int screenWidth, int imageWidth) {
        return shown && eligible(screen) ? BookText.recipeShift(screenWidth, imageWidth) : 0;
    }

    public static boolean besideScreen(Screen screen) {
        return current != null && current.screen == screen && current.renderer != null;
    }

    public static void onRecipeBookToggled(Screen screen) {
        if (current == null || current.screen != screen || !shown) {
            return;
        }
        if (recipeBlocked(screen, screen.width, current.host.stacksquing$imageWidth())) {
            shown = false;
        }
        current.rebuild();
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

    /**
     * Book state for one container screen. Fabric only fires AFTER_INIT (and resets per-screen events) from
     * setScreen and resize, not from rebuildWidgets, so the handlers are registered once per Fabric init and
     * {@link #attach} rebuilds the book after every rebuildWidgets call.
     */
    private static final class Panel {
        private final Screen screen;
        private final ContainerBookHost host;
        private BookRenderer renderer;
        private EditBox box;

        Panel(Screen screen) {
            this.screen = screen;
            this.host = (ContainerBookHost) screen;
        }

        void attach() {
            renderer = null;
            box = null;
            if (!active(screen, screen.width, host.stacksquing$imageWidth())) {
                return;
            }
            renderer = new BookRenderer(Screens.getFont(screen), BookLayout.SINGLE,
                    BookText.containerBookX(host.stacksquing$leftPos(), host.stacksquing$imageWidth()),
                    screen.width / 2, BookText.containerBookTop(screen.height));
            box = renderer.createInput(savedInput);
            box.setCanLoseFocus(true);
            box.setFocused(false);
            box.setResponder(v -> savedInput = v);
            Screens.getWidgets(screen).add(box);
            renderer.clamp();
        }

        void rebuild() {
            host.stacksquing$rebuild();
            attach();
        }

        boolean onKey(KeyEvent event, KeyMapping openBook) {
            if (box == null || !box.isFocused()) {
                if (screen.isInputCaptured()) {
                    return true;
                }
                if (openBook.matches(event)) {
                    if (recipeBlocked(screen, screen.width, host.stacksquing$imageWidth())) {
                        if (!shown) {
                            shown = true;
                            StackSquingClient.state().onOpen();
                        }
                        ((RecipeBookHost) screen).stacksquing$closeRecipeBook();
                    } else {
                        shown = !shown;
                        if (shown) {
                            StackSquingClient.state().onOpen();
                        }
                    }
                    rebuild();
                    return false;
                }
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                unfocus(screen, box);
                return false;
            }
            switch (commandLine().handleKey(event.key(), box, renderer)) {
                case CLOSE -> {
                    shown = false;
                    savedInput = "";
                    rebuild();
                }
                case HANDLED -> {
                }
                default -> box.keyPressed(event);
            }
            return false;
        }

        void register(KeyMapping openBook) {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> onKey(event, openBook));
            ScreenEvents.afterBackground(screen).register((s, g, mouseX, mouseY, delta) -> {
                if (renderer != null) {
                    renderer.drawBackground(g);
                }
            });
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
                if (renderer != null) {
                    renderer.drawPage(g);
                }
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (box != null && box.isFocused() && !box.isMouseOver(event.x(), event.y())) {
                    unfocus(s, box);
                }
                return true;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
                if (renderer != null && renderer.contains(mouseX, mouseY)) {
                    renderer.scrollBy(-(int) Math.signum(vertical));
                    return false;
                }
                return true;
            });
        }
    }

    public static void register(KeyMapping openBook) {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!eligible(screen)) {
                return;
            }
            Panel panel = new Panel(screen);
            panel.register(openBook);
            current = panel;
            ScreenEvents.remove(screen).register(s -> {
                if (current == panel) {
                    current = null;
                }
            });
            panel.attach();
        });
    }
}
