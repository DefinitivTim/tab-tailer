package io.github.definitivtim.tabtailor.mixin;

import io.github.definitivtim.tabtailor.core.TabOrdering;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** The creative screen builds its tab pages from this list, so order and hiding are applied here. */
@Mixin(CreativeModeTabRegistry.class)
public abstract class CreativeModeTabRegistryMixin {
    @Inject(method = "getSortedCreativeModeTabs", at = @At("RETURN"), cancellable = true)
    private static void tabtailor$applyOrder(CallbackInfoReturnable<List<CreativeModeTab>> cir) {
        cir.setReturnValue(TabOrdering.apply(cir.getReturnValue()));
    }
}
