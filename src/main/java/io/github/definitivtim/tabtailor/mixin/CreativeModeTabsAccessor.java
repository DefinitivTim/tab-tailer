package io.github.definitivtim.tabtailor.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    /** Setting this to null makes Minecraft rebuild all tab contents the next time they are needed. */
    @Accessor("CACHED_PARAMETERS")
    static void tabtailor$setCachedParameters(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new AssertionError();
    }
}
