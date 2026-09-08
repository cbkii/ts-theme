package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

final class LauncherPrefs {
    static final String KEY_NAV = "app.navigation";
    static final String KEY_RADIO = "app.radio";
    static final String KEY_BLUETOOTH = "app.bluetooth";
    static final String KEY_MUSIC = "app.music";
    static final String KEY_MAP_ENABLED = "map.enabled";

    private static final String FILE = "ts18_launcher";

    private LauncherPrefs() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String packageFor(Context context, String key) {
        return prefs(context).getString(key, "");
    }

    static void setPackage(Context context, String key, String packageName) {
        prefs(context).edit().putString(key, packageName == null ? "" : packageName).apply();
    }

    static boolean mapEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MAP_ENABLED, true);
    }

    static void setMapEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MAP_ENABLED, enabled).apply();
    }
}
