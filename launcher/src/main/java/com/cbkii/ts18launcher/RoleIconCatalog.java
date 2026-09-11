package com.cbkii.ts18launcher;

import android.content.Context;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

final class RoleIconCatalog {
    static final String NAVIGATION = "navigation";
    static final String RADIO = "radio";
    static final String MUSIC = "music";
    static final String BLUETOOTH = "bluetooth";
    static final String PHONE = "phone";
    static final String FAVORITE = "favorite";
    static final String UTILITY = "utility";
    static final String GENERIC = "generic";

    static final String[] VALUES = {
            NAVIGATION, RADIO, MUSIC, BLUETOOTH, PHONE, FAVORITE, UTILITY, GENERIC
    };
    static final String[] LABELS = {
            "Navigation", "Radio", "Music", "Bluetooth", "Phone", "Favourite", "Utility", "Generic"
    };

    private RoleIconCatalog() {}

    static boolean isKnown(String value) {
        if (value == null) return false;
        for (String role : VALUES) if (role.equals(value)) return true;
        return false;
    }

    static String defaultQuickRole(int index) {
        switch (index) {
            case 0: return RADIO;
            case 1: return MUSIC;
            case 2: return BLUETOOTH;
            case 3: return FAVORITE;
            case 4: return UTILITY;
            default: return GENERIC;
        }
    }

    static String defaultDrawerRole(int index) {
        switch (index) {
            case 0: return NAVIGATION;
            case 1: return RADIO;
            case 2: return MUSIC;
            case 3: return BLUETOOTH;
            default: return FAVORITE;
        }
    }

    static int icon(String role) {
        if (NAVIGATION.equals(role)) return R.drawable.ic_navigation;
        if (RADIO.equals(role)) return R.drawable.ic_radio;
        if (MUSIC.equals(role)) return R.drawable.ic_music;
        if (BLUETOOTH.equals(role)) return R.drawable.ic_bluetooth;
        if (PHONE.equals(role)) return R.drawable.ic_phone;
        if (FAVORITE.equals(role)) return R.drawable.ic_star;
        if (UTILITY.equals(role)) return R.drawable.ic_utility;
        return R.drawable.ic_shortcut;
    }

    static String label(String role) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(role)) return LABELS[i];
        return "Generic";
    }

    static String fallbackPackage(Context context, String role) {
        if (NAVIGATION.equals(role)) return LauncherPrefs.packageFor(context, LauncherPrefs.KEY_NAV);
        if (RADIO.equals(role)) return RadioProvider.resolvePackage(context);
        if (MUSIC.equals(role)) {
            String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
            return pkg.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : pkg;
        }
        if (BLUETOOTH.equals(role) || PHONE.equals(role)) {
            return LauncherPrefs.packageFor(context, LauncherPrefs.KEY_BLUETOOTH);
        }
        return "";
    }
}
