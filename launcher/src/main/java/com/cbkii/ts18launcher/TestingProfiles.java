package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Selected integration profile; retained Radio preference remains independent. */
final class TestingProfiles {
    private TestingProfiles() { }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("testing_profiles", Context.MODE_PRIVATE); }
    static String navigation(Context c) { return "N2"; }
    static String transition(Context c) { return "bridge"; }
    static String radio(Context c) { return choice(c, "radio", "auto", "auto", "root", "normal"); }
    static String startup(Context c) { return "auto"; }
    static boolean prepareAuxio(Context c) { return true; }
    static boolean retainCompact(Context c) { return false; }
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
                + " music=" + (prepareAuxio(c) ? "prepare" : "bind") + " radio=" + radio(c) + " startup=" + startup(c);
    }
}
