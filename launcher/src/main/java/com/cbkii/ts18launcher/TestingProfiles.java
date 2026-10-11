package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Explicit local experiment choices, isolated from ordinary user configuration backups. */
final class TestingProfiles {
    private TestingProfiles() { }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("testing_profiles", Context.MODE_PRIVATE); }
    static String navigation(Context c) { return choice(c, "navigation", "N1", "N0", "N1", "N2"); }
    static String transition(Context c) { return choice(c, "transition", "bridge", "bridge", "intent"); }
    static String radio(Context c) { return choice(c, "radio", "auto", "auto", "root", "normal"); }
    static boolean prepareAuxio(Context c) { return "prepare".equals(choice(c, "music", "prepare", "prepare", "bind")); }
    static boolean retainCompact(Context c) { return "retain".equals(choice(c, "departure", "fullscreen", "fullscreen", "retain")); }
    private static String choice(Context c, String key, String fallback, String... allowed) {
        String value = prefs(c).getString(key, fallback);
        for (String item : allowed) if (item.equals(value)) return value;
        return fallback;
    }
    static void set(Context c, String key, String value) {
        prefs(c).edit().putString(key, value).apply();
        MediaEventTrace.record("testing", "profile", key + "=" + value);
    }
    static String summary(Context c) {
        return "nav=" + navigation(c) + " transition=" + transition(c)
                + " departure=" + (retainCompact(c) ? "retain" : "fullscreen")
                + " music=" + (prepareAuxio(c) ? "prepare" : "bind") + " radio=" + radio(c);
    }
}
