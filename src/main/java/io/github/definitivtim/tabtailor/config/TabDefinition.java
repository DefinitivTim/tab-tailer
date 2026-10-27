package io.github.definitivtim.tabtailor.config;

import io.github.definitivtim.tabtailor.core.Selector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

import java.util.List;

/**
 * Everything one file in config/tabtailor/tabs/ says about one creative tab.
 *
 * @param sections null means "keep the tab's normal contents" (then {@code add} and {@code remove} apply)
 */
public record TabDefinition(
        String sourceFile,
        ResourceLocation id,
        boolean newTab,
        @Nullable Component title,
        @Nullable ResourceLocation icon,
        boolean exclusive,
        @Nullable List<SectionDefinition> sections,
        List<Selector> add,
        List<Selector> remove,
        SectionStyle sectionStyle,
        SidebarStyle sidebarStyle,
        TabLook tabLook) {
}
