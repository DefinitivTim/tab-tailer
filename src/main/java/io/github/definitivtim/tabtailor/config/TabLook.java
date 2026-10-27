package io.github.definitivtim.tabtailor.config;

import javax.annotation.Nullable;

/**
 * Look of the creative window for a tab ("tab_style"). Fields may be null ("inherit"): a tab's "tab_style" falls
 * back to the one in tabtailor.json, which falls back to {@link #DEFAULT} (plain vanilla).
 *
 * @param background "vanilla", "mod" (keep what the mod set), a PNG in config/tabtailor/images/ or a texture ID.
 *                   Same 256 x 256 layout as minecraft:textures/gui/container/creative_inventory/tab_items.png
 * @param labelColor color of the tab title, null = vanilla
 * @param slotHighlight color drawn over a slot under the mouse, null = vanilla
 * @param scroller "vanilla", "mod" or a GUI sprite ID for the scroll bar handle
 */
public record TabLook(
        @Nullable String background,
        @Nullable Integer labelColor,
        @Nullable Integer slotHighlight,
        @Nullable String scroller,
        @Nullable Boolean keepModColors) {

    public static final int VANILLA_LABEL = 0xFF404040;
    public static final int VANILLA_SLOT_HIGHLIGHT = 0x80FFFFFF;

    public static final TabLook EMPTY = new TabLook(null, null, null, null, null);
    public static final TabLook DEFAULT = new TabLook("vanilla", null, null, "vanilla", false);

    public TabLook inherit(TabLook parent) {
        return new TabLook(
                background != null ? background : parent.background,
                labelColor != null ? labelColor : parent.labelColor,
                slotHighlight != null ? slotHighlight : parent.slotHighlight,
                scroller != null ? scroller : parent.scroller,
                keepModColors != null ? keepModColors : parent.keepModColors);
    }
}
