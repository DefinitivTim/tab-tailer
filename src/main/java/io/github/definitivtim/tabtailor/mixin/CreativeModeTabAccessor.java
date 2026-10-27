package io.github.definitivtim.tabtailor.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;
import java.util.Set;

@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor("displayItems")
    void tabtailor$setDisplayItems(Collection<ItemStack> items);

    /** The mod's own title, without Tab Tailor's override. */
    @Accessor("displayName")
    Component tabtailor$getRawDisplayName();

    @Accessor("displayItemsSearchTab")
    void tabtailor$setDisplayItemsSearchTab(Set<ItemStack> items);
}
