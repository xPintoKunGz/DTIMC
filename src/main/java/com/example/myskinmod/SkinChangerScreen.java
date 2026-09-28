package com.example.myskinmod;

import com.example.myskinmod.util.SkinNetworkHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
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

@Environment(EnvType.CLIENT)
public class SkinChangerScreen extends Screen {

    private final MinecraftClient client;
    private Identifier previewSkinId;
    private ClientPlayerEntity previewPlayer;
    private TextFieldWidget urlField;
    private NativeImage currentImage;
    private String currentSkinUrl;
    private String currentModelType = "default";
    private long windowHandle;
    private boolean dragDropInitialized = false;
    private static final String MOD_CHANGELOG = "Updated: Added Error Toasts & 64x32 resolution support.";

    public SkinChangerScreen() {
        super(Text.literal("NUI Unified Interface - Menu"));
        this.client = MinecraftClient.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        setupDragAndDrop();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.urlField = new TextFieldWidget(
                this.textRenderer,
                centerX - 150,
                centerY + 25,
                230,
                20,
                Text.literal("")
        );
        this.urlField.setMaxLength(Integer.MAX_VALUE);
        this.urlField.setPlaceholder(Text.literal("Paste URL or File Path here..."));
        this.addDrawableChild(this.urlField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("📂 Browse"), button -> {
            openFileExplorer();
        }).position(centerX + 85, centerY + 25).size(65, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Load Skin"), button -> {
            String input = this.urlField.getText().trim();
            if (input.isEmpty()) {
                showErrorToast("Input Error", "Please enter a URL or file path");
                return;
            }

            if (input.startsWith("http://") || input.startsWith("https://")) {
                currentSkinUrl = input;
                loadFromUrl(input);
            } else {
                currentSkinUrl = null;
                loadFromFile(input);
            }
        }).position(centerX - 100, centerY + 70).size(200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
            close();
        }).position(centerX - 100, centerY + 95).size(200, 20).build());
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
                        "Select Skin File",
                        null,
                        filters,
                        "Image Files (*.png, *.jpg, *.jpeg)",
                        false
                );
            } catch (Exception e) {
                System.err.println("TinyFD Error: " + e.getMessage());
            }

            if (selectedFile == null) {
                selectedFile = openAwtFileDialogFallback();
            }

            if (selectedFile != null && !selectedFile.trim().isEmpty()) {
                final String path = selectedFile;
                client.execute(() -> {
                    if (urlField != null) {
                        urlField.setText(path);
                    }
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

    private void setupDragAndDrop() {
        if (dragDropInitialized) return;

        Window window = client.getWindow();
        this.windowHandle = window.getHandle();

        client.execute(() -> {
            GLFW.glfwSetDropCallback(windowHandle, (win, count, names) -> {
                if (!(client.currentScreen instanceof SkinChangerScreen)) {
                    return;
                }

                for (int i = 0; i < count; i++) {
                    long namePtr = MemoryUtil.memGetAddress(names + i * Pointer.POINTER_SIZE);
                    String path = MemoryUtil.memUTF8(namePtr);

                    this.droppedFilePath = "Dropped file: " + path;

                    if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                        try {
                            handleDroppedFile(path);
                        } catch (IOException e) {
                            showErrorToast("File Error", "Could not read dropped file.");
                        }
                    } else {
                        showErrorToast("Invalid Format", "Only PNG or JPG files are supported");
                    }

                    break;
                }
            });
        });

        dragDropInitialized = true;
    }

    private void handleDroppedFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.size(path) > 50 * 1024 * 1024) {
            showErrorToast("File Too Large", "Max file size is 50MB.");
            return;
        }

        NativeImage skinImage = NativeImage.read(Files.newInputStream(path));
        validateAndLoadImage(skinImage, filePath);
    }

    private void loadFromUrl(String urlStr) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Minecraft Skin Mod");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    showErrorToast("Download Failed", "HTTP Error: " + responseCode);
                    return;
                }

                try (InputStream in = connection.getInputStream()) {
                    NativeImage image = NativeImage.read(in);
                    client.execute(() -> validateAndLoadImage(image, urlStr));
                }
            } catch (Exception e) {
                showErrorToast("Network Error", "Could not download skin.");
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
                    showErrorToast("File Not Found", "Check if the path is correct.");
                    return;
                }
                try (InputStream in = Files.newInputStream(path)) {
                    NativeImage image = NativeImage.read(in);
                    client.execute(() -> validateAndLoadImage(image, pathStr));
                }
            } catch (Exception e) {
                showErrorToast("Load Error", "Unable to load the file.");
            }
        }).start();
    }

    // ฟังก์ชันรวมสำหรับตรวจสอบขนาดสกินและรันหน้าต่างต่อไป
    private void validateAndLoadImage(NativeImage image, String sourceUrl) {
        if (image == null) {
            showErrorToast("Invalid Image", "Image file is corrupted or empty.");
            return;
        }

        try {
            int width = image.getWidth();
            int height = image.getHeight();

            // --- เช็คสัดส่วนสกินแบบ 1:1 (Modern) และ 2:1 (Classic) ---
            boolean isModernSize = (width == height) && (width >= 64 && width <= 8192);
            boolean isClassicSize = (width == height * 2) && (width >= 64 && width <= 1024);

            if (!isModernSize && !isClassicSize) {
                image.close();
                showErrorToast("Unsupported Resolution", "Your skin is " + width + "x" + height);
                return;
            }

            this.currentImage = image;

            byte[] skinBytes = image.getBytes();
            SkinNetworkHandler.sendSkinChunks(skinBytes);

            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier skinId = client.getTextureManager().registerDynamicTexture("custom_skin_" + client.player.getUuid(), texture);

            client.setScreen(new SkinTypeSelectionScreen(this, image, skinId, sourceUrl != null ? sourceUrl : this.currentSkinUrl));

        } catch (Exception e) {
            if (image != null) image.close();
            showErrorToast("Processing Error", "Failed to process skin image.");
        }
    }

    public static void openForHighResUrl(Screen parent, String skinUrl) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(skinUrl).openConnection();
                connection.setRequestProperty("User-Agent", "Minecraft Skin Mod");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    if (client.getToastManager() != null) {
                        client.getToastManager().add(new SkinErrorToast("HTTP Error", "Code: " + connection.getResponseCode()));
                    }
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
                if (client.getToastManager() != null) {
                    client.getToastManager().add(new SkinErrorToast("Error Loading", "Unable to load URL"));
                }
                client.setScreen(parent);
            }
        });
    }

    // ===================== ระบบ Error Toast =====================
    private void showErrorToast(String title, String message) {
        client.execute(() -> {
            if (client.getToastManager() != null) {
                client.getToastManager().add(new SkinErrorToast(title, message));
            }
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[Skin Error] " + title + ": " + message), false);
            }
        });
    }

    private static class SkinErrorToast implements net.minecraft.client.toast.Toast {
        private final String title;
        private final String message;

        public SkinErrorToast(String title, String message) {
            this.title = title;
            this.message = message;
        }

        @Override
        public net.minecraft.client.toast.Toast.Visibility draw(DrawContext context, net.minecraft.client.toast.ToastManager manager, long startTime) {
            context.fill(0, 0, 160, 32, 0xDD441111); // พื้นหลังแดงเข้ม
            context.fill(0, 0, 160, 1, 0xFFFF5555); // ขอบแดงสว่าง
            context.fill(0, 31, 160, 32, 0xFFFF5555);
            context.fill(0, 0, 1, 32, 0xFFFF5555);
            context.fill(159, 0, 160, 32, 0xFFFF5555);

            context.drawText(manager.getClient().textRenderer, this.title, 8, 7, 0xFFFF5555, false);

            // ตัดคำถ้ายาวเกินไปจะได้ไม่ล้น Toast
            String displayMsg = this.message.length() > 25 ? this.message.substring(0, 22) + "..." : this.message;
            context.drawText(manager.getClient().textRenderer, displayMsg, 8, 18, 0xFFFFFFFF, false);

            return startTime >= 5000L ? Visibility.HIDE : Visibility.SHOW;
        }
    }
    // =========================================================

    private void drawDashedBorder(DrawContext context, int x, int y, int width, int height, int color) {
        int dashLength = 4;
        for (int i = 0; i < width; i += dashLength * 2) {
            int w = Math.min(dashLength, width - i);
            context.fill(x + i, y, x + i + w, y + 1, color);
            context.fill(x + i, y + height - 1, x + i + w, y + height, color);
        }
        for (int i = 0; i < height; i += dashLength * 2) {
            int h = Math.min(dashLength, height - i);
            context.fill(x, y + i, x + 1, y + i + h, color);
            context.fill(x + width - 1, y + i, x + width, y + i + h, color);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int boxWidth = 340;
        int boxHeight = 140;
        int boxX = this.width / 2 - boxWidth / 2;
        int boxY = this.height / 2 - 80;

        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x44000000);
        drawDashedBorder(context, boxX, boxY, boxWidth, boxHeight, 0xFFAAAAAA);

        int textY = boxY + 15;
        context.drawCenteredTextWithShadow(this.textRenderer, "Drag & Drop your skin file here", this.width / 2, textY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━", this.width / 2, textY + 12, 0x44FFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, "Supports: PNG, JPG (Max 10MB)", this.width / 2, textY + 28, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, "Resolution: 64x64 to 8192x8192 | 64x32 to 1024x512", this.width / 2, textY + 40, 0x888888);

        context.drawCenteredTextWithShadow(this.textRenderer, "Or use a direct image link below:", this.width / 2, textY + 60, 0xAAAAAA);

        if (droppedFilePath != null) {
            String displayPath = droppedFilePath.length() > 45 ? droppedFilePath.substring(0, 42) + "..." : droppedFilePath;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(displayPath), this.width / 2, boxY - 15, 0x55FF55);
        }

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(MOD_CHANGELOG), this.width / 2, this.height - 20, 0x666666);
    }

    private String droppedFilePath = null;

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (windowHandle != 0) {
            client.execute(() -> GLFW.glfwSetDropCallback(windowHandle, null));
        }
        super.close();
    }

    public SkinChangerScreen preloadUrl(String url) {
        if (url != null && !url.isEmpty()) {
            this.urlField.setText(url);
            this.currentSkinUrl = url;
            this.loadFromUrl(url);
        }
        return this;
    }
}