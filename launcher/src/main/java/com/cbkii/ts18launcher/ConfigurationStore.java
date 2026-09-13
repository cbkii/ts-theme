package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies only configuration keys in one preference transaction. HOME/package state is separate. */
final class ConfigurationStore {
    private ConfigurationStore() {}
    static Map<String, Object> read(Context context) {
        SharedPreferences prefs = LauncherPrefs.prefs(context);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigurationCodec.Type> entry : ConfigurationCodec.KEYS.entrySet()) {
            String key = entry.getKey();
            if (!prefs.contains(key)) continue;
            switch (entry.getValue()) {
                case BOOLEAN: result.put(key, prefs.getBoolean(key, false)); break;
                case COUNT: case MINUTES: result.put(key, prefs.getInt(key, 0)); break;
                default: result.put(key, prefs.getString(key, "")); break;
            }
        }
        return result;
    }
    static synchronized boolean replace(Context context, Map<String, Object> values) {
        Map<String, Object> previous = read(context);
        if (edit(context, values).commit()) return true;
        // commit() changes in-memory preferences even if storage fails: restore that snapshot too.
        edit(context, previous).commit();
        return false;
    }
    private static SharedPreferences.Editor edit(Context context, Map<String, Object> values) {
        SharedPreferences.Editor editor = LauncherPrefs.prefs(context).edit();
        for (String key : ConfigurationCodec.KEYS.keySet()) editor.remove(key);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!ConfigurationCodec.KEYS.containsKey(entry.getKey())) continue;
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
        }
        return editor;
    }
}
