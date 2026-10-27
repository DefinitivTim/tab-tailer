package io.github.definitivtim.tabtailor.compat.mixin;

import io.github.definitivtim.tabtailor.compat.ForeignSections;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Create Propulsion section banners: off in tabs whose contents Tab Tailor changes. */
@Pseudo
@Mixin(targets = "dev.propulsionteam.propulsionsimulated.client.PropulsionCreativeTabRenderer", remap = false)
public abstract class PropulsionCreativeTabRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tabtailor$skipBanners(CreativeModeInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (ForeignSections.suppressed()) {
            ci.cancel();
        }
    }
}
