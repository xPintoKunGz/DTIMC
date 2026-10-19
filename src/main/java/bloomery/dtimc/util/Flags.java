package bloomery.dtimc.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws a language flag. Uses assets/myskinmod/textures/gui/flags/<code>.png (en.png, th.png) when present,
 * otherwise draws a simple built-in flag so nothing is ever missing.
 */
public final class Flags {
    private static final Map<String, int[]> SIZES = new HashMap<>();

    private Flags() { }

    private static Identifier id(String code) {
        return new Identifier("myskinmod", "textures/gui/flags/" + code + ".png");
    }

    private static int[] sizeOf(String code) {
        return SIZES.computeIfAbsent(code, k -> {
            try {
                Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id(k));
                if (res.isPresent()) {
                    try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                        return new int[]{img.getWidth(), img.getHeight()};
                    }
                }
            } catch (Exception ignored) { }
            return new int[]{0, 0};
        });
    }

    public static void draw(DrawContext c, String code, int x, int y, int w, int h) {
        int[] s = sizeOf(code);
        if (s[0] > 0) {
            c.drawTexture(id(code), x, y, w, h, 0f, 0f, s[0], s[1], s[0], s[1]);
        } else if (code.equals("th")) {
            int u = Math.max(1, h / 6);
            int[] cols = {0xFFA51931, 0xFFF4F5F8, 0xFF2D2A4A, 0xFFF4F5F8, 0xFFA51931};
            int[] hs = {u, u, h - 4 * u, u, u};
            int yy = y;
            for (int i = 0; i < 5; i++) {
                c.fill(x, yy, x + w, yy + hs[i], cols[i]);
                yy += hs[i];
            }
        } else {
            c.fill(x, y, x + w, y + h, 0xFF012169);
            int vx = x + w * 2 / 5, vw = Math.max(2, w / 5);
            int hy = y + h * 2 / 5, hh = Math.max(2, h / 5);
            c.fill(vx, y, vx + vw, y + h, 0xFFFFFFFF);
            c.fill(x, hy, x + w, hy + hh, 0xFFFFFFFF);
            c.fill(vx + vw / 3, y, vx + vw - vw / 3, y + h, 0xFFC8102E);
            c.fill(x, hy + hh / 3, x + w, hy + hh - hh / 3, 0xFFC8102E);
        }
        c.fill(x, y, x + w, y + 1, 0x55000000);
        c.fill(x, y + h - 1, x + w, y + h, 0x55000000);
        c.fill(x, y, x + 1, y + h, 0x55000000);
        c.fill(x + w - 1, y, x + w, y + h, 0x55000000);
    }
}
