package com.cbkii.ts18launcher;

/** Binary wall-clock schedule: inclusive dawn, exclusive dusk, including overnight windows. */
final class AppearanceSchedule {
    private AppearanceSchedule() {}

    static boolean valid(int day, int night) {
        return anchor(day, LauncherPrefs.DEFAULT_DAY_START_MINUTES)
                != anchor(night, LauncherPrefs.DEFAULT_NIGHT_START_MINUTES);
    }

    static int effectiveDayStart(int day, int night) {
        return valid(day, night) ? anchor(day, LauncherPrefs.DEFAULT_DAY_START_MINUTES)
                : LauncherPrefs.DEFAULT_DAY_START_MINUTES;
    }

    static int effectiveNightStart(int day, int night) {
        return valid(day, night) ? anchor(night, LauncherPrefs.DEFAULT_NIGHT_START_MINUTES)
                : LauncherPrefs.DEFAULT_NIGHT_START_MINUTES;
    }

    static String resolve(int minuteOfDay, int dayStart, int nightStart) {
        int day = effectiveDayStart(dayStart, nightStart);
        int night = effectiveNightStart(dayStart, nightStart);
        return normalize(minuteOfDay - day) < normalize(night - day)
                ? LauncherPrefs.APPEARANCE_DAY : LauncherPrefs.APPEARANCE_NIGHT;
    }

    private static int anchor(int value, int fallback) { return value < 0 ? fallback : normalize(value); }
    private static int normalize(int value) { return ((value % 1440) + 1440) % 1440; }
}
