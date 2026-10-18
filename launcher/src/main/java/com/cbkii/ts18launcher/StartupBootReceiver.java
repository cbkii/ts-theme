package com.cbkii.ts18launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Signals startup reconciliation after Android boot. It never launches an Activity: foreground
 * bootstrap waits until the HOME Activity is actually resumed.
 */
public final class StartupBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        MediaEventTrace.record("startup", "boot-completed-signal");
        StartupReadinessCoordinator.get(context).onTrigger(StartupReadinessPolicy.Trigger.BOOT);
    }
}
