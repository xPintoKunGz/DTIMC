package bloomery.dtimc.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic "choose one" dialog (folders, Figura avatars...) drawn above everything (z = 3000).
 * Click a row to highlight it; a Confirm button then appears next to Cancel. Mouse wheel scrolls the list.
 * Enter confirms the highlighted row (or highlights the suggested one), Esc cancels.
 */
public final class FolderPicker {
    /** @param icon optional glyph; defaults to a star for "main" entries and a square for the others. */
    public record Option(String id, String label, boolean main, String icon) {
        public Option(String id, String label, boolean main) {
            this(id, label, main, null);
        }
    }

    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 6;

    private final String title, subtitle;
    private final List<Option> options;
    private final String suggestedId;
    private final Consumer<String> onPick;
    private final Runnable onCancel;
    private boolean closed;
    private double scroll = 0;
    private String selectedId = null;

    private int listX, listY, listW, listH;
    private int confirmX, confirmY, confirmW, cancelX, cancelY, cancelW;

    public FolderPicker(String title, String subtitle, List<Option> options, String suggestedId,
                        Consumer<String> onPick, Runnable onCancel) {
        this.title = title;
        this.subtitle = subtitle;
        this.options = new ArrayList<>(options);
        this.suggestedId = suggestedId;
        this.onPick = onPick;
        this.onCancel = onCancel;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(suggestedId)) scroll = Math.max(0, (i - 2) * ROW_H);
        }
    }

    public boolean isClosed() { return closed; }

    private int visibleRows() { return Math.min(MAX_ROWS, options.size()); }

    private double maxScroll() { return Math.max(0, options.size() * ROW_H - visibleRows() * ROW_H); }

    public void render(DrawContext c, TextRenderer tr, int sw, int sh, int mx, int my) {
        int dlgW = Math.min(300, sw - 20);
        listW = dlgW - 32;
        listH = visibleRows() * ROW_H;
        int dlgH = 16 + 12 + 14 + 8 + listH + 12 + 20 + 14;
        int x = (sw - dlgW) / 2, y = (sh - dlgH) / 2;
        listX = x + 16;
        listY = y + 16 + 12 + 14 + 8;
        cancelY = confirmY = listY + listH + 12;

        boolean hasSelection = selectedId != null;
        int btnW = Math.min(110, (listW - 8) / 2);
        if (hasSelection) {
            confirmW = cancelW = btnW;
            confirmX = x + dlgW / 2 - 4 - btnW;
            cancelX = x + dlgW / 2 + 4;
        } else {
            cancelW = btnW;
            cancelX = x + (dlgW - cancelW) / 2;
        }

        scroll = Math.max(0, Math.min(maxScroll(), scroll));

        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 3000);

        c.fill(0, 0, sw, sh, 0xB0000000);
        c.fill(x - 1, y - 1, x + dlgW + 1, y + dlgH + 1, 0x55FFFFFF);
        c.fill(x, y, x + dlgW, y + dlgH, 0xFF17171D);
        c.fill(x, y, x + dlgW, y + 3, UiKit.ACCENT);

        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, title, dlgW - 20), sw / 2, y + 14, UiKit.ACCENT);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, subtitle, dlgW - 20), sw / 2, y + 28, 0xFF9A9FB2);

        c.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            int ry = listY + i * ROW_H - (int) scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            boolean hover = UiKit.in(mx, my, listX, Math.max(ry, listY), listW - 6, ROW_H - 2)
                    && my >= listY && my < listY + listH;
            boolean selected = o.id().equals(selectedId);
            boolean suggested = o.id().equals(suggestedId);
            int accent = o.main() ? UiKit.ACCENT : UiKit.VIOLET;

            c.fill(listX, ry, listX + listW - 6, ry + ROW_H - 2, selected ? 0xFF1B5E7A : hover ? 0xFF343A52 : 0xFF262A38);
            c.fill(listX, ry, listX + 3, ry + ROW_H - 2, accent);
            if (selected) {
                c.fill(listX, ry, listX + listW - 6, ry + 1, UiKit.ACCENT);
                c.fill(listX, ry + ROW_H - 3, listX + listW - 6, ry + ROW_H - 2, UiKit.ACCENT);
            } else if (suggested) {
                c.fill(listX, ry + ROW_H - 3, listX + listW - 6, ry + ROW_H - 2, UiKit.GOLD);
            }

            String icon = o.icon() != null ? o.icon() : (o.main() ? "★" : "▣");
            c.drawText(tr, icon, listX + 9, ry + 7, accent, false);
            String tag = suggested ? ModLang.t("type.pick.suggested") : null;
            int tagW = tag != null ? tr.getWidth(tag) + 6 : 0;
            c.drawText(tr, UiKit.fit(tr, o.label(), listW - 6 - 26 - tagW), listX + 22, ry + 7, 0xFFFFFFFF, false);
            if (tag != null) c.drawText(tr, tag, listX + listW - 6 - tagW + 3, ry + 7, UiKit.GOLD, false);
        }
        c.disableScissor();

        if (maxScroll() > 0) {
            int tx = listX + listW - 3;
            c.fill(tx, listY, tx + 3, listY + listH, 0x66404048);
            int thumbH = Math.max(14, (int) (listH * (listH / (double) (options.size() * ROW_H))));
            int thumbY = listY + (int) ((listH - thumbH) * (scroll / maxScroll()));
            c.fill(tx, thumbY, tx + 3, thumbY + thumbH, UiKit.ACCENT);
        }

        if (hasSelection) {
            boolean hover = UiKit.in(mx, my, confirmX, confirmY, confirmW, 20);
            c.fill(confirmX, confirmY, confirmX + confirmW, confirmY + 20, hover ? 0xFF269B75 : 0xFF18785A);
            if (hover) c.fill(confirmX, confirmY, confirmX + confirmW, confirmY + 1, 0xFFFFFFFF);
            c.drawCenteredTextWithShadow(tr, ModLang.t("common.confirm"), confirmX + confirmW / 2, confirmY + 6, 0xFFFFFFFF);
        }
        boolean cHover = UiKit.in(mx, my, cancelX, cancelY, cancelW, 20);
        c.fill(cancelX, cancelY, cancelX + cancelW, cancelY + 20, cHover ? 0xFF737B8C : 0xFF55555F);
        if (cHover) c.fill(cancelX, cancelY, cancelX + cancelW, cancelY + 1, 0xFFFFFFFF);
        c.drawCenteredTextWithShadow(tr, ModLang.t("common.cancel"), cancelX + cancelW / 2, cancelY + 6, 0xFFFFFFFF);

        c.getMatrices().pop();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        if (selectedId != null && UiKit.in(mx, my, confirmX, confirmY, confirmW, 20)) {
            closed = true;
            onPick.accept(selectedId);
            return true;
        }
        if (UiKit.in(mx, my, cancelX, cancelY, cancelW, 20)) {
            closed = true;
            onCancel.run();
            return true;
        }
        if (UiKit.in(mx, my, listX, listY, listW - 6, listH)) {
            int idx = (int) Math.floor((my - listY + scroll) / ROW_H);
            if (idx >= 0 && idx < options.size()) {
                int ry = listY + idx * ROW_H - (int) scroll;
                if (my < ry + ROW_H - 2) selectedId = options.get(idx).id(); // highlight first, confirm afterwards
            }
        }
        return true;
    }

    public boolean mouseScrolled(double amount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ROW_H));
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closed = true;
            onCancel.run();
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (selectedId != null) {
                closed = true;
                onPick.accept(selectedId);
            } else if (suggestedId != null) {
                selectedId = suggestedId;
            }
        }
        return true;
    }
}
