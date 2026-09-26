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
 * Bounded exact-source foreground preparation after background readiness has failed, or when
 * the user explicitly enabled cold HOME warm-up. Native-window launches use navigation handoff.
 */
final class StartupBootstrapCoordinator implements MediaListenerService.Observer {
    interface PrimeCallback { void onResult(boolean ready, String detail); }

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
    private boolean interactive;
    private boolean currentReady;
    private int index;
    private int sourceGeneration;
    private long globalDeadline;
    private long sourceDeadline;
    private String currentPackage = "";
    private PrimeCallback interactiveCallback;
    private final java.util.Set<String> interactiveAttempts = new java.util.HashSet<>();

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
        interactive = false;
        interactiveCallback = null;
        sources.clear();
        index = 0;
        begin(GLOBAL_TIMEOUT_MS, "cold");
        if (!running) return;

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

    void primeForCommand(String packageName, PrimeCallback callback) {
        if (callback == null) return;
        if (destroyed) {
            callback.onResult(false, "Launcher unavailable");
            return;
        }
        if (isUsable(packageName)) {
            callback.onResult(true, "Controller already ready");
            return;
        }

        if (!qualified(packageName) || running || !interactiveAttempts.add(packageName)) {
            callback.onResult(false, "Cold source preparation already attempted or unavailable");
            return;
        }
        interactive = true;
        interactiveCallback = callback;
        sources.clear();
        index = 0;
        if (HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(
                HomeNavigationSurfacePolicy.mode(activity)) && activity instanceof LauncherActivity) {
            ((LauncherActivity) activity).afterMediaBootstrapHomeRestored(restored -> {
                if (!restored || destroyed) {
                    completeInteractive(false, "HOME navigation is not ready");
                    return;
                }
                beginInteractive(packageName);
            });
        } else beginInteractive(packageName);
    }

    private void beginInteractive(String packageName) {
        if (destroyed) { completeInteractive(false, "Launcher unavailable"); return; }
        begin(GLOBAL_TIMEOUT_MS, "interactive");
        if (running) { sources.add(packageName); primeNext(); }
    }

    private void begin(long timeoutMs, String mode) {
        running = true;
        waitingForHome = false;
        currentReady = false;
        currentPackage = "";
        globalDeadline = SystemClock.uptimeMillis() + timeoutMs;
        boolean canMaskExternal = mask.show();
        MediaEventTrace.record("startup", "begin", "mode=" + mode + " externalMask=" + canMaskExternal);
        if (!canMaskExternal) {
            MediaEventTrace.record("startup", "foreground-prime-skipped",
                    "SYSTEM_ALERT_WINDOW unavailable");
            if (interactive) completeInteractive(false, "Startup overlay unavailable");
            else finishCold();
            return;
        }
        MediaListenerService.addObserver(this);
        observerRegistered = true;
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
        if (qualified(packageName)) values.add(packageName);
        else if (packageName != null && !packageName.isEmpty())
            MediaEventTrace.record("startup", "foreground-prime-unqualified", packageName);
    }

    private boolean qualified(String packageName) {
        if (packageName == null || packageName.isEmpty()
                || activity.getPackageName().equals(packageName)) return false;
        return MediaSourceAdapter.AUXIO_PACKAGE.equals(packageName)
                || MediaSourceAdapter.NAVRADIO_PACKAGE.equals(packageName);
    }

    private void primeNext() {
        if (!running || destroyed || waitingForHome) return;
        if (SystemClock.uptimeMillis() >= globalDeadline || index >= sources.size()) {
            if (interactive) completeInteractive(false, "Source did not become ready in time");
            else finishCold();
            return;
        }
        String packageName = sources.get(index++);
        currentPackage = packageName;
        currentReady = false;
        int generation = ++sourceGeneration;
        // Passive startup preparation must not steal audio from the already playing source.
        if (!interactive && ((MediaSourceAdapter.NAVRADIO_PACKAGE.equals(packageName)
                && genericSnapshot.playing) || (MediaSourceAdapter.AUXIO_PACKAGE.equals(packageName)
                && radioSnapshot.playing))) {
            MediaEventTrace.record("startup", "opposite-playing-skip", packageName);
            currentPackage = "";
            primeNext();
            return;
        }
        if (isUsable(packageName)) {
            MediaEventTrace.record("startup", "source-already-ready", packageName);
            currentReady = true;
            if (interactive) completeInteractive(true, "Controller already ready");
            else {
                currentPackage = "";
                primeNext();
            }
            return;
        }
        if (HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(
                HomeNavigationSurfacePolicy.mode(activity)) && activity instanceof LauncherActivity) {
            ((LauncherActivity) activity).launchMediaBootstrap(packageName,
                    launched -> onSourceLaunched(packageName, generation, launched));
        } else {
            onSourceLaunched(packageName, generation,
                    AppResolver.launchPackageQuietly(activity, packageName));
        }
    }

    private void onSourceLaunched(String packageName, int generation, boolean launched) {
        if (!running || destroyed || generation != sourceGeneration
                || !packageName.equals(currentPackage)) return;
        if (!launched) {
            MediaEventTrace.record("startup", "source-launch-failed", packageName);
            if (interactive) completeInteractive(false, "Source launch failed");
            else {
                currentPackage = "";
                primeNext();
            }
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
        return snapshot != null && packageName != null && packageName.equals(snapshot.packageName)
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
            currentReady = false;
            MediaEventTrace.record("startup", "source-timeout", packageName);
            returnHomeThenContinue(generation);
        }, delay);
    }

    private void sourceReady(String packageName, int generation) {
        if (!running || destroyed || generation != sourceGeneration
                || !packageName.equals(currentPackage)) return;
        currentReady = true;
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
            if (interactive) completeInteractive(false, "Could not return HOME");
            else {
                currentPackage = "";
                primeNext();
            }
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
        boolean ready = currentReady;
        currentPackage = "";
        if (interactive && activity instanceof LauncherActivity
                && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(
                        HomeNavigationSurfacePolicy.mode(activity))) {
            ((LauncherActivity) activity).afterMediaBootstrapHomeRestored(restored ->
                    completeInteractive(ready && restored,
                            !restored ? "HOME navigation did not restore" :
                            ready ? "Controller ready" : "Source did not become ready"));
            return;
        }
        if (interactive) {
            completeInteractive(ready, ready ? "Controller ready" : "Source did not become ready");
            return;
        }
        if (activity instanceof LauncherActivity && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(
                HomeNavigationSurfacePolicy.mode(activity))) {
            ((LauncherActivity) activity).afterMediaBootstrapHomeRestored(restored -> {
                if (!running || destroyed) return;
                if (!restored || SystemClock.uptimeMillis() >= globalDeadline) finishCold();
                else primeNext();
            });
            return;
        }
        if (SystemClock.uptimeMillis() >= globalDeadline) finishCold();
        else primeNext();
    }

    private void finishCold() {
        if (!running) return;
        cleanupRun();
        MediaListenerService.refreshActiveSessions();
        MediaEventTrace.record("startup", "complete", "mode=cold");
    }

    private void completeInteractive(boolean ready, String detail) {
        PrimeCallback callback = interactiveCallback;
        cleanupRun();
        MediaListenerService.refreshActiveSessions();
        MediaEventTrace.record("startup", "complete",
                "mode=interactive ready=" + ready + " detail=" + detail);
        if (callback != null) callback.onResult(ready, detail);
    }

    private void cleanupRun() {
        running = false;
        waitingForHome = false;
        currentPackage = "";
        currentReady = false;
        sourceGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (observerRegistered) {
            MediaListenerService.removeObserver(this);
            observerRegistered = false;
        }
        mask.dismiss();
        sources.clear();
        index = 0;
        interactive = false;
        interactiveCallback = null;
    }

    void destroy() {
        destroyed = true;
        PrimeCallback callback = interactiveCallback;
        boolean wasInteractive = interactive && running;
        cleanupRun();
        if (wasInteractive && callback != null) callback.onResult(false, "Launcher unavailable");
        ViewTreeObserver observer = decor.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(focusListener);
    }
}
