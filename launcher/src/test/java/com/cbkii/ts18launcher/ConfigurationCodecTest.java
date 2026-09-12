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
        values.put(LauncherPrefs.KEY_MAP_ENABLED, true);
        values.put(LauncherPrefs.KEY_APPEARANCE_MODE, LauncherPrefs.APPEARANCE_NIGHT);
        String json = ConfigurationCodec.encode(values);
        ConfigurationCodec.Preview preview = ConfigurationCodec.decode(json, name -> false);
        assertEquals(3, preview.values.size());
        assertEquals(1, preview.unavailable.size());
        assertTrue(preview.summary().contains("com.example.player"));
    }

    @Test(expected = JSONException.class)
    public void unknownSettingIsRejected() throws Exception {
        ConfigurationCodec.decode("{\"schema\":\"com.cbkii.ts18launcher.config\",\"version\":1,\"settings\":{\"secret\":true}}", name -> true);
    }
}
