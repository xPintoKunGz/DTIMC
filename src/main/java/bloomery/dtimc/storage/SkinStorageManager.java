package bloomery.dtimc.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Skin_storage.json layout (v2):
 * { "version": 2, "nextSequence": N, "skins": [ ... ] }
 * The old layout (a bare array) is still read and migrated automatically.
 *
 * Every skin has a never-reused "sequence" number and a creation timestamp, so two identical skins can always be
 * told apart: lower sequence = saved first. Identical skins share "contentHash"; "duplicateIndex" is 1 for the
 * first one, 2 for the next, ... and "duplicateOfId" points at the first one.
 */
public class SkinStorageManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/myskinmod/skins");
    private static final Path JSON_FILE = STORAGE_DIR.resolve("skin_storage.json");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static class SkinEntry {
        public String id;
        public String name;
        public String modelType; // "default" or "slim"
        public String createdDate;
        public String fileName;

        // --- v2 fields (0 / null for entries from older versions until migrated) ---
        public long sequence;
        public long createdAtMillis;
        public String createdAt;
        public long updatedAtMillis;
        public String contentHash;
        public int duplicateIndex = 1;
        public String duplicateOfId;

        public SkinEntry(String id, String name, String modelType, String createdDate, String fileName) {
            this.id = id;
            this.name = name;
            this.modelType = modelType;
            this.createdDate = createdDate;
            this.fileName = fileName;
        }
    }

    private static List<SkinEntry> entries = new ArrayList<>();
    private static long nextSequence = 1;

    // ------------------------------------------------------------------ lifecycle
    public static void init() {
        try {
            if (!Files.exists(STORAGE_DIR)) Files.createDirectories(STORAGE_DIR);
            loadStorage();
            boolean changed = migrateAndHash();
            // Entries whose PNG vanished from disk are removed from the config for good.
            changed |= purgeMissingFiles(false);
            recomputeDuplicates();
            if (changed) saveStorage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<SkinEntry> getEntries() {
        return entries;
    }

    public static SkinEntry getEntryById(String id) {
        for (SkinEntry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    public static void loadStorage() {
        if (!Files.exists(JSON_FILE)) {
            entries = new ArrayList<>();
            return;
        }
        try (Reader reader = Files.newBufferedReader(JSON_FILE, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            List<SkinEntry> loaded = null;
            long next = 1;
            if (root != null && root.isJsonArray()) {                // legacy file
                loaded = GSON.fromJson(root, new TypeToken<List<SkinEntry>>() {
                }.getType());
            } else if (root != null && root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("skins")) {
                    loaded = GSON.fromJson(obj.getAsJsonArray("skins"), new TypeToken<List<SkinEntry>>() {
                    }.getType());
                }
                if (obj.has("nextSequence")) next = obj.get("nextSequence").getAsLong();
            }
            entries = loaded != null ? loaded : new ArrayList<>();
            long max = 0;
            for (SkinEntry e : entries) max = Math.max(max, e.sequence);
            nextSequence = Math.max(next, max + 1);
        } catch (Exception e) {
            e.printStackTrace();
            entries = new ArrayList<>();
        }
    }

    /**
     * Writes through a temp file so a crash can never leave a half-written config.
     */
    public static void saveStorage() {
        try {
            if (!Files.exists(STORAGE_DIR)) Files.createDirectories(STORAGE_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            root.addProperty("nextSequence", nextSequence);
            JsonArray arr = GSON.toJsonTree(entries, new TypeToken<List<SkinEntry>>() {
            }.getType()).getAsJsonArray();
            root.add("skins", arr);

            Path tmp = JSON_FILE.resolveSibling("skin_storage.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, JSON_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, JSON_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ migration / hashing / duplicates
    private static boolean migrateAndHash() {
        boolean changed = false;
        for (SkinEntry e : entries) {
            if (e.sequence <= 0) {
                e.sequence = nextSequence++;
                changed = true;
            }
            if (e.createdAtMillis <= 0) {
                e.createdAtMillis = 0; // unknown for old skins; sequence still tells the order
            }
            if (e.contentHash == null) {
                Path p = STORAGE_DIR.resolve(e.fileName);
                if (Files.exists(p)) {
                    try {
                        e.contentHash = sha256(Files.readAllBytes(p));
                        changed = true;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Re-labels identical skins: duplicateIndex 1 = saved first, 2 = second ...
     */
    private static void recomputeDuplicates() {
        Map<String, List<SkinEntry>> groups = new HashMap<>();
        for (SkinEntry e : entries) {
            if (e.contentHash == null) continue;
            groups.computeIfAbsent(e.contentHash, k -> new ArrayList<>()).add(e);
        }
        for (List<SkinEntry> g : groups.values()) {
            g.sort(Comparator.comparingLong(a -> a.sequence));
            for (int i = 0; i < g.size(); i++) {
                g.get(i).duplicateIndex = i + 1;
                g.get(i).duplicateOfId = i == 0 ? null : g.get(0).id;
            }
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Hash of the PNG exactly as it would be stored.
     */
    public static String computeHash(NativeImage image) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("skin_hash", ".png");
            image.writeTo(tmp);
            return sha256(Files.readAllBytes(tmp));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (tmp != null) Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Skins already in storage that are pixel-identical to this image (oldest first).
     */
    public static List<SkinEntry> findDuplicates(NativeImage image) {
        String hash = computeHash(image);
        return entriesWithHash(hash);
    }

    private static List<SkinEntry> entriesWithHash(String hash) {
        List<SkinEntry> result = new ArrayList<>();
        if (hash == null) return result;
        for (SkinEntry e : entries) if (hash.equals(e.contentHash)) result.add(e);
        result.sort(Comparator.comparingLong(a -> a.sequence));
        return result;
    }

    /**
     * Drops entries whose PNG is gone. Returns true if anything was removed.
     */
    public static boolean purgeMissingFiles(boolean save) {
        boolean removed = entries.removeIf(e -> !Files.exists(STORAGE_DIR.resolve(e.fileName)));
        if (removed && save) saveStorage();
        return removed;
    }

    // ------------------------------------------------------------------ add / replace
    public static SkinEntry addSkin(String name, String modelType, NativeImage image) {
        String hash = computeHash(image);
        List<SkinEntry> dups = entriesWithHash(hash);

        if (name == null || name.trim().isEmpty()) {
            name = dups.isEmpty() ? generateNextDefaultName() : dups.get(0).name + " (" + (dups.size() + 1) + ")";
        } else {
            name = name.trim();
        }

        long seq = nextSequence++;
        long now = System.currentTimeMillis();
        String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = safeName + "_" + seq + "_" + now + ".png";
        Path imagePath = STORAGE_DIR.resolve(fileName);

        try {
            image.writeTo(imagePath);
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            SkinEntry entry = new SkinEntry(UUID.randomUUID().toString(), name, modelType, date, fileName);
            entry.sequence = seq;
            entry.createdAtMillis = now;
            entry.createdAt = LocalDateTime.now().format(STAMP);
            entry.updatedAtMillis = now;
            entry.contentHash = hash;
            entries.add(entry);
            recomputeDuplicates();
            saveStorage();
            return entry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Overwrites the stored image + model type of an existing skin (id, name, folder and favorite are kept).
     */
    public static SkinEntry replaceSkin(String id, String modelType, NativeImage image) {
        SkinEntry entry = getEntryById(id);
        if (entry == null) return null;
        try {
            long now = System.currentTimeMillis();
            String safeName = entry.name.replaceAll("[^a-zA-Z0-9._-]", "_");
            String newFile = safeName + "_" + entry.sequence + "_" + now + ".png";
            image.writeTo(STORAGE_DIR.resolve(newFile));
            Files.deleteIfExists(STORAGE_DIR.resolve(entry.fileName));
            entry.fileName = newFile;
            entry.modelType = modelType;
            entry.updatedAtMillis = now;
            entry.contentHash = computeHash(image);
            recomputeDuplicates();
            saveStorage();
            return entry;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String generateNextDefaultName() {
        int number = 1;
        while (true) {
            String candidate = "My Skin " + number;
            boolean exists = false;
            for (SkinEntry entry : entries) {
                if (entry.name.equalsIgnoreCase(candidate)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) return candidate;
            number++;
        }
    }

    // ------------------------------------------------------------------ rename / delete / update
    public static void renameSkin(String id, String newName) {
        if (newName == null || newName.trim().isEmpty()) return;
        String cleanName = newName.trim();

        for (SkinEntry entry : entries) {
            if (entry.id.equals(id)) {
                entry.name = cleanName;
                try {
                    String safe = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + entry.sequence + "_" + System.currentTimeMillis() + ".png";
                    Path oldPath = STORAGE_DIR.resolve(entry.fileName);
                    Path newPath = STORAGE_DIR.resolve(safe);
                    if (Files.exists(oldPath)) {
                        Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.fileName = safe;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        saveStorage();
    }

    /**
     * Removes the skin from the config AND the disk, then saves the cloud immediately.
     */
    public static void deleteSkin(String id) {
        SkinEntry toRemove = getEntryById(id);
        if (toRemove != null) {
            try {
                Files.deleteIfExists(STORAGE_DIR.resolve(toRemove.fileName));
            } catch (Exception e) {
                e.printStackTrace();
            }
            entries.remove(toRemove);
            recomputeDuplicates(); // the next identical skin becomes "first"
            saveStorage();
        }
    }

    public static void updateSkin(String id, String newName, String newModelType) {
        for (SkinEntry entry : entries) {
            if (entry.id.equals(id)) {
                if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(entry.name)) {
                    renameSkin(id, newName);
                }
                if (newModelType != null && !newModelType.trim().isEmpty()) {
                    entry.modelType = newModelType;
                    entry.updatedAtMillis = System.currentTimeMillis();
                }
                break;
            }
        }
        saveStorage();
    }

    public static Path getSkinPath(SkinEntry entry) {
        return STORAGE_DIR.resolve(entry.fileName);
    }
}
