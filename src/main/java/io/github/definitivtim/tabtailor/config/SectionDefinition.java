package io.github.definitivtim.tabtailor.config;

import io.github.definitivtim.tabtailor.core.Selector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

import java.util.List;

/**
 * One section of a tab. A section without title has no header row (used for the "items" shorthand).
 */
public record SectionDefinition(
        @Nullable Component title,
        @Nullable ResourceLocation icon,
        SectionStyle style,
        List<Selector> items,
        List<Selector> exclude) {
}
