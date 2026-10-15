package com.cbkii.ts18launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Signals reconciliation only. It never launches an Activity: foreground staging happens later,
 * in the application process, once HOME is actually resumed. Screen-on or boot is never ACC proof.
 */
public final class StartupBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        MediaEventTrace.record("startup", "boot-completed");
        android.app.Application app = (android.app.Application) context.getApplicationContext();
        if (app instanceof Ts18LauncherApplication) {
            StartupReadinessCoordinator coordinator = ((Ts18LauncherApplication) app).startup();
            if (coordinator != null) coordinator.onTrigger(StartupReadinessPolicy.Trigger.BOOT_COMPLETED);
        }
    }
}
