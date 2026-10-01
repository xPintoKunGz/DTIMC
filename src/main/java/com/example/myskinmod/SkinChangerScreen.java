package com.example.myskinmod;

import com.example.myskinmod.gui.SkinStorageScreen;
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
    private static final String MOD_CHANGELOG = "Updated: Skin Cloud Storage & 3D Interactive Preview Added.";

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

        int totalWidth = 290;
        int halfWidth = 142;
        int startX = centerX - (totalWidth / 2);
        int rightX = startX + totalWidth - halfWidth;

        // 🎯 1. กำหนดอิงจากพิกเซลเดียวกับ render()
        int boxY = centerY - 90;
        int boxHeight = 64;

        // 🎯 2. วางตำแหน่ง Y ของ urlField ไว้ใต้ข้อความเตือน (boxY + boxHeight + 28px)
        int inputY = boxY + boxHeight + 28; // เท่ากับ centerY + 2

        // ช่องใส่ URL หรือ Path ไฟล์
        this.urlField = new TextFieldWidget(
                this.textRenderer,
                startX,
                inputY,
                218,
                20,
                Text.literal("")
        );
        this.urlField.setMaxLength(Integer.MAX_VALUE);
        this.urlField.setPlaceholder(Text.literal("Paste URL or File Path here..."));
        this.addDrawableChild(this.urlField);

        // ปุ่ม 📂 Browse (เรียงตามแนว Y เดียวกับ urlField)
        this.addDrawableChild(new CustomStyledButton(startX + 224, inputY, 66, 20, Text.literal("📂 Browse"), button -> {
            openFileExplorer();
        }, false));

        // --- ปุ่มกดแถวที่ 1: Load Skin (ต่อจากช่องกรอก URL 26px) ---
        this.addDrawableChild(new CustomStyledButton(startX, inputY + 26, totalWidth, 20, Text.literal("Load Skin"), button -> {
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
        }, true));

        // --- ปุ่มกดแถวที่ 2: Skin Storage Cloud และ Options ---
        this.addDrawableChild(new CustomStyledButton(startX, inputY + 50, halfWidth, 20, Text.literal("Skin Storage Cloud"), button -> {
            client.setScreen(new SkinStorageScreen(this));
        }, false));

        this.addDrawableChild(new CustomStyledButton(rightX, inputY + 50, halfWidth, 20, Text.literal("Options"), button -> {
            // TODO: โค้ดสำหรับเมนู Options ในอนาคต
        }, false));

        // --- ปุ่มกดแถวที่ 3: ปุ่ม Close ---
        this.addDrawableChild(new CustomStyledButton(startX, inputY + 74, totalWidth, 20, Text.literal("Close"), button -> {
            close();
        }, false));
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

    private void validateAndLoadImage(NativeImage image, String sourceUrl) {
        if (image == null) {
            showErrorToast("Invalid Image", "Image file is corrupted or empty.");
            return;
        }

        try {
            int width = image.getWidth();
            int height = image.getHeight();

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

    // ===================== ระบบ Error Toast สไตล์ Custom =====================
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
        // 🎯 1. กำหนดตำแหน่งไฟล์รูปภาพไอคอน
        private static final Identifier ERROR_ICON = new Identifier("myskinmod", "textures/gui/decline.png");

        private final String title;
        private final String message;
        private final long TOTAL_TIME = 5000L;

        public SkinErrorToast(String title, String message) {
            this.title = title;
            this.message = message;
        }

        @Override
        public net.minecraft.client.toast.Toast.Visibility draw(DrawContext context, net.minecraft.client.toast.ToastManager manager, long startTime) {
            int backgroundColor = 0xDD441111;
            int borderColor = 0xFFFF5555;
            int progressColor = 0xFFAA2222;

            // วาดพื้นหลังและขอบ Toast
            context.fill(0, 0, 160, 32, backgroundColor);
            context.fill(0, 0, 160, 1, borderColor);
            context.fill(0, 31, 160, 32, borderColor);
            context.fill(0, 0, 1, 32, borderColor);
            context.fill(159, 0, 160, 32, borderColor);

            // 🎯 2. วาดรูปภาพไอคอน (ขนาด 16x16 พิกเซล ที่ตำแหน่ง X=6, Y=8)
            // drawTexture(Identifier, x, y, u, v, width, height, textureWidth, textureHeight)
            context.drawTexture(ERROR_ICON, 6, 8, 0, 0, 16, 16, 16, 16);

            // คำนวณหลอดเวลานับถอยหลัง Progress Bar
            float progress = 1.0f - ((float) startTime / (float) TOTAL_TIME);
            int progressBarWidth = (int) (158 * progress);
            if (progressBarWidth > 0) {
                context.fill(1, 29, 1 + progressBarWidth, 31, progressColor);
            }

            // 🎯 3. แสดงข้อความ Title (ขยับ X จาก 8 เป็น 26 เพื่อหลบไอคอน)
            context.drawText(manager.getClient().textRenderer, this.title, 26, 6, borderColor, false);

            // แสดงข้อความ Message ด้านล่างไอคอน/ข้อความ
            String displayMsg = this.message.length() > 22 ? this.message.substring(0, 19) + "..." : this.message;
            context.drawText(manager.getClient().textRenderer, displayMsg, 26, 17, 0xFFE0E0E0, false);

            return startTime >= TOTAL_TIME ? Visibility.HIDE : Visibility.SHOW;
        }
    }

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

        // 1. หัวข้อหน้าต่าง (Title ด้านบนสุด)
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // 2. ขนาดและตำแหน่งกรอบ Drag & Drop
        int boxWidth = 340;
        int boxHeight = 64; // ความสูงที่พอดีกับเนื้อหาภายในกรอบ 4 บรรทัด
        int boxX = this.width / 2 - boxWidth / 2;
        int boxY = this.height / 2 - 90; // ขยับขึ้นเล็กน้อยเพื่อเผื่อพื้นที่ให้ช่องใส่ URL และปุ่มกดด้านล่าง

        // 3. แสดง Path ไฟล์ที่ถูก Drag & Drop (ถ้ามี จะแสดงสีเขียวเหนือกรอบ)
        if (droppedFilePath != null) {
            String displayPath = droppedFilePath.length() > 45 ? droppedFilePath.substring(0, 42) + "..." : droppedFilePath;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Selected: " + displayPath), this.width / 2, boxY - 14, 0x55FF55);
        }

        // 4. วาดพื้นหลังและขอบประของกรอบ Drag & Drop
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x44000000);
        drawDashedBorder(context, boxX, boxY, boxWidth, boxHeight, 0xFFAAAAAA);

        // 5. ข้อความภายในกรอบ Drag & Drop (จัดระยะ Y ห่างกันบรรทัดละ 11-12px)
        context.drawCenteredTextWithShadow(this.textRenderer, "Drag & Drop your skin file here", this.width / 2, boxY + 9, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━", this.width / 2, boxY + 20, 0x44FFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Supports: PNG, JPG (Max 10MB)", this.width / 2, boxY + 32, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, "Resolution: 64x64 to 8192x8192 | 64x32 to 1024x512", this.width / 2, boxY + 44, 0x888888);

        // 6. ข้อความกำกับด้านล่างกรอบ (เว้นระยะ 12px นอกกรอบ สำหรับเป็นหัวข้อของช่อง URL)
        context.drawCenteredTextWithShadow(this.textRenderer, "Or use a direct image link below:", this.width / 2, boxY + boxHeight + 12, 0xAAAAAA);

        // 7. วาดปุ่มกดและ TextFieldWidget ทั้งหมด (ผ่าน super.render)
        super.render(context, mouseX, mouseY, delta);

        // 8. แสดง Changelog ด้านล่างสุดของหน้าจอ
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(MOD_CHANGELOG), this.width / 2, this.height - 18, 0x666666);
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

    // ===================== ปุ่ม Custom Style =====================
    private static class CustomStyledButton extends ButtonWidget {
        private final boolean isConfirmButton;

        public CustomStyledButton(int x, int y, int width, int height, Text message, PressAction onPress, boolean isConfirm) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.isConfirmButton = isConfirm;
        }

        @Override
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!this.visible) return;

            int x1 = this.getX();
            int y1 = this.getY();
            int x2 = x1 + this.width;
            int y2 = y1 + this.height;

            int backgroundColor = 0xAA000000;
            int borderColor = 0x44FFFFFF;
            int textColor = 0xFFFFFFFF;

            if (!this.active) {
                backgroundColor = isConfirmButton ? 0xAA22AA22 : 0xAA55FF55;
                borderColor = 0xFF55FF55;
            } else if (this.isSelected()) {
                backgroundColor = isConfirmButton ? 0xDD228822 : 0x6655FF55;
                borderColor = 0xFFFFFFFF;
                textColor = 0xFFFFFF55;
            }

            context.fill(x1, y1, x2, y2, backgroundColor);
            context.fill(x1, y1, x2, y1 + 1, borderColor);
            context.fill(x1, y2 - 1, x2, y2, borderColor);
            context.fill(x1, y1, x1 + 1, y2, borderColor);
            context.fill(x2 - 1, y1, x2, y2, borderColor);

            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    this.getMessage(), (x1 + x2) / 2, y1 + (this.height - 8) / 2, textColor);
        }
    }
}