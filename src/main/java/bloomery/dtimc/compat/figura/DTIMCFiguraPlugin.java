package bloomery.dtimc.compat.figura;

import bloomery.dtimc.compat.lipsync.LipSyncBootstrap;
import bloomery.dtimc.compat.plasmovoice.PlasmoVoiceAPI;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.entries.FiguraAPI;
import org.figuramc.figura.lua.LuaWhitelist;

import java.util.Collection;
import java.util.List;

import static org.figuramc.figura.lua.FiguraAPIManager.API_GETTERS;
import static org.figuramc.figura.lua.FiguraAPIManager.WHITELISTED_CLASSES;

/**
 * Figura API plugin (Fabric entrypoint "figura_api"). Exposes the {@code plasmovoice} global to avatar
 * scripts when Plasmo Voice is present.
 *
 * <p>Ported from FigExtra's {@code FigExtraPlugin}. Figura only loads this class when Figura itself is
 * installed, so it is safe for it to reference Figura types.</p>
 */
@LuaWhitelist
public class DTIMCFiguraPlugin implements FiguraAPI {

    /** Name of the (otherwise empty) global this plugin registers. */
    public static final String PLUGIN_ID = "dtimc";

    /** Instantiated once by Figura through the "figura_api" entrypoint. */
    public DTIMCFiguraPlugin() {
        // Same pattern FigExtra uses: add our class + a global getter straight into Figura's tables.
        if (LipSyncBootstrap.isEnabled()) {
            WHITELISTED_CLASSES.add(PlasmoVoiceAPI.class);
            API_GETTERS.put("plasmovoice", runtime -> new PlasmoVoiceAPI());
        }
    }

    /** Per-avatar instance created by {@link #build(Avatar)}; must not re-register anything. */
    private DTIMCFiguraPlugin(Avatar avatar) {
    }

    @Override
    public FiguraAPI build(Avatar avatar) {
        return new DTIMCFiguraPlugin(avatar);
    }

    @Override
    public String getName() {
        return PLUGIN_ID;
    }

    @Override
    public Collection<Class<?>> getWhitelistedClasses() {
        return List.of(DTIMCFiguraPlugin.class);
    }

    @Override
    public Collection<Class<?>> getDocsClasses() {
        return List.of();
    }

    @Override
    public String toString() {
        return "DTIMCFiguraPlugin";
    }
}
