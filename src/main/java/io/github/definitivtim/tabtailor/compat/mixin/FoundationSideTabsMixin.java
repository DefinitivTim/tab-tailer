package io.github.definitivtim.tabtailor.compat.mixin;

import io.github.definitivtim.tabtailor.compat.ForeignSections;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The Let's Do mods (Furniture, BloomingNature, ...) share a library ("foundation") that adds its own side tabs to
 * their creative tabs and filters the grid by them. In tabs that Tab Tailor has a file for, both would fight over the
 * same place and the same items, so the Let's Do side tabs are switched off there.
 */
@Pseudo
@Mixin(targets = "net.satisfy.foundation.client.creative.CreativeSideTabs", remap = false)
public abstract class FoundationSideTabsMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0)
    private static void tabtailor$hideWhenSectioned(CreativeModeTab tab, CallbackInfoReturnable<List<?>> cir) {
        if (ForeignSections.configured(tab)) {
            cir.setReturnValue(List.of());
        }
    }
}
