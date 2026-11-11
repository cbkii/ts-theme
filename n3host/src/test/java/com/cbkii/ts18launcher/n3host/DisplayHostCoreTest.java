package com.cbkii.ts18launcher.n3host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cbkii.ts18launcher.navigation.n3.N3HostPolicy;
import com.cbkii.ts18launcher.navigation.n3.N3HostPolicy.Decision;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class DisplayHostCoreTest {
    private static String rep(String s) { StringBuilder b = new StringBuilder(); for (int i = 0; i < 32; i++) b.append(s); return b.toString(); }
    static final String CERT = rep("ab");
    static final String LAUNCHER = "com.cbkii.ts18launcher";
    static final String MAPS = "app.organicmaps.incar/app.organicmaps.SplashActivity";
    static final int UID = 10223;

    static final class FakePlatform implements DisplayHostCore.Platform {
        final Map<Integer, N3HostPolicy.Caller> callers = new HashMap<>();
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        final Map<Object, Runnable> deaths = new HashMap<>();
        int nextId = 7;
        boolean launchOk = true;
        @Override public N3HostPolicy.Caller resolve(int uid) { return callers.get(uid); }
        @Override public synchronized int createDisplay(int w, int h, int dpi, Object s, Object c) { log.add("create"); return nextId++; }
        @Override public boolean setSurface(int id, Object s) { log.add("surface " + id); return true; }
        @Override public boolean launch(int id, String component) { log.add("launch " + id + " " + component); return launchOk; }
        @Override public boolean inject(int id, Object e) { log.add("inject " + id); return true; }
        @Override public boolean back(int id) { log.add("back " + id); return true; }
        @Override public void releaseDisplay(int id) { log.add("release " + id); }
        @Override public boolean linkToDeath(Object client, Runnable onDeath) { deaths.put(client, onDeath); return true; }
    }

    private final FakePlatform platform = new FakePlatform();
    private final Object surface = new Object(), client = new Object();

    private DisplayHostCore core(boolean enabled) {
        platform.callers.put(UID, new N3HostPolicy.Caller(UID, 0, new String[] {LAUNCHER}, CERT));
        return new DisplayHostCore(new N3HostPolicy(new N3HostPolicy.Config(enabled, LAUNCHER, CERT, 0,
                new HashSet<>(Collections.singletonList(MAPS))), () -> 0L), platform);
    }

    @Test public void disabledHostCreatesNothing() {
        DisplayHostCore core = core(false);
        assertEquals(Decision.DENY_HOST_DISABLED, core.open(UID, surface, 1024, 600, 160, client).decision);
        assertTrue(platform.log.isEmpty());
    }

    @Test public void forgedSharedOrUnknownCallersAreRefusedBeforeAnyDisplay() {
        DisplayHostCore core = core(true);
        platform.callers.put(10300, new N3HostPolicy.Caller(10300, 0, new String[] {LAUNCHER}, rep("cd")));
        platform.callers.put(1000, new N3HostPolicy.Caller(1000, 0, new String[] {LAUNCHER, "android"}, CERT));
        platform.callers.put(1010223, new N3HostPolicy.Caller(1010223, 10, new String[] {LAUNCHER}, CERT));
        assertEquals(Decision.DENY_CALLER_CERTIFICATE, core.open(10300, surface, 1024, 600, 160, client).decision);
        assertEquals(Decision.DENY_CALLER_AMBIGUOUS, core.open(1000, surface, 1024, 600, 160, client).decision);
        assertEquals(Decision.DENY_CALLER_USER, core.open(1010223, surface, 1024, 600, 160, client).decision);
        assertEquals(Decision.DENY_CALLER_AMBIGUOUS, core.open(4242, surface, 1024, 600, 160, client).decision);
        assertTrue(platform.log.isEmpty());
    }

    @Test public void ownerCanLaunchInjectAndCloseOnlyItsOwnDisplay() {
        DisplayHostCore core = core(true);
        DisplayHostCore.Opened opened = core.open(UID, surface, 1024, 600, 160, client);
        assertEquals(Decision.ALLOW, opened.decision);
        assertEquals(Decision.ALLOW, core.launch(UID, opened.displayId, opened.lease, MAPS));
        assertEquals(Decision.DENY_COMPONENT, core.launch(UID, opened.displayId, opened.lease, "com.android.settings/.Settings"));
        assertEquals(Decision.DENY_DISPLAY_NOT_OWNED, core.launch(UID, 0, opened.lease, MAPS));
        assertEquals(Decision.ALLOW, core.motion(UID, opened.displayId, opened.lease, 0, new float[] {5f}, new float[] {5f}, new Object()));
        assertEquals(Decision.DENY_COORDINATES, core.motion(UID, opened.displayId, opened.lease, 2, new float[] {2000f}, new float[] {5f}, new Object()));
        assertEquals(Decision.DENY_ACTION, core.motion(UID, opened.displayId, opened.lease, 9, new float[] {5f}, new float[] {5f}, new Object()));
        assertEquals(Decision.ALLOW, core.back(UID, opened.displayId, opened.lease));

        platform.callers.put(10999, new N3HostPolicy.Caller(10999, 0, new String[] {LAUNCHER}, CERT));
        assertEquals(Decision.DENY_DISPLAY_NOT_OWNED, core.close(10999, opened.displayId, opened.lease));
        assertEquals(Decision.DENY_DISPLAY_NOT_OWNED, core.setSurface(10999, opened.displayId, opened.lease, surface));
        assertEquals(Decision.DENY_LEASE, core.close(UID, opened.displayId, opened.lease + 1));
        assertEquals(Decision.ALLOW, core.close(UID, opened.displayId, opened.lease));
        assertTrue(platform.log.contains("release " + opened.displayId));
        assertEquals(Decision.DENY_DISPLAY_NOT_OWNED, core.launch(UID, opened.displayId, opened.lease, MAPS));
    }

    @Test public void concurrentOpensCreateAtMostOneDisplay() throws Exception {
        DisplayHostCore core = core(true);
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Thread t = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                if (core.open(UID, surface, 1024, 600, 160, client).decision == Decision.ALLOW) allowed.incrementAndGet();
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        assertEquals(1, allowed.get());
        assertEquals(1, platform.log.stream().filter("create"::equals).count());
    }

    @Test public void clientDeathReleasesItsDisplayAndAllowsAFreshOpen() {
        DisplayHostCore core = core(true);
        DisplayHostCore.Opened opened = core.open(UID, surface, 1024, 600, 160, client);
        platform.deaths.get(client).run();
        assertTrue(platform.log.contains("release " + opened.displayId));
        assertEquals(Decision.DENY_DISPLAY_NOT_OWNED, core.launch(UID, opened.displayId, opened.lease, MAPS));
        DisplayHostCore.Opened again = core.open(UID, surface, 1024, 600, 160, new Object());
        assertEquals(Decision.ALLOW, again.decision);
        assertTrue(again.lease != opened.lease);
    }

    @Test public void missingSurfaceOrClientCancelsTheReservation() {
        DisplayHostCore core = core(true);
        assertEquals(Decision.DENY_GEOMETRY, core.open(UID, null, 1024, 600, 160, client).decision);
        assertEquals(Decision.ALLOW, core.open(UID, surface, 1024, 600, 160, client).decision);
    }

    @Test public void refusedPlatformLaunchIsNotReportedAsAllowed() {
        DisplayHostCore core = core(true);
        DisplayHostCore.Opened opened = core.open(UID, surface, 1024, 600, 160, client);
        platform.launchOk = false;
        assertEquals(Decision.DENY_COMPONENT, core.launch(UID, opened.displayId, opened.lease, MAPS));
    }
}
