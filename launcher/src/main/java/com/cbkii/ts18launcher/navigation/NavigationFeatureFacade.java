package com.cbkii.ts18launcher.navigation;

import android.content.Context;
import android.content.SharedPreferences;

import com.cbkii.ts18launcher.navigation.n3.N3NavigationBackend;
import com.cbkii.ts18launcher.navigation.n3.N3State;

/**
 * Single owner of "which navigation engine acts now". Legacy (N0-N2) is always the default and
 * the rollback. N3 is a separate, default-OFF experimental engine with its own display; while N3
 * holds a display, legacy automatic launch is not admitted, so the two never compete for the
 * navigator. Turning N3 off shuts its session down and hands everything back to legacy.
 */
public final class NavigationFeatureFacade {
    public enum Engine { LEGACY, N3 }

    static final String PREFS = "navigation_engine";
    static final String KEY_N3_ENABLED = "n3.experimental.enabled";

    private static NavigationFeatureFacade instance;

    private final Context app;
    private final N3NavigationBackend n3;

    private NavigationFeatureFacade(Context context) {
        app = context.getApplicationContext();
        n3 = new N3NavigationBackend(app);
    }

    public static synchronized NavigationFeatureFacade get(Context context) {
        if (instance == null) instance = new NavigationFeatureFacade(context);
        return instance;
    }

    private SharedPreferences prefs() { return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Explicit opt-in only; never set by migration, import or setup defaults. */
    public boolean n3Enabled() { return prefs().getBoolean(KEY_N3_ENABLED, false); }

    public void setN3Enabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_N3_ENABLED, enabled).apply();
        if (!enabled) n3.shutdown();
    }

    /** The N3 backend, only when the driver enabled it; null otherwise. */
    public N3NavigationBackend n3() {
        if (!n3Enabled()) return null;
        return n3;
    }

    public Engine engine() {
        return n3Enabled() && n3.snapshot().state.holdsDisplay() ? Engine.N3 : Engine.LEGACY;
    }

    /** Class-monitor admission; also read by the main-thread N3 host before display creation. */
    private volatile boolean legacyAutomaticInFlight;

    /**
     * Atomically admit one bounded legacy automatic launch: refused when N3 owns a display.
     * While the claim is held, N3 display acquisition is refused, so the engines can never
     * race a competing navigator launch between policy check and root dispatch.
     */
    public static synchronized boolean beginLegacyAutomaticLaunch(Context context) {
        NavigationFeatureFacade facade = get(context);
        if (facade.legacyAutomaticInFlight || facade.engine() == Engine.N3) return false;
        facade.legacyAutomaticInFlight = true;
        return true;
    }

    public static synchronized void endLegacyAutomaticLaunch(Context context) {
        get(context).legacyAutomaticInFlight = false;
    }

    /** N3 display acquisition is refused for the bounded lifetime of a legacy automatic launch. */
    public boolean n3MayAcquireDisplay() { return !legacyAutomaticInFlight; }

    /** "Use legacy navigation": end N3 and turn it off. */
    public void useLegacy() { setN3Enabled(false); }

    static boolean holds(N3State state) { return state != null && state.holdsDisplay(); }
}
