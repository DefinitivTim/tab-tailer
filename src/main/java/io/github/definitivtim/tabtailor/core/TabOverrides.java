package io.github.definitivtim.tabtailor.core;

import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/** Title and icon overrides. Looked up often (every frame), so results are cached per tab. */
public final class TabOverrides {
    private record Entry(@Nullable Component title, @Nullable ItemStack icon, boolean newTabWithoutIcon) {
    }

    private static final Entry NONE = new Entry(null, null, false);
    private static final Map<CreativeModeTab, Entry> CACHE = new IdentityHashMap<>();
    private static int cachedGeneration = -1;

    private TabOverrides() {
    }

    public static @Nullable Component title(CreativeModeTab tab) {
        return entry(tab).title();
    }

    public static @Nullable ItemStack icon(CreativeModeTab tab) {
        Entry entry = entry(tab);
        if (entry.icon() != null) {
            return entry.icon();
        }
        if (entry.newTabWithoutIcon()) {
            // New tabs without "icon" show their first item.
            for (ItemStack stack : tab.getDisplayItems()) {
                return stack;
            }
        }
        return null;
    }

    private static synchronized Entry entry(CreativeModeTab tab) {
        TabTailorConfig config = ConfigManager.get();
        if (config.generation() != cachedGeneration) {
            CACHE.clear();
            cachedGeneration = config.generation();
        }
        Entry entry = CACHE.get(tab);
        if (entry == null) {
            entry = compute(tab, config);
            CACHE.put(tab, entry);
        }
        return entry;
    }

    private static Entry compute(CreativeModeTab tab, TabTailorConfig config) {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (id == null) {
            return NONE;
        }
        TabDefinition def = config.tab(id);
        if (def == null) {
            return NONE;
        }
        ItemStack icon = null;
        if (def.icon() != null) {
            icon = BuiltInRegistries.ITEM.getOptional(def.icon()).map(ItemStack::new).filter(s -> !s.isEmpty()).orElse(null);
        }
        return new Entry(def.title(), icon, def.newTab() && icon == null);
    }
}
