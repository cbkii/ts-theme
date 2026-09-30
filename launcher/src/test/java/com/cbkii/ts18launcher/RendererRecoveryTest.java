package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RendererRecoveryTest {
    @Test public void permitsOneAutomaticRecreationThenBlocksLoop() {
        RendererRecovery recovery = new RendererRecovery();
        assertTrue(recovery.failed());
        assertFalse(recovery.failed());
        assertTrue(recovery.blocked());
        recovery.retry();
        assertFalse(recovery.failed());
    }
}
