package bloomery.dtimc.util;

import java.util.List;

/** Release notes. The text of every line is a key in assets/myskinmod/lang/*.json */
public final class Changelog {
    /** Bump this on every release: the update popup appears once per new version. */
    public static final String CURRENT_VERSION = "1.2.3";

    public record Release(String version, String date, List<String> keys) { }

    public static final List<Release> RELEASES = List.of(
            new Release("1.2.3", "2026-10-20", List.of(
                    "changelog.1_2_3.1", "changelog.1_2_3.2", "changelog.1_2_3.3", "changelog.1_2_3.4",
                    "changelog.1_2_3.5", "changelog.1_2_3.6", "changelog.1_2_3.7")),
            new Release("1.2.2", "2026-10-13", List.of(
                    "changelog.1_2_2.1", "changelog.1_2_2.2", "changelog.1_2_2.3", "changelog.1_2_2.4", "changelog.1_2_2.5")),
            new Release("1.2.1", "2026-10-13", List.of(
                    "changelog.1_2_1.1", "changelog.1_2_1.2", "changelog.1_2_1.3",
                    "changelog.1_2_1.4", "changelog.1_2_1.5", "changelog.1_2_1.6")),
            new Release("1.2.0", "2026-10-12", List.of(
                    "changelog.1_2_0.1", "changelog.1_2_0.2", "changelog.1_2_0.3", "changelog.1_2_0.4",
                    "changelog.1_2_0.5", "changelog.1_2_0.6", "changelog.1_2_0.7", "changelog.1_2_0.8")),
            new Release("1.1.0", "", List.of("changelog.1_1_0.1"))
    );

    private Changelog() { }

    public static Release latest() {
        return RELEASES.get(0);
    }

    /** True on the first launch ever and after every update, unless the user opted out. */
    public static boolean shouldAnnounce() {
        ModSettings s = ModSettings.get();
        return s.showUpdatePopup && !CURRENT_VERSION.equals(s.lastSeenVersion);
    }

    public static void markSeen() {
        ModSettings.get().lastSeenVersion = CURRENT_VERSION;
        ModSettings.save();
    }
}
