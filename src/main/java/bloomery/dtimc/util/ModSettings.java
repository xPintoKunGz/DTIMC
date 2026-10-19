package bloomery.dtimc.devathip_interface.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;

/** User options stored in config/myskinmod/settings.json */
public final class ModSettings {
    /** "auto" (follow the game language), "en" or "th" */
    public String language = "auto";
    public boolean streamerMode = false;
    /** false = the user pressed "don't show again" */
    public boolean showUpdatePopup = true;
    public String lastSeenVersion = "";
    /** true = the Streamer Mode file-window warning is not shown any more */
    public boolean hideBrowseWarning = false;
    /** lets us switch things on once when a new settings version arrives */
    public int settingsVersion = 0;

    private static ModSettings instance;

    private static Path path() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/myskinmod/settings.json");
    }

    public static ModSettings get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ModSettings load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                ModSettings s = new Gson().fromJson(Files.readString(p), ModSettings.class);
                if (s != null) {
                    if (s.language == null) s.language = "auto";
                    if (s.lastSeenVersion == null) s.lastSeenVersion = "";
                    migrate(s);
                    return s;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ModSettings fresh = new ModSettings();
        migrate(fresh);
        return fresh;
    }

    /** Version 2: update announcements are switched ON once; after that the player's choice is respected. */
    private static void migrate(ModSettings s) {
        if (s.settingsVersion < 2) {
            s.showUpdatePopup = true;
            s.settingsVersion = 2;
            instance = s;
            save();
        }
    }

    public static void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(get()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ModSettings() { }
}
