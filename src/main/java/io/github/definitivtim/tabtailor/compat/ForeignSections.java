package io.github.definitivtim.tabtailor.compat;

import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.mixin.CreativeModeInventoryScreenAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Some mods (Create Simulated / Aeronautics, Create Propulsion, Create Flight Control) draw their own section
 * banners. They compute the banner rows from their own idea of the tab contents, so once Tab Tailor changes the
 * contents of such a tab their banners land on top of items. In that case their sections are switched off and
 * Tab Tailor's sections are used instead.
 */
public final class ForeignSections {
    private ForeignSections() {
    }

    /** True when the creative tab currently shown has its contents changed by Tab Tailor. */
    public static boolean suppressed() {
        try {
            CreativeModeTab tab = CreativeModeInventoryScreenAccessor.tabtailor$getSelectedTab();
            return changesContents(tab);
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when Tab Tailor has a file for this tab: then side bars of other mods are switched off there. */
    public static boolean configured(CreativeModeTab tab) {
        if (tab == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id != null && ConfigManager.get().tab(id) != null;
    }

    public static boolean changesContents(CreativeModeTab tab) {
        if (tab == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        TabDefinition def = id == null ? null : ConfigManager.get().tab(id);
        return def != null && (def.sections() != null || !def.add().isEmpty() || !def.remove().isEmpty());
    }
}
