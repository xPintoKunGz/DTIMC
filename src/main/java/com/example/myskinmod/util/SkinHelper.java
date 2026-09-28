package com.example.myskinmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public class SkinHelper {

    public static class SkinData {
        public final Identifier skinId;
        public final String modelType;
        public final int width;
        public final int height;

        public SkinData(Identifier skinId, String modelType, int width, int height) {
            this.skinId = skinId;
            this.modelType = modelType;
            this.width = width;
            this.height = height;
        }
    }

    private static final Map<UUID, SkinData> playerSkinData = new ConcurrentHashMap<>();

//    public static AbstractClientPlayerEntity createPreviewPlayer(ClientWorld world, Identifier skinId, String modelType) {
//        GameProfile profile = new GameProfile(UUID.randomUUID(), "PreviewPlayer");
//
//        // สร้าง texture property พร้อม metadata
//        String textureJson = "{\n" +
//                "  \"textures\": {\n" +
//                "    \"SKIN\": {\n" +
//                "      \"url\": \"http://textures.minecraft.net/texture/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"\n" +
//                (modelType.equals("slim") ? "      ,\"metadata\": {\"model\": \"slim\"}" : "") + "\n" +
//                "    }\n" +
//                "  }\n" +
//                "}";
//
//        String encoded = Base64.getEncoder().encodeToString(textureJson.getBytes());
//        profile.getProperties().put("textures", new Property("textures", encoded));
//
//        // สร้าง player จำลอง
//        return new AbstractClientPlayerEntity(world, profile) {
//            @Override
//            public Identifier getSkinTexture() {
//                return skinId;
//            }
//
//            @Override
//            public boolean isSpectator() {
//                return false;
//            }
//
//            @Override
//            public boolean isCreative() {
//                return false;
//            }
//        };
//    }

    public static void applySkin(PlayerEntity player, Identifier skinId, String modelType, int width, int height) {
        if (player == null || skinId == null) return;

        String finalModelType = modelType;

        if (finalModelType == null || finalModelType.trim().isEmpty()) {
            try {
                if (player instanceof AbstractClientPlayerEntity clientPlayer) {
                    String originalModel = clientPlayer.getModel();
                    finalModelType = (originalModel != null && originalModel.equalsIgnoreCase("slim")) ? "slim" : "default";
                } else {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.getNetworkHandler() != null) {
                        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
                        if (entry != null && entry.getModel() != null) {
                            finalModelType = entry.getModel().equalsIgnoreCase("slim") ? "slim" : "default";
                        } else {
                            finalModelType = "default";
                        }
                    } else {
                        finalModelType = "default";
                    }
                }
            } catch (Exception e) {
                finalModelType = "default";
            }
        }

        playerSkinData.put(player.getUuid(), new SkinData(skinId, finalModelType, width, height));
    }

    public static Identifier getPlayerSkinTexture(UUID playerId) {
        if (playerId == null) return null;
        SkinData data = playerSkinData.get(playerId);
        return (data != null) ? data.skinId : null;
    }

    public static String getPlayerModelType(UUID playerId) {
        if (playerId == null) return null;
        SkinData data = playerSkinData.get(playerId);
        return (data != null) ? data.modelType : null;
    }

    /**
     * ดึงวัตถุ SkinData ทั้งหมด
     */
    public static SkinData getSkinData(UUID playerId) {
        if (playerId == null) return null;
        return playerSkinData.get(playerId);
    }

    /**
     * ลบข้อมูลสกินเมื่อต้องการคืนค่าสกินเดิม
     */
    public static void removeSkin(UUID playerId) {
        if (playerId != null) {
            playerSkinData.remove(playerId);
        }
    }
}