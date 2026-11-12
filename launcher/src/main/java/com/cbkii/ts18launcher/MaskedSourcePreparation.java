package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One operator-authorised, bounded closed-app transaction. No playback or tuner commands. */
final class MaskedSourcePreparation {
    private final Activity home;
    private final StartupReadinessCoordinator.LoaderUi loader;
    private final StartupReadinessCoordinator.NavigationHook navigation;
    private final Runnable released;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ts18-source-staging"); t.setDaemon(true); return t;
    });
    private final java.util.List<Consumer<Boolean>> completions = new java.util.ArrayList<>();
    private String pkg, launchEntry, resumedEntry;
    private long deadline;
    private int sourceTask;
    private volatile boolean ended, cancelRequested;
    private boolean dispatched, restoring, launchInFlight, presentationLeased;

    MaskedSourcePreparation(Activity home, StartupReadinessCoordinator.LoaderUi loader,
            StartupReadinessCoordinator.NavigationHook navigation, Runnable released) {
        this.home = home; this.loader = loader; this.navigation = navigation; this.released = released;
    }

    void begin(String packageName, long until, Consumer<Boolean> done) {
        completions.add(done); pkg = packageName;
        deadline = Math.min(until, SystemClock.elapsedRealtime() + 12000L);
        if (!"com.navimods.radio".equals(pkg) || home.isFinishing() || home.isDestroyed()
                || !CompactStartupClient.automaticAllowed(home) || !loader.canMask()
                || deadline - SystemClock.elapsedRealtime() < 2000L) { finish(false); return; }
        try {
            Intent entry = home.getPackageManager().getLaunchIntentForPackage(pkg);
            ComponentName component = entry == null ? null : entry.getComponent();
            if (component == null) { finish(false); return; }
            ActivityInfo info = home.getPackageManager().getActivityInfo(component, 0);
            if (!info.enabled || !info.exported || !pkg.equals(info.packageName)) { finish(false); return; }
            launchEntry = component.flattenToString();
            resumedEntry = new ComponentName(pkg, info.targetActivity == null ? info.name : info.targetActivity).flattenToString();
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
            finish(false); return;
        }
        main.postDelayed(this::cancel, Math.max(1L, deadline - SystemClock.elapsedRealtime()));
        if (navigation != null) { presentationLeased = true; navigation.deferAutomaticPresentation(true); }
        awaitNavigation();
    }

    private void awaitNavigation() {
        if (ended) return;
        // Do not cancel/replay an already accepted map transaction to get a staging slot.
        if (navigation != null && navigation.busy()) {
            main.postDelayed(this::awaitNavigation, 250L); return;
        }
        loader.render(home.getString(R.string.startup_radio_preparing), true);
        loader.mask(true);
        loader.whenMaskDrawn(() -> {
            if (ended || !loader.ownsMask() || !loader.canMask()) { finish(false); return; }
            launchInFlight = true;
            io.execute(() -> {
                if (ended || cancelRequested) { postLaunchFailed(); return; }
                NavigationHelperResult before = observe(deadline);
                if (!valid(before)) { postLaunchFailed(); return; }
                if (before.taskId != home.getTaskId()) {
                    if (navigation == null || before.taskId != navigation.taskId()
                            || !before.component.equals("app.organicmaps.incar/app.organicmaps.MwmActivity")) {
                        postLaunchFailed(); return;
                    }
                    NavigationHelperResult parked = RootTaskSession.get(home).runCurrent(deadline,
                            "return-home", user(), "app.organicmaps.incar", Integer.toString(before.taskId),
                            home.getPackageName(), Integer.toString(home.getTaskId()));
                    if (parked == null || !parked.success) { postLaunchFailed(); return; }
                } else if (!homeComponent(before.component)) { postLaunchFailed(); return; }
                if (ended || cancelRequested || !StartupMaskFocus.validTitle(StartupMaskFocus.activeTitle)) {
                    postLaunchFailed(); return;
                }
                NavigationRootHelper helper = new NavigationRootHelper(home);
                helper.setDeadline(deadline);
                NavigationHelperResult launch = helper.run("startup-source", pkg, launchEntry,
                        home.getPackageName(), Integer.toString(home.getTaskId()));
                main.post(() -> {
                    launchInFlight = false;
                    if (ended) return;
                    // An uncertain accepted launch is never replayed through another method.
                    dispatched = launch.success || "STARTUP_SOURCE_UNCERTAIN".equals(launch.code)
                            || "PHASE_TIMEOUT".equals(launch.code);
                    if (!launch.success || cancelRequested) { cleanupAfterLaunch(); return; }
                    observeSource();
                });
            });
        });
    }

    private void observeSource() {
        if (ended || restoring) return;
        if (home.isFinishing() || home.isDestroyed() || !loader.ownsMask() || !loader.canMask()
                || !pkg.equals(MediaEngineSelector.configuredPackage(home, MediaSourceKey.RADIO))) {
            cancel(); return;
        }
        Ts18LauncherApplication.mediaFacade(home).refresh();
        io.execute(() -> {
            NavigationHelperResult foreground = observe(deadline);
            main.post(() -> {
                if (ended || cancelRequested || restoring) return;
                if (valid(foreground)) {
                    boolean stillHome = foreground.taskId == home.getTaskId() && homeComponent(foreground.component);
                    if (!stillHome && !resumedEntry.equals(foreground.component)) {
                        // Preserve login/settings/permission flows instead of hiding or dismissing them.
                        finish(false); return;
                    }
                    if (!stillHome) sourceTask = foreground.taskId;
                    MediaSourceState source = Ts18LauncherApplication.mediaFacade(home).state(MediaSourceKey.RADIO);
                    if (!stillHome && source.live() && pkg.equals(source.packageName)
                            && (source.playing() || (source.actions & (android.media.session.PlaybackState.ACTION_PLAY
                                | android.media.session.PlaybackState.ACTION_PLAY_PAUSE)) != 0L)) {
                        restoreHome(source); return;
                    }
                }
                if (SystemClock.elapsedRealtime() >= deadline) cancel();
                else main.postDelayed(this::observeSource, 500L);
            });
        });
    }

    private void restoreHome(MediaSourceState source) {
        restoring = true;
        io.execute(() -> {
            NavigationHelperResult returned = returnHome(deadline);
            NavigationHelperResult focused = returned != null && returned.success ? observe(deadline) : null;
            main.postDelayed(() -> {
                if (ended) return;
                MediaSourceState after = Ts18LauncherApplication.mediaFacade(home).state(MediaSourceKey.RADIO);
                boolean survived = after.live() && pkg.equals(after.packageName)
                        && source.sessionIdentity != null && source.sessionIdentity.equals(after.sessionIdentity);
                finish(!cancelRequested && survived && valid(focused) && focused.taskId == home.getTaskId()
                        && homeComponent(focused.component));
            }, 300L); // Observe onStop/session teardown after the verified task handoff.
        });
    }

    boolean serves(String packageName) { return !ended && packageName.equals(pkg); }
    void join(Consumer<Boolean> callback) { if (!ended) completions.add(callback); }

    private void postLaunchFailed() { main.post(() -> { launchInFlight = false; cleanupAfterLaunch(); }); }

    void cancel() {
        if (ended) return;
        cancelRequested = true;
        // Keep the mask until an in-flight launch has reached its bounded outcome.
        if (launchInFlight) return;
        if (restoring) return;
        cleanupAfterLaunch();
    }

    private void cleanupAfterLaunch() {
        if (ended) return;
        restoring = true;
        // Cleanup may restore only the exact source task observed in this transaction.
        // A foreign/permission Activity is never moved out of the way.
        if (dispatched) {
            io.execute(() -> {
                long cleanupUntil = SystemClock.elapsedRealtime() + 1500L;
                NavigationHelperResult current = observe(cleanupUntil);
                if (valid(current) && resumedEntry.equals(current.component)) {
                    sourceTask = current.taskId;
                    returnHome(cleanupUntil);
                }
                postFinish(false);
            });
        } else finish(false);
    }

    private NavigationHelperResult returnHome(long until) {
        return RootTaskSession.get(home).runCurrent(until, "startup-home", user(), pkg, resumedEntry,
                Integer.toString(sourceTask), home.getPackageName(), Integer.toString(home.getTaskId()));
    }

    private NavigationHelperResult observe(long until) {
        return RootTaskSession.get(home).runCurrent(Math.min(until, SystemClock.elapsedRealtime() + 2000L), "observe", user());
    }

    private static boolean valid(NavigationHelperResult result) {
        return result != null && result.success && result.taskId > 0 && result.userId == AndroidUserId.current();
    }
    private static boolean homeComponent(String component) {
        return "com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity".equals(component)
                || "com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias".equals(component);
    }
    private static String user() { return Integer.toString(AndroidUserId.current()); }
    private void postFinish(boolean ready) { main.post(() -> finish(ready)); }

    private void finish(boolean ready) {
        if (ended) return;
        ended = true;
        main.removeCallbacksAndMessages(null);
        loader.hide();
        if (navigation != null && presentationLeased) navigation.deferAutomaticPresentation(false);
        io.shutdown();
        released.run();
        MediaEventTrace.record("startup-loader", ready ? "background-ready" : "released", pkg);
        java.util.List<Consumer<Boolean>> done = new java.util.ArrayList<>(completions);
        completions.clear();
        for (Consumer<Boolean> callback : done) if (callback != null) callback.accept(ready);
    }
}
