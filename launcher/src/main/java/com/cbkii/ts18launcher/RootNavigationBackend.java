package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared bounded executor for one root-backed navigation experiment. */
abstract class RootNavigationBackend implements NavigationSurfaceBackend {
    private final Context context;
    private final NavigationRootHelper helper;
    private final ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed;

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
                NavigationHelperResult verified = helper.run("verify-native", packageName,
                        Integer.toString(bounds.left), Integer.toString(bounds.top),
                        Integer.toString(bounds.right), Integer.toString(bounds.bottom),
                        Integer.toString(taskId));
                // Geometry alone does not establish presentation after parking.
                if ("TASK_NOT_FOUND".equals(verified.code)
                        || "TASK_AMBIGUOUS".equals(verified.code)
                        || "COMPONENT_MISMATCH".equals(verified.code)) return verified;
                android.util.Log.i("TS18Nav", "verified mismatch before repair task=" + taskId
                        + " code=" + verified.code);
            }
            return helper.run("present-native", packageName, launchComponent,
                    Integer.toString(bounds.left), Integer.toString(bounds.top),
                    Integer.toString(bounds.right), Integer.toString(bounds.bottom), taskHint(taskId),
                    Integer.toString(transactionId));
        }, callback);
    }

    @Override public void verify(String packageName, NavigationWindowBounds bounds,
            int taskId, Callback callback) {
        submit(() -> {
            ensureNavigationPermissions(packageName);
            return helper.run("verify-native", packageName,
                    Integer.toString(bounds.left), Integer.toString(bounds.top),
                    Integer.toString(bounds.right), Integer.toString(bounds.bottom), taskHint(taskId));
        }, callback);
    }

    @Override public void resume(String packageName, NavigationWindowBounds bounds,
            int taskId, String homePackage, int homeTaskId, Callback callback) {
        if (taskId <= 0 || homeTaskId <= 0) {
            if (callback != null) callback.onResult(
                    NavigationHelperResult.failure("TASK_AUTHORITY_REQUIRED", ""));
            return;
        }
        submit(() -> helper.run("resume-windowed", packageName,
                Integer.toString(bounds.left), Integer.toString(bounds.top),
                Integer.toString(bounds.right), Integer.toString(bounds.bottom),
                Integer.toString(taskId), homePackage, Integer.toString(homeTaskId)), callback);
    }

    @Override public void status(String packageName, int taskId, Callback callback) {
        submit(() -> helper.run("status", packageName, taskHint(taskId)), callback);
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
            // Do not follow a successful fullscreen transition with non-null task bounds. On
            // Android Q/AOSP, applying explicit bounds to a non-freeform task is itself a route
            // back into freeform. Let WindowManager own fullscreen geometry/insets and leave
            // physical visibility/touch as an exact-device qualification boundary.
            if (!transitioned.success || transitioned.taskId != taskId
                    || transitioned.windowingMode != 1) return transitioned;
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
        submit(() -> helper.parkWindowedTask(packageName, taskId, homePackage, homeTaskId), callback);
    }

    @Override public void destroy() {
        destroyed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
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
