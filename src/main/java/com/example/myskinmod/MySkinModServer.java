package com.example.myskinmod;

import net.fabricmc.api.ModInitializer;
import com.example.myskinmod.util.SkinNetworkHandler;
import com.example.myskinmod.storage.ActiveSkinManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class MySkinModServer implements ModInitializer {
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