package com.example.myskinmod.storage;

import com.example.myskinmod.util.SkinHelper;
import com.example.myskinmod.util.SkinNetworkHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ActiveSkinManager {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("myskinmod_active.json");
    private static final Path SAVED_SKIN_PATH = FabricLoader.getInstance().getConfigDir().resolve("myskinmod_last_equipped.png");
    private static final Gson GSON = new Gson();

    // บันทึกสกินล่าสุดเมื่อผู้ใช้สวมใส่สกิน
    public static void saveActiveSkin(byte[] skinBytes, String modelType) {
        try {
            Files.write(SAVED_SKIN_PATH, skinBytes);

            JsonObject json = new JsonObject();
            json.addProperty("modelType", modelType != null ? modelType : "default");

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // โหลดสกินล่าสุดเมื่อเข้าเกม หรือเข้าเซิร์ฟเวอร์ใหม่
    public static void loadAndApplySavedSkin() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !Files.exists(SAVED_SKIN_PATH)) return;

        try {
            String modelType = "default";
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null && json.has("modelType")) {
                        modelType = json.get("modelType").getAsString();
                    }
                }
            }

            byte[] bytes = Files.readAllBytes(SAVED_SKIN_PATH);
            try (InputStream in = Files.newInputStream(SAVED_SKIN_PATH);
                 NativeImage img = NativeImage.read(in)) {

                NativeImageBackedTexture texture = new NativeImageBackedTexture(img);
                Identifier skinId = client.getTextureManager().registerDynamicTexture("active_custom_skin", texture);

                // สวมใส่สกินให้ตัวเราเอง
                SkinHelper.applySkin(client.player, skinId, modelType, img.getWidth(), img.getHeight());

                // ส่ง Packet กระจายให้ผู้เล่นคนอื่นในเซิร์ฟเวอร์/LAN เห็น
                SkinNetworkHandler.sendSkinChunks(bytes, modelType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}