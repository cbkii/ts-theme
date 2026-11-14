package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;

import com.cbkii.ts18launcher.navigation.n3.N3Feasibility.Observation;
import com.cbkii.ts18launcher.navigation.n3.N3Feasibility.Result;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Feasibility is classified from observations only, and never claims device success. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class N3FeasibilityTest {
    @Test public void classificationCoversEveryObservedShape() {
        assertEquals(Result.STATIC_POC_READY, N3Feasibility.classify(Observation.notProbed()));
        assertEquals(Result.INFEASIBLE_ON_THIS_ROM,
                N3Feasibility.classify(new Observation(true, false, false, false, null, "")));
        assertEquals(Result.NO_TARGET, N3Feasibility.classify(new Observation(true, true, false, false, null, "")));
        assertEquals(Result.BLOCKED_NO_AUTHORITY,
                N3Feasibility.classify(new Observation(true, true, true, true, false, "")));
        assertEquals(Result.LAUNCH_ADMITTED_UNVERIFIED,
                N3Feasibility.classify(new Observation(true, true, true, true, true, "")));
        assertEquals(Result.PROBE_FAILED,
                N3Feasibility.classify(new Observation(true, true, true, true, null, "SecurityException")));
        assertEquals("a display that was never asked about is not a verdict", Result.PROBE_FAILED,
                N3Feasibility.classify(new Observation(true, true, true, false, null, "")));
    }

    @Test public void noResultEverClaimsDeviceFeasibility() {
        for (Result result : Result.values()) {
            assertEquals(false, result.name().contains("PASS"));
        }
    }

    @Test public void probeWithoutATargetLaunchesNothingAndReportsNoTarget() {
        Observation observation = N3DisplayProbe.run(RuntimeEnvironment.getApplication(), "");
        Result result = N3Feasibility.classify(observation);
        // The host may or may not advertise the feature; either way no target is chosen for it.
        assertEquals(true, result == Result.NO_TARGET || result == Result.INFEASIBLE_ON_THIS_ROM);
    }
}
