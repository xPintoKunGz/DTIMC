package com.example.myskinmod.util;

import com.example.myskinmod.networking.SkinPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Arrays;

public class SkinNetworkHandler {

    // ใช้ ID เดียวให้จบ ไม่ต้องมีหลายอัน
    public static final Identifier SKIN_SYNC_ID = new Identifier("kui", "skin_sync");

    public static void registerServer() {
        // รับข้อมูลจาก Client ที่ส่งสกินมา
        ServerPlayNetworking.registerGlobalReceiver(SKIN_SYNC_ID, (server, player, handler, buf, responseSender) -> {

            // อ่านข้อมูลด้วย SkinPacket ตัวใหม่ของเรา (อ่านแบบ Raw Bytes)
            SkinPacket packet = SkinPacket.read(buf);

            server.execute(() -> {
                // วนลูปส่งให้ทุกคนในเซิร์ฟเวอร์
                for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList()) {
                    // ไม่ต้องส่งกลับไปให้ตัวเอง
                    if (otherPlayer.getUuid().equals(player.getUuid())) continue;

                    PacketByteBuf outBuf = PacketByteBufs.create();
                    packet.write(outBuf);

                    // ด่านตรวจสุดท้าย: ถ้าขนาดปลอดภัย ค่อยส่งออกไป
                    if (outBuf.readableBytes() <= 32767) {
                        ServerPlayNetworking.send(otherPlayer, SKIN_SYNC_ID, outBuf);
                    } else {
                        System.err.println("KUI Server: Packet โดนบล็อกเพราะขนาดเกิน (" + outBuf.readableBytes() + " bytes)");
                    }
                }
            });
        });
    }

    public static void sendSkinChunks(byte[] fullData) {
        final int CHUNK_SIZE = 16384;
        int totalChunks = (int) Math.ceil((double) fullData.length / CHUNK_SIZE);

        if (net.minecraft.client.MinecraftClient.getInstance().player == null) return;
        java.util.UUID myUuid = net.minecraft.client.MinecraftClient.getInstance().player.getUuid();

        // ใช้ Thread ใหม่เพื่อไม่ให้หน้าจอเกมค้างตอนกำลังส่ง
        new Thread(() -> {
            try {
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * CHUNK_SIZE;
                    int end = Math.min(fullData.length, start + CHUNK_SIZE);
                    byte[] chunk = Arrays.copyOfRange(fullData, start, end);

                    SkinPacket packet = new SkinPacket(myUuid, i, totalChunks, chunk);
                    PacketByteBuf buf = PacketByteBufs.create();
                    packet.write(buf);

                    // ส่ง Packet
                    ClientPlayNetworking.send(SKIN_SYNC_ID, buf);

                    // ⭐ หัวใจสำคัญ: หน่วงเวลา 50 มิลลิวินาทีต่อ 1 Packet
                    // เพื่อไม่ให้ Server ตกใจจนเตะเราออก
                    Thread.sleep(100);
                }
                System.out.println("KUI: ส่งสกินครบทุกส่วนแล้ว!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}