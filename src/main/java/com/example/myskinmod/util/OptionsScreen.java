package com.example.myskinmod.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Options page: General (language, Streamer Mode, update popup) and Changelog. Same look as Skin Storage. */
public class OptionsScreen extends Screen {

    private enum Tab { GENERAL, CHANGELOG }

    private record Hit(int x, int y, int w, int h, Runnable action) { }

    private final Screen parent;
    private Tab tab = Tab.GENERAL;
    private final List<Hit> hits = new ArrayList<>();
    private double changelogScroll = 0;
    private int changelogContentH = 0;
    private boolean draggingBar = false;

    private int sideW, mainW, startX, mainX, topY;
    private static final int PANEL_H = UiKit.PANEL_H;

    public OptionsScreen(Screen parent) {
        super(Text.literal("Options"));
        this.parent = parent;
    }

    private static String t(String key, Object... a) {
        return ModLang.t(key, a);
    }

    private void computeLayout() {
        int total = Math.min(width - 20, 430);
        sideW = Math.max(74, Math.min(104, total / 4));
        mainW = total - sideW - 8;
        startX = (width - total) / 2;
        mainX = startX + sideW + 8;
        topY = UiKit.topY(height);
    }

    @Override
    protected void init() {
        computeLayout();
        this.addDrawableChild(new LinkButton(8, 8, LinkButton.Icon.BACK, t("common.back"), this::close));
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        computeLayout();
        this.renderBackground(c);
        hits.clear();
        TextRenderer tr = this.textRenderer;

        c.drawCenteredTextWithShadow(tr, t("options.title"), width / 2, Math.max(6, topY - 16), 0xFFFFFFFF);

        drawSidebar(c, tr, mouseX, mouseY);

        UiKit.panel(c, mainX, topY, mainW, PANEL_H);
        String header = tab == Tab.GENERAL ? t("options.tab.general") : t("options.tab.changelog");
        c.drawText(tr, header, mainX + 12, topY + 9, UiKit.GOLD, false);

        if (tab == Tab.GENERAL) drawGeneral(c, tr, mouseX, mouseY);
        else drawChangelog(c, tr);

        super.render(c, mouseX, mouseY, delta);
        Privacy.badge(c, tr, height);
    }

    private void drawSidebar(DrawContext c, TextRenderer tr, int mx, int my) {
        UiKit.panel(c, startX, topY, sideW, PANEL_H);
        c.drawText(tr, UiKit.fit(tr, t("options.title"), sideW - 12), startX + 8, topY + 9, UiKit.GOLD, false);

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tb = tabs[i];
            int y = topY + 28 + i * 24;
            int x = startX + 4, w = sideW - 8;
            boolean sel = tb == tab;
            boolean hover = UiKit.in(mx, my, x, y, w, 20);
            c.fill(x, y, x + w, y + 20, sel ? 0xCC17607E : hover ? 0x55FFFFFF : 0x40000000);
            c.fill(x, y, x + 3, y + 20, sel ? UiKit.ACCENT : 0x44FFFFFF);
            if (sel) c.fill(x, y + 19, x + w, y + 20, UiKit.ACCENT);
            String label = tb == Tab.GENERAL ? t("options.tab.general") : t("options.tab.changelog");
            c.drawText(tr, Text.literal(UiKit.fit(tr, label, w - 14)).formatted(Formatting.BOLD), x + 8, y + 6,
                    sel ? 0xFFFFFFFF : 0xFFD0D4E0, false);
            final Tab target = tb;
            hits.add(new Hit(x, y, w, 20, () -> {
                tab = target;
                changelogScroll = 0;
            }));
        }

        // version at the bottom of the sidebar
        c.drawCenteredTextWithShadow(tr, "v" + Changelog.CURRENT_VERSION, startX + sideW / 2, topY + PANEL_H - 14, UiKit.GRAY);
    }

    // ---------------- General ----------------
    private void drawGeneral(DrawContext c, TextRenderer tr, int mx, int my) {
        int x = mainX + 12;
        int w = mainW - 24;
        int y = topY + 30;
        ModSettings s = ModSettings.get();

        // --- Language ---
        c.drawText(tr, Text.literal(t("options.language")).formatted(Formatting.BOLD), x, y, 0xFFFFFFFF, false);
        y += 12;
        y = drawWrapped(c, tr, t("options.language.desc"), x, y, w, 0xFF8A8FA3) + 4;

        String pref = ModLang.preference();
        int tileW = (w - 8) / 3;
        langTile(c, tr, mx, my, x, y, tileW, "auto", pref.equals("auto"), null, t("options.language.auto"));
        langTile(c, tr, mx, my, x + tileW + 4, y, tileW, "en", pref.equals("en"), "en", "English");
        langTile(c, tr, mx, my, x + (tileW + 4) * 2, y, w - (tileW + 4) * 2, "th", pref.equals("th"), "th", "ไทย");
        y += 34;

        divider(c, x, y, w);
        y += 8;

        // --- Streamer mode ---
        y = toggleRow(c, tr, mx, my, x, y, w, t("options.streamer"), t("options.streamer.desc"), s.streamerMode, () -> {
            s.streamerMode = !s.streamerMode;
            ModSettings.save();
        });

        divider(c, x, y, w);
        y += 8;

        // --- Update announcement ---
        toggleRow(c, tr, mx, my, x, y, w, t("options.popup"), t("options.popup.desc"), s.showUpdatePopup, () -> {
            s.showUpdatePopup = !s.showUpdatePopup;
            ModSettings.save();
        });
    }

    private void divider(DrawContext c, int x, int y, int w) {
        c.fill(x, y, x + w, y + 1, 0x22FFFFFF);
    }

    private int drawWrapped(DrawContext c, TextRenderer tr, String text, int x, int y, int w, int color) {
        for (OrderedText line : tr.wrapLines(Text.literal(text), w)) {
            c.drawText(tr, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void langTile(DrawContext c, TextRenderer tr, int mx, int my, int x, int y, int w,
                          String pref, boolean selected, String flag, String label) {
        int h = 24;
        boolean hover = UiKit.in(mx, my, x, y, w, h);
        c.fill(x, y, x + w, y + h, selected ? 0xFF1B5E7A : hover ? 0xFF343A52 : 0xFF262A38);
        c.fill(x, y, x + w, y + 1, selected || hover ? UiKit.ACCENT : 0x55FFFFFF);
        if (selected) c.fill(x, y + h - 2, x + w, y + h, UiKit.ACCENT);

        int fw = 18, fh = 12;
        int labelW = tr.getWidth(label);
        boolean showFlag = flag != null;
        int contentW = (showFlag ? fw + 5 : 0) + labelW;
        int cx = x + Math.max(3, (w - contentW) / 2);
        if (showFlag) {
            Flags.draw(c, flag, cx, y + (h - fh) / 2, fw, fh);
            cx += fw + 5;
        }
        c.drawText(tr, UiKit.fit(tr, label, x + w - cx - 3), cx, y + 8, 0xFFFFFFFF, false);

        hits.add(new Hit(x, y, w, h, () -> {
            ModLang.setLanguage(pref);
            this.clearAndInit(); // rebuild so every label switches immediately
        }));
    }

    /** Title + description on the left, switch on the right. Returns the next y. */
    private int toggleRow(DrawContext c, TextRenderer tr, int mx, int my, int x, int y, int w,
                          String title, String desc, boolean on, Runnable toggle) {
        int swW = 34, swH = 16;
        int textW = w - swW - 10;
        c.drawText(tr, Text.literal(UiKit.fit(tr, title, textW)).formatted(Formatting.BOLD), x, y, 0xFFFFFFFF, false);
        int ny = drawWrapped(c, tr, desc, x, y + 12, textW, 0xFF8A8FA3);

        int sx = x + w - swW, sy = y + 1;
        boolean hover = UiKit.in(mx, my, sx, sy, swW, swH);
        c.fill(sx, sy, sx + swW, sy + swH, on ? (hover ? 0xFF269B75 : 0xFF18785A) : (hover ? 0xFF4A5068 : 0xFF3A3F55));
        c.fill(sx, sy, sx + swW, sy + 1, on ? UiKit.GREEN : 0x55FFFFFF);
        int kx = on ? sx + swW - 14 : sx + 2;
        c.fill(kx, sy + 2, kx + 12, sy + swH - 2, 0xFFFFFFFF);
        hits.add(new Hit(sx - 4, sy - 2, swW + 8, swH + 4, toggle));
        return Math.max(ny, y + 22) + 6;
    }

    // ---------------- Changelog ----------------
    private void drawChangelog(DrawContext c, TextRenderer tr) {
        int x = mainX + 12;
        int w = mainW - 30;
        int vpTop = topY + 28, vpBottom = topY + PANEL_H - 6;
        int vpH = vpBottom - vpTop;

        changelogScroll = Math.max(0, Math.min(Math.max(0, changelogContentH - vpH), changelogScroll));

        c.enableScissor(mainX + 1, vpTop, mainX + mainW - 1, vpBottom);
        int y = vpTop + 2 - (int) changelogScroll;
        boolean first = true;
        for (Changelog.Release r : Changelog.RELEASES) {
            // header: version badge + date + LATEST
            String ver = "v" + r.version();
            int bw = tr.getWidth(ver) + 12;
            c.fill(x, y, x + bw, y + 14, first ? 0xFF1B5E7A : 0xFF3A3F55);
            c.fill(x, y, x + 3, y + 14, first ? UiKit.ACCENT : 0x66FFFFFF);
            c.drawText(tr, Text.literal(ver).formatted(Formatting.BOLD), x + 7, y + 3, 0xFFFFFFFF, false);
            int hx = x + bw + 6;
            if (!r.date().isEmpty()) {
                c.drawText(tr, r.date(), hx, y + 3, UiKit.GRAY, false);
                hx += tr.getWidth(r.date()) + 6;
            }
            if (first) c.drawText(tr, t("options.changelog.latest"), hx, y + 3, UiKit.GREEN, false);
            y += 20;

            for (String key : r.keys()) {
                List<OrderedText> lines = tr.wrapLines(Text.literal(t(key)), w - 12);
                c.drawText(tr, "•", x + 3, y, UiKit.ACCENT, false);
                for (OrderedText line : lines) {
                    c.drawText(tr, line, x + 12, y, 0xFFE0E0E8, false);
                    y += 10;
                }
                y += 3;
            }
            y += 8;
            first = false;
        }
        changelogContentH = y + (int) changelogScroll - vpTop + 2;
        c.disableScissor();

        if (changelogContentH > vpH) {
            int trackX = mainX + mainW - 7;
            c.fill(trackX, vpTop, trackX + 3, vpBottom, 0x66404048);
            int thumbH = Math.max(18, vpH * vpH / changelogContentH);
            int maxScroll = Math.max(1, changelogContentH - vpH);
            int thumbY = vpTop + (int) ((vpH - thumbH) * (changelogScroll / maxScroll));
            c.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, draggingBar ? 0xFFFFFFFF : UiKit.ACCENT);
        }
    }

    private void scrollFromMouse(double my) {
        int vpTop = topY + 28, vpH = PANEL_H - 34;
        if (changelogContentH <= vpH) return;
        int thumbH = Math.max(18, vpH * vpH / changelogContentH);
        double frac = Math.max(0, Math.min(1, (my - vpTop - thumbH / 2.0) / Math.max(1, vpH - thumbH)));
        changelogScroll = frac * (changelogContentH - vpH);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit h : hits) {
                if (UiKit.in(mouseX, mouseY, h.x, h.y, h.w, h.h)) {
                    h.action.run();
                    return true;
                }
            }
            if (tab == Tab.CHANGELOG && UiKit.in(mouseX, mouseY, mainX + mainW - 9, topY + 28, 9, PANEL_H - 34)) {
                draggingBar = true;
                scrollFromMouse(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingBar) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (tab == Tab.CHANGELOG) {
            changelogScroll = Math.max(0, changelogScroll - amount * 18);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
}
