package com.cbkii.ts18launcher;

/** Pure schedule resolver kept Android-free for deterministic JVM tests. */
final class AppearanceSchedule {
    static final int TRANSITION_MINUTES = 45;
    static final int GLARE_INSET_MINUTES = 180;

    private AppearanceSchedule() {}

    static String resolve(int minuteOfDay, int dayStart, int nightStart) {
        int now = normalize(minuteOfDay);
        int day = dayStart < 0 ? LauncherPrefs.DEFAULT_DAY_START_MINUTES : normalize(dayStart);
        int night = nightStart < 0 ? LauncherPrefs.DEFAULT_NIGHT_START_MINUTES : normalize(nightStart);
        int daylight = forwardDistance(day, night);
        if (daylight < 240) return LauncherPrefs.APPEARANCE_DIM;

        if (circularDistance(now, day) <= TRANSITION_MINUTES
                || circularDistance(now, night) <= TRANSITION_MINUTES) {
            return LauncherPrefs.APPEARANCE_DIM;
        }

        if (!inForwardWindow(now, day, night)) return LauncherPrefs.APPEARANCE_NIGHT;

        int glareInset = Math.min(GLARE_INSET_MINUTES, Math.max(60, daylight / 4));
        int glareStart = normalize(day + glareInset);
        int glareEnd = normalize(night - glareInset);
        if (inForwardWindow(now, glareStart, glareEnd)) {
            return LauncherPrefs.APPEARANCE_HIGH_CONTRAST;
        }
        return LauncherPrefs.APPEARANCE_DAY;
    }

    private static int normalize(int minutes) {
        int result = minutes % 1440;
        return result < 0 ? result + 1440 : result;
    }

    private static int forwardDistance(int from, int to) {
        return normalize(to - from);
    }

    private static int circularDistance(int left, int right) {
        int forward = forwardDistance(left, right);
        return Math.min(forward, 1440 - forward);
    }

    private static boolean inForwardWindow(int value, int start, int end) {
        int span = forwardDistance(start, end);
        int offset = forwardDistance(start, value);
        return span > 0 && offset < span;
    }
}
