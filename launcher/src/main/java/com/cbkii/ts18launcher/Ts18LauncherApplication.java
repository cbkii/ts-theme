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
import android.os.SystemClock;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

/**
 * Process-level launcher lifecycle, navigation bootstrap and removable-media reconciliation.
 *
 * Storage broadcasts are observations only. A successful mount may trigger one bounded background
 * music warm-up while HOME is resumed; it never scans media, owns the queue, auto-plays or replays
 * an expired command. Source membership and queue restoration remain player-owned.
 */
public final class Ts18LauncherApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private static final long STORAGE_WARM_DEBOUNCE_MS = 1500L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launcherResumed;
    private boolean receiverRegistered;
    private long lastStorageWarmMs;

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            RemovableMediaPolicy.Event event = RemovableMediaPolicy.classify(intent.getAction());
            if (event == RemovableMediaPolicy.Event.IGNORE) return;
            String detail = intent.getData() == null ? "" : intent.getData().toString();
            MediaEventTrace.record("storage", event.name().toLowerCase(java.util.Locale.ROOT), detail);
            MediaListenerService.refreshActiveSessions();
            if (RemovableMediaPolicy.shouldWarm(event, launcherResumed,
                    UiPersonalizationPrefs.mediaStartupWarmup(Ts18LauncherApplication.this))) {
                warmConfiguredMusicAfterMount();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NavigationPermissionBootstrapper.ensureEarly(this);
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
        MediaEventTrace.record("process", "created");
    }

    private void warmConfiguredMusicAfterMount() {
        long now = SystemClock.uptimeMillis();
        if (lastStorageWarmMs > 0L && now - lastStorageWarmMs < STORAGE_WARM_DEBOUNCE_MS) {
            MediaEventTrace.record("storage", "warm-debounced");
            return;
        }
        lastStorageWarmMs = now;
        String packageName = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        if (packageName.isEmpty()) packageName = TopwayAdapter.defaultMusicPackage(this);
        if (packageName.isEmpty()) {
            MediaEventTrace.record("storage", "warm-skipped", "no configured music package");
            return;
        }
        final String target = packageName;
        MediaEventTrace.record("storage", "warm-request", target);
        final MediaSourceBootstrapper bootstrapper = new MediaSourceBootstrapper(this);
        bootstrapper.warm(target);
        handler.postDelayed(() -> {
            bootstrapper.destroy();
            MediaEventTrace.record("storage", "warm-window-finished", target);
        }, MediaSourceBootstrapper.PREPARE_TIMEOUT_MS + 750L);
    }

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
        MediaEventTrace.record("home", "resumed");
    }

    @Override public void onActivityPaused(Activity activity) {
        if (!isLauncher(activity)) return;
        launcherResumed = false;
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
            } catch (RuntimeException ignored) {
                // The framework may already have torn the process down.
            }
            receiverRegistered = false;
        }
        unregisterActivityLifecycleCallbacks(this);
        handler.removeCallbacksAndMessages(null);
        MediaEventTrace.record("process", "terminated");
        super.onTerminate();
    }
}
