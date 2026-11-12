package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaCommandResult.Outcome;
import com.cbkii.ts18launcher.media.MediaSourceKey;
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
 * Play to a live, listener-discovered session that has nothing loaded. The other backend tests use
 * a fixture that always carries an item, so this path is exercised here on its own: ordinary Play
 * is reserved for a confirmed loaded session, and an unloaded one is sent only the bounded
 * deadline Play it advertises, for either source slot.
 */
public class DirectStartContractTest {
    private static final String PLAYER = "com.example.player";
    private static final String RADIO = "com.navimods.radio";

    private FakeSource source;
    private ManualClock clock;
    private DirectSourceMediaBackend backend;
    private final List<MediaCommandResult> results = new ArrayList<>();
    private final List<String> states = new ArrayList<>();

    @Before public void setUp() {
        source = new FakeSource();
        clock = new ManualClock();
        backend = new DirectSourceMediaBackend(source, clock, new DirectSourceMediaBackend.Host() {
            @Override public String appLabel(String packageName) { return packageName; }
            @Override public void trace(String event, String detail) {
                if ("state".equals(event)) states.add(detail);
            }
        });
    }

    private MediaCommandResult last() { return results.get(results.size() - 1); }

    private FakeSession unloaded(String token, String packageName) {
        FakeSession session = new FakeSession(token, packageName).state(PlaybackState.STATE_PAUSED);
        source.sessions.add(session);
        return session;
    }

    @Test public void unloadedSessionWithoutTheDeadlineProtocolIsNotSentPlay() {
        for (MediaSourceKey key : new MediaSourceKey[] {MediaSourceKey.MUSIC, MediaSourceKey.RADIO}) {
            String pkg = key == MediaSourceKey.MUSIC ? PLAYER : RADIO;
            FakeSession session = unloaded("t-" + key, pkg);
            backend.attach(key, pkg);

            backend.command(key, pkg, MediaUserAction.PLAY_PAUSE, results::add);

            assertEquals(key.name(), Outcome.UNSUPPORTED, last().outcome);
            assertFalse(key.name(), last().dispatched);
            assertTrue(key.name(), session.dispatched.isEmpty());
        }
    }

    @Test public void unloadedSessionAdvertisingTheDeadlineProtocolGetsOneBoundedPlay() {
        FakeSession player = unloaded("m1", PLAYER);
        player.deadlinePlay = true;
        backend.attach(MediaSourceKey.MUSIC, PLAYER);
        long tappedAt = clock.now;

        backend.command(MediaSourceKey.MUSIC, PLAYER, MediaUserAction.PLAY_PAUSE, results::add);
        backend.command(MediaSourceKey.MUSIC, PLAYER, MediaUserAction.PLAY_PAUSE, results::add);

        assertEquals("a second tap joins the one operation",
                Collections.singletonList(DirectOperation.PLAY), player.dispatched);
        assertEquals(tappedAt + DirectSourceEngine.PLAY_ACK_MS, player.dispatchedDeadline);
        player.state(PlaybackState.STATE_PLAYING).item("song", "Song", "").emit();
        assertEquals(Outcome.ACKNOWLEDGED, last().outcome);
    }

    @Test public void onceTheSessionPublishesAnItemOrdinaryPlayIsUsed() {
        FakeSession radio = unloaded("r1", RADIO);
        backend.attach(MediaSourceKey.RADIO, RADIO);
        radio.item("", "ABC Classic", "").emit();

        backend.command(MediaSourceKey.RADIO, RADIO, MediaUserAction.PLAY_PAUSE, results::add);

        assertEquals(Collections.singletonList(DirectOperation.PLAY), radio.dispatched);
        assertEquals("ordinary Play carries no deadline", 0L, radio.dispatchedDeadline);
    }

    @Test public void pauseNeverDependsOnALoadedItem() {
        FakeSession radio = unloaded("r1", RADIO).state(PlaybackState.STATE_PLAYING);
        backend.attach(MediaSourceKey.RADIO, RADIO);

        backend.command(MediaSourceKey.RADIO, RADIO, MediaUserAction.PLAY_PAUSE, results::add);

        assertEquals(Collections.singletonList(DirectOperation.PAUSE), radio.dispatched);
    }

    @Test public void stateTraceRecordsWhatAPlayTapWouldMeetAndIgnoresMetadataChurn() {
        FakeSession player = unloaded("m1", PLAYER);
        backend.attach(MediaSourceKey.MUSIC, PLAYER);
        assertEquals(Collections.singletonList(
                "MUSIC avail=LIVE state=2 loaded=0 deadline=0 owned=0 startable=0 starting=0 preparing=0"), states);

        player.deadlinePlay = true;
        player.emit();
        player.item("a", "First", "").emit();
        player.item("b", "Second", "").emit();

        assertEquals(3, states.size());
        assertTrue(states.get(1).contains("loaded=0 deadline=1"));
        assertTrue(states.get(2).contains("loaded=1 deadline=1"));
    }
}
