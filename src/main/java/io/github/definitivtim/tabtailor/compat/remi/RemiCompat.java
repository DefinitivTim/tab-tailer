package io.github.definitivtim.tabtailor.compat.remi;

import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.core.TabOrdering;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Glue for REMI (EMI addon with a creative tab browser). Nothing here runs when REMI is not installed. */
public final class RemiCompat {
    private static final String MANAGER = "com.evandev.remi.feature.creativemodetab.CreativeModeTabManager";

    private RemiCompat() {
    }

    /** REMI's tab list in Tab Tailor's order, without hidden tabs. Special tabs (index, search) stay in front. */
    public static List<CreativeModeTab> order(List<CreativeModeTab> remiTabs) {
        if (remiTabs == null) {
            return null;
        }
        try {
            Set<CreativeModeTab> present = Collections.newSetFromMap(new IdentityHashMap<>());
            present.addAll(remiTabs);
            List<CreativeModeTab> out = new ArrayList<>(remiTabs.size());
            for (CreativeModeTab tab : remiTabs) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                    out.add(tab);
                }
            }
            Set<CreativeModeTab> added = Collections.newSetFromMap(new IdentityHashMap<>());
            for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                if (present.contains(tab) && tab.getType() == CreativeModeTab.Type.CATEGORY && tab.hasAnyItems() && added.add(tab)) {
                    out.add(tab);
                }
            }
            for (CreativeModeTab tab : remiTabs) {
                if (tab.getType() == CreativeModeTab.Type.CATEGORY && !added.contains(tab)
                        && !TabOrdering.isHidden(tab, ConfigManager.get()) && tab.hasAnyItems()) {
                    out.add(tab);
                }
            }
            return out;
        } catch (Throwable t) {
            TabTailor.LOGGER.warn("[Tab Tailor] Could not reorder REMI tabs", t);
            return remiTabs;
        }
    }

    /** REMI caches tab contents; after Tab Tailor rebuilt the tabs it has to read them again. */
    public static void reloadLater() {
        if (!ModList.get().isLoaded("remi")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            try {
                Class.forName(MANAGER).getMethod("reload").invoke(null);
            } catch (Throwable t) {
                TabTailor.LOGGER.debug("[Tab Tailor] REMI reload failed", t);
            }
        });
    }
}
