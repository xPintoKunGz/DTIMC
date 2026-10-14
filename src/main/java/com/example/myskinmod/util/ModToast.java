package com.example.myskinmod.util;

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
 * One toast for the whole mod: sized to its text, wraps to 2 lines, colour-coded by type,
 * optional skin-head icon, countdown bar, de-duplicates identical toasts, always created on the client thread.
 */
public final class ModToast implements Toast {
    public enum Type { SUCCESS, ERROR, INFO, WARNING }

    private final Type type;
    private final String key;
    private final String title;
    private final List<OrderedText> lines;
    private final Identifier skin;
    private final int skinW, skinH;
    private final int width, height;
    private final long duration;

    private ModToast(Type type, String title, String message, Identifier skin, int skinW, int skinH) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        this.type = type;
        this.key = type + "|" + title + "|" + message;
        this.skin = skin;
        this.skinW = skinW;
        this.skinH = skinH;
        this.duration = type == Type.ERROR ? 4500L : 3500L;

        int textX = 40;
        int maxTextW = 165;
        this.title = UiKit.fit(tr, title, maxTextW);
        List<OrderedText> wrapped = tr.wrapLines(Text.literal(message), maxTextW);
        this.lines = wrapped.size() > 2 ? wrapped.subList(0, 2) : wrapped;

        int widest = tr.getWidth(this.title);
        for (OrderedText l : lines) widest = Math.max(widest, tr.getWidth(l));
        this.width = Math.max(150, Math.min(textX + maxTextW + 10, textX + widest + 10));
        this.height = lines.size() > 1 ? 46 : 36;
    }

    // Both are read by the toast manager (declared without @Override so it compiles on every 1.20.x mapping)
    public int getWidth() { return width; }

    public int getHeight() { return height; }

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

        c.fill(0, 0, width, height, 0xE6101014);
        c.fill(0, 0, width, 1, 0x22FFFFFF);
        c.fill(0, height - 1, width, height, 0x22FFFFFF);
        c.fill(width - 1, 0, width, height, 0x22FFFFFF);
        c.fill(0, 0, 3, height, accent);

        int ix = 9, is = 22, iy = (height - is) / 2;
        if (skin != null) {
            c.fill(ix - 1, iy - 1, ix + is + 1, iy + is + 1, (accent & 0x00FFFFFF) | 0x99000000);
            float k = skinW / 64.0f;
            int region = Math.max(1, Math.round(8 * k));
            c.drawTexture(skin, ix, iy, is, is, 8.0f * k, 8.0f * k, region, region, skinW, skinH);
            c.drawTexture(skin, ix, iy, is, is, 40.0f * k, 8.0f * k, region, region, skinW, skinH);
        } else {
            c.fill(ix, iy, ix + is, iy + is, (accent & 0x00FFFFFF) | 0x33000000);
            String glyph = switch (type) {
                case SUCCESS -> "✓";
                case ERROR -> "×";
                case WARNING -> "!";
                default -> "i";
            };
            c.drawCenteredTextWithShadow(tr, glyph, ix + is / 2, iy + 7, accent);
        }

        int tx = 40; // 9px gap between the icon / head and the text
        int blockH = 9 + 3 + lines.size() * 10;
        int ty = (height - blockH) / 2;
        c.drawText(tr, Text.literal(title).formatted(Formatting.BOLD), tx, ty, accent, false);
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(tr, lines.get(i), tx, ty + 12 + i * 10, 0xFFD6D9E2, false);
        }

        float progress = 1.0f - Math.min(1.0f, (float) elapsed / (float) duration);
        int barW = (int) ((width - 3) * progress);
        if (barW > 0) c.fill(2, height - 2, 2 + barW, height, (accent & 0x00FFFFFF) | 0x99000000);

        return elapsed >= duration ? Visibility.HIDE : Visibility.SHOW;
    }

    // ------------------------------------------------------------------ API
    public static void show(Type type, String title, String message) {
        show(type, title, message, null, 0, 0);
    }

    public static void show(Type type, String title, String message, Identifier skin, int skinW, int skinH) {
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
