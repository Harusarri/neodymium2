package kr.harusarri.neodymium2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kr.harusarri.neodymium2.core.Settings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side localization loader and resolver for Neodymium 2.
 * Loads {@code /assets/neodymium2/lang/en_us.json} and {@code /assets/neodymium2/lang/ko_kr.json},
 * resolving player client locale ({@code ko_*} -> Korean, otherwise English) or explicit admin override.
 */
public final class NeodymiumI18n {
    public static final String EN_US = "en_us";
    public static final String KO_KR = "ko_kr";
    public static final String AUTO = "auto";

    private static final Map<String, String> EN = loadJson("/assets/neodymium2/lang/en_us.json");
    private static final Map<String, String> KO = loadJson("/assets/neodymium2/lang/ko_kr.json");
    private static volatile String mode = normalizeMode(System.getProperty("neodymium2.lang", AUTO));

    private NeodymiumI18n() {}

    public static String mode() {
        return mode;
    }

    public static boolean setMode(String newMode) {
        if (newMode == null) return false;
        String m = newMode.trim().toLowerCase(Locale.ROOT);
        switch (m) {
            case "auto" -> { mode = AUTO; return true; }
            case "en", "en_us", "english" -> { mode = EN_US; return true; }
            case "ko", "ko_kr", "kr", "korean" -> { mode = KO_KR; return true; }
            default -> { return false; }
        }
    }

    private static String normalizeMode(String raw) {
        if (raw == null) return AUTO;
        String m = raw.trim().toLowerCase(Locale.ROOT);
        if (m.startsWith("en")) return EN_US;
        if (m.startsWith("ko") || m.equals("kr")) return KO_KR;
        return AUTO;
    }

    public static String resolveLang(CommandSourceStack source) {
        if (source != null) {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                return resolveLang(player);
            }
        }
        String currentMode = mode;
        if (EN_US.equals(currentMode) || KO_KR.equals(currentMode)) {
            return currentMode;
        }
        String defaultLang = Locale.getDefault().getLanguage();
        return defaultLang != null && defaultLang.toLowerCase(Locale.ROOT).startsWith("ko") ? KO_KR : EN_US;
    }

    public static String resolveLang(ServerPlayer player) {
        String currentMode = mode;
        if (EN_US.equals(currentMode) || KO_KR.equals(currentMode)) {
            return currentMode;
        }
        if (player != null) {
            try {
                var info = player.clientInformation();
                if (info != null && info.language() != null && !info.language().isBlank()) {
                    return info.language().toLowerCase(Locale.ROOT).startsWith("ko") ? KO_KR : EN_US;
                }
            } catch (Throwable ignored) {
            }
        }
        String defaultLang = Locale.getDefault().getLanguage();
        return defaultLang != null && defaultLang.toLowerCase(Locale.ROOT).startsWith("ko") ? KO_KR : EN_US;
    }

    public static String getByLang(String lang, String key) {
        Map<String, String> table = KO_KR.equalsIgnoreCase(lang) ? KO : EN;
        String val = table.get(key);
        if (val != null) return val;
        String fallback = EN.get(key);
        return fallback != null ? fallback : key;
    }

    public static String trByLang(String lang, String key, Object... args) {
        String pattern = getByLang(lang, key);
        if (args == null || args.length == 0) return pattern;
        return String.format(Locale.US, pattern, args);
    }

    public static Map<String, String> englishMap() {
        return EN;
    }

    public static Map<String, String> koreanMap() {
        return KO;
    }

    private static Map<String, String> loadJson(String resourcePath) {
        try (InputStream in = NeodymiumI18n.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing language resource: " + resourcePath);
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = Settings.JSON.fromJson(raw, JsonObject.class);
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
            return Collections.unmodifiableMap(map);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
