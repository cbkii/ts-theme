package com.cbkii.ts18launcher.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/** Engine ownership and cross-engine handover, exercised across the M1/M2 selection matrix. */
public class MediaFeatureFacadeTest {
    private final List<String> log = new ArrayList<>();
    private final Map<MediaSourceKey, MediaEngine> requested = new EnumMap<>(MediaSourceKey.class);
    private final Map<MediaSourceKey, String> packages = new EnumMap<>(MediaSourceKey.class);
    private FakeMediaBackend legacy;
    private FakeMediaBackend direct;
    private final MediaFeatureFacade.Configuration configuration = new MediaFeatureFacade.Configuration() {
        @Override public MediaEngine requestedEngine(MediaSourceKey key) { return requested.get(key); }
        @Override public String configuredPackage(MediaSourceKey key) { return packages.get(key); }
    };

    private static MediaCommandResult ack(boolean playing) {
        return new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED, "", playing, true);
    }

    @Before public void setUp() {
        legacy = new FakeMediaBackend(MediaEngine.LEGACY, log);
        direct = new FakeMediaBackend(MediaEngine.DIRECT, log);
        requested.put(MediaSourceKey.MUSIC, MediaEngine.LEGACY);
        requested.put(MediaSourceKey.RADIO, MediaEngine.LEGACY);
        packages.put(MediaSourceKey.MUSIC, "com.tw.media");
        packages.put(MediaSourceKey.RADIO, "com.navimods.radio");
    }

    private MediaFeatureFacade facade() {
        MediaFeatureFacade facade = new MediaFeatureFacade(configuration, legacy, direct);
        facade.reconcile();
        log.clear();
        return facade;
    }

    @Test public void defaultSelectionAttachesOnlyLegacyAndLegacyOwnsHandover() {
        MediaFeatureFacade facade = new MediaFeatureFacade(configuration, legacy, direct);
        assertEquals(MediaEngine.LEGACY, facade.engine(MediaSourceKey.MUSIC));
        facade.reconcile();
        assertEquals("[LEGACY.attach MUSIC com.tw.media, LEGACY.attach RADIO com.navimods.radio]", log.toString());
        assertTrue(direct.attached.isEmpty());
        assertTrue(facade.legacyOwnsHandover(MediaSourceKey.MUSIC));
        assertTrue(facade.legacyOwnsHandover(MediaSourceKey.RADIO));
    }

    @Test public void bothLegacyCommandIsPassedThroughWithoutAnyFacadeHandover() {
        MediaFeatureFacade facade = facade();
        legacy.playing.put(MediaSourceKey.RADIO, true);
        List<MediaCommandResult> results = new ArrayList<>();
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, results::add);
        legacy.complete(ack(true));
        assertEquals("[LEGACY.command MUSIC com.tw.media PLAY_PAUSE]", log.toString());
        assertEquals(1, results.size());
    }

    @Test public void switchingEngineDetachesTheOldEngineBeforeAttachingTheNew() {
        MediaFeatureFacade facade = facade();
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        facade.reconcile();
        assertEquals("[LEGACY.detach MUSIC, DIRECT.attach MUSIC com.tw.media]", log.toString());
        assertEquals(MediaEngine.DIRECT, facade.engine(MediaSourceKey.MUSIC));
        assertEquals(MediaEngine.LEGACY, facade.engine(MediaSourceKey.RADIO));
        assertFalse(facade.legacyOwnsHandover(MediaSourceKey.RADIO));
        log.clear();
        facade.reconcile();
        assertTrue("unchanged configuration must not re-attach", log.isEmpty());
        requested.put(MediaSourceKey.MUSIC, MediaEngine.LEGACY);
        facade.reconcile();
        assertEquals("[DIRECT.detach MUSIC, LEGACY.attach MUSIC com.tw.media]", log.toString());
    }

    @Test public void packageChangeReattachesTheSameEngineWithTheNewExactPackage() {
        MediaFeatureFacade facade = facade();
        packages.put(MediaSourceKey.MUSIC, "org.example.player");
        facade.reconcile();
        assertEquals("[LEGACY.attach MUSIC org.example.player]", log.toString());
    }

    @Test public void missingDirectEngineResolvesAStoredDirectRequestToLegacy() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        requested.put(MediaSourceKey.RADIO, MediaEngine.DIRECT);
        MediaFeatureFacade facade = new MediaFeatureFacade(configuration, legacy, null);
        facade.reconcile();
        assertFalse(facade.directAvailable());
        assertEquals(MediaEngine.LEGACY, facade.engine(MediaSourceKey.MUSIC));
        assertEquals(MediaEngine.LEGACY, facade.engine(MediaSourceKey.RADIO));
        assertTrue(facade.legacyOwnsHandover(MediaSourceKey.MUSIC));
    }

    @Test public void commandOnlyReachesTheSelectedEngine() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.NEXT, null);
        facade.command(MediaSourceKey.RADIO, "com.navimods.radio", MediaUserAction.NEXT, null);
        assertEquals("[DIRECT.command MUSIC com.tw.media NEXT, LEGACY.command RADIO com.navimods.radio NEXT]",
                log.toString());
    }

    @Test public void directPlayPausesTheLegacyOppositeOnlyAfterObservedPlayback() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        legacy.playing.put(MediaSourceKey.RADIO, true);
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, null);
        assertEquals("replacement is requested first; the old source keeps playing",
                "[DIRECT.command MUSIC com.tw.media PLAY_PAUSE]", log.toString());
        direct.complete(ack(true));
        assertEquals("[DIRECT.command MUSIC com.tw.media PLAY_PAUSE, LEGACY.pauseForHandover RADIO]",
                log.toString());
    }

    @Test public void legacyPlayPausesTheDirectOppositeAfterObservedPlayback() {
        requested.put(MediaSourceKey.RADIO, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        direct.playing.put(MediaSourceKey.RADIO, true);
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, null);
        legacy.complete(ack(true));
        assertEquals("[LEGACY.command MUSIC com.tw.media PLAY_PAUSE, DIRECT.pauseForHandover RADIO]",
                log.toString());
    }

    @Test public void failedOrUnconfirmedReplacementLeavesTheFormerSourcePlaying() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        requested.put(MediaSourceKey.RADIO, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        direct.playing.put(MediaSourceKey.RADIO, true);
        for (MediaCommandResult result : new MediaCommandResult[] {
                new MediaCommandResult(MediaCommandResult.Outcome.FAILED, "No session", false, false),
                new MediaCommandResult(MediaCommandResult.Outcome.SENT_UNCONFIRMED, "", false, true),
                new MediaCommandResult(MediaCommandResult.Outcome.SOURCE_LOST, "", false, true),
                new MediaCommandResult(MediaCommandResult.Outcome.UNSUPPORTED, "", false, false)}) {
            facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, null);
            direct.complete(result);
        }
        for (String entry : log) assertFalse(entry, entry.contains("pauseForHandover"));
        assertTrue(direct.playing.get(MediaSourceKey.RADIO));
    }

    @Test public void pauseSkipOrAlreadyPlayingNeverTriggersAHandover() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        legacy.playing.put(MediaSourceKey.RADIO, true);
        direct.playing.put(MediaSourceKey.MUSIC, true);
        // PLAY_PAUSE on a playing source is a pause, not a switch.
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, null);
        direct.complete(ack(false));
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.NEXT, null);
        direct.complete(ack(true));
        for (String entry : log) assertFalse(entry, entry.contains("pauseForHandover"));
    }

    @Test public void engineChangedWhileCommandInFlightCancelsTheHandover() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        legacy.playing.put(MediaSourceKey.RADIO, true);
        facade.command(MediaSourceKey.MUSIC, "com.tw.media", MediaUserAction.PLAY_PAUSE, null);
        requested.put(MediaSourceKey.RADIO, MediaEngine.DIRECT);
        facade.reconcile();
        direct.complete(ack(true));
        for (String entry : log) assertFalse(entry, entry.contains("pauseForHandover"));
    }

    @Test public void deselectedEngineCannotPublishStateForASourceItNoLongerOwns() {
        requested.put(MediaSourceKey.MUSIC, MediaEngine.DIRECT);
        MediaFeatureFacade facade = facade();
        List<MediaSourceState> seen = new ArrayList<>();
        facade.setStateListener(seen::add);
        legacy.publish(MediaSourceKey.MUSIC);
        assertTrue("legacy is not the Music engine", seen.isEmpty());
        direct.publish(MediaSourceKey.MUSIC);
        legacy.publish(MediaSourceKey.RADIO);
        direct.publish(MediaSourceKey.RADIO);
        assertEquals(2, seen.size());
        assertSame(MediaEngine.DIRECT, seen.get(0).engine);
        assertSame(MediaEngine.LEGACY, seen.get(1).engine);
    }

    @Test public void storedEngineValuesFailSafeToLegacy() {
        assertEquals(MediaEngine.DIRECT, MediaEngine.fromStored("m2"));
        for (String value : new String[] {null, "", "legacy", "M2", "m3", "direct", "virtual"})
            assertEquals(String.valueOf(value), MediaEngine.LEGACY, MediaEngine.fromStored(value));
        assertTrue(MediaEngine.isKnownStoredValue("legacy"));
        assertTrue(MediaEngine.isKnownStoredValue("m2"));
        assertFalse(MediaEngine.isKnownStoredValue("m3"));
    }

    @Test public void unavailableStateNeverCarriesStaleMetadataOrPlayingClaim() {
        MediaSourceState lost = new MediaSourceState(MediaSourceKey.MUSIC, MediaEngine.DIRECT, "com.tw.media",
                MediaSourceState.Availability.SOURCE_LOST, MediaSourceState.NATIVE_STATE_PLAYING, 4L,
                new Object(), "id", "Old title", "Old artist", 7L);
        assertFalse(lost.playing());
        assertEquals("", lost.title);
        assertEquals("", lost.itemId);
        assertEquals(0L, lost.actions);
        MediaSourceState buffering = new MediaSourceState(MediaSourceKey.MUSIC, MediaEngine.DIRECT, "com.tw.media",
                MediaSourceState.Availability.LIVE, 6, 4L, "t", "id", "T", "A", 7L);
        assertFalse("buffering is not playing", buffering.playing());
        assertTrue(buffering.samePresentation(new MediaSourceState(MediaSourceKey.MUSIC, MediaEngine.DIRECT,
                "com.tw.media", MediaSourceState.Availability.LIVE, 6, 4L, "t", "id", "T", "A", 9L)));
    }
}
