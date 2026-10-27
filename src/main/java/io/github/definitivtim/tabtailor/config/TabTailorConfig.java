package io.github.definitivtim.tabtailor.config;

import io.github.definitivtim.tabtailor.core.Selector;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;

/** Immutable snapshot of the whole configuration. Replaced as a whole on reload. */
public record TabTailorConfig(
        List<String> tabOrder,
        List<String> hiddenTabs,
        boolean showHiddenTabs,
        List<Selector> hiddenItems,
        SectionStyle sectionStyle,
        boolean sectionTabs,
        SidebarStyle sidebarStyle,
        TabLook tabLook,
        List<TabDefinition> tabs,
        Map<ResourceLocation, TabDefinition> tabsById,
        List<String> problems,
        int generation) {

    public static final TabTailorConfig EMPTY = new TabTailorConfig(
            List.of(), List.of(), false, List.of(), SectionStyle.DEFAULT, true, SidebarStyle.DEFAULT, TabLook.DEFAULT, List.of(), Map.of(), List.of(), 0);

    public @Nullable TabDefinition tab(ResourceLocation id) {
        return tabsById.get(id);
    }
}
