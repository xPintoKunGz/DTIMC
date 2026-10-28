package bloomery.dtimc.storage;

import bloomery.dtimc.SkinChangerScreen;
import bloomery.dtimc.compat.figura.FiguraBridge;
import bloomery.dtimc.storage.ActiveSkinManager;
import bloomery.dtimc.storage.FiguraAssignments;
import bloomery.dtimc.storage.SkinStorageManager;
import bloomery.dtimc.storage.SkinStorageManager.SkinEntry;
import bloomery.dtimc.util.*;
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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
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
    private int panelH = 250;                     // follows the window height (UiKit.panelHeight)
    private static final int ROW_H = 38;          // stride of a skin row
    private static final int ROW_INNER_H = 34;    // drawn height of a skin row
    private static final int SEARCH_H = 24;       // search row under the list header
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
    private StyledButton equipButton;
    private StyledButton deleteButton;
    private StyledButton removeButton;
    private StyledButton addSkinButton;
    private StyledButton addToFolderButton;

    // 3D preview (rotate = left drag, move = right drag, zoom = wheel)
    private ClientPlayerEntity previewPlayer;
    private Identifier previewSkinId;
    private final PreviewView view = new PreviewView();
    private boolean rotating = false;
    private boolean panning = false;

    // Search
    private String searchText = "";
    private boolean searchFocused = false;
    private boolean searchVisible = false;

    // Figura
    private boolean figuraOn = false;
    private List<FiguraBridge.Avatar> avatarCache = new ArrayList<>();
    private long avatarCacheAt = 0L;
    private FolderPicker picker;
    private float modelSwitchAnim = 0f;

    // Misc
    private long lastRefresh = 0L;
    private String hoverTip = null;
    private boolean overlayOpen = false;
    private ModalOverlay announce;

    // Layout (recomputed by computeLayout)
    private int catW, panelW, startX, listX, rightX, topY;
    private int rbx, rbw, prevX, prevY, prevW, prevH, capY;
    private int equipY, equipH, modelY, modelH, figY, figH, remY, remH, delY, delH;

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
        catW = Math.max(60, Math.min(140, width / 6));
        panelW = Math.max(90, Math.min(280, (width - catW - 32) / 2));
        panelH = UiKit.panelHeight(height);
        int total = catW + panelW * 2 + 16;
        startX = (width - total) / 2;
        listX = startX + catW + 8;
        rightX = listX + panelW + 8;
        topY = Math.max(22, (height - (panelH + 30)) / 2 + 8);
        figuraOn = FiguraBridge.isInstalled();
        layoutRight();
    }

    /** Right panel: preview box + caption, then equip / model switch / Figura card / remove / delete. */
    private void layoutRight() {
        boolean custom = isCustomCategory(currentCategory);
        int gap = 4;
        int[] base = {24, 28, figuraOn ? 34 : 0, custom ? 22 : 0, 24};
        int count = 0, sum = 0;
        for (int b : base) if (b > 0) {
            count++;
            sum += b;
        }
        int gaps = gap * (count - 1);
        int header = 24, caption = 14, pads = 3 + 4 + 4 + 8;

        int previewH = panelH - header - caption - pads - sum - gaps;
        double scale = 1.0;
        if (previewH < 90) {
            previewH = 90;
            int stackAvail = panelH - header - caption - pads - 90 - gaps;
            scale = Math.max(0.7, stackAvail / (double) sum);
        }
        previewH = Math.min(previewH, 190);

        rbx = rightX + 8;
        rbw = panelW - 16;
        prevX = rightX + 6;
        prevW = panelW - 12;
        prevY = topY + header + 3;
        prevH = previewH;
        capY = prevY + prevH + 4;

        int y = capY + caption + 4;
        int[] ys = new int[5], hs = new int[5];
        for (int i = 0; i < 5; i++) {
            if (base[i] == 0) continue;
            hs[i] = Math.max(16, (int) Math.round(base[i] * scale));
            ys[i] = y;
            y += hs[i] + gap;
        }
        equipY = ys[0]; equipH = hs[0];
        modelY = ys[1]; modelH = hs[1];
        figY = ys[2]; figH = hs[2];
        remY = ys[3]; remH = hs[3];
        delY = ys[4]; delH = hs[4];
    }

    @Override
    protected void init() {
        computeLayout();
        refreshAndValidateStorage();
        lastRefresh = System.currentTimeMillis();

        if (openCategoryOnInit != null) {
            if (openCategoryOnInit.equals("All") || categories.contains(openCategoryOnInit)) currentCategory = openCategoryOnInit;
            if (openSkinOnInit != null && findEntryById(openSkinOnInit) != null) {
                selectedId = openSkinOnInit;
                pendingScrollToSelected = true;
            }
            openCategoryOnInit = null;
            openSkinOnInit = null;
            scrollOffset = 0;
        }

        // top-left: back arrow -> main menu (SkinChangerScreen)
        this.addDrawableChild(new LinkButton(8, 8, LinkButton.Icon.BACK, t("common.back"), this::goBack));

        int bx = rightX + 8;
        int bw = panelW - 16;

        equipButton = new StyledButton(bx, topY + 130, bw, 20, Text.literal(t("storage.btn.equip")),
                b -> equipSelectedSkin(), StyledButton.Style.PRIMARY);
        this.addDrawableChild(equipButton);

        removeButton = new StyledButton(bx, topY + 174, bw, 20, Text.literal(t("storage.btn.remove")),
                b -> removeSelectedFromFolder(), StyledButton.Style.WARN);
        this.addDrawableChild(removeButton);

        deleteButton = new StyledButton(bx, topY + 196, bw, 20, Text.literal(t("storage.btn.delete")),
                b -> startDeleteSkin(), StyledButton.Style.DANGER);
        this.addDrawableChild(deleteButton);

        int importW = Math.min(180, panelW * 2 - 24);
        addSkinButton = new StyledButton(listX + (panelW * 2 + 8 - importW) / 2, topY + 124, importW, 22,
                Text.literal(t("storage.btn.import")), b -> startImport(), StyledButton.Style.PRIMARY);
        this.addDrawableChild(addSkinButton);

        // Custom folders only: keep adding skins straight into the folder you are looking at
        addToFolderButton = new StyledButton(listX + 6, topY + panelH - 26, panelW - 12, 20,
                Text.literal(t("storage.btn.add_here")), b -> startImport(), StyledButton.Style.FOLDER);
        this.addDrawableChild(addToFolderButton);
    }

    /** Opens the import menu for the current folder (the menu shows which folder the skin is added to). */
    private void startImport() {
        importTargetCategory = isCustomCategory(currentCategory) ? currentCategory : "All";
        entriesBeforeImport.clear();
        for (SkinEntry existing : SkinStorageManager.getEntries()) {
            entriesBeforeImport.add(existing.id);
        }
        if (this.client != null) this.client.setScreen(new SkinChangerScreen(this, currentCategory));
    }

    public static String getImportTargetCategory() {
        return importTargetCategory;
    }

    private static final Map<String, String> pendingAssignments = new HashMap<>();
    private static String openCategoryOnInit = null;
    private static String openSkinOnInit = null;

    /** After the type screen saved a skin: put it in this folder and open the Storage on it. */
    public static void assignCategoryOnOpen(String skinId, String category) {
        pendingAssignments.put(skinId, category);
        openCategoryOnInit = category;
        openSkinOnInit = skinId;
    }

    /** Names of the custom folders, read straight from the config file. */
    public static List<String> readCustomCategories() {
        List<String> result = new ArrayList<>();
        try {
            Path path = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/myskinmod_storage.json");
            if (!Files.exists(path)) return result;
            JsonObject root = new Gson().fromJson(Files.readString(path), JsonObject.class);
            if (root != null && root.has("customCategories")) {
                for (JsonElement el : root.getAsJsonArray("customCategories")) {
                    String name = el.getAsString();
                    if (!result.contains(name) && isCustomCategory(name)) result.add(name);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void clearImportTargetCategory() {
        importTargetCategory = "All";
        entriesBeforeImport.clear();
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
    private static String t(String key, Object... args) {
        return ModLang.t(key, args);
    }

    /** Display name of a category ("All" / "Favorites" are translated, custom ones are the user's own text). */
    private static String catLabel(String name) {
        if (name == null) return "";
        if (name.equals("All")) return t("storage.cat.all");
        if (name.equals("Favorites")) return t("storage.cat.favorites");
        return name;
    }

    private void toast(ModToast.Type type, String key, Object... args) {
        ModToast.show(type, t("storage.toast.title"), t(key, args));
    }

    private boolean announceActive() {
        if (announce != null && announce.isClosed()) announce = null;
        return announce != null;
    }

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
        if (!searchText.isEmpty()) filtered.removeIf(en -> !matchesSearch(en));

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

                String pending = pendingAssignments.remove(entry.id);
                if (pending != null && (pending.equals("All") || categories.contains(pending))) {
                    skinCategories.put(entry.id, pending);
                    saveCategoryData();
                }

                // A brand new skin is bound to the folder it was imported into.
                if (!skinCategories.containsKey(entry.id)) {
                    boolean newlyImported = !entriesBeforeImport.contains(entry.id);
                    String assignCat = "All";
                    if (newlyImported && isCustomCategory(importTargetCategory) && categories.contains(importTargetCategory)) {
                        assignCat = importTargetCategory;
                    } else if (isCustomCategory(currentCategory) && categories.contains(currentCategory)) {
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

        // Skins deleted from the cloud must not linger in this config (favorites / folders / order).
        Set<String> knownIds = new HashSet<>();
        for (SkinEntry entry : allEntries) knownIds.add(entry.id);
        boolean pruned = favoriteSkins.removeIf(id -> !knownIds.contains(id));
        pruned |= skinCategories.keySet().removeIf(id -> !knownIds.contains(id));
        if (pruned) saveCategoryData();

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
                view.reset();
            }
            createPreviewPlayerForSelected();
        }
    }

    private void createPreviewPlayerForSelected() {
        SkinEntry entry = getSelectedEntry();
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier skinId = entry == null ? null : loadedTextures.get(entry.id);

        if (entry != null && skinId != null) {
            final SkinEntry e = entry;
            this.previewSkinId = skinId;
            this.previewPlayer = PreviewPlayerFactory.create(client, () -> skinId, () -> e.modelType);
            if (this.previewPlayer != null) return;
        }
        this.previewSkinId = null;
        this.previewPlayer = null;
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
                SkinNetworkHandler.sendSkinChunks(bytes, entry.modelType);

                // The Figura avatar linked to this skin (or its folder) is loaded and uploaded together with it.
                FiguraAssignments.Resolved fig = FiguraAssignments.resolve(entry.id, readSkinCategory(entry.id));
                ActiveSkinManager.saveActiveSkin(bytes, entry.modelType, fig.avatarId());
                if (fig.avatarId() != null) FiguraBridge.loadAndUpload(fig.avatarId());

                ModToast.show(ModToast.Type.SUCCESS, ModLang.t("storage.toast.title"),
                        ModLang.t("storage.toast.equipped", entry.name), skinId, img.getWidth(), img.getHeight());
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    private void goBack() {
        if (this.client != null) this.client.setScreen(parent != null ? parent : new SkinChangerScreen());
    }

    @Override
    public void close() {
        goBack();
    }

    /** Takes the selected skin out of its custom folder; it goes back to "All" (nothing is deleted). */
    private void removeSelectedFromFolder() {
        SkinEntry e = getSelectedEntry();
        if (e == null || !isCustomCategory(currentCategory)) return;
        if (!currentCategory.equals(skinCategories.get(e.id))) return;

        int idx = indexOfId(getFilteredEntries(), e.id);
        skinCategories.put(e.id, "All");
        saveCategoryData();

        List<SkinEntry> after = getFilteredEntries();
        selectedId = after.isEmpty() ? null : after.get(Math.max(0, Math.min(idx, after.size() - 1))).id;
        toast(ModToast.Type.SUCCESS, "storage.toast.removed", e.name);
    }

    private static String readSkinCategory(String skinId) {
        try {
            Path path = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/myskinmod_storage.json");
            if (!Files.exists(path)) return "All";
            JsonObject root = new Gson().fromJson(Files.readString(path), JsonObject.class);
            if (root != null && root.has("skinCategories") && root.getAsJsonObject("skinCategories").has(skinId)) {
                return root.getAsJsonObject("skinCategories").get(skinId).getAsString();
            }
        } catch (Exception ignored) { }
        return "All";
    }

    private void setModelType(SkinEntry entry, String type) {
        if (entry.modelType.equals(type)) return;
        SkinStorageManager.updateSkin(entry.id, entry.name, type);
        refreshAndValidateStorage(); // the preview rebuilds automatically (its key changes)
    }

    private boolean pickerActive() {
        if (picker != null && picker.isClosed()) picker = null;
        return picker != null;
    }

    // ---- Figura ----
    private List<FiguraBridge.Avatar> avatars() {
        long now = System.currentTimeMillis();
        if (now - avatarCacheAt > 3000) {
            avatarCache = FiguraBridge.listAvatars();
            avatarCacheAt = now;
        }
        return avatarCache;
    }

    private String avatarName(String id) {
        if (id == null) return t("figura.none");
        for (FiguraBridge.Avatar a : avatars()) if (a.id().equals(id)) return a.name();
        return t("figura.missing", id);
    }

    private void openFiguraPicker(String title, String current, java.util.function.Consumer<String> onChosen) {
        List<FiguraBridge.Avatar> list = FiguraBridge.listAvatars();
        if (list.isEmpty()) {
            ModToast.info(t("figura.toast.title"), t("figura.none_found"));
            return;
        }
        List<FolderPicker.Option> options = new ArrayList<>();
        options.add(new FolderPicker.Option("", t("figura.pick.none"), true, "×"));
        for (FiguraBridge.Avatar a : list) options.add(new FolderPicker.Option(a.id(), a.name(), false, "◆"));
        picker = new FolderPicker(title, t("figura.pick.subtitle"), options, current == null ? "" : current,
                id -> onChosen.accept(id.isEmpty() ? null : id), () -> { });
    }

    private void openFiguraPickerForSkin() {
        SkinEntry e = getSelectedEntry();
        if (e == null) return;
        final String skinId = e.id;
        openFiguraPicker(t("figura.pick.skin.title"), FiguraAssignments.forSkin(skinId), id -> {
            FiguraAssignments.setSkin(skinId, id);
            ModToast.success(t("figura.toast.title"), id == null ? t("figura.toast.cleared")
                    : t("figura.toast.assigned", FiguraBridge.displayName(id)));
        });
    }

    private void openFiguraPickerForFolder() {
        if (!isCustomCategory(currentCategory)) return;
        final String folder = currentCategory;
        openFiguraPicker(t("figura.pick.folder.title", folder), FiguraAssignments.forFolder(folder), id -> {
            FiguraAssignments.setFolder(folder, id);
            ModToast.success(t("figura.toast.title"), id == null ? t("figura.toast.cleared")
                    : t("figura.toast.assigned", FiguraBridge.displayName(id)));
        });
    }

    // ---- search ----
    private boolean matchesSearch(SkinEntry e) {
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        if (e.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (e.modelType.toLowerCase(Locale.ROOT).contains(q)) return true;
        int[] sz = textureSizes.get(e.id);
        if (sz != null && (sz[0] + "x" + sz[1]).contains(q)) return true;
        String cat = skinCategories.getOrDefault(e.id, "All");
        return isCustomCategory(cat) && cat.toLowerCase(Locale.ROOT).contains(q);
    }

    private int[] searchRect() {
        return new int[]{listX + 6, topY + 27, panelW - 12, 18};
    }

    private boolean canReorder() {
        return searchText.isEmpty() && !currentCategory.equals("Favorites");
    }

    private void drawSearchBox(DrawContext c, int mx, int my, boolean cursorOn) {
        int[] r = searchRect();
        boolean hover = in(r, mx, my);
        c.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF101018);
        int border = searchFocused ? ACCENT : hover ? 0x77FFFFFF : 0x44FFFFFF;
        c.fill(r[0], r[1], r[0] + r[2], r[1] + 1, border);
        c.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], border);
        c.fill(r[0], r[1], r[0] + 1, r[1] + r[3], border);
        c.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], border);

        // magnifier
        int mxp = r[0] + 5, myp = r[1] + 4, mc = searchFocused ? ACCENT : 0xFF8A8FA3;
        c.fill(mxp + 1, myp, mxp + 5, myp + 1, mc);
        c.fill(mxp + 1, myp + 5, mxp + 5, myp + 6, mc);
        c.fill(mxp, myp + 1, mxp + 1, myp + 5, mc);
        c.fill(mxp + 5, myp + 1, mxp + 6, myp + 5, mc);
        c.fill(mxp + 5, myp + 5, mxp + 7, myp + 7, mc);
        c.fill(mxp + 7, myp + 7, mxp + 8, myp + 8, mc);

        int tx = r[0] + 17;
        int clearW = searchText.isEmpty() ? 0 : 14;
        int maxW = r[2] - 17 - 4 - clearW;
        if (searchText.isEmpty() && !searchFocused) {
            c.drawText(textRenderer, fit(t("storage.search.placeholder"), maxW), tx, r[1] + 5, 0xFF6C7186, false);
        } else {
            String shown = textRenderer.trimToWidth(searchText, Math.max(4, maxW), true);
            c.drawText(textRenderer, shown, tx, r[1] + 5, 0xFFFFFFFF, false);
            if (searchFocused && cursorOn) {
                int cx = tx + textRenderer.getWidth(shown);
                c.fill(cx + 1, r[1] + 3, cx + 2, r[1] + 14, 0xFFFFFFFF);
            }
        }
        if (clearW > 0) {
            int cx = r[0] + r[2] - 14;
            boolean cHover = in(mx, my, cx, r[1] + 2, 12, r[3] - 4);
            c.drawCenteredTextWithShadow(textRenderer, "×", cx + 6, r[1] + 5, cHover ? 0xFFFFFFFF : 0xFFAAAAAA);
            if (cHover) hoverTip = t("storage.search.clear");
        }
    }

    private void drawLock(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y, x + 5, y + 1, color);
        c.fill(x + 1, y + 1, x + 2, y + 3, color);
        c.fill(x + 4, y + 1, x + 5, y + 3, color);
        c.fill(x, y + 3, x + 6, y + 8, color);
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
        FiguraAssignments.removeSkin(id);
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
        toast(ModToast.Type.SUCCESS, "storage.toast.deleted", name);
    }

    private void executeDeleteCategory() {
        String catName = pendingCategory;
        if (catName == null || !isCustomCategory(catName)) return;
        skinCategories.values().removeIf(c -> c.equals(catName));
        categories.remove(catName);
        FiguraAssignments.removeFolder(catName);
        if (catName.equals(importTargetCategory)) importTargetCategory = "All";
        currentCategory = "All";
        scrollOffset = 0;
        saveCategoryData();
        refreshAndValidateStorage();
        toast(ModToast.Type.SUCCESS, "storage.toast.deleted_folder", catName);
    }

    private void executeMove() {
        SkinEntry e = findEntryById(pendingSkinId);
        if (e == null || pendingCategory == null) return;
        skinCategories.put(e.id, pendingCategory);
        saveCategoryData();
        currentCategory = pendingCategory;
        selectedId = e.id;
        pendingScrollToSelected = true;
        toast(ModToast.Type.SUCCESS, "storage.toast.moved", catLabel(pendingCategory));
    }

    private void handleDropOnCategory(int catIdx) {
        SkinEntry e = findEntryById(draggedEntryId);
        if (e == null || catIdx < 0 || catIdx >= categories.size()) return;
        String target = categories.get(catIdx);

        if (target.equals("Favorites")) {
            if (favoriteSkins.add(e.id)) {
                saveCategoryData();
                toast(ModToast.Type.SUCCESS, "storage.toast.fav_added", e.name);
            } else {
                toast(ModToast.Type.INFO, "storage.toast.fav_already");
            }
            return;
        }
        String current = skinCategories.getOrDefault(e.id, "All");
        if (current.equals(target)) {
            toast(ModToast.Type.INFO, "storage.toast.already_in", catLabel(target));
            return;
        }
        pendingSkinId = e.id;
        pendingCategory = target;
        modal = Modal.MOVE;
    }

    // ---- reorder ----
    /** In "All" the favorites are pinned at the top: nothing can be dropped above (or onto) them. */
    private int firstMovableIndex(List<SkinEntry> filtered) {
        if (!currentCategory.equals("All")) return 0;
        int n = 0;
        for (SkinEntry e : filtered) {
            if (favoriteSkins.contains(e.id)) n++;
            else break;
        }
        return n;
    }

    private int rawDropIndex(List<SkinEntry> filtered, double my) {
        int idx = (int) Math.floor((my - rowsOrigin() + scrollOffset) / ROW_H);
        return Math.max(0, Math.min(filtered.size() - 1, idx));
    }

    private int dropIndex(List<SkinEntry> filtered, double my) {
        return Math.max(firstMovableIndex(filtered), rawDropIndex(filtered, my));
    }

    private void performReorder(double my) {
        if (!canReorder() || favoriteSkins.contains(draggedEntryId)) return; // favorites never move
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
                    toast(ModToast.Type.WARNING, "storage.toast.name_taken");
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
                    toast(ModToast.Type.WARNING, "storage.toast.name_taken");
                    return;
                }
                int idx = categories.indexOf(oldCat);
                if (idx >= 2) {
                    categories.set(idx, text);
                    FiguraAssignments.renameFolder(oldCat, text);
                    if (oldCat.equals(importTargetCategory)) importTargetCategory = text;
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
                    toast(ModToast.Type.SUCCESS, "storage.toast.renamed", text);
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

    private int toolbarCount() {
        return figuraOn ? 4 : 3;
    }

    /** Toolbar tile i (0 new, 1 rename, 2 delete, 3 Figura avatar of the folder) at the bottom of the category panel. */
    private int[] toolbarTile(int i) {
        int n = toolbarCount();
        int gap = 2;
        int tw = (catW - 8 - gap * (n - 1)) / n;
        int total = tw * n + gap * (n - 1);
        int x0 = startX + (catW - total) / 2;
        return new int[]{x0 + i * (tw + gap), topY + panelH - 26, tw, 20};
    }

    private int[] reorderTile() {
        return new int[]{listX + panelW - 27, topY + 5, 20, 14};
    }

    private int customTop() { return topY + 82; }

    private int customBottom() { return topY + panelH - 30; }

    private int listTop() { return topY + 26 + SEARCH_H; }

    /** y of the first skin row (rows are ROW_H apart). */
    private int rowsOrigin() { return listTop() + 2; }

    private int listBottom() { return topY + panelH - (isCustomCategory(currentCategory) ? 30 : 4); }

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

        if (pickerActive()) {
            picker.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (announceActive()) {
            announce.mouseClicked(mouseX, mouseY, button);
            return true;
        }

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
        if (button == 1) { // right mouse button: move the model inside the preview
            if (getSelectedEntry() != null && in(mouseX, mouseY, prevX, prevY, prevW, prevH)) {
                panning = true;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Clicking anywhere while editing commits the edit first.
        String committedSkinId = editMode == EditMode.SKIN ? editTargetId : null;
        if (editMode != EditMode.NONE) applyEdit();

        List<SkinEntry> filtered = getFilteredEntries();

        // Search box
        if (searchVisible) {
            int[] sr = searchRect();
            if (in(mouseX, mouseY, sr[0], sr[1], sr[2], sr[3])) {
                if (!searchText.isEmpty() && mouseX >= sr[0] + sr[2] - 16) {
                    searchText = "";
                    scrollOffset = 0;
                }
                searchFocused = true;
                return true;
            }
        }
        searchFocused = false;

        // Category toolbar
        boolean custom = isCustomCategory(currentCategory);
        for (int i = 0; i < toolbarCount(); i++) {
            if (in(toolbarTile(i), mouseX, mouseY)) {
                if (i == 0) startCreateCategory();
                else if (i == 1 && custom) startRenameCategory();
                else if (i == 2 && custom) startDeleteCategory();
                else if (i == 3 && custom) openFiguraPickerForFolder();
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
                int idx = (int) Math.floor((mouseY - rowsOrigin() + scrollOffset) / ROW_H);
                if (idx >= 0 && idx < filtered.size()) {
                    int y = rowsOrigin() + idx * ROW_H - (int) scrollOffset;
                    if (mouseY < y + ROW_INNER_H) {
                        SkinEntry e = filtered.get(idx);
                        int pencilX = x2 - 31;
                        int heartX = x2 - 16;
                        if (in(mouseX, mouseY, pencilX, y + 10, 14, 14)) {
                            if (e.id.equals(committedSkinId)) return true; // the ✓ button just saved it
                            selectedId = e.id;
                            beginSkinEdit(e);
                            return true;
                        }
                        if (in(mouseX, mouseY, heartX, y + 10, 14, 14)) {
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

            SkinEntry sel = getSelectedEntry();
            if (sel != null) {
                if (view.captionClicked(mouseX, mouseY)) return true;
                if (in(mouseX, mouseY, rbx, modelY, rbw, modelH)) {
                    setModelType(sel, mouseX < rbx + rbw / 2.0 ? "default" : "slim");
                    return true;
                }
                if (figuraOn && in(mouseX, mouseY, rbx, figY, rbw, figH)) {
                    openFiguraPickerForSkin();
                    return true;
                }
                if (in(mouseX, mouseY, prevX, prevY, prevW, prevH)) { // left drag = rotate
                    rotating = true;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (modal != Modal.NONE || announceActive() || pickerActive()) return true;
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
                view.dragRotate(deltaX, deltaY);
                return true;
            }
        }
        if (button == 1 && panning) {
            view.dragPan(deltaX, deltaY, prevW, prevH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) panning = false;
        if (button == 0) {
            computeLayout();
            scrollbarDragging = false;
            rotating = false;
            if (draggedEntryId != null) {
                if (dragStarted && modal == Modal.NONE) {
                    int ci = categoryAt(mouseX, mouseY);
                    if (ci >= 0) {
                        handleDropOnCategory(ci);
                    } else if (reorderMode && canReorder() && !favoriteSkins.contains(draggedEntryId) && inListViewport(mouseX, mouseY)) {
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
        if (pickerActive()) return picker.mouseScrolled(amount);
        if (modal != Modal.NONE || announceActive() || pickerActive()) return true;
        computeLayout();
        if (getSelectedEntry() != null && in(mouseX, mouseY, prevX, prevY, prevW, prevH)) {
            view.scroll(amount); // wheel over the preview = zoom
            return true;
        }
        if (mouseX >= startX && mouseX <= startX + catW && mouseY >= customTop() && mouseY < customBottom()) {
            categoryScrollOffset = Math.max(0, categoryScrollOffset - amount * 18);
            return true;
        }
        if (mouseX >= listX && mouseX < listX + panelW && mouseY >= topY && mouseY < topY + panelH) {
            scrollOffset = Math.max(0, scrollOffset - amount * 18);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount); // everywhere else: no scrolling
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (modal != Modal.NONE || announceActive() || pickerActive()) return true;
        if (searchFocused) {
            if (SharedConstants.isValidChar(chr) && searchText.length() < 32) {
                searchText += chr;
                scrollOffset = 0;
            }
            return true;
        }
        if (editMode != EditMode.NONE) {
            if (SharedConstants.isValidChar(chr) && editBuffer.length() < MAX_NAME_LEN) editBuffer += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerActive()) return picker.keyPressed(keyCode);
        if (announceActive()) return announce.keyPressed(keyCode);
        if (modal != Modal.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) confirmModal();
            else if (keyCode == GLFW.GLFW_KEY_ESCAPE) closeModal();
            return true;
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchText = "";
                searchFocused = false;
                scrollOffset = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    scrollOffset = 0;
                }
                return true;
            }
            if (Screen.isPaste(keyCode) && this.client != null) {
                for (char ch : this.client.keyboard.getClipboard().toCharArray()) {
                    if (searchText.length() >= 32) break;
                    if (SharedConstants.isValidChar(ch)) searchText += ch;
                }
                scrollOffset = 0;
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_UP && keyCode != GLFW.GLFW_KEY_DOWN) return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F && searchVisible) {
            searchFocused = true;
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

        boolean modalOpen = modal != Modal.NONE || announceActive() || pickerActive();
        overlayOpen = modalOpen;
        int mx = modalOpen ? -9999 : mouseX;
        int my = modalOpen ? -9999 : mouseY;
        hoverTip = null;
        boolean cursorOn = (now / 450) % 2 == 0;

        context.drawCenteredTextWithShadow(textRenderer, t("storage.title"), width / 2,
                Math.max(6, topY - 16), 0xFFFFFFFF);

        drawCategoryPanel(context, mx, my, cursorOn);

        boolean empty = filtered.isEmpty();
        boolean noResults = empty && !searchText.isEmpty();
        boolean inCustom = isCustomCategory(currentCategory);
        boolean showRight = !empty;
        searchVisible = !empty || noResults;

        equipButton.visible = showRight;
        deleteButton.visible = showRight;
        removeButton.visible = showRight && inCustom;
        addToFolderButton.visible = showRight && inCustom;
        equipButton.setRect(rbx, equipY, rbw, equipH);
        deleteButton.setRect(rbx, delY, rbw, delH);
        removeButton.setRect(rbx, remY, rbw, remH);
        addToFolderButton.setRect(listX + 6, topY + panelH - 26, panelW - 12, 20);
        equipButton.setMessage(Text.literal(t("storage.btn.equip")));
        deleteButton.setMessage(Text.literal(t("storage.btn.delete")));
        removeButton.setMessage(Text.literal(t("storage.btn.remove")));
        addToFolderButton.setMessage(Text.literal(t("storage.btn.add_here")));

        // Favorites: only the heart adds skins. No import button while a search simply has no results.
        addSkinButton.visible = empty && !noResults && !currentCategory.equals("Favorites");
        int importW = Math.min(180, panelW * 2 - 24);
        addSkinButton.setRect(listX + (panelW * 2 + 8 - importW) / 2, topY + panelH / 2 + 11, importW, 22);

        if (empty && !noResults) {
            drawEmptyState(context);
        } else {
            drawListPanel(context, filtered, mx, my, cursorOn);
            drawRightPanel(context, mx, my);
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

        if (modal != Modal.NONE) drawModal(context, mouseX, mouseY);
        else if (announceActive()) announce.render(context, textRenderer, width, height, mouseX, mouseY);
        else if (pickerActive()) picker.render(context, textRenderer, width, height, mouseX, mouseY);
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
        drawPanel(c, startX, topY, catW, panelH);
        c.drawText(textRenderer, fit(t("storage.categories"), catW - 12), startX + 8, topY + 9, GOLD, false);

        boolean dragging = dragStarted && draggedEntryId != null;
        boolean showCount = catW >= 76;

        // Main (pinned) categories
        for (int i = 0; i < 2; i++) {
            String name = categories.get(i);
            int y = topY + 24 + i * CAT_ROW_H;
            boolean hover = in(mx, my, startX + 4, y, catW - 8, 20);
            drawCategoryRow(c, catLabel(name), startX + 4, y, catW - 8, i, name.equals(currentCategory),
                    hover, dragging && hover, showCount ? countFor(name) : -1, false, cursorOn);
        }

        // Divider
        int divY = topY + 69;
        String customLabel = t("storage.custom");
        c.drawText(textRenderer, customLabel, startX + 8, divY, 0xFF9A8CCB, false);
        int lw = textRenderer.getWidth(customLabel);
        c.fill(startX + 8 + lw + 4, divY + 4, startX + catW - 6, divY + 5, 0x44FFFFFF);

        // Custom categories (scrollable)
        int cTop = customTop(), cBottom = customBottom();
        int customCount = categories.size() - 2;
        int shown = customCount + (editMode == EditMode.CAT_NEW ? 1 : 0);
        if (shown == 0) {
            c.drawCenteredTextWithShadow(textRenderer, t("storage.no_folders"), startX + catW / 2, cTop + 30, 0xFF777788);
            c.drawCenteredTextWithShadow(textRenderer, t("storage.press_plus"), startX + catW / 2, cTop + 44, 0xFF555566);
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
        String[] glyphs = {"+", "✎", "×", "F"};
        int[] colors = {GREEN, 0xFFFFD27A, 0xFFFF7B7B, VIOLET};
        String[] tips = {t("storage.tip.new_folder"), t("storage.tip.rename_folder"), t("storage.tip.delete_folder"), t("figura.tip.folder")};
        for (int i = 0; i < toolbarCount(); i++) {
            int[] r = toolbarTile(i);
            boolean enabled = i == 0 || custom;
            boolean hover = in(r, mx, my);
            drawTile(c, r[0], r[1], r[2], r[3], glyphs[i], colors[i], enabled, false, hover);
            if (hover) hoverTip = enabled ? tips[i] : t("storage.tip.main_locked");
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
        drawPanel(c, listX, topY, w, panelH);
        c.drawText(textRenderer, fit(t("storage.folder", catLabel(currentCategory)), w - 20), listX + 10, topY + 9, GOLD, false);

        int cx = listX + w / 2;
        boolean fav = currentCategory.equals("Favorites");
        String l1 = validEntries.isEmpty() ? t("storage.empty.storage")
                : fav ? t("storage.empty.fav") : t("storage.empty.folder");
        String l2 = fav ? t("storage.empty.fav_hint") : t("storage.empty.import_hint");
        int base = panelH / 2 - (fav ? 29 : 47);
        c.drawCenteredTextWithShadow(textRenderer, fav ? "♥" : "✦", cx, topY + base, fav ? PINK : ACCENT);
        c.drawCenteredTextWithShadow(textRenderer, fit(l1, w - 20), cx, topY + base + 20, 0xFFCCCCCC);
        c.drawCenteredTextWithShadow(textRenderer, fit(l2, w - 20), cx, topY + base + 34, 0xFF888888);
    }

    // ---------------- List panel ----------------
    private void drawListPanel(DrawContext c, List<SkinEntry> filtered, int mx, int my, boolean cursorOn) {
        drawPanel(c, listX, topY, panelW, panelH);

        // Header: folder name, count, reorder toggle
        int[] rt = reorderTile();
        boolean canReorder = canReorder();
        boolean rHover = in(rt, mx, my);
        String countStr = String.valueOf(filtered.size());
        int countX = rt[0] - 5 - textRenderer.getWidth(countStr);
        c.drawText(textRenderer, countStr, countX, topY + 9, 0xFF8A8FA3, false);
        c.drawText(textRenderer, fit(t("storage.folder", catLabel(currentCategory)), countX - 6 - (listX + 10)),
                listX + 10, topY + 9, GOLD, false);
        drawTile(c, rt[0], rt[1], rt[2], rt[3], "↕", reorderMode && canReorder ? 0xFFFFFFFF : 0xFFB8C0D4,
                canReorder, reorderMode && canReorder, rHover);
        if (rHover) {
            hoverTip = !canReorder ? (searchText.isEmpty() ? t("storage.tip.locked") : t("storage.tip.reorder_search"))
                    : reorderMode ? t("storage.reorder_on") : t("storage.reorder_off");
        }

        drawSearchBox(c, mx, my, cursorOn);

        int vpTop = listTop(), vpBottom = listBottom();
        int x1 = listX + 3;
        int x2 = listX + panelW - 9;
        boolean showIdx = panelW >= 140;
        boolean reorderOn = reorderMode && canReorder;
        boolean reorderDrag = reorderOn && dragStarted && draggedEntryId != null && !favoriteSkins.contains(draggedEntryId);
        boolean dragging = dragStarted && draggedEntryId != null;
        int minIdx = firstMovableIndex(filtered);

        if (filtered.isEmpty()) {
            c.drawCenteredTextWithShadow(textRenderer, fit(t("storage.empty.no_results"), panelW - 16),
                    listX + panelW / 2, vpTop + 30, 0xFFCCCCCC);
            c.drawCenteredTextWithShadow(textRenderer, fit(t("storage.empty.no_results_hint"), panelW - 16),
                    listX + panelW / 2, vpTop + 44, 0xFF888888);
        }

        c.enableScissor(listX + 1, vpTop, listX + panelW - 1, vpBottom);
        for (int i = 0; i < filtered.size(); i++) {
            SkinEntry e = filtered.get(i);
            int y = rowsOrigin() + i * ROW_H - (int) scrollOffset;
            if (y + ROW_INNER_H < vpTop || y > vpBottom) continue;

            boolean selected = e.id.equals(selectedId);
            boolean beingDragged = dragStarted && e.id.equals(draggedEntryId);
            boolean hover = !dragging && mx >= x1 && mx < x2
                    && my >= Math.max(y, vpTop) && my < Math.min(y + ROW_INNER_H, vpBottom);
            boolean editingThis = editMode == EditMode.SKIN && e.id.equals(editTargetId);
            boolean fav = favoriteSkins.contains(e.id);

            int headX = x1 + (showIdx ? 28 : 8);
            int nameX = headX + 26 + 7;
            int pencilX = x2 - 31;
            int heartX = x2 - 16;
            int nameMaxW = Math.max(10, pencilX - 3 - nameX);

            // Is the name cut off? Hold SHIFT over it: full-name tooltip + the row lights up gold
            boolean truncated = textRenderer.getWidth(e.name) > nameMaxW;
            boolean shiftFocus = hover && truncated && !editingThis && Screen.hasShiftDown();

            int bg = editingThis ? 0xAA3A3320 : selected ? 0x904078A8 : hover ? 0x45FFFFFF : 0x30FFFFFF;
            if (shiftFocus) bg = 0x77FFC857;
            if (beingDragged) bg = 0x18FFFFFF;
            c.fill(x1, y, x2, y + ROW_INNER_H, bg);
            if (shiftFocus) {
                c.fill(x1, y, x2, y + 1, 0xFFFFC857);
                c.fill(x1, y + ROW_INNER_H - 1, x2, y + ROW_INNER_H, 0xFFFFC857);
                c.fill(x1, y, x1 + 1, y + ROW_INNER_H, 0xFFFFC857);
                c.fill(x2 - 1, y, x2, y + ROW_INNER_H, 0xFFFFC857);
            }
            if (hover && truncated && !editingThis) hoverTip = Screen.hasShiftDown() ? e.name : t("storage.tip.shift_full");
            if (hover && reorderOn && fav) hoverTip = t("storage.tip.locked");
            if (selected) c.fill(x1, y, x1 + 3, y + ROW_INNER_H, ACCENT);
            else if (fav) c.fill(x1, y, x1 + 2, y + ROW_INNER_H, 0x99FF7B9C);

            if (showIdx) {
                if (reorderOn && fav) {
                    drawLock(c, x1 + 9, y + 13, GOLD); // favorites are pinned
                } else {
                    c.drawText(textRenderer, reorderOn ? "≡" : "#" + (i + 1), x1 + 7, y + 13, reorderOn ? GREEN : GOLD, true);
                }
            }
            drawHead(c, e.id, headX, y + 4, 26);

            if (editingThis) {
                String shown = textRenderer.trimToWidth(editBuffer, nameMaxW - 3, true);
                c.drawText(textRenderer, shown, nameX, y + 6, 0xFFFFE9A6, false);
                int cx = nameX + textRenderer.getWidth(shown);
                if (cursorOn) c.fill(cx + 1, y + 5, cx + 2, y + 15, 0xFFFFFFFF);
                c.fill(nameX - 1, y + 17, nameX + nameMaxW, y + 18, GOLD);
                c.drawText(textRenderer, fit(t("storage.edit_hint"), nameMaxW), nameX, y + 22, 0xFF8FD9B0, false);
            } else {
                c.drawText(textRenderer, fit(e.name, nameMaxW), nameX, y + 7, 0xFFFFFFFF, false);

                // model + resolution (+ folder)
                String typeStr = e.modelType.toUpperCase();
                c.drawText(textRenderer, fit(typeStr, nameMaxW), nameX, y + 21, 0xFFAAAAAA, false);
                int off = textRenderer.getWidth(typeStr) + 6;
                int[] sz = textureSizes.get(e.id);
                if (sz != null && nameMaxW - off > 24) {
                    String res = sz[0] + "x" + sz[1];
                    c.drawText(textRenderer, fit(res, nameMaxW - off), nameX + off, y + 21, 0xFF8FC4E8, false);
                    off += textRenderer.getWidth(res) + 6;
                }
                String skinCat = skinCategories.getOrDefault(e.id, "All");
                if (isCustomCategory(skinCat) && nameMaxW - off > 14) {
                    c.drawText(textRenderer, fit("▣ " + skinCat, nameMaxW - off), nameX + off, y + 21, VIOLET, false);
                }
            }

            // Rename ✎ / save ✓ (part of the row, so it scrolls and clips with it)
            boolean pHover = !dragging && in(mx, my, pencilX, y + 10, 14, 14) && my >= vpTop && my < vpBottom;
            if (pHover) c.fill(pencilX, y + 10, pencilX + 14, y + 24, 0x40FFFFFF);
            int pColor = editingThis ? GREEN : pHover ? 0xFFFFFFFF : selected ? 0xFFFFD27A : 0xFFB0B4C4;
            c.drawCenteredTextWithShadow(textRenderer, editingThis ? "✓" : "✎", pencilX + 7, y + 13, pColor);
            if (pHover) hoverTip = editingThis ? t("storage.tip.save_name") : t("storage.tip.rename");

            // Favorite heart
            boolean hHover = !dragging && in(mx, my, heartX, y + 10, 14, 14) && my >= vpTop && my < vpBottom;
            if (hHover) c.fill(heartX, y + 10, heartX + 14, y + 24, 0x40FFFFFF);
            int hColor = fav ? 0xFFFF5555 : hHover ? 0xFFFFFFFF : 0xFFAAAAAA;
            c.drawCenteredTextWithShadow(textRenderer, fav ? "♥" : "♡", heartX + 7, y + 13, hColor);
            if (hHover) hoverTip = fav ? t("storage.tip.fav_remove") : t("storage.tip.fav_add");
        }

        // Reorder insertion marker (red when the drop would land above the pinned favorites)
        if (reorderDrag && inListViewport(mx, my)) {
            int oldIdx = indexOfId(filtered, draggedEntryId);
            int raw = rawDropIndex(filtered, my);
            int dropIdx = Math.max(minIdx, raw);
            if (oldIdx >= 0 && dropIdx != oldIdx) {
                int ly = rowsOrigin() + dropIdx * ROW_H - (int) scrollOffset + (dropIdx > oldIdx ? ROW_INNER_H : -3);
                c.fill(x1, ly, x2, ly + 2, raw < minIdx ? 0xFFFF5555 : GREEN);
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
    private void drawRightPanel(DrawContext c, int mx, int my) {
        drawPanel(c, rightX, topY, panelW, panelH);
        SkinEntry e = getSelectedEntry();
        if (e == null) return;

        c.drawCenteredTextWithShadow(textRenderer, fit("✦ " + e.name, panelW - 16),
                rightX + panelW / 2, topY + 9, GOLD);

        // 3D preview: left drag rotates, right drag moves, wheel zooms (zoom % shown inside the box)
        view.render(c, textRenderer, previewPlayer, prevX, prevY, prevW, prevH);
        if (previewPlayer == null) {
            c.drawCenteredTextWithShadow(textRenderer, t("storage.preview_unavailable"), rightX + panelW / 2,
                    prevY + prevH / 2 - 4, 0xFF666677);
        }
        view.drawCaption(c, textRenderer, prevX, capY, prevW, mx, my);

        drawModelSwitch(c, mx, my, e);
        if (figuraOn) drawFiguraCard(c, mx, my, e);
    }

    /** Sliding two-way switch with a little pixel character for each arm style. */
    private void drawModelSwitch(DrawContext c, int mx, int my, SkinEntry e) {
        boolean slim = e.modelType.equals("slim");
        modelSwitchAnim += ((slim ? 1f : 0f) - modelSwitchAnim) * 0.3f;

        int x = rbx, y = modelY, w = rbw, h = modelH;
        int segW = (w - 4) / 2;
        c.fill(x, y, x + w, y + h, 0xFF1A1C26);
        c.fill(x, y, x + w, y + 1, 0x44FFFFFF);

        int knobX = x + 2 + Math.round(modelSwitchAnim * segW);
        c.fill(knobX, y + 2, knobX + segW, y + h - 2, 0xFF1B5E7A);
        c.fill(knobX, y + 2, knobX + segW, y + 3, ACCENT);
        c.fill(knobX, y + h - 4, knobX + segW, y + h - 2, ACCENT);

        for (int i = 0; i < 2; i++) {
            int segX = x + 2 + i * segW;
            boolean active = (i == 1) == slim;
            boolean hover = in(mx, my, segX, y, segW, h);
            int fh = 17;
            int fx = segX + 7, fy = y + (h - fh) / 2 - 1;
            int skin = 0xFFE8B98F, shirt = active ? 0xFF3AC0D0 : 0xFF2E7F8A, legs = active ? 0xFF4B4BC8 : 0xFF38389A;
            boolean slimFig = i == 1;
            c.fill(fx + 3, fy, fx + 9, fy + 5, skin);                 // head
            c.fill(fx + 3, fy, fx + 9, fy + 2, 0xFF5A3A22);           // hair
            c.fill(fx + 3, fy + 5, fx + 9, fy + 11, shirt);           // body
            int armW = slimFig ? 2 : 3;
            c.fill(fx + 3 - armW, fy + 5, fx + 3, fy + 11, skin);     // arms
            c.fill(fx + 9, fy + 5, fx + 9 + armW, fy + 11, skin);
            c.fill(fx + 3, fy + 11, fx + 6, fy + 17, legs);           // legs
            c.fill(fx + 6, fy + 11, fx + 9, fy + 17, legs);

            String label = i == 0 ? t("storage.model.classic") : t("storage.model.slim");
            Text txt = active ? Text.literal(fit(label, segW - 30)).formatted(Formatting.BOLD) : Text.literal(fit(label, segW - 30));
            c.drawText(textRenderer, txt, fx + 17, y + (h - 8) / 2, active ? 0xFFFFFFFF : hover ? 0xFFD0D4E0 : 0xFF7C8196, false);
            if (hover) hoverTip = i == 0 ? t("storage.tip.model_classic") : t("storage.tip.model_slim");
        }
    }

    private void drawFiguraCard(DrawContext c, int mx, int my, SkinEntry e) {
        int x = rbx, y = figY, w = rbw, h = figH;
        FiguraAssignments.Resolved r = FiguraAssignments.resolve(e.id, skinCategories.getOrDefault(e.id, "All"));
        boolean hover = in(mx, my, x, y, w, h);
        c.fill(x, y, x + w, y + h, hover ? 0xFF2A2740 : 0xFF1E1C2C);
        c.fill(x, y, x + 3, y + h, VIOLET);
        c.fill(x, y, x + w, y + 1, hover ? VIOLET : 0x44FFFFFF);

        // little avatar glyph
        int ix = x + 10, iy = y + (h - 12) / 2;
        c.fill(ix + 3, iy, ix + 9, iy + 5, VIOLET);
        c.fill(ix, iy + 7, ix + 12, iy + 12, VIOLET);

        int tx = x + 29;
        int top = y + Math.max(2, (h - 19) / 2);
        c.drawText(textRenderer, "Figura", tx, top, 0xFF9A8CCB, false);
        String src = switch (r.source()) {
            case SKIN -> t("figura.src.skin");
            case FOLDER -> t("figura.src.folder", r.folder());
            default -> null;
        };
        if (src != null) {
            String fitted = fit(src, w - 34 - textRenderer.getWidth("Figura") - 6);
            c.drawText(textRenderer, fitted, x + w - 6 - textRenderer.getWidth(fitted), top, GOLD, false);
        }
        String name = avatarName(r.avatarId());
        c.drawText(textRenderer, fit(name, w - 36), tx, top + 10, r.avatarId() == null ? 0xFF8A8FA3 : 0xFFFFFFFF, false);
        if (hover) hoverTip = t("figura.tip.card");
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
                title = t("storage.modal.delete_cat.title");
                line = t("storage.modal.delete_cat.line", pendingCategory);
                confirmLabel = t("common.delete");
                accent = 0xFFFF5555;
                confirmBase = 0xFFB83232;
                confirmHover = 0xFFE34B55;
            }
            case MOVE -> {
                SkinEntry moving = findEntryById(pendingSkinId);
                title = t("storage.modal.move.title");
                line = (moving == null ? t("common.unknown_skin") : moving.name) + " → " + catLabel(pendingCategory);
                confirmLabel = t("storage.modal.move.confirm");
                accent = ACCENT;
                confirmBase = 0xFF18785A;
                confirmHover = 0xFF269B75;
            }
            default -> {
                SkinEntry e = findEntryById(pendingSkinId);
                title = t("storage.modal.delete_skin.title");
                line = t("storage.modal.delete_skin.line", e == null ? "" : e.name);
                confirmLabel = t("common.delete");
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
        c.drawCenteredTextWithShadow(textRenderer, t("common.cancel"), no[0] + no[2] / 2, no[1] + 6, 0xFFFFFFFF);

        c.getMatrices().pop();
    }
}
