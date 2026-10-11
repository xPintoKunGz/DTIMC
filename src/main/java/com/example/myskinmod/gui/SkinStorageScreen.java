package com.example.myskinmod.gui;

import com.example.myskinmod.SkinChangerScreen;
import com.example.myskinmod.storage.SkinStorageManager;
import com.example.myskinmod.storage.SkinStorageManager.SkinEntry;
import com.example.myskinmod.util.SkinHelper;
import com.example.myskinmod.util.SkinNetworkHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SkinStorageScreen extends Screen {

    // ------------------------------------------------------------------
    // Layout constants (every panel shares the same height / header style)
    // ------------------------------------------------------------------
    private static final int PANEL_H = 226;
    private static final int ROW_H = 34;          // stride of a skin row
    private static final int ROW_INNER_H = 30;    // drawn height of a skin row
    private static final int CAT_ROW_H = 22;      // stride of a category row
    private static final int MAX_NAME_LEN = 24;

    private static final int ACCENT = 0xFF7DD3FC;
    private static final int GOLD = 0xFFFFAA00;
    private static final int PINK = 0xFFFF7B9C;
    private static final int VIOLET = 0xFFB69CFF;
    private static final int GREEN = 0xFF6EE7A8;

    private enum EditMode { NONE, SKIN, CAT_NEW, CAT_RENAME }

    private enum Modal { NONE, DELETE_SKIN, DELETE_CAT, MOVE }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private final Screen parent;
    private final Map<String, Identifier> loadedTextures = new HashMap<>();
    private final Map<String, int[]> textureSizes = new HashMap<>();
    private final List<SkinEntry> validEntries = new ArrayList<>();

    // Categories / favorites / ordering
    private final List<String> categories = new ArrayList<>(Arrays.asList("All", "Favorites"));
    private String currentCategory = "All";
    private final Map<String, String> skinCategories = new HashMap<>();
    private final Set<String> favoriteSkins = new HashSet<>();
    private final List<String> skinOrder = new ArrayList<>();

    private String selectedId = null;
    private String previewKey = null;
    private boolean pendingScrollToSelected = false;

    // Drag (reorder / move to category)
    private String draggedEntryId = null;
    private double dragStartX, dragStartY;
    private boolean dragStarted = false;
    private boolean reorderMode = false;

    // Scrolling
    private double scrollOffset = 0.0;
    private double categoryScrollOffset = 0.0;
    private boolean scrollbarDragging = false;

    // Inline editing (no text field widget – the text itself is edited)
    private EditMode editMode = EditMode.NONE;
    private String editBuffer = "";
    private String editTargetId = null;
    private String editTargetCat = null;

    // Modal dialog
    private Modal modal = Modal.NONE;
    private String pendingSkinId = null;
    private String pendingCategory = null;

    private static String importTargetCategory = "All";
    private static final Set<String> entriesBeforeImport = new HashSet<>();

    // Widgets
    private ButtonWidget equipButton;
    private ButtonWidget toggleTypeButton;
    private ButtonWidget deleteButton;
    private ButtonWidget addSkinButton;

    // 3D preview
    private ClientPlayerEntity previewPlayer;
    private Identifier previewSkinId;
    private float modelYaw = 0.0f;
    private float modelPitch = 0.0f;
    private boolean rotating = false;

    // Misc
    private long lastRefresh = 0L;
    private String hoverTip = null;

    // Layout (recomputed by computeLayout)
    private int catW, panelW, startX, listX, rightX, topY;

    public SkinStorageScreen(Screen parent) {
        super(Text.literal("Skin Storage Cloud"));
        this.parent = parent;
        SkinStorageManager.init();
        loadCategoryData();
    }

    // ==================================================================
    // Init / layout
    // ==================================================================
    private void computeLayout() {
        catW = Math.max(58, Math.min(110, width / 7));
        panelW = Math.max(86, Math.min(210, (width - catW - 32) / 2));
        int total = catW + panelW * 2 + 16;
        startX = (width - total) / 2;
        listX = startX + catW + 8;
        rightX = listX + panelW + 8;
        topY = Math.max(22, (height - 256) / 2 + 10);
    }

    @Override
    protected void init() {
        computeLayout();
        refreshAndValidateStorage();
        lastRefresh = System.currentTimeMillis();

        int bx = rightX + 8;
        int bw = panelW - 16;

        equipButton = ButtonWidget.builder(Text.literal("⚡ Equip Skin"), b -> equipSelectedSkin())
                .dimensions(bx, topY + 142, bw, 20).build();
        this.addDrawableChild(equipButton);

        toggleTypeButton = ButtonWidget.builder(Text.literal("Model: DEF"), b -> {
            SkinEntry entry = getSelectedEntry();
            if (entry != null) {
                String newType = entry.modelType.equals("slim") ? "default" : "slim";
                SkinStorageManager.updateSkin(entry.id, entry.name, newType);
                refreshAndValidateStorage(); // preview rebuilds automatically (key changes)
            }
        }).dimensions(bx, topY + 166, bw, 20).build();
        this.addDrawableChild(toggleTypeButton);

        deleteButton = ButtonWidget.builder(Text.literal("× Delete Skin").formatted(Formatting.RED), b -> startDeleteSkin())
                .dimensions(bx, topY + 190, bw, 20).build();
        this.addDrawableChild(deleteButton);

        int importW = Math.min(180, panelW * 2 - 24);
        addSkinButton = ButtonWidget.builder(Text.literal("+ Import New Skin"), b -> {
            importTargetCategory = isCustomCategory(currentCategory) ? currentCategory : "All";
            entriesBeforeImport.clear();
            for (SkinEntry existing : SkinStorageManager.getEntries()) {
                entriesBeforeImport.add(existing.id);
            }
            if (this.client != null) this.client.setScreen(new SkinChangerScreen());
        }).dimensions(listX + (panelW * 2 + 8 - importW) / 2, topY + 124, importW, 22).build();
        this.addDrawableChild(addSkinButton);

        ButtonWidget backButton = ButtonWidget.builder(Text.translatable("gui.back"), b -> {
            if (this.client != null) this.client.setScreen(parent);
        }).dimensions((width - 140) / 2, topY + PANEL_H + 8, 140, 20).build();
        this.addDrawableChild(backButton);
    }

    public static String getImportTargetCategory() {
        return importTargetCategory;
    }

    public static void clearImportTargetCategory() {
        importTargetCategory = "All";
    }

    // ==================================================================
    // Persistence
    // ==================================================================
    private Path getConfigPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/myskinmod_storage.json");
    }

    private void saveCategoryData() {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();

            JsonArray catArray = new JsonArray();
            for (String cat : categories) {
                if (isCustomCategory(cat)) catArray.add(cat);
            }
            root.add("customCategories", catArray);

            JsonObject skinMap = new JsonObject();
            for (Map.Entry<String, String> entry : skinCategories.entrySet()) {
                skinMap.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("skinCategories", skinMap);

            JsonArray favArray = new JsonArray();
            for (String id : favoriteSkins) favArray.add(id);
            root.add("favorites", favArray);

            JsonArray orderArray = new JsonArray();
            for (String id : skinOrder) orderArray.add(id);
            root.add("skinOrder", orderArray);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCategoryData() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) return;

            String jsonStr = Files.readString(path);
            JsonObject root = new Gson().fromJson(jsonStr, JsonObject.class);

            if (root.has("customCategories")) {
                for (JsonElement el : root.getAsJsonArray("customCategories")) {
                    String cat = el.getAsString();
                    if (!categories.contains(cat)) categories.add(cat);
                }
            }
            if (root.has("skinCategories")) {
                JsonObject skinMap = root.getAsJsonObject("skinCategories");
                for (String key : skinMap.keySet()) {
                    skinCategories.put(key, skinMap.get(key).getAsString());
                }
            }
            if (root.has("favorites")) {
                for (JsonElement el : root.getAsJsonArray("favorites")) favoriteSkins.add(el.getAsString());
            }
            if (root.has("skinOrder")) {
                for (JsonElement el : root.getAsJsonArray("skinOrder")) {
                    String id = el.getAsString();
                    if (!skinOrder.contains(id)) skinOrder.add(id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================================================================
    // Data helpers
    // ==================================================================
    private static boolean isCustomCategory(String name) {
        return !name.equals("All") && !name.equals("Favorites");
    }

    private List<SkinEntry> getFilteredEntries() {
        List<SkinEntry> filtered = new ArrayList<>();
        for (SkinEntry entry : validEntries) {
            if (currentCategory.equals("All")) {
                filtered.add(entry);
            } else if (currentCategory.equals("Favorites")) {
                if (favoriteSkins.contains(entry.id)) filtered.add(entry);
            } else if (skinCategories.getOrDefault(entry.id, "All").equals(currentCategory)) {
                filtered.add(entry);
            }
        }
        // In "All" the favorites always come first; inside each group the saved order is used.
        final boolean all = currentCategory.equals("All");
        filtered.sort((a, b) -> {
            if (all) {
                boolean af = favoriteSkins.contains(a.id);
                boolean bf = favoriteSkins.contains(b.id);
                if (af != bf) return af ? -1 : 1;
            }
            int ai = skinOrder.indexOf(a.id);
            int bi = skinOrder.indexOf(b.id);
            if (ai < 0) ai = Integer.MAX_VALUE;
            if (bi < 0) bi = Integer.MAX_VALUE;
            int byOrder = Integer.compare(ai, bi);
            return byOrder != 0 ? byOrder : a.name.compareToIgnoreCase(b.name);
        });
        return filtered;
    }

    private int countFor(String category) {
        if (category.equals("All")) return validEntries.size();
        int n = 0;
        for (SkinEntry e : validEntries) {
            if (category.equals("Favorites")) {
                if (favoriteSkins.contains(e.id)) n++;
            } else if (skinCategories.getOrDefault(e.id, "All").equals(category)) {
                n++;
            }
        }
        return n;
    }

    private SkinEntry findEntryById(String id) {
        if (id == null) return null;
        for (SkinEntry entry : validEntries) if (entry.id.equals(id)) return entry;
        return null;
    }

    private SkinEntry getSelectedEntry() {
        return findEntryById(selectedId);
    }

    private static int indexOfId(List<SkinEntry> list, String id) {
        if (id == null) return -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return i;
        return -1;
    }

    private void refreshAndValidateStorage() {
        validEntries.clear();
        var allEntries = SkinStorageManager.getEntries();

        for (SkinEntry entry : allEntries) {
            Path skinPath = SkinStorageManager.getSkinPath(entry);
            if (Files.exists(skinPath)) {
                validEntries.add(entry);
                if (!skinOrder.contains(entry.id)) skinOrder.add(entry.id);
                if (!loadedTextures.containsKey(entry.id)) loadTextureForEntry(entry);

                // A brand new skin is bound to the folder it was imported into.
                if (!skinCategories.containsKey(entry.id)) {
                    boolean newlyImported = !entriesBeforeImport.contains(entry.id);
                    String assignCat = "All";
                    if (newlyImported && isCustomCategory(importTargetCategory)) {
                        assignCat = importTargetCategory;
                    } else if (isCustomCategory(currentCategory)) {
                        assignCat = currentCategory;
                    }
                    skinCategories.put(entry.id, assignCat);
                    saveCategoryData();

                    if (newlyImported) {
                        importTargetCategory = "All";
                        entriesBeforeImport.clear();
                    }
                }
            }
        }

        Set<String> liveIds = new HashSet<>();
        for (SkinEntry entry : validEntries) liveIds.add(entry.id);
        skinOrder.removeIf(id -> !liveIds.contains(id));
        for (SkinEntry entry : validEntries) if (!skinOrder.contains(entry.id)) skinOrder.add(entry.id);
    }

    private void loadTextureForEntry(SkinEntry entry) {
        Path path = SkinStorageManager.getSkinPath(entry);
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path);
                 NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                NativeImageBackedTexture texture = new NativeImageBackedTexture(img);
                Identifier id = MinecraftClient.getInstance().getTextureManager()
                        .registerDynamicTexture("storage_skin_" + entry.id, texture);
                loadedTextures.put(entry.id, id);
                textureSizes.put(entry.id, new int[]{w, h});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================================================================
    // Selection / preview
    // ==================================================================
    private void syncSelection(List<SkinEntry> filtered) {
        if (selectedId != null && indexOfId(filtered, selectedId) < 0) selectedId = null;
        if (selectedId == null && !filtered.isEmpty()) selectedId = filtered.get(0).id;

        SkinEntry e = getSelectedEntry();
        String key = e == null ? null : e.id + "|" + e.modelType;
        if (!Objects.equals(key, previewKey)) {
            boolean newSkin = e == null || previewKey == null || !previewKey.startsWith(e.id + "|");
            previewKey = key;
            if (newSkin) {
                modelYaw = 0.0f;
                modelPitch = 0.0f;
            }
            createPreviewPlayerForSelected();
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
                return;
            }
        }
        this.previewSkinId = null;
        this.previewPlayer = null;
    }

    private void render3DPreview(DrawContext context, int x, int y) {
        if (previewPlayer != null && previewSkinId != null) {
            previewPlayer.age = 0;
            previewPlayer.headYaw = 0.0f;
            previewPlayer.bodyYaw = 0.0f;
            previewPlayer.setYaw(0.0f);
            previewPlayer.setPitch(0.0f);
            previewPlayer.limbAnimator.setSpeed(0.0f);

            Quaternionf bodyRotation = new Quaternionf()
                    .rotateX((float) Math.toRadians(180f + modelPitch))
                    .rotateY((float) Math.toRadians(modelYaw));

            InventoryScreen.drawEntity(context, x, y, 38, bodyRotation, null, previewPlayer);
        }
    }

    // ==================================================================
    // Actions
    // ==================================================================
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

    private void showToast(String title, String description) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getToastManager() != null) {
            SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                    Text.literal(title), Text.literal(description));
        }
    }

    private void toggleFavorite(String id) {
        if (!favoriteSkins.remove(id)) favoriteSkins.add(id);
        saveCategoryData();
    }

    // ---- delete / move (all go through the modal) ----
    private void startDeleteSkin() {
        SkinEntry entry = getSelectedEntry();
        if (entry == null) return;
        pendingSkinId = entry.id;
        modal = Modal.DELETE_SKIN;
    }

    private void startDeleteCategory() {
        if (!isCustomCategory(currentCategory)) return;
        pendingCategory = currentCategory;
        modal = Modal.DELETE_CAT;
    }

    private void closeModal() {
        modal = Modal.NONE;
        pendingSkinId = null;
        pendingCategory = null;
    }

    private void confirmModal() {
        switch (modal) {
            case DELETE_SKIN -> executeDeleteSkin();
            case DELETE_CAT -> executeDeleteCategory();
            case MOVE -> executeMove();
            default -> { }
        }
        closeModal();
    }

    private void executeDeleteSkin() {
        SkinEntry e = findEntryById(pendingSkinId);
        if (e == null) return;
        String id = e.id;
        String name = e.name;
        int idx = indexOfId(getFilteredEntries(), id);

        skinCategories.remove(id);
        favoriteSkins.remove(id);
        skinOrder.remove(id);
        SkinStorageManager.deleteSkin(id);

        Identifier tex = loadedTextures.remove(id);
        textureSizes.remove(id);
        if (tex != null) {
            try {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(tex);
            } catch (Exception ignored) { }
        }
        saveCategoryData();
        refreshAndValidateStorage();

        List<SkinEntry> after = getFilteredEntries();
        selectedId = after.isEmpty() ? null : after.get(Math.max(0, Math.min(idx, after.size() - 1))).id;
        showToast("Skin Storage", "Deleted: " + name);
    }

    private void executeDeleteCategory() {
        String catName = pendingCategory;
        if (catName == null || !isCustomCategory(catName)) return;
        skinCategories.values().removeIf(c -> c.equals(catName));
        categories.remove(catName);
        currentCategory = "All";
        scrollOffset = 0;
        saveCategoryData();
        refreshAndValidateStorage();
        showToast("Skin Storage", "Deleted folder: " + catName);
    }

    private void executeMove() {
        SkinEntry e = findEntryById(pendingSkinId);
        if (e == null || pendingCategory == null) return;
        skinCategories.put(e.id, pendingCategory);
        saveCategoryData();
        currentCategory = pendingCategory;
        selectedId = e.id;
        pendingScrollToSelected = true;
        showToast("Skin Storage", "Moved to " + pendingCategory);
    }

    private void handleDropOnCategory(int catIdx) {
        SkinEntry e = findEntryById(draggedEntryId);
        if (e == null || catIdx < 0 || catIdx >= categories.size()) return;
        String target = categories.get(catIdx);

        if (target.equals("Favorites")) {
            if (favoriteSkins.add(e.id)) {
                saveCategoryData();
                showToast("Skin Storage", "Added to Favorites: " + e.name);
            } else {
                showToast("Skin Storage", "Already in Favorites");
            }
            return;
        }
        String current = skinCategories.getOrDefault(e.id, "All");
        if (current.equals(target)) {
            showToast("Skin Storage", "Already in " + target);
            return;
        }
        pendingSkinId = e.id;
        pendingCategory = target;
        modal = Modal.MOVE;
    }

    // ---- reorder ----
    private int dropIndex(List<SkinEntry> filtered, double my) {
        int idx = (int) Math.floor((my - (topY + 28) + scrollOffset) / ROW_H);
        return Math.max(0, Math.min(filtered.size() - 1, idx));
    }

    private void performReorder(double my) {
        List<SkinEntry> filtered = getFilteredEntries();
        int oldIdx = indexOfId(filtered, draggedEntryId);
        if (oldIdx < 0 || filtered.isEmpty()) return;
        int newIdx = dropIndex(filtered, my);
        if (newIdx == oldIdx) return;

        List<String> ids = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (SkinEntry e : filtered) {
            ids.add(e.id);
            int p = skinOrder.indexOf(e.id);
            if (p < 0) return;
            slots.add(p);
        }
        Collections.sort(slots);
        String moved = ids.remove(oldIdx);
        ids.add(newIdx, moved);
        for (int k = 0; k < slots.size(); k++) skinOrder.set(slots.get(k), ids.get(k));
        saveCategoryData();
    }

    // ---- inline editing ----
    private void beginSkinEdit(SkinEntry entry) {
        editMode = EditMode.SKIN;
        editTargetId = entry.id;
        editBuffer = entry.name;
    }

    private void startCreateCategory() {
        editMode = EditMode.CAT_NEW;
        editBuffer = "";
        categoryScrollOffset = Double.MAX_VALUE; // clamped to bottom on next frame
    }

    private void startRenameCategory() {
        if (!isCustomCategory(currentCategory)) return;
        editMode = EditMode.CAT_RENAME;
        editTargetCat = currentCategory;
        editBuffer = currentCategory;
    }

    private void cancelEdit() {
        editMode = EditMode.NONE;
        editBuffer = "";
        editTargetId = null;
        editTargetCat = null;
    }

    private boolean nameTaken(String text) {
        if (text.equalsIgnoreCase("All") || text.equalsIgnoreCase("Favorites")) return true;
        for (String c : categories) if (c.equalsIgnoreCase(text)) return true;
        return false;
    }

    private void applyEdit() {
        EditMode mode = editMode;
        String text = editBuffer.trim();
        String skinId = editTargetId;
        String oldCat = editTargetCat;
        cancelEdit();
        if (text.isEmpty()) return;

        switch (mode) {
            case CAT_NEW -> {
                if (nameTaken(text)) {
                    showToast("Skin Storage", "That folder name is already used");
                    return;
                }
                categories.add(text);
                currentCategory = text;
                scrollOffset = 0;
                categoryScrollOffset = Double.MAX_VALUE;
                saveCategoryData();
            }
            case CAT_RENAME -> {
                if (oldCat == null || text.equals(oldCat)) return;
                if (nameTaken(text)) {
                    showToast("Skin Storage", "That folder name is already used");
                    return;
                }
                int idx = categories.indexOf(oldCat);
                if (idx >= 2) {
                    categories.set(idx, text);
                    for (Map.Entry<String, String> entry : skinCategories.entrySet()) {
                        if (entry.getValue().equals(oldCat)) entry.setValue(text);
                    }
                    if (currentCategory.equals(oldCat)) currentCategory = text;
                    saveCategoryData();
                }
            }
            case SKIN -> {
                SkinEntry e = findEntryById(skinId);
                if (e != null && !text.equals(e.name)) {
                    SkinStorageManager.renameSkin(e.id, text);
                    showToast("Skin Storage", "Renamed to: " + text);
                    refreshAndValidateStorage();
                }
            }
            default -> { }
        }
    }

    // ==================================================================
    // Geometry helpers
    // ==================================================================
    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean in(int[] r, double mx, double my) {
        return in(mx, my, r[0], r[1], r[2], r[3]);
    }

    /** Toolbar tile i (0 = new, 1 = rename, 2 = delete) at the bottom of the category panel. */
    private int[] toolbarTile(int i) {
        int gap = 2;
        int tw = (catW - 8 - gap * 2) / 3;
        int total = tw * 3 + gap * 2;
        int x0 = startX + (catW - total) / 2;
        return new int[]{x0 + i * (tw + gap), topY + PANEL_H - 26, tw, 20};
    }

    private int[] reorderTile() {
        return new int[]{listX + panelW - 27, topY + 5, 20, 14};
    }

    private int customTop() { return topY + 82; }

    private int customBottom() { return topY + PANEL_H - 30; }

    private int listTop() { return topY + 26; }

    private int listBottom() { return topY + PANEL_H - 4; }

    private int categoryAt(double mx, double my) {
        if (mx < startX + 4 || mx >= startX + catW - 4) return -1;
        for (int i = 0; i < 2; i++) {
            int y = topY + 24 + i * CAT_ROW_H;
            if (my >= y && my < y + 20) return i;
        }
        if (my >= customTop() && my < customBottom()) {
            for (int i = 2; i < categories.size(); i++) {
                int y = customTop() + (i - 2) * CAT_ROW_H - (int) categoryScrollOffset;
                if (my >= y && my < y + 20) return i;
            }
        }
        return -1;
    }

    private boolean inListViewport(double mx, double my) {
        return mx >= listX && mx < listX + panelW && my >= listTop() && my < listBottom();
    }

    private int listMaxScroll(int count) {
        return Math.max(0, count * ROW_H + 4 - (listBottom() - listTop()));
    }

    private void clampScrolls(List<SkinEntry> filtered) {
        scrollOffset = Math.max(0, Math.min(listMaxScroll(filtered.size()), scrollOffset));
        int shown = Math.max(0, categories.size() - 2) + (editMode == EditMode.CAT_NEW ? 1 : 0);
        double maxCat = Math.max(0, shown * CAT_ROW_H - (customBottom() - customTop()));
        categoryScrollOffset = Math.max(0, Math.min(maxCat, categoryScrollOffset));
    }

    private void scrollToSelected(List<SkinEntry> filtered) {
        int idx = indexOfId(filtered, selectedId);
        if (idx < 0) return;
        int vpH = listBottom() - listTop();
        int top = idx * ROW_H;
        if (top + 2 < scrollOffset) {
            scrollOffset = top;
        } else if (top + 2 + ROW_INNER_H > scrollOffset + vpH) {
            scrollOffset = top + 2 + ROW_INNER_H - vpH + 2;
        }
    }

    private void scrollFromMouse(double my, int count) {
        int vpTop = listTop();
        int vpH = listBottom() - listTop();
        int contentH = count * ROW_H + 4;
        if (contentH <= vpH) return;
        int thumbH = Math.max(18, vpH * vpH / contentH);
        double frac = (my - vpTop - thumbH / 2.0) / Math.max(1, vpH - thumbH);
        frac = Math.max(0, Math.min(1, frac));
        scrollOffset = frac * listMaxScroll(count);
    }

    private String fit(String s, int maxW) {
        if (maxW <= 0) return "";
        if (textRenderer.getWidth(s) <= maxW) return s;
        String ell = "..";
        int ew = textRenderer.getWidth(ell);
        if (maxW <= ew) return textRenderer.trimToWidth(s, maxW);
        return textRenderer.trimToWidth(s, maxW - ew) + ell;
    }

    // ==================================================================
    // Input
    // ==================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        computeLayout();

        // Modal layer swallows everything.
        if (modal != Modal.NONE) {
            if (button == 0) {
                int[] ok = modalConfirmRect();
                int[] no = modalCancelRect();
                if (in(ok, mouseX, mouseY)) confirmModal();
                else if (in(no, mouseX, mouseY)) closeModal();
            }
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Clicking anywhere while editing commits the edit first.
        String committedSkinId = editMode == EditMode.SKIN ? editTargetId : null;
        if (editMode != EditMode.NONE) applyEdit();

        List<SkinEntry> filtered = getFilteredEntries();

        // Category toolbar
        boolean custom = isCustomCategory(currentCategory);
        for (int i = 0; i < 3; i++) {
            if (in(toolbarTile(i), mouseX, mouseY)) {
                if (i == 0) startCreateCategory();
                else if (i == 1 && custom) startRenameCategory();
                else if (i == 2 && custom) startDeleteCategory();
                return true;
            }
        }

        // Reorder toggle
        if (in(reorderTile(), mouseX, mouseY)) {
            reorderMode = !reorderMode;
            draggedEntryId = null;
            dragStarted = false;
            return true;
        }

        // Category rows
        int ci = categoryAt(mouseX, mouseY);
        if (ci >= 0) {
            currentCategory = categories.get(ci);
            scrollOffset = 0;
            return true;
        }

        if (!filtered.isEmpty()) {
            // Scrollbar
            int vpH = listBottom() - listTop();
            if (filtered.size() * ROW_H + 4 > vpH
                    && in(mouseX, mouseY, listX + panelW - 8, listTop(), 8, vpH)) {
                scrollbarDragging = true;
                scrollFromMouse(mouseY, filtered.size());
                return true;
            }

            // Skin rows
            int x1 = listX + 3;
            int x2 = listX + panelW - 9;
            if (mouseX >= x1 && mouseX < x2 && mouseY >= listTop() && mouseY < listBottom()) {
                int idx = (int) Math.floor((mouseY - (topY + 28) + scrollOffset) / ROW_H);
                if (idx >= 0 && idx < filtered.size()) {
                    int y = topY + 28 + idx * ROW_H - (int) scrollOffset;
                    if (mouseY < y + ROW_INNER_H) {
                        SkinEntry e = filtered.get(idx);
                        int pencilX = x2 - 31;
                        int heartX = x2 - 16;
                        if (in(mouseX, mouseY, pencilX, y + 8, 14, 14)) {
                            if (e.id.equals(committedSkinId)) return true; // the ✓ button just saved it
                            selectedId = e.id;
                            beginSkinEdit(e);
                            return true;
                        }
                        if (in(mouseX, mouseY, heartX, y + 8, 14, 14)) {
                            toggleFavorite(e.id);
                            return true;
                        }
                        draggedEntryId = e.id;
                        dragStartX = mouseX;
                        dragStartY = mouseY;
                        dragStarted = false;
                        selectedId = e.id;
                        return true;
                    }
                }
            }

            // Preview rotation
            if (getSelectedEntry() != null
                    && in(mouseX, mouseY, rightX + 6, topY + 24, panelW - 12, 112)) {
                rotating = true;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (modal != Modal.NONE) return true;
        if (button == 0) {
            if (scrollbarDragging) {
                scrollFromMouse(mouseY, getFilteredEntries().size());
                return true;
            }
            if (draggedEntryId != null) {
                if (!dragStarted && Math.abs(mouseX - dragStartX) + Math.abs(mouseY - dragStartY) > 4) {
                    dragStarted = true;
                }
                return true;
            }
            if (rotating) {
                modelYaw += (float) deltaX * 1.5f;
                modelPitch = Math.max(-80.0f, Math.min(80.0f, modelPitch + (float) deltaY * 1.5f));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            computeLayout();
            scrollbarDragging = false;
            rotating = false;
            if (draggedEntryId != null) {
                if (dragStarted && modal == Modal.NONE) {
                    int ci = categoryAt(mouseX, mouseY);
                    if (ci >= 0) {
                        handleDropOnCategory(ci);
                    } else if (reorderMode && inListViewport(mouseX, mouseY)) {
                        performReorder(mouseY);
                    }
                }
                draggedEntryId = null;
                dragStarted = false;
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (modal != Modal.NONE) return true;
        computeLayout();
        if (mouseX >= startX && mouseX <= startX + catW && mouseY >= customTop() && mouseY < customBottom()) {
            categoryScrollOffset = Math.max(0, categoryScrollOffset - amount * 18);
            return true;
        }
        scrollOffset = Math.max(0, scrollOffset - amount * 18);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (modal != Modal.NONE) return true;
        if (editMode != EditMode.NONE) {
            if (SharedConstants.isValidChar(chr) && editBuffer.length() < MAX_NAME_LEN) editBuffer += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modal != Modal.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) confirmModal();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) closeModal();
            return true;
        }

        if (editMode != EditMode.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyEdit();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelEdit();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            } else if (Screen.isPaste(keyCode) && this.client != null) {
                String clip = this.client.keyboard.getClipboard();
                StringBuilder sb = new StringBuilder(editBuffer);
                for (char c : clip.toCharArray()) {
                    if (sb.length() >= MAX_NAME_LEN) break;
                    if (SharedConstants.isValidChar(c)) sb.append(c);
                }
                editBuffer = sb.toString();
            }
            return true;
        }

        List<SkinEntry> filtered = getFilteredEntries();
        if (!filtered.isEmpty()) {
            int idx = indexOfId(filtered, selectedId);
            if (keyCode == GLFW.GLFW_KEY_UP) {
                idx = idx <= 0 ? filtered.size() - 1 : idx - 1;
                selectedId = filtered.get(idx).id;
                pendingScrollToSelected = true;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                idx = idx >= filtered.size() - 1 ? 0 : idx + 1;
                selectedId = filtered.get(idx).id;
                pendingScrollToSelected = true;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_SPACE) {
                equipSelectedSkin();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_F2) {
                SkinEntry e = getSelectedEntry();
                if (e != null) beginSkinEdit(e);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
                startDeleteSkin();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================================================================
    // Rendering
    // ==================================================================
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        computeLayout();
        this.renderBackground(context);

        long now = System.currentTimeMillis();
        if (now - lastRefresh > 500) {
            refreshAndValidateStorage();
            lastRefresh = now;
        }

        List<SkinEntry> filtered = getFilteredEntries();
        syncSelection(filtered);
        if (pendingScrollToSelected) {
            scrollToSelected(filtered);
            pendingScrollToSelected = false;
        }
        autoScrollWhileDragging(mouseX, mouseY);
        clampScrolls(filtered);

        boolean modalOpen = modal != Modal.NONE;
        int mx = modalOpen ? -9999 : mouseX;
        int my = modalOpen ? -9999 : mouseY;
        hoverTip = null;
        boolean cursorOn = (now / 450) % 2 == 0;

        context.drawCenteredTextWithShadow(textRenderer, "Skin Storage Cloud", width / 2,
                Math.max(6, topY - 16), 0xFFFFFFFF);

        drawCategoryPanel(context, mx, my, cursorOn);

        boolean empty = filtered.isEmpty();
        equipButton.visible = !empty;
        toggleTypeButton.visible = !empty;
        deleteButton.visible = !empty;
        addSkinButton.visible = empty;
        SkinEntry sel = getSelectedEntry();
        if (!empty && sel != null) {
            toggleTypeButton.setMessage(Text.literal("Model: " + (sel.modelType.equals("slim") ? "SLIM" : "DEF")));
        }

        if (empty) {
            drawEmptyState(context);
        } else {
            drawListPanel(context, filtered, mx, my, cursorOn);
            drawRightPanel(context);
        }

        super.render(context, mx, my, delta); // widgets

        // Drag ghost
        if (dragStarted && draggedEntryId != null && !modalOpen) {
            SkinEntry d = findEntryById(draggedEntryId);
            if (d != null) {
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 2000);
                int gw = Math.min(130, textRenderer.getWidth(d.name) + 32);
                int gx = mouseX + 10, gy = mouseY + 6;
                context.fill(gx, gy, gx + gw, gy + 22, 0xEE17171D);
                context.fill(gx, gy, gx + gw, gy + 1, ACCENT);
                drawHead(context, d.id, gx + 2, gy + 2, 18);
                context.drawText(textRenderer, fit(d.name, gw - 26), gx + 24, gy + 7, 0xFFFFFFFF, false);
                context.getMatrices().pop();
            }
        }

        if (!modalOpen && hoverTip != null) {
            context.drawTooltip(textRenderer, Text.literal(hoverTip), mouseX, mouseY);
        }

        if (modalOpen) drawModal(context, mouseX, mouseY);
    }

    private void autoScrollWhileDragging(int mx, int my) {
        if (reorderMode && dragStarted && draggedEntryId != null && mx >= listX && mx <= listX + panelW) {
            if (my < listTop() + 16) scrollOffset -= 4;
            else if (my > listBottom() - 16) scrollOffset += 4;
        }
    }

    private void drawPanel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xD0101014);
        c.fill(x, y, x + w, y + 2, ACCENT);
        c.fill(x, y, x + 1, y + h, 0x22FFFFFF);
        c.fill(x + w - 1, y, x + w, y + h, 0x22FFFFFF);
        c.fill(x, y + h - 1, x + w, y + h, 0x33FFFFFF);
        c.fill(x + 6, y + 22, x + w - 6, y + 23, 0x22FFFFFF); // header separator
    }

    private void drawTile(DrawContext c, int x, int y, int w, int h, String glyph, int color,
                          boolean enabled, boolean active, boolean hover) {
        int bg = !enabled ? 0x33000000 : active ? 0xFF1F7A5A : hover ? 0xFF3A3F55 : 0xFF262A38;
        c.fill(x, y, x + w, y + h, bg);
        int top = !enabled ? 0x22FFFFFF : (hover || active) ? ACCENT : 0x44FFFFFF;
        c.fill(x, y, x + w, y + 1, top);
        int tc = enabled ? color : 0xFF555566;
        c.drawCenteredTextWithShadow(textRenderer, glyph, x + w / 2, y + (h - 8) / 2 + 1, tc);
    }

    private void drawHead(DrawContext c, String id, int x, int y, int size) {
        Identifier tex = loadedTextures.get(id);
        if (tex == null) return;
        int[] s = textureSizes.getOrDefault(id, new int[]{64, 64});
        float k = s[0] / 64.0f;
        int region = Math.max(1, Math.round(8 * k));
        c.drawTexture(tex, x, y, size, size, 8.0f * k, 8.0f * k, region, region, s[0], s[1]);
        c.drawTexture(tex, x, y, size, size, 40.0f * k, 8.0f * k, region, region, s[0], s[1]);
    }

    // ---------------- Category panel ----------------
    private void drawCategoryPanel(DrawContext c, int mx, int my, boolean cursorOn) {
        drawPanel(c, startX, topY, catW, PANEL_H);
        c.drawText(textRenderer, fit("Categories", catW - 12), startX + 8, topY + 9, GOLD, false);

        boolean dragging = dragStarted && draggedEntryId != null;
        boolean showCount = catW >= 76;

        // Main (pinned) categories
        for (int i = 0; i < 2; i++) {
            String name = categories.get(i);
            int y = topY + 24 + i * CAT_ROW_H;
            boolean hover = in(mx, my, startX + 4, y, catW - 8, 20);
            drawCategoryRow(c, name, startX + 4, y, catW - 8, i, name.equals(currentCategory),
                    hover, dragging && hover, showCount ? countFor(name) : -1, false, cursorOn);
        }

        // Divider
        int divY = topY + 69;
        c.drawText(textRenderer, "Custom", startX + 8, divY, 0xFF9A8CCB, false);
        int lw = textRenderer.getWidth("Custom");
        c.fill(startX + 8 + lw + 4, divY + 4, startX + catW - 6, divY + 5, 0x44FFFFFF);

        // Custom categories (scrollable)
        int cTop = customTop(), cBottom = customBottom();
        int customCount = categories.size() - 2;
        int shown = customCount + (editMode == EditMode.CAT_NEW ? 1 : 0);
        if (shown == 0) {
            c.drawCenteredTextWithShadow(textRenderer, "No folders yet", startX + catW / 2, cTop + 30, 0xFF777788);
            c.drawCenteredTextWithShadow(textRenderer, "Press + below", startX + catW / 2, cTop + 44, 0xFF555566);
        } else {
            c.enableScissor(startX + 2, cTop, startX + catW - 2, cBottom);
            for (int i = 0; i < shown; i++) {
                int y = cTop + i * CAT_ROW_H - (int) categoryScrollOffset;
                if (y + 20 < cTop || y > cBottom) continue;
                boolean creating = i >= customCount;
                String name = creating ? "" : categories.get(i + 2);
                boolean editing = creating
                        || (editMode == EditMode.CAT_RENAME && name.equals(editTargetCat));
                boolean hover = in(mx, my, startX + 4, y, catW - 8, 20) && my >= cTop && my < cBottom;
                drawCategoryRow(c, editing ? editBuffer : name, startX + 4, y, catW - 8, 2,
                        !creating && name.equals(currentCategory), hover, dragging && hover,
                        showCount && !editing ? countFor(name) : -1, editing, cursorOn);
            }
            c.disableScissor();

            int contentH = shown * CAT_ROW_H;
            int vpH = cBottom - cTop;
            if (contentH > vpH) {
                int tx = startX + catW - 3;
                c.fill(tx, cTop, tx + 2, cBottom, 0x66404048);
                int thumbH = Math.max(14, vpH * vpH / contentH);
                double maxScroll = Math.max(1, contentH - vpH);
                int thumbY = cTop + (int) ((vpH - thumbH) * (categoryScrollOffset / maxScroll));
                c.fill(tx, thumbY, tx + 2, thumbY + thumbH, VIOLET);
            }
        }

        // Toolbar (compact icon tiles)
        boolean custom = isCustomCategory(currentCategory);
        String[] glyphs = {"+", "✎", "×"};
        int[] colors = {GREEN, 0xFFFFD27A, 0xFFFF7B7B};
        String[] tips = {"New folder", "Rename folder", "Delete folder"};
        for (int i = 0; i < 3; i++) {
            int[] r = toolbarTile(i);
            boolean enabled = i == 0 || custom;
            boolean hover = in(r, mx, my);
            drawTile(c, r[0], r[1], r[2], r[3], glyphs[i], colors[i], enabled, false, hover);
            if (hover) hoverTip = enabled ? tips[i] : "Main folders can't be edited";
        }
    }

    /** kind: 0 = All, 1 = Favorites, 2 = custom. */
    private void drawCategoryRow(DrawContext c, String label, int x, int y, int w, int kind,
                                 boolean selected, boolean hover, boolean drop, int count,
                                 boolean editing, boolean cursorOn) {
        int accent = kind == 0 ? ACCENT : kind == 1 ? PINK : VIOLET;
        int bg;
        if (editing) bg = 0xCC3A3320;
        else if (drop) bg = 0xCC1F8F5F;
        else if (selected) bg = kind == 2 ? 0xCC40307A : 0xCC17607E;
        else if (hover) bg = 0x55FFFFFF;
        else bg = kind == 2 ? 0x30FFFFFF : 0x50000000;

        c.fill(x, y, x + w, y + 20, bg);
        c.fill(x, y, x + (kind == 2 ? 2 : 3), y + 20, accent);
        if (kind != 2) c.fill(x + 3, y, x + w, y + 1, (accent & 0x00FFFFFF) | 0x66000000); // "pinned" top edge
        if (selected && !editing) c.fill(x, y + 19, x + w, y + 20, accent);
        if (drop) c.fill(x, y, x + w, y + 1, GREEN);

        String icon = kind == 0 ? "★" : kind == 1 ? "♥" : "▣";
        c.drawText(textRenderer, icon, x + 7, y + 6, accent, false);

        int tx = x + 18;
        String cs = count >= 0 ? String.valueOf(count) : null;
        int maxW = w - 18 - 4 - (cs != null ? textRenderer.getWidth(cs) + 4 : 0);

        if (editing) {
            String shown = textRenderer.trimToWidth(label, Math.max(4, maxW - 4), true);
            c.drawText(textRenderer, shown, tx, y + 5, 0xFFFFE9A6, false);
            int cx = tx + textRenderer.getWidth(shown);
            if (cursorOn) c.fill(cx + 1, y + 4, cx + 2, y + 14, 0xFFFFFFFF);
            c.fill(tx - 1, y + 16, tx + maxW, y + 17, GOLD);
        } else {
            int color = selected ? 0xFFFFFFFF : kind == 2 ? 0xFFD6C7FF : 0xFFE5E7EB;
            if (kind != 2) {
                c.drawText(textRenderer, Text.literal(fit(label, maxW - 2)).formatted(Formatting.BOLD),
                        tx, y + 6, color, false);
            } else {
                c.drawText(textRenderer, fit(label, maxW), tx, y + 6, color, false);
            }
            if (cs != null) {
                c.drawText(textRenderer, cs, x + w - 4 - textRenderer.getWidth(cs), y + 6, 0xFF8A8FA3, false);
            }
        }
    }

    // ---------------- Empty state ----------------
    private void drawEmptyState(DrawContext c) {
        int w = panelW * 2 + 8;
        drawPanel(c, listX, topY, w, PANEL_H);
        c.drawText(textRenderer, fit("Folder: " + currentCategory, w - 20), listX + 10, topY + 9, GOLD, false);

        int cx = listX + w / 2;
        boolean fav = currentCategory.equals("Favorites");
        String l1 = validEntries.isEmpty() ? "Your skin storage is empty."
                : fav ? "No favorite skins yet." : "No skins in this folder yet.";
        String l2 = fav && !validEntries.isEmpty() ? "Tap the ♡ on a skin to add it here."
                : "Import a skin to get started.";
        c.drawCenteredTextWithShadow(textRenderer, "✦", cx, topY + 66, ACCENT);
        c.drawCenteredTextWithShadow(textRenderer, fit(l1, w - 20), cx, topY + 86, 0xFFCCCCCC);
        c.drawCenteredTextWithShadow(textRenderer, fit(l2, w - 20), cx, topY + 100, 0xFF888888);
    }

    // ---------------- List panel ----------------
    private void drawListPanel(DrawContext c, List<SkinEntry> filtered, int mx, int my, boolean cursorOn) {
        drawPanel(c, listX, topY, panelW, PANEL_H);

        // Header: folder name, count, reorder toggle
        int[] rt = reorderTile();
        boolean rHover = in(rt, mx, my);
        String countStr = String.valueOf(filtered.size());
        int countX = rt[0] - 5 - textRenderer.getWidth(countStr);
        c.drawText(textRenderer, countStr, countX, topY + 9, 0xFF8A8FA3, false);
        c.drawText(textRenderer, fit("Folder: " + currentCategory, countX - 6 - (listX + 10)),
                listX + 10, topY + 9, GOLD, false);
        drawTile(c, rt[0], rt[1], rt[2], rt[3], "↕", reorderMode ? 0xFFFFFFFF : 0xFFB8C0D4, true, reorderMode, rHover);
        if (rHover) hoverTip = reorderMode ? "Reorder: ON (drag skins up/down)" : "Reorder: OFF";

        int vpTop = listTop(), vpBottom = listBottom();
        int x1 = listX + 3;
        int x2 = listX + panelW - 9;
        boolean showIdx = panelW >= 130;
        boolean reorderDrag = reorderMode && dragStarted && draggedEntryId != null;
        boolean dragging = dragStarted && draggedEntryId != null;

        c.enableScissor(listX + 1, vpTop, listX + panelW - 1, vpBottom);
        for (int i = 0; i < filtered.size(); i++) {
            SkinEntry e = filtered.get(i);
            int y = topY + 28 + i * ROW_H - (int) scrollOffset;
            if (y + ROW_INNER_H < vpTop || y > vpBottom) continue;

            boolean selected = e.id.equals(selectedId);
            boolean beingDragged = dragStarted && e.id.equals(draggedEntryId);
            boolean hover = !dragging && mx >= x1 && mx < x2
                    && my >= Math.max(y, vpTop) && my < Math.min(y + ROW_INNER_H, vpBottom);
            boolean editingThis = editMode == EditMode.SKIN && e.id.equals(editTargetId);
            boolean fav = favoriteSkins.contains(e.id);

            int bg = editingThis ? 0xAA3A3320 : selected ? 0x904078A8 : hover ? 0x45FFFFFF : 0x30FFFFFF;
            if (beingDragged) bg = 0x18FFFFFF;
            c.fill(x1, y, x2, y + ROW_INNER_H, bg);
            if (selected) c.fill(x1, y, x1 + 3, y + ROW_INNER_H, ACCENT);
            else if (fav) c.fill(x1, y, x1 + 2, y + ROW_INNER_H, 0x99FF7B9C);

            if (showIdx) {
                String lead = reorderMode ? "≡" : "#" + (i + 1);
                c.drawText(textRenderer, lead, x1 + 7, y + 11, reorderMode ? GREEN : GOLD, true);
            }
            int headX = x1 + (showIdx ? 26 : 8);
            drawHead(c, e.id, headX, y + 4, 22);

            int nameX = headX + 22 + 6;
            int pencilX = x2 - 31;
            int heartX = x2 - 16;
            int nameMaxW = Math.max(10, pencilX - 3 - nameX);

            if (editingThis) {
                String shown = textRenderer.trimToWidth(editBuffer, nameMaxW - 3, true);
                c.drawText(textRenderer, shown, nameX, y + 5, 0xFFFFE9A6, false);
                int cx = nameX + textRenderer.getWidth(shown);
                if (cursorOn) c.fill(cx + 1, y + 4, cx + 2, y + 14, 0xFFFFFFFF);
                c.fill(nameX - 1, y + 15, nameX + nameMaxW, y + 16, GOLD);
                c.drawText(textRenderer, fit("Enter = save", nameMaxW), nameX, y + 19, 0xFF8FD9B0, false);
            } else {
                c.drawText(textRenderer, fit(e.name, nameMaxW), nameX, y + 6, 0xFFFFFFFF, false);
                c.drawText(textRenderer, fit(e.modelType.toUpperCase(), nameMaxW), nameX, y + 18, 0xFFAAAAAA, false);
            }

            // Rename ✎ / save ✓ (part of the row, so it scrolls and clips with it)
            boolean pHover = !dragging && in(mx, my, pencilX, y + 8, 14, 14)
                    && my >= vpTop && my < vpBottom;
            if (pHover) c.fill(pencilX, y + 8, pencilX + 14, y + 22, 0x40FFFFFF);
            int pColor = editingThis ? GREEN : pHover ? 0xFFFFFFFF : selected ? 0xFFFFD27A : 0xFFB0B4C4;
            c.drawCenteredTextWithShadow(textRenderer, editingThis ? "✓" : "✎", pencilX + 7, y + 11, pColor);
            if (pHover) hoverTip = editingThis ? "Save name" : "Rename";

            // Favorite heart
            boolean hHover = !dragging && in(mx, my, heartX, y + 8, 14, 14)
                    && my >= vpTop && my < vpBottom;
            if (hHover) c.fill(heartX, y + 8, heartX + 14, y + 22, 0x40FFFFFF);
            int hColor = fav ? 0xFFFF5555 : hHover ? 0xFFFFFFFF : 0xFFAAAAAA;
            c.drawCenteredTextWithShadow(textRenderer, fav ? "♥" : "♡", heartX + 7, y + 11, hColor);
            if (hHover) hoverTip = fav ? "Remove from favorites" : "Add to favorites";
        }

        // Reorder insertion marker
        if (reorderDrag && inListViewport(mx, my)) {
            int oldIdx = indexOfId(filtered, draggedEntryId);
            int dropIdx = dropIndex(filtered, my);
            if (oldIdx >= 0 && dropIdx != oldIdx) {
                int ly = topY + 28 + dropIdx * ROW_H - (int) scrollOffset + (dropIdx > oldIdx ? ROW_INNER_H : -3);
                c.fill(x1, ly, x2, ly + 2, GREEN);
            }
        }
        c.disableScissor();

        // Scrollbar
        int vpH = vpBottom - vpTop;
        int contentH = filtered.size() * ROW_H + 4;
        if (contentH > vpH) {
            int trackX = listX + panelW - 6;
            c.fill(trackX, vpTop, trackX + 3, vpBottom, 0x66404048);
            int thumbH = Math.max(18, vpH * vpH / contentH);
            int maxScroll = Math.max(1, contentH - vpH);
            int thumbY = vpTop + (int) ((vpH - thumbH) * (scrollOffset / maxScroll));
            c.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, scrollbarDragging ? 0xFFFFFFFF : ACCENT);
        }
    }

    // ---------------- Right panel ----------------
    private void drawRightPanel(DrawContext c) {
        drawPanel(c, rightX, topY, panelW, PANEL_H);
        SkinEntry e = getSelectedEntry();
        if (e == null) return;

        c.drawCenteredTextWithShadow(textRenderer, fit("✦ " + e.name, panelW - 16),
                rightX + panelW / 2, topY + 9, GOLD);

        int bx1 = rightX + 6, by1 = topY + 24, bx2 = rightX + panelW - 6, by2 = topY + 136;
        c.fill(bx1, by1, bx2, by2, 0x66000000);
        c.fill(bx1, by2 - 1, bx2, by2, 0x22FFFFFF);

        if (modal == Modal.NONE) render3DPreview(c, rightX + panelW / 2, topY + 120);
        if (previewPlayer == null) {
            c.drawCenteredTextWithShadow(textRenderer, "Preview unavailable", rightX + panelW / 2,
                    topY + 76, 0xFF666677);
        } else {
            c.drawCenteredTextWithShadow(textRenderer, "Drag to rotate", rightX + panelW / 2,
                    topY + 125, 0xFF555566);
        }
    }

    // ---------------- Modal ----------------
    private static final int DLG_W = 260, DLG_H = 96;

    private int[] modalConfirmRect() {
        int x = (width - DLG_W) / 2, y = (height - DLG_H) / 2;
        return new int[]{x + 16, y + 64, 108, 20};
    }

    private int[] modalCancelRect() {
        int x = (width - DLG_W) / 2, y = (height - DLG_H) / 2;
        return new int[]{x + 136, y + 64, 108, 20};
    }

    private void drawModal(DrawContext c, int mx, int my) {
        String title, line, confirmLabel;
        int accent, confirmBase, confirmHover;
        switch (modal) {
            case DELETE_CAT -> {
                title = "Delete folder?";
                line = "Delete '" + pendingCategory + "'? Skins go back to All.";
                confirmLabel = "Delete";
                accent = 0xFFFF5555;
                confirmBase = 0xFFB83232;
                confirmHover = 0xFFE34B55;
            }
            case MOVE -> {
                SkinEntry moving = findEntryById(pendingSkinId);
                title = "Move skin to folder?";
                line = (moving == null ? "Unknown skin" : moving.name) + " → " + pendingCategory;
                confirmLabel = "Confirm move";
                accent = ACCENT;
                confirmBase = 0xFF18785A;
                confirmHover = 0xFF269B75;
            }
            default -> {
                SkinEntry e = findEntryById(pendingSkinId);
                title = "Delete skin?";
                line = "Delete '" + (e == null ? "" : e.name) + "' permanently?";
                confirmLabel = "Delete";
                accent = 0xFFFF5555;
                confirmBase = 0xFFB83232;
                confirmHover = 0xFFE34B55;
            }
        }

        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 3000); // always above the 3D preview and widgets

        c.fill(0, 0, width, height, 0xB0000000);
        int x = (width - DLG_W) / 2, y = (height - DLG_H) / 2;
        c.fill(x - 1, y - 1, x + DLG_W + 1, y + DLG_H + 1, 0x55FFFFFF);
        c.fill(x, y, x + DLG_W, y + DLG_H, 0xFF17171D);
        c.fill(x, y, x + DLG_W, y + 3, accent);

        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, y + 14, accent);
        c.drawCenteredTextWithShadow(textRenderer, fit(line, DLG_W - 24), width / 2, y + 36, 0xFFE0E0E0);

        int[] ok = modalConfirmRect();
        int[] no = modalCancelRect();
        boolean okHover = in(ok, mx, my);
        boolean noHover = in(no, mx, my);

        c.fill(ok[0], ok[1], ok[0] + ok[2], ok[1] + ok[3], okHover ? confirmHover : confirmBase);
        if (okHover) c.fill(ok[0], ok[1], ok[0] + ok[2], ok[1] + 1, 0xFFFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer, confirmLabel, ok[0] + ok[2] / 2, ok[1] + 6, 0xFFFFFFFF);

        c.fill(no[0], no[1], no[0] + no[2], no[1] + no[3], noHover ? 0xFF737B8C : 0xFF55555F);
        if (noHover) c.fill(no[0], no[1], no[0] + no[2], no[1] + 1, 0xFFFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer, "Cancel", no[0] + no[2] / 2, no[1] + 6, 0xFFFFFFFF);

        c.getMatrices().pop();
    }
}