package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

/**
 * Owns the deterministic selected-navigation task contract for HOME.
 * The configured package is always authority; observed recent/focused tasks never redefine selection.
 */
final class NavigationWindowController {
    enum State { IDLE, STARTING, WINDOWED, SUSPENDED, FULLSCREEN_HANDOFF, FAILED, DESTROYED }

    private static final String ORGANIC_MAPS_INCAR = "app.organicmaps.incar";
    private static final long POST_LAUNCH_RECONCILE_MS = 300L;

    private final Activity activity;
    private final NativeNavigationPanel panel;
    private final TopwayFreeformBackend backend;
    private final Handler main = new Handler(Looper.getMainLooper());

    private State state = State.IDLE;
    private NavigationWindowBounds bounds;
    private String activePackage = "";
    private int generation;
    private boolean homeVisible;

    NavigationWindowController(Activity activity, NativeNavigationPanel panel) {
        this.activity = activity;
        this.panel = panel;
        backend = new TopwayFreeformBackend(activity);
        panel.setBoundsListener(this::onBoundsChanged);
        panel.setRetryAction(this::retry);
    }

    State state() { return state; }

    void onHomeVisible() {
        if (state == State.DESTROYED) return;
        homeVisible = true;
        NavigationWindowBounds current = panel.currentBounds();
        if (current != null) bounds = current;
        reconcile(false);
    }

    void onHomeStopped() {
        homeVisible = false;
        if (state == State.WINDOWED || state == State.STARTING) state = State.SUSPENDED;
    }

    void onLauncherOverlayOpened() {
        if (state == State.DESTROYED) return;
        state = State.SUSPENDED;
        // Bring the HOME task/stack back to the foreground without destroying navigation state.
        backend.focus(activity.getPackageName(), ignored -> { });
    }

    void onLauncherOverlayClosed() {
        if (state == State.DESTROYED) return;
        if (homeVisible) reconcile(false);
    }

    void suspendForExperimentalMap() {
        if (state == State.DESTROYED) return;
        state = State.SUSPENDED;
        backend.focus(activity.getPackageName(), ignored -> { });
    }

    boolean openFullscreen(Location location) {
        if (state == State.DESTROYED) return false;
        String pkg = selectedPackage();
        if (pkg.isEmpty()) return false;
        final int request = ++generation;
        state = State.FULLSCREEN_HANDOFF;
        backend.fullscreen(pkg, result -> {
            if (request != generation || state == State.DESTROYED) return;
            // The normal navigation launch remains the universal fallback and also brings the task forward.
            NavigationProvider.open(activity, pkg, location);
        });
        return true;
    }

    void retry() {
        if (state == State.DESTROYED) return;
        reconcile(true);
    }

    void destroy() {
        state = State.DESTROYED;
        homeVisible = false;
        generation++;
        main.removeCallbacksAndMessages(null);
        backend.destroy();
    }

    private void onBoundsChanged(NavigationWindowBounds changed) {
        if (state == State.DESTROYED) return;
        boolean materiallyChanged = !changed.equals(bounds);
        bounds = changed;
        if (homeVisible && materiallyChanged && state != State.FULLSCREEN_HANDOFF) reconcile(false);
    }

    private void reconcile(boolean force) {
        if (!homeVisible || state == State.DESTROYED || state == State.FULLSCREEN_HANDOFF) return;
        NavigationWindowBounds target = bounds;
        if (target == null) {
            main.postDelayed(() -> {
                if (state != State.DESTROYED && homeVisible) {
                    NavigationWindowBounds current = panel.currentBounds();
                    if (current != null) { bounds = current; reconcile(force); }
                }
            }, 80L);
            return;
        }

        String pkg = selectedPackage();
        if (pkg.isEmpty()) {
            activePackage = "";
            state = State.FAILED;
            panel.showUnavailable("Choose a Navigation app in Settings");
            return;
        }

        if (!force && state == State.WINDOWED && pkg.equals(activePackage)) {
            final int request = ++generation;
            backend.verify(pkg, target, result -> {
                if (!isCurrent(request, pkg)) return;
                if (result.success) panel.showReady();
                else launchAndWindow(pkg, target);
            });
            return;
        }
        launchAndWindow(pkg, target);
    }

    private void launchAndWindow(String pkg, NavigationWindowBounds target) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            state = State.FAILED;
            panel.showUnavailable("Navigation app unavailable");
            return;
        }

        activePackage = pkg;
        state = State.STARTING;
        panel.showStarting(AppResolver.labelFor(activity, pkg, "Navigation"));
        final int request = ++generation;

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(target.asRect());
            activity.startActivity(launch, options.toBundle());
        } catch (RuntimeException error) {
            state = State.FAILED;
            panel.showUnavailable("Navigation launch failed");
            return;
        }

        // The first launch can transiently stop HOME before the task has been resized. Do not cancel
        // the operation solely because of that lifecycle transition; the generation/package guard is
        // the authority and HOME will reconcile again when it resumes.
        main.postDelayed(() -> {
            if (!isCurrent(request, pkg)) return;
            backend.showWindowed(pkg, target, result -> {
                if (!isCurrent(request, pkg)) return;
                if (result.success) {
                    state = State.WINDOWED;
                    panel.showReady();
                } else {
                    state = State.FAILED;
                    panel.showUnavailable(failureText(result));
                }
            });
        }, POST_LAUNCH_RECONCILE_MS);
    }

    private boolean isCurrent(int request, String pkg) {
        return state != State.DESTROYED && request == generation && pkg.equals(activePackage);
    }

    private String selectedPackage() {
        String configured = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_NAV);
        if (!configured.isEmpty()) return configured;
        return packageInstalled(ORGANIC_MAPS_INCAR) ? ORGANIC_MAPS_INCAR : "";
    }

    private boolean packageInstalled(String pkg) {
        try {
            activity.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) { return false; }
    }

    private static String failureText(NavigationHelperResult result) {
        if ("ROOT_REQUIRED".equals(result.code) || "ROOT_UNAVAILABLE".equals(result.code))
            return "Native navigation needs Magisk root";
        if ("TASK_NOT_FOUND".equals(result.code)) return "Navigation task not found";
        if ("BOUNDS_MISMATCH".equals(result.code)) return "Navigation window was rejected";
        if ("TIMEOUT".equals(result.code) || "INSTALL_TIMEOUT".equals(result.code))
            return "Navigation window timed out";
        return "Native navigation unavailable · " + result.code;
    }
}
