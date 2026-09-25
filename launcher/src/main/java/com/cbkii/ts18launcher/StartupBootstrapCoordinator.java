package com.cbkii.ts18launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Bounded true-cold-start preparation. One owner serialises exact-source foreground priming behind
 * the launcher mask; it does not race a second background warm-up path against the same source.
 */
final class StartupBootstrapCoordinator implements MediaListenerService.Observer {
    private static final long GLOBAL_TIMEOUT_MS = 11000L;
    private static final long SOURCE_TIMEOUT_MS = 5000L;
    private static final long HOME_RETURN_TIMEOUT_MS = 1200L;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StartupMaskController mask;
    private final List<String> sources = new ArrayList<>();
    private final View decor;
    private final ViewTreeObserver.OnWindowFocusChangeListener focusListener;

    private MediaListenerService.Snapshot genericSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);
    private MediaListenerService.Snapshot radioSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);
    private boolean running;
    private boolean destroyed;
    private boolean observerRegistered;
    private boolean waitingForHome;
    private int index;
    private int sourceGeneration;
    private long globalDeadline;
    private long sourceDeadline;
    private String currentPackage = "";

    StartupBootstrapCoordinator(Activity activity, MediaSourceBootstrapper ignoredBootstrapper) {
        this.activity = activity;
        this.mask = new StartupMaskController(activity);
        this.decor = activity.getWindow().getDecorView();
        this.focusListener = hasFocus -> {
            if (hasFocus && waitingForHome) onHomeReturned();
        };
        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (observer.isAlive()) observer.addOnWindowFocusChangeListener(focusListener);
    }

    boolean isRunning() { return running; }

    void start() {
        if (running || destroyed) return;
        running = true;
        globalDeadline = SystemClock.uptimeMillis() + GLOBAL_TIMEOUT_MS;
        boolean canMaskExternal = mask.show();
        MediaEventTrace.record("startup", "begin", "externalMask=" + canMaskExternal);

        if (!canMaskExternal) {
            MediaEventTrace.record("startup", "foreground-prime-skipped",
                    "SYSTEM_ALERT_WINDOW unavailable");
            finish();
            return;
        }

        MediaListenerService.addObserver(this);
        observerRegistered = true;
        LinkedHashSet<String> exact = new LinkedHashSet<>();
        String music = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_MUSIC);
        if (music == null || music.isEmpty()) music = TopwayAdapter.defaultMusicPackage(activity);
        String radio = RadioProvider.resolvePackage(activity);
        if (MediaSelection.RADIO.equals(LauncherPrefs.lastSource(activity))) {
            addQualified(exact, radio);
            addQualified(exact, music);
        } else {
            addQualified(exact, music);
            addQualified(exact, radio);
        }
        sources.addAll(exact);
        primeNext();
    }

    @Override public void onMediaStateChanged(MediaListenerService.Snapshot genericMedia,
                                                MediaListenerService.Snapshot radio) {
        genericSnapshot = genericMedia == null
                ? new MediaListenerService.Snapshot("", "", "", false) : genericMedia;
        radioSnapshot = radio == null
                ? new MediaListenerService.Snapshot("", "", "", false) : radio;
        if (!running || currentPackage.isEmpty() || waitingForHome) return;
        if (isUsable(currentPackage)) sourceReady(currentPackage, sourceGeneration);
    }

    private void addQualified(LinkedHashSet<String> values, String packageName) {
        if (packageName == null || packageName.isEmpty()
                || activity.getPackageName().equals(packageName)) return;
        if (!MediaSourceAdapter.AUXIO_PACKAGE.equals(packageName)
                && !MediaSourceAdapter.NAVRADIO_PACKAGE.equals(packageName)) {
            MediaEventTrace.record("startup", "foreground-prime-unqualified", packageName);
            return;
        }
        values.add(packageName);
    }

    private void primeNext() {
        if (!running || destroyed || waitingForHome) return;
        if (SystemClock.uptimeMillis() >= globalDeadline || index >= sources.size()) {
            finish();
            return;
        }
        String packageName = sources.get(index++);
        currentPackage = packageName;
        int generation = ++sourceGeneration;
        if (isUsable(packageName)) {
            MediaEventTrace.record("startup", "source-already-ready", packageName);
            currentPackage = "";
            primeNext();
            return;
        }
        if (!AppResolver.launchPackageQuietly(activity, packageName)) {
            MediaEventTrace.record("startup", "source-launch-failed", packageName);
            currentPackage = "";
            primeNext();
            return;
        }
        MediaEventTrace.record("startup", "source-launched", packageName);
        sourceDeadline = Math.min(globalDeadline, SystemClock.uptimeMillis() + SOURCE_TIMEOUT_MS);
        MediaListenerService.refreshActiveSessions();
        scheduleSourceTimeout(packageName, generation);
    }

    private boolean isUsable(String packageName) {
        return snapshotUsable(genericSnapshot, packageName) || snapshotUsable(radioSnapshot, packageName);
    }

    private static boolean snapshotUsable(MediaListenerService.Snapshot snapshot, String packageName) {
        return snapshot != null && packageName.equals(snapshot.packageName)
                && snapshot.supports(MediaListenerService.Command.PLAY_PAUSE);
    }

    private void scheduleSourceTimeout(String packageName, int generation) {
        long delay = Math.max(1L, sourceDeadline - SystemClock.uptimeMillis());
        handler.postDelayed(() -> {
            if (!running || destroyed || waitingForHome || generation != sourceGeneration
                    || !packageName.equals(currentPackage)) return;
            if (isUsable(packageName)) {
                sourceReady(packageName, generation);
                return;
            }
            MediaEventTrace.record("startup", "source-timeout", packageName);
            returnHomeThenContinue(generation);
        }, delay);
    }

    private void sourceReady(String packageName, int generation) {
        if (!running || destroyed || generation != sourceGeneration
                || !packageName.equals(currentPackage)) return;
        MediaEventTrace.record("startup", "source-controller-ready", packageName);
        returnHomeThenContinue(generation);
    }

    private void returnHomeThenContinue(int generation) {
        if (generation != sourceGeneration || waitingForHome) return;
        waitingForHome = true;
        boolean accepted = HomeMode.bringLauncherToFront(activity);
        MediaEventTrace.record("startup", accepted ? "home-requested" : "home-request-failed");
        if (!accepted) {
            waitingForHome = false;
            currentPackage = "";
            primeNext();
            return;
        }
        if (decor.hasWindowFocus()) {
            decor.post(this::onHomeReturned);
            return;
        }
        handler.postDelayed(() -> {
            if (!running || destroyed || !waitingForHome || generation != sourceGeneration) return;
            MediaEventTrace.record("startup", "home-focus-timeout", currentPackage);
            onHomeReturned();
        }, HOME_RETURN_TIMEOUT_MS);
    }

    private void onHomeReturned() {
        if (!running || destroyed || !waitingForHome) return;
        waitingForHome = false;
        MediaEventTrace.record("startup", "home-returned", currentPackage);
        currentPackage = "";
        if (SystemClock.uptimeMillis() >= globalDeadline) finish();
        else primeNext();
    }

    private void finish() {
        if (!running) return;
        running = false;
        waitingForHome = false;
        currentPackage = "";
        sourceGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (observerRegistered) {
            MediaListenerService.removeObserver(this);
            observerRegistered = false;
        }
        HomeMode.bringLauncherToFront(activity);
        MediaListenerService.refreshActiveSessions();
        mask.dismiss();
        MediaEventTrace.record("startup", "complete");
    }

    void destroy() {
        destroyed = true;
        running = false;
        waitingForHome = false;
        currentPackage = "";
        sourceGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (observerRegistered) {
            MediaListenerService.removeObserver(this);
            observerRegistered = false;
        }
        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(focusListener);
        mask.dismiss();
    }
}
