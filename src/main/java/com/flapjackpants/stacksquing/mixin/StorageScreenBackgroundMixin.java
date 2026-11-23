package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.ContainerBookHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * These screens blit their texture at (width - imageWidth) / 2 instead of leftPos, so the texture would stay
 * centred while the slots move. Translate the texture blit by however far leftPos was shifted.
 */
@Mixin({ContainerScreen.class, ShulkerBoxScreen.class, HopperScreen.class, DispenserScreen.class})
public abstract class StorageScreenBackgroundMixin {
    @Unique
    private boolean stacksquing$translated;

    @Inject(method = "extractBackground", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void stacksquing$shiftTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta,
                                          CallbackInfo ci) {
        ContainerBookHost host = (ContainerBookHost) this;
        int dx = host.stacksquing$leftPos() - (((Screen) (Object) this).width - host.stacksquing$imageWidth()) / 2;
        stacksquing$translated = dx != 0;
        if (stacksquing$translated) {
            g.pose().pushMatrix();
            g.pose().translate(dx, 0);
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void stacksquing$restoreTexture(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta,
                                            CallbackInfo ci) {
        if (stacksquing$translated) {
            g.pose().popMatrix();
            stacksquing$translated = false;
        }
    }
}
