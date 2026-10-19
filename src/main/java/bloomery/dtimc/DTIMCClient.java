package bloomery.dtimc;

import bloomery.dtimc.compat.plasmovoice.PlasmoVoiceStateChannel;
import bloomery.dtimc.networking.SkinPacket;
import bloomery.dtimc.storage.SkinStorageManager;
import bloomery.dtimc.util.SkinManager;
import bloomery.dtimc.util.SkinNetworkHandler;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class DTIMC implements ClientModInitializer {
    private static KeyBinding openMenuKey;
    public static final Map<UUID, Boolean> SPEAKING_PLAYERS = new ConcurrentHashMap<>();

    @Override
    public void onInitializeClient() {
        SkinStorageManager.init();
        SkinNetworkHandler.registerServer();

        ClientPlayNetworking.registerGlobalReceiver(SkinNetworkHandler.SKIN_SYNC_ID, (client, handler, buf, responseSender) -> {
            SkinPacket packet = SkinPacket.read(buf);
            client.execute(() -> {
                SkinManager.receive(packet.getUuid(), packet.getChunkIndex(), packet.getTotalChunks(), packet.getData());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PlasmoVoiceStateChannel.VOICE_STATE_PACKET, (client, handler, buf, responseSender) -> {
            UUID speakerUuid = buf.readUuid();
            boolean isSpeaking = buf.readBoolean();

            client.execute(() -> {
                if (isSpeaking) {
                    SPEAKING_PLAYERS.put(speakerUuid, true);
                } else {
                    SPEAKING_PLAYERS.remove(speakerUuid);
                }
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
                            context.fill(0, 0, width, height, 0xFF8800FF);
                            context.fill(1, 1, width - 1, height - 1, 0xFF000000);
                            context.fill(2, 2, 4, height - 2, 0xFF00FFFF); // สีฟ้า Cyan

                            context.drawText(manager.getClient().textRenderer,
                                    Text.literal("Devathip Skin: Activated!"), 10, 7, 0x00FFFF, false);

                            context.drawText(manager.getClient().textRenderer,
                                    Text.literal("Press R to open skin menu!"), 10, 18, 0xAAAAAA, false); // สีเทาอ่อน

                            return startTime >= 5000L ? Visibility.HIDE : Visibility.SHOW;
                        }
                    });
                }
            });
        });

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Skin Menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "Devathip Interface"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SkinChangerScreen());
                }
            }
        });
    }
}