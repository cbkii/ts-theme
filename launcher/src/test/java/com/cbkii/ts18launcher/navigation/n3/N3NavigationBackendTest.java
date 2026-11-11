package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Surface;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class N3NavigationBackendTest {
    static final String MAPS = "app.organicmaps.incar/app.organicmaps.SplashActivity";

    static final class FakePath implements N3DisplayPath {
        final boolean host;
        final List<String> log = new ArrayList<>();
        Callback callback;
        long createdGen = -1, launchedGen = -1;
        Surface surface;
        final List<MotionEvent> motions = new ArrayList<>();
        int backs;
        String inputReason = "";
        FakePath(boolean host) { this.host = host; }
        @Override public Authority authority() { return host ? Authority.OPTIONAL_HOST : Authority.LAUNCHER_PRIVATE_DISPLAY; }
        @Override public void create(long gen, Surface s, int w, int h, int dpi, Callback cb) {
            log.add("create " + w + "x" + h + "@" + dpi); callback = cb; createdGen = gen; surface = s;
        }
        @Override public void launch(long gen, String component, Callback cb) { log.add("launch " + component); launchedGen = gen; }
        @Override public void setSurface(Surface s) { log.add(s == null ? "detach" : "attach"); surface = s; }
        @Override public N3InputTransport input() {
            return new N3InputTransport() {
                @Override public String unavailableReason() { return inputReason; }
                @Override public boolean motion(MotionEvent e) { motions.add(MotionEvent.obtain(e)); return inputReason.isEmpty(); }
                @Override public boolean back() { backs++; return inputReason.isEmpty(); }
                @Override public void close() { }
            };
        }
        @Override public void release() { log.add("release"); }
    }

    private final List<FakePath> paths = new ArrayList<>();
    private N3NavigationBackend backend;
    private final List<N3Snapshot> snapshots = new ArrayList<>();
    private final Surface surface = new Surface(new android.graphics.SurfaceTexture(0));

    @Before public void setUp() {
        backend = new N3NavigationBackend(RuntimeEnvironment.getApplication(),
                host -> { FakePath p = new FakePath(host); paths.add(p); return p; });
        backend.addListener(snapshots::add);
        backend.setGeometry(1024, 600, 160);
    }

    private FakePath path() { return paths.get(paths.size() - 1); }
    private N3State state() { return backend.snapshot().state; }

    private void interactive() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        path().callback.created(path().createdGen, 7);
        path().callback.launched(path().launchedGen);
        path().callback.firstFrame(path().launchedGen);
    }

    @Test public void startWithoutPresenterFailsHonestlyAndCreatesNothing() {
        backend.start(MAPS);
        ShadowLooper.idleMainLooper();
        assertEquals(N3State.FAILED, state());
        assertFalse(path().log.contains("create 1024x600@160"));
    }

    @Test public void fullPathLaunchesOnceAndReportsPixelsOnlyAfterFirstFrame() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        assertEquals(N3State.DISPLAY_CREATING, state());
        path().callback.created(path().createdGen, 7);
        assertEquals(N3State.APP_LAUNCHING, state());
        assertEquals(1, path().log.stream().filter(l -> l.startsWith("launch ")).count());
        path().callback.launched(path().launchedGen);
        assertEquals(N3State.RENDER_AWAITING, state());
        assertFalse("launch acceptance is not pixels", backend.snapshot().pixelsObserved);
        path().callback.firstFrame(path().launchedGen);
        assertEquals(N3State.INTERACTIVE, state());
        assertTrue(backend.snapshot().pixelsObserved);
        assertEquals("RENDER_AND_INPUT_PATH_OBSERVED", backend.snapshot().classification());
        backend.start(MAPS);
        assertEquals("an active session is never doubled", 1, path().log.stream().filter(l -> l.startsWith("launch ")).count());
    }

    @Test public void blockedLaunchIsClassifiedAndNeverFallsBackToLegacy() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        path().callback.created(path().createdGen, 7);
        path().callback.failed(path().launchedGen, PublicDisplayPath.BLOCKED + ": no embedding");
        assertEquals(N3State.FAILED, state());
        assertEquals("BLOCKED_NO_AUTHORITY", backend.snapshot().classification());
        assertTrue(path().log.contains("release"));
    }

    @Test public void staleCallbacksAndDeadlinesFromAFailedAttemptAreIgnored() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        long first = path().createdGen;
        ShadowLooper.idleMainLooper(N3Session.DISPLAY_DEADLINE_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(N3State.FAILED, state());
        path().callback.created(first, 7);
        assertEquals("late creation of a failed attempt changes nothing", N3State.FAILED, state());
        backend.retry();
        assertEquals(N3State.DISPLAY_CREATING, state());
        assertTrue(path().createdGen != first);
    }

    @Test public void eachPhaseGetsItsOwnDeadlineRatherThanTheDisplayDeadline() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        path().callback.created(path().createdGen, 7);
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("display timer must not expire the launch phase", N3State.APP_LAUNCHING, state());
        path().callback.launched(path().launchedGen);
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("launch timer must not expire the render phase", N3State.RENDER_AWAITING, state());
        ShadowLooper.idleMainLooper(4, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(N3State.FAILED, state());
        assertTrue(backend.snapshot().detail.contains("No map appeared"));
    }

    @Test public void detachedSurfaceCannotBeFailedByAnOldRenderTimer() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        path().callback.created(path().createdGen, 7);
        path().callback.launched(path().launchedGen);
        backend.surfaceGone();
        ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(N3State.SURFACE_DETACHED, state());
        assertFalse(path().log.contains("release"));
    }

    @Test public void diagnosticSnapshotsKeepTheExactTargetThroughRetry() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        assertEquals(MAPS, backend.snapshot().component);
        path().callback.failed(path().createdGen, "refused");
        backend.retry();
        assertEquals(MAPS, backend.snapshot().component);
    }

    @Test public void surfaceLossKeepsTheDisplayAndReattachesWithoutRelaunch() {
        interactive();
        backend.surfaceGone();
        assertEquals(N3State.SURFACE_DETACHED, state());
        assertTrue(path().log.contains("detach"));
        assertFalse(path().log.contains("release"));
        backend.surfaceAvailable(surface, 800, 469);
        assertEquals(N3State.INTERACTIVE, state());
        assertEquals(1, path().log.stream().filter(l -> l.startsWith("launch ")).count());
    }

    @Test public void contentLossFailsAndOnlyExplicitRetryStartsAgain() {
        interactive();
        path().callback.lost(path().launchedGen, "N3 display was removed");
        assertEquals(N3State.FAILED, state());
        ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(N3State.FAILED, state());
        backend.retry();
        assertEquals(N3State.DISPLAY_CREATING, state());
    }

    @Test public void touchAndBackReachTheDisplayOnlyWhenInteractive() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 512f, 300f, 0);
        assertFalse(backend.touch(down));
        assertFalse(backend.back());
        interactive();
        assertTrue(backend.touch(down));
        assertEquals(1, path().motions.size());
        assertEquals(512f, path().motions.get(0).getX(), 0.5f);
        assertTrue(backend.back());
        assertEquals(1, path().backs);
    }

    @Test public void viewOnlyWhenNoInputPathIsReportedTruthfully() {
        backend.surfaceAvailable(surface, 1024, 600);
        backend.start(MAPS);
        path().inputReason = "Input path refused (SecurityException)";
        path().callback.created(path().createdGen, 7);
        path().callback.launched(path().launchedGen);
        path().callback.firstFrame(path().launchedGen);
        assertEquals("RENDER_OBSERVED_VIEW_ONLY", backend.snapshot().classification());
    }

    @Test public void retiredHostSelectionIsIgnoredAndShutdownReleases() {
        interactive();
        backend.selectHost(true);
        assertFalse(path().host);
        backend.shutdown();
        assertEquals(N3State.SHUTDOWN, state());
        assertTrue(path().log.contains("release"));
        backend.selectHost(true);
        backend.start(MAPS);
        assertFalse(path().host);
    }
}
