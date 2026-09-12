package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Optional UI-personalisation preferences layered over the core launcher contract. */
final class UiPersonalizationPrefs {
    static final String KEY_MEDIA_STARTUP_WARMUP = "media.startup.warmup";
    static final String KEY_ACCENT_HUE = "ui.accent.hue";
    static final String KEY_HOME_SHORTCUTS_ENABLED = "ui.home.shortcuts.enabled";
    static final String KEY_QUICK_ICON_1 = "ui.quick.icon.1";
    static final String KEY_QUICK_ICON_2 = "ui.quick.icon.2";
    static final String KEY_QUICK_ICON_3 = "ui.quick.icon.3";
    static final String KEY_QUICK_ICON_4 = "ui.quick.icon.4";
    static final String KEY_QUICK_ICON_5 = "ui.quick.icon.5";
    static final String KEY_QUICK_ICON_6 = "ui.quick.icon.6";
    static final String KEY_DRAWER_ICON_1 = "ui.drawer.quick.icon.1";
    static final String KEY_DRAWER_ICON_2 = "ui.drawer.quick.icon.2";
    static final String KEY_DRAWER_ICON_3 = "ui.drawer.quick.icon.3";
    static final String KEY_DRAWER_ICON_4 = "ui.drawer.quick.icon.4";
    static final String KEY_DRAWER_ICON_5 = "ui.drawer.quick.icon.5";

    static final String[] QUICK_ICON_KEYS = {
            KEY_QUICK_ICON_1, KEY_QUICK_ICON_2, KEY_QUICK_ICON_3,
            KEY_QUICK_ICON_4, KEY_QUICK_ICON_5, KEY_QUICK_ICON_6
    };
    static final String[] DRAWER_ICON_KEYS = {
            KEY_DRAWER_ICON_1, KEY_DRAWER_ICON_2, KEY_DRAWER_ICON_3,
            KEY_DRAWER_ICON_4, KEY_DRAWER_ICON_5
    };

    private UiPersonalizationPrefs() {}

    static boolean mediaStartupWarmup(Context context) {
        return prefs(context).getBoolean(KEY_MEDIA_STARTUP_WARMUP, true);
    }
    static void setMediaStartupWarmup(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MEDIA_STARTUP_WARMUP, enabled).apply();
    }

    static boolean homeShortcutsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_HOME_SHORTCUTS_ENABLED, true);
    }
    static void setHomeShortcutsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_HOME_SHORTCUTS_ENABLED, enabled).apply();
    }

    static String accentHue(Context context) {
        return AccentPalette.safe(prefs(context).getString(KEY_ACCENT_HUE, AccentPalette.ORANGE));
    }
    static void setAccentHue(Context context, String hue) {
        prefs(context).edit().putString(KEY_ACCENT_HUE, AccentPalette.safe(hue)).apply();
    }

    static String quickIcon(Context context, int index) { return slotIcon(context, QUICK_ICON_KEYS[index]); }
    static String drawerQuickIcon(Context context, int index) { return slotIcon(context, DRAWER_ICON_KEYS[index]); }
    static void setQuickIcon(Context context, int index, String icon) { setSlotIcon(context, QUICK_ICON_KEYS[index], icon); }
    static void setDrawerQuickIcon(Context context, int index, String icon) { setSlotIcon(context, DRAWER_ICON_KEYS[index], icon); }

    private static String slotIcon(Context context, String key) {
        return SlotIconCatalog.safe(prefs(context).getString(key, SlotIconCatalog.AUTO));
    }
    private static void setSlotIcon(Context context, String key, String icon) {
        String safe = SlotIconCatalog.safe(icon);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (SlotIconCatalog.AUTO.equals(safe)) editor.remove(key); else editor.putString(key, safe);
        editor.apply();
    }

    private static SharedPreferences prefs(Context context) { return LauncherPrefs.prefs(context); }
}
