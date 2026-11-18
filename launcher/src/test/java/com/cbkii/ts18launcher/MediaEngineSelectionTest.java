package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaUserAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Persisted engine choice, import/export, and the listener root-grant consent rules. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class MediaEngineSelectionTest {
    private Context context;
    private SharedPreferences prefs;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = LauncherPrefs.prefs(context);
        prefs.edit().clear().commit();
    }

    @Test public void cleanInstallAndUpgradeDefaultBothSourcesToDirect() {
        assertEquals(MediaEngine.DIRECT, MediaEngineSelector.requested(context, MediaSourceKey.MUSIC));
        assertEquals(MediaEngine.DIRECT, MediaEngineSelector.requested(context, MediaSourceKey.RADIO));
    }

    @Test public void sourcesAreSelectedIndependentlyAndLegacyIsExplicit() {
        MediaEngineSelector.setRequested(context, MediaSourceKey.RADIO, MediaEngine.DIRECT);
        assertEquals(MediaEngine.DIRECT, MediaEngineSelector.requested(context, MediaSourceKey.MUSIC));
        assertEquals(MediaEngine.DIRECT, MediaEngineSelector.requested(context, MediaSourceKey.RADIO));
        assertEquals("m2", prefs.getString("radio.engine", ""));
        MediaEngineSelector.setRequested(context, MediaSourceKey.RADIO, MediaEngine.LEGACY);
        assertEquals("legacy", prefs.getString("radio.engine", ""));
    }

    @Test public void malformedOrFutureStoredValuesReadAsLegacy() {
        prefs.edit().putBoolean("music.engine", true).putString("radio.engine", "m9").commit();
        assertEquals(MediaEngine.LEGACY, MediaEngineSelector.requested(context, MediaSourceKey.MUSIC));
        assertEquals(MediaEngine.LEGACY, MediaEngineSelector.requested(context, MediaSourceKey.RADIO));
    }

    @Test public void engineChoiceRoundTripsThroughExportAndResetRestoresDirect() throws Exception {
        MediaEngineSelector.setRequested(context, MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        String json = ConfigurationCodec.encode(ConfigurationStore.read(context));
        assertTrue(json.contains("\"music.engine\": \"m2\""));
        assertTrue(ConfigurationStore.replace(context, new LinkedHashMap<>()));
        assertEquals(MediaEngine.DIRECT, MediaEngineSelector.requested(context, MediaSourceKey.MUSIC));
        assertTrue(ConfigurationStore.replace(context, ConfigurationCodec.decode(json, name -> true).values));
        assertEquals(MediaEngine.DIRECT, MediaEngineSelector.requested(context, MediaSourceKey.MUSIC));
    }

    @Test(expected = JSONException.class)
    public void unknownEngineValueIsRejectedOnImport() throws Exception {
        ConfigurationCodec.decode("{\"schema\":\"com.cbkii.ts18launcher.config\",\"version\":1,"
                + "\"settings\":{\"music.engine\":\"m3\"}}", name -> true);
    }

    @Test public void detachedLegacyEngineRefusesToCommand() {
        LegacyMediaBackend backend = new LegacyMediaBackend(context);
        List<MediaCommandResult> results = new ArrayList<>();
        backend.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, results::add);
        assertEquals(1, results.size());
        assertEquals(MediaCommandResult.Outcome.FAILED, results.get(0).outcome);
        assertFalse(results.get(0).dispatched);
        assertFalse(backend.playing(MediaSourceKey.MUSIC, "com.tw.media"));
        backend.pauseForHandover(MediaSourceKey.MUSIC); // must be a no-op, not a crash or a command
    }

    @Test public void legacyCommandMappingIsLossless() {
        for (MediaListenerService.Command command : MediaListenerService.Command.values())
            assertEquals(command, LegacyMediaBackend.legacyCommand(LegacyMediaBackend.userAction(command)));
    }

    @Test public void rootListenerGrantNeedsConsentAndRespectsRevocation() {
        // Never implicit: without consent the answer is always Android Settings.
        assertEquals(MediaListenerAccessPolicy.Action.USE_SETTINGS,
                MediaListenerAccessPolicy.decide(false, false, false, false));
        assertEquals(MediaListenerAccessPolicy.Action.USE_SETTINGS,
                MediaListenerAccessPolicy.decide(false, false, true, false));
        // Consent, nothing applied yet: one grant.
        assertEquals(MediaListenerAccessPolicy.Action.ROOT_GRANT,
                MediaListenerAccessPolicy.decide(false, true, false, false));
        // Access vanished after our own grant: the owner revoked it.
        assertEquals(MediaListenerAccessPolicy.Action.WITHDRAW_CONSENT,
                MediaListenerAccessPolicy.decide(false, true, true, false));
        // Only the owner turning the option on again is fresh consent.
        assertEquals(MediaListenerAccessPolicy.Action.ROOT_GRANT,
                MediaListenerAccessPolicy.decide(false, true, true, true));
        for (boolean consent : new boolean[] {false, true}) for (boolean applied : new boolean[] {false, true})
            assertEquals(MediaListenerAccessPolicy.Action.ALREADY_GRANTED,
                    MediaListenerAccessPolicy.decide(true, consent, applied, false));
    }

    @Test public void consentIsOffByDefaultDeviceLocalAndClearedByImportOrReset() throws Exception {
        assertFalse(UiPersonalizationPrefs.mediaListenerRootGrant(context));
        UiPersonalizationPrefs.setMediaListenerRootGrant(context, true);
        UiPersonalizationPrefs.markMediaListenerRootGrantApplied(context);
        assertTrue(UiPersonalizationPrefs.mediaListenerRootGrantApplied(context));
        Map<String, Object> exported = ConfigurationStore.read(context);
        for (String key : ConfigurationCodec.DEVICE_CONSENT_KEYS) {
            assertFalse(exported.containsKey(key));
            assertFalse(ConfigurationCodec.KEYS.containsKey(key));
        }
        // Giving consent again is fresh: the applied marker is cleared.
        UiPersonalizationPrefs.setMediaListenerRootGrant(context, true);
        assertFalse(UiPersonalizationPrefs.mediaListenerRootGrantApplied(context));
        UiPersonalizationPrefs.markMediaListenerRootGrantApplied(context);
        assertTrue(ConfigurationStore.replace(context, new LinkedHashMap<>()));
        assertFalse(UiPersonalizationPrefs.mediaListenerRootGrant(context));
        assertFalse(UiPersonalizationPrefs.mediaListenerRootGrantApplied(context));
    }

    @Test(expected = JSONException.class)
    public void aSettingsFileCannotCarryRootConsent() throws Exception {
        ConfigurationCodec.decode("{\"schema\":\"com.cbkii.ts18launcher.config\",\"version\":1,"
                + "\"settings\":{\"media.listener.root_grant\":true}}", name -> true);
    }

    @Test public void processStartWithoutConsentNeverRunsRootAndWithdrawsAfterRevocation() {
        // Robolectric: the listener is not enabled, so hasNotificationAccess is false.
        MediaNotificationAccessBootstrapper.ensureEarly(context);
        assertFalse(UiPersonalizationPrefs.mediaListenerRootGrant(context));
        UiPersonalizationPrefs.setMediaListenerRootGrant(context, true);
        UiPersonalizationPrefs.markMediaListenerRootGrantApplied(context);
        MediaNotificationAccessBootstrapper.ensureEarly(context);
        assertFalse("revocation after an applied grant withdraws consent",
                UiPersonalizationPrefs.mediaListenerRootGrant(context));
        assertFalse(UiPersonalizationPrefs.mediaListenerRootGrantApplied(context));
    }
}
