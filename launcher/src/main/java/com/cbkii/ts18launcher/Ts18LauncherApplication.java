package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Process-level launcher lifecycle, navigation bootstrap and removable-media reconciliation.
 *
 * Storage broadcasts are observations only. A successful mount may trigger one bounded background
 * music warm-up while HOME is resumed; it never scans media, owns the queue, auto-plays or replays
 * an expired command. Source membership and queue restoration remain player-owned.
 */
public final class Ts18LauncherApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launcherResumed;
    private boolean receiverRegistered;

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            RemovableMediaPolicy.Event event = RemovableMediaPolicy.classify(intent.getAction());
            if (event == RemovableMediaPolicy.Event.IGNORE) return;
            String detail = intent.getData() == null ? "" : intent.getData().toString();
            MediaEventTrace.record("storage", event.name().toLowerCase(java.util.Locale.ROOT), detail);
            ProcessMediaSessionMonitor.refresh(Ts18LauncherApplication.this);
            MediaListenerService.refreshActiveSessions();
            if (RemovableMediaPolicy.shouldWarm(event, launcherResumed,
                    UiPersonalizationPrefs.mediaStartupWarmup(Ts18LauncherApplication.this))) {
                StartupReadinessCoordinator.get(Ts18LauncherApplication.this).onStorageMounted();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NavigationPermissionBootstrapper.ensureEarly(this);
        MediaNotificationAccessBootstrapper.ensureEarly(this);
        ProcessMediaSessionMonitor.start(this);
        registerActivityLifecycleCallbacks(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addDataScheme("file");
        registerReceiver(storageReceiver, filter);
        receiverRegistered = true;
        IntentFilter wake = new IntentFilter(Intent.ACTION_SCREEN_ON);
        wake.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(wakeReceiver, wake);
        StartupReadinessCoordinator.get(this).onProcessStart();
        MediaEventTrace.record("process", "created");
    }

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            // Screen-on/user-present is a wake hint only; it is never treated as verified ACC-on.
            StartupReadinessCoordinator.get(Ts18LauncherApplication.this)
                    .onTrigger(StartupReadinessPolicy.Trigger.WAKE, String.valueOf(intent.getAction()));
        }
    };

    private static boolean isLauncher(Activity activity) {
        return activity instanceof LauncherActivity;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (isLauncher(activity)) MediaEventTrace.record("home", "created");
    }

    @Override public void onActivityStarted(Activity activity) {
        if (isLauncher(activity)) MediaEventTrace.record("home", "started");
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!isLauncher(activity)) return;
        launcherResumed = true;
        StartupReadinessCoordinator.get(this).onHomeResumed(true);
        ProcessMediaSessionMonitor.refresh(this);
        MediaEventTrace.record("home", "resumed");
    }

    @Override public void onActivityPaused(Activity activity) {
        if (!isLauncher(activity)) return;
        launcherResumed = false;
        StartupReadinessCoordinator.get(this).onHomeResumed(false);
        MediaEventTrace.record("home", "paused");
    }

    @Override public void onActivityStopped(Activity activity) {
        if (isLauncher(activity)) MediaEventTrace.record("home", "stopped");
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
        // No additional process state is persisted here.
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (isLauncher(activity)) MediaEventTrace.record("home", "destroyed");
    }

    @Override public void onTerminate() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(storageReceiver);
                unregisterReceiver(wakeReceiver);
            } catch (RuntimeException ignored) {
                // The framework may already have torn the process down.
            }
            receiverRegistered = false;
        }
        unregisterActivityLifecycleCallbacks(this);
        handler.removeCallbacksAndMessages(null);
        ProcessMediaSessionMonitor.stop();
        MediaEventTrace.record("process", "terminated");
        super.onTerminate();
    }
}
