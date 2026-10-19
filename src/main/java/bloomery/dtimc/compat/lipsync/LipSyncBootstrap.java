package bloomery.dtimc.compat.lipsync;

import bloomery.dtimc.compat.plasmovoice.PlasmoVoiceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the optional Figura + Plasmo Voice lip-sync integration.
 *
 * <p>This class deliberately references <b>no</b> Figura or Plasmo Voice classes, so it is always
 * safe to load - even when neither mod is installed. Everything that touches those mods lives in
 * other classes that are only loaded after {@link #isEnabled()} returned {@code true}.</p>
 *
 * <p>Ported from FigExtra (https://github.com/Furrc/FigExtra, MIT, (c) 2024 Furrc).
 * See THIRD_PARTY_NOTICES.md.</p>
 */
public final class LipSyncBootstrap {

    public static final Logger LOGGER = LoggerFactory.getLogger("DTIMC/LipSync");

    /**
     * Plasmo Voice 2.1.1 changed both the addon injection mechanism (@InjectPlasmoVoice) and the
     * audio capture event API. This integration is written against that newer API.
     */
    private static final String MIN_PLASMO_VOICE = "2.1.1";

    private static Boolean enabled;

    private LipSyncBootstrap() {
    }

    /** Called once from {@code MySkinMod#onInitializeClient}. Never throws. */
    public static void init() {
        if (!isEnabled()) {
            return;
        }
        try {
            PlasmoVoiceLoader.load();
            LOGGER.info("Figura lip-sync enabled (Plasmo Voice addon registered).");
        } catch (Throwable t) {
            // Never let an optional integration take the whole game down.
            LOGGER.error("Failed to register the Plasmo Voice addon; lip-sync is disabled.", t);
        }
    }

    /** True only if Figura and a supported Plasmo Voice version are both installed. */
    public static synchronized boolean isEnabled() {
        if (enabled == null) {
            enabled = computeEnabled();
        }
        return enabled;
    }

    private static boolean computeEnabled() {
        FabricLoader loader = FabricLoader.getInstance();

        if (!loader.isModLoaded("figura")) {
            LOGGER.info("Figura not installed - lip-sync integration skipped.");
            return false;
        }
        if (!loader.isModLoaded("plasmovoice")) {
            LOGGER.info("Plasmo Voice not installed - lip-sync integration skipped.");
            return false;
        }

        String installed = loader.getModContainer("plasmovoice")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        try {
            Version have = Version.parse(installed);
            Version need = Version.parse(MIN_PLASMO_VOICE);
            if (have.compareTo(need) < 0) {
                LOGGER.warn("Plasmo Voice {} is too old for DTIMC lip-sync (need >= {}). Integration skipped.",
                        installed, MIN_PLASMO_VOICE);
                return false;
            }
        } catch (Exception e) {
            // Unparseable version string: assume it is a modern build rather than disabling the feature.
            LOGGER.warn("Could not parse Plasmo Voice version '{}'; assuming it is supported.", installed);
        }
        return true;
    }
}
