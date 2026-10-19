package bloomery.dtimc.compat.plasmovoice;

import bloomery.dtimc.compat.lipsync.LipSyncBootstrap;
import bloomery.dtimc.compat.lipsync.LipSyncEvents;
import net.minecraft.client.MinecraftClient;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.connection.ServerConnection;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.connection.UdpClientPacketSendEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.proto.data.player.VoicePlayerInfo;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Plasmo Voice client addon: listens to the local microphone and feeds {@link PlasmoVoiceAPI}.
 *
 * <p>Ported from FigExtra's {@code PlasmoVoiceAddon} (MIT, (c) 2024 Furrc), updated for the
 * Plasmo Voice 2.1.1+ API:</p>
 * <ul>
 *   <li>{@code @InjectPlasmoVoice} replaces Guice's {@code @Inject}.</li>
 *   <li>{@code event.getProcessed().getMono()} replaces {@code getMonoSamplesProcessed()}.</li>
 * </ul>
 */
@Addon(
        id = "pv-addon-dtimc-lipsync",
        name = "DTIMC Figura Lip Sync",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        authors = {"Bloomery Network Team", "xPintoKunG_Z", "Furrc (FigExtra)"}
)
public class PlasmoVoiceAddon implements AddonInitializer {

    public static final PlasmoVoiceAddon INSTANCE = new PlasmoVoiceAddon();

    /** Filled in by Plasmo Voice before {@link #onAddonInitialize()} runs. */
    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    @Override
    public void onAddonInitialize() {
        LipSyncBootstrap.LOGGER.info("Plasmo Voice addon initialized.");
    }

    @Override
    public void onAddonShutdown() {
    }

    /**
     * Fired (on Plasmo Voice's audio thread) for every voice packet the local player sends.
     * Forwards it to the avatar as the {@code FIGEXTRA.PLASMOVOICE_PLAYER_SPEAK} Lua event.
     */
    @EventSubscribe
    public void onPlayerSpeak(@NotNull UdpClientPacketSendEvent event) {
        if (!(event.getPacket() instanceof PlayerAudioPacket audioPacket)) {
            return;
        }

        final String activationId = audioPacket.getActivationId().toString();
        final int distance = audioPacket.getDistance();

        // Figura's Lua runtime is NOT thread-safe. Hop onto the client thread before touching it.
        MinecraftClient.getInstance().execute(() -> {
            Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            if (avatar != null) {
                avatar.run(LipSyncEvents.PLAYER_SPEAK_KEY, avatar.tick, activationId, distance);
            }
        });
    }

    /** Fired (on the audio thread) for every processed microphone frame. */
    @EventSubscribe
    public void onCaptureProcessed(@NotNull AudioCaptureProcessedEvent event) {
        PlasmoVoiceAPI.handle(event.getProcessed().getMono());
    }

    public boolean isMuted(UUID player) {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) return false;

        Optional<ServerConnection> connection = client.getServerConnection();
        if (connection.isEmpty()) return false;

        Optional<VoicePlayerInfo> playerInfo = connection.get().getPlayerById(player);
        if (playerInfo.isEmpty()) return false;
        if (playerInfo.get().isMuted() || playerInfo.get().isMicrophoneMuted()) return true;

        if (Objects.equals(FiguraMod.getLocalPlayerUUID(), player)) {
            return client.getConfig().getVoice().getMicrophoneDisabled().value();
        }
        return false;
    }

    public String getPlasmoVoiceVersion() {
        PlasmoVoiceClient client = voiceClient;
        return client == null ? "unknown" : client.getVersion();
    }
}
