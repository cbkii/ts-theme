package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaSourceState.Availability;
import com.cbkii.ts18launcher.media.MediaUserAction;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSession;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSource;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.ManualClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration review 2026-11-13 (F1/F3/F4): a deliberate second tap cancels an owed Play, every
 * Play to a deadline-capable source is bounded, and a refused bind is not blindly repeated.
 * Each case failed on 768fd36 before the correction.
 */
public class DirectCancelAndBoundedPlayTest {
    private static final String AUXIO = "com.tw.media";

    private static final class Starter implements DirectSourceEngine.Starter {
        BrowserSessionStarter.Events events;
        final List<String> log = new ArrayList<>();
        @Override public boolean canStart(String packageName) { return AUXIO.equals(packageName); }
        @Override public void start(Object owner, String packageName) { log.add("start"); }
        @Override public void abandon(Object owner, String packageName) { log.add("abandon"); }
        @Override public void release(Object owner, String packageName) { log.add("release"); }
    }

    private ManualClock clock;
    private Starter starter;
    private DirectSourceMediaBackend backend;
    private final List<MediaCommandResult> results = new ArrayList<>();

    @Before public void setUp() {
        clock = new ManualClock();
        starter = new Starter();
        backend = new DirectSourceMediaBackend(new FakeSource(), clock, new DirectSourceMediaBackend.Host() {
            @Override public String appLabel(String packageName) { return packageName; }
            @Override public void trace(String event, String detail) { }
        }, events -> { starter.events = events; return starter; });
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
    }

    private void tap() { backend.command(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE, results::add); }

    private FakeSession offer(FakeSession session) {
        starter.events.onSessionOffered(AUXIO, session);
        return session;
    }

    /** F1: the engine still admits one explicit attempt after a refusal (presentation is separate). */
    @Test public void connectionFailureIsNotAdvertisedAsStartable() {
        starter.events.onStartFailed(AUXIO, "media service connection failed");
        MediaSourceState state = backend.state(MediaSourceKey.MUSIC);
        assertEquals(Availability.CONNECTION_FAILED, state.availability);
        assertTrue("a fresh, explicit attempt stays possible", state.startable);
    }

    /** F3: the second primary tap while a cold Play is still pending is a cancel, not a join. */
    @Test public void secondPrimaryTapWhileStartingCancelsThePendingPlay() {
        tap();
        assertTrue(backend.state(MediaSourceKey.MUSIC).starting);
        clock.advanceTo(clock.now + 1000L);
        tap();
        FakeSession session = new FakeSession("t1", AUXIO).state(PlaybackState.STATE_NONE);
        session.deadlinePlay = true;
        offer(session);
        assertEquals("no Play may leave after the driver cancelled", Collections.emptyList(), session.dispatched);
    }

    /** F3: during HOME_PLAY restore (session PAUSED with item, play gate armed) a tap is Pause. */
    @Test public void tapAfterBoundedPlayButBeforePlaybackIsPause() {
        FakeSession session = new FakeSession("t1", AUXIO).state(PlaybackState.STATE_NONE);
        session.deadlinePlay = true;
        offer(session);
        tap();
        assertEquals(Collections.singletonList(DirectOperation.PLAY), session.dispatched);
        session.state(PlaybackState.STATE_PAUSED).item("raw:1", "Song", "Artist").emit();
        clock.advanceTo(clock.now + 1000L);
        tap();
        assertEquals("the second tap must stop the owed start",
                DirectOperation.PAUSE, session.dispatched.get(session.dispatched.size() - 1));
    }

    /** F4: every launcher Play to Auxio carries a tap-anchored deadline, loaded or not. */
    @Test public void loadedAuxioPlayIsStillBounded() {
        FakeSession session = new FakeSession("t1", AUXIO).state(PlaybackState.STATE_PAUSED)
                .item("raw:1", "Song", "Artist");
        session.deadlinePlay = true;
        offer(session);
        tap();
        assertEquals(Collections.singletonList(DirectOperation.PLAY), session.dispatched);
        assertTrue("loaded Auxio receives unbounded PLAY", session.dispatchedDeadline > clock.now);
    }

    /** F1: a refused bind is not repeated passively, and only once explicitly, until evidence changes. */
    @Test public void refusedBindIsNotRepeatedWithoutNewEvidence() {
        starter.events.onStartFailed(AUXIO, "media service connection failed");
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 2);
        assertEquals("passive readiness repeated the refused bind", Collections.emptyList(), starter.log);
        tap();
        assertEquals("one explicit retry is admitted", Collections.singletonList("start"), starter.log);
        starter.events.onStartFailed(AUXIO, "media service connection failed");
        clock.advanceTo(clock.now + 1000L);
        tap();
        assertEquals("a second identical refusal is not retried at once", 1, starter.log.size());
        assertFalse(results.get(results.size() - 1).dispatched);
        assertTrue(results.get(results.size() - 1).message.contains("refused"));
        backend.prerequisitesChanged();
        tap();
        assertEquals("unlock/storage/wake/package evidence re-admits the bind", 2, starter.log.size());
    }

    @Test public void refusalBackoffExpiresWithoutOtherEvidence() {
        starter.events.onStartFailed(AUXIO, "media service connection failed");
        tap();
        starter.events.onStartFailed(AUXIO, "media service connection failed");
        clock.advanceTo(clock.now + DirectSourceEngine.REFUSED_RETRY_MS);
        backend.preparePassive(MediaSourceKey.MUSIC, AUXIO, 3);
        assertEquals(2, starter.log.size());
    }

    @Test public void timedOutBindIsNotTreatedAsRefusal() {
        tap();
        clock.advanceTo(clock.now + DirectSourceEngine.COLD_START_MS);
        assertEquals(Availability.CONNECTION_FAILED, backend.state(MediaSourceKey.MUSIC).availability);
        tap();
        tap();
        assertTrue("a slow source may answer next time", starter.log.stream().filter("start"::equals).count() >= 2);
    }
}
