package bloomery.dtimc;

import bloomery.dtimc.compat.plasmovoice.PlasmoVoiceBridgeCompat;
import net.fabricmc.api.ModInitializer;
import bloomery.dtimc.util.SkinNetworkHandler;
import bloomery.dtimc.storage.ActiveSkinManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class DTIMCServer implements ModInitializer {
    @Override
    public void onInitialize() {
        SkinNetworkHandler.registerClient();
        PlasmoVoiceBridgeCompat.init();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                ActiveSkinManager.loadAndApplySavedSkin();
            });
        });

        System.out.println("DTIMC: Server-side networking initialized!");
    }
}