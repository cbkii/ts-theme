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
    static final String KEY_RADIO_SIDE = "ui.radio.side";
    static final String KEY_LAST_SOURCE = "media.last.explicit.source";
    static final String KEY_LAST_MUSIC = "media.last.explicit.package";
    static final String KEY_MEDIA_CONTROLS_SIDE = "ui.media.controls.side";
    static final String KEY_MAP_ENABLED = "map.enabled";
    static final String KEY_MAP_CONTROLS_ENABLED = "map.controls.enabled";
    static final String KEY_MEDIA_MODE = "media.selection.mode";
    static final String KEY_APPEARANCE_MODE = "ui.appearance.mode";
    static final String KEY_APPEARANCE_AUTO_SOURCE = "ui.appearance.auto.source";
    static final String KEY_APPEARANCE_DAY_START = "ui.appearance.day.start.minutes";
    static final String KEY_APPEARANCE_NIGHT_START = "ui.appearance.night.start.minutes";

    // Compatibility aliases retained for existing source-contract tests and upgrades.
    static final String KEY_MAP_APPEARANCE = KEY_APPEARANCE_MODE;

    static final String MEDIA_MODE_AUTO = "auto";
    static final String MEDIA_MODE_PREFER_MUSIC = "prefer_music";

    static final String RAIL_DRIVER = "driver";
    static final String RAIL_LEFT = "left";
    static final String RAIL_RIGHT = "right";
    static final String MEDIA_CONTROLS_LEFT = "left";
    static final String MEDIA_CONTROLS_RIGHT = "right";

    static final String APPEARANCE_AUTO = "auto";
    static final String APPEARANCE_DAY = "day";
    static final String APPEARANCE_HIGH_CONTRAST = "high_contrast";
    static final String APPEARANCE_DIM = "dim";
    static final String APPEARANCE_NIGHT = "night";
    static final String MAP_APPEARANCE_AUTO = APPEARANCE_AUTO;
    static final String MAP_APPEARANCE_NORMAL = APPEARANCE_DAY;
    static final String MAP_APPEARANCE_DIM = APPEARANCE_DIM;
    static final String AUTO_SOURCE_SENSOR = "sensor";
    static final String AUTO_SOURCE_SCHEDULE = "schedule";
    static final int SCHEDULE_UNSET = -1;
    static final int DEFAULT_DAY_START_MINUTES = 7 * 60;
    static final int DEFAULT_NIGHT_START_MINUTES = 19 * 60;

    static final String KEY_QUICK_ROLE_1 = "ui.quick.role.1";
    static final String KEY_QUICK_ROLE_2 = "ui.quick.role.2";
    static final String KEY_QUICK_ROLE_3 = "ui.quick.role.3";
    static final String KEY_QUICK_ROLE_4 = "ui.quick.role.4";
    static final String KEY_QUICK_ROLE_5 = "ui.quick.role.5";
    static final String KEY_QUICK_ROLE_6 = "ui.quick.role.6";
    static final String KEY_DRAWER_ROLE_1 = "ui.drawer.quick.role.1";
    static final String KEY_DRAWER_ROLE_2 = "ui.drawer.quick.role.2";
    static final String KEY_DRAWER_ROLE_3 = "ui.drawer.quick.role.3";
    static final String KEY_DRAWER_ROLE_4 = "ui.drawer.quick.role.4";
    static final String KEY_DRAWER_ROLE_5 = "ui.drawer.quick.role.5";

    static final String[] QUICK_KEYS = {KEY_QUICK_1, KEY_QUICK_2, KEY_QUICK_3, KEY_QUICK_4, KEY_QUICK_5, KEY_QUICK_6};
    static final String[] QUICK_ROLE_KEYS = {KEY_QUICK_ROLE_1, KEY_QUICK_ROLE_2, KEY_QUICK_ROLE_3, KEY_QUICK_ROLE_4, KEY_QUICK_ROLE_5, KEY_QUICK_ROLE_6};
    static final String[] DRAWER_QUICK_KEYS = {KEY_DRAWER_QUICK_1, KEY_DRAWER_QUICK_2, KEY_DRAWER_QUICK_3, KEY_DRAWER_QUICK_4, KEY_DRAWER_QUICK_5};
    static final String[] DRAWER_ROLE_KEYS = {KEY_DRAWER_ROLE_1, KEY_DRAWER_ROLE_2, KEY_DRAWER_ROLE_3, KEY_DRAWER_ROLE_4, KEY_DRAWER_ROLE_5};

    private static final String FILE = "ts18_launcher";
    private LauncherPrefs() {}

    static SharedPreferences prefs(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static String packageFor(Context context, String key) { return prefs(context).getString(key, ""); }
    static void setPackage(Context context, String key, String packageName) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (packageName == null || packageName.isEmpty()) editor.remove(key);
        else editor.putString(key, packageName);
        editor.apply();
    }

    static String roleFor(Context context, String key, String fallback) {
        String value = prefs(context).getString(key, fallback);
        return RoleIconCatalog.isKnown(value) ? value : fallback;
    }
    static void setRole(Context context, String key, String role) {
        if (RoleIconCatalog.isKnown(role)) prefs(context).edit().putString(key, role).apply();
    }
    static String quickRole(Context context, int index) { return roleFor(context, QUICK_ROLE_KEYS[index], RoleIconCatalog.defaultQuickRole(index)); }
    static String drawerQuickRole(Context context, int index) { return roleFor(context, DRAWER_ROLE_KEYS[index], RoleIconCatalog.defaultDrawerRole(index)); }

    static String mediaMode(Context context) {
        String value = prefs(context).getString(KEY_MEDIA_MODE, MEDIA_MODE_AUTO);
        return MEDIA_MODE_PREFER_MUSIC.equals(value) ? MEDIA_MODE_PREFER_MUSIC : MEDIA_MODE_AUTO;
    }
    static void setMediaMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MEDIA_MODE,
                MEDIA_MODE_PREFER_MUSIC.equals(mode) ? MEDIA_MODE_PREFER_MUSIC : MEDIA_MODE_AUTO).apply();
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
    static boolean railOnRight(Context context) {
        String value = railPosition(context);
        return RAIL_DRIVER.equals(value) || RAIL_RIGHT.equals(value);
    }

    static boolean radioOnRight(Context context) {
        // The old per-panel control-side preference had different semantics: do not migrate it.
        return RAIL_RIGHT.equals(prefs(context).getString(KEY_RADIO_SIDE, RAIL_LEFT));
    }
    static void setRadioSide(Context context, String value) {
        prefs(context).edit().putString(KEY_RADIO_SIDE, RAIL_RIGHT.equals(value) ? RAIL_RIGHT : RAIL_LEFT).apply();
    }
    static String lastSource(Context context) {
        return MediaSelection.RADIO.equals(prefs(context).getString(KEY_LAST_SOURCE, MediaSelection.MUSIC))
                ? MediaSelection.RADIO : MediaSelection.MUSIC;
    }
    static void selectSource(Context context, String source) {
        if (!source.equals(lastSource(context))) prefs(context).edit().putString(KEY_LAST_SOURCE, source).apply();
    }
    static String lastMusicPackage(Context context) { return packageFor(context, KEY_LAST_MUSIC); }
    static void rememberMusic(Context context, String pkg) {
        if (pkg != null && !pkg.isEmpty() && !pkg.equals(lastMusicPackage(context))) setPackage(context, KEY_LAST_MUSIC, pkg);
    }

    static boolean mapEnabled(Context context) { return prefs(context).getBoolean(KEY_MAP_ENABLED, true); }
    static void setMapEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean(KEY_MAP_ENABLED, enabled).apply(); }
    static boolean mapControlsEnabled(Context context) { return prefs(context).getBoolean(KEY_MAP_CONTROLS_ENABLED, true); }
    static void setMapControlsEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean(KEY_MAP_CONTROLS_ENABLED, enabled).apply(); }

    static String appearanceMode(Context context) {
        String value = prefs(context).getString(KEY_APPEARANCE_MODE, APPEARANCE_AUTO);
        if (APPEARANCE_DAY.equals(value) || APPEARANCE_HIGH_CONTRAST.equals(value)
                || APPEARANCE_DIM.equals(value) || APPEARANCE_NIGHT.equals(value)) return value;
        return APPEARANCE_AUTO;
    }
    static void setAppearanceMode(Context context, String value) {
        String safe = APPEARANCE_AUTO;
        if (APPEARANCE_DAY.equals(value) || APPEARANCE_HIGH_CONTRAST.equals(value)
                || APPEARANCE_DIM.equals(value) || APPEARANCE_NIGHT.equals(value)) safe = value;
        prefs(context).edit().putString(KEY_APPEARANCE_MODE, safe).apply();
    }
    static String mapAppearance(Context context) { return appearanceMode(context); }
    static void setMapAppearance(Context context, String value) { setAppearanceMode(context, value); }

    static String appearanceAutoSource(Context context) {
        return AUTO_SOURCE_SCHEDULE.equals(prefs(context).getString(KEY_APPEARANCE_AUTO_SOURCE, AUTO_SOURCE_SENSOR))
                ? AUTO_SOURCE_SCHEDULE : AUTO_SOURCE_SENSOR;
    }
    static void setAppearanceAutoSource(Context context, String value) {
        prefs(context).edit().putString(KEY_APPEARANCE_AUTO_SOURCE,
                AUTO_SOURCE_SCHEDULE.equals(value) ? AUTO_SOURCE_SCHEDULE : AUTO_SOURCE_SENSOR).apply();
    }
    static int appearanceDayStartMinutes(Context context) { return prefs(context).getInt(KEY_APPEARANCE_DAY_START, SCHEDULE_UNSET); }
    static int appearanceNightStartMinutes(Context context) { return prefs(context).getInt(KEY_APPEARANCE_NIGHT_START, SCHEDULE_UNSET); }
    static void setAppearanceDayStartMinutes(Context context, int minutes) { prefs(context).edit().putInt(KEY_APPEARANCE_DAY_START, clampMinutes(minutes)).apply(); }
    static void setAppearanceNightStartMinutes(Context context, int minutes) { prefs(context).edit().putInt(KEY_APPEARANCE_NIGHT_START, clampMinutes(minutes)).apply(); }
    static void clearAppearanceSchedule(Context context) {
        prefs(context).edit().remove(KEY_APPEARANCE_DAY_START).remove(KEY_APPEARANCE_NIGHT_START).apply();
    }
    private static int clampMinutes(int minutes) {
        if (minutes == SCHEDULE_UNSET) return SCHEDULE_UNSET;
        return Math.max(0, Math.min(1439, minutes));
    }

    // Legacy source marker: static String mediaControlsSide is intentionally gone;
    // Radio/Music side is stored independently under KEY_RADIO_SIDE.
}
