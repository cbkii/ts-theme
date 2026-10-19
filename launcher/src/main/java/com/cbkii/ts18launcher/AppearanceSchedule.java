package com.cbkii.ts18launcher;

/** Binary wall-clock schedule: inclusive dawn, exclusive dusk, including overnight windows. */
final class AppearanceSchedule {
    private AppearanceSchedule() {}

    static boolean valid(int day, int night) {
        return anchor(day, LauncherPrefs.DEFAULT_DAY_START_MINUTES)
                != anchor(night, LauncherPrefs.DEFAULT_NIGHT_START_MINUTES);
    }

    static String resolve(int minuteOfDay, int dayStart, int nightStart) {
        int day = anchor(dayStart, LauncherPrefs.DEFAULT_DAY_START_MINUTES);
        int night = anchor(nightStart, LauncherPrefs.DEFAULT_NIGHT_START_MINUTES);
        // Recover corrupt/old equal anchors without trapping the display in a third mode.
        if (day == night) {
            day = LauncherPrefs.DEFAULT_DAY_START_MINUTES;
            night = LauncherPrefs.DEFAULT_NIGHT_START_MINUTES;
        }
        return normalize(minuteOfDay - day) < normalize(night - day)
                ? LauncherPrefs.APPEARANCE_DAY : LauncherPrefs.APPEARANCE_NIGHT;
    }

    private static int anchor(int value, int fallback) { return value < 0 ? fallback : normalize(value); }
    private static int normalize(int value) { return ((value % 1440) + 1440) % 1440; }
}
