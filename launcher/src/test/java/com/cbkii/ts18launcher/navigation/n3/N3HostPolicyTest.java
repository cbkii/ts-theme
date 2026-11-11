package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class N3HostPolicyTest {
    private static String rep(String s) { StringBuilder b = new StringBuilder(); for (int i = 0; i < 32; i++) b.append(s); return b.toString(); }
    static final String CERT = rep("ab");
    static final String MAPS = "app.organicmaps.incar/app.organicmaps.SplashActivity";
    private long now = 1000L;

    private final N3HostPolicy.Config config = new N3HostPolicy.Config(true, "com.cbkii.ts18launcher", CERT, 0,
            new HashSet<>(Collections.singletonList(MAPS)));
    private final N3HostPolicy policy = new N3HostPolicy(config, () -> now);
    private final N3HostPolicy.Caller launcher = new N3HostPolicy.Caller(10223, 0, new String[] {"com.cbkii.ts18launcher"}, CERT);

    private N3HostPolicy.Decision open(N3HostPolicy.Caller c, int id) {
        N3HostPolicy.Decision d = policy.createDisplay(c, 1280, 720, 160);
        if (d == N3HostPolicy.Decision.ALLOW) policy.displayCreated(c, id);
        return d;
    }

    @Test public void defaultConfigurationAdmitsNothing() {
        N3HostPolicy off = new N3HostPolicy(N3HostPolicy.Config.DISABLED, () -> now);
        assertEquals(N3HostPolicy.Decision.DENY_HOST_DISABLED, off.createDisplay(launcher, 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_HOST_DISABLED, off.launch(launcher, 5, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_HOST_DISABLED, new N3HostPolicy(null, () -> now).release(launcher, 5));
    }

    @Test public void callerMustMatchPackageCertificateUserAndBeUnambiguous() {
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_AMBIGUOUS, policy.createDisplay(
                new N3HostPolicy.Caller(1000, 0, new String[] {"com.cbkii.ts18launcher", "android"}, CERT), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_PACKAGE, policy.createDisplay(
                new N3HostPolicy.Caller(10300, 0, new String[] {"com.evil"}, CERT), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_CERTIFICATE, policy.createDisplay(
                new N3HostPolicy.Caller(10223, 0, new String[] {"com.cbkii.ts18launcher"}, rep("cd")), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_CALLER_USER, policy.createDisplay(
                new N3HostPolicy.Caller(1010223, 10, new String[] {"com.cbkii.ts18launcher"}, CERT), 1280, 720, 160));
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.createDisplay(launcher, 1280, 720, 160));
    }

    @Test public void geometryAndDisplayCountAreBounded() {
        assertEquals(N3HostPolicy.Decision.DENY_GEOMETRY, policy.createDisplay(launcher, 10, 720, 160));
        assertEquals(N3HostPolicy.Decision.DENY_GEOMETRY, policy.createDisplay(launcher, 1280, 9000, 160));
        assertEquals(N3HostPolicy.Decision.DENY_GEOMETRY, policy.createDisplay(launcher, 1280, 720, 9));
        assertEquals(N3HostPolicy.Decision.ALLOW, open(launcher, 7));
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_LIMIT, policy.createDisplay(launcher, 1280, 720, 160));
    }

    @Test public void launchNeedsOwnedDisplayAndExactAllowlistedComponent() {
        open(launcher, 7);
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.launch(launcher, 7, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.launch(launcher, 0, MAPS));
        assertEquals(N3HostPolicy.Decision.DENY_COMPONENT, policy.launch(launcher, 7, "app.organicmaps.incar/.Other"));
        assertEquals(N3HostPolicy.Decision.DENY_COMPONENT, policy.launch(launcher, 7, "app.organicmaps.incar"));
        assertEquals(N3HostPolicy.Decision.DENY_COMPONENT, policy.launch(launcher, 7, null));
    }

    @Test public void injectionIsBoundedOwnedAndRateLimited() {
        open(launcher, 7);
        float[] in = {10f}, out = {1280f};
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.inject(launcher, 0, 1280, 720, in, in));
        assertEquals(N3HostPolicy.Decision.DENY_COORDINATES, policy.inject(launcher, 7, 1280, 720, out, in));
        assertEquals(N3HostPolicy.Decision.DENY_COORDINATES, policy.inject(launcher, 7, 1280, 720, new float[] {Float.NaN}, in));
        assertEquals(N3HostPolicy.Decision.DENY_POINTERS, policy.inject(launcher, 7, 1280, 720, new float[11], new float[11]));
        for (int i = 0; i < N3HostPolicy.MAX_EVENTS_PER_SECOND; i++) {
            assertEquals(N3HostPolicy.Decision.ALLOW, policy.inject(launcher, 7, 1280, 720, in, in));
        }
        assertEquals(N3HostPolicy.Decision.DENY_RATE, policy.inject(launcher, 7, 1280, 720, in, in));
        now += 1000L;
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.inject(launcher, 7, 1280, 720, in, in));
    }

    @Test public void anotherCallerCannotUseOrReleaseTheDisplay() {
        open(launcher, 7);
        N3HostPolicy.Config other = new N3HostPolicy.Config(true, "com.cbkii.ts18launcher", CERT, 0, null);
        N3HostPolicy.Caller twin = new N3HostPolicy.Caller(10999, 0, new String[] {"com.cbkii.ts18launcher"}, CERT);
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.release(twin, 7));
        assertEquals(N3HostPolicy.Decision.ALLOW, policy.release(launcher, 7));
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.release(launcher, 7));
        assertTrue(other.components.isEmpty());
    }

    @Test public void callerDeathReleasesOnlyItsDisplays() {
        open(launcher, 7);
        assertEquals(Arrays.asList(7), policy.callerDied(launcher.uid));
        assertTrue(policy.callerDied(launcher.uid).isEmpty());
        assertEquals(N3HostPolicy.Decision.DENY_DISPLAY_NOT_OWNED, policy.launch(launcher, 7, MAPS));
        assertEquals(N3HostPolicy.Decision.ALLOW, open(launcher, 8));
    }
}
