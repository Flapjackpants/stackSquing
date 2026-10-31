package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.BookText;
import com.flapjackpants.stacksquing.client.ContainerBook;
import com.flapjackpants.stacksquing.client.ContainerBookHost;
import com.flapjackpants.stacksquing.client.RecipeBookHost;
import com.flapjackpants.stacksquing.client.RecipeBookShift;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin implements RecipeBookHost {
    @Shadow
    @Final
    private RecipeBookComponent<?> recipeBookComponent;

    @Override
    public boolean stacksquing$recipeBookVisible() {
        return recipeBookComponent.isVisible();
    }

    @Override
    public void stacksquing$closeRecipeBook() {
        if (recipeBookComponent.isVisible()) {
            recipeBookComponent.toggleVisibility();
        }
    }

    @Inject(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;init(IILnet/minecraft/client/Minecraft;Z)V"))
    private void stacksquing$shiftRecipeBook(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        ContainerBookHost host = (ContainerBookHost) this;
        ((RecipeBookShift) recipeBookComponent).stacksquing$setShift(
                ContainerBook.recipeShift(screen, screen.width, host.stacksquing$imageWidth()));
    }

    @Inject(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;initButton()V"))
    private void stacksquing$placeContainer(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        ContainerBookHost host = (ContainerBookHost) this;
        int iw = host.stacksquing$imageWidth();
        if (ContainerBook.active(screen, screen.width, iw)) {
            host.stacksquing$setLeftPos(recipeBookComponent.isVisible()
                    ? host.stacksquing$leftPos() - BookText.recipeShift(screen.width, iw)
                    : BookText.containerLeftPos(screen.width, iw));
        }
    }

    @Inject(method = "lambda$initButton$0(Lnet/minecraft/client/gui/components/Button;)V", at = @At("TAIL"))
    private void stacksquing$recipeBookToggled(CallbackInfo ci) {
        ContainerBook.onRecipeBookToggled((Screen) (Object) this);
    }
}
