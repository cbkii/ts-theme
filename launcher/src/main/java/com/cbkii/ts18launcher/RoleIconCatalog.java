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
    static final String HOME = "home";
    static final String WORK = "work";
    static final String SEARCH = "search";
    static final String CAMERA = "camera";
    static final String SETTINGS = "settings";
    static final String VIDEO = "video";
    static final String WEATHER = "weather";
    static final String GENERIC = "generic";

    static final String[] VALUES = {
            NAVIGATION, RADIO, MUSIC, BLUETOOTH, PHONE, FAVORITE, HOME, WORK, SEARCH, CAMERA, SETTINGS, UTILITY, VIDEO, WEATHER, GENERIC
    };
    static final String[] LABELS = {
            "Navigation", "Radio", "Music", "Bluetooth", "Phone", "Favourite", "Home", "Work", "Search", "Camera", "Settings", "Utility", "Media/video", "Weather", "App/shortcut"
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
        if (HOME.equals(role)) return R.drawable.ic_home;
        if (WORK.equals(role)) return R.drawable.ic_work;
        if (SEARCH.equals(role)) return R.drawable.ic_search;
        if (CAMERA.equals(role)) return R.drawable.ic_camera;
        if (SETTINGS.equals(role)) return R.drawable.ic_settings;
        if (VIDEO.equals(role)) return R.drawable.ic_video;
        if (WEATHER.equals(role)) return R.drawable.ic_weather;
        if (UTILITY.equals(role)) return R.drawable.ic_utility;
        return R.drawable.ic_shortcut;
    }

    static String label(String role) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(role)) return LABELS[i];
        return "Generic";
    }

    static boolean hasDefault(String role) {
        return NAVIGATION.equals(role) || RADIO.equals(role) || MUSIC.equals(role)
                || BLUETOOTH.equals(role) || PHONE.equals(role) || CAMERA.equals(role) || SETTINGS.equals(role);
    }

    static boolean launch(Context context, String role) {
        if (MUSIC.equals(role)) LauncherPrefs.selectSource(context, MediaSelection.MUSIC);
        if (RADIO.equals(role)) LauncherPrefs.selectSource(context, MediaSelection.RADIO);
        if (NAVIGATION.equals(role)) return NavigationProvider.open(context, fallbackPackage(context, role), null);
        if (SETTINGS.equals(role)) {
            context.startActivity(new android.content.Intent(context, SettingsActivity.class)); return true;
        }
        if (PHONE.equals(role) || CAMERA.equals(role)) {
            android.content.Intent intent = new android.content.Intent(PHONE.equals(role)
                    ? android.content.Intent.ACTION_DIAL : android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            try { context.startActivity(intent); return true; } catch (RuntimeException ignored) { return false; }
        }
        // Home/Work are semantics only; no destinations or routing are owned here.
        return AppResolver.launchPackage(context, fallbackPackage(context, role));
    }

    static String fallbackPackage(Context context, String role) {
        if (NAVIGATION.equals(role)) return LauncherPrefs.packageFor(context, LauncherPrefs.KEY_NAV);
        if (RADIO.equals(role)) return RadioProvider.resolvePackage(context);
        if (MUSIC.equals(role)) {
            String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
            return pkg.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : pkg;
        }
        if (BLUETOOTH.equals(role)) {
            return LauncherPrefs.packageFor(context, LauncherPrefs.KEY_BLUETOOTH);
        }
        return "";
    }
}
