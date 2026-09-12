package com.cbkii.ts18launcher;

/** Curated visual-only quick-slot icon catalogue. It never changes what a slot launches. */
final class SlotIconCatalog {
    static final String AUTO = "auto";
    static final String NAVIGATION = "navigation";
    static final String RADIO = "radio";
    static final String MUSIC = "music";
    static final String BLUETOOTH = "bluetooth";
    static final String PHONE = "phone";
    static final String FAVORITE = "favorite";
    static final String HOME = "home";
    static final String WORK = "work";
    static final String SEARCH = "search";
    static final String CAMERA = "camera";
    static final String SETTINGS = "settings";
    static final String UTILITY = "utility";
    static final String VIDEO = "video";
    static final String WEATHER = "weather";
    static final String GENERIC = "generic";
    static final String CAR = "car";
    static final String MAP = "map";
    static final String ROUTE = "route";
    static final String EQUALIZER = "equalizer";
    static final String PODCAST = "podcast";
    static final String USB = "usb";
    static final String FILES = "files";
    static final String WIFI = "wifi";
    static final String DOWNLOAD = "download";
    static final String VOLUME = "volume";
    static final String POWER = "power";
    static final String DASHBOARD = "dashboard";
    static final String LIGHT = "light";
    static final String NOTIFICATIONS = "notifications";

    static final String[] VALUES = {
            AUTO, NAVIGATION, RADIO, MUSIC, BLUETOOTH, PHONE, FAVORITE, HOME, WORK,
            SEARCH, CAMERA, SETTINGS, UTILITY, VIDEO, WEATHER, GENERIC, CAR, MAP,
            ROUTE, EQUALIZER, PODCAST, USB, FILES, WIFI, DOWNLOAD, VOLUME, POWER,
            DASHBOARD, LIGHT, NOTIFICATIONS
    };
    static final String[] LABELS = {
            "Auto", "Navigation", "Radio", "Music", "Bluetooth", "Phone", "Favourite",
            "Home", "Work", "Search", "Camera", "Settings", "Tools", "Media/video",
            "Weather", "App", "Car", "Map", "Route", "Equaliser", "Podcast", "USB",
            "Files", "Wi-Fi", "Download", "Volume", "Power", "Dashboard", "Light",
            "Notifications"
    };

    private SlotIconCatalog() {}

    static boolean isKnown(String value) {
        if (value == null) return false;
        for (String candidate : VALUES) if (candidate.equals(value)) return true;
        return false;
    }

    static String safe(String value) { return isKnown(value) ? value : AUTO; }

    static String label(String value) {
        String safe = safe(value);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(safe)) return LABELS[i];
        return "Auto";
    }

    static int icon(String value) {
        String safe = safe(value);
        if (NAVIGATION.equals(safe)) return R.drawable.ic_navigation;
        if (RADIO.equals(safe)) return R.drawable.ic_radio;
        if (MUSIC.equals(safe)) return R.drawable.ic_music;
        if (BLUETOOTH.equals(safe)) return R.drawable.ic_bluetooth;
        if (PHONE.equals(safe)) return R.drawable.ic_phone;
        if (FAVORITE.equals(safe)) return R.drawable.ic_star;
        if (HOME.equals(safe)) return R.drawable.ic_home;
        if (WORK.equals(safe)) return R.drawable.ic_work;
        if (SEARCH.equals(safe)) return R.drawable.ic_search;
        if (CAMERA.equals(safe)) return R.drawable.ic_camera;
        if (SETTINGS.equals(safe)) return R.drawable.ic_settings;
        if (UTILITY.equals(safe)) return R.drawable.ic_utility;
        if (VIDEO.equals(safe)) return R.drawable.ic_video;
        if (WEATHER.equals(safe)) return R.drawable.ic_weather;
        if (CAR.equals(safe)) return R.drawable.ic_car;
        if (MAP.equals(safe)) return R.drawable.ic_map;
        if (ROUTE.equals(safe)) return R.drawable.ic_route;
        if (EQUALIZER.equals(safe)) return R.drawable.ic_equalizer;
        if (PODCAST.equals(safe)) return R.drawable.ic_podcast;
        if (USB.equals(safe)) return R.drawable.ic_usb;
        if (FILES.equals(safe)) return R.drawable.ic_folder;
        if (WIFI.equals(safe)) return R.drawable.ic_wifi;
        if (DOWNLOAD.equals(safe)) return R.drawable.ic_download;
        if (VOLUME.equals(safe)) return R.drawable.ic_volume;
        if (POWER.equals(safe)) return R.drawable.ic_power;
        if (DASHBOARD.equals(safe)) return R.drawable.ic_dashboard;
        if (LIGHT.equals(safe)) return R.drawable.ic_lightbulb;
        if (NOTIFICATIONS.equals(safe)) return R.drawable.ic_notifications;
        return R.drawable.ic_shortcut;
    }
}
