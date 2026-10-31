package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.BookText;
import com.flapjackpants.stacksquing.client.ContainerBook;
import com.flapjackpants.stacksquing.client.ContainerBookHost;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen implements ContainerBookHost {
    @Shadow
    protected int leftPos;

    @Shadow
    @Final
    protected int imageWidth;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Override
    public int stacksquing$leftPos() {
        return leftPos;
    }

    @Override
    public int stacksquing$imageWidth() {
        return imageWidth;
    }

    @Override
    public void stacksquing$rebuild() {
        rebuildWidgets();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void stacksquing$shiftForBook(CallbackInfo ci) {
        if (ContainerBook.active((Screen) (Object) this, width, imageWidth)) {
            leftPos = BookText.containerLeftPos(width, imageWidth);
        }
    }
}
