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
    private int activeTaskId = -1;
    private int generation;
    private boolean homeVisible;

    NavigationWindowController(Activity activity, NativeNavigationPanel panel) {
        this.activity = activity;
        this.panel = panel;
        backend = new TopwayFreeformBackend(activity);
        panel.setBoundsListener(this::onBoundsChanged);
        panel.setRetryAction(this::retry);
    }

    void onHomeVisible() {
        if (state == State.DESTROYED) return;
        homeVisible = true;
        if (state == State.FULLSCREEN_HANDOFF) state = State.SUSPENDED;
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
        // Bring the HOME task/stack to the foreground without destroying the selected nav task.
        backend.focus(activity.getPackageName(), -1, ignored -> { });
    }

    void onLauncherOverlayClosed() {
        if (state == State.DESTROYED) return;
        if (homeVisible) reconcile(false);
    }

    void suspendForExperimentalMap() {
        if (state == State.DESTROYED) return;
        state = State.SUSPENDED;
        backend.focus(activity.getPackageName(), -1, ignored -> { });
    }

    boolean openFullscreen(Location location) {
        if (state == State.DESTROYED) return false;
        String pkg = selectedPackage();
        if (pkg.isEmpty()) return false;
        final int request = ++generation;
        final int taskId = activeTaskId;
        state = State.FULLSCREEN_HANDOFF;
        backend.fullscreen(pkg, taskId, result -> {
            if (request != generation || state == State.DESTROYED) return;
            if (result.success && result.taskId > 0) activeTaskId = result.taskId;
            // The normal navigation launch remains the universal fallback and also carries any
            // location request. It should reuse the same task where Android's normal task rules allow.
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
            activeTaskId = -1;
            state = State.FAILED;
            panel.showUnavailable("Choose a Navigation app in Settings");
            return;
        }
        if (!pkg.equals(activePackage)) {
            switchPackage(pkg, target);
            return;
        }

        if (!force && activeTaskId > 0) {
            final int request = ++generation;
            final boolean restoreFocus = state == State.SUSPENDED;
            backend.verify(pkg, target, activeTaskId, result -> {
                if (!isCurrent(request, pkg)) return;
                if (result.success) {
                    activeTaskId = result.taskId;
                    state = State.WINDOWED;
                    if (restoreFocus) {
                        backend.focus(pkg, activeTaskId, focus -> {
                            if (!isCurrent(request, pkg)) return;
                            if (focus.success) panel.showReady();
                            else fail(focus);
                        });
                    } else panel.showReady();
                    return;
                }
                if ("TASK_NOT_FOUND".equals(result.code)) activeTaskId = -1;
                launchAndWindow(pkg, target);
            });
            return;
        }
        launchAndWindow(pkg, target);
    }

    private void switchPackage(String pkg, NavigationWindowBounds target) {
        final String previousPackage = activePackage;
        final int previousTaskId = activeTaskId;
        final int request = ++generation;
        state = State.SUSPENDED;
        panel.showStarting(AppResolver.labelFor(activity, pkg, "Navigation"));

        if (previousPackage.isEmpty() || previousTaskId <= 0) {
            adoptPackageAndLaunch(request, pkg, target);
            return;
        }

        // A no-longer-selected freeform navigator must not remain the visible authority. Foreground
        // HOME first, then launch/acquire the new configured package. Failure to focus HOME is
        // fail-open for switching because the subsequent explicit launch still establishes authority.
        backend.focus(activity.getPackageName(), -1, ignored -> adoptPackageAndLaunch(request, pkg, target));
    }

    private void adoptPackageAndLaunch(int request, String pkg, NavigationWindowBounds target) {
        if (state == State.DESTROYED || request != generation || !homeVisible) return;
        activePackage = pkg;
        activeTaskId = -1;
        state = State.IDLE;
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
        final int taskHint = activeTaskId;

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
        // the operation solely because of that lifecycle transition; generation + package + task
        // identity are the authorities and HOME will reconcile again when it resumes.
        main.postDelayed(() -> {
            if (!isCurrent(request, pkg)) return;
            backend.showWindowed(pkg, target, taskHint, result -> {
                if (!isCurrent(request, pkg)) return;
                if (!result.success && taskHint > 0 && "TASK_NOT_FOUND".equals(result.code)) {
                    // The previously validated task died between launch and reconciliation. Reacquire
                    // exactly once after the explicit package launch; do not silently switch tasks later.
                    activeTaskId = -1;
                    backend.showWindowed(pkg, target, -1, reacquired -> handleWindowResult(request, pkg, reacquired));
                    return;
                }
                handleWindowResult(request, pkg, result);
            });
        }, POST_LAUNCH_RECONCILE_MS);
    }

    private void handleWindowResult(int request, String pkg, NavigationHelperResult result) {
        if (!isCurrent(request, pkg)) return;
        if (result.success && result.taskId > 0) {
            activeTaskId = result.taskId;
            state = State.WINDOWED;
            // startActivity made this task foreground before resize; avoid another focus operation here.
            panel.showReady();
        } else fail(result);
    }

    private void fail(NavigationHelperResult result) {
        state = State.FAILED;
        panel.showUnavailable(failureText(result));
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
        if ("FULLSCREEN_REJECTED".equals(result.code)) return "Navigation fullscreen was rejected";
        if ("TIMEOUT".equals(result.code) || "INSTALL_TIMEOUT".equals(result.code))
            return "Navigation window timed out";
        return "Native navigation unavailable · " + result.code;
    }
}
