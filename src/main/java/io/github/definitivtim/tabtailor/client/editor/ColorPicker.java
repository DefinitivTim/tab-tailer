package io.github.definitivtim.tabtailor.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/** Saturation/value square, hue bar and alpha bar. Colors are ARGB ints. */
final class ColorPicker {
    static final int SIZE = 64;
    static final int BAR = 8;
    static final int GAP = 4;
    static final int WIDTH = SIZE + 2 * (GAP + BAR);

    int x;
    int y;
    private float hue;
    private float sat;
    private float val;
    private float alpha = 1.0F;
    private int dragging = -1; // 0 = square, 1 = hue, 2 = alpha
    private final IntConsumer onChange;

    ColorPicker(IntConsumer onChange) {
        this.onChange = onChange;
    }

    void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    void setColor(int argb) {
        alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0;
        if (delta > 0) {
            if (max == r) {
                h = ((g - b) / delta) % 6;
            } else if (max == g) {
                h = (b - r) / delta + 2;
            } else {
                h = (r - g) / delta + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
            hue = h; // keep the hue of grays as it was
        }
        sat = max == 0 ? 0 : delta / max;
        val = max;
    }

    int color() {
        return ((Math.round(alpha * 255) & 0xFF) << 24) | (Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF);
    }

    private int squareX0() {
        return x;
    }

    private int hueX0() {
        return x + SIZE + GAP;
    }

    private int alphaX0() {
        return hueX0() + BAR + GAP;
    }

    void render(GuiGraphics g) {
        // saturation (left to right) and value (top to bottom)
        for (int i = 0; i < SIZE; i++) {
            float s = i / (float) (SIZE - 1);
            int top = 0xFF000000 | Mth.hsvToRgb(hue, s, 1.0F);
            g.fillGradient(x + i, y, x + i + 1, y + SIZE, top, 0xFF000000);
        }
        g.renderOutline(x - 1, y - 1, SIZE + 2, SIZE + 2, 0xFF000000);
        int mx = x + Math.round(sat * (SIZE - 1));
        int my = y + Math.round((1 - val) * (SIZE - 1));
        g.renderOutline(mx - 2, my - 2, 5, 5, val > 0.5F ? 0xFF000000 : 0xFFFFFFFF);

        // hue
        int hx = hueX0();
        for (int i = 0; i < SIZE; i++) {
            g.fill(hx, y + i, hx + BAR, y + i + 1, 0xFF000000 | Mth.hsvToRgb(i / (float) SIZE, 1.0F, 1.0F));
        }
        g.renderOutline(hx - 1, y - 1, BAR + 2, SIZE + 2, 0xFF000000);
        int hy = y + Math.round(hue * (SIZE - 1));
        g.fill(hx - 2, hy, hx + BAR + 2, hy + 1, 0xFFFFFFFF);

        // alpha over a checker board
        int ax = alphaX0();
        for (int cy = 0; cy < SIZE; cy += 4) {
            for (int cx = 0; cx < BAR; cx += 4) {
                g.fill(ax + cx, y + cy, ax + cx + 4, y + cy + 4, ((cx + cy) / 4) % 2 == 0 ? 0xFFCCCCCC : 0xFF888888);
            }
        }
        int rgb = Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
        g.fillGradient(ax, y, ax + BAR, y + SIZE, 0xFF000000 | rgb, rgb);
        g.renderOutline(ax - 1, y - 1, BAR + 2, SIZE + 2, 0xFF000000);
        int ay = y + Math.round((1 - alpha) * (SIZE - 1));
        g.fill(ax - 2, ay, ax + BAR + 2, ay + 1, 0xFFFFFFFF);
    }

    boolean mouseClicked(double mx, double my) {
        if (my < y || my >= y + SIZE) {
            return false;
        }
        if (mx >= squareX0() && mx < squareX0() + SIZE) {
            dragging = 0;
        } else if (mx >= hueX0() && mx < hueX0() + BAR) {
            dragging = 1;
        } else if (mx >= alphaX0() && mx < alphaX0() + BAR) {
            dragging = 2;
        } else {
            return false;
        }
        update(mx, my);
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (dragging < 0) {
            return false;
        }
        update(mx, my);
        return true;
    }

    boolean mouseReleased() {
        boolean was = dragging >= 0;
        dragging = -1;
        return was;
    }

    private void update(double mx, double my) {
        float fy = Mth.clamp((float) (my - y) / (SIZE - 1), 0, 1);
        switch (dragging) {
            case 0 -> {
                sat = Mth.clamp((float) (mx - x) / (SIZE - 1), 0, 1);
                val = 1 - fy;
            }
            case 1 -> hue = Math.min(fy, 0.999F);
            case 2 -> alpha = 1 - fy;
            default -> {
                return;
            }
        }
        onChange.accept(color());
    }

    static String hex(int argb) {
        if ((argb >>> 24) == 0xFF) {
            return String.format("#%06X", argb & 0xFFFFFF);
        }
        return String.format("#%08X", argb);
    }

    /** Parses "#RRGGBB" or "#AARRGGBB" (also without #), null if invalid. */
    static Integer parse(String text) {
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
            // invalid
        }
        return null;
    }
}
