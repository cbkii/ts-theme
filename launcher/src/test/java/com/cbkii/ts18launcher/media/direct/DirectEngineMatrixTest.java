package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaBackend;
import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaFeatureFacade;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaUserAction;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSession;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSource;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.ManualClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * The four engine combinations through the real facade and the real direct engine, with a
 * recording stand-in for the legacy engine. Proves that only the selected engine acts for a
 * source and that a cross-engine handover is committed only after observed playback.
 */
public class DirectEngineMatrixTest {
    private static final String AUXIO = "com.tw.media";
    private static final String NAVRADIO = "com.navimods.radio";

    /** Legacy stand-in: records what it is asked to do and completes commands on request. */
    private static final class RecordingLegacy implements MediaBackend {
        final List<String> log = new ArrayList<>();
        final Map<MediaSourceKey, String> attached = new EnumMap<>(MediaSourceKey.class);
        final Map<MediaSourceKey, Boolean> playing = new EnumMap<>(MediaSourceKey.class);
        final List<ResultCallback> pending = new ArrayList<>();
        @Override public MediaEngine engine() { return MediaEngine.LEGACY; }
        @Override public void attach(MediaSourceKey key, String packageName) { attached.put(key, packageName); }
        @Override public void detach(MediaSourceKey key) { attached.remove(key); log.add("detach " + key); }
        @Override public boolean attached(MediaSourceKey key) { return attached.containsKey(key); }
        @Override public MediaSourceState state(MediaSourceKey key) {
            return MediaSourceState.unavailable(key, MediaEngine.LEGACY, attached.get(key),
                    MediaSourceState.Availability.NO_SESSION, 0L);
        }
        @Override public boolean playing(MediaSourceKey key, String packageName) {
            return attached.containsKey(key) && Boolean.TRUE.equals(playing.get(key));
        }
        @Override public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                                      ResultCallback callback) {
            log.add("command " + key + " " + packageName + " " + action);
            pending.add(callback);
        }
        @Override public void pauseForHandover(MediaSourceKey key) { log.add("pause " + key); playing.put(key, false); }
        @Override public void setStateListener(StateListener listener) { }
    }

    private final Map<MediaSourceKey, MediaEngine> choice = new EnumMap<>(MediaSourceKey.class);
    private FakeSource source;
    private RecordingLegacy legacy;
    private DirectSourceMediaBackend direct;
    private MediaFeatureFacade facade;
    private FakeSession music;
    private FakeSession radio;
    private final List<MediaCommandResult> results = new ArrayList<>();
    private final List<MediaSourceState> uiStates = new ArrayList<>();

    @Before public void setUp() {
        source = new FakeSource();
        music = new FakeSession("music", AUXIO).state(PlaybackState.STATE_PAUSED);
        radio = new FakeSession("radio", NAVRADIO).state(PlaybackState.STATE_PAUSED);
        source.sessions.add(music);
        source.sessions.add(radio);
        legacy = new RecordingLegacy();
        direct = new DirectSourceMediaBackend(source, new ManualClock(), new DirectSourceMediaBackend.Host() {
            @Override public String appLabel(String packageName) { return packageName; }
            @Override public void trace(String event, String detail) { }
        });
        facade = new MediaFeatureFacade(new MediaFeatureFacade.Configuration() {
            @Override public MediaEngine requestedEngine(MediaSourceKey key) {
                MediaEngine engine = choice.get(key);
                return engine == null ? MediaEngine.LEGACY : engine;
            }
            @Override public String configuredPackage(MediaSourceKey key) {
                return key == MediaSourceKey.RADIO ? NAVRADIO : AUXIO;
            }
        }, legacy, direct);
        facade.setStateListener(uiStates::add);
    }

    private void select(MediaEngine musicEngine, MediaEngine radioEngine) {
        choice.put(MediaSourceKey.MUSIC, musicEngine);
        choice.put(MediaSourceKey.RADIO, radioEngine);
        facade.reconcile();
    }

    @Test public void legacyLegacyLeavesTheDirectEngineCompletelyIdle() {
        select(MediaEngine.LEGACY, MediaEngine.LEGACY);
        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);
        facade.refresh();

        assertEquals(Arrays.asList("command MUSIC com.tw.media PLAY_PAUSE"), legacy.log);
        assertEquals(0, source.reads);
        assertTrue(source.listener == null);
        assertEquals(0, DirectTestKit.totalDispatches(music, radio));
        assertTrue(facade.legacyOwnsHandover(MediaSourceKey.MUSIC));
    }

    @Test public void directMusicLegacyRadioRoutesEachSourceToItsOwnEngineOnly() {
        select(MediaEngine.DIRECT, MediaEngine.LEGACY);

        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.NEXT, results::add);
        facade.command(MediaSourceKey.RADIO, NAVRADIO, MediaUserAction.NEXT, results::add);

        assertEquals(Arrays.asList(DirectOperation.NEXT), music.dispatched);
        assertEquals("the direct engine never touches the legacy-owned Radio session", 0, radio.dispatched.size());
        assertEquals(Arrays.asList("command RADIO com.navimods.radio NEXT"), legacy.log);
        assertFalse(legacy.attached(MediaSourceKey.MUSIC));
        assertFalse(direct.attached(MediaSourceKey.RADIO));
        assertFalse(facade.legacyOwnsHandover(MediaSourceKey.MUSIC));
    }

    @Test public void directMusicReplacesLegacyRadioOnlyAfterMusicIsObservedPlaying() {
        select(MediaEngine.DIRECT, MediaEngine.LEGACY);
        legacy.playing.put(MediaSourceKey.RADIO, true);

        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);
        assertEquals(Arrays.asList(DirectOperation.PLAY), music.dispatched);
        assertTrue("Radio keeps playing while Music is only requested", legacy.log.isEmpty());

        music.state(PlaybackState.STATE_BUFFERING).emit();
        assertTrue("buffering does not commit the handover", legacy.log.isEmpty());

        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(Arrays.asList("pause RADIO"), legacy.log);
        assertEquals(1, results.size());
    }

    @Test public void failedDirectReplacementLeavesTheLegacySourcePlaying() {
        select(MediaEngine.DIRECT, MediaEngine.LEGACY);
        legacy.playing.put(MediaSourceKey.RADIO, true);
        source.sessions.remove(music);
        source.changed();

        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);

        assertEquals(MediaCommandResult.Outcome.SOURCE_LOST, results.get(0).outcome);
        assertFalse(results.get(0).dispatched);
        assertTrue(legacy.log.isEmpty());
        assertTrue(legacy.playing(MediaSourceKey.RADIO, NAVRADIO));
        assertEquals(0, music.dispatched.size());
    }

    @Test public void legacyMusicReplacesDirectRadioWithOneDirectPauseAfterObservedPlayback() {
        select(MediaEngine.LEGACY, MediaEngine.DIRECT);
        radio.state(PlaybackState.STATE_PLAYING).emit();

        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);
        assertEquals(0, radio.dispatched.size());

        legacy.playing.put(MediaSourceKey.MUSIC, true);
        legacy.pending.remove(0).onResult(new MediaCommandResult(
                MediaCommandResult.Outcome.ACKNOWLEDGED, "", true, true));

        assertEquals(Arrays.asList(DirectOperation.PAUSE), radio.dispatched);
        assertEquals(0, music.dispatched.size());
    }

    @Test public void directDirectHandoverPausesTheExactPreviousOwnerOnce() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        radio.state(PlaybackState.STATE_PLAYING).emit();

        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);
        assertEquals(0, radio.dispatched.size());
        music.state(PlaybackState.STATE_PLAYING).emit();

        assertEquals(Arrays.asList(DirectOperation.PLAY), music.dispatched);
        assertEquals(Arrays.asList(DirectOperation.PAUSE), radio.dispatched);
        assertTrue(legacy.log.isEmpty());
    }

    @Test public void unconfirmedDirectPlayDoesNotPauseTheOtherSource() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        radio.state(PlaybackState.STATE_PLAYING).emit();
        ManualClock clock = new ManualClock();
        direct = new DirectSourceMediaBackend(source, clock, new DirectSourceMediaBackend.Host() {
            @Override public String appLabel(String packageName) { return packageName; }
            @Override public void trace(String event, String detail) { }
        });
        direct.attach(MediaSourceKey.MUSIC, AUXIO);
        direct.attach(MediaSourceKey.RADIO, NAVRADIO);
        direct.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);
        clock.expireAll();
        assertEquals(MediaCommandResult.Outcome.SENT_UNCONFIRMED, results.get(0).outcome);
        assertFalse("an unconfirmed play is not observed playback", results.get(0).playingObserved);
    }

    @Test public void switchingASourceBackToLegacySilencesTheDirectEngineForIt() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add);
        uiStates.clear();

        select(MediaEngine.LEGACY, MediaEngine.DIRECT);

        assertEquals("the in-flight direct command ends without a second dispatch",
                MediaCommandResult.Outcome.FAILED, results.get(0).outcome);
        music.state(PlaybackState.STATE_PLAYING).emit();
        for (MediaSourceState state : uiStates) {
            assertFalse("no direct Music state reaches the UI after deselection",
                    state.key == MediaSourceKey.MUSIC && state.engine == MediaEngine.DIRECT);
        }
        facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.NEXT, results::add);
        assertEquals(1, music.dispatched.size());
        assertEquals(Arrays.asList("command MUSIC com.tw.media NEXT"), legacy.log);
        assertTrue("Radio is still served by the direct engine", source.listener != null);
    }

    @Test public void uiReceivesStateOnlyFromTheSelectedEngine() {
        select(MediaEngine.DIRECT, MediaEngine.LEGACY);
        uiStates.clear();
        music.state(PlaybackState.STATE_PLAYING).item("a", "Song", "Artist").emit();
        radio.state(PlaybackState.STATE_PLAYING).item("", "Station", "").emit();
        assertEquals(1, uiStates.size());
        assertEquals(MediaSourceKey.MUSIC, uiStates.get(0).key);
        assertEquals("Song", uiStates.get(0).title);
    }
}
