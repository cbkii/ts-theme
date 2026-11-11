package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class N3HostPolicyTest {
    private static String rep(String s) { StringBuilder b = new StringBuilder(); for (int i = 0; i < 32; i++) b.append(s); return b.toString(); }
    static final String CERT = rep("ab");
    static final String MAPS = "app.organicmaps.incar/app.organicmaps.SplashActivity";
    private long now = 1000L;

    private final Set<String> allow = new HashSet<>(Collections.singletonList(MAPS));
    private final N3HostPolicy.Config config = new N3HostPolicy.Config(true, "com.cbkii.ts18launcher", CERT, 0, allow);
    private final N3HostPolicy policy = new N3HostPolicy(config, () -> now);
    private final N3HostPolicy.Caller launcher = new N3HostPolicy.Caller(10223, 0, new String[] {"com.cbkii.ts18launcher"}, CERT);

    private N3HostPolicy.Lease open(N3HostPolicy.Caller c, int id) {
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.reserve(c, 1280, 720, 160));
        return policy.displayCreated(c, id, 1280, 720);
    }

    @Test public void defaultConfigurationAdmitsNothing() {
        N3HostPolicy off = new N3HostPolicy(N3HostPolicy.Config.DISABLED, () -> now);
        assertEquals(N3HostPolicy.Decision.DENY_HOST_DISABLED, off.reserve(launcher, 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_HOST_DISABLED, off.launch(launcher, 5, 1L, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_HOST_DISABLED, new N3HostPolicy(null, () -> now).release(launcher, 5, 1L));
    }

    @Test public void callerMustMatchPackageCertificateUserAndBeUnambiguous() {
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_AMBIGUOUS, policy.reserve(
                new N3HostPolicy.Caller(1000, 0, new String[] {"com.cbkii.ts18launcher", "android"}, CERT), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_PACKAGE, policy.reserve(
                new N3HostPolicy.Caller(10300, 0, new String[] {"com.evil"}, CERT), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_CERTIFICATE, policy.reserve(
                new N3HostPolicy.Caller(10223, 0, new String[] {"com.cbkii.ts18launcher"}, rep("cd")), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_USER, policy.reserve(
                new N3HostPolicy.Caller(1010223, 10, new String[] {"com.cbkii.ts18launcher"}, CERT), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.reserve(launcher, 1280, 720, 160));
    }

    @Test public void configIsImmutableAndRejectsWildcards() {
        allow.add("app.evil/.Main");
        assertEquals(N3HostPolicy.Decision.DENY_COMPONENT, policy.launch(launcher, open(launcher, 7).displayId,
                policy.lease(7).token, "app.evil/.Main"));
        N3HostPolicy.Config wild = new N3HostPolicy.Config(true, "p", CERT, 0,
                new HashSet<>(Arrays.asList("app.organicmaps.incar/*", "app.organicmaps.incar", "/x", "a/b/c")));
        assertTrue(wild.components.isEmpty());
    }

    @Test public void geometryAndDisplayCountAreBoundedAtomically() throws Exception {
        assertEquals(N3HostPolicy.Decision.DENY_GEOMETRY, policy.reserve(launcher, 10, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_GEOMETRY, policy.reserve(launcher, 1280, 9000, 160));
        assertEquals(N3HostPolicy.Decision.DENY_GEOMETRY, policy.reserve(launcher, 1280, 720, 9));
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                if (policy.reserve(launcher, 1280, 720, 160) == N3HostPolicy.Decision.ALLOW) allowed.incrementAndGet();
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        assertEquals("concurrent reservations never over-allocate", 1, allowed.get());
        assertNotNull(policy.displayCreated(launcher, 7, 1280, 720));
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_LIMIT, policy.reserve(launcher, 1280, 720, 160));
    }

    @Test public void unreservedDefaultOrDuplicateDisplaysAreNeverRecorded() {
        assertNull(policy.displayCreated(launcher, 7, 1280, 720));
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.reserve(launcher, 1280, 720, 160));
        assertNull("display 0 is the default display", policy.displayCreated(launcher, 0, 1280, 720));
        assertNotNull(open(launcher, 7));
    }

    @Test public void launchNeedsOwnedDisplayCurrentLeaseAndExactComponent() {
        N3HostPolicy.Lease lease = open(launcher, 7);
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.launch(launcher, 7, lease.token, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_LEASE, policy.launch(launcher, 7, lease.token + 1, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.launch(launcher, 0, lease.token, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_COMPONENT, policy.launch(launcher, 7, lease.token, "app.organicmaps.incar/.Other"));
        assertEquals(N3HostPolicy.Decision.DENY_COMPONENT, policy.launch(launcher, 7, lease.token, null));
    }

    @Test public void injectionUsesHostGeometryValidatesActionsAndRateLimits() {
        N3HostPolicy.Lease lease = open(launcher, 7);
        float[] in = {10f}, out = {1280f};
        assertEquals(N3HostPolicy.Decision.DENY_COORDINATES, policy.inject(launcher, 7, lease.token, 0, out, in));
        assertEquals(N3HostPolicy.Decision.DENY_COORDINATES, policy.inject(launcher, 7, lease.token, 0, new float[] {Float.NaN}, in));
        assertEquals(N3HostPolicy.Decision.DENY_POINTERS, policy.inject(launcher, 7, lease.token, 2, new float[11], new float[11]));
        assertEquals(N3HostPolicy.Decision.DENY_ACTION, policy.inject(launcher, 7, lease.token, 7 /* HOVER_MOVE */, in, in));
        assertEquals(N3HostPolicy.Decision.DENY_ACTION, policy.inject(launcher, 7, lease.token, 8 /* SCROLL */, in, in));
        assertEquals(N3HostPolicy.Decision.DENY_LEASE, policy.inject(launcher, 7, 999L, 0, in, in));
        for (int i = 0; i < N3HostPolicy.MAX_EVENTS_PER_SECOND; i++) {
            assertEquals(N3HostPolicy.Decision.ALLOW, policy.inject(launcher, 7, lease.token, 2, in, in));
        }
        assertEquals(N3HostPolicy.Decision.DENY_RATE, policy.inject(launcher, 7, lease.token, 2, in, in));
        assertEquals(N3HostPolicy.Decision.DENY_RATE, policy.back(launcher, 7, lease.token));
        now += 1000L;
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.back(launcher, 7, lease.token));
    }

    @Test public void anotherCallerOrStaleLeaseCannotUseOrReleaseTheDisplay() {
        N3HostPolicy.Lease lease = open(launcher, 7);
        N3HostPolicy.Caller twin = new N3HostPolicy.Caller(10999, 0, new String[] {"com.cbkii.ts18launcher"}, CERT);
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.release(twin, 7, lease.token));
        assertEquals(N3HostPolicy.Decision.DENY_LEASE, policy.release(launcher, 7, lease.token - 1));
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.release(launcher, 7, lease.token));
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.release(launcher, 7, lease.token));
        N3HostPolicy.Lease next = open(launcher, 7);
        assertTrue("a recreated display gets a new lease; the old one replays nothing", next.token != lease.token);
        assertEquals(N3HostPolicy.Decision.DENY_LEASE, policy.launch(launcher, 7, lease.token, MAPS));
    }

    @Test public void callerDeathReleasesOnlyItsDisplaysAndReservation() {
        open(launcher, 7);
        assertEquals(Arrays.asList(7), policy.callerDied(launcher.uid));
        assertTrue(policy.callerDied(launcher.uid).isEmpty());
        assertNull(policy.lease(7));
        assertNotNull(open(launcher, 8));
    }
}
