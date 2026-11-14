package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaCommandResult.Outcome;
import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaSourceState.Availability;
import com.cbkii.ts18launcher.media.MediaUserAction;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSession;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSource;
import com.cbkii.ts18launcher.media.direct.DirectTestKit.ManualClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Behaviour of the complete direct engine (registry + per-source state machines) against a
 * scripted session authority. Pure JVM: no framework session, device or audio is involved.
 */
public class DirectSourceMediaBackendTest {
    private static final String AUXIO = "com.tw.media";
    private static final String NAVRADIO = "com.navimods.radio";
    private static final String OTHER = "com.example.player";
    private static final String FOREIGN = "com.spotify.music";

    private FakeSource source;
    private ManualClock clock;
    private DirectSourceMediaBackend backend;
    private final List<String> trace = new ArrayList<>();
    private final List<MediaSourceState> published = new ArrayList<>();
    private final List<MediaCommandResult> results = new ArrayList<>();

    @Before public void setUp() {
        source = new FakeSource();
        clock = new ManualClock();
        backend = new DirectSourceMediaBackend(source, clock, new DirectSourceMediaBackend.Host() {
            @Override public String appLabel(String packageName) { return "App(" + packageName + ")"; }
            @Override public void trace(String event, String detail) { trace.add(event + " " + detail); }
        });
        backend.setStateListener(published::add);
    }

    private FakeSession session(String token, String packageName) {
        FakeSession session = new FakeSession(token, packageName).item("loaded-" + token, "Loaded item", "");
        source.sessions.add(session);
        return session;
    }

    private void tap(MediaSourceKey key, String packageName, MediaUserAction action) {
        backend.command(key, packageName, action, results::add);
    }

    private MediaCommandResult lastResult() { return results.get(results.size() - 1); }

    // ---- idle and ownership -------------------------------------------------------------

    @Test public void holdsNoSubscriptionUntilASourceIsAttachedAndDropsEverythingWhenNoneRemain() {
        FakeSession music = session("t1", AUXIO);
        assertNull(source.listener);
        assertEquals(0, source.reads);

        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        assertTrue(source.listener != null);
        assertTrue(music.watched());
        backend.attach(MediaSourceKey.RADIO, NAVRADIO);
        assertEquals("one callback per token however many sources are attached", 1, music.watches);

        backend.detach(MediaSourceKey.MUSIC);
        assertTrue("still serving Radio", source.listener != null);
        backend.detach(MediaSourceKey.RADIO);
        assertNull(source.listener);
        assertFalse(music.watched());
        assertEquals(0, clock.scheduled.size());
    }

    @Test public void detachedSourceNeverCommandsOrPublishes() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.detach(MediaSourceKey.MUSIC);
        published.clear();

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        music.state(PlaybackState.STATE_PLAYING).emit();
        source.changed();

        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertFalse(lastResult().dispatched);
        assertEquals(0, music.dispatched.size());
        assertTrue(published.isEmpty());
        assertFalse(backend.playing(MediaSourceKey.MUSIC, AUXIO));
    }

    @Test public void foreignPlayingSessionIsNeverSelectedOrCommanded() {
        FakeSession foreign = session("spotify", FOREIGN).state(PlaybackState.STATE_PLAYING)
                .item("s1", "Foreign Song", "Foreign Artist");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);

        MediaSourceState state = backend.state(MediaSourceKey.MUSIC);
        assertEquals(Availability.NO_SESSION, state.availability);
        assertEquals("", state.title);
        assertFalse(backend.playing(MediaSourceKey.MUSIC, AUXIO));

        for (MediaUserAction action : MediaUserAction.values()) tap(MediaSourceKey.MUSIC, AUXIO, action);
        // Addressing the foreign package through the Music button is refused, not redirected.
        tap(MediaSourceKey.MUSIC, FOREIGN, MediaUserAction.PLAY_PAUSE);

        assertEquals(0, foreign.dispatched.size());
        for (MediaCommandResult result : results) {
            assertEquals(Outcome.FAILED, result.outcome);
            assertFalse(result.dispatched);
        }
    }

    @Test public void foreignSessionAppearingLaterDoesNotDisturbThePinnedSource() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING).item("a", "Song A", "Artist");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        long generation = backend.state(MediaSourceKey.MUSIC).generation;

        FakeSession foreign = session("spotify", FOREIGN).state(PlaybackState.STATE_PLAYING).item("x", "X", "Y");
        source.changed();
        foreign.emit();

        MediaSourceState state = backend.state(MediaSourceKey.MUSIC);
        assertEquals("Song A", state.title);
        assertEquals(generation, state.generation);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList(DirectOperation.PAUSE), music.dispatched);
        assertEquals(0, foreign.dispatched.size());
    }

    @Test public void musicAndRadioAreIndependentOwners() {
        FakeSession music = session("m", AUXIO).state(PlaybackState.STATE_PAUSED);
        FakeSession radio = session("r", NAVRADIO).state(PlaybackState.STATE_PLAYING).item("", "Triple J", "105.7");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        backend.attach(MediaSourceKey.RADIO, NAVRADIO);

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);

        assertEquals(Arrays.asList(DirectOperation.PLAY), music.dispatched);
        assertEquals("the direct engine never pauses the other source by itself", 0, radio.dispatched.size());
        assertTrue(backend.playing(MediaSourceKey.RADIO, NAVRADIO));
        assertEquals("Triple J", backend.state(MediaSourceKey.RADIO).title);
        // A Radio tap addressed with the Music package is refused.
        tap(MediaSourceKey.RADIO, AUXIO, MediaUserAction.NEXT);
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertEquals(0, radio.dispatched.size());
    }

    // ---- directional commands -----------------------------------------------------------

    @Test public void oneTapIsOneDirectionalDispatchConfirmedOnlyByTheOwningSession() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList(DirectOperation.PLAY), music.dispatched);
        assertTrue("not settled until the session confirms", results.isEmpty());

        music.state(PlaybackState.STATE_BUFFERING).emit();
        assertTrue("buffering is not playing", results.isEmpty());
        assertFalse(backend.playing(MediaSourceKey.MUSIC, AUXIO));
        assertFalse(backend.state(MediaSourceKey.MUSIC).playing());

        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(Outcome.ACKNOWLEDGED, lastResult().outcome);
        assertTrue(lastResult().playingObserved);
        assertTrue(lastResult().dispatched);
        assertEquals(1, music.dispatched.size());
        assertEquals("the deadline is disarmed", 0, clock.scheduled.size());
    }

    @Test public void playingOrBufferingSourceIsPausedNotToggled() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_BUFFERING);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList(DirectOperation.PAUSE), music.dispatched);
        music.state(PlaybackState.STATE_PAUSED).emit();
        assertEquals(Outcome.ACKNOWLEDGED, lastResult().outcome);
        assertFalse(lastResult().playingObserved);
    }

    @Test public void rapidTapsJoinTheInFlightOperationAndNeverDispatchTwice() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(1, music.dispatched.size());

        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(3, results.size());
        for (MediaCommandResult result : results) assertEquals(Outcome.ACKNOWLEDGED, result.outcome);
        assertEquals(1, music.dispatched.size());
    }

    @Test public void slowAcknowledgementIsReportedUnconfirmedAndNeverRetried() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);

        clock.expireAll();

        assertEquals(Outcome.SENT_UNCONFIRMED, lastResult().outcome);
        assertTrue(lastResult().dispatched);
        assertFalse(lastResult().playingObserved);
        assertEquals(1, music.dispatched.size());
        // A late confirmation changes displayed state but cannot complete or repeat anything.
        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(1, results.size());
        assertEquals(1, music.dispatched.size());
        assertTrue(backend.playing(MediaSourceKey.MUSIC, AUXIO));
    }

    @Test public void playbackErrorAfterPlayFailsTruthfully() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        music.state(PlaybackState.STATE_ERROR).emit();
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertTrue(lastResult().dispatched);
        assertEquals(1, music.dispatched.size());
    }

    @Test public void unadvertisedActionIsUnsupportedForAGenericPlayerAndSendsNothing() {
        FakeSession player = session("t1", OTHER).state(PlaybackState.STATE_PLAYING)
                .actions(PlaybackState.ACTION_PAUSE);
        backend.attach(MediaSourceKey.MUSIC, OTHER);

        tap(MediaSourceKey.MUSIC, OTHER, MediaUserAction.NEXT);

        assertEquals(Outcome.UNSUPPORTED, lastResult().outcome);
        assertFalse(lastResult().dispatched);
        assertEquals(0, player.dispatched.size());
        // Its advertised Pause still works: any user-selected player is controllable as far as
        // it says it is.
        tap(MediaSourceKey.MUSIC, OTHER, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList(DirectOperation.PAUSE), player.dispatched);
    }

    @Test public void noPackageIsGrantedSkipsItDoesNotAdvertise() {
        // Applies equally to the expected source packages: a same-named replacement build that
        // does not advertise Next is told so, not sent a command it may ignore.
        for (String pkg : new String[] {AUXIO, NAVRADIO}) {
            FakeSource fresh = new FakeSource();
            FakeSession session = new FakeSession("t", pkg).state(PlaybackState.STATE_PLAYING)
                    .actions(DirectTestKit.PLAYABLE);
            fresh.sessions.add(session);
            DirectSourceMediaBackend other = new DirectSourceMediaBackend(fresh, new ManualClock(),
                    new DirectSourceMediaBackend.Host() {
                        @Override public String appLabel(String packageName) { return packageName; }
                        @Override public void trace(String event, String detail) { }
                    });
            other.attach(MediaSourceKey.MUSIC, pkg);
            List<MediaCommandResult> got = new ArrayList<>();
            other.command(MediaSourceKey.MUSIC, pkg, MediaUserAction.NEXT, got::add);
            assertEquals(pkg, Outcome.UNSUPPORTED, got.get(0).outcome);
            assertEquals(0, session.dispatched.size());
            assertEquals(0L, other.state(MediaSourceKey.MUSIC).actions & PlaybackState.ACTION_SKIP_TO_NEXT);
        }
    }

    // ---- skips --------------------------------------------------------------------------

    @Test public void skipIsOneShotAndAcknowledgedOnlyByAnItemChange() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING).item("a", "Song A", "Artist");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.NEXT);
        music.emit();
        assertTrue("same item: dispatch alone is not confirmation", results.isEmpty());

        music.item("b", "Song B", "Artist").emit();
        assertEquals(Outcome.ACKNOWLEDGED, lastResult().outcome);
        assertEquals(Arrays.asList(DirectOperation.NEXT), music.dispatched);
        assertEquals("Song B", backend.state(MediaSourceKey.MUSIC).title);
    }

    @Test public void skipWithoutObservedChangeIsSentUnconfirmed() {
        FakeSession radio = session("r", NAVRADIO).state(PlaybackState.STATE_PLAYING).item("", "Station", "");
        backend.attach(MediaSourceKey.RADIO, NAVRADIO);
        tap(MediaSourceKey.RADIO, NAVRADIO, MediaUserAction.PREVIOUS);
        clock.expireAll();
        assertEquals(Outcome.SENT_UNCONFIRMED, lastResult().outcome);
        assertEquals(Arrays.asList(DirectOperation.PREVIOUS), radio.dispatched);
    }

    @Test public void everySkipTapIsItsOwnSingleDispatch() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING).item("a", "A", "");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.NEXT);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.NEXT);
        assertEquals(Arrays.asList(DirectOperation.NEXT, DirectOperation.NEXT), music.dispatched);
        music.item("c", "C", "").emit();
        assertEquals(2, results.size());
    }

    // ---- item identity and metadata -------------------------------------------------------

    @Test public void newItemUnderTheSameSessionNeverInheritsThePreviousText() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING)
                .item("a", "Song A", "Artist A");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        music.item("b", "", "").emit();
        MediaSourceState state = backend.state(MediaSourceKey.MUSIC);
        assertEquals("b", state.itemId);
        assertEquals("", state.title);
        assertEquals("", state.subtitle);
    }

    @Test public void identicalReadingsAreNotRepublished() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING).item("a", "Song A", "Artist");
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        int after = published.size();
        music.emit();
        music.emit();
        source.changed();
        assertEquals(after, published.size());
        music.item("a", "Song A (live)", "Artist").emit();
        assertEquals(after + 1, published.size());
    }

    // ---- session lifetime ---------------------------------------------------------------

    @Test public void sessionDeathMidCommandIsSourceLostAndLateCallbacksAreIgnored() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);

        source.sessions.clear();
        music.end();

        assertEquals(Outcome.SOURCE_LOST, lastResult().outcome);
        assertEquals(Availability.SOURCE_LOST, backend.state(MediaSourceKey.MUSIC).availability);
        assertEquals(0, clock.scheduled.size());
        // Nothing the dead session reports afterwards can revive state or complete anything.
        music.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(1, results.size());
        assertFalse(backend.playing(MediaSourceKey.MUSIC, AUXIO));

        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.SOURCE_LOST, lastResult().outcome);
        assertFalse(lastResult().dispatched);
        assertEquals(1, music.dispatched.size());
    }

    @Test public void restartedAppGetsAFreshGenerationAndTheOldCommandIsNotConfirmedByIt() {
        FakeSession first = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        long generation = backend.state(MediaSourceKey.MUSIC).generation;
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);

        // The app restarts: a new token, already playing, replaces the old one.
        source.sessions.clear();
        FakeSession second = session("t2", AUXIO).state(PlaybackState.STATE_PLAYING);
        source.changed();

        assertEquals("the old operation ends as lost, not acknowledged by a different session",
                Outcome.SOURCE_LOST, lastResult().outcome);
        assertTrue(backend.state(MediaSourceKey.MUSIC).generation > generation);
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
        assertFalse(first.watched());
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList(DirectOperation.PAUSE), second.dispatched);
        assertEquals(1, first.dispatched.size());
    }

    @Test public void dispatchToAVanishedSessionReportsSourceLostWithoutRetry() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        music.throwOnDispatch = true;
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.SOURCE_LOST, lastResult().outcome);
        assertFalse(lastResult().dispatched);
        assertEquals(0, clock.scheduled.size());
    }

    @Test public void sourcePackageChangeMidCommandEndsTheOperationAndRepins() {
        FakeSession auxio = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        FakeSession other = session("t2", OTHER).state(PlaybackState.STATE_PLAYING);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);

        backend.attach(MediaSourceKey.MUSIC, OTHER);

        assertEquals(Outcome.SOURCE_LOST, lastResult().outcome);
        assertEquals(OTHER, backend.state(MediaSourceKey.MUSIC).packageName);
        assertTrue(backend.playing(MediaSourceKey.MUSIC, OTHER));
        auxio.state(PlaybackState.STATE_PLAYING).emit();
        assertEquals(1, results.size());
        assertEquals(0, other.dispatched.size());
    }

    // ---- several sessions in one package --------------------------------------------------

    @Test public void twoIdleSessionsOfOnePackageAreAmbiguousAndNotGuessed() {
        FakeSession a = session("a", OTHER).state(PlaybackState.STATE_PAUSED);
        FakeSession b = session("b", OTHER).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, OTHER);
        assertEquals(Availability.AMBIGUOUS_SOURCE, backend.state(MediaSourceKey.MUSIC).availability);
        tap(MediaSourceKey.MUSIC, OTHER, MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertEquals(0, DirectTestKit.totalDispatches(a, b));
    }

    @Test public void theOneActiveSessionOfAPackageIsChosenAndStaysPinnedWhenItPauses() {
        FakeSession idle = session("a", OTHER).state(PlaybackState.STATE_NONE).actions(0L);
        FakeSession active = session("b", OTHER).state(PlaybackState.STATE_PLAYING);
        backend.attach(MediaSourceKey.MUSIC, OTHER);
        assertEquals("b", backend.state(MediaSourceKey.MUSIC).sessionIdentity);

        active.state(PlaybackState.STATE_PAUSED).emit();
        source.changed();
        assertEquals("b", backend.state(MediaSourceKey.MUSIC).sessionIdentity);
        tap(MediaSourceKey.MUSIC, OTHER, MediaUserAction.PLAY_PAUSE);
        assertEquals(Arrays.asList(DirectOperation.PLAY), active.dispatched);
        assertEquals(0, idle.dispatched.size());
    }

    @Test public void theOnlyControllableSessionIsChosenOverAnInertOne() {
        session("a", OTHER).state(PlaybackState.STATE_NONE).actions(0L);
        session("b", OTHER).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, OTHER);
        assertEquals("b", backend.state(MediaSourceKey.MUSIC).sessionIdentity);
    }

    // ---- listener access ------------------------------------------------------------------

    @Test public void missingAccessIsReportedAndNothingIsReadOrSent() {
        source.access = false;
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        assertEquals(Availability.PERMISSION_NEEDED, backend.state(MediaSourceKey.MUSIC).availability);
        assertEquals(0, source.reads);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.FAILED, lastResult().outcome);
        assertEquals(0, music.dispatched.size());
    }

    @Test public void accessRevokedWhileLiveDropsTheSessionAndPendingWork() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        tap(MediaSourceKey.MUSIC, AUXIO, MediaUserAction.PLAY_PAUSE);

        source.access = false;
        source.changed();

        assertEquals(Outcome.SOURCE_LOST, lastResult().outcome);
        assertEquals(Availability.PERMISSION_NEEDED, backend.state(MediaSourceKey.MUSIC).availability);
        assertFalse(music.watched());
        // Granting it again is picked up from the same change signal, with no polling.
        source.access = true;
        source.changed();
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
    }

    @Test public void accessListedButNotYetEffectiveIsTreatedAsMissing() {
        source.throwSecurity = true;
        session("t1", AUXIO);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        assertEquals(Availability.PERMISSION_NEEDED, backend.state(MediaSourceKey.MUSIC).availability);
        source.throwSecurity = false;
        backend.refresh();
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
    }

    @Test public void sessionServiceFailureIsNotProofOfNoSession() {
        FakeSession music = session("t1", AUXIO).state(PlaybackState.STATE_PLAYING);
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        source.throwRuntime = true;
        source.changed();
        // The pinned session keeps working through its own callbacks.
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.MUSIC).availability);
        assertTrue(music.watched());
        music.state(PlaybackState.STATE_PAUSED).emit();
        assertFalse(backend.playing(MediaSourceKey.MUSIC, AUXIO));
    }

    @Test public void sessionServiceFailureWithNothingPinnedIsUnknownNotNoSession() {
        source.throwRuntime = true;
        backend.attach(MediaSourceKey.RADIO, NAVRADIO);
        MediaSourceState state = backend.state(MediaSourceKey.RADIO);
        assertEquals(Availability.SERVICE_UNAVAILABLE, state.availability);
        assertFalse("an unknown source is not offered a cold start", state.startable);
        tap(MediaSourceKey.RADIO, NAVRADIO, MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.FAILED, lastResult().outcome);
        source.throwRuntime = false;
        session("r", NAVRADIO).state(PlaybackState.STATE_PAUSED);
        source.changed();
        assertEquals(Availability.LIVE, backend.state(MediaSourceKey.RADIO).availability);
    }

    @Test public void unconfiguredSourceIsReportedAndInert() {
        backend.attach(MediaSourceKey.RADIO, "");
        assertEquals(Availability.UNCONFIGURED, backend.state(MediaSourceKey.RADIO).availability);
        tap(MediaSourceKey.RADIO, "", MediaUserAction.PLAY_PAUSE);
        assertEquals(Outcome.FAILED, lastResult().outcome);
    }

    // ---- handover -------------------------------------------------------------------------

    @Test public void handoverPausesOnceOnlyWhenObservedPlaying() {
        FakeSession radio = session("r", NAVRADIO).state(PlaybackState.STATE_PAUSED);
        backend.attach(MediaSourceKey.RADIO, NAVRADIO);

        backend.pauseForHandover(MediaSourceKey.RADIO);
        assertEquals("not playing: nothing to pause", 0, radio.dispatched.size());

        radio.state(PlaybackState.STATE_BUFFERING).emit();
        backend.pauseForHandover(MediaSourceKey.RADIO);
        assertEquals("buffering is not observed playback", 0, radio.dispatched.size());

        radio.state(PlaybackState.STATE_PLAYING).emit();
        backend.pauseForHandover(MediaSourceKey.RADIO);
        assertEquals(Arrays.asList(DirectOperation.PAUSE), radio.dispatched);
    }

    @Test public void engineReportsItsIdentity() {
        assertEquals(MediaEngine.DIRECT, backend.engine());
        backend.attach(MediaSourceKey.MUSIC, AUXIO);
        assertEquals(MediaEngine.DIRECT, backend.state(MediaSourceKey.MUSIC).engine);
        assertTrue(backend.attached(MediaSourceKey.MUSIC));
        assertFalse(backend.attached(MediaSourceKey.RADIO));
    }
}
