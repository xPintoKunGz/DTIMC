package bloomery.dtimc.compat.figura;

import bloomery.dtimc.util.ModLang;
import bloomery.dtimc.util.ModSettings;
import bloomery.dtimc.util.ModToast;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Talks to Figura WITHOUT compiling against it: everything goes through reflection, so the mod keeps working
 * (and simply hides the Figura options) when Figura is missing or a different version is installed.
 *
 * Avatars are the folders inside  .minecraft/figura/avatars  (a folder that contains avatar.json / avatar.jsonc).
 */
public final class FiguraBridge {
    public record Avatar(String id, String name, Path path) { }

    private static boolean workerRunning = false;

    private FiguraBridge() { }

    public static boolean isInstalled() {
        return FabricLoader.getInstance().isModLoaded("figura");
    }

    public static Path avatarsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("figura").resolve("avatars");
    }

    // ------------------------------------------------------------------ listing
    public static List<Avatar> listAvatars() {
        List<Avatar> result = new ArrayList<>();
        Path root = avatarsDir();
        if (!Files.isDirectory(root)) return result;
        scan(root, root, 0, result);
        result.sort(Comparator.comparing(a -> a.name().toLowerCase()));
        return result;
    }

    private static void scan(Path root, Path dir, int depth, List<Avatar> out) {
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : (Iterable<Path>) children.sorted()::iterator) {
                if (!Files.isDirectory(child)) continue;
                if (isAvatarFolder(child)) {
                    String id = root.relativize(child).toString().replace('\\', '/');
                    out.add(new Avatar(id, readName(child), child));
                } else if (depth < 2) { // Figura lets avatars live inside plain folders
                    scan(root, child, depth + 1, out);
                }
            }
        } catch (Exception ignored) { }
    }

    private static boolean isAvatarFolder(Path dir) {
        return Files.isRegularFile(dir.resolve("avatar.json")) || Files.isRegularFile(dir.resolve("avatar.jsonc"));
    }

    private static String readName(Path dir) {
        String fallback = dir.getFileName().toString();
        try {
            Path meta = Files.isRegularFile(dir.resolve("avatar.jsonc")) ? dir.resolve("avatar.jsonc") : dir.resolve("avatar.json");
            JsonReader reader = new JsonReader(new StringReader(Files.readString(meta)));
            reader.setLenient(true); // accepts the comments allowed in avatar.jsonc
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("name") && !obj.get("name").getAsString().isBlank()) return obj.get("name").getAsString();
        } catch (Exception ignored) { }
        return fallback;
    }

    public static Avatar find(String id) {
        if (id == null) return null;
        for (Avatar a : listAvatars()) if (a.id().equals(id)) return a;
        return null;
    }

    /** Display name for an avatar id ("missing" if the folder is gone). */
    public static String displayName(String id) {
        if (id == null) return ModLang.t("figura.none");
        Avatar a = find(id);
        return a != null ? a.name() : ModLang.t("figura.missing", id);
    }

    // ------------------------------------------------------------------ load + upload
    /**
     * Loads the avatar for the local player and uploads it to the Figura backend once it has finished loading.
     * Safe to call from any thread; does nothing (quietly) if Figura is missing or the setting is off.
     */
    public static void loadAndUpload(String avatarId) {
        if (avatarId == null || !isInstalled() || !ModSettings.get().figuraAutoLoad) return;
        Avatar avatar = find(avatarId);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (avatar == null) {
            ModToast.warning(ModLang.t("figura.toast.title"), ModLang.t("figura.toast.failed", avatarId));
            return;
        }
        mc.execute(() -> {
            try {
                Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager");
                manager.getMethod("loadLocalAvatar", Path.class).invoke(null, avatar.path());
            } catch (Throwable t) {
                t.printStackTrace();
                ModToast.error(ModLang.t("figura.toast.title"), ModLang.t("figura.toast.api"));
                return;
            }
            startUploadWorker(avatar);
        });
    }

    private static synchronized void startUploadWorker(Avatar avatar) {
        if (workerRunning) return; // a newer call simply replaces the avatar; the waiting worker handles the latest one
        workerRunning = true;
        Thread th = new Thread(() -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                for (int i = 0; i < 120; i++) { // up to ~30 s for Figura to compile the avatar
                    Thread.sleep(250);
                    final Object[] holder = new Object[1];
                    mc.executeSync(() -> holder[0] = readyAvatarObject());
                    if (holder[0] != null) {
                        mc.execute(() -> upload(holder[0], avatar));
                        return;
                    }
                }
                ModToast.warning(ModLang.t("figura.toast.title"), ModLang.t("figura.toast.failed", avatar.name()));
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                workerRunning = false;
            }
        }, "dtimc-figura-upload");
        th.setDaemon(true);
        th.start();
    }

    /** The local player's Figura Avatar object once it has finished loading, otherwise null. */
    private static Object readyAvatarObject() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return null;
            UUID id = mc.player.getUuid();
            Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager");
            Object avatar = manager.getMethod("getAvatarForPlayer", UUID.class).invoke(null, id);
            if (avatar == null) return null;
            Field loaded = avatar.getClass().getField("loaded");
            Field nbt = avatar.getClass().getField("nbt");
            if (loaded.getBoolean(avatar) && nbt.get(avatar) != null) return avatar;
        } catch (Throwable ignored) { }
        return null;
    }

    private static void upload(Object avatarObject, Avatar avatar) {
        try {
            Class<?> net = Class.forName("org.figuramc.figura.backend2.NetworkStuff");
            boolean connected = (boolean) net.getMethod("isConnected").invoke(null);
            if (!connected) {
                ModToast.info(ModLang.t("figura.toast.title"), ModLang.t("figura.toast.local_only", avatar.name()));
                return;
            }
            Method upload = null;
            for (Method m : net.getMethods()) {
                if (m.getName().equals("uploadAvatar") && m.getParameterCount() == 1) upload = m;
            }
            if (upload == null) throw new NoSuchMethodException("NetworkStuff.uploadAvatar");
            upload.invoke(null, avatarObject);
            ModToast.success(ModLang.t("figura.toast.title"), ModLang.t("figura.toast.uploaded", avatar.name()));
        } catch (Throwable t) {
            t.printStackTrace();
            ModToast.error(ModLang.t("figura.toast.title"), ModLang.t("figura.toast.api"));
        }
    }
}
