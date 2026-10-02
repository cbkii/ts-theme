package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared bounded executor for one root-backed navigation experiment. */
abstract class RootNavigationBackend implements NavigationSurfaceBackend {
    private static final long KNOWN_TASK_RECHECK_DELAY_MS = 180L;
    private static final long CORROBORATION_TIMEOUT_MS = 1200L;

    private enum AbsenceEvidence { PRESENT_IN_RECENTS, PROCESS_ALIVE, ABSENT, UNKNOWN }

    private final Context context;
    private final NavigationRootHelper helper;
    private final ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed;
    private NavigationHelperResult lastKnownTaskObservation;

    RootNavigationBackend(Context context, String threadName) {
        this.context = context.getApplicationContext();
        helper = new NavigationRootHelper(context);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override public void present(String packageName, String launchComponent,
            NavigationWindowBounds bounds, int taskId, int transactionId, Callback callback) {
        submit(() -> {
            ensureNavigationPermissions(packageName);
            if (taskId > 0) {
                NavigationHelperResult verified = retryKnownTaskMiss(packageName, taskId,
                        "pre-present verify", () -> helper.run("verify-native", packageName,
                                Integer.toString(bounds.left), Integer.toString(bounds.top),
                                Integer.toString(bounds.right), Integer.toString(bounds.bottom),
                                Integer.toString(taskId)));
                if ("TASK_OBSERVATION_UNCERTAIN".equals(verified.code)) {
                    android.util.Log.w("TS18Nav", "known task observation uncertain before repair; "
                            + "retaining authority without present-native task=" + taskId);
                    return verified;
                }
                if ("TASK_NOT_FOUND".equals(verified.code)
                        || "TASK_AMBIGUOUS".equals(verified.code)
                        || "COMPONENT_MISMATCH".equals(verified.code)) return verified;
                android.util.Log.i("TS18Nav", "verified mismatch before repair task=" + taskId
                        + " code=" + verified.code);
            }
            NavigationHelperResult result = helper.run("present-native", packageName, launchComponent,
                    Integer.toString(bounds.left), Integer.toString(bounds.top),
                    Integer.toString(bounds.right), Integer.toString(bounds.bottom), taskHint(taskId),
                    Integer.toString(transactionId));
            rememberKnownTask(result, packageName, result.taskId);
            return result;
        }, callback);
    }

    @Override public void verify(String packageName, NavigationWindowBounds bounds,
            int taskId, Callback callback) {
        submit(() -> {
            ensureNavigationPermissions(packageName);
            return retryKnownTaskMiss(packageName, taskId, "verify", () ->
                    helper.run("verify-native", packageName,
                            Integer.toString(bounds.left), Integer.toString(bounds.top),
                            Integer.toString(bounds.right), Integer.toString(bounds.bottom),
                            taskHint(taskId)));
        }, callback);
    }

    @Override public void resume(String packageName, NavigationWindowBounds bounds,
            int taskId, String homePackage, int homeTaskId, Callback callback) {
        if (taskId <= 0 || homeTaskId <= 0) {
            if (callback != null) callback.onResult(
                    NavigationHelperResult.failure("TASK_AUTHORITY_REQUIRED", ""));
            return;
        }
        submit(() -> retryKnownTaskMiss(packageName, taskId, "resume", () ->
                helper.run("resume-windowed", packageName,
                        Integer.toString(bounds.left), Integer.toString(bounds.top),
                        Integer.toString(bounds.right), Integer.toString(bounds.bottom),
                        Integer.toString(taskId), homePackage, Integer.toString(homeTaskId))), callback);
    }

    @Override public void status(String packageName, int taskId, Callback callback) {
        submit(() -> retryKnownTaskMiss(packageName, taskId, "status",
                () -> helper.run("status", packageName, taskHint(taskId))), callback);
    }

    @Override public void fullscreen(String packageName, int taskId, Callback callback) {
        if (taskId <= 0) {
            if (callback != null) callback.onResult(
                    NavigationHelperResult.failure("TASK_AUTHORITY_REQUIRED", ""));
            return;
        }
        submit(() -> {
            ensureNavigationPermissions(packageName);
            NavigationHelperResult transitioned =
                    helper.run("fullscreen", packageName, Integer.toString(taskId));
            if (!transitioned.success || transitioned.taskId != taskId
                    || transitioned.windowingMode != 1) return transitioned;
            rememberKnownTask(transitioned, packageName, taskId);
            android.util.Log.i("TS18Nav", "fullscreen mode verified task=" + taskId
                    + " bounds=" + transitioned.bounds + " (WindowManager-owned)");
            return transitioned;
        }, callback);
    }

    @Override public void suspend(String packageName, int taskId, String homePackage,
            int homeTaskId, Callback callback) {
        if (taskId <= 0 || homeTaskId <= 0) {
            if (callback != null) callback.onResult(
                    NavigationHelperResult.failure("TASK_AUTHORITY_REQUIRED", ""));
            return;
        }
        submit(() -> {
            NavigationHelperResult result =
                    helper.parkWindowedTask(packageName, taskId, homePackage, homeTaskId);
            rememberKnownTask(result, packageName, taskId);
            return result;
        }, callback);
    }

    @Override public void destroy() {
        destroyed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        lastKnownTaskObservation = null;
    }

    private void ensureNavigationPermissions(String packageName) {
        NavigationPermissionBootstrapper.Result result =
                NavigationPermissionBootstrapper.ensureNow(context, packageName);
        if (!result.success && result.attempted) {
            android.util.Log.w("TS18NavPerm",
                    "permission mitigation failed open package=" + packageName
                            + " detail=" + result.detail);
        }
    }

    /**
     * A second miss from the same dumpsys/activity parser is not independent evidence of task
     * removal. After the short parser recheck, corroborate through ActivityManager recents plus
     * process existence. Only a clean recents miss with no package process proves absence. When
     * contrary/ambiguous evidence remains, retain the last successfully observed task instead of
     * launching or repairing it.
     */
    private NavigationHelperResult retryKnownTaskMiss(
            String packageName, int taskId, String phase, Operation operation) {
        NavigationHelperResult first = operation.run();
        if (taskId <= 0 || !"TASK_NOT_FOUND".equals(first.code) || destroyed) {
            rememberKnownTask(first, packageName, taskId);
            return first;
        }
        android.util.Log.w("TS18Nav", "transient TASK_NOT_FOUND phase=" + phase
                + " package=" + packageName + " task=" + taskId + " · rechecking parser once");
        try {
            Thread.sleep(KNOWN_TASK_RECHECK_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return first;
        }
        if (destroyed) return first;
        NavigationHelperResult second = operation.run();
        if (!"TASK_NOT_FOUND".equals(second.code)) {
            rememberKnownTask(second, packageName, taskId);
            android.util.Log.i("TS18Nav", "known-task recheck phase=" + phase
                    + " package=" + packageName + " task=" + taskId + " code=" + second.code);
            return second;
        }

        AbsenceEvidence evidence = corroborateKnownTask(packageName, taskId);
        android.util.Log.i("TS18Nav", "known-task corroboration phase=" + phase
                + " package=" + packageName + " task=" + taskId + " evidence=" + evidence);
        if (evidence == AbsenceEvidence.ABSENT) return second;

        String detail = "same-parser miss; independent evidence=" + evidence;
        if ("resume".equals(phase)) {
            // Resume already has a non-mutating foreground-changed path in the controller.
            return NavigationHelperResult.failure("FOREGROUND_CHANGED", detail);
        }
        NavigationHelperResult cached = cachedKnownTask(packageName, taskId);
        if (cached != null) {
            return NavigationHelperResult.withCode(
                    cached, "TASK_OBSERVATION_UNCERTAIN", detail);
        }
        return NavigationHelperResult.failure("TASK_OBSERVATION_UNCERTAIN", detail);
    }

    private AbsenceEvidence corroborateKnownTask(String packageName, int taskId) {
        if (!safePackage(packageName) || taskId <= 0) return AbsenceEvidence.UNKNOWN;
        String command = "snapshot=\"$(dumpsys activity recents 2>/dev/null)\" || exit 3; "
                + "printf '%s\\n' \"$snapshot\" | grep -F '#" + taskId + " A="
                + packageName + " ' >/dev/null && exit 0; "
                + "pidof " + packageName + " >/dev/null 2>&1 && exit 2; exit 1";
        RootShell.Result result = RootShell.runMillis(command, CORROBORATION_TIMEOUT_MS);
        if (!result.completed) return AbsenceEvidence.UNKNOWN;
        if (result.exitCode == 0) return AbsenceEvidence.PRESENT_IN_RECENTS;
        if (result.exitCode == 1) return AbsenceEvidence.ABSENT;
        if (result.exitCode == 2) return AbsenceEvidence.PROCESS_ALIVE;
        return AbsenceEvidence.UNKNOWN;
    }

    private static boolean safePackage(String packageName) {
        return packageName != null && packageName.matches("[A-Za-z0-9._]+")
                && packageName.indexOf('.') > 0;
    }

    private void rememberKnownTask(NavigationHelperResult result, String packageName, int taskId) {
        if (result == null || !result.success || result.taskId <= 0
                || !packageName.equals(result.packageName)) return;
        if (taskId > 0 && result.taskId != taskId) return;
        lastKnownTaskObservation = result;
    }

    private NavigationHelperResult cachedKnownTask(String packageName, int taskId) {
        NavigationHelperResult cached = lastKnownTaskObservation;
        if (cached == null || !cached.success || cached.taskId != taskId
                || !packageName.equals(cached.packageName)) return null;
        return cached;
    }

    private static String taskHint(int taskId) {
        return Integer.toString(taskId > 0 ? taskId : 0);
    }

    private void submit(Operation operation, Callback callback) {
        if (destroyed) {
            if (callback != null) callback.onResult(
                    NavigationHelperResult.failure("DESTROYED", ""));
            return;
        }
        executor.execute(() -> {
            NavigationHelperResult result;
            try {
                result = operation.run();
            } catch (RuntimeException e) {
                result = NavigationHelperResult.failure("BACKEND_EXCEPTION",
                        e.getClass().getSimpleName());
            }
            NavigationHelperResult delivered = result;
            if (destroyed) return;
            main.post(() -> {
                if (!destroyed && callback != null) callback.onResult(delivered);
            });
        });
    }

    private interface Operation { NavigationHelperResult run(); }
}
