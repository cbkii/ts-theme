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
        // Commit inside goAsync: no receiver-created singleton/timer must outlive onReceive.
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                int boot = android.provider.Settings.Global.getInt(context.getContentResolver(),
                        android.provider.Settings.Global.BOOT_COUNT, -1);
                context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE).edit()
                        .putInt("pending_boot_v1", boot).commit();
            } finally { pending.finish(); }
        }, "startup-boot-signal").start();
    }
}
