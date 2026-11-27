package com.flapjackpants.stacksquing.mixin;

import com.flapjackpants.stacksquing.client.ContainerBookHost;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * These screens place their texture, hit boxes and buttons at (width - imageWidth) / 2 (or width / 2) instead of
 * leftPos, so they would stay centred while the slots move. Reading width as 2 * leftPos + imageWidth makes that
 * expression equal leftPos; unshifted it gives the same result as the real width.
 */
@Mixin({AbstractMountInventoryScreen.class, BeaconScreen.class, BrewingStandScreen.class, ContainerScreen.class,
        CrafterScreen.class, DispenserScreen.class, EnchantmentScreen.class, GrindstoneScreen.class, HopperScreen.class,
        MerchantScreen.class, ShulkerBoxScreen.class})
public abstract class CenteredScreenMixin {
    @ModifyExpressionValue(method = {"init", "extractBackground", "extractContents", "extractRedstone", "mouseClicked"},
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "width:I"))
    private int stacksquing$widthAroundLeftPos(int width) {
        ContainerBookHost host = (ContainerBookHost) this;
        return 2 * host.stacksquing$leftPos() + host.stacksquing$imageWidth();
    }
}
