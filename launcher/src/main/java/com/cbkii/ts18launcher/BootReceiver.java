package com.cbkii.ts18launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Signals startup reconciliation after boot. Never starts an Activity or service. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        MediaEventTrace.record("startup", "boot-completed");
        StartupReadinessCoordinator.get(context)
                .onTrigger(StartupReadinessPolicy.Trigger.BOOT, "boot-completed");
    }
}
