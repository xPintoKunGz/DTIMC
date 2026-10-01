package com.example.myskinmod.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SkinStorageManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_DIR = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/myskinmod/skins");
    private static final Path JSON_FILE = STORAGE_DIR.resolve("skin_storage.json");

    public static class SkinEntry {
        public String id;
        public String name;
        public String modelType; // "default" หรือ "slim"
        public String createdDate;
        public String fileName;

        public SkinEntry(String id, String name, String modelType, String createdDate, String fileName) {
            this.id = id;
            this.name = name;
            this.modelType = modelType;
            this.createdDate = createdDate;
            this.fileName = fileName;
        }
    }

    private static List<SkinEntry> entries = new ArrayList<>();

    public static void init() {
        try {
            if (!Files.exists(STORAGE_DIR)) {
                Files.createDirectories(STORAGE_DIR);
            }
            loadStorage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<SkinEntry> getEntries() {
        return entries;
    }

    public static void loadStorage() {
        if (!Files.exists(JSON_FILE)) {
            entries = new ArrayList<>();
            return;
        }
        try (Reader reader = Files.newBufferedReader(JSON_FILE)) {
            List<SkinEntry> loaded = GSON.fromJson(reader, new TypeToken<List<SkinEntry>>(){}.getType());
            if (loaded != null) {
                entries = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
            entries = new ArrayList<>();
        }
    }

    public static void saveStorage() {
        try (Writer writer = Files.newBufferedWriter(JSON_FILE)) {
            GSON.toJson(entries, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static SkinEntry addSkin(String name, String modelType, NativeImage image) {
        String id = UUID.randomUUID().toString();

        // กำหนดชื่อเริ่มต้นถ้าไม่ได้ระบุ
        if (name == null || name.trim().isEmpty()) {
            name = generateNextDefaultName();
        } else {
            name = name.trim();
        }

        // ตั้งชื่อไฟล์ตามชื่อสกินที่ปลอดภัย
        String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = safeName + "_" + System.currentTimeMillis() + ".png";
        Path imagePath = STORAGE_DIR.resolve(fileName);

        try {
            image.writeTo(imagePath);
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            SkinEntry entry = new SkinEntry(id, name, modelType, date, fileName);
            entries.add(entry);
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
            if (!exists) {
                return candidate;
            }
            number++;
        }
    }

    // [เพิ่มใหม่] เปลี่ยนชื่อสกิน + เปลี่ยนชื่อไฟล์ .png จริงบนดิสก์
    public static void renameSkin(String id, String newName) {
        if (newName == null || newName.trim().isEmpty()) return;
        String cleanName = newName.trim();

        for (SkinEntry entry : entries) {
            if (entry.id.equals(id)) {
                entry.name = cleanName;

                // เปลี่ยนชื่อไฟล์ .png จริงบนดิสก์
                try {
                    String safeFileName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + System.currentTimeMillis() + ".png";
                    Path oldPath = STORAGE_DIR.resolve(entry.fileName);
                    Path newPath = STORAGE_DIR.resolve(safeFileName);

                    if (Files.exists(oldPath)) {
                        Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.fileName = safeFileName;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        saveStorage();
    }

    public static void deleteSkin(String id) {
        SkinEntry toRemove = null;
        for (SkinEntry entry : entries) {
            if (entry.id.equals(id)) {
                toRemove = entry;
                break;
            }
        }
        if (toRemove != null) {
            try {
                Files.deleteIfExists(STORAGE_DIR.resolve(toRemove.fileName));
            } catch (Exception e) {
                e.printStackTrace();
            }
            entries.remove(toRemove);
            saveStorage();
        }
    }

    public static void updateSkin(String id, String newName, String newModelType) {
        for (SkinEntry entry : entries) {
            if (entry.id.equals(id)) {
                if (newName != null && !newName.trim().isEmpty()) {
                    renameSkin(id, newName);
                }
                if (newModelType != null && !newModelType.trim().isEmpty()) {
                    entry.modelType = newModelType;
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