package com.example.myskinmod.util;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class SkinFilePicker {

    /**
     * ฟังก์ชันเปิดหน้าต่างเลือกไฟล์แบบ Asynchronous (ไม่บล็อกเกม)
     * รองรับเฉพาะไฟล์ .png เพื่อป้องกัน User เลือกไฟล์ผิดประเภท
     */
    public static CompletableFuture<File> browseSkinAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // กำหนดประเภทไฟล์ (Filter) ให้แสดงแค่ .png
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();

                // เปิดหน้าต่าง File Dialog ของระบบปฏิบัติการ (Windows/Mac/Linux)
                String resultPath = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Minecraft Skin (.png)", // ชื่อหน้าต่าง
                        null,                           // ตำแหน่งเริ่มต้น (null = ค่าเริ่มต้นของระบบ)
                        filters,                        // นามสกุลไฟล์ที่อนุญาต
                        "PNG Images",                   // คำอธิบาย
                        false                           // ไม่อนุญาตให้เลือกหลายไฟล์พร้อมกัน
                );

                // ตรวจสอบว่าผู้เล่นได้เลือกไฟล์หรือไม่ (หรือกด Cancel)
                if (resultPath != null) {
                    File selectedFile = new File(resultPath);
                    if (selectedFile.exists() && selectedFile.isFile()) {
                        return selectedFile;
                    }
                }
            } catch (Exception e) {
                System.err.println("[MySkinMod] File Picker Error: " + e.getMessage());
            }
            return null; // คืนค่า null หากยกเลิกการเลือกหรือเกิดข้อผิดพลาด
        });
    }
}