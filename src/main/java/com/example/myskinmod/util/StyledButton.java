package com.example.myskinmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Flat button in the Skin Storage style. */
public class StyledButton extends ButtonWidget {
    public enum Style { NORMAL, PRIMARY, DANGER }

    private final Style style;
    private boolean selected;

    public StyledButton(int x, int y, int w, int h, Text message, PressAction onPress, Style style) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.style = style;
    }

    /** Used for the Classic / Slim segmented toggle. */
    public StyledButton setSelectedState(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;
        int x1 = getX(), y1 = getY(), x2 = x1 + width, y2 = y1 + height;
        boolean hover = this.active && this.isHovered();

        int bg, topLine, textColor = 0xFFFFFFFF;
        if (!this.active) {
            bg = 0x44000000;
            topLine = 0x22FFFFFF;
            textColor = 0xFF666677;
        } else if (selected) {
            bg = hover ? 0xFF22708F : 0xFF1B5E7A;
            topLine = UiKit.ACCENT;
        } else {
            switch (style) {
                case PRIMARY -> bg = hover ? 0xFF269B75 : 0xFF18785A;
                case DANGER -> bg = hover ? 0xFFB83232 : 0xFF7A2A2F;
                default -> bg = hover ? 0xFF343A52 : 0xFF262A38;
            }
            topLine = hover ? UiKit.ACCENT : 0x55FFFFFF;
        }

        c.fill(x1, y1, x2, y2, bg);
        c.fill(x1, y1, x2, y1 + 1, topLine);
        c.fill(x1, y2 - 1, x2, y2, 0x33000000);
        if (selected) c.fill(x1, y2 - 2, x2, y2, UiKit.ACCENT);

        var tr = MinecraftClient.getInstance().textRenderer;
        String label = UiKit.fit(tr, getMessage().getString(), width - 8);
        int ty = y1 + (height - 8) / 2 + (hover && !selected ? 0 : 0);
        c.drawCenteredTextWithShadow(tr, Text.literal(label).setStyle(getMessage().getStyle()), (x1 + x2) / 2, ty, textColor);
    }
}
