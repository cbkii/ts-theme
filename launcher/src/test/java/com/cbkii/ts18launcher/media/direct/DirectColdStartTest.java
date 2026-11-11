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
 * Source-owned cold start: a Play tap on a source that is not running asks that source's own
 * media service for its session, then sends the one Play. Scripted; no real service is bound.
 */
public class DirectColdStartTest {
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
        FakeSession session = new FakeSession(token, AUXIO).state(state);
        session.deadlinePlay = true; // This fixture models the companion's advertised HOME_PLAY_V1.
        starter.events.onSessionOffered(AUXIO, session);
        return session;
    }

    @Test public void notRunningSourceIsShownStartableAndNothingStartsWithoutATap() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        MediaSourceState state = backend.state(MediaSourceKey.MUSIC);
        assertEquals(Availability.NO_SESSION, state.availability);
        assertTrue(state.startable);
        assertFalse(state.starting);
        backend.refresh();
        source.changed();
        assertTrue("attaching, refreshing and membership changes never start a source", starter.log.isEmpty());
    }

    @Test public void playTapStartsTheSourceOnceThenSendsExactlyOnePlay() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList("start com.tw.media"), starter.log);
        assertTrue(backend.state(MediaSourceKey.MUSIC).starting);
        assertTrue("nothing is settled while the source starts", results.isEmpty());

        FakeSession session = offer("t1", PlaybackState.STATE_NONE);
        assertEquals(Arrays.asList(DirectOperation.PLAY), session.dispatched);
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
        assertTrue("Play is sent but not yet confirmed", results.isEmpty());

        session.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(Outcome.ACKNOWLEDGED, lastResult().outcome);
        assertTrue(lastResult().playingObserved);
        assertEquals(1, session.dispatched.size());
        assertEquals(1, starter.log.size());
    }

    @Test public void repeatedTapsWhileStartingJoinTheOneAttempt() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(1, starter.log.size());

        FakeSession session = offer("t1", PlaybackState.STATE_PAUSED);
        session.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(1, session.dispatched.size());
        assertEquals(3, results.size());
        for (MediaCommandResult result : results) assertEquals(Outcome.ACKNOWLEDGED, result.outcome);
    }

    @Test public void skipOnANotRunningSourceNeverStartsIt() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.NEXT);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PREVIOUS);
        assertTrue(starter.log.isEmpty());
        assertEquals(Outcome.FAILED, lastResult().outcome);
    }

    @Test public void sourceThatResumesByItselfIsAcknowledgedWithoutSendingPlay() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        FakeSession session = offer("t1", PlaybackState.STATE_PLAYING);
        assertEquals(0, session.dispatched.size());
        assertEquals(Outcome.ACKNOWLEDGED, lastResult().outcome);
        assertFalse(lastResult().dispatched);
        assertTrue(lastResult().playingObserved);
    }

    @Test public void startThatNeverProducesASessionFailsAtTheDeadlineWithoutRetry() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        clock.expireAll();
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertFalse(lastResult().dispatched);
        assertEquals("the stalled attempt is abandoned, never retried by itself",
                Arrays.asList("start com.tw.media", "abandon com.tw.media"), starter.log);
        assertFalse(backend.state(MediaSourceKey.MUSIC).starting);
        // A session arriving after the deadline becomes controllable but is not auto-played.
        FakeSession late = offer("t1", PlaybackState.STATE_PAUSED);
        assertEquals(0, late.dispatched.size());
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
    }

    @Test public void refusedStartIsReportedImmediately() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        starter.events.onStartFailed(AUXIO, "refused the connection");
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertTrue(lastResult().message.contains("refused the connection"));
        assertEquals(0, clock.scheduled.size());
    }

    @Test public void startedSessionWithoutPlayIsUnsupportedNotForced() {
        backend.attach(MediaSourceKey.MUSIC, "com.example.player");
        starter.startable.add("com.example.player");
        source.changed();
        backend.command(MediaSourceKey.MUSIC, "com.example.player", MediaUserAction.PLAY_PAUSE, results::add);
        FakeSession session = new FakeSession("t1", "com.example.player")
                .state(PlaybackState.STATE_NONE).actions(0L);
        starter.events.onSessionOffered("com.example.player", session);
        assertEquals(Outcome.UNSUPPORTED, lastResult().outcome);
        assertEquals(0, session.dispatched.size());
    }

    @Test public void packageWithoutAMediaServiceIsNotStartableAndIsNeverAsked() {
        backend.attach(MediaSourceKey.RADIO, STOCK_RADIO);
        assertFalse(backend.state(MediaSourceKey.RADIO).startable);
        tap(MediaSourceKey.RADIO, STOCK_RADIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertTrue(starter.log.isEmpty());
    }

    @Test public void startingMusicNeverTouchesAPlayingRadio() {
        FakeSession radio = new FakeSession("r", "com.navimods.radio").state(PlaybackState.STATE_PLAYING);
        source.sessions.add(radio);
        backend.attach(MediaSourceKey.RADIO, "com.navimods.radio");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        clock.expireAll();
        assertEquals(0, radio.dispatched.size());
        assertTrue(backend.playing(MediaSourceKey.RADIO, "com.navimods.radio"));
    }

    @Test public void deselectingOrReconfiguringEndsTheAttemptAndReleasesTheSource() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        backend.detach(MediaSourceKey.MUSIC);
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertEquals(Arrays.asList("start com.tw.media", "release com.tw.media"), starter.log);
        assertEquals(0, clock.scheduled.size());
    }

    @Test public void withdrawnSessionIsLostAndTheNextPlayStartsAgain() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        FakeSession session = offer("t1", PlaybackState.STATE_PAUSED);
        session.state(PlaybackState.STATE_PLAYING).emit();

        starter.events.onSessionWithdrawn(AUXIO, "t1");
        assertEquals(Availability.SOURCE_LOST, backend.state(MediaSourceKey.MUSIC).availability);
        assertTrue(backend.state(MediaSourceKey.MUSIC).startable);
        assertFalse(session.watched());

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList("start com.tw.media", "start com.tw.media"), starter.log);
    }

    @Test public void retryAfterATimeoutIsAFreshAttempt() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        clock.expireAll();
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList("start com.tw.media", "abandon com.tw.media", "start com.tw.media"),
                starter.log);
        assertTrue(backend.state(MediaSourceKey.MUSIC).starting);
    }

    @Test public void revokingListenerAccessPreservesTheSourceOwnedConnection() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        FakeSession session = offer("t1", PlaybackState.STATE_PLAYING);
        source.access = false;
        source.changed();
        assertFalse(starter.log.contains("release com.tw.media"));
        assertTrue(session.watched());
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
    }

    @Test public void sessionListedByThePlatformAndOfferedByTheSourceIsOneSession() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        FakeSession offered = offer("t1", PlaybackState.STATE_PAUSED);
        // The same platform session then also appears in the listed membership.
        source.sessions.add(new FakeSession("t1", AUXIO).state(PlaybackState.STATE_PAUSED));
        source.changed();
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
        assertEquals("t1", backend.state(MediaSourceKey.MUSIC).sessionIdentity);
        assertEquals("the pending Play was sent once, to the one session", 1, offered.dispatched.size());
    }

    @Test public void browserSourceCanStartWithoutNotificationAccess() {
        source.access = false;
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        assertTrue(backend.state(MediaSourceKey.MUSIC).startable);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertTrue(starter.log.contains("start com.tw.media"));
        FakeSession session = offer("owned", PlaybackState.STATE_PAUSED);
        assertEquals(1, session.dispatched.size());
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
    }

    @Test public void uncertainColdSourceWithoutDeadlineCapabilityNeverReceivesOrdinaryPlay() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        FakeSession session = new FakeSession("unsupported", AUXIO);
        session.deadlinePlay = false;
        starter.events.onSessionOffered(AUXIO, session);
        assertTrue(session.dispatched.isEmpty());
        assertEquals(Outcome.UNSUPPORTED, lastResult().outcome);
        assertFalse(lastResult().dispatched);
    }

    @Test public void bindingConsumesTheOriginalDeadlineAndDoesNotResetPlayBudget() {
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        clock.advanceTo(1000L + DirectSourceEngine.COLD_START_MS - 100L);
        FakeSession session = offer("bounded", PlaybackState.STATE_PAUSED);
        assertEquals(1000L + DirectSourceEngine.COLD_START_MS, session.dispatchedDeadline);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(1, session.dispatched.size());
        clock.advanceTo(1000L + DirectSourceEngine.COLD_START_MS - 1L);
        assertTrue(results.isEmpty());
        clock.advanceTo(1000L + DirectSourceEngine.COLD_START_MS);
        assertEquals(Outcome.SENT_UNCONFIRMED, lastResult().outcome);
        assertTrue(lastResult().dispatched);
        assertEquals(1, session.dispatched.size());
    }
}
