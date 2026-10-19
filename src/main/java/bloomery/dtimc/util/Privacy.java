package bloomery.dtimc.devathip_interface.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * Streamer Mode. File paths and URLs are never drawn as text while it is on; a mosaic is drawn instead.
 * Holding ALT reveals them (for the player), so a stream sees them only while ALT is held.
 */
public final class Privacy {
    private Privacy() { }

    public static boolean peeking() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    public static boolean enabled() {
        return ModSettings.get().streamerMode;
    }

    public static boolean masked() {
        return enabled() && !peeking();
    }

    /** Pixelated censor block that does not depend on the hidden text. */
    public static void mosaic(DrawContext c, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        c.fill(x, y, x + w, y + h, 0xFF22232B);
        int cell = 3;
        for (int cy = 0; cy * cell < h; cy++) {
            for (int cx = 0; cx * cell < w; cx++) {
                int n = ((cx * 73856093) ^ (cy * 19349663)) & 0xFF;
                int g = 0x4A + (n % 0x38);
                int color = 0xFF000000 | (g << 16) | (g << 8) | Math.min(0xFF, g + 0x14);
                c.fill(x + cx * cell, y + cy * cell, Math.min(x + w, x + cx * cell + cell),
                        Math.min(y + h, y + cy * cell + cell), color);
            }
        }
    }

    /** Draws sensitive text (or a mosaic of the same width). Returns the width used. */
    public static int draw(DrawContext c, TextRenderer tr, String text, int x, int y, int color, int maxW) {
        String shown = UiKit.fitTail(tr, text, maxW);
        int w = tr.getWidth(shown);
        if (masked()) {
            mosaic(c, x, y - 1, Math.max(12, Math.min(w, maxW)), 10);
        } else {
            c.drawText(tr, shown, x, y, color, false);
        }
        return w;
    }

    /** A string safe to show inside a text field (bullets only). */
    public static String bullets(String s) {
        int n = Math.min(s.length(), 48);
        return "•".repeat(n);
    }

    public static void badge(DrawContext c, TextRenderer tr, int screenH) {
        if (!enabled()) return;
        String text = ModLang.t("streamer.badge");
        int w = tr.getWidth(text) + 18;
        int x = 8, y = screenH - 20;
        c.fill(x, y, x + w, y + 14, 0xAA101014);
        c.fill(x, y, x + 2, y + 14, UiKit.RED);
        c.fill(x + 7, y + 5, x + 12, y + 10, peeking() ? UiKit.GOLD : UiKit.RED);
        c.drawText(tr, text, x + 16, y + 3, 0xFFE0E0E8, false);
    }
}
