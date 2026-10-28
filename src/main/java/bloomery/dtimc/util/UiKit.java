package bloomery.dtimc.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Shared look & feel so every screen matches the Skin Storage screen. */
public final class UiKit {
    public static final int ACCENT = 0xFF7DD3FC;
    public static final int GOLD = 0xFFFFAA00;
    public static final int PINK = 0xFFFF7B9C;
    public static final int VIOLET = 0xFFB69CFF;
    public static final int GREEN = 0xFF6EE7A8;
    public static final int RED = 0xFFFF6B6B;
    public static final int GRAY = 0xFF8A8FA3;
    public static final int PANEL_H = 226;

    /** Panel height that follows the window: bigger screens get a bigger, roomier interface. */
    public static int panelHeight(int screenHeight) {
        return Math.max(250, Math.min(360, screenHeight - 80));
    }

    private UiKit() { }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static int topY(int screenHeight) {
        return Math.max(22, (screenHeight - 256) / 2 + 10);
    }

    public static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xD0101014);
        c.fill(x, y, x + w, y + 2, ACCENT);
        c.fill(x, y, x + 1, y + h, 0x22FFFFFF);
        c.fill(x + w - 1, y, x + w, y + h, 0x22FFFFFF);
        c.fill(x, y + h - 1, x + w, y + h, 0x33FFFFFF);
        c.fill(x + 6, y + 22, x + w - 6, y + 23, 0x22FFFFFF);
    }

    public static String fit(TextRenderer tr, String s, int maxW) {
        if (maxW <= 0) return "";
        if (tr.getWidth(s) <= maxW) return s;
        int ew = tr.getWidth("..");
        if (maxW <= ew) return tr.trimToWidth(s, maxW);
        return tr.trimToWidth(s, maxW - ew) + "..";
    }

    /** Keeps the END of a long string (file names / URLs). */
    public static String fitTail(TextRenderer tr, String s, int maxW) {
        if (maxW <= 0) return "";
        if (tr.getWidth(s) <= maxW) return s;
        int ew = tr.getWidth("..");
        if (maxW <= ew) return tr.trimToWidth(s, maxW, true);
        return ".." + tr.trimToWidth(s, maxW - ew, true);
    }

    public static void dashedBorder(DrawContext c, int x, int y, int w, int h, int color) {
        int dash = 4;
        for (int i = 0; i < w; i += dash * 2) {
            int len = Math.min(dash, w - i);
            c.fill(x + i, y, x + i + len, y + 1, color);
            c.fill(x + i, y + h - 1, x + i + len, y + h, color);
        }
        for (int i = 0; i < h; i += dash * 2) {
            int len = Math.min(dash, h - i);
            c.fill(x, y + i, x + 1, y + i + len, color);
            c.fill(x + w - 1, y + i, x + w, y + i + len, color);
        }
    }

    /** Inset box with a thin border, used for thumbnails / text fields. */
    public static void inset(DrawContext c, int x, int y, int w, int h, int border) {
        c.fill(x, y, x + w, y + h, 0x66000000);
        c.fill(x, y, x + w, y + 1, border);
        c.fill(x, y + h - 1, x + w, y + h, border);
        c.fill(x, y, x + 1, y + h, border);
        c.fill(x + w - 1, y, x + w, y + h, border);
    }
}
