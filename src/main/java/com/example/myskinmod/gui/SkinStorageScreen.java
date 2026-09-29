package com.example.myskinmod.gui;

import com.example.myskinmod.storage.SkinStorageManager;
import com.example.myskinmod.storage.SkinStorageManager.SkinEntry;
import com.example.myskinmod.util.SkinHelper;
import com.example.myskinmod.util.SkinNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SkinStorageScreen extends Screen {

    private final Screen parent;
    private int selectedIndex = -1;
    private final Map<String, Identifier> loadedTextures = new HashMap<>();

    // Widgets แผงควบคุมรายละเอียด
    private TextFieldWidget nameField;
    private ButtonWidget equipButton;
    private ButtonWidget deleteButton;
    private ButtonWidget toggleTypeButton;

    // ระบบ Animation GUI (Slide Panel)
    private float animProgress = 0.0f; // 0.0 (ซ่อน) ถึง 1.0 (แสดงผลเต็มที่)
    private float targetAnim = 0.0f;

    // ระบบ 3D Player Preview
    private ClientPlayerEntity previewPlayer;
    private float previewMouseX = 0;
    private float previewMouseY = 0;

    public SkinStorageScreen(Screen parent) {
        super(Text.literal("Skin Storage Cloud"));
        this.parent = parent;
        SkinStorageManager.init();
    }

    @Override
    protected void init() {
        int panelX = width - 210;
        int topY = 40;

        // ช่องพิมพ์แก้ไขชื่อสกิน
        nameField = new TextFieldWidget(textRenderer, panelX + 15, topY + 145, 180, 20, Text.literal("Skin Name"));
        nameField.setMaxLength(32);
        nameField.setChangedListener(this::onNameChanged);
        this.addSelectableChild(nameField);

        // ปุ่มสลับประเภทโมเดล Slim / Default
        toggleTypeButton = ButtonWidget.builder(Text.literal("Type: Default"), button -> {
            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                String newType = entry.modelType.equals("slim") ? "default" : "slim";
                SkinStorageManager.updateSkin(entry.id, entry.name, newType);
                createPreviewPlayerForSelected();
                updateUIValues();
            }
        }).dimensions(panelX + 15, topY + 170, 180, 20).build();
        this.addDrawableChild(toggleTypeButton);

        // ปุ่มสวมใส่สกิน
        equipButton = ButtonWidget.builder(Text.literal("⚡ Equip Skin"), button -> {
            equipSelectedSkin();
        }).dimensions(panelX + 15, topY + 195, 180, 20).build();
        this.addDrawableChild(equipButton);

        // ปุ่มลบสกิน
        deleteButton = ButtonWidget.builder(Text.literal("🗑 Delete Skin"), button -> {
            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                SkinStorageManager.deleteSkin(entry.id);
                selectedIndex = -1;
                targetAnim = 0.0f;
                updateUIValues();
            }
        }).dimensions(panelX + 15, topY + 220, 180, 20).build();
        this.addDrawableChild(deleteButton);

        // ปุ่มย้อนกลับ
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(20, height - 30, 100, 20).build());

        loadAllTextures();
        updateUIValues();
    }

    private void loadAllTextures() {
        for (SkinEntry entry : SkinStorageManager.getEntries()) {
            if (!loadedTextures.containsKey(entry.id)) {
                Path path = SkinStorageManager.getSkinPath(entry);
                if (Files.exists(path)) {
                    try (InputStream in = Files.newInputStream(path);
                         NativeImage img = NativeImage.read(in)) {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(img);
                        Identifier id = MinecraftClient.getInstance().getTextureManager()
                                .registerDynamicTexture("storage_skin_" + entry.id, texture);
                        loadedTextures.put(entry.id, id);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private SkinEntry getSelectedEntry() {
        var entries = SkinStorageManager.getEntries();
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            return entries.get(selectedIndex);
        }
        return null;
    }

    private void onNameChanged(String newName) {
        SkinEntry entry = getSelectedEntry();
        if (entry != null && !newName.trim().isEmpty() && !newName.equals(entry.name)) {
            SkinStorageManager.updateSkin(entry.id, newName.trim(), entry.modelType);
        }
    }

    private void updateUIValues() {
        SkinEntry entry = getSelectedEntry();
        boolean hasSelection = entry != null;

        if (hasSelection) {
            nameField.setText(entry.name);
            toggleTypeButton.setMessage(Text.literal("Model: " + entry.modelType.toUpperCase()));
        }
    }

    // สร้าง Dummy Player สำหรับเรนเดอร์ 3D Preview
    private void createPreviewPlayerForSelected() {
        SkinEntry entry = getSelectedEntry();
        MinecraftClient client = MinecraftClient.getInstance();

        if (entry != null && client.player != null) {
            Identifier skinId = loadedTextures.get(entry.id);
            if (skinId != null) {
                previewPlayer = new ClientPlayerEntity(
                        client, client.world, client.player.networkHandler,
                        client.player.getStatHandler(), client.player.getRecipeBook(),
                        false, false
                ) {
                    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
                    @Override public Iterable<ItemStack> getArmorItems() { return Collections.emptyList(); }
                    @Override public ItemStack getMainHandStack() { return ItemStack.EMPTY; }
                    @Override public ItemStack getOffHandStack() { return ItemStack.EMPTY; }
                    @Override public boolean isUsingItem() { return false; }
                    @Override public EntityPose getPose() { return EntityPose.STANDING; }
                    @Override public Identifier getSkinTexture() { return skinId; }
                    @Override public String getModel() { return entry.modelType; }
                    @Override public boolean isPartVisible(PlayerModelPart part) {
                        if (part == PlayerModelPart.CAPE) return false;
                        return client.options.isPlayerModelPartEnabled(part);
                    }
                    @Override public Text getName() { return Text.empty(); }
                };
                previewPlayer.copyFrom(client.player);
            }
        }
    }

    private void equipSelectedSkin() {
        SkinEntry entry = getSelectedEntry();
        if (entry == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Path path = SkinStorageManager.getSkinPath(entry);

        if (client.player != null && Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path);
                 NativeImage img = NativeImage.read(in)) {

                NativeImageBackedTexture texture = new NativeImageBackedTexture(img);
                Identifier skinId = client.getTextureManager().registerDynamicTexture("active_custom_skin", texture);

                SkinHelper.applySkin(client.player, skinId, entry.modelType, img.getWidth(), img.getHeight());

                byte[] bytes = Files.readAllBytes(path);
                SkinNetworkHandler.sendSkinChunks(bytes);

                client.setScreen(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftX = 20;
        int topY = 40;
        var entries = SkinStorageManager.getEntries();

        // ตรวจจับการคลิกเลือกรายการสกินในคลัง
        for (int i = 0; i < entries.size(); i++) {
            int itemY = topY + (i * 36);
            if (mouseX >= leftX && mouseX <= leftX + 220 && mouseY >= itemY && mouseY < itemY + 32) {
                selectedIndex = i;
                targetAnim = 1.0f; // เล่น Animation สไลด์แผงรายละเอียดออกมา
                createPreviewPlayerForSelected();
                updateUIValues();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // ให้ผู้ใช้สามารถลากเมาส์หมุนตัวละคร 3D Preview ได้
        if (selectedIndex >= 0 && mouseX >= width - 210) {
            previewMouseX -= deltaX * 2.5f;
            previewMouseY -= deltaY * 2.5f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // คำนวณ Smooth Animation Interpolation
        animProgress += (targetAnim - animProgress) * 0.2f;

        // อัปเดตตำแหน่งซ่อน/แสดงของ Widget ตามค่า Animation
        int panelWidth = 210;
        int currentPanelX = (int) (width - (panelWidth * animProgress));

        boolean showPanel = animProgress > 0.05f;
        nameField.setVisible(showPanel);
        nameField.setX(currentPanelX + 15);
        toggleTypeButton.visible = showPanel;
        toggleTypeButton.setX(currentPanelX + 15);
        equipButton.visible = showPanel;
        equipButton.setX(currentPanelX + 15);
        deleteButton.visible = showPanel;
        deleteButton.setX(currentPanelX + 15);

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Skin Storage Cloud"), width / 2, 15, 0xFFFFFF);

        int leftX = 20;
        int topY = 40;
        var entries = SkinStorageManager.getEntries();

        // ===================== ฝั่งซ้าย: รายชื่อสกิน (พร้อมลำดับเลขที่) =====================
        context.fill(leftX - 2, topY - 2, leftX + 222, topY + 200, 0x66000000);

        if (entries.isEmpty()) {
            context.drawText(textRenderer, "No saved skins yet.", leftX + 10, topY + 10, 0xAAAAAA, false);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                SkinEntry entry = entries.get(i);
                int itemY = topY + (i * 36);

                int bgColor = (i == selectedIndex) ? 0xAA335588 : 0x44000000;
                context.fill(leftX, itemY, leftX + 220, itemY + 32, bgColor);

                // 🔢 1. แสดงลำดับเลขที่สกิน (#1, #2, #3...)
                String orderTag = "#" + (i + 1);
                context.drawText(textRenderer, orderTag, leftX + 6, itemY + 12, 0xFFFFAA00, true);

                // 2. แสดงรูปไอคอนหัวสกิน
                Identifier textureId = loadedTextures.get(entry.id);
                if (textureId != null) {
                    context.drawTexture(textureId, leftX + 28, itemY + 4, 24, 24, 8.0f, 8.0f, 8, 8, 64, 64);
                    context.drawTexture(textureId, leftX + 28, itemY + 4, 24, 24, 40.0f, 8.0f, 8, 8, 64, 64);
                }

                // 3. ชื่อและวันที่สร้าง
                String displayName = entry.name.length() > 14 ? entry.name.substring(0, 12) + ".." : entry.name;
                context.drawText(textRenderer, displayName, leftX + 58, itemY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, entry.createdDate + " (" + entry.modelType + ")", leftX + 58, itemY + 18, 0xAAAAAA, false);
            }
        }

        // ===================== ฝั่งขวา: แผงควบคุมรายละเอียด + 3D Preview (Animation Slide) =====================
        if (showPanel) {
            int panelY = 40;
            context.fill(currentPanelX, panelY - 2, currentPanelX + panelWidth, panelY + 250, 0xBB111111);
            context.fill(currentPanelX, panelY - 2, currentPanelX + 2, panelY + 250, 0xFF5588FF); // ขอบฟ้าสวยงาม

            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                context.drawCenteredTextWithShadow(textRenderer, "Skin Details " + "#" + (selectedIndex + 1), currentPanelX + (panelWidth / 2), panelY + 8, 0xFFFFAA00);

                // 🧍 3D Interactive Player Preview
                if (previewPlayer != null) {
                    int previewX = currentPanelX + (panelWidth / 2);
                    int previewY = panelY + 130;
                    int scale = 48;

                    // วาดโมเดลผู้เล่น 3D
                    InventoryScreen.drawEntity(
                            context,
                            previewX, previewY, scale,
                            previewX - previewMouseX,
                            previewY - 50 - previewMouseY,
                            previewPlayer
                    );
                }
            }
        }
    }
}