package io.github.definitivtim.tabtailor.mixin;

import io.github.definitivtim.tabtailor.client.TabLooks;
import io.github.definitivtim.tabtailor.core.TabOverrides;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Title and icon overrides from the tab files. */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void tabtailor$overrideTitle(CallbackInfoReturnable<Component> cir) {
        Component title = TabOverrides.title((CreativeModeTab) (Object) this);
        if (title != null) {
            cir.setReturnValue(title);
        }
    }

    @Inject(method = "getBackgroundTexture", at = @At("HEAD"), cancellable = true)
    private void tabtailor$background(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture = TabLooks.background((CreativeModeTab) (Object) this);
        if (texture != null) {
            cir.setReturnValue(texture);
        }
    }

    @Inject(method = "getLabelColor", at = @At("HEAD"), cancellable = true)
    private void tabtailor$labelColor(CallbackInfoReturnable<Integer> cir) {
        Integer color = TabLooks.labelColor((CreativeModeTab) (Object) this);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }

    @Inject(method = "getSlotColor", at = @At("HEAD"), cancellable = true)
    private void tabtailor$slotColor(CallbackInfoReturnable<Integer> cir) {
        Integer color = TabLooks.slotHighlight((CreativeModeTab) (Object) this);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }

    @Inject(method = "getScrollerSprite", at = @At("HEAD"), cancellable = true)
    private void tabtailor$scroller(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation sprite = TabLooks.scroller((CreativeModeTab) (Object) this);
        if (sprite != null) {
            cir.setReturnValue(sprite);
        }
    }

    @Inject(method = "getIconItem", at = @At("HEAD"), cancellable = true)
    private void tabtailor$overrideIcon(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack icon = TabOverrides.icon((CreativeModeTab) (Object) this);
        if (icon != null) {
            cir.setReturnValue(icon);
        }
    }
}
