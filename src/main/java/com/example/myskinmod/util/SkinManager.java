package com.example.myskinmod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinManager {
    // ใช้ Map ซ้อน Map: UUID -> (Index -> Data) เพื่อรองรับการส่งพร้อมกันหลายคน
    private static final Map<UUID, Map<Integer, byte[]>> assemblingData = new ConcurrentHashMap<>();
    public static float currentProgress = 0f;

    public static void receive(UUID uuid, int index, int total, byte[] chunk) {
        // อัปเดต Progress
        if (MinecraftClient.getInstance().player != null && uuid.equals(MinecraftClient.getInstance().player.getUuid())) {
            currentProgress = (float) (index + 1) / total;
        }

        // สร้างที่เก็บจิ๊กซอว์สำหรับ UUID นี้ (ใช้ TreeMap เพื่อให้ Index เรียงลำดับ 0, 1, 2... เสมอ)
        assemblingData.putIfAbsent(uuid, new TreeMap<>());
        Map<Integer, byte[]> userChunks = assemblingData.get(uuid);

        // เก็บชิ้นส่วนลงในตำแหน่ง Index ของมัน
        userChunks.put(index, chunk);

        // ถ้าได้ครบทุกชิ้นแล้ว (จำนวนชิ้นใน Map == total)
        if (userChunks.size() == total) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                // ประกอบร่างจิ๊กซอว์ตามลำดับ Index
                for (int i = 0; i < total; i++) {
                    byte[] data = userChunks.get(i);
                    if (data != null) {
                        baos.write(data);
                    }
                }

                // ส่งข้อมูลที่ประกอบเสร็จแล้วไป Apply
                apply(uuid, baos.toByteArray());

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // เคลียร์ข้อมูลออกจาก Memory
                assemblingData.remove(uuid);
                if (MinecraftClient.getInstance().player != null && uuid.equals(MinecraftClient.getInstance().player.getUuid())) {
                    currentProgress = 0f;
                }
            }
        }
    }

    private static void apply(UUID uuid, byte[] fullData) {
        MinecraftClient.getInstance().execute(() -> {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(fullData)) {
                NativeImage image = NativeImage.read(bais);

                if (image != null) {
                    Identifier id = new Identifier("kui", "skins/" + uuid.toString());

                    // เคลียร์ Texture เก่า
                    MinecraftClient.getInstance().getTextureManager().destroyTexture(id);

                    // ลงทะเบียนรูปใหม่
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);

                    System.out.println("KUI: Skin applied successfully for " + uuid + " (Size: " + fullData.length + " bytes)");
                }
            } catch (Exception e) {
                System.err.println("KUI: Image construction failed - " + e.getMessage());
                // ถ้าพังตรงนี้ แสดงว่า byte[] ที่ได้มายังไม่ใช่ PNG ที่สมบูรณ์
            }
        });
    }
}