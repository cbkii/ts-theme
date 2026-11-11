package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
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
 * A handover pause is committed exactly once per logical Play, only to the exact owner captured
 * at tap time, through the real facade and the real direct engine. A pause sent to the other
 * source does not change its observed state until that session reports it, which is the
 * asynchronous path a synchronous fake would hide.
 */
public class HandoverCommitTest {
    private static final String AUXIO = "com.tw.media";
    private static final String NAVRADIO = "com.navimods.radio";
    private static final String OTHER_RADIO = "com.example.radio";

    /** Legacy stand-in that is playing until it is told to pause, and records each pause. */
    private static final class Legacy implements MediaBackend {
        final List<String> log = new ArrayList<>();
        final Map<MediaSourceKey, String> attached = new EnumMap<>(MediaSourceKey.class);
        boolean playing = true;
        final List<ResultCallback> pending = new ArrayList<>();
        @Override public MediaEngine engine() { return MediaEngine.LEGACY; }
        @Override public void attach(MediaSourceKey key, String packageName) { attached.put(key, packageName); }
        @Override public void detach(MediaSourceKey key) { attached.remove(key); }
        @Override public boolean attached(MediaSourceKey key) { return attached.containsKey(key); }
        @Override public MediaSourceState state(MediaSourceKey key) {
            String pkg = attached.get(key);
            return new MediaSourceState(key, MediaEngine.LEGACY, pkg == null ? "" : pkg,
                    MediaSourceState.Availability.LIVE, playing ? PlaybackState.STATE_PLAYING
                    : PlaybackState.STATE_PAUSED, DirectTestKit.FULL, "legacy-session", "", "", "", 7L);
        }
        @Override public boolean playing(MediaSourceKey key, String packageName) {
            return attached.containsKey(key) && playing;
        }
        @Override public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                                      ResultCallback callback) {
            log.add("command " + key);
            pending.add(callback);
        }
        // Like the real engine, a pause is a request; the state changes only when observed.
        @Override public void pauseForHandover(MediaSourceKey key) { log.add("pause " + key); }
        @Override public void setStateListener(StateListener listener) { }
    }

    private final Map<MediaSourceKey, MediaEngine> engines = new EnumMap<>(MediaSourceKey.class);
    private final Map<MediaSourceKey, String> packages = new EnumMap<>(MediaSourceKey.class);
    private FakeSource source;
    private ManualClock clock;
    private Legacy legacy;
    private MediaFeatureFacade facade;
    private FakeSession music;
    private FakeSession radio;
    private final List<MediaCommandResult> results = new ArrayList<>();

    @Before public void setUp() {
        source = new FakeSource();
        clock = new ManualClock();
        music = new FakeSession("music", AUXIO).state(PlaybackState.STATE_PAUSED);
        radio = new FakeSession("radio", NAVRADIO).state(PlaybackState.STATE_PLAYING);
        source.sessions.add(music);
        source.sessions.add(radio);
        legacy = new Legacy();
        DirectSourceMediaBackend direct = new DirectSourceMediaBackend(source, clock,
                new DirectSourceMediaBackend.Host() {
                    @Override public String appLabel(String packageName) { return packageName; }
                    @Override public void trace(String event, String detail) { }
                });
        packages.put(MediaSourceKey.MUSIC, AUXIO);
        packages.put(MediaSourceKey.RADIO, NAVRADIO);
        facade = new MediaFeatureFacade(new MediaFeatureFacade.Configuration() {
            @Override public MediaEngine requestedEngine(MediaSourceKey key) {
                MediaEngine engine = engines.get(key);
                return engine == null ? MediaEngine.LEGACY : engine;
            }
            @Override public String configuredPackage(MediaSourceKey key) { return packages.get(key); }
        }, legacy, direct);
    }

    private void select(MediaEngine musicEngine, MediaEngine radioEngine) {
        engines.put(MediaSourceKey.MUSIC, musicEngine);
        engines.put(MediaSourceKey.RADIO, radioEngine);
        facade.reconcile();
    }

    private void tapMusic() { facade.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add); }

    private long radioPauses() {
        return radio.dispatched.stream().filter(op -> op == DirectOperation.PAUSE).count();
    }

    @Test public void coalescedTapsOnDirectMusicPauseDirectRadioOnce() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        tapMusic(); tapMusic(); tapMusic();
        assertEquals(Arrays.asList(DirectOperation.PLAY), music.dispatched);

        music.state(PlaybackState.STATE_PLAYING).emit();

        assertEquals(3, results.size());
        assertEquals("one logical Play commits one handover", 1, radioPauses());
    }

    @Test public void coalescedTapsOnDirectMusicPauseLegacyRadioOnce() {
        select(MediaEngine.DIRECT, MediaEngine.LEGACY);
        tapMusic(); tapMusic();
        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(Arrays.asList("pause RADIO"), legacy.log);
    }

    @Test public void repeatedHandoverRequestsToOneSessionSendOnePause() {
        select(MediaEngine.LEGACY, MediaEngine.DIRECT);
        DirectSourceMediaBackend direct = backendOf();
        direct.pauseForHandover(MediaSourceKey.RADIO);
        direct.pauseForHandover(MediaSourceKey.RADIO);
        assertEquals("a pause in flight to the same session is not repeated", 1, radioPauses());
        // Once the session reports it paused and resumes, a later handover may pause it again.
        radio.state(PlaybackState.STATE_PAUSED).emit();
        radio.state(PlaybackState.STATE_PLAYING).emit();
        direct.pauseForHandover(MediaSourceKey.RADIO);
        assertEquals(2, radioPauses());
    }

    @Test public void pauseGuardExpiresWithoutConfirmation() {
        select(MediaEngine.LEGACY, MediaEngine.DIRECT);
        DirectSourceMediaBackend direct = backendOf();
        direct.pauseForHandover(MediaSourceKey.RADIO);
        clock.expireAll();
        direct.pauseForHandover(MediaSourceKey.RADIO);
        assertEquals(2, radioPauses());
    }

    @Test public void lateAcknowledgementDoesNotPauseANewlySelectedRadioApp() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        tapMusic();
        // Before Music confirms, the user switches Radio to another app that is also playing.
        FakeSession otherRadio = new FakeSession("other", OTHER_RADIO).state(PlaybackState.STATE_PLAYING);
        source.sessions.add(otherRadio);
        packages.put(MediaSourceKey.RADIO, OTHER_RADIO);
        facade.reconcile();

        music.state(PlaybackState.STATE_PLAYING).emit();

        assertEquals(0, otherRadio.dispatched.size());
        assertEquals(0, radioPauses());
    }

    @Test public void lateAcknowledgementDoesNotPauseARestartedRadioSession() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        tapMusic();
        // Radio restarts under a new token, still playing.
        source.sessions.remove(radio);
        FakeSession restarted = new FakeSession("radio-2", NAVRADIO).state(PlaybackState.STATE_PLAYING);
        source.sessions.add(restarted);
        source.changed();

        music.state(PlaybackState.STATE_PLAYING).emit();

        assertEquals("a different session than the one captured is never paused", 0, restarted.dispatched.size());
    }

    @Test public void newerRadioTapIsNotUndoneByAnOlderMusicAcknowledgement() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        tapMusic();
        // The driver presses Radio Next before Music confirms: Radio is the newer intent.
        facade.command(MediaSourceKey.RADIO, NAVRADIO, MediaUserAction.NEXT, results::add);

        music.state(PlaybackState.STATE_PLAYING).emit();

        assertEquals(0, radioPauses());
    }

    @Test public void engineSwitchBeforeAcknowledgementVoidsTheHandover() {
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        tapMusic();
        select(MediaEngine.DIRECT, MediaEngine.LEGACY);
        music.state(PlaybackState.STATE_PLAYING).emit();
        assertTrue(legacy.log.isEmpty());
        assertEquals(0, radioPauses());
    }

    @Test public void legacyMusicAcknowledgementPausesDirectRadioOnceEvenIfDeliveredTwice() {
        select(MediaEngine.LEGACY, MediaEngine.DIRECT);
        legacy.playing = false;
        tapMusic();
        tapMusic();
        legacy.playing = true;
        MediaCommandResult ok = new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED, "", true, true);
        legacy.pending.get(0).onResult(ok);
        legacy.pending.get(1).onResult(ok);
        assertEquals(1, radioPauses());
    }

    @Test public void bothLegacyKeepsLegacyOwnHandover() {
        select(MediaEngine.LEGACY, MediaEngine.LEGACY);
        legacy.playing = false;
        tapMusic();
        legacy.pending.get(0).onResult(new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED, "", true, true));
        assertEquals(Arrays.asList("command MUSIC"), legacy.log);
    }

    @Test public void notPlayingOppositeIsNeverCapturedOrPaused() {
        radio.state(PlaybackState.STATE_PAUSED);
        select(MediaEngine.DIRECT, MediaEngine.DIRECT);
        tapMusic();
        radio.state(PlaybackState.STATE_PLAYING).emit(); // starts on its own, after the tap
        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals("only an owner playing at tap time is handed over", 0, radioPauses());
    }

    private DirectSourceMediaBackend backendOf() {
        try {
            java.lang.reflect.Field field = MediaFeatureFacade.class.getDeclaredField("direct");
            field.setAccessible(true);
            return (DirectSourceMediaBackend) field.get(facade);
        } catch (ReflectiveOperationException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
