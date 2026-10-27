package io.github.definitivtim.tabtailor.config;

import javax.annotation.Nullable;

/**
 * Colors of the section bar on the left side. Every field may be null ("inherit"): a tab's "sidebar" falls back to
 * the "sidebar" in tabtailor.json, which falls back to {@link #DEFAULT}. Light and dark bevel edges are derived from
 * these colors automatically, so any color still looks like a Minecraft GUI element.
 *
 * @param background       frame of the bar
 * @param slot             a normal section slot
 * @param backgroundActive the slot of the section you are currently in
 * @param borderActive     frame and side notches of the active slot
 * @param button           arrow button while there is nothing to scroll in that direction
 * @param buttonActive     arrow button while it can scroll
 * @param arrow            arrow while there is nothing to scroll in that direction
 * @param arrowActive      arrow while it can scroll
 */
public record SidebarStyle(
        @Nullable Integer background,
        @Nullable Integer slot,
        @Nullable Integer backgroundActive,
        @Nullable Integer borderActive,
        @Nullable Integer button,
        @Nullable Integer buttonActive,
        @Nullable Integer arrow,
        @Nullable Integer arrowActive) {

    public static final SidebarStyle EMPTY = new SidebarStyle(null, null, null, null, null, null, null, null);

    public static final SidebarStyle DEFAULT = new SidebarStyle(
            0xFFC6C6C6, // background
            0xFF8B8B8B, // slot
            0xFFE6E6E6, // background_active
            0xFFE08A00, // border_active (amber orange)
            0xFF8B8B8B, // button
            0xFFA6A6A6, // button_active
            0xFF6F6F6F, // arrow
            0xFFE08A00); // arrow_active

    public SidebarStyle inherit(SidebarStyle parent) {
        return new SidebarStyle(
                background != null ? background : parent.background,
                slot != null ? slot : parent.slot,
                backgroundActive != null ? backgroundActive : parent.backgroundActive,
                borderActive != null ? borderActive : parent.borderActive,
                button != null ? button : parent.button,
                buttonActive != null ? buttonActive : parent.buttonActive,
                arrow != null ? arrow : parent.arrow,
                arrowActive != null ? arrowActive : parent.arrowActive);
    }

    /** Fully resolved against {@link #DEFAULT}. */
    public SidebarStyle resolved() {
        return inherit(DEFAULT);
    }
}
