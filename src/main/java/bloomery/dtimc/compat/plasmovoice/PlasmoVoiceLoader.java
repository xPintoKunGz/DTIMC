package bloomery.dtimc.compat.plasmovoice;

import su.plo.voice.api.client.PlasmoVoiceClient;

/**
 * Isolated so that {@link LipSyncBootstrap} never links against Plasmo Voice classes
 * unless Plasmo Voice is actually installed.
 */
final class PlasmoVoiceLoader {

    private PlasmoVoiceLoader() {
    }

    static void load() {
        PlasmoVoiceClient.getAddonsLoader().load(PlasmoVoiceAddon.INSTANCE);
    }
}
