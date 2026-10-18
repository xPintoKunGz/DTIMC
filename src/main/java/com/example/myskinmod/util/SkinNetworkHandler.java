package com.example.myskinmod.util;

import com.example.myskinmod.networking.SkinPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkinNetworkHandler {

    public static final Identifier SKIN_SYNC_ID = new Identifier("kui", "skin_sync");

    private static final Map<UUID, Map<Integer, byte[]>> incomingChunks = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> totalChunksMap = new ConcurrentHashMap<>();
    private static final Map<UUID, String> incomingModelTypes = new ConcurrentHashMap<>(); // ⭐ เก็บ Model Type รายคน

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SKIN_SYNC_ID, (server, player, handler, buf, responseSender) -> {
            SkinPacket packet = SkinPacket.read(buf);
            if (packet == null) return;

            server.execute(() -> {
                UUID senderUuid = packet.getUuid();
                int chunkIndex = packet.getChunkIndex();
                int totalChunks = packet.getTotalChunks();
                byte[] data = packet.getData();
                String modelType = packet.getModelType();

                if (senderUuid == null || data == null) return;

                incomingChunks.computeIfAbsent(senderUuid, k -> new ConcurrentHashMap<>()).put(chunkIndex, data);
                totalChunksMap.put(senderUuid, totalChunks);
                incomingModelTypes.put(senderUuid, modelType);

                Map<Integer, byte[]> userChunks = incomingChunks.get(senderUuid);

                if (userChunks.size() >= totalChunks) {
                    int totalLength = 0;
                    for (int i = 0; i < totalChunks; i++) {
                        totalLength += userChunks.get(i).length;
                    }

                    byte[] fullSkinBytes = new byte[totalLength];
                    int currentPos = 0;
                    for (int i = 0; i < totalChunks; i++) {
                        byte[] chunkData = userChunks.get(i);
                        System.arraycopy(chunkData, 0, fullSkinBytes, currentPos, chunkData.length);
                        currentPos += chunkData.length;
                    }

                    String finalModelType = incomingModelTypes.getOrDefault(senderUuid, "default");

                    incomingChunks.remove(senderUuid);
                    totalChunksMap.remove(senderUuid);
                    incomingModelTypes.remove(senderUuid);

                    for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList()) {
                        if (otherPlayer.getUuid().equals(senderUuid)) continue;
                        sendChunksToClient(otherPlayer, senderUuid, fullSkinBytes, finalModelType);
                    }
                }
            });
        });
    }

    private static void sendChunksToClient(ServerPlayerEntity target, UUID senderUuid, byte[] fullData, String modelType) {
        final int CHUNK_SIZE = 16384;
        int totalChunks = (int) Math.ceil((double) fullData.length / CHUNK_SIZE);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(fullData.length, start + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(fullData, start, end);

            SkinPacket packet = new SkinPacket(senderUuid, i, totalChunks, chunk, modelType);
            PacketByteBuf outBuf = PacketByteBufs.create();
            packet.write(outBuf);

            if (outBuf.readableBytes() <= 32767) {
                ServerPlayNetworking.send(target, SKIN_SYNC_ID, outBuf);
            }
        }
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SKIN_SYNC_ID, (client, handler, buf, responseSender) -> {
            SkinPacket packet = SkinPacket.read(buf);
            if (packet == null) return;

            client.execute(() -> {
                UUID senderUuid = packet.getUuid();
                int chunkIndex = packet.getChunkIndex();
                int totalChunks = packet.getTotalChunks();
                byte[] data = packet.getData();
                String modelType = packet.getModelType();

                if (senderUuid == null || data == null) return;

                incomingChunks.computeIfAbsent(senderUuid, k -> new ConcurrentHashMap<>()).put(chunkIndex, data);
                totalChunksMap.put(senderUuid, totalChunks);
                incomingModelTypes.put(senderUuid, modelType);

                Map<Integer, byte[]> userChunks = incomingChunks.get(senderUuid);

                if (userChunks.size() >= totalChunks) {
                    int totalLength = 0;
                    for (int i = 0; i < totalChunks; i++) {
                        totalLength += userChunks.get(i).length;
                    }

                    byte[] fullSkinBytes = new byte[totalLength];
                    int currentPos = 0;
                    for (int i = 0; i < totalChunks; i++) {
                        byte[] chunkData = userChunks.get(i);
                        System.arraycopy(chunkData, 0, fullSkinBytes, currentPos, chunkData.length);
                        currentPos += chunkData.length;
                    }

                    String finalModelType = incomingModelTypes.getOrDefault(senderUuid, "default");

                    incomingChunks.remove(senderUuid);
                    totalChunksMap.remove(senderUuid);
                    incomingModelTypes.remove(senderUuid);

                    if (client.world != null) {
                        net.minecraft.entity.player.PlayerEntity targetPlayer = client.world.getPlayerByUuid(senderUuid);
                        if (targetPlayer != null) {
                            try (ByteArrayInputStream in = new ByteArrayInputStream(fullSkinBytes);
                                 NativeImage img = NativeImage.read(in)) {

                                NativeImageBackedTexture texture = new NativeImageBackedTexture(img);
                                Identifier skinId = client.getTextureManager().registerDynamicTexture("remote_skin_" + senderUuid, texture);

                                // ⭐ ใช้ finalModelType ที่รับมาจาก Packet แทน "default"
                                SkinHelper.applySkin(targetPlayer, skinId, finalModelType, img.getWidth(), img.getHeight());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        });
    }

    public static void sendSkinChunks(byte[] fullData) {
        sendSkinChunks(fullData, "default");
    }

    // เมธอดหลักเดิมที่มี 2 พารามิเตอร์
    public static void sendSkinChunks(byte[] fullData, String modelType) {
        final int CHUNK_SIZE = 16384;
        int totalChunks = (int) Math.ceil((double) fullData.length / CHUNK_SIZE);

        if (MinecraftClient.getInstance().player == null) return;
        UUID myUuid = MinecraftClient.getInstance().player.getUuid();

        new Thread(() -> {
            try {
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * CHUNK_SIZE;
                    int end = Math.min(fullData.length, start + CHUNK_SIZE);
                    byte[] chunk = Arrays.copyOfRange(fullData, start, end);

                    SkinPacket packet = new SkinPacket(myUuid, i, totalChunks, chunk, modelType);
                    PacketByteBuf buf = PacketByteBufs.create();
                    packet.write(buf);

                    ClientPlayNetworking.send(SkinNetworkHandler.SKIN_SYNC_ID, buf);
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}