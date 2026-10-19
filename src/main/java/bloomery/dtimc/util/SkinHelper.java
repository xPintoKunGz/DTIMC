package bloomery.dtimc.devathip_interface.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
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

    private static final Map<java.util.UUID, Identifier> CUSTOM_SKINS = new ConcurrentHashMap<>();

    public static Identifier getCustomSkin(UUID uuid) {
        return CUSTOM_SKINS.get(uuid);
    }

    public static void applySkin(PlayerEntity player, Identifier skinId, String modelType, int width, int height) {
        if (player == null || skinId == null) return;

        String finalModelType = modelType;

        CUSTOM_SKINS.put(player.getUuid(), skinId);

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