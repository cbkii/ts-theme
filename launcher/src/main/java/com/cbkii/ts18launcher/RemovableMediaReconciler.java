package com.cbkii.ts18launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Visible-HOME removable-media signal bridge.
 *
 * This observes Android mount lifecycle only. It never scans storage, decides Auxio queue
 * validity, requests playback, or treats system/root mount visibility as proof of app access.
 */
final class RemovableMediaReconciler {
    interface AvailabilityCallback { void onMediaAvailable(); }

    private final Context context;
    private final AvailabilityCallback callback;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            String action = intent == null ? "" : intent.getAction();
            if (action == null) action = "";
            MediaDiagnostics.record("storage", action);
            MediaListenerService.refreshActiveSessions();
            if (Intent.ACTION_MEDIA_MOUNTED.equals(action) && callback != null) {
                callback.onMediaAvailable();
            }
        }
    };

    RemovableMediaReconciler(Context context, AvailabilityCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    void start() {
        if (registered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addDataScheme("file");
        try {
            context.registerReceiver(receiver, filter);
            registered = true;
            MediaDiagnostics.record("storage", "receiver_registered");
        } catch (RuntimeException error) {
            MediaDiagnostics.record("storage", "receiver_register_failed="
                    + error.getClass().getSimpleName());
        }
    }

    void stop() {
        if (!registered) return;
        registered = false;
        try {
            context.unregisterReceiver(receiver);
        } catch (RuntimeException ignored) {
            // Framework may already have detached it during Activity teardown.
        }
        MediaDiagnostics.record("storage", "receiver_unregistered");
    }
}
