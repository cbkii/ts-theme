package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

/** Coordinates launcher-owned geometry with one authoritative external navigation task. */
final class NavigationWindowController {
    private static final String TAG = "TS18Nav";

    private enum State {
        IDLE,
        ACQUIRING,
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
    private final NavigationWindowUiState uiState = new NavigationWindowUiState();
    private final NavigationOverlayGate overlayGate = new NavigationOverlayGate();

    private NavigationSurfaceBackend backend;
    private String backendMode = "";
    private State state = State.IDLE;
    private boolean needsValidation = true;
    private boolean pendingReconcile;
    private String pendingSuspendReason = "";
    private boolean fullscreenRequested;
    private Location pendingFullscreenLocation;

    private int authorityGeneration;
    private int nextTransactionId;
    private int activeOperationId;
    private int acquisitionAttemptGeneration = -1;
    private int failureGeneration = -1;
    private String failurePackage = "";
    private String failureMode = "";
    private String failureDetail = "";

    private String configuredPackage = "";
    private String configuredMode = "";
    private String activePackage = "";
    private int activeTaskId = -1;
    private NavigationWindowBounds bounds;
    private NavigationWindowBounds appliedBounds;

    NavigationWindowController(Activity activity, NativeNavigationPanel panel) {
        this.activity = activity;
        this.panel = panel;
        panel.setBoundsListener(this::onBoundsChanged);
        panel.setAction("Open navigation", () -> openFullscreen(null));
    }

    void onHomeVisible() {
        if (state == State.DESTROYED) return;
        if (overlayGate.isPending()) {
            uiState.onHomeVisibleWithOverlay();
            overlayGate.onVisible();
            return;
        }
        overlayGate.onVisible();
        uiState.onHomeVisible();
        pendingSuspendReason = "";
        if (state == State.FULLSCREEN_HANDOFF) {
            state = State.IDLE;
            needsValidation = true;
        }
        NavigationWindowBounds measured = panel.currentBounds();
        if (measured != null) bounds = measured;
        reconcile(false);
    }

    void onHomeStopped() {
        if (state == State.DESTROYED) return;
        uiState.onHomeStopped();
        overlayGate.onStopped();
        needsValidation = true;
        if (activeOperationId != 0) {
            Log.i(TAG, "HOME stopped during bounded transaction; transaction retained id="
                    + activeOperationId);
        } else {
            Log.i(TAG, "HOME stopped; task authority retained without relaunch");
        }
    }

    void onLauncherOverlayOpened() {
        if (state == State.DESTROYED || !uiState.onLauncherOverlayOpened()) return;
        suspendForLauncherSurface("launcher overlay");
    }

    /**
     * The drawer is a launcher recovery/control surface. Show it first. If the launcher's own
     * window already has focus, Android has already completed the user-driven HOME handoff and a
     * second root focus transaction would only add latency; defer read-only validation until the
     * drawer closes. Otherwise use the guarded navigation parking transaction, which refuses to
     * steal foreground from an unrelated app.
     */
    void openLauncherOverlay(Runnable show) {
        if (state == State.DESTROYED || !uiState.onLauncherOverlayOpened()) return;
        overlayGate.cancel();
        if (show != null) show.run();
        if (activity.hasWindowFocus()) {
            pendingSuspendReason = "";
            state = State.SUSPENDED;
            needsValidation = true;
            Log.i(TAG, "launcher overlay visible with HOME already focused; root parking skipped");
            MediaEventTrace.record("drawer", "home-focus-already-owned");
            return;
        }
        Log.i(TAG, "launcher overlay visible; guarded navigation parking required");
        suspendForLauncherSurface("launcher overlay");
    }

    void cancelLauncherOverlay() {
        overlayGate.cancel();
        pendingSuspendReason = "";
    }

    void launchAfterSuspension(Runnable launch) {
        cancelLauncherOverlay();
        if (state == State.DESTROYED || !overlayGate.request(launch)) return;
        uiState.onLauncherOverlayOpened();
        suspendForLauncherSurface("external app launch");
    }

    void suspendForExperimentalMap() {
        if (state == State.DESTROYED) return;
        uiState.onLauncherOverlayOpened();
        suspendForLauncherSurface("legacy online map fallback active");
    }

    /** Returns true when the controller accepted or completed the user-authorised handoff. */
    boolean openFullscreen(Location location) {
        if (state == State.DESTROYED) return false;
        overlayGate.cancel();
        pendingSuspendReason = "";
        String pkg = selectedPackage();
        if (pkg.isEmpty()) {
            panel.showUnavailable("Choose a Navigation app in Settings", null);
            return false;
        }
        if (activeOperationId != 0) {
            fullscreenRequested = true;
            pendingFullscreenLocation = location;
            panel.showStarting(label(pkg), "Finishing the current navigation transaction");
            return true;
        }
        return openFullscreenNow(pkg, location);
    }

    void destroy() {
        overlayGate.cancel();
        authorityGeneration++;
        activeOperationId = 0;
        state = State.DESTROYED;
        destroyBackendInstance();
    }

    private void onBoundsChanged(NavigationWindowBounds next) {
        if (state == State.DESTROYED || next.equals(bounds)) return;
        bounds = next;
        needsValidation = true;
        if (activeOperationId != 0) {
            pendingReconcile = true;
        } else if (uiState.canPresentNavigation()) {
            reconcile(false);
        }
    }

    private void reconcile(boolean force) {
        if (!uiState.canPresentNavigation() || state == State.DESTROYED || bounds == null) return;
        if (activeOperationId != 0) {
            pendingReconcile = true;
            return;
        }

        String desiredMode = HomeNavigationSurfacePolicy.mode(activity);
        String desiredPackage = selectedPackage();
        if (!desiredMode.equals(configuredMode) || !desiredPackage.equals(configuredPackage)) {
            if (hasManagedNativeTask()
                    && (!HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(desiredMode)
                    || !desiredPackage.equals(activePackage))) {
                transitionManagedTask(desiredMode, desiredPackage);
                return;
            }
            adoptAuthority(desiredMode, desiredPackage);
        }

        if (HomeNavigationSurfacePolicy.LEAFLET.equals(configuredMode)) {
            state = State.SUSPENDED;
            return;
        }
        if (HomeNavigationSurfacePolicy.FULLSCREEN.equals(configuredMode)) {
            state = State.IDLE;
            panel.showFullscreenOnly(label(configuredPackage), () -> openFullscreen(null));
            return;
        }
        if (!HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(configuredMode)) {
            latchFailure(configuredPackage, configuredMode, "Unsupported navigation surface mode");
            return;
        }
        if (configuredPackage.isEmpty()) {
            latchFailure("", configuredMode, "Choose a Navigation app in Settings");
            return;
        }
        if (isFailureLatched()) {
            showLatchedFailure();
            return;
        }

        ensureNativeBackend();
        if (backend == null) {
            latchFailure(configuredPackage, configuredMode, "Native backend unavailable");
            return;
        }
        String component = resolveLaunchComponent(configuredPackage);
        if (component.isEmpty()) {
            latchFailure(configuredPackage, configuredMode, "No exported launcher Activity");
            return;
        }

        NavigationWindowBounds target = bounds;
        if (activeTaskId > 0) {
            if (!force && !needsValidation && State.WINDOWED == state
                    && target.equals(appliedBounds)) {
                showWindowedStatus(configuredPackage, activeTaskId, target);
                return;
            }
            if (needsValidation) {
                startPresent(configuredPackage, component, target, activeTaskId);
            } else {
                startVerify(configuredPackage, component, target, activeTaskId);
            }
        } else {
            startPresent(configuredPackage, component, target, -1);
        }
    }

    private void adoptAuthority(String mode, String packageName) {
        boolean packageChanged = !packageName.equals(configuredPackage);
        configuredMode = mode;
        configuredPackage = packageName;
        authorityGeneration++;
        acquisitionAttemptGeneration = -1;
        clearFailureLatch();
        needsValidation = true;
        appliedBounds = null;
        if (packageChanged) {
            activePackage = packageName;
            activeTaskId = -1;
        }
        if (!HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(mode) && activeTaskId <= 0) {
            backendMode = "";
            destroyBackendInstance();
        }
    }

    private void startVerify(String pkg, String component, NavigationWindowBounds target,
            int taskId) {
        int operation = beginOperation();
        if (operation == 0) return;
        state = State.PRESENTING;
        panel.showStarting(label(pkg), "Verifying task " + taskId + " at HOME bounds");
        backend.verify(pkg, target, taskId, result -> {
            if (!finishOperation(operation)) return;
            retainObservedTask(result, pkg);
            if (acceptWindowedResult(result, pkg, target, taskId)) {
                markWindowed(result, pkg, target);
                drainPendingWork();
                return;
            }
            if ("TASK_NOT_FOUND".equals(result.code)) {
                activeTaskId = -1;
                latchFailure(pkg, configuredMode, "Navigation app closed · use Retry");
                drainPendingWork();
                return;
            }
            if ("TASK_AMBIGUOUS".equals(result.code)
                    || "COMPONENT_MISMATCH".equals(result.code)) {
                latchFailure(pkg, configuredMode, result.code);
                drainPendingWork();
                return;
            }
            startPresent(pkg, component, currentTarget(target), taskId);
        });
    }

    private void startPresent(String pkg, String component, NavigationWindowBounds target,
            int taskHint) {
        if (!uiState.canPresentNavigation()) {
            needsValidation = true;
            drainPendingWork();
            return;
        }
        boolean acquisition = taskHint <= 0;
        if (acquisition && acquisitionAttemptGeneration == authorityGeneration) {
            latchFailure(pkg, configuredMode, "Acquisition already attempted; use Retry");
            drainPendingWork();
            return;
        }
        int operation = beginOperation();
        if (operation == 0) return;
        if (acquisition) acquisitionAttemptGeneration = authorityGeneration;
        state = acquisition ? State.ACQUIRING : State.PRESENTING;
        panel.showStarting(label(pkg), acquisition
                ? "Acquiring one configured task · transaction " + operation
                : "Repairing task " + taskHint + " · transaction " + operation);
        backend.present(pkg, component, target, taskHint, operation, result -> {
            if (!finishOperation(operation)) return;
            logCapabilityEvidence(result);
            retainObservedTask(result, pkg);
            if (acceptWindowedResult(result, pkg, target, taskHint)) {
                markWindowed(result, pkg, target);
            } else if (taskHint > 0 && "TASK_NOT_FOUND".equals(result.code)) {
                activeTaskId = -1;
                latchFailure(pkg, configuredMode, "Navigation app closed · use Retry");
            } else {
                latchFailure(pkg, configuredMode, result.code);
            }
            drainPendingWork();
        });
    }

    private void beginReacquisitionGeneration() {
        authorityGeneration++;
        acquisitionAttemptGeneration = -1;
        clearFailureLatch();
        needsValidation = true;
    }

    private void transitionManagedTask(String nextMode, String nextPackage) {
        if (activeOperationId != 0) {
            pendingReconcile = true;
            return;
        }
        ensureNativeBackend();
        if (backend == null) {
            adoptAuthority(nextMode, nextPackage);
            latchFailure(nextPackage, nextMode, "Previous navigation backend unavailable");
            return;
        }
        final String managedPackage = activePackage;
        final int managedTask = activeTaskId;
        int operation = beginOperation();
        if (operation == 0) return;
        state = State.SUSPENDING;
        panel.showStarting(label(managedPackage), "Leaving native navigation window");
        backend.suspend(managedPackage, managedTask, activity.getPackageName(), activity.getTaskId(),
                result -> {
                    if (!finishOperation(operation)) return;
                    boolean missing = "TASK_NOT_FOUND".equals(result.code);
                    if (!missing && !(acceptIdentity(result, managedPackage, managedTask)
                            && result.windowingMode == 5)) {
                        adoptAuthority(nextMode, nextPackage);
                        latchFailure(nextPackage, nextMode,
                                "Could not leave previous navigation task · " + result.code);
                        drainPendingWork();
                        return;
                    }
                    if (missing || !managedPackage.equals(nextPackage)) {
                        activeTaskId = -1;
                        activePackage = nextPackage;
                    } else {
                        activeTaskId = result.taskId;
                    }
                    adoptAuthority(nextMode, nextPackage);
                    state = State.SUSPENDED;
                    reconcile(true);
                    drainPendingWork();
                });
    }

    private void suspendForLauncherSurface(String reason) {
        if (state == State.DESTROYED) return;
        if (activeOperationId != 0) {
            pendingSuspendReason = reason;
            return;
        }
        if (!hasManagedNativeTask()) {
            state = State.SUSPENDED;
            Log.i(TAG, "suspend " + reason + " without managed task");
            overlayGate.onSettled();
            return;
        }
        ensureNativeBackend();
        if (backend == null) return;
        final String pkg = activePackage;
        final int task = activeTaskId;
        int operation = beginOperation();
        if (operation == 0) return;
        state = State.SUSPENDING;
        backend.suspend(pkg, task, activity.getPackageName(), activity.getTaskId(), result -> {
            if (!finishOperation(operation)) return;
            if (!result.success) Log.w(TAG, "suspend helper failure: " + result.raw);
            if ("TASK_NOT_FOUND".equals(result.code)) {
                activeTaskId = -1;
            } else if (acceptIdentity(result, pkg, task) && result.windowingMode == 5) {
                activeTaskId = result.taskId;
            } else {
                latchFailure(pkg, configuredMode, "Could not suspend navigation · " + result.code);
            }
            state = State.SUSPENDED;
            needsValidation = true;
            Log.i(TAG, "suspended navigation reason=" + reason + " task=" + task);
            pendingSuspendReason = "";
            overlayGate.onSettled();
            drainPendingWork();
        });
    }

    private boolean openFullscreenNow(String pkg, Location location) {
        if (backend == null || activeTaskId <= 0 || !pkg.equals(activePackage)) {
            return NavigationProvider.open(activity, pkg, location);
        }
        final int task = activeTaskId;
        int operation = beginOperation();
        if (operation == 0) return true;
        state = State.FULLSCREEN_HANDOFF;
        panel.showStarting(label(pkg), "Opening fullscreen · same task " + task);
        backend.fullscreen(pkg, task, result -> {
            if (!finishOperation(operation)) return;
            retainObservedTask(result, pkg);
            if (acceptIdentity(result, pkg, task) && result.windowingMode == 1) {
                activeTaskId = result.taskId;
                needsValidation = true;
                Log.i(TAG, "fullscreen task=" + task + " package=" + pkg
                        + " verifiedBounds=" + result.bounds);
                if (location != null && !NavigationProvider.open(activity, pkg, location)) {
                    Log.w(TAG, "location handoff failed after fullscreen transition package=" + pkg);
                }
            } else {
                Log.w(TAG, "fullscreen helper failed code=" + result.code + " raw=" + result.raw);
                if ("TASK_NOT_FOUND".equals(result.code)) activeTaskId = -1;
                latchFailure(pkg, configuredMode,
                        "Fullscreen unavailable · " + (result.code == null ? "UNKNOWN" : result.code));
            }
            drainPendingWork();
        });
        return true;
    }

    private int beginOperation() {
        if (activeOperationId != 0 || state == State.DESTROYED) {
            pendingReconcile = true;
            return 0;
        }
        activeOperationId = ++nextTransactionId;
        return activeOperationId;
    }

    private boolean finishOperation(int operation) {
        if (state == State.DESTROYED || operation != activeOperationId) return false;
        activeOperationId = 0;
        return true;
    }

    private void drainPendingWork() {
        if (state == State.DESTROYED || activeOperationId != 0) return;
        if (fullscreenRequested) {
            Location location = pendingFullscreenLocation;
            fullscreenRequested = false;
            pendingFullscreenLocation = null;
            openFullscreen(location);
            return;
        }
        if (!pendingSuspendReason.isEmpty()) {
            String reason = pendingSuspendReason;
            pendingSuspendReason = "";
            suspendForLauncherSurface(reason);
            return;
        }
        if (pendingReconcile) {
            pendingReconcile = false;
            reconcile(false);
        }
    }

    private NavigationWindowBounds currentTarget(NavigationWindowBounds fallback) {
        return bounds == null ? fallback : bounds;
    }

    private boolean acceptWindowedResult(NavigationHelperResult result, String pkg,
            NavigationWindowBounds target, int expectedTask) {
        return acceptIdentity(result, pkg, expectedTask)
                && result.displayId == 0
                && result.windowingMode == 5
                && target.toString().equals(result.bounds);
    }

    private static boolean acceptIdentity(NavigationHelperResult result, String pkg,
            int expectedTask) {
        if (!result.success || result.userId != AndroidUserId.current()
                || result.taskId <= 0 || !pkg.equals(result.packageName)) return false;
        if (expectedTask > 0 && result.taskId != expectedTask) return false;
        return componentMatches(result.component, pkg);
    }

    private static boolean componentMatches(String component, String pkg) {
        return component != null && !component.isEmpty() && !"unknown".equals(component)
                && component.startsWith(pkg + "/");
    }

    private void retainObservedTask(NavigationHelperResult result, String pkg) {
        if (result.userId == AndroidUserId.current() && result.taskId > 0
                && pkg.equals(result.packageName) && componentMatches(result.component, pkg)) {
            activePackage = pkg;
            activeTaskId = result.taskId;
        }
    }

    private void markWindowed(NavigationHelperResult result, String pkg,
            NavigationWindowBounds target) {
        activePackage = pkg;
        activeTaskId = result.taskId;
        backendMode = HomeNavigationSurfacePolicy.NATIVE_WINDOW;
        state = State.WINDOWED;
        appliedBounds = target;
        needsValidation = false;
        clearFailureLatch();
        if (uiState.canPresentNavigation()) showWindowedStatus(pkg, result.taskId, target);
        Log.i(TAG, "windowed package=" + pkg + " task=" + result.taskId + " stack="
                + result.stackId + " mode=" + result.windowingMode + " bounds=" + result.bounds
                + " launched=" + result.launched + " transaction=" + result.transactionId);
    }

    private static void logCapabilityEvidence(NavigationHelperResult result) {
        if (!result.success) Log.w(TAG, "helper failure: " + result.raw);
        if (result.helpExit < 0 && result.launchExit < 0) return;
        Log.i(TAG, "native launch evidence code=" + result.code + " helpExit="
                + result.helpExit + " helpWindowingMode=" + result.helpWindowingMode
                + " helpDisplay=" + result.helpDisplay + " launchExit=" + result.launchExit);
    }

    private void showWindowedStatus(String pkg, int taskId, NavigationWindowBounds target) {
        panel.showConfigured("", () -> openFullscreen(null));
    }

    private void latchFailure(String pkg, String mode, String detail) {
        failureGeneration = authorityGeneration;
        failurePackage = pkg == null ? "" : pkg;
        failureMode = mode == null ? "" : mode;
        failureDetail = detail == null || detail.isEmpty() ? "UNKNOWN" : detail;
        state = State.FAILED;
        showLatchedFailure();
        Log.w(TAG, "native navigation failed package=" + failurePackage + " detail="
                + failureDetail + " generation=" + failureGeneration);
    }

    private boolean isFailureLatched() {
        return failureGeneration == authorityGeneration
                && failurePackage.equals(configuredPackage)
                && failureMode.equals(configuredMode);
    }

    private void showLatchedFailure() {
        if (!uiState.canPresentNavigation()) return;
        panel.showFailure("Navigation unavailable", this::retry, () -> openFullscreen(null));
    }

    private void clearFailureLatch() {
        failureGeneration = -1;
        failurePackage = "";
        failureMode = "";
        failureDetail = "";
    }

    private void retry() {
        if (state == State.DESTROYED || activeOperationId != 0) return;
        authorityGeneration++;
        acquisitionAttemptGeneration = -1;
        clearFailureLatch();
        needsValidation = true;
        state = State.IDLE;
        reconcile(true);
    }

    private void ensureNativeBackend() {
        if (backend != null && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(backendMode)) return;
        destroyBackendInstance();
        backendMode = HomeNavigationSurfacePolicy.NATIVE_WINDOW;
        backend = new RawFreeformTaskBackend(activity);
    }

    private void destroyBackendInstance() {
        if (backend != null) backend.destroy();
        backend = null;
    }

    private boolean hasManagedNativeTask() {
        return activeTaskId > 0 && !activePackage.isEmpty()
                && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(backendMode);
    }

    private String resolveLaunchComponent(String pkg) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return "";
        PackageManager packages = activity.getPackageManager();
        ComponentName component = launch.getComponent();
        ActivityInfo info = launch.resolveActivityInfo(packages, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null && component != null) {
            try {
                info = packages.getActivityInfo(component, 0);
            } catch (PackageManager.NameNotFoundException ignored) {
                return "";
            }
        }
        if (info == null || !info.exported || !pkg.equals(info.packageName)) return "";
        if (component == null) component = new ComponentName(info.packageName, info.name);
        if (!pkg.equals(component.getPackageName())) return "";
        return component.flattenToString();
    }

    private String selectedPackage() {
        String configured = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_NAV);
        if (!configured.isEmpty()) return configured;
        return NavigationProvider.hasLauncherActivity(activity, NavigationProvider.ORGANIC_MAPS_INCAR)
                ? NavigationProvider.ORGANIC_MAPS_INCAR : "";
    }

    private String label(String pkg) {
        return pkg == null || pkg.isEmpty() ? "Navigation"
                : AppResolver.labelFor(activity, pkg, "Navigation");
    }
}
