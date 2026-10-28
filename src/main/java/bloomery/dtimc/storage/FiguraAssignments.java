package bloomery.dtimc.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Which Figura avatar goes with which skin / folder. Stored in config/myskinmod_figura.json.
 * A skin's own avatar wins over its folder's avatar.
 */
public final class FiguraAssignments {
    public enum Source { NONE, SKIN, FOLDER }

    public record Resolved(String avatarId, Source source, String folder) { }

    private static final Map<String, String> skins = new HashMap<>();
    private static final Map<String, String> folders = new HashMap<>();
    private static boolean loaded = false;

    private FiguraAssignments() { }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("myskinmod_figura.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            JsonObject root = new Gson().fromJson(Files.readString(p), JsonObject.class);
            if (root == null) return;
            if (root.has("skins")) for (String k : root.getAsJsonObject("skins").keySet()) skins.put(k, root.getAsJsonObject("skins").get(k).getAsString());
            if (root.has("folders")) for (String k : root.getAsJsonObject("folders").keySet()) folders.put(k, root.getAsJsonObject("folders").get(k).getAsString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject s = new JsonObject(), f = new JsonObject();
            skins.forEach(s::addProperty);
            folders.forEach(f::addProperty);
            root.add("skins", s);
            root.add("folders", f);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized String forSkin(String skinId) {
        ensureLoaded();
        return skins.get(skinId);
    }

    public static synchronized String forFolder(String folder) {
        ensureLoaded();
        return folders.get(folder);
    }

    /** avatarId == null clears the assignment. */
    public static synchronized void setSkin(String skinId, String avatarId) {
        ensureLoaded();
        if (avatarId == null) skins.remove(skinId);
        else skins.put(skinId, avatarId);
        save();
    }

    public static synchronized void setFolder(String folder, String avatarId) {
        ensureLoaded();
        if (avatarId == null) folders.remove(folder);
        else folders.put(folder, avatarId);
        save();
    }

    public static synchronized void removeSkin(String skinId) {
        ensureLoaded();
        if (skins.remove(skinId) != null) save();
    }

    public static synchronized void removeFolder(String folder) {
        ensureLoaded();
        if (folders.remove(folder) != null) save();
    }

    public static synchronized void renameFolder(String oldName, String newName) {
        ensureLoaded();
        String v = folders.remove(oldName);
        if (v != null) {
            folders.put(newName, v);
            save();
        }
    }

    /** category = the skin's folder name ("All" when it has none). */
    public static synchronized Resolved resolve(String skinId, String category) {
        ensureLoaded();
        String own = skins.get(skinId);
        if (own != null) return new Resolved(own, Source.SKIN, null);
        if (category != null) {
            String viaFolder = folders.get(category);
            if (viaFolder != null) return new Resolved(viaFolder, Source.FOLDER, category);
        }
        return new Resolved(null, Source.NONE, null);
    }
}
