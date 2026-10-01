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
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SkinStorageScreen extends Screen {

    private final Screen parent;
    private int selectedIndex = -1;
    private final Map<String, Identifier> loadedTextures = new HashMap<>();
    private List<SkinEntry> validEntries = new ArrayList<>();

    public static final Map<String, Integer> skinKeybinds = new HashMap<>();

    // Widgets
    private TextFieldWidget nameInputField;
    private ButtonWidget renameButton;
    private ButtonWidget toggleTypeButton;
    private ButtonWidget keybindButton;
    private ButtonWidget replaceButton;
    private ButtonWidget equipButton;
    private ButtonWidget deleteButton;
    private ButtonWidget backButton;

    // Animation & State
    private float animProgress = 0.0f;
    private float targetAnim = 0.0f;

    // Delete Animation Variables
    private int deletingIndex = -1;
    private float deleteAnimProgress = 1.0f;
    private SkinEntry entryToDelete = null;

    private ClientPlayerEntity previewPlayer;
    private Identifier previewSkinId;
    private float modelYaw = 0.0f;
    private float modelPitch = 0.0f;
    private boolean isBindingKey = false;

    public SkinStorageScreen(Screen parent) {
        super(Text.literal("Skin Storage Cloud"));
        this.parent = parent;
        SkinStorageManager.init();
    }

    @Override
    protected void init() {
        // คำนวณขนาด Layout ให้สมดุล (Total Width = 420px, Panel Width = 205px Each)
        int totalWidth = 420;
        int panelWidth = 205;
        int startX = (width - totalWidth) / 2;
        int rightPanelX = startX + 215;
        int topY = (height - 235) / 2;

        // ตรวจสอบไฟล์ใน Storage เสมอ
        refreshAndValidateStorage();

        // 1. TextField กรอกชื่อ
        nameInputField = new TextFieldWidget(textRenderer, rightPanelX + 10, topY + 115, 120, 20, Text.literal("Skin Name"));
        nameInputField.setMaxLength(32);
        this.addDrawableChild(nameInputField);

        // 2. ปุ่ม Rename
        renameButton = ButtonWidget.builder(Text.literal("Rename"), button -> applySkinRename())
                .dimensions(rightPanelX + 135, topY + 115, 60, 20)
                .build();
        this.addDrawableChild(renameButton);

        // 3. ปุ่ม Equip Skin
        equipButton = ButtonWidget.builder(Text.literal("Equip Skin"), button -> equipSelectedSkin())
                .dimensions(rightPanelX + 10, topY + 140, 185, 20)
                .build();
        this.addDrawableChild(equipButton);

        // 4. ปุ่ม Model Type & Keybind
        toggleTypeButton = ButtonWidget.builder(Text.literal("Model: DEF"), button -> {
            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                String newType = entry.modelType.equals("slim") ? "default" : "slim";
                SkinStorageManager.updateSkin(entry.id, entry.name, newType);
                createPreviewPlayerForSelected();
                updateUIValues();
            }
        }).dimensions(rightPanelX + 10, topY + 165, 90, 20).build();
        this.addDrawableChild(toggleTypeButton);

        keybindButton = ButtonWidget.builder(Text.literal("Keybind"), button -> {
            if (getSelectedEntry() != null) {
                isBindingKey = true;
                keybindButton.setMessage(Text.literal("> Press <"));
            }
        }).dimensions(rightPanelX + 105, topY + 165, 90, 20).build();
        this.addDrawableChild(keybindButton);

        // 5. ปุ่ม Replace & Delete
        replaceButton = ButtonWidget.builder(Text.literal("Replace"), button -> overwriteSelectedSkinFile())
                .dimensions(rightPanelX + 10, topY + 190, 90, 20)
                .build();
        this.addDrawableChild(replaceButton);

        deleteButton = ButtonWidget.builder(Text.literal("Delete"), button -> startDeleteAnimation())
                .dimensions(rightPanelX + 105, topY + 190, 90, 20)
                .build();
        this.addDrawableChild(deleteButton);

        // 6. ปุ่ม Back ด้านล่าง
        backButton = ButtonWidget.builder(Text.translatable("gui.back"), button -> {
            if (this.client != null) this.client.setScreen(parent);
        }).dimensions((width - 150) / 2, height - 28, 150, 20).build();
        this.addDrawableChild(backButton);

        updateUIValues();
    }

    /**
     * ตรวจสอบว่าไฟล์สกินใน Storage มีอยู่จริงตลอดเวลา ป้องกันสกินค้างในไฟล์
     */
    private void refreshAndValidateStorage() {
        validEntries.clear();
        var allEntries = SkinStorageManager.getEntries();

        for (SkinEntry entry : allEntries) {
            Path skinPath = SkinStorageManager.getSkinPath(entry);
            if (Files.exists(skinPath)) {
                validEntries.add(entry);
                if (!loadedTextures.containsKey(entry.id)) {
                    loadTextureForEntry(entry);
                }
            }
        }

        if (selectedIndex >= validEntries.size()) {
            selectedIndex = validEntries.size() - 1;
        }
    }

    private void loadTextureForEntry(SkinEntry entry) {
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

    private SkinEntry getSelectedEntry() {
        if (selectedIndex >= 0 && selectedIndex < validEntries.size()) {
            return validEntries.get(selectedIndex);
        }
        return null;
    }

    /**
     * เริ่มการทำงาน Animation สำหรับลบสกิน
     */
    private void startDeleteAnimation() {
        if (deletingIndex != -1) return; // ทำการลบอยู่อยู่แล้ว

        SkinEntry entry = getSelectedEntry();
        if (entry != null) {
            deletingIndex = selectedIndex;
            entryToDelete = entry;
            deleteAnimProgress = 1.0f;
        }
    }

    /**
     * ดำเนินการลบสกินออกจาก Storage จริงเมื่อ Animation เล่นจบ
     */
    private void executeActualDelete() {
        if (entryToDelete != null) {
            String deletedName = entryToDelete.name;
            skinKeybinds.remove(entryToDelete.id);
            SkinStorageManager.deleteSkin(entryToDelete.id);

            refreshAndValidateStorage();

            if (validEntries.isEmpty()) {
                selectedIndex = -1;
                targetAnim = 0.0f;
                previewPlayer = null;
                previewSkinId = null;
            } else if (selectedIndex >= validEntries.size()) {
                selectedIndex = validEntries.size() - 1;
                onSkinSelectionChanged();
            } else {
                onSkinSelectionChanged();
            }

            showToast(Text.literal("Skin Storage"), Text.literal("Deleted: " + deletedName));
        }

        deletingIndex = -1;
        entryToDelete = null;
        deleteAnimProgress = 1.0f;
    }

    private void applySkinRename() {
        SkinEntry entry = getSelectedEntry();
        if (entry != null) {
            String newName = nameInputField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(entry.name)) {
                SkinStorageManager.renameSkin(entry.id, newName);
                showToast(Text.literal("Skin Storage"), Text.literal("Renamed to: " + newName));
                nameInputField.setFocused(false);
                refreshAndValidateStorage();
                updateUIValues();
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

                showToast(Text.literal("Skin Storage"), Text.literal("Equipped: " + entry.name));

                // ⚡ ปิด GUI ทันทีเมื่อทำการสวมใส่สกินสำเร็จ
                client.setScreen(null);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void overwriteSelectedSkinFile() {
        SkinEntry entry = getSelectedEntry();
        if (entry == null) return;

        try {
            loadTextureForEntry(entry);
            createPreviewPlayerForSelected();
            showToast(Text.literal("Skin Storage"), Text.literal("Overwrote: " + entry.name));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(Text title, Text description) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getToastManager() != null) {
            SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT, title, description);
        }
    }

    private void updateUIValues() {
        SkinEntry entry = getSelectedEntry();
        if (entry != null) {
            nameInputField.setText(entry.name);
            toggleTypeButton.setMessage(Text.literal("Model: " + (entry.modelType.equals("slim") ? "SLIM" : "DEF")));

            Integer boundKey = skinKeybinds.get(entry.id);
            if (boundKey != null && boundKey != GLFW.GLFW_KEY_UNKNOWN) {
                String keyName = GLFW.glfwGetKeyName(boundKey, 0);
                if (keyName == null) keyName = "K" + boundKey;
                keybindButton.setMessage(Text.literal(keyName.toUpperCase()));
            } else {
                keybindButton.setMessage(Text.literal("Keybind"));
            }
        }
    }

    private void createPreviewPlayerForSelected() {
        SkinEntry entry = getSelectedEntry();
        MinecraftClient client = MinecraftClient.getInstance();

        if (entry != null && client.world != null && client.player != null) {
            Identifier skinId = loadedTextures.get(entry.id);
            if (skinId != null) {
                this.previewSkinId = skinId;
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
            } else {
                this.previewSkinId = null;
                this.previewPlayer = null;
            }
        } else {
            this.previewSkinId = null;
            this.previewPlayer = null;
        }
    }

    private void render3DPreview(DrawContext context, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (previewPlayer != null && previewSkinId != null) {
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

            InventoryScreen.drawEntity(
                    context,
                    x, y,
                    38,
                    bodyRotation,
                    null,
                    previewPlayer
            );

            context.getMatrices().pop();
        } else {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Select a skin to preview"), x, y - 10, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int totalWidth = 420;
        int startX = (width - totalWidth) / 2;
        int topY = (height - 235) / 2;
        int currentPanelX = startX + 215;

        if (button == 0 && mouseX >= currentPanelX && mouseX <= currentPanelX + 205 && mouseY >= topY && mouseY <= topY + 105) {
            modelYaw += (float) deltaX * 1.5f;
            modelPitch = Math.max(-80.0f, Math.min(80.0f, modelPitch + (float) deltaY * 1.5f));
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isBindingKey) {
            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    skinKeybinds.remove(entry.id);
                    showToast(Text.literal("Keybind Cleared"), Text.literal("Removed keybind for " + entry.name));
                } else {
                    skinKeybinds.put(entry.id, keyCode);
                    String keyName = GLFW.glfwGetKeyName(keyCode, scanCode);
                    if (keyName == null) keyName = "Key " + keyCode;
                    showToast(Text.literal("Keybind Saved"), Text.literal("Bound [" + keyName.toUpperCase() + "] to " + entry.name));
                }
            }
            isBindingKey = false;
            updateUIValues();
            return true;
        }

        if (nameInputField != null && nameInputField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applySkinRename();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // เช็ค Keybind สำหรับสวมใส่สกิน (พร้อมปิด GUI ทันที)
        for (Map.Entry<String, Integer> kEntry : skinKeybinds.entrySet()) {
            if (kEntry.getValue() == keyCode) {
                for (int i = 0; i < validEntries.size(); i++) {
                    if (validEntries.get(i).id.equals(kEntry.getKey())) {
                        selectedIndex = i;
                        onSkinSelectionChanged();
                        equipSelectedSkin();
                        return true;
                    }
                }
            }
        }

        if (!validEntries.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedIndex = (selectedIndex <= 0) ? validEntries.size() - 1 : selectedIndex - 1;
                onSkinSelectionChanged();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedIndex = (selectedIndex >= validEntries.size() - 1) ? 0 : selectedIndex + 1;
                onSkinSelectionChanged();
                return true;
            } else if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                int index = keyCode - GLFW.GLFW_KEY_1;
                if (index < validEntries.size()) {
                    selectedIndex = index;
                    onSkinSelectionChanged();
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                if (selectedIndex >= 0 && selectedIndex < validEntries.size()) {
                    equipSelectedSkin();
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSkinSelectionChanged() {
        targetAnim = 1.0f;
        modelYaw = 0.0f;
        modelPitch = 0.0f;
        createPreviewPlayerForSelected();
        updateUIValues();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalWidth = 420;
        int startX = (width - totalWidth) / 2;
        int leftX = startX;
        int topY = (height - 235) / 2;

        for (int i = 0; i < validEntries.size(); i++) {
            int itemY = topY + (i * 36);
            if (mouseX >= leftX && mouseX <= leftX + 205 && mouseY >= itemY && mouseY < itemY + 32) {
                selectedIndex = i;
                onSkinSelectionChanged();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // อัปเดตและตรวจสอบความถูกต้องของสกินตลอดเวลา
        refreshAndValidateStorage();

        // คำนวณ Delete Animation
        if (deletingIndex != -1) {
            deleteAnimProgress -= delta * 0.15f;
            if (deleteAnimProgress <= 0.0f) {
                executeActualDelete();
            }
        }

        animProgress += (targetAnim - animProgress) * 0.2f;

        int totalWidth = 420;
        int panelWidth = 205;
        int startX = (width - totalWidth) / 2;
        int topY = (height - 235) / 2;

        int basePanelX = startX + 215;
        int currentPanelX = basePanelX + (int) ((1.0f - animProgress) * 40);

        boolean showPanel = animProgress > 0.05f && !validEntries.isEmpty();

        // อัปเดตตำแหน่ง Control Widgets
        nameInputField.setVisible(showPanel);
        nameInputField.setX(currentPanelX + 10);

        renameButton.visible = showPanel;
        renameButton.setX(currentPanelX + 135);

        equipButton.visible = showPanel;
        equipButton.setX(currentPanelX + 10);

        toggleTypeButton.visible = showPanel;
        toggleTypeButton.setX(currentPanelX + 10);

        keybindButton.visible = showPanel;
        keybindButton.setX(currentPanelX + 105);

        replaceButton.visible = showPanel;
        replaceButton.setX(currentPanelX + 10);

        deleteButton.visible = showPanel;
        deleteButton.setX(currentPanelX + 105);

        // วาดส่วนประกอบ GUI มาตรฐาน
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Skin Storage Cloud"), width / 2, Math.max(8, topY - 22), 0xFFFFFF);

        int leftX = startX;

        // ===================== ฝั่งซ้าย: รายการสกิน (List Panel) =====================
        context.fill(leftX - 2, topY - 2, leftX + panelWidth, topY + 235, 0x66000000);

        if (validEntries.isEmpty()) {
            context.drawText(textRenderer, "No saved skins yet.", leftX + 10, topY + 10, 0xAAAAAA, false);
        } else {
            for (int i = 0; i < validEntries.size(); i++) {
                SkinEntry entry = validEntries.get(i);
                int baseItemY = topY + (i * 36);

                int itemX = leftX;
                int itemY = baseItemY;

                // เล่น Animation สไลด์รายการเลื่อนออกเมื่อลบ
                if (i == deletingIndex) {
                    itemX = leftX - (int) ((1.0f - deleteAnimProgress) * 210);
                } else if (deletingIndex != -1 && i > deletingIndex) {
                    itemY = baseItemY - (int) ((1.0f - deleteAnimProgress) * 36);
                }

                int bgColor = (i == selectedIndex) ? 0xAA335588 : 0x44000000;
                context.fill(itemX, itemY, itemX + panelWidth - 5, itemY + 32, bgColor);

                String orderTag = "#" + (i + 1);
                context.drawText(textRenderer, orderTag, itemX + 6, itemY + 12, 0xFFFFAA00, true);

                Identifier textureId = loadedTextures.get(entry.id);
                if (textureId != null) {
                    context.drawTexture(textureId, itemX + 28, itemY + 4, 24, 24, 8.0f, 8.0f, 8, 8, 64, 64);
                    context.drawTexture(textureId, itemX + 28, itemY + 4, 24, 24, 40.0f, 8.0f, 8, 8, 64, 64);
                }

                String displayName = entry.name.length() > 11 ? entry.name.substring(0, 9) + ".." : entry.name;
                Integer k = skinKeybinds.get(entry.id);
                if (k != null && k != GLFW.GLFW_KEY_UNKNOWN) {
                    String kName = GLFW.glfwGetKeyName(k, 0);
                    displayName += " [" + (kName != null ? kName.toUpperCase() : k) + "]";
                }

                context.drawText(textRenderer, displayName, itemX + 58, itemY + 5, 0xFFFFFF, false);
                context.drawText(textRenderer, entry.createdDate + " (" + entry.modelType + ")", itemX + 58, itemY + 18, 0xAAAAAA, false);
            }
        }

        // ===================== ฝั่งขวา: รายละเอียดสกิน & 3D Preview =====================
        if (showPanel) {
            context.fill(currentPanelX, topY - 2, currentPanelX + panelWidth, topY + 235, 0xBB111111);
            context.fill(currentPanelX, topY - 2, currentPanelX + 2, topY + 235, 0xFF5588FF);

            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                context.drawCenteredTextWithShadow(textRenderer, "Skin Details #" + (selectedIndex + 1), currentPanelX + (panelWidth / 2), topY + 6, 0xFFFFAA00);

                int previewX = currentPanelX + (panelWidth / 2);
                int previewY = topY + 98;
                render3DPreview(context, previewX, previewY);
            }
        }
    }
}