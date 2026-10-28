package bloomery.dtimc.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/**
 * Shared 3D skin preview: left-drag rotates, right-drag moves the model, wheel zooms (shows the zoom %).
 * The model is clipped to its box and the depth buffer is cleared afterwards, so it can never cover text or
 * any other part of the GUI, however far it is zoomed.
 */
public final class PreviewView {
    public static final float MIN_ZOOM = 0.6f;
    public static final float MAX_ZOOM = 3.0f;

    public float yaw, pitch, panX, panY;
    public float zoom = 1.0f;

    private int resetX, resetY, resetW, resetH;

    public void reset() {
        yaw = 0f;
        pitch = 0f;
        panX = 0f;
        panY = 0f;
        zoom = 1.0f;
    }

    /** Left mouse button drag. */
    public void dragRotate(double dx, double dy) {
        yaw += (float) dx * 1.5f;
        pitch = Math.max(-20f, Math.min(20f, pitch + (float) dy * 1.5f));
    }

    /** Right mouse button drag: slide the model left / right / up / down inside the box. */
    public void dragPan(double dx, double dy, int boxW, int boxH) {
        panX = (float) Math.max(-boxW * 0.7, Math.min(boxW * 0.7, panX + dx));
        panY = (float) Math.max(-boxH * 0.7, Math.min(boxH * 0.7, panY + dy));
    }

    public void scroll(double amount) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + (float) amount * 0.12f));
    }

    public int zoomPercent() {
        return Math.round(zoom * 100f);
    }

    public void render(DrawContext c, TextRenderer tr, ClientPlayerEntity player, int bx, int by, int bw, int bh) {
        c.fill(bx, by, bx + bw, by + bh, 0x66000000);
        c.fill(bx, by + bh - 1, bx + bw, by + bh, 0x22FFFFFF);

        if (player != null) {
            player.age = 0;
            player.headYaw = 0.0f;
            player.bodyYaw = 0.0f;
            player.setYaw(0.0f);
            player.setPitch(0.0f);
            player.limbAnimator.setSpeed(0.0f);

            Quaternionf rotation = new Quaternionf()
                    .rotateX((float) Math.toRadians(180f + pitch))
                    .rotateY((float) Math.toRadians(yaw));

            int base = Math.max(16, (int) (0.62 * bh / 1.8));
            int size = Math.max(8, Math.round(base * zoom));
            int cx = bx + bw / 2 + Math.round(panX);
            int cy = by + bh / 2 + Math.round(panY);
            int feetY = cy + Math.round(0.9f * size); // zoom around the centre of the model

            c.enableScissor(bx + 1, by + 1, bx + bw - 1, by + bh - 1);
            InventoryScreen.drawEntity(c, cx, feetY, size, rotation, null, player);
            // The entity is drawn very close to the camera; clear its depth so later GUI drawing is never hidden by it.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            c.disableScissor();
        }

        // zoom % badge (drawn after the model, so it always stays on top)
        String z = zoomPercent() + "%";
        int tw = tr.getWidth(z);
        int px = bx + bw - tw - 10, py = by + 4;
        c.fill(px, py, px + tw + 8, py + 12, 0xB0101014);
        c.fill(px, py, px + 2, py + 12, UiKit.ACCENT);
        c.drawText(tr, z, px + 5, py + 2, 0xFFE0E0E8, false);
    }

    /** One line under the box: control hints on the left, a Reset button on the right. */
    public void drawCaption(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
        String reset = ModLang.t("view.reset");
        int rw = tr.getWidth(reset) + 10;
        resetX = x + w - rw;
        resetY = y - 2;
        resetW = rw;
        resetH = 12;
        boolean hover = UiKit.in(mx, my, resetX, resetY, resetW, resetH);
        c.fill(resetX, resetY, resetX + resetW, resetY + resetH, hover ? 0xFF3A3F55 : 0xFF262A38);
        c.fill(resetX, resetY, resetX + resetW, resetY + 1, hover ? UiKit.ACCENT : 0x44FFFFFF);
        c.drawText(tr, reset, resetX + 5, y, hover ? 0xFFFFFFFF : 0xFFB8C0D4, false);
        c.drawText(tr, UiKit.fit(tr, ModLang.t("view.hint"), w - rw - 6), x, y, 0xFF6C7186, false);
    }

    /** @return true if the Reset button was clicked (and the view has been reset). */
    public boolean captionClicked(double mx, double my) {
        if (UiKit.in(mx, my, resetX, resetY, resetW, resetH)) {
            reset();
            return true;
        }
        return false;
    }
}
