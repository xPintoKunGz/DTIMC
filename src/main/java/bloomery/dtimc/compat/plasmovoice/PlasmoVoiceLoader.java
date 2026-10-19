package bloomery.dtimc.compat.plasmovoice;

import su.plo.voice.api.client.PlasmoVoiceClient;

/**
 * Isolated so that LipSyncBootstrap never links against Plasmo Voice classes
 * unless Plasmo Voice is actually installed.
 */
public final class PlasmoVoiceLoader {

    private PlasmoVoiceLoader() {
    }

    public static void load() {
        PlasmoVoiceClient.getAddonsLoader().load(PlasmoVoiceAddon.INSTANCE);
    }
}
