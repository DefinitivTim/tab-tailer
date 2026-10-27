package io.github.definitivtim.tabtailor.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CreativeModeTabRegistry.class)
public interface CreativeModeTabRegistryAccessor {
    /** NeoForge's sorted tab list without Tab Tailor's reordering and hiding. */
    @Accessor("SORTED_TABS_VIEW")
    static List<CreativeModeTab> tabtailor$getSortedTabs() {
        throw new AssertionError();
    }
}
