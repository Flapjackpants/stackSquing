package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.ContainerBook;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the capped GUI scale before the new screen (or the HUD) draws a frame at the old one. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("RETURN"))
    private void stacksquing$refreshScale(Screen screen, CallbackInfo ci) {
        ContainerBook.refreshScale();
    }
}
