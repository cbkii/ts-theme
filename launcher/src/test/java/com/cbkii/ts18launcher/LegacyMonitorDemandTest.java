package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** With no source on the legacy engine, the legacy session monitor holds nothing and stays idle. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class LegacyMonitorDemandTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @After public void restore() { ProcessMediaSessionMonitor.setDemand(context, true); }

    @Test public void noLegacySourceMeansNoMonitorAndNoReactivationFromCallers() {
        ProcessMediaSessionMonitor.setDemand(context, true);
        ProcessMediaSessionMonitor.refresh(context);
        assertTrue(ProcessMediaSessionMonitor.active());

        ProcessMediaSessionMonitor.setDemand(context, false);
        assertFalse(ProcessMediaSessionMonitor.active());

        // HOME resume, visibility, listener and settings refreshes must not recreate it.
        ProcessMediaSessionMonitor.refresh(context);
        ProcessMediaSessionMonitor.setVisible(context, true);
        ProcessMediaSessionMonitor.start(context);
        assertFalse(ProcessMediaSessionMonitor.active());

        ProcessMediaSessionMonitor.setDemand(context, true);
        assertTrue("switching a source back to legacy restores observation", ProcessMediaSessionMonitor.active());
    }
}
