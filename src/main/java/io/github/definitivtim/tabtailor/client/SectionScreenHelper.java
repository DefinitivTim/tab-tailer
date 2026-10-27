package io.github.definitivtim.tabtailor.client;

import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.SectionStyle;
import io.github.definitivtim.tabtailor.config.SidebarStyle;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import io.github.definitivtim.tabtailor.core.SectionLayout;
import io.github.definitivtim.tabtailor.mixin.ItemPickerMenuAccessor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Screen side of sections: inserts empty header rows into the item grid, draws the titles on them and shows one
 * small tab per section on the left side of the creative window to jump there.
 */
public final class SectionScreenHelper {
    private static final int VISIBLE_ROWS = 5;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 17;
    private static final int CELL = 18;
    private static final int WIDTH = SectionLayout.COLUMNS * CELL;

    /** Padding of the grid currently shown (only one creative screen exists at a time). */
    private static @Nullable SectionLayout.Padded current;
    private static @Nullable CreativeModeTab currentTab;
    /** The layout {@link #current} was padded with; after /tabtailor reload the tab gets a new one. */
    private static @Nullable SectionLayout currentLayout;
    private static int sideScroll;
    private static int lastActiveSection = -1;
    private static @Nullable SectionLayout.Padded lastActiveLayout;
    /**
     * Section the player clicked last, and the grid scroll that click produced. Near the end of a tab the grid
     * cannot scroll far enough to put the clicked header at the top, so "top row" alone would mark the section above.
     * The clicked one stays marked until the grid is scrolled by other means.
     */
    private static int pinnedSection = -1;
    private static float pinnedScroll;
    private static @Nullable SectionLayout.Padded pinnedLayout;
    /** Side tab area of the last frame in screen coordinates, read by the EMI plugin. */
    private static volatile @Nullable int[] lastSideTabArea;

    private SectionScreenHelper() {
    }

    private static final java.util.Set<CreativeModeTab> PAD_LOGGED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static final class ForeignSectionsHolder {
        static boolean wantsSections(CreativeModeTab tab) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            TabDefinition def = id == null ? null : ConfigManager.get().tab(id);
            return def != null && def.sections() != null && def.sections().size() >= 2;
        }
    }

    private static long repairWindowStart;
    private static int repairsInWindow;
    private static boolean repairWarned;
    private static final java.util.Set<CreativeModeTab> REPAIR_LOGGED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Called every frame before the grid is drawn. If the grid of a sectioned tab is not (or no longer) in Tab Tailor's
     * layout, for example because another mod refilled or filtered it after Tab Tailor padded it, pad it again from
     * whatever it holds now. Without this the headers and the side bar silently disappear.
     */
    public static void ensurePadded(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab, float scroll) {
        SectionLayout layout = SectionLayout.get(tab);
        if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY || layout == null) {
            return;
        }
        SectionLayout.Padded padded = current;
        if (padded != null && tab == currentTab && layout == currentLayout && menu.items.size() == padded.items().size()) {
            return;
        }
        long now = Util.getMillis();
        if (now - repairWindowStart > 1000L) {
            repairWindowStart = now;
            repairsInWindow = 0;
        }
        if (++repairsInWindow > 5) {
            // another mod changes the grid every frame: stop fighting it
            if (!repairWarned) {
                repairWarned = true;
                io.github.definitivtim.tabtailor.TabTailor.LOGGER.warn("[Tab Tailor] Another mod keeps changing the items of tab {}, sections are skipped there",
                        BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
            }
            return;
        }
        if (REPAIR_LOGGED.add(tab)) io.github.definitivtim.tabtailor.TabTailor.LOGGER.info("[Tab Tailor] Grid of {} was changed outside Tab Tailor ({} items, expected {}), padding again (padded: {}, same tab: {}, same layout: {})",
                BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), menu.items.size(), padded == null ? -1 : padded.items().size(),
                padded != null, tab == currentTab, layout == currentLayout);
        currentTab = tab; // keep the side bar position
        pad(menu, tab, scroll);
    }

    private static @Nullable SectionLayout.Padded active(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab) {
        SectionLayout.Padded padded = current;
        if (padded == null || tab == null || tab != currentTab || tab.getType() != CreativeModeTab.Type.CATEGORY
                || menu.items.size() != padded.items().size()) {
            return null;
        }
        return padded;
    }

    /** Called after the screen filled the grid with the tab's items. */
    public static void pad(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab, float scroll) {
        if (tab != currentTab) {
            sideScroll = 0;
        }
        current = null;
        currentTab = tab;
        if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
            return;
        }
        SectionLayout layout = SectionLayout.get(tab);
        if (layout == null) {
            if (ForeignSectionsHolder.wantsSections(tab) && PAD_LOGGED.add(tab)) {
                io.github.definitivtim.tabtailor.TabTailor.LOGGER.info("[Tab Tailor] Tab {} has sections in its file but no layout (layouts: {})",
                        BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), SectionLayout.count());
            }
            return;
        }
        currentLayout = layout;
        menu.items.removeIf(net.minecraft.world.item.ItemStack::isEmpty); // never pad an already padded grid twice
        SectionLayout.Padded padded = layout.pad(List.copyOf(menu.items));
        menu.items.clear();
        menu.items.addAll(padded.items());
        current = padded;
        if (padded.sections().size() < 2 && ForeignSectionsHolder.wantsSections(tab) && PAD_LOGGED.add(tab)) {
            io.github.definitivtim.tabtailor.TabTailor.LOGGER.info("[Tab Tailor] Tab {} padded into {} section(s) from {} stacks",
                    BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), padded.sections().size(), padded.items().size());
        }
        menu.scrollTo(scroll);
    }

    public static boolean isHeaderSlot(int containerSlot, CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab, float scroll) {
        SectionLayout.Padded padded = active(menu, tab);
        if (padded == null || containerSlot < 0 || containerSlot >= VISIBLE_ROWS * SectionLayout.COLUMNS) {
            return false;
        }
        int firstRow = ((ItemPickerMenuAccessor) menu).tabtailor$getRowIndexForScroll(scroll);
        return padded.headerRows().containsKey(firstRow + containerSlot / SectionLayout.COLUMNS);
    }

    // ------------------------------------------------------------------ header rows

    /** Runs inside renderLabels, so (0, 0) is the top left corner of the creative window. */
    public static void renderHeaders(GuiGraphics graphics, Font font, CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab, float scroll,
                                     int mouseX, int mouseY, int leftPos, int topPos) {
        SectionLayout.Padded padded = active(menu, tab);
        if (padded == null) {
            return;
        }
        int firstRow = ((ItemPickerMenuAccessor) menu).tabtailor$getRowIndexForScroll(scroll);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        for (int visibleRow = 0; visibleRow < VISIBLE_ROWS; visibleRow++) {
            SectionLayout.Header header = padded.headerRows().get(firstRow + visibleRow);
            if (header != null) {
                int hy = GRID_Y + visibleRow * CELL;
                int mx = mouseX - leftPos;
                int my = mouseY - topPos;
                boolean hovered = mx >= GRID_X && mx < GRID_X + WIDTH && my >= hy && my < hy + CELL;
                drawHeader(graphics, font, header, GRID_X, hy, hovered);
            }
        }
        graphics.pose().popPose();
    }

    /** One header row, 162 x 18. Public for the style editor preview. */
    public static void drawHeader(GuiGraphics graphics, Font font, SectionLayout.Header header, int x, int y, boolean hovered) {
        SectionStyle style = header.style();
        HeaderImages.Image image = style.image() != null ? HeaderImages.get(style.image()) : null;
        if (image != null) {
            RenderSystem.enableBlend();
            int frames = image.frames(style.imageFrames());
            int frameHeight = Math.max(1, image.height() / frames);
            int frame = 0;
            boolean animate = frames > 1 && (!Boolean.TRUE.equals(style.imageHoverOnly()) || hovered);
            if (animate) {
                int frametime = style.imageFrametime() != null ? style.imageFrametime()
                        : (image.metaFrametime() > 0 ? image.metaFrametime() : 2);
                frame = (int) ((Util.getMillis() / 50L / Math.max(1, frametime)) % frames);
            }
            graphics.blit(image.texture(), x, y, WIDTH, CELL, 0.0F, (float) (frame * frameHeight),
                    image.width(), frameHeight, image.width(), image.height());
            RenderSystem.disableBlend();
        } else if (style.sprite() != null) {
            graphics.blitSprite(style.sprite(), x, y, WIDTH, CELL);
        } else {
            graphics.fill(x, y, x + WIDTH, y + CELL, style.backgroundOrDefault());
        }
        int line = style.lineOrDefault();
        if ((line >>> 24) != 0) {
            graphics.fill(x, y + CELL - 1, x + WIDTH, y + CELL, line);
        }

        int textX = x + 5;
        if (!header.icon().isEmpty()) {
            graphics.renderItem(header.icon(), x + 1, y + 1);
            textX = x + 20;
        }
        int maxWidth = x + WIDTH - 4 - textX;
        FormattedText text = header.title();
        if (font.width(text) > maxWidth) {
            text = FormattedText.composite(font.substrByWidth(text, maxWidth - font.width("...")), FormattedText.of("..."));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F); // above the icon
        graphics.drawString(font, Language.getInstance().getVisualOrder(text), textX, y + 5, style.colorOrDefault(), style.shadowOrDefault());
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------ side bar

    /*
     * A gray bar left of the creative window with one slot per section (style inspired by Vanilla Backport's
     * bundled tabs). Arrow buttons appear when not all sections fit; they get a orange arrow while there
     * is something to scroll to in that direction.
     */
    private static final int BAR_W = 24;
    private static final int BAR_GAP = 2;          // space between bar and window
    private static final int BAR_TOP = 2;          // offset from the top of the window
    private static final int SLOT = 18;
    private static final int SLOT_STEP = 19;
    private static final int BUTTON_H = 8;
    private static final int MAX_VISIBLE = 5;
    private static final int UP = -2;
    private static final int DOWN = -3;

    private static final int C_OUTLINE = 0xFF000000;
    private static final int ACTIVE_OUT = 2;        // how far the active slot sticks out of the bar on each side

    /** Mixes a color towards white (amount > 0) or black (amount < 0), keeping its alpha. */
    private static int shade(int color, float amount) {
        int a = color >>> 24;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (amount >= 0) {
            r += (int) ((255 - r) * amount);
            g += (int) ((255 - g) * amount);
            b += (int) ((255 - b) * amount);
        } else {
            r = (int) (r * (1 + amount));
            g = (int) (g * (1 + amount));
            b = (int) (b * (1 + amount));
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Bar colors for a tab: its own "sidebar", then the one in tabtailor.json, then the defaults. */
    private static SidebarStyle sidebarStyle(@Nullable CreativeModeTab tab) {
        TabTailorConfig config = ConfigManager.get();
        SidebarStyle global = config.sidebarStyle();
        if (tab != null) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            TabDefinition def = id == null ? null : config.tab(id);
            if (def != null) {
                return def.sidebarStyle().inherit(global).resolved();
            }
        }
        return global.resolved();
    }

    /** Raised (light top-left) or sunken (dark top-left) bevel around a filled rectangle. */
    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int fill, boolean raised) {
        int light = shade(fill, 0.55F);
        int dark = shade(fill, -0.55F);
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w - 1, y + 1, raised ? light : dark);
        g.fill(x, y, x + 1, y + h - 1, raised ? light : dark);
        g.fill(x + 1, y + h - 1, x + w, y + h, raised ? dark : light);
        g.fill(x + w - 1, y + 1, x + w, y + h, raised ? dark : light);
    }

    /** Geometry of the bar for the current screen size and section count. */
    private record Bar(int x, int y, int height, int visible, boolean buttons, int first) {
        int contentX() {
            return x + 3;
        }

        int slotsY() {
            return y + 3 + (buttons ? BUTTON_H + 1 : 0);
        }

        int upY() {
            return y + 3;
        }

        int downY() {
            return slotsY() + visible * SLOT_STEP;
        }
    }

    private static @Nullable List<SectionLayout.PlacedSection> sideSections(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab) {
        if (!ConfigManager.get().sectionTabs()) {
            return null;
        }
        SectionLayout.Padded padded = active(menu, tab);
        if (padded == null || padded.sections().size() < 2) {
            return null; // a single section needs no navigation
        }
        return padded.sections();
    }

    private static int barHeight(int slots, boolean buttons) {
        return 3 + (buttons ? BUTTON_H + 1 : 0) + slots * SLOT_STEP - 1 + (buttons ? 1 + BUTTON_H : 0) + 3;
    }

    private static Bar bar(int count, int leftPos, int topPos, int screenHeight) {
        int y = topPos + BAR_TOP;
        int available = screenHeight - y - 4;
        int visible = Math.min(count, MAX_VISIBLE);
        while (visible > 1 && barHeight(visible, true) > available) {
            visible--;
        }
        sideScroll = Mth.clamp(sideScroll, 0, Math.max(0, count - visible));
        return new Bar(leftPos - BAR_GAP - BAR_W, y, barHeight(visible, true), visible, true, sideScroll);
    }

    /** Section index under the mouse, UP / DOWN for the arrow buttons, -1 for nothing. */
    private static int hit(Bar bar, int count, double mouseX, double mouseY) {
        int cx = bar.contentX();
        if (mouseX < cx || mouseX >= cx + SLOT) {
            return -1;
        }
        if (bar.buttons()) {
            if (mouseY >= bar.upY() && mouseY < bar.upY() + BUTTON_H) {
                return UP;
            }
            if (mouseY >= bar.downY() && mouseY < bar.downY() + BUTTON_H) {
                return DOWN;
            }
        }
        double rel = mouseY - bar.slotsY();
        if (rel < 0) {
            return -1;
        }
        int slot = (int) (rel / SLOT_STEP);
        if (slot >= bar.visible() || rel - slot * SLOT_STEP >= SLOT) {
            return -1;
        }
        int index = bar.first() + slot;
        return index < count ? index : -1;
    }

    private static boolean insideBar(Bar bar, double mouseX, double mouseY) {
        return mouseX >= bar.x() - ACTIVE_OUT && mouseX < bar.x() + BAR_W + ACTIVE_OUT
                && mouseY >= bar.y() && mouseY < bar.y() + bar.height();
    }

    /** The section the top visible row belongs to. */
    private static int currentSection(List<SectionLayout.PlacedSection> sections, int firstRow) {
        int result = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).headerRow() <= firstRow) {
                result = i;
            }
        }
        return result;
    }

    /** Runs at the end of renderBg (screen coordinates), after the window background is drawn. */
    public static void renderSideTabs(GuiGraphics g, CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab,
                                      float scroll, int leftPos, int topPos, int screenHeight, int mouseX, int mouseY) {
        lastSideTabArea = sideTabArea(menu, tab, leftPos, topPos, screenHeight);
        List<SectionLayout.PlacedSection> sections = sideSections(menu, tab);
        if (sections == null) {
            return;
        }
        Bar bar = bar(sections.size(), leftPos, topPos, screenHeight);
        int selected = currentSection(sections, ((ItemPickerMenuAccessor) menu).tabtailor$getRowIndexForScroll(scroll));
        if (pinnedSection >= 0 && pinnedSection < sections.size() && pinnedLayout == current
                && Math.abs(scroll - pinnedScroll) < 1.0E-4F) {
            selected = pinnedSection;
        } else {
            pinnedSection = -1;
        }
        // keep the active section in view when it changes (scrolling the grid); manual bar scrolling stays possible
        if (selected != lastActiveSection || current != lastActiveLayout) {
            lastActiveSection = selected;
            lastActiveLayout = current;
            if (selected < bar.first()) {
                sideScroll = selected;
            } else if (selected >= bar.first() + bar.visible()) {
                sideScroll = selected - bar.visible() + 1;
            }
            bar = bar(sections.size(), leftPos, topPos, screenHeight);
        }
        int hovered = hit(bar, sections.size(), mouseX, mouseY);
        SidebarStyle style = sidebarStyle(tab);
        drawFrame(g, style.background(), bar);

        int cx = bar.contentX();
        if (bar.buttons()) {
            boolean canUp = bar.first() > 0;
            boolean canDown = bar.first() + bar.visible() < sections.size();
            drawButton(g, style, cx, bar.upY(), true, canUp, canUp && hovered == UP);
            drawButton(g, style, cx, bar.downY(), false, canDown, canDown && hovered == DOWN);
        }

        int activeY = Integer.MIN_VALUE;
        for (int i = 0; i < bar.visible(); i++) {
            int index = bar.first() + i;
            if (index >= sections.size()) {
                break;
            }
            int sy = bar.slotsY() + i * SLOT_STEP;
            if (index == selected) {
                activeY = sy; // drawn last, on top of its neighbours
                continue;
            }
            int fill = index == hovered ? shade(style.slot(), 0.15F) : style.slot();
            bevel(g, cx, sy, SLOT, SLOT, fill, false); // sunken like an inventory slot
            g.renderItem(sections.get(index).tabIcon(), cx + 1, sy + 1);
        }
        if (activeY != Integer.MIN_VALUE) {
            drawActiveSlot(g, style, bar, activeY, sections.get(selected).tabIcon());
        }
    }

    /**
     * The active slot is wider than the bar, framed in "border_active" and has small notches on both sides
     * pointing at the icon, so it stands out clearly (like the selected bundle in Vanilla Backport).
     */
    private static void drawFrame(GuiGraphics g, int frame, Bar bar) {
        int x0 = bar.x();
        int y0 = bar.y();
        int x1 = x0 + BAR_W;
        int y1 = y0 + bar.height();
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, C_OUTLINE);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, C_OUTLINE);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, C_OUTLINE);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, C_OUTLINE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, frame);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 2, shade(frame, 0.8F));
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 2, shade(frame, 0.8F));
        g.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, shade(frame, -0.55F));
        g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, shade(frame, -0.55F));
    }

    /** Height of the side bar with the given number of slots, for the style editor preview. */
    public static int sidebarPreviewHeight(int slots) {
        return barHeight(slots, true);
    }

    /** The side bar with the given icons, for the style editor preview. Up arrow off, down arrow on. */
    public static void renderSidebarPreview(GuiGraphics g, SidebarStyle style, int x, int y, List<ItemStack> icons, int selected) {
        Bar bar = new Bar(x, y, barHeight(icons.size(), true), icons.size(), true, 0);
        drawFrame(g, style.background(), bar);
        int cx = bar.contentX();
        drawButton(g, style, cx, bar.upY(), true, false, false);
        drawButton(g, style, cx, bar.downY(), false, true, false);
        for (int i = 0; i < icons.size(); i++) {
            if (i == selected) {
                continue;
            }
            int sy = bar.slotsY() + i * SLOT_STEP;
            bevel(g, cx, sy, SLOT, SLOT, style.slot(), false);
            g.renderItem(icons.get(i), cx + 1, sy + 1);
        }
        if (selected >= 0 && selected < icons.size()) {
            drawActiveSlot(g, style, bar, bar.slotsY() + selected * SLOT_STEP, icons.get(selected));
        }
    }

    private static void drawActiveSlot(GuiGraphics g, SidebarStyle style, Bar bar, int sy, ItemStack icon) {
        int x0 = bar.x() - ACTIVE_OUT;
        int x1 = bar.x() + BAR_W + ACTIVE_OUT;
        int y0 = sy - 2;
        int y1 = sy + SLOT + 2;
        int border = style.borderActive();
        int borderLight = shade(border, 0.45F);
        int borderDark = shade(border, -0.45F);

        // black outline with cut corners
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, C_OUTLINE);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, C_OUTLINE);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, C_OUTLINE);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, C_OUTLINE);
        // 2 px frame in the accent color, light on top-left, dark on bottom-right
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, border);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 2, borderLight);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 2, borderLight);
        g.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, borderDark);
        g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, borderDark);
        // inner field
        int ix0 = x0 + 3;
        int ix1 = x1 - 3;
        g.fill(ix0, y0 + 3, ix1, y1 - 3, style.backgroundActive());
        g.fill(ix0, y0 + 3, ix1, y0 + 4, shade(style.backgroundActive(), -0.25F));
        // notches pointing at the icon
        int mid = (y0 + y1) / 2;
        for (int r = -2; r <= 2; r++) {
            int len = 3 - Math.abs(r);
            g.fill(ix0, mid + r - 1, ix0 + len, mid + r, border);
            g.fill(ix1 - len, mid + r - 1, ix1, mid + r, border);
        }
        g.renderItem(icon, bar.contentX() + 1, sy + 1);
    }

    private static void drawButton(GuiGraphics g, SidebarStyle style, int x, int y, boolean up, boolean enabled, boolean hovered) {
        int bg = enabled ? style.buttonActive() : style.button();
        if (hovered) {
            bg = shade(bg, 0.2F);
        }
        bevel(g, x, y, SLOT, BUTTON_H, bg, enabled);
        int color = enabled ? style.arrowActive() : style.arrow();
        if (hovered) {
            color = shade(color, 0.3F);
        }
        int center = x + SLOT / 2;
        int top = y + 2;
        for (int row = 0; row < 3; row++) {
            int half = up ? row + 1 : 3 - row;
            g.fill(center - half, top + row, center + half, top + row + 1, color);
        }
    }

    /** Runs at the end of render, draws the title of the hovered section slot. */
    public static void renderSideTabTooltip(GuiGraphics graphics, Font font, CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab,
                                            int leftPos, int topPos, int screenHeight, int mouseX, int mouseY) {
        List<SectionLayout.PlacedSection> sections = sideSections(menu, tab);
        if (sections == null) {
            return;
        }
        int hovered = hit(bar(sections.size(), leftPos, topPos, screenHeight), sections.size(), mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderTooltip(font, sections.get(hovered).header().title(), mouseX, mouseY);
        }
    }

    /** @return the new scroll offset of the item grid, the old one for arrow clicks, or NaN when the bar was not clicked */
    public static float clickSideTab(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab,
                                     int leftPos, int topPos, int screenHeight, double mouseX, double mouseY, float currentScroll) {
        List<SectionLayout.PlacedSection> sections = sideSections(menu, tab);
        if (sections == null) {
            return Float.NaN;
        }
        Bar bar = bar(sections.size(), leftPos, topPos, screenHeight);
        if (!insideBar(bar, mouseX, mouseY)) {
            return Float.NaN;
        }
        int hit = hit(bar, sections.size(), mouseX, mouseY);
        if (hit == UP || hit == DOWN) {
            int before = sideScroll;
            sideScroll += hit == UP ? -1 : 1;
            bar(sections.size(), leftPos, topPos, screenHeight); // clamps
            if (before != sideScroll) {
                playClick();
            }
            return currentScroll;
        }
        if (hit < 0) {
            return currentScroll; // click on the frame: swallow it
        }
        playClick();
        float target = 0.0F;
        if (menu.canScroll()) {
            target = ((ItemPickerMenuAccessor) menu).tabtailor$getScrollForRowIndex(sections.get(hit).headerRow());
            target = Mth.clamp(target, 0.0F, 1.0F);
            menu.scrollTo(target);
        }
        pinnedSection = hit;
        pinnedScroll = target;
        pinnedLayout = current;
        return target;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Mouse wheel over the bar scrolls the bar when not all sections fit. */
    public static boolean scrollSideTabs(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab,
                                         int leftPos, int topPos, int screenHeight, double mouseX, double mouseY, double delta) {
        List<SectionLayout.PlacedSection> sections = sideSections(menu, tab);
        if (sections == null) {
            return false;
        }
        Bar bar = bar(sections.size(), leftPos, topPos, screenHeight);
        if (!insideBar(bar, mouseX, mouseY)) {
            return false;
        }
        if (bar.buttons()) {
            sideScroll -= (int) Math.signum(delta);
            bar(sections.size(), leftPos, topPos, screenHeight);
        }
        return true;
    }

    /** Clicks on the bar must not count as "outside the window" (that would drop the carried item). */
    public static boolean isOverSideTabs(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab,
                                         int leftPos, int topPos, int screenHeight, double mouseX, double mouseY) {
        List<SectionLayout.PlacedSection> sections = sideSections(menu, tab);
        return sections != null && insideBar(bar(sections.size(), leftPos, topPos, screenHeight), mouseX, mouseY);
    }

    public static @Nullable int[] lastSideTabArea() {
        return lastSideTabArea;
    }

    public static void screenClosed() {
        lastSideTabArea = null;
    }

    /** Area covered by the bar in screen coordinates (x, y, width, height), for recipe viewer exclusion zones. */
    public static @Nullable int[] sideTabArea(CreativeModeInventoryScreen.ItemPickerMenu menu, @Nullable CreativeModeTab tab,
                                              int leftPos, int topPos, int screenHeight) {
        List<SectionLayout.PlacedSection> sections = sideSections(menu, tab);
        if (sections == null) {
            return null;
        }
        Bar bar = bar(sections.size(), leftPos, topPos, screenHeight);
        return new int[]{bar.x() - ACTIVE_OUT, bar.y(), BAR_W + BAR_GAP + ACTIVE_OUT, bar.height()};
    }
}
