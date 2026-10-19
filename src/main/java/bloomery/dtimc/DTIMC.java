package bloomery.dtimc;

import net.fabricmc.api.ModInitializer;
import bloomery.dtimc.util.SkinNetworkHandler;
import bloomery.dtimc.storage.ActiveSkinManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class DTIMC implements ModInitializer {
    @Override
    public void onInitialize() {
        SkinNetworkHandler.registerClient();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                ActiveSkinManager.loadAndApplySavedSkin();
            });
        });

        System.out.println("DTIMC: Server-side networking initialized!");
    }
}