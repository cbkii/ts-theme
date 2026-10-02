package com.cbkii.ts18launcher;

import android.app.Activity;

/**
 * Compatibility shell for the retired foreground media-prime path.
 *
 * Exact-device cold-boot evidence showed that launching Auxio/NavRadio Activities merely to make
 * their sessions ready stops HOME and disrupts the managed Organic Maps task. Readiness is now
 * owned exclusively by {@link MediaSourceBootstrapper}: MediaBrowser, exact qualified service
 * contracts and existing MediaSessions. Source Activities are presentation endpoints only.
 */
final class StartupBootstrapCoordinator {
    interface PrimeCallback { void onResult(boolean ready, String detail); }

    StartupBootstrapCoordinator(Activity activity) {
        // Kept temporarily as a source-compatible shell while stacked PRs converge. No Activity,
        // overlay, root or task/window authority is retained here.
    }

    boolean isRunning() { return false; }

    void start() {
        MediaEventTrace.record("startup", "foreground-prime-disabled",
                "background-session-readiness-only");
    }

    void primeForCommand(String packageName, PrimeCallback callback) {
        MediaEventTrace.record("startup", "foreground-prime-disabled",
                packageName == null ? "" : packageName);
        if (callback != null) {
            callback.onResult(false,
                    "Background media service/session is not ready; foreground priming is disabled");
        }
    }

    void destroy() {
        // No resources are owned by this compatibility shell.
    }
}
