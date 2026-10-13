package com.example.myskinmod;

import com.example.myskinmod.gui.SkinStorageScreen;
import com.example.myskinmod.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Devathip Interface - MAIN MENU.
 * Import (drag & drop / link / Browse) plus two buttons: Skin Storage Cloud and Settings.
 * Esc / Close leave the menu. When it is opened from an empty folder in Skin Storage Cloud ("targetCategory" set)
 * it also shows which folder the skin is being added to, and Esc / the back arrow return to the Storage.
 */
@Environment(EnvType.CLIENT)
public class SkinChangerScreen extends Screen {

    private final MinecraftClient mc;
    private final Screen parent;
    private final String targetCategory;

    private PrivateTextField urlField;
    private StyledButton loadButton;
    private long windowHandle;
    private String droppedFilePath = null;
    private volatile boolean busy = false;
    private volatile boolean browseOpen = false;
    private ModalOverlay overlay;

    // layout
    private int panelX, panelW, panelH, top, bannerOffset;
    private static final int BASE_H = 208;

    public SkinChangerScreen() {
        this(null, null);
    }

    public SkinChangerScreen(Screen parent) {
        this(parent, null);
    }

    /** @param targetCategory folder shown in the "Adding to" banner, or null for no banner. */
    public SkinChangerScreen(Screen parent, String targetCategory) {
        super(Text.literal("Devathip Interface"));
        this.mc = MinecraftClient.getInstance();
        this.parent = parent;
        this.targetCategory = targetCategory;
        // Opened from the main menu itself: skins go to "All", forget any earlier folder choice.
        if (targetCategory == null) SkinStorageScreen.clearImportTargetCategory();
    }

    public Screen getReturnScreen() {
        return parent;
    }

    /** Called when the player comes back from the skin type screen: no file path, no green outline. */
    public void clearImportState() {
        droppedFilePath = null;
        if (urlField != null) {
            String txt = urlField.getText().trim();
            if (!txt.startsWith("http://") && !txt.startsWith("https://")) urlField.setText("");
        }
    }

    private static String t(String key, Object... a) {
        return ModLang.t(key, a);
    }

    private void computeLayout() {
        panelW = Math.min(380, width - 20);
        panelX = (width - panelW) / 2;
        bannerOffset = targetCategory != null ? 22 : 0;
        panelH = BASE_H + bannerOffset;
        top = Math.max(26, (height - panelH) / 2 + 6);
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        registerDropCallback();

        if (parent != null) {
            this.addDrawableChild(new LinkButton(8, 8, LinkButton.Icon.BACK, t("common.back"), this::close));
        }
        LinkButton closeLink = new LinkButton(0, 8, LinkButton.Icon.CLOSE, t("common.close"), () -> mc.setScreen(null));
        closeLink.setX(width - 8 - closeLink.getWidth());
        this.addDrawableChild(closeLink);

        int fx = panelX + 12;
        int fw = panelW - 24;
        int by = bannerOffset;
        int fieldY = top + 112 + by;
        int browseW = 74;
        int boxW = fw - browseW - 6;

        String old = urlField != null ? urlField.getText() : "";
        urlField = new PrivateTextField(textRenderer, fx + 6, fieldY + 7, boxW - 12, 12);
        urlField.setMaxLength(Integer.MAX_VALUE);
        urlField.setPlaceholder(Text.literal(t("changer.placeholder")));
        urlField.setText(old);
        this.addDrawableChild(urlField);

        this.addDrawableChild(new StyledButton(fx + fw - browseW, fieldY, browseW, 22,
                Text.literal(t("changer.browse")), b -> onBrowse(), StyledButton.Style.NORMAL));

        loadButton = new StyledButton(fx, fieldY + 28, fw, 22, Text.literal(t("changer.load")),
                b -> loadFromField(), StyledButton.Style.PRIMARY);
        this.addDrawableChild(loadButton);

        // main menu buttons
        int rowY = top + 174 + by;
        int storageW = (fw - 6) * 6 / 10;
        this.addDrawableChild(new StyledButton(fx, rowY, storageW, 22, Text.literal(t("changer.btn.storage")),
                b -> mc.setScreen(new SkinStorageScreen(this)), StyledButton.Style.NORMAL));
        this.addDrawableChild(new StyledButton(fx + storageW + 6, rowY, fw - storageW - 6, 22,
                Text.literal(t("changer.btn.settings")), b -> mc.setScreen(new OptionsScreen(this)),
                StyledButton.Style.NORMAL));
    }

    // ------------------------------------------------------------------ import actions
    private void loadFromField() {
        String input = urlField.getText().trim();
        if (input.isEmpty()) {
            ModToast.warning(t("changer.err.input.title"), t("changer.err.input.msg"));
            return;
        }
        if (input.startsWith("http://") || input.startsWith("https://")) loadFromUrl(input);
        else loadFromFile(input);
    }

    private void onBrowse() {
        if (browseOpen || busy) return;
        if (Privacy.enabled()) {
            overlay = new ModalOverlay(t("streamer.browse.title"),
                    List.of(t("streamer.browse.line1"), t("streamer.browse.line2")), UiKit.GOLD)
                    .button(t("common.cancel"), 0xFF55555F, 0xFF737B8C, false, () -> { })
                    .button(t("streamer.browse.continue"), 0xFFB7791F, 0xFFD99A2B, false, this::openFileExplorer)
                    .onEscape(() -> { });
        } else {
            openFileExplorer();
        }
    }

    /** Native file window (TinyFD). Only the file PATH is returned here; decoding happens in loadFromFile(). */
    private void openFileExplorer() {
        browseOpen = true;
        net.minecraft.util.Util.getIoWorkerExecutor().execute(() -> {
            String selected = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.flip();
                selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Skin File", null, filters, "Image Files (*.png, *.jpg, *.jpeg)", false);
            } catch (Throwable e) {
                e.printStackTrace();
                ModToast.error(t("changer.err.browse.title"), t("changer.err.browse.msg"));
            } finally {
                browseOpen = false;
            }

            if (selected != null && !selected.trim().isEmpty()) {
                final String path = selected.trim();
                mc.execute(() -> {
                    if (urlField != null) urlField.setText(path);
                    loadFromFile(path);
                });
            }
        });
    }

    private void loadFromUrl(String urlStr) {
        runLoad(urlStr, () -> ImageLoader.readUrl(urlStr));
    }

    private void loadFromFile(String pathStr) {
        runLoad(pathStr, () -> ImageLoader.readFile(Paths.get(pathStr)));
    }

    private interface Loader {
        NativeImage load() throws Exception;
    }

    private void runLoad(String source, Loader loader) {
        if (busy) return;
        busy = true;
        if (loadButton != null) loadButton.active = false;
        Thread th = new Thread(() -> {
            try {
                NativeImage image = loader.load();
                mc.execute(() -> openTypeSelection(image, source));
            } catch (Throwable t) {
                ImageLoader.toast(t);
            } finally {
                mc.execute(() -> {
                    busy = false;
                    if (loadButton != null) loadButton.active = true;
                });
            }
        }, "skin-import");
        th.setDaemon(true);
        th.start();
    }

    /** The image is already validated and decoded as RGBA by ImageLoader. Nothing is sent to the server yet. */
    private void openTypeSelection(NativeImage image, String source) {
        try {
            String name = "custom_skin_" + (mc.player != null ? mc.player.getUuid().toString() : "local");
            Identifier skinId = mc.getTextureManager().registerDynamicTexture(name, new NativeImageBackedTexture(image));
            mc.setScreen(new SkinTypeSelectionScreen(this, image, skinId, source));
        } catch (Throwable e) {
            image.close();
            ImageLoader.toast(e);
        }
    }

    public static void openForHighResUrl(Screen parent, String skinUrl) {
        MinecraftClient client = MinecraftClient.getInstance();
        Thread th = new Thread(() -> {
            try {
                NativeImage image = ImageLoader.readUrl(skinUrl);
                client.execute(() -> {
                    Identifier skinId = client.getTextureManager().registerDynamicTexture("temp_highres", new NativeImageBackedTexture(image));
                    client.setScreen(new SkinTypeSelectionScreen(parent, image, skinId, skinUrl));
                });
            } catch (Throwable e) {
                ImageLoader.toast(e);
                client.execute(() -> client.setScreen(parent));
            }
        }, "skin-import-url");
        th.setDaemon(true);
        th.start();
    }

    public SkinChangerScreen preloadUrl(String url) {
        if (url != null && !url.isEmpty()) {
            this.urlField.setText(url);
            this.loadFromUrl(url);
        }
        return this;
    }

    // ------------------------------------------------------------------ drag & drop
    private void registerDropCallback() {
        this.windowHandle = mc.getWindow().getHandle();
        final long handle = windowHandle;
        mc.execute(() -> GLFW.glfwSetDropCallback(handle, (win, count, names) -> {
            if (!(mc.currentScreen instanceof SkinChangerScreen screen) || screen != this) return;
            if (count <= 0) return;

            long namePtr = MemoryUtil.memGetAddress(names + 0L * Pointer.POINTER_SIZE);
            String path = MemoryUtil.memUTF8(namePtr);
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                droppedFilePath = path;
                loadFromFile(path);
            } else {
                ModToast.error(t("changer.err.format.title"), t("changer.err.format.msg"));
            }
        }));
    }

    /** Give the drop callback back to vanilla whenever this screen is left (it is re-registered by init()). */
    @Override
    public void removed() {
        if (windowHandle != 0) {
            final long handle = windowHandle;
            mc.execute(() -> mc.mouse.setup(handle));
        }
        super.removed();
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        computeLayout();
        this.renderBackground(c);
        boolean modal = overlay != null && !overlay.isClosed();
        int mx = modal ? -9999 : mouseX, my = modal ? -9999 : mouseY;

        TextRenderer tr = this.textRenderer;
        c.drawCenteredTextWithShadow(tr, t("app.title"), width / 2, Math.max(6, top - 16), 0xFFFFFFFF);

        UiKit.panel(c, panelX, top, panelW, panelH);
        c.drawText(tr, t("changer.header"), panelX + 12, top + 9, UiKit.GOLD, false);

        int ix = panelX + 12, iw = panelW - 24;

        // folder banner: only when opened from an empty folder in Skin Storage Cloud
        if (targetCategory != null) {
            boolean custom = !"All".equals(targetCategory) && !"Favorites".equals(targetCategory);
            int accent = custom ? UiKit.VIOLET : UiKit.ACCENT;
            c.fill(ix, top + 28, ix + iw, top + 46, 0x40FFFFFF);
            c.fill(ix, top + 28, ix + 3, top + 46, accent);
            String prefix = t("changer.adding_to") + " ";
            c.drawText(tr, prefix, ix + 9, top + 33, 0xFFC8CCD8, false);
            String dest = custom ? "▣ " + targetCategory : "★ " + t("changer.target_all");
            c.drawText(tr, UiKit.fit(tr, dest, iw - 14 - tr.getWidth(prefix)), ix + 9 + tr.getWidth(prefix), top + 33, accent, false);
        }

        // drop zone
        int dropY = top + 28 + bannerOffset, dropH = 56;
        c.fill(ix, dropY, ix + iw, dropY + dropH, 0x44000000);
        UiKit.dashedBorder(c, ix, dropY, iw, dropH, droppedFilePath != null ? UiKit.GREEN : 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, t("changer.drop.title"), iw - 12), width / 2, dropY + 9, 0xFFFFFFFF);
        c.fill(width / 2 - 50, dropY + 22, width / 2 + 50, dropY + 23, 0x33FFFFFF);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, t("changer.drop.formats"), iw - 12), width / 2, dropY + 29, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, t("changer.drop.res"), iw - 12), width / 2, dropY + 41, 0xFF888888);

        // selected file (hidden in Streamer Mode, cleared when coming back from the type screen)
        if (droppedFilePath != null) {
            String label = t("changer.selected") + " ";
            int lw = tr.getWidth(label);
            c.drawText(tr, label, ix, top + 88 + bannerOffset, 0xFF55FF55, false);
            Privacy.draw(c, tr, droppedFilePath, ix + lw, top + 88 + bannerOffset, 0xFF55FF55, iw - lw);
        }

        c.drawText(tr, t("changer.link_label"), ix, top + 100 + bannerOffset, 0xFFAAAAAA, false);

        int fieldY = top + 112 + bannerOffset, browseW = 74, boxW = iw - browseW - 6;
        UiKit.inset(c, ix, fieldY, boxW, 22, urlField != null && urlField.isFocused() ? UiKit.ACCENT : 0x55FFFFFF);

        // divider between import and the two menu buttons
        c.fill(ix, top + 166 + bannerOffset, ix + iw, top + 167 + bannerOffset, 0x22FFFFFF);

        super.render(c, mx, my, delta);

        // credit
        String credit = t("app.credit");
        c.drawText(tr, credit, width - 8 - tr.getWidth(credit), height - 14, 0xFF6C7186, false);

        Privacy.badge(c, tr, height);
        if (modal) overlay.render(c, tr, width, height, mouseX, mouseY);
        else if (overlay != null) overlay = null;
    }

    // ------------------------------------------------------------------ input
    private boolean overlayActive() {
        if (overlay != null && overlay.isClosed()) overlay = null;
        return overlay != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overlayActive()) {
            overlay.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlayActive()) return overlay.keyPressed(keyCode);
        if (urlField != null && urlField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            loadFromField();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (overlayActive()) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Esc: leave the menu (or return to Skin Storage Cloud when we came from there). */
    @Override
    public void close() {
        mc.setScreen(parent);
    }

    // ------------------------------------------------------------------ URL / path field that hides its content
    private static class PrivateTextField extends TextFieldWidget {
        PrivateTextField(TextRenderer tr, int x, int y, int w, int h) {
            super(tr, x, y, w, h, Text.literal(""));
            this.setDrawsBackground(false);
            this.setRenderTextProvider((text, first) ->
                    OrderedText.styledForwardsVisitedString(Privacy.masked() ? "•".repeat(text.length()) : text, Style.EMPTY));
        }
    }
}
