package com.example.myskinmod;

import net.fabricmc.api.ModInitializer;
import com.example.myskinmod.util.SkinNetworkHandler;

public class MySkinModServer implements ModInitializer {
    @Override
    public void onInitialize() {
        SkinNetworkHandler.registerServer();

        System.out.println("NUI: Server-side networking initialized!");
    }
}