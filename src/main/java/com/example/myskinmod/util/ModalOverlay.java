package com.example.myskinmod.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic confirmation dialog drawn above EVERYTHING (z = 3000, so 3D previews and widgets can't poke through),
 * with a dark dim layer, hover feedback and 1..n buttons (2 -> side by side, 3+ -> stacked).
 */
public final class ModalOverlay {
    private record Btn(String label, int base, int hover, boolean primary, Runnable action) { }

    private final String title;
    private final List<String> lines;
    private final int accent;
    private final List<Btn> buttons = new ArrayList<>();
    private final List<int[]> rects = new ArrayList<>();
    private Runnable onEscape;
    private boolean closed;

    public ModalOverlay(String title, List<String> lines, int accent) {
        this.title = title;
        this.lines = lines;
        this.accent = accent;
    }

    public ModalOverlay button(String label, int base, int hover, boolean primary, Runnable action) {
        buttons.add(new Btn(label, base, hover, primary, action));
        return this;
    }

    public ModalOverlay onEscape(Runnable r) {
        this.onEscape = r;
        return this;
    }

    public boolean isClosed() { return closed; }

    public void close() { closed = true; }

    public void render(DrawContext c, TextRenderer tr, int sw, int sh, int mx, int my) {
        int dlgW = Math.min(300, sw - 20);
        int innerW = dlgW - 32;
        List<OrderedText> wrapped = new ArrayList<>();
        for (String l : lines) wrapped.addAll(tr.wrapLines(Text.literal(l), innerW));

        int n = buttons.size();
        boolean stacked = n >= 3;
        int btnH = 20;
        int btnArea = stacked ? n * btnH + (n - 1) * 4 : btnH;
        int dlgH = 16 + 12 + 10 + wrapped.size() * 11 + 14 + btnArea + 14;
        int x = (sw - dlgW) / 2, y = (sh - dlgH) / 2;

        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 3000);

        c.fill(0, 0, sw, sh, 0xB0000000);
        c.fill(x - 1, y - 1, x + dlgW + 1, y + dlgH + 1, 0x55FFFFFF);
        c.fill(x, y, x + dlgW, y + dlgH, 0xFF17171D);
        c.fill(x, y, x + dlgW, y + 3, accent);

        c.drawCenteredTextWithShadow(tr, title, sw / 2, y + 14, accent);
        int ty = y + 34;
        for (OrderedText l : wrapped) {
            c.drawCenteredTextWithShadow(tr, l, sw / 2, ty, 0xFFE0E0E0);
            ty += 11;
        }

        rects.clear();
        int by = y + dlgH - 14 - btnArea;
        for (int i = 0; i < n; i++) {
            int bx, bw, byy;
            if (stacked) {
                bx = x + 16;
                bw = innerW;
                byy = by + i * (btnH + 4);
            } else {
                bw = (innerW - 8 * (n - 1)) / n;
                bx = x + 16 + i * (bw + 8);
                byy = by;
            }
            rects.add(new int[]{bx, byy, bw, btnH});
            Btn b = buttons.get(i);
            boolean hover = UiKit.in(mx, my, bx, byy, bw, btnH);
            c.fill(bx, byy, bx + bw, byy + btnH, hover ? b.hover : b.base);
            if (hover) c.fill(bx, byy, bx + bw, byy + 1, 0xFFFFFFFF);
            c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, b.label, bw - 8), bx + bw / 2, byy + 6, 0xFFFFFFFF);
        }
        c.getMatrices().pop();
    }

    /** Swallows every click; runs the button that was hit. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                if (UiKit.in(mx, my, r[0], r[1], r[2], r[3])) {
                    closed = true;
                    buttons.get(i).action.run();
                    break;
                }
            }
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closed = true;
            if (onEscape != null) onEscape.run();
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            for (Btn b : buttons) {
                if (b.primary) {
                    closed = true;
                    b.action.run();
                    break;
                }
            }
        }
        return true;
    }
}
