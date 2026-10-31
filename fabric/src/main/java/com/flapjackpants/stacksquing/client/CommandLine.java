package com.flapjackpants.stacksquing.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;

final class CommandLine {
    enum Result { NOT_HANDLED, HANDLED, CLOSE }

    private int historyCursor = StackSquingClient.history().size();

    Result handleKey(int key, EditBox box, BookRenderer renderer) {
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            String cmd = box.getValue().trim();
            List<String> history = StackSquingClient.history();
            if (!cmd.isEmpty() && (history.isEmpty() || !history.get(history.size() - 1).equals(cmd))) {
                history.add(cmd);
            }
            box.setValue("");
            historyCursor = history.size();
            if (!StackSquingClient.state().execute(cmd)) {
                return Result.CLOSE;
            }
            renderer.clamp();
            return Result.HANDLED;
        }
        if (key == InputConstants.KEY_PAGEUP) {
            renderer.scrollBy(-BookText.visibleListRows(renderer.layout()));
            return Result.HANDLED;
        }
        if (key == InputConstants.KEY_PAGEDOWN) {
            renderer.scrollBy(BookText.visibleListRows(renderer.layout()));
            return Result.HANDLED;
        }
        if (key == InputConstants.KEY_UP) {
            List<String> history = StackSquingClient.history();
            if (historyCursor > 0 && !history.isEmpty()) {
                historyCursor = Math.min(historyCursor, history.size()) - 1;
                box.setValue(history.get(historyCursor));
                box.moveCursorToEnd(false);
            }
            return Result.HANDLED;
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
            return Result.HANDLED;
        }
        return Result.NOT_HANDLED;
    }
}
