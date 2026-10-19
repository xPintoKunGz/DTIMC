package bloomery.dtimc.util;

import net.minecraft.client.gui.DrawContext;

/** Small pixel icons drawn with fills (no font glyphs needed). Every icon fits in a 12 x 10 box. */
public final class Icons {
    public enum Type { FOLDER, DOWNLOAD, CLOUD, GEAR }

    public static final int W = 12;
    public static final int H = 10;

    private Icons() { }

    /** @param bg colour behind the icon (used for the "holes" of the gear). */
    public static void draw(DrawContext c, Type type, int x, int y, int color, int bg) {
        switch (type) {
            case FOLDER -> {
                c.fill(x, y + 1, x + 5, y + 3, color);          // tab
                c.fill(x, y + 3, x + 12, y + 9, color);         // body
                c.fill(x + 1, y + 4, x + 11, y + 5, (color & 0x00FFFFFF) | 0x66000000); // fold line
            }
            case DOWNLOAD -> {
                c.fill(x + 5, y, x + 7, y + 5, color);          // arrow stem
                for (int i = 0; i < 4; i++) c.fill(x + 3 + i, y + 4 + i, x + 9 - i, y + 5 + i, color); // head
                c.fill(x + 1, y + 8, x + 2, y + 10, color);     // tray
                c.fill(x + 10, y + 8, x + 11, y + 10, color);
                c.fill(x + 1, y + 9, x + 11, y + 10, color);
            }
            case CLOUD -> {
                c.fill(x + 3, y + 2, x + 7, y + 4, color);      // left bump
                c.fill(x + 6, y, x + 10, y + 4, color);         // right bump
                c.fill(x + 1, y + 4, x + 12, y + 8, color);     // body
                c.fill(x + 2, y + 3, x + 11, y + 4, color);
            }
            case GEAR -> {
                c.fill(x + 5, y, x + 7, y + 2, color);          // teeth
                c.fill(x + 5, y + 8, x + 7, y + 10, color);
                c.fill(x + 1, y + 4, x + 3, y + 6, color);
                c.fill(x + 9, y + 4, x + 11, y + 6, color);
                c.fill(x + 2, y + 1, x + 4, y + 3, color);
                c.fill(x + 8, y + 1, x + 10, y + 3, color);
                c.fill(x + 2, y + 7, x + 4, y + 9, color);
                c.fill(x + 8, y + 7, x + 10, y + 9, color);
                c.fill(x + 3, y + 2, x + 9, y + 8, color);      // ring
                c.fill(x + 5, y + 4, x + 7, y + 6, bg);         // hole
            }
        }
    }
}
