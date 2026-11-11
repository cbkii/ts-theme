package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/** The N3 session lifecycle: opt-in, single owner, stale-report rejection, no self-retry. */
public class N3SessionTest {
    private static final String MAPS = "app.organicmaps.incar/app.organicmaps.SplashActivity";
    private final List<String> calls = new ArrayList<>();
    private final List<N3State> states = new ArrayList<>();
    private N3Session session;

    @Before public void setUp() {
        session = new N3Session(new N3Session.Host() {
            @Override public void createDisplay(long generation) { calls.add("create " + generation); }
            @Override public void launchApp(long generation, String component) { calls.add("launch " + generation); }
            @Override public void releaseDisplay(long generation) { calls.add("release " + generation); }
            @Override public void schedule(long generation, long deadlineMs) { }
            @Override public void onState(N3State state, String detail) { states.add(state); }
        });
    }

    private long reachInteractive() {
        session.enable(true);
        session.start(MAPS);
        long gen = session.generation();
        session.onDisplayCreated(gen);
        session.onLaunchAccepted(gen);
        session.onInteractive(gen);
        return gen;
    }

    @Test public void disabledUntilExplicitlyEnabledAndUnavailableWithoutAuthority() {
        assertEquals(N3State.DISABLED, session.state());
        session.start(MAPS);
        assertEquals(N3State.HOST_UNAVAILABLE, session.state());
        assertTrue(calls.isEmpty());
        session.enable(false);
        assertEquals(N3State.HOST_UNAVAILABLE, session.state());
    }

    @Test public void happyPathCreatesOneDisplayAndLaunchesOnce() {
        reachInteractive();
        assertEquals(N3State.INTERACTIVE, session.state());
        assertEquals(Arrays.asList("create 1", "launch 1"), calls);
        assertEquals(Arrays.asList(N3State.HOST_CONNECTING, N3State.DISPLAY_CREATING, N3State.APP_LAUNCHING,
                N3State.RENDER_AWAITING, N3State.INTERACTIVE), states);
    }

    @Test public void launchAcceptedIsNotInteractive() {
        session.enable(true);
        session.start(MAPS);
        long gen = session.generation();
        session.onDisplayCreated(gen);
        session.onLaunchAccepted(gen);
        assertEquals(N3State.RENDER_AWAITING, session.state());
    }

    @Test public void secondStartWhileActiveIsIgnored() {
        reachInteractive();
        session.start(MAPS);
        assertEquals(2, calls.size());
        assertEquals(N3State.INTERACTIVE, session.state());
    }

    @Test public void deadlineFailsReleasesAndNeverRetriesByItself() {
        session.enable(true);
        session.start(MAPS);
        long gen = session.generation();
        session.onDisplayCreated(gen);
        session.onDeadline(gen);
        assertEquals(N3State.FAILED, session.state());
        assertEquals(Arrays.asList("create 1", "launch 1", "release 1"), calls);
        // Late reports from the failed attempt change nothing.
        session.onLaunchAccepted(gen);
        session.onInteractive(gen);
        assertEquals(N3State.FAILED, session.state());
        assertEquals(3, calls.size());
    }

    @Test public void retryIsExplicitAndUsesAFreshGeneration() {
        session.enable(true);
        session.start(MAPS);
        long first = session.generation();
        session.onFailure(first, "refused");
        session.retry();
        long second = session.generation();
        assertTrue(second > first);
        assertEquals(N3State.DISPLAY_CREATING, session.state());
        session.onDisplayCreated(first);
        assertEquals("a stale display report is ignored", N3State.DISPLAY_CREATING, session.state());
        session.onDisplayCreated(second);
        assertEquals(N3State.APP_LAUNCHING, session.state());
    }

    @Test public void surfaceDetachKeepsTheDisplayAndReattachMustReconfirm() {
        long gen = reachInteractive();
        session.onSurfaceDetached(gen);
        assertEquals(N3State.SURFACE_DETACHED, session.state());
        assertEquals("the display is not released for a HOME stop", 2, calls.size());
        session.onSurfaceAttached(gen);
        assertEquals(N3State.REATTACHING, session.state());
        session.onInteractive(gen);
        assertEquals(N3State.INTERACTIVE, session.state());
    }

    @Test public void reattachTimeoutFailsVisibly() {
        long gen = reachInteractive();
        session.onSurfaceDetached(gen);
        session.onSurfaceAttached(gen);
        session.onDeadline(gen);
        assertEquals(N3State.FAILED, session.state());
        assertTrue(session.detail().contains("did not return"));
    }

    @Test public void contentLossFailsAndReleases() {
        long gen = reachInteractive();
        session.onContentLost(gen, "");
        assertEquals(N3State.FAILED, session.state());
        assertTrue(calls.contains("release " + gen));
    }

    @Test public void shutdownReleasesAndInvalidatesEverything() {
        long gen = reachInteractive();
        session.shutdown();
        assertEquals(N3State.SHUTDOWN, session.state());
        assertTrue(calls.contains("release " + gen));
        session.onInteractive(gen);
        session.onContentLost(gen, "x");
        assertEquals(N3State.SHUTDOWN, session.state());
    }

    @Test public void noTargetIsReportedNotGuessed() {
        session.enable(true);
        session.start("");
        assertEquals(N3State.HOST_UNAVAILABLE, session.state());
        assertTrue(session.detail().contains("No navigation app"));
    }
}
