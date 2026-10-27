package io.github.definitivtim.tabtailor.compat.mixin;

import io.github.definitivtim.tabtailor.compat.remi.RemiCompat;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** REMI shows the creative tabs inside EMI. Make it use Tab Tailor's order and hidden tabs. */
@Pseudo
@Mixin(targets = "com.evandev.remi.feature.creativemodetab.CreativeModeTabManager", remap = false)
public abstract class RemiCreativeModeTabManagerMixin {
    @Inject(method = "getVisibleCreativeModeTabs", at = @At("RETURN"), cancellable = true, require = 0)
    private static void tabtailor$applyOrder(CallbackInfoReturnable<List<CreativeModeTab>> cir) {
        cir.setReturnValue(RemiCompat.order(cir.getReturnValue()));
    }
}
