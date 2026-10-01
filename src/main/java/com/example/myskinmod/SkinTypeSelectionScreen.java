package com.example.myskinmod;

import com.example.myskinmod.util.SkinNetworkHandler;
import com.example.myskinmod.storage.SkinStorageManager;
import com.example.myskinmod.gui.SkinStorageScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerModelPart;
import org.joml.Quaternionf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityPose;
import java.util.Collections;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import com.example.myskinmod.util.SkinHelper;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SkinTypeSelectionScreen extends Screen {

    private String currentModelType = "default";
    private boolean confirmed = false;

    private final Screen parent;
    private final Identifier skinId;
    private final String skinUrl;
    private final String skinSource;
    private long sourceDisplayTime = 0;

    // ตัวแปรสำหรับการหมุนตัวละคร
    private float modelYaw = 0f;
    private float modelPitch = 0f;

    public SkinTypeSelectionScreen(Screen parent, NativeImage skinImage, Identifier skinId, String skinUrl) {
        super(Text.literal("Select Skin Type"));
        this.parent = parent;
        this.skinImage = skinImage;
        this.skinId = skinId;
        this.skinUrl = skinUrl;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            try {
                // เช็คจาก PlayerListEntry หรือ Player Entity ตรงๆ ก่อนจะมีการโหลด Texture ใหม่
                net.minecraft.client.network.PlayerListEntry entry = client.getNetworkHandler() != null ?
                        client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) : null;

                String detectedModel = null;
                if (entry != null) {
                    detectedModel = entry.getModel();
                } else {
                    detectedModel = client.player.getModel();
                }

                // ถ้าเป็น slim ให้ใช้ slim ถ้าไม่ใช่ให้ใช้ default (ตามที่ผู้เล่นตั้งไว้จริง)
                if (detectedModel != null && detectedModel.equalsIgnoreCase("slim")) {
                    this.currentModelType = "slim";
                } else {
                    this.currentModelType = "default";
                }
            } catch (Exception e) {
                this.currentModelType = "default";
            }
        }

        if (skinUrl != null && !skinUrl.startsWith("http")) {
            Path path = Paths.get(skinUrl);
            if (Files.exists(path)) {
                this.skinSource = "File: " + path.toAbsolutePath().toString();
            } else {
                this.skinSource = "Invalid file: " + path.toAbsolutePath();
            }
            this.sourceDisplayTime = System.currentTimeMillis();
        } else {
            this.skinSource = "Only local files are supported";
        }
    }

    private void fixSkinTransparency(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int scale = width / 64; // อัตราส่วนตามขนาดสกิน (รองรับ HD Skin)

        // ขอบเขตพิกเซลของ Base Layer บนสกินมาตรฐาน 64x64 [x1, y1, x2, y2]
        int[][] baseRegions = {
                {0, 0, 32, 16},   // Head Base
                {0, 16, 16, 32},  // Right Leg Base
                {16, 16, 40, 32}, // Torso Base
                {40, 16, 56, 32}, // Right Arm Base
                {16, 48, 32, 64}, // Left Leg Base
                {32, 48, 48, 64}  // Left Arm Base
        };

        for (int[] region : baseRegions) {
            int xStart = region[0] * scale;
            int yStart = region[1] * scale;
            int xEnd = region[2] * scale;
            int yEnd = region[3] * scale;

            // หากเป็นสกินรุ่นเก่า (64x32) ให้ข้ามส่วนขากับแขนซ้าย
            if (yStart >= height) continue;

            for (int x = xStart; x < xEnd && x < width; x++) {
                for (int y = yStart; y < yEnd && y < height; y++) {
                    int color = image.getColor(x, y);
                    int alpha = (color >> 24) & 0xFF; // ดึงค่า Alpha

                    if (alpha < 255) {
                        if (alpha == 0) {
                            // ถ้าโปร่งใส 100% ให้แทนที่ด้วยสีดำสนิท (Opaque Black)
                            image.setColor(x, y, 0xFF000000);
                        } else {
                            // ถ้าโปร่งใสบางส่วน ให้ปรับ Alpha เป็น 255 (ทึบแสง)
                            image.setColor(x, y, color | 0xFF000000);
                        }
                    }
                }
            }
        }
    }

    private ButtonWidget classicButton;
    private ButtonWidget slimButton;
    private NativeImage skinImage;
    private Identifier previewSkinId;
    private ClientPlayerEntity previewPlayer;
    private boolean skinReady = false;

    private void showSkinToast(String title, String message, String type, Identifier iconId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getToastManager() != null) {
            client.execute(() -> {
                if (type.equals("success")) {
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
                } else if (type.equals("loading")) {
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                } else {
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 1.0f));
                }

                client.getToastManager().add(new CustomSkinToast(title, message, type, iconId));
            });
        }
    }

    @Override
    protected void init() {
        if (previewPlayer == null) {
            loadAndPreviewSkin(skinUrl);
        }

        int buttonWidth = 210;
        int halfWidth = 102;
        int startX = width / 2 - (buttonWidth / 2);
        int rightX = startX + buttonWidth - halfWidth;
        int buttonStartY = height / 2 + 48; // ปรับระดับความสูงปุ่มให้อยู่ในระยะที่พอดี

        // --- แถวที่ 1: เลือกประเภทโมเดล ---
        classicButton = new CustomStyledButton(startX, buttonStartY, halfWidth, 20, Text.literal("Classic (Steve)"), button -> {
            currentModelType = "default";
            updateModelButtons();
        }, false);

        slimButton = new CustomStyledButton(rightX, buttonStartY, halfWidth, 20, Text.literal("Slim (Alex)"), button -> {
            currentModelType = "slim";
            updateModelButtons();
        }, false);

        this.addDrawableChild(classicButton);
        this.addDrawableChild(slimButton);

        // --- แถวที่ 2: ปุ่มConfirm และ Save to Storage (วางคู่กัน) ---
        this.addDrawableChild(new CustomStyledButton(startX, buttonStartY + 24, halfWidth, 20, Text.literal("Confirm Skin!"), button -> {
            confirmed = true;
            applySkin(currentModelType);
        }, true));

        this.addDrawableChild(new CustomStyledButton(rightX, buttonStartY + 24, halfWidth, 20, Text.literal("Save to Storage"), button -> {
            if (this.skinImage != null) {
                String defaultName = "My Skin " + (SkinStorageManager.getEntries().size() + 1);
                SkinStorageManager.addSkin(defaultName, currentModelType, this.skinImage);
                showSkinToast("บันทึกแล้ว!", "บันทึกสกินลงคลังเรียบร้อย", "success", previewSkinId);

                // ✅ ปิดหน้าต่าง GUI ทันทีหลังจากเซฟลงคลังเสร็จ
                if (this.client != null) {
                    this.client.setScreen(null);
                }
            }
        }, false));

        // --- แถวที่ 3: ปุ่ม Back (เต็มความกว้าง) ---
        this.addDrawableChild(new CustomStyledButton(startX, buttonStartY + 48, buttonWidth, 20, Text.literal("Back"), button -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }, false));

        updateModelButtons();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) {
            this.modelYaw += (float) deltaX * 1.5f;
            this.modelPitch += (float) deltaY * 1.5f;

            // ล็อกมุมก้มเงยเพื่อไม่ให้โมเดลทะลุพื้นหรือกลับหัว
            this.modelPitch = Math.max(-45f, Math.min(45f, this.modelPitch));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void updateModelButtons() {
        if (classicButton == null || slimButton == null) return;

        if (currentModelType.equals("default")) {
            classicButton.active = false;
            slimButton.active = true;
        } else {
            slimButton.active = false;
            classicButton.active = true;
        }
    }

    // ฟังก์ชันสำหรับวาดขอบเส้นประ (Dashed Border)
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
        super.render(context, mouseX, mouseY, delta);

        int boxWidth = 160;
        int boxHeight = 155;
        int boxX = width / 2 - boxWidth / 2;
        int boxY = height / 2 - 115;

        // 1. วาดกรอบ Player Model หลัก
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x66000000);
        drawDashedBorder(context, boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2, 0xFFFFFFFF);

        // 2. วาดกรอบ Profile (หัว) ข้างๆ ด้านขวา
        int headBoxSize = 42;
        int headBoxX = boxX + boxWidth + 8;
        int headBoxY = boxY;
        context.fill(headBoxX, headBoxY, headBoxX + headBoxSize, headBoxY + headBoxSize, 0x66000000);
        drawDashedBorder(context, headBoxX - 1, headBoxY - 1, headBoxSize + 2, headBoxSize + 2, 0xFFFFFFFF);

        if (previewSkinId != null && skinImage != null) {
            int imgW = skinImage.getWidth();
            int imgH = skinImage.getHeight();
            float scale = imgW / 64.0f;

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 500.0f);
            context.drawTexture(previewSkinId, headBoxX + 5, headBoxY + 5, 32, 32, 8.0f * scale, 8.0f * scale, (int)(8 * scale), (int)(8 * scale), imgW, imgH);
            context.drawTexture(previewSkinId, headBoxX + 5, headBoxY + 5, 32, 32, 40.0f * scale, 8.0f * scale, (int)(8 * scale), (int)(8 * scale), imgW, imgH);
            context.getMatrices().pop();
        }

        // 3. วาดช่อง Full Texture ด้านซ้าย
        int texBoxSize = 42;
        int texBoxX = boxX - texBoxSize - 8;
        int texBoxY = boxY;
        context.fill(texBoxX, texBoxY, texBoxX + texBoxSize, texBoxY + texBoxSize, 0x66000000);
        drawDashedBorder(context, texBoxX - 1, texBoxY - 1, texBoxSize + 2, texBoxSize + 2, 0xFFFFFFFF);

        if (previewSkinId != null && skinImage != null) {
            int imgW = skinImage.getWidth();
            int imgH = skinImage.getHeight();

            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 500.0f);
            context.drawTexture(previewSkinId, texBoxX + 3, texBoxY + 3, texBoxSize - 6, texBoxSize - 6, 0.0f, 0.0f, imgW, imgH, imgW, imgH);
            context.getMatrices().pop();
        }

        // ข้อความรายละเอียด
        if (skinSource != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(skinSource), width / 2, boxY - 22, 0x55FF55);
        }

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Skin Preview (Drag to rotate)"), width / 2, boxY + 8, 0xFFFFFF);

        if (skinImage != null) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Type: " + currentModelType.toUpperCase() + " (" + skinImage.getWidth() + "x" + skinImage.getHeight() + ")"),
                    width / 2,
                    boxY + 22,
                    0xAAAAAA
            );
        }

        // วาดโมเดลผู้เล่นในกรอบหลัก
        render3DPreview(context, width / 2, boxY + boxHeight - 20);
    }

    private void render3DPreview(DrawContext context, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (previewPlayer != null && previewSkinId != null) {
            // ดัน Matrix ไปหน้าสุดของ GUI
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 500.0f);

            Quaternionf bodyRotation = new Quaternionf()
                    .rotateX((float) Math.toRadians(180f + modelPitch))
                    .rotateY((float) Math.toRadians(modelYaw));

            if (client.world != null) {
                previewPlayer.age = (int) (client.world.getTime() % 10000L);
            } else {
                previewPlayer.age++;
            }

            previewPlayer.headYaw = (float) Math.sin(System.currentTimeMillis() / 1000.0) * 5f;
            previewPlayer.bodyYaw = 0f;
            previewPlayer.setYaw(0f);
            previewPlayer.setPitch(0f);
            previewPlayer.isPartVisible(PlayerModelPart.CAPE); // ทำการปิด/เช็ค Part Cape

            InventoryScreen.drawEntity(
                    context,
                    x, y,
                    50,
                    bodyRotation,
                    null,
                    previewPlayer
            );

            context.getMatrices().pop();
        } else {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading skin preview..."), x, y - 80, 0xAAAAAA);
        }
    }

    public void loadAndPreviewSkin(String skinUrl) {
        MinecraftClient client = MinecraftClient.getInstance();
        skinReady = false;

        new Thread(() -> {
            try {
                NativeImage loadedImage;

                if (skinUrl != null && (skinUrl.startsWith("http://") || skinUrl.startsWith("https://"))) {
                    HttpURLConnection conn = (HttpURLConnection) new URL(skinUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Minecraft-Skin-Mod");

                    try (InputStream in = conn.getInputStream()) {
                        loadedImage = NativeImage.read(in);
                    }
                } else {
                    if (skinUrl == null || skinUrl.isEmpty()) {
                        showSkinToast("Load Error", "Skin path is empty!", "error", null);
                        return;
                    }

                    Path filePath = Paths.get(skinUrl);
                    try (InputStream in = Files.newInputStream(filePath)) {
                        loadedImage = NativeImage.read(in);
                    }
                }

                int w = loadedImage.getWidth();
                int h = loadedImage.getHeight();
                if (w < 64 || h < 64 || w > 8192 || h > 8192) {
                    loadedImage.close();
                    showSkinToast("ขนาดสกินไม่ถูกต้อง!", "รองรับขนาด 64x64 ถึง 8192x8192 เท่านั้น", "error", null);
                    return;
                }

                fixSkinTransparency(loadedImage);

                if (this.skinImage != null) {
                    this.skinImage.close();
                }

                this.skinImage = loadedImage;

                if (client.player != null) {
                    try {
                        // ดึง Model Type เดิมจาก PlayerListEntry หรือ ตัวละครผู้เล่นหลักในขณะนั้น
                        net.minecraft.client.network.PlayerListEntry entry = (client.getNetworkHandler() != null) ?
                                client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) : null;

                        String originalModel = (entry != null) ? entry.getModel() : client.player.getModel();

                        // ถ้าเดิมเป็น slim ให้ใช้ slim ถ้าไม่ใช่ให้คงไว้ตามเดิม
                        if (originalModel != null && originalModel.equalsIgnoreCase("slim")) {
                            this.currentModelType = "slim";
                        } else {
                            this.currentModelType = "default";
                        }
                    } catch (Exception e) {
                        this.currentModelType = client.player.getModel() != null ? client.player.getModel() : "default";
                    }
                }

                client.execute(() -> {
                    try {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(skinImage);
                        previewSkinId = client.getTextureManager().registerDynamicTexture("skin_preview", texture);

                        previewPlayer = new ClientPlayerEntity(
                                client, client.world, client.player.networkHandler,
                                client.player.getStatHandler(), client.player.getRecipeBook(),
                                false, false
                        ) {
                            @Override
                            public ItemStack getEquippedStack(EquipmentSlot slot) {
                                return ItemStack.EMPTY; // ให้ของในมือ/ชุดเกราะทุกช่องเป็นมือเปล่า
                            }

                            @Override
                            public Iterable<ItemStack> getArmorItems() {
                                return Collections.emptyList(); // ซ่อนชุดเกราะ
                            }

                            @Override
                            public ItemStack getMainHandStack() {
                                return ItemStack.EMPTY; // ไม่ถือบล็อก/ของในมือหลัก
                            }

                            @Override
                            public ItemStack getOffHandStack() {
                                return ItemStack.EMPTY; // ไม่ถือของในมือรอง
                            }

                            @Override
                            public boolean isUsingItem() {
                                return false;
                            }

                            @Override
                            public EntityPose getPose() {
                                return EntityPose.STANDING;
                            }

                            @Override
                            public boolean isSneaking() {
                                return false; // ไม่ย่อตัว (Sneak)
                            }

                            @Override
                            public boolean isInSneakingPose() {
                                return false;
                            }

                            @Override
                            public boolean isRiding() {
                                return false;
                            }

                            @Override
                            public boolean isFallFlying() {
                                return false;
                            }

                            @Override
                            public boolean isSwimming() {
                                return false;
                            }

                            @Override
                            public boolean isSpectator() {
                                return false;
                            }

                            @Override
                            public boolean isCreative() {
                                return false;
                            }

                            @Override
                            public Identifier getSkinTexture() {
                                return previewSkinId;
                            }

                            @Override
                            public String getModel() {
                                return currentModelType; // คืนค่าตามที่สกินเดิมเป็น หรือตามปุ่มที่ผู้เล่นเลือกปรับในมอด
                            }

                            @Override
                            public boolean isPartVisible(PlayerModelPart part) {
                                if (part == PlayerModelPart.CAPE) {
                                    return false;
                                }
                                return client.options.isPlayerModelPartEnabled(part);
                            }

                            @Override
                            public Identifier getCapeTexture() { return null; }

                            @Override
                            public Identifier getElytraTexture() { return null; }

                            @Override
                            public boolean shouldRenderName() { return false; }

                            @Override
                            public boolean hasCustomName() { return false; }

                            @Override
                            public boolean isCustomNameVisible() { return false; }

                            @Override
                            public Text getName() { return Text.empty(); }

                            @Override
                            public Text getDisplayName() { return Text.empty(); }
                        };

                        previewPlayer.copyFrom(client.player);
                        skinReady = true;

                        updateModelButtons();
                        showSkinToast("นำสกินเข้าสู่ระบบแล้ว!", "กรุณาเลือกประเภทสกินที่ต้องการ", "loading", previewSkinId);

                    } catch (Exception e) {
                        showSkinToast("นำสกินเข้าสู่เกมผิดพลาด!", "Failed to setup preview.", "error", null);
                    }
                });

            } catch (Exception e) {
                showSkinToast("Download Error", "Could not load file.", "error", null);
            }
        }).start();
    }

    private void applySkin(String modelType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && skinImage != null) {
            try {
                NativeImageBackedTexture texture = new NativeImageBackedTexture(skinImage);
                Identifier newSkinId = client.getTextureManager().registerDynamicTexture(
                        "custom_skin_" + client.player.getUuid(),
                        texture
                );
                SkinHelper.applySkin(client.player, newSkinId, modelType, skinImage.getWidth(), skinImage.getHeight());

                java.nio.file.Path tempPath = null;
                try {
                    tempPath = java.nio.file.Files.createTempFile("skin_send", ".png");
                    skinImage.writeTo(tempPath);

                    byte[] pngBytes = java.nio.file.Files.readAllBytes(tempPath);

                    SkinNetworkHandler.sendSkinChunks(pngBytes);

                    showSkinToast("ใส่สกินเสร็จสิ้น!", "สกินของคุณถูกส่งไปยังเซิร์ฟเวอร์แล้ว!", "success", previewSkinId);

                } catch (Exception e) {
                    System.out.println("[MySkinMod] Network Error: " + e.getMessage());
                    showSkinToast("Network Error", "ไม่สามารถส่งสกินได้", "error", null);
                } finally {
                    // ✅ ลบไฟล์ชั่วคราวทิ้งแน่นอน 100% ไม่ว่าจะส่งสำเร็จหรือล้มเหลว
                    if (tempPath != null) {
                        java.nio.file.Files.deleteIfExists(tempPath);
                    }
                }

                client.setScreen(null);
            } catch (Exception e) {
                showSkinToast("ไม่สามารถใช้สกินได้!", "เกิดปัญหากับสกินของคุณ", "error", null);
            }
        }
    }

    @Override
    public void close() {
        if (skinImage != null) skinImage.close();
        if (skinId != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(skinId);
        super.close();
    }

    // --- แก้ไขจุดที่ 1: Toast draw method (แก้ Override Error) ---
    private static class CustomSkinToast implements Toast {
        private final String title;
        private final String message;
        private final String type;
        private final Identifier skinId;
        private final long TOTAL_TIME = 5000L;

        public CustomSkinToast(String title, String message, String type, Identifier skinId) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.skinId = skinId;
        }

        @Override
        public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
            int backgroundColor = 0xDD441111;
            int borderColor = 0xFF55FF55;
            int progressColor = 0xFF22AA22;

            if (type.equals("success")) {
                backgroundColor = 0xDD114411;
                borderColor = 0xFF55FF55;
                progressColor = 0xFF22AA22;
            } else if (type.equals("loading")) {
                backgroundColor = 0xDD444411;
                borderColor = 0xFFFFFF55;
                progressColor = 0xFFAAAA22;
            }

            context.fill(0, 0, 160, 32, backgroundColor);
            context.fill(0, 0, 160, 1, borderColor);
            context.fill(0, 31, 160, 32, borderColor);
            context.fill(0, 0, 1, 32, borderColor);
            context.fill(159, 0, 160, 32, borderColor);

            float progress = 1.0f - ((float) startTime / (float) TOTAL_TIME);
            int progressBarWidth = (int) (158 * progress);

            if (progressBarWidth > 0) {
                context.fill(1, 29, 1 + progressBarWidth, 31, progressColor);
            }

            if (skinId != null) {
                int iconX = 8;
                int iconY = 6;
                context.fill(iconX - 1, iconY - 1, iconX + 19, iconY + 19, 0xAA000000);
                context.drawTexture(skinId, iconX, iconY, 18, 18, 8.0f, 8.0f, 8, 8, 64, 64);
                context.drawTexture(skinId, iconX, iconY, 18, 18, 40.0f, 8.0f, 8, 8, 64, 64);
            }

            context.drawText(manager.getClient().textRenderer, title, 32, 7, borderColor, false);
            context.drawText(manager.getClient().textRenderer, message, 32, 18, 0xFFE0E0E0, false);

            return startTime >= TOTAL_TIME ? Visibility.HIDE : Visibility.SHOW;
        }
    }

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