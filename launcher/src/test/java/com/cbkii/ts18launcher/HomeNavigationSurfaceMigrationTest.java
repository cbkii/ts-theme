package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Upgrade, import and downgrade behaviour for the retired in-HOME online map selection. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class HomeNavigationSurfaceMigrationTest {
    private Context context;
    private SharedPreferences prefs;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = LauncherPrefs.prefs(context);
        prefs.edit().clear().commit();
    }

    @Test public void pureResolutionCoversEveryStoredShape() {
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW,
                HomeNavigationSurfacePolicy.resolve(false, "", false));
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN,
                HomeNavigationSurfacePolicy.resolve(false, "", true));
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW,
                HomeNavigationSurfacePolicy.resolve(true, "native_window", true));
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW,
                HomeNavigationSurfacePolicy.resolve(true, "raw_freeform", false));
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN,
                HomeNavigationSurfacePolicy.resolve(true, "leaflet", false));
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN,
                HomeNavigationSurfacePolicy.resolve(true, "fullscreen", false));
        // Unknown or future values (for example a later build's mode after a downgrade) fail safe.
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN,
                HomeNavigationSurfacePolicy.resolve(true, "virtual_display", false));
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN,
                HomeNavigationSurfacePolicy.resolve(true, "", false));
    }

    @Test public void cleanInstallKeepsNativeDefaultAndWritesNothing() {
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW, HomeNavigationSurfacePolicy.mode(context));
        assertTrue(prefs.getAll().isEmpty());
    }

    @Test public void retiredBooleanSelectionBecomesPersistedFullscreen() {
        prefs.edit().putBoolean("map.enabled", true).putBoolean("map.controls.enabled", false)
                .putString(LauncherPrefs.KEY_NAV, "app.organicmaps.incar").commit();
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN, HomeNavigationSurfacePolicy.mode(context));
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        assertEquals("fullscreen", prefs.getString(HomeNavigationSurfacePolicy.KEY, ""));
        assertFalse(prefs.contains("map.enabled"));
        assertFalse(prefs.contains("map.controls.enabled"));
        // Unrelated user choices survive the migration.
        assertEquals("app.organicmaps.incar", prefs.getString(LauncherPrefs.KEY_NAV, ""));
    }

    @Test public void retiredBooleanOffOnlyDropsTheRetiredKeys() {
        prefs.edit().putBoolean("map.enabled", false).commit();
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        assertFalse(prefs.contains("map.enabled"));
        assertFalse(prefs.contains(HomeNavigationSurfacePolicy.KEY));
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW, HomeNavigationSurfacePolicy.mode(context));
    }

    @Test public void storedRetiredModeIsRewrittenOnceAndMigrationIsIdempotent() {
        prefs.edit().putString(HomeNavigationSurfacePolicy.KEY, "leaflet")
                .putBoolean("map.enabled", true).commit();
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        Map<String, ?> first = new LinkedHashMap<>(prefs.getAll());
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        assertEquals(first, prefs.getAll());
        assertEquals("fullscreen", first.get(HomeNavigationSurfacePolicy.KEY));
        assertEquals(1, first.size());
    }

    @Test public void explicitNativeWindowIsNotDowngradedByAStaleRetiredBoolean() {
        prefs.edit().putString(HomeNavigationSurfacePolicy.KEY, "native_window")
                .putBoolean("map.enabled", true).commit();
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        assertEquals("native_window", prefs.getString(HomeNavigationSurfacePolicy.KEY, ""));
        assertFalse(prefs.contains("map.enabled"));
    }

    @Test public void malformedRetiredValueIsNotASelection() {
        prefs.edit().putString("map.enabled", "true").commit();
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW, HomeNavigationSurfacePolicy.mode(context));
        HomeNavigationSurfacePolicy.migrateRetiredSurfaces(context);
        assertFalse(prefs.contains("map.enabled"));
    }

    @Test public void settingAModeNeverWritesTheRetiredBooleanBack() {
        HomeNavigationSurfacePolicy.setMode(context, "leaflet");
        assertEquals("fullscreen", prefs.getString(HomeNavigationSurfacePolicy.KEY, ""));
        HomeNavigationSurfacePolicy.setMode(context, HomeNavigationSurfacePolicy.NATIVE_WINDOW);
        assertEquals("native_window", prefs.getString(HomeNavigationSurfacePolicy.KEY, ""));
        assertFalse(prefs.contains("map.enabled"));
    }

    @Test public void importReplacesRetiredStateAndExportIsCanonical() throws Exception {
        prefs.edit().putBoolean("map.enabled", true).putString(HomeNavigationSurfacePolicy.KEY, "leaflet").commit();
        Map<String, Object> exported = ConfigurationStore.read(context);
        assertEquals("fullscreen", exported.get(HomeNavigationSurfacePolicy.KEY));
        assertFalse(exported.containsKey("map.enabled"));

        ConfigurationCodec.Preview preview = ConfigurationCodec.decode(
                "{\"schema\":\"com.cbkii.ts18launcher.config\",\"version\":1,\"settings\":"
                        + "{\"map.enabled\":true}}", name -> true);
        assertTrue(ConfigurationStore.replace(context, preview.values));
        assertEquals("fullscreen", prefs.getString(HomeNavigationSurfacePolicy.KEY, ""));
        assertFalse(prefs.contains("map.enabled"));
    }
}
