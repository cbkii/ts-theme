package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONException;
import org.junit.Test;

public class ConfigurationCodecTest {
    @Test public void roundTripIsWhitelistedAndUnavailablePackagesAreFlagged() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LauncherPrefs.KEY_QUICK_COUNT, 5);
        values.put(LauncherPrefs.KEY_QUICK_1, "com.example.player");
        values.put(HomeNavigationSurfacePolicy.KEY, HomeNavigationSurfacePolicy.NATIVE_WINDOW);
        values.put(UiPersonalizationPrefs.KEY_NAV_ROOT_PERMISSION_GRANT, true);
        values.put(LauncherPrefs.KEY_APPEARANCE_MODE, LauncherPrefs.APPEARANCE_NIGHT);
        String json = ConfigurationCodec.encode(values);
        ConfigurationCodec.Preview preview = ConfigurationCodec.decode(json, name -> false);
        assertEquals(4, preview.values.size());
        assertEquals(Boolean.TRUE,
                preview.values.get(UiPersonalizationPrefs.KEY_NAV_ROOT_PERMISSION_GRANT));
        assertEquals(1, preview.unavailable.size());
        assertTrue(preview.warnings.isEmpty());
        assertTrue(preview.summary().contains("com.example.player"));
    }

    @Test public void readinessLoaderOptionsRoundTripIndependently() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(UiPersonalizationPrefs.KEY_STARTUP_LOADER, false);
        values.put(UiPersonalizationPrefs.KEY_STARTUP_LOADER_AFTER_SLEEP, true);
        ConfigurationCodec.Preview preview = ConfigurationCodec.decode(ConfigurationCodec.encode(values), name -> true);
        assertEquals(Boolean.FALSE, preview.values.get(UiPersonalizationPrefs.KEY_STARTUP_LOADER));
        assertEquals(Boolean.TRUE, preview.values.get(UiPersonalizationPrefs.KEY_STARTUP_LOADER_AFTER_SLEEP));
    }

    private static ConfigurationCodec.Preview decodeSettings(String settings) throws Exception {
        return ConfigurationCodec.decode("{\"schema\":\"com.cbkii.ts18launcher.config\",\"version\":1,"
                + "\"settings\":" + settings + "}", name -> true);
    }

    @Test public void retiredOnlineMapExportImportsAsFullscreenAndNeverStoresRetiredKeys() throws Exception {
        ConfigurationCodec.Preview preview = decodeSettings(
                "{\"map.enabled\":true,\"map.controls.enabled\":false,\"ui.quick.count\":4}");
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN,
                preview.values.get(HomeNavigationSurfacePolicy.KEY));
        assertEquals(2, preview.values.size());
        for (String retired : ConfigurationCodec.RETIRED_BOOLEAN_KEYS) {
            assertTrue(!preview.values.containsKey(retired));
            assertTrue(!ConfigurationCodec.KEYS.containsKey(retired));
        }
    }

    @Test public void retiredMapDisabledLeavesSurfaceToTheProductDefault() throws Exception {
        ConfigurationCodec.Preview preview = decodeSettings("{\"map.enabled\":false}");
        assertTrue(preview.values.isEmpty());
    }

    @Test public void explicitSurfaceWinsOverRetiredBooleanAndRetiredModeIsCanonicalised() throws Exception {
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW, decodeSettings(
                "{\"map.enabled\":true,\"navigation.surface.mode\":\"native_window\"}")
                .values.get(HomeNavigationSurfacePolicy.KEY));
        assertEquals(HomeNavigationSurfacePolicy.FULLSCREEN, decodeSettings(
                "{\"navigation.surface.mode\":\"leaflet\"}").values.get(HomeNavigationSurfacePolicy.KEY));
        assertEquals(HomeNavigationSurfacePolicy.NATIVE_WINDOW, decodeSettings(
                "{\"navigation.surface.mode\":\"raw_freeform\"}").values.get(HomeNavigationSurfacePolicy.KEY));
    }

    @Test(expected = JSONException.class)
    public void retiredKeyWithWrongTypeIsRejected() throws Exception {
        decodeSettings("{\"map.enabled\":\"yes\"}");
    }

    @Test(expected = JSONException.class)
    public void unknownSurfaceModeIsRejected() throws Exception {
        decodeSettings("{\"navigation.surface.mode\":\"virtual_display\"}");
    }

    @Test public void exportNeverContainsRetiredKeys() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("map.enabled", true);
        values.put(LauncherPrefs.KEY_QUICK_COUNT, 3);
        String json = ConfigurationCodec.encode(values);
        assertTrue(!json.contains("map.enabled"));
        assertTrue(json.contains(LauncherPrefs.KEY_QUICK_COUNT));
    }

    @Test public void unknownSettingIsSkippedWithPreviewWarning() throws Exception {
        ConfigurationCodec.Preview preview = decodeSettings("{\"future.setting\":true,\"ui.quick.count\":4}");
        assertEquals(1, preview.values.size());
        assertEquals(1, preview.warnings.size());
        assertTrue(preview.unavailable.isEmpty());
        assertTrue(preview.summary().contains("Unknown setting ignored"));
    }
}
