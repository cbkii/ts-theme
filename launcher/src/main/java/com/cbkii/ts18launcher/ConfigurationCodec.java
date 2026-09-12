package com.cbkii.ts18launcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, whitelisted configuration. No preferences dump, evidence, cache or location. */
final class ConfigurationCodec {
    static final String SCHEMA = "com.cbkii.ts18launcher.config";
    static final int VERSION = 1;
    static final int MAX_BYTES = 64 * 1024;
    interface Packages { boolean available(String name); }
    enum Type { PACKAGE, ROLE, ICON, HUE, BOOLEAN, COUNT, MINUTES, RAIL, SIDE, MEDIA, APPEARANCE, AUTO_SOURCE }
    static final Map<String, Type> KEYS;
    static {
        Map<String, Type> keys = new LinkedHashMap<>();
        for (String key : new String[] {LauncherPrefs.KEY_NAV, LauncherPrefs.KEY_RADIO,
                LauncherPrefs.KEY_MUSIC, LauncherPrefs.KEY_BLUETOOTH}) keys.put(key, Type.PACKAGE);
        for (String key : LauncherPrefs.QUICK_KEYS) keys.put(key, Type.PACKAGE);
        for (String key : LauncherPrefs.DRAWER_QUICK_KEYS) keys.put(key, Type.PACKAGE);
        for (String key : LauncherPrefs.QUICK_ROLE_KEYS) keys.put(key, Type.ROLE);
        for (String key : LauncherPrefs.DRAWER_ROLE_KEYS) keys.put(key, Type.ROLE);
        for (String key : UiPersonalizationPrefs.QUICK_ICON_KEYS) keys.put(key, Type.ICON);
        for (String key : UiPersonalizationPrefs.DRAWER_ICON_KEYS) keys.put(key, Type.ICON);
        keys.put(UiPersonalizationPrefs.KEY_ACCENT_HUE, Type.HUE);
        keys.put(UiPersonalizationPrefs.KEY_MEDIA_STARTUP_WARMUP, Type.BOOLEAN);
        keys.put(UiPersonalizationPrefs.KEY_HOME_SHORTCUTS_ENABLED, Type.BOOLEAN);
        keys.put(LauncherPrefs.KEY_QUICK_COUNT, Type.COUNT);
        keys.put(LauncherPrefs.KEY_RAIL_POSITION, Type.RAIL);
        keys.put(LauncherPrefs.KEY_RADIO_SIDE, Type.SIDE);
        keys.put(LauncherPrefs.KEY_MAP_ENABLED, Type.BOOLEAN);
        keys.put(LauncherPrefs.KEY_MAP_CONTROLS_ENABLED, Type.BOOLEAN);
        keys.put(LauncherPrefs.KEY_MEDIA_MODE, Type.MEDIA);
        keys.put(LauncherPrefs.KEY_APPEARANCE_MODE, Type.APPEARANCE);
        keys.put(LauncherPrefs.KEY_APPEARANCE_AUTO_SOURCE, Type.AUTO_SOURCE);
        keys.put(LauncherPrefs.KEY_APPEARANCE_DAY_START, Type.MINUTES);
        keys.put(LauncherPrefs.KEY_APPEARANCE_NIGHT_START, Type.MINUTES);
        KEYS = Collections.unmodifiableMap(keys);
    }

    static final class Preview {
        final Map<String, Object> values;
        final List<String> unavailable;
        Preview(Map<String, Object> values, List<String> unavailable) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
        }
        String summary() {
            StringBuilder text = new StringBuilder("Replace launcher configuration with ")
                    .append(values.size()).append(" settings. Omitted settings return to defaults.\n\n");
            for (Map.Entry<String, Object> entry : values.entrySet())
                text.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
            if (!unavailable.isEmpty()) text.append("\nUnavailable apps ignored (defaults restored):\n")
                    .append(String.join("\n", unavailable));
            return text.toString();
        }
    }

    static String encode(Map<String, Object> values) throws JSONException {
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!KEYS.containsKey(entry.getKey())) continue;
            validate(entry.getKey(), entry.getValue());
            settings.put(entry.getKey(), entry.getValue());
        }
        return new JSONObject().put("schema", SCHEMA).put("version", VERSION)
                .put("settings", settings).toString(2);
    }

    static Preview decode(String json, Packages packages) throws JSONException {
        if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new JSONException("Configuration exceeds 64 KiB");
        JSONObject document = new JSONObject(json);
        if (document.length() != 3 || !SCHEMA.equals(document.opt("schema"))
                || !(document.opt("version") instanceof Integer) || document.getInt("version") != VERSION
                || !(document.opt("settings") instanceof JSONObject))
            throw new JSONException("Unsupported configuration schema/version");
        JSONObject settings = document.getJSONObject("settings");
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        Iterator<String> names = settings.keys();
        while (names.hasNext()) {
            String key = names.next();
            Object value = settings.get(key);
            validate(key, value);
            if (KEYS.get(key) == Type.PACKAGE) {
                String name = (String) value;
                if (name.isEmpty()) continue;
                if (!packages.available(name)) { unavailable.add(key + ": " + name); continue; }
            }
            values.put(key, value);
        }
        Object day = values.get(LauncherPrefs.KEY_APPEARANCE_DAY_START);
        Object night = values.get(LauncherPrefs.KEY_APPEARANCE_NIGHT_START);
        if (day instanceof Integer && (Integer) day >= 0 && day.equals(night))
            throw new JSONException("Day and night anchors must differ");
        return new Preview(values, unavailable);
    }

    private static void validate(String key, Object value) throws JSONException {
        Type type = KEYS.get(key);
        if (type == null) throw new JSONException("Unknown setting: " + key);
        if (type == Type.BOOLEAN && value instanceof Boolean) return;
        if (type == Type.COUNT && value instanceof Integer && (Integer) value >= 3 && (Integer) value <= 6) return;
        if (type == Type.MINUTES && value instanceof Integer && (Integer) value >= -1 && (Integer) value < 1440) return;
        if (value instanceof String) {
            String text = (String) value;
            if (text.length() > 255) throw new JSONException("Setting too long: " + key);
            if (type == Type.PACKAGE && (text.isEmpty() || text.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) return;
            if (type == Type.ROLE && RoleIconCatalog.isKnown(text)) return;
            if (type == Type.ICON && SlotIconCatalog.isKnown(text)) return;
            if (type == Type.HUE && AccentPalette.isKnown(text)) return;
            if (type == Type.SIDE && oneOf(text, "left", "right")) return;
            if (type == Type.RAIL && oneOf(text, "left", "right", "driver")) return;
            if (type == Type.MEDIA && oneOf(text, "auto", "prefer_music")) return;
            if (type == Type.APPEARANCE && oneOf(text, "auto", "day", "high_contrast", "dim", "night")) return;
            if (type == Type.AUTO_SOURCE && oneOf(text, "sensor", "schedule")) return;
        }
        throw new JSONException("Invalid value for " + key);
    }

    private static boolean oneOf(String value, String... options) {
        for (String option : options) if (option.equals(value)) return true;
        return false;
    }
}
