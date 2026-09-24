package com.cbkii.ts18launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Bounded true-cold-start preparation. Background contracts remain preferred; when Android overlay
 * access is already granted, exact configured media Activities may be primed sequentially behind
 * the opaque startup mask and immediately returned to HOME.
 */
final class StartupBootstrapCoordinator {
    private static final long GLOBAL_TIMEOUT_MS = 9000L;
    private static final long SOURCE_TIMEOUT_MS = 2400L;
    private static final long POLL_MS = 150L;
    private static final long HOME_SETTLE_MS = 250L;

    private final Activity activity;
    private final MediaSourceBootstrapper mediaBootstrapper;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StartupMaskController mask;
    private final List<String> sources = new ArrayList<>();
    private boolean running;
    private boolean destroyed;
    private int index;
    private long globalDeadline;
    private long sourceDeadline;

    StartupBootstrapCoordinator(Activity activity, MediaSourceBootstrapper mediaBootstrapper) {
        this.activity = activity;
        this.mediaBootstrapper = mediaBootstrapper;
        this.mask = new StartupMaskController(activity);
    }

    boolean isRunning() { return running; }

    void start() {
        if (running || destroyed) return;
        running = true;
        globalDeadline = SystemClock.uptimeMillis() + GLOBAL_TIMEOUT_MS;
        boolean canMaskExternal = mask.show();
        MediaEventTrace.record("startup", "begin", "externalMask=" + canMaskExternal);
        mediaBootstrapper.warmConfiguredSources();

        if (!canMaskExternal) {
            MediaEventTrace.record("startup", "foreground-prime-skipped",
                    "SYSTEM_ALERT_WINDOW unavailable");
            handler.postDelayed(this::finish, 900L);
            return;
        }

        LinkedHashSet<String> exact = new LinkedHashSet<>();
        String music = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_MUSIC);
        if (music == null || music.isEmpty()) music = TopwayAdapter.defaultMusicPackage(activity);
        String radio = RadioProvider.resolvePackage(activity);
        if (MediaSelection.RADIO.equals(LauncherPrefs.lastSource(activity))) {
            add(exact, radio);
            add(exact, music);
        } else {
            add(exact, music);
            add(exact, radio);
        }
        sources.addAll(exact);
        primeNext();
    }

    private void add(LinkedHashSet<String> values, String packageName) {
        if (packageName != null && !packageName.isEmpty()
                && !activity.getPackageName().equals(packageName)) values.add(packageName);
    }

    private void primeNext() {
        if (!running || destroyed) return;
        if (SystemClock.uptimeMillis() >= globalDeadline || index >= sources.size()) {
            finish();
            return;
        }
        String packageName = sources.get(index++);
        if (MediaListenerService.hasObservableSession(packageName)) {
            MediaEventTrace.record("startup", "source-already-ready", packageName);
            primeNext();
            return;
        }
        if (!AppResolver.launchPackageQuietly(activity, packageName)) {
            MediaEventTrace.record("startup", "source-launch-failed", packageName);
            primeNext();
            return;
        }
        MediaEventTrace.record("startup", "source-launched", packageName);
        sourceDeadline = Math.min(globalDeadline, SystemClock.uptimeMillis() + SOURCE_TIMEOUT_MS);
        pollSource(packageName);
    }

    private void pollSource(String packageName) {
        if (!running || destroyed) return;
        long now = SystemClock.uptimeMillis();
        if (MediaListenerService.hasObservableSession(packageName)) {
            MediaEventTrace.record("startup", "source-session-ready", packageName);
            returnHomeThenContinue();
            return;
        }
        if (now >= sourceDeadline || now >= globalDeadline) {
            MediaEventTrace.record("startup", "source-timeout", packageName);
            returnHomeThenContinue();
            return;
        }
        handler.postDelayed(() -> pollSource(packageName), POLL_MS);
    }

    private void returnHomeThenContinue() {
        boolean accepted = HomeMode.bringLauncherToFront(activity);
        MediaEventTrace.record("startup", accepted ? "home-requested" : "home-request-failed");
        handler.postDelayed(this::primeNext, HOME_SETTLE_MS);
    }

    private void finish() {
        if (!running) return;
        running = false;
        handler.removeCallbacksAndMessages(null);
        HomeMode.bringLauncherToFront(activity);
        MediaListenerService.refreshActiveSessions();
        mask.dismiss();
        MediaEventTrace.record("startup", "complete");
    }

    void destroy() {
        destroyed = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        mask.dismiss();
    }
}
