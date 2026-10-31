package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.RecipeBookShift;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookShift {
    @Shadow
    private int xOffset;

    @Unique
    private int stacksquing$shift;

    @Override
    public void stacksquing$setShift(int px) {
        stacksquing$shift = px;
    }

    @Inject(method = "initVisuals", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;xOffset:I",
            shift = At.Shift.AFTER))
    private void stacksquing$applyShift(CallbackInfo ci) {
        xOffset += stacksquing$shift;
    }
}
