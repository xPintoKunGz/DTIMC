package bloomery.dtimc.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Floating alert box in the top-right corner: rounded, with a soft shadow and a gap to the screen edge,
 * a colour-coded icon (or the skin head), a seconds countdown and a progress bar showing when it disappears.
 * Can be switched off completely in the options (ModSettings.toastsEnabled).
 */
public final class ModToast implements Toast {
    public enum Type { SUCCESS, ERROR, INFO, WARNING }

    private static final int MARGIN_X = 10; // transparent strip to the right so the box floats off the edge
    private static final int MARGIN_Y = 4;  // gap above / below, between stacked toasts
    private static final int TEXT_X = 40;

    private final Type type;
    private final String key;
    private final String title;
    private final List<OrderedText> lines;
    private final Identifier skin;
    private final int skinW, skinH;
    private final int boxW, boxH;
    private final long duration;

    private ModToast(Type type, String title, String message, Identifier skin, int skinW, int skinH) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        this.type = type;
        this.key = type + "|" + title + "|" + message;
        this.skin = skin;
        this.skinW = skinW;
        this.skinH = skinH;
        this.duration = type == Type.ERROR ? 5000L : 4000L;

        int maxTextW = 170;
        this.title = UiKit.fit(tr, title, maxTextW - 30);
        List<OrderedText> wrapped = tr.wrapLines(Text.literal(message), maxTextW);
        this.lines = wrapped.size() > 2 ? wrapped.subList(0, 2) : wrapped;

        int widest = tr.getWidth(this.title) + 30;
        for (OrderedText l : lines) widest = Math.max(widest, tr.getWidth(l));
        this.boxW = Math.max(160, Math.min(TEXT_X + maxTextW + 12, TEXT_X + widest + 12));
        this.boxH = lines.size() > 1 ? 50 : 40;
    }

    // read by the toast manager (no @Override so it compiles on every 1.20.x mapping)
    public int getWidth() { return boxW + MARGIN_X; }

    public int getHeight() { return boxH + MARGIN_Y * 2; }

    @Override
    public Object getType() { return key; }

    private int accent() {
        return switch (type) {
            case SUCCESS -> UiKit.GREEN;
            case ERROR -> UiKit.RED;
            case WARNING -> 0xFFFFC857;
            default -> UiKit.ACCENT;
        };
    }

    @Override
    public Visibility draw(DrawContext c, ToastManager manager, long elapsed) {
        TextRenderer tr = manager.getClient().textRenderer;
        int accent = accent();
        int oy = MARGIN_Y;

        // soft shadow + rounded box
        c.fill(2, oy + 2, boxW + 2, oy + boxH + 2, 0x55000000);
        c.fill(1, oy, boxW - 1, oy + boxH, 0xF0101014);
        c.fill(0, oy + 1, boxW, oy + boxH - 1, 0xF0101014);
        c.fill(1, oy, boxW - 1, oy + 1, 0x33FFFFFF);
        c.fill(0, oy + 3, 3, oy + boxH - 3, accent);

        // icon / skin head
        int is = 24, ix = 10, iy = oy + (boxH - is) / 2 - 1;
        if (skin != null) {
            c.fill(ix - 1, iy - 1, ix + is + 1, iy + is + 1, (accent & 0x00FFFFFF) | 0xAA000000);
            float k = skinW / 64.0f;
            int region = Math.max(1, Math.round(8 * k));
            c.drawTexture(skin, ix, iy, is, is, 8.0f * k, 8.0f * k, region, region, skinW, skinH);
            c.drawTexture(skin, ix, iy, is, is, 40.0f * k, 8.0f * k, region, region, skinW, skinH);
        } else {
            c.fill(ix, iy, ix + is, iy + is, (accent & 0x00FFFFFF) | 0x33000000);
            c.fill(ix, iy, ix + is, iy + 1, accent);
            c.fill(ix, iy + is - 1, ix + is, iy + is, accent);
            c.fill(ix, iy, ix + 1, iy + is, accent);
            c.fill(ix + is - 1, iy, ix + is, iy + is, accent);
            String glyph = switch (type) {
                case SUCCESS -> "✓";
                case ERROR -> "×";
                case WARNING -> "!";
                default -> "i";
            };
            c.drawCenteredTextWithShadow(tr, Text.literal(glyph).formatted(Formatting.BOLD), ix + is / 2, iy + 8, accent);
        }

        // title + message, vertically centred (9px gap between the icon and the text)
        int blockH = 9 + 3 + lines.size() * 10;
        int ty = oy + (boxH - 2 - blockH) / 2;
        c.drawText(tr, Text.literal(title).formatted(Formatting.BOLD), TEXT_X, ty, accent, false);
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(tr, lines.get(i), TEXT_X, ty + 12 + i * 10, 0xFFD6D9E2, false);
        }

        // countdown: seconds left + progress bar
        long left = Math.max(0, duration - elapsed);
        String secs = (int) Math.ceil(left / 1000.0) + "s";
        int sw = tr.getWidth(secs);
        c.fill(boxW - sw - 14, oy + 5, boxW - 6, oy + 16, 0x33FFFFFF);
        c.drawText(tr, secs, boxW - sw - 10, oy + 7, 0xFFC8CCD8, false);

        int barMax = boxW - 8;
        int barW = (int) (barMax * (left / (double) duration));
        c.fill(4, oy + boxH - 4, 4 + barMax, oy + boxH - 3, 0x22FFFFFF);
        if (barW > 0) c.fill(4, oy + boxH - 4, 4 + barW, oy + boxH - 3, accent);

        return elapsed >= duration ? Visibility.HIDE : Visibility.SHOW;
    }

    // ------------------------------------------------------------------ API
    public static void show(Type type, String title, String message) {
        show(type, title, message, null, 0, 0);
    }

    public static void show(Type type, String title, String message, Identifier skin, int skinW, int skinH) {
        if (!ModSettings.get().toastsEnabled) return; // notifications switched off in the options
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            ToastManager tm = mc.getToastManager();
            if (tm == null) return;
            ModToast toast = new ModToast(type, title, message, skin, skinW, skinH);
            if (tm.getToast(ModToast.class, toast.key) != null) return; // identical toast already on screen
            tm.add(toast);
            switch (type) {
                case SUCCESS -> mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
                case ERROR -> mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f));
                default -> mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
        });
    }

    public static void success(String title, String message) { show(Type.SUCCESS, title, message); }

    public static void error(String title, String message) { show(Type.ERROR, title, message); }

    public static void info(String title, String message) { show(Type.INFO, title, message); }

    public static void warning(String title, String message) { show(Type.WARNING, title, message); }
}
