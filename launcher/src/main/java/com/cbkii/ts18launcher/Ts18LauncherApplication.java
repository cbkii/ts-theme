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
    private MediaSourceBootstrapper mediaBootstrapper;
    private String mediaConfiguration = "";

    /** One playback-command/bootstrap owner survives HOME stop and Activity recreation. */
    static MediaSourceBootstrapper mediaSources(Context context) {
        Ts18LauncherApplication app = (Ts18LauncherApplication) context.getApplicationContext();
        String music = LauncherPrefs.packageFor(app, LauncherPrefs.KEY_MUSIC);
        if (music.isEmpty()) music = TopwayAdapter.defaultMusicPackage(app);
        String configuration = AndroidUserId.current() + ":" + music + ":"
                + RadioProvider.resolvePackage(app);
        if (app.mediaBootstrapper == null || !configuration.equals(app.mediaConfiguration)) {
            if (app.mediaBootstrapper != null) app.mediaBootstrapper.destroy();
            app.mediaBootstrapper = new MediaSourceBootstrapper(app);
            app.mediaConfiguration = configuration;
        }
        return app.mediaBootstrapper;
    }

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                ProcessMediaSessionMonitor.setVisible(Ts18LauncherApplication.this, false);
                StartupReadinessCoordinator.get(Ts18LauncherApplication.this).onScreenOff(); return;
            }
            MediaNotificationAccessBootstrapper.ensureConnected(Ts18LauncherApplication.this);
            ProcessMediaSessionMonitor.setVisible(Ts18LauncherApplication.this, launcherResumed);
            // Screen-on is a wake observation only; it is never treated as verified ACC-on.
            StartupReadinessCoordinator.get(Ts18LauncherApplication.this)
                    .onTrigger(StartupReadinessPolicy.Trigger.SCREEN_ON);
        }
    };

    private final BroadcastReceiver storageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            RemovableMediaPolicy.Event event = RemovableMediaPolicy.classify(intent.getAction());
            if (event == RemovableMediaPolicy.Event.IGNORE) return;
            String detail = intent.getData() == null ? "" : intent.getData().toString();
            MediaEventTrace.record("storage", event.name().toLowerCase(java.util.Locale.ROOT), detail);
            ProcessMediaSessionMonitor.refresh(Ts18LauncherApplication.this);
            MediaListenerService.refreshActiveSessions();
            if (RemovableMediaPolicy.shouldReconcile(event,
                    UiPersonalizationPrefs.mediaStartupWarmup(Ts18LauncherApplication.this))) {
                warmConfiguredMusicAfterMount();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (HomeMode.isDefaultHome(this)) RootTaskSession.warm(this);
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
        wake.addAction(Intent.ACTION_SCREEN_OFF);
        wake.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(wakeReceiver, wake);
        MediaEventTrace.record("process", "created");
        MediaEventTrace.record("startup", "profile", TestingProfiles.summary(this));
        // Process (re)creation: classify against the persisted boot counter and reconcile.
        StartupReadinessCoordinator.get(this).onTrigger(StartupReadinessPolicy.Trigger.RESTART);
    }

    private void warmConfiguredMusicAfterMount() {
        long now = SystemClock.uptimeMillis();
        if (lastStorageWarmMs > 0L && now - lastStorageWarmMs < STORAGE_WARM_DEBOUNCE_MS) {
            MediaEventTrace.record("storage", "warm-debounced");
            return;
        }
        lastStorageWarmMs = now;
        MediaEventTrace.record("storage", "warm-request", "startup-coordinator");
        StartupReadinessCoordinator.get(this).onTrigger(StartupReadinessPolicy.Trigger.STORAGE_MOUNT);
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
        StartupReadinessCoordinator.get(this).onHomeResumed(activity);
        ProcessMediaSessionMonitor.refresh(this);
        MediaEventTrace.record("home", "resumed");
    }

    @Override public void onActivityPaused(Activity activity) {
        if (!isLauncher(activity)) return;
        launcherResumed = false;
        StartupReadinessCoordinator.get(this).onHomePaused();
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
                unregisterReceiver(wakeReceiver);
                unregisterReceiver(storageReceiver);
            } catch (RuntimeException ignored) {
                // The framework may already have torn the process down.
            }
            receiverRegistered = false;
        }
        unregisterActivityLifecycleCallbacks(this);
        handler.removeCallbacksAndMessages(null);
        StartupReadinessCoordinator.get(this).destroy();
        if (mediaBootstrapper != null) mediaBootstrapper.destroy();
        ProcessMediaSessionMonitor.stop();
        MediaEventTrace.record("process", "terminated");
        super.onTerminate();
    }
}
