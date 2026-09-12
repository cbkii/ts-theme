package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exact-device backend for the TS18's proven resizable/freeform task path.
 * It uses only Android 10 ActivityManager shell task operations under a narrow Magisk root bridge;
 * it does not write Topway force-PIP properties.
 */
final class TopwayFreeformBackend {
    interface Callback { void onResult(NavigationHelperResult result); }

    private final NavigationRootHelper helper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TS18-NavigationTask");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed;

    TopwayFreeformBackend(Context context) { helper = new NavigationRootHelper(context); }

    void probe(Callback callback) { submit(() -> helper.run("probe"), callback); }

    void showWindowed(String packageName, NavigationWindowBounds bounds, Callback callback) {
        submit(() -> helper.run("window", packageName,
                Integer.toString(bounds.left), Integer.toString(bounds.top),
                Integer.toString(bounds.right), Integer.toString(bounds.bottom)), callback);
    }

    void verify(String packageName, NavigationWindowBounds bounds, Callback callback) {
        submit(() -> helper.run("verify", packageName,
                Integer.toString(bounds.left), Integer.toString(bounds.top),
                Integer.toString(bounds.right), Integer.toString(bounds.bottom)), callback);
    }

    void focus(String packageName, Callback callback) {
        submit(() -> helper.run("focus", packageName), callback);
    }

    void fullscreen(String packageName, Callback callback) {
        submit(() -> helper.run("fullscreen", packageName), callback);
    }

    void status(String packageName, Callback callback) {
        submit(() -> helper.run("status", packageName), callback);
    }

    void destroy() {
        destroyed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private void submit(Operation operation, Callback callback) {
        if (destroyed) {
            if (callback != null) callback.onResult(NavigationHelperResult.failure("DESTROYED", ""));
            return;
        }
        executor.execute(() -> {
            NavigationHelperResult result;
            try { result = operation.run(); }
            catch (RuntimeException e) { result = NavigationHelperResult.failure("BACKEND_EXCEPTION", e.getClass().getSimpleName()); }
            final NavigationHelperResult delivered = result;
            if (destroyed) return;
            main.post(() -> {
                if (!destroyed && callback != null) callback.onResult(delivered);
            });
        });
    }

    private interface Operation { NavigationHelperResult run(); }
}
