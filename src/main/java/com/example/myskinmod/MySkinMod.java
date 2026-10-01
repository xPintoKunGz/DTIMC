package com.example.myskinmod;

import com.example.myskinmod.networking.SkinPacket;
import com.example.myskinmod.util.SkinManager;
import com.example.myskinmod.util.SkinNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class MySkinMod implements ClientModInitializer {

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        // 1. ลงทะเบียนระบบรับข้อมูลสกิน
        ClientPlayNetworking.registerGlobalReceiver(SkinNetworkHandler.SKIN_SYNC_ID, (client, handler, buf, responseSender) -> {
            SkinPacket packet = SkinPacket.read(buf);
            client.execute(() -> {
                SkinManager.receive(packet.senderUuid(), packet.chunkIndex(), packet.totalChunks(), packet.data());
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                if (client.getToastManager() != null) {
                    client.getToastManager().add(new net.minecraft.client.toast.Toast() {
                        @Override
                        public Visibility draw(net.minecraft.client.gui.DrawContext context, net.minecraft.client.toast.ToastManager manager, long startTime) {
                            int width = this.getWidth();
                            int height = this.getHeight();

                            // 1. วาดขอบ (Border) - สีม่วงนีออน (0xFF8800FF)
                            context.fill(0, 0, width, height, 0xFF8800FF);

                            // 2. วาดพื้นหลัง (Background) - สีดำเข้มถัดเข้ามา 1 pixel เพื่อให้เห็นขอบ
                            context.fill(1, 1, width - 1, height - 1, 0xFF000000);

                            // 3. วาดแถบสีด้านซ้ายเล็กๆ ให้ดูเท่ (Accent Line)
                            context.fill(2, 2, 4, height - 2, 0xFF00FFFF); // สีฟ้า Cyan

                            // 4. วาดข้อความ (0x00FFFF = สีฟ้า, 0xFFFFFF = สีขาว)
                            context.drawText(manager.getClient().textRenderer,
                                    Text.literal("Devathip Skin: Activated!"), 10, 7, 0x00FFFF, false);

                            context.drawText(manager.getClient().textRenderer,
                                    Text.literal("Press R to open skin menu!"), 10, 18, 0xAAAAAA, false); // สีเทาอ่อน

                            // แสดงผล 5 วินาที
                            return startTime >= 5000L ? Visibility.HIDE : Visibility.SHOW;
                        }
                    });
                }
            });
        });

        // 2. ลงทะเบียนปุ่มกด
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Devathip Skin Menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "Devathip Skin"
        ));

        // 4. ตรวจจับการกดปุ่ม
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SkinChangerScreen());
                }
            }
        });
    }
}