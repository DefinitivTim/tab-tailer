package io.github.definitivtim.tabtailor.mixin;

import io.github.definitivtim.tabtailor.core.TabContentProcessor;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * buildAllTabContents first builds every normal tab, then the special tabs (search reads the others).
 * We hook in between, so the search tab already sees the new contents.
 */
@Mixin(CreativeModeTabs.class)
public abstract class CreativeModeTabsMixin {
    @Inject(
            method = "buildAllTabContents",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private static void tabtailor$afterCategoryTabs(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        TabContentProcessor.process(parameters);
    }
}
