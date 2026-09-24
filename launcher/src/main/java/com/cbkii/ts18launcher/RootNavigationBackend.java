package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared bounded executor for one root-backed navigation experiment. */
abstract class RootNavigationBackend implements NavigationSurfaceBackend {
    private static final long FULLSCREEN_RESIZE_TIMEOUT_MS = 1400L;

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
            if (!transitioned.success || transitioned.windowingMode != 1) return transitioned;

            // Physical TS18 qualification proved that mode=1 alone can retain the old freeform
            // application geometry. Force one bounded standard Android task re-layout to the real
            // physical display, then verify the same task/package/user/display again. No Topway
            // property/file writer is involved, and park-windowed remains the reversible return.
            RootShell.Result resize = RootShell.runMillis(
                    fullscreenResizeCommand(taskId), FULLSCREEN_RESIZE_TIMEOUT_MS);
            if (!resize.success()) {
                android.util.Log.w("TS18Nav", "fullscreen physical resize rejected task=" + taskId
                        + " exit=" + resize.exitCode);
                return NavigationHelperResult.failure("FULLSCREEN_RESIZE_FAILED", resize.output);
            }
            String expectedBounds = parseFullBounds(resize.output);
            if (expectedBounds.isEmpty()) {
                return NavigationHelperResult.failure("FULLSCREEN_SIZE_UNREADABLE", resize.output);
            }

            NavigationHelperResult verified =
                    helper.run("status", packageName, Integer.toString(taskId));
            if (!verified.success || verified.taskId != taskId || verified.windowingMode != 1) {
                return NavigationHelperResult.failure("FULLSCREEN_VERIFY_FAILED", verified.raw);
            }
            if (!expectedBounds.equals(verified.bounds)
                    && !"0,0,0,0".equals(verified.bounds)) {
                android.util.Log.w("TS18Nav", "fullscreen stale bounds task=" + taskId
                        + " expected=" + expectedBounds + " observed=" + verified.bounds);
                return NavigationHelperResult.failure("FULLSCREEN_BOUNDS_STALE", verified.raw);
            }
            android.util.Log.i("TS18Nav", "fullscreen geometry verified task=" + taskId
                    + " bounds=" + verified.bounds + " physical=" + expectedBounds);
            return verified;
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

    static String fullscreenResizeCommand(int taskId) {
        return "PATH=/system/bin:/system/xbin:/vendor/bin; export PATH; "
                + "size=$(wm size 2>/dev/null | awk '/Physical size:/ {print $3; exit}'); "
                + "case \"$size\" in [0-9]*x[0-9]*) ;; *) exit 41;; esac; "
                + "w=${size%x*}; h=${size#*x}; "
                + "case \"$w:$h\" in *[!0-9:]*|:|*:) exit 42;; esac; "
                + "[ \"$w\" -gt 0 ] && [ \"$h\" -gt 0 ] || exit 43; "
                + "am task resize " + taskId + " 0 0 \"$w\" \"$h\" >/dev/null 2>&1 || exit 44; "
                + "printf 'FULL_BOUNDS=0,0,%s,%s\\n' \"$w\" \"$h\"";
    }

    static String parseFullBounds(String output) {
        if (output == null || output.isEmpty()) return "";
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("FULL_BOUNDS=")) continue;
            String bounds = trimmed.substring("FULL_BOUNDS=".length());
            return bounds.matches("[0-9]+,[0-9]+,[0-9]+,[0-9]+") ? bounds : "";
        }
        return "";
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
