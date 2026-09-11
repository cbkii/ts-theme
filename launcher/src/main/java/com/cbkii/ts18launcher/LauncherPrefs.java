package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

final class LauncherPrefs {
    static final String KEY_NAV = "app.navigation";
    static final String KEY_RADIO = "app.radio";
    static final String KEY_BLUETOOTH = "app.bluetooth";
    static final String KEY_MUSIC = "app.music";
    static final String KEY_QUICK_1 = "app.quick.1";
    static final String KEY_QUICK_2 = "app.quick.2";
    static final String KEY_QUICK_3 = "app.quick.3";
    static final String KEY_QUICK_4 = "app.quick.4";
    static final String KEY_QUICK_5 = "app.quick.5";
    static final String KEY_QUICK_6 = "app.quick.6";
    static final String KEY_DRAWER_QUICK_1 = "app.drawer.quick.1";
    static final String KEY_DRAWER_QUICK_2 = "app.drawer.quick.2";
    static final String KEY_DRAWER_QUICK_3 = "app.drawer.quick.3";
    static final String KEY_DRAWER_QUICK_4 = "app.drawer.quick.4";
    static final String KEY_DRAWER_QUICK_5 = "app.drawer.quick.5";
    static final String KEY_QUICK_COUNT = "ui.quick.count";
    static final String KEY_RAIL_POSITION = "ui.rail.position";
    static final String KEY_MAP_ENABLED = "map.enabled";
    static final String KEY_MAP_CONTROLS_ENABLED = "map.controls.enabled";
    static final String KEY_MAP_APPEARANCE = "map.appearance";
    static final String KEY_MEDIA_MODE = "media.selection.mode";

    static final String MEDIA_MODE_AUTO = "auto";
    static final String MEDIA_MODE_PREFER_MUSIC = "prefer_music";

    static final String RAIL_DRIVER = "driver";
    static final String RAIL_LEFT = "left";
    static final String RAIL_RIGHT = "right";

    static final String MAP_APPEARANCE_AUTO = "auto";
    static final String MAP_APPEARANCE_NORMAL = "normal";
    static final String MAP_APPEARANCE_DIM = "dim";

    static final String[] QUICK_KEYS = {
            KEY_QUICK_1, KEY_QUICK_2, KEY_QUICK_3,
            KEY_QUICK_4, KEY_QUICK_5, KEY_QUICK_6
    };

    static final String[] DRAWER_QUICK_KEYS = {
            KEY_DRAWER_QUICK_1, KEY_DRAWER_QUICK_2, KEY_DRAWER_QUICK_3,
            KEY_DRAWER_QUICK_4, KEY_DRAWER_QUICK_5
    };

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

    static String mediaMode(Context context) {
        String value = prefs(context).getString(KEY_MEDIA_MODE, MEDIA_MODE_AUTO);
        return MEDIA_MODE_PREFER_MUSIC.equals(value) ? MEDIA_MODE_PREFER_MUSIC : MEDIA_MODE_AUTO;
    }

    static void setMediaMode(Context context, String mode) {
        String safe = MEDIA_MODE_PREFER_MUSIC.equals(mode)
                ? MEDIA_MODE_PREFER_MUSIC : MEDIA_MODE_AUTO;
        prefs(context).edit().putString(KEY_MEDIA_MODE, safe).apply();
    }

    static int quickCount(Context context) {
        int value = prefs(context).getInt(KEY_QUICK_COUNT, 4);
        return Math.max(3, Math.min(6, value));
    }

    static void setQuickCount(Context context, int count) {
        prefs(context).edit().putInt(KEY_QUICK_COUNT, Math.max(3, Math.min(6, count))).apply();
    }

    static String railPosition(Context context) {
        String value = prefs(context).getString(KEY_RAIL_POSITION, RAIL_DRIVER);
        if (RAIL_LEFT.equals(value) || RAIL_RIGHT.equals(value)) return value;
        return RAIL_DRIVER;
    }

    static void setRailPosition(Context context, String value) {
        String safe = RAIL_DRIVER;
        if (RAIL_LEFT.equals(value) || RAIL_RIGHT.equals(value)) safe = value;
        prefs(context).edit().putString(KEY_RAIL_POSITION, safe).apply();
    }

    /** This exact user-owned Australian TS18 is right-hand-drive; Driver side therefore means right. */
    static boolean railOnRight(Context context) {
        String value = railPosition(context);
        return RAIL_DRIVER.equals(value) || RAIL_RIGHT.equals(value);
    }

    static boolean mapEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MAP_ENABLED, true);
    }

    static void setMapEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MAP_ENABLED, enabled).apply();
    }

    static boolean mapControlsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MAP_CONTROLS_ENABLED, true);
    }

    static void setMapControlsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MAP_CONTROLS_ENABLED, enabled).apply();
    }

    static String mapAppearance(Context context) {
        String value = prefs(context).getString(KEY_MAP_APPEARANCE, MAP_APPEARANCE_AUTO);
        if (MAP_APPEARANCE_NORMAL.equals(value) || MAP_APPEARANCE_DIM.equals(value)) return value;
        return MAP_APPEARANCE_AUTO;
    }

    static void setMapAppearance(Context context, String value) {
        String safe = MAP_APPEARANCE_AUTO;
        if (MAP_APPEARANCE_NORMAL.equals(value) || MAP_APPEARANCE_DIM.equals(value)) safe = value;
        prefs(context).edit().putString(KEY_MAP_APPEARANCE, safe).apply();
    }
}
