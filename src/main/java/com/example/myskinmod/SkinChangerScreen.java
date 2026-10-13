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
import net.minecraft.client.util.Window;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Import page (drag & drop / direct link / Browse). It is NOT the main page any more:
 * the main page is the Skin Storage Cloud, and the arrow at the top-left returns there.
 */
@Environment(EnvType.CLIENT)
public class SkinChangerScreen extends Screen {

    private final MinecraftClient mc;
    private final Screen parent;
    private PrivateTextField urlField;
    private String currentSkinUrl;
    private long windowHandle;
    private boolean dragDropInitialized = false;
    private String droppedFilePath = null;
    private ModalOverlay overlay;

    // layout
    private int panelX, panelW, top;
    private static final int PANEL_H = 204;

    public SkinChangerScreen() {
        this(null);
    }

    public SkinChangerScreen(Screen parent) {
        super(Text.literal("Import Skin"));
        this.mc = MinecraftClient.getInstance();
        this.parent = parent;
    }

    public Screen getReturnScreen() {
        return parent;
    }

    private static String t(String key, Object... a) {
        return ModLang.t(key, a);
    }

    private void computeLayout() {
        panelW = Math.min(380, width - 20);
        panelX = (width - panelW) / 2;
        top = Math.max(26, (height - PANEL_H) / 2 + 6);
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        setupDragAndDrop();

        this.addDrawableChild(new LinkButton(8, 8, LinkButton.Icon.BACK, t("common.back"), this::close));

        int fx = panelX + 12;
        int fw = panelW - 24;
        int fieldY = top + 140;
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

        this.addDrawableChild(new StyledButton(fx, fieldY + 28, fw, 22,
                Text.literal(t("changer.load")), b -> loadFromField(), StyledButton.Style.PRIMARY));
    }

    // ------------------------------------------------------------------ actions
    private void loadFromField() {
        String input = urlField.getText().trim();
        if (input.isEmpty()) {
            ModToast.warning(t("changer.err.input.title"), t("changer.err.input.msg"));
            return;
        }
        if (input.startsWith("http://") || input.startsWith("https://")) {
            currentSkinUrl = input;
            loadFromUrl(input);
        } else {
            currentSkinUrl = null;
            loadFromFile(input);
        }
    }

    private void onBrowse() {
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

    private void openFileExplorer() {
        net.minecraft.util.Util.getIoWorkerExecutor().execute(() -> {
            String selectedFile = null;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.flip();

                selectedFile = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Skin File", null, filters, "Image Files (*.png, *.jpg, *.jpeg)", false);
            } catch (Exception e) {
                System.err.println("TinyFD Error: " + e.getMessage());
            }

            if (selectedFile == null) selectedFile = openAwtFileDialogFallback();

            if (selectedFile != null && !selectedFile.trim().isEmpty()) {
                final String path = selectedFile;
                mc.execute(() -> {
                    if (urlField != null) urlField.setText(path);
                    loadFromFile(path);
                });
            }
        });
    }

    private String openAwtFileDialogFallback() {
        try {
            java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "Select Skin File", java.awt.FileDialog.LOAD);
            dialog.setFilenameFilter((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            });
            dialog.setVisible(true);
            if (dialog.getFile() != null) {
                return new java.io.File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ------------------------------------------------------------------ drag & drop
    private void setupDragAndDrop() {
        if (dragDropInitialized) return;

        Window window = mc.getWindow();
        this.windowHandle = window.getHandle();

        mc.execute(() -> GLFW.glfwSetDropCallback(windowHandle, (win, count, names) -> {
            if (!(mc.currentScreen instanceof SkinChangerScreen)) return;

            for (int i = 0; i < count; i++) {
                long namePtr = MemoryUtil.memGetAddress(names + (long) i * Pointer.POINTER_SIZE);
                String path = MemoryUtil.memUTF8(namePtr);
                this.droppedFilePath = path;

                String lowerPath = path.toLowerCase(java.util.Locale.ROOT);
                if (lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
                    try {
                        handleDroppedFile(path);
                    } catch (IOException e) {
                        ModToast.error(t("changer.err.file.title"), t("changer.err.file.msg"));
                    }
                } else {
                    ModToast.error(t("changer.err.format.title"), t("changer.err.format.msg"));
                }
                break;
            }
        }));

        dragDropInitialized = true;
    }

    private void handleDroppedFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.size(path) > 50 * 1024 * 1024) {
            ModToast.error(t("changer.err.large.title"), t("changer.err.large.msg"));
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage skinImage = NativeImage.read(in);
            validateAndLoadImage(skinImage, filePath);
        }
    }

    // ------------------------------------------------------------------ loading
    private void loadFromUrl(String urlStr) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Minecraft Skin Mod");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    ModToast.error(t("changer.err.download.title"), t("changer.err.http", responseCode));
                    return;
                }
                try (InputStream in = connection.getInputStream()) {
                    NativeImage image = NativeImage.read(in);
                    mc.execute(() -> validateAndLoadImage(image, urlStr));
                }
            } catch (Exception e) {
                ModToast.error(t("changer.err.network.title"), t("changer.err.network.msg"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void loadFromFile(String pathStr) {
        new Thread(() -> {
            try {
                Path path = Paths.get(pathStr);
                if (!Files.exists(path)) {
                    ModToast.error(t("changer.err.notfound.title"), t("changer.err.notfound.msg"));
                    return;
                }
                try (InputStream in = Files.newInputStream(path)) {
                    NativeImage image = NativeImage.read(in);
                    mc.execute(() -> validateAndLoadImage(image, pathStr));
                }
            } catch (Exception e) {
                ModToast.error(t("changer.err.load.title"), t("changer.err.load.msg"));
            }
        }).start();
    }

    private void validateAndLoadImage(NativeImage image, String sourceUrl) {
        if (image == null) {
            ModToast.error(t("changer.err.invalid.title"), t("changer.err.invalid.msg"));
            return;
        }

        try {
            int w = image.getWidth();
            int h = image.getHeight();

            boolean isModernSize = (w == h) && (w >= 64 && w <= 8192);
            boolean isClassicSize = (w == h * 2) && (w >= 64 && w <= 1024);

            if (!isModernSize && !isClassicSize) {
                image.close();
                ModToast.error(t("changer.err.res.title"), t("changer.err.res.msg", w, h));
                return;
            }

            byte[] skinBytes = image.getBytes();
            SkinNetworkHandler.sendSkinChunks(skinBytes);

            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier skinId = mc.getTextureManager().registerDynamicTexture("custom_skin_" + mc.player.getUuid(), texture);

            mc.setScreen(new SkinTypeSelectionScreen(this, image, skinId, sourceUrl != null ? sourceUrl : this.currentSkinUrl));

        } catch (Exception e) {
            image.close();
            ModToast.error(t("changer.err.process.title"), t("changer.err.process.msg"));
        }
    }

    public static void openForHighResUrl(Screen parent, String skinUrl) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(skinUrl).openConnection();
                connection.setRequestProperty("User-Agent", "Minecraft Skin Mod");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    ModToast.error(t("changer.err.download.title"), t("changer.err.http", connection.getResponseCode()));
                    client.setScreen(parent);
                    return;
                }
                try (InputStream in = connection.getInputStream()) {
                    NativeImage skinImage = NativeImage.read(in);
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(skinImage);
                    Identifier skinId = client.getTextureManager().registerDynamicTexture("temp_highres", texture);
                    client.setScreen(new SkinTypeSelectionScreen(parent, skinImage, skinId, skinUrl));
                }
            } catch (Exception e) {
                ModToast.error(t("changer.err.load.title"), t("changer.err.url.msg"));
                client.setScreen(parent);
            }
        });
    }

    public SkinChangerScreen preloadUrl(String url) {
        if (url != null && !url.isEmpty()) {
            this.urlField.setText(url);
            this.currentSkinUrl = url;
            this.loadFromUrl(url);
        }
        return this;
    }

    // ------------------------------------------------------------------ rendering
    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        computeLayout();
        this.renderBackground(c);
        boolean modal = overlay != null && !overlay.isClosed();
        int mx = modal ? -9999 : mouseX, my = modal ? -9999 : mouseY;

        TextRenderer tr = this.textRenderer;
        c.drawCenteredTextWithShadow(tr, t("changer.title"), width / 2, Math.max(6, top - 16), 0xFFFFFFFF);

        UiKit.panel(c, panelX, top, panelW, PANEL_H);
        c.drawText(tr, t("changer.header"), panelX + 12, top + 9, UiKit.GOLD, false);

        int ix = panelX + 12, iw = panelW - 24;

        // --- destination folder banner ---
        String target = SkinStorageScreen.getImportTargetCategory();
        boolean custom = !"All".equals(target) && !"Favorites".equals(target);
        int bannerAccent = custom ? UiKit.VIOLET : UiKit.ACCENT;
        c.fill(ix, top + 28, ix + iw, top + 46, 0x40FFFFFF);
        c.fill(ix, top + 28, ix + 3, top + 46, bannerAccent);
        String prefix = t("changer.adding_to") + " ";
        c.drawText(tr, prefix, ix + 9, top + 33, 0xFFC8CCD8, false);
        String dest = custom ? "▣ " + target : "★ " + t("changer.target_all");
        c.drawText(tr, UiKit.fit(tr, dest, iw - 14 - tr.getWidth(prefix)), ix + 9 + tr.getWidth(prefix), top + 33, bannerAccent, false);

        // --- drop zone ---
        int dropY = top + 52, dropH = 60;
        c.fill(ix, dropY, ix + iw, dropY + dropH, 0x44000000);
        UiKit.dashedBorder(c, ix, dropY, iw, dropH, droppedFilePath != null ? UiKit.GREEN : 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, t("changer.drop.title"), iw - 12), width / 2, dropY + 10, 0xFFFFFFFF);
        c.fill(width / 2 - 50, dropY + 23, width / 2 + 50, dropY + 24, 0x33FFFFFF);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, t("changer.drop.formats"), iw - 12), width / 2, dropY + 31, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(tr, UiKit.fit(tr, t("changer.drop.res"), iw - 12), width / 2, dropY + 43, 0xFF888888);

        // --- selected file (path is hidden in Streamer Mode) ---
        if (droppedFilePath != null) {
            String label = t("changer.selected") + " ";
            int lw = tr.getWidth(label);
            c.drawText(tr, label, ix, top + 117, 0xFF55FF55, false);
            Privacy.draw(c, tr, droppedFilePath, ix + lw, top + 117, 0xFF55FF55, iw - lw);
        }

        c.drawText(tr, t("changer.link_label"), ix, top + 129, 0xFFAAAAAA, false);

        // --- text field frame ---
        int fieldY = top + 140, browseW = 74, boxW = iw - browseW - 6;
        UiKit.inset(c, ix, fieldY, boxW, 22, urlField != null && urlField.isFocused() ? UiKit.ACCENT : 0x55FFFFFF);

        super.render(c, mx, my, delta);

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

    @Override
    public void close() {
        if (windowHandle != 0) {
            // give the drop callback back to vanilla instead of leaving it null
            final long handle = windowHandle;
            mc.execute(() -> mc.mouse.setup(handle));
        }
        mc.setScreen(parent != null ? parent : new SkinStorageScreen(null));
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
