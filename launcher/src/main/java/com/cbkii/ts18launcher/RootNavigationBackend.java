package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared bounded executor for one root-backed navigation experiment. */
abstract class RootNavigationBackend implements NavigationSurfaceBackend {
    private final NavigationRootHelper helper;
    private final ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed;

    RootNavigationBackend(Context context, String threadName) {
        helper = new NavigationRootHelper(context);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override public void present(String packageName, String launchComponent,
            NavigationWindowBounds bounds, int taskId, int transactionId, Callback callback) {
        submit(() -> helper.run("present-native", packageName, launchComponent,
                Integer.toString(bounds.left), Integer.toString(bounds.top),
                Integer.toString(bounds.right), Integer.toString(bounds.bottom), taskHint(taskId),
                Integer.toString(transactionId)), callback);
    }

    @Override public void verify(String packageName, NavigationWindowBounds bounds,
            int taskId, Callback callback) {
        submit(() -> helper.run("verify-native", packageName,
                Integer.toString(bounds.left), Integer.toString(bounds.top),
                Integer.toString(bounds.right), Integer.toString(bounds.bottom), taskHint(taskId)), callback);
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
        submit(() -> helper.run("fullscreen", packageName, Integer.toString(taskId)), callback);
    }

    @Override public void suspend(String packageName, int taskId, String homePackage,
            int homeTaskId, Callback callback) {
        if (taskId <= 0 || homeTaskId <= 0) {
            if (callback != null) callback.onResult(
                    NavigationHelperResult.failure("TASK_AUTHORITY_REQUIRED", ""));
            return;
        }
        submit(() -> helper.run("suspend", packageName, Integer.toString(taskId),
                homePackage, Integer.toString(homeTaskId)), callback);
    }

    @Override public void destroy() {
        destroyed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
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
