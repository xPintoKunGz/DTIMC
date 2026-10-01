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
    private final List<SkinEntry> validEntries = new ArrayList<>();

    public static final Map<String, Integer> skinKeybinds = new HashMap<>();

    // Compact UI Controls
    private TextFieldWidget nameInputField;
    private boolean isEditingName = false;

    private ButtonWidget equipButton;
    private ButtonWidget toggleTypeButton;
    private ButtonWidget keybindButton;
    private ButtonWidget renameButton;
    private ButtonWidget deleteButton;

    // Animations & State
    private float animProgress = 0.0f;
    private float targetAnim = 0.0f;

    private int deletingIndex = -1;
    private float deleteAnimProgress = 1.0f;
    private SkinEntry entryToDelete = null;

    // 3D Preview
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
        int panelWidth = 205;
        int totalWidth = (panelWidth * 2) + 12; // 422px
        int startX = (width - totalWidth) / 2;
        int rightPanelX = startX + panelWidth + 12;
        int topY = (height - 220) / 2;

        refreshAndValidateStorage();

        // 1. Text Field สำหรับเปลี่ยนชื่อ (Inline ซ่อนไว้ด้านบนของแผงขวา)
        nameInputField = new TextFieldWidget(textRenderer, rightPanelX + 10, topY + 22, 185, 18, Text.literal("Skin Name"));
        nameInputField.setMaxLength(24);
        nameInputField.setVisible(false);
        this.addDrawableChild(nameInputField);

        // 2. ปุ่ม Equip Skin (ปุ่มหลัก แถวที่ 1)
        equipButton = ButtonWidget.builder(Text.literal("Equip Skin"), button -> equipSelectedSkin())
                .dimensions(rightPanelX + 10, topY + 132, 185, 20)
                .build();
        this.addDrawableChild(equipButton);

        // 3. ปุ่ม Model Type & Keybind (แถวที่ 2)
        toggleTypeButton = ButtonWidget.builder(Text.literal("Model: DEF"), button -> {
            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                String newType = entry.modelType.equals("slim") ? "default" : "slim";
                SkinStorageManager.updateSkin(entry.id, entry.name, newType);
                createPreviewPlayerForSelected();
                updateUIValues();
            }
        }).dimensions(rightPanelX + 10, topY + 156, 90, 20).build();
        this.addDrawableChild(toggleTypeButton);

        keybindButton = ButtonWidget.builder(Text.literal("Keybind"), button -> {
            if (getSelectedEntry() != null) {
                isBindingKey = true;
                keybindButton.setMessage(Text.literal("> Press <"));
            }
        }).dimensions(rightPanelX + 105, topY + 156, 90, 20).build();
        this.addDrawableChild(keybindButton);

        // 4. ปุ่ม Rename & Delete (แถวที่ 3)
        renameButton = ButtonWidget.builder(Text.literal("Rename"), button -> toggleRenameMode())
                .dimensions(rightPanelX + 10, topY + 180, 90, 20)
                .build();
        this.addDrawableChild(renameButton);

        deleteButton = ButtonWidget.builder(Text.literal("Delete"), button -> startDeleteAnimation())
                .dimensions(rightPanelX + 105, topY + 180, 90, 20)
                .build();
        this.addDrawableChild(deleteButton);

        // 5. ปุ่ม Back (ล่างสุด)
        ButtonWidget backButton = ButtonWidget.builder(Text.translatable("gui.back"), button -> {
            if (this.client != null) this.client.setScreen(parent);
        }).dimensions((width - 120) / 2, topY + 225, 120, 20).build();
        this.addDrawableChild(backButton);

        updateUIValues();
    }

    private void toggleRenameMode() {
        SkinEntry entry = getSelectedEntry();
        if (entry == null) return;

        if (isEditingName) {
            applySkinRename();
        } else {
            isEditingName = true;
            nameInputField.setText(entry.name);
            nameInputField.setVisible(true);
            nameInputField.setFocused(true);
            renameButton.setMessage(Text.literal("Save [↵]"));
        }
    }

    private void applySkinRename() {
        SkinEntry entry = getSelectedEntry();
        if (entry != null && nameInputField != null) {
            String newName = nameInputField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(entry.name)) {
                SkinStorageManager.renameSkin(entry.id, newName);
                showToast(Text.literal("Skin Storage"), Text.literal("Renamed to: " + newName));
                refreshAndValidateStorage();
            }
        }
        closeRenameMode();
    }

    private void closeRenameMode() {
        isEditingName = false;
        if (nameInputField != null) {
            nameInputField.setVisible(false);
            nameInputField.setFocused(false);
        }
        if (renameButton != null) {
            renameButton.setMessage(Text.literal("Rename"));
        }
        updateUIValues();
    }

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

    private void startDeleteAnimation() {
        if (deletingIndex != -1) return;
        SkinEntry entry = getSelectedEntry();
        if (entry != null) {
            deletingIndex = selectedIndex;
            entryToDelete = entry;
            deleteAnimProgress = 1.0f;
        }
    }

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

    private void equipSelectedSkin() {
        SkinEntry entry = getSelectedEntry();
        if (entry == null) return;
        equipSkinById(entry);
        if (this.client != null) this.client.setScreen(null);
    }

    public static void equipSkinById(SkinEntry entry) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entry == null || client.player == null) return;

        Path path = SkinStorageManager.getSkinPath(entry);
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path);
                 NativeImage img = NativeImage.read(in)) {

                NativeImageBackedTexture texture = new NativeImageBackedTexture(img);
                Identifier skinId = client.getTextureManager().registerDynamicTexture("active_custom_skin", texture);

                SkinHelper.applySkin(client.player, skinId, entry.modelType, img.getWidth(), img.getHeight());

                byte[] bytes = Files.readAllBytes(path);
                SkinNetworkHandler.sendSkinChunks(bytes);

                if (client.getToastManager() != null) {
                    SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            Text.literal("Skin Storage"), Text.literal("Equipped: " + entry.name));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            toggleTypeButton.setMessage(Text.literal("Model: " + (entry.modelType.equals("slim") ? "SLIM" : "DEF")));

            Integer boundKey = skinKeybinds.get(entry.id);
            if (boundKey != null && boundKey != GLFW.GLFW_KEY_UNKNOWN) {
                String keyName = GLFW.glfwGetKeyName(boundKey, 0);
                if (keyName == null) keyName = "K" + boundKey;
                keybindButton.setMessage(Text.literal("[" + keyName.toUpperCase() + "]"));
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
                    @Override public boolean isSneaking() { return false; }
                    @Override public boolean isSprinting() { return false; }
                    @Override public EntityPose getPose() { return EntityPose.STANDING; }
                    @Override public Identifier getSkinTexture() { return skinId; }
                    @Override public String getModel() { return entry.modelType; }
                    @Override public boolean isPartVisible(PlayerModelPart part) {
                        if (part == PlayerModelPart.CAPE) return false;
                        return client.options.isPlayerModelPartEnabled(part);
                    }
                    @Override public Text getName() { return Text.empty(); }
                };

                previewPlayer.setPose(EntityPose.STANDING);
                previewPlayer.handSwinging = false;
                previewPlayer.handSwingProgress = 0.0f;
                previewPlayer.limbAnimator.setSpeed(0.0f);
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
        if (previewPlayer != null && previewSkinId != null) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300.0f);

            previewPlayer.age = 0;
            previewPlayer.headYaw = 0.0f;
            previewPlayer.bodyYaw = 0.0f;
            previewPlayer.setYaw(0.0f);
            previewPlayer.setPitch(0.0f);
            previewPlayer.limbAnimator.setSpeed(0.0f);

            Quaternionf bodyRotation = new Quaternionf()
                    .rotateX((float) Math.toRadians(180f + modelPitch))
                    .rotateY((float) Math.toRadians(modelYaw));

            InventoryScreen.drawEntity(
                    context,
                    x, y,
                    38,
                    bodyRotation,
                    null,
                    previewPlayer
            );

            context.getMatrices().pop();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int panelWidth = 205;
        int totalWidth = (panelWidth * 2) + 12;
        int startX = (width - totalWidth) / 2;
        int topY = (height - 220) / 2;
        int currentPanelX = startX + panelWidth + 12;

        if (button == 0 && mouseX >= currentPanelX && mouseX <= currentPanelX + panelWidth && mouseY >= topY + 20 && mouseY <= topY + 125) {
            modelYaw += (float) deltaX * 1.5f;
            modelPitch = Math.max(-80.0f, Math.min(80.0f, modelPitch + (float) deltaY * 1.5f));
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isEditingName && nameInputField != null && nameInputField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applySkinRename();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRenameMode();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

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
        closeRenameMode();
        createPreviewPlayerForSelected();
        updateUIValues();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = 205;
        int totalWidth = (panelWidth * 2) + 12;
        int startX = (width - totalWidth) / 2;
        int leftX = startX;
        int topY = (height - 220) / 2;

        for (int i = 0; i < validEntries.size(); i++) {
            int itemY = topY + (i * 35);
            if (mouseX >= leftX && mouseX <= leftX + panelWidth && mouseY >= itemY && mouseY < itemY + 31) {
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
        refreshAndValidateStorage();

        if (deletingIndex != -1) {
            deleteAnimProgress -= delta * 0.18f;
            if (deleteAnimProgress <= 0.0f) {
                executeActualDelete();
            }
        }

        animProgress += (targetAnim - animProgress) * 0.2f;

        int panelWidth = 205;
        int panelHeight = 215;
        int totalWidth = (panelWidth * 2) + 12;
        int startX = (width - totalWidth) / 2;
        int topY = (height - 220) / 2;

        int basePanelX = startX + panelWidth + 12;
        int currentPanelX = basePanelX + (int) ((1.0f - animProgress) * 30);

        boolean showPanel = animProgress > 0.05f && !validEntries.isEmpty();

        // 1. Title หลัก
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Skin Storage Cloud"), width / 2, Math.max(6, topY - 20), 0xFFFFFF);

        int leftX = startX;

        // ===================== 2. ฝั่งซ้าย: List Panel Background & Items =====================
        context.fill(leftX, topY, leftX + panelWidth, topY + panelHeight, 0xC0101014);

        if (validEntries.isEmpty()) {
            context.drawText(textRenderer, "No saved skins yet.", leftX + 12, topY + 12, 0xAAAAAA, false);
        } else {
            for (int i = 0; i < validEntries.size(); i++) {
                SkinEntry entry = validEntries.get(i);
                int baseItemY = topY + (i * 35);

                int itemX = leftX;
                int itemY = baseItemY;

                if (i == deletingIndex) {
                    itemX = leftX - (int) ((1.0f - deleteAnimProgress) * 210);
                } else if (deletingIndex != -1 && i > deletingIndex) {
                    itemY = baseItemY - (int) ((1.0f - deleteAnimProgress) * 35);
                }

                // Item Highlight
                boolean isSelected = (i == selectedIndex);
                int bgColor = isSelected ? 0x803B82F6 : 0x30FFFFFF;
                context.fill(itemX + 2, itemY + 2, itemX + panelWidth - 2, itemY + 32, bgColor);

                if (isSelected) {
                    context.fill(itemX + 2, itemY + 2, itemX + 5, itemY + 32, 0xFF3B82F6); // แถบไฮไลต์ด้านซ้าย
                }

                // สัญลักษณ์อันดับ #
                context.drawText(textRenderer, "#" + (i + 1), itemX + 9, itemY + 12, 0xFFFFAA00, true);

                // ภาพส่วนหัวสกิน
                Identifier textureId = loadedTextures.get(entry.id);
                if (textureId != null) {
                    context.drawTexture(textureId, itemX + 28, itemY + 5, 22, 22, 8.0f, 8.0f, 8, 8, 64, 64);
                    context.drawTexture(textureId, itemX + 28, itemY + 5, 22, 22, 40.0f, 8.0f, 8, 8, 64, 64);
                }

                // ชื่อและรายละเอียดสกิน
                String displayName = entry.name.length() > 12 ? entry.name.substring(0, 10) + ".." : entry.name;
                Integer k = skinKeybinds.get(entry.id);
                if (k != null && k != GLFW.GLFW_KEY_UNKNOWN) {
                    String kName = GLFW.glfwGetKeyName(k, 0);
                    displayName += " [" + (kName != null ? kName.toUpperCase() : k) + "]";
                }

                context.drawText(textRenderer, displayName, itemX + 56, itemY + 6, 0xFFFFFF, false);
                context.drawText(textRenderer, entry.createdDate + " (" + entry.modelType + ")", itemX + 56, itemY + 18, 0xAAAAAA, false);
            }
        }

        // ===================== 3. ฝั่งขวา: Preview & Control Panel =====================
        if (showPanel) {
            context.fill(currentPanelX, topY, currentPanelX + panelWidth, topY + panelHeight, 0xC0101014);
            context.fill(currentPanelX, topY, currentPanelX + 2, topY + panelHeight, 0xFF3B82F6);

            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                // Header (แสดงชื่อ หรือ ช่องกรอกชื่อ)
                if (isEditingName) {
                    nameInputField.setVisible(true);
                    nameInputField.setX(currentPanelX + 10);
                    nameInputField.setY(topY + 6);
                } else {
                    nameInputField.setVisible(false);
                    String titleText = "✦ " + (entry.name.length() > 18 ? entry.name.substring(0, 16) + ".." : entry.name);
                    context.drawCenteredTextWithShadow(textRenderer, titleText, currentPanelX + (panelWidth / 2), topY + 8, 0xFFFFAA00);
                }

                // 3D Preview
                int previewX = currentPanelX + (panelWidth / 2);
                int previewY = topY + 115;
                render3DPreview(context, previewX, previewY);
            }
        }

        // ===================== 4. อัปเดตตำแหน่งปุ่มกด =====================
        equipButton.visible = showPanel;
        equipButton.setX(currentPanelX + 10);

        toggleTypeButton.visible = showPanel;
        toggleTypeButton.setX(currentPanelX + 10);

        keybindButton.visible = showPanel;
        keybindButton.setX(currentPanelX + 105);

        renameButton.visible = showPanel;
        renameButton.setX(currentPanelX + 10);

        deleteButton.visible = showPanel;
        deleteButton.setX(currentPanelX + 105);

        super.render(context, mouseX, mouseY, delta);
    }
}