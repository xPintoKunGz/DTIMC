package bloomery.dtimc.compat.lipsync;

import com.mojang.datafixers.util.Pair;
import org.figuramc.figura.entries.FiguraEvent;
import org.figuramc.figura.lua.api.event.LuaEvent;

import java.util.Collection;
import java.util.List;

/**
 * Registers the custom Lua event that avatars can listen to:
 *
 * <pre>
 * events["FIGEXTRA.PLASMOVOICE_PLAYER_SPEAK"]:register(function(activationId, distance) ... end)
 * </pre>
 *
 * <p>The id ("figextra") and event name are kept identical to the original FigExtra so avatar scripts
 * written for FigExtra keep working unchanged.</p>
 *
 * <p>Figura raises a script error if code fires an event that was never registered, so this entrypoint
 * is required even though Figura itself never calls the event.</p>
 */
public class LipSyncEvents implements FiguraEvent {

    public static final String ID = "figextra";
    public static final String PLAYER_SPEAK = "plasmovoice_player_speak";

    /** Key used to fire the event: {@code <ID>.<NAME>}, upper-cased (see Figura's EventsAPI). */
    public static final String PLAYER_SPEAK_KEY = (ID + "." + PLAYER_SPEAK).toUpperCase(java.util.Locale.US);

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public Collection<Pair<String, LuaEvent>> getEvents() {
        // Figura calls this once per avatar runtime. A NEW LuaEvent each time is essential:
        // sharing one instance would make every avatar's handlers fire for every other avatar.
        return List.of(Pair.of(PLAYER_SPEAK, new LuaEvent()));
    }
}
