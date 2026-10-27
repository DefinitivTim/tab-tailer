package io.github.definitivtim.tabtailor.config;

import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

/**
 * Look of a section header. Every field may be null, which means "inherit": section style falls back
 * to the tab's "section_style", which falls back to the global "section_style" in tabtailor.json.
 */
public record SectionStyle(
        @Nullable Integer background,
        @Nullable Integer color,
        @Nullable Integer line,
        @Nullable ResourceLocation sprite,
        @Nullable Boolean shadow,
        @Nullable String image,
        @Nullable Integer imageFrames,
        @Nullable Integer imageFrametime,
        @Nullable Boolean imageHoverOnly) {

    public static final SectionStyle EMPTY = new SectionStyle(null, null, null, null, null, null, null, null, null);

    /** Built-in defaults, used when nothing else is configured. */
    public static final SectionStyle DEFAULT = new SectionStyle(0xAA2B2B2B, 0xFFF0F0F0, 0x00000000, null, true, null, null, null, null);

    /** Fills every unset field of this style with the value from {@code parent}. */
    public SectionStyle inherit(SectionStyle parent) {
        return new SectionStyle(
                background != null ? background : parent.background,
                color != null ? color : parent.color,
                line != null ? line : parent.line,
                sprite != null ? sprite : parent.sprite,
                shadow != null ? shadow : parent.shadow,
                image != null ? image : parent.image,
                imageFrames != null ? imageFrames : parent.imageFrames,
                imageFrametime != null ? imageFrametime : parent.imageFrametime,
                imageHoverOnly != null ? imageHoverOnly : parent.imageHoverOnly);
    }

    public int backgroundOrDefault() {
        return background != null ? background : DEFAULT.background;
    }

    public int colorOrDefault() {
        return color != null ? color : DEFAULT.color;
    }

    public int lineOrDefault() {
        return line != null ? line : DEFAULT.line;
    }

    public boolean shadowOrDefault() {
        return shadow != null ? shadow : DEFAULT.shadow;
    }
}
