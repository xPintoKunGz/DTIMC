package com.example.myskinmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Icon + text link (no Minecraft button frame). Used for the back arrow and the Options link. */
public class LinkButton extends ClickableWidget {
    public enum Icon { BACK, OPTIONS, CLOSE }

    private final Icon icon;
    private final Runnable action;
    private static final int ICON_W = 12;

    public LinkButton(int x, int y, Icon icon, String label, Runnable action) {
        super(x, y, ICON_W + 6 + MinecraftClient.getInstance().textRenderer.getWidth(label) + 6, 16, Text.literal(label));
        this.icon = icon;
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        action.run();
    }

    @Override
    public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;
        boolean hover = this.isHovered();
        int color = hover ? UiKit.ACCENT : 0xFFD0D4E0;
        int cy = getY() + height / 2;
        int x = getX();

        if (hover) c.fill(x - 3, getY(), x + width, getY() + height, 0x33FFFFFF);

        if (icon == Icon.BACK) {
            for (int i = 0; i < 5; i++) c.fill(x + i, cy - i, x + i + 1, cy + i + 1, color);
            c.fill(x + 4, cy - 1, x + ICON_W, cy + 2, color);
        } else if (icon == Icon.CLOSE) {
            for (int i = 0; i < 9; i++) {
                c.fill(x + 1 + i, cy - 4 + i, x + 2 + i, cy - 3 + i, color);
                c.fill(x + 1 + i, cy + 4 - i, x + 2 + i, cy + 5 - i, color);
            }
        } else {
            int[] knob = {3, 8, 5};
            for (int i = 0; i < 3; i++) {
                int ly = cy - 4 + i * 4;
                c.fill(x, ly, x + ICON_W, ly + 1, color);
                c.fill(x + knob[i], ly - 1, x + knob[i] + 3, ly + 2, color);
            }
        }
        c.drawTextWithShadow(tr, getMessage(), x + ICON_W + 6, cy - 4, color);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
