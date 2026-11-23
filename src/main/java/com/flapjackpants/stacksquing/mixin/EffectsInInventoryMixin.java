package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.ContainerBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
    @Shadow
    @Final
    private AbstractContainerScreen<?> screen;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void stacksquing$hideBesideBook(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
        if (ContainerBook.besideScreen(screen)) {
            ci.cancel();
        }
    }
}
