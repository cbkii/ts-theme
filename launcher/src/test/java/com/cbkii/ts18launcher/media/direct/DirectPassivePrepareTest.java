package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaCommandResult.Outcome;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaSourceState.Availability;
import com.cbkii.ts18launcher.media.MediaUserAction;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSession;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSource;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.ManualClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Passive readiness: on a boot, wake or HOME epoch a Direct source that is not running is bound
 * through its own media browser and, only if it advertises PREPARE and has nothing loaded, asked
 * once to load its queue. Never Play, never twice per epoch, always superseded by a user tap.
 */
public class DirectPassivePrepareTest {
    private static final String AUXIO = "com.tw.media";
    private static final String STOCK_RADIO = "com.tw.radio";

    /** Scripted stand-in for the source's exported media browser service. */
    private static final class FakeStarter implements DirectSourceEngine.Starter {
        final Set<String> startable = new HashSet<>();
        final List<String> log = new ArrayList<>();
        BrowserSessionStarter.Events events;
        @Override public boolean canStart(String packageName) { return startable.contains(packageName); }
        @Override public void start(Object owner, String packageName) { log.add("start " + packageName); }
        @Override public void abandon(Object owner, String packageName) { log.add("abandon " + packageName); }
        @Override public void release(Object owner, String packageName) { log.add("release " + packageName); }
    }

    private FakeSource source;
    private ManualClock clock;
    private FakeStarter starter;
    private DirectSourceMediaBackend backend;
    private final List<MediaCommandResult> results = new ArrayList<>();
    private final List<MediaSourceState> published = new ArrayList<>();

    @Before public void setUp() {
        source = new FakeSource();
        clock = new ManualClock();
        starter = new FakeStarter();
        starter.startable.add(AUXIO);
        backend = new DirectSourceMediaBackend(source, clock, new DirectSourceMediaBackend.Host() {
            @Override public String appLabel(String packageName) { return packageName; }
            @Override public void trace(String event, String detail) { }
        }, events -> { starter.events = events; return starter; });
        backend.setStateListener(published::add);
    }

    private void tap(MediaSourceKey key, String packageName, MediaUserAction action) {
        backend.command(key, packageName, action, results::add);
    }

    private MediaCommandResult lastResult() { return results.get(results.size() - 1); }

    private FakeSession offer(String token, int state) {
        // Auxio-TS advertises PREPARE alongside its transport actions.
        FakeSession session = new FakeSession(token, AUXIO).state(state)
                .actions(DirectTestKit.FULL | PlaybackState.ACTION_PREPARE);
        starter.events.onSessionOffered(AUXIO, session);
        return session;
    }


    private static final long NO_PREPARE = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE;

    @Test public void coldSourceIsBoundAndPreparedOnceWithoutPlay() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        assertEquals(Arrays.asList("start com.tw.media"), starter.log);
        assertTrue(backend.state(MediaSourceKey.MUSIC).preparing);
        assertFalse(backend.state(MediaSourceKey.MUSIC).starting);

        FakeSession session = offer("t1", PlaybackState.STATE_NONE);
        assertEquals(Arrays.asList(DirectOperation.PREPARE), session.dispatched);
        assertTrue(backend.state(MediaSourceKey.MUSIC).preparing);

        session.state(PlaybackState.STATE_PAUSED).item("song-1", "Song", "Artist").emit();
        MediaSourceState ready = backend.state(MediaSourceKey.MUSIC);
        assertFalse(ready.preparing);
        assertEquals(Availability.LIVE, ready.availability);
        assertEquals("song-1", ready.itemId);
        assertFalse(ready.playing());
        assertEquals(1, session.dispatched.size());
        assertTrue("passive readiness reports no command result", results.isEmpty());
    }

    @Test public void sameEpochNeverPreparesTwiceButANewEpochMay() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        assertEquals(1, starter.log.size());
        FakeSession session = offer("t1", PlaybackState.STATE_NONE);
        clock.expireAll();
        assertEquals(1, session.dispatched.size());
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        assertEquals(1, session.dispatched.size());
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 2L);
        assertEquals(Arrays.asList(DirectOperation.PREPARE, DirectOperation.PREPARE), session.dispatched);
        assertEquals(1, starter.log.size());
    }

    @Test public void alreadyLoadedOrNonPreparingSourcesGetNothing() {
        FakeSession loaded = new FakeSession("t1", AUXIO).state(PlaybackState.STATE_PAUSED).item("i", "T", "A")
                .actions(DirectTestKit.FULL | PlaybackState.ACTION_PREPARE);
        source.sessions.add(loaded);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        source.changed();
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        assertTrue(loaded.dispatched.isEmpty());

        loaded.item("", "", "").actions(NO_PREPARE);
        loaded.emit();
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 2L);
        assertTrue("PREPARE is only sent when advertised", loaded.dispatched.isEmpty());
        assertTrue(starter.log.isEmpty());
    }

    @Test public void coldSourceWithoutPrepareIsBoundButNeverCommanded() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        FakeSession session = new FakeSession("t1", AUXIO).state(PlaybackState.STATE_NONE).actions(NO_PREPARE);
        starter.events.onSessionOffered(AUXIO, session);
        assertTrue(session.dispatched.isEmpty());
        assertFalse(backend.state(MediaSourceKey.MUSIC).preparing);
    }

    @Test public void userPlayDuringPassivePreparationWinsAndNoPrepareIsSent() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertTrue(backend.state(MediaSourceKey.MUSIC).starting);
        FakeSession session = offer("t1", PlaybackState.STATE_PAUSED);
        assertEquals(Arrays.asList(DirectOperation.PLAY), session.dispatched);
        session.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(Outcome.ACKNOWLEDGED, lastResult().outcome);
    }

    @Test public void preparationTimesOutHonestlyAndReleasesItsClaim() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        clock.expireAll();
        assertEquals(Arrays.asList("start com.tw.media", "abandon com.tw.media"), starter.log);
        MediaSourceState state = backend.state(MediaSourceKey.MUSIC);
        assertFalse(state.preparing);
        assertTrue("Play still works after a failed passive attempt", state.startable);
    }

    @Test public void sentPrepareWithNoLoadedItemEndsAfterItsWindow() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        FakeSession session = offer("t1", PlaybackState.STATE_NONE);
        assertEquals(Arrays.asList(DirectOperation.PREPARE), session.dispatched);
        clock.expireAll();
        assertFalse(backend.state(MediaSourceKey.MUSIC).preparing);
        assertEquals(1, session.dispatched.size());
    }

    @Test public void browserReadinessDoesNotRequireListenerAccessAndDetachCancels() {
        source.access = false;
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        source.changed();
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        assertTrue(starter.log.contains("start com.tw.media"));
        source.access = true;
        source.changed();
        assertEquals("deferred epoch runs once access is known", 1,
                java.util.Collections.frequency(starter.log, "start com.tw.media"));
        backend.detach(MediaSourceKey.MUSIC);
        assertTrue(starter.log.contains("release com.tw.media"));
        assertFalse(backend.state(MediaSourceKey.MUSIC).preparing);
    }

    @Test public void wrongPackageOrUnattachedSourceIsIgnored() {
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, STOCK_RADIO, 1L);
        assertTrue(starter.log.isEmpty());
    }

    @Test public void sessionReplacedWhilePreparingCancelsWithoutSendingToTheNewOne() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 1L);
        FakeSession first = offer("t1", PlaybackState.STATE_NONE);
        starter.events.onSessionWithdrawn(AUXIO, "t1");
        FakeSession second = new FakeSession("t2", AUXIO).state(PlaybackState.STATE_NONE)
                .actions(DirectTestKit.FULL | PlaybackState.ACTION_PREPARE);
        source.sessions.add(second);
        source.changed();
        assertEquals(1, first.dispatched.size());
        assertTrue(second.dispatched.isEmpty());
        assertFalse(backend.state(MediaSourceKey.MUSIC).preparing);
    }
}
