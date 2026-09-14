package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Coordinates launcher-owned geometry with an explicitly selected external navigation task. */
final class NavigationWindowController {
    private static final String TAG = "TS18Nav";
    private static final long POST_LAUNCH_RECONCILE_MS = 350L;

    private enum State {
        IDLE,
        STARTING,
        PRESENTING,
        WINDOWED,
        SUSPENDING,
        SUSPENDED,
        FULLSCREEN_HANDOFF,
        FAILED,
        DESTROYED
    }

    private final Activity activity;
    private final NativeNavigationPanel panel;
    private final Handler main = new Handler(Looper.getMainLooper());

    private NavigationSurfaceBackend backend;
    private String backendMode = "";
    private State state = State.IDLE;
    private boolean homeVisible;
    private int generation;
    private String activePackage = "";
    private int activeTaskId = -1;
    private NavigationWindowBounds bounds;

    NavigationWindowController(Activity activity, NativeNavigationPanel panel) {
        this.activity = activity;
        this.panel = panel;
        panel.setBoundsListener(this::onBoundsChanged);
        panel.setAction("Open navigation", () -> openFullscreen(null));
    }

    void onHomeVisible() {
        if (state == State.DESTROYED) return;
        homeVisible = true;
        if (state == State.FULLSCREEN_HANDOFF) state = State.IDLE;
        NavigationWindowBounds measured = panel.currentBounds();
        if (measured != null) bounds = measured;
        reconcile(false);
    }

    void onHomeStopped() {
        if (state == State.DESTROYED) return;
        homeVisible = false;
        if (state == State.FULLSCREEN_HANDOFF) {
            Log.i(TAG, "HOME stopped during intentional fullscreen handoff");
            return;
        }
        generation++;
        state = State.SUSPENDED;
        // Stop queued/running helper work without mutating the external task. Retain task/package
        // authority so HOME return can validate the same task before any reacquisition.
        destroyBackendInstance();
        Log.i(TAG, "HOME stopped; helper work cancelled, task authority retained");
    }

    void onLauncherOverlayOpened() {
        suspendForLauncherSurface("launcher overlay");
    }

    void onLauncherOverlayClosed() {
        if (state == State.DESTROYED) return;
        homeVisible = true;
        reconcile(true);
    }

    void suspendForExperimentalMap() {
        suspendForLauncherSurface("Leaflet comparator active");
    }

    void openFullscreen(Location location) {
        if (state == State.DESTROYED) return;
        String pkg = selectedPackage();
        if (pkg.isEmpty()) {
            panel.showUnavailable("Choose a Navigation app in Settings", null);
            return;
        }

        if (backend == null || activeTaskId <= 0 || !pkg.equals(activePackage)) {
            NavigationProvider.open(activity, pkg, location);
            return;
        }

        final int request = ++generation;
        final int task = activeTaskId;
        state = State.FULLSCREEN_HANDOFF;
        panel.showStarting(label(pkg), "Opening fullscreen · same task " + task);
        backend.fullscreen(pkg, task, result -> {
            if (!isRequestCurrent(request, pkg, false)) return;
            if (acceptIdentity(result, pkg, task) && result.windowingMode == 1) {
                activeTaskId = result.taskId;
                Log.i(TAG, "fullscreen task=" + task + " package=" + pkg);
                // The helper already foregrounded the exact authorised task. Do not launch the
                // package again unless a semantic location handoff still needs to be delivered.
                if (location != null && !NavigationProvider.open(activity, pkg, location)) {
                    Log.w(TAG, "location handoff failed after fullscreen transition package=" + pkg);
                }
                return;
            }
            Log.w(TAG, "fullscreen helper failed code=" + result.code + " raw=" + result.raw);
            NavigationProvider.open(activity, pkg, location);
        });
    }

    void destroy() {
        generation++;
        state = State.DESTROYED;
        main.removeCallbacksAndMessages(null);
        destroyBackendInstance();
    }

    private void onBoundsChanged(NavigationWindowBounds next) {
        if (state == State.DESTROYED) return;
        if (next.equals(bounds)) return;
        bounds = next;
        if (homeVisible) reconcile(false);
    }

    private void reconcile(boolean force) {
        if (!homeVisible || state == State.DESTROYED || bounds == null) return;

        String mode = HomeNavigationSurfacePolicy.mode(activity);
        if (HomeNavigationSurfacePolicy.LEAFLET.equals(mode)) {
            suspendForExperimentalMap();
            return;
        }

        String pkg = selectedPackage();
        if (needsManagedTaskTransition(mode, pkg)) {
            transitionManagedTask(mode, pkg, force);
            return;
        }

        if (HomeNavigationSurfacePolicy.FULLSCREEN.equals(mode)) {
            destroyBackendInstance();
            backendMode = HomeNavigationSurfacePolicy.FULLSCREEN;
            state = State.IDLE;
            panel.showFullscreenOnly(label(pkg), () -> openFullscreen(null));
            return;
        }

        if (pkg.isEmpty()) {
            activePackage = "";
            activeTaskId = -1;
            panel.showUnavailable("Choose a Navigation app in Settings", null);
            state = State.FAILED;
            return;
        }

        if (!pkg.equals(activePackage)) {
            activePackage = pkg;
            activeTaskId = -1;
            generation++;
        }

        ensureBackend(mode);
        if (backend == null) {
            panel.showUnavailable("Unsupported navigation surface mode", () -> openFullscreen(null));
            state = State.FAILED;
            return;
        }

        final NavigationWindowBounds target = bounds;
        final int request = ++generation;
        if (activeTaskId > 0) {
            state = State.PRESENTING;
            panel.showStarting(label(pkg), backend.label() + " · verifying task " + activeTaskId);
            final int knownTask = activeTaskId;
            backend.verify(pkg, target, knownTask,
                    result -> verifyOrRepair(request, pkg, target, knownTask, result));
            return;
        }

        state = State.STARTING;
        panel.showStarting(label(pkg), backend.label() + " · acquiring configured task");
        launchSelectedPackage(pkg, target);
        main.postDelayed(() -> {
            if (!isRequestCurrent(request, pkg, true)) return;
            present(request, pkg, target, -1);
        }, POST_LAUNCH_RECONCILE_MS);
    }

    private boolean needsManagedTaskTransition(String nextMode, String nextPackage) {
        return activeTaskId > 0 && isExperimentalMode(backendMode) && !activePackage.isEmpty()
                && (!backendMode.equals(nextMode) || !activePackage.equals(nextPackage));
    }

    private void transitionManagedTask(String nextMode, String nextPackage, boolean force) {
        String managedPackage = activePackage;
        int managedTask = activeTaskId;
        ensureBackend(backendMode);
        if (backend == null) {
            panel.showUnavailable("Previous navigation backend unavailable", () -> openFullscreen(null));
            state = State.FAILED;
            return;
        }

        final int request = ++generation;
        state = State.SUSPENDING;
        panel.showStarting(label(managedPackage), "Leaving " + backend.label());
        backend.suspend(managedPackage, managedTask, activity.getPackageName(), activity.getTaskId(), result -> {
            if (!isRequestCurrent(request, managedPackage, true)) return;
            if (!result.success && "TASK_NOT_FOUND".equals(result.code)) {
                destroyBackendInstance();
                backendMode = "";
                activeTaskId = -1;
                activePackage = nextPackage;
                state = State.SUSPENDED;
                reconcile(force);
                return;
            }
            if (!acceptIdentity(result, managedPackage, managedTask) || result.windowingMode != 1) {
                state = State.FAILED;
                panel.showUnavailable("Could not leave previous navigation surface · " + result.code,
                        () -> NavigationProvider.open(activity, managedPackage, null));
                Log.w(TAG, "surface transition failed code=" + result.code + " raw=" + result.raw);
                return;
            }

            destroyBackendInstance();
            backendMode = "";
            if (!managedPackage.equals(nextPackage)) {
                activePackage = nextPackage;
                activeTaskId = -1;
            } else {
                activeTaskId = result.taskId;
            }
            state = State.SUSPENDED;
            reconcile(force);
        });
    }

    private void suspendForLauncherSurface(String reason) {
        if (state == State.DESTROYED) return;
        generation++;
        state = State.SUSPENDED;
        if (activeTaskId <= 0 || activePackage.isEmpty() || !isExperimentalMode(backendMode)) {
            Log.i(TAG, "suspend " + reason + " without managed task");
            return;
        }

        ensureBackend(backendMode);
        if (backend == null) return;
        final int request = generation;
        final String pkg = activePackage;
        final int task = activeTaskId;
        state = State.SUSPENDING;
        backend.suspend(pkg, task, activity.getPackageName(), activity.getTaskId(), result -> {
            if (!isRequestCurrent(request, pkg, true)) return;
            state = State.SUSPENDED;
            if (!result.success && "TASK_NOT_FOUND".equals(result.code)) {
                activeTaskId = -1;
                Log.i(TAG, "suspend found task already gone reason=" + reason);
            } else if (acceptIdentity(result, pkg, task) && result.windowingMode == 1) {
                activeTaskId = result.taskId;
                Log.i(TAG, "suspended task=" + task + " reason=" + reason);
            } else {
                Log.w(TAG, "suspend failed code=" + result.code + " reason=" + reason
                        + " raw=" + result.raw);
            }
        });
    }

    private void verifyOrRepair(int request, String pkg, NavigationWindowBounds target,
            int knownTask, NavigationHelperResult result) {
        if (!isRequestCurrent(request, pkg, true)) return;
        if (acceptWindowedResult(result, pkg, target, knownTask)) {
            markWindowed(result, pkg, target);
            return;
        }
        if (!result.success && "TASK_NOT_FOUND".equals(result.code)) {
            activeTaskId = -1;
            state = State.STARTING;
            panel.showStarting(label(pkg), backend.label() + " · task gone, launching selected app");
            launchSelectedPackage(pkg, target);
            main.postDelayed(() -> {
                if (!isRequestCurrent(request, pkg, true)) return;
                present(request, pkg, target, -1);
            }, POST_LAUNCH_RECONCILE_MS);
            return;
        }

        // Preserve package+task authority: repair the same task before considering a relaunch.
        panel.showStarting(label(pkg), backend.label() + " · repairing task " + knownTask);
        backend.present(pkg, target, knownTask, repaired -> {
            if (!isRequestCurrent(request, pkg, true)) return;
            if (acceptWindowedResult(repaired, pkg, target, knownTask)) {
                markWindowed(repaired, pkg, target);
            } else {
                fail(pkg, "Task " + knownTask + " rejected · " + repaired.code);
            }
        });
    }

    private void present(int request, String pkg, NavigationWindowBounds target, int taskHint) {
        if (!isRequestCurrent(request, pkg, true)) return;
        state = State.PRESENTING;
        backend.present(pkg, target, taskHint, result -> {
            if (!isRequestCurrent(request, pkg, true)) return;
            if (acceptWindowedResult(result, pkg, target, taskHint)) {
                markWindowed(result, pkg, target);
            } else {
                fail(pkg, result.code);
            }
        });
    }

    private boolean acceptWindowedResult(NavigationHelperResult result, String pkg,
            NavigationWindowBounds target, int expectedTask) {
        int expectedMode = HomeNavigationSurfacePolicy.ANDROID_PIP.equals(backendMode) ? 2 : 5;
        return acceptIdentity(result, pkg, expectedTask)
                && result.displayId == 0
                && result.windowingMode == expectedMode
                && target.toString().equals(result.bounds);
    }

    private static boolean acceptIdentity(NavigationHelperResult result, String pkg, int expectedTask) {
        if (!result.success || result.taskId <= 0 || !pkg.equals(result.packageName)) return false;
        if (expectedTask > 0 && result.taskId != expectedTask) return false;
        return result.component.startsWith(pkg + "/");
    }

    private void markWindowed(NavigationHelperResult result, String pkg, NavigationWindowBounds target) {
        activeTaskId = result.taskId;
        state = State.WINDOWED;
        panel.showReady(label(pkg) + " · " + backend.label() + "\nTask " + result.taskId
                + " · display " + result.displayId + " · mode " + result.windowingMode
                + " · " + target.width() + "×" + target.height());
        panel.setAction("Open fullscreen", () -> openFullscreen(null));
        Log.i(TAG, "windowed package=" + pkg + " task=" + result.taskId + " stack="
                + result.stackId + " mode=" + result.windowingMode + " bounds=" + result.bounds);
    }

    private void fail(String pkg, String detail) {
        state = State.FAILED;
        panel.showUnavailable(backend.label() + " unavailable · " + detail,
                () -> NavigationProvider.open(activity, pkg, null));
        Log.w(TAG, "navigation experiment failed package=" + pkg + " detail=" + detail);
    }

    private void ensureBackend(String mode) {
        if (backend != null && mode.equals(backendMode)) return;
        destroyBackendInstance();
        backendMode = mode;
        if (HomeNavigationSurfacePolicy.RAW_FREEFORM.equals(mode)) {
            backend = new RawFreeformTaskBackend(activity);
        } else if (HomeNavigationSurfacePolicy.ANDROID_PIP.equals(mode)) {
            backend = new AndroidPipBackend(activity);
        }
    }

    private void destroyBackendInstance() {
        if (backend != null) backend.destroy();
        backend = null;
    }

    private void launchSelectedPackage(String pkg, NavigationWindowBounds target) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            fail(pkg, "No exported launcher Activity");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(target.asRect());
            activity.startActivity(launch, options.toBundle());
        } catch (RuntimeException first) {
            try {
                activity.startActivity(launch);
            } catch (RuntimeException second) {
                fail(pkg, "Launch failed");
            }
        }
    }

    private String selectedPackage() {
        String configured = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_NAV);
        if (!configured.isEmpty()) return configured;
        return AppResolver.isInstalled(activity, AppResolver.ORGANIC_MAPS_INCAR)
                ? AppResolver.ORGANIC_MAPS_INCAR : "";
    }

    private String label(String pkg) {
        return pkg == null || pkg.isEmpty() ? "Navigation"
                : AppResolver.labelFor(activity, pkg, "Navigation");
    }

    private boolean isRequestCurrent(int request, String pkg, boolean requireHome) {
        return state != State.DESTROYED
                && request == generation
                && pkg.equals(activePackage)
                && (!requireHome || homeVisible);
    }

    private static boolean isExperimentalMode(String mode) {
        return HomeNavigationSurfacePolicy.RAW_FREEFORM.equals(mode)
                || HomeNavigationSurfacePolicy.ANDROID_PIP.equals(mode);
    }
}
