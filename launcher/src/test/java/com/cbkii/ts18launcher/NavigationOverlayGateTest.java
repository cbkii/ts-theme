package com.cbkii.ts18launcher;

import static org.junit.Assert.*;
import org.junit.Test;

public class NavigationOverlayGateTest {
    @Test public void stoppedHomeCancelsPendingLaunch() {
        NavigationOverlayGate gate = new NavigationOverlayGate();
        int[] shown = {0};
        gate.onVisible();
        gate.request(() -> shown[0]++);
        gate.onStopped();
        gate.onSettled();
        gate.onVisible();
        assertEquals(0, shown[0]);
        assertFalse(gate.isPending());
    }

    @Test public void newestRequestReplacesOlderPendingLaunch() {
        NavigationOverlayGate gate = new NavigationOverlayGate();
        int[] shown = {0};
        gate.onVisible();
        gate.request(() -> shown[0]++);
        gate.request(() -> shown[0] += 100);
        gate.onSettled();
        gate.onVisible();
        gate.onSettled();
        assertEquals(100, shown[0]);
    }

    @Test public void homeBeforeCompletionStillCannotExposeLaunchButtons() {
        NavigationOverlayGate gate = new NavigationOverlayGate();
        int[] shown = {0};
        gate.request(() -> shown[0]++);
        gate.onVisible();
        assertEquals(0, shown[0]);
        gate.onSettled();
        assertEquals(1, shown[0]);
    }

    @Test public void fullscreenOrDestructionCancelsStaleDrawer() {
        NavigationOverlayGate gate = new NavigationOverlayGate();
        gate.request(() -> fail("obsolete drawer delivered"));
        gate.cancel();
        gate.onSettled();
        gate.onVisible();
        assertFalse(gate.isPending());
    }
}
