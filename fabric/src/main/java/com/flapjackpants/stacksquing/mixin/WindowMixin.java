package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.ContainerBook;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Never touches the saved guiScale option; only the computed scale is capped. */
@Mixin(Window.class)
public abstract class WindowMixin {
    @Inject(method = "calculateScale", at = @At("RETURN"), cancellable = true)
    private void stacksquing$capScale(int guiScale, boolean enforceUnicode, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ContainerBook.capScale(cir.getReturnValue(), ((Window) (Object) this).getWidth()));
    }
}
