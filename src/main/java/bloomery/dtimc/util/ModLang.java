package bloomery.dtimc.devathip_interface.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * All mod text lives in assets/myskinmod/lang/<code>.json (en_us, th_th).
 * Setting "auto" follows the game language (Thai -> th_th, anything else -> en_us).
 */
public final class ModLang {
    private static Map<String, String> fallback = new HashMap<>();
    private static Map<String, String> current = new HashMap<>();
    private static String loadedCode = null;

    private ModLang() { }

    public static String resolveCode() {
        String pref = ModSettings.get().language;
        if ("en".equals(pref)) return "en_us";
        if ("th".equals(pref)) return "th_th";
        MinecraftClient c = MinecraftClient.getInstance();
        String gl = (c != null && c.options != null) ? c.options.language : null;
        if (gl != null && gl.toLowerCase(Locale.ROOT).startsWith("th")) return "th_th";
        return "en_us";
    }

    private static Map<String, String> read(String code) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = ModLang.class.getResourceAsStream("/assets/myskinmod/lang/" + code + ".json")) {
            if (in == null) return map;
            JsonObject obj = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            for (String k : obj.keySet()) map.put(k, obj.get(k).getAsString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    private static void ensureLoaded() {
        if (fallback.isEmpty()) fallback = read("en_us");
        String code = resolveCode();
        if (!code.equals(loadedCode)) {
            current = code.equals("en_us") ? fallback : read(code);
            loadedCode = code;
        }
    }

    public static String t(String key, Object... args) {
        ensureLoaded();
        String s = current.get(key);
        if (s == null) s = fallback.get(key);
        if (s == null) return key;
        if (args.length > 0) {
            try {
                return String.format(s, args);
            } catch (Exception e) {
                return s;
            }
        }
        return s;
    }

    /** pref: "auto", "en" or "th" */
    public static void setLanguage(String pref) {
        ModSettings.get().language = pref;
        ModSettings.save();
        loadedCode = null;
    }

    public static String preference() {
        return ModSettings.get().language;
    }
}
