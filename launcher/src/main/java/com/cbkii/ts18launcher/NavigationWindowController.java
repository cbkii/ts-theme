package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

/** Coordinates launcher-owned geometry with one authoritative external navigation task. */
final class NavigationWindowController {
    private static final String TAG = "TS18Nav";

    private enum State { IDLE, ACQUIRING, PRESENTING, WINDOWED, SUSPENDING, SUSPENDED, FULLSCREEN_HANDOFF, FAILED, DESTROYED }

    private final Activity activity;
    private final NativeNavigationPanel panel;
    private final NavigationWindowUiState uiState = new NavigationWindowUiState();
    private final NavigationOverlayGate overlayGate = new NavigationOverlayGate();
    private NavigationSurfaceBackend backend;
    private String backendMode = "";
    private State state = State.IDLE;
    private boolean needsValidation = true;
    private boolean needsPresentation = true;
    private boolean pendingReconcile;
    private boolean pendingDeparture;
    private boolean departureExternalOnly;
    private String departurePackage = "";
    private int departureTaskId = -1;
    private int focusGeneration;
    private String pendingSuspendReason = "";
    private boolean fullscreenRequested;
    private boolean fullscreenGoal;
    private boolean retryRequested;
    private boolean observationOnly;
    private String repairedObservation = "";
    private boolean homeStopped = true;
    private Runnable operationTimeout;
    private int externalLaunchGeneration;
    private String testingProfile = "";
    private Location pendingFullscreenLocation;
    private int authorityGeneration;
    private int nextTransactionId;
    private int activeOperationId;
    private final NavigationRecoveryPolicy recovery = new NavigationRecoveryPolicy();
    private Runnable recoveryCallback;
    private String lastHelperCode = "";
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
    private java.util.function.Consumer<Boolean> pendingPresentationCallback;

    NavigationWindowController(Activity activity, NativeNavigationPanel panel) {
        this.activity = activity; this.panel = panel;
        panel.setBoundsListener(this::onBoundsChanged);
        panel.setAction("Open navigation", () -> openNormally(null));
    }

    void onHomeVisible() {
        if (state == State.DESTROYED) return;
        if (overlayGate.isPending()) { uiState.onHomeVisibleWithOverlay(); overlayGate.onVisible(); return; }
        overlayGate.onVisible();
        uiState.onHomeVisible();
        if (homeStopped && !fullscreenGoal) { recovery.reset(); clearFailureLatch(); observationOnly = false; repairedObservation = ""; }
        homeStopped = false;
        String profile = TestingProfiles.navigation(activity) + "/" + TestingProfiles.transition(activity);
        if (!profile.equals(testingProfile)) {
            testingProfile = profile; cancelActiveOperation(); beginReacquisitionGeneration();
        }
        pendingSuspendReason = "";
        focusGeneration++; pendingDeparture = false;
        if (state == State.FULLSCREEN_HANDOFF) { state = State.IDLE; needsValidation = true; }
        NavigationWindowBounds measured = panel.currentBounds(); if (measured != null) bounds = measured;
        reconcile(false);
    }

    void onHomeStopped() {
        if (state == State.DESTROYED) return;
        homeStopped = true; externalLaunchGeneration++;
        if (overlayGate.isPending()) MediaEventTrace.record("external-launch", "cancelled-home-stopped");
        cancelRecovery(); uiState.onHomeStopped(); overlayGate.onStopped(); needsValidation = true; needsPresentation = true;
        if (activeOperationId != 0) Log.i(TAG, "HOME stopped during bounded transaction; transaction retained id=" + activeOperationId);
        else Log.i(TAG, "HOME stopped; task authority retained without relaunch");
        if (state != State.FULLSCREEN_HANDOFF && !fullscreenGoal) { recovery.reset(); requestDeparture(false); }
    }

    /** Focus loss can be our healthy native map, or a direct SystemUI/Recents handoff. */
    void onHomeFocusLost() {
        final int generation = ++focusGeneration;
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (state == State.DESTROYED || generation != focusGeneration
                    || activity.hasWindowFocus() || state == State.FULLSCREEN_HANDOFF) return;
            requestDeparture(true);
        }, 300L);
    }

    private void requestDeparture(boolean externalOnly) {
        if (state == State.DESTROYED || !hasManagedNativeTask() || TestingProfiles.retainCompact(activity)) return;
        // onStop has stronger lifecycle evidence than a focus-only check.
        boolean sameRequest = pendingDeparture && activePackage.equals(departurePackage)
                && activeTaskId == departureTaskId;
        departureExternalOnly = sameRequest ? departureExternalOnly && externalOnly : externalOnly;
        departurePackage = activePackage;
        departureTaskId = activeTaskId;
        pendingDeparture = true;
        drainPendingWork();
    }

    private void restoreBackgroundFullscreen() {
        final String pkg = departurePackage;
        final int task = departureTaskId;
        final boolean externalOnly = departureExternalOnly;
        pendingDeparture = false;
        if (!hasManagedNativeTask() || !pkg.equals(activePackage) || task != activeTaskId) {
            Log.i(TAG, "Discarded HOME departure after task authority changed");
            drainPendingWork();
            return;
        }
        int operation = beginOperation();
        if (operation == 0) { pendingDeparture = true; return; }
        backend.backgroundFullscreen(pkg, task, activity.getPackageName(), activity.getTaskId(),
                externalOnly, result -> {
            if (!finishOperation(operation)) return;
            if (acceptIdentity(result, pkg, task) && result.windowingMode == 1) {
                needsValidation = true; needsPresentation = true; state = State.SUSPENDED;
                Log.i(TAG, "HOME departure fullscreen task=" + task + " toTop=false");
            } else if (!"FOREGROUND_CHANGED".equals(result.code)) {
                // Preserve task authority on unsupported/uncertain observations. No focus fallback.
                Log.w(TAG, "HOME departure retained task=" + task + " code=" + result.code
                        + " raw=" + result.raw);
            }
            drainPendingWork();
        });
    }

    void onLauncherOverlayOpened() {
        if (state == State.DESTROYED || !uiState.onLauncherOverlayOpened()) return;
        suspendForLauncherSurface("launcher overlay");
    }

    /** Show the drawer first; skip a redundant root focus when HOME already owns window focus. */
    void openLauncherOverlay(Runnable show) {
        if (state == State.DESTROYED || !uiState.onLauncherOverlayOpened()) return;
        overlayGate.cancel(); if (show != null) show.run();
        if (activity.hasWindowFocus()) {
            pendingSuspendReason = ""; state = State.SUSPENDED; needsValidation = true; needsPresentation = true;
            Log.i(TAG, "launcher overlay visible with HOME already focused; root parking skipped");
            MediaEventTrace.record("drawer", "home-focus-already-owned"); return;
        }
        Log.i(TAG, "launcher overlay visible; guarded navigation parking required");
        suspendForLauncherSurface("launcher overlay");
    }

    void cancelLauncherOverlay() { overlayGate.cancel(); pendingSuspendReason = ""; }

    void launchAfterSuspension(Runnable launch) {
        if (state == State.DESTROYED || launch == null) return;
        cancelRecovery(); cancelActiveOperation();
        fullscreenGoal = false; fullscreenRequested = false; retryRequested = false;
        pendingReconcile = false; pendingDeparture = false; pendingSuspendReason = "";
        final int generation = ++externalLaunchGeneration;
        MediaEventTrace.record("external-launch", "request", "action=" + generation);
        overlayGate.request(() -> {
            if (generation != externalLaunchGeneration) return;
            cancelActiveOperation(); pendingSuspendReason = "";
            state = State.FULLSCREEN_HANDOFF;
            MediaEventTrace.record("external-launch", "dispatched", "action=" + generation);
            launch.run();
        });
        uiState.onLauncherOverlayOpened();
        recovery.reset();
        suspendForLauncherSurface("external app launch");
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (generation == externalLaunchGeneration && overlayGate.isPending()) {
                cancelActiveOperation(); pendingSuspendReason = "";
                MediaEventTrace.record("external-launch", "park-timeout", "action=" + generation);
                overlayGate.onSettled();
            }
        }, 3500L);
    }

    /** Explicit independent escape route. Never used by automatic map recovery. */
    boolean openNormally(Location location) {
        String pkg = selectedPackage();
        if (pkg.isEmpty() || state == State.DESTROYED) return false;
        launchAfterSuspension(() -> openOrdinaryFullscreen(pkg, location, "explicit-normal-open"));
        return true;
    }

    void suspendForExperimentalMap() { if (state != State.DESTROYED) { uiState.onLauncherOverlayOpened(); suspendForLauncherSurface("legacy online map fallback active"); } }

    boolean openFullscreen(Location location) {
        if (state == State.DESTROYED) return false;
        if ("N0".equals(TestingProfiles.navigation(activity))) return openNormally(location);
        cancelRecovery(); overlayGate.cancel(); externalLaunchGeneration++;
        fullscreenGoal = true; retryRequested = false; observationOnly = false; repairedObservation = ""; pendingDeparture = false;
        pendingFullscreenLocation = location;
        pendingSuspendReason = ""; String pkg = selectedPackage();
        if (pkg.isEmpty()) { panel.showUnavailable("Choose a Navigation app in Settings", null); return false; }
        if (activeOperationId != 0) { fullscreenRequested = true; pendingFullscreenLocation = location; panel.showStarting(label(pkg), "Finishing the current navigation transaction"); return true; }
        recovery.reset(); clearFailureLatch();
        return openFullscreenNow(pkg, location);
    }

    void destroy() { cancelActiveOperation(); cancelRecovery(); overlayGate.cancel(); finishPresentationCallback(false); authorityGeneration++; activeOperationId = 0; state = State.DESTROYED; destroyBackendInstance(); }

    void whenHomePresented(java.util.function.Consumer<Boolean> callback) {
        if (callback == null) return;
        if (state == State.WINDOWED && !needsPresentation && uiState.canPresentNavigation()) {
            callback.accept(true); return;
        }
        if (state == State.DESTROYED) {
            callback.accept(false); return;
        }
        if (state == State.FAILED) {
            callback.accept(!hasManagedNativeTask()); return;
        }
        finishPresentationCallback(false);
        pendingPresentationCallback = callback;
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (pendingPresentationCallback == callback)
                finishPresentationCallback(!hasManagedNativeTask());
        }, 6000L);
    }

    private void finishPresentationCallback(boolean success) {
        java.util.function.Consumer<Boolean> callback = pendingPresentationCallback;
        pendingPresentationCallback = null;
        if (callback != null) callback.accept(success);
    }

    private void onBoundsChanged(NavigationWindowBounds next) {
        if (state == State.DESTROYED || next.equals(bounds)) return;
        bounds = next; needsValidation = true; needsPresentation = true;
        if (activeOperationId != 0) pendingReconcile = true; else if (uiState.canPresentNavigation()) reconcile(false);
    }

    private void reconcile(boolean force) {
        if (!uiState.canPresentNavigation() || state == State.DESTROYED || bounds == null || recoveryCallback != null) return;
        if (activeOperationId != 0) { pendingReconcile = true; return; }
        String desiredMode = HomeNavigationSurfacePolicy.mode(activity), desiredPackage = selectedPackage();
        if (!desiredMode.equals(configuredMode) || !desiredPackage.equals(configuredPackage)) {
            if (hasManagedNativeTask() && (!HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(desiredMode) || !desiredPackage.equals(activePackage))) { transitionManagedTask(desiredMode, desiredPackage); return; }
            adoptAuthority(desiredMode, desiredPackage);
        }
        if ("N0".equals(TestingProfiles.navigation(activity))) {
            panel.showFullscreenOnly("N0 · Normal opening", () -> openNormally(null)); return;
        }
        if (isFailureLatched()) { showLatchedFailure(); return; }
        if (observationOnly) { observeRecovery(); return; }
        if (fullscreenGoal) { openFullscreenNow(selectedPackage(), pendingFullscreenLocation); return; }
        if (HomeNavigationSurfacePolicy.LEAFLET.equals(configuredMode)) { state = State.SUSPENDED; return; }
        if (HomeNavigationSurfacePolicy.FULLSCREEN.equals(configuredMode)) { state = State.IDLE; panel.showFullscreenOnly(label(configuredPackage), () -> openFullscreen(null)); return; }
        if (!HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(configuredMode)) { latchFailure(configuredPackage, configuredMode, "Unsupported navigation surface mode"); return; }
        if (configuredPackage.isEmpty()) { latchFailure("", configuredMode, "Choose a Navigation app in Settings"); return; }
        if (isFailureLatched()) { showLatchedFailure(); return; }
        ensureNativeBackend(); if (backend == null) { latchFailure(configuredPackage, configuredMode, "Native backend unavailable"); return; }
        String component = resolveLaunchComponent(configuredPackage); if (component.isEmpty()) { latchFailure(configuredPackage, configuredMode, "No exported launcher Activity"); return; }
        NavigationWindowBounds target = bounds;
        if (activeTaskId > 0) {
            if (!force && !needsValidation && !needsPresentation && State.WINDOWED == state && target.equals(appliedBounds)) { showWindowedStatus(configuredPackage, activeTaskId, target); return; }
            // Known tasks are read-only verified before any repair transaction.
            startVerify(configuredPackage, component, target, activeTaskId);
        } else startPresent(configuredPackage, component, target, -1);
    }

    private void observeRecovery() {
        ensureNativeBackend();
        final String pkg = selectedPackage();
        int operation = beginOperation(); if (operation == 0) return;
        backend.status(pkg, activeTaskId, result -> {
            if (!finishOperation(operation)) return;
            retainObservedTask(result, pkg);
            if (!acceptIdentity(result, pkg, 0) || (NavigationProvider.ORGANIC_MAPS_INCAR.equals(pkg)
                    && !result.component.endsWith("/app.organicmaps.MwmActivity"))) {
                scheduleRecovery(pkg, result.success ? "BOOTSTRAP_PENDING" : result.code);
                drainPendingWork(); return;
            }
            String observed = result.taskId + "/" + result.component + "/" + result.windowingMode
                    + "/" + result.bounds;
            if (observed.equals(repairedObservation)) {
                if (fullscreenGoal) {
                    latchFailure(pkg, configuredMode, "UNCHANGED_AFTER_REPAIR · choose another testing method");
                    drainPendingWork(); return;
                }
                int verifyOperation = beginOperation(); if (verifyOperation == 0) return;
                NavigationWindowBounds target = bounds;
                backend.verify(pkg, target, result.taskId, verified -> {
                    if (!finishOperation(verifyOperation)) return;
                    if (acceptWindowedResult(verified, pkg, target, result.taskId) && verified.presentationConfirmed())
                        markWindowed(verified, pkg, target);
                    else scheduleRecovery(pkg, "PRESENTATION_UNCONFIRMED");
                    drainPendingWork();
                });
                return;
            }
            repairedObservation = observed;
            observationOnly = false;
            if (fullscreenGoal) openFullscreenNow(pkg, pendingFullscreenLocation);
            else reconcile(false);
        });
    }

    private void adoptAuthority(String mode, String packageName) {
        boolean packageChanged = !packageName.equals(configuredPackage);
        configuredMode = mode; configuredPackage = packageName; authorityGeneration++; recovery.reset(); observationOnly = false; repairedObservation = ""; cancelRecovery(); clearFailureLatch(); needsValidation = true; needsPresentation = true; appliedBounds = null;
        if (packageChanged) { activePackage = packageName; activeTaskId = -1; }
        if (!HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(mode) && activeTaskId <= 0) { backendMode = ""; destroyBackendInstance(); }
    }

    private void startVerify(String pkg, String component, NavigationWindowBounds target, int taskId) {
        int operation = beginOperation(); if (operation == 0) return; state = State.PRESENTING;
        panel.showStarting(label(pkg), "Verifying task " + taskId + " at HOME bounds");
        backend.verify(pkg, target, taskId, result -> {
            if (!finishOperation(operation)) return; retainObservedTask(result, pkg);
            if (deferUncertainTaskObservation(result, pkg, "verify")) { drainPendingWork(); return; }
            if (acceptWindowedResult(result, pkg, target, taskId)) {
                if (needsPresentation) startResume(pkg, component, target, taskId);
                else {
                    if (result.presentationConfirmed()) markWindowed(result, pkg, target);
                    else scheduleRecovery(pkg, "PRESENTATION_UNCONFIRMED");
                    drainPendingWork();
                }
                return;
            }
            if ("TASK_NOT_FOUND".equals(result.code)) { activeTaskId = -1; scheduleRecovery(pkg, "TASK_NOT_FOUND"); drainPendingWork(); return; }
            if ("TASK_AMBIGUOUS".equals(result.code) || "COMPONENT_MISMATCH".equals(result.code)) { latchFailure(pkg, configuredMode, result.code); drainPendingWork(); return; }
            startPresent(pkg, component, currentTarget(target), taskId);
        });
    }

    private void startResume(String pkg, String component, NavigationWindowBounds target, int taskId) {
        if (!uiState.canPresentNavigation()) { needsPresentation = true; drainPendingWork(); return; }
        int operation = beginOperation(); if (operation == 0) return;
        state = State.PRESENTING;
        backend.resume(pkg, target, taskId, activity.getPackageName(), activity.getTaskId(), result -> {
            if (!finishOperation(operation)) return;
            retainObservedTask(result, pkg);
            if ("RESUMED_NATIVE".equals(result.code)
                    && acceptWindowedResult(result, pkg, target, taskId)) {
                if (result.presentationConfirmed()) markWindowed(result, pkg, target);
                else scheduleRecovery(pkg, "PRESENTATION_UNCONFIRMED");
                drainPendingWork(); return;
            }
            if (deferUncertainTaskObservation(result, pkg, "resume")) { drainPendingWork(); return; }
            if ("FOREGROUND_CHANGED".equals(result.code)) {
                needsPresentation = true; scheduleRecovery(pkg, result.code); drainPendingWork(); return;
            }
            if ("TASK_NOT_FOUND".equals(result.code)) {
                activeTaskId = -1;
                scheduleRecovery(pkg, "TASK_NOT_FOUND");
                drainPendingWork(); return;
            }
            // One active repair transaction before exposing manual Retry.
            startPresent(pkg, component, currentTarget(target), taskId);
        });
    }

    private void startPresent(String pkg, String component, NavigationWindowBounds target, int taskHint) {
        if (!uiState.canPresentNavigation()) { needsValidation = true; drainPendingWork(); return; }
        boolean acquisition = taskHint <= 0;
        int operation = beginOperation(); if (operation == 0) return;
        state = acquisition ? State.ACQUIRING : State.PRESENTING;
        panel.showStarting(label(pkg), acquisition ? "Acquiring one configured task · transaction " + operation : "Repairing task " + taskHint + " · transaction " + operation);
        backend.present(pkg, component, target, taskHint, operation, result -> {
            if (!finishOperation(operation)) return; logCapabilityEvidence(result); retainObservedTask(result, pkg);
            if (deferUncertainTaskObservation(result, pkg, "pre-present")) { drainPendingWork(); return; }
            if ("FOREGROUND_CHANGED".equals(result.code)) {
                state = State.SUSPENDED; needsValidation = true; needsPresentation = true;
                scheduleRecovery(pkg, result.code); drainPendingWork(); return;
            }
            if (acceptWindowedResult(result, pkg, target, taskHint)) {
                if (result.presentationConfirmed()) markWindowed(result, pkg, target);
                else scheduleRecovery(pkg, "PRESENTATION_UNCONFIRMED");
            }
            else if (taskHint > 0 && "TASK_NOT_FOUND".equals(result.code)) { activeTaskId = -1; scheduleRecovery(pkg, "TASK_NOT_FOUND"); }
            else scheduleRecovery(pkg, result.code);
            drainPendingWork();
        });
    }

    private void beginReacquisitionGeneration() { authorityGeneration++; recovery.reset(); observationOnly = false; repairedObservation = ""; cancelRecovery(); clearFailureLatch(); needsValidation = true; needsPresentation = true; }

    private void transitionManagedTask(String nextMode, String nextPackage) {
        if (activeOperationId != 0) { pendingReconcile = true; return; }
        ensureNativeBackend(); if (backend == null) { adoptAuthority(nextMode, nextPackage); latchFailure(nextPackage, nextMode, "Previous navigation backend unavailable"); return; }
        final String managedPackage = activePackage; final int managedTask = activeTaskId; int operation = beginOperation(); if (operation == 0) return; state = State.SUSPENDING;
        panel.showStarting(label(managedPackage), "Leaving native navigation window");
        backend.suspend(managedPackage, managedTask, activity.getPackageName(), activity.getTaskId(), result -> {
            if (!finishOperation(operation)) return; boolean missing = "TASK_NOT_FOUND".equals(result.code);
            if (!missing && !(acceptIdentity(result, managedPackage, managedTask) && result.windowingMode == 5)) { adoptAuthority(nextMode, nextPackage); latchFailure(nextPackage, nextMode, "Could not leave previous navigation task · " + result.code); drainPendingWork(); return; }
            if (missing || !managedPackage.equals(nextPackage)) { activeTaskId = -1; activePackage = nextPackage; } else activeTaskId = result.taskId;
            adoptAuthority(nextMode, nextPackage); state = State.SUSPENDED; reconcile(true); drainPendingWork();
        });
    }

    private void suspendForLauncherSurface(String reason) {
        if (state == State.DESTROYED) return; if (activeOperationId != 0) { pendingSuspendReason = reason; return; }
        if (!hasManagedNativeTask()) { state = State.SUSPENDED; Log.i(TAG, "suspend " + reason + " without managed task"); overlayGate.onSettled(); return; }
        ensureNativeBackend(); if (backend == null) return; final String pkg = activePackage; final int task = activeTaskId; int operation = beginOperation(); if (operation == 0) return; state = State.SUSPENDING;
        backend.suspend(pkg, task, activity.getPackageName(), activity.getTaskId(), result -> {
            if (!finishOperation(operation)) return; if (!result.success) Log.w(TAG, "suspend helper failure: " + result.raw);
            if ("TASK_NOT_FOUND".equals(result.code)) activeTaskId = -1;
            else if (acceptIdentity(result, pkg, task) && result.windowingMode == 5) activeTaskId = result.taskId;
            else latchFailure(pkg, configuredMode, "Could not suspend navigation · " + result.code);
            state = State.SUSPENDED; needsValidation = true; needsPresentation = true; Log.i(TAG, "suspended navigation reason=" + reason + " task=" + task); pendingSuspendReason = ""; overlayGate.onSettled(); drainPendingWork();
        });
    }

    private boolean openFullscreenNow(String pkg, Location location) {
        if ("ROOT_UNAVAILABLE".equals(lastHelperCode) || "ROOT_REQUIRED".equals(lastHelperCode)
                || "INSTALL_TIMEOUT".equals(lastHelperCode))
            return openOrdinaryFullscreen(pkg, location, lastHelperCode);
        ensureNativeBackend();
        String component = resolveLaunchComponent(pkg);
        if (component.isEmpty()) { latchFailure(pkg, configuredMode, "No exported launcher Activity"); return false; }
        final int task = pkg.equals(activePackage) ? activeTaskId : -1;
        int operation = beginOperation(); if (operation == 0) return true;
        fullscreenGoal = true;
        pendingDeparture = false;
        focusGeneration++;
        state = State.FULLSCREEN_HANDOFF;
        panel.showStarting(label(pkg), "Resolving navigation task for fullscreen");
        backend.fullscreen(pkg, component, task, result -> {
            if (!finishOperation(operation)) return;
            retainObservedTask(result, pkg);
            if (acceptIdentity(result, pkg, 0) && result.displayId == 0 && result.windowingMode == 1) {
                state = State.FULLSCREEN_HANDOFF; fullscreenGoal = false; cancelRecovery(); recovery.reset(); observationOnly = false;
                activeTaskId = result.taskId; activePackage = pkg; lastHelperCode = "";
                needsValidation = true; needsPresentation = true; clearFailureLatch();
                Log.i(TAG, "fullscreen task=" + result.taskId + " package=" + pkg + " verifiedBounds=" + result.bounds);
                if (location != null && !NavigationProvider.open(activity, pkg, location))
                    Log.w(TAG, "location handoff failed after fullscreen transition package=" + pkg);
            } else {
                Log.w(TAG, "fullscreen helper failed code=" + result.code + " raw=" + result.raw);
                if (NavigationRecoveryPolicy.ordinaryFullscreenAllowed(result.code)) {
                    openOrdinaryFullscreen(pkg, location, result.code); return;
                }
                state = State.SUSPENDED;
                if (!deferUncertainTaskObservation(result, pkg, "fullscreen"))
                    latchFailure(pkg, configuredMode, "Fullscreen unavailable · " + result.code);
            }
            drainPendingWork();
        });
        return true;
    }

    private boolean deferUncertainTaskObservation(NavigationHelperResult result, String pkg, String phase) {
        if (result == null || !("TASK_OBSERVATION_UNCERTAIN".equals(result.code)
                || "BOOTSTRAP_PENDING".equals(result.code) || "LAUNCH_PENDING".equals(result.code)
                || "PHASE_TIMEOUT".equals(result.code) || "TASK_REPLACED".equals(result.code))) return false;
        needsValidation = true;
        needsPresentation = true;
        state = State.SUSPENDED;
        scheduleRecovery(pkg, result.code);
        finishPresentationCallback(false);
        Log.w(TAG, "navigation task observation uncertain phase=" + phase + " task=" + activeTaskId);
        return true;
    }

    private boolean openOrdinaryFullscreen(String pkg, Location location, String reason) {
        // Only this explicit-user action uses Android's ordinary launcher route. No task flags,
        // force-stop, synthetic input, automatic HOME replacement or permission bypass.
        cancelRecovery(); pendingReconcile = false; pendingDeparture = false;
        state = State.FULLSCREEN_HANDOFF; needsValidation = true; needsPresentation = true;
        boolean opened = NavigationProvider.open(activity, pkg, location);
        if (opened) fullscreenGoal = false;
        if (!opened) latchFailure(pkg, configuredMode, "Android launch unavailable");
        Log.i(TAG, "explicit ordinary fullscreen package=" + pkg + " reason=" + reason + " dispatched=" + opened);
        return opened;
    }

    private boolean scheduleRecovery(String pkg, String code) {
        lastHelperCode = code; observationOnly = true;
        needsValidation = true; needsPresentation = true;
        if (recoveryCallback != null) return true;
        long delay = recovery.nextDelay(code, SystemClock.elapsedRealtime());
        if (delay < 0L) { latchFailure(pkg, configuredMode, code); return false; }
        state = State.SUSPENDED;
        final int generation = authorityGeneration;
        Runnable followup = () -> {
            recoveryCallback = null;
            if (state == State.DESTROYED || generation != authorityGeneration
                    || !uiState.canPresentNavigation() || state == State.FULLSCREEN_HANDOFF) return;
            clearFailureLatch(); reconcile(false);
        };
        recoveryCallback = followup;
        activity.getWindow().getDecorView().postDelayed(followup, delay);
        if (uiState.canPresentNavigation())
            panel.showFailure("Recovering navigation · " + code + " · observation " + recovery.observations(),
                    this::retry, () -> openNormally(null));
        Log.i(TAG, "recovery code=" + code + " delayMs=" + delay + " task=" + activeTaskId
                + " generation=" + generation + " attempt=" + nextTransactionId);
        return true;
    }

    private void cancelRecovery() {
        if (recoveryCallback != null) activity.getWindow().getDecorView().removeCallbacks(recoveryCallback);
        recoveryCallback = null;
    }

    private int beginOperation() {
        if (activeOperationId != 0 || state == State.DESTROYED) { pendingReconcile = true; return 0; }
        long now = SystemClock.elapsedRealtime();
        long deadline = recovery.deadline(now);
        if (now >= deadline) { latchFailure(selectedPackage(), configuredMode, "ACTION_TIMEOUT"); return 0; }
        activeOperationId = ++nextTransactionId;
        final int operation = activeOperationId;
        if (backend != null) backend.setDeadline(deadline);
        operationTimeout = () -> {
            if (activeOperationId != operation || state == State.DESTROYED) return;
            cancelActiveOperation(); latchFailure(selectedPackage(), configuredMode, "ACTION_TIMEOUT");
            drainPendingWork();
        };
        activity.getWindow().getDecorView().postDelayed(operationTimeout, deadline - now);
        Log.i(TAG, "operation start transaction=" + operation + " method=" + testingProfile
                + " goal=" + (fullscreenGoal ? "fullscreen" : "compact") + " deadline=" + deadline + " task=" + activeTaskId);
        return operation;
    }
    private boolean finishOperation(int operation) {
        if (state == State.DESTROYED || operation != activeOperationId) return false;
        activeOperationId = 0;
        if (operationTimeout != null) activity.getWindow().getDecorView().removeCallbacks(operationTimeout);
        operationTimeout = null;
        if (recovery.expired(SystemClock.elapsedRealtime())) {
            latchFailure(selectedPackage(), configuredMode, "ACTION_TIMEOUT"); drainPendingWork(); return false;
        }
        // A queued explicit action supersedes this result, including a success from the old goal.
        if (retryRequested || fullscreenRequested) {
            drainPendingWork(); return false;
        }
        return true;
    }
    private void cancelActiveOperation() {
        activeOperationId = 0;
        if (operationTimeout != null) activity.getWindow().getDecorView().removeCallbacks(operationTimeout);
        operationTimeout = null;
        destroyBackendInstance();
    }
    private void drainPendingWork() {
        if (state == State.DESTROYED || activeOperationId != 0) return;
        if (retryRequested) { retryRequested = false; retry(); return; }
        if (fullscreenRequested) { Location location = pendingFullscreenLocation; fullscreenRequested = false; pendingFullscreenLocation = null; openFullscreen(location); return; }
        if (pendingDeparture) { restoreBackgroundFullscreen(); return; }
        if (!pendingSuspendReason.isEmpty()) { String reason = pendingSuspendReason; pendingSuspendReason = ""; suspendForLauncherSurface(reason); return; }
        if (pendingReconcile) { pendingReconcile = false; reconcile(false); }
    }
    private NavigationWindowBounds currentTarget(NavigationWindowBounds fallback) { return bounds == null ? fallback : bounds; }
    private boolean acceptWindowedResult(NavigationHelperResult result, String pkg, NavigationWindowBounds target, int expectedTask) { return acceptIdentity(result, pkg, expectedTask) && result.displayId == 0 && result.windowingMode == 5 && target.toString().equals(result.bounds); }
    private static boolean acceptIdentity(NavigationHelperResult result, String pkg, int expectedTask) { if (!result.success || result.userId != AndroidUserId.current() || result.taskId <= 0 || !pkg.equals(result.packageName)) return false; if (expectedTask > 0 && result.taskId != expectedTask) return false; return componentMatches(result.component, pkg); }
    private static boolean componentMatches(String component, String pkg) { return component != null && !component.isEmpty() && !"unknown".equals(component) && component.startsWith(pkg + "/"); }
    private void retainObservedTask(NavigationHelperResult result, String pkg) { if (result != null) Log.i(TAG, "observation attempt=" + nextTransactionId + " code=" + result.code + " phase=" + result.phase + " task=" + result.taskId + " mode=" + result.windowingMode + " visible=" + result.windowVisible + " drawn=" + result.windowDrawn); if (result == null || (!result.success && !NavigationRecoveryPolicy.recoverable(result.code))) return; if (result.userId == AndroidUserId.current() && result.taskId > 0 && pkg.equals(result.packageName) && componentMatches(result.component, pkg)) { activePackage = pkg; activeTaskId = result.taskId; } }
    private void markWindowed(NavigationHelperResult result, String pkg, NavigationWindowBounds target) { if (!target.equals(bounds)) { needsValidation = true; needsPresentation = true; pendingReconcile = true; return; } activePackage = pkg; activeTaskId = result.taskId; backendMode = HomeNavigationSurfacePolicy.NATIVE_WINDOW; state = State.WINDOWED; lastHelperCode = ""; observationOnly = false; repairedObservation = ""; cancelRecovery(); recovery.reset(); appliedBounds = target; needsValidation = false; needsPresentation = false; clearFailureLatch(); if (uiState.canPresentNavigation()) { showWindowedStatus(pkg, result.taskId, target); finishPresentationCallback(true); } Log.i(TAG, "windowed package=" + pkg + " task=" + result.taskId + " stack=" + result.stackId + " mode=" + result.windowingMode + " bounds=" + result.bounds + " launched=" + result.launched + " transaction=" + result.transactionId); }
    private static void logCapabilityEvidence(NavigationHelperResult result) { if (!result.success) Log.w(TAG, "helper failure: " + result.raw); if (result.helpExit < 0 && result.launchExit < 0) return; Log.i(TAG, "native launch evidence code=" + result.code + " helpExit=" + result.helpExit + " helpWindowingMode=" + result.helpWindowingMode + " helpDisplay=" + result.helpDisplay + " launchExit=" + result.launchExit); }
    private void showWindowedStatus(String pkg, int taskId, NavigationWindowBounds target) { // Physical visibility and touch are not inferred from Android task identity/bounds alone; exact-device qualification remains required.
        panel.showConfigured("", () -> openFullscreen(null)); }
    private void latchFailure(String pkg, String mode, String detail) { failureGeneration = authorityGeneration; failurePackage = pkg == null ? "" : pkg; failureMode = mode == null ? "" : mode; failureDetail = detail == null || detail.isEmpty() ? "UNKNOWN" : detail; state = State.FAILED; finishPresentationCallback(!hasManagedNativeTask()); showLatchedFailure(); Log.w(TAG, "native navigation failed package=" + failurePackage + " detail=" + failureDetail + " generation=" + failureGeneration); }
    private boolean isFailureLatched() { return failureGeneration == authorityGeneration && failurePackage.equals(configuredPackage) && failureMode.equals(configuredMode); }
    private void showLatchedFailure() { if (uiState.canPresentNavigation()) panel.showFailure("Navigation unavailable · " + failureDetail + " · observation " + recovery.observations(), this::retry, () -> openNormally(null)); }
    private void clearFailureLatch() { failureGeneration = -1; failurePackage = ""; failureMode = ""; failureDetail = ""; }
    private void retry() {
        if (state == State.DESTROYED) return;
        MediaEventTrace.record("navigation", "retry-request", "transaction=" + activeOperationId);
        if (activeOperationId != 0) {
            retryRequested = true;
            panel.showStarting(label(selectedPackage()), "Retry queued · finishing current operation"); return;
        }
        cancelRecovery(); beginReacquisitionGeneration(); state = State.IDLE; reconcile(true);
    }
    private void ensureNativeBackend() { if (backend != null && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(backendMode)) return; destroyBackendInstance(); backendMode = HomeNavigationSurfacePolicy.NATIVE_WINDOW; backend = new RawFreeformTaskBackend(activity); }
    private void destroyBackendInstance() { if (backend != null) backend.destroy(); backend = null; }
    private boolean hasManagedNativeTask() { return activeTaskId > 0 && !activePackage.isEmpty() && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(backendMode); }
    private String resolveLaunchComponent(String pkg) { Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg); if (launch == null) return ""; PackageManager packages = activity.getPackageManager(); ComponentName component = launch.getComponent(); ActivityInfo info = launch.resolveActivityInfo(packages, PackageManager.MATCH_DEFAULT_ONLY); if (info == null && component != null) try { info = packages.getActivityInfo(component, 0); } catch (PackageManager.NameNotFoundException ignored) { return ""; } if (info == null || !info.exported || !pkg.equals(info.packageName)) return ""; if (component == null) component = new ComponentName(info.packageName, info.name); return pkg.equals(component.getPackageName()) ? component.flattenToString() : ""; }
    private String selectedPackage() { String configured = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_NAV); if (!configured.isEmpty()) return configured; return NavigationProvider.hasLauncherActivity(activity, NavigationProvider.ORGANIC_MAPS_INCAR) ? NavigationProvider.ORGANIC_MAPS_INCAR : ""; }
    private String label(String pkg) { return pkg == null || pkg.isEmpty() ? "Navigation" : AppResolver.labelFor(activity, pkg, "Navigation"); }
}
