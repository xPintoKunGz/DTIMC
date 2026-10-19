package bloomery.dtimc.compat.plasmovoice;

import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static bloomery.dtimc.compat.plasmovoice.PlasmoVoiceAddon.INSTANCE;

/**
 * The {@code plasmovoice} global exposed to Figura avatar scripts.
 *
 * <pre>
 * local level = plasmovoice.getVoiceLevel(avatar:getUUID())   -- 0.0 .. 1.0
 * </pre>
 *
 * <p>Ported from FigExtra's {@code PlasmoVoiceAPI} (MIT, (c) 2024 Furrc). Method names are unchanged
 * so existing FigExtra scripts keep working.</p>
 *
 * <p>Differences from the original:</p>
 * <ul>
 *   <li>Level is stored in two volatile fields instead of a Guava cache, so the audio thread never has to
 *       look up the local player's UUID (it only exists on the client thread).</li>
 *   <li>The RMS window divisor is the real window length (the original used {@code end / 2}, which made
 *       levels depend on where in the frame a window sat). Results differ by at most a few percent.</li>
 *   <li>{@code renderDistanceVisualizer} was not ported (its {@code Pos3d} type moved between Plasmo Voice
 *       versions).</li>
 * </ul>
 *
 * <p>Only the <b>local</b> player's microphone is measured; any other UUID returns {@code 0}. To show the
 * mouth moving for other players, the host script must broadcast it with a Figura ping.</p>
 */
@LuaWhitelist
@LuaTypeDoc(
        name = "PlasmoVoice",
        value = "plasmo_voice"
)
public class PlasmoVoiceAPI {

    /** Levels older than this are treated as silence (the mic stopped delivering frames). */
    private static final long EXPIRY_NANOS = TimeUnit.SECONDS.toNanos(1);

    /** RMS is measured over windows of this many samples; the loudest window wins. */
    private static final int WINDOW = 100;

    private static volatile float localLevel = 0f;
    private static volatile long localLevelNanos = 0L;

    /** Called from Plasmo Voice's audio thread with the processed mono microphone frame. */
    public static void handle(short[] data) {
        localLevel = (data == null || data.length == 0) ? 0f : calcVoiceLevel(data);
        localLevelNanos = System.nanoTime();
    }

    private static float calcVoiceLevel(short[] data) {
        return (float) dbToPerc(getHighestAudioLevel(data));
    }

    /** Loudness in dB (-127 .. 0) of samples[offset, end). */
    private static double calculateAudioLevel(short[] samples, int offset, int end) {
        int sampleCount = end - offset;
        if (sampleCount <= 0) return -127D;

        double sumSquares = 0D;
        for (int i = offset; i < end; i++) {
            double sample = (double) samples[i] / (double) Short.MAX_VALUE;
            sumSquares += sample * sample;
        }

        double rms = Math.sqrt(sumSquares / sampleCount);
        return rms > 0D ? Math.min(Math.max(20D * Math.log10(rms), -127D), 0D) : -127D;
    }

    private static double getHighestAudioLevel(short[] samples) {
        double highest = -127D;
        for (int i = 0; i < samples.length; i += WINDOW) {
            double level = calculateAudioLevel(samples, i, Math.min(i + WINDOW, samples.length));
            if (level > highest) {
                highest = level;
            }
        }
        return highest;
    }

    private static double dbToPerc(double db) {
        return (db + 127D) / 127D;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "uuid"
            ),
            value = "plasmo_voice.get_voice_level"
    )
    public static float getVoiceLevel(String uuid) {
        try {
            if (!UUID.fromString(uuid).equals(FiguraMod.getLocalPlayerUUID())) {
                return 0f;
            }
            if (System.nanoTime() - localLevelNanos > EXPIRY_NANOS) {
                return 0f;
            }
            return localLevel;
        } catch (Exception e) {
            return 0f;
        }
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "uuid"
            ),
            value = "plasmo_voice.is_muted"
    )
    public static boolean isMuted(String uuid) {
        try {
            return INSTANCE.isMuted(UUID.fromString(uuid));
        } catch (Exception e) {
            return false;
        }
    }

    @LuaWhitelist
    @LuaMethodDoc("plasmo_voice.get_version")
    public String getVersion() {
        return INSTANCE.getPlasmoVoiceVersion();
    }

    @Override
    public String toString() {
        return "PlasmoVoiceAPI";
    }
}
