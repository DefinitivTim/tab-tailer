package io.github.definitivtim.tabtailor.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.IntSupplier;

/** A scrollable list with selection and drag and drop reordering, drawn by a callback. */
final class DragList {
    interface RowRenderer {
        void render(GuiGraphics g, int index, int x0, int y, int x1, int rowHeight, boolean selected, boolean hovered, int mouseX, int mouseY);
    }

    interface Mover {
        void move(int from, int to);
    }

    static final int C_HOVER = 0x22FFFFFF;
    static final int C_SELECTED = 0x44FFFFFF;
    static final int C_ACCENT = 0xFFE08A00;

    int x0;
    int y0;
    int x1;
    int y1;
    final int rowHeight;
    private final IntSupplier size;
    private final Mover mover;
    private final RowRenderer renderer;
    int selected = -1;
    double scroll;
    private int dragIndex = -1;
    private boolean dragging;
    private double dragStartY;
    private double lastMouseY;
    /** Called when selection or order changed. */
    Runnable onChange = () -> { };

    DragList(int rowHeight, IntSupplier size, Mover mover, RowRenderer renderer) {
        this.rowHeight = rowHeight;
        this.size = size;
        this.mover = mover;
        this.renderer = renderer;
    }

    void setBounds(int x0, int y0, int x1, int y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        clampScroll();
    }

    int size() {
        return size.getAsInt();
    }

    boolean inside(double mx, double my) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    int maxScroll() {
        return Math.max(0, size() * rowHeight - (y1 - y0));
    }

    void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    int contentRight() {
        return x1 - (maxScroll() > 0 ? 6 : 0);
    }

    int rowAt(double my) {
        int index = (int) Math.floor((my - y0 + scroll) / rowHeight);
        return index >= 0 && index < size() ? index : -1;
    }

    void select(int index) {
        if (size() == 0) {
            selected = -1;
            return;
        }
        selected = Mth.clamp(index, 0, size() - 1);
        ensureVisible(selected);
    }

    void ensureVisible(int index) {
        int top = index * rowHeight;
        if (top < scroll) {
            scroll = top;
        } else if (top + rowHeight > scroll + (y1 - y0)) {
            scroll = top + rowHeight - (y1 - y0);
        }
        clampScroll();
    }

    boolean dragging() {
        return dragging;
    }

    /** Starts a possible drag on a row. Returns the row index or -1. */
    int press(double mx, double my) {
        if (!inside(mx, my)) {
            return -1;
        }
        int index = rowAt(my);
        if (index < 0) {
            return -1;
        }
        selected = index;
        dragIndex = index;
        dragging = false;
        dragStartY = my;
        lastMouseY = my;
        onChange.run();
        return index;
    }

    boolean drag(double my) {
        if (dragIndex < 0) {
            return false;
        }
        lastMouseY = my;
        if (!dragging && Math.abs(my - dragStartY) > 3) {
            dragging = true;
        }
        if (dragging) {
            dragTo(my);
        }
        return true;
    }

    private void dragTo(double my) {
        if (size() == 0) {
            return;
        }
        double clamped = Mth.clamp(my, y0, y1 - 1);
        int target = Mth.clamp((int) Math.floor((clamped - y0 + scroll) / rowHeight), 0, size() - 1);
        if (target != dragIndex) {
            mover.move(dragIndex, target);
            dragIndex = target;
            selected = target;
            onChange.run();
        }
    }

    boolean release() {
        if (dragIndex < 0) {
            return false;
        }
        dragIndex = -1;
        dragging = false;
        return true;
    }

    boolean scroll(double mx, double my, double amount) {
        if (!inside(mx, my)) {
            return false;
        }
        scroll -= amount * rowHeight * 2;
        clampScroll();
        return true;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (dragging) {
            if (lastMouseY < y0 + 8) {
                scroll -= 4;
            } else if (lastMouseY > y1 - 8) {
                scroll += 4;
            }
            clampScroll();
            dragTo(lastMouseY);
        }
        g.enableScissor(x0, y0, x1, y1);
        int count = size();
        int first = Math.max(0, (int) (scroll / rowHeight));
        int last = Math.min(count - 1, first + (y1 - y0) / rowHeight + 1);
        int hovered = inside(mouseX, mouseY) && dragIndex < 0 ? rowAt(mouseY) : -1;
        int right = contentRight();
        for (int i = first; i <= last; i++) {
            int y = y0 + i * rowHeight - (int) scroll;
            if (i == selected) {
                g.fill(x0, y, right, y + rowHeight, C_SELECTED);
                if (dragging) {
                    g.renderOutline(x0, y, right - x0, rowHeight, C_ACCENT);
                }
            } else if (i == hovered) {
                g.fill(x0, y, right, y + rowHeight, C_HOVER);
            }
            renderer.render(g, i, x0, y, right, rowHeight, i == selected, i == hovered, mouseX, mouseY);
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int track = y1 - y0;
            int bar = Math.max(12, track * track / (track + max));
            int barY = y0 + (int) ((track - bar) * (scroll / max));
            g.fill(x1 - 5, y0, x1 - 1, y1, 0x40000000);
            g.fill(x1 - 5, barY, x1 - 1, barY + bar, 0xFF8B8B8B);
        }
    }

    /** The three-line drag handle at the left of a row. */
    static void handle(GuiGraphics g, int x, int y, int rowHeight) {
        int top = y + rowHeight / 2 - 3;
        for (int line = 0; line < 3; line++) {
            g.fill(x, top + line * 3, x + 6, top + line * 3 + 1, 0xFF9A9A9A);
        }
    }
}
