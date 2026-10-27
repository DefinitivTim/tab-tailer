package io.github.definitivtim.tabtailor.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface ItemPickerMenuAccessor {
    @Invoker("getRowIndexForScroll")
    int tabtailor$getRowIndexForScroll(float scroll);

    @Invoker("getScrollForRowIndex")
    float tabtailor$getScrollForRowIndex(int row);
}
