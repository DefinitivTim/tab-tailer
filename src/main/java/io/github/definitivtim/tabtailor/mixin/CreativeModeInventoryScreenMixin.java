package io.github.definitivtim.tabtailor.mixin;

import io.github.definitivtim.tabtailor.client.SectionScreenHelper;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.core.TabOrdering;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Sections: pads the item grid, draws the header rows and the section tabs on the left side. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    @Final
    static SimpleContainer CONTAINER;

    @Shadow
    private float scrollOffs;

    @Shadow
    private CreativeTabsScreenPage currentPage;

    @Shadow
    private void selectTab(CreativeModeTab tab) {
        throw new AssertionError();
    }

    private CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** If the remembered tab got hidden, open the first visible tab instead. */
    @Inject(method = "init", at = @At("TAIL"))
    private void tabtailor$skipHiddenTab(CallbackInfo ci) {
        if (selectedTab != null && selectedTab.getType() == CreativeModeTab.Type.CATEGORY
                && TabOrdering.isHidden(selectedTab, ConfigManager.get()) && this.currentPage != null) {
            CreativeModeTab fallback = this.currentPage.getDefaultTab();
            if (fallback != null) {
                this.selectTab(fallback);
            }
        }
    }

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void tabtailor$padAfterSelect(CreativeModeTab tab, CallbackInfo ci) {
        SectionScreenHelper.pad(this.menu, selectedTab, this.scrollOffs);
    }

    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"))
    private void tabtailor$padAfterRefresh(Collection<ItemStack> items, CallbackInfo ci) {
        SectionScreenHelper.pad(this.menu, selectedTab, this.scrollOffs);
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void tabtailor$renderHeaders(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        SectionScreenHelper.renderHeaders(graphics, this.font, this.menu, selectedTab, this.scrollOffs,
                mouseX, mouseY, this.leftPos, this.topPos);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void tabtailor$renderSideTabs(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        SectionScreenHelper.ensurePadded(this.menu, selectedTab, this.scrollOffs);
        SectionScreenHelper.renderSideTabs(graphics, this.menu, selectedTab, this.scrollOffs,
                this.leftPos, this.topPos, this.height, mouseX, mouseY);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tabtailor$renderSideTabTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SectionScreenHelper.renderSideTabTooltip(graphics, this.font, this.menu, selectedTab,
                this.leftPos, this.topPos, this.height, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tabtailor$clickSideTab(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            return;
        }
        float scroll = SectionScreenHelper.clickSideTab(this.menu, selectedTab, this.leftPos, this.topPos, this.height, mouseX, mouseY, this.scrollOffs);
        if (!Float.isNaN(scroll)) {
            this.scrollOffs = scroll;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void tabtailor$scrollSideTabs(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (SectionScreenHelper.scrollSideTabs(this.menu, selectedTab, this.leftPos, this.topPos, this.height, mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void tabtailor$sideTabsAreInside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        if (SectionScreenHelper.isOverSideTabs(this.menu, selectedTab, this.leftPos, this.topPos, this.height, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void tabtailor$closed(CallbackInfo ci) {
        SectionScreenHelper.screenClosed();
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void tabtailor$ignoreHeaderClicks(Slot slot, int slotId, int mouseButton, ClickType type, CallbackInfo ci) {
        if (slot != null && slot.container == CONTAINER
                && SectionScreenHelper.isHeaderSlot(slot.getContainerSlot(), this.menu, selectedTab, this.scrollOffs)) {
            ci.cancel();
        }
    }
}
