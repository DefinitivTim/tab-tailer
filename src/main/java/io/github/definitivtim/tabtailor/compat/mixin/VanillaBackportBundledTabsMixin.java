package io.github.definitivtim.tabtailor.compat.mixin;

import io.github.definitivtim.tabtailor.compat.ForeignSections;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Backport shows a bar of "bundled tabs" next to its creative tab and filters the grid by them.
 * In tabs that Tab Tailor has a file for, Tab Tailor's own section bar is used instead.
 */
@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.client.api.bundled_tabs.BundledTabSelector", remap = false)
public abstract class VanillaBackportBundledTabsMixin {
    @Inject(method = "isValidTab", at = @At("HEAD"), cancellable = true, require = 0)
    private void tabtailor$notInTailoredTabs(CreativeModeTab tab, CallbackInfoReturnable<Boolean> cir) {
        if (ForeignSections.configured(tab)) {
            cir.setReturnValue(false);
        }
    }
}
