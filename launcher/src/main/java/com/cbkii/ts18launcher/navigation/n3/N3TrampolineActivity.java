package com.cbkii.ts18launcher.navigation.n3;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Not exported; started only by PublicDisplayPath, onto the launcher's own private display. On
 * Android 10 another app's Activity may be started on an app-owned private display only by a
 * caller already present on that display, and only if the target allows embedding. This
 * launcher-owned Activity provides that presence, starts the exact target once, and finishes.
 * It cannot be used to reach any other display or any target the path did not register.
 */
public final class N3TrampolineActivity extends Activity {
    static final String EXTRA_TARGET = "n3.target";
    static final String EXTRA_TOKEN = "n3.token";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        long token = getIntent().getLongExtra(EXTRA_TOKEN, 0L);
        N3LaunchRelay.Result result = N3LaunchRelay.take(token);
        ComponentName target = ComponentName.unflattenFromString(
                String.valueOf(getIntent().getStringExtra(EXTRA_TARGET)));
        int display = getWindowManager().getDefaultDisplay().getDisplayId();
        if (result == null || target == null || display == 0) { finish(); return; }
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        try {
            if (am == null || !am.isActivityStartAllowedOnDisplay(this, display, intent)) {
                result.refused(PublicDisplayPath.BLOCKED + ": " + target.getPackageName()
                        + " does not allow embedding on a private display");
            } else {
                startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
                result.launched();
            }
        } catch (SecurityException refused) {
            result.refused(PublicDisplayPath.BLOCKED + ": start refused on the N3 display");
        } catch (RuntimeException failed) {
            result.refused("Start failed: " + failed.getClass().getSimpleName());
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
