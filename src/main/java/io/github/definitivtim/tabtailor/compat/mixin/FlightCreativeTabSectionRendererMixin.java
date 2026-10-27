package io.github.definitivtim.tabtailor.compat.mixin;

import io.github.definitivtim.tabtailor.compat.ForeignSections;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create Flight Control section banners: off in tabs whose contents Tab Tailor changes. */
@Pseudo
@Mixin(targets = "ace.flight.neoforge.client.FlightCreativeTabSectionRenderer", remap = false)
public abstract class FlightCreativeTabSectionRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tabtailor$skipBanners(CreativeModeInventoryScreen screen, GuiGraphics graphics, CallbackInfo ci) {
        if (ForeignSections.suppressed()) {
            ci.cancel();
        }
    }

    @Inject(method = "isSectionHeaderSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tabtailor$noHeaderSlots(int x, int y, CallbackInfoReturnable<Boolean> cir) {
        if (ForeignSections.suppressed()) {
            cir.setReturnValue(false);
        }
    }
}
