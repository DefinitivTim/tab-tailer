package io.github.definitivtim.tabtailor.core;

import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies "tab_order" and "hidden_tabs" to NeoForge's sorted tab list. That list never contains the special
 * tabs (search, hotbar, inventory, operator), NeoForge adds those to every page itself.
 */
public final class TabOrdering {
    private TabOrdering() {
    }

    public static List<CreativeModeTab> apply(List<CreativeModeTab> sorted) {
        TabTailorConfig config = ConfigManager.get();
        if (config.tabOrder().isEmpty() && config.hiddenTabs().isEmpty()) {
            return sorted;
        }
        List<CreativeModeTab> visible = new ArrayList<>(sorted.size());
        for (CreativeModeTab tab : sorted) {
            if (!isHidden(tab, config)) {
                visible.add(tab);
            }
        }
        return order(visible, config);
    }

    /** All given tabs (hidden ones too) in "tab_order" order. */
    public static List<CreativeModeTab> order(List<CreativeModeTab> visible, TabTailorConfig config) {

        Set<CreativeModeTab> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        List<CreativeModeTab> ordered = new ArrayList<>(visible.size());
        int restIndex = -1;
        for (String pattern : config.tabOrder()) {
            if (pattern.trim().equals("*")) {
                if (restIndex < 0) {
                    restIndex = ordered.size();
                }
                continue;
            }
            for (CreativeModeTab tab : visible) {
                if (!placed.contains(tab) && matches(pattern, tab)) {
                    placed.add(tab);
                    ordered.add(tab);
                }
            }
        }
        List<CreativeModeTab> rest = new ArrayList<>();
        for (CreativeModeTab tab : visible) {
            if (!placed.contains(tab)) {
                rest.add(tab);
            }
        }
        if (restIndex < 0) {
            ordered.addAll(rest);
        } else {
            ordered.addAll(restIndex, rest);
        }
        return Collections.unmodifiableList(ordered);
    }

    public static boolean isHidden(CreativeModeTab tab, TabTailorConfig config) {
        return !config.showHiddenTabs() && inHiddenList(tab, config);
    }

    /** Listed in "hidden_tabs", no matter whether "show_hidden_tabs" is on. */
    public static boolean inHiddenList(CreativeModeTab tab, TabTailorConfig config) {
        for (String pattern : config.hiddenTabs()) {
            if (matches(pattern, tab)) {
                return true;
            }
        }
        return false;
    }

    /** "create:base", "@create" (whole mod) or wildcards like "create:*". */
    public static boolean matches(String rawPattern, CreativeModeTab tab) {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (id == null) {
            return false;
        }
        String pattern = rawPattern.trim();
        if (pattern.startsWith("@")) {
            return glob(pattern.substring(1)).matcher(id.getNamespace()).matches();
        }
        if (!pattern.contains(":")) {
            pattern = "minecraft:" + pattern;
        }
        if (pattern.contains("*")) {
            return glob(pattern).matcher(id.toString()).matches();
        }
        return id.toString().equals(pattern);
    }

    private static Pattern glob(String text) {
        StringBuilder regex = new StringBuilder();
        for (String part : text.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }
}
