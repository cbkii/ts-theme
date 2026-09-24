package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.ViewTreeObserver;

import java.util.IdentityHashMap;
import java.util.Map;

/** Process-level lifecycle diagnostics and visible-HOME removable-media receiver ownership. */
public final class Ts18LauncherApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private final Map<Activity, ViewTreeObserver.OnWindowFocusChangeListener> focusListeners =
            new IdentityHashMap<>();
    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            MediaStorageReconciler.handle(context, intent.getAction(), intent.getData());
        }
    };
    private int startedHomeActivities;
    private boolean storageRegistered;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        MediaEventTrace.record("process.create", "launcher process created");
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (!(activity instanceof LauncherActivity)) return;
        MediaEventTrace.record("home.create", state == null ? "fresh" : "recreated");
        ViewTreeObserver.OnWindowFocusChangeListener listener = hasFocus ->
                MediaEventTrace.record("home.focus", hasFocus ? "gained" : "lost");
        focusListeners.put(activity, listener);
        activity.getWindow().getDecorView().getViewTreeObserver()
                .addOnWindowFocusChangeListener(listener);
    }

    @Override public void onActivityStarted(Activity activity) {
        if (!(activity instanceof LauncherActivity)) return;
        startedHomeActivities++;
        MediaEventTrace.record("home.start", "started=" + startedHomeActivities);
        if (startedHomeActivities == 1) registerStorageReceiver();
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof LauncherActivity) MediaEventTrace.record("home.resume", "visible");
    }

    @Override public void onActivityPaused(Activity activity) {
        if (activity instanceof LauncherActivity) MediaEventTrace.record("home.pause", "losing foreground");
    }

    @Override public void onActivityStopped(Activity activity) {
        if (!(activity instanceof LauncherActivity)) return;
        startedHomeActivities = Math.max(0, startedHomeActivities - 1);
        MediaEventTrace.record("home.stop", "started=" + startedHomeActivities);
        if (startedHomeActivities == 0) {
            unregisterStorageReceiver();
            MediaStorageReconciler.cancel("HOME stopped");
        }
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
        // No diagnostic state is persisted; the timeline is process-local by design.
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (!(activity instanceof LauncherActivity)) return;
        ViewTreeObserver.OnWindowFocusChangeListener listener = focusListeners.remove(activity);
        if (listener != null) {
            ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(listener);
        }
        MediaEventTrace.record("home.destroy", "activity destroyed");
    }

    private void registerStorageReceiver() {
        if (storageRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addDataScheme("file");
        try {
            registerReceiver(storageReceiver, filter);
            storageRegistered = true;
            MediaEventTrace.record("storage.receiver", "registered while HOME started");
        } catch (RuntimeException error) {
            MediaEventTrace.record("storage.receiver", "registration failed");
        }
    }

    private void unregisterStorageReceiver() {
        if (!storageRegistered) return;
        storageRegistered = false;
        try {
            unregisterReceiver(storageReceiver);
        } catch (RuntimeException ignored) {
            // Receiver may already have been detached during process teardown.
        }
        MediaEventTrace.record("storage.receiver", "unregistered");
    }
}
