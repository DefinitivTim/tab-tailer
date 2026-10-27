package io.github.definitivtim.tabtailor.client.editor;

import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * In-game editor, stage 1: order of the tabs (drag and drop or arrows), hide or show them, title and icon.
 * Opened with /tabtailor edit or the "Config" button of Tab Tailor in the mod list.
 */
public final class TabEditorScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int MARGIN = 8;
    private static final int TOP = 26;
    private static final int TOGGLE_W = 34;

    private static final int C_PANEL = 0xB0101010;
    private static final int C_PANEL_EDGE = 0xFF3A3A3A;
    private static final int C_ROW_HOVER = 0x22FFFFFF;
    private static final int C_ROW_SELECTED = 0x44FFFFFF;
    private static final int C_ACCENT = 0xFFE08A00;
    private static final net.minecraft.resources.ResourceLocation BUTTON = net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button");
    private static final net.minecraft.resources.ResourceLocation BUTTON_HIGHLIGHTED = net.minecraft.resources.ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_TEXT_DIM = 0xFF9A9A9A;
    private static final int C_TEXT_HIDDEN = 0xFF707070;

    private final @Nullable Screen parent;
    private final TabEditorModel model = new TabEditorModel();

    private int selected = 0;
    private double scroll = 0;
    private int dragIndex = -1;
    private boolean dragging = false;
    private double dragStartY;
    private double lastMouseY;
    private boolean syncing = false;
    private Component status = Component.empty();
    private int statusColor = C_TEXT_DIM;

    private EditBox titleBox;
    private EditBox iconBox;
    private Button upButton;
    private Button downButton;
    private Button visibilityButton;
    private Button saveButton;

    // layout, computed in init
    private int listX0;
    private int listX1;
    private int listY0;
    private int listY1;
    private int panelX0;
    private int panelX1;

    public TabEditorScreen(@Nullable Screen parent) {
        super(Component.translatable("tabtailor.editor.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        listX0 = MARGIN;
        listY0 = TOP;
        listY1 = height - 46;
        int panelWidth = Mth.clamp(width / 3, 150, 220);
        panelX1 = width - MARGIN;
        panelX0 = panelX1 - panelWidth;
        listX1 = panelX0 - MARGIN;

        int px = panelX0 + 6;
        int pw = panelWidth - 12;
        int y = TOP + 34;

        titleBox = new EditBox(font, px, y + 10, pw, 18, Component.translatable("tabtailor.editor.field.title"));
        titleBox.setMaxLength(100);
        titleBox.setResponder(value -> {
            if (!syncing && current() != null) {
                current().title = value;
                changed();
            }
        });
        addRenderableWidget(titleBox);
        y += 34;

        iconBox = new EditBox(font, px, y + 10, pw - 22, 18, Component.translatable("tabtailor.editor.field.icon"));
        iconBox.setMaxLength(200);
        iconBox.setResponder(value -> {
            iconBox.setTextColor(TabEditorModel.iconValid(value) ? 0xE0E0E0 : 0xFF5555);
            if (!syncing && current() != null) {
                current().icon = value;
                changed();
            }
        });
        addRenderableWidget(iconBox);
        y += 38;

        int half = (pw - 4) / 2;
        upButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.up"), b -> moveSelected(-1))
                .bounds(px, y, half, 20).build());
        downButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.down"), b -> moveSelected(1))
                .bounds(px + half + 4, y, pw - half - 4, 20).build());
        y += 24;
        // hiding is done with the On/Off buttons in the list; kept as hidden widget for keyboard users
        visibilityButton = Button.builder(Component.empty(), b -> toggleSelected()).bounds(px, y, pw, 20).build();
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.edit_sections"), b -> openSections())
                .bounds(px, y, pw, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.editor.edit_sections.tooltip"))).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.tab_style"), b -> openTabStyle())
                .bounds(px, y, pw, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.editor.tab_style.tooltip"))).build());
        y += 24;
        int fileW = (pw - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.open_file"), b -> openTabFile())
                .bounds(px, y, fileW, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.editor.open_file.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.open_folder"), b -> Util.getPlatform().openFile(ConfigManager.root().toFile()))
                .bounds(px + fileW + 4, y, pw - fileW - 4, 20).build());

        int bw = Math.min(110, (width - 5 * MARGIN) / 4);
        int bx = width / 2 - (bw * 4 + 3 * MARGIN) / 2;
        int by = height - 28;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.save"), b -> save())
                .bounds(bx, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.reload"), b -> reloadFiles())
                .bounds(bx + bw + MARGIN, by, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.editor.reload.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.global_style"), b -> minecraft.setScreen(
                        new StyleEditorScreen(this, StyleEditorScreen.Scope.GLOBAL, null, Component.translatable("tabtailor.editor.all_tabs"), null, null, net.minecraft.world.item.ItemStack.EMPTY)))
                .bounds(bx + 2 * (bw + MARGIN), by, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.editor.global_style.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.close"), b -> onClose())
                .bounds(bx + 3 * (bw + MARGIN), by, bw, 20).build());

        selected = Mth.clamp(selected, 0, Math.max(0, model.rows.size() - 1));
        syncFields();
    }

    private @Nullable TabEditorModel.Row current() {
        return selected >= 0 && selected < model.rows.size() ? model.rows.get(selected) : null;
    }

    private void syncFields() {
        TabEditorModel.Row row = current();
        syncing = true;
        titleBox.setValue(row == null ? "" : row.title);
        titleBox.setHint(row == null ? Component.empty() : row.modTitle.copy().withStyle(ChatFormatting.DARK_GRAY));
        iconBox.setValue(row == null ? "" : row.icon);
        iconBox.setHint(Component.literal("minecraft:stone").withStyle(ChatFormatting.DARK_GRAY));
        syncing = false;
        titleBox.setEditable(row != null);
        iconBox.setEditable(row != null);
        updateButtons();
    }

    private void updateButtons() {
        TabEditorModel.Row row = current();
        upButton.active = row != null && selected > 0;
        downButton.active = row != null && selected < model.rows.size() - 1;
        visibilityButton.active = row != null;
        visibilityButton.setMessage(Component.translatable(row != null && row.hidden ? "tabtailor.editor.show" : "tabtailor.editor.hide"));
        saveButton.active = model.dirty();
    }

    private void changed() {
        status = Component.empty();
        updateButtons();
    }

    // ------------------------------------------------------------------ actions

    private void select(int index) {
        if (index < 0 || index >= model.rows.size()) {
            return;
        }
        selected = index;
        ensureVisible(index);
        syncFields();
    }

    private void moveSelected(int delta) {
        int target = selected + delta;
        if (current() == null || target < 0 || target >= model.rows.size()) {
            return;
        }
        model.move(selected, target);
        selected = target;
        ensureVisible(selected);
        changed();
    }

    private void toggleSelected() {
        TabEditorModel.Row row = current();
        if (row != null) {
            row.hidden = !row.hidden;
            changed();
        }
    }

    private void openTabStyle() {
        TabEditorModel.Row row = current();
        if (row != null) {
            minecraft.setScreen(new StyleEditorScreen(this, StyleEditorScreen.Scope.TAB, row.id, row.displayTitle(), null, null, row.displayIcon()));
        }
    }

    private void openSections() {
        TabEditorModel.Row row = current();
        if (row != null) {
            minecraft.setScreen(new SectionEditorScreen(this, row.tab, row.id, row.displayTitle()));
        }
    }

    private void openTabFile() {
        TabEditorModel.Row row = current();
        if (row == null) {
            return;
        }
        try {
            Path file = TabEditorModel.tabFile(row.id, ConfigManager.get(), true);
            Util.getPlatform().openFile(file.toFile());
            setStatus(Component.translatable("tabtailor.editor.status.opened", file.getFileName().toString()), C_TEXT_DIM);
        } catch (Exception e) {
            TabTailor.LOGGER.warn("[Tab Tailor] Could not open tab file", e);
            setStatus(Component.translatable("tabtailor.editor.error.save", String.valueOf(e.getMessage())), 0xFFFF5555);
        }
    }

    private void save() {
        TabEditorModel.SaveResult result = model.save();
        if (!result.ok()) {
            String[] parts = result.message().split("\\|", 2);
            setStatus(Component.translatable(parts[0], parts.length > 1 ? parts[1] : ""), 0xFFFF5555);
            return;
        }
        selected = Mth.clamp(selected, 0, Math.max(0, model.rows.size() - 1));
        syncFields();
        if (result.problems() == 0) {
            setStatus(Component.translatable("tabtailor.editor.status.saved"), 0xFF55FF55);
        } else {
            setStatus(Component.translatable("tabtailor.editor.status.saved_problems", result.problems()), 0xFFFFAA00);
        }
    }

    private void reloadFiles() {
        Runnable doReload = () -> {
            ConfigManager.reload();
            io.github.definitivtim.tabtailor.client.TabTailorCommands.rebuildTabsNow();
            model.load();
            selected = Mth.clamp(selected, 0, Math.max(0, model.rows.size() - 1));
            syncFields();
            setStatus(Component.translatable("tabtailor.editor.status.reloaded"), C_TEXT_DIM);
        };
        if (!model.dirty()) {
            doReload.run();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(this);
            if (yes) {
                doReload.run();
            }
        }, Component.translatable("tabtailor.editor.discard.title"), Component.translatable("tabtailor.editor.discard.message")));
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void onClose() {
        if (!model.dirty()) {
            minecraft.setScreen(parent);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> minecraft.setScreen(yes ? parent : this),
                Component.translatable("tabtailor.editor.discard.title"), Component.translatable("tabtailor.editor.discard.message")));
    }

    // ------------------------------------------------------------------ list geometry

    private int visibleRows() {
        return Math.max(1, (listY1 - listY0) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, model.rows.size() * ROW_H - (listY1 - listY0));
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private void ensureVisible(int index) {
        int top = index * ROW_H;
        int bottom = top + ROW_H;
        if (top < scroll) {
            scroll = top;
        } else if (bottom > scroll + (listY1 - listY0)) {
            scroll = bottom - (listY1 - listY0);
        }
        clampScroll();
    }

    private boolean insideList(double mx, double my) {
        return mx >= listX0 && mx < listX1 && my >= listY0 && my < listY1;
    }

    private int rowAt(double my) {
        int index = (int) Math.floor((my - listY0 + scroll) / ROW_H);
        return index >= 0 && index < model.rows.size() ? index : -1;
    }

    private int toggleX() {
        return listX1 - 6 - TOGGLE_W - (maxScroll() > 0 ? 6 : 0);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && insideList(mx, my)) {
            int index = rowAt(my);
            if (index >= 0) {
                setFocused(null);
                titleBox.setFocused(false);
                iconBox.setFocused(false);
                if (mx >= toggleX() && mx < toggleX() + TOGGLE_W) {
                    TabEditorModel.Row row = model.rows.get(index);
                    row.hidden = !row.hidden;
                    select(index);
                    changed();
                    return true;
                }
                select(index);
                dragIndex = index;
                dragging = false;
                dragStartY = my;
                lastMouseY = my;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragIndex >= 0 && button == 0) {
            lastMouseY = my;
            if (!dragging && Math.abs(my - dragStartY) > 3) {
                dragging = true;
            }
            if (dragging) {
                dragTo(my);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private void dragTo(double my) {
        double clamped = Mth.clamp(my, listY0, listY1 - 1);
        int target = Mth.clamp((int) Math.floor((clamped - listY0 + scroll) / ROW_H), 0, model.rows.size() - 1);
        if (target != dragIndex) {
            model.move(dragIndex, target);
            dragIndex = target;
            selected = target;
            changed();
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragIndex >= 0 && button == 0) {
            dragIndex = -1;
            dragging = false;
            syncFields();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (insideList(mx, my)) {
            scroll -= scrollY * ROW_H * 2;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean typing = titleBox.isFocused() || iconBox.isFocused();
        if (!typing) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (key == GLFW.GLFW_KEY_UP) {
                if (shift) {
                    moveSelected(-1);
                } else {
                    select(selected - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                if (shift) {
                    moveSelected(1);
                } else {
                    select(selected + 1);
                }
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (model.dirty()) {
                save();
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        panel(g, listX0 - 2, listY0 - 2, listX1 + 2, listY1 + 2);
        panel(g, panelX0, TOP - 2, panelX1, listY1 + 2);
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, C_PANEL);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, C_PANEL_EDGE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // auto scroll while dragging near the edges
        if (dragging) {
            if (lastMouseY < listY0 + 10) {
                scroll -= 4;
            } else if (lastMouseY > listY1 - 10) {
                scroll += 4;
            }
            clampScroll();
            dragTo(lastMouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, title, width / 2, 9, C_TEXT);
        Component hint = Component.translatable("tabtailor.editor.hint");
        g.drawString(font, hint, listX0, TOP - 12, C_TEXT_DIM, false);

        renderList(g, mouseX, mouseY);
        renderPanel(g);

        if (!status.getString().isEmpty()) {
            g.drawCenteredString(font, status, width / 2, height - 40, statusColor);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(listX0, listY0, listX1, listY1);
        int first = Math.max(0, (int) (scroll / ROW_H));
        int last = Math.min(model.rows.size() - 1, first + visibleRows() + 1);
        int hovered = insideList(mouseX, mouseY) && dragIndex < 0 ? rowAt(mouseY) : -1;
        for (int i = first; i <= last; i++) {
            TabEditorModel.Row row = model.rows.get(i);
            int y = listY0 + i * ROW_H - (int) scroll;
            int x1 = listX1 - (maxScroll() > 0 ? 6 : 0);
            if (i == selected) {
                g.fill(listX0, y, x1, y + ROW_H, C_ROW_SELECTED);
                if (dragging) {
                    g.renderOutline(listX0, y, x1 - listX0, ROW_H, C_ACCENT);
                }
            } else if (i == hovered) {
                g.fill(listX0, y, x1, y + ROW_H, C_ROW_HOVER);
            }
            // drag handle
            int hx = listX0 + 4;
            for (int line = 0; line < 3; line++) {
                g.fill(hx, y + 7 + line * 3, hx + 6, y + 8 + line * 3, C_TEXT_DIM);
            }
            // number, icon, title, id
            String number = String.valueOf(i + 1);
            g.drawString(font, number, listX0 + 14 + (18 - font.width(number)), y + 6, C_TEXT_DIM, false);
            int iconX = listX0 + 36;
            g.renderItem(row.displayIcon(), iconX, y + 2);
            if (row.hidden) {
                g.fill(iconX, y + 2, iconX + 16, y + 18, 0x99000000);
            }
            int textX = iconX + 20;
            int textMax = toggleX() - 6 - textX;
            String name = font.plainSubstrByWidth(row.displayTitle().getString(), textMax);
            int color = row.hidden ? C_TEXT_HIDDEN : C_TEXT;
            g.drawString(font, name, textX, y + 6, color, false);
            int idX = textX + font.width(name) + 6;
            if (idX < toggleX() - 20) {
                String id = font.plainSubstrByWidth(row.id.toString(), toggleX() - 6 - idX);
                g.drawString(font, id, idX, y + 6, 0xFF5E5E5E, false);
            }
            boolean changed = row.fileChanged() || row.hidden != row.originalHidden;
            if (changed) {
                g.fill(listX0 + 1, y + 2, listX0 + 3, y + ROW_H - 2, C_ACCENT);
            }
            // visibility toggle
            int tx = toggleX();
            int ty = y + 2;
            boolean overToggle = i == hovered && mouseX >= tx && mouseX < tx + TOGGLE_W;
            // vanilla button texture, highlighted on hover like a real button
            g.blitSprite(overToggle ? BUTTON_HIGHLIGHTED : BUTTON, tx, ty, TOGGLE_W, 16);
            Component label = Component.translatable(row.hidden ? "tabtailor.editor.toggle.off" : "tabtailor.editor.toggle.on");
            g.drawCenteredString(font, label, tx + TOGGLE_W / 2, ty + 4, row.hidden ? 0xFFFF6B6B : 0xFF7CFC6B);
        }
        g.disableScissor();

        // scrollbar
        int max = maxScroll();
        if (max > 0) {
            int trackH = listY1 - listY0;
            int barH = Math.max(16, trackH * trackH / (trackH + max));
            int barY = listY0 + (int) ((trackH - barH) * (scroll / max));
            g.fill(listX1 - 5, listY0, listX1 - 1, listY1, 0x40000000);
            g.fill(listX1 - 5, barY, listX1 - 1, barY + barH, 0xFF8B8B8B);
        }
    }

    private void renderPanel(GuiGraphics g) {
        TabEditorModel.Row row = current();
        int px = panelX0 + 6;
        int pw = panelX1 - panelX0 - 12;
        if (row == null) {
            g.drawString(font, Component.translatable("tabtailor.editor.none"), px, TOP + 4, C_TEXT_DIM, false);
            return;
        }
        g.renderItem(row.displayIcon(), px, TOP + 3);
        g.drawString(font, font.plainSubstrByWidth(row.displayTitle().getString(), pw - 20), px + 20, TOP + 3, C_TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(row.id.toString(), pw - 20), px + 20, TOP + 13, C_TEXT_DIM, false);
        g.drawString(font, Component.translatable("tabtailor.editor.field.title"), px, titleBox.getY() - 10, C_TEXT_DIM, false);
        g.drawString(font, Component.translatable("tabtailor.editor.field.icon"), px, iconBox.getY() - 10, C_TEXT_DIM, false);
        // icon preview next to the icon field
        int ix = iconBox.getX() + iconBox.getWidth() + 4;
        g.fill(ix, iconBox.getY(), ix + 18, iconBox.getY() + 18, 0xFF8B8B8B);
        g.renderItem(row.displayIcon(), ix + 1, iconBox.getY() + 1);
        if (!TabEditorModel.iconValid(row.icon)) {
            g.drawString(font, Component.translatable("tabtailor.editor.icon_unknown"), px, iconBox.getY() + 21, 0xFFFF5555, false);
        } else if (row.icon.isBlank()) {
            g.drawString(font, Component.translatable("tabtailor.editor.icon_default"), px, iconBox.getY() + 21, 0xFF5E5E5E, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
