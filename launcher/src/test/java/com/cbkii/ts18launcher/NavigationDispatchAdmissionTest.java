package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Automatic cold-start admission is re-checked when the helper is spawned, not only when set. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class NavigationDispatchAdmissionTest {
    @Test public void automaticIsDowngradedWhenHomeAdmissionNoLongerHolds() {
        Context context = RuntimeEnvironment.getApplication();
        NavigationRootHelper helper = new NavigationRootHelper(context);
        boolean[] admitted = {true};
        helper.admission = ignored -> admitted[0];
        helper.setColdLaunchPolicy("automatic");
        assertEquals("automatic", helper.dispatchColdPolicy());
        // A call, lock or HOME change after the policy was set must stop the spawn.
        admitted[0] = false;
        assertEquals("deny", helper.dispatchColdPolicy());
    }

    @Test public void explicitAndDeniedPoliciesAreUnchangedAtDispatch() {
        NavigationRootHelper helper = new NavigationRootHelper(RuntimeEnvironment.getApplication());
        helper.admission = ignored -> false;
        helper.setColdLaunchPolicy("manual");
        assertEquals("manual", helper.dispatchColdPolicy());
        helper.setColdLaunchPolicy("deny");
        assertEquals("deny", helper.dispatchColdPolicy());
        helper.setColdLaunchPolicy("unexpected");
        assertEquals("deny", helper.dispatchColdPolicy());
    }
}
